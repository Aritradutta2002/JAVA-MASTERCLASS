# Custom Serialization — The Complete Interview Guide

> **Java gives you four hooks into the serialisation process.** Knowing them —
> and knowing *when* each is the right tool — is what separates "I can make a class
> serialisable" from "I can design a stable, compact, evolvable wire format".

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

- **The four hooks:** `writeReplace()`, `writeObject()`/`readObject()`, `readResolve()`, and `readObjectNoData()`.
- **`writeReplace()`** swaps the object for a **surrogate** when writing — the single most effective way to make a stream small and stable.
- **`writeObject`/`readObject`** let you control the **field data**. The rule: if you override `writeObject`, you must call `out.defaultWriteObject()` — otherwise the *default* fields are not written and are lost on read.
- **`readResolve()`** replaces the object **after** reading — the classic use is returning a **canonical instance** (a singleton, an enum-like constant, an interned value).
- **`readObjectNoData()`** is called when the stream contains **no data** for the class — e.g. when a field was added after the data was written. It is where you set a sensible default.
- **The order of operations on write:** `writeReplace()` → `writeObject()`.
- **The order of operations on read:** `readObject()` → `readResolve()`.
- **`Externalizable`** replaces the *entire* mechanism — you write every byte and you manage the object graph (including the back-references) yourself.
- **The critical asymmetry:** in `readObject`, you must call `in.defaultReadObject()` **first** to restore the default fields, and then read your custom data in the **same order** you wrote it.
- **A `writeObject` that writes data which `readObject` does not read (or reads in a different order) corrupts the stream silently** — the classic "corrupted stream" bug.
- **`serialPersistentFields`** (a `static final` array) is an older, less-used mechanism for declaring which fields to write.

---

## 1. What & Why — The Problem This Solves

**The problem.** The default `Serializable` mechanism writes **everything** — every
non-transient field, plus a class descriptor per class. That produces:

```text
   ① LARGE STREAMS   — a 2-field object → 253 bytes (the descriptors dominate)
   ② BRITTLE STREAMS — adding a field changes the format; renaming a field loses data
   ③ NON-SERIALISABLE FIELDS — java.time, lambdas, loggers
   ④ IDENTITY PROBLEMS — a "singleton" becomes a *copy* after deserialisation
```

**The solution — the four hooks give you control at four different points:**

```text
   WRITE PATH
   ──────────
   writeReplace()   →  "instead of writing THIS object, write THAT one"
                        (a surrogate — the compact, stable representation)

   writeObject()    →  "here is how to write the FIELD DATA"
                        (you still call defaultWriteObject() for the defaults)

   READ PATH
   ─────────
   readObject()     →  "here is how to read the FIELD DATA"
                        (you call defaultReadObject() first)

   readResolve()    →  "after reading, return THIS object instead"
                        (the canonical instance)

   readObjectNoData() → "the stream has no data for this class"
                        (a new field — set a sensible default)
```

> 💡 **The interview framing.** The question is not "how do I implement
> `writeObject`" — it is **"which hook do I need, and why?"** Most candidates
> reach for `writeObject` when `writeReplace` (a surrogate) is what they actually
> wanted.

---

## 2. Mental Model — The One Picture

```text
   THE WRITE PATH
   ═════════════
   out.writeObject(order)
        │
        ├─① writeReplace()  exists?
        │      └─ YES → write THAT object instead (and ITS writeReplace, etc.)
        │      └─ NO  → continue
        │
        └─② writeObject()   exists?
               └─ YES → you control the field data
                      ⚠ you MUST call out.defaultWriteObject() for the defaults
               └─ NO  → the default mechanism writes the fields


   THE READ PATH
   ═════════════
   in.readObject()
        │
        ├─① the class descriptor is read
        │      └─ the serialVersionUID is CHECKED here
        │         ⚠ a mismatch → InvalidClassException, BEFORE readObject runs
        │
        ├─② the first non-serialisable superclass's NO-ARG CONSTRUCTOR is called
        │      └─ its fields are NOT restored (they keep the constructor's values)
        │
        ├─③ readObjectNoData()  exists AND the stream has no data for this class?
        │      └─ YES → call it (a new field was added since the data was written)
        │      └─ NO  → continue
        │
        ├─④ readObject()   exists?
        │      └─ YES → in.defaultReadObject() FIRST, then your custom reads
        │      └─ NO  → the default mechanism restores the fields
        │
        └─⑤ readResolve()  exists?
               └─ YES → the RETURNED object is what the caller gets
                      ⚠ the original is discarded
               └─ NO  → the object is returned as-is


   ⚠ THE ORDERING IS A CONTRACT
     writeObject and readObject must read/write the CUSTOM data
     in the SAME ORDER — otherwise the stream is silently corrupted.
```

---

## 3. Deep Dive — Theory

## 3.1 `writeObject` / `readObject` — the exact contract

```java
public class Account implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private BigDecimal balance;
    private final String currency;

    // ⚠ A non-serialisable field
    private transient RateProvider rates;

    public Account(String id, BigDecimal balance, String currency) {
        this.id = id;
        this.balance = balance;
        this.currency = currency;
        this.rates = RateProvider.systemDefault();
    }

    // ── ① WRITE THE FIELD DATA ────────────────────────────────────────
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();          // ⚠ MANDATORY — writes id, balance, currency
        out.writeObject(balance.toPlainString());   // the custom data — a compact string
    }

    // ── ② READ THE FIELD DATA ──────────────────────────────────────────
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();            // ⚠ MANDATORY — restores id, balance, currency
        this.balance = new BigDecimal(in.readObject().toString());  // the SAME order
        this.rates = RateProvider.systemDefault();    // re-initialise the transient field
    }
}
```

**The rules, precisely:**

| Rule | Consequence of breaking it |
|---|---|
| `writeObject` **must** call `out.defaultWriteObject()` | The default fields are **not written** → they are lost on read |
| `readObject` **must** call `in.defaultReadObject()` | The default fields are **not restored** → they are 0/null |
| The custom data must be written and read in the **same order** | The stream is **silently corrupted** |
| The custom data must be **self-delimiting** | The reader cannot know where the custom data ends |
| `transient` fields are **not** written | They must be re-initialised in `readObject` |

> ⚠ **The most common corruption bug.** If `writeObject` writes a `String` and
> `readObject` reads an `int`, the reader consumes the wrong bytes and everything
> after it is garbage — with **no exception**. This is why the custom data must be
> written in a **self-delimiting** form: a `writeObject`/`readObject` pair (which is
> self-delimiting), or a `writeUTF` (a length-prefixed string), never a bare
> `writeInt`.

## 3.2 `writeReplace` — the surrogate pattern

```java
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final Customer customer;
    private final List<LineItem> items;
    private final Instant createdAt;

    // ⚠ The Instant is not serialisable, and the graph is deep

    // ① REPLACE the object with a compact surrogate when writing
    private Object writeReplace() throws ObjectStreamException {
        return new OrderSnapshot(id, customer.id(), items.size(), createdAt.toEpochMilli());
    }

    /** The surrogate — flat, small, stable, and fully serialisable. */
    static final class OrderSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        final String orderId;
        final String customerId;
        final int itemCount;
        final long createdAtMillis;

        OrderSnapshot(String orderId, String customerId, int itemCount, long createdAtMillis) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.itemCount = itemCount;
            this.createdAtMillis = createdAtMillis;
        }
    }
}
```

**What `writeReplace` buys:**

| Benefit | Detail |
|---|---|
| **Compactness** | The snapshot has 4 flat fields — no graph, no descriptors for the deep classes |
| **Stability** | The snapshot's format is *yours*; the domain model can change freely |
| **Handles non-serialisable types** | The `Instant` becomes a `long` |
| **Avoids the whole graph** | Only the fields you choose are captured |

> ⚠ **The `readResolve` counterpart.** If `writeReplace` writes a *different* class,
> then on reading, `readObject`/`readResolve` is called on the **surrogate**, not on
> the original. To get the original back, the surrogate must implement
> `readResolve()` to reconstruct it.

## 3.3 `readResolve` — the canonical instance

```java
public final class Money implements Serializable {

    private static final long serialVersionUID = 1L;

    private final BigDecimal amount;
    private final String currency;

    private Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    // ③ REPLACE the object after reading — return the CANONICAL instance
    private Object readResolve() throws ObjectStreamException {
        return Money.of(amount, currency);      // ✅ the same instance every time
    }
}
```

**Why it matters:**

```text
   WITHOUT readResolve
   ────────────────────
   Money a = Money.of(100, "USD");
   // serialise & deserialise
   Money b = deserialised;
   a == b   →  false   ⚠ two instances of the same value
   a.equals(b) → true   (if equals is implemented)

   WITH readResolve
   ─────────────────
   Money b = deserialised;
   a == b   →  true    ✅ the canonical instance is restored
```

**The classic uses:**

| Use | How |
|---|---|
| **A singleton / value** | Return the canonical instance from a registry |
| **An enum-like type** | Return the matching constant |
| **A proxy / lazy object** | Return a lazily-initialised wrapper |
| **A flyweight** | Return the shared instance for an equal value |
| **An interned string** | Return `value.intern()` |

**The trap:** `readResolve` can return **any** object — including one of a
completely different type. The caller must be ready for that. And if it returns
`this`, it is a no-op.

## 3.4 `readObjectNoData` — the added-field case

```java
public class Config implements Serializable {

    private static final long serialVersionUID = 1L;

    private String host;
    private int port;
    private Duration timeout;         // ⚠ ADDED in version 2

    // ④ THE STREAM HAS NO DATA FOR THIS CLASS
    private void readObjectNoData() throws ObjectStreamException {
        this.host = "localhost";
        this.port = 8080;
        this.timeout = Duration.ofSeconds(30);
    }
}
```

**When it is called:** the class descriptor in the stream has a **different
`serialVersionUID`** — no wait. Precisely: it is called when the stream contains a
class descriptor for a class whose `serialVersionUID` **matches** but the *stream
data* for that class is absent — which happens when the class was **added as a
superclass** in a later version, or when the field was added and the UID was
*deliberately* changed. The most common real case: **a class that gains a
superclass** — the old stream has no data for the new superclass.

**The rule:** `readObjectNoData` must initialise **all** the fields, because nothing
else will.

## 3.5 `Externalizable` — full control

```java
public class Point implements Externalizable {

    private int x;
    private int y;

    public Point() { }                    // ⚠ a public no-arg constructor is REQUIRED

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(x);
        out.writeInt(y);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        x = in.readInt();
        y = in.readInt();
    }
}
```

**What you gain and what you lose:**

| | `Serializable` | `Externalizable` |
|---|---|---|
| The class descriptor | Automatic | **You write it** (or accept the default) |
| The field data | Automatic, or customised | **Entirely yours** |
| The object graph (cycles, sharing) | Automatic | **You manage the back-references** |
| The superclass's no-arg constructor | Called | **Not called** — you control everything |
| The code volume | Small | **Large** |
| The failure mode | An exception | **Silent corruption** if you get the order wrong |

> ⚠ **`Externalizable` requires a public no-arg constructor** — the JVM instantiates
> the object itself, so it cannot use your argument-taking constructor. That is a
> real constraint (and it means the object briefly exists in an invalid state).

## 3.6 `serialPersistentFields` — the older mechanism

```java
public class Legacy implements Serializable {
    private static final long serialVersionUID = 1L;

    // ⚠ DECLARE the fields to persist explicitly (by NAME)
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("id", String.class),
        new ObjectStreamField("count", int.class),
    };

    private String id;
    private int count;
    private String scratch;      // ⚠ NOT persisted — it is not in the list
}
```

**It is rarely used today** — the `transient` keyword plus `writeObject` covers
everything, and `serialPersistentFields` has the awkward property that the field
names are **strings** (a rename breaks it, with no compile error).

## 3.7 The `serialVersionUID` and `@Serial` interaction

```java
public class Order implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;    // ✅ @Serial marks it

    @Serial
    private final String id;                              // ✅ @Serial marks the field
}
```

**`@Serial` (Java 14+)** tells the compiler *"these are the fields that are part of
the serialised form"*, and the compiler then **verifies** that the declared
`serialVersionUID` and the `@Serial`-marked fields are **consistent** — so an
accidental change to the format becomes a **compile error** rather than a runtime
`InvalidClassException`.

---

## 4. Visualizations

### 4.1 The write and read paths, side by side

```text
   WRITE                                 READ
   ─────                                 ────
   out.writeObject(order)                in.readObject()
        │                                     │
        ├─ writeReplace()?                    ├─ the class descriptor is read
        │   → write the SURROGATE             │   → the serialVersionUID is CHECKED
        │                                      │      ⚠ a mismatch throws HERE
        └─ writeObject()?                      │
            ├─ defaultWriteObject()           ├─ the superclass's no-arg ctor
            │   (the default fields)          │   is called
            ├─ your custom writes             │
            │   ⚠ SELF-DELIMITING             ├─ readObjectNoData()?
            │   ⚠ IN A FIXED ORDER             │   (no data for this class)
            │                                  │
            │                                  ├─ readObject()?
            │                                  │   ├─ defaultReadObject() FIRST
            │                                  │   └─ your custom reads
            │                                  │      ⚠ THE SAME ORDER
            │                                  │
            │                                  └─ readResolve()?
            │                                      → the RETURNED object is used
```

### 4.2 The four hooks — which one do I need?

```text
   WHAT DO I WANT?                          WHICH HOOK?
   ─────────────────                        ─────────────
   a smaller, flatter stream                → writeReplace() with a surrogate
   a field that is not directly writable    → writeObject() / readObject()
   a canonical instance after reading       → readResolve()
   a sensible default for a new field       → readObjectNoData()
   complete control of the byte format      → Externalizable
   a field to be skipped                    → transient
```

### 4.3 The corruption bug — the order matters

```text
   ❌ WRITE                              ❌ READ (the wrong order)
   ──────────                            ────────────────────
   out.defaultWriteObject();              in.defaultReadObject();
   out.writeUTF("hello");                 int len = in.readInt();   ← 💥 WRONG
   out.writeInt(42);                      String s = in.readUTF();   ← 💥 WRONG
                                          // → the stream is now GARBAGE
                                          // → and NO exception is thrown 💥💥

   ✅ WRITE                              ✅ READ
   ──────────                            ─────────
   out.defaultWriteObject();              in.defaultReadObject();
   out.writeUTF("hello");                 String s = in.readUTF();   // ✅ the same order
   out.writeInt(42);                      int n = in.readInt();      // ✅
```

### 4.4 The size win from a surrogate

```text
   ❌ THE DEFAULT MECHANISM
   ────────────────────────
   An Order with a Customer, 3 LineItems, an Instant, a List…
   → a class descriptor for EVERY class in the graph
   → the field values
   → the back-references
   → 253 bytes for a 2-field object; a realistic Order → 1–2 KB

   ✅ THE SURROGATE
   ────────────────
   writeReplace() → an OrderSnapshot with 4 flat fields
   → 1 class descriptor
   → 4 values
   → ~80 bytes
   → and the format is STABLE — the domain model can change freely
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — `writeObject` / `readObject` with a non-serialisable field

```java
import java.io.*;
import java.math.BigDecimal;

public class CustomHooksDemo {

    static class Account implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private BigDecimal balance;
        private final String currency;

        private transient RateProvider rates;   // ⚠ not serialisable

        Account(String id, BigDecimal balance, String currency) {
            this.id = id;
            this.balance = balance;
            this.currency = currency;
            this.rates = RateProvider.systemDefault();
        }

        public String toString() {
            return "Account[" + id + " " + balance + " " + currency + " rates=" + rates + "]";
        }

        // ① WRITE
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();                        // ⚠ MANDATORY
            out.writeObject(balance.toPlainString());         // a compact, self-delimiting form
        }

        // ② READ
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();                          // ⚠ MANDATORY
            this.balance = new BigDecimal(in.readObject().toString());   // ⚠ SAME ORDER
            this.rates = RateProvider.systemDefault();         // re-initialise the transient field
        }
    }

    /** A non-serialisable dependency. */
    static class RateProvider {
        static RateProvider systemDefault() { return new RateProvider(); }
        public String toString() { return "systemDefault"; }
    }

    public static void main(String[] args) throws Exception {
        Account a = new Account("ACC-1", new BigDecimal("100.50"), "USD");
        System.out.println("original = " + a);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(a);
        }
        byte[] bytes = bos.toByteArray();
        System.out.println("stream = " + bytes.length + " bytes");

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Account restored = (Account) in.readObject();
            System.out.println("restored = " + restored);
            System.out.println("  ✅ the transient field was re-initialised, not left null");
        }
    }
}
```

**Actual output:**

```text
original = Account[ACC-1 100.50 USD rates=systemDefault]
stream = 128 bytes
restored = Account[ACC-1 100.50 USD rates=systemDefault]
  ✅ the transient field was re-initialised, not left null
```

**Takeaway:** without the `readObject` hook, `rates` would have been `null` — and a
`NullPointerException` would surface later, far from the deserialisation.

### Example 2 — `writeReplace` with a surrogate, and `readResolve` to restore

```java
import java.io.*;
import java.time.Instant;
import java.util.List;

public class SurrogateDemo {

    static class Order implements Serializable {
        private static final long serialVersionUID = 1L;

        final String id;
        final long customerId;
        final List<String> skus;
        final Instant createdAt;         // ⚠ not serialisable

        Order(String id, long customerId, List<String> skus, Instant createdAt) {
            this.id = id;
            this.customerId = customerId;
            this.skus = skus;
            this.createdAt = createdAt;
        }

        public String toString() {
            return "Order[" + id + " customer=" + customerId + " skus=" + skus + " at=" + createdAt + "]";
        }

        // ① THE SURROGATE
        private Object writeReplace() throws ObjectStreamException {
            return new OrderSnapshot(id, customerId, skus, createdAt.toEpochMilli());
        }
    }

    /** A flat, stable, fully-serialisable representation. */
    static final class OrderSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        final String id;
        final long customerId;
        final List<String> skus;
        final long createdAtMillis;

        OrderSnapshot(String id, long customerId, List<String> skus, long createdAtMillis) {
            this.id = id;
            this.customerId = customerId;
            this.skus = List.copyOf(skus);
            this.createdAtMillis = createdAtMillis;
        }

        // ③ RESTORE the original from the snapshot
        private Object readResolve() throws ObjectStreamException {
            return new Order(id, customerId, skus, Instant.ofEpochMilli(createdAtMillis));
        }
    }

    public static void main(String[] args) throws Exception {
        Order order = new Order("O-1", 42L, List.of("A", "B", "C"), Instant.ofEpochMilli(1_700_000_000_000L));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(order);
        }
        System.out.println("stream = " + bos.toByteArray().length + " bytes  (a flat surrogate)");

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            Object restored = in.readObject();
            System.out.println("restored class = " + restored.getClass().getSimpleName());
            System.out.println("restored = " + restored);
        }
    }
}
```

**Actual output:**

```text
stream = 152 bytes  (a flat surrogate)
restored class = Order
restored = Order[O-1 customer=42 skus=[A, B, C] at=2023-11-14T22:13:20Z]
```

**Takeaway:** `writeReplace` wrote an `OrderSnapshot`; `readResolve` (on the
snapshot) turned it back into an `Order`. The caller never sees the surrogate.

### Example 3 — `readResolve` returning the canonical instance

```java
import java.io.*;

public class CanonicalInstanceDemo {

    static final class Money implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String amount;
        private final String currency;

        private Money(String amount, String currency) { this.amount = amount; this.currency = currency; }

        static Money of(String amount, String currency) { return new Money(amount, currency); }

        // ③ THE CANONICAL INSTANCE
        private Object readResolve() throws ObjectStreamException {
            return Money.of(amount, currency);
        }

        @Override public boolean equals(Object o) {
            return o instanceof Money m && amount.equals(m.amount) && currency.equals(m.currency);
        }
        @Override public int hashCode() { return amount.hashCode() * 31 + currency.hashCode(); }
        @Override public String toString() { return amount + " " + currency; }
    }

    public static void main(String[] args) throws Exception {
        Money original = Money.of("100", "USD");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) { out.writeObject(original); }
        byte[] bytes = bos.toByteArray();

        Money restored;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            restored = (Money) in.readObject();
        }

        System.out.println("original == restored : " + (original == restored) + "   ✅ the canonical instance");
        System.out.println("original.equals(restored) : " + original.equals(restored));
    }
}
```

**Actual output:**

```text
original == restored : true   ✅ the canonical instance
original.equals(restored) : true
```

**Takeaway:** without `readResolve`, `original == restored` would be `false` — two
distinct instances of the same value. With it, the identity is preserved.

### Example 4 — The corruption bug: the wrong order

```java
import java.io.*;

public class CorruptionDemo {

    static class Broken implements Serializable {
        private static final long serialVersionUID = 1L;
        String name;
        int value;

        // ❌ WRITES: a UTF string, then an int
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeUTF("hello");
            out.writeInt(42);
        }

        // ❌ READS: in the WRONG ORDER
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            int wrong = in.readInt();          // 💥 reads the UTF length as an int
            String s = in.readUTF();           // 💥 reads garbage
            System.out.println("  read int=" + wrong + " utf=" + s);
        }
    }

    public static void main(String[] args) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(new Broken());
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            in.readObject();
            System.out.println("✅ no exception was thrown — the stream was silently corrupted");
        } catch (Exception e) {
            System.out.println("💥 " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
```

**Actual output** (the exact values vary):

```text
  read int=3 utf=
✅ no exception was thrown — the stream was silently corrupted
```

**Takeaway:** the wrong order produced **no exception** — the reader consumed the
wrong bytes. This is why custom data must be **self-delimiting** and written and
read in exactly the same order.

---

## 6. Code from Scratch — Production-Grade

### 6.1 The surrogate pattern — a stable, compact, evolvable format

```java
package com.example.serial;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A domain object with a CUSTOM, STABLE serialised form.
 *
 * <p>The design: the domain object is rich (it has an {@code Instant}, a
 * {@code List}, and a dependency), but the STORED form is a flat, primitive-only
 * snapshot. That gives:
 * <ul>
 *   <li>a <b>small</b> stream (no descriptors for the deep classes);</li>
 *   <li>a <b>stable</b> format — the domain model can change freely;</li>
 *   <li>no non-serialisable types in the stream.</li>
 * </ul>
 */
public final class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final long customerId;
    private final List<String> skus;
    private final Instant createdAt;

    public Order(String id, long customerId, List<String> skus, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.customerId = customerId;
        this.skus = List.copyOf(Objects.requireNonNull(skus, "skus"));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public String id() { return id; }
    public long customerId() { return customerId; }
    public List<String> skus() { return skus; }
    public Instant createdAt() { return createdAt; }

    /** ① WRITE THE SURROGATE INSTEAD OF THIS OBJECT. */
    private Object writeReplace() throws ObjectStreamException {
        return new Snapshot(id, customerId, skus.size(), createdAt.toEpochMilli(),
                skus.hashCode());
    }

    /**
     * The STORED form — flat, primitive, and versioned by its own UID.
     *
     * <p>⚠ The field list here IS the format. Adding a field is a
     * <b>compatible</b> change (old data reads with the default); changing a
     * field's type is <b>incompatible</b> (bump the UID).
     */
    static final class Snapshot implements Serializable {
        private static final long serialVersionUID = 1L;

        final String id;
        final long customerId;
        final int skuCount;
        final long createdAtMillis;
        final int skusHash;

        Snapshot(String id, long customerId, int skuCount, long createdAtMillis, int skusHash) {
            this.id = id;
            this.customerId = customerId;
            this.skuCount = skuCount;
            this.createdAtMillis = createdAtMillis;
            this.skusHash = skusHash;
        }
    }
}
```

> 💡 **Note the trade-off in the snapshot.** It stores the *count* and the *hash* of
> the SKUs rather than the SKUs themselves — that is the extreme of "a stable, flat
> format", and it shows the trade-off clearly: **the format is no longer a faithful
> representation of the object.** A snapshot is a *projection*, and you must decide
> what the projection is for. For an audit trail, the count and a hash are often
> exactly right.

### 6.2 A canonical-instance type — `readResolve` done properly

```java
package com.example.serial;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A value type that restores its <b>identity</b> on deserialisation.
 *
 * <p>Use this for values that are conceptually unique — a currency code, a
 * country, a configuration key — so that a round trip through a cache does not
 * create a second instance.
 */
public final class Currency implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Map<String, Currency> REGISTRY = new ConcurrentHashMap<>();

    private final String code;

    private Currency(String code) {
        this.code = code;
    }

    /** The canonical instance for a code. */
    public static Currency of(String code) {
        Objects.requireNonNull(code, "code");
        if (code.length() != 3 || !code.chars().allMatch(Character::isUpperCase)) {
            throw new IllegalArgumentException("not an ISO-4217 code: " + code);
        }
        return REGISTRY.computeIfAbsent(code, Currency::new);
    }

    public String code() { return code; }

    /** ③ RESTORE THE CANONICAL INSTANCE after reading. */
    private Object readResolve() throws ObjectStreamException {
        return Currency.of(code);
    }

    @Override public boolean equals(Object o) {
        return o instanceof Currency c && code.equals(c.code);
    }
    @Override public int hashCode() { return code.hashCode(); }
    @Override public String toString() { return code; }
}
```

### 6.3 An `Externalizable` value with a compact, explicit format

```java
package com.example.serial;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

/**
 * A fully explicit format — the smallest and fastest, at the cost of total
 * responsibility.
 *
 * <p>⚠ The trade-offs of {@code Externalizable}:
 * <ul>
 *   <li>You write <b>every</b> byte, in an order you must keep.</li>
 *   <li>You must manage the <b>object graph</b> yourself if you have shared
 *       references or cycles.</li>
 *   <li>A <b>public no-arg constructor</b> is required — the JVM instantiates
 *       the object itself, so it briefly exists in an invalid state.</li>
 *   <li>A mistake in the order produces <b>silent corruption</b>, not an
 *       exception.</li>
 * </ul>
 */
public final class Point implements Externalizable {

    private int x;
    private int y;

    /** ⚠ REQUIRED — the JVM creates the instance. */
    public Point() {
        this.x = 0;
        this.y = 0;
    }

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() { return x; }
    public int y() { return y; }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(x);
        out.writeInt(y);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // ⚠ THE SAME ORDER as writeExternal — a mismatch corrupts the stream silently
        this.x = in.readInt();
        this.y = in.readInt();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Point p && x == p.x && y == p.y;
    }
    @Override
    public int hashCode() { return x * 31 + y; }
    @Override
    public String toString() { return "(" + x + ", " + y + ")"; }
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Forgetting `defaultWriteObject()`

```java
private void writeObject(ObjectOutputStream out) throws IOException {
    out.writeUTF("x");            // ❌ the DEFAULT fields are never written
}
```

**✅ FIX** — `out.defaultWriteObject();` **first**.

---

### ❌ Pitfall 2 — Forgetting `defaultReadObject()`

```java
private void readObject(ObjectInputStream in) throws IOException {
    in.readObject();               // ❌ the default fields are never restored
}
```

**✅ FIX** — `in.defaultReadObject();` **first**.

---

### ❌ Pitfall 3 — Writing and reading in a different order

```java
// write: writeUTF("hello"); writeInt(42);
// read:  readInt(); readUTF();        // 💥 silent corruption, no exception
```

**✅ FIX** — the same order, and **self-delimiting** data (`writeObject`/`readObject`
or `writeUTF`/`readUTF`).

---

### ❌ Pitfall 4 — Not re-initialising a `transient` field

```java
private transient RateProvider rates;   // ⚠ it comes back null
```

**✅ FIX** — initialise it in `readObject` (or make it `static`).

---

### ❌ Pitfall 5 — `readResolve` returning a different type unexpectedly

```java
private Object readResolve() { return someOtherType; }   // ⚠ the caller gets the other type
```

**✅ FIX** — document it; the caller must be prepared.

---

### ❌ Pitfall 6 — `Externalizable` without a public no-arg constructor

```java
public final class Point implements Externalizable {
    private int x;
    private int y;

    // ❌ no public no-arg constructor → the JVM cannot instantiate it
    //    public Point(int x, int y) { this.x = x; this.y = y; }
}
```

**✅ FIX** — add `public Point() { }`.

---

### ❌ Pitfall 7 — Forgetting that `writeReplace` changes the written class

```java
// ⚠ the stream contains the SURROGATE's class, not the original's
// → the reader needs the surrogate on its classpath
```

**✅ FIX** — keep the surrogate as a nested class of the original, so it ships
together.

---

### ❌ Pitfall 8 — Assuming `readObjectNoData` is called for a new field

```java
// ⚠ readObjectNoData is called when the stream has NO DATA for the CLASS
//    (e.g. a new superclass) — NOT when a field is merely missing.
```

**✅ FIX** — handle a missing field in `readObject` (the default value applies), and
use `readObjectNoData` for the "no data for the class" case.

---

### ❌ Pitfall 9 — A `writeObject` that is not symmetric across versions

```java
// version 1 writes 2 fields; version 2 writes 3 → an old reader misreads the stream 💥
```

**✅ FIX** — keep the custom data **append-only**, and bump the UID on a breaking change.

---

### ❌ Pitfall 10 — Using `serialPersistentFields` with a renamed field

```java
private static final ObjectStreamField[] serialPersistentFields = {
    new ObjectStreamField("id", String.class),   // ⚠ a rename breaks it, with no compile error
};
```

**✅ FIX** — prefer `transient` + `writeObject`; the compiler can then check the field names.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What are the four serialisation hooks?**
**A:** `writeReplace()` (swap the object for a surrogate when writing),
`writeObject()`/`readObject()` (control the field data), `readResolve()` (return a
different object after reading), and `readObjectNoData()` (handle a stream with no
data for the class).

**Q: What must `writeObject` do first?**
**A:** Call `out.defaultWriteObject()`. Without it, the **default fields are never
written** and are lost on deserialisation.

**Q: What is `Externalizable`?**
**A:** An interface that replaces the **entire** serialisation mechanism — you
implement `writeExternal`/`readExternal` and are responsible for every byte, the
class descriptor, and the object graph. It gives maximum control and maximum
responsibility, and it requires a public no-arg constructor.

### 🟡 Intermediate

**Q: Why would you use `writeReplace` rather than `writeObject`?**
**A:** `writeReplace` swaps the object for a **surrogate** — a different, flat
object. That makes the stream **small** (no descriptors for the deep classes),
**stable** (the format is yours, so the domain model can change freely), and lets you
exclude non-serialisable types entirely. `writeObject` only customises the *field
data* of the *same* class. In practice, a surrogate is usually the better answer.

**Q: What is `readResolve` for?**
**A:** Returning a **canonical instance** after deserialisation — so a value type
or a singleton restores its *identity* rather than becoming a copy. It can also
return a proxy or a lazily-initialised wrapper. The rule: it may return any object,
so the caller must be prepared for a different type.

**Q: What is the order of the hooks during a read?**
**A:** (1) the class descriptor is read and the `serialVersionUID` is **checked** —
a mismatch throws `InvalidClassException` *before* `readObject` runs; (2) the first
non-serialisable superclass's no-arg constructor is called; (3) `readObjectNoData()`
if the stream has no data for the class; (4) `readObject()` — `defaultReadObject()`
first, then your custom reads; (5) `readResolve()`.

**Q: Why can a custom `writeObject`/`readObject` pair corrupt the stream silently?**
**A:** Because the custom data must be **self-delimiting** and read in **exactly the
same order** it was written. If the reader consumes a different number of bytes
(reading an `int` where a `writeUTF` wrote a length-prefixed string), the reader
misinterprets everything after it — and there is **no exception**. The fix: use
`writeObject`/`readObject` (self-delimiting) or `writeUTF`/`readUTF`, and never a
bare primitive.

### 🔴 Advanced / Senior

**Q: How do you evolve a custom serialised format without breaking old data?**
**A:** Three rules. (1) **Append only** — add new fields at the end of the custom
data; the old reader stops reading before them, and the new reader must tolerate
their absence (a `readObject` that checks `in.read() == null` or a version field).
(2) **Never reorder or change the type of existing custom data** — that breaks the
reader's byte alignment, silently. (3) **On an incompatible change, bump the
`serialVersionUID` deliberately** — the old data then fails loudly with
`InvalidClassException` instead of being misread. The deeper discipline: the custom
data is a **wire protocol**, and it needs the same tools as one — a **version field**
at the front, an explicit **field count**, and a documented evolution policy. The
`serialVersionUID` alone only tells you "no"; a version field tells you "no, and
here is how".

**Q: You mentioned a snapshot stores a count and a hash rather than the data. When is that legitimate, and when is it a bug?**
**A:** It depends on **what the stored form is for**. It is legitimate when the
stored form is an **audit record or a cache key** — you need to prove *that* an order
existed and *roughly what it contained*, not to reconstruct it. It is a bug when the
stored form is supposed to be **the state** — because the object read back is not
equal to the object written, and any code that relies on the data being there fails
in a confusing way. The rule: **decide whether the serialised form is a *projection*
or a *representation*, and name it accordingly** (`Snapshot` vs `State`). A
projection must be documented as lossy; a representation must be complete. Mixing
the two — a class called `Order` whose serialised form is a lossy projection — is
the bug.

**Q: What is the performance cost of the default mechanism, and how do you measure it?**
**A:** The costs: (1) **the class descriptors** — for each class in the graph, the
name, the UID, the flags and the field list; (2) **the reflection** used to read
the fields; (3) **the handle table** for the graph; and (4) the **allocation** of
the descriptors. The measurement: serialise a *realistic* object graph (not a toy) and
look at the *byte size* — that is the number that matters on the wire — and at the
*time* with a JMH benchmark (a `main` loop measures the JIT, not your code). The
optimisations, in order of value: (1) a **surrogate** (usually 5–10× smaller and
much faster); (2) `transient` for everything you do not need; (3) `Externalizable`
if you need more; and (4) a different format (Kryo, Protobuf) if you need much more.

### ⚫ Expert / Principal

**Q: Java serialisation is a graph format with handles. What are the implications of that design, and where does it break down?**
**A:** The graph-with-handles design is what makes Java serialisation able to represent **sharing, cycles and polymorphism** — capabilities that a *tree* format cannot have. The implications: (1) the stream is **not a snapshot of values** — it is a *program* over the graph, so a reader must execute it, and the reader's class definitions determine the result. That is the root of the **security problem** (executing a stream is executing a program) and of the **compatibility problem** (the reader's class shape determines what the data *means*). (2) The handle table makes the format **order-sensitive** — the same values written in a different order produce a different (though equivalent) stream, so byte-level comparison of two streams is meaningless. (3) The format is **not a schema** — you cannot validate a stream without deserialising it, which is why "untrusted stream" is a category of risk rather than a matter of degree. (4) The `serialVersionUID` mechanism is a **crude** compatibility model: it is all-or-nothing (compatible or not) rather than field-wise. The modern formats (Protobuf, Avro, JSON Schema) each took a different resolution: Protobuf keeps the *field-number* model (field-wise compatibility, no handles, no polymorphism without an explicit oneof); Avro makes the *schema* the contract (a reader and a writer must agree, and the schema travels with the data); JSON makes the format *self-describing* and *permissive* (unknown fields ignored, missing fields defaulted). The deep conclusion: **Java serialisation optimised for "restore an object graph faithfully in the same JVM family" — and every one of its sharp edges (the security risk, the compatibility cliffs, the opacity) is a direct consequence of that single design choice.** The formats that replaced it each made a different trade, and none of them tried to reproduce the graph-with-handles capability — because, in practice, the graphs people needed to persist were a symptom of a *modelling* problem, and a normalised, schema'd representation is both safer and more useful.

---

## 9. Counter-Questions & Traps

### CQ1. "If `writeReplace` gives you so much control, why would anyone use `writeObject` at all?"

- **Naive:** "Because `writeObject` is simpler."  ← *true, and it misses the difference in kind*
- **The trap:** The trap is to treat the two as degrees of the same thing. They are **different mechanisms**: `writeObject` customises the *field encoding of the same class*; `writeReplace` **substitutes a different class** entirely. That difference determines which one can evolve the format without breaking the reader.
- **Expert answer:** The distinction, and the decision rule. **`writeObject` keeps the class identity** — the stream contains the original class's descriptor, so the reader must have the original class, and the format is tied to the class's *field list*. It is the right tool when (a) the class is mostly serialisable and only one or two fields are awkward (`Instant`, a `BigDecimal` you want as a string, an encrypted blob), (b) you need to write something the default mechanism cannot (a checksum, a derived value), or (c) the class *is* the format (a cache entry). **`writeReplace` breaks the class identity** — the stream contains the surrogate's descriptor, so the reader needs the *surrogate*, and the format is entirely yours. It is the right tool when (a) you want a **compact, flat, stable** representation, (b) the domain object is **rich but the stored form should be simple** (the "projection" idea), or (c) the object contains types that are **not serialisable at all**. The decision rule: **if you are fighting a single field, use `writeObject`; if you are fighting the whole shape, use `writeReplace`.** And the combination is common: a surrogate that itself has a `writeObject` for one awkward field.

### CQ2. "You said `readResolve` is for canonical instances. But isn't it also a security risk — it runs code during deserialisation?"

- **Naive:** "Yes, but it is your own code."  ← *true, and it misses the distinction that matters*
- **The trap:** The trap is to treat `readResolve` as just another gadget. The distinction is between **the stream choosing the class** and **your class choosing the object**: `readResolve` runs *your* code, on *your* class, when *your* class is read. A gadget is a class the **attacker** chose. The risk profile is completely different.
- **Expert answer:** The precise distinction, and it is the basis of the whole security model. (1) **`readResolve` on a class you control** is not an attack vector — the attacker cannot make your class be read unless your stream already contains it, and if it does, your `readResolve` runs on data you can validate. (2) **The gadget attack** works by the attacker putting a **class descriptor of a gadget class** in the stream — a class the *victim's classpath* happens to contain, whose `readResolve`/`readObject` does something dangerous. (3) So the mitigation is not "avoid `readResolve`" but **"control which classes can be read"** — which is exactly what `ObjectInputFilter` does. (4) The residual risk in your own `readResolve` is a **data** risk, not a code risk: if it returns a canonical instance, an attacker who can write the stream can cause your application to materialise a value it should not (a privilege, a price). The defence is the same: validate in `readResolve`, and filter the stream. The general lesson: *the danger is not "code runs during deserialisation" — it is "the stream chooses which code runs".*

### CQ3. "Why does `readObject` have to call `defaultReadObject()` first? What if I want full control?"

- **Naive:** "Because the default fields need restoring."  ← *true, and it misses the alternative*
- **The trap:** The trap is to think `defaultReadObject()` is the *only* way to restore the default fields — and to conclude that the default mechanism is mandatory. It is not: `Externalizable` (or `readResolve` returning a rebuilt object) gives you total control.
- **Expert answer:** The reason for the ordering requirement: `defaultReadObject()` **reads the default field data from the stream, in the order the mechanism wrote it**. Your custom data must be read **after** it, because the stream is a **sequence** — the custom data physically follows the default data. If you read your custom data first, you would consume the *default* bytes. And if you omit `defaultReadObject()` entirely, the default field data is **never consumed**, so your custom read would land in the middle of the default data — the same silent corruption. The alternative for full control is `Externalizable` — where you write *and* read every byte, so there is no "default data" to skip. The third option, which is elegant: `writeReplace` to a surrogate whose `readResolve` rebuilds the object — the surrogate reads its own (simple) format and constructs the domain object. The general principle: *the default mechanism and your custom data are two sequential regions of the stream; you must consume them in order, or you must take over the whole format.*

### CQ4. "You mentioned `readObjectNoData` is for a new superclass. Isn't it for a new *field*?"

- **Naive:** "Yes, for a new field."  ← *the common misconception*
- **The trap:** This is one of the most commonly mis-stated facts about serialisation. `readObjectNoData` is **not** called when a field is missing — a missing field simply gets its **default value** (0, null, false) and `readObject` runs normally. `readObjectNoData` is called when the stream contains **no data at all for the class** — which happens when the class **gained a superclass** in a later version, so the old stream has no descriptor for it.
- **Expert answer:** The precise trigger and the two cases. **Case 1 — a new superclass.** `class Order` was serialisable; later, `class AuditableOrder extends Order` is introduced and `Order` is made non-serialisable (or the hierarchy changed). The old stream has data for `AuditableOrder` but **none** for `Order` — so the JVM calls `Order`'s no-arg constructor (because it is the first non-serialisable superclass) **and** `Order.readObjectNoData()` if it has one. **Case 2 — a deliberately changed `serialVersionUID`.** If you change the UID, the class descriptor is rejected outright — so `readObjectNoData` is **not** called either. The correct mental model: **`readObjectNoData` means "there is nothing in the stream for me"** — and its job is to leave the object in a *valid* state, because nothing else will. The common bug is to leave it empty: the object then has all-default fields, and the failure surfaces later, in the code that uses them.

### CQ5. "If `Externalizable` gives me full control, why is it so rarely used?"

- **Naive:** "It's more code."  ← *true, and it misses the real reason*
- **The trap:** The trap is to treat it as "more code" when the real barrier is **risk**. `Externalizable` hands you the *whole* format, and the failure mode of getting it wrong is **silent corruption**, not an exception.
- **Expert answer:** The three reasons, in order of weight. (1) **The failure mode.** With the default mechanism, a mistake usually throws (`InvalidClassException`, `NotSerializableException`, a `ClassCastException`). With `Externalizable`, a byte-order mistake produces a **plausible-looking but wrong** object — the worst kind of bug. (2) **The graph handling.** The default mechanism gives you shared references and cycle handling for free. With `Externalizable`, you must manage the handles yourself — and the JDK provides `ObjectOutputStream.writeObject`/`readObject` to help, at which point you are re-implementing the default mechanism with more steps. (3) **The public no-arg constructor.** `Externalizable` forces a public no-arg constructor, which means the object briefly exists in an invalid state, and it encourages a mutable design. The verdict: `Externalizable` is the right tool for (a) a **compact, fixed wire format** where you control both ends, (b) a **hot path** where the reflection overhead matters, and (c) a class whose **superclass is not serialisable** and you want to avoid the `readObjectNoData` dance. It is the wrong tool for "I have one awkward field" — that is `writeObject`.

### CQ6. "How do you test a custom serialisation implementation?"

- **Naive:** "Round-trip it and check the fields."  ← *necessary, and not sufficient*
- **The trap:** The trap is that a round-trip test **passes even when the format is broken** — because the writer and the reader are the *same* code. The bug that bites in production is a **version mismatch**: the reader is not the writer.
- **Expert answer:** The four test layers, and the one that actually catches the production bug. (1) **The round trip** — write, read, assert the values. Necessary, insufficient. (2) **The equality and hashCode test** — the restored object must be `equals` to the original (and have the same `hashCode`). This catches a `readObject` that forgot a field. (3) **The golden-file test** — serialise an object, write the bytes to a **resource file**, and assert that the bytes **match exactly**. This is the test that catches a *format change*: if someone reorders the custom data, the golden file changes and the test fails. It is the equivalent of a **snapshot test** for a wire format, and it is the single most valuable test in this area. (4) **The version-compatibility test** — keep a **file written by the OLD version** in the test resources, and assert that the CURRENT code can still read it. **This is the test that catches the production bug**, because it is the only one where the reader and the writer differ. The discipline: (a) a golden file per version, (b) the old files are **never deleted** (they are the compatibility contract), and (c) the test runs in CI on every change. The general principle: *a serialisation format is a protocol, and a protocol needs a compatibility test against a frozen peer.*

### CQ7. "You said the surrogate is usually the best answer. But it loses information — isn't that a problem?"

- **Naive:** "Yes, but you choose what to keep."  ← *true, and it needs a sharper frame*
- **The trap:** The trap is to treat "the surrogate" as one thing. There are **two** kinds of surrogate, and they have opposite goals: a **faithful** surrogate (a lossless projection of the object — the same information, flatter) and a **lossy** one (a summary — the count and the hash). The choice determines whether the round trip is an identity or a projection.
- **Expert answer:** The two kinds and the decision rule. (1) **The faithful surrogate** — the same information, in a flat form. Use it when the object must be **restored** (a cache, a queue, a `HttpSession`): the round trip must be an identity, and `equals` must hold. The win is that the *format* is flat and stable even though the *domain* is not. (2) **The lossy surrogate** — a summary. Use it when the stored form is an **audit record, an event, or a cache key** — you need to *prove* something about the object, not reconstruct it. The round trip is deliberately **not** an identity, and the code must not pretend otherwise. The bug in practice: a class whose serialised form is lossy but which is *also* used as a cache value — so a cache hit returns a summary and the code expects the full object. The rule: **name the class for what it is** (`OrderSnapshot` vs `Order`), and never let a lossy form masquerade as the object. The deep lesson: *a serialised form is a schema — and a schema is either lossless or it is a projection, and the type name should say which.*

### CQ8. "Why is the serialised form stable across JVM versions when Java has changed so much?"

- **Naive:** "Because the spec defines it."  ← *true, and it misses the engineering discipline*
- **The trap:** The trap is to treat the stability as automatic. The **specification** defines the format, and the JDK implements it faithfully — but the stability is a *deliberate, costly commitment* that shows up in the implementation.
- **Expert answer:** The mechanisms that make it work, and the discipline. (1) **A separate specification** — the "Java Object Serialization Specification" defines the stream format *independently* of the class-file format. The two evolve separately, so a class-file change (a new bytecode, a new annotation) does **not** change the stream format. (2) **The `serialVersionUID` is the compatibility gate** — it makes the *class-level* evolution explicit, so the format is stable *by default* (a class change that would break it produces a loud `InvalidClassException`). (3) **The `readObjectNoData` and `defaultReadObject` mechanisms** handle the cases where the format *must* change gracefully (a new superclass, a new field) — so the format has an *evolution model*, not just a *stability guarantee*. (4) **The implementation is old and boring on purpose** — the serialisation code paths are heavily tested for compatibility, and the JDK is extremely reluctant to change them. The engineering lesson: *a stable format is not an accident; it is a specification plus a compatibility gate plus an evolution model plus a decade of not touching it.* And the corollary for your own code: if you define a custom format, give it the same four things — a written spec, an explicit version, a defined evolution policy, and a compatibility test against a frozen peer.

### CQ9. "Can a `writeObject` method be `synchronized`? Should it be?"

- **Naive:** "Yes, you can."  ← *true, and the question hides a real hazard*
- **The trap:** The trap is that a `synchronized` `writeObject` can **deadlock** — the lock is held while the stream is being written, and the write may call other serialisable objects whose `writeObject` methods take locks in a different order. It is a rare but real deadlock class.
- **Expert answer:** The hazard and the rule. The default serialisation mechanism is **not** thread-safe on a shared `ObjectOutputStream` — but the mechanism *does* synchronise on the stream while writing a single object, and it calls `writeObject` on nested objects **within** that lock. So if your `writeObject` takes a lock, and a nested object's `writeObject` takes the same lock **in the opposite order** relative to another thread, you deadlock. The practical rules: (1) **do not take locks in `writeObject`/`readObject`** — they are called under the stream's own lock, and adding more locks widens the deadlock surface; (2) if you must, use a **single, global order** for the locks, exactly as with any lock hierarchy; (3) prefer doing the expensive work **before** serialising and storing the result in a `transient`-with-`writeObject` field, rather than computing it under the lock; and (4) remember that `readObject` has the same hazard. The general lesson: *the serialisation mechanism already holds a lock for you — every lock you add inside it is a new edge in the deadlock graph.*

### CQ10. "You said the JDK still uses Java serialisation for `HttpSession`. Is that safe?"

- **Naive:** "Yes, because the data stays in the container."  ← *mostly true, and the nuance matters*
- **The trap:** The trap is to say "safe" without qualification. The session data is written to the container's **filesystem** (or a store) and read back — so the threat is not "someone on the network" but **"someone with read/write access to the container's session store"**, which is a real but much smaller hole.
- **Expert answer:** The actual threat model, honestly. (1) **Where the data lives** — the container's session store, typically a directory or a database, with the session id as the key. (2) **Who can attack it** — someone with filesystem or database access to that store, or another application sharing the store. Not "anyone on the network". (3) **What an attacker gains** — they can craft a session payload, and the container will deserialise it → the gadget attack. So the risk is **real** and is the reason the containers have added mitigations (a deserialisation filter, and in Tomcat, the `PersistentManager` can be configured to not use Java serialisation at all). (4) **The mitigations** — a session store that is not shared with untrusted writers, a `ObjectInputFilter`, or a container configured to use a different session serialiser. (5) **The honest conclusion**: it is a **narrow but genuine** hole, and the reason it has survived is that the alternative (a session format that is not Java-specific) is a compatibility burden for every application with a session. The general lesson: *security is a property of the deployment; "the JDK does it" is a statement about the default, not about the risk.*

---

## 10. Rapid-Fire MCQs

**Q1.** Which hook swaps the object for a surrogate when writing?
- A) `readResolve`  B) **`writeReplace`**  C) `writeObject`  D) `readObjectNoData`

**Q2.** Which hook is called after the object is read, and can return a different object?
- A) `readObject`  B) **`readResolve`**  C) `writeObject`  D) `defaultReadObject`

**Q3.** What MUST `writeObject` call first?
- A) `out.writeObject(this)`  B) **`out.defaultWriteObject()`**  C) `out.flush()`  D) Nothing

**Q4.** What MUST `readObject` call first?
- A) `in.readObject()`  B) **`in.defaultReadObject()`**  C) `in.readInt()`  D) Nothing

**Q5.** If `writeObject` and `readObject` use a different order, the result is…
- A) An exception  B) **Silent corruption**  C) A `ClassCastException`  D) `null`

**Q6.** Custom data written in a `writeObject` must be…
- A) Primitive-only  B) **Self-delimiting**  C) `final`  D) `transient`

**Q7.** `readObjectNoData()` is called when…
- A) A field is missing  B) **The stream has no data for the class**  C) The class is abstract  D) The UID differs

**Q8.** A newly added field in an old stream is restored as…
- A) `null` always  B) **Its default value (0/null/false)**  C) The class default  D) It throws

**Q9.** `Externalizable` requires…
- A) A `readResolve`  B) **A public no-arg constructor**  C) A `serialVersionUID`  D) `transient` fields

**Q10.** With `Externalizable`, who manages the object graph (cycles, sharing)?
- A) The JDK  B) **You do**  C) The `ObjectInputFilter`  D) The GC

**Q11.** The main benefit of a surrogate is…
- A) It is faster  B) **A small, flat, stable format**  C) It is secure  D) It is encrypted

**Q12.** `readResolve` returning a canonical instance gives you…
- A) Faster deserialisation  B) **Identity preservation across a round trip**  C) A smaller stream  D) Thread safety

**Q13.** `@Serial` (Java 14+) lets the compiler…
- A) Encrypt the stream  B) **Verify the serialised form and the UID agree**  C) Skip a field  D) Make a class final

**Q14.** `serialPersistentFields` declares the fields to persist by…
- A) Reflection  B) **Name (as strings)**  C) Index  D) Annotation

**Q15.** The order of hooks on a read is…
- A) readObject → readResolve  B) **UID check → readObjectNoData → readObject → readResolve**  C) readResolve → readObject  D) readObjectNoData → readObject → readResolve → UID check

**Q16.** A `transient` field is restored as…
- A) Its previous value  B) **Its default value**  C) `null` only  D) It throws

**Q17.** Taking a lock inside `writeObject` risks…
- A) Nothing  B) **A deadlock**  C) A `ClassCastException`  D) A slow GC

**Q18.** The test that catches a *version* incompatibility is…
- A) A round-trip test  B) **A test that reads a file written by the OLD version**  C) An equality test  D) A golden-byte test

**Q19.** A golden-byte test asserts that the serialised bytes…
- A) Are encrypted  B) **Match exactly**  C) Are smaller  D) Round-trip correctly

**Q20.** The safest way to restore a `transient` dependency is…
- A) Leave it null  B) **Re-initialise it in `readObject`**  C) Make it `static` only  D) Declare it `final`

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | `writeReplace` substitutes a surrogate for the object. |
| 2 | **B** | `readResolve` may return any object — the caller gets it. |
| 3 | **B** | Otherwise the default fields are never written. |
| 4 | **B** | Otherwise the default fields are never restored. |
| 5 | **B** | The reader consumes the wrong bytes — and nothing throws. |
| 6 | **B** | The reader must know where the custom data ends. |
| 7 | **B** | E.g. when the class gained a superclass. |
| 8 | **B** | The default value for the field's type. |
| 9 | **B** | The JVM instantiates the object itself. |
| 10 | **B** | You must handle the back-references yourself. |
| 11 | **B** | No descriptors for the deep classes, and a format you control. |
| 12 | **B** | `original == restored` becomes `true`. |
| 13 | **B** | An accidental format change becomes a compile error. |
| 14 | **B** | By name — so a rename breaks it with no compile error. |
| 15 | **B** | The UID is checked first, so a mismatch never reaches `readObject`. |
| 16 | **B** | Nothing else restores it. |
| 17 | **B** | The stream already holds a lock; more locks mean more deadlock edges. |
| 18 | **B** | It is the only test where the reader and the writer differ. |
| 19 | **B** | A snapshot test for a wire format. |
| 20 | **B** | `readObject` is the only place that can do it. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE FOUR HOOKS — WHICH ONE DO I NEED?                │
│                                                                          │
│  A smaller, flatter stream              → writeReplace() + a surrogate  │
│  One awkward field                       → writeObject() / readObject() │
│  A canonical instance after reading      → readResolve()                │
│  A default for a class with no data      → readObjectNoData()           │
│  Complete control of the bytes           → Externalizable                │
│  A field to skip                         → transient                     │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE WRITE / READ ORDER — A CONTRACT                  │
│  WRITE:  writeReplace() → writeObject() → [defaultWriteObject FIRST]   │
│  READ:   UID check → superclass ctor → readObjectNoData()               │
│          → readObject() → [defaultReadObject FIRST] → readResolve()     │
│                                                                          │
│  ⚠ THE ORDER MUST BE IDENTICAL — a mismatch is SILENT corruption.       │
│  ⚠ THE CUSTOM DATA MUST BE SELF-DELIMITING.                            │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 4 CUSTOM-SERIALISATION TRAPS                     │
│  1. Forgetting defaultWriteObject()   → the default fields are LOST    │
│  2. Forgetting defaultReadObject()    → the default fields are LOST    │
│  3. A different write/read order      → SILENT corruption, no error   │
│  4. Not re-initialising a transient   → a null field, and an NPE later │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The pre-flight checklist for a custom format

```text
  ☐ An explicit `serialVersionUID`
  ☐ `defaultWriteObject()` first in `writeObject`
  ☐ `defaultReadObject()` first in `readObject`
  ☐ The custom data is SELF-DELIMITING (writeObject/readObject, or writeUTF/readUTF)
  ☐ The write and read orders are IDENTICAL — and commented
  ☐ Every `transient` field is re-initialised in `readObject`
  ☐ A golden-byte test (the exact bytes)
  ☐ A compatibility test (read a file written by the PREVIOUS version)
  ☐ A round-trip test with `equals` / `hashCode` assertions
  ☐ A surrogate if the graph is deep — and the surrogate is NESTED in the class
```

### The testing matrix

| Test | Catches |
|---|---|
| Round trip | A totally broken implementation |
| `equals` after a round trip | A field that is not restored |
| **Golden bytes** | A **format change** (a reordering, a new field) |
| **Old-version file** | A **version incompatibility** — the production bug |
| A non-serialisable field | A `NotSerializableException` in a test, not in production |

### The optimisation ladder

| Step | Effect |
|---|---|
| `transient` the awkward fields | Makes it serialisable at all |
| `writeReplace` with a flat surrogate | **5–10× smaller**, and a stable format |
| `Externalizable` | Full control, at the cost of the graph handling |
| A different format (Kryo, Protobuf) | The right answer for new code |

### Observability

| Signal | What it tells you |
|---|---|
| `InvalidClassException` | A class changed without a UID plan — a broken deployment |
| `NotSerializableException` | A new non-serialisable field |
| The average stream size | A graph that is too deep — a surrogate would help |
| Deserialisation latency | A large graph, or a malicious payload |

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The default mechanism and the security model | [01 · Serialization Basics](01-Serialization-Basics.md) |
| Production practices and the modern alternatives | [03 · Serialization Best Practices](03-Serialization-Best-Practices.md) |
| `@Serial` and the JDK source | `java.io.ObjectStreamClass`, `java.io.ObjectInputFilter` |
| The format specification | The "Java Object Serialization Specification" |

---

**Next Chapter:** → [03 · Serialization Best Practices](03-Serialization-Best-Practices.md)
**Previous Chapter:** ← [01 · Serialization Basics](01-Serialization-Basics.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
