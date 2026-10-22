# 05 · Distributed Tracing & Observability

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★☆ ("How do you debug a request that spans four services?")
> **Prerequisite:** [04 · Circuit Breaker & Resilience](./04-Circuit-Breaker-and-Resilience.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Three Pillars](#2-mental-model--the-three-pillars)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The three pillars](#31-the-three-pillars)
  - [3.2 The trace anatomy](#32-the-trace-anatomy)
  - [3.3 Context propagation](#33-context-propagation)
  - [3.4 The span kinds](#34-the-span-kinds)
  - [3.5 The metrics model](#35-the-metrics-model)
  - [3.6 The four golden signals](#36-the-four-golden-signals)
  - [3.7 The RED method](#37-the-red-method)
  - [3.8 The logging model](#38-the-logging-model)
  - [3.9 SLOs and the error budget](#39-slos-and-the-error-budget)
  - [3.10 The cardinality problem](#310-the-cardinality-problem)
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

- **Observability is not monitoring.** Monitoring answers the questions you **knew** to ask and
  instrumented for. Observability lets you **investigate** an unknown problem using the system's
  own telemetry. You monitor with **metrics**, you observe with **traces**, you explain with
  **logs**.
- **The three pillars:** **Metrics** (aggregate numbers — cheap, always on, low cardinality, for
  trends and alerts), **Traces** (the path of one request across services — expensive, sampled,
  for "where is the time going?"), **Logs** (discrete events — richest, noisiest, for "what
  exactly happened?").
- **A trace is a tree of spans.** The **trace id** identifies the whole request, the **span id**
  identifies one operation, and the **parent id** builds the tree. **Context propagation** is the
  mechanism: the `traceparent` header (W3C Trace Context) carries the trace and span ids across
  service boundaries.
- **Cardinality is the killer.** Metric label values multiply. `userId` as a label is a metric
  explosion — 1M users means 1M time series and a Prometheus OOM. Put userId in **logs** and
  **span attributes**, never in a **metric label**.
- **The RED method** for request paths: **Rate**, **Errors**, **Duration** — three metrics per
  endpoint. **The USE method** for resources: **Utilization**, **Saturation**, **Errors**.
- **SLOs and the error budget** are the contract. 99.9% availability means a 0.1% error budget
  over a rolling 30-day window. Budget consumption is the **release gate** — spend it on risky
  changes and stop when it's exhausted.
- **Rule of thumb:** metrics answer "is it healthy?" (dashboards, alerts), traces answer "where
  did the time go?" (latency investigation), logs answer "what happened to *this* request?"
  (correlation id).

---

## 1. What & Why

### The scenario

```text
  10:00  The checkout API's p99 latency jumps from 120ms to 4s.
  10:05  The on-call engineer gets paged.
  10:06  "Which endpoint?" → metrics show POST /orders.
  10:08  "Which service?" → CPU normal, memory normal, pods look fine.
  10:12  "Which downstream?" → logs show the payment call taking 3.9s.
  10:20  "Is it always slow or only some requests?" → ⚠ nobody can answer.
          The logs show the slow requests, but logs are sampled and rotated,
          and there is no correlation id tying the request's entry and exit.
  10:35  A restart "fixes" it. Nobody knows why.
  10:40  It happens again.

  THE COST: 35 minutes of outage duration, and a root cause that will be
  rediscovered next week.
```

**With the three pillars:**

```text
  10:00  The p99 latency alert fires (the metric).
  10:01  The dashboard shows the payment client span's p99 = 3.9s
         (a metric, tagged by dependency).
  10:02  Click the exemplar → the trace opens in Jaeger.
  10:03  The trace shows 3.9s in the payment HTTP call, 3.8s of which is
         waiting to read from the TCP socket. The payment service's own
         server span is 40ms.
  10:04  The payment service's dashboards show the connection pool is
         exhausted (HikariCP pending = 200).
  10:06  The slow query log: SELECT ... FROM orders WHERE ... (no index).
  10:08  FIXED.

  THE COST: 8 minutes, and the root cause is known.
```

### The framing

> **In a monolith, a stack trace is your debugger. In a distributed system you don't have one —
> you have to build it.** Observability is the discipline of making a system's internal state
> **inferable from the outside**, so that when something breaks that you didn't predict, you can
> still find out what happened.

---

## 2. Mental Model — The Three Pillars

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  THE THREE PILLARS OF OBSERVABILITY DATA                                     │
│                                                                              │
│   ┌────────────────┐  ┌────────────────┐  ┌────────────────┐                 │
│   │    METRICS     │  │    TRACES      │  │     LOGS       │                 │
│   ├────────────────┤  ├────────────────┤  ├────────────────┤                 │
│   │ AGGREGATE      │  │ PATH           │  │ DISCRETE       │                 │
│   │                │  │                │  │                │                 │
│   │ "how many?"    │  │ "where did the │  │ "what exactly  │                 │
│   │ "how fast?"    │  │  time go?"     │  │  happened?"    │                 │
│   │ "how often     │  │                │  │                │                 │
│   │  did it fail?" │  │  one request   │  │  one event     │                 │
│   │                │  │  across N      │  │  at a time     │                 │
│   │                │  │  services      │  │                │                 │
│   ├────────────────┤  ├────────────────┤  ├────────────────┤                 │
│   │ CARDINALITY:   │  │ CARDINALITY:   │  │ CARDINALITY:   │                 │
│   │ LOW ✅         │  │ HIGH (bounded  │  │ UNBOUNDED      │                 │
│   │ (bounded)      │  │  by spans)     │  │ (fine — it's a │                 │
│   │                │  │                │  │  log, not a    │                 │
│   │ always on      │  │ SAMPLED        │  │  time series)  │                 │
│   │ 15-60s scrape  │  │ 1-10% of       │  │                │                 │
│   │                │  │ requests       │  │ shipped to     │                 │
│   │                │  │                │  │ ELK/Loki       │                 │
│   ├────────────────┤  ├────────────────┤  ├────────────────┤                 │
│   │ ALERTING       │  │ DEBUGGING      │  │ FORENSICS      │                 │
│   │ DASHBOARDS     │  │ PROFILING      │  │                │                 │
│   │ CAPACITY       │  │                │  │                │                 │
│   └────────────────┘  └────────────────┘  └────────────────┘                 │
│                                                                              │
│   ⚠ THE ORDER OF INVESTIGATION:                                             │
│      METRIC says "WHAT is wrong" → TRACE says "WHERE" → LOG says "WHY"      │
└──────────────────────────────────────────────────────────────────────────────┘
```

**The key insight (the one that separates strong from weak candidates):**

> **Each pillar costs differently, so each answers a different question.** Metrics are cheap and
> always on, so they're the tool for "**is it healthy**" (dashboards, alerts). Traces are
> expensive — storage, CPU, cardinality — so they're sampled, and they're the tool for "**where
> did the time go**". Logs are richest and noisiest, so they're the tool for "**what exactly
> happened to this one request**". Answering a trace question with logs is the classic mistake —
> you end up grepping logs and **correlating timestamps by eye**, which is exactly what the
> trace id exists to make unnecessary.

---

## 3. Deep Dive — Theory

### 3.1 The three pillars

**Which tool for which question:**

| | **Metrics** | **Traces** | **Logs** |
|---|---|---|---|
| **Unit** | A number over a time window | A timed operation | A discrete event |
| **Cost** | Low (~1–10 bytes/sample) | High (~1–5 KB/span) | Medium-high (text volume) |
| **Sampling** | Never (all counters) | Usually (1–10%) | Never (or by log level) |
| **Cardinality** | Bounded (limited label values) | Per-span (attributes) | Unbounded (content) |
| **Query language** | PromQL / SQL | The trace UI | Log query (Lucene / LogQL) |
| **Answers** | "Is it healthy? What's the trend?" | "Which service spent the time?" | "What exactly went wrong?" |
| **Retention** | 30 days–1 year | 7–30 days (the expensive one) | 7–30 days (the volume one) |
| **Tooling** | Prometheus + Grafana | Jaeger / Tempo / Zipkin | ELK / Loki / Splunk |
| **Alerting** | ✅ the primary tool | ⚠ rarely — traces aren't complete enough | ⚠ noisy |

### 3.2 The trace anatomy

```text
  a TRACE = one request's journey. A SPAN = one operation within it.

  TRACE (trace id: 4bf92f3577b34da6a3ce929d0e0e4736)
  │
  ├── SPAN #1  "POST /orders"  (server span)                [40ms]
  │     span id: a2fb4a1d1a96d312      parent id: (none — root)
  │     attributes: http.method=POST, http.route=/orders, http.status_code=201
  │     │
  │     ├── SPAN #2  "JPA findById"  [12ms]
  │     │     attributes: db.system=postgresql, db.statement=select * from orders
  │     │
  │     ├── SPAN #3  "HTTP POST payment-gateway"  [3900ms]  ⚠ the bottleneck
  │     │     attributes: http.url=https://pay.example.com/v1/charge
  │     │     events: {"exception.type": "SocketTimeoutException"}
  │     │
  │     ├── SPAN #4  "Kafka publish order.created"  [3ms]
  │     │
  │     └── SPAN #5  "HTTP POST inventory-service"  [45ms]
  │
  └── (the other service's spans, joined by the parent id)
        ├── SPAN #6  "POST /charges" (payment service)  [38ms]
        │     └── SPAN #7  "JDBC insert payment"  [22ms]
        └── SPAN #8  "POST /reservations" (inventory service)  [40ms]

  THE KEY RELATIONSHIPS:
    trace id  → the whole journey (same across all services)
    span id   → this operation
    parent id → who called me
    span links → causal relationship that is not nesting
```

**Span attributes (the OTel semantic conventions):**

| Category | Examples | Caution |
|----------|----------|---------|
| **HTTP** | `http.request.method`, `http.response.status_code`, `url.path`, `server.address` | ⚠ `http.url` with a query string can leak PII and blow cardinality |
| **Database** | `db.system.name`, `db.namespace`, `db.operation.name` | ⚠ NEVER parameter values (`?` placeholders only) — the values are PII and a cardinality bomb |
| **Messaging** | `messaging.system`, `messaging.destination.name`, `messaging.operation` | Correlate by message id, never the payload |
| **RPC** | `rpc.system`, `rpc.service`, `rpc.method` | — |
| **User** | `enduser.id` (the **hashed** one) | ⚠ a raw userId is PII — hash it or use an internal id |
| **Custom** | `order.id`, `payment.amount` | Useful, but must stay bounded |

**The span kinds:**

```text
  SERVER       the server side of an RPC — the inbound request
  CLIENT       the client side of an RPC — the outbound request
  PRODUCER     a messaging producer
  CONSUMER     a messaging consumer
  INTERNAL     in-process work (repository call, computation)
```

### 3.3 Context propagation

**The W3C Trace Context header (the current standard):**

```http
  traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
                │  │                                │        │
                │  │                                │        └─ flags
                │  │                                │           (01 = sampled)
                │  │                                └────────── parent span id
                │  └─────────────────────────────────────────── trace id
                └────────────────────────────────────────────── version (00)
```

**Propagation mechanisms:**

| Mechanism | How | Note |
|-----------|-----|------|
| **HTTP headers** | `traceparent` + `tracestate` | The standard way; injected by the agent or library |
| **gRPC metadata** | `grpc-trace-bin` (binary) | gRPC clients also propagate deadlines |
| **Kafka headers** | `traceparent` in message headers | ⚠ not the payload — keep the schema clean |
| **RabbitMQ headers** | AMQP headers | — |
| **Scheduler context** | `TaskDecorator` on the executor | ⚠ the critical one — see below |
| **Reactor context** | Automatic in WebFlux | — |
| **`@Async` context** | `TaskDecorator` | ⚠ the default executor LOSES the context |

**The #1 gotcha: losing context across async boundaries.**

```text
  ⚠ an ASYNC boundary BREAKS the trace:

    @PostMapping("/orders")
    public Order create(...) {
        // CONTEXT is here (the server span is current)
        asyncService.processOrder(order);   // ⚠ the NEW THREAD has NO context
        return order;
    }

    ⚠ consequence: the async spans become ROOT spans of a SEPARATE trace
      → they don't appear in the original trace view, and "show me the whole
      journey" is broken.

  ✅ THE FIX (Spring's TaskDecorator):

    // Spring Framework 6.1 / Boot 3.2+ — auto-configured for the default executor
    @Bean
    TaskDecorator traceContextPropagatingDecorator() {
        // org.springframework.core.task.support.ContextPropagatingTaskDecorator
        return new ContextPropagatingTaskDecorator();
    }

    // Or, directly with the OpenTelemetry API:
    @Bean
    TaskDecorator otelContextPropagatingDecorator() {
        return task -> Context.current().wrap(task);   // io.opentelemetry.context.Context
    }
```

**The precise `@Async` pitfall:** Spring's `ThreadPoolTaskExecutor` does **not** propagate
thread-local context by default; `TaskDecorator` is the hook. In modern code, Micrometer's
`ObservationRegistry` plus `ObservationThreadLocalAccessor` does the same job, and Spring Boot
3.2+ auto-configures that accessor so thread-local context survives executor boundaries.

### 3.4 The span kinds

```text
  The SERVER span and the CLIENT span are DIFFERENT spans linked by a parent
  relationship — NOT the same span reused.

  CLIENT (orders)                        SERVER (payment)
  ┌──────────────────────────┐          ┌──────────────────────────┐
  │ SPAN: HTTP POST /charges │          │ SPAN: POST /charges      │
  │   parent = the order's   │─────────▶│   parent = the client's  │
  │   server span            │  same    │   span id                │
  │                          │  trace   │                          │
  │   duration = 3900ms      │  id      │   duration = 38ms        │
  │   (the round trip)       │          │   (the server's work)    │
  │                          │          │                          │
  │ ⚠ CLIENT span time = SERVER time + NETWORK time                  │
  │   (queueing, network, serialisation)                             │
  │   → the GAP between them = infrastructure overhead               │
  └──────────────────────────┘          └──────────────────────────┘

  ⚠ THIS is why the trace shows where the 3.9s went: the server span is
    only 38ms, so 3.86s was network/queueing/serialisation — a networking
    or client-pool problem, not an application one. That distinction is
    the single most valuable thing a trace gives you.
```

### 3.5 The metrics model

**The four metric types:**

| Type | Example | Use |
|------|---------|-----|
| **Counter** | `http.server.requests` (count) | Rate calculation (`rate()`) |
| **Gauge** | `jvm.memory.used`, `hikaricp.connections.active` | Instantaneous value |
| **Timer** | `http.server.requests` (duration) | Rate + histogram percentiles |
| **Distribution** | The same timer, with SLO buckets | SLO burn rate |

**Micrometer naming and its Prometheus translation:**

```text
  MICROMETER name:  http.server.requests
  PROMETHEUS name:  http_server_requests_total  (the _total suffix is added)

  MICROMETER tag:   uri="/orders/{id}"
  PROMETHEUS label: uri="/orders/{id}"

  ⚠ the templated URI ("/orders/{id}", NOT "/orders/123") is ESSENTIAL —
    it is what keeps cardinality bounded.
```

**Spring Boot's auto-configured metrics:**

```text
  http.server.requests        HTTP server metrics (the RED signals)
  jvm.memory.*                heap, non-heap, GC count and pause
  jvm.gc.*                    GC pause time
  process.cpu.usage           CPU
  hikaricp.*                  connection pool (active, idle, pending,
                              max, acquire time)
  tomcat.sessions.*           active sessions
  logback.events              log count by level
  spring.data.jpa.*           Hibernate sessions, transactions
  cache.*                     cache hits and misses (Caffeine)
  resilience4j.*              breaker, retry, bulkhead metrics
```

**Custom metrics with the Micrometer API:**

```java
  // COUNTER — the rate
  Counter ordersCreated = Counter.builder("orders.created")
          .description("Number of orders created")
          .tag("channel", "web")
          .register(registry);
  ordersCreated.increment();

  // TIMER — the duration distribution
  Timer checkoutTimer = Timer.builder("checkout.duration")
          .description("End-to-end checkout duration")
          .publishPercentiles(0.5, 0.95, 0.99)     // ⚠ client-side percentiles
          .register(registry);
  checkoutTimer.record(Duration.ofMillis(120));

  // GAUGE — the instantaneous value
  Gauge.builder("orders.pending", pendingOrders, AtomicInteger::get)
          .register(registry);

  // TIMER with buckets (SLO-friendly)
  Timer.builder("checkout.duration")
          .publishPercentileHistogram()          // ⚠ the Prometheus histogram
          .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(500))
          .register(registry);
```

**⚠ Client-side percentiles vs histogram (the classic interview trade-off):**

```text
  CLIENT-SIDE PERCENTILES (Micrometer's publishPercentiles):
    ✅ the exact value for p50/p95/p99
    ❌ NOT aggregatable across instances (each pod computes its own)
    ❌ buckets are fixed at registration time
    ❌ high cardinality cost (quantile series per pod)

  THE HISTOGRAM (publishPercentileHistogram):
    ✅ aggregatable across instances (sum the buckets)
    ✅ enables SLO burn-rate calculation
    ❌ approximate percentiles (bucket boundaries)
    ❌ more series (buckets × labels)

  ✅ THE ANSWER: use the histogram for SLOs and dashboard percentiles; use
    client-side percentiles only when you need an exact value on a single
    instance.
```

### 3.6 The four golden signals

```text
  THE FOUR GOLDEN SIGNALS (Google SRE book) — the minimum set:

  ① LATENCY    — "how long does it take?" (for successful AND failed
                  responses — error latency is often the longest)
  ② TRAFFIC    — "how much load?"
  ③ ERRORS     — "how often does it fail?"
  ④ SATURATION — "how full is the resource, how close to its limit?"

  ⚠ THE #1 MISTAKE: measuring latency only on successful responses.
    A 500 often takes LONGER (timeout path, stack trace generation) →
    "p99 is 50ms" while users report hanging requests is a classic blind
    spot.
```

**The four signals mapped to Spring actuator metrics:**

| Signal | Metric |
|--------|--------|
| Latency | `http_server_requests_seconds` (`_max`, `_sum` → average) |
| Traffic | `http_server_requests_seconds_count` → `rate()` |
| Errors | `http_server_requests_seconds_count{status=~"5.."}` → `rate()` |
| Saturation | `hikaricp_connections_pending`, `jvm_memory_used_bytes{area="heap"}`, `tomcat_threads_busy` |

### 3.7 The RED method

```text
  THE RED METHOD — for REQUEST PATHS (the service tier):
    RATE      — how many requests/second?
    ERRORS    — how many failures/second?
    DURATION  — how long do they take? (percentiles)

  THE USE METHOD — for the RESOURCE tier:
    UTILIZATION — how busy is the resource? (CPU, memory)
    SATURATION  — what is queued or waiting? (pool pending, queue depth,
                  disk IO wait)
    ERRORS      — the failures

  ⚠ THE CRITICAL DISTINCTION: UTILIZATION vs SATURATION.
    CPU utilization at 90% may be FINE (it's the throughput limit).
    SATURATION at 90% (requests queueing) is the PROBLEM — that's where
    latency comes from.
    ⚠ Saturation metrics are the EARLIEST signal of impending overload —
    they LEAD, while latency and errors LAG.
```

**RED with Micrometer common tags:**

```java
  @Bean
  MeterFilter commonTags() {
      return MeterFilter.commonTags(Tags.of(
          "application", "orders-service",
          "environment", "prod"
      ));
  }
```

### 3.8 The logging model

**The log levels and what they mean operationally:**

| Level | Purpose | Volume | Example |
|-------|---------|--------|---------|
| `ERROR` | The request failed and a human is needed | Low, high value | Unhandled exception, failed business operation |
| `WARN` | Something unexpected but handled | Medium | A retry happened, a fallback was used, a deprecation |
| `INFO` | Significant business events | Medium | Order created, user logged in |
| `DEBUG` | Diagnostic detail | High | The SQL statement, the branch taken |
| `TRACE` | Everything | Very high | ⚠ never a production default |

**Structured logging (non-negotiable in a distributed system):**

```java
  // ❌ string concatenation — unsearchable, unparseable
  log.info("Order created for user " + userId + " with amount " + amount);

  // ✅ structured logging (key-value pairs)
  log.info("Order created: orderId={} userId={} amount={}", orderId, userId, amount);
  // or MDC (mapped diagnostic context) for request-scoped fields
  MDC.put("orderId", orderId);
```

**The MDC — request-scoped context:**

```text
  The MDC is a thread-local map of key-values that the logging pattern can
  include in EVERY log line.

  A filter populates it at request entry:
    · trace id   (the correlation id)
    · span id
    · user id
    · request id

  → every log line for that request carries the trace id → you can grep the
    logs by trace id and get the whole story ✅
```

```java
  @Component
  public class MdcFilter extends OncePerRequestFilter {

      @Override
      protected void doFilterInternal(HttpServletRequest request,
                                      HttpServletResponse response,
                                      FilterChain chain) throws IOException, ServletException {
          try {
              Span span = Span.current();
              String traceId = span.getSpanContext().isValid()
                      ? span.getSpanContext().getTraceId()
                      : "no-trace";
              MDC.put("traceId", traceId);
              MDC.put("requestId", UUID.randomUUID().toString());
              MDC.put("method", request.getMethod());
              MDC.put("path", request.getRequestURI());
              chain.doFilter(request, response);
          } finally {
              MDC.clear();     // ⚠ MUST clear — pool threads are reused!
          }
      }
  }
```

```xml
  <!-- the LOGBACK pattern — include the MDC values -->
  <pattern>
    %d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [traceId=%X{traceId}] %msg%n
  </pattern>
```

**⚠ The MDC leak (the classic bug):** Tomcat's thread pool is **reused**. If you
`MDC.put(...)` and never `MDC.clear()`, the next request on the same thread inherits the
**previous request's** trace id → the logs lie. The `finally { MDC.clear(); }` is mandatory.

### 3.9 SLOs and the error budget

**The definitions:**

```text
  SLI (Service Level Indicator) — the measured quantity
      e.g. "the ratio of successful requests"
  SLO (Service Level Objective) — the target value
      e.g. "99.9% of requests succeed over 30 days"
  SLA (Service Level Agreement) — the contractual commitment
      e.g. "99.5% or we owe you money" (an SLO made contractual)

  ERROR BUDGET = (1 - SLO) × window
    for a 99.9% SLO over 30 days:
      0.1% × 30 days = 43.2 minutes of allowed failure per 30 days
    for a 99.99% SLO over 30 days:
      0.01% × 30 days = 4.32 minutes per 30 days
```

**The burn rate (the core SRE mechanic):**

```text
  BURN RATE = observed error rate ÷ allowed error rate

    a 99.9% SLO → allowed rate = 0.1%
    if the actual error rate is 1% → burn rate = 1% / 0.1% = 10×

  what the burn rate means:
    10×  → consuming the 30-day budget in 3 days
    100× → in 7.2 hours
    1000× → in 43 minutes

  ⚠ ALERTING THRESHOLDS (multi-window, multi-burn-rate):
    a fast window (5 min) plus a slow window (1 hour), both burning at
    >14.4× → the fast page
    both >6× (slow) and >3× (fast) → a slow ticket
    the slow window is what prevents paging on a transient blip
```

**The SLO as a release gate (error-budget policy):**

```text
  THE POLICY:
    · budget remaining > 75%  → risky changes are OK
    · budget remaining 25-75% → risky changes need a canary / extra review
    · budget remaining < 25%  → risky changes are FROZEN (reliability work
                                takes priority)
    · budget exhausted        → feature work stops entirely

  ⚠ THIS is the key insight: an SLO without a budget policy is just a number
  on a dashboard. The POLICY is what changes behaviour.
```

**The SLO vs SLI trap ("the dashboard is a lie"):**

```text
  ❌ WRONG SLI: "99.9% of requests returned 2xx"
     ⚠ 429s (rate limited) and 4xx (client fault) count as "not 2xx" but
       are NOT service failures.
     ⚠ a request that timed out at the client but succeeded at the server
       counts as a failure.
     ⚠ → the SLI is noisy and the availability number is wrong.

  ✅ RIGHT SLI: "the ratio of GOOD events to VALID events"
     where:
       GOOD events  = requests the service successfully handled
       VALID events = requests the service was ASKED to handle
                      (excluding 4xx client faults and 429 load shedding)
     and GOOD events are measured at the SERVER (the truth), not the client.
```

### 3.10 The cardinality problem

```text
  THE CARDINALITY EXPLOSION FORMULA:

    number of time series = Π (number of values per label)

    example:
      metric: http_server_requests_seconds
      labels: uri (50) × method (5) × status (10) × instance (20 pods)
        = 50 × 5 × 10 × 20 = 50,000 time series
        ⚠ add a userId label (1M users):
        = 50 × 5 × 10 × 20 × 1,000,000 = 50,000,000,000 ← 💥

  MEMORY COST (Prometheus):
    each series ≈ 1-3 KB in memory (chunks + index)
    50,000 series × 2 KB ≈ 100 MB   ✅ OK
    50M series × 2 KB   ≈ 100 GB   ❌ OOM

  THE RULES:
    ① NEVER use a high-cardinality value as a label:
       userId, email, orderId, sessionId, requestId, raw URL with ids,
       exception message, stack trace
    ② DO put them in TRACE attributes and LOG fields ✅
    ③ the URI label must be the TEMPLATE ("/orders/{id}"), not the concrete
       path ("/orders/8f3a-...") ⚠ — Spring Actuator does this by default
       via the `http.route` tag
    ④ tag values must come from a FIXED set (a bounded enum)
    ⑤ CHECK: a cardinality budget per metric, alerted on growth rate
```

**The mitigations:**

```text
  · a label-value allow-list (Micrometer's MeterFilter.maximumAllowableTags)
    → if tag count exceeds the limit, extra values become "other" ✅
  · Prometheus's series/label cardinality analysis endpoints
  · recording rules → pre-aggregation (drop labels at query time instead
    of storage time)
  · Mimir/Cortex/Thanos → per-tenant cardinality limits
```

---

## 4. Visualizations

### 4.1 The three pillars and their questions

```text
┌────────────────────────┬────────────────────────┬────────────────────────┐
│       METRICS          │        TRACES          │         LOGS           │
├────────────────────────┼────────────────────────┼────────────────────────┤
│  THE QUESTION:         │  THE QUESTION:         │  THE QUESTION:         │
│  "is it healthy?"      │  "where is the time?"  │  "what happened?"      │
│                        │                        │                        │
│  ┌──────────────────┐  │  ┌──────────────────┐  │  ┌──────────────────┐  │
│  │  ▁▂▃▅▇▅▃▂▁ p99  │  │  │  ▁▂▃▅▇▅▃▂▁       │  │  │ ERROR order 42  │  │
│  │  ▁▁▁▁▁▁▁▁ 5xx   │  │  │  1.2ms  ▁▂▃       │  │  │ WARN retry pay  │  │
│  │  ▁▁▁▁▁▁▁▁ req/s │  │  │  3.9ms  ████████  │  │  │ INFO order made │  │
│  └──────────────────┘  │  └──────────────────┘  │  └──────────────────┘  │
│  STACK health          │  REQUEST path          │  EVENT detail          │
│  ALERT source          │  DEBUG tool            │  FORENSIC tool         │
│  EVERY 15s             │  SAMPLED 1-10%         │  EVERY event           │
└────────────────────────┴────────────────────────┴────────────────────────┘
```

### 4.2 The trace tree (the waterfall)

```text
  TRACE (4bf92f3577b34da6a3ce929d0e0e4736) — the checkout request
  elapsed scale: 0ms ─────────────────────────────────────────────▶ 4000ms

  orders-service                                                payment-service
  ┌────────────────────────────┐
  │ POST /orders           40ms│
  │└─ JPA findById        12ms │
  │  └─ HTTP POST /charges 3900ms ──────────────────────────────┐
  │  └─ Kafka publish      3ms │                                │
  │  └─ HTTP POST /inventory 45ms ───────────┐                  │
  └───────────────────────────────────────────┼──────────────────┼──────────┘
                                              │                  │
                                   ┌──────────┴──────┐  ┌────────┴────────┐
                                   │ POST /reserv.   │  │ POST /charges   │
                                   │           40ms  │  │           38ms  │
                                   │└─ JDBC ins 22ms │  │└─ JDBC ins 22ms │
                                   └─────────────────┘  └─────────────────┘

  ⚠ THE READING:
    CLIENT span (3900ms) vs SERVER span (38ms) = 3862ms of
    NETWORK/QUEUE/serialisation.
    → it is NOT the payment service's code. It's the client↔server path.
    → look at DNS, connection pool, LB queue, TLS.

  ⚠ clicking a span → EVENTS → the exception stack trace
```

### 4.3 Context propagation across boundaries

```text
   CLIENT                                  SERVER
   ┌─────────────────────┐                ┌─────────────────────┐
   │ server span         │                │ server span         │
   │  trace: abc123      │  traceparent:  │  trace: abc123 ✅    │
   │  span:  span1       │ ────────────▶ │  span:  span3       │
   │                     │abc123-span1-01 │  parent: span1 ✅    │
   │ └─ client span      │                │                     │
   │    trace: abc123 ✅ │  traceparent:  │                     │
   │    span:  span2     │ ────────────▶ │                     │
   │    parent: span1 ✅ │abc123-span2-01 │                     │
   └─────────────────────┘                └─────────────────────┘
        the SAME trace id ✅                 parent id = the client span ✅

   the ASYNC boundary (the ⚠ case):
   ┌─────────────────────┐        ┌─────────────────────┐
   │ the calling thread  │        │ the worker thread   │
   │  ctx: abc123/span1  │ ──────▶ │  ctx: (EMPTY) ❌     │
   │  task.execute(...)  │        │  → new spans become │
   └─────────────────────┘        │    a SEPARATE trace │
                                  │    root ❌           │
                                  └─────────────────────┘
                                  ✅ FIX: the TaskDecorator
```

### 4.4 SLO burn rate (the 30-day budget)

```text
  ALLOWED error rate (99.9% SLO) = 0.1%
  the 30-day budget = 43.2 minutes of failure time

  BURN RATE → time to budget exhaustion:
  ┌────────────┬──────────────────────────────┐
  │ burn rate  │ 30-day budget exhaustion     │
  ├────────────┼──────────────────────────────┤
  │     1×     │ 30 days  (the steady state)  │
  │     2×     │ 15 days                      │
  │     6×     │ 5 days                       │
  │    14.4×   │ 50 hours                     │
  │    100×    │ 7.2 hours                    │
  │   1000×    │ 43 minutes                   │
  └────────────┴──────────────────────────────┘

  ALERT POLICY (multi-window, multi-burn-rate):
    ┌──────────────────┬──────────────────┬───────────────────┐
    │ LONG window      │ SHORT window     │ SEVERITY          │
    ├──────────────────┼──────────────────┼───────────────────┤
    │ 6× (1 hour)      │ 3× (5 minutes)   │ ticket            │
    │ 14.4× (1 hour)   │ 14.4× (5 minutes)│ PAGE ⚠           │
    └──────────────────┴──────────────────┴───────────────────┘
    ⚠ BOTH windows must burn → the fast window catches a sudden break,
      the slow window catches a slow burn.
```

---

## 5. Worked Examples

### 5.1 Trace analysis (worked example)

```text
  SYMPTOM: POST /orders p99 = 4.2s (was 120ms)

  STEP 1 — the METRIC (which service?):
    http_server_requests_seconds_bucket{uri="/orders",method="POST"}
      p50 = 118ms   p95 = 141ms   p99 = 4212ms
    ⚠ THE SHAPE: p50 is FINE, p99 is 30× worse.
      → not a uniform slowdown. Something affects a MINORITY of requests.
        (if p50 were also 4s it would be uniform — a deployment, a config
         change, a bad query)

  STEP 2 — the TRACE (where is the time?):
    click the p99 exemplar → the trace opens:
      total: 4,180ms
      breakdown:
        POST /orders (server span)        4,180ms
        └─ JPA findById                      12ms
        └─ HTTP POST /charges (client)    4,120ms  ⚠
        │    └─ server span (payment)        38ms  ⚠
        └─ Kafka publish                      3ms
        └─ HTTP POST /inventory              45ms
    ⚠ the CLIENT span is 4,120ms but the SERVER span is 38ms.
      → 4,082ms was spent OUTSIDE the payment service.

  STEP 3 — the DIFFERENTIAL (whose fault?):
    the CLIENT span includes DNS + TCP + TLS + queue wait + serialisation
    + network + the server's think time.

    EVIDENCE from other spans:
      POST /inventory client span = 45ms, server span = 40ms
      → ⚠ the SAME client→server path is FAST for the inventory call!
      → so it is NOT the network, DNS, TLS, or LB.

    → the difference between the two calls: the payment target and the
      client configuration for it.
      · does the payment URL resolve to a different host/region/LB?
      · is the payment client's connection pool exhausted?
        → check connection-pending metrics on the client side
      · does the payment client use a different timeout (e.g. 30s) so the
        4.1s is queue wait?
        → check `server.address` on the span and the server-side queue time

  STEP 4 — THE VERDICT:
    most likely the payment gateway's REQUEST QUEUE (server think time) or
    the CLIENT's CONNECTION POOL exhaustion (client queue wait).
    → FIX: pool sizing, server capacity scaling, or a shorter timeout.
```

### 5.2 Cardinality explosion (worked example)

```text
  ⚠ THE BUG:
    @GetMapping("/users/{id}")
    public User get(@PathVariable String id) {
        meterRegistry.counter("users.viewed", "userId", id).increment();
        return userService.find(id);
    }

  THE RESULT:
    1,000,000 users × 1 counter = 1,000,000 time series
    ~2 KB each in Prometheus memory = 2 GB
    ⚠ scrape time grows (more series = more work)
    ⚠ query time grows

  THE SYMPTOM:
    the Prometheus pod gets OOM-killed → a metrics gap → empty dashboards,
    and if it's a shared Prometheus it took down other services' monitoring
    too.

  THE FIX:
    @GetMapping("/users/{id}")
    public User get(@PathVariable String id) {
        meterRegistry.counter("users.viewed", "userType", userType(id)).increment();
        // ⚠ a bounded value (an enum), NOT the userId
        // ⚠ the userId goes in the LOG (via MDC) and the TRACE
    }

  THE ALTERNATIVE (if you need per-user counts):
    · logs + click aggregation (Loki or Athena)
    · an event stream (Kafka → Flink → warehouse)
    · a high-cardinality store (ClickHouse, Druid)
    · ⚠ NEVER Prometheus
```

### 5.3 SLO burn rate (worked example)

```text
  SERVICE: the checkout API. SLO: 99.9% over 30 days.
  allowed error rate = 0.1% = 43.2 minutes of failure time.

  STEP 1 — observed error rate over the last hour:
    requests: 360,000
    5xx:        5,400
    rate:       5,400 / 360,000 = 1.5%

  STEP 2 — burn rate:
    1.5% / 0.1% = 15×

  STEP 3 — interpretation:
    15× → the 30-day budget is exhausted in 30 / 15 = 2 days
    ⚠ at this rate we blow the budget in 2 days.

  STEP 4 — the 5-minute window:
    480 / 30,000 = 1.6% → 16×
    → BOTH windows burning above 14.4× → ⚠ PAGE

  STEP 5 — budget remaining:
    consumed 2 days / 30 days = 6.7%
    → 93% left → feature work may continue, but this is the page's subject.

  STEP 6 — release decision:
    error-budget policy: 93% remaining → risky changes are OK (threshold 75%).
    ✅ the change can ship, with the reliability team aware.
```

---

## 6. Code from Scratch — Production-Grade

### 6.1 The Actuator configuration (a minimal viable setup)

```yaml
  management:
    endpoints:
      web:
        exposure:
          # ⚠ EXPOSE ONLY WHAT YOU NEED — /actuator/env and /actuator/configprops
          #   can leak secrets
          include: health,info,metrics,prometheus
    endpoint:
      health:
        show-details: when-authorized
        probes:
          enabled: true          # ⚠ K8s probes: /actuator/health/liveness + /readiness
    health:
      circuitbreakers:
        enabled: true           # ⚠ Resilience4j breaker health
    metrics:
      tags:
        application: ${spring.application.name}
        environment: ${APP_ENV:local}
      distribution:
        # ⚠ SLO-friendly histogram buckets (percentile query cost)
        percentiles-histogram:
          http.server.requests: true
        # ⚠ SLO buckets — 100ms, 500ms, 1s, 2s
        slo:
          http.server.requests: 100ms, 500ms, 1s, 2s

  management.prometheus.metrics.export.enabled: true

  management.otlp.tracing.endpoint: ${OTLP_ENDPOINT:http://otel-collector:4318/v1/traces}
  management.otlp.metrics.export.url: ${OTLP_ENDPOINT:http://otel-collector:4318/v1/metrics}
  # ⚠ the tracing sample rate — 1.0 in dev, 0.05-0.1 in prod
  management.tracing.sampling.probability: ${TRACING_SAMPLE_RATE:0.05}
```

### 6.2 The async context (the critical piece)

```java
  @Configuration
  public class AsyncConfig {

      /**
       * ⚠ THE CRITICAL PIECE. Without this decorator the @Async spans become
       *   ROOT spans of a separate trace, and the trace view is broken.
       *
       *   Spring Framework 6.1+ ships ContextPropagatingTaskDecorator, and
       *   Spring Boot 3.2+ auto-configures one for the default executor. If
       *   you build your own executor, you must set it yourself.
       */
      @Bean
      public TaskDecorator contextPropagatingTaskDecorator() {
          return new ContextPropagatingTaskDecorator();
      }

      @Bean(name = "applicationTaskExecutor")
      public Executor applicationTaskExecutor(
              TaskDecorator contextPropagatingTaskDecorator) {
          ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
          executor.setCorePoolSize(8);
          executor.setMaxPoolSize(32);
          executor.setQueueCapacity(200);
          executor.setThreadNamePrefix("async-");
          executor.setTaskDecorator(contextPropagatingTaskDecorator);
          executor.initialize();
          return executor;
      }
  }
```

### 6.3 Custom metrics (the Micrometer API)

```java
  @Component
  @RequiredArgsConstructor
  public class OrderMetrics {

      private final MeterRegistry registry;

      /**
       * COUNTER — for the rate.
       * ⚠ tag values MUST come from a BOUNDED set.
       */
      public void recordOrderCreated(String channel, String region) {
          Counter.builder("orders.created")
                  .description("Number of orders created")
                  .tag("channel", channel)     // "web" | "mobile" | "batch"
                  .tag("region", region)       // "us-east-1" | "eu-west-1"
                  .register(registry)
                  .increment();
      }

      /**
       * TIMER — the duration distribution.
       * ⚠ publishPercentileHistogram() gives aggregatable percentiles.
       */
      public void recordCheckout(Duration duration) {
          Timer.builder("checkout.duration")
                  .description("End-to-end checkout duration")
                  .publishPercentileHistogram()
                  .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(500))
                  .register(registry)
                  .record(duration);
      }

      /**
       * TIMER with a bounded dependency tag (SLO-friendly).
       */
      public void recordPaymentCall(String gateway, long millis) {
          Timer.builder("payment.gateway.duration")
                  .tag("gateway", gateway)
                  .publishPercentileHistogram()
                  .register(registry)
                  .record(millis, TimeUnit.MILLISECONDS);
      }

      /**
       * GAUGE — the instantaneous value (the callback reads live state).
       */
      public void registerPendingOrdersGauge(AtomicInteger pendingOrders) {
          registry.gauge("orders.pending", Tags.of("state", "awaiting_payment"), pendingOrders);
      }
  }
```

### 6.4 Cardinality guard and common tags

```java
  @Configuration
  public class MetricsConfig {

      /**
       * ⚠ THE CARDINALITY GUARD — the last line of defence. When a tag value
       *   count exceeds the limit, extra values are collapsed to "other".
       */
      @Bean
      public MeterFilter cardinalityGuard() {
          return MeterFilter.maximumAllowableTags(
                  "http.server.requests", "uri", 100, MeterFilter.deny());
      }

      @Bean
      public MeterFilter commonTags() {
          return MeterFilter.commonTags(Tags.of(
                  "application", "orders-service",
                  "env", System.getenv().getOrDefault("APP_ENV", "local")));
      }
  }
```

### 6.5 The OpenTelemetry agent (zero code changes)

```yaml
  # ⚠ The AGENT approach — no code changes.
  #   JAVA_TOOL_OPTIONS: "-javaagent:/otel/opentelemetry-javaagent.jar"
  #   OTEL_SERVICE_NAME: orders-service
  #   OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318
  #   OTEL_EXPORTER_OTLP_PROTOCOL: http/protobuf
  #   OTEL_TRACES_SAMPLER: parentbased_traceidratio
  #   OTEL_TRACES_SAMPLER_ARG: 0.05
  #   OTEL_RESOURCE_ATTRIBUTES: service.name=orders-service,deployment.environment=prod
  #   OTEL_PROPAGATORS: tracecontext,baggage
  #   OTEL_INSTRUMENTATION_COMMON_DB_STATEMENT_SANITIZER_ENABLED: true
  #     ⚠ CRITICAL: sanitises SQL parameter values (PII + cardinality)
```

**What the agent instruments for free:**

```text
  ✅ Spring MVC / WebFlux     server spans
  ✅ HTTP clients             OkHttp, Apache HttpClient, RestTemplate, WebClient, Feign
  ✅ JDBC / JPA               SQL spans (with the statement template)
  ✅ Kafka                    producer and consumer spans
  ✅ Redis                    command spans
  ✅ RabbitMQ                 consumer and producer spans
  ✅ gRPC                     client and server
  ✅ Reactor                  automatic context propagation
  ⚠  @Async / schedulers      CHECK — context may not propagate
  ⚠  JDBC parameter values    SANITIZE (PII leak)
```

### 6.6 Structured logging (JSON output)

```xml
  <!-- logback-spring.xml -->
  <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
          <customFields>{"service":"orders-service"}</customFields>
          <includeMdcKeyName>traceId</includeMdcKeyName>
          <includeMdcKeyName>spanId</includeMdcKeyName>
          <includeMdcKeyName>userId</includeMdcKeyName>
          <includeMdcKeyName>requestId</includeMdcKeyName>
      </encoder>
  </appender>
```

```java
  @Component
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @RequiredArgsConstructor
  public class TracingMdcFilter extends OncePerRequestFilter {

      private final Tracer tracer;

      @Override
      protected void doFilterInternal(HttpServletRequest request,
                                      HttpServletResponse response,
                                      FilterChain filterChain) throws ServletException, IOException {
          try {
              MDC.put("requestId", UUID.randomUUID().toString());
              MDC.put("method", request.getMethod());
              MDC.put("path", request.getRequestURI());

              Span currentSpan = tracer.currentSpan();
              if (currentSpan != null) {
                  MDC.put("traceId", currentSpan.context().traceId());
                  MDC.put("spanId", currentSpan.context().spanId());
              }

              filterChain.doFilter(request, response);
              MDC.put("status", String.valueOf(response.getStatus()));
          } finally {
              // ⚠ CRITICAL: pool threads are REUSED — a leaked MDC makes the
              //   NEXT request's logs carry the PREVIOUS request's trace id.
              MDC.clear();
          }
      }
  }
```

### 6.7 Manual spans (for work no framework instruments)

```java
  @Service
  @RequiredArgsConstructor
  public class OrderService {

      private final OrderRepository repository;
      private final RiskEngine riskEngine;
      private final Tracer tracer;

      public Order create(CreateOrderCommand cmd) {
          // ⚠ a MANUAL span for the business logic
          Span span = tracer.nextSpan().name("order.riskScoring").start();
          try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
              // ⚠ bounded attributes, no PII
              span.tag("order.itemCount", String.valueOf(cmd.items().size()));

              RiskScore score = riskEngine.score(cmd);
              span.event("risk.scored");
              return repository.save(build(cmd, score));
          } catch (Exception e) {
              span.error(e);            // ⚠ records the exception
              throw e;
          } finally {
              span.end();               // ⚠ ALWAYS end the span
          }
      }
  }
```

### 6.8 Prometheus recording and alerting rules

```yaml
  # prometheus-rules.yml
  groups:
    - name: slo
      interval: 30s
      rules:
        - record: service:http_requests:rate5m
          expr: sum(rate(http_server_requests_seconds_count[5m])) by (uri, method, status)

        - record: service:sli_error:ratio_rate1h
          expr: |
            sum(rate(http_server_requests_seconds_count{status=~"5.."}[1h]))
              /
            sum(rate(http_server_requests_seconds_count[1h]))

        - record: service:burn_rate:1h
          expr: service:sli_error:ratio_rate1h / 0.001   # 0.001 = the 99.9% SLO

        - alert: SloFastBurn
          expr: |
            service:burn_rate:1h > 14.4
              and
            service:burn_rate:5m > 14.4
          for: 2m
          labels:
            severity: page
          annotations:
            summary: "{{ $labels.uri }} is burning the error budget at 14.4× (30-day budget gone in ~50h)"

        - alert: ConnectionPoolExhausted
          expr: hikaricp_connections_pending > 5
          for: 2m
          labels:
            severity: page
          annotations:
            summary: "{{ $value }} requests pending on the connection pool"

        - alert: ThreadPoolSaturated
          expr: (tomcat_threads_busy / tomcat_threads_config_max) > 0.85
          for: 5m
          labels:
            severity: page
          annotations:
            summary: "Tomcat thread pool at {{ $value | humanizePercentage }} — latency will spike"
```

### 6.9 The Grafana RED + saturation panel queries

```json
  {
    "title": "Checkout API — RED + saturation",
    "panels": [
      {
        "title": "Rate (requests/sec)",
        "targets": [{ "expr": "sum(rate(http_server_requests_seconds_count{uri=~\"/orders.*\"}[1m]))" }],
        "type": "timeseries"
      },
      {
        "title": "Errors (5xx rate and ratio)",
        "targets": [{
          "expr": "sum(rate(http_server_requests_seconds_count{uri=~\"/orders.*\",status=~\"5..\"}[5m]))"
        }],
        "type": "timeseries"
      },
      {
        "title": "Duration (p50 / p95 / p99)",
        "targets": [{
          "expr": "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{uri=~\"/orders.*\"}[5m])) by (le))"
        }],
        "type": "timeseries"
      },
      {
        "title": "Saturation (connection pool pending)",
        "targets": [{ "expr": "hikaricp_connections_pending" }],
        "type": "timeseries",
        "thresholds": "0.5,0.8"
      },
      {
        "title": "Saturation (thread pool utilisation)",
        "targets": [{ "expr": "tomcat_threads_busy / tomcat_threads_config_max" }],
        "type": "gauge",
        "thresholds": "0.7,0.85"
      }
    ]
  }
```

### 6.10 A custom health indicator

```java
  @Component("paymentGateway")
  public class PaymentGatewayHealthIndicator implements HealthIndicator {

      private final RestClient client;

      public PaymentGatewayHealthIndicator(RestClient client) {
          this.client = client;
      }

      /**
       * ⚠ This is a READINESS check, not liveness. Readiness answers "can this
       *   pod serve traffic?" — a dependency being down makes the pod unready,
       *   but it must not make Kubernetes kill it.
       */
      @Override
      public Health health() {
          try {
              client.get().uri("/health").retrieve().toBodilessEntity();
              return Health.up().withDetail("gateway", "reachable").build();
          } catch (Exception e) {
              return Health.down(e).build();
          }
      }
  }
```

```yaml
  # ⚠ readiness and liveness are DIFFERENT probes
  livenessProbe:
    httpGet: { path: /actuator/health/liveness, port: 8080 }
    initialDelaySeconds: 30
    periodSeconds: 10
    failureThreshold: 3
  readinessProbe:
    httpGet: { path: /actuator/health/readiness, port: 8080 }
    initialDelaySeconds: 10
    periodSeconds: 5
    failureThreshold: 2
  startupProbe:
    httpGet: { path: /actuator/health/liveness, port: 8080 }
    failureThreshold: 30          # ⚠ covers the JVM warm-up
    periodSeconds: 2
```

---

## 7. Common Pitfalls & Gotchas

| # | ❌ Wrong | ✅ Right | Why |
|---|----------|----------|-----|
| 1 | `userId` as a metric label | userId in logs/traces | Cardinality explosion → Prometheus OOM |
| 2 | Raw URL as the `uri` tag | The route template (`/orders/{id}`) | 1M time series |
| 3 | No timeout on the health check | A 1–2s timeout | Probe hangs → pod restarts |
| 4 | Liveness probe checks dependencies | Liveness = the process; readiness = dependencies | A dependency outage → restart storm |
| 5 | `@Async` without context propagation | A `TaskDecorator` | Async spans become a separate trace |
| 6 | MDC without `clear()` in `finally` | Always clear | Pool threads are reused → logs lie |
| 7 | 100% trace sampling in production | 1–10% head sampling | Storage cost, collector CPU |
| 8 | Sampling decided per service | Head sampling (parent-based) | Inconsistent traces with gaps |
| 9 | SQL parameters in span attributes | The statement template only | PII leak, cardinality bomb |
| 10 | Exception message as a metric label | Exception *type* (bounded) | Cardinality bomb |
| 11 | `publishPercentiles` for SLOs | `publishPercentileHistogram` | Percentiles aren't aggregatable |
| 12 | Error rate counted as "not 2xx" | Exclude 4xx and 429 | A wrong SLI → a wrong SLO |
| 13 | Client percentiles across pods | Histogram quantiles | Each pod computes its own → a lying dashboard |
| 14 | Alerting on raw error rate | Burn rate, multi-window | Noise → alert fatigue |
| 15 | Breaker health on the readiness probe | A metric + an alert | Restart storm |
| 16 | Full stack traces at INFO | Stack at ERROR, message at INFO | Log volume and cost |
| 17 | Tracing off in dev | On, with a high sample rate | Dev is where you learn the flows |
| 18 | Swallowing the exception without `span.error(e)` | Always record it | The trace shows a successful span that actually failed |
| 19 | `log.info("id=" + id)` | `log.info("id={}", id)` | Allocation, unparseable logs |
| 20 | An SLO with no budget policy | The policy gates releases | A number on a dashboard changes nothing |

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner
**Q: What is distributed tracing?**
**A:** Following a single request as it crosses multiple services. Each service records a
**span** — a timed operation — and links it to its parent through context propagation (the
`traceparent` header). Spans join into a **trace**: the whole journey, usually shown as a
waterfall. It solves the problem that in a distributed system a stack trace only shows one
service, while the latency cause usually sits *between* services — the network, a queue, a
connection pool.

**Q: What's the difference between metrics, traces, and logs?**
**A:** Metrics are **aggregated numbers** over time — cheap, always on, for dashboards and
alerts. Traces are the **path of one request** — expensive, sampled, used to find where time
went. Logs are **discrete events** with rich context — noisiest, used to find what exactly
happened. The investigation order: metric says *what*, trace says *where*, log says *why*.

### 🟡 Intermediate
**Q: How does trace context propagate between services?**
**A:** Via headers. The standard is **W3C Trace Context**: a `traceparent` header carrying the
version, trace id (16 bytes hex), parent span id (8 bytes hex), and flags. The client library or
OTel agent injects it into outgoing requests; the server extracts it and creates a child span.
For messaging the same header goes into **message headers**, never the payload. The hard part
is the **async boundary** — an executor loses thread-local context, so you need a
`TaskDecorator`.

**Q: What is metric cardinality and why does it matter?**
**A:** The number of distinct time series for a metric — the product of distinct label values.
50 URIs × 5 methods × 10 statuses × 20 pods = 50,000 series, fine. Add a `userId` label with 1M
users and it becomes 50 **billion** — Prometheus OOMs, scrape time explodes, query time
explodes. The rule: bounded labels only (route template not concrete id, enums not free text);
put high-cardinality data in **span attributes** and **log fields**, where it belongs.

**Q: How do you measure latency correctly?**
**A:** Two common mistakes. (1) **Measure error responses too** — 500s often take *longer*
(timeout path, stack trace generation), so a p99 over 2xx only is a lie. (2) **Use the
histogram, not client-side percentiles** — client percentiles aren't aggregatable across
instances, so a multi-pod dashboard's p99 is a per-pod p99. Publish
`publishPercentileHistogram()` and compute with `histogram_quantile` over summed buckets.

### 🔴 Advanced / Senior
**Q: How do you debug a slow request spanning four services?**
**A:** Use the pillars in order. (1) **The metric narrows it**: compare p50 with p99. If p50 is
normal and p99 is 30× worse, it's a minority problem (specific path, specific dependency, pool
exhaustion, GC pause), not a uniform one (deploy, config change). (2) **The trace locates it**:
the key comparison is **client span vs server span**. A 4,120ms client span with a 38ms server
span means 4,082ms was spent outside the target service — DNS, TCP, TLS, pool wait, LB queue,
serialisation. (3) **The differential check**: look at another client→server call in the same
trace. If a call to a different service took 45ms end to end, network and TLS are fine, so the
problem is specific to that dependency. (4) **The metric confirms**: the target's own server-side
queue, thread pool, and DB pool metrics tell you which side. (5) **The log explains**: the
correlation id in the MDC takes you to the exact lines, including SQL timing and exception
detail. This only works because **the trace id is in every log line**.

**Q: How do you design an SLO for a service?**
**A:** Start from the **user journey**, not internals. (1) **Pick the SLI from user experience** —
e.g. "ratio of successful API responses" — and be precise about the denominator: exclude 4xx
(client fault) and 429 (deliberate load shedding), count 5xx **and timeouts** as failures.
Measure at the **server**, where truth is known. (2) **Pick the target** — 99.9% is a starting
point; it's a *business* decision, and the SRE's job is to make the cost visible. (3) **Set the
window** — 30 days smooths seasonality. (4) **Define burn-rate alerts** — multi-window,
multi-burn-rate (1h and 5m both above 14.4× → page; 6h and 30m above 6× and 3× → ticket).
(5) **Write the error-budget policy** — above 75% ship freely, 25–75% canary and review, below
25% freeze risky changes. Without (5) the SLO is just a number. (6) **Iterate** — the first SLI
is usually wrong, and the SLO report exists to force that conversation.

### ⚫ Expert / Principal
**Q: "We have Prometheus, Grafana, and ELK. The on-call still can't debug issues. What's
missing?"**
> You have the pillars' **infrastructure** but not their **correlation** — the thing that makes
them one system rather than three dashboards. (1) **The correlation id is the centre.** Without
a shared identifier the flow breaks: metrics say *what*, logs say *why*, but nothing joins them
and you manually cross-reference timestamps — exactly the work tracing was meant to remove.
Fix: trace id in the MDC plus **exemplars**, so a Grafana p99 spike is clickable into the
waterfall. (2) **The traces are probably mis-propagated.** Most teams that "have tracing" have
the SDK installed but broken propagation at `@Async` and messaging boundaries, producing
disconnected root spans. Symptom: a trace showing one service with no children, or a missing
hop. Also check **head sampling** — per-service sampling creates gaps that look like
uninstrumented services. (3) **Cardinality is usually why the metrics "don't work"** — slow
dashboards or randomly missing series, because Prometheus hit its sample limit and **silently
dropped** samples. Audit labels, apply `maximumAllowableTags`, move detail to traces and logs.
(4) **Alerts are mis-set** — paging on raw error rate rather than burn rate, and omitting
**saturation** alerts, which are the leading indicators. (5) **Context is missing** — an alert
saying "requests slow" without endpoint, dependency, and region dimensions is half a signal.
(6) **Possibly no structured logging** — free text can't be grouped or counted cheaply.
(7) **No runbook** — dashboards should be linked from alerts, and alert annotations should carry
the first three response steps.
> **Order I'd take them:** trace id in logs plus exemplars; propagation audit; cardinality audit;
burn-rate and saturation alerts; structured logging; runbook links. And the principle:
> **observability is a product for the on-call, not a collection of dashboards** — the measure of
> success is **mean time to diagnose**, and every manual step in that path is one to automate.

---

## 9. Counter-Questions & Traps

### CQ1. "Why not just use logs with a correlation ID? Why tracing?"
- **Naive answer:** "Tracing gives the timing breakdown." ← the trap
- **The trap:** The interviewer wants the distinction between *correlation* and *structured causation*.
- **Expert answer:** A correlation id gives the grouping, and for a two-service flow that is
often enough. Tracing adds three things logs can't: (1) **causation structure** — parent/child
relationships show the 4s delay was inside the payment *server* call, not merely somewhere in
the request; with logs you infer nesting from timestamps. (2) **Uninstrumented gaps** —
auto-instrumentation covers frameworks (Spring, JDBC, Kafka, HTTP clients) even where nobody
wrote code, so JPA query time and connection acquire time arrive free; an un-logged step is
invisible. (3) **Aggregated analysis** — trace data can be queried and aggregated ("show traces
where the payment client span exceeded 1s", p99 by dependency). And honestly: they're
complements. The trace id in the logs is the *link* — the trace says where, the log says why.

### CQ2. "Tracing is expensive. Why sample?"
- **Naive answer:** "To save storage." ← the trap
- **The trap:** The interviewer wants the sampling strategies and the tail problem.
- **Expert answer:** Cost is real (1–5 KB per span; millions of requests means terabytes), but
the deeper reason is that a diagnostic tool needs to be *representative and findable*, not
complete. The catch: **random sampling loses the slow requests**, which are what you want. The
hierarchy: (1) **Head sampling** (parent-based ratio at the edge, propagated to children) — the
default, and the only approach giving **consistent traces**. (2) **Tail sampling** (collector
buffers spans, decides at trace end) — keeps 100% of errors and slow requests while dropping
fast successes; the right answer for "I need the slow ones". (3) **Priority sampling** for
premium traffic. The honest trade-off: head sampling gives a statistically meaningful view of
the distribution but you will miss a 0.01% one-off error — which is why tail sampling exists and
why logs must remain the complete record.

### CQ3. "Should you alert on the error rate or something else?"
- **Naive answer:** "The error rate." ← the trap
- **The trap:** The SLO model and alert fatigue.
- **Expert answer:** Neither alone — alert on the **error-budget burn rate**, the error rate
normalised by the SLO. A 1% error rate is on-budget for a 99% SLO (1× burn) but 10× over for a
99.99% SLO, where the 30-day budget is gone in three days. Burn rate makes services comparable.
Multi-window solves fatigue: the fast window catches a sudden break, the slow window catches a
slow burn, and requiring **both** means a brief blip doesn't page. Also alert on **leading
indicators** — pool pending, thread utilisation, queue depth — which fire before latency and
errors and tell you why. Framing: the error rate is a **symptom**, the burn rate is a **budget
statement**, saturation metrics are **leading indicators**.

### CQ4. "How do you measure latency across a distributed call?"
- **Naive answer:** "A timer around the client call." ← the trap
- **The trap:** Separating client and server latency, and knowing what the gap means.
- **Expert answer:** Measure **both**; the difference is the value. The client span measures the
full round trip; the server span measures the work the server did. The gap = DNS + TCP + TLS +
pool wait + network + serialisation + server queue wait + response transit. That gap is where
"it's slow but the code is fine" lives. Pitfalls: (1) **clock skew** — durations are locally
computed, so only durations are reliable, not absolute timestamps; (2) **pool wait** is inside
the client span and often dominant — agent instrumentation attributes it to a separate
connection-get span; (3) **network vs server queue** — if the server's own metrics show queue
wait it's a capacity problem, if the server span starts late relative to the client send it's
network or pool; (4) never measure server work with the client span.

### CQ5. "What is a cardinality problem and how do you fix it?"
- **Naive answer:** "Use fewer labels." ← the trap
- **The trap:** The mechanism and the diagnostic tooling.
- **Expert answer:** Cardinality is the number of distinct series — the product of label values.
It breaks Prometheus first because memory is linear in series count while scrape and query cost
are super-linear in request rate. Causes by frequency: an ID as a label (userId, orderId,
sessionId, **trace id** — the classic mistake); the raw URL instead of the route template; the
exception message (unbounded); legitimately unbounded dimensions (tenant). Diagnostics:
`/api/v1/label/<label>/values` for value counts, `count by (__name__) ({__name__=~".+"})` for
series per metric, TSDB status, and the Micrometer registry listing. Fixes in order: move data to
span attributes and log fields; use route templates; bucket values (status class, not status);
`maximumAllowableTags` as a last resort; for genuine per-tenant analysis use Loki, Athena, or
ClickHouse. Note the symptom: cardinality problems often present as **metrics randomly missing**
because Prometheus silently drops samples at its limit.

### CQ6. "How do you handle observability in a multi-tenant system?"
- **Naive answer:** "Add the tenant id as a label." ← the trap
- **The trap:** The tension between per-tenant requirements and cardinality.
- **Expert answer:** Real requirement, but tenant id is high-cardinality by construction, so
layer it: (1) **Metrics stay bounded** — aggregate per plan/tier and per region for SLOs and
capacity, and keep per-tenant usage as counters that roll up or are bounded; (2) **Traces carry
the full tenant id as an attribute** — per-request and sampled, with exemplars linking an
aggregated metric to a specific tenant's trace; (3) **Logs carry it as a field** — precise
per-tenant queries at per-event cost, not per-series cost; (4) for noisy-neighbour detection use
a distributed per-tenant rate limiter plus bounded per-tier saturation metrics. The rule:
**metrics answer aggregate questions; high-cardinality detail lives in traces and logs.**

### CQ7. "Your service is behind a load balancer and the trace timing is wrong."
- **Naive answer:** "Disable the proxy headers." ← the trap
- **The trap:** Proxy protocol handling and header trust.
- **Expert answer:** Two problems. (1) **Forwarded headers** — `X-Forwarded-For/-Proto/-Host` and
`Forwarded` must be honoured via `server.forward-headers-strategy` (`native` or `framework`), but
⚠ only trust them when the request actually came through your proxy, or a client can spoof its IP
and poison audit logs. (2) **Timing distortion** — proxy buffering (`proxy_buffering on` by
default in Nginx) makes the client span include buffering; gzip at the proxy means the client
measures compressed transfer while the server measures uncompressed work; TLS termination at the
proxy puts the handshake in the client span. Fixes: disable buffering for streaming responses,
compare like with like on compression, and remember absolute numbers aren't comparable across the
proxy boundary while percentile distributions remain valid per side. A consistent ~50ms gap
suggests buffering or keep-alive; occasional multi-second gaps suggest client pool wait or server
queue wait.

### CQ8. "Why is my Prometheus query slow?"
- **Naive answer:** "Add hardware." ← the trap
- **The trap:** Diagnosing cardinality and query shape.
- **Expert answer:** Four causes in the order I'd check. (1) **Cardinality** — Prometheus keeps
every series in memory and a query scans matching series; 1M series turns 10ms into 10s. Check
`count by (__name__)` and the label-values API. (2) **Query shape** — unanchored regex matchers
(`{job=~".*"}`) force a full scan; exact matchers use the index. Long ranges with short steps
create enormous numbers of points; use shorter ranges, `irate()` for spikes, and recording rules.
(3) **High-cardinality dashboard variables** — a `$instance` or similar variable multiplies panel
load; constrain with regex filters. (4) **Remote read/federation** adds store lookups. Practical:
watch query duration in the UI and check the TSDB status page for series count and memory.

### CQ9. "How do you trace a message-driven flow end to end?"
- **Naive answer:** "Put the trace id in the payload." ← the trap
- **The trap:** Header-based propagation and async structure.
- **Expert answer:** Use **message headers**, not the payload: the payload schema stays clean and
consumers needn't know about tracing; headers are the metadata channel (Kafka `Headers`,
AMQP headers). The producer injects `traceparent` at publish, the consumer extracts at consume
and creates a span whose parent is the producer's span — or, for **fan-out**, a **span link**
rather than a parent, so parallel consumers don't appear as serial children (the OTel messaging
semantic conventions specify this). Harder cases: multiple consumers produce siblings with the
same parent, which the parent id handles correctly; batching means one poll with 100 messages
produces 100 spans, which is why links are the right model; delayed or retried messages inherit
the original producer context, so a message that sat an hour shows a one-hour gap — which is
actually valuable; and consumer lag is not in the span, so you need the lag metric.

### CQ10. "What's the difference between SLI, SLO, and SLA?"
- **Naive answer:** "The SLA is the SLO with a penalty." ← the trap
- **The trap:** Precise definitions and the SLO-as-decision-tool role.
- **Expert answer:** The **SLI** is the measured quantity — ratio, count, latency distribution.
The **SLO** is the target over a window — "99.9% of valid requests succeed over 30 days". The
**SLA** is the contractual commitment to an external party with financial consequence, and it is
deliberately **looser** than the SLO so you keep a buffer before breaching the contract. The SLO
is the internal engineering goal, the SLA the external business promise, and the gap is the
buffer. The point that usually matters: an SLO is useful **only if it changes behaviour** —
budget remaining gates release risk. And setting the target is a business trade-off; the SRE
makes the cost visible rather than deciding unilaterally. The classic failure is a 99.99% SLO on
a non-critical feature nobody will fund redundancy for.

### CQ11. "The dashboard p99 doesn't match what users report."
- **Naive answer:** "Users are exaggerating." ← the trap
- **The trap:** The catalogue of measurement discrepancies.
- **Expert answer:** Six likely reasons. (1) **Aggregation mismatch** — the dashboard p99 covers
all traffic; a user's request may be p99.9 or worse. Check p99.9, max, and the top bucket. 
(2) **Client vs server latency** — users experience DNS + TLS + network + server + return +
rendering; a 300ms server p99 with a 3s user report means 2.7s is elsewhere. Only RUM
(`PerformanceNavigationTiming`) measures the whole journey. (3) **Sampling bias** — if the sampler
drops requests during load spikes, your p99 is optimistic. (4) **Cross-instance aggregation** —
summing histogram buckets is correct; averaging per-pod quantiles dilutes the tail. (5)
**Different endpoint** — the user hit a specific expensive path; the `uri` label exposes it.
(6) **Resolution** — 10–15s scrape resolution hides sub-second spikes. Checklist: compare
percentile levels, check server vs end-to-end, verify the aggregation function, break down by
uri/status/instance/region, and check sampling bias.

### CQ12. "Do you need both Prometheus and OpenTelemetry?"
- **Naive answer:** "Yes, it's redundant." ← the trap
- **The trap:** Each system's specialisation.
- **Expert answer:** Not redundant — different questions and cost profiles, and OTel metrics
exist to unify the pipeline. Historically OTel tracing finalised first and its Java agent is the
most mature implementation, while metrics arrived later, so most teams run Prometheus's pull
model for metrics (de-facto standard; recording rules and Grafana dashboards depend on it) and
OTLP push for traces. The convergence: the OTel collector can receive and export Prometheus
scrape metrics, so the realistic end state is agent → OTLP → collector → traces to Tempo/Jaeger
and metrics to a Prometheus-compatible backend. For a new project: OTel agent for tracing,
Micrometer for metrics, collector unifying both. Honest trade-off: OTel metrics are the future
standard, but Prometheus's ecosystem maturity is unmatched, so "one stack" is the direction and
"two protocols" is today.

---

## 10. Rapid-Fire MCQs

**1.** The trace context propagation standard:
A) B3  B) W3C Trace Context  C) Jaeger  D) OpenTracing
**✅ B**

**2.** Cardinality explosion is caused by:
A) High RPS  B) High-cardinality labels  C) Long retention  D) Many metrics
**✅ B**

**3.** `MDC.clear()` exists because:
A) It frees memory  B) Tomcat pool threads are reused  C) Serialization  D) Permissions
**✅ B**

**4.** The RED method is:
A) Rate, Errors, Duration  B) Reliability, Efficiency, Durability  C) Read, Execute, Delete  D) Response, Error, Data
**✅ A**

**5.** A 99.9% SLO over 30 days allows:
A) 4.32 min  B) 43.2 min  C) 4.32 h  D) 7.2 h
**✅ B**

**6.** Context loss across `@Async` is fixed by:
A) `@EnableAsync`  B) A `TaskDecorator`  C) `ThreadLocal`  D) An `ExecutorService`
**✅ B**

**7.** Client-side percentiles are limited because they are:
A) Slow  B) Not aggregatable across instances  C) Memory-heavy  D) Inaccurate
**✅ B**

**8.** `traceparent` fields are:
A) version-traceId-spanId-flags  B) traceId-spanId  C) JSON  D) A UUID
**✅ A**

**9.** A saturation metric example:
A) CPU usage  B) HikariCP pending connections  C) GC count  D) Heap size
**✅ B**

**10.** Head sampling is decided at:
A) The collector  B) The first service (the edge)  C) The database  D) The client
**✅ B**

**11.** An SLI denominator should exclude:
A) 5xx  B) 4xx and 429  C) Timeouts  D) Nothing
**✅ B**

**12.** `publishPercentileHistogram()` gives:
A) Exact percentiles  B) Aggregatable percentiles  C) Less memory  D) Simpler config
**✅ B**

**13.** A 10× burn rate exhausts the 30-day budget in:
A) 3 days  B) 30 days  C) 30 hours  D) Never
**✅ A**

**14.** A liveness probe should check dependencies:
A) Yes  B) No — that causes restart storms  C) Only databases  D) Only in production
**✅ B**

**15.** Exemplars provide:
A) Compression  B) A link from a metric point to a trace  C) Sampling  D) Alert routing
**✅ B**

---

## 11. Hands-On Exercises

**🧪 Lab 1 — Read a waterfall**
1. Start Jaeger or Zipkin with a demo service, or attach the OTel agent to a local app.
2. Make a request that calls three services.
3. Compare the client span with the server span for one hop and compute the gap.

**🧪 Lab 2 — Explode cardinality**
1. Add a `userId` tag to a counter.
2. Hit the endpoint with 10,000 distinct ids.
3. Watch series count and Prometheus memory.

**🧪 Lab 3 — Break the async context**
1. Create a span inside an `@Async` method and inspect the trace view.
2. Confirm the async span is a separate root.
3. Add the `TaskDecorator` and re-check.

**🧪 Lab 4 — Burn the budget**
1. Write PromQL computing the burn rate for a 99.9% SLO.
2. Kill a dependency and watch the burn rate cross 14.4.

---

## 12. Code Challenges

### Challenge 1 — An observability starter (medium)
**Goal:** a starter module giving every new service the secured Actuator, the Prometheus registry,
common tags, the **cardinality guard**, the **MDC filter** with the trace id, structured JSON
logging, OTel agent configuration, correctly split liveness/readiness probes, and exemplar wiring.
**Requirements:** starter tests, authenticated metrics endpoint, health group configuration.

### Challenge 2 — An SLO platform (hard)
**Goal:** per-service SLO definitions in YAML, a generator for Prometheus recording rules and
burn-rate alerts, a generator for Grafana dashboards, and a monthly SLO report (burn-rate chart,
budget remaining, top incidents).
**Requirements:** YAML schema, generator tests, HTML/PDF report output.

### Challenge 3 — A propagation audit (hard)
**Goal:** a harness asserting context propagation across HTTP client/server, `@Async`, Kafka
producer/consumer, RabbitMQ, and schedulers — each asserting the child's parent id and a
consistent trace id — plus an MDC leak test (sequential requests must not share a trace id).
**Requirements:** the OTel in-memory exporter, span capture, assertions, messaging cases.

---

## 13. Scenario-Based Questions

**S1. "We have dashboards for everything, but during an incident the on-call spends 30 minutes
working out what to look at. 40 panels across 8 dashboards. What's wrong?"**
>
> **Ideal answer:** (1) **The framing:** the problem isn't the telemetry, it's the **information
> architecture**. Forty panels across eight dashboards is "dashboards as a data dump" —
> everything measured, nothing organised around a question. The on-call's actual sequence is:
> *is it down → which endpoint → which dependency → which version/pod → what changed?* The panels
> are arranged by system (CPU, heap, threads, DB) rather than by that decision path.
> (2) **Diagnosis:** no single starting point answering "is it down, and since when?"; no
> correlation, so after finding the panel the next step is copying a timestamp into a log search;
> signal-to-noise is poor, with leading saturation indicators buried among lagging CPU and GC;
> no runbook, so dashboards show *what* but never *what to do*.
> (3) **The fix, in order:** a **tiered structure** — Tier 1 is one screen with the RED signals
> per endpoint, the burn-rate gauge, the saturation gauges, and a **recent deploys** panel (the
> most commonly missing and most often the answer); Tier 2 is per-service deep dives (JVM, DB,
> dependency breakdown); Tier 3 is per-dependency client-side metrics. Then **exemplars** so a p99
> spike on Tier 1 clicks into the waterfall, **annotation links** so every alert deep-links to the
> right panel with variables pre-set plus the runbook, and a **consolidation pass** taking 40
> panels to about 12. The test of a good dashboard: an on-call who has never seen the service can
> answer "what's wrong?" in 60 seconds.
> (4) **Measurement:** mean time to acknowledge, mean time to diagnose, alert precision (false
> positive rate and actionable rate), missed incidents, and the on-call survey.
> (5) **Senior framing:** "The telemetry isn't the problem — you have plenty. It isn't organised
> around the decision path. The on-call's question is always the same five, so the dashboard
> should be tiered around them, with the deploys timeline on the first screen. Second fix is the
> correlation mechanism — exemplars and the trace id in logs — because without those every
> investigation is manual copy-paste, and that's where the 30 minutes goes. Third is making the
> alert an entry point rather than a dead end. And the consolidation rule: every panel must
> answer one of the five questions, or it moves to a deep dive or goes."

**S2. "Our Prometheus pod keeps getting OOM-killed. We have about 200 services."**
>
> **Ideal answer:** (1) **Diagnosis path:** check the TSDB status and
> `prometheus_tsdb_head_series` for series count and memory; find the metrics with the most
> series via `count by (__name__)`; inspect label value counts for the offenders.
> (2) **Causes by likelihood:** (a) a high-cardinality label on a hot metric — trace id, request
> id, raw URL, exception message, userId; (b) a **meter leak** — a counter registered inside a
> handler with a dynamic tag value creates a new meter per request, and the registry map grows
> unbounded (detector: series count rising monotonically); (c) a legitimate but unbounded
> dimension — tenant id, pod name with churn; (d) sheer instance count, where 500 series × 2,000
> instances is a million series; (e) histogram bucket multiplication — 100 buckets × 20 label
> combinations is 2,000 series for one metric.
> (3) **Fix plan:** immediately identify and remove the offending label, or fix the meter leak by
> caching meters under a bounded key; short term add the `maximumAllowableTags` guard, recording
> rules to pre-aggregate, and an alert on `deriv(prometheus_tsdb_head_series[1h]) > 0`; medium
> term move to sharding or a scalable backend (Mimir, Thanos) with downsampling; long term a
> documented cardinality budget per metric, a CI check counting registry size, and the guard on by
> default in the platform starter.
> (4) **Prevention:** the OOM is a feedback loop — more series, more memory, more pressure, more
> metrics added to debug. The durable fix is making the pattern safe by default, plus an ongoing
> weekly top-20-by-series report and an alert on rate of change rather than absolute count.

---

## 14. Tricky Interview Questions

**T1. "Sampling is 5%. How do you debug a one-off error that happened once this week?"**
> Be honest that the trace may not exist. The decision tree: (1) Is it in the **logs**? Errors
> logged at ERROR with the trace id form the complete, unsampled record, and the id leads you to
> the trace if it was sampled. So the first requirement is that errors are always logged with the
> trace id, exception type, and stack. (2) For the future, configure **tail sampling** to keep
> 100% of traces with error status, exception events, or duration over a threshold (the OTel
> collector's `tail_sampling` processor). (3) If reproducible, a load test or staging reproduction
> with 100% sampling gives a full trace. (4) **Exemplars** may point at a specific sample from the
> spike's bucket. (5) In practice one-off diagnosis usually lands in the logs — which is why
> structured logging with a correlation id is foundational, not optional.

**T2. "How do you measure effective latency — what the user experiences?"**
> Server metrics measure only the server's part. The full journey is DNS → TCP → TLS → request
> transit → proxy buffering → server queue → processing → response transit → rendering and JSON
> parsing. Three approaches: (1) **RUM** via `PerformanceNavigationTiming` — the only way to
> capture rendering; (2) **synthetic probing** from real vantage points on a schedule — catches
> ISP, DNS, and CDN problems your datacentre can't see; (3) the **client/server span gap** as a
> proxy for the network portion. Structure it so the SLO is defined on the server SLI (the truth)
> while user experience is monitored separately, since they answer different questions. The
> sharpest and most fixable cause of divergence is **payload size** — an API returning 500 KB when
> the UI renders five fields is a latency bug no server p99 will show you, because the server
> generated it quickly.

**T3. "Prometheus, Datadog, or New Relic?"**
> Decision axes in order: (1) **Cost model** — self-hosted is infrastructure cost with cardinality
> as your problem; SaaS is per-host/per-metric pricing, which converts cardinality into a bill.
> For 200 services this is often decisive. (2) **Tracing quality and correlation** — native OTel
> ingestion and one-click metric→trace→log drill-down, which is the SaaS's strongest argument.
> (3) **Query power and query cost** — PromQL and LogQL are powerful, but SaaS vendors charge for
> custom queries too. (4) **Lock-in** — OTel instrumentation plus a collector keeps you portable.
> (5) **Operational burden** — self-hosting Prometheus is a real job (upgrades, compaction, HA,
> capacity), decisive without an SRE function. Recommendation for 200 services: standard
> instrumentation is mandatory regardless; then self-host if you have SRE capacity, otherwise
> SaaS. Hybrid (traces to SaaS, metrics to Prometheus) works but loses the single pane.

**T4. "Your logs are 2 TB a day. Is that a problem?"**
> Both a cost and a signal-to-noise problem. Cost: at roughly $0.50/GB indexed that's about
> $30k/month, before retention and query latency. Sources in usual order: DEBUG logging in
> production (biggest single lever, one-line fix); stack traces at INFO for *expected* exceptions
> (a 404 can emit a full stack per request); logging full request/response bodies; duplicate
> logging across services; noisy third-party retry loops. Reductions by value: level discipline
> with per-package overrides and expiry; sampling noisy WARN categories while keeping counters at
> 100%; dedup with suppressed-count lines; tiered retention (7 days hot, 90 warm, 1 year cold in
> object storage); field minimisation with no PII and truncated fields. The goal isn't less
> logging but **less noise, same signal** — ERROR and WARN are a fraction of volume and carry the
> value. Measure volume by level and logger, top-10 loggers by volume, and alert on daily volume
> exceeding budget.

**T5. "How do you measure the success of your observability setup?"**
> In incident outcomes, not dashboard counts. Metrics that matter: **MTTD** (failure onset to
> alert — where leading saturation indicators win, since latency and error alerts fire after
> user impact); **mean time to diagnose** (where correlation and tiered dashboards pay off);
> **mean time to mitigate**; **change failure rate** (the business number that justifies the
> investment); **alert precision** (false positive rate under 5%, plus actionable rate).
> Qualitatively, the on-call survey and the postmortem question "what did we wish we'd had?" are
> the highest-signal backlog sources. The anti-metrics to avoid are dashboard count, metric count,
> and spans ingested — those are inputs, and optimising them produces exactly the 40-panel
> problem: more data, no more knowledge.

---

## 15. Follow-Up Questions Asked by Interviewers

**F1** (after tracing) → *"What if the context is lost between services?"*
→ The trace view becomes a forest of disconnected roots and you lose cross-service timing.
Causes in order: the **async boundary** (`@Async`, `@Scheduled`, `CompletableFuture`,
`ExecutorService`) — fix with a `TaskDecorator` or `ContextSnapshot`; the **messaging boundary** —
consumer isn't extracting from headers; an **uninstrumented client** that doesn't inject the
header; **sampling mismatch** where services sample differently — fix with parent-based head
sampling; a **proxy stripping unknown headers**. Diagnosis: a single service span with no children
means inbound propagation is broken; a span with children but no parent means outbound is broken.

**F2** (after metrics) → *"How do you choose histogram buckets?"*
→ Align boundaries with SLO thresholds plus the tail values you need for diagnosis — coarse
SLO buckets (100ms, 500ms, 1s) and finer tail resolution (1s, 2s, 5s, 10s, 30s) so "is the p99 two
seconds or twenty?" is answerable. Trade-off: each bucket is a series, so a 20-bucket histogram
costs 20× a counter. Note that `publishPercentileHistogram()` generates roughly 70 exponential
buckets by default — customise it for the metrics that matter. And bucket granularity determines
how precisely exemplars can drill from a metric point into a trace.

**F3** (after SLO) → *"Who decides the SLO number?"*
→ A business decision informed by engineering analysis; the SRE makes the cost visible rather
than deciding. Engineering proposes a number from current reliability and architectural
capability; the business weighs revenue at stake and customer expectations; the SLO report makes
it a structured conversation showing current performance, proposed target, and the cost of the
gap. Two pitfalls: setting a target above current capability without investment guarantees
missing it, and a missed SLO destroys trust in the practice; setting it too low to be cheap lets
customer experience degrade uncaptured.

**F4** (after cardinality) → *"What if the cardinality is genuinely needed?"*
→ Then use a different system for that data. Options in preference order: **logs** with
structured fields (per-tenant analysis is what log aggregation does well, cost is per byte not per
series); **trace attributes** with exemplars linking aggregates to specific instances; an
**analytical store** (ClickHouse, Druid, BigQuery, Athena); **recording rules** that aggregate the
dimension away; and only as a last resort Prometheus with per-tenant cardinality limits. The
principle: the metrics layer is the aggregate layer by design, and when you need individual detail
you're querying a different system. The temptation to "just add the label" is how a Prometheus
ends up in a memory crisis.

---

## 16. Real 3–5 Year Scenarios

**R1. "We added OpenTelemetry across 12 services over 3 months. It's in production but the team
doesn't use it — they say 'the trace is nice but I find it faster in the logs'. What went wrong?"**
>
> **Ideal answer:** (1) **Framing:** a common adoption failure with three components —
> instrumentation gaps (the trace doesn't show what the team needs), workflow gaps (no path from
> symptom to trace), and cost (the overhead is real). "I find it faster in the logs" is a rational
> response to poor integration, not resistance to the technology.
> (2) **Diagnosis, in the order I'd check:**
> (a) **Correlation missing** — if the trace id isn't in log lines, the team can't get from a log
> line to the trace, so tracing is a separate system they must go and look in, while logs are
> already where their runbook and muscle memory live. Fix: MDC filter with the trace id (a day's
> work) plus exemplars. Highest-leverage change, because it turns tracing from "a thing to learn"
> into "a thing that's already where I am".
> (b) **Traces incomplete or fragmented** — propagation broken at async and messaging boundaries,
> so traces show three services and then nothing. The team concludes tracing is unreliable, which
> is worse than not having it. Fix: propagation audit and consistent head sampling (per-service
> sampling creates gaps that look like missing instrumentation).
> (c) **Overhead unmanaged** — 100% sampling or an unconfigured collector inflates p99 and burns
> CPU; the team felt the regression and distrusted it. Fix: 1–10% sampling, agent memory sizing,
> collector batching plus the `memory_limiter` processor (without it the collector OOMs and takes
> traces with it), and tail sampling for errors.
> (d) **Trace search worse than grep** — if the backend can't search by error status, service,
> attribute, and duration, the team is right to prefer logs. Fix: backend choice (Tempo's search is
> much better than Jaeger's here) and a designed query catalogue.
> (e) **Habit** — a change-management problem. Make the trace reachable from where they already
> are, prove one concrete use case, and train on a real incident.
> (3) **Plan:** week 1 trace id in MDC plus exemplars plus a one-page "symptom → trace" runbook;
> week 2 propagation audit, sampling consistency, collector limiter, agent tuning; month 2 backend
> evaluation against real queries plus a named query catalogue; month 3 a game day where the team
> diagnoses a real scenario using only the stack, and you fix the friction it exposes.
> (4) **Measurement:** percentage of incidents where traces were used, mean time to diagnose before
> vs after, trace query volume, percentage of complete traces, agent overhead, collector drop rate.
> (5) **Senior framing:** "The team isn't wrong — that's what happens when the trace is a separate
> system instead of the same system. The pillars must be correlated by the same identifier or the
> human brain is the correlation engine, which is exactly the work you were removing. First fix is
> the trace id in logs and exemplars: a day's work, and the difference between a system people must
> learn and one they already use. Second is completeness — fragmented traces destroy trust, and the
> cause is usually async and Kafka propagation plus inconsistent sampling. Third is overhead. Fourth
> is search UX. And the fifth, which matters most for adoption, is change management: tools are
> adopted when they appear inside the workflow the team already has, and one successful game-day
> diagnosis teaches the habit faster than any documentation."

---

## 17. Frequently Asked Production Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| Prometheus OOM | Cardinality explosion | Cardinality analysis, label removal, the guard |
| Prometheus samples randomly missing | Sample limit hit, samples silently dropped | Reduce cardinality, shard |
| Grafana p99 disagrees with users | Percentile level mismatch; client vs server latency | Check p99.9, add RUM, check payload size |
| Trace shows one service only | Broken propagation (async, messaging) | `TaskDecorator`, header extraction |
| Trace has gaps | Inconsistent per-service sampling | Head sampling with `parentbased` |
| Span timestamps don't match logs | Clock skew, proxy buffering | Compare durations, not absolute times |
| Slow metrics queries | Cardinality, unanchored regex, long ranges | Exact matchers, recording rules |
| Agent adds 15% to p99 | 100% sampling, too many spans | Lower sample rate, span filtering |
| OTel collector OOM | Missing `memory_limiter` | Add the processor, use tail sampling |
| Logs show the wrong trace id | MDC leak (no `clear()`) | `finally { MDC.clear(); }` |
| Alert fires constantly | Raw error rate, no burn rate | Burn-rate rules |
| Grafana is slow | High-cardinality template variables | Constrain variables, consolidate panels |
| Log volume doubled | DEBUG level in prod, new exception logging | Level discipline, sampling |

---

## 18. Debugging Scenarios

**D1. "Find the metric with exploding cardinality:"**
```promql
  # top 20 metrics by series count
  topk(20, count by (__name__) ({__name__=~".+"}))

  # series count growth rate (the leak detector)
  deriv(prometheus_tsdb_head_series[1h]) > 0

  # label breakdown for a suspect metric
  count by (userId) ({__name__="users_viewed_total"})   # ⚠ 1M here means a leak
```

**D2. "Confirm async context loss:"**
```bash
  grep "traceId=a2fb4a1d1a96d312" /var/log/orders/*.log | head -20
  # ⚠ if the async lines carry a DIFFERENT trace id, the context was lost
```

**D3. "Is the client or the server the bottleneck?"**
```promql
  # the server's own processing time
  histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{uri="/charges"}[5m])) by (le))
  # vs the client-observed time
  histogram_quantile(0.99, sum(rate(payment_gateway_duration_seconds_bucket[5m])) by (le))
  # ⚠ the GAP = network + pool wait + queue
```

**D4. "Check the OTel collector's health:"**
```promql
  rate(otelcol_receiver_refused_spans_total[5m])
  rate(otelcol_exporter_send_failed_spans_total[5m])
  container_memory_working_set_bytes{name="otel-collector"}
```

**D5. "Audit the trace structure:"**
```bash
  # Tempo API — the trace tree
  curl -s "http://tempo:3200/api/traces/$TRACE_ID" \
    | jq '[.batches[].scopeSpans[].spans[] | {name, parentSpanId, traceId}]'
  # ⚠ a span with no parentSpanId that should have one = a propagation gap
```

---

## 19. Architecture Design Questions

**Q1. 🏗️ Design the observability stack for a 30-service platform.**
> (1) **Instrumentation standard** — OTel agent plus Micrometer plus structured logging, shipped
> in the platform starter so it's the default rather than a per-team decision. (2) **Collector
> topology** — daemonset or gateway, with `memory_limiter`, `batch`, and `tail_sampling`.
> (3) **Backends** — Prometheus and Grafana, Loki, Tempo (or a SaaS) with capacity planning: a
> series budget, tiered retention, storage growth. (4) **Correlation wiring** — MDC, exemplars,
> dashboard deep links. (5) **The SLO platform** — a definition format, generated burn-rate rules,
> generated dashboards, generated monthly reports. (6) **Alerting philosophy** — burn rate,
> saturation, never raw error rate. (7) **Runbooks and on-call process** — game days, postmortem
> template, alerts that link to panels. (8) **Cost model** — a volume budget and a cardinality
> budget.

**Q2. 🏗️ Design observability for a multi-tenant SaaS.**
> (1) **Tenant isolation** in metrics (tiers, quotas, noisy-neighbour detection) with full detail
> in traces and logs. (2) **Per-tenant SLOs** — per-tenant availability reporting is a product
> promise, and per-tenant burn rate is the core of it. (3) **PII handling** — hashed tenant ids,
> residency requirements, log redaction at the collector. (4) **Cost attribution** — per-tenant
> usage metering. (5) **Audit trail** — security event logging with tamper evidence. (6)
> **Per-tenant dashboards** with self-serve access.

**Q3. 🏗️ Design observability for a hard compliance requirement (healthcare, finance).**
> (1) **Immutable audit trail** — access events in a separate store with required retention and
> non-repudiation. (2) **PII controls** — redaction at the collector, field allow-lists, encryption
> at rest. (3) **Data residency** — region routing and per-region backends. (4) **Access control on
> the observability tools themselves** — dashboards expose internals and are a privilege-escalation
> path, so they need their own RBAC. (5) **SLOs reflecting the contract.** (6) **Retention and
> deletion policies.**

---

## 20. Cheat Sheet — One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  THE THREE PILLARS: METRICS (aggregate) · TRACES (path) · LOGS (discrete)   │
│  ⚠ investigation order: METRIC (what) → TRACE (where) → LOG (why)           │
├──────────────────────────────────────────────────────────────────────────────┤
│  METRICS: RED (Rate, Errors, Duration) + USE (Utilization, Saturation,      │
│  Errors)                                                                     │
│  ⚠ cardinality is the killer — NO userId/orderId/traceId as a label         │
│  ⚠ latency must include ERROR responses                                      │
│  ⚠ histogram (not client percentiles) for aggregatable p99                  │
├──────────────────────────────────────────────────────────────────────────────┤
│  TRACES: trace id + span id + parent id                                      │
│  W3C traceparent: version-traceId-spanId-flags                               │
│  ⚠ the async boundary LOSES context → TaskDecorator                         │
│  ⚠ HEAD sampling for consistent traces; TAIL sampling for errors and slow    │
│  ⚠ CLIENT span vs SERVER span = the network/queue gap                       │
├──────────────────────────────────────────────────────────────────────────────┤
│  LOGS: STRUCTURED (JSON) + MDC (traceId, userId, requestId)                  │
│  ⚠ MDC.clear() in a finally — Tomcat threads are reused                      │
│  ⚠ level discipline: INFO for business, ERROR for actionable, DEBUG dev-only │
├──────────────────────────────────────────────────────────────────────────────┤
│  SLO: SLI (measured) → SLO (target) → SLA (contract)                         │
│  a 99.9% SLO over 30 days = 43.2 minutes of budget                           │
│  burn rate = observed rate / allowed rate                                    │
│  ALERT on the burn rate (multi-window: 5m AND 1h > 14.4× → page)            │
│  ⚠ an SLO without a budget POLICY is just a dashboard                       │
│  ⚠ exclude 4xx and 429 from the SLI denominator                             │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 21. Follow-up Learning

- → [06 · Distributed Data Management & Sagas](./06-Distributed-Data-Management-and-Saga-Patterns.md)
- → [04 · Circuit Breaker & Resilience](./04-Circuit-Breaker-and-Resilience.md)
- → [01 · Actuator & Observability](../01-Spring-Boot-Fundamentals/04-Actuator-and-Observability.md)
- → [14 · Deployment & DevOps](../14-Deployment-DevOps/README.md)

---
**Next Chapter:** → [06 · Distributed Data Management & Sagas](./06-Distributed-Data-Management-and-Saga-Patterns.md)
**Previous Chapter:** ← [04 · Circuit Breaker & Resilience](./04-Circuit-Breaker-and-Resilience.md)

