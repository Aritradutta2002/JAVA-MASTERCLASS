# 02 · Scalability, Availability & Reliability

> **Level:** Intermediate → Expert
> **Interview Frequency:** ★★★★★ ("What's the difference between availability and reliability?")
> **Prerequisite:** [01 · The System Design Framework](./01-System-Design-Framework.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. The Four Words](#1-the-four-words)
- [2. Scaling](#2-scaling)
- [3. Availability Arithmetic](#3-availability-arithmetic)
- [4. Reliability Patterns](#4-reliability-patterns)
- [5. Resilience Patterns](#5-resilience-patterns)
- [6. Consistency Choices and Their Cost](#6-consistency-choices-and-their-cost)
- [7. Multi-Region](#7-multi-region)
- [8. Load Balancing](#8-load-balancing)
- [9. Backpressure & Load Shedding](#9-backpressure--load-shedding)
- [10. Code Examples](#10-code-examples)
- [11. Interview Questions & Model Answers](#11-interview-questions--model-answers)
- [12. Self-Check & Revision Card](#12-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> **Scalability** = ability to handle growth by adding resources. **Availability** = fraction of
> time the system serves requests. **Reliability** = probability it does the right thing.
> **Resilience** = ability to recover. They're different; a system can be available and wrong.
>
> Scale **horizontally** (more instances, stateless services) before vertically, and make state
> the thing you design around. `N` instances behind a load balancer requires **no local session
> state** — everything in Redis/DB.
>
> Availability **multiplies** for services in series: five dependencies at 99.9% give 99.5%.
> So the senior moves are: **reduce the number of serial dependencies**, make them
> **asynchronous**, and add **fallbacks** so a dependency failure degrades rather than fails.
>
> Reliability needs **idempotency** (retries are inevitable), **timeouts** (before retries),
> **circuit breakers** (before cascading failure), and **bulkheads** (before resource
> exhaustion). In that order, because each one is useless without the previous.

---

## 1. The Four Words

| Term | Definition | Measured by | Fails when |
|---|---|---|---|
| **Scalability** | handles growth by adding resources, with sub-linear cost | QPS per instance stays flat as N grows | shared state becomes the bottleneck |
| **Availability** | fraction of time the service answers correctly | `uptime / total` (99.9% = 43 min/month down) | any serial dependency is down |
| **Reliability** | does the right thing, every time, over time | error rate, data correctness | retries create duplicates, partial writes |
| **Resilience** | recovers from failure gracefully | MTTR, blast radius | no timeouts, no isolation, no fallback |

**The distinction to state in an interview:** "A system can be 99.99% available and completely
wrong — if it answers every request with corrupted data, availability is perfect. That's why I
separate correctness (idempotency, consistency) from uptime."

---

## 2. Scaling

### 2.1 Vertical vs horizontal

```
VERTICAL (bigger box)                    HORIZONTAL (more boxes)
┌─────────────────────┐                 ┌────┐ ┌────┐ ┌────┐ ┌────┐
│  32 cores, 256 GB   │                 │ 4c │ │ 4c │ │ 4c │ │ 4c │
└─────────────────────┘                 └────┘ └────┘ └────┘ └────┘
 simple, no code change                 needs statelessness, but unlimited
 hard ceiling, expensive, downtime      failure-tolerant, cheap per unit
```

**Order:** vertical first (cheap, no code), then horizontal when you hit the ceiling or need
redundancy.

### 2.2 The stateless rule

Horizontal scaling requires that **any instance can serve any request**. That means:

| State | Where it goes |
|---|---|
| Session | Redis or a signed cookie (never in-memory) |
| Uploaded files | S3/object store (never local disk) |
| Cached data | shared cache (or an L1 with a documented staleness window) |
| Scheduled jobs | a leader-elected scheduler or a distributed lock |
| In-flight workflows | a durable store (saga/state machine), not a thread |

```java
// ❌ breaks horizontal scaling: the next request may hit another instance
@GetMapping("/cart")
Cart cart(HttpSession session) { return (Cart) session.getAttribute("cart"); }

// ✅ stateless: state lives outside the JVM
@GetMapping("/cart")
Cart cart(@AuthenticationPrincipal User user) {
    return cartService.get(user.id());     // redis-backed
}
```

### 2.3 Amdahl's law, briefly

Speedup from parallelising a fraction `p` of the work: `1 / ((1-p) + p/N)`. If 5% of the work is
serialised, even infinite parallelism caps you at 20x. **State and coordination are the
serial fraction** — that's why sharding and caching (removing coordination) matter more than
adding instances.

### 2.4 Where scaling actually stops

| Tier | Scales how | Wall |
|---|---|---|
| Stateless app | add instances | connection limits downstream |
| Cache | shard by key | hot keys, cluster ops |
| Relational DB | read replicas; shard for writes | single-writer ceiling |
| Queue | add partitions | partition count, consumer parallelism |
| Object storage | effectively unlimited | cost, egress |
| Search | add shards + replicas | reindex cost |

**The rule:** find the tier with the **worst scaling profile** — it sets your ceiling. It's
almost always the relational primary.

---

## 3. Availability Arithmetic

### 3.1 The nines

| Availability | Downtime/year | Downtime/month | Downtime/week |
|---|---|---|---|
| 99% | 3.65 days | 7.2 hours | 1.7 hours |
| 99.9% | 8.8 hours | 43 min | 10 min |
| 99.95% | 4.4 hours | 22 min | 5 min |
| 99.99% | 53 min | 4.3 min | 1 min |
| 99.999% | 5.3 min | 26 s | 6 s |

**Know the 99.9% = 43 min/month line.** It's the standard reference point and quoting it
accurately signals fluency.

### 3.2 Serial vs parallel availability

**Serial (all must work):**
```
A → B → C   with each at 99.9%
A_total = 0.999³ = 99.70%     ← worse than any individual component
```
Five dependencies at 99.9%: `0.999⁵ = 99.50%` — **3.6 hours/month**, not 43 minutes.

**Parallel (any one works):**
```
A ∥ B   with each at 99.9%
A_total = 1 - (0.001 × 0.001) = 99.9999%
```

**This is the single most important availability insight:** the request path is a **serial
chain**, and every dependency multiplies your failure probability. So:

1. **Remove dependencies from the critical path** (async, precompute).
2. **Add redundancy in parallel** (replicas, multi-AZ).
3. **Add fallbacks** so the chain doesn't need the dependency at all.

```
❌ checkout requires: inventory + pricing + payment + loyalty + recommendations
   (5 × 99.9% = 99.5% — recommendations will take checkout down)

✅ checkout requires: inventory + pricing + payment
   recommendations load asynchronously and fail silently
```

**Interview line:** "Recommendations aren't allowed to fail checkout. I'd move them off the
serial path entirely and let the UI render without them."

### 3.3 MTTR vs MTBF

```
Availability = MTBF / (MTBF + MTTR)

Two ways to improve it:
  increase MTBF  → better testing, canaries, fewer moving parts
  decrease MTTR  → faster detection, automated failover, runbooks
```

**At scale, MTTR matters more.** You cannot prevent every failure; you can make recovery fast.
Detection time (alerting) is often the largest component of MTTR, and the cheapest to fix.

### 3.4 SLOs, SLIs, error budgets

| Term | Meaning | Example |
|---|---|---|
| **SLI** | the measurement | fraction of requests with latency < 300 ms |
| **SLO** | the target for the SLI | 99.9% over 30 days |
| **SLA** | the contract with consequences | 99.9% or service credits |
| **Error budget** | 1 − SLO | 43 min/month of allowed failure |

Error budgets turn reliability into an engineering decision: if you've spent the budget,
freeze features and fix reliability. Mentioning them signals you've worked in a mature org.

---

## 4. Reliability Patterns

### 4.1 Idempotency — the foundation

Every network call can be retried; therefore every mutating operation must be safe to repeat.

| Technique | Example |
|---|---|
| Idempotency key | `POST /payments` with `Idempotency-Key`, response cached |
| Natural idempotency | `SET status = 'SHIPPED'` (not `SET count = count + 1`) |
| Unique constraint | `INSERT ... ON CONFLICT DO NOTHING` |
| Version check | `UPDATE ... WHERE version = ?` |
| Dedupe table | `(consumer, event_id)` in the same transaction as the effect |

**Say it early.** "Retries are inevitable, so I'd make this write idempotent" is a senior reflex.

### 4.2 Timeouts

**No timeout = unbounded thread occupation = cascading failure.** A single downstream slowdown
propagates up through thread pools in seconds.

```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 2000
  data:
    redis:
      timeout: 200ms
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 300ms
            readTimeout: 1000ms
```

**Timeout budget:** if your SLA is 1 s, your internal timeouts must sum to less than 1 s.
A chain of three calls with 1 s timeouts each has a 3 s worst case.

### 4.3 Retries — with conditions

```java
@Retryable(retryFor = {IOException.class, TimeoutException.class},
           noRetryFor = {IllegalArgumentException.class},
           maxAttempts = 3,
           backoff = @Backoff(delay = 100, multiplier = 2, random = true))
```

Rules:
- **Only retry transient errors** (timeouts, 5xx, connection resets). A 400 or a validation
  error will never succeed.
- **Exponential backoff with jitter** — without jitter all clients retry in lockstep.
- **Retry budget** — cap retries at 10% of traffic, or retries become the outage
  (the "retry storm" / metastable failure).
- **Only retry idempotent operations.**

### 4.4 Circuit breakers

```
        failures > threshold
 CLOSED ──────────────────────► OPEN
   ▲                              │
   │   success (N probes)         │ timeout
   └──────── HALF_OPEN ◄──────────┘
              │
              └── failure ──► OPEN
```

- **CLOSED** — requests pass; failures are counted.
- **OPEN** — fail fast; no calls to the sick dependency. This is what protects the *caller*.
- **HALF_OPEN** — allow a few probes; close on success, reopen on failure.

```java
@CircuitBreaker(name = "inventory", fallbackMethod = "reserveFallback")
public Reservation reserve(ReserveRequest r) { return client.reserve(r); }

public Reservation reserveFallback(ReserveRequest r, Throwable t) {
    return Reservation.pending(r);     // degrade: accept the order, reconcile later
}
```

**Crucial:** a circuit breaker without timeouts is useless — the breaker only trips on
*errors*, and a hung call isn't an error until the timeout fires.

### 4.5 Bulkheads

Isolate resources so one slow dependency can't consume all of them.

```java
@Bulkhead(name = "inventory", type = Bulkhead.Type.THREADPOOL, fallbackMethod = "busy")
```

```
❌ one shared thread pool of 200:
   200 threads stuck on the slow dependency → the whole service is dead

✅ separate pools:
   inventory: 20 threads (saturated → rejects fast)
   normal requests: 180 threads (still serving)
```

Semaphore bulkheads limit concurrency per dependency; thread-pool bulkheads also isolate
waiting. Combine with per-dependency connection pools.

### 4.6 Fallbacks and graceful degradation

| Failure | Degradation |
|---|---|
| Recommendations down | show a static "popular items" list |
| Cache down | read from DB; shed expensive endpoints |
| Search index down | show a filtered DB query, or "search unavailable" |
| Payment provider down | queue the request, confirm asynchronously |
| Price service down | serve last-known prices with a warning |

**Design the degraded state up front.** A system with no degraded mode has one failure mode:
total.

### 4.7 The reliability stack, in order

```
  1. IDEMPOTENCY      retries are safe
  2. TIMEOUTS         calls terminate
  3. RETRIES+JITTER   transient failures recover
  4. CIRCUIT BREAKER  failing dependencies stop being called
  5. BULKHEAD         isolation stops cascades
  6. RATE LIMIT       protects you from traffic spikes
  7. FALLBACK         degrade instead of fail
```

Each layer is useless without the one before it. Saying this order is a strong signal.

---

## 5. Resilience Patterns

### 5.1 Rate limiting

| Algorithm | Behaviour | Note |
|---|---|---|
| **Token bucket** | steady rate, allows bursts up to capacity | the practical default |
| **Leaky bucket** | smooths output to a constant rate | queues bursts |
| **Fixed window** | N per window | boundary burst (2x) |
| **Sliding window log** | exact, per-request timestamps | memory-heavy |
| **Sliding window counter** | interpolation between windows | the good compromise |

```java
// token bucket with Redis, atomic via Lua
Long allowed = redis.execute(script, List.of("rl:" + key),
    String.valueOf(rate), String.valueOf(capacity), String.valueOf(now()));
```

**Throttle by the right key:** per-user, per-API-key, per-IP, per-tenant. Global limits protect
the system; per-user limits keep one client from starving others.

### 5.2 Load shedding

When you can't serve everyone, serve the *important* vendors:

```
priority 1: authenticated paid requests (checkout)
priority 2: authenticated reads
priority 3: anonymous browsing
priority 4: recommendations, analytics beacons
```

Shed from the bottom when the queue exceeds a threshold. Returning **503 with `Retry-After`** is
strictly better than timing out.

### 5.3 Backpressure

Bound every queue; when full, reject rather than grow.

```
unbounded queue = OOM + hours of latency + a recovery that melts downstream
bounded queue + reject = fast, honest degradation
```

| Mechanism | Use |
|---|---|
| Bounded thread pools | reject when saturated |
| Bounded queues + 429/503 | API rate limits |
| `max.poll.records` + consumer limits | don't over-consume from Kafka |
| Reactive `limitRate` / `onBackpressureBuffer(n)` | streaming |

### 5.4 Chaos testing

Deliberately inject failures: kill a dependency, add latency, drop packets, fill a disk. Do it
in staging on a schedule, and in production with a small blast radius (Chaos Monkey style).
**A fallback you've never exercised doesn't exist.**

---

## 6. Consistency Choices and Their Cost

| Model | Guarantee | Latency | Example |
|---|---|---|---|
| **Strong** | reads see the latest write | high (quorum, sync replication) | balance, inventory count |
| **Read-your-writes** | a user sees their own writes | medium | profile edits |
| **Monotonic reads** | never go backwards in time | medium | feed scrolling |
| **Eventual** | converges eventually | low | likes, view counts, search index |
| **Causal** | cause before effect | medium | comment threads |

**The design question is always:** "who is harmed if this is 2 seconds stale?" Likes: nobody.
Available inventory: someone buys something you don't have — so reserve with a strong check.

**The practical pattern:** strong consistency on the **write decision**, eventual on
**everything derived** (counts, feeds, search, analytics).

---

## 7. Multi-Region

### 7.1 Topologies

| Topology | Writes | Reads | Trade-off |
|---|---|---|---|
| **Single region + CDN** | one region | local via CDN | simplest; high latency for distant users |
| **Active-passive** | primary region | replicas elsewhere | failover is manual/slow; RPO > 0 |
| **Active-active reads** | one primary | any region | read latency low; writes still cross-region |
| **Active-active writes** | multiple regions | local | conflicts need resolution (CRDT/LWW) |
| **Cell-based** | per-cell | per-cell | blast-radius isolation; complex routing |

### 7.2 The latency floor

```
cross-region RTT: ~30 ms (same continent) to ~150 ms+ (intercontinental)
```

That's a hard floor set by physics. If your write SLA is 100 ms and the primary is on another
continent, you cannot do a synchronous cross-region commit and meet it. Options: local writes
with conflict resolution, or accept the latency.

### 7.3 What changes

- **Ids must be globally unique** → no auto-increment; use ULID/UUIDv7 or a Snowflake scheme.
- **Timestamps become ambiguous** → use hybrid logical clocks or version vectors for ordering.
- **Conflicts are real** → last-write-wins (data loss), CRDTs (complex), or partition by user so
  one user's writes always go to one region (the pragmatic answer).
- **Data residency** — EU data in the EU is a legal constraint, not a performance one.

**The pragmatic recommendation:** partition by user/home region so each user's writes are
single-region, and replicate reads. You get local latency for writes and avoid conflict
resolution entirely. Say this — it's the answer most teams converge on.

---

## 8. Load Balancing

| Algorithm | Use |
|---|---|
| Round robin | uniform requests, uniform servers |
| Least connections | variable request duration |
| Least response time | latency-sensitive |
| Consistent hash | cache affinity (same key → same node) |
| Weighted | heterogeneous instance sizes |

**Layers:** DNS (geo) → CDN → L4 (TCP, fast) → L7 (HTTP, routing/retries/TLS). Health checks at
each layer; **passive** checks (eject on errors) catch what active checks miss.

**The classic bug:** sticky sessions. They defeat horizontal scaling and turn a lost instance
into lost users. Fix the statelessness instead.

---

## 9. Backpressure & Load Shedding

**The queueing reality:**

```
utilization  →  queue delay
   50%            2x service time
   80%            5x
   90%           10x
   95%           20x
   99%          100x
```

Latency explodes non-linearly near saturation. **That's why 70% utilization is a healthy target
and 90% is an emergency.** Little's Law (`L = λW`) and this curve are the two facts to quote.

**Practical rule:** if you're above 70% CPU you're already losing p99. Autoscale on *latency*
and *queue depth*, not just CPU.

---

## 10. Code Examples

### 10.1 The full resilience stack on one call

```java
@Service
public class InventoryClient {

    @Bulkhead(name = "inventory", type = Bulkhead.Type.THREADPOOL,
              fallbackMethod = "assumeAvailable")
    @CircuitBreaker(name = "inventory", fallbackMethod = "assumeAvailable")
    @Retry(name = "inventory")
    @TimeLimiter(name = "inventory")
    public CompletableFuture<Reservation> reserve(ReserveRequest req) {
        return CompletableFuture.supplyAsync(() -> http.post("/reservations", req, Reservation.class));
    }

    /** Degraded: accept the order, reconcile asynchronously. */
    public CompletableFuture<Reservation> assumeAvailable(ReserveRequest req, Throwable t) {
        log.warn("inventory degraded, accepting provisionally: {}", req.orderId(), t);
        outbox.append("Order", req.orderId(), "ReservationPending", req);
        return CompletableFuture.completedFuture(Reservation.pending(req));
    }
}
```

```yaml
resilience4j:
  timelimiter.instances.inventory.timeout-duration: 800ms
  retry.instances.inventory:
    max-attempts: 3
    wait-duration: 100ms
    enable-exponential-backoff: true
    exponential-backoff-multiplier: 2
    enable-randomized-wait: true
  circuitbreaker.instances.inventory:
    sliding-window-size: 20
    failure-rate-threshold: 50
    wait-duration-in-open-state: 10s
    permitted-number-of-calls-in-half-open-state: 3
  bulkhead.instances.inventory:
    max-concurrent-calls: 20
    max-wait-duration: 10ms
```

Note the annotation order matters: bulkhead outermost (reject early), then circuit breaker, then
retry, then timeout innermost.

### 10.2 Idempotent write with a key

```java
@PostMapping("/payments")
ResponseEntity<PaymentResult> pay(@RequestHeader("Idempotency-Key") String key,
                                  @RequestBody PaymentRequest req) {
    var existing = idempotency.find(key);
    if (existing.isPresent()) return ResponseEntity.ok(existing.get().result());

    var claimed = idempotency.claim(key);          // INSERT ... ON CONFLICT DO NOTHING
    if (!claimed) return ResponseEntity.status(409).body(null);   // in flight

    var result = gateway.charge(req);
    idempotency.complete(key, result);
    return ResponseEntity.ok(result);
}
```

### 10.3 Token-bucket rate limiter (Redis + Lua)

```lua
local key, rate, capacity, now = KEYS[1], tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3])
local state = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(state[1]) or capacity
local ts = tonumber(state[2]) or now
local elapsed = math.max(0, now - ts) / 1000.0
tokens = math.min(capacity, tokens + elapsed * rate)
if tokens < 1 then
  redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
  return 0
end
redis.call('HMSET', key, 'tokens', tokens - 1, 'ts', now)
redis.call('PEXPIRE', key, 60000)
return 1
```

### 10.4 Bounded work queue with rejection

```java
private final ThreadPoolExecutor pool = new ThreadPoolExecutor(
    16, 16, 60, TimeUnit.SECONDS,
    new ArrayBlockingQueue<>(1_000),
    new ThreadPoolExecutor.AbortPolicy());   // reject, don't grow unbounded

public CompletableFuture<Result> submit(Task t) {
    try {
        return CompletableFuture.supplyAsync(() -> run(t), pool);
    } catch (RejectedExecutionException e) {
        return CompletableFuture.failedFuture(new ServiceBusyException());
    }
}
```

### 10.5 Health checks that mean something

```java
@Component
class DependencyHealth implements HealthIndicator {
    public Health health() {
        boolean dbUp = canQuery("SELECT 1");
        boolean cacheUp = canPingRedis();
        // a cache outage is DEGRADED, not DOWN — don't remove yourself from the LB
        if (!dbUp) return Health.down().withDetail("db", false).build();
        return cacheUp ? Health.up().build()
                       : Health.status("DEGRADED").withDetail("cache", false).build();
    }
}
```

**Deep health checks vs readiness:** readiness should reflect "can I serve traffic", not "is
every dependency perfect". A cache outage shouldn't take every pod out of the load balancer.

---

## 11. Interview Questions & Model Answers

**Q1. Difference between availability and reliability?**
> Availability is uptime — the fraction of requests served. Reliability is correctness over time
> — the fraction served *correctly*. A system can be 100% available and wrong, e.g. it answers
> every request with stale or corrupted data. So availability is about the failure of the
> process, reliability is about the failure of the result, and resilience is how fast you
> recover.

**Q2. How do you calculate the availability of a system with five dependencies?**
> If they're all on the serial path, multiply: 0.999⁵ ≈ 99.5%, which is 3.6 hours a month, not
> 43 minutes. That's why I'd take non-essential dependencies off the critical path — load them
> asynchronously or with a fallback — and add redundancy where they must stay. Parallel
> redundancy is the opposite: 1 − 0.001² ≈ 99.9999%.

**Q3. How would you make this service horizontally scalable?**
> Remove all local state: sessions to Redis or a signed cookie, uploads to object storage,
> caches shared or with a documented per-node staleness window, and jobs behind leader election.
> Then any instance can serve any request, and the load balancer can spread traffic. The real
> constraint after that is whatever's shared downstream — the DB connections, the cache, the
> third-party quotas.

**Q4. What's the difference between a timeout and a circuit breaker?**
> A timeout bounds a single call; a circuit breaker stops making calls to something that's
> clearly failing, which protects the caller's threads and gives the dependency room to recover.
> Crucially, a breaker without timeouts doesn't work — a hung call isn't an error until a
> timeout fires, so the breaker never trips.

**Q5. Why is jitter needed on retries?**
> Without jitter, all clients fail at the same instant and retry at the same instant, so the
> recovering service gets hit with the full load again — a retry storm that becomes the outage.
> Randomising the backoff spreads the retries. And I'd cap the retry budget at around 10% of
> traffic so retries can't amplify load indefinitely.

**Q6. How do you prevent one slow dependency from taking down the whole service?**
> Bulkheads: a separate, bounded thread pool or semaphore per dependency, so saturation of one
> doesn't consume the threads needed for everything else. Plus timeouts so threads aren't held,
> a circuit breaker so we stop calling it, and a fallback so the request can still serve a
> degraded answer.

**Q7. What is a metastable failure?**
> A failure that persists after the trigger is gone because the system's recovery behaviour
> sustains it — retries, cache stampedes, or a cold cache all re-creating the original overload.
> The fix is to change the recovery dynamics: retry budgets, circuit breakers, gradual ramp-up,
> and load shedding rather than full restore.

**Q8. Active-active or active-passive?**
> Active-passive is simpler and safe; failover costs RTO and some RPO. Active-active reads are
> easy — replicate and serve locally. Active-active writes require conflict resolution, which
> usually means partitioning by user so each user's writes are single-region, avoiding conflicts
> entirely. I'd choose that over last-write-wins, which silently loses data.

**Q9. What utilization should you run at?**
> Around 70%. Queueing delay grows non-linearly — at 90% utilization latency is roughly 10x
> service time, at 95% it's 20x — so high utilization looks efficient and destroys p99. I'd also
> autoscale on latency and queue depth rather than CPU, because CPU can look fine while requests
> queue.

**Q10. What does an SLO buy you?**
> A shared, measurable definition of "good enough" and an error budget that turns reliability
> into an engineering trade-off. When the budget is spent, feature work pauses and reliability
> work takes priority. Without it, every reliability discussion is an argument about anecdotes.

---

## 12. Self-Check & Revision Card

- [ ] I can define scalability, availability, reliability and resilience distinctly.
- [ ] I can compute serial and parallel availability.
- [ ] I can list the seven reliability layers in order.
- [ ] I can explain why timeouts precede circuit breakers.
- [ ] I can explain jitter and retry budgets.
- [ ] I can name the rate-limiting algorithms and pick one.
- [ ] I can explain why unbounded queues are dangerous.
- [ ] I can quote the utilization/latency curve.
- [ ] I can compare multi-region topologies and name the latency floor.

```
┌─────────── SCALABILITY & RELIABILITY — 60s RECALL ──────────────────────────┐
│ 4 words: scalability (grow) · availability (uptime) · reliability (correct)  │
│          · resilience (recover). Available ≠ correct.                        │
│                                                                              │
│ SERIAL availability MULTIPLIES: 5 × 99.9% = 99.5% = 3.6 h/month.             │
│ ⇒ remove deps from the critical path, or add fallbacks.                      │
│ PARALLEL: 1 - (1-A)^n ≈ 99.9999% for two.                                    │
│                                                                              │
│ HORIZONTAL needs STATELESS: session→Redis, files→S3, jobs→leader election.   │
│                                                                              │
│ RELIABILITY STACK (order matters): idempotency → timeouts → retry+jitter →   │
│ circuit breaker → bulkhead → rate limit → fallback.                          │
│                                                                              │
│ UTILIZATION: 50%→2x latency, 90%→10x, 95%→20x. Target ~70%.                  │
│ Bound every queue; reject, don't grow. 503 + Retry-After > timeout.          │
│                                                                              │
│ MULTI-REGION: partition writes by user region to avoid conflict resolution.  │
│ Cross-region RTT 30–150 ms is a physics floor.                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [01 · The System Design Framework](./01-System-Design-Framework.md) · **Next:** [03 · Classic Design Problems](./03-Classic-Design-Problems.md)
