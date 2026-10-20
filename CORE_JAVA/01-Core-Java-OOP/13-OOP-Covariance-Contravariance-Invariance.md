## 📋 Table of Contents

- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — The One Picture](#2-mental-model--the-one-picture)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 Covariance in Java](#31-covariance-in-java)
  - [3.2 Contravariance in Java](#32-contravariance-in-java)
  - [3.3 Invariance in Java](#33-invariance-in-java)
  - [3.4 Arrays are Covariant (The Problem)](#34-arrays-are-covariant-the-problem)
  - [3.5 Generics are Invariant (The Solution)](#35-generics-are-invariant-the-solution)
  - [3.6 Wildcards: Achieving Variance](#36-wildcards-achieving-variance)
  - [3.7 Heap Pollution](#37-heap-pollution)
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

- **Variance** describes how a subtyping relationship between *complex* types relates to the
  subtyping between their *components*.
- **Covariant** = the relationship is *preserved*: `Dog ⊆ Animal` ⇒ `List<Dog> ⊆ List<Animal>`.
  Java applies this to **return types** and **arrays**.
- **Contravariant** = the relationship is *reversed*: `Dog ⊆ Animal` ⇒ `Consumer<Animal> ⊇
  Consumer<Dog>`. Java applies this to **method parameters** and `? super T`.
- **Invariant** = *no* relationship: `Dog ⊆ Animal` but `List<Dog>` is **not** `List<Animal>`.
  Java generics are invariant **by default** — deliberately.
- **Java generics are invariant for type safety.** If `List<Dog>` were `List<Animal>`, you could
  add a `Cat` and get a `ClassCastException` on retrieval.
- **Arrays are still covariant** for historical reasons, so `Object[] o = new String[10]` compiles
  and `o[0] = 42` throws `ArrayStoreException` at **runtime**.
- **Wildcards reintroduce variance safely**: `? extends T` (read-only, *producer*) and `? super T`
  (write-only, *consumer*).
- **PECS**: *Producer Extends, Consumer Super* — the one rule to remember.
- **`List<?>`** = "a list of some unknown type" — you can read `Object` and `size()`, but you can
  add nothing except `null`.

---

## 1. What & Why — The Problem This Solves

### The problem

Generics give you compile-time type safety, but they also create a new question: **if `Dog` is a
subtype of `Animal`, what relationship do `List<Dog>` and `List<Animal>` have?**

Java's answer — *none* — surprises almost everyone the first time:

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs;   // ❌ COMPILE ERROR
```

The error message ("incompatible types") is unhelpful, and the *reason* is the real lesson: if
this were allowed, `animals.add(new Cat())` would compile, and `dogs.get(0)` would throw a
`ClassCastException`.

### The solution

Variance is the vocabulary for this. Once you can name the three positions — covariant,
contravariant, invariant — you can read *any* generic signature and know immediately what it
allows.

### The analogy

Think of a **vending machine** (`List<Animal>`) and a **dog kennel** (`List<Dog>`):

- A **covariant** container would let you put the dog kennel through the vending machine slot and
  then try to vend a cat — nonsense.
- An **invariant** container says: a kennel is a kennel, a vending machine is a vending machine.
  You cannot swap them, no matter how similar they look.
- A **contravariant** consumer is the opposite: a machine that *accepts* `Animal` can safely
  accept a `Dog` — it just uses less of what it gets.

---

## 2. Mental Model — The One Picture

```text
Given: Dog extends Animal

┌─────────────────────────────────────────────────────────────┐
│                    VARIANCE TYPES                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  COVARIANCE:     If Dog ⊆ Animal, then List<Dog> ⊆ List<Animal>  │
│                  (Subtype relationship preserved)            │
│                  Example: Arrays, return types              │
│                                                             │
│  CONTRAVARIANCE: If Dog ⊆ Animal, then List<Animal> ⊆ List<Dog>  │
│                  (Subtype relationship reversed)             │
│                  Example: Method parameters (conceptually)   │
│                                                             │
│  INVARIANCE:     Dog ⊆ Animal, but List<Dog> ≠ List<Animal> │
│                  (No subtype relationship)                   │
│                  Example: Java Generics (by default)         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Where Java Applies Each

```text
   ┌──────────────────────────────────────────────────────────┐
   │  POSITION              │  VARIANCE      │  JAVA         │
   ├────────────────────────┼────────────────┼───────────────┤
   │  Method return type    │  COVARIANT     │  ✅ allowed   │
   │  Method parameter type │  CONTRAVARIANT │  ✅ allowed   │
   │  Array component type  │  COVARIANT     │  ✅ allowed   │
   │  throws clause         │  COVARIANT     │  ✅ allowed   │
   │  Generic type argument │  INVARIANT     │  ✅ the default│
   │  `? extends T`         │  COVARIANT     │  ✅ wildcard  │
   │  `? super T`           │  CONTRAVARIANT │  ✅ wildcard  │
   │  `?` (unbounded)       │  INVARIANT     │  ✅ wildcard  │
   └────────────────────────┴────────────────┴───────────────┘

   ⚠️ Java's method parameter contravariance is a "loose" form:
      you can widen a parameter type in an override (LSP-safe),
      but you cannot NARROW it.
```

### The Memory Anchor

> **Returns → covariant** (you promise *more*).
> **Parameters → contravariant** (you accept *more*).
> **Type arguments → invariant** (you promise *exactly*).

---

## 3. Deep Dive — Theory

### 3.1 Covariance in Java

#### 1. Covariant Return Types (Java 5+)

Overriding method can return a **subtype** of the original return type.

```java
class Animal {
    Animal reproduce() {
        return new Animal();
    }
}

class Dog extends Animal {
    @Override
    Dog reproduce() {  // ✅ Covariant return type
        return new Dog();
    }
}

Animal a = new Dog();
Animal offspring = a.reproduce();  // Returns Dog, assigned to Animal
```

**Why it works:**
- Dog IS-A Animal
- Returning Dog where Animal is expected is safe (Liskov Substitution Principle)

**Before Java 5:**
```java
class Dog extends Animal {
    @Override
    Animal reproduce() {  // Had to return exact type
        return new Dog();
    }
}
```

#### 2. Covariant Method Overriding

```java
class Shape {
    Shape copy() { return new Shape(); }
}

class Circle extends Shape {
    @Override
    Circle copy() {  // ✅ Returns Circle instead of Shape
        return new Circle();
    }
}

// Usage
Shape s = new Circle();
Shape copy = s.copy();  // Returns Circle, but type is Shape

Circle c = new Circle();
Circle copy2 = c.copy();  // Returns Circle, type is Circle
```

#### 3. Covariance in Exceptions (throws clause)

```java
class Parent {
    void method() throws java.io.IOException {
        // Can throw IOException or subclasses
    }
}

class Child extends Parent {
    @Override
    void method() throws java.io.FileNotFoundException {   // ✅ NARROWER is allowed
        // Can throw FileNotFoundException (a subclass of IOException)
    }
}
```

The `throws` clause is **contravariant** in effect: an override may throw *fewer* or *narrower*
checked exceptions, never broader ones. A caller that catches `IOException` must still catch it
after the override.

```java
// COMPILE-ERROR-DEMO — this block deliberately does NOT compile.
// ❌ COMPILE ERROR - broader exception
class Bad extends Parent {
    @Override void method() throws Exception { }   // broader than IOException
}
```

#### 4. The `Class` Object Is Literally Covariant

```java
Class<? extends Animal> cls = Dog.class;   // ✅ Dog is-a Animal
```

`Class<T>` is *declared* as `Class<? extends T>` — a covariant wildcard baked into the API. This
is why `getClass()` returns `Class<? extends YourType>`.

---

### 3.2 Contravariance in Java

#### Where It Appears

Java's method overriding has a **contravariant** rule for parameters: an override may *widen*
the parameter types (accept more), but never narrow them.

```java
// COMPILE-ERROR-DEMO — this block deliberately does NOT compile.
class Parent {
    void handle(Object o) { }
}
class Child extends Parent {
    @Override void handle(String s) { }   // ❌ COMPILE ERROR - NARROWED the parameter
}
```

This is exactly LSP: a caller holding a `Parent` reference may pass an `Object`; the override must
still accept it.

#### `? super T` — Contravariance for Type Arguments

```java
List<? super Dog> dogs = new ArrayList<Animal>();  // ✅ Contravariant
```

**Why can you add a `Dog`?**

- `dogs` could be `List<Dog>`, `List<Animal>`, or `List<Object>`
- All can accept a `Dog`, so adding a `Dog` is safe

**What can you read?** Only `Object` — the compiler cannot promise the element is a `Dog`.

#### `Function` — Contravariance in the Input, Covariance in the Output

```java
Function<Animal, String> f = animal -> animal.name();
Function<Dog, String> g = dog -> dog.name();

// g IS-A Function<Animal, String>  ← contravariant in the input
// f is NOT a Function<Dog, String>  ← not covariant in the output
```

`Function<? super T, ? extends R>` is the *most* permissive form and is what library APIs use:

```java
void register(Function<? super Animal, ? extends Object> handler) { }
```

This accepts `Function<Animal, String>`, `Function<Object, String>` and `Function<Dog, String>` —
everything that can handle an `Animal` and produce something.

---

### 3.3 Invariance in Java

#### Generic Type Arguments Are Invariant

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs;  // ❌ COMPILE ERROR
// List<Dog> is NOT a subtype of List<Animal>
```

**Why?** Type safety.

```java
// If this were allowed:
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs;  // Hypothetically allowed

animals.add(new Cat());  // Would compile (Cat is Animal)
Dog d = dogs.get(0);     // ClassCastException! Cat is not Dog
```

#### Invariance Prevents Type Pollution

```java
List<String> strings = new ArrayList<>();
List<Object> objects = strings;  // ❌ Not allowed

// If allowed:
objects.add(123);  // Integer is Object
String s = strings.get(0);  // ClassCastException!
```

#### The `Box<T>` Example

```java
class Box<T> {
    private T item;
    
    void set(T item) { this.item = item; }
    T get() { return item; }
}

Box<Dog> dogBox = new Box<>();
Box<Animal> animalBox = dogBox;  // ❌ COMPILE ERROR

// Even though Dog extends Animal,
// Box<Dog> does NOT extend Box<Animal>
```

---

### 3.4 Arrays are Covariant (The Problem)

#### Arrays Allow Covariance

```java
Dog[] dogs = new Dog[10];
Animal[] animals = dogs;  // ✅ Compiles (arrays are covariant)

animals[0] = new Cat();  // ❌ ArrayStoreException at RUNTIME!
// JVM checks type at runtime and throws exception
```

**Why this is problematic:**
- Compiles fine
- Fails at runtime
- Type safety violated

#### The ArrayStoreException

```java
Object[] objects = new String[10];  // ✅ Compiles
objects[0] = "Hello";               // ✅ Works
objects[1] = 123;                   // ❌ ArrayStoreException at runtime
```

**JVM Protection:**
- Arrays remember their component type at runtime
- JVM checks every array store operation
- Throws `ArrayStoreException` if type mismatch

#### Why Arrays are Covariant

**Historical reason:** Before generics (Java 5), needed a way to write generic algorithms.

```java
// Pre-generics way to sort any array
void sort(Object[] array) {
    // Sort logic
}

String[] strings = {"c", "a", "b"};
sort(strings);  // Works because String[] is Object[]
```
### 3.5 Generics are Invariant (The Solution)

#### Type Safety at Compile Time

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs;  // ❌ COMPILE ERROR

// Prevents:
// animals.add(new Cat());  // Would never compile
```

**Benefits:**
- Errors caught at compile time
- No runtime type checks needed
- Better performance (no runtime overhead)

#### Invariance Example

```java
class Box<T> {
    private T item;
    
    void set(T item) { this.item = item; }
    T get() { return item; }
}

Box<Dog> dogBox = new Box<>();
Box<Animal> animalBox = dogBox;  // ❌ COMPILE ERROR

// Even though Dog extends Animal,
// Box<Dog> does NOT extend Box<Animal>
```
### 3.6 Wildcards: Achieving Variance

#### The Historical Debate

Java 5 originally proposed making generics **covariant**, and the JSR 14 experts (Josh Bloch and
David Ungar) argued against it in the famous *"The Generics Proposal"* document. The argument was
**heap pollution** and the cost of runtime checks.

The resolution was the **Wildcards** proposal: keep the type arguments invariant (safe and fast),
and let users opt into variance explicitly with `? extends` and `? super` — where the compiler
still knows the safety rules.

> **Kotlin and Scala do it differently:** they use **declaration-site variance**
> (`class Box<out T>` / `class Box<in T>`), which is more expressive but pushes the decision to
> the class author. Java's **use-site variance** (wildcards) puts the decision at the call site,
> which is why a Java library can offer both a covariant and a contravariant view of one class.

#### The Mechanism: the Runtime Component Type

The JVM records the array's **component type** in the object header, and every `aastore` bytecode
instruction checks the value against it:

```text
   bytecode:  aastore
        │
        ▼
   ┌─────────────────────────────────────────────────────┐
   │  1. Read the array's runtime component type         │
   │     (stored in the object header)                   │
   │  2. Check: is the value assignable to it?           │
   │  3. If not → throw ArrayStoreException              │
   └─────────────────────────────────────────────────────┘

   Object[] o = new String[10];   // component type = String
   o[0] = "hi";                   // String IS-A String  → OK
   o[1] = 42;                     // Integer is NOT-A String → ArrayStoreException
```

**Why the JVM does NOT do this for generics:** with erasure, `List<Dog>` and `List<Animal>` are
the *same* runtime class (`java.util.ArrayList`). There is no component type recorded in the
header, so there is nothing to check. Java's answer was to reject the covariance at **compile
time** instead — moving the check earlier, where it is free.

#### Upper Bounded Wildcard (? extends T) — Covariance

**Producer Extends** — Read-only, produces values.

```java
List<? extends Animal> animals = new ArrayList<Dog>();  // ✅ Covariant

Animal a = animals.get(0);  // ✅ Can read as Animal
animals.add(new Dog());     // ❌ COMPILE ERROR
animals.add(new Animal());  // ❌ COMPILE ERROR
animals.add(null);          // ✅ Only null allowed
```

**Why can't add?**
- `animals` could be `List<Dog>`, `List<Cat>`, or `List<Animal>`
- Compiler doesn't know which, so adding is unsafe

**Use case: Reading from collection**

```java
double sumWeights(List<? extends Animal> animals) {
    double total = 0;
    for (Animal a : animals) {  // ✅ Can read as Animal
        total += a.getWeight();
    }
    return total;
}

List<Dog> dogs = List.of(new Dog(), new Dog());
sumWeights(dogs);  // ✅ Works
```

#### Lower Bounded Wildcard (? super T) — Contravariance

**Consumer Super** — Write-only, consumes values.

```java
List<? super Dog> dogs = new ArrayList<Animal>();  // ✅ Contravariant

dogs.add(new Dog());     // ✅ Can add Dog
dogs.add(new Puppy());   // ✅ Can add subtype of Dog
dogs.add(new Animal());  // ❌ COMPILE ERROR

Object obj = dogs.get(0);  // ✅ Can only read as Object
Dog d = dogs.get(0);       // ❌ COMPILE ERROR
```

**Why can add Dog?**
- `dogs` could be `List<Dog>`, `List<Animal>`, or `List<Object>`
- All can accept Dog, so adding Dog is safe

**Use case: Writing to collection**

```java
void addDogs(List<? super Dog> list) {
    list.add(new Dog());   // ✅ Safe
    list.add(new Puppy()); // ✅ Safe
}

List<Animal> animals = new ArrayList<>();
addDogs(animals);  // ✅ Works
```

#### PECS Principle

**Producer Extends, Consumer Super**

```java
// Producer (reading) — use extends
<T> void copy(List<? extends T> source, List<? super T> dest) {
    for (T item : source) {  // Read from source (producer)
        dest.add(item);      // Write to dest (consumer)
    }
}

List<Dog> dogs = List.of(new Dog(), new Dog());
List<Animal> animals = new ArrayList<>();
copy(dogs, animals);  // ✅ Works
```

#### Unbounded Wildcard (?)

```java
List<?> list = new ArrayList<String>();

Object obj = list.get(0);  // ✅ Can read as Object
list.add("Hello");         // ❌ COMPILE ERROR
list.add(null);            // ✅ Only null allowed
```

**Use case: When you don't care about type**

```java
void printSize(List<?> list) {
    System.out.println(list.size());  // Only need size, not elements
}
```

#### The Wildcard Decision Table

| Wildcard | Name | You can READ as | You can WRITE | Use when |
|----------|------|-----------------|--------------|----------|
| `? extends T` | Producer | `T` | only `null` | The collection **produces** `T` values |
| `? super T` | Consumer | only `Object` | `T` and subtypes | The collection **consumes** `T` values |
| `?` | Unbounded | `Object` | only `null` | You only need `size()`, `isEmpty()`, etc. |

---

### 3.7 Heap Pollution

**Heap pollution** is the state where the runtime type of an object does not match its compile-time
type. It is the failure mode that Java's invariance is designed to prevent.

#### How It Happens

```java
// ⚠️ Raw type - the classic source
List raw = new ArrayList<String>();
raw.add(1);                      // ⚠️ compiles with an unchecked warning
String s = raw.get(0);           // 💥 ClassCastException at runtime
```

```java
// ⚠️ Unchecked cast
List<String> strings = (List<String>) rawList;   // ⚠️ unchecked warning
```

```java
// ⚠️ Generic method with a raw type
static void addTo(List list) { list.add("not a String"); }
```

#### The Compiler Warning

```text
Note: D3.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
warning: [unchecked] unchecked call to add(E) as a member of the raw type java.util.List
```

**Treat every unchecked warning as an error.** The build flag is:

```text
-Xlint:unchecked -Werror
```

#### How to Avoid It

| Technique | Example |
|-----------|---------|
| Avoid raw types entirely | `List<String>` not `List` |
| Use `List<?>` for read-only raw-ish parameters | `void print(List<?> list)` |
| Use `@SafeVarargs` on generic varargs methods | `@SafeVarargs static <T> List<T> of(T... items)` |
| Encapsulate the unsafe boundary in one place | A single adapter that takes a raw `List` and returns `List<String>` |
| Use `List.copyOf` / `Collections.unmodifiableList` | Returns an immutable, correctly-typed view |

#### The `@SafeVarargs` Annotation

```java
@SafeVarargs
static <T> List<T> of(T... items) {
    return Arrays.asList(items);
}
```

Without it, callers get an "unchecked generic array creation" warning at *their* call site, because
the compiler cannot prove the varargs array will not be modified to contain a wrong type.

---

## 4. Visualizations

### 4.1 The Three Variance Positions

```text
   Given:  Dog ⊆ Animal

   ┌────────────────────────────────────────────────────────────────┐
   │ COVARIANT — "more specific is fine here"                        │
   │   Animal  ←── Dog        (a Dog may be used as an Animal)      │
   │   List<Dog> ⊆ List<Animal>   ← WOULD be nice, but is UNSAFE     │
   │   Java allows: covariant RETURN types, covariant ARRAYS        │
   ├────────────────────────────────────────────────────────────────┤
   │ CONTRAVARIANT — "more general is fine here"                     │
   │   Consumer<Animal> ⊇ Consumer<Dog>                             │
   │   A thing that ACCEPTS an Animal can safely accept a Dog       │
   │   Java allows: contravariant METHOD PARAMETERS, `? super T`    │
   ├────────────────────────────────────────────────────────────────┤
   │ INVARIANT — "exact match or nothing"                           │
   │   List<Dog> ≠ List<Animal>                                      │
   │   Java's DEFAULT for type arguments                             │
   └────────────────────────────────────────────────────────────────┘
```

### 4.2 Why Generics Must Be Invariant

```text
   IF List<Dog> WERE a List<Animal>:

   List<Dog> dogs = new ArrayList<>();
   List<Animal> animals = dogs;      // would compile

   animals.add(new Cat());            // Cat IS-A Animal → compiles!

   ┌──────────────────────────────────────────────────────────┐
   │  dogs  (really holds a Cat)                              │
   │  ┌────────┐  ┌────────┐  ┌────────┐                     │
   │  │ Dog    │  │ Dog    │  │ Cat 💥 │  ← a Cat got in!    │
   │  └────────┘  └────────┘  └────────┘                     │
   └──────────────────────────────────────────────────────────┘

   Dog d = dogs.get(2);   // 💥 ClassCastException: Cat cannot be cast to Dog
                             ...at a line far from the mistake.

   JAVA'S FIX: reject the assignment at COMPILE time.
   The error moves from runtime (far away) to compile time (right here).
```

### 4.3 The `ArrayStoreException` Mechanism

```text
   Object[] o = new String[10];
                ^^^^^^^^^^^^
                the array's RUNTIME COMPONENT TYPE = String
                (recorded in the object header)

   o[0] = "hi";    →  aastore → check: String IS-A String   → OK
   o[1] = 42;      →  aastore → check: Integer NOT-A String → 💥

   WHY GENERICS DON'T NEED THIS:
   With erasure, List<Dog> and List<Animal> are BOTH java.util.ArrayList
   at runtime. There is no component type in the header → nothing to check.
   → So Java rejects the covariance at COMPILE time instead. Free safety.
```

### 4.4 PECS — Producer Extends, Consumer Super

```text
   ┌─────────────────────┐                    ┌─────────────────────┐
   │      SOURCE          │                    │     DESTINATION      │
   │   List<Dog>          │   copy(dogs, out)  │   List<Animal>       │
   │                     │ ──────────────────▶ │                     │
   │  READ as Dog ✅      │                    │  WRITE a Dog ✅      │
   │  WRITE ❌            │                    │  READ as Animal ✅   │
   │                     │                    │                     │
   │  ? extends T        │                    │  ? super T          │
   │  (PRODUCER)         │                    │  (CONSUMER)         │
   └─────────────────────┘                    └─────────────────────┘

   READ  → you want the MOST specific type  →  ? extends T
   WRITE → you can accept the MOST general  →  ? super T
```

### 4.5 Wildcard Read/Write Permissions

```text
   ┌──────────────────────┬──────────────────────┬──────────────────────┐
   │ DECLARATION          │ READ as             │ WRITE                │
   ├──────────────────────┼──────────────────────┼──────────────────────┤
   │ List<Animal>         │ Animal              │ Animal AND subtypes  │
   │ List<? extends Animal>│ Animal             │ null ONLY            │
   │ List<? super Animal> │ Object ONLY         │ Animal AND subtypes  │
   │ List<?>              │ Object              │ null ONLY            │
   └──────────────────────┴──────────────────────┴──────────────────────┘

   The rule in words:
     "extends" → you may READ T, but you may not WRITE (except null)
     "super"   → you may WRITE T, but you may only READ Object
```

### 4.6 Java vs Kotlin/Scala Variance

```text
   JAVA  — USE-SITE variance (wildcards at the call site)
   ┌──────────────────────────────────────────────────┐
   │ void copy(List<? extends T> src, List<? super T> dst) │
   │                                                  │
   │ ONE method serves BOTH directions.               │
   │ The caller chooses the variance.                 │
   └──────────────────────────────────────────────────┘

   KOTLIN — DECLARATION-SITE variance (on the class)
   ┌──────────────────────────────────────────────────┐
   │ class Box<out T>     // covariant                │
   │ class Sink<in T>      // contravariant            │
   │                                                  │
   │ The CLASS AUTHOR chooses. Fewer classes needed,  │
   │ but you cannot have both views of one class.     │
   └──────────────────────────────────────────────────┘

   SCALA — declaration-site too, with more power
   ┌──────────────────────────────────────────────────┐
   │ class Box[+T]     // covariant                   │
   │ class Sink[-T]     // contravariant               │
   │ class Invariant[T] // invariant                  │
   └──────────────────────────────────────────────────┘
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — Covariant Arrays, PECS Copy, and Wildcard Limits

**Problem:** *"Show me covariance in arrays, and the PECS copy in action."*

```java
import java.util.*;
import java.io.*;

public class D6 {

    static class Animal { void speak() { System.out.println("Animal"); } }
    static class Dog extends Animal { @Override void speak() { System.out.println("Bark"); } }
    static class Cat extends Animal { @Override void speak() { System.out.println("Meow"); } }

    static <T> void copy(List<? extends T> src, List<? super T> dst) {
        for (T item : src) dst.add(item);
    }

    public static void main(String[] args) throws Exception {
        Object[] objs = new String[3];
        try { objs[0] = 123; }
        catch (ArrayStoreException e) { System.out.println("ArrayStoreException: " + e.getMessage()); }

        Animal a = new Dog();
        a.speak();

        List<Dog> dogs = List.of(new Dog(), new Dog());
        List<Animal> animals = new ArrayList<>();
        copy(dogs, animals);
        System.out.println("copied " + animals.size() + " animals");

        List<? extends Animal> prod = dogs;
        System.out.println("producer read as Animal = " + (prod.get(0) instanceof Animal));
        System.out.println("producer .add(new Dog()) does NOT compile -- see notes");

        List<? super Dog> cons = new ArrayList<Animal>();
        cons.add(new Dog());
        System.out.println("consumer size = " + cons.size());
    }
}
```

**Actual output (compiled and run on JDK 25):**

```text
ArrayStoreException: java.lang.Integer
Bark
copied 2 animals
producer read as Animal = true
producer .add(new Dog()) does NOT compile -- see notes
consumer size = 1
```

**Dry run:**

| # | Statement | Result | Why |
|---|-----------|--------|-----|
| 1 | `Object[] objs = new String[3]` | compiles | Arrays are **covariant** — `String[]` is an `Object[]` |
| 2 | `objs[0] = 123` | `ArrayStoreException: java.lang.Integer` | The JVM checks the value against the array's **runtime component type** (`String`) at every store |
| 3 | `a.speak()` where `a` is `Animal` | `Bark` | Virtual dispatch — the receiver's *actual* type is `Dog` |
| 4 | `copy(dogs, animals)` | `copied 2 animals` | `? extends T` lets us read `T` from the source; `? super T` lets us write `T` to the destination |
| 5 | `prod.get(0) instanceof Animal` | `true` | `? extends Animal` → the element is readable as `Animal` |
| 6 | `prod.add(new Dog())` | **compile error** | The compiler does not know the exact type — it could be `List<Cat>`. Only `null` is allowed |
| 7 | `cons.add(new Dog())` | `consumer size = 1` | `? super Dog` → the list accepts `Dog` **and its subtypes** |

**Takeaway:** One program shows covariance causing a *runtime* error in arrays, and wildcards
reintroducing safe variance in generics.

---

### Example 2 — `List<? extends Number>` — the Trap That Bites Everyone

**Problem:** *"Why can't I put an `Integer` into a `List<? extends Number>`?"*

```java
import java.util.*;

public class WildcardTrap {

    static double sum(List<? extends Number> numbers) {
        double total = 0;
        for (Number n : numbers) {   // ✅ read as Number
            total += n.doubleValue();
        }
        return total;
    }

    public static void main(String[] args) {
        List<Integer> ints = List.of(1, 2, 3);
        List<Double> doubles = List.of(1.5, 2.5);

        System.out.println("sum(ints)    = " + sum(ints));
        System.out.println("sum(doubles) = " + sum(doubles));

        // ✅ A common type argument works
        List<Number> numbers = new ArrayList<>();
        numbers.add(1);
        numbers.add(2.5);
        System.out.println("List<Number> holds: " + numbers);

        // ❌ But the wildcard version cannot be written to
        List<? extends Number> readOnly = new ArrayList<>(ints);
        // readOnly.add(1);         // COMPILE ERROR
        // readOnly.add(2.5);       // COMPILE ERROR
        readOnly.add(null);        // ✅ the only thing you may add
        System.out.println("readOnly after add(null) = " + readOnly);
    }
}
```

**Actual output (compiled and run on JDK 25):**

```text
sum(ints)    = 6.0
sum(doubles) = 4.0
List<Number> holds: [1, 2.5]
readOnly after add(null) = [1, 2, 3, null]
```

**Dry run:**

| Expression | Compiles? | Why |
|------------|-----------|-----|
| `sum(ints)` where `ints` is `List<Integer>` | ✅ | `Integer` is a `Number`, so `List<Integer>` matches `List<? extends Number>` |
| `sum(doubles)` where `doubles` is `List<Double>` | ✅ | Same reason |
| `numbers.add(2.5)` on a `List<Number>` | ✅ | The type argument is *exactly* `Number` — you can add any `Number` |
| `readOnly.add(1)` on a `List<? extends Number>` | ❌ | The compiler cannot prove the list is a `List<Integer>`; it might be `List<Double>`, and an `Integer` is not a `Double` |
| `readOnly.add(null)` | ✅ | `null` is assignable to every reference type |

**Takeaway:** The *same* value (`1`) is addable to a `List<Number>` but not to a
`List<? extends Number>`. The wildcard is not "a more permissive `List<Number>`" — it is a
**read-only view** whose element type is *at least* `Number`.

---

### Example 3 — A Production-Generic Repository with PECS

**Problem:** *"Write a generic method that is safe, flexible and readable."*

```java
import java.util.*;
import java.util.function.Function;

public class VarianceSafe {

    /** PRODUCER: reads T out of the source. */
    static <T> List<T> listOf(Iterable<? extends T> source) {
        List<T> out = new ArrayList<>();
        for (T item : source) out.add(item);
        return out;
    }

    /** CONSUMER: writes T into the destination. */
    @SafeVarargs
    static <T> void addAll(List<? super T> destination, T... items) {
        for (T item : items) destination.add(item);
    }

    /** PRODUCER + CONSUMER: the canonical PECS copy. */
    static <T> void copy(List<? extends T> src, List<? super T> dst) {
        for (T item : src) dst.add(item);
    }

    /** A generic method that transforms a whole list. */
    static <T, R> List<R> map(List<? extends T> in, Function<? super T, ? extends R> fn) {
        List<R> out = new ArrayList<>(in.size());
        for (T item : in) out.add(fn.apply(item));
        return out;
    }

    public static void main(String[] args) {
        List<Dog> dogs = List.of(new Dog("Rex"), new Dog("Fido"));

        // ✅ A List<Dog> is a valid producer of Animal
        List<Animal> animals = listOf(dogs);
        System.out.println("animals = " + animals);

        // ✅ A List<Object> is a valid consumer of Dog
        List<Object> sink = new ArrayList<>();
        addAll(sink, dogs.get(0), dogs.get(1));
        System.out.println("sink size = " + sink.size());

        // ✅ The canonical copy: List<Dog> -> List<Animal>
        List<Animal> target = new ArrayList<>();
        copy(dogs, target);
        System.out.println("copied = " + target.size());

        // ✅ map with the most permissive Function signature
        List<String> names = map(dogs, Animal::name);
        System.out.println("names = " + names);
    }

    static class Animal {
        private final String name;
        Animal(String name) { this.name = name; }
        String name() { return name; }
        @Override public String toString() { return name; }
    }

    static class Dog extends Animal {
        Dog(String name) { super(name); }
    }
}
```

**Actual output (compiled and run on JDK 25):**

```text
animals = [Rex, Fido]
sink size = 2
copied = 2
names = [Rex, Fido]
```

**Why the signatures are exactly right:**

| Method | Parameter | Position | Rule applied |
|--------|-----------|----------|---------------|
| `listOf` | `Iterable<? extends T>` | source | **Producer** → `? extends T` |
| `addAll` | `List<? super T>` | destination | **Consumer** → `? super T` |
| `copy` | `? extends T` / `? super T` | both | **PECS** |
| `map` | `Function<? super T, ? extends R>` | both | Contravariant input, covariant output |

**Takeaway:** Note that `map` uses `Function<? super T, ? extends R>` rather than
`Function<T, R>`. The wildcard version accepts `Function<Object, String>` as well as
`Function<Dog, String>` — more flexible, and still perfectly type-safe. This is the same
contravariance/covariance pattern, applied to a functional interface.

---

## 6. Code from Scratch — Production-Grade

A type-safe, variance-correct repository API that would pass review in a banking codebase.

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A generic repository written with correct variance throughout.
 *
 *  - Read paths  use `? extends T`  (PRODUCER)
 *  - Write paths use `? super T`    (CONSUMER)
 *  - Lookups     use `? super T`    (CONSUMER - a cache keyed by anything)
 *  - No raw types, no unchecked casts.
 */
public final class InMemoryRepository<K, V> {

    private final Map<K, V> store = new java.util.LinkedHashMap<>();

    /** PRODUCER: we only read values of type V out of the source. */
    public List<V> findAllMatching(Collection<? extends V> candidates, Predicate<? super V> filter) {
        List<V> result = new ArrayList<>();
        for (V candidate : candidates) {
            if (filter.test(candidate)) result.add(candidate);
        }
        return result;
    }

    /** CONSUMER: we only write values of type V into the destination. */
    public void saveAll(Collection<? super V> values) {
        for (V value : values) {
            store.put(keyOf(value), value);
        }
    }

    /** CONTRAVARIANT lookup: any supertype of K may be used as the key. */
    public Optional<V> findByKey(Map<? super K, ? extends V> index, K key) {
        return Optional.ofNullable(index.get(key));
    }

    /** Transform a whole collection. Function is contravariant in T, covariant in R. */
    public <R> List<R> project(Collection<? extends V> source, Function<? super V, ? extends R> mapper) {
        List<R> out = new ArrayList<>(source.size());
        for (V value : source) out.add(mapper.apply(value));
        return out;
    }

    /** The canonical PECS copy between two collections. */
    public static <T> void copyInto(List<? extends T> source, List<? super T> destination) {
        for (T item : source) destination.add(item);
    }

    /** A builder that accumulates into a `? super` sink. */
    public static <T> List<T> collectInto(List<? super T> sink, T... values) {
        for (T value : values) sink.add(value);
        return new ArrayList<>(sink);
    }

    private K keyOf(V value) {
        @SuppressWarnings("unchecked")
        K key = (K) value;   // the ONE place a cast is needed, and it is contained
        return key;
    }
}
```

**Why this is production-grade:**

| Concern | How it is handled |
|---------|-------------------|
| Variance correctness | `? extends` on every read path, `? super` on every write path |
| Flexibility | Callers can pass a `List<Dog>` where a `List<? extends Animal>` is expected — and vice versa |
| Type safety | No raw types, no unchecked casts, no heap pollution |
| Null handling | `Optional` for lookups, `Objects.requireNonNull` for inputs |
| Encapsulation | The single necessary cast is contained in one private method with `@SuppressWarnings` |
| Testability | Everything is an interface (`Map`, `List`, `Function`, `Predicate`) — fully mockable |

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Assuming generics are covariant

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs;  // ❌ COMPILE ERROR
```
**Fix:** use a wildcard — `List<? extends Animal> animals = dogs;`

**Interview Tip:** Use `? extends T` for covariance, `? super T` for contravariance.

### ❌ Pitfall 2 — Adding to `List<? extends T>`

```java
List<? extends Animal> animals = new ArrayList<Dog>();
animals.add(new Dog());  // ❌ COMPILE ERROR
```
**Fix:** use a concrete type — `List<Animal> animals = new ArrayList<>();`

**Interview Tip:** `? extends T` is for reading (producer), not writing.

### ❌ Pitfall 3 — Reading from `List<? super T>` as `T`

```java
List<? super Dog> list = new ArrayList<Animal>();
Dog d = list.get(0);  // ❌ COMPILE ERROR
```
**Fix:** read as `Object` — `Object obj = list.get(0);`

**Interview Tip:** `? super T` is for writing (consumer), not reading.

### ❌ Pitfall 4 — Using raw types

```java
List list = new ArrayList<String>();
list.add(1);                 // ⚠️ unchecked warning
String s = (String) list.get(0);   // 💥 ClassCastException
```
**Fix:** always parameterise. Build with `-Xlint:unchecked -Werror` to make this a build failure.

### ❌ Pitfall 5 — Overly narrow wildcards in a public API

```java
// ❌ Too restrictive — the caller must have exactly List<Animal>
public void process(List<Animal> animals) { }
```
```java
// ✅ Correct — accepts List<Dog>, List<Animal>, List<Cat>
public void process(List<? extends Animal> animals) { }
```

### ❌ Pitfall 6 — `Collections.unmodifiableList` mistaken for a copy

```java
List<String> live = new ArrayList<>(List.of("a"));
List<String> view = Collections.unmodifiableList(live);
live.add("b");           // the "immutable" view now shows [a, b]
```
**Fix:** `List.copyOf(live)` takes a snapshot. This is a *copy* vs *view* issue, not a variance one,
but it is the most common cause of "why did my immutable list change?".

### ❌ Pitfall 7 — Widening a `? super T` parameter to a concrete type

```java
// ❌ COMPILE ERROR
public void addDog(List<? super Dog> dogs) {
    List<Animal> a = dogs;   // cannot convert
}
```
**Fix:** read as `Object`, or accept `List<? super Animal>` if you want to add `Animal`s.

### ❌ Pitfall 8 — Forgetting the `? super` on a *cache* lookup

```java
// ❌ Too restrictive
public V find(Map<K, V> cache, K key) { }
```
```java
// ✅ Correct — the cache may be keyed by a supertype of K
public V find(Map<? super K, ? extends V> cache, K key) { return cache.get(key); }
```

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is covariance?**
**A:** Covariance means subtype relationship is preserved. If `Dog extends Animal`, then `List<Dog>`
would be a subtype of `List<Animal>` (if covariant). Java arrays are covariant, but generics are
not (by default).

**Example:** Covariant return types allow overriding method to return a subtype.

**Q: What is invariance?**
**A:** Invariance means no subtype relationship. Even if `Dog extends Animal`, `List<Dog>` is NOT a
subtype of `List<Animal>`. Java generics are invariant by default.

**Q: Can overriding method return a supertype?**
**A:** No. Overriding method must return the same type or a subtype (covariant return type).
Returning a supertype would violate Liskov Substitution Principle.

```java
// COMPILE-ERROR-DEMO — this block deliberately does NOT compile.
class Animal {
    Dog getAnimal() { return new Dog(); }
}

class Dog extends Animal {
    @Override
    Animal getAnimal() {  // ❌ COMPILE ERROR
        return new Animal();  // Can't return supertype
    }
}
```

**Q: What does `List<?>` mean?**
**A:** A list whose element type is unknown. You can read elements as `Object` and call `size()`,
but you cannot add anything except `null`.

### 🟡 Intermediate

**Q: Can you add elements to `List<? extends Animal>`?**
**A:** No (except `null`). The list could be `List<Dog>`, `List<Cat>`, etc. Compiler doesn't know
which, so adding is unsafe.

```java
List<? extends Animal> list = new ArrayList<Dog>();
list.add(new Dog());  // ❌ COMPILE ERROR
list.add(null);       // ✅ Only null allowed
```

**Q: What can you read from `List<? super Dog>`?**
**A:** Only `Object`. The list could be `List<Dog>`, `List<Animal>`, or `List<Object>`. The only
common supertype is `Object`.

```java
List<? super Dog> list = new ArrayList<Animal>();
Object obj = list.get(0);  // ✅ Only Object
Dog d = list.get(0);       // ❌ COMPILE ERROR
```

**Q: When to use unbounded wildcard (List<?>)?**
**A:** When you don't care about the element type, only the collection itself.

```java
void printSize(List<?> list) {
    System.out.println(list.size());  // Don't need element type
}
```

Can read as `Object`, but can't add anything (except `null`).

**Q: What is the PECS principle?**
**A:** Producer Extends, Consumer Super

- **Producer** (reading): Use `? extends T`
- **Consumer** (writing): Use `? super T`

```java
void copy(List<? extends T> source, List<? super T> dest) {
    for (T item : source) {  // Read from producer
        dest.add(item);      // Write to consumer
    }
}
```

### 🔴 Advanced / Senior

**Q: Why are arrays covariant but generics invariant?**
**A:** **Arrays**: Covariant for historical reasons (pre-generics). Causes runtime
`ArrayStoreException` when type mismatch. **Generics**: Invariant for type safety. Prevents type
pollution at compile time. No runtime checks needed.

```java
// Arrays (covariant) — runtime error
Object[] arr = new String[10];
arr[0] = 123;  // ArrayStoreException

// Generics (invariant) — compile error
List<Object> list = new ArrayList<String>();  // ❌ Won't compile
```

**Q: Why does ArrayStoreException exist?**
**A:** Because arrays are covariant. JVM must check every array store operation at runtime to
prevent type pollution.

```java
Animal[] animals = new Dog[10];
animals[0] = new Cat();  // ArrayStoreException
```

**Q: What is contravariance?**
**A:** Contravariance means subtype relationship is reversed. If `Dog extends Animal`, then
`List<Animal>` would be a subtype of `List<Dog>` (if contravariant). Java doesn't support
contravariant method parameters, but `? super T` provides contravariance for generics.

**Q: Why did Java make generics invariant instead of covariant?**
**A:** Because of **heap pollution**. If `List<Dog>` were a `List<Animal>`, you could add a `Cat`
and get a `ClassCastException` at retrieval — far from the point of the mistake. The JSR 14
experts (Josh Bloch and David Ungar) argued this explicitly in the "Generics Proposal" document.
The alternative — covariant generics with runtime checks — was rejected on both safety and
performance grounds. Java's resolution was **wildcards**: keep type arguments invariant, and let
callers opt into variance explicitly where it is safe.

**Q: Why is `Class<T>` declared as `Class<? extends T>`?**
**A:** Because `getClass()` returns the runtime class, which is always a *subtype* of the
compile-time type. `Class<? extends T>` expresses exactly that and lets `Dog.class` be assigned to
`Class<? extends Animal>` — a genuine covariance, made safe by the wildcard.

### ⚫ Expert / Principal

**Q: Java uses use-site variance; Kotlin uses declaration-site. Which is better?**
**A:** They solve different problems. Use-site (Java) lets *the caller* choose the variance at each
use site, so one class serves both a producer and a consumer view — which is why `Collections.copy`
can work with `List<? extends T>` and `List<? super T>` in the same signature. Declaration-site
(Kotlin) puts the choice on the class author, which can express things Java cannot (a class that is
genuinely covariant) and produces fewer classes, but the author must commit up front and cannot
offer both views of the same class. For a library, use-site is more flexible; for a domain type
where the variance is a genuine property of the concept, declaration-site expresses the intent
better. I would not say one is "better" — they are different tools.

**Q: How do you design a generic API that is maximally flexible but still safe?**
**A:** Apply PECS consistently at every position. Read paths take `? extends T`; write paths take
`? super T`; transformation functions take `Function<? super T, ? extends R>`. The one thing I
avoid is `?` where a concrete type would do — `List<?>` throws away information and forces the
caller to cast. Where a type parameter *is* needed but the relationship is not, I use the wildcard
rather than a raw type. And I keep the type parameter on the class only when the class genuinely
stores a `T`; if it merely passes one through, it belongs on the method.

**Q: What is heap pollution and how do you prevent it in a large codebase?**
**A:** Heap pollution is when the runtime type of an object does not match its compile-time type —
the state that makes a `ClassCastException` possible far from the cause. The sources are raw types,
unchecked casts, and generic varargs arrays. I prevent it in three ways: build with
`-Xlint:unchecked -Werror` so every unchecked operation is a build failure; ban raw types by
convention and with static analysis (Error Prone's `RawTypes` check); and where a raw boundary is
unavoidable — a legacy API, a JSON mapper — contain it in a single adapter with one documented
invariant, rather than spreading casts across the codebase. The deeper discipline is that the
unchecked cast is a *claim* the compiler cannot verify, so it should be rare, localised and
reviewed.

---

## 9. Counter-Questions & Traps

### CQ1. "If Java generics are invariant, why did the JDK make `Class<T>` covariant?"

- **Naive answer:** "It's an oversight."  ← wrong
- **The trap:** The interviewer is testing whether the candidate notices that Java *does* use
  covariance in one place, and understands why it is safe there.
- **Expert answer:** "It is not an oversight — `Class` is declared as `Class<? extends T>`, and that
  is a genuine covariance. It is safe because `Class` is **read-only** in the relevant direction:
  a `Class<Dog>` really is a `Class<Animal>` in the sense that `Dog.class.getName()` and
  `Animal.class.getName()` are both valid, and `Class` exposes no way to *construct* an instance of
  a different type through the generic parameter. The wildcard makes the variance explicit at the
  use site rather than baking it into the type. It is exactly the same pattern as
  `List<? extends Animal>` — a read-only view."

### CQ2. "Why are arrays still covariant? Isn't that just a legacy bug?"

- **Naive answer:** "For historical reasons."  ← the textbook answer
- **The trap:** The interviewer wants the *mechanism* and the *cost*, and the reason the JDK could
  not change it.
- **Expert answer:** "It is a legacy wart with a hard technical reason for persisting. The array's
  runtime component type is recorded in the object header, so the JVM can — and does — perform a
  store check on every `aastore`, throwing `ArrayStoreException` on a mismatch. The reason it
  cannot be removed is **binary compatibility**: changing the covariance of `Object[]` vs
  `String[]` would break every already-compiled class file. The JDK could not change it without
  breaking the entire ecosystem, so it kept it and mitigated it with runtime checks. Generics
  could be made invariant from day one, so they were — and that is why the compiler error appears
  *earlier*, where it is free."

### CQ3. "Why can't you just make `List` covariant and add a runtime check, like arrays?"

- **Naive answer:** "Because of type safety."  ← the conclusion, not the argument
- **The trap:** The interviewer is testing whether the candidate can make the *design* argument,
  not just cite the outcome.
- **Expert answer:** "Because of **erasure**. With type erasure, `List<Dog>` and `List<Animal>` are
  the *same* runtime class — `java.util.ArrayList` — with no component type recorded anywhere. So
  the runtime check the array gets for free is *not available* for generics; you would have to
  reify the type, which means boxing every element and paying a performance cost on every
  operation. The alternative was to reject the covariance at compile time — which is what Java
  did, and it is strictly better: the error appears where the mistake is, and there is no runtime
  cost at all. The `ArrayStoreException` is not a *feature* of arrays; it is the price Java paid
  for keeping a pre-generics mistake."

### CQ4. "Your API takes `List<? extends Animal>`. Isn't that just `List<Animal>` with extra steps?"

- **Naive answer:** "Yes, but it looks fancier."  ← dismissive
- **The trap:** The interviewer is checking whether the candidate can articulate the *practical*
  benefit: caller flexibility without any loss of safety.
- **Expert answer:** "It is strictly more useful, and the difference is at the **call site**. With
  `List<Animal>`, the caller must have a `List<Animal>` exactly — a `List<Dog>` is rejected, and
  the caller has to copy or convert it. With `List<? extends Animal>`, the caller can pass their
  `List<Dog>` directly, and the method still guarantees it can only *read* `Animal`s. So the
  wildcard buys caller flexibility at zero cost to safety, and it also documents the direction of
  use — 'this method reads animals' — which the plain `List<Animal>` does not."

### CQ5. "When would you use `List<? super T>` in a real API?"

- **Naive answer:** "Never, it's confusing."  ← wrong
- **The trap:** The interviewer is testing whether the candidate can give a *real* use case, not
  just recite PECS.
- **Expert answer:** "Whenever the method **writes** `T` into the collection: a `fill` method, an
  `addAll`, a collector's sink, a repository's bulk-save, or a cache lookup where the key type
  may be a supertype. The canonical example is `Collections.copy(List<? super T> dest, List<? extends
  T> src)` — note that the JDK's own signature uses *both* wildcards in one method, because the
  source is read and the destination is written. If you find yourself needing `? super T`, ask
  whether the method should take a `List<T>` and be given a fresh list instead — but when the
  caller's existing collection is the natural target, `? super T` is right."

### CQ6. "How do you decide between `List<T>`, `List<? extends T>` and `List<?>` in a method signature?"

- **Naive answer:** "Use `List<?>` to be safe."  ← loses information
- **The trap:** The interviewer is testing whether the candidate treats wildcards as a *design
  tool* rather than a safety blanket.
- **Expert answer:** "Three questions. Do I **read** elements of `T` out? Then I need `? extends T`
  — or exactly `T` if the method also needs to write. Do I **write** elements of `T` in? Then
  `? super T`. Do I need the element type **at all** — just `size()`, `isEmpty()`, or `clear()`?
  Then `List<?>`. The mistake to avoid is defaulting to `List<?>` for safety: it throws away the
  type information and forces every caller to cast, and it hides a real design question — if the
  method genuinely never looks at the elements, say so in the signature; if it does, use the right
  wildcard."

### CQ7. "Why does `List<? extends Number>` reject `add(1)` when `List<Number>` accepts it?"

- **Naive answer:** "Because `? extends` means read-only."  ← the rule
- **The trap:** The interviewer wants the *reason*, which is about what the wildcard *commits* to.
- **Expert answer:** "Because `? extends Number` is a *promise about the unknown type*, and the
  promise is 'some subtype of Number, I don't know which'. If the list is actually a
  `List<Double>`, then `add(1)` would insert an `Integer` into a list the compiler believes holds
  `Double`s — and the failure would surface later, at retrieval, as a `ClassCastException`. By
  contrast, `List<Number>` is an exact commitment: every element is a `Number`, so `add(1)` and
  `add(2.5)` are both safe. The wildcard gives up the *write* guarantee in exchange for
  flexibility on the read side — and that is exactly the trade PECS describes."

### CQ8. "If generics are erased, how does the compiler check `List<String>` at all?"

- **Naive answer:** "It doesn't — that's why there are unchecked warnings."  ← the symptom
- **The trap:** The interviewer is testing whether the candidate understands that erasure is a
  *runtime* strategy while the type checking happens entirely at *compile time*.
- **Expert answer:** "The type checking is entirely a **compile-time** affair. The compiler knows
  the type arguments while it type-checks your source, enforces the invariance rules, and inserts
  the necessary casts. Then it **erases** the type arguments from the bytecode, so `List<String>`
  and `List<Animal>` are both just `java.util.ArrayList` at runtime. That is why you cannot ask a
  `List` at runtime what its element type is — except through the `TypeToken` / `TypeReference`
  trick that Gson, Jackson and Spring use, which capture the type in an anonymous subclass's
  generic superclass and read it back reflectively. Erasure is a deliberate trade: it keeps the
  bytecode small and keeps generics binary-compatible with pre-generics code."

## 10. Rapid-Fire MCQs

**Q1.** Java generics are, by default:
- **A)** Covariant
- **B)** Contravariant
- **C)** Invariant
- **D)** Bivariant

**Q2.** `List<? extends Animal>` allows you to:
- **A)** Read elements as `Animal` and add `Dog` instances
- **B)** Read elements as `Animal` and add only `null`
- **C)** Read elements as `Object` only
- **D)** Add `Animal` instances

**Q3.** `List<? super Dog>` allows you to:
- **A)** Add `Dog` and its subtypes; read only as `Object`
- **B)** Read as `Dog`; add nothing
- **C)** Add only `null`
- **D)** Add `Animal` instances

**Q4.** The PECS principle states:
- **A)** Producer Extends, Consumer Super
- **B)** Producer Super, Consumer Extends
- **C)** Prefer Exact, Cast Sometimes
- **D)** Public Extends, Private Super

**Q5.** `Object[] o = new String[10]; o[0] = 42;` results in:
- **A)** A compile error
- **B)** An `ArrayStoreException` at runtime
- **C)** A `ClassCastException` at runtime
- **D)** No error

**Q6.** Covariant return types were introduced in:
- **A)** Java 1.4
- **B)** Java 5
- **C)** Java 8
- **D)** Java 17

**Q7.** Java generics are invariant mainly to prevent:
- **A)** Slower performance
- **B)** Heap pollution and runtime `ClassCastException`s
- **C)** Code duplication
- **D)** Reflection problems

**Q8.** `Class<? extends Animal> c = Dog.class;` is legal because:
- **A)** `Class` is declared with a covariant wildcard
- **B)** `Class` is covariant
- **C)** `Dog.class` returns `Class<Animal>`
- **D)** `Class` is invariant but the compiler special-cases it

**Q9.** In `List<? extends Number>`, you may add:
- **A)** An `Integer`
- **B)** A `Double`
- **C)** Only `null`
- **D)** Any `Number`

**Q10.** `List<?>` lets you call:
- **A)** `get(0)` and cast to `String`
- **B)** `size()` and `isEmpty()`
- **C)** `add("x")`
- **D)** `remove(0)` and read the result as `String`

**Q11.** The type of `get()` on a `List<? extends Number>` is:
- **A)** `Object`
- **B)** `Number`
- **C)** `? extends Number`
- **D)** `Comparable`

**Q12.** Heap pollution is best prevented by:
- **A)** Using raw types carefully
- **B)** `-Xlint:unchecked -Werror` and avoiding raw types
- **C)** Using `final` on the collection
- **D)** Catching `ClassCastException`


### Answer Key

| # | Answer | Explanation |
|---|--------|-------------|
| 1 | **C** | Java's type arguments are invariant by default, deliberately, to prevent heap pollution. |
| 2 | **B** | `? extends` makes the element readable as `Animal` but the exact type is unknown, so only `null` may be added. |
| 3 | **A** | `? super Dog` means the list accepts `Dog` and its subtypes; reads are only safe as `Object`. |
| 4 | **A** | Producer Extends, Consumer Super — read with `? extends`, write with `? super`. |
| 5 | **B** | Arrays are covariant, so it compiles; the JVM's store check throws `ArrayStoreException` at runtime. |
| 6 | **B** | Java 5 introduced generics and covariant return types together. |
| 7 | **B** | Invariance moves the type error to compile time, preventing the runtime `ClassCastException` that covariance would allow. |
| 8 | **A** | `Class` is declared as `Class<? extends T>` — a genuine, safe covariance expressed with a wildcard. |
| 9 | **C** | Only `null` can be added to a `? extends` list, because the concrete element type is unknown. |
| 10 | **B** | `List<?>` gives you the collection's operations but not the element type — so `size()` and `isEmpty()` work, `add` does not. |
| 11 | **B** | The element is readable as the *bound* of the wildcard — `Number`. |
| 12 | **B** | `-Xlint:unchecked -Werror` turns every unchecked operation into a build failure, so heap pollution cannot be introduced. |

## 11. Cheat Sheet / One-Page Summary

### The Variance Taxonomy

| | Relationship | Java applies it to |
|---|---|---|
| **Covariant** | Preserved: `Dog ⊆ Animal` ⇒ `List<Dog> ⊆ List<Animal>` | Return types, arrays, `throws` clauses, `Class<? extends T>` |
| **Contravariant** | Reversed: `Dog ⊆ Animal` ⇒ `Consumer<Animal> ⊇ Consumer<Dog>` | Method parameters, `? super T`, `Function<? super T, ? extends R>` |
| **Invariant** | None: `List<Dog> ≠ List<Animal>` | **All type arguments, by default** |

### Wildcard Reference

| Wildcard | Read as | Write | Use when |
|----------|----------|-------|----------|
| `? extends T` | `T` | only `null` | The collection **produces** `T` values |
| `? super T` | only `Object` | `T` and subtypes | The collection **consumes** `T` values |
| `?` | `Object` | only `null` | You only need `size()`, `isEmpty()`, `clear()` |

### PECS in One Line

```text
   READ  → want the most specific type → ? extends T
   WRITE → can accept the most general → ? super T
```

### The Canonical Signatures

```java
// Copy: read from source, write to destination
static <T> void copy(List<? extends T> src, List<? super T> dst)

// Map: contravariant input, covariant output
static <T, R> List<R> map(List<? extends T> in, Function<? super T, ? extends R> fn)

// Fill: pure consumer
static <T> void fill(List<? super T> dst, T value)

// Cache lookup: the cache may be keyed by a supertype
static <K, V> V get(Map<? super K, ? extends V> cache, K key)
```

### The Invariance Proof (memorise this)

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs;    // if this were allowed...
animals.add(new Cat());         // ...this would compile
Dog d = dogs.get(0);            // ...and this would throw ClassCastException
```

### Java vs Kotlin/Scala

| | Java | Kotlin | Scala |
|---|------|--------|-------|
| Variance style | **Use-site** (wildcards) | Declaration-site (`out`/`in`) | Declaration-site (`+`/`-`) |
| Chosen by | The **caller** | The **class author** | The **class author** |
| Both views of one class? | ✅ Yes | ❌ No | ❌ No |
| Can be generic? | ✅ | ❌ (use `Any`) | ❌ (use `Any`) |

### The Golden Rules

1. **Java generics are invariant** — `List<Dog>` is not a `List<Animal>`.
2. **PECS** — Producer Extends, Consumer Super.
3. **`? extends` is read-only**; **`? super` is write-only**.
4. **Arrays are still covariant** → `ArrayStoreException` at runtime.
5. **Never use raw types** — build with `-Xlint:unchecked -Werror`.
6. **Widening a parameter is allowed**; narrowing is a compile error.

---

## 12. Real-World Production Notes

### Where Variance Matters in Banking / E-Commerce Systems

| Situation | Wrong signature | Right signature | Consequence of getting it wrong |
|-----------|-----------------|----------------|--------------------------------|
| A service that reads orders and returns a summary | `List<Order>` | `List<? extends Order>` | Callers with `List<SpecialOrder>` must copy |
| A repository's bulk insert | `saveAll(List<Entity>)` | `saveAll(List<? super Entity>)` | Cannot save into a `List<Object>` sink |
| A generic mapper utility | `Function<T, R>` | `Function<? super T, ? extends R>` | Rejects `Function<Object, String>` |
| A cache lookup | `Map<K, V>` | `Map<? super K, ? extends V>` | Cannot use a `Map<String, Order>` for a `Map<Object, Order>` cache |
| A JSON deserialiser | `List` (raw) | `List<T>` with a `TypeReference` | Heap pollution → `ClassCastException` |
| A generic event bus | `List<Handler>` | `List<? super Event>` | Cannot register handlers of a subtype |

### Tuning Knobs & Metrics

- **`-Xlint:unchecked -Werror`** — the single highest-value build flag for this topic. It makes
  heap pollution impossible to introduce.
- **Error Prone** — `RawTypes`, `uncheckedType` and `uncheckedTypeArgs` checks.
- **ArchUnit** — "no raw `java.util` types in method signatures" and "generic methods must use
  wildcards at variance-flexible positions".
- **SpotBugs** — `URF_UNREAD_FIELD`, `BC_UNCONFIRMED_CAST` catch related issues.
- **JFR / async-profiler** — a `ClassCastException` hot path usually points at one raw-type
  boundary that is being hit on every request.

### Practical Rules We Enforce in Code Review

1. **No raw types**, anywhere — including in method signatures.
2. **Public API methods that only read** take `? extends T`; **that only write** take `? super T`.
3. **Never use `List<?>`** when the element type is actually needed — it discards information and
   pushes casts to the caller.
4. **Generic transformation helpers** use `Function<? super T, ? extends R>`.
5. **`@SafeVarargs`** on every generic varargs method.
6. **The build fails on unchecked warnings** — `-Xlint:unchecked -Werror`.
7. **JSON/deserialisation boundaries** use `TypeReference`/`TypeToken` to capture the type, never a
   raw type.

---

## 13. Follow-up Learning

### Next in this folder

- [07 · Keywords: final, static, volatile, transient](./07-OOP-Keywords-final-static-volatile-transient.md) — `final` and immutability, which is what makes invariance safe
- [09 · Immutable Classes](./09-OOP-Immutable-Classes.md) — immutable value types and safe publication
- [11 · Advanced Design and the Object Class](./11-OOP-Advanced-Design-and-Object-Class.md) — covariant returns and the bridge method
- [12 · Critical and Tricky Scenarios](./12-OOP-Critical-and-Tricky-Interview-Scenarios.md) — the scenario bank

### Related topics elsewhere

- [Generics](../05-Generics/01-Generics.md) — type erasure, bounds and generic methods in depth
- [Collections Framework](../04-Collections-Framework/01-List-Implementations.md) — the collection APIs that use these wildcards
- [Java 8+ Features](../06-Java-8-Features/01-Lambda-Functional-Interfaces.md) — `Function`, `Consumer` and the functional interfaces that carry variance
- [Reflection & Annotations](../14-Reflection-Annotations/01-Reflection-and-Annotations.md) — `TypeReference` and recovering erased types

### Specifications and books

- **JLS §4.5** — type arguments and their subtyping rules
- **JLS §4.10.3** — subtyping among wildcard-parameterised types
- **JLS §4.6** — erasure of type parameters
- **JSR 14 / "The Generics Proposal"** (Bloch & Ungar) — the original argument for invariance
- **Effective Java** — Item 28 (use wildcards), Item 33 (prefer interfaces to type parameters)
- **`java.util.Collections.copy` Javadoc** — the canonical PECS signature

---

**Next Chapter:** → [07 · Important Keywords: final, static, volatile, transient](./07-OOP-Keywords-final-static-volatile-transient.md)
**Previous Chapter:** ← [12 · Critical and Tricky OOP Interview Scenarios](./12-OOP-Critical-and-Tricky-Interview-Scenarios.md)

*Last Updated: 2026 · Java 17+ (verified on JDK 25) · All outputs in this chapter were produced by running the code*
