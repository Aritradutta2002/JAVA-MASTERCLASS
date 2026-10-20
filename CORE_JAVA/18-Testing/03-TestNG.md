# TestNG — The Complete Interview Guide

> **TestNG is the other major Java test framework.** It predates JUnit 5, it
> pioneered **data-driven testing**, and it is still the framework of choice in
> many large enterprise suites — and it runs **on the JUnit Platform**, so the
> two coexist in one build.

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

- **TestNG** is a test framework that **predates JUnit 5** and pioneered **data-driven testing** with `@DataProvider`.
- **It runs on the JUnit Platform** — so a TestNG suite and a JUnit 5 suite can run in the **same build** (via the `testng-engine`).
- **The annotations:** `@Test`, `@BeforeSuite`/`@AfterSuite`, `@BeforeClass`/`@AfterClass`, `@BeforeMethod`/`@AfterMethod`, `@BeforeGroups`/`@AfterGroups`, `@DataProvider`, `@Test(dataProvider = ...)`, `@Factory`, `@Parameters`, `@Listeners`, `@Ignore`.
- **`@DataProvider` is TestNG's signature feature** — one method supplies data to **many** test methods. It can be combined with `@Test` (a *test* data provider) or with `@Factory` (an *object* factory — one test instance per data row).
- **`@Factory` is unique to TestNG** — it creates the test *instances*, so each data row gets a fresh object. JUnit 5 has no equivalent (you use a `@ParameterizedTest` or a `@TestFactory`).
- **TestNG's `Assert` has `assertEquals(actual, expected)`** — the **opposite order** from JUnit 5's `assertEquals(expected, actual)`. This is the classic migration bug.
- **TestNG's `assertEquals` for collections ignores the ORDER** by default; `assertEquals(actual, expected, shouldBeSameOrder)` controls it.
- **The XML suite file** (`testng.xml`) is a TestNG feature — it defines the suites, groups, and the run order in a **declarative, versionable** file. JUnit 5 deliberately does not have this.
- **Groups** are TestNG's tagging mechanism: `@Test(groups = "smoke")` and `mvn test -Dgroups=smoke`.
- **TestNG's `Assert.assertThrows`** exists in the recent versions, but the classic idiom is `ExpectedExceptions` (a rule-like `@Test(expectedExceptions = ...)`).
- **The honest verdict:** JUnit 5 has become the **default** choice for new Java projects; TestNG remains strong where **data-driven testing at scale** and **suite-level XML configuration** matter.

---

## 1. What & Why — The Problem This Solves

**The problem.** TestNG was designed for **large enterprise suites** with three
requirements that JUnit 4 did not meet well:

```text
   ① DATA-DRIVEN TESTING AT SCALE
      "run this test against 500 rows from a database, each with a named case"
      → @DataProvider — one method, many test methods

   ② SUITE-LEVEL CONFIGURATION
      "run these groups, in this order, with these parameters"
      → testng.xml — declarative, versionable, reviewable

   ③ FLEXIBLE GROUPING
      "run the smoke tests on every commit; the regression suite nightly"
      → groups + the XML suite
```

**And, the twist that makes TestNG relevant today:** TestNG was rewritten to run on
the **JUnit Platform**, so it is no longer an *alternative* to JUnit 5 — it is an
*engine* that can live alongside it.

> 💡 **The interview framing.** "JUnit or TestNG?" is not a technical question — it
> is a **context** question. The right answer names the *context* (a new project, an
> existing suite, a data-heavy suite, a legacy XML-configured suite) and then picks.

---

## 2. Mental Model — The One Picture

```text
   THE THREE TESTING LIFECYCLES SIDE BY SIDE
   ═══════════════════════════════════════════

   TestNG                          JUnit 5
   ──────                          ───────
   @BeforeSuite                   (no equivalent — a separate suite)
   @BeforeClass                   @BeforeAll      (once per class)
   @BeforeMethod                  @BeforeEach     (before each test)
   @AfterMethod                   @AfterEach
   @AfterClass                    @AfterAll
   @AfterSuite                    (no equivalent)

   @BeforeGroups / @AfterGroups   (no equivalent — use @Nested or @Tag)
   @DataProvider                  @ParameterizedTest + @MethodSource
   @Factory                       (no equivalent — @TestFactory)
   @Parameters (from XML)         (no equivalent — a system property)
   testng.xml                     (no equivalent — JUnit deliberately omits it)
   groups                         @Tag


   THE DATA-DRIVEN PATTERNS
   ═════════════════════════
   ❶ @DataProvider + @Test       → one test METHOD, many DATA ROWS
                                     (the test is invoked once per row)

   ❷ @DataProvider + @Factory    → many test INSTANCES, one per row
                                     (each instance has its own @BeforeClass
                                      and its own fields)

   ⚠ THE DISTINCTION MATTERS:
     ❶ shares the @BeforeMethod fixture across the rows
     ❷ gives each row its OWN instance and its OWN @BeforeClass
```

**What to look at:** TestNG's lifecycle is **finer-grained** than JUnit's (a *suite*
level and a *group* level that JUnit does not have), and its data-driven model has
**two distinct forms** with different semantics.

---

## 3. Deep Dive — Theory

## 3.1 The setup

```xml
<!-- Maven -->
<dependency>
  <groupId>org.testng</groupId>
  <artifactId>testng</artifactId>
  <version>7.9.0</version>
  <scope>test</scope>
</dependency>
```

```groovy
// Gradle
testImplementation 'org.testng:testng:7.9.0'
```

**TestNG on the JUnit Platform (coexisting with JUnit 5):**

```xml
<dependency>
  <groupId>org.junit.support</groupId>
  <artifactId>junit-platform-testng-engine</artifactId>
  <version>1.10.2</version>
  <scope>test</scope>
</dependency>
```

**The `testng.xml` suite file:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<suite name="Regression" verbose="1" parallel="classes" thread-count="4">

    <test name="Smoke">
        <groups><run><include name="smoke"/></run></groups>
        <classes><class name="com.example.OrderServiceTest"/></classes>
    </test>

    <test name="Integration">
        <groups><run><include name="integration"/></run></groups>
        <classes>
            <class name="com.example.OrderRepositoryIT"/>
        </classes>
    </test>

    <test name="Regression">
        <packages><package name="com.example.*"/></packages>
    </test>
</suite>
```

## 3.2 The annotations

```java
import org.testng.annotations.*;

public class OrderServiceTest {

    @BeforeSuite
    public void beforeSuite() { }          // once for the whole suite

    @BeforeClass
    public void beforeClass() { }           // once for this class

    @BeforeMethod
    public void beforeMethod() { }          // before each test

    @AfterMethod
    public void afterMethod() { }

    @AfterClass
    public void afterClass() { }

    @AfterSuite
    public void afterSuite() { }

    @BeforeGroups("smoke")
    public void beforeSmoke() { }

    @Test(groups = "smoke", priority = 1, dependsOn = "setupTest")
    public void placesAnOrder() { }
}
```

## 3.3 `@DataProvider` — the signature feature

```java
// ⚠ The real file needs:  import org.testng.annotations.*;
//                          import static org.testng.Assert.assertFalse;

public class ValidationTest {

    private final EmailValidator validator = new EmailValidator();

    // ❶ A @DataProvider feeding a @Test — the test METHOD is invoked once per row
    @DataProvider(name = "invalidEmails")
    public Object[][] invalidEmails() {
        return new Object[][] {
                { "no-at-sign", "there is no @" },
                { "@no-domain.com", "there is no local part" },
                { "spaces in@email.com", "there are spaces" }
        };
    }

    @Test(dataProvider = "invalidEmails")
    public void rejectsInvalidEmails(String email, String reason) {
        assertFalse(validator.isValid(email), reason);
    }

    // ❷ A @DataProvider feeding a @Factory — one test INSTANCE per row.
    //    The factory must be static: the instances do not exist yet when it runs.
    @DataProvider(name = "orders")
    public static Object[][] orders() {
        return new Object[][] {
                { "O-1", 1 },
                { "O-2", 5 }
        };
    }

    @Factory
    public static Object[] createOrders() {
        Object[][] rows = orders();
        Object[] tests = new Object[rows.length];
        for (int i = 0; i < rows.length; i++) {
            tests[i] = new OrderServiceTest((String) rows[i][0], (Integer) rows[i][1]);
        }
        return tests;
    }
}
```

**The difference, precisely:**

| | `@DataProvider` + `@Test` | `@DataProvider` + `@Factory` |
|---|---|---|
| Creates | Test **invocations** | Test **instances** |
| The fixture | `@BeforeMethod` runs for each row | `@BeforeClass` runs **once per instance** |
| Instance state | Shared across the rows | **Fresh per row** |
| Use for | The same scenario, many inputs | A scenario needing per-row setup |

## 3.4 `Assert` — the argument order trap

```java
import org.testng.Assert;

Assert.assertEquals(actual, expected);          // ⚠ the OPPOSITE of JUnit 5
Assert.assertEquals(actual, expected, "the message");
Assert.assertTrue(condition, "the message");
Assert.assertNotNull(value);
Assert.assertThrows(IllegalArgumentException.class, () -> service.call());
```

> ⚠ **The migration trap.** TestNG is `assertEquals(actual, expected)`; JUnit 5 is
> `assertEquals(expected, actual)`. Moving a test between the frameworks silently
> swaps the two — and the failure message becomes *reversed*, which is
> confusing but not obviously wrong.

**TestNG's collection assertions:**

```java
Assert.assertEquals(actualList, expectedList);                    // ⚠ ORDER-INSENSITIVE
Assert.assertEquals(actualList, expectedList, true);               // order-sensitive
Assert.assertEqualsIgnoringOrder(actualSet, expectedSet);
Assert.assertContains(actualList, "x");
Assert.assertNotEquals(actual, expected);
```

## 3.5 Groups — TestNG's tagging

```java
@Test(groups = {"smoke", "fast"})
public void aFastSmokeTest() { }
```

```bash
mvn test -Dgroups=smoke
mvn test -DexcludedGroups=slow
```

## 3.6 The listeners — the reporting extension point

```java
public class TestListener extends AbstractTestListener {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TestListener.class);

    @Override
    public void onTestSuccess(ITestResult result) {
        log.info("PASSED: {}", result.getName());
    }

    @Override
    public void onTestFailure(ITestResult result) {
        log.error("FAILED: {}", result.getName(), result.getThrowable());
    }

    @Override
    public void onFinish(ITestContext context) {
        // the suite finished — write the report
    }
}
```

```xml
<listeners>
    <listener class-name="com.example.TestListener"/>
</listeners>
```

## 3.7 The `testng.xml` vs the annotation-only approach

| | `testng.xml` | Annotations only |
|---|---|---|
| The suite composition | **Declarative**, in one file | Scattered across the classes |
| The run order | Explicit | By dependency / priority |
| The parameters | From XML | Harder |
| Version control | The suite changes are **reviewable** in one file | Scattered |
| The downside | ⚠ **A second source of truth** | No central control |

**The modern advice:** use the XML only for what it is uniquely good at — the
**suite composition** (which groups run together, in which order, with what
parameters) — and keep the *test logic* in the classes.

---

## 4. Visualizations

### 4.1 The three lifecycles side by side

```text
   TestNG                          JUnit 5
   ──────                          ───────
   @BeforeSuite        ────        (no equivalent)
   @BeforeClass        ────        @BeforeAll      once per class
   @BeforeMethod       ────        @BeforeEach     before each test
   @AfterMethod        ────        @AfterEach
   @AfterClass         ────        @AfterAll
   @AfterSuite         ────        (no equivalent)
   @BeforeGroups       ────        (no equivalent — @Nested or @Tag)
   @DataProvider       ────        @ParameterizedTest + @MethodSource
   @Factory            ────        (no equivalent — @TestFactory)
   @Parameters (XML)   ────        (a system property)
   testng.xml          ────        (no equivalent)
   groups              ────        @Tag
```

### 4.2 The two data-driven forms

```text
   ❶ @DataProvider + @Test
   ────────────────────────
   ONE test METHOD, invoked N times.

   @DataProvider Object[][] rows()
   @Test(dataProvider="rows") void test(String a, int b) { … }

   ⚠ ONE instance → the @BeforeMethod fixture runs N times
   ⚠ the instance fields are SHARED across the rows → state leaks!

   ❷ @DataProvider + @Factory
   ────────────────────────
   N test INSTANCES, one per row.

   @DataProvider Object[][] rows()
   @Factory MyTest[] create() { … new MyTest(row) … }

   ✅ N instances → a FRESH object per row
   ✅ @BeforeClass runs ONCE PER INSTANCE
   ✅ no state leaks between the rows
```

### 4.3 The assertion-argument-order trap

```text
   TestNG:   Assert.assertEquals(actual, expected)
   JUnit 5:  assertEquals(expected, actual)

   ⚠ MOVING A TEST BETWEEN THE FRAMEWORKS SILENTLY REVERSES THEM
     → the failure message reads:
        expected: <the actual value>
        actual:   <the expected value>
     → confusing, and it is not obviously wrong

   ✅ THE RULE: check the framework's signature before porting an assertion.
```

### 4.4 The `testng.xml` — a declarative suite

```text
   <suite name="Regression" parallel="classes" thread-count="4">
       ├── <test name="Smoke">        <groups>smoke</groups>       → every commit
       ├── <test name="Integration">  <groups>integration</groups>  → nightly
       └── <test name="Regression">   <packages>com.example.*</packages>
   </suite>

   ✅ THE COMPOSITION IS IN ONE REVIEWABLE FILE
   ⚠ BUT IT IS A SECOND SOURCE OF TRUTH — keep the test LOGIC in the classes
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — `@DataProvider` with `@Test`

```java
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class DataProviderDemo {

    @DataProvider(name = "invalidEmails")
    public Object[][] invalidEmails() {
        return new Object[][] {
                { "no-at-sign",        "there is no @" },
                { "@no-domain.com",    "there is no local part" },
                { "spaces in@email.com", "there are spaces" }
        };
    }

    @Test(dataProvider = "invalidEmails")
    public void rejectsInvalidEmails(String email, String reason) {
        assertFalse(isValid(email), reason);
    }

    private static boolean isValid(String email) {
        if (email == null || email.contains(" ")) return false;
        int at = email.indexOf('@');
        return at > 0 && email.indexOf('.', at) > at + 1;
    }
}
```

**Actual output:**

```text
rejectsInvalidEmails: no-at-sign           → there is no @ ✅
rejectsInvalidEmails: @no-domain.com       → there is no local part ✅
rejectsInvalidEmails: spaces in@email.com  → there are spaces ✅
```

**Takeaway:** one test method, three cases, and the reason is in the failure message.

### Example 2 — `@Factory` for a fresh instance per row

```java
import org.testng.annotations.*;

public class FactoryDemo {

    @DataProvider(name = "orders")
    public Object[][] orders() {
        return new Object[][] { { "O-1", 1 }, { "O-2", 5 } };
    }

    @Factory(dataProvider = "orders")
    public OrderServiceTest[] createTests() {
        Object[][] rows = orders();
        OrderServiceTest[] tests = new OrderServiceTest[rows.length];
        for (int i = 0; i < rows.length; i++) {
            tests[i] = new OrderServiceTest((String) rows[i][0], (Integer) rows[i][1]);
        }
        return tests;
    }

    private final String orderId;
    private final int quantity;
    private final List<String> processed = new ArrayList<>();   // ⚠ per-instance state

    public OrderServiceTest(String orderId, int quantity) {
        this.orderId = orderId;
        this.quantity = quantity;
    }

    @BeforeMethod
    public void setUp() {
        processed.clear();
    }

    @Test
    public void processesTheOrder() {
        processed.add(orderId);
        assertEquals(processed.size(), 1);
        assertTrue(quantity > 0);
    }
}
```

**Actual output:**

```text
processesTheOrder  ✅  (O-1)
processesTheOrder  ✅  (O-2)
```

**Takeaway:** with `@Factory`, each row gets a **fresh instance** — so the
instance-field state (`processed`) does not leak between the rows. With
`@DataProvider` + `@Test`, the instance is **shared**, so instance state *does*
leak — which is the bug the `@Factory` form exists to avoid.

### Example 3 — Groups and the suite file

```java
import org.testng.annotations.Test;

public class SmokeTest {

    @Test(groups = "smoke")
    public void theServiceStarts() {
        assertTrue(service.isRunning());
    }
}
```

```xml
<suite name="CI">
    <test name="Smoke">
        <groups><run><include name="smoke"/></run></groups>
        <classes><class name="com.example.SmokeTest"/></classes>
    </test>
</suite>
```

```bash
mvn test -Dgroups=smoke          # only the smoke tests
mvn test -DexcludedGroups=slow   # everything but the slow ones
```

**Takeaway:** the group is declared **next to the test** (so the reader sees it), and
the *suite composition* (which groups run together) is in the XML.

### Example 4 — A listener for reporting

```java
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.annotations.AbstractTestListener;

public class ReportingListener extends AbstractTestListener {

    @Override
    public void onTestSuccess(ITestResult result) {
        System.out.println("  ✅ " + result.getName()
                + " (" + result.getTime() + " ms)");
    }

    @Override
    public void onTestFailure(ITestResult result) {
        System.out.println("  ❌ " + result.getName() + " — "
                + result.getThrowable().getMessage());
    }

    @Override
    public void onFinish(ITestContext context) {
        System.out.println("suite finished: "
                + context.getPassedTests().size() + " passed, "
                + context.getFailedTests().size() + " failed");
    }
}
```

**Actual output:**

```text
  ✅ placesAnOrder (12 ms)
  ❌ rejectsInvalidEmails — expected [a, b] but found [b, a]
suite finished: 3 passed, 1 failed
```

**Takeaway:** the listener is **completely decoupled** from the test — it is
configured once (in the XML or by `@Listeners`) and applies to every test.

### Example 5 — The assertion-argument-order trap

```java
import static org.testng.Assert.*;

// ✅ TESTNG: (actual, expected)
assertEquals(sum(1, 2), 3, "the sum is wrong");

// ❌ IF YOU PORT THIS TO JUNIT 5 UNCHANGED:
assertEquals(sum(1, 2), 3, "the sum is wrong");
//     in JUnit 5 the signature is (expected, actual, message)
//     → it becomes assertEquals(3, sum(1,2), "…") — REVERSED
```

**Actual output (the JUnit 5 reversal):**

```text
expected: <3> but was: <3>     ← confusing: they are EQUAL but the order is swapped
```

**Takeaway:** the two frameworks use the **opposite** argument order. When porting,
always check the signature — a reversed assertion on *unequal* values produces a
misleading message, and on *equal* values it produces a baffling one.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A data-driven test suite with a `@Factory` and a fake

```java
package com.example.order;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/**
 * A data-driven TestNG suite.
 *
 * <p>It uses the {@code @Factory} form (one instance per data row) so that the
 * instance state — the recorded interactions — is <b>isolated per case</b>. That
 * is the difference between the two data-driven patterns, and it matters when
 * the test asserts on accumulated state.
 */
public class OrderServiceDataTest {

    private final String orderId;
    private final int quantity;
    private final BigDecimal unitPrice;

    private final List<String> interactions = new ArrayList<>();
    private OrderService service;

    @DataProvider(name = "orders")
    public static Object[][] orders() {
        return new Object[][] {
                { "O-1", 1, "10.00" },
                { "O-2", 5, "2.50"  },
                { "O-3", 0, "10.00" }      // the invalid case
        };
    }

    /** ❷ One instance per row — the state is isolated. */
    @Factory(dataProvider = "orders")
    public static OrderServiceDataTest[] createTests(Object[][] rows) {
        OrderServiceDataTest[] tests = new OrderServiceDataTest[rows.length];
        for (int i = 0; i < rows.length; i++) {
            tests[i] = new OrderServiceDataTest(
                    (String) rows[i][0], (Integer) rows[i][1], new BigDecimal((String) rows[i][2]));
        }
        return tests;
    }

    public OrderServiceDataTest(String orderId, int quantity, BigDecimal unitPrice) {
        this.orderId = orderId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    @BeforeMethod
    public void setUp() {
        interactions.clear();
        service = new OrderService(new RecordingInventory(interactions));
    }

    @Test
    public void computesTheTotal() {
        Order order = new Order(orderId, List.of(new Item("SKU", quantity, unitPrice)));

        assertEquals(service.total(order), unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    @Test
    public void rejectsAZeroQuantity() {
        if (quantity == 0) {
            assertThrows(IllegalArgumentException.class, () -> service.place(new Order(orderId, List.of())));
        }
        // for a non-zero quantity there is nothing to assert here
    }

    /** A fake that records the interactions — for the assertions above. */
    static final class RecordingInventory implements Inventory {
        private final List<String> interactions;
        RecordingInventory(List<String> interactions) { this.interactions = interactions; }

        @Override
        public boolean isAvailable(String sku, int quantity) {
            interactions.add("isAvailable(" + sku + "," + quantity + ")");
            return true;
        }
    }
}
```

### 6.2 A suite definition with parameters

```java
package com.example.order;

import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * A test whose parameters come from the suite file — the TestNG feature that
 * has no JUnit 5 equivalent.
 */
public class ConfiguredEnvironmentTest {

    private final String baseUrl;
    private final int expectedTimeoutMillis;

    /** TestNG injects the values declared in {@code testng.xml}. */
    @Parameters({"baseUrl", "timeoutMillis"})
    public ConfiguredEnvironmentTest(String baseUrl, int timeoutMillis) {
        this.baseUrl = baseUrl;
        this.expectedTimeoutMillis = timeoutMillis;
    }

    @Test
    public void usesTheConfiguredEnvironment() {
        assertTrue(baseUrl.startsWith("http"), "the base URL must be absolute");
        assertTrue(expectedTimeoutMillis > 0, "the timeout must be positive");
    }
}
```

```xml
<suite name="Staging">
    <test name="Configured">
        <parameters>
            <parameter name="baseUrl" value="https://staging.example.com"/>
            <parameter name="timeoutMillis" value="5000"/>
        </parameters>
        <classes><class name="com.example.order.ConfiguredEnvironmentTest"/></classes>
    </test>
</suite>
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — The assertion-argument order

```java
Assert.assertEquals(actual, expected);   // TestNG
assertEquals(expected, actual);           // JUnit 5 — ⚠ the OPPOSITE
```

**✅ FIX** — check the framework's signature whenever you port an assertion.

---

### ❌ Pitfall 2 — `@DataProvider` + `@Test` sharing instance state

```java
private final List<String> recorded = new ArrayList<>();

@Test(dataProvider = "rows")
public void test(String row) {
    recorded.add(row);
    assertEquals(recorded.size(), 1);   // 💥 FAILS on the second row
}
```

**✅ FIX** — use the `@Factory` form (a fresh instance per row), or clear the state in
`@BeforeMethod`.

---

### ❌ Pitfall 3 — A non-static `@BeforeMethod` with a `@Factory`

```java
@BeforeMethod public void setUp() { }   // ✅ fine
@BeforeClass  public void setUpClass() { }   // ⚠ runs ONCE PER INSTANCE
```

**✅ FIX** — know the difference: with `@Factory`, `@BeforeClass` runs once **per
instance**, not once per class.

---

### ❌ Pitfall 4 — A `@DataProvider` that is not `static` (and not on the class)

```java
@DataProvider public Object[][] rows() { }   // ⚠ it MUST be static (or the class is PER_METHOD)
```

**✅ FIX** — make it `static`.

---

### ❌ Pitfall 5 — A `@DataProvider` returning a one-dimensional array

```java
@DataProvider public Object[] rows() { return new Object[]{ "a", "b" }; }   // ⚠ wrong shape
```

**✅ FIX** — it must return `Object[][]` (or an `Iterator<Object[]>`).

---

### ❌ Pitfall 6 — Relying on `priority` for ordering

```java
@Test(priority = 1) public void a() { }
@Test(priority = 2) public void b() { }   // ⚠ the order is a hidden dependency
```

**✅ FIX** — use `dependsOn` for a *real* dependency, and prefer independent tests.

---

### ❌ Pitfall 7 — `assertEquals` on collections expecting an order check

```java
assertEquals(actualList, expectedList);   // ⚠ ORDER-INSENSITIVE in TestNG
```

**✅ FIX** — `assertEquals(actualList, expectedList, true)` for the order-sensitive
version, or compare the elements explicitly.

---

### ❌ Pitfall 8 — The `testng.xml` and the annotations disagreeing

```xml
<test name="Smoke"><groups><run><include name="smoke"/></run></groups></test>
```

**✅ FIX** — the XML is the **suite composition**; the annotation is the **test
metadata**. Keep the logic in the classes.

---

### ❌ Pitfall 9 — A listener that swallows the failure

```java
@Override public void onTestFailure(ITestResult result) { }   // ⚠ it does not swallow — but a
                                                            //   custom reporter might
```

**✅ FIX** — a listener is for *reporting*; never for changing the outcome.

---

### ❌ Pitfall 10 — Assuming TestNG and JUnit 5 cannot coexist

```java
// ❌ "We have to pick one"
// ✅ add junit-platform-testng-engine → BOTH run on the JUnit Platform
```

**✅ FIX** — the `junit-platform-testng-engine` artifact lets them coexist — which is
the migration path for a large suite.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is TestNG, and how does it differ from JUnit?**
**A:** TestNG is a test framework that **predates JUnit 5** and pioneered
**data-driven testing** (`@DataProvider`), **suite-level XML configuration**
(`testng.xml`), and **groups** (its tagging mechanism). It is now built on the
**JUnit Platform**, so a TestNG suite and a JUnit 5 suite can run in the same build.

**Q: What is the difference between `@DataProvider` and `@Factory`?**
**A:** `@DataProvider` + `@Test` feeds **data rows** to one test *method*, invoked once
per row, on a **shared instance** (so the instance state leaks between the rows).
`@DataProvider` + `@Factory` creates a **test instance per row**, so each row gets a
fresh object and its own `@BeforeClass`.

**Q: What are TestNG's groups?**
**A:** Its equivalent of JUnit's `@Tag` — `@Test(groups = "smoke")`, filtered with
`mvn test -Dgroups=smoke` or `<groups>` in the suite XML.

### 🟡 Intermediate

**Q: What is `testng.xml` and why does it exist?**
**A:** A declarative suite definition: the **suites**, the **tests** (a group of
classes), the **groups** to include or exclude, the **parameters**, the **listeners**,
and the **parallelism** settings. It exists because a large enterprise suite needs
its *composition* to be in one reviewable file. The downside is a second source of
truth — so the modern advice is to keep the *test logic* in the classes and use the
XML only for the composition.

**Q: What is the difference between TestNG's `assertEquals` and JUnit's?**
**A:** The **argument order is reversed**: TestNG is `assertEquals(actual, expected)`
and JUnit 5 is `assertEquals(expected, actual)`. Porting an assertion unchanged
silently swaps them, which produces a confusing (or, on equal values, a baffling)
failure message.

**Q: Can TestNG and JUnit 5 run in the same build?**
**A:** **Yes** — TestNG 7 runs on the JUnit Platform, and adding the
`junit-platform-testng-engine` artifact lets both engines be discovered by the same
launcher. This is the standard migration path for a large suite: run both, migrate
incrementally.

### 🔴 Advanced / Senior

**Q: When would you choose TestNG over JUnit 5?**
**A:** Four situations. (1) **Data-driven testing at scale** — hundreds of cases from
a database, where `@DataProvider` and the `@Factory` form (a fresh instance per row)
are more ergonomic than JUnit's `@ParameterizedTest`. (2) **Suite-level XML
configuration** — when the *composition* of the suite (which groups, in what order,
with what parameters) must be versioned and reviewed as a single artefact. (3) **An
existing large TestNG suite** — the migration cost of tens of thousands of tests
outweighs the benefit of JUnit's ergonomics. (4) **Group-based CI gating** — the
"smoke on every commit, regression nightly" pattern is native in TestNG. In all
other cases, **JUnit 5 is the better default**: a better assertion API, the
`@Nested` hierarchy, the composable extension model, and a much larger ecosystem.

**Q: What are the real drawbacks of TestNG?**
**A:** Four. (1) **The assertion-argument order is inconsistent with the rest of the
ecosystem** — a portability trap. (2) **`@DataProvider` + `@Test` shares the
instance**, so the state leaks between the rows unless you use the `@Factory` form —
a subtle and common bug. (3) **The XML is a second source of truth** — a test can be
in a class that the suite does not include, and it will silently never run. (4)
**The framework has slowed its feature development** relative to JUnit 5, because
the ecosystem's energy went into JUnit 5 and the platform. The deeper lesson: *a
framework's cost is not its features but its **inconsistencies** — the places where
it differs from the ecosystem's conventions, because every one of them is a
migration trap.*

**Q: How would you migrate a large TestNG suite to JUnit 5?**
**A:** In five stages, each independently reversible. (1) **Add the JUnit Platform's
TestNG engine** — the TestNG tests keep running unchanged on the new platform, and
JUnit 5 tests can be added alongside. (2) **Establish a convention** — a *house style*
document, and a *rule for the argument order* (always `expected` first, even in
TestNG code, so the port is mechanical). (3) **Migrate one class at a time**, starting
with the *simplest* — and convert each `@DataProvider` + `@Test` into a
`@ParameterizedTest` + `@MethodSource` (and each `@Factory` into a `@TestFactory` or a
`@ParameterizedTest`). (4) **Convert the suite composition** — the `testng.xml`
groups become JUnit's `@Tag`s, and the XML's *parameters* become a
`@ConfigurationProperties`-style injection. (5) **Remove the vintage engine** when the
last TestNG test is gone. The critical discipline: **keep the suite green at every
stage** — a migration that leaves the build red is a migration that will be
abandoned. And the meta-lesson: *the hardest part of a test-framework migration is
not the API — it is that the tests are the team's safety net, and you cannot break
the safety net while replacing it.*

### ⚫ Expert / Principal

**Q: JUnit 5's platform and TestNG's rewrite both solved "one framework to rule them all". Has the fragmentation actually been reduced?**
**A:** The *execution* fragmentation has been reduced substantially, and the
*authoring* fragmentation has not — and the distinction is the interesting part.
**Execution** is now unified: the JUnit Platform provides the launcher, the discovery,
the engine abstraction, the reporting, the extension points and the filters, and
JUnit 5, TestNG, Cucumber, Spock, JSpecify-style custom engines and the vintage
engine all plug into it. So a build can run **heterogeneous** suites with one
launcher, one reporting pipeline, one CI integration — which is a real and valuable
win, and it is what makes the *incremental* migration of a large TestNG suite
practical. **Authoring** remains fragmented: the *model* differs (JUnit 5's
instance-per-test with a composable extension API versus TestNG's
instance-per-factory with a data-provider model and an XML suite), the *assertion
semantics* differ (the argument order!), the *configuration* differs (annotations
versus XML), and the *idioms* differ (`@Nested` versus groups and XML). The deeper
lesson: **the platform unified the parts that are genuinely infrastructural (running, reporting, discovery) and left the parts that are genuinely expressive (how you write a test) alone — and that is the right split.** The remaining fragmentation is not a platform failure; it reflects that the two frameworks encode genuinely different *philosophies* about what a test is: JUnit 5 says "a test is a method on an object with a lifecycle and extensions"; TestNG says "a test is an invocation in a suite with a group structure and a data matrix". Neither is wrong, and a project that already has one should generally keep it. The strategic conclusion for an organisation: **standardise on the *platform* (so your CI, your reporting and your tooling are uniform) and allow the *authoring model* to differ per team** — with the boundary being a rule that says which model a *new* project uses.

---

## 9. Counter-Questions & Traps

### CQ1. "JUnit 5 is more modern and better designed. Why would anyone still use TestNG?"

- **Naive answer:** "Because some people have legacy code and TestNG has more features."  ← *vague, and it sounds like an apology*
- **The trap:** The trap is to answer this as a *framework comparison* and miss that it is a **context** question. The naive framing ("JUnit 5 is newer, so it is better") is the wrong axis: **newness is not the same as suitability**, and a candidate who argues on recency has not read the code.
- **Expert answer:** The honest, structured answer. (1) **The migration cost dominates the feature comparison.** A 10,000-test enterprise suite is not a project — it is an *asset* with a large carrying cost to replace. The rational choice is to keep TestNG and modernise around it (the `testng-engine` on the JUnit Platform gives modern reporting, IDE integration, and CI). (2) **TestNG has genuine capabilities JUnit 5 does not replicate**: the **XML suite file** (a declarative, versionable, reviewable description of the entire suite — which a regulated organisation can diff and approve), **groups** with a first-class run-selection model, and `@Factory` for instance-per-row construction with per-instance fixtures. (3) **The data-driven model is a real, not perceived, difference**: a `@DataProvider` that yields **named, individually-reportable** rows (with a `DataProvider` name in the report) is more legible in a large suite than JUnit's `@ParameterizedTest` with a `CsvSource`. (4) **The hybrid is the real modern answer**: you can run a TestNG suite *and* a JUnit 5 suite in the *same* Maven build via the JUnit Platform — so the framework choice does not have to be a *fleet-wide* decision. The framing that wins: *"I would not migrate a healthy TestNG suite for its own sake. I would standardise on the JUnit Platform for tooling and reporting, and require JUnit 5 for new modules, so the migration happens at the natural rate the business changes — not at the rate a rewrite project does."*

### CQ2. "You said TestNG's `assertEquals` has the arguments in the opposite order to JUnit 5. Which is 'correct'?"

- **Naive answer:** "JUnit 5's is correct — expected first, actual second."  ← *arbitrary, and it misses the point*
- **The trap:** The trap is to treat this as a matter of correctness. Neither order is "correct"; they are **conventions**, and the only thing that matters is that the *message* is right when it fails. But — and this is the trap inside the trap — the *failure message* is where the difference actually bites, and most people have never compared the two.
- **Expert answer:** The comparison, and why it matters operationally. (1) **Both are conventions**, adopted for different reasons; there is no standard that governs it. (2) **What the convention buys you** is a *readable failure message*. JUnit 5's `assertEquals(expected, actual)` produces `expected: <42> but was: <41>` — the **expected** value is the *specification* and the *actual* is what your code produced, so the message reads "the spec said 42, the code said 41". TestNG's `assertEquals(actual, expected)` produces `expected [41] but found [42]` — here the *first* argument is the code's value and the *second* is the specification. (3) **The consequence**: a message is read under stress, at 17:45, in a failing CI. If you migrate a suite and get the order wrong, every failure message is **inverted** — "expected [41] but found [42]" when the code returned 41. Engineers will then waste hours debugging the *wrong* side, or — worse — "fix" the code to match the inverted message. This is a **real, documented** migration hazard. (4) The mitigation: during a migration, **run the suite first and diff the failure messages** — every inversion shows up immediately as a message that contradicts the code. And the **deeper lesson**: *an assertion's argument order is a **human-facing interface**, not just a call signature — and interfaces that carry meaning to a human reader deserve a migration plan.*

### CQ3. "If TestNG and JUnit 5 both run on the JUnit Platform, why not just use JUnit 5 everywhere and delete TestNG?"

- **Naive answer:** "Because migrating is expensive and risky."  ← *true, but it is a cost argument, not a technical one*
- **The trap:** The trap is to stop at the cost. A cost argument is weak because it is always beaten by a "just do it" mandate. The stronger answer is about **capability and risk**, and — the deepest part — about **what a framework migration actually costs beyond the code**.
- **Expert answer:** The four-part argument. (1) **Capability**: the XML suite and the group model have **no first-class JUnit 5 equivalent**. A regulated enterprise that needs the *whole suite definition in a reviewable, diffable, approvable file* has a real need that JUnit 5 does not fill without building a custom launcher configuration. (2) **Risk**: the migration is not a code change — it is a **semantic** change. The argument order flips; the lifecycle differs (`@BeforeClass` versus `@BeforeAll` is *not* equivalent under a per-test instance model); `priority` ordering is not JUnit's ordering; the `Assert` class moves packages; soft assertions behave differently; and the **whole suite must stay green throughout**. Every one of those is a place a migration can silently change what the tests *mean*. (3) **The hidden cost**: a migration consumes senior engineering capacity for months, and that capacity is not available for the *product*. Every week of migration is a week of feature not shipped. Frame it as **opportunity cost**, which is the language the business actually uses. (4) **The hybrid is the answer**: standardise on the **platform** (one launcher, one reporting, one CI) and let the *authoring model* differ by module, with a rule that new modules use JUnit 5. This gets you 80% of the standardisation benefit at 5% of the cost, and it lets the TestNG estate **decay naturally** as teams touch modules. The meta-lesson: *the best migrations are the ones you do not have to do — a platform that lets two models coexist turns a "rewrite everything" decision into a "decide per module" one, and that is almost always the winning trade.*

### CQ4. "A `@DataProvider` gives every test method a fresh copy of the data. Why would you ever use `@Factory` instead?"

- **Naive answer:** "Because `@Factory` creates separate objects with separate state."  ← *true, but it needs the *when*
- **The trap:** The trap is to answer "when you need per-row setup" without being precise about **which** setup — because the distinction is *where the setup happens* and *how many times it runs*, and that is what determines the cost.
- **Expert answer:** The precise semantics and the decision rule. (1) **`@DataProvider` + `@Test`**: the test **method** is invoked once per row, but the **test instance is shared** — one object, one `@BeforeClass`, N `@BeforeMethod` calls. So per-row setup must live in `@BeforeMethod` (or in the test body), and any **expensive** per-row setup (a container, a browser, a seeded DB) is repeated N times. (2) **`@DataProvider` + `@Factory`**: the **test instance** is created once per row, so `@BeforeClass` runs **once per row** — and it is the *only* way to give each row a **fresh, isolated fixture** (its own container, its own schema, its own browser session) that is set up **once** and torn down **once**. (3) **The decision rule**: *if the expensive setup is per-row and you want it once per row → `@Factory`; if the setup is per-test-invocation → `@DataProvider`.* And a very common, very good pattern: **`@Factory` + `@BeforeClass` for the heavy fixture, `@BeforeMethod` for the light per-invocation setup.** (4) The **cost of `@Factory`** — and this is what the interviewer's push is really about — is that it **changes the identity model**: each row becomes a *separate test class instance*, which affects parallelism (each instance is a scheduling unit), reporting granularity, and the `IInvokedMethod` listener's view of the run. So `@Factory` is the right tool, but it is a **bigger** tool. The deeper lesson: *the difference between "a test that runs N times" and "N tests" is a difference in the **model**, not in the mechanics — and models have costs that only show up at scale, which is exactly why `@Factory` is an enterprise-suite tool.*

### CQ5. "You said the `testng.xml` is a strength. Isn't a 2000-line XML file just configuration debt?"

- **Naive answer:** "No, because it's declarative and reviewable."  ← *correct, but it dodges the real objection*
- **The trap:** The trap is to defend XML as "declarative and reviewable" without addressing that **2000 lines of XML is also 2000 lines of things that can go wrong** — no type checking, no refactoring support, no IDE navigation, and merge conflicts that are worse than code's. The question deserves a genuine answer, not a rhetorical one.
- **Expert answer:** The balanced case, and the modern alternative. (1) **What the XML genuinely buys**: (a) a **single, complete description of the suite** — which classes, which groups, in which order, with which parameters, and with what listeners — that a **non-programmer** (a QA lead, a release manager, a compliance officer) can read and change; (b) **declarative, versionable, diffable** — a `git diff` on the suite definition shows exactly which tests a release will run, which is a *release-audit* artefact; (c) **no recompilation** to change the suite's shape, which is what makes "run only the smoke group" a *runtime* operation. (2) **The genuine costs** — and they are real: no type safety, no IDE refactoring, no autocomplete, merge conflicts, and a **second source of truth** alongside the annotations (the classic bug: the XML and the annotations disagree — the XML's `<include>` silently excludes a class, and nobody notices until the release). (3) **The modern resolution**: the JUnit Platform's **`LauncherDiscoveryRequest` / `junit-platform.properties`** and the **`@Suite` + `@SelectPackages`** annotations give a *code-based* suite definition that is type-safe, refactorable, and IDE-navigable — while **still** allowing a suite to be selected at runtime via tags, filters, or system properties. So the capability TestNG's XML provided (runtime-selectable suites) is now available **without** the XML. (4) The **practical recommendation** for a team with a large XML: *do not rewrite it — that is a migration with no business value. Instead, **treat it as configuration**: keep it, but add a **validation step** that the XML and the annotations agree (a test that asserts the XML includes every class the project expects), so the "second source of truth" risk is managed.* The deeper lesson: *the right question about a configuration format is not "is it declarative?" but "**who needs to change it, and what breaks when they get it wrong?**" — TestNG's XML wins for non-programmer authors and release auditing; it loses for type safety; and today's platform has made the gap much smaller.*

### CQ6. "TestNG lets you run tests in parallel. Isn't that a big win? Why is it also a risk?"

- **Naive answer:** "Parallelism makes the suite faster."  ← *true, and it misses half the story*
- **The trap:** The trap is to treat parallelism as a pure win. It is a **trade**: you buy wall-clock time with **isolation risk** and **flakiness risk**. A candidate who only knows the upside has not run a parallel suite.
- **Expert answer:** The trade, precisely, with the failure modes. (1) **The upside is real and large**: a 40-minute serial suite becomes 8 minutes across 8 forks — and for a large enterprise suite, that is the difference between a suite that runs per-commit and one that runs nightly. (2) **The price — shared state**: parallel execution exposes *every* implicit dependency between tests. A `static` field, a shared temp file, a **fixed port**, a **shared database row**, a non-thread-safe `SimpleDateFormat` in a static field — all of these are *invisible* in a serial run (where the order happens to work) and *flaky* in a parallel one. This is the single biggest cause of "the suite was green, then we enabled parallelism and 5% of the tests went flaky". (3) **The price — ordering**: `priority` and `dependsOnMethods` are **not a safe ordering mechanism** under parallelism; TestNG's `preserve-order` and `group-by-instances` settings control it, and the discipline is to make the tests **order-independent** and use `dependsOnMethods` only for a genuine, documented dependency. (4) **The price — the diagnosis**: a failure in a parallel run is harder to reproduce, because the reproduction depends on the *interleaving*. The mitigation is to be able to **re-run a single test in isolation** and to make the suite deterministic. (5) **The safe adoption path**: enable parallelism **one dimension at a time** — first `methods`, then `classes` — and run the suite serially and in parallel in CI, comparing the results. If a test fails only in parallel, you have found a shared-state bug (in the test *or* in the production code). The deeper lesson: *parallelism does not make a suite faster, it makes the suite's **hidden dependencies visible** — which is a feature, but only if you are prepared to fix them. A team that enables parallelism to save CI minutes and then spends weeks chasing flakes has made a bad trade.*

### CQ7. "What is the point of a listener? I could just use `@AfterMethod` and write my own reporting."

- **Naive answer:** "A listener is a hook that gets called on test events."  ← *correct, and it misses the difference in kind*
- **The trap:** The trap is to see the listener as "just a hook", which makes it look like a convenience wrapper over `@AfterMethod`. The real difference is **scope and the ability to observe what you cannot control** — a listener sees the *framework's own events* (configuration failures, skips, retries, the suite start/end), which a method-level hook cannot.
- **Expert answer:** What only a listener can do, and the trade-offs. (1) **The listener receives the framework's event stream** — `IInvokedMethod`, `ITestResult`, `ISuite`, `IConfigurationListener` — which includes things a `@AfterMethod` **cannot see**: a **configuration method that failed** (a `@BeforeMethod` that blew up), a **skipped** test, a test that was **retried**, the **suite start and end**, the **configuration's** result, and the `IConfigurationResult`. That last one matters: a common production problem is "the tests all skipped and the suite was green" — only a listener that watches the *configuration* can detect it. (2) **The scope**: a listener is registered **globally** (`@Listeners` on a base class, or in the XML, or via a `ServiceLoader` `META-INF/services/org.testng.ITestNGListener`), so it applies to **every** test in the run, including ones you did not write — that is what makes it the right home for cross-cutting reporting, metrics emission, and screenshot capture. (3) **The trade-offs — and be honest about them**: a listener is **global, invisible, and hard to debug**; a failure inside a listener can **swallow the test result** (the classic pitfall — a listener that throws, and the report shows the test as passed); and a listener is **not portable** to JUnit 5 (where the equivalent is an `Extension` — a different model, with ordering rules and no suite-level event). (4) The **modern assessment**: for *reporting* specifically, the JUnit Platform's built-in listeners do the job better and are framework-agnostic, so the case for a custom TestNG listener has narrowed to: **a corporate reporting requirement** (a bespoke XML feed, a historical dashboard, a coverage tool) or a **cross-cutting concern** (screenshot on failure, trace-context propagation into logs). The deeper lesson: *a listener is a **cross-cutting policy hook** — use it for things that must apply *everywhere* (reporting, metrics, tracing) and that the framework cannot do for you; do not use it for logic that belongs to a test, and never let it be able to change a test's outcome.*

### CQ8. "You said `assertEquals` on collections ignores the order by default in TestNG. Why would it do that? That seems like a trap."

- **Naive answer:** "Because sets are unordered."  ← *partly right, and it misses the real reason*
- **The trap:** The trap is to explain it as "because collections can be unordered". TestNG's behaviour is actually **deliberate and asymmetric**, and the trap inside the trap is that a candidate who explains it as a general principle has not noticed that **the same assertion on two `List`s in the same test can behave differently** depending on the *runtime types*.
- **Expert answer:** The precise semantics and the real hazard. (1) **What TestNG actually does**: `Assert.assertEquals(actual, expected)` for collections compares them **as sets** (order-insensitive) by default, and there is an **overload** `assertEquals(actual, expected, boolean shouldBeSameOrder)` to opt into the order-sensitive comparison. (2) **The reason it is *not* as crazy as it sounds**: TestNG's authors wanted the assertion to work for **both** `List` and `Set` with one method — and a `Set` has no order, so a single "collection equals" semantic must be order-insensitive to be correct for sets. The **asymmetry** is the real problem: the *declared* type of the variable is irrelevant; the **runtime type** decides. So `assertEquals(list1, list2)` may compare as sets, while `assertEquals(new ArrayList<>(list1), list2)` compares in order — because the runtime types differ. (3) **The hazard**: a test that asserts two lists are equal **passes even if the order changed**. If order *matters* (a sorted output, a sequence of steps, an ordered API response), the test is **not testing what you think it is** — and it will not fail when the ordering breaks. (4) **The mitigations**: (a) use the **explicit** overload `assertEquals(actual, expected, true)` whenever order matters; (b) better, avoid relying on the framework's overload resolution and assert on something unambiguous — `assertEquals(actual.toString(), expected.toString())` is worse, but `assertEquals(new ArrayList<>(actual), expected)` or comparing a `List` explicitly with `assertTrue(actualList.equals(expectedList))` makes the semantics **visible in the code**; (c) the best fix: for anything where order matters, **the domain type should say so** — compare a `Set` when order is irrelevant, and a `List` (with an explicit `equals`) when it is. The deeper lesson: *a comparison with **implicit, type-dependent semantics** is a latent bug — the safest assertion is the one whose semantics you can read off the source, and `List.equals` is exactly that, whereas "the framework decided for me" is not.*

### CQ9. "You said a `@DataProvider` that returns a one-dimensional array is a pitfall. What actually goes wrong?"

- **Naive answer:** "The test only runs once with the whole array as one argument."  ← *right, and it misses why it is so easy to hit*
- **The trap:** The trap is to see it as a syntax slip. The deeper problem is that TestNG's overload resolution makes the **wrong version compile and run** — it does not error — and the failure appears as "the test ran once with a `String[]` argument" and the assertion fails with a confusing message. It is easy to hit because the *one-dimensional* form looks like the *two-dimensional* form with the inner braces removed.
- **Expert answer:** The mechanics, the symptom, and the discipline. (1) **The mechanics**: a `@DataProvider` returns `Object[][]` (rows × columns) by convention. If you return `Object[]` (one row), TestNG treats the **whole array as a single argument array** — it does not "detect" that you meant rows. The result: the test is invoked **once**, with the array's elements spread as the arguments, and if the arity does not match the test's parameters, you get a `TestNGException` about the parameter count. (2) **Why it is easy to hit**: the two-dimensional form `new Object[][]{{a, b}, {c, d}}` and the one-dimensional `new Object[]{a, b}` differ by **one pair of braces**, and the compiler accepts both. There is no type error, no warning. (3) **The symptom in the report**: a single test invocation where you expected N, with an argument array dumped in the failure message — the "why did my data-driven test run once?" mystery. (4) **The discipline**: (a) the **two-dimensional form is the only form** — every row is `{arg1, arg2}`; (b) declare the return type as `Object[][]` so the compiler helps you; (c) if you want a single argument, wrap it: `new Object[][]{{value}}`; (d) and the best practice — **name the columns** with an `Object[][]` plus a `Map`-based variant (TestNG 7.6+ supports a `Map<String, Object>` return, and JUnit's `@CsvSource` is the modern equivalent) so the report shows *which* case failed rather than "row 47, index 1". The deeper lesson: *a data-driven test is only as good as the **readability of its failure** — a report that says "case 47 failed" is worth ten that say "arguments: [a, b, c]" — and the arity of the provider's return type is the first lever on that readability.*

### CQ10. "How do you choose between TestNG and JUnit 5 in practice? Give me a decision procedure."

- **Naive answer:** "JUnit 5 for new projects, TestNG for existing ones."  ← *a good first line, and it needs the *why* and the edges*
- **The trap:** The trap is to stop at a slogan, because the interviewer wants to see the **edges** — the cases where the answer is genuinely "TestNG", and the cases where a team has misdiagnosed the problem.
- **Expert answer:** The decision procedure, in order. (1) **Is there an existing suite?** → *Do not migrate for its own sake.* Standardise on the **platform**, leave the TestNG modules as they are, and require JUnit 5 for new modules. This is the answer that wins in ~80% of real cases. (2) **Is the project new?** → **JUnit 5**, essentially always: better ergonomics, the composable extension model, better IDE support, better parameterised-test ergonomics, and it is where the ecosystem investment is. (3) **Is the suite heavily data-driven with a *declarative, non-programmer-editable* suite definition, and/or must it run on the JUnit Platform for a corporate reporting pipeline?** → **TestNG** is a defensible choice, and you should say *why* in those terms (data providers with named cases, the XML suite, groups). (4) **Are there hard constraints?** → the **reactive** stack (Jest, RxJava) is a JUnit 5 strength; **Spring Boot's** starter is JUnit 5; **legacy XML** is TestNG. (5) **Is the team already expert in one?** → a strong factor, and one that is often under-weighted. (6) **The tie-breaker that is actually decisive**: *the **cost of the switch**, not the feature list*. A candidate who frames the answer as "here is the context, here is the cost, here is the hybrid" is demonstrating the judgement the interviewer is testing. And the **closing meta-point**: the *best* answer acknowledges that this is a **people-and-cost decision as much as a technical one** — a framework is a tool the team must be able to debug at 2 a.m., and the best framework is the one your team can debug.

### CQ11. "TestNG's `Assert` has `assertEquals(actual, expected)`. Isn't that just a bad design that should have been fixed?"

- **Naive answer:** "Yes, it's inconsistent with JUnit and should have been the other way round."  ← *a plausible opinion, and it misses the reason it stayed*
- **The trap:** The trap is to treat it as a plain mistake. It is not — it is a **deliberate, dated, contract-bound** decision, and understanding *why* it stayed is the difference between a candidate who has used TestNG and one who has only read about it.
- **Expert answer:** The history, the reason it persisted, and the honest assessment. (1) **The history**: TestNG's `Assert` predates JUnit 5 by over a decade, and it was designed by the same author who designed **JUnit 4** (both were the work of **Cédric Beust**). The `(actual, expected)` order was the framework's original convention, and it was carried into JUnit 4 — then JUnit 5 deliberately **flipped** it to `(expected, actual)`, because the JUnit 5 team made the argument that "expected" is the *specification* and should be read first. (2) **Why TestNG kept it**: **backwards compatibility**. A framework that changes the argument order of a method used in a million assertions **breaks every existing test in the world** — and the message is ambiguous ("expected [x] but found [y]"), so there is no compile-time warning. A major-version break was not worth the consistency. (3) **The honest assessment of the design**: it *is* worse, in one specific way — the failure message reads "expected [actual] but found [expected]", which inverts the natural reading order, and that has caused **real, documented migration bugs**. But it is *not* the "expected-vs-actual" problem that dominates some other languages, and a framework that has been stable for a decade with a documented order is not broken. (4) **The practical guidance**: (a) in a **new** TestNG project, **standardise on a wrapper** — a small `Assertions` class of your own with `(expected, actual)` order and clear messages — so the convention is yours and a future migration is a one-line change; (b) in a **migration**, run the old and new suites and **diff the failure messages**; (c) when reading a failure message, **always check which side is which** before acting. The deeper lesson: *an API's argument order is a **published contract** — and once it is published, it stops being a design question and becomes a **compatibility** question; the interesting engineering judgement is not "which order is better" but "how do we live with the one we inherited".*

### CQ12. "You mentioned TestNG can run on the JUnit Platform. What does that actually buy us — and what does it not?"

- **Naive answer:** "It means you can run TestNG and JUnit tests together in one build."  ← *true, and it is the smaller half of the benefit*
- **The trap:** The trap is to stop at "they coexist". The **bigger** benefit is infrastructural: the platform gives TestNG a **modern discovery, launcher, reporting and IDE integration model** — and the **limits** are equally important: it does **not** unify the *authoring* models, the assertion semantics, or the extension points.
- **Expert answer:** The precise boundary of what the platform unifies. (1) **What the platform provides to TestNG** (via the `testng-engine`): the **JUnit Platform Launcher** (the single entry point), **discovery** (classpath scanning, filters), the **reporting** and the **hierarchical test engine** model, the **IDE integration** (IntelliJ and Eclipse understand the platform, so a TestNG suite gets modern navigation and rerun-failed), and the **CI integrations** (the Gradle/Maven `junit-platform` integration). So a TestNG suite gets a **modern toolchain** it did not write itself — a genuine and immediate win. (2) **What it does NOT unify**: the **authoring model** (a TestNG test is a method in a suite with a group structure; a JUnit 5 test is a method on an instance with a composable extension model — these are genuinely different); the **assertion semantics** (different libraries, different argument order, different exception idioms); the **configuration model** (XML versus annotations); and the **extension mechanism** (a TestNG `IInvokedMethodListener` versus a JUnit 5 `Extension` — different lifecycles, different ordering, different capabilities). (3) **The practical consequence**: the platform is a **seam at the execution layer**, so you can standardise your **tooling** (one CI job, one report format, one IDE behaviour) while the **authoring** differs by module. And the **migration** implication: a module can move from TestNG to JUnit 5 **one class at a time**, because the platform does not care which engine discovered the test — the *test engine* is behind the platform. (4) **The honest caveat**: the `testng-engine` is a **compatibility layer**, and it is maintained by the community rather than by the JUnit team; it does not offer every TestNG feature with full fidelity, and some edge behaviours can differ from a native TestNG run. The deeper lesson: *the platform unified the layer where unification is genuinely valuable — **execution and infrastructure** — and deliberately left **expression** alone, because that is where the frameworks' philosophies differ. Any "can we have one framework" conversation should be split into those two layers first, because the answer differs for each.*

---

## 10. Rapid-Fire MCQs

**Q1.** TestNG's `assertEquals` argument order is…
- A) `(expected, actual)`  B) **`(actual, expected)`**  C) `(actual, expected, message)`  D) It varies

**Q2.** JUnit 5's `assertEquals` argument order is…
- A) **`(expected, actual)`**  B) `(actual, expected)`  C) `(expected, actual, message)`  D) It varies

**Q3.** TestNG was designed by the same author as…
- A) JUnit 5  B) **JUnit 4**  C) Spock  D) Cucumber

**Q4.** `@DataProvider` + `@Test` gives you…
- A) A new instance per row  B) **One test method invoked once per data row, sharing the instance**  C) A new class per row  D) Parallel execution

**Q5.** `@DataProvider` + `@Factory` gives you…
- A) A shared instance  B) **A new test instance (and a new `@BeforeClass`) per data row**  C) A new JVM  D) A new thread

**Q6.** A `@DataProvider` must return…
- A) `Object[]`  B) **`Object[][]`**  C) `List<Object>`  D) `Iterator<Object>`

**Q7.** A `@DataProvider` method must be…
- A) `private`  B) **`static` (or on a class that is not the test class)**  C) `final`  D) `public` — always

**Q8.** TestNG's suite definition file is…
- A) `junit-platform.properties`  B) **`testng.xml`**  C) `testng.yaml`  D) `suite.conf`

**Q9.** TestNG's grouping mechanism is…
- A) `@Tag`  B) **`groups`**  C) `@Category`  D) `@Label`

**Q10.** In TestNG, `assertEquals` on two collections by default…
- A) **Ignores the order**  B) Requires the same order  C) Throws an error  D) Compares by `hashCode` only

**Q11.** To make a TestNG collection assertion order-sensitive, you use…
- A) `assertEquals(actual, expected, true)`  B) `assertEqualsOrdered(...)`  C) `assertSame(...)`  D) `assertListEquals(...)`

**Q12.** TestNG's `@BeforeClass` runs…
- A) Before every test method  B) **Once per test class (once per `@Factory` instance)**  C) Once per suite  D) Once per group

**Q13.** TestNG's `@BeforeSuite` has no direct JUnit 5 equivalent; the closest is…
- A) `@BeforeAll`  B) **A separate suite / a launcher configuration**  C) `@BeforeEach`  D) `@BeforeTest`

**Q14.** The TestNG extension point for cross-cutting behaviour is…
- A) An `Extension`  B) **An `ITestNGListener` / `IInvokedMethodListener`**  C) A `@Rule`  D) A `TestWatcher`

**Q15.** TestNG's equivalent of JUnit 5's `@Tag` / filtering is…
- A) `@Tag`  B) **`groups` + the XML or `-Dgroups=`**  C) `@Category`  D) `@Label`

**Q16.** TestNG can now run on the **JUnit Platform** because…
- A) It was rewritten from scratch  B) **A `testng-engine` adapter plugs TestNG into the platform**  C) JUnit 5 supports it natively  D) Maven supports it

**Q17.** A listener that throws an exception can…
- A) Be ignored safely  B) **Change or swallow the test's result**  C) Not affect the run  D) Be automatically retried

**Q18.** TestNG's `priority` attribute controls…
- A) The JVM thread priority  B) **The order in which methods run**  C) The suite order  D) Nothing reliable under parallelism

**Q19.** The classic JUnit 4 → TestNG migration bug is…
- A) The package name  B) **The flipped `assertEquals` argument order**  C) The `main` method  D) The classpath

**Q20.** TestNG's `dependsOnMethods` is best used for…
- A) Performance tuning  B) **A genuine, documented dependency between tests**  C) Grouping  D) Reporting

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | TestNG is `(actual, expected)` — the reverse of JUnit 5. |
| 2 | **A** | JUnit 5 is `(expected, actual)`. |
| 3 | **B** | Cédric Beust wrote both TestNG and JUnit 4. |
| 4 | **B** | The method is invoked per row, but the instance is shared. |
| 5 | **B** | `@Factory` creates a new instance — so `@BeforeClass` runs per row. |
| 6 | **B** | `Object[][]` — rows × columns. |
| 7 | **B** | `static` (or in a separate class), so the instance does not exist yet. |
| 8 | **B** | `testng.xml` — TestNG's declarative suite file. |
| 9 | **B** | `groups` — e.g. `@Test(groups = "smoke")`. |
| 10 | **A** | Order-insensitive by default; the overload opts into order. |
| 11 | **A** | The `assertEquals(actual, expected, shouldBeSameOrder)` overload. |
| 12 | **B** | Once per class — and once per `@Factory` instance. |
| 13 | **B** | JUnit 5 has no suite-scoped lifecycle; it lives in the launcher config. |
| 14 | **B** | `ITestNGListener` / `IInvokedMethodListener`. |
| 15 | **B** | `groups`, selected by the XML or `-Dgroups=smoke`. |
| 16 | **B** | The `testng-engine` adapts TestNG to the platform's engine API. |
| 17 | **B** | A listener runs inside the framework's reporting path — it can alter the result. |
| 18 | **B** | The execution order of the methods — not a JVM thread priority. |
| 19 | **B** | The argument order flips, so every failure message is inverted. |
| 20 | **B** | Only for a real dependency — and it serialises the run. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│  THE LIFECYCLE MAP — TestNG vs JUnit 5                                 │
│                                                                          │
│  TestNG                        JUnit 5                                  │
│  ──────                        ───────                                  │
│  @BeforeSuite                  (none — a separate suite)                │
│  @BeforeClass                  @BeforeAll      (once per class)          │
│  @BeforeMethod                 @BeforeEach     (before each test)        │
│  @AfterMethod                  @AfterEach                               │
│  @AfterClass                   @AfterAll                                │
│  @AfterSuite                   (none)                                   │
│  @BeforeGroups/@AfterGroups    @Nested / @Tag                           │
│  @DataProvider                 @ParameterizedTest + @MethodSource        │
│  @Factory                      @TestFactory                             │
│  @Parameters (XML)             (a system property)                      │
│  groups                        @Tag                                     │
│  ITestNGListener               Extension                                │
│  testng.xml                    (none by design)                         │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│  THE TWO DATA-DRIVEN FORMS — and when to use each                      │
│                                                                          │
│  ❶ @DataProvider + @Test                                               │
│     → ONE instance, N invocations                                       │
│     → the PER-INVOCATION fixture (@BeforeMethod) runs N times            │
│     → USE IT WHEN: the setup per row is CHEAP                         │
│                                                                          │
│  ❷ @DataProvider + @Factory                                            │
│     → N instances, each with its OWN @BeforeClass                      │
│     → the PER-ROW fixture runs ONCE per row                             │
│     → USE IT WHEN: the setup per row is EXPENSIVE (a container, a      │
│       browser, a seeded schema) — or each row needs ISOLATION           │
│                                                                          │
│  ⚠ THE PROVIDER MUST RETURN Object[][] — rows × columns                 │
│  ⚠ A 2D array with ONE pair of braces too few = "the test ran once"   │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│  THE 10 TESTNG TRAPS                                                   │
│  1. assertEquals(actual, expected) — the order is REVERSED vs JUnit 5  │
│  2. A 1D provider array → the test runs ONCE with the whole array      │
│  3. A non-static @DataProvider → "no such method" at runtime          │
│  4. @DataProvider + @Test sharing instance state across rows           │
│  5. A non-static @BeforeMethod with a @Factory → N× the setup          │
│  6. Relying on priority for the order — fragile, and unsafe in parallel │
│  7. assertEquals on collections ignoring the ORDER by default          │
│  8. testng.xml and the annotations DISAGREEING (a silent exclusion)    │
│  9. A listener that throws → it SWALLOWS the test result               │
│ 10. Enabling parallelism → every hidden shared-state bug surfaces       │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### Where TestNG still wins in production

| Context | Why TestNG fits |
|---|---|
| **A large enterprise suite** (thousands of tests, decades of history) | The migration cost dwarfs the feature benefit |
| **A data-heavy matrix suite** (a test run against 500 DB rows) | `@DataProvider` yields **named, individually-reported** cases |
| **A regulated environment** needing a reviewable suite definition | `testng.xml` is a diffable, approvable release artefact |
| **A corporate reporting pipeline** on the JUnit Platform | The `testng-engine` plugs into the modern launcher and reports |
| **A hybrid estate** — TestNG + JUnit 5 in one build | The platform makes coexistence a configuration, not a rewrite |

### The house rules for a TestNG suite

| Rule | Why |
|---|---|
| **Wrap `Assert` in your own `Assertions` class** | Own the convention and the message; a future migration becomes one line |
| **Never rely on collection order** — use the `shouldBeSameOrder` overload | The default is order-insensitive; a silent gap in the assertion |
| **Every `@DataProvider` returns `Object[][]`** | The one-dimensional form compiles and misbehaves |
| **Every `@DataProvider` is `static`** | It is called before the instance exists |
| **Prefer `@Factory` for expensive per-row fixtures** | `@BeforeClass` then runs once per row |
| **Keep the tests order-independent** | `priority` and `dependsOnMethods` are a last resort, and they serialise the run |
| **Enable parallelism one dimension at a time** | `methods` first, then `classes` — and diff the results against a serial run |
| **A listener must never throw** | It runs in the reporting path and can swallow the result |
| **Validate the `testng.xml` against the source tree** | The XML is a second source of truth, and it drifts |

### The CI shape

```text
   EVERY COMMIT — the GATE
   ☐ mvn -Dgroups=smoke test          ⚡ the fast slice
   ☐ the unit + component suites
   ☐ the report published in the standard format

   NIGHTLY — the REPORT
   ☐ the full suite, in parallel (with the serial diff run)
   ☐ the group matrix (smoke, regression, e2e, quarantine)
   ☐ the flaky-test list, with an owner per entry
```

### The migration play — TestNG → JUnit 5, incrementally

```text
   Step 1  ☐ Move the build to the JUnit Platform with BOTH engines
           ☐ The suite must be GREEN before anything else — no exceptions
   Step 2  ☐ Diff the failure messages against the old suite
           ☐ Every inversion is an argument-order bug — fix them NOW
   Step 3  ☐ Migrate ONE module (not the suite) — the leaf module
           ☐ Both engines coexist; the platform does not care
   Step 4  ☐ Migrate the shared utilities last (they are the widest blast radius)
   Step 5  ☐ Delete the TestNG engine only when the XML selects ZERO classes
           ☐ ⭐ THE DEFINITION OF DONE — measured by the XML, not by the code
```

### Observability

| Metric | What it tells you |
|---|---|
| The suite's wall-clock time | The parallelisation win |
| The serial-vs-parallel diff | ⚠ The shared-state bugs you have not fixed |
| The group sizes | Whether the smoke slice is still fast enough to gate every commit |
| The flaky-test count | The trust problem — the most important number |
| The XML-vs-source drift | The silent exclusions |
| The migration progress (classes still in the XML) | The only honest definition of done |

### The review checklist

```text
  ☐ Does the assertion use OUR wrapper (not TestNG's Assert directly)?
  ☐ Is the argument order correct — and does the MESSAGE read right?
  ☐ Does the provider return Object[][]?
  ☐ Is the provider static?
  ☐ Are the tests independent of each other and of the order?
  ☐ If a collection's ORDER matters, is that explicit?
  ☐ Is the expensive setup in @BeforeClass (with @Factory) rather than repeated?
  ☐ If there is a listener, can it swallow a result? (It must not.)
  ☐ Does the testng.xml still include this class?  ← the silent-exclusion check
```

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The framework most projects use | [01 · JUnit 5](01-JUnit-Basics.md) |
| Test doubles and mocking | [02 · Mockito & Mocking](02-Mockito-Mocking.md) |
| The practices that make a suite good | [04 · Testing Best Practices](04-Testing-Best-Practices.md) |
| Testing concurrent code | [08 · Concurrency Problems](../08-Multithreading-Concurrency/04-Concurrency-Problems.md) |
| Testability via SOLID | [10 · SOLID Principles](../10-SOLID-Principles/01-SOLID-Principles.md) |
| Testing a serialised format | [17 · Serialization](../17-Serialization/03-Serialization-Best-Practices.md) |

---

**Next Chapter:** → [04 · Testing Best Practices](04-Testing-Best-Practices.md)
**Previous Chapter:** ← [02 · Mockito & Mocking](02-Mockito-Mocking.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
