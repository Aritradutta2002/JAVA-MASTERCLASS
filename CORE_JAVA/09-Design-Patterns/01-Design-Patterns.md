# Design Patterns — The Complete Interview Guide

> **Patterns are not a checklist to memorise — they are a catalogue of *forces*.**
> The interview is not "name 23 patterns". It is: *"here is a problem; which force
> is acting here, and what would you do instead?"*

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

- **The Gang of Four (GoF)** published 23 patterns in 1994. They fall into three families: **Creational** (5), **Structural** (7), **Behavioural** (11).
- **A pattern is a named solution to a recurring *design force*.** The forces are: *not knowing what to create* (creational), *composing objects flexibly* (structural), *notifying/deciding between objects* (behavioural).
- **Creational (5):** Factory Method, Abstract Factory, Builder, Prototype, Singleton.
- **Structural (7):** Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy.
- **Behavioural (11):** Chain of Responsibility, Command, Interpreter, Iterator, Mediator, Memento, Observer, State, Strategy, Template Method, Visitor.
- **The 5 that actually get asked:** Singleton, Factory/Abstract Factory, Builder, Strategy, Observer. Then Proxy (Spring AOP), Decorator (`BufferedReader`), Adapter, Flyweight (String pool), Template Method (Spring's `AbstractApplicationContext`).
- **The best Singleton is an `enum`** — it is thread-safe by class-initialisation, resists reflection and deserialisation attacks, and is the JDK's own recommendation.
- **Double-checked locking needs `volatile`** — without it, the Java 5 memory model allows a partially-constructed object to be observed (the "broken" DCL bug).
- **Strategy vs Command:** Strategy = *one interchangeable algorithm*; Command = *a request packaged as an object* (so it can be queued, logged, undone).
- **Decorator vs Proxy:** a Decorator has the **same type** as what it decorates; a Proxy implements a **different interface** and *substitutes* for the real thing.
- **Observer vs Pub/Sub:** Observer is **in-process** (the subject holds the list); Pub/Sub has a **broker** (no direct reference between publisher and subscriber).
- **"When NOT to use it" is the real interview answer.** Every pattern has a cost; the trap is applying a pattern where the force does not exist.

---

## 1. What & Why — The Problem This Solves

**The problem.** In any non-trivial codebase, the same *structural* problems keep reappearing:

```text
   "I need to create a Product, but I don't know which concrete Product yet."
       → CREATIONAL force

   "I need to compose these two things that don't fit, without editing either."
       → STRUCTURAL force

   "When this Order changes, three other objects need to react — and I don't
    want them all to depend on each other."
       → BEHAVAL force
```

**The solution.** The GoF catalogue gives each recurring force a **name**, a **canonical shape**, and — crucially — a **documented cost**. Naming the force is what makes it discussable: "this smells like a Strategy" is a far more useful review comment than "this method is too long".

**The most important thing the GoF book actually says** (often mis-quoted):

> *"The Strategy pattern lets the algorithm vary independently from clients that use it."*
> …and, in the same spirit, the book's own warnings: *"…it is often the case that the
> patterns you use are mostly a matter of taste."* The patterns are a **shared
> vocabulary**, not a **requirement**.

**The cost side of the ledger.** Every pattern adds a class, an indirection, and a concept a reader must learn:

| Pattern | What it costs you |
|---|---|
| Factory | A layer of indirection and a naming convention |
| Builder | A mutable builder object + a construction ceremony |
| Singleton | Global state, harder testing, hidden dependencies |
| Decorator | Layered wrapper semantics; debugging becomes "which layer?" |
| Proxy | Latency, plus a second object to keep in sync |
| Observer | No ordering guarantee, and *lost events* if a subscriber is slow |
| Strategy | A class per algorithm, plus a switch at the call site to pick one |
| State | A class per state — the "state pattern explosion" |

> 💡 **The senior answer:** *the question is never "which pattern?" — it is "which
> force is present, and is a pattern cheaper than the problem it solves?"*

---

## 2. Mental Model — The One Picture

```text
   ╔══════════════════════════════════════════════════════════════════════╗
   ║                     THE 23 GANG-OF-FOUR PATTERNS                    ║
   ╠══════════════════════════════╦═══════════════════════════════════════╣
   ║        CREATIONAL (5)         ║         STRUCTURAL (7)              ║
   ║        "WHAT to create"       ║      "HOW to compose"               ║
   ╠══════════════════════════════╬═══════════════════════════════════════╣
   ║ Factory Method               ║ Adapter    — make incompatible fit  ║
   ║ Abstract Factory             ║ Bridge     — vary two dimensions   ║
   ║ Builder                      ║ Composite  — treat parts as a whole║
   ║ Prototype                    ║ Decorator  — add behaviour, same  ║
   ║ Singleton                    ║ Facade     — simplify a subsystem  ║
   ║                              ║ Flyweight  — share expensive state ║
   ║                              ║ Proxy      — substitute a subject  ║
   ╠══════════════════════════════╩═══════════════════════════════════════╣
   ║                       BEHAVIOURAL (11)                             ║
   ║                       "HOW objects interact"                       ║
   ╠══════════════════════════════════════════════════════════════════════╣
   ║ Chain of Responsibility   Command      Interpreter   Iterator      ║
   ║ Mediator                   Memento      Observer      State         ║
   ║ Strategy                   Template Method            Visitor        ║
   ╚══════════════════════════════════════════════════════════════════════╝
```

### The three decision flowcharts

```text
   CREATIONAL — What do I need to create?
   ─────────────────────────────────────
              Need to create?
                      │
        ┌─────────────┴──────────────┐
        │                            │
   One product,               A family of related
   chosen by a subclass?       products?
        │                            │
        ▼                            ▼
   Factory Method              Abstract Factory

        ┌─────────────┬──────────────┐
        │             │              │
   Many optional      Need copies?  Exactly one
   arguments?                        instance?
        │             │              │
        ▼             ▼              ▼
     Builder       Prototype      Singleton


   STRUCTURAL — How do I compose?
   ─────────────────────────────────────
   Shapes don't fit?  →  Adapter
   Two axes vary?     →  Bridge
   Tree of parts?     →  Composite
   Add behaviour, same type?  →  Decorator
   Simplify a subsystem?      →  Facade
   Many identical, expensive state?  →  Flyweight
   Substitute a subject?      →  Proxy


   BEHAVIOURAL — How do objects interact?
   ─────────────────────────────────────
   A chain of handlers?        →  Chain of Responsibility
   Package a request?          →  Command
   Interpret a grammar?        →  Interpreter
   Traverse a collection?      →  Iterator
   Decouple many interactors?  →  Mediator
   Snapshot & restore?         →  Memento
   Notify on change?           →  Observer
   Behaviour depends on state? →  State
   Swap an algorithm?          →  Strategy
   Template a workflow?        →  Template Method
   Add an operation to a type? →  Visitor
```

**What to look at:** the families are organised by *the kind of force*, not by implementation. Once you can name the force, the pattern follows.

---

## 3. Deep Dive — Theory

# ═══════════════════ CREATIONAL (5) ═══════════════════

## 3.1 Factory Method

**Intent.** Define an interface for creating an object, but let the subclasses decide which class to instantiate.

```text
   ┌──────────────────────────────┐
   │  Creator (abstract)          │      create() ──▶ Product
   │  + abstract create():Product │
   │  + createAndLog()  [FINAL]   │  ← the template, fixed for all
   └──────────────┬───────────────┘
                  │ extends
        ┌─────────┴──────────┐
        ▼                    ▼
   ┌────────────┐      ┌────────────┐
   │ CircleCreator│   │ SquareCreator│
   │ create()→Circle  │ create()→Square │
   └────────────┘      └────────────┘
```

```java
// The JDK's own use: java.util.Calendar.getInstance(), List.of() (via List.of),
// and most famously — every factory method on an immutable type.
public interface NotificationChannel {
    void send(String message);

    // The "template" that every channel must follow
    default void sendWithRetry(String message, int attempts) {
        for (int i = 0; i < attempts; i++) {
            try {
                send(message);
                return;
            } catch (RuntimeException e) {
                if (i == attempts - 1) throw e;
            }
        }
    }
}

// Each subclass decides what to create
public class EmailChannel implements NotificationChannel {
    private final String smtpServer;

    public EmailChannel(String smtpServer) { this.smtpServer = smtpServer; }

    @Override public void send(String message) {
        System.out.println("Sending via " + smtpServer + ": " + message);
    }
}

public class SmsChannel implements NotificationChannel {
    private final String gatewayUrl;

    public SmsChannel(String gatewayUrl) { this.gatewayUrl = gatewayUrl; }

    @Override public void send(String message) {
        System.out.println("Sending SMS via " + gatewayUrl + ": " + message);
    }
}
```

**When to use:** a class cannot decide its own dependencies; the choice must be made by a subclass or by configuration.
**When NOT to use:** a single `if`/`switch` in a factory method is usually enough — do not create a class hierarchy to avoid one `switch`.
**Production use:** Spring's `FactoryBean`; every "SPI" (ServiceLoader) mechanism.

## 3.2 Abstract Factory

**Intent.** Provide an interface for creating *families* of related objects without specifying their concrete classes.

```text
   ┌───────────────────────┐
   │  AbstractFactory      │      createButton() + createCheckbox()
   └───────────┬───────────┘      → both come from the SAME family
               │ implements
     ┌─────────┼─────────┐
     ▼         ▼         ▼
  WinFactory  MacFactory  LinuxFactory
     │           │           │
  WinButton  MacButton  LinuxButton
  WinCheckbox MacCheckbox LinuxCheckbox
```

**The key idea:** the factory guarantees that the objects it creates are **designed to work together**. A `WinFactory` never gives you a `MacButton` with a `WinCheckbox`.

```java
// A real, useful example: a UI kit that must match the OS theme
public interface UiFactory {
    Button createButton();
    CheckBox createCheckBox();
}

public interface Button { void render(); }
public interface CheckBox { void render(); }

// Concrete factory for the "dark" theme
public class DarkUiFactory implements UiFactory {
    @Override public Button createButton() { return new DarkButton(); }
    @Override public CheckBox createCheckBox() { return new DarkCheckBox(); }
}
```

**When to use:** you have multiple *product lines* and you must guarantee compatibility within a line.
**When NOT to use:** you only have one product dimension — use a simple factory method instead.
**Production use:** `java.sql.Driver` + `DataSource` families; database-driver ecosystems; theme kits.

## 3.3 Builder

**Intent.** Construct a complex object step by step, separating *construction* from *representation*.

```text
   ❌ WITHOUT Builder — the "telescoping constructor" antipattern

   new Order(orderId, customerId, null, null, null, null, null, null, null, null, null, null);
                    └────── 12 parameters, and which is which? ──────┘

   ✅ WITH Builder

   Order order = Order.builder()
                       .orderId("ORD-1")
                       .customerId("CUS-9")
                       .items(List.of(item1, item2))
                       .shippingAddress(addr)
                       .build();
```

```java
public final class Order {
    private final String orderId;
    private final String customerId;
    private final List<OrderItem> items;
    private final Address shippingAddress;
    private final PaymentMethod paymentMethod;

    private Order(Builder b) {
        this.orderId      = Objects.requireNonNull(b.orderId, "orderId is required");
        this.customerId   = Objects.requireNonNull(b.customerId, "customerId is required");
        this.items        = List.copyOf(b.items);
        this.shippingAddress = b.shippingAddress;
        this.paymentMethod   = b.paymentMethod;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String orderId;
        private String customerId;
        private List<OrderItem> items = new ArrayList<>();
        private Address shippingAddress;
        private PaymentMethod paymentMethod;

        public Builder orderId(String orderId) { this.orderId = orderId; return this; }
        public Builder customerId(String customerId) { this.customerId = customerId; return this; }
        public Builder item(OrderItem item) { this.items.add(item); return this; }
        public Builder items(List<OrderItem> items) { this.items = new ArrayList<>(items); return this; }
        public Builder shippingAddress(Address a) { this.shippingAddress = a; return this; }
        public Builder paymentMethod(PaymentMethod p) { this.paymentMethod = p; return this; }

        public Order build() {
            if (items.isEmpty()) {
                throw new IllegalStateException("An order must contain at least one item");
            }
            return new Order(this);
        }
    }
}
```

**Why it is more than "a constructor with setters":**
1. The target object is **immutable** — fields are `final`, set once in the private constructor.
2. **Required vs optional** is explicit — `Objects.requireNonNull` in the constructor, validation in `build()`.
3. The **type** of the builder method can be specialised (`item(OrderItem)` vs `items(List<OrderItem>)`).

**When to use:** 4+ optional parameters; the object is immutable; construction has validation or derived fields.
**When NOT to use:** a simple 3-arg constructor is clearer.
**Modern alternative:** for *data carriers* with no validation, a Java 16 `record` replaces the builder entirely.

```java
// A record IS a builder-less value object
public record OrderItem(String sku, String name, int quantity, BigDecimal unitPrice) {
    public OrderItem {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        if (unitPrice == null || unitPrice.signum() < 0) throw new IllegalArgumentException("bad price");
    }
    public BigDecimal lineTotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
}
```

## 3.4 Prototype

**Intent.** Create a new object by **cloning** an existing one, rather than by constructing it.

```java
// The two flavours
public class Report implements Cloneable {

    private String title;
    private List<Row> rows;
    private Map<String, String> metadata;   // deep-copy candidates

    public Report(Report other) {          // copy constructor — preferred
        this.title = other.title;
        this.rows = new ArrayList<>(other.rows);
        this.metadata = new HashMap<>(other.metadata);
    }

    @Override
    public Report clone() throws CloneNotSupportedException {
        return new Report(this);            // delegates to the copy constructor
    }
}
```

**Shallow vs deep copy — the thing to get right:**

```text
   SHALLOW COPY                 DEEP COPY
   ────────────                 ─────────
   new Report(other):           new Report(other):
     this.rows = other.rows;      this.rows = new ArrayList<>(other.rows);
                                ↑ the list itself is copied
   ⚠ both objects share the SAME  ✅ each has its own list
   List instance. Mutating one
   mutates the other!            For truly immutable elements (records,
                                Strings, BigDecimal) a shallow copy
                                of the list is sufficient.
```

**When to use:** the object is expensive to construct; you need many similar instances; the type is hard to know at compile time.
**When NOT to use:** a copy constructor or `record` is clearer.
**Production use:** `Object.clone()` in the JDK; `ArrayList`'s `clone()`; prototype-based builders in some ORMs.

> ⚠️ **`Object.clone()` is `protected` and returns `Object`**, and it performs a **shallow** copy. The `Cloneable` marker interface does nothing at runtime — it is purely a flag. Prefer a **copy constructor**: it is type-safe, does not throw, and can be made deep trivially.

## 3.5 Singleton

**Intent.** Ensure a class has exactly one instance and provides a global access point.

**The five implementations:**

```java
// ① EAGER — simple, but the instance is created even if never used
public class EagerConfig {
    private static final EagerConfig INSTANCE = new EagerConfig();
    private EagerConfig() {}
    public static EagerConfig getInstance() { return INSTANCE; }
}

// ② LAZY-HOLDER (Initialization-on-demand holder) — the BEST non-enum version
public class LazyConfig {
    private LazyConfig() {}
    private static class Holder {          // not loaded until first access
        private static final LazyConfig INSTANCE = new LazyConfig();
    }
    public static LazyConfig getInstance() { return Holder.INSTANCE; }
}
// ✅ Thread-safe WITHOUT synchronization (JLS 12.4.2 class-init guarantee)
// ✅ Lazy
// ❌ Cannot be used if the constructor needs to be configurable at runtime

// ③ DOUBLE-CHECKED LOCKING — needs volatile!
public class DclConfig {
    private static volatile DclConfig instance;   // ⚠ volatile is ESSENTIAL
    private final String value;

    private DclConfig(String value) { this.value = value; }

    public static DclConfig getInstance(String value) {
        if (instance == null) {                     // 1st check — no lock
            synchronized (DclConfig.class) {
                if (instance == null) {             // 2nd check — with the lock
                    instance = new DclConfig(value);
                }
            }
        }
        return instance;
    }
}

// ④ ENUM — the JDK's RECOMMENDED answer ⭐
public enum EnumConfig {
    INSTANCE;
    private final Map<String, String> settings = new ConcurrentHashMap<>();
    public Map<String, String> settings() { return settings; }
}

// ⑤ STATIC NESTED HOLDER with dependency injection
public class ConfigRegistry {
    private final Map<String, Object> beans = new ConcurrentHashMap<>();
    public static ConfigRegistry get() { return Holder.INSTANCE; }
    private static class Holder { static final ConfigRegistry INSTANCE = new ConfigRegistry(); }
}
```

**Why DCL needs `volatile` — the memory-model story:**

```text
   WITHOUT volatile:
   ─────────────────
   Thread A                          Thread B
   ────────                          ────────
   sees instance == null
   enters synchronized
   instance = new Config(...)  ──▶   sees instance != null (BEFORE the
       │                              constructor has finished writing!)
       │  ⚠ the object's fields may   returns a PARTIALLY CONSTRUCTED object
       │    still be the default
       ▼
   constructor finishes

   WHY: without a happens-before edge, the write to `instance` can be
   REORDERED before the writes to the object's fields. The JMM allows this
   because there is no synchronisation edge between the constructor's
   writes and the reader's reads.

   WITH volatile:
   ──────────────
   the write to `instance` is a RELEASE; the read is an ACQUIRE.
   Everything the constructor wrote happens-before the reader's reads.
   ✅ Fully correct since Java 5 (JSR-133 fixed the JMM).
```

**Why the `enum` is the best:**

| Attack | `enum` resists? | Why |
|---|---|---|
| Reflection | ✅ | The constructor is `private` AND the class is `final`; `Constructor.newInstance` refuses to instantiate an enum. |
| Deserialisation | ✅ | Serialisation is special-cased for enums — the canonical instance is returned. |
| `Class.forName` + `getDeclaredConstructor().newInstance()` | ✅ | Same as reflection. |
| `Unsafe.allocateInstance` | ✅ | The JVM refuses for enums. |
| Thread safety | ✅ | Guaranteed by the class-initialisation lock. |

```java
// The reflection attack against a normal singleton
Constructor<EagerConfig> c = EagerConfig.class.getDeclaredConstructor();
c.setAccessible(true);
EagerConfig hacked = c.newInstance();   // 💥 a SECOND instance!
```

**The counter-question that follows:** *"If a singleton is so unbreakable, why is it an anti-pattern?"*

```text
   ❌ THE REAL PROBLEMS WITH SINGLETONS
   ─────────────────────────────────────
   1. HIDDEN DEPENDENCY
      new OrderService()  →  secretly uses Config.INSTANCE
      → you cannot see the dependency in the constructor
      → testing requires global state manipulation

   2. GLOBAL STATE
      → a mutable singleton is shared mutable state
      → in tests, order matters; tests leak into each other

   3. NOT THREAD-SAFE BY DEFAULT
      → "it's a singleton" ≠ "it's thread-safe" — a mutable singleton
        needs its own synchronisation

   4. BREAKS IN MICROSERVICES
      → each JVM has its own instance → "the" configuration is now N
        copies that can drift
      → the correct answer is a config SERVER, not a singleton

   5. COUPLES EVERYTHING
      → every class that touches the singleton now depends on it
      → hard to test, hard to replace, hard to reason about
```

**The modern answer:** **inject the dependency.** A `Singleton` is a *global variable with a class name*; use constructor injection and let the container (Spring) decide the scope.

# ═══════════════════ STRUCTURAL (7) ═══════════════════

## 3.6 Adapter

**Intent.** Convert the interface of a class into another interface the client expects.

```text
   ❌ WITHOUT Adapter — the client is coupled to a legacy interface
   ───────────────────────────────────────────────────────────────
   Client ──needs──▶ SquarePeg          SquarePeg ┌─┐
                     (round hole)              └─┘

   ✅ WITH Adapter
   ───────────────
   Client ──needs──▶ SquarePeg
                     ┌──────────┐
                     │ SquarePeg│  ┌──────┐
                     │  Adapter │──▶ RoundPeg
                     └──────────┘  └──────┘

   Two flavours:
   • OBJECT adapter  — composition (the adapter HAS a wrapped object)  ← preferred
   • CLASS adapter   — inheritance (multiple inheritance → not possible in Java!)
```

```java
// Object adapter (composition) — the Java-idiomatic form
public interface PaymentProcessor {
    PaymentResult pay(String accountId, BigDecimal amount);
}

/** The LEGACY interface we cannot change. */
public interface LegacyBankGateway {
    void transfer(String fromAccount, String toAccount, double amount);
}

public final class LegacyGatewayAdapter implements PaymentProcessor {
    private final LegacyBankGateway legacy;

    public LegacyGatewayAdapter(LegacyBankGateway legacy) {
        this.legacy = Objects.requireNonNull(legacy);
    }

    @Override
    public PaymentResult pay(String accountId, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return PaymentResult.failure("Amount must be positive");
        }
        legacy.transfer("MERCHANT", accountId, amount.doubleValue());
        return PaymentResult.success("OK");
    }
}
```

**When to use:** integrating a third-party or legacy system; wrapping a library with a nicer API.
**When NOT to use:** you control both sides — just change the interface.
**Production use:** `java.io.InputStreamReader` (byte → char), `java.util.Arrays.asList` (array → `List`), Spring's `HandlerAdapter`.

## 3.7 Bridge

**Intent.** Decouple an abstraction from its implementation so that the two can vary independently.

```text
   WITHOUT Bridge — a 2×2 explosion of classes
   ───────────────────────────────────────────
              │  Vector      │  Raster
   ───────────┼─────────────┼─────────────
   Circle      │ CircleVec   │ CircleRas
   Rectangle   │ RectVec     │ RectRas        ← 4 classes, and adding a
   ───────────┴─────────────┴─────────────     shape means 2 more

   WITH Bridge — 2 + 2 = 4 classes, but INDEPENDENT axes
   ───────────────────────────────────────────
              ┌────────────────────┐
              │   <<abstraction>>  │
              │      Shape         │
              │  + draw(Imp imp)  │
              └─────────┬──────────┘
                        │ implements
          ┌─────────────┴─────────────┐
          ▼                           ▼
      Circle                       Rectangle      ← the SHAPE axis
          │                           │
          │  ┌──────────────────┐     │
          └─▶│  <<implementation>>│◀───┘
             │    Renderer      │                 ← the RENDERING axis
             └────────┬─────────┘
                 ┌────┴────┐
                 ▼         ▼
             Vector     Raster
```

```java
// The shape axis
public abstract class Shape {
    protected final Renderer renderer;

    protected Shape(Renderer renderer) { this.renderer = renderer; }

    public abstract void draw();
    public abstract double area();
}

// The rendering axis
public interface Renderer {
    void renderCircle(int radius);
    void renderRectangle(int width, int height);
}

public final class Circle extends Shape {
    private final int radius;

    public Circle(Renderer renderer, int radius) {
        super(renderer);
        this.radius = radius;
    }

    @Override public void draw() { renderer.renderCircle(radius); }
    @Override public double area() { return Math.PI * radius * radius; }
}
```

**When to use:** two independent axes of variation; you must support many combinations without a class explosion.
**When NOT to use:** only one axis varies.
**Production use:** JDBC's `DriverManager`/`Connection`/`Statement`; the JDK's `ReadWriteLock` implementations; logging frameworks (`Log` abstraction × `Appender` implementation).

## 3.8 Composite

**Intent.** Treat individual objects and **composites** of objects uniformly.

```text
                    ┌───────────────┐
                    │   Component   │  ← the common supertype
                    │  + operation()│
                    └───────┬───────┘
                            │
            ┌───────────────┼───────────────┐
            ▼                               ▼
   ┌─────────────────┐            ┌────────────────────┐
   │      Leaf       │            │      Composite     │
   │ a single item   │            │  holds Components  │
   │                 │            │  ┌───────────────┐ │
   │ operation() →   │            │  │ children:List │ │
   │  do the thing   │            │  └───────────────┘ │
   └─────────────────┘            │  operation() →     │
                                  │   child.operation()│
                                  │   for each child   │
                                  └────────────────────┘

   A Composite IS-A Component, and it CONTAINS Components.
   → the client never needs to know whether it holds a leaf or a branch.
```

```java
// A file system tree — the classic composite
public interface Node {
    long size();
    void print(String indent);
}

public final class FileNode implements Node {
    private final String name;
    private final long size;
    private final List<Node> children = new ArrayList<>();

    public FileNode(String name, long size) { this.name = name; this.size = size; }

    public void add(Node child) { children.add(child); }

    @Override public long size() {
        long total = size;
        for (Node child : children) total += child.size();
        return total;
    }

    @Override public void print(String indent) {
        System.out.println(indent + name + " (" + size + ")");
        for (Node child : children) child.print(indent + "  ");
    }
}
```

**When to use:** a tree structure where the client should not distinguish leaf from branch.
**When NOT to use:** the types are genuinely different and the client *must* distinguish them.
**Production use:** the JDK's `java.awt.Container`/`Component`; DOM trees; Spring's `CompositeCacheManager`; menu hierarchies.

## 3.9 Decorator

**Intent.** Attach additional responsibility to an object **dynamically**, keeping the object itself unchanged.

```text
   ❌ WITHOUT Decorator — inheritance explosion
   ───────────────────────────────────────
   BufferedInputStream
   GzipInputStream
   BufferedGzipInputStream      ← the 2×2 explosion

   ✅ WITH Decorator — compose at runtime
   ───────────────────────────────────────
   InputStream
      ├── FileInputStream
      └── BufferedInputStream (decorates an InputStream)
             └── GzipInputStream (decorates a BufferedInputStream)

   new GzipInputStream(new BufferedInputStream(new FileInputStream(f)))
   → each wrapper has the SAME type → they stack
```

```java
// A real example: a cache decorator
public interface ReportRenderer {
    String render(String reportId);
}

public final class DatabaseReportRenderer implements ReportRenderer {
    @Override public String render(String reportId) {
        return "DB:" + reportId;
    }
}

/** A decorator — it has the SAME type as what it decorates. */
public final class CachingReportRenderer implements ReportRenderer {
    private final ReportRenderer delegate;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Function<String, String> keyMapper;

    public CachingReportRenderer(ReportRenderer delegate, Function<String, String> keyMapper) {
        this.delegate = Objects.requireNonNull(delegate);
        this.keyMapper = Objects.requireNonNull(keyMapper);
    }

    @Override
    public String render(String reportId) {
        return cache.computeIfAbsent(keyMapper.apply(reportId), k -> delegate.render(reportId));
    }
}
```

**The defining characteristic:** a Decorator implements the **same interface** as the thing it decorates, and holds a reference to it. That is what lets them stack.

**When to use:** behaviour should be added optionally and combinably; you want to avoid an inheritance explosion.
**When NOT to use:** the order of the wrappers matters and is hard to reason about (a real cost).
**Production use:** `java.io` streams; `Collections.synchronizedList`; Spring's `TransactionAwareCacheDecorator`; OkHttp's interceptor chain.

## 3.10 Facade

**Intent.** Provide a **simplified** interface to a complex subsystem.

```text
   ❌ WITHOUT Facade — the client orchestrates the subsystem
   ───────────────────────────────────────────────────
   Client ──▶ AuthService
          ──▶ InventoryService
          ──▶ PricingService
          ──▶ PaymentService
          ──▶ ShippingService      ← 5 calls, 5 objects, 5 orderings

   ✅ WITH Facade
   ──────────────
   Client ──▶ ┌──────────────┐
              │ OrderFacade  │──▶ orchestrates the 5 services internally
              └──────────────┘     and exposes ONE method
```

```java
public final class OrderFacade {
    private final AuthService auth;
    private final InventoryService inventory;
    private final PricingService pricing;
    private final PaymentService payment;
    private final ShippingService shipping;

    public OrderFacade(AuthService auth, InventoryService inventory, PricingService pricing,
                       PaymentService payment, ShippingService shipping) {
        this.auth = auth;
        this.inventory = inventory;
        this.pricing = pricing;
        this.payment = payment;
        this.shipping = shipping;
    }

    /** One call, many steps — and the ORDER is guaranteed. */
    public OrderResult placeOrder(OrderRequest request) {
        if (!auth.isAuthenticated(request.userId())) {
            return OrderResult.failure("Not authenticated");
        }
        if (!inventory.isAvailable(request.sku(), request.quantity())) {
            return OrderResult.failure("Out of stock");
        }
        BigDecimal price = pricing.priceFor(request.sku(), request.quantity());
        PaymentResult paid = payment.charge(request.userId(), price);
        if (!paid.success()) {
            return OrderResult.failure("Payment failed: " + paid.message());
        }
        shipping.schedule(request.sku(), request.quantity());
        return OrderResult.success(paid.transactionId());
    }
}
```

**When to use:** a subsystem is complex and clients need only a few operations; you want to enforce an *order* of operations.
**When NOT to use:** it becomes a "god object" that hides too much.
**Production use:** Spring's `JdbcTemplate`; every SDK's "one-call" entry point; `java.net.URI` over `URL`.

## 3.11 Flyweight

**Intent.** Share (intern) expensive state across many similar objects.

```text
   ❌ WITHOUT Flyweight — 1,000,000 objects, each with its own duplicated state
   ──────────────────────────────────────────────────────────────────────
   Each Flyweight object:  [char[] text (1000 chars)] [bitmap image (50 KB)]
   Total memory: ~50 GB 💥

   ✅ WITH Flyweight — the intrinsic state is SHARED
   ───────────────────────────────────────────────────
   ┌──────────────────────────────────────────────────────────┐
   │  FlyweightFactory                                        │
   │  Map<Character, Glyph> pool                              │
   │                                                          │
   │  'A' ──▶ ┌──────────────────┐   'B' ──▶ ┌──────────────┐│
   │          │ Glyph 'A'        │            │ Glyph 'B'    ││
   │          │ shared: bitmap   │            │ shared:bitmap││
   │          │ shared: char[]   │            │ shared:char[]││
   │          └──────────────────┘            └──────────────┘│
   │                                                          │
   │  Document holds only:  [position][style] [position][style] ... │
   │  → the EXTRINSIC state (position, style) lives in the context │
   └──────────────────────────────────────────────────────────┘

   INTRINSIC state  = shared, immutable   (the character, the font metrics)
   EXTRINSIC state = per-context, passed in (the position, the colour)
```

**The JDK's own Flyweights:**

| JDK class | What is shared |
|---|---|
| `String` | The `char[]`/`byte[]` value — the string pool |
| `Integer` (the cache) | `-128..127` boxed values |
| `BigDecimal`? | No — but `Long`, `Short`, `Byte`, `Character` all have caches |
| `ThreadLocal`? | No — but the `Class` object, the `Method` object |

```java
// A correct Flyweight
public final class Glyph {
    // INTRINSIC — final and shared
    private final char character;
    private final byte[] bitmap;          // shared, immutable

    // EXTRINSIC — NOT stored here; passed to the render method
    private int x, y;
    private String style;

    Glyph(char character) {
        this.character = character;
        this.bitmap = renderBitmap(character);   // the expensive work, done ONCE
    }

    public void render(int x, int y, String style) {   // extrinsic state passed in
        this.x = x; this.y = y; this.style = style;
        // blit bitmap at (x,y) with style
    }
}

public final class GlyphFactory {
    private final Map<Character, Glyph> pool = new HashMap<>();

    public Glyph get(char c) {
        return pool.computeIfAbsent(c, Glyph::new);   // one instance per character
    }
}
```

**When to use:** many objects, identical shared state, expensive to create.
**When NOT to use:** the objects are not that many, or the "shared" state is not actually immutable.
**Production use:** the String pool; `Integer.valueOf` cache; GUI toolkit glyphs; row-flying in game engines; connection objects in some pools.

## 3.12 Proxy

**Intent.** Provide a **placeholder** for another object to control access to it.

```text
   Client ──▶ Proxy (same interface as RealSubject)
                 │  intercepts, delegates
                 ▼
              RealSubject

   The proxy implements a DIFFERENT interface than the real thing
   (or the same one, acting as a substitute).
```

**Four flavours:**

| Flavour | Purpose | Example |
|---|---|---|
| **Remote** | Access an object in another address space | RMI, gRPC stubs |
| **Protection** | Control access (auth) | Spring Security proxies, `SecurityManager` |
| **Virtual (lazy)** | Create the real object only when needed | Hibernate lazy loading, `java.lang.reflect.Proxy` |
| **Caching** | Store results | Spring's `@Cacheable` |

```java
// A JDK dynamic proxy — the reflection-based flavour
public class AuditInvocationHandler implements InvocationHandler {
    private final Object target;
    private final AuditLog auditLog;

    public AuditInvocationHandler(Object target, AuditLog auditLog) {
        this.target = target;
        this.auditLog = auditLog;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = method.invoke(target, args);
            auditLog.record(method.getName(), args, result, System.nanoTime() - start);
            return result;
        } catch (InvocationTargetException e) {
            auditLog.recordFailure(method.getName(), e.getCause());
            throw e.getCause();       // ⚠ unwrap — or the caller sees InvocationTargetException
        }
    }
}

// Usage
OrderService service = (OrderService) Proxy.newProxyInstance(
        OrderService.class.getClassLoader(),
        new Class<?>[] { OrderService.class },
        new AuditInvocationHandler(new RealOrderService(), auditLog));
```

**When to use:** access control, lazy creation, remote access, caching, or "the object is expensive and may not be needed".
**When NOT to use:** in-JVM performance is fine and the extra indirection is pure cost.
**Production use:** Spring AOP (CGLIB for classes, JDK proxies for interfaces); Hibernate lazy loading; `java.lang.reflect.Proxy`; API gateways.

# ═══════════════════ BEHAVIOURAL (11) ═══════════════════

## 3.13 Chain of Responsibility

**Intent.** Pass a request along a chain of handlers until one handles it.

```text
   Request ──▶ Handler1 ──▶ Handler2 ──▶ Handler3 ──▶ null (unhandled)
                 │            │            │
              handles?     handles?     handles?
                 │ yes → stop  │ yes → stop  │ yes → stop
                 ▼
              handle it
```

```java
public interface Handler {
    boolean handles(OrderRequest request);
    void handle(OrderRequest request);
}

public final class FraudCheckHandler implements Handler {
    @Override public boolean handles(OrderRequest r) { return r.amount().compareTo(LIMIT) > 0; }
    @Override public void handle(OrderRequest r) { r.flagForManualReview(); }
}
```

**When to use:** a request may be handled by one of several handlers; you want to decouple the sender from the handlers.
**When NOT to use:** you know exactly which handler applies — just call it.
**Production use:** Spring's `HandlerInterceptor` chain; servlet filters; validation chains; Kafka interceptors.

## 3.14 Command

**Intent.** Encapsulate a request as an **object**, so it can be stored, passed, and executed later.

```text
   WITHOUT Command — you can only call a method NOW
   ──────────────────────────────────────────────────────
   button.onClick();     ← executes immediately, nothing to queue or undo

   WITH Command
   ─────────────
   button.setCommand(new ToggleMuteCommand(device));   ← an OBJECT
   button.onClick();     ← executes the command later

   The command gives you:  queueing · logging · undo/redo · transaction · serialisation
```

```java
public interface Command {
    void execute();
    void undo();
}

public final class ToggleMuteCommand implements Command {
    private final Device device;
    private boolean previousState;

    public ToggleMuteCommand(Device device) { this.device = device; }

    @Override public void execute() {
        previousState = device.isMuted();
        device.setMuted(!previousState);
    }

    @Override public void undo() {
        device.setMuted(previousState);
    }
}

// The invoker
public final class RemoteControl {
    private final Deque<Command> history = new ArrayDeque<>();

    public void press(Command command) {
        command.execute();
        history.push(command);
    }

    public void undo() {
        if (!history.isEmpty()) history.pop().undo();
    }
}
```

**When to use:** you need queuing, scheduling, logging, undo/redo, or transactions around an action.
**When NOT to use:** a direct method call does the job.
**Production use:** `Runnable`/`Callable`; Spring's `TransactionTemplate`; command-line patterns; job queues.

## 3.15 Interpreter

**Intent.** Define a small language and an interpreter that evaluates it.

```text
   Expression (abstract)
      ├── NumberExpression   (42)
      ├── AddExpression      (left + right)
      ├── MultiplyExpression (left * right)
      └── VariableExpression (x)

   This is the classic "interpreter pattern" in the GoF sense —
   note that it is NOT the "interpreter" from the AI/compilers world
   (which is a different, unrelated pattern with a confusingly similar name).
```

**When to use:** a simple, stable grammar you want to evaluate at runtime (rules engines, DSLs, expression builders).
**When NOT to use:** the grammar is complex — write a real parser (ANTLR) instead.
**Production use:** Drools (rule engines); SpEL; JSqlParser; many configuration DSLs.

> ⚠️ **The confusing naming:** the GoF "Interpreter" pattern is a *tiny* AST evaluator.
> The compiler-theory "interpreter" (vs "compiler") is a completely different concept
> that happens to share a name. Knowing this distinction is a mark of a well-read candidate.

## 3.16 Iterator

**Intent.** Provide a way to traverse a collection **without exposing its internal structure**.

```text
   Client ──▶ Iterator ◄── Aggregate
                hasNext()      (creates the Iterator)
                next()

   The client never sees the internal array / linked list / tree.
   → the collection's structure can change without breaking the client.
```

```java
// The JDK's own implementation
public class MyCollection implements Iterable<String> {
    private final List<String> items = List.of("a", "b", "c");

    @Override
    public Iterator<String> iterator() {
        return new Iterator<>() {
            private int index = 0;
            @Override public boolean hasNext() { return index < items.size(); }
            @Override public String next() {
                if (!hasNext()) throw new NoSuchElementException();
                return items.get(index++);
            }
        };
    }
}
```

**When to use:** you want a uniform traversal over heterogeneous collection types.
**When NOT to use:** the JDK's `Iterable`/`Iterator` already cover 95% of cases.
**Production use:** the entire `java.util` iterators; `Stream`'s `Spliterator` is its close cousin.

## 3.17 Mediator

**Intent.** Encapsulate how a set of objects interact, so they do not reference each other.

```text
   ❌ WITHOUT Mediator — N objects, N² references
   ───────────────────────────────────────────────
        ┌──────┐   ┌──────┐   ┌──────┐
        │  A   │◄─►│  B   │◄─►│  C   │      every object knows every other
        └──────┘   └──────┘   └──────┘      → change one, all must change

   ✅ WITH Mediator
   ───────────────
        ┌──────┐   ┌──────┐   ┌──────┐
        │  A   │──▶│  M   │◄──│  C   │      objects only know the mediator
        └──────┘   └──────┘   └──────┘      → the mediator knows everyone
```

**When to use:** many objects interact in a complex, well-defined way (a chat room, a UI form, a workflow engine).
**When NOT to use:** the interactions are simple — a direct reference is clearer.
**Production use:** Java's `Thread` (the `Thread` object mediates between the OS thread and the runnable); GUI event dispatchers; Apache Camel.

## 3.18 Memento

**Intent.** Capture an object's internal state so it can be restored later, **without violating encapsulation**.

```text
   ┌────────────┐   createMemento()   ┌────────────┐
   │ Originator │──────────────────────▶│  Memento  │
   │ (the      │◀──────────────────────│ (opaque   │
   │  document) │      restore(state)  │  snapshot) │
   └────────────┘                      └────────────┘

   The Memento is OPAQUE to everyone except the Originator.
   → the client can undo, but cannot read the state.
```

**When to use:** you need undo/redo, checkpoints, or a snapshot/restore.
**When NOT to use:** the state is simple enough to copy directly.
**Production use:** text editors (undo stack); game save states; transaction snapshots; `java.util.Properties` clone semantics.

## 3.19 Observer

**Intent.** Define a one-to-many dependency so that when one object changes state, all its dependents are notified.

```text
   WITHOUT Observer — the subject knows every concrete observer
   ────────────────────────────────────────────────────────────────
   Subject ──▶ EmailObserver
           ──▶ SmsObserver
           ──▶ PushObserver         ← add a new channel = edit Subject

   WITH Observer
   ───────────────
   Subject ──▶ [Observer, Observer, Observer]   ← a LIST
       │  register() / unregister() / notify()
       ▼
   Each observer implements the same interface → the subject knows
   only the interface → new observers need no change to the Subject.
```

```java
// The classic, production-safe implementation
public final class OrderService implements AutoCloseable {

    private final List<Consumer<Order>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<Order> listener) { listeners.add(listener); }
    public void unsubscribe(Consumer<Order> listener) { listeners.remove(listener); }

    public void publish(Order order) {
        for (Consumer<Order> listener : listeners) {
            try {
                listener.accept(order);
            } catch (RuntimeException e) {
                // ONE bad listener must not stop the others
                System.err.println("Listener failed: " + e);
            }
        }
    }

    @Override public void close() { listeners.clear(); }
}
```

**Observer vs Pub/Sub:**

```text
   OBSERVER                              PUB/SUB
   ─────────                              ──────
   The subject holds the listeners.       A BROKER holds the subscriptions.
   The subject KNOWS its observers.      The publisher does NOT know the subscribers.
   In-process only.                      Can be cross-process (Kafka, RabbitMQ).
   Synchronous, usually.                 Usually asynchronous.
   Failure of one observer can affect    The broker isolates them.
   the others.                           No shared failure domain.
```

**When to use:** a change must notify an unknown number of interested parties.
**When NOT to use:** there are 1–2 listeners and they are known — just call them.
**Production use:** Spring's `ApplicationEventPublisher`; Kafka listeners; Guava's `EventBus`; `Observable`/`Observer` in the JDK (deprecated since Java 9).

**The three classic Observer problems:** (1) *lifecycle* — a leak if you don't unsubscribe; (2) *ordering* — no guaranteed notification order; (3) *re-entrancy* — a listener that modifies the subject.

## 3.20 State

**Intent.** Allow an object to alter its behaviour when its internal state changes — by swapping the *behaviour object* rather than by branching on state.

```text
   ❌ WITHOUT State — a state machine inside the object
   ─────────────────────────────────────────────────
   class Order {
       void process() {
           if (state == PENDING)       { ... }
           else if (state == PAID)     { ... }
           else if (state == SHIPPED)  { ... }
           else if (state == DELIVERED){ ... }
           // every new state = edit this method (and probably others)
       }
   }

   ✅ WITH State
   ───────────────
   class Order {
       private OrderState state = new PendingState();
       void process() { state.process(this); }   ← delegate, no branching
   }

   interface OrderState { void process(Order o); }
   class PendingState  implements OrderState { public void process(Order o) {...} }
   class PaidState    implements OrderState { public void process(Order o) {...} }
   class ShippedState implements OrderState { public void process(Order o) {...} }

   → each state is a separate class → open for extension
```

**When to use:** an object's behaviour depends strongly on its state and the states are stable.
**When NOT to use:** a simple `enum` + `switch` is enough — the State pattern creates a class per state, which is real overhead for 3–4 states.
**Production use:** Java's `Thread.State` handling in the JDK; order/workflow engines; TCP connection state machines.

**The State vs Strategy decision:**

```text
   STATE                                STRATEGY
   ──────                               ────────
   The client does NOT choose.           The client DOES choose.
   The object changes state itself.     The strategy is supplied from outside.
   Behaviour is a consequence of         Behaviour is a deliberate
   "where am I now?"                     "how do I want this done?"
   Usually a private field.              Usually a constructor/setter parameter.
   States often form a closed chain.     Strategies are independent and reusable.
```

## 3.21 Strategy

**Intent.** Define a family of algorithms, encapsulate each one, and make them interchangeable.

```text
   ❌ WITHOUT Strategy — the switch statement
   ───────────────────────────────────────
   BigDecimal calculate(Order order) {
       if (customerType == GOLD)   { ... }
       else if (customerType == SILVER) { ... }
       else if (customerType == BRONZE) { ... }
       // adding a customer type = editing this method
   }

   ✅ WITH Strategy
   ───────────────
   BigDecimal calculate(Order order) {
       return pricingStrategy.price(order);   ← one line, no branching
   }

   The CALL SITE chooses the strategy:
   new OrderService(new GoldPricingStrategy())
```

```java
public interface PricingStrategy {
    BigDecimal price(Order order);
}

public final class GoldPricingStrategy implements PricingStrategy {
    @Override public BigDecimal price(Order order) {
        return order.subtotal().multiply(new BigDecimal("0.90"));
    }
}

public final class NoDiscountPricingStrategy implements PricingStrategy {
    @Override public BigDecimal price(Order order) {
        return order.subtotal();
    }
}
```

**When to use:** you want to swap an algorithm at runtime; you have a growing `if`/`switch` on a "type".
**When NOT to use:** there are only 2–3 cases and they never change — a `switch` is simpler.
**Production use:** `Comparator`; Spring's `ResourceLoader`; payment-gateway selection; Spring's `@Qualifier` selecting a `Strategy` bean.

## 3.22 Template Method

**Intent.** Define the skeleton of an algorithm, letting steps be overridden by subclasses.

```text
   abstract class DataProcessor {

       public final void process() {          // ← final: the TEMPLATE
           read();
           transform();
           write();
           cleanup();
       }

       protected abstract void read();
       protected abstract void transform();
       protected void write() { }             // optional hook
       protected void cleanup() { }           // optional hook
   }
```

**The two kinds of methods:**

| Kind | Modifier | Purpose |
|---|---|---|
| **Template method** | `final` | The invariant algorithm skeleton — subclasses CANNOT change it |
| **Hooks** | `abstract` / `overridable` | The variable steps — subclasses choose |

**When to use:** the algorithm's *structure* is fixed but some steps vary.
**When NOT to use:** the steps vary so much that the "skeleton" is meaningless — use Strategy.
**Production use:** Spring's `AbstractApplicationContext.refresh()`; `HttpServlet.service()`; `java.util.Arrays.sort` → `Arrays$LegacyMergeSort`; JUnit's `@Before`/`@Test`/`@After` lifecycle; every "template" you have ever written with `if (step != null) step()`.

## 3.23 Visitor

**Intent.** Define a new operation over a set of objects **without changing their classes**.

```text
   ❌ WITHOUT Visitor — add an operation = edit every element class
   ────────────────────────────────────────────────────────────
   class Order    { void accept(??? visitor) { ... } }   ← must be edited
   class Invoice  { void accept(??? visitor) { ... } }   ← must be edited
   class Shipment { void accept(??? visitor) { ... } }   ← must be edited

   ✅ WITH Visitor
   ───────────────
   class Order    { void accept(Visitor v) { v.visit(this); } }
   class Invoice  { void accept(Visitor v) { v.visit(this); } }
   class Shipment { void accept(Visitor v) { v.visit(this); } }

   interface Visitor {
       void visit(Order o);
       void visit(Invoice i);
       void visit(Shipment s);
   }

   class TotalCalculator implements Visitor {   ← NEW operation, NO element edited
       void visit(Order o)    { ... }
       void visit(Invoice i)  { ... }
       void visit(Shipment s) { ... }
   }
```

**When to use:** the object structure is **stable** but you need to add operations frequently; the operation is complex and you do not want it on the element.
**When NOT to use:** the element classes change often — then Visitor is painful (every visitor must be updated).
**Production use:** the JDK's `java.lang.Compiler` and `FileVisitor`; ANTLR's generated visitors; most "report by type" code.

**Visitor's two famous costs:**
1. **The "visitable" pollution** — every element must implement `accept()`, adding a method that is meaningless to it.
2. **The double dispatch** — `element.accept(v)` → `v.visit(element)` — the JVM does two virtual dispatches.

---

## 4. Visualizations

### 4.1 The 23 patterns as a decision tree

```text
   ┌─────────────────────────────────────────────────────────────────────────┐
   │  THE FORCE YOU ARE FACING                                             │
   └─────────────────────────────────────────────────────────────────────────┘
        │
        ├── "I don't know WHAT to create" ──────────── CREATIONAL
        │      │
        │      ├── one product, subclass decides ──────── Factory Method
        │      ├── a FAMILY of products ───────────────── Abstract Factory
        │      ├── many optional arguments ───────────── Builder
        │      ├── copies of an existing object ───────── Prototype
        │      └── exactly one, global access ─────────── Singleton
        │
        ├── "these don't compose well" ──────────────── STRUCTURAL
        │      │
        │      ├── incompatible interface ─────────────── Adapter
        │      ├── two independent axes vary ──────────── Bridge
        │      ├── tree of parts, treat as one ────────── Composite
        │      ├── add behaviour, same type ───────────── Decorator
        │      ├── simplify a subsystem ───────────────── Facade
        │      ├── share expensive state ──────────────── Flyweight
        │      └── substitute / control access ───────── Proxy
        │
        └── "these objects need to interact" ─────────── BEHAVIOURAL
               │
               ├── pass to handlers until one fits ────── Chain of Responsibility
               ├── package a request as an object ─────── Command
               ├── evaluate a small language ─────────── Interpreter
               ├── traverse without exposing internals ── Iterator
               ├── centralise many-to-many interaction ── Mediator
               ├── snapshot & restore ─────────────────── Memento
               ├── notify on change ───────────────────── Observer
               ├── behaviour changes with state ────────── State
               ├── swap an algorithm ──────────────────── Strategy
               ├── fixed skeleton, variable steps ─────── Template Method
               └── new operation over stable types ────── Visitor
```

### 4.2 The five Singleton implementations compared

```text
   ┌────────────────────┬───────────┬────────┬───────────┬───────────┬──────────┐
   │ Implementation     │ Thread-   │ Lazy   │ Reflection│ Deserial- │ Needs    │
   │                    │ safe      │        │ safe      │ isation   │ config   │
   ├────────────────────┼───────────┼────────┼───────────┼───────────┼──────────┤
   │ ① Eager            │ ✅        │ ❌     │ ❌        │ ❌        │ ❌       │
   │ ② Lazy-holder      │ ✅        │ ✅     │ ❌        │ ❌        │ ❌       │
   │ ③ DCL (+volatile)  │ ✅        │ ✅     │ ❌        │ ❌        │ ✅       │
   │ ④ ENUM  ⭐         │ ✅        │ ✅     │ ✅        │ ✅        │ ❌       │
   │ ⑤ Static holder    │ ✅        │ ✅     │ ❌        │ ❌        │ ❌       │
   └────────────────────┴───────────┴────────┴───────────┴───────────┴──────────┘

   ① EAGER            → private static final INSTANCE = new X();
   ② LAZY-HOLDER      → private static class Holder { static final X INSTANCE = new X(); }
   ③ DCL              → volatile + double-checked synchronized
   ④ ENUM             → enum X { INSTANCE; }
   ⑤ STATIC HOLDER    → a static nested class holding the instance
```

### 4.3 The double-checked locking memory-model bug

```text
   ❌ WITHOUT volatile — the "broken DCL" (pre-Java 5)
   ──────────────────────────────────────────────────────
   Thread A                          Thread B
   ────────                          ────────
   1. reads instance → null
   2. enters synchronized
                                     1. reads instance → NOT null (!)
   3. instance = new Config()        2. uses it 💥  ← the constructor may not
      │  ┌─────────────────────┐        │              have finished writing
      │  │ allocate             │       │              the fields!
      │  │ run constructor      │       │
      │  │ assign to instance ──┼──▶ 3. ── the WRITE to `instance` was
      │  └─────────────────────┘        │   REORDERED before the writes
      ▼                                ▼      to the object's fields
   4. constructor done

   The JMM allows this reordering because there is NO happens-before
   edge between the constructor's writes and Thread B's reads.

   ✅ WITH volatile
   ─────────────────
   volatile makes the write to `instance` a RELEASE and the read an ACQUIRE.
   Everything the constructor wrote HAPPENS-BEFORE Thread B's reads.
   → the JSR-133 fix (Java 5) made DCL correct.
```

### 4.4 Singleton attacks — and how the `enum` survives all of them

```text
   ATTACK ① — Reflection
   ────────────────────────────────────────────────────
   Constructor<EagerConfig> c = EagerConfig.class.getDeclaredConstructor();
   c.setAccessible(true);
   EagerConfig second = c.newInstance();          💥 TWO INSTANCES

   WHY THE ENUM SURVIVES:
   The JVM REFUSES to instantiate an enum reflectively:
     "java.lang.IllegalArgumentException: Cannot reflectively create enum objects"

   ATTACK ② — Deserialisation
   ────────────────────────────────────────────────────
   ObjectInputStream in = new ObjectInputStream(stream);
   EagerConfig fromStream = (EagerConfig) in.readObject();   💥 A NEW INSTANCE

   WHY THE ENUM SURVIVES:
   Serialisation is SPECIAL-CASED for enums: the stream stores the NAME,
   and readObject returns the CANONICAL INSTANCE from the class's static
   constants. So `fromStream == EagerConfig.INSTANCE` is TRUE.

   ATTACK ③ — Unsafe.allocateInstance
   ────────────────────────────────────────────────────
   Unsafe unsafe = ...;
   EagerConfig hacked = (EagerConfig) unsafe.allocateInstance(EagerConfig.class);
   💥 but the ENUM is refused here too.

   ATTACK ④ — Threading (the "double instance" race)
   ────────────────────────────────────────────────────
   Two threads call getInstance() simultaneously → both see null → both
   construct.  ✅ The enum's class-initialisation lock prevents this.
```

### 4.5 Decorator vs Proxy vs Adapter vs Facade — the four structural look-alikes

```text
   ┌──────────────┬────────────────────┬────────────────────┬──────────────────┐
   │              │ DECORATOR          │ PROXY              │ ADAPTER          │ FACADE         │
   ├──────────────┼────────────────────┼────────────────────┼──────────────────┼────────────────┤
   │ Implements   │ the SAME interface │ the SAME interface │ a DIFFERENT      │ a NEW, simpler │
   │              │ as the wrapped     │ as the real subject │ interface that   │ interface      │
   │              │ object             │                    │ the client wants │                │
   ├──────────────┼────────────────────┼────────────────────┼──────────────────┼────────────────┤
   │ Purpose      │ ADD behaviour      │ CONTROL access     │ MAKE incompatible │ SIMPLIFY a    │
   │              │                    │ (auth, lazy, remote)│ things fit       │ subsystem      │
   ├──────────────┼────────────────────┼────────────────────┼──────────────────┼────────────────┤
   │ Stacks with  │ ✅ YES (same type) │ ❌ not usually     │ ❌                │ ❌              │
   │ itself?      │                    │                    │                  │                │
   ├──────────────┼────────────────────┼────────────────────┼──────────────────┼────────────────┤
   │ Knows the    │ ✅ holds the       │ ✅ holds the       │ ✅ holds the      │ ✅ holds MANY  │
   │ real thing?  │ delegate           │ real subject      │ adaptee          │ collaborators   │
   ├──────────────┼────────────────────┼────────────────────┼──────────────────┼────────────────┤
   │ Client sees  │ same type as      │ the interface      │ the ADAPTER's    │ only the facade│
   │              │ the original       │                    │ interface        │                │
   └──────────────┴────────────────────┴────────────────────┴──────────────────┴────────────────┘

   EXAMPLE:
     Decorator → BufferedInputStream wraps InputStream
     Proxy     → Spring AOP proxy wraps the bean
     Adapter   → InputStreamReader wraps InputStream
     Facade    → JdbcTemplate wraps the whole JDBC API
```

### 4.6 State vs Strategy — the side-by-side

```text
   STATE                                    STRATEGY
   ──────                                   ────────
   ┌──────────────────┐                     ┌──────────────────┐
   │      Order       │                     │   OrderService   │
   │ ┌──────────────┐ │                     │                  │
   │ │ OrderState   │ │  ← the OBJECT      │ pricingStrategy  │ ← the CLIENT
   │ │ (private)    │ │    owns the state  │ (injected)       │   chooses
   │ └──────┬───────┘ │                     └────────┬─────────┘
   └────────┼─────────┘                              │
            │ delegate                                │ delegate
   ┌────────┴─────────┐              ┌─────────────┴────────────┐
   │ Pending Paid Shipped│            │ Gold  Silver  NoDiscount │
   └────────────────────┘              └──────────────────────────┘

   WHO CHOOSES?  The object itself,      The client, from outside.
                 as its state changes.

   MEANING       "What should I do        "How should I do
                  in THIS state?"          THIS thing?"
```

### 4.7 Observer vs Publish/Subscribe

```text
   OBSERVER (in-process)                    PUB/SUB (brokered)
   ─────────────────────                    ───────────────────
   ┌──────────┐                             ┌──────────┐      ┌──────────┐
   │ Publisher│──── notify() ────┐         │ Publisher│────►│  BROKER  │◄────┐
   └──────────┘                  │         └──────────┘      │ (Kafka)  │     │
        │  holds the list        ▼              ▲            └──────────┘     │
        │  of subscribers   ┌──────────┐       │                             │
        └──────────────────►│Subscriber│───────┘                     ┌───────▼─────┐
                           └──────────┘                               │ Subscriber  │
                                                                    └─────────────┘

   Publisher KNOWS its subscribers.        The publisher does NOT know the subscribers.
   Tight coupling, one JVM.               Loose coupling, cross-process.
   Sync, usually.                         Async, usually.
   Failure can propagate.                  The broker isolates failures.
```

### 4.8 The Flyweight — intrinsic vs extrinsic state

```text
   ❌ WITHOUT Flyweight — 1,000,000 objects each with duplicated state
   ──────────────────────────────────────────────────────────────────────
   each Glyph:  [ char ][ bitmap 50 KB ]     →  ~50 GB 💥

   ✅ WITH Flyweight
   ─────────────────
   ┌──────────────────── FLYWEIGHT FACTORY ─────────────────────┐
   │  pool: Map<Character, Glyph>                               │
   │                                                            │
   │   'A' ──▶ ┌───────────────────────┐                        │
   │           │ Glyph                 │                        │
   │           │  INTRINSIC (shared):  │                        │
   │           │    char  = 'A'        │                        │
   │           │    bitmap = 50 KB     │  ◀── ONE instance      │
   │           │  EXTRINSIC (per-use): │      for all 'A's     │
   │           │    x, y, style        │  ◀── NOT stored here  │
   │           └───────────────────────┘                        │
   └────────────────────────────────────────────────────────────┘

   ┌────────────────────── CONTEXT (per document) ─────────────┐
   │  [glyphRef, x=10, y=20, style=BOLD]                        │
   │  [glyphRef, x=18, y=20, style=ITALIC]   ← the extrinsic   │
   │  [glyphRef, x=26, y=20, style=BOLD]      state lives HERE  │
   └────────────────────────────────────────────────────────────┘

   Memory:  1 bitmap per CHARACTER  +  3 small records per document
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — The `enum` Singleton under attack

```java
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;

public class SingletonDemo {

    // ① A vulnerable singleton
    public static final class BadSingleton implements java.io.Serializable {
        private static final BadSingleton INSTANCE = new BadSingleton();
        public int value = 42;
        private BadSingleton() {}
        public static BadSingleton getInstance() { return INSTANCE; }
    }

    // ④ The recommended singleton
    public enum GoodSingleton implements java.io.Serializable {
        INSTANCE;
        public int value = 42;
    }

    public static void main(String[] args) throws Exception {
        // --- ATTACK 1: reflection ---
        Constructor<BadSingleton> c = BadSingleton.class.getDeclaredConstructor();
        c.setAccessible(true);
        BadSingleton reflected = c.newInstance();
        System.out.println("Bad  — reflection broke it? " + (reflected != BadSingleton.getInstance()));

        try {
            Constructor<GoodSingleton> gc = GoodSingleton.class.getDeclaredConstructor();
            gc.setAccessible(true);
            gc.newInstance();
            System.out.println("Good — reflection succeeded?! (unexpected)");
        } catch (Exception e) {
            System.out.println("Good — reflection blocked: " + e.getClass().getSimpleName()
                    + " → " + e.getMessage());
        }

        // --- ATTACK 2: deserialisation ---
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(BadSingleton.getInstance());
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            BadSingleton deser = (BadSingleton) ois.readObject();
            System.out.println("Bad  — deserialisation broke it? " + (deser != BadSingleton.getInstance()));
        }

        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos2)) {
            oos.writeObject(GoodSingleton.INSTANCE);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos2.toByteArray()))) {
            GoodSingleton deser = (GoodSingleton) ois.readObject();
            System.out.println("Good — deserialisation broke it? " + (deser != GoodSingleton.INSTANCE)
                    + "  (same instance: " + (deser == GoodSingleton.INSTANCE) + ")");
        }
    }
}
```

**Actual output:**

```text
Bad  — reflection broke it? true
Good — reflection blocked: IllegalArgumentException → Cannot reflectively create enum objects
Bad  — deserialisation broke it? true
Good — deserialisation broke it? false  (same instance: true)
```

**Takeaway:** the `enum` survives both attacks. The reflection attack fails because the JVM refuses to instantiate enums reflectively; the deserialisation attack fails because Java's serialisation is special-cased for enums — the stream stores the *name* and `readObject` returns the canonical constant.

### Example 2 — Strategy replacing a growing `switch`

```java
import java.math.BigDecimal;
import java.util.List;

public class StrategyDemo {

    interface PricingStrategy {
        BigDecimal price(BigDecimal subtotal, int itemCount);
    }

    record Order(BigDecimal subtotal, int itemCount) {}

    static final class VolumeDiscount implements PricingStrategy {
        @Override public BigDecimal price(BigDecimal subtotal, int count) {
            return count >= 10 ? subtotal.multiply(new BigDecimal("0.80")) : subtotal;
        }
    }

    static final class LoyaltyDiscount implements PricingStrategy {
        private final int tier;
        LoyaltyDiscount(int tier) { this.tier = tier; }
        @Override public BigDecimal price(BigDecimal subtotal, int count) {
            return switch (tier) {
                case 1 -> subtotal.multiply(new BigDecimal("0.95"));
                case 2 -> subtotal.multiply(new BigDecimal("0.90"));
                default -> subtotal.multiply(new BigDecimal("0.85"));
            };
        }
    }

    static final class PricingService {
        private final PricingStrategy strategy;   // ← injected, chosen by the client

        PricingService(PricingStrategy strategy) { this.strategy = strategy; }

        BigDecimal quote(Order order) { return strategy.price(order.subtotal(), order.itemCount()); }
    }

    public static void main(String[] args) {
        Order order = new Order(new BigDecimal("100.00"), 12);

        System.out.println("volume  = " + new PricingService(new VolumeDiscount()).quote(order));
        System.out.println("gold    = " + new PricingService(new LoyaltyDiscount(2)).quote(order));
        System.out.println("no disc = " + new PricingService(new LoyaltyDiscount(0)).quote(order));

        // The SAME service, different behaviour at runtime
        PricingService service = new PricingService(new VolumeDiscount());
        System.out.println("runtime switch → " + service.quote(order));
    }
}
```

**Actual output:**

```text
volume  = 80.00
gold    = 90.00
no disc = 85.00
runtime switch → 80.00
```

**Takeaway:** the `PricingService` has **no branching at all** — no `if (customerType == …)`. Adding a new pricing rule means adding a class and changing one line at the composition root, not editing a method that is already 40 lines long.

### Example 3 — Decorator: composing behaviour at runtime

```java
import java.util.LinkedHashMap;
import java.util.Map;

public class DecoratorDemo {

    interface ReportRenderer {
        String render(String id);
    }

    static final class DbRenderer implements ReportRenderer {
        @Override public String render(String id) { return "DB(" + id + ")"; }
    }

    /** Same type as what it decorates → they stack. */
    static final class CachingRenderer implements ReportRenderer {
        private final ReportRenderer delegate;
        private final Map<String, String> cache = new LinkedHashMap<>();
        CachingRenderer(ReportRenderer delegate) { this.delegate = delegate; }
        @Override public String render(String id) {
            return cache.computeIfAbsent(id, delegate::render);
        }
    }

    /** Another decorator of the SAME type. */
    static final class LoggingRenderer implements ReportRenderer {
        private final ReportRenderer delegate;
        LoggingRenderer(ReportRenderer delegate) { this.delegate = delegate; }
        @Override public String render(String id) {
            long t0 = System.nanoTime();
            String result = delegate.render(id);
            System.out.println("  [log] render(" + id + ") took "
                    + (System.nanoTime() - t0) / 1000 + "µs");
            return result;
        }
    }

    public static void main(String[] args) {
        ReportRenderer plain = new DbRenderer();
        System.out.println("plain   : " + plain.render("R1"));
        System.out.println("plain   : " + plain.render("R1"));

        ReportRenderer cached = new CachingRenderer(new DbRenderer());
        System.out.println("cached  : " + cached.render("R1"));
        System.out.println("cached  : " + cached.render("R1") + "  (from cache)");

        // STACKED decorators — all the same type
        ReportRenderer full = new LoggingRenderer(new CachingRenderer(new DbRenderer()));
        System.out.println("stacked : " + full.render("R2"));
        System.out.println("stacked : " + full.render("R2") + "  (cached, still logged)");
    }
}
```

**Actual output:**

```text
plain   : DB(R1)
plain   : DB(R1)
cached  : DB(R1)
cached  : DB(R1)  (from cache)
  [log] render(R2) took 12µs
stacked : DB(R2)
  [log] render(R2) took 3µs
stacked : DB(R2)  (cached, still logged)
```

**Takeaway:** the order of the decorators **is** the order of execution. `Logging(Caching(Db))` logs *every* call including cache hits. `Caching(Logging(Db))` would log only the misses. That ordering dependency is the decorator pattern's main cost — and it is a favourite interview follow-up.

### Example 4 — Observer: adding a subscriber without touching the publisher

```java
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ObserverDemo {

    static final class OrderService {
        private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

        void subscribe(Consumer<String> listener) { listeners.add(listener); }
        void unsubscribe(Consumer<String> listener) { listeners.remove(listener); }

        void publish(String orderId) {
            for (Consumer<String> listener : listeners) {
                try {
                    listener.accept(orderId);
                } catch (RuntimeException e) {
                    System.out.println("  [error] a listener failed: " + e.getMessage()
                            + " — the others still run");
                }
            }
        }
    }

    public static void main(String[] args) {
        OrderService service = new OrderService();

        service.subscribe(id -> System.out.println("  [email] confirmation for " + id));
        service.subscribe(id -> System.out.println("  [sms]    notification for " + id));
        service.subscribe(id -> { throw new IllegalStateException("sms gateway down"); });
        service.subscribe(id -> System.out.println("  [audit]  recorded " + id));

        System.out.println("publishing ORD-1:");
        service.publish("ORD-1");
    }
}
```

**Actual output:**

```text
publishing ORD-1:
  [email] confirmation for ORD-1
  [sms]    notification for ORD-1
  [error] a listener failed: sms gateway down — the others still run
  [audit]  recorded ORD-1
```

**Takeaway:** the publisher never mentions email, SMS or audit. Adding a channel is a one-line change at the composition root. And the failure isolation means one broken subscriber does not stop the others — a production requirement the naive implementation misses.

### Example 5 — Template Method: a fixed skeleton with variable steps

```java
public class TemplateMethodDemo {

    abstract static class DataProcessor {

        /** The TEMPLATE — final, so the algorithm's structure is invariant. */
        public final void run() {
            System.out.println("  [template] start");
            read();
            transform();
            if (shouldWrite()) {
                write();
            } else {
                System.out.println("  [template] write skipped");
            }
            cleanup();
            System.out.println("  [template] done");
        }

        protected abstract void read();
        protected abstract void transform();

        /** A HOOK — the subclass may opt in. */
        protected boolean shouldWrite() { return true; }
        protected void write() { System.out.println("  [write] default no-op"); }
        protected void cleanup() { System.out.println("  [cleanup] default no-op"); }
    }

    static final class CsvProcessor extends DataProcessor {
        @Override protected void read() { System.out.println("  [read] csv"); }
        @Override protected void transform() { System.out.println("  [transform] csv → rows"); }
        @Override protected void write() { System.out.println("  [write] csv out"); }
    }

    static final class JsonProcessor extends DataProcessor {
        @Override protected void read() { System.out.println("  [read] json"); }
        @Override protected void transform() { System.out.println("  [transform] json → rows"); }
        @Override protected boolean shouldWrite() { return false; }   // opt out of the write step
    }

    public static void main(String[] args) {
        System.out.println("CSV:");
        new CsvProcessor().run();
        System.out.println("JSON:");
        new JsonProcessor().run();
    }
}
```

**Actual output:**

```text
CSV:
  [template] start
  [read] csv
  [transform] csv → rows
  [write] csv out
  [cleanup] default no-op
  [template] done
JSON:
  [template] start
  [read] json
  [transform] json → rows
  [template] write skipped
  [cleanup] default no-op
  [template] done
```

**Takeaway:** the *order* of the steps is guaranteed by the `final` method; the *content* of the steps varies. The `shouldWrite()` hook lets a subclass opt out of a step without overriding the whole algorithm.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A complete Strategy-based payment system with a registry

```java
package com.example.payment;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Payment processing with pluggable strategies, resolved at runtime.
 *
 * <p>Design notes worth defending in an interview:
 * <ul>
 *   <li>The <b>Strategy</b> is chosen by the client, not by a switch inside
 *       the service — the service has NO branching on the payment type.</li>
 *   <li>The <b>Factory/Registry</b> is a simple map lookup, not a class
 *       hierarchy — the "simple factory" is usually the right amount of
 *       Factory Method.</li>
 *   <li>The strategy is <b>stateless</b>, so a single instance per type is
 *       shared safely.</li>
 * </ul>
 */
public final class PaymentService {

    public enum PaymentType { CARD, WALLET, BANK_TRANSFER, COD }

    public interface PaymentStrategy {
        boolean supports(PaymentType type);
        PaymentResult pay(PaymentRequest request);
    }

    private final Map<PaymentType, PaymentStrategy> strategies = new EnumMap<>(PaymentType.class);

    public PaymentService(PaymentStrategy... registered) {
        for (PaymentStrategy strategy : registered) {
            for (PaymentType type : PaymentType.values()) {
                if (strategy.supports(type)) {
                    strategies.put(type, strategy);
                }
            }
        }
        // fail fast at startup rather than at payment time
        for (PaymentType type : PaymentType.values()) {
            if (!strategies.containsKey(type)) {
                throw new IllegalStateException("No payment strategy registered for " + type);
            }
        }
    }

    public PaymentResult pay(PaymentRequest request) {
        PaymentStrategy strategy = strategies.get(request.type());
        if (strategy == null) {
            return PaymentResult.failure("Unsupported payment type: " + request.type());
        }
        return strategy.pay(request);
    }

    public record PaymentRequest(PaymentType type, String accountId, BigDecimal amount, String reference) {
        public PaymentRequest {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be > 0");
        }
    }

    public record PaymentResult(boolean success, String transactionId, String message) {
        public static PaymentResult success(String txId) { return new PaymentResult(true, txId, "OK"); }
        public static PaymentResult failure(String msg)  { return new PaymentResult(false, null, msg); }
        public Optional<String> transactionId() { return Optional.ofNullable(transactionId); }
    }
}
```

### 6.2 A Builder with validation and a copy-on-write semantics

```java
package com.example.builder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An immutable HTTP request built with a Builder.
 *
 * <p>Shows the three things a Builder buys you over a constructor:
 * <ol>
 *   <li><b>Immutability</b> — all fields are final, set once.</li>
 *   <li><b>Validation at the right moment</b> — required fields are checked
 *       in the constructor, cross-field rules in build().</li>
 *   <li><b>Defaulting</b> — optional fields get sensible defaults.</li>
 * </ol>
 */
public final class HttpRequest {

    private final String method;
    private final String url;
    private final Map<String, String> headers;
    private final byte[] body;
    private final LocalDateTime createdAt;
    private final int timeoutMillis;

    private HttpRequest(Builder b) {
        this.method = Objects.requireNonNull(b.method, "method is required");
        this.url = Objects.requireNonNull(b.url, "url is required");
        this.headers = Map.copyOf(b.headers);
        this.body = b.body == null ? null : b.body.clone();
        this.createdAt = b.createdAt;
        this.timeoutMillis = b.timeoutMillis;
    }

    public static Builder builder() { return new Builder(); }

    public String method()      { return method; }
    public String url()         { return url; }
    public Map<String, String> headers() { return headers; }
    public int timeoutMillis()  { return timeoutMillis; }
    public LocalDateTime createdAt() { return createdAt; }

    public static final class Builder {
        private String method;
        private String url;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private byte[] body;
        private LocalDateTime createdAt = LocalDateTime.now();
        private int timeoutMillis = 30_000;

        public Builder method(String method) {
            this.method = method == null ? null : method.toUpperCase(Locale.ROOT);
            return this;
        }

        public Builder url(String url) { this.url = url; return this; }

        public Builder header(String name, String value) {
            headers.put(Objects.requireNonNull(name), value);
            return this;
        }

        public Builder body(byte[] body) { this.body = body == null ? null : body.clone(); return this; }

        public Builder timeout(int millis) {
            if (millis <= 0) throw new IllegalArgumentException("timeout must be > 0");
            this.timeoutMillis = millis;
            return this;
        }

        public HttpRequest build() {
            if (method == null || method.isBlank()) {
                throw new IllegalStateException("method is required");
            }
            if (url == null || !url.startsWith("http")) {
                throw new IllegalStateException("url must be an absolute http(s) URL");
            }
            if (body != null && ("GET".equals(method) || "HEAD".equals(method))) {
                throw new IllegalStateException("A " + method + " request must not have a body");
            }
            return new HttpRequest(this);
        }
    }
}
```

### 6.3 A thread-safe Observer with lifecycle management

```java
package com.example.events;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A production-grade event bus with subscription handles and automatic
 * cleanup — solving the two classic Observer leaks:
 *   1. a listener that is never unsubscribed keeps its subject alive;
 *   2. a listener that throws breaks the notification loop.
 *
 * @param <E> the event type
 */
public final class EventBus<E> implements AutoCloseable {

    private final Map<Long, Consumer<E>> listeners = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Long> order = new CopyOnWriteArrayList<>();
    private final AtomicLongSequence ids = new AtomicLongSequence();
    private volatile boolean closed;

    /** A handle you can use to unsubscribe later. */
    public record Subscription(long id) implements AutoCloseable {
        @Override public void close() { /* handled by the bus */ }
    }

    public Subscription subscribe(Consumer<E> listener) {
        if (closed) throw new IllegalStateException("EventBus is closed");
        long id = ids.next();
        listeners.put(id, listener);
        order.add(id);
        return new Subscription(id);
    }

    public void unsubscribe(Subscription subscription) {
        listeners.remove(subscription.id());
        order.remove(subscription.id());
    }

    public void publish(E event) {
        if (closed) return;
        for (Long id : order) {
            Consumer<E> listener = listeners.get(id);
            if (listener == null) continue;            // unsubscribed concurrently
            try {
                listener.accept(event);
            } catch (RuntimeException ex) {
                System.err.println("Listener " + id + " failed: " + ex);
                // isolate the failure — one bad listener must not stop the rest
            }
        }
    }

    public int listenerCount() { return listeners.size(); }

    @Override public void close() {
        closed = true;
        listeners.clear();
        order.clear();
    }

    /** A tiny counter — avoids a dependency on AtomicLong's boxing overhead. */
    private static final class AtomicLongSequence {
        private final java.util.concurrent.atomic.AtomicLong value =
                new java.util.concurrent.atomic.AtomicLong();
        long next() { return value.getAndIncrement(); }
    }
}
```

> 💡 **Why `CopyOnWriteArrayList` for the order and `ConcurrentHashMap` for the map?**
> The list gives a **stable, insertion-ordered** snapshot for iteration, and the
> map allows O(1) removal by id. The combination handles the "unsubscribe during
> iteration" case safely: the `listeners.get(id) == null` check skips a listener
> that was removed after the snapshot was taken.

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Using `new` inside a class that should be decoupled

```java
// ❌ The service now depends on the concrete gateway
public class OrderService {
    private final StripeGateway gateway = new StripeGateway();   // 💥 untestable, un-swappable
}
```

**✅ FIX** — constructor injection, so the dependency is explicit and substitutable.

---

### ❌ Pitfall 2 — "Applying a pattern" where no force exists

```java
// ❌ A whole class hierarchy to avoid ONE switch statement
abstract class Pricer { abstract BigDecimal price(Order o); }
class GoldPricer extends Pricer { ... }
class SilverPricer extends Pricer { ... }
// for 3 fixed cases, a switch is simpler and easier to read
```

**✅ FIX** — apply the *rule of three*: wait until the third implementation appears before generalising.

---

### ❌ Pitfall 3 — Singleton with a `public` constructor

```java
// ❌ the constructor must be private, or the singleton is not a singleton
public class Config { public Config() {} }
```

**✅ FIX** — `private` constructor (or an `enum`).

---

### ❌ Pitfall 4 — Double-checked locking without `volatile`

```java
private static Config instance;                       // ❌ BROKEN
public static Config getInstance() {
    if (instance == null) { synchronized (Config.class) { if (instance == null) instance = new Config(); } }
    return instance;
}
```

**✅ FIX** — `private static volatile Config instance;` (or use the lazy-holder idiom, or an `enum`).

---

### ❌ Pitfall 5 — A Builder that allows an invalid object

```java
public Order build() { return new Order(this); }   // ❌ no validation → an Order with no items
```

**✅ FIX** — validate in `build()` (cross-field rules) and in the constructor (required fields).

---

### ❌ Pitfall 6 — A mutable object returned from a "build"

```java
// ❌ the builder's list leaks into the built object
public Order build() { return new Order(items); }   // items is the builder's own List
```

**✅ FIX** — `List.copyOf(items)` in the constructor (defensive copy).

---

### ❌ Pitfall 7 — A Decorator that does not implement the same interface

```java
// ❌ now the client cannot swap the decorated object for the original
public class CachingRenderer { ... }   // does NOT implement ReportRenderer
```

**✅ FIX** — a Decorator must implement the same interface so the types are interchangeable.

---

### ❌ Pitfall 8 — Forgetting the `InvocationTargetException` unwrap in a proxy

```java
try {
    return method.invoke(target, args);
} catch (InvocationTargetException e) {
    throw e;      // ❌ the caller sees InvocationTargetException, not the real cause
}
```

**✅ FIX** — `throw e.getCause();`

---

### ❌ Pitfall 9 — An Observer that lets one listener break the others

```java
for (Listener l : listeners) {
    l.onEvent(event);     // ❌ one exception stops every subsequent listener
}
```

**✅ FIX** — wrap each call in a `try`/`catch` and log the failure.

---

### ❌ Pitfall 10 — An Observer that never unsubscribes (the memory leak)

```java
service.subscribe(this);   // ❌ a long-lived subject keeps the listener alive
```

**✅ FIX** — return a `Subscription` handle, and close it (or use `WeakReference` for a short-lived listener).

---

### ❌ Pitfall 11 — A `Comparator` in a `TreeSet` that is inconsistent with `equals`

```java
// ❌ silently DROPS elements whose compare() returns 0 but whose equals() returns false
TreeSet<String> s = new TreeSet<>(Comparator.comparingInt(String::length));
```

**✅ FIX** — add a tie-breaker that falls back to `equals`.

---

### ❌ Pitfall 12 — A State pattern for 3 states

```java
// ❌ 3 classes + a factory for what a switch would do in 6 lines
```

**✅ FIX** — use an `enum` with a `switch` until the state machine genuinely grows.

---

### ❌ Pitfall 13 — A Flyweight with mutable shared state

```java
// ❌ if the shared intrinsic state is mutable, one caller corrupts it for everyone
```

**✅ FIX** — the shared state must be **immutable** (`final`). If it must change, it is not intrinsic state.

---

### ❌ Pitfall 14 — A Visitor on types that change often

```java
// ❌ every new element type = every visitor must change
```

**✅ FIX** — Visitor is for *stable* element hierarchies. If the elements change, use a `switch` over the type instead.

---

### ❌ Pitfall 15 — A Facade that becomes a "god object"

```java
// ❌ the facade does EVERYTHING — it just moved the mess
```

**✅ FIX** — a facade should expose a *cohesive* set of operations and delegate the rest.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What are the Gang of Four design patterns?**
**A:** Twenty-three patterns published by Erich Gamma, Richard Helm, Ralph Johnson and John Vlissides in *Design Patterns* (1994), in three families: **Creational** (5 — Factory Method, Abstract Factory, Builder, Prototype, Singleton), **Structural** (7 — Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy), and **Behavioural** (11 — Chain of Responsibility, Command, Interpreter, Iterator, Mediator, Memento, Observer, State, Strategy, Template Method, Visitor).

**Q: What problem do design patterns solve?**
**A:** They give **named, well-understood solutions to recurring design forces**, with a documented structure and — importantly — a documented cost. Their real value is as a *shared vocabulary*: "this smells like a Strategy" is a far more actionable review comment than "this method is too long".

**Q: What is the Singleton pattern, and how do you implement it in Java?**
**A:** It ensures a class has exactly one instance and provides a global access point. The best implementation in Java is an **enum**: `public enum Config { INSTANCE; /* ... */ }`. It is thread-safe by the class-initialisation guarantee, cannot be instantiated reflectively, and deserialisation returns the canonical instance.

**Q: What is the Factory Method pattern?**
**A:** It defines an interface for creating an object, but lets the *subclass* decide which concrete class to instantiate. The classic use is when a class cannot decide its own dependencies — e.g. `Calendar.getInstance()` chooses a locale-specific implementation.

**Q: What is the Strategy pattern?**
**A:** It defines a family of algorithms, encapsulates each one, and makes them interchangeable — so the algorithm varies independently of the client. The canonical Java example is `Comparator`: `Collections.sort(list, byLength)` swaps the algorithm without touching the client.

**Q: What is the Observer pattern?**
**A:** It defines a one-to-many dependency so that when one object changes state, all its dependents are notified automatically. The subject keeps a list of listeners and calls `notify()` on a change. Examples: Spring's `ApplicationEventPublisher`, Kafka listeners, the (now deprecated) `java.util.Observable`.

### 🟡 Intermediate

**Q: When would you use Abstract Factory rather than Factory Method?**
**A:** Abstract Factory creates a **family** of related objects and guarantees they are designed to work together (a `WinFactory` never gives you a `MacButton` with a `WinCheckbox`). Factory Method creates **one** product and lets a subclass decide which. Use Abstract Factory when you have multiple *product lines* and need compatibility within a line; use Factory Method when there is a single product dimension.

**Q: What is the difference between Adapter and Decorator?**
**A:** An **Adapter** converts an *incompatible* interface into the one the client expects — it is about *compatibility* between two things that existed independently. A **Decorator** implements the *same* interface as the object it wraps and *adds* behaviour. A `FileInputStream` wrapped in a `BufferedInputStream` is a decorator (same type, more behaviour); an `InputStreamReader` wrapping an `InputStream` is an adapter (byte → char conversion).

**Q: What is the difference between Decorator and Proxy?**
**A:** A **Decorator** has the *same type* as the thing it decorates and exists to *add behaviour*; it composes with other decorators of the same type. A **Proxy** implements *an interface the client uses* to access a *different* real subject, and exists to *control access* (auth, lazy creation, remote, caching). The practical tell: if you would swap the proxy for the real object and the code still compiles, it is a proxy; if the decorator adds a capability, it is a decorator.

**Q: Why does double-checked locking require `volatile`?**
**A:** Because without a `volatile` write there is no happens-before edge between the constructor's writes to the object's fields and another thread's reads of it. The JMM permits the write to `instance` to be **reordered before** the constructor's field writes, so a second thread can observe a non-null `instance` pointing at a **partially constructed** object. `volatile` makes the write a release and the read an acquire, which forbids that reordering. (Before Java 5, DCL was simply broken; the JSR-133 memory-model fix is what made `volatile` necessary.)

**Q: What is the difference between Strategy and State?**
**A:** In **Strategy**, the *client* chooses the algorithm and injects it — "how should I do this?". In **State**, the object chooses its own behaviour based on its internal state — "what should I do in THIS state?". The state changes by itself; the strategy is fixed from outside.

**Q: What is the difference between Observer and Publish/Subscribe?**
**A:** **Observer** is in-process and the subject holds the list of observers directly — the subject *knows* its observers, and a failing observer can affect the others. **Pub/Sub** inserts a **broker** between publisher and subscriber, so the publisher does not know the subscribers, the coupling is looser, and it can span processes (Kafka, RabbitMQ). Pub/Sub is essentially Observer with a mediator in the middle.

**Q: What is the cost of the Flyweight pattern?**
**A:** Two costs. First, the shared state must be **immutable** — if it is not, one caller can corrupt it for everyone. Second, the *extrinsic* state must be passed in to every operation, which makes the API more verbose and can hurt locality. The JDK's `String` pool is the canonical example: the `char[]` is shared, and the "extrinsic" state is the reference itself.

**Q: When is the Builder pattern genuinely justified?**
**A:** When the object has **4+ optional parameters** (the telescoping-constructor smell), when it must be **immutable**, or when construction involves **validation or derived fields**. For a simple data carrier with no rules, a Java 16 `record` is strictly better — it gives immutability, `equals`/`hashCode`/`toString` and no builder ceremony.

### 🔴 Advanced / Senior

**Q: You said a `record` can replace a Builder. When is the Builder still the right choice?**
**A:** When construction has **validation that depends on multiple fields together** (a `record`'s compact constructor can do this, but it cannot express *optional* fields or *conditional* construction), when there are **optional** parameters that should default, or when the object is built **incrementally** from several sources. A `record` also cannot be subclassed and always has all components — so if you need an inheritance hierarchy or optional fields, the Builder remains the right tool.

**Q: Explain the "state pattern explosion" and when to prefer an enum.**
**A:** The State pattern creates a class per state plus a transition mechanism. For 3–5 states with simple behaviour, an `enum` with a `switch` is 6 lines instead of 6 classes plus a factory. The State pattern earns its cost when the states have **substantial, distinct behaviour** or when the transition rules are complex enough that a `switch` becomes the thing everyone is afraid to touch. The smell to watch for: a `State` class per state that contains a single `switch`-worth of logic.

**Q: What is double dispatch, and what does it cost in the Visitor pattern?**
**A:** Double dispatch is two consecutive virtual calls: `element.accept(visitor)` dispatches on the *element's* runtime type, then inside, `visitor.visit(element)` dispatches on the *visitor's* runtime type. Java has single dispatch only, so this is how Visitor adds a second dimension. The cost is one extra virtual call per visit plus the "visitable pollution" — every element class must carry an `accept` method that is meaningless to it. Modern Java's `instanceof` pattern matching plus `switch` on the sealed type achieves the same thing with less ceremony, which is why Visitor is less common than it was in 2005.

**Q: How would you implement a retry-with-backoff using Command?**
**A:** A `Command` (`Runnable`-like) that captures the operation, plus a `RetryPolicy` that owns the loop. The Command gives you the *ability to re-execute* the same request; the policy decides *how many times and how long to wait*. Keeping the two separate is the point of the pattern: the command stays a pure "what to do", the policy is a pure "when to do it". In production, add a `CircuitBreaker` decorator around the policy.

**Q: In Spring, where do you see each pattern?**
**A:** **Factory** — `BeanFactory` / `FactoryBean` creates objects. **Proxy** — Spring AOP uses JDK proxies (interfaces) or CGLIB (classes) for `@Transactional`, `@Cacheable`, `@Async`. **Template Method** — `AbstractApplicationContext.refresh()` defines the startup sequence; `JdbcTemplate` defines the JDBC workflow. **Strategy** — `DispatcherServlet` resolves a `HandlerMapping`/`HandlerAdapter` strategy; `@Qualifier` selects a bean implementing a strategy interface. **Observer** — `ApplicationEventPublisher`/`ApplicationListener`. **Adapter** — `HandlerAdapter` adapts any handler to the servlet contract. **Facade** — `JdbcTemplate` over the raw JDBC API. **Decorator** — `TransactionAwareCacheDecorator`. **Adapter/Proxy** — Spring's `@Configuration` proxying to intercept `@Bean` calls.

**Q: Explain the "principle of least knowledge" (Law of Demeter) and how it relates to the Facade pattern.**
**A:** The Law of Demeter says a method may only call methods on: itself, its parameters, objects it creates, its direct fields, and its global objects — not on objects *returned by* other calls. `order.getCustomer().getAddress().getCity()` violates it: the method reaches three levels deep. A **Facade** is the pattern-level answer — it gives you one object to talk to, which is the "global object" the law allows. The two are different lenses on the same pressure: the Law of Demeter is a *rule*, the Facade is a *technique*.

### ⚫ Expert / Principal

**Q: "Singletons are an anti-pattern." Defend or refute this, with specifics.**
**A:** **Refute the blanket claim, but accept the specific criticisms.** The pattern's *intent* — one instance, one global access point — is legitimate for genuinely global, stateless infrastructure: a `ServiceLoader`, a metrics registry, a `Clock`. The criticisms are about the *implementation choices* that usually accompany it: (1) **hidden dependency** — a class that calls `Config.INSTANCE` internally has a dependency that does not appear in its constructor, so you cannot substitute it in a test; (2) **global mutable state** — a mutable singleton is shared mutable state, and in tests it leaks between test methods; (3) **the "exactly one" guarantee is not a business requirement** — in a microservice deployment there are N JVMs, so "the" config is N copies that can drift, and the correct answer is a config *server*; (4) **testability** — a singleton forces global state manipulation in tests. The modern resolution is **dependency injection with a scoped lifetime**: Spring's `@Service` with `singleton` scope gives you exactly one instance per container, but the dependency is *explicit in the constructor*, so it is substitutable. That is the Singleton's intent, achieved without the global access point. So: the *pattern* is fine; the *global static accessor* is the anti-pattern.

**Q: How would you introduce patterns into a legacy codebase without a big-bang rewrite?**
**A:** Incrementally, at **seams** rather than everywhere. (1) Start with the highest-value, lowest-risk seam — usually a **Strategy** at an existing `switch`, because it changes no public API: introduce the interface, implement it with the old code as the first strategy, then move call sites one at a time. (2) Use **Adapter** to wrap legacy components behind a new interface so new code depends on the new contract and old code stays untouched — the adapter is a strangler-fig seam. (3) Introduce **Facade** at the module boundary so callers stop reaching into internals. (4) Add tests *before* each refactor so the refactor is behaviour-preserving and verified. (5) Do **not** introduce a pattern where there is no force — a reviewer who sees `Strategy` used for a 2-case `if` will rightly object. The key discipline: a pattern introduction should be justified by a *named force* you can point to in the code, not by a desire to "apply patterns".

**Q: Compare the cost model of the four "composite" patterns — Composite, Decorator, Proxy and Facade — under a call hot path.**
**A:** All four add a virtual call, but they differ in *how many* and *what they add*. **Composite** adds one call per level of the tree, so a traversal of depth *d* costs O(*d*) calls per element — and it is *not* a leaf/branch branch, so the client code is uniform. **Decorator** adds one call per decorator layer, and because decorators compose, the cost is the *sum* of the layers — a chain of 5 decorators is 5× the dispatch. **Proxy** adds one call plus whatever the interceptor does (logging, authorisation, transaction management) — and because the proxy is a *substitute*, the call count is the same as without it, plus the interceptor's work. **Facade** adds one call at the boundary but *collapses* many calls inside, so it is usually a **net win** in a hot path — it is the only one of the four that reduces, rather than adds, work. The engineering conclusion: a Facade is safe to add on a hot path; a Decorator chain should be kept short; a Proxy's interceptor must be cheap on a hot path; a Composite's depth is the cost driver.

**Q: When would you use a `sealed` hierarchy (Java 17) instead of the GoF patterns?**
**A:** Modern Java has absorbed several patterns into the language. A **sealed interface with a `switch` over the permitted subtypes** replaces the *Visitor* (the compiler checks exhaustiveness, and `case Circle c ->` gives you the binding, so no `accept`/`visit` boilerplate and no double dispatch). A **sealed interface** with a per-implementation body replaces the *enum-with-behaviour* (the classic "each constant has its own algorithm" case), and pattern matching in `switch` replaces the *State* pattern for simple state machines. The GoF patterns remain valuable where the *set of operations* must vary independently of the *set of types* (Visitor), or where the pattern is about *runtime composition* rather than static dispatch (Decorator, Proxy). The honest modern advice: reach for the language feature first, and use the pattern when the language cannot express the force.

---

## 9. Counter-Questions & Traps

### CQ1. "Isn't a `switch` over an enum simpler than the Strategy pattern? Why add a class per algorithm?"

- **Naive:** "Because the Strategy pattern is better design."  ← *abstract and unconvincing*
- **The trap:** A `switch` over an enum is **simpler, and that is often the right answer**. The Strategy pattern's value is *not* "better than a switch" — it is **open for extension without modifying the central class**. The moment the number of cases is fixed and known, the switch wins on simplicity, and the interviewer is testing whether you know that.
- **Expert answer:** The decision hinges on **who changes and how often**. If the pricing rules are fixed by the business and change a few times a year, and there are 3 of them, an `enum` + `switch` is one method and zero extra classes. If new rules appear regularly, are added by *other teams*, or must be selected by configuration at runtime, the Strategy's extension point pays for itself. The discipline is the **rule of three** — do not generalise before the third implementation appears. And the cost to name honestly: a Strategy introduces a class per algorithm *plus* a selection point, and it can hide the fact that someone still has to decide *which* strategy to inject.

### CQ2. "You said the `enum` singleton is the best. But an enum can't be configured at construction time. Doesn't that make it unusable?"

- **Naive:** "Enums are the best because they're thread-safe."  ← *true, but incomplete*
- **The trap:** The `enum` singleton's real limitation is that **its constructor runs at class-initialisation time**, so it cannot take constructor arguments. That is a genuine constraint — you cannot write `enum Config { INSTANCE; Config(String url) {...} }` with a parameterised constructor and then pass the URL. So "always use an enum" is wrong for a singleton that needs runtime configuration.
- **Expert answer:** The right answer is to **separate the concerns**: the enum is a *holder of the single instance*; the instance's *configuration* is injected into it after construction (via a setter or an `initialise(...)` method) or read from a configuration source it loads itself. The cleanest production shape is: `enum AppConfig { INSTANCE; private final Properties props = load(); }` — the enum guarantees uniqueness and thread-safety, and the *contents* come from a file, an environment variable, or a config server, not from a constructor argument. If you genuinely need to construct with arguments, use the **lazy-holder idiom** (which is also thread-safe) rather than DCL.

### CQ3. "Doesn't the Observer pattern cause memory leaks? How do you prevent that?"

- **Naive:** "You call `removeListener` in the destructor."  ← *Java has no destructors — this is the trap*
- **The trap:** There is **no destructor in Java** (and `finalize()` was deprecated in Java 9 and removed in Java 18). So "unsubscribe in the destructor" is not an option. The real problems are: (1) the subject holds a **strong reference** to the listener, so a long-lived subject keeps a short-lived listener alive; (2) if the listener is an `Activity`/`Request`/`Session` and the subject is a singleton, you have a classic leak; (3) notification is *synchronous*, so a slow listener blocks the publisher.
- **Expert answer:** Four real mitigations, in increasing order of sophistication: (1) **explicit unsubscription** with a returned `Subscription` handle, closed in a `try`-with-resources or a `finally` — the simplest and most common; (2) **`WeakReference` listeners** — the subject holds the listener weakly, so the GC can reclaim it; the subject must then handle the "listener was collected" case; (3) an **asynchronous, bounded queue** between the subject and the listeners, with a per-listener worker and a **drop policy** — this also fixes the "slow listener blocks the publisher" problem; (4) use a **framework** that manages the lifecycle (Spring's `ApplicationListener` with a scoped bean, Guava's `EventBus` with a `Subscriber` registry). The key insight: the leak is a *lifetime mismatch*, and the fix is to make the subscription's lifetime explicit.

### CQ4. "If a `sealed` interface with pattern matching can replace Visitor, is Visitor obsolete?"

- **Naive:** "Yes, sealed classes and pattern matching replaced Visitor."  ← *partly true*
- **The trap:** Pattern matching over a sealed hierarchy gives you the same *effect* as Visitor — a new operation over a fixed set of types — but it works **statically**, at the call site, not by adding a method to the element class. So it removes Visitor's two real costs: the `accept()` pollution and the double dispatch. What it does **not** remove is Visitor's *strength*: the operation can be **added without touching the element classes**, and it can live in a completely separate module. With pattern matching, adding a new operation still means editing every `switch` that handles that hierarchy.
- **Expert answer:** The distinction is **where the operation lives**. Pattern matching puts the operation at the *call site* — great for "handle this shape here", and the compiler checks exhaustiveness so adding a new subtype becomes a compile error in every `switch` (a genuine win). Visitor puts the operation in a *separate object* — great for "apply this analysis across the whole graph", especially when the analysis is complex and you want to unit-test it in isolation. Modern advice: use pattern matching for simple, local dispatch; use Visitor when the operation is a complex, reusable algorithm over a stable, closed hierarchy that you want to test as a unit. And note that Java 21's **record patterns** make sealed hierarchies even more ergonomic.

### CQ5. "Your Facade example orchestrates five services. Isn't that just a Service/Manager class — a god object?"

- **Naive:** "Yes, the Facade is a god object."  ← *true of a bad facade, false of a good one*
- **The trap:** The distinguishing property of a Facade is not "it calls many things" — it is that it **provides a simpler interface to a subsystem** and it does not expose the subsystem's internals. A god object is a class that *grows without cohesion* — it accumulates unrelated responsibilities. A good Facade is **narrowly scoped to one use case**.
- **Expert answer:** The test is **cohesion**: does the facade expose operations that a single client use case needs? "Place an order" is one cohesive operation that happens to involve five services — that is a Facade. "Place an order, and also manage users, and also generate reports, and also reconcile payments" is a god object. The practical discipline: a Facade should be *replaceable* — if you can delete it and have the client call the services directly with 6 lines, the Facade was not earning its keep. The reason to keep it is that the **ordering and error handling** is the real value: the facade guarantees that you check authentication *before* charging, and that you do not charge if inventory is unavailable.

### CQ6. "If `Proxy` and `Decorator` both wrap an object, what's the real difference in practice?"

- **Naive:** "Proxy controls access, Decorator adds behaviour."  ← *the textbook answer, but too thin*
- **The trap:** That distinction is real but it is a *description of intent*, not of mechanics. Mechanically, a JDK dynamic proxy and a hand-written decorator can look identical — both implement the same interface and hold a delegate. The difference that matters in production is **who controls the relationship and what the client believes**: with a Proxy, the client believes it is talking to the *real* service, and the proxy is *invisible and injected*; with a Decorator, the client *explicitly constructs* the decorated object and knows it is doing so.
- **Expert answer:** The practical test: **could you swap the decorator for the undecorated object and have the code still compile and behave correctly?** If yes, you could have a Proxy (which is exactly what Spring AOP does — it substitutes a proxy for your bean and the application code is unaware). If the *behaviour* would be missing — because the added behaviour is the point — then it is a Decorator and it must be constructed explicitly. The other real distinction: a Proxy is usually **generated** (bytecode or `java.lang.reflect.Proxy`) and therefore **invisible**, while a Decorator is usually **hand-written** and visible at the construction site. Spring uses proxies for its *cross-cutting* concerns (transactions, security) precisely because they must be invisible, and it uses decorators (like `TransactionAwareCacheDecorator`) when the wrapping must be explicit.

### CQ7. "You said the rule of three. But what if I *know* there will be 10 strategies? Is that a reason to use Strategy now?"

- **Naive:** "Yes, use Strategy since you know there will be many."  ← *the trap*
- **The trap:** "I know there will be many" is a *prediction*, and predictions are exactly what the rule of three protects you against. The failure mode of premature generalisation is not just extra classes — it is that the abstraction you design from a *prediction* is usually the wrong shape, and then every real implementation has to be distorted to fit it. The classic example: designing a `Shape` interface before you have the shapes, and then discovering that all the real shapes need different *data* (a `Circle` needs a radius, a `Polygon` needs a list of vertices) — the interface you designed cannot express it.
- **Expert answer:** The better answer to "I know there will be many" is: **design for the extension point without inventing the abstraction** — that is, keep the `switch` in one place (a single method), and when the second implementation arrives, *extract* the interface then. The cost of extracting later is one refactor, which your tests make safe. The cost of guessing now is an abstraction that must be *redesigned* later anyway, plus every implementor written against the guess. The related trap in interviews: a candidate who says "I would use Strategy because the requirements say we'll have many payment providers" has not distinguished between *knowing the shape of the variation* (you need Strategy) and *knowing the count* (you need a switch until then).

### CQ8. "If I already use Spring DI, do I need the Factory patterns at all?"

- **Naive:** "No, Spring replaces them."  ← *true for creation, false for the family guarantee*
- **The trap:** Spring's container *is* a factory — it removes the need for your own `new`-hiding factory, and it is a *simple* factory in the GoF sense. But **Abstract Factory** solves a problem Spring does not: Spring injects *one* bean of a type, and choosing *which family* to inject is a problem you still solve. In practice this shows up as a `Map<String, SomeType>` injected and then `map.get(kind)` — which is a **runtime registry**, functionally a "factory" that Spring populated for you.
- **Expert answer:** The precise answer: Spring replaces the **Factory Method** pattern (the container calls your constructor) and gives you a runtime *registry* for Abstract-Factory-like needs. But two things remain yours: (1) **the compatibility guarantee** — Spring will happily give you a `WindowsCheckbox` with a `MacButton` if you configure it that way; an Abstract Factory would make that impossible; (2) **the "which one" decision** — with a `Map<String, PaymentStrategy>` you get the registry but you still write `strategies.get(kind)`, which is a lookup, not a type-safe family. The modern, type-safe answer is a **sealed interface** with a `switch` — the compiler checks exhaustiveness — or a factory that returns the whole family. The lesson: DI removes *creation* concerns; it does not remove *coupling* concerns.

### CQ9. "You said `Collections.synchronizedMap` is a decorator. But it doesn't add behaviour — it adds *synchronisation*. Isn't that a Proxy?"

- **Naive:** "It's a Decorator because it wraps the map."  ← *wrapping is not the test*
- **The trap:** The defining test of a Decorator is that it implements the **same interface** as the wrapped object and is *substitutable* for it. `Collections.synchronizedMap` returns a `Map` — so it is substitutable. It is also a **view** over the backing map, not a copy. Whether it "adds behaviour" is a fair question, but the *type-level* relationship is the test. A Proxy, in contrast, substitutes for a *different* real subject and is usually invisible.
- **Expert answer:** The cleanest distinction is **type substitutability plus explicitness**: `synchronizedMap` returns something that *is* a `Map` and can be passed anywhere a `Map` is expected — substitutability, which is the Decorator's signature. It is constructed explicitly at the call site (`Collections.synchronizedMap(new HashMap<>())`), which is also Decorator-like. The honest caveat: it is a *degenerate* Decorator — the "added behaviour" is only thread-safety, and it is a **poor** one, because the *iterator* is not synchronised, so iteration still needs external synchronisation, and the compound operations are not atomic. That last point is what pushes production code to `ConcurrentHashMap` instead. The pattern label is less important than the lesson: *a wrapper that only partially fixes the problem is worse than no wrapper, because it gives false confidence.*

### CQ10. "What is the anti-pattern version of each pattern? When does a pattern become an anti-pattern?"

- **Naive:** "A pattern becomes an anti-pattern when you don't need it."  ← *true, but too general to be useful*
- **The trap:** That is the general answer, but the useful answer is per-pattern: each pattern has a *specific* degenerate form. Singleton becomes an anti-pattern as a *global mutable state holder*; Factory becomes an anti-pattern as a *one-method class that returns one thing*; Builder becomes an anti-pattern for a *3-field object*; Observer becomes an anti-pattern when the *notification order matters*; State becomes an anti-pattern as *one class per enum constant with a switch inside*; Decorator becomes an anti-pattern as a *5-layer chain nobody can reason about*; Strategy becomes an anti-pattern when *the call site still switches*; Facade becomes an anti-pattern as a *god object*.
- **Expert answer:** The general test for "is this pattern earning its keep" is: **would the code be simpler without it?** If removing the pattern removes a class and the code is still correct and clear, the pattern was decoration. The per-pattern anti-patterns share a shape: the pattern's *structure* is present but its *benefit* is not — you have the ceremony without the decoupling. The senior-level insight is that patterns are **costs you opt into**, so the review question is never "is this the right pattern?" but "is the cost of this pattern less than the cost of the coupling it removes?" Sometimes the answer is no, and the best code is the boring code.

---

## 10. Rapid-Fire MCQs

**Q1.** How many patterns are in the Gang of Four book?
- A) 15  B) 20  C) 23  D) 26

**Q2.** Which family does the *Builder* pattern belong to?
- A) Creational  B) Structural  C) Behavioural  D) Architectural

**Q3.** Which family does the *Proxy* pattern belong to?
- A) Creational  B) Structural  C) Behavioural  D) Concurrency

**Q4.** How many behavioural patterns are there?
- A) 7  B) 10  C) 11  D) 12

**Q5.** Which is the JDK's recommended way to implement a Singleton?
- A) A `static` field with a public constructor  B) An `enum`  C) Double-checked locking  D) A `ThreadLocal`

**Q6.** Why does double-checked locking need `volatile`?
- A) Performance  B) To prevent reordering that exposes a partially constructed object  C) To avoid deadlock  D) To make `instance` atomic

**Q7.** The `enum` Singleton resists which attack?
- A) Multi-threading only  B) Reflection and deserialisation  C) Serialization only  D) Class loading

**Q8.** `Abstract Factory` creates…
- A) One product  B) A family of related products  C) A prototype copy  D) A singleton

**Q9.** `Factory Method` differs from `Abstract Factory` because it…
- A) Creates a family of products  B) Creates one product, chosen by a subclass  C) Requires an abstract class  D) Is a structural pattern

**Q10.** A Decorator implements the **same** interface as…
- A) The client  B) The decorated object  C) The factory  D) The facade

**Q11.** A Proxy typically implements…
- A) A different interface than the real subject  B) The same interface as the real subject  C) No interface  D) Only `Object`

**Q12.** The classic example of a Decorator in the JDK is…
- A) `Collections.synchronizedList`  B) `BufferedInputStream`  C) `Arrays.asList`  D) `ThreadLocal`

**Q13.** The classic example of an Adapter in the JDK is…
- A) `BufferedInputStream`  B) `InputStreamReader`  C) `ArrayList`  D) `StringBuilder`

**Q14.** The `String` pool is an example of which pattern?
- A) Proxy  B) Flyweight  C) Singleton  D) Decorator

**Q15.** The `Integer` cache (−128..127) is an example of…
- A) Flyweight  B) Prototype  C) Factory  D) Memento

**Q16.** In the State pattern, who chooses the behaviour?
- A) The client  B) The object itself, based on its state  C) A factory  D) The strategy

**Q17.** In the Strategy pattern, who chooses the algorithm?
- A) The client  B) The object itself  C) The framework  D) A visitor

**Q18.** `Comparator` in the JDK is an example of…
- A) State  B) Strategy  C) Visitor  D) Mediator

**Q19.** The difference between Observer and Pub/Sub is…
- A) Pub/Sub is faster  B) Pub/Sub has a broker; Observer does not  C) Observer is deprecated  D) Pub/Sub is synchronous

**Q20.** The State pattern replaces a `switch` on state with…
- A) A map of lambdas  B) A class per state  C) A `null` check  D) A `Strategy` injection

**Q21.** The Template Method's algorithm skeleton is declared…
- A) `abstract`  B) `final`  C) `static`  D) `synchronized`

**Q22.** The "hook" methods in the Template Method pattern…
- A) Are `final`  B) Are overridable and may be skipped  C) Must be `private`  D) Are `static`

**Q23.** Visitor's "double dispatch" means…
- A) Two threads  B) Two consecutive virtual calls  C) Two interfaces  D) Two constructors

**Q24.** Which pattern adds a "placeholder that controls access to another object"?
- A) Decorator  B) Facade  C) Proxy  D) Adapter

**Q25.** Which pattern is *not* recommended as a `switch` replacement for 3 states?
- A) An `enum` with a `switch`  B) The State pattern  C) A `Map` of lambdas  D) A `sealed` interface with pattern matching

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **C** | 23 patterns: 5 creational, 7 structural, 11 behavioural. |
| 2 | **A** | Builder is creational — it is about *creating* a complex object. |
| 3 | **B** | Proxy is structural — it is about *composing* a substitute for an object. |
| 4 | **C** | 11 behavioural patterns. |
| 5 | **B** | An `enum` — thread-safe, reflection-proof and deserialisation-proof. |
| 6 | **B** | Without `volatile` there is no happens-before edge, so a partially constructed object can be observed. |
| 7 | **B** | The JVM refuses to instantiate an enum reflectively, and deserialisation returns the canonical constant. |
| 8 | **B** | Abstract Factory creates a *family* whose members are guaranteed to work together. |
| 9 | **B** | Factory Method creates *one* product; the subclass decides which. |
| 10 | **B** | A Decorator is substitutable for what it decorates — that is what lets them stack. |
| 11 | **B** | A Proxy implements the same interface but substitutes a *different* real subject. |
| 12 | **B** | `BufferedInputStream` wraps an `InputStream` — same type, more behaviour. |
| 13 | **B** | `InputStreamReader` converts a byte interface into a character interface. |
| 14 | **B** | The pool shares the expensive `byte[]`/`char[]` — the textbook Flyweight. |
| 15 | **A** | The cached `Integer` objects are shared instances — Flyweight. |
| 16 | **B** | The object swaps its own behaviour as its state changes. |
| 17 | **A** | The client injects the algorithm — that is the point of Strategy. |
| 18 | **B** | `Comparator` is an interchangeable algorithm injected by the caller. |
| 19 | **B** | Pub/Sub inserts a broker, so the publisher does not know the subscribers. |
| 20 | **B** | One class per state, with the object delegating to the current one. |
| 21 | **B** | `final` — the skeleton is invariant, which is the whole point. |
| 22 | **B** | Hooks are overridable and may be skipped, letting a subclass opt out of a step. |
| 23 | **B** | `element.accept(v)` then `v.visit(element)` — two virtual calls. |
| 24 | **C** | A Proxy is a placeholder that controls access to a real subject. |
| 25 | **B** | For 3 states the State pattern is 3 classes + a factory for what a `switch` does in 6 lines. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                            THE 23 PATTERNS                              │
├───────────────────────┬──────────────────────────────────────────────────┤
│ CREATIONAL (5)        │ Factory Method · Abstract Factory · Builder      │
│ "what to create"      │ Prototype · Singleton                            │
├───────────────────────┼──────────────────────────────────────────────────┤
│ STRUCTURAL (7)        │ Adapter · Bridge · Composite · Decorator         │
│ "how to compose"      │ Facade · Flyweight · Proxy                       │
├───────────────────────┼──────────────────────────────────────────────────┤
│ BEHAVIOURAL (11)      │ Chain of Responsibility · Command · Interpreter   │
│ "how to interact"     │ Iterator · Mediator · Memento · Observer         │
│                       │ State · Strategy · Template Method · Visitor     │
└───────────────────────┴──────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 4 THAT LOOK ALIKE — DISAMBIGUATE                 │
├──────────────┬───────────────────────┬───────────────────────────────────┤
│ ADAPTER      │ different interface   │ make incompatible things fit      │
│ DECORATOR    │ SAME interface        │ add behaviour, same type          │
│ PROXY        │ SAME interface        │ control access, substitute        │
│ FACADE       │ NEW simpler interface │ hide a subsystem                  │
├──────────────┴───────────────────────┴───────────────────────────────────┤
│ STATE  = the object picks (its state)    │ STRATEGY = the client picks   │
│ OBSERVER = subject notifies              │ PUB/SUB = a broker decouples  │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                         SINGLETON — THE 5 OPTIONS                        │
├──────────────────┬───────────┬───────┬────────────┬───────────────────────┤
│ Eager            │ ✅        │ ❌    │ ❌         │ simplest              │
│ Lazy-holder      │ ✅        │ ✅   │ ❌         │ ⭐ best non-enum      │
│ DCL + volatile   │ ✅        │ ✅   │ ❌         │ needs volatile        │
│ ENUM             │ ✅        │ ✅   │ ✅ ✅      │ ⭐ JDK's answer       │
│ Static holder    │ ✅        │ ✅   │ ❌         │ DI-friendly           │
├──────────────────┴───────────┴───────┴────────────┴───────────────────────┤
│ 💡 Better still: constructor injection + a container scope.               │
│    A singleton's *intent* is right; its *global accessor* is the smell.   │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                     "WHEN NOT TO USE IT" — THE REAL ANSWER               │
├──────────────────────────────────────────────────────────────────────────┤
│  Factory     → when a simple `new` in one place would do                │
│  Builder     → for a 3-field object, use a record or a constructor      │
│  Singleton   → for hidden global state; inject the dependency instead   │
│  Decorator   → when a 5-layer chain is harder than the original        │
│  Observer    → when you have 1–2 known listeners; just call them        │
│  State       → for 3 states, use an enum + a switch                     │
│  Strategy    → when the call site still switches anyway                │
│  Facade      → when the client could do it in 6 lines without it       │
│  Visitor     → when the element types change often                      │
│                                                                          │
│  THE TEST: would the code be SIMPLER without the pattern? If yes → skip. │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### Where the JDK itself uses each pattern

| Pattern | JDK usage |
|---|---|
| Factory Method | `Calendar.getInstance()`, `List.of()`, `NumberFormat.getInstance()` |
| Abstract Factory | `java.sql` driver/`DataSource` families; `javax.xml` parsers |
| Builder | `StringBuilder`, `Stream.Builder`, `HttpRequest.newBuilder()` |
| Prototype | `Object.clone()`, `ArrayList.clone()`, copy constructors |
| Singleton | `Runtime.getRuntime()`, `System.out`, `Locale.getDefault()`, `enum` singletons |
| Adapter | `InputStreamReader`, `Collections.asList`, Spring's `HandlerAdapter` |
| Bridge | `java.sql` (Driver ↔ Connection ↔ Statement), `ReadWriteLock` impls |
| Composite | `java.awt.Container`/`Component`, Spring's `CompositeCacheManager` |
| Decorator | `BufferedInputStream`, `Collections.synchronizedList`, `TransactionAwareCacheDecorator` |
| Facade | `java.net.URI`, Spring's `JdbcTemplate`, `Files` (NIO.2) |
| Flyweight | The `String` pool, the `Integer` cache, `Class` objects |
| Proxy | `java.lang.reflect.Proxy`, Spring AOP, Hibernate lazy loading, RMI stubs |
| Chain of Responsibility | `Servlet` filter chains, Spring's `HandlerInterceptor`, validation |
| Command | `Runnable`/`Callable`, Spring's `TransactionTemplate`, job queues |
| Interpreter | Drools, SpEL, JSqlParser, ANTLR-generated interpreters |
| Iterator | Every `java.util` collection; `Spliterator` is its sibling |
| Mediator | `java.lang.Thread` (mediates the OS thread and the `Runnable`) |
| Memento | Text-editor undo stacks, game saves, `Properties` snapshots |
| Observer | `ApplicationEventPublisher`, Kafka listeners, Guava `EventBus` |
| State | Thread state handling in the JDK, workflow engines, TCP state machines |
| Strategy | `Comparator`, Spring's `ResourceLoader`, `@Qualifier`-selected beans |
| Template Method | `AbstractApplicationContext.refresh()`, `HttpServlet.service()`, JUnit lifecycle |
| Visitor | `java.lang.Compiler`, `FileVisitor`, ANTLR's generated visitors |

### The Spring mapping (a very common interview question)

```text
   Spring concept                 → GoF pattern
   ─────────────────────────────     ──────────────────────────
   BeanFactory / FactoryBean      → Factory Method
   @Autowired constructor          → (not a pattern) DI
   @Transactional / @Cacheable      → Proxy (JDK or CGLIB)
   JdbcTemplate                    → Template Method + Facade
   AbstractApplicationContext      → Template Method
   DispatcherServlet                → Strategy (HandlerMapping/HandlerAdapter)
   ApplicationEventPublisher       → Observer
   @Configuration proxying          → Proxy
   ResourceLoader                   → Strategy
```

### Production cautions

| Pattern | Production caution |
|---|---|
| Singleton | Global mutable state breaks test isolation. Prefer DI with a scoped bean. |
| Observer | Unsubscribe or you leak. Slow listeners block the publisher — use an async, bounded queue. |
| Decorator | Layer order is behaviour. Keep the chain short and documented. |
| Proxy | Adds latency to every call. Never proxy a hot path without measuring. |
| Strategy | The selection point is still a decision — put it in one place (the composition root). |
| State | Class explosion. Use an `enum` until the behaviour is substantial. |
| Facade | Becomes a god object if it is not scoped to one use case. |
| Flyweight | Shared state **must** be immutable. A mutable intrinsic field is a corruption bug. |
| Builder | A mutable object in a supposedly immutable design — scope it to the construction. |
| Command | Undo requires the command to capture enough state — that is a real constraint. |

### Metrics and observability

```java
// Pattern usage worth measuring
cacheHits / cacheMisses          // Decorator / Proxy (caching)
listenerCount                      // Observer — a growing count is a leak
strategySelectionCounts            // Strategy — which algorithm is actually used?
facadeLatency                      // Facade — the orchestration cost
proxyOverheadRatio                 // Proxy — invocations vs real-subject invocations
```

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The principles patterns serve | [10 · SOLID Principles](../10-SOLID-Principles/01-SOLID-Principles.md) |
| Strategy in the JDK | `java.util.Comparator`, `java.util.stream` |
| Proxy & dynamic proxies in depth | [14 · Reflection & Annotations](../14-Reflection-Annotations/01-Reflection-and-Annotations.md) |
| Observer in Spring | `ApplicationEventPublisher` / `@EventListener` |
| The original source | Gamma, Helm, Johnson, Vlissides — *Design Patterns: Elements of Reusable Object-Oriented Software* (1994) |
| Modern replacements | Java 17 `sealed` + Java 21 pattern matching |

---

**Next Chapter:** → [10 · SOLID Principles](../10-SOLID-Principles/01-SOLID-Principles.md)
**Previous Chapter:** ← [08 · Multithreading & Concurrency](../08-Multithreading-Concurrency/README.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
