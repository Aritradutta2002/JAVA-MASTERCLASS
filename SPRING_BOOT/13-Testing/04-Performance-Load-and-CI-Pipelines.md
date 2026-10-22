# 04 · Performance, Load Testing & CI Pipelines

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★☆☆ ("How do you know it will hold at 10x traffic?")
> **Prerequisite:** [03 · Testcontainers & Contract Testing](./03-Testcontainers-and-Contract-Testing.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. The Four Kinds of Performance Test](#1-the-four-kinds-of-performance-test)
- [2. What to Measure](#2-what-to-measure)
- [3. Load Testing with Gatling & k6](#3-load-testing-with-gatling--k6)
- [4. JMH for Microbenchmarks](#4-jmh-for-microbenchmarks)
- [5. Profiling — Finding Where Time Goes](#5-profiling--finding-where-time-goes)
- [6. The CI Pipeline](#6-the-ci-pipeline)
- [7. Quality Gates](#7-quality-gates)
- [8. Flaky Test Management](#8-flaky-test-management)
- [9. Test Data Management](#9-test-data-management)
- [10. Code Examples](#10-code-examples)
- [11. Common Mistakes](#11-common-mistakes)
- [12. Interview Questions & Model Answers](#12-interview-questions--model-answers)
- [13. Self-Check & Revision Card](#13-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> Four different questions, four different tests: **load** (does it meet the SLA at expected
> traffic?), **stress** (where does it break?), **soak** (does it degrade over hours?),
> **spike** (does it survive a sudden 10x?).
>
> **Report percentiles, never averages.** p50 hides the pain; p99 is what users feel and what
> SLAs are written on. An average of 50 ms with a p99 of 4 s is a broken system that looks fine
> on a dashboard.
>
> **A load test without a bottleneck analysis is entertainment.** Always capture CPU, GC, DB
> connections, pool saturation and slow queries during the run, and identify the first saturated
> resource.
>
> **CI:** fast unit tests on every push, integration tests on every PR, load tests on a schedule or
> before a release — never on every commit. Quality gates (coverage floor, mutation score on core
> packages, no new critical CVEs) prevent slow decay without blocking on noise.

---

## 1. The Four Kinds of Performance Test

| Test | Question | Shape | Duration |
|---|---|---|---|
| **Load** | do we meet the SLA at expected traffic? | steady at target | 30–60 min |
| **Stress** | where does it break, and how? | ramp up until failure | 30–60 min |
| **Soak / endurance** | does it degrade over time? | steady for hours | 2–24 h |
| **Spike** | does it survive a sudden surge? | instant 10x | minutes |
| **Capacity** | how much can one instance handle? | ramp, find the knee | 30 min |
| **Breakpoint** | exact failure threshold | slow ramp | 1–2 h |

### 1.1 What each one catches

- **Load** — SLA breaches under the design's expected conditions.
- **Stress** — the failure mode itself: does it degrade gracefully or collapse? Do timeouts and
  circuit breakers work? Does it recover?
- **Soak** — **memory leaks, connection leaks, log growth, cache bloat, FD leaks**. Bugs that
  only appear after hours. The most under-run test and the highest-value in production.
- **Spike** — autoscaling lag, queue buildup, thundering herds, cold caches.
- **Capacity** — the actual number for your capacity plan ([T12 ch05](../12-System-Design-for-Java-Backend/05-Capacity-Estimation-and-Back-of-Envelope.md)).

### 1.2 Realistic load

**A test that only hits one endpoint with one user is not a load test.** Realistic means:

- A **traffic mix** matching production (e.g. 80% reads, 15% writes, 5% checkout).
- **Think time** between requests per virtual user (or you're testing a DDoS, not a workload).
- **Cache behaviour as in production** — start cold at least once, and warm for the steady-state case.
- **Realistic data volume** — a table with 1,000 rows plans differently from one with 10M.
- **Realistic auth** — JWT verification, session lookups, not a bypassed security filter.

**The most common load-testing lie:** testing a 3-endpoint service with 10 rows of data on a
laptop-sized DB, getting great numbers, and extrapolating to production.

---

## 2. What to Measure

### 2.1 The latency distribution

```
p50   the median — half are faster
p90   the "typical bad" user
p95   where most dashboards are drawn
p99   the SLA number; 1% of users, but often 20% of revenue
p999  the tail — usually a GC pause, a lock, or a cold cache
max   one-off outliers; useful only with the count
```

**Never average.** Two distributions with the same mean:

```
A: 10, 10, 10, 10, 10, 10, 10, 200   → mean 34, p99 200
B: 34, 34, 34, 34, 34, 34, 34, 34    → mean 34, p99 34
```

Same average, completely different user experience.

### 2.2 Throughput vs latency curve

```
latency
   ▲
   │                                    ╱   ← knee: past this, latency explodes
   │                          ╱────────╱
   │              ╱───────────╱
   │  ╱───────────╱
   └──────────────────────────────────────► throughput (req/s)
        stable            knee        collapse
```

**Find the knee.** That's your instance's real capacity, and it's usually at 60–75% of theoretical
throughput. Operating past it means latency grows super-linearly
([T12 ch02](../12-System-Design-for-Java-Backend/02-Scalability-Availability-and-Reliability.md)).

### 2.3 The metrics to capture during a run

| Layer | Metric | Why |
|---|---|---|
| Load generator | achieved RPS, error rate, latency percentiles | did the test actually apply load? |
| App | CPU, heap, GC pause/time, thread count | saturation, GC pressure |
| App | request queue depth, Tomcat busy threads | thread pool exhaustion |
| App | p99 per endpoint | which endpoint breaks first |
| DB | connections in use vs pool size | pool saturation (very common) |
| DB | slow queries, lock waits, buffer hit ratio | the real bottleneck |
| Cache | hit rate, latency, evictions | cache effectiveness under load |
| Network/OS | context switches, load average, network saturation | generator vs server |
| Errors | which error codes, at what rate | the first symptom |

**The single most common finding:** the app is fine and the **connection pool is at 100%**.
`hikari.pool-size = 10` with 200 concurrent users means 190 of them are waiting on a connection,
and no amount of CPU will help.

---

## 3. Load Testing with Gatling & k6

### 3.1 Gatling (Scala DSL, JVM)

```scala
class CheckoutSimulation extends Simulation {

  val httpProtocol = http.baseUrl("https://staging.example.com")
    .acceptHeader("application/json")
    .authorizationHeader("Bearer " + token)

  val browse = scenario("Browse")
    .exec(http("list").get("/api/products?page=1"))
    .pause(1, 3)                                  // think time — essential
    .exec(http("detail").get("/api/products/42").check(status.is(200)))

  val checkout = scenario("Checkout")
    .exec(http("addToCart").post("/api/cart").body(StringBody("""{"sku":"SKU-1","qty":1}""")).asJson)
    .pause(2)
    .exec(http("checkout").post("/api/checkout").check(status.is(201)))

  setUp(
    browse.inject(rampUsers(2000).during(60.seconds)),    // 80% of traffic
    checkout.inject(rampUsers(200).during(60.seconds))    // realistic mix
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.percentile3.lt(500),             // p95 < 500 ms
     global.responseTime.percentile4.lt(1000),            // p99 < 1 s
     global.successfulRequests.percent.gt(99.5)
   )
}
```

**Assertions turn a load test into a test** — a CI run fails when the SLA is breached, instead of
producing a report nobody reads.

### 3.2 k6 (JavaScript, single binary)

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    steady: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 500 },    // ramp
        { duration: '10m', target: 500 },   // steady state
        { duration: '1m', target: 0 },      // ramp down
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.005'],
  },
};

export default function () {
  const res = http.get(`${__ENV.BASE_URL}/api/products/42`);
  check(res, { 'status 200': r => r.status === 200 });
  sleep(1 + Math.random() * 2);             // think time
}
```

`k6 run --out experimental-prometheus-rw` streams metrics to Prometheus, which is how you correlate
load with app/DB metrics on the same dashboard.

### 3.3 Choosing

| | Gatling | k6 | JMeter |
|---|---|---|---|
| Language | Scala DSL | JS | XML GUI |
| Resource use | JVM, moderate | very low | high |
| CI friendliness | good | excellent | poor |
| Reporting | excellent HTML | good (+ Grafana) | dated |
| Best for | JVM shops | most teams today | legacy |

**Whatever the tool:** the generator must not be the bottleneck. One k6 instance handles ~10–30k
VUs; Gatling similar. Verify by checking the generator's own CPU — if it's at 100%, your numbers
are about the generator, not the server.

---

## 4. JMH for Microbenchmarks

**Never benchmark with `System.currentTimeMillis()` around a loop.** The JIT will eliminate dead
code, warm-up will distort results, and you'll measure the wrong thing.

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class MoneyParseBenchmark {

    private String input;

    @Setup
    public void setup() { input = "1234.56"; }

    @Benchmark
    public BigDecimal bigDecimal() { return new BigDecimal(input); }

    @Benchmark
    public Money money() { return Money.of(input); }

    @Benchmark
    public long centsManual() {
        int dot = input.indexOf('.');
        return Long.parseLong(input.substring(0, dot)) * 100
             + Long.parseLong(input.substring(dot + 1));
    }
}
```

**When JMH is worth it:** you're choosing between two implementations of a hot path (a serializer,
a parser, a hashing scheme) and the difference might matter at your QPS.

**When it isn't:** almost everything else. For a service, end-to-end latency under load matters
far more than any single method's nanoseconds.

**The trap to mention:** microbenchmark results rarely transfer to production, because real code
has cache misses, lock contention, allocation pressure and GC. A 3x microbenchmark win can be
invisible end-to-end.

---

## 5. Profiling — Finding Where Time Goes

### 5.1 The tools

| Problem | Tool |
|---|---|
| CPU hot spots | `async-profiler`, JFR, YourKit |
| Allocation / GC pressure | JFR allocation profiling, `-Xlog:gc*` |
| Lock contention | JFR `JavaMonitorEnter`, async-profiler `--lock` |
| Blocked threads | `jstack`, JFR thread dumps |
| Slow SQL | `pg_stat_statements`, `EXPLAIN ANALYZE` |
| Slow HTTP dependency | distributed tracing spans |

### 5.2 JFR — always-on, low overhead

```bash
java -XX:StartFlightRecording=filename=app.jfr,duration=120s,settings=profile -jar app.jar
```

```bash
jfr print --events jdk.ObjectAllocationInNewTLAB --stack-depth 10 app.jfr | head -50
jfr print --events jdk.ExecutionSample app.jfr | head -50          # CPU hot spots
```

Also enable **continuous JFR** in production with a small ring buffer — when an incident happens,
you have the data instead of guessing.

### 5.3 async-profiler — flame graphs

```bash
asprof -d 30 -f profile.html <pid>
```

Open the flame graph and read it **top-down for the call path, left-to-right for width = time**.
The widest frame that is *your* code is where to look — not the widest frame overall (usually
`Object.wait` or a socket read).

### 5.4 The profiling discipline

```
1. REPRODUCE the problem with a measurement (p99, not "feels slow")
2. PROFILE during that state (not on an idle system)
3. FIND the widest self-time frame in your code
4. FIX one thing
5. RE-MEASURE the same metric
```

**Profiling an idle system finds nothing.** And "the CPU is at 80%" is not a finding — a
saturated system doing useful work is fine; the question is which work.

---

## 6. The CI Pipeline

### 6.1 The staged pipeline

```
┌──────────┐  ┌──────────┐  ┌────────────┐  ┌───────────┐  ┌──────────┐  ┌────────┐
│ compile  │─►│ unit     │─►│ integration│─►│ quality   │─►│ build    │─►│ deploy │
│ + static │  │ *Test    │  │ *IT        │  │ gates     │  │ image    │  │ staging│
│ ~1 min   │  │ ~2 min   │  │ ~5 min     │  │ ~2 min    │  │ ~2 min   │  │        │
└──────────┘  └──────────┘  └────────────┘  └───────────┘  └──────────┘  └────────┘
   every         every          every           every         main        main
   push          push           PR              PR            only        only
```

**Rules that make it work:**

- **Fail fast.** Compile and unit tests first — a broken build shouldn't spend five minutes starting
  containers.
- **Parallelise independent jobs.** Unit and static analysis can run together.
- **Cache dependencies and Docker images.** Maven/Gradle cache + Testcontainers image cache.
- **One pipeline, one artifact.** Build the image once and promote it through environments; don't
  rebuild per environment (`dev` ≠ `prod` binaries is a classic incident).
- **Fail the build, not the reviewer.** Style, formatting and architecture rules should be automated
  (Spotless, Checkstyle, ArchUnit), not code-review comments.

### 6.2 Timing targets

| Stage | Target | If slower |
|---|---|---|
| compile + static | < 2 min | check annotation processors |
| unit tests | < 3 min | parallelise, remove Spring from unit tests |
| integration (`*IT`) | < 10 min | share containers, reduce context count |
| total PR feedback | **< 15 min** | beyond that, developers context-switch and stop waiting |

**The 15-minute rule:** if PR feedback takes longer, engineers start batching changes and the
pipeline's value drops. Split the suite rather than accepting a 40-minute build.

### 6.3 Caching

```yaml
- uses: actions/cache@v4
  with:
    path: |
      ~/.m2/repository
      ~/.gradle/caches
    key: ${{ runner.os }}-deps-${{ hashFiles('**/pom.xml') }}
    restore-keys: ${{ runner.os }}-deps-
```

```yaml
# Testcontainers: pre-pull common images so tests don't pay the download
- run: docker pull postgres:16-alpine && docker pull redis:7-alpine
```

---

## 7. Quality Gates

### 7.1 Gates that are worth having

| Gate | Threshold | Rationale |
|---|---|---|
| Compile + tests pass | 100% | non-negotiable |
| Line coverage floor | 60–70% (must not decrease) | prevents decay |
| Branch coverage on core packages | 85% | where money lives |
| Mutation score on core packages | 60–70% | real assertion quality |
| No new critical/high CVE | `dependency-check` / Trivy | supply chain |
| ArchUnit rules | 100% | keeps the architecture honest |
| Performance smoke | p95 < SLA on a small load test | catches order-of-magnitude regressions |
| API contract check | no breaking change | consumers keep working |

### 7.2 Gates that are not worth having

| Gate | Why not |
|---|---|
| Global 90% coverage | produces assertion-free tests |
| Zero warnings | noise; suppress deliberately instead |
| 100% mutation score | diminishing returns, very slow |
| Lint on every rule | style debates automated into failures |
| Load test on every PR | too slow and too noisy; schedule it |

### 7.3 The failure budget

```
1 test flaked twice in a week  → quarantine (tagged, excluded, with an owner and a ticket)
quarantined > 1 week           → either fix it or delete it; do not let it rot
failures in main               → fix before new features; a red main is a team emergency
```

**A permanently red or permanently ignored pipeline is worse than no pipeline** — it trains the
team to skip reading the output.

---

## 8. Flaky Test Management

### 8.1 Detection

```xml
<!-- rerun only failures, once, to distinguish flake from real failure -->
<plugin>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <rerunFailingTestsCount>1</rerunFailingTestsCount>
  </configuration>
</plugin>
```

But **track the reruns** — a rising rerun count is a leading indicator of a flaky suite.

Better: record every test outcome in CI over time and compute a per-test flake rate. Tests above
~1% get quarantined automatically.

### 8.2 The policy

```
flake detected → tag @Flaky + exclude from the blocking suite
              → open a ticket with an owner and a 5-day deadline
              → fix the root cause (usually: time, ordering, sleep, shared state)
              → if it can't be fixed, delete it and replace it with a deterministic test
```

**Never** "just re-run it." That's how a suite becomes decoration.

### 8.3 The usual root causes (again, because they're always the same)

| Cause | Fix |
|---|---|
| `Thread.sleep` | Awaitility |
| `Instant.now()` | inject `Clock` |
| Unordered collection assertions | sort, or assert as a set |
| Shared DB rows between tests | `TRUNCATE` per test |
| Test order dependency | fresh fixtures, no static state |
| Real network | WireMock / Testcontainers |
| Timezone | UTC everywhere |
| Parallel port collision | random ports |
| Async without waiting for completion | latch / Awaitility / `@TransactionalEventListener` |

---

## 9. Test Data Management

### 9.1 The three approaches

| Approach | Speed | Fidelity | Use |
|---|---|---|---|
| **Builders/factories in code** | fast | high (explicit) | unit and integration |
| **SQL fixtures** | fast | medium | integration with complex graphs |
| **Anonymised production dump** | slow, heavy | highest (real distributions) | performance tests, staging |

```java
public final class OrderBuilder {
    private Customer customer = CustomerBuilder.aCustomer().build();
    private OrderStatus status = OrderStatus.PENDING;
    private List<OrderLine> lines = List.of(OrderLineBuilder.aLine().build());

    public static OrderBuilder anOrder() { return new OrderBuilder(); }
    public OrderBuilder withStatus(OrderStatus s) { this.status = s; return this; }
    public OrderBuilder withLines(OrderLine... l) { this.lines = List.of(l); return this; }
    public Order build() { ... }
}
```

**Builders give readable tests:**
`anOrder().withStatus(SHIPPED).withLines(aLine().withSku("SKU-1")).build()`

### 9.2 Never use production data directly

- **PII** — GDPR/CCPA violation, and a real breach risk in CI logs.
- **Volume** — a 500 GB dump doesn't fit in a test pipeline.
- **Anonymise** if you need distributions: hash emails, shuffle names, perturb amounts.

### 9.3 Performance test data volume

For a load test, data volume must match production, or the query plans lie:

```
❌ 1,000 orders     → index scan, 2 ms
✅ 10,000,000 orders → the plan you'll actually get
```

Seed the load-test DB with production-shaped volumes and run `ANALYZE` before the test — otherwise
you're testing a different database.

---

## 10. Code Examples

### 10.1 Maven: separate fast and slow suites

```xml
<plugin>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <excludedGroups>integration,slow</excludedGroups>
  </configuration>
</plugin>

<plugin>
  <artifactId>maven-failsafe-plugin</artifactId>
  <configuration>
    <includes><include>**/*IT.java</include></includes>
  </configuration>
  <executions>
    <execution>
      <goals><goal>integration-test</goal><goal>verify</goal></goals>
    </execution>
  </executions>
</plugin>
```

```bash
mvn -q test              # ~2 min: unit only
mvn -q verify            # ~10 min: unit + integration
mvn -q verify -Pload     # + load tests against staging
```

### 10.2 JaCoCo with per-package rules

```xml
<plugin>
  <artifactId>jacoco-maven-plugin</artifactId>
  <executions>
    <execution><goals><goal>prepare-agent</goal></goals></execution>
    <execution>
      <id>report</id>
      <phase>verify</phase>
      <goals><goal>report</goal><goal>check</goal></goals>
      <configuration>
        <rules>
          <rule>
            <element>BUNDLE</element>
            <limits>
              <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.60</minimum></limit>
            </limits>
          </rule>
          <rule>
            <element>PACKAGE</element>
            <includes><include>com.acme.pricing</include></includes>
            <limits>
              <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.85</minimum></limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### 10.3 Mutation testing in CI (targeted)

```xml
<plugin>
  <groupId>org.pitest</groupId>
  <artifactId>pitest-maven</artifactId>
  <configuration>
    <targetClasses><param>com.acme.pricing.*</param><param>com.acme.payments.*</param></targetClasses>
    <targetTests><param>com.acme.*Test</param></targetTests>
    <mutationThreshold>60</mutationThreshold>
    <outputFormats><param>HTML</param></outputFormats>
  </configuration>
</plugin>
```

Run it nightly, not per-PR — it's too slow for the 15-minute budget.

### 10.4 A GitHub Actions pipeline

```yaml
name: ci
on: [push, pull_request]

jobs:
  fast:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - run: mvn -B -q compile spotless:check
      - run: mvn -B -q test

  integration:
    runs-on: ubuntu-latest
    needs: fast
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - run: docker pull postgres:16-alpine
      - run: mvn -B -q verify
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: reports, path: 'target/surefire-reports,target/failsafe-reports' }

  security:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      # dependency CVEs
      - run: mvn -B -q org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7
      # image scan
      - run: |
          docker build -t app:${{ github.sha }} .
          trivy image --severity HIGH,CRITICAL --exit-code 1 app:${{ github.sha }}

  load:
    if: github.event_name == 'schedule'
    runs-on: ubuntu-latest
    needs: integration
    steps:
      - uses: actions/checkout@v4
      - run: docker run --rm -i grafana/k6 run - < load/checkout.js
        env: { BASE_URL: ${{ secrets.STAGING_URL }} }
```

### 10.5 A performance smoke test in the PR (fast, tiny)

```javascript
// 30 seconds, 50 VUs — catches order-of-magnitude regressions, not SLA breaches
export const options = {
  vus: 50, duration: '30s',
  thresholds: { http_req_duration: ['p(95)<1500'] },   // deliberately loose
};
```

**A loose threshold that runs in 30 seconds beats a rigorous one that never runs.** The rigorous
load test goes to the nightly/staging job.

---

## 11. Common Mistakes

| Mistake | Consequence | Fix |
|---|---|---|
| Reporting averages | hides the tail | p50/p95/p99 |
| Load test with 1,000 rows of data | wrong query plans, fake results | production-shaped volumes + ANALYZE |
| No think time | you tested a DDoS, not a workload | pauses per virtual user |
| Generator is the bottleneck | numbers measure your laptop | monitor generator CPU |
| No metrics during the run | a number without a cause | capture CPU/GC/pool/locks |
| Testing on a cold JVM | first run is misleading | warm up, then measure |
| Ignoring connection pool size | 190 users waiting | check pool saturation first |
| Load test on every PR | slow, noisy, ignored | nightly / pre-release |
| Flaky tests re-run forever | team ignores red | quarantine + owner + deadline |
| Rebuilding per environment | dev ≠ prod binary | build once, promote |
| Coverage gate on the whole repo | assertion-free tests | per-package rules |
| Micro-optimising with JMH when the DB is the bottleneck | wasted effort | profile end-to-end first |
| Skipping soak tests | memory leaks ship | run 2–4 h soak before major releases |

---

## 12. Interview Questions & Model Answers

**Q1. How would you verify the service handles 10x current traffic?**
> A load test at 10x with a realistic traffic mix, think time, production-shaped data, and full
> observability during the run — application CPU and GC, thread pools, DB connection pool,
> slow queries, cache hit rate. Then I'd find the first resource to saturate and fix that, and
> repeat. Then a stress test past 10x to see the failure mode, because I also want to know whether
> it degrades gracefully or collapses, and whether it recovers.

**Q2. Why percentiles and not averages?**
> Averages hide the tail, and the tail is what users experience. Two systems can both average 34 ms
> — one with a p99 of 34, one with a p99 of 200 because a few requests take seconds. SLAs are
> written on percentiles, and the tail usually comes from a specific cause: GC, a lock, a cold
> cache, or a slow dependency. Reporting p50/p95/p99 gives you a shape you can diagnose.

**Q3. What's the most common performance bug you find under load?**
> Connection pool saturation. The app looks fine on CPU, but 200 concurrent requests are queueing
> for a pool of 10 connections, so p99 is dominated by waiting. The fix is to size the pool against
> the database's real capacity — usually a small multiple of cores — and make sure nothing holds a
> connection across a slow call. After that, it's usually a missing index or an N+1.

**Q4. How do you find where the time goes in a slow request?**
> Trace it first — a distributed trace shows which span is slow, whether it's the DB, a remote
> call, or CPU. Then for a CPU-heavy span, a flame graph from async-profiler or JFR. For a DB span,
> `EXPLAIN ANALYZE`. I always measure during the actual degraded state, not on an idle system, and I
> change one thing at a time.

**Q5. When is JMH worth it?**
> When I'm choosing between implementations of a genuinely hot path and the difference could matter
> at my QPS — a serializer, a parser, a hashing scheme. Not for general service tuning, because a
> microbenchmark win can be invisible end-to-end once you add cache misses, allocation pressure,
> contention and GC. And I'd still confirm the win with an end-to-end measurement.

**Q6. How long should a CI pipeline take?**
> PR feedback under about 15 minutes, or developers stop waiting and batch changes. I get there with
> a staged pipeline: compile and static analysis in under two minutes, unit tests in a couple more,
> integration tests up to ten, all with dependency and image caching. Anything slower — load tests,
> mutation testing — goes on a nightly schedule instead of blocking the PR.

**Q7. How do you handle flaky tests?**
> Treat them as bugs with owners. Detect them from CI history rather than by complaint; when the
> flake rate crosses about 1%, quarantine the test out of the blocking suite and open a ticket with
> a deadline. The root cause is almost always time, ordering, sleep, or shared state. If it can't be
> fixed, delete it and replace it with a deterministic test — a test the team ignores is worse than
> no test.

**Q8. What quality gates do you actually enforce?**
> Tests and compile must pass, coverage can't decrease, higher branch coverage on money-critical
> packages, ArchUnit rules, and no new high/critical CVEs. I'd run mutation testing nightly on the
> core packages rather than per-PR because it's slow. I avoid global coverage targets because they
> produce tests that execute code without asserting anything.

**Q9. How do you test for memory leaks?**
> A soak test — steady load for several hours while watching heap after GC, thread count, open file
> descriptors and connection counts. A leak shows as a monotonic rise that never returns to
> baseline. I'd also take heap dumps at intervals and diff the dominators, which usually names the
> culprit: an unbounded cache, a listener registry, a ThreadLocal, or an ever-growing collection.

---

## 13. Self-Check & Revision Card

- [ ] I can name the four test types and what each catches.
- [ ] I can explain why averages lie and quote p50/p95/p99.
- [ ] I can describe find-the-knee capacity testing.
- [ ] I can list the metrics to capture during a load run.
- [ ] I know the most common bottleneck (connection pool) and why.
- [ ] I can explain when JMH is and isn't worth it.
- [ ] I can profile CPU, allocation and locks.
- [ ] I can design a staged CI pipeline with a 15-minute PR budget.
- [ ] I can name the gates worth having, and the ones that aren't.
- [ ] I can describe a flaky-test policy with owners and deadlines.

```
┌──────────── PERFORMANCE & CI — 60s RECALL ───────────────────────────────────┐
│ LOAD (SLA) · STRESS (failure mode) · SOAK (leaks!) · SPIKE (autoscale) ·     │
│   CAPACITY (find the knee).                                                  │
│                                                                              │
│ PERCENTILES NOT AVERAGES. Same mean, p99 of 34 vs 2000.                      │
│ Latency vs throughput has a KNEE at ~60–75% — operate below it.              │
│                                                                              │
│ REALISTIC LOAD: traffic mix · think time · production data volume + ANALYZE · │
│   real auth · warm JVM. Measure app CPU/GC/pools + DB pool/locks + cache.    │
│ #1 FINDING: connection pool saturation, not CPU.                             │
│                                                                              │
│ JMH only for hot-path A/B. Microbenchmarks don't transfer end-to-end.        │
│ PROFILE during degradation: JFR/async-profiler flame graph → YOUR widest frame│
│                                                                              │
│ CI: compile+unit <5 min · IT <10 min · PR feedback <15 MIN.                 │
│ Build ONCE, promote the artifact. Nightly: load + mutation testing.          │
│                                                                              │
│ FLAKY = bug with an owner. Quarantine >1% flake, fix or delete in a week.    │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [03 · Testcontainers & Contract Testing](./03-Testcontainers-and-Contract-Testing.md) · **Up:** [README](./README.md)
