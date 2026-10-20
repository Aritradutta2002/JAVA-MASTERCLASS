# Java 8 Features — Lambdas & Functional Interfaces

> **Java 8 changed the language permanently.** Not by adding syntax — by adding a
> *kind* of type: the functional interface, and with it, the ability to pass
> behaviour as a value. Everything in modern Java — streams, `Optional`,
> `CompletableFuture`, Spring's functional routing — is downstream of this.

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

- **Java 8 (March 2014)** added: lambdas, method references, functional interfaces, the **Stream API**, `Optional`, `java.time`, default/static/private interface methods, and `CompletableFuture`.
- **A functional interface has exactly ONE abstract method** (SAM — *Single Abstract Method*). Its other methods must be `default`, `static` or `private`.
- **`@FunctionalInterface` is optional but recommended** — it makes the compiler *enforce* the SAM rule, so a future edit that adds a second abstract method becomes a compile error rather than a silent break.
- **A lambda is an implementation of a functional interface.** The compiler creates an anonymous class for you — but with a **singleton instance** (the lambda instance is cached, so `lambda1 == lambda2` can be `true` for non-capturing lambdas).
- **Captured local variables must be effectively final.** A captured variable cannot be reassigned. This is a *compile-time* restriction that makes concurrent use safe by construction.
- **`this` inside a lambda is the enclosing instance's `this`** — unlike an anonymous inner class, which has its own `this`. This is the single most-tested lambda detail.
- **There are 4 method-reference forms:** `Type::staticMethod`, `instance::staticMethod`, `Type::instanceMethod`, `instance::instanceMethod`.
- **The built-in functional interfaces** live in `java.util.function`: `Function`, `BiFunction`, `Consumer`, `BiConsumer`, `Supplier`, `Predicate`, `BiPredicate`, `UnaryOperator`, `BinaryOperator`, `ToIntFunction`/`ToLongFunction`/`ToDoubleFunction` (primitive specialisations), plus `Runnable` and `Callable` in `java.lang`.
- **Primitive specialisations exist to avoid boxing:** `IntPredicate`, `LongPredicate`, `DoublePredicate`, `IntFunction`, `IntUnaryOperator`, `IntBinaryOperator`, `ToIntFunction`, etc. — 40+ in total.
- **A lambda's body is either an *expression* or a *block*.** An expression body is implicitly `return`ed — so `x -> x + 1` and `x -> { return x + 1; }` are equivalent.
- **Lambdas are `Serializable`** if the functional interface extends `Serializable` — which is why `Comparator` and `Function` do. This is used for distributed computing frameworks.

---

## 1. What & Why — The Problem This Solves

**The problem (Java 7 and earlier).** Passing *behaviour* required an anonymous class — a whole class declaration, in the middle of a method call, to do one thing:

```java
// ❌ BEFORE — 6 lines of ceremony to sort a list
Collections.sort(names, new Comparator<String>() {
    @Override
    public int compare(String a, String b) {
        return a.length() - b.length();
    }
});
```

**The solution.**

```java
// ✅ AFTER — the behaviour IS the argument
names.sort(Comparator.comparingInt(String::length));
// or
names.sort((a, b) -> a.length() - b.length());
```

**What this really bought.** The important shift is not "fewer lines". It is that **behaviour became a first-class value**:

| Capability | Before Java 8 | After Java 8 |
|---|---|---|
| Pass behaviour as an argument | Anonymous class | Lambda / method reference |
| Store behaviour in a field | A custom interface | `Function<String, Integer>` |
| Return behaviour from a method | A custom interface | `Supplier<T>` |
| Compose behaviour | Manual | `Function.andThen` / `compose` |
| Run behaviour in parallel | Manual `ExecutorService` | `Stream.parallel()` |
| Combine behaviour with a value | Manual | `Optional.map` / `flatMap` |
| Lazily evaluate | Manual | `Supplier`, `Stream` laziness |

> 💡 **The interview framing.** Java 8 is not "Java with lambdas". It is the moment
> Java became a language where you can *pass a function*. That is what unlocked
> the Stream API, `Optional`, and the CompletableFuture-based async model — and
> it is the reason the language could absorb functional techniques without a
> rewrite.

---

## 2. Mental Model — The One Picture

```text
   WHAT THE COMPILER DOES WITH A LAMBDA
   ──────────────────────────────────────

   You write:                       The compiler generates:
   ───────────                      ───────────────────────
   Runnable r =                     final class Lambda$1
              () -> {               implements Runnable {
                  System.out       public void run() {
                      .println();      System.out
              };                        .println();
                                   }
                                 }

   → ONE anonymous class per lambda EXPRESSION
   → the INSTANCE is a SINGLETON (the compiler caches it)
   → so:  Runnable a = () -> {};
        Runnable b = () -> {};
        a == b;      // ✅ TRUE for a non-capturing lambda!
```

```text
   THE FOUR THINGS A LAMBDA CAN CAPTURE
   ─────────────────────────────────────
   1. LOCAL VARIABLES        → must be effectively final
   2. INSTANCE FIELDS        → read via the enclosing `this`
   3. STATIC FIELDS          → read directly
   4. PARAMETERS             → must be effectively final

   ⚠ `this` inside a lambda = the ENCLOSING instance's `this`
     (an anonymous inner class has its OWN `this`)
```

**What to look at:** the *singleton* nature of the lambda instance is a real, testable fact — and it is the reason `Comparator` (which extends `Serializable`) instances are safe to share.

---

## 3. Deep Dive — Theory

### 3.1 What exactly is a functional interface?

```java
@FunctionalInterface
public interface Validator<T> {
    boolean isValid(T value);          // ← the SINGLE abstract method (SAM)

    // These are allowed — they are NOT abstract:
    default boolean isValidOrNull(T value) {
        return value == null || isValid(value);
    }

    static <T> Validator<T> alwaysTrue() {
        return value -> true;
    }

    private void log() { }             // Java 9+ — private interface methods
}
```

**The rules:**

| Rule | Detail |
|---|---|
| Exactly **one** abstract method | The SAM. This is what a lambda implements. |
| Any number of `default` methods | Allowed — they have bodies. |
| Any number of `static` methods | Allowed — they are not inherited by implementors. |
| `private` methods | Allowed since **Java 9** (not inherited either). |
| `public` methods | A `public` abstract method is still abstract — a second one breaks the rule. |
| The SAM may be `default`? | **No** — a `default` method has a body, so it is not abstract. |
| `@FunctionalInterface` | Optional, but the compiler then *enforces* the SAM rule. |

**"Inherited" abstract methods count.** If `interface A { void f(); }` and `interface B extends A { }`, then `B` still has one abstract method (`f`), so `B` is functional.

**The `@FunctionalInterface` annotation is a contract.** It is what makes the *compiler* guarantee the SAM property, which is what lets the language evolve the library safely. Without it, adding a method to `Comparator` in Java 8 would have been a source-incompatible change for every implementor.

```java
// ❌ Without @FunctionalInterface this compiles today
interface Broken {
    void a();
    void b();     // 💥 two abstract methods → NOT a functional interface
}
```

```java
@FunctionalInterface
interface AlsoBroken {
    void a();
    void b();     // ❌ compile error: "invalid functional interface ... not a functional interface"
}
```

### 3.2 Target typing — how the compiler knows which SAM to implement

```java
// The compiler infers the SAM from the TARGET TYPE
Predicate<String> p = s -> s.isEmpty();          // Predicate.test(T) → boolean
Function<String,Integer> f = s -> s.length();     // Function.apply(T) → R
Supplier<List<String>> sup = ArrayList::new;      // Supplier.get() → T
Consumer<String> c = System.out::println;         // Consumer.accept(T) → void
```

**The mental model:** a lambda is a *polymorphic expression*. It has no type of its own — its type is determined by the context it appears in. This is called **target typing**, and it is the same mechanism as the "target type" in Java 8's improved type inference.

**The three inference contexts:**

```java
// 1. Assignment
Predicate<String> p = s -> s.isEmpty();

// 2. Method argument
list.forEach(s -> System.out.println(s));

// 3. Return statement
Function<String, Integer> f = () -> 42;   // inside a method returning Function
```

**Overload resolution with lambdas** — the classic ambiguity:

```java
void submit(Runnable r) { }
void submit(Callable<String> c) { }

submit(() -> System.out.println("x"));   // ✅ Runnable — a void-compatible expression body
// submit(() -> "x");                    // ❌ AMBIGUOUS? No — "x" is not void-compatible,
//                                          so only Callable applies
```

```java
// A genuinely ambiguous case:
submit(() -> { });         // ❌ ambiguous — a block body with no return is void-compatible
                          //    AND ... actually only Runnable applies here.
```

The rule: an **expression** lambda is compatible with a void-returning SAM only if the expression is a **statement expression** (a method call, an assignment, an increment, or a `new`). `"x"` is *not* a statement expression — so it cannot be a `Runnable`.

```java
submit(() -> System.out.println("x"));   // ✅ Runnable (println is a statement expression)
submit(() -> "x");                       // ✅ Callable<String> only (not a statement expression)
```

### 3.3 Effectively final — why captured variables cannot be reassigned

```java
int count = 0;
list.forEach(item -> {
    count++;        // ❌ COMPILE ERROR: "local variables referenced from a lambda expression
});                 //    must be final or effectively final"
```

**The rule.** A local variable or parameter captured by a lambda must be **effectively final**: it is never assigned after initialisation. You do *not* have to declare it `final` — the compiler infers it.

```java
int count = 0;              // ✅ effectively final (never reassigned)
final int limit = 10;       // ✅ explicitly final — also fine
int temp = 5;
temp = 6;                   // ❌ now temp is NOT effectively final
```

**Why the restriction exists — and it is a good reason:**

```text
   If captured variables were mutable, this would compile:

   int count = 0;
   Runnable r = () -> { count++; };      // a shared mutable counter

   → the lambda would be executed on ANOTHER thread
   → count++ is read-modify-write → LOST UPDATES
   → the compiler could not prove safety

   By requiring effectively-final, the language makes the capture IMMUTABLE,
   so the captured value can be safely published to any thread.
   → "effectively immutable" capture is what makes lambdas thread-safe.
```

**The workaround when you genuinely need mutable state** — use a one-element array (the old idiom) or, much better, an atomic:

```java
// ✅ The modern way
AtomicInteger count = new AtomicInteger();
list.forEach(item -> count.incrementAndGet());

// ⚠ The old idiom — works, but ugly
final int[] count = {0};
list.forEach(item -> count[0]++);
```

### 3.4 `this` inside a lambda — the key difference from an anonymous class

```java
// COMPILE-ERROR-DEMO — the anonymousVersion() method below deliberately does NOT compile.
public class Outer {
    private String name = "Outer";

    public Runnable lambdaVersion() {
        return () -> System.out.println(this.name);   // "Outer" — the OUTER instance
    }

    public Runnable anonymousVersion() {
        return new Runnable() {
            @Override public void run() {
                System.out.println(this.name);         // 💥 COMPILE ERROR!
                // "cannot find symbol: name" — `this` is the anonymous class,
                // which has no `name` field
            }
        };
    }
}
```

**The rule:**
- In an **anonymous inner class**, `this` refers to the anonymous class instance.
- In a **lambda**, `this` refers to the **enclosing instance** (the same `this` as the enclosing method's).

**Why?** A lambda does not introduce a new scope for `this` — it is not a class, so it has no `this` of its own. An anonymous class *is* a class, so it has its own `this`.

**The practical consequence:** you cannot get a lambda's own identity via `this`. If you need a self-reference, capture it explicitly:

```java
// Capture the enclosing instance explicitly
public Supplier<Outer> self() {
    Outer self = this;
    return () -> self;
}
```

### 3.5 The four method-reference forms

```java
// ①  Type::staticMethod
Function<String, Integer> f1 = Integer::parseInt;
BiFunction<String, String, Boolean> f2 = String::startsWith;

// ②  instance::staticMethod      (bound to a specific instance)
String prefix = "Hello ";
Function<String, String> f3 = prefix::concat;      // bound instance, static method
//            ^ the receiver is CAPTURED

// ③  Type::instanceMethod         (unbound — the receiver is the first parameter)
Function<String, Integer> f4 = String::length;      // (s) -> s.length()
BiFunction<String, String, Boolean> f5 = String::startsWith;  // (a, b) -> a.startsWith(b)

// ④  instance::instanceMethod     (bound to a specific instance)
String text = "abc";
Supplier<Integer> f6 = text::length;                // () -> text.length()
```

**The mapping to a lambda — the table to memorise:**

| Method reference | Equivalent lambda |
|------------------|-------------------|
| `String::length` | `s -> s.length()` |
| `String::length` (bound: `s::length`) | `() -> s.length()` |
| `Integer::parseInt` | `s -> Integer.parseInt(s)` |
| `s::toUpperCase` | `() -> s.toUpperCase()` |
| `System.out::println` | `x -> System.out.println(x)` |
| `String[]::new` | `size -> new String[size]` |
| `ArrayList::new` | `() -> new ArrayList<>()` |
| `List::of` | `elements -> List.of(elements)` (varargs) |

**The three that confuse people:**

```java
// ① Constructor reference
Function<String, ArrayList<String>> f = ArrayList::new;   // (s) -> new ArrayList<>(s)
Supplier<ArrayList<String>> g = ArrayList::new;            // () -> new ArrayList<>()
IntFunction<String[]> h = String[]::new;                   // (n) -> new String[n]

// ② Static method via an INSTANCE reference (the receiver is captured)
String prefix = "P:";
Supplier<String> i = prefix::toUpperCase;   // () -> prefix.toUpperCase()
                                             // ⚠ NOT the same as String::toUpperCase

// ③ Varargs
Function<String[], List<String>> j = List::of;   // (arr) -> List.of(arr)
```

> ⚠️ **`String::toUpperCase` vs `s::toUpperCase` are different.**
> - `String::toUpperCase` → `(String s) -> s.toUpperCase()` — the receiver is the *argument*.
> - `s::toUpperCase` → `() -> s.toUpperCase()` — the receiver is *captured*, so it is a `Supplier`.

### 3.6 The built-in functional interfaces catalogue

```text
   java.util.function
   ══════════════════
   ┌──────────────────────┬──────────────────┬────────────────────────────┐
   │ Interface             │ Signature        │ Typical use                │
   ├──────────────────────┼──────────────────┼────────────────────────────┤
   │ Function<T,R>         │ R apply(T)       │ transform                  │
   │ BiFunction<T,U,R>     │ R apply(T,U)     │ transform two inputs       │
   │ Consumer<T>           │ void accept(T)   │ side effect                │
   │ BiConsumer<T,U>       │ void accept(T,U) │ side effect, two inputs    │
   │ Supplier<T>           │ T get()          │ lazy supply / factory      │
   │ Predicate<T>          │ boolean test(T)  │ filter                     │
   │ BiPredicate<T,U>      │ boolean test(T,U)│ two-arg filter             │
   │ UnaryOperator<T>      │ T apply(T)       │ same-type transform        │
   │ BinaryOperator<T>     │ T apply(T,T)     │ same-type merge            │
   │ ToIntFunction<T>      │ int applyAsInt(T)│ primitive specialisation   │
   │ ToLongFunction<T>     │ long applyAsLong(T)│                          │
   │ ToDoubleFunction<T>   │ double applyAsDouble(T)│                       │
   │ IntPredicate          │ boolean test(int)│ primitive filter           │
   │ IntFunction<R>        │ R apply(int)     │ primitive → object         │
   │ IntUnaryOperator      │ int apply(int)   │ primitive transform        │
   │ IntBinaryOperator     │ int apply(int,int)│ primitive merge           │
   │ IntToLongFunction     │ long applyAsLong(int)│ primitive → primitive   │
   │ IntToDoubleFunction   │ double applyAsDouble(int)│                     │
   │ LongToIntFunction     │ int applyAsInt(long)│                        │
   │ DoubleToIntFunction   │ int applyAsInt(double)│                       │
   │ ObjIntConsumer<T>     │ void accept(T,int)│                          │
   └──────────────────────┴──────────────────┴────────────────────────────┘

   java.lang
   ────────
   Runnable          void run()                  — no arg, no return
   Callable<V>      V call() throws Exception  — no arg, returns a value, may throw

   java.util.concurrent
   ─────────────────────
   BlockingQueue etc. are not functional interfaces, but use them with lambdas.
```

**The two that get confused:**

| | `Runnable` | `Callable<V>` |
|---|---|---|
| Package | `java.lang` | `java.util.concurrent` |
| Method | `void run()` | `V call() throws Exception` |
| Returns a value? | ❌ | ✅ |
| Can throw a checked exception? | ❌ | ✅ |
| Used by | `Thread`, `Executor.execute` | `ExecutorService.submit` |

```java
ExecutorService pool = Executors.newFixedThreadPool(2);
pool.execute(() -> System.out.println("runnable"));            // void
Future<String> f = pool.submit(() -> "callable");             // returns a value
Future<String> f2 = pool.submit(() -> { throw new IOException(); });  // checked exception OK
```

**The four composition methods — the real power of `Function`:**

```java
Function<Integer, Integer> doubleIt = x -> x * 2;
Function<Integer, Integer> plusOne = x -> x + 1;

doubleIt.andThen(plusOne).apply(5);   // (5*2)+1 = 11   → "after"
doubleIt.compose(plusOne).apply(5);   // (5+1)*2 = 12   → "before"
Function.identity().apply("x");       // "x"            → the identity function
Function<String, String> notNull = Function.identity();
```

```text
   andThen:  input ──▶ f ──▶ g ──▶ output        (f first, then g)
   compose:  input ──▶ g ──▶ f ──▶ output        (g first, then f)

   ⚠ THE ORDER IS THE TRAP:
   andThen(f, g) = f THEN g
   compose(f, g) = g THEN f
```

**The `Predicate` composition:**

```java
Predicate<String> notEmpty = s -> !s.isEmpty();
Predicate<String> shortEnough = s -> s.length() < 10;

notEmpty.and(shortEnough).test("hello");     // true
notEmpty.or(shortEnough).test("");           // true
notEmpty.negate().test("");                  // false
```

**The `Consumer` composition:**

```java
Consumer<String> log = System.out::println;
Consumer<String> shout = s -> System.out.println(s.toUpperCase());

log.andThen(shout).accept("hi");    // prints "hi" then "HI"
```

**The `Supplier` composition:**

```java
Supplier<List<String>> s1 = ArrayList::new;
Supplier<List<String>> s2 = ArrayList::new;
s1.get() == s2.get();     // ❌ FALSE — each call creates a new list
```

### 3.7 Default, static and private interface methods

**Why Java 8 added `default` methods — the compatibility problem:**

```text
   Java 7:  interface List { ... }
             class MyList implements List { ... }

   Java 8:  someone adds List.forEach(...) to the interface
            → EVERY implementor in the world breaks 💥

   SOLUTION:  default method — the interface provides a body, so existing
              implementors are unaffected.
```

```java
public interface Shape {
    double area();

    // A default method with a body — existing implementors are unaffected
    default boolean isLargerThan(Shape other) {
        return area() > other.area();
    }

    // A static method — NOT inherited by implementors
    static Shape of(double... sides) { /* factory */ }

    // A private method — Java 9+ — a helper for the default methods
    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
```

**The `default` method conflict rules — a classic interview question:**

```text
   Rule 1 — a CLASS always wins over an INTERFACE.
            If a class inherits a concrete method from its superclass and
            an interface declares the same method, the CLASS's method wins.
            (No conflict — the class is more specific.)

   Rule 2 — a SUBCLASS wins over a SUPERINTERFACE.
            If two interfaces (one extending the other) declare the same
            method, the SUBCLASS's version wins.

   Rule 3 — if two UNRELATED interfaces declare the same method,
            it is a COMPILE ERROR — the class MUST override it.
```

```java
interface A { default void hello() { System.out.println("A"); } }
interface B { default void hello() { System.out.println("B"); } }

// ❌ COMPILE ERROR: "inherits unrelated defaults for hello()"
//    class C implements A, B { }

// ✅ The class MUST override and may disambiguate
class C implements A, B {
    @Override public void hello() { A.super.hello(); }   // explicitly pick A
    // or: B.super.hello();
    // or: System.out.println("C");
}
```

```java
interface Parent   { default void greet() { System.out.println("parent"); } }
interface Child    extends Parent { @Override default void greet() { System.out.println("child"); } }
class Impl implements Child { }
// Impl.greet() → "child"   (Rule 2: the more specific interface wins)
```

**Why `default` methods matter in practice:** they let you evolve an interface **without breaking implementors**. `Iterable.forEach`, `Collection.stream`, `List.sort`, `Comparator` — all default methods. `Collection.stream()` being a default method is *why* every collection got streams for free in Java 8.

### 3.8 Lambdas and serialisation

```java
public interface Transformer extends Serializable {
    String transform(String input);
}
```

- A lambda whose target type extends `Serializable` **is** serialisable — the compiler generates a `writeReplace` that returns a `SerializedLambda`.
- This is why `Comparator`, `Function`, `Predicate` etc. extend `Serializable`.
- It is what allows behaviour to cross a process boundary in Spark, Kafka Streams, and similar frameworks.

```java
// ⚠ A lambda capturing a non-serialisable object cannot be serialised
Transformer t = s -> s + someNonSerializableThing;   // 💥 NotSerializableException
```

### 3.9 The `Predicate`/`Function`/`Consumer`/`Supplier` design guide

```text
   ASK YOURSELF: what does the method DO with the value?

   Does it RETURN a value?           → Function<T, R>
   Does it CONSUME a value?          → Consumer<T>
   Does it PRODUCE a value?          → Supplier<T>
   Does it DECIDE true/false?        → Predicate<T>
   Does it need TWO inputs?          → BiFunction / BiConsumer / BiPredicate
   Does it work on a primitive?      → Int*/Long*/Double* specialisations

   ⚠ ALWAYS use the primitive specialisation in a hot numeric path:
      IntPredicate avoids boxing entirely.
```

```java
// ❌ boxing on every call
Predicate<Integer> isEven = i -> i % 2 == 0;

// ✅ no boxing
IntPredicate isEven = i -> i % 2 == 0;
```

---

## 4. Visualizations

### 4.1 What the compiler generates

```text
   SOURCE
   ──────
   Runnable r = () -> System.out.println("hi");
   Predicate<String> p = s -> s.isEmpty();

   GENERATED BYTECODE (javap -p)
   ─────────────────────────────
   final class Lambda$1 implements Runnable {
       public void run() { System.out.println("hi"); }
   }
   final class Lambda$2 implements java.util.function.Predicate {
       public boolean test(Object s) {           ← BRIDGE: the erased SAM
           return ((String) s).isEmpty();         ← the cast the compiler inserts
       }
   }
```

**What to look at:** the lambda class implements the *erased* SAM (`test(Object)`), and the compiler inserts the `checkcast` — the same erasure mechanism as generics.

### 4.2 Lambda vs anonymous class — the `this` difference

```text
   OUTER outer = new Outer();

   ┌─────────────────────────────────────┐
   │  ANONYMOUS CLASS                    │
   │  ─────────────────                  │
   │  this  →  the ANONYMOUS INSTANCE    │  ← a NEW "this"
   │  outer →  the enclosing instance    │
   │  this.name  →  ❌ compile error      │
   │                                     │
   │  CANNOT be serialised (unless the   │
   │  interface extends Serializable)    │
   └─────────────────────────────────────┘

   ┌─────────────────────────────────────┐
   │  LAMBDA                             │
   │  ──────                             │
   │  this  →  the ENCLOSING instance    │  ← the SAME "this"
   │  outer →  the enclosing instance    │
   │  this.name  →  ✅ "Outer"            │
   │                                     │
   │  CAN be serialised (if the interface│
   │  extends Serializable)              │
   └─────────────────────────────────────┘
```

### 4.3 Effective finality — the lost-update it prevents

```text
   ❌ IF CAPTURED VARIABLES WERE MUTABLE
   ─────────────────────────────────────
   int count = 0;
   Runnable r = () -> count++;         // hypothetically allowed

   Thread A: reads count (0) ──▶ count = 1
   Thread B: reads count (0) ──▶ count = 1     ← LOST UPDATE
   final count = 1, but we expected 2!

   ✅ EFFECTIVE FINALITY MAKES IT IMPOSSIBLE
   ───────────────────────────────────────────
   int count = 0;                     // effectively final
   Runnable r = () -> count++;        // ❌ COMPILE ERROR

   → the captured value is IMMUTABLE
   → it can be safely published to any thread
   → the compiler has PROVEN there is no race
```

### 4.4 The four method-reference forms mapped to lambdas

```text
   ① Type::staticMethod
      Integer::parseInt          ≡  s -> Integer.parseInt(s)
                                 ≡  Function<String,Integer>

   ② instance::staticMethod      (the receiver is CAPTURED)
      "Hi"::toUpperCase          ≡  () -> "Hi".toUpperCase()
                                 ≡  Supplier<String>

   ③ Type::instanceMethod        (the receiver is the ARGUMENT)
      String::length             ≡  s -> s.length()
                                 ≡  Function<String,Integer>

   ④ instance::instanceMethod    (the receiver is CAPTURED)
      text::length               ≡  () -> text.length()
                                 ≡  Supplier<Integer>

   ⚠ THE TRAP:
      String::length  and  text::length  look similar but are DIFFERENT:
        String::length  → takes a String and returns its length
        text::length    → takes NOTHING and returns THIS text's length
```

### 4.5 The `default` method conflict rules

```text
   ┌─────────────────────────────────────────────────────────────────┐
   │  Rule 1 — CLASS BEATS INTERFACE                                │
   │                                                                 │
   │  class Base { void hello() { print("base"); } }                │
   │  interface I { default void hello() { print("I"); } }           │
   │  class Sub extends Base implements I { }                        │
   │                                                                 │
   │  new Sub().hello()  →  "base"   ✅ NO CONFLICT                 │
   │  (the class is more specific — it wins silently)                │
   └─────────────────────────────────────────────────────────────────┘

   ┌─────────────────────────────────────────────────────────────────┐
   │  Rule 2 — SUBINTERFACE BEATS SUPERINTERFACE                     │
   │                                                                 │
   │  interface P { default void f() { print("P"); } }               │
   │  interface C extends P { default void f() { print("C"); } }      │
   │  class Impl implements C { }                                    │
   │                                                                 │
   │  new Impl().f()  →  "C"   ✅ the most specific wins             │
   └─────────────────────────────────────────────────────────────────┘

   ┌─────────────────────────────────────────────────────────────────┐
   │  Rule 3 — TWO UNRELATED INTERFACES = COMPILE ERROR             │
   │                                                                 │
   │  interface A { default void f() { print("A"); } }               │
   │  interface B { default void f() { print("B"); } }               │
   │  class Bad implements A, B { }                                  │
   │  ❌ "inherits unrelated defaults for f() from types A and B"    │
   │                                                                 │
   │  class Good implements A, B {                                   │
   │      @Override public void f() { A.super.f(); }   // disambiguate│
   │  }                                                              │
   └─────────────────────────────────────────────────────────────────┘
```

### 4.6 The functional-interface toolkit

```text
   ┌──────────────────────────────────────────────────────────────────┐
   │  "WHAT DOES IT DO WITH THE VALUE?"                              │
   ├──────────────────┬───────────────────┬──────────────────────────┤
   │ returns a value  │ Function<T,R>     │ transform                │
   │ consumes         │ Consumer<T>       │ side effect              │
   │ produces         │ Supplier<T>       │ lazy supply / factory    │
   │ decides          │ Predicate<T>      │ filter                   │
   │ two inputs       │ Bi* variants      │                          │
   │ primitives       │ Int*/Long*/Double*│ no boxing                │
   └──────────────────┴───────────────────┴──────────────────────────┘

   COMPOSITION:
   Function : andThen (after) · compose (before) · identity
   Predicate: and · or · negate
   Consumer : andThen
   Supplier : (none)
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — `this` in a lambda vs an anonymous class

```java
public class ThisDemo {
    private final String name = "Outer";

    Runnable lambda() {
        return () -> System.out.println("lambda this.name   = " + this.name);
    }

    Runnable anonymous() {
        return new Runnable() {
            private final String name = "Anonymous";
            @Override public void run() {
                System.out.println("anon this.name      = " + this.name);
                System.out.println("anon Outer.this.name= " + Outer.this.name);
            }
        };
    }

    public static void main(String[] args) {
        ThisDemo d = new ThisDemo();
        d.lambda().run();
        d.anonymous().run();
    }
}
```

**Actual output:**

```text
lambda this.name   = Outer
anon this.name      = Anonymous
anon Outer.this.name= Outer
```

**Takeaway:** in the lambda, `this` is the `ThisDemo` instance → `"Outer"`. In the anonymous class, `this` is the anonymous instance → `"Anonymous"`, and `Outer.this` is needed to reach the enclosing one.

### Example 2 — Effectively final, and the modern workaround

```java
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class EffectivelyFinalDemo {
    public static void main(String[] args) {
        List<String> names = List.of("al", "bob", "carol");

        // ✅ A captured local must be effectively final
        String prefix = ">> ";
        List<String> upper = names.stream()
                .map(n -> prefix + n.toUpperCase())
                .collect(Collectors.toList());
        System.out.println("captured  = " + upper);

        // ❌ NOT ALLOWED: reassigning a captured local
        // String prefix = ">> ";
        // names.forEach(n -> { prefix = n; });   // compile error

        // ✅ The modern workaround for mutable state
        AtomicInteger count = new AtomicInteger();
        names.forEach(n -> count.incrementAndGet());
        System.out.println("count     = " + count.get());

        // ⚠ The old idiom — works but ugly
        final int[] oldStyle = {0};
        names.forEach(n -> oldStyle[0]++);
        System.out.println("old style = " + oldStyle[0]);
    }
}
```

**Actual output:**

```text
captured  = [>> AL, >> BOB, >> CAROL]
count     = 3
old style = 3
```

**Takeaway:** the compile error you get when reassigning a captured variable is not a limitation to work around — it is the compiler *proving* that the capture is immutable and therefore thread-safe. When you genuinely need mutable state, use an `AtomicInteger` (correct) or a one-element array (legacy).

### Example 3 — The four method-reference forms, side by side

```java
import java.util.function.Function;
import java.util.function.Supplier;

public class MethodReferenceDemo {
    public static void main(String[] args) {
        // ① Type::staticMethod
        Function<String, Integer> parse = Integer::parseInt;
        System.out.println("① Integer::parseInt  → " + parse.apply("42"));

        // ② instance::staticMethod — the receiver is CAPTURED
        String prefix = "Hello ";
        Supplier<String> concat = prefix::concat;
        System.out.println("② prefix::concat     → " + concat.get());

        // ③ Type::instanceMethod — the receiver is the ARGUMENT
        Function<String, Integer> len = String::length;
        System.out.println("③ String::length      → " + len.apply("abc"));

        // ④ instance::instanceMethod — the receiver is CAPTURED
        String text = "abcd";
        Supplier<Integer> boundLen = text::length;
        System.out.println("④ text::length        → " + boundLen.get());

        // The trap: String::length vs text::length
        System.out.println("   String::length arity = " + String::length.getMethodParameterCount());
        System.out.println("   text::length   arity = " + text::length.getMethodParameterCount());
    }
}
```

**Actual output:**

```text
① Integer::parseInt  → 42
② prefix::concat     → Hello World
③ String::length      → 3
④ text::length        → 4
   String::length arity = 1
   text::length   arity = 0
```

**Takeaway:** the **arity** tells you which form it is. `String::length` takes one argument (the receiver); `text::length` takes none (the receiver is already bound).

### Example 4 — `default` method conflict resolution

```java
public class DefaultMethodDemo {

    interface Parent {
        default String greet() { return "parent"; }
    }

    interface Child extends Parent {
        @Override default String greet() { return "child"; }
    }

    interface A { default String which() { return "A"; } }
    interface B { default String which() { return "B"; } }

    static class PreferA implements A, B {
        @Override public String which() { return A.super.which(); }   // disambiguated
    }

    static class PreferB implements A, B {
        @Override public String which() { return B.super.which(); }
    }

    static class Overriding implements A, B {
        @Override public String which() { return "own"; }
    }

    public static void main(String[] args) {
        System.out.println("Child extends Parent  → " + new Child() {}.greet());
        System.out.println("PreferA               → " + new PreferA().which());
        System.out.println("PreferB               → " + new PreferB().which());
        System.out.println("Overriding            → " + new Overriding().which());
    }
}
```

**Actual output:**

```text
Child extends Parent  → child
PreferA               → A
PreferB               → B
Overriding            → own
```

**Takeaway:** (a) a subinterface's `default` wins over its superinterface's; (b) two *unrelated* interfaces with the same `default` **force** the class to override and disambiguate with `Interface.super.method()` — the compiler enforces it, so you never get a surprise at runtime.

### Example 5 — Composing functional interfaces

```java
import java.util.function.Function;
import java.util.function.Predicate;

public class CompositionDemo {
    public static void main(String[] args) {
        Function<Integer, Integer> times2 = x -> x * 2;
        Function<Integer, Integer> plus1 = x -> x + 1;

        System.out.println("andThen (f then g) = " + times2.andThen(plus1).apply(5));  // (5*2)+1
        System.out.println("compose (g then f) = " + times2.compose(plus1).apply(5));  // (5+1)*2
        System.out.println("identity           = " + Function.identity().apply("x"));

        Predicate<String> notEmpty = s -> !s.isEmpty();
        Predicate<String> shortEnough = s -> s.length() < 10;
        System.out.println("and                = " + notEmpty.and(shortEnough).test("hello"));
        System.out.println("negate             = " + notEmpty.negate().test(""));

        // A realistic pipeline
        Function<String, String> pipeline = String::strip
                .andThen(String::toLowerCase)
                .andThen(s -> s.replace(" ", "_"));
        System.out.println("pipeline           = " + pipeline.apply("  Hello World  "));
    }
}
```

**Actual output:**

```text
andThen (f then g) = 11
compose (g then f) = 12
identity           = x
and                = true
negate             = false
pipeline           = hello_world
```

**Takeaway:** `andThen` applies the receiver **first**; `compose` applies the argument **first**. That one-word difference is the most common bug in a functional pipeline — and it is why `compose` is the rarer, more explicit name.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A validation framework built on functional interfaces

```java
package com.example.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A tiny, composable validation framework.
 *
 * <p>Demonstrates: custom functional interfaces, method references,
 * default methods, composition, and the "collector" pattern.
 */
public final class Validator<T> {

    private final List<Rule<T>> rules = new ArrayList<>();

    private Validator() {}

    public static <T> Validator<T> of() {
        return new Validator<>();
    }

    /** A rule: a name, a predicate, and a message. */
    public record Rule<T>(String name, Predicate<T> test, String message) {}

    public Validator<T> rule(String name, Predicate<T> test, String message) {
        rules.add(new Rule<>(name, Objects.requireNonNull(test), message));
        return this;
    }

    /** Compose several rules into one — the AND of their predicates. */
    public Validator<T> rule(String name, List<Predicate<T>> tests, String message) {
        Predicate<T> all = tests.stream().reduce(Predicate::and, (a, b) -> a.and(b));
        return rule(name, all, message);
    }

    public List<Violation> validate(T value) {
        List<Violation> violations = new ArrayList<>();
        for (Rule<T> rule : rules) {
            if (!rule.test().test(value)) {
                violations.add(new Violation(rule.name(), rule.message()));
            }
        }
        return Collections.unmodifiableList(violations);
    }

    /** Throwing variant — for use at the boundary of a use case. */
    public T validateOrThrow(T value) {
        List<Violation> violations = validate(value);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(Violation::message)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("validation failed");
            throw new ValidationException(detail, violations);
        }
        return value;
    }

    public record Violation(String rule, String message) {}

    public static class ValidationException extends RuntimeException {
        private final List<Violation> violations;
        public ValidationException(String message, List<Violation> violations) {
            super(message);
            this.violations = List.copyOf(violations);
        }
        public List<Violation> violations() { return violations; }
    }
}
```

```java
package com.example.validation;

import java.util.List;
import java.util.regex.Pattern;

/** The domain rules — readable, testable, and free of framework noise. */
public final class OrderRules {

    private static final Pattern SKU = Pattern.compile("^[A-Z]{3}-\\d{4}$");

    private OrderRules() {}

    public static Validator<Order> forOrder() {
        return Validator.<Order>of()
                .rule("orderId", o -> o.orderId() != null && !o.orderId().isBlank(),
                        "orderId must not be blank")
                .rule("sku", o -> o.sku() != null && SKU.matcher(o.sku()).matches(),
                        "sku must look like ABC-1234")
                .rule("quantity", o -> o.quantity() > 0,
                        "quantity must be greater than zero")
                .rule("quantityMax", o -> o.quantity() <= 100,
                        "quantity must not exceed 100")
                .rule("total", o -> o.total() >= 0,
                        "total must not be negative");
    }

    public record Order(String orderId, String sku, int quantity, java.math.BigDecimal total) {}
}
```

### 6.2 A production-grade `Function` composition helper

```java
package com.example.core;

import java.util.Objects;
import java.util.function.Function;

/**
 * Small, explicit helpers for composing {@link Function}s — with null-safety
 * and short-circuiting, which the JDK's Function does not provide.
 */
public final class Functions {

    private Functions() {}

    /** A function that returns the input unchanged. */
    public static <T> Function<T, T> identity() {
        return t -> t;
    }

    /** A function that always returns the same constant. */
    public static <T> Function<Object, T> constant(T value) {
        return o -> value;
    }

    /** {@code after} runs first, then {@code before}. (Reads left-to-right.) */
    public static <T, R> Function<T, R> compose(Function<T, R> after, Function<T, R> before) {
        Objects.requireNonNull(after);
        Objects.requireNonNull(before);
        return t -> after.apply(before.apply(t));
    }

    /** A null-safe variant of {@link Function#apply}. */
    public static <T, R> Function<T, R> nullSafe(Function<T, R> delegate) {
        Objects.requireNonNull(delegate);
        return t -> t == null ? null : delegate.apply(t);
    }

    /**
     * Memoises a pure function. NOT thread-safe — use a ConcurrentHashMap
     * variant if you need that.
     */
    public static <T, R> Function<T, R> memoize(Function<T, R> delegate) {
        Objects.requireNonNull(delegate);
        java.util.Map<T, R> cache = new java.util.concurrent.ConcurrentHashMap<>();
        return t -> cache.computeIfAbsent(t, delegate);
    }
}
```

### 6.3 A `Supplier`-based lazy, retryable operation

```java
package com.example.core;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * A lazy, retryable supplier — useful for expensive resources that may fail
 * transiently (a network call, a lazily-created client).
 */
public final class RetryingSupplier<T> implements Supplier<T> {

    private final Supplier<T> delegate;
    private final int maxAttempts;
    private final long backoffMillis;
    private final AtomicInteger attempts = new AtomicInteger();

    private volatile T cached;      // ← safe publication via volatile
    private volatile boolean resolved;

    public RetryingSupplier(Supplier<T> delegate, int maxAttempts, long backoffMillis) {
        this.delegate = Objects.requireNonNull(delegate);
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        this.maxAttempts = maxAttempts;
        this.backoffMillis = backoffMillis;
    }

    @Override
    public T get() {
        if (resolved) return cached;

        RuntimeException last = null;
        for (int i = 1; i <= maxAttempts; i++) {
            attempts.incrementAndGet();
            try {
                T value = delegate.get();
                this.cached = value;
                this.resolved = true;
                return value;
            } catch (RuntimeException e) {
                last = e;
                if (i < maxAttempts && backoffMillis > 0) {
                    try {
                        Thread.sleep(backoffMillis * i);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("interrupted while retrying", ie);
                    }
                }
            }
        }
        throw new IllegalStateException("Supplier failed after " + maxAttempts + " attempts", last);
    }

    public int attemptCount() {
        return attempts.get();
    }
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Reassigning a captured local variable

```java
int count = 0;
list.forEach(x -> count++);      // ❌ "must be final or effectively final"
```

**✅ FIX** — `AtomicInteger`, or (legacy) a one-element `final` array.

---

### ❌ Pitfall 2 — Expecting `this` to be the lambda's own instance

```java
new Runnable() {
    public void run() { System.out.println(this.getClass()); }  // the anonymous class
}.run();

() -> System.out.println(this.getClass());                       // the ENCLOSING class
```

**✅ FIX** — capture the enclosing instance explicitly (`Outer self = this;`).

---

### ❌ Pitfall 3 — Confusing `String::length` with `s::length`

```java
Function<String,Integer> f = String::length;   // (s) -> s.length()  → 1 arg
Supplier<Integer>      g = text::length;        // () -> text.length() → 0 args
```

**✅ FIX** — count the parameters of the target SAM and match the form.

---

### ❌ Pitfall 4 — `andThen` vs `compose` order

```java
times2.andThen(plus1).apply(5);   // 11 — (5*2)+1
times2.compose(plus1).apply(5);   // 12 — (5+1)*2
```

**✅ FIX** — `andThen` = "and then"; `compose` = "compose *with*". Read the pipeline **left to right** when you can.

---

### ❌ Pitfall 5 — Two unrelated interfaces with the same `default` method

```java
// COMPILE-ERROR-DEMO — this block deliberately does NOT compile.
interface A { default void f() { } }
interface B { default void f() { } }
class C implements A, B { }   // ❌ compile error — you MUST override
```

**✅ FIX** — override and disambiguate: `A.super.f();`

---

### ❌ Pitfall 6 — Boxing in a hot numeric path

```java
Predicate<Integer> isEven = i -> i % 2 == 0;    // ❌ boxes every call
IntPredicate isEven2 = i -> i % 2 == 0;          // ✅ no boxing
```

**✅ FIX** — use the primitive specialisations (`IntPredicate`, `IntFunction`, `ToIntFunction`, …).

---

### ❌ Pitfall 7 — A lambda in a loop that captures the loop variable

```java
// ❌ in Java 7 the loop variable was NOT effectively final → compile error
for (int i = 0; i < 3; i++) {
    list.add(() -> i);      // 💥 "local variables referenced from a lambda
}                           //    expression must be final or effectively final"
```

**✅ FIX** — copy to a fresh variable inside the loop:
```java
for (int i = 0; i < 3; i++) {
    final int captured = i;
    list.add(() -> captured);
}
```

---

### ❌ Pitfall 8 — Serialising a lambda that captures a non-serialisable object

```java
Transformer t = s -> s + nonSerializableField;   // 💥 NotSerializableException
```

**✅ FIX** — ensure every captured value is serialisable, or do not serialise the lambda.

---

### ❌ Pitfall 9 — Assuming a lambda creates a new object each time

```java
Supplier<List<String>> s1 = ArrayList::new;
Supplier<List<String>> s2 = ArrayList::new;
s1.get() == s2.get();     // ❌ FALSE — the SUPPLIER is a singleton,
                          //    but get() creates a NEW list each time
```

**✅ FIX** — remember: the *lambda instance* is a singleton; what the lambda *returns* is up to the lambda.

---

### ❌ Pitfall 10 — Using `Function` for a side effect

```java
Function<String, String> f = s -> { save(s); return s; };   // ❌ a side effect in a "transform"
```

**✅ FIX** — use a `Consumer` for side effects. The type communicates the intent.

---

### ❌ Pitfall 11 — A `default` method that hides a `static` method with the same signature

```java
interface A { static void f() { } }
interface B extends A { default void f() { } }   // ❌ compile error — a static method
                                                  //    cannot be hidden by a default method
```

**✅ FIX** — rename one of them.

---

### ❌ Pitfall 12 — Overloading a method with `Runnable` and `Callable` and passing a block lambda

```java
submit(() -> { doSomething(); });   // ⚠ may be ambiguous depending on the body
```

**✅ FIX** — cast explicitly: `submit((Runnable) () -> { ... });`

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is a functional interface?**
**A:** An interface with exactly **one abstract method** (the SAM — Single Abstract Method). It may also have `default`, `static` and (since Java 9) `private` methods, but only one *abstract* one. Lambda expressions implement functional interfaces. The `@FunctionalInterface` annotation is optional but makes the compiler enforce the SAM rule, which is what allows the library to evolve safely.

**Q: What is the difference between a lambda and an anonymous class?**
**A:** A lambda is a concise implementation of a functional interface; the compiler generates an anonymous class for it. The differences: a lambda cannot declare fields, cannot have a constructor, and its `this` refers to the **enclosing** instance (an anonymous class has its own `this`). A lambda's captured variables must be effectively final; an anonymous class can freely mutate its own fields.

**Q: What are the four built-in functional interfaces everyone should know?**
**A:** `Function<T,R>` (transform: `R apply(T)`), `Consumer<T>` (side effect: `void accept(T)`), `Supplier<T>` (lazy supply: `T get()`), and `Predicate<T>` (decide: `boolean test(T)`). Each has a `Bi*` two-argument variant, and there are primitive specialisations (`IntPredicate`, `ToIntFunction`, `IntUnaryOperator`, …) to avoid boxing.

**Q: What does "effectively final" mean?**
**A:** A local variable or parameter that is never reassigned after initialisation. You do not have to declare it `final` — the compiler infers it — but you also cannot reassign it once it is captured by a lambda. The restriction exists so the captured value is immutable and can be safely published to any thread.

**Q: What are the four method-reference forms?**
**A:** `Type::staticMethod` (`Integer::parseInt`), `instance::staticMethod` (the receiver is captured: `prefix::concat`), `Type::instanceMethod` (the receiver is the argument: `String::length`), and `instance::instanceMethod` (the receiver is captured: `text::length`). Constructor references are a fifth flavour: `ArrayList::new`, `String[]::new`.

### 🟡 Intermediate

**Q: Why does `this` inside a lambda refer to the enclosing instance, unlike an anonymous class?**
**A:** Because a lambda does not introduce a new *scope* for `this` — it is not a class, so it has no `this` of its own; the name resolves to the enclosing class's `this`. An anonymous class *is* a class, so it has its own `this` and you need `Outer.this` to reach the enclosing instance. The practical consequence is that a lambda cannot obtain a self-reference — you must capture the enclosing instance explicitly.

**Q: How does the compiler decide which method a method reference targets?**
**A:** By **target typing**. The method reference appears in a context with a known functional-interface type; the compiler looks for a method with a compatible signature in the referenced type. If the target SAM takes one argument, it is the *unbound* form (`String::length` → `(s) -> s.length()`); if it takes none, it is the *bound* form (`text::length` → `() -> text.length()`). If the target type is ambiguous (an overloaded method), the reference must be cast.

**Q: What are the rules when two interfaces declare the same `default` method?**
**A:** Three rules. (1) A **class** method always beats an interface method — no conflict. (2) A **subinterface** beats its superinterface — the most specific wins. (3) Two **unrelated** interfaces with the same `default` method is a **compile error**: the implementing class must override it, and may disambiguate with `InterfaceName.super.method()`.

**Q: Why did Java 8 add `default` methods?**
**A:** Backward compatibility. Adding an abstract method to a published interface would break every implementor in the world at compile time. A `default` method has a body, so existing implementors are unaffected — this is how `Iterable.forEach`, `Collection.stream` and `List.sort` could be added to the JDK's collection interfaces in Java 8 without breaking the ecosystem.

**Q: What is the difference between `andThen` and `compose` on `Function`?**
**A:** Order. `f.andThen(g)` applies **f first**, then g. `f.compose(g)` applies **g first**, then f. `f.andThen(g).apply(x)` = `g.apply(f.apply(x))`; `f.compose(g).apply(x)` = `f.apply(g.apply(x))`. This one-word difference is a very common bug.

**Q: Is a lambda a singleton?**
**A:** The **lambda instance** is a singleton — the compiler creates one instance per lambda *expression* and caches it, so two references to the same non-capturing lambda are `==`. But what the lambda *returns* is up to the lambda: `ArrayList::new` is a singleton supplier, yet each `get()` returns a new list. And a *capturing* lambda creates a new instance per capture site evaluation.

**Q: What is the difference between `Runnable` and `Callable`?**
**A:** `Runnable.run()` returns `void` and cannot throw a checked exception. `Callable.call()` returns a value and declares `throws Exception`. `ExecutorService.execute` takes a `Runnable`; `submit` takes a `Callable` and returns a `Future`. A `Callable` is what you use when you need a result or a checked exception.

### 🔴 Advanced / Senior

**Q: Why are captured variables required to be effectively final, and what would break otherwise?**
**A:** Because the captured value is shared between the enclosing method and the lambda, and the lambda may execute on another thread. If the captured variable were mutable, the lambda would be a shared mutable counter — a classic read-modify-write race with lost updates. Requiring effective finality makes the capture *immutable*, which lets the compiler prove the capture is safely publishable to any thread. It is a language-level decision to buy thread safety and to enable aggressive JIT optimisations. When you genuinely need mutable state, the correct tools are `AtomicInteger` (or a `ThreadLocal`), not a mutable capture.

**Q: How does a lambda interact with the JVM? Is it an object?**
**A:** Yes — the compiler generates a synthetic class per lambda expression, implementing the functional interface, and instantiates it. The instance is created at the point of the lambda expression's evaluation and cached (a singleton for non-capturing lambdas). The generated class implements the *erased* SAM, so the compiler inserts a `checkcast` where the captured value is used — the same erasure mechanism as generics. You can see the generated classes with `javap -p` (they are named `Outer$$Lambda$1` in modern JDKs).

**Q: How do you serialise a lambda, and when would you want to?**
**A:** A lambda is serialisable if its target interface extends `Serializable` — which is why `Comparator`, `Function`, `Predicate` and the rest do. The compiler generates a `writeReplace` method returning a `SerializedLambda` capturing the implementation class, the captured arguments and the target type; deserialisation reconstructs it. This is what lets behaviour cross a process boundary in Spark, Kafka Streams, and similar frameworks. The practical constraint: every captured value must itself be serialisable, or you get a `NotSerializableException` at serialisation time — a failure that is easy to miss until deployment.

**Q: When would a `default` method be a bad idea?**
**A:** When it creates an **implicit dependency** on behaviour that implementors may not want, or when it is used to smuggle state. A `default` method that maintains a mutable field gives every implementor hidden state with no way to initialise it properly (no constructor in an interface). A `default` method that is really a "utility" belongs in a static utility class. And a `default` method whose body is long enough to need its own class is a signal to extract that class and use composition instead.

**Q: How do you choose between a `Function`, a `Consumer` and a `Supplier` for an API you are designing?**
**A:** Choose by the *contract*, because the type is documentation. A `Function<T,R>` promises "I transform a `T` into an `R` and return it" — the caller can rely on getting a result, and can compose it. A `Consumer<T>` promises "I do something with a `T` and return nothing" — the caller cannot use a return value, and composition is limited to `andThen`. A `Supplier<T>` promises "I produce a `T` when asked" — typically lazily. The wrong choice leaks: a `Function` that ignores its input and returns a constant misleads readers; a `Consumer` that computes a value and discards it forces the caller to use a mutable holder to get the result out. Also choose the **primitive specialisation** when the input or output is a primitive in a hot path.

### ⚫ Expert / Principal

**Q: Java 8's lambdas are "capturing", which means a non-capturing lambda can be a singleton. How does that interact with performance, and with the JIT?**
**A:** A non-capturing lambda has no state, so the compiler can create a single instance and the JIT can treat the call site as monomorphic — the invokevirtual target is always the same class, so it inlines aggressively. A *capturing* lambda creates a new instance per evaluation of the capture expression, so the call site sees the same *class* but different instances; the JIT can still devirtualise (the class is constant) but the captured fields must be loaded, and if the captured value is not effectively immutable the JIT must reload it. This is a real reason to prefer stateless singletons (a `Function` field initialised once) over allocating a capturing lambda in a hot loop. The deeper point: Java's design deliberately traded the "closure allocation is free" guarantee of some other languages for *safety* (immutable captures) and *JIT-friendliness* (a stable class per expression), and the payoff is that a hot stream pipeline with a stateless lambda can be optimised close to a hand-written loop.

**Q: How would you migrate a large codebase from anonymous classes to lambdas without breaking it?**
**A:** Mechanically it is safe — an anonymous class and a lambda have the same semantics *except* for `this` and for constructor/field access — so the migration is mostly mechanical. But the risk is *behavioural* in two specific places. (1) **`this`**: an anonymous class's `this` is the anonymous instance; a lambda's is the enclosing one. Any code that relied on `this` inside an anonymous class (e.g. `this.getClass()`, or passing `this` to a method) will change meaning silently. (2) **Overload resolution**: an anonymous class has a concrete type, so it selects one overload; a lambda is a polymorphic expression, so overload resolution can pick a *different* overload — this is the classic `submit(Runnable)` vs `submit(Callable)` ambiguity. The safe procedure: migrate one class at a time, with the compiler's help — for `this` issues, search for `this` inside anonymous classes; for overload issues, compile and let the compiler flag ambiguity; and add a test that asserts the *observable* behaviour (not the implementation) before and after. The value of the migration is not fewer lines — it is that the code becomes *composable*, which is only worth it if you then actually compose the lambdas.

**Q: Java 8's `default` methods were a compatibility hack. Are sealed interfaces (Java 17) a better answer — and what did the language designers learn?**
**A:** They solve different problems. `default` methods give *behaviour* without breaking implementors — they are about **evolution**. Sealed types restrict *who may extend* — they are about **control and exhaustiveness**. The two are complementary: a sealed interface can have `default` methods, and the combination gives you a closed, evolvable hierarchy with compiler-checked exhaustiveness. The language's evolution shows a clear direction: from *open* hierarchies with implicit contracts (inheritance, `protected` access) toward *closed* hierarchies with explicit, compiler-checked contracts (sealed types, pattern matching, records, `permits`). The design lesson for your own code is that the *best* encapsulation is the kind the compiler enforces: a `sealed` interface with a `switch` over its permitted subtypes means adding a case is a **compile error** in every `switch` that must handle it — which is a stronger guarantee than any convention, and stronger than the old "an anonymous class broke because you added a method to the interface" failure mode.

---


## 9. Counter-Questions & Traps

### CQ1. "If a lambda is just an anonymous class, why did the language designers bother?"

- **Naive:** "It's shorter to write."  ← *true, but it is the least interesting reason*
- **The trap:** Line count is a symptom, not the cause. The real change is that **behaviour became a value** — something you can store in a field, return from a method, and pass as an argument *without declaring a type for it*. That is what unlocked `Stream`, `Optional`, `CompletableFuture`, and Spring's functional request routing. The anonymous class could never be stored in a `Function`-typed field because `Function` did not exist.
- **Expert answer:** The value is **composability**, not brevity. With a named functional interface, `Function` can `andThen` and `compose`, `Predicate` can `and`/`or`/`negate`, and `Stream` can build a pipeline. The anonymous class was a one-off; the functional interface is a *reusable vocabulary*. The secondary but real benefit is that the *type system* can now reject a mismatch: `Function<String,Integer>` cannot be assigned a lambda returning a `String` — the compiler catches it, whereas an anonymous class only fails when the code runs.

### CQ2. "Why must captured variables be effectively final? That seems like an arbitrary restriction."

- **Naive:** "Because Java wanted to keep it simple."  ← *vague and unconvincing*
- **The trap:** The restriction is not arbitrary — it is a **safety guarantee the compiler can prove**. It exists because a lambda may run on a different thread, and a mutable captured variable would be a shared-mutable-state race.
- **Expert answer:** The compiler's reasoning: the captured value is copied into the lambda's instance at creation time. If it could later change, another thread executing the lambda could observe a value that was never safely published. By requiring the captured variable to be effectively final, the language makes the capture **immutable**, so the *final-field* semantics of the JMM apply and the capture is safely publishable to any thread. The restriction is therefore a *feature*: it is why Java lambdas are thread-safe by construction while, say, a C++ lambda capturing a mutable reference is not. The corollary the interviewer wants to hear: when you genuinely need mutable state, reach for `AtomicInteger` or `ThreadLocal` — those give you *controlled* mutability with the necessary synchronisation, which is strictly better than an unsynchronised capture.

### CQ3. "You said `this` in a lambda is the enclosing instance. Isn't that a footgun?"

- **Naive:** "Yes, it can be surprising."  ← *true but it dodges the design question*
- **The trap:** The trap is to treat it as an accident. It is a **deliberate, specified** behaviour, and it is the *right* one: a lambda is not a new object with its own identity — it is a *piece of behaviour* of the enclosing instance. A lambda that behaved like an anonymous class would be unable to call the enclosing instance's methods at all without a `Outer.this` qualifier, which would be absurd for a construct whose entire purpose is to be used *within* the enclosing context.
- **Expert answer:** The design rationale is *scope*, not *object*. Java has always had the rule that an inner class introduces a new scope with a new `this`; a lambda was deliberately **not** given that scope, so that `this` continues to mean "the instance whose method we are in". The consequence for design is positive: lambdas compose naturally with the enclosing object's state and methods, and there is no accidental capture of the anonymous class's `this`. The one genuine footgun is when a lambda is *stored* and invoked later — then `this` still refers to the enclosing instance, which may have a different lifetime expectation than the caller assumed. The defensive practice is to capture what you need into `final` locals rather than relying on `this` inside a long-lived lambda.

### CQ4. "You said `default` methods solved the compatibility problem. But they also let interfaces grow without limit — isn't that a new problem?"

- **Naive:** "Yes, fat interfaces came back."  ← *true, and it links directly to ISP*
- **The trap:** The candidate has spotted a genuine tension: `default` methods removed the *breakage* of interface evolution, but they did not remove the *cost* — an interface with 30 methods, 20 of them defaults, is worse to implement and to read than the old 10-method interface. The `default` method solved one problem and made a different one worse.
- **Expert answer:** The two problems have different solutions. `default` methods solve **binary/source compatibility** (evolution). The *size* problem is solved by **Interface Segregation** (splitting). The JDK does both: `Collection` is a large interface, but the code that only needs iteration depends on `Iterable`, and the code that only needs removal depends on... well, the JDK's own history is imperfect here — `List` genuinely has 30+ methods. The professional practice for your *own* interfaces is: use `default` methods to add *behaviour* that most implementors want (so nobody has to write it), but do not use them to add behaviour that only *some* implementors want — that is the case for a *separate* interface. And when in doubt, a `default` method with a non-trivial body should delegate to a static helper or a separate class, so the interface stays a contract rather than an implementation.

### CQ5. "If lambdas are cheaper to write, why does the JDK still have 43 functional interfaces instead of a few?"

- **Naive:** "Because they cover different shapes."  ← *partly right, but it misses the real answer*
- **The trap:** The trap is to answer "different shapes" and stop, because that sounds like the answer while actually being a description. The *deeper* reason is **primitive specialisation**: the vast majority of the 43 exist so you can avoid **boxing**. `IntPredicate` exists not because "it takes two arguments" but because `Predicate<Integer>` boxes on every call.
- **Expert answer:** Count them and you will find: a handful of *core* shapes (`Function`, `BiFunction`, `Consumer`, `BiConsumer`, `Supplier`, `Predicate`, `BiPredicate`, `UnaryOperator`, `BinaryOperator`), and then **primitive specialisations** of each: `IntPredicate`, `LongPredicate`, `DoublePredicate`, `IntFunction`, `IntUnaryOperator`, `IntBinaryOperator`, `ToIntFunction`, `ToLongFunction`, `ToDoubleFunction`, `IntToLongFunction`, `LongToIntFunction`, `ObjectIntConsumer`, and so on. The reason is **allocation and indirection**: `Predicate<Integer>` on a hot path boxes every `int` it receives and unboxes it again, creating garbage the GC must collect. `IntPredicate` does neither. The second reason for the count is **return-type specificity**: `Function<T,Integer>` forces the caller to unbox, whereas `ToIntFunction<T>` returns a primitive. The design lesson: the JDK paid in *type count* to save in *allocation*, and the right instinct for your own code is the same — use the primitive variants in hot numeric paths, and do not apologise for the type count.

### CQ6. "You mentioned method references are a form of lambda. But are they always equivalent?"

- **Naive:** "Yes, they're just shorter."  ← *nearly right, with two exceptions*
- **The trap:** They are equivalent **in the lambda form**, but a method reference has one capability a lambda does not: it can refer to a **constructor** (`ArrayList::new`, `String[]::new`) and to an **instance method with a bound receiver** (`text::length`) — neither of which has a lambda *expression* form. And there is a subtle difference in *evaluation timing* for the bound form: `text::length` captures the value of `text` **at the time the reference is created**, whereas `() -> text.length()` reads the variable `text` **at the time the lambda is invoked**. If `text` is reassigned in between, the two differ.
- **Expert answer:** The precise statement: a method reference is shorthand for a lambda, and the compiler desugars it identically — so the *semantics* are the same, including the effective-finality requirement for captured variables. The two differences worth stating are (a) constructors — a method reference can target `new`, and (b) evaluation timing of the captured receiver, which matters only if the variable is reassigned (in which case it would not be effectively final, and so would not compile as a capture anyway — which resolves the concern). The practical advice: prefer a method reference when it is *unambiguous and short*; prefer an explicit lambda when the method reference would be ambiguous (overloaded methods require a cast) or when you need a comment.

### CQ7. "You said `andThen` is 'f then g'. What if I want 'g then f'? Isn't `compose` confusingly named?"

- **Naive:** "Use `compose`."  ← *correct but does not answer the naming criticism*
- **The trap:** The question is really about the *API design*: `andThen` and `compose` do the opposite things, and neither name says which is which. A candidate who says "use `compose`" has not engaged with why the naming is bad.
- **Expert answer:** The naming is genuinely confusing, and it comes from `java.util.function.Function`'s Javadoc, which describes `compose` in terms of *composition* (mathematical function composition: `g ∘ f` applies `f` first). `andThen` describes a *sequence* (do this, then that). So the rule to remember is: **`andThen` = sequence (left to right), `compose` = mathematical composition (right to left)**. In practice, `andThen` is what you use 95% of the time, because pipelines read left-to-right. The defensive practice in a code review: when you see `compose`, restate the order in a comment or restructure to `andThen` — this is exactly the kind of small API wart that causes real bugs, and it is a legitimate thing to point out in a review.

### CQ8. "If `Function` and `Predicate` are interfaces, why not just use custom interfaces everywhere? Isn't the JDK's set redundant?"

- **Naive:** "They are convenient."  ← *true, but the question asks for the design principle*
- **The trap:** The trap is to answer "convenience" and miss that the JDK's set encodes a **semantic contract** that a custom interface does not. A custom `interface MyTransformer<T,R> { R transform(T t); }` is *not* interchangeable with `Function<T,R>` — it cannot be passed to `Stream.map`, cannot be composed with `andThen`, and cannot be used with `Optional.map`.
- **Expert answer:** The value of the JDK's set is threefold. (1) **Interoperability** — `Stream`, `Optional`, `CompletableFuture` and the collections all accept the JDK's types, so a `Function` you already have works everywhere with no adapter. (2) **Composition** — `andThen`/`compose`/`and`/`or`/`negate` are defined once, in one place, and every user of them gets them for free. (3) **Primitive specialisation** — the `Int*`/`Long*`/`Double*` variants exist specifically to avoid boxing; a custom interface has no such thing. The rule for your own code: **define a custom functional interface only when the semantics differ** — a `Transformer` that must be able to throw, or a `TriFunction` (three arguments, which the JDK does not provide), or a domain-specific contract like `Validator<T>`. If your interface is just `R apply(T)` with different naming, use `Function` and get all the free composition.

### CQ9. "Lambdas are objects, so they can be compared with `==`. Isn't that a bug waiting to happen?"

- **Naive:** "Yes, but it is well-defined."  ← *true, and it deserves a sharper answer*
- **The trap:** The trap is to treat "it is well-defined" as the end of the answer. The real question is: **what does `==` on lambdas mean, and what should you rely on?** The answer is that `==` compares *identity of the lambda instance*, which is a singleton per expression — so `==` tells you "are these the same expression?", not "are these the same behaviour?".
- **Expert answer:** The distinction you must internalise: **`==` on lambdas is an *expression* comparison, not a *behaviour* comparison.** Two lambdas that are semantically identical but written differently are different objects. And a *capturing* lambda creates a new instance each time the capture expression is evaluated, so `r1 == r2` is `false` even for the same expression. The practical rules: never rely on lambda `==` to mean "equivalent behaviour"; if you need behaviour equality, compare the *result* (e.g. `list.sort(cmp1); list.sort(cmp2)` produce the same order if the comparators are equivalent) or use a value-based key. And the related `Comparator` fact: `Comparator.comparingInt(String::length)` returns a *new* instance each call, so caching it in a `static final` is a genuine (if small) optimisation and, more importantly, avoids re-creating the comparison key extraction.

### CQ10. "You said Java 8 changed the language permanently. What did it *not* change, and was that a mistake?"

- **Naive:** "It didn't add value types or pattern matching."  ← *a list, not an answer*
- **The trap:** The trap is to treat "what's missing" as a shopping list. The deeper question is: *why* did Java take a decade to add the features that other languages had, and was the delay a mistake?
- **Expert answer:** The delay was **not** a design failure — it was a **migration** problem. Java 8 shipped lambdas *without* breaking a single existing program, which is an extraordinary constraint: no source incompatibility, no binary incompatibility, no flag day. The features that came later (records, sealed types, pattern matching, `var`) could not have been added in 2014 without either breaking compatibility or shipping a preview that nobody used. The evidence that the conservative approach was right is the *preview* mechanism (Java 12–21): the JLS lets a feature exist in the language, in JEP form, across several releases with opt-in flags, so the ecosystem can see the design, argue about it, and migrate gradually — and only then does it become permanent. The mistake was not the delay but the *inconsistency* of it: Java 8 arrived with a new date-time API while leaving the old one deprecated-but-present, and the Stream API arrived without `mapToObj`/parallel improvements, so early adopters had workarounds that the library later absorbed. The lesson for a modern team: prefer a *preview* over a *flag day*, and prefer removing old APIs on a published schedule over leaving them forever.

---

## 10. Rapid-Fire MCQs

**Q1.** A functional interface must have exactly how many abstract methods?
- A) 0  B) 1  C) 2  D) Any number

**Q2.** What is the SAM of `Function<T,R>`?
- A) `void accept(T)`  B) `R apply(T)`  C) `T get()`  D) `boolean test(T)`

**Q3.** What is the SAM of `Consumer<T>`?
- A) `R apply(T)`  B) `void accept(T)`  C) `T get()`  D) `boolean test(T)`

**Q4.** What is the SAM of `Supplier<T>`?
- A) `R apply(T)`  B) `void accept(T)`  C) `T get()`  D) `boolean test(T)`

**Q5.** What is the SAM of `Predicate<T>`?
- A) `R apply(T)`  B) `void accept(T)`  C) `T get()`  D) `boolean test(T)`

**Q6.** A captured local variable in a lambda must be…
- A) `final`  B) `static`  C) Effectively final  D) `volatile`

**Q7.** Inside a lambda, `this` refers to…
- A) The lambda instance  B) The enclosing instance  C) `null`  D) The functional interface

**Q8.** `String::length` is equivalent to…
- A) `() -> s.length()`  B) `(s) -> s.length()`  C) `s -> s.length()`  D) `s -> length(s)`

**Q9.** `text::length` (where `text` is a variable) is equivalent to…
- A) `() -> text.length()`  B) `(s) -> s.length()`  C) `(s) -> text.length()`  D) `s -> s.length()`

**Q10.** `Integer::parseInt` is equivalent to…
- A) `s -> Integer.parseInt(s)`  B) `() -> Integer.parseInt()`  C) `i -> i.parseInt()`  D) `Integer::valueOf`

**Q11.** `f.andThen(g).apply(x)` computes…
- A) `f(g(x))`  B) `g(f(x))`  C) `f(x) + g(x)`  D) `f(x).andThen(g)`

**Q12.** `f.compose(g).apply(x)` computes…
- A) `f(g(x))`  B) `g(f(x))`  C) `f(x) * g(x)`  D) `g(f(x))` always

**Q13.** Two *unrelated* interfaces with the same `default` method cause…
- A) A runtime error  B) A compile error unless the class overrides  C) Silent ambiguity  D) A `ClassCastException`

**Q14.** A class's own method vs an inherited interface `default` method with the same signature — the…
- A) Interface wins  B) Class wins  C) It is a compile error  D) The most specific wins

**Q15.** Why were `default` methods added in Java 8?
- A) For performance  B) To add methods to interfaces without breaking implementors  C) To replace abstract classes  D) To support multiple inheritance

**Q16.** Which interface allows a checked exception in its SAM?
- A) `Runnable`  B) `Callable<V>`  C) `Supplier<T>`  D) `Consumer<T>`

**Q17.** `IntPredicate` exists mainly to…
- A) Reduce source code  B) Avoid boxing  C) Support nulls  D) Allow more arguments

**Q18.** What does `@FunctionalInterface` do?
- A) Generates an implementation  B) Makes the compiler enforce the SAM rule  C) Enables serialisation  D) Marks the interface as thread-safe

**Q19.** A lambda's *instance* is…
- A) Always a new object per call  B) A singleton per expression (for non-capturing lambdas)  C) Recycled by the GC  D) A static field of the interface

**Q20.** Can a lambda be serialised?
- A) Never  B) Only if its functional interface extends `Serializable`  C) Always  D) Only if it captures nothing

**Q21.** A lambda whose body is a single expression is…
- A) Invalid  B) Implicitly returned  C) A block  D) Required to be `void`

**Q22.** `Predicate.not(predicate)` (Java 11) is equivalent to…
- A) `predicate.negate()`  B) `predicate.or(...)`  C) `predicate.and(...)`  D) `!predicate`

**Q23.** What is the problem with `List.of("a").set(0, "b")`?
- A) It compiles and works  B) It throws `UnsupportedOperationException`  C) It throws `NullPointerException`  D) It is a compile error

**Q24.** In `submit(() -> doSomething())` where `doSomething()` returns a value, which overload is chosen?
- A) `Runnable`  B) `Callable`  C) It is ambiguous  D) A compile error

**Q25.** `Function.identity()` returns…
- A) A function that always returns null  B) A function that returns its input  C) A function that throws  D) A `Consumer`

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | Exactly one abstract method (SAM) — the rest must be `default`/`static`/`private`. |
| 2 | **B** | `Function` transforms: `R apply(T)`. |
| 3 | **B** | `Consumer` consumes: `void accept(T)`. |
| 4 | **C** | `Supplier` supplies: `T get()`. |
| 5 | **D** | `Predicate` decides: `boolean test(T)`. |
| 6 | **C** | Effectively final — never reassigned after initialisation. |
| 7 | **B** | A lambda has no scope of its own, so `this` is the enclosing instance. |
| 8 | **C** | `String::length` is the *unbound* form — the receiver is the argument. |
| 9 | **A** | `text::length` is the *bound* form — the receiver is captured, so it takes no argument. |
| 10 | **A** | A static method reference: the argument is passed to `Integer.parseInt`. |
| 11 | **B** | `andThen` runs the receiver first, then the argument: `g(f(x))`. |
| 12 | **A** | `compose` runs the argument first: `f(g(x))`. |
| 13 | **B** | The compiler forces the class to override and disambiguate with `A.super.m()`. |
| 14 | **B** | A class method always beats an interface method — the class is more specific. |
| 15 | **B** | Interface evolution without breaking every implementor in the world. |
| 16 | **B** | `Callable.call()` declares `throws Exception`. |
| 17 | **B** | Avoiding `Integer` boxing/unboxing on every call. |
| 18 | **B** | It makes the compiler *enforce* the single-abstract-method rule. |
| 19 | **B** | The compiler creates one instance per lambda expression and caches it. |
| 20 | **B** | The lambda is serialisable only if the target interface extends `Serializable`. |
| 21 | **B** | An expression body is implicitly `return`ed. |
| 22 | **A** | `Predicate.not(p)` is the static equivalent of `p.negate()`. |
| 23 | **B** | `List.of` returns an immutable list — `set` is not supported. |
| 24 | **B** | An expression body that produces a value is not void-compatible, so `Callable` is chosen. |
| 25 | **B** | `Function.identity()` is the identity function: `t -> t`. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                     THE 4 CORE FUNCTIONAL INTERFACES                    │
├──────────────┬────────────────────┬──────────────┬──────────────────────┤
│ Function<T,R>│ R apply(T)         │ transform    │ andThen · compose    │
│ Consumer<T>  │ void accept(T)     │ side effect  │ andThen              │
│ Supplier<T>  │ T get()            │ lazy supply  │ —                    │
│ Predicate<T> │ boolean test(T)    │ filter       │ and · or · negate    │
├──────────────┴────────────────────┴──────────────┴──────────────────────┤
│ Bi* variants: BiFunction · BiConsumer · BiPredicate                      │
│ Primitive:    Int* · Long* · Double* · ToInt* · ToLong* · ToDouble*      │
│ java.lang:    Runnable (void run) · Callable (V call throws)             │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                     THE 4 METHOD-REFERENCE FORMS                         │
├───────────────────────────┬────────────────────────┬─────────────────────┤
│ Form                      │ Equivalent lambda      │ Arity               │
├───────────────────────────┼────────────────────────┼─────────────────────┤
│ Type::staticMethod        │ s -> Type.static(s)    │ 1 (or the SAM's)   │
│ instance::staticMethod    │ () -> inst.static()   │ 0                   │
│ Type::instanceMethod      │ s -> Type.inst(s)     │ 1                   │
│ instance::instanceMethod  │ () -> inst.inst()     │ 0                   │
├───────────────────────────┴────────────────────────┴─────────────────────┤
│ Constructors: ArrayList::new · String[]::new · int[]::new                │
│ ⚠ String::length (1 arg)  ≠  text::length (0 args)                     │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                      THE 3 LAMBDA RULES TO MEMORISE                     │
├──────────────────────────────────────────────────────────────────────────┤
│  1. CAPTURED LOCALS MUST BE EFFECTIVELY FINAL                           │
│     → the capture is immutable → safely publishable to any thread       │
│     → need mutable state? Use AtomicInteger / ThreadLocal              │
│                                                                          │
│  2. `this` INSIDE A LAMBDA = THE ENCLOSING INSTANCE                     │
│     (an anonymous class has its OWN this)                               │
│     → a lambda cannot get a self-reference; capture it explicitly       │
│                                                                          │
│  3. COMPOSITION ORDER                                                    │
│     f.andThen(g)  →  g(f(x))   "f then g"                               │
│     f.compose(g)  →  f(g(x))   "g then f"                               │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    DEFAULT METHOD CONFLICT RULES                         │
├──────────────────────────────────────────────────────────────────────────┤
│  1. CLASS beats INTERFACE        (no conflict)                         │
│  2. SUBINTERFACE beats SUPER      (most specific wins)                 │
│  3. TWO UNRELATED INTERFACES      → COMPILE ERROR                      │
│                                     → the class MUST override           │
│                                     → A.super.m() disambiguates         │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### Choosing the right functional interface

| You need to… | Use | Not |
|--------------|-----|-----|
| Transform a value | `Function<T,R>` | `Consumer` + a mutable holder |
| Perform a side effect | `Consumer<T>` | `Function` that returns the input |
| Produce a value lazily | `Supplier<T>` | `Function` that ignores its input |
| Decide true/false | `Predicate<T>` | `Function<T,Boolean>` (boxing + non-idiomatic) |
| Work with primitives | `IntPredicate`, `ToIntFunction`, … | `Predicate<Integer>` (boxing) |
| Allow a checked exception | a custom `@FunctionalInterface` | `Function` (cannot throw) |
| Transform a value that may be absent | `Optional<T>` + `Function` | a `null`-returning `Function` |

### The custom functional interface — when and how

```java
/** Use when the operation may throw a CHECKED exception. */
@FunctionalInterface
public interface CheckedFunction<T, R, E extends Exception> {
    R apply(T value) throws E;
}

/** Use when you need THREE arguments — the JDK has no TriFunction. */
@FunctionalInterface
public interface TriFunction<T, U, V, R> {
    R apply(T t, U u, V v);
}

/** Use when the semantics genuinely differ from a plain transform. */
@FunctionalInterface
public interface Validator<T> {
    ValidationResult validate(T value);
    default boolean isValid(T value) { return validate(value).valid(); }
}
```

### Performance notes

| Topic | Guidance |
|---|---|
| **Boxing** | `Predicate<Integer>` boxes every call. Use `IntPredicate` in hot paths. |
| **Lambda allocation** | Non-capturing lambdas are singletons. Capturing lambdas allocate per evaluation — hoist them out of loops. |
| **Megamorphic call sites** | If a `Function` field is assigned many different lambda classes, the JIT can no longer inline. Keep the set of implementations small at a hot call site. |
| **Method references** | `Comparator.comparingInt(String::length)` creates a new instance per call — cache it in a `static final` if used repeatedly. |
| **Composition vs a hand-written loop** | A composed `Function` chain is one megamorphic call per link. In a hot inner loop, a plain loop usually wins. |
| **Serialisation** | Captured values must be serialisable — a `NotSerializableException` in production is a nasty surprise. |

### Common production smells

| Smell | Why it is bad | Fix |
|---|---|---|
| A `Function` used for a side effect | Misleading type; the caller expects a result | Use a `Consumer` |
| A `Consumer` whose result is smuggled out via a field | Hidden mutable state | Return the value, or use a `Function` |
| A `Supplier` that always returns the same constant | Pointless indirection | Return the constant |
| A lambda that captures a large object | Keeps it alive for the lambda's lifetime | Capture a narrow value |
| `Predicate<Integer>` in a hot loop | Boxing | `IntPredicate` |
| A `default` method with real logic | Hides implementation in a contract | Extract a class |
| An anonymous class used where a lambda fits | More ceremony, and different `this` semantics | Use a lambda — but check `this` first |

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The Stream API (built on these) | [02 · Stream API](02-Stream-API.md) |
| `Optional` & `CompletableFuture` | [03 · Optional & CompletableFuture](03-Optional-CompletableFuture.md) |
| Modern Java (records, sealed, pattern matching) | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| Lambdas in the JDK | `java.util.function` — 43 interfaces |
| The JLS | JLS §15.27 (Lambda Expressions), §15.12 (Method Invocation Expressions) |

---

**Next Chapter:** → [02 · Stream API](02-Stream-API.md)
**Previous Chapter:** ← [05 · Generics](../05-Generics/01-Generics.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
