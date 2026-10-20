# Interface vs Abstract Class — The Complete Interview Guide

> **Asked in ~90% of Java interviews.** The naive answer ("interfaces for multiple
> inheritance, abstract classes for shared code") is *true but incomplete* — and
> it is the kind of answer that ends an interview early. The **expert** answer
> turns on **what the type *is***, not on what the language permits.

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

- **An interface is a *contract*; an abstract class is a *partial implementation*.** The rule of thumb: **use an interface unless you have shared state or shared code to give to the implementors.**
- **Since Java 8, an interface can have `default` and `static` methods** — so the "interfaces have no code" argument is **obsolete**. What an interface still cannot have is **instance state**.
- **An interface's fields are implicitly `public static final`** — it cannot have instance fields, so it cannot hold state.
- **An abstract class CAN have state, constructors, and instance initialisers** — that is the *only* thing it can do that an interface cannot.
- **The real question is "is the relationship *is-a* (inheritance) or *has-a* (composition)?"** If the subtype can stand in for the supertype everywhere, use inheritance. If it merely uses it, use composition.
- **Java 8's `default` methods changed the calculus** — you can now evolve an interface without breaking implementors, which is why "interfaces are risky to publish" is now a *much* weaker objection.
- **Java 17's `sealed` types** let you say "only these types may implement this" — which gives an interface the *closed-set* guarantee that only an abstract class used to provide.
- **A practical decision rule:** (1) need shared state or a constructor? → **abstract class**; (2) need multiple inheritance of type? → **interface**; (3) need to express a *capability*? → **interface**; (4) need to share code across a *hierarchy*? → **abstract class**.
- **The "prefer composition over inheritance" argument** applies here too: an interface is a *pure* abstraction with no coupling, which is why it is the safer default.
- **A single-implementation interface is a smell** — if there is exactly one implementation and no test double, use the class.

---

## 1. What & Why — The Problem This Solves

**The problem.** Java has **single inheritance of classes** but **multiple inheritance of interfaces**. That asymmetry creates a design question every type you write must answer: *is this a contract, or is it a shared base?*

```text
   THE QUESTION
   ═══════════
   "I have a set of related types that share SOME behaviour and some DATA.
    How do I model that?"

   ❌ An interface only  → I cannot share the state or the common code.
   ❌ An abstract class only → I cannot also extend another class.
   ✅ Both              → pick the one that matches the RELATIONSHIP.
```

**The historical timeline explains why the question is even asked:**

```text
   Java 5-7   interface  = a pure contract (methods only, no code)
              abstract  = a partial implementation (code + state)
              → the distinction was CLEAR

   Java 8     interface  = a contract + default/static methods (code, no state)
              abstract  = a partial implementation (code + state)
              → the distinction became "STATE" (and much subtler)

   Java 9+    interface  = + private methods
   Java 17    interface  = + SEALED (a closed set of implementors)
   Java 21    interface  = + record patterns, exhaustive switch
              → the distinction is now "does it carry STATE, and is the set OPEN or CLOSED?"
```

> 💡 **The interview framing.** "Interfaces are for multiple inheritance" is the
> answer to a question nobody asked. The question is: **"what is the relationship
> between these types?"** An interface says *"these types can do X"* (a
> **capability**). An abstract class says *"these types **are a kind of** Y"*
> (a **taxonomy**). Get the relationship right and the choice follows.

---

## 2. Mental Model — The One Picture

```text
   THE DECISION

        "Is this a CAPABILITY or a TAXONOMY?"
                    │
        ┌───────────┴────────────┐
        │                        │
   CAPABILITY                TAXONOMY
   "can do X"                "IS A kind of Y"
        │                        │
        ▼                        ▼
   ┌─────────┐              ┌──────────────────┐
   │INTERFACE│              │  ABSTRACT CLASS  │
   │         │              │                  │
   │ no state│              │  state ✅        │
   │ default │              │  code ✅         │
   │  methods│              │  ctor ✅         │
   │ multiple│              │  single inherit. │
   │  inherit│              │                  │
   └─────────┘              └──────────────────┘

   THE THREE QUESTIONS THAT DECIDE IT
   ─────────────────────────────────────
   1. Do the subtypes need SHARED STATE?      → abstract class
   2. Do the subtypes need SHARED CODE?       → interface (default method) OR abstract class
   3. Do the subtypes need to extend ANOTHER class? → interface
   4. Is the set of subtypes CLOSED and known? → sealed interface
```

**What to look at:** the *only* technical difference that survives Java 8 is
**instance state** (and the constructor). Everything else — shared code, static
helpers, private helpers — an interface can now do.

---

## 3. Deep Dive — Theory

## 3.1 What each can have — the exact rules

```java
// ══════════════════ INTERFACE ══════════════════
public interface ModernInterface extends OtherInterface {

    // CONSTANTS — implicitly public static final
    int MAX_RETRIES = 3;

    // ABSTRACT methods — implicitly public abstract
    void execute();

    // DEFAULT methods — Java 8+ (shared code, but NO state)
    default void executeWithRetry() {
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                execute();
                return;
            } catch (RuntimeException e) {
                if (i == MAX_RETRIES - 1) throw e;
            }
        }
    }

    // STATIC methods — Java 8+ (NOT inherited by implementors)
    static ModernInterface create() {
        return () -> { };
    }

    // PRIVATE methods — Java 9+ (helpers for the default methods)
    private void log(String message) {
        System.out.println(message);
    }
}

// ══════════════════ ABSTRACT CLASS ══════════════════
public abstract class BaseClass extends ParentClass implements SomeInterface {

    // STATE — the thing an interface cannot have
    private final List<String> history = new ArrayList<>();
    protected int counter = 0;

    // CONSTRUCTOR — an interface cannot have one
    protected BaseClass(String initial) {
        this.history.add(initial);
    }

    // ABSTRACT + CONCRETE methods
    public abstract void execute();

    public void record(String entry) {
        history.add(entry);
        counter++;
    }

    // It can also override a default method
    @Override
    public void executeWithRetry() { /* … */ }
}
```

| Feature | Interface | Abstract class |
|---|---|---|
| Abstract methods | ✅ | ✅ |
| `default` methods (shared code) | ✅ (Java 8+) | ✅ |
| `static` methods | ✅ (Java 8+) | ✅ |
| `private` methods | ✅ (Java 9+) | ✅ |
| **Instance fields (state)** | ❌ | ✅ |
| **Constructor** | ❌ | ✅ |
| **Instance initialisers** | ❌ | ✅ |
| Multiple inheritance | ✅ | ❌ |
| `sealed` (a closed set) | ✅ (Java 17+) | ✅ |
| A `main` method | ❌ (only `static`) | ✅ |
| Can be `final` | ✅ | ❌ (abstract + final is illegal) |

**Why an interface cannot have instance state:** every implementor would need to
*inherit* that state, and the interface has no way to initialise it. A `default`
method can only have *static* state, which is shared by all implementors — which is
usually not what you want.

## 3.2 The `default` method — the feature that changed everything

```java
public interface Sized {
    int size();

    default boolean isEmpty() {
        return size() == 0;     // shared code, no state needed ✅
    }

    default boolean isBiggerThan(Sized other) {
        return size() > other.size();
    }
}
```

**Why it exists — the compatibility problem:**

```text
   WITHOUT default methods (Java 7 and earlier)
   ─────────────────────────────────────────
   interface Collection {
       boolean isEmpty();     // ← adding this in Java 8 would break EVERY implementor
   }                          //   in the world: the JDK's, Spring's, everyone's

   WITH default methods (Java 8+)
   ─────────────────────────────
   interface Collection {
       default boolean isEmpty() { return size() == 0; }   // ← existing implementors
   }                                                     //   are UNAFFECTED ✅
```

**The conflict rules — a classic interview question:**

```text
   RULE 1 — a CLASS always wins over an INTERFACE.
            If a class inherits a concrete method from its superclass and
            an interface declares the same method, the CLASS's method wins.
            (No conflict at all — the class is more specific.)

   RULE 2 — a SUBINTERFACE beats a SUPERINTERFACE.
            If two interfaces (one extending the other) declare the same
            method, the SUBCLASS's version wins.

   RULE 3 — two UNRELATED interfaces with the same method = COMPILE ERROR.
            The implementing class MUST override it, and may disambiguate
            with InterfaceName.super.method().
```

```java
interface Parent   { default void greet() { System.out.println("parent"); } }
interface Child    extends Parent { @Override default void greet() { System.out.println("child"); } }
class Impl implements Child { }
// Impl.greet() → "child"   ✅ Rule 2

interface A { default void which() { System.out.println("A"); } }
interface B { default void which() { System.out.println("B"); } }
// class Bad implements A, B { }        ❌ COMPILE ERROR

class Good implements A, B {
    @Override public void which() { A.super.which(); }   // ✅ disambiguate
    // or: B.super.which();
    // or: System.out.println("own");
}
```

## 3.3 The `private` interface method (Java 9+)

```java
public interface Validator {

    /** @return true when the value is acceptable. */
    boolean validate(String value);

    // A private helper — NOT inherited, NOT visible to implementors
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // A private STATIC helper
    private static String normalise(String value) {
        return value == null ? "" : value.strip();
    }

    /** A blank value is "optional" — anything else must pass the real validation. */
    default boolean validateOptional(String value) {
        return isBlank(value) || validate(normalise(value));
    }
}
```

**Why it matters:** it lets an interface hold **real implementation logic** without
exposing helper methods to the implementors. Before Java 9, the workaround was a
`static` method in a separate utility class — which pollutes the namespace.

## 3.4 Sealed interfaces (Java 17) — the closed set

```java
// A CLOSED set of implementations
public sealed interface PaymentMethod
        permits CardPayment, WalletPayment, CashOnDelivery {

    BigDecimal fee(BigDecimal amount);

    // A default method that all three share
    default String describe() {
        return switch (this) {
            case CardPayment c    -> "Card ending " + c.last4();
            case WalletPayment w  -> w.provider() + " wallet";
            case CashOnDelivery d -> "COD in " + d.region();
        };
    }
}

public record CardPayment(String last4) implements PaymentMethod {
    public BigDecimal fee(BigDecimal amount) { return amount.multiply(new BigDecimal("0.029")); }
}
public record WalletPayment(String provider) implements PaymentMethod {
    public BigDecimal fee(BigDecimal amount) { return BigDecimal.ZERO; }
}
public record CashOnDelivery(String region) implements PaymentMethod {
    public BigDecimal fee(BigDecimal amount) { return new BigDecimal("5.00"); }
}
```

**What `sealed` gives you that a plain interface does not:**

| Property | Plain interface | `sealed` interface |
|---|---|---|
| The set of implementors | Open — anyone may add one | **Closed** — only the permitted types |
| Switch exhaustiveness | Not checkable | ✅ **Checked by the compiler** |
| A shared `default` method with a `switch` | Needs a `default` branch | ✅ No `default` needed — the compiler proves it |
| "Enum with behaviour" | ❌ (an enum cannot carry per-case state) | ✅ (each case is its own `record`) |
| Extension later | ✅ freely | ⚠ only by editing the `permits` clause |

**This is the modern answer to the old question.** A `sealed` interface gives you the
**closed-set** guarantee that only an abstract class used to provide, while keeping
the **no-state** and **multiple-implementation** properties of an interface.

## 3.5 The decision rules — in full

```text
   STEP 1: Is the set of implementations CLOSED and KNOWN?
   ────────────────────────────────────────────────────────
     YES → a sealed interface (Java 17+) — the best of both worlds
     NO  → continue

   STEP 2: Do the implementations need to share STATE or a CONSTRUCTOR?
   ───────────────────────────────────────────────────────────────
     YES → an ABSTRACT CLASS
     NO  → continue

   STEP 3: Do the implementations need to extend ANOTHER class?
   ───────────────────────────────────────────────────────────
     YES → an INTERFACE (Java has single class inheritance)
     NO  → continue

   STEP 4: Is this a CAPABILITY ("can do X") or a TAXONOMY ("is a kind of Y")?
   ────────────────────────────────────────────────────────────────
     CAPABILITY → INTERFACE
     TAXONOMY   → an ABSTRACT CLASS (or a sealed interface)

   STEP 5: Is there exactly ONE implementation and no test double?
   ────────────────────────────────────────────────────────────
     YES → use the CLASS directly; an interface with one implementation
           and no substitutability is ceremony
     NO  → an interface, so the type can be substituted
```

**The "one implementation" test — a design heuristic worth stating:**

```text
   An interface earns its keep when there is:
     ✓ more than one implementation, OR
     ✓ a test double (a fake/mock), OR
     ✓ a caller who must not know the implementation (a "port" in hexagonal terms)

   With exactly one implementation and no substitutability requirement,
   the interface is a **naming ceremony** — and it costs you an extra
   file and an extra indirection on every call.
```

## 3.6 The `is-a` vs `has-a` test

```text
   "IS A"  → inheritance
   ─────────
   A Car IS A Vehicle.        ✅ extend Vehicle / implement Vehicle
   A SavingsAccount IS AN Account.  ✅
   A Circle IS A Shape.      ✅

   "HAS A" → composition
   ─────────
   A Car HAS A Engine.       ❌ do NOT extend Engine
   An Order HAS A PaymentMethod.  ❌
   A Circle HAS A Point.     ❌

   THE LAMP TEST: can you say "X is a Y" without wincing?
   If the sentence is awkward or the substitutability is false —
   it is HAS A, and you want composition.
```

**A famous counter-example:**

```java
// ❌ "Stack IS A ArrayList" — it is not substitutable
class BadStack<E> extends ArrayList<E> {
    @Override public void add(int index, E element) {
        throw new UnsupportedOperationException();  // 💥 breaks the List contract
    }
}

// ✅ "Stack HAS A deque"
class Stack<E> {
    private final Deque<E> items = new ArrayDeque<>();
    public void push(E e) { items.push(e); }
    public E pop() { return items.pop(); }
}
```

## 3.7 What the JDK itself does — the best evidence

| JDK type | Choice | Why |
|---|---|---|
| `List`, `Set`, `Map`, `Queue` | **Interfaces** | Multiple implementations are the whole point |
| `ArrayList`, `HashMap` | Classes | The implementations |
| `Collection`, `Iterable` | Interfaces | Capabilities |
| `AbstractList`, `AbstractMap` | **Abstract classes** | Shared *state* (the `modCount`, the table) and shared code |
| `InputStream`, `OutputStream` | Abstract classes | Shared state (`buf`, `count`, `pos`) |
| `Reader`, `Writer` | Abstract classes | Shared state |
| `Thread` | A class | It IS a thread — the "is-a" relationship is literal |
| `Runnable`, `Callable` | Interfaces | A capability |
| `Comparator` | Interface | Multiple orderings; a lambda can implement it |
| `UncaughtExceptionHandler` | Interface | A capability |
| `CharSequence` | Interface | A capability (String, StringBuilder, StringBuffer) |

> 💡 **The pattern in the JDK:** the *abstractions* (`List`, `Set`, `Map`) are
> interfaces, and the *base implementations* that share state (`AbstractList`,
> `AbstractMap`) are abstract classes. The JDK uses **both** — and it uses the
> interface when the relationship is a capability and the abstract class when
> it is a base with shared state.

---

## 4. Visualizations

### 4.1 The decision tree

```text
   I have a set of related types to model.
                    │
                    ▼
        ┌───────────────────────────┐
        │ Is the set of implementors│
        │ CLOSED and KNOWN?         │
        └─────────────┬─────────────┘
                 YES  │        NO
        ┌────────────▼─────┐      │
        │ SEALED INTERFACE │      │
        │ (Java 17+)       │      │
        │ closed set + no   │      │
        │ state + multiple │      │
        │ inheritance ✅    │      │
        └──────────────────┘      │
                    │             ▼
                    │    ┌──────────────────────────┐
                    │    │ Do they need SHARED STATE │
                    │    │ or a CONSTRUCTOR?        │
                    │    └────────────┬─────────────┘
                    │             YES  │        NO
                    │    ┌────────────▼─────┐      │
                    │    │ ABSTRACT CLASS   │      │
                    │    │ state ✅         │      │
                    │    │ ctor ✅          │      │
                    │    │ single inherit. │      │
                    │    └──────────────────┘      │
                    │                            ▼
                    │                 ┌──────────────────────────┐
                    │                 │ CAPABILITY ("can do X")  │
                    │                 │ or TAXONOMY ("is a Y")?  │
                    │                 └────────────┬─────────────┘
                    │                        CAPABILITY│  TAXONOMY
                    │                              ▼        ▼
                    │                         INTERFACE  (consider a sealed
                    │                                    interface or an
                    │                                    abstract class)
                    ▼
              ONE implementation and
              no test double?
                    YES → use the CLASS directly
```

### 4.2 What each can hold — the exact boundary

```text
   ┌────────────────────────────────────┬───────────────┬────────────────┐
   │ FEATURE                           │  INTERFACE    │ ABSTRACT CLASS │
   ├────────────────────────────────────┼───────────────┼────────────────┤
   │ Abstract methods                   │      ✅       │       ✅       │
   │ Shared code (default methods)      │      ✅       │       ✅       │
   │ Static helper methods              │      ✅       │       ✅       │
   │ Private helper methods             │      ✅ (9+)  │       ✅       │
   │ ───────────────────────────────────┼───────────────┼────────────────┤
   │ INSTANCE FIELDS (state)            │      ❌       │       ✅       │
   │ CONSTRUCTOR                        │      ❌       │       ✅       │
   │ Instance initialisers              │      ❌       │       ✅       │
   │ ───────────────────────────────────┼───────────────┼────────────────┤
   │ Multiple inheritance of type       │      ✅       │       ❌       │
   │ Sealed (closed set)                │      ✅ (17+) │       ✅       │
   │ Can be `final`                     │      ✅       │       ❌       │
   └────────────────────────────────────┴───────────────┴────────────────┘

   THE ONLY SURVIVING TECHNICAL DIFFERENCE: **STATE**.
   (Java 8 gave interfaces default methods; Java 9 gave them private ones.)
```

### 4.3 The `default` method conflict rules

```text
   ① CLASS beats INTERFACE
   ────────────────────────
      class Base { void f() { print("base"); } }
      interface I { default void f() { print("I"); } }
      class Sub extends Base implements I { }

      new Sub().f()  →  "base"   ✅ NO CONFLICT
      (the class is more specific)


   ② SUBINTERFACE beats SUPERINTERFACE
   ─────────────────────────────────────
      interface P { default void f() { print("P"); } }
      interface C extends P { @Override default void f() { print("C"); } }
      class Impl implements C { }

      new Impl().f()  →  "C"   ✅ the most specific wins


   ③ TWO UNRELATED INTERFACES  →  COMPILE ERROR
   ──────────────────────────────────────────────────
      interface A { default void f() { print("A"); } }
      interface B { default void f() { print("B"); } }
      class Bad implements A, B { }        ❌ "inherits unrelated defaults"

      class Good implements A, B {
          @Override public void f() { A.super.f(); }   // ✅ disambiguate
      }
```

### 4.4 Sealed interface — the closed set

```text
   ❌ AN OPEN INTERFACE
   ───────────────────
   interface Shape { double area(); }

   double describe(Shape s) {
       return switch (s) {
           case Circle c -> "circle";
           case Square q -> "square";
           default -> "other";     // ← a new Shape would silently land here 💥
       };
   }

   ✅ A SEALED INTERFACE
   ────────────────────
   sealed interface Shape permits Circle, Square, Triangle { double area(); }

   double describe(Shape s) {
       return switch (s) {
           case Circle c   -> "circle";
           case Square q   -> "square";
           case Triangle t -> "triangle";
           // ← NO default! the compiler PROVES it is exhaustive
           //   add a Rectangle → this method becomes a COMPILE ERROR ✅
       };
   }
```

### 4.5 The is-a vs has-a test

```text
   "IS A"  → INHERITANCE
   ─────────
   A Car IS A Vehicle.             ✅ Vehicle vehicle = new Car();
   A SavingsAccount IS AN Account.  ✅
   A Circle IS A Shape.             ✅

   "HAS A" → COMPOSITION
   ─────────
   A Car HAS A Engine.              ❌ Car is NOT an Engine
   An Order HAS A PaymentMethod.    ❌
   A Stack HAS A Deque.             ❌ (and the famous "Stack extends ArrayList" bug)

   THE TEST: can you substitute the subtype for the supertype EVERYWHERE
   the supertype is used? If not — it is "has a", and use composition.
```

### 4.6 The JDK's own choices — the best evidence

```text
   CAPABILITY  → interface
   ────────────
   List · Set · Map · Queue · Runnable · Callable · Comparator
   CharSequence · AutoCloseable · Iterable · Cloneable

   BASE WITH SHARED STATE → abstract class
   ─────────────────────────────────────────
   AbstractList (modCount)  AbstractMap (table)  AbstractCollection
   InputStream (buf,count,pos)  OutputStream  Reader  Writer

   A TAXONOMY ROOT → a class
   ─────────────────────
   Thread  (it literally IS a thread)
   Exception / Throwable

   ⇒ THE JDK USES BOTH. The abstraction is an interface; the base
     implementation with shared state is an abstract class.
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — A capability (interface) vs a taxonomy (abstract class)

```java
import java.math.BigDecimal;

public class InterfaceVsAbstract {

    // ═══ A CAPABILITY — an interface. "can be paid", "can be notified"
    interface Payable {
        BigDecimal amount();

        default BigDecimal withFee(BigDecimal fee) {
            return amount().add(fee);
        }

        static Payable of(BigDecimal amount) {
            return () -> amount;
        }
    }

    // ═══ A TAXONOMY — an abstract class. "IS A kind of vehicle", sharing STATE
    abstract static class Vehicle {
        protected final String registration;
        private int odometer;                 // ⚠ SHARED STATE — impossible in an interface

        protected Vehicle(String registration) {
            this.registration = registration;
        }

        public void drive(int km) {
            if (km < 0) throw new IllegalArgumentException("km must be >= 0");
            odometer += km;
        }

        public int odometer() { return odometer; }

        public abstract BigDecimal dailyCost();
    }

    static final class Car extends Vehicle {
        Car(String reg) { super(reg); }
        @Override public BigDecimal dailyCost() { return new BigDecimal("12.50"); }
    }

    static final class Truck extends Vehicle {
        Truck(String reg) { super(reg); }
        @Override public BigDecimal dailyCost() { return new BigDecimal("45.00"); }
    }

    public static void main(String[] args) {
        Car car = new Car("KA-01");
        car.drive(120);
        System.out.println("car odometer  = " + car.odometer() + " km  ← SHARED STATE in the base class");
        System.out.println("car dailyCost = " + car.dailyCost());

        Payable p = Payable.of(new BigDecimal("100"));
        System.out.println("payable        = " + p.amount() + "  ← an interface CAN hold behaviour");
        System.out.println("with a fee     = " + p.withFee(new BigDecimal("2.50")));

        // ✅ A sealed interface when the set is closed
        System.out.println("✅ and a sealed interface gives the closed-set guarantee");
    }
}
```

**Actual output:**

```text
car odometer  = 120 km  ← SHARED STATE in the base class
car dailyCost = 12.50
payable        = 100  ← an interface CAN hold behaviour
with a fee     = 102.50
✅ and a sealed interface gives the closed-set guarantee
```

**Takeaway:** the interface holds *behaviour* (`withFee`); the abstract class holds *state*
(`odometer`) that all the vehicles share. That is the real distinction.

### Example 2 — The `default` method conflict rules in action

```java
public class DefaultConflictDemo {

    interface Parent {
        default String greet() { return "parent"; }
    }
    interface Child extends Parent {
        @Override default String greet() { return "child"; }
    }

    // ① a CLASS wins over an INTERFACE
    static class Base { public String greet() { return "base"; } }
    static class Sub extends Base implements Parent { }

    interface A { default String which() { return "A"; } }
    interface B { default String which() { return "B"; } }

    // ② a SUBINTERFACE wins over a SUPERINTERFACE
    static class ImplChild implements Child { }

    // ③ two UNRELATED interfaces → the class MUST override
    static class PreferA implements A, B {
        @Override public String which() { return A.super.which(); }
    }
    static class PreferB implements A, B {
        @Override public String which() { return B.super.which(); }
    }
    static class Own implements A, B {
        @Override public String which() { return "own"; }
    }

    public static void main(String[] args) {
        System.out.println("① class beats interface  : " + new Sub().greet());
        System.out.println("② subinterface wins      : " + new ImplChild().greet());
        System.out.println("③ A.super.which()       : " + new PreferA().which());
        System.out.println("③ B.super.which()       : " + new PreferB().which());
        System.out.println("③ own implementation    : " + new Own().which());
    }
}
```

**Actual output:**

```text
① class beats interface  : base
② subinterface wins      : child
③ A.super.which()       : A
③ B.super.which()       : B
③ own implementation    : own
```

**Takeaway:** the three rules, demonstrated. Note that rule 1 produces **no conflict at all** — the class simply wins silently.

### Example 3 — A sealed interface versus an enum-with-behaviour

```java
import java.math.BigDecimal;

public class SealedVsEnum {

    // ❌ AN ENUM — all the cases share ONE class, so they share ONE set of fields
    enum LegacyMethod {
        CARD, WALLET, COD;

        BigDecimal fee() { return BigDecimal.ZERO; }   // the same for all — or use a switch
        String describe() { return "a payment method"; }
    }

    // ✅ A SEALED INTERFACE — each case is its OWN record, with its OWN state
    sealed interface Method permits Card, Wallet, Cod {
        BigDecimal fee();

        default String describe() {
            return switch (this) {
                case Card c   -> "Card ending " + c.last4();
                case Wallet w -> w.provider() + " wallet";
                case Cod d    -> "COD in " + d.region();
                // ← NO default — the compiler proves exhaustiveness ✅
            };
        }
    }

    record Card(String last4) implements Method {
        @Override public BigDecimal fee() { return new BigDecimal("0.029"); }
    }
    record Wallet(String provider) implements Method {
        @Override public BigDecimal fee() { return BigDecimal.ZERO; }
    }
    record Cod(String region) implements Method {
        @Override public BigDecimal fee() { return new BigDecimal("5.00"); }
    }

    public static void main(String[] args) {
        Method m = new Card("4242");
        System.out.println("sealed : " + m.describe() + "  fee=" + m.fee());
        System.out.println("         " + m.describe().getClass().getSimpleName()
                + "  ← the CASE is the runtime type ✅");

        // The enum version can only switch on the enum
        LegacyMethod legacy = LegacyMethod.CARD;
        System.out.println("enum   : " + legacy.describe() + "  (all cases share one class)");
    }
}
```

**Actual output:**

```text
sealed : Card ending 4242  fee=0.029
         Card  ← the CASE is the runtime type ✅
enum   : a payment method  (all cases share one class)
```

**Takeaway:** the sealed version carries **per-case state** (`last4`, `provider`, `region`)
and the compiler checks the `switch` for exhaustiveness. The enum version cannot hold
different fields per case — which is exactly why the sealed interface is the modern answer.

### Example 4 — The "one implementation" test

```java
public class OneImplementationDemo {

    // ❌ AN INTERFACE WITH ONE IMPLEMENTATION AND NO SUBSTITUTABILITY
    interface UserRepository {
        User findById(String id);
        void save(User user);
    }
    static class JdbcUserRepository implements UserRepository {
        public User findById(String id) { return new User(id, "Alice"); }
        public void save(User user) { }
    }

    // ✅ A CLASS — when there is exactly one implementation and no test double
    static class OrderService {
        private final JdbcUserRepository repository = new JdbcUserRepository();
        public User load(String id) { return repository.findById(id); }
    }

    record User(String id, String name) {}

    public static void main(String[] args) {
        OrderService service = new OrderService();
        System.out.println("loaded = " + service.load("U-1"));
        System.out.println();
        System.out.println("The interface costs a file and an indirection on every call,");
        System.out.println("and buys nothing until a second implementation or a test double exists.");
    }
}
```

**Actual output:**

```text
loaded = User[id=U-1, name=Alice]

The interface costs a file and an indirection on every call,
and buys nothing until a second implementation or a test double exists.
```

**Takeaway:** an interface is a **contract with future substitutability**. If you cannot
name the second implementation or the test double, you are paying for a promise you are
not making.

### Example 5 — Evolving an interface with a `default` method

```java
import java.util.List;

public class DefaultEvolutionDemo {

    // The ORIGINAL interface (Java 7 style)
    interface Repository {
        List<String> findAll();
    }

    // The EVOLVED interface (Java 8+) — a new method with a default body
    interface RepositoryV2 {
        List<String> findAll();

        default List<String> findByPrefix(String prefix) {
            return findAll().stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
    }

    // An EXISTING implementor — written against the OLD interface
    static class ListRepository implements Repository, RepositoryV2 {
        private final List<String> data = List.of("apple", "apricot", "banana", "blueberry");
        public List<String> findAll() { return data; }
    }

    public static void main(String[] args) {
        ListRepository repo = new ListRepository();

        // ✅ The NEW method works on the EXISTING implementor — no changes needed
        System.out.println("findAll        = " + repo.findAll());
        System.out.println("findByPrefix(a)= " + repo.findByPrefix("a"));
        System.out.println("findByPrefix(b)= " + repo.findByPrefix("b"));
        System.out.println();
        System.out.println("✅ the implementor was NEVER changed — that is what default methods buy");
    }
}
```

**Actual output:**

```text
findAll        = [apple, apricot, banana, blueberry]
findByPrefix(a)= [apple, apricot]
findByPrefix(b)= [banana, blueberry]

✅ the implementor was NEVER changed — that is what default methods buy
```

**Takeaway:** this is the *compatibility* argument for `default` methods, and it is why
"publishing an interface is risky" is now a much weaker objection than it was in Java 7.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A payment hierarchy: a sealed interface + a strategy for the rules

```java
package com.example.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A payment domain modelled as a SEALED interface.
 *
 * <p>Why sealed and not an enum: each case carries its OWN state (a card has a
 * last-4, a wallet has a provider), and a sealed hierarchy gives us the
 * compiler-checked exhaustiveness that an enum switch provides.
 */
public sealed interface PaymentMethod {

    /** The stable persisted identifier. */
    String code();

    /** The fee for a given amount. */
    BigDecimal fee(BigDecimal amount);

    /** A human-readable description. */
    String description();

    // ── The permitted cases ─────────────────────────────────────────────
    record Card(String last4, CardNetwork network) implements PaymentMethod {
        public Card {
            Objects.requireNonNull(last4, "last4");
            if (!last4.matches("\\d{4}")) {
                throw new IllegalArgumentException("last4 must be 4 digits");
            }
            Objects.requireNonNull(network, "network");
        }

        @Override public String code() { return "CARD"; }
        @Override public String description() { return "Card ending " + last4 + " (" + network + ")"; }

        @Override
        public BigDecimal fee(BigDecimal amount) {
            return amount.multiply(new BigDecimal("0.029")).add(new BigDecimal("0.30"))
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }

    record Wallet(String provider, String walletId) implements PaymentMethod {
        public Wallet {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(walletId, "walletId");
        }

        @Override public String code() { return "WALLET"; }
        @Override public String description() { return provider + " wallet " + walletId; }

        @Override
        public BigDecimal fee(BigDecimal amount) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    record BankTransfer(String bankCode, String accountNumber) implements PaymentMethod {
        public BankTransfer {
            Objects.requireNonNull(bankCode, "bankCode");
            Objects.requireNonNull(accountNumber, "accountNumber");
        }

        @Override public String code() { return "BANK"; }
        @Override public String description() { return "Transfer from " + bankCode; }

        @Override
        public BigDecimal fee(BigDecimal amount) {
            return new BigDecimal("4.50");
        }
    }

    // ── Shared behaviour, exhaustively checked ─────────────────────────
    /** The total with the fee applied. */
    default BigDecimal totalWithFee(BigDecimal amount) {
        return amount.add(fee(amount)).setScale(2, RoundingMode.HALF_UP);
    }

    /** A risk score — the compiler checks every case. */
    default int riskScore() {
        return switch (this) {
            case Card c     -> c.network() == CardNetwork.VISA ? 20 : 35;
            case Wallet w   -> 25;
            case BankTransfer b -> 40;
        };
    }

    enum CardNetwork { VISA, MASTERCARD, AMEX, RUPAY }
}
```

### 6.2 An abstract class where state genuinely must be shared

```java
package com.example.vehicle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A base class for vehicles — an ABSTRACT CLASS because the subclasses share
 * STATE (the odometer, the trip log) that an interface cannot hold.
 *
 * <p>Note: this is a genuine "IS A" relationship — a Car IS A Vehicle — and the
 * shared state is what makes inheritance the right tool here.
 */
public abstract class Vehicle {

    private final String registration;
    private final List<Trip> trips = new ArrayList<>();
    private int odometerKm;

    protected Vehicle(String registration) {
        this.registration = Objects.requireNonNull(registration, "registration");
    }

    public String registration() { return registration; }
    public int odometerKm() { return odometerKm; }
    public List<Trip> trips() { return List.copyOf(trips); }

    public void drive(int km) {
        if (km <= 0) {
            throw new IllegalArgumentException("km must be > 0");
        }
        odometerKm += km;
        trips.add(new Trip(km, odometerKm));
    }

    /** The cost of the next service — the subclass decides. */
    public abstract BigDecimal serviceCost();

    /** The fuel efficiency — the subclass decides. */
    public abstract double kmPerLitre();

    public record Trip(int km, int totalOdometer) {}

    // ── Concrete subclasses ────────────────────────────────────────────
    public static final class Car extends Vehicle {
        private final int seats;

        public Car(String registration, int seats) {
            super(registration);
            this.seats = seats;
        }

        @Override public BigDecimal serviceCost() { return new BigDecimal("250.00"); }
        @Override public double kmPerLitre() { return 18.5; }
        public int seats() { return seats; }
    }

    public static final class Truck extends Vehicle {
        private final double maxTonnes;

        public Truck(String registration, double maxTonnes) {
            super(registration);
            this.maxTonnes = maxTonnes;
        }

        @Override public BigDecimal serviceCost() { return new BigDecimal("850.00"); }
        @Override public double kmPerLitre() { return 6.2; }
        public double maxTonnes() { return maxTonnes; }
    }
}
```

### 6.3 A template-method base class with a pluggable strategy

```java
package com.example.report;

import java.util.List;
import java.util.Objects;

/**
 * A base class that fixes the ALGORITHM and delegates the VARIABLE parts.
 *
 * <p>This is the "shared code in an abstract class" case: the report flow is
 * identical for every format; only the rendering differs. An interface with a
 * default method could express the shared flow too — but this class also
 * carries the shared STATE (the rows, the title), which an interface cannot.
 */
public abstract class AbstractReport {

    private final String title;
    protected final List<String> rows = new java.util.ArrayList<>();

    protected AbstractReport(String title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    /** The TEMPLATE — final, so the flow cannot change. */
    public final String render() {
        StringBuilder out = new StringBuilder();
        out.append(header());
        for (String row : rows) {
            out.append(formatRow(row));
        }
        out.append(footer());
        return out.toString();
    }

    public AbstractReport addRow(String row) {
        rows.add(Objects.requireNonNull(row, "row"));
        return this;
    }

    // ── The HOOKS — the variable parts ─────────────────────────────────
    protected abstract String header();
    protected abstract String formatRow(String row);
    protected abstract String footer();

    public String title() { return title; }

    // ── Concrete reports ───────────────────────────────────────────────
    public static final class CsvReport extends AbstractReport {
        public CsvReport(String title) { super(title); }
        @Override protected String header() { return "id,name\n"; }
        @Override protected String formatRow(String row) { return row + "\n"; }
        @Override protected String footer() { return ""; }
    }

    public static final class HtmlReport extends AbstractReport {
        public HtmlReport(String title) { super(title); }
        @Override protected String header() { return "<table><caption>" + title() + "</caption>"; }
        @Override protected String formatRow(String row) { return "<tr><td>" + row + "</td></tr>"; }
        @Override protected String footer() { return "</table>"; }
    }
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — "Interfaces can't have code" (obsolete since Java 8)

```java
// ❌ The pre-Java-8 belief
```

**✅ FIX** — Java 8 added `default` and `static` methods; Java 9 added `private` ones.

---

### ❌ Pitfall 2 — Using an interface when state is needed

```java
interface Vehicle {
    int getOdometer();      // ⚠ where does the state live?
    void setOdometer(int km);
}
```

**✅ FIX** — an abstract class holds the state; the interface expresses only the capability.

---

### ❌ Pitfall 3 — Two unrelated interfaces with the same `default` method

```java
// COMPILE-ERROR-DEMO — this block deliberately does NOT compile.
interface A { default void f() { } }
interface B { default void f() { } }
class C implements A, B { }   // ❌ compile error
```

**✅ FIX** — override and disambiguate: `A.super.f();`

---

### ❌ Pitfall 4 — An interface with exactly one implementation and no test double

```java
interface UserRepository { }   // ❌ a naming ceremony
```

**✅ FIX** — use the class; add the interface when a second implementation or a fake appears.

---

### ❌ Pitfall 5 — Extending when you mean "has-a"

```java
class Stack<E> extends ArrayList<E> { }   // ❌ the famous LSP violation
```

**✅ FIX** — compose: `private final Deque<E> items = new ArrayDeque<>();`

---

### ❌ Pitfall 6 — Assuming an enum can carry per-case state

```java
enum Method {
    CARD("4242"), WALLET("paytm");
    private final String code;
    Method(String code) { this.code = code; }
    // ⚠ "4242" and "paytm" mean different THINGS — but they share one field
}
```

**✅ FIX** — a sealed interface with a `record` per case.

---

### ❌ Pitfall 7 — `default` methods that hide a real behavioural difference

```java
interface Collection<E> extends Iterable<E> {
    boolean add(E e);                                  // ⚠ may throw for an immutable collection
    default void addAll(Collection<? extends E> other) {
        for (E o : other) add(o);                      // ⚠ add() may throw for an immutable collection
    }
}
```

**✅ FIX** — a `default` method must have a *correct* body for every implementor, or it will break the ones that cannot support it. This is the risk `default` methods introduced.

---

### ❌ Pitfall 8 — A `default` method that captures `this` in a lambda

```java
default void process() {
    items.forEach(item -> handle(item));   // ⚠ `this` is the implementor — may be mutable
}
```

**✅ FIX** — be careful with `default` methods that call back into overridable methods: a subclass's override may see a partially-initialised object.

---

### ❌ Pitfall 9 — Forgetting that `static` interface methods are NOT inherited

```java
interface Utils { static void helper() { } }
class Impl implements Utils { }
// Impl.helper();   ❌ compile error — static interface methods are not inherited
```

**✅ FIX** — call `Utils.helper()`.

---

### ❌ Pitfall 10 — An abstract class with an abstract method but no abstract intent

```java
abstract class Base { abstract void run(); }   // ❌ why abstract? just make it a class
```

**✅ FIX** — if there is no shared state and no shared code, use a plain class.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: When do you use an interface and when an abstract class?**
**A:** Use an **interface** to express a *capability* ("these types can do X") or when you need multiple inheritance of type. Use an **abstract class** when the subtypes must share **state** or a **constructor**, or when you want to share code across a hierarchy with `is-a` semantics. Since Java 8, interfaces can have `default` and `static` methods, so **state is the only remaining technical difference**.

**Q: Can an interface have a constructor?**
**A:** No. An interface has no instance state for a constructor to initialise. Its fields are implicitly `public static final`, so there is nothing per-instance to construct.

**Q: Can an abstract class have `final` methods?**
**A:** Yes — and a `final` method in an abstract class is the standard way to make a *template method* non-overridable (the algorithm skeleton is fixed; the hooks are overridable). Note that an abstract class itself cannot be `final`.

**Q: What is a marker interface?**
**A:** An interface with **no methods at all** — it exists purely as a *tag* that `instanceof` can test (`Serializable`, `Cloneable`, `Remote`). It is the pre-annotation way of saying "this object has this property". An **annotation** is usually the better tool today, because it can carry data.

**Q: Can you instantiate an interface?**
**A:** No, not directly. You use an anonymous class (`new Runnable() { … }`), a lambda (for a functional interface), or — in modern code — a `sealed` interface with `record` implementations.

### 🟡 Intermediate

**Q: What did Java 8's `default` methods change about this decision?**
**A:** They removed the "interfaces have no code" objection, because an interface can now ship shared behaviour. And they solved the **compatibility problem**: an interface could be *extended* with a new method without breaking every implementor in the world. This is why `Collection.forEach`, `List.sort` and `Comparator`'s static helpers could all be added in Java 8. The remaining distinction is purely **state** — and, since Java 17, the *closed-set* guarantee via `sealed`.

**Q: What is a functional interface, and how does it relate?**
**A:** A functional interface has exactly one abstract method, so it can be implemented by a lambda. It is the *sub-case* of an interface used when the type is a "one-operation" contract. `Runnable`, `Comparator` and `Function` are functional interfaces. An abstract class can never be implemented by a lambda.

**Q: How do you decide between a `sealed` interface and an `enum`?**
**A:** If the cases carry **different state**, use a `sealed` interface with a `record` per case (an enum's constants are all instances of one class, so they share one set of fields). If the cases are pure labels, an `enum` is simpler and gives you `values()`, `valueOf`, `EnumSet` and `EnumMap` for free. A `sealed` interface plus a `default` method with a `switch` gives you the same compiler-checked exhaustiveness an enum switch provides.

**Q: What is the "one implementation" heuristic?**
**A:** An interface earns its keep when there is **more than one implementation**, or a **test double** (a fake or a mock), or a **caller that must not know the implementation** (a "port"). With exactly one implementation and no substitutability requirement, the interface is a naming ceremony that costs a file and an indirection on every call — and it can be added later, at the point where the second implementation appears.

**Q: What happens if two interfaces have the same `default` method?**
**A:** It depends on their relationship. A class method beats an interface method (no conflict). A subinterface's `default` beats its superinterface's. Two **unrelated** interfaces with the same `default` method is a **compile error** — the implementing class must override it, and may disambiguate with `InterfaceName.super.method()`.

### 🔴 Advanced / Senior

**Q: Java has single class inheritance. How would you model a type that needs to extend another class *and* express a capability?**
**A:** Two options. (1) **Composition + an interface** — the class extends what it must be and *implements* the capability, delegating to a collaborator. This is the default and the right answer most of the time. (2) **Interface with `default` methods** — put the capability's behaviour in a `default` method that calls an abstract "hook" the class implements. This is the pattern that lets you have "mixin" behaviour without inheritance: `interface Closeable { default void closeQuietly() { try { close(); } catch (IOException e) { … } } }`. The trade-off of (2) is that the `default` method's correctness now depends on the hook's contract, and it can be *overridden* by accident — which is a real maintenance risk in a large codebase.

**Q: How do you evolve a published interface without breaking its implementors?**
**A:** With `default` methods (Java 8+) — a new method with a body leaves the existing implementors unaffected. Two cautions: (1) the `default` body's semantics must be **correct for every implementor** (a `default` that assumes mutability will break an immutable implementor); and (2) a `default` method that calls an overridable method can observe a **partially initialised** object when it is called from a subclass constructor — the classic "overridable method called from the constructor" hazard, which `default` methods inherit. If the change is not safely expressible as a `default`, the honest options are a new interface (`interface V2 extends V`) or an **abstract class**.

**Q: When is an abstract class the wrong choice?**
**A:** When the relationship is "has-a" rather than "is-a" — inheritance then couples the child to the parent's *implementation*, and a change to the parent can break every child. The `Stack extends ArrayList` case is the canonical example: the child inherits `add(int index, E)`, which is meaningless for a stack, so the subclass must throw — breaking the `List` contract. The second case is when the shared "code" is small: a two-line shared method does not justify a whole inheritance hierarchy, and composition is clearer. The third is when the set of subtypes should be **open** — an abstract class permits only one inheritance line, so if the type must also implement a framework's interface hierarchy, the class is the constraint.

**Q: How do `sealed` types change the interface-vs-abstract-class decision?**
**A:** They largely **dissolve** it for the "closed set" case. A `sealed` interface gives you: multiple implementations, no shared state, a **compiler-enforced** set of implementors, and — the big one — **exhaustiveness checking in a `switch` without a `default`**. That is the property that previously made an abstract class attractive (you could switch over the subclasses and be sure you had covered them all). The remaining reasons to use an abstract class are therefore *state* and *constructors* — and the "shared code with a fixed algorithm" (template method) pattern, which a `sealed` interface can also express with a `default` method. The modern guidance: **default to a `sealed` interface for a closed domain; use an abstract class only when you genuinely need shared state or a constructor.**

### ⚫ Expert / Principal

**Q: Java's type system has no way to express "this interface has exactly these implementations" before Java 17. How did teams work around it, and what did `sealed` change?**
**A:** The workarounds were three. (1) **A `default` method with a `switch` on the runtime type** — which requires a `default` branch, so a new implementation is **silently** mishandled. This is the "open/closed violation by stealth" that the type system cannot catch. (2) **A registry** — a `Map<Class<?>, Implementation>` populated at startup, which moves the problem from the type system to a runtime lookup and loses the compile-time check entirely. (3) **An abstract class with an abstract method** — which *does* give exhaustiveness (the compiler knows the concrete subclasses *within* the hierarchy) but forces single inheritance and shared state. `sealed` interfaces (Java 17) gave the *first* option the compile-time safety of the third: the permitted set is declared, the compiler checks that every `switch` covers it, and a new implementation requires editing the `permits` clause — which the compiler then enforces everywhere. The deeper significance: `sealed` is the language's first **explicit statement of exhaustiveness** in a type hierarchy, and it is what makes "the compiler checks your `switch`" a general property rather than an enum-specific trick. Combined with record patterns (Java 21), it also removes the "visitor pollution" of the classic Visitor pattern — the element types no longer need an `accept` method. The architectural reading: `sealed` moves the *open/closed decision* from a **convention** (a `default` branch, a registry entry) to a **declaration** in the type system — and that is the same trajectory the language has been on since Java 8: move it from runtime to compile time.

---

## 9. Counter-Questions & Traps

### CQ1. "If interfaces can have default methods now, what's left for abstract classes?"

- **Naive:** "Shared state and constructors."  ← *correct, but it is a list, not a decision rule*
- **The trap:** The trap is to present a feature checklist and stop. The interviewer is asking: *given that the old reason is gone, what is the rule now?* The answer has to be a **decision procedure**, not a list.
- **Expert answer:** The precise remainder: **state, constructors, and the "single-inheritance line" constraint.** And the decision procedure that follows from it: (1) Do the subtypes need to *hold data* that the supertype manages? → abstract class. (2) Do they need a constructor to establish that state? → abstract class. (3) Must the type also extend another class? → interface. (4) Is the set of subtypes closed and known? → `sealed` interface. (5) Otherwise, and if the shared code is a **fixed algorithm with variable steps** (a template method), → abstract class; if the shared code is a **set of interchangeable operations** (a capability), → interface with `default` methods. The meta-point worth making: **the question "interface or abstract class?" is a symptom of a deeper question — "what is the relationship?"** Get the relationship right and the mechanism follows mechanically. An interview that pushes on this is testing whether you reason from the *domain* or from the *syntax*.

### CQ2. "You said an interface with one implementation is a smell. But surely that's the 'program to an interface' principle?"

- **Naive:** "The principle says depend on abstractions, not implementations."  ← *the principle quoted, and its limits missed*
- **The trap:** The trap is to treat "depend on abstractions" as an absolute. The principle is a **heuristic for managing coupling**, and it has a *cost*: an interface adds a file, an indirection, and a promise (that there will be substitutability) that may never be kept. Applying it reflexively produces "interface ceremony" — a codebase where every class has a matching interface with one implementation.
- **Expert answer:** The refined principle, which is what a senior engineer actually believes: **depend on the abstraction at the boundary where the implementation may vary, and on the concrete type everywhere else.** The boundary is usually the *edge of your module* — the repository the service talks to, the client it calls. Inside a module, where you control both sides and the implementation is not going to vary, a concrete class is *more* honest and *less* coupled. And the cost of premature abstraction is not just a file: it is (1) an indirection on every call, (2) a **false promise** that the type is substitutable, and (3) a *navigation* cost — a developer has to jump between two files to see the whole type. The right moment to introduce the interface is **when the second implementation appears** (a real one, or a test fake) — and that is a *design* decision you can make later, at low cost, because adding an interface to an existing class is a non-breaking change. The deep lesson: **abstraction is cheap to add later and expensive to remove** — so add it when the evidence arrives, not before.

### CQ3. "Java 17's `sealed` — doesn't that just make abstract classes obsolete?"

- **Naive:** "Yes, mostly."  ← *partly true, and it misses the two things `sealed` cannot do*
- **The trap:** The trap is to believe `sealed` gives an interface everything an abstract class has. It does not: a `sealed` interface still has **no instance state and no constructor** — so a hierarchy that needs shared mutable state still needs an abstract class. And `sealed` has a *cost* the abstract class does not: the set is **frozen at compile time**.
- **Expert answer:** The precise comparison. A `sealed` interface gives you: multiple implementations, no shared state, a closed set, and **compiler-checked exhaustiveness** in a `switch`. An abstract class gives you: all of that *except* multiple inheritance, **plus** shared state, constructors, and the ability to be extended further by an *unsealed* subclass. The two things `sealed` cannot replace: (1) **state** — a sealed interface cannot hold a common counter or a shared buffer; (2) **the openness of the hierarchy** — a `sealed` type's permitted set is fixed at compile time, so a third-party library cannot add a new implementation (which is sometimes exactly what you want, and sometimes a limitation). The balanced conclusion: **`sealed` removed the *main* historical reason for abstract classes (the closed-set guarantee), leaving only "state and constructors"** — and that is a much clearer question to ask. And the practice that follows: for a domain model with a known set of cases, prefer a `sealed` interface with records; for a framework base class with shared state, use an abstract class.

### CQ4. "You said the `default` method conflict rules. But what if a class inherits a `default` method from two unrelated interfaces — which one wins?"

- **Naive:** "Neither — it is a compile error unless you override."  ← *correct, and it misses the class-inheritance part*
- **The trap:** The trap is to give only the interface-vs-interface rule and forget that a **class in the hierarchy** can resolve the conflict *without* any override. That is the subtle case an interviewer is testing.
- **Expert answer:** The full rule set, in the order the compiler applies it. (1) If a **class** in the hierarchy provides a concrete implementation of the method — whether declared in the class itself or inherited from a superclass — that wins over any interface `default`. The compiler prefers the "most specific" source, and a class always beats an interface. (2) Otherwise, if one interface **extends** another, the subinterface's `default` wins. (3) Otherwise, if two **unrelated** interfaces provide a `default`, it is a **compile error** — the class must override it, and may disambiguate with `A.super.m()`. The "most specific wins" principle is really a **partial-order** comparison on the type hierarchy, and an interface is always *less* specific than any class that implements it. The practical advice: **do not rely on the resolution rules** — if two of your interfaces provide the same `default`, override it explicitly, because the resolution is a compile-time accident of the hierarchy, not a statement of intent.

### CQ5. "If `default` methods let you evolve an interface, is publishing an interface still risky?"

- **Naive:** "No, it's safe now."  ← *overclaims*
- **The trap:** The trap is to think `default` methods remove *all* the risk. They remove the **source and binary compatibility** risk of adding a method — but there are two remaining hazards that a senior engineer should name.
- **Expert answer:** The two remaining hazards. (1) **The `default` body's semantics must be correct for every implementor.** A `default` that assumes the implementor is mutable will break an immutable one; a `default` that assumes a particular `size()` semantics will break a lazy or unbounded one. This is a *contract* obligation that the compiler does not check. (2) **A `default` method that calls an overridable method can be invoked from a subclass constructor**, so it can observe a **partially initialised** object. This is the same hazard as calling an overridable method from a constructor — and `default` methods inherit it. The residual rule: **a `default` method should be a *complete* implementation that does not depend on the implementor's internal state beyond its contract** — or it should be `private` and called only from other `default` methods. And the honest summary: `default` methods made interface evolution *possible*; they did not make it *free*.

### CQ6. "What about a generic interface versus a generic abstract class — does the choice change anything?"

- **Naive:** "No, the same rules apply."  ← *true, and it misses a real subtlety*
- **The trap:** The trap is that generic *interfaces* and generic *abstract classes* behave differently in one respect that bites: **a generic interface can be implemented by a generic class with a different type parameter** (the implementation is *generic* in a way the interface did not require), and the **bridge methods** that erasure forces can surprise you.
- **Expert answer:** The subtlety: `interface Box<T> { void put(T value); }` can be implemented by `class StringBox implements Box<String>`. But it can *also* be implemented by `class BoxImpl<T> implements Box<T>` — and now `BoxImpl` is generic in a way `Box` was not. With **erasure**, the compiler generates bridge methods to preserve polymorphism (`put(Object)` → `put(String)`), and those bridges are a common source of "why is this method called twice?" confusion. The practical differences that do matter: (1) an interface can be implemented by a class that **already extends another class**; (2) an abstract class can carry **state** and a **type parameter bound with an abstract method**; (3) a generic abstract class can have a **constructor** that uses its type parameter (e.g. `Box(T initial)`), which an interface cannot. The design conclusion is the same as the non-generic case, and the rule of thumb holds: **generic interfaces for capabilities, generic abstract classes for base implementations with state**.

### CQ7. "Is a marker interface really obsolete? Why not always use an annotation?"

- **Naive:** "Annotations are better because they carry data."  ← *partly true, and it misses the type-safety point*
- **The trap:** The trap is to treat this as a style question. The real difference is **compile-time type safety**: `instanceof` on a marker interface is a **type check the compiler understands** — it narrows your type, it participates in overload resolution, and it is checked by the compiler. An annotation is **metadata** that the compiler ignores.
- **Expert answer:** The precise distinction. `obj instanceof Serializable` — the compiler **knows** that `obj` is a `Serializable` afterwards, and the check is *static* (a cast would be checked). `obj instanceof @Auditable` — **not legal**; you write `obj.getClass().isAnnotationPresent(Auditable.class)`, which is a **runtime** check, carries no type information, and cannot be used to narrow a type. The consequences: (1) an annotation on a class does **not** make the class implement any interface, so you cannot pass it to a method that requires the capability; (2) annotations are **not inherited** the way interfaces are — `@Auditable` on a base class is not visible on a subclass instance unless you check the hierarchy explicitly; (3) annotations are **not** checked by the compiler, so a typo in the annotation name is a runtime failure. So the rule: **use a marker interface when the property is a *type-level capability* that the compiler should enforce; use an annotation when it is *metadata* that some framework will read at runtime** (JPA's `@Entity`, Jackson's `@JsonProperty`, Spring's `@Transactional`).

### CQ8. "If `sealed` gives exhaustiveness, why not make EVERY interface sealed?"

- **Naive:** "Because you might want to add implementations later."  ← *correct, and it misses the main reason*
- **The trap:** The trap is to think the only cost is extensibility. The real cost is **coupling**: a `sealed` interface is a **commitment** — every new implementation requires editing the `permits` clause, which is a **source change to a published type** and therefore a **compatibility event** for every consumer of that hierarchy.
- **Expert answer:** The layered reasoning. (1) **Extensibility** — a `sealed` type says "this set is closed *for now*", and opening it later requires editing the type, which breaks source compatibility for anyone who compiled against the old `permits` list (they would have an exhaustive `switch` that no longer compiles). That is a *real* cost — it is the same reason enums are hard to extend. (2) **Coupling** — the `permits` clause is a *central* list; every new implementation must be registered there, so the "owner" of the sealed type must be consulted. For a framework's extension point, that is fatal. (3) **Practical guidance** — seal a **domain** type that you own and that has a genuinely closed set of cases (payment methods, order states, event types); do **not** seal a **framework SPI** that third parties are meant to implement. And the reason `sealed` exists at all: the exhaustiveness guarantee is worth a lot — it is what turns "you forgot a case" from a production bug into a compile error — so the trade is worth making in the closed-set case.

### CQ9. "How do you decide between `default` methods and an abstract class for shared code?"

- **Naive:** "Use `default` if there is no state."  ← *correct, and it is a rule, not a judgement*
- **The trap:** The trap is to think this is purely a mechanical question. The deeper issue is **who owns the shared code and who may change it**. A `default` method's body is part of the *interface's* contract and is inherited by every implementor — including third-party ones you do not control. An abstract class's concrete methods are part of a *base class* that only the subclasses inherit, and you control the whole hierarchy.
- **Expert answer:** The decision, with the ownership question at the centre. (1) **Does the shared code need state?** → abstract class, no further thought. (2) **Is the shared code a *complete* implementation that needs no state?** → a `default` method. (3) **Is the shared code a *template* (a fixed algorithm with overridable steps)?** → an abstract class, because the template method pattern is *about* the class controlling the flow — and a `default` method that calls an overridable hook has the partially-initialised-object hazard. (4) **Will third parties implement this type?** → prefer a `default` method over an abstract class, because an abstract class forces them to extend *your* base. (5) **Is the shared code likely to evolve?** → an abstract class, because you can change the base class's internals without touching the subclasses; a `default` method's body is *contract* — changing it changes the behaviour of every implementor, including ones you do not know about. That last point is the one most candidates miss: **a `default` method is a promise to every implementor, forever.**

### CQ10. "What is the actual cost of choosing an interface over an abstract class?"

- **Naive:** "An extra file and an indirection."  ← *true, but it misses the biggest cost*
- **The trap:** The trap is to understate it. The file and the indirection are the *visible* costs. The **invisible** costs are bigger: a **testability** cost, a **coupling** cost, and a **cognitive** cost.
- **Expert answer:** The three hidden costs. (1) **Testability** — an interface with a single implementation means your unit test must either use a mocking framework (with all its ceremony and its coupling to the production API) or write a fake for a type that has only one possible behaviour. A concrete class needs neither. (2) **Coupling** — an interface is a *published contract*. Once a caller depends on `UserRepository`, changing the class's method signature is a breaking change to every caller, even if the interface's own shape did not change. With a concrete class, the same change is an internal refactor. (3) **Cognitive** — a developer reading `OrderService` must jump to `OrderRepository` to learn what it does; with a concrete class the type is right there. And the counter-cost of the abstract class: it creates a **taxonomy** where there may be none, which forecloses the implementation you have not thought of yet, and it can only be extended along one line. The balanced conclusion: **the interface costs more than a file, and buys you substitutability — so pay it where substitutability is real, and not elsewhere.**

---

## 10. Rapid-Fire MCQs

**Q1.** What is the *only* feature an interface cannot have that an abstract class can?
- A) Methods  B) **Instance state**  C) Constants  D) Static methods

**Q2.** Interface fields are implicitly…
- A) `private final`  B) `protected`  C) **`public static final`**  D) `public final`

**Q3.** An interface's methods without a body are implicitly…
- A) `private`  B) **`public abstract`**  C) `protected`  D) `public`

**Q4.** An abstract class can be `final`?
- A) Yes  B) **No**  C) Only if it has no abstract methods  D) Only in Java 17+

**Q5.** `default` methods were introduced in…
- A) Java 5  B) Java 7  C) **Java 8**  D) Java 9

**Q6.** `private` interface methods were introduced in…
- A) Java 8  B) **Java 9**  C) Java 11  D) Java 17

**Q7.** `sealed` interfaces were finalised in…
- A) Java 11  B) Java 16  C) **Java 17**  D) Java 21

**Q8.** Two unrelated interfaces with the same `default` method cause…
- A) A runtime error  B) **A compile error unless the class overrides**  C) The leftmost wins  D) The `Object` method is used

**Q9.** In a conflict, a **class** method beats an interface `default` method?
- A) Yes  B) No  C) Only if the class is `final`  D) Only in fair mode

**Q10.** A subinterface's `default` method beats its superinterface's?
- A) Yes  B) No  C) Only with `sealed`  D) Only if the subinterface is `final`

**Q11.** `static` methods in an interface are inherited by implementors?
- A) Yes  B) **No**  C) Only if `public`  D) Only in Java 8

**Q12.** A marker interface is one with…
- A) One abstract method  B) **No methods**  C) Only `default` methods  D) Only constants

**Q13.** Can a functional interface be implemented by a lambda?
- A) Yes  B) No  C) Only if it is `sealed`  D) Only if it has one method

**Q14.** A switch over a `sealed` type needs a `default` branch?
- A) Yes  B) **No — the compiler checks exhaustiveness**  C) Only if the type is open  D) Only in Java 17

**Q15.** The `Stack extends ArrayList` problem is an example of a violation of…
- A) Encapsulation  B) **The Liskov Substitution Principle**  C) The Interface Segregation Principle  D) Dependency Inversion

**Q16.** A `sealed` interface can hold instance state?
- A) Yes  B) **No**  C) Only via `default` methods  D) Only if it extends a class

**Q17.** An interface can have a constructor?
- A) Yes  B) **No**  C) Only a no-arg one  D) Only a private one

**Q18.** A generic interface can be implemented by a class that is itself generic?
- A) No  B) Yes  C) Only with `sealed`  D) Only with a raw type

**Q19.** The best rule of thumb is: use an interface when…
- A) You need shared code  B) **You express a capability or need multiple inheritance of type**  C) You need a constructor  D) You need state

**Q20.** The `permits` clause of a `sealed` type is a…
- A) Runtime annotation  B) **Compile-time declaration of the permitted subtypes**  C) A Javadoc tag  D) A naming convention

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | Instance state — plus the constructor. Everything else an interface can now do. |
| 2 | **C** | Interface fields are implicitly `public static final` constants. |
| 3 | **B** | Public and abstract implicitly. |
| 4 | **B** | An abstract class must be extendable, so it cannot be `final`. |
| 5 | **C** | Java 8 — along with lambdas and streams. |
| 6 | **B** | Java 9 — so interfaces can have private helper methods. |
| 7 | **C** | Java 17 (preview in 15 and 16). |
| 8 | **B** | The class must override and disambiguate with `A.super.m()`. |
| 9 | **A** | A class method is always more specific than an interface's. |
| 10 | **A** | The subinterface's `default` wins — the most specific one. |
| 11 | **B** | Static interface methods are not inherited; call `I.m()`. |
| 12 | **B** | No methods at all — it is a pure tag for `instanceof`. |
| 13 | **A** | A lambda implements a functional interface's single abstract method. |
| 14 | **B** | The compiler proves the switch covers every permitted subtype. |
| 15 | **B** | `Stack` inherits `add(int, E)`, which breaks the `List` contract. |
| 16 | **B** | Interfaces have no instance state; a sealed interface is still state-free. |
| 17 | **B** | There is no per-instance state for a constructor to initialise. |
| 18 | **B** | `class Impl<T> implements Box<T>` is legal — and erasure adds bridge methods. |
| 19 | **B** | A capability, or a need for multiple inheritance of type. |
| 20 | **B** | A compile-time declaration — the exhaustiveness guarantee comes from it. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    INTERFACE vs ABSTRACT CLASS                          │
├──────────────────────┬───────────────────────┬──────────────────────────┤
│                      │  INTERFACE           │  ABSTRACT CLASS          │
├──────────────────────┼───────────────────────┼──────────────────────────┤
│ Abstract methods      │        ✅            │          ✅              │
│ Shared code           │ ✅ (default, J8+)    │          ✅              │
│ Static helpers        │ ✅ (J8+)             │          ✅              │
│ Private helpers       │ ✅ (J9+)             │          ✅              │
│ INSTANCE STATE        │        ❌            │          ✅              │
│ CONSTRUCTOR           │        ❌            │          ✅              │
│ Multiple inheritance  │        ✅            │          ❌              │
│ Sealed (closed set)   │ ✅ (J17+)            │          ✅              │
│ Can be final          │        ✅            │          ❌              │
├──────────────────────┴───────────────────────┴──────────────────────────┤
│                                                                          │
│  THE DECISION:                                                            │
│    1. Closed & known set?   → SEALED INTERFACE (Java 17+)               │
│    2. Needs shared state?   → ABSTRACT CLASS                            │
│    3. Must extend another?  → INTERFACE                                 │
│    4. Capability ("can do") → INTERFACE                                 │
│    5. Taxonomy ("is a")     → ABSTRACT CLASS (or a sealed interface)     │
│                                                                          │
│  THE JDK'S OWN CHOICES:                                                  │
│    Capabilities  → List, Set, Map, Runnable, Comparator, CharSequence   │
│    Shared state  → AbstractList, AbstractMap, InputStream, Reader      │
│    A taxonomy    → Thread (it literally IS a thread)                    │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE DEFAULT-METHOD CONFLICT RULES                    │
│  ① CLASS beats INTERFACE         → no conflict, the class wins        │
│  ② SUBINTERFACE beats SUPER      → the most specific wins             │
│  ③ TWO UNRELATED INTERFACES      → COMPILE ERROR; override and use    │
│                                     A.super.m() to disambiguate       │
│                                                                          │
│  ⚠ A `default` method is a PROMISE to every implementor, forever.       │
│    Its body must be correct for ALL of them.                            │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The selection checklist

```text
  ☐ Is the set of implementations CLOSED and known?  → SEALED INTERFACE
  ☐ Do the subtypes share MUTABLE STATE?            → ABSTRACT CLASS
  ☐ Do the subtypes need a CONSTRUCTOR?             → ABSTRACT CLASS
  ☐ Is the shared code a FIXED ALGORITHM?           → ABSTRACT CLASS (template)
  ☐ Must the type also EXTEND another class?       → INTERFACE
  ☐ Will THIRD PARTIES implement it?               → INTERFACE
  ☐ Is it a CAPABILITY ("can do X")?                → INTERFACE
  ☐ Is there only ONE implementation and no fake?   → the CLASS, not an interface
```

### The Spring/framework evidence

| Framework type | Choice | Why |
|---|---|---|
| `ApplicationContext` | **Interface** | Multiple implementations (a capability the app depends on) |
| `AbstractApplicationContext` | **Abstract class** | Shared state and the `refresh()` template |
| `JdbcTemplate` | **Class** | A concrete, usable template — you use it, you do not implement it |
| `TransactionTemplate` | **Class** | The same |
| `HandlerAdapter` | **Interface** | The framework adapts *your* handler — an SPI |
| `Comparator` | **Interface** | Multiple orderings; a lambda can implement it |
| `@Transactional` | **Annotation** | Metadata read by a proxy at runtime — not a type-level capability |

> 💡 **The `HandlerAdapter` case is instructive.** Spring *needs* third parties to
> implement it, so it must be an **open interface**. It cannot be an abstract class,
> because the user's handler would then have to extend Spring's class. That is the
> "will third parties implement it?" rule in action.

### The publishing checklist for an interface

```text
  ☐ Every new method has a `default` body that is CORRECT for all implementors
  ☐ No `default` method relies on an implementor's internal state
  ☐ No `default` method calls an overridable method (the partially-initialised hazard)
  ☐ The `default` bodies are documented as part of the contract
  ☐ If the change is not safely expressible as a `default`, use a NEW interface
    (interface V2 extends V) rather than breaking V
```

### Common code-review comments

| Smell | Review comment |
|---|---|
| An interface with one implementation | "What is the second implementation? If there is none, drop the interface." |
| A `default` method that casts the implementor | "This couples the interface to a specific implementation." |
| An abstract class with no state and one subclass | "Is this a taxonomy? If not, use composition." |
| An interface with 30 methods | "Segregate it — clients should not depend on methods they do not use." |
| A `default` method that mutates | "A `default` must not assume the implementor is mutable." |

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| Encapsulation vs abstraction (the sibling question) | [03 · Encapsulation vs Abstraction](03-OOP-Encapsulation-vs-Abstraction.md) |
| Inheritance and the diamond problem | [04 · Inheritance Types & the Diamond Problem](04-OOP-Inheritance-Types-and-Diamond-Problem.md) |
| Polymorphism and dispatch | [05 · Polymorphism Deep Dive](05-OOP-Polymorphism-Deep-Dive.md) |
| Sealed types, records, pattern matching | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| The Strategy pattern (a capability in the wild) | [09 · Design Patterns](../09-Design-Patterns/01-Design-Patterns.md) |
| SOLID and the dependency direction | [10 · SOLID Principles](../10-SOLID-Principles/01-SOLID-Principles.md) |

---

**Next Chapter:** → [07 · OOP Keywords: final, static, volatile, transient](../01-Core-Java-OOP/07-OOP-Keywords-final-static-volatile-transient.md)
**Previous Chapter:** ← [05 · Polymorphism Deep Dive](05-OOP-Polymorphism-Deep-Dive.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
