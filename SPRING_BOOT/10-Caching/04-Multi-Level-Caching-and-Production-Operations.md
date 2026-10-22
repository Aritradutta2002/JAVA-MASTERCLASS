# 04 · Multi-Level Caching & Production Operations

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★☆ ("How would you cache this at scale?")
> **Prerequisite:** [03 · Cache Consistency, Invalidation & Stampedes](./03-Cache-Consistency-Invalidation-and-Stampedes.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. Why Layers](#1-why-layers)
- [2. Mental Model — The Layer Budget](#2-mental-model--the-layer-budget)
- [3. HTTP, Browser & CDN Caching](#3-http-browser--cdn-caching)
- [4. Local Cache (Caffeine) in Depth](#4-local-cache-caffeine-in-depth)
- [5. Shared Cache (Redis) in Depth](#5-shared-cache-redis-in-depth)
- [6. Putting Layers Together](#6-putting-layers-together)
- [7. Cache Warming, Preloading and Deploys](#7-cache-warming-preloading-and-deploys)
- [8. Observability](#8-observability)
- [9. Failure Modes & Recovery Runbook](#9-failure-modes--recovery-runbook)
- [10. Capacity Planning & Cost](#10-capacity-planning--cost)
- [11. Code Examples](#11-code-examples)
- [12. Common Mistakes & Gotchas](#12-common-mistakes--gotchas)
- [13. Interview Questions & Model Answers](#13-interview-questions--model-answers)
- [14. Self-Check & Revision Card](#14-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> Layers exist because each one trades staleness for distance: browser → CDN → gateway →
> in-process (Caffeine) → Redis → DB. Each hop is ~10x further and ~10x slower; each hop down
> should be hit by ~10x fewer requests.
>
> **Cache-Control is the cheapest performance work you will ever do.** `max-age` +
> `stale-while-revalidate` + `ETag` can remove 80% of traffic before it reaches your JVM. Most
> Spring apps never set it.
>
> Two-tier (Caffeine + Redis) is the standard for hot reads: sub-millisecond L1, shared
> consistency in L2, DB as truth. The cost is **invalidation fan-out** and **memory
> duplication**.
>
> Production discipline is the part everyone skips: **hit-rate metrics per cache**, **staleness
> sampling**, **warm-up on deploy**, **load-shedding when the cache dies**, and a **runbook**
> that says exactly what to do when Redis is down. A cache without these is a liability with a
> nice latency graph.

---

## 1. Why Layers

A single Redis cache still costs a network round trip — 0.5 ms in-AZ, but 0.5 ms × 50k QPS is
25 CPU-seconds of pure waiting, plus connection pool pressure. An in-process cache is ~50 ns.
A CDN is ~0 ms from the edge and takes load off everything.

Each layer also has a distinct failure profile:

| Layer | Latency | Shared? | Survives deploy? | Failure impact |
|---|---|---|---|---|
| Browser | ~0 | no | yes | none (client-side) |
| CDN | ~5–50 ms | yes | yes | origin gets full traffic |
| Gateway | ~1 ms | yes | yes | minor |
| Caffeine (JVM) | ~50 ns | **no** | **no** | none (empty on restart) |
| Redis | ~0.5–1 ms | yes | yes | DB load spike |
| DB buffer pool | ~100 µs | n/a | yes | n/a |

**The layering principle:** the closer to the user, the longer the TTL and the more staleness
tolerated. A product page at the CDN for 5 minutes is fine. A balance is never cached anywhere
shared.

---

## 2. Mental Model — The Layer Budget

```
   1,000,000 requests/min
        │
        ▼
   ┌─────────┐  95% hit  →    50,000 reach origin
   │   CDN   │            (static assets, product pages, public APIs)
   └────┬────┘
        ▼
   ┌─────────┐  80% hit  →    10,000 reach the service
   │ Gateway │            (short-TTL response cache for anonymous traffic)
   └────┬────┘
        ▼
   ┌─────────┐  90% hit  →     1,000 reach Redis
   │Caffeine │            (per-node hot set, 30s)
   └────┬────┘
        ▼
   ┌─────────┐  95% hit  →        50 reach the DB
   │  Redis  │            (shared, 10min)
   └────┬────┘
        ▼
      ┌────┐
      │ DB │  50 queries/min instead of 1,000,000
      └────┘
```

**Budget rule:** if a layer's hit rate is below ~70%, it's probably costing more (memory,
invalidation complexity, staleness bugs) than it saves. Measure, then delete the layer.

**And the trap:** multiplying hit rates is not multiplicative. 90% at L1 and 90% at L2 gives
99% overall *only if L1 misses are uncorrelated with L2 misses* — which they are, mostly, but
a cold start or a hot-key pattern can align them. Assume worse than the product.

---

## 3. HTTP, Browser & CDN Caching

### 3.1 The headers that matter

| Header | Meaning |
|---|---|
| `Cache-Control: max-age=N` | fresh for N seconds |
| `s-maxage=N` | same, for shared caches (CDN) |
| `stale-while-revalidate=N` | serve stale for N s while revalidating in background |
| `stale-if-error=N` | serve stale if the origin errors (free resilience) |
| `no-store` | never store (balances, tokens) |
| `no-cache` | store but revalidate every time (misleading name) |
| `private` / `public` | browser-only vs shared |
| `ETag` + `If-None-Match` | conditional request → `304` (no body) |
| `Vary` | key the cache on headers (`Accept-Encoding`, `Authorization`) |

```java
@GetMapping("/products/{id}")
ResponseEntity<Product> get(@PathVariable Long id) {
    Product p = service.get(id);
    return ResponseEntity.ok()
        .eTag("\"" + p.version() + "\"")
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))
            .sMaxAge(Duration.ofMinutes(30))
            .staleWhileRevalidate(Duration.ofMinutes(10))
            .cachePublic())
        .body(p);
}
```

Spring handles `If-None-Match` automatically for `ResponseEntity` with an ETag —
`ShallowEtagHeaderFilter` can add ETags globally, but explicit versions are better than
hashing the body.

### 3.2 The rules people get wrong

- **`no-cache` ≠ don't cache.** It means "revalidate before use." `no-store` is "don't store."
  Getting this backwards either leaks private data or kills your hit rate.
- **`Vary: Authorization`** — if you cache authenticated responses without varying on the
  credential, one user's response is served to another. Never cache authenticated responses
  in a shared cache without `private` or a per-user key.
- **Version the URL for immutable assets** — `/static/app.abc123.js` with
  `max-age=31536000, immutable`. Filename hashing makes infinite TTLs safe.
- **HTML should not be cached long** unless you use a short `max-age` + `stale-while-revalidate`.
- **`stale-if-error` is free availability** — during a deploy or DB hiccup, the CDN serves the
  last good copy instead of an error page.

### 3.3 Cache invalidation at the edge

- **Purge by URL** — precise, but you must know every URL that contains the changed data
  (including list pages and API responses).
- **Surrogate keys / cache tags** — tag responses with entity ids, purge by tag. The right
  answer for "invalidate everything containing product 42."
- **Versioned URLs** — `?v=17` or `/v17/products/42`. No purge needed; old entries expire.
- **Short TTL** — the blunt instrument that always works.

---

## 4. Local Cache (Caffeine) in Depth

### 4.1 Why Caffeine

W-TinyLFU gives near-optimal hit rates, and it's lock-free on the read path with
`ConcurrentHashMap` under the hood, so a hit is ~50 ns.

```java
Cache<Long, Product> cache = Caffeine.newBuilder()
    .maximumSize(50_000)                              // bound by count
    // .maximumWeight(64 * 1024 * 1024)                // or by bytes
    // .weigher((Long k, Product v) -> estimateSize(v))
    .expireAfterWrite(Duration.ofMinutes(10))          // hard staleness bound
    .refreshAfterWrite(Duration.ofMinutes(2))          // stale-while-revalidate
    .recordStats()                                     // hit rate metrics
    .removalListener((k, v, cause) -> log.debug("evicted {} {}", k, cause))
    .build();
```

### 4.2 The rules

1. **Always bound it.** `maximumSize` or `maximumWeight`. An unbounded local cache is an OOM
   scheduled for peak traffic.
2. **`expireAfterWrite` is your staleness ceiling.** `refreshAfterWrite` alone never removes a
   key, so a stale entry can live forever if it keeps being read.
3. **Never cache user-specific data in a large local cache** — memory per node multiplies by
   the fleet size, and invalidation becomes impossible.
4. **Handle the cold start.** A deploy gives you 20 empty caches; that's a DB spike unless you
   warm up or ramp traffic gradually.
5. **Invalidate across nodes** — pub/sub or a short TTL ([ch 03](./03-Cache-Consistency-Invalidation-and-Stampedes.md)).

### 4.3 Sizing

```
memory ≈ maximumSize × (avgValueSize + avgKeySize + ~100 bytes overhead)
```

50k products × 2 KB ≈ 100 MB + overhead ≈ 120–140 MB per JVM. On a 2 GB container that's a
lot — use `maximumWeight` with a memory estimate, and leave headroom for the heap.

### 4.4 Caffeine vs Guava vs EHCache

| | Caffeine | Guava | Ehcache |
|---|---|---|---|
| Policy | W-TinyLFU | LRU-ish segmented | many |
| Async refresh | yes | no | yes |
| Off-heap / disk tiers | no | no | **yes** |
| Stats | detailed | basic | detailed |
| Verdict | **default choice** | legacy | when you need disk/off-heap tiers |

---

## 5. Shared Cache (Redis) in Depth

### 5.1 Topology choices revisited

| Need | Topology | Note |
|---|---|---|
| HA, < 25 GB | Sentinel | failover in seconds; client must handle retry |
| Horizontal scale | Cluster | hash tags for multi-key |
| Managed | whatever the provider gives | check eviction policy and maxmemory defaults |
| Read-heavy, tolerant | primary + replicas, reads on replicas | stale reads possible |

### 5.2 Client-side configuration that saves you

```yaml
spring:
  data:
    redis:
      timeout: 200ms
      connect-timeout: 500ms
      lettuce:
        pool: { max-active: 32, max-idle: 16, min-idle: 4, max-wait: 100ms }
        cluster:
          refresh:
            adaptive: true            # react to MOVED/ASK
            period: 30s
```

- **`timeout: 200ms`** — without it, a network hiccup hangs threads and takes down the app.
- **`max-wait`** — a bounded wait means a saturated pool fails fast instead of queueing.
- **Retries** — one retry at most, with a short backoff. Retrying a slow cache three times
  triples your p99.

### 5.3 Cache availability ≠ app availability

The hierarchy of behaviour when Redis is degraded:

```
1. Serve from L1 (if you have one)           → near-normal latency for hot keys
2. Fall through to the DB                    → slower but correct
3. Circuit breaker on the DB                 → shed load, return 503/partial
4. Serve stale (if you kept a local copy)    → degraded but available
```

Most teams stop at 2 and hit 3 by accident. Deciding 3 **in advance** (which endpoints shed,
what a shed response looks like) is the difference between a degraded hour and an outage.

---

## 6. Putting Layers Together

### 6.1 Read path

```
request
  │
  ├─► CDN / gateway cache ──hit──► return
  │
  ├─► L1 (Caffeine) ─────────hit──► return (note: may be slightly stale)
  │
  ├─► L2 (Redis) ────────────hit──► populate L1, return
  │
  └─► DB ─────────────────────────► populate L2 + L1, return
              │
              └─ single-flight so only one request does this per key
```

### 6.2 Write path

```
write request
  │
  ├─► DB transaction
  │
  ├─► afterCommit: Redis DEL (or version bump)
  │
  └─► publish invalidation event → every node evicts from Caffeine
```

Order matters: **DB → commit → evict L2 → broadcast L1**. Reversing any of these creates a
window where a reader repopulates from stale data.

### 6.3 When two tiers hurt

- **Two staleness windows** instead of one — the user can see data that's 30 s (L1) + 10 min
  (L2) old.
- **Invalidation complexity** — you now need a bus, and a missed message means a stale L1.
- **Memory duplication** — N nodes × local heap.

**Only add L1 when the numbers justify it:** Redis p99 is a latency problem for you, or a hot
key is overloading one shard. Otherwise the single L2 tier is simpler and almost as fast.

---

## 7. Cache Warming, Preloading and Deploys

### 7.1 The cold-start problem

Every deploy creates N empty local caches. If your read path is uncached, the DB sees a spike
proportional to fleet size. Symptoms: a latency bump exactly at deploy time, sometimes an
outage on a big deploy.

Fixes:
- **Warm up in an `ApplicationRunner`** from a known hot set, before readiness returns true.
- **Ramp traffic** — Kubernetes `maxSurge`/`maxUnavailable` plus a readiness gate that waits
  for warm-up.
- **Keep L2 warm** — Redis survives deploys, so only L1 is cold. Another reason to have L2.
- **Precompute** — a nightly job writes the hot set into Redis (`SET` with TTL) so the first
  users never miss.

```java
@Component
class Warmup implements ApplicationRunner {
    private final HotKeyRepository hot;
    private final ProductCache cache;

    public void run(ApplicationArguments args) {
        hot.topProductIds(5_000).forEach(id -> cache.get(id));   // populate, ignore failures
        log.info("cache warm-up complete");
    }
}
```

### 7.2 Deploying a cache-key change

Version the key, don't flush:
`product:v1:42` → `product:v2:42`. Old entries expire on their own; there's no flush-induced
stampede and you can roll back safely.

Flushing is a last resort and should be rate-limited — `FLUSHALL` under load is an
avalanche you caused yourself.

### 7.3 Backfills

To populate a new cache, iterate the source in pages with a **dedicated low-priority
process**, not in the request path. Throttle it and watch DB load; a backfill and peak traffic
are the same thing as far as the DB is concerned.

---

## 8. Observability

### 8.1 The metrics that matter

| Metric | Why | Alert |
|---|---|---|
| **Hit rate per cache** | the entire point of caching | drop > 10 points in 10 min |
| `cache.gets` / `cache.puts` by result | hit/miss split | — |
| cache operation latency p99 | a slow cache is worse than no cache | p99 > 10 ms |
| `evicted_keys` | undersized or no TTLs | spike |
| `used_memory` vs `maxmemory` | headroom | > 85% |
| `keyspace_hits/misses` (Redis `INFO`) | server-side sanity vs client-side | — |
| `connected_clients`, `rejected_connections` | pool exhaustion | any rejection |
| staleness sample (cached `updatedAt` vs DB) | the real correctness signal | p99 > SLA |
| DB QPS correlated with hit rate | proves the cache is doing its job | inverse correlation |

```java
@Bean
MeterBinder cacheMetrics(CacheManager cacheManager) {
    return registry -> cacheManager.getCacheNames().forEach(name -> {
        var native_ = (CaffeineCache) cacheManager.getCache(name);
        var stats = native_.getNativeCache().stats();
        Gauge.builder("cache.hit.ratio", stats, s -> s.hitRate())
             .tag("cache", name).register(registry);
    });
}
```

### 8.2 The three dashboards

1. **Effectiveness** — hit rate + effective latency + DB QPS, on one graph.
2. **Health** — Redis memory, evictions, latency, client pool, rejected connections.
3. **Correctness** — staleness samples, invalidation lag (publish → evict), negative-cache age.

### 8.3 Tracing

A cache hit should be visible as a span attribute, not a guess:

```java
Observation.createNotStarted("cache.lookup", registry)
    .lowCardinalityKeyValue("cache", "product")
    .highCardinalityKeyValue("key", String.valueOf(id))
    .observe(() -> load(id));
```

If p99 is bad, the trace tells you instantly whether you're missing or the cache itself is
slow. Without it, you're guessing.

---

## 9. Failure Modes & Recovery Runbook

| Symptom | Likely cause | Immediate action | Long-term |
|---|---|---|---|
| Latency spike, DB CPU high, hit rate flat | cache keys changed / TTL too short | check deploy diff, restore key prefix | version keys, add tests for hit rate |
| Latency spike after deploy | cold caches | scale out / ramp slower | warm-up gate |
| `OOM command not allowed` | `maxmemory` reached with `noeviction` | switch to `allkeys-lru`, evict | add TTLs, size correctly |
| p99 cache calls > 50 ms | network/oversized values/hot shard | check big keys, `SLOWLOG` | compress, split keys, reshard |
| Thread pool exhaustion | no command timeout | set `timeout: 200ms` immediately | circuit breaker around cache |
| Stale data reports | invalidation path broken | flush by prefix **carefully** | versioned keys, staleness metric |
| Eviction storm | memory pressure | increase memory or reduce TTL | `allkeys-lfu`, split by workload |
| One shard saturated | hot key in Cluster | local L1 for that key | key replication with suffixes |

### The runbook discipline

Write it down **before** the incident: who owns the cache, how to identify it (which dashboard),
the first three commands to run, how to shed load, and how to communicate. A cache incident is
usually diagnosed in ten minutes and mitigated badly because nobody pre-decided.

---

## 10. Capacity Planning & Cost

**Redis sizing:**

```
mem = Σ (keys × (avgValue + avgKey + overhead ~60B)) × (1 + fragmentation ~1.3) × 1.3 headroom
```

A 10 GB dataset usually needs a 16–20 GB instance. Replicas double the cost.

**Hit-rate-driven DB sizing:** if the cache carries 95% of reads, the DB can be sized for 5%
of read traffic plus 100% of writes. Losing the cache means the DB needs 20x read capacity —
which is why the load-shedding plan matters more than the cache itself.

**Cost trade:** Redis memory is expensive per GB compared to disk. Caching a 5 GB JSON blob
set in Redis costs far more than storing it in Postgres and caching only ids. Cache the
**hot 1%**, not everything.

**Latency budget:** if the request SLA is 100 ms and Redis p99 is 5 ms, the cache is 5% of the
budget — fine. If Redis p99 is 40 ms under load, the cache is your latency problem, not your
solution.

---

## 11. Code Examples

### 11.1 Complete two-tier read path with single-flight and metrics

```java
@Component
public class ProductReadPath {
    private final Cache<Long, Product> l1 = Caffeine.newBuilder()
        .maximumSize(20_000)
        .expireAfterWrite(Duration.ofSeconds(60))
        .recordStats()
        .build();

    private final StringRedisTemplate redis;
    private final ProductRepository repo;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;

    public Product get(Long id) {
        // L1
        var local = l1.getIfPresent(id);
        if (local != null) { meters.counter("cache", "layer", "l1", "result", "hit").increment(); return local; }

        // L2
        String key = "product:v1:" + id;
        String json = safeGet(key);
        if (json != null) {
            var p = read(json);
            if (p != null) { l1.put(id, p); meters.counter("cache", "layer", "l2", "result", "hit").increment(); return p; }
        }

        // single-flight load
        Product fresh = l1.get(id, k -> {                 // collapses concurrent loads
            var fromDb = repo.findById(k).orElse(null);
            if (fromDb != null) safeSet(key, write(fromDb), jitter(Duration.ofMinutes(10)));
            return fromDb;
        });
        meters.counter("cache", "layer", "l2", "result", "miss").increment();
        return fresh;
    }

    /** Cache failures never propagate. */
    private String safeGet(String key) {
        try { return redis.opsForValue().get(key); }
        catch (Exception e) { meters.counter("cache.errors", "op", "get").increment(); return null; }
    }

    private void safeSet(String key, String v, Duration ttl) {
        try { redis.opsForValue().set(key, v, ttl); }
        catch (Exception e) { meters.counter("cache.errors", "op", "set").increment(); }
    }

    private Duration jitter(Duration d) {
        long ms = d.toMillis();
        return Duration.ofMillis(ms + (long) (ms * 0.1 * (ThreadLocalRandom.current().nextDouble() - 0.5)));
    }

    private Product read(String json) { try { return mapper.readValue(json, Product.class); } catch (Exception e) { return null; } }
    private String write(Product p) { try { return mapper.writeValueAsString(p); } catch (Exception e) { return "{}"; } }
}
```

Note the shadowing of `json` (a Java 15+ restricted identifier is fine as a variable). Use
`payload` if your toolchain objects.

### 11.2 Load shedding when the cache is down

```java
@Component
class CacheHealthGate {
    private final AtomicReference<State> state = new AtomicReference<>(State.HEALTHY);

    enum State { HEALTHY, DEGRADED }

    @Scheduled(fixedDelay = 5_000)
    void probe() {
        try { redis.ping(); state.set(State.HEALTHY); }
        catch (Exception e) { state.set(State.DEGRADED); log.error("redis degraded"); }
    }

    /** When the cache is down, serve stale-or-shed instead of hammering the DB. */
    boolean shouldShed(String endpoint) {
        return state.get() == State.DEGRADED && SHEDDABLE.contains(endpoint);
    }
}
```

```java
@GetMapping("/search")
ResponseEntity<?> search(@RequestParam String q) {
    if (gate.shouldShed("search")) {
        return ResponseEntity.status(503)
            .header("Retry-After", "10")
            .body(Map.of("error", "temporarily degraded"));
    }
    return ResponseEntity.ok(searchService.search(q));
}
```

### 11.3 Readiness gate that waits for warm-up

```java
@Component
class WarmupReadiness implements HealthIndicator {
    private volatile boolean warm = false;

    @EventListener(ApplicationReadyEvent.class)
    void warm() {
        hotKeys.forEach(cache::get);
        warm = true;
    }

    @Override public Health health() {
        return warm ? Health.up().build()
                    : Health.status("WARMING").build();   // k8s keeps it out of rotation
    }
}
```

### 11.4 Cache metrics via Micrometer

```java
@Bean
MeterBinder caffeineMetrics(CacheManager cm) {
    return reg -> {
        var cache = (CaffeineCache) cm.getCache("product");
        var stats = cache.getNativeCache().stats();
        Gauge.builder("caffeine.hit_rate", stats, CacheStats::hitRate)
             .tag("cache", "product").register(reg);
        FunctionCounter.builder("caffeine.evictions", stats, CacheStats::evictionCount)
             .tag("cache", "product").register(reg);
    };
}
```

---

## 12. Common Mistakes & Gotchas

| Mistake | Consequence | Fix |
|---|---|---|
| Unbounded local cache | OOM at peak | `maximumSize`/`maximumWeight` |
| `refreshAfterWrite` with no `expireAfterWrite` | Entries never expire → unbounded staleness | Add a hard TTL |
| No CDN `Cache-Control` | Paying origin for static traffic | `max-age` + immutable + ETag |
| `Vary` missing on `Authorization` | Cross-user data leak | `private` or per-user key |
| `no-cache` confused with `no-store` | Wrong privacy/hit behaviour | Use the right directive |
| Flushing the cache on deploy | Avalanche + latency cliff | Version keys instead |
| No warm-up after deploy | DB spike per replica | Warm before ready |
| Cache retries stacked | Tripled p99 under stress | One retry max, then fail fast |
| Hit rate not monitored | Silent regression to 0% for weeks | Per-cache hit-rate metric + alert |
| Cache treated as the only copy | Data loss on eviction | DB is truth |
| Two tiers with no invalidation bus | Inconsistent nodes | Pub/sub + short TTL |
| Sizing Redis for all data | Cost explosion | Cache the hot 1% |

---

## 13. Interview Questions & Model Answers

**Q1. How would you cache a product catalog serving 50k RPS?**
> Layered. CDN for public product pages with `s-maxage` and `stale-while-revalidate`, so most
> traffic never reaches us. Inside the service, Caffeine for the hot set (a few thousand
> products) and Redis for the full catalog with a 10–30 minute TTL, both keyed and versioned
> so writes bump the key. Writes go DB → commit → evict Redis → broadcast to nodes. Hit-rate
> and DB QPS on one dashboard, and a plan to shed load if Redis dies.

**Q2. When is an in-process cache the wrong choice?**
> When the data is user-specific or changes often enough that fleet-wide invalidation is
> impractical, or when heap budget is tight. It also multiplies staleness by the invalidation
> propagation delay. For per-user data I'd go straight to Redis, or not cache at all.

**Q3. What would you set `Cache-Control` to for a public product page?**
> `public, max-age=60, s-maxage=300, stale-while-revalidate=60, stale-if-error=600` plus an
> ETag. Browsers revalidate quickly, the CDN keeps it 5 minutes, and during an origin incident
> the CDN serves stale for 10 minutes instead of an error.

**Q4. How do you avoid a thundering herd when you deploy 20 pods with empty caches?**
> Warm up before readiness — an `ApplicationRunner` that loads the hot set, gated by a health
> indicator so Kubernetes doesn't route traffic until it's done. Also keep the shared tier, so
> only L1 is cold, and ramp with `maxUnavailable` low.

**Q5. Redis is down. What happens to your service?**
> It degrades, not fails: cache errors are caught and fall through to the DB, timeouts are
> 200 ms so threads aren't pinned, L1 absorbs the hot set, and a gate sheds load on
> expensive endpoints with a 503 and `Retry-After`. We've tested this by killing Redis in
> staging. The key point is that we pre-decided which endpoints shed rather than discovering it
> at 3 a.m.

**Q6. How do you know your cache is still working?**
> Hit rate per cache exported to metrics with an alert on a drop, DB QPS correlated with it,
> plus a staleness sampler that compares cached `updatedAt` with the DB. Eviction and memory
> metrics catch sizing drift. Without the staleness metric, invalidation bugs are invisible.

**Q7. How do you cache something that's expensive and rarely changes?**
> Long TTL with an explicit version in the key and an event-driven invalidation, rather than
> trying to be clever with short TTLs. For a report, precompute on a schedule into Redis with
> a TTL longer than the recompute interval, and serve from cache with a `generatedAt` field so
> the UI can show freshness.

---

## 14. Self-Check & Revision Card

- [ ] I can draw the five cache layers and their latency/consistency trade-offs.
- [ ] I can set correct `Cache-Control` for static assets, HTML and API responses.
- [ ] I can explain `no-cache` vs `no-store` and why `Vary` matters.
- [ ] I can size a Caffeine cache and a Redis instance roughly.
- [ ] I can design a warm-up path for deploys.
- [ ] I can name the three dashboards.
- [ ] I can write a load-shedding plan for cache failure.
- [ ] I can version keys instead of flushing.

```
┌────────────── MULTI-LEVEL CACHING — 60s RECALL ──────────────────────────────┐
│ browser → CDN → gateway → Caffeine → Redis → DB                              │
│ closer to user = longer TTL = more staleness tolerated                       │
│                                                                              │
│ CDN FIRST — cheapest win. max-age + s-maxage + SWR + stale-if-error + ETag.   │
│ no-cache = revalidate · no-store = don't store · Vary on Authorization.      │
│                                                                              │
│ CAFFEINE: ALWAYS bound (maximumSize/Weight). expireAfterWrite = hard TTL;     │
│ refreshAfterWrite alone never expires. Fleet memory multiplies.               │
│                                                                              │
│ TWO-TIER COST: two staleness windows + invalidation bus + N× heap.           │
│ Only add L1 when Redis latency or a hot shard demands it.                    │
│                                                                              │
│ DEPLOYS: warm before ready · version keys, never flush · keep L2 warm.       │
│                                                                              │
│ OPS: hit rate per cache + DB QPS on one graph · staleness sampler ·          │
│ runbook that says which endpoints shed when Redis dies.                      │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [03 · Cache Consistency, Invalidation & Stampedes](./03-Cache-Consistency-Invalidation-and-Stampedes.md) · **Up:** [README](./README.md)
