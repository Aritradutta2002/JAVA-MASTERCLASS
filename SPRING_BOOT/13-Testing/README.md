# 13 — Testing

> **Level:** Intermediate → Expert
> **Interview Frequency:** ★★★★☆ (asked in every senior loop, usually as "how do you test this?")
> **Prerequisite:** [12 · System Design](../12-System-Design-for-Java-Backend/README.md)
> **Last Updated:** 2026

---

## 🎯 Why This Topic Is Separate

Almost every backend engineer has written tests. Far fewer can answer these three:

1. **"What would you test here, and what would you deliberately not?"** — risk-based testing, not
   coverage chasing.
2. **"Why is your integration layer thin?"** — because every mock is an assumption, and the bugs
   live in the wiring.
3. **"How do you test a service that spans three services and a Kafka topic?"** — contract tests,
   Testcontainers, and the honesty to admit what an E2E test can't cover.

The signal interviewers want is **taste**: knowing which test buys the most confidence per minute,
and which test is decoration.

---

## 📚 Chapters

| # | Chapter | Core Idea | Difficulty |
|---|---------|-----------|------------|
| 01 | [Testing Strategy & the Test Pyramid](./01-Testing-Strategy-and-the-Test-Pyramid.md) | Risk-based testing, the pyramid vs the trophy, mocks vs fakes, determinism, honest coverage | ⭐⭐⭐ |
| 02 | [Unit & Slice Testing](./02-Unit-and-Slice-Testing.md) | JUnit 5, AssertJ, Mockito, `@WebMvcTest`/`@DataJpaTest`, context caching, security tests | ⭐⭐⭐ |
| 03 | [Testcontainers & Contract Testing](./03-Testcontainers-and-Contract-Testing.md) | Real Postgres/Kafka, WireMock failure paths, Pact, event schema compatibility | ⭐⭐⭐⭐ |
| 04 | [Performance, Load & CI Pipelines](./04-Performance-Load-and-CI-Pipelines.md) | Load/stress/soak/spike, percentiles, JMH, profiling, staged CI, flaky-test policy | ⭐⭐⭐⭐ |

---

## 🧠 The One Mental Model That Covers Everything

```text
                 TESTS ARE A RISK-MANAGEMENT INSTRUMENT.

   ┌──────────────────────────────────────────────────────────────────┐
   │  Where do bugs happen?      Where are they expensive?            │
   │        │                            │                            │
   │        └──────────┬─────────────────┘                            │
   │                   ▼                                              │
   │            TEST THERE.                                           │
   │                                                                  │
   │  Business rules · money · state machines · boundaries ·          │
   │  authorisation · idempotency · concurrency · CONTRACTS           │
   └──────────────────────────────────────────────────────────────────┘

   And the corollary that changes how you write tests:

        EVERY MOCK IS A LIE ABOUT HOW THE REAL THING BEHAVES.
        → the wiring (SQL, JSON, transactions, security) needs real tests.
```

---

## 📊 The Decision Matrix

| Decision | Default | Alternative | Trade-off |
|---|---|---|---|
| **Test layer** | unit for logic | slice/integration for wiring | speed vs confidence |
| **Doubles** | fakes (assert state) | mocks (assert interactions) | robustness vs precision |
| **Database in tests** | Testcontainers real DB | H2 | fidelity vs speed |
| **Containers** | static, shared, `@ServiceConnection` | per-class | speed vs isolation |
| **Spring context** | one shared configuration | per-test properties | test time |
| **Cross-service** | contract tests | full E2E | speed/robustness vs end-to-end proof |
| **Third party** | WireMock for HTTP behaviour | Mockito on your interface | realism vs speed |
| **Coverage** | floor + per-package branch rules | global target | signal vs gaming |
| **Mutation testing** | nightly on core packages | per PR | depth vs build time |
| **Load tests** | nightly / pre-release | per PR | rigour vs feedback time |
| **Flaky tests** | quarantine + owner + deadline | rerun | honesty vs convenience |

---

## ⚠️ The Traps

| Trap | Wrong answer | Right answer |
|---|---|---|
| "85% coverage means good tests" | "Yes" | Coverage measures **execution**, not **verification** |
| "Mock the repository and assert it's called" | "Standard practice" | Then the SQL is untested — the bug lives in the wiring |
| "Test repositories with H2" | "It's faster" | H2 differs on `jsonb`, `timestamptz`, `ON CONFLICT`, constraints, plans |
| "One `@SpringBootTest` per test class" | "It's thorough" | Each different mock/property set = a **new context** = minutes |
| "`@Transactional` test proves rollback" | "Yes" | It never commits — `afterCommit` behaviour is untested |
| "`Thread.sleep` then assert" | "Works locally" | Flaky in both directions — use Awaitility |
| "`Instant.now()` in production code" | "It's simple" | Untestable expiry — inject a `Clock` |
| "`assertEquals` on `BigDecimal`" | "Numbers are equal" | Scale differs — `isEqualByComparingTo` |
| "Flaky test? Re-run it" | "Green now" | Every re-run teaches the team to ignore red |
| "E2E tests cover cross-service contracts" | "They're the real test" | Slow, flaky, skipped — use **contract tests** |
| "Load test with averages" | "Mean latency is fine" | p99 is what users feel and SLAs are written on |
| "Load test on 1,000 rows" | "It's the same query" | The plan is different at 10M rows |
| "Profile the idle system" | "Nothing obvious" | Profile **during** the degradation |
| "Test private methods" | "For coverage" | Test public behaviour; private tests break on refactor |
| "Skip the soak test" | "It's slow" | Leaks only appear after hours |

---

## 🎤 The 20 Questions to Expect

| # | Question | Where |
|---|---|---|
| 1 | How do you decide what to test? | [Ch 01](./01-Testing-Strategy-and-the-Test-Pyramid.md) |
| 2 | Explain the test pyramid | [Ch 01](./01-Testing-Strategy-and-the-Test-Pyramid.md) |
| 3 | Is 100% coverage a good goal? | [Ch 01](./01-Testing-Strategy-and-the-Test-Pyramid.md) |
| 4 | Mock or fake — and why? | [Ch 01](./01-Testing-Strategy-and-the-Test-Pyramid.md) |
| 5 | How do you prevent flaky tests? | [Ch 01](./01-Testing-Strategy-and-the-Test-Pyramid.md), [Ch 04](./04-Performance-Load-and-CI-Pipelines.md) |
| 6 | How do you test a REST controller? | [Ch 02](./02-Unit-and-Slice-Testing.md) |
| 7 | How do you test a repository? | [Ch 02](./02-Unit-and-Slice-Testing.md), [Ch 03](./03-Testcontainers-and-Contract-Testing.md) |
| 8 | Why not H2? | [Ch 03](./03-Testcontainers-and-Contract-Testing.md) |
| 9 | How do you keep a Spring test suite fast? | [Ch 02](./02-Unit-and-Slice-Testing.md) |
| 10 | Why is `@Transactional` on a test sometimes wrong? | [Ch 02](./02-Unit-and-Slice-Testing.md) |
| 11 | How do you test security rules? | [Ch 02](./02-Unit-and-Slice-Testing.md) |
| 12 | How do you test a Kafka consumer? | [Ch 03](./03-Testcontainers-and-Contract-Testing.md) |
| 13 | WireMock or Mockito? | [Ch 03](./03-Testcontainers-and-Contract-Testing.md) |
| 14 | How do you test a circuit breaker? | [Ch 03](./03-Testcontainers-and-Contract-Testing.md) |
| 15 | What is contract testing and what does it replace? | [Ch 03](./03-Testcontainers-and-Contract-Testing.md) |
| 16 | How do you test event schema compatibility? | [Ch 03](./03-Testcontainers-and-Contract-Testing.md) |
| 17 | How do you verify it handles 10x traffic? | [Ch 04](./04-Performance-Load-and-CI-Pipelines.md) |
| 18 | Why percentiles, not averages? | [Ch 04](./04-Performance-Load-and-CI-Pipelines.md) |
| 19 | How do you find where a slow request spends its time? | [Ch 04](./04-Performance-Load-and-CI-Pipelines.md) |
| 20 | How long should CI take, and what gates do you keep? | [Ch 04](./04-Performance-Load-and-CI-Pipelines.md) |

---

## 🛠️ The Code You'll Actually Write

| Tool / Annotation | Purpose | Where |
|---|---|---|
| `@ParameterizedTest` + `@CsvSource` | boundary coverage | [Ch 01](./01-Testing-Strategy-and-the-Test-Pyramid.md) |
| `assertThat(...).isEqualByComparingTo(...)` | `BigDecimal` comparisons | [Ch 02](./02-Unit-and-Slice-Testing.md) |
| `@WebMvcTest` + MockMvc + `@MockitoBean` | controller slices | [Ch 02](./02-Unit-and-Slice-Testing.md) |
| `@DataJpaTest` + `Replace.NONE` | repositories on the real DB | [Ch 02](./02-Unit-and-Slice-Testing.md) |
| `@WithMockUser`, `with(user(...))` | security tests | [Ch 02](./02-Unit-and-Slice-Testing.md) |
| `@Testcontainers` + `@ServiceConnection` | real Postgres/Kafka/Redis | [Ch 03](./03-Testcontainers-and-Contract-Testing.md) |
| `KafkaTestUtils` | producer/consumer assertions | [Ch 03](./03-Testcontainers-and-Contract-Testing.md) |
| WireMock scenarios | retries, breaker, timeouts | [Ch 03](./03-Testcontainers-and-Contract-Testing.md) |
| Pact consumer/provider tests | cross-service contracts | [Ch 03](./03-Testcontainers-and-Contract-Testing.md) |
| Awaitility | async assertions without sleeps | [Ch 01](./01-Testing-Strategy-and-the-Test-Pyramid.md), [Ch 03](./03-Testcontainers-and-Contract-Testing.md) |
| ArchUnit rules | architecture tests | [Ch 01](./01-Testing-Strategy-and-the-Test-Pyramid.md) |
| JaCoCo per-package rules, PIT | coverage and mutation gates | [Ch 04](./04-Performance-Load-and-CI-Pipelines.md) |
| k6 / Gatling with thresholds | load tests that fail the build | [Ch 04](./04-Performance-Load-and-CI-Pipelines.md) |
| async-profiler / JFR | finding hot spots | [Ch 04](./04-Performance-Load-and-CI-Pipelines.md) |

---

## 🔗 The Setup (the minimum)

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-testcontainers</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.awaitility</groupId>
  <artifactId>awaitility</artifactId>
  <scope>test</scope>
</dependency>
```

```java
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class IntegrationTestBase {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE order_items, orders, customers RESTART IDENTITY CASCADE");
    }
}
```

---

## 📐 The Reference Pipeline

```text
   push ──► ┌─────────┐   ┌──────────┐   ┌──────────────┐   ┌──────────┐
            │ compile │──►│ unit     │──►│ integration  │──►│ security │
            │ + static│   │ *Test    │   │ *IT (TC)     │   │ scan     │
            │  ~1 min │   │  ~2 min  │   │  ~5–10 min   │   │  ~2 min  │
            └─────────┘   └──────────┘   └──────────────┘   └────┬─────┘
                                                                 ▼
                                                          ┌──────────────┐
   nightly ─────────────────────────────────────────────► │ load test    │
                                                          │ mutation     │
                                                          │ dependency   │
                                                          └──────────────┘

   Budget: PR feedback < 15 minutes, or developers stop waiting.
```

**Rules the diagram encodes:**
- Fast checks first — a broken build shouldn't wait for containers.
- Dependency and image caching, or every build pays the download.
- **Build the artifact once**, promote it through environments.
- Load and mutation testing are **scheduled**, not per-commit.

---

## ✅ The Self-Check

- [ ] I can state what a test suite is for, and what it isn't.
- [ ] I can explain the pyramid and the "trophy" correction.
- [ ] I can list what to test and what to deliberately skip.
- [ ] I can choose between fakes, mocks, and real infrastructure.
- [ ] I can write a controller test including the negative cases.
- [ ] I can explain why H2 is the wrong choice for repositories.
- [ ] I can explain Spring context caching and how to exploit it.
- [ ] I know when `@Transactional` on a test hides a bug.
- [ ] I can test Kafka, Redis and a resilient HTTP client.
- [ ] I can explain contract testing and what it doesn't replace.
- [ ] I can design a load test that produces a decision.
- [ ] I can profile to find where time actually goes.
- [ ] I can describe a CI pipeline with a 15-minute PR budget.
- [ ] I have a flaky-test policy with owners and deadlines.

---

## 🧪 The Practice Drill

- [ ] Write a `@WebMvcTest` for a controller with 200, 400, 401, 403, 404 cases.
- [ ] Write a `@DataJpaTest` with Testcontainers that proves a constraint rejects duplicates.
- [ ] Break a query on purpose (rename a field) and prove a unit test still passes while the
      integration test fails — the demonstration of why slices matter.
- [ ] Add an `afterCommit` listener and show that a `@Transactional` test can't see it.
- [ ] Make 10 test classes share one context; measure the suite time before and after.
- [ ] Write a WireMock test that proves a circuit breaker stops calling a failing dependency.
- [ ] Write a Pact consumer test and a provider verification for one endpoint.
- [ ] Run a 30-second k6 smoke test in CI with a loose threshold.
- [ ] Run a 2-hour soak test and watch heap-after-GC for a monotonic rise.

---

**Related folders:** ← [12 · System Design](../12-System-Design-for-Java-Backend/README.md) · → [14 · Deployment & DevOps](../14-Deployment-DevOps/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
