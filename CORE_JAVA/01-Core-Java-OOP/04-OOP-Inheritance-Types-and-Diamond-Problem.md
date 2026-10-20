# Inheritance Types & the Diamond Problem — The Complete Interview Guide

> **Java has single inheritance of classes — and the reason is the diamond
> problem.** This chapter covers the four kinds of inheritance, the diamond
> problem itself, the four solutions Java offers, and the rule that decides
> between them: **prefer composition unless the relationship is genuinely
> substitutable**.

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

- **Java supports only single inheritance of *classes*** — a class has exactly one `superclass`. But a class may implement **any number of interfaces**.
- **The four kinds of inheritance:** **single**, **multi-level**, **hierarchical**, and **hybrid** (a mix of hierarchical and multiple).
- **The diamond problem:** `D extends B, C` where `B` and `C` both extend `A` — the compiler cannot decide whether to use `A`'s implementation once or twice.
- **Java's solution for classes:** there is none — that is *why* Java has single class inheritance. You use **composition** instead.
- **Java's solution for interfaces (Java 8+):** `default` methods. If `B` and `C` both provide a `default`, the class **must** override it — and may disambiguate with `B.super.m()` / `C.super.m()`.
- **The four "solutions" to the diamond** are: (1) **replicate the code** (C++'s answer — duplication), (2) **inherit it** (C++'s virtual inheritance — not in Java), (3) **use composition** (Java's idiomatic answer), (4) **use `default` methods + explicit override** (Java's answer for interfaces).
- **`super` is not "the parent"** — it is "the direct superclass", and it is a **static** reference (not polymorphic). `super.method()` always calls the parent's version; `this.method()` calls the override.
- **`super()` must be the FIRST statement** of a constructor — and it is **implicit** if you omit it.
- **A `final` class cannot be extended** — the JDK uses it to close hierarchies (`String`, `Integer`).
- **The `instanceof` pattern (Java 16+)** replaces the "cast after checking" idiom: `if (o instanceof Circle c) { c.radius(); }`.
- **The rule:** *inherit when the subtype can stand in for the supertype **everywhere**; compose when it merely uses it.*

---

## 1. What & Why — The Problem This Solves

**The problem.** Inheritance is the most misused feature in OO. It is powerful
(substitutability, code reuse) and dangerous (tight coupling, the fragile base class
problem, the diamond problem).

**The two forces inheritance balances:**

```text
   THE PULL OF INHERITANCE          THE PUSH TOWARD COMPOSITION
   ─────────────────────────          ─────────────────────────────
   • code reuse                        • no coupling to the base's internals
   • substitutability (polymorphism)   • the base can change freely
   • a natural "is-a" taxonomy         • works when the relationship is "has-a"
   • less code                         • more code (a delegate field)
```

**And the failure modes of over-using inheritance:**

| Failure | What it looks like |
|---|---|
| **The fragile base class** | Changing the base breaks every subclass, even ones that do not use the changed part |
| **The diamond problem** | Ambiguous inherited state and behaviour |
| **Substitution failure** | The child breaks the parent's contract (`Stack extends ArrayList`) |
| **Deep hierarchies** | `A → B → C → D` where nobody can say what `D` does |
| **"Spaghetti inheritance"** | A hierarchy with no semantic meaning — just shared code in the wrong place |

> 💡 **The interview framing.** The question is never "should I use inheritance?"
> It is **"is this relationship substitutable?"** If a `Circle` can be used wherever a
> `Shape` is expected — everywhere, without exception — inherit. If it merely *uses*
> a `Shape`, compose.

---

## 2. Mental Model — The One Picture

```text
   THE FOUR KINDS OF INHERITANCE
   ═════════════════════════════

   ① SINGLE              ② MULTI-LEVEL           ③ HIERARCHICAL
   ┌────────┐            ┌────────┐               ┌────────┐
   │   A    │            │   A    │               │   A    │
   └───┬────┘            └───┬────┘               └────┬───┘
       │ extends              │ extends                 │
   ┌───▼────┐            ┌───▼────┐              ┌────▼───┐  ┌────▼───┐
   │   B    │            │   B    │              │   B    │  │   C    │
   └────────┘            └───┬────┘              └────────┘  └────────┘
                             │ extends
                         ┌───▼────┐
                         │   C    │
                         └────────┘

   ④ HYBRID (hierarchical + multiple)
   ┌────────┐
   │   A    │
   └───┬────┘
       │ extends
   ┌───▼────┐   implements   ┌────────┐
   │   B    │ ─────────────▶ │  I, J  │
   └───┬────┘                └────────┘
       │ extends
   ┌───▼────┐
   │   C    │
   └────────┘


   THE DIAMOND — why Java banned multiple class inheritance
   ═════════════════════════════════════════════════════════
                    ┌────────┐
                    │   A    │  ← ONE implementation, or TWO?
                    └───┬────┘
              ┌─────────┴─────────┐
              ▼                   ▼
         ┌────────┐           ┌────────┐
         │   B    │           │   C    │
         └───┬────┘           └───┬────┘
             └─────────┬─────────┘
                       ▼
                  ┌────────┐
                  │   D    │  ← which A's state does D have?
                  └────────┘

   THE AMBIGUITY:
     • STATE  — does D have ONE `a` field or TWO?
     • BEHAVIOUR — if A.m() is overridden in both B and C, which runs?
   C++ solved it with "virtual inheritance" (one shared A).
   JAVA CHOSE: no multiple class inheritance at all → use COMPOSITION.
```

---

## 3. Deep Dive — Theory

## 3.1 The four kinds, in code

```java
// ① SINGLE inheritance
class Animal { }
class Dog extends Animal { }

// ② MULTI-LEVEL inheritance — Puppy → Dog → Animal
class Puppy extends Dog { }

// ③ HIERARCHICAL inheritance — many children, one parent
class Vehicle { }
class Car extends Vehicle { }
class Truck extends Vehicle { }

// ④ HYBRID — hierarchical + multiple (via INTERFACES)
class Logger { }
interface Serializable2 { }
class FileManager extends Logger implements Serializable2 { }
```

**The limits:**

| | Classes | Interfaces |
|---|---|---|
| How many parents? | **1** | **unlimited** |
| Can the parent have state? | ✅ | ❌ |
| Can the parent have a constructor? | ✅ | ❌ |
| Can the parent have code? | ✅ | ✅ (`default`/`static`, Java 8+) |

## 3.2 The diamond problem — the precise ambiguity

```java
// ❌ NOT COMPILABLE IN JAVA — and that is the POINT
//    class D extends B, C { }   ← javac: "duplicate class: A"
class A {
    protected int value;
    public void greet() { System.out.println("A.greet"); }
}
class B extends A { }
class C extends A { }
```

**The two ambiguities:**

```text
   THE STATE AMBIGUITY
   ───────────────────
   If Java allowed it, would D have:
     • ONE `value` field (shared, like C++ virtual inheritance), or
     • TWO `value` fields (one from B's A, one from C's A)?
   → the answer changes the semantics of `d.value`

   THE BEHAVIOUR AMBIGUITY
   ────────────────────────
   If B and C both override `greet()`:
     d.greet()  →  B's version?  C's version?  A's version?
   → C++ resolved it with virtual inheritance + virtual methods.
      Java did not: it removed the possibility.
```

**Why Java's answer was to remove the feature:** the ambiguity is not just a syntax
problem — it is a **semantic** one with no universally "right" answer. If `B` and `C`
each maintain their own copy of `A`'s state, then `d.value` is ambiguous; if they
share one, then `B`'s updates are visible through `C` — which some consider a
feature (shared state) and others a bug (unexpected coupling). C++ chose "shared";
Java chose "not possible".

## 3.3 The four solutions, and which one Java uses where

| # | Solution | Mechanism | Where it appears |
|---|---|---|---|
| 1 | **Replicate the code** | Copy the implementation into each branch | C++'s default — duplication, and the two copies can diverge |
| 2 | **Inherit it** | "Virtual inheritance" — one shared base subobject | C++ only; **not in Java** |
| 3 | **Composition** | Hold a reference instead of inheriting | ✅ **Java's idiomatic answer for classes** |
| 4 | **`default` methods + explicit override** | The implementing class must resolve the conflict | ✅ **Java's answer for interfaces (Java 8+)** |

**Solution 3 — composition (the Java way for classes):**

```java
// ❌ The diamond (illegal in Java):
//    class D extends B, C { }

// ✅ Composition — no ambiguity at all
class B {
    protected int value;
    public void greet() { System.out.println("B.greet"); }
    public int value() { return value; }
}
class C {
    protected int value;
    public void greet() { System.out.println("C.greet"); }
    public int value() { return value; }
}
class D {
    private final B b = new B();
    private final C c = new C();

    public void greet() {
        b.greet();
        c.greet();          // ✅ EXPLICIT — you decide what happens
    }

    public int value() {
        return b.value() + c.value();   // ✅ explicit: sum, or take one, or whatever
    }
}
```

**The benefit is not just legality — it is that the ambiguity becomes a *decision*.**
With `default` methods, the compiler says "you must choose"; with composition, you
*choose* in the code, visibly.

**Solution 4 — `default` methods (the Java way for interfaces):**

```java
interface A {
    default String greet() { return "A"; }
}
interface B extends A {
    @Override default String greet() { return "B"; }
}
interface C extends A {
    @Override default String greet() { return "C"; }
}

// ⚠ B and C are UNRELATED to each other (both extend A) → the conflict is unresolved
//    class D implements B, C { }        // ❌ COMPILE ERROR: "inherits unrelated defaults"

// ✅ The class MUST override and may disambiguate
class D implements B, C {
    @Override
    public String greet() {
        return B.super.greet() + " + " + C.super.greet();   // "B + C"
    }
}
```

> 💡 **Note the subtlety.** If `B extends A` and `C extends A`, and `D implements B, C`
> — then B and C are *unrelated* to each other (they are siblings), so their `default`
> methods conflict. But if `C extends B`, then C's `default` **overrides** B's — no
> conflict at all. The rule is the "most specific wins" dominance rule.

## 3.4 `super` — what it really is

```java
class Parent {
    protected String name = "parent";
    Parent() { System.out.println("Parent()"); }
    Parent(String name) { this.name = name; }
    void display() { System.out.println("Parent.display: " + name); }
}

class Child extends Parent {
    private final String extra;

    Child() {
        super();                    // ⚠ MUST be the FIRST statement — and it is implicit
        this.extra = "child";
    }

    Child(String name) {
        super(name);                // ✅ pass to the parent's constructor
        this.extra = "child";
    }

    @Override
    void display() {
        super.display();            // ✅ explicitly call the PARENT's version
        System.out.println("Child.display: " + extra);
    }
}
```

**The three rules:**

| Rule | Detail |
|---|---|
| `super()` must be **first** | In a constructor, `super(...)` (or `this(...)`) must be the first statement. If you omit it, the compiler inserts `super()`. |
| `super` is **not polymorphic** | `super.display()` calls `Parent.display()` **always** — it is a *static* binding to the direct superclass. `this.display()` calls the **overridden** version. |
| `super` refers to the **direct** superclass only | To reach a grandparent, you must go through it (`super.super()` is not Java). |

**`super` vs `this` — the key contrast:**

```text
   this.display()   →  VIRTUAL dispatch  →  the OVERRIDDEN version (if any)
   super.display()  →  STATIC binding    →  the DIRECT parent's version, always
```

**The `super.field` case — a classic trap:**

```java
class Parent {
    protected String name = "parent";
}
class Child extends Parent {
    private String name = "child";     // ⚠ SHADOWS the parent's field

    void print() {
        System.out.println(name);        // "child"   (this.name)
        System.out.println(this.name);   // "child"
        System.out.println(super.name);  // "parent"  ✅
    }
}
```

> ⚠ **`super.field` is NOT polymorphic.** If a grandchild exists and the *parent's*
> `name` is itself shadowed, `super.name` gives the *direct parent's* field — it does
> not walk up the hierarchy. Field access is always statically bound.

## 3.5 `final` — closing a hierarchy

```java
public final class String { }         // ❌ cannot be extended
public final void method() { }        // ❌ cannot be overridden
public final static int CONST = 1;    // a constant
```

**Why `final` matters for inheritance:**

| Use | Effect |
|---|---|
| `final class` | The hierarchy is **closed** — no subclass can break your invariants |
| `final method` | The behaviour is **fixed** — a template method's skeleton cannot change |
| `final` in a constructor | A subclass cannot override the method the constructor calls — the classic "calling an overridable method from a constructor" bug |

**The "call to an overridable method in the constructor" bug:**

```java
class Base {
    Base() {
        init();                 // ⚠ calls the OVERRIDDEN version — before the subclass's
    }                          //   fields are initialised 💥
    void init() { }
}

class Child extends Base {
    private final String value = "child";
    @Override void init() {
        System.out.println(value);   // 💥 null! (the field initialiser has not run yet)
    }
}
```

**✅ The fixes:**

```java
// ① Make the method final — the subclass cannot override it
class Base {
    Base() { init(); }
    final void init() { }
}

// ② Use a static factory method instead of a public constructor
class Child extends Base {
    private final String value;
    private Child(String value) { this.value = value; }
    static Child create() { return new Child("child"); }
}
```

## 3.6 The `instanceof` pattern (Java 16+)

```java
// ❌ BEFORE — a cast that can fail
if (shape instanceof Circle) {
    Circle c = (Circle) shape;     // ⚠ two operations; the cast can throw
    System.out.println(c.radius());
}

// ✅ AFTER — the pattern binds the variable safely
if (shape instanceof Circle c) {
    System.out.println(c.radius());   // ✅ no cast, no risk
}

// ✅ The pattern is in scope in the whole if-block (flow scoping)
if (shape instanceof Circle c && c.radius() > 10) { … }

// ⚠ NOT in scope in the NEGATIVE branch (the dominance rule)
if (!(shape instanceof Circle c)) {
    return;                         // ⚠ `c` is NOT in scope here
}
```

**The `instanceof` + pattern replaces the "cast or throw" idiom:**

```java
// ❌ BEFORE
Circle c = (Circle) shape;                 // 💥 ClassCastException at runtime
if (c instanceof Circle) { … }             // ⚠ unreachable

// ✅ AFTER
if (shape instanceof Circle c) { … }        // ✅ checked and cast in one step
```

## 3.7 Composition over inheritance — the practical test

```java
// ❌ INHERITANCE — "a stack IS an array list" (it is not!)
class BadStack<E> extends ArrayList<E> {
    @Override public void add(int index, E element) {
        throw new UnsupportedOperationException();   // 💥 breaks the List contract
    }
}

// ✅ COMPOSITION — "a stack HAS a deque"
class Stack<E> {
    private final Deque<E> items = new ArrayDeque<>();
    public void push(E e) { items.push(e); }
    public E pop() { return items.pop(); }
    public int size() { return items.size(); }
}
```

**The decisive argument for composition:**

```text
   WITH INHERITANCE
   ─────────────────
   Stack extends ArrayList
     → Stack inherits add(int,E), remove(int), listIterator, subList, sort, …
     → EACH of those is meaningless for a stack
     → you must either override it to throw (breaking the contract) or
       inherit a method that corrupts the data structure
     → the child's API is the PARENT's API plus the child's own — and you
       cannot remove what you have inherited

   WITH COMPOSITION
   ─────────────────
   Stack has a Deque
     → Stack's API is EXACTLY what a stack should offer
     → the compiler prevents the misuse
     → you can change the delegate later without touching the callers

   THE GENERAL RULE:
   inheritance gives the child EVERYTHING the parent has, including the
   things the child must not have. Composition gives the child EXACTLY
   what it chooses to expose.
```

**The classic "inheritance for code reuse" trap:**

```java
// ❌ REUSE VIA INHERITANCE — a semantically wrong relationship
//    class ArrayListStack extends ArrayList { }   // "a stack is an array list" 💥

// ✅ REUSE VIA COMPOSITION — the correct relationship
class Stack<E> {
    private final List<E> items = new ArrayList<>();

    public void push(E item) { items.add(item); }
    public E pop() { return items.remove(items.size() - 1); }
    public int size() { return items.size(); }
}
```

> 💡 **The refactoring catalogue (Refactoring Guru):** *Replace Inheritance with
> Composition* and *Extract Superclass* are the two refactorings that address this
> directly. The first is the right move for a "has-a" relationship; the second is the
> right move when you notice the **duplicated code** between two siblings — and
> extracting a *superclass* is only safe when the two really are the same kind of
> thing.

## 3.8 What the JDK does — the evidence

| JDK type | Relationship | Note |
|---|---|---|
| `ArrayList` → `AbstractList` → `AbstractCollection` → `Object` | Multi-level | The **shared state** (`modCount`, `size`) lives in the abstract base |
| `HashSet` → `AbstractSet` → `AbstractCollection` | Single | `AbstractSet` holds no state — it exists to share the `equals`/`hashCode` contract |
| `Thread` → `Object` | Single | A thread "is a" object with thread behaviour |
| `Integer` → `Number` → `Object` | Multi-level | The shared `intValue()` contract |
| `ArrayList` implements `List`, `Collection`, `Iterable` | Multiple (interfaces) | **Capabilities**, not a taxonomy |

> 💡 **The pattern in the JDK:** the JDK uses **multi-level inheritance** where there
> is genuine shared *state* (`AbstractList` holds `modCount`), and **interfaces** where
> there is only a shared *contract* (`List` adds nothing but methods). It **never**
> uses inheritance merely to share code between unrelated types.

---

## 4. Visualizations

### 4.1 The four kinds of inheritance

```text
   ① SINGLE                  ② MULTI-LEVEL            ③ HIERARCHICAL
   ┌────────┐                ┌────────┐                ┌────────┐
   │   A    │                │   A    │                │   A    │
   └───┬────┘                └───┬────┘                └────┬───┘
       │ extends                  │ extends                   │ extends
   ┌───▼────┐                ┌───▼────┐               ┌────▼───┐  ┌────▼───┐
   │   B    │                │   B    │               │   B    │  │   C    │
   └────────┘                └───┬────┘               └────────┘  └────────┘
                                │ extends
                            ┌───▼────┐
                            │   C    │
                            └────────┘

   ④ HYBRID
   ┌────────┐
   │   A    │
   └───┬────┘
       │ extends
   ┌───▼────┐   implements   ┌────────┐
   │   B    │ ─────────────▶ │  I, J  │
   └───┬────┘                └────────┘
       │ extends
   ┌───▼────┐
   │   C    │
   └────────┘
```

### 4.2 The diamond — the state and the behaviour ambiguity

```text
                    ┌────────┐
                    │   A    │   value = 0 ;  void greet() { … }
                    └───┬────┘
              ┌─────────┴─────────┐
              ▼                   ▼
         ┌────────┐           ┌────────┐
         │   B    │           │   C    │
         └────────┘           └────────┘
              └─────────┬─────────┘
                        ▼
                   ┌────────┐
                   │   D    │
                   └────────┘

   THE TWO AMBIGUITIES:
   ┌────────────────────────────────────────────────────────────────┐
   │ ① STATE: does D have ONE `value` (shared) or TWO (duplicated)?  │
   │    → C++: virtual inheritance = ONE shared                      │
   │    → Java: ILLEGAL — so the question never arises               │
   ├────────────────────────────────────────────────────────────────┤
   │ ② BEHAVIOUR: if B and C both override greet(), which runs?      │
   │    → C++: virtual methods resolve it                            │
   │    → Java with `default`: the class MUST override and choose   │
   └────────────────────────────────────────────────────────────────┘
```

### 4.3 Java's answers — composition and `default` methods

```text
   ✅ SOLUTION A — COMPOSITION (for classes)
   ───────────────────────────────────────
   class D {
       private final B b = new B();
       private final C c = new C();

       void greet() {
           b.greet();        // ✅ EXPLICIT — you decide
           c.greet();
       }
       int value() { return b.value() + c.value(); }   // ✅ the ambiguity becomes a DECISION
   }

   ✅ SOLUTION B — default METHODS (for interfaces, Java 8+)
   ──────────────────────────────────────────────────────
   interface A { default String greet() { return "A"; } }
   interface B extends A { default String greet() { return "B"; } }
   interface C extends A { default String greet() { return "C"; } }

   class D implements B, C { }        ❌ COMPILE ERROR (B and C are siblings)

   class D implements B, C {
       @Override public String greet() {
           return B.super.greet() + " & " + C.super.greet();   // ✅ EXPLICIT
       }
   }

   ⚠ BUT if C extends B, then C's default OVERRIDES B's → no conflict at all
     (the "most specific wins" dominance rule)
```

### 4.4 `super` vs `this` — static vs virtual binding

```text
   class Parent { void display() { print("parent"); } }
   class Child  extends Parent { @Override void display() { print("child"); } }

   THIS.SUPER DISPATCH
   ──────────────────
   this.display()   →  VIRTUAL  →  "child"    (the OVERRIDDEN version)
   super.display()  →  STATIC   →  "parent"   (the DIRECT parent's version, ALWAYS)

   ⚠ super is NOT polymorphic. It is a static reference to the
     DIRECT superclass — it never walks further up.
```

### 4.5 The shadowing trap

```text
   class Parent { protected String name = "parent"; }
   class Child  extends Parent {
       private String name = "child";       // ⚠ SHADOWS the parent's field
       void print() {
           System.out.println(name);         // "child"   (this.name)
           System.out.println(this.name);    // "child"
           System.out.println(super.name);   // "parent"  ✅
       }
   }

   ⚠ `super.name` is STATICALLY bound to the DIRECT parent's field.
     If the parent also shadows ITS parent, `super.name` gives the
     parent's — not the grandparent's.
     → FIELD ACCESS IS NEVER POLYMORPHIC.
```

### 4.6 The "overridable method called from a constructor" bug

```text
   class Base {
       Base() { init(); }               // ⚠ calls the OVERRIDDEN version
   }
   class Child extends Base {
       private final String value = "child";
       @Override void init() {
           System.out.println(value);    // 💥 null!  the field initialiser
       }                                //    has NOT run yet
   }

   THE TIMELINE:
   ┌────────┬──────────────────────────────────────────────────┐
   │ STEP   │ WHAT HAPPENS                                        │
   ├────────┼──────────────────────────────────────────────────┤
   │ 1      │ Base() runs → calls init()                         │
   │ 2      │ the OVERRIDDEN init() runs (dynamic dispatch)       │
   │ 3      │ ⚠ Child's field initialisers have NOT run yet     │
   │ 4      │ value is still null → 💥 NPE or a wrong value      │
   │ 5      │ THEN Child's field initialisers run                │
   │ 6      │ THEN Child's constructor body runs                 │
   └────────┴──────────────────────────────────────────────────┘

   THE FIXES:
     ① final void init()   → the subclass cannot override it
     ② a static factory    → the object is fully built before use
```

### 4.7 Inheritance vs composition — the API surface

```text
   ❌ INHERITANCE
   ───────────────
   class Stack<E> extends ArrayList<E> { … }

   Stack's API = ArrayList's API + Stack's own:
     add(E) ✅  add(int,E) ❌  remove(int) ❌  get(int) ❌
     sort() ❌  listIterator() ❌  subList() ❌  clear() ❌  …
   → you INHERITED all the operations a stack must not have 💥

   ✅ COMPOSITION
   ───────────────
   class Stack<E> {
       private final Deque<E> items = new ArrayDeque<>();
   }

   Stack's API = EXACTLY:
     push(E) pop() peek() size() isEmpty() iterator()
   → the compiler prevents the misuse ✅
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — The diamond problem, and Java's two answers

```java
import java.util.ArrayDeque;
import java.util.Deque;

public class DiamondDemo {

    // ═══ THE CLASS DIAMOND — ILLEGAL IN JAVA ═══
    // class A { protected int value; public void greet() { print("A"); } }
    // class B extends A { }
    // class C extends A { }
    // class D extends B, C { }        ❌ "duplicate class: A"

    // ═══ ✅ ANSWER 1 — COMPOSITION ═══
    static class B {
        protected int value = 1;
        public void greet() { System.out.println("  B.greet"); }
    }
    static class C {
        protected int value = 2;
        public void greet() { System.out.println("  C.greet"); }
    }
    static class D {
        private final B b = new B();
        private final C c = new C();

        public void greet() {
            b.greet();
            c.greet();                     // ✅ EXPLICIT — you decide
        }
        public int value() {
            return b.value + c.value;      // ✅ the ambiguity became a DECISION
        }
    }

    // ═══ ✅ ANSWER 2 — default METHODS (for interfaces) ═══
    interface IA { default String greet() { return "A"; } }
    interface IB extends IA { @Override default String greet() { return "B"; } }
    interface IC extends IA { @Override default String greet() { return "C"; } }

    // ❌ IClass implements IB, IC { }   → "inherits unrelated defaults"
    static class DImpl implements IB, IC {
        @Override
        public String greet() {
            return IB.super.greet() + " + " + IC.super.greet();   // ✅ disambiguate
        }
    }

    public static void main(String[] args) {
        System.out.println("=== COMPOSITION (classes) ===");
        D d = new D();
        d.greet();
        System.out.println("  value = " + d.value());

        System.out.println();
        System.out.println("=== default METHODS (interfaces) ===");
        System.out.println("  " + new DImpl().greet());
    }
}
```

**Actual output:**

```text
=== COMPOSITION (classes) ===
  B.greet
  C.greet
  value = 3

=== default METHODS (interfaces) ===
  B + C
```

**Takeaway:** Java's two answers both end the same way — **the ambiguity becomes an
explicit decision in your code**, rather than an implicit one in the compiler.

### Example 2 — `super` vs `this`, and the shadowing trap

```java
public class SuperDemo {

    static class Parent {
        protected String name = "parent";
        protected void display() { System.out.println("  Parent.display: " + name); }
    }

    static class Child extends Parent {
        private final String name = "child";      // ⚠ SHADOWS the parent's field

        @Override
        protected void display() {
            super.display();                       // ✅ the PARENT's version
            System.out.println("  Child.display: " + name);
        }

        void printAll() {
            System.out.println("  name          = " + name);        // "child"
            System.out.println("  this.name     = " + this.name);   // "child"
            System.out.println("  super.name    = " + super.name);  // "parent" ✅
        }
    }

    public static void main(String[] args) {
        Child c = new Child();
        c.display();
        c.printAll();
    }
}
```

**Actual output:**

```text
  Parent.display: parent
  Child.display: child
  name          = child
  this.name     = child
  super.name    = parent
```

**Takeaway:** `super.display()` calls the parent's version (static binding), while
`this.display()` would call the child's (virtual). And `super.name` reaches the
*direct* parent's field — field access is never polymorphic.

### Example 3 — The "overridable method called from a constructor" bug

```java
public class ConstructorBugDemo {

    static class Base {
        Base() {
            init();                 // ⚠ the OVERRIDDEN version runs here
            System.out.println("  Base() finished");
        }
        void init() { System.out.println("  Base.init"); }
    }

    static class BrokenChild extends Base {
        private final String value = "child-value";
        @Override void init() {
            System.out.println("  Child.init, value = " + value);   // 💥 null!
        }
    }

    // ✅ THE FIX — a final method
    static class SafeBase {
        SafeBase() { init(); }
        final void init() { System.out.println("  SafeBase.init"); }
    }
    static class SafeChild extends SafeBase {
        private final String value = "child-value";
        // ⚠ you CANNOT override init() here — it is final in SafeBase.
        //    That is exactly the point: the constructor now calls a method that
        //    the subclass provably cannot change.
    }

    public static void main(String[] args) {
        System.out.println("=== BROKEN ===");
        new BrokenChild();

        System.out.println();
        System.out.println("=== FIXED (final method) ===");
        new SafeChild();
    }
}
```

**Actual output:**

```text
=== BROKEN ===
  Child.init, value = null      ← 💥 the field initialiser has not run yet
  Base() finished

=== FIXED (final method) ===
  SafeBase.init
```

**Takeaway:** the subclass's field initialisers run **after** the superclass
constructor, so a virtual call from the constructor sees `null`. The fixes are a
`final` method or a static factory.

### Example 4 — Composition beats inheritance: the `Stack` case

```java
import java.util.ArrayDeque;
import java.util.Deque;

public class StackVsInheritance {

    // ❌ INHERITANCE — inherits operations a stack must not have
    static class BadStack<E> extends java.util.ArrayList<E> {
        @Override public void add(int index, E element) {
            throw new UnsupportedOperationException("a stack has no indexed add");
        }
    }

    // ✅ COMPOSITION — the API is EXACTLY a stack's
    static class GoodStack<E> {
        private final Deque<E> items = new ArrayDeque<>();
        public void push(E e) { items.push(e); }
        public E pop() { return items.pop(); }
        public E peek() { return items.peek(); }
        public int size() { return items.size(); }
        public boolean isEmpty() { return items.isEmpty(); }
    }

    public static void main(String[] args) {
        BadStack<String> bad = new BadStack<>();
        bad.add("a");
        try {
            bad.add(0, "b");            // 💥 inherited, meaningless, broken
        } catch (UnsupportedOperationException e) {
            System.out.println("❌ inherited add(int, E): " + e.getMessage());
        }

        GoodStack<String> good = new GoodStack<>();
        good.push("a");
        good.push("b");
        System.out.println("✅ composed stack: pop=" + good.pop() + " peek=" + good.peek()
                + " size=" + good.size());
        System.out.println("   and there is NO add(int, E) to misuse ✅");
    }
}
```

**Actual output:**

```text
❌ inherited add(int, E): a stack has no indexed add
✅ composed stack: pop=b peek=a size=1
   and there is NO add(int, E) to misuse ✅
```

**Takeaway:** inheritance gives the child **everything** the parent has, including
the operations it must not have. Composition gives it **exactly** what it chooses.

### Example 5 — The `instanceof` pattern

```java
public class InstanceOfPatternDemo {

    sealed interface Shape permits Circle, Square { }
    record Circle(double radius) implements Shape { }
    record Square(double side) implements Shape { }

    public static void main(String[] args) {
        Object shape = new Circle(3.0);

        // ❌ BEFORE — a cast that can throw
        //    Circle c = (Circle) shape;                     // 💥 ClassCastException
        //    if (shape instanceof Circle) { ... }           // ⚠ the cast above already happened

        // ✅ AFTER — the pattern binds safely
        if (shape instanceof Circle c) {
            System.out.println("✅ radius = " + c.radius());
        }

        // ✅ Flow scoping — the binding is available after the condition
        if (shape instanceof Circle c && c.radius() > 1) {
            System.out.println("✅ the binding is in scope in the && chain");
        }

        // ✅ Exhaustive switch over a sealed hierarchy.
        //    ⚠ The static type MUST be the sealed type — a switch over `Object`
        //    cannot be proven exhaustive, so javac demands a `default`.
        Shape sealedShape = new Circle(3.0);
        String describe = switch (sealedShape) {
            case Circle c -> "circle r=" + c.radius();
            case Square s -> "square s=" + s.side();
        };
        System.out.println("✅ sealed switch = " + describe);
    }
}
```

**Actual output:**

```text
✅ radius = 3.0
✅ the binding is in scope in the && chain
✅ sealed switch = circle r=3.0
```

**Takeaway:** the pattern removes the "check then cast" split, and Java 21's
pattern-matching `switch` over a sealed hierarchy removes the `default` branch.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A hierarchy built on composition — the "diamond-free" way

```java
package com.example.hierarchy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A notification system built on COMPOSITION, not inheritance.
 *
 * <p>This is the shape most production frameworks converge on: a small number of
 * *stable* abstractions (interfaces), and composition where the "diamond" would
 * otherwise appear.
 */
public final class Notifications {

    /** The capability — a thing that can be delivered. */
    public interface Channel {
        String name();

        /** @return true if the message was accepted */
        boolean send(String recipient, String message);
    }

    /** The composed implementation — it holds the channels rather than extending them. */
    public static final class NotificationService {

        private final List<Channel> channels;

        public NotificationService(List<Channel> channels) {
            this.channels = List.copyOf(channels);
        }

        /**
         * Sends to every channel, collecting the per-channel outcome.
         * A failing channel does NOT prevent the others from being tried.
         */
        public List<String> broadcast(String recipient, String message) {
            var results = new java.util.ArrayList<String>();
            for (Channel channel : channels) {
                try {
                    boolean accepted = channel.send(recipient, message);
                    results.add(channel.name() + ": " + (accepted ? "sent" : "rejected"));
                } catch (RuntimeException e) {
                    results.add(channel.name() + ": failed (" + e.getClass().getSimpleName() + ")");
                }
            }
            return results;
        }

        /** Sends to the FIRST channel that accepts — a fallback chain. */
        public Optional<String> sendToFirstAccepting(String recipient, String message) {
            for (Channel channel : channels) {
                try {
                    if (channel.send(recipient, message)) {
                        return Optional.of(channel.name());
                    }
                } catch (RuntimeException e) {
                    // try the next one
                }
            }
            return Optional.empty();
        }
    }

    // ── Concrete channels — they implement the capability, they do not extend each other
    public static final class EmailChannel implements Channel {
        private final String smtpHost;

        public EmailChannel(String smtpHost) {
            this.smtpHost = Objects.requireNonNull(smtpHost);
        }

        @Override public String name() { return "email"; }

        @Override public boolean send(String recipient, String message) {
            System.out.println("    [email] to=" + recipient + " via=" + smtpHost);
            return true;
        }
    }

    public static final class SmsChannel implements Channel {
        private final String gateway;

        public SmsChannel(String gateway) {
            this.gateway = Objects.requireNonNull(gateway);
        }

        @Override public String name() { return "sms"; }

        @Override public boolean send(String recipient, String message) {
            if (recipient.length() < 10) {
                throw new IllegalArgumentException("not a valid phone number: " + recipient);
            }
            System.out.println("    [sms] to=" + recipient + " via=" + gateway);
            return true;
        }
    }
}
```

### 6.2 A template-method base class with a `final` skeleton and protected hooks

```java
package com.example.pipeline;

import java.util.List;

/**
 * A base class that fixes the ALGORITHM and lets subclasses vary the STEPS.
 *
 * <p>Notes:
 * <ul>
 *   <li>{@code importAll()} is {@code final} — the flow cannot change.</li>
 *   <li>{@code validate()} is a HOOK — a subclass may tighten the rule.</li>
 *   <li>{@code enrich()} is overridable — a subclass may add behaviour.</li>
 *   <li>No overridable method is called from the constructor.</li>
 * </ul>
 */
public abstract class AbstractImport<T> {

    private int importedCount;
    private int skippedCount;

    /** The TEMPLATE — final, so the algorithm is invariant. */
    public final ImportResult importAll(List<T> records) {
        for (T record : records) {
            if (!validate(record)) {
                skippedCount++;
                continue;
            }
            persist(record);
            enrich(record);
            importedCount++;
        }
        return new ImportResult(importedCount, skippedCount);
    }

    /** A HOOK — the default rule; a subclass may TIGHTEN it (never loosen it). */
    protected boolean validate(T record) {
        return record != null;
    }

    /** An overridable step. */
    protected void persist(T record) {
        // default: do nothing
    }

    protected void enrich(T record) {
        // default: do nothing
    }

    public record ImportResult(int imported, int skipped) {
        public int total() { return imported + skipped; }
    }
}
```

```java
package com.example.pipeline;

/** A concrete import that tightens the validation rule. */
public final class UserImport extends AbstractImport<String> {

    private final UserRepository repository;

    public UserImport(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    protected boolean validate(String record) {
        return super.validate(record) && record.contains("@");   // ✅ TIGHTENED
    }

    @Override
    protected void persist(String record) {
        repository.save(record);
    }
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Extending for code reuse alone

```java
class ArrayListStack extends ArrayList { }   // ❌ "a stack is an array list" is false
```

**✅ FIX** — compose: `private final Deque<E> items = new ArrayDeque<>();`

---

### ❌ Pitfall 2 — Calling an overridable method from a constructor

```java
Base() { init(); }   // ❌ the subclass's override runs before its fields are initialised
```

**✅ FIX** — make `init()` `final`, or use a static factory.

---

### ❌ Pitfall 3 — Assuming `super` is polymorphic

```java
super.display();   // ❌ it is STATICALLY bound — always the direct parent's version
```

**✅ FIX** — use `this.display()` for the overridden version.

---

### ❌ Pitfall 4 — Shadowing a parent field

```java
private String name = "child";   // ⚠ hides the parent's field
```

**✅ FIX** — rename it, or use `super.name` deliberately.

---

### ❌ Pitfall 5 — Forgetting `super()` must be first

```java
Child() {
    this.value = 1;      // ❌ "call to super must be the first statement"
    super();
}
```

**✅ FIX** — `super()` goes first.

---

### ❌ Pitfall 6 — A `protected` field that a subclass corrupts

```java
protected BigDecimal balance;   // ⚠ a subclass can break the invariant
```

**✅ FIX** — `private` + a `protected` hook.

---

### ❌ Pitfall 7 — Deep hierarchies

```java
A → B → C → D → E   // ❌ nobody can say what E does
```

**✅ FIX** — flatten with composition; extract interfaces for the capabilities.

---

### ❌ Pitfall 8 — An "abstract class with no state and one subclass"

```java
abstract class Base { void run() { } }   // ❌ why abstract?
```

**✅ FIX** — make it a plain class, or an interface.

---

### ❌ Pitfall 9 — Assuming an inherited method belongs to the child

```java
class Stack<E> extends ArrayList<E> { }   // ⚠ Stack now has sort(), add(int,E), remove(int)…
```

**✅ FIX** — composition gives the child *exactly* the API it chooses.

---

### ❌ Pitfall 10 — Overriding `equals`/`hashCode` in a subclass incorrectly

```java
// ❌ the subclass must include the SUPERclass's fields, or the contract breaks
```

**✅ FIX** — use `super.equals(o)` and `super.hashCode()` in the combination.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What are the types of inheritance in Java?**
**A:** **Single** (B extends A), **multi-level** (C extends B extends A), **hierarchical** (B and C both extend A), and **hybrid** (a combination — a hierarchy plus multiple *interfaces*). Java supports only **single inheritance of classes**; a class may implement multiple interfaces.

**Q: What is the diamond problem?**
**A:** It arises when a class inherits from two classes that share a common ancestor — `D extends B, C` where both `B` and `C` extend `A`. The compiler cannot decide whether `D` has one copy of `A`'s state or two, or which override of `A`'s method runs. **Java avoids it entirely by not allowing multiple class inheritance** — you use composition or interfaces with `default` methods.

**Q: What is the difference between `super` and `this`?**
**A:** `this` refers to the current object, and a call to `this.method()` uses **virtual dispatch** (the overridden version runs). `super` refers to the **direct** superclass, and `super.method()` is **statically bound** — it always calls the parent's implementation.

**Q: Can a `final` class be extended?**
**A:** No. `final` on a class closes the hierarchy — which is how the JDK makes `String` and `Integer` safe to use with `==` and as map keys. `final` on a *method* prevents overriding.

### 🟡 Intermediate

**Q: How does Java solve the diamond problem for interfaces?**
**A:** With `default` methods (Java 8+). If two **unrelated** interfaces both provide a `default` implementation, the implementing class **must** override it — the compiler refuses to guess — and it may disambiguate with `InterfaceName.super.method()`. If one interface **extends** the other, the more specific `default` simply wins (the dominance rule), and no override is needed.

**Q: Why does Java have single inheritance of classes but multiple of interfaces?**
**A:** Because interfaces (before Java 8) carried **no state**, so inheriting from two of them could not produce ambiguous *state*. The diamond's danger is duplicated *state* — and interfaces had none. Java 8's `default` methods reintroduced *behaviour* into interfaces, and the diamond reappeared as a *behavioural* ambiguity — which is now resolved by the "must override" rule rather than by banning the feature.

**Q: What is the "fragile base class" problem?**
**A:** When a change to a base class breaks its subclasses even though they do not use the changed part — because they inherit a *concrete* method whose behaviour they did not override, or depend on an inherited field. The fix is to design base classes for extension: make the state `private` and provide protected accessors, use `protected` hooks rather than overridable calls, and document the extension points.

**Q: When should you prefer composition over inheritance?**
**A:** When the relationship is **"has-a"** rather than "is-a"; when you need multiple inheritance of *type*; when the base's state or implementation is likely to change; or when the child must not inherit the parent's full API. A useful heuristic: *inheritance forces the child's API to be a superset of the parent's — if the child's API should be **smaller**, use composition.*

**Q: What is the difference between `super.field` and a polymorphic field access?**
**A:** Field access is **never polymorphic** — it is always statically bound to the declaring class. `super.field` gives the *direct* parent's field, even if the parent itself shadows its own parent's field. Only *method calls* are virtual. This is why `private` fields with protected accessors are the idiomatic pattern: the accessor can be overridden, the field cannot.

### 🔴 Advanced / Senior

**Q: Why is `super` not polymorphic, and what are the consequences?**
**A:** Because `super` is a **compile-time** reference to the direct superclass — it is a static binding baked into the bytecode (an `invokespecial`, not an `invokevirtual`). The consequences: (1) `super.method()` in a grandchild calls the *parent's* version, never the grandparent's; (2) `super.field` gives the direct parent's field, even if it shadows its own parent; and (3) it is the mechanism that makes the "template method" pattern work — the base class deliberately calls its own version to fix the algorithm, while the overridable hooks provide the variation. The trade-off is that `super` is *not* a way to "skip one level" of an override chain, which is exactly what you want for a template but not what you want for polymorphic dispatch.

**Q: Explain the "fragile base class" problem and how to design a base class for extension.**
**A:** The problem: a subclass implicitly depends on the base's *concrete* methods and fields, so a change to the base can alter the subclass's behaviour without the subclass changing — and the subclass may not even be recompiled. The design rules for an extensible base: (1) **state is `private`**, exposed through **protected accessors** (so the access can be overridden even though the field cannot); (2) **the invariant-establishing methods are `final`** (so a subclass cannot weaken them) while the **validation is a protected hook** (so a subclass can tighten it); (3) **never call an overridable method from a constructor** — use a static factory or a `final` initialiser method; (4) **document the extension points** explicitly; and (5) prefer **composition with an interface** for anything that is not genuinely a taxonomy. The deepest statement: *a base class is a contract with two audiences — the direct user and the extender — and the design must serve both without either being able to break the other.*

**Q: How would you migrate a deep inheritance hierarchy to composition?**
**A:** In stages. (1) **Map the hierarchy** — for each class, write the sentence "X **is a** Y" or "X **has a** Y"; the ones that fail are candidates. (2) **Extract the capabilities into interfaces** — the *behaviour* the subclasses share becomes an interface, and the hierarchy's API shrinks to those interfaces. (3) **Introduce composition for the "has-a" parts** — replace the inherited field with a delegate and forward the calls. (4) **Flatten the hierarchy** — the intermediate classes that only existed to share code become collaborators. (5) **Keep the inheritance that is genuinely substitutable** — usually a small kernel at the bottom. The order matters: extract the interfaces **first** (so the clients are unaffected), then move the implementation. The risk to manage is the *fragile base class* problem in reverse — during the migration, clients must not see both the old hierarchy and the new one at once, or the substitutability breaks.

**Q: `sealed` (Java 17) changes the inheritance calculus. How?**
**A:** It introduces a **third option** between "open" and "closed by abstraction". A `sealed` class or interface says "this hierarchy is closed, and here is the list" — which gives you the *exhaustiveness* guarantee that only an abstract class used to provide, while keeping the *no shared state* and *multiple implementation* properties of an interface. Concretely, a `switch` over a `sealed` hierarchy needs no `default` branch, so adding a new permitted subtype becomes a **compile error** in every switch that must handle it — a guarantee that an open hierarchy simply cannot give. It also makes "enum with behaviour" expressible without the enum's shared-field limitation, by giving each case its own `record`. The remaining limitation: a `sealed` type's permitted set is fixed at compile time, so it is unsuitable for a framework SPI that third parties are meant to extend — which is exactly the case where an open interface remains correct.

### ⚫ Expert / Principal

**Q: Java's single class inheritance was a design compromise in 1995. Is it still the right constraint?**
**A:** The *constraint* remains, but the *reasons* have changed and the *workarounds* have improved. In 1995 the choice was between (a) multiple class inheritance with C++'s virtual-inheritance semantics — a real, if subtle, mechanism — and (b) single inheritance plus interfaces. Java chose (b), partly for implementation cost (the JVM's verifier and the `invokespecial`/`invokevirtual` model) and partly for the "the programmer is responsible for multiple inheritance of *type* only" argument. Three decades later, the interesting change is that **the ambiguity the diamond created has moved**: it was originally about *state* (duplicated fields), then about *behaviour* (`default` methods), and the language has responded each time by making the ambiguity **explicit** — first by requiring an override, and now (Java 17) by letting the *type system* state the closed set so the compiler can check exhaustiveness. The deeper observation: Java has progressively converted open-hierarchy problems into closed-hierarchy declarations, and the cost has been a series of *opt-in* language features rather than a change to the core model. What has **not** changed — and is the enduring lesson — is that multiple inheritance of *state* is genuinely dangerous, and no amount of language machinery fixes it: a hierarchy that duplicates mutable state is a correctness hazard regardless of how the compiler resolves the method calls. The modern equivalent of "prefer composition" is therefore not "avoid inheritance" but "**make the state single-owner**" — which is precisely what `sealed` types, records, and immutable values all push you toward.

---

## 9. Counter-Questions & Traps

### CQ1. "Java has single inheritance. Isn't that a limitation compared to C++?"

- **Naive:** "Yes, but Java offers interfaces for multiple inheritance."  ← *true, and it dodges the real comparison*
- **The trap:** The trap is to compare the *feature* rather than the *problem the feature solves*. C++ has multiple class inheritance **with a solution** (virtual inheritance) that is famously difficult to use correctly. Java's answer is not "we couldn't do it" — it is "**we made the ambiguous case unrepresentable**".
- **Expert answer:** The precise comparison: in C++, the diamond's ambiguity is *resolvable* but at a cost — virtual inheritance changes the object layout, introduces the "most-derived class controls construction" rule, and makes the semantics harder to reason about. Java made the *state* ambiguity impossible (no multiple class inheritance) and the *behaviour* ambiguity **explicit** (the "must override" rule for unrelated `default` methods). The design lesson: **an unrepresentable ambiguity is better than a resolvable one**, because a resolvable one is a trap for anyone who does not know the rule. And the honest caveat to add: Java's restriction has a real cost — the "is-a" hierarchy is limited to one line, which is why the *composition* and *interface* patterns exist, and why `sealed` types were added to recover the closed-set case. The net assessment: for the vast majority of real designs, the cost is not paid, because the problematic hierarchies were mistakes anyway.

### CQ2. "If `super` isn't polymorphic, how does the template method pattern work?"

- **Naive:** "The base calls `super.method()` to fix the algorithm."  ← *true, and it needs the nuance*
- **The trap:** The trap is to think `super` is "the parent's version" and therefore *skips one level of overriding* — which it does, but the subtlety is *which* version it reaches.
- **Expert answer:** The precise mechanics. In a base class `B` with `void run() { step(); }` and `void step() { … }`, `B.run()` calls `B.step()` — **statically bound** to `B`'s version, so the base's algorithm is fixed. The *variation* comes from a different mechanism: `B.run()` calls an **overridable** hook (a different method, or an abstract one), which dispatches virtually to the subclass. So the template method uses **two different methods**: a `final` skeleton (which may use `super` deliberately) and an **overridable** hook (which is called virtually). `super` is not what makes the template work — the `final` modifier and the separation of "skeleton" from "hook" are. And a subclass that wants the parent's *default* step but adds its own calls `super.step()` explicitly — which is the *correct* use of `super` and a good demonstration that `super` is a deliberate, static choice rather than a dynamic one.

### CQ3. "You said prefer composition. But isn't inheritance the whole point of OOP?"

- **Naive:** "Inheritance is one of the pillars of OOP."  ← *true, and it is the framing the trap exploits*
- **The trap:** The trap is to treat "OOP" and "inheritance" as synonyms. The pillars are **encapsulation**, **abstraction**, and **polymorphism** — and **all three work perfectly with composition**. The classic OOP texts (Gamma, and the *Effective Java* item "prefer composition over inheritance") are consistent: inheritance is a *technique*, not a pillar.
- **Expert answer:** The reframe: *the pillars are about polymorphism, and polymorphism does not require inheritance.* A `List<PaymentMethod>` where `PaymentMethod` is an interface is polymorphism — and it is *more* flexible, because the implementors need not share a base class. The "OOP requires inheritance" belief is a leftover from the early-1990s, when Java had no interfaces with behaviour and composition was clumsier. The evidence from the JDK: `List`, `Set` and `Map` are all **interfaces** — the most-polymorphic, most-used abstractions in the language — and the implementations (`ArrayList`, `HashMap`) do **not** extend each other. And the practical evidence from the field: the most common inheritance-related production bugs (the fragile base class, the `Stack extends ArrayList` violation, the "call an overridable method from a constructor" NPE) are all *avoided* by composition. The rule remains: **inherit when the subtype is substitutable for the supertype; compose when it merely uses it.**

### CQ4. "What is the difference between the diamond problem and the diamond *inheritance* in interfaces?"

- **Naive:** "The same problem."  ← *true historically, and it misses the modern resolution*
- **The trap:** The trap is to treat them as the same problem with the same severity. With **interfaces**, the *state* ambiguity does not exist (there is no instance state), so the only remaining issue is a *behavioural* ambiguity — and the language resolves it by **forcing the implementor to choose**.
- **Expert answer:** The precise difference, historically and technically. (1) **Historically:** in Java 1.0, an interface had only abstract methods, so a class implementing two interfaces with the same method was fine (the class had to implement it). The diamond problem for interfaces arose in **Java 8** when `default` methods brought *behaviour* into interfaces. (2) **Technically:** the class-level diamond has an *unresolvable* state ambiguity, so Java banned it; the interface-level diamond has a *resolvable* behaviour ambiguity, so Java **required the resolution** — the implementing class must override, and may disambiguate with `A.super.m()`. (3) **The severity difference:** the class diamond is a *compile error you cannot write*; the interface diamond is a *compile error you must resolve*, and the resolution is a line of code in your class. So the interface "diamond" is not a limitation — it is a **prompt** to make an explicit choice, which is exactly what the composition answer does. And the final observation: `sealed` (Java 17) goes further and lets the *type system* make the choice for you, by declaring the closed set and letting the compiler check every `switch` for exhaustiveness.

### CQ5. "If a subclass can shadow a field, why does Java allow it at all?"

- **Naive:** "For flexibility."  ← *vague, and it misses the design tension*
- **The trap:** The trap is to treat field shadowing as a *feature* to be used. It is really a **consequence of a design decision** — that *fields are not polymorphic* — and that decision has a rationale.
- **Expert answer:** The rationale: **field access is statically bound**, because a field is *storage*, not *behaviour*. The alternative — making fields virtual — would mean every field access becomes a getter call, which changes the performance model of the language fundamentally (and the JVM's field access is a single instruction). The cost is that shadowing is possible, and it is a well-known trap: `name` in the child silently hides `name` in the parent, and `super.name` is the only way to reach it. The mitigation is a **convention**: the JDK's own style guide says *do not hide a field* — and the practical pattern that removes the hazard entirely is to make the parent's fields **`private`** and expose them through **protected accessors** (which *are* polymorphic, so the child can override the *access* without shadowing the *storage*). So the answer to "why allow it" is: *the alternative is worse*, and the mitigation is a coding convention plus a design pattern.

### CQ6. "How do you decide between a hierarchy and a flat set of classes with composition?"

- **Naive:** "It depends on the design."  ← *true, and it is not an answer*
- **The trap:** The trap is to answer with a preference ("I prefer composition") instead of a *procedure*. The interviewer is asking: *given a specific design problem, how do you work out which shape it wants?*
- **Expert answer:** The procedure. (1) **Write the substitutability sentence for every candidate** — "a `Circle` can be used wherever a `Shape` is expected". If the sentence is *false* for any call site, the hierarchy is wrong. (2) **Look at the state** — do the subtypes share a *single* piece of mutable state that must be consistent? If yes, inheritance (or a shared collaborator) is natural; if no, the shared state is probably a *composition* candidate. (3) **Count the shared code** — if the only thing the "base" provides is a few lines of shared code, that is a *composition* candidate (a collaborator), not a base class. (4) **Ask what changes together** — if the base and the subtypes change for *different* reasons, they are separate concerns. (5) **Check the substitutability at the boundary** — if the client will hold `Shape` references, the hierarchy is right; if the client always holds a `Circle` and never a `Shape`, the "base" is not a base — it is a shared-code holder, and composition is better. The output of the procedure is a *decision*, and it is the same procedure every time.

### CQ7. "Why does the compiler require `super()` to be the first statement?"

- **Naive:** "Because the parent must be constructed first."  ← *true, and it misses the *reason* it must be first*
- **The trap:** The trap is to think the rule exists for the sake of *ordering*. The real reason is that the compiler must be able to prove that the **superclass constructor sees a fully-initialised object** — and it can only do that if the `super()` call is unambiguous and unconditioned.
- **Expert answer:** The precise reason, and it is subtle. A constructor's job is to initialise the object; the superclass constructor is what establishes the inherited state, so it *must* run before the subclass's initialisers. The compiler enforces "first statement" for two reasons: (1) **there must be exactly one** superclass constructor invocation — an implicit `super()` plus an explicit `super(...)` would be two, and the "which state wins" question would be unanswerable; and (2) the `this(...)` alternative — a *constructor delegation* — must also be first, so the two mechanisms cannot be mixed and confused. The deeper consequence is exactly the "overridable method called from a constructor" hazard: because the superclass constructor runs *before* the subclass's field initialisers, any virtual call it makes sees a partially-initialised object. The design response in modern Java is therefore to prefer **`static` factory methods** over public constructors (as the JDK does for `List.of`, `Map.of`, and the `Optional` factories) — the factory can build the object completely and then hand it out, so no constructor ever observes a partial object.

### CQ8. "Is the diamond problem really the reason Java banned multiple class inheritance?"

- **Naive:** "Yes, that's the textbook reason."  ← *true, and the interviewer wants more*
- **The trap:** The trap is to treat the *stated* reason as the whole reason. The diamond is the **immediate, technical** reason, but the deeper design considerations are about **implementation cost** and **the programmer's mental model**.
- **Expert answer:** The three layers. (1) **The immediate reason** — the diamond is genuinely ambiguous, and C++'s resolution (virtual inheritance) is subtle, has surprising object-layout rules, and interacts badly with constructors. (2) **The implementation reason** — the JVM's `invokespecial`/`invokevirtual` model and the verifier assume single class inheritance; supporting C++-style virtual inheritance would have required a substantially different object model, and the JVM was designed to be *verifiable* (the verifier checks type safety without needing to understand the whole program). (3) **The design reason** — the Java language designers argued that multiple inheritance of *state* is a **correctness hazard** that is better prevented than resolved: the ambiguity is not just about which method runs, it is about *which state exists*, and a class with two copies of its ancestor's state is a bug factory. And the *programmer's model* argument: single inheritance plus interfaces gives the programmer one obvious place to put shared behaviour (the interface's `default` method) and one obvious place to put shared state (the base class) — which is a clearer division than C++'s, where both live in the base class. The interesting coda: Java 8 re-introduced behaviour into interfaces, so the ambiguity came back — and the language's answer was not to ban the feature but to **require an explicit resolution**. That is the pattern worth remembering: *when a language cannot prevent an ambiguity, it can at least force the programmer to confront it.*

### CQ9. "If `sealed` gives exhaustiveness, should every hierarchy be sealed?"

- **Naive:** "Yes, for safety."  ← *true, and it ignores the cost*
- **The trap:** The trap is to treat `sealed` as a free safety improvement. It is a **commitment** — a source-level, binary-level commitment that the permitted set is fixed.
- **Expert answer:** The cost, stated precisely: (1) **Opening the set later is a breaking change** — anyone who compiled against the old `permits` list has an exhaustive `switch` that will no longer compile when you add a case (a *good* thing for them, a *breaking* thing for you). (2) **Third parties cannot extend it** — so a `sealed` type is unsuitable for a framework SPI. (3) It is a **coupling point** — every new implementation requires editing the `permits` clause, so the owner of the sealed type must be involved. The guidance: **seal a domain type you own** (payment methods, order states, event kinds) where the set is genuinely closed and a compile error on a new case is a *feature*; **do not seal an extension point**. And the deeper principle: `sealed` is the language's way of saying "**this set is a decision, not an accident**" — and like any decision, it has a maintenance cost, so it should be made deliberately.

### CQ10. "You said `super` is a static binding. But `super.method()` in a template method *is* the pattern. Isn't that contradictory?"

- **Naive:** "No — the template method is different."  ← *true, but it needs the mechanism*
- **The trap:** The trap is to think the template method's fixed algorithm comes from `super`. It does not — it comes from the `final` modifier and from the *separation* of the skeleton from the hook.
- **Expert answer:** The precise mechanism, because it is genuinely elegant. The template method has **two kinds of method**: (1) the **skeleton** — `final void process() { read(); transform(); write(); }` — which is `final` so the algorithm is *invariant*; and (2) the **hooks** — `read()`, `transform()`, `write()` — which are *overridable* and are called **virtually**, so the subclass supplies the variation. The subclass overriding a hook does **not** change the skeleton's *structure* — it changes what each step does. And when the subclass wants the *parent's* default step as well as its own, it calls `super.step()` explicitly — that is a **deliberate, static** choice to include the parent's behaviour, which is a different thing from the virtual dispatch of the hook itself. So the resolution of the apparent contradiction: `super` is the mechanism for **composing** with the parent's implementation; virtual dispatch is the mechanism for **replacing** it. The template method uses both, and each is used for the right purpose.

---

## 10. Rapid-Fire MCQs

**Q1.** Java supports how many superclasses for a class?
- A) Unlimited  B) **One**  C) Two  D) Zero

**Q2.** The four types of inheritance are…
- A) Public, protected, private, default  B) **Single, multi-level, hierarchical, hybrid**  C) Abstract, concrete, sealed, final  D) Class, interface, enum, record

**Q3.** The diamond problem is ambiguous about…
- A) The method signature  B) **The inherited state and the overridden behaviour**  C) The access modifier  D) The serialisation

**Q4.** How does Java avoid the class-level diamond?
- A) Virtual inheritance  B) **By not allowing multiple class inheritance**  C) By making classes `final`  D) By using `default` methods

**Q5.** How does Java handle the interface-level diamond (Java 8+)?
- A) It picks the leftmost  B) **The implementing class must override and may use `A.super.m()`**  C) It is a runtime error  D) It uses virtual inheritance

**Q6.** `super.method()` performs…
- A) Virtual dispatch  B) **Static binding — the direct parent's version**  C) Reflection  D) A `default` lookup

**Q7.** `this.method()` performs…
- A) Static binding  B) **Virtual dispatch**  C) A `super` call  D) A field lookup

**Q8.** `super()` must be…
- A) The last statement  B) **The first statement of the constructor**  C) Called explicitly  D) Called only in `static` methods

**Q9.** Field access in Java is…
- A) Polymorphic  B) **Always statically bound**  C) Virtual  D) `final`

**Q10.** Shadowing a parent field is fixed by…
- A) `final`  B) **Renaming it, or using `super.field` deliberately**  C) `static`  D) `sealed`

**Q11.** Calling an overridable method from a constructor is dangerous because…
- A) It is slow  B) **The subclass's fields are not yet initialised**  C) It is illegal  D) It deadlocks

**Q12.** The fix for the constructor hazard is…
- A) Make the method `private`  B) **Make it `final`, or use a static factory**  C) Make the class `final`  D) Synchronise it

**Q13.** A `final` class…
- A) Cannot be instantiated  B) **Cannot be extended**  C) Has only `final` methods  D) Cannot be serialised

**Q14.** The `instanceof` pattern (Java 16+) replaces…
- A) `equals`  B) **The check-then-cast idiom**  C) `getClass()`  D) Reflection

**Q15.** `Stack extends ArrayList` violates…
- A) Encapsulation  B) **The Liskov Substitution Principle**  C) DIP  D) ISP

**Q16.** The strongest argument for composition over inheritance is that…
- A) It is faster  B) **The child's API is exactly what it chooses, not a superset**  C) It is more modern  D) It allows multiple inheritance

**Q17.** A `sealed` interface gives the compiler the ability to…
- A) Inline the methods  B) **Check `switch` exhaustiveness**  C) Remove `final`  D) Optimise dispatch

**Q18.** `sealed` is unsuitable for…
- A) A domain model  B) **A framework SPI that third parties extend**  C) An enum replacement  D) A sealed hierarchy

**Q19.** The "fragile base class" problem is that…
- A) Base classes are slow  B) **A change to the base can break subclasses that do not use the change**  C) Base classes cannot be `final`  D) Subclasses cannot be tested

**Q20.** The JDK's `List` is an interface and `ArrayList` is a class — this shows that…
- A) Inheritance is discouraged  B) **Capabilities are interfaces; shared state lives in abstract base classes**  C) The JDK has no inheritance  D) Interfaces are faster

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | Exactly one superclass — that is the constraint the diamond forced. |
| 2 | **B** | Single, multi-level, hierarchical, hybrid. |
| 3 | **B** | Whether the ancestor's state is shared or duplicated, and which override runs. |
| 4 | **B** | Java removed the possibility rather than resolving it. |
| 5 | **B** | The conflict must be resolved explicitly in the implementing class. |
| 6 | **B** | `super` is a compile-time reference to the direct superclass (`invokespecial`). |
| 7 | **B** | A normal call uses virtual dispatch. |
| 8 | **B** | So the compiler can prove exactly one superclass constructor runs, first. |
| 9 | **B** | Fields are storage, not behaviour — they are never virtual. |
| 10 | **B** | Rename, or use `super.field` where the parent's field is genuinely meant. |
| 11 | **B** | The superclass constructor runs before the subclass's field initialisers. |
| 12 | **B** | `final` prevents the override; a static factory avoids the constructor entirely. |
| 13 | **B** | `final` on a class closes the hierarchy. |
| 14 | **B** | It fuses the check and the cast into one safe step. |
| 15 | **B** | It inherits `add(int, E)`, which breaks the `List` contract. |
| 16 | **B** | Inheritance forces the child's API to be a superset of the parent's. |
| 17 | **B** | With a known permitted set, the compiler can prove a `switch` is complete. |
| 18 | **B** | A sealed set is fixed at compile time — third parties cannot extend it. |
| 19 | **B** | The subclass implicitly depends on the base's concrete behaviour. |
| 20 | **B** | `List` is a capability; `AbstractList` holds the shared `modCount` state. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 4 TYPES OF INHERITANCE                           │
│  ① SINGLE          ② MULTI-LEVEL       ③ HIERARCHICAL    ④ HYBRID       │
│      A                  A                     A              A            │
│      │                  │                 ┌───┴───┐          │            │
│      B                  B                 B       C        B  implements │
│                         │                                     I, J        │
│                         C                                                       │
│                                                                          │
│  CLASSES: 1 superclass   ·   INTERFACES: unlimited                        │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE DIAMOND — AND JAVA'S TWO ANSWERS                 │
│                                                                          │
│         ┌────────┐                                                     │
│         │   A    │                                                     │
│         └───┬────┘                                                     │
│     ┌───────┴───────┐                                                 │
│     ▼               ▼                                                 │
│  ┌──────┐       ┌──────┐                                              │
│  │  B   │       │  C   │                                              │
│  └───┬──┘       └──┬──┘                                               │
│      └──────┬──────┘                                                   │
│             ▼                                                          │
│        ┌──────┐                                                        │
│        │  D   │                                                        │
│        └──────┘                                                        │
│                                                                          │
│  ❌ CLASSES: illegal → USE COMPOSITION                                   │
│  ✅ INTERFACES: the class MUST override; use A.super.m() to disambiguate │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    super vs this — THE KEY CONTRAST                      │
│  this.method()   →  VIRTUAL  →  the OVERRIDDEN version                   │
│  super.method()  →  STATIC   →  the DIRECT parent's version, ALWAYS     │
│  super.field     →  STATIC   →  the DIRECT parent's field              │
│                                                                          │
│  ⚠ FIELD ACCESS IS NEVER POLYMORPHIC                                   │
│  ⚠ `super()` MUST BE THE FIRST STATEMENT OF A CONSTRUCTOR                │
│  ⚠ NEVER CALL AN OVERRIDABLE METHOD FROM A CONSTRUCTOR                  │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    INHERIT vs COMPOSITION — THE DECISION                 │
│                                                                          │
│  "Can the child be used EVERYWHERE the parent is used?"                 │
│        │                                                                 │
│      YES│        NO                                                       │
│        ▼          ▼                                                       │
│  INHERIT    COMPOSE                                                      │
│  (is-a)     (has-a)                                                      │
│                                                                          │
│  ⚠ INHERITANCE FORCES THE CHILD'S API TO BE A SUPERSET OF THE PARENT'S.  │
│    If the child's API should be SMALLER → compose.                       │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The inheritance smells in a code review

| Smell | Review comment | Fix |
|---|---|---|
| `class X extends Y` where X does not "is a" Y | "This is has-a, not is-a." | Composition |
| A hierarchy deeper than 3 levels | "Nobody can say what the leaf does." | Flatten; extract interfaces |
| A `protected` field | "A subclass can break the invariant." | `private` + a protected hook |
| An overridable method called from a constructor | "The subclass's fields are not initialised yet." | `final`, or a static factory |
| A shadowed field | "This silently hides the parent's field." | Rename, or `super.field` deliberately |
| A subclass that overrides `equals` without `super` | "The contract is broken." | Include `super.equals`/`super.hashCode` |
| A `public` constructor on a subclass with invariants | "The object can be half-built." | A static factory |

### The design rules for an extensible base class

```text
  ☐ STATE is private; expose it through protected ACCESSORS
  ☐ The invariant-establishing methods are FINAL
  ☐ The validation is a protected HOOK a subclass can TIGHTEN
  ☐ No overridable method is called from a constructor
  ☐ The extension points are documented
  ☐ Anything that is not a taxonomy is a COLLABORATOR, not a base class
```

### The migration playbook (hierarchy → composition)

```text
  1. MAP      — write "X is a Y" / "X has a Y" for every class
  2. EXTRACT  — the shared BEHAVIOUR becomes an interface (clients unaffected)
  3. COMPOSE  — the "has-a" parts become delegates
  4. FLATTEN  — the intermediate code-sharing classes become collaborators
  5. KEEP     — the genuinely substitutable kernel (usually one base class)
  ⚠ Do the extraction FIRST — never expose both hierarchies at once
```

### The JDK's evidence

| JDK pattern | Lesson |
|---|---|
| `List`/`Set`/`Map` are **interfaces** | A capability is an interface; the implementations do not extend each other |
| `AbstractList` holds the shared `modCount` | Shared **state** justifies a shared base class |
| `ArrayList` implements `List`, `Collection`, `Iterable` | Multiple *capabilities* via interfaces |
| `Thread extends Object` only | A taxonomy with no shared base — the deepest class is a leaf |
| `Integer extends Number` | A real "is-a" — a number IS a number |

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The sibling question (interface vs abstract class) | [06 · Interface vs Abstract Class](06-OOP-Interface-vs-Abstract-Class.md) |
| Encapsulation and invariants | [03 · Encapsulation vs Abstraction](03-OOP-Encapsulation-vs-Abstraction.md) |
| Polymorphism and dispatch | [05 · Polymorphism Deep Dive](05-OOP-Polymorphism-Deep-Dive.md) |
| `sealed`, records and pattern matching | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| Composition in the wild | [09 · Design Patterns](../09-Design-Patterns/01-Design-Patterns.md) |
| SOLID and the dependency direction | [10 · SOLID Principles](../10-SOLID-Principles/01-SOLID-Principles.md) |

---

**Next Chapter:** → [05 · Polymorphism Deep Dive](05-OOP-Polymorphism-Deep-Dive.md)
**Previous Chapter:** ← [03 · Encapsulation vs Abstraction](03-OOP-Encapsulation-vs-Abstraction.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
