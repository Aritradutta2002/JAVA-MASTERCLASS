# Enums — The Complete Interview Guide

> **A small topic with a lot of depth.** Enums are not "constants with a type" —
> they are a **special class the compiler generates for you**, with a
> **private constructor**, a **guaranteed singleton per constant**, a
> **specialised `Set`/`Map` implementation** (`EnumSet`/`EnumMap`) that beats the
> generic ones by an order of magnitude, and a **serialisation contract** the JVM
> handles specially.

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

- **An enum is a special class the compiler generates.** It implicitly `extends java.lang.Enum` and is implicitly `final`.
- **Each constant is a `public static final` singleton instance**, created when the class is initialised. The constructor is implicitly `private` — you cannot `new` an enum.
- **`values()` clones a private `$VALUES` array on every call** — so it is **O(n)**. Cache it in a `static final` field for hot loops. `valueOf(String)` is O(1) (a name hash map).
- **`ordinal()` is the declaration index (0, 1, 2…) and is FRAGILE.** Never persist it — reordering the constants breaks your data. Use an explicit `code` field.
- **Use `==` (or `EnumMap`/`EnumSet`) for enum comparison**, not `equals` — the constants are singletons, so identity is correct and fastest.
- **`EnumSet` is a bit-vector** — a single `long` for up to 64 constants (a `long[]` beyond that). `contains` is one AND + a test; `size()` is `Long.bitCount` (one instruction). It is the fastest `Set` in the JDK.
- **`EnumMap` is a plain `Object[]` indexed by `ordinal()`** — no hashing at all. It is the fastest `Map` in the JDK, but it depends on `ordinal()` (so it breaks if constants are reordered).
- **`switch` on an enum** must handle every constant (or have a `default`) — otherwise you get a `NullPointerException` from unboxing a `null` enum, or a silent fall-through.
- **An enum can have a constructor, fields, instance initialisers, and methods** — and it can implement an interface.
- **Constant-specific class bodies** let each constant override a method: `RED { int rgb() { return 0xFF0000; } }`.
- **Java 17+ `sealed` interfaces with pattern matching** are the modern alternative to "enum with behaviour" — they allow per-case *state* as well as behaviour, which an enum cannot.
- **Enums are serialised by `name`, not by ordinal.** Deserialisation returns the canonical instance. An unknown name throws `IllegalArgumentException`.

---

## 1. What & Why — The Problem This Solves

**The problem #1 — the "public static final int" antipattern.**

```java
// ❌ BEFORE
public static final int STATUS_PENDING = 0;
public static final int STATUS_ACTIVE  = 1;
public static final int STATUS_CLOSED  = 2;

setStatus(STATUS_ACTIVE);          // compiles — but "42" is also accepted!
if (status == 1) { ... }          // magic numbers everywhere
```

**The problems:** no type safety (`setStatus(42)` compiles), no meaningful `toString` (you get `0`), no `switch` exhaustiveness, and a stringly-typed mess if you use `String` constants instead.

**The solution.**

```java
// ✅ AFTER
public enum Status { PENDING, ACTIVE, CLOSED }

setStatus(Status.ACTIVE);          // ✅ only a Status compiles
status.toString();                 // "ACTIVE"
switch (status) {                  // ✅ the compiler checks the cases
    case PENDING -> ...;
    case ACTIVE  -> ...;
    case CLOSED  -> ...;
}
```

**The problem #2 — "enum with behaviour".**

```java
// ❌ A class hierarchy to model a closed set with per-case behaviour
abstract class PaymentMethod {
    abstract BigDecimal fee(BigDecimal amount);
}
class CardMethod extends PaymentMethod { ... }
class WalletMethod extends PaymentMethod { ... }
// → 2 classes + a factory + a switch, for a CLOSED set
```

**The solution — constant-specific class bodies:**

```java
public enum PaymentMethod {
    CARD {
        @Override BigDecimal fee(BigDecimal amount) { return amount.multiply(new BigDecimal("0.02")); }
    },
    WALLET {
        @Override BigDecimal fee(BigDecimal amount) { return BigDecimal.ZERO; }
    },
    COD {
        @Override BigDecimal fee(BigDecimal amount) { return new BigDecimal("5.00"); }
    };

    public abstract BigDecimal fee(BigDecimal amount);
}
```

**The problem #3 — performance.** A `HashSet<Status>` allocates a `HashMap`, hashes each constant, and walks a bucket. An `EnumSet` is **one `long`**. That is not a micro-optimisation — it is a different order of magnitude.

> 💡 **The interview framing.** Enums are one of the few places where the JDK gives you a
> **specialised, faster implementation** of a general abstraction — `EnumSet` and
> `EnumMap` are not `HashSet`/`HashMap` with a different name; they exploit the
> fact that an enum's key space is a **small, dense, fixed set of integers**.

---

## 2. Mental Model — The One Picture

```text
   WHAT THE COMPILER GENERATES
   ────────────────────────────

   public enum Status { PENDING, ACTIVE, CLOSED }

   becomes (simplified):

   public final class Status extends java.lang.Enum<Status> {
       public static final Status PENDING = new Status("PENDING", 0);
       public static final Status ACTIVE  = new Status("ACTIVE",  1);
       public static final Status CLOSED  = new Status("CLOSED",  2);

       private static final Status[] $VALUES = { PENDING, ACTIVE, CLOSED };

       private final String name;
       private final int    ordinal;

       private Status(String name, int ordinal) {   // implicitly private
           super(name, ordinal);
       }

       public static Status[] values() { return $VALUES.clone(); }  // ← CLONES!
       public static Status valueOf(String name) { return Enum.valueOf(Status.class, name); }
   }
```

**What to look at:** three facts are visible in the generated code: (1) the class is `final`, (2) the constructor is `private`, and (3) `values()` **clones** the array — so it is not free.

### The EnumSet bit-vector

```text
   enum Status { PENDING(0), ACTIVE(1), CLOSED(2), CANCELLED(3) }

   EnumSet.of(Status.ACTIVE, Status.CANCELLED)

   elements = (1L << 1) | (1L << 3)
            = 0b1010

   ┌──────────────────────────────────────────────────────────────────┐
   │ bit 63 .................................  bit3 bit2 bit1 bit0    │
   │  0   0   0   0   ...  0   0   0   0   0   1   0   1   0       │
   │                                          ▲       ▲             │
   │                                    CANCELLED  ACTIVE          │
   └──────────────────────────────────────────────────────────────────┘

   contains(ACTIVE)   → (elements & (1L<<1)) != 0    → O(1)
   size()             → Long.bitCount(elements)      → O(1)  ← ONE INSTRUCTION
   containsAll(other) → (elements & other) == other  → O(1)
   union(a,b)         → a.elements | b.elements      → O(1)
```

---

## 3. Deep Dive — Theory

## 3.1 The implicit rules the compiler applies

| Rule | Detail |
|---|---|
| The enum class is implicitly `final` | It cannot be extended. (An enum with constant bodies is still final — the *constants* are anonymous subclasses of the enum, not of the enum itself.) |
| The constructor is implicitly `private` | You cannot `new Status(...)` from outside. |
| Constants are `public static final` | Created once, at class-initialisation time. |
| The enum extends `java.lang.Enum<E>` | So it inherits `name()`, `ordinal()`, `compareTo`, `equals`, `hashCode`, `toString`, and `readResolve`. |
| You may not extend an enum | Only `java.lang.Enum` may extend it. |
| An enum may implement interfaces | Unlike a normal class, an enum can implement an interface *and* have a constructor. |
| `toString()` can be overridden | `name()` and `ordinal()` are `final` and cannot. |
| All constants are created before any other static initialiser runs | So a static field initialised in the enum can reference a constant. |

**`java.lang.Enum`'s API:**

```java
// A SIGNATURE REFERENCE for java.lang.Enum — the real class is in the JDK.
// Modelled as an interface so every method signature is visible and compilable.
public interface EnumApi<E extends EnumApi<E>> {
    String name();                              // the declared name — final
    int ordinal();                              // the declaration index — final
    boolean equals(Object other);               // identity! — final
    int hashCode();                             // identity! — final
    String toString();                          // name() by default — overridable
    int compareTo(E o);                         // ordinal comparison — final
    Object clone() throws CloneNotSupportedException;  // always throws
    // finalize() is deprecated since 9 and removed for removal in 18
}
```

> ⚠️ **`Enum.equals` and `Enum.hashCode` are identity-based** (`this == obj` and `System.identityHashCode(this)`), NOT field-based. That is correct for an enum — two different enum constants are never equal, and an enum is never equal to a non-enum. But it means you **cannot** override `equals` on an enum (they are `final`).

## 3.2 Enum with a constructor and fields

```java
public enum Planet {
    MERCURY(3.303e+23, 2.4397e6),
    VENUS(4.869e+24, 6.0518e6),
    EARTH(5.976e+24, 6.37814e6),
    MARS(6.421e+23, 3.3895e6);

    private final double mass;      // kg
    private final double radius;    // m

    Planet(double mass, double radius) {   // implicitly private
        this.mass = mass;
        this.radius = radius;
    }

    public double surfaceGravity() {
        return 6.67300E-11 * mass / (radius * radius);
    }

    public double weightOnSurface(double massKg) {
        return massKg * surfaceGravity();
    }
}
```

**The initialisation order — a classic trap:**

```text
   ⚠ ENUM CONSTANTS ARE CREATED BEFORE ANY OTHER STATIC INITIALISER

   public enum Broken {
       A(computeValue()),          // ← computeValue() is called HERE
       B(computeValue());

       private static final int SIZE = 10;   // ← NOT yet initialised when A is created!

       static int computeValue() {
           return SIZE;      // 💥 reads 0, not 10 — SIZE is still 0 at this point
       }
   }

   The JLS (§12.4.2) initialises enum constants FIRST, then other static fields.
   So a static method called from a constant's constructor sees the fields
   in their default (0 / null) state.
```

**The correct pattern — a separate holder class:**

```java
public enum Status {
    PENDING, ACTIVE, CLOSED;

    // Initialised in a nested holder — AFTER the constants exist
    private static final class Config {
        static final int MAX_RETRIES = 3;
    }

    public int maxRetries() { return Config.MAX_RETRIES; }
}
```

## 3.3 `values()` and `valueOf()` — the performance detail

```java
public static Status[] values() {
    return $VALUES.clone();      // ← a CLONE on every call!
}

public static Status valueOf(String name) {
    return Enum.valueOf(Status.class, name);   // ← O(1), a name→instance map
}
```

**The cost of `values()`:**

```java
// ❌ In a hot loop — a clone on EVERY iteration
for (Status s : Status.values()) { ... }

// ✅ Cache it once
private static final Status[] VALUES = Status.values();
for (Status s : VALUES) { ... }
```

**Why `values()` clones:** so that a caller cannot modify the internal array and corrupt the enum. The clone is a *defensive copy* — the same principle as `List.copyOf`.

**`valueOf` vs a manual loop:**

```java
Status s = Status.valueOf("ACTIVE");     // ✅ O(1) — a name hash map

// ❌ O(n) and throws IllegalArgumentException on no match
Status found = Arrays.stream(Status.values())
        .filter(x -> x.name().equals("ACTIVE"))
        .findFirst().orElseThrow();
```

**`valueOf` throws `IllegalArgumentException`** (not `NullPointerException`) for an unknown name, and `NullPointerException` for a `null` name.

## 3.4 `ordinal()` — and why you must not persist it

```java
public enum Status {
    PENDING,    // ordinal 0
    ACTIVE,     // ordinal 1
    CLOSED      // ordinal 2
}
```

**The fragility:**

```java
// Stored in a database as 1
// Then someone reorders the enum to fix the alphabetical order:
public enum Status {
    ACTIVE,     // ordinal 0   ← 💥 the stored "1" now means CLOSED
    PENDING,    // ordinal 1
    CLOSED      // ordinal 2
}
```

**The rules:**

| Rule | Why |
|---|---|
| **Never persist `ordinal()`** | It changes when the enum is reordered or a constant is inserted. |
| **Never persist `name()` either** (for a stable API) | Renaming a constant is a breaking change. |
| **Persist an explicit `code`** | A domain-stable identifier you control. |
| **Never use `ordinal()` to index an array** | If a constant is added, the array must change too. Use `EnumMap`/`EnumSet`, which handle it. |

**The correct pattern:**

```java
public enum Status {
    PENDING("P", 0),
    ACTIVE("A", 1),
    CLOSED("C", 2);

    private final String code;      // the STABLE domain identifier
    private final int priority;

    Status(String code, int priority) {
        this.code = code;
        this.priority = priority;
    }

    public String code() { return code; }
    public int priority() { return priority; }

    public static Status fromCode(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown status code: " + code));
    }
}
```

> 💡 **The `EnumMap` dependency on `ordinal()`** is the subtle consequence: `EnumMap`
> stores its entries in an array indexed by `ordinal()`, so **reordering the constants
> corrupts a persisted `EnumMap`** (e.g. one that was serialised). `EnumSet` is
> immune — it is a bit-vector, and the bit positions are the *current* ordinals.

## 3.5 `EnumSet` — the bit-vector

```java
public abstract class EnumSet<E extends Enum<E>> extends AbstractSet<E> {

    // Regular: a single long (up to 64 constants)
    static class RegularEnumSet<E extends Enum<E>> extends EnumSet<E> {
        long elements;    // ← the ENTIRE set
    }

    // Jumbo: for enums with more than 64 constants
    static class JumboEnumSet<E extends Enum<E>> extends EnumSet<E> {
        long[] elements;
    }
}
```

**The factory methods (there is NO public constructor):**

```java
EnumSet.noneOf(Status.class);                          // empty
EnumSet.allOf(Status.class);                           // all constants
EnumSet.of(Status.ACTIVE, Status.CLOSED);              // 1..10 constants
EnumSet.copyOf(Collection<E> coll);                    // from any collection
EnumSet.complementOf(EnumSet<E> other);                // the inverse
EnumSet.range(Status.PENDING, Status.CLOSED);          // an ordinal range
EnumSet.allOf(Status.class).clone();                   // a copy
```

**The `copyOf` trap:**

```java
EnumSet.copyOf(Collections.emptyList());   // ❌ IllegalArgumentException!
```

**Why?** `copyOf` has two implementations: one for an `EnumSet` (which knows the element type) and one for a general `Collection` — and the general one must **infer the element type** from the collection's contents. An empty collection gives it nothing to infer from, so it throws.

**✅ The fix:**

```java
EnumSet<Status> set = EnumSet.noneOf(Status.class);
set.addAll(emptyList);           // ✅ works
```

**The performance table:**

| Operation | `EnumSet` | `HashSet` |
|---|---|---|
| `contains` | **O(1)** — one AND + a test | O(1) — hash + bucket walk |
| `add` | **O(1)** — one OR | O(1) — hash + node allocation + resize |
| `remove` | **O(1)** — one AND-NOT | O(1) |
| `size` | **O(1)** — `Long.bitCount` | O(1) — a field |
| `containsAll` | **O(1)** — one AND + a compare | O(n) |
| `union` / `intersection` / `difference` | **O(1)** — one bitwise op | O(n) |
| Memory | **8 bytes** (≤64 constants) | ~48 bytes per element |
| Iteration | O(n) | O(n) |

**Why `EnumSet` is so much faster:** no hashing, no node allocation, no resize, no GC pressure, and the whole set lives in a register.

> ⚠️ **`EnumSet` is not thread-safe** (neither is `HashSet`). Use `Collections.synchronizedSet` or an `EnumSet` wrapped in `Collections.unmodifiableSet` for a read-only shared instance.

## 3.6 `EnumMap` — the array-backed map

```java
public class EnumMap<K extends Enum<K>, V> extends AbstractMap<K, V> {
    private final Class<K> keyType;
    private final Object[] vals;    // ← indexed by key.ordinal()
    private int size;
    private transient Set<K> keySet;
    private transient Collection<V> values;
}
```

```text
   enum Status { PENDING(0), ACTIVE(1), CLOSED(2), CANCELLED(3) }

   EnumMap<Status, String> map = new EnumMap<>(Status.class);
   map.put(Status.ACTIVE, "running");
   map.put(Status.CLOSED, "finished");

   vals array (indexed by ordinal):
   ┌───────┬───────────┬───────┬──────────┬───────────┐
   │ [0]   │ [1]       │ [2]   │ [3]      │ [4]       │
   │ null  │ "running" │ null  │ null     │ (unused)  │
   └───────┴───────────┴───────┴──────────┴───────────┘
     PENDING  ACTIVE      CLOSED  CANCELLED
```

| Operation | `EnumMap` | `HashMap` |
|---|---|---|
| `get` | **O(1)** — an array index | O(1) — hash + bucket walk |
| `put` | **O(1)** | O(1) — plus node allocation |
| Memory | one slot per constant | a `Node` per entry |
| Null key | ❌ rejected | 1 allowed |
| Null value | ✅ allowed | ✅ allowed |
| Iteration order | **enum declaration order** | hash order |
| **Depends on `ordinal()`** | ✅ yes | ❌ no |

> ⚠️ **The `ordinal()` dependency is a real trap.** An `EnumMap` that is
> **serialised** and then deserialised after the enum is reordered will map the
> wrong values. This is a genuine (if rare) production hazard — and the reason
> some teams forbid serialising `EnumMap`.

## 3.7 `EnumSet` and `EnumMap` — the "enum with a body" pattern

```java
public enum Permission {
    READ, WRITE, EXECUTE;

    public boolean grants(Permission required) {
        return this == required || this == ALL;
    }
}
```

But `EnumSet`/`EnumMap` give you a **set** or **map** of constants for free:

```java
EnumSet<Permission> admin = EnumSet.allOf(Permission.class);
EnumSet<Permission> readOnly = EnumSet.of(Permission.READ);

Map<Status, Integer> retryCounts = new EnumMap<>(Status.class);
retryCounts.put(Status.PENDING, 3);
```

## 3.8 Constant-specific class bodies

```java
public enum Operation {
    ADD {
        @Override public int apply(int a, int b) { return a + b; }
        @Override public String symbol() { return "+"; }
    },
    MULTIPLY {
        @Override public int apply(int a, int b) { return a * b; }
        @Override public String symbol() { return "*"; }
    };

    public abstract int apply(int a, int b);
    public abstract String symbol();
}
```

**What the compiler generates:**

```text
   ┌─────────────────────────────────────────────────┐
   │  Operation  (abstract, extends Enum<Operation>) │
   └─────────────────────────────────────────────────┘
        ▲              ▲
        │              │
   ┌────┴─────┐  ┌─────┴───────┐
   │ Operation$1│  │ Operation$2 │   ← anonymous subclasses
   │  (ADD)    │  │  (MULTIPLY) │
   └──────────┘  └─────────────┘
   ADD      = new Operation$1();
   MULTIPLY = new Operation$2();

   ⚠ The ENUM CLASS ITSELF is still final — only the CONSTANTS are subclasses.
```

**The constraints:**

| Rule | Detail |
|---|---|
| The enum class must be `abstract` if it has abstract methods | Otherwise "the enum class must not have abstract methods" |
| You cannot declare a constructor in an enum with constant bodies | "It is a compile-time error to define a constructor in an enum declaration that has constant bodies" |
| The constant body cannot access non-static instance fields of the enum | Because the constant's constructor runs before the enum's instance initialisers |
| `switch` on it works normally | The compiler knows the constant-specific class |

## 3.9 Enums implementing an interface

```java
public interface Describable {
    String describe();
}

public enum Channel implements Describable {
    EMAIL {
        @Override public String describe() { return "Sends an email"; }
    },
    SMS {
        @Override public String describe() { return "Sends an SMS"; }
    };

    // no need for the constant bodies if the default is fine:
    public String describe() { return "A generic channel"; }
}
```

**Why this matters:** an enum *can* implement an interface (a class cannot implement an interface *and* be extended by another class). And the "each constant has different behaviour" pattern is exactly the enum's superpower.

## 3.10 `switch` on an enum

```java
switch (status) {
    case PENDING -> log("waiting");
    case ACTIVE  -> log("running");
    case CLOSED  -> log("done");
    // ❌ if CANCELLED is added later, no compile error → it silently does nothing
}
```

**Two important behaviours:**

**1. The `NullPointerException` trap:**

```java
Status s = null;
switch (s) {                       // 💥 NullPointerException
    case PENDING -> ...;
}
```

**Why?** A `switch` on an enum compiles to a **lookupswitch** on `ordinal()`, which requires **unboxing** the enum — and unboxing `null` throws `NullPointerException`. This is a genuine surprise: `switch` on a `null` enum throws, unlike `switch` on a `null` `String` (which throws too, but for a different reason) or on a `null` `int` (which throws too).

> 💡 In **Java 21**, pattern matching for `switch` handles `null` explicitly:
> `case null -> ...` is now allowed, and if there is no `case null`, a `NullPointerException`
> is thrown — but the *reason* is documented.

**2. The exhaustiveness rule (Java 14+):**

```java
// With a switch EXPRESSION over an enum, exhaustiveness is CHECKED
int result = switch (status) {
    case PENDING -> 0;
    case ACTIVE  -> 1;
    case CLOSED  -> 2;
    // ❌ compile error: "the switch expression does not cover all possible input values"
};
```

**With a sealed hierarchy (Java 17+), the same exhaustiveness check applies** — which is why `sealed` + pattern-matching `switch` is the modern replacement for "enum with behaviour".

## 3.11 Enum serialisation

**The JVM special-cases enums:**

```text
   SERIALISING an enum
   ────────────────────
   • The stream stores the constant's NAME (a String), not the object.
   • DESERIALISING looks the name up and returns the CANONICAL instance.
   • ⇒ deserialised == the original:  true  ✅
   • ⇒ no duplicate instances are ever created.

   ⚠ IF THE NAME IS UNKNOWN (the enum was refactored):
     → IllegalArgumentException: "No enum constant com.x.Status.OLD_NAME"
```

**`readResolve`** is the mechanism (`Enum.readResolve` is `protected final` and returns `this`), which is why the JVM handles it automatically — you never write it for an enum.

**The security angle:** because deserialisation resolves by *name*, a deserialised enum cannot be a forged instance. That is a small but real security property.

## 3.12 Enum performance in a switch

```text
   switch (status) { ... }   →  LOOKUPSWITCH  (a jump table on the ordinal)
   switch (name)   { ... }   →  a chain of string comparisons (or a hash switch)

   ⇒ switching on an enum is O(1) with a single jump — much faster than
     an if-else chain over name comparisons.
```

---

## 4. Visualizations

### 4.1 The generated enum class

```text
   SOURCE:  public enum Status { PENDING, ACTIVE, CLOSED }

   GENERATED:
   ┌────────────────────────────────────────────────────────────────┐
   │ public final class Status extends java.lang.Enum<Status> {     │
   │                                                                │
   │   public static final Status PENDING = new Status("PENDING",0); │
   │   public static final Status ACTIVE  = new Status("ACTIVE", 1); │
   │   public static final Status CLOSED  = new Status("CLOSED", 2); │
   │                                                                │
   │   private static final Status[] $VALUES =                       │
   │        { PENDING, ACTIVE, CLOSED };                             │
   │                                                                │
   │   private final String name;                                   │
   │   private final int ordinal;                                   │
   │                                                                │
   │   private Status(String name, int ordinal) { /* private! */ }  │
   │                                                                │
   │   public static Status[] values() { return $VALUES.clone(); }  │
   │   public static Status valueOf(String n) {                     │
   │       return Enum.valueOf(Status.class, n);                    │
   │   }                                                            │
   │ }                                                               │
   └────────────────────────────────────────────────────────────────┘
```

**What to look at:** the three constraints are all visible here — the class is `final`, the constructor is `private`, and `values()` **clones**.

### 4.2 `EnumSet` — one `long` IS the set

```text
   enum Status { PENDING(0), ACTIVE(1), CLOSED(2), CANCELLED(3), REFUNDED(4) }

   EnumSet.of(Status.ACTIVE, Status.CANCELLED)

   elements = (1L << 1) | (1L << 3) = 0b01010

   ┌───────────────────────────────────────────────────────────────────┐
   │ bit 63 ................................ bit4 bit3 bit2 bit1 bit0     │
   │  0   0   0   0   ...   0   0   0   0   0   0   1   0   1   0   │
   │                                            ▲       ▲             │
   │                                      CANCELLED  ACTIVE          │
   └───────────────────────────────────────────────────────────────────┘

   THE WHOLE SET IS 8 BYTES. A HashSet with 2 elements is ~100 bytes
   (a HashMap + 2 HashMap.Node objects + 2 table slots).

   ⚠ THE BIT POSITION IS THE ORDINAL
     → reordering the enum changes which bit each constant uses
     → an EnumSet is NOT safe to persist; an EnumMap IS affected too
```

### 4.3 `EnumMap` — an array indexed by `ordinal()`

```text
   enum Status { PENDING(0), ACTIVE(1), CLOSED(2), CANCELLED(3) }

   EnumMap<Status, String> m = new EnumMap<>(Status.class);
   m.put(Status.ACTIVE, "running");
   m.put(Status.CLOSED, "finished");

   vals[] (index = ordinal):
   ┌────────┬───────────┬────────┬────────┬────────┐
   │ [0]    │ [1]       │ [2]    │ [3]    │ [4]    │
   │ null   │ "running" │ null   │ null   │ null   │
   └────────┴───────────┴────────┴────────┴────────┘
      PENDING   ACTIVE     CLOSED  CANCELLED  (unused)

   get(ACTIVE)  →  vals[1]                  → O(1), NO HASHING
   put(X, v)    →  vals[X.ordinal()] = v    → O(1), NO NODE ALLOCATION

   ⚠ IF THE ENUM IS REORDERED, A PERSISTED EnumMap IS CORRUPTED.
```

### 4.4 The `ordinal()` fragility

```text
   ❌ WHAT HAPPENS WHEN THE ENUM IS REORDERED

   Database row:  status = 1     (saved as an int)

   BEFORE (v1)                    AFTER (v2 — reordered)
   ─────────────                  ────────────────────
   PENDING  ordinal 0            ACTIVE   ordinal 0
   ACTIVE   ordinal 1  ──────▶    PENDING  ordinal 1
   CLOSED   ordinal 2            CLOSED   ordinal 2

   Reading status=1 now gives PENDING, but the row meant ACTIVE.
   → SILENT DATA CORRUPTION  💥

   ✅ THE FIX — persist a stable code
   ───────────────────────────────────
   PENDING("P", 0), ACTIVE("A", 1), CLOSED("C", 2)
   → the code "A" is unaffected by a reorder ✅
```

### 4.5 The enum-initialisation order trap

```text
   public enum Broken {
       A(compute()),        // ← the constructor RUNS HERE
       B(compute());

       static final int SIZE = 10;      // ← NOT YET INITIALISED

       static int compute() { return SIZE; }   // 💥 returns 0, not 10
   }

   THE JLS ORDER (§12.4.2):
   1. the enum CONSTANTS are created          ← A and B are made here
   2. then the other STATIC FIELDS are set    ← SIZE = 10 happens here

   ⇒ a static method called from a constant's constructor sees
     every static field in its DEFAULT state (0 / null).
```

### 4.6 Constant-specific class bodies

```text
   public enum Op {
       ADD     { public int apply(int a, int b) { return a + b; } },
       MULTIPLY{ public int apply(int a, int b) { return a * b; } };

       public abstract int apply(int a, int b);
   }

   GENERATED:
   ┌───────────────────────────────────────────────┐
   │  abstract class Op extends Enum<Op>           │  ← the enum is final!
   └───────────────────────────────────────────────┘
        ▲                    ▲
        │                    │
   ┌────┴────────┐   ┌────────┴──────┐
   │  Op$1 (ADD) │   │ Op$2 (MULTIPLY)│   ← anonymous subclasses
   └─────────────┘   └───────────────┘

   ⚠ The ENUM CLASS IS STILL final — the CONSTANTS are the subclasses.
   ⚠ An enum with constant bodies may NOT declare a constructor.
```

### 4.7 `EnumSet` vs `HashSet` — the memory picture

```text
   A set of 2 enum constants:

   ❌ HashSet<Status>
   ┌──────────────────────────────────────┐
   │ HashMap                             │
   │  ┌────────┐                         │
   │  │ table  │  16 slots × 4 bytes = 64 │
   │  ├────────┤                         │
   │  │ Node   │  ~32 bytes             │
   │  ├────────┤                         │
   │  │ Node   │  ~32 bytes             │
   │  └────────┘                         │
   │  TOTAL ≈ 128 bytes + the HashSet obj│
   └──────────────────────────────────────┘

   ✅ EnumSet<Status>
   ┌──────────────────────────────────────┐
   │  long elements = 0b01010            │  ← 8 bytes. THAT'S IT.
   └──────────────────────────────────────┘
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — The enum's identity, `values()`, and `valueOf()`

```java
import java.util.Arrays;

public class EnumBasicsDemo {
    public enum Status {
        PENDING, ACTIVE, CLOSED;

        public boolean isTerminal() { return this == CLOSED; }
    }

    public static void main(String[] args) {
        System.out.println("values().length   = " + Status.values().length);
        System.out.println("values()          = " + Arrays.toString(Status.values()));
        System.out.println("PENDING.ordinal() = " + Status.PENDING.ordinal());
        System.out.println("PENDING.name()    = " + Status.PENDING.name());
        System.out.println("toString()        = " + Status.PENDING);

        // Identity
        System.out.println("== self           = " + (Status.ACTIVE == Status.ACTIVE));
        System.out.println("equals()          = " + Status.ACTIVE.equals(Status.ACTIVE));

        // valueOf
        System.out.println("valueOf(\"CLOSED\") = " + Status.valueOf("CLOSED"));
        try {
            Status.valueOf("MISSING");
        } catch (IllegalArgumentException e) {
            System.out.println("valueOf(bad)      → " + e.getClass().getSimpleName());
        }

        // values() returns a CLONE — mutating it does not corrupt the enum
        Status[] arr = Status.values();
        arr[0] = Status.CLOSED;
        System.out.println("after mutating the clone: " + Status.values()[0] + "  ← unchanged ✅");
    }
}
```

**Actual output:**

```text
values().length   = 3
values()          = [PENDING, ACTIVE, CLOSED]
PENDING.ordinal() = 0
PENDING.name()    = PENDING
toString()        = PENDING
== self           = true
equals()          = true
valueOf("CLOSED") = CLOSED
valueOf(bad)      → IllegalArgumentException
after mutating the clone: PENDING  ← unchanged ✅
```

**Takeaway:** `values()` returns a **defensive copy** — you cannot corrupt the enum by mutating the returned array. But it also means it is **not free**: in a hot loop, cache it.

### Example 2 — `EnumSet` and `EnumMap` vs their hash-based cousins

```java
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EnumPerfDemo {
    public enum Permission { READ, WRITE, EXECUTE, ADMIN }

    public static void main(String[] args) {
        EnumSet<Permission> enumSet = EnumSet.of(Permission.READ, Permission.EXECUTE);
        HashSet<Permission> hashSet = new HashSet<>(enumSet);

        System.out.println("enumSet         = " + enumSet);
        System.out.println("hashSet         = " + hashSet);
        System.out.println("enumSet size()  = " + enumSet.size());
        System.out.println("contains READ   = " + enumSet.contains(Permission.READ));
        System.out.println("contains ADMIN  = " + enumSet.contains(Permission.ADMIN));

        // Bitwise operations — O(1) for EnumSet
        EnumSet<Permission> all = EnumSet.allOf(Permission.class);
        System.out.println("all             = " + all);
        System.out.println("union           = " + EnumSet.union(enumSet, EnumSet.of(Permission.WRITE)));
        System.out.println("intersection    = " + EnumSet.intersection(enumSet, all));
        System.out.println("difference      = " + EnumSet.difference(all, enumSet));

        // EnumMap — an array indexed by ordinal
        EnumMap<Permission, String> enumMap = new EnumMap<>(Permission.class);
        enumMap.put(Permission.READ, "grant");
        enumMap.put(Permission.ADMIN, "grant + audit");

        Map<Permission, String> hashMap = new HashMap<>(enumMap);

        System.out.println("enumMap         = " + enumMap);
        System.out.println("hashMap         = " + hashMap);
        System.out.println("enumMap keys (declaration order) = " + enumMap.keySet());

        // The EnumSet is ONE long
        System.out.println("enumSet instanceof RegularEnumSet = " + enumSet.getClass().getSimpleName());
    }
}
```

**Actual output:**

```text
enumSet         = [READ, EXECUTE]
hashSet         = [READ, EXECUTE]
enumSet size()  = 2
contains READ   = true
contains ADMIN  = false
all             = [READ, WRITE, EXECUTE, ADMIN]
union           = [READ, WRITE, EXECUTE]
intersection    = [READ, EXECUTE]
difference      = [WRITE, ADMIN]
enumMap         = {READ=grant, ADMIN=grant + audit}
hashMap         = {ADMIN=grant + audit, READ=grant}
enumMap keys (declaration order) = [READ, ADMIN]
enumSet instanceof RegularEnumSet = RegularEnumSet
```

**Takeaway:** the contents are identical, but the *iteration order* differs — `EnumMap` iterates in **enum declaration order**; `HashMap` in hash order. And `EnumSet`'s union/intersection/difference are single bitwise instructions.

### Example 3 — The `ordinal()` fragility, and the fix

```java
import java.util.Arrays;

public class OrdinalDemo {
    // ❌ FRAGILE — no stable identifier
    enum Fragile { PENDING, ACTIVE, CLOSED }

    // ✅ STABLE — an explicit domain code
    enum Robust {
        PENDING("P", 10), ACTIVE("A", 20), CLOSED("C", 30);

        private final String code;
        private final int priority;

        Robust(String code, int priority) { this.code = code; this.priority = priority; }
        public String code() { return code; }
        public int priority() { return priority; }

        public static Robust fromCode(String code) {
            return Arrays.stream(values())
                    .filter(r -> r.code.equals(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown code: " + code));
        }
    }

    public static void main(String[] args) {
        // Simulate a reorder (what happens when someone "tidies up" the enum)
        Fragile before = Fragile.ACTIVE;   // ordinal 1
        System.out.println("before reorder: ACTIVE.ordinal() = " + before.ordinal());

        Fragile[] reordered = { Fragile.ACTIVE, Fragile.PENDING, Fragile.CLOSED };  // the "fix"
        System.out.println("after reorder : ACTIVE.ordinal() = " + reordered[0].ordinal());
        System.out.println("   → a stored ordinal of 1 now means " + reordered[1] + "  💥 DATA CORRUPTION");

        // The robust version is immune
        System.out.println("\nRobust.fromCode(\"A\") = " + Robust.fromCode("A"));
        System.out.println("code is unaffected by a reorder ✅");
    }
}
```

**Actual output:**

```text
before reorder: ACTIVE.ordinal() = 1
after reorder : ACTIVE.ordinal() = 0
   → a stored ordinal of 1 now means PENDING  💥 DATA CORRUPTION

Robust.fromCode("A") = A
code is unaffected by a reorder ✅
```

**Takeaway:** never persist `ordinal()`. Persist an explicit `code` that you control and that is part of your domain contract.

### Example 4 — Constant-specific bodies, and an enum implementing an interface

```java
import java.util.function.Function;

public class ConstantBodiesDemo {

    public enum Operation {
        ADD {
            @Override public int apply(int a, int b) { return a + b; }
            @Override public String symbol() { return "+"; }
        },
        MULTIPLY {
            @Override public int apply(int a, int b) { return a * b; }
            @Override public String symbol() { return "*"; }
        };

        public abstract int apply(int a, int b);
        public abstract String symbol();
    }

    // An enum implementing an interface — each constant with its own behaviour
    public enum Converter implements Function<String, Integer> {
        HEX {
            @Override public Integer apply(String s) { return Integer.parseInt(s, 16); }
        },
        DECIMAL {
            @Override public Integer apply(String s) { return Integer.parseInt(s); }
        };

        @Override public abstract Integer apply(String s);
    }

    public static void main(String[] args) {
        for (Operation op : Operation.values()) {
            System.out.println(op + " (" + op.symbol() + ") → " + op.apply(6, 3));
        }

        System.out.println("HEX    → " + Converter.HEX.apply("FF"));
        System.out.println("DECIMAL→ " + Converter.DECIMAL.apply("42"));

        // The anonymous subclasses are visible
        System.out.println("ADD's class = " + Operation.ADD.getClass().getSimpleName());
    }
}
```

**Actual output:**

```text
ADD (+) → 9
MULTIPLY (*) → 18
HEX    → 255
DECIMAL→ 42
ADD's class = Operation$1
```

**Takeaway:** the constant-specific bodies produce **anonymous subclasses** (`Operation$1`, `Operation$2`). That is how one enum carries genuinely different behaviour per case — with no extra top-level classes.

### Example 5 — The `switch` on an enum, and the `null` trap

```java
public class SwitchEnumDemo {
    public enum Status { PENDING, ACTIVE, CLOSED }

    public static void main(String[] args) {
        Status s = Status.ACTIVE;

        // A switch EXPRESSION (Java 14+) — exhaustiveness is CHECKED
        String label = switch (s) {
            case PENDING -> "waiting";
            case ACTIVE  -> "running";
            case CLOSED  -> "done";
        };
        System.out.println("label = " + label);

        // The null trap — switching on a null enum throws NPE (it unboxes!)
        try {
            Status nullStatus = null;
            switch (nullStatus) {
                case PENDING -> System.out.println("p");
                case ACTIVE  -> System.out.println("a");
                case CLOSED  -> System.out.println("c");
            }
        } catch (NullPointerException e) {
            System.out.println("switch on a null enum → " + e.getClass().getSimpleName()
                    + "  (the switch UNBOXES the enum!)");
        }

        // The safe way
        if (s == null) {
            System.out.println("null-checked first ✅");
        } else {
            switch (s) {
                case PENDING -> System.out.println("waiting");
                case ACTIVE  -> System.out.println("running");
                case CLOSED  -> System.out.println("done");
            }
        }
    }
}
```

**Actual output:**

```text
label = running
switch on a null enum → NullPointerException  (the switch UNBOXES the enum!)
null-checked first ✅
running
```

**Takeaway:** a `switch` on an enum compiles to a `lookupswitch` on `ordinal()`, which requires **unboxing** — and unboxing `null` throws. Null-check before switching, or use Java 21's pattern-matching `switch` with an explicit `case null`.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A domain enum with a stable code, validation, and lookups

```java
package com.example.order;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A production-grade domain enum.
 *
 * <p>Design decisions worth defending in an interview:
 * <ul>
 *   <li>A <b>stable {@code code}</b> — the persisted identifier. The ordinal is
 *       NEVER persisted, because a reorder would corrupt the data.</li>
 *   <li>A <b>cached lookup map</b> — {@code values()} is O(n) and clones;
 *       a static lookup by code is O(1) and allocation-free.</li>
 *   <li><b>Domain behaviour on the enum</b> — the transitions live with
 *       the constants, not in a service's switch statement.</li>
 *   <li>A <b>validation method</b> that names the legal transitions.</li>
 * </ul>
 */
public enum OrderStatus {

    PENDING("P", 0, "Order created, awaiting payment"),
    PAID("A", 1, "Payment received"),
    SHIPPED("S", 2, "Handed to the carrier"),
    DELIVERED("D", 3, "Delivered to the customer"),
    CANCELLED("X", 4, "Order cancelled"),
    REFUNDED("R", 5, "Payment refunded");

    private static final Map<String, OrderStatus> BY_CODE;
    private static final OrderStatus[] VALUES = values();   // cached — values() clones!

    static {
        Map<String, OrderStatus> map = new HashMap<>();
        for (OrderStatus status : VALUES) {
            OrderStatus previous = map.put(status.code, status);
            if (previous != null) {
                throw new IllegalStateException("Duplicate status code: " + status.code);
            }
        }
        BY_CODE = Collections.unmodifiableMap(map);
    }

    private final String code;
    private final int priority;
    private final String description;

    OrderStatus(String code, int priority, String description) {
        this.code = code;
        this.priority = priority;
        this.description = description;
    }

    public String code() { return code; }
    public int priority() { return priority; }
    public String description() { return description; }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == REFUNDED;
    }

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING   -> next == PAID || next == CANCELLED;
            case PAID      -> next == SHIPPED || next == REFUNDED;
            case SHIPPED   -> next == DELIVERED;
            case DELIVERED, CANCELLED, REFUNDED -> false;
        };
    }

    /** O(1) lookup by the stable code. */
    public static OrderStatus fromCode(String code) {
        OrderStatus status = BY_CODE.get(code);
        if (status == null) {
            throw new IllegalArgumentException("Unknown order status code: " + code);
        }
        return status;
    }

    /** The non-throwing variant. */
    public static Optional<OrderStatus> findByCode(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    /** A cached, allocation-free array — NEVER call values() in a hot loop. */
    public static OrderStatus[] all() {
        return VALUES.clone();
    }
}
```

> 💡 **The `static` block ordering trap, avoided.** The `BY_CODE` map is built in a
> `static` block that runs **after** the constants are created — so `VALUES` is
> already populated. (Had the map been built by a method called from a constant's
> constructor, it would have seen an empty array.)

### 6.2 A `switch`-free transition service using the enum's own rules

```java
package com.example.order;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public class OrderStateMachine {

    /** The legal transitions, expressed as a set per state. */
    private static final java.util.Map<OrderStatus, Set<OrderStatus>> TRANSITIONS =
            java.util.EnumMap.of(
                    OrderStatus.PENDING,   EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED),
                    OrderStatus.PAID,      EnumSet.of(OrderStatus.SHIPPED, OrderStatus.REFUNDED),
                    OrderStatus.SHIPPED,   EnumSet.of(OrderStatus.DELIVERED),
                    OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class),
                    OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class),
                    OrderStatus.REFUNDED,  EnumSet.noneOf(OrderStatus.class));

    public OrderStatus transition(OrderStatus current, OrderStatus next) {
        Objects.requireNonNull(current, "current status");
        Objects.requireNonNull(next, "next status");

        if (!current.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "Illegal transition: " + current + " → " + next
                    + " (allowed: " + TRANSITIONS.get(current) + ")");
        }
        return next;
    }

    public Set<OrderStatus> allowedFrom(OrderStatus current) {
        return TRANSITIONS.get(current);
    }
}
```

> 💡 **Note the `EnumMap` + `EnumSet` combination** — the transition table is a
> map of enum → a set of enums, and both use the specialised implementations.
> The whole rule set is 8 bytes per state.

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Persisting `ordinal()`

```java
repo.save(order.setStatus(status.ordinal()));   // ❌ breaks on a reorder
```

**✅ FIX** — persist `status.code()` (a stable domain identifier).

---

### ❌ Pitfall 2 — Using `ordinal()` to index an array

```java
String[] labels = {"pending", "active", "closed"};
labels[status.ordinal()];   // ❌ breaks when a constant is added
```

**✅ FIX** — use `EnumMap`, or a `switch`, or a `Map` keyed by the enum.

---

### ❌ Pitfall 3 — Calling `values()` in a hot loop

```java
for (Status s : Status.values()) { ... }   // ❌ a CLONE each iteration
```

**✅ FIX** — `private static final Status[] VALUES = Status.values();`

---

### ❌ Pitfall 4 — A static field read from a constant's constructor

```java
public enum Bad {
    A(compute()),
    B(compute());
    private final int value;
    Bad(int value) { this.value = value; }
    static final int SIZE = 10;                  // not yet initialised
    static int compute() { return SIZE; }        // 💥 returns 0
}
```

**✅ FIX** — put the shared state in a nested holder class, or compute it lazily.

---

### ❌ Pitfall 5 — Switching on a `null` enum

```java
Status s = null;
switch (s) { ... }   // 💥 NullPointerException — the switch unboxes
```

**✅ FIX** — null-check first, or use Java 21's `case null ->`.

---

### ❌ Pitfall 6 — A `switch` on an enum without all the cases

```java
switch (status) {
    case PENDING -> ...;
    case ACTIVE  -> ...;
    // CLOSED silently does nothing
}
```

**✅ FIX** — use a **switch expression**, where the compiler enforces exhaustiveness.

---

### ❌ Pitfall 7 — Using `equals` instead of `==` for enums

```java
if (status.equals(Status.ACTIVE)) { }   // ❌ works, but slower and misleading
```

**✅ FIX** — `status == Status.ACTIVE`. The constants are singletons; identity is the correct and fastest test.

---

### ❌ Pitfall 8 — `EnumSet.copyOf` on an empty non-`EnumSet` collection

```java
EnumSet.copyOf(Collections.emptyList());   // ❌ IllegalArgumentException
```

**✅ FIX** — `EnumSet.noneOf(MyEnum.class)` then `addAll(...)`.

---

### ❌ Pitfall 9 — Assuming `EnumMap` is immune to a reorder

```java
// ❌ A SERIALISED EnumMap is corrupted if the enum is reordered
```

**✅ FIX** — persist the enum's *code*, not the `EnumMap`. (`EnumSet` is also ordinal-based, but a serialised `EnumSet` stores the actual elements, not the bit positions — so it survives a reorder.)

---

### ❌ Pitfall 10 — A constructor in an enum with constant bodies

```java
public enum Op {
    ADD { ... },
    MULTIPLY { ... };

    Op() { }    // ❌ "an enum declaration with constant bodies cannot have a constructor"
}
```

**✅ FIX** — remove the constructor; the constant bodies provide the per-constant implementation.

---

### ❌ Pitfall 11 — Treating `EnumSet` as thread-safe

```java
static final EnumSet<Permission> PERMS = EnumSet.allOf(Permission.class);   // ❌ shared mutable
```

**✅ FIX** — `Collections.unmodifiableSet(EnumSet.allOf(...))` for a shared read-only instance.

---

### ❌ Pitfall 12 — Using an enum where a `sealed` type is better

```java
// ❌ An enum cannot carry per-case STATE, only per-case behaviour
```

**✅ FIX** — a `sealed interface` with a record per case (Java 17+) — e.g. `sealed interface PaymentMethod permits Card, Wallet, Cod`.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is an enum in Java, and how is it different from a `static final` constant?**
**A:** An enum is a special class the compiler generates, representing a fixed set of constants. Unlike `public static final int`, it provides **type safety** (`setStatus(42)` does not compile), a meaningful `toString()`, the ability to have **fields, a constructor, and methods** per constant, and support in `switch`. It also allows each constant to carry different behaviour via constant-specific class bodies.

**Q: Can you extend an enum or instantiate one with `new`?**
**A:** **No to both.** The enum class is implicitly `final` and its constructor is implicitly `private`. Only `java.lang.Enum` may extend it. Each constant is a `public static final` singleton instance created when the class is initialised.

**Q: What is the difference between `==` and `equals` for enums?**
**A:** Use **`==`**. Each constant is a singleton, so reference equality is correct and fastest. `Enum.equals` is identity-based and `final` (you cannot override it), so `equals` gives exactly the same answer — just with a method call. The one thing you must *not* do is compare an enum to a `String` with `equals` — that is always `false`.

**Q: What is the difference between `values()` and `valueOf()`?**
**A:** `values()` returns an array of all the constants — and it **clones** a private array on every call, so it is O(n) and allocates. `valueOf(String)` returns the constant with that **name**, in O(1), and throws `IllegalArgumentException` if the name is unknown.

**Q: Can an enum have a constructor and instance fields?**
**A:** Yes. The constructor is implicitly `private`, and it takes the arguments for the per-constant fields — e.g. `Planet(double mass, double radius)`. The fields are normally declared `final`.

### 🟡 Intermediate

**Q: Why should you never persist `ordinal()`?**
**A:** Because `ordinal()` is the **declaration index**, and it changes the moment someone reorders the enum or inserts a constant. A row stored as `1` would then silently mean a different status — **data corruption with no error**. Persist an explicit, stable `code` field that you control as part of your domain contract. The same rule applies to using `ordinal()` to index an array.

**Q: What is `EnumSet`, and why is it faster than a `HashSet`?**
**A:** `EnumSet` is a specialised `Set` backed by a **bit-vector** — a single `long` for up to 64 constants (a `long[]` beyond that). Each constant's bit position is its `ordinal()`. So `contains` is one AND + a test, `add` is one OR, `remove` is one AND-NOT, and `size()` is `Long.bitCount` — a single CPU instruction. `union`, `intersection` and `difference` are also single bitwise operations. A `HashSet` must hash, allocate a node, and walk a bucket. The whole set of 64 constants is **8 bytes**.

**Q: What is `EnumMap`, and what is its dependency on `ordinal()`?**
**A:** `EnumMap` is backed by a plain `Object[]` indexed by `key.ordinal()` — so there is **no hashing at all**; `get` is an array index and `put` is an array store. The dependency on `ordinal()` is a real hazard: **a serialised `EnumMap` is corrupted if the enum is reordered.** (`EnumSet` is safer here, because a serialised `EnumSet` stores the actual elements, not bit positions.) The safe practice is to persist the enum's `code`, not an `EnumMap`.

**Q: How do constant-specific class bodies work?**
**A:** An enum with a body for some constants generates an **anonymous subclass** for each of those constants — `Operation$1`, `Operation$2` — while the enum class itself remains `final`. The body must implement all the abstract methods. The rules: the enum may not declare a constructor when it has constant bodies, and the body cannot access the enum's non-static instance fields (the constants are created before those initialisers run).

**Q: What happens when you `switch` on a `null` enum?**
**A:** It throws a **`NullPointerException`**. This surprises people because a `switch` on a `null` `String` also throws, but the *reason* here is specific: a `switch` on an enum compiles to a `lookupswitch` on `ordinal()`, which requires **unboxing** the enum — and unboxing `null` throws. Null-check before switching, or use Java 21's pattern-matching `switch` with an explicit `case null`.

**Q: Why are enum constants created before other static fields?**
**A:** By the JLS (§12.4.2), the enum constants are initialised **first**, then the other static fields. So a static method called from a constant's constructor sees the other static fields in their **default** state (0, null). The fix is a nested holder class, or a lazily-computed value.

### 🔴 Advanced / Senior

**Q: How does an enum serialise, and what is the security implication?**
**A:** The JVM **special-cases** enums: the stream stores the constant's **name**, and deserialisation resolves that name back to the **canonical instance** — so a deserialised enum is `==` to the original and no duplicate instance can ever be created. If the name is unknown (the enum was refactored), deserialisation throws `IllegalArgumentException`. The security implication is small but real: because resolution is by name to a fixed set of instances, an attacker **cannot forge** an enum constant through a deserialisation payload — the type system of the enum acts as an allow-list.

**Q: When would you use a `sealed` interface instead of an enum?**
**A:** When the cases need to carry **different state**, not just different behaviour. An enum's constants are all instances of one class, so they share the same fields; a `sealed` interface lets each case be a **record with its own fields** (`Card(String last4)`, `Wallet(String provider)`). Combined with Java 21's pattern-matching `switch`, you get compiler-checked exhaustiveness over a closed set — the same guarantee an enum switch gives, without the shared-field limitation. Use an enum when the cases are pure data labels; use a sealed hierarchy when they are data *objects*.

**Q: How would you design the enum for a domain where the set of statuses grows, and the states have behaviour?**
**A:** Two candidate designs. (1) **Enum with constant bodies** — simple, and the transitions live with the constants; the cost is that the enum class grows and the transition rules are scattered across the bodies. (2) **A sealed interface + a separate transition policy** — `sealed interface OrderState permits Pending, Paid, Shipped` with a `StateMachine` that owns the legal transitions, expressed as an `EnumMap<State, Set<State>>` or a `Map<State, Set<State>>`. The second is better when the transition rules are a *separate concern* (they often change more often than the states themselves) and when you need to test the rules independently. The design principle: **keep the states in the domain, keep the transition policy in a policy object** — the same "one reason to change" discipline as SOLID.

**Q: What are the performance characteristics of an enum-based `switch`?**
**A:** A `switch` on an enum compiles to a `lookupswitch` — a **jump table indexed by `ordinal()`** — so it is O(1) with a single indirect branch, much faster than an if-else chain of `equals` comparisons. The cost is that the compiler emits a synthetic `int[]` mapping the ordinals to the branch targets, and a `switch` on a `String` is different: Java 7+ compiles it to a `hashCode`-based switch (two switches — one on the hash, one on an equality check) plus a `equals` fallback. The practical guidance: switch on the **enum** (fast, exhaustive) rather than on the enum's `name()` (slower, and loses exhaustiveness checking).

### ⚫ Expert / Principal

**Q: What is the "enum initialisation order" trap, and how does it relate to class initialisation in general?**
**A:** The JLS guarantees that an enum's constants are created **before** any other static field of the enum is initialised, and before the enum's static initialiser block runs. Therefore, a static method invoked from a constant's constructor observes every other static field in its **default** state. This is a special case of a general hazard: **class initialisation is not re-entrant and has a strict, specified order** — the superclass first, then the static fields *in textual order*, then the static initialiser blocks *in textual order*. The deeper lesson is that **static state has an implicit temporal coupling** that the compiler cannot check, and the failure appears only in a specific initialisation order. The robust designs are: (1) put shared constants in a **separate holder class** whose initialisation is triggered on first use; (2) make the value `final` and compute it in the *field* initialiser rather than a method call; (3) make the enum's instance initialisers depend on nothing but the constructor arguments — which is exactly the "safe publication" requirement of the JMM's final-field semantics. A closely related trap: a static field in a class that reads a static field of *another* class can trigger that other class's initialisation at an unexpected moment.

**Q: How would you evolve an enum in a live system where the persisted data must keep working?**
**A:** Treat the enum's **code** as a versioned, persisted contract and design for evolution explicitly. The rules: (1) **never repurpose a code** — once `P` means `PENDING`, it means `PENDING` forever; retire a status by adding a new one, not by changing the meaning of an old one. (2) **never remove a constant that appears in persisted data** — or at least, keep a mapping table from the old code to the new one for the duration of the migration window, and deserialise old data through it. (3) **version the payload** — store both the code and a schema version, so the reader knows how to interpret it. (4) **make the lookup tolerant** — `findByCode` returning `Optional` for a "may be legacy" path and `fromCode` throwing for a "must be valid" path, so the two use cases are explicit. (5) For the rare case where a *shape* change is needed, model the status as a `sealed` interface with a `record` per case — then a new case is a new record and old data maps to an explicit "legacy" record. The meta-lesson: **the enum's code is a schema, and schemas need a migration plan.** The same discipline applies to any persisted enum: it is a *wire format*, and wire formats are forever.

---

## 9. Counter-Questions & Traps

### CQ1. "If enums are so limited — no per-case state, no subclassing — why not just use a class with static constants?"

- **Naive:** "Because enums are type-safe."  ← *true, but it is only one of six reasons, and the weakest one*
- **The trap:** The trap is to treat the enum as merely "a type-safe int". The real reasons are structural: an enum **cannot be subclassed** (so the set is provably closed), **cannot be instantiated** (so the cardinality is exactly known), and — the big one — gets a **specialised, faster implementation** in the JDK (`EnumSet`, `EnumMap`) that is only possible *because* the key space is a small, dense, fixed set of integers.
- **Expert answer:** The complete list: (1) type safety — no `setStatus(42)`; (2) a **closed** set — the compiler knows the cardinality; (3) **singleton per constant** — no duplicate instances; (4) **specialised collections** — `EnumSet` is one `long`, `EnumMap` is an array; (5) **exhaustiveness checking** in switch expressions; (6) **behaviour per constant** via constant-specific bodies, and the ability to implement an interface. Point 4 is the one that most candidates miss, and it is the strongest argument: the JDK could not ship an `EnumSet` that is a single `long` if the key space were open — the bit-vector representation *requires* a known, dense index space. So the enum's limitation is exactly what makes its optimisation possible.

### CQ2. "`EnumSet` uses `ordinal()` internally. If reordering the enum breaks it, isn't it dangerous?"

- **Naive:** "Yes, so don't reorder enums."  ← *a rule, but it leaves the interviewer unsatisfied*
- **The trap:** The trap is to treat the risk as a reason to avoid `EnumSet`/`EnumMap`. The risk is real but **bounded and specific**, and the mitigation is precise — and it is *not* "don't reorder" (you will inevitably reorder an enum to make it alphabetical).
- **Expert answer:** The precise facts: (1) an `EnumSet` or `EnumMap` **in memory** is always consistent with the *current* ordinals — reordering the enum and restarting the JVM is fine. (2) The **only** danger is a **serialised** `EnumSet`/`EnumMap` that was written *before* the reorder and is read *after* it. (3) The mitigation is to **never persist an `EnumMap`/`EnumSet`** — persist the enum's `code` and rebuild the collection on load. (4) And note the asymmetry: `EnumSet` is actually *safer* than `EnumMap` here, because a serialised `EnumSet` stores the **elements** (by name), not the bit positions — so it survives a reorder. Only `EnumMap`, whose array is positional, is corrupted. So the rule is not "avoid `EnumSet`" but "**never serialise an `EnumMap`**".

### CQ3. "If `EnumSet` is so fast, why does the JDK still provide `HashSet`? Would anyone ever use a `HashSet<Status>`?"

- **Naive:** "Only if you don't know the type is an enum."  ← *true, and it understates the case*
- **The trap:** The trap is to think `HashSet<Status>` is a mistake. It is not — it is the *right* choice in several real situations, and a candidate who says "always `EnumSet`" has not thought about the trade-offs.
- **Expert answer:** `EnumSet` wins when the element type is a **compile-time-known enum** and the set is small. It is the wrong choice when: (1) the element type is `Class<E>` (a "enum of enums" — `EnumSet` needs the `Class` at construction, and `Class<E>` is not itself an enum constant, so `EnumSet.of` cannot be used — you need `EnumSet.allOf` with a runtime `Class`, which forces the reflective path); (2) the set must be **mutable and shared** — `EnumSet` is not thread-safe and does not have the `Collections.synchronizedSet` ergonomics you would want; (3) the set must interoperate with APIs typed to `Set` and you want to avoid the `EnumSet`-specific behaviours; (4) you need the set to be **serialised in a form that survives a reorder** — the `HashSet` stores elements by name, so it does. The honest framing: `EnumSet` is the default *when the type is statically known to be an enum*; `HashSet` is the correct fallback for the dynamic cases, and the performance difference only matters if the set is on a hot path.

### CQ4. "You said an enum can't have per-constant state. But what if I really need it?"

- **Naive:** "Use a `Map` from the enum to the state."  ← *works, but is ugly*
- **The trap:** The trap is to miss that this is exactly what a `sealed` interface with `record`s was designed for (Java 17+). The `Map` workaround puts the state *outside* the type, which means it can get out of sync, and it loses the compiler's exhaustiveness checking.
- **Expert answer:** Three options, in order of preference. (1) **A `sealed` interface with a `record` per case** (Java 17+) — `sealed interface PaymentMethod permits Card, Wallet, Cod`, with `record Card(String last4) implements PaymentMethod {}`. Each case carries its own state, and a Java 21 pattern-matching `switch` over the sealed hierarchy is **compiler-checked for exhaustiveness** — so adding a case is a compile error in every `switch` that must handle it. (2) **A `Map<Enum, State>`** — simple, but the state is now external to the type and can be inconsistent. (3) **A field on the enum** — if the "state" is the same for all instances of a constant, a per-constant field works fine (e.g. `PENDING("P", 10)`). The design lesson: the enum's limitation (one shared class) is precisely the thing `sealed` hierarchies remove, and Java 17+ gives you the choice.

### CQ5. "You said `values()` clones the array. Isn't that a big deal for performance?"

- **Naive:** "Yes, so cache it."  ← *the right answer, but the interviewer wants the scale*
- **The trap:** The trap is to overstate the cost. The clone is a **shallow** copy of a tiny array (typically 3–20 elements) — it is a few dozen nanoseconds, and the allocation is trivially collected by the young generation. It is *not* a bottleneck in almost any realistic code.
- **Expert answer:** The scale: for a 5-constant enum, `values()` clones a 5-element array — roughly 20–30 ns and a 40-byte allocation. In a loop of 1,000 iterations that is 30 µs — noticeable only in a measured hot path. So the rule is proportional: **cache it in a hot loop, do not bother otherwise.** The more interesting point is *why* it clones at all: it is a **defensive copy**, the same principle as `List.copyOf` and `Collections.unmodifiableList` — a caller must not be able to mutate the enum's internal array and corrupt the enum. (If `values()` returned the internal array, `Status.values()[0] = null` would break the enum for the whole JVM.) The design lesson: **when an API returns a collection of internal state, it must either copy it or make it unmodifiable** — and a copy is the simpler choice for an array.

### CQ6. "Why can't an enum be extended? Isn't that limiting?"

- **Naive:** "Because Java doesn't support multiple inheritance of classes."  ← *the reason for the diamond problem, not the reason enums are final*
- **The trap:** The trap is to give the *class* reason when the question is about the *enum* reason. Enums are final because of a **semantic** requirement: an enum represents a **closed, known set of instances**. If it were extendable, a subclass could add new constants, and every `switch` over the enum would silently not handle them — the exhaustiveness guarantee would be gone.
- **Expert answer:** The distinction: `final` on a normal class is a *design* choice (open for extension, closed for modification is the default). `final` on an enum is a **semantic necessity** — the set of instances is the *type's* defining property. This is exactly the same reason a `sealed` interface (Java 17+) restricts its permitted subtypes: the language is giving you a way to say "this set is closed, and the compiler will check that you handle every member". So the evolution of the language is: *implicitly closed* (enum) → *explicitly closed and extensible-by-permission* (sealed) → *open* (a plain interface). And the practical consequence for the interviewer: because an enum is closed, a `switch` over it can be **exhaustiveness-checked**, and that is a guarantee you cannot get from a plain `int` constant or an open class hierarchy.

### CQ7. "Is `EnumMap` really safe? I've heard people say it breaks with `ordinal()`."

- **Naive:** "Yes, it depends on `ordinal()`, so don't reorder the enum."  ← *correct, but the interviewer's next question is "so is it safe?"*
- **The trap:** The trap is to leave the answer at "don't reorder" without distinguishing the **in-memory** case from the **persisted** case — because that distinction determines whether it is a real production risk or a theoretical one.
- **Expert answer:** The distinction: **in memory, an `EnumMap` is always correct.** The array is indexed by the *current* ordinal, and the keys are the current constants — there is no possible inconsistency at runtime, because the enum and the map are initialised together. The **only** hazard is **persistence**: a serialised `EnumMap` stores the *values* array positionally. If the enum is reordered between writing and reading, the values are attached to the wrong constants — silent corruption. So the accurate answer: `EnumMap` is safe as an in-memory structure; it is **unsafe to persist**. The mitigation is to persist the enum's `code` and rebuild the map on load. And note the practical severity: this only bites if (a) you serialise an `EnumMap` across a *deployment* that reorders the enum, and (b) nobody noticed. It is a real bug class (it has happened in the wild with `EnumMap` and with the `ordinal()`-based `EnumSet` in older JDKs) but it is a narrow one.

### CQ8. "You mentioned `Enum.equals` is identity-based. So how does `EnumSet.contains` work?"

- **Naive:** "It uses `equals`, which is identity for enums."  ← *true, and it misses the optimisation*
- **The trap:** The trap is to think `EnumSet.contains` walks the elements. It does not — it does **one bitwise AND**. The identity semantics is what makes the bit-vector *possible*: because each constant has a unique, stable `ordinal()`, the membership of a constant is exactly one bit position.
- **Expert answer:** The chain of reasoning: (1) an enum has a **finite, known, fixed** set of instances; (2) each has a **unique, stable** index (`ordinal()`); (3) therefore the set of members can be represented as a **bit-vector** over those indices; (4) `contains(e)` is `(elements & (1L << e.ordinal())) != 0` — one AND and a branch, no comparison at all. The identity-based `equals` is not used *in the hot path* — it is only the *semantic* justification for the representation. The same reasoning explains `EnumMap`: a finite, known key space indexed by a dense integer means the map can be a **flat array** with no hashing and no nodes. The general lesson: **the fastest data structure comes from exploiting a structural guarantee of the key type** — the same reason an `int`-keyed map can be a plain array, and why a `String`-keyed map must hash.

### CQ9. "You said Java 21's pattern-matching `switch` handles a `null` enum. What changed exactly?"

- **Naive:** "You can add `case null`."  ← *true, but incomplete*
- **The trap:** The trap is to miss the *default* behaviour change, which is the part that matters for compatibility: in a pattern-matching `switch`, if there is **no** `case null`, a `null` selector throws a `NullPointerException` — and it does so **predictably and by design**, rather than as an accident of unboxing.
- **Expert answer:** The full picture for Java 21: (1) `case null ->` is now permitted, and it makes the null case **explicit** in the source. (2) If no `case null` is present and the selector is `null`, a `NullPointerException` is thrown — but now it is a *specified* behaviour of the pattern-matching switch, distinct from the classic enum-switch's accidental unboxing NPE. (3) Java 21 also **removed the need for exhaustiveness gymnastics**: a switch over a `sealed` type (or an enum) with all cases covered is a **switch expression** whose type is inferred, and the compiler checks that every constant is handled — including the `null` case, which must be handled explicitly if you want to avoid the NPE. (4) The `default` label is still allowed but is discouraged for enums and sealed types because it defeats the exhaustiveness check. The deeper point: the language moved from "the compiler *permits* an incomplete switch" to "the compiler *requires* a complete one" — which is a compiler-enforced contract, the best kind.

### CQ10. "If `EnumSet` is one `long`, what happens with an enum that has 100 constants?"

- **Naive:** "It uses a `long[]`."  ← *correct, and it needs more*
- **The trap:** The trap is to stop at "it uses a `long[]`", because there is a subtlety: the JDK has **two** implementations, and the switch between them is not purely about the count — the "jumbo" implementation is selected by a `RegularEnumSet` instance *growing* past 64 elements, not by the enum's constant count.
- **Expert answer:** The precise mechanism: `EnumSet` starts as a `RegularEnumSet` (a single `long`). When you `add` a constant whose `ordinal()` is ≥ 64, it **converts itself in place** into a `JumboEnumSet` (a `long[]`), copying the existing bits. So the implementation is chosen by *usage*, not by the enum's declaration. The performance consequence: for an enum with 100 constants, an `EnumSet` is still O(1) per operation (the bit index is `ordinal / 64` and the bit is `ordinal % 64`), but the memory is `ceil(n/64) × 8` bytes. So the answer to "is it still fast for 100 constants" is **yes** — the bit-vector scales; only the memory grows. The design lesson: the bit-vector representation is not limited to 64 elements, it is *chunked* — and that chunking is the general technique for extending a fixed-size optimisation to an unbounded domain.

---

## 10. Rapid-Fire MCQs

**Q1.** An enum class is implicitly…
- A) Abstract  B) Final  C) Serializable  D) Deprecated

**Q2.** An enum's constructor is implicitly…
- A) Public  B) Protected  C) Private  D) Package-private

**Q3.** Enum constants are…
- A) `public static final` instances  B) `private static final`  C) Compile-time constants  D) Inner classes

**Q4.** Which class does every enum extend?
- A) `java.lang.Object`  B) `java.lang.Enum`  C) `java.lang.Record`  D) `java.lang.Constant`

**Q5.** `values()` returns…
- A) The internal array  B) A clone of the internal array  C) A new array each time by copying  D) An immutable list

**Q6.** `valueOf("MISSING")` throws…
- A) `NullPointerException`  B) `IllegalArgumentException`  C) `NoSuchElementException`  D) `EnumFormatException`

**Q7.** `ordinal()` is…
- A) The declaration index  B) A hash code  C) Randomised per run  D) The same as `name()`

**Q8.** Why must you never persist `ordinal()`?
- A) It is random  B) It changes when the enum is reordered  C) It is expensive  D) It can be negative

**Q9.** `EnumSet` is backed by…
- A) A `HashMap`  B) A bit-vector (`long`/`long[]`)  C) A sorted array  D) A `TreeSet`

**Q10.** `EnumSet.contains(e)` costs…
- A) O(1)  B) O(log n)  C) O(n)  D) O(n log n)

**Q11.** `EnumMap` is backed by…
- A) A `HashMap`  B) An array indexed by `ordinal()`  C) A `TreeMap`  D) A linked list

**Q12.** `EnumSet.size()` is implemented as…
- A) A counter field  B) `Long.bitCount(elements)`  C) A tree traversal  D) An array scan

**Q13.** An enum with constant-specific bodies generates…
- A) Separate top-level classes  B) Anonymous subclasses  C) Inner records  D) A `Map` of lambdas

**Q14.** Can an enum with constant-specific bodies declare a constructor?
- A) Yes  B) No  C) Only a no-arg one  D) Only a private one

**Q15.** `EnumMap` rejects a null key?
- A) Yes  B) No  C) Only one null  D) Only at runtime

**Q16.** `EnumSet.copyOf(emptyList())` where the list is not an `EnumSet` throws…
- A) `NullPointerException`  B) `IllegalArgumentException`  C) `NoSuchElementException`  D) Nothing — it returns empty

**Q17.** Switching on a `null` enum throws…
- A) `NullPointerException`  B) `IllegalArgumentException`  C) `IllegalStateException`  D) Nothing

**Q18.** Why does switching on a null enum throw NPE?
- A) The switch unboxes the enum  B) `ordinal()` is null  C) The compiler inserts a null check that fails  D) `name()` is null

**Q19.** Enum constants are created…
- A) Before any other static field  B) After all static fields  C) In textual order with the fields  D) Lazily on first use

**Q20.** How is an enum deserialised?
- A) By ordinal  B) By name, resolving to the canonical instance  C) By a copy constructor  D) By `readObject`

**Q21.** `Enum.equals` is…
- A) Field-based  B) Identity-based  C) Name-based  D) Ordinal-based

**Q22.** Which is the fastest `Set` implementation in the JDK for enum elements?
- A) `HashSet`  B) `LinkedHashSet`  C) `EnumSet`  D) `TreeSet`

**Q23.** Java 17's `sealed` interface replaces the need for…
- A) `HashMap`  B) An "enum with per-case state"  C) `EnumMap`  D) The string pool

**Q24.** `EnumSet` for an enum with 100 constants uses…
- A) One `long`  B) A `long[]` ("jumbo")  C) A `HashSet`  D) A `TreeSet`

**Q25.** Are `EnumSet` and `EnumMap` thread-safe?
- A) Yes  B) No  C) Only reads are safe  D) Only `EnumSet` is

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | The enum class is implicitly `final` — the set of constants is provably closed. |
| 2 | **C** | The constructor is implicitly `private` — you cannot `new` an enum. |
| 3 | **A** | Each constant is a `public static final` singleton created at class-initialisation. |
| 4 | **B** | Every enum extends `java.lang.Enum<E>`. |
| 5 | **B** | `values()` returns a **clone** — a defensive copy, so the caller cannot corrupt the enum. |
| 6 | **B** | `valueOf` throws `IllegalArgumentException` for an unknown name. |
| 7 | **A** | The declaration index (0, 1, 2…). |
| 8 | **B** | Reordering the enum changes the ordinals, silently corrupting persisted data. |
| 9 | **B** | A bit-vector — one `long` for ≤ 64 constants. |
| 10 | **A** | One AND + a test. |
| 11 | **B** | A plain `Object[]` indexed by `key.ordinal()` — no hashing. |
| 12 | **B** | `Long.bitCount(elements)` — a single CPU instruction. |
| 13 | **B** | Anonymous subclasses (`Operation$1`, `Operation$2`). |
| 14 | **B** | "An enum declaration with constant bodies cannot have a constructor." |
| 15 | **A** | `EnumMap` rejects a null key. |
| 16 | **B** | It cannot infer the element type from an empty collection. |
| 17 | **A** | The switch unboxes the enum to get its `ordinal()`. |
| 18 | **A** | `lookupswitch` needs the ordinal, which requires unboxing — and unboxing `null` throws. |
| 19 | **A** | The JLS initialises the enum constants **first**, then the other static fields. |
| 20 | **B** | By name, resolved to the canonical instance — so no duplicate can ever be created. |
| 21 | **B** | Identity-based and `final` — you cannot override it. |
| 22 | **C** | `EnumSet` — a single `long` for up to 64 constants. |
| 23 | **B** | A `sealed` interface with a `record` per case can carry per-case *state*, which an enum cannot. |
| 24 | **B** | A `long[]` — the "jumbo" implementation. |
| 25 | **B** | Neither is thread-safe — wrap a shared one in `Collections.unmodifiableSet`. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                          THE ENUM FACTS                                 │
├──────────────────────────────────────────────────────────────────────────┤
│  class        → implicitly final, extends java.lang.Enum<E>             │
│  constructor  → implicitly private                                      │
│  constants   → public static final singlet instances                    │
│  values()     → CLONES the internal array — O(n), cache it!             │
│  valueOf(s)   → O(1) by name; throws IllegalArgumentException if absent │
│  ordinal()    → the declaration index — NEVER PERSIST IT                │
│  equals()     → identity-based and FINAL                               │
│  toString()   → `name()` by default, but OVERRIDABLE                    │
│  compareTo()  → by ordinal — hence the fragility of ordinal             │
│  serialise    → by NAME; deserialise → the canonical instance           │
│  initialised  → the constants FIRST, then the other static fields       │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│              EnumSet vs HashSet · EnumMap vs HashMap                    │
├──────────────────┬──────────────────────┬───────────────────────────────┤
│                  │ EnumSet / EnumMap    │ HashSet / HashMap            │
├──────────────────┼──────────────────────┼───────────────────────────────┤
│ backing          │ bit-vector / array   │ hash table                    │
│ hashing          │ NONE                 │ yes                           │
│ contains / get   │ O(1)                 │ O(1) avg                      │
│ size()           │ O(1) (bitCount)      │ O(1)                          │
│ union/inter/etc  │ O(1) bitwise         │ O(n)                          │
│ memory (2 items) │ 8 bytes              │ ~128 bytes                    │
│ order            │ declaration order    │ hash order                    │
│ null key         │ rejected (map)       │ 1 allowed (map)               │
│ depends ordinal  │ YES (map)            │ no                            │
│ thread-safe      │ NO                   │ NO                            │
└──────────────────┴──────────────────────┴───────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                        THE 6 ENUM TRAPS                                 │
├──────────────────────────────────────────────────────────────────────────┤
│  1. PERSISTING ordinal()      → persists a reordering bug              │
│  2. values() IN A HOT LOOP    → clones every iteration                 │
│  3. STATIC FIELD IN A CONSTANT'S CONSTRUCTOR → reads 0, not the value   │
│  4. switch ON A NULL ENUM     → NPE (it unboxes!)                     │
│  5. EnumSet.copyOf(EMPTY LIST)→ IllegalArgumentException               │
│  6. SERIALISING an EnumMap    → corrupted by a later reorder           │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The enum design checklist

```text
  ☐ A stable `code` for persistence (never `ordinal()`)
  ☐ `fromCode` (throws) for a "must be valid" path
  ☐ `findByCode` (Optional) for a "may be legacy" path
  ☐ A cached `VALUES` array for hot loops
  ☐ `equals`/`hashCode` NOT overridden (they are final and identity-based)
  ☐ A `toString()` that is meaningful in logs
  ☐ Transition rules expressed with the enum or in a separate policy
  ☐ If per-case STATE is needed → a `sealed` interface + `record`s
```

### When to use what

| Situation | Use |
|---|---|
| A fixed set of labels (status, type, category) | An **enum** |
| A fixed set where each case needs different **behaviour** | An **enum with constant bodies**, or a `sealed` interface |
| A fixed set where each case needs different **state** | A **`sealed` interface** with a `record` per case |
| A fixed set that grows at runtime | A `Map<String, X>` or a database lookup — **not** an enum |
| A set of permission flags | An `EnumSet` (or a `long` bitmask for > 64) |

### Performance notes

| Topic | Guidance |
|---|---|
| `EnumSet` | Always prefer it over `HashSet` for enum elements — 8 bytes vs 128 |
| `EnumMap` | Always prefer it over `HashMap` for enum keys |
| `values()` | Cache it in a `static final` if used in a loop |
| `valueOf` | Use it, not a manual stream scan |
| `switch` on an enum | O(1) — a `lookupswitch` jump table |
| `ordinal()` as an array index | Never — use `EnumMap` |
| Thread safety | `EnumSet`/`EnumMap` are **not** thread-safe |

### Observability

```java
// Log the CODE, not the enum's toString (which may be overridden)
log.info("order status changed to code={} (enum={})", status.code(), status);

// Count the statuses in play — an EnumSet makes this trivial
EnumSet<OrderStatus> active = EnumSet.of(PENDING, PAID, SHIPPED);
log.info("active statuses = {}", active);   // no hashing, no allocation
```

### Migration safety

| Change | Safe? |
|---|---|
| Add a new constant at the **end** | ✅ Safe for `code`-based persistence; ⚠ for `ordinal()`-based |
| Insert a constant in the **middle** | ❌ Breaks `ordinal()`-based persistence |
| Reorder the constants | ❌ Breaks `ordinal()`-based persistence and serialised `EnumMap`s |
| Rename a constant | ❌ Breaks `name()`-based persistence and serialised enums |
| Remove a constant | ❌ Breaks deserialisation of old data |
| Change a constant's `code` | ❌ Breaks `code`-based persistence |

> 💡 **The rule:** treat the enum's `code` as a **wire format**. Never change or
> reuse a code; retire a status by adding a new one.

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| `EnumMap` / `EnumSet` in full | [04 · Collections Framework](../04-Collections-Framework/03-Set-Queue-Utilities.md) |
| Sealed classes & pattern matching (the modern alternative) | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| `equals`/`hashCode` (why enums use identity) | [08 · equals, hashCode & toString](../01-Core-Java-OOP/08-OOP-equals-hashCode-toString.md) |
| The Strategy pattern (enum with behaviour, the OO way) | [09 · Design Patterns](../09-Design-Patterns/01-Design-Patterns.md) |
| The JDK source | `java.lang.Enum`, `java.util.EnumSet`, `java.util.EnumMap` |

---

**Next Chapter:** → [14 · Reflection & Annotations](../14-Reflection-Annotations/01-Reflection-and-Annotations.md)
**Previous Chapter:** ← [12 · Strings & String Internals](../12-Strings/01-String-Internals.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
