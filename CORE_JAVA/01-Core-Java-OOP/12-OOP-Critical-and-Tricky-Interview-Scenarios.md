## 📋 Table of Contents

- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — How to Think About Tricky OOP](#2-mental-model--how-to-think-about-tricky-oop)
- [3. Deep Dive — The Scenarios That Trip People Up](#3-deep-dive--the-scenarios-that-trip-people-up)
  - [3.1 Is Java 100% Object-Oriented?](#31-is-java-100-object-oriented)
  - [3.2 Polymorphism Without Inheritance](#32-polymorphism-without-inheritance)
  - [3.3 Null: The Billion-Dollar Mistake](#33-null-the-billion-dollar-mistake)
  - [3.4 The Problem with Getters and Setters](#34-the-problem-with-getters-and-setters)
  - [3.5 Singleton: Pattern or Anti-Pattern?](#35-singleton-pattern-or-anti-pattern)
  - [3.6 Object vs Class at Runtime](#36-object-vs-class-at-runtime)
  - [3.7 Anemic Domain Model](#37-anemic-domain-model)
  - [3.8 Early Binding vs Late Binding](#38-early-binding-vs-late-binding)
  - [3.9 Virtual Functions in Java](#39-virtual-functions-in-java)
  - [3.10 Destructor in Java](#310-destructor-in-java)
  - [3.11 Copy Constructor](#311-copy-constructor)
  - [3.12 Can Constructors Be Inherited?](#312-can-constructors-be-inherited)
  - [3.13 Can We Override Final Methods?](#313-can-we-override-final-methods)
  - [3.14 Scenario: Constructor Ordering and the "Calling an overridable method" Trap](#314-scenario-constructor-ordering-and-the-calling-an-overridable-method-trap)
  - [3.15 Scenario: The Integer Cache and Wrapper Identity](#315-scenario-the-integer-cache-and-wrapper-identity)
  - [3.16 Scenario: Method Resolution Order](#316-scenario-method-resolution-order)
  - [3.17 Scenario: Class Loading, Initialisation and the `static` Trap](#317-scenario-class-loading-initialisation-and-the-static-trap)
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

- **Java is NOT 100% OOP** — primitives, `static` members and `null` all break pure OOP. It is a
  *practically* object-oriented language, and the reason is **performance**, not laziness.
- **Polymorphism does not need inheritance** — interfaces give you polymorphism with *no* IS-A
  coupling. This is the single most important design lever in Java.
- **`null` is a type-system hole**, not a value. `Optional` (or a Null Object) closes it at the
  cost of an allocation.
- **Getters/setters are the enemy of encapsulation** — they let callers mutate state the class
  believes it owns. Prefer behaviour methods ("tell, don't ask").
- **`static` is not thread-safe and is not testable** — it hides dependencies. Prefer DI.
- **All non-`static`, non-`final`, non-`private` instance methods are virtual** in Java; the
  dispatch is a vtable lookup.
- **Java has no destructor** — `finalize()` is deprecated since 9 and removed for removal in 18.
  Use `try-with-resources` or `Cleaner`.
- **Constructors are never inherited** — a subclass constructor *always* chains to a superclass
  constructor via `super(...)`.
- **`final` methods cannot be overridden** — and that is a *feature*: it closes the
  "call an overridable method from a constructor" hole.
- **The classic constructor trap**: calling an overridable method from a superclass constructor
  dispatches to the *subclass* override **before** the subclass fields are initialised.
- **Wrapper identity is a trap** — `Integer` caches `-128..127`, so `==` "works" for small numbers
  and silently breaks at 128. Always use `equals()`.
- **The best answer to any trap question is a *reproduction*** — say "let me show you with a
  five-line program". That is what separates a senior from a memoriser.

---

## 1. What & Why — The Problem This Solves

### The problem

Most candidates learn OOP as four buzzwords: encapsulation, abstraction, inheritance, polymorphism.
That is enough to *write* code. It is not enough to *defend a design* — and senior interviews are
entirely about defence.

The failure mode is specific and repeatable:

```
┌─────────────────────────────────────────────────────────────────┐
│        THE INTERVIEWER'S REAL QUESTION                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  "You said X. But under condition Y, X breaks. Now what?"       │
│                                                                 │
│  The candidate who memorised the textbook:  "I... don't know"   │
│  The candidate who understands the mechanism: reproduces it,    │
│  names the root cause, and proposes the fix.                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### The solution

This chapter is organised as a **scenario bank**, not a tutorial. Each scenario follows the same
rhythm:

1. **The question as it is actually asked** (not the textbook phrasing).
2. **The trap** — the tempting, *partially* correct answer.
3. **The mechanism** — *why* the trap is tempting and *why* it fails. This is the part that
   separates a senior from a mid-level.
4. **The expert answer** — what you say when you have 60 seconds.
5. **The proof** — a runnable program with real output.

### Why this matters in production

Almost every ugly legacy Java codebase was written by someone who answered one of these questions
*incorrectly and confidently*:

| Wrong belief | Production consequence |
|---|---|
| "`static` is fine for utilities" | Hidden dependency, untestable, no DI |
| "Getters and setters are encapsulation" | `NullPointerException` from a leaked mutable `Date` |
| "`null` means 'not set yet'" | Ambiguity between "absent" and "unknown" |
| "`equals` is just value comparison" | `HashMap` lookups silently return `null` |
| "`final` is only about constants" | Subclass breaks a security check |
| "Inheritance models every relationship" | `Stack extends ArrayList`; fragile base class |

---

## 2. Mental Model — How to Think About Tricky OOP

When an interviewer asks a "tricky" OOP question, they are almost always probing one of
**five mechanisms**. Learn these five and you can reason your way to an answer for questions
you have never seen.

```text
┌──────────────────────────────────────────────────────────────────────┐
│                    THE FIVE MECHANISMS                              │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  1. WHERE DOES THE VALUE LIVE?        (heap vs stack vs pool)       │
│     "Where does this object actually live, and who owns it?"         │
│     → Integer cache, String pool, escape analysis, the this$0 field  │
│                                                                      │
│  2. WHEN DOES IT HAPPEN?              (compile time vs runtime)     │
│     "Is this decided by javac or by the JVM?"                        │
│     → static final inlining, overload vs override, early/late bind  │
│                                                                      │
│  3. WHO GETS TO DECIDE?               (the receiver's type)         │
│     "Does the reference type or the object type decide?"             │
│     → virtual dispatch, method hiding, field hiding                  │
│                                                                      │
│  4. WHAT IS THE CONTRACT?             (who promises what)           │
│     "What must remain true when someone else plugs in?"              │
│     → equals/hashCode, LSP, Serializable, AutoCloseable             │
│                                                                      │
│  5. WHAT DOES IT COST?                (the trade nobody mentions)   │
│     "What do you give up to get this?"                               │
│     → immutability vs allocation, composition vs coupling,          │
│       final vs JIT devirtualisation                                  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

**The universal interview move:** when you hit a trap, name the mechanism.

> "That's a *runtime* decision, not a compile-time one — the JVM looks at the **receiver's
> actual type**, not the reference type. So the answer is `Bark`, and here's a five-line
> reproduction."

That structure — *mechanism → answer → reproduction* — is what an interviewer is grading for.
Memorised answers get a "sure" and a follow-up question. Mechanistic answers get a "good".

---

## 3. Deep Dive — The Scenarios That Trip People Up

### 3.1 Is Java 100% Object-Oriented?

#### Short Answer: **NO**

#### Why Java is NOT 100% OOP:

```text
┌─────────────────────────────────────────────────────────────┐
│           Why Java Breaks Pure OOP                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. PRIMITIVE TYPES                                         │
│     int, char, boolean, double, etc.                        │
│     → Not objects, no methods, no inheritance             │
│                                                             │
│  2. STATIC MEMBERS                                          │
│     static methods/variables belong to class, not object    │
│     → No polymorphism, no overriding                      │
│                                                             │
│  3. NULL REFERENCES                                         │
│     null is not an object                                 │
│     → Breaks "everything is an object" rule               │
│                                                             │
│  4. WRAPPER AUTO-BOXING                                   │
│     int → Integer conversion                               │
│     → Primitives still exist underneath                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### Detailed Arguments:

##### 1. Primitive Types

```java
// These are NOT objects
int x = 10;
char c = 'A';
boolean flag = true;

// You CANNOT do:
// x.toString();     // ❌ Compile error
// x.hashCode();     // ❌ Compile error
// x.getClass();     // ❌ Compile error

// Must use wrapper classes for object behavior
Integer objX = 10;  // Auto-boxing
objX.toString();      // ✅ Works
```

**Why it matters:** Pure OOP languages like Smalltalk have no primitives — everything is an object.

##### 2. Static Methods

```java
class Calculator {
    // Static method - belongs to class, not object
    public static int add(int a, int b) {
        return a + b;
    }
}

// Usage - no object needed!
int result = Calculator.add(5, 3);  // Direct class call

// This breaks OOP because:
// 1. No "this" reference
// 2. Cannot be overridden (method hiding instead)
// 3. No polymorphism
```

##### 3. Null References

```java
String s = null;
// null is not an object - it's a special reference value
// Calling any method on null throws NullPointerException
// s.length();  // ❌ NPE!
```

##### 4. The Performance Argument (the *real* reason)

The textbook answer is "Java is pragmatic, not pure". The *engineer* answer is **allocation cost**.
A pure-OOP `int` would be a heap object with a 16-byte header — for a loop that increments a
counter a million times, that is 16 MB of garbage instead of 4 bytes on the stack. Java's designers
chose pragmatism; the escape hatch is **auto-boxing** plus the JDK's escape analysis.

#### Interview Q&A

**Q: If Java isn't 100% OOP, what percentage is it?**
A: It's a hybrid language — mostly OOP with procedural elements. The existence of primitives and
static members makes it a practical compromise. Saying "Java is 90% OOP" is a meme, not an answer;
say instead: *"Java is object-oriented at the type level but pragmatic at the runtime level —
primitives exist for performance and are bridged to objects via auto-boxing."*

**Q: Which language IS 100% OOP?**
A: Smalltalk is considered pure OOP. Even Python has primitives (though they're objects).

**Q: Why did Java designers include primitives?**
A: **Performance**. Object overhead for every number would be too slow. Primitives are stack-allocated
and faster. (More precisely: they avoid allocation and enable the JIT to keep values in registers.)

---

### 3.2 Polymorphism Without Inheritance

#### Short Answer: **YES, through Interfaces**

#### How Java Achieves Polymorphism Without Inheritance:

```text
┌─────────────────────────────────────────────────────────────┐
│     Polymorphism Through Interfaces                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Interface: Drawable                                       │
│   ├── Circle implements Drawable                            │
│   ├── Rectangle implements Drawable                         │
│   └── Triangle implements Drawable                          │
│                                                             │
│   NO inheritance between Circle, Rectangle, Triangle        │
│   BUT they all respond to draw() polymorphically            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### Code Example:

```java
import java.util.Arrays;
import java.util.List;

// Interface defines contract
interface Drawable {
    void draw();
}

// Unrelated classes - no inheritance between them
class Circle implements Drawable {
    @Override
    public void draw() {
        System.out.println("Drawing Circle");
    }
}

class Rectangle implements Drawable {
    @Override
    public void draw() {
        System.out.println("Drawing Rectangle");
    }
}

class Triangle implements Drawable {
    @Override
    public void draw() {
        System.out.println("Drawing Triangle");
    }
}

// Polymorphism WITHOUT inheritance!
public class PolymorphismDemo {
    public static void main(String[] args) {
        List<Drawable> shapes = Arrays.asList(
            new Circle(),
            new Rectangle(),
            new Triangle()
        );

        // Same method call, different behaviors
        for (Drawable shape : shapes) {
            shape.draw();  // Polymorphic behavior!
        }
    }
}
```

#### Other Ways to Achieve Polymorphism Without Inheritance:

##### 1. Duck Typing (via Reflection)

```java
// If it walks like a duck and quacks like a duck...
class Duck {
    void quack() { System.out.println("Quack!"); }
}

class Person {
    void quack() { System.out.println("Person quacking!"); }
}

// Both can "quack" - polymorphic behavior without common inheritance
```

##### 2. Generics (Parametric Polymorphism)

```java
// Same method works with different types
public <T> void print(T item) {
    System.out.println(item);
}

print("Hello");     // Works with String
print(123);       // Works with Integer
print(3.14);      // Works with Double
```

##### 3. Composition

```java
// Behavior injected at runtime - no inheritance
interface PaymentStrategy {
    void pay(double amount);
}

class ShoppingCart {
    private PaymentStrategy paymentStrategy;  // Composition

    public void setPaymentStrategy(PaymentStrategy strategy) {
        this.paymentStrategy = strategy;
    }

    public void checkout(double amount) {
        paymentStrategy.pay(amount);  // Polymorphic behavior!
    }
}
```

#### Interview Q&A

**Q: What's the difference between inheritance-based and interface-based polymorphism?**
A: Inheritance is an "IS-A" relationship (tight coupling, shared implementation), interfaces are a
"CAN-DO" relationship (loose coupling, contract only). Prefer interfaces: a class can implement
five of them but extend only one.

**Q: Can you have polymorphism with neither inheritance nor interfaces?**
A: Yes — through generics (parametric polymorphism) or duck typing via reflection. The trade-off is
that the compiler can no longer check the contract for you, so the safety moves to runtime.

---

### 3.3 Null: The Billion-Dollar Mistake

#### The Story

> **"I call it my billion-dollar mistake. It was the invention of the null reference in 1965."**
> — **Sir Tony Hoare**, inventor of null (2009)

#### Why Null is a Problem:

```text
┌─────────────────────────────────────────────────────────────┐
│           The Null Problem                                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. TYPE SAFETY VIOLATION                                   │
│     String s = null;                                        │
│     // Compiler thinks s is a String                        │
│     // But it's actually NOTHING                            │
│                                                             │
│  2. BILLION-DOLLAR COST                                     │
│     - NullPointerExceptions in production                   │
│     - Debugging time                                        │
│     - Defensive null checks everywhere                      │
│     - Security vulnerabilities                              │
│                                                             │
│  3. SEMANTIC AMBIGUITY                                      │
│     - "Not initialized yet"                                 │
│     - "No value found"                                      │
│     - "Error occurred"                                      │
│     - "Optional field"                                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### The Cost of Null:

```java
// Defensive programming - null checks everywhere!
public void processUser(User user) {
    if (user == null) return;  // ❌ Boilerplate!

    Address address = user.getAddress();
    if (address == null) return;  // ❌ More boilerplate!

    String city = address.getCity();
    if (city == null) return;  // ❌ Even more!

    // Finally do actual work
    System.out.println(city.toUpperCase());
}
```

#### Java's Solution: Optional (Java 8+)

```java
// Before Java 8 - null hell
public String getCity(User user) {
    if (user != null) {
        Address address = user.getAddress();
        if (address != null) {
            return address.getCity();
        }
    }
    return "Unknown";
}

// After Java 8 - clean with Optional
public String getCity(User user) {
    return Optional.ofNullable(user)
        .map(User::getAddress)
        .map(Address::getCity)
        .orElse("Unknown");
}
```

#### Better Alternatives:

```java
// 1. Null Object Pattern
interface Animal {
    void makeSound();
}

class Dog implements Animal {
    public void makeSound() { System.out.println("Woof!"); }
}

class NullAnimal implements Animal {
    public void makeSound() { /* Do nothing */ }  // Safe default
}

// 2. Factory that never returns null
class AnimalFactory {
    public static Animal create(String type) {
        switch (type) {
            case "dog": return new Dog();
            case "cat": return new Cat();
            default: return new NullAnimal();  // Never null!
        }
    }
}
```

#### The Honest Nuance (what a Principal says)

`Optional` is **not** a free win, and claiming otherwise is a mid-level answer:

- `Optional` is a **container**, so every use boxes an allocation (usually escape-analysed away,
  but not always).
- `Optional` as a **field** or **method parameter** is actively discouraged — it leaks an
  implementation detail into every caller. It is a *return type* idiom.
- `Optional` does **not** fix the ambiguity problem completely: `Optional.empty()` still cannot
  distinguish "not found" from "found but null" unless you use `Optional.ofNullable` plus a
  sentinel, which pushes the problem back out.
- The **real** fix is making illegal states unrepresentable: use a `record`, a sealed interface, or
  a domain type that cannot be null.

#### Interview Q&A

**Q: Why did Tony Hoare create null if it's so bad?**
A: It was a simple, easy implementation for "no value" — seemed like a good idea at the time in
ALGOL, where every reference could be null. The cost was paid by the entire industry decades later.

**Q: How does Optional solve the null problem?**
A: It makes absence of value explicit in the type system — the compiler forces you to handle it.
The nuance: it helps at the *return* boundary, and it does not remove `null` from the language.

**Q: What's the Null Object Pattern?**
A: Instead of returning null, return a special object that does nothing / provides safe defaults.
It trades a runtime failure for a slightly larger API surface and a "do nothing" instance that can
hide bugs.

---

### 3.4 The Problem with Getters and Setters

#### The Argument: Getters/Setters are Procedural Code in OOP Clothing

```text
┌─────────────────────────────────────────────────────────────┐
│     The Getters/Setters Problem                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ❌ BAD: Anemic Domain Model                                │
│  class BankAccount {                                        │
│      private double balance;                                │
│      public double getBalance() { return balance; }         │
│      public void setBalance(double b) { balance = b; }      │
│  }                                                          │
│                                                             │
│  // Client code - procedural style                          │
│  account.setBalance(account.getBalance() + 100);  // ❌     │
│                                                             │
│  ✅ GOOD: Rich Domain Model                                 │
│  class BankAccount {                                        │
│      private double balance;                                │
│      public void deposit(double amount) {                   │
│          if (amount > 0) balance += amount;               │
│      }                                                      │
│      public void withdraw(double amount) {                  │
│          if (amount <= balance) balance -= amount;          │
│      }                                                      │
│  }                                                          │
│                                                             │
│  // Client code - object-oriented                           │
│  account.deposit(100);  // ✅                               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### The Problems:

##### 1. Encapsulation Leakage

```java
// ❌ Bad - exposes internal state
class Employee {
    private Date hireDate;

    public Date getHireDate() {
        return hireDate;  // ❌ Returns mutable reference!
    }
}

// Client can modify internal state!
Employee emp = new Employee();
Date date = emp.getHireDate();
date.setYear(2025);  // ❌ Modified employee's hire date!

// ✅ Good - defensive copy
public Date getHireDate() {
    return new Date(hireDate.getTime());  // Returns copy
}
```

##### 2. No Validation

```java
// ❌ Bad - direct setter
public void setAge(int age) {
    this.age = age;  // No validation!
}

// ✅ Good - validate in constructor/methods
public void setAge(int age) {
    if (age < 0 || age > 150) {
        throw new IllegalArgumentException("Invalid age");
    }
    this.age = age;
}
```

##### 3. Procedural Thinking

```java
// ❌ Procedural style with getters/setters
order.setStatus("SHIPPED");
order.setShippedDate(new Date());
order.setTrackingNumber("12345");

// ✅ Object-oriented - tell, don't ask
order.ship("12345");  // Method encapsulates all changes
```

#### When ARE Getters/Setters Okay?

```java
// ✅ DTOs (Data Transfer Objects)
class UserDTO {
    private String name;
    private String email;
    // Getters/setters OK here - just data transport
}

// ✅ Configuration objects
class AppConfig {
    private String databaseUrl;
    // Getters/setters OK - just settings
}

// ✅ Framework requirements (Hibernate, Jackson, etc.)
@Entity
class User {
    // Getters/setters required by framework
}
```

#### Interview Q&A

**Q: Should we never use getters/setters?**
A: Use them for DTOs and when frameworks require them. For domain objects, expose behaviour instead.
The *precise* statement is: a getter that returns a **mutable** reference is an encapsulation
breach; a getter that returns an immutable value (`String`, `int`, a `record`) is harmless.

**Q: What's "Tell, Don't Ask"?**
A: Instead of asking for data and making decisions, tell the object what to do. Let it handle the logic.
It moves the *invariant* next to the *data* that it protects.

**Q: What's an Anemic Domain Model?**
A: Objects that are just data bags with no behaviour — all logic is in service classes.

---

### 3.5 Singleton: Pattern or Anti-Pattern?

#### The Debate

```text
┌─────────────────────────────────────────────────────────────┐
│     Singleton: Pattern vs Anti-Pattern                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ✅ WHY IT'S A PATTERN                                      │
│  • Ensures single instance (DB connection, config)          │
│  • Global access point                                      │
│  • Lazy initialization                                      │
│  • Thread-safe with proper implementation                   │
│                                                             │
│  ❌ WHY IT'S AN ANTI-PATTERN                                │
│  • Hidden dependencies (hard to test)                       │
│  • Violates Single Responsibility Principle                 │
│  • Global state (hard to reason about)                      │
│  • Tight coupling                                           │
│  • Hard to mock in unit tests                               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### The Problems:

##### 1. Hidden Dependencies

```java
// ❌ Bad - dependency hidden inside method
class OrderService {
    public void processOrder(Order order) {
        // Hidden dependency on Database singleton!
        Database.getInstance().save(order);  // ❌
    }
}

// ✅ Good - dependency injection
class InjectingOrderService {
    private final Database database;

    public InjectingOrderService(Database database) {  // Explicit dependency
        this.database = database;
    }

    public void processOrder(Order order) {
        database.save(order);  // ✅ Testable
    }
}
```

##### 2. Hard to Test

```java
// ❌ Bad - can't mock Singleton
@Test
void testOrderService() {
    OrderService service = new OrderService();
    service.processOrder(order);  // Uses real database!
}

// ✅ Good - inject mock
@Test
void testOrderService() {
    Database mockDb = mock(Database.class);
    OrderService service = new OrderService(mockDb);
    service.processOrder(order);  // Uses mock
    verify(mockDb).save(order);   // Verify behavior
}
```
#### The Three Ways to Implement It (and their failure modes)

```java
// ❌ 1. Eager static holder — fine, but not lazy
public class Eager {
    private static final Eager INSTANCE = new Eager();
    private Eager() {}
    public static Eager getInstance() { return INSTANCE; }
}

// ❌ 2. Double-checked locking WITHOUT volatile — broken
public class BrokenDcl {
    private static BrokenDcl instance;                 // ❌ not volatile!
    private BrokenDcl() {}
    public static BrokenDcl getInstance() {
        if (instance == null) {                       // T1 reads stale null
            synchronized (BrokenDcl.class) {
                if (instance == null) {               // ❌ second check needed
                    instance = new BrokenDcl();
                }
            }
        }
        return instance;
    }
}
// Bug: another thread can observe a non-null `instance` whose fields are
// not yet visible, because the constructor's writes were reordered after
// the reference write. `volatile` fixes it by creating a happens-before edge.

// ✅ 3. Holder idiom — lazy AND thread-safe, no synchronization
public class Holder {
    private Holder() {}
    private static class InstanceHolder {
        private static final Holder INSTANCE = new Holder();
    }
    public static Holder getInstance() { return InstanceHolder.INSTANCE; }
}
// The JVM guarantees the holder class is initialised exactly once, and
// class initialisation is thread-safe by the JLS.

// ✅✅ 4. Enum — the best answer
public enum Config {
    INSTANCE;
    public void reload() { /* ... */ }
}
// ✅ Thread-safe by construction
// ✅ Immune to reflection (no private constructor to hack)
// ✅ Serialization-safe (the JVM deserialises enums as singletons)
// ✅ JIT can devirtualise the access completely
```

#### When Singleton IS Appropriate

- Logging frameworks
- Configuration management
- Connection pools (managed by frameworks)
- Use **Dependency Injection** (Spring) instead of manual Singleton

#### Interview Q&A

**Q: How to break Singleton?**
A: Reflection, serialization, cloning. Enum Singleton prevents all three. (For reflection: with the
double-checked-locking or static-holder versions, `setAccessible(true)` on the private constructor
lets you create a second instance; the enum's constructor cannot be reflectively invoked.)

**Q: Why prefer enum Singleton?**
A: Thread-safe by default, prevents reflection attack, handles serialization automatically, and the
JIT can optimise the access because the instance is a compile-time constant.

---

### 3.6 Object vs Class at Runtime

#### How JVM Represents Objects

```text
┌─────────────────────────────────────────────────────────────┐
│           Object in Memory                                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐                                          │
│  │ Object Header │  - Mark Word (hashCode, GC age, locks)  │
│  │  (12-16 bytes)│  - Klass Pointer (→ Class metadata)     │
│  ├──────────────┤                                          │
│  │ Instance Data │  - Fields (int, String, etc.)            │
│  │              │  - Ordered by size (longs, ints, shorts) │
│  ├──────────────┤                                          │
│  │   Padding    │  - Align to 8-byte boundary              │
│  └──────────────┘                                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

```java
// getClass() returns runtime class
Object obj = new String("Hello");
Class<?> clazz = obj.getClass();  // java.lang.String (runtime type)

// instanceof checks IS-A relationship
obj instanceof String;    // true
obj instanceof Object;    // true
obj instanceof Integer;   // false

// Class object is itself an Object
Class<?> c = String.class;
c instanceof Object;      // true
c instanceof Class;       // true
```

#### The Three Levels of "Type" in Java (this is what the question is really probing)

```text
┌─────────────────────────────────────────────────────────────────────┐
│                    THREE LEVELS OF "TYPE"                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. COMPILE-TIME (static) TYPE                                      │
│     What javac sees: the DECLARED type of the reference.            │
│     Used for: overload resolution, casting checks, generics.        │
│                                                                     │
│  2. RUNTIME (dynamic) TYPE                                         │
│     The ACTUAL class of the object, read from the object header.     │
│     Used for: virtual dispatch (invokevirtual), getClass().         │
│                                                                     │
│  3. CLASS/DECLARATION TYPE                                          │
│     The .class literal — the Class object itself.                    │
│     One Class object per (binary name + ClassLoader) pair.           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

#### Class Identity: One Class Object Per ClassLoader

```java
// Same class loaded by two different classloaders => two distinct Class objects
ClassLoader a = new URLClassLoader(urlA);
ClassLoader b = new URLClassLoader(urlB);

Class<?> c1 = a.loadClass("com.acme.Order");
Class<?> c2 = b.loadClass("com.acme.Order");

c1 == c2;              // false — different ClassLoader, different Class
c1.getName().equals(c2.getName());  // true — same binary name
c1.getClassLoader() != c2.getClassLoader();  // true
```

This is why `ClassCastException` can happen for a class that *looks* identical: two classloaders
loaded two copies, and casting between them fails.

#### Instanceof Pattern Matching (Java 16+)

```java
// Before Java 16 — cast after a null check, twice
if (obj instanceof Person) {
    Person p = (Person) obj;
    System.out.println(p.getName());
}

// Java 16+ — pattern matching for instanceof (final in Java 21)
if (obj instanceof Person p) {
    System.out.println(p.getName());  // p is in scope, no cast
}
```

#### Interview Q&A

**Q: Difference between getClass() and instanceof?**
A: `getClass()` returns the exact runtime class. `instanceof` checks IS-A (includes parent types).
Use `getClass()` for strict equality; use `instanceof` for type tests.

**Q: How many Class objects for one class?**
A: One per ClassLoader. The same class loaded by different ClassLoaders = different Class objects.
This is the basis of classloader isolation in app servers, and the cause of "impossible"
`ClassCastException`s in plugin architectures.

---

### 3.7 Anemic Domain Model

#### What is it?

Objects that are just data containers with no behaviour — all logic is in service classes.

```java
// ❌ Anemic Domain Model — the logic lives OUTSIDE the entity
class AnemicEmployee {
    private String name;
    private double salary;
    // Only getters and setters — no behavior!
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getSalary() { return salary; }
    public void setSalary(double salary) { this.salary = salary; }
}

class AnemicEmployeeService {
    public void giveRaise(AnemicEmployee emp, double percent) {
        double newSalary = emp.getSalary() * (1 + percent / 100);
        emp.setSalary(newSalary);
    }
}

// ✅ Rich Domain Model — the entity owns its own rules
class Employee {
    private String name;
    private double salary;

    public void giveRaise(double percent) {
        if (percent <= 0) throw new IllegalArgumentException("Raise must be positive");
        this.salary *= (1 + percent / 100);
    }

    public boolean isHighEarner(double threshold) {
        return salary > threshold;
    }
}
```

#### Why Anemic is Bad

- Breaks encapsulation — logic outside the object
- Procedural code disguised as OOP
- Business rules scattered across services

#### The Counter-Argument (a Principal will raise it)

Martin Fowler, who coined "anemic domain model", has explicitly said it is **not** always wrong. The
honest position is:

| Anemic is fine when... | Rich is better when... |
|---|---|
| The model is a pure data contract (DTO, API schema) | The entity has real invariants |
| The logic is simple CRUD with no rules | Rules must be enforced atomically |
| The team is split into frontend/backend | You want to protect invariants |
| A NoSQL document or view model is the domain | Transactional consistency matters |

The *real* failure mode is **not** "anemic" but "**anemic with rules scattered in services**" —
the same business rule implemented in three services, diverging over time.

---

### 3.8 Early Binding vs Late Binding

#### Early Binding (Compile-time / Static Binding)

```java
// Resolved at COMPILE time
class Calculator {
    int add(int a, int b) { return a + b; }       // Overloaded
    double add(double a, double b) { return a + b; } // Overloaded
}

Calculator calc = new Calculator();
calc.add(1, 2);     // Compiler picks int version at compile time
calc.add(1.0, 2.0); // Compiler picks double version at compile time
```

**Early binding applies to:**
- Method overloading
- static methods
- final methods
- private methods

#### Late Binding (Runtime / Dynamic Binding)

```java
// Resolved at RUNTIME
class Animal { void speak() { System.out.println("Animal"); } }
class Dog extends Animal { void speak() { System.out.println("Bark"); } }
class Cat extends Animal { void speak() { System.out.println("Meow"); } }

Animal a = new Dog();  // Reference: Animal, Object: Dog
a.speak();  // "Bark" — resolved at runtime based on OBJECT type
```

**Late binding applies to:**
- Method overriding (virtual method dispatch)
- Interface method calls

#### Comparison

| Aspect | Early Binding | Late Binding |
|--------|--------------|-------------|
| When | Compile time | Runtime |
| What | Overloading | Overriding |
| Based on | Reference type | Object type |
| Performance | Faster | Slightly slower (vtable lookup) |
| Methods | static, final, private | Instance methods |

#### The Nuance: `final` and `private` are *statically bound for a reason*

This is the detail that separates a senior answer. The JLS says a method is *potentially
overridable* only if it is not `private`, not `static`, and not `final`. **The reason is the
"fragile base class" problem combined with devirtualisation:**

- A `final` method can be **inlined** by the JIT because the compiler knows no subclass can
  replace it → faster, and it protects invariants.
- A `private` method is not inherited at all → it is not part of the contract.
- A `static` method is bound to the *declaring* class → no virtual dispatch is possible.

> **Interviewer trap:** "Does `final` on a method guarantee faster code?"
> **Answer:** No — not by itself. The JIT can inline non-final methods too if it can *prove* (via
> class-hierarchy analysis, CHA, or a class-hierarchy assumption) that no subclass overrides them.
> What `final` guarantees is **semantic**: the behaviour cannot change. It is a design tool that
> *happens* to enable optimisation, not an optimisation switch.

---

### 3.9 Virtual Functions in Java

#### Are All Methods Virtual in Java?

**YES** — All non-static, non-final, non-private instance methods are virtual by default.

```java
class Parent {
    void display() {  // Virtual by default
        System.out.println("Parent");
    }

    final void show() {  // NOT virtual — cannot override
        System.out.println("Final");
    }

    private void helper() {  // NOT virtual — not visible to child
        System.out.println("Private");
    }

    static void staticMethod() {  // NOT virtual — belongs to class
        System.out.println("Static");
    }
}

class Child extends Parent {
    @Override
    void display() {  // Overrides virtual method
        System.out.println("Child");
    }
}

Parent p = new Child();
p.display();  // "Child" — virtual method dispatch
```

#### How JVM Implements Virtual Methods

- **vtable (Virtual Method Table):** Each class has a vtable mapping method signatures to actual implementations.
- At runtime, JVM looks up the vtable of the **actual object type** to find the correct method.

```text
Parent vtable:        Child vtable:
┌─────────────┐       ┌─────────────┐
│display→Parent│       │display→Child │  ← overridden
│show→Parent   │       │show→Parent   │  ← inherited (final)
└─────────────┘       └─────────────┘
```

#### The Bytecode Reality

```java
Parent p = new Child();
p.display();   // compiles to: invokevirtual  Parent.display:()V
```

- `invokevirtual` — the default for instance methods; the JVM resolves the receiver's actual class
  and looks up the vtable slot.
- `invokeinterface` — the same, but through the interface's method table.
- `invokespecial` — for constructors, `private` methods, and `super.method()` calls: **no virtual
  dispatch**. This is exactly why `super.display()` prints "Parent" even from a `Child`.
- `invokestatic` — for `static` methods: bound to the declaring class at compile time.
- `invokedynamic` — for lambdas (via `LambdaMetafactory`).

```java
class Parent { void display() { System.out.println("Parent"); } }
class Child extends Parent {
    @Override void display() { System.out.println("Child"); }
    void both() {
        super.display();   // invokespecial -> "Parent"
        this.display();    // invokevirtual  -> "Child"
    }
}
```

---

### 3.10 Destructor in Java

#### Short Answer: Java Has No Destructors

Unlike C++, Java uses **Garbage Collection**, not manual memory management.

```java
// C++ has destructors
~MyClass() { /* cleanup */ }

// Java had finalize() — DEPRECATED since Java 9
@Deprecated
protected void finalize() throws Throwable {
    // DON'T use this!
}
```

#### Why finalize() is Bad

- Unpredictable when (or if) it runs
- Performance overhead
- Can resurrect objects (bad practice)
- Deprecated since Java 9
- **Removed for removal in Java 18** — the compiler warns, and it is scheduled for deletion
- It was never called for objects that become unreachable while the finalizer is pending, so it is
  not even a *guarantee* of cleanup
- It is **not** a resource-management mechanism: it does not run at a predictable time, and it does
  not run for I/O streams that are still open

#### Modern Alternatives

```java
// 1. try-with-resources (Java 7+) — PREFERRED
try (Connection conn = getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {
    // use resources
}  // Automatically closed

// 2. Cleaner API (Java 9+)
Cleaner cleaner = Cleaner.create();
class MyResource implements AutoCloseable {
    private final Cleaner.Cleanable cleanable;

    MyResource() {
        cleanable = cleaner.register(this, () -> {
            // Cleanup action
        });
    }

    @Override
    public void close() {
        cleanable.clean();
    }
}
```

#### `try-with-resources` Is Not Just "Convenience"

The reason it was added in Java 7 is **suppressed exceptions**, and this is what an interviewer
should hear:

```java
// Without TWR: the second exception SWALLOWS the first
BufferedReader br = new BufferedReader(new FileReader("f.txt"));
try {
    br.readLine();            // throws IOException A
} finally {
    br.close();               // throws IOException B -> A is LOST
}

// With TWR: both are preserved, B is the "suppressed" exception
try (BufferedReader br = new BufferedReader(new FileReader("f.txt"))) {
    br.readLine();
}
// If both throw, A is the primary and B appears as:
//   Suppressed: java.io.IOException: B
```

Resources are closed in **reverse order** of declaration, and every resource is closed even if an
earlier one throws.

---

### 3.11 Copy Constructor

#### Java Doesn't Have Built-in Copy Constructor (Unlike C++)

```java
class Person {
    private String name;
    private List<String> hobbies;

    // Regular constructor
    public Person(String name, List<String> hobbies) {
        this.name = name;
        this.hobbies = hobbies;
    }

    // Copy constructor (manual)
    public Person(Person other) {
        this.name = other.name;                              // String is immutable — safe
        this.hobbies = new ArrayList<>(other.hobbies);       // Deep copy of mutable list
    }
}

// Usage
Person p1 = new Person("John", Arrays.asList("Chess", "Reading"));
Person p2 = new Person(p1);  // Copy
p2.getHobbies().add("Gaming");
// p1's hobbies unchanged — deep copy worked
```

#### Copy Constructor vs clone()

| Aspect | Copy Constructor | clone() |
|--------|-----------------|---------|
| Explicit | Yes | No (magic method) |
| Type safety | Yes | Returns Object (needs cast) |
| Deep copy | Easy to implement | Must manually deep copy |
| Inheritance | Works naturally | Fragile with inheritance |
| Recommended | ✅ Yes | ❌ Avoid (Effective Java) |

#### The Deeper Problem with `clone()`

`Object.clone()` is `protected` and does a **field-by-field shallow copy** without running any
constructor. That has three nasty consequences:

1. **No constructor invariants.** The clone never passes through validation, so you can create an
   object that the constructor would have rejected.
2. **The `Cloneable` marker interface is a wart.** `Cloneable` has **no methods** — it is a pure
   marker. If a class implements `Cloneable` but does not override `clone()`, you get
   `CloneNotSupportedException`. If it does *not* implement `Cloneable` but overrides `clone()`,
   `super.clone()` throws. The interface exists purely to *flip* the behaviour of a native method.
3. **Subclass fragility.** A subclass's `clone()` must itself call `super.clone()` and then fix up
   its own fields, or the copy is of the wrong type/state.

---

### 3.12 Can Constructors Be Inherited?

#### Short Answer: **NO**

```java
class Parent {
    public Parent(String name) {
        System.out.println("Parent: " + name);
    }
}

class Child extends Parent {
    // ❌ ERROR: No default constructor in Parent
    // Must explicitly call parent constructor

    public Child(String name) {
        super(name);  // Must be first line
    }
}

// new Child("John");
// Output: Parent: John
```

#### Constructor Chaining Rules

1. `super()` is implicitly called if no explicit `super()` or `this()`
2. `super()` or `this()` must be **first statement**
3. If parent has no default constructor, child MUST call `super(args)`
4. Cannot call both `super()` and `this()` in same constructor

```java
class Animal {
    Animal() { System.out.println("Animal"); }
    Animal(String name) { System.out.println("Animal: " + name); }
}

class Dog extends Animal {
    Dog() {
        this("Unknown");  // Calls Dog(String)
    }

    Dog(String name) {
        super(name);  // Calls Animal(String)
        System.out.println("Dog: " + name);
    }
}

// new Dog();
// Output: Animal: Unknown
//         Dog: Unknown
```

#### Field Initialiser vs Constructor Body Order

A detail that catches people: **field initialisers run before the constructor body**, and always
after the superclass constructor.

```java
class Parent {
    Parent() { System.out.println("Parent ctor"); }
}

class Child extends Parent {
    int x = 5;                                  // initialiser
    Child() {
        super();                                // "Parent ctor" prints FIRST
        System.out.println("Child ctor x=" + x);
    }
}
// Output:
// Parent ctor
// Child ctor x=5
```

---

### 3.13 Can We Override Final Methods?

#### Short Answer: **NO**

```java
class Parent {
    final void display() {
        System.out.println("Parent");
    }
}

class Child extends Parent {
    // ❌ COMPILE ERROR: Cannot override final method
    // void display() { System.out.println("Child"); }
}
```

#### Why Use final Methods?

1. **Security:** Prevent subclasses from changing critical behavior
2. **Performance:** JIT can inline final methods (no virtual dispatch needed)
3. **Design:** Signal that method is complete and shouldn't be modified

#### final Keyword Summary

| Applied To | Effect |
|-----------|--------|
| Variable | Cannot reassign (constant) |
| Method | Cannot override |
| Class | Cannot extend |
| Parameter | Cannot reassign inside method |

```java
final class String { }       // Cannot extend String
final int MAX = 100;         // Constant

class Parent {
    final void critical() { } // Cannot override
}

void process(final int x) {
    // x = 10;  // ❌ Cannot reassign
}
```

#### `final` Does NOT Mean "Immutable"

This is the single most common `final` misconception, and it is worth stating explicitly:

```java
final class Config {
    private final List<String> servers;      // final REFERENCE...
    private final Date lastModified;         // ...but the OBJECT is mutable

    Config(List<String> servers) {
        this.servers = servers;              // ❌ no defensive copy!
    }

    void mutate() {
        servers.add("evil-host");            // ❌ the object changed
        lastModified.setTime(0);            // ❌ and so did this
    }
}
```

`final` guarantees **the reference never changes**. It guarantees **nothing** about the object's
contents. For true immutability you need `final` + `private` + no setters + **defensive copies** —
see [09 · Immutable Classes](./09-OOP-Immutable-Classes.md).

---

### 3.14 Scenario: Constructor Ordering and the "Calling an overridable method" Trap

This is the trap that most reliably separates a mid-level from a senior in Java interviews. It is
almost never taught, and every candidate gets it wrong the first time.

#### The Question

> "What is printed? Why?"

```java
class Base {
    Base() {
        System.out.println("Base()");
        init();                       // <-- calls an overridable method!
    }
    void init() {
        System.out.println("Base.init");
    }
}

class Derived extends Base {
    int x = 5;                        // field initialiser
    Derived() {
        super();                      // triggers Base() -> init()
        System.out.println("Derived() x=" + x);
    }
    @Override
    void init() {
        System.out.println("Derived.init x=" + x);
    }
}

public class ConstructorTrap {
    public static void main(String[] args) {
        new Derived();
    }
}
```

#### The Actual Output (verified)

```text
Base()
Derived.init x=0
Derived() x=5
```

#### The Dry Run

| Step | What happens | Why |
|------|--------------|-----|
| 1 | `new Derived()` begins | — |
| 2 | `Derived()` calls `super()` **first** | `super()` must be the first statement |
| 3 | `Base()` constructor body starts | — |
| 4 | `System.out.println("Base()")` | prints `Base()` |
| 5 | `init()` is called | **virtual dispatch** — receiver is a `Derived` |
| 6 | `Derived.init()` runs | the subclass override wins, even from the superclass ctor |
| 7 | `x` is read — but it is **0**, not 5 | the subclass's field initialiser has **not run yet** |
| 8 | prints `Derived.init x=0` | ✅ the bug |
| 9 | `Base()` returns; `Derived` field initialiser runs | `x = 5` |
| 10 | `Derived()` constructor body runs | prints `Derived() x=5` |

#### Why This Happens (the mechanism)

Two independent rules collide:

1. **Java initialisation order**: superclass constructor → superclass field initialisers →
   superclass constructor body → subclass field initialisers → subclass constructor body.
2. **Java dispatch is always virtual** for instance methods — *even from a constructor*.

So when `Base()` calls `init()`, the JVM looks at the receiver's *actual* class (`Derived`) and
picks `Derived.init()`. But at that instant `Derived`'s fields are still in their default state
(`x == 0`).

#### The Fixes (in order of preference)

```java
// ✅ FIX 1 (best): Don't call overridable methods from a constructor.
// Use a template method or an explicit init() called AFTER construction.

// ✅ FIX 2: Make init() final in the parent so it can't be overridden
class Base {
    Base() { init(); }
    final void init() { /* safe */ }
}

// ✅ FIX 3: Use a separate, non-overridable "do the work" method
class OtherBase {
    private void doInit() { /* called by OtherBase() */ }
}
```

#### The Production Impact

This is not an academic puzzle — it is a classic source of **NPEs and silently-zeroed fields** in
frameworks that call overridable methods during construction (some legacy `BaseService` patterns,
some serialisation frameworks, some dependency-injection containers that inject into fields during
construction). The rule of thumb: **a constructor should not call any method that a subclass could
override.**

---

### 3.15 Scenario: The Integer Cache and Wrapper Identity

#### The Question

> "What is printed? And is `==` ever safe for wrappers?"

```java
public class IntegerCache {
    public static void main(String[] args) {
        Integer i1 = 127, i2 = 127, i3 = 128, i4 = 128;
        System.out.println("Integer 127 == 127 : " + (i1 == i2));
        System.out.println("Integer 128 == 128 : " + (i3 == i4));
        System.out.println("Integer.valueOf(128)==new Integer(128) : "
                + (Integer.valueOf(128) == new Integer(128)));
    }
}
```

#### The Actual Output (verified)

```text
Integer 127 == 127 : true
Integer 128 == 128 : false
Integer.valueOf(128)==new Integer(128) : false
```

#### The Mechanism

- `Integer.valueOf(int)` returns a **cached instance** for values in `-128..127`
  (the `IntegerCache` in `java.lang.Integer`). So `i1` and `i2` are the *same object* → `==` is `true`.
- For `128`, `valueOf` allocates a **new** `Integer` each time → two different objects → `==` is
  `false`, but `.equals()` is `true`.
- `new Integer(128)` always allocates, so it is never `==` to a cached one.

#### Why This Is a Production Bug

```java
// ❌ A real bug: works in dev (small ids), fails in prod (large ids)
if (userId == session.getUserId()) { ... }   // id 5000 -> always false!
```

This is the classic **"it worked in testing"** bug. Test data has small IDs; production data has
big ones.

#### The Rules

| Type | Cache range | Use `==`? |
|------|-------------|-----------|
| `Integer` | `-128..127` (fixed by spec; may be larger) | ❌ Never |
| `Long` | `-128..127` | ❌ Never |
| `Short` | `-128..127` | ❌ Never |
| `Byte` | all values (`-128..127`) | ❌ Never |
| `Character` | `0..127` | ❌ Never |
| `Boolean` | `TRUE` / `FALSE` only | ⚠️ Technically safe, but still use `equals()` |
| `Double` / `Float` | none | ❌ Never |
| `String` | interned literals in the pool | ⚠️ Only for literals — never for computed strings |

> **Note:** the `Integer` cache upper bound of 127 is the *specified minimum*. A JVM may cache
> more (e.g. `-XX:AutoBoxCacheMax`), so you can **never** rely on it. Never rely on `-128..127`
> being the exact range either.

---

### 3.16 Scenario: Method Resolution Order

#### The Question

> "Which method is called, and when is it decided?"

```java
class Parent {
    static void show()  { System.out.println("Parent static"); }
    void showInstance() { System.out.println("Parent instance"); }
}
class Child extends Parent {
    static void show()  { System.out.println("Child static"); }
    @Override void showInstance() { System.out.println("Child instance"); }
}

public class Resolution {
    public static void main(String[] args) {
        Parent p = new Child();
        p.show();                 // ?  (static  -> bound to the reference type)
        new Child().show();       // ?  (static  -> bound to the reference type)
        p.showInstance();         // ?  (instance -> bound to the receiver)
    }
}
```

#### The Actual Output

```text
Parent static
Child static
Child instance
```

#### The Rules (memorise this table)

| Call | Resolved by | When | Bytecode |
|------|-------------|------|----------|
| Overload (`add(int)` vs `add(double)`) | **Reference type** + argument types | Compile time | `invokevirtual`/`invokespecial`/`invokestatic` |
| Override of an instance method | **Receiver's actual type** | Runtime | `invokevirtual` / `invokeinterface` |
| `static` method | **Reference type** (compile-time) | Compile time | `invokestatic` |
| `private` method | Declaring class only | Compile time | `invokespecial` |
| `super.m()` | Declaring class of `super` | Compile time | `invokespecial` |

**The rule in one sentence:** *overloads are chosen by the compiler using the static types;
overrides are chosen by the JVM using the runtime type.*

#### The Trap

> "But `p.show()` — `p` is declared as `Parent` and the object is `Child`. Why does the static
> method resolve to `Parent` and the instance method to `Child`?"

Because **static methods are not polymorphic at all**. There is no `Child` instance in the picture
for a `static` call — the method is a member of the *class*, and the class selected is the one
named by the *reference's compile-time type*. Instance methods, by contrast, are dispatched on the
*object*.

```java
// The classic demonstration
Parent p = new Child();
p.show();          // "Parent static"  -- resolved by Parent (reference type)
((Child) p).show(); // "Child static"  -- resolved by Child
```

---

### 3.17 Scenario: Class Loading, Initialisation and the `static` Trap

#### The Question

> "In what order do these run? And when?"

```java
public class InitOrder {
    static { System.out.println("static block"); }
    { System.out.println("instance block"); }
    static int s = 10;
    int i = 20;
    InitOrder() { System.out.println("constructor"); }

    public static void main(String[] args) {
        System.out.println("--- main starts ---");
        new InitOrder();
    }
}
```

#### The Actual Output

```text
static block
--- main starts ---
instance block
constructor
```

#### The Full Ordering Rules

```text
┌──────────────────────────────────────────────────────────────────┐
│              CLASS INITIALISATION ORDER (JLS 12.4)               │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ON FIRST ACTIVE USE OF A CLASS:                                │
│    1. Superclass initialised first (top-down)                   │
│    2. Static fields initialised (in declaration order)          │
│    3. Static initialiser blocks executed (in declaration order) │
│    4. Class is now "initialised" — usable                      │
│                                                                  │
│  ON EACH `new` OF THAT CLASS:                                   │
│    5. Allocate memory, fields default (0/null/false)            │
│    6. Superclass: field inits + instance blocks                 │
│    7. Superclass constructor body                              │
│    8. Subclass: field inits + instance blocks                   │
│    9. Subclass constructor body                                 │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### "First active use" — the subtle part

A class is initialised **lazily**, at its first *active use*. This includes:

- `new` of the class
- Accessing a **static** field or **static** method
- `Class.forName(...)` (with initialisation) / `ClassLoader.loadClass(...)` (without)
- Reflection `getStaticFields()` / `getDeclaredMethods()` — some force initialisation
- Using it as a superclass or in a `instanceof`/cast

**It does NOT include:**

- `Class.forName(name, false, loader)` — loads without initialising
- Defining a class that merely *references* it
- Accessing an **instance** method or field through a `null` reference
#### The `static` Trap That Costs Real Money

```java
import java.util.Properties;

public class Config {
    static final Properties PROPS = load();          // static initialiser
    private static Properties load() {
        Properties p = new Properties();
        p.setProperty("mode", "prod");               // in reality: read a file
        return p;
    }
}
```

If a static initialiser **throws**, the class enters the **"Erroneous" state** and **every
subsequent access throws `NoClassDefFoundError`** — even the ones that would not have triggered
the failure. This is a classic "it works at startup, then everything dies" failure.

The mitigation is to not do I/O in a static initialiser, or to wrap it so a failure degrades
gracefully rather than poisoning the class.

## 4. Visualizations

### 4.1 The Complete OOP Trap Map

```text
┌──────────────────────────────────────────────────────────────────────┐
│                    THE OOP TRAP MAP                                  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  COMPILE TIME (javac decides)                                       │
│  ├── Overload resolution ........... by argument + reference type    │
│  ├── static method selection ....... by reference type               │
│  ├── final constant inlining ....... `static final int` inlined     │
│  ├── private / super method call ... invokespecial (no dispatch)    │
│  ├── generic type checking ......... invariance enforced here       │
│  └── unreachable code ............... after return/throw             │
│                                                                      │
│  RUNTIME (JVM decides)                                               │
│  ├── Virtual dispatch .............. by receiver's actual class      │
│  ├── getClass() .................... actual class                    │
│  ├── instanceof .................... subtype test                    │
│  ├── ArrayStoreException ........... covariant array store check     │
│  ├── Integer cache ................. -128..127 identity              │
│  ├── String pool ................... intern() identity               │
│  ├── class initialisation .......... on first active use            │
│  └── constructor dispatch trap ...... override runs before fields    │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 4.2 The Constructor Trap — Timeline

```text
time ──────────────────────────────────────────────────────────────▶

new Derived()
   │
   ├─ 1. allocate Derived, fields = default (x = 0)
   │
   ├─ 2. Derived() calls super()          ← must be first statement
   │      │
   │      ├─ 3. Base() body starts
   │      │     ├─ print "Base()"
   │      │     └─ init()  ─────────────────┐
   │      │            │                   │  receiver is a Derived
   │      │            ▼                   │  → virtual dispatch
   │      │      Derived.init()  ──────────┘
   │      │            └─ reads x  →  x == 0   ❌ NOT 5 !
   │      │               (Derived's field initialiser has not run yet)
   │      └─ Base() returns
   │
   ├─ 4. Derived's field initialisers run   →  x = 5
   │
   └─ 5. Derived() constructor body runs   →  print "Derived() x=5"

Actual output:
  Base()
  Derived.init x=0
  Derived() x=5
```

### 4.3 Object Memory Layout with the `this$0` Field

```text
┌─────────────────────────────────────────────────────────────────┐
│  HEAP                                                             │
│                                                                  │
│  ┌────────────────────────┐        ┌──────────────────────────┐  │
│  │ Outer instance          │        │ Inner instance           │  │
│  ├────────────────────────┤        ├──────────────────────────┤  │
│  │ header (12-16 B)       │        │ header (12-16 B)         │  │
│  │ klass pointer           │        │ klass pointer            │  │
│  │ outerVar: int           │        │ this$0 ──────────────┐   │  │
│  │ largeData: byte[1MB]    │        └──────────────────────┼───┘  │
│  └────────────────────────┘              ▲             │      │
│        ▲                                │             └──────┘      │
│        │                                │   implicit reference to   │
│        │                                │   the enclosing instance   │
│        └────────────────────────────────┘                          │
│   Outer stays reachable as long as Inner is reachable              │
│   → the 1 MB byte[] can NEVER be collected                          │
└─────────────────────────────────────────────────────────────────┘

  Fix: make Inner STATIC  →  no this$0 field  →  no back-reference
```

### 4.4 Method Resolution Decision Tree

```text
                    a.foo(x)
                        │
                        ▼
            ┌───────────────────────┐
            │ Is foo static?        │
            └───┬───────────────┬───┘
              yes                no
                │                 │
                ▼                 ▼
   ┌──────────────────────┐  ┌────────────────────────┐
   │ Resolve by the       │  │ Is the compile-time type│
   │ REFERENCE type.      │  │ of `a` final/private?   │
   │ (Parent p = new      │  └──┬──────────────┬──────┘
   │  Child(); p.foo())  │   yes               no
   │ → Parent.foo()       │    │                 │
   └──────────────────────┘    ▼                 ▼
                       ┌──────────────┐  ┌────────────────────────┐
                       │ invokestatic │  │ RUNTIME: look at the   │
                       │ (bound early)│  │ receiver's actual class│
                       └──────────────┘  │ → Child.foo()          │
                                        └────────────────────────┘

  Overloads are ALSO resolved at compile time, by the
  compile-time types of the ARGUMENTS.
```

### 4.5 The `Optional` / Null Spectrum

```text
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  null            →  "there is no value"   (ambiguous, unsafe)    │
│                                                                  │
│  Optional.empty()→  "there is no value"   (explicit, safe)      │
│                                                                  │
│  Optional.of(x)  →  "there IS a value"    (non-null asserted)   │
│                                                                  │
│  Null Object     →  "the absence IS a value" (behavioural)      │
│                                                                  │
│  sealed/records  →  absence is a DIFFERENT TYPE (best)          │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — The Constructor Trap, Reproduced

**Problem:** *"What does this print, and why?"*

```java
class Base {
    Base() {
        System.out.println("Base()");
        init();
    }
    void init() {
        System.out.println("Base.init");
    }
}

class Derived extends Base {
    int x = 5;
    Derived() {
        super();
        System.out.println("Derived() x=" + x);
    }
    @Override
    void init() {
        System.out.println("Derived.init x=" + x);
    }
}

public class ConstructorTrap {
    public static void main(String[] args) {
        new Derived();
    }
}
```

**Actual output (verified on JDK 25):**

```text
Base()
Derived.init x=0
Derived() x=5
```

**Dry run:**

| # | Step | `x` value | Printed |
|---|------|-----------|---------|
| 1 | `new Derived()` allocates; `x = 0` (default) | 0 | — |
| 2 | `Derived()` → `super()` → `Base()` starts | 0 | — |
| 3 | `Base()` prints | 0 | `Base()` |
| 4 | `Base()` calls `init()`; receiver is `Derived` → `Derived.init()` | 0 | — |
| 5 | `Derived.init()` reads `x` — **still 0** | 0 | `Derived.init x=0` |
| 6 | `Base()` returns; `Derived`'s field initialiser runs | **5** | — |
| 7 | `Derived()` body runs | 5 | `Derived() x=5` |

**Takeaway:** The order is *super-ctor → override → subclass field init*. If `init()` depended on
`x`, it would have read a zero. **Never call an overridable method from a constructor.**

---

### Example 2 — Integer Cache: Why `==` Works in Dev and Fails in Prod

**Problem:** *"Is `Integer` `==` comparison ever safe?"*

```java
public class IntegerCache {
    public static void main(String[] args) {
        Integer i1 = 127, i2 = 127, i3 = 128, i4 = 128;
        System.out.println("Integer 127 == 127 : " + (i1 == i2));
        System.out.println("Integer 128 == 128 : " + (i3 == i4));
        System.out.println("Integer.valueOf(128)==new Integer(128) : "
                + (Integer.valueOf(128) == new Integer(128)));
    }
}
```

**Actual output (verified on JDK 25):**

```text
Integer 127 == 127 : true
Integer 128 == 128 : false
Integer.valueOf(128)==new Integer(128) : false
```

**Dry run:**

| Expression | `valueOf` behaviour | Same object? | `==` |
|------------|--------------------|--------------|------|
| `i1 == i2` (both 127) | both return the cached `Integer(127)` | ✅ yes | `true` |
| `i3 == i4` (both 128) | 128 > 127 → two new `Integer`s | ❌ no | `false` |
| `valueOf(128) == new Integer(128)` | one cached-or-new, one always-new | ❌ no | `false` |

**Takeaway:** `==` on wrappers compares *references*. The `-128..127` cache makes it *appear* to
work for small numbers. Test data has small IDs; production data does not. **Always use
`equals()`.** The same applies to `Long`, `Short`, `Byte`, `Character`.

---

### Example 3 — String Identity: Pool, `new` and `intern()`

**Problem:** *"What is the difference between these two strings?"*

```java
public class StringIdentity {
    public static void main(String[] args) {
        String s1 = "hello";
        String s2 = "hello";
        String s3 = new String("hello");
        String s4 = new String("hello").intern();
        System.out.println("s1==s2 : " + (s1 == s2) + " | s1==s3 : " + (s1 == s3) + " | s1==s4 : " + (s1 == s4));
        System.out.println("s1.equals(s3) : " + s1.equals(s3));
    }
}
```

**Actual output (verified on JDK 25):**

```text
s1==s2 : true | s1==s3 : false | s1==s4 : true
s1.equals(s3) : true
```

**Dry run:**

| Variable | How it was created | Lives in | `s1 == it`? |
|----------|--------------------|----------|--------------|
| `s1` | literal | String pool | — |
| `s2` | literal (same constant) | String pool (same object) | `true` |
| `s3` | `new String(...)` | heap (a new copy) | `false` |
| `s4` | `new String(...).intern()` | pool (moved back) | `true` |

**Takeaway:** `==` on `String` is reference comparison; `.equals()` is content comparison. Literals
and `intern()`ed strings are pooled, so `==` happens to work for them — but a string built at
runtime (`sb.toString()`, a DB read, `String.valueOf(char[])`) is never pooled. Use `.equals()`,
or `.equalsIgnoreCase()` for case-insensitive comparison.

---

## 6. Code from Scratch — Production-Grade

A small, realistic, **dependency-free** domain model that applies the principles from this chapter:
rich behaviour, no `null`, no `static` globals, immutable value objects, and defensive copying.

```java
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An immutable value object. Every field is private final, the class is final
 * (so nobody can subclass it and add mutable state), and mutable inputs are
 * defensively copied on the way IN and OUT.
 */
final class Money implements Comparable<Money> {

    private final long amountMinorUnits;      // long, not double — no rounding drift
    private final String currency;             // ISO-4217, e.g. "INR"

    public Money(long amountMinorUnits, String currency) {
        this.amountMinorUnits = amountMinorUnits;
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    public long getAmountMinorUnits() { return amountMinorUnits; }
    public String getCurrency() { return currency; }

    /** Defensive copy OUT — the caller can never mutate our list. */
    public List<Money> asList() {
        return List.of(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;   // getClass() keeps symmetry
        Money other = (Money) o;
        return amountMinorUnits == other.amountMinorUnits
                && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amountMinorUnits, currency);   // SAME fields as equals()
    }

    @Override
    public String toString() {
        return "Money{amount=" + amountMinorUnits + ", currency='" + currency + "'}";
    }

    @Override
    public int compareTo(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot compare " + currency + " with " + other.currency);
        }
        return Long.compare(amountMinorUnits, other.amountMinorUnits);
    }
}

/**
 * A rich domain entity. Behaviour lives on the object ("tell, don't ask"),
 * there are no setters, and the state can only move forward through
 * explicit, validated transitions.
 */
final class Order {

    public enum Status { CREATED, PAID, SHIPPED, DELIVERED, CANCELLED }

    private final String orderId;
    private final List<Money> lines;          // defensive copy in the constructor
    private Status status = Status.CREATED;
    private Instant lastUpdated;

    public Order(String orderId, List<Money> lines) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one line");
        }
        this.lines = List.copyOf(lines);      // immutable copy — truly immutable
        this.lastUpdated = Instant.now();
    }

    /** Total is derived, never stored — it can never drift out of sync. */
    public Money total() {
        long sum = 0;
        for (Money line : lines) {
            if (!line.getCurrency().equals(lines.get(0).getCurrency())) {
                throw new IllegalStateException("Mixed currencies in order " + orderId);
            }
            sum += line.getAmountMinorUnits();
        }
        return new Money(sum, lines.get(0).getCurrency());
    }

    public Status getStatus() { return status; }
    public List<Money> getLines() { return lines; }   // already immutable — no copy needed

    /** Behaviour, not a setter: the invariant is enforced inside the object. */
    public void pay() {
        require(Status.CREATED, "pay");
        this.status = Status.PAID;
        this.lastUpdated = Instant.now();
    }

    public void ship(String trackingNumber) {
        require(Status.PAID, "ship");
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("trackingNumber is required");
        }
        this.status = Status.SHIPPED;
        this.lastUpdated = Instant.now();
    }

    public void cancel() {
        if (status == Status.SHIPPED || status == Status.DELIVERED) {
            throw new IllegalStateException("Cannot cancel a " + status + " order");
        }
        this.status = Status.CANCELLED;
        this.lastUpdated = Instant.now();
    }

    private void require(Status expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("Cannot " + action + " an order in state " + status);
        }
    }

    @Override
    public String toString() {
        return "Order{id='" + orderId + "', status=" + status + ", total=" + total() + "}";
    }
}
```

**Why this is production-grade:**

| Concern | How it is handled |
|---------|-------------------|
| Nullability | `Objects.requireNonNull` at every trust boundary |
| Immutability of value objects | `final` class, `private final` fields, `List.copyOf` |
| Derived state | `total()` is computed, never cached → cannot drift |
| State machine | `pay()`/`ship()`/`cancel()` validate the current state |
| No `static` mutable state | Nothing global; trivially testable and parallel-safe |
| `equals`/`hashCode` | Overridden together, `getClass()`-based, same fields |
| No leaking of internals | `getLines()` returns an immutable list, not the backing one |
| Money correctness | `long` minor units, not `double`; no floating-point money |

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Calling an overridable method from a constructor

```java
// ❌ WRONG — the subclass override runs before the subclass fields exist
class Base {
    Base() { init(); }              // ⚠️ dispatches to Derived.init()
    void init() { }
}
class Derived extends Base {
    int x = 5;
    @Override void init() { System.out.println(x); }   // prints 0, not 5!
}
```

```java
// ✅ RIGHT — make it final, or don't call overridable methods from a constructor
class Base {
    Base() { }                      // no call
    final void init() { }           // cannot be overridden
}
```

**WHY:** Java initialises the superclass *before* the subclass, but dispatch is always virtual.
The subclass's field initialisers have not run yet, so the override sees default values.

---

### ❌ Pitfall 2 — Using `==` for wrapper and String comparison

```java
// ❌ WRONG
if (userId == session.getUserId()) { }     // breaks for ids > 127
if (name == request.getName()) { }         // breaks for computed strings
```

```java
// ✅ RIGHT
if (Objects.equals(userId, session.getUserId())) { }
if (name.equals(request.getName())) { }
```

**WHY:** `==` compares references. The `Integer` cache (`-128..127`) and the String pool make it
*appear* to work for small/constant values — and then fail in production.

---

### ❌ Pitfall 3 — Exposing mutable state through a getter

```java
// ❌ WRONG
public Date getHireDate() { return hireDate; }        // caller mutates our state
public List<String> getTags() { return tags; }        // caller mutates our state
public int[] getScores() { return scores; }            // caller mutates our state
```

```java
// ✅ RIGHT — defensive copy out
public Date getHireDate() { return new Date(hireDate.getTime()); }
public List<String> getTags() { return List.copyOf(tags); }
public int[] getScores() { return scores.clone(); }
```

**WHY:** The getter hands out a reference to *our* object. Any mutation through it bypasses every
invariant the class enforces.

---

### ❌ Pitfall 4 — `static` mutable state

```java
import java.util.HashMap;
import java.util.Map;

// ❌ WRONG — global mutable state, shared across tests and threads
public class Cache {
    private static Map<String, Object> MAP = new HashMap<>();
}
```

```java
import java.util.Map;

// ✅ RIGHT — inject it, or make it a proper immutable value
public class Cache {
    private final Map<String, Object> map;   // instance state, DI-friendly
    public Cache(Map<String, Object> map) { this.map = map; }
}
```

**WHY:** `static` state is shared by every instance and every test in the JVM. It is invisible
coupling, it is not thread-safe by default, and it cannot be mocked.

---

### ❌ Pitfall 5 — A `static` method pretending to be an instance method

```java
// ❌ WRONG — the compiler warns, but it compiles
class Util {
    static String format(String s) { return s.trim(); }
    public static void main(String[] a) {
        String r = Util.format("  x  ");      // ⚠️ static access via class name
    }
}
```

**WHY:** `static` methods cannot be overridden, so they cannot participate in polymorphism. If you
need different behaviour per implementation, it must be an instance method.

---

### ❌ Pitfall 6 — Returning `null` from a "never null" API

```java
// ❌ WRONG — every caller must null-check
public String findEmail(long userId) {
    return repo.lookup(userId);      // may be null
}
```

```java
// ✅ RIGHT — make absence explicit
public Optional<String> findEmail(long userId) {
    return Optional.ofNullable(repo.lookup(userId));
}
```

**WHY:** `null` is a value that is not in the type system. The compiler cannot help you, and every
caller must remember to check.

---

### ❌ Pitfall 7 — Assuming `final` means immutable

```java
// ❌ WRONG
final class Config {
    private final List<String> servers = new ArrayList<>();
    void add(String s) { servers.add(s); }   // ❌ still mutable!
}
```

```java
// ✅ RIGHT
final class Config {
    private final List<String> servers;
    Config(List<String> s) { this.servers = List.copyOf(s); }
}
```

**WHY:** `final` freezes the *reference*, not the *object*.

---

### ❌ Pitfall 8 — Overriding `equals` without `hashCode`

```java
// ❌ WRONG
class User {
    String email;
    @Override public boolean equals(Object o) { /* ... */ }
    // no hashCode() -> identity hash -> HashMap lookups FAIL
}
```

**WHY:** `HashMap` uses `hashCode()` to pick a bucket and `equals()` to confirm. Equal objects with
different hash codes land in different buckets, so `get()` returns `null` even though the key is
present.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: Is Java a pure object-oriented language?**
A: No. Java has primitive types (`int`, `char`, `boolean`), `static` members that belong to the
class rather than an object, and the `null` reference — none of which exist in a pure-OOP language
like Smalltalk. Java is *primarily* object-oriented with pragmatic exceptions for performance.

**Q: What is polymorphism?**
A: The ability of a single reference type to point at different object types and have the correct
method invoked at runtime. In Java it has two forms: *subtype* polymorphism (a `Parent` reference
holding a `Child`, with overridden methods) and *parametric* polymorphism (generics).

**Q: Can you override a `static` method?**
A: No. A `static` method is bound to the class at compile time, so a subclass method with the same
signature *hides* it rather than overriding it. There is no runtime dispatch, so there is nothing
to override.

**Q: Is `final` required for a constant?**
A: `static final` is the convention for a true constant. The `static` makes it one copy per class
rather than per instance, and the `final` prevents reassignment. A `final` instance field is still
one value per object, so it is not a constant in the `static` sense.

### 🟡 Intermediate

**Q: What is the difference between `==` and `equals()`?**
A: `==` compares *references* (or primitive values), while `equals()` compares *logical content*.
For `String`, `Integer` and other wrappers, `==` is a trap because of the String pool and the
`Integer` cache — always use `equals()`.

**Q: Why must `hashCode()` be overridden together with `equals()`?**
A: Because `HashMap`/`HashSet` use `hashCode()` to select a bucket and `equals()` to confirm a
match within that bucket. The contract requires equal objects to have equal hash codes; if you
break it, lookups silently return `null` and duplicates appear in sets.

**Q: What is the difference between composition and inheritance?**
A: Inheritance is an IS-A relationship with tight coupling — a subclass is bound to its parent's
implementation. Composition is a HAS-A relationship where the component is held as a field and can
be swapped at runtime. Prefer composition: it avoids the fragile base class problem and is more
flexible.

**Q: Why can a subclass not be used in place of its parent?**
A: That is the Liskov Substitution Principle. A subclass must honour every guarantee the parent
made. `Square extends Rectangle` breaks it because a `Square` cannot honour independent width and
height, so code written against `Rectangle` breaks when given a `Square`.

**Q: How does Java decide which overloaded method to call?**
A: At compile time, using the compile-time types of the arguments (widening beats boxing beats
varargs). The chosen overload is fixed in the bytecode — it is not re-evaluated at runtime.

### 🔴 Advanced / Senior

**Q: Explain the "fragile base class" problem.**
A: When a base class changes, every subclass may break — even ones that never used the changed
member. This happens because subclasses depend on the base class's *implementation details* (protected
fields, package-private methods), not just its contract. The mitigations are: keep `protected` surface
small, prefer composition, and use `final` to close extension points you do not want changed.

**Q: What actually happens when a `HashMap` resolves a key?**
A: `HashMap` computes `hash(key)` = `h ^ (h >>> 16)` where `h` is the key's `hashCode()`, then
indexes the table with `hash & (n-1)`. If that bucket is empty the key is inserted; otherwise the
bucket's linked list (or tree, past TREEIFY_THRESHOLD = 8) is walked, calling `equals()` to find a
match. This is why `hashCode()` and `equals()` must agree.

**Q: Why is `final` a better design tool than `private` for closing an API?**
A: `private` hides a member from subclasses entirely, which prevents reuse. `final` keeps the member
visible and usable but makes it impossible to *override*, so the behaviour is fixed while the code
can still be shared. It also documents "this is the complete implementation" and enables JIT
devirtualisation.

**Q: When would you use a `static` method, and what is the risk?**
A: Use it for pure functions with no state dependence (factory methods, value converters,
constants). The risk is that it becomes a global dependency that is invisible at the call site,
cannot be overridden, and cannot be injected — so it is untestable in isolation. If a `static` method
needs to reach out to a database, that is a design smell.

**Q: How would you diagnose a `NullPointerException` in production?**
A: Read the stack trace to the exact line; if the message is unhelpful (Java 14+ has
`NullPointerException` "helpful messages" that name the null variable), use it. Then check whether
the value came from a map lookup, a deserialised field, or a getter that returns `null`. Fix the
*cause* — a null-safe API (`Optional`, Null Object) — rather than sprinkling `if (x != null)`
everywhere.

### ⚫ Expert / Principal

**Q: Argue both sides: is `null` actually that bad?**
A: The counter-argument is that `null` is cheap, universal, and the JIT handles it well; languages
without it often need sum types or an `Option` type with a runtime cost. The pro-argument is that
`null` is not a *value* — it is the absence of one — and it breaks the type system's ability to
distinguish "not set" from "unknown" from "explicitly empty". My position: `null` is acceptable
*internally* but should be eliminated at *boundaries* (public API returns, message payloads), where
`Optional` or a sealed type costs almost nothing and buys real safety.

**Q: How do you decide between an interface, an abstract class, and a sealed hierarchy?**
A: Interface when there is no shared state or code and the implementors are unrelated — maximum
loose coupling. Abstract class when there is genuine shared state or shared implementation to
reuse. Sealed hierarchy when the set of subtypes is *known and closed* and you want the compiler to
exhaustively check your `switch` — that is the modern answer to the "tagged union with a String
discriminator" anti-pattern, and it makes illegal states unrepresentable.

**Q: What is your position on the `Optional` as a field or parameter type?**
A: I do not allow it. `Optional` is a return-type idiom that signals "the caller must handle
absence". As a field it adds an allocation and a wrapper for a value that is already nullable, and
as a parameter it forces every caller to wrap a value, which adds noise without adding safety.
For fields, prefer `null` plus a clear invariant, or a sealed type that models absence as a
variant. `Optional` as a parameter is almost always a design smell.

**Q: How do you keep OOP design from decaying in a large codebase?**
A: Three levers. First, make the *boundaries* enforce the design: package-private constructors,
`sealed` types, and immutable value objects make illegal states unrepresentable rather than
documented. Second, keep the dependency direction one-way — depend on interfaces you own, never on
concrete framework classes. Third, use static analysis (ArchUnit, Error Prone, forbidden-apis) so
that architectural rules fail the build instead of failing a code review. Design rules that live only
in a wiki are already broken.

**Q: How do you explain the trade-off between immutability and allocation cost?**
A: Immutable objects remove an entire class of bugs (races, torn state, aliasing) and make values
safe to share and cache. The cost is allocation and, historically, extra copying for defensive
copies. Modern JVMs largely defuse the cost: escape analysis can stack-allocate a non-escaping
object, and `List.copyOf` on small lists is cheap. The right default is immutability for values and
mutability only where identity or large-scale mutation genuinely requires it — and even then,
encapsulate the mutable part behind a boundary.

---

## 9. Counter-Questions & Traps

### CQ1. "Is Java 100% object-oriented? That's a trick question, isn't it?"

- **Naive answer:** "No, because of primitives and `static`."  ← correct but shallow
- **The trap:** The interviewer is fishing for *why* Java made that trade-off. Answering "Java is
  90% OOP" is the meme answer and scores zero.
- **Expert answer:** "No. Primitives, `static` members and `null` all break pure OOP. The reason is
  *performance* — a pure-OOP `int` would be a heap object with a 16-byte header, so an int loop
  would allocate 16 MB of garbage instead of keeping values in registers. Java is pragmatic: it
  keeps primitives for speed and bridges them to the object world with auto-boxing. Note that
  Smalltalk, the archetypal pure-OOP language, is essentially unused in production for exactly
  this reason."

### CQ2. "You said use `Optional` instead of `null`. Isn't that just wrapping `null`?"

- **Naive answer:** "Yes, `Optional` makes null handling explicit."  ← incomplete
- **The trap:** `Optional` is often criticised as a wrapper that adds an allocation and does not
  actually eliminate `null` from the language. An interviewer who has heard that critique wants to
  hear whether you understand the *limits*.
- **Expert answer:** "It does wrap `null`, and it does not remove `null` from Java. Its value is
  *type-level* — absence becomes a value in the signature, so the compiler forces the caller to
  handle it, and `Optional` has no meaningful `==` semantics to misuse. But it is a *return-type*
  idiom only: as a field or parameter it adds noise and an allocation without adding safety. The
  strongest answer is to make absence unrepresentable — a sealed interface with an
  `Empty`/`Present` variant, or a domain type that simply cannot be null."

### CQ3. "Is `final` just about constants?"

- **Naive answer:** "`final` means a value can't be reassigned."  ← true but incomplete
- **The trap:** `final` on a *class* or *method* has nothing to do with constants, and `final` on a
  reference field does **not** make the object immutable. A candidate who equates `final` with
  "immutable" will get the classic `final List` trap wrong.
- **Expert answer:** "`final` has three meanings: a final *field* cannot be reassigned, a final
  *method* cannot be overridden, a final *class* cannot be extended. Critically, a final reference
  field freezes the *reference*, not the referenced object — `private final List<String> l` is still
  mutable through `l.add(...)`. That is exactly why immutability needs `final` **plus** defensive
  copies. The real value of `final` on methods and classes is *design*: it closes an extension point
  so the behaviour cannot change, which protects invariants and lets the JIT devirtualise."

### CQ4. "If `final` enables devirtualisation, should I mark everything `final`?"

- **Naive answer:** "Yes, it makes the code faster."  ← wrong reasoning
- **The trap:** This leads to marking the entire codebase `final` and destroying extensibility for
  no measurable benefit. The JIT already does class-hierarchy analysis and can inline non-final
  methods when it can prove no subclass overrides them.
- **Expert answer:** "No — `final` is a *correctness* tool that happens to enable optimisation, not
  an optimisation switch. The JIT inlines non-final methods too, using CHA (class hierarchy
  analysis) and profile data; it only needs a guard to deoptimise if a new subclass appears. So the
  performance argument for blanket-`final` is largely obsolete with modern JITs. Use `final` where
  it expresses intent: value objects, framework extension points, and security-sensitive methods —
  and never at the cost of destroying legitimate extension."

### CQ5. "Doesn't `equals`/`hashCode` just handle value comparison? Why is it so hard?"

- **Naive answer:** "`equals` compares fields, `hashCode` buckets them."  ← correct but misses the trap
- **The trap:** The interviewer is about to ask about **inheritance**, and the `getClass()` vs
  `instanceof` decision is a genuine design fork with no universally right answer.
- **Expert answer:** "The hard part isn't the field comparison, it's the *contract under
  inheritance*. With `getClass()`, `Employee` and `Manager` are never equal, and symmetry is
  guaranteed — but a subclass can never be equal to its parent. With `instanceof`, symmetry breaks
  unless the subclass also uses `instanceof` in the same direction, which you cannot enforce. The
  standard solutions are: make the class `final` (then `instanceof` is safe and simple), or use the
  `canEqual` pattern from Effective Java, where the subclass grants permission explicitly. In
  practice, most value objects should be `final` and use `getClass()`."

### CQ6. "Why not just use inheritance everywhere? Composition is more code."

- **Naive answer:** "Inheritance is simpler."  ← the trap the interviewer wants you to fall into
- **The trap:** This is a design-quality trap. The interviewer is testing whether you know *when*
  inheritance is right, not whether you can recite a rule.
- **Expert answer:** "Inheritance is right for a genuine IS-A relationship with a stable base and
  shared behaviour — that's what it is for, and Spring's whole design leans on it. It's wrong for
  HAS-A, and it's wrong whenever the relationship might vary at runtime. The cost of composition is
  a few extra lines of delegation; the cost of wrong inheritance is the fragile base class problem,
  which shows up as a breaking change in a subclass you did not even know existed. 'Favour
  composition over inheritance' is a heuristic, not a law — 'favour composition when in doubt' is
  the correct reading."

### CQ7. "How would you find out that a `HashMap` lookup is failing in production?"

- **Naive answer:** "Check the `equals` implementation."  ← good but stops early
- **The trap:** This is an *observability* question. The interviewer wants to know if you can
  diagnose a data-dependent failure rather than just guess at the cause.
- **Expert answer:** "First, establish the failure mode: does `get()` return `null` for a key that
  was definitely put? That points to the hashCode/equals contract or to a **mutated key** — if the
  key's fields changed after insertion, its hash changed and it now sits in a bucket nothing will
  search. Second, check whether the key type overrides `equals` but not `hashCode`. Third, look for
  a `BigDecimal` scale mismatch, or a `double`-vs-`Double` mismatch. The generalisable lesson: a
  key used in a hash-based collection must be *immutable* while it is in the collection, and its
  `equals`/`hashCode` must be consistent."

### CQ8. "You said Java has no destructor. But what about `finalize()`?"

- **Naive answer:** "`finalize()` is the destructor, but it's deprecated."  ← close, but misses why
- **The trap:** Saying only "it's deprecated" leaves the interviewer to ask *why*, and the real
  reasons are more interesting than "the JEP said so".
- **Expert answer:** "`finalize()` was never a destructor and never should have been relied on. It
  runs at an unpredictable time — possibly never, if the object becomes unreachable while the
  finalizer is pending — it can resurrect the object, it runs on the finalizer thread with its own
  performance cost, and it is not called at all for resources like open streams that matter. It was
  deprecated in Java 9 and removed for removal in Java 18. The correct mechanism is
  `try-with-resources` for anything `AutoCloseable`, and `Cleaner` (Java 9+) as a last-resort
  safety net for native resources that cannot be closed deterministically."

## 10. Rapid-Fire MCQs

### Q1
What does this print?

```java
class A { A() { System.out.println("A"); init(); } void init() { System.out.println("A.init"); } }
class B extends A { int x = 5; B() { super(); System.out.println("B x=" + x); }
                    @Override void init() { System.out.println("B.init x=" + x); } }
new B();
```

- **A)** `A`, `A.init`, `B x=5`
- **B)** `A`, `B.init x=0`, `B x=5`
- **C)** `A`, `B.init x=5`, `B x=5`
- **D)** Compile error

### Q2
```java
Integer a = 1000, b = 1000;
System.out.println(a == b);
```

- **A)** `true`
- **B)** `false`
- **C)** Compile error
- **D)** `true` only if `-XX:AutoBoxCacheMax` is raised

### Q3
Which method CANNOT be overridden?

- **A)** `public void run()`
- **B)** `protected void run()`
- **C)** `final void run()`
- **D)** `void run()`

### Q4
A class implements `equals` but not `hashCode`. What happens?

- **A)** `HashMap` lookups return `null` for equal keys
- **B)** `HashMap` throws `NullPointerException`
- **C)** Compile error
- **D)** Everything works, `hashCode` is auto-derived

### Q5
```java
class P { static void f() { System.out.println("P"); } }
class C extends P { static void f() { System.out.println("C"); } }
P p = new C();
p.f();
```

- **A)** Prints `C`
- **B)** Prints `P`
- **C)** Compile error
- **D)** Runtime `ClassCastException`

### Q6
Which is **true** about `static` methods?

- **A)** They can be overridden
- **B)** They are bound to the reference type at compile time
- **C)** They cannot be called on an instance
- **D)** They are always thread-safe

### Q7
`private final List<String> items = new ArrayList<>();` — is the object immutable?

- **A)** Yes, `final` makes it immutable
- **B)** No, but it is thread-safe
- **C)** No, the list contents can still be mutated
- **D)** Only if the class is `final`

### Q8
`Object[] o = new String[10]; o[0] = 42;` — what happens?

- **A)** Compiles and runs
- **B)** `ArrayStoreException` at runtime
- **C)** Compile error
- **D)** `ClassCastException` at runtime

### Q9
The `Integer` cache guarantees that `Integer.valueOf(x)` returns the same object for:

- **A)** All values
- **B)** `-128` to `127`
- **C)** `0` to `127`
- **D)** Only values `> 127`

### Q10
Which statement about `final` is **correct**?

- **A)** `final` fields are guaranteed visible to other threads without `volatile`
- **B)** `final` methods are always inlined by the JIT
- **C)** A `final` reference prevents reassignment but not mutation of the object
- **D)** `final` classes can be extended by nested classes

### Q11
```java
interface I { void m(); }
class A implements I { public void m() { System.out.println("A"); } }
I i = new A();
i.m();
```

- **A)** Prints `A` — resolved at compile time
- **B)** Prints `A` — resolved at runtime
- **C)** Compile error
- **D)** Prints `null`

### Q12
What does `super.toString()` inside a subclass `toString()` enable?

- **A)** Calling an overridable method safely from a constructor
- **B)** Bypassing the override to get the parent's implementation
- **C)** Making the class immutable
- **D)** Enabling the `instanceof` pattern

### Answer Key

| # | Answer | Explanation |
|---|--------|-------------|
| 1 | **B** | `super()` runs first; `init()` dispatches virtually to `B.init()` but `x` is still `0` because B's field initialiser has not run. |
| 2 | **B** | The `Integer` cache covers only `-128..127`; `1000` is outside it, so two distinct objects are created. |
| 3 | **C** | `final` methods cannot be overridden. `private` and `static` are also non-overridable but are not "overridden" in the first place. |
| 4 | **A** | `HashMap` uses `hashCode()` to pick the bucket; different hash codes mean the key is never found. |
| 5 | **B** | `static` methods are bound to the *reference* type (`P`) at compile time — this is hiding, not overriding. |
| 6 | **B** | Static methods are compile-time bound to the reference type; they can also be called via an instance (with a warning), and they are not automatically thread-safe. |
| 7 | **C** | `final` freezes the reference only. `items.add(...)` still works — you need `List.copyOf` for real immutability. |
| 8 | **B** | Arrays are covariant, so the JVM performs a runtime store check and throws `ArrayStoreException`. |
| 9 | **B** | The JLS specifies a cache of at least `-128..127`; a JVM may cache more, so you must never rely on the range. |
| 10 | **C** | `final` prevents reassignment of the reference; it says nothing about the referenced object's internal state. |
| 11 | **B** | Interface methods are dispatched at runtime via `invokeinterface` — the receiver's actual class wins. |
| 12 | **B** | `super.toString()` compiles to `invokespecial`, which skips virtual dispatch and calls the parent's implementation directly. |

---

## 11. Cheat Sheet / One-Page Summary

### Quick Reference: Tricky OOP Questions

| Question | Answer |
|----------|--------|
| Is Java 100% OOP? | No (primitives, static, null) |
| Polymorphism without inheritance? | Yes (interfaces, generics) |
| All methods virtual in Java? | Yes (except static, final, private) |
| Java has destructors? | No (use try-with-resources) |
| Constructors inherited? | No (must call super()) |
| Override final method? | No (compile error) |
| Override private method? | No (not visible to child) |
| Override static method? | No (method hiding, not overriding) |
| Can abstract class have constructor? | Yes |
| Can interface have constructor? | No |
| `final` = immutable? | **No** — only the reference is frozen |
| `==` on `Integer`/`String`? | **No** — use `equals()` |
| `equals` without `hashCode`? | **Broken** — `HashMap` lookups fail |
| `getClass()` or `instanceof`? | `getClass()` for symmetry; `instanceof` for substitutability |
| Call overridable method in ctor? | **Never** — subclass fields are still default |
| `Optional` as a parameter? | Design smell — use for return types |
| Best Singleton? | Enum, or an injected singleton bean |
| `Collections.unmodifiableList` vs `List.copyOf`? | The former is a **view**, the latter a **copy** |

### The Five Mechanisms — Quick Recall

| Mechanism | Compile time or Runtime? | Decided by |
|-----------|--------------------------|------------|
| Overload resolution | Compile time | Argument types + reference type |
| Override dispatch | Runtime | Receiver's actual class |
| `static` method binding | Compile time | Reference type |
| `final` constant inlining | Compile time | javac |
| `instanceof` / `getClass()` | Runtime | Object header |

### Object Lifecycle Order

```text
Class init (once):      superclass → static fields → static blocks
Instance (each `new`):  super fields+blocks → super ctor → sub fields+blocks → sub ctor
```

---

## 12. Real-World Production Notes

### Where These Come Up in Banking / E-Commerce Systems

| Scenario | Where it bites | Mitigation |
|----------|----------------|------------|
| Mutable key in a `HashMap` | Caching a `Cart` keyed by `customerId` where the ID is assigned after insert | Use immutable keys; or `HashMap` of `String` id → value |
| `Integer` `==` | `if (orderId == session.getOrderId())` with real DB ids | `Objects.equals(...)` everywhere; add a static-analysis rule |
| Leaked mutable `Date` | `getHireDate()` returning a live `Date` — audit log shows wrong dates | Return `java.time` types; they are immutable |
| `static` cache | A `static Map` cache in a service — unbounded growth, cross-test pollution | Bounded `Caffeine`/`Guava` cache, injected, with TTL and max size |
| Constructor dispatch | A framework calling an overridable method during construction → NPE in prod only | Make the method `final`, or use a factory that calls `init()` explicitly |
| Anemic domain | Business rule for "discount above ₹50,000" duplicated in 3 services | Push the rule into the domain object; add a rule-level unit test |
| `null` in a JSON API | A missing field deserialises to `null` and NPEs downstream | `Optional` at the boundary, or a `record` with non-null components |

### Tuning Knobs & Metrics

- **Static analysis is the highest-leverage control here.** Error Prone / SpotBugs rules catch
  `==` on wrappers, `equals` without `hashCode`, and `static` mutable state *at build time*.
- **ArchUnit** can enforce "no class in `..service..` may depend on `..util..`" — turning the
  `static`-state rule into a build failure rather than a review comment.
- **JITWatch / `-XX:+PrintCompilation`** can show whether a `final` method is actually being
  inlined, if you are ever asked to justify the optimisation claim.
- **Micrometer metric worth adding:** a counter for `NullPointerException` caught at a boundary —
  it turns "nulls are bad" into an observable SLO.

### The Design Rules Worth Enforcing Across a Team

1. Domain entities are **not** data bags — behaviour lives on the object.
2. Values crossing a boundary are **immutable**; entities that must mutate are **encapsulated**.
3. No `static` mutable state outside a deliberate, bounded, injected cache.
4. `equals` and `hashCode` are overridden **together**, or not at all.
5. A key in a hash-based collection is **immutable** for its whole residency.
6. Constructors do not call overridable methods.
7. `null` is allowed internally; the **public API** returns `Optional` or a non-null default.

---

## 13. Follow-up Learning

### Next in this folder

- [07 · Keywords: final, static, volatile, transient](./07-OOP-Keywords-final-static-volatile-transient.md) — the modifier-level detail behind `final` and `static` in this chapter
- [08 · equals / hashCode / toString](./08-OOP-equals-hashCode-toString.md) — the full contract, in depth
- [09 · Immutable Classes](./09-OOP-Immutable-Classes.md) — how to actually build the immutable values recommended here
- [13 · Covariance, Contravariance, Invariance](./13-OOP-Covariance-Contravariance-Invariance.md) — why `List<Dog>` is not a `List<Animal>`

### Related topics elsewhere

- [Java Memory Model](../02-Java-Memory-Model/01-Memory-Areas.md) — what `volatile` and `final` mean to the JMM
- [Design Patterns](../09-Design-Patterns/01-Design-Patterns.md) — Strategy, Null Object, Singleton
- [SOLID Principles](../10-SOLID-Principles/01-SOLID-Principles.md) — LSP, SRP, DIP in depth
- [Serialization](../17-Serialization/01-Serialization-Basics.md) — `Serializable` contracts and deserialisation risk
- [Testing](../18-Testing/01-JUnit-Basics.md) — making `static` state testable

### Books and specifications

- **Effective Java** (Joshua Bloch) — Items 10–13 (`equals`/`hashCode`/`toString`/`clone`), 17–19 (value-based classes), 22–23 (static members)
- **JLS Chapter 8** — nested classes; **Chapter 12** — class initialisation; **Chapter 17** — threads and `final` field semantics
- **JLS §15.12.3** — method invocation, overload vs override
- **JVM Specification §5.4.3.3** — method resolution and dynamic dispatch

---

**Next Chapter:** → [13 · Covariance, Contravariance and Invariance](./13-OOP-Covariance-Contravariance-Invariance.md)
**Previous Chapter:** ← [11 · Advanced Design and the Object Class](./11-OOP-Advanced-Design-and-Object-Class.md)

*Last Updated: 2026 · Java 17+ (JDK 21/25 verified) · All outputs in this chapter were produced by running the code*
