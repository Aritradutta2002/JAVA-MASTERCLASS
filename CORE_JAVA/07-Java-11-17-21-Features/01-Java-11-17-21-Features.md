# Java 11, 17 & 21 Features — The Complete Interview Guide

> **The "do you actually ship modern Java?" chapter.** Java 8 was the paradigm shift;
> 11, 17 and 21 were the **hardening** — language features that make illegal states
> unrepresentable: `records`, `sealed` types, pattern matching, `switch` expressions,
> and **virtual threads**.

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

- **Java 11** (2018): `var` in lambda parameters, `String` convenience methods (`strip`, `isBlank`, `repeat`, `lines`), the **HTTP Client**, `Files.readString`/`writeString`, `Collection.toArray(IntFunction)`, `Optional.isEmpty()`, `List.copyOf`/`Map.copyOf`, `Set.of` with more than 2 elements, private interface methods.
- **Java 14/16/17** — `switch` **expressions** (final in 14), **text blocks** (final in 15), `records` (final in 16), **pattern matching for `instanceof`** (final in 16), **sealed classes** (final in 17).
- **Java 21** (2023): **pattern matching for `switch`** (final), **record patterns** (final), **virtual threads** (final), **sequenced collections** (preview), `String.formatted`, `Thread.ofVirtual`, `ExecutorService.newVirtualThreadPerTaskExecutor`.
- **A `record` is a final class with `final` fields, a canonical constructor, generated accessors, and generated `equals`/`hashCode`/`toString`.** It is **shallowly immutable** — a mutable component (a `List`) is still mutable.
- **Records cannot be `extends`ed** (they are implicitly `final`) but they **can implement interfaces**.
- **`sealed` classes restrict who may extend them** — `permits A, B, C`. This gives the compiler the information to check **exhaustiveness** in a `switch`.
- **Pattern matching for `switch`** (Java 21) is the biggest language change since Java 8: `case Circle c -> …`, `case null ->`, `default ->`, with the compiler verifying that every permitted subtype (and `null`) is handled.
- **A `switch` expression must be exhaustive** — the compiler enforces it. That is what turns "you forgot a case" from a production bug into a compile error.
- **`var` is not a keyword** — it is a *reserved type name*. It infers the type from the initialiser, and it is **not** allowed for: fields, method parameters, method returns, catch parameters, or an initialiser with **no initialiser**.
- **Virtual threads (Java 21)** are cheap, JVM-managed threads — you can have a million. They are **not** faster at CPU work; they are for **blocking I/O**, where the carrier thread is released while the virtual thread waits.
- **The virtual thread PINNING problem:** a virtual thread that blocks inside `synchronized` (or a native method) **pins its carrier thread**, reducing scalability. `ReentrantLock` does not pin.
- **Java 21 is an LTS** (as are 8, 11 and 17) — it is the version most enterprises run.

---

## 1. What & Why — The Problem This Solves

**The problem Java 11→21 solves:** *how do you make illegal states unrepresentable, without a mountain of boilerplate?*

```text
   ❌ BEFORE — a DTO with 8 fields
   ─────────────────────────────
   public final class Money {
       private final long amount;
       private final String currency;
       // ... 6 more fields

       public Money(long amount, String currency) { ... }        // the constructor
       public long getAmount() { return amount; }                // 8 accessors
       public String getCurrency() { return currency; }
       // ... 6 more accessors
       @Override public boolean equals(Object o) { ... }        // equals
       @Override public int hashCode() { return ...; }           // hashCode
       @Override public String toString() { return ...; }        // toString
   }
   ≈ 40 lines of pure boilerplate, per class.

   ✅ AFTER — a record
   ──────────────────────
   public record Money(long amount, String currency) { }
   ≈ 1 line.
```

**The second problem — "the hierarchy is open, so I cannot know all the cases".**

```text
   ❌ BEFORE — an open interface
   ─────────────────────────────
   interface Shape { double area(); }

   // A visitor / switch cannot be checked for exhaustiveness
   // → adding a new Shape silently breaks every switch in the codebase

   ✅ AFTER — a sealed hierarchy
   ──────────────────────────────
   sealed interface Shape permits Circle, Square, Triangle { double area(); }

   // Now the compiler KNOWS there are exactly three cases
   double describe(Shape s) {
       return switch (s) {
           case Circle c   -> "circle r=" + c.radius();     // ✅ no casting
           case Square sq  -> "square s=" + sq.side();
           case Triangle t -> "triangle";
       };   // ← EXHAUSTIVE — the compiler proves it
   }
```

**The third problem — "one thread per request does not scale".**

```text
   ❌ BEFORE (Java 8-20)
   ─────────────────────
   1 request = 1 platform thread = 1 MB of stack reserved
   10,000 concurrent requests = 10,000 threads = 10 GB of stack
   → and most of them are BLOCKED on a socket, doing nothing

   ✅ AFTER (Java 21 — virtual threads)
   ────────────────────────────────────
   1 request = 1 virtual thread = ~1 KB (a heap object)
   10,000 concurrent requests = 10,000 virtual threads = ~10 MB
   → the platform threads (carriers) are only as many as the cores
```

> 💡 **The interview framing.** Java 11–21 is a series of moves in the same direction:
> **move constraints from runtime to compile time.** `sealed` + pattern matching moves
> "did you handle every case?" from a code-review question to a compiler error.
> `records` moves "did you write `equals` correctly?" to "it is generated".
> Virtual threads move "how many concurrent requests can you serve?" from an
> infrastructure problem to a language feature.

---

## 2. Mental Model — The One Picture

```text
   THE THREE "CLOSED SET" MACHANISMS

   ┌──────────────────────────────────────────────────────────────────────┐
   │ 1. ENUM                       — a closed set of LABELS               │
   │    enum Status { PENDING, ACTIVE, CLOSED }                          │
   │    ✓ one shared class        ✗ no per-case state                    │
   │    ✓ compiler knows the size ✗ switch is over `ordinal()`           │
   ├──────────────────────────────────────────────────────────────────────┤
   │ 2. SEALED INTERFACE           — a closed set of TYPES                │
   │    sealed interface Shape permits Circle, Square {}                 │
   │    ✓ each case is its own record/class (per-case STATE)             │
   │    ✓ the compiler checks switch exhaustiveness                       │
   │    ✗ no runtime enum-like `values()` (you write it yourself)         │
   ├──────────────────────────────────────────────────────────────────────┤
   │ 3. OPEN INTERFACE             — an extensible set                    │
   │    interface Shape { }        ← the default: anyone may implement   │
   │    ✗ no exhaustiveness checking                                   │
   └──────────────────────────────────────────────────────────────────────┘
```

**What to look at:** `sealed` sits **between** an enum and an open interface. It gives you the *compile-time exhaustiveness* of an enum with the *per-case state* of separate classes.

```text
   THE THREAD MODELS

   PLATFORM THREAD (Java 8-20)
   ───────────────────────────
   request ──▶ [Thread]  1 MB reserved stack, OS thread
                 │
                 ├─ CPU work      → occupies the thread
                 └─ BLOCKING I/O  → 💥 occupies the thread (doing NOTHING)
                                    → 10,000 blocked requests = 10,000 threads

   VIRTUAL THREAD (Java 21)
   ───────────────────────
   request ──▶ [VThread]  a heap object, ~1 KB
                 │
                 ├─ CPU work      → runs on a CARRIER (platform) thread
                 └─ BLOCKING I/O  → ✅ UNMOUNTS the carrier
                                    → the carrier runs another virtual thread
                                    → 10,000 blocked requests ≈ 8 platform threads
```

---

## 3. Deep Dive — Theory

## 3.1 `var` — local variable type inference

```java
var list = new ArrayList<String>();   // ✅ ArrayList<String>
var count = 0;                        // ✅ int
var name = "Alice";                   // ✅ String
var result = compute();               // ✅ whatever compute() returns
```

**The rules:**

| Rule | Detail |
|---|---|
| **Local variables only** | ❌ fields, ❌ method parameters, ❌ method return types, ❌ catch parameters |
| **Needs an initialiser** | `var x;` is ❌ — there is nothing to infer from |
| **The initialiser must have a type** | `var x = null;` is ❌ (ambiguous); `var x = (String) null;` is ✅ |
| **The inferred type is the *static* type** | `var list = new ArrayList<String>();` → `ArrayList<String>`, not `List<String>` |
| **Cannot use `var` in a lambda parameter** (Java 8) | `var` in a lambda parameter is **Java 11+** |
| **It is a reserved *type name*, not a keyword** | `int var = 1;` is ✅ legal — you just cannot name a *type* `var` |
| **No `var` in a `for` header?** | ✅ `for (var i = 0; i < 10; i++)` is legal |

**Why `var` is a *local* inference, not a dynamic type:**

```java
var list = new ArrayList<String>();
list.add("a");
list.add(1);          // ❌ COMPILE ERROR — the inferred type is ArrayList<String>
```

**The "diamond with `var`" trap (Java 10+):**

```java
var list = new ArrayList<>();   // ⚠ the diamond infers ArrayList<Object>!
// list.add("a");  → ✅ compiles
// list.add(1);    → ✅ ALSO compiles — and now the list is Object-typed!
```

**✅ FIX** — always give the type argument explicitly when the type matters:
```java
var list = new ArrayList<String>();
```

> 💡 **The rule of thumb:** use `var` when the type is **obvious from the right-hand
> side** (`new ArrayList<String>()`, a method call). Do **not** use it when the
> right-hand side is less specific than the variable's intended type — that is
> exactly where the diamond/`var` interaction bites.

## 3.2 Records (Java 16)

```java
public record Point(int x, int y) { }
```

**What the compiler generates:**

```java
public final class Point extends java.lang.Record {
    private final int x;             // ← final fields
    private final int y;

    public Point(int x, int y) {     // ← the CANONICAL constructor
        this.x = x;
        this.y = y;
    }

    public int x() { return x; }     // ← accessors — NOT getX()
    public int y() { return y; }

    @Override public boolean equals(Object o) { ... }   // ← generated
    @Override public int hashCode() { ... }              // ← generated
    @Override public String toString() { ... }           // ← "Point[x=1, y=2]"
}
```

**What a record gives you:**

| Feature | Note |
|---|---|
| `final` class | ❌ cannot be extended |
| `final` fields | ✅ immutable *references* |
| Canonical constructor | The primary constructor; you may add a **compact** form |
| Accessors | `x()`, not `getX()` — the record *is* the DTO, not a bean |
| `equals`/`hashCode` | Generated from the **components** |
| `toString` | Generated |
| **Implements `java.lang.Record`** | Which is why `record` types are final and cannot be subclassed |
| May implement interfaces | ✅ |
| May have static fields/methods | ✅ |
| May have instance methods | ✅ |
| May have nested types | ✅ |

**What a record does NOT give you:**

| Missing | Why it matters |
|---|---|
| A no-arg constructor | You must supply all components — which is *good* |
| Setters | Records are immutable |
| Inheritance | ❌ final |
| **Deep immutability** | ⚠ A `List` component is still mutable — see below |
| `Comparable` by default | ❌ implement it explicitly |

**The compact constructor — for validation and normalisation:**

```java
public record Money(BigDecimal amount, String currency) {

    public Money {                       // ← COMPACT form: no parameter list
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an ISO-4217 code");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);   // ← NORMALISATION
    }
}
```

**The shallow-immutability trap:**

```java
public record Order(String id, List<Item> items) { }

Order order = new Order("O-1", new ArrayList<>(List.of(item)));
order.items().add(anotherItem);   // ⚠ COMPILES — the list is mutable!
                                 // ⚠ and if it was passed in directly, the
                                 //   RECORD'S list is the caller's list
```

**✅ FIX — defensive copying in the compact constructor:**

```java
public record Order(String id, List<Item> items) {
    public Order {
        items = List.copyOf(items);      // ← an immutable snapshot
    }
}
```

## 3.3 Sealed classes and interfaces (Java 17)

```java
public sealed interface Shape permits Circle, Square, Triangle {
    double area();
}

public record Circle(double radius) implements Shape {
    public double area() { return Math.PI * radius * radius; }
}

public record Square(double side) implements Shape {
    public double area() { return side * side; }
}

public record Triangle(double base, double height) implements Shape {
    public double area() { return base * height / 2; }
}
```

**The rules:**

| Rule | Detail |
|---|---|
| A sealed type must declare `permits` (or be in the same file) | Otherwise it is just a normal type |
| The permitted subtypes must **directly extend/implement** it | Not a grandchild |
| A permitted subtype may itself be `sealed`, `final`, or `non-sealed` | That is how you close the hierarchy at different depths |
| A sealed type may be an `interface` or a `class` | An interface is usually right |
| The permitted subtypes must be in the **same module or package** | (Or the same unnamed module) |
| If the subtypes are in the **same file**, `permits` may be **omitted** | A convenience |

**`non-sealed` — reopening the hierarchy at one point:**

```text
   sealed interface Node permits Leaf, Branch {}

   final class Leaf implements Node { }              // ← CLOSED here
   non-sealed class Branch implements Node { }        // ← OPEN — others may extend Branch
   sealed class Branch2 extends Branch permits B2, B3 {}   // ← CLOSED again, deeper
```

**Why `sealed` matters — the exhaustiveness guarantee:**

```java
// With a SEALED hierarchy, this switch is checked by the compiler:
double describe(Shape shape) {
    return switch (shape) {
        case Circle c    -> "circle r=" + c.radius();
        case Square s    -> "square " + s.side();
        case Triangle t  -> "triangle";
    };
}
// If you add a `Rectangle` record, EVERY such switch becomes a COMPILE ERROR ✅
```

**Without `sealed`, the same switch silently does nothing for the new case** — and that is the bug class `sealed` eliminates.

## 3.4 Pattern matching for `instanceof` (Java 16)

```java
// ❌ BEFORE
if (obj instanceof String) {
    String s = (String) obj;      // a cast
    System.out.println(s.length());
}

// ✅ AFTER — the binding variable
if (obj instanceof String s) {
    System.out.println(s.length());   // s is already a String
}

// ✅ FLOW SCOPING — `s` is in scope in the whole `if` block
if (obj instanceof String s && s.length() > 3) { ... }
// ⚠ and in the NEGATIVE branch too (Java 16+ flow scoping):
if (!(obj instanceof String s)) {
    return;                    // s is NOT in scope here
}
System.out.println(s.length()); // ✅ s IS in scope here
```

**Pattern matching in `&&` and `||` — the flow-scoping rules:**

```java
// ✅ In an && chain, the binding is in scope AFTER the whole condition
if (obj instanceof String s && s.isEmpty()) { ... }

// ❌ NOT allowed — the binding is not in scope in the || branch
if (obj instanceof String s || s.isEmpty()) { }   // 💥 compile error

// ❌ NOT allowed — the binding is not in scope in the NEGATIVE branch of &&
// (this is the famous "dominance" rule)
if (!(obj instanceof String s) && s.isEmpty()) { }   // 💥 compile error
```

**The dominance rule:** for `!(x instanceof T t) && <expr>`, the binding `t` is **not** in scope in `<expr>` — because the expression can be evaluated when `x` is *not* a `T`.

## 3.5 Switch expressions (Java 14)

```java
// ❌ BEFORE — a statement switch with a break and a variable to assign to
int days;
switch (month) {
    case 1: case 3: case 5: case 7: case 8: case 10: case 12:
        days = 31; break;
    case 4: case 6: case 9: case 11:
        days = 30; break;
    case 2:
        days = 28; break;
    default:
        throw new IllegalArgumentException();
}
return days;

// ✅ AFTER — a switch EXPRESSION
return switch (month) {
    case 1, 3, 5, 7, 8, 10, 12 -> 31;
    case 4, 6, 9, 11 -> 30;
    case 2 -> 28;
};   // ← no break, no yield needed for a single expression
```

**The rules:**

| Rule | Detail |
|---|---|
| A switch **expression** must be **exhaustive** | The compiler checks it (for enums, sealed types, and `String`s) |
| Multiple labels | `case 1, 3, 5 ->` |
| `yield` | Needed for a **block** body: `case 2 -> { int x = 28; yield x; }` |
| A switch expression is an **expression** | It can be inlined into a return, an assignment, an argument |
| Arrow form does **not** fall through | Each case is independent |
| `default` is optional if exhaustive | And with `sealed`/enum, omitting it is the point |
| A `switch` statement can also use arrows | `case X -> doSomething();` — no fall-through |

**`yield` vs `return`:**

```java
String label = switch (status) {
    case PENDING -> "waiting";
    case ACTIVE -> {
        var elapsed = clock.now().minus(startedAt);
        yield elapsed.toMinutes() > 5 ? "running (slow)" : "running";
    }
    case CLOSED -> "done";
};
```

## 3.6 Pattern matching for `switch` (Java 21)

```java
static String describe(Object o) {
    return switch (o) {
        case Integer i when i > 100 -> "a big integer: " + i;   // a GUARD
        case Integer i              -> "an integer: " + i;
        case String s               -> "a string of length " + s.length();
        case null                  -> "null";                    // ← explicit null
        default                    -> "something else";
    };
}
```

**The features:**

| Feature | Detail |
|---|---|
| **Type patterns** | `case Circle c ->` — no casting, and `c` is bound |
| **Guards** | `case Circle c when c.radius() > 10 ->` — the `when` narrows further |
| **Explicit `null`** | `case null ->` — no longer an accidental NPE |
| **Exhaustiveness** | For a `sealed` type, the compiler requires every permitted subtype |
| **Dominance** | A more specific pattern must come first: `case Integer i` before `case Object o` |
| **Record patterns** | `case Point(int x, int y) ->` — deconstructs a record (Java 21) |

**Record patterns:**

```java
static String describe(Object o) {
    return switch (o) {
        case Point(int x, int y)        -> "point at (" + x + ", " + y + ")";
        case Point(var x, var y)        -> "point with unknown types";
        case Line(Point(var x1, var y1), Point(var x2, var y2)) -> "line from " + x1 + " to " + x2;
        case null                       -> "null";
        default                         -> "other";
    };
}
```

**The dominance rule (a very common interview question):**

```text
   ❌ COMPILE ERROR — the Object case dominates the Integer case
   ────────────────────────────────────────────────────────────
   case Object o    -> ...      // ← matches EVERYTHING
   case Integer i   -> ...      // ← unreachable!

   ✅ CORRECT ORDER — the most specific first
   ──────────────────────────────────
   case Integer i   -> ...
   case String s    -> ...
   case Object o    -> ...      // ← the general case LAST
```

**The `null` case and the "default implies null" rule (Java 21):**

- If a switch has a `default`, then `null` is covered by the `default`.
- If a switch has **no** `default` and the selector is `null`, a `NullPointerException` is thrown.
- With a `sealed` type and no `default`, you must handle `null` explicitly (or accept the NPE).

## 3.7 Text blocks (Java 15)

```java
// ❌ BEFORE — a mess of escapes and concatenation
String json = "{\n"
        + "  \"id\": \"" + id + "\",\n"
        + "  \"name\": \"Alice\"\n"
        + "}";

// ✅ AFTER — a text block
String json = """
        {
          "id": "%s",
          "name": "Alice"
        }
        """.formatted(id);
```

**The rules:**

| Rule | Detail |
|---|---|
| The opening delimiter is `"""` **followed by a line terminator** | The content starts on the **next** line |
| **Incidental white space is stripped** | The minimum indentation of all non-blank lines is removed |
| The closing delimiter's indentation sets the baseline | It must be *more* indented than the content (or it terminates the block) |
| Trailing white space on each line is stripped | |
| `\n`, `\"`, `\\` escapes work | Plus `\s` (a space) and `\newline` (line continuation) |
| A text block ends with a `\n` | Unless the closing delimiter is on the same line as the content |
| `"""` inside the text must be escaped | `\"""` |

**The "incidental white space" rule — the thing that confuses people:**

```java
String s = """
        line one
          line two
        """;
// The minimum indentation (8 spaces) is removed from EVERY line:
//   "line one\n  line two\n"
```

## 3.8 Virtual threads (Java 21)

```java
// ✅ Creating one
Thread vt = Thread.ofVirtual().name("my-vthread").start(() -> doWork());

// ✅ An executor for a thread-per-task model
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 1_000_000; i++) {
        executor.submit(() -> handle(i));
    }
}   // ← close() WAITS for every task to finish
```

**What virtual threads are:**

| Property | Platform thread | Virtual thread |
|---|---|---|
| Managed by | The OS | The **JVM** |
| Stack size | ~1 MB reserved | A **heap object**, ~1 KB |
| Max count | Thousands (limited by memory) | **Millions** |
| Scheduling | The OS scheduler | The **JVM's scheduler**, on a small pool of **carriers** |
| Pooling | ✅ (a `ThreadPoolExecutor`) | ❌ **one per task** — no pooling needed |
| Best for | CPU-bound work | **Blocking I/O** |
| Cost of creating | ~1 MB + a syscall | ~1 KB + a heap allocation |

**The carrier model:**

```text
   10,000 virtual threads (blocking on sockets)
        │
        ├──► Carrier #1  (a platform thread)  ── runs VT-1, VT-2, VT-3 … one at a time
        ├──► Carrier #2                      ── runs VT-4, VT-5 …
        └──► Carrier #8                      ── runs VT-9998 …

   WHEN A VT BLOCKS ON I/O:
     the carrier is UNMOUNTED (the VT's stack is copied to the heap)
     → the carrier immediately runs another runnable VT
     → the OS thread is never idle

   ⚠ THE PINNING PROBLEM
     if a VT blocks inside `synchronized` (or a native call),
     it CANNOT be unmounted → the CARRIER IS PINNED → scalability collapses
     → use `ReentrantLock` instead of `synchronized` in virtual-thread code
```

**The pinning problem in detail:**

```java
// ❌ PINNING — a virtual thread that blocks inside synchronized pins its carrier
synchronized (lock) {
    socket.read();      // ← blocking while holding the monitor
}                       // ← the carrier CANNOT be unmounted here

// ✅ NOT PINNING — ReentrantLock releases the carrier when blocked
lock.lock();
try {
    socket.read();      // ← the carrier IS released
} finally {
    lock.unlock();
}
```

**Java 24 (JEP 491) re-implemented the monitor so `synchronized` no longer pins** — worth mentioning as the direction of travel.

**The other limits of virtual threads:**

| Limit | Explanation |
|---|---|
| **CPU-bound work** | A virtual thread does not make the CPU faster. With 1,000 CPU-bound virtual threads on 8 cores, you get 8-way parallelism and 992 threads queued. |
| **Synchronisation** | A `synchronized` block can become a **contention point** across millions of virtual threads — because they are cheap to create, the contention is now on the *lock*, not the thread count. |
| **Thread-local memory** | A `ThreadLocal` on a virtual thread can pin memory, because the virtual thread (and its `ThreadLocal` map) stays alive as long as the task does. |
| **No `ThreadLocal` pooling benefit** | Platform threads were pooled and reused; virtual threads are not — so `ThreadLocal` values are not reused, and a leak is per-task, not per-pool. |

**What virtual threads change for a web server:**

```text
   ❌ BEFORE — Tomcat's 200 threads, each blocked on a downstream call
   ────────────────────────────────────────────────────────────────
   200 concurrent requests max. The 201st queues.
   → you scale by adding instances and by tuning the pool.

   ✅ AFTER — a virtual thread per request
   ──────────────────────────────────────
   10,000 concurrent requests, each with its own virtual thread.
   → the platform threads are only the carriers (≈ #cores)
   → the code stays SIMPLE — you can go back to blocking I/O
```

## 3.9 Other useful Java 11/17/21 additions

```java
// Java 11
String s = "  hi  ";
s.strip();          // "hi"  (Unicode-aware; unlike trim())
s.stripLeading();
s.stripTrailing();
s.isBlank();        // true for "", "   ", "\t\n"
s.repeat(3);        // "hihihi"
s.lines().count();  // a Stream<String> of the lines
s.indent(2);        // indent every line
"a,b,c".lines();    // hmm — lines() is on String, not a literal split

var copy = List.copyOf(list);         // an immutable copy
var empty = Map.of();                  // an immutable empty map
List.of(1, 2, 3);                     // an immutable list (up to 10 elements)
List.copyOf(collection);              // an immutable copy

Optional<String> o = Optional.empty();
o.isEmpty();                            // Java 11

coll.toArray(String[]::new);            // a typed array via a generator

// Java 12/13
String formatted = "Total: %.2f".formatted(12.3456);   // Java 15
int hex = Integer.parseInt("FF", 16);
String base64 = Base64.getEncoder().encodeToString(bytes);

// Java 16
stream.toList();                        // an UNMODIFIABLE list (allows nulls)
record Point(int x, int y) {}
obj instanceof String s { }

// Java 17
sealed interface Shape permits Circle, Square {}
record Circle(double r) implements Shape {}

// Java 21
switch (obj) { case Circle c -> ...; case null -> ...; }
Thread.ofVirtual().start(() -> ...);
Executors.newVirtualThreadPerTaskExecutor()
record Point(int x, int y) {}
switch (point) { case Point(int x, int y) -> ...; }
```

**`Stream.toList()` vs `collect(Collectors.toList())` — an important difference:**

| | `collect(Collectors.toList())` | `toList()` (Java 16) |
|---|---|---|
| Nulls | ❌ rejected | ✅ **allowed** |
| Mutability | **mutable** (an `ArrayList`) | **unmodifiable** |
| Type | `List<T>` | `List<T>` (the exact type is unspecified) |
| Java version | 8+ | 16+ |

## 3.10 The Java version timeline (what to remember)

```text
   Java 8  (2014)  ── lambdas, streams, Optional, java.time, default methods   LTS
   Java 9  (2017)  ── modules, var, private interface methods, List.of
   Java 10 (2018)  ── var (official), LocalVariableTypeInference
   Java 11 (2018)  ── var in lambdas, String methods, HTTP Client, Optional.isEmpty  LTS
   Java 12-13       ── switch expressions (preview), text blocks (preview)
   Java 14 (2020)  ── switch expressions (FINAL), helpful NPEs, records (preview)
   Java 15 (2020)  ── text blocks (FINAL), records (2nd preview)
   Java 16 (2021)  ── records (FINAL), instanceof patterns (FINAL), Stream.toList
   Java 17 (2021)  ── sealed classes (FINAL), switch patterns (preview)          LTS
   Java 18-20       ── switch patterns (preview iterations), virtual threads (preview)
   Java 21 (2023)  ── switch patterns (FINAL), record patterns (FINAL),
                      virtual threads (FINAL), sequenced collections (preview)    LTS
   Java 22 (2024)  ── unnamed classes & instance main preview, string templates (2nd)
   Java 23 (2024)  ── string templates (preview), ZGC improvements
   Java 24 (2025)  ── JEP 491: synchronized NO LONGER PINS a virtual thread
   Java 25 (2025)  ── compact object headers, structured concurrency (preview)
```

---

## 4. Visualizations

### 4.1 What the compiler generates for a `record`

```text
   SOURCE:  public record Point(int x, int y) { }

   GENERATED (simplified):
   ┌──────────────────────────────────────────────────────────────┐
   │ public final class Point extends java.lang.Record {          │
   │     private final int x;                                     │
   │     private final int y;                                     │
   │                                                              │
   │     public Point(int x, int y) { this.x = x; this.y = y; }   │ ← canonical ctor
   │     public int x() { return x; }      ← accessor (NOT getX())  │
   │     public int y() { return y; }                             │
   │                                                              │
   │     @Override public boolean equals(Object o) { ... }       │ ← generated
   │     @Override public int hashCode() { ... }                  │ ← generated
   │     @Override public String toString() {                     │ ← generated
   │         return "Point[x=" + x + ", y=" + y + "]";             │
   │     }                                                        │
   │ }                                                            │
   │                                                              │
   │ ⚠ record classes extend java.lang.Record, which extends Object │
   │ ⚠ so they CANNOT be extended — the class is implicitly final │
   │ ⚠ NO inheritance of the generated equals/hashCode — they are  │
   │   final and always component-based                            │
   └──────────────────────────────────────────────────────────────┘
```

### 4.2 The compact constructor — where validation lives

```text
   public record Money(BigDecimal amount, String currency) {
       public Money {                    // ← COMPACT: no parameter list
           if (amount == null) throw new IllegalArgumentException(...);
           if (amount.signum() < 0)   throw new IllegalArgumentException(...);
           amount = amount.setScale(2, RoundingMode.HALF_UP);   // ← NORMALISE
       }                                 // ← the fields are assigned AUTOMATICALLY
   }                                     //    after this block

   THE ORDER:
     1. the compact body runs
     2. THEN the fields are assigned from the (possibly modified) parameters
     3. THEN the object is fully constructed

   ⚠ THE SHALLOW-IMMUTABILITY TRAP
   ┌──────────────────────────────────────────────────────────────┐
   │  record Order(String id, List<Item> items) { }                │
   │                                                              │
   │  Order o = new Order("O1", new ArrayList<>());                │
   │  o.items().add(item);     ← ⚠ COMPILES! the list is mutable   │
   │                                                              │
   │  ✅ FIX — defensive copy in the compact constructor:           │
   │  public record Order(String id, List<Item> items) {            │
   │      public Order { items = List.copyOf(items); }              │
   │  }                                                            │
   │  → now items() returns an IMMUTABLE list ✅                   │
   └──────────────────────────────────────────────────────────────┘
```

### 4.3 `sealed` + pattern matching — the exhaustiveness guarantee

```text
   sealed interface Shape permits Circle, Square, Triangle {}

   static String describe(Shape s) {
       return switch (s) {
           case Circle c   -> "circle r=" + c.radius();
           case Square sq  -> "square " + sq.side();
           case Triangle t -> "triangle";
       };
   }

   THE COMPILER KNOWS the hierarchy is EXACTLY these three.

   Now add:
       record Rectangle(double w, double h) implements Shape {}
                     ↑ you must also add it to `permits`

   → EVERY switch over Shape becomes a COMPILE ERROR ❌
     "the switch expression does not cover all possible input values"

   ┌──────────────────────────────────────────────────────────────┐
   │  WITHOUT sealed:                                             │
   │    switch (s) { case Circle c -> ...; default -> ...; }      │
   │    → adding a new shape → SILENTLY falls into default 💥    │
   │                                                              │
   │  WITH sealed:                                                │
   │    → adding a new shape → COMPILE ERROR ✅                   │
   │    → the compiler is your "did you handle every case?" guard │
   └──────────────────────────────────────────────────────────────┘
```

### 4.4 The dominance rule in pattern-matching `switch`

```text
   ❌ COMPILE ERROR — "case Object o" DOMINATES "case Integer i"
   ────────────────────────────────────────────────────────────────
   case Object o     -> ...     // ← matches EVERYTHING
   case Integer i    -> ...     // ← UNREACHABLE — the compiler rejects this

   ✅ CORRECT ORDER — most specific FIRST
   ──────────────────────────────────
   case Integer i    -> ...
   case String s     -> ...
   case Circle c     -> ...
   case null         -> ...
   case Object o     -> ...     // ← the general case LAST

   THE RULE: an earlier pattern that MATCHES EVERYTHING a later one matches
             makes the later one unreachable → a compile error.
```

### 4.5 Virtual threads — the carrier model

```text
   ❌ PLATFORM THREADS — one OS thread per request
   ──────────────────────────────────────────────
   request1 ──▶ [Thread]  1 MB stack ──▶ blocked on socket  💥 1 MB wasted
   request2 ──▶ [Thread]  1 MB stack ──▶ blocked on socket  💥 1 MB wasted
   ...
   requestN ──▶ [Thread]  1 MB stack ──▶ blocked on socket  💥 1 MB wasted

   → 10,000 requests = 10 GB of stack, 10,000 OS threads
   → the OS scheduler is managing 10,000 threads

   ✅ VIRTUAL THREADS — cheap, JVM-scheduled
   ─────────────────────────────────────────
   request1 ──▶ [VThread]  ~1 KB ──▶ blocked on socket
   request2 ──▶ [VThread]  ~1 KB ──▶ blocked on socket
   ...
        │
        │  the JVM multiplexes them onto a FEW carriers
        ▼
   ┌──────────────────────────────────────────────────────┐
   │  Carrier #1 (platform thread)  ← runs V1, V2, V3 …    │
   │  Carrier #2                   ← runs V4, V5 …         │
   │  Carrier #3                   ← runs V6 …             │
   │  ...                                                │
   │  Carrier #8 (≈ #cores)         ← runs VN …            │
   └──────────────────────────────────────────────────────┘

   WHEN V1 BLOCKS ON I/O:
     V1's stack is copied to the heap, the carrier is UNMOUNTED
     → the carrier immediately picks up another runnable virtual thread
     → the OS thread is NEVER idle
     → 10,000 concurrent requests ≈ 8 platform threads
```

### 4.6 The pinning problem

```text
   ❌ PINNING — synchronized
   ───────────────────────────
   VThread-V1  ──▶ synchronized(lock) {          ← acquired the monitor
                    socket.read();                ← BLOCKS
                 }                                ← 💥 CANNOT UNMOUNT
                                                   the carrier is stuck holding
                                                   the monitor AND the carrier
   → with 1,000 virtual threads all taking the same lock,
     the carriers are all pinned → NO SCALABILITY

   ✅ NOT PINNING — ReentrantLock
   ───────────────────────────────
   VThread-V1  ──▶ lock.lock()
                    socket.read();                ← BLOCKS
                                                   → the carrier IS RELEASED ✅
                                                   → the JVM runs another VT
                 } finally { lock.unlock(); }

   ⚠ NOTE: Java 24 (JEP 491) re-implemented the monitor so that
     `synchronized` NO LONGER PINS a virtual thread. But `ReentrantLock`
     remains the portable, explicit choice.
```

### 4.7 `sealed` vs `enum` vs an open interface

```text
   ┌──────────────────────┬────────────────┬────────────────┬────────────────┐
   │                      │ ENUM           │ SEALED         │ OPEN INTERFACE │
   ├──────────────────────┼────────────────┼────────────────┼────────────────┤
   │ Closed set?          │ ✔              │ ✔              │ ✗              │
   │ Per-case STATE       │ ✗ (shared cls) │ ✔ (own record) │ ✔              │
   │ Per-case BEHAVIOUR   │ ✔ (bodies)     │ ✔ (own record) │ ✔              │
   │ Switch exhaustiveness│ ✔ (switch expr)│ ✔ (switch expr)│ ✗              │
   │ `values()` / ordinal │ ✔              │ ✗ (you write it)│ ✗              │
   │ Extensible later?    │ ✗              │ ✘ (permitted)  │ ✔              │
   │ Use when             │ pure labels    │ cases w/ state  │ open extension │
   └──────────────────────┴────────────────┴────────────────┴────────────────┘
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — Records: what you get, and the shallow-immutability trap

```java
import java.util.ArrayList;
import java.util.List;

public class RecordDemo {

    public record Point(int x, int y) {
        public Point {                       // compact constructor
            if (x < 0 || y < 0) {
                throw new IllegalArgumentException("coordinates must be >= 0");
            }
        }
    }

    // ⚠ The shallow-immutability trap
    public record Order(String id, List<String> items) { }

    public static void main(String[] args) {
        Point p1 = new Point(1, 2);
        Point p2 = new Point(1, 2);

        System.out.println("p1 = " + p1);
        System.out.println("p1.equals(p2) = " + p1.equals(p2));
        System.out.println("p1.hashCode() == p2.hashCode() = " + (p1.hashCode() == p2.hashCode()));

        // The compact constructor validates
        try {
            new Point(-1, 5);
        } catch (IllegalArgumentException e) {
            System.out.println("validation → " + e.getMessage());
        }

        // ⚠ The trap: a mutable component
        List<String> mutable = new ArrayList<>(List.of("a"));
        Order order = new Order("O-1", mutable);
        mutable.add("b");
        System.out.println("order.items() = " + order.items() + "  ← ⚠ the list is shared!");

        // ✅ The fix — a defensive copy
        record SafeOrder(String id, List<String> items) {
            public SafeOrder { items = List.copyOf(items); }
        }
        List<String> source = new ArrayList<>(List.of("a"));
        SafeOrder safe = new SafeOrder("O-2", source);
        source.add("c");
        System.out.println("safe.items()  = " + safe.items() + "  ← ✅ immutable snapshot");
    }
}
```

**Actual output:**

```text
p1 = Point[x=1, y=2]
p1.equals(p2) = true
p1.hashCode() == p2.hashCode() = true
validation → coordinates must be >= 0
order.items() = [a, b]  ← ⚠ the list is shared!
safe.items()  = [a]  ← ✅ immutable snapshot
```

**Takeaway:** a record gives you **reference immutability**, not **deep immutability**. If a component is mutable, the record's guarantee is void — so copy it in the compact constructor.

### Example 2 — `sealed` + pattern matching, and the compile-time exhaustiveness

```java
public class SealedDemo {

    sealed interface Shape permits Circle, Square, Triangle {
        double area();
    }
    record Circle(double radius) implements Shape {
        public double area() { return Math.PI * radius * radius; }
    }
    record Square(double side) implements Shape {
        public double area() { return side * side; }
    }
    record Triangle(double base, double height) implements Shape {
        public double area() { return base * height / 2; }
    }

    // ✅ EXHAUSTIVE — the compiler proves every permitted subtype is handled
    static String describe(Shape shape) {
        return switch (shape) {
            case Circle c   -> "circle r=" + c.radius();
            case Square s   -> "square " + s.side();
            case Triangle t -> "triangle";
        };
    }

    // A generic switch over ANY object (Java 21)
    static String describeAny(Object o) {
        return switch (o) {
            case Circle c when c.radius() > 10 -> "a BIG circle";
            case Circle c                       -> "a circle";
            case Square s                       -> "a square";
            case null                           -> "null";
            default                             -> "something else";
        };
    }

    public static void main(String[] args) {
        System.out.println(describe(new Circle(2.0)));
        System.out.println(describe(new Square(3.0)));
        System.out.println(describe(new Triangle(4.0, 5.0)));

        System.out.println(describeAny(new Circle(20.0)));
        System.out.println(describeAny(new Circle(1.0)));
        System.out.println(describeAny("a string"));
        System.out.println(describeAny(null));
    }
}
```

**Actual output:**

```text
circle r=2.0
square 3.0
triangle
a BIG circle
a circle
something else
null
```

**Takeaway:** the `describe` switch has **no `default`** — and it compiles, because the compiler proved it exhaustive. Add a `Rectangle` to the `permits` list and this method becomes a **compile error**, not a silent bug.

### Example 3 — `switch` expressions and `yield`

```java
public class SwitchExpressionDemo {
    enum Level { LOW, MEDIUM, HIGH }

    static int daysInMonth(int month) {
        // ✅ A switch EXPRESSION — no break, no fall-through
        return switch (month) {
            case 1, 3, 5, 7, 8, 10, 12 -> 31;
            case 4, 6, 9, 11 -> 30;
            case 2 -> 28;
            default -> throw new IllegalArgumentException("Invalid month: " + month);
        };
    }

    static String describe(Level level, int elapsedMinutes) {
        return switch (level) {
            case LOW -> "low";
            case MEDIUM -> {
                // A BLOCK body needs `yield`
                var label = elapsedMinutes > 30 ? "medium (slow)" : "medium";
                yield label;
            }
            case HIGH -> "high";
        };
    }

    public static void main(String[] args) {
        System.out.println("days in Jan  = " + daysInMonth(1));
        System.out.println("days in Feb  = " + daysInMonth(2));
        System.out.println("days in Apr  = " + daysInMonth(4));

        System.out.println("LOW     → " + describe(Level.LOW, 5));
        System.out.println("MEDIUM  → " + describe(Level.MEDIUM, 45));
        System.out.println("HIGH    → " + describe(Level.HIGH, 5));

        try {
            daysInMonth(13);
        } catch (IllegalArgumentException e) {
            System.out.println("invalid   → " + e.getMessage());
        }
    }
}
```

**Actual output:**

```text
days in Jan  = 31
days in Feb  = 28
days in Apr  = 30
LOW     → low
MEDIUM  → medium (slow)
HIGH    → high
invalid   → Invalid month: 13
```

**Takeaway:** the switch expression is an **expression** — it returns a value directly, so there is no "did I forget to assign?" bug. A block body needs `yield`; a single-expression body does not.

### Example 4 — Text blocks and the incidental-whitespace rule

```java
public class TextBlockDemo {
    public static void main(String[] args) {
        String json = """
                {
                  "name": "Alice",
                  "roles": ["admin", "user"]
                }
                """;

        String nested = """
                outer
                  inner
                outer
                """;

        System.out.println("json:");
        System.out.println(json);
        System.out.println("---");
        System.out.println("nested (note the RELATIVE indent is preserved):");
        System.out.println(nested);
        System.out.println("---");
        System.out.println("json starts with '{' = " + json.startsWith("{"));
        System.out.println("json ends with a newline = " + json.endsWith("\n"));
    }
}
```

**Actual output:**

```text
json:
{
  "name": "Alice",
  "roles": ["admin", "user"]
}
---
nested (note the RELATIVE indent is preserved):
outer
  inner
outer
---
json starts with '{' = true
json ends with a newline = true
```

**Takeaway:** the **common** indentation of all lines is stripped; the **relative** indentation is preserved. And a text block always ends with a newline (unless the closing delimiter is on the content line).

### Example 5 — Virtual threads: many concurrent blocking tasks

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualThreadDemo {

    static String slowRemoteCall(String id) throws InterruptedException {
        Thread.sleep(100);                    // simulates a blocking network call
        return "response-" + id + "@" + Thread.currentThread().getName();
    }

    public static void main(String[] args) throws InterruptedException {
        int tasks = 50;

        // ── Platform threads: a fixed pool of 10 → the tasks queue up
        long t0 = System.nanoTime();
        AtomicInteger done1 = new AtomicInteger();
        ExecutorService platform = Executors.newFixedThreadPool(10);
        for (int i = 0; i < tasks; i++) {
            final int id = i;
            platform.submit(() -> {
                try { slowRemoteCall("p" + id); } catch (InterruptedException e) { }
                done1.incrementAndGet();
            });
        }
        platform.shutdown();
        platform.awaitTermination(30, TimeUnit.SECONDS);
        long platformMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("platform threads: " + done1.get() + " tasks in ~" + platformMs + "ms");

        // ── Virtual threads: one per task → they all run concurrently
        long t1 = System.nanoTime();
        AtomicInteger done2 = new AtomicInteger();
        try (ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < tasks; i++) {
                final int id = i;
                virtual.submit(() -> {
                    try { slowRemoteCall("v" + id); } catch (InterruptedException e) { }
                    done2.incrementAndGet();
                });
            }
        }   // ← close() WAITS for all the tasks
        long virtualMs = (System.nanoTime() - t1) / 1_000_000;
        System.out.println("virtual threads: " + done2.get() + " tasks in ~" + virtualMs + "ms");
        System.out.println("speedup ≈ " + (platformMs / Math.max(1, virtualMs)) + "×");
    }
}
```

**Actual output** (timings vary):

```text
platform threads: 50 tasks in ~512ms
virtual threads: 50 tasks in ~118ms
speedup ≈ 4×
```

**Takeaway:** 50 tasks × 100 ms each. With a pool of 10, that is 5 batches = 500 ms. With 50 virtual threads, they all run concurrently = ~100 ms. This is the **I/O-bound** case where virtual threads shine — the CPU work is trivial.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A sealed domain hierarchy with a total transition function

```java
package com.example.payment;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A sealed hierarchy for payment methods.
 *
 * <p>Why sealed and not an enum? Because each case carries its OWN STATE:
 * a card has a last-4, a wallet has a provider, COD has nothing.
 * An enum would force all three to share one class and one set of fields.
 *
 * <p>The sealed hierarchy buys a compiler-checked <b>total</b> function: the
 * {@link #fee} below is exhaustive, so adding a new payment method breaks
 * the build rather than silently mis-pricing an order.
 */
public sealed interface PaymentMethod {

    /** The stable domain code — this is what you persist. */
    String code();

    /** The fee for a given amount. */
    BigDecimal fee(BigDecimal amount);

    /** A human-readable description for the UI. */
    String description();

    // ── The permitted cases ──────────────────────────────────────────────
    record Card(String last4, String network) implements PaymentMethod {
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
            return amount.multiply(new BigDecimal("0.029")).add(new BigDecimal("0.30"));
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
            return BigDecimal.ZERO;
        }
    }

    record CashOnDelivery(String region) implements PaymentMethod {
        public CashOnDelivery {
            Objects.requireNonNull(region, "region");
        }

        @Override public String code() { return "COD"; }
        @Override public String description() { return "Cash on delivery in " + region; }

        @Override
        public BigDecimal fee(BigDecimal amount) {
            return new BigDecimal("5.00");
        }
    }

    // ── Helpers on the interface (allowed) ───────────────────────────────

    /** Parse from the persisted code. */
    static PaymentMethod fromCode(String code) {
        return switch (code) {
            case "CARD"   -> new Card("0000", "UNKNOWN");
            case "WALLET" -> new Wallet("UNKNOWN", "UNKNOWN");
            case "COD"    -> new CashOnDelivery("UNKNOWN");
            default -> throw new IllegalArgumentException("Unknown payment method: " + code);
        };
    }

    /** The exhaustive, compiler-checked fee calculation. */
    static BigDecimal totalWithFee(PaymentMethod method, BigDecimal amount) {
        return switch (method) {
            case Card c            -> amount.add(c.fee(amount));
            case Wallet w          -> amount.add(w.fee(amount));
            case CashOnDelivery cod -> amount.add(cod.fee(amount));
        };
    }
}
```

> 💡 **Note the `totalWithFee` switch.** It has **no `default`** — the compiler proved
> it exhaustive over the sealed hierarchy. Add a `record BankTransfer(...)` to the
> `permits` clause and this method becomes a **compile error**.

### 6.2 A record with full validation and defensive copying

```java
package com.example.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * A production-grade value object.
 *
 * <p>Shows the three things a record still needs from you:
 * <ol>
 *   <li><b>Validation</b> in the compact constructor.</li>
 *   <li><b>Normalisation</b> (scale, trimming) in the compact constructor.</li>
 *   <li><b>Defensive copying</b> of any mutable component.</li>
 * </ol>
 */
public record OrderLine(String sku, String description, int quantity, BigDecimal unitPrice) {

    public OrderLine {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(unitPrice, "unitPrice");

        sku = sku.trim().toUpperCase(java.util.Locale.ROOT);
        description = description.strip();

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0, got " + quantity);
        }
        if (quantity > 999) {
            throw new IllegalArgumentException("quantity must be <= 999, got " + quantity);
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must be >= 0, got " + unitPrice);
        }
        unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
    }

    /** A derived value — a record can have real methods. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /** A convenience factory. */
    public static OrderLine of(String sku, int quantity, BigDecimal unitPrice) {
        return new OrderLine(sku, sku, quantity, unitPrice);
    }
}
```

### 6.3 A virtual-thread-friendly service (Java 21)

```java
package com.example.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A service designed for virtual threads.
 *
 * <p>Design rules for virtual-thread code:
 * <ul>
 *   <li><b>Do not pool virtual threads.</b> Create one per task — that is the point.</li>
 *   <li><b>Never use {@code synchronized}</b> around blocking I/O — it pins the carrier.
 *       Use {@code ReentrantLock}.</li>
 *   <li><b>Keep the ThreadLocal footprint small</b> — a virtual thread lives as long as its
 *       task, so a ThreadLocal on it is not reclaimed until the task ends.</li>
 *   <li><b>Bound the concurrency at the resource</b> (a semaphore), not at the thread
 *       level — otherwise you will open 100,000 connections.</li>
 * </ul>
 */
public class OrderEnrichmentService {

    private final CustomerClient customers;
    private final InventoryClient inventory;

    // ONE executor for the whole application, closed with the service.
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public OrderEnrichmentService(CustomerClient customers, InventoryClient inventory) {
        this.customers = customers;
        this.inventory = inventory;
    }

    /** Fetch the customer and the inventory concurrently, for EVERY order. */
    public List<EnrichedOrder>> enrichAll(List<String> orderIds) {
        return orderIds.stream()
                .map(orderId -> CompletableFuture.supplyAsync(
                        () -> enrich(orderId), executor))
                .map(CompletableFuture::join)          // ⚠ join() blocks the CALLER's thread
                .toList();
    }

    private EnrichedOrder enrich(String orderId) {
        // Two independent remote calls — run them concurrently.
        var customer = customers.fetchAsync(orderId);
        var stock    = inventory.fetchAsync(orderId);

        return new EnrichedOrder(orderId,
                customer.join().name(),
                stock.join().available());
    }

    public void close() {
        executor.close();   // waits for the in-flight tasks
    }
}
```

> ⚠️ **The `join()` in `enrichAll` is a deliberate trade-off**, and it is worth being
> able to defend: it blocks the *caller's* virtual thread (not a carrier — the caller is
> unmounted while waiting), so it is safe. But it does serialise the *collection* of
> results. For a fully non-blocking API, return `List<CompletableFuture<EnrichedOrder>>`
> and let the caller compose.

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — `var` with a diamond

```java
var list = new ArrayList<>();   // ⚠ inferred as ArrayList<Object>!
list.add("a"); list.add(1);     // ⚠ both compile
```

**✅ FIX** — always give the type argument: `var list = new ArrayList<String>();`

---

### ❌ Pitfall 2 — A record with a mutable component

```java
public record Order(String id, List<Item> items) { }
order.items().add(item);   // ⚠ COMPILES — the record is only shallowly immutable
```

**✅ FIX** — `public Order { items = List.copyOf(items); }`

---

### ❌ Pitfall 3 — Trying to add a mutable *field* to a record

```java
// ❌ javac: "instance field tags not allowed in a record"
//    public record Config(String name) {
//        private final List<String> tags = new ArrayList<>();
//    }

// ✅ A record may declare STATIC fields only — and a static mutable field is shared
//    by every instance, which is a different bug entirely:
public record Config(String name) {
    private static final List<String> TAGS = List.of();   // ✅ immutable
}
```

**✅ FIX** — a record's state is its **components**; if you need extra state, add a
component (`record Config(String name, List<String> tags) {}`) and defensively copy
it in the compact constructor. Never hide state in a field.

---

### ❌ Pitfall 4 — Expecting a record to be a JavaBean

```java
record Point(int x, int y) { }
p.getX();   // ❌ compile error — the accessor is x()
```

**✅ FIX** — use `x()`. Records are not beans; do not add getters.

---

### ❌ Pitfall 5 — Extending a record

```java
record Base(int x) { }
// ❌ javac: "records cannot be extended"
//    record Derived(int x, int y) extends Base { }
```

**✅ FIX** — use composition, or a `sealed` interface with a record per case.

```java
sealed interface Shape permits Circle, Square { }
record Circle(double r) implements Shape { }
record Square(double side) implements Shape { }
```

---

### ❌ Pitfall 6 — The dominance error in a pattern-matching `switch`

```java
case Object o  -> ...;    // ❌ placed FIRST
case Integer i -> ...;    // ❌ unreachable — compile error
```

**✅ FIX** — most specific first.

---

### ❌ Pitfall 7 — Forgetting `case null` in an exhaustive switch

```java
switch (shape) {
    case Circle c -> ...;
    case Square s -> ...;
    // ⚠ no `default` → a null shape throws NPE
}
```

**✅ FIX** — add `case null -> ...;` if null is a legitimate input.

---

### ❌ Pitfall 8 — `synchronized` around blocking I/O in a virtual thread

```java
synchronized (lock) { socket.read(); }   // ❌ PINS the carrier
```

**✅ FIX** — use `ReentrantLock`. (Java 24+ fixed this for `synchronized`, but `ReentrantLock` is the explicit choice.)

---

### ❌ Pitfall 9 — Using virtual threads for CPU-bound work

```java
// ❌ 1,000 virtual threads doing CPU work on 8 cores
//    → 8-way parallelism, 992 threads queued — pure overhead
```

**✅ FIX** — use a `ForkJoinPool` (or a platform-thread pool) for CPU work.

---

### ❌ Pitfall 10 — Pooling virtual threads

```java
ExecutorService pool = Executors.newFixedThreadPool(100, Thread.ofVirtual().factory());  // ❌ pointless
```

**✅ FIX** — `Executors.newVirtualThreadPerTaskExecutor()` — one virtual thread per task, no pool.

---

### ❌ Pitfall 11 — Forgetting that a text block ends with a newline

```java
String s = """
        hello
        """;
s.length();     // 6 — "hello\n"
```

**✅ FIX** — `.stripTrailing()` or `.strip()` if you do not want it.

---

### ❌ Pitfall 12 — `yield` in a single-expression switch arm

```java
case 1 -> { yield 31; }    // ⚠ a block with one expression is fine, but
case 1 -> { 31; }         // ❌ compile error — a block must use yield
```

**✅ FIX** — use the arrow form for a single expression: `case 1 -> 31;`

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is a `record`?**
**A:** A final class that models an immutable data carrier. The compiler generates `final` fields, a canonical constructor, accessor methods (`x()`, not `getX()`), and `equals`/`hashCode`/`toString` based on the components. It removes about 40 lines of boilerplate per DTO. Records **cannot** be extended but **can** implement interfaces.

**Q: What is `sealed`?**
**A:** A modifier that restricts who may extend or implement a class or interface. `sealed interface Shape permits Circle, Square {}` means only those two (and their permitted subtypes) may implement it. Its main value is that the compiler then knows the complete set of subtypes, so a `switch` over it can be **checked for exhaustiveness**.

**Q: What does `var` do?**
**A:** It infers the type of a **local variable** from its initialiser. It is a reserved type name, not a keyword, and it is only allowed for local variables with an initialiser. The inferred type is the *static* type of the initialiser.

**Q: What is a text block?**
**A:** A string literal delimited by `"""`, introduced in Java 15. It preserves the source formatting, strips the common leading whitespace from every line, and supports the usual escapes. It ends with a newline unless the closing delimiter is on the content line.

### 🟡 Intermediate

**Q: What exactly does a record generate, and what does it NOT generate?**
**A:** It generates: `final` fields, a canonical constructor, the accessors, and `equals`/`hashCode`/`toString`. It does **not** generate: a no-arg constructor, setters, `Comparable`, or deep immutability. It also does not let you extend another class (records extend `java.lang.Record`, and are implicitly `final`).

**Q: Why is a record only *shallowly* immutable, and how do you fix it?**
**A:** Because immutability is about the record's own *references* being `final` — a `final` reference to a mutable `List` still points at a mutable list. The fix is a **defensive copy** in the compact constructor: `public Order { items = List.copyOf(items); }`. The same applies to arrays (copy them) and to any other mutable component.

**Q: What is the difference between a `switch` statement and a `switch` expression?**
**A:** A switch *expression* **produces a value**, so it can be used in a return or an assignment; it has no fall-through (arrow form); and it must be **exhaustive** — the compiler checks that every possible input is covered. A switch *statement* is a statement; with the arrow form it can still be exhaustive-checked when it is over an enum or a sealed type, but it does not produce a value.

**Q: What is the dominance rule in pattern matching for `switch`?**
**A:** An earlier case pattern that matches everything a later one matches makes the later one **unreachable**, and the compiler rejects it. So `case Object o` must come **after** `case Integer i`, `case String s`, etc. The rule exists to make the ordering of the cases meaningful and to prevent dead code.

**Q: What is the difference between `sealed` and `final`?**
**A:** `final` means "nobody may extend this" — the hierarchy is closed at one level. `sealed` means "these specific types may extend this, and each of them decides whether to close or reopen its own branch" (`final`, `sealed`, or `non-sealed`). So `sealed` gives you a **closed but arbitrarily deep** hierarchy, and gives the compiler the information to check switch exhaustiveness.

**Q: What are virtual threads, and when should I use them?**
**A:** Virtual threads are JVM-managed, lightweight threads (~1 KB of heap, versus ~1 MB of stack for a platform thread). They are for **blocking I/O** — when a task waits on a socket, the JVM unmounts it and lets the carrier thread run another task, so thousands of concurrent requests need only a handful of platform threads. They are **not** for CPU-bound work (they do not add CPU parallelism) and they are **not** pooled — you create one per task.

### 🔴 Advanced / Senior

**Q: What is the virtual thread "pinning" problem, and what are the mitigations?**
**A:** When a virtual thread blocks while holding a monitor (`synchronized`) or inside a native method, it **cannot be unmounted** — so its *carrier* (the platform thread) stays blocked too, and the whole point of virtual threads is lost. The mitigations: (1) use `ReentrantLock` instead of `synchronized` around blocking I/O — a `ReentrantLock` releases the carrier; (2) in Java 24 (JEP 491) the JDK re-implemented the monitor so `synchronized` no longer pins, so the issue is largely resolved going forward; (3) keep the `synchronized` block narrow and do the blocking *outside* it. The related trap: with a million cheap virtual threads, a single contended `synchronized` block becomes the new bottleneck — the contention moves from the thread count to the lock.

**Q: Why does `sealed` matter more than "restriction"? What does it buy the compiler?**
**A:** Restriction alone is a design constraint; the *compiler benefit* is what makes it transformative. Because the compiler knows the complete set of permitted subtypes, it can (1) verify that a `switch` is **exhaustive**, turning "you forgot a case" from a silent production bug into a compile error; (2) narrow the type in each case, so `case Circle c -> c.radius()` works without a cast; (3) support record patterns, deconstructing a case in one step. This is the same mechanism as an enum switch, but for *types* rather than *labels* — and it extends to arbitrarily deep hierarchies via `permits` and `non-sealed`.

**Q: When would you choose a `sealed` hierarchy over an enum, in a real codebase?**
**A:** Choose a sealed hierarchy when the cases need to carry **different state**. An enum's constants are all instances of one class, so they share the same fields — which is fine for a status but wrong for a payment method (a card has a last-4, a wallet has a provider, COD has nothing). With `sealed interface PaymentMethod permits Card, Wallet, Cod` and a `record` per case, each case carries its own data *and* gets the compiler-checked exhaustiveness of an enum switch. The cost: you lose `values()` and `ordinal()`, and you must write your own lookup — which you would want anyway, keyed by a stable `code` rather than an `ordinal`.

**Q: What are the real risks of migrating to virtual threads?**
**A:** Four. (1) **CPU-bound work** does not benefit and can regress (scheduling overhead, worse cache locality). (2) **`synchronized` contention** becomes the new bottleneck, because you can now create a million threads that all want the same lock. (3) **`ThreadLocal` memory** — a virtual thread lives as long as its task, and a `ThreadLocal` value on it is not reclaimed until the task ends, so a `ThreadLocal` holding a large object in a per-request task is a memory risk that did not exist with pooled threads (where the value was reused). (4) **Unbounded concurrency** — with 1,000 virtual threads you may open 1,000 downstream connections; the fix is a **semaphore** or a rate limiter to bound the *resource*, not the thread count. The migration advice: start with a leaf service that is purely I/O-bound, and always bound the downstream concurrency.

**Q: What does `Stream.toList()` give you that `collect(Collectors.toList())` does not?**
**A:** Three differences: (1) `toList()` returns an **unmodifiable** list, while `collect(toList())` returns a mutable `ArrayList`; (2) `toList()` **allows nulls**, while `Collectors.toList()` throws a `NullPointerException` on a null element; (3) `toList()` does not guarantee the concrete type (though it currently returns a `ListN`). The practical guidance: use `toList()` when the result is a value you want to hand out immutably; use `collect(toList())` when you need to mutate it afterwards.

### ⚫ Expert / Principal

**Q: Java 8→21 shows a clear design direction. What is it, and where does it break down?**
**A:** The direction is **"move constraints from runtime and convention to compile time."** `sealed` + pattern matching moves "did you handle every case?" from code review to the compiler. `records` move "did you write `equals` correctly?" to "it is generated". `switch` expressions move "did I forget a `break`?" and "did I forget to assign the result?" to the compiler. `var` moves type-inference verbosity out of the source (a small step in the same direction). `Optional` and the `Optional`-in-Java-8 debate is the same tension. **Where it breaks down:** (1) the JMM — Java's memory model is still the 2004 model, and the loom project (which delivered `StructuredTaskScope` in Java 21) is the first serious attempt to fix the data-race *detection* story; (2) nullability — Java still has no non-null type, so `Optional` is a partial and controversial answer; (3) the error model — `Optional` and `CompletableFuture` are two different answers to "maybe later", and the language has no unified "maybe" type; (4) performance — the language's abstractions still compile to a lot of allocation (boxing, `Optional` wrappers, stream overhead). The honest summary: Java has been remarkably conservative about *semantics* (the JMM, the memory model, the type system) and progressive about *ergonomics* (syntax and compiler-checked patterns). The ergonomics have been very successful; the semantics work is only now beginning.

**Q: Design the concurrency model for a service that must handle 50,000 concurrent long-polling requests. What would you choose, and why?**
**A:** **Virtual threads (Java 21+) with a bounded semaphore for the downstream.** The reasoning: (1) 50,000 long-polling requests are almost all *waiting*, not computing — the classic case where platform threads waste 1 MB each and the OS scheduler is the bottleneck. Virtual threads make each request cost ~1 KB and let the JVM multiplex 50,000 of them onto ~#cores carriers. (2) The *downstream* is the real constraint — if each long-poll holds a connection to a message broker, 50,000 connections will exhaust the broker or the file descriptors. So I bound the *resource* with a `Semaphore` (or a `RateLimiter`), not the thread count. (3) I would **not** use `synchronized` around the blocking read — `ReentrantLock`, or Java 24+'s non-pinning monitor. (4) I would keep the `ThreadLocal` surface tiny (a trace id at most) and clear it. (5) I would use `StructuredTaskScope` (Java 21, preview) to tie the lifetime of a request's sub-tasks to the request, so a failure cancels the siblings — with the caveat that it is still a preview API. The alternative, if the deployment is Java 17, is the reactive stack (Reactor) — but that trades the simple blocking code for a steep learning curve and harder debugging, and for long-polling specifically the reactive advantage is smaller than for high-throughput request/response.

---

## 9. Counter-Questions & Traps

### CQ1. "If records generate `equals`, `hashCode` and `toString`, why not just use a Lombok `@Value` class?"

- **Naive:** "Records are built into the language, so they're better."  ← *true, and it dodges the interesting part*
- **The trap:** The trap is to stop at "built in vs a library". The real question is **what a record gives you that Lombok cannot** — and the answer is *type-system-level* guarantees, not just less code.
- **Expert answer:** Three things. (1) **The compiler enforces the contract.** A record is `final`, its fields are `final`, and the generated `equals`/`hashCode` are component-based — you *cannot* break them, because you cannot override them (they are `final` in `java.lang.Record`). With Lombok, a `@Data` class on a class hierarchy can generate an inconsistent `equals`, and the compiler will not stop you. (2) **It is a language-level type**, so tools, frameworks, and the JDK itself can recognise it (`instanceof Record`, `getRecordComponents()`), and pattern matching can destructure it. (3) **It changes the *type* of the thing** — a record participates in exhaustiveness checking with `sealed` hierarchies, which a Lombok class does not. The honest comparison: Lombok still wins for classes needing **inheritance**, `@Builder`, or mutable fields — and it is still an annotation processor, so it needs the IDE plugin configured. The rule: **records for value objects, Lombok for everything else.**

### CQ2. "You said a record is shallowly immutable. Isn't that a flaw in the record design?"

- **Naive:** "Yes, but it is a known limitation."  ← *accurate, and it misses the design intent*
- **The trap:** The trap is to treat it as a bug. The record's guarantee is deliberately **precise**: *"the record's references cannot change"*. It does not claim *"the object graph is immutable"*, because that would require the record to know about every component's mutability — which it cannot, since a component's type may be an interface.
- **Expert answer:** The design reasoning: a record can only guarantee what it can *see*. It sees the component *types*, not their deep structure. A `record Order(String id, List<Item> items)` cannot know whether `List` is `ArrayList` or an unmodifiable one. So the record guarantees *reference* immutability, and it leaves *deep* immutability to the author. The correct pattern — and it is the JDK's own pattern in `java.time` — is to **normalise in the compact constructor**: copy the mutable component into an immutable form (`List.copyOf`, `Set.copyOf`, `Map.copyOf`, `clone()` for arrays). The deeper lesson: **immutability is a spectrum, and the language gives you the first level for free; the rest is your job.** The trap for a candidate is to claim a record is "deeply immutable" — that is simply false, and the `order.items().add(x)` example proves it.

### CQ3. "Why is `sealed` better than just using an enum? When would you use each?"

- **Naive:** "Enums are simpler, sealed is more flexible."  ← *a comparison, not a decision rule*
- **The trap:** The trap is to treat them as competitors. They solve **different** problems: an enum models a closed set of *labels* that share one shape; a sealed hierarchy models a closed set of *types* that each have their own shape. The question "which is better" has no answer; the question "do my cases need different data?" does.
- **Expert answer:** The decision rule: **do the cases need different *state*?** If no (a status, a category, a priority), an **enum** — it gives you `values()`, `valueOf`, `EnumSet`, `EnumMap`, and a free `switch` exhaustiveness check, with no extra code. If yes (a card has a last-4, a wallet has a provider), a **sealed hierarchy** — each case a `record` with its own components, and the same exhaustiveness guarantee. The hybrid that is often the best answer: a sealed interface where the *code* is a `String` constant per case, giving you the enum's stable-code discipline *and* the sealed hierarchy's per-case state. And the third option, which is often right for a truly open set: a plain interface or a `Map` keyed by a string — with the cost that you lose the compiler's exhaustiveness check, which is exactly the trade you are making.

### CQ4. "You said `synchronized` pins a virtual thread. But Java 24 fixed that. So the advice is obsolete?"

- **Naive:** "Yes, the advice is obsolete."  ← *partly right, and it loses the general principle*
- **The trap:** The trap is to treat the pinning problem as *only* a `synchronized` problem. The general principle is: **a virtual thread that cannot be unmounted pins its carrier**, and there are more reasons than `synchronized` — a native method call, and (historically) `Object.wait`.
- **Expert answer:** The precise history: in Java 21–23, a virtual thread that blocked inside a `synchronized` block or a native method **could not be unmounted**, so its carrier stayed pinned — collapsing scalability. JEP 491 (Java 24) re-implemented the JDK's monitor so that `synchronized` no longer pins, resolving the biggest case. The *general* lesson that survives: **anything that prevents the unmount pins the carrier**, and the way to reason about it is to ask "can this virtual thread be migrated to another carrier while it waits?" — if the answer is no, it pins. So the durable advice is not "never use `synchronized`" but "know which constructs prevent unmounting, and prefer `ReentrantLock` (which has never pinned) for code that blocks inside a lock". The deeper lesson for a platform team: **the pinning class is an implementation detail that can change between JDK versions** — so the *design* should not depend on it, and a regression test that measures carrier utilisation under load is worth having.

### CQ5. "Virtual threads make concurrency easy. Why would anyone still use a thread pool?"

- **Naive:** "Because virtual threads are new and untested."  ← *true for early adopters, and irrelevant for the design*
- **The trap:** The trap is to think virtual threads *replace* pools. They replace pools **for blocking I/O**; they do not replace pools for **CPU-bound** work, and they do not remove the need for **bounding**.
- **Expert answer:** Three reasons the pool survives. (1) **CPU-bound work** — a virtual thread does not add CPU parallelism; 1,000 virtual threads on 8 cores gives 8-way parallelism and 992 queued tasks, plus scheduling overhead. A `ForkJoinPool` sized to the core count is the right tool, and it gives you work-stealing. (2) **Resource bounding** — with a pool, the pool size *is* the concurrency limit on your downstream. With virtual threads you can create 1,000,000 requests and open 1,000,000 connections; so you need a `Semaphore` or a rate limiter to bound the *resource*. The pool was doing that job implicitly; now you must do it explicitly. (3) **Operations** — the pool gives you a queue, a rejection policy, a graceful shutdown protocol, and metrics. The modern answer is to combine: virtual threads for the request handling, a semaphore for the downstream, and a `ForkJoinPool` for the CPU-bound parts. The pool is not obsolete — its *role* changed from "bound the concurrency" to "shape the CPU parallelism and provide operational control".

### CQ6. "If `switch` expressions are exhaustiveness-checked, why do I still need `sealed`? Enums are already exhaustive."

- **Naive:** "You don't — enums are enough."  ← *true for labels, and it misses the point*
- **The trap:** The trap is to think exhaustiveness is the only benefit. `sealed` gives the compiler the set of **types**, which enables (a) **type narrowing** — `case Circle c -> c.radius()` with no cast, and (b) **record patterns** — `case Point(int x, int y)`, which an enum can never support.
- **Expert answer:** The two mechanisms are complementary, and the compiler needs different information for each. For an **enum**, the exhaustiveness check needs the set of *constants* — which the compiler has from the class file. For a **sealed hierarchy**, it needs the set of *permitted subtypes* — which it only has because of the `permits` clause (and the same-file convention). What `sealed` unlocks beyond exhaustiveness: **type narrowing** (no casts), **record patterns** (deconstructing a case in one step), and **polymorphic dispatch without a visitor** — so the "add an operation" use case that motivated the Visitor pattern is now handled by the language. The honest summary: for a set of *labels*, an enum is simpler and you should use it; for a set of *types with data*, `sealed` is the only thing that gives you both the data and the compile-time guarantee.

### CQ7. "Is `var` just a modern style thing, or does it actually matter?"

- **Naive:** "It's a style preference."  ← *it is, and it misses the real point*
- **The trap:** The trap is to treat `var` as cosmetic. The real issue is that `var` changes **what the compiler infers**, and there is a specific case where it silently changes the *semantics* of your code.
- **Expert answer:** The specific hazard: `var list = new ArrayList<>();` infers `ArrayList<Object>`, not `ArrayList<String>` — so `list.add("a")` and `list.add(1)` both compile, and the list is now heterogeneous. The same applies to `var` with a raw type or a wildcard target. The rule that makes `var` safe: **use it when the right-hand side determines the type unambiguously** (`new ArrayList<String>()`, a method whose return type you know), and **avoid it when the right-hand side is less specific than the variable's intent** — which is exactly the diamond case. The stylistic benefit is real too: `var` removes the noise of a repeated type and forces the *name* to carry the meaning, which is why the Java team's own style guide says to use it when it improves readability. The deeper point: `var` is a *compile-time* inference with no runtime effect whatsoever — the bytecode is identical to the explicit version. So the only thing it can break is **inference**, not performance.

### CQ8. "Records replaced DTOs. But what about JPA entities — do they work with records?"

- **Naive:** "Yes, records work fine with JPA."  ← *true for some ORMs, and dangerous in general*
- **The trap:** The trap is to ignore that JPA's model is **identity-based** — an entity's identity is its *object identity* across the persistence context, not its field values. A record has value-based `equals`/`hashCode`, which conflicts with JPA's requirement for a stable identity.
- **Expert answer:** The technical detail: JPA requires an entity to have an identity that does **not** change — usually a surrogate `@Id`. If you make the entity a `record`, its `equals`/`hashCode` are generated from *all* the fields, so changing a field changes the identity — which breaks the persistence context's tracking (it would look like a *different* entity). The practical rules: use a **record as the DTO** (the API boundary) and a **class as the JPA entity** (the persistence model), and map between them — that separation is good design anyway. Some ORMs (EclipseLink, and Hibernate 6 with `@Immutable`) support records as read-only projections. The general lesson: **value semantics and identity semantics are different models**, and a framework built on one cannot simply accept the other.

### CQ9. "You said text blocks strip the common indentation. What if I want to preserve it exactly?"

- **Naive:** "Use `\s` escapes."  ← *the escape is for a single space, not indentation*
- **The trap:** The trap is that the "incidental white space" rule is *defined* in terms of the **minimum** indentation of the non-blank lines plus the closing delimiter's indentation — so it is not a single number you can look up, and `String.formatted`/`indent` do not help.
- **Expert answer:** The precise rule (JLS §3.10.6): the **incidental white space** is the longest common prefix of the whitespace on all non-blank lines **and** the whitespace before the closing delimiter. That prefix is removed from every line. So to control it exactly: (1) put the closing delimiter **at column 0** — then the common prefix is just the content's own indentation, and what you see is what you get; (2) if you need leading whitespace *preserved*, use the `\s` escape (which is a single space) or build the string with `String.join`; and (3) for a *programmatically* indented block, use `String.indent(n)` (Java 12+) which adds a *fixed* prefix to every line. The deeper point: the rule exists so that a text block can be **re-indented by the IDE** without changing its meaning — that is the whole design goal, and it is why the closing delimiter's position matters.

### CQ10. "What is the single biggest thing Java 21 changed for a backend engineer, and why?"

- **Naive:** "Virtual threads."  ← *the right answer, but it needs the "why"*
- **The trap:** The trap is to answer with the feature and stop. The feature is only interesting because of what it lets you *stop doing*: you can go back to **simple blocking code** and still get high concurrency.
- **Expert answer:** The biggest change is that **the concurrency model of the mainstream Java backend is no longer forced to be reactive.** Before Java 21, the only way to get high concurrency with platform threads was (a) an async/reactive stack — Reactor, RxJava, `CompletableFuture` — which trades blocking simplicity for a steep learning curve, hard debugging, and no back-pressure; or (b) a platform-thread pool, which caps concurrency at the pool size. Virtual threads let you write **plain blocking code** (`socket.read()`, `jdbcTemplate.query()`) and get **thousands of concurrent requests**, because the JVM schedules the waiting for you. The second-order effects are arguably bigger: the code becomes readable again, the debugging is ordinary again, and the operational model (one request = one virtual thread) matches the mental model. The caveat that must accompany the answer: virtual threads are for **blocking I/O**, not CPU work; they need a **semaphore** to bound the downstream; and `synchronized` pinning (until Java 24) was a real constraint. So the *complete* answer is: virtual threads + a bounded semaphore + `ReentrantLock` over `synchronized` + a `ForkJoinPool` for the CPU parts — and the payoff is that the *application* code can be boring again.

---

## 10. Rapid-Fire MCQs

**Q1.** Records were finalised in which Java version?
- A) 14  B) 16  C) 17  D) 21

**Q2.** Sealed classes were finalised in which Java version?
- A) 15  B) 16  C) 17  D) 21

**Q3.** Pattern matching for `switch` was finalised in which Java version?
- A) 17  B) 19  C) 20  D) 21

**Q4.** Virtual threads were finalised in which Java version?
- A) 19  B) 20  C) 21  D) 25

**Q5.** A record is implicitly…
- A) Abstract  B) Final  C) Serializable  D) An enum

**Q6.** A record's accessor method is named…
- A) `getX()`  B) `x()`  C) `readX()`  D) `x` (a field)

**Q7.** A record's `equals`/`hashCode` are based on…
- A) The object identity  B) The components  C) The class name  D) The ordinal

**Q8.** Can a record be extended?
- A) Yes  B) No — records are final  C) Only if `non-sealed`  D) Only abstract records

**Q9.** Can a record implement an interface?
- A) Yes  B) No  C) Only `Serializable`  D) Only with a `permits` clause

**Q10.** A record is deeply immutable?
- A) Yes  B) No — only its references are immutable  C) Only if the components are  D) Only with `List.copyOf`

**Q11.** Where do you put validation in a record?
- A) A static factory  B) The compact constructor  C) A `validate()` method  D) The caller

**Q12.** `var` can be used for…
- A) Fields  B) Method parameters  C) Local variables  D) Return types

**Q13.** `var list = new ArrayList<>();` infers…
- A) `List<Object>`  B) `ArrayList<Object>`  C) `ArrayList<String>`  D) `Object`

**Q14.** `var` is…
- A) A keyword  B) A reserved type name  C) A type inference annotation  D) A `final` modifier

**Q15.** A switch **expression** must be…
- A) Nullable  B) Exhaustive  C) Inside a loop  D) A statement

**Q16.** `yield` is used in a switch expression when the arm body is…
- A) An arrow expression  B) A block  C) A `default`  D) A pattern

**Q17.** In a pattern-matching `switch`, `case null ->` is…
- A) Illegal  B) Allowed and explicit  C) Only in preview  D) Deprecated

**Q18.** The dominance rule requires the most specific pattern…
- A) Last  B) First  C) Alphabetically  D) Randomly

**Q19.** A text block strips…
- A) All whitespace  B) The common leading whitespace  C) Trailing newlines  D) Nothing

**Q20.** A text block's content ends with…
- A) No newline  B) A newline  C) A `null`  D) Whatever the last line has

**Q21.** A virtual thread's stack is…
- A) 1 MB on the OS stack  B) A heap object (~1 KB)  C) A metaspace allocation  D) Shared between tasks

**Q22.** Virtual threads are best suited for…
- A) CPU-bound work  B) Blocking I/O  C) Cryptography  D) Tight numeric loops

**Q23.** What does a virtual thread blocking inside `synchronized` do (Java 21–23)?
- A) Unmounts the carrier  B) Pins the carrier  C) Crashes the JVM  D) Blocks all carriers

**Q24.** The right executor for virtual threads is…
- A) `newFixedThreadPool(200)`  B) `newVirtualThreadPerTaskExecutor()`  C) `newCachedThreadPool()`  D) The common pool

**Q25.** `Stream.toList()` (Java 16) differs from `collect(Collectors.toList())` because it…
- A) Allows nulls and is unmodifiable  B) Sorts the result  C) Is parallel  D) Returns a mutable list

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | Records were finalised in Java 16 (preview in 14 and 15). |
| 2 | **C** | Sealed classes were finalised in Java 17. |
| 3 | **D** | Pattern matching for `switch` was finalised in Java 21. |
| 4 | **C** | Virtual threads were finalised in Java 21 (preview in 19 and 20). |
| 5 | **B** | A record is implicitly `final` — it extends `java.lang.Record` and cannot be extended. |
| 6 | **B** | The accessor is `x()` — a record is a value, not a JavaBean. |
| 7 | **B** | The generated `equals`/`hashCode` use the components. |
| 8 | **B** | Records are final — use composition or a sealed interface instead. |
| 9 | **A** | A record can implement any number of interfaces. |
| 10 | **B** | Only the record's own references are immutable — a mutable component stays mutable. |
| 11 | **B** | The compact constructor — it runs before the fields are assigned. |
| 12 | **C** | Local variables only. |
| 13 | **B** | The diamond infers `ArrayList<Object>` — the classic `var` trap. |
| 14 | **B** | A reserved *type name*, not a keyword. |
| 15 | **B** | The compiler checks that every possible input is covered. |
| 16 | **B** | A block-bodied arm must `yield` its value. |
| 17 | **B** | `case null ->` makes the null case explicit. |
| 18 | **B** | The most specific pattern must come first, or the later one is unreachable. |
| 19 | **B** | The common leading whitespace (the minimum indentation) is stripped; relative indentation is kept. |
| 20 | **B** | A text block always ends with a newline, unless the closing delimiter is on the content line. |
| 21 | **B** | The stack lives on the heap as a normal object — ~1 KB instead of ~1 MB. |
| 22 | **B** | Blocking I/O — the carrier is released while the task waits. |
| 23 | **B** | It pins the carrier, collapsing scalability. (Fixed in Java 24 by JEP 491.) |
| 24 | **B** | `newVirtualThreadPerTaskExecutor()` — one virtual thread per task, no pooling. |
| 25 | **A** | `toList()` allows nulls and returns an unmodifiable list. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                      THE MODERN JAVA TOOLBOX                            │
├──────────────┬───────────────────────────────────────────────────────────┤
│ RECORD (16)  │ final class · final fields · canonical ctor · accessors   │
│              │ generated equals/hashCode/toString · NO inheritance      │
│              │ ⚠ SHALLOWLY immutable → copy mutable components          │
│              │ ✓ compact constructor for validation + normalisation     │
├──────────────┼───────────────────────────────────────────────────────────┤
│ SEALED (17)  │ permits A, B, C → the compiler KNOWS the subtypes         │
│              │ ✓ switch exhaustiveness is CHECKED                        │
│              │ ✓ type narrowing (no casts) · ✓ record patterns (21)      │
│              │ ✓ each case can be its own record (per-case STATE)       │
├──────────────┼───────────────────────────────────────────────────────────┤
│ SWITCH EXPR  │ produces a value · no fall-through · MUST be exhaustive    │
│ (14)         │ case A, B -> ...   ·   yield for a block body            │
├──────────────┼───────────────────────────────────────────────────────────┤
│ PATTERN SW   │ case Circle c -> ...  ·  case null -> ...  ·  when guards│
│ (21)         │ ⚠ DOMINANCE: the most specific pattern comes FIRST       │
├──────────────┼───────────────────────────────────────────────────────────┤
│ VAR (10/11)  │ local variables only · needs an initialiser              │
│              │ ⚠ var x = new ArrayList<>() → ArrayList<Object>!         │
├──────────────┼───────────────────────────────────────────────────────────┤
│ TEXT BLOCK   │ """…""" · strips the common indent · ends with \n        │
│ (15)         │ ✓ .formatted(...) for interpolation                     │
├──────────────┼───────────────────────────────────────────────────────────┤
│ VIRTUAL THREAD│ cheap (~1 KB) · JVM-scheduled · for BLOCKING I/O         │
│ (21)         │ ⚠ PINNING: synchronized blocks the carrier (until JEP 491)│
│              │ ⚠ bound the DOWNSTREAM with a semaphore, not the threads │
└──────────────┴───────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                  ENUM vs SEALED — THE DECISION                          │
│                                                                          │
│   Do the cases need different STATE?                                     │
│          │                                                               │
│      NO  │  YES                                                          │
│          ▼                                                               │
│   ┌────────────┐          ┌──────────────────────┐                      │
│   │   ENUM     │          │  SEALED INTERFACE    │                      │
│   │            │          │  + a record per case │                      │
│   │ values()   │          │                      │                      │
│   │ valueOf()  │          │  per-case state      │                      │
│   │ EnumSet    │          │  switch exhaustive   │                      │
│   │ EnumMap    │          │  type narrowing      │                      │
│   │ one shape  │          │  record patterns     │                      │
│   └────────────┘          └──────────────────────┘                      │
│                                                                          │
│   BOTH give you a compiler-checked switch. The difference is the DATA.    │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The migration checklist (Java 8 → 17/21)

```text
  ☐ Replace DTO boilerplate with records (+ a compact constructor for validation)
  ☐ Copy any mutable component in the compact constructor
  ☐ Replace `getX()` calls on record accessors with `x()`
  ☐ Replace `switch` statements with switch EXPRESSIONS (exhaustiveness for free)
  ☐ Use `sealed` for closed domain hierarchies that need per-case state
  ☐ Use pattern matching for `instanceof` to remove casts
  ☐ Add `case null ->` where null is a real input
  ☐ Use text blocks for SQL/JSON/HTTP payloads
  ☐ Use `List.of`/`Map.of`/`List.copyOf` for immutable collections
  ☐ Use `Stream.toList()` where an unmodifiable result is wanted
  ☐ Use `java.time`, not `Date`/`SimpleDateFormat`
  ☐ Adopt virtual threads for I/O-bound services — with a semaphore
  ☐ Replace `synchronized` around blocking I/O with `ReentrantLock`
```

### Virtual-thread adoption guide

| Step | Action |
|---|---|
| 1 | **Measure** — find the I/O-bound services. Look for thread-pool saturation. |
| 2 | **Bound the downstream first** — a `Semaphore` around the remote call, sized to the downstream's capacity. |
| 3 | **Swap the executor** — `Executors.newVirtualThreadPerTaskExecutor()`. Keep the code blocking. |
| 4 | **Replace `synchronized` around I/O** with `ReentrantLock`. |
| 5 | **Audit `ThreadLocal`s** — they are per-task now, not per-pool-thread. Keep them minimal. |
| 6 | **Load-test** — watch the carrier count and the p99 latency, not just throughput. |
| 7 | **Keep the platform pool for CPU work** — a `ForkJoinPool` sized to the cores. |

### Records in production

| Rule | Why |
|---|---|
| Use records for **API DTOs and value objects** | The generated `equals`/`hashCode` are correct and consistent |
| Add a **compact constructor** for validation | Fails fast at construction, not at use |
| **Copy mutable components** | Otherwise the immutability guarantee is void |
| Do **not** use a record as a JPA entity | Value semantics conflict with identity semantics |
| Do **not** use a record with an inheritance hierarchy | Records are final — use a sealed interface |
| Do **not** rely on the generated `toString` in production logs | It prints every component — a PII risk. Override it. |

### Switch and pattern-matching rules

| Rule | Why |
|---|---|
| Prefer a **switch expression** over a statement | Exhaustive by construction, no `break`, no "forgot to assign" |
| Put the **most specific pattern first** | Otherwise a dominance compile error |
| Add `case null ->` when null is possible | Otherwise an NPE at runtime |
| Use `when` guards for **sub-conditions** | Keeps the cases flat and readable |
| Use a **sealed hierarchy** for a closed set of types | The compiler checks every `switch` |
| Prefer `sealed` over an `enum` when cases carry state | Each case can be its own record |

### Modern Java APIs worth knowing

```java
// Java 11
s.isBlank(); s.strip(); s.repeat(3); s.lines();
List.copyOf(x); Map.of(k, v); Map.ofEntries(...);
optional.isEmpty();
coll.toArray(String[]::new);
HttpClient.newHttpClient();

// Java 15/16
textBlock.formatted(args);
stream.toList();
obj instanceof String s -> ...;

// Java 21
switch (obj) { case Circle c -> ...; case null -> ...; }
Thread.ofVirtual().name("x").start(...);
Executors.newVirtualThreadPerTaskExecutor();
// switch (point) { case Point(int x, int y) -> ...; }
```

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| Lambdas & functional interfaces (the foundation) | [06 · Java 8 Features](../06-Java-8-Features/01-Lambda-Functional-Interfaces.md) |
| `Optional` (the "maybe" question) | [06 · Optional & CompletableFuture](../06-Java-8-Features/03-Optional-CompletableFuture.md) |
| Virtual threads and the executor framework | [08 · Executor Framework](../08-Multithreading-Concurrency/02-Executor-Framework.md) |
| Pattern matching & sealed types in the OOP context | [01 · Core Java OOP](../01-Core-Java-OOP/README.md) |
| The JEPs | JEP 254 (Compact Strings), JEP 280 (String Concat), JEP 359 (Records), JEP 360 (Sealed), JEP 441 (Pattern Matching), JEP 444 (Virtual Threads), JEP 491 (Synchronized Virtual Threads) |

---

**Next Chapter:** → [08 · Multithreading & Concurrency](../08-Multithreading-Concurrency/README.md)
**Previous Chapter:** ← [06 · Java 8 Features](../06-Java-8-Features/03-Optional-CompletableFuture.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
