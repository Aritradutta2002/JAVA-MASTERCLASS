# SOLID Principles — The Complete Interview Guide

> **SOLID is not a set of rules to obey — it is a set of *forces* to balance.**
> The interview is never "what does SRP mean?". It is: *"here is a class that
> violates three of these; refactor it and defend every step."*

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

- **SOLID** was coined by Robert C. Martin (Uncle Bob) in 2000. Five principles: **S**ingle Responsibility, **O**pen/Closed, **L**iskov Substitution, **I**nterface Segregation, **D**ependency Inversion.
- **SRP** — a class should have **one reason to change**. Not "one responsibility" (vague) and **not** "one method" (wrong). The test: *if you can describe the class in one sentence without "and", you are probably there.*
- **OCP** — open for **extension**, closed for **modification**. Adding a new payment provider should not mean editing the `switch` in your service.
- **LSP** — a subtype must be **behaviourally substitutable** for its parent. If `ArrayList.add(int, E)` exists, then a `Stack extends ArrayList` is broken.
- **ISP** — prefer **small, client-driven interfaces** over one fat interface. A client should not be forced to implement methods it does not use.
- **DIP** — high-level modules should **not** depend on low-level modules; both should depend on abstractions. In practice: **depend on interfaces, and inject them.**
- **The 5 are not independent.** DIP + ISP together produce many small interfaces; OCP is the *goal* that SRP, LSP and ISP *enable*.
- **SOLID is not "more interfaces = better."** Over-applying ISP produces 20 one-method interfaces and a readability disaster. The principles are *tension-balancing tools*, not maximisation targets.
- **Every SOLID violation has a runtime symptom**: SRP → a change breaks unrelated things; OCP → a new requirement means editing a shared file (merge conflicts); LSP → a `ClassCastException` or a surprising `UnsupportedOperationException`; ISP → an `UnsupportedOperationException` in a client; DIP → untestable code and framework lock-in.

---

## 1. What & Why — The Problem This Solves

**The problem.** Software changes. Requirements change faster than the code. The question SOLID answers is: **when a requirement changes, how much of the codebase must change with it?**

```text
   ❌ THE "GOD CLASS" PROBLEM
   ──────────────────────────
   class OrderService {
       // ... 60 methods ...
       validateOrder()
       calculateTax()
       applyDiscount()
       chargeCard()
       chargePayPal()
       sendEmail()
       sendSms()
       saveToDatabase()
       updateInventory()
       logToFile()
       generateInvoice()
       publishToKafka()
       // ...
   }

   Requirement: "add a new tax jurisdiction (UAE VAT)"
   → you must OPEN this class and EDIT it
   → 6 other teams also edit this class
   → merge conflicts, and a tax change can break the email code
```

**The solution.** SOLID distributes the reasons to change across many small, focused, substitutable pieces, so a new requirement adds *new code* rather than *modifying shared code*.

```text
   ✅ AFTER
   ────────
   OrderService        → orchestrates only
   TaxCalculator       → tax rules
   PaymentStrategy     → payment rules (Strategy pattern)
   NotificationChannel → notifications (Observer/Strategy)
   OrderRepository     → persistence (interface + impl)

   Requirement: "add UAE VAT"
   → create UaeVatCalculator  → register it   → NOTHING EXISTING CHANGED
```

> 💡 **The single most important reframe:** SOLID is not about *writing more classes*.
> It is about **minimising the number of existing files you must touch** when a
> requirement changes. That is the metric. "How many files do I edit to add a tax
> jurisdiction?" is the question SOLID is trying to reduce.

---

## 2. Mental Model — The One Picture

```text
   ┌─────────────────────────────────────────────────────────────────────────┐
   │                        THE FIVE PRINCIPLES                             │
   ├──────────┬──────────────────────────────────────────────────────────────┤
   │ S        │ ONE REASON TO CHANGE            →  focus                     │
   │ SRP      │   "one reason" (not "one method", not "one job")             │
   ├──────────┼──────────────────────────────────────────────────────────────┤
   │ O        │ EXTEND, DON'T MODIFY            →  extensibility            │
   │ OCP      │   new behaviour = new code, not edited code                 │
   ├──────────┼──────────────────────────────────────────────────────────────┤
   │ L        │ SUBSTITUTABLE                    →  correctness              │
   │ LSP      │   a child must honour the parent's contract                 │
   ├──────────┼──────────────────────────────────────────────────────────────┤
   │ I        │ SMALL, CLIENT-DRIVEN INTERFACES →  flexibility             │
   │ ISP      │   no client forced to implement what it doesn't use          │
   ├──────────┼──────────────────────────────────────────────────────────────┤
   │ D        │ DEPEND ON ABSTRACTIONS           →  testability & flexibility│
   │ DIP      │   the arrow of dependency points INWARD                      │
   └──────────┴──────────────────────────────────────────────────────────────┘
```

### The dependency-direction picture (DIP made visual)

```text
   ❌ WITHOUT DIP — the arrow points the WRONG WAY
   ─────────────────────────────────────────────────
        ┌──────────────────────┐
        │      OrderService    │  (high level)
        │                      │
        │  new MySqlOrderRepo()│  ← it CREATES the low-level detail
        └──────────┬───────────┘
                   │ depends on
                   ▼
        ┌──────────────────────┐
        │   MySqlOrderRepo     │  (low level)
        │   (JDBC, SQL, …)     │
        └──────────────────────┘

   → OrderService is now untestable without a real MySQL
   → swapping the DB means editing OrderService


   ✅ WITH DIP — BOTH point at the abstraction
   ───────────────────────────────────────────
        ┌──────────────────────┐        ┌──────────────────────┐
        │      OrderService    │        │   MySqlOrderRepo     │
        │                      │        │                      │
        │   OrderRepository    │◄───────┤  implements          │
        └──────────┬───────────┘        └──────────┬───────────┘
                   │ implements                    │ implements
                   ▼                              ▼
        ┌──────────────────────────────────────────────────────┐
        │              interface OrderRepository              │
        │              ◄── THE ABSTRACTION                    │
        └──────────────────────────────────────────────────────┘

   → OrderService depends on the interface; the repo depends on the interface
   → the ARROW OF DEPENDENCY points INWARD, toward the stable core
```

**What to look at:** in the good version, the **abstraction lives with the high-level module** (the one that *uses* it), not with the low-level implementation. That is DIP's most-often-missed detail.

---

## 3. Deep Dive — Theory

## 3.1 SRP — Single Responsibility Principle

**Definition.** A class should have **one reason to change** — one *axis* of change.

**The three wrong readings of SRP:**

| Wrong reading | Why it is wrong |
|---|---|
| "One class = one method" | Nonsense. A value object legitimately has many accessors. |
| "One class = one responsibility" | Vacuous. Every class has *some* responsibility. |
| "One class = one job" | Better, but still ambiguous. What is a "job"? |

**The correct reading:** identify the **actors** who will ask you to change the class.

```text
   class Report {
       generateReport()      ← changed by: the business (new report type)
       saveToDatabase()      ← changed by: the DBA (new storage engine)
       sendEmail()           ← changed by: the ops team (new channel)
       compressImage()       ← changed by: the design team (new format)
   }

   FOUR different actors → FOUR different reasons to change
   → FOUR reasons to change → SRP is VIOLATED

   The fix is not "split into 4 classes" by method.
   The fix is "group by REASON TO CHANGE":
       ReportGenerator  (business changes)
       ReportRepository (DBA changes)
       ReportNotifier   (ops changes)
       ImageCompressor  (design changes)
```

**The smell test:**

```java
// A class whose name needs "and" to describe
class OrderProcessorAndNotifier { }   // 🚩 two responsibilities

// A class described by a sentence with "and":
//   "It processes orders and sends emails."   🚩 two reasons to change
```

**The "reasons to change" question in an interview:**

> *"If the marketing team wants a new email template, which files do you open?"*
> If the answer is "the `OrderService`", SRP is violated. The answer should be
> "only the `OrderNotifier` / `EmailTemplate`" — and if the answer is "only
> `OrderService` because it's a one-line change", ask the *next* question:
> *"what if a new requirement comes along for the payment flow at the same time?"*

## 3.2 OCP — Open/Closed Principle

**Definition.** Software entities should be **open for extension** but **closed for modification**.

```text
   ❌ CLOSED FOR EXTENSION — every new type means editing the switch
   ────────────────────────────────────────────────────────────────
   class InvoiceService {
       BigDecimal calculate(Invoice invoice) {
           switch (invoice.getType()) {
               case DOMESTIC:  return calculateDomestic(invoice);
               case INTERNATIONAL: return calculateInternational(invoice);
               // adding a type → EDIT this method → every team conflicts
           }
       }
   }

   ✅ OPEN FOR EXTENSION — the new type is NEW code
   ────────────────────────────────────────────────────────────────
   interface TaxStrategy { BigDecimal tax(Invoice invoice); }

   class DomesticTaxStrategy  implements TaxStrategy { ... }
   class InternationalTaxStrategy implements TaxStrategy { ... }
   class UaeVatTaxStrategy implements TaxStrategy { ... }   ← NEW, no edit

   class InvoiceService {
       private final TaxStrategy strategy;
       InvoiceService(TaxStrategy strategy) { this.strategy = strategy; }
       BigDecimal calculate(Invoice invoice) { return strategy.tax(invoice); }
   }
```

**Why OCP matters in practice — the "stable module" argument:**

> *A module is "closed" when every other module can depend on it without being affected
> by changes to it.* — Robert C. Martin

The **practical symptom** of violating OCP is **merge conflicts and regression risk**: a file that everyone edits is a file where a change can break unrelated behaviour.

**OCP is the GOAL; the other principles SERVE it:**

```text
   SRP  ──▶ each class has one reason to change  ──┐
   LSP  ──▶ subclasses are safely substitutable   ──┼──▶  adding a new behaviour
   ISP  ──▶ interfaces are small & focused         ──┘     does NOT require
   DIP  ──▶ dependencies are injected & abstract         modifying existing code
```

**When OCP is deliberately violated:** performance-critical inner loops (a `switch` is measurably faster than a megamorphic virtual call), and tiny, provably-stable code. The trade is explicit and should be commented.

## 3.3 LSP — Liskov Substitution Principle

**Definition.** If `S` is a subtype of `T`, then objects of type `T` may be **replaced** with objects of type `S` **without altering the correctness of the program**.

**The formal test:** the subtype must honour the parent's **preconditions** (it must not demand more), its **postconditions** (it must guarantee at least as much), and it must not **strengthen** the parent's exceptions.

```text
   ┌──────────────────────────────────────────────────────────────────────┐
   │  THE CONTRACT HAS THREE PARTS                                       │
   │                                                                      │
   │  PRECONDITIONS   what the caller must guarantee before calling     │
   │                  the child may NOT demand MORE than the parent       │
   │                                                                      │
   │  POSTCONDITIONS  what the method guarantees after returning         │
   │                  the child must guarantee AT LEAST as much            │
   │                                                                      │
   │  EXCEPTIONS     the child must not throw NEW unchecked exceptions   │
   └──────────────────────────────────────────────────────────────────────┘
```

**The canonical violations:**

```java
// ❌ VIOLATION 1 — the child STRENGTHENS the precondition
class ArrayList<E> {
    void add(int index, E element) { ... }     // any index is fine
}
class Stack<E> extends ArrayList<E> {          // ❌ Stack is-a ArrayList? NO
    @Override public void add(int index, E e) {
        throw new UnsupportedOperationException();   // 💥 breaks the contract
    }
}
// A caller holding a `Stack` and calling add(0, x) gets a runtime exception.
// The Liskov Substitution Principle says: it must not.

// ❌ VIOLATION 2 — the child WEAKENS the postcondition
class Account {
    BigDecimal getBalance() { return balance; }         // never null
}
class SavingsAccount extends Account {
    @Override public BigDecimal getBalance() { return null; }  // ❌ now it can be null
}

// ❌ VIOLATION 3 — the child throws a NEW unchecked exception
class Rectangle {
    void setWidth(double w) { ... }          // never throws
}
class Square extends Rectangle {
    @Override public void setWidth(double w) {
        super.setHeight(w);                  // ❌ also changes the height — surprising!
    }
}
```

**The most common real-world LSP violation: mutable collections exposed as `List`.**

```java
List<String> list = new ArrayList<>();
list.add("a");
list.set(0, "b");

List<String> fixed = List.of("a");
fixed.set(0, "b");     // 💥 UnsupportedOperationException
```

A caller that only knows "this is a `List`" must be able to `set` it. `List.of` breaks that contract. This is why **`List.of` and `List.copyOf` are not full substitutes for `ArrayList`** — and why `Arrays.asList` (which *does* support `set` but not `add`) is also a partial substitute.

**The "sneaky" LSP violation that reviewers miss:**

```java
// ❌ The interface promises a LIST, but the implementation is a SET
public interface ShoppingCart extends List<Item> { }    // 😱
public class SetBackedCart extends HashSet<Item> implements ShoppingCart { }
// → the `List` contract says "duplicates allowed, ordered" — a Set violates both
```

**How to check LSP in practice:** ask *"can a client written against the parent type be given the child instance, with no change and no surprise?"* If the answer is no, the hierarchy is wrong.

## 3.4 ISP — Interface Segregation Principle

**Definition.** No client should be forced to depend on methods it does not use.

```text
   ❌ WITHOUT ISP — the fat interface
   ────────────────────────────────
   interface Worker {
       void work();
       void eat();            // 💥 a robot worker does not eat
       void sleep();          // 💥 a robot worker does not sleep
       void drinkCoffee();    // 💥 and a robot certainly does not drink coffee
   }

   class Robot implements Worker {
       public void work() { ... }
       public void eat() { throw new UnsupportedOperationException(); }  // 💥
       public void sleep() { throw new UnsupportedOperationException(); }// 💥
       public void drinkCoffee() { throw new UnsupportedOperationException(); }
   }

   → the CLIENT that only wants to work now depends on eat/sleep/drinkCoffee
   → LSP is violated too, and ISP is violated

   ✅ WITH ISP — client-driven segmentation
   ─────────────────────────────────────────
   interface Workable { void work(); }
   interface Feedable { void eat(); void drinkCoffee(); }
   interface Sleepable { void sleep(); }

   class Robot implements Workable { public void work() { ... } }        // ✅ clean
   class Human implements Workable, Feedable, Sleepable { ... }
```

**The key insight: ISP is *client-driven*.** You do not segment by the *implementation's* convenience; you segment by what each **client** actually calls.

```text
   ❌ "The implementation has 3 groups of methods, so make 3 interfaces"
      → ❌ WRONG — segment by CLIENT usage

   ✅ "These three CLIENTS use disjoint subsets of methods, so give them
      three interfaces"                                → ✅ RIGHT
```

**Why the fat interface is a *runtime* problem, not just a design smell:**

```java
// ❌ The client must implement methods it will never call
class SimplePrinter implements Printer {
    public void print() { ... }
    public void scan() { throw new UnsupportedOperationException("not supported"); }
    public void fax()  { throw new UnsupportedOperationException("not supported"); }
}
```

The failure surfaces when someone calls `scan()` on a `SimplePrinter` — a `UnsupportedOperationException` in production, far from the class that caused it. This is the **same failure mode as LSP violation** and the reason the two are related.

**ISP and DIP together:** DIP says depend on abstractions; ISP says make those abstractions **small enough** that depending on them is cheap. Together they produce many narrow interfaces — which is *good*, up to a point.

**How small is too small?** The test: if an interface has only one method and is used by exactly one implementation and one client, you may have over-segmented. The guideline is the **Interface Segregation smell**: an interface named `Xxxable` with 15 methods and 15 implementations is a clear violation; an interface with one method and one implementation is fine (it is a "functional interface" and often a `Supplier`/`Function` in disguise).

## 3.5 DIP — Dependency Inversion Principle

**Definition.** High-level modules should **not** depend on low-level modules directly. Both should depend on **abstractions**. Abstractions should not depend on details. Details should depend on abstractions.

```text
   THE ARROW TEST
   ──────────────
   In a well-designed system, the DEPENDENCY ARROWS point INWARD,
   from the volatile details (framework, database, HTTP) toward the
   stable core (domain logic).

   ❌ details pointing at the core:
        OrderService  ──▶  MySqlOrderRepository        (core → detail)
   ✅ the core pointing at an abstraction that the detail implements:
        OrderService  ──▶  OrderRepository  ◄──  MySqlOrderRepository
```

**DIP in practice = Dependency Injection.** There are three forms:

| Form | Example | Testability |
|---|---|---|
| **Constructor injection** ⭐ | `new OrderService(repo)` | ✅ Best — the dependency is required and visible |
| **Setter injection** | `service.setRepo(repo)` | ⚠ Optional dependency; can leave the object half-built |
| **Field injection** | `@Autowired private Repo repo;` | ❌ Not visible in the constructor; `null` in unit tests |

> 💡 **Constructor injection is the default.** It makes the dependency part of the
> object's *identity* — you cannot create the object without it — and it makes
> the class trivially testable with a mock.

**Where does the interface live?** With the **high-level** module — the one that *uses* it.

```text
   ❌ WRONG: the interface lives with the implementation
   com.mycompany.persistence.OrderRepository     ← owned by the DB team
   com.mycompany.service.OrderService            ← depends on the DB team's interface
   → the core's shape is dictated by the infrastructure team's API

   ✅ RIGHT: the interface lives with the consumer
   com.mycompany.service.port.OrderRepository    ← owned by the service team
   com.mycompany.persistence.JpaOrderRepository  ← implements it
   → the infrastructure team adapts to the core's needs
```

This is **Hexagonal Architecture / Ports & Adapters**, and it is the architectural expression of DIP.

**DIP is not "always use an interface".** A `String` is a fine dependency. The rule applies to **volatile, detail-level** dependencies — frameworks, databases, third-party services. A `BigDecimal` does not need an interface.

## 3.6 How the 5 principles interact

```text
        ┌──────────────────────────────────────────────────┐
        │                   OCP  (the goal)                │
        │        "add behaviour without changing code"     │
        └───────────────┬──────────────────────────────────┘
                        │  enabled by
        ┌───────────────┼───────────────┬──────────────────┐
        ▼               ▼               ▼                  ▼
   ┌─────────┐    ┌─────────┐    ┌─────────┐        ┌──────────┐
   │  SRP    │    │  LSP    │    │  ISP    │        │   DIP    │
   │ one    │    │ subtypes│    │ small   │        │ depend on│
   │ reason │    │ are safe│    │ client- │        │ abstract-│
   │        │    │         │    │ driven  │        │ ions     │
   └─────────┘    └─────────┘    └─────────┘        └──────────┘
        │               │               │                  │
        └───────────────┴───────────────┴──────────────────┘
                                │
                     all serve ONE goal:
              minimise the blast radius of a change
```

**The dependency chain that makes it work:**

> SRP splits a god class into focused classes → LSP ensures the split classes can
> be substituted → ISP keeps the interfaces small enough to depend on → DIP lets
> you inject them → and the result is **OCP**: a new requirement is a new class,
> not an edit.

## 3.7 The related laws (bonus — frequently asked as a follow-up)

| Law | Meaning | Java example |
|---|---|---|
| **DRY** | Don't Repeat Yourself | Extract a method/constant when the same *knowledge* appears 3+ times. |
| **KISS** | Keep It Simple Stupid | Don't add a pattern without a force. |
| **YAGNI** | You Aren't Gonna Need It | Don't build for imagined futures. |
| **Tell, Don't Ask** | Ask an object to do something rather than inspecting its state and deciding for it | `cart.total()` not `cart.items().stream()...` |
| **Law of Demeter** | Talk only to your immediate friends | No `a.getB().getC().doX()` chains |
| **Principle of Least Knowledge** | A method should use only the public API of the classes it interacts with | Same as Demeter |
| **Composition over Inheritance** | Prefer composition when the relationship is "has-a" | `Stack` delegating to an `ArrayList` |

**DRY's important nuance:** duplicate *knowledge* is the smell, not duplicate *text*. Two classes that both have `private double taxRate` are not violating DRY — they have different reasons to change. Three copies of the *same tax rule* are.

**Tell, Don't Ask** with a concrete example:

```java
// ❌ ASK — the caller reaches into the cart and decides
if (cart.items().isEmpty()) {
    throw new IllegalStateException("Cannot checkout an empty cart");
}
if (cart.items().stream().anyMatch(i -> i.stock() == 0)) {
    throw new IllegalStateException("Out of stock");
}
BigDecimal total = cart.items().stream()
        .map(i -> i.price().multiply(BigDecimal.valueOf(i.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

// ✅ TELL — the cart knows its own rules
cart.checkout();   // the cart validates and computes
```

---

## 4. Visualizations

### 4.1 The god class → the distributed design

```text
   ❌ BEFORE — 1 class, 8 reasons to change, 1 file everyone edits
   ────────────────────────────────────────────────────────────────────
   ┌────────────────────────────────────────────────────────────────┐
   │                      OrderService                               │
   │  validate()  calculateTax()  applyDiscount()  chargeCard()     │
   │  chargePayPal()  sendEmail()  sendSms()  save()  load()         │
   │  updateInventory()  log()  generateInvoice()  publishEvent()    │
   └────────────────────────────────────────────────────────────────┘
                    ▲
        marketing, payments, ops, DBA, data, infra — ALL edit this file

   ✅ AFTER — each class has ONE reason to change
   ───────────────────────────────────────────────────
   ┌──────────────┐   ┌──────────────┐   ┌──────────────────┐
   │OrderService  │──▶│ TaxStrategy  │   │ PaymentStrategy │
   │ (orchestrate)│   │ (tax rules)  │   │ (payment rules) │
   └──────┬───────┘   └──────────────┘   └──────────────────┘
          │
   ┌──────┴───────┐   ┌──────────────┐   ┌──────────────────┐
   │Notification  │   │OrderRepository│  │  InventoryGateway│
   │  Channel     │   │  (port)       │   │  (port)          │
   └──────────────┘   └──────────────┘   └──────────────────┘
```

**What to look at:** a new requirement ("UAE VAT") now means **one new class** plus a one-line registration. No existing file changes. That is OCP made concrete.

### 4.2 SRP — the "reasons to change" test

```text
   class Invoice
   ───────────────
   REASON 1: the business adds a new invoice type
   REASON 2: the DBA changes the storage engine
   REASON 3: ops adds a new delivery channel
   REASON 4: the tax authority changes the tax format

   → 4 reasons to change → SRP VIOLATED
   → the class is described as "it does invoices AND storage AND delivery AND tax"

   The FIX is not "split by method" but "split by REASON":
   ─────────────────────────────────────────────────────
   InvoiceCalculator  → reason 1, 4   (business + tax)
   InvoiceRepository  → reason 2       (DBA)
   InvoiceNotifier    → reason 3       (ops)
```

### 4.3 LSP — preconditions, postconditions, exceptions

```text
   THE PARENT'S CONTRACT
   ┌───────────────────────────────────────────────────────────────┐
   │  List<E>.set(int index, E element)                            │
   │                                                               │
   │  PRECONDITION:  index is within [0, size)                     │
   │  POSTCONDITION: the element at `index` is now `element`       │
   │  EXCEPTIONS:   IndexOutOfBoundsException if the index is bad  │
   │                (an UNCHECKED exception — the parent may throw) │
   └───────────────────────────────────────────────────────────────┘
                                  │
          ┌───────────────────────┼───────────────────────┐
          ▼                       ▼                       ▼
   ┌──────────────┐        ┌──────────────┐        ┌──────────────┐
   │ ArrayList    │        │ LinkedList   │        │ List.of(..)  │
   │              │        │              │        │              │
   │ ✅ honours   │        │ ✅ honours   │        │ ❌ VIOLATION │
   │   all three  │        │   all three  │        │ throws       │
   │              │        │              │        │ Unsupported- │
   │              │        │              │        │ Operation-   │
   │              │        │              │        │ Exception    │
   │              │        │              │        │ (a NEW       │
   │              │        │              │        │  exception)  │
   └──────────────┘        └──────────────┘        └──────────────┘

   → a client written against `List` CANNOT be given a `List.of(...)`
   → the substitutability is broken
```

### 4.4 ISP — the fat interface and its cost

```text
   ❌ FAT INTERFACE
   interface Machine {
       void print();
       void scan();
       void fax();
       void staple();
       void shred();
       void printInvoice();
   }

   ┌──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐
   │ Printer  │  Scanner │  Fax     │ Stapler  │ Shredder │ Invoice- │
   │ uses     │ uses     │ uses     │ uses     │ uses     │ Printer  │
   ├──────────┼──────────┼──────────┼──────────┼──────────┼──────────┤
   │ print    │ scan     │ fax      │ staple   │ shred    │ print    │
   │          │          │          │          │          │ printInv │
   └──────────┴──────────┴──────────┴──────────┴──────────┴──────────┘
      ALL SIX depend on all SIX methods, even though each uses 1–2.

   ✅ SEGREGATED
   interface Printable { void print(); void printInvoice(); }
   interface Scannable { void scan(); }
   interface Faxable  { void fax(); }
   interface Stapleable { void staple(); }
   interface Shredderable { void shred(); }

   → each client depends only on what it uses
   → an implementation implements only the interfaces it supports
   → NO UnsupportedOperationException
```

### 4.5 DIP — the package/arrow picture

```text
   ❌ WITHOUT DIP — the core depends on the detail
   ─────────────────────────────────────────────
   package com.acme.service;
       class OrderService {
           MySqlOrderRepository repo = new MySqlOrderRepository();
           //               ▲
           //               └── the core now knows the DB technology
       }

   package com.acme.persistence;
       class MySqlOrderRepository { ... }


   ✅ WITH DIP — the port lives in the core, the adapter implements it
   ──────────────────────────────────────────────────────────────
   package com.acme.service.port;          ← the CORE owns the abstraction
       interface OrderRepository {
           Optional<Order> findById(String id);
           void save(Order order);
       }

   package com.acme.service;
       class OrderService {
           private final OrderRepository repo;    // depends on the PORT
           OrderService(OrderRepository repo) { this.repo = repo; }
       }

   package com.acme.persistence;            ← the DETAIL implements the port
       class MySqlOrderRepository implements OrderRepository { ... }

   THE TEST: can you swap MySqlOrderRepository for an in-memory fake
             WITHOUT touching OrderService?  →  YES ⇒ DIP is satisfied.
```

### 4.6 The SOLID quadrant — when each principle bites

```text
                    HIGH IMPACT OF VIOLATION
                            ▲
                            │
        OCP  ●              │              ● LSP
     (new requirement      │        (subtype breaks the
      = edit everything)   │         contract at runtime)
                            │
   LOW FREQUENCY ──────────┼────────────── HIGH FREQUENCY
     of change            │              of change
                            │
        SRP ●              │              ● ISP
     (one god class,       │         (a fat interface, or
      many reasons)        │          a method used by all)
                            │
                            ▼
                    LOW IMPACT OF VIOLATION

   → THE HIGH-HIGH QUADRANT (LSP, ISP) IS WHERE THE RUNTIME BUGS LIVE.
     Violating SRP or OCP usually hurts the TEAM.
     Violating LSP or ISP usually hurts the PRODUCTION SYSTEM.
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — SRP: splitting a god class

```java
import java.math.BigDecimal;
import java.util.List;

public class SrpDemo {

    // ❌ BEFORE — 4 reasons to change in one class
    static class OrderService {
        private final List<String> emails = new java.util.ArrayList<>();

        void validate(String orderId) { if (orderId == null) throw new IllegalArgumentException("id"); }
        void calculateTax(BigDecimal subtotal) { /* tax rules change */ }
        void sendEmail(String to) { emails.add(to); }
        void saveToDatabase(String order) { /* DBA changes this */ }
    }

    // ✅ AFTER — one reason each
    static class OrderValidator {
        void validate(String orderId) {
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalArgumentException("orderId is required");
            }
        }
    }

    static class TaxCalculator {
        BigDecimal calculate(BigDecimal subtotal) {
            return subtotal.multiply(new BigDecimal("1.05"));
        }
    }

    static class EmailNotifier {
        private final List<String> sent = new java.util.ArrayList<>();
        void send(String to) { sent.add(to); }
        int sentCount() { return sent.size(); }
    }

    static class OrderRepository {
        private final List<String> rows = new java.util.ArrayList<>();
        void save(String order) { rows.add(order); }
        int rowCount() { return rows.size(); }
    }

    public static void main(String[] args) {
        OrderValidator validator = new OrderValidator();
        TaxCalculator tax = new TaxCalculator();
        EmailNotifier notifier = new EmailNotifier();
        OrderRepository repository = new OrderRepository();

        validator.validate("ORD-1");
        System.out.println("tax   = " + tax.calculate(new BigDecimal("100.00")));
        notifier.send("a@x.com");
        repository.save("ORD-1");

        System.out.println("emails sent = " + notifier.sentCount());
        System.out.println("rows saved  = " + repository.rowCount());

        try {
            validator.validate(" ");
        } catch (IllegalArgumentException e) {
            System.out.println("validation failed: " + e.getMessage());
        }
    }
}
```

**Actual output:**

```text
tax   = 105.0000
emails sent = 1
rows saved  = 1
validation failed: orderId is required
```

**Takeaway:** the "add a new tax jurisdiction" requirement now touches **only** `TaxCalculator`. The `EmailNotifier` and `OrderRepository` are untouched, so a tax change cannot break the email path — that is the *blast radius* reduction that SRP is really about.

### Example 2 — LSP: the `List.of` trap

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LspDemo {
    public static void main(String[] args) {
        // A client written against "List" — it may reasonably call set()
        List<String> mutable = new ArrayList<>(List.of("a", "b"));
        List<String> fixed   = List.of("a", "b");
        List<String> view    = Arrays.asList("a", "b");

        System.out.println("ArrayList.set(0)  → " + trySet(mutable));
        System.out.println("Arrays.asList.set → " + trySet(view));
        System.out.println("List.of.set       → " + trySet(fixed));
    }

    private static String trySet(List<String> list) {
        try {
            list.set(0, "z");
            return "OK → " + list;
        } catch (UnsupportedOperationException e) {
            return "💥 " + e.getClass().getSimpleName();
        }
    }
}
```

**Actual output:**

```text
ArrayList.set(0)  → OK → [z, b]
Arrays.asList.set → OK → [z, b]
List.of.set       → 💥 UnsupportedOperationException
```

**Takeaway:** `List.of(...)` is **not** a substitutable `List`. A client written against `List` that calls `set()` breaks at runtime when handed a `List.of`. That is an LSP violation, and it is why `List.of` should be used where the *read-only* nature is the point, and `new ArrayList<>(List.of(...))` where a mutable list is needed.

### Example 3 — ISP: the fat interface

```java
import java.util.List;

public class IspDemo {

    // ❌ BEFORE
    interface Machine {
        void print();
        void scan();
        void fax();
    }

    // ✅ AFTER — client-driven segmentation
    interface Printable { void print(); }
    interface Scannable { void scan(); }
    interface Faxable   { void fax(); }

    static class MultiFunctionPrinter implements Printable, Scannable, Faxable {
        @Override public void print() { System.out.println("printing"); }
        @Override public void scan()  { System.out.println("scanning"); }
        @Override public void fax()   { System.out.println("faxing"); }
    }

    // A simple printer implements ONLY what it supports — no UnsupportedOperationException
    static class SimplePrinter implements Printable {
        @Override public void print() { System.out.println("simple printing"); }
    }

    public static void main(String[] args) {
        Machine bad = new Machine() {
            @Override public void print() { System.out.println("printing"); }
            @Override public void scan()  { throw new UnsupportedOperationException("no scanner"); }
            @Override public void fax()   { throw new UnsupportedOperationException("no fax"); }
        };

        System.out.println("--- fat interface ---");
        try {
            bad.scan();
        } catch (UnsupportedOperationException e) {
            System.out.println("💥 " + e.getMessage());
        }

        System.out.println("--- segregated ---");
        Printable simple = new SimplePrinter();
        simple.print();                 // ✅ compiles — the client only needs Printable
    }
}
```

**Actual output:**

```text
--- fat interface ---
💥 no scanner
--- segregated ---
simple printing
```

**Takeaway:** with the segregated interfaces, `SimplePrinter` has **no** `UnsupportedOperationException` method to implement. The compiler prevents the failure instead of documenting it at runtime.

### Example 4 — DIP: swapping the database without touching the service

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DipDemo {

    // The PORT — owned by the service (the high-level module)
    interface OrderRepository {
        Optional<String> findById(String id);
        void save(String id, String payload);
    }

    // The ADAPTER — owned by the infrastructure team
    static class InMemoryOrderRepository implements OrderRepository {
        private final Map<String, String> store = new HashMap<>();
        @Override public Optional<String> findById(String id) { return Optional.ofNullable(store.get(id)); }
        @Override public void save(String id, String payload) { store.put(id, payload); }
    }

    // The CORE — knows only the port
    static class OrderService {
        private final OrderRepository repository;   // ← the abstraction

        OrderService(OrderRepository repository) { this.repository = repository; }

        void placeOrder(String id, String payload) {
            if (repository.findById(id).isPresent()) {
                throw new IllegalStateException("Duplicate order " + id);
            }
            repository.save(id, payload);
            System.out.println("order placed: " + id);
        }
    }

    public static void main(String[] args) {
        // The test wires a fake — no database, no mocking framework needed
        InMemoryOrderRepository repo = new InMemoryOrderRepository();
        OrderService service = new OrderService(repo);

        service.placeOrder("ORD-1", "{}");
        service.placeOrder("ORD-2", "{}");

        try {
            service.placeOrder("ORD-1", "{}");
        } catch (IllegalStateException e) {
            System.out.println("💥 " + e.getMessage());
        }

        // Swapping the implementation needs NO change to OrderService
        OrderRepository other = new InMemoryOrderRepository();
        new OrderService(other).placeOrder("ORD-3", "{}");
        System.out.println("swapped implementation — OrderService unchanged ✅");
    }
}
```

**Actual output:**

```text
order placed: ORD-1
order placed: ORD-2
💥 Duplicate order ORD-1
order placed: ORD-3
swapped implementation — OrderService unchanged ✅
```

**Takeaway:** `OrderService` has **zero** knowledge of the storage technology. The test needs no mocking library — a hand-written fake is enough. That is the practical payoff of DIP.

### Example 5 — The "reason to change" refactoring

```java
import java.math.BigDecimal;
import java.util.List;

public class ReasonsToChangeDemo {

    // ❌ BEFORE — "processes orders AND notifies AND persists"
    static class OrderProcessor {
        private final List<String> log = new java.util.ArrayList<>();

        void process(String orderId, BigDecimal amount) {
            if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be > 0");
            log.add("persisted " + orderId);
            log.add("emailed " + orderId);
            log.add("audited " + orderId);
        }
    }

    // ✅ AFTER — one reason to change each
    interface OrderValidator { void validate(BigDecimal amount); }
    interface OrderPersistence { void persist(String orderId); }
    interface OrderNotifier { void notifyCustomer(String orderId); }

    static class AmountValidator implements OrderValidator {
        @Override public void validate(BigDecimal amount) {
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("amount must be > 0");
            }
        }
    }
    static class LoggingPersistence implements OrderPersistence {
        private final List<String> rows = new java.util.ArrayList<>();
        @Override public void persist(String orderId) { rows.add(orderId); }
        int count() { return rows.size(); }
    }
    static class EmailNotifier implements OrderNotifier {
        private final List<String> sent = new java.util.ArrayList<>();
        @Override public void notifyCustomer(String orderId) { sent.add(orderId); }
        int count() { return sent.size(); }
    }

    static class OrderProcessor {
        private final OrderValidator validator;
        private final OrderPersistence persistence;
        private final OrderNotifier notifier;

        OrderProcessor(OrderValidator validator, OrderPersistence persistence, OrderNotifier notifier) {
            this.validator = validator;
            this.persistence = persistence;
            this.notifier = notifier;
        }

        void process(String orderId, BigDecimal amount) {
            validator.validate(amount);
            persistence.persist(orderId);
            notifier.notifyCustomer(orderId);
        }
    }

    public static void main(String[] args) {
        LoggingPersistence persistence = new LoggingPersistence();
        EmailNotifier notifier = new EmailNotifier();
        OrderProcessor processor = new OrderProcessor(
                new AmountValidator(), persistence, notifier);

        processor.process("ORD-1", new BigDecimal("10.00"));
        System.out.println("persisted = " + persistence.count());
        System.out.println("notified  = " + notifier.count());

        // A new requirement — a new implementation, no existing class edited
        OrderProcessor withSms = new OrderProcessor(
                new AmountValidator(), persistence, orderId -> System.out.println("SMS sent " + orderId));
        withSms.process("ORD-2", new BigDecimal("5.00"));
    }
}
```

**Actual output:**

```text
persisted = 1
notified  = 1
SMS sent ORD-2
```

**Takeaway:** the "add SMS notification" requirement added **one lambda** and touched **no existing class**. Notice also that `OrderProcessor` is now trivially testable — every collaborator is a fake you can write in three lines.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A SOLID, testable order-processing core

```java
package com.example.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The DOMAIN CORE — depends only on its own abstractions.
 *
 * <p>This is the "inner circle" of Hexagonal Architecture:
 * <ul>
 *   <li>{@link OrderRepository} is a PORT, owned by this package.</li>
 *   <li>{@link PaymentGateway} and {@link NotificationSender} are PORTS too.</li>
 *   <li>Nothing here knows about SQL, HTTP, Kafka or Spring.</li>
 * </ul>
 *
 * <p>Every constructor argument is a collaborator you can replace with a
 * three-line fake in a unit test. That is DIP paying off.
 */
public class PlaceOrderUseCase {

    // ── PORTS (owned by the core) ──────────────────────────────────────
    public interface OrderRepository {
        boolean exists(String orderId);
        void save(Order order);
    }

    public interface PaymentGateway {
        PaymentResult charge(String customerId, BigDecimal amount, String reference);
    }

    public interface NotificationSender {
        void orderPlaced(String customerId, String orderId);
    }

    public interface Clock {              // ← inject time; never call now() directly
        Instant now();
    }

    // ── DOMAIN TYPES ───────────────────────────────────────────────────
    public record Order(
            String orderId,
            String customerId,
            List<LineItem> items,
            BigDecimal total,
            Instant createdAt) {

        public Order {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(customerId, "customerId");
            items = List.copyOf(items);
            Objects.requireNonNull(total, "total");
            if (items.isEmpty()) throw new IllegalArgumentException("an order needs at least one item");
            if (total.signum() <= 0) throw new IllegalArgumentException("total must be > 0");
        }
    }

    public record LineItem(String sku, int quantity, BigDecimal unitPrice) {
        public LineItem {
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
            if (unitPrice.signum() < 0) throw new IllegalArgumentException("unitPrice must be >= 0");
        }
        public BigDecimal lineTotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
    }

    public record PaymentResult(boolean success, String transactionId, String failureReason) {
        public static PaymentResult success(String txId) { return new PaymentResult(true, txId, null); }
        public static PaymentResult failure(String reason) { return new PaymentResult(false, null, reason); }
    }

    public static class OrderRejectedException extends RuntimeException {
        private final String reason;
        public OrderRejectedException(String reason) { super(reason); this.reason = reason; }
        public String reason() { return reason; }
    }

    // ── THE USE CASE ────────────────────────────────────────────────────
    private final OrderRepository orders;
    private final PaymentGateway payments;
    private final NotificationSender notifications;
    private final Clock clock;

    public PlaceOrderUseCase(OrderRepository orders,
                             PaymentGateway payments,
                             NotificationSender notifications,
                             Clock clock) {
        this.orders = Objects.requireNonNull(orders);
        this.payments = Objects.requireNonNull(payments);
        this.notifications = Objects.requireNonNull(notifications);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * One use case = one reason to change. Adding "apply a promotion code"
     * means adding a collaborator, NOT editing this method.
     */
    public Order place(String orderId, String customerId, List<LineItem> items) {
        if (orders.exists(orderId)) {
            throw new OrderRejectedException("Duplicate order " + orderId);
        }

        BigDecimal total = items.stream()
                .map(LineItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PaymentResult payment = payments.charge(customerId, total, orderId);
        if (!payment.success()) {
            throw new OrderRejectedException("Payment failed: " + payment.failureReason());
        }

        Order order = new Order(orderId, customerId, items, total, clock.now());
        orders.save(order);
        notifications.orderPlaced(customerId, orderId);
        return order;
    }
}
```

### 6.2 The adapters — where the technology lives

```java
package com.example.order.adapter;

import com.example.order.PlaceOrderUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ADAPTERS — the volatile details. These implement the core's ports.
 * Swapping any of them requires NO change to the core.
 */
public final class Adapters {

    /** An in-memory order repository — also the test double. */
    public static final class InMemoryOrderRepository implements PlaceOrderUseCase.OrderRepository {
        private final Map<String, PlaceOrderUseCase.Order> store = new ConcurrentHashMap<>();

        @Override public boolean exists(String orderId) { return store.containsKey(orderId); }
        @Override public void save(PlaceOrderUseCase.Order order) { store.put(order.orderId(), order); }

        public PlaceOrderUseCase.Order get(String orderId) { return store.get(orderId); }
        public int size() { return store.size(); }
    }

    /** A notification sender that just logs — a "null object" adapter. */
    public static final class LoggingNotificationSender implements PlaceOrderUseCase.NotificationSender {
        @Override public void orderPlaced(String customerId, String orderId) {
            System.out.println("  [notify] " + customerId + " ← order " + orderId);
        }
    }

    /** A fixed clock — never call LocalDateTime.now() in business logic. */
    public static final class FixedClock implements PlaceOrderUseCase.Clock {
        private final Instant instant;
        public FixedClock(Instant instant) { this.instant = instant; }
        @Override public Instant now() { return instant; }
    }

    /** A payment gateway that always declines — the safe default in tests. */
    public static final class DecliningPaymentGateway implements PlaceOrderUseCase.PaymentGateway {
        @Override public PlaceOrderUseCase.PaymentResult charge(String customerId, BigDecimal amount, String ref) {
            return PlaceOrderUseCase.PaymentResult.failure("gateway unavailable in test");
        }
    }
}
```

### 6.3 A unit test with no mocking framework

```java
package com.example.order;

import com.example.order.adapter.Adapters;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaceOrderUseCaseTest {

    private final Adapters.InMemoryOrderRepository repository = new Adapters.InMemoryOrderRepository();
    private final PlaceOrderUseCase useCase = new PlaceOrderUseCase(
            repository,
            new Adapters.DecliningPaymentGateway(),
            new Adapters.LoggingNotificationSender(),
            new Adapters.FixedClock(Instant.parse("2026-01-01T00:00:00Z")));

    @Test
    void rejectsAnOrderWithNoItems() {
        assertThrows(IllegalArgumentException.class,
                () -> useCase.place("ORD-1", "CUS-1", List.of()));
    }

    @Test
    void rejectsADuplicateOrder() {
        useCase.place("ORD-1", "CUS-1", List.of(item()));
        assertThrows(PlaceOrderUseCase.OrderRejectedException.class,
                () -> useCase.place("ORD-1", "CUS-1", List.of(item())));
    }

    @Test
    void propagatesThePaymentFailureAsARejection() {
        PlaceOrderUseCase.OrderRejectedException ex = assertThrows(
                PlaceOrderUseCase.OrderRejectedException.class,
                () -> useCase.place("ORD-1", "CUS-1", List.of(item())));
        assertTrue(ex.reason().contains("gateway unavailable"));
        assertEquals(0, repository.size(), "nothing must be persisted when payment fails");
    }

    @Test
    void computesTheTotalFromTheLineItems() {
        PlaceOrderUseCase.Order order = useCase.place("ORD-1", "CUS-1", List.of(item()));
        assertEquals(new BigDecimal("25.00"), order.total());
    }

    private static PlaceOrderUseCase.LineItem item() {
        return new PlaceOrderUseCase.LineItem("SKU-1", 5, new BigDecimal("5.00"));
    }
}
```

> 💡 **The point of this test:** there is **no mocking framework**, no Spring context,
> no database. Every collaborator is a three-line fake. That is what DIP buys you —
> and it is why "SOLID" and "testable" are the same conversation.

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — "One class = one method"

```java
// ❌ SRP does NOT mean this
class OrderId {
    private final String value;
    OrderId(String value) { this.value = value; }
    String value() { return value; }
}
```

**✅ FIX** — SRP is about *reasons to change*, not method count. A value object with five accessors has one reason to change.

---

### ❌ Pitfall 2 — Splitting a god class by *method* instead of by *reason*

```java
// ❌ "OrderValidator" that also formats invoices — still 2 reasons
class OrderHelper {
    void validate() { }
    String formatInvoice() { return ""; }
}
```

**✅ FIX** — group by the **actor** who will ask for the change.

---

### ❌ Pitfall 3 — Over-applying ISP

```java
// ❌ 20 one-method interfaces for 1 implementation and 1 client
interface ReadOrderId { String get(); }
interface WriteOrderId { void set(String id); }
```

**✅ FIX** — ISP is *client-driven*. If there is one client that uses everything, one interface is correct.

---

### ❌ Pitfall 4 — An interface with no second implementation

```java
// ❌ an interface with exactly one implementation and no substitution in sight
interface UserDao { User findById(String id); }
```

**✅ FIX** — the interface earns its keep when there is a *second* implementation (a real one, or a test double). If neither exists, use the class.

---

### ❌ Pitfall 5 — Field injection in production code

```java
@Autowired private OrderRepository repo;   // ❌ invisible dependency, null in plain unit tests
```

**✅ FIX** — constructor injection. The dependency is visible, required and testable.

---

### ❌ Pitfall 6 — Applying DIP to stable JDK types

```java
interface BigDecimalWrapper { }   // ❌ absurd — BigDecimal will not be swapped
```

**✅ FIX** — DIP is about **volatile** dependencies: frameworks, databases, third-party services.

---

### ❌ Pitfall 7 — Making a class `final` to "satisfy" LSP instead of fixing the design

```java
final class Stack extends ArrayList { }   // ❌ hiding the problem, not solving it
```

**✅ FIX** — use composition: `class Stack { private final Deque<E> d = new ArrayDeque<>(); }`

---

### ❌ Pitfall 8 — Treating `List.of` as a drop-in `List`

```java
List<String> l = List.of("a");
l.set(0, "b");   // 💥 UnsupportedOperationException — an LSP violation in disguise
```

**✅ FIX** — `new ArrayList<>(List.of(...))` when mutation is required.

---

### ❌ Pitfall 9 — Confusing "closed for modification" with "never change the class"

```java
// ❌ OCP does NOT mean a class is frozen forever
```

**✅ FIX** — OCP means *adding a new behaviour* does not require modifying the existing code path. Bug fixes and internal refactors are still fine.

---

### ❌ Pitfall 10 — Treating SOLID as a maximum rather than a balance

```java
// ❌ 40 tiny classes, none cohesive, impossible to follow
```

**✅ FIX** — SOLID is a set of *tensions*. The goal is the smallest change footprint, not the most classes.

---

### ❌ Pitfall 11 — A "port" that leaks the adapter's technology

```java
// ❌ the port is not abstract — it is a SQL-shaped interface
interface OrderRepository {
    List<Object[]> findByCustomerAndStatus(String customerId, String status);
}
```

**✅ FIX** — the port should speak the **domain's** language: `Optional<Order> findActiveOrders(String customerId)`.

---

### ❌ Pitfall 12 — Assuming SOLID is a Java-only concern

**✅ FIX** — SOLID is language-agnostic. In a functional codebase, the same forces appear as pure functions, immutable data, and dependency injection of *values* rather than interfaces.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What does each letter of SOLID stand for, and what is the one-line meaning?**
**A:** **S**ingle Responsibility — one reason to change. **O**pen/Closed — open for extension, closed for modification. **L**iskov Substitution — subtypes are behaviourally substitutable. **I**nterface Segregation — small, client-driven interfaces. **D**ependency Inversion — depend on abstractions, not details. The principles were popularised by Robert C. Martin (2000), building on his 1995 paper *The SOLID Principles*.

**Q: What is the "Single Responsibility" principle, precisely?**
**A:** A class should have **one reason to change** — one *axis* of change, usually corresponding to one *actor* (the business, the DBA, the ops team). It does **not** mean "one method" and it does **not** mean "one job". The test: name the actors who would ask you to change this class; if there is more than one, the class has more than one responsibility.

**Q: What is the "Open/Closed" principle?**
**A:** Software entities should be open for **extension** (you can add new behaviour by adding new code) and closed for **modification** (you do not have to change existing code to add that behaviour). In practice: replace the `switch`/`if-else` on a type with a polymorphic abstraction (Strategy, or an interface with implementations), so a new type is a new class rather than an edit to a shared file.

**Q: What is the "Liskov Substitution" principle?**
**A:** If `S` is a subtype of `T`, then `S` must be usable anywhere a `T` is expected, without changing the correctness of the program. Concretely: the subtype must not strengthen preconditions, must not weaken postconditions, and must not introduce new unchecked exceptions. The classic violation is `Stack extends ArrayList` inheriting `add(int index, E)` — an operation that is meaningless for a stack.

**Q: What is the "Interface Segregation" principle?**
**A:** No client should be forced to depend on methods it does not use. If an interface has methods that only some clients need, split it into smaller interfaces — and split according to **client usage**, not according to the implementation's convenience. The symptom of a violation is a client implementing methods that throw `UnsupportedOperationException`.

**Q: What is the "Dependency Inversion" principle?**
**A:** High-level modules should not depend on low-level modules; both should depend on abstractions, and abstractions should not depend on details. In Java this means: the service defines the interface it needs, and the database/framework class implements it — so the dependency arrow points inward. In practice, DIP is **dependency injection**, preferably via the constructor.

### 🟡 Intermediate

**Q: Give a concrete example of an LSP violation with a runtime failure.**
**A:** `Stack extends ArrayList` inherits `add(int index, E element)`, which inserts in the middle — meaningless for a stack, and it breaks the `List` contract. The runtime symptom is an `UnsupportedOperationException` if the stack overrides it, or a silently corrupted stack if it does not. A subtler, very common one: `List.of(...)` is not substitutable for `ArrayList` because `set()` throws `UnsupportedOperationException` — a client written against `List` may legitimately call `set`.

**Q: How do you know if an interface is too fat?**
**A:** Apply the **client-driven** test: list the clients, and for each, list the methods it actually calls. If two clients use disjoint subsets, the interface should be split. The concrete signal is an implementation throwing `UnsupportedOperationException` for methods it does not support — that is the runtime proof of a violation. But also watch the opposite failure: if there is exactly one client that uses every method, one fat interface is correct, and splitting it would be over-application.

**Q: What is the "fat interface" cost in practice, beyond theory?**
**A:** Two real costs. First, every implementation must implement every method, usually by throwing `UnsupportedOperationException` — a runtime landmine that surfaces far from the cause. Second, it prevents a client from depending on a *narrow* abstraction, so the coupling graph stays dense. But the honest counterweight: splitting interfaces increases the number of types, which increases cognitive load and can make a simple model harder to follow. The principle is a *tension*, not a maximisation target.

**Q: Where should the interface live — with the implementation or with the consumer?**
**A:** With the **consumer** (the high-level module). If the interface lives with the persistence layer, the core's shape is dictated by the infrastructure team's API, and the dependency arrow points the wrong way. Putting the port in the service's own package is the "ports and adapters" (hexagonal) arrangement, and it lets the infrastructure team adapt to the domain rather than the reverse.

**Q: Is "depend on abstractions" the same as "always use an interface"?**
**A:** No — and this is a common misunderstanding. DIP is about the **direction of dependency in the module/package graph**, and it applies to *volatile* dependencies: frameworks, databases, third-party APIs, message brokers. A `String` or a `BigDecimal` is a stable detail; wrapping it in an interface is ceremony with no benefit. The test: "would I ever swap this for a different implementation?" If yes, abstract it.

**Q: How do SOLID and the design patterns relate?**
**A:** The patterns are the *implementations* of the principles. Strategy and Factory exist to achieve OCP. Adapter and Decorator exist to enable LSP-safe substitution. ISP explains why the `Runnable`/`Callable`/`Comparator` interfaces are small. DIP explains why Spring's dependency injection exists. In short: **SOLID states the goal, the patterns are the toolbox.**

### 🔴 Advanced / Senior

**Q: How do you refactor a god class while keeping the release moving?**
**A:** Incrementally, in a **strangler** sequence, ordered by risk. (1) **Characterise** the current behaviour with tests — a god class usually has no tests, so the first step is a characterisation test that pins the existing behaviour, warts included. (2) **Extract the safest seam first** — usually a leaf collaborator with no callers of its own (a `TaxCalculator`, a `DateTimeProvider`), because extracting it changes no call sites. (3) **Introduce the port** at the boundary of the next candidate: create the interface, make the existing class implement it, and inject it. At this point you have achieved DIP *without* changing behaviour. (4) **Move behaviour** one reason-to-change at a time, one PR per actor, with the characterisation test as the safety net. (5) **Delete the old path** in the same PR — never leave two paths. The rule that makes it safe: **never split by method, always split by reason to change**, or you end up with the same god class in more files.

**Q: "Is inheritance bad?" — the honest answer.**
**A:** Inheritance is not bad; *unconditional* inheritance is. The problems with inheritance-as-a-default are: (1) it couples the child to the parent's *implementation* (a change to the parent can break every child), (2) it creates the diamond problem for state, (3) it forces an `is-a` relationship that is often really a `has-a`, and (4) it produces the LSP violations we discussed (`Stack extends ArrayList`). Composition avoids all four, at the cost of an extra object and a delegation call. The right rule: **prefer composition; use inheritance when the relationship is genuinely substitutable and the parent is designed for extension** (with protected extension points or documented invariants). A good heuristic: if you cannot state the "is-a" sentence without hesitation, use composition.

**Q: How do SOLID and functional programming relate?**
**A:** They are two answers to the same forces. SOLID's SRP maps to small pure functions; its DIP maps to passing values rather than reaching for singletons; its OCP maps to adding a new function rather than editing a dispatch table; its LSP maps to functions that honour their input contracts (total functions rather than partial ones with hidden preconditions). Where they differ: SOLID is *class*-centric and permits mutable state, while FP is *value*-centric and prefers immutability. In modern Java you can take both: use `record`s and immutable collections for the domain, and use interfaces + injection at the boundaries. The pragmatic combination is what most well-engineered Java codebases actually do.

**Q: How do you apply SOLID without over-engineering a small codebase?**
**A:** Apply it **at the seams, not everywhere**. The forces that matter are at the *boundaries* — where you talk to a database, a third-party API, a message broker, or another team. Those are volatile and deserve interfaces and injection. The *interior* of a small, cohesive module (a value object, a pure algorithm, a DTO) does **not** need to be decomposed into five classes with five interfaces; that is ceremony, and ceremony has a cost. The test: would a new requirement realistically require a change here? If not, leave it alone. Concretely, for a 200-line CRUD service you might end up with: a controller, a service, and a repository interface + one implementation — four classes, and that is fine.

### ⚫ Expert / Principal

**Q: SOLID principles can conflict. Give an example and show how you resolve it.**
**A:** The classic conflict is **ISP vs YAGNI/KISS**: ISP pushes you to split a fat interface into many small ones, but each split adds a type, adds cognitive load, and often adds an *adapter* to bridge the pieces back together — which is more code, not less. A concrete case: a `Machine` interface with `print`, `scan`, `fax` used by three clients. Full ISP gives `Printable`, `Scannable`, `Faxable` — but if every client actually uses all three, you have added two types and gained nothing, and a class that needs all three must implement three interfaces. The resolution is that **ISP is client-driven, not method-driven**: the split is justified only when a client genuinely uses a subset. The second conflict is **DIP vs YAGNI**: DIP says "depend on an abstraction", but introducing an interface for a dependency that will never be swapped is speculative. The resolution is to weight DIP by *volatility* — abstract the things that change (frameworks, databases, external APIs), and leave the stable things concrete. The meta-principle that resolves both: **SOLID is a set of tensions, not a set of maximisations.** The correct posture is not "maximum ISP" or "maximum DIP" but "the smallest amount of structure that reduces the blast radius of the change I actually expect."

**Q: How would you apply SOLID to a codebase that has none of it, with no mandate to refactor?**
**A:** **Do not do a big-bang SOLID-ification.** It is high-risk, low-visible-value, and it burns the team's trust. Instead: (1) **new code only** — every new class you write is SOLID by construction, and no existing class is touched; this is the "paved road" approach, and it compounds. (2) **Fix the class that keeps breaking** — the class that causes regressions or that everyone is afraid to touch is the one whose "reason to change" has multiplied; extracting its collaborators is justified by the pain, not by principle. (3) **Use the metrics** — a class that changes every sprint, a file with constant merge conflicts, a bug that recurs in the same class: these are the SRP/OCP violations that are *paying* for the refactor. (4) **Introduce ports at the boundaries you are already changing** — when you next touch the persistence layer, define the interface in the service package. (5) **Never mix a refactor with a behaviour change in one PR** — reviewers cannot review both, and you cannot attribute a regression. The senior-level framing: SOLID is a tool for *managing change*, so apply it where change is actually painful, and let the rest of the codebase be.

**Q: Where does SOLID sit relative to DDD and Clean Architecture?**
**A:** In a hierarchy: **SOLID is a set of class-level heuristics; DDD is a modelling approach; Clean/Hexagonal Architecture is an application-level arrangement.** They are consistent and reinforce each other. SOLID operates *within* a module — it tells you how to shape a class. DDD tells you what the *boundaries* are (bounded contexts, aggregates, entities vs value objects) and gives you the domain model to apply SOLID *to*. Clean Architecture arranges those boundaries into rings (domain → application → adapters → frameworks) and makes the dependency rule structural: the inner ring knows nothing about the outer ones. That is DIP applied at the *architecture* level rather than the class level — the same idea, scaled up. The practical reading for an interview: SOLID tells you how to write a class; DDD tells you what the class *is*; Clean Architecture tells you where the class *goes*.

---

## 9. Counter-Questions & Traps

### CQ1. "Doesn't SOLID just lead to over-engineering? A simple class is better than five interfaces."

- **Naive:** "SOLID is about writing clean, maintainable code."  ← *true, but it dodges the real cost*
- **The trap:** The candidate has identified a genuine tension but resolved it in the wrong direction. SOLID does **not** mandate five interfaces — it mandates that a change in one *reason* should not require changes in unrelated code. A cohesive class with five methods and one reason to change is perfectly SOLID. The trap in the opposite direction is just as common: using "simplicity" as a blanket excuse for a god class that changes for four different reasons.
- **Expert answer:** The right frame is **cost vs benefit, per decision**. The cost of SOLID is real: more types, more indirection, more files, a steeper onboarding curve. The benefit is a smaller blast radius when requirements change. The correct question is not "is SOLID good?" but "for *this* class, is the probability of a reason-to-change divergence higher than the cost of the structure?" For a `LineItem` record, the answer is clearly no — leave it alone. For a `PaymentProcessor` that three teams are extending every quarter, the answer is clearly yes. And note the asymmetry: **the cost of over-engineering is paid continuously (every read, every onboarding), while the cost of under-engineering is paid in a crisis (the incident, the hotfix, the weekend).** That asymmetry is why the industry trends toward more structure than strictly necessary.

### CQ2. "Is `final` needed for thread safety? Doesn't making everything `final` satisfy SRP?"

- **Naive:** "`final` fields are safe because they can't change."  ← *the two questions are unrelated*
- **The trap:** This question mixes two completely different concerns. `final` is about **immutability and safe publication**; SRP is about **focus and change coupling**. Making every field `final` does not reduce the number of reasons a class changes — it just means the change must be a *new class*. And a class with 10 `final` fields and 10 unrelated methods is still a god class.
- **Expert answer:** Separate them. `final` gives you (a) the ability to publish the object safely across threads without synchronisation (the JMM's final-field semantics guarantee that a properly constructed object is seen fully initialised), and (b) the ability to use it as a **key** in a `HashMap` without the mutation bug, and (c) the ability to make it a constant for the JIT. SRP is about *design*, not *modifiers*. Interestingly, the two can reinforce each other: an immutable class with `final` fields and a narrow API is both thread-safe by construction and hard to misuse — and "hard to misuse" is a very good proxy for "has one reason to change".

### CQ3. "If I use Spring, isn't DIP handled for me? Why do I need interfaces at all?"

- **Naive:** "Spring's `@Autowired` is dependency injection, so DIP is automatic."  ← *partly true, and the trap is in "automatic"*
- **The trap:** Spring *performs* dependency injection, but it does **not** give you DIP's *benefit* unless the abstraction is right. Spring will happily inject a concrete `MySqlOrderRepository` into a field typed `MySqlOrderRepository` — no interface, no DIP, and now your service is untestable without a Spring context. Worse, Spring will happily inject a `WindowsCheckbox` into a field expecting a `Checkbox` — the Abstract-Factory-style compatibility guarantee does **not** exist in DI.
- **Expert answer:** The distinction is between the **mechanism** and the **architecture**. Spring gives you the mechanism (injection). DIP requires the **abstraction to be owned by the consumer** and to be a genuine contract. A practical test: *can you write a plain JUnit test for your service with no Spring context and no mocking library?* If yes, you have DIP. If you need `@SpringBootTest` to inject a mock, you do not — you have "injection without abstraction", and the test is slow and brittle. Spring's own philosophy is instructive here: Spring Data's `Repository` interfaces live in the *data* layer, but Spring's `JdbcTemplate` API lives in the *framework* layer and the abstraction is the `DataSource` — the JDK's own interface, not Spring's. Pick abstractions that are *yours*, not ones you inherit.

### CQ4. "You said SRP is about reasons to change. But a class with 20 methods all about 'orders' has one reason to change, right?"

- **Naive:** "Yes, it's about orders, so it has one responsibility."  ← *the "reason" test is about ACTORS, not topics*
- **The trap:** "All about orders" is a **topic**, not a **reason**. A topic can hide several reasons: the order *schema* changes (DBA), the order *pricing* changes (business), the order *notification* changes (ops), the order *audit trail* changes (compliance). All of them are "about orders", and all of them would require editing the same class.
- **Expert answer:** The test that works is the **actor** test: *name the people or teams who will ask you to change this class.* If the answer is "the DBA, the business, and ops", there are three reasons, no matter how many of them concern orders. A good heuristic: if two changes to the class would be *requested by different people on different schedules*, they are different reasons to change. And a practical symptom: **a class where a change requested by one team breaks behaviour another team depends on** is a class whose reasons have not been separated.

### CQ5. "LSP says `Stack extends ArrayList` is wrong. But the JDK ships `Stack`! Doesn't the JDK violate its own principle?"

- **Naive:** "The JDK is wrong."  ← *technically true, historically explicable*
- **The trap:** This is a genuinely interesting question, and the naive answer misses that the JDK's `Stack` is a **legacy artefact from 1996** (Java 1.0), kept only for binary compatibility. It was widely criticised and the JLS's own `java.util` documentation acknowledges that `Stack` should not be used in new code. The modern JDK's own guidance is `Deque` — which is exactly why `ArrayDeque` implements `Deque` and *not* `List`: `ArrayDeque` deliberately does **not** extend `List`, so the LSP-violating `add(int, E)` operation does not exist.
- **Expert answer:** The JDK's history is a useful lesson about how principles erode. `Stack` was fine in 1996; it became a violation as `List` grew operations (`add(int index, E)`, `remove(int)`, `listIterator`, `subList`) that are meaningless for a stack. The **fix** in the modern JDK was not to make `Stack` behave correctly (impossible without breaking compatibility) but to **not inherit from `List`** — `ArrayDeque` is a `Deque` only. The general principle: **LSP violations are usually fixed by narrowing the type, not by patching the behaviour.** And the meta-lesson: a public API, once shipped, is effectively frozen — so the *design* decisions you make about inheritance at version 1 determine whether you can evolve safely at version 10.

### CQ6. "You said ISP is client-driven. Then how do I know who the clients are before I write the code?"

- **Naive:** "You look at the current usages."  ← *too naive for a greenfield component*
- **The trap:** For a *library* with unknown clients, "look at the usages" is useless — you have none. And waiting to see the clients is exactly the "rule of three" delay, which for a published library can be years. The trap is to guess wrong and publish a fat interface that every client must implement.
- **Expert answer:** You do not need to know the clients — you need to know the **capabilities**, and you derive those from the **domain**, not from the code. The intuition is: *what are the distinct roles that can perform this operation?* A `Repository` has roles: reader, writer, deleter. A `Message` has roles: sender, receiver, admin. Segment by **role**, and the segment boundaries are stable even as clients come and go. A second, pragmatic signal: if you cannot imagine a second, *meaningfully different* client for a method, that method probably belongs on a different interface. And a third: the **"interface per implementor"** heuristic — if a class would have to throw `UnsupportedOperationException` for a method, that method does not belong in that class's interface. That is a *design-time* test that works without knowing the clients.

### CQ7. "If a class has one reason to change, isn't that just 'one class per table' or 'one class per use case'?"

- **Naive:** "Yes, one class per use case."  ← *a common but wrong simplification*
- **The trap:** "One class per use case" produces a different anti-pattern: a class per *operation*, each with a tiny amount of shared state, and a swarm of collaborators. That is the "anemic domain model" — the opposite failure of the god class. The truth is in between: SRP is about the *reason to change*, and a reason to change often spans several use cases (e.g. "the way we compute tax changes" is one reason covering three use cases).
- **Expert answer:** The right unit is the **concept that changes together**. In DDD terms: an **entity** or a **value object** (Order, LineItem, Money) is one cohesive concept with one reason to change. A **use case** (PlaceOrder) is a *collaborator* that orchestrates concepts, not a concept itself. So the anti-pattern is not "one class per use case" but "one class per *table*" (a schema-driven design with no domain meaning) and "one class per method". The signal you have it right: you can describe each class in one sentence, and that sentence is about a *thing in the domain*, not about a *screen, a table, or a service*.

### CQ8. "You said `List.of` violates LSP. But it's in the JDK — isn't the JDK full of LSP violations?"

- **Naive:** "Yes, the JDK is full of them."  ← *a lazy answer*
- **The trap:** The JDK does contain design compromises, but "the JDK ships it" is not an argument for or against LSP. More importantly, the *interesting* question is why the JDK made this trade and what the consequences are.
- **Expert answer:** The JDK's `List.of` is a deliberate, documented **narrowing** of the `List` contract for the **immutable** case, and the Javadoc states it explicitly: the returned list is *unmodifiable* and does not support `set`. The JDK accepts this because (a) the alternative — a mutable "copy of" type — would double the API surface for a marginal use case, and (b) the *cost* of the violation is a runtime `UnsupportedOperationException` on an operation that only a caller relying on mutability would attempt. The broader point: **LSP is a guideline for designing hierarchies, and the JDK's design hierarchy is deliberately flat** (`List` has few subinterfaces precisely to avoid the LSP problems of a deep hierarchy). The lesson for your own code: when you need an immutable variant of a mutable type, prefer a **separate type or a copy** over a subclass with different behaviour.

### CQ9. "You said 'composition over inheritance'. But isn't that just a style preference with no measurable difference?"

- **Naive:** "Composition is better."  ← *a slogan, not an argument*
- **The trap:** "Better" needs a dimension. Composition is *not* uniformly better: it costs an extra object and a delegation call, and it removes the ability to use polymorphism on the composed part. If the relationship really is substitutable, inheritance gives you something composition does not — the ability to treat the child as the parent *without* a delegating wrapper.
- **Expert answer:** The measurable difference is in the **coupling and the change blast radius**. Inheritance couples the child to the parent's *implementation*: a change to the parent's protected state can silently break every child, and the child cannot choose a different implementation of the inherited behaviour. Composition decouples them: the child holds a delegate and can be given a different one. The measurable costs of composition are: one extra allocation (usually irrelevant — the JIT can scalar-replace it), one extra virtual call on the hot path (measurable only in tight loops), and slightly more verbose code. So the rule is: **use composition by default; use inheritance when the substitutability is real, the parent is designed for extension, and the extra indirection matters.** In performance-critical inner loops, flat inheritance can genuinely win — which is why the JDK's `ArrayList` uses an array rather than composing a `LinkedList`.

### CQ10. "If every principle is a trade-off, how do you decide? What's your actual decision procedure?"

- **Naive:** "It depends."  ← *true, and useless in an interview*
- **The trap:** "It depends" is the *non-answer*. The interviewer is asking for a **procedure**, not a philosophy. A candidate who can articulate a repeatable decision process is far more convincing than one who can recite the principles.
- **Expert answer:** Here is a procedure that works. (1) **Identify the volatile seams** — the places where the code talks to something that will change: another team, a third party, a database, a message bus, a UI, a config source. These are where DIP earns its cost. (2) **At each seam, ask "who will ask me to change this?"** — one actor means one reason, and you may be fine; two actors means you have at least two reasons and should split along that line. (3) **Look for the runtime symptoms** — `UnsupportedOperationException` (ISP/LSP), a `ClassCastException` (LSP), a class that is the cause of most regressions (SRP), a file with constant merge conflicts (OCP). Refactor what is *bleeding*, not what is theoretically impure. (4) **Prefer the smallest structure that solves the identified problem** — one interface with one method is fine; twenty interfaces for one client is not. (5) **Re-evaluate on change** — every time a requirement arrives, ask which principle it stresses; that is the moment the refactor is justified and safe, because you have a test that pins the current behaviour. The philosophy in one line: **SOLID is applied under uncertainty, at the seams, in response to observed pain — not applied globally, in advance, on faith.**

---

## 10. Rapid-Fire MCQs

**Q1.** SOLID was popularised by…
- A) Martin Fowler  B) Robert C. Martin  C) Erich Gamma  D) James Gosling

**Q2.** SRP means a class should have…
- A) One method  B) One reason to change  C) One responsibility  D) One dependency

**Q3.** "Open/Closed" means…
- A) Open source, closed source  B) Open for extension, closed for modification  C) Open methods, closed classes  D) Open for reading, closed for writing

**Q4.** LSP is named after a mathematician who proposed it in…
- A) 1950  B) 1965  C) 1987  D) 1994

**Q5.** `Stack extends ArrayList` violates which principle?
- A) SRP  B) OCP  C) LSP  D) ISP

**Q6.** `List.of("a").set(0, "b")` throws…
- A) `NullPointerException`  B) `UnsupportedOperationException`  C) `IndexOutOfBoundsException`  D) `ClassCastException`

**Q7.** ISP says interfaces should be split according to…
- A) The implementation's convenience  B) Client usage  C) The number of methods  D) Alphabetical order

**Q8.** A "fat interface" symptom in an implementation is…
- A) `UnsupportedOperationException`  B) `NullPointerException`  C) `ClassCastException`  D) `OutOfMemoryError`

**Q9.** DIP in Java is implemented in practice by…
- A) Static methods  B) Dependency injection  C) `final` classes  D) Singleton

**Q10.** In DIP, where should the interface live?
- A) With the implementation  B) With the consumer  C) In a `common` package  D) In the JDK

**Q11.** Which of these is the *goal* that the other principles enable?
- A) SRP  B) OCP  C) ISP  D) DIP

**Q12.** The "reason to change" usually corresponds to…
- A) A method  B) An actor/team  C) A database table  D) A screen

**Q13.** Constructor injection is preferred over field injection because…
- A) It is faster  B) The dependency is explicit, required, and testable  C) Spring requires it  D) It allows `final` fields only

**Q14.** `synchronized` and `volatile` are examples of which concept?
- A) Encapsulation  B) Information hiding  C) Polymorphism  D) Both are unrelated to SOLID

**Q15.** The best response to a class with 15 methods but only one reason to change is…
- A) Split it into 15 classes  B) Leave it — SRP is satisfied  C) Make it an interface  D) Mark it `final`

**Q16.** "Tell, Don't Ask" is best described as…
- A) A Gang of Four pattern  B) Letting an object manage its own invariants  C) A SOLID principle  D) A testing technique

**Q17.** The Law of Demeter forbids…
- A) `private` fields  B) Method chains like `a.getB().getC().doX()`  C) `static` methods  D) Inheritance

**Q18.** Which principle is violated when a class implements an interface method by throwing `UnsupportedOperationException`?
- A) Only SRP  B) Only ISP  C) ISP and LSP  D) None — it is a valid technique

**Q19.** A `Comparator` inconsistent with `equals` used in a `TreeSet` causes…
- A) A compile error  B) Silent element loss  C) An `IllegalArgumentException`  D) A `ConcurrentModificationException`

**Q20.** Hexagonal (Ports & Adapters) Architecture is DIP applied at the…
- A) Method level  B) Class level  C) Architecture/module level  D) JVM level

**Q21.** "Composition over inheritance" primarily addresses…
- A) Compile time  B) Coupling and the ability to vary implementation  C) Memory usage  D) Code style

**Q22.** Which SOLID principle is most directly about *merge conflicts and change blast radius*?
- A) SRP  B) OCP  C) LSP  D) ISP

**Q23.** Applying ISP to a single-client interface results in…
- A) Better design  B) Over-engineering  C) A compile error  D) An LSP violation

**Q24.** DIP applies to *volatile* dependencies. Which of these is **least** likely to need an abstraction?
- A) A database repository  B) A third-party payment API  C) `java.time.LocalDate`  D) A message broker

**Q25.** The strongest practical signal that a god class should be split is…
- A) Its line count  B) Its number of public methods  C) The number of distinct actors/teams that change it  D) Its name

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | Robert C. Martin ("Uncle Bob") popularised SOLID in 2000. |
| 2 | **B** | One *reason to change* — an axis of change, usually tied to one actor. |
| 3 | **B** | Open for extension, closed for modification. |
| 4 | **C** | Barbara Liskov proposed the substitution principle in a 1987 keynote. |
| 5 | **C** | `Stack` inherits `add(int, E)`, which is meaningless for a stack. |
| 6 | **B** | `List.of` returns an immutable list; `set` is not supported. |
| 7 | **B** | Segregate according to what each **client** actually calls. |
| 8 | **A** | Throwing `UnsupportedOperationException` is the runtime signature of a too-fat interface. |
| 9 | **B** | DIP is implemented through dependency injection, ideally via the constructor. |
| 10 | **B** | With the **consumer** — so the core's shape is not dictated by infrastructure. |
| 11 | **B** | OCP is the goal; SRP, LSP, ISP and DIP are what enable it. |
| 12 | **B** | An actor/team — if two teams change the class on different schedules, they are two reasons. |
| 13 | **B** | The dependency becomes part of the object's construction contract and is trivially substitutable in tests. |
| 14 | **B** | Both are forms of information hiding — the "clients depend on abstractions" idea. |
| 15 | **B** | SRP is about reasons to change, not method count — leave it alone. |
| 16 | **B** | Let the object manage its own invariants instead of the caller inspecting and deciding. |
| 17 | **B** | The Law of Demeter forbids navigating more than one level away from your own collaborators. |
| 18 | **C** | It violates ISP (the client depends on methods it does not use) *and* LSP (the subtype does not honour the contract). |
| 19 | **B** | The `TreeSet` treats `compare == 0` as "same element" and silently discards the second. |
| 20 | **C** | The architecture/module level — the dependency rule between rings. |
| 21 | **B** | It decouples the child from the parent's implementation, so the delegated part can vary. |
| 22 | **B** | OCP is about the *blast radius* of adding behaviour — the file everyone edits. |
| 23 | **B** | Splitting for a single client that uses everything is over-application of ISP. |
| 24 | **C** | `LocalDate` is a stable JDK value type; it will never be swapped. |
| 25 | **C** | The number of distinct actors who change it — the true "reasons to change". |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                            THE 5 PRINCIPLES                              │
├──────┬───────────────────────┬──────────────────────────────────────────┤
│ S    │ ONE REASON TO CHANGE  │ split by ACTOR, not by method              │
│ SRP  │                       │ symptom: a change breaks unrelated things │
├──────┼───────────────────────┼──────────────────────────────────────────┤
│ O    │ EXTEND, NOT MODIFY    │ new requirement = new class               │
│ OCP  │                       │ symptom: merge conflicts in a hot file    │
├──────┼───────────────────────┼──────────────────────────────────────────┤
│ L    │ SUBSTITUTABLE         │ honour pre/post-conditions and exceptions │
│ LSP  │                       │ symptom: UnsupportedOperationException    │
├──────┼───────────────────────┼──────────────────────────────────────────┤
│ I    │ SMALL, CLIENT-DRIVEN │ segment by what each client CALLS          │
│ ISP  │                       │ symptom: a client implements what it never│
│      │                       │   uses                                    │
├──────┼───────────────────────┼──────────────────────────────────────────┤
│ D    │ DEPEND ON            │ the port lives with the CONSUMER           │
│ DIP  │ ABSTRACTIONS          │ symptom: untestable without the real thing│
└──────┴───────────────────────┴──────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE RELATIONSHIP — OCP IS THE GOAL                   │
│                                                                          │
│      SRP ──┐                                                             │
│      LSP ──┼──▶  OCP : a new requirement adds NEW code,                 │
│      ISP ──┤         and does not modify existing code                   │
│      DIP ──┘                                                             │
│                                                                          │
│   THE MEASURE: "how many existing files must I edit for requirement X?" │
│   THE GOAL  : that number should be as close to ZERO as possible.        │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE DECISION PROCEDURE                               │
│                                                                          │
│  1. Find the VOLATILE SEAMS (db, 3rd-party, broker, config)            │
│  2. At each seam: "who asks me to change this?" → 1 actor = OK          │
│  3. Hunt the RUNTIME SYMPTOMS (USE, CCE, merge conflicts, regressions) │
│  4. Choose the SMALLEST structure that removes the observed pain        │
│  5. RE-EVALUATE on every requirement — that is when a refactor is safe  │
│                                                                          │
│  ⚠ Do NOT apply SOLID globally, in advance, on faith.                   │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│              THE 4 THAT LOOK SIMILAR — DISAMBIGUATE                     │
├──────────┬───────────────────────────────────────────────────────────────┤
│ SRP      │ how many ACTORS change this class?                            │
│ OCP      │ does a new requirement EDIT existing code?                    │
│ LSP      │ can a client written against the PARENT use the CHILD?       │
│ ISP      │ does a client depend on methods it never CALLS?               │
│ DIP      │ does the CORE know the DETAIL technology?                     │
└──────────┴───────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The Spring mapping

| SOLID principle | Spring/Java mechanism |
|---|---|
| SRP | One `@Service` per use case; a `@Component` per cohesive concern. |
| OCP | `Strategy` beans selected with `@Qualifier`; a new provider = a new `@Component`. |
| LSP | Spring's `HandlerAdapter` contract; `JdbcTemplate` honouring the `DataSource` contract. |
| ISP | Spring's small, focused interfaces (`Aware`, `InitializingBean`, `ApplicationListener`). |
| DIP | Constructor injection everywhere; the container is the composition root. |

### The "god class" refactoring recipe (safe order)

```text
   1. CHARACTERISE   write tests that pin the CURRENT behaviour
                     (a god class usually has none — this is the safety net)

   2. EXTRACT LEAF   pull out a collaborator with no callers of its own
                     (a DateTimeProvider, a TaxCalculator)
                     → ZERO call sites change

   3. INTRODUCE PORT create the interface in the CONSUMER's package
                     make the existing class implement it
                     inject it
                     → DIP achieved, no behaviour change

   4. MOVE BEHAVIOUR one reason-to-change per PR
                     → each PR is reviewable in isolation

   5. DELETE         remove the old path in the SAME PR
                     → never leave two paths alive
```

### The rules that survive a code review

| Rule | Why |
|---|---|
| Constructor injection, always | The dependency is visible, required, and testable |
| No `new` of a collaborator inside a business method | That is a hidden dependency — DIP in miniature |
| One `@Service` per use case, not per controller | The controller is an adapter, not the domain |
| Extract a `Clock` and never call `now()` directly | Testable time; no flaky tests |
| Prefer a `record` for a value object | Immutable, `equals`/`hashCode` free, no setters |
| Keep the port in the consumer's package | The core's shape is not dictated by infrastructure |
| An interface needs a second implementation | Otherwise use the class |

### Metrics worth watching

| Metric | What it tells you |
|---|---|
| Files changed per requirement | The OCP score — should be small and stable |
| Classes with the most distinct authors | The SRP score — many authors = many reasons |
| Number of `UnsupportedOperationException` throws | The ISP score — should be zero |
| Test setup time (Spring context vs plain) | The DIP score — a Spring context means no DIP |
| Cyclomatic complexity per method | A smell proxy for SRP/OCP violations |
| `new` keyword count in service packages | The DIP score — non-zero is a smell |

### The "is this worth it?" checklist before a refactor

```text
   ☐ Is there OBSERVED pain (regressions, merge conflicts, slow tests)?
   ☐ Is the change isolated enough to review on its own?
   ☐ Do we have a test that pins the current behaviour?
   ☐ Is the new structure SMALLER or SIMPLER in total, not just more correct?
   ☐ Can the refactor be reverted independently if it goes wrong?
   → if any answer is NO, defer it. SOLID is a means, not a goal.
```

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The patterns that implement SOLID | [09 · Design Patterns](../09-Design-Patterns/01-Design-Patterns.md) |
| Encapsulation & abstraction | [03 · Encapsulation vs Abstraction](../01-Core-Java-OOP/03-OOP-Encapsulation-vs-Abstraction.md) |
| The `equals`/`hashCode` contract (a SOLID-adjacent trap) | [08 · equals, hashCode & toString](../01-Core-Java-OOP/08-OOP-equals-hashCode-toString.md) |
| SOLID in action | [11 · DSA](../11-DSA/README.md) — design a clean API for a problem |
| The original sources | Robert C. Martin, *Design Principles and Design Patterns* (2000); *Clean Architecture* (2017) |
| DDD | Vaughn Vernon, *Implementing Domain-Driven Design* (2013) |

---

**Next Chapter:** → [11 · Data Structures & Algorithms](../11-DSA/01-DSA-Quick-Reference.md)
**Previous Chapter:** ← [09 · Design Patterns](../09-Design-Patterns/01-Design-Patterns.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
