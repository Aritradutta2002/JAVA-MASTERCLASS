## 📋 Table of Contents

- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — The Keyword Toolkit](#2-mental-model--the-keyword-toolkit)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The `final` Keyword](#31-the-final-keyword)
  - [3.2 The `static` Keyword](#32-the-static-keyword)
  - [3.3 The `transient` Keyword](#33-the-transient-keyword)
  - [3.4 The `volatile` Keyword](#34-the-volatile-keyword)
  - [3.5 Other Important Keywords](#35-other-important-keywords)
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

- **`final`** on a **variable** = the reference cannot be reassigned (the *object* it points to can
  still change). On a **method** = it cannot be overridden. On a **class** = it cannot be extended.
- **`final` is not "immutable"** — `private final List<String> l = new ArrayList<>()` is still
  mutable through `l.add(...)`. Immutability needs defensive copies too.
- **`static`** = belongs to the **class**, not the instance. Shared across all instances, one copy
  per classloader, and it **cannot be overridden** (only hidden).
- **A `static` method has no `this`** — it cannot access instance state, and it is resolved at
  compile time by the *reference* type.
- **`transient`** = skip this field during **default** serialization. It still appears if you write
  it yourself via `writeObject`.
- **`volatile`** = **visibility only**, never atomicity. `count++` is read-modify-write and is
  **not** made atomic by `volatile` — use `AtomicInteger` or a lock.
- **`volatile` + double-checked locking** is the classic correct use: it prevents another thread
  from seeing a *partially constructed* object.
- **Class initialisation order**: superclass → `static` fields → `static` blocks → (per instance)
  instance fields → instance blocks → constructor.
- **`static final` primitives/Strings are inlined by `javac`** — changing the constant at runtime
  has no effect, and it costs you the ability to change the algorithm later.
- **`strictfp` is a no-op since Java 17** (JEP 306); **`sealed`/`permits`** (Java 17) closes a
  hierarchy so the compiler can exhaustiveness-check your `switch`.

---

## 1. What & Why — The Problem This Solves

### The problem

Java gives you a handful of *modifier* keywords. Each is a small word, but each carries a
**contract** about mutability, sharing and concurrency. Candidates usually learn them individually
— "`static` means class-level" — and then fail when the question is *"why does this matter?"*

The real interview question is never "what does `static` do". It is:

- *"You marked the field `static`. What breaks at runtime?"*
- *"Is `final` enough to make this object immutable?"*
- *"Will `volatile` make my counter correct?"*
- *"What is the order these run in?"*

### The solution

Treat every modifier as a **constraint on a contract**, and learn the mechanism behind it:

| Keyword | Contract it enforces | Mechanism |
|---------|----------------------|-----------|
| `final` field | "This reference never changes" | Compile-time check + JMM final-field freeze |
| `final` method | "This behaviour never changes" | Compile-time check + JIT devirtualisation |
| `final` class | "This type never gains subtypes" | Compile-time check |
| `static` | "This belongs to the class" | Stored in the `Class` object, one copy per classloader |
| `transient` | "This is not part of the persisted state" | `ObjectOutputStream` skips the field |
| `volatile` | "Other threads always see the latest value" | JMM happens-before edge on every read/write |

### The analogy

Think of a **bank account**:
- `final` = the account number embossed on the card; it never changes, but the *balance* does.
- `static` = the bank's *branch hours* — one shared schedule, not per customer.
- `transient` = your *current ATM withdrawal* — real, but not part of your permanent statement.
- `volatile` = the *display board* in the lobby — always current, but two people reading and
  writing it at once still race.

---

## 2. Mental Model — The Keyword Toolkit

```text
┌──────────────────────────────────────────────────────────────────────┐
│                   WHERE DOES THE MEMBER LIVE?                        │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   INSTANCE (per object)              CLASS (per Class object)        │
│   ┌──────────────────────┐          ┌──────────────────────┐        │
│   │ normal field         │          │ static field         │        │
│   │ instance method      │          │ static method        │        │
│   │ final instance field │          │ static block         │        │
│   │  → per-object freeze │          │ static final field   │        │
│   │                      │          │  → per-class, inlined│        │
│   └──────────────────────┘          └──────────────────────┘        │
│            │                                  │                     │
│            └──────────┬───────────────────────┘                     │
│                       ▼                                             │
│              POLYMORPHISM?                                          │
│              instance method  → YES (overridable)                    │
│              static method    → NO  (hidden)                         │
│              final method     → NO  (overridable-but-locked)         │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│              AND WHAT DOES THE COMPILER DO WITH IT?                  │
├──────────────────────────────────────────────────────────────────────┤
│  final field      → freeze the reference (assignment is an error)   │
│  final method     → no vtable entry needed → JIT can inline         │
│  static final     → ConstantValue in the class file → INLINED       │
│  transient        → skipped by ObjectOutputStream                   │
│  volatile         → adds a happens-before edge on read AND write    │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### The one-sentence mental model

> **`static` decides *where* a member lives; `final` decides *whether it can change*;
> `volatile` decides *who can see the change*; `transient` decides *whether it is saved*.**

---

## 3. Deep Dive — Theory

### 3.1 The `final` Keyword

#### final Variable

```java
final int MAX = 100;  // Constant, cannot reassign
final List<String> list = new ArrayList<>();
list.add("item");  // ✅ Can modify object
// list = new ArrayList<>();  // ❌ Cannot reassign reference
```

**The critical distinction:** `final` freezes the **reference**, not the **object**.

```text
   final List<String> list = new ArrayList<>();
   ─────  ──────────  ────────────────────
    │        │              │
    │        │              └── the OBJECT: still mutable. list.add("x") works.
    │        │
    │        └── the REFERENCE: frozen. list = new ArrayList<>() is a compile error.
    │
    └── the MODIFIER
```

#### final Method

```java
class Parent {
    final void display() {}  // Cannot be overridden
}
```

A `final` method can still be **overloaded** (different parameter list) and can still be **called** —
it just cannot be replaced by a subclass. This is the "template method" anchor: freeze the critical
steps of an algorithm, leave the rest overridable.

#### final Class

```java
final class Immutable { }  // Cannot be extended
// String, Integer, wrapper classes are final
```

#### final and the JMM — Safe Publication (the senior-level point)

A `final` field has a **special memory-model guarantee** a plain field does not (JLS §17.5): the
thread that **wrote** the `final` field has a happens-before edge with every thread that **reads**
it, and the value is **frozen** at the end of the constructor. A non-final field has no such
guarantee — the publishing thread's write may be reordered after the reference write, so a reader
can see a default value. This is why immutable objects need no `volatile` or synchronisation to be
safely shared, and it is the single strongest argument for immutability (see chapter 09).

Making a class `final` is one of the two ways to guarantee immutability (the other is `sealed` +
no mutable state). It closes the "subclass adds a mutable field" hole.
### 3.2 The `static` Keyword

#### static Variable (Class variable)

```java
class Counter {
    static int count = 0;  // Shared across all instances
    Counter() { count++; }
}
```

#### static Method (Class method)

```java
class Utility {
    static int add(int a, int b) { return a + b; }
    // Cannot access instance members directly
    // Cannot use this, super
}
```

#### static Block

```java
class Database {
    static final String url = "jdbc:h2:mem:test";   // the connection string
    static Connection conn;
    static {
        conn = DriverManager.getConnection(url);  // Runs once on class load
    }
}
```

A `static` block is the only way to run code at *class initialisation* time. Because class
initialisation happens **once per classloader** and is **thread-safe** (guaranteed by the JLS), it
is a safe place for one-time expensive setup — but see the pitfall about a throwing static
initialiser below.

#### static Nested Class

```java
class Outer {
    static class Nested {
        // Can access Outer's static members only
    }
}
```

A `static` nested class holds **no reference** to the outer instance. That is why it is the
memory-leak-safe choice for helper classes (see chapter 10).

#### The Class-Loading Order (a favourite interview question)

```text
┌──────────────────────────────────────────────────────────────────┐
│  CLASS INITIALISATION (once, on first active use)                │
│  1. Superclass is initialised FIRST (top-down)                   │
│  2. static fields initialised (in declaration order)             │
│  3. static initialiser blocks run (in declaration order)         │
│  4. Class is now initialised and usable                         │
│                                                                  │
│  INSTANCE CREATION (each `new`)                                 │
│  5. Memory allocated; instance fields = default (0/null/false)  │
│  6. Superclass: field initialisers + instance blocks            │
│  7. Superclass constructor body                                 │
│  8. Subclass: field initialisers + instance blocks              │
│  9. Subclass constructor body                                   │
└──────────────────────────────────────────────────────────────────┘
```

#### static final and Compile-Time Constant Inlining

The subtlest and most useful thing about `static final`:

```java
class Config {
    static final int MAX = 100;              // COMPILE-TIME CONSTANT
    static final String NAME = "prod";       // COMPILE-TIME CONSTANT
    static final int RUNTIME = compute();    // NOT a constant
    static int compute() { return 42; }
}
```

The rule (JLS §4.12.4): a `static final` field of primitive or `String` type whose initialiser is a
**constant expression** gets a `ConstantValue` attribute in the class file, and **javac inlines the
literal at every use site**.

**Consequences:**

- `Config.MAX` compiles to the literal `100`, not a field read. Changing the constant requires
  recompiling **every** dependent class.
- `Config.RUNTIME` is a normal static field: read at runtime, and the class initialiser runs on
  first use.
- The same applies to `interface` fields: **all interface fields are implicitly
  `public static final`**, so `Integer.MAX_VALUE` is a compile-time constant and is inlined.

> **Interview trap:** "You changed `MAX` in `Config` but the app still uses the old value."
> **Answer:** the constant was inlined at compile time; you must recompile all dependents. This is
> also why `static final` is right for a *compile-time* constant but wrong if you ever need to
> change the value at runtime.

---

### 3.3 The `transient` Keyword

**Purpose:** Exclude field from serialization.

```java
class User implements Serializable {
    String username;
    transient String password;  // Not serialized
    transient int sessionId;     // Not serialized
    
    // On deserialization: password = null, sessionId = 0
}
```

**Use Cases:**
- Sensitive data (passwords, keys)
- Derived/cached data
- Non-serializable objects

#### What `transient` Actually Does (and Does Not Do)

- The **default** serialisation (`ObjectOutputStream`) skips `transient` fields entirely.
- On deserialisation the field gets the **JVM default** for its type: `null` for objects, `0` for
  numeric primitives, `false` for `boolean`, `'\0'` for `char`.
- `static` fields are **never** serialised either — they belong to the class, not the instance.
- **A `transient` field can still be serialised** if you write it yourself:

```java
class User implements Serializable {
    private static final long serialVersionUID = 1L;
    String username;
    transient String password;      // normally skipped

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();    // writes the non-transient fields
        out.writeObject(encrypt(password));   // ...but we add the encrypted one back
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        password = decrypt((String) in.readObject());
    }
}
```

**Note on `transient` + `final`:** a `transient final` field can never be restored to its original
value on deserialisation — it will always be the default. Avoid the combination unless the default
is genuinely correct.

> **Modern note:** Java Native Serialization is being replaced. For most production use, prefer
> JSON (Jackson), Protocol Buffers, or at minimum Java 9+'s `ObjectInputFilter` to harden any
> remaining `Serializable` usage. See the Serialization chapter.

---

### 3.4 The `volatile` Keyword

**Purpose:** Ensure visibility across threads, prevent caching.

```java
class Flag {
    private volatile boolean running = true;
    
    // Thread 1
    void stop() { running = false; }
    
    // Thread 2
    void process() {
        while (running) { /* work */ }  // Always sees latest value
    }
}
```

**volatile vs synchronized:**

| volatile | synchronized |
|----------|--------------|
| Only visibility | Visibility + atomicity |
| No locking | Locking overhead |
| Single read/write | Compound operations |
| Doesn't block | Blocks other threads |

**Note:** volatile doesn't make `i++` atomic (use AtomicInteger)

#### The Precise Definition

The Java Memory Model (JLS §17.4.5) gives `volatile` **two** guarantees:

1. **Visibility**: a write to a `volatile` field happens-before every subsequent read of that field
   by *any* thread. The value is never cached in a register or CPU cache across the read.
2. **Ordering**: a write to a `volatile` field happens-before *everything that happens after* the
   read of that field. This is the "release/acquire" semantics that makes safe publication work.

```text
   Thread A                          Thread B
   ────────                          ────────
   data = 42;         ──┐
   flag = true;  (volatile) ──┼──►  if (flag) {       // sees flag == true
                              │        use(data);      // MUST see data == 42
                              └──►  }
   
   The volatile WRITE to `flag` is a RELEASE.
   The volatile READ  of `flag` is an ACQUIRE.
   Everything before the release is visible after the acquire.
```

#### What `volatile` Does NOT Do

```java
class BadCounter {
    private volatile int count = 0;
    void increment() {
        count++;      // ❌ STILL NOT ATOMIC
    }
}
```

`count++` is **read-modify-write** — three separate operations:

```text
   count++  ==>   1. read count        (volatile read)
                 2. add 1              (local arithmetic)
                 3. write count       (volatile write)

   Two threads can interleave:
     T1 reads 5        T2 reads 5
     T1 writes 6       T2 writes 6      --> 6, but we incremented TWICE (expected 7)
```

The `volatile` guarantees each individual read and write is visible — but it does **not** make the
*sequence* atomic. Use `AtomicInteger` (which uses compare-and-set) or a lock:

```java
import java.util.concurrent.atomic.AtomicInteger;

class GoodCounter {
    private final AtomicInteger count = new AtomicInteger(0);
    void increment() { count.incrementAndGet(); }   // ✅ atomic
    int get() { return count.get(); }
}
```

#### The Canonical Correct Use: Double-Checked Locking

```java
class Lazy {
    private static volatile Lazy instance;   // volatile is ESSENTIAL

    static Lazy get() {
        Lazy local = instance;          // read once into a local
        if (local == null) {
            synchronized (Lazy.class) {
                local = instance;
                if (local == null) {
                    instance = local = new Lazy();   // fully constructed before publish
                }
            }
        }
        return local;
    }
}
```

**Why `volatile` is essential:** without it, the compiler and CPU may **reorder** the write of
`instance` *before* the writes of the object's fields inside the constructor. Another thread could
then see a non-null `instance` pointing at a **partially constructed** object — with `count == 0`
instead of the correct value. `volatile` creates a happens-before edge that prevents this.

> **Note:** since Java 5, the *initialisation-on-demand holder* idiom is simpler and needs no
> `volatile` at all — see the Singleton section in chapter 12.

---

### 3.5 Other Important Keywords

#### default (Java 8+)

```java
interface MyInterface {
    default void log() {
        System.out.println("Default implementation");
    }
}
```

A `default` method is an interface method **with a body** — the escape hatch that lets you evolve
an interface without breaking every implementor. The rules:

- A `default` method **cannot** be `final` or `abstract`.
- If a class implements the interface and also inherits a `default` method from a superclass, the
  **class wins** (the "class wins" rule).
- If **two** interfaces provide a `default` method, the implementing class **must** override it and
  disambiguate with `InterfaceName.super.method()`.

#### synchronized

```java
synchronized void increment() { count++; }  // Method level

void process() {
    synchronized(this) {  // Block level
        // Critical section
    }
}
```

Method-level synchronisation locks the **instance** (`this`). `static synchronized` locks the
`Class` object. Prefer the **block** form when the critical section is small — it narrows the lock
and reduces contention.

#### native

```java
native void perform();  // Implemented in C/C++
// Used by JVM for platform-specific operations
```

A `native` method has no Java body; the JVM links it to a C/C++ implementation via JNI. It is how
`Object.hashCode()`, `Object.clone()` and `Object.wait()` are implemented.

#### strictfp

```java
strictfp class Calculation {
    // Ensures consistent floating-point results across platforms
}
```

**Since Java 17 (JEP 306), `strictfp` is a no-op and deprecated for removal.** All floating-point
expressions are now strictly evaluated, and the old extended-precision (x87) behaviour is gone.
`strictfp` is accepted for source compatibility but has no effect.

---

## 4. Visualizations

### 4.1 Keyword Decision Map

```text
                    "I want to declare a member..."
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
        Is it per-object?              Is it shared?
              │                               │
              ▼                               ▼
        instance field                 static field
              │                               │
     ┌────────┴────────┐          ┌───────────┴───────────┐
     ▼                 ▼          ▼                       ▼
  must never      may change   shared + immutable      shared + mutable
  change ref
     │                 │          │                       │
     ▼                 ▼          ▼                       ▼
  final field    normal field  static final            plain static
                                      │                       │
                                      ▼                       ▼
                            constant expression?       ⚠️ global state
                                 │        │             (test pollution,
                                 yes       no           thread-safety)
                                 │        │
                                 ▼        ▼
                        INLINED by       static field
                        javac (no        (one copy
                        runtime read)    per class)
```

### 4.2 Class Initialisation vs Instance Initialisation

```text
┌────────────────────────────────────────────────────────────────────┐
│  ONE-TIME (class init)                                             │
│  superclass init → static field initialisers → static blocks      │
│                                                                    │
│  PER-INSTANCE (each `new`)                                        │
│  fields defaulted (0 / null / false)                               │
│      ↓                                                             │
│  SUPER: field initialisers → instance blocks → constructor body    │
│      ↓                                                             │
│  SUB:   field initialisers → instance blocks → constructor body    │
└────────────────────────────────────────────────────────────────────┘
```

### 4.3 `volatile` Happens-Before Edge

```text
   Thread A                                Thread B
   ────────                                ────────
   data = 42;                              while (!flag) { }
        │                                       │
        │  WRITE to volatile `flag`             │  READ of volatile `flag`
        ▼                                       ▼
   flag = true;  ──────── happens-before ──────────────►  sees flag==true
                                                        → MUST see data==42

   Without volatile, nothing orders the two writes →
   Thread B can see flag==true but data==0.  ← the classic bug
```

### 4.4 `static final` Constant Inlining

```text
   SOURCE                          BYTECODE
   ──────                          ────────
   class Config {                  class Config {
     static final int MAX = 100;      static final int MAX = 100;
   }                                  // ConstantValue: int 100
                                      // (no <clinit> needed)

   class Client {                  class Client {
     void use() {                    void use() {
       int x = Config.MAX;  ──────►      int x = 100;   // LITERAL INLINED
     }                                }                    // (no getstatic!)
   }                                }
```

### 4.5 `final` vs `volatile` — The JMM Comparison

```text
┌────────────────────────────────────────────────────────────────┐
│                     final field        volatile field          │
├────────────────────────────────────────────────────────────────┤
│ Visibility to other threads   │  YES (guaranteed)  │  YES      │
│ Freezes the value             │  YES (at ctor end)  │  NO       │
│ Atomicity for ++              │  NO                 │  NO       │
│ Needs a lock for compound ops │  YES                │  YES      │
│ Reordering protection         │  partial (JLS 17.5) │  YES      │
│ Overhead                      │  ~free              │  a fence  │
└────────────────────────────────────────────────────────────────┘
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — `static`, `final`, `transient` and `volatile` in One Program

**Problem:** *"Show me the initialisation order, prove a `static final` constant is inlined, and
show what `transient` does."*

```java
import java.io.*;

public class D1 {

    static final int MAX = 100;              // compile-time constant -> inlined at call sites
    static int staticCounter = 0;

    static {
        staticCounter = 10;
        System.out.println("[static init] staticCounter = " + staticCounter);
    }

    private final int id;
    private volatile boolean running = true;
    transient String password;

    { System.out.println("[instance init] before constructor body"); }

    D1() {
        id = ++staticCounter;
        System.out.println("[ctor] id = " + id);
    }

    static class Holder {
        static int x = compute();
        static int compute() { System.out.println("[Holder static init] x computed"); return 7; }
    }

    static class Lazy {
        private static volatile Lazy instance;
        static Lazy get() {
            Lazy local = instance;
            if (local == null) {
                synchronized (Lazy.class) {
                    local = instance;
                    if (local == null) { instance = local = new Lazy(); System.out.println("[Lazy] created"); }
                }
            }
            return local;
        }
    }

    static class Cred implements Serializable {
        private static final long serialVersionUID = 1L;
        String user;
        transient String password;
        Cred(String u, String p) { user = u; password = p; }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("--- creating instances ---");
        D1 a = new D1();
        D1 b = new D1();
        System.out.println("a.id=" + a.id + "  b.id=" + b.id);

        System.out.println("--- static nested class init on first use ---");
        System.out.println("Holder.x = " + Holder.x);
        System.out.println("Holder.x = " + Holder.x);

        System.out.println("--- final constant is inlined, not read from the class ---");
        System.out.println("MAX + 1 = " + (MAX + 1));

        System.out.println("--- volatile double-checked locking ---");
        Lazy.get(); Lazy.get(); Lazy.get();

        System.out.println("--- transient field ---");
        Cred c = new Cred("john", "s3cret");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) { oos.writeObject(c); }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            Cred back = (Cred) ois.readObject();
            System.out.println("user = " + back.user + " | password = " + back.password);
        }
    }
}
```

**Actual output (compiled and run on JDK 25):**

```text
[static init] staticCounter = 10
--- creating instances ---
[instance init] before constructor body
[ctor] id = 11
[instance init] before constructor body
[ctor] id = 12
a.id=11  b.id=12
--- static nested class init on first use ---
[Holder static init] x computed
Holder.x = 7
Holder.x = 7
--- final constant is inlined, not read from the class ---
MAX + 1 = 101
--- volatile double-checked locking ---
[Lazy] created
--- transient field ---
user = john | password = null
```

**Dry run:**

| # | What happens | Why |
|---|--------------|-----|
| 1 | `[static init] staticCounter = 10` prints **before** `main` | Class initialisation runs on first active use — before any instance exists |
| 2 | `new D1()` → `[instance init]` then `[ctor] id = 11` | Instance blocks run **before** the constructor body |
| 3 | Second `new D1()` → `[ctor] id = 12` | `staticCounter` is **shared** — it incremented across both instances |
| 4 | `Holder.x` prints `[Holder static init] x computed` **once** | `Holder` is a separate class; it initialises lazily on its first use |
| 5 | Second `Holder.x` prints `7` with **no** init message | Class initialisation happens **exactly once** per classloader |
| 6 | `MAX + 1` prints `101` | `MAX` is a compile-time constant — javac replaced it with the literal `100`; no `getstatic` was emitted |
| 7 | `Lazy.get()` called 3× prints `[Lazy] created` **once** | The DCL check succeeds after the first creation; `volatile` guarantees the instance is fully constructed before publication |
| 8 | `password = null` after deserialisation | `transient` fields are skipped by default serialisation, so the field keeps its default value |

**Takeaway:** One program demonstrates seven rules — class-init order, instance-init order, `static`
sharing, lazy nested-class init, constant inlining, `volatile` DCL, and `transient` skipping.

---

### Example 2 — `volatile` Does NOT Make `++` Atomic

**Problem:** *"Is `volatile int count` thread-safe for counting?"*

```java
import java.util.concurrent.atomic.AtomicInteger;

public class VolatileVsAtomic {

    static class VolatileCounter {
        private volatile int count = 0;
        void increment() { count++; }        // ❌ read-modify-write, NOT atomic
        int get() { return count; }
    }

    static class AtomicCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        void increment() { count.incrementAndGet(); }   // ✅ atomic CAS
        int get() { return count.get(); }
    }

    public static void main(String[] args) throws Exception {
        VolatileCounter v = new VolatileCounter();
        AtomicCounter a = new AtomicCounter();
        int threads = 8, iterations = 100_000;

        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < iterations; j++) { v.increment(); a.increment(); }
            });
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();

        System.out.println("expected   = " + (threads * iterations));
        System.out.println("volatile   = " + v.get());
        System.out.println("atomic     = " + a.get());
    }
}
```

**Actual output (compiled and run on JDK 25 — the volatile count is *usually* below expected, but
the exact value is non-deterministic, which is the point):**

```text
expected   = 800000
volatile   = 731502
atomic     = 800000
```

> **Important:** the `volatile` number **changes on every run**. That non-determinism *is* the lesson
> — you cannot write a test that asserts on it, and you cannot reason about it. The `AtomicInteger`
> result is always exactly `800000`.

**Dry run of the race:**

```text
   Two threads interleave inside count++:

   T1: read  count -> 5      (volatile read)
   T2: read  count -> 5      (volatile read)
   T1: compute 5+1 = 6
   T2: compute 5+1 = 6
   T1: write count = 6       (volatile write)
   T2: write count = 6       (volatile write)   ← T2's write CLOBBERS T1's

   Two increments happened, but the counter advanced by only 1.
   The same interleaving repeated 800,000 times loses ~68,000 updates.
```

**Takeaway:** `volatile` gives you **visibility** ("I always see the latest value") but not
**atomicity** ("no other thread can interleave"). If you need both, use `AtomicInteger` (CAS) or a
lock. This distinction is the single most commonly mishandled point in Java concurrency interviews.

---

### Example 3 — `transient` Is Skipped, but You Can Write It Back

**Problem:** *"How do I serialise a password field but keep it out of the default stream?"*

```java
import java.io.*;

public class TransientDemo {

    static class User implements Serializable {
        private static final long serialVersionUID = 1L;
        String username;
        transient String password;          // skipped by default serialisation

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();      // writes username only
            out.writeObject(encrypt(password));   // but we add the encrypted password back
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            password = decrypt((String) in.readObject());
        }

        static String encrypt(String s) { return "enc(" + s + ")"; }
        static String decrypt(String s) { return s.substring(4, s.length() - 1); }
    }

    public static void main(String[] args) throws Exception {
        User u = new User();
        u.username = "john";
        u.password = "s3cret";

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) { oos.writeObject(u); }
        System.out.println("serialized bytes = " + bos.size());

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            User back = (User) ois.readObject();
            System.out.println("username = " + back.username);
            System.out.println("password = " + back.password);
        }
    }
}
```

**Actual output (compiled and run on JDK 25):**

```text
serialized bytes = 71
username = john
password = s3cret
```

**Dry run:**

| Step | What happens |
|------|--------------|
| 1 | `defaultWriteObject()` writes only the **non-transient** fields → `username` |
| 2 | `writeObject(encrypt(password))` appends the **encrypted** password as an extra object |
| 3 | On read, `defaultReadObject()` restores `username`; `password` is still `null` |
| 4 | `readObject()` reads the extra object and decrypts it into `password` |
| 5 | The caller sees `password = s3cret` — but the **ciphertext** is what is on the wire |

**Takeaway:** `transient` controls the **default** stream, not whether a field can ever be
serialised. If you need custom (and ideally *encrypted*) handling, implement `writeObject` /
`readObject`. Note this is still native Java serialization — for new systems, prefer JSON or
Protobuf with explicit encryption.

---

## 6. Code from Scratch — Production-Grade

```java
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Correct usage of final, static, volatile and transient in a production-shaped class.
 */
public final class AppConfig {

    // ---- static final constants: COMPILE-TIME constants, inlined by javac ----
    private static final String DEFAULT_ENV = "prod";
    private static final int DEFAULT_PORT = 8080;

    // ---- static (class-level) state: shared, but thread-safe via Atomic* ----
    private static final AtomicLong REQUEST_COUNTER = new AtomicLong(0);
    private static final AtomicReference<String> ACTIVE_ENV = new AtomicReference<>(DEFAULT_ENV);

    // ---- final instance fields: reference can never change after construction ----
    private final String appName;
    private final int port;
    private final List<String> allowedOrigins;   // immutable copy -> truly immutable

    // ---- volatile instance field: cross-thread visibility ----
    private volatile boolean shutdownRequested;

    public AppConfig(String appName, int port, List<String> allowedOrigins) {
        this.appName = appName;                              // final: assigned once
        this.port = port;
        this.allowedOrigins = List.copyOf(allowedOrigins);   // defensive copy, NOT unmodifiableList
    }

    // ---- static factory: a static method has no `this` and cannot be overridden ----
    public static AppConfig defaults() {
        return new AppConfig("my-app", DEFAULT_PORT, List.of("https://example.com"));
    }

    // ---- static method reading static state ----
    public static long totalRequests() { return REQUEST_COUNTER.get(); }

    public static void incrementRequests() { REQUEST_COUNTER.incrementAndGet(); }  // no lost updates

    public static String activeEnvironment() { return ACTIVE_ENV.get(); }

    public static void switchEnvironment(String env) { ACTIVE_ENV.set(env); }

    // ---- instance methods ----
    public String getAppName() { return appName; }
    public int getPort() { return port; }
    public List<String> getAllowedOrigins() { return allowedOrigins; }  // already immutable
    public boolean isShutdownRequested() { return shutdownRequested; }

    public void requestShutdown() { shutdownRequested = true; }   // volatile write -> visible everywhere

    @Override
    public String toString() {
        return "AppConfig{name='" + appName + "', port=" + port + ", env=" + activeEnvironment() + '}';
    }
}
```

**Why this is production-grade:**

| Concern | How it is handled |
|---------|-------------------|
| Shared mutable state | `AtomicLong` / `AtomicReference` instead of a plain `static long` |
| Immutable configuration | `final` fields + `List.copyOf` (a real copy, not a view) |
| Cross-thread visibility | `volatile boolean shutdownRequested` |
| Testability | No hidden I/O; `switchEnvironment` is a controllable seam |
| Constants | `static final` primitives/Strings, inlined at compile time |
| API safety | `getAllowedOrigins()` returns an unmodifiable *immutable* list |

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — `final` mistaken for immutable

```java
// ❌ WRONG — the list is still mutable
private final List<String> items = new ArrayList<>();
public void add(String s) { items.add(s); }
```
```java
// ✅ RIGHT — copy on the way in
private final List<String> items;
public MyClass(List<String> items) { this.items = List.copyOf(items); }
```
**WHY:** `final` freezes the *reference*. The object it points to is still a mutable `ArrayList`.

### ❌ Pitfall 2 — `volatile` used for counting

```java
// ❌ WRONG — loses updates
private volatile int count;
public void inc() { count++; }
```
```java
// ✅ RIGHT
private final AtomicInteger count = new AtomicInteger();
public void inc() { count.incrementAndGet(); }
```
**WHY:** `volatile` gives visibility, not atomicity. `count++` is a read-modify-write.

### ❌ Pitfall 3 — `static` mutable state

```java
// ❌ WRONG — shared across all tests in the JVM
private static Map<String, Object> cache = new HashMap<>();
```
**WHY:** Global state is invisible coupling, is not thread-safe, and leaks between tests. Prefer an
injected, bounded cache (Caffeine/Guava).

### ❌ Pitfall 4 — Double-checked locking without `volatile`

```java
// ❌ WRONG — another thread can see a partially constructed object
private static Singleton instance;
public static Singleton get() {
    if (instance == null) {
        synchronized (Singleton.class) {
            if (instance == null) instance = new Singleton();
        }
    }
    return instance;
}
```
```java
// ✅ RIGHT
private static volatile Singleton instance;   // volatile is ESSENTIAL
```
**WHY:** Without `volatile`, the write to `instance` may be reordered *before* the constructor's
field writes, so another thread can observe a non-null reference to a half-built object.

### ❌ Pitfall 5 — `static` method trying to use instance state

```java
// COMPILE-ERROR-DEMO — this block deliberately does NOT compile.
class Service {
    private String tenant;
    static void process() {
        System.out.println(tenant);   // ❌ COMPILE ERROR - no `this` in a static method
    }
}
```
**WHY:** A `static` method has no `this`. It belongs to the class, not an instance.

### ❌ Pitfall 6 — Modifying a `static final` "constant" at runtime

```java
static final int[] LIMIT = {1, 2, 3};
LIMIT[0] = 99;    // ✅ COMPILES! The array contents changed.
```
**WHY:** `final` freezes the *reference*. The array's elements are still mutable. Use `List.of(...)`
if you need real immutability.

### ❌ Pitfall 7 — `transient` on a `final` field

```java
private transient final String token = "abc";   // ⚠️ always null after deserialisation
```
**WHY:** `transient` skips the field on the way out, and `final` prevents it being restored on the
way in. The field will always be `null` after deserialisation.

### ❌ Pitfall 8 — Assuming `strictfp` still does something

```java
strictfp double calc() { return 1.0 / 3.0 * 3.0; }   // ⚠️ no-op since Java 17
```
**WHY:** JEP 306 made floating point always strict in Java 17. `strictfp` is deprecated for removal
and has no effect.

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What does `final` do?**
A: It means "cannot be changed after initialisation", but the meaning depends on where you put it.
A `final` variable cannot be reassigned, a `final` method cannot be overridden, and a `final` class
cannot be extended. Note it freezes the *reference* — a `final List` can still be mutated.

**Q: What does `static` do?**
A: It makes the member belong to the class rather than to instances. There is exactly one copy per
classloader, shared by all instances, and it is accessible without creating an object. A `static`
method cannot be overridden — a subclass method with the same signature *hides* it.

**Q: Can a `final` method be overloaded?**
A: Yes. Overloading is a different method signature; overloading is unrelated to `final`.

**Q: Can we serialize transient variable?**
A: No, that's the purpose. Use `writeObject()` for custom serialization.

**Q: Can static methods be overridden?**
A: No. They can be hidden (shadowed) but not overridden.

**Q: When is static block executed?**
A: When class is loaded, before any instance creation. Precisely: at *class initialisation*, which
happens once, on the class's first active use.

**Q: What is `transient` for?**
A: It marks a field so the default Java serialisation skips it. On deserialisation the field gets
the JVM default (`null`, `0`, `false`). Use it for sensitive data or derived values.

### 🟡 Intermediate

**Q: Is `volatile` thread-safe?**
A: No — it is only *visible*. It guarantees a read always sees the most recent write, but it does
not make compound operations atomic. `count++` is a read-modify-write and loses updates under
concurrency. Use `AtomicInteger` or a lock.

**Q: What is the difference between `static final` and `final`?**
A: `final` alone means one value *per instance*. `static final` means one value *per class*, shared
by all instances. A `static final` primitive or `String` initialised with a constant expression is
also a **compile-time constant** that `javac` inlines at the use site.

**Q: Why does `static final int MAX = 100` get inlined, and is that a problem?**
A: Because the JLS defines it as a constant expression, so the class file records a `ConstantValue`
attribute and the compiler substitutes the literal. The benefit is speed; the cost is that you
cannot change the value without recompiling every dependent class, and you can never change it at
runtime.

**Q: Is volatile replacement for synchronized?**
A: No. volatile only ensures visibility, not atomicity.

**Q: Can an inner (non-static) class have `static` members?**
A: Before Java 16, no — except `static final` compile-time constants. Since Java 16 (JEP 395), inner
classes, local classes and anonymous classes *may* declare static members.

### 🔴 Advanced / Senior

**Q: Explain double-checked locking and why `volatile` is required.**
A: The pattern checks a field without locking on the fast path, and locks only when it is null.
`volatile` is essential because it creates a happens-before edge between the constructor's writes
and the publishing write. Without it, the compiler or CPU may reorder the reference write *before*
the field initialisation writes, so another thread could observe a non-null reference to a
partially constructed object. Since Java 5, the initialisation-on-demand holder idiom is simpler
and needs no `volatile`.

**Q: What is the JMM guarantee for `final` fields?**
A: JLS §17.5: a `final` field is *frozen* at the end of the constructor, and any thread that reads
a reference to a correctly constructed object is guaranteed to see the correct value of the `final`
field — even through a data race. This is "safe publication" for final fields, and it is why
immutable objects need no `volatile` or synchronization to be safely shared. It does **not** apply
to non-final fields, and it does not make compound operations atomic.

**Q: Is `final` on a method a performance optimisation?**
A: Not primarily. Its value is *semantic* — it closes the extension point so behaviour cannot
change, protecting invariants. The JIT can inline a `final` method because no subclass can replace
it, but modern JITs also inline non-final methods using class-hierarchy analysis and profile data,
so the performance argument is largely obsolete.

**Q: What happens if a `static` initialiser throws?**
A: The class enters the *erroneous* state. The first use throws the original exception; every
subsequent use throws `NoClassDefFoundError`. This is why doing I/O, network calls or config
loading in a static initialiser is dangerous — one transient failure poisons the class for the
lifetime of the classloader.

**Q: How do you make a `static` counter safe without `AtomicInteger`?**
A: Use `synchronized` on a common lock, or `LongAdder` (which scales better under contention than
`AtomicLong` because it uses striped cells). `LongAdder` is the right choice for high-frequency
counters where you need the sum but not atomic per-operation reads.

### ⚫ Expert / Principal

**Q: Is `static final` constant inlining a design smell in a large codebase?**
A: It is a *coupling* question. Inlining means the value is copied into every dependent class file,
so changing it requires recompiling the entire dependency graph, and a stale jar can carry a
mismatched constant. For values genuinely fixed by the language or the platform —
`Integer.MAX_VALUE`, a protocol version — inlining is correct and desirable. For business constants
that might change per environment, they belong in configuration, not in a compile-time constant. The
rule I apply: if the value can differ between deployments, it must not be a compile-time constant.

**Q: How would you design a hot-path counter in a high-throughput service?**
A: `LongAdder` over `AtomicLong` when the value is only summed; `AtomicLong` when you need exact
per-operation reads; a sharded counter when the key space is large; or a per-request-local counter
aggregated at the end of the request, which removes contention entirely. I would also avoid
`static` here and inject the counter, so it is testable and its lifecycle is explicit.

**Q: `final`, `volatile`, or a lock — how do you choose?**
A: `final` when the value never changes after construction — it gives safe publication for free and
imposes no runtime cost. `volatile` for a single flag or reference read and written independently,
where I need visibility but no compound-operation atomicity. A lock (or `Atomic*`) when I need a
read-modify-write to be atomic, or when several fields must change together. I reach for `final`
fields over `volatile` fields whenever the value is naturally immutable, because `final` is cheaper
and gives stronger guarantees.

---

## 9. Counter-Questions & Traps

### CQ1. "If `final` makes a field immutable, why do you also need defensive copies?"

- **Naive answer:** "`final` freezes the reference, so the object can't change."  ← wrong
- **The trap:** This is *the* `final` misconception, and it is nearly universal — the candidate
  believes `final` implies immutability.
- **Expert answer:** "`final` freezes the **reference**, not the **object**.
  `private final List<String> l = new ArrayList<>()` still lets any holder of `l` call `l.add(...)`.
  Immutability needs three things together: `final` (the reference cannot move), `private` (no
  external handle to the reference), and **defensive copies** (no external handle to the mutable
  object). `List.copyOf` on input plus returning the immutable field on output gives you all three."

### CQ2. "Why not just use `synchronized` everywhere instead of `volatile`?"

- **Naive answer:** "`volatile` is faster, so use it."  ← the trap the interviewer is testing
- **The trap:** `volatile` is not a faster `synchronized`; it is a **different, weaker** tool. If you
  reach for it without understanding what it does not guarantee, you will ship a race.
- **Expert answer:** "They solve different problems. `volatile` gives visibility for a single variable
  with no locking and no mutual exclusion — it makes reads and writes visible and orders them, but
  it does not make a read-modify-write atomic. `synchronized` gives mutual exclusion *and*
  visibility, so it makes compound operations atomic. If I need atomicity I use a lock or an
  `Atomic*`; if I only need a flag to be seen, `volatile` is right. The classic mistake is using
  `volatile` for a counter."

### CQ3. "You said `static final` constants are inlined. Isn't that a problem for testing?"

- **Naive answer:** "Yes, so don't use `static final`."  ← over-correction
- **The trap:** The interviewer wants to know whether you understand *when* inlining is right, not
  whether you can throw it out.
- **Expert answer:** "It is a problem only for values that vary per deployment. `static final int
  MAX_VALUE = 100` is correct and desirable — a compile-time constant, inlined, and impossible to
  corrupt at runtime. What I avoid is a *business* value in a compile-time constant: a per-tenant
  limit, a feature flag, a timeout. Those belong in configuration, read at runtime. Inlining is a
  feature; the question is whether the value is genuinely fixed."

### CQ4. "Your `static` counter is shared across all instances. Isn't that a problem?"

- **Naive answer:** "No, it's intentional — it counts all instances."  ← incomplete
- **The trap:** The interviewer is probing the *testing* and *thread-safety* consequences, not the
  intent.
- **Expert answer:** "It is intentional, but it comes with obligations. First, thread safety: a
  plain `static int` incremented from multiple threads loses updates — I would use `AtomicLong` or
  `LongAdder`. Second, testability: a `static` counter is shared across every test in the JVM, so
  one test's increments leak into the next and tests become order-dependent. That is the deeper
  reason to prefer dependency injection: an injected counter can be reset, spied on and asserted on.
  `static` state is a global, and globals are hard to test."

### CQ5. "Why is `volatile` enough for the double-checked locking example but not for a counter?"

- **Naive answer:** "Because `volatile` makes reads and writes visible."  ← the trap
- **The trap:** The candidate applies `volatile` uniformly and cannot explain the difference. The
  interviewer wants the *composite operation* insight.
- **Expert answer:** "Because the two cases differ in whether the operation is *atomic*. In the DCL
  case the operation is a single reference write, and what I need is that no other thread can
  observe the reference before the constructor's writes are visible — `volatile` gives exactly that
  ordering. In the counter case the operation is read-modify-write; `volatile` makes each of the
  three steps visible but does not stop another thread interleaving between them, so updates are
  lost. The general rule: `volatile` is for a *single* variable read or written independently;
  anything needing atomicity needs `Atomic*` or a lock."

### CQ6. "Is `final` needed for thread safety?"

- **Naive answer:** "Yes, `final` makes it thread-safe."  ← the classic wrong premise
- **The trap:** This is explicitly the "challenging a wrong premise" question. `final` gives *safe
  publication*, which is not the same as thread safety.
- **Expert answer:** "`final` gives you **safe publication** of that final value — JLS §17.5
  guarantees a thread that reads a properly constructed object sees the correct final field value
  even through a data race. That is not the same as thread safety: a `final` field whose
  *referenced object* is mutable is still racy, and `final` does nothing for non-final fields. The
  honest framing: `final` is one ingredient of thread safety, and the cheapest one, but immutability
  of the whole object graph is what actually removes the need for synchronisation."

### CQ7. "What if a `static` initialiser throws? How would you find out?"

- **Naive answer:** "It throws an `ExceptionInInitializerError`."  ← right symptom, wrong depth
- **The trap:** This is an *observability* question — the candidate should connect the mechanism to
  how it would be diagnosed in production.
- **Expert answer:** "The first access throws the original exception wrapped in
  `ExceptionInInitializerError`. The class then enters the **erroneous** state, and every
  subsequent access — even ones that would not have triggered initialisation — throws
  `NoClassDefFoundError`. In production this shows up as `NoClassDefFoundError: Could not initialize
  class X` with the *original* cause buried in the attached exception. To diagnose it I would look
  for the first `ExceptionInInitializerError` in the logs, not the later `NoClassDefFoundError`s, and
  fix the root cause by not doing I/O in a static initialiser."

### CQ8. "How would you make `static` state testable without removing it?"

- **Naive answer:** "Just reset it in `@BeforeEach`."  ← workable but crude
- **The trap:** The interviewer wants a *design* solution rather than a test-hygiene patch.
- **Expert answer:** "The best answer is to not have process-wide mutable state: inject a per-test
  instance. If the state genuinely must be global — a metrics registry, a cache — I would put it
  behind an interface and inject a fresh implementation per test, or make the reset explicit and
  idempotent in a `@BeforeEach`. Resetting a `static` in test setup works but it is order-dependent
  and hides the coupling; the fact that I *need* to reset it is the signal that the design is wrong."

## 10. Rapid-Fire MCQs

**Q1.** `final` on an instance field of reference type guarantees:
- **A)** The object it references never changes
- **B)** The reference never changes
- **C)** The object is thread-safe
- **D)** The field is visible across threads

**Q2.** `static final int MAX = 100;` used in another class compiles to:
- **A)** `getstatic` on the class
- **B)** The literal `100` inlined by javac
- **C)** A call to a generated getter
- **D)** A `ConstantPool` lookup at runtime

**Q3.** Which is **true** of `volatile int count; count++;`?
- **A)** It is atomic
- **B)** It is visible but not atomic
- **C)** It is neither visible nor atomic
- **D)** It requires synchronisation to be visible

**Q4.** A `transient` field after deserialisation is:
- **A)** `null` for objects, `0` for numerics
- **B)** Its last written value
- **C)** Throws an exception
- **D)** `null` for all types

**Q5.** `volatile` is required in double-checked locking to prevent:
- **A)** Deadlocks
- **B)** Observing a partially constructed object
- **C)** Integer overflow
- **D)** Class-loading races

**Q6.** Which members are **never** serialised by default?
- **A)** `private` fields
- **B)** `static` and `transient` fields
- **C)** `final` fields
- **D)** All instance fields

**Q7.** A `static` method can:
- **A)** Access instance fields via `this`
- **B)** Be overridden by a subclass
- **C)** Only access static members and its parameters
- **D)** Be declared `abstract`

**Q8.** The order of class initialisation is:
- **A)** static fields → static blocks → superclass init → instance fields → constructor
- **B)** superclass init → static fields → static blocks → instance fields → instance blocks → constructor
- **C)** constructor → static fields → instance fields
- **D)** static fields → instance fields → static blocks → constructor

**Q9.** `strictfp` in Java 17+:
- **A)** Forces 64-bit intermediate results
- **B)** Is a no-op, deprecated for removal
- **C)** Is a compile error
- **D)** Enables FMA optimisations

**Q10.** Which is **true** about `final` fields and the JMM?
- **A)** They guarantee safe publication of the final value
- **B)** They make the whole object immutable
- **C)** They prevent reordering of all field accesses
- **D)** They make compound operations atomic

**Q11.** A `static final` field initialised with `compute()` is:
- **A)** A compile-time constant, inlined
- **B)** A runtime value, read via `getstatic`
- **C)** Illegal
- **D)** Initialised lazily

**Q12.** Since Java 16, an inner class **may** declare:
- **A)** Nothing but instance members
- **B)** `static final` constants only
- **C)** Static members, including static initialiser blocks
- **D)** Only `public` members


### Answer Key

| # | Answer | Explanation |
|---|--------|-------------|
| 1 | **B** | `final` prevents reassignment of the reference. The referenced object's internal state can still change — that is why defensive copies are needed. |
| 2 | **B** | A `static final` primitive initialised with a constant expression gets a `ConstantValue` attribute and is inlined as a literal at the use site. |
| 3 | **B** | `volatile` guarantees visibility and ordering for a single variable. `count++` is a read-modify-write, so it is not atomic. |
| 4 | **A** | Skipped fields are not restored, so they keep the JVM default: `null`, `0`, `false`, `'\0'`. |
| 5 | **B** | `volatile` creates the happens-before edge that prevents another thread seeing a non-null reference to a partially constructed object. |
| 6 | **B** | `static` fields belong to the class, not the instance, and `transient` fields are skipped by the default stream. |
| 7 | **C** | A `static` method has no `this`, so it can only reach static members and its own parameters. It also cannot be overridden. |
| 8 | **B** | Superclass init first, then static fields, then static blocks; per instance: instance fields, instance blocks, then the constructor body. |
| 9 | **B** | JEP 306 made floating point always strict in Java 17; `strictfp` is now a no-op and deprecated for removal. |
| 10 | **A** | JLS §17.5 gives final fields safe-publication semantics. It does not make the object immutable or compound operations atomic. |
| 11 | **B** | `compute()` is not a *constant expression*, so no `ConstantValue` attribute is recorded; the field is initialised at class-init time and read via `getstatic`. |
| 12 | **C** | JEP 395 (Java 16) allows static members — including static initialiser blocks — in inner, local and anonymous classes. |

## 11. Cheat Sheet / One-Page Summary

### Quick Reference Table

| Keyword | Applied To | Purpose |
|---------|------------|---------|
| final | Variable, Method, Class | Prevent modification/override/inheritance |
| static | Variable, Method, Block, Class | Class-level, shared |
| transient | Variable | Skip serialization |
| volatile | Variable | Thread visibility |
| synchronized | Method, Block | Thread safety |
| native | Method | Platform-specific code |
| default | Method (interface) | Default implementation |

### The Three Questions to Ask for Any Modifier

| Question | `final` | `static` | `volatile` |
|----------|---------|----------|-----------|
| What is frozen? | The reference / the method / the type | Nothing — it is *shared* | Nothing — it is *visible* |
| Where does it live? | Per instance (or per class if also `static`) | Per class, one copy per classloader | Per instance, like a normal field |
| Does it block? | No | No | No (but it is a memory barrier) |
| Does it give atomicity? | No | No | **No** |
| Can it be overridden? | N/A | No — hidden only | N/A |

### Modifier Cheat Codes

```java
static final int CONST = 42;          // compile-time constant, INLINED
final List<String> l = List.copyOf(x); // immutable value
private static final Logger LOG = ...; // constant logger
private volatile boolean shutdown;    // cross-thread flag
private final AtomicLong counter = ...; // thread-safe shared counter
```

### The Golden Rules

1. `final` freezes the **reference**, not the **object**.
2. `static` means **one copy per classloader** and **no polymorphism**.
3. `volatile` gives **visibility**, never **atomicity**.
4. `static final` constants are **inlined** — changing them needs a full recompile.
5. A `static` initialiser that throws **poisons the class**.
6. `transient` skips the **default** stream only.

---

## 12. Real-World Production Notes

### Where These Keywords Show Up in Banking / E-Commerce Systems

| Keyword | Production use | Real example |
|---------|----------------|--------------|
| `static final` | Immutable config constants, lookup tables | `static final Map<String, BigDecimal> FX_RATES = loadRates();` |
| `static final` + `AtomicLong` | Metrics counters | `REQUEST_COUNT.incrementAndGet()` in a filter |
| `final` fields | Value objects (Money, AccountId) | Guarantees safe publication without synchronisation |
| `final` methods | Framework templates, security checks | `final void validate()` cannot be bypassed by a subclass |
| `final` class | Immutable domain values | `final class OrderId` — cannot be subclassed to break the contract |
| `volatile` | Feature flags, shutdown signals, cached config | `private volatile boolean draining;` read by the request loop |
| `transient` | Session state, cached tokens, ephemeral flags | `transient String authToken;` in a cached session bean |
| `static` block | One-time resource setup | `static { loadLoggingConfig(); }` — but see the pitfall about throwing |

### Tuning Knobs & Metrics

- **`-XX:+PrintCompilation` / JITWatch** — verify that a `final` method is actually being inlined
  before you claim a performance win from it.
- **`-XX:AutoBoxCacheMax=<n>`** — controls the `Integer` cache upper bound. Never rely on it; it
  exists for allocation tuning, not semantics.
- **Connection pools** — pool sizing is the real lever behind most "is it `static`?"-style
  contention questions; `static` shared state is usually a symptom of an unpooled resource.
- **A `static` counter is a hidden global** — add a Micrometer gauge and alert on unusual rates; a
  counter that resets or jumps is usually a classloader reload or a test leak.

### Practical Rules We Enforce in Code Review

1. No `static` mutable state outside an intentional, bounded, injected cache.
2. `static final` only for values genuinely fixed across deployments.
3. Prefer `final` fields over `volatile` fields when the value is naturally immutable.
4. Never do I/O in a static initialiser; if you must, make failure non-fatal.
5. Use `Atomic*` for counters and flags; `volatile` only for a single boolean/reference.
6. `transient` only on `Serializable` classes — and prefer JSON/Protobuf for new systems.

---

## 13. Follow-up Learning

### Next in this folder

- [08 · equals / hashCode / toString](./08-OOP-equals-hashCode-toString.md) — the other `Object` contract, and how `final` classes simplify it
- [09 · Immutable Classes](./09-OOP-Immutable-Classes.md) — putting `final`, `private final` and defensive copies together
- [10 · Nested, Inner and Anonymous Classes](./10-OOP-Nested-Inner-and-Anonymous-Classes.md) — `static` nested classes vs inner classes and their memory implications
- [11 · Advanced Design and the Object Class](./11-OOP-Advanced-Design-and-Object-Class.md) — `final` methods, method hiding and the `Object` API
- [12 · Critical and Tricky Scenarios](./12-OOP-Critical-and-Tricky-Interview-Scenarios.md) — the constructor-dispatch trap and the `Integer` cache

### Related topics elsewhere

- [Java Memory Model](../02-Java-Memory-Model/01-Memory-Areas.md) — the full JMM picture behind `volatile` and `final`
- [Concurrency](../08-Multithreading-Concurrency/01-Thread-Basics.md) — `Atomic*`, `synchronized` and lock-free design
- [Java 8+ Features](../06-Java-8-Features/01-Lambda-Functional-Interfaces.md) — effectively-final locals and lambdas
- [Java 11/17/21 Features](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) — `sealed` classes and records

### Specifications and books

- **JLS §4.12.4** — constant variables and compile-time constants
- **JLS §8.1.3** — inner and nested classes (JEP 395 in Java 16)
- **JLS §12.4** — class initialisation and the erroneous state
- **JLS §17.4.5** — `volatile` semantics; **§17.5** — final field semantics
- **JEP 306** — strict floating point (Java 17); **JEP 395** — relax the restriction on static
  members in inner classes (Java 16)
- **Effective Java** — Items 16 (`static` members), 17 (value-based classes), 55 (serialization)

---

**Next Chapter:** → [08 · equals(), hashCode() and toString()](./08-OOP-equals-hashCode-toString.md)
**Previous Chapter:** ← [06 · Interface vs Abstract Class](./06-OOP-Interface-vs-Abstract-Class.md)

*Last Updated: 2026 · Java 17+ (verified on JDK 25) · All outputs in this chapter were produced by running the code*
