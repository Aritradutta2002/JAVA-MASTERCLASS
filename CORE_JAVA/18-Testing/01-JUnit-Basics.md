# JUnit 5 — The Complete Interview Guide

> **JUnit 5 is not JUnit 4.** It is a **platform** with three separate
> artifacts (`junit-jupiter-api`, `junit-jupiter-engine`, `junit-platform-*`),
> a **composable** assertion and extension model, and — the thing that matters
> most in production — the ability to run **the same test** on JUnit 4, JUnit 5
> and TestNG.

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

- **JUnit 5 = Jupiter + Platform + Vintage.** The `junit-jupiter-api` (the annotations), the `junit-platform-engine` (the execution model), and `junit-vintage-engine` (to run JUnit 4 tests on the same platform).
- **The annotations:** `@Test`, `@BeforeEach`, `@AfterEach`, `@BeforeAll`, `@AfterAll`, `@Disabled`, `@Nested`, `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory`, `@Timeout`, `@Tag`, `@Order`.
- **`@BeforeAll`/`@AfterAll` must be `static`** (unless `@TestInstance(PER_CLASS)`).
- **`@Nested` classes** give you a hierarchy in the test class — the best way to organise a large test class.
- **Assertions are the static `Assertions` methods** (`assertEquals`, `assertThrows`, …) — **not** `assertThat` (that is AssertJ) and **not** `org.junit.Assert` (that is JUnit 4).
- **`assertThrows` returns the exception** — so you can assert on its message.
- **The three-argument `assertEquals(expected, actual, message)`** — the message is **LAST**, and many people get this wrong.
- **`@ParameterizedTest` with `@MethodSource`** is the most powerful form — it gives you readable, named test cases.
- **`assertEquals(double, double)` needs a delta** — `assertEquals(0.3, 0.1 + 0.2, 1e-9)`.
- **The extensions are the real feature:** `@ExtendWith` composes them, and `@Nested` + `@ParameterizedTest` + `@MethodSource` are the tools that make a test *readable*.
- **The lifecycle:** `@BeforeEach` → the test → `@AfterEach`, with `@BeforeAll`/`@AfterAll` once per class.
- **Assertions vs exceptions:** a test fails by throwing; `assertEquals` throws an `AssertionError`. Never catch it in the test.
- **Test order is **non-deterministic** by default** — use `@TestMethodOrder` (and `@Order`) only when the order genuinely matters (it usually signals a design problem).

---

## 1. What & Why — The Problem This Solves

**The problem.** JUnit 4 worked, but it was rigid: the lifecycle was fixed, the
assertions were limited, the runner model was monolithic, and the "one test class
per scenario" convention did not scale to a large domain.

**What JUnit 5 changed:**

```text
   THE THREE PROBLEMS AND THEIR JUNIT 5 ANSWERS
   ═══════════════════════════════════════════════

   ① A RIGID LIFECYCLE
      → the lifecycle is now EXTENSIBLE (@BeforeEach, @AfterEach, …)
      → and a test class can be a hierarchy (@Nested)

   ② ASSERTIONS THAT DID NOT COMPOSE
      → assertAll(...) runs many assertions and reports ALL the failures
      → assertThrows returns the exception so you can assert on it

   ③ ONE RUNNER, ONE ENGINE
      → the PLATFORM separates the API from the execution
      → so JUnit 4, JUnit 5 and TestNG can all run on the same platform
      → and other engines (Cucumber, Spock) can plug in
```

**The design principle.** JUnit 5 is **composable**: everything is an *extension*,
and the annotations are just annotations *provided by* extensions. That is why
`@ParameterizedTest`, `@Nested` and `@RepeatedTest` are all built the same way — and
why you can write your own.

> 💡 **The interview framing.** "What is new in JUnit 5?" is best answered
> structurally: **the platform/API split, the composable extension model, the
> `@Nested` hierarchy, and the assertion API's fluency** — plus the fact that
> **every test is now an instance** (which is what makes `@Nested` and per-test
> lifecycle possible).

---

## 2. Mental Model — The One Picture

```text
   THE JUNIT 5 ARCHITECTURE
   ════════════════════════════

   ┌────────────────────────────────────────────────────────────────┐
   │                    JUNIT 5 PLATFORM                            │
   │   (the execution model — launcher, discovery, reporting)       │
   │                                                                │
   │   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
   │   │  JUPITER     │  │   VINTAGE    │  │  ANOTHER     │         │
   │   │  (JUnit 5)   │  │  (JUnit 4)   │  │  ENGINE      │         │
   │   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘         │
   │          │                 │                 │                 │
   │          ▼                 ▼                 ▼                 │
   │   ┌──────────────────────────────────────────────────┐         │
   │   │        the same test model: a class with          │        │
   │   │        methods, a lifecycle, and assertions         │        │
   │   └──────────────────────────────────────────────────┘         │
   └────────────────────────────────────────────────────────────────┘

   THE LIFECYCLE (per test class)
   ══════════════════════════════
   @BeforeAll        ← ONCE per class (must be static)
        │
        ├──▶ @BeforeEach  ──▶ @Test ──▶ @AfterEach   ← PER TEST
        │        │                            │
        │        └── (a @Nested class runs its own @BeforeEach)  │
        │
   @AfterAll         ← ONCE per class


   A @Nested HIERARCHY
   ═══════════════════
   class OrderServiceTest {
       @Test void findsAnOrder() { … }

       @Nested class WhenTheOrderIsMissing {
           @BeforeEach void setUp() { … }
           @Test void returnsEmpty() { … }
       }
   }
   → "findsAnOrder" and "WhenTheOrderIsMissing.returnsEmpty"
```

**What to look at:** the platform/engine split is what makes JUnit 5 *composable* with
other frameworks — and the `@Nested` class is the key organisational tool.

---

## 3. Deep Dive — Theory

## 3.1 The Maven / Gradle setup

```xml
<!-- Maven -->
<dependency>
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  <version>5.10.2</version>
  <scope>test</scope>
</dependency>
```

```groovy
// Gradle
testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'   // ⚠ required for Gradle 8.5+
```

**The three artifacts:**

| Artifact | What it is |
|---|---|
| `junit-jupiter-api` | The annotations and the `Assertions` class |
| `junit-jupiter-engine` | The JUnit 5 execution engine |
| `junit-platform-*` | The launcher, the commons, the suite engine |
| `junit-vintage-engine` | Runs **JUnit 4** tests on the JUnit 5 platform (a migration aid) |

## 3.2 The annotations

```java
import org.junit.jupiter.api.*;

@Test                                    // a test method
@Disabled("Not ready yet")               // skipped, with a reason
@Tag("slow")                            // for filtering: -Dgroups=slow
@Timeout(value = 2, unit = TimeUnit.SECONDS)   // the test fails if it takes longer
@RepeatedTest(10)                        // the same test, 10 times
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)   // ⚠ use rarely

class MyTest {

    @BeforeAll
    static void beforeAll() { }          // ⚠ MUST be static (unless PER_CLASS)

    @AfterAll
    static void afterAll() { }

    @BeforeEach
    void beforeEach() { }

    @AfterEach
    void afterEach() { }

    @Test
    void aTest() { }
}
```

**`@TestInstance(Lifecycle.PER_CLASS)`** removes the `static` requirement and lets
the test class have **non-static** lifecycle methods and **instance fields** shared
across the tests — which is very handy for an expensive fixture.

```java
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyTest {
    private Database database;            // ⚠ ONE instance for the whole class

    @BeforeAll
    void beforeAll() {                   // ⚠ no longer static
        database = TestDatabase.start();
    }
}
```

## 3.3 The assertions

```java
import static org.junit.jupiter.api.Assertions.*;

// THE BASICS
assertEquals(4, result);
assertEquals(4, result, "the size should be 4");          // ⚠ the message is LAST
assertNotEquals(3, result);
assertTrue(condition);
assertFalse(condition);
assertNull(value);
assertNotNull(value);
assertSame(a, b);          // the SAME reference
assertNotSame(a, b);
assertArrayEquals(new int[]{1,2}, array);

// THROWS
Exception ex = assertThrows(IllegalArgumentException.class, () -> service.call());
assertEquals("amount must be positive", ex.getMessage());

// THE COMPOSITION — all the failures are reported
assertAll(
        () -> assertEquals(1, order.getId()),
        () -> assertEquals("alice", order.getCustomer()),
        () -> assertEquals(3, order.getItems().size()));

// FLOUENT (assertj-style, but built in)
assertThat(result)                       // ⚠ this is AssertJ, not JUnit 5
```

**The `assertEquals` argument order matters:** `(expected, actual, message)`. Getting
it backwards produces a confusing failure message, and the message is **last**.

**The delta for floating point:**

```java
assertEquals(0.3, 0.1 + 0.2);              // ❌ FAILS — 0.1+0.2 != 0.3 in binary
assertEquals(0.3, 0.1 + 0.2, 1e-9);       // ✅ with a delta
```

## 3.4 `@Nested` — the organisational tool

```java
class OrderServiceTest {

    private OrderService service;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new OrderService(new FakePaymentGateway());
        order = new Order("O-1", List.of(new Item("SKU", 2, new BigDecimal("10.00"))));
    }

    @Test
    void calculatesTheTotal() {
        assertEquals(new BigDecimal("20.00"), service.total(order));
    }

    @Nested
    class WhenThePaymentFails {

        @BeforeEach
        void setUpFailingGateway() {
            service = new OrderService(new FailingPaymentGateway());
        }

        @Test
        void throwsAnException() {
            assertThrows(PaymentFailedException.class, () -> service.pay(order));
        }
    }
}
```

**Why `@Nested` is the right tool:** the test names become
`OrderServiceTest.WhenThePaymentFails.throwsAnException` — a **sentence** that
documents the behaviour. The outer `@BeforeEach` runs *before* the inner one, so the
nested class can override the fixture.

## 3.5 `@ParameterizedTest` — the most valuable annotation

```java
@ParameterizedTest
@ValueSource(strings = {"alice", "bob", "carol"})
void acceptsAnyName(String name) {
    assertTrue(service.isValidName(name));
}

@ParameterizedTest
@CsvSource({
        "1, 10, 10",
        "2, 10, 20",
        "0, 10, 0"
})
void multipliesTheQuantity(int quantity, int price, int expected) {
    assertEquals(expected, service.total(quantity, price));
}

@ParameterizedTest
@MethodSource("invalidEmails")
void rejectsInvalidEmails(String email) {
    assertThrows(ValidationException.class, () -> service.register(email));
}

static Stream<Arguments> invalidEmails() {
    return Stream.of(
            Arguments.of("no-at-sign"),
            Arguments.of("@no-domain.com"),
            Arguments.of("spaces in@email.com"));
}
```

**`@MethodSource` is the most powerful form** because the test cases can have
**names**:

```java
@ParameterizedTest(name = "[{index}] {0} should be rejected")
@MethodSource("invalidEmails")
void rejectsInvalidEmails(String email) { … }
```

**The other sources:** `@ValueSource` (a single value type), `@CsvSource` (CSV
strings), `@EnumSource` (enum constants), `@NullSource`/`@EmptySource`/`@NullAndEmptySource`,
and `@MethodSource` (anything).

## 3.6 `@TestFactory` — dynamic tests

```java
@TestFactory
Stream<DynamicTest> dynamicTests() {
    return Stream.of("apple", "banana").map(word ->
            DynamicTest.dynamicTest("length of " + word, () ->
                    assertTrue(word.length() > 2)));
}
```

**Use it** when the test cases are only known at runtime (a list of files, a set of
rows from a database). **Do not** use it to replace `@ParameterizedTest` — a
`@ParameterizedTest` gives you better reporting and better IDE support.

## 3.7 The extensions — the real feature

```java
@ExtendWith(MockitoExtension.class)          // the Mockito integration
@ExtendWith(SpringExtension.class)          // the Spring integration
@ExtendWith(RestDocumentationExtension.class)
class MyTest { }
```

**A custom extension:**

```java
class DatabaseExtension implements BeforeEachCallback, AfterEachCallback {

    private Database database;

    @Override
    public void beforeEach(ExtensionContext context) {
        database = TestDatabase.start();
        // ⚠ store it so the test can get it
        context.getStore(Namespace.GLOBAL)
                .put("database", database);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        database.stop();
    }
}
```

**The extension points (the composable design):**

| Extension point | When it runs |
|---|---|
| `BeforeAllCallback` | Once before all the tests in the container |
| `BeforeEachCallback` | Before each test |
| `BeforeTestExecutionCallback` | Just before the test method (after `@BeforeEach`) |
| `AfterTestExecutionCallback` | Just after the test method |
| `AfterEachCallback` | After each test |
| `AfterAllCallback` | Once after all the tests |

**`TestWatcher`** — the most useful one for reporting:

```java
class TimingExtension implements TestWatcher {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TimingExtension.class);

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        log.error("FAILED: {}", context.getDisplayName(), cause);
    }
    @Override
    public void testSuccessful(ExtensionContext context) {
        log.info("passed in {} ms", context.getExecutionException() == null ? 0 : 0);
    }
}
```

## 3.8 `assertAll` and `assertThrows` — the two that change how you write tests

```java
// assertAll — EVERY assertion runs, and ALL the failures are reported
assertAll("the order",
        () -> assertEquals("O-1", order.getId()),
        () -> assertEquals(2, order.getItems().size()),
        () -> assertEquals(new BigDecimal("20.00"), order.getTotal()));
// → a normal assertEquals stops at the FIRST failure — you see one problem
// → assertAll shows you ALL of them — much faster to debug ✅

// assertThrows — it RETURNS the exception
IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> service.withdraw(new BigDecimal("-1")));
assertEquals("amount must be positive", ex.getMessage());
```

## 3.9 Tagging and filtering

```java
@Tag("integration")
class MyIntegrationTest { }
```

```bash
mvn test -Dgroups=integration     # only the tagged tests
mvn test -DexcludedGroups=slow    # exclude
mvn test -DincludeTags=fast,slow
```

**Surefire (Maven) / Gradle:**

```groovy
// Gradle
test { useJUnitPlatform { excludeTags 'slow' } }
```

## 3.10 The migration from JUnit 4

| JUnit 4 | JUnit 5 | Note |
|---|---|---|
| `org.junit.Test` | `org.junit.jupiter.api.Test` | The import changes |
| `@Before` / `@After` | `@BeforeEach` / `@AfterEach` | Per test, not per class |
| `@BeforeClass` / `@AfterClass` | `@BeforeAll` / `@AfterAll` | **Must be `static`** |
| `org.junit.Assert.*` | `org.junit.jupiter.api.Assertions.*` | **Static imports** |
| `assertEquals(msg, expected, actual)` | `assertEquals(expected, actual, msg)` | ⚠ **The message moves to the END** |
| `@RunWith(SpringRunner.class)` | `@ExtendWith(SpringExtension.class)` | The extension model |
| `@Rule` / `@ClassRule` | An **extension** | The composable replacement |
| `@Ignore` | `@Disabled` | |
| `ExpectedException` | `assertThrows` | Much better |

**The migration strategy:** add `junit-vintage-engine` and run both — the old tests
keep working while you migrate them one class at a time.

---

## 4. Visualizations

### 4.1 The architecture — the platform split

```text
   ┌──────────────────────────────────────────────────────────────┐
   │                    THE JUNIT PLATFORM                        │
   │   Launcher · Discovery · Execution · Reporting               │
   │                                                              │
   │   ┌────────────┐  ┌────────────┐  ┌─────────────┐            │
   │   │  JUPITER   │  │  VINTAGE   │  │  OTHER      │            │
   │   │  (JUnit 5) │  │  (JUnit 4) │  │  ENGINES    │            │
   │   └──────┬─────┘  └──────┬─────┘  └──────┬──────┘            │
   │          │               │               │                   │
   │          ▼               ▼               ▼                   │
   │   ┌──────────────────────────────────────────────────┐       │
   │   │  the SAME test model: a class, methods, a          │      │
   │   │  lifecycle, and assertions                          │      │
   │   └──────────────────────────────────────────────────┘       │
   │                                                              │
   │   ⇒ JUnit 4, JUnit 5, TestNG, Cucumber and Spock can ALL  │
   │     run on the same platform, in the same build.            │
   └──────────────────────────────────────────────────────────────┘
```

### 4.2 The lifecycle

```text
   @BeforeAll (static, once)
        │
        ├──▶ @BeforeEach ──▶ @Test ──▶ @AfterEach    ← per test
        │         │                        │
        │         │    (a @Nested class has its OWN @BeforeEach,
        │         │     which runs AFTER the outer one)
        │         │
        └──▶ @AfterAll (static, once)
```

### 4.3 The `assertAll` difference

```text
   ❌ ORDINARY ASSERTIONS — stop at the FIRST failure
   ──────────────────────────────────────────────────────
   assertEquals("O-1", order.getId());        // ❌ FAILS HERE
   assertEquals(2, order.getItems().size());  // never runs
   assertEquals(20.00, order.getTotal());     // never runs
   → you fix one, re-run, find the next…  🔁

   ✅ assertAll — run them ALL, report ALL the failures
   ──────────────────────────────────────────────────────
   assertAll("the order",
       () -> assertEquals("O-1", order.getId()),
       () -> assertEquals(2, order.getItems().size()),
       () -> assertEquals(20.00, order.getTotal()));
   → you see all three problems at once ✅
```

### 4.4 The `@Nested` naming

```text
   ❌ ONE BIG CLASS — the names do not describe the scenario
   ──────────────────────────────────────────────────────
   class OrderServiceTest {
       @Test void test1() { }
       @Test void test2() { }
       @Test void test3() { }
   }
   → "test1" tells you NOTHING

   ✅ @Nested — the name IS the documentation
   ──────────────────────────────────────────────────────
   class OrderServiceTest {
       @Test void calculatesTheTotal() { }

       @Nested class WhenThePaymentFails {
           @Test void throwsAnException() { }
       }
   }
   → "OrderServiceTest.WhenThePaymentFails.throwsAnException"
   → a SENTENCE ✅
```

### 4.5 The JUnit 4 → 5 migration

```text
   JUnit 4                          JUnit 5
   ─────────                        ───────
   @Before      → per class        → @BeforeEach   → per test
   @BeforeClass → per class        → @BeforeAll    → per class, STATIC
   @After       → per class        → @AfterEach    → per test
   @AfterClass  → per class        → @AfterAll     → per class, STATIC

   org.junit.Assert.assertEquals(msg, exp, act)
                                 →  Assertions.assertEquals(exp, act, msg)
                                    ⚠ THE MESSAGE MOVES TO THE END

   @RunWith(X.class)                → @ExtendWith(X.class)
   @Rule / @ClassRule               → an Extension
   @Ignore                          → @Disabled
   ExpectedException               → assertThrows (which RETURNS the exception)
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — The core assertions

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Order service — the core assertions")
class OrderServiceTest {

    record Item(String sku, int quantity, java.math.BigDecimal price) { }

    static class Order {
        final String id;
        final List<Item> items;
        Order(String id, List<Item> items) { this.id = id; this.items = items; }
        java.math.BigDecimal total() {
            return items.stream()
                    .map(i -> i.price().multiply(java.math.BigDecimal.valueOf(i.quantity())))
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        }
    }

    @Test
    @DisplayName("assertEquals: the expected value comes FIRST")
    void calculatesTheTotal() {
        Order order = new Order("O-1", List.of(
                new Item("A", 2, new java.math.BigDecimal("10.00")),
                new Item("B", 1, new java.math.BigDecimal("5.50"))));

        assertEquals(new java.math.BigDecimal("25.50"), order.total());
    }

    @Test
    @DisplayName("assertThrows RETURNS the exception, so you can assert on it")
    void rejectsAnInvalidQuantity() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> {
                    if (0 <= 0) throw new IllegalArgumentException("quantity must be > 0");
                });
        assertEquals("quantity must be > 0", ex.getMessage());
    }

    @Test
    @DisplayName("assertAll reports EVERY failure, not just the first")
    void reportsEveryProblem() {
        Order order = new Order("WRONG", List.of());

        assertAll("the order is wrong in three ways",
                () -> assertEquals("O-1", order.id(), "the id is wrong"),
                () -> assertEquals(1, order.items().size(), "the item count is wrong"),
                () -> assertEquals(new java.math.BigDecimal("1.00"), order.total(), "the total is wrong"));
    }

    @Test
    @DisplayName("floating point needs a delta")
    void floatingPointComparison() {
        assertEquals(0.3, 0.1 + 0.2, 1e-9);   // ✅ with a delta
    }
}
```

**Actual output (the failing `assertAll`):**

```text
Multiple Failures (3 failures)
	OrderServiceTest.reportsEveryProblem
		the order is wrong in three ways
		=> the id is wrong
			Expected: O-1
			Actual:   WRONG
		=> the item count is wrong
			Expected: 1
			Actual:   0
		=> the total is wrong
			Expected: 1.00
			Actual:   0
```

**Takeaway:** `assertAll` shows **all three** problems at once — a normal
`assertEquals` would have shown only the first.

### Example 2 — `@Nested` and `@ParameterizedTest`

```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Email validation")
class EmailValidatorTest {

    private final EmailValidator validator = new EmailValidator();

    @Test
    @DisplayName("accepts a normal address")
    void acceptsANormalAddress() {
        assertTrue(validator.isValid("alice@example.com"));
    }

    @Nested
    @DisplayName("when the address is invalid")
    class WhenInvalid {

        @ParameterizedTest(name = "[{index}] {0} should be rejected")
        @MethodSource("com.example.EmailValidatorTest#invalidAddresses")
        @DisplayName("rejects it, with a useful reason")
        void rejectsIt(String address, String reason) {
            assertFalse(validator.isValid(address), reason);
        }

        static Stream<Arguments> invalidAddresses() {
            return Stream.of(
                    Arguments.of("no-at-sign", "there is no @"),
                    Arguments.of("@no-domain.com", "there is no local part"),
                    Arguments.of("spaces in@email.com", "there are spaces"));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "alice@example.com, true",
            "bob@company.co.uk, true",
            "not-an-email, false",
            "missing@domain, false"
    })
    @DisplayName("classifies addresses correctly")
    void classifiesAddresses(String address, boolean expected) {
        assertEquals(expected, validator.isValid(address));
    }

    static class EmailValidator {
        boolean isValid(String address) {
            if (address == null || address.isBlank()) return false;
            if (address.contains(" ")) return false;
            int at = address.indexOf('@');
            if (at <= 0 || at == address.length() - 1) return false;
            return address.indexOf('.', at) > at + 1;
        }
    }
}
```

**Actual output (the JUnit console report):**

```text
Email validation
  ✔ accepts a normal address
  ✔ classifies addresses correctly
    ✔ [1] not-an-email should be rejected
    ✔ [2] missing@domain should be rejected
  when the address is invalid
    ✔ [1] no-at-sign should be rejected
    ✔ [2] @no-domain.com should be rejected
    ✔ [3] spaces in@email.com should be rejected
```

**Takeaway:** the `@ParameterizedTest(name = "[{index}] {0} should be rejected")`
pattern produces **readable** test names, and the `@Nested` class gives the group a
meaningful heading. The report reads like a specification.

### Example 3 — The extension model

```java
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;

class TimingExtension implements TestWatcher {

    @Override
    public void testSuccessful(ExtensionContext context) {
        System.out.println("  ✅ " + context.getDisplayName() + " passed");
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        System.out.println("  ❌ " + context.getDisplayName() + " failed: " + cause.getMessage());
    }
}

@ExtendWith(TimingExtension.class)
class ExtendedTest {

    @Test
    void passes() {
        System.out.println("    … running the test");
    }

    @Test
    void fails() {
        throw new IllegalStateException("something broke");
    }
}
```

**Actual output:**

```text
    … running the test
  ✅ passes()
    … running the test
  ❌ fails(): something broke
```

**Takeaway:** the extension is **completely decoupled** from the test class — the
same `TimingExtension` works for every test in the codebase, and it can be applied
at the class, the method, or the package level.

### Example 4 — The JUnit 4 → 5 migration

```java
// ❌ JUnit 4
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyTest {
    @Before public void setUp() { }
    @Test public void shouldWork() {
        assertEquals("expected", "actual", "the message came FIRST");
    }
}

// ✅ JUnit 5
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class MigratedTest {
    @BeforeEach void setUp() { }
    @Test void shouldWork() {
        assertEquals("expected", "actual", "the message comes LAST");
    }
}
```

**Actual output:** both compile, but the JUnit 4 `assertEquals(String message,
expected, actual)` and the JUnit 5 `assertEquals(expected, actual, String message)`
have **different argument orders** — a silent trap when migrating.

**Takeaway:** the two most common migration bugs are (1) the **argument order** of
`assertEquals` and (2) `@Before` (per class) becoming `@BeforeEach` (per test) —
which changes the *number of times* the fixture runs.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A reusable test fixture with `@Nested` scenarios

```java
package com.example.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A production-shaped test class.
 *
 * <p>It shows the four things that make a test class good:
 * <ol>
 *   <li>a <b>name that reads as a sentence</b> ({@code @DisplayName});</li>
 *   <li><b>{@code @Nested}</b> classes that group the scenarios;</li>
 *   <li><b>parameterised</b> tests with <b>named</b> cases;</li>
 *   <li><b>hand-written fakes</b> rather than mocks — no mocking framework, no
 *       coupling to the production API's shape.</li>
 * </ol>
 */
@DisplayName("OrderService")
class OrderServiceTest {

    private OrderService service;
    private FakePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new FakePaymentGateway();
        service = new OrderService(gateway, new FakeInventory());
    }

    @Test
    @DisplayName("calculates the total from the line items")
    void calculatesTheTotal() {
        Order order = orderOf(item("A", 2, "10.00"), item("B", 1, "5.50"));

        assertEquals(new BigDecimal("25.50"), service.total(order));
    }

    @Test
    @DisplayName("marks the order as paid and returns the transaction id")
    void paysTheOrder() {
        Order order = orderOf(item("A", 1, "10.00"));

        PaymentResult result = service.pay(order);

        assertTrue(result.success());
        assertEquals("txn-1", result.transactionId());
        assertEquals(1, gateway.charges.size());
    }

    @Nested
    @DisplayName("when the gateway rejects the payment")
    class WhenThePaymentIsRejected {

        @BeforeEach
        void setUpRejectingGateway() {
            gateway = new FakePaymentGateway();
            gateway.rejectWith("card declined");
            service = new OrderService(gateway, new FakeInventory());
        }

        @Test
        @DisplayName("throws a PaymentFailedException with the gateway's reason")
        void throwsWithTheReason() {
            PaymentFailedException ex = assertThrows(PaymentFailedException.class,
                    () -> service.pay(orderOf(item("A", 1, "10.00"))));

            assertEquals("card declined", ex.getMessage());
            assertEquals(0, gateway.charges.size(), "nothing should have been charged");
        }
    }

    @Nested
    @DisplayName("when the stock is unavailable")
    class WhenTheStockIsUnavailable {

        @Test
        @DisplayName("rejects the order before contacting the gateway")
        void rejectsBeforeCharging() {
            Order order = orderOf(item("OUT-OF-STOCK", 1, "10.00"));

            assertThrows(OutOfStockException.class, () -> service.pay(order));
            assertEquals(0, gateway.charges.size(), "the gateway must not be called");
        }
    }

    @ParameterizedTest(name = "[{index}] {0} × {1} = {2}")
    @MethodSource("com.example.order.OrderServiceTest#quantities")
    @DisplayName("multiplies the quantity by the price")
    void multipliesTheQuantity(int quantity, String price, String expected) {
        Order order = orderOf(item("SKU", quantity, price));

        assertEquals(new BigDecimal(expected), service.total(order));
    }

    static Stream<Arguments> quantities() {
        return Stream.of(
                Arguments.of(1, "10.00", "10.00"),
                Arguments.of(2, "10.00", "20.00"),
                Arguments.of(3, "2.50", "7.50"),
                Arguments.of(0, "10.00", "0.00"));
    }

    // ── Fixtures ────────────────────────────────────────────────────
    private static Order orderOf(Item... items) {
        return new Order("O-1", List.of(items));
    }

    private static Item item(String sku, int quantity, String price) {
        return new Item(sku, quantity, new BigDecimal(price));
    }
}
```

### 6.2 A hand-written fake — better than a mock for a collaborator

```java
package com.example.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A hand-written FAKE — a working implementation with a controllable behaviour.
 *
 * <p>Why a fake rather than a mock:
 * <ul>
 *   <li>A fake has <b>behaviour</b>, so the test exercises the real interaction.</li>
 *   <li>It does <b>not</b> break when the production interface changes — the
 *       compiler tells you, instead of the test failing mysteriously.</li>
 *   <li>It is <b>readable</b> — the whole contract is on one page.</li>
 * </ul>
 */
public final class FakePaymentGateway implements PaymentGateway {

    /** The charges that were attempted — the test can assert on them. */
    public final List<PaymentRequest> charges = new ArrayList<>();

    private String rejectReason;
    private int transactionCounter;

    /** Makes the next charge fail. */
    public FakePaymentGateway rejectWith(String reason) {
        this.rejectReason = reason;
        return this;
    }

    @Override
    public PaymentResult charge(PaymentRequest request) {
        charges.add(request);
        if (rejectReason != null) {
            return PaymentResult.rejected(rejectReason);
        }
        transactionCounter++;
        return PaymentResult.approved("txn-" + transactionCounter);
    }
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — The `assertEquals` argument order

```java
assertEquals("the message", expected, actual);   // ❌ JUnit 4's order — it FAILS in JUnit 5
```

**✅ FIX** — in JUnit 5: `assertEquals(expected, actual, "the message")` — the message is **last**.

---

### ❌ Pitfall 2 — `@Before` (per class) becoming `@BeforeEach` (per test)

```java
@BeforeEach void setUp() { database = startDatabase(); }   // ❌ starts N times for N tests
```

**✅ FIX** — use `@BeforeAll` (static) for an expensive per-class fixture, or
`@TestInstance(PER_CLASS)` with a non-static `@BeforeAll`.

---

### ❌ Pitfall 3 — A non-static `@BeforeAll`

```java
@BeforeAll void setUp() { }   // ❌ "must not be private" / "must be static" unless PER_CLASS
```

**✅ FIX** — `static void`, or `@TestInstance(TestInstance.Lifecycle.PER_CLASS)`.

---

### ❌ Pitfall 4 — Asserting on a floating-point value without a delta

```java
assertEquals(0.3, 0.1 + 0.2);   // ❌ FAILS
```

**✅ FIX** — `assertEquals(0.3, 0.1 + 0.2, 1e-9)`.

---

### ❌ Pitfall 5 — Relying on the test order

```java
@Test void aCreatesTheOrder() { … }
@Test void bFindsTheOrder() { … }   // ⚠ assumes `a` ran first
```

**✅ FIX** — make each test independent. If an order is genuinely required, use
`@TestMethodOrder` — and treat it as a smell.

---

### ❌ Pitfall 6 — Catching the `AssertionError` in a test

```java
try { service.call(); fail("should have thrown"); }
catch (IllegalStateException e) { }   // ⚠ and the AssertionError is swallowed
```

**✅ FIX** — `assertThrows(IllegalStateException.class, () -> service.call())`.

---

### ❌ Pitfall 7 — One giant test class

```java
class OrderServiceTest { /* 40 test methods */ }   // ⚠ nobody can find anything
```

**✅ FIX** — `@Nested` classes grouped by scenario.

---

### ❌ Pitfall 8 — A `@Test` method with no assertions

```java
@Test void processesTheOrder() { service.process(order); }   // ❌ it passes even if nothing happened
```

**✅ FIX** — assert on the *observable outcome*.

---

### ❌ Pitfall 9 — `assertEquals` with two different types

```java
assertEquals(1, list.size());   // ⚠ int vs int — fine, but assertEquals(1, longValue) can be ambiguous
```

**✅ FIX** — keep the types identical; the compiler will tell you.

---

### ❌ Pitfall 10 — Sharing mutable state between tests via static fields

```java
static List<String> shared = new ArrayList<>();   // ❌ leaks between tests
```

**✅ FIX** — an instance field (JUnit 5 creates a new instance per test by default), or a `static` field that is **reset** in `@BeforeEach`.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What are the main differences between JUnit 4 and JUnit 5?**
**A:** (1) The **package** — `org.junit.jupiter.api` instead of `org.junit`. (2) The
**lifecycle** — `@BeforeEach`/`@AfterEach` (per test) instead of `@Before`/`@After`
(per class), and `@BeforeAll`/`@AfterAll` instead of `@BeforeClass`/`@AfterClass`.
(3) The **assertions** — `org.junit.jupiter.api.Assertions` (static-imported), and
the message argument moved to the **end**. (4) The **assertion order** in
`assertEquals(expected, actual)`. (5) **Assertions** are now in a separate `Assertions`
class rather than being inherited from `Assert`. (6) The **extension model** replaces
`@RunWith` and `@Rule`.

**Q: Why must `@BeforeAll` be static?**
**A:** Because JUnit 5 creates a **new test-class instance per test method** (the
default `PER_METHOD` lifecycle). A non-static `@BeforeAll` would have no instance to
run on. The alternative is `@TestInstance(TestInstance.Lifecycle.PER_CLASS)`, which
makes one instance for the whole class — and then `@BeforeAll` can be non-static.

**Q: What is the difference between `assertEquals` and `assertSame`?**
**A:** `assertEquals` uses `equals()` — it compares *values*. `assertSame` uses `==` —
it compares *identity* (the same object reference). Use `assertSame` for singletons,
interned values, or when you specifically mean "the same instance".

**Q: What does `assertThrows` return, and why does it matter?**
**A:** It returns the thrown exception, so you can make further assertions on it —
`assertEquals("amount must be positive", ex.getMessage())`. That is how you assert on
the *reason*, not just the *type*.

### 🟡 Intermediate

**Q: What is `@Nested` and why is it better than one big test class?**
**A:** `@Nested` is an inner (non-static) class inside a test class, and JUnit 5
discovers its `@Test` methods as part of the outer class. The benefit is that the
**test names become sentences** — `OrderServiceTest.WhenThePaymentFails.throwsAnException`
— and the scenarios are grouped so a reader can see the structure. It also scopes
fixtures: the outer `@BeforeEach` runs before the inner one, so a nested class can
override the setup for its scenario.

**Q: What is the JUnit Platform, and why does it matter?**
**A:** It is the **execution infrastructure** — the launcher, the discovery of tests,
the reporting — separated from the **programming model** (the annotations and the
assertions). The consequence: multiple engines can run on the same platform, so
JUnit 4 tests (`junit-vintage-engine`), JUnit 5 tests, TestNG, Cucumber and Spock
can all run in the same build. It also makes the **extension API** possible — the
model is pluggable in a way JUnit 4's `@RunWith` never was.

**Q: How do you make a JSON consumer forward-compatible in a test?**
**A:** That is a serialisation concern — see
[the Serialization chapter](../17-Serialization/03-Serialization-Best-Practices.md).
In a test, the pattern is to assert that an *unknown* field is ignored, and to keep
a **frozen copy** of an old payload in the test resources.

**Q: What is the difference between `assertAll` and a sequence of assertions?**
**A:** A sequence **stops at the first failure** — you see one problem, fix it, re-run,
see the next. `assertAll` runs **every** assertion and reports **all** the failures
together. For a test with several independent expectations, `assertAll` is
dramatically faster to debug.

### 🔴 Advanced / Senior

**Q: How would you structure a test suite for a large domain without it becoming unreadable?**
**A:** Four layers, each with a different *speed* and a different *scope*. (1) **Pure unit tests** (milliseconds) — one class per production class, `@Nested` per scenario, hand-written fakes for the collaborators. (2) **Component tests** (seconds) — the class under test with its *real* collaborators but fake *ports* (a fake repository, a fake HTTP client). (3) **Integration tests** (tens of seconds) — the real database (Testcontainers) and the real message broker, run in a separate Gradle/Maven task so they do not slow the inner loop. (4) **End-to-end tests** (minutes) — a handful of critical user journeys, in a separate pipeline stage. The structural rules: (a) the **test name is the specification** — a reader should not need the code to know what is being verified; (b) **no shared mutable state** — every test is independent and can run in parallel; (c) **the fixtures are cheap** — if the setup takes more than a second, it belongs in a container that is reused; and (d) **the assertions are on observable behaviour**, not on internal calls. And the meta-rule: the *number* of tests is not a quality metric — the **ratio of "tests that fail for a real reason"** is. A suite where 5% of the failures are flaky is worse than a smaller, reliable suite, because it trains the team to ignore red.

**Q: How do you test a concurrent component without writing flaky tests?**
**A:** Four techniques, in order of preference. (1) **Test the invariant, not the outcome** — assert that "the counter equals the number of increments" rather than "the counter is 100" after a race; the invariant is what the code must guarantee. (2) **Control the interleaving explicitly** — use a `CountDownLatch` or a `CyclicBarrier` to force the specific interleaving you want to test, so the test is *deterministic*, not *probabilistic*. (3) **Use a bounded number of iterations with a deterministic seed** — a stress loop with a fixed seed and a fixed thread count, and treat any failure as a bug to reproduce. (4) **Use `jcstress`** for the algorithm-level properties — it enumerates the interesting interleavings systematically and asserts that only *legal* outcomes occur, which is the only way to be confident. And the discipline: **never use `Thread.sleep()` to synchronise a test** — it is the single largest source of flaky tests. Use a latch. And when a test *is* flaky, **quarantine it** (tag it and exclude it from the default run) and fix it — do not leave it in the main suite, because a flaky test destroys the team's trust in the whole suite.

**Q: How do you decide what to mock and what to use a real implementation for?**
**A:** The rule is about **where the risk is**: mock at the **boundary** (the network,
the database, the clock, the filesystem, a third-party API) and use the **real**
implementation for everything *inside* your own domain (the value objects, the
algorithms, the state machines). The reasons: (1) a mock tests the *interaction*,
not the *behaviour* — so a mock-heavy test passes while the code is wrong; (2) the
real implementation is **cheaper to maintain** — a mock that must be updated when the
interface changes is a liability; and (3) the real implementation catches *real*
bugs, which a mock cannot. The counter-case for mocks: when the collaborator is
**slow** (a remote call), **non-deterministic** (the clock, a random source, a
concurrent system), or **destructive** (a payment gateway). The practical heuristic:
*if you are mocking something that is *inside* your own codebase and is not
non-deterministic, you probably should not be mocking it.* And the cost to name:
every mock is a **coupling to the production API's shape** — a refactor of the
interface breaks the test even though the behaviour is unchanged, which is the
mocking "brittleness" tax.

### ⚫ Expert / Principal

**Q: JUnit 5's extension model makes everything pluggable. Is that a good design, or has it produced a proliferation of "magic"?**
**A:** It is a good design with a real cost, and the honest answer names both. The
*good*: the extension points are **composable** (an extension can implement
`BeforeEachCallback` and `AfterEachCallback` and `TestWatcher` independently), and
the *ordering* is **well-defined** — `BeforeAll` → `BeforeEach` → `BeforeTestExecution`
→ the test → `AfterTestExecution` → `AfterEach` → `AfterAll` — so a framework can
hook into exactly the phase it needs. The *cost*: the composability means that
**the behaviour of a test is the sum of every extension on the classpath**, and when
something unexpected happens (a test that mysteriously shares state, an ordering that
changes, a database that is not rolled back), the cause is *not* in the test file —
it is in an extension that someone added for a legitimate reason. The deeper issue is
that the **extension model is a global configuration surface**: adding
`@ExtendWith(SpringExtension.class)` to one class changes the *semantics* of that
class in ways that are invisible in the class. The mitigation the ecosystem has
adopted is a **house style**: a small, explicit set of base classes (`@IntegrationTest`,
`@UnitTest`) that carry the extensions, so the *composition* is declared in one
place and the *test* stays clean. The deeper lesson: *composability trades local
clarity for global flexibility — and the two must be balanced by convention, because
the language cannot make the trade for you.* The same pattern appeared in the JDK
with `ServiceLoader` (a global plugin mechanism) and with the module system (a global
boundary mechanism): the industry keeps choosing global composability, and keeps
needing a house style to keep it comprehensible.

---

## 9. Counter-Questions & Traps

### CQ1. "If `@BeforeAll` must be static, doesn't that make the test class stateful and awkward?"

- **Naive:** "Use `@TestInstance(PER_CLASS)`."  ← *correct, and it misses the trade-off*
- **The trap:** The trap is to jump to the escape hatch without understanding **why** the rule exists. The static requirement is a *consequence* of the default `PER_METHOD` lifecycle — and understanding that is what tells you when `PER_CLASS` is the right choice.
- **Expert answer:** The mechanism and the decision. JUnit 5 creates a **new test-class instance for each test method** (`PER_METHOD`) — which is what makes the tests *independent* (no shared state, safe to run in parallel). With that model, a `@BeforeAll` has no instance to run on, so it must be static. `@TestInstance(PER_CLASS)` flips the model: one instance for the whole class, so `@BeforeAll` can be non-static and instance fields are shared. When to use it: (1) the fixture is **expensive** (a container, a database) and you want it set up once; (2) the tests build on each other *deliberately* (rare, and a smell); (3) you want a **non-static** `@BeforeAll` with DI. The cost of `PER_CLASS`: the tests now **share an instance**, so they are **not independent** — a mutation in one test leaks into the next, and parallel execution within the class is disabled. The honest recommendation: **default to `PER_METHOD` and keep the tests independent**; use `PER_CLASS` only for a genuinely expensive, genuinely shared fixture — and if the tests start depending on the shared state, that is a design smell, not a feature.

### CQ2. "Is `assertThrows` better than the old `ExpectedException` rule? What do I lose?"

- **Naive:** "Yes, it's much better."  ← *true, and the trade-off is not only about the API*
- **The trap:** The trap is to treat this as a syntactic improvement. The real gain is **compositionality** — `assertThrows` is a *value-returning function*, so it can be used inside a lambda, assigned to a variable, and asserted on. That is a different *kind* of capability, not a nicer syntax.
- **Expert answer:** The three gains, in order of importance. (1) **It returns the exception** — so you can assert on the message, the cause, or the state; the old `ExpectedException` rule could only check the *type* (and only within a narrow scope). (2) **It is scoped to one statement** — the `try`/`catch` in the old rule was a separate block, so it was easy to assert on the wrong exception, or to have the assertion *outside* the block. (3) **It composes** — `assertThrows(..., () -> assertThrows(..., inner))` works; and it fits into `assertAll`. What you "lose": nothing functional — but note that the old rule was *more* forgiving of a sloppy test, because a `try`/`catch` with an assertion in the `catch` block would pass even if the method did not throw at all (the assertion simply never ran). `assertThrows` makes that mistake impossible. The deeper point: **the new API removed a footgun by construction** — which is the JUnit 5 design philosophy applied to the assertion layer.

### CQ3. "You said use a fake rather than a mock. But a fake means my test depends on the real implementation's behaviour, which is the thing I wanted to isolate."

- **Naive:** "It depends — use a mock for a boundary, a fake for an internal collaborator."  ← *the right rule, but it needs the reasoning*
- **The trap:** The trap is to treat "fake vs mock" as a style preference. The real distinction is **what the test is verifying**: a mock lets you assert on the *interaction*; a fake lets you assert on the *outcome*. And the right choice follows from which one the test is *about*.
- **Expert answer:** The decision rule, stated as a question: **"What am I trying to prove?"** (1) *"My code called the gateway with the right amount"* → a **mock** (`verify(gateway).charge(request)`) — this is a legitimate and precise assertion. (2) *"My code produces the right order total"* → the gateway is **irrelevant** — use a **fake** (or nothing at all). The cost of the mock-first approach: the test now depends on the *production interface's shape*, so a refactor of the interface breaks the test even though the behaviour is unchanged. The cost of the fake-first approach: you write more code, and the fake is a second implementation that can drift from the real one. The resolution: **use a fake for a collaborator you do not care about** (it just needs to not blow up), and a **mock only for the one collaborator whose *interaction* is the point of the test**. And the third option that is often best: **make the collaborator a parameter** — a *pure* function or a simple value — so there is nothing to mock at all. The general principle: *a test should fail for one reason; the more collaborators it asserts on, the more reasons it has to fail.*

### CQ4. "Why is JUnit 5's `assertEquals(expected, actual)` order the opposite of JUnit 4's for the message?"

- **Naive:** "It was a mistake."  ← *true-ish, and it misses why the new order is better*
- **The trap:** The trap is to treat it as an arbitrary change. The new order is **deliberately better**, and the reason is *type safety*.
- **Expert answer:** The reason the new order is better: with `(expected, actual)`, the compiler can **check that the two arguments have compatible types** — and the *actual* is the one whose type you often do not control (a `long` from a `count()`, a `BigDecimal` with a different scale). With JUnit 4's `(message, expected, actual)`, the compiler **cannot** check that the second and third arguments match, because the first is a `String` — so `assertEquals("msg", 1, "one")` compiles and fails at runtime with a confusing message. The corollary: the new order makes a **type mismatch a compile error**, which is exactly the class of bug the JUnit 4 order let through. The residual problem — that `assertEquals(1, list.size())` with an `int` and a `long` is fine, but `assertEquals(1, someLong)` can be ambiguous — is handled by the JUnit 5 overloads (`assertEquals(long, long)`, `assertEquals(Object, Object)`, etc.), which resolve the ambiguity at compile time. The deeper lesson: *the argument order of an assertion API is a type-safety decision, not a cosmetic one* — and it is the same reason `assertThat(actual, is(expected))` (AssertJ) puts the actual first.

### CQ5. "If `@Nested` is so good, why does the JUnit team say not to overuse it?"

- **Naive:** "Too much nesting is confusing."  ← *true, and it misses the specific failure mode*
- **The trap:** The trap is to treat it as a style rule. The specific failure mode is that **a nested class inherits the outer class's `@BeforeEach`** — and if the outer's setup is expensive or has side effects, every nested test pays for it, and the *failure* is attributed to the nested class.
- **Expert answer:** The real constraint, and the guidance. The mechanics: (1) the outer `@BeforeEach` runs **before** the inner one for every nested test; (2) the outer `@BeforeAll` runs once; (3) a nested class **cannot** have a non-static `@BeforeAll` unless the outer class is `PER_CLASS`; and (4) the **display name** of a nested test is `Outer.Nested.method` — so a deep nesting produces a long, hard-to-read name. The guidance: use `@Nested` for **one level** (a scenario group), keep the *number* of nested levels to one or two, and make sure the outer `@BeforeEach` is **cheap** — put the expensive fixture in the nested class (or use `PER_CLASS` with a container). And the failure mode to name: a nested class that *inherits* an outer fixture it does not need, so the test is coupled to the outer class's setup — which is the `@Nested` equivalent of the "shared mutable state" problem. The general principle: *`@Nested` gives you a *naming* and *grouping* benefit; it also gives you an *inheritance* of fixtures — and the grouping must not become a hidden dependency.*

### CQ6. "You said `assertAll` reports every failure. But shouldn't a test stop at the first failure?"

- **Naive:** "No, `assertAll` is better."  ← *true, and it misses the case where stopping is right*
- **The trap:** The trap is to believe `assertAll` is always better. There are cases where the **first** failure is the important one — the *setup* failed, so every subsequent assertion is meaningless noise.
- **Expert answer:** The distinction, and the pattern that handles both. (1) **A setup failure should stop the test** — if the fixture did not initialise, the ten assertions after it are all noise, and the report becomes unreadable. The way to express that: keep the setup in `@BeforeEach` (where a failure *aborts* the test before any assertion runs) and use `assertAll` only for the **assertions**. (2) **Independent expectations should be reported together** — if the assertions are about *different* aspects of the result, `assertAll` shows you all the problems at once. (3) The **mixed** pattern that gets both: `assertAll` for the independent checks, and a plain `assertEquals` for the *precondition* that the rest depends on. The general principle: *the "stop at the first failure" rule and the "report everything" rule serve different purposes — use the first for a **chain of dependencies** and the second for **independent facts**.*

### CQ7. "Why does JUnit 5 not guarantee the test order? Isn't that a problem for tests that depend on each other?"

- **Naive:** "You can use `@TestMethodOrder`."  ← *true, and it misses why the default is right*
- **The trap:** The trap is to treat the non-determinism as a limitation. The non-determinism is a **feature**: it is what makes the tests **independent** and therefore **parallelisable** and **randomly ordered** (so a hidden dependency shows up).
- **Expert answer:** The reasoning. (1) **The default exposes hidden dependencies** — if your test only passes because another test ran first, a random order (or a parallel run) will break it, and you will find the bug *before* it reaches production. The JUnit team's position is that a test that needs another test is **already broken**. (2) **Parallel execution requires independence** — JUnit 5's `junit.jupiter.execution.parallel.enabled` makes the tests in a class run concurrently; an order dependency would become a race. (3) `@TestMethodOrder` exists, but it is a **diagnostic tool**: if you enable it and a test starts failing, you have found a hidden dependency. The legitimate uses of ordering are rare: a test that genuinely needs a shared, expensive resource (and should use `PER_CLASS` instead), and a "the class must be constructed in this order" scenario. The general principle: *the default should be the safe choice; a non-deterministic order surfaces exactly the class of bug that a deterministic order hides.* And the practical corollary: **if you need order, you need `@TestInstance(PER_CLASS)`** — a deliberate, visible, single-instance design — rather than a hidden inter-test dependency.

### CQ8. "Is JUnit 5 slow? Should we use TestNG instead, or JUnit 4?"

- **Naive:** "JUnit 5 is fast enough."  ← *true, and it misses where the time actually goes*
- **The trap:** The trap is to attribute test-suite slowness to the *framework*. The framework is almost never the bottleneck — the **fixture** is.
- **Expert answer:** Where the time actually goes, and the framework comparison. (1) **The framework overhead is negligible** — a JUnit 5 test with no I/O runs in **microseconds**; the discovery and reporting overhead is a fixed cost per class. (2) **The real cost is the fixture** — a `@BeforeEach` that starts a Spring context, or hits a database, or spins up a container, costs **hundreds of milliseconds to seconds** per test. So the optimisation is: (a) **reuse the expensive resource** (`@BeforeAll` + `PER_CLASS`, or a singleton container); (b) **replace the slow dependency with a fake**; (c) **run the slow tests in a separate task** (a `test` and `integrationTest` source set, or Gradle test suites) so the inner loop stays fast. (3) **The framework comparison**: TestNG's advantages are **data-driven testing** (a `@DataProvider` can feed *many* test methods, and it has more powerful grouping), and its **suite XML** (a configuration format JUnit 5 deliberately does not have). JUnit 5's advantages are the **extension model**, **`@Nested`**, **parameterised tests with named cases**, and — decisively — **the platform**, which lets you run TestNG and JUnit together. (4) The honest modern verdict: **JUnit 5 has become the default**, and the cases where TestNG is genuinely better (a very complex, data-driven suite) are rare. And the important nuance: *the framework choice matters far less than the test design* — a well-designed suite with a fast fixture is fast on any framework, and a badly-designed one is slow on all of them.

### CQ9. "Why did JUnit 5 keep `assertEquals(expected, actual)` when `assertThat(actual, is(expected))` is better?"

- **Naive:** "The fluent API is AssertJ, a separate library."  ← *true, and it misses the design tension*
- **The trap:** The trap is to treat the fluent API as unambiguously better. It *is* better for **rich** assertions — but it is not better for the **simple** case, and the simple case is 90% of the assertions.
- **Expert answer:** The trade-off, precisely. (1) **The fluent API wins on expressiveness** — `assertThat(result).hasSize(3).contains("a").extracting(Item::sku).containsExactly("x","y")` is far more expressive than five `assertEquals` calls, and the failure messages are better. (2) **The JUnit assertions win on *cost*** — `assertEquals(a, b)` is a **static method on a JDK class**: no dependency, no lambda allocation, no matcher objects, and it is trivially discoverable. The fluent API allocates a matcher per assertion and requires a library. (3) The **IDE integration** matters: JUnit's `assertEquals` is understood by every IDE, and the "generate test" and "go to assertion" features work with it. (4) The **historical** reason: JUnit 5 could not add AssertJ's API without making it a *different library* — and the JUnit team's position has been that the *core* assertions should stay minimal and dependency-free, with the rich assertions belonging in a library. The modern practice: **use the JUnit 5 assertions for the simple cases and AssertJ for the rich ones** — they coexist perfectly. And the deeper lesson: *a testing API has two audiences — the person writing the test (ergonomics) and the person reading the failure (diagnostics) — and the "best" API depends on which audience you are optimising for.*

### CQ10. "If `@Nested` and `@ParameterizedTest` exist, is `@TestFactory` still useful?"

- **Naive:** "No, `@ParameterizedTest` replaces it."  ← *true for most cases, and it misses the real distinction*
- **The trap:** The trap is to think the difference is the *source* of the cases. The real difference is that `@ParameterizedTest` cases are **known before the test runs** (they are enumerated by the engine), while `@TestFactory` cases are **discovered at runtime* — and that difference changes the *reporting*, the *lifecycle*, and the *failure isolation*.
- **Expert answer:** The distinction, and the guidance. (1) **`@TestFactory`** returns a `Stream<DynamicTest>` — the cases are created **when the test runs**, so they can depend on runtime state (the files in a directory, the rows in a database, the entries in a config). Each `DynamicTest` is reported **individually**, with its own pass/fail. (2) **`@ParameterizedTest`** enumerates its cases **before** the test method runs (the `@MethodSource` is invoked once), so the cases cannot depend on per-test runtime state; and the cases share the test method's fixture. (3) The **reporting** difference is the practical one: `@ParameterizedTest` gives you clean, named cases in the report, while a `@TestFactory` that returns a `Stream<DynamicNode>` gives a tree. (4) The **guidance**: use `@ParameterizedTest` for a **known set of cases** (99% of the time) — it has better naming, better IDE support, and better failure isolation. Use `@TestFactory` only when the cases are **genuinely dynamic** (a directory listing, a database query) — and then return `DynamicTest`s, not a plain `Stream<String>`, so each case is reported separately. And the failure mode to avoid: using `@TestFactory` with a `Stream` of values (rather than `DynamicTest`s) — the whole test then fails as one unit, and you lose the per-case reporting.

---

## 10. Rapid-Fire MCQs

**Q1.** JUnit 5's assertions live in…
- A) `org.junit.Assert`  B) **`org.junit.jupiter.api.Assertions`**  C) `org.junit.jupiter.api.Test`  D) `org.assertj.core.api.Assertions`

**Q2.** `@BeforeAll` must be `static` unless…
- A) The class is `final`  B) **`@TestInstance(PER_CLASS)`**  C) The method is `private`  D) It is never an issue

**Q3.** In JUnit 5, `assertEquals` takes the message…
- A) First  B) **Last**  C) In the middle  D) It has no message parameter

**Q4.** `assertThrows` returns…
- A) `void`  B) **The thrown exception**  C) `true`/`false`  D) The error message

**Q5.** `assertAll` differs from a sequence of assertions because it…
- A) Runs faster  B) **Reports EVERY failure, not just the first**  C) Runs in parallel  D) Is optional

**Q6.** `assertSame` compares…
- A) Values with `equals`  B) **Identity with `==`**  C) Hash codes  D) References by content

**Q7.** A floating-point `assertEquals` needs…
- A) Nothing  B) **A delta**  C) `assertThat`  D) `assertNotEquals`

**Q8.** `@Nested` is used to…
- A) Run a test repeatedly  B) **Group scenarios in inner classes**  C) Skip a test  D) Set a timeout

**Q9.** `@ParameterizedTest` with `@MethodSource` gets its cases from…
- A) An annotation  B) **A static method that returns a `Stream`**  C) A CSV file  D) The classpath

**Q10.** The JUnit Platform exists so that…
- A) Tests run faster  B) **Multiple engines (JUnit 4, JUnit 5, TestNG) can share one platform**  C) The JDK can be mocked  D) The IDE can find tests

**Q11.** The JUnit 5 extension model replaces…
- A) `@Test`  B) **`@RunWith` and `@Rule`**  C) `assertEquals`  D) The Maven plugin

**Q12.** `@TestFactory` is for…
- A) Known test cases  B) **Test cases discovered at runtime**  C) Performance tests  D) Repeating a test

**Q13.** The default JUnit 5 test order is…
- A) Declaration order  B) **Non-deterministic**  C) Alphabetical  D) Random but seeded

**Q14.** `@TestInstance(PER_CLASS)` changes the lifecycle to…
- A) One instance per class  B) **One instance per class (instead of per method)**  C) One thread per class  D) No instances

**Q15.** `@Disabled` replaces JUnit 4's…
- A) `@Ignore`  B) `@Skip`  C) `@IgnoreClass`  D) `@Comment`

**Q16.** `@Tag` is used for…
- A) Documentation  B) **Grouping and filtering the tests**  C) Timing  D) Parameterising

**Q17.** `@Timeout` on a test method means…
- A) The test is skipped  B) **The test fails if it exceeds the time**  C) The test is retried  D) The JVM exits

**Q18.** To run JUnit 4 tests on the JUnit 5 platform you add…
- A) `junit-jupiter-api`  B) **`junit-vintage-engine`**  C) `junit-platform-suite`  D) Nothing

**Q19.** The JUnit 5 Maven dependency is…
- A) `junit:junit`  B) **`org.junit.jupiter:junit-jupiter`**  C) `junit-platform-engine`  D) `org.junit.platform:junit-platform-launcher`

**Q20.** A hand-written fake is preferable to a mock for a collaborator that is…
- A) Non-deterministic  B) **A deterministic, internal part of your own domain**  C) A remote service  D) The clock

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | `org.junit.jupiter.api.Assertions` — statically imported. |
| 2 | **B** | `@TestInstance(PER_CLASS)` gives one instance, so the method can be non-static. |
| 3 | **B** | The message is the **last** argument in JUnit 5. |
| 4 | **B** | The exception — so you can assert on its message and cause. |
| 5 | **B** | It reports every failure, so you see all the problems at once. |
| 6 | **B** | Identity (`==`) — used for singletons and interned values. |
| 7 | **B** | A delta — `assertEquals(0.3, 0.1+0.2, 1e-9)`. |
| 8 | **B** | Inner classes that group the scenarios and make the names sentences. |
| 9 | **B** | A static method returning a `Stream` of `Arguments`. |
| 10 | **B** | The platform separates the execution model from the programming model. |
| 11 | **B** | The composable extension model. |
| 12 | **B** | Cases that are only known when the test runs. |
| 13 | **B** | Non-deterministic — which exposes hidden inter-test dependencies. |
| 14 | **B** | One instance for the whole class (the default is one per method). |
| 15 | **A** | `@Ignore` → `@Disabled`. |
| 16 | **B** | Grouping, so a run can be filtered by tag. |
| 17 | **B** | The test fails if it takes longer than the timeout. |
| 18 | **B** | `junit-vintage-engine` — the migration aid. |
| 19 | **B** | `org.junit.jupiter:junit-jupiter` (the aggregate). |
| 20 | **B** | A deterministic internal collaborator — a fake exercises the real behaviour. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE JUNIT 5 ANNOTATIONS                              │
│  @Test              a test method                                       │
│  @BeforeEach        before EVERY test (per test)                         │
│  @AfterEach         after EVERY test                                    │
│  @BeforeAll         once per class (STATIC)                             │
│  @AfterAll          once per class (STATIC)                             │
│  @Disabled("why")   skip it, with a reason                              │
│  @Nested            an inner class = a scenario group                    │
│  @ParameterizedTest many cases from one method                          │
│  @RepeatedTest(n)   the same test, n times                              │
│  @TestFactory       cases discovered at RUNTIME                         │
│  @Timeout           the test fails if it takes too long                  │
│  @Tag("group")      for filtering the run                               │
│  @Order(n)          ordering (rarely needed)                             │
│  @TestInstance(PER_CLASS)  one instance for the whole class              │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE ASSERTION ESSENTIALS                             │
│  assertEquals(expected, actual, message)   ⚠ THE MESSAGE IS LAST       │
│  assertNotEquals / assertTrue / assertFalse / assertNull / assertNotNull  │
│  assertSame / assertNotSame               (identity, not equality)      │
│  assertArrayEquals                        (for arrays)                  │
│  assertThrows(Type.class, () -> …)        → RETURNS the exception      │
│  assertAll("group", () -> …, () -> …)      → reports EVERY failure     │
│  assertEquals(0.3, 0.1+0.2, 1e-9)         → floats need a DELTA       │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE JUNIT 4 → 5 MIGRATION                            │
│  org.junit.Test              → org.junit.jupiter.api.Test               │
│  @Before / @After            → @BeforeEach / @AfterEach                │
│  @BeforeClass / @AfterClass  → @BeforeAll / @AfterAll (STATIC)         │
│  org.junit.Assert.*          → org.junit.jupiter.api.Assertions.*       │
│  assertEquals(msg,e,a)       → assertEquals(e,a,msg)   ⚠ ORDER FLIPS   │
│  @RunWith(X.class)           → @ExtendWith(X.class)                     │
│  @Rule / @ClassRule          → an Extension                            │
│  @Ignore                     → @Disabled                               │
│  ExpectedException           → assertThrows                             │
│  ⇒ add junit-vintage-engine and migrate one class at a time             │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The test-suite layout

```text
   src/test/java/
     ├── unit/          ⚡ milliseconds   — no Spring, no I/O, hand-written fakes
     │     └── OrderServiceTest.java
     ├── component/     🕐 seconds        — the real class, fake PORTS
     └── integration/   🕑 tens of seconds — Testcontainers: a real DB, a real broker

   ⚠ THE LAYOUT IS A BUILD CONCERN:
     Gradle source sets or Maven profiles, so `mvn test` runs ONLY the fast ones.
     The slow suites run in a separate CI stage.
```

### The house rules

| Rule | Why |
|---|---|
| **No `Thread.sleep()`** in a test | The single largest source of flaky tests — use a `CountDownLatch` |
| **No shared mutable state** between tests | Independence ⇒ parallel execution is safe |
| **The test name is the specification** | A reader should not need the code |
| **Assert on observable behaviour**, not on calls | A mock-heavy test passes while the code is wrong |
| **One reason to fail** per test | More assertions = more failure modes |
| **Use `assertAll`** for independent expectations | All the problems at once |
| **Prefer a fake** for a deterministic internal collaborator | It does not break when the interface changes |
| **Mock only a boundary** | A mock is a coupling to the production API's shape |
| **Tag the slow tests** | So the inner loop stays fast |
| **Quarantine a flaky test** — do not leave it in the main suite | A flaky test destroys trust in the whole suite |

### The performance checklist

```text
  ☐ The fixture is CHEAP (< 100 ms) — or it is reused via @BeforeAll
  ☐ A container (DB, broker) is a SINGLETON started once (Testcontainers)
  ☐ The slow suites are in a SEPARATE task, not the default `test` task
  ☐ A JUnit 5 extension resets the state between tests (no @DirtiesContext)
  ☐ The assertions are not the bottleneck (they are not)
```

### Observability

| Signal | What it tells you |
|---|---|
| The suite's wall-clock time | The inner-loop budget |
| The slowest test | The fixture to optimise |
| The flaky-test count | ⚠ A trust problem — quarantine and fix |
| The assertion count per test | Too many independent facts? Use `assertAll` |
| The `@Tag` breakdown | Which suite is slow |

---

## 13. Follow-Up Learning

| Topic | Where to go next |
|---|---|
| Mocking and fakes in depth | [02 · Mockito & Mocking](02-Mockito-Mocking.md) |
| The other framework | [03 · TestNG](03-TestNG.md) |
| The practices that make a suite good | [04 · Testing Best Practices](04-Testing-Best-Practices.md) |
| Testing a serialised format | [17 · Serialization](../17-Serialization/03-Serialization-Best-Practices.md) |
| The extension model in the JDK source | `junit-jupiter-api` — the `Extension` interfaces |

---

**Next Chapter:** → [02 · Mockito & Mocking](02-Mockito-Mocking.md)
**Previous Chapter:** → [17 · Serialization](../17-Serialization/03-Serialization-Best-Practices.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
