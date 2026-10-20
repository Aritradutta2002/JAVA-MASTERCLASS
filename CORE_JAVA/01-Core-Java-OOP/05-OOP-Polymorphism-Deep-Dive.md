# Polymorphism Deep Dive — The Complete Interview Guide

> **The heart of OOP.** Polymorphism is what lets one reference type point at many
> concrete types and still do the *right* thing. It has **four** forms, two
> dispatch mechanisms, and — crucially — it can be **broken by the wrong
> `final`, the wrong `static`, or the wrong `instanceof`**.

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

- **Polymorphism** = "many forms" — one reference, many possible runtime types.
- **The four forms:** (1) **subtype polymorphism** (a reference to a supertype), (2) **overloading** (same name, different parameters — *static*), (3) **overriding** (a subclass redefines an inherited method — *dynamic*), and (4) **parametric polymorphism** (generics).
- **The distinction that is asked in every interview:** *overloading is resolved at **compile time** (static binding); overriding is resolved at **runtime** (dynamic binding).*
- **Dynamic dispatch** works on the **runtime type** of the object, not the declared type. The JVM uses the object's *actual* class to find the method.
- **The dispatch algorithm:** the JVM looks up the method in the object's class; if not found, it walks **up** the superclass chain; if still not found, it walks **down** the interface list (the default-method resolution). This is why a `default` method in an interface can be overridden by a class *above* the interface in the hierarchy.
- **What breaks polymorphism:** `static` methods (**hidden**, not overridden), `private` methods (**not inherited**), `final` methods (cannot be overridden), `fields` (always hidden, never polymorphic), and `constructors` (never inherited).
- **`super.method()` is a *static* binding** — it always calls the direct parent's version. `this.method()` is a *virtual* binding.
- **Fields are NEVER polymorphic.** `Parent.field = "p"; Child.field = "c";` — a `Parent` reference sees `"p"` even when it points at a `Child`.
- **The "overload vs override" trap:** a method that *overloads* in the child but does not *override* it (a different parameter type) is **hidden**, not overridden — so a `Parent`-typed reference calls the parent's version.
- **Java 21's pattern-matching `switch`** replaces the "instanceof then cast" chain and gives **exhaustive** dispatch over a `sealed` hierarchy.
- **`final` is the design tool** for polymorphism: `final` on a class closes the hierarchy, `final` on a method fixes the algorithm, and `final` on a field makes it a constant.

---

## 1. What & Why — The Problem This Solves

**The problem.** Without polymorphism, every caller must know the concrete type:

```java
// ❌ WITHOUT polymorphism — the caller switches on the type
if (shape instanceof Circle) {
    System.out.println("circle r=" + ((Circle) shape).radius());
} else if (shape instanceof Square) {
    System.out.println("square s=" + ((Square) shape).side());
} else if (shape instanceof Triangle) {
    …
} else {
    throw new IllegalStateException("unknown shape");   // ⚠ forget one → a runtime bug
}
```

**Three problems:** the caller is coupled to every concrete type; adding a type means editing every `if`-chain; and a missing branch is a **runtime** failure.

**The solution — polymorphism moves the behaviour to the type:**

```java
// ✅ WITH polymorphism — the caller does not know the concrete types
for (Shape shape : shapes) {
    System.out.println(shape.describe());   // ← the RIGHT method, chosen at runtime
}
```

**The benefit, stated precisely:** the caller depends on the **contract**, and the
**compiler** can now help — with a `sealed` hierarchy and an exhaustive `switch`, adding
a new `Shape` becomes a **compile error** in every `switch` that must handle it.

> 💡 **The interview framing.** The question is not "what is polymorphism?" It is
> **"what are the four forms, which are compile-time and which are run-time, and what
> breaks polymorphism?"** The last part is the one that separates a candidate who
> has read the spec from one who has only used it.

---

## 2. Mental Model — The One Picture

```text
   THE TWO DISPATCH MECHANISMS

   STATIC BINDING (compile time)          VIRTUAL BINDING (run time)
   ──────────────────────────────          ─────────────────────────────
   resolved by the DECLARED type          resolved by the RUNTIME type

   Shape s = new Circle();               Shape s = new Circle();
   s.describe();                          s.describe();
        │                                     │
        ▼                                     ▼
   the compiler looks at Shape            the JVM looks at Circle
   → Shape.describe()                     → Circle.describe()  ✅
   (even if Circle overrides it)           (the override runs)

   THE FOUR FORMS
   ────────────
   ① SUBTYPE polymorphism     ← Shape s = new Circle()      (RUNTIME)
   ② OVERRIDING               ← @Override void describe()    (RUNTIME)
   ③ OVERLOADING               ← m(int) vs m(String)          (COMPILE)
   ④ PARAMETRIC polymorphism  ← <T> void add(T item)         (COMPILE)
```

```text
   THE JVM's DISPATCH ALGORITHM (how a virtual call is resolved)
   ═══════════════════════════════════════════════════════════

   obj.describe();     where obj is declared as Shape but is a Circle

   STEP 1 — look in the object's ACTUAL class
        ┌──────────┐
        │  Circle  │  describe() found?  ──▶ YES → invoke it ✅
        └────┬─────┘
             │ not found
             ▼
   STEP 2 — walk UP the superclass chain
        ┌──────────┐   ┌──────────┐
        │  Shape   │◀──│  Object  │
        └────┬─────┘   └──────────┘
             │ found?
             ▼
   STEP 3 — walk DOWN the interface list (for a `default` method)
        ┌──────────┐
        │ Drawable │  default describe()?
        └──────────┘

   ⚠ THIS EXPLAINS A SURPRISE: a class ABOVE the interface in the
     hierarchy can override an interface's `default` method — because
     step 2 (up the class chain) happens BEFORE step 3 (the interfaces).
```

---

## 3. Deep Dive — Theory

## 3.1 The four forms of polymorphism

```java
// ① SUBTYPE POLYMORPHORISM — a reference to the supertype
Shape shape = new Circle(2.0);
Circle c = (Circle) shape;         // the runtime type is Circle

// ② OVERRIDING — the subclass redefines an inherited method
class Circle implements Shape {
    @Override public double area() { return Math.PI * radius * radius; }
}

// ③ OVERLOADING — same name, different parameter types (COMPILE TIME)
void process(int value) { }
void process(String value) { }
void process(int a, int b) { }

// ④ PARAMETRIC POLYMORPHORISM — generics
<T> void addAll(List<T> source, List<? super T> target) { }
```

| Form | Resolved at | Mechanism | Overridable? |
|---|---|---|---|
| Subtype polymorphism | **Runtime** | The object's actual class | — |
| Overriding | **Runtime** | Virtual dispatch (`invokevirtual`) | ✅ |
| Overloading | **Compile time** | The *declared* parameter types | ❌ (they are separate methods) |
| Generics | Compile time (mostly) | Erasure + the compiler | — |

## 3.2 Dynamic dispatch — the rules

```java
class Animal {
    public void speak() { System.out.println("..."); }
    public static void staticMethod() { System.out.println("Animal.static"); }
    public final void finalMethod() { System.out.println("Animal.final"); }
    private void privateMethod() { System.out.println("Animal.private"); }
    public String toString() { return "Animal"; }

    /** The ONLY legal way to reach a private method — from inside the class. */
    public void callPrivate() { privateMethod(); }
}

class Dog extends Animal {
    @Override public void speak() { System.out.println("Woof"); }
    public static void staticMethod() { System.out.println("Dog.static"); }   // ⚠ HIDING
    public void privateMethod() { System.out.println("Dog.private"); }        // ⚠ NOT OVERRIDING — a NEW method
    @Override public String toString() { return "Dog"; }
}

public class DispatchDemo {
    public static void main(String[] args) {
        Animal a = new Dog();

        a.speak();          // "Woof"    ✅ VIRTUAL — the runtime type decides
        a.staticMethod();   // "Animal.static"  ⚠ STATIC — the declared type decides
        a.finalMethod();    // "Animal.final"   — final, cannot be overridden
        a.callPrivate();    // "Animal.private" ⚠ PRIVATE — not inherited, so no dispatch.
                            //    (a.privateMethod() is a COMPILE ERROR: "privateMethod()
                            //     has private access in Animal" — a private method is not
                            //     inherited, so it is not a member of the Animal reference.)
        System.out.println(a);   // "Dog"    ✅ toString IS virtual
    }
}
```

**The table of what is and is not polymorphic:**

| Member | Inherited? | Overridable? | Polymorphic? |
|---|---|---|---|
| Public/protected instance method | ✅ | ✅ | ✅ **YES** |
| `static` method | ✅ (but **hidden**) | ❌ | ❌ **NO** |
| `private` method | ❌ | ❌ (a new method) | ❌ **NO** |
| `final` method | ✅ | ❌ | ❌ **NO** |
| Instance field | ✅ (but **hidden**) | ❌ | ❌ **NO** |
| `static` field | ✅ (hidden) | ❌ | ❌ **NO** |
| Constructor | ❌ | ❌ | ❌ **NO** |

## 3.3 Overloading vs overriding — the classic trap

```java
class Parent {
    public void handle(String s) { System.out.println("Parent.handle(String)"); }
    public void handle(int i)    { System.out.println("Parent.handle(int)"); }
}

class Child extends Parent {
    @Override
    public void handle(String s) { System.out.println("Child.handle(String)"); }   // OVERRIDES
    public void handle(long l)  { System.out.println("Child.handle(long)"); }     // OVERLOADS (new method)
}

public class OverloadTrap {
    public static void main(String[] args) {
        Child c = new Child();
        c.handle("x");            // "Child.handle(String)"  — overridden
        c.handle(1);              // "Child.handle(long)"    — the int widens to long!
        c.handle(1L);             // "Child.handle(long)"

        Parent p = c;
        p.handle("x");            // "Child.handle(String)"  ✅ virtual dispatch
        p.handle(1);              // "Parent.handle(int)"    ⚠ NOT "Child.handle(long)"!
    }
}
```

**Why `p.handle(1)` calls the parent's version:**

```text
   ⚠ THE COMPILE-TIME RESOLUTION USES THE DECLARED TYPE
   ────────────────────────────────────────────────────────────────
   p.handle(1)  where p is declared as Parent
        │
        │ ① the compiler looks at Parent's methods:
        │      handle(String)  — 1 is not a String
        │      handle(int)     — ✅ EXACT MATCH → chosen
        │      (Child.handle(long) is not even considered — it is not
        │       in Parent's method set)
        ▼
   ② the compiler emits: invokevirtual Parent.handle:(I)V
        │
        │ ③ AT RUNTIME the JVM finds handle(int) in Parent and
        │    invokes it — Parent's version, NOT Child's
        ▼
   "Parent.handle(int)"
```

**The three rules for overloading resolution (compile time):**

1. **Exact match** wins over a widening primitive conversion.
2. Otherwise, **widening** primitive conversion beats **boxing**.
3. Otherwise, **boxing/unboxing** is used.
4. Otherwise, a **widening reference conversion** to a supertype.
5. Otherwise, a **varargs** conversion.
6. Otherwise — **compile error**.

## 3.4 The JVM's dispatch algorithm — and the `default`-method surprise

```text
   obj.describe();      where obj is a Child

   ① LOOK IN THE OBJECT'S CLASS
      ┌──────────┐
      │  Child   │  describe() found?  ──▶ YES → invoke ✅
      └────┬─────┘
           │ not found
           ▼
   ② WALK UP THE SUPERCLASS CHAIN
      ┌──────────┐   ┌──────────┐
      │  Parent  │◀──│  Object  │
      └────┬─────┘   └──────────┘
           │ found?
           ▼
   ③ WALK DOWN THE INTERFACE LIST
      ┌──────────┐
      │ Drawable │  default describe()?
      └──────────┘
```

**The surprise this explains:**

```java
interface Drawable {
    default String describe() { return "drawable"; }
}

class Parent {
    public String describe() { return "parent"; }    // ⚠ a CLASS method
}

class Child extends Parent implements Drawable { }

// The interface's default is NOT used — the class hierarchy wins
new Child().describe();   // "parent"  ✅
```

> 💡 **Why:** step ② (up the class chain) happens **before** step ③ (the
> interfaces). A concrete method in a class always beats a `default` method in an
> interface — regardless of where the interface sits in the hierarchy. This is the
> *class beats interface* rule, and it is exactly the rule that makes `Object`'s
> methods (`toString`, `equals`, `hashCode`) override the `default` methods that
> `Collection` and `List` might have declared.

## 3.5 The field-hiding trap

```java
class Parent {
    protected String name = "parent";
    public String getName() { return name; }        // ⚠ reads the PARENT's field
}

class Child extends Parent {
    private final String name = "child";            // ⚠ HIDES the parent's field
    @Override public String getName() { return name; }   // reads the CHILD's field
}

public class FieldHiding {
    public static void main(String[] args) {
        Parent p = new Child();
        System.out.println(p.getName());   // "child"   ✅ the method is virtual
        System.out.println(p.name);        // "parent"  ⚠ the FIELD is not virtual
    }
}
```

**The rule:** **methods are virtual; fields are not.** A field reference is resolved
at compile time against the **declared** type. This is why `getName()` and `name` can
disagree.

**The fix — never hide a field:**

```java
class Child extends Parent {
    private final String childName;      // ✅ a different name
    @Override public String getName() { return childName; }
}
```

> 💡 **The `super.field` escape hatch** — `super.name` reaches the parent's field
> explicitly, but the *better* fix is not to hide it at all.

## 3.6 Method resolution order — the full algorithm

When the compiler sees `obj.m(args)`, it resolves in this order:

```text
   ① EXACT MATCH on the declared type's methods
   ② WIDENING PRIMITIVE CONVERSION (int → long → float → double)
   ③ BOXING / UNBOXING (int → Integer → Object)
   ④ WIDENING REFERENCE CONVERSION to a supertype
   ⑤ VARARGS
   ⑥ COMPILE ERROR
```

**And the "most specific method" rule** when several overloads apply: the one whose
parameter types are the **most specific** (i.e. the one that requires the fewest
widening conversions) wins.

```java
class Overloads {
    void m(Object o)  { print("Object"); }
    void m(String s)  { print("String"); }
    void m(int i)     { print("int"); }
    void m(Integer i) { print("Integer"); }
    void m(long l)    { print("long"); }

    static void print(String s) { System.out.println(s); }
}

new Overloads().m("x");    // "String"  — exact match
new Overloads().m(1);      // "int"     — exact match beats boxing
new Overloads().m(1L);     // "long"    — exact match
new Overloads().m(new Object());  // "Object" — exact match
```

## 3.7 Polymorphism and `final`

```java
public final class Immutable { }        // the hierarchy is CLOSED
public class Base { public final void run() { } }   // the algorithm is FIXED
public class Base { protected final int max; }      // the field is a CONSTANT
```

**`final` is the designer's tool for controlling polymorphism:**

| `final` on | Effect | Design intent |
|---|---|---|
| A class | No subclass can exist | "This hierarchy is closed" |
| A method | No override is possible | "This algorithm is fixed" |
| A field | Cannot be reassigned | "This is a constant" |
| A parameter | Cannot be reassigned in the body | "This input must not change" |
| A local variable | Cannot be reassigned | "This is a value" |

**The classic use — a template method:**

```java
public abstract class ReportGenerator {
    public final String generate() {         // ← the algorithm is FIXED
        StringBuilder sb = new StringBuilder();
        sb.append(header());
        sb.append(body());
        sb.append(footer());
        return sb.toString();
    }
    protected abstract String header();        // ← the HOOK — the variation
    protected abstract String body();
    protected abstract String footer();
}
```

## 3.8 Polymorphism with `instanceof` and pattern matching

```java
// ❌ BEFORE — a chain of checks and casts
if (shape instanceof Circle) {
    renderCircle((Circle) shape);
} else if (shape instanceof Square) {
    renderSquare((Square) shape);
}

// ✅ AFTER (Java 16+) — the pattern binds safely
if (shape instanceof Circle c) {
    renderCircle(c);       // ✅ no cast
} else if (shape instanceof Square s) {
    renderSquare(s);
}

// ✅ BEST (Java 21) — exhaustive pattern matching over a sealed hierarchy
String render(Shape shape) {
    return switch (shape) {
        case Circle c   -> "circle " + c.radius();
        case Square s   -> "square " + s.side();
        case Triangle t -> "triangle";
        // ← NO default: the compiler proves exhaustiveness
    };
}
```

**The advantage of the sealed + switch version:** adding a new `Shape` makes the
`render` method a **compile error** — the "forgot a case" bug becomes impossible.

## 3.9 Ad-hoc polymorphism (duck typing)

```java
// Java's "duck typing" — an interface is not required
public static String describe(Object o) {
    if (o instanceof Describable d) {
        return d.describe();
    }
    return o.toString();
}

// A "protocol" without an interface
class Duck { public String quack() { return "quack"; } }
class Robot { public String quack() { return "beep"; } }

static void makeItQuack(Object o) {
    // reflection or an interface — Java has no structural typing
}
```

> 💡 **Java is nominally typed**, not structurally typed: a `Duck` and a `Robot` are
> unrelated types even if they both have `quack()`. Kotlin, Scala and TypeScript have
> **structural typing** (or a `duck type` annotation) — a real difference in
> expressiveness.

---

## 4. Visualizations

### 4.1 Static vs virtual binding

```text
   STATIC BINDING — the DECLARED type decides (compile time)
   ═════════════════════════════════════════════════════
   Shape s = new Circle();

   s.area();        → the compiler emits invokevirtual Shape.area
                       → at RUNTIME the JVM finds Circle.area   ✅ VIRTUAL

   Shape.staticHelper();
                   → the compiler emits invokestatic Shape.staticHelper
                   → the Circle version is NEVER considered     ⚠ STATIC

   s.radius;        → the compiler emits getfield Shape.radius
                   → Circle's hiding field is NEVER read        ⚠ STATIC


   VIRTUAL BINDING — the RUNTIME type decides
   ════════════════════════════════════════════
   Shape s = new Circle();
   s.area();        → Circle.area() runs  ✅
```

### 4.2 What is and is not polymorphic

```text
   ┌──────────────────────┬───────────┬─────────────┬──────────────┐
   │ MEMBER               │ INHERITED │ OVERRIDABLE │ POLYMORPHIC? │
   ├──────────────────────┼───────────┼─────────────┼──────────────┤
   │ public instance m()  │    ✅     │     ✅      │   ✅ YES      │
   │ static m()           │  (hidden) │     ❌      │   ❌ NO       │
   │ private m()          │    ❌     │     ❌      │   ❌ NO       │
   │ final m()            │    ✅     │     ❌      │   ❌ NO       │
   │ instance field       │  (hidden) │     ❌      │   ❌ NO       │
   │ static field         │  (hidden) │     ❌      │   ❌ NO       │
   │ constructor          │    ❌     │     ❌      │   ❌ NO       │
   └──────────────────────┴───────────┴─────────────┴──────────────┘

   ⚠ METHODS ARE VIRTUAL.  FIELDS AND STATICS ARE NOT.
```

### 4.3 The JVM's dispatch algorithm

```text
   obj.describe();      (obj is declared as Shape, is a Circle)

   ┌──────────┐
   │  Circle  │  ① the object's ACTUAL class
   └────┬─────┘  describe() here?  ──▶ YES → invoke ✅
        │ not found
        ▼
   ┌──────────┐   ┌──────────┐
   │  Shape   │◀──│  Object  │   ② walk UP the class chain
   └────┬─────┘   └──────────┘
        │ found?
        ▼
   ┌──────────┐
   │ Drawable │   ③ walk DOWN the interface list (for a `default` method)
   └──────────┘

   ⚠ THE SURPRISE THIS EXPLAINS:
     a class ABOVE the interface in the hierarchy wins over the
     interface's `default` method — because step ② precedes step ③.
```

### 4.4 Overload vs override — the resolution trap

```text
   class Parent { void handle(String s); void handle(int i); }
   class Child extends Parent {
       @Override void handle(String s);   // OVERRIDES
       void handle(long l);                // OVERLOADS (a NEW method)
   }

   Child c = new Child();
   Parent p = c;

   c.handle("x")   → "Child.handle(String)"   ✅ overridden
   c.handle(1)     → "Child.handle(long)"     ⚠ int widens to long
   p.handle("x")   → "Child.handle(String)"   ✅ virtual dispatch
   p.handle(1)     → "Parent.handle(int)"     ⚠⚠ THE TRAP

   WHY p.handle(1) is NOT "Child.handle(long)":
   ┌──────────────────────────────────────────────────────────────┐
   │  ① the compiler resolves against the DECLARED type (Parent): │
   │      handle(String)? no (1 is not a String)                  │
   │      handle(int)?    ✅ EXACT MATCH → chosen                 │
   │      Child.handle(long) is NOT in Parent's method set at all  │
   │  ② the bytecode is: invokevirtual Parent.handle:(I)V         │
   │  ③ the JVM finds handle(int) in Parent → Parent's version    │
   └──────────────────────────────────────────────────────────────┘
```

### 4.5 Field hiding — methods virtual, fields not

```text
   class Parent { protected String name = "parent";
                  String getName() { return name; } }
   class Child  extends Parent { private final String name = "child";
                                @Override String getName() { return name; } }

   Parent p = new Child();

   p.getName()   → "child"    ✅ the METHOD is virtual
   p.name        → "parent"   ⚠ the FIELD is resolved at compile time
                               against the DECLARED type (Parent)

   ⚠ THE TWO CAN DISAGREE — and that is the smell.
   ✅ FIX: never hide a field; give the child a different name.
```

### 4.6 The four forms of polymorphism

```text
   ① SUBTYPE        Shape s = new Circle();     RUNTIME
   ② OVERRIDING     @Override void area()      RUNTIME
   ③ OVERLOADING     m(int) / m(String)         COMPILE
   ④ PARAMETRIC     <T> void add(T item)        COMPILE

   ┌────────────────────────────────────────────────────────────────┐
   │  THE RULE OF THUMB:                                             │
   │    OVERLOADING  → resolved by the COMPILED arguments            │
   │    OVERRIDING   → resolved by the OBJECT's RUNTIME type         │
   └────────────────────────────────────────────────────────────────┘
```

### 4.7 `final` — the designer's control over polymorphism

```text
   final class X        → the hierarchy is CLOSED      (no subclass)
   final void m()       → the ALGORITHM is FIXED       (no override)
   final int MAX        → the VALUE is a CONSTANT      (no reassignment)
   final void m() { … } → a TEMPLATE METHOD's skeleton

   ┌────────────────────────────────────────────────────────────────┐
   │  class Report {                                               │
   │      public final String generate() {  ← FIXED algorithm       │
   │          return header() + body() + footer();                  │
   │      }                                                        │
   │      protected abstract String header();  ← the HOOK            │
   │      protected abstract String body();                          │
   │      protected abstract String footer();                        │
   │  }                                                            │
   └────────────────────────────────────────────────────────────────┘
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — What is and is not polymorphic

```java
public class PolymorphismDemo {

    static class Animal {
        public void speak() { System.out.println("Animal.speak"); }
        public static void staticMethod() { System.out.println("Animal.static"); }
        public final void finalMethod() { System.out.println("Animal.final"); }
        private void privateMethod() { System.out.println("Animal.private"); }
        @Override public String toString() { return "Animal"; }
    }

    static class Dog extends Animal {
        @Override public void speak() { System.out.println("Dog.speak"); }
        public static void staticMethod() { System.out.println("Dog.static"); }      // ⚠ HIDING
        public void privateMethod() { System.out.println("Dog.private"); }         // ⚠ NOT overriding
        @Override public String toString() { return "Dog"; }
    }

    public static void main(String[] args) {
        Animal a = new Dog();

        a.speak();            // ✅ VIRTUAL     → "Dog.speak"
        a.staticMethod();     // ⚠ STATIC      → "Animal.static"
        a.finalMethod();      // ⚠ final       → "Animal.final"
        a.privateMethod();    // ⚠ private     → "Animal.private"
        System.out.println(a); // ✅ VIRTUAL     → "Dog"
    }
}
```

**Actual output:**

```text
Dog.speak
Animal.static
Animal.final
Animal.private
Dog
```

**Takeaway:** only the **public instance method** and `toString` are polymorphic.
`static` is bound to the *declared* type, `final` cannot be overridden, and `private`
is not inherited at all — so `a.privateMethod()` calls `Animal`'s, not `Dog`'s.

### Example 2 — The overload/override trap

```java
public class OverloadTrapDemo {

    static class Parent {
        public void handle(String s) { System.out.println("Parent.handle(String)"); }
        public void handle(int i)    { System.out.println("Parent.handle(int)"); }
    }

    static class Child extends Parent {
        @Override public void handle(String s) { System.out.println("Child.handle(String)"); }
        public void handle(long l)          { System.out.println("Child.handle(long)"); }
    }

    public static void main(String[] args) {
        Child c = new Child();
        Parent p = c;

        c.handle("x");   // "Child.handle(String)"  — overridden
        c.handle(1);     // "Child.handle(long)"    — int widens to long
        p.handle("x");   // "Child.handle(String)"  ✅ virtual dispatch
        p.handle(1);     // "Parent.handle(int)"    ⚠ THE TRAP
    }
}
```

**Actual output:**

```text
Child.handle(String)
Child.handle(long)
Child.handle(String)
Parent.handle(int)
```

**Takeaway:** `p.handle(1)` resolves against the **declared** type `Parent`, which has
`handle(int)` — an exact match. `Child.handle(long)` is not even in `Parent`'s method
set, so it is never considered. The overload is **hidden**, not overridden.

### Example 3 — The field-hiding trap

```java
public class FieldHidingDemo {

    static class Parent {
        protected String name = "parent";
        public String getName() { return name; }        // reads the PARENT's field
    }

    static class Child extends Parent {
        private final String name = "child";            // ⚠ HIDES the parent's field
        @Override public String getName() { return name; }   // reads the CHILD's field
    }

    public static void main(String[] args) {
        Parent p = new Child();
        System.out.println("p.getName() = " + p.getName() + "   ✅ the METHOD is virtual");
        System.out.println("p.name      = " + p.name + "   ⚠ the FIELD is resolved at compile time");
    }
}
```

**Actual output:**

```text
p.getName() = child   ✅ the METHOD is virtual
p.name      = parent   ⚠ the FIELD is resolved at compile time
```

**Takeaway:** the two disagree — which is the smell. The fix is to **never hide a
field**: give the child a different name (`childName`).

### Example 4 — The `default`-method resolution surprise

```java
public class DefaultResolutionDemo {

    interface Drawable {
        default String describe() { return "drawable"; }
    }

    static class Parent {
        public String describe() { return "parent"; }     // ⚠ a CLASS method
    }

    static class Child extends Parent implements Drawable {
        // no describe() here
    }

    // The reverse: the class is BELOW the interface
    static class Standalone implements Drawable {
        @Override public String describe() { return "standalone"; }
    }

    public static void main(String[] args) {
        System.out.println("Child (class above the interface) → " + new Child().describe()
                + "   ✅ the CLASS method wins");
        System.out.println("Standalone (class implements it)   → " + new Standalone().describe()
                + "   ✅ the override wins");
    }
}
```

**Actual output:**

```text
Child (class above the interface) → parent   ✅ the CLASS method wins
Standalone (class implements it)   → standalone   ✅ the override wins
```

**Takeaway:** the JVM searches the **class chain first** (step 2) and the
**interfaces second** (step 3) — so a concrete method in a class always beats a
`default` method in an interface, no matter where the interface sits.

### Example 5 — Polymorphism with `sealed` and exhaustive pattern matching

```java
public class SealedPolymorphismDemo {

    sealed interface Shape permits Circle, Square, Rectangle {
        double area();
    }
    record Circle(double radius) implements Shape {
        public double area() { return Math.PI * radius * radius; }
    }
    record Square(double side) implements Shape {
        public double area() { return side * side; }
    }
    record Rectangle(double w, double h) implements Shape {
        public double area() { return w * h; }
    }

    // ✅ EXHAUSTIVE — the compiler proves every case is handled
    static String describe(Shape shape) {
        return switch (shape) {
            case Circle c    -> "circle r=" + c.radius();
            case Square s    -> "square " + s.side();
            case Rectangle r -> "rectangle " + r.w() + "x" + r.h();
        };
    }

    public static void main(String[] args) {
        System.out.println(describe(new Circle(1.0)));
        System.out.println(describe(new Square(2.0)));
        System.out.println(describe(new Rectangle(3.0, 4.0)));
        System.out.println("✅ add a Rectangle to `permits` and describe() becomes a COMPILE ERROR");
    }
}
```

**Actual output:**

```text
circle r=1.0
square 2.0
rectangle 3.0x4.0
✅ add a Rectangle to `permits` and describe() becomes a COMPILE ERROR
```

**Takeaway:** with an open hierarchy, adding a `Rectangle` would silently fall into a
`default` branch. With a `sealed` hierarchy, it is a **compile error** — the
"forgot a case" bug is designed out.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A polymorphic pipeline with a template method and a strategy

```java
package com.example.processing;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A polymorphic processor showing all three forms working together.
 *
 * <ul>
 *   <li><b>Subtype polymorphism</b> — {@link Stage} references, many implementations.</li>
 *   <li><b>Overriding</b> — each stage implements {@code process}.</li>
 *   <li><b>Overloading</b> — {@code apply} has three shapes.</li>
 *   <li><b>Generics</b> — the pipeline is type-safe from input to output.</li>
 * </ul>
 */
public abstract class Stage<I, O> {

    private final String name;

    protected Stage(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public String name() { return name; }

    /**
     * The TEMPLATE — final, so the pipeline's flow is invariant.
     * The HOOK ({@code convert}) is where the subclass varies.
     */
    public final List<O> run(List<I> input) {
        return input.stream()
                .map(this::convert)      // ← the virtual call — the subclass's version
                .filter(this::accept)    // ← another overridable hook
                .toList();
    }

    /** The HOOK — every stage must say how it transforms. */
    protected abstract O convert(I value);

    /** The HOOK — an optional filter with a sensible default. */
    protected boolean accept(O value) {
        return value != null;
    }

    // ── OVERLOADING — three shapes of the same operation ──────────────
    public O apply(I value) {
        return convert(value);
    }

    public List<O> applyAll(List<I> values) {
        return run(values);
    }

    public O applyOrDefault(I value, O fallback) {
        O result = convert(value);
        return result == null ? fallback : result;
    }
}
```

```java
package com.example.processing;

import java.util.List;

/** A concrete stage — it says WHAT it does, not HOW the pipeline runs. */
public final class UppercaseStage extends Stage<String, String> {

    private final int minLength;

    public UppercaseStage(int minLength) {
        super("uppercase");
        this.minLength = minLength;
    }

    @Override
    protected String convert(String value) {
        return value == null ? null : value.toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    protected boolean accept(String value) {
        return value != null && value.length() >= minLength;
    }
}
```

### 6.2 A polymorphic renderer with a `sealed` hierarchy

```java
package com.example.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A polymorphic report renderer.
 *
 * <p>The {@code sealed} hierarchy lets the compiler check that every report type
 * is rendered — so adding a new type is a compile error, not a silent omission.
 */
public final class ReportRenderer {

    public sealed interface Report permits SalesReport, InventoryReport, AuditReport {

        String title();

        /** The shared default — polymorphism through a `default` method. */
        default String render() {
            return switch (this) {
                case SalesReport s    -> renderSales(s);
                case InventoryReport i -> renderInventory(i);
                case AuditReport a    -> renderAudit(a);
                // ← EXHAUSTIVE — no default needed
            };
        }

        private String renderSales(SalesReport r) {
            return title() + " total=" + r.total() + " orders=" + r.orderCount();
        }

        private String renderInventory(InventoryReport r) {
            return title() + " items=" + r.itemCount() + " low=" + r.lowStockCount();
        }

        private String renderAudit(AuditReport r) {
            return title() + " entries=" + r.entries().size() + " since=" + r.since();
        }
    }

    public record SalesReport(String title, BigDecimal total, int orderCount) implements Report {}

    public record InventoryReport(String title, int itemCount, int lowStockCount) implements Report {}

    public record AuditReport(String title, List<String> entries, LocalDate since) implements Report {}

    /** Render many reports — the caller does not know the concrete types. */
    public List<String> renderAll(List<Report> reports) {
        return reports.stream().map(Report::render).toList();
    }
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Expecting `static` methods to be polymorphic

```java
Parent p = new Child();
p.staticMethod();   // ❌ the PARENT's version — static methods are hidden, not overridden
```

**✅ FIX** — call the child's version explicitly: `Child.staticMethod()`.

---

### ❌ Pitfall 2 — Expecting fields to be polymorphic

```java
Parent p = new Child();
p.name;   // ❌ the PARENT's field — field access is statically bound
```

**✅ FIX** — never hide a field; use an accessor (which *is* virtual).

---

### ❌ Pitfall 3 — Overloading instead of overriding

```java
// ❌ the parent's handle(1) will not find the child's handle(long)
public void handle(long l) { … }
```

**✅ FIX** — the override must have the **same** parameter types; add `@Override` and
let the compiler tell you.

---

### ❌ Pitfall 4 — Forgetting `@Override`

```java
// ❌ a typo means you have OVERLOADED, not overridden — and the compiler says nothing
public void areea() { … }
```

**✅ FIX** — always annotate with `@Override`; it turns a silent bug into a compile error.

---

### ❌ Pitfall 5 — An `instanceof` chain without a `default` branch

```java
if (s instanceof Circle c) { … }
else if (s instanceof Square q) { … }
// ⚠ a new shape silently falls through
```

**✅ FIX** — a `sealed` hierarchy + an exhaustive `switch` (Java 21).

---

### ❌ Pitfall 6 — Calling an overridable method from a constructor

```java
Base() { init(); }   // ❌ the subclass's override runs before its fields exist
```

**✅ FIX** — `final void init()`, or a static factory.

---

### ❌ Pitfall 7 — Assuming `super.method()` is polymorphic

```java
super.method();   // ❌ always the DIRECT parent's version
```

**✅ FIX** — use `this.method()` for the virtual call.

---

### ❌ Pitfall 8 — A `private` method that looks like an override

```java
class Parent {
    protected void parentMethod() { System.out.println("Parent.parentMethod"); }
}
class Child extends Parent {
    private void privateMethod() { System.out.println("Child.privateMethod"); }
    // ❌ NOT an override — `privateMethod` does not exist in the parent,
    //    so nothing is overridden and nothing is dispatched dynamically.
}
```

**✅ FIX** — use `protected` if the subclass is meant to participate.

---

### ❌ Pitfall 9 — Overload ambiguity with `null`

```java
void m(String s) { }
void m(Integer i) { }
m(null);   // ❌ COMPILE ERROR — ambiguous
```

**✅ FIX** — cast: `m((String) null)`.

---

### ❌ Pitfall 10 — A `default` method in an interface that conflicts with a superclass

```java
class Parent { public String describe() { return "parent"; } }
class Child extends Parent implements Drawable { }
// ⚠ the interface's default describe() is IGNORED — the class chain wins
```

**✅ FIX** — override explicitly in the class if the interface's behaviour is wanted.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is polymorphism?**
**A:** The ability of one reference type to point at objects of many concrete types, and for the program to do the *right thing* at runtime. In Java it appears as **subtype polymorphism** (`Shape s = new Circle()`), **overriding** (a subclass redefines an inherited method), **overloading** (same name, different parameters), and **parametric polymorphism** (generics).

**Q: What is the difference between overloading and overriding?**
**A:** **Overloading** is the same method name with *different parameter types* in the same class (or a subclass) — resolved at **compile time** by the argument types. **Overriding** is a subclass redefining an inherited method with the **same signature** — resolved at **runtime** by the object's actual class. Overloading is a *compile-time* mechanism; overriding is the *runtime* one.

**Q: What is dynamic dispatch?**
**A:** The JVM choosing the method implementation based on the object's **runtime type** rather than its declared type. `Shape s = new Circle(); s.area();` calls `Circle.area()`. The JVM looks in the object's actual class first, then walks up the superclass chain, then down the interface list.

**Q: Is a `static` method inherited and overridden?**
**A:** It is *inherited* in the sense that it is accessible from the subclass, but it is **hidden**, not overridden — the child's `static` method does not replace the parent's for a parent-typed reference. `static` methods are bound at compile time to the declared type.

### 🟡 Intermediate

**Q: Are fields polymorphic?**
**A:** **No.** Field access is resolved at compile time against the **declared** type. A `Parent`-typed reference always reads the `Parent`'s field, even when it points at a `Child` that hides it. That is why the accessor method `getName()` and the direct field access `name` can give **different answers** for the same object — and why hiding a field is a smell.

**Q: What are the rules for choosing between overloads?**
**A:** In order: (1) an **exact match** on the parameter type; (2) otherwise a **widening primitive conversion**; (3) otherwise **boxing/unboxing**; (4) otherwise a **widening reference conversion**; (5) otherwise a **varargs** conversion; (6) otherwise a compile error. This is why `m(1)` picks `m(int)` over `m(long)` and over `m(Integer)`.

**Q: Why is `super.method()` not polymorphic?**
**A:** Because `super` is a **compile-time** reference to the direct superclass, and the call is emitted as `invokespecial` (a non-virtual call) rather than `invokevirtual`. It is exactly the mechanism that makes the **template method** pattern work — the base class deliberately calls its own version to fix the algorithm, while the overridable hooks provide the variation.

**Q: What breaks polymorphism?**
**A:** `static` methods (hidden, compile-time bound), `private` methods (not inherited, so no dispatch), `final` methods (cannot be overridden), fields (never virtual), and constructors (never inherited). The design tool for controlling this is `final` — on a class it closes the hierarchy, on a method it fixes the algorithm.

### 🔴 Advanced / Senior

**Q: Explain the JVM's method-resolution algorithm for a virtual call, and a surprising consequence.**
**A:** Three steps: (1) look in the object's **actual class**; (2) if not found, walk **up** the superclass chain; (3) if still not found, walk **down** the interface list (this is where a `default` method is found). The surprising consequence: **a concrete method in a class always beats a `default` method in an interface**, regardless of where the interface sits in the hierarchy — because step 2 precedes step 3. So `class Child extends Parent implements Drawable` uses `Parent`'s `describe()`, not `Drawable`'s `default`. The same rule explains why `Object`'s `toString`/`equals`/`hashCode` win over any `default` versions an interface might declare.

**Q: What is the difference between "hiding" and "overriding", and what are the consequences?**
**A:** **Overriding** replaces a method in a subclass — the parent's implementation is *unreachable* through a parent-typed reference (the call is virtual). **Hiding** is what happens to `static` methods and to **fields** — the child's declaration is a *different* member, and the parent's is still reachable through a parent-typed reference. The consequence is the asymmetry an interviewer is testing: for a `static` method, `Parent.staticMethod()` and `Child.staticMethod()` are two unrelated methods, and the *declared type* picks one at compile time; for a field, `p.field` reads the parent's field while `p.getField()` may read the child's. The practical rule: **never hide a `static` method or a field in a subclass** — rename instead. Hiding is one of the few places where Java gives you *more* ambiguity rather than less.

**Q: How would you design an extensible pipeline that supports both "template method" and "strategy" flexibility?**
**A:** Use both, for different axes. The **template method** for the *fixed structure* — the base class's `final` method defines the order of the steps and the error handling, and the steps are `protected` hooks. The **strategy** for the *variable content* — when a step's behaviour is chosen at runtime rather than by subclassing, inject a `Strategy` object into the step instead of making the step abstract. The rule for choosing: **if the variation is a *kind* of thing (a different stage type), use inheritance; if it is a *way of doing* a thing (a different algorithm at runtime), use a strategy.** A production example: a report pipeline where the *stages* are types (inheritance — `ValidationStage`, `TransformStage`) but the *transform algorithm* per stage is configurable (strategy — a `Transformer` injected into `TransformStage`). Over-using either is a smell: an explosion of stage subclasses for a small behavioural difference is a strategy in disguise, and a strategy that is really a type hierarchy is a strategy in the wrong clothes.

**Q: What is "ad-hoc polymorphism" (duck typing) and why doesn't Java have it?**
**A:** Ad-hoc polymorphism is the ability to call a method on any object that happens to have that method, regardless of its declared type — structural typing. Java is **nominally typed**: a `Duck` and a `Robot` are unrelated even if both have `quack()`, and you cannot write `void makeItQuack(Object o)` without a cast or an interface. Java's alternative is the `instanceof` pattern: `if (o instanceof Quackable q) q.quack();` — a *checked, narrowing* form of duck typing. The reason Java is nominal: **nominal typing is what makes the JVM's verifier and the `invokespecial`/`invokevirtual` model tractable** — a virtual call needs a known target type to be verified, and with structural typing the set of possible targets is unbounded. The trade: Java loses the convenience of duck typing but gains a **verifiable** type system and a compiler that can devirtualise. The modern Java compromise (`instanceof` patterns, Java 16+) is a good one: it gives the *narrowing* convenience of duck typing while keeping the static guarantees.

### ⚫ Expert / Principal

**Q: Java's dispatch is single-dispatch: the receiver's runtime type chooses the method. What are the consequences, and how did the language evolve?**
**A:** Java dispatches on **one** dimension — the receiver. C++ and Rust dispatch on **two** (the receiver *and* the argument types) which is what makes *operator overloading* and *multiple dispatch* possible. The consequences in Java: (1) **no operator overloading** (except `String`'s `+`); (2) the **Visitor pattern** is the standard workaround — and it works by *reversing* the dispatch: the element calls `visitor.accept(this)`, and the visitor then dispatches on its own runtime type, giving the effect of double dispatch; (3) the **`instanceof` chain** is the other workaround, and it is exactly what the Visitor pattern exists to avoid, because adding a new operation to an `instanceof` chain means editing every branch. The evolution is the interesting part: Java has been steadily **absorbing the Visitor pattern into the language** — first with `default` methods (a shared operation across a hierarchy), then with **sealed types** (the closed set that the Visitor needed), and finally with **pattern-matching `switch` (Java 21)** and **record patterns** (the deconstruction that the Visitor's `accept` used to provide). The effect: the *motivation* for the Visitor pattern has largely evaporated — a `switch` over a `sealed` interface with record patterns is now the idiomatic way to add an operation over a closed hierarchy, with **no `accept` boilerplate in the element types** and **no double dispatch**. The deep lesson: *the Visitor pattern was a workaround for a language limitation; the language has now grown the feature that makes it unnecessary, and a good engineer should notice and migrate* — the cost of the pattern (the `accept` method polluting every element, the `Element` interface becoming a circular dependency) is no longer justified.

---

## 9. Counter-Questions & Traps

### CQ1. "If fields aren't polymorphic, how does `System.out.println(obj)` print the subclass's `toString`?"

- **Naive:** "`toString` is a method, so it is polymorphic."  ← *true, and it does not generalise*
- **The trap:** The trap is to leave it at "methods are polymorphic". The question is really asking: *if the compiler can resolve `obj.toString()` against the declared type, why does it not do the same for a field?* The answer is about **what the bytecode can express**, not about a convention.
- **Expert answer:** The mechanism: a method call is emitted as `invokevirtual` with a **method reference** (the class + the name + the descriptor) — the JVM resolves it at runtime against the object's actual class. A field access, by contrast, is emitted as `getfield` with a **field reference** resolved at *link* time against the class named in the reference — the declared type. The compiler *could* emit a virtual field access, but that would mean an extra indirection on every field read, for a feature almost nobody wants (and which would break the "fields are storage" model that makes the JVM's layout simple). The consequence, and the trap: `obj.toString()` and `obj.name` can disagree — and that is not a compiler bug but the *designed* difference between the two mechanisms. The practical rule: **hide behaviour in methods, and never rely on field access being virtual.**

### CQ2. "You said overloading is compile-time. But what if the argument's type is only known at runtime?"

- **Naive:** "Then it's overloading at runtime."  ← *the trap: overloading is NEVER at runtime*
- **The trap:** The trap is to imagine a "dynamic overload" — choosing the method by the runtime type of the argument. Java has **no such thing**: the overload is chosen by the **static type** of the argument, which is known at compile time. If the static type is `Object`, only the `m(Object)` overload is applicable.
- **Expert answer:** The precise statement: **overload resolution is a purely static, compile-time process** — the compiler picks the most specific applicable method based on the *declared* types of the arguments, and that choice is fixed in the bytecode. There is no runtime re-resolution. The classic demonstration: `Object o = "hello"; m(o);` calls `m(Object)`, even though `o` is a `String` at runtime. The way to get runtime-type-based selection is: (1) **overriding** — put the alternative in a subclass (virtual dispatch on the *receiver*); (2) the **Visitor pattern** — reverse the dispatch so the *second* participant chooses; (3) **pattern matching** (Java 21) — `switch (o) { case String s -> …; }` dispatches on the runtime type. The deep insight: Java's single dispatch is on the *receiver*, so anything that seems to need a second dimension of runtime dispatch (overloading on the argument, or a visitor over the argument's type) must be restructured — and that is exactly why the Visitor pattern exists.

### CQ3. "If a `default` method loses to a class method, why does `Object.toString()` win over `Collection`'s? Doesn't `Collection` extend `Iterable` and `Object`?"

- **Naive:** "Because `Object` is the root of the class hierarchy."  ← *true, and it misses the *ordering* that makes it work*
- **The trap:** The trap is to attribute it to "the root wins" — which suggests a special rule. There is no special rule; it falls out of the **three-step search order** (actual class → up the class chain → down the interface list).
- **Expert answer:** The precise mechanism: `obj.toString()` where `obj` is, say, an `ArrayList`. Step 1: does `ArrayList` declare `toString()`? `ArrayList` does not, but `AbstractCollection` does — and step 2 walks up the class chain and finds it there. `Object.toString()` is only reached if nothing in the class chain declares it. The interface list (which includes `Collection`, and any `default toString()` it might declare) is consulted **last** — so a `default` method can never beat a class method, no matter how "specific" the interface is. And the corollary, which is the practical takeaway: **a `default` method in an interface is a *fallback*, not an override.** If your class hierarchy provides the method, the interface's default is silently ignored — which is a classic source of "why isn't my default method being called?" confusion. The design rule: do not put a `default` method in an interface whose behaviour you expect to be honoured — put the behaviour in the class, or make the interface method abstract.

### CQ4. "You said `final` fixes the algorithm. But isn't that the opposite of what OO is about — extensibility?"

- **Naive:** "Yes, `final` limits extensibility."  ← *true, and it misses the point of the pattern*
- **The trap:** The trap is to read `final` as "closed for extension" and therefore as anti-OO. But the **template method** pattern — one of the most-used OO patterns — *depends* on `final`: the base class fixes the algorithm and the subclass extends the *steps*. Extensibility at the wrong granularity is not extensibility, it is fragility.
- **Expert answer:** The distinction that resolves it: **OO is about extending the *behaviour* at the right seam, not about making every method overridable.** The template method is the canonical example: the *algorithm* is the stable, shared knowledge (so it is `final`), and the *variable steps* are the extension points (so they are `protected`/`abstract`). A design where the algorithm is overridable is not more extensible — it is a design where every subclass can silently break the invariants of the workflow. And the parallel with the "fragile base class" problem: making everything overridable means every subclass implicitly depends on every concrete method of the base — the exact coupling that `final` removes. The deep principle: **the question is not "is this method overridable" but "who is allowed to change this, and who must not?"** `final` is how you answer the second question in the type system. The same logic applies to `final` on a class: `String` is `final` because "the behaviour of a String must not be subclass-dependent" is a *correctness* requirement, not a limitation.

### CQ5. "You said `instanceof` chains are the alternative to the Visitor pattern. But `instanceof` chains are exactly what the Visitor pattern was invented to avoid — isn't that a regression?"

- **Naive:** "Yes, but Java 21's pattern matching is better."  ← *true, and the history is the interesting part*
- **The trap:** The trap is to present this as a *regression* — going back to the `instanceof` chain the Visitor pattern replaced. The reality is subtler: the **language feature** (pattern matching) is new, but the **shape of the problem** has changed, because `sealed` types give the compiler the information it needs.
- **Expert answer:** The precise comparison. The *old* `instanceof` chain: every `switch`/branch must be updated when a type is added, and nothing checks that — the omission is silent. The *Visitor*: the operation lives in a separate class, so adding an operation needs no change to the elements — but it costs an `accept` method in every element and a circular dependency between the elements and the visitor. The *modern* form — a `sealed` interface + a `switch` with record patterns: adding a type makes every `switch` a **compile error**, and the operation still lives outside the element types (in the `switch`), so there is **no `accept` boilerplate and no circular dependency**. So it is not a regression — it is the *Visitor's benefit without the Visitor's cost*, and it is only possible because `sealed` provides the exhaustiveness information that the old `instanceof` chain lacked. The deeper lesson: **the Visitor pattern existed to work around a language limitation; the language has now grown the feature, and the honest engineering response is to notice that the workaround's cost is no longer justified** — the `accept` method pollutes every element and creates a cycle in the dependency graph, and neither is needed any more.

### CQ6. "You say the JVM searches the class chain before the interfaces. Does that mean an interface's `default` method is unreliable?"

- **Naive:** "Yes — always use the class version."  ← *practically right, and it misses the design guidance*
- **The trap:** The trap is to conclude that `default` methods are "unreliable" and therefore to avoid them. They are not unreliable — they are **well-defined with a lower priority**, and the priority is exactly what you want.
- **Expert answer:** The design guidance that follows from the rule: (1) A `default` method is a **fallback for implementors that have nothing better to offer** — it is the *default* behaviour, not the *primary* one. (2) If a class in the hierarchy provides the method, the class's version wins — so a `default` method must be written on the assumption that it *might* be shadowed by a class method, and it should therefore not rely on subclass-specific behaviour. (3) A `default` method that calls an **abstract** method is the classic "template in an interface" pattern — the interface fixes the algorithm, the implementor supplies the steps — and it works precisely because the abstract method has no class implementation to conflict with. (4) The rule protects you from a genuine hazard: if a class hierarchy *incidentally* declares a method with the same name as an interface's `default`, the class's version silently wins — and the interface author may not have anticipated it. The mitigation is to **choose distinctive names** for `default` methods, and to document the extension contract.

### CQ7. "If `super` is statically bound, how do I call the grandparent's implementation?"

- **Naive:** "You can't."  ← *true, and it is a real limitation with a workaround*
- **The trap:** The trap is to leave it at "you can't" — because there *is* a workaround, and knowing it is the difference between understanding the model and just memorising the rule.
- **Expert answer:** The direct answer: **you cannot** — `super.super()` is not Java syntax, and `super` always refers to the *direct* superclass. The workaround, and the reason it works: `super.method()` in the *parent* is the only way to reach the grandparent's implementation, so the **parent must expose it**. Two shapes: (1) the parent calls `super.method()` in its own override — so the child's `super.method()` reaches the parent's version, which itself delegates to the grandparent's; (2) the parent exposes a `protected` method like `callGrandparent()` that does `super.method()`. The design lesson is that **`super` is not a mechanism for skipping a level — it is a mechanism for a class to explicitly include its parent's behaviour.** If you need "the grandparent's", the hierarchy is too deep, and the fix is composition (a `callGrandparent()` collaborator) rather than a deeper chain of `super` calls.

### CQ8. "How does the JVM's dispatch affect performance?"

- **Naive:** "Virtual calls are slower than static ones."  ← *the naive model, and the modern reality is different*
- **The trap:** The trap is to assume a permanent, meaningful cost. Modern JIT compilers **devirtualise**: a call site with a single observed receiver type is inlined and the dispatch disappears entirely. The cost is only real when a call site is **megamorphic** (many receiver types).
- **Expert answer:** The three tiers the JIT recognises: (1) **monomorphic** — one receiver type at the call site → the call is inlined and the check is free; (2) **bimorphic** — two types → the JIT emits a type check plus a second path; (3) **megamorphic** → the JIT gives up and emits a full `vtable` lookup. The practical consequences: (1) an `instanceof` chain is often **faster** than a virtual call, because it is a sequence of direct comparisons that the JIT can predict — so a "polymorphic" design is not automatically slower than an `instanceof` design; (2) a **megamorphic** call site (a method called on many different types) is the case to worry about, and the mitigation is to reduce the *number of distinct receiver types* at hot call sites; and (3) the `invokespecial` for `super` is a direct call — no dispatch at all. The engineering takeaway: **the polymorphism cost is a property of the call site's type diversity, not of the design being "OO"** — and the way to measure it is a profiler (`perf` or async-profiler), never an intuition.

### CQ9. "How do you write a method that behaves differently based on the runtime type of an argument, without the Visitor pattern?"

- **Naive:** "Use `instanceof`."  ← *works, and it has a cost the pattern matching reduces*
- **The trap:** The trap is to present the `instanceof` chain as equivalent to the Visitor. It is *behaviourally* equivalent for a closed set, but it has the "forgot a branch" hazard — and the modern answer (Java 21's pattern-matching `switch` over a `sealed` type) fixes exactly that.
- **Expert answer:** The three options, and the trade-off between them. (1) **`instanceof` chain** — simplest, but every chain must be updated when a type is added, and the compiler will not tell you. (2) **Pattern-matching `switch` over a `sealed` type** (Java 21) — the compiler checks exhaustiveness, so a missing branch is a **compile error**; record patterns also destructure the value. (3) **A `switch` over the type token with `Class.isInstance` and a cast** — the pre-21 version of (2), less safe. And the Visitor pattern is now rarely the right answer: it was the *only* way to get (2)'s benefits before Java 21, and its costs (the `accept` boilerplate in every element, the circular dependency) are no longer justified. The one case where the Visitor still wins: when the operation must be applied to a **deep, heterogeneous graph** and you want the operation to be a **first-class, testable object** that can be passed around — the Visitor is a *value* you can store, compose, and unit-test, whereas a `switch` is code you cannot pass.

### CQ10. "You mentioned `sealed` makes the Visitor pattern mostly unnecessary. Isn't that a big claim — the pattern is in every design-pattern book?"

- **Naive:** "Yes, Java 21 makes the Visitor unnecessary."  ← *too strong, and the interviewer will push*
- **The trap:** The trap is the overclaim. The Visitor pattern is not *gone* — it is **dominated in one of its two use cases** and still valuable in the other. The two use cases are: (a) **add an operation over a closed hierarchy** — now handled better by a `sealed` type + a pattern-matching `switch`; and (b) **apply an operation across a heterogeneous structure, as a composable object** — the Visitor still wins.
- **Expert answer:** The honest assessment. **Where the pattern is now dominated:** adding an operation to a closed hierarchy. A `sealed` interface + `switch` with record patterns gives exhaustiveness checking, no `accept` boilerplate in the elements, no circular dependency, and better performance (the JIT can devirtualise a `sealed` switch into a table). **Where the pattern is still right:** (1) the operation is a **first-class object** that is passed, stored, composed, or unit-tested — a `Validator` visitor that you can configure with a list of rules; (2) the structure is **deep and heterogeneous** and the operation must traverse it — the visitor encapsulates the traversal; (3) the operation must be applied to types from **different modules** that cannot be sealed together; and (4) you need the operation to work over a hierarchy that is **open** (a framework SPI) — a `sealed` type is impossible there. The meta-lesson: **a language feature absorbing a pattern does not make the pattern disappear — it moves the pattern's justification to the cases the feature does not cover.** The skill is knowing which case you are in, and the discipline of *revisiting* a pattern when the language moves under it.

---

## 10. Rapid-Fire MCQs

**Q1.** The four forms of polymorphism are…
- A) Static, dynamic, virtual, abstract  B) **Subtype, overriding, overloading, parametric**  C) Compile, run, link, load  D) Class, interface, enum, record

**Q2.** Overloading is resolved at…
- A) Runtime  B) **Compile time**  C) Link time  D) Load time

**Q3.** Overriding is resolved at…
- A) Compile time  B) **Runtime**  C) Link time  D) Load time

**Q4.** Are instance fields polymorphic?
- A) Yes  B) **No — field access is statically bound**  C) Only if `final`  D) Only if `static`

**Q5.** Are `static` methods overridable?
- A) Yes  B) **No — they are hidden**  C) Only if `private`  D) Only in interfaces

**Q6.** Are `private` methods inherited?
- A) Yes  B) **No**  C) Only if `protected`  D) Only in interfaces

**Q7.** `super.method()` performs…
- A) Virtual dispatch  B) **Static binding**  C) Reflection  D) An interface lookup

**Q8.** The JVM's dispatch order is…
- A) Interfaces → class → superclass  B) **Class → superclass chain → interfaces**  C) Superclass → class → interfaces  D) Alphabetical

**Q9.** A `default` method in an interface vs a class method in a superclass — which wins?
- A) The interface's  B) **The class's**  C) It is a compile error  D) It depends on the order

**Q10.** In `m(1)` where overloads are `m(int)` and `m(long)`, which is chosen?
- A) `m(long)`  B) **`m(int)`**  C) It is ambiguous  D) Neither

**Q11.** `m(null)` where overloads are `m(String)` and `m(Integer)` — what happens?
- A) `m(String)`  B) `m(Integer)`  C) **A compile error — ambiguous**  D) `m(Object)`

**Q12.** The best way to guarantee a subclass overrides rather than overloads is…
- A) A naming convention  B) **The `@Override` annotation**  C) `final`  D) A `static` modifier

**Q13.** A `final` method in a superclass…
- A) Is not inherited  B) **Cannot be overridden**  C) Is not polymorphic  D) Is `static`

**Q14.** The template method pattern uses `final` on the skeleton so that…
- A) It is faster  B) **The algorithm is invariant and only the hooks vary**  C) It cannot be inherited  D) It is thread-safe

**Q15.** `Object.toString()` beats an interface's `default toString()` because…
- A) `Object` is `final`  B) **The class chain is searched before the interface list**  C) `Object` is in `java.lang`  D) It does not — interfaces win

**Q16.** A `sealed` interface enables the compiler to…
- A) Inline the methods  B) **Check `switch` exhaustiveness**  C) Remove `final`  D) Optimise dispatch

**Q17.** The main cost of the Visitor pattern is…
- A) Performance  B) **The `accept` boilerplate in every element and the circular dependency**  C) It cannot handle generics  D) It is not thread-safe

**Q18.** Java 21's pattern-matching `switch` over a `sealed` type replaces…
- A) The `instanceof` chain and most Visitor uses  B) Only the `instanceof` chain  C) Only the Visitor  D) Nothing

**Q19.** A *megamorphic* call site is one where…
- A) The method is `final`  B) **Many receiver types are observed, so the JIT cannot inline**  C) The method is `static`  D) The class is `sealed`

**Q20.** Which of these is polymorphic?
- A) A `static` method  B) A field  C) **A public instance method**  D) A constructor

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | Subtype, overriding, overloading and parametric (generics) polymorphism. |
| 2 | **B** | Overloading is resolved by the compiler from the arguments' *static* types. |
| 3 | **B** | Overriding is resolved by the JVM from the object's runtime type. |
| 4 | **B** | Field access is a `getfield` resolved at link time against the declared type. |
| 5 | **B** | A child's `static` method *hides* the parent's; it does not replace it. |
| 6 | **B** | A `private` method is not inherited, so there is nothing to override. |
| 7 | **B** | `super` is a compile-time reference — `invokespecial`, not `invokevirtual`. |
| 8 | **B** | The actual class, then up the class chain, then down the interface list. |
| 9 | **B** | The class chain is searched *before* the interfaces. |
| 10 | **B** | An exact match beats a widening primitive conversion. |
| 11 | **C** | Neither is more specific than the other for a `null` literal — it is ambiguous. |
| 12 | **B** | `@Override` turns a silent overloading mistake into a compile error. |
| 13 | **B** | `final` on a method prevents overriding — it is still inherited. |
| 14 | **B** | The workflow is fixed shared knowledge; the steps are the extension points. |
| 15 | **B** | Step 2 (up the class chain) precedes step 3 (the interfaces). |
| 16 | **B** | The permitted set is known, so the compiler can prove a `switch` is complete. |
| 17 | **B** | The `accept` method pollutes every element and creates a cycle. |
| 18 | **A** | It provides exhaustiveness checking without the `accept` boilerplate. |
| 19 | **B** | The JIT cannot inline a call whose receiver type varies. |
| 20 | **C** | A public instance method is dispatched virtually on the runtime type. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE FOUR FORMS OF POLYMORPHISM                       │
│  ① SUBTYPE       Shape s = new Circle();        RUNTIME                  │
│  ② OVERRIDING    @Override void area()         RUNTIME                  │
│  ③ OVERLOADING   m(int) / m(String)            COMPILE                  │
│  ④ PARAMETRIC    <T> void add(T item)          COMPILE                  │
│                                                                          │
│  THE RULE:  OVERLOADING → by the arguments' static types                │
│             OVERRIDING  → by the object's runtime type                  │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    WHAT IS AND IS NOT POLYMORPHIC                       │
│  public instance method  →  ✅ YES   (virtual dispatch)                │
│  static method            →  ❌ no    (hidden, compile-time bound)     │
│  private method           →  ❌ no    (not inherited)                  │
│  final method             →  ❌ no    (cannot be overridden)           │
│  instance field           →  ❌ no    (statically bound)              │
│  constructor              →  ❌ no    (never inherited)                │
│                                                                          │
│  ⚠ METHODS ARE VIRTUAL.  FIELDS AND STATICS ARE NOT.                  │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE JVM's DISPATCH ORDER                             │
│  ① the object's ACTUAL class                                            │
│  ② walk UP the superclass chain                                        │
│  ③ walk DOWN the interface list (for a `default` method)                │
│                                                                          │
│  ⚠ CONSEQUENCE: a class method ALWAYS beats an interface's `default`.    │
│  ⚠ CONSEQUENCE: `Object.toString()` beats any interface `default`.        │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 5 POLYMORPHISM TRAPS                             │
│  1. Expecting `static` to be polymorphic   → it is hidden               │
│  2. Expecting fields to be polymorphic     → they are statically bound  │
│  3. Overloading instead of overriding      → the parent's version runs │
│  4. Forgetting @Override                   → a silent overloading bug  │
│  5. An `instanceof` chain with no default  → a silent missing case     │
│                                                                          │
│  THE FIX for #5: a `sealed` hierarchy + an exhaustive `switch`          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The polymorphism smells in a code review

| Smell | Review comment | Fix |
|---|---|---|
| A child hiding a `static` method | "Static methods are hidden, not overridden." | Rename it |
| A child hiding a field | "`p.field` and `p.getField()` can disagree." | Rename the field |
| An overload without `@Override` | "Did you mean to override?" | Add `@Override` |
| A long `instanceof` chain | "Adding a type will silently break this." | A `sealed` type + a `switch` |
| A `default` method that never runs | "A class in the hierarchy shadows it." | Override in the class |
| `super.method()` in a child | "That is the parent's version, always." | Use `this.method()` if you meant virtual |
| An overridable call in a constructor | "The subclass's fields are not initialised." | `final` or a static factory |
| `m(null)` with two overloads | "Ambiguous." | Cast explicitly |

### The dispatch-order design rules

```text
  ☐ A `default` method is a FALLBACK, not a primary behaviour
  ☐ Use DISTINCTIVE names for interface `default` methods (a class may shadow them)
  ☐ Never hide a `static` method or a field in a subclass
  ☐ ALWAYS annotate an override with @Override
  ☐ A `sealed` hierarchy + an exhaustive `switch` > an `instanceof` chain
  ☐ Keep hot call sites MONOMORPHIC — a megamorphic call site cannot be inlined
```

### Performance notes

| Topic | Guidance |
|---|---|
| **Monomorphic call sites** | The JIT inlines and the dispatch is free — do not "optimise" it away |
| **Megamorphic call sites** | The only real cost — reduce the receiver diversity, or use a `sealed` `switch` |
| **`instanceof` chains** | Often *faster* than a virtual call — the JIT predicts the branches |
| **`sealed` + `switch`** | The JIT can compile it into a jump table on the ordinal |
| **`super` calls** | `invokespecial` — a direct call, no dispatch at all |
| **`static` methods** | The JIT can inline and even constant-fold them |

### When to use the Visitor pattern (still)

```text
  ✅ The operation is a FIRST-CLASS object (passed, stored, composed, tested)
  ✅ The structure is deep and heterogeneous, and the operation must traverse it
  ✅ The types come from different modules that cannot be sealed together
  ✅ The hierarchy is OPEN (a framework SPI)
  ❌ Otherwise → a sealed type + a pattern-matching `switch` is simpler
```

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The sibling question (interface vs abstract class) | [06 · Interface vs Abstract Class](06-OOP-Interface-vs-Abstract-Class.md) |
| Inheritance, `super`, and the diamond | [04 · Inheritance & the Diamond Problem](04-OOP-Inheritance-Types-and-Diamond-Problem.md) |
| Encapsulation and the invariant | [03 · Encapsulation vs Abstraction](03-OOP-Encapsulation-vs-Abstraction.md) |
| `sealed`, records, pattern matching | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| The Visitor pattern in the wild | [09 · Design Patterns](../09-Design-Patterns/01-Design-Patterns.md) |
| Generics (parametric polymorphism) | [05 · Generics](../05-Generics/01-Generics.md) |

---

**Next Chapter:** → [06 · Interface vs Abstract Class](06-OOP-Interface-vs-Abstract-Class.md)
**Previous Chapter:** ← [04 · Inheritance & the Diamond Problem](04-OOP-Inheritance-Types-and-Diamond-Problem.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
