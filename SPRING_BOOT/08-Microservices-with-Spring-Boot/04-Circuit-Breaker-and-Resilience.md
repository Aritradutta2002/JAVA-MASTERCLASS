# 04 · Circuit Breaker, Retries & Resilience Patterns

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★★ ("What happens when a downstream service goes down?")
> **Prerequisite:** [03 · Service Discovery, Config & API Gateway](./03-Service-Discovery-Config-and-API-Gateway.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Five Layers](#2-mental-model--the-five-layers)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The failure's cascade](#31-the-failures-cascade)
  - [3.2 The timeout](#32-the-timeout)
  - [3.3 The retry](#33-the-retry)
  - [3.4 The circuit's breaker](#34-the-circuits-breaker)
  - [3.5 The bulkhead](#35-the-bulkhead)
  - [3.6 The rate's limiter](#36-the-rates-limiter)
  - [3.7 The fallback's & the degradation's ladder](#37-the-fallbacks--the-degradations-ladder)
  - [3.8 The resilience's patterns](#38-the-resiliences-patterns)
  - [3.9 The Resilience4j's modules](#39-the-resilience4js-modules)
- [4. Visualizations](#4-visualizations)
- [5. Worked Examples](#5-worked-examples)
- [6. Code from Scratch — Production-Grade](#6-code-from-scratch--production-grade)
- [7. Common Pitfalls & Gotchas](#7-common-pitfalls--gotchas)
- [8. Interview Q&A — Level-wise](#8-interview-qa--level-wise)
- [9. Counter-Questions & Traps](#9-counter-questions--traps)
- [10. Rapid-Fire MCQs](#10-rapid-fire-mcqs)
- [11. Hands-On Exercises](#11-hands-on-exercises)
- [12. Code Challenges](#12-code-challenges)
- [13. Scenario-Based Questions](#13-scenario-based-questions)
- [14. Tricky Interview Questions](#14-tricky-interview-questions)
- [15. Follow-Up Questions Asked by Interviewers](#15-follow-up-questions-asked-by-interviewers)
- [16. Real 3–5 Year Scenarios](#16-real-35-year-scenarios)
- [17. Frequently Asked Production Issues](#17-frequently-asked-production-issues)
- [18. Debugging Scenarios](#18-debugging-scenarios)
- [19. Architecture Design Questions](#19-architecture-design-questions)
- [20. Cheat Sheet — One-Page Summary](#20-cheat-sheet--one-page-summary)
- [21. Follow-up Learning](#21-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

- **Resilience is a stack, and the order matters: timeout → retry → circuit
  breaker → bulkhead → rate limiter → fallback.** A retry without a timeout is a
  thread leak; a retry without a breaker amplifies an outage; a breaker without
  a bulkhead can still exhaust your pool.
- **The timeout is the most important and the most neglected.** The default HTTP
  client timeout is **infinite**. Every call needs a connect timeout and a read
  timeout, and the timeout must be **derived from the caller's budget**, not
  guessed.
- **The retry's rules:** only for **idempotent** operations, only for
  **transient** failures (never a `4xx` except `429`), with an **exponential
  backoff AND a jitter** (no jitter = a thundering herd), and a **bounded
  budget** (the total retry time must be less than the caller's remaining
  deadline).
- **The circuit breaker is a three's state machine: CLOSED → OPEN → HALF_OPEN.**
  CLOSED = normal, count the failures. OPEN = the dependency is considered down,
  fail fast immediately. HALF_OPEN = let a limited number of probe requests
  through to test recovery.
- **The bulkhead isolates the resources per dependency**, so one slow dependency
  can't consume all your threads/connections. It's the pattern that stops a
  cascade from becoming a total outage.
- **The fallback is the user's experience of the failure.** Degrade gracefully
  (stale cache, default value, partial result, "pending" state) rather than
  return a 500. The best fallback is often a **degraded business state**, not a
  static error.
- **A retry is not a resilience strategy on its own** — it's a *multiplier*. The
  resilience comes from the breaker (failing fast) and the bulkhead (not
  cascading).

---

## 1. What & Why

### The scenario

```text
  10:00:00  The catalogue service deploys a bad release. Its p99 goes from
             20ms to 30 seconds (it didn't crash — it's slow).

  10:00:01  The orders service calls it. The call doesn't fail — it HANGS.
             The orders service's 200 threads are now all blocked.

  10:00:05  All 200 threads are blocked. The orders service stops serving
             EVERYTHING — not just the catalogue calls. The health check
             fails, K8s restarts the pod, and the new pod does the same thing.

  10:01:00  The catalogue is fixed. But the orders service is in a crash-loop
             and the customers are seeing 503s on the entire API.

  TOTAL: 1 minute of a slow dependency = 1 minute of total outage.
```

**This is the cascade, and it's the single most important distributed-systems
failure mode to be able to explain.** Every piece of it is preventable:

| The step | The prevention |
|---------|----------------|
| The call hangs | A **timeout** |
| The threads are exhausted | A **bulkhead** (per-dependency isolation) |
| The repeated calls hammer the sick dependency | A **circuit breaker** (fail fast) |
| The pod's restart doesn't help | The **readiness** probe tied to the dependency's health, or the degradation's path |
| The customers see a 500 | A **fallback** (degraded but responding) |

### The mental reframe

> **Resilience isn't about "handling errors". It's about bounding the blast
> radius of a failure.** A resilient system fails *partially, predictably, and
> quickly* instead of *totally, unpredictably, and slowly*.

---

## 2. Mental Model — The Five Layers

```text
  the request's path through the resilience's stack:

  ┌────────────────────────────────────────────────────────────────┐
  │  ⑤ the FALLBACK          "what do we return when it fails?"     │
  │      the stale's cache · the default · the partial · the 202    │
  ├────────────────────────────────────────────────────────────────┤
  │  ④ the RATE's LIMITER    "how much can this dependency take?"   │
  │      the token's bucket · the concurrency's limiter             │
  ├────────────────────────────────────────────────────────────────┤
  │  ③ the BULKHEAD          "how much of ME can this take?"       │
  │      the per-dependency's thread/connection's isolation          │
  ├────────────────────────────────────────────────────────────────┤
  │  ② the CIRCUIT's BREAKER "should we even TRY?"                  │
  │      CLOSED → OPEN → HALF_OPEN                                  │
  ├────────────────────────────────────────────────────────────────┤
  │  ① the TIMEOUT + RETRY    "how long, and how many times?"       │
  │      the deadline's budget · the backoff · the jitter           │
  └────────────────────────────────────────────────────────────────┘

  ⚠ the ORDER is the DESIGN. Each layer assumes the one below it works:
    · the retry's backoff needs a per-attempt's timeout
    · the breaker's fail-fast needs the retry's budget to be bounded
    · the bulkhead's limit needs the breaker to not queue
    · the fallback needs the failure to be detected and bounded
```

**The one-line expert framing:**

> "The **timeout** bounds how long a failure takes; the **breaker** bounds how
> often you hit a failing dependency; the **bulkhead** bounds how much of your
> own capacity one dependency can consume; the **fallback** bounds what the
> user experiences. Get all four and a downstream outage becomes a
> **degradation**, not a **cascade**."

---

## 3. Deep Dive — Theory

### 3.1 The failure's cascade

**The anatomy of the cascade (the exact's mechanism):**

```text
  ① the LATENCY's build-up
     the dependency's latency rises → the caller's threads are held longer
     → the caller's throughput drops (Little's Law: concurrency = rate × latency)
     → the caller's QUEUE builds (the Tomcat's max-threads = 200 → the 201st
       request WAITS in the accept's queue)

  ② the QUEUE's saturation
     the queue's wait's time is now unbounded
     → the caller's own p99 explodes (it includes the queue's wait)
     → the caller's p99 becomes the NEXT's caller's problem (the cascade)

  ③ the RETRY's amplification
     the timeout fires → the client retries → the load on the sick's
     dependency DOUBLES → it gets slower → more's timeouts → more's retries
     → ⚠ the positive's feedback's loop: the dependency's own's load is
       making it worse

  ④ the EXHAUSTION
     the threads are all blocked → no thread's left to accept new's
     connections → the health check's thread is blocked too (if it's a
     separate's pool, the check might still pass — a dangerous's false's OK)
     → K8s sees the pod's as failing → restarts it → the new pod's repeats
       the cycle

  ⑤ the RECOVERY's problem
     the dependency's fixed → but the callers are in a crash-loop or a
     reconnect's storm → the dependency is immediately overloaded again by
     the clients' reconnect's surge
```

**Little's Law (the throughput's formula the interview loves):**

```text
  L = λ × W        (the concurrency = the arrival's rate × the wait's time)

  ⚠ THIS is why the timeout's value matters so much:
    with a 30s's timeout and 200's threads:
      the maximum's throughput = 200 / 30 = 6.7 requests/second
    with a 500ms's timeout and 200's threads:
      the maximum's throughput = 200 / 0.5 = 400 requests/second

    the SAME service, 60× the difference's capacity, purely from the timeout.
```

**The connection's pool's parallel (the same's law, the DB's side):**

```text
  the maximum's throughput = the pool's size / the query's time
    HikariCP with 20's connections and a 10ms's query  → 2000 QPS
    HikariCP with 20's connections and a 500ms's query → 40 QPS  ← the DB is slow

  ⚠ a slow's DB (a missing's index, a lock's wait, a replica's lag) turns the
    pool's size into the throughput's CEILING, and the queue's wait's time
    becomes the latency's FLOOR.
```

### 3.2 The timeout

**The three's timeouts on a network's call:**

| The timeout | What it bounds | The default (Spring) |
|------------|---------------|---------------------|
| The **connect's timeout** | The TCP's handshake + the TLS's handshake | Infinite (`SimpleClientHttpRequestFactory`) |
| The **read's timeout** | The server's think's time (from the request's sent to the response's first byte) | Infinite |
| The **total's timeout** (the deadline) | The whole's operation | Not a thing — you have to build it |

**The critical's point about the read's timeout:** it bounds the time from
**sending** the request to **the first byte** of the response. A server that
sends the headers and then trickles the body can hold the connection
**indefinitely** — you need a **response's timeout** for the whole's body read
too (the `RestClient`'s `SimpleClientHttpRequestFactory` doesn't have one;
the `JdkClientHttpRequestFactory` with the `HttpClient`'s `responseTimeout`
does, and the `WebClient`'s `ReadTimeoutHandler` does).

**The deadline's propagation (the correct's model):**

```text
  ❌ the TIME-OUT's model (each hop's own's choice):
     the gateway: 10s
       → the orders: 5s
         → the catalogue: 5s
     worst case: 10s > the user's budget ❌ (the outer gives up first,
     the inner's work is wasted)

  ✅ the DEADLINE's model (the budget's flow):
     the user's request arrives with a deadline of T+3s
       → the gateway derives: "I have 3s"
         → the orders derives: "I have 2.8s (minus my own's processing)"
           → the catalogue derives: "I have 2.5s (minus my own's processing)"
     worst case: the total's work finishes inside 3s ✅
     and if the remaining's budget is too small, the hop FAILS FAST
     instead of starting work it can't finish
```

**The deadline's propagation's mechanisms:**

| The mechanism | How |
|-------------|-----|
| The **HTTP's header** | `timeout: 3000` (the Spring's `RestClient` sets it automatically from the request's factory) or `X-Request-Timeout` (a custom's convention) |
| The **gRPC's deadline** | The `grpc-timeout` header, propagated natively by the gRPC's client |
| The **correlation's id + the budget's header** | A custom's header carrying the absolute's deadline (`X-Deadline: 2026-10-21T10:15:33.123Z`) |
| The **Micronaut/Quarkus's context** | The framework's propagation |

**The "the remaining's budget" arithmetic (the code's pattern):**

```java
long remaining = deadlineNanos - System.nanoTime();
if (remaining < MIN_USEFUL_WORK_NANOS) {
    throw new DeadlineExceededException("The remaining's budget is too small to be useful");
}
```

### 3.3 The retry

**The retry's decision's tree:**

```text
  should I retry this call?
     │
     ├── is the operation IDEMPOTENT (or does it have an idempotency's key)?
     │     NO → don't retry ❌ (the duplicate's charge, the duplicate's order)
     │
     ├── is the failure TRANSIENT?
     │     · the connection's reset / refused  → YES
     │     · the read's timeout               → MAYBE (the write's side's effect's unknown!)
     │     · the 503 / the 429                → YES
     │     · the 500                          → MAYBE (often a bug → won't help)
     │     · the 400 / the 401 / the 403 / the 404 / the 422 → NO ❌
     │     · the 409                          → only with a re-read
     │
     ├── is there enough's BUDGET?
     │     the total's retry's time must be < the caller's remaining's deadline
     │
     └── how many's ATTEMPTS and with what BACKOFF?
           the exponential + the FULL's jitter
```

**The "the write's timeout" problem (the most's subtle):**

```text
  ⚠ if the operation is a WRITE and the read's timeout fires, you DON'T KNOW
    whether the server's processed it:
      · the server's received the request and committed, but the response was
        lost → the retry creates a DUPLICATE
      · the server's never's received it → the retry is correct

    the ONLY safe's answer: the IDEMPOTENCY's KEY.
    without it, you must treat the write's timeout's outcome as UNKNOWN and
    not retry (or reconcile).
```

**The backoff's algorithms:**

```text
  ① FIXED:        wait 100ms, 100ms, 100ms
     ⚠ the synchronised's stampede — every client retries at the same moments

  ② EXPONENTIAL:  wait 100ms, 200ms, 400ms, 800ms
     ✅ the growth's shape is right, but the clients are still synchronised
        (they all started at the same time)

  ③ EXPONENTIAL + FULL's JITTER:  wait random(0, 100ms), random(0, 200ms), ...
     ✅ the RECOMMENDED's form — the clients' spread is uniform's
     ⚠ note: full's jitter can retry IMMEDIATELY (random could be 0)
        → a zero's wait's a "retry's storm" on the first's attempt

  ④ EXPONENTIAL + EQUAL's JITTER:  wait = base/2 + random(0, base/2)
     ✅ the guaranteed's minimum's backoff (never retries immediately)
        and the spread's cap

  ⑤ DECORRELATED's JITTER:  wait = random(base, previous × 3)
     ✅ the best's spread's distribution (the AWS's recommendation for
        the S3's clients) — the no's burst's synchronisation
```

**The "the retry's storm" maths (why the jitter matters):**

```text
  WITHOUT the jitter:
    10,000's clients hit the dependency at t=0
    all 10,000's time out at t=500ms
    all 10,000's retry at t=600ms  ← the same's instant
    the dependency's receives 10,000's requests/second AGAIN
    → it's still down → they all time out again
    → the cycle repeats, and the dependency's recovery is IMPOSSIBLE
    because the retry's traffic never lets up

  WITH the full's jitter:
    the 10,000's clients' retries are spread over 0-500ms
    the dependency's sees a SMOOTH's 20 requests/second
    → as it recovers, the traffic's ramp is gentle
    → the recovery's HAPPENS
```

**The retry's budget (the advanced's concept):**

```text
  the RETRY's BUDGET is a PERCENTAGE of the request's total's time.

  the Google's SRE's rule of thumb: the retries must consume at most
  ~10% of the total's latency's budget. If the request's budget is 3s,
  the total's retry's time (including the backoff's sleeps) must be ≤ 300ms.

  ⚠ WHY: the retry's time is a MULTIPLIER on the load. If the original's
    request is 100ms and the retry's budget is 300ms, the service's effective
    load can be 4× — the original + 3's retries. A retry's budget that's
    100% of the request's budget means the load can be INFINITE.

  the PROPAGATION: the budget must be passed DOWN the call's chain, so a
  deep's call doesn't retry past the top's deadline. The standard's
  implementation: a "the remaining's retry's budget" in the context, and
  the client checks it before each's retry.
```

### 3.4 The circuit's breaker

**The three's states:**

```text
                    the failure's threshold's exceeded
        ┌───────────────────────────────────────────────────────┐
        │                                                       ▼
   ┌─────────┐                                    ┌──────────────────┐
   │ CLOSED  │                                    │      OPEN        │
   │         │  the wait's duration's elapsed      │                  │
   │ the     │  + the probe's request's succeed   │ fail fast,       │
   │ normal  │◀───────────────────────────────┐    │ no calls made    │
   │         │                               │    └──────────────────┘
   │ counts  │                               │              │
   │ failures│                               │              │ the probe's fails
   └─────────┘                               │              ▼
        ▲                                     │      ┌──────────────────┐
        │ the failure's rate's below         └──────│   HALF_OPEN      │
        │ the threshold's                     the    │                  │
        └────────────────────────────────────────────│ a few's probes   │
              (the success's count's high)           └──────────────────┘
```

**The configuration's parameters (the Resilience4j's names):**

| The parameter | The meaning | The typical's value |
|---------------|-------------|-------------------|
| `slidingWindowSize` | The number of the last's calls to consider | 10-100 |
| `minimumNumberOfCalls` | The minimum's calls before the failure's rate's evaluated | 10-20 |
| `failureRateThreshold` | The % of failures that trips the breaker | 50% |
| `slowCallRateThreshold` | The % of slow's calls that trips the breaker | 100% |
| `slowCallDurationThreshold` | What counts as "slow" | The dependency's p95 |
| `waitDurationInOpenState` | How long the breaker stays open | 30-60s |
| `permittedNumberOfCallsInHalfOpenState` | The probes allowed in HALF_OPEN | 3-10 |
| `automaticTransitionFromOpenToHalfOpenEnabled` | The timer-driven's probe | true |

**The two's calculation's models:**

```text
  ① the COUNT's BASED (the simple's one):
     "N of the last M calls failed" → the breaker opens
     ⚠ the small's sample's size makes it noisy (1 failure out of 3 trips it)

  ② the PERCENTAGE's BASED (the Resilience4j's default):
     "the failure's RATE over the last M calls is ≥ the threshold" → opens
     ✅ the more's stable's signal
     ⚠ with a small's M, the rate is coarse (1 of 2 = 50% = the threshold)
     → hence the minimumNumberOfCalls's floor
```

**The minimum's number's calls (the subtle's but important's point):**

```text
  ⚠ WITHOUT the minimumNumberOfCalls:
     the window's size = 10, but after 2 calls (1 failure), the rate is 50%
     → the breaker opens on 2 calls' worth of data
     → a single's transient's error during a low's traffic's period
       (e.g. at 3am) takes the breaker OPEN
     → and it stays open for the full's waitDuration (30s+)
     → a tiny's traffic's spike causes a service's outage

  ✅ WITH minimumNumberOfCalls = 10:
     the breaker only evaluates after 10's calls in the window
     → at 3am with 2's calls, the breaker never's state-changes ✅
```

**The slow's calls (the often-forgotten's dimension):**

A dependency that is **slow** but not **failing** is just as dangerous — it
consumes your threads. The `slowCallRateThreshold` +
`slowCallDurationThreshold` let the breaker trip on slowness:

```text
  the CONFIG:
    slowCallDurationThreshold: 2s    ← 2s+ = a "slow's call"
    slowCallRateThreshold: 80%      ← if 80% of the calls are slow → OPEN

  ⚠ and a separate's metric — the "the slow's calls' rate" — is often the
    EARLIEST's signal of an impending's outage, before the error's rate moves.
```

**The "the breaker is open" behaviour:**

```text
  the OPEN's state → the call is REJECTED IMMEDIATELY with a
  CallNotPermittedException. It does NOT wait for the timeout. ⚠ this is the
  point: the point of the breaker is to NOT hold your threads for a
  dependency you already know is down.
```

**The half's open's design (the probe's strategy):**

```text
  after waitDurationInOpenState:
    → HALF_OPEN: the next N calls are PERMITTED (as probes)
    → if they all succeed → CLOSED (the recovery's confirmed)
    → if any fails → back to OPEN (and the wait's timer resets)

  ⚠ the ALTERNATIVE's design: the timed's probe — the breaker transitions
    automatically after the wait's duration and lets ONE call through as the
    probe. The Resilience4j's automaticTransitionFromOpenToHalfOpenEnabled
    implements this. The difference matters: with the automatic's transition,
    the state's change is on a TIMER (no call's needed to trigger it); without
    it, the state only changes when a call arrives and the breaker checks the
    timer.
```

### 3.5 The bulkhead

**The name's origin:** a ship's hull is divided into watertight compartments, so
one compartment's flooding doesn't sink the whole's ship.

```text
  WITHOUT the bulkhead:
    ┌─────────────────────────────────────┐
    │  200's threads                       │
    │  ┌────────┐┌────────┐┌────────┐    │
    │  │catalogue││catalogue││catalogue│   │  ← 200's calls to the
    │  │ call #1 ││ call #2 ││ call #3 │... │     slow's catalogue
    │  └────────┘└────────┘└────────┘    │
    │  ⚠ ALL 200's threads are blocked      │
    │  ⚠ the API's serves NOTHING          │
    └─────────────────────────────────────┘

  WITH the bulkhead:
    ┌─────────────────────────────────────┐
    │  200's threads                       │
    │  ┌────────┐┌────────┐┌────────┐    │
    │  │catalogue││catalogue││ catalogue│   │  ← the catalogue's compartment
    │  │ call #1 ││ call #2 ││  (idle)  │   │     (20's threads, the semaphore)
    │  └────────┘└────────┘└────────┘    │
    │  ┌────────┐┌────────┐              │
    │  │payment ││payment │              │  ← the payment's compartment
    │  │ call #1 ││ call #2 │              │     (20's threads)
    │  └────────┘└────────┘              │
    │  ⚠ the catalogue's failure's isolated │
    │  ✅ 160's threads still serve the API │
    └─────────────────────────────────────┘
```

**The two's implementations in Spring:**

| The kind | The mechanism | The scope |
|---------|-------------|----------|
| The **thread's pool's isolation** (the Resilience4j's `ThreadPoolBulkhead`) | A dedicated's thread's pool per dependency | The isolation's degree, the **thread's cost** |
| The **semaphore's isolation** (the Resilience4j's `Bulkhead`) | A `Semaphore` with a permits's count | Cheap, but the waiting's callers **hold** a request's thread while waiting |

**The semaphore's bulkhead's trap:**

```text
  ⚠ the SEMAPHORE's bulkhead: when the permits are exhausted, the caller WAITS
    for a permit. That wait happens on the caller's thread → the caller's
    threads are STILL blocked (just on the semaphore instead of the socket).
    → the bulkhead's protection is PARTIAL: it bounds the load on the
      dependency, but it doesn't free the caller's threads.
    → the FIX: the wait's timeout must be SHORT (a few's ms) and it must
      fail fast with a BulkheadFullException.

  ✅ the THREAD's POOL's bulkhead: the calling's thread is freed immediately
    (the work is submitted to the dedicated's pool). The caller's thread
    returns... ⚠ but only if you use the reactive's API. In the blocking's
    API, the caller WAITS for the future anyway → same's problem.
    → the REAL's fix for the blocking's case: the **semaphore's bulkhead with
      a short's wait's timeout** (it bounds the dependency's load) PLUS the
      **circuit's breaker** (it stops the doomed's calls early) PLUS the
      **fallback** (it returns fast).
```

**The database's bulkhead (the connection's pool is the natural's one):**
Each service's DB access is naturally bulkheaded by the **connection's pool** —
HikariCP's `maximumPoolSize` is the bulkhead's permits. The mistake is using
**one** pool's size for all the query types: a slow's reporting's query holds a
connection and starves the checkout's queries. The **separation**: a second's
pool for the reporting's queries (or a separate's read's replica).

### 3.6 The rate's limiter

**The three's algorithms:**

| The algorithm | The behaviour | The use case |
|--------------|-------------|-------------|
| **The token's bucket** | A bucket of `capacity` tokens refilled at `rate`/s | The general's purpose; allows a burst |
| **The sliding's window's log** | The exact's count in the last N seconds | The precise's limit; the memory's cost |
| **The sliding's window's counter** | The previous + current window's weighted count | The good's approximation; the low's memory |
| **The leaky's bucket** | A queue that drains at a fixed's rate | The smoothing's outbound's traffic |

**The Resilience4j's RateLimiter** is a **single's instance's, in-memory**
limiter — per JVM, per bucket. In a 10-pod's deployment, the effective global
limit is **10×** the configured value. That's usually fine (it's a per-pod's
shaping) but it must be understood — for a **global's** limit, you need the
Redis's implementation (the Gateway's `RequestRateLimiter`).

**The concurrency's limiter (the semaphore's variant):** limits the number of
**simultaneous** calls (not the rate) — this is the one that protects a
dependency from a burst better than a rate's limiter, because the rate's
limiter still allows N concurrent calls if they arrive within the same's
millisecond.

### 3.7 The fallback's & the degradation's ladder

**The ladder (from the best's to the worst's):**

```text
  ① the FRESH's DATA (the normal's path)
  ② the STALE's CACHE (the last's known's good's value)
  ③ the DEFAULT's VALUE (a hardcoded's fallback — "the recommendations are
     empty", "the price is 0", "the avatar is the generic's one")
  ④ the PARTIAL's RESULT (the response's with the failed's part omitted)
  ⑤ the DEGRADED's STATE (the "the payment's pending, we'll confirm by
     webhook" — the business's state, not an error)
  ⑥ the ASYNC's DEFERMENT (the 202 + the background's processing)
  ⑦ the ERROR (the 503 + the Problem's Details)

  ⚠ the ORDER matters: the graceful's degradation is the point. A service that
    returns a 503 when it could return a stale's price has failed the user,
    even though it's technically "correct".
```

**The most's valuable's example (the real's pattern):**

```text
  the CHECKOUT's service calls the CATALOGUE for the price.

  ❌ the naive's design: the price is required → the call's failure → 500.
     the user loses the whole's checkout because the recommendation's
     service is down.

  ✅ the good's design: the price's unavailable → the CHECKOUT uses the
     PRICE from the CART (the price the user already saw when they added it
     to the cart — it's the "the stale's cache" in the business's domain)
     and marks the order as "the price's pending's verification". The
     background's job re-checks and either confirms or notifies.

  the LESSON: the BEST's fallback is a domain's concept, not a technical's
  one. "the price's pending's verification" is a *business's* state that
  happens to be the right's degradation.
```

### 3.8 The resilience's patterns

**The AWS's "let it fail" / "fail fast" / "fail gracefully" taxonomy:**

| The pattern | The meaning |
|------------|-----------|
| **Fail fast** | The system's response to an increased's load is to **decrease** the available's resources (the breaker) |
| **Fail slow** | The system increases the load as the resources's decrease (the queue's growth) — the opposite, and dangerous |
| **Fail gracefully** | The system degrades in small's increments (the partial's degradation) rather than all at once |
| **Let it fail** | Deliberately fail a small's part of the system to protect the rest (the bulkhead, the priority's shedding) |

**The "the load's shedding" (the priority's queue):**

```text
  under's overload, the system must REJECT some's work:
    · the LOW's priority's requests are dropped first (the report's generation
      is dropped before the checkout)
    · the rate's limiting is applied per tenant (the free's tier's users are
      limited before the enterprise's)
    · the queue's depth is bounded (a bounded's queue, and the rejections are
      FAST — 429 with a Retry-After, not a 30s wait)

  ⚠ an UNBOUNDED's queue is the classic's killer: it converts a latency's
    problem into a memory's problem and then an outage's. The queue's depth
    must be bounded and the rejections must be fast.
```

**The "the back's pressure" pattern:** the consumer's slowness propagates to
the producer as a slowdown in the **request's rate** (the queue's depth's signal
in the protocol) — e.g. the HTTP's `429`/`503` with a `Retry-After`, the
Kafka's **pause's partition** (the consumer's `pause()` on the slow's partition
so the broker's stops sending), the reactive's streams' **demand's propagation**
(the `request(n)` model).

### 3.9 The Resilience4j's modules

```text
  the RESILIENCE4J's modules (the Spring Cloud's Circuit Breaker's integration):
    ├── CircuitBreaker        — the fail's fast
    ├── Retry                 — the retry's policy
    ├── TimeLimiter           — the timeout's decorator
    ├── Bulkhead              — the semaphore's isolation
    ├── RateLimiter           — the in-memory's rate's limiting
    └── Cache                 — the cache's decorator
```

**The annotation's based's configuration (the Spring Cloud's Circuit Breaker):**

```java
@CircuitBreaker(name = "catalogue", fallbackMethod = "fallback")
@TimeLimiter(name = "catalogue", fallbackMethod = "fallback")
@Retry(name = "catalogue", fallbackMethod = "fallback")
@Bulkhead(name = "catalogue", type = Bulkhead.Type.THREADPOOL, fallbackMethod = "fallback")
public Product getProduct(String skuId) { ... }
```

⚠ **The important's gotcha:** the annotations are applied via **AOP**, and the
**order** of the aspects is defined by the `@Order` on the aspects. The
**correct's nesting** (the outermost's first):

```text
  Bulkhead → TimeLimiter → CircuitBreaker → Retry → the method
  (outermost)                                  (innermost)

  · the BULKHEAD is outermost → it rejects the excess's calls FIRST
  · the TIME's LIMITER wraps the whole's thing → the total's budget
  · the CIRCUIT's BREAKER decides whether to try at all
  · the RETRY is innermost → the actual's attempts
```

**The `fallbackMethod`'s signature's rules:**

```text
  the fallback's method must MATCH the original's parameters, and may ADD
  one trailing's parameter for the exception:

    Product getProduct(String skuId)
    Product getProduct(String skuId, Exception e)      ✅
    Product getProduct(String skuId, Throwable t)      ✅
    Product getProduct(String skuId, Exception e, Other o) ✅ (any order? NO —
        the exception's parameter must be the LAST one)
    Product getProduct(String skuId, Other o)           ❌ (won't be selected)
```

**The programmatic's API (the recommended's approach for the complex's logic):**

```java
Supplier<Product> decorated = Decorators.ofSupplier(this::fetchProduct)
        .withTimeLimiter(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(2)).build())
        .withCircuitBreaker(CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(100)
                .slowCallDurationThreshold(Duration.ofSeconds(1))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .minimumNumberOfCalls(10)
                .slidingWindowSize(20)
                .build())
        .withRetry(RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(100, 2, 0.5))
                .retryExceptions(IOException.class, TimeoutException.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build())
        .withFallback(fallbackSupplier,
                throwable -> throwable instanceof CallNotPermittedException)
        .decorate();
```

---

## 4. Visualizations

### 4.1 The five's layers

```text
   the CLIENT's request
        │
        ▼
   ┌──────────────────────────────────────────────────────────┐
   │  ① the TIMEOUT (the deadline's budget)                   │
   │     2s max. NO WAITING FOREVER.                          │
   │     ┌──────────────────────────────────────────────────┐  │
   │     │  ② the RETRY (the transient's failures only)     │  │
   │     │     attempt 1 → fail → wait (backoff + jitter)   │  │
   │     │     attempt 2 → fail → wait                     │  │
   │     │     attempt 3 → fail → give up                  │  │
   │     │     ⚠ the total's retry's time ≤ 10% of the     │  │
   │     │       request's budget                           │  │
   │     │     ┌──────────────────────────────────────┐    │  │
   │     │     │  ③ the CIRCUIT's BREAKER             │    │  │
   │     │     │     CLOSED ──failures ≥ threshold──▶  │    │  │
   │     │     │     OPEN   ──wait 30s──▶ HALF_OPEN    │    │  │
   │     │     │     ▲                    │  probe ok   │    │  │
   │     │     │     └────────────────────┘             │    │  │
   │     │     └──────────────────────────────────────┘    │  │
   │     └──────────────────────────────────────────────────┘  │
   └──────────────────────────────────────────────────────────┘
        │ (the exception's escapes)      │
        ▼                                ▼
   ┌──────────────────────────┐   ┌──────────────────────────┐
   │  ④ the BULKHEAD           │   │  ⑤ the FALLBACK           │
   │     "not one dependency   │   │     the stale's cache      │
   │      can eat all my       │   │     the default's value    │
   │      threads"             │   │     the degraded's state   │
   └──────────────────────────┘   └──────────────────────────┘
```

### 4.2 The circuit's breaker's state's machine

```text
                   the failure's rate ≥ 50% (over ≥ 10's calls)
        ┌──────────────────────────────────────────────────────────┐
        │                                                          ▼
   ┌─────────┐      the failure's rate < 50%           ┌───────────────┐
   │ CLOSED  │                                           │     OPEN      │
   │         │◀──────────────────────────────────────    │               │
   │ the     │        (or the success's rate's high)    │  fail fast,   │
   │ normal  │                                           │  no calls     │
   │         │                                           └───────────────┘
   └─────────┘                                                  │
        ▲                                                      │ the 30s's
        │                                                      │ wait's elapsed
        │                                                      ▼
        │        the probe's succeed              ┌──────────────────────┐
        └──────────────────────────────────────────│     HALF_OPEN        │
                                                   │  3's probes allowed  │
                                                   └──────────────────────┘
                                                           │ any's probe fails
                                                           └──▶ back to OPEN
```

*Look at: the OPEN state does not "wait and try" — it rejects **instantly**.
That instant rejection is what frees your threads.*

### 4.3 The bulkhead's compartments

```text
   WITHOUT the bulkhead — the total's collapse:
   ┌───────────────────────────────────────────┐
   │ 200 threads │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │ ← all to the catalogue
   │            │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │   (all blocked)
   │            │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
   │            └───────────────────────────────┘
   │ ⚠ ZERO's capacity left for anything's else                   │
   └───────────────────────────────────────────┘

   WITH the bulkhead — the bounded's damage:
   ┌───────────────────────────────────────────┐
   │ 200 threads │ ▓▓▓▓▓▓▓▓ (20 to catalogue)  │ ← the compartment
   │            │ ▓▓▓▓▓▓▓▓ ░░░░ (idle)        │   is full
   │            │ ▒▒▒▒▒▒▒▒ (20 to payment)     │ ← the payment's
   │            │ ▒▒▒▒▒▒▒▒ ░░░░ ░░░░ (idle)   │   compartment
   │            │ ░░░░░░░░░░░░░░░ (160 free)    │
   │            └───────────────────────────────┘
   │ ✅ the API's still serves 160's worth of traffic             │
   └───────────────────────────────────────────┘
```

### 4.4 The cascade's timeline

```text
  t=0s     the catalogue's release goes bad (p99: 20ms → 30s)
  t=1s     the orders' calls to the catalogue start's hanging
  t=5s     the orders' 200's threads are ALL blocked
           ⚠ Little's Law: throughput = 200 threads / 30s = 6.7 RPS (was 10,000)
  t=8s     the orders' queue builds → the p99 → 30s+ (the QUEUE's wait)
           ⚠ the other endpoints are now slow too (the shared's threads)
  t=15s    the readiness's probe fails → K8s kills the pod
  t=20s    the new pod starts → 200's fresh's threads → all block again
           ⚠ the CRASH-LOOP begins
  t=60s    the catalogue is fixed
           t=60-75s the orders' pods are crash-looping; the clients' retries
                    (if any) keep the catalogue's load's high
  t=90s    the orders' stabilises

  THE COST: 90 seconds of total outage for a 1-minute dependency's issue
  THE FIX:  the timeout (1s) + the bulkhead (20's threads) + the breaker
            → the catalogue's failure affects 20's worth of traffic; the
              other 180's threads keep serving ✅
```

---

## 5. Worked Examples

### 5.1 The breaker's state's transitions (the dry run)

```text
  the CONFIG:
    slidingWindowSize: 20
    minimumNumberOfCalls: 10
    failureRateThreshold: 50
    waitDurationInOpenState: 30s
    permittedNumberOfCallsInHalfOpenState: 3

  the TIMELINE:
    t=0s     call #1  → 200 (success)  [the window: 1 call]
    ...
    t=4s     call #10 → 500 (failure)  [the window: 10 calls]
             ⚠ the minimumNumberOfCalls = 10 is NOW met → the rate's evaluated
             the failures: 1/10 = 10% < 50% → stays CLOSED ✅

    t=5-30s  calls #11-#25 → mostly 500
             the window's rate: 16 failures / 20 calls = 80% ≥ 50% → **OPEN**
             ⚠ the calls #21-#25 were REJECTED instantly (CallNotPermitted)
                — they never's reached the catalogue ✅ (the load's protection)

    t=30s    the wait's elapsed → HALF_OPEN
             the next 3's calls are PERMITTED (the probes):
               call #26 → 200 ✅
               call #27 → 200 ✅
               call #28 → 200 ✅
             → all 3's probes succeeded → **CLOSED** ✅
             ⚠ the 3's probes were the ONLY's calls that hit the catalogue
                — the rest were's rejected ✅

    ⚠ the ALTERNATIVE: if call #26 had FAILED → back to OPEN, and the
      30s's timer RESETS (so the dependency gets another 30s of protection)
```

### 5.2 The retry's storm (the dry run)

```text
  the SITUATION: 5,000's clients, the dependency is down for 30s

  WITHOUT the jitter (the exponential's backoff, the base = 100ms):
    t=0.0s   5,000's calls arrive
    t=0.5s   all 5,000's time out → all retry at t=0.6s
    t=0.6s   5,000's calls arrive AGAIN (simultaneously)
    t=1.1s   all 5,000's time out → all retry at t=1.2s
    t=1.2s   5,000's calls arrive AGAIN
    → the dependency's sees 5,000's requests/second CONTINUOUSLY
    → even if it recovers at t=30s, it's immediately overwhelmed
    → ⚠ the recovery is IMPOSSIBLE

  WITH the full's jitter (random(0, backoff)):
    t=0.5s   all 5,000's time out → each waits random(0, 100ms)
    t=0.5-0.6s  the retries are SPREAD over 100ms
    → the dependency's sees ~50,000/second at the peak, DECAYING as the
      spread's continues
    → the total's load's over the 30s ≈ the same's total's calls, but
      SMOOTHED
    → as the dependency's recovers at t=30s, the load's ramp is gentle ✅

  ⚠ and the combination with the BREAKER:
    the breaker's OPEN's state means the retries are NOT made at all
    → the dependency's load drops to ZERO immediately ✅
    → this is why the breaker + the jitter is the complete's answer
```

### 5.3 The degradation's ladder (the dry run)

```text
  the SCENARIO: the catalogue's service is down. The checkout's service needs
  the price.

  ❌ LEVEL 0 (no fallback):
     the checkout's service → 500 "Internal Server Error"
     the user: "the app's broken" 😞
     the conversion's rate: -40%

  ⚠ LEVEL 1 (the technical's error):
     the checkout's service → 503 with a Retry-After
     the user: "try again later" 😕
     the conversion's rate: -25%
     ⚠ better than a 500, but the user still can't buy

  ✅ LEVEL 2 (the stale's cache):
     the checkout's service uses the price from the CART (what the user saw)
     the user: "the order's placed at the price I saw" 🙂
     the conversion's rate: -5% (only the new's visitors are affected)
     ⚠ the risk: the price's stale → the order's placed at the wrong's price
       → the FIX: the "the price's pending's verification" (level 4)

  ✅✅ LEVEL 4 (the degraded's business's state):
     the checkout's service places the order with priceStatus = PENDING
     the background's job re-checks the price when the catalogue recovers
     → if the price's unchanged → the order's confirmed
     → if the price's changed → the customer is notified and can cancel
     the user: "the order's placed, we'll confirm the price shortly" 😊
     the conversion's rate: -2%

  the LESSON: the BEST's fallback is a DOMAIN's concept. "the price's pending's
  verification" is a business's state — and it happens to be exactly the right's
  technical's degradation.
```

### 5.4 The Little's Law's calculation (the dry run)

```text
  the SERVICE: 200's threads (the Tomcat's max-threads), the checkout's API

  the SCENARIO A (the healthy's dependency, 20ms):
    L = λ × W → 200 = λ × 0.02 → λ = 10,000 RPS (the max's throughput)
    the actual's capacity: the DB's limit

  the SCENARIO B (the timeout at 500ms, the dependency's degraded to 500ms):
    200 = λ × 0.5 → λ = 400 RPS
    → the capacity's dropped 25× ✅ (but it's still serving)

  the SCENARIO C (the timeout at 30s — the default's infinite, effectively):
    200 = λ × 30 → λ = 6.7 RPS
    → ⚠ 6.7 RPS! the API's effectively DOWN.
    → and the queue's wait's time is ADDITIONAL (the requests beyond 200's
       wait in the accept's queue → the p99 is unbounded)

  the FIX, in the order:
    ① the TIMEOUT (2s) → the capacity's floor = 100 RPS
    ② the BULKHEAD (20's threads to the catalogue) →
       the catalogue's calls' capacity = 20/2s = 10 RPS,
       and the other 180's threads serve 180/0.02 = 9,000 RPS ✅
    ③ the BREAKER → the catalogue's calls stop entirely
    ④ the FALLBACK → the requests succeed with a degraded's result
```

---

## 6. Code from Scratch — Production-Grade

### 6.1 The Resilience4j's configuration (the YAML)

```yaml
resilience4j:
  # the CIRCUIT's BREAKER's defaults
  circuitbreaker:
    configs:
      default:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        slow-call-rate-threshold: 100
        slow-call-duration-threshold: 2s
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        # ⚠ the register's health's indicator — for the actuator
        register-health-indicator: true
        record-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
          - feign.RetryableException
        # ⚠ IGNORE the exceptions that are NOT the dependency's fault
        #   (a 404 from the catalogue is not the catalogue being broken!)
        ignore-exceptions:
          - com.example.orders.client.CatalogueClientException
          - org.springframework.web.client.HttpClientErrorException

  # the RETRY's defaults
  retry:
    configs:
      default:
        max-attempts: 3
        # ⚠ the exponential's RANDOM's backoff — the base, the multiplier, the
        #   max's interval, and the JITTER's factor (0.5 = full's jitter)
        interval-function: exponential-random-backoff
        initial-interval: 100ms
        multiplier: 2
        max-interval: 2s
        jitter-factor: 0.5
        # ⚠ the retry's exceptions — ONLY the transient's ones
        retry-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
          - org.springframework.web.client.ResourceAccessException
        # ⚠ the errors that are NOT the transient's ones — a 4xx will never succeed
        ignore-exceptions:
          - com.example.orders.client.CatalogueClientException
          - org.springframework.web.client.HttpClientErrorException
          - java.lang.IllegalArgumentException

  # the TIMEOUT's defaults
  timelimiter:
    configs:
      default:
        timeout-duration: 2s
        # ⚠ the cancel's running's future — true if the thread's interrupted
        cancel-running-future: true

  # the BULKHEAD's defaults
  bulkhead:
    configs:
      default:
        # the semaphore's isolation
        max-concurrent-calls: 20
        # ⚠ the wait's duration — ⚠ SHORT! a long's wait defeats the purpose
        #   (the caller's thread is blocked waiting for a permit)
        max-wait-duration: 100ms

  # the THREAD's POOL's bulkhead (the real's isolation)
  thread-pool-bulkhead:
    configs:
      default:
        max-thread-pool-size: 20
        core-thread-pool-size: 5
        queue-capacity: 100
        keep-alive-duration: 20ms

  # the RATE's LIMITER
  ratelimiter:
    configs:
      default:
        limit-for-period: 100
        limit-refresh-period: 1s
        timeout-duration: 0

  # the per-instance's overrides
  instances:
    catalogue:
      circuitbreaker:
        configs:
          default:
            failure-rate-threshold: 50
            slow-call-duration-threshold: 1s
            wait-duration-in-open-state: 20s
    payment:
      circuitbreaker:
        configs:
          default:
            # ⚠ the payment's breaker is TIGHTER — the failure's cost's higher
            failure-rate-threshold: 25
            wait-duration-in-open-state: 10s
      retry:
        configs:
          default:
            # ⚠ the payment's retry is LONGER — a temporary's gateway's blip
            #   is worth's retrying (and the operation has an idempotency's key)
            max-attempts: 4
            initial-interval: 500ms
            max-interval: 5s
    inventory:
      circuitbreaker:
        configs:
          default:
            # ⚠ the inventory's breaker is LOOSER — it's a hard's dependency
            #   (we can't serve without the stock) and it's usually's healthy
            failure-rate-threshold: 75
            slow-call-duration-threshold: 3s
```

### 6.2 The programmatic's API (the recommended's approach)

```java
package com.example.orders.client;

import com.example.orders.domain.Product;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.VoidSupplier;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Component
@Slf4j
public class ResilientCatalogueClient {

    private final CatalogueClient delegate;

    /**
     * The DECORATED's SUPPLIER — the whole's resilience's stack in one object.
     *
     * ⚠ The NESTING's ORDER is the DESIGN (outermost → innermost):
     *   Bulkhead → TimeLimiter → CircuitBreaker → Retry → the method
     *
     *   · the BULKHEAD is outermost → the excess's calls are rejected
     *     IMMEDIATELY (no thread is held)
     *   · the TIME's LIMITER bounds the TOTAL (including the retries)
     *   · the CIRCUIT's BREAKER decides whether to try at all
     *   · the RETRY is innermost → the actual's attempts
     */
    private final Supplier<Product> decorated;

    public ResilientCatalogueClient(CatalogueClient delegate,
                                    CircuitBreakerRegistry circuitBreakerRegistry,
                                    BulkheadRegistry bulkheadRegistry) {
        this.delegate = delegate;

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .slowCallRateThreshold(100)
                .slowCallDurationThreshold(Duration.ofSeconds(1))
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(IOException.class, TimeoutException.class)
                .ignoreExceptions(CatalogueClientException.class)
                .build();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("catalogue", cbConfig);

        TimeLimiterConfig tlConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(3))   // ⚠ the TOTAL, incl. the retries
                .cancelRunningFuture(true)
                .build();
        TimeLimiter timeLimiter = TimeLimiter.of("catalogue", tlConfig);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialRandomBackoff(100, 2, 0.5))   // the jitter's included
                .retryExceptions(IOException.class, TimeoutException.class)
                .ignoreExceptions(CatalogueClientException.class)
                .build();
        Retry retry = Retry.of("catalogue", retryConfig);

        Bulkhead bulkhead = bulkheadRegistry.bulkhead("catalogue",
                io.github.resilience4j.bulkhead.BulkheadConfig.custom()
                        .maxConcurrentCalls(20)
                        .maxWaitDuration(Duration.ofMillis(100))  // ⚠ SHORT
                        .build());

        this.decorated = Decorators.ofSupplier(this::fetch)
                .withBulkhead(bulkhead)
                .withTimeLimiter(timeLimiter)
                .withCircuitBreaker(circuitBreaker)
                .withRetry(retry)
                .withFallback(this::fallback,
                        throwable -> throwable instanceof CallNotPermittedException)
                .decorate();
    }

    private Product fetch() {
        return delegate.getProduct(currentSkuId);
    }

    /**
     * The FALLBACK — the graceful's degradation.
     * ⚠ The fallback's exceptions must be CAUGHT (a checked's exception
     *   can't be thrown from a Supplier).
     */
    private Product fallback(Exception e) {
        if (e instanceof CallNotPermittedException) {
            log.warn("The catalogue's breaker is OPEN — the call's rejected");
        } else {
            log.error("The catalogue's call's failed", e);
        }
        // the DEGRADED's state — the domain's concept
        return Product.unavailable(currentSkuId);
    }

    public Product getProduct(String skuId) {
        return decorated.get();
    }
}
```

### 6.3 The annotation's based (the simpler's approach)

```java
package com.example.orders.client;

import com.example.orders.domain.Product;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.stereotype.Service;

@Service
public class AnnotatedCatalogueClient {

    private final CatalogueClient client;

    public AnnotatedCatalogueClient(CatalogueClient client) {
        this.client = client;
    }

    /**
     * ⚠ The ANNOTATION's ORDER is defined by the @Order on the aspects:
     *   Bulkhead → TimeLimiter → CircuitBreaker → Retry → the method
     *
     * ⚠ The fallbackMethod's SIGNATURE must match the method's parameters,
     *   optionally with the exception's as the LAST's parameter.
     */
    @CircuitBreaker(name = "catalogue", fallbackMethod = "fallback")
    @TimeLimiter(name = "catalogue", fallbackMethod = "fallback")
    @Retry(name = "catalogue", fallbackMethod = "fallback")
    public Product getProduct(String skuId) {
        return client.getProduct(skuId);
    }

    /**
     * The FALLBACK's method.
     * ⚠ The signature: the SAME's parameters + the exception's LAST.
     */
    @SuppressWarnings("unused")
    private Product fallback(String skuId, Exception e) {
        return Product.unavailable(skuId);
    }
}
```

### 6.4 The timeout's configuration (the client's factory)

```java
package com.example.orders.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HttpClientConfig {

    /**
     * The JDK's HttpClient's factory — the ONE that supports the
     * response's timeout (the whole's body read), not just the read's
     * first-byte's timeout.
     */
    @Bean
    public JdkClientHttpRequestFactory jdkRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))            // the TCP + TLS
                .version(HttpClient.Version.HTTP_2)                 // the h2's negotiation
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        // ⚠ THE TIMEOUT — without it, the default is INFINITE
        factory.setReadTimeout(Duration.ofSeconds(3));
        return factory;
    }

    @Bean
    public RestClient catalogueRestClient(RestTemplateBuilder builder) {
        return builder
                .requestFactory(jdkRequestFactory())
                .baseUrl("http://catalogue-service")
                .build();
    }
}
```

```java
package com.example.orders.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;

/**
 * The CLIENT's ERROR's MAPPING — the classification that drives the
 * retry's and the breaker's decisions.
 * ⚠ The classification is EVERYTHING: a 404 must not trip the breaker's
 *   failure's rate (the catalogue's not broken — the product's just missing),
 *   and a timeout on a WRITE must not be retried without an idempotency's key.
 */
@Component
public class CatalogueClient {

    private final RestClient restClient;

    public CatalogueClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public Product getProduct(String skuId) {
        try {
            Product product = restClient.get()
                    .uri("/api/products/{skuId}", skuId)
                    .retrieve()
                    .body(Product.class);
            if (product == null) {
                throw new CatalogueClientException("The catalogue's returned an empty's body", skuId);
            }
            return product;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status >= 400 && status < 500 && status != 429) {
                // ⚠ the CLIENT's fault — NOT the dependency's failure
                //   → the breaker's ignoreExceptions must cover this
                throw new CatalogueClientException(
                        "The catalogue's rejected the request: " + status, e, skuId);
            }
            // the 5xx and the 429 → the transient's failure → the retry's candidate
            throw new CatalogueUnavailableException(
                    "The catalogue's returned " + status, e);
        } catch (ResourceAccessException e) {
            // the TIMEOUT or the connection's failure
            throw new CatalogueUnavailableException(
                    "The catalogue's did not respond in time", e);
        }
    }

    // the DTO
    public record Product(String skuId, String name, long priceCents, boolean available) {
        public static Product unavailable(String skuId) {
            return new Product(skuId, "Unavailable", 0, false);
        }
    }
}
```

### 6.5 The bulkhead's configuration (the per-dependency's isolation)

```java
package com.example.orders.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.threadpool.ThreadPoolBulkhead;
import io.github.resilience4j.threadpool.ThreadPoolBulkheadConfig;
import io.github.resilience4j.threadpool.ThreadPoolBulkheadRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class BulkheadConfig {

    /**
     * The SEMAPHORE's bulkhead — the cheap's isolation.
     * ⚠ The maxWaitDuration MUST be short. A long's wait means the caller's
     *   thread is blocked on the semaphore (not the socket) — the dependency
     *   is protected, but the caller's capacity is still consumed.
     */
    @Bean
    public Bulkhead catalogueBulkhead(BulkheadRegistry registry) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(20)
                .maxWaitDuration(Duration.ofMillis(100))
                .writableStackTraceEnabled(false)
                .build();
        return registry.bulkhead("catalogue", config);
    }

    /**
     * The THREAD's POOL's bulkhead — the real's isolation.
     * ⚠ The thread's pool's size must be SMALL and the queue's capacity
     *   limited — a big's queue just moves the waiting's inside the pool
     *   (and the latency's stays).
     */
    @Bean
    public ThreadPoolBulkhead catalogueThreadPoolBulkhead(ThreadPoolBulkheadRegistry registry) {
        ThreadPoolBulkheadConfig config = ThreadPoolBulkheadConfig.custom()
                .maxThreadPoolSize(20)
                .coreThreadPoolSize(5)
                .queueCapacity(50)
                .keepAliveDuration(Duration.ofMillis(20))
                .build();
        return registry.bulkhead("catalogue", config);
    }
}
```

```java
package com.example.orders.client;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BulkheadedCatalogueClient {

    private final CatalogueClient delegate;
    private final Bulkhead catalogueBulkhead;

    public Product getProduct(String skuId) {
        try {
            // ⚠ the Supplier's variant throws BulkheadFullException IMMEDIATELY
            //   when the permits are exhausted (no waiting) — this is the
            //   non-blocking's behaviour we want
            return catalogueBulkhead.executeSupplier(() -> delegate.getProduct(skuId));
        } catch (BulkheadFullException e) {
            // ⚠ the compartment's FULL — the dependency's already saturated.
            //   Fail fast with the degraded's result.
            log.warn("The catalogue's bulkhead is full — the call's rejected");
            return Product.unavailable(skuId);
        }
    }
}
```

### 6.6 The fallback's ladder (the domain's degradation)

```java
package com.example.orders.service;

import com.example.orders.domain.Order;
import com.example.orders.domain.PriceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private final CatalogueClient catalogueClient;
    private final OrderRepository orderRepository;

    /**
     * The CHECKOUT with the graceful's degradation.
     *
     * ⚠ The KEY's design: the price's failure does NOT fail the checkout.
     *   The order's placed with priceStatus = PENDING, and a background's
     *   job re-verifies the price. This is the DOMAIN's concept, not a
     *   technical's error's page.
     */
    public Order checkout(String customerId, String skuId, int quantity) {
        BigDecimal price = null;
        boolean priceConfirmed = false;

        try {
            Product product = catalogueClient.getProduct(skuId);
            price = BigDecimal.valueOf(product.priceCents(), 2);
            priceConfirmed = product.available();
        } catch (Exception e) {
            // ⚠ the DEGRADED's path — the order's still placed
            log.warn("The price's unavailable for {} — placing the order's PENDING", skuId, e);
        }

        Order order = Order.pending(customerId, skuId, quantity);
        if (priceConfirmed) {
            order.confirmPrice(price);
        } else {
            // ⚠ the price's from the CART (the stale's cache in the domain)
            order.markPricePending(priceFromCart(skuId));
        }

        return orderRepository.save(order);
    }
}
```

### 6.7 The custom's actuator's indicators (the observability)

```java
package com.example.orders.actuator;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component("resilienceCircuitBreakers")
public class CircuitBreakerHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry registry;

    public CircuitBreakerHealthIndicator(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    /**
     * The BREAKER's health's indicator.
     * ⚠ The OPEN's breakers must make the service's health's DOWN — otherwise
     *   K8s keeps routing to a pod whose dependencies are all broken.
     * ⚠ But be careful: if ALL the breakers are open, the health's down means
     *   the pod's restarts — a restart's storm. The common's practice is to
     *   expose the breaker's state as a METRIC and an ALERT, and only fail
     *   the readiness's probe for the truly's critical's dependencies.
     */
    @Override
    public Health health() {
        Map<String, CircuitBreaker.State> states = registry.getAllCircuitBreakers()
                .stream()
                .collect(Collectors.toMap(CircuitBreaker::getName, CircuitBreaker::getState));

        boolean anyOpen = states.values().contains(CircuitBreaker.State.OPEN);

        Health.Builder builder = anyOpen ? Health.down() : Health.up();
        states.forEach((name, state) -> builder.withDetail(name, state.name()));
        builder.withDetail("total", states.size());
        return builder.build();
    }
}
```

```java
package com.example.orders.actuator;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class CircuitBreakerMetrics {

    private final CircuitBreakerRegistry registry;
    private final MeterRegistry meterRegistry;

    public CircuitBreakerMetrics(CircuitBreakerRegistry registry, MeterRegistry meterRegistry) {
        this.registry = registry;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void registerGauges() {
        registry.getAllCircuitBreakers().forEach(cb -> {
            // the STATE as a number (0=closed, 1=open, 2=half-open)
            Gauge.builder("resilience.circuitbreaker.state",
                            cb, c -> switch (c.getState()) {
                                case CLOSED -> 0;
                                case OPEN -> 1;
                                case HALF_OPEN -> 2;
                                default -> -1;
                            })
                    .description("The circuit's breaker's state")
                    .tag("name", cb.getName())
                    .register(meterRegistry);

            // the FAILURE's rate — the most's useful's metric
            Gauge.builder("resilience.circuitbreaker.failure_rate",
                            cb, c -> c.getMetrics().getFailureRate())
                    .tag("name", cb.getName())
                    .register(meterRegistry);

            // the SLOW's calls' rate — the early's warning
            Gauge.builder("resilience.circuitbreaker.slow_call_rate",
                            cb, c -> c.getMetrics().getSlowCallRate())
                    .tag("name", cb.getName())
                    .register(meterRegistry);

            // the BUFFERED's calls' count — the number's calls in the window
            Gauge.builder("resilience.circuitbreaker.number_of_calls",
                            cb, c -> c.getMetrics().getNumberOfBufferedCalls())
                    .tag("name", cb.getName())
                    .register(meterRegistry);
        });
    }
}
```

### 6.8 The WebClient's timeout (the reactive's client)

```java
package com.example.orders.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    /**
     * The REACTIVE's CLIENT with the timeouts.
     * ⚠ In the reactive's world, the timeouts are the ONLY thing bounding a
     *   hung's call — there's no thread to exhaust, so a missing's timeout
     *   means the request's never's completes (and the memory's leaked).
     */
    @Bean
    public WebClient catalogueWebClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .responseTimeout(Duration.ofSeconds(3))     // the whole's response
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(3000)));        // the idle's read

        return builder
                .baseUrl("http://catalogue-service")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
```

```java
package com.example.orders.client;

import com.example.orders.domain.Product;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReactiveCatalogueClient {

    private final WebClient webClient;

    /**
     * The REACTIVE's client with the built-in's retry.
     * ⚠ The reactive's retry is DEADLINE-aware if you set the timeout's
     *   duration on the retry itself (the total's time, not per-attempt).
     * ⚠ And the reactive's model has no thread's exhaustion — but it DOES
     *   need a timeout, or the subscription's never's completes and the
     *   memory's leaked.
     */
    @CircuitBreaker(name = "catalogue", fallbackMethod = "fallback")
    public Mono<Product> getProduct(String skuId) {
        return webClient.get()
                .uri("/api/products/{skuId}", skuId)
                .retrieve()
                .bodyToMono(Product.class)
                .timeout(Duration.ofSeconds(2))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                        .jitter(0.5)                       // ⚠ the JITTER
                        .filter(this::isTransient)
                        .doBeforeRetry(signal -> log.warn(
                                "The catalogue's call's retrying: {}", signal.failure().getMessage())));
    }

    private boolean isTransient(Throwable t) {
        // ⚠ only the transient's failures — a 404 is NOT
        return !(t instanceof org.springframework.web.reactive.function.client
                .WebClientResponseException.NotFound);
    }

    @SuppressWarnings("unused")
    private Mono<Product> fallback(String skuId, Throwable t) {
        log.warn("The catalogue's is degraded: {}", t.getMessage());
        return Mono.just(Product.unavailable(skuId));
    }
}
```

---

## 7. Common Pitfalls & Gotchas

| # | ❌ Wrong | ✅ Right | Why |
|---|----------|----------|-----|
| 1 | The default's timeout (infinite) | Always set the connect + the read's timeouts | A hung's downstream holds the thread forever |
| 2 | A retry on a `4xx` | Only on the `5xx`/`429`/the timeouts | The pointless's retries, the account's lockout |
| 3 | A retry on a write's timeout without an idempotency's key | The key, or don't retry | The duplicate's order/charge |
| 4 | A retry without the jitter | The exponential + the random's backoff | The thundering herd re-creates the outage |
| 5 | The breaker's `minimumNumberOfCalls` too low (or absent) | ≥ 10 (or ≥ the traffic's rate) | One's error at 3am trips the breaker |
| 6 | A breaker with no `slowCallRateThreshold` | Add the slow's call's detection | The slow's dependency is as dangerous as the down's one |
| 7 | The bulkhead's `maxWaitDuration` too long (or 0 = infinite) | A short's wait (50-100ms) | The caller's thread is blocked on the semaphore |
| 8 | The `ThreadPoolBulkhead`'s queue's capacity huge | A small's queue (or 0 = the caller's rejection) | The waiting's just moves inside the pool |
| 9 | The breaker's `ignoreExceptions` missing the `4xx`s | Ignore the client-fault's exceptions | A 404's storm opens the breaker and takes down the API |
| 10 | The fallback's exception's signature wrong | The same's params + the exception's LAST | The fallback's never's selected |
| 11 | The `@Retry` on a method that mutates state | Only on the idempotent's methods | The duplicate's side's effect |
| 12 | The fallback's returning `null` | Return a meaningful's degraded's value | The NPE's downstream |
| 13 | The breaker's health's indicator failing the readiness's probe | The metric + the alert (carefully) | The restart's storm when the breakers are open |
| 14 | The retry's budget exceeding the caller's deadline | Derive the timeouts from the remaining's budget | The deadline's violation upstream |
| 15 | The `RateLimiter`'s per-JVM's limit mistaken for the global's | The Redis's implementation for the global's | The effective limit is N × the configured's |
| 16 | The unbounded's queue as the overload's response | The bounded's queue + the fast's rejection | The latency's problem becomes a memory's problem |
| 17 | The `TimeLimiter` with `cancel-running-future: false` | `true` | The thread's keeps working after the timeout |
| 18 | The bulkhead's applied but the fallback's missing | The fallback's on the bulkhead's rejection too | The BulkheadFullException's becomes a 500 |
| 19 | The `Retry` and the `CircuitBreaker` order swapped | The breaker outside the retry | The breaker's counts the retries as separate's calls |
| 20 | The resilience's applied to the local's calls | Only the remote's calls | The latency's budget's wasted |

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner
**Q: What is a circuit breaker?**
**A:** A pattern that stops calls to a failing dependency after repeated
failures, so you fail fast instead of waiting for timeouts. It has three states:
CLOSED (normal, counting failures), OPEN (failing fast immediately), and
HALF_OPEN (letting a few probe requests through to test whether the dependency
recovered). The point isn't just to protect the dependency — it's to protect
**your own** threads and connections from being consumed by a dependency that's
already known to be down.

**Q: What does a timeout do?**
**A:** It bounds how long you wait for a call. Every network call needs one,
because the default in most HTTP clients is *infinite* — a hung dependency
otherwise holds your thread forever. The timeout also bounds your throughput:
with N threads and a T-second timeout, your maximum throughput is N/T requests
per second.

### 🟡 Intermediate
**Q: How do you decide whether to retry a failed call?**
**A:** Four gates. (1) **Idempotency** — only retry if the operation is
idempotent or has an idempotency key; a retried "create order" duplicates. (2)
**The error's class** — retry only transient failures: connection errors, timeouts
(on reads, or on writes with a key), 503, 429. Never a 400/401/403/404/422. (3)
**The budget** — the total time spent retrying must be a small fraction of the
caller's remaining deadline (the SRE rule of thumb is ~10%), otherwise you're
multiplying the load. (4) **The mechanism** — exponential backoff with
**jitter** (full or equal), a bounded attempt count, and the jitter is
non-negotiable: without it, every client retries at the same instants and
re-creates the thundering herd.

**Q: What's the purpose of a bulkhead?**
**A:** To isolate the resources per dependency, so one slow dependency can't
consume all your threads or connections. It's named after the watertight
compartments on a ship. Without it, a slow catalogue service can block every
one of your 200 threads and take down your entire API — not just the catalogue
calls. The implementation is either a semaphore (cheap, but the waiting caller
still holds its thread) or a dedicated thread pool (real isolation, but more
resources). The key detail with the semaphore: the wait timeout must be short,
otherwise you've just moved the blocking.

### 🔴 Advanced / Senior
**Q: How do you prevent one slow dependency from taking down your whole
service?**
**A:** A layered defence, and the order matters. **Timeout** — bounds how long
a single call can hold a thread. **Bulkhead** — a per-dependency limit on
concurrent calls, so the catalogue's calls can consume at most N of your
threads; the rest of the API keeps working. **Circuit breaker** — after a
threshold of failures or slow calls, fail fast without touching the network,
so the load on the sick dependency drops to zero and your threads are freed
immediately. **Retry with a bounded budget** — for the transient failures only,
so a brief blip doesn't fail the request, but a sustained outage doesn't
multiply the load. **Fallback** — degrade gracefully so the user gets a
partial or stale answer instead of an error. And the **observability** — the
breaker's state, the slow-call rate (which is the early warning, before the
error rate moves), and the bulkhead's rejection count are all metrics you need
on a dashboard with alerts. The key insight: a **slow** dependency is more
dangerous than a **down** one, because a down one fails fast and a slow one
consumes your threads — so the breaker must be configured with
`slowCallRateThreshold` as well as the failure rate.

**Q: How do you decide the timeout values?**
**A:** From the budget, not by guessing. Start from the end-to-end SLO: if the
API must respond in 500ms, that's the total budget. Subtract the known costs
(processing, serialization, the database) and the remaining is what the
downstream calls can consume. Then set each call's timeout above the
dependency's measured p99 (so normal variance doesn't trip it) but below its
share of the budget. Watch the timeout *rate* as a signal: if it's > 1%, the
timeout is probably wrong or the dependency is degraded. And propagate
**deadlines** rather than using independent timeouts — otherwise a chain of
four calls at 3s each can take 12s inside a 3s budget. The derived rule: each
hop's timeout must be less than the caller's remaining budget minus the
estimated downstream processing, and if the remaining budget is too small to do
useful work, fail immediately rather than start.

### ⚫ Expert / Principal
**Q: "Our service degrades badly when one dependency slows down, even though we
have a circuit breaker. What's likely wrong?"**
>
> **The framing: "having a circuit breaker" and "the breaker being effective
> are different things, and there are four's distinct's failure's modes here —
> I want to identify which one before prescribing a fix."** (1) **The breaker
> isn't tripping on the slowness.** If it's only configured with
> `failureRateThreshold` and no `slowCallRateThreshold`/
> `slowCallDurationThreshold`, then a dependency that is *slow but succeeding*
> never trips it — and a slow dependency is the more dangerous case, because it
> holds your threads for the full timeout instead of failing fast. The
> **fix**: set the slow-call threshold to the dependency's p95 and trip the
> breaker on the slow-call *rate*, not just the failure rate. This is the
> **most common** cause, and the symptom is distinctive: the breaker's state
> stays CLOSED while the latency's rises.
> (2) **The breaker is tripping too late.** The `minimumNumberOfCalls` and the
> `slidingWindowSize` determine the **detection's latency**: with a 20-call
> window and 50% threshold, you need ~10 failures to trip — and during the
> detection's window, every call is taking the full timeout. At a high's RPS
> that's 10's worth of blocked's threads. Worse, if the traffic is *low* (the
> 3am case), the breaker may never's evaluate at all. The **fix**: the
> window's size should be sized to the *detection's latency* you can afford
> (`windowSize / RPS` seconds), and the `slowCallRateThreshold` gives you a
> much earlier's signal than the failure's rate.
> (3) **The bulkhead's missing or mis-configured.** Even with the breaker
> tripping, the calls *during* the detection's window have already consumed
> the threads. If the bulkhead is absent, one slow dependency takes all your
> threads and the whole API dies — the breaker only helps *after* it trips. If
> the bulkhead is present but its `maxWaitDuration` is long (or the
> `ThreadPoolBulkhead`'s queue is big), the callers are still blocked — just on
> the semaphore instead of the socket. The **fix**: the semaphore's wait's
> timeout must be short (50-100ms), and the thread pool's queue's capacity
> must be small.
> (4) **The retries are amplifying it.** If the retry's budget is unbounded or
> the backoff has no jitter, the sick dependency's load is multiplied, and the
> breaker's failure's *rate* is computed over a window that includes the
> retries — so the detection's window's *duration* is inflated by the retries.
> The **fix**: bound the retry's budget, add the jitter, and — importantly —
> make sure the **breaker's failure's counting** matches your intent (the
> Resilience4j's default counts each attempt separately, which is arguably
> correct for the *dependency's* health but wrong if you want "the request
> failed" semantics).
> **And the fifth's thing I'd add that nobody asks about: the health's
> indicator.** If the breaker's state is wired to the **readiness's probe**,
> then when the breaker opens, the pod's becomes unready and gets **restarted**
> — and the new pod's starts with a fresh, closed breaker and immediately
> hammers the dependency again. That's the **restart's storm** that turns a
> dependency's brownout into a full's outage. The breaker's state should be a
> **metric and an alert**, not a readiness's probe (except for the truly's
> critical's dependencies, and even then with a long's grace's period).
> **The measurement's plan:** the breaker's state (a gauge), the
> slow-call's rate (the early's warning), the bulkhead's rejection's count
> (the saturation's signal), the timeout's rate (the misconfiguration's
> signal), and — the one that tells the whole's story — the **thread's pool's
> utilisation** and the **queue's depth**. If the threads are at 100% while
> the breaker's is CLOSED, the breaker's detection is too slow or not
> configured for slowness. And the **design's principle** I'd state: "the
> resilience's pattern's job is to bound the **blast's radius**, and each
> layer bounds a different's dimension — the timeout bounds the **time**, the
> breaker bounds the **rate**, the bulkhead bounds the **capacity**, and the
> fallback bounds the **impact**. If any one of them is missing or
> mis-tuned, the failure's shape changes from a degradation to an outage, and
> it usually does so in the *worst* way."

---

## 9. Counter-Questions & Traps

### CQ1. "Why not just retry more times instead of a circuit breaker?"
- **Naive answer:** "Retries handle transient failures." ← the trap
- **The trap:** The interviewer wants you to see the **load's amplification** and
the **latency's budget**.
- **Expert answer:** Retries and the breaker solve different problems, and using
retries alone is dangerous. A retry is a **multiplier** on the load: if the
dependency is down and you retry 3 times, the load on it is 3× — and the
latency for the caller is up to 3× the timeout. The breaker is a **multiplier of
zero**: once it's open, the load is 0% and the latency is ~0 (an instant
rejection). So the correct's design is: **the retry handles the brief's
transient's failures** (a single network blip — the retry succeeds and the
user never's knows), and **the breaker handles the sustained's failure** (the
dependency is down for 30 seconds — the retries would be pure's waste and
pure's harm). The **magnitudes**: the retry's budget should be a small's
fraction of the request's deadline (the SRE's ~10% rule), so the retries'
time is bounded; the breaker's `waitDurationInOpenState` (30s) means the
dependency gets 30 seconds of relief. And the **question's premise** is worth
challenging: "if you rely only on the retries, you're relying on the
dependency's recovery being fast — but a down's dependency stays down for
minutes, and 3× the load makes the recovery *slower*. The retries and the
breaker are **complementary**: the retries smooth the brief's blips, the
breaker protects the sustained's outage."

### CQ2. "Isn't a circuit breaker just hiding a real problem?"
- **Naive answer:** "Yes, it masks the failure." ← the trap
- **The trap:** The interviewer wants you to distinguish the **breaker's**
purpose from the **underlying's** issue.
- **Expert answer:** The breaker isn't hiding it — it's **containing** it while
the underlying's issue is diagnosed. The distinction is between the **failure's
symptom** (the errors, the latency — which the breaker stops) and the
**failure's cause** (the bad's deploy, the database's lock — which the breaker
doesn't touch and shouldn't). The **analogy**: the breaker is a **circuit's
breaker** in an electrical's system — it doesn't fix the short, it prevents the
fire from taking the whole's building. The **key's requirement** is that the
breaker's state is **observable and alerted**: the breaker's state must be a
dashboard and an alert, so the "hiding" is actually the opposite — it makes
the problem's boundary's **explicit** and **fast** (the breaker's state changes
in seconds, versus the error's rate which might take minutes to show up in the
aggregate). The **right's practice**: (1) the breaker's state is a metric and
an alert with a **runbook** ("the catalogue's breaker is open — check the
catalogue's health, don't restart the orders' pods"); (2) the breaker's
`waitDurationInOpenState` should be short enough that it **recovers
automatically** when the dependency does; (3) the **half-open's probe** is a
natural's health's check — it verifies the recovery rather than assuming it.
And the **honest's caveat**: the breaker can hide a **slow's leak** — if the
breaker's thresholds are too loose, the dependency's slowness is masked by a
breaker that trips too late. That's why the slow-call's rate's metric matters:
it shows the dependency degrading *while* the breaker is still closed.

### CQ3. "What if the dependency is slow but eventually succeeds — is a timeout the
right choice?"
- **Naive answer:** "Increase the timeout." ← the trap
- **The trap:** The interviewer wants you to understand the **timeout's** effect
on the **capacity**.
- **Expert answer:** Increasing the timeout is one of the worst's responses to a
slow dependency, because of Little's Law: with N threads and a timeout T, the
maximum throughput is N/T. Doubling the timeout **halves** your maximum
throughput. If the dependency's p99 is 5 seconds and you set the timeout to 6,
you've cut your capacity in half (or worse) to accommodate the slow's
dependency — and the users' experience is a 5-second response time, which is
its own failure. The **right's approach** is the layered one: (1) Keep the
timeout's value **tight** (bounding the resource's consumption) and accept that
the slow's calls fail. (2) The **circuit's breaker** with a slow-call's
threshold stops hammering it. (3) The **fallback** gives the user something
usable (a stale's value, a partial's result). (4) **Fix the dependency** —
that's the real's answer, and the slowness is a symptom of a capacity's
problem (a missing's index, a lock's contention, an undersized's pool, a
replica's lag) and the timeout's increase just moves the pressure. There are
legitimate cases for a generous's timeout — a batch's job with a 10-minute's
budget, or a dependency whose *normal* behaviour is slow (the report's
generator) — but those should be **separate's endpoints** with their own's
budget, not the interactive's path. And the **honest's framing**: "the timeout's
value isn't a knob for the dependency's speed; it's a knob for *your* capacity.
A slow dependency's p99 of 5 seconds is a **capacity's problem** in that
dependency, and the correct's response is to fail fast, degrade, and fix the
capacity — not to make your own's service 5 seconds slower and half as
capable."

### CQ4. "Why does the bulkhead with a semaphore still block the caller's thread?"
- **Naive answer:** "The bulkhead protects you." ← the trap
- **The trap:** The interviewer wants the **precise's** semantics of the two's
bulkhead's implementations.
- **Expert answer:** Because the semaphore's bulkhead's protection is
**admission's control**, not **isolation's**. When the permits are exhausted,
the caller either (a) **waits** on `semaphore.acquire()` — and that wait
happens on the caller's thread, so the caller's thread is still consumed (the
dependency is protected, but the caller's capacity isn't); or (b) **fails fast**
with `BulkheadFullException` (the `tryAcquire` path, which the Resilience4j's
`executeSupplier` uses) — in which case the caller's thread is freed
immediately. So the semaphore's bulkhead is only fully protective if you use the
**non-blocking's** variant or set a very short `maxWaitDuration` (50-100ms).
The `ThreadPoolBulkhead` gives a **real's isolation** — the work happens on a
dedicated's pool, so the caller's thread isn't doing the waiting — but in a
**blocking's** API the caller still waits for the future, so you haven't fully
escaped the problem; the full's escape is the **reactive's** API, where the
caller's thread is freed and the completion is delivered later. The
**practical's conclusion**: (1) Use the semaphore with a **short's wait** and
the **fallback** for the rejection. (2) Remember the database's pool is
**already** a bulkhead — HikariCP's `maximumPoolSize` is the permits — so
don't forget to size it deliberately and to give the slow's query types their
own's pool. (3) In the reactive's stack, the bulkhead's semaphore is the right
tool, because the waiting doesn't consume a thread.

### CQ5. "How do you test a circuit breaker?"
- **Naive answer:** "Unit test that the exception's thrown." ← the trap
- **The trap:** The interviewer wants you to know the **state's transitions**
must be tested, and that a **timing-dependent's** test is flaky.
- **Expert answer:** The breaker's behaviour is a **state's machine**, so the
test must cover the **transitions**, not just the exception. The right's
approach: (1) **The deterministic's unit's test** — use Resilience4j's
`TestTimeLimiter`/the `Awaitility` library to advance the breaker's clock
(the `CircuitBreaker` accepts a `Clock` — inject a mutable one in the test),
so you can trip it, advance the `waitDurationInOpenState`, and observe the
HALF_OPEN's probes without sleeping. (2) **The scenario's tests**: N failures
below the minimum's calls → stays CLOSED; N failures at the threshold → OPEN;
a call while OPEN → `CallNotPermittedException` **and no network call made**
(verify with a mock's verification — this is the important's assertion); after
the wait → HALF_OPEN → the probe's success → CLOSED; the probe's failure →
OPEN again. (3) **The slow-call's test** — a call that takes longer than the
`slowCallDurationThreshold` counts as a failure for the *rate* (but the
`recordException` doesn't apply). (4) **The integration's test** with a
**WireMock** or a **Testcontainers**-based fault's injection: stub the
dependency to return 500s, and assert the client's behaviour (the errors, the
latency, the fallbacks) and the **metrics** (the breaker's state gauge). The
**anti-flake's rule**: never `Thread.sleep` in the test — use the injectable's
clock or `Awaitility`'s polling. And the **what to actually assert**: not
"an exception is thrown" but "**the dependency was called N times and no more**
" — that's the property that matters (the load's protection).

### CQ6. "How do you handle a dependency that is intermittently slow (a 1% p99
spike)?"
- **Naive answer:** "Set the timeout higher." ← the trap
- **The trap:** The interviewer wants you to distinguish the **tail's** problem
from the **bulk's** problem, and to know that the average's OK.
- **Expert answer:** A 1% tail's spike is a **tail's** problem, and the fix is
different from the "the dependency's consistently slow" case. The first's
insight: with a 1% tail, **most of the requests are fine**, so any's
whole-service degradation (a breaker that trips on a few's slow calls) is
**disproportionate** — you'd fail 99% of the requests that would have
succeeded. So: (1) **Don't trip the breaker on a small's number of slow calls**
— the `minimumNumberOfCalls` and the percentage's thresholds must be set so a
1% tail doesn't open it (the `slowCallRateThreshold: 100` default with a
`slowCallDurationThreshold` at the p99 is reasonable; the failure's rate's
threshold at 50% is not triggered by a 1% tail). (2) **The timeout's value**
should be set at the **p99.9** (not the p99, not the p50) — the timeout's
budget is where you say "this one's too slow", and if 1% of the calls are
legitimately slower than your timeout, you're failing 1% of your requests
unnecessarily. (3) **The hedge's requests** — for the very's tail, send a
second request to another replica after ~50% of the p99's budget has elapsed
and take the first's response. The extra load is small (only on the slow's
path) and the tail improvement is dramatic (this is the "tail at scale" trick
from Google's paper). (4) **The load's balancing** — prefer the least's
request's time (the least's response's time's algorithm) so the slow's
replica's traffic's reduced. (5) **The metric's discipline** — track the
**p99.9** and the timeout's rate; the p99's average hides the tail's story.
The **honest's framing**: "a 1% tail's spike is a **hedge's** problem, not a
breaker's problem. Tripping the breaker would fail 99% of the requests that
would have succeeded. The right's tools are the **timeout at the p99.9**, the
**hedge's requests** (the second's attempt after 50% of the budget), and the
**least's request's time's load's balancing**. The breaker's thresholds must be
set so they DON'T trip on a 1% tail — which is exactly what the
`minimumNumberOfCalls` and the `slowCallRateThreshold` are for."

### CQ7. "Why not use a queue with a timeout instead of a bulkhead?"
- **Naive answer:** "A bounded's queue with a timeout's the same's thing." ← the
trap
- **The trap:** The interviewer wants you to see the difference between
**shaping** (the queue) and **isolating** (the bulkhead).
- **Expert answer:** They solve different problems. A **bounded's queue with a
timeout** is a **shaping's** mechanism: it smooths the bursts and it bounds
the *waiting's* duration (the caller waits at most T). But during the wait, the
caller **still holds its thread** (in a blocking's API), so the queue's
capacity in threads is exactly the number of waiting's callers. A **bulkhead**
is an **isolation's** mechanism: it caps the **concurrent's demand** on the
dependency, so the dependency can't receive more than N's concurrent calls
regardless of how many callers are waiting. The difference's **observable
behaviour** under overload: with a queue only, a slow's dependency (10s per
call) with a 100-caller's queue and a 1s wait's timeout → the callers spend
1s each waiting (100 threads' worth of latency added to every request) and the
dependency still gets 100's concurrent calls. With a bulkhead, the dependency
gets 20's concurrent calls, the other 80's callers are **rejected immediately**
(the fallback's path), and the user-facing latency is *lower*, not higher.
The **right's combination**: the bulkhead for the isolation (reject the excess
fast), the queue for the smoothing (absorb the brief's bursts within the
bulkhead's capacity), and the timeout for the bound. And the **honest's
caveat**: the queue's capacity must be **small** — a large's queue converts a
latency's problem into a memory's problem and then into an outage's, because
the requests waiting in the queue are already stale by the time they're
served.

### CQ8. "Your fallback is returning stale data. How do you know that's OK?"
- **Naive answer:** "The TTL is short enough." ← the trap
- **The trap:** The interviewer wants you to connect the fallback's
correctness to the **domain's** semantics.
- **Expert answer:** The TTL is a start, but the real's question is **what
stale's data means in this domain**. A **price's** staleness is a business's
risk (you might sell below's cost, or above the market and lose the sale) — so
it needs a **business's mechanism**: the "the price's pending's verification"
state with a background's re-check, or a **max's staleness** bound (if the
price's more than 5 minutes old, don't use it — fail honestly). A
**product's description's** staleness is harmless — a slightly outdated's
description is fine. A **stock's level's** staleness is a correctness's
problem (you'd oversell) — so it needs a **reservation** (the inventory's
reserve's call, which is the authoritative's write) rather than a cache. A
**user's profile's** staleness is fine. So the fallback's design is a
**domain's decision**, and the engineering's process is: for each's dependency,
write down (1) what stale's data means, (2) the maximum's acceptable's
staleness, (3) the business's mechanism when it's exceeded, (4) the **user's
experience** during the staleness (a "the price's confirming" banner is
honest; a wrong's price is not). And the **general's principle**: a fallback's
that returns **stale** data must be **visibly** stale to the user when the
staleness matters — the "the price's pending" is a **state**, not a lie. And
the **measurement**: track the fallback's invocation's count and its
staleness's distribution — a rising fallback's rate is the early's warning
that the dependency is degrading, and a rising staleness's distribution is the
signal that the **fallback is degrading the experience** and needs a better's
degradation's path.

### CQ9. "How do you prevent the retry from making a partial failure worse?"
- **Naive answer:** "Limit the retries to 3." ← the trap
- **The trap:** The interviewer wants you to know the **budget's** model, not
the count's model.
- **Expert answer:** The attempt count is the wrong's unit of control. What
matters is the **retry's budget** — the total's time and the load's
amplification. A 3-retry's policy with a 2s timeout per attempt can consume 6
seconds (and 3× the load); a 2-retry's policy with a 200ms timeout consumes 600
ms. The correct's controls: (1) **The total's time's budget** — the retries
(including the backoff's sleeps) must fit within a small's fraction of the
caller's remaining's deadline; the SRE's rule of thumb is 10%. (2) **The
load's amplification's cap** — the retries mean the dependency sees up to
`maxAttempts` × the normal's load. At a high's RPS, even 2's attempts is 2×
the load on a dependency that's already's struggling. (3) **The circuit's
breaker's interaction** — the breaker must be *outside* the retry (so it counts
the logical's failures, and once it's open, no retries at all). In the
Resilience4j's decoration, the ordering is Bulkhead → TimeLimiter →
CircuitBreaker → Retry, which is correct. (4) **The propagation's** — the
budget must be passed down the call's chain, so a deep's call doesn't retry
past the top's deadline. (5) **The load's shedding** — under a sustained's
overload, the *right* response is to **stop** retrying (fail fast, reject with
a `Retry-After`) rather than to keep trying. That's the opposite of the naive's
intuition, and it's why the **circuit's breaker** is the primary's mechanism
and the retry is the secondary's one. The **honest's summary**: "the retry's
budget is the unit of control, not the retry's count. The budget has two's
dimensions — the **time** (it must fit inside the caller's remaining's
deadline) and the **load** (the amplification's factor must be acceptable to
the dependency). And the budget has to be **propagated** — a deep's call that
retries past the top's deadline is a latency's bug that looks like a
timeout's bug."

### CQ10. "Do you need resilience on calls to the database?"
- **Naive answer:** "No, the database's in-process." ← the trap
- **The trap:** The interviewer wants you to know that the DB is a **network's
dependency** with its own's failure's modes.
- **Expert answer:** Yes, and it's arguably the **most important** dependency
to be resilient about. The database is a network's hop (a TCP's connection over
a network that can partition), and it has failure's modes the application
doesn't control: the connection's pool's exhaustion, the lock's wait, the
replica's lag, the storage's slowness, the failover's promotion. The
**specific's patterns** for the database: (1) **The pool IS the bulkhead** —
HikariCP's `maximumPoolSize` is the isolation's boundary, and the
`connectionTimeout` is the **timeout** (a 30s's default means a request can
wait 30 seconds for a connection — a classic's latency's bug; it should be
~2-3s, shorter than the request's deadline). (2) The **query's timeout** —
PostgreSQL's `statement_timeout` and MySQL's `max_execution_time`, set on the
**session** or per's statement, so a pathological's query doesn't hold a
connection. (3) The **read's replica's lag** — a write followed by a read from
the replica can return stale's data (the classic's "the user saves and doesn't
see the change" bug) — and the **lag's monitoring** matters. (4) The
**failover's handling** — a promotion means a brief's unavailability and a
possible's split's brain; the connection's pool must **recover** (the
validation's query, the `connectionTimeout`, the retry's logic). (5) The
**circuit's breaker** on the database's calls is usually **not** the right's
pattern (the database is a hard's dependency — you can't serve without it), but
the **bulkhead** and the **timeouts** absolutely are. And the **honest's
distinction**: the resilience's patterns are usually about *degrading*; for the
database there's no meaningful's degradation (except the **read's replica's
fallback** — serve a stale's read from the replica when the primary's down, or
fail the writes but serve the reads). The **question's real's answer**: "the
database needs the **timeouts** and the **bulkhead** (the pool) more than it
needs the breaker, and the two's database's specifics — the
`connectionTimeout`, the `statement_timeout`, and the replica's lag — are where
the real's outages live. The breaker on the database is a **last's resort**,
because there's no useful's fallback when the primary's down."

### CQ11. "How do you decide whether a failure should be retried or the request
should be failed immediately?"
- **Naive answer:** "Retry everything's transient." ← the trap
- **The trap:** The interviewer wants the **risk's analysis** of the retry's
consequences.
- **Expert answer:** The decision is a **trade between the failure's likelihood
and the failure's cost**, and the retry's cost is the amplification's factor.
Ask: (1) **Is the failure likely transient?** A network's reset, a 503 from an
overloaded's service, a 429 — yes. A 500 from a NullPointerException, a 400 —
no. (2) **What does a retry cost?** The **latency** (the budget) and the
**load** (the amplification). If the dependency is degraded, the retry's load
makes it worse — so the retry's cost isn't constant; it's *highest exactly when
you most need the retry to be cheap*. (3) **Is the operation's outcome
knowable?** This is the subtle's one: a **read's** timeout is safe to retry
(the read either happened or didn't, and reads are idempotent). A **write's**
timeout is **not** safe unless there's an idempotency's key — the write may
have committed. So the write's timeout's correct's handling is: retry **with
the key**, or **don't retry and reconcile** (query the state by the client's
ID). (4) **Is the user waiting?** If the request's already been slow and the
user's about to give up, a retry that adds 2 seconds is worse than an error.
The **decision's framework**: a table per dependency — the error's class, the
retryable's yes/no, the maximum's attempts, the budget's share. And the
**operational's refinement**: the retryable's classification should be
**explicit in the code** (the exception's hierarchy — `TransientDependency
Exception` vs `PermanentDependencyException`) rather than inferred from the
status's code at the call's site, so the decision is consistent and testable.
That's the pattern in the code section: `CatalogueUnavailableException`
(retryable) vs `CatalogueClientException` (not) — and the breaker's and the
retry's `ignoreExceptions` reference the **same** classification, so the two
can't disagree.

### CQ12. "What happens during a partial outage — one instance of a service is
down?"
- **Naive answer:** "The load's balancer routes around it." ← the trap
- **The trap:** The interviewer wants you to connect the **partial's failure**
to the **health's checks** and the **retry's** behaviour.
- **Expert answer:** A partial's outage is the **most common** real's failure,
and it has a specific's signature: the **error's rate** is roughly
`1/instances` (25% for 4's instances), and the **latency** is normal (the
healthy's instances are fast) — which means it looks like an **application's
bug** (a specific SKU, a specific tenant) rather than an infrastructure's
problem. The **causes**: the instance's process is dead but the LB's health
check's passing (a check on the wrong's port, or a check that only verifies the
process is alive, not that it's serving), the instance's is up but its
**dependency's** is failing (so it can't serve), the instance's is up but it's
**out of memory** and thrashing, or the instance's is **zombie** (the process
is alive but the request's path is broken). The **fixes**: (1) The health's
check must reflect the **ability to serve**, not the **liveness** — the
readiness's probe should check the critical's dependencies (the database's
connectivity), and the liveness's probe should only check the process (a
failing liveness's probe on a dependency's outage causes a **restart's
storm**). (2) The **client-side's retry** absorbs the transient's failures of
the dead's instance — but ⚠ the retry's **without a retry-on-connection-error**
won't help, and the retry must be **instance-aware** enough to try a different
one (a connection's error → the next's attempt goes elsewhere). (3) The
**circuit's breaker** is the wrong's tool here — the dependency is 75% healthy,
so the breaker won't trip (the failure's rate is 25%, below the threshold) and
it shouldn't (the service is mostly working). (4) The **load's balancing's**
algorithm matters: the **least's request's time** or the **least's connections**
would avoid the sick's instance if it's slow; the round's robin would keep
sending to it. And the **observability's** key: the **per-instance's** metrics
(the CPU, the error's rate, the request's count) are what reveal the partial's
outage — a round's robin's balancer makes the *cluster's* average look fine
while one instance's is at 100% errors. The **honest's framing**: "the
partial's outage is the one that looks like an application's bug, and the
diagnosis's key is the **per-instance's** metrics and the **per-instance's**
logs (the correlation's id tells you which instance served the failed's
request). The resilience's patterns' role is limited — the breaker won't trip
(the service's mostly's up), and the retry's job is to route around the dead's
instance. The real's fix is the **readiness's probe's correctness** — the
health's check must be able to tell the difference between 'the process is
alive' and 'the process can serve'."

---

## 10. Rapid-Fire MCQs

**1.** The default's read's timeout of Spring's `RestClient`:
A) 5s  B) 30s  C) Infinite  D) 1s
**✅ C**

**2.** The circuit's breaker's states:
A) OPEN/CLOSED  B) CLOSED/OPEN/HALF_OPEN  C) UP/DOWN  D) GREEN/YELLOW/RED
**✅ B**

**3.** The formula for the maximum's throughput with a thread's pool:
A) threads × timeout  B) threads / timeout  C) timeout / threads  D) threads² / timeout
**✅ B**

**4.** The `minimumNumberOfCalls`'s purpose:
A) The load's limit  B) Prevent the breaker from tripping on a small's sample  C) The retry's limit  D) The timeout's budget
**✅ B**

**5.** The retry's jitter's purpose:
A) The randomness  B) The thundering herd's prevention  C) The encryption  D) The ordering
**✅ B**

**6.** The bulkhead's primary's purpose:
A) The rate's limiting  B) The resource's isolation per's dependency  C) The caching  D) The auth
**✅ D** ❌ — **B**

**7.** The `slowCallRateThreshold`'s purpose:
A) The timeout's setting  B) Trip the breaker on slowness, not just's failures  C) The retry's delay  D) The pool's sizing
**✅ B**

**8.** The retry's budget's recommended's share of the request's deadline:
A) 100%  B) ~10%  C) ~50%  D) 0%
**✅ B**

**9.** The correct's nesting's order (outermost → innermost):
A) Retry → Breaker → Timeout  B) Bulkhead → Timeout → Breaker → Retry  C) Timeout → Bulkhead → Retry → Breaker  D) Breaker → Retry → Bulkhead
**✅ B**

**10.** A `404` from a dependency should:
A) Trip the breaker  B) Be ignored by the breaker and the retry  C) Be retried  D) Be a `500`
**✅ B**

**11.** HikariCP's `connectionTimeout` too long (30s) causes:
A) Nothing  B) The requests to wait 30s for a connection  C) The pool to grow  D) The DB to die
**✅ B**

**12.** The `ThreadPoolBulkhead`'s queue's capacity should be:
A) Huge  B) Small  C) Infinite  D) Zero
**✅ B**

**13.** The retry's on a write's timeout requires:
A) Nothing  B) The idempotency's key  C) The breaker's OPEN  D) The bulkhead
**✅ B**

**14.** The best's backoff for a distributed's system:
A) Fixed  B) Exponential  C) Exponential + jitter  D) Random
**✅ C**

**15.** The `TimeLimiter`'s `cancelRunningFuture: true` means:
A) The future's cancelled  B) The thread's interrupted  C) The call's retried  D) The breaker opens
**✅ B**

---

## 11. Hands-On Exercises

**🧪 Lab 1 — The cascade's reproduction**
1. Create a `SlowController` that sleeps 30s.
2. Call it with a `RestClient` with **no** timeout; send 300 concurrent's
   requests.
3. Observe: the server's threads are exhausted, the health check fails, the
   p99 is unbounded.
4. Add a 2s timeout + a bulkhead of 20; observe the difference.

**🧪 Lab 2 — The breaker's state's transitions**
1. Configure a breaker (window 10, threshold 50%, min calls 5).
2. Call a failing endpoint 5 times → observe the state (OPEN).
3. Call again → observe the `CallNotPermittedException` and **no** network call.
4. Wait the `waitDuration` → observe HALF_OPEN → a success → CLOSED.

**🧪 Lab 3 — The jitter's difference**
1. Write a test that fires 1,000's concurrent's calls at a failing's endpoint.
2. Count the calls per 100ms's bucket, with a fixed's backoff.
3. Repeat with the exponential's **random's** backoff.
4. Observe: the fixed's version's spikes (the synchronised's buckets), the
   random's version's smooth.

**🧪 Lab 4 — The fallback's ladder**
1. Kill the catalogue's service; call the checkout's endpoint.
2. Observe the order's created with `priceStatus = PENDING`.
3. Restore the catalogue; run the background's re-check's job.
4. Observe the price's confirmed.

---

## 12. Code Challenges

### Challenge 1 — The resilient's client (medium)
**Goal:** A `ResilientHttpClient` wrapper with the timeout, the retry with the
jitter, the breaker, the bulkhead, and the fallback — with the **error's
classification** as a shared's hierarchy so the retry and the breaker can't
disagree.
**Requirements:** the exception's hierarchy, the config's properties, the
metrics, the WireMock's tests (the timeout, the 500s, the slow's responses).

### Challenge 2 — The degradation's ladder (hard)
**Goal:** Implement the **domain's degradation** for 3's dependencies (the
catalogue → the stale's price, the inventory → the reservation's pending, the
payment → the pending's state with a webhook), with the **reconciliation's
job** that re-checks the pending's states when the dependency's recovers.
**Requirements:** the pending's states, the background's job, the retry's
scheduler, the metrics (the pending's count, the age's distribution).

### Challenge 3 — The chaos's harness (hard)
**Goal:** A test's harness that injects the fault's modes (the latency's
injection, the 500's injection, the connection's drop, the partial's instance's
death) into a dependency and **asserts** the system's's behaviour: the
load's bounded, the latency's bounded, the fallback's used, the breaker
tripped, the **no's thread's exhaustion**.
**Requirements:** the fault's injection's abstraction, the assertions'
library, the before/after's metrics' comparison.

---

## 13. Scenario-Based Questions

**S1.** *"Our payment service is a third-party gateway. It occasionally takes
30 seconds to respond (their API is slow) and sometimes times out. Our
checkout's p99 is 12 seconds and customers are complaining. What's wrong and
what do you do?"**
>
> **Ideal answer:** (1) **The framing:** this is a **classic's "the slow's
dependency"** case, and the 12s p99 tells you the **timeouts are too long or
absent** — because the gateway's 30s response's latency can only produce a 12s
p99 if your timeout is around 10-12s (or if the retries are stacking). Before
anything: **check the current's timeout and the retry's configuration** — if
the timeout is 10s with 2's retries, the worst case is 30s+ and the p99 of 12s
fits perfectly. (2) **The diagnosis:**
- (a) **The timeout's value** — a 10-30s's timeout on a user's request's path
  is wrong for an interactive's API. The user's tolerance is ~2-3 seconds.
- (b) **The retries** — if the retries are on, the **latency's multiplier** is
  3× and the **load's multiplier** is 3× on a third-party's gateway that's
  already's struggling. And you might be **double-charging**: if the write's
  timeout fires after the gateway's actually charged the card, the retry
  charges again ⚠ — this is a **money's bug**, not just a latency's one.
- (c) **The connection's pool's exhaustion** — with a 30s's timeout, a few's
  slow's calls consume your whole's pool.
- (d) **The thread's exhaustion** — 200's threads × a 10s's hold = a 20 RPS
  ceiling on the checkout's API.
(3) **The fix's plan, in the order:**
- **The timeout**: 2s for the interactive's path. ⚠ The critical's detail: the
  **payment's authorisation must be idempotent**. The **client-generated's
  idempotency's key** (a UUID per the checkout's attempt, passed to the
  gateway) — the gateway's API almost certainly supports one, and it's the
  difference between a safe's retry and a double's charge. Verify the
  gateway's semantics: does it return the **same** result for the same's key?
- **The retry**: only with the key, only for the transient's failures, with
  the **jitter** and a **short** budget (the total's retry's time ≤ 300ms
  inside a 2s's timeout).
- **The circuit's breaker**: on the gateway's slow calls (the
  `slowCallDurationThreshold` = 1.5s) — this is where the slow's call's
  detection matters, because the gateway's mostly's works. With a 50%
  slow-call's rate's threshold, a bad's period trips the breaker and the
  payment's calls stop entirely.
- **The bulkhead**: 20's concurrent's calls to the gateway, so the gateway's
  slowness can't consume the checkout's whole's capacity.
- **The fallback (the important's one)**: **the asynchronous's payment
  model**. The checkout returns **201 with `paymentStatus = PENDING`**, and the
  payment's confirmation arrives **by webhook** (the third-party's gateway
  almost always supports this — it's the standard's pattern for the slow's
  payment's providers). The order's placed, the user's redirected, and the
  confirmation's asynchronous. This removes the gateway's latency from the
  user's path **entirely** — the correct's architectural's answer, not just a
  tuning's one. The **reconciliation's job** handles the webhook's loss
  (poll the gateway's status's API for the pending's payments).
- **The user's experience**: "the payment's processing, we'll email you when
  it's confirmed" — a proper's pending's state with the order's already
  placed.
(4) **The measurement's plan:** the **payment's call's latency's distribution**
(the p50/p95/p99 against the gateway's own's SLA), the **timeout's rate**, the
**breaker's slow-call's rate** (the early's warning), the **double-charge's
count** (the reconciliation's job must compare the gateway's records against
the local's — this is the metric that would have caught the bug), and the
**checkout's conversion's rate** (the business's truth). (5) The senior's
framing: "the p99 of 12 seconds says the **timeouts are wrong for an
interactive's API** — that's the tuning's part, and it's a 30-minute's fix. The
**money's risk** is the more important's finding: if a write's timeout is being
retried without an idempotency's key, you've been **double-charging**
customers, and no amount of latency's tuning fixes that. So the first's step is
to **audit the payment's retry's path for the double-charge's risk** and
reconcile the recent's transactions. The **architectural's answer** is the
**webhook's asynchronous** model — take the gateway's latency out of the
user's path entirely — and it's the right's answer for a third-party's gateway
specifically, because their's latency's is outside your's control and their's
SLA's is not your's SLO's. And the **honest's framing about the third
party**: you don't get to fix the gateway, so your's resilience's budget has to
assume the worst's case; that means the **timeout must be short** (your
capacity's protection), the **breaker must trip on slowness** (their's slow is
your's outage if you let it), and the **fallback must be a domain's state**
(the payment's pending — which is a perfectly legitimate business's state, not
an error's page)."

**S2.** *"We added a circuit breaker to our calls to the inventory service.
Yesterday, inventory had a 5-minute deployment issue (rolling restart). During
that time, our order-creation endpoint started returning errors even though
inventory was mostly healthy. Why?"**
>
> **Ideal answer:** (1) **The framing:** this is the **breaker's threshold's**
> and **window's sizing** problem, and it's one of the most common's
> "the breaker made things worse" scenarios. During a rolling restart, the
> inventory's service has a **mixed's health**: some instances are up, some are
> terminating, and the **requests that land on a terminating's instance fail**
> (the connection's refused, or the instance's mid-request's shutdown). The
> failure's *rate* is therefore elevated (maybe 20-40% during the rollout),
> and the breaker's **failure's rate's threshold** (say 50%) is close enough
> that it **trips** — and once it's OPEN, **100%** of the calls are rejected,
> including the ones that would have succeeded. **The breaker converted a
> partial's degradation into a total's outage.** (2) **The diagnosis:**
confirm the breaker's state in the metrics (`resilience.circuitbreaker.state`
= 1 = OPEN) during the incident's window, and look at the **breaker's
failure's rate** just before it tripped. If it was 30-45% with a 50%
threshold, the trip's expected. Also check the **timing**: the rollout's
duration and the breaker's `waitDurationInOpenState` — if the rollout lasted
5 minutes and the breaker's wait was 30s, the breaker would cycle
(OPEN → HALF_OPEN → probe → fail → OPEN) for the whole 5 minutes, rejecting
everything's continuously. (3) **The fix's plan:**
- **The threshold's tuning**: the breaker's failure's rate's threshold must be
  above the **normal's variance's ceiling**. If the inventory's service
  routinely has a 5% error's rate during the deployments, a 50% threshold is
  too close — a 25% one would be worse. Actually, the correct's framing: the
  threshold should be set so the breaker only trips on a **real's** outage, and
  the **deployments' noise** should be excluded by a different's mechanism. But
  the more's fundamental's fix:
- **The breaker's threshold vs the deployment's noise is a symptom of the
  dependency's deployment's strategy.** The right's fix is at the **dependency's**
  end: the inventory's deployment should be **non-disruptive** (the `preStop`'s
  hook + the graceful's shutdown + `maxUnavailable: 0`), so the failing's
  requests during the rollout go to **zero**. That's the real's fix — the
  breaker's threshold shouldn't have to absorb a self-inflicted's outage.
- **The `waitDurationInOpenState`** should be **long enough** to cover a
  planned's deployment (or the breaker should be **suspended** during the
  planned's maintenance — the Resilience4j's `CircuitBreakerStateMachine` has
  a `transitionToForcedOpenState` / `transitionToClosedState` and the Spring
  Cloud's Circuit Breaker has an actuator's endpoint to transition the state
  manually, and there's the `AutoDisabled`/suspend's API). A **planned's
  deployment's runbook**: suspend the breaker → deploy → resume. (This is
  exactly the operational's tool the interviewer wants to hear you know about.)
- **The half-open's behaviour**: during the breaker's OPEN's period, the
  **half-open's probe** is a **single** call — if it lands on a terminating's
  instance, the breaker re-opens. With a mixed's fleet, the probe's success's
  probability is low. The fix: more's probes (`permittedNumberOfCallsInHalfOpenState`
  = 3-5) and the **slow-call's** distinction (a terminating's instance usually
  **fails fast** — a connection's refused — so it's a failure; but a
  **draining's** instance might be slow, so the slow-call's threshold matters
  too).
- **The "the breaker's threshold" as a non-event**: consider whether the
  breaker should trip on the **failure's rate** at all, versus the
  **availability's** threshold (e.g. trip when the failure's rate > 50% for
  > 30 seconds — a *sustained's* condition rather than a *windowed's* one).
  The Resilience4j's `slowCallRateThreshold` and the window's type
  (`TIME_BASED` with a 60-second's window) affect this.
- **The gateway's alternative**: a **canary's** deployment for the inventory's
  (or a blue/green's) means the traffic's shift is instantaneous and the
  old's instances drain — the failing's window is essentially zero, and the
  breaker never sees the noise. (4) **The prevention:** the **planned's
  deployment's runbook** (suspend the breaker), the **dependency's
  non-disruptive's deployment's** config, and the **breaker's threshold's
  tuning** based on the **observed's baseline's variance** (not a guess —
  look at the historical's error's rate during the past's deployments and set
  the threshold above it). And a **dashboard** on the breaker's failure's rate
  over time, so the next's deployment's window is visible. (5) The senior's
framing: "the breaker did exactly what it was configured to do — the
configuration was wrong. During a rolling's deployment, the dependency's error's
rate is **elevated by design**, and the breaker was **tripping on a
self-inflicted's noise** and converting a **partial's degradation** (a
percentage of the calls failing) into a **total's outage** (100% rejected).
That's the breaker's most dangerous's failure's mode: it can make things
worse, because the **fail-fast's** rejection is applied to the calls that
would have succeeded. The fix has two's levels: the **operational's** — the
planned's deployment's runbook with the breaker's **suspension** (the
Resilience4j's `transitionToForcedOpenState` and the actuator's endpoint
exist for exactly this), so a planned's deployment doesn't trip the breaker;
and the **structural's** — the dependency's **non-disruptive's deployment**,
where the `preStop`'s hook and the graceful's shutdown mean the rollout's
failing's window is zero and the breaker never sees the noise. And the
**threshold's tuning**: the failure's rate's threshold must sit **above the
dependency's normal's variance's ceiling**, and 'normal' includes its
deployments — which means the threshold's calibration needs the
**historical's data** from the past's deployments, not a guess. And the
deeper's point: the breaker's **window's size** determines the **detection's
latency** and the **exposure's duration** — 30s's wait with a 60s's window
means the dependency gets 90 seconds of zero traffic during a brief's
outage, which is the breaker's *protection* working, but it also means a
single's bad's request can take out the dependency for 90 seconds. So the
`waitDurationInOpenState` should be sized to the dependency's *recovery's
time*, and the **half-open's probes** should be more than one (a
terminating's instance fails fast, so a single's probe has a good's chance of
landing on a sick's instance)."

---

## 14. Tricky Interview Questions

**T1. "What happens if the circuit breaker's configuration is changed at
runtime?"**
> In the Spring Cloud Circuit Breaker's YAML, the config is read at the
registry's creation — changing the YAML and restarting is the simple's path.
But the Resilience4j's registry supports **runtime's updates**
programmatically: `circuitBreakerRegistry.circuitBreaker("name", newConfig)`
reconfigures the existing instance (the `CircuitBreakerConfig` can be changed
via the `circuitBreaker.changeConfiguration(...)` API, and the Actuator's
endpoints expose the state transitions). The **important's nuance**: the
breaker's **state** (CLOSED/OPEN) is **preserved** across a reconfiguration
(unless you explicitly transition it), and the **sliding window's** data is
**reset** when the window's type or size changes — so a reconfiguration
effectively gives you a fresh's start on the failure's statistics, which is
usually what you want when you *widen* the threshold after a false's trip.
The **practical's advice**: the threshold's changes should be a **deliberate's,
deployed's change** (with the config's server's refresh if you're using it),
not an ad-hoc's adjustment — but the **state's transitions** (suspending the
breaker during a planned's deployment) *should* be a runtime's operation, and
the **actuator's endpoint** is the right's tool for it. And the honest's
warning: a threshold that gets widened every time the breaker trips is a
**disguised's outage** — you should be fixing the dependency, not relaxing the
protection.

**T2. "Can you use the circuit breaker on a reactive (WebClient) call?"**
> Yes, and you have to, because the reactive's model has no thread's exhaustion
to protect — but it has a different's problem: **the subscription's never's
completes**. Without a timeout, a hung's call leaves the subscription's
pending forever, and the memory and the connection's resources are leaked (the
buffer's capacity is consumed). So the reactive's stack needs the **timeout**
at least as much. The **Spring Cloud Circuit Breaker** supports the reactive's
types (`Mono`/`Flux` return types) and its `ReactorCircuitBreaker` handles
them natively — including the **cancellation's propagation**: when the
caller's `Mono` is cancelled, the Resilience4j's decoration cancels the
underlying's call. The **gotchas**: (1) The **`TimeLimiter`'s
`cancelRunningFuture`** doesn't apply the same's way — the reactive's version
cancels the subscription. (2) The **retry's** must be the **reactive's** retry
(`Retry.backoff(...).jitter(...)`) or the Resilience4j's (which is blocking-
oriented) — using the blocking's `@Retry` on a `Mono`-returning method can
block the event's loop ⚠. (3) The **fallback's** method must return a `Mono`/
`Flux` (or a `Mono.empty()` → the error's propagation). (4) The **bulkhead**
in the reactive's world is the right's isolation's mechanism, because the
waiting doesn't consume a thread. And the **honest's answer**: "yes, and
it's *more* important in the reactive's stack, not less — the reactive's
model doesn't protect you from a hung's dependency, it just moves the problem
from a thread's exhaustion to a memory's leak. The timeout and the breaker
are the same's tools; the retry's implementation's different (the reactive's
`Retry` with the jitter, not the blocking's one)."

**T3. "How do you prevent a retry storm when a service comes back up?"**
> This is the **"thundering herd"** on the recovery's side, and it's the
mirror image of the retry's storm on the failure's side. The mechanisms: (1)
**The jitter** — the primary's defence. With the full's jitter, the retries
are spread over the backoff's window, so the recovery's load ramps gently
rather than spiking. (2) **The circuit's breaker with a `waitDurationInOpenState`
and half-open's probes** — the breaker's OPEN's state means zero's load on the
recovered's service, and the **limited's probes** (3-5) test the recovery's
health *before* the full's traffic's returns. That's the **ramp's** mechanism:
the recovery's traffic is deliberately's limited. (3) **The load's balancer's
slow's start** (the AWS's "the slow's start" algorithm, or the K8s's
`maxSurge` with a gradual's ramp) — the *server's* side of the same's
protection. (4) **The client's retry's budget** — the retries are the
amplification, so bounding them bounds the recovery's spike. (5) The
**readiness's probe's delay** — the recovering's instance shouldn't receive
the traffic until it's actually warm (the JVM's warm-up, the cache's
warm-up, the connection's pool's fill). The **honest's framing**: "the
recovery's spike is the mirror's image of the failure's storm, and the
**jitter** and the **breaker's half-open's probes** are the two's primary's
tools: the jitter's spreads the retries, and the half-open's probes act as
a **deliberate's ramp** — the recovered's service sees a controlled's trickle
instead of a wall's traffic. The **server's side** should do the same with the
**slow's start** and a **readiness's gate**, so the recovery is a
**two's-sided's** coordination."

**T4. "What is the difference between the Retry and the Circuit Breaker in
terms of what they measure?"**
> They measure **different's things at different's levels**. The **retry**
measures **per-call** outcomes and acts **per-call** — it decides whether
*this* attempt should be repeated, and it has no memory beyond the current
call's history (it does count the failures within the current call, and it
respects the `maxAttempts`). The **circuit's breaker** measures **aggregated**
outcomes over a **sliding window** (a count-based window of the last N calls,
or a time-based window) and acts **globally for the dependency** — its state is
shared by all the calls to that dependency, so one call's failure doesn't
open it, but the *pattern* does. The practical's implications: (1) The
breaker's threshold's **calibration** is in terms of the aggregate (a
percentage over a window) while the retry's is in terms of the per-call (the
count). (2) The **order's nesting** matters: with the breaker **outside** the
retry, the breaker sees one "call" (which failed after 3's attempts) and the
failure's rate reflects the *logical's* failure's rate; with the retry
**outside** the breaker, the breaker sees each attempt separately and its
failure's rate reflects the *attempt's* failure's rate — and it would trip
much faster (since the retries are attempts). The Resilience4j's decoration
order (Bulkhead → TimeLimiter → CircuitBreaker → Retry) puts the breaker
outside the retry, which is the right's semantic: "**the logical's request
failed**" is what should count toward the dependency's health. (3) The
**interaction with the metrics**: the breaker's `numberOfBufferedCalls` and
the failure's rate are aggregated, while the retry's metrics
(`resilience4j.retry.calls`) count the attempts. Both are worth alerting on
— but the breaker's state is the "should I even try" signal and the retry's
count is the "the dependency's flaking" signal.

**T5. "Your bulkhead's limit is 20, and the dependency can handle 50
concurrent calls. Why not raise it?"**
> Because the bulkhead's limit isn't only about the dependency's capacity —
it's about **your own** capacity and the **failure's amplification**. The
reasons to keep it below the dependency's theoretical's capacity: (1) **Your
own's threads and connections** — 20's concurrent's calls at a 2s's timeout
consume 20's threads; if the dependency's suddenly's slow (its p99 goes to
10s), those 20's threads are held for 10s each, and your other's traffic's
starves. The bulkhead is sized to protect **you** under the dependency's
*degraded* behaviour, not its healthy's one. (2) **The dependency's
concurrency's limit is a snapshot** — the dependency's capacity's changes
(it's deployed on smaller's instances, it has a seasonal's peak, its
downstream's degraded), and a bulkhead's sized to the peak will be a
liability's in the trough. (3) **The latency's amplification** — a queue's
depth of 50's calls means the last one's caller waits 50 × the call's time.
With a 2s's calls, that's 100 seconds — the calls are useless by the time
they're served. The bulkhead's limit is really a **latency's budget's
constraint**: `limit × call's time ≤ the deadline`. (4) **The fairness's
across the tenants** — a single's global's bulkhead means one tenant's
traffic's can consume the whole's permits; the per-tenant's bulkhead (or the
rate's limiter) prevents that. The **honest's framing**: "the bulkhead's
limit is derived from **the failure's scenario**, not the happy's path.
The right's calculation is: *how many's concurrent's calls to this dependency
can I afford to have in flight, given that each might hold a thread for the
worst's case's timeout?* — that's `my_threads_for_this_dependency /
worst_case_timeout`. And the second's constraint is the **latency's budget**:
the queue's depth's wait's time must stay inside the deadline. The
dependency's own's capacity of 50 is relevant but it's a ceiling, not the
target — because you're designing for the day it's degraded to 10s, not for
the day it's healthy."

---

## 15. Follow-Up Questions Asked by Interviewers

**F1** (after the timeout) → *"What if the timeout fires but the operation
actually succeeded?"**
→ That's the **"the success that's lost"** problem, and it's the reason the
idempotency's key exists. Three's cases: (1) **A read** — it's fine, the
retry re-reads (the only's cost is a duplicate's read). (2) **A write with an
idempotency's key** — the retry with the **same** key returns the **original's
result**, so it's safe. (3) **A write without a key** — you have an
**unknown's outcome**: the write may have committed. The correct's handling is
to **reconcile**: query the resource by the client's generated's ID (which is
the classic's technique — the client generates the order's UUID, so a "did my
order's created?" query is possible), or to check the **provider's status's
API** (the payment's gateway's "what's the status of transaction X?"). If
neither is possible, you must **not retry** and must surface an "unknown's
state" to the user (which the business's design should make visible: "the
payment's processing"). And the general's rule: the timeout's error's
classification differs by the operation's type — a **read's timeout** is
retryable, a **write's timeout** is "unknown's outcome", and the code should
model that difference explicitly (different exception's types → different
retry's configuration) rather than lumping them together. The money's case is
the sharpest: a payment's capture's timeout without an idempotency's key can
**double-charge**, and the reconciliation's job (comparing the provider's
records with the local's) is the only way to detect it.

**F2** (after the breaker) → *"What if the dependency recovers but the breaker
stays open?"*
→ Three's failure's modes to check: (1) **The `waitDurationInOpenState` hasn't
elapsed yet** — the breaker's timer; with
`automaticTransitionFromOpenToHalfOpenEnabled: true`, the transition is
timer-driven, so this resolves itself. (2) **The half-open's probe's failed**
— the probe's landed on a still's sick's instance, so the breaker re-opened.
If the dependency is actually healthy, the probe should succeed; if it doesn't,
look at **which instance** the probe's hit (the per-instance's metrics) —
there may be a **partial's outage** where one instance is broken. (3) **The
exceptions' classification is wrong** — if the probe's throws an exception
that the breaker **ignores**, the breaker would transition to CLOSED, not
stay open. So "stays open" means the probe **did** fail and was recorded.
The **diagnostic's path**: check the breaker's metrics
(`resilience.circuitbreaker.state`, `failure_rate`,
`number_of_buffered_calls`) over the period; check the **application's logs**
for the `CallNotPermittedException` (the rejections) versus the **actual's
errors** (the probes); and look at the **dependency's own's health** at the
same's moment. The **common's causes**: (a) The dependency's recovery is
partial (one instance's still's broken) — the fix is the dependency's
non-disruptive's deployment; (b) The **exception's classification** catches
the probes' failures in a way you didn't expect (e.g. the probe's fails with
a `CatalogueClientException`, which the breaker **ignores**... no, that would
close it — so check the opposite: a **new** exception's type that isn't in the
`recordExceptions` list would be **ignored**, closing it — so if it's staying
open, the exception's IS being recorded, meaning the probe's genuinely
failed); (c) The **dependency's authentication's** or **network's** issue
between your's service and it (a firewall's rule, a mTLS's cert's expiry) —
the probe's fails with a network's error, and no amount of waiting fixes it.
And the **manual's override**: the Resilience4j's Spring Cloud's actuator
exposes the breaker's state and allows the manual's transition
(`POST /actuator/circuitbreakers/{name}` with a `{"state": "CLOSED"}`
body), so an operator can force it closed during an emergency — with the
understanding that the dependency's health's issue is still there.

**F3** (after the bulkhead) → *"How do you size it?"**
> From the **failure's scenario**, not the happy's path. The **formula**:
`the bulkhead's limit = the threads you're willing to give this dependency ÷
the worst-case's timeout`. If you're willing to give the catalogue 20's
threads and the timeout is 2s, then 20's concurrent's calls is the limit. The
**second's constraint** is the **latency's budget**: the queue's depth's
wait's time (the limit's worth of callers waiting × the call's time) must
stay inside the request's deadline — so the limit's related to the call's
latency's distribution. The **third's constraint** is the **dependency's
capacity** — but that's a *ceiling*, not the target, because you're designing
for the day it's degraded. And the **fourth's constraint** is the **fairness**:
if the limit is global and one tenant dominates, the other tenants starve — so
either the per-tenant's bulkhead or a rate's limiter in front. The
**empirical's tuning**: start with the formula, then watch the **bulkhead's
rejection's count** in the metrics — if it's non-zero in normal's operation,
the limit's too low (or the dependency's too slow); if it's always zero, the
limit's too high (you're not actually isolating). And the **chaos's test**:
kill the dependency and verify that the API's latency and throughput stay
within the budget — that's the only's way to know the limit's right.

**F4** (after the fallback) → *"How do you test the fallback?"**
> The fallback's testing has three's layers. (1) **The unit's test** — force
the dependency's failure (a mocked's client that throws) and assert the
fallback's value is returned. Simple, but it only tests the happy's failure's
path. (2) **The integration's test** with a **WireMock** (or a fault's
injecting's proxy) that returns 500s or a slow's response — and the
**assertions** must include the **dependency's call's count** (to prove the
breaker is protecting) and the **response's shape** (the degraded's value, not
a null). (3) **The chaos's test** — kill the actual's dependency in a
staging's environment and assert the **end-to-end's behaviour**: the API's
still returns a valid's response (the degraded's one), the latency's within
the budget, and the error's rate is ~0. This is the only's test that validates
the *ladder's* design (the fallback is actually reachable, the user's
experience is acceptable). And the **assertion's discipline**: the tests
should assert the **user-visible's contract** (a 200 with a degraded's field,
not a 500), not the implementation's details. The **common's gap**: people
test the fallback's *method* in isolation and never test that the *chain*
(bulkhead → timeout → breaker → retry → fallback) produces the right's
behaviour under a *real's* fault — the ordering's bugs only show up at the
chain's level.

---

## 16. Real 3–5 Year Scenarios

**R1. *"We had an outage last month where our whole API went down for 8 minutes
because the search service (a third-party Elasticsearch) was slow. We have a
circuit breaker on it now, but the post-mortem says the root cause was that our
other endpoints were also affected. Why, and what should we have had?"**
>
> **Ideal answer:** (1) **The framing:** the post-mortem's finding is the
**classic's signature** of a missing (or mis-configured) **bulkhead**, and the
fact that the *other* endpoints were affected is the key's evidence. If the
search's slowness had been contained, the other endpoints wouldn't have
degraded. The mechanism: the search's calls hung for the timeout's duration,
holding the request's threads; the **shared's thread's pool** (the Tomcat's
200's threads) was consumed by the search's calls; and with the threads gone,
**every** endpoint (the products' list, the cart, the checkout) had to queue
for a thread → the whole API degraded. **The circuit's breaker doesn't prevent
this** if it's mis-configured or absent: the breaker trips *after* the threshold,
and by then the threads are already gone; and if the breaker's threshold was
based on the **failure's rate** only (not the **slow's call's rate**), a
dependency that was slow-but-succeeding would **never trip it** at all. (2) **The
diagnosis of what we had:** the search's calls were **hanging** (not failing)
— the third-party's Elasticsearch was accepting the connections and not
responding, so the calls took the **full's timeout** each. Three's likely
gaps: (a) **No timeout** (or a 30s's one) — the calls held the threads for
30s each. (b) **No bulkhead** — the search's calls could take all 200's
threads. (c) **A breaker configured on the failure's rate only** — the slow's
calls aren't failures, so the breaker never's tripped. (d) **No fallback** —
the search's failure became a 500 on the search's endpoints. And (e) possibly
**the connection's pool's exhaustion** — the HikariCP's pool for the ES's
client (if it used one) was exhausted, and its `connectionTimeout` (30s's
default) meant the requests waited 30s for a connection.
(3) **What we should have had — the complete's stack, in the order:**
- **A tight's timeout** (500ms-1s for the search — an interactive's search
shouldn't take more than that; the third-party's own's SLA is what it is).
- **A bulkhead** (20-30's concurrent's calls) — the containment's mechanism.
The size's derivation: the threads you're willing to give the search ÷ the
timeout. With a 1s's timeout and 30's threads, the search's calls would
consume at most 30's threads and 30's RPS, and the other 170's threads would
keep serving.
- **A breaker with the slow-call's detection** (`slowCallDurationThreshold` =
800ms, `slowCallRateThreshold` = 80%) — so the slowness itself trips it.
- **A fallback** — a **degraded's search**: the search's results are
**non-critical**; the product's list should render with the search's section
empty (or with a "search's temporarily unavailable" message) while the
**rest** of the page works. The correct's design: the **search's endpoint is
separate** from the **page's endpoint**, so a search's failure doesn't take out
the page's render. That separation is the **architecture's** fix.
- **A retry with a jitter** and a short's budget, for the brief's blips.
- **The connection's pool's timeout** set to ~2s, not the 30s's default.
(4) **The measurement's plan (and the SLO's):** the search's call's latency's
distribution (p50/p95/p99) as a **separate's metric** from the rest of the
API's; the **thread's pool's utilisation** and the **queue's depth** (the
Tomcat's `currentThreadsBusy` vs `maxThreads`) — the **leading's indicator**
of this's failure; the **breaker's slow-call's rate**; the **timeout's rate**;
and the **error's budget's burn's rate**. The **alert's design**: the
"**the thread's pool's saturation**" alert (busy/max > 80% for 2 minutes) is
the one that would have caught this **in the first 30 seconds** — it's a
**leading's** signal, whereas the error's rate is a **lagging's** one. That's
the key's lesson: **alert on the leading's indicators** (the thread's
saturation, the slow-call's rate, the bulkhead's rejections), not just the
lagging's ones (the error's rate). (5) **The remediation's plan:**
- **The immediate's**: add the timeout, the bulkhead, and the fallback (a
week's work, and it removes 80% of the risk).
- **The short's term**: the breaker's slow-call's configuration, the
separate's search's endpoint, and the leading's indicator's alerts.
- **The medium's term**: the **circuit's isolation's review** across all the
dependencies — for each one: does it have a timeout, a bulkhead, a breaker
with the slow-call's detection, a fallback, and a *separate's* endpoint from
the critical's path? The answer for a third-party's dependency is *always*
yes to all five, because you don't control their's behaviour.
- **The long's term**: the **dependency's audit** — identify all the
third-party's calls (the search, the payments, the SMS, the address's
validation, the tax's calculation) and give each one the full's stack. The
post-mortem's lesson is usually "we had the pattern on some's dependencies
and not on others" — and the fix is a **standard's template** in the platform's
starter, so the pattern is the **default**, not a per-dependency's decision.
(6) The senior's framing: "the post-mortem's finding — 'the other endpoints
were affected' — is the whole's diagnosis in one sentence, and it points at
the **bulkhead's absence**, not the breaker's. The mechanism is
**Little's Law**: with 200's shared's threads and a 30s's timeout, a
third-party's service that accepts the connections and doesn't respond
consumes the entire's thread's pool, and then *every* endpoint is queuing for
a thread. The breaker's absence is the second's finding: a breaker configured
on the **failure's rate** alone will **never trip** on a dependency that's
slow-but-succeeding, which is exactly what a hung's third-party's search is.
And the **timeout's absence** (or its 30s's default) is what converted a
slow's dependency into a total's outage. So the **complete's answer** is the
five's layers, and the **order of the review** matters: the **timeout** and
the **bulkhead** first (they're the containment's mechanisms and they're a
day's work), then the **breaker's slow-call's detection**, then the
**fallback** — but the **fallback's** design's decision ("is the search's
result's non-critical?") is actually the first's *design's* question, because
if the search's results are non-critical, the search's endpoint should be
**separate** from the page's endpoint, and that one change would have made
the 8 minutes a **degradation** instead of an outage. And the **alerting's**
lesson is the one I'd make sure lands: we would have caught this with an
alert on the **thread's pool's saturation** (`currentThreadsBusy/maxThreads`
> 80%) — a **leading's** indicator, 30 seconds into the event, instead of the
error's rate, which is a **lagging's** one. That's the difference between a
detection's time of 30 seconds and 5 minutes. And the **organisational's**
fix is the **template**: every's third-party's dependency gets the pattern by
default — the timeout, the bulkhead, the breaker with the slow-call's
detection, and the fallback's contract — because the evidence from every's
incident is that the pattern gets applied to the dependencies someone
remembered and skipped on the ones they didn't."

---

## 17. Frequently Asked Production Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| The whole's API slows when one dependency's slow | No bulkhead, the shared's thread's pool | The bulkhead, the tight's timeout |
| The breaker never trips during an outage | The failure's rate's threshold is too high, or the failures aren't classified | The threshold, the `recordExceptions` |
| The breaker trips during a normal's deployment | The threshold's below the normal's variance | Raise the threshold, the non-disruptive's deploy, the breaker's suspension |
| The breaker trips on a 404's storm | The 4xx's are counted as failures | The `ignoreExceptions` |
| The retries make the outage's worse | The retry's without a jitter or budget | The jitter, the budget, the breaker outside |
| The timeout's rate is high (>1%) | The timeout's value is too low, or the dependency's degraded | Measure the p99, re-tune |
| The p99 is 30× the p50 | The thread's pool's saturation, the queue's wait | The bulkhead, the timeout's budget |
| The requests are slow but the error's rate is 0 | The queue's wait's time (the threads are busy) | The bulkhead, the scale-out, the fast's rejection |
| The connection's pool's exhausted | The slow's queries, the long's transactions | The `statement_timeout`, the pool's sizing, the separate's pool |
| The payment's charged twice | The write's timeout's retry without an idempotency's key | The key, the reconciliation's job |
| The fallback's returns null → the NPE | The fallback's not defined for the exception's type | The fallback's return a meaningful's value |
| The breaker's state shows OPEN but the dependency's fine | The probe's landing on a sick's instance | More's probes, the partial's outage's check |
| The service's restarts during a dependency's outage | The breaker's health's wired to the readiness's probe | The metric + the alert, not the probe |
| The thread's count grows without's bound | The blocking's call in the reactive's path | The reactive's APIs, the timeouts |

---

## 18. Debugging Scenarios

**D1. "Is the thread's pool the bottleneck?"**
```bash
# the Tomcat's metrics
curl -s http://localhost:8080/actuator/metrics/tomcat.threads.busy | jq .measurements[0].value
curl -s http://localhost:8080/actuator/metrics/tomcat.threads.config.max | jq .measurements[0].value
# the ratio > 0.8 → the pool's saturated

# the thread's dump — what are they WAITING on?
jcmd 1 Thread.print | grep -A 5 "http-nio-8080-exec" | head -40
# ⚠ "SocketInputStream.socketRead" → the call's hanging (the timeout's missing)
# ⚠ "HikariPool.getConnection" → the pool's exhausted
```

**D2. "Is the breaker's protecting us? (the metrics):"**
```promql
# the breaker's state
resilience_circuitbreaker_state{name="catalogue"}
# the failure's rate — the early's warning
resilience_circuitbreaker_failure_rate{name="catalogue"}
# the slow's call's rate
resilience_circuitbreaker_slow_call_rate{name="catalogue"}
# the retry's count — the flaking's signal
rate(resilience4j_retry_calls_seconds_count[5m])
```

**D3. "Which dependency is consuming the threads?"**
```bash
# the per-dependency's call's count
curl -s http://localhost:8080/actuator/metrics/http.client.requests | jq
# ⚠ the by-tag's breakdown: the URI's tag
```

**D4. "Find the calls that are exceeding the timeout's budget:"**
```promql
# the timeout's error's count, by the dependency
sum by (uri) (rate(http_client_requests_seconds_count{status="504"}[5m]))
# the p99 per's dependency
histogram_quantile(0.99, sum(rate(http_client_requests_seconds_bucket[5m])) by (le, uri))
```

**D5. "The latency's mystery — the trace's breakdown:"**
```bash
# the OTel's span's durations (the Tempo/Jaeger)
curl -s "http://localhost:16686/api/traces/$TRACE_ID" | \
  jq '.[] | {name, ms: (.duration/1000)}' | sort_by(-.ms) | head -10
# ⚠ the largest's span is the bottleneck's dependency
```

---

## 19. Architecture Design Questions

**Q1. 🏗️ Design the resilience's strategy for a service with 8 dependencies.**
> (1) The **dependency's inventory** (the critical's path's vs the optional's,
the third-party's vs the internal's, the read's vs the write's); (2) The
**timeout's budget's allocation** (the total's SLO split across the hops,
the derived's timeouts); (3) The **per-dependency's policy** (the timeout, the
retry's class, the breaker's thresholds, the bulkhead's size); (4) The
**fallback's ladder** per dependency (what's degraded, and the business's
state); (5) The **isolation's boundaries** (the critical's dependencies get a
big's share; the optional's get a small's one); (6) The **observability's**
(the leading's indicators, the dashboards, the alerts); (7) The **chaos's
test's plan**; (8) The **standard's template** (the platform's starter applies
the pattern by default).

**Q2. 🏗️ Design a checkout's service that must survive a payment gateway's
outage.**
> (1) The **asynchronous's payment's model** (the `202` + the webhook + the
`PENDING` state) — the gateway's latency is out of the user's path; (2) The
**idempotency's key** (the client-generated's, per the attempt) and the
gateway's semantics; (3) The **reconciliation's job** (the webhook's loss, the
`PENDING`'s ageing, the gateway's status's API); (4) The **timeout's budget**
(2s) and the **breaker** with the slow-call's detection; (5) The **bulkhead**;
(6) The **user's experience** (the order's placed, the payment's confirming,
the notification); (7) The **observability's** (the pending's count and age,
the double-charge's detection, the gateway's latency's percentiles); (8) The
**degradation's** (the order's placed, the payment's later — the business
absorbs the failure).

**Q3. 🏗️ Design a graceful's degradation's strategy for a product's catalogue
page.**
> (1) The **page's decomposition** (the critical's sections vs the optional's
— the price, the stock, the reviews, the recommendations); (2) The **endpoint's
separation** (the page's single's endpoint vs the sections' individual's
endpoints — the latter's lets the client render partially); (3) The **cache's
layers** (the fresh's, the stale's, the fallback's default) and their TTLs;
(4) The **staleness's semantics** per field (the price's → the `PENDING`
state; the description's → silent's staleness); (5) The **timeout's budget's
allocation** across the sections (the total's page's budget, the
proportional's split, the parallel's fetch with a per-section's timeout); (6)
The **client's rendering** (the skeleton's, the partial's, the error's
boundaries); (7) The **observability's** (the degraded's render's rate, the
per-section's fallback's count).

---

## 20. Cheat Sheet — One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  the ORDER:  TIMEOUT → RETRY → BREAKER → BULKHEAD → RATE's LIMIT → FALLBACK │
│  ⚠ each layer assumes the one below's bounded                             │
├──────────────────────────────────────────────────────────────────────────────┤
│  the TIMEOUT: ALWAYS set it. the default's INFINITE.                       │
│  the connect's timeout + the read's timeout + the whole's response's       │
│  the BUDGET's derivation: the timeout < the caller's remaining's deadline  │
│  ⚠ the timeout's value = your's capacity's knob: throughput = threads/T    │
├──────────────────────────────────────────────────────────────────────────────┤
│  the RETRY: the idempotent's only · the transient's only (not the 4xx)     │
│  the exponential's backoff + the JITTER (no jitter = the herd)             │
│  the bounded's BUDGET: ≤10% of the request's deadline                      │
│  ⚠ a write's timeout's retry REQUIRES the idempotency's key                │
├──────────────────────────────────────────────────────────────────────────────┤
│  the BREAKER: CLOSED → OPEN → HALF_OPEN                                    │
│  the window: 20's calls · the min's calls: 10 · the threshold: 50%          │
│  the slow's calls: the threshold's duration + the rate's threshold         │
│  the wait: 30s in OPEN · the probes: 3 in HALF_OPEN                       │
│  ⚠ the 4xx's MUST be in the ignoreExceptions                              │
├──────────────────────────────────────────────────────────────────────────────┤
│  the BULKHEAD: the semaphore (the short's wait!) or the thread's pool      │
│  the size: the threads you're willing to give ÷ the worst's timeout        │
│  ⚠ the maxWaitDuration's LONG = the caller's thread's still's blocked     │
├──────────────────────────────────────────────────────────────────────────────┤
│  the FALLBACK's LADDER: fresh → stale's cache → default → partial →        │
│  the degraded's business's state → async's deferment → the error          │
│  ⚠ the BEST's fallback is a DOMAIN's concept ("the price's pending")      │
├──────────────────────────────────────────────────────────────────────────────┤
│  the ALERT's on the LEADING's indicators: the thread's pool's saturation,   │
│  the slow-call's rate, the bulkhead's rejections, the timeout's rate       │
│  (not just the error's rate — that's the LAGGING's one)                    │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 21. Follow-up Learning
- → [05 · Distributed Tracing & Observability](./05-Distributed-Tracing-and-Observability.md)
- → [02 · Service Communication & Contracts](./02-Service-Communication-and-API-Contracts.md)
- → [09 · Messaging Systems](../09-Messaging-Systems/README.md)
- → [11 · Database Design & Optimisation](../11-Database-Design-Optimization/README.md)

---
**Next Chapter:** → [05 · Distributed Tracing & Observability](./05-Distributed-Tracing-and-Observability.md)
**Previous Chapter:** ← [03 · Service Discovery, Config & API Gateway](./03-Service-Discovery-Config-and-API-Gateway.md)
