# 01 · Testing Strategy & the Test Pyramid

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★☆ ("How do you decide what to test?")
> **Prerequisite:** [12 · System Design](../12-System-Design-for-Java-Backend/README.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — Tests Buy Confidence](#2-mental-model--tests-buy-confidence)
- [3. The Pyramid, Honestly](#3-the-pyramid-honestly)
- [4. What to Test (and What Not To)](#4-what-to-test-and-what-not-to)
- [5. The Test Doubles Spectrum](#5-the-test-doubles-spectrum)
- [6. Test Naming & Structure](#6-test-naming--structure)
- [7. Determinism](#7-determinism)
- [8. Coverage — the Honest Version](#8-coverage--the-honest-version)
- [9. What Tests to Write for a Feature](#9-what-tests-to-write-for-a-feature)
- [10. Code Examples](#10-code-examples)
- [11. Common Mistakes](#11-common-mistakes)
- [12. Interview Questions & Model Answers](#12-interview-questions--model-answers)
- [13. Self-Check & Revision Card](#13-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> A test suite is a **risk-management instrument**, not a coverage number. You write tests where
> a bug is **expensive** and **likely**: business rules, money, state transitions, boundaries,
> and integrations whose contracts you don't own.
>
> The pyramid shape is about **feedback speed**: many fast unit tests, fewer integration tests,
> very few end-to-end tests. The modern correction is that the **integration layer is often the
> most valuable** — a unit test with everything mocked can pass while the app is broken, because
> the bug lives in the wiring (serialization, SQL, transactions, security).
>
> **Test behaviour, not implementation.** A test that breaks when you rename a private method
> costs maintenance and catches nothing. A test that asserts "placing an order twice charges
> once" catches a real bug and survives every refactor.
>
> **Flaky tests are worse than no tests** — they train the team to ignore red. Determinism > 
> thoroughness.

---

## 1. What & Why

### 1.1 The economics

```
cost of a bug found...
  in your editor     ~0
  by a unit test     ~1 min
  by CI              ~10 min + a context switch
  in staging         ~1 hour + a release delay
  in production      ~1 day + incident + trust
```

The point of a test suite is to **shift detection left**, and to make refactoring safe — which
is really the same thing: you can only change code quickly if you can prove you didn't break it.

**The two questions a suite answers:**

1. *Does this still work?* (regression protection)
2. *What was this supposed to do?* (executable specification)

Both are valuable. Neither is "we have 85% coverage".

### 1.2 What tests are for — and not for

| Tests are for | Tests are not for |
|---|---|
| encoding business rules | proving correctness formally |
| catching regressions on refactor | replacing code review |
| documenting intent | replacing monitoring in production |
| enabling change | achieving a coverage number |
| pinning down bugs (reproduce → fix → keep) | testing the framework |

**The line to remember:** *a test that has never caught a bug is either new or worthless.*
You don't need to delete it — but you should know which tests have earned their place.

---

## 2. Mental Model — Tests Buy Confidence

```
        CONFIDENCE
            ▲
            │                          ┌──────────────┐
            │                          │  E2E         │  high confidence
            │                          │  few, slow   │  high cost, flaky
            │              ┌───────────┴──────────────┘
            │              │  INTEGRATION              │  most value per test
            │              │  real DB / real HTTP      │  medium cost
            │  ┌───────────┴──────────────────────────┘
            │  │  UNIT                                │  fast, precise
            │  │  pure logic, no I/O                  │  cheap, low confidence alone
            │  └──────────────────────────────────────┘
            └────────────────────────────────────────────────────► COST / TIME
```

**The insight that changes how you write tests:**

> *Every mock is a lie you're telling yourself about how the real thing behaves.*

A unit test with a mocked repository proves your logic works **given** the repository behaves as
mocked. If the actual bug is a wrong JPQL query, a missing `@Transactional`, a Jackson
serialisation issue, or a Spring Security rule, **no amount of unit tests will find it.**

That's why the integration layer carries disproportionate value in a Spring app — it's where
Spring's wiring actually gets exercised.

---

## 3. The Pyramid, Honestly

### 3.1 The classic pyramid

```
        /\        E2E / UI           5%    minutes
       /  \
      /────\      Integration       15%   seconds
     /      \
    /────────\    Unit              80%   milliseconds
```

**Why the shape:** speed, determinism, and failure localisation. A failing unit test tells you
the method; a failing E2E test tells you "something in the stack".

### 3.2 The "testing trophy" correction (Kent C. Dodds)

```
        ___
       /   \      E2E              small
      /─────\
     /       \    INTEGRATION      ← the biggest layer
    /─────────\
   /           \  UNIT             some
  /─────────────\
        STATIC TYPING             (compiler + linters)
```

The argument: integration tests give the **best confidence per unit of cost**, because they test
the wiring where most real bugs live. In a Spring app, that maps to:

- `@WebMvcTest` for controllers (real MVC, mocked service)
- `@DataJpaTest` + Testcontainers for repositories (real SQL)
- `@SpringBootTest` + Testcontainers for full slices
- Plain JUnit for domain logic

### 3.3 For a Spring Boot backend, the practical shape

```
┌──────────────────────────────────────────────────────────────┐
│  E2E (few)            full app + real DB + real broker       │  1–5%
│  full-slice (@SpringBootTest + Testcontainers)               │ 10–20%
│  slice (@WebMvcTest, @DataJpaTest)                           │ 30–40%
│  unit (domain logic, pure functions)                         │ 40–50%
│  static (compiler, SpotBugs, ArchUnit)                       │ free
└──────────────────────────────────────────────────────────────┘
```

**The rule:** the layer you're least sure about should have the most tests. If your risk is "the
SQL is wrong," your `@DataJpaTest` layer should be thick. If it's "the discount calculation is
subtle," unit tests should be thick.

### 3.4 The anti-pattern: the ice-cream cone

```
    /────────────\   manual testing      ← most effort here
   /              \
  /────────────────\  E2E
 /──────────────────\ unit              ← least effort here
```

Slow, flaky, and it finds bugs late. If your team has a QA person clicking through a checklist
for every release and few automated tests, you have an ice-cream cone.

---

## 4. What to Test (and What Not To)

### 4.1 Test these

| Category | Example | Why |
|---|---|---|
| **Business rules** | discount tiers, tax rounding, eligibility | expensive, subtle, changes often |
| **State machines** | order status transitions, saga steps | illegal transitions are real bugs |
| **Boundaries** | 0, 1, max, empty, null, off-by-one | where bugs concentrate |
| **Money** | rounding, currency, refund totals | highest cost of error |
| **Authorisation** | who can see/change what | security bugs, and they regress silently |
| **Idempotency** | same event twice → one effect | distributed systems |
| **Concurrency** | two writers, lost update | highest-cost bugs |
| **Contracts** | SQL, JSON shape, error codes | the wiring where unit tests lie |
| **Bug reproductions** | a failing test that pins the fix | prevents recurrence |

### 4.2 Don't test these

| Don't test | Why |
|---|---|
| Getters/setters, `toString` | zero risk, high noise |
| The framework | Spring's routing works; don't prove it |
| Third-party libraries | their tests exist |
| Private methods | test through the public behaviour |
| Implementation details (call counts on internals) | brittle, breaks on refactor |
| Trivial delegation (`service` calls `repo`) | no logic to break |
| Exact log strings | brittle and low value |

### 4.3 The "what would break?" heuristic

For any line of code, ask: **"if this were wrong, what would the user see?"** If the answer is
"nothing noticeable," it probably doesn't need a dedicated test. If it's "we refund the wrong
amount," it needs three.

---

## 5. The Test Doubles Spectrum

| Double | What it does | When |
|---|---|---|
| **Dummy** | passed but never used | filling a parameter |
| **Stub** | returns canned answers | control an input |
| **Spy** | real object + recorded calls | partial mocking (avoid) |
| **Mock** | pre-programmed expectations, verifies calls | verify an interaction |
| **Fake** | working simplified implementation (in-memory repo, H2) | replace infrastructure cheaply |

### 5.1 The mock-vs-fake distinction matters

```java
// MOCK: verifies the interaction — "did you call save()?"
verify(repo).save(any(Order.class));

// FAKE: actually stores it — the test can then read it back
var fakeRepo = new InMemoryOrderRepository();
service.place(order);
assertThat(fakeRepo.findById(order.getId())).isPresent();
```

**Fakes are usually better** because they let you assert on *state* rather than *interactions*,
and state assertions survive refactors. A mock test breaks when you change *how* you call the
repository even though the behaviour is identical.

### 5.2 When mocking is right

- The dependency is **slow** (network, third-party API).
- The dependency is **non-deterministic** (clock, random, UUID).
- The dependency is **impossible to instantiate** in a test (hardware).
- You need to **simulate a failure** the real thing can't produce on demand.

### 5.3 When mocking is wrong

- You're mocking a **type you own** — you're then testing the mock, not the code.
- The behaviour under test **lives in the interaction** (a SQL query, a transaction boundary, a
  serialisation format).
- You're mocking so much that the test is a mirror of the implementation.

**The smell:** a test with five mocks and forty lines of `when(...)` is usually testing a design
problem, not validating behaviour.

---

## 6. Test Naming & Structure

### 6.1 Names that explain failures

```java
// ❌ tells you nothing when it fails at 3am
@Test void testPlaceOrder() { ... }

// ✅ tells you the rule and the condition
@Test void placeOrder_whenStockInsufficient_throwsOutOfStock() { ... }
@Test void placeOrder_whenCouponExpired_appliesNoDiscount() { ... }
```

Format: `methodOrBehaviour_whenCondition_expectedOutcome`.

Or the BDD style:

```java
@Test void should_not_charge_twice_when_the_same_event_is_redelivered() { ... }
```

Either is fine; **consistency** matters more than the format.

### 6.2 Arrange-Act-Assert

```java
@Test
void withdraw_whenBalanceInsufficient_throwsInsufficientFunds() {
    // arrange
    var account = Account.withBalance(Money.of("10.00"));

    // act + assert
    assertThatThrownBy(() -> account.withdraw(Money.of("11.00")))
        .isInstanceOf(InsufficientFundsException.class);

    // assert the state didn't change (the part people forget)
    assertThat(account.balance()).isEqualTo(Money.of("10.00"));
}
```

**The forgotten assertion:** after a failure path, check that **nothing changed**. Half the bugs
in transactional code are "it threw, but it also wrote a row".

### 6.3 One behaviour per test

A test with five assertions on five behaviours fails with "line 47" and gives you no information.
Split it. The exception: asserting several facets of the **same** behaviour (the response code,
the body, and the side effect) is one test.

### 6.4 Parameterised tests for boundaries

```java
@ParameterizedTest
@CsvSource({
    "0,       0.00",
    "9999,    0.00",
    "10000,   5.00",     // boundary
    "10001,    5.00"     // boundary + 1
})
void discount_is_applied_from_10000(long cents, String expected) {
    assertThat(pricing.discountFor(cents)).isEqualTo(Money.of(expected));
}
```

Boundaries are where bugs live. `@ParameterizedTest` makes them cheap to cover.

---

## 7. Determinism

**A flaky test is a liability.** It teaches the team to re-run until green, which means real
failures get re-run too.

### 7.1 The sources of flakiness

| Source | Symptom | Fix |
|---|---|---|
| **Time** | fails at month-end, weekends, DST | inject a `Clock` |
| **Random / UUID** | intermittent assertions | inject a `Random`/id generator |
| **Ordering** | `HashMap` iteration, unordered SQL | sort before asserting, or assert as a set |
| **Timezone** | passes locally, fails in CI | `-Duser.timezone=UTC`, use `Instant` |
| **Ports / fixed resources** | parallel runs collide | random ports, Testcontainers |
| **Async** | `Thread.sleep` | Awaitility, `CountDownLatch`, virtual time |
| **Shared state** | tests pass alone, fail together | fresh context, no static mutable state |
| **Network** | real HTTP calls | fake server (WireMock) |
| **Floating point** | `assertEquals(0.1 + 0.2, 0.3)` | `BigDecimal`, or `isCloseTo` |
| **DB state** | leftover rows | `@Transactional` rollback or truncation |

### 7.2 Injecting time and randomness

```java
@Service
public class CouponService {
    private final Clock clock;

    public CouponService(Clock clock) { this.clock = clock; }

    public boolean isValid(Coupon c) {
        return !clock.instant().isAfter(c.expiresAt());
    }
}
```

```java
@Test
void coupon_isInvalid_afterExpiry() {
    var clock = Clock.fixed(Instant.parse("2026-03-11T10:00:00Z"), ZoneOffset.UTC);
    var service = new CouponService(clock);
    var coupon = new Coupon(Instant.parse("2026-03-11T09:59:59Z"));

    assertThat(service.isValid(coupon)).isFalse();
}
```

**This is also a design improvement** — a class that reads `Instant.now()` directly is untestable
without a static mock (and static mocks are a hint you should have injected the dependency).

### 7.3 Never `Thread.sleep`

```java
// ❌ flaky in both directions: too short fails, too long is slow
Thread.sleep(500);
assertThat(processed).isTrue();

// ✅
await().atMost(Duration.ofSeconds(2)).until(() -> processed.get());
```

`Awaitility` polls until the condition holds or the timeout expires — fast when it works,
deterministic when it doesn't.

---

## 8. Coverage — the Honest Version

### 8.1 What coverage measures

**Line coverage** — which lines executed. **Branch coverage** — which decisions took both paths.
Neither says your assertions are meaningful:

```java
@Test
void coversEverything() {
    service.calculate(order);      // 100% line coverage, zero assertions
}
```

That test gives you 100% coverage on `calculate` and catches nothing.

### 8.2 How to use it

| Do | Don't |
|---|---|
| Use it to find **untested** areas | Use it as a target to hit |
| Set a **floor** (e.g. 60% won't decrease) | Celebrate 90% |
| Look at branch coverage on business logic | Enforce 100% globally |
| Exclude DTOs/config, or the number is meaningless | Let boilerplate inflate the score |
| Review the **diff** coverage of a PR | Only look at the project total |

**The pragmatic setup:**

```xml
<rule>
  <element>BUNDLE</element>
  <limits>
    <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.60</minimum></limit>
  </limits>
</rule>
<rule>
  <element>CLASS</element>
  <includes><include>com.acme.pricing.*</include></includes>
  <limits>
    <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.85</minimum></limit>
  </limits>
</rule>
```

**Per-package rules beat global rules.** Money logic at 85% branch coverage is meaningful;
a global 85% target just produces assertion-free tests in DTOs.

### 8.3 Mutation testing — the honest measure

`PIT` mutates your code (flip a comparison, negate a condition) and checks that a test fails. If
a mutation survives, your tests didn't notice — that's a real gap.

```
score 70%+ on business logic = genuinely good tests
score 20% with 90% line coverage = tests that execute but don't assert
```

Running it on the whole codebase is slow; run it on the packages that matter. Mentioning mutation
testing at all is a strong signal.

---

## 9. What Tests to Write for a Feature

A checklist you can apply to any story:

```
FEATURE: "Apply a percentage discount to an order."

 1. HAPPY PATH
    discount applied to a normal order

 2. BOUNDARIES
    no discount at 0; minimum threshold; maximum percentage (100%); rounding at .005

 3. FAILURE
    expired coupon, wrong customer, below minimum, already-applied

 4. STATE
    on failure, the order total is unchanged

 5. IDEMPOTENCY / REPEAT
    applying twice doesn't double the discount

 6. AUTHORISATION
    a user can't apply a coupon they don't own

 7. INTEGRATION
    the discount survives the DB round-trip and the JSON response
    (this is where a `BigDecimal` scale or a serialisation bug shows up)

 8. CONCURRENCY (if relevant)
    two simultaneous applications on the same order produce one discount
```

**Eight categories.** Most teams write #1. The value is in #2–#8, and #7 is the one unit tests
structurally cannot cover.

---

## 10. Code Examples

### 10.1 A well-structured unit test

```java
class DiscountCalculatorTest {

    private final DiscountCalculator calc = new DiscountCalculator();

    @Test
    void appliesPercentageDiscount_whenAboveThreshold() {
        var order = Order.withSubtotal(Money.of("200.00"));

        var discounted = calc.apply(order, new PercentCoupon(10, Money.of("100.00")));

        assertThat(discounted.total()).isEqualTo(Money.of("180.00"));
    }

    @Test
    void doesNotApply_whenBelowThreshold() {
        var order = Order.withSubtotal(Money.of("99.99"));

        var discounted = calc.apply(order, new PercentCoupon(10, Money.of("100.00")));

        assertThat(discounted.total()).isEqualTo(Money.of("99.99"));
    }

    @ParameterizedTest
    @CsvSource({
        "100.00,  10, 90.00",
        "100.01,  10, 90.01",     // boundary + 1 cent
        "333.33,  15, 283.33"     // rounding
    })
    void handlesRoundingAtBoundaries(String subtotal, int percent, String expected) {
        var order = Order.withSubtotal(Money.of(subtotal));
        assertThat(calc.apply(order, new PercentCoupon(percent, Money.of("100.00"))).total())
            .isEqualTo(Money.of(expected));
    }
}
```

### 10.2 Testing an interaction you actually care about

```java
@Test
void publishesOrderPlaced_withinTheSameTransaction_asTheSave() {
    var repo = new InMemoryOrderRepository();
    var outbox = new RecordingOutbox();
    var service = new OrderService(repo, outbox);

    service.place(new Cart(...));

    // state, not interaction: both effects happened
    assertThat(repo.count()).isEqualTo(1);
    assertThat(outbox.events()).extracting(OutboxEvent::type).containsExactly("OrderPlaced");
}
```

### 10.3 Time and id injection

```java
@Configuration
class ClockConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean IdGenerator ids() { return () -> UUID.randomUUID().toString(); }
}
```

```java
@Test
void reservation_expires_after15Minutes() {
    var clock = new MutableClock(Instant.parse("2026-03-11T10:00:00Z"));
    var service = new ReservationService(clock);

    var r = service.reserve(sku);
    assertThat(service.isValid(r)).isTrue();

    clock.advance(Duration.ofMinutes(15).plusSeconds(1));
    assertThat(service.isValid(r)).isFalse();
}
```

A `MutableClock` (a tiny test helper) is one of the highest-value utilities you can write.

### 10.4 ArchUnit — tests for architecture

```java
@AnalyzeClasses(packages = "com.acme")
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllers_do_not_access_repositories =
        noClasses().that().resideInAPackage("..web..")
                   .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule services_are_transactional_only_at_the_boundary =
        classes().that().resideInAPackage("..service..")
                 .should().onlyBeAccessed().byAnyPackage("..web..", "..service..");

    @ArchTest
    static final ArchRule no_field_injection =
        noFields().should().beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class);
}
```

**ArchUnit encodes the rules nobody remembers in review**, and it's the cheapest "test" in the
suite — no runtime, pure bytecode analysis.

---

## 11. Common Mistakes

| Mistake | Consequence | Fix |
|---|---|---|
| Chasing coverage % | assertion-free tests | per-package rules + mutation testing |
| Mocking everything | tests pass, app broken | integration slice with real DB |
| Testing private methods | brittle, breaks on refactor | test public behaviour |
| One giant test with 20 assertions | uninformative failures | one behaviour per test |
| `Thread.sleep` | flaky, slow | Awaitility |
| `Instant.now()` inside production code | untestable expiry logic | inject `Clock` |
| Asserting `HashMap` order | flaky | sort or assert as a set |
| Shared mutable static state | order-dependent failures | fresh fixtures per test |
| Testing the framework | noise | delete |
| No test for the bug you just fixed | it comes back | reproduce → fix → keep |
| Slow test suite (30+ min) | devs stop running it | split fast/slow, parallelise |
| Only testing the happy path | production finds the rest | boundaries + failure + state |
| Ignoring flaky tests | team ignores red | quarantine and fix within a day |
| Tests coupled to production by copy-paste | tests pass but don't test | build fixtures independently |

---

## 12. Interview Questions & Model Answers

**Q1. How do you decide what to test?**
> By risk: what's expensive if it's wrong, and what's likely to break. That means business rules,
> money, state transitions, boundaries, authorisation, and integration points whose contracts I
> don't own. Getters and framework behaviour get nothing. And I'd test the layer where the risk
> actually lives — for a Spring app that's often the integration slice, because a unit test with
> mocked repositories can pass while the SQL or the transaction is broken.

**Q2. What's wrong with a high unit-test count and no integration tests?**
> Every mock is an assumption about how the real component behaves. If the bug is in the JPQL, the
> serialisation, the transaction boundary, or the security config, unit tests are structurally
> blind to it. That's why I want the integration layer thick enough to exercise the wiring,
> especially for repositories and controllers.

**Q3. Is 100% coverage a good goal?**
> No — coverage measures execution, not verification. You can get 100% with no assertions. I'd use
> coverage to find blind spots, set a floor so it can't regress, and set a higher branch-coverage
> bar on the packages that matter like pricing. For real confidence I'd use mutation testing on
> those packages, because surviving mutations show where the assertions are missing.

**Q4. How do you keep tests from being flaky?**
> Remove every source of non-determinism: inject a `Clock` instead of calling `now()`, inject an
> id generator, sort before comparing, never sleep (use Awaitility), set the timezone to UTC, use
> Testcontainers rather than fixed ports, and isolate state so tests don't depend on order. And I'd
> treat a flaky test as a bug with an owner, quarantined within a day — otherwise the team learns
> to ignore red.

**Q5. When do you mock, and when do you use a fake?**
> I mock things I don't own and can't run — third-party APIs, slow or non-deterministic
> dependencies, and to simulate failures. For my own types I prefer fakes or real instances,
> because then I can assert on state rather than interactions, and the test survives refactoring. A
> test with five mocks and forty lines of stubbing usually indicates a design problem.

**Q6. How do you test a bug fix?**
> Reproduce it as a failing test first, then fix it, then keep the test forever. That guarantees
> the fix is real and prevents the regression. If I can't write a failing test, I don't yet
> understand the bug.

**Q7. What test would you write for a discount calculation?**
> The happy path, the threshold boundaries (below, at, and one cent above), rounding at .005,
> expired and wrong-customer coupons, the failure path asserting the total is unchanged, and
> reapplying without double-discounting. Then one integration test proving the discounted total
> survives the database round-trip and the JSON response, because a `BigDecimal` scale or
> serialisation bug is exactly what unit tests miss.

**Q8. How do you test that a method is transactional?**
> Not by unit-testing the annotation — that's testing Spring. I'd write an integration test with a
> real database: force an exception after the write and assert the row isn't there. That proves the
> transaction actually rolls back, which is the behaviour. It also catches the classic
> self-invocation bug, which no annotation check would find.

---

## 13. Self-Check & Revision Card

- [ ] I can state what a test suite is for in one sentence.
- [ ] I can explain the pyramid and the "trophy" correction.
- [ ] I can list what to test and what not to.
- [ ] I can distinguish mock, stub, spy and fake and choose correctly.
- [ ] I can write a test name that explains the failure.
- [ ] I can eliminate time, randomness and ordering from tests.
- [ ] I can explain what coverage does and doesn't measure.
- [ ] I can name the eight categories of test for a feature.

```
┌───────────────────── TESTING STRATEGY — 60s RECALL ─────────────────────────┐
│ Tests = RISK MANAGEMENT. Test where bugs are expensive AND likely.           │
│                                                                              │
│ PYRAMID: many fast unit · fewer integration · very few E2E.                 │
│ TROPHY: integration is often the HIGHEST VALUE in Spring — unit tests with   │
│   mocked repos can pass while SQL/tx/serialisation/security are broken.      │
│                                                                              │
│ TEST: business rules · money · state machines · BOUNDARIES · authZ ·         │
│   idempotency · concurrency · CONTRACTS · bug reproductions.                 │
│ DON'T: getters · framework · private methods · implementation details.        │
│                                                                              │
│ DOUBLES: prefer FAKES (assert state) over MOCKS (assert interactions).        │
│   Mock only what you don't own or can't run.                                 │
│                                                                              │
│ DETERMINISM: inject Clock · inject ids · sort before assert · Awaitility     │
│   never sleep · UTC · Testcontainers · no shared static state.               │
│                                                                              │
│ COVERAGE: measures execution, not verification. Floor + per-package branch   │
│   rules + MUTATION testing on money/domain packages.                         │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Next:** [02 · Unit & Slice Testing with JUnit 5, Mockito & Spring Boot Test](./02-Unit-and-Slice-Testing.md)
