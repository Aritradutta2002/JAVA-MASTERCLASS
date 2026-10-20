# Testing Best Practices — The Complete Interview Guide

> **The chapter that decides whether your tests are an asset or a liability.**
> A test suite is not measured by its *coverage percentage* — it is measured by
> whether it **fails when the behaviour breaks** and **stays green when it does not**.

## Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — The One Picture](#2-mental-model--the-one-picture)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
- [4. Visualizations](#4-visualizations)
- [5. Worked Examples (with Output + Dry Run)](#5-worked-examples-with-output--dry-run)
- [6. Code from Scratch — Production-Grade](#6-code-from-scratch--production-grade)
- [7. Common Pitfalls & Gotchas](#7-common-pitfalls--gotchas)
- [8. Interview Q&A — Level-wise](#8-interview-qa--level-wise)
- [9. Counter-Questions & Traps](#9-counter-questions--traps)
- [10. Rapid-Fire MCQs](#10-rapid-fire-mcqs)
- [11. Cheat Sheet / One-Page Summary](#11-cheat-sheet--one-page-summary)
- [12. Real-World Production Notes](#12-real-world-production-notes)
- [13. Follow-up Learning](#13-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

- **The test pyramid:** many **unit** tests (fast, isolated), fewer **integration** tests (real dependencies), a few **end-to-end** tests. The ratio matters more than the absolute number.
- **Test what matters:** the *behaviour*, not the implementation. A test that breaks on a refactor that did not change the behaviour is a **bad** test.
- **The AAA pattern:** **Arrange, Act, Assert** — the structure of a good test.
- **The FIRST principles:** **Fast, Independent, Repeatable, Self-validating, Time-boxed** — a test that is not fast is not run; a test that is not repeatable is a flake.
- **Determinism is non-negotiable.** No `Thread.sleep()`, no wall-clock dependency, no random without a seed, no ordering dependency.
- **Flaky tests are worse than no tests** — they destroy the team's trust in the whole suite. Quarantine and fix them; never leave them in the main run.
- **Coverage is a *hint*, not a goal.** 100% coverage with meaningless assertions is worse than 60% with good ones. Use **mutation testing** (PIT) to measure whether your tests are actually *effective*.
- **Test the contract, not the implementation.** Assert on observable outcomes; verify interactions only when the interaction *is* the requirement.
- **A test should have ONE reason to fail.** Use `assertAll` for independent facts; use a plain assertion for a precondition.
- **The test name is the specification.** If a reader needs the code to know what is verified, the name is wrong.
- **Use a "golden master" / characterisation test** when refactoring legacy code with no tests — capture the current behaviour, then refactor safely.
- **The "test pyramid" is a heuristic, not a law** — an IO-bound service may legitimately invert it (many component tests, few E2E).

---

## 1. What & Why — The Problem This Solves

**The problem.** A test suite has a **cost** and a **benefit**, and the benefit only
materialises if the tests are *trusted*.

```text
   THE TRUST EQUATION
   ═══════════════════

   TRUST = the probability that a red build means a real defect

   A suite that is 95% reliable:
     → 5% of the red builds are noise
     → the team learns to "rerun it"
     → the signal is lost, and the suite becomes theatre

   A suite that is 100% reliable:
     → every red build is investigated
     → the suite is a REAL safety net
     → refactoring becomes safe
```

**The solution.** A set of practices that maximise the *signal* and minimise the
*noise*:

```text
   ① DETERMINISM    — the same input always gives the same result
   ② ISOLATION      — a test cannot affect another
   ③ SPEED          — fast enough to be run on every save
   ④ CLARITY        — the name and the structure document the behaviour
   ⑤ TRUTH          — the test fails when the behaviour breaks
```

> 💡 **The interview framing.** The question "how do you know your tests are good?"
> has a precise answer: **mutation testing**. If you mutate the production code
> (change `>` to `>=`, delete a line, negate a condition) and the test suite still
> passes, the test is not *verifying* anything. Coverage measures *execution*;
> mutation measures *verification*.

---

## 2. Mental Model — The One Picture

```text
   THE TEST PYRAMID — and where the time goes
   ══════════════════════════════════════════

              ╱╲
             ╱  ╲              E2E (a few)
            ╱ E2E╲             • slow (seconds–minutes)
           ╱──────╲            • brittle
          ╱        ╲           • expensive to maintain
         ╱ INTEGR.  ╲         • ⚠ a handful of critical journeys
        ╱────────────╲
       ╱              ╲      COMPONENT / INTEGRATION
      ╱   COMPONENT    ╲     • real dependencies (Testcontainers)
     ╱──────────────────╲    • slower (seconds)
    ╱                    ╲   • valuable — catches the wiring bugs
   ╱      UNIT TESTS       ╲
  ╱──────────────────────────╲  UNIT (many)
 ╱                            ╲ • milliseconds
╱______________________________╲• pure, fast, isolated
                                 • ⚠ 95% of the suite — and the FIRST
                                   thing to run

   ⚠ THE PYRAMID IS A HEAP, NOT A LAW:
     for an IO-bound service, the "integration" layer may be the
     cheapest way to get REAL coverage — so the ratio inverts.
```

```text
   THE QUALITY OF A TEST — measured by MUTATION, not coverage
   ══════════════════════════════════════════════════════════

   ORIGINAL CODE                    MUTANT                    RESULT
   ─────────────                    ──────                    ───────
   if (amount > 0) {                if (amount >= 0) {         💥 the test MUST fail
       charge(amount);                  charge(amount);
   }                                }

   ORIGINAL CODE                    MUTANT                    RESULT
   ─────────────                    ──────                    ───────
   return balance.subtract(amount);  return balance.add(amount);  💥 the test MUST fail

   IF THE MUTANT SURVIVES  →  the assertion is not verifying anything
   → 100% coverage can still mean ZERO verification
```

**What to look at:** coverage answers "was this line *executed*?"; mutation answers
"would a wrong answer have been *caught*?" — and only the second one matters.

---

## 3. Deep Dive — Theory

## 3.1 The FIRST principles

| Principle | Meaning | How to break it |
|---|---|---|
| **Fast** | Runs in milliseconds | A network call, a container, a `Thread.sleep(1000)` |
| **Independent** | No ordering or shared state | A static field, a shared database row, an order dependency |
| **Repeatable** | The same result every time | The wall clock, `Math.random()`, a mutable singleton |
| **Self-validating** | Passes or fails on its own | A test that always passes, or a manual check |
| **Time-boxed** | Has a deadline | A test that hangs on a deadlock |

**The two that get broken most:** **Independent** and **Repeatable** — and both
produce the same symptom, a **flaky test**.

## 3.2 The AAA pattern

```java
@Test
void rejectsANegativeAmount() {
    // ARRANGE
    var service = new OrderService(new FakeGateway());
    var order = new Order("O-1", List.of(new Item("SKU", 1, new BigDecimal("10.00"))));

    // ACT
    var result = service.total(order);

    // ASSERT
    assertEquals(result, new BigDecimal("10.00"));
}
```

**Why the three sections:** the AAA structure makes the *assertion* obvious when
reading the test, and it makes it easy to see that the arrange and act sections do
not contain assertions (which would make the test pass for the wrong reason).

## 3.3 The three test levels

| Level | What is real | What is faked | Speed | Count |
|---|---|---|---|---|
| **Unit** | The class under test | **Everything** | ms | Hundreds/thousands |
| **Component** | The class + its internal collaborators | The *ports* (DB, HTTP) | ms–s | Tens |
| **Integration** | The real infrastructure (Testcontainers) | Nothing | s–min | Tens |
| **E2E** | The whole deployed system | Nothing | min | A handful |

**The critical insight:** the "unit" test is a *design* artefact — it is only
possible if the class has **injectable** dependencies. A class that news up its
collaborators internally cannot be unit tested without a mocking framework — which
is a *design* signal, not a testing problem.

## 3.4 Test doubles — the choice

| Kind | What it is | Use for |
|---|---|---|
| **Fake** | A working implementation | An internal collaborator, a repository |
| **Stub** | A canned response | A boundary whose exact response you need |
| **Spy** | A real object, calls recorded | You need the real behaviour *and* the calls |
| **Mock** | A programmable double | Asserting on an interaction |
| **Dummy** | An inert object | Filling a parameter you do not care about |

**The rule of thumb:** *fake the internal, mock the boundary*.

## 3.5 Determinism — the four sources of non-determinism

| Source | The fix |
|---|---|
| **The wall clock** | Inject a `java.time.Clock` |
| **Randomness** | Inject a seeded `Random` / a `RandomGenerator` |
| **The current thread** | Inject an `Executor` |
| **Ordering** | Make the tests independent; never rely on `@Order` |
| **The environment** | Inject the configuration; never read a system property in a test |
| **The network** | A fake or a container; never a real call |

## 3.6 The characterisation test — for legacy code

```java
/**
 * A CHARACTERISATION test: it captures the CURRENT behaviour, not the
 * desired behaviour. It is the safe way to start testing legacy code.
 */
class LegacyReportRendererTest {

    /** The fixture the golden value was captured from. */
    private Report sampleReport() {
        return new Report("42.00", 3);
    }

    @Test
    void rendersTheCurrentOutput() {
        var renderer = new LegacyReportRenderer();

        var output = renderer.render(sampleReport());

        // ⚠ THE GOLDEN VALUE — captured from the CURRENT implementation
        assertEquals(output, "=== REPORT ===\nTotal: 42.00\nItems: 3\n");
    }
}
```

**The workflow:**

```text
   ① CAPTURE   run the legacy code, record the output
   ② PIN       assert against the recorded value (a golden master)
   ③ REFACTOR  change the code
   ④ VERIFY    the test still passes → the behaviour is preserved ✅
   ⑤ IMPROVE   now change the test to assert the DESIRED behaviour,
                and change the code to match ✅
```

**The golden master is usually a file**, so the diff is reviewable:

```java
assertThat(renderer.render(input))
        .isEqualTo(readResource("expected/report-output.txt"));
```

## 3.7 Mutation testing — measuring the *effectiveness* of the tests

```bash
# Maven
mvn org.pitest:pitest-maven:mutationCoverage

# Gradle
./gradlew pitest
```

**The report says:** the **mutation score** — the percentage of mutants that were
**killed**. A score of 100% means every mutation was caught. A score of 20% means
your tests barely verify anything.

**The mutants that survive reveal *specific* gaps:**

| A surviving mutant | What it means |
|---|---|
| `>` → `>=` survived | A **boundary** case is not tested |
| `&&` → `\|\|` survived | A **combination** case is not tested |
| A deleted statement survived | The statement's **effect** is not asserted |
| A negated return survived | The **return value** is not asserted |

## 3.8 The test-data strategy

| Strategy | Use for |
|---|---|
| **Inline literals** | The simple case — readable and local |
| **A builder** | An object with many optional fields (`Order.builder()…`) |
| **A factory method** | A valid object with a sensible default |
| **A data provider** | The same scenario, many inputs |
| **A "persona" fixture** | A realistic, named scenario (`aGuestWithNoOrders()`) |
| **Property-based testing** (jqwik) | "For ALL inputs, this holds" — the strongest form |

**The builder/factory pattern:**

```java
static Order anOrder() {
    return Order.builder()
            .id("O-1")
            .customer("C-1")
            .items(List.of(new Item("SKU-1", 2, new BigDecimal("10.00"))))
            .build();
}
```

**The property-based form (jqwik):**

```java
@Property
void theTotalIsTheSumOfTheItems(@ForAll("items") List<Item> items) {
    var order = new Order("O-1", items);
    assertEquals(order.total(), items.stream()
            .map(Item::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
}
```

## 3.9 The pyramid in a modern service — the "test taxonomy"

```text
   WHAT YOU ARE TESTING         WHAT IT CATCHES                 COST
   ────────────────────         ──────────────────                ─────
   a DOMAIN RULE (pure)         a logic error                    free
   a SERVICE with fake ports     a wiring/orchestration error      ms
   a REPOSITORY (Testcontainers) a SQL/schema error                s
   a CONTROLLER (MockMvc)       a wiring/serialisation error      ms
   a full SYSTEM (E2E)          a deployment/contract error       min
```

## 3.10 Contract testing — for a service boundary

```text
   THE PROBLEM
   ───────────
   Service A and Service B have a contract.
   A changes it → B breaks → and B is a DIFFERENT TEAM's service.

   THE SOLUTION — Pact
   ─────────────────────
   ① The CONSUMER writes a pact: "I expect GET /orders/1 to return
      {status: 200, body: {id: "1", total: 10.00}}"
   ② The pact is published to a broker
   ③ The PROVIDER's build verifies it against the real provider
   → ⚠ the provider's CI FAILS if it breaks the contract

   ⇒ the breakage is caught in the PROVIDER's pipeline, not in production.
```

## 3.11 The CI pipeline shape

```text
   EVERY COMMIT (the inner loop — must be < 10 minutes)
   ─────────────────────────────────────────────────────
   ① compile + static analysis (SpotBugs, Error Prone, Checkstyle)
   ② UNIT tests (parallel)                    ⚡ < 1 minute
   ③ COMPONENT tests with fakes              ⚡ < 1 minute
   ④ CONTAINER tests (Testcontainers)         🕐 < 5 minutes
   ⑤ coverage + mutation report (a report, not a gate)

   EVERY NIGHT
   ──────────
   ⑥ the full E2E suite against a deployed environment

   THE RULE: the inner loop is a GATE; the outer loop is a REPORT.
   A slow gate trains people to batch their pushes — and to skip the gate.
```

---

## 4. Visualizations

### 4.1 The AAA structure

```text
   @Test
   void rejectsANegativeAmount() {
       // ARRANGE — build the inputs
       var service = new OrderService(new FakeGateway());
       var order  = new Order("O-1", List.of(item("SKU", 1, "10.00")));

       // ACT — do the ONE thing
       var result = service.total(order);

       // ASSERT — check the observable outcome
       assertEquals(result, new BigDecimal("10.00"));
   }

   ⚠ THE VALUE: a reader can see the ASSERTION without reading the code,
     and it is obvious if an assertion has crept into the ARRANGE section
     (which would make the test pass for the wrong reason).
```

### 4.2 Coverage vs mutation

```text
   COVERAGE — "was this line EXECUTED?"
   ──────────────────────────────────────
   if (amount > 0) {          ← 100% covered
       charge(amount);
   }
   ✅ "covered"        ❌ but is it VERIFIED?

   MUTATION — "would a WRONG answer have been CAUGHT?"
   ────────────────────────────────────────────────────
   if (amount >= 0) {         ← the boundary MUTANT
       charge(amount);
   }
   💥 the test MUST fail → the boundary IS verified ✅

   ⚠ 100% COVERAGE + 20% MUTATION SCORE
      = the tests execute the code but verify almost nothing
      = WORSE than 60% coverage with an 80% mutation score
```

### 4.3 The four sources of flakiness

```text
   ❶ THE WALL CLOCK       → inject a java.time.Clock
   ❷ RANDOMNESS           → inject a seeded Random / RandomGenerator
   ❸ ORDERING             → make the tests independent; never rely on @Order
   ❹ THE ENVIRONMENT      → inject the config; never read a system property

   ⚠ EVERY ONE OF THEM PRODUCES THE SAME SYMPTOM:
     "it passes locally and fails in CI"
   → AND THE SAME CONSEQUENCE:
     the team stops reading the failures
```

### 4.4 The characterisation-test workflow

```text
   ① CAPTURE   run the legacy code, record the output
   ② PIN       assert against the recorded value (a golden master)
   ③ REFACTOR  change the code
   ④ VERIFY    the test still passes → the behaviour is preserved ✅
   ⑤ IMPROVE   now assert the DESIRED behaviour, and change the code to match

   ⚠ THIS IS THE SAFE WAY TO START TESTING LEGACY CODE:
     you are not claiming the behaviour is CORRECT — only that it is
     UNCHANGED. That is a claim you can make.
```

### 4.5 The CI pipeline — a gate and a report

```text
   EVERY COMMIT — the GATE (must be < 10 min)
   ─────────────────────────────────────────────
   ① static analysis
   ② UNIT + COMPONENT tests (parallel)          ⚡ < 1 min
   ③ CONTAINER tests                            🕐 < 5 min
   ④ coverage + mutation → a REPORT (not a gate)

   NIGHTLY — the REPORT
   ───────────────────────
   ⑤ the full E2E suite
   ⑥ the mutation score, tracked over time

   ⚠ A SLOW GATE TRAINS PEOPLE TO BATCH THEIR PUSHES
     → AND THEN TO SKIP THE GATE. Keep the inner loop fast.
```

### 4.6 The "test smells" checklist

```text
   ❌ A test with NO assertions          → it passes even if nothing happened
   ❌ A test that asserts on the MOCK    → it tests the wiring, not the behaviour
   ❌ A test that sleeps                 → it is a race waiting to happen
   ❌ A test that depends on the order    → the hidden-dependency bug
   ❌ A test with a HUGE arrange block   → the fixture is too complicated
   ❌ A test named test1/test2            → the name is not documentation
   ❌ A test that needs a MANUAL step     → it is not automated
   ❌ A test that is SLOW                 → it will not be run
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — A deterministic test: the injected clock

```java
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicTest {

    // ✅ The clock is INJECTED — the test controls it, with no global state
    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    record Event(String id, Instant at) { }

    static final class EventService {
        private final Clock clock;
        EventService(Clock clock) { this.clock = clock; }
        Event create(String id) { return new Event(id, clock.instant()); }
    }

    @Test
    @DisplayName("the event timestamp comes from the injected clock")
    void usesTheInjectedClock() {
        EventService service = new EventService(FIXED);

        Event event = service.create("E-1");

        assertEquals(event.at(), Instant.parse("2026-01-01T00:00:00Z"));
        // ✅ deterministic, no mockStatic, no agent, and it works in parallel
    }
}
```

**Actual output:** the test passes, and it would pass identically at any time, on any
thread, in any order.

### Example 2 — A flaky test, and the fix

```java
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest {

    // ❌ THE FLAKY VERSION
    @Test
    void incrementsTheCounter() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        for (int i = 0; i < 1000; i++) {
            new Thread(() -> counter.incrementAndGet()).start();
        }
        Thread.sleep(100);          // ❌ a GUESS about how long the threads take
        assertEquals(1000, counter.get());
    }

    // ✅ THE DETERMINISTIC VERSION
    @Test
    void incrementsTheCounter() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        int threads = 10;
        int perThread = 1000;

        CountDownLatch start = new CountDownLatch(1);       // everyone starts TOGETHER
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();                          // wait for the signal
                    for (int j = 0; j < perThread; j++) counter.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            t.start();
        }

        start.countDown();                                  // ⚠ release them all
        assertTrue(done.await(5, TimeUnit.SECONDS), "the workers should finish");
        assertEquals(threads * perThread, counter.get());
    }
}
```

**Actual output:** the deterministic version passes **every** time; the flaky one
fails intermittently (and in CI, on a loaded machine, it fails *often*).

**Takeaway:** a `CountDownLatch` makes the interleaving **controlled**. A
`Thread.sleep` makes it a **guess** — and a guess that fails under load.

### Example 3 — The AAA structure and `assertAll`

```java
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AaaTest {

    record Item(String sku, int quantity, BigDecimal price) { }

    @Test
    @DisplayName("the order summary is correct")
    void summarisesTheOrder() {
        // ARRANGE
        var items = List.of(
                new Item("A", 2, new BigDecimal("10.00")),
                new Item("B", 1, new BigDecimal("5.50")));

        // ACT
        BigDecimal total = items.stream()
                .map(i -> i.price().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int count = items.size();

        // ASSERT — independent facts → report them all at once
        assertAll("the order summary",
                () -> assertEquals(new BigDecimal("25.50"), total),
                () -> assertEquals(2, count));
    }
}
```

**Actual output (if the total is wrong):**

```text
Multiple Failures (1 failure)
	AaaTest.summarisesTheOrder
		the order summary
		=> expected: <25.50> but was: <25.00>
```

**Takeaway:** with `assertAll`, one run tells you everything that is wrong — not just
the first thing.

### Example 4 — A characterisation test for legacy code

```java
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LegacyRendererTest {

    /**
     * A CHARACTERISATION test: it pins the CURRENT behaviour — not the
     * desired behaviour. It is the safe way to start testing legacy code.
     */
    @Test
    @DisplayName("the current output is preserved")
    void preservesTheCurrentOutput() {
        var renderer = new LegacyReportRenderer();

        String output = renderer.render("ORD-1", List.of("A", "B"));

        // ⚠ THE GOLDEN VALUE — captured from the CURRENT implementation
        assertEquals(output, """
                === REPORT ===
                Order: ORD-1
                Items: 2
                """);
    }
}
```

**Actual output:**

```text
=== REPORT ===
Order: ORD-1
Items: 2
```

**Takeaway:** the golden value is captured from the *existing* code. The claim the
test makes is "this behaviour is **unchanged**" — which is a claim you can
actually make. Step 5 of the workflow then upgrades it to "this behaviour is
**correct**".

### Example 5 — Mutation testing, conceptually

```java
// ⚠ The real file needs:  import org.junit.jupiter.api.Test;
//                          import static org.junit.jupiter.api.Assertions.*;

class MutationDemo {

    // THE ORIGINAL
    static boolean isEligible(int age, boolean hasAccount) {
        return age >= 18 && hasAccount;    // the MUTANTS: >= → >, && → ||
    }

    // A WEAK TEST — it executes the line but verifies little
    @Test
    void weak() {
        assertTrue(isEligible(20, true));
        assertFalse(isEligible(17, true));
    }

    // A STRONG TEST — it kills the boundary and the combination mutants
    @Test
    void strong() {
        assertTrue(isEligible(18, true));    // ⚠ kills ">= → >"  (the boundary)
        assertFalse(isEligible(18, false));   // ⚠ kills "&& → ||" (the combination)
        assertFalse(isEligible(17, false));
    }
}
```

**The mutation report for the weak test:**

```text
Mutant: isEligible(int, boolean): (int) int  →  (int) (int + 1)   → SURVIVED
Mutant: isEligible(int, boolean):  replaces && with ||              → SURVIVED
Mutant: isEligible(int, boolean): (int) int  →  (int) (int - 1)   → SURVIVED

MUTATION SCORE: 40%   ⚠ the weak test verifies very little
```

**Takeaway:** the strong test kills the boundary and the combination mutants — and
those are exactly the two mutations that cause the most real bugs. This is why
mutation testing is a better measure than coverage.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A test-data builder — readable, minimal, immutable

```java
package com.example.testing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A test-data builder.
 *
 * <p>Why a builder and not a constructor with 12 arguments: the test reads as a
 * <b>sentence</b> ({@code anOrder().withItems(...).build()}) and the defaults are
 * <b>valid</b> — so a test only states the thing it cares about.
 *
 * <p>⚠ The builder is <b>immutable</b> — every {@code with...} returns a NEW builder —
 * so a partially-built object can be safely shared between tests.
 */
public final class OrderBuilder {

    private String id = "O-DEFAULT";
    private String customerId = "C-DEFAULT";
    private BigDecimal discount = BigDecimal.ZERO;
    private final List<Item> items = new ArrayList<>();

    public static OrderBuilder anOrder() {
        return new OrderBuilder();
    }

    public OrderBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public OrderBuilder withCustomer(String customerId) {
        this.customerId = customerId;
        return this;
    }

    public OrderBuilder withDiscount(BigDecimal discount) {
        this.discount = Objects.requireNonNull(discount, "discount");
        return this;
    }

    public OrderBuilder withItem(String sku, int quantity, String unitPrice) {
        items.add(new Item(sku, quantity, new BigDecimal(unitPrice)));
        return this;
    }

    public OrderBuilder withItems(List<Item> items) {
        this.items.clear();
        this.items.addAll(items);
        return this;
    }

    /** An order with no items — the "empty" scenario, stated explicitly. */
    public OrderBuilder withNoItems() {
        this.items.clear();
        return this;
    }

    public Order build() {
        return new Order(id, customerId, List.copyOf(items), discount);
    }

    public record Item(String sku, int quantity, BigDecimal unitPrice) {
        public Item {
            Objects.requireNonNull(sku, "sku");
            Objects.requireNonNull(unitPrice, "unitPrice");
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be > 0");
            }
        }
    }

    public record Order(String id, String customerId, List<Item> items, BigDecimal discount) {
        public Order {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(customerId, "customerId");
            items = List.copyOf(items);
            discount = Objects.requireNonNull(discount, "discount");
        }

        public BigDecimal total() {
            BigDecimal gross = items.stream()
                    .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return gross.subtract(discount);
        }
    }
}
```

### 6.2 A deterministic concurrency test — no `sleep`

```java
package com.example.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A DETERMINISTIC concurrency test.
 *
 * <p>The discipline:
 * <ul>
 *   <li>a {@link CountDownLatch} to <b>start</b> the workers together;</li>
 *   <li>another to <b>wait</b> for them to finish — with a <b>timeout</b>;</li>
 *   <li>NO {@code Thread.sleep} anywhere — a sleep is a guess, and a guess
 *       that fails under load.</li>
 *   <li>an <b>invariant</b> assertion, not an exact-value assertion.</li>
 * </ul>
 */
class ConcurrentCounterTest {

    @Test
    @DisplayName("every increment is counted exactly once")
    void countsEveryIncrement() throws InterruptedException {
        int workers = 8;
        int incrementsPerWorker = 10_000;
        AtomicInteger counter = new AtomicInteger();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(workers);

        try (ExecutorService pool = Executors.newFixedThreadPool(workers)) {
            for (int i = 0; i < workers; i++) {
                pool.submit(() -> {
                    try {
                        start.await();                       // wait for the signal
                        for (int j = 0; j < incrementsPerWorker; j++) {
                            counter.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
            }

            start.countDown();                                // ⚠ release them TOGETHER
            assertTrue(finished.await(30, TimeUnit.SECONDS),
                    "the workers should finish — otherwise there is a deadlock");
        }

        // ✅ THE INVARIANT — the exact number, which is what the code guarantees
        assertEquals((long) workers * incrementsPerWorker, counter.get());
    }
}
```

### 6.3 A contract test for a service boundary

```java
package com.example.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A CONTRACT test — the consumer's expectation of the provider.
 *
 * <p>In a monolith this is a "component" test. In a microservice fleet it becomes
 * a <b>Pact</b> contract: the consumer publishes this expectation, and the
 * PROVIDER's build verifies it — so the breakage is caught in the provider's
 * pipeline, not in production.
 */
class OrderServiceContractTest {

    @Test
    @DisplayName("the provider returns the documented shape")
    void returnsTheDocumentedShape() {
        FakeOrderProvider provider = new FakeOrderProvider()
                .returningAnOrder("O-1", "ACTIVE", new java.math.BigDecimal("42.00"));

        var response = provider.getOrder("O-1");

        // ✅ THE CONTRACT — the fields, and their types, are the agreement
        assertAll("the order response",
                () -> assertNotNull(response.id()),
                () -> assertEquals("O-1", response.id()),
                () -> assertEquals("ACTIVE", response.status()),
                () -> assertEquals(new java.math.BigDecimal("42.00"), response.total()));
    }
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — `Thread.sleep()` to synchronise

```java
service.start();
Thread.sleep(100);        // ❌ a GUESS — it fails on a loaded CI machine
assertTrue(service.isRunning());
```

**✅ FIX** — a `CountDownLatch`, a `Future.get(timeout)`, or an `awaitility` call.

---

### ❌ Pitfall 2 — Depending on the test order

```java
@Test void aCreatesTheOrder() { … }
@Test void bFindsTheOrder() { … }   // ⚠ assumes `a` ran first
```

**✅ FIX** — make each test independent. If an order is genuinely required, use
`@TestMethodOrder` — and treat it as a smell.

---

### ❌ Pitfall 3 — The wall clock in a test

```java
assertTrue(service.createdAt().isAfter(Instant.now().minusSeconds(1)));   // ❌ flaky
```

**✅ FIX** — inject a `java.time.Clock` and assert against the fixed value.

---

### ❌ Pitfall 4 — Randomness without a seed

```java
int id = random.nextInt(1000);   // ❌ non-deterministic
```

**✅ FIX** — a seeded `Random`, or an injected `RandomGenerator`.

---

### ❌ Pitfall 5 — A test with no assertions

```java
@Test void processesTheOrder() { service.process(order); }   // ❌ always passes
```

**✅ FIX** — assert on the observable outcome.

---

### ❌ Pitfall 6 — Asserting on the mock instead of the result

```java
verify(gateway).charge(any());   // ⚠ it says nothing about whether it WORKED
```

**✅ FIX** — assert on the result; verify the interaction only when it is the contract.

---

### ❌ Pitfall 7 — A shared mutable fixture

```java
static List<String> shared = new ArrayList<>();   // ⚠ leaks between tests
```

**✅ FIX** — an instance field, or reset it in `@BeforeEach`.

---

### ❌ Pitfall 8 — Chasing a coverage number

```java
// "we need 90% coverage"
assertNotNull(service.get(0));   // ❌ an assertion that verifies nothing
```

**✅ FIX** — measure with **mutation testing**; assert on behaviour.

---

### ❌ Pitfall 9 — A slow inner loop

```java
@BeforeEach void setUp() { container.start(); }   // ❌ 5 seconds PER TEST
```

**✅ FIX** — start the container **once** (`@BeforeAll`) and reuse it.

---

### ❌ Pitfall 10 — Leaving a flaky test in the main suite

```java
@Disabled("flaky")   // ❌ it is disabled FOREVER, and nobody fixes it
```

**✅ FIX** — **quarantine** it (a tag, excluded from the main run but still run
nightly) with an owner and a ticket — and fix it.

---

### ❌ Pitfall 11 — Testing the framework

```java
assertEquals(mock.getName(), "foo");   // ❌ you are testing Mockito
```

**✅ FIX** — assert on *your* code's behaviour.

---

### ❌ Pitfall 12 — A test that needs a manual step

```java
// "run this, then paste the output here"
```

**✅ FIX** — automate it, or delete it — a test that needs a human is not a test.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What makes a good unit test?**
**A:** It is **fast** (milliseconds), **independent** (no shared state or ordering),
**repeatable** (deterministic — no clock, no randomness), **self-validating** (it
passes or fails on its own, with no manual step), and **focused** (one reason to
fail). The name should describe the behaviour, and the structure should be
**Arrange, Act, Assert**.

**Q: What is the AAA pattern?**
**A:** **Arrange, Act, Assert** — the three sections of a test: build the inputs, do
the one thing, check the outcome. It makes the assertion obvious to a reader, and it
makes it easy to spot an assertion that has crept into the arrange section (which
would let the test pass for the wrong reason).

**Q: What is the test pyramid?**
**A:** Many fast **unit** tests at the base, fewer **component/integration** tests
that use real infrastructure, and a few slow **E2E** tests at the top. The ratio
matters: the inner loop must be fast enough to run on every save, and the E2E
tests must be few enough to be maintainable.

**Q: What is a flaky test?**
**A:** A test that passes and fails **non-deterministically** with the same code. The
usual causes are timing (`Thread.sleep`), shared state, ordering dependencies, the
wall clock, and randomness. Flaky tests are worse than no tests, because they
destroy the team's trust in the whole suite — every red build is assumed to be
noise.

### 🟡 Intermediate

**Q: Why is coverage a poor measure of test quality?**
**A:** Because it measures **execution**, not **verification**. A test can execute
every line and still verify almost nothing — `assertNotNull(result.get(0))` executes
the code and asserts almost nothing. **Mutation testing** measures the real thing:
you change the production code (a boundary, a condition, a return value) and check
whether the test **fails**. A surviving mutant is a specific gap — an untested
boundary or an untested combination.

**Q: How do you test code that depends on the current time?**
**A:** **Inject a `java.time.Clock`.** `Clock.fixed(instant, zone)` in the test,
`Clock.systemUTC()` in production. This is deterministic, thread-safe, works under
parallel execution, needs no mocking agent, and — importantly — the same mechanism
works in production code (a staging environment can use a fixed clock too).

**Q: What is a characterisation test, and when do you use one?**
**A:** A test that pins the **current** behaviour of existing code, so that a
refactoring can be done safely. The workflow: capture the current output, assert
against it (a golden value or a golden file), refactor, and verify the test still
passes. It is the safe way to start testing legacy code, because the claim it makes
is "this behaviour is **unchanged**" — a claim you can actually make — rather than
"this behaviour is **correct**", which is a different (and much harder) claim.

**Q: What is the difference between a mock and a fake, and when do you use each?**
**A:** A **fake** is a working implementation (an in-memory repository) that you
assert the *outcome* against; a **mock** is a programmable double that you assert
the *interaction* on. Use a fake for a deterministic collaborator that is part of
your own domain, and a mock for a **boundary** — a remote service, a clock, a
random source — that is slow, non-deterministic, or outside your control.

### 🔴 Advanced / Senior

**Q: How do you test a concurrent component without writing flaky tests?**
**A:** Four techniques, in order of preference. (1) **Assert an invariant**, not an
outcome — "the counter equals the number of increments", not "the counter is 100". (2) **Control the interleaving** with a `CountDownLatch` or a `CyclicBarrier` so the test is *deterministic* rather than *probabilistic*. (3) **Use `jcstress`** for the algorithm-level properties — it enumerates the interesting interleavings and asserts that only *legal* outcomes occur, which is the only way to be confident. (4) **Never `Thread.sleep()`** — it is the single largest source of flaky tests. And the process discipline: when a test *is* flaky, **quarantine it** (a tag, excluded from the default run, run nightly) and **fix it** — a flaky test in the main suite is worse than no test, because it trains the team to ignore red.

**Q: How would you introduce tests into a large legacy codebase with none?**
**A:** In five stages, each independently valuable. (1) **Add a safety net** — a **characterisation test** around the riskiest module, pinning the current behaviour with a golden file; this is the highest-value first step because it enables every later refactor. (2) **Find the seams** — the classes with the most inbound dependencies and the most churn; those are where a bug is most likely and most expensive. (3) **Extract testable units** — a pure function, a value object, a state machine — and test them exhaustively; this is usually cheap because the extraction is a refactor the code needs anyway. (4) **Add the missing edge tests** — the boundary conditions, the error paths, and the *combinations* (which is where the surviving mutation mutants point). (5) **Measure with mutation**, not coverage — and track the score over time. The critical discipline: **the suite must be green before you start**, and every step must leave it green — a test-introduction that breaks the build is abandoned.

**Q: How do you decide what to unit test, what to integration test, and what to leave alone?**
**A:** By **where the risk is**, not by coverage. (1) **Unit test the domain logic** — the rules, the calculations, the state machines. It is fast, and it is where the *interesting* bugs are. (2) **Integration test the wiring** — the SQL, the serialisation, the framework configuration. These are where the *boring but expensive* bugs are (a wrong column name, a missing `@Transactional`). (3) **E2E test only the critical journeys** — the ones that would cost money if they broke. (4) **Do not test the framework** — Spring, Jackson and Hibernate have their own tests; yours should test *your* configuration of them. And the practical ratio: the "test everything" instinct is a trap — a test you write because the code is uncovered, rather than because the behaviour matters, is a test that will be deleted in the next refactor and will not catch anything in the meantime.

### ⚫ Expert / Principal

**Q: "Test the behaviour, not the implementation" is good advice, but how do you decide what the "behaviour" of a component is?**
**A:** The question exposes that the advice is a **heuristic**, not a definition, and the honest answer is that the boundary is **determined by the design, not by the test**. A class's "behaviour" is the set of *observable consequences* of its operations for the **clients** it is written for. Whether a particular method is part of that set depends on **who is allowed to call it** — and that is a design decision, not a testing one. If a method is `public`, it is part of the contract and its behaviour should be pinned; if it is `package-private` or `private`, it is an **implementation detail** and a test that depends on it is a liability. The corollary, and the practical advice: **make the boundary explicit in the code** — a `public` API surface that is deliberately narrow, and the internals `private` or package-private. Then "test the behaviour, not the implementation" becomes a mechanical rule: *test everything that is public; do not test anything else.* The deeper point: the advice is really a **statement about encapsulation** — a well-encapsulated class has a small public surface, so a test written against it is automatically a behaviour test. A class with 40 public methods cannot be tested at the behaviour level, because its "behaviour" *is* 40 methods, and any refactoring of them breaks the test. So the advice and the design constraint are **the same thing**: *if the behaviour is hard to pin, the boundary is wrong.*

---

## 9. Counter-Questions & Traps

### CQ1. "You said coverage is a poor measure. Then why is it still the metric everyone reports?"

- **Naive:** "Because it is easy to measure."  ← *true, and it misses why it persists*
- **The trap:** The trap is to dismiss coverage as merely "easy". The real reason is that it is **a proxy for a property everyone wants** (confidence), and **a bad proxy is still a proxy** — a team with 30% coverage is probably under-tested, even if 100% coverage does not mean well-tested.
- **Expert answer:** The honest assessment. (1) **Coverage is a *necessary* condition, not a sufficient one** — untested code is *certainly* unverified, so low coverage is a reliable *smell*. But the converse does not hold: covered code may be verified by nothing. (2) **It is comparable across projects**, which is why managers like it — a mutation score is harder to benchmark. (3) **It is a *proxy for effort***, and effort correlates with coverage in most teams. (4) The **fix** is not to abandon coverage but to **use it as a floor and mutation as the ceiling**: coverage says "is anything tested here?" (a per-module check); mutation says "are the tests *effective*?" (a per-assertion check). And the *practical* recommendation: track the **mutation score** for the *domain* code (where the business risk is) and use coverage only as a coarse *smell detector* for the rest. The deeper lesson: *a metric becomes a target, and a target becomes a specification of what to optimise — so the fix is to measure the thing you actually care about (does a wrong answer get caught?) rather than the thing that is easy to count.*

### CQ2. "You said use a fake instead of a mock. But a fake is a second implementation — isn't that worse?"

- **Naive:** "A fake is more realistic."  ← *true, and it misses the real trade*
- **The trap:** The trap is to treat "fake vs mock" as a moral choice. The real trade is **two different kinds of risk**: a fake can **drift** from the real implementation (and the test then passes against behaviour that does not exist), while a mock is **always in sync with the interface** (the compiler tells you) but **couples the test to the interface's shape**.
- **Expert answer:** The precise trade, and how to manage each. (1) **The drift risk of a fake** is real — the fake's semantics can diverge from the real one (a real repository might do a case-insensitive lookup; the fake might not). The mitigations: (a) keep the fake **minimal** — implement only the methods the test needs, and **throw `UnsupportedOperationException`** for the rest, so a *new* method on the interface is a **compile error** in the fake rather than a silent behaviour difference; (b) the fake must not be used for the behaviour you are testing — the fake is for the *collaborator*, and the *system under test* is the real one. (2) **The coupling risk of a mock** is the *refactoring* cost. The mitigation: mock the **narrow port** rather than the wide collaborator. (3) The **resolution**: use a fake for an interface with **few methods** and a mock for a wide one, and — the key discipline — **prefer a narrow port**, so the question rarely arises. The deeper principle: *the fake-versus-mock decision is a decision about **where the risk lives** — the risk of the fake drifting, or the risk of the mock coupling. You cannot remove both; you can only choose, deliberately, and with the right tool for each.*

### CQ3. "You said never use `Thread.sleep()`. But what if I need to wait for something to happen?"

- **Naive:** "Use a `CountDownLatch`."  ← *correct, but it misses the general pattern*
- **The trap:** The trap is to present the latch as the only alternative. The general pattern is **waiting on a *condition*, not on a *duration*** — and there are several ways to express that, depending on what you are waiting for.
- **Expert answer:** The general principle and the toolkit. (1) **The problem with a sleep**: it is a *guess* about a duration. Too short → the test fails (flaky). Too long → the test is slow. And the guess's accuracy depends on the machine's load — so it is *most* wrong exactly where it matters (a loaded CI machine). (2) **The alternatives, by what you are waiting for**: (a) **a `CountDownLatch`** — a known number of events; (b) **a `CyclicBarrier`** — a rendezvous point; (c) **a `Future.get(timeout)`** — an async result (with a *timeout*, so the test fails cleanly rather than hanging); (d) **Awaitility** (`await().atMost(5, SECONDS).until(...)`) — a *polling* condition with a bounded wait, which is the most readable for "wait until the state is X"; (e) **an event listener / a callback** — the cleanest: the code under test *notifies* you, and you wait on a latch the callback counts down. (3) The key difference between (c) and (d): `get(timeout)` fails with a `TimeoutException` (a clear failure), while a sleep *silently* continues with the wrong state. **Always bound your wait.** (4) The exception: a sleep in a **load test** or a **chaos test**, where the point is to let things settle — but even there, prefer a condition.

### CQ4. "If the test name is the specification, how do you name a test for a bug fix?"

- **Naive:** "Name it after the issue."  ← *useful, but incomplete*
- **The trap:** The trap is to think the name must be unique or formal. The real requirement is that the name **states the behaviour that must hold** — and the issue reference is an *addition*, not a substitute.
- **Expert answer:** The anatomy of a good test name, and the bug-fix case. (1) The **three-part** shape: **the unit** + **the condition** + **the expected outcome** — e.g. `"a withdrawn account rejects a further withdrawal"`, `"an order with no items has a total of zero"`. (2) For a **bug fix**, the name should state the **regression** explicitly: `"a negative amount is rejected instead of creating a negative balance"` — the *before* ("instead of") is what makes it a regression test. And the JUnit 5 convention of **`@DisplayName`** lets you write a full sentence with the domain vocabulary, while the **method name** stays a stable identifier (so the IDE's "go to test" works). (3) **Do not** put the issue number in the *method* name (`testIssue1234`) — it carries no information about the behaviour and it becomes noise. Put it in a **comment**, a **commit message**, or a `@DisplayName` suffix. (4) The pragmatic rule: if you cannot write the sentence, you may not understand the behaviour — and that is worth knowing before you write the fix.

### CQ5. "You said mutation testing is better. Why doesn't everyone use it?"

- **Naive:** "It's slow."  ← *partly true, and it misses the deeper reason*
- **The trap:** The trap is to attribute it to cost. The deeper reason is that mutation testing **reports on the tests**, not on the code — and a report on your tests is a **harder thing to act on** than a coverage number, because it says "your tests are weak" rather than "your code is untested".
- **Expert answer:** The four reasons, and the honest recommendation. (1) **The cost** — mutation testing is genuinely slow (it re-runs the suite for every mutant). It belongs in a **nightly** job, not the inner loop. (2) **The tool maturity** — the PIT configuration is fiddly, and the *false positives* (a mutant killed only by an assertion that does not matter) need a human to triage. (3) **The cultural reason** — a mutation score is a **verdict on the test suite**, and a coverage number is a **verdict on the code**. Teams are far more comfortable with the latter. (4) **The scope** — you do not need it everywhere. The recommendation: (a) run it **nightly** on the **domain** code (where the business risk is); (b) use the report as a **guide** — it tells you *exactly* which untested boundary to cover; (c) track the score **over time** as a trend, not as a gate. And the pragmatic middle ground many teams adopt: **use coverage as a per-module floor** (to catch the obviously-untested code) and **mutation as a per-module report** (to find the weak assertions). The deeper lesson: *the metric you adopt determines what you optimise — so adopt the one that measures the thing you actually fear (a wrong answer reaching production), and accept that it is more expensive to interpret.*

### CQ6. "How do you test a class with no seams — no injection, and it news up its own collaborators?"

- **Naive:** "Use a mocking framework."  ← *it works, and it is the second-best answer*
- **The trap:** The trap is to treat the *absence of seams* as a testing problem. It is a **design** problem: a class that constructs its own collaborators cannot be tested in isolation, cannot have its collaborators substituted, and cannot be verified.
- **Expert answer:** The ladder, from least to most invasive. (1) **The pragmatic answer** — use a mocking framework's `mockConstruction` (Mockito 5) to intercept the constructed collaborator. It works, it requires no production change, and it is a *temporary* measure. (2) **The proper answer** — introduce a **seam**: make the constructor take the collaborator, with a convenience constructor that news up the default. This is a **small, safe, backward-compatible change**, and it is the step that makes the class testable *and* reusable. (3) **The better answer** — if the class constructs the collaborator *inside a method* (not a constructor), extract it into a field injected via the constructor. (4) The **deeper** observation: a class that news up its own collaborators is a class with **hidden dependencies** — the caller cannot see what it needs, cannot substitute it, and cannot be tested. The testability difficulty is the *symptom*; the *cause* is the coupling. And the **legacy** case: you cannot always change the production code (it is not yours, or it is too risky right now) — so the honest answer is a *layered* approach: use `mockConstruction` now, and file the refactoring. The discipline that matters: **do not let the mocking framework become the permanent answer** — every class that needs `mockConstruction` is a class with a design debt, and the debt should be tracked.

### CQ7. "What is the difference between a unit test and a component test? Why does the distinction matter?"

- **Naive:** "A unit test has no I/O; a component test has some."  ← *true, and it misses why the boundary is drawn there*
- **The trap:** The trap is to draw the line at "does it touch the network" — which is a *technical* line. The meaningful line is **which layer of the architecture the test exercises**, and that is a *design* line: the unit test exercises a **domain** component in isolation; the component test exercises a **component** (a class plus its real internal collaborators) with only the **ports** faked.
- **Expert answer:** The architectural reading, and why it matters. (1) The **hexagonal layering** gives the natural test levels: a **domain** object (no ports at all) → a **unit** test; an **application service** (with the ports faked) → a **component** test; an **adapter** (with a real database in a container) → an **integration** test. (2) The distinction matters because the **failure modes are different**: a unit-test failure is a *logic* bug (fast to diagnose, in your code); a component-test failure is an *orchestration* bug (the service wired the wrong port, or passed the wrong value); an integration-test failure is an *infrastructure* or *schema* bug. Knowing the layer tells you where to look. (3) The **practical** benefit of drawing the line architecturally: it makes the test *level* predictable from the *code* — a developer knows that a class in the domain layer will have only unit tests, so they know what the test suite will and will not cover. (4) And the rule that follows: **if a "unit" test needs a mock of a collaborator in the same module, the collaborator should be a port** — because a port is a boundary you *intend* to substitute, whereas an internal class is not.

### CQ8. "How do you decide what to do with a test that fails intermittently but you cannot reproduce locally?"

- **Naive:** "Add a retry."  ← *it hides the problem and it is the wrong fix*
- **The trap:** The trap is to reach for the **retry** — which is what most CI systems offer. A retry converts a *flaky test* into a *slow, unreliable test* that occasionally passes and occasionally fails, and it destroys the signal without fixing the cause. It also hides a real defect that may be a **heisenbug** in the production code.
- **Expert answer:** The triage, and the correct outcome. (1) **Never "fix" a flake with a retry** — it makes the suite slower and *less* trustworthy, and it hides a possible production defect. (2) **Quarantine immediately** — tag it (`@Tag("flaky")`), exclude it from the default run, and **run it nightly** so it still gets attention. (3) **Diagnose**: the usual causes, in order of frequency — a **timing assumption** (a sleep, a timeout, a race); **shared state** (a static field, a database row, a port); **ordering**; **the wall clock**; **randomness**; **resource exhaustion** (a connection pool under contention); and — the one people forget — **a real defect** (a genuine heisenbug in the production code, which the flake is *reporting*). (4) **Fix the cause**: replace the sleep with a latch; make the fixture isolated; inject the clock. (5) **Track it**: a flake count is a quality metric. And the deeper lesson: *a flaky test is a **message** about the system — either the test has a hidden dependency, or the production code has a race. Both are defects, and the retry throws the message away.*

### CQ9. "You said a test should have one reason to fail. But a test that sets up five things and asserts one thing is fine, isn't it?"

- **Naive:** "Yes, one assertion is fine."  ← *true, and it misses the distinction that matters*
- **The trap:** The trap is to confuse "one reason to fail" with "one assertion". A test with **five independent assertions** has **five** reasons to fail — and the *first* one masks the other four. The distinction that matters is between a **chain of dependent facts** and **independent facts**.
- **Expert answer:** The distinction, and the tool. (1) A **dependent** chain — "the order is created, therefore the total is X, therefore the summary says Y" — should be a **sequence of assertions**, because if the first fails, the later ones are *meaningless* (the setup failed). (2) **Independent** facts — "the id is right, the count is right, the total is right" — should be in an **`assertAll`**, because they are *all true simultaneously* and seeing them together is faster to diagnose. (3) The **mechanism** that makes the distinction automatic: put the *setup* in `@BeforeEach` (a failure there aborts the test before any assertion runs) and the *assertions* in `assertAll`. (4) The deeper reason the rule exists: **a test that reports one failure at a time costs a full build cycle per problem.** In a suite of 500 tests where 20 fail, the sequential version needs 20 iterations to fix them; the `assertAll` version needs 1. That is a *developer-time* argument, and it is the one that actually moves teams.

### CQ10. "If tests are so valuable, why do teams hate writing them?"
- **Naive:** "Because they are boring and slow."  ← *true, and it misses the systemic causes*
- **The trap:** The trap is to attribute it to the *nature* of testing. The real causes are **structural**: the tests are written **after** the design (so they are hard to write), they are **slow** (so they are not run), and they are **brittle** (so they are resented).
- **Expert answer:** The three systemic causes and their fixes — each of which is a *design* decision, not a discipline problem. (1) **"The code is hard to test"** → the code has hidden dependencies and no seams. The fix is **dependency injection** and a narrow public API — which is why the *design* and the *testability* are the same conversation. (2) **"The tests are slow"** → the fixture is expensive, or the suite is dominated by integration tests. The fix is the **pyramid**: fast unit tests in the inner loop, the slow ones in a separate task. (3) **"The tests break on every refactor"** → the tests assert on the implementation, or the collaborators are mocked at the wrong altitude. The fix is to test the *behaviour*, at the *boundary*. And the fourth, organisational cause: (4) **"Nobody owns the tests"** → they are written once and then *rot*. The fix is to make the test suite **part of the definition of done** — a PR is not done until the behaviour is covered — and to *delete* the tests that are not preventing a defect. The deeper lesson: *teams do not hate testing; they hate the friction the current design creates. Fix the design, and the tests get easier to write, faster to run, and more valuable — the "testing is boring" complaint is a symptom of a code structure that resists verification.*

---

## 10. Rapid-Fire MCQs

**Q1.** The test pyramid has the most tests at…
- A) The top (E2E)  B) **The bottom (unit)**  C) The middle  D) It is even

**Q2.** The AAA pattern stands for…
- A) Assert, Arrange, Act  B) **Arrange, Act, Assert**  C) Act, Assert, Arrange  D) All, Any, Assert

**Q3.** Coverage measures…
- A) Verification  B) **Execution**  C) Correctness  D) Performance

**Q4.** Mutation testing measures…
- A) Execution  B) **Whether a wrong implementation would be caught**  C) Speed  D) Style

**Q5.** A "flaky test" is one that…
- A) Fails always  B) **Passes and fails non-deterministically**  C) Is slow  D) Has no assertions

**Q6.** The most common cause of flakiness is…
- A) The framework  B) **Timing (`Thread.sleep`) and shared state**  C) The JDK version  D) The build tool

**Q7.** The FIRST principles of a good test include…
- A) Fast, Independent, Repeatable, Self-validating, Time-boxed  B) Fast, Small, Isolated, Documented, Typed  C) Fast, Few, Isolated, Realistic, Tested  D) Fast, Simple, Independent, Reliable, Thorough

**Q8.** To test code that uses the current time, you should…
- A) Mock the static `Instant.now()`  B) **Inject a `java.time.Clock`**  C) Use a tolerance  D) Freeze the system clock

**Q9.** A characterisation test asserts…
- A) The desired behaviour  B) **The current behaviour of existing code**  C) The performance  D) The coverage

**Q10.** A fake differs from a mock in that…
- A) A fake is faster  B) **A fake is a working implementation; a mock is a programmable double**  C) A mock is final  D) A fake needs a framework

**Q11.** You should mock a collaborator when it is…
- A) Deterministic and internal  B) **Slow, non-deterministic, or external**  C) A value object  D) Part of the domain

**Q12.** `assertAll` is used to…
- A) Run the tests in parallel  B) **Report every failure in one test**  C) Skip the failures  D) Set a timeout

**Q13.** `Thread.sleep()` in a test should be replaced by…
- A) A longer sleep  B) **A `CountDownLatch` or a bounded wait**  C) `assertTrue(true)`  D) A retry

**Q14.** A golden master / golden file is used for…
- A) Performance testing  B) **Pinning the current output of legacy code**  C) Load testing  D) Coverage

**Q15.** A test that depends on another test having run first is…
- A) Fine  B) **A hidden dependency — a defect**  C) Necessary  D) A data provider

**Q16.** The best way to make a test suite fast is…
- A) More CPUs  B) **A fast unit-test layer in the inner loop, slow tests in a separate task**  C) A smaller JVM heap  D) Caching

**Q17.** A test with no assertions…
- A) Is a placeholder  B) **Always passes, even if the behaviour is broken**  C) Is skipped  D) Is fast

**Q18.** The recommended structure for a test method is…
- A) Given-When-Then  B) **Arrange-Act-Assert**  C) Setup-Execute-Verify  D) Prepare-Run-Check

**Q19.** Contract testing (Pact) exists to catch…
- A) Unit-test gaps  B) **A break in a service-to-service contract, in the provider's CI**  C) Performance regressions  D) Flaky tests

**Q20.** A test that fails only in CI usually indicates…
- A) A broken CI  B) **A timing assumption, shared state, or a resource limit**  C) A bad test name  D) An outdated framework

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | The unit layer is the widest and the fastest. |
| 2 | **B** | Arrange the inputs, Act, Assert the outcome. |
| 3 | **B** | It measures whether the line ran, not whether it was verified. |
| 4 | **B** | A surviving mutant means a wrong implementation would not be caught. |
| 5 | **B** | The same code, a different outcome — the definition of a flake. |
| 6 | **B** | Timing assumptions and shared state are the two dominant causes. |
| 7 | **A** | Fast, Independent, Repeatable, Self-validating, Time-boxed. |
| 8 | **B** | An injected `Clock` — deterministic, thread-safe, no agent. |
| 9 | **B** | It pins what the code *does*, so a refactor is safe. |
| 10 | **B** | A fake has real behaviour; a mock is programmed and verified. |
| 11 | **B** | A boundary that is slow, flaky, or outside your control. |
| 12 | **B** | All the independent failures are reported in one run. |
| 13 | **B** | A latch or a bounded wait — a sleep is a guess. |
| 14 | **B** | A characterisation test of the current output. |
| 15 | **B** | A hidden dependency — the suite should expose it, not hide it. |
| 16 | **B** | A fast inner loop, and the slow suites in a separate task. |
| 17 | **B** | It exercises the code and verifies nothing. |
| 18 | **B** | Arrange, Act, Assert. |
| 19 | **B** | The breakage is caught in the provider's pipeline, not in production. |
| 20 | **B** | A timing assumption, shared state, or a resource limit under load. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE FIRST PRINCIPLES                                 │
│  Fast          ⚡ milliseconds — or it will not be run                 │
│  Independent   ↔ no shared state, no ordering                          │
│  Repeatable    ⟳ no clock, no randomness, no ordering                  │
│  Self-validating ✓ it passes or fails on its own                        │
│  Time-boxed    ⏱ a deadline, so a hang fails rather than hangs          │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE TEST LEVELS                                       │
│  LEVEL          WHAT IS REAL         SPEED   COUNT   CATCHES             │
│  ─────────────  ────────────────────  ──────  ─────   ────────────────   │
│  Unit           the class only        ⚡ ms    many    logic errors       │
│  Component      + internal collabs    ⚡ ms    tens    wiring errors      │
│  Integration    + real infra          🕐 s     tens    SQL/schema errors  │
│  E2E            the whole system      🕐 min   a few   contract errors    │
│                                                                          │
│  ⚠ THE PYRAMID IS A HEAP, NOT A LAW — for an IO-bound service the        │
│    "integration" layer may be the cheapest real coverage.                │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 10 TEST TRAPS                                    │
│  1. Thread.sleep() to synchronise      → a latch                        │
│  2. A dependency on the test order     → independent tests              │
│  3. The wall clock in an assertion     → inject a Clock                 │
│  4. Randomness without a seed          → a seeded Random                │
│  5. A test with no assertions          → assert on the outcome          │
│  6. Asserting on the mock              → assert on the result           │
│  7. A shared mutable fixture          → an instance field               │
│  8. Chasing a coverage number         → measure with mutation          │
│  9. A slow inner loop                 → a fast unit layer              │
│ 10. Leaving a flake in the main suite  → quarantine it and fix it        │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The house rules

| Rule | Why |
|---|---|
| **No `Thread.sleep()`** | The largest source of flakiness — use a latch or a bounded wait |
| **No shared mutable state** between tests | Independence ⇒ safe parallelism |
| **Inject the clock, the randomness and the id generator** | Determinism without a global mock |
| **Assert on the outcome**; verify the interaction only when it *is* the contract | A mock-only test can pass while the behaviour is wrong |
| **One reason to fail** per test | Faster to diagnose; `assertAll` for independent facts |
| **The name states the behaviour** | It is the specification |
| **AAA** in every test | Obvious, scannable, and it exposes a stray assertion |
| **Quarantine a flake; never retry it away** | A retry hides a possible production defect |
| **Delete a test that does not prevent a defect** | A test with no value is a maintenance cost |
| **Mutation-test the domain code nightly** | Coverage does not tell you the tests *work* |

### The CI shape

```text
   EVERY COMMIT — the GATE
   ☐ static analysis (SpotBugs, Error Prone)
   ☐ UNIT + COMPONENT tests, in parallel          ⚡ < 1 min
   ☐ CONTAINER tests (Testcontainers)             🕐 < 5 min
   ☐ the coverage report (a report, not a gate)

   NIGHTLY — the REPORT
   ☐ the full E2E suite
   ☐ the mutation score, tracked over time
   ☐ the flaky-test list (an owner per flake)
```

### Observability

| Metric | What it tells you |
|---|---|
| The suite's wall-clock time | The inner-loop budget |
| The flaky-test count | ⚠ A trust problem — the most important number |
| The mutation score (domain code) | Whether the tests actually *verify* |
| The surviving mutants | Exactly which boundary to cover next |
| The slowest test | The fixture to optimise |
| The assertion count per test | Too many independent facts? Use `assertAll` |

### The review checklist

```text
  ☐ Does the NAME state the behaviour (not "test1")?
  ☐ Is there a clear ARRANGE / ACT / ASSERT?
  ☐ Does it assert on an OUTCOME, not just on the mock?
  ☐ Is it deterministic (no sleep, no clock, no randomness)?
  ☐ Does it run independently of every other test?
  ☐ Would it still pass after a REFACTORING that preserves the behaviour?
  ☐ Does it fail when the behaviour BREAKS? (a mutant check)
  ☐ Is it FAST enough for the inner loop?
```

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The framework | [01 · JUnit 5](01-JUnit-Basics.md) |
| Mocking and fakes | [02 · Mockito & Mocking](02-Mockito-Mocking.md) |
| The other framework | [03 · TestNG](03-TestNG.md) |
| Dependency injection (the seam that makes testing possible) | [10 · SOLID Principles](../10-SOLID-Principles/01-SOLID-Principles.md) |
| Testing a serialised format | [17 · Serialization](../17-Serialization/03-Serialization-Best-Practices.md) |
| Testing concurrent code | [08 · Concurrency Problems](../08-Multithreading-Concurrency/04-Concurrency-Problems.md) |

---

**Next Chapter:** → [CORE_JAVA README](../README.md)
**Previous Chapter:** ← [03 · TestNG](03-TestNG.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
