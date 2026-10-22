# 01 · Caching Fundamentals & Patterns

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★★ ("How do you invalidate the cache?" — the question that never dies)
> **Prerequisite:** [09 · Messaging Systems](../09-Messaging-Systems/README.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Cache Hit Budget](#2-mental-model--the-cache-hit-budget)
- [3. Deep Dive — Strategies](#3-deep-dive--strategies)
- [4. Eviction Policies](#4-eviction-policies)
- [5. Invalidation](#5-invalidation)
- [6. The Classic Failure Modes](#6-the-classic-failure-modes)
- [7. Where Caches Live](#7-where-caches-live)
- [8. Memory, Serialization & Keys](#8-memory-serialization--keys)
- [9. Code Examples — Spring Cache](#9-code-examples--spring-cache)
- [10. Common Mistakes & Gotchas](#10-common-mistakes--gotchas)
- [11. Performance & Sizing](#11-performance--sizing)
- [12. Security](#12-security)
- [13. Best Practices Checklist](#13-best-practices-checklist)
- [14. Exercises & Challenges](#14-exercises--challenges)
- [15. Interview Questions & Model Answers](#15-interview-questions--model-answers)
- [16. Self-Check & Revision Card](#16-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> A cache is a **copy** that must be allowed to be wrong. If the business cannot tolerate a
> stale read for *any* duration, don't cache it — or cache only a version/etag.
>
> The default pattern is **cache-aside** (read-through by the app): read cache → miss → read
> DB → write cache. It's simple, tolerant of cache outages, and the only cost is a brief
> stale window on writes.
>
> The write side has three options: **write-through** (write cache + DB synchronously — safe,
> slower), **write-behind** (write cache, flush async — fast, may lose data), and **write-around**
> (write DB only, invalidate the cache — safe, miss after each write).
>
> **Invalidation is the hard part.** Either delete-on-write (simplest, one-off race) or use
> versioned/short-TTL keys. Never try to update the cache *and* the DB atomically across two
> systems — you can't.
>
> Three failure modes to name: **stampede/thundering herd** (hot key expires, 10k requests
> hit the DB), **penetration** (requests for keys that don't exist), **avalanche** (many keys
> expire at once, or the cache node dies). Each has a standard fix — locking, negative
> caching/bloom filter, jitter — and knowing them by name wins points.

---

## 1. What & Why

### 1.1 The physics

| Operation | Approximate latency |
|---|---|
| L1 cache | ~1 ns |
| Main memory | ~100 ns |
| Local SSD random read | ~100 µs |
| Redis/memcached (same AZ) | ~0.5–1 ms |
| Postgres indexed read (same AZ) | ~1–3 ms |
| Postgres read with a join/scan | ~10–500 ms |
| HTTP call to another service | ~5–100 ms |

A cache sits between two orders of magnitude. That's why caching works — and why adding it
"just in case" is a common way to make a system worse.

### 1.2 What caching actually buys

- **Latency** — serve in ~1 ms instead of ~50 ms.
- **Throughput / offload** — 95% of reads never touch the DB, which frees primary capacity
  for writes.
- **Cost** — a Redis node is far cheaper than doubling your RDS instance.
- **Resilience** — an in-memory cache can serve a degraded response during a DB outage
  (this is a *feature* you must design deliberately, not a happy accident).

### 1.3 What caching costs

- **Correctness surface** — every cached value is a potential stale read, a bug report, and
  a "why doesn't my update show up?" ticket.
- **Operational surface** — one more cluster with its own failure modes (hot slots, eviction
  storms, memory pressure, replication lag).
- **Code complexity** — key design, invalidation, warm-up, serialization, versioning.
- **Debug difficulty** — invisible state between the request and the truth.

**Decision rule:** cache only when you can name the metric you're fixing (p99, DB CPU, cost)
and the staleness budget the business accepts. "It'll be faster" is not a design.

---

## 2. Mental Model — The Cache Hit Budget

```
                 100 requests
                       │
      ┌────────────────┴────────────────┐
      │  95 hits (1 ms)                 │  5 misses (50 ms)
      │  → 95 ms                        │  → 250 ms
      └─────────────────────────────────┘
   total ≈ 345 ms   vs   100 × 50 ms = 5000 ms without cache

   Effective latency  =  hitRate × cacheLatency + (1 - hitRate) × dbLatency
```

**The hit-rate curve is what matters, and it's brutally non-linear at the low end:**

| Hit rate | DB load vs uncached |
|---|---|
| 0% | 100% |
| 50% | 50% |
| 80% | 20% |
| 95% | **5%** |
| 99% | 1% |

Going from 90% → 99% cuts DB load by 10x. Going from 0% → 50% only halves it. This is why
**cache key design and TTL tuning matter more than the cache technology.**

**The corollary for interviews:**

> *A cache with a bad key strategy is a random-number generator with extra steps.*

**Key-shape rule:** the key must be the exact unit of data you can invalidate. Cache
`order:42`, not `orders-page-1` — because a page-level key needs invalidating whenever *any*
order on that page changes, and you'll never get all the writes.

---

## 3. Deep Dive — Strategies

### 3.1 Cache-aside (lazy loading) — the default

```
read(id):
  v = cache.get(k)
  if v != null: return v                 // hit
  v = db.find(id)                        // miss
  cache.put(k, v, ttl)
  return v

write(id, data):
  db.update(id, data)
  cache.delete(k)                        // NOT cache.put(newValue)
```

- ✅ Only requested data is cached; cache failures degrade to DB reads; simple to reason about.
- ❌ A miss costs 3 round trips; the first reader is slow; stale window between DB commit
  and cache delete.
- ❌ **Two concurrent reads on a miss both hit the DB** (unless you add locking).

**This is what `@Cacheable` implements.** It is the right default for 80% of cases.

### 3.2 Read-through

The cache library/loader fetches on miss — the app only talks to the cache.

```java
@Bean
CacheLoader<Long, Order> loader() { return id -> orderRepository.findById(id).orElseThrow(); }
```

- ✅ Cleaner app code; the miss path is in one place.
- ❌ Requires a cache abstraction that supports loaders (JCache/Caffeine do; Redis via
  Spring's `CacheLoader` does not natively).
- Same stampede risk as cache-aside.

### 3.3 Write-through

Write to cache **and** DB synchronously, in that order (or with a transaction).

- ✅ Cache is always fresh for the written key; reads never see a stale value.
- ❌ Every write pays cache latency; caches data that may never be read (pollutes the cache
  with write-only data).
- Failure mode: cache write succeeds, DB write fails → cache holds a value that doesn't
  exist. Order matters: **DB first, then cache**.

### 3.4 Write-behind (write-back)

Write to the cache, return, flush to the DB asynchronously.

- ✅ Lowest write latency, batches writes, great for counters.
- ❌ **Data loss on cache failure.** A Redis crash can drop minutes of writes. Only
  acceptable when the data is reconstructable or the loss is tolerable.
- Complexity: need a reliable flush, ordering per key, and idempotent replay.

### 3.5 Write-around

Write DB only; **invalidate** (delete) the cache entry.

- ✅ No cache pollution, no lost writes, safe under failure.
- ❌ Next read is a guaranteed miss (and a possible stampede).
- This is what most "cache-aside + delete on write" systems actually do — and it's the
  right default.

### 3.6 Refresh-ahead

Proactively refresh entries before TTL expiry, based on access frequency.

- ✅ Avoids misses on hot keys; smooth DB load.
- ❌ Wasted work if the key isn't read again; needs access statistics.

Caffeine's `refreshAfterWrite` does this — it returns the stale value while refreshing
asynchronously.

### 3.7 Comparison

| Strategy | Read latency | Write latency | Staleness | Data loss risk | Complexity |
|---|---|---|---|---|---|
| Cache-aside | miss = slow | fast | small window | none | low |
| Read-through | miss = slow | fast | small window | none | low-med |
| Write-through | fast | **slower** | none | none | medium |
| Write-behind | fast | **fastest** | none | **yes** | high |
| Write-around | miss after write | fast | small window | none | low |
| Refresh-ahead | **fast** | fast | bounded by refresh | none | medium |

**Interview default:** cache-aside + delete-on-write + TTL as a safety net. Mention the others
as deliberate deviations with named trade-offs.

---

## 4. Eviction Policies

| Policy | Behaviour | Best for |
|---|---|---|
| **LRU** | evict least recently used | general purpose, recency-skewed traffic |
| **LFU** | evict least frequently used | stable hot set, long tail |
| **FIFO** | evict oldest inserted | streaming/queue-like data (rarely right for caches) |
| **TTL / volatile-ttl** | evict nearest expiry | mixed lifetimes |
| **Random** | evict a random key | very large key spaces, cheap |
| **W-TinyLFU** (Caffeine) | window + frequency sketch | best general-purpose hit rate |
| **Noeviction** (Redis) | error on write when full | when losing data is unacceptable (queues, sessions) |

**Redis `maxmemory-policy` names:** `allkeys-lru`, `allkeys-lfu`, `volatile-lru`,
`volatile-lfu`, `allkeys-random`, `volatile-random`, `volatile-ttl`, `noeviction`.

Two rules people get wrong:

1. **`volatile-*` only evicts keys with a TTL set.** If you forget TTLs, it evicts nothing
   and you get `OOM command not allowed`.
2. **`noeviction` turns a full cache into an outage.** For a pure cache, `allkeys-lru` (or
   `allkeys-lfu`) is correct: losing a key just means a slower read.

**LRU in Redis is approximated** — Redis samples a few keys and evicts the least recently
used among them (`maxmemory-samples`). That's why the policy is cheap and why hit rates
differ slightly from a true LRU.

---

## 5. Invalidation

### 5.1 The three honest options

| Approach | Mechanism | Guarantee |
|---|---|---|
| **TTL only** | value expires eventually | bounded staleness ≤ TTL |
| **Explicit delete** | app deletes the key on write | near-immediate, but racy |
| **Versioned keys** | key includes a version; bump to invalidate | atomic, no race, but leaks old entries until TTL |

There is no fourth option that gives you strict consistency across two systems without
distributed transactions. **Distributed cache invalidation is a hard problem; solve it by
making staleness bounded, not by pretending it's zero.**

### 5.2 Delete vs update on write

**Delete.** Always delete, then let the next read repopulate.

Why not update? Two concurrent writers can interleave:

```
W1: write DB=A          W2: write DB=B
                        W2: cache.set(B)
W1: cache.set(A)        ← cache now holds A, DB holds B. Permanent staleness.
```

Deleting has a race too (a reader can repopulate with a pre-write value), but its window is
microseconds and it self-heals on the next write or TTL. The mitigated version is
**delayed double delete**: delete → wait a few hundred ms → delete again, catching a reader
that repopulated from a stale replica.

### 5.3 The stale-read race (cache-aside, in detail)

```
Reader                 Writer                DB               Cache
  │ get → miss           │                   │                 │
  ├──────────────────────┼───────────────────┼────────────────►│
  ├── read (old value) ──┼───────────────────┼────────────────►│
  │                      ├── update ────────►│                 │
  │                      ├── delete ─────────┼────────────────►│
  ├── set(old value) ────┼───────────────────┼────────────────►│  ← stale forever (until TTL)
```

The reader's write to the cache lands **after** the writer's delete. Fixes:

- **Short TTL** — bounds the damage. Simplest and usually sufficient.
- **Versioned key** — `order:42:v7`; the reader writes to `v7` while writes bump to `v8`, so
  the stale write goes to a key nobody reads. Elegant and race-free.
- **Delayed double delete** — the common pragmatic patch.
- **Single-flight + write lock** — expensive, rarely worth it.

### 5.4 Cross-node invalidation

With a local (in-process) cache on multiple nodes, deleting in one node's cache doesn't
affect the others. You need a **pub/sub invalidation bus** (Redis pub/sub, Kafka, or a
`CacheManager` backed by a shared store):

```java
// publish on write
redis.convertAndSend("cache.invalidate", "product:" + id);

// every node subscribes and evicts locally
@RedisListener("cache.invalidate")
void onInvalidate(String key) { caffeineCache.invalidate(key); }
```

This is inherently **eventually consistent** — a node that misses the message keeps serving
stale data until its TTL. Say that out loud in an interview; claiming it's instant is wrong.

---

## 6. The Classic Failure Modes

### 6.1 Cache stampede / thundering herd

A hot key expires; 10,000 concurrent requests all miss and all hit the DB simultaneously.

Fixes, in order of preference:

1. **Per-key lock / single-flight** — one request recomputes, others wait or serve stale.
2. **Probabilistic early expiry (XFetch)** — each request refreshes with probability that
   rises as expiry approaches, smoothing the herd out.
3. **Jittered TTL** — no two keys expire at the same instant.
4. **Serve stale while revalidating** — return the old value, refresh in the background.
5. **Refresh-ahead** — never let a hot key expire cold.

```java
// single-flight with Caffeine
LoadingCache<Long, Order> cache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofMinutes(10))
    .maximumSize(100_000)
    .build(id -> orderRepository.findById(id).orElseThrow());
// Caffeine collapses concurrent loads for the same key automatically
```

Redis-backed single-flight needs an explicit `SET NX` lock with a short TTL and a fallback
to "read DB anyway after waiting".

### 6.2 Cache penetration

Requests for keys that **don't exist** — every request misses, hits the DB, and caches
nothing. An attacker enumerating `/users/1..999999999` turns your cache into a DB load
amplifier.

Fixes:
- **Negative caching** — cache the null result with a short TTL (30–60s).
- **Bloom filter** — reject ids that provably don't exist before the cache lookup.
- **Validate the key shape** — reject out-of-range or malformed ids at the edge.

### 6.3 Cache avalanche

A large fraction of keys expire simultaneously (same TTL set at deploy), or the cache
cluster dies and 100% of traffic goes to the DB.

Fixes:
- **TTL jitter** — `ttl = base + random(0, 10% of base)`.
- **Layered cache** — local L1 absorbs the hit when Redis is down.
- **Circuit breaker on the DB path** — shed load rather than die.
- **Warm-up** on cache restart before taking traffic.

### 6.4 Hot key

One key (a celebrity profile, a global config) receives a huge share of traffic and
concentrates on one Redis node.

Fixes: **local L1 cache** in front, key replication with a suffix and random read choice,
or request coalescing at the gateway.

### 6.5 Big key / hot slot

A single key holding a 10 MB JSON blob or a set with millions of members → slow ops, uneven
memory, replication lag. Fix: split (`hash` field or chunked keys), store blobs in object
storage with a pointer in the cache.

---

## 7. Where Caches Live

```
browser cache ─ CDN ─ API gateway ─ local (Caffeine) ─ Redis/memcached ─ DB buffer pool
   (HTTP)      (edge)   (edge)        (per-node)        (shared)          (engine)
```

| Layer | Typical TTL | Invalidation | Strength |
|---|---|---|---|
| Browser | minutes–days | `Cache-Control`, ETag | zero latency, free |
| CDN | minutes–hours | purge API, surrogate keys | global scale, offloads all |
| Gateway | seconds–minutes | TTL, purge | protects services |
| Local (Caffeine) | seconds–minutes | pub/sub, TTL | sub-ms, no network |
| Shared (Redis) | minutes–hours | delete, TTL, version | consistent view, survives deploys |
| DB buffer pool | managed | n/a | transparent, free |

**Layering rule:** closer to the user = longer TTL = more tolerant of staleness. A CDN
caching a product page for 5 minutes is fine; caching a bank balance is not.

---

## 8. Memory, Serialization & Keys

### 8.1 Key design

```
✅ order:42
✅ user:42:profile
✅ product:42:v7                    (versioned)
✅ tenant:7:config

❌ SELECT * FROM ... (full query as key — unmaintainable)
❌ objects with unstable toString() (default Object.hashCode → new key every call!)
❌ keys without a namespace prefix (can't flush a domain)
```

Include the **version** in the prefix so a schema change can be rolled out by bumping it
(`product:v2:42`) — old entries expire naturally, no flush needed.

For Spring's `@Cacheable`, `key = "#id"` uses `SimpleKeyGenerator`, which relies on
`equals`/`hashCode`. A DTO without them produces a different key per call → **0% hit rate**.
That's a top-3 real-world bug.

### 8.2 Serialization

| Format | Size | Speed | Human-readable | Notes |
|---|---|---|---|---|
| JDK serialization | largest | slowest | no | avoid — version-fragile, RCE risk |
| JSON | medium | fast | yes | default choice, verbose |
| Protobuf/Avro | small | fastest | no | best for large values/high volume |
| MessagePack/CBOR | small | fast | no | good middle ground |

JDK serialization is a security problem: deserializing untrusted bytes is a known RCE vector
(gadget chains). If Redis is compromised or shared, this is your breach. Use JSON or Protobuf
with an explicit type mapping.

### 8.3 What NOT to store in a shared cache

- Unbounded collections (grow until OOM).
- Whole aggregates when a field would do.
- Per-user permissions (invalidation nightmare — one role change must invalidate N keys).
- Anything you can't afford to serve stale for the full TTL.

---

## 9. Code Examples — Spring Cache

### 9.1 Dependencies and configuration

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 10m
      cache-null-values: true        # negative caching, on purpose
      key-prefix: "app:"
      use-key-prefix: true
  data:
    redis:
      timeout: 200ms                 # cache ops must fail fast
      lettuce:
        pool:
          max-active: 16
          max-wait: 100ms
```

```java
@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        var defaults = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .disableCachingNullValues()               // we opt in per-cache instead
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(cf)
            .cacheDefaults(defaults)
            // per-cache overrides: short TTL + negative caching for hot, mutable data
            .withCacheConfiguration("order",
                defaults.entryTtl(Duration.ofSeconds(60)).disableCachingNullValues())
            .withCacheConfiguration("product",
                defaults.entryTtl(Duration.ofMinutes(30)))
            .transactionAware()                        // evict after commit — see below
            .build();
    }
}
```

### 9.2 Annotated service

```java
@Service
public class ProductService {

    @Cacheable(cacheNames = "product", key = "#id",
               unless = "#result == null")
    public Product get(Long id) {
        return repo.findById(id).orElse(null);
    }

    @CachePut(cacheNames = "product", key = "#result.id")   // write-through for create
    public Product create(Product p) {
        return repo.save(p);
    }

    @CacheEvict(cacheNames = "product", key = "#id")
    public void update(Long id, Product p) {
        repo.save(p);                                   // delete AFTER the commit
    }

    @CacheEvict(cacheNames = "product", allEntries = true)  // bulk, expensive — use rarely
    public void reimport() { /* ... */ }
}
```

**`@CacheEvict` ordering bug:** by default the eviction happens **after** the method returns
but **before** the surrounding transaction commits. If the transaction rolls back, you've
evicted a cache entry for a change that never happened (harmless — just a miss), but worse,
a concurrent reader can repopulate with the pre-commit value. Use
`@CacheEvict(beforeInvocation = false)` (default) **plus** `TransactionSynchronization` or
`.transactionAware()` so eviction fires **after commit**:

```java
@Transactional
public void update(Long id, Product p) {
    repo.save(p);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override public void afterCommit() { cacheManager.getCache("product").evict(id); }
        });
}
```

### 9.3 Two-level cache (Caffeine L1 + Redis L2)

```java
@Component
public class TwoLevelCache {
    private final Cache<Long, Product> l1 = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofSeconds(30))
        .build();
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ProductRepository repo;

    public Product get(Long id) {
        var local = l1.getIfPresent(id);
        if (local != null) return local;

        String key = "product:v1:" + id;
        var json = redis.opsForValue().get(key);
        if (json != null) {
            var p = read(json);
            l1.put(id, p);
            return p;
        }
        var p = repo.findById(id).orElse(null);
        if (p != null) {
            redis.opsForValue().set(key, write(p), Duration.ofMinutes(10));
            l1.put(id, p);
        }
        return p;
    }

    @EventListener
    public void onInvalidate(CacheInvalidationEvent e) {
        l1.invalidate(e.id());
        redis.delete("product:v1:" + e.id());
    }
}
```

### 9.4 Resilience: never let the cache take you down

```java
@Bean
CacheErrorHandler cacheErrorHandler() {
    return new SimpleCacheErrorHandler() {
        @Override public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
            log.warn("cache GET failed, falling back to source: {}", ex.toString());
            // swallow → the method executes normally (DB read)
        }
        @Override public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
            log.warn("cache PUT failed: {}", ex.toString());
        }
        @Override public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
            log.error("cache EVICT failed — stale data possible: {}", ex.toString());
        }
    };
}
```

**A cache outage must be a performance incident, never an availability incident.** Test it:
stop Redis in staging and verify the service still works, just slower.

---

## 10. Common Mistakes & Gotchas

| Mistake | Consequence | Fix |
|---|---|---|
| Caching without a staleness budget | Product/business argument you can't win | Define max staleness per cache |
| `@Cacheable` on a self-invoked method | No proxy → no caching | Separate bean |
| `@Cacheable` on a private method | Silently ignored | Public methods only |
| DTO without `equals`/`hashCode` as key | 0% hit rate | Immutable keys, explicit `key = "#id"` |
| Same TTL on everything | Avalanche on expiry | Jitter |
| Caching `null` without intent | Either penetration or stale negative results | Explicit negative caching, short TTL |
| `allEntries = true` on a hot cache | Full flush → stampede | Per-key eviction |
| Evicting before commit | Reader repopulates with old value | Evict in `afterCommit` |
| Caching exceptions | Poison value served for the TTL | Never cache failures |
| Unbounded cached collections | OOM | Bound the value size; store ids, not graphs |
| JDK serialization across versions | `InvalidClassException` after a deploy | JSON/Protobuf + explicit class mapping |
| Cache treated as source of truth | Data loss on flush | Cache is a copy; DB is the truth |
| No monitoring of hit rate | Silent regression to 0% | Export hit/miss metrics, alert on drop |
| `noeviction` on a cache | Writes fail when full → outage | `allkeys-lru` |

---

## 11. Performance & Sizing

**Sizing:** `memory ≈ keys × (valueSize + keySize + ~50–80 bytes overhead)`. Redis overhead
per key is significant — 1M keys of 100 bytes ≈ 200 MB, not 100 MB. Add 25–50% headroom for
fragmentation and replication buffers.

**Throughput:** a single Redis node does ~100k+ ops/s on simple GET/SET with pipelining;
latency ~0.5 ms in-AZ. Use **pipelining** or **MGET** for batch reads — 100 round trips at
0.5 ms is 50 ms, one MGET is 1 ms.

**Key metrics to watch:**
- hit rate (per cache, per endpoint)
- `evicted_keys` / `expired_keys`
- `used_memory` vs `maxmemory` (headroom)
- `instantaneous_ops_per_sec`, `latency_percentiles`
- `rejected_connections`, `blocked_clients`
- DB load correlation (a hit-rate dip shows up as DB CPU immediately)

**The single best dashboard:** cache hit rate and DB QPS on the same graph. A cache
regression is visible instantly as an inverse correlation.

---

## 12. Security

- **Never trust cached authorisation decisions** for long TTLs — a revoked permission served
  from cache is an access-control bypass. Cache with a short TTL **and** invalidate on
  permission change.
- **Key injection** — user-supplied ids used verbatim in keys allow collisions or
  `:`-injection (`order:42:admin`). Validate and normalise.
- **No plaintext PII** in a shared cache; Redis is often less protected than the DB.
- **Auth on Redis**: `requirepass`/ACL, TLS, no public binding. An open Redis is a breach
  waiting to happen (and a common one).
- **Disable dangerous commands** (`FLUSHALL`, `CONFIG`, `KEYS`) for app users via ACLs.
- **Avoid JDK deserialization** of cached bytes — RCE vector.
- **Cache poisoning**: if keys are predictable and the cache is shared across tenants,
  one tenant can poison another's entry. Namespace by tenant.

---

## 13. Best Practices Checklist

- [ ] Every cache has a documented **staleness budget** and a TTL that encodes it.
- [ ] Cache-aside + delete-on-write as the default; deviations justified.
- [ ] Keys namespaced and versioned (`domain:v1:id`).
- [ ] TTLs **jittered**; no synchronized expiry.
- [ ] Single-flight or early-refresh for hot keys.
- [ ] Negative caching for enumeration-prone lookups.
- [ ] Eviction policy is `allkeys-lru`/`allkeys-lfu`, never `noeviction` on a cache.
- [ ] Eviction happens **after** transaction commit.
- [ ] Cache errors degrade gracefully (fallback to DB), never propagate.
- [ ] Hit rate exported as a metric, with an alert on regression.
- [ ] Serialization is JSON/Protobuf, never JDK.
- [ ] Cache outage tested in staging.
- [ ] Bounded value sizes; no unbounded collections.

---

## 14. Exercises & Challenges

1. **Measure the hit-rate curve** — instrument `@Cacheable` and print effective latency at
   50/80/95/99% hit rates; confirm the 90→99% jump.
2. **Break the key** — remove `equals`/`hashCode` from a cached DTO and observe the hit rate
   collapse to 0.
3. **Reproduce the stampede** — cache a key with 1s TTL and fire 200 concurrent requests;
   count DB queries. Then add a lock and recount.
4. **Reproduce penetration** — request 10k random non-existent ids; watch DB QPS; add
   negative caching and bloom filter.
5. **Avalanche drill** — expire 10k keys at once by flushing the cache under load; measure
   DB saturation; add jitter and a local L1.
6. **Kill Redis** — stop the container while serving traffic; verify graceful degradation and
   recovery.
7. **Stale-read race** — force the reader/writer interleaving from §5.3 with a sleep and prove
   the stale value persists; fix with versioned keys.

---

## 15. Interview Questions & Model Answers

**Q1. Which caching strategy would you choose and why?**
> Cache-aside with delete-on-write and a TTL safety net. It degrades to plain DB reads if the
> cache is unavailable, it doesn't risk data loss like write-behind, and it only caches data
> that's actually read. Write-through only when the read-after-write pattern is dominant and
> writes are rare; write-behind only for reconstructable counters where losing a few seconds
> is acceptable.

**Q2. How do you invalidate the cache?**
> Delete the key on write — never update it, because two concurrent writers can leave the
> cache permanently holding the loser's value. Accept that a race can leave a stale entry,
> and bound it with a short TTL. For strict needs, version the key so stale writes land on a
> key nobody reads. And for multi-node local caches, publish invalidation events; that path
> is eventually consistent by nature.

**Q3. What is a cache stampede and how do you prevent it?**
> A hot key expires and every concurrent request misses simultaneously, all hitting the DB.
> Fix with single-flight loading (one recompute, others wait or serve stale), probabilistic
> early refresh, jittered TTLs, and refresh-ahead for keys known to be hot.

**Q4. Cache penetration vs avalanche?**
> Penetration is requests for non-existent keys that always miss — fixed with negative
> caching or a bloom filter. Avalanche is a mass expiry or a cache outage — fixed with TTL
> jitter, layered caches, warm-up, and back-pressure on the DB.

**Q5. How do you handle a cache outage?**
> Treat it as a performance incident, not an availability one: `CacheErrorHandler` swallows
> cache errors so calls fall through to the source, timeouts are aggressive (200 ms), a local
> L1 absorbs some traffic, and a DB circuit breaker sheds load if needed. We test it by
> killing Redis in staging.

**Q6. Write-through or write-behind?**
> Write-through keeps the cache consistent at the cost of write latency and caching
> write-only data. Write-behind is fastest but can lose writes on cache failure and needs a
> reliable flush — appropriate for counters or reconstructable aggregates, never for money.

**Q7. How do you size the cache?**
> `keys × (value + key + per-key overhead)`, plus 25–50% headroom for fragmentation and
> replication. Then verify with real hit rate: if `evicted_keys` is high and hit rate is
> falling, you're undersized or your keys are too granular.

**Q8. Why not just set a long TTL everywhere?**
> Long TTLs increase staleness and make invalidation bugs last longer, and they waste memory
> on cold data. TTL should encode the business's staleness budget per data type — product
> catalog 30 min, pricing 30 s, balance never.

---

## 16. Self-Check & Revision Card

- [ ] I can name the five strategies and pick one with justification.
- [ ] I can explain delete-vs-update on write with the race example.
- [ ] I can describe stampede, penetration and avalanche and their fixes.
- [ ] I know why `allkeys-lru` and not `noeviction`.
- [ ] I can explain the stale-read race and three ways to bound it.
- [ ] I can compute effective latency from a hit rate.
- [ ] I know why JDK serialization is a security problem.

```
┌──────────────────────── CACHING — 60s RECALL ────────────────────────────────┐
│ A cache is a COPY. Define the staleness budget or don't cache.               │
│                                                                              │
│ DEFAULT: cache-aside + DELETE on write + TTL. Never update the cache.        │
│         (two writers interleaving ⇒ permanent staleness)                     │
│                                                                              │
│ WRITES: through = safe/slow · behind = fast/lossy · around = safe/missy      │
│                                                                              │
│ FAILURES: stampede (hot key expiry) → single-flight + jitter                │
│           penetration (nonexistent keys) → negative cache + bloom filter    │
│           avalanche (mass expiry/outage) → jitter + L1 + warm-up            │
│                                                                              │
│ EVICTION: allkeys-lru/lfu for caches. noeviction = self-inflicted outage.    │
│ volatile-* only evicts keys that HAVE a TTL.                                 │
│                                                                              │
│ HIT RATE: 90→99% cuts DB load 10x. Key design > cache technology.           │
│                                                                              │
│ CACHE DOWN ≠ SERVICE DOWN. CacheErrorHandler + 200ms timeouts + L1.         │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Next:** [02 · Redis, Data Structures & Spring Cache](./02-Redis-Data-Structures-and-Spring-Cache.md)
