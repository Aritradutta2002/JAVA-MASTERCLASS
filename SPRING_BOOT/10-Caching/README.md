# 10 — Caching

> **Level:** Intermediate → Expert
> **Interview Frequency:** ★★★★★ ("How do you invalidate the cache?" — every loop, always)
> **Prerequisite:** [09 · Messaging Systems](../09-Messaging-Systems/README.md)
> **Last Updated:** 2026

---

## 🎯 Why This Topic Is Separate

Caching is the highest-leverage performance change you can make and the easiest way to ship
a correctness bug. The interview is not about whether you know `@Cacheable`; it is about
whether you can:

- **Define a staleness budget** instead of promising consistency you can't deliver,
- **Pick a strategy** and defend the trade-off (aside vs through vs behind),
- **Explain the races** that make naive invalidation permanently wrong,
- **Name the three failure modes** — stampede, penetration, avalanche — and their fixes,
- **Operate it**: hit-rate metrics, warm-up, load shedding when the cache dies.

The single sentence that separates candidates: *"The cache is a copy; I bound how wrong it
can be and I have a metric that tells me when the bound is breached."*

---

## 📚 Chapters

| # | Chapter | Core Idea | Difficulty |
|---|---------|-----------|------------|
| 01 | [Caching Fundamentals & Patterns](./01-Caching-Fundamentals-and-Patterns.md) | Cache-aside/write-through/behind/around, eviction policies, invalidation, stampede/penetration/avalanche | ⭐⭐⭐ |
| 02 | [Redis, Data Structures & Spring Cache](./02-Redis-Data-Structures-and-Spring-Cache.md) | Single-threaded model, the seven structures, persistence, cluster slots, `RedisTemplate` vs `@Cacheable` | ⭐⭐⭐ |
| 03 | [Cache Consistency, Invalidation & Stampedes](./03-Cache-Consistency-Invalidation-and-Stampedes.md) | Bounded staleness, the four races, versioned keys, single-flight, distributed locks and fencing | ⭐⭐⭐⭐⭐ |
| 04 | [Multi-Level Caching & Production Operations](./04-Multi-Level-Caching-and-Production-Operations.md) | HTTP/CDN caching, Caffeine, two-tier design, warm-up, observability, runbooks | ⭐⭐⭐⭐ |

---

## 🧠 The One Mental Model That Covers Everything

```text
        A CACHE IS A COPY. THE ONLY QUESTION IS: HOW WRONG MAY IT BE?

   ┌──────────────────────────────────────────────────────────────┐
   │   business says: "prices must be right within 30s"           │
   │                        │                                     │
   │                        ▼                                     │
   │   TTL ≤ 30s   ← this is the WORST-CASE bug bound,             │
   │                  not a random tuning number                  │
   │                                                              │
   │   + delete on write   → makes it right normally (~µs)        │
   │   + evict after commit→ makes it never repopulate stale      │
   │   + versioned key     → makes the residual race harmless     │
   └──────────────────────────────────────────────────────────────┘
```

**And the economics that justify all of it:**

```text
   hit rate    DB load vs uncached
    50%              50%
    90%              10%
    99%               1%     ← 90→99 matters 10x more than 0→50
```

Key design and TTL tuning beat cache technology every time.

---

## 📊 The Design Decision Matrix

| Decision | Default | Alternative | Trade-off |
|---|---|---|---|
| **Strategy** | Cache-aside | Write-through / write-behind | Simplicity vs write latency |
| **On write** | **Delete** | Update | Delete has a bounded race; update can be permanent |
| **Eviction timing** | After commit | Before | Correctness |
| **Race handling** | Short TTL | Versioned keys | Simplicity vs correctness |
| **Eviction policy** | `allkeys-lru` | `allkeys-lfu` | General vs stable hot set |
| **Serialization** | JSON | Protobuf | Readability vs size/speed |
| **Topology** | Sentinel | Cluster | HA vs horizontal scale |
| **Tiers** | Redis only | Caffeine + Redis | Simplicity vs sub-ms latency |
| **Stampede fix** | Single-flight | XFetch / refresh-ahead | Determinism vs smoothness |
| **Penetration fix** | Negative caching | Bloom filter | Memory vs precision |
| **Local invalidation** | Short TTL | Pub/sub broadcast | Simplicity vs immediacy |
| **HTTP caching** | `max-age` + ETag | No caching | Free performance left on the table |

---

## ⚠️ The Traps (the ones that cost people interviews)

| Trap | Wrong answer | Right answer |
|---|---|---|
| "Cache and DB are consistent" | "We use `@CacheEvict`" | They **can't be**; you bound the window with TTL |
| "Update the cache on write" | "Yes, keeps it warm" | Two writers can make it **permanently** wrong — delete |
| "TTL is just a memory thing" | "Yes, for eviction" | TTL is the **staleness ceiling** when invalidation fails |
| "Evict then update the DB" | "Order doesn't matter" | A reader repopulates from the **uncommitted** value |
| "Redis is a database" | "It has persistence" | Replication is **async**; failover can lose writes |
| "`KEYS *` is fine in a script" | "It's quick" | It's O(N) and **blocks the single thread** — outage |
| "`no-cache` means don't cache" | "Yes" | `no-cache` = revalidate; `no-store` = don't store |
| "A distributed lock makes it safe" | "Yes, with TTL" | A GC pause past the TTL gives **two writers**; need fencing |
| "Hit rate doesn't need monitoring" | "It just works" | Silent regressions to 0% are common and invisible |
| "Cache outage = service outage" | "Yes, it's a dependency" | It must be **degraded performance**, not downtime |
| "Flush the cache on deploy" | "Clean slate" | Avalanche you caused; **version the keys** |
| "Same TTL for everything" | "Simpler" | Synchronized expiry = avalanche; jitter |
| "JDK serialization is fine" | "It's built in" | Version fragility + **RCE** risk |
| "Set for 100M DAU" | "It's a set" | ~6 GB vs **12.5 MB** bitmap vs 12 KB HLL |

---

## 🎤 The 20 Questions to Expect

| # | Question | Where |
|---|---|---|
| 1 | Which caching strategy would you use? | [Ch 01](./01-Caching-Fundamentals-and-Patterns.md) |
| 2 | How do you invalidate the cache? | [Ch 01](./01-Caching-Fundamentals-and-Patterns.md), [Ch 03](./03-Cache-Consistency-Invalidation-and-Stampedes.md) |
| 3 | What is a cache stampede and how do you fix it? | [Ch 01](./01-Caching-Fundamentals-and-Patterns.md), [Ch 03](./03-Cache-Consistency-Invalidation-and-Stampedes.md) |
| 4 | Cache penetration vs avalanche? | [Ch 03](./03-Cache-Consistency-Invalidation-and-Stampedes.md) |
| 5 | Which eviction policy, and why? | [Ch 01](./01-Caching-Fundamentals-and-Patterns.md) |
| 6 | Write-through vs write-behind? | [Ch 01](./01-Caching-Fundamentals-and-Patterns.md) |
| 7 | How do you size the cache? | [Ch 01](./01-Caching-Fundamentals-and-Patterns.md), [Ch 04](./04-Multi-Level-Caching-and-Production-Operations.md) |
| 8 | Why is Redis single-threaded? | [Ch 02](./02-Redis-Data-Structures-and-Spring-Cache.md) |
| 9 | Hash or JSON string for an object? | [Ch 02](./02-Redis-Data-Structures-and-Spring-Cache.md) |
| 10 | How would you build a rate limiter? | [Ch 02](./02-Redis-Data-Structures-and-Spring-Cache.md) |
| 11 | How would you build a leaderboard? | [Ch 02](./02-Redis-Data-Structures-and-Spring-Cache.md) |
| 12 | RDB or AOF? | [Ch 02](./02-Redis-Data-Structures-and-Spring-Cache.md) |
| 13 | How does Redis Cluster shard keys? | [Ch 02](./02-Redis-Data-Structures-and-Spring-Cache.md) |
| 14 | How do you implement a distributed lock? | [Ch 03](./03-Cache-Consistency-Invalidation-and-Stampedes.md) |
| 15 | Is Redlock safe? | [Ch 03](./03-Cache-Consistency-Invalidation-and-Stampedes.md) |
| 16 | How do you get read-your-writes for a user? | [Ch 03](./03-Cache-Consistency-Invalidation-and-Stampedes.md) |
| 17 | How would you cache a catalog at 50k RPS? | [Ch 04](./04-Multi-Level-Caching-and-Production-Operations.md) |
| 18 | What `Cache-Control` would you set? | [Ch 04](./04-Multi-Level-Caching-and-Production-Operations.md) |
| 19 | Redis is down — what happens? | [Ch 04](./04-Multi-Level-Caching-and-Production-Operations.md) |
| 20 | How do you know the cache is working? | [Ch 04](./04-Multi-Level-Caching-and-Production-Operations.md) |

---

## 🛠️ The Code You'll Actually Write

| Class / Component | What it does | Where |
|---|---|---|
| `@EnableCaching` + `RedisCacheManager` | Declarative caching with per-cache TTLs | [Ch 01](./01-Caching-Fundamentals-and-Patterns.md) |
| `@Cacheable` / `@CacheEvict` / `@CachePut` | Read-through, invalidation, write-through | [Ch 01](./01-Caching-Fundamentals-and-Patterns.md) |
| `CacheErrorHandler` | Cache failure → fallback to source | [Ch 01](./01-Caching-Fundamentals-and-Patterns.md) |
| `RedisTemplate` / `StringRedisTemplate` | Counters, locks, rate limits, streams | [Ch 02](./02-Redis-Data-Structures-and-Spring-Cache.md) |
| `DefaultRedisScript` (Lua) | Atomic rate limiting and lock release | [Ch 02](./02-Redis-Data-Structures-and-Spring-Cache.md) |
| `StreamMessageListenerContainer` | Durable queue semantics on Redis | [Ch 02](./02-Redis-Data-Structures-and-Spring-Cache.md) |
| `TransactionSynchronization.afterCommit` | Evict only after the DB commits | [Ch 03](./03-Cache-Consistency-Invalidation-and-Stampedes.md) |
| `VersionedCache` | Race-free invalidation via key versions | [Ch 03](./03-Cache-Consistency-Invalidation-and-Stampedes.md) |
| Single-flight loader | Stampede prevention | [Ch 03](./03-Cache-Consistency-Invalidation-and-Stampedes.md) |
| `Caffeine.newBuilder()` | L1 with W-TinyLFU, refresh-ahead, stats | [Ch 04](./04-Multi-Level-Caching-and-Production-Operations.md) |
| `CacheControl` + `ETag` | HTTP/CDN caching | [Ch 04](./04-Multi-Level-Caching-and-Production-Operations.md) |
| `MeterBinder` for cache stats | Hit rate/eviction metrics | [Ch 04](./04-Multi-Level-Caching-and-Production-Operations.md) |
| Warm-up `ApplicationRunner` + readiness gate | Cold-start protection | [Ch 04](./04-Multi-Level-Caching-and-Production-Operations.md) |

---

## 🔗 The Spring Boot 3 Setup (the minimum)

```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 10m
      cache-null-values: false
  data:
    redis:
      timeout: 200ms                  # THE most important line
      lettuce:
        pool:
          max-active: 32
          max-wait: 100ms
```

```java
@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        var base = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(SerializationPair.fromSerializer(
                new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(cf)
            .cacheDefaults(base)
            .withCacheConfiguration("product", base.entryTtl(Duration.ofMinutes(30)))
            .withCacheConfiguration("price",   base.entryTtl(Duration.ofSeconds(30)))
            .transactionAware()            // evict after commit
            .build();
    }

    @Bean
    CacheErrorHandler cacheErrorHandler() {
        return new SimpleCacheErrorHandler();   // swallow cache errors → fall back to DB
    }
}
```

---

## 📐 The Reference Architecture

```text
   client
     │  Cache-Control: max-age=60
     ▼
  ┌──────┐   miss   ┌──────────┐   miss   ┌───────────┐   miss   ┌──────┐
  │ CDN  │─────────►│ gateway  │─────────►│ Caffeine  │─────────►│Redis │
  │ 5 min│          │ 30 s     │          │ L1 (60 s) │          │L2 10m│
  └──────┘          └──────────┘          └───────────┘          └──┬───┘
      ▲                                       │                    │
      │                                       └── single-flight ───┤
      │                                                            ▼
      │                                                         ┌──────┐
      │                                                         │  DB  │
      │                                                         └──┬───┘
      │                                                            │
      │        write: DB → commit → DEL L2 → broadcast evict ──────┘
      │
      └── stale-if-error: during an incident the CDN serves the last good copy
```

**Rules the diagram encodes:**
- Every layer has a **longer TTL** than the one below it.
- A **miss at every layer** is single-flighted so one request fills the cache.
- Writes go **DB → commit → evict L2 → broadcast **, never the other way round.
- Cache failures **never** propagate to the caller.

---

## ✅ The Self-Check

- [ ] I can name a staleness budget for a given entity and derive the TTL.
- [ ] I can explain cache-aside vs write-through vs write-behind vs write-around.
- [ ] I can explain why delete beats update with a concrete interleaving.
- [ ] I can name all four races and a fix for each.
- [ ] I can explain single-flight, XFetch, refresh-ahead, and jitter.
- [ ] I can distinguish stampede, penetration and avalanche.
- [ ] I can pick Redis structures by access pattern, not convenience.
- [ ] I know why Redis's single thread bans `KEYS` and big `DEL`s.
- [ ] I can explain cluster hash tags.
- [ ] I can write `SET NX PX` + Lua release, and explain fencing.
- [ ] I can set correct `Cache-Control` and avoid `Vary: Authorization` leaks.
- [ ] I can size Caffeine and Redis with rough numbers.
- [ ] I can describe warm-up, hit-rate alerting and a cache-outage runbook.

---

## 🧪 The Coding Drill

- [ ] Instrument `@Cacheable` and print effective latency at 50/80/95/99% hit rates.
- [ ] Remove `equals`/`hashCode` from a cached DTO and watch the hit rate hit zero.
- [ ] Fire 500 concurrent requests at an expired hot key: count DB queries, then add
      single-flight and recount.
- [ ] Request 10k non-existent ids and measure DB QPS; add negative caching.
- [ ] Kill Redis under load and verify degraded-but-working behaviour.
- [ ] Implement versioned keys and prove a stale write is unreachable.
- [ ] Implement a Lua sliding-window rate limiter and prove no boundary burst.
- [ ] Store 1M "active today" users as Set vs Bitmap vs HyperLogLog and compare memory.
- [ ] Set `Cache-Control` on a controller and verify a `304` on the second request.
- [ ] Add a warm-up runner and measure DB QPS at deploy time with and without it.

---

**Related folders:** ← [09 · Messaging Systems](../09-Messaging-Systems/README.md) · → [11 · Database Design & Optimisation](../11-Database-Design-Optimization/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
