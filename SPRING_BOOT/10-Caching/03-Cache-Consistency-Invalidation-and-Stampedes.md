# 03 · Cache Consistency, Invalidation & Stampedes

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★★ ("How do you keep the cache and the DB in sync?")
> **Prerequisite:** [02 · Redis, Data Structures & Spring Cache](./02-Redis-Data-Structures-and-Spring-Cache.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. The Honest Framing](#1-the-honest-framing)
- [2. Mental Model — Bounded Staleness](#2-mental-model--bounded-staleness)
- [3. Consistency Strategies, Ranked](#3-consistency-strategies-ranked)
- [4. The Races in Detail](#4-the-races-in-detail)
- [5. Invalidation Across Nodes](#5-invalidation-across-nodes)
- [6. Stampede, Penetration, Avalanche — Full Treatments](#6-stampede-penetration-avalanche--full-treatments)
- [7. Distributed Locks & Fencing](#7-distributed-locks--fencing)
- [8. Read-Your-Writes for the User](#8-read-your-writes-for-the-user)
- [9. Code Examples](#9-code-examples)
- [10. Common Mistakes & Gotchas](#10-common-mistakes--gotchas)
- [11. Testing Consistency](#11-testing-consistency)
- [12. Interview Questions & Model Answers](#12-interview-questions--model-answers)
- [13. Self-Check & Revision Card](#13-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> You cannot make a cache and a database consistent without a distributed transaction, and
> you don't want one. So you **bound the staleness**: pick a maximum age, enforce it with TTL,
> and make the window small enough that no business rule depends on it.
>
> The reliable default is **cache-aside + delete-on-write + TTL**, with **eviction after the
> DB transaction commits**. Delete, never update. If you must be race-free, use **versioned
> keys** (`product:42:v17`) so a stale write lands on a key nobody reads.
>
> Read-your-writes for the *same user* is a different requirement from global consistency:
> solve it with a short-lived sticky read (read from primary), a version returned to the
> client, or simply not caching that entity.
>
> Stampede fixes: **single-flight**, **probabilistic early refresh**, **jittered TTL**,
> **refresh-ahead**. Penetration: **negative caching + bloom filter**. Avalanche:
> **jitter + layered cache + warm-up**.
>
> Distributed locks give **efficiency**, not correctness, unless you add **fencing tokens**.

---

## 1. The Honest Framing

The question "how do you keep the cache and DB consistent?" is a trap. There is no answer
that makes the two atomically consistent. What exists is a set of designs whose **maximum
inconsistency window** is known, small, and acceptable.

Answer the question like this, in order:

1. **Name the requirement.** "How stale can this data be?" A product catalog can be 5 minutes
   old; a bank balance cannot be 5 milliseconds old.
2. **Pick a mechanism that meets it.** TTL alone may suffice. If not, add explicit
   invalidation. If the race window matters, version the key.
3. **Name the failure behaviour.** What happens if the delete fails? If Redis is down? If a
   node misses an invalidation message? Every strategy has one; state it.
4. **Name the measurement.** Cache hit rate, staleness histogram (compare cached `updatedAt`
   with DB `updatedAt` on a sample), and an alert if the window is exceeded.

That structure is what separates "I'd use `@CacheEvict`" from a senior answer.

---

## 2. Mental Model — Bounded Staleness

```
   t=0        write committed in DB
    │
    │◄────── INCONSISTENCY WINDOW ──────►│
    │                                     │
    │   cache still holds the old value   │   cache now matches DB
    │                                     │
    └─────────────────────────────────────┴──────────────► time
                                          ▲
                            either the delete lands, or the TTL expires
```

The window is `max(invalidation latency, 0)` normally, and `TTL` in the worst case (a lost
invalidation message, a crashed app, a partitioned node).

**So the TTL is not decoration — it is the bound on your worst-case bug.** Every cached
entity should have a TTL justified by "if invalidation never worked, how wrong could we be?"

```
  staleness budget  =  business tolerance
  TTL               ≤  staleness budget
```

If the business says "prices must be accurate to the second," TTL is 1 second and the cache
barely helps — so the answer may be "don't cache this, or cache it with a version and
validate."

---

## 3. Consistency Strategies, Ranked

| # | Strategy | Consistency | Complexity | Use when |
|---|---|---|---|---|
| 1 | **TTL only** | bounded by TTL | trivial | read-mostly, staleness fine (catalogs, configs) |
| 2 | **Delete on write** | near-real-time, small race | low | **the default** |
| 3 | **Delete on write + delayed double delete** | narrows the race | low-med | read-heavy hot keys with concurrent writes |
| 4 | **Versioned keys** | race-free | medium | correctness matters, can afford key churn |
| 5 | **Write-through** | strong for the written key | medium | read-after-write dominant, low write volume |
| 6 | **CDC invalidation** (outbox/Debezium) | survives app bugs, covers all writers | high | multiple writers, legacy code, strict needs |
| 7 | **No cache** | exact | zero | money, permissions, inventory counts |

### 3.1 Versioned keys — the underrated answer

```java
// read path: resolve current version (cheap: cached separately, or from DB)
int v = versionOf("product", 42);              // e.g. from a version table or a Redis counter
String key = "product:42:v" + v;
Product p = cache.get(key);                    // miss on version bump = correct by design

// write path: bump version in the same DB transaction as the update
@Transactional
void update(Long id, ProductUpdate u) {
    repo.update(id, u);
    jdbc.update("UPDATE entity_version SET version = version + 1 WHERE entity = 'product' AND id = ?", id);
}
```

- A stale writer writes to `v17` while the world now reads `v18` — **impossible to corrupt**.
- Old versions expire by TTL; no explicit delete needed.
- Cost: one extra lookup for the version, and old entries linger until TTL.

This is the answer to "how do you make cache invalidation actually correct?" — you make the
invalid key **unreachable** rather than trying to delete it everywhere.

### 3.2 CDC-driven invalidation

If *any* code path (admin panel, batch job, another service) can write the table, app-level
`@CacheEvict` will miss some of them. Tailing the DB log removes that class of bug:

```
Postgres WAL ─► Debezium ─► Kafka `db.changes` ─► cache-invalidator ─► Redis DEL
```

Now invalidation is driven by the *actual commit*, not by developers remembering to call
`evict`. The cost is real infrastructure. It's the right answer for large legacy estates.

---

## 4. The Races in Detail

### 4.1 Race A — reader repopulates with a pre-write value

```
Reader                      Writer                     DB               Cache
  │ get(k) → miss            │                          │                 │
  ├──────────────────────────┼──────────────────────────┼────────────────►│
  ├── SELECT ────────────────┼──────────────────────────┼────────────────►│ returns OLD
  │                          ├── UPDATE ────────────────┼────────────────►│
  │                          ├── COMMIT ────────────────┼────────────────►│
  │                          ├── DEL k ─────────────────┼────────────────►│
  ├── SET k = OLD ───────────┼──────────────────────────┼────────────────►│  ← STALE
```

The stale value now lives until TTL. Mitigations, in increasing strength:

1. **Short TTL** — bounds it.
2. **Delayed double delete** — `DEL` at t and t+500ms; catches the repopulation. Ugly but
   effective and widely used.
3. **Versioned key** — the SET goes to a version nobody reads. Correct.
4. **Delete under a per-key lock** — the reader holds the lock while it writes the cache,
   and the writer takes the lock before deleting. Adds contention; rarely worth it.

### 4.2 Race B — two writers, cache updated instead of deleted

```
W1: UPDATE db = A
W2: UPDATE db = B
W2: SET cache = B
W1: SET cache = A       ← cache = A, DB = B. PERMANENT.
```

This is why **you delete, not update**. Deleting has a bounded race; updating can be
permanently wrong.

### 4.3 Race C — delete before commit

```
Writer: DEL k          ← another request repopulates from the OLD (uncommitted) row
Writer: UPDATE ... COMMIT
```

The cache now holds the pre-commit value with a full TTL. **Always evict after commit**, via
`TransactionSynchronization.afterCommit()` or `RedisCacheManager.transactionAware()`.

### 4.4 Race D — read from a replica

If the read path falls back to a read replica, a just-committed row may not be visible yet
(replication lag), so the cache gets populated with stale data for a full TTL. Fixes: read
from primary on a miss (expensive but correct), or use `pg_last_wal_replay_lsn`-based
causality, or a short TTL on entries populated from replicas.

---

## 5. Invalidation Across Nodes

Local caches (Caffeine in each JVM) make invalidation a **broadcast** problem:

```
   node A: write ──► DB commit ──► publish "invalidate product:42"
                                        │
   ┌────────────────────────────────────┼────────────────────┐
   ▼                                    ▼                    ▼
 node A evicts                        node B evicts        node C evicts
 (also subscriber)                    (subscriber)         (subscriber)
```

Facts to state:

- **It is eventually consistent.** A node that hasn't processed the message serves stale data
  for the propagation delay. Bound it with TTL.
- **Pub/sub (Redis `PUBLISH`) is fire-and-forget** — an offline subscriber misses messages
  permanently. For guaranteed delivery use a **Stream** or **Kafka** with durable consumer
  offsets.
- **Ordering matters** — a delete followed by a put must arrive in order, or you keep the
  old value. Per-key partitioning fixes it.
- **A new node starts with an empty cache** — it will read from source. Fine, but watch the
  cold-start load.

```java
@Component
class LocalCacheInvalidator {
    private final Cache<Long, Product> l1;
    private final StringRedisTemplate redis;

    @PostConstruct
    void subscribe() {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redis.getConnectionFactory());
        container.addMessageListener((msg, pattern) ->
            l1.invalidate(Long.parseLong(new String(msg.getBody(), UTF_8))),
            new ChannelTopic("cache.invalidate.product"));
        container.start();
    }

    void notifyChange(Long id) {
        redis.convertAndSend("cache.invalidate.product", id.toString());
    }
}
```

---

## 6. Stampede, Penetration, Avalanche — Full Treatments

### 6.1 Stampede (thundering herd)

**Symptom:** p99 and DB QPS spike periodically; one key dominates the misses.

**Fix 1 — single-flight (per-key mutex)**

```java
public Product getWithLock(Long id) {
    String k = "product:v1:" + id;
    var cached = read(k);
    if (cached != null) return cached;

    String lockKey = "lock:" + k;
    String token = UUID.randomUUID().toString();
    boolean owner = Boolean.TRUE.equals(
        redis.opsForValue().setIfAbsent(lockKey, token, Duration.ofSeconds(3)));

    if (owner) {
        try {
            var fresh = repo.findById(id).orElse(null);     // exactly one DB read
            if (fresh != null) write(k, fresh);
            return fresh;
        } finally { release(lockKey, token); }
    }
    // not the owner: brief wait then re-read; never hit the DB in parallel
    sleep(50);
    var again = read(k);
    if (again != null) return again;
    return repo.findById(id).orElse(null);                   // last resort
}
```

Nuance most candidates miss: the "last resort" branch can still stampede. Options: bounded
retries, serving a stale value, or returning a 503-ish "retry" for a hot endpoint.

**Fix 2 — probabilistic early expiration (XFetch)**

```java
boolean shouldRefresh(Instant expiresAt, long computeMillis) {
    double gap = Duration.between(Instant.now(), expiresAt).toMillis();
    double threshold = computeMillis * Math.log(ThreadLocalRandom.current().nextDouble());
    return -gap >= threshold * GAP_BETA;      // refresh earlier as expiry nears
}
```

Spreads refreshes over the tail of the TTL instead of a cliff at expiry.

**Fix 3 — refresh-ahead (Caffeine)**

```java
LoadingCache<Long, Product> cache = Caffeine.newBuilder()
    .maximumSize(100_000)
    .refreshAfterWrite(Duration.ofMinutes(5))     // async refresh, serves stale meanwhile
    .expireAfterWrite(Duration.ofMinutes(30))     // hard bound on staleness
    .build(this::loadFromDb);
```

`refreshAfterWrite` + `expireAfterWrite` together is the cleanest stampede avoidance
available: stale-while-revalidate with a hard ceiling.

**Fix 4 — jittered TTL** — the cheapest, and it prevents the *coordinated* version of the
problem (deploy sets all TTLs at once).

### 6.2 Penetration

**Symptom:** DB load from queries for ids that don't exist; cache hit rate looks fine for
real traffic.

```java
// negative caching with a short TTL — deliberate, documented
public Product get(Long id) {
    String k = "product:v1:" + id;
    var hit = read(k);
    if (hit == NEGATIVE) return null;          // cached absence
    if (hit != null) return hit;

    var p = repo.findById(id).orElse(null);
    write(k, p == null ? NEGATIVE : p, p == null ? Duration.ofSeconds(30) : TTL);
    return p;
}
```

Plus:
- **Bloom filter** of existing ids, refreshed periodically, checked before the cache.
- **Input validation** — reject ids outside the valid range at the edge.
- **Rate limiting** per client to blunt enumeration.

### 6.3 Avalanche

**Symptom:** the whole DB saturates at once — often right after a cache restart, a deploy, or
a synchronized TTL expiry.

- **Jitter** every TTL.
- **Warm the cache** before the node takes traffic (read the hot set from a snapshot).
- **Layered cache** — L1 absorbs the window when Redis is unavailable.
- **Circuit breaker / load shedding** on the DB path — returning 503 fast beats a 30-minute
  outage.
- **Cache cluster HA** — replicas, and a client that fails over quickly, not after 30 s of
  retries.

---

## 7. Distributed Locks & Fencing

### 7.1 When you actually need one

- Avoiding duplicated expensive work (recompute, report generation) — **efficiency lock**.
- Preventing concurrent mutation of a resource that has no DB-level guard — **correctness lock**.

For the second case, prefer the database: a unique constraint, `SELECT ... FOR UPDATE`, or
optimistic versioning. A distributed lock is a *fallback* when you can't touch the DB, not a
first choice. Saying this earns points.

### 7.2 Correct `SET NX PX` usage

```java
public boolean tryLock(String key, String token, Duration ttl) {
    return Boolean.TRUE.equals(
        redis.opsForValue().setIfAbsent("lock:" + key, token, ttl));
}

private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
    "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
    Long.class);

public void unlock(String key, String token) {
    redis.execute(UNLOCK, List.of("lock:" + key), token);
}
```

Never `DEL` unconditionally — you may delete a lock someone else now holds.

### 7.3 The fundamental flaw and the fix

```
T0  client A acquires lock (ttl 10s)
T1  A pauses for 12s (GC / VM freeze / network partition)
T2  lock expires
T3  client B acquires lock, starts work
T4  A wakes up, believes it holds the lock, writes      ← TWO WRITERS
```

No TTL-based lock can prevent this. The fix is a **fencing token**: a monotonically
increasing number issued with each acquisition, which the *resource* validates and rejects
if lower than the last seen.

```java
long fence = redis.opsForValue().increment("fence:order:42");
// write with the fence; storage rejects fence <= last_applied_fence
storage.write(orderId, data, fence);
```

If the storage is a plain DB, implement it as
`UPDATE ... WHERE fence < ?` on a row holding `last_fence`. If the storage can't do that, you
don't have a safe lock — you have a load-shedding heuristic. Say so.

### 7.4 Redlock, briefly

Redlock acquires locks on N independent Redis masters and requires a majority. It's the
subject of a well-known critique (Kleppmann) because it still relies on timing assumptions
and can't prevent the pause problem without fencing. **Interview-safe position:** Redlock is
stronger than a single-node lock but does not remove the need for fencing; use it for
efficiency, and use fencing for correctness.

---

## 8. Read-Your-Writes for the User

A subtler requirement: the *same user* must see their own write immediately, even if other
users can see stale data.

| Technique | How |
|---|---|
| **Skip cache after write** | set a short-lived `no-cache:user:42` flag; the read path bypasses cache for that user |
| **Version in the response** | the write returns `version=17`; the client sends it; the server serves at least that version |
| **Sticky to primary** | read from primary for N seconds after a write by that session |
| **Client-side optimistic UI** | the app already has the value; the cache catches up |

The first is the cheapest and most used: a 30-second "bypass cache for this user" marker.

```java
@Transactional
public void updateProfile(Long userId, ProfileUpdate u) {
    repo.update(userId, u);
    cache.evict(userId);
    redis.opsForValue().set("nocache:user:" + userId, "1", Duration.ofSeconds(30));
}

public Profile readProfile(Long userId) {
    if (Boolean.TRUE.equals(redis.hasKey("nocache:user:" + userId))) {
        return repo.find(userId);          // direct read for this user
    }
    return cachedProfile(userId);
}
```

---

## 9. Code Examples

### 9.1 Evict after commit, correctly

```java
@Service
public class ProductWriter {
    private final ProductRepository repo;
    private final CacheManager cacheManager;

    @Transactional
    public void update(Long id, ProductUpdate u) {
        repo.update(id, u);
        evictAfterCommit("product", id);
    }

    private void evictAfterCommit(String cacheName, Object key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cacheManager.getCache(cacheName).evict(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                cacheManager.getCache(cacheName).evict(key);
            }
        });
    }
}
```

### 9.2 Versioned cache decorator

```java
@Component
public class VersionedCache {
    public <T> T getOrLoad(String entity, Long id, Duration ttl, Supplier<T> loader) {
        int v = currentVersion(entity, id);
        String key = entity + ":" + id + ":v" + v;
        var cached = read(key);
        if (cached != null) return cached;
        var value = loader.get();
        if (value != null) write(key, value, ttl);
        return value;
    }

    private int currentVersion(String entity, Long id) {
        String k = "ver:" + entity + ":" + id;
        String v = redis.opsForValue().get(k);
        if (v != null) return Integer.parseInt(v);
        int fromDb = repo.versionOf(entity, id);        // authoritative
        redis.opsForValue().setIfAbsent(k, String.valueOf(fromDb), Duration.ofHours(1));
        return fromDb;
    }
}
```

### 9.3 Warm-up on startup

```java
@Component
class CacheWarmer implements ApplicationRunner {
    private final ProductRepository repo;
    private final ProductCache cache;

    public void run(ApplicationArguments args) {
        int page = 0, size = 500, total = 0;
        while (true) {
            var batch = repo.findTopNByOrderCount(size, page * size);
            if (batch.isEmpty()) break;
            batch.forEach(p -> cache.put(p, Duration.ofMinutes(30)));
            total += batch.size();
            page++;
        }
        log.info("warmed {} hot products", total);
    }
}
```

### 9.4 Sanity check: staleness monitor

```java
@Component
class StalenessMonitor {
    /** Sample N cached entries and compare updatedAt with the DB. Alert if p99 > SLA. */
    @Scheduled(fixedDelay = 60_000)
    void sample() {
        for (var cached : sampleCachedProducts(100)) {
            var fresh = repo.findUpdatedAtById(cached.id());
            if (fresh == null) continue;
            long age = Duration.between(fresh, cached.updatedAt()).toMillis();
            meters.summary("cache.staleness.ms", "entity", "product").record(age);
        }
    }
}
```

Very few teams do this — mentioning it is a strong signal. Caches fail silently; a staleness
metric is the only way to see it.

---

## 10. Common Mistakes & Gotchas

| Mistake | Consequence | Fix |
|---|---|---|
| `cache.put(newValue)` instead of delete | Two writers → permanent staleness | Delete |
| Evicting before the transaction commits | Cache repopulated with pre-commit value | `afterCommit` |
| No TTL "because we invalidate correctly" | One missed invalidation → stale forever | TTL always |
| Assuming pub/sub invalidation is reliable | Offline node misses messages | TTL + durable bus if needed |
| Single-flight lock without TTL | Deadlock if the owner dies | `PX` always, plus a timeout on work |
| `DEL` to release a lock | Deletes another holder's lock | Lua compare-and-delete |
| Lock with TTL for correctness | GC pause → two writers | Fencing token |
| Negative caching without a short TTL | Deleted-then-recreated entity invisible | 30–60s max |
| Same TTL derived from `now()` at startup | Synchronized expiry | Jitter |
| Caching a `null` from a transient error | "Not found" for 10 minutes | Only cache a definitive not-found |
| Version bump outside the write transaction | Version and data disagree | Same transaction |
| Using a replica for the miss fallback | Populates cache with stale data | Read primary on miss |

---

## 11. Testing Consistency

You can't prove consistency by code review. Test it at the boundaries:

1. **Race injection** — add a `Thread.sleep` between the DB read and the cache set in a test
   profile, run a concurrent writer, assert the stale value is bounded by TTL and that the
   versioned variant is never wrong.
2. **Kill-during-write** — kill the app after the DB commit, before eviction; restart; assert
   the TTL eventually corrects the stale entry (and that the versioned variant is already
   correct).
3. **Cache down** — stop Redis under load; assert the service serves correct (slower) data
   and recovers without errors.
4. **Stampede load test** — 500 concurrent requests for an expired hot key; count DB queries:
   should be 1 with single-flight, 500 without.
5. **Invalidation fan-out** — three app instances with local caches; write on one; assert the
   others converge within the documented SLA.
6. **Staleness assertion** — a test that reads a cached value, updates the DB directly
   (bypassing the service), and asserts behaviour is documented (stale until TTL) — this
   proves you know your own failure mode.

---

## 12. Interview Questions & Model Answers

**Q1. How do you keep the cache and the DB consistent?**
> You don't — you bound the inconsistency. Cache-aside with delete-on-write, evicting after
> the transaction commits, plus a TTL sized to the business's staleness budget. That gives a
> window of a few microseconds normally and the TTL in the worst case where an invalidation
> is lost. If the business needs better than that, I'd use versioned keys so a stale write is
> unreachable, or not cache the entity.

**Q2. Why delete instead of update the cache?**
> Because two concurrent writers can leave the cache permanently holding the loser's value:
> W1 writes A, W2 writes B, W2 sets the cache to B, then W1 sets it to A. The DB says B and
> the cache says A forever. Deleting has a race too, but it's tiny and self-heals with the
> next write or TTL.

**Q3. Your cache is repopulated with an old value right after a write. What now?**
> That's the reader-repopulates race: the reader read before the commit and wrote the cache
> after the writer's delete. I bound it with a short TTL, and if the entity is hot or
> business-critical I switch to versioned keys so the stale write goes to an unreachable
> version. Delayed double-delete is the pragmatic middle option.

**Q4. How do you invalidate a local cache across 20 instances?**
> Publish an invalidation event — Redis pub/sub or Kafka — and every node evicts from its own
> Caffeine cache. I'd state clearly that this is eventually consistent: a node that misses the
> message serves stale data until TTL, so pub/sub fire-and-forget needs a short TTL behind it.
> For guarantee I'd use a durable stream or accept the TTL as the bound.

**Q5. How do you prevent a stampede on a hot key?**
> Single-flight, so only one request recomputes and the rest wait or get the stale value;
> probabilistic early refresh to spread the work; and refresh-ahead in Caffeine, which serves
> stale while refreshing in the background. Jittered TTLs prevent the coordinated version
> where everything expires together.

**Q6. Is Redlock safe?**
> It's safer than a single-node lock but it doesn't eliminate the fundamental issue — a client
> can pause longer than the lock TTL and resume believing it still holds it. The only real fix
> is fencing: the lock issues a monotonically increasing token and the resource rejects writes
> with an older token. So Redlock is fine for efficiency, not for correctness.

**Q7. What's the difference between cache penetration and an avalanche?**
> Penetration is requests for keys that don't exist — every one misses and hits the DB, so the
> cache provides no protection; fixed with negative caching and a bloom filter. An avalanche is
> a mass simultaneous miss, either from synchronized TTL expiry, a deploy, or the cache going
> down; fixed with jitter, warm-up, layering and load shedding.

**Q8. How do you make a user see their own write immediately?**
> A per-user "bypass cache" marker for 30 seconds after a write, or returning the entity
> version and having the read path serve at least that version. That decouples the user's
> read-your-writes requirement from global consistency, which is the point — other users can
> see a slightly stale view.

---

## 13. Self-Check & Revision Card

- [ ] I can state the inconsistency window for my design, worst case included.
- [ ] I can explain why delete beats update with the interleaving.
- [ ] I can explain evict-after-commit and why before-commit is wrong.
- [ ] I can describe versioned keys and when they're worth it.
- [ ] I can name four stampede fixes and pick one with justification.
- [ ] I can distinguish penetration from avalanche.
- [ ] I can explain the fencing-token necessity for correctness locks.
- [ ] I can design read-your-writes for one user.

```
┌──────────────── CACHE CONSISTENCY — 60s RECALL ──────────────────────────────┐
│ No atomicity across cache+DB. BOUND the staleness: TTL = worst-case bug.     │
│                                                                              │
│ DEFAULT: cache-aside · DELETE (never update) · evict AFTER COMMIT · TTL.     │
│                                                                              │
│ RACES: reader repopulates pre-write value (window ~µs, bounded by TTL)      │
│        two writers + update ⇒ PERMANENT staleness (why we delete)           │
│        evict-before-commit ⇒ repopulate from uncommitted view                │
│                                                                              │
│ RACE-FREE: versioned keys — stale write lands on an unreachable version.     │
│ MULTI-NODE: pub/sub invalidation is eventual; TTL is the real bound.         │
│                                                                              │
│ STAMPEDE → single-flight · XFetch · refresh-ahead · jitter                   │
│ PENETRATION → negative cache (≤60s) · bloom filter · validate ids            │
│ AVALANCHE → jitter · warm-up · L1 layer · load shedding                      │
│                                                                              │
│ LOCKS: SET NX PX + Lua release. TTL lock ≠ correctness. FENCING TOKEN.       │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [02 · Redis, Data Structures & Spring Cache](./02-Redis-Data-Structures-and-Spring-Cache.md) · **Next:** [04 · Multi-Level Caching & Production Operations](./04-Multi-Level-Caching-and-Production-Operations.md)
