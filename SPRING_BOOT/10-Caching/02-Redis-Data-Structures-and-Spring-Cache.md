# 02 · Redis, Data Structures & Spring Cache

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★★ ("Which Redis structure would you use for X?")
> **Prerequisite:** [01 · Caching Fundamentals & Patterns](./01-Caching-Fundamentals-and-Patterns.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — Single-Threaded Command Executor](#2-mental-model--single-threaded-command-executor)
- [3. The Data Structures](#3-the-data-structures)
- [4. Choosing a Structure](#4-choosing-a-structure)
- [5. Persistence](#5-persistence)
- [6. Replication, Sentinel & Cluster](#6-replication-sentinel--cluster)
- [7. Transactions, Lua & Optimistic Locking](#7-transactions-lua--optimistic-locking)
- [8. Spring Data Redis & Spring Cache](#8-spring-data-redis--spring-cache)
- [9. Code Examples](#9-code-examples)
- [10. Common Mistakes & Gotchas](#10-common-mistakes--gotchas)
- [11. Performance](#11-performance)
- [12. Security](#12-security)
- [13. Best Practices Checklist](#13-best-practices-checklist)
- [14. Exercises & Challenges](#14-exercises--challenges)
- [15. Interview Questions & Model Answers](#15-interview-questions--model-answers)
- [16. Self-Check & Revision Card](#16-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> Redis is a **single-threaded command executor** with an in-memory data store of typed
> values. Every command is atomic; that's the whole concurrency model — no locks needed for
> a single command, and multi-command atomicity needs **Lua** or `MULTI/EXEC`.
>
> The structures and their one-line uses: **String** (cache, counter, lock), **Hash**
> (object fields, partial updates), **List** (queue/stack), **Set** (uniqueness, tags),
> **Sorted Set** (leaderboards, rate limits, delay queues by score), **Bitmap/HyperLogLog**
> (presence, cardinality), **Stream** (log with consumer groups).
>
> Choose by **access pattern**, not by convenience: if you always read the whole object, a
> JSON string is cheaper than a hash; if you update one field, a hash avoids rewriting the
> whole value.
>
> Persistence: **RDB** (snapshot — fast restart, may lose recent writes) and **AOF**
> (append-only log — durable, larger, slower). Production default is usually
> `appendonly yes` with `everysec`, plus RDB for backups.
>
> Cluster mode shards by **hash slot** (16384 slots) — this is why multi-key operations need
> **hash tags** (`{user:42}:profile`) to co-locate keys.

---

## 1. What & Why

### 1.1 What Redis actually is

Not "a cache." Redis is an **in-memory data structure server** that you can also use as a
cache, a lock service, a rate limiter, a queue, a leaderboard, and a pub/sub bus — all with
sub-millisecond latency and atomic single commands.

That breadth is exactly why interviews probe it: the wrong structure is 10x slower and 5x
more memory than the right one, and teams pick "string with JSON" for everything.

### 1.2 What it is not

| Redis is not… | Because |
|---|---|
| a durable database | persistence is best-effort; a `fork`/OOM/failover can lose the last writes |
| a message broker for critical events | Streams are decent, but no retention guarantees like Kafka |
| a general-purpose search engine | no secondary indexes, no joins, `SCAN` only |
| thread-safe for multi-key logic | single commands are atomic; multi-step logic isn't without Lua/WATCH |
| a good place for large blobs | values > 100 KB hurt latency, replication and memory |

**Rule of thumb:** Redis is a **derived store**. If you lost every key right now, the
system should recover from the source of truth — with a performance hit, not data loss.

---

## 2. Mental Model — Single-Threaded Command Executor

```
  clients ──► [ I/O multiplexing (epoll) ] ──► [ ONE command execution thread ] ──► data
                                                          │
                                                          ├─ every command is atomic
                                                          ├─ no command ever blocks mid-way
                                                          └─ a slow command stalls EVERYONE
```

Consequences you must be able to state:

1. **`KEYS *` is an outage.** It's O(N) and blocks the server. Use `SCAN` with a cursor.
2. **`DEL` of a big key blocks.** A 5M-member set takes seconds. Use `UNLINK` (background
   free).
3. **O(N) commands on big collections** (`SMEMBERS`, `HGETALL`, `LRANGE 0 -1`) are dangerous.
   Page them (`SSCAN`, `HSCAN`, `LRANGE` in chunks).
4. **No parallelism for CPU work** — Redis 6+ added I/O threads for network reads/writes, but
   command execution is still single-threaded.
5. **Atomicity is free for one command** — `INCR`, `SETNX`, `ZADD` need no lock.

**The one-liner:**

> *Redis is fast because it's simple: one thread, in-memory, no locks. It's fragile because
> one bad command blocks everything.*

---

## 3. The Data Structures

### 3.1 String

Binary-safe up to 512 MB. The universal workhorse.

```bash
SET product:42 '{"id":42,"name":"..."}' EX 600      # cache with TTL
SET lock:order:42 1 NX PX 30000                     # distributed lock
INCR page:views:2026-03-11                          # counter
SETBIT online:2026-03-11 12345 1                    # bitmap presence
SETRANGE / GETRANGE                                 # partial updates
```

**When:** whole-object caching, counters, locks, idempotency flags, session tokens.
**Watch out:** counter hot keys (`INCR` on one key) serialise on a single shard.

### 3.2 Hash

A map of field → value under one key. Ideal for objects with field-level access.

```bash
HSET user:42 name "Ada" email "ada@x.io" lastLogin "2026-03-11"
HGET user:42 email
HINCRBY user:42 loginCount 1
HSCAN user:42 0 COUNT 100          # never HGETALL on a big hash
EXPIRE user:42 3600                # TTL is per-key, not per-field
```

**When:** you update a few fields frequently, or you'd otherwise rewrite a large JSON blob.
**Saves memory** for small hashes (listpack encoding) — a hash of ≤ 128 fields is often
smaller than the equivalent JSON string plus its key overhead.

**When not:** you always read the whole object → a single JSON string is one round trip
instead of `HGETALL`.

### 3.3 List

Doubly-linked list; `LPUSH`/`RPOP` is O(1).

```bash
LPUSH queue:emails job1
BRPOP queue:emails 5                # blocking pop — a simple work queue
LRANGE feed:42 0 49                 # latest 50 items
LTRIM feed:42 0 999                 # keep only 1000 → a capped feed
```

**When:** simple queues, recent-items feeds, bounded logs (`LPUSH` + `LTRIM`).
**Not for:** reliable queues — a crash between pop and process loses the job (use Streams or
a real broker).

### 3.4 Set

Unordered unique members.

```bash
SADD tags:post:7 java spring
SINTER tags:post:7 tags:post:8      # posts with both tags
SISMEMBER seen:events 5b0f0a12      # idempotency check
SPOP lottery:tickets 10             # random winners
```

**When:** uniqueness, tags, set algebra (intersection/union), membership tests, dedupe.

### 3.5 Sorted Set (ZSet)

Members with a score; ordered by score, O(log N) inserts and range queries. **The most
underrated structure in interviews.**

```bash
ZADD leaderboard 100 alice
ZREVRANGE leaderboard 0 9 WITHSCORES         # top 10
ZINCRBY leaderboard 5 alice
ZRANGEBYSCORE delay-queue 0 1741690000000 LIMIT 0 10   # due jobs
ZADD rate:user:42 1741690000500 req1         # sliding window
ZREMRANGEBYSCORE rate:user:42 -inf 1741689999000       # prune old
```

**When:** leaderboards, **rate limiting (sliding window)**, **delayed/scheduled jobs**
(score = due timestamp), priority queues, "top N" queries, time-ordered indexes.

### 3.6 Bitmap & HyperLogLog

```bash
SETBIT active:2026-03-11 42 1        # 1 bit per user — 12.5 MB for 100M users (per day)
BITCOUNT active:2026-03-11
BITOP AND active:week active:mon active:tue ...    # weekly active = AND of dailies

PFADD uv:2026-03-11 user42           # HyperLogLog: 12 KB, ~0.81% error
PFCOUNT uv:2026-03-11
```

**When:** presence/DAU/retention (bitmaps), approximate unique counts at scale (HLL).
**Caveat:** HLL is approximate and can't be enumerated — never for billing.

### 3.7 Stream

An append-only log with consumer groups — the most Kafka-like structure.

```bash
XADD orders * orderId 42 event OrderPlaced
XGROUP CREATE orders inventory $ MKSTREAM
XREADGROUP GROUP inventory consumer-1 COUNT 10 BLOCK 5000 STREAMS orders '>'
XACK orders inventory <id>
XPENDING orders inventory          # unacked — your retry source
XAUTOCLAIM orders inventory 60000 0-0   # recover from a dead consumer
```

**When:** you need a durable-ish queue with acks and consumer groups, but don't want to run
Kafka. **When not:** retention guarantees, replay at scale, or cross-DC replication — that's
Kafka.

### 3.8 Geospatial & other

`GEOADD`/`GEOSEARCH` for radius queries; `JSON` module for path-level updates; `TS` for time
series. Only reach for modules if your managed Redis supports them.

---

## 4. Choosing a Structure

| Need | Structure | Cost note |
|---|---|---|
| Cache a whole object | String (JSON) | one round trip |
| Update one field of an object | Hash | avoids rewriting the blob |
| Counter / rate budget | String `INCR`, or ZSet for sliding window | ZSet is precise, more memory |
| Distributed lock | String + `SET NX PX` | see §7 |
| Idempotency / dedupe | Set + `SADD` (1 if new) | TTL on the set key |
| Top-N leaderboard | ZSet | O(log N) |
| Delayed job | ZSet (score = due time) | poll with `ZRANGEBYSCORE` |
| Recent items feed | List + `LTRIM` | capped size |
| Unique visitors | HyperLogLog | ~0.8% error, 12 KB |
| Daily active users | Bitmap | 1 bit/user/day |
| Reliable work queue | Stream + consumer group | more memory, more ops |
| Pub/sub fan-out | `PUBLISH`/`SUBSCRIBE` | **fire-and-forget, no durability** |

### The memory comparison that wins the question

Counting daily active users for 100M users:

| Approach | Memory |
|---|---|
| Set of user ids | ~100M × ~60 bytes ≈ **6 GB** |
| Bitmap (1 bit/user) | 100M / 8 ≈ **12.5 MB** |
| HyperLogLog | **~12 KB** (but approximate, can't intersect per-user) |

Two orders of magnitude — and that's the answer interviewers want.

---

## 5. Persistence

### 5.1 RDB (snapshot)

`fork()` a child that writes a point-in-time dump to `dump.rdb`.

- ✅ Compact, fast restart, cheap to ship to S3 for backups.
- ❌ Loses everything since the last snapshot. `fork` on a large dataset can cause latency
  spikes (copy-on-write memory doubling risk).

### 5.2 AOF (append-only file)

Every write command appended; replay on restart.

- `appendfsync always` — fsync per write: durable, very slow (~no throughput).
- `appendfsync everysec` — **the standard trade-off**: ≤1 s of loss, good throughput.
- `appendfsync no` — let the OS decide: fastest, unpredictable loss.
- **AOF rewrite** compacts the log (like a compaction). Under RDB-preamble mode (default
  since 4.0) the rewrite produces an RDB-prefix + incremental AOF.

### 5.3 Choosing

| Use case | Config |
|---|---|
| Pure cache, loss acceptable | RDB off, AOF off — fastest, smallest |
| Sessions / locks / rate limits | AOF `everysec` |
| "We'd rather not lose writes" | AOF `everysec` + RDB snapshots for backups |
| Anything financial | **not Redis** |

**Say this in an interview:** "Redis persistence is a durability *improvement*, not a
guarantee. Even with `appendfsync always`, a failover can lose writes that the replica hasn't
received — Redis replication is asynchronous."

### 5.4 Replication is asynchronous

```
client ──► master ──(async)──► replica
              │
              └── ack to client happens BEFORE the replica has the write
```

So `WAIT 1 100` (wait for 1 replica, 100 ms) reduces but doesn't eliminate the window. Any
design that needs strong durability across a failover must not rely on Redis alone.

---

## 6. Replication, Sentinel & Cluster

### 6.1 Replication

One master, N replicas, async. Replicas serve reads (with staleness) and can be promoted.

### 6.2 Sentinel

A quorum of sentinel processes monitors the master, and on failure **promotes a replica**
and reconfigures clients. Solves HA, **not** sharding — all data must fit on one node.

Spring Boot:

```yaml
spring:
  data:
    redis:
      sentinel:
        master: mymaster
        nodes: sentinel-1:26379,sentinel-2:26379,sentinel-3:26379
```

### 6.3 Cluster

Data is sharded across **16384 hash slots**; each master owns a slot range, each master has
replicas.

```
slot = CRC16(key) % 16384
```

Consequences:

- **Multi-key operations require keys in the same slot** → **hash tags**:
  `{user:42}:profile` and `{user:42}:sessions` hash on `user:42`, so they co-locate.
- `MGET` across slots fails with `CROSSSLOT`. Use hash tags, or pipeline per key.
- Multi-key Lua scripts must only touch one slot.
- Resharding moves slots (and their keys) between nodes; clients get `MOVED`/`ASK`
  redirects. Lettuce/Jedis handle this transparently.
- `SELECT` (multiple DBs) isn't supported — use key prefixes.

### 6.4 Which topology?

| Need | Choice |
|---|---|
| < 25 GB, HA only | Sentinel |
| > single-node memory, or write throughput beyond one node | Cluster |
| Managed (ElastiCache/Memorystore/Upstash) | Whatever they offer; usually both |

---

## 7. Transactions, Lua & Optimistic Locking

### 7.1 `MULTI`/`EXEC`

Queues commands and executes them atomically — but **not** transactionally in the SQL sense:
no rollback on error, and you can't branch on intermediate results.

```bash
MULTI
INCR counter
EXPIRE counter 60
EXEC
```

If `INCR` fails (wrong type), `EXPIRE` still runs. There is no rollback.

### 7.2 `WATCH` — optimistic locking (CAS)

```java
redis.execute(new SessionCallback<>() {
  public Object execute(RedisOperations ops) {
    ops.watch(key);
    Integer v = (Integer) ops.opsForValue().get(key);
    ops.multi();
    ops.opsForValue().set(key, v + 1);
    return ops.exec();     // null if `key` changed since WATCH → retry
  }
});
```

Retry on `null`. Works, but under contention you retry a lot.

### 7.3 Lua — atomic compound logic (preferred)

A script runs atomically; nothing interleaves.

```lua
-- rate limiter: sliding window over a sorted set
local key    = KEYS[1]
local now    = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit  = tonumber(ARGV[3])

redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
local count = redis.call('ZCARD', key)
if count >= limit then return 0 end
redis.call('ZADD', key, now, now .. '-' .. math.random())
redis.call('PEXPIRE', key, window)
return 1
```

```java
@Bean
DefaultRedisScript<Long> rateLimitScript() {
    var s = new DefaultRedisScript<Long>();
    s.setLocation(new ClassPathResource("lua/rate_limit.lua"));
    s.setResultType(Long.class);
    return s;
}

public boolean allowed(String userId) {
    Long ok = redis.execute(script, List.of("rate:" + userId),
        String.valueOf(System.currentTimeMillis()), "60000", "100");
    return ok != null && ok == 1L;
}
```

**Rules for Lua in production:** keep scripts small (they block the server), avoid loops over
large collections, and remember **Cluster requires all keys in one slot** — use hash tags.

### 7.4 Optimistic locking with `SET NX` for locks

```java
// acquire: value must be unique so we only release our own lock
String token = UUID.randomUUID().toString();
Boolean got = redis.opsForValue().setIfAbsent("lock:" + key, token, Duration.ofSeconds(30));
...
// release with Lua compare-and-delete (never plain DEL)
String lua = "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end";
redis.execute(new DefaultRedisScript<>(lua, Long.class), List.of("lock:" + key), token);
```

Single-node Redis locks are "good enough" for efficiency (avoid duplicate work), **not**
correctness across failovers — for that you need Redlock (contested) or a fencing-token
design. Detailed treatment in [ch 03](./03-Cache-Consistency-Invalidation-and-Stampedes.md).

---

## 8. Spring Data Redis & Spring Cache

### 8.1 Two different abstractions

| | `RedisTemplate` | `CacheManager` / `@Cacheable` |
|---|---|---|
| Purpose | general Redis access | declarative caching |
| Control | full (structures, TTL per call) | limited (cache name + TTL) |
| Use | counters, locks, rate limits, queues | read-through caching of method results |

**Don't** try to do rate limiting with `@Cacheable`. **Don't** hand-roll caching with
`RedisTemplate` when `@Cacheable` fits — you'll reimplement key generation badly.

### 8.2 Lettuce vs Jedis

- **Lettuce** (default in Spring Boot 2+) — Netty-based, thread-safe, async/reactive,
  multiplexes one connection. **Use it.**
- **Jedis** — thread-per-connection, pool-based, simpler. Fine, but no async.

### 8.3 Serialization choices

```java
@Bean
RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf) {
    var t = new RedisTemplate<String, Object>();
    t.setConnectionFactory(cf);
    t.setKeySerializer(new StringRedisSerializer());          // readable keys
    t.setHashKeySerializer(new StringRedisSerializer());
    t.setValueSerializer(new GenericJackson2JsonRedisSerializer());
    t.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
    t.afterPropertiesSet();
    return t;
}
```

`GenericJackson2JsonRedisSerializer` writes a `@class` type hint. That's convenient but
couples the cache to your class names — renaming a package invalidates every entry. For
long-lived caches, use `Jackson2JsonRedisSerializer<T>` with a fixed type, or Protobuf.

### 8.4 Connection pooling and timeouts

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: 6379
      timeout: 200ms                       # command timeout — must be short
      connect-timeout: 500ms
      lettuce:
        pool:
          max-active: 32
          max-idle: 16
          min-idle: 4
          max-wait: 100ms
        shutdown-timeout: 200ms
```

**`timeout: 200ms` is the single most important line.** A hanging Redis without a timeout
will exhaust your Tomcat threads and take down the whole service — the worst kind of
cascading failure, caused by adding a cache.

---

## 9. Code Examples

### 9.1 Cache + explicit TTL control

```java
@Service
public class ProductCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public Optional<Product> get(Long id) {
        String json = redis.opsForValue().get("product:v1:" + id);
        if (json == null) return Optional.empty();
        try { return Optional.of(mapper.readValue(json, Product.class)); }
        catch (JsonProcessingException e) { redis.delete("product:v1:" + id); return Optional.empty(); }
    }

    public void put(Product p, Duration ttl) {
        try {
            redis.opsForValue().set("product:v1:" + p.id(), mapper.writeValueAsString(p), jitter(ttl));
        } catch (JsonProcessingException e) { log.warn("cache skip", e); }
    }

    /** ±10% jitter prevents synchronized expiry (avalanche). */
    private Duration jitter(Duration ttl) {
        long ms = ttl.toMillis();
        long delta = (long) (ms * 0.1 * (ThreadLocalRandom.current().nextDouble() * 2 - 1));
        return Duration.ofMillis(ms + delta);
    }
}
```

### 9.2 Counter with an atomic increment and TTL

```java
public long incrementAndExpire(String key, Duration ttl) {
    // atomic: INCR + set TTL only on first creation
    Long v = redis.execute((RedisCallback<Long>) c -> {
        byte[] k = key.getBytes(UTF_8);
        Long n = c.stringCommands().incr(k);
        if (n != null && n == 1L) c.keyCommands().expire(k, ttl.getSeconds());
        return n;
    });
    return v == null ? 0 : v;
}
```

### 9.3 Sliding-window rate limiter (Lua, single round trip)

```java
public boolean allow(String user, int limit, Duration window) {
    Long ok = redis.execute(RATE_LIMIT, List.of("rl:" + user),
        String.valueOf(System.currentTimeMillis()),
        String.valueOf(window.toMillis()),
        String.valueOf(limit));
    return Long.valueOf(1).equals(ok);
}
```

Advantages over a fixed-window `INCR`: no burst at the window boundary.

### 9.4 Distributed lock (safe release)

```java
public <T> T withLock(String key, Duration ttl, Supplier<T> work) {
    String token = UUID.randomUUID().toString();
    Boolean acquired = redis.opsForValue().setIfAbsent("lock:" + key, token, ttl);
    if (!Boolean.TRUE.equals(acquired)) throw new IllegalStateException("busy: " + key);
    try {
        return work.get();
    } finally {
        redis.execute(UNLOCK, List.of("lock:" + key), token);
    }
}
```

> For correctness under failover you need a fencing token (monotonic version checked by the
> resource). See ch 03.

### 9.5 Stream-based work queue

```java
// produce
redis.opsForStream().add(StreamRecords.newRecord()
    .in("orders").ofMap(Map.of("orderId", "42", "type", "OrderPlaced")));

// consume with a group
@Bean
StreamMessageListenerContainer<String, MapRecord<String, String, String>> container(
        RedisConnectionFactory cf) {
    var opts = StreamMessageListenerContainer
        .StreamMessageListenerContainerOptions.builder()
        .pollTimeout(Duration.ofSeconds(2)).build();
    var c = StreamMessageListenerContainer.create(cf, opts);
    c.receive(Consumer.from("inventory", "c1"),
              StreamOffset.create("orders", ReadOffset.lastConsumed()),
              msg -> {
                  handle(msg.getValue());
                  redis.opsForStream().acknowledge("orders", "inventory", msg.getId());
              });
    c.start();
    return c;
}
```

### 9.6 `@Cacheable` with a conditional and per-cache TTL

```java
@Cacheable(cacheNames = "product", key = "#id",
           condition = "#id > 0",
           unless = "#result == null || #result.discontinued()")
public Product find(Long id) { return repo.findById(id).orElse(null); }
```

Remember: `condition` is evaluated **before** the call (can we use the cache?), `unless`
**after** (should we store it?).

---

## 10. Common Mistakes & Gotchas

| Mistake | Consequence | Fix |
|---|---|---|
| `KEYS *` in app code | Blocks the server | `SCAN` with cursor, or maintain an index set |
| `DEL` on a huge key | Multi-second stall | `UNLINK` |
| `HGETALL` on a big hash | Blocks, huge payload | `HSCAN`, or split the hash |
| No command timeout | Thread pool exhaustion → full outage | `timeout: 200ms` |
| JDK serialization | Version fragility + RCE risk | JSON/Protobuf |
| Different TTL for the same key on different code paths | Unpredictable staleness | Centralise key + TTL definitions |
| Multi-key ops in Cluster without hash tags | `CROSSSLOT` errors | `{tag}:a`, `{tag}:b` |
| Storing sessions and cache in the same DB/instance | Cache flush logs everyone out | Separate logical instance or prefix |
| `MULTI/EXEC` expected to roll back | Partial application | Lua for real atomicity |
| Unbounded keys (no TTL) | Memory grows to `maxmemory` → eviction storm | TTL on every cache key |
| `maxmemory-policy noeviction` | Writes fail = outage | `allkeys-lru` |
| Redis as the source of truth | Data loss on failover | DB is truth |
| Hot key `INCR` on one shard | Single-slot bottleneck | Shard the counter, sum on read |
| Reusing one key for multiple concerns | Invalidation touches unrelated data | Namespaced keys per concern |

---

## 11. Performance

- **RTT is the cost, not the command.** Batch with pipelining/MGET/MSET; 100 GETs
  sequentially at 0.5 ms is 50 ms, pipelined is ~1 ms.
- **Use Lua to collapse multi-step logic** into one round trip when atomicity matters.
- **Compression**: for values > 1–2 KB, compressing before caching can cut memory and
  network. Below that, compression costs more than it saves.
- **Memory overhead per key** is ~50–80 bytes plus allocator fragmentation; millions of tiny
  keys are surprisingly expensive. Consolidate into hashes.
- **`fork` cost** for RDB grows with dataset size — a 20 GB instance can block for hundreds
  of ms. Use replicas for backups.
- **Big keys**: define a threshold (e.g. 10 KB) and alert on keys exceeding it
  (`redis-cli --bigkeys`).
- **Metrics**: `used_memory_rss`, `mem_fragmentation_ratio` (>1.5 = restart or defrag),
  `evicted_keys`, `keyspace_hits/misses`, `latency` percentiles, `connected_clients`.

---

## 12. Security

- **Never expose Redis to the internet.** Bind to a private network, use TLS (`rediss://`),
  and require auth (ACL users in Redis 6+).
- **ACLs**: give each service a user limited to its key prefix and the commands it needs:

```bash
ACL SETUSER order-svc on >secret ~order:* +@read +@write -@dangerous
```

- **Disable `FLUSHALL`, `CONFIG`, `KEYS`, `DEBUG`, `EVAL`** for application users where
  possible. `EVAL` is often needed, so at least restrict it to a known set of scripts
  (`SCRIPT LOAD` + `EVALSHA`).
- **No PII in cache keys** (keys show up in `MONITOR`, slowlog and metrics).
- **Encrypt sensitive values** at the application layer if the cache is shared across trust
  boundaries.
- **Audit slowlog** for commands that could be exfiltration attempts (`SCAN` sweeps).

---

## 13. Best Practices Checklist

- [ ] Structure chosen per access pattern, documented.
- [ ] Every cache key has a TTL (or a documented reason it doesn't).
- [ ] Keys namespaced: `service:entity:vN:id`.
- [ ] `timeout` set to ≤ 200 ms; pool sized and bounded.
- [ ] `maxmemory` set with `allkeys-lru` (or `allkeys-lfu`).
- [ ] No blocking commands (`KEYS`, big `DEL`, `HGETALL` on big hashes).
- [ ] Cluster-safe keys use hash tags for multi-key access.
- [ ] JSON/Protobuf serialization, never JDK.
- [ ] Lua for multi-step atomic logic; kept small.
- [ ] Locks use `SET NX PX` + compare-and-delete via Lua, with a fencing token where
      correctness matters.
- [ ] Persistence mode matches the data's value (AOF `everysec` for semi-durable).
- [ ] ACLs + TLS; no public exposure.
- [ ] Hit rate, evictions, memory and latency on a dashboard.

---

## 14. Exercises & Challenges

1. **Structure shootout** — store the same 1M-user "active today" data as a Set, a Bitmap and
   a HyperLogLog; compare memory with `MEMORY USAGE` and `INFO memory`.
2. **Blocking demo** — run `KEYS *` against a 1M-key instance while measuring p99 latency of a
   concurrent GET loop; then repeat with `SCAN`.
3. **Pipelining win** — fetch 1000 keys individually vs `MGET` vs pipeline; report timings.
4. **Lua rate limiter** — implement sliding-window and prove no burst at the boundary
   (compare with fixed-window `INCR`).
5. **Cluster hash tags** — in cluster mode, run `MGET` on `user:1`/`user:2` (fails) and on
   `{u1}:a`/`{u1}:b` (succeeds).
6. **Lock safety** — acquire a lock, expire it manually, then release with the old token;
   prove the Lua guard prevents deleting someone else's lock.
7. **Persistence test** — run with AOF `everysec`, `kill -9` the server under write load,
   restart, and count lost writes.

---

## 15. Interview Questions & Model Answers

**Q1. Why is Redis single-threaded, and what are the consequences?**
> Because a single thread removes lock contention and makes every command atomic, which is
> why `INCR`, `SETNX` and `ZADD` are safe without coordination. The consequence is that a
> slow O(N) command blocks every client, so you avoid `KEYS`, huge `DEL`s and full-collection
> reads. Redis 6+ uses threads for network I/O but keeps one execution thread.

**Q2. When would you use a hash instead of a JSON string?**
> When the access pattern is field-level — updating `lastSeen` shouldn't rewrite a 20 KB blob.
> A hash lets you `HSET` one field in O(1) and saves bandwidth. If every read needs the whole
> object, a single string is one round trip and simpler.

**Q3. How would you implement a rate limiter?**
> A sorted set per user with the request timestamp as the score: remove entries older than the
> window, count, and reject if over the limit — all in one Lua script for atomicity. For
> high-volume cases, a fixed-window counter with `INCR` + `EXPIRE` is cheaper but allows a
> 2x burst at the boundary.

**Q4. How would you build a leaderboard of 50M users?**
> A sorted set with the score as the ranking value: `ZADD` on score change, `ZREVRANGE 0 9`
> for the top 10, `ZREVRANK` for a user's position. Memory is roughly 50M × ~80 bytes ≈ 4 GB,
> so I'd shard by region or keep only the top N in Redis and the full table in the DB.

**Q5. RDB or AOF?**
> AOF with `everysec` for anything semi-durable — at most a second of loss with good
> throughput. RDB alone for pure caches or to speed up restart, plus RDB snapshots for
> offline backups. Anything financial shouldn't rely on Redis persistence at all, since
> replication is asynchronous and a failover can lose recent writes.

**Q6. How does Redis Cluster distribute keys?**
> 16384 hash slots, `CRC16(key) % 16384`, each master owns a range. Multi-key commands need
> all keys in one slot, so I use hash tags like `{user:42}:profile` to co-locate. Clients get
> `MOVED`/`ASK` redirects during resharding, which Lettuce handles automatically.

**Q7. How do you implement a distributed lock and what are its limits?**
> `SET key token NX PX ttl`, release with a Lua compare-and-delete so I never delete another
> holder's lock. That gives mutual exclusion most of the time, but a pause longer than the TTL
> (GC, network) lets two holders overlap. For correctness-critical sections I use a fencing
> token the resource validates, or a consensus store like ZooKeeper/etcd.

**Q8. What would you do if Redis memory keeps growing?**
> First check whether keys have TTLs — usually a missing-TTL bug. Then look at big keys and
> hot keys, then `mem_fragmentation_ratio`. Confirm `maxmemory-policy` is eviction-based, not
> `noeviction`. Long term: consolidate tiny keys into hashes, compress large values, and
> consider sharding.

---

## 16. Self-Check & Revision Card

- [ ] I can name all seven core structures and one use case each.
- [ ] I can explain why a slow O(N) command is an outage.
- [ ] I can pick hash vs string with a justification.
- [ ] I can explain cluster slots and hash tags.
- [ ] I can distinguish `MULTI/EXEC` from Lua from `WATCH`.
- [ ] I know the persistence trade-offs and their limits.
- [ ] I can write `SET NX PX` + safe release from memory.

```
┌────────────────────────── REDIS — 60s RECALL ────────────────────────────────┐
│ Single-threaded executor ⇒ every command atomic, ONE slow command = outage.  │
│ ⇒ ban KEYS, big DEL, HGETALL on big hashes. Use SCAN/UNLINK/HSCAN.           │
│                                                                              │
│ String=cache/counter/lock · Hash=field updates · List=queue/feed             │
│ Set=uniqueness · ZSet=leaderboard/rate-limit/delay-queue · Bitmap=DAU        │
│ HLL=approx cardinality · Stream=queue w/ acks                                │
│                                                                              │
│ 100M DAU: Set 6GB · Bitmap 12.5MB · HLL 12KB.                                │
│                                                                              │
│ CLUSTER: 16384 slots, CRC16, hash tags {tag}:a/{tag}:b for multi-key.        │
│ ATOMICITY: one command free · MULTI/EXEC no rollback · Lua = real atomic.    │
│                                                                              │
│ PERSIST: RDB=snapshots(fast restart, lossy) · AOF everysec=≤1s loss.         │
│ Replication is ASYNC ⇒ failover can lose writes. Redis ≠ durable DB.         │
│                                                                              │
│ ALWAYS: timeout 200ms · maxmemory + allkeys-lru · TTL everywhere · ACL+TLS.  │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [01 · Caching Fundamentals & Patterns](./01-Caching-Fundamentals-and-Patterns.md) · **Next:** [03 · Cache Consistency, Invalidation & Stampedes](./03-Cache-Consistency-Invalidation-and-Stampedes.md)
