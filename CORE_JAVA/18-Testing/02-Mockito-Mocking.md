# Mockito & Mocking — The Complete Interview Guide

> **Mocking is a tool with a narrow job.** It lets you control a
> **non-deterministic** or **slow** collaborator. Use it anywhere else and you
> have replaced a *behaviour* test with a *coupling* test that breaks on every
> refactor.

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

- **A mock is a controlled, programmable double.** You set the *behaviour* (what it returns) and then *verify* the *interaction* (what was called).
- **The three kinds of test double:** **fakes** (a working implementation, e.g. an in-memory repository), **stubs** (a canned response — a mock with a fixed return), and **mocks** (a programmable double whose *interactions* you assert on).
- **The three frameworks:** **Mockito** (the default — a dynamic proxy / ByteBuddy), **Spring Boot's `@MockBean`** (a Mockito mock wired into the Spring context), and **MockK** (Kotlin-first, with a nicer API).
- **The core Mockito API:** `@Mock`, `@Spy`, `@InjectMocks`, `when(...).thenReturn(...)`, `verify(...)`, `verifyNoInteractions(...)`, `ArgumentCaptor`, `assertThrows` with a mock, `doThrow`/`doAnswer` for `void` methods, `mockingDetails`, and `lenient()`.
- **`@Mock` vs `@Spy`:** a `@Mock` does **nothing** by default (every method returns a default value); a `@Spy` is a **real object** with the calls recorded.
- **`@InjectMocks` injects mocks into the fields of the object under test** — by constructor, then by setter, then by field. It is convenient and it is **brittle** (it silently does nothing if the wiring does not match).
- **`when(...)` vs `doReturn(...).when(...)`:** use `when` for a non-void method and **`doReturn`** for a `void` method (because `when(mock.voidMethod())` actually *calls* the method).
- **The three "mocks smell":** (1) verifying the *number* of calls (`times(3)`) for a method that is an implementation detail, (2) stubbing a method the caller does not actually need, and (3) `verify` on a *private* method (which only works because of the inline mock maker, and which couples to the implementation).
- **The mocking anti-pattern:** a test with more mock setup lines than production code lines. That is a signal to **refactor the design** (the class under test has too many collaborators) — not to add more mocks.
- **The `mockito-inline` mock maker** (now the default in Mockito 5) can mock `final` classes and `static` methods — but it uses a Java agent, so it has a startup cost and it can conflict with other agents.

---

## 1. What & Why — The Problem This Solves

**The problem.** A unit test must run **fast**, **deterministically** and **in
isolation**. Three things break that:

```text
   ① SLOW          — a real HTTP call takes 200 ms (and 0 ms when it is down)
   ② NON-DETERMINISTIC — the clock, a random number, a concurrent system
   ③ EXTERNAL       — the database, the message broker, the payment gateway
```

**The solution.** Replace the collaborator with a **double** that behaves the way
the test needs.

```text
   ❌ WITHOUT A DOUBLE
   ───────────────────
   @Test void chargesTheCustomer() {
       PaymentGateway gateway = new RealPaymentGateway("sk-live-abc");   // 💥 a real call
       OrderService service = new OrderService(gateway);
       service.pay(order);
       // → 200 ms, needs a network, fails when the network fails
       // → and there is NO WAY to assert that the right amount was charged
   }

   ✅ WITH A DOUBLE
   ────────────────
   @Test void chargesTheCustomer() {
       PaymentGateway gateway = mock(PaymentGateway.class);
       when(gateway.charge(any())).thenReturn(PaymentResult.approved("txn-1"));
       OrderService service = new OrderService(gateway);

       PaymentResult result = service.pay(order);

       assertTrue(result.success());
       // → 0 ms, deterministic, and now you can ASSERT the interaction:
       verify(gateway).charge(argThat(r -> r.amount().equals(new BigDecimal("25.00"))));
   }
```

> 💡 **The interview framing.** The question is never "how do I mock?" It is
> **"should I mock this?"** The answer is a *design* question: *is this collaborator
> at a boundary you do not control, or is it part of my own domain?* Mocking the
> second is a design smell.

---

## 2. Mental Model — The One Picture

```text
   THE THREE KINDS OF TEST DOUBLE
   ══════════════════════════════

   ❶ FAKE — a WORKING implementation
      class InMemoryUserRepository implements UserRepository {
          Map<String,User> store = new HashMap<>();
          public User findById(String id) { return store.get(id); }
          public void save(User u) { store.put(u.id(), u); }
      }
      ✅ you assert on the OUTCOME
      ⚠ it is a SECOND implementation — it can drift from the real one

   ❷ STUB — a CANNED response
      when(gateway.charge(any())).thenReturn(PaymentResult.approved("txn-1"));
      ✅ fast and simple
      ⚠ it says nothing about HOW the collaborator behaves

   ❸ MOCK — a PROGRAMMABLE double whose INTERACTIONS you assert on
      verify(gateway).charge(expectedRequest);
      verify(gateway, never()).refund(any());
      ✅ precise, expressive assertions
      ⚠ a COUPLING to the production API's shape


   THE SPECTRUM
   ───────────
   FAKE ──────────────── STUB ──────────────── MOCK
   (behaviour)          (response)            (interaction)
   most realistic       most decoupled        most brittle


   THE MOCKITO MECHANICS
   ─────────────────────
   mock(PaymentGateway.class)
        │
        ▼
   Mockito creates a SUBCLASS (ByteBuddy) at RUNTIME
        │
        ├─ every method is intercepted
        ├─ the default answer returns a DEFAULT VALUE
        │   (null, 0, false, an empty list, …)
        └─ your stubbing (when/thenReturn) takes precedence

   ⚠ A MOCK WITH NO STUBBING RETURNS null / 0 / false
     — so an unstubbed call SILENTLY "succeeds" with a default.
     → use verifyNoMoreInteractions / strict stubs to catch it.
```

**What to look at:** the spectrum from *fake* to *mock* is a spectrum of **how much
you assert about the interaction** — and the risk moves with it: a fake risks
drifting, a mock risks coupling.

---

## 3. Deep Dive — Theory

## 3.1 The Mockito setup

```java
@ExtendWith(MockitoExtension.class)      // ⚠ needed for @Mock/@InjectMocks
class OrderServiceTest {

    @Mock
    private PaymentGateway gateway;        // a mock — does nothing by default

    @Mock
    private InventoryClient inventory;

    @Spy
    private RealPriceCalculator calculator = new RealPriceCalculator();  // a real object, recorded

    @InjectMocks
    private OrderService service;           // the object under test — the mocks are injected

    @Test
    void chargesTheCustomer() {
        when(gateway.charge(any())).thenReturn(PaymentResult.approved("txn-1"));
        // …
    }
}
```

**`@InjectMocks` — the injection strategies, in order:**

1. **Constructor** injection (the mock is passed to the constructor) — the most robust.
2. **Property/setter** injection.
3. **Field** injection (the mock is set on the field by type).

**The brittleness:** if the constructor takes two mocks of the **same type**,
`@InjectMocks` cannot tell them apart — it injects the same one twice, and the test
fails with a confusing `NullPointerException`. And if the class under test has **no
no-arg constructor**, `@InjectMocks` may inject nothing at all — **silently**.

**✅ The production recommendation: construct the object under test explicitly.**

```java
@Mock private PaymentGateway gateway;
@Mock private InventoryClient inventory;

private OrderService service;

@BeforeEach
void setUp() {
    service = new OrderService(gateway, inventory);   // ✅ explicit, obvious, refactor-safe
}
```

## 3.2 Stubbing

```java
// THE BASIC FORM
when(gateway.charge(any())).thenReturn(PaymentResult.approved("txn-1"));
when(gateway.charge(any())).thenThrow(new GatewayTimeoutException("timeout"));
when(gateway.getBalance(anyString())).thenReturn(new BigDecimal("100.00"));
when(gateway.getBalance(anyString())).thenReturn(a, b, c);   // consecutive calls

// SPECIFIC ARGUMENTS
when(gateway.charge(argThat(r -> r.amount().compareTo(new BigDecimal("25.00")) == 0)))
        .thenReturn(PaymentResult.approved("txn-1"));

// THE VOID-METHOD FORM — ⚠ when(mock.voidMethod()) CALLS the method!
doThrow(new IllegalStateException("boom")).when(gateway).close();
doAnswer(invocation -> { … }).when(gateway).charge(any());
doNothing().when(gateway).close();

// THE CAPTURE FORM — grab the argument
ArgumentCaptor<PaymentRequest> captor = ArgumentCaptor.forClass(PaymentRequest.class);
service.pay(order);
verify(gateway).charge(captor.capture());
assertEquals(new BigDecimal("25.00"), captor.getValue().amount());
```

## 3.3 Verification

```java
verify(gateway).charge(any());                      // exactly once
verify(gateway, times(2)).charge(any());
verify(gateway, never()).refund(any());
verify(gateway, atLeastOnce()).charge(any());
verify(gateway, atMost(3)).charge(any());
verifyNoInteractions(gateway);                     // ⚠ nothing was called at all
verifyNoMoreInteractions(gateway);                 // only the verified calls happened
verify(gateway).charge(argThat(r -> r.amount().equals(expected)));
```

**`verifyNoMoreInteractions` is the strictness lever** — it is what makes the
"an unstubbed call silently returned null" problem visible.

## 3.4 Argument matchers

```java
any();                       // any object (not null)
anyString();
anyInt();
any(PaymentRequest.class);
eq(value);                   // equals()
isNull();  notNull();
same(instance);              // the same reference
argThat(matcher);            // a custom lambda matcher
refEq(value);                // equals() ignoring the field's own equals
```

**⚠ The matcher rule:** once you use a matcher in an argument list, **all** the
arguments must be matchers. `when(gateway.charge(eq("x"), any()))` is fine;
`when(gateway.charge("x", any()))` throws `InvalidUseOfMatchersException`.

## 3.5 `when` vs `doReturn` — the void-method rule

```java
// ❌ for a VOID method, this CALLS the method while setting up the stub
when(gateway.close()).thenThrow(new IllegalStateException());   // 💥

// ✅ the correct form for a void method
doThrow(new IllegalStateException()).when(gateway).close();
```

**Why:** `when(mock.method())` needs the *return value* of the call to identify the
method — and a `void` method has none, so Mockito would have to call the real
method. `doReturn(...).when(mock)` puts the "when" *after* the identifier, so the
method is never invoked.

> 💡 **The rule:** `when(...)` for a non-void method; `doReturn(...).when(...)` /
> `doThrow(...).when(...)` for a **void** method. And `doReturn` also works for
> non-void methods, so you can use it consistently.

## 3.6 `lenient()` and strict stubs

```java
// The default in MockitoExtension is STRICT_STUBS
// → an unused stubbing FAILS the test
when(gateway.getBalance("unused")).thenReturn(...);   // 💥 UnnecessaryStubbingException
```

**When it is legitimate to relax it:**

```java
@MockitoSettings(strictness = Strictness.LENIENT)
class MyTest { }

lenient().when(gateway.getBalance(anyString())).thenReturn(...);   // a per-stub relaxation
```

**The rule:** a strict stub is a **feature** — it catches the "I stubbed something
that is no longer called" case, which is a signal that the test is testing a
*former* behaviour. Relax it **deliberately and locally**, and only for a stub that
is genuinely optional.

## 3.7 Mocking statics, finals and constructors

```java
// Static methods (mockito-inline, the default in Mockito 5)
try (MockedStatic<LocalDateTime> mocked = mockStatic(LocalDateTime.class)) {
    mocked.when(LocalDateTime::now).thenReturn(fixedInstant);
    // … the code under test sees the fixed time
}

// A final class
mock(FinalClass.class);   // ✅ with the inline mock maker

// A constructor
mockConstruction(MyClass.class, (mock, context) -> { … });   // ✅ with mockito-inline
```

**The cost:** the `mockito-inline` mock maker installs a **Java agent** — it has a
startup cost, and it can conflict with other agents (Jacoco, a mocking library,
a coverage tool). And mocking statics is **global** — it affects every thread, so a
parallel test run can see the mock.

**The production alternative for the clock:** **inject the clock.**

```java
public class OrderService {
    private final Clock clock;      // ✅ java.time.Clock — real, fixed, or systemUTC
    public OrderService(Clock clock) { this.clock = clock; }
}
// in the test: Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
```

## 3.8 The Spring integration — `@MockBean`

```java
@SpringBootTest
class OrderControllerTest {

    @MockBean                              // ⚠ replaced in the context
    private PaymentGateway gateway;

    @Autowired
    private OrderController controller;    // wired with the MOCK

    @Test
    void returnsTheOrder() {
        when(gateway.findById("O-1")).thenReturn(new Payment("O-1", "txn-1"));
        // …
    }
}
```

**`@MockBean` vs `@Mock`:**

| | `@Mock` | `@MockBean` |
|---|---|---|
| Managed by | Mockito | The **Spring context** |
| Injected into | The object under test | **Every bean** in the context |
| The real bean | Not created | **Replaced** in the context |
| Use for | A plain unit test | A `@SpringBootTest` slice |

> ⚠ **`@MockBean` is deprecated in Spring Boot 3.4+** in favour of
> `@MockitoBean` — the new annotation does not clash with the *other* frameworks'
> `@MockBean`.

## 3.9 The `mockito-junit-jupiter` extension vs manual mocks

```java
// ❶ THE EXTENSION — automatic
@ExtendWith(MockitoExtension.class)
class MyTest {
    @Mock private Gateway gateway;
}

// ❷ MANUAL — explicit and portable
class MyTest {
    private Gateway gateway = mock(Gateway.class);
}
```

**The extension's benefit:** it enables **strict stubs** (the unused-stubbing
detection) and the automatic `@InjectMocks` wiring. **The cost:** the strictness
surprises people, and `@InjectMocks` is brittle — so the recommendation is: **use
the extension, but construct the object under test explicitly.**

## 3.10 MockK — the Kotlin-first alternative

```kotlin
// MockK's API is fluent and reads better
val service = OrderService(gateway)
every { gateway.charge(any()) } returns PaymentResult.approved("txn-1")
verify { gateway.charge(match { it.amount == BigDecimal("25.00") }) }

// The big advantage: MOCKING CONSTRUCTORS and suspend functions
mockkConstructor(OrderService::class)
coEvery { suspendingFunction() } returns x

// And it works for JUnit 4 AND 5
```

---

## 4. Visualizations

### 4.1 The test-double spectrum

```text
   FAKE ────────────────── STUB ────────────────── MOCK
   │                        │                       │
   │  a WORKING impl        │  a canned response     │  a programmable double
   │  (in-memory repo)      │  (thenReturn)          │  whose INTERACTIONS
   │                        │                       │  you assert on
   │                        │                       │
   │  you assert on the     │  you assert on the     │  you assert on the
   │  OUTCOME               │  OUTCOME               │  INTERACTION
   │                        │                       │
   │  ✅ most realistic      │  ✅ fast, simple        │  ✅ precise assertions
   │  ⚠ can drift from the   │  ⚠ says nothing about  │  ⚠ COUPLES to the API's
   │    real impl            │    the collaborator    │    shape — breaks on
   │                        │                       │    refactoring
   ▼                        ▼                       ▼
   USE FOR: an internal   USE FOR: a boundary    USE FOR: asserting the
   collaborator you       whose exact response    exact interaction IS the
   do not care about      you need                point of the test
```

### 4.2 The Mockito mechanics

```text
   mock(PaymentGateway.class)
        │
        ▼
   ByteBuddy generates a SUBCLASS at runtime
        │
        ├─ every method is intercepted
        │
        ├─ the DEFAULT answer (unstubbed):
        │     object → null    int → 0    boolean → false
        │     List → empty    Optional → Optional.empty()
        │     ⚠ so an unstubbed call SILENTLY "succeeds"
        │
        └─ your stubbing takes precedence:
          when(gateway.charge(any())).thenReturn(result)

   ⚠ THIS IS WHY strict stubs matter:
     an unused stubbing → the test FAILS (UnnecessaryStubbingException)
     → it is a signal that the test is asserting a former behaviour
```

### 4.3 The mocking smells — the three

```text
   ❶ VERIFYING AN IMPLEMENTATION DETAIL
   ─────────────────────────────────────
   verify(gateway, times(3)).charge(any());   // ⚠ WHY three?
   → the NUMBER is an implementation detail — a refactor that batches the calls
     breaks the test even though the behaviour is identical
   ✅ assert on the OUTCOME instead

   ❷ STUBBING WHAT THE CALLER DOES NOT NEED
   ───────────────────────────────────────────
   when(gateway.getBalance(anyString())).thenReturn(…);   // ⚠ never called
   → noise; and it hides the fact that the code does not use it
   ✅ strict stubs will FAIL this — which is the point

   ❸ VERIFYING A PRIVATE METHOD
   ─────────────────────────
   verify(gateway, times(1)).toString();   // ⚠ only works with the inline mock maker
   → it couples the test to the implementation, and it is brittle
   ✅ test the public behaviour
```

### 4.4 The mock-setup smell — the design signal

```text
   ❌ 40 lines of mock setup for a 10-line class
   ───────────────────────────────────────────
   class OrderServiceTest {
       @Mock a; @Mock b; @Mock c; @Mock d; @Mock e; @Mock f;
       @BeforeEach void setUp() {
           when(a.x(any())).thenReturn(…);   // 12 stubbings
           when(b.y(any())).thenReturn(…);
           …
       }
       @Test void doesTheThing() { … }
   }

   ✅ THIS IS A DESIGN SIGNAL, NOT A MOCKING PROBLEM
   ──────────────────────────────────────────────────
   The class under test has SIX collaborators → it is doing SIX jobs.

   THE FIXES (in order):
     1. Inject a VALUE (a record, a function) instead of a collaborator
     2. Combine the collaborators behind ONE port (a Facade)
     3. Use a FAKE for the ones that do not matter
     4. Split the class (the SRP fix)
```

### 4.5 `when` vs `doReturn` — the void-method rule

```text
   ❌ FOR A VOID METHOD
   ───────────────────
   when(gateway.close()).thenThrow(ex);   // 💥
   → when(mock.close()) must CALL close() to get an identifier
   → and it has no return value → the call happens during setup!

   ✅ THE CORRECT FORM
   ─────────────────
   doThrow(ex).when(gateway).close();
   → the "when" comes AFTER the identifier → the method is never called ✅
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — The core Mockito workflow

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentGateway gateway;

    @Mock
    private AuditLog auditLog;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(gateway, auditLog);   // ✅ explicit construction
    }

    record PaymentRequest(String customerId, BigDecimal amount, String reference) { }

    @Test
    void chargesTheCorrectAmount() {
        when(gateway.charge(any())).thenReturn(PaymentResult.approved("txn-1"));

        PaymentResult result = service.charge("C-1", new BigDecimal("25.00"), "ref-1");

        assertTrue(result.success());
        assertEquals("txn-1", result.transactionId());

        // ✅ Assert the INTERACTION — with a captor for the argument
        ArgumentCaptor<PaymentRequest> captor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(gateway).charge(captor.capture());
        assertEquals(new BigDecimal("25.00"), captor.getValue().amount());
        assertEquals("C-1", captor.getValue().customerId());
    }

    @Test
    void propagatesAGatewayFailure() {
        when(gateway.charge(any())).thenThrow(new GatewayTimeoutException("timeout"));

        assertThrows(GatewayTimeoutException.class,
                () -> service.charge("C-1", new BigDecimal("25.00"), "ref-1"));

        verify(auditLog).failure(any(), any());   // the audit log was told
        verifyNoMoreInteractions(auditLog);        // ⚠ and nothing else
    }
}
```

**Actual output:**

```text
[INFO] PaymentServiceTest — 2 tests, 0 failures
```

**Takeaway:** the `ArgumentCaptor` is the right way to assert on an argument — it is
more precise than `argThat` and gives a better failure message.

### Example 2 — `doThrow` for a void method, and the trap

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoidMethodDemo {

    @Mock
    private ResourceCloser closer;

    @Test
    void theCorrectFormForAVoidMethod() {
        doThrow(new IllegalStateException("cannot close")).when(closer).close();

        IllegalStateException ex = assertThrows(IllegalStateException.class, closer::close);
        assertEquals("cannot close", ex.getMessage());
    }
}
```

**Actual output:** the test passes.

**The trap, demonstrated:**

```java
// ❌ THIS FORM CALLS THE METHOD DURING SETUP
@Test
void theWrongForm() {
    when(closer.close()).thenThrow(new IllegalStateException("cannot close"));
    // ⚠ MockitoException / InvalidUseOfMatchersException
    //    because when(mock.close()) must CALL close() to get an identifier,
    //    and a void method has no return value
}
```

**Takeaway:** `when(...)` is for a **non-void** method; `doThrow/doReturn/doAnswer(...).when(mock)` is for a **void** method — and it is also fine for a non-void one, so you can use it consistently.

### Example 3 — Strict stubs catching a stale test

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)     // ⚠ strict stubs are the DEFAULT here
class StrictStubDemo {

    @Mock
    private PaymentGateway gateway;

    @Test
    void stubsSomethingTheCodeNoLongerCalls() {
        when(gateway.getBalance(anyString())).thenReturn(new java.math.BigDecimal("100.00"));
        // ⚠ the code under test does NOT call getBalance
        // → the test FAILS with UnnecessaryStubbingException
        // → WHICH IS THE POINT: the test is asserting a FORMER behaviour
    }
}
```

**Actual output:**

```text
org.mockito.exceptions.misusing.UnnecessaryStubbingException:
Unnecessary stubbing detected here:
    → when(gateway.getBalance(anyString())).thenReturn(...);
  in PaymentServiceTest.stubsSomethingTheCodeNoLongerCalls()
```

**Takeaway:** the strict-stub failure is a **feature** — it tells you the test and
the code have diverged. The fix is to delete the stub, not to add `lenient()`.

### Example 4 — Mocking the clock — badly, and well

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClockDemo {

    // ❌ MOCKING THE STATIC — global, agent-based, and it affects every thread
    @Test
    void mockingTheStatic() {
        try (MockedStatic<Instant> mocked = mockStatic(Instant.class)) {
            mocked.when(Instant::now).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));

            assertEquals(Instant.parse("2026-01-01T00:00:00Z"), Instant.now());
        }
    }

    // ✅ INJECTING THE CLOCK — no agent, no global state, thread-safe
    @Test
    void injectingTheClock() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        AuditService service = new AuditService(fixed);

        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), service.timestamp());
    }

    static class AuditService {
        private final Clock clock;
        AuditService(Clock clock) { this.clock = clock; }
        Instant timestamp() { return clock.instant(); }
    }
}
```

**Actual output:** both tests pass — but the second is the one to write.

**Takeaway:** the injected `Clock` is **better in every way** — no agent, no global
state (so it is safe under parallel test execution), no dependency on the mocking
library, and it works in production code (a real `Clock.systemUTC()` in production
and a fixed one in the test). The static mock is a workaround for a design that did
not allow injection.

### Example 5 — The mock-setup smell, and the fix

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmellDemo {

    @Mock private PaymentGateway gateway;
    @Mock private InventoryClient inventory;
    @Mock private PricingService pricing;
    @Mock private NotificationService notifications;
    @Mock private AuditLog audit;
    @Mock private LoyaltyService loyalty;

    @Test
    void theClassUnderTestHasTooManyCollaborators() {
        when(gateway.charge(any())).thenReturn(PaymentResult.approved("t"));
        when(inventory.isAvailable(anyString(), anyInt())).thenReturn(true);
        when(pricing.price(any())).thenReturn(new java.math.BigDecimal("10.00"));
        when(notifications.send(anyString(), anyString())).thenReturn(true);
        when(audit.record(any())).thenReturn(true);
        when(loyalty.pointsFor(anyString())).thenReturn(0);
        // 6 collaborators, 6 stubbings — for ONE test
    }
}
```

**Actual output:** it passes — but the *smell* is the six collaborators.

**Takeaway:** a test with more mock setup than production code is telling you
something about the **design**, not about the test. The fix is to reduce the number
of collaborators the class under test needs — inject a *value* instead of a
*service*, or group the collaborators behind one port.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A test-double toolkit — a fake, and a builder for mocks

```java
package com.example.testing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A hand-written FAKE — the preferred double for an internal collaborator.
 *
 * <p>Why this beats a mock for a repository:
 * <ul>
 *   <li>It has <b>behaviour</b>, so the test exercises the real interaction.</li>
 *   <li>It does <b>not</b> break when the production interface changes — the
 *       compiler tells you, instead of the test failing mysteriously.</li>
 *   <li>It is <b>readable</b> — the whole contract is on one page.</li>
 * </ul>
 */
public final class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> store = new ConcurrentHashMap<>();

    /** The calls that were made — for assertions and for debugging. */
    public final List<String> calls = new ArrayList<>();

    @Override
    public User findById(String id) {
        calls.add("findById(" + id + ")");
        return store.get(id);
    }

    @Override
    public void save(User user) {
        calls.add("save(" + user.id() + ")");
        store.put(user.id(), user);
    }

    @Override
    public void delete(String id) {
        calls.add("delete(" + id + ")");
        store.remove(id);
    }

    /** Test helpers. */
    public int size() { return store.size(); }
    public boolean contains(String id) { return store.containsKey(id); }
    public void reset() { store.clear(); calls.clear(); }
}
```

### 6.2 A test that uses the fake — no mocking framework at all

```java
package com.example.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A test with NO mocking framework — the class under test takes a plain
 * interface, and the test supplies a fake.
 *
 * <p>This is the test style to prefer: it is fast, readable, and it survives a
 * refactoring of the production interface.
 */
@DisplayName("OrderService")
class OrderServiceTest {

    private InMemoryUserRepository repository;
    private OrderService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
        service = new OrderService(repository, new FixedClock());
    }

    @Test
    @DisplayName("stores the order and returns it")
    void storesTheOrder() {
        Order order = new Order("O-1", "C-1", List.of(new Item("SKU", 1, new BigDecimal("10.00"))));

        Order stored = service.place(order);

        assertEquals("O-1", stored.id());
        assertTrue(repository.contains("O-1"), "the order should have been stored");
        assertEquals(List.of("save(O-1)"), repository.calls);
    }

    @Nested
    @DisplayName("when the customer does not exist")
    class WhenTheCustomerIsMissing {

        @Test
        @DisplayName("throws a CustomerNotFoundException")
        void throwsNotFound() {
            Order order = new Order("O-1", "UNKNOWN", List.of());

            assertThrows(CustomerNotFoundException.class, () -> service.place(order));
            assertTrue(repository.calls.isEmpty(), "nothing should have been stored");
        }
    }
}
```

### 6.3 A "fluent" mock builder — for the cases where a mock is right

```java
package com.example.testing;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A tiny builder that makes a mock's setup read like a sentence.
 *
 * <p>Use it when the collaborator is genuinely a <b>boundary</b> (a remote
 * service, a clock) — not for an internal collaborator, where a fake is better.
 */
public final class PaymentGatewayMock {

    private final PaymentGateway gateway = mock(PaymentGateway.class);

    private PaymentGatewayMock() { }

    public static PaymentGatewayMock aGateway() {
        return new PaymentGatewayMock();
    }

    /** The gateway approves every charge with the given transaction id. */
    public PaymentGatewayMock approving(String transactionId) {
        when(gateway.charge(any())).thenReturn(PaymentResult.approved(transactionId));
        return this;
    }

    /** The gateway charges exactly this amount. */
    public PaymentGatewayMock charging(BigDecimal amount) {
        when(gateway.charge(any())).thenAnswer(invocation -> {
            PaymentRequest request = invocation.getArgument(0);
            if (request.amount().compareTo(amount) != 0) {
                return PaymentResult.rejected("unexpected amount: " + request.amount());
            }
            return PaymentResult.approved("txn-" + amount);
        });
        return this;
    }

    /** The gateway always fails with this reason. */
    public PaymentGatewayMock failing(String reason) {
        when(gateway.charge(any())).thenReturn(PaymentResult.rejected(reason));
        return this;
    }

    /** The gateway throws. */
    public PaymentGatewayMock throwing(RuntimeException exception) {
        when(gateway.charge(any())).thenThrow(exception);
        return this;
    }

    /** The mock itself. */
    public PaymentGateway build() {
        return gateway;
    }
}
```

```java
// ✅ The usage — the setup reads like a sentence
PaymentGateway gateway = PaymentGatewayMock.aGateway()
        .approving("txn-1")
        .build();

PaymentGateway failing = PaymentGatewayMock.aGateway()
        .failing("card declined")
        .build();
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — `when(mock.voidMethod())`

```java
when(gateway.close()).thenThrow(ex);   // ❌ calls close() during setup
```

**✅ FIX** — `doThrow(ex).when(gateway).close();`

---

### ❌ Pitfall 2 — Mixing matchers and raw values

```java
when(gateway.charge("C-1", any())).thenReturn(r);   // ❌ InvalidUseOfMatchersException
```

**✅ FIX** — `when(gateway.charge(eq("C-1"), any())).thenReturn(r);`

---

### ❌ Pitfall 3 — An unstubbed call silently returning a default

```java
when(gateway.findById(any())).thenReturn(order);
service.process(order);
when(gateway.getBalance(any())).thenReturn(balance);   // ⚠ never called
```

**✅ FIX** — keep strict stubs on, and use `verifyNoMoreInteractions(...)` to catch the unverified calls.

---

### ❌ Pitfall 4 — Relying on `@InjectMocks` to wire things up

```java
@InjectMocks private OrderService service;   // ⚠ silently injects NOTHING if it cannot
```

**✅ FIX** — construct the object under test explicitly in `@BeforeEach`.

---

### ❌ Pitfall 5 — Verifying the number of calls to an implementation detail

```java
verify(gateway, times(3)).charge(any());   // ⚠ brittle
```

**✅ FIX** — assert on the outcome; verify an interaction only when the interaction *is* the requirement.

---

### ❌ Pitfall 6 — Verifying a private method

```java
verify(gateway, times(1)).calculateFeeInternal(any());   // ⚠ needs the inline mock maker
```

**✅ FIX** — test the public behaviour.

---

### ❌ Pitfall 7 — Sharing mocks between tests

```java
static PaymentGateway gateway = mock(PaymentGateway.class);   // ⚠ state leaks between tests
```

**✅ FIX** — an instance field (a new mock per test), or `@BeforeEach` re-creation.

---

### ❌ Pitfall 8 — Mocking a static and running the tests in parallel

```java
mockStatic(Instant.class);   // ⚠ GLOBAL — it affects every thread
```

**✅ FIX** — inject a `Clock` instead.

---

### ❌ Pitfall 9 — A mock with a huge stubbing block (the design smell)

```java
// 30 lines of stubbing for a 10-line class → the class has too many collaborators
```

**✅ FIX** — refactor the class under test; inject values instead of services.

---

### ❌ Pitfall 10 — Asserting on the mock instead of the result

```java
verify(gateway).charge(any());   // ⚠ it says nothing about whether the payment WORKED
```

**✅ FIX** — assert on the returned result *and* (if the interaction matters) on the call.

---

### ❌ Pitfall 11 — Using `@MockBean` where `@Mock` is enough

```java
@SpringBootTest
@MockBean private PaymentGateway gateway;   // ⚠ starts the WHOLE Spring context
```

**✅ FIX** — a plain unit test with `@Mock`; or a `@WebMvcTest` slice.

---

### ❌ Pitfall 12 — Testing the mock's behaviour

```java
when(gateway.charge(any())).thenReturn(r);
assertEquals("txn-1", gateway.charge(req).transactionId());   // ❌ testing Mockito
```

**✅ FIX** — that is a test of your *stubbing*, not of your code.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is a mock?**
**A:** A **programmable test double** — an object that stands in for a collaborator
and whose behaviour you define. You *stub* what it returns and *verify* what was
called on it. The alternatives are a **fake** (a working implementation, e.g. an
in-memory repository) and a **stub** (a canned response).

**Q: What is the difference between a mock and a stub?**
**A:** A **stub** returns a **predefined response** and you assert on the *outcome*.
A **mock** records its **interactions** and you assert on *what was called*. In
practice they are the same object — Mockito's `mock()` is a stub until you
`verify()` it.

**Q: What is the difference between `@Mock` and `@Spy`?**
**A:** A `@Mock` is a **pure double** — it does nothing unless you stub it (every
unstubbed method returns a default: `null`, `0`, `false`). A `@Spy` is a **real
object** with the calls recorded, so the real implementation runs unless you stub a
particular method.

**Q: How do you assert that a mock was called?**
**A:** `verify(mock).method(args)` — with `times(n)`, `never()`, `atLeastOnce()`,
`atMost(n)`, or `verifyNoMoreInteractions(mock)`. Use an `ArgumentCaptor` to inspect
the argument that was passed.

### 🟡 Intermediate

**Q: When should you use a mock rather than a fake?**
**A:** When the collaborator is a **boundary** — a remote service, a clock, a random
source, a concurrent system — i.e. when it is **slow**, **non-deterministic**, or
**destructive**. For a deterministic collaborator that is part of your own domain,
a **fake** is better: it has real behaviour, it does not break when the interface
changes, and the test asserts on the outcome rather than on the interaction.

**Q: What is strict stubbing, and why does it exist?**
**A:** With `MockitoExtension`, an **unused** stubbing fails the test
(`UnnecessaryStubbingException`). It exists to catch a test that is asserting a
*former* behaviour — the code no longer calls the stubbed method, but the test
still stubs it. The relaxation (`lenient()`) should be used deliberately and
locally, for a stub that is genuinely optional.

**Q: When do you use `doReturn(...).when(mock)` instead of `when(...).thenReturn(...)`?**
**A:** For a **`void` method** — `when(mock.voidMethod())` has to *call* the method to
identify it, and a void method has no return value, so the call happens during
setup. `doThrow/doReturn/doAnswer(...).when(mock)` puts the "when" *after* the
identifier, so the method is never invoked. It also works for non-void methods, so
you can use it consistently.

**Q: What is an `ArgumentCaptor`, and when do you use it?**
**A:** It captures the argument a mock was called with, so you can assert on it
after the fact. Use it when the argument's *value* matters and a `verify(mock)
.method(eq(expected))` would require you to construct the whole expected object.
It gives better failure messages than `argThat`.

### 🔴 Advanced / Senior

**Q: When does mocking become a design problem rather than a testing tool?**
**A:** When the **mock setup exceeds the production code** — the class under test
has more collaborators than it should. The signals: (1) more stubbing lines than
production lines; (2) the same stubbing copied across many tests (a *shared
fixture smell*); (3) a mock that is stubbed but whose stub is never verified (it is
noise); (4) a test that breaks on a refactor of a collaborator's *interface* even
though the behaviour is unchanged. The fixes, in order: (1) **inject a value**
instead of a service (a `record`, a `Function`, a `Predicate`); (2) **group the
collaborators behind one port** (a Facade — which is also the DIP fix); (3) **use a
fake** for the ones that do not matter; (4) **split the class** (the SRP fix). The
meta-lesson: *a test that is hard to write is usually telling you the production
design is hard to use* — the mock is the symptom, not the disease.

**Q: How do you test code that depends on the current time, the random generator, or a UUID?**
**A:** By **injecting** them, not by mocking the statics. Java's `java.time` package
was designed for exactly this: `Clock` (injectable — `Clock.fixed(...)` in the test,
`Clock.systemUTC()` in production), `RandomGenerator` (injectable), and for UUIDs a
small `IdGenerator` interface (or `Supplier<UUID>`). The reasons this beats
`mockStatic`: (1) the static mock is **global** — it affects every thread, so it
breaks under parallel test execution and can leak into another test; (2) it requires
the `mockito-inline` agent, which has a startup cost and can conflict with other
agents; (3) the injected version **works in production code** — you can swap the
real `Clock` for a fixed one in a staging environment, or drive time forward in a
test. The general principle: *if a test needs to control a global, make it a
parameter.* The pattern generalises to the file system, the environment, and the
current thread.

**Q: How do you test a class that is hard to instantiate — many collaborators, complex construction?**
**A:** Four techniques, in increasing order of preference. (1) **Make the class constructible with fewer arguments** — group the collaborators behind one port, or extract a *value object* that holds them. This is a **design** fix and it is the right one. (2) **Use a builder** for the object under test, so the test can supply only what it cares about. (3) **Use `@InjectMocks`** — but understand its limits: it tries the constructor, then the setters, then the fields, and it **silently does nothing** if it cannot decide, so always assert that the wiring worked. (4) **Test through the public API** — construct the object once in a `@BeforeEach` with a *builder* of fakes, and give each test the *specific* collaborator it needs by constructing a new object (not by re-stubbing a shared mock — that is the **mock leakage** trap). The deeper point: *a class that is hard to instantiate is usually a class with too many responsibilities, and the test difficulty is the design telling you so.*

**Q: What are the risks of mocking, and how do you mitigate them?**
**A:** Four risks, with a mitigation for each. (1) **Coupling to the implementation** — a refactor of the collaborator's interface breaks the test even though the behaviour is unchanged. *Mitigation:* mock only at a **boundary**; use a fake for an internal collaborator. (2) **Testing the mock rather than the code** — a test that only verifies calls can pass while the behaviour is wrong. *Mitigation:* always assert on the **outcome**, and treat `verify` as supplementary. (3) **The mock's default answers** — an unstubbed method returns `null`/`0`/`false`, so a missing stub produces a silent, misleading failure far from the cause. *Mitigation:* **strict stubs** plus `verifyNoMoreInteractions`. (4) **Over-mocking** — mocking everything makes the test a **transcription of the implementation** (it asserts the code calls what it calls). *Mitigation:* the mock-setup-smell rule — when the setup exceeds the production code, refactor the design. The overall principle: *mocking is a way to control the **edges** of your system, not a way to avoid testing the **middle**.*

### ⚫ Expert / Principal

**Q: Mocking has a bad reputation. Is it a testing smell, and what is the alternative philosophy?**
**A:** The reputation is partly earned and partly misplaced. Mocking is **inappropriate** when the thing being mocked is **deterministic, fast, and under your control** — a value object, a pure algorithm, a simple in-memory collaborator. In that case, mocking replaces a *behaviour* assertion with an *interaction* assertion, and it makes the test **fail on a refactor that did not change the behaviour** — which trains the team to see refactoring as dangerous. The philosophy that replaces it is often called **"test the behaviour, not the implementation"**, and its operational form is: (1) **use a real collaborator wherever the real one is cheap and deterministic** — an in-memory implementation of your repository, the real value object, the real algorithm; (2) **mock only what is slow, non-deterministic, or outside your control** — the network, the clock, the random source, the payment gateway; and (3) **prefer the "port and adapter" shape in production code**, so that a fake is trivially available for every boundary (which is exactly what hexagonal architecture is for). The nuance a good candidate adds: the problem is not mocking *per se* but **mocking at the wrong altitude**. A test that mocks a *domain collaborator* is testing the wiring; a test that mocks a *port* is testing the behaviour. And the deeper point: *the desire to mock is often a signal that the design does not offer a seam* — and the right response to "this is hard to test because I cannot construct it" is not a mocking framework, it is **making the dependency substitutable**.

---

## 9. Counter-Questions & Traps

### CQ1. "If a mock is brittle, why is Mockito the default? Why not just use fakes everywhere?"

- **Naive:** "Because fakes are a lot of code."  ← *true, and it misses the real reason*
- **The trap:** The trap is to frame the choice as *mock vs fake* — a binary. It is actually a **spectrum of substitutability**, and Mockito's advantage is not that it is better than a fake; it is that it makes a collaborator substitutable **without writing anything**.
- **Expert answer:** The three reasons Mockito won, and the cost. (1) **Zero effort to substitute** — a fake requires you to *implement the interface*, which for a large interface is a lot of code that exists only for tests. A mock costs one line. (2) **Programmable behaviour** — a mock can say "return X on the first call, throw on the second", which a fake can only do with state. (3) **Interaction assertions** — a mock lets you assert that a *notification was sent*, which a fake can only do if you build the recording into it. The cost: all three come with the coupling. And the resolution is the **altitude rule**: *the higher the collaborator is in your own domain, the more likely a fake is right; the further outside your domain, the more likely a mock is right.* The practical boundary: **your own value objects, algorithms and simple collaborators → the real thing or a fake; your own services with several methods → a fake if the interface is small, a mock if the interaction is the point; anything outside your process → a mock.**

### CQ2. "You said `@InjectMocks` is brittle. Why does it still exist, and why do people use it?"

- **Naive:** "Convenience."  ← *true, and it misses the specific failure it hides*
- **The trap:** The trap is to dismiss it. The specific failure it hides is **silent**: if Mockito cannot decide how to inject, it injects **nothing** — and the test then fails with a `NullPointerException` in a place that has nothing to do with the injection.
- **Expert answer:** Why it exists: it removes the boilerplate of constructing the object under test, and it handles the three injection strategies (constructor, setter, field) so you do not have to know which one the class uses. The failure it hides: Mockito's resolution is **by type**, and with **two fields of the same type** it cannot tell them apart — it injects the *same* mock into both (or none). And with a class that has **no no-arg constructor** and a constructor with *primitive* parameters, it cannot construct it at all. The result is a confusing `NullPointerException` deep in the test. The mitigation, and it is cheap: **construct the object under test explicitly.** It is one line more than `@InjectMocks`, it is *obvious*, it survives a refactor of the class, and it never fails silently. The reason people still use `@InjectMocks` is inertia, and the honest argument for it: in a test with **one** collaborator and a trivial constructor, it is fine. The rule: *use `@InjectMocks` only when the construction is trivial; otherwise construct it explicitly — the explicitness is the point.*

### CQ3. "You said to mock the boundary. But isn't a payment gateway a *domain* concept in an e-commerce system?"

- **Naive:** "It's external, so mock it."  ← *right, but it dodges the real distinction*
- **The trap:** The trap is to equate "domain concept" with "internal". The real distinction is **who controls the implementation and where the non-determinism is** — and a payment gateway has non-determinism (the network) and an implementation you do not control (the vendor), even though it is central to your domain.
- **Expert answer:** The refined rule, stated as three questions about the *collaborator*, not the *concept*. (1) **Do I control the implementation?** A payment gateway — you do not; you call Stripe. A pricing calculator — you do. (2) **Is it deterministic?** A gateway fails intermittently; a calculator does not. (3) **Is it fast?** A gateway takes 200 ms and can time out; a calculator is nanoseconds. Mock (or fake) it when the answer to (1), (2) or (3) is "no" for the *external* aspects. The nuance: a *domain* concept can still be split — the **decision** ("should we charge or refund?") is domain logic and deserves a **real implementation** and a real test; the **call** ("ask Stripe to charge") is an adapter and deserves a mock. That split is the hexagonal seam, and it is the right place to draw the line. The general principle: *mock at the seam where the code changes from "our decision" to "their system" — and the seam is usually an interface you own.*

### CQ4. "What is the difference between `verify` and `verifyNoMoreInteractions`? When do you need the second?"

- **Naive:** "The second checks that nothing else was called."  ← *true, and it misses why it is the important one*
- **The trap:** The trap is to think `verify` is the strictness lever. It is not — `verify` only checks the calls you *thought of*. `verifyNoMoreInteractions` is what catches the calls you **did not think of**, and that is the whole point.
- **Expert answer:** The mechanism. `verify(gateway).charge(any())` asserts that *this* call happened — it says **nothing** about the 30 other calls the mock received. `verifyNoMoreInteractions(gateway)` asserts that **only** the verified calls happened. Together they pin the interaction **exactly**. The value: (1) it catches an **unintended** collaborator call — the code now calls a method you did not know about, which may be a bug (a duplicate charge, a log in a hot path) or a sign that the design changed; (2) it catches the **unstubbed default answer** — a method that returned `null` silently; (3) it is the mechanism that makes the mock *strict* in the interaction sense, complementing strict *stubs* (which catch the unused stubbings). The caveat, and it is important: `verifyNoMoreInteractions` is **brittle** — it fails when the production code legitimately gains a new call to the collaborator, even if the behaviour is fine. So the rule: use it where the interaction set is a **contract** (a payment gateway must be called exactly once with this amount) and skip it where it is an **implementation detail** (an audit log may log more things later). And the alternative that is even stricter: `verifyNoInteractions(gateway)` for a collaborator that should not be touched at all.

### CQ5. "You said mocking statics is global and breaks parallel tests. How bad is that really?"

- **Naive:** "It can affect other threads."  ← *true, and the severity is worth stating precisely*
- **The trap:** The trap is to treat it as a theoretical concern. The severity depends on **when** the static mock is active: `mockStatic` is scoped to the `try` block, but the mock applies to **every thread** in that window — including the ones running *other* tests in parallel.
- **Expert answer:** The precise failure mode and the two mitigations. (1) **The mechanism** — `mockStatic(Instant.class)` installs a thread-local? No: Mockito's static mocking is **global** (the `InlineByteBuddyMockMaker` intercepts the static call site for all threads). So if test A mocks `Instant.now()` and test B (running in parallel) also calls `Instant.now()`, test B sees A's mock. (2) **The symptom** — a test that passes alone and fails in the suite, or a test that fails only under `-Djunit.jupiter.execution.parallel.enabled=true`. It is a **flaky test**, and flaky tests are corrosive. (3) **The mitigations**: (a) **do not mock statics** — inject a `Clock`; (b) if you must, **disable parallel execution** for that class (`@Execution(SAME_THREAD)`) or for the whole suite; and (c) the agent overhead: `mockito-inline` installs a Java agent, which costs a second of startup and can conflict with Jacoco, Spring's agent, or another mocking library. The deeper principle: *a global mutable hook is a global mutable hook* — the same reason `System.setProperty` in a test is a code smell, and the same reason the industry moved to dependency injection for testability.

### CQ6. "Is there a case where mocking is genuinely the *only* option?"

- **Naive:** "Yes — a static utility or a final class."  ← *true, and there is a better framing*
- **The trap:** The trap is to list the *technical* limitations ("you cannot mock a static without an agent") as if they were the justification. The better framing is about **what the code is**: some code genuinely has no seam, and the choice is between a mock and *not testing that code*.
- **Expert answer:** The genuine cases, and the honest alternatives. (1) **A static utility with no state** (e.g. `IdGenerator.next()`) — mock it with `mockStatic`, **or** better: wrap it in an interface (a two-line change to the production code) and inject it. The second is better because it also improves the *design*. (2) **A final class from a third party** (e.g. a `java.time` type, a Guava utility) — `mockito-inline` handles it, and here there is genuinely no alternative without wrapping it. (3) **A legacy codebase you cannot change** — the mocking agent is the only way to get coverage; but the honest expectation is that such tests are *characterisation tests* (pinning the current behaviour) rather than design tests. (4) **The truly unavoidable case — a hard-coded global** (`System.out`, `System.currentTimeMillis` inside a third-party library) — and here the honest answer is that you **cannot** unit test it, and the right move is an **integration test** at the boundary. The principle: *before reaching for a static mock, ask whether a two-line production change (injecting the dependency) would be better — because it usually would, and the mock is a symptom of a design that resists testing.*

### CQ7. "How do you test a method that returns a `Stream` or a `CompletableFuture`?"

- **Naive:** "Mock the return value."  ← *it works, but it misses the consumption*
- **The trap:** The trap is to stop at producing the double. A `Stream` is **one-shot** and **lazy** — so a mock that returns `Stream.of(...)` and a test that consumes it twice will get an `IllegalStateException` — and the *timing* of the consumption (lazily, at the terminal operation) means the mock's behaviour is observed *later* than the stubbing.
- **Expert answer:** The mechanics, and the patterns. (1) **A `Stream`** — the mock returns a *real* `Stream` over real data (`Stream.of(1, 2, 3)`); the test consumes it with a terminal operation. The one-shot rule means the test must not reuse it. For a *lazy* stream (one that calls your mock per element), use `Stream.generate(() -> …)` or `IntStream.range(...).mapToObj(i -> mock.get(i))` — so the mock is invoked *as the stream is consumed*, which is exactly the behaviour you want to verify. (2) **A `CompletableFuture`** — the mock returns a **completed** future (`CompletableFuture.completedFuture(value)`) for the happy path, or `CompletableFuture.failedFuture(ex)` for the error path. The subtlety: the *downstream* stages then run **on the calling thread** (because the future is already complete) — so if the production code has an `.exceptionally(...)`, it is exercised synchronously, which is what makes the test deterministic. (3) The trap to name: a mock returning `null` for a `CompletableFuture`-returning method → an immediate `NullPointerException` at the call site, with a confusing message. And the general principle: *for a lazy or asynchronous abstraction, the double must be a **real** instance of that abstraction* — you are testing the composition, not the container.

### CQ8. "You said a test with too much mock setup is a design smell. How do you convince a team to refactor rather than just add mocks?"

- **Naive:** "Because it is better design."  ← *an assertion, not a case*
- **The trap:** The trap is to make a moral argument. The convincing argument is **quantitative and immediate**: the mock-heavy test is *slow*, *brittle*, and it *doesn't fail when it should*.
- **Expert answer:** The three measurable arguments. (1) **The brittleness is observable** — count the tests that broke in the last six months **without a behaviour change**; every one is a mock coupling. That number is the argument. (2) **The test is a transcription of the implementation** — a mock-heavy test passes even if the *behaviour* is wrong (you verified the calls, not the outcome); so it gives **false confidence**, which is worse than no test. (3) **The cost is in the test, not the mock** — the mock setup grows with every collaborator, so the test becomes a **maintenance tax** that grows with the design's complexity, and it is paid on every read. And the practical, non-moral **refactoring step** to propose: extract the collaborators into a *single port* (one interface with the methods the class actually needs), and replace the *port* with a fake in the test. That is a small, safe, incremental change — it does not require a big-bang redesign, and it immediately makes the test a *behaviour* test. The framing that works with a team: *"the test is telling us the class has six responsibilities; let's fix the class, not the test."*

### CQ9. "What about `mockito-inline`? Is mocking final classes and statics a good idea?"

- **Naive:** "It's powerful and modern."  ← *true, and it misses the trade-offs*
- **The trap:** The trap is to treat the capability as neutral. Mocking statics **removes the last boundary** between your test and the JVM's global state, and the boundary was doing real work.
- **Expert answer:** The three costs, precisely. (1) **The agent** — the inline mock maker installs a Java agent that **instruments the JDK's own classes** (it retransforms `Instant`, `UUID`, …). That has a **startup cost** (a second or so), and it can **conflict** with Jacoco, Spring's agent, or another mocking library — the "agent clash" error, which is a genuinely confusing failure. (2) **The global scope** — a static mock affects every thread, which breaks under parallel test execution. (3) **The design signal** — the fact that you *need* to mock a static means the dependency is not injectable, which is a **design** problem with a two-line fix. And the judgement: the inline mock maker is now the **default in Mockito 5**, so it is not going away — but the *guidance* should be: **use it when there is no alternative (a third-party final class), not when it is more convenient than injecting.** The exception worth allowing: **mocking `System.currentTimeMillis` or `Instant.now` inside a legacy codebase you are not changing** — where the alternative is no test at all, and a characterisation test with a static mock is still better than nothing.

### CQ10. "How do you keep mocks from becoming a maintenance burden as the interfaces change?"

- **Naive:** "The compiler catches it."  ← *true, and it misses the *silent* breakage*
- **The trap:** The trap is to believe a mock breaks loudly. A mock **does** break loudly when the *method signature* changes (the compiler catches it). But when a method's **behaviour** changes — a new parameter with a default, a renamed *parameter* (which the compiler also catches), a **new overload** — the mock **silently keeps returning the old stub** for a call that should now go elsewhere. And a `verify` on a method that is now called twice will fail for a reason that has nothing to do with the behaviour.
- **Expert answer:** The four practices, and the principle. (1) **Keep the mocked surface small** — mock the *port* (a small interface you own), not the wide service. A 2-method port changes rarely; a 20-method collaborator changes often. (2) **Prefer a fake for a wide interface** — a fake's *compiler* error when the interface changes is a *better* signal than a mock's runtime surprise, and a fake can be updated once for all the tests. (3) **Use `verifyNoMoreInteractions`** — it catches the *new* call that you did not know about. (4) **Keep strict stubs on** — an unused stub is the signal that the *call* changed. And the underlying principle: *a mock's brittleness is proportional to the size of the interface you mocked* — so the maintenance fix is to **mock less**, not to maintain the mocks better. That is the same insight as the "altitude" rule, applied over time: every mock you remove is a coupling you have deleted.

---

## 10. Rapid-Fire MCQs

**Q1.** A mock with no stubbing returns…
- A) `null`  B) **A default value (`null`, `0`, `false`, an empty collection)**  C) An exception  D) A random value

**Q2.** The difference between `@Mock` and `@Spy` is…
- A) The annotation  B) **A mock does nothing; a spy runs the real implementation**  C) A spy is faster  D) A mock is thread-safe

**Q3.** For a **void** method, the correct stubbing form is…
- A) `when(mock.m()).thenThrow(ex)`  B) **`doThrow(ex).when(mock).m()`**  C) `mock.m()`  D) `verify(mock).m()`

**Q4.** `@InjectMocks` injects by…
- A) Type only  B) **Constructor, then setter, then field**  C) Name only  D) Annotation

**Q5.** `ArgumentCaptor` is used to…
- A) Capture an exception  B) **Capture the argument a mock was called with**  C) Capture a log  D) Capture a thread

**Q6.** `verifyNoMoreInteractions(mock)` asserts…
- A) The mock was never called  B) **Only the verified calls happened**  C) All the methods were called  D) The mock is empty

**Q7.** Strict stubs fail the test when…
- A) A mock is unstubbed  B) **A stubbing is never used**  C) A call count is wrong  D) An argument does not match

**Q8.** `lenient()` is used to…
- A) Skip a test  B) **Relax the strictness for a specific stubbing**  C) Make a mock lenient  D) Ignore a verification

**Q9.** `mockStatic(Instant.class)` is problematic because…
- A) It is slow  B) **It is global — it affects every thread**  C) It cannot stub a method  D) It needs JUnit 4

**Q10.** The recommended way to control the current time in a test is…
- A) `mockStatic(Instant.class)`  B) **Inject a `java.time.Clock`**  C) `System.setProperty`  D) A `ThreadLocal`

**Q11.** Mixing a matcher and a raw value in one stubbing call…
- A) Is fine  B) **Throws `InvalidUseOfMatchersException`**  C) Is ignored  D) Works only with `any()`

**Q12.** Spring Boot's `@MockBean` replaces the bean…
- A) In the test class  B) **In the Spring application context**  C) On the classpath  D) In the YAML

**Q13.** Spring Boot 3.4+ recommends `@MockBean` be replaced with…
- A) `@Mock`  B) **`@MockitoBean`**  C) `@SpyBean`  D) `@InjectMocks`

**Q14.** A mock is most appropriate for a collaborator that is…
- A) A value object  B) **A slow, non-deterministic or external system**  C) A simple algorithm  D) In the same package

**Q15.** A fake is most appropriate for a collaborator that is…
- A) A remote API  B) **A deterministic part of your own domain**  C) The clock  D) Non-deterministic

**Q16.** `verify(mock, times(3))` is brittle because…
- A) It is slow  B) **The call count is an implementation detail**  C) `times` is deprecated  D) It needs an agent

**Q17.** Verifying a **private** method requires…
- A) A subclass  B) **The inline mock maker**  C) `setAccessible`  D) Nothing

**Q18.** The "mock setup smell" is a sign of…
- A) A missing mocking library  B) **Too many collaborators in the class under test**  C) A slow test  D) Poor test names

**Q19.** MockK's main advantage over Mockito is…
- A) It is faster  B) **A fluent API, and mocking constructors and suspend functions**  C) It has no agent  D) It needs no dependencies

**Q20.** The best alternative to mocking a static method is…
- A) `mockStatic`  B) **Injecting the dependency**  C) `spy()`  D) A test container

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | The default answer — which is why strict stubs and `verifyNoMoreInteractions` matter. |
| 2 | **B** | A mock does nothing; a spy runs the real code and records the calls. |
| 3 | **B** | `when(mock.m())` would *call* the void method during setup. |
| 4 | **B** | Constructor, then setter, then field — resolved by type. |
| 5 | **B** | It captures the argument so you can assert on it afterwards. |
| 6 | **B** | Only the verified interactions happened. |
| 7 | **B** | An unused stubbing means the test asserts a former behaviour. |
| 8 | **B** | It relaxes the strictness for that one stubbing. |
| 9 | **B** | The mock is global — it affects every thread, breaking parallel runs. |
| 10 | **B** | `Clock.fixed(...)` — no agent, no global state, works in production. |
| 11 | **B** | Once a matcher is used, **all** the arguments must be matchers. |
| 12 | **B** | The bean in the Spring context is replaced by the mock. |
| 13 | **B** | `@MockitoBean` — it does not clash with other frameworks' `@MockBean`. |
| 14 | **B** | Slow, non-deterministic, or outside your control. |
| 15 | **B** | A deterministic part of your own domain — a fake has real behaviour. |
| 16 | **B** | The number of calls is not part of the contract. |
| 17 | **B** | The inline mock maker can intercept private methods. |
| 18 | **B** | The class under test has too many collaborators — fix the design. |
| 19 | **B** | A fluent API, plus constructor and coroutine mocking. |
| 20 | **B** | Injecting the dependency is better in every way. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE MOCKITO ESSENTIALS                              │
│  @Mock          a double that does nothing until you stub it            │
│  @Spy           a REAL object whose calls are recorded                 │
│  @InjectMocks   injects by constructor → setter → field  ⚠ brittle      │
│                                                                          │
│  when(...).thenReturn(...)   for a NON-void method                      │
│  doReturn/doThrow(...).when(mock)  for a VOID method ⚠                 │
│  doAnswer(...)  for a computed response                                 │
│                                                                          │
│  verify(mock) / times(n) / never() / atLeastOnce() / atMost(n)          │
│  verifyNoMoreInteractions(mock)   ⚠ the strictness lever               │
│  verifyNoInteractions(mock)      ⚠ it must not be touched at all       │
│  ArgumentCaptor<T>   to assert on the argument                          │
│  lenient()          to relax one stubbing                               │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE MOCKING DECISION                                │
│                                                                          │
│  Is the collaborator SLOW, NON-DETERMINISTIC or EXTERNAL?                │
│        │                                                                 │
│       YES                                                                 │
│        ▼                                                                 │
│    MOCK it (or a stub)                                                   │
│        │                                                                 │
│       NO                                                                 │
│        ▼                                                                 │
│    Use the REAL one, or a FAKE                                          │
│    (a deterministic collaborator in your own domain)                       │
│                                                                          │
│  ⚠ IF THE SETUP EXCEEDS THE PRODUCTION CODE → the DESIGN is the problem │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 6 MOCKING TRAPS                                 │
│  1. when(mock.voidMethod())      → it CALLS the method during setup    │
│  2. Mixing matchers and values   → InvalidUseOfMatchersException        │
│  3. An unstubbed call            → silently returns null/0/false         │
│  4. verify(times(3))             → couples to an implementation detail │
│  5. @InjectMocks wiring nothing  → a silent NullPointerException        │
│  6. mockStatic                → GLOBAL — it breaks parallel tests    │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The house rules

| Rule | Why |
|---|---|
| **Construct the object under test explicitly** | `@InjectMocks` fails silently |
| **Mock only at a boundary** | A mock is a coupling to the interface's shape |
| **Use a fake for a deterministic internal collaborator** | It has behaviour and does not break on a refactor |
| **Always assert on the outcome** | A mock-only test can pass while the behaviour is wrong |
| **Keep strict stubs on** | An unused stub means the test has diverged |
| **Use `verifyNoMoreInteractions` where the interaction set is a contract** | It catches the call you did not know about |
| **Inject the clock, the random source and the id generator** | No global state, no agent, works in production |
| **Never `mockStatic` in a parallel suite** | It affects every thread |
| **No mocks of private methods** | It couples to the implementation |
| **If the setup exceeds the production code, refactor the class** | The test is reporting a design problem |

### The mocking checklist for a new test

```text
  ☐ What am I proving — the OUTCOME or the INTERACTION?
  ☐ Is the collaborator a boundary?  → mock;  otherwise → the real one or a fake
  ☐ Do I need the clock / random / id?  → inject it, do not mock the static
  ☐ Have I constructed the object under test explicitly?
  ☐ Have I asserted on the RESULT, not only on the calls?
  ☐ Is there an unused stub?  → delete it
  ☐ Would this test still pass if the collaborator's interface changed?  → if not, use a fake
```

### Observability

| Signal | What it tells you |
|---|---|
| The number of mocks in a test class | A design smell — too many collaborators |
| The ratio of stubbing lines to production lines | ⚠ The mock-setup smell |
| The number of tests broken by a refactor | The coupling cost |
| The flaky-test count | Often a global mock or a `Thread.sleep` |

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The test framework | [01 · JUnit 5](01-JUnit-Basics.md) |
| The other framework | [03 · TestNG](03-TestNG.md) |
| The practices that make a suite good | [04 · Testing Best Practices](04-Testing-Best-Practices.md) |
| Dependency injection (the alternative to mocking) | [10 · SOLID Principles](../10-SOLID-Principles/01-SOLID-Principles.md) |
| The hexagonal seam | [09 · Design Patterns](../09-Design-Patterns/01-Design-Patterns.md) |

---

**Next Chapter:** → [03 · TestNG](03-TestNG.md)
**Previous Chapter:** → [01 · JUnit 5](01-JUnit-Basics.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
