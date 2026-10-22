# 04 · Observability, SRE & Incident Response

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★☆ ("Production is broken — how do you find out why?")
> **Prerequisite:** [03 · CI/CD & Release Strategies](./03-CICD-and-Release-Strategies.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. Monitoring vs Observability](#1-monitoring-vs-observability)
- [2. The Three Pillars (+ One)](#2-the-three-pillars--one)
- [3. SLIs, SLOs and Error Budgets](#3-slis-slos-and-error-budgets)
- [4. Alerting That Doesn't Burn Out the Team](#4-alerting-that-doesnt-burn-out-the-team)
- [5. The Java Metrics That Matter](#5-the-java-metrics-that-matter)
- [6. Structured Logging](#6-structured-logging)
- [7. Distributed Tracing in Practice](#7-distributed-tracing-in-practice)
- [8. Incident Response](#8-incident-response)
- [9. Postmortems](#9-postmortems)
- [10. Capacity & Cost Signals](#10-capacity--cost-signals)
- [11. Code Examples](#11-code-examples)
- [12. Common Mistakes](#12-common-mistakes)
- [13. Interview Questions & Model Answers](#13-interview-questions--model-answers)
- [14. Self-Check & Revision Card](#14-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> **Monitoring** answers questions you predicted ("is CPU high?"). **Observability** lets you
> answer questions you didn't predict ("why are German users' checkouts slow only on mobile?").
> The difference is cardinality and context, not tooling.
>
> Three pillars: **metrics** (cheap, aggregated, alertable), **logs** (detailed, expensive,
> searchable), **traces** (causal, sampled, show where time went). Plus **exemplars** to jump from
> a metric spike to a specific trace.
>
> **Alert on symptoms, not causes.** "Checkout error rate > 1%" is a symptom users feel. "CPU >
> 80%" is a cause that may be harmless. **Every page must be actionable, urgent, and about something
> a human must do now** — everything else is a ticket or a dashboard.
>
> **SLOs turn reliability into a number**: 99.9% over 30 days = 43 minutes of error budget. When
> the budget is spent, feature work pauses. That's how you avoid arguing about reliability with
> anecdotes.
>
> **Incident response:** declare early, assign a single incident commander, mitigate before you
> diagnose, communicate on a schedule, and write a blameless postmortem with owned action items.

---

## 1. Monitoring vs Observability

| | Monitoring | Observability |
|---|---|---|
| Question | "is X above threshold?" | "why is this specific request slow?" |
| Data | pre-aggregated metrics | high-cardinality events, traces, logs |
| Failure mode | you only see what you instrumented | you can ask new questions without deploying |
| Cost | low | higher (storage, cardinality) |

**The practical test:** if a user reports "checkout is slow for users in Berlin on iOS 17", can you
answer it without a code change? If you can filter by those dimensions, you have observability. If
you can only see "checkout p99 is up", you have monitoring.

**But:** you don't need infinite cardinality. You need the **right** dimensions — service, endpoint,
status, tenant, region — plus traces for the rest.

---

## 2. The Three Pillars (+ One)

```
                    ┌──────────────┐
                    │   METRICS    │  cheap, aggregated, alertable
                    │  (counters,  │  "what is happening?"
                    │   gauges,    │
                    │  histograms) │
                    └──────┬───────┘
                           │ exemplar: trace_id
                           ▼
                    ┌──────────────┐
                    │   TRACES     │  sampled, causal, per-request
                    │  (spans,     │  "where did the time go?"
                    │   context)   │
                    └──────┬───────┘
                           │ trace_id / span_id in MDC
                           ▼
                    ┌──────────────┐
                    │    LOGS      │  detailed, expensive, searchable
                    │ (structured  │  "what exactly happened?"
                    │   events)    │
                    └──────────────┘

   + EVENTS/DEPLOYS  ← "what changed?" — the most underrated signal
```

### 2.1 What each is for

| Pillar | Strength | Weakness | Use for |
|---|---|---|---|
| **Metrics** | cheap at any scale, fast queries, alerting | no per-request detail, aggregation loses outliers | SLOs, dashboards, alerts, capacity |
| **Traces** | causality, per-request timing, dependency map | sampling, storage cost | debugging latency, finding the slow span |
| **Logs** | maximum detail, arbitrary fields | expensive, slow at volume, unbounded cardinality | forensics, errors, audit |
| **Deploy/change events** | answers "what changed?" instantly | needs integration | correlating an incident with a release |

**The one line to remember:** *"metrics tell you something is wrong, traces tell you where, logs tell
you why — and the deploy timeline tells you what changed."*

### 2.2 Correlation is the point

```java
// the trace id is the join key across all three
MDC.put("traceId", currentTraceId());
log.info("payment failed");       // log carries traceId
// and the metric exemplar carries it too — so a latency spike links to a trace
```

Without a shared id, you have three separate tools and a lot of copy-pasting.

---

## 3. SLIs, SLOs and Error Budgets

### 3.1 Definitions

```
SLI  = the measurement          "fraction of requests with status < 500 and latency < 300ms"
SLO  = the target               "99.9% over a rolling 30 days"
SLA  = the contract             "99.9% or service credits"  (money attached)
BUDGET = 1 - SLO                "43 minutes of allowed failure per 30 days"
```

### 3.2 Choosing SLIs

| Service type | Good SLI |
|---|---|
| Request/response | **availability** (non-5xx / total) and **latency** (% under threshold) |
| Async consumer | **freshness** (age of the oldest unprocessed message) |
| Data pipeline | **completeness** (% of expected records landed) |
| Storage | **durability** and **read success** |
| Batch | **timely completion** (% finishing before the deadline) |

**Latency SLIs must be a threshold, not an average**: "95% of requests under 300 ms" is a
statement about users; "average 120 ms" is not.

### 3.3 Multi-window burn-rate alerting

The industry-standard approach (Google SRE workbook):

| Window | Burn rate | Meaning |
|---|---|---|
| 1 h | 14.4x | 2% of the monthly budget in an hour → **page** |
| 6 h | 6x | 5% in 6 hours → **page** |
| 3 d | 1x | burning at exactly the sustainable rate → **ticket** |

```promql
# 1-hour burn rate for a 99.9% availability SLO
(1 - (sum(rate(http_server_requests_seconds_count{status!~"5.."}[1h]))
      / sum(rate(http_server_requests_seconds_count[1h])))) / 0.001
```

**Why not alert on the raw error rate?** Because 1% errors for one minute may be fine, and 0.2% for
a day may be fatal. Burn rate weights **severity by duration**, which is exactly what matters.

### 3.4 Why error budgets work

```
budget healthy  → ship features fast, take risks
budget 50% used → slow down, review risky changes
budget spent    → freeze features, fix reliability, then resume
```

It converts "should we fix reliability or ship?" from an argument into a rule. **Quote this in an
interview** — it shows you've operated a service, not just built one.

---

## 4. Alerting That Doesn't Burn Out the Team

### 4.1 The hierarchy

```
PAGE     immediate human action required, users affected    → 3am wake-up justified
TICKET   needs action within a day or two                   → business hours
DASHBOARD informational, no action                          → look when curious
```

**If it doesn't require immediate human action, it must not page.** This single rule fixes most
alert fatigue.

### 4.2 Symptoms vs causes

| ❌ Cause-based (noisy) | ✅ Symptom-based (actionable) |
|---|---|
| CPU > 80% | checkout error rate > 1% for 5 min |
| heap > 80% | p99 latency > 1s for 10 min |
| `disk_io > X` | consumer lag > 10 min of processing |
| pod restarts > 0 | login success rate < 99% |

Cause-based alerts detect *conditions*; symptom-based alerts detect *user impact*. The latter is
what a page is for. (Cause metrics still belong on dashboards — they're how you diagnose.)

### 4.3 The alert quality checklist

- [ ] **Actionable** — the runbook says what to do.
- [ ] **Urgent** — waiting until morning makes it materially worse.
- [ ] **Owned** — a team, not "someone".
- [ ] **Accurate** — low false-positive rate (measure it).
- [ ] **Specific** — names the service and the SLO.
- [ ] **Linked** — a dashboard and a runbook in the alert payload.

```yaml
groups:
  - name: orders-slo
    rules:
      - alert: OrdersErrorBudgetFastBurn
        expr: |
          (sum(rate(http_server_requests_seconds_count{app="orders",status=~"5.."}[1h]))
           / sum(rate(http_server_requests_seconds_count{app="orders"}[1h]))) > 0.0144
        for: 2m
        labels:
          severity: page
          team: checkout
        annotations:
          summary: "orders: 1h error budget burning 14x"
          runbook: "https://runbooks/orders-error-budget"
          dashboard: "https://grafana/d/orders"
```

**`for: 2m`** matters — without it, a single bad scrape pages someone.

### 4.4 The metrics of your alerting

Track: **pages per week**, **% of pages actioned**, **% of incidents found by alert vs by user**
(if users find most incidents, your alerting is wrong), and **time to detect**.

---

## 5. The Java Metrics That Matter

### 5.1 The RED and USE methods

**RED** for request-driven services: **R**ate, **E**rrors, **D**uration.
**USE** for resources: **U**tilization, **S**aturation, **E**rrors.

```
RED:  http_server_requests_seconds_count{status=~"5.."} / total     (errors)
      rate(http_server_requests_seconds_count[1m])                   (rate)
      histogram_quantile(0.99, ...)                                   (duration)

USE:  cpu utilization · thread pool saturation · connection pool saturation
```

### 5.2 The JVM / Spring metrics to actually watch

| Metric | Why | Alert when |
|---|---|---|
| `http_server_requests_seconds` (histogram) | latency and error rate per endpoint | p99 > SLA |
| `hikaricp_connections_pending` | threads waiting for a DB connection | > 0 sustained |
| `hikaricp_connections_usage` | pool utilisation | > 0.8 sustained |
| `executor_active_threads` / `executor_queued_tasks` | thread pool saturation | queue > 0 sustained |
| `jvm_gc_pause_seconds` | GC pauses | p99 > 200ms |
| `jvm_memory_used_bytes{area="heap"}` after GC | leak detection | monotonic rise |
| `jvm_threads_live_threads` | thread leaks | unbounded rise |
| `jvm_classes_loaded_classes` | classloader leaks | monotonic rise |
| `tomcat_threads_busy` | request saturation | near max |
| `lettuce_command_completion` (or Redis client) | cache latency | p99 > 10ms |
| `kafka_consumer_fetch_manager_records_lag_max` | consumer health | growing for 10 min |

**The single most valuable one:** `hikaricp_connections_pending`. A non-zero value means requests
are queueing for a connection — the most common invisible bottleneck in Spring apps
([T13 ch04](../13-Testing/04-Performance-Load-and-CI-Pipelines.md)).

### 5.3 Histograms, not averages

```java
Timer.builder("checkout.duration")
     .publishPercentiles(0.5, 0.95, 0.99)
     .publishPercentileHistogram()
     .register(registry)
     .record(() -> checkout(cart));
```

**Percentiles cannot be averaged across instances.** Either publish a histogram (and aggregate via
`histogram_quantile`) or accept per-instance percentiles. A dashboard that averages per-instance
p99s is wrong.

---

## 6. Structured Logging

### 6.1 JSON to stdout

```yaml
logging:
  pattern:
    console: '{"ts":"%d{ISO8601,UTC}","level":"%p","logger":"%logger{36}","thread":"%t","msg":"%replace(%m){'"'"'"'"'"'"'"'"','"'"'\\\\'"'"'"'"'"'"'"'"'}","traceId":"%X{traceId:-}","spanId":"%X{spanId:-}","service":"orders"}%n'
```

**Better: use a proper encoder** rather than a regex-in-a-pattern:

```xml
<encoder class="net.logstash.logback.encoder.LogstashEncoder">
  <includeMdcKeyName>traceId</includeMdcKeyName>
  <includeMdcKeyName>spanId</includeMdcKeyName>
  <includeMdcKeyName>tenantId</includeMdcKeyName>
</encoder>
```

### 6.2 The rules

| Do | Don't |
|---|---|
| Log **events**, with structured fields | log free-text sentences you'll regex later |
| Include ids: `orderId`, `customerId`, `traceId` | log PII, tokens, card numbers |
| Log at boundaries: inbound request, outbound call, state change | log inside tight loops |
| Use the right level (ERROR = someone must act) | log everything at INFO |
| Sample debug logs | leave DEBUG on in production |

```java
log.info("order placed orderId={} customerId={} totalCents={}",
         order.id(), order.customerId(), order.totalCents());
```

**The most common logging mistake:** logging exceptions without the exception
(`log.error("failed: " + e.getMessage())`) — you lose the stack trace and the cause. Always pass
the exception as the last argument.

### 6.3 Log volume and cost

Logs are the most expensive pillar per byte. A noisy service at INFO can produce terabytes a day.
Control it: log the **outcome**, not every step; sample high-volume paths; and set retention per
index (7 days hot, 30 days warm, 1 year cold).

---

## 7. Distributed Tracing in Practice

### 7.1 Sampling

| Sampling | How | When |
|---|---|---|
| **Head-based** (probabilistic) | decide at the root: 1% | cheap, simple, may miss rare errors |
| **Head-based** (rate-limited) | N traces/sec | predictable cost |
| **Tail-based** | decide after the trace completes: keep slow/error traces | best signal, needs a collector that buffers |

**The practical setup:** 1–10% head sampling in production, with **tail sampling** at the collector
to keep 100% of errors and slow traces. That gives you complete coverage of the interesting cases
at a fraction of the cost.

```yaml
# OpenTelemetry collector tail sampling
processors:
  tail_sampling:
    decision_wait: 10s
    policies:
      - name: errors
        type: status_code
        status_code: { status_codes: [ERROR] }
      - name: slow
        type: latency
        latency: { threshold_ms: 1000 }
      - name: baseline
        type: probabilistic
        probabilistic: { sampling_percentage: 1 }
```

### 7.2 Span design

```
GET /api/checkout                     ← server span (root for this request)
├── SELECT orders ...                 ← DB span (auto)
├── inventory.reserve                 ← custom business span
│   └── POST http://inventory/...     ← client span
├── payment.charge                    ← custom business span
│   └── POST https://stripe/...       ← client span
└── outbox.append                     ← custom span (cheap but useful)
```

**Rule:** the trace should let you see **which business step** was slow, not just which HTTP call.
Custom spans around business operations are what make traces useful beyond latency.

```java
@Observed(name = "payment.charge", contextualName = "charge-card")
public ChargeResult charge(ChargeRequest req) { ... }
```

### 7.3 What traces answer

- Which dependency is slow, and how much of the total it accounts for.
- Where a retry happened (two identical child spans).
- Whether a request touched a cache or the DB.
- The blast radius of a slow downstream: which endpoints depend on it.

**What they don't:** exact counts at scale (sampling), or business questions. That's metrics and
logs.

---

## 8. Incident Response

### 8.1 The lifecycle

```
DETECT ──► TRIAGE ──► DECLARE ──► MITIGATE ──► RESOLVE ──► POSTMORTEM
  │          │          │            │            │            │
alert     severity    IC + channel  restore     verify      action items
or user   assess      comms cadence service     stable      with owners
```

### 8.2 Severity

| Sev | Definition | Response |
|---|---|---|
| **SEV1** | total outage, data loss, security breach | page everyone, exec comms, all hands |
| **SEV2** | major degradation, one region/tenant down | page on-call, IC assigned |
| **SEV3** | minor degradation, workaround exists | business hours |
| **SEV4** | cosmetic, single user | ticket |

### 8.3 Roles

| Role | Responsibility |
|---|---|
| **Incident Commander (IC)** | decides, coordinates, does **not** debug |
| **Ops lead** | hands on keyboards |
| **Comms lead** | status updates to stakeholders on a schedule |
| **Scribe** | timeline, decisions, what was tried |

**The single most important rule:** **the IC does not debug.** Someone whose hands are in the code
loses situational awareness. Separating the roles is what makes the response scale.

### 8.4 The first five minutes

```
1. Is it us?           check the dashboard, the alert, the SLO burn
2. What changed?       the deploy timeline, config changes, feature flags, traffic
3. What's the blast?   all users or some? which region/tenant/endpoint?
4. Declare.            a Slack channel, an IC, a severity — early, not when certain
5. Mitigate, don't diagnose:
   rollback · flip the flag · failover · shed load · scale out · block the bad input
```

**"Mitigate before you diagnose"** is the discipline that separates a 15-minute incident from a
2-hour one. You can find the root cause afterwards — with the system healthy.

### 8.5 Communication cadence

```
T+0    "Investigating reports of checkout failures. IC: @ada. Next update 15:30."
T+15   "Identified: payment provider timeouts. Mitigation: circuit breaker forced open,
        checkout falling back to queue. Next update 15:45."
T+30   "Resolved: provider recovered 15:38. Monitoring. Postmortem by Friday."
```

**Update on a schedule even when there's nothing new** — silence generates escalations.

### 8.6 The common first moves by symptom

| Symptom | First move |
|---|---|
| Error rate spike after a deploy | **rollback** (don't debug first) |
| Latency spike, no deploy | check the slow dependency's spans |
| DB CPU at 100% | kill the slow query, then fix it |
| Consumer lag growing | scale consumers, check for a poison message |
| Pods restarting | OOMKilled? liveness probe? read `--previous` logs |
| Traffic spike | rate limit, shed load, scale (in that order) |

---

## 9. Postmortems

### 9.1 Blameless, and specific

```
# Incident: Checkout failures, 2026-03-11 14:02–14:38 UTC

## Impact
- 12,400 failed checkouts (4.1% of attempts) over 36 minutes
- Estimated €38k in delayed revenue, all recovered
- SEV2, detected by alert at 14:04 (2 min after onset)

## Timeline (UTC)
14:02  payment provider (Stripe) latency rises to 8s
14:04  OrdersErrorBudgetFastBurn alert fires
14:07  IC assigned, incident channel opened
14:09  Deploy timeline checked: no deploy since 11:00 — provider issue, not ours
14:12  Circuit breaker OPEN confirmed; fallback queuing payments
14:15  Bulkhead saturated — checkout still failing, found the pool at 100%
14:20  Scaled pool; failures continue from queue backlog
14:38  Provider recovers; backlog drains; error rate back to baseline

## Root causes
1. Stripe latency exceeded our 1s read timeout → circuit breaker opened (correct)
2. The fallback path used the same connection pool as the primary path →
   the pool saturated, so the fallback couldn't run either (design flaw)
3. Bulkhead was configured but with a shared pool, so it provided no isolation

## What went well
- Alert fired in 2 minutes; rollback wasn't needed and we correctly identified it
- Fallback logic worked once capacity was available

## Action items
- [ ] Separate connection pool for the payment fallback path — @ada — Mar 20
- [ ] Alert on hikaricp_connections_pending > 0 for 1m — @bob — Mar 14
- [ ] Load test with a degraded provider (chaos) — @chen — Mar 31
- [ ] Add a chaos experiment: provider latency 10s for 5 min — @chen — Apr 15
```

### 9.2 What makes a postmortem good

| Good | Bad |
|---|---|
| Specific timeline with timestamps | vague narrative |
| **Contributing factors**, not a single "root cause" | "human error" |
| Blameless — asks what made the error easy | names an individual |
| Action items with owners and dates | "we should be more careful" |
| Includes what went well | exclusively negative |
| Shared publicly | buried in a ticket |

**"Human error" is not a root cause.** The question is always: what made that error easy, and what
would have caught it?

### 9.3 From incidents to reliability

```
incident ──► postmortem ──► action items ──► tests/alerts/design changes
                                    │
                                    └──► if none of these changed, the incident will repeat
```

**The measure of a postmortem is whether the same incident can happen again.**

---

## 10. Capacity & Cost Signals

### 10.1 Capacity metrics that predict incidents

| Metric | Warning sign |
|---|---|
| DB connection pool utilisation | > 80% sustained |
| Thread pool queue depth | > 0 sustained |
| Heap after GC | rising trend |
| Disk usage | > 80% (at 100% databases stop) |
| Consumer lag | growing for > 10 min |
| Kafka partition count vs consumers | consumers pinned at partition count |
| Rate limit headroom | > 50% of provider quota |
| Certificate expiry | < 30 days |

### 10.2 Cost observability

```
cost per request = (infra cost / requests)
cost per tenant  = attributing shared infra by usage
```

Tag resources by service/team/environment so cost is attributable — untagged infrastructure is
unmanageable cost. The classic finding: a forgotten staging cluster, or an over-provisioned
`requests` value preventing bin-packing (requesting 4 GB and using 800 MB).

**Rightsizing is a reliability *and* cost win:** a `request` far above actual usage means fewer pods
per node, so autoscaling is slower and the bill is higher.

---

## 11. Code Examples

### 11.1 Micrometer + Prometheus

```yaml
management:
  endpoints.web.exposure.include: health,info,prometheus,metrics
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${ENVIRONMENT:local}
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 100ms,300ms,1s
```

```java
@Configuration
class MetricsConfig {
    @Bean
    MeterRegistryCustomizer<MeterRegistry> commonTags(
            @Value("${spring.application.name}") String app) {
        return r -> r.config().commonTags("service", app);
    }

    @Bean
    TimedAspect timedAspect(MeterRegistry r) { return new TimedAspect(r); }
}
```

### 11.2 Business metrics with dimensions

```java
@Service
public class CheckoutService {
    private final Counter checkoutFailed;
    private final DistributionSummary orderValue;

    public CheckoutService(MeterRegistry registry) {
        this.checkoutFailed = Counter.builder("checkout.failed")
            .tag("reason", "unknown")            // tag values are added per call, see below
            .register(registry);
        this.orderValue = DistributionSummary.builder("checkout.order.value")
            .baseUnit("eur_cents")
            .publishPercentileHistogram()
            .register(registry);
    }

    public CheckoutResult checkout(Cart cart) {
        try {
            var result = doCheckout(cart);
            orderValue.record(result.totalCents());
            return result;
        } catch (PaymentDeclinedException e) {
            registry.counter("checkout.failed", "reason", "declined").increment();
            throw e;
        } catch (Exception e) {
            registry.counter("checkout.failed", "reason", "error").increment();
            throw e;
        }
    }
}
```

**Cardinality warning:** never tag with `orderId`, `customerId` or a raw exception message — that's
unbounded cardinality and it will kill Prometheus. Tag with a bounded enum: `reason`.

### 11.3 SLO burn-rate alert rules

```yaml
groups:
  - name: slo
    interval: 30s
    rules:
      - record: slo:orders_availability:ratio_1h
        expr: |
          sum(rate(http_server_requests_seconds_count{app="orders",status!~"5.."}[1h]))
          / sum(rate(http_server_requests_seconds_count{app="orders"}[1h]))

      - alert: OrdersFastBurn
        expr: (1 - slo:orders_availability:ratio_1h) > 14.4 * (1 - 0.999)
        for: 2m
        labels: { severity: page, team: checkout }
        annotations:
          summary: "orders burning 14.4x error budget"
          runbook_url: "https://runbooks/orders"

      - alert: OrdersSlowBurn
        expr: (1 - slo:orders_availability:ratio_1h) > 6 * (1 - 0.999)
        for: 30m
        labels: { severity: ticket, team: checkout }
```

### 11.4 Trace-aware logging filter

```java
@Component
public class TraceMdcFilter extends OncePerRequestFilter {
    private final Tracer tracer;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        var span = tracer.currentSpan();
        if (span != null) {
            MDC.put("traceId", span.context().traceId());
            MDC.put("spanId", span.context().spanId());
        }
        MDC.put("path", req.getRequestURI());
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove("traceId");
            MDC.remove("spanId");
            MDC.remove("path");     // never leak MDC on a pooled thread
        }
    }
}
```

**`MDC.clear()` in `finally` is mandatory** — on a pooled thread, a leaked trace id makes every
subsequent log line lie about which request it belongs to.

### 11.5 Health, readiness and info endpoints for ops

```yaml
management:
  info.env.enabled: true
  endpoint:
    health.probes.enabled: true
    info.enabled: true
info:
  app: { name: orders, version: "@project.version@" }
  build: { time: "@maven.build.timestamp@", commit: "${GIT_SHA:unknown}" }
```

`/actuator/info` with the commit sha is how a smoke test proves the deploy actually happened.

---

## 12. Common Mistakes

| Mistake | Consequence | Fix |
|---|---|---|
| Alerting on causes (CPU, heap) | alert fatigue, missed real incidents | alert on symptoms/SLO burn |
| No `for:` on a rule | one bad scrape pages | `for: 2m+` |
| Averaging p99s across instances | nonsense dashboards | histograms + `histogram_quantile` |
| High-cardinality metric tags | Prometheus OOM | bounded enums only |
| Logging PII / tokens | compliance breach | never log secrets; mask |
| `log.error("failed: " + e.getMessage())` | no stack trace | pass the exception |
| MDC not cleared | logs attribute to the wrong request | clear in `finally` |
| No trace id in logs | can't correlate | MDC from the tracer |
| No deploy markers | hours spent before checking "what changed" | deploy annotations in Grafana |
| `for: 0s` on every alert | noise | tune |
| No runbook | 20 minutes figuring out what to do | link it in the alert |
| Sampling 0.1% with no tail sampling | never see the errors | tail sampling keeps errors |
| IC also debugging | loses the overview | separate roles |
| Debugging before mitigating | long incidents | rollback first |
| "Human error" as a root cause | nothing changes | what made it easy, what would catch it |
| Action items with no owner/date | they never happen | owner + deadline in the doc |

---

## 13. Interview Questions & Model Answers

**Q1. Production is broken. How do you find out why?**
> First, what changed — the deploy timeline, a config or flag change, traffic. Then the symptom
> shape: error rate or latency? all users or one region/tenant/endpoint? Then a trace of a failing
> request to see which span is failing, and the logs for that trace id to see why. If it correlates
> with a deploy, I roll back before diagnosing, because mitigation beats understanding when users
> are affected.

**Q2. What's the difference between monitoring and observability?**
> Monitoring answers questions I predicted when I set up the dashboard. Observability lets me ask a
> new question — "why is checkout slow for iOS users in Berlin?" — without changing code. That
> requires high-cardinality data and correlation by trace id, not just pre-aggregated metrics.

**Q3. How do you decide what pages someone at 3am?**
> Only things that are urgent, user-impacting, and actionable. So I alert on SLO burn rate rather
> than CPU: the page says users are being affected and the runbook says what to do. Everything else
> is a ticket or a dashboard. And I measure the page volume and the false-positive rate, because an
> alert nobody trusts is worse than no alert.

**Q4. How do you set an SLO?**
> Pick the SLI users actually care about — availability as non-5xx over total, latency as the
> fraction under a threshold. Then set the target from what the business needs and what the
> architecture can deliver, remembering that dependencies multiply: five 99.9% dependencies on the
> serial path give 99.5%. Then derive the error budget — 99.9% over 30 days is 43 minutes — and use
> it to decide when to stop shipping features.

**Q5. Why alert on burn rate instead of error rate?**
> Because severity is a function of both magnitude and duration. 5% errors for two minutes may be
> nothing; 0.2% for a day destroys the budget. Burn rate compares the current rate to the
> sustainable rate, so a fast burn pages immediately and a slow burn creates a ticket — with
> multi-window rules for both.

**Q6. Which Java metrics do you watch first?**
> For request services: the HTTP histogram — latency percentiles and error rate per endpoint. Then
> the Hikari pool: pending connections is the most under-watched metric and the most common hidden
> bottleneck. Then executor queue depth, GC pause time, and heap-after-GC for leaks. And consumer
> lag for anything async.

**Q7. How do you debug a slow request across five services?**
> A distributed trace. I'd look for the span with the largest self-time, not the largest total —
> the total includes children, so a slow leaf is the culprit. Then check whether it's one call or
> many, whether retries appear as duplicate child spans, and whether the slowness is CPU (profiler)
> or I/O (downstream latency). Without tracing I'd be correlating logs by timestamp, which doesn't
> scale past two services.

**Q8. Talk me through an incident you'd run.**
> Detect from the SLO alert, triage the blast radius, declare a severity and open a channel with an
> incident commander who doesn't debug. First question is what changed. Mitigate before diagnosing —
> rollback, flag off, failover. Communicate on a fixed cadence even with no news. Once stable,
> verify with the SLO back at baseline, then write a blameless postmortem with contributing factors
> and action items that have owners and dates.

**Q9. What makes a postmortem good?**
> A specific timeline, contributing factors instead of a single "root cause", no individual blame,
> action items with owners and deadlines, and a section on what went well. The test is whether the
> same incident can happen again — if nothing in the code, the tests, or the alerts changed, it
> will.

**Q10. How do you control observability cost?**
> Cardinality first — bounded tag values only, no ids or raw error messages. Then retention: short
> hot retention, longer cold. Then sampling: 1% head sampling with tail sampling that keeps all
> errors and slow traces, which is where the value is. And logs at the outcome level rather than
> every step, since logs are the most expensive pillar per useful byte.

---

## 14. Self-Check & Revision Card

- [ ] I can distinguish monitoring from observability with an example.
- [ ] I can name the three pillars and what each answers.
- [ ] I can define SLI/SLO/SLA/error budget and quote the 43 minutes.
- [ ] I can explain burn-rate alerting and why it beats raw error rate.
- [ ] I can name the Java metrics to watch, including the connection pool.
- [ ] I can explain histogram vs percentile aggregation.
- [ ] I can design structured logs with trace correlation.
- [ ] I can explain head vs tail sampling.
- [ ] I can run an incident: declare, mitigate, communicate.
- [ ] I can write a blameless postmortem with owned action items.

```
┌──────────── OBSERVABILITY & SRE — 60s RECALL ───────────────────────────────┐
│ MONITORING answers predicted questions. OBSERVABILITY answers new ones.      │
│ METRICS say something's wrong · TRACES where · LOGS why · DEPLOYS what changed│
│                                                                              │
│ SLO 99.9%/30d = 43 MIN of error budget. Spent ⇒ freeze features.             │
│ ALERT ON SYMPTOMS + BURN RATE (14.4x/1h page, 6x/6h page, 1x/3d ticket).     │
│ A page must be urgent + actionable + owned. Else ticket/dashboard.           │
│                                                                              │
│ JAVA: http histogram p99 · HIKARICP_CONNECTIONS_PENDING (the hidden killer) · │
│   executor queue · GC pause · heap-after-GC · consumer lag.                  │
│ Percentiles DON'T average across instances — use histograms.                  │
│                                                                              │
│ LOGS: JSON to stdout, traceId in MDC (CLEAR IT in finally), never PII,       │
│   pass the exception object, log outcomes not steps.                          │
│ TRACING: 1% head + TAIL sampling keeping errors/slow traces.                  │
│   Read the largest SELF-time span, not the largest total.                     │
│                                                                              │
│ INCIDENT: declare early · IC does NOT debug · MITIGATE BEFORE DIAGNOSE ·      │
│   update on a cadence. POSTMORTEM: timeline, contributing factors,            │
│   owners+dates. "Human error" is not a root cause.                            │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [03 · CI/CD & Release Strategies](./03-CICD-and-Release-Strategies.md) · **Next:** [05 · Cloud, Infrastructure as Code & Cost](./05-Cloud-IaC-and-Cost.md)
