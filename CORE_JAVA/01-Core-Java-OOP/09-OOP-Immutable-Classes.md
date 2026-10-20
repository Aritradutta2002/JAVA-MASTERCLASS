## 📋 Table of Contents

- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — The Immutability Checklist](#2-mental-model--the-immutability-checklist)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The 5 Rules](#31-the-5-rules)
  - [3.2 Defensive Copying Explained](#32-defensive-copying-explained)
  - [3.3 Java Records as Immutable Data Carriers (Java 16+)](#33-java-records-as-immutable-data-carriers-java-16)
  - [3.4 Why String Is Immutable](#34-why-string-is-immutable)
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

- An **immutable class** is one whose instances can never change after construction. Once created,
  the state is fixed forever.
- **The 5 rules**: `final` class, `private final` fields, no setters, initialise via constructor,
  and return **defensive copies** of mutable state.
- **`final` alone is not enough** — `private final List<String> l = new ArrayList<>()` is still
  mutable through `l.add(...)`. Immutability is about the *whole reachable object graph*.
- **Defensive copy on the way IN and on the way OUT** — the constructor must not retain a caller's
  mutable reference, and the getter must not hand out its own.
- **`Collections.unmodifiableList(x)` is a VIEW, not a copy** — it still reflects later changes to
  `x`. `List.copyOf(x)` is a true immutable **copy**. This is the single most-missed distinction.
- **Records (Java 16+) are the modern default** for immutable data carriers — but they are only
  *shallowly* immutable: a record with a `List` component is still mutable unless you copy in a
  compact constructor.
- **JDK immutables**: `String`, `Integer`, `Long`, `Double`, `BigDecimal`, `LocalDate`,
  `LocalDateTime`, `Instant`.
- **Immutability buys you**: free thread safety (no locks), safe publication under the JMM, stable
  `hashCode()` (safe as a map key), cacheability, and failure atomicity.

---

## 1. What & Why — The Problem This Solves

### The problem

Mutable shared state is the root of a huge fraction of production incidents:

- Two threads read-modify-write the same object → **lost updates** and torn state.
- A collection is handed to a caller, who mutates it → **the caller changes your internal state**.
- An object used as a `HashMap` key is mutated → it becomes **permanently unreachable**.
- A half-constructed object is observed by another thread → **partially initialised state**.

All four are prevented by the same design decision: **make the state immutable, and the class of
bugs disappears**.

### The solution

An immutable class:

1. Is declared `final` (or `sealed`) so it cannot be subclassed.
2. Has only `private final` fields.
3. Exposes no mutators.
4. Computes its state fully in the constructor.
5. Never lets a mutable object escape — it copies mutable inputs in and copies mutable outputs out.

### The analogy

An **immutable invoice**. Once issued, the amount, the date and the line items are fixed. If the
customer wants a different amount, the system issues a *new* invoice — it does not reach in and
edit the old one. A mutable invoice would be a blank cheque.

---

## 2. Mental Model — The Immutability Checklist

```text
┌─────────────────────────────────────────────────────────────┐
│           5 Rules for Immutable Class                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Declare class as FINAL (prevent subclassing)            │
│  2. Make all fields PRIVATE and FINAL                       │
│  3. NO setter methods                                        │
│  4. Initialize all fields via CONSTRUCTOR                    │
│  5. Return DEFENSIVE COPIES of mutable fields               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### The "reachability" test

The real definition of immutability is not about the fields — it is about what a caller can
**reach**:

```text
   ┌──────────────── Your immutable object ────────────────┐
   │                                                      │
   │   private final String name;      ← immutable leaf    │
   │   private final int age;          ← primitive         │
   │   private final List<String> tags;← ⚠️ MUTABLE leaf   │
   │                                                      │
   └──────────────────────────────────────────────────────┘
              ▲
              │  the caller must not be able to REACH
              │  a mutable object and change it
              │
        ┌─────┴──────┐
        │  IN:  copy │   new ArrayList<>(tags)
        │  OUT: copy │   List.copyOf(tags)
        └────────────┘
```

> **The one-line test:** *can any code outside this class change the object's state?* If yes, it is
> not immutable, no matter how many `final` keywords you wrote.

---

## 3. Deep Dive — Theory

### 3.1 The 5 Rules

```text
┌─────────────────────────────────────────────────────────────┐
│           5 Rules for Immutable Class                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Declare class as FINAL (prevent subclassing)            │
│  2. Make all fields PRIVATE and FINAL                       │
│  3. NO setter methods                                        │
│  4. Initialize all fields via CONSTRUCTOR                    │
│  5. Return DEFENSIVE COPIES of mutable fields               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### Complete Example

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class Employee {                          // Rule 1: final class
    private final String name;                          // Rule 2: private final
    private final int age;                              // Rule 2: private final
    private final Date joinDate;                        // Rule 2: private final (mutable object!)
    private final List<String> skills;                  // Rule 2: private final (mutable collection!)
    
    // Rule 4: Initialize via constructor
    public Employee(String name, int age, Date joinDate, List<String> skills) {
        this.name = name;                               // String is immutable — safe
        this.age = age;                                 // Primitive — safe
        this.joinDate = new Date(joinDate.getTime());   // Rule 5: Defensive copy on input
        this.skills = new ArrayList<>(skills);           // Rule 5: Defensive copy on input
    }
    
    // Rule 3: Only getters, NO setters
    public String getName() { return name; }            // String is immutable — safe to return
    public int getAge() { return age; }                 // Primitive — safe to return
    
    // Rule 5: Return defensive copies of mutable fields
    public Date getJoinDate() {
        return new Date(joinDate.getTime());            // Return copy, not original
    }
    
    public List<String> getSkills() {
        return Collections.unmodifiableList(skills);    // Return unmodifiable view
    }
}
```

#### Why Each Rule Matters

```java
// WITHOUT Rule 1 (final class): Subclass can break immutability
class MutableEmployee extends Employee {
    private String mutableField;
    public void setMutableField(String value) { mutableField = value; }
}

// WITHOUT Rule 5 (defensive copy): External code can modify state
Employee emp = new Employee("John", 30, date, skills);
Date d = emp.getJoinDate();
d.setYear(2000);           // ❌ Modifies internal state if no defensive copy!

List<String> s = emp.getSkills();
s.add("Hacking");          // ❌ Modifies internal list if no defensive copy!
```

**Why Rule 1 matters specifically:** a subclass can add a *mutable* field, which means the object is
no longer immutable even though the parent's fields are `final`. It can also override `equals()` or
`toString()` in a way that breaks the parent's contract. `final` (or `sealed` with no mutable
subtypes) closes this.

---

### 3.2 Defensive Copying Explained

#### What is Defensive Copying?

Creating a new copy of mutable objects when accepting input AND returning output.

```java
// ❌ Bad: No defensive copy
public Employee(String name, Date joinDate) {
    this.joinDate = joinDate;  // Stores reference — caller can modify!
}

public Date getJoinDate() {
    return joinDate;           // Returns reference — caller can modify!
}

// ✅ Good: Defensive copy both ways
public Employee(String name, Date joinDate) {
    this.joinDate = new Date(joinDate.getTime());  // Copy on input
}

public Date getJoinDate() {
    return new Date(joinDate.getTime());           // Copy on output
}
```

#### When NOT Needed

- Primitive types (`int`, `boolean`, etc.) — always copied by value
- Immutable objects (`String`, `Integer`, `LocalDate`) — safe to share reference

#### The Critical Distinction: View vs Copy

This is the subtlest and most commonly mishandled point in immutability:

```java
List<String> original = new ArrayList<>(List.of("a", "b"));

// ❌ unmodifiableList is a VIEW, not a copy!
List<String> view = Collections.unmodifiableList(original);
original.add("c");              // the source list changed...
System.out.println(view);       // ...so the VIEW changed too: [a, b, c]

// ✅ List.copyOf takes a SNAPSHOT
List<String> copy = List.copyOf(original);
original.add("d");
System.out.println(copy);       // still [a, b, c] — unaffected
```

| | `Collections.unmodifiableList(x)` | `List.copyOf(x)` |
|---|---|---|
| Type of result | **Unmodifiable view** | **Immutable copy** |
| Reflects later changes to `x`? | ✅ **Yes** | ❌ No |
| `null` elements allowed | ✅ Yes | ❌ **No** — throws `NullPointerException` |
| Allocation | One wrapper object | A new array |
| Use for | Exposing a live list read-only | True immutability |

> **Rule of thumb:** if the class claims to be immutable, use `List.copyOf` (or `Collections.unmodifiableList`
> on a field you already copied). `unmodifiableList` alone gives you a read-only *window*, not
> immutability.

---

### 3.3 Java Records as Immutable Data Carriers (Java 16+)

```java
// Records are automatically immutable (but NOT fully defensive)
public record Person(String name, int age) { }

// Compiler generates:
// - final class
// - private final fields
// - Constructor, getters (name(), age())
// - equals(), hashCode(), toString()

Person p = new Person("John", 30);
p.name();  // "John"
p.age();   // 30

// ⚠️ WARNING: Records with mutable fields are NOT truly immutable
public record Team(String name, List<String> members) { }

Team team = new Team("Dev", new ArrayList<>(List.of("Alice", "Bob")));
team.members().add("Charlie");  // ❌ Modifies internal list!

// Fix: Add defensive copy in compact constructor
public record Team(String name, List<String> members) {
    public Team {
        members = List.copyOf(members);  // Immutable copy
    }
}
```

**The compact constructor** (`public Team { ... }` with no parameter list) is the compact form: it
runs before the field assignments, and any assignment to a parameter *replaces* the value the
compiler will store. That is exactly where the defensive copy belongs.

**What records give you that classes don't:**

| Feature | Record | Class |
|---------|--------|-------|
| `final` class + `final` fields | ✅ automatic | manual |
| Canonical constructor | ✅ generated | manual |
| Accessors | ✅ `name()` | manual `getName()` |
| `equals`/`hashCode` from components | ✅ generated | manual |
| `toString` | ✅ generated | manual |
| Extends another class | ❌ **no** | ✅ yes |
| Implements interfaces | ✅ yes | ✅ yes |
| Declares instance fields | ❌ **no** | ✅ yes |
| Mutability of components | ⚠️ **shallow** | depends |

**A record cannot extend another class** — it implicitly extends `java.lang.Record`. That is a
deliberate trade: you lose inheritance but gain a compiler-guaranteed contract.

---

### 3.4 Why String Is Immutable

`String` is the canonical immutable class, and its immutability is not accidental:

1. **String Pool** — the pool can share instances safely *only* if strings never change. If
   `String` were mutable, `s1 == s2` could become `false` after `s1` was modified, and the pool
   would be corrupted.
2. **Thread safety** — a `String` can be shared across threads with zero synchronisation. The JMM
   guarantees that a `String` built in one thread is safely published to another.
3. **Security** — class loading, file paths and URLs are strings. If a `String` were mutable, a
   malicious thread could change a path *after* a security check but *before* the file was opened
   (a TOCTOU attack).
4. **HashCode caching** — `String` caches its hash code because it can never change, making it a
   fast, stable map key.
5. **Compiler interning of literals** — string literals are interned, and interning is only sound
   for immutable values.

> **Note on `StringBuilder` vs `String`:** `StringBuilder` is **mutable** (and therefore not
> thread-safe); `String` is immutable (and therefore safe to share). Use `StringBuilder` for
> building strings in a single thread; use `String` for anything shared or stored.

---

## 4. Visualizations

### 4.1 The Escape Analysis — How Mutability Leaks

```text
   ❌ MUTABLE (leaky)                     ✅ IMMUTABLE (sealed)
   ┌──────────────────────┐               ┌──────────────────────┐
   │ Employee             │               │ Employee             │
   │  final List<String>  │               │  final List<String>  │◄──┐
   │      tags            │               │      tags            │   │ copy
   └──────────┬───────────┘               └──────────────────────┘   │
              │                                        ▲            │
              │  getTags() returns THE SAME list       │            │
              ▼                                        │            │
   ┌──────────────────────┐               ┌───────────┴──────────┐ │
   │ caller                │               │ caller               │ │
   │  tags.add("HACKED")  │               │  tags.add(..) ❌      │ │
   │  → EMPLOYEE MUTATED! │               │  UnsupportedOpEx     │ │
   └──────────────────────┘               └──────────────────────┘ │
                                                              │
                     defensive copy OUT ───────────────────────┘
```

### 4.2 `unmodifiableList` (View) vs `List.copyOf` (Copy)

```text
   original = [a, b]
        │
        ├──► Collections.unmodifiableList(original)  ──► VIEW
        │         points at the SAME backing list              │
        │    original.add("c")  →  view is now [a, b, c]  ⚠️    │
        │                                                       │
        └──► List.copyOf(original)                    ──► COPY
                  new snapshot, independent                       │
             original.add("d")  →  copy is still [a, b, c]  ✅    │
```

### 4.3 Why Immutability Removes the Need for Synchronisation

```text
   MUTABLE (needs a lock)                  IMMUTABLE (needs nothing)
   ┌────────────────────────┐               ┌────────────────────────┐
   │ Thread A               │               │ Thread A               │
   │  read  count → 5       │               │  read  count → 5       │
   │  write count = 6       │               │  (never writes)        │
   ├────────────────────────┤               ├────────────────────────┤
   │ Thread B               │               │ Thread B               │
   │  read  count → 5       │  ← RACE!      │  read  count → 5       │
   │  write count = 6       │  (lost update)│  (same value, always)  │
   └────────────────────────┘               └────────────────────────┘
        needs synchronized / AtomicInteger       safe by construction

   WHY: there is no write, so there is no interleaving to order.
   The JLS §17.5 final-field freeze gives safe publication for free.
```

### 4.4 The Immutability Checklist Applied

```text
   ┌──────────────────────────────────────────────────────────────────┐
   │  RULE 1  final class / sealed                                    │
   │          └── prevents a subclass adding mutable state            │
   │                                                                  │
   │  RULE 2  private final fields                                    │
   │          └── no external handle to the reference                 │
   │                                                                  │
   │  RULE 3  no setters                                              │
   │          └── no way to change the state after construction        │
   │                                                                  │
   │  RULE 4  constructor initialises everything                       │
   │          └── the object is never half-built                      │
   │                                                                  │
   │  RULE 5  defensive copies in AND out                             │
   │          └── no mutable object is reachable from outside         │
   │                                                                  │
   │  EXTRA    no mutable superclass, no mutable fields exposed       │
   │  EXTRA    deep-immutable collaborators (or immutable ones)       │
   └──────────────────────────────────────────────────────────────────┘
```

### 4.5 Record vs Final Class — Choosing

```text
   Use a RECORD when:                        Use a FINAL CLASS when:
   ────────────────────                      ─────────────────────
   ✓ Pure data carrier                       ✓ Need a custom constructor
   ✓ Want equals/hashCode/toString free     ✓ Need validation logic
   ✓ No inheritance needed                  ✓ Need to implement interfaces
   ✓ All components immutable               ✓ Want compact constructor
                                            ✓ Need static factories
   Record limitations:                       Class limitations:
   ✗ Cannot extend a class                   ✗ Must write equals/hashCode
   ✗ Cannot declare instance fields          ✗ Must remember the contract
   ✗ Accessors are name(), not getName()
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — The Leak, and the Seal

**Problem:** *"Show me a class that claims to be immutable but isn't."*

```java
import java.util.*;

public class D3 {

    static class Unsafe {
        private final List<String> items = new ArrayList<>();
        void add(String s) { items.add(s); }
        List<String> get() { return items; }              // ❌ returns THE list
    }

    static class Safe {
        private final List<String> items;
        Safe(List<String> src) { this.items = List.copyOf(src); }   // ✅ copy IN
        List<String> get() { return items; }               // ✅ copy OUT (already immutable)
    }

    record Team(String name, List<String> members) { }     // ⚠️ shallow

    public static void main(String[] args) {
        List<String> src = new ArrayList<>(List.of("Alice", "Bob"));

        Unsafe u = new Unsafe();
        u.add("x");
        u.get().add("HACKED");
        System.out.println("unsafe internal list = " + u.get());

        Safe s = new Safe(src);
        src.add("Charlie");                                // caller mutates their list
        System.out.println("safe internal list = " + s.get());
        try { s.get().add("HACKED"); }
        catch (UnsupportedOperationException e) { System.out.println("mutation blocked: " + e.getClass().getSimpleName()); }

        record R(String name, List<String> members) { }
        R r = new R("Dev", new ArrayList<>(List.of("Alice")));
        System.out.println("record list is mutable: " + r.members());

        record Fixed(String name, List<String> members) {
            Fixed { members = List.copyOf(members); }      // compact constructor
        }
        Fixed f = new Fixed("Dev", new ArrayList<>(List.of("Alice")));
        try { f.members().add("Charlie"); }
        catch (UnsupportedOperationException e) { System.out.println("fixed record blocked mutation: " + e.getClass().getSimpleName()); }
    }
}
```

**Actual output (compiled and run on JDK 25):**

```text
unsafe internal list = [x, HACKED]
safe internal list = [Alice, Bob]
mutation blocked: UnsupportedOperationException
record list is mutable: [Alice]
fixed record blocked mutation: UnsupportedOperationException
```

**Dry run:**

| # | Code | Result | Why |
|---|------|--------|-----|
| 1 | `u.get().add("HACKED")` | internal list = `[x, HACKED]` | `get()` returns the **same** `ArrayList`; the caller mutated our state |
| 2 | `src.add("Charlie")` then `s.get()` | `[Alice, Bob]` | The constructor **copied** `src`, so later changes to `src` do not reach us |
| 3 | `s.get().add("HACKED")` | `UnsupportedOperationException` | `List.copyOf` returns an **immutable** list — mutation throws rather than silently succeeding |
| 4 | `r.members()` | `[Alice]` and mutable | A plain record holds the **same** `ArrayList` it was given — shallow immutability |
| 5 | `f.members().add("Charlie")` | `UnsupportedOperationException` | The compact constructor replaced the component with an immutable copy |

**Takeaway:** Five lines, five different levels of protection. Note that a *defensive copy* is not
enough on its own — you must also **not leak** the internal reference on the way out.

---

### Example 2 — Immutability and the HashMap Key

**Problem:** *"Why are immutable objects such good map keys?"*

```java
import java.util.*;

public class ImmutableKey {

    record Point(int x, int y) { }   // a record IS an immutable value object

    static class MutablePoint {
        int x, y;
        MutablePoint(int x, int y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object o) {
            return o instanceof MutablePoint p && p.x == x && p.y == y;
        }
        @Override public int hashCode() { return Objects.hash(x, y); }
    }

    public static void main(String[] args) {
        Map<Point, String> immutableMap = new HashMap<>();
        immutableMap.put(new Point(1, 2), "immutable");
        System.out.println("immutable key lookup = " + immutableMap.get(new Point(1, 2)));

        Map<MutablePoint, String> mutableMap = new HashMap<>();
        MutablePoint key = new MutablePoint(1, 2);
        mutableMap.put(key, "mutable");
        System.out.println("mutable key lookup (before) = " + mutableMap.get(new MutablePoint(1, 2)));

        key.x = 99;    // ❌ mutating a key that is already in the map
        System.out.println("mutable key lookup (after mutation) = " + mutableMap.get(new MutablePoint(1, 2)));
        System.out.println("map still contains " + mutableMap.size() + " entry (unreachable)");
    }
}
```

**Actual output (compiled and run on JDK 25):**

```text
immutable key lookup = immutable
mutable key lookup (before) = mutable
mutable key lookup (after mutation) = null
map still contains 1 entry (unreachable)
```

**Dry run:**

| Step | `key.hashCode()` | Bucket | Result |
|------|------------------|--------|--------|
| 1 | `hash(1, 2)` | X | `mutableMap.put(key, "mutable")` → stored in bucket X |
| 2 | `hash(1, 2)` | X | `get(new MutablePoint(1,2))` → same bucket → `equals()` matches → `"mutable"` |
| 3 | `key.x = 99` | — | The hash **will** change |
| 4 | `hash(99, 2)` | Y | `get(...)` → looks in bucket Y → empty → `null` |
| 5 | — | X | The entry is still in the map — a **ghost**, unreachable by value |

**Takeaway:** The immutable key works forever; the mutable key became a ghost the moment it
changed. This is the practical payoff of immutability: **the `hashCode` is stable for the object's
whole life**, so the contract cannot be broken by accident.

---

### Example 3 — Thread Safety Without a Lock

**Problem:** *"Show me a counter that needs no synchronisation."*

```java
import java.util.concurrent.atomic.AtomicLong;

public class ImmutableCounter {

    // The state never changes after construction -> the same object can be
    // shared by any number of threads with ZERO synchronisation.
    record Counters(long requests, long errors) { }

    public static void main(String[] args) throws Exception {
        int threads = 8, iterations = 100_000;

        // ❌ The mutable version needs a lock
        final long[] unsafe = {0};
        Thread[] ts1 = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts1[i] = new Thread(() -> {
                for (int j = 0; j < iterations; j++) { unsafe[0]++; }
            });
        }
        for (Thread t : ts1) t.start();
        for (Thread t : ts1) t.join();
        System.out.println("mutable counter   = " + unsafe[0] + " (expected " + (threads * iterations) + ")");

        // ✅ The immutable version needs no lock at all
        final AtomicLong total = new AtomicLong();
        Thread[] ts2 = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts2[i] = new Thread(() -> {
                for (int j = 0; j < iterations; j++) { total.addAndGet(1); }
            });
        }
        for (Thread t : ts2) t.start();
        for (Thread t : ts2) t.join();

        Counters snapshot = new Counters(total.get(), 0);
        System.out.println("immutable snapshot= " + snapshot);
    }
}
```

**Actual output (compiled and run on JDK 25):**

```text
mutable counter   = 728419 (expected 800000)
immutable snapshot= Counters[requests=800000, errors=0]
```

> The mutable counter loses updates (the exact number varies per run); the immutable snapshot is
> always exactly `800000` because the `AtomicLong` accumulates into a *new* immutable value that is
> then shared read-only.

**Takeaway:** The immutable object is the *snapshot*; the atomic accumulator produces it. The
snapshot itself can be published to any thread with no lock, no `volatile`, and no risk — that is
the JMM's safe-publication guarantee for properly constructed objects with `final` fields.

---

## 6. Code from Scratch — Production-Grade

```java
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A genuinely immutable domain value object.
 *
 *  - final class          -> cannot be subclassed to add mutable state
 *  - private final fields -> no external handle to the references
 *  - no setters           -> no way to change state after construction
 *  - constructor only     -> the object is never half-built
 *  - defensive copies     -> no mutable object is reachable from outside
 */
public final class Payment {

    private final String paymentId;
    private final String orderId;
    private final BigDecimal amount;      // BigDecimal is immutable - safe to share
    private final String currency;
    private final List<String> labels;    // mutable type -> copied in, immutable out
    private final Instant createdAt;

    public Payment(String paymentId, String orderId, BigDecimal amount,
                   String currency, List<String> labels, Instant createdAt) {
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");

        // Defensive copy IN - we own this list, not the caller.
        // List.copyOf rejects nulls, so a null label fails fast here.
        this.labels = List.copyOf(Objects.requireNonNull(labels, "labels"));

        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Payment amount must not be negative");
        }
    }

    // ---- accessors: return only immutable values, never the internal list ----
    public String getPaymentId() { return paymentId; }
    public String getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }

    // List.copyOf already returns an immutable list, so returning it directly
    // is safe - no further copy needed, and no copy is possible.
    public List<String> getLabels() { return labels; }

    // ---- equals / hashCode from the SAME fields (see chapter 08) ----
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payment other = (Payment) o;
        return paymentId.equals(other.paymentId)
            && orderId.equals(other.orderId)
            && amount.compareTo(other.amount) == 0   // scale-insensitive
            && currency.equals(other.currency)
            && labels.equals(other.labels)
            && createdAt.equals(other.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentId, orderId, amount.stripTrailingZeros(), currency, labels, createdAt);
    }

    @Override
    public String toString() {
        return "Payment{id='" + paymentId + "', order='" + orderId + "', amount="
            + amount.toPlainString() + " " + currency + '}';
    }
}
```

**Why this is production-grade:**

| Concern | How it is handled |
|---------|-------------------|
| Real immutability | `final` class + `private final` + no setters + defensive copies |
| Deep immutability | The `List` component is copied in **and** returned as an immutable list |
| Null safety | `requireNonNull` at every boundary — fails fast at construction |
| Invariants | Negative amounts are rejected in the constructor; the object is never invalid |
| `BigDecimal` correctness | `compareTo` for equality, `stripTrailingZeros` for hashing (see chapter 08) |
| Safe publication | `final` fields → JLS §17.5 gives free safe publication across threads |
| Safe as a map key | The `hashCode` can never change → the entry is always findable |
| No leaks in `toString` | No PAN, no card number, no secret |

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Returning a mutable field directly

```java
public List<String> getItems() {
    return items;  // ❌ Caller can modify!
}
```
**Fix:** return `List.copyOf(items)` (or return an already-immutable field).

### ❌ Pitfall 2 — Not copying mutable input

```java
public MyClass(Date date) {
    this.date = date;  // ❌ Caller retains reference!
}
```
**Fix:** `this.date = new Date(date.getTime());`

### ❌ Pitfall 3 — Forgetting to make class final

```java
public class NotTrulyImmutable {  // ❌ Can be subclassed!
    private final int value = 42;
}
```
**Fix:** `public final class TrulyImmutable { ... }` — or `sealed` with no mutable permitted subtypes.

### ❌ Pitfall 4 — Using arrays (always mutable)

```java
private final int[] data;
public int[] getData() { return data; }  // ❌ Caller can modify!
```
**Fix:** `return data.clone();` (or `Arrays.copyOf(data, data.length)`).

### ❌ Pitfall 5 — `Collections.unmodifiableList` mistaken for a copy

```java
private final List<String> items = new ArrayList<>();
public List<String> getItems() { return Collections.unmodifiableList(items); }
```
**Why it's still a bug:** the wrapper is a **view**. If anything else holds a reference to `items`
and mutates it, the "immutable" list changes too. Use `List.copyOf` in the constructor.

### ❌ Pitfall 6 — A record with a mutable component

```java
public record Team(String name, List<String> members) { }
// team.members().add("Charlie");  → ❌ compiles and mutates!
```
**Fix:** a compact constructor with `members = List.copyOf(members);`

### ❌ Pitfall 7 — A mutable superclass

```java
class Base { protected int counter; }          // ❌ mutable superclass
public final class Child extends Base { }      // "final" but NOT immutable
```
**Why:** the inherited `counter` can be changed by any code with access. A class can only be
immutable if its whole supertype chain is immutable.

### ❌ Pitfall 8 — Believing `final` on the collection field is enough

```java
private final List<String> items = new ArrayList<>();  // ❌ still mutable
```
**Why:** `final` freezes the *reference*. The list itself is still an `ArrayList`.

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: How to create an immutable class in Java?**
A: Make class final, all fields private final, no setters, constructor initialization, defensive
copies of mutable fields.

**Q: What is an immutable class?**
A: A class whose instances cannot be modified after creation. Once created, the state never
changes.

**Q: Why make immutable class final?**
A: Prevents subclasses from adding mutable state or overriding methods to break immutability.

**Q: Can an immutable class have mutable fields?**
A: Yes, but must use defensive copying on both input (constructor) and output (getters).

**Q: What is defensive copying?**
A: Creating a new copy of mutable objects when accepting/returning them, so external code can't
modify internal state.

**Q: How does immutability help with thread safety?**
A: Immutable objects can't change state, so no race conditions, no synchronization needed.

### 🟡 Intermediate

**Q: Why is String immutable?**
A: String Pool optimization, thread safety, security (class loading), hashCode caching.

**Q: Are Records truly immutable?**
A: Only if all components are immutable. Mutable components (List, Date) require defensive copying
in compact constructor.

**Q: What are the built-in immutable classes?**
A: `String`, `Integer`, `Long`, `Double`, `BigDecimal`, `LocalDate`, `LocalDateTime` — plus
`Instant`, `Duration`, `UUID`, `BigInteger` and every primitive wrapper.

**Q: What's the difference between `Collections.unmodifiableList` and `List.copyOf`?**
A: `unmodifiableList` returns an unmodifiable **view** that still reflects later changes to the
backing list. `List.copyOf` returns a truly **immutable copy** — it also rejects `null` elements
with an `NullPointerException`.

**Q: Can we modify an immutable object using reflection?**
A: Technically yes, but it violates the contract. Module system (Java 9+) restricts this further.

### 🔴 Advanced / Senior

**Q: What is the JMM benefit of immutability?**
A: A properly constructed object with `final` fields is **safely publishable** without any
synchronisation. JLS §17.5 guarantees that a thread which reads a reference to the object also sees
the correctly initialised `final` fields, even through a data race. This is why immutable objects
can be shared freely between threads — the "safe publication" guarantee is what makes it work, not
the absence of a race by luck.

**Q: Why is `final` insufficient for immutability on its own?**
A: Because `final` freezes the *reference*, not the *object*. `private final List<String> l = new
ArrayList<>()` is still mutable through `l.add(...)`. Immutability is a property of the entire
reachable object graph: the class must be `final`, the fields `private final`, and every mutable
collaborator must be defensively copied in and out.

**Q: What is the cost of immutability?**
A: Allocation and copying. Every "change" creates a new object rather than mutating one, which
costs GC pressure and, in the naive case, O(n) copying per update. The mitigations are structural
sharing, `List.copyOf` on small lists, and the fact that the JIT's escape analysis can stack-
allocate short-lived immutables. In exchange you get free thread safety, stable hash codes,
cacheability and dramatically simpler reasoning.

**Q: How do you make a JPA entity immutable?**
A: You generally don't — JPA entities need a no-arg constructor and mutable state for dirty
checking. The pattern is to separate the **entity** (mutable, persistence-aware) from the
**value object** (immutable, embeddable). The entity holds an `@Embeddable` immutable value type
for the parts that never change, and only exposes a setter that *replaces* the whole immutable
value.

### ⚫ Expert / Principal

**Q: Records are called immutable. Is that actually true?**
A: A record is *shallowly* immutable: the record's own fields are `final` and cannot be
reassigned, but a component can still reference a mutable object, and that object can be mutated
through the accessor. `record Team(String name, List<String> members)` returns the very same list
it was given. So the record guarantees *reference immutability of its own state*, not *deep
immutability of the graph*. To get the latter you copy in a compact constructor — and note the
copy must also be reflected in `hashCode`/`equals` if you change the semantics. The precise
statement is: a record is immutable if and only if all its components are immutable.

**Q: Where do you draw the line on immutability in a large system?**
A: I apply it to **values** and leave **entities** mutable. A value — a Money, an OrderId, a
PriceRange — has no identity beyond its content, so immutability is free and it buys thread
safety, stable hashing and cacheability. An entity — a Customer, an Order — has an identity that
survives its attribute changes, so it must be mutable, and the correct discipline there is to keep
the *identity* fields immutable and expose behaviour rather than setters. The anti-pattern I
watch for is an "immutable" class that actually contains a mutable collaborator, which gives the
*appearance* of safety with none of the guarantees.

---

## 9. Counter-Questions & Traps

### CQ1. "If `final` prevents reassignment, why isn't the class immutable?"

- **Naive answer:** "Because the fields could still be changed."  ← vague
- **The trap:** The candidate knows something is off but cannot articulate *how* a `final` field's
  contents can change. The interviewer is listening for "the reference is frozen, the object is
  not".
- **Expert answer:** "`final` freezes the **reference**, so the field can never point at a different
  object. But the object it points at may still be mutable — `l.add(...)`, `date.setTime(...)`. So
  the field is *stable*, not the *state*. To make the state immutable you must also ensure no
  mutable object is reachable: copy mutable inputs in the constructor and copy mutable outputs in
  the getters, or only hold references to immutable types."

### CQ2. "Is `Collections.unmodifiableList` enough to make the class immutable?"

- **Naive answer:** "Yes, the list can't be modified."  ← the classic wrong answer
- **The trap:** It *looks* right — the returned list throws on `add`. But it is a **view**, and
  the interviewer is testing whether you know that.
- **Expert answer:** "No. `unmodifiableList(x)` returns an unmodifiable **view** of `x` — it
  prevents modification *through the wrapper*, but it still reflects any later change made to `x`
  itself. If any other code holds a reference to `x`, the supposedly-immutable list changes
  underneath. The correct tool is `List.copyOf(x)`, which takes a snapshot and returns a truly
  immutable list. The same distinction applies to `unmodifiableMap` vs `Map.copyOf`."

### CQ3. "Records are final and have final fields. Are they automatically immutable?"

- **Naive answer:** "Yes, records are immutable by definition."  ← the trap
- **The trap:** The candidate has heard "records are immutable" and stopped there. The interviewer
  wants the *shallow vs deep* distinction.
- **Expert answer:** "Only **shallowly**. A record's own fields are `final` and cannot be
  reassigned, but a component may reference a mutable object. `record Team(String name, List<String>
  members)` hands back the very same list it was given — `team.members().add("Charlie")` compiles
  and mutates it. A record is deeply immutable if and only if all its components are. The fix is a
  compact constructor: `public Team { members = List.copyOf(members); }`."

### CQ4. "Doesn't immutability just waste memory and hurt performance?"

- **Naive answer:** "Yes, so avoid it."  ← over-correction
- **The trap:** The interviewer is testing whether you can articulate the *trade*, not just pick a
  side.
- **Expert answer:** "It costs allocation and copying, and that is real. But the return is larger:
  no locks, no races, no partial state, stable hash codes, and objects that are safe to cache and
  share. And the cost is often overstated — the JIT's escape analysis can stack-allocate
  short-lived immutables, `List.copyOf` on small lists is cheap, and structural sharing avoids the
  copying entirely. I would not make every object immutable: I make **values** immutable because
  they have no identity, and leave **entities** mutable because they do. The right question is not
  'is immutable faster' but 'does this object have an identity beyond its content?'"

### CQ5. "How would you make a JPA entity immutable? Isn't that impossible?"

- **Naive answer:** "It's impossible — JPA needs mutable entities and a no-arg constructor."  ←
  correct but unhelpful
- **The trap:** The interviewer is looking for the *separation* pattern, which is the actual
  professional answer.
- **Expert answer:** "A JPA *entity* cannot be immutable — Hibernate needs a no-arg constructor and
  mutable state to perform dirty checking and to populate proxies. The professional answer is to
  separate the two concerns: the entity stays mutable and owns the identity, while the parts that
  never change live in an `@Embeddable` **immutable value type**. The entity then exposes a setter
  that *replaces* the whole value rather than mutating it, and the value's own fields are
  `final` with a defensive copy on construction. That gives you the JPA behaviour you need and
  the value-object guarantees where they matter."

### CQ6. "How would you prove a class is truly immutable?"

- **Naive answer:** "Read the code and check the fields are final."  ← incomplete
- **The trap:** The interviewer is asking about *verification*, and the naive answer misses the
  transitive nature of the problem — a `final` field of a mutable type is not immutable.
- **Expert answer:** "I check three things, in order. One: the class is `final` or `sealed`, so no
  subclass can add mutable state. Two: every field is `private final` and initialised in the
  constructor. Three — and this is the part people miss — **every mutable type reachable from the
  object is defensively copied in and out**. That is a transitive property: it is not enough for
  my direct fields, it has to hold for the whole object graph. I would also check the superclass
  chain, since a mutable superclass makes everything below it mutable. Tools like `Error Prone`'s
  `Immutable` checker and `japicmp` can help, but the design review is what actually catches it."

### CQ7. "What about `java.util.Date`? Everyone uses it in constructors."

- **Naive answer:** "Just copy it with `new Date(date.getTime())`."  ← right, but incomplete
- **The trap:** The interviewer is pushing toward the *modern* answer, and there is a better one
  than copying a legacy mutable type.
- **Expert answer:** "Copying it works, but `java.util.Date` is a legacy mutable class with a
  confusing API — `getYear()` is deprecated, month indexing is off-by-one, and it is not
  thread-safe. I would not put it in a new design at all. `java.time` has the right types:
  `Instant` for a point in time, `LocalDate` for a date without a zone, `ZonedDateTime` for a
  date-time with a zone. All are immutable, so no defensive copy is needed and they can be shared
  freely. Copying `Date` is the correct *migration* step; moving to `java.time` is the correct
  *destination*."

### CQ8. "If a class is immutable, do I still need `synchronized` anywhere?"

- **Naive answer:** "No, never."  ← the trap
- **The trap:** The candidate over-generalises. The *object* needs no lock, but the *system around
  it* might.
- **Expert answer:** "The immutable object itself needs no lock — that is exactly the benefit, and
  it comes from the JMM's safe-publication guarantee for `final` fields. But immutability does not
  make the surrounding system safe. If I need to *accumulate* state, I still need an atomic
  counter or a lock, because the accumulation itself is mutable — I would compute the new immutable
  value and publish it atomically. And if two threads must coordinate on a *sequence* of events, I
  need a lock, a queue or a `synchronized` block regardless of how immutable the payload is.
  Immutability removes the lock from the *data*, not from the *protocol*."

---

## 10. Rapid-Fire MCQs

**Q1.** Which is **NOT** required for an immutable class?
- **A)** The class must be `final`
- **B)** All fields must be `private final`
- **C)** There must be no setters
- **D)** All fields must be `static`

**Q2.** `Collections.unmodifiableList(list)` differs from `List.copyOf(list)` because:
- **A)** `unmodifiableList` returns a **view**; `copyOf` returns an **immutable copy**
- **B)** `copyOf` allows `null` elements
- **C)** `unmodifiableList` is faster
- **D)** They are identical

**Q3.** A Java `record` with a `List` component is:
- **A)** Deeply immutable
- **B)** Shallowly immutable — the list can still be mutated
- **C)** Not immutable at all
- **D)** Immutable only if the list is empty

**Q4.** The correct way to copy a `List` in a record's compact constructor is:
- **A)** `members = new ArrayList<>(members);`
- **B)** `members = List.copyOf(members);`
- **C)** `members = Collections.unmodifiableList(members);`
- **D)** `this.members = members;`

**Q5.** `List.copyOf(list)` throws `NullPointerException` when:
- **A)** The list is empty
- **B)** The list contains a `null` element
- **C)** The list is immutable
- **D)** The list has more than 10 elements

**Q6.** Why must an immutable class be `final`?
- **A)** For performance
- **B)** So a subclass cannot add mutable state
- **C)** Because `final` methods cannot be overridden
- **D)** To enable the JIT to inline methods

**Q7.** `java.util.Date` is:
- **A)** Immutable
- **B)** Mutable — `setTime()` changes it
- **C)** A primitive
- **D)** Automatically thread-safe

**Q8.** An immutable object used as a `HashMap` key is safe because:
- **A)** `HashMap` copies the key
- **B)** Its `hashCode()` can never change
- **C)** `HashMap` uses `equals()` first
- **D)** It is `final`

**Q9.** Which is a JDK-immutable type?
- **A)** `java.util.Date`
- **B)** `java.time.LocalDate`
- **C)** `java.util.ArrayList`
- **D)** `StringBuilder`

**Q10.** The JMM guarantee that makes immutable objects safely publishable is in:
- **A)** JLS §17.5
- **B)** JLS §8.1.3
- **C**) JLS §12.4
- **D)** JLS §4.12.4

**Q11.** `private final int[] data` with `getData()` returning `data` is:
- **A)** Immutable
- **B)** Not immutable — the array contents can be changed
- **C)** Immutable if the class is `final`
- **D)** Immutable if the array is empty

**Q12.** Which best describes "shallow immutability"?
- **A)** The object's own fields cannot be reassigned
- **B)** The entire reachable object graph cannot be mutated
- **C)** The class is `final`
- **D)** The object is thread-safe


### Answer Key

| # | Answer | Explanation |
|---|--------|-------------|
| 1 | **D** | Instance fields must be `private final`; `static` is a separate concern (and a `static` mutable field would *break* immutability). |
| 2 | **A** | `unmodifiableList` wraps the original and reflects later changes to it; `List.copyOf` takes a snapshot and returns a truly immutable list. |
| 3 | **B** | A record's own fields are `final`, but a mutable component (a `List`) can still be mutated through the accessor. |
| 4 | **B** | `List.copyOf` returns a truly immutable copy and rejects nulls. `new ArrayList<>` is mutable; `unmodifiableList` is only a view. |
| 5 | **B** | `List.copyOf` rejects `null` elements with a `NullPointerException` — fail fast at construction. |
| 6 | **B** | A subclass could add a mutable field, making the object mutable despite the parent's `final` fields. |
| 7 | **B** | `java.util.Date` is a legacy mutable class — `setTime()` mutates it. Use `java.time` types instead. |
| 8 | **B** | The `hashCode` is computed from `final` fields, so it can never change and the key can never become unreachable. |
| 9 | **B** | `java.time.LocalDate` is immutable. `Date` and `StringBuilder` are mutable; `ArrayList` is mutable. |
| 10 | **A** | JLS §17.5 defines the final-field semantics that give safe publication of properly constructed objects. |
| 11 | **B** | `final` freezes the array *reference*; `data[0] = 5` still works. You must return `data.clone()`. |
| 12 | **A** | Shallow immutability means the object's own fields cannot be reassigned, but the objects they reference may still be mutated. |

## 11. Cheat Sheet / One-Page Summary

### The 5 Rules

| # | Rule | Why |
|---|------|-----|
| 1 | Class is `final` (or `sealed`) | A subclass could add mutable state |
| 2 | All fields `private final` | No external handle; reference never moves |
| 3 | No setters | No way to change state after construction |
| 4 | Initialise in the constructor | The object is never half-built |
| 5 | Defensive copies in **and** out | No mutable object is reachable from outside |

### JDK Immutable Types

`String` · `Integer` `Long` `Short` `Byte` `Double` `Float` `Boolean` `Character` · `BigDecimal`
`BigInteger` · `LocalDate` `LocalDateTime` `Instant` `Duration` `Period` `ZonedDateTime` ·
`UUID` · `Optional` · `Collections.unmodifiable*` (read-only views)

### Mutable Types to Defend Against

`Date` · `ArrayList` `HashMap` `HashSet` (all collections) · arrays (`int[]`, `String[]`) ·
`StringBuilder` · `StringBuffer` (thread-safe but mutable) · `Calendar` · `Scanner` · any POJO with
a setter

### Copy vs View — Decision Table

| Method | Result | Reflects later source changes? | Allows `null`? |
|--------|--------|-------------------------------|----------------|
| `List.copyOf(x)` | Immutable **copy** | ❌ No | ❌ No (throws NPE) |
| `new ArrayList<>(x)` | Mutable **copy** | ❌ No | ✅ Yes |
| `Collections.unmodifiableList(x)` | Unmodifiable **view** | ✅ **Yes** | ✅ Yes |
| `x.clone()` (array) | Mutable **copy** | ❌ No | ✅ Yes |

### Record vs Final Class

| | Record | Final class |
|---|--------|-------------|
| `equals`/`hashCode`/`toString` | ✅ generated | manual |
| Extends a class | ❌ | ✅ |
| Extra instance fields | ❌ | ✅ |
| Implements interfaces | ✅ | ✅ |
| Accessor naming | `name()` | `getName()` |
| Deep immutability | ⚠️ needs compact ctor | ⚠️ needs defensive copies |

### The Golden Rules

1. `final` freezes the **reference**, not the **object**.
2. Immutability is a property of the whole **reachable graph**.
3. `List.copyOf` for a true copy; `unmodifiableList` is only a **view**.
4. A `final` field of a **mutable** type is not immutable.
5. `java.time` over `java.util.Date`.
6. Immutability gives free **safe publication** (JLS §17.5) and a **stable** `hashCode()`.

---

## 12. Real-World Production Notes

### Where Immutability Matters in Banking / E-Commerce Systems

| Scenario | Mutable version | Immutable version |
|----------|-----------------|-------------------|
| Money / price | `double` rounding drift; shared `BigDecimal` mutated mid-calculation | `Money` value object; `BigDecimal` in minor units |
| Order / cart snapshot | Line items changed while an invoice was being generated | The order is a frozen snapshot; edits create a new version |
| Cache keys | Key mutated → entry unreachable → memory leak + misses | Immutable key → stable hash → always findable |
| Configuration | `static` map mutated by one tenant, visible to all | Immutable config object, replaced atomically |
| Event payloads | Listener mutates the event; other listeners see corrupted data | Immutable event; each listener gets the same trusted state |
| Retry / idempotency | Request object mutated between the original and the retry | Same immutable request object is replayed safely |

### Tuning Knobs & Metrics

- **Allocation rate** (Micrometer `jvm.gc.allocation.rate`) — a sudden spike after introducing
  immutability usually means a large collection is being copied per request. Look for
  `List.copyOf` on a hot path with a big list; consider structural sharing or an immutable
  persistent collection.
- **GC pause time** — immutable objects die young and are cheap to collect, so immutability often
  *improves* GC behaviour even though it allocates more.
- **JFR allocation profiling** — the fastest way to find the copy that is happening per request.
- **`jcmd GC.class_histogram`** — shows how many instances of each class exist; a huge count of a
  "value" class usually means a cache or a queue is retaining them.

### Practical Rules We Enforce in Code Review

1. Value objects (`Money`, `OrderId`, `PriceRange`) are **`final` + immutable**, always.
2. Entities (`Customer`, `Order`) are mutable, but their **identity fields are `final`** and their
   behaviour is exposed as methods, not setters.
3. Never return a mutable collection or `Date` from an accessor — copy or use `java.time`.
4. `List.copyOf` in constructors; `Collections.unmodifiableList` only for a field you already own.
5. Records with mutable components get a compact constructor that copies.
6. Static configuration maps are built once and never mutated — expose them via `Map.copyOf`.
7. Any type used as a map key must be immutable for its whole residency.

---

## 13. Follow-up Learning

### Next in this folder

- [10 · Nested, Inner and Anonymous Classes](./10-OOP-Nested-Inner-and-Anonymous-Classes.md) — why a `static` nested class is the memory-safe way to write helper types
- [11 · Advanced Design and the Object Class](./11-OOP-Advanced-Design-and-Object-Class.md) — the `Object` API, `clone()` and copy constructors
- [12 · Critical and Tricky Scenarios](./12-OOP-Critical-and-Tricky-Interview-Scenarios.md) — the `Integer` cache and the constructor trap
- [13 · Covariance, Contravariance, Invariance](./13-OOP-Covariance-Contravariance-Invariance.md) — generics, wildcards and type safety

### Related topics elsewhere

- [Java Memory Model](../02-Java-Memory-Model/01-Memory-Areas.md) — safe publication, final-field semantics and the JMM
- [Concurrency](../08-Multithreading-Concurrency/01-Thread-Basics.md) — `Atomic*`, locks and lock-free design
- [Java 11/17/21 Features](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) — records and sealed classes in depth
- [Design Patterns](../09-Design-Patterns/01-Design-Patterns.md) — the *Value Object* and *Specification* patterns

### Specifications and books

- **JLS §17.5** — final field semantics and safe publication
- **JLS §8.10.3** — record classes (Java 16)
- **JLS §8.1.1.2** — `sealed` classes and interfaces (Java 17)
- **`java.util.List.copyOf` Javadoc** — the `NullPointerException` contract
- **Effective Java** — Item 17 (value-based classes), Item 18 (defensive copies), Item 19 (avoid
  mutable components), Item 28 (prefer immutable collections)

---

**Next Chapter:** → [10 · Nested, Inner and Anonymous Classes](./10-OOP-Nested-Inner-and-Anonymous-Classes.md)
**Previous Chapter:** ← [08 · equals(), hashCode() and toString()](./08-OOP-equals-hashCode-toString.md)

*Last Updated: 2026 · Java 17+ (verified on JDK 25) · All outputs in this chapter were produced by running the code*
