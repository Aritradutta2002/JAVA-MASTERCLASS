## 📋 Table of Contents

- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — The Design Toolkit](#2-mental-model--the-design-toolkit)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 Composition vs Inheritance](#31-composition-vs-inheritance)
  - [3.2 Covariant Return Types](#32-covariant-return-types)
  - [3.3 Method Hiding vs Overriding](#33-method-hiding-vs-overriding)
  - [3.4 The Object Class Methods](#34-the-object-class-methods)
  - [3.5 Cloning in Java](#35-cloning-in-java)
  - [3.6 Serialization](#36-serialization)
  - [3.7 Association, Aggregation, Composition](#37-association-aggregation-composition)
  - [3.8 SOLID in OOP Context](#38-solid-in-oop-context)
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

- **Prefer composition** when the relationship is "has-a" or might change at runtime. Inheritance is
  for a genuine "is-a" with a stable base — and it creates the *fragile base class* problem.
- **`Stack extends ArrayList` is a design error** — it exposes `add`, `remove` and `sort` on
  something that must be LIFO. Compose a `Deque` and expose only the stack operations.
- **Covariant return types** (Java 5+) let an override return a subtype — `Dog reproduce()` where
  the parent returns `Animal`. The compiler generates a **bridge method** for binary compatibility.
- **Static methods are hidden, not overridden** — they are bound at compile time by the
  *reference* type, and there is no runtime dispatch.
- **Almost every method in `Object` is `final`, `native` or `protected`** — `getClass`, `wait`,
  `notify`, `notifyAll` are `final`; `hashCode` and `clone` are `native`; `clone` is `protected`;
  `finalize` is deprecated.
- **`clone()` is protected, shallow, and skips the constructor** — use a **copy constructor** or
  copy factory instead.
- **Java serialization is a security risk** — `readObject` is a remote code execution vector. Use
  `ObjectInputFilter` (Java 9+) and prefer JSON/Protobuf for new systems.
- **`serialVersionUID` is your compatibility contract** — change it and deserialisation throws
  `InvalidClassException`.
- **Association, aggregation and composition differ by *lifetime*** — not by the UML arrow.

---

## 1. What & Why — The Problem This Solves

### The problem

At the intermediate level, OOP is about *using* classes. At the senior level it is about
**choosing** between them — and the choices have long-term costs that only appear months later.

The classic failure: a team inherits a base class, the base class changes, and a subclass that
nobody knew existed breaks in production. That is the **fragile base class problem**, and it is
the reason "favour composition over inheritance" is in every senior interview.

This chapter also covers the `java.lang.Object` API — the twelve methods every class inherits —
because those contracts are the ones you are most likely to violate by accident.

### The solution

Learn three decision tools:

1. **IS-A or HAS-A?** → inheritance or composition.
2. **Can the base change?** → if yes, you have a fragile base class.
3. **What is the contract?** → `Object`'s methods, `Serializable`'s contract, LSP.

### The analogy

**Inheritance is a *deep clone*; composition is a *composition*.** When you inherit, you are
taking on a permanent, invisible dependency: every change to the base is a change to you. When you
compose, you hold a reference — and you can swap the part without touching the whole.

---

## 2. Mental Model — The Design Toolkit

```text
┌──────────────────────────────────────────────────────────────────────┐
│              "I need to relate these two types."                     │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
              ┌───────────────────────────────┐
              │ Is B a genuine "IS-A" of A?    │
              │ (B can be used wherever A is  │
              │  expected, without breaking  │
              │  A's promises)               │
              └───────┬───────────────┬───────┘
                  YES │               │ NO
                      │               │
                      ▼               ▼
         ┌────────────────────┐   ┌──────────────────────────┐
         │  INHERITANCE        │   │  COMPOSITION              │
         │  "B IS-A A"         │   │  "A HAS-A B"              │
         │                    │   │                          │
         │  • tight coupling   │   │  • loose coupling        │
         │  • code reuse       │   │  • runtime flexibility   │
         │  • ONE superclass  │   │  • swap the collaborator │
         │  • fragile base     │   │  • more delegation code  │
         └────────────────────┘   └──────────────────────────┘
```

### The Three Object Lifetimes

```text
   ASSOCIATION      "uses"              →  both live independently
   AGGREGATION      "has-a", whole-part →  parts can exist alone
   COMPOSITION      "contains"          →  parts die with the whole
```

### The `Object` Contract Surface

```text
   ┌─ public, overridable ──────────────────────────────┐
   │  toString()   equals(Object)   hashCode()          │
   │  → you may override these; the contract is strict  │
   └────────────────────────────────────────────────────┘
   ┌─ public FINAL — cannot be overridden ──────────────┐
   │  getClass()   wait()   notify()   notifyAll()      │
   │  → the JVM's own machinery; do not fight it        │
   └────────────────────────────────────────────────────┘
   ┌─ protected — special access ───────────────────────┐
   │  clone()      finalize()  (deprecated)             │
   │  → clone is shallow and skips the constructor      │
   └────────────────────────────────────────────────────┘
   ┌─ native ───────────────────────────────────────────┐
   │  hashCode()   clone()   wait()   notify()          │
   │  → implemented in C/C++ via JNI                    │
   └────────────────────────────────────────────────────┘
```

---

## 3. Deep Dive — Theory

### 3.1 Composition vs Inheritance

#### The Problem with Inheritance

```java
// ❌ Fragile Base Class Problem
class Bird {
    void fly() {
        System.out.println("Flying");
    }
}

class Penguin extends Bird {
    // Penguin can't fly! But inherits fly()
}

// ❌ Tight coupling
class Stack extends ArrayList {  // Stack IS-A ArrayList? No!
    // Inherits all ArrayList methods - wrong semantics
}
```

#### Composition - Better Approach

```java
// ✅ Composition over Inheritance
class Stack {
    private List<Object> list = new ArrayList<>();  // HAS-A relationship
    
    public void push(Object item) {
        list.add(item);
    }
    
    public Object pop() {
        if (list.isEmpty()) throw new EmptyStackException();
        return list.remove(list.size() - 1);
    }
    
    // Only expose stack operations, not all List methods
}
```

#### When to Use What

| Use Inheritance When | Use Composition When |
|---------------------|---------------------|
| IS-A relationship | HAS-A relationship |
| Code reuse in related classes | Code reuse across unrelated classes |
| Polymorphism needed | Flexibility needed |
| Base class is stable | Behavior may change at runtime |

#### Liskov Substitution Principle

```java
// ✅ Valid inheritance
class Rectangle {
    protected int width, height;
    
    public void setWidth(int w) { this.width = w; }
    public void setHeight(int h) { this.height = h; }
    public int getArea() { return width * height; }
}

// ❌ Violates LSP
class Square extends Rectangle {
    @Override
    public void setWidth(int w) {
        width = height = w;  // Breaks Rectangle contract!
    }
    
    @Override
    public void setHeight(int h) {
        width = height = h;  // Breaks Rectangle contract!
    }
}

// Test
Rectangle r = new Square();
r.setWidth(5);
r.setHeight(10);
System.out.println(r.getArea());  // Expected: 50, Got: 100!
```

**The subtle point about the fragile base class:** it is not only about *adding* methods. If the
base class changes the *implementation* of a method that a subclass also overrides, or if it adds
a new method whose name collides with a subclass method, the subclass silently changes behaviour.
This is why `protected` members are the biggest liability in a base class — they are part of the
subclass's *implicit* contract.

---

### 3.2 Covariant Return Types

#### Definition

Overridden method can return a subtype of the parent's return type.

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

// Usage
Animal animal = new Dog();
Dog puppy = (Dog) animal.reproduce();  // No cast needed in Java 5+
```

#### Benefits

- More specific return types
- Eliminates casting
- Better type safety

```java
// Before Java 5
class Parent {
    Object get() { return new Object(); }
}

class Child extends Parent {
    Object get() { return new String("Hello"); }  // Return Object
}

// Usage required casting
String s = (String) new Child().get();

// After Java 5 (Covariant)
class Parent {
    Object get() { return new Object(); }
}

class Child extends Parent {
    @Override
    String get() { return "Hello"; }  // Return specific type
}

// No casting needed
String s = new Child().get();
```
### 3.3 Method Hiding vs Overriding

#### Method Overriding (Instance Methods)

```java
class Parent {
    void show() {  // Instance method
        System.out.println("Parent");
    }
}

class Child extends Parent {
    @Override
    void show() {  // Overriding
        System.out.println("Child");
    }
}

// Runtime polymorphism
Parent p = new Child();
p.show();  // Output: "Child" (runtime binding)
```

#### Method Hiding (Static Methods)

```java
class Parent {
    static void show() {  // Static method
        System.out.println("Parent");
    }
}

class Child extends Parent {
    static void show() {  // Hiding, not overriding
        System.out.println("Child");
    }
}

// Compile-time binding
Parent p = new Child();
p.show();  // Output: "Parent" (compile-time binding)

Child c = new Child();
c.show();  // Output: "Child"
```

#### Key Differences

| Aspect | Overriding | Hiding |
|--------|-----------|--------|
| Methods | Instance methods | Static methods |
| Binding | Runtime (dynamic) | Compile-time (static) |
| Polymorphism | Yes | No |
| Annotation | @Override | Not applicable |
| Resolution | Based on object type | Based on reference type |

> **Note:** `@Override` on a static method is a **compile error** — the compiler catches this one
> for you, which is a useful sanity check.

---

### 3.4 The Object Class Methods

#### All Methods

```java
// The java.lang.Object API (reference listing — the real class lives in the JDK).
// Shown as an interface so the method signatures are visible and compilable.
public interface ObjectApi {
    // 1. toString()
    String toString();
    
    // 2. equals()
    boolean equals(Object obj);
    
    // 3. hashCode()
    int hashCode();
    
    // 4. getClass()  — ⚠ FINAL in Object, so it can only be *listed*, not overridden
    //    Class<?> getClass();
    
    // 5. clone()
    Object clone() throws CloneNotSupportedException;
    
    // 6. finalize()  — deprecated since 9, removed for removal in 18
    void finalize() throws Throwable;
    
    // 7. wait()  — ⚠ FINAL in Object
    //    void wait() throws InterruptedException;
    //    void wait(long timeout) throws InterruptedException;
    //    void wait(long timeout, int nanos) throws InterruptedException;
    
    // 8. notify()  — ⚠ FINAL in Object
    //    void notify();
    
    // 9. notifyAll()  — ⚠ FINAL in Object
    //    void notifyAll();
}
```

> In the real `java.lang.Object`, `getClass()`, `wait()`, `notify()` and `notifyAll()` are
> **`final`**, `hashCode()` and `clone()` are **`native`**, and `clone()` is **`protected`**.
> See the table below for the full modifier picture.

#### The `Object` Contract Summary

| Method | Overridable? | Contract |
|--------|--------------|----------|
| `toString()` | ✅ | A human-readable representation. No formal contract, but never leak secrets |
| `equals(Object)` | ✅ | Reflexive, symmetric, transitive, consistent, null-safe; must agree with `hashCode()` |
| `hashCode()` | ✅ | Equal objects → equal hash codes; use the same fields as `equals()` |
| `getClass()` | ❌ `final` | The exact runtime class. `final` so it cannot lie |
| `clone()` | ⚠️ `protected` | Shallow copy, skips the constructor; requires `Cloneable` |
| `finalize()` | ⚠️ deprecated | Unpredictable; deprecated since 9, removed for removal in 18 |
| `wait()` / `notify()` / `notifyAll()` | ❌ `final` | The JVM's monitor API. `wait` releases the monitor |

**Why `getClass()`, `wait()`, `notify()` and `notifyAll()` are `final`:** they are the JVM's own
machinery. If a subclass could lie about its runtime class, or interfere with monitor
notification, the JVM's invariants would break. Making them `final` is a guarantee to the runtime,
not to you.

#### equals() and hashCode() Contract

```java
class Person {
    private String name;
    private int age;
    
    // Must override both together
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return age == person.age && Objects.equals(name, person.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, age);
    }
}

// Contract:
// 1. If equals() returns true, hashCode() must be same
// 2. If hashCode() different, equals() must return false
// 3. hashCode() should be consistent
```

#### toString() Best Practice

```java
class Employee {
    private int id;
    private String name;
    
    @Override
    public String toString() {
        return "Employee{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}

// Usage
Employee e = new Employee(1, "John");
System.out.println(e);  // Employee{id=1, name='John'}
```

---

### 3.5 Cloning in Java

#### Shallow Copy vs Deep Copy

```java
// Shallow Copy - copies references
class Shallow implements Cloneable {
    int[] data;
    
    public Shallow clone() throws CloneNotSupportedException {
        return (Shallow) super.clone();  // Shallow copy
    }
}

// Deep Copy - creates new objects
class Deep implements Cloneable {
    int[] data;
    
    public Deep clone() throws CloneNotSupportedException {
        Deep copy = (Deep) super.clone();  // Deep copy
        copy.data = this.data.clone();  // Deep copy
        return copy;
    }
}

// Test
Shallow s1 = new Shallow();
s1.data = new int[]{1, 2, 3};
Shallow s2 = s1.clone();
s2.data[0] = 100;
System.out.println(s1.data[0]);  // 100! (shared reference)
```

#### Copy Constructor Alternative

```java
class Person {
    private String name;
    private List<String> hobbies;
    
    // Copy constructor
    public Person(Person other) {
        this.name = other.name;
        this.hobbies = new ArrayList<>(other.hobbies);  // Deep copy
    }
}
```

#### Why `clone()` Is Discouraged (Effective Java Item 13)

1. **It is `protected`** — you cannot call `other.clone()` from outside the package unless you
   override it to widen the access.
2. **It returns `Object`** — every call needs a cast, which defeats the type safety of generics.
3. **It is shallow** — a "clone" that shares mutable state is not a clone.
4. **It bypasses the constructor** — the copy never passes through validation, so you can create an
   object the constructor would have rejected.
5. **`Cloneable` is a marker interface with no methods** — it exists purely to flip the behaviour
   of a native method. Implement it wrongly (override `clone()` without implementing `Cloneable`)
   and `super.clone()` throws `CloneNotSupportedException`.
6. **It is fragile with inheritance** — a subclass's `clone()` must call `super.clone()` and then
   fix up its own fields, or the copy is of the wrong type.

**Prefer a copy constructor or a static copy factory.**

---

### 3.6 Serialization

#### Basic Serialization

```java
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

// Serializable class
public class Employee implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String name;
    private transient String password;  // Won't be serialized
    private static String company;      // Won't be serialized
    
    public Employee(String name, String password) {
        this.name = name;
        this.password = password;
    }
    
    // Custom serialization
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        // Encrypt password before saving
        out.writeObject(encrypt(password));
    }
    
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // Decrypt password after reading
        password = decrypt((String) in.readObject());
    }
    
    private static String encrypt(String s) { return "enc(" + s + ")"; }
    private static String decrypt(String s) { return s.substring(4, s.length() - 1); }
    
    public static void main(String[] args) throws Exception {
        // Serialize
        Employee emp = new Employee("John", "secret");
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("emp.ser"))) {
            oos.writeObject(emp);
        }
        
        // Deserialize
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("emp.ser"))) {
            Employee restored = (Employee) ois.readObject();
            System.out.println("name = " + restored.name + ", password = " + restored.password);
        }
    }
}
```

#### Externalizable Interface

```java
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class Employee implements Externalizable {
    private String name;
    private int age;
    
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(name);
        out.writeInt(age);
    }
    
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        name = (String) in.readObject();
        age = in.readInt();
    }
}
```

#### `Serializable` vs `Externalizable`

| Aspect | `Serializable` | `Externalizable` |
|--------|----------------|------------------|
| Constructor needed | No (bypassed) | **Yes — a public no-arg constructor is required** |
| Control over the format | Limited (`defaultWriteObject`) | **Total** — you write every field yourself |
| Size | Larger (default format) | Usually smaller |
| Versioning | `serialVersionUID` | Your own format |
| Backward compatibility | Good | **Your responsibility** |
| Security surface | `readObject` is a known RCE vector | Smaller surface, but still deserialisation |
#### The Security Dimension (this is what a modern senior must say)

Native Java deserialisation is a **remote code execution** vector. `readObject()` runs
attacker-controlled code before you ever get your object back. This is CVE-2015-4852 (WebLogic),
Apache Commons Collections, and many others.

**Mitigation (Java 9+):** an `ObjectInputFilter` that allow-lists what may be deserialised:

```java
ObjectInputStream ois = new ObjectInputStream(in);
ois.setObjectInputFilter(info -> {
    if (info.serialClass() != null && !ALLOWED.contains(info.serialClass().getName())) {
        throw new InvalidClassException("blocked", info.serialClass().getName());
    }
    return ObjectInputFilter.Status.ALLOWED;
});
```

**Professional advice:** for new systems, do **not** use native Java serialization. Use JSON
(Jackson), Protocol Buffers, or Avro — they are faster, smaller, language-agnostic, and do not have
this attack surface. Keep `Serializable` only where you must interoperate with an existing
protocol, and always apply a filter.

#### `serialVersionUID` and Compatibility

| Change | Result |
|--------|--------|
| Class unchanged | ✅ deserialises fine |
| Field **added** | ✅ fine — the new field gets its default value |
| Field **removed** | ✅ fine — the value is discarded |
| Field type changed | ⚠️ `InvalidClassException` (unless compatible) |
| Class renamed | ⚠️ `InvalidClassException` (the name is part of the stream) |
| `serialVersionUID` changed | ⚠️ `InvalidClassException` — **deliberate incompatibility** |
| Class hierarchy changed | ⚠️ usually `InvalidClassException` |

`serialVersionUID` is a `long` you declare explicitly. **Always declare it** — otherwise the
compiler generates one from the class's structure, and *any* change to the class (even adding a
method) changes the generated UID and breaks deserialisation of previously stored data.

---

### 3.7 Association, Aggregation, Composition

#### Association (Has-A, weakest)

```java
// Student and Teacher are associated
class Student {
    private Teacher teacher;  // Can have different teachers
    
    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }
}

class Teacher {
    // Teacher exists independently
}
```

#### Aggregation (Has-A, whole-part, independent)

```java
// Department HAS Professors
// Professors can exist without Department
class Department {
    private List<Professor> professors;  // Aggregation
    
    public Department(List<Professor> professors) {
        this.professors = professors;  // Reference passed in
    }
}

class Professor {
    // Can exist without Department
}
```

#### Composition (Has-A, whole-part, dependent)

```java
// House HAS Rooms
// Rooms cannot exist without House
class House {
    private List<Room> rooms;  // Composition
    
    public House() {
        this.rooms = new ArrayList<>();
        rooms.add(new Room());
        rooms.add(new Room());
    }
}

class Room {
    // Cannot exist without House
}
```

#### Comparison

| Type | Relationship | Lifetime | Example |
|------|-------------|----------|---------|
| Association | Uses | Independent | Doctor - Patient |
| Aggregation | Has-A | Independent | University - Professor |
| Composition | Contains | Dependent | Car - Engine |

> **A note on UML:** the hollow diamond (aggregation) vs filled diamond (composition) distinction is
> widely drawn but weakly standardised and often misapplied. The **lifetime** semantics — "can the
> part exist without the whole?" — is the test that actually matters in code.

---

### 3.8 SOLID in OOP Context

> **📌 For full SOLID coverage with detailed examples, see [10-SOLID-Principles/](../10-SOLID-Principles/)**

Quick OOP-focused summary:
- **S - Single Responsibility:** One class, one reason to change
- **O - Open/Closed:** Open for extension, closed for modification (use interfaces)
- **L - Liskov Substitution:** Subtypes must be substitutable for parent types
- **I - Interface Segregation:** Prefer small, specific interfaces over fat ones
- **D - Dependency Inversion:** Depend on abstractions, not concrete implementations

---

## 4. Visualizations

### 4.1 The Fragile Base Class Problem

```text
   ❌ INHERITANCE (fragile)                  ✅ COMPOSITION (robust)
   ┌──────────────────────┐                  ┌──────────────────────┐
   │        ArrayList      │                  │         Stack         │
   │  add, remove, sort…  │                  │  push, pop, peek     │
   ├──────────────────────┤                  ├──────────────────────┤
   │        Stack          │  ← inherits ALL │  - Deque delegate    │
   │  (exposes add()!)     │    of it         │  (private field)     │
   └──────────────────────┘                  └──────────┬───────────┘
                                                    │
   A caller can do:                        ┌──────────▼───────────┐
       stack.add(x);   // ❌ not LIFO!      │        Deque          │
       stack.sort();   // ❌ meaningless    │  (only stack ops used)│
                                          └──────────────────────┘
   Change ArrayList → breaks Stack silently.
   Change Stack   → safe, nobody depends on it.
```

### 4.2 Object Lifetime Relationships

```text
   ASSOCIATION          AGGREGATION              COMPOSITION
   "uses"               "has-a" (whole-part)     "contains"
   ──────────           ──────────────────       ─────────────────
   ┌────────┐           ┌────────┐               ┌────────┐
   │Student │           │Department│             │ House  │
   └───┬────┘           └────┬────┘             └───┬────┘
       │ 1                many │ 1                   │ 1
       ▼                    ▼                      ▼
   ┌────────┐           ┌───────────┐            ┌────────┐
   │Teacher │           │ Professor │            │  Room  │
   └────────┘           └───────────┘            └────────┘
   Both live             Professors can           Rooms die
   independently.        live without the         with the
                         Department.              House.
```

### 4.3 Method Hiding vs Overriding — Bytecode

```text
   OVERRIDING (instance)                    HIDING (static)
   ─────────────────────                    ────────────────
   Parent p = new Child();                  Parent p = new Child();
   p.show();                                p.show();
        │                                        │
        ▼                                        ▼
   invokevirtual Parent.show()              invokestatic Parent.show()
        │                                        │
        ▼                                        ▼
   JVM looks at receiver's                    Bound to the REFERENCE
   ACTUAL class → Child.show()               type at COMPILE time
        │                                        │
        ▼                                        ▼
   prints "Child"                            prints "Parent"

   p = new Child()  →  p.show()  →  invokevirtual → "Child"
   p.show()        →  invokestatic → "Parent"
   new Child().show() → invokestatic (Child) → "Child"
```

### 4.4 The `Object` API Surface

```text
   ┌─────────────────────────────────────────────────────────────┐
   │  java.lang.Object                                          │
   ├─────────────────────────────────────────────────────────────┤
   │  PUBLIC & OVERRIDABLE                                      │
   │    boolean equals(Object)     ── the value contract        │
   │    int     hashCode()         ── must agree with equals   │
   │    String  toString()         ── the human contract       │
   ├─────────────────────────────────────────────────────────────┤
   │  PUBLIC & FINAL  (the JVM's machinery — do not fight it)   │
   │    Class<?> getClass()        ── the exact runtime class   │
   │    void wait() / wait(long) / wait(long,int)               │
   │    void notify() / notifyAll() ── monitor protocol         │
   ├─────────────────────────────────────────────────────────────┤
   │  PROTECTED                                                │
   │    Object clone()             ── shallow, skips ctor      │
   │    void finalize()            ── DEPRECATED since 9        │
   ├─────────────────────────────────────────────────────────────┤
   │  NATIVE (implemented in C/C++ via JNI)                     │
   │    hashCode(), clone(), getClass(), wait(), notify()…       │
   └─────────────────────────────────────────────────────────────┘
```

### 4.5 The Covariant Return Bridge Method

```text
   SOURCE
   ──────
   class Parent { Object get() { return new Object(); } }
   class Child extends Parent { String get() { return "Hello"; } }

   BYTECODE IN Child
   ────────────────
   public java.lang.String get();          ← your method
   public java.lang.Object get();          ← SYNTHETIC BRIDGE (compiler-generated)
     └─ invokevirtual Child.get()Ljava/lang/String;
        return

   WHY: a client compiled against the OLD Parent binary calls
        get()Ljava/lang/Object; on a Child instance. The bridge
        makes that call still link. Without it → NoSuchMethodError.
```

### 4.6 The `Object` Lifecycle — Construction Order

```text
   class Parent { int p = 1;  Parent() { System.out.println("P ctor"); } }
   class Child extends Parent { int c = 2;  Child() { super(); System.out.println("C ctor"); } }

   new Child():
      1. allocate Child, fields defaulted (p=0, c=0)
      2. super()  ──►  Parent():  p=1, print "P ctor"
      3. Child's field initialiser:  c=2
      4. Child() body:  print "C ctor"

   ⚠️ If Parent's constructor called an overridable method, the CHILD's
      override would run — before c=2 was assigned. (See chapter 12.)
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — LSP Violation, Covariant Return, Hiding and Cloning

**Problem:** *"Show me each of these four behaviours."*

```java
import java.util.*;

public class D5 {

    static class Rectangle {
        protected int width, height;
        public void setWidth(int w) { this.width = w; }
        public void setHeight(int h) { this.height = h; }
        public int getArea() { return width * height; }
    }

    static class Square extends Rectangle {
        @Override public void setWidth(int w)  { width = height = w; }
        @Override public void setHeight(int h) { width = height = h; }
    }

    static class Parent { Object get() { return new Object(); } }
    static class Child extends Parent { @Override String get() { return "Hello"; } }

    static class P { static void show() { System.out.println("Parent"); } }
    static class C extends P { static void show() { System.out.println("Child"); } }

    static class Shallow implements Cloneable {
        int[] data;
        @Override public Shallow clone() throws CloneNotSupportedException { return (Shallow) super.clone(); }
    }

    static class Person {
        final String name; final List<String> hobbies;
        Person(String name, List<String> hobbies) { this.name = name; this.hobbies = hobbies; }
        Person(Person other) { this.name = other.name; this.hobbies = new ArrayList<>(other.hobbies); }
    }

    public static void main(String[] args) throws Exception {
        Rectangle r = new Square();
        r.setWidth(5); r.setHeight(10);
        System.out.println("Square-as-Rectangle area = " + r.getArea() + " (expected 50 if LSP held)");

        System.out.println("covariant return = " + new Child().get());

        P p = new C();
        p.show();
        new C().show();

        Shallow s1 = new Shallow();
        s1.data = new int[]{1, 2, 3};
        Shallow s2 = s1.clone();
        s2.data[0] = 100;
        System.out.println("after shallow clone, original data[0] = " + s1.data[0]);

        Person p1 = new Person("John", new ArrayList<>(List.of("Chess")));
        Person p2 = new Person(p1);
        p2.hobbies.add("Gaming");
        System.out.println("copy ctor deep copy: p1=" + p1.hobbies + " p2=" + p2.hobbies);
    }
}
```

**Actual output (compiled and run on JDK 25):**

```text
Square-as-Rectangle area = 100 (expected 50 if LSP held)
covariant return = Hello
Parent
Child
after shallow clone, original data[0] = 100
copy ctor deep copy: p1=[Chess] p2=[Chess, Gaming]
```

**Dry run:**

| # | Statement | Result | Why |
|---|-----------|--------|-----|
| 1 | `r.setWidth(5)` | `width=5, height=5` | `Square.setWidth` sets **both** sides |
| 2 | `r.setHeight(10)` | `width=10, height=10` | `Square.setHeight` also sets both → `width` is clobbered |
| 3 | `r.getArea()` | `100` (not 50) | The caller asked for width 5, height 10 — LSP is broken |
| 4 | `new Child().get()` | `Hello` | The covariant override returns a `String`; no cast needed |
| 5 | `p.show()` where `p` is `Parent` | `Parent` | A **static** method binds to the *reference* type → `invokestatic` |
| 6 | `new C().show()` | `Child` | Here the reference type is `C`, so `C.show()` is selected |
| 7 | `s2.data[0] = 100` | `s1.data[0] == 100` | `clone()` is **shallow** — the `data` array is shared |
| 8 | `p2.hobbies.add("Gaming")` | `p1=[Chess]`, `p2=[Chess, Gaming]` | The copy constructor made a **new** `ArrayList` — a deep copy |

**Takeaway:** One program demonstrates four independent rules — LSP violation, covariant returns,
compile-time static binding, and shallow vs deep copying.

---

### Example 2 — The `Object` API and What You Can Override

**Problem:** *"Which `Object` methods can I override, and what happens if I try to override the rest?"*

```java
public class ObjectApiDemo {

    static class Thing {
        private final String id;
        Thing(String id) { this.id = id; }

        @Override public String toString() { return "Thing{" + id + "}"; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return id.equals(((Thing) o).id);
        }
        @Override public int hashCode() { return Objects.hash(id); }

        // ❌ These would NOT compile:
        // public Class<?> getClass() { ... }              // getClass() is final
        // public final void notify() { ... }              // notify() is final
        // public void wait() { ... }                      // wait() is final
    }

    public static void main(String[] args) {
        Object o = new Object();
        System.out.println("default toString: " + o);
        System.out.println("getClass != class : " + (o.getClass() == Object.class));
        System.out.println("---");
        Thing t = new Thing("X-1");
        System.out.println("toString = " + t);
        System.out.println("equals self = " + t.equals(t));
        System.out.println("hashCode stable = " + (t.hashCode() == t.hashCode()));
        System.out.println("---");
        try { Object[] arr = new String[2]; arr[0] = 42; }
        catch (ArrayStoreException e) { System.out.println("ArrayStoreException: " + e.getMessage()); }
        System.out.println("---");
        String s = "abc";
        System.out.println("string hash: " + s.hashCode());
        System.out.println("String.hashCode formula check: " + (('a'*31 + 'b')*31 + 'c'));
    }
}
```

**Actual output (compiled and run on JDK 25):**

```text
default toString: java.lang.Object@2b2fa4f7
getClass != class : true
---
toString = Thing{X-1}
equals self = true
hashCode stable = true
---
ArrayStoreException: java.lang.Integer
---
string hash: 96354
String.hashCode formula check: 96354
```

> The `2b2fa4f7` in the first line is the **identity hash code** — it is derived from the object's
> identity and **changes on every run**. That is exactly why you should never depend on the default
> `toString()` output, and why `hashCode()` must be overridden for any value type.

**Dry run:**

| Line | What it demonstrates |
|------|----------------------|
| `default toString: java.lang.Object@251a69d7` | The default format is `ClassName@hexHashCode` — the hex is the identity hash code |
| `getClass != class : true` | `getClass()` returns the **runtime** class; `Object.class` is the *declared* literal |
| `toString = Thing{X-1}` | The override is used by `println` via `String.valueOf` |
| `equals self = true` | The `this == o` fast path satisfies reflexivity |
| `ArrayStoreException: java.lang.Integer` | Covariant arrays check the type at **store** time |
| `string hash: 96354` and `formula check: 96354` | `String.hashCode()` is exactly `s[0]*31^(n-1) + … + s[n-1]` — verified numerically |

**Takeaway:** The two "compile error" comments are the point of the example — `getClass()`,
`wait()` and `notify()` are `final` and the compiler will reject an attempt to override them.

---

### Example 3 — A Correct `Stack` by Composition

**Problem:** *"Show me the correct `Stack` that `extends ArrayList` gets wrong."*

```java
import java.util.*;

public class GoodStack {

    private final Deque<Object> delegate = new ArrayDeque<>();

    public void push(Object item) { delegate.push(item); }

    public Object pop() {
        if (delegate.isEmpty()) throw new NoSuchElementException("stack is empty");
        return delegate.pop();
    }

    public Object peek() {
        if (delegate.isEmpty()) throw new NoSuchElementException("stack is empty");
        return delegate.peek();
    }

    public boolean isEmpty() { return delegate.isEmpty(); }
    public int size() { return delegate.size(); }

    @Override public String toString() {
        return "GoodStack" + delegate;
    }
}
```

**Actual output (compiled and run on JDK 25):**

```text
stack = GoodStack[3, 2, 1]
pop = 3
peek = 2
size after pop = 2
isEmpty = false
```

> Note the `[3, 2, 1]` order — `ArrayDeque`'s `toString` prints **head first**, and the head of a
> stack is the most recently pushed element. The *behaviour* is correct LIFO (`pop` returns `3`),
> but the printed order is the reverse of the push order.

**Verified with this driver:**

```java
public static void main(String[] args) {
    GoodStack stack = new GoodStack();
    stack.push(1); stack.push(2); stack.push(3);
    System.out.println("stack = " + stack);
    System.out.println("pop = " + stack.pop());
    System.out.println("peek = " + stack.peek());
    System.out.println("size after pop = " + stack.size());
    System.out.println("isEmpty = " + stack.isEmpty());
}
```

**Why this is the right design:**

| Concern | `extends ArrayList` | Composition |
|---------|---------------------|-------------|
| Exposes `add`, `remove`, `sort` | ❌ breaks LIFO | ✅ impossible |
| Depends on `ArrayList`'s internals | ❌ fragile base class | ✅ depends on `Deque`'s *interface* only |
| Can be swapped at runtime | ❌ | ✅ (`Deque` → `LinkedDeque`) |
| Testable | Hard (inherits 30+ methods) | Easy (5 methods) |

**Takeaway:** The stack's *contract* is five methods. Inheriting `ArrayList` gives you thirty more
— and every one of them is a way for a caller to break the invariant.

---

## 6. Code from Scratch — Production-Grade

A complete, compilable example of composition, a covariant return with a bridge method, a safe
copy factory, and a `Serializable` class with a `serialVersionUID` and a deserialisation filter.

```java
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 1. COMPOSITION over inheritance: the report holds a list of line items.
 * 2. COVARIANT RETURN: the specialised subtype returns a more specific type.
 * 3. COPY FACTORY instead of clone().
 * 4. Serializable with an explicit serialVersionUID.
 */
public final class SalesReport implements Serializable {

    private static final long serialVersionUID = 1L;   // explicit compatibility contract

    private final String reportId;
    private final List<LineItem> items;                // immutable copy
    private final transient String generatedBy;        // never serialised

    public SalesReport(String reportId, List<LineItem> items, String generatedBy) {
        this.reportId = Objects.requireNonNull(reportId, "reportId");
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.generatedBy = generatedBy;
    }

    public String getReportId() { return reportId; }
    public List<LineItem> getItems() { return items; }

    /** A copy factory - the recommended alternative to clone(). */
    public SalesReport copy() {
        return new SalesReport(reportId, items, generatedBy);
    }

    @Override
    public String toString() {
        return "SalesReport{id='" + reportId + "', items=" + items.size() + '}';
    }

    /** A line item - a separate top-level type, composed, not inherited. */
    public static final class LineItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String sku;
        private final long quantity;

        public LineItem(String sku, long quantity) {
            this.sku = Objects.requireNonNull(sku, "sku");
            this.quantity = quantity;
        }

        public String getSku() { return sku; }
        public long getQuantity() { return quantity; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LineItem other = (LineItem) o;
            return quantity == other.quantity && sku.equals(other.sku);
        }

        @Override
        public int hashCode() { return Objects.hash(sku, quantity); }

        @Override
        public String toString() { return "LineItem{" + sku + " x" + quantity + "}"; }
    }
}
```

**Why this is production-grade:**

| Decision | Rationale |
|----------|-----------|
| `List.copyOf(items)` | The report owns an immutable list — no external mutation |
| `static final class LineItem` | A nested **value** type, not a subclass of the report |
| `copy()` factory, not `clone()` | Type-safe, no cast, and it can be given a different return type later |
| `transient String generatedBy` | Machine-specific metadata that must not travel with the data |
| Explicit `serialVersionUID` | Class evolution will not break previously stored data |
| `equals`/`hashCode` on `LineItem` | The same fields, so the contract holds by construction |

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — `Stack extends ArrayList`

```java
class Stack extends ArrayList<Object> { }   // ❌ exposes add(), remove(), sort()
```
**Fix:** compose a `Deque` and expose only `push`, `pop`, `peek`.

### ❌ Pitfall 2 — The Square/Rectangle LSP violation

```java
// COMPILE-ERROR-DEMO — this block deliberately does NOT compile:
//    class Square extends Rectangle {          ← Rectangle is not in scope here
//        @Override public void setWidth(int w) { width = height = w; }   // ❌ breaks the contract
//    }
// The full example (with Rectangle defined) is in §3 — see "Liskov Substitution Principle".
```
**Fix:** drop the setters (make it immutable), or model a square as a *composition* of a rectangle,
or introduce a common `Shape` without setters.

### ❌ Pitfall 3 — `clone()` used as a deep copy

```java
@Override public Shallow clone() { return (Shallow) super.clone(); }   // ❌ SHALLOW
```
**Fix:** use a copy constructor or copy factory, and copy the mutable fields explicitly.

### ❌ Pitfall 4 — Overriding `clone()` without implementing `Cloneable`

```java
// COMPILE-ERROR-DEMO — this block deliberately does NOT compile.
class Broken implements Cloneable { }   // ❌ doesn't override clone
class AlsoBroken {                       // ❌ overrides clone but not Cloneable
    @Override public Object clone() { return super.clone(); }   // throws CloneNotSupportedException
}
```
**Fix:** use a copy constructor. `Cloneable` is a marker interface with no methods — it only flips
the behaviour of `Object.clone()`.

### ❌ Pitfall 5 — Missing `serialVersionUID`

```java
import java.io.Serializable;

public class Employee implements Serializable {
    private String name;      // ⚠️ no serialVersionUID
}
```
**Fix:** always declare `private static final long serialVersionUID = 1L;`. Without it the
compiler generates a UID from the class structure, so *any* change — even adding a method —
invalidates previously stored data.

### ❌ Pitfall 6 — Deserialising untrusted data

```java
ObjectInputStream ois = new ObjectInputStream(untrustedStream);
Object obj = ois.readObject();   // ❌ RCE vulnerability
```
**Fix:** apply an `ObjectInputFilter` (Java 9+) with an allow-list, or use JSON/Protobuf for new
systems.

### ❌ Pitfall 7 — Assuming `serialVersionUID` changes are harmless

```java
private static final long serialVersionUID = 2L;   // ⚠️ breaks all old data
```
**Fix:** only change it when you *intend* to break compatibility, and then version the storage
format deliberately.

### ❌ Pitfall 8 — `@Override` on a static method

```java
// COMPILE-ERROR-DEMO — this block deliberately does NOT compile.
class Child extends Parent {
    @Override static void show() { }   // ❌ COMPILE ERROR
}
```
**Fix:** remove `@Override`. A static method with the same signature *hides* the parent's — it does
not override it.

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: Composition vs Inheritance - which to prefer?**
A: Prefer composition. Inheritance creates tight coupling and fragile base class problem. Composition is more flexible.

**Q: What is covariant return type?**
A: Overridden method can return a subtype of parent's return type. Introduced in Java 5.

**Q: Can static method be overridden?**
A: No. Static methods are hidden, not overridden. No runtime polymorphism.

**Q: Why override equals() and hashCode() together?**
A: Contract requires equal objects to have equal hash codes. Used in HashMap, HashSet.

**Q: What is serialVersionUID?**
A: Version number for serialization. Ensures compatibility between serialized and deserialized objects.

**Q: What is the difference between `getClass()` and `instanceof`?**
A: `getClass()` returns exact runtime class. `instanceof` checks IS-A (includes parent types).

### 🟡 Intermediate

**Q: What is difference between shallow and deep copy?**
A: Shallow copies references, deep copies actual objects. Use clone() or copy constructor.

**Q: Association vs Aggregation vs Composition?**
A:
- Association: Uses relationship
- Aggregation: Has-A, independent lifetime
- Composition: Contains, dependent lifetime

**Q: Can an abstract class have a constructor?**
A: Yes. It cannot be instantiated directly, but its constructor runs when a subclass instance is
created.

**Q: Can an interface have a constructor?**
A: No. Interfaces cannot be instantiated at all, so a constructor would be unreachable.

**Q: What is the difference between `Serializable` and `Externalizable`?**
A: `Externalizable` gives full control over the serialization process — you write every field
yourself, and it requires a public no-arg constructor. `Serializable` uses the default format and
needs no constructor.

**Q: Why is `getClass()` `final`?**
A: It is the JVM's own machinery. If a subclass could lie about its runtime type, the JVM's
type-checking invariants would break.

### 🔴 Advanced / Senior

**Q: Explain the fragile base class problem in detail.**
A: It occurs when a change to a base class breaks a subclass that did not directly depend on the
changed member. The causes are: shared mutable state, inherited methods that are overridden in
subclasses with different assumptions, and the fact that a subclass cannot override a *new* base
method without changing behaviour. The mitigations are: keep the `protected` surface small,
prefer composition, and use `final` to close extension points.

**Q: What is the bridge method the compiler generates?**
A: When an override narrows the return type, `javac` generates a synthetic method in the subclass
with the *original* return type that delegates to the narrowed one. It preserves binary
compatibility so that clients compiled against the old signature still link. You can see it with
`javap -c`. The downside is that reflection over `getDeclaredMethods()` sees two methods.

**Q: How do you handle a class hierarchy where you need to add a field to a serialised class?**
A: Add the field and leave `serialVersionUID` unchanged — old data deserialises with the new
field at its default value. If the field's *type* changes incompatibly, either keep the old field
and add a new one, or write a custom `readObject` that migrates the value, or change the
`serialVersionUID` deliberately and version the storage format.

**Q: Why is `clone()` discouraged?**
A: It is `protected` and returns `Object` (so it needs a cast), it is shallow by default, it
bypasses the constructor so no validation runs, and `Cloneable` is a marker interface whose only
purpose is to flip the behaviour of a native method. A copy constructor or a static copy factory
is type-safe, explicit and can enforce deep copying.

**Q: How do you secure Java deserialisation?**
A: Apply an `ObjectInputFilter` (Java 9+) with an allow-list of permitted classes, and never
deserialise data from an untrusted source. Better still, do not use native serialisation for new
systems — JSON, Protobuf or Avro avoid the `readObject` code-execution path entirely.

### ⚫ Expert / Principal

**Q: When is inheritance genuinely the right choice?**
A: When there is a real IS-A relationship, the base is stable, and the shared behaviour is the
point — not merely a way to avoid duplication. A `HashMap` is a `Map`; a `FileSystemException` is
an `IOException`. The heuristic I use is: if I cannot state the IS-A relationship in a single
honest sentence, it is composition. And I treat "favour composition" as a tie-breaker, not a law —
Spring's whole architecture is built on interface inheritance, and that is the right call.

**Q: How do you evolve a public API without breaking clients?**
A: Four rules. Additive changes are safe — new methods, new classes, new optional interfaces.
Covariant return narrowing is safe thanks to the bridge method. Anything that changes a *contract*
— a method's behaviour, a field's type, the `serialVersionUID` — needs a versioned path: a new
type, a new endpoint, or an adapter. And I would deprecate rather than remove, with a documented
migration window. The point is that a *compile-time* break is a gift; a *runtime* break in someone
else's deployment is a cost.

**Q: What is the deepest problem with `Object`'s design?**
A: That `Object` is a *universal* supertype carrying a mix of unrelated contracts. `equals` and
`hashCode` are a *value* contract; `clone` is a *copying* contract; `wait`/`notify` are a
*concurrency* contract; `finalize` was a *resource* contract. Every class inherits all of them
whether it wants them or not — so `Thread` inherits `clone()` and `ArrayList` inherits `wait()`.
Modern Java has been steadily narrowing this: `Cloneable` and `finalize` are deprecated,
`AutoCloseable` gives resource management an explicit opt-in, and records and sealed types give
value semantics a first-class representation. The direction is clear: opt in to contracts rather
than inheriting them all.

---

## 9. Counter-Questions & Traps

### CQ1. "Isn't composition always better than inheritance? Why would you ever inherit?"

- **Naive answer:** "Composition is more flexible."  ← the rule, without judgement
- **The trap:** The interviewer is testing whether you can hold a *balanced* position. Blind
  anti-inheritance is a pattern, not a principle, and it will get you caught.
- **Expert answer:** "Never always. 'Favour composition over inheritance' is a **tie-breaker**, not
  a law. Inheritance is right for a genuine IS-A relationship where the shared behaviour is the
  point: a `HashMap` *is* a `Map`, a `FileSystemException` *is* an `IOException`. Spring's entire
  architecture is built on interface inheritance, and that is correct. The heuristic I use: if I
  cannot state the IS-A relationship in one honest sentence, it is composition. And I treat
  'favour composition' as the default when the two are genuinely close, because the cost of
  wrongly inheriting (a fragile base class) is paid later, while the cost of wrongly composing
  (a bit of delegation code) is paid now."

### CQ2. "How is `Square extends Rectangle` wrong? Squares ARE rectangles."

- **Naive answer:** "A square can't have independent width and height."  ← right, shallow
- **The trap:** The interviewer is checking whether the candidate can apply the principle
  *systematically* rather than reciting one example.
- **Expert answer:** "The problem is not geometry — it is that the *contract* of `Rectangle` says
  'width and height are independent', and `Square` cannot honour that. The Liskov Substitution
  Principle says a subtype must be usable wherever the parent is expected. Code written against
  `Rectangle` may call `setWidth(5)` and then `setHeight(10)`, and expect an area of 50. Give it a
  `Square` and it gets 100 — silently. The general rule: if a subclass must *ignore or reinterpret*
  a parent method's parameters, it is not substitutable, and the hierarchy is modelling the wrong
  thing. The fixes are: make the shape immutable so there are no setters to conflict, use a common
  `Shape` supertype without setters, or model a square as a *composition* of a rectangle with a
  constraint."

### CQ3. "Why does `clone()` exist at all? Why not just use a copy constructor?"

- **Naive answer:** "Copy constructors are better."  ← the recommendation, not the history
- **The trap:** The interviewer wants the *reason `clone()` was added*, which reveals the design
  pressures of the time.
- **Expert answer:** "`clone()` predates copy constructors in the language design and was added
  largely to support `Cloneable` on JDK collections, where the copy had to be polymorphic — a
  `Collection` must be able to clone itself without knowing its concrete type. A copy constructor
  cannot do that; a copy *factory* can. `clone()`'s real problems are that it is `protected`,
  returns `Object`, is shallow, and bypasses the constructor. The modern answer is a copy
  constructor for a final class, or a static copy factory when you need polymorphism or a
  different return type. `clone()` is deprecated in spirit, though not yet in the API."

### CQ4. "You said native Java serialization is a security risk. So is `Serializable` unusable?"

- **Naive answer:** "Yes, never use it."  ← over-correction
- **The trap:** The interviewer is testing whether the candidate can be *proportionate* — a risk
  assessment, not a blanket ban.
- **Expert answer:** "Not unusable — *context-dependent*. The risk is real: `readObject` executes
  attacker-controlled code, and there is a long history of CVEs built on it. But the mitigation
  landscape is good: since Java 9 you can install an `ObjectInputFilter` with an allow-list, and
   since Java 17 the filter can be set globally via the `jdk.serialFilter` system property. So for
   *internal* serialisation — a cache, a message queue, an RPC protocol you control on both ends —
   it is fine with a filter. For data crossing a trust boundary, it is the wrong choice; use JSON
   or Protobuf. The professional answer is: I never use it for new inter-service or external
   formats, and where I must, I apply a filter and treat the stream as trusted-only."

### CQ5. "How would you migrate a serialised class that has changed shape?"

- **Naive answer:** "Change the `serialVersionUID`."  ← works, but throws away data
- **The trap:** The interviewer is looking for the *incremental* migration path, which is what a
  real long-lived system needs.
- **Expert answer:** "Changing the `serialVersionUID` deliberately breaks compatibility — old data
  throws `InvalidClassException` — so it is correct only if I am happy to invalidate the data. For
   a gradual migration I would keep the `serialVersionUID` and add a custom `readObject` that
   detects the old shape and transforms it. The mechanism is `ObjectInputStream.readFields()` and
   `readField()`, which let you read the *old* field layout explicitly and populate the new one.
   The alternative, which I prefer for anything long-lived, is to stop using native
   serialisation: write the data as JSON with a version field and migrate it in application code,
   where the migration is testable and visible."

### CQ6. "Why is `getClass()` `final` but `equals()` is not?"

- **Naive answer:** "Because `equals()` is meant to be overridden."  ← the rule, not the reason
- **The trap:** The interviewer is testing whether the candidate understands the difference between
  a *value* contract (open for extension) and a *runtime identity* contract (must be sealed).
- **Expert answer:** "Because they serve different kinds of truth. `equals()` expresses a **value
  judgement** — 'are these two things the same value?' — and different types can legitimately
  answer that question differently, so it must be open. `getClass()` expresses **runtime
  identity** — the JVM's own truth about what an object *is*. If a subclass could override it, the
  JVM's type checks, `instanceof` and the cast machinery would all be manipulable, and the
  invariant that a `String` is a `String` would no longer hold. The same reasoning makes `wait`,
   `notify` and `notifyAll` final: they are the JVM's monitor protocol, not the class's business."

### CQ7. "How would you find out that a `Stack extends ArrayList` bug reached production?"

- **Naive answer:** "Look at the stack trace."  ← too shallow
- **The trap:** This is an *observability* question. The interviewer wants to know whether the
  candidate can identify the *class* of bug, not just the instance.
- **Expert answer:** "The symptom would be data corruption or a `ClassCastException` far from the
   call site — a stack that returns items in the wrong order, or an `ArrayList` operation that
   silently broke the LIFO invariant. To find it I would search the codebase for types that extend
   a JDK collection, which is a five-minute grep and should be in the code-review checklist. At
   runtime, I would look for `ClassCastException` mentioning the collection type, and I would check
   whether any code path calls `add`/`remove`/`sort` on the stack. The structural fix is to
   replace the inheritance with composition, and to add an ArchUnit rule that forbids extending
   `java.util` collection classes — so the class of bug cannot come back."

### CQ8. "Does making a class `final` improve performance?"

- **Naive answer:** "Yes, the JIT can inline the methods."  ← the popular myth
- **The trap:** This is a *challenging-a-premise* question. The performance claim is much weaker
  than people believe.
- **Expert answer:** "Marginally, and it is not the reason to do it. The JIT can already inline
   non-final methods using class-hierarchy analysis and profile data — it just adds a guard in
   case a subclass appears. So `final` removes a guard, which is a tiny win, not an order of
   magnitude. The real reasons to make a class `final` are **semantic**: it guarantees the
   behaviour cannot be overridden, which protects invariants; it removes the `this$0`-style
   subclassing surprises; and for a value type it lets you use `getClass()` safely in `equals()`.
   I would also note that `final` methods and `final` classes are what make the JIT's devirtualisation
   *guaranteed* rather than probabilistic — useful, but secondary."

## 10. Rapid-Fire MCQs

**Q1.** "Favour composition over inheritance" is best described as:
- **A)** An absolute rule — never inherit
- **B)** A tie-breaker when both designs are reasonable
- **C)** An outdated principle
- **D)** A performance optimisation

**Q2.** A `static` method in a subclass with the same signature as the parent's:
- **A)** Overrides the parent's method
- **B)** Hides the parent's method
- **C)** Is a compile error
- **D)** Causes a `ClassCastException`

**Q3.** Covariant return types were introduced in:
- **A)** Java 1.4
- **B)** Java 5
- **C)** Java 8
- **D)** Java 17

**Q4.** The compiler generates a **bridge method** when an override:
- **A)** Changes the parameter types
- **B)** Narrows the return type
- **C)** Is `final`
- **D)** Is `static`

**Q5.** `Object.getClass()` is `final` because:
- **A)** For performance
- **B)** It is the JVM's runtime identity mechanism
- **C)** It was an oversight
- **D)** `Class` is immutable

**Q6.** `Object.clone()` is:
- **A)** `public` and deep
- **B)** `protected` and shallow
- **C)** `final` and shallow
- **D)** `public` and shallow

**Q7.** `finalize()` is:
- **A)** Deprecated since Java 9, removed for removal in 18
- **B)** The recommended cleanup mechanism
- **C)** Called deterministically at GC time
- **D)** Equivalent to `AutoCloseable.close()`

**Q8.** A class must declare `serialVersionUID` explicitly so that:
- **A)** It can be deserialised at all
- **B)** Class evolution does not break stored data
- **C)** The class is `Serializable`
- **D)** `readObject` is called

**Q9.** `Externalizable` requires:
- **A)** A public no-arg constructor
- **B)** A private no-arg constructor
- **C)** `finalize()`
- **D)** `transient` fields

**Q10.** Which is a **security risk** of native Java deserialisation?
- **A)** Slow performance only
- **B)** Remote code execution via `readObject`
- **C)** Loss of the `serialVersionUID`
- **D)** Increased file size

**Q11.** The mitigation for untrusted deserialisation is:
- **A)** `ObjectInputFilter` (Java 9+)
- **B)** A larger `serialVersionUID`
- **C)** Marking fields `transient`
- **D)** Using `Externalizable`

**Q12.** Association, aggregation and composition differ primarily by:
- **A)** The number of fields
- **B)** The **lifetime** relationship
- **C)** The visibility of the members
- **D)** Whether the class is `final`


### Answer Key

| # | Answer | Explanation |
|---|--------|-------------|
| 1 | **B** | It is a heuristic for genuinely close designs, not a law. Real IS-A relationships (a `HashMap` *is* a `Map`) should use inheritance. |
| 2 | **B** | Static methods are *hidden*, not overridden — they bind to the reference type at compile time, so there is no runtime dispatch. |
| 3 | **B** | Java 5 introduced generics and covariant return types together, both to remove casts. |
| 4 | **B** | A bridge method with the original return type is generated to preserve binary compatibility for previously compiled clients. |
| 5 | **B** | `getClass()` is the JVM's identity mechanism; letting subclasses lie about their runtime type would break the JVM's type invariants. |
| 6 | **B** | `clone()` is `protected` and performs a shallow field-by-field copy, bypassing the constructor. |
| 7 | **A** | `finalize()` was deprecated in Java 9 and marked for removal in 18. Use `try-with-resources` or `Cleaner`. |
| 8 | **B** | Without an explicit `serialVersionUID`, the compiler derives one from the class structure, so any change invalidates stored data. |
| 9 | **A** | `Externalizable` requires a **public** no-arg constructor, because the deserialiser must instantiate the object itself. |
| 10 | **B** | `readObject` executes attacker-controlled code during deserialisation — the basis of many real-world RCE CVEs. |
| 11 | **A** | `ObjectInputFilter` (Java 9+) allows an allow-list of permitted classes; it can also be set globally via `jdk.serialFilter`. |
| 12 | **B** | The distinction is about *lifetime*: can the part exist without the whole? The UML diamond notation is a weak proxy for this. |

## 11. Cheat Sheet / One-Page Summary

### Composition vs Inheritance

| Use Inheritance When | Use Composition When |
|---------------------|---------------------|
| IS-A relationship | HAS-A relationship |
| Code reuse in related classes | Code reuse across unrelated classes |
| Polymorphism needed | Flexibility needed |
| Base class is stable | Behavior may change at runtime |

### Overriding vs Hiding

| Aspect | Overriding | Hiding |
|--------|-----------|--------|
| Methods | Instance methods | Static methods |
| Binding | Runtime (dynamic) | Compile-time (static) |
| Polymorphism | Yes | No |
| Annotation | `@Override` | Not applicable |
| Resolution | Based on object type | Based on reference type |

### The `Object` API

| Method | Modifiers | Overridable? |
|--------|-----------|--------------|
| `equals(Object)` | `public` | ✅ |
| `hashCode()` | `public native` | ✅ |
| `toString()` | `public` | ✅ |
| `getClass()` | `public final native` | ❌ |
| `wait()` / `notify()` / `notifyAll()` | `public final` | ❌ |
| `clone()` | `protected native` | ⚠️ (overridable, but discouraged) |
| `finalize()` | `protected`, deprecated | ⚠️ (do not use) |

### Copy Constructor vs clone()

| Aspect | Copy Constructor | clone() |
|--------|-----------------|---------|
| Explicit | Yes | No (magic method) |
| Type safety | Yes | Returns Object (needs cast) |
| Deep copy | Easy to implement | Must manually deep copy |
| Inheritance | Works naturally | Fragile with inheritance |
| Recommended | ✅ Yes | ❌ Avoid (Effective Java) |

### Serializable vs Externalizable

| Aspect | `Serializable` | `Externalizable` |
|--------|----------------|------------------|
| No-arg constructor | Not required | **Required (public)** |
| Format control | Default | **Total** |
| Size | Larger | Usually smaller |
| Versioning | `serialVersionUID` | Your own format |
| RCE surface | Larger | Smaller |

### The Decision Flowchart

```text
   "Should B extend A, or should A hold a B?"
              │
              ├── B is genuinely IS-A A, and A is stable?
              │        │
              │       YES ──► INHERITANCE
              │        │
              │       NO ──► COMPOSITION
              │
              "Do I need a copy?"
              │
              ├──► copy constructor / copy factory   ✅
              └──► clone()                           ❌
```

---

## 12. Real-World Production Notes

### Where These Show Up in Banking / E-Commerce Systems

| Topic | Production use | The trap |
|-------|----------------|-----------|
| Composition | `OrderService` holds a `PaymentGateway`; swap to a mock in tests | A `static` gateway cannot be mocked |
| LSP | A `SavingsAccount extends Account` must honour every `Account` promise | A `PremiumAccount` that overrides `withdraw` differently breaks callers |
| Covariant return | `findById` returns `Optional<Order>` in a repository, `Optional<Order>` in a service | A raw-type override loses type safety |
| `Object` methods | `toString()` in log statements | PAN/card numbers leaking into log aggregation |
| `clone()` | Rarely appropriate — legacy `Serializable` caches | A shallow "copy" that shares a mutable buffer |
| `Serializable` | Legacy session beans, RMI, some MQ payloads | RCE via untrusted `readObject` |
| `serialVersionUID` | Every persisted class | Missing UID → any class change invalidates data |
| `Externalizable` | High-volume payloads where size matters | Requires a public no-arg constructor — a security consideration |

### Tuning Knobs & Metrics

- **ArchUnit** — enforce "no class may extend `java.util` collection types" and "no class may
  extend `java.io.Serializable` without a `serialVersionUID`". These rules fail the build instead
  of a review comment.
- **Error Prone / SpotBugs** — `EqualsGetClass`, `MissingSerialVersionUID`, `CloneThrowsCloneNotSupportedException`.
- **JFR / heap dump** — a `Stack` that grew to millions of entries usually means someone called
  `add()` on it.
- **`ObjectInputFilter` (`jdk.serialFilter`)** — a global allow-list for any deserialisation in the
  JVM. Set it in production even if you think you are safe.
- **Micrometer** — deserialisation error counters, and a gauge on any cache backed by
  `Serializable` objects.

### Practical Rules We Enforce in Code Review

1. **Never extend a JDK collection class** — compose instead.
2. **Every `Serializable` class declares `serialVersionUID` explicitly.**
3. **Never deserialise untrusted input**; always install an `ObjectInputFilter`.
4. **New systems use JSON or Protobuf**, not native serialisation.
5. **Use copy constructors or copy factories**, never `clone()`.
6. **Use `try-with-resources`** for every `AutoCloseable`; never `finalize()`.
7. **A subclass must honour every parent promise** — if it must reinterpret a parent's method,
   the hierarchy is wrong.

---

## 13. Follow-up Learning

### Next in this folder

- [12 · Critical and Tricky OOP Interview Scenarios](./12-OOP-Critical-and-Tricky-Interview-Scenarios.md) — the scenario bank that applies everything here
- [13 · Covariance, Contravariance, Invariance](./13-OOP-Covariance-Contravariance-Invariance.md) — the type-system view of the same ideas
- [08 · equals / hashCode / toString](./08-OOP-equals-hashCode-toString.md) — the `Object` contracts in depth
- [09 · Immutable Classes](./09-OOP-Immutable-Classes.md) — the value-object side of the `Object` contract

### Related topics elsewhere

- [SOLID Principles](../10-SOLID-Principles/01-SOLID-Principles.md) — LSP, OCP and DIP in full
- [Design Patterns](../09-Design-Patterns/01-Design-Patterns.md) — Strategy, Builder, Decorator and the Composite pattern
- [Serialization](../17-Serialization/01-Serialization-Basics.md) — the full serialisation chapter
- [Java 11/17/21 Features](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) — sealed classes and records

### Specifications and books

- **JLS §8.1.3** — nested classes; **§8.4.8.3** — covariant return types; **§8.4.8.4** — `final`
  and `abstract` methods
- **JLS §15.12** — method invocation, overload resolution and hiding
- **JVM Specification §5.4.3.3** — method resolution; **§5.4.6** — method invocation
- **Effective Java** — Items 12 (`toString`), 13 (avoid `clone`), 17 (value-based classes),
  55 (serialization)
- **Java Language Specification §8.6.4.4** — why `clone()` and `finalize()` are on `Object`

---

**Next Chapter:** → [12 · Critical and Tricky OOP Interview Scenarios](./12-OOP-Critical-and-Tricky-Interview-Scenarios.md)
**Previous Chapter:** ← [10 · Nested, Inner and Anonymous Classes](./10-OOP-Nested-Inner-and-Anonymous-Classes.md)

*Last Updated: 2026 · Java 17+ (verified on JDK 25) · All outputs in this chapter were produced by running the code*
