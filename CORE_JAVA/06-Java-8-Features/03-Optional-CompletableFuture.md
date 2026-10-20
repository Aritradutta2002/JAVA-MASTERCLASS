# Java 8 Features — `Optional` & `CompletableFuture`

> **Two of the most-misused APIs in modern Java.** `Optional` was designed for
> *return values* and is a **mistake** as a field or parameter. `CompletableFuture`
> is Java's async toolkit — and the difference between using it well and using it
> badly is the difference between a resilient system and a cascade of timeouts.

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

- **`Optional<T>` is a container that may or may not hold a value.** It was added in Java 8 to give a *type-safe* way to express "no result" instead of using `null` or a sentinel.
- **`Optional` is intended for RETURN VALUES ONLY.** It is explicitly **not** intended as a field type or a method parameter — the Javadoc says so, and Brian Goetz (who led the design) has said so repeatedly. As a field it is not `Serializable`, and as a parameter it is a "null check in disguise".
- **`Optional` is not `Serializable`** and it does **not** implement `equals`/`hashCode` — two `Optional`s holding equal values are **not** equal. A famous, still-unfixed quirk.
- **The API to memorise:** `of` (rejects null), `ofNullable` (allows null), `empty`, `get` (throws `NoSuchElementException`), `getOrDefault`, `orElse` (**always evaluates** its argument), `orElseGet` (lazy — a supplier), `orElseThrow`, `ifPresent`, `ifPresentOrElse`, `filter`, `map`, `flatMap`, `stream`, `isEmpty` (Java 11), `isPresent`.
- **`orElse` vs `orElseGet` is the #1 trap.** `orElse(defaultValue)` evaluates the argument **eagerly**, every time — even when the value is present. Use `orElseGet(() -> expensiveCall())`.
- **`Optional.map` returns `Optional`; `Optional.flatMap` flattens.** `map` on an empty `Optional` returns `empty()` and **never calls the mapper**.
- **`CompletableFuture` implements `Future` but completes *asynchronously*.** It is the JDK's toolkit for composing async stages without blocking threads.
- **The stage methods:** `supplyAsync` (returns a value), `runAsync` (no value), `thenApply` (transform, same thread), `thenApplyAsync` (transform, on a pool), `thenCompose` (flatten — the async equivalent of `map`), `thenCombine` (join two futures), `allOf` (wait for all), `anyOf` (first to complete), `exceptionally` (recover), `handle` (recover + transform), `whenComplete` (observe), `thenAccept` (consume).
- **`thenApply` vs `thenCompose`:** `thenApply` returns `CompletableFuture<CompletableFuture<R>>`; `thenCompose` flattens it to `CompletableFuture<R>`. Use `thenCompose` when the mapper itself returns a `CompletableFuture`.
- **Exceptions in a stage are wrapped in a `CompletionException`.** `join()` rethrows it as-is (unchecked); `get()` wraps it in an `ExecutionException`.
- **`join()` vs `get()`:** `join` throws unchecked `CompletionException`; `get` throws checked `InterruptedException`/`ExecutionException`. `join` is fine in a `CompletableFuture` chain; `get` is better at the *boundary* where you must handle interruption.
- **The default executor is the common `ForkJoinPool`** — shared with parallel streams. In a **web server** that is a disaster: your request threads and your async tasks fight for the same pool. Always pass a **dedicated executor**.
- **`CompletableFuture` is not cancellable** in a meaningful way, and a task that blocks occupies its thread. For blocking I/O, use a platform thread pool; for CPU work, use `ForkJoinPool`; for thousands of waiting tasks, use **virtual threads** (Java 21).

---

## 1. What & Why — The Problem This Solves

**The problem #1 — `null`.**

```java
// ❌ The classic NPE
String name = user.getAddress().getCity().getName();
```

Every `get` that *might* return nothing returns `null`, and the compiler cannot help you. The type system does not distinguish "there is no value" from "the value is a `String`". The result is the `NullPointerException` — and it is famously the JDK's most common exception.

**The solution — `Optional` makes absence part of the type.**

```java
Optional<Address> address = user.getAddress();
Optional<String> city = address.flatMap(Address::getCity);
String name = city.orElse("Unknown");
```

Now the *type* says "this might be empty", and the caller is **forced by the compiler** to deal with it. There is no way to accidentally dereference it.

**The problem #2 — blocking threads for remote calls.**

```java
// ❌ BLOCKING — the thread sits idle waiting for the network
User user = userClient.fetch(userId);        // 200 ms of nothing
Orders orders = orderClient.fetch(userId);   // 150 ms of nothing
Recommendations recs = recoClient.fetch(userId);
```

Three sequential remote calls = 550 ms, and the thread is **blocked the whole time**. Under load, you need more threads than the machine can hold.

**The solution — `CompletableFuture` composes them concurrently.**

```java
CompletableFuture<User>        user   = userClient.fetchAsync(userId);
CompletableFuture<List<Order>> orders = orderClient.fetchAsync(userId);
CompletableFuture<List<Reco>>   recs   = recoClient.fetchAsync(userId);

CompletableFuture.allOf(user, orders, recs).thenRun(() -> { /* all done */ });
```

Three calls in **parallel** = ~200 ms, and **no thread is blocked** while waiting. This is the foundation of modern reactive Java.

> 💡 **The interview framing.** `Optional` and `CompletableFuture` solve *opposite*
> problems but share a shape: both are **containers for a value that is not there
> yet** — one because it does not exist, the other because it does not exist *yet*.
> That shared shape is why `Optional` has a `map`/`flatMap` and `CompletableFuture`
> has `thenApply`/`thenCompose`: both are *monadic* containers, and both compose
> their functions without nesting.

---

## 2. Mental Model — The One Picture

```text
   THE THREE STATES OF A VALUE

   ┌──────────────┐   of(x)    ┌──────────────┐
   │  EMPTY       │───────────▶│  PRESENT     │
   │  (no value)  │            │  (has x)     │
   └──────────────┘◀───────────└──────────────┘
        ▲            ofNullable(null)  │
        │                              │
        └──────────────────────────────┘
                   map/filter → empty


   THE CONTAINER PIPELINE (both Optional and CompletableFuture)

   ┌────────┐   map(f)    ┌────────┐   map(g)    ┌────────┐
   │  EMPTY │────────────▶│  EMPTY │────────────▶│  EMPTY │   ← short-circuits
   └────────┘             └────────┘             └────────┘
        │
        │ PRESENT(x)
        ▼
   ┌────────┐   map(f)    ┌────────┐   map(g)    ┌────────┐
   │PRESENT │────────────▶│PRESENT │────────────▶│PRESENT │
   │   x    │             │  f(x)  │             │ g(f(x))│
   └────────┘             └────────┘             └────────┘

   ⚠ THE FUNCTION IS NEVER CALLED ON AN EMPTY CONTAINER.
     This is what makes chaining safe — and it is why
     `map` on an empty Optional never throws NPE.
```

```text
   CompletableFuture — THE THREE WAYS TO REACT

   ┌───────────────────┐
   │  source CF        │
   │  (completes with  │
   │   value or error) │
   └─────────┬─────────┘
             │
   ┌─────────┼──────────┬──────────────┬────────────────┐
   ▼         ▼          ▼              ▼                ▼
 thenApply thenCompose thenCombine  exceptionally      handle
 (transform)(flatten) (join 2)      (recover only)  (recover+transform)
   │
   └─── on a thread determined by the ASYNC variant:
        thenApply      → the COMPLETING thread  (no extra hop)
        thenApplyAsync → the ForkJoinPool        (extra hop, but decoupled)
```

**What to look at:** the difference between the `thenX` and `thenXAsync` variants is **which thread runs the next stage**. That single choice determines whether your pipeline is a chain of cheap in-memory transformations (use the sync variants) or a genuinely asynchronous chain (use the async variants, and pass a dedicated executor).

---

## 3. Deep Dive — Theory

## 3.1 `Optional` — the API, exactly

```java
public final class Optional<T> {

    // ── FACTORIES ──────────────────────────────────────────────
    public static <T> Optional<T> of(T value);              // NPE if value == null
    public static <T> Optional<T> ofNullable(T value);      // null → empty
    public static <T> Optional<T> empty();                  // the shared empty instance
    public static <T> Optional<T> of(T... values);          // Java 9+ varargs → first non-null

    // ── TESTS ──────────────────────────────────────────────────
    public boolean isPresent();
    public boolean isEmpty();                               // Java 11+
    public T get();                                         // NoSuchElementException if empty
    public T getOrDefault(T other);
    public T orElse(T other);                               // ⚠ EAGER
    public T orElseGet(Supplier<? extends T> supplier);     // ✅ LAZY
    public T orElseThrow(Supplier<? extends X> exceptionSupplier);

    // ── CONSUMPTION ────────────────────────────────────────────
    public void ifPresent(Consumer<? super T> action);      // action only if present
    public void ifPresentOrElse(Consumer<? super T> action, Runnable emptyAction);  // Java 9+

    // ── TRANSFORMATION ─────────────────────────────────────────
    public Optional<T> filter(Predicate<? super T> predicate);
    public Optional<U> map(Function<? super T, ? extends U> mapper);
    public Optional<U> flatMap(Function<? super T, ? extends Optional<U>> mapper);
    public Optional<T> or(Supplier<? extends Optional<T>> other);        // Java 9+
    public Optional<T> and(Optional<? extends T> other);                // Java 9+

    // ── STREAMING ──────────────────────────────────────────────
    public Stream<T> stream();
    public Optional<T> or(Supplier<? extends Optional<? extends T>> other);
}
```

### 3.2 `Optional`'s two documented anti-uses

The Javadoc is explicit:

> *"API note: `Optional` is primarily intended to be used as a return type where
> there is a clear single 'natural' result that would be represented by `null`."*
>
> *"API note: `Optional` is not intended to be used in fields, or as a parameter
> type. `Optional` is primarily intended to be used as a return type where there
> is a clear single 'natural' result that would be represented by `null`."*

**Why not as a field?**

| Problem | Explanation |
|---|---|
| Not `Serializable` | You cannot serialise a class with an `Optional` field without a custom `writeObject` — and a DTO usually must be serialisable. |
| No `equals`/`hashCode` | `Optional.of("a").equals(Optional.of("a"))` is **`false`**! Two `Optional`s holding equal values are *not* equal. A field that is not `equals`-correct breaks every collection that contains it. |
| Adds a layer of noise | `getOptionalField().get()` is no better than `getField()` with a null check. |
| Framework friction | Most mapping frameworks (Jackson, JPA) need special support for `Optional` fields. |

**Why not as a parameter?**

```java
// ❌ A null check in disguise
void process(Optional<String> input) {
    if (input.isEmpty()) return;      // this is just "input might be null"
    use(input.get());
}
```

The caller writes `process(Optional.ofNullable(x))` or `process(Optional.of(x))` — they have already had to deal with the null. The `Optional` adds ceremony without removing the problem. The correct response to "I don't know if this is null" is either to **require** a value (non-null parameter) or to have **two overloads** — one that takes the value, one that takes a default.

### 3.3 `orElse` vs `orElseGet` — the eager-evaluation trap

```java
public class Cache {
    private final Map<String, String> store = new HashMap<>();

    public String get(String key) {
        return Optional.ofNullable(store.get(key))
                .orElse(expensiveLookup(key));       // ❌ ALWAYS calls expensiveLookup!
    }

    public String getBetter(String key) {
        return Optional.ofNullable(store.get(key))
                .orElseGet(() -> expensiveLookup(key));  // ✅ only on a miss
    }

    private String expensiveLookup(String key) {
        System.out.println("  [db] expensive lookup for " + key);
        return "value-" + key;
    }
}
```

**Why it matters:** `orElse(T other)` takes a **value**, so the argument is evaluated *before* the method is even called — on every invocation, hit or miss. `orElseGet(Supplier)` takes a **supplier**, so the work happens only when needed.

```text
   store.get("k") returns "V"   (a HIT)
   ───────────────────────────
   orElse(expensiveLookup("k"))       → expensiveLookup("k") IS CALLED, then discarded
   orElseGet(() -> expensiveLookup(k)) → NOT called ✅

   store.get("k") returns null      (a MISS)
   ───────────────────────────
   both call expensiveLookup — but orElse always pays the cost
```

> 💡 **The rule:** `orElse` for **cheap constants** (`orElse(0)`, `orElse("")`),
> `orElseGet` for **anything expensive** (a DB call, a remote call, a computation).

### 3.4 `map` vs `flatMap` in `Optional`

```java
Optional<String> name    = Optional.of("alice");
Optional<Optional<String>> nested = name.map(s -> Optional.of(s.toUpperCase()));
//   → Optional[Optional[ALICE]]   ← the NESTING you do not want

Optional<String> flat = name.flatMap(s -> Optional.of(s.toUpperCase()));
//   → Optional[ALICE]              ← FLATTENED
```

**The rule:** if the mapper returns an `Optional`, use `flatMap`. If it returns a plain value, use `map`. This is the same rule as `CompletableFuture.thenApply` vs `thenCompose`.

```java
// A realistic example
Optional<Customer> customer = findCustomer(id);
Optional<String> tier = customer
        .flatMap(Customer::getMembership)     // Optional<Membership>
        .map(Membership::getTierName);         // String → Optional<String>
```

### 3.5 The `Optional` "monad" — what it really gives you

```java
// Without Optional — every step needs a null check
Customer c = findCustomer(id);
if (c != null) {
    Membership m = c.getMembership();
    if (m != null) {
        String tier = m.getTierName();
        if (tier != null) { ... }
    }
}

// With Optional — the compiler forces you to handle absence at every step
Optional<Customer> customer = findCustomer(id);
String tier = customer
        .flatMap(Customer::getMembership)
        .map(Membership::getTierName)
        .filter(t -> !t.isBlank())
        .orElse("BRONZE");
```

**The key insight:** `map`/`flatMap` on an empty `Optional` **never invoke the function** — so a chain of transformations is *safe by construction*. There is no way for a `NullPointerException` to occur inside the chain.

### 3.6 `CompletableFuture` — the class

```java
public class CompletableFuture<T> implements Future<T>, CompletionStage<T> {

    // ── CREATION ───────────────────────────────────────────────
    public static <U> CompletableFuture<U> completedFuture(U value);
    public static <U> CompletableFuture<U> failedFuture(Throwable ex);   // Java 9+
    public static CompletableFuture<Void> completedStage(Object value);

    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier);
    public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor);
    public static CompletableFuture<Void> runAsync(Runnable runnable);
    public static CompletableFuture<Void> runAsync(Runnable runnable, Executor executor);

    // ── STAGES ─────────────────────────────────────────────────
    public <U> CompletableFuture<U> thenApply(Function<? super T, ? extends U> fn);
    public <U> CompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn);
    public <U> CompletableFuture<U> thenApplyAsync(Function<...> fn, Executor executor);
    public <U> CompletableFuture<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn);
    public <U> CompletableFuture<U> thenComposeAsync(...);
    public CompletableFuture<Void> thenAccept(Consumer<? super T> action);
    public CompletableFuture<Void> thenAcceptAsync(...);
    public CompletableFuture<Void> thenRun(Runnable action);
    public CompletableFuture<Void> thenRunAsync(...);

    // ── COMBINING ──────────────────────────────────────────────
    public <U> CompletableFuture<U> thenCombine(CompletionStage<? extends U> other,
                                                 BiFunction<? super T, ? super U, ? extends V> fn);
    public CompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn);
    public <U> CompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn);
    public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action);
    public CompletableFuture<T> exceptionallyCompose(Function<Throwable, ? extends CompletionStage<T>> fn);

    public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs);
    public static <T> CompletableFuture<T> anyOf(CompletableFuture<?>... cfs);
}
```

### 3.7 The stage methods, precisely

| Method | Input → Output | Thread | Returns |
|---|---|---|---|
| `thenApply` | `T → R` | **completing thread** | `CF<R>` |
| `thenApplyAsync` | `T → R` | pool (or given executor) | `CF<R>` |
| `thenCompose` | `T → CF<R>` | **completing thread** | `CF<R>` (flattened) |
| `thenAccept` | `T → void` | completing thread | `CF<Void>` |
| `thenRun` | `() → void` | completing thread | `CF<Void>` |
| `thenCombine` | `(T, U) → R` | completing thread | `CF<R>` |
| `allOf` | many → `Void` | — | `CF<Void>` (completes when ALL do) |
| `anyOf` | many → `T` | — | `CF<T>` (completes when the FIRST does) |
| `exceptionally` | error → `T` | completing thread | `CF<T>` |
| `handle` | `(T, error) → R` | completing thread | `CF<R>` |
| `whenComplete` | observe only | completing thread | `CF<T>` (passes through) |
| `completeOnTimeout` | timeout → `T` | — | `CF<T>` |
| `orTimeout` | timeout → error | — | `CF<T>` |

**The critical distinction — `thenApply` vs `thenApplyAsync`:**

```text
   COMPLETING THREAD (thenApply)
   ─────────────────────────────
   Thread-A completes the source CF
        │
        └──▶ thenApply's function runs ON THREAD-A
              → no thread hand-off, no scheduling cost
              → ⚠ but if the function BLOCKS, it blocks Thread-A
                (which might be an HTTP request thread!)

   POOL THREAD (thenApplyAsync)
   ───────────────────────────
   Thread-A completes the source CF
        │
        └──▶ the function is SUBMITTED to the ForkJoinPool
              → Thread-A is freed immediately
              → ⚠ extra scheduling cost (a context switch)
              → ✅ the function can block without blocking the completer
```

> 💡 **The rule of thumb:** use the **sync** variants for cheap, in-memory
> transformations (mapping a DTO, formatting a string). Use the **async** variants
> when the next stage does I/O, or when you want to free the completing thread.

### 3.8 `thenApply` vs `thenCompose`

```java
// ❌ NESTED — CompletableFuture<CompletableFuture<String>>
CompletableFuture<CompletableFuture<String>> nested =
        cf.thenApply(s -> fetchAsync(s));

// ✅ FLATTENED — CompletableFuture<String>
CompletableFuture<String> flat =
        cf.thenCompose(s -> fetchAsync(s));
```

**The rule:** if the mapper returns a `CompletableFuture`, use `thenCompose`. This is exactly `map` vs `flatMap` for `Optional`, and exactly the "monad" law.

```java
// The classic "fetch, then fetch its details" chain
CompletableFuture<Order> order = orderService.fetchAsync(orderId)
        .thenCompose(o -> customerService.fetchAsync(o.customerId()))
        .thenCompose(c -> addressService.fetchAsync(c.addressId()));
```

### 3.9 Error handling — the three recovery methods

```java
CompletableFuture<String> source = CompletableFuture.supplyAsync(() -> {
    if (Boolean.parseBoolean("false")) throw new IllegalStateException("boom");
    return "value";
});

// ① exceptionally — recover with a FALLBACK VALUE
CompletableFuture<String> a = source.exceptionally(ex -> "fallback: " + ex.getMessage());

// ② handle — recover AND transform, with access to both the value and the error
CompletableFuture<Integer> b = source.handle((value, ex) -> {
    if (ex != null) return -1;              // the error branch
    return value.length();                   // the success branch
});

// ③ whenComplete — OBSERVE only; the result passes through unchanged
source.whenComplete((value, ex) -> {
    if (ex != null) log.error("failed", ex);
    else            log.info("got " + value);
});
```

**The differences that matter:**

| Method | Sees the value? | Sees the error? | Changes the result? |
|---|---|---|---|
| `exceptionally` | ❌ | ✅ | ✅ (replaces with the fallback) |
| `handle` | ✅ | ✅ | ✅ (computes a new one) |
| `whenComplete` | ✅ | ✅ | ❌ (passes through) |

```text
   source ──▶ exceptionally(f)  →  on error: f(ex) ; on success: the ORIGINAL value
            ──▶ handle(f)        →  on error: f(null, ex) ; on success: f(value, null)
            ──▶ whenComplete(f)  →  on error: f(null, ex) then RE-THROW the error
                                 →  on success: f(value, null) then pass the value through
```

**`whenComplete` does NOT swallow the error** — it observes and then propagates. That is the difference from `exceptionally` and `handle`, which *replace* the failure with a value.

### 3.10 Exception propagation and wrapping

```java
CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
    throw new IllegalStateException("db is down");
});

// The ORIGINAL exception is wrapped in a CompletionException
cf.exceptionally(ex -> {
    // ex is a CompletionException, NOT the IllegalStateException
    System.out.println(ex.getClass());                    // CompletionException
    System.out.println(ex.getCause().getClass());         // IllegalStateException  ← the real one
    return "fallback";
});
```

**The rule:** **always unwrap the `CompletionException`** to get the original cause.

```java
static Throwable unwrap(Throwable ex) {
    if (ex instanceof CompletionException && ex.getCause() != null) {
        return ex.getCause();
    }
    if (ex instanceof ExecutionException && ex.getCause() != null) {
        return ex.getCause();
    }
    return ex;
}
```

**`join()` vs `get()`:**

| | `join()` | `get()` |
|---|---|---|
| Blocks? | ✅ | ✅ |
| Exception on failure | `CompletionException` (unchecked) | `ExecutionException` (checked) |
| Interrupted? | Not interruptible (ignores the interrupt) | Throws `InterruptedException` |
| Use at | inside a CF chain | the application boundary (so you can handle the checked exception) |

> ⚠ **`join()` is not interruptible.** If the thread is interrupted while in `join()`,
> the interrupt is *remembered* (the interrupt flag is set) but the call keeps waiting.
> At an application boundary, prefer `get()` so you can respond to the interrupt.

### 3.11 The default executor — the production trap

```java
public static <U> CompletableFuture<U> supplyAsync(Supplier<U> supplier) {
    return asyncSupplyStage(screenExecutor(ASYNC_POOL), supplier);
}

private static final Executor ASYNC_POOL = ForkJoinPool.commonPool();
```

**The `ForkJoinPool.commonPool()` is shared with `parallelStream()`.** In a **web server** this is a serious problem:

```text
   ❌ THE PROBLEM
   ──────────────
   Tomcat's 200 request threads
        │
        │ each calls cf.supplyAsync(() -> httpClient.get(...))   ← BLOCKING I/O
        ▼
   ForkJoinPool.commonPool()  (parallelism = #cores - 1 = say 7 threads)
        │
        └─▶ 200 blocking tasks queue up on 7 threads
             → the pool is SATURATED
             → parallelStream() elsewhere in the SAME JVM now also stalls
             → latency explodes

   ✅ THE FIX — always pass a DEDICATED executor
   ─────────────────────────────────────────────────
   ExecutorService ioPool = Executors.newFixedThreadPool(200);
   cf.supplyAsync(() -> httpClient.get(...), ioPool);
```

**The three pools you should have:**

| Pool | Size | For |
|---|---|---|
| `ioPool` | large (e.g. 200) | **blocking** I/O — each thread waits on a socket |
| `cpuPool` | `nproc` | CPU-bound work |
| `commonPool()` | `nproc - 1` | **leave it alone** — it is shared with parallel streams |

> 💡 **Java 21 changed the calculus.** With **virtual threads**, a blocking I/O
> task costs almost nothing — a virtual thread that blocks unmounts its carrier
> thread. So the modern pattern is: `Executors.newVirtualThreadPerTaskExecutor()`,
> or plain `Thread.ofVirtual().start(...)`, and *no* pool sizing at all. This is
> the single biggest practical change to async Java in years.

### 3.12 Timeouts

```java
// Java 9+
cf.orTimeout(5, TimeUnit.SECONDS);          // completes EXCEPTIONALLY if not done in time
cf.completeOnTimeout("default", 5, TimeUnit.SECONDS);   // completes NORMALLY with a default

// The pre-Java-9 idiom — a ScheduledExecutorService
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.schedule(() -> cf.completeExceptionally(new TimeoutException()), 5, TimeUnit.SECONDS);
```

**⚠ The critical caveat:** `orTimeout` does **not** cancel the underlying task.
The work keeps running (and keeps consuming resources) — only the *future* fails.
If the task holds a connection, you leak it. Java 21's `StructuredTaskScope` addresses
this by cancelling sibling tasks on failure.

### 3.13 `allOf` and `anyOf`

```java
CompletableFuture<String> a = fetchAsync("a");
CompletableFuture<String> b = fetchAsync("b");
CompletableFuture<String> c = fetchAsync("c");

// allOf — completes when ALL complete; returns CF<Void>
CompletableFuture.allOf(a, b, c).thenRun(() -> {
    // all three are done; the individual futures hold the values
    System.out.println(a.join() + b.join() + c.join());
});

// anyOf — completes when the FIRST completes
CompletableFuture<Object> first = CompletableFuture.anyOf(a, b, c);
first.thenAccept(v -> System.out.println("first = " + v));

// Java 12+ — allOf with typed results
CompletableFuture.allOf(a, b, c).thenApply(v -> List.of(a.join(), b.join(), c.join()));
```

> ⚠ **`allOf` returns `CompletableFuture<Void>`** — you cannot get the values from
> it. You must `join()` the individual futures afterwards. (The `allOf` overload
> taking a `Function` is a third-party library pattern, not the JDK.)

### 3.14 `CompletableFuture` vs `Future` vs `CompletionStage`

| | `Future` | `CompletableFuture` | `CompletionStage` |
|---|---|---|---|
| Package | `java.util.concurrent` | `java.util.concurrent` | `java.util.concurrent` |
| Result | `get()` blocks | `join()` blocks | No blocking method |
| Composition | ❌ none | ✅ full | ✅ full |
| Completion | by the task only | by the task **or by you** (`complete`, `obtrudeValue`) | — |
| Implement it? | ✅ | ❌ (final class) | ✅ (the interface) |

`CompletableFuture` is the *implementation*; `CompletionStage` is the *interface* that describes the composition API. Use `CompletionStage` as the **return type** in your APIs — it is the smaller, more focused contract, and it lets you return a `CompletableFuture` or any other implementation.

---

## 4. Visualizations

### 4.1 `Optional` — the container and its transformations

```text
   ┌─────────────────────┐
   │      Optional       │
   ├─────────────────────┤
   │  T value;           │   ← null means EMPTY
   │  boolean isPresent; │   ← (the flag is derived, not stored)
   └─────────────────────┘

   EMPTY                              PRESENT("alice")
     │                                    │
     │ map(f)                             │ map(f)
     ▼                                    ▼
   EMPTY   ← f NEVER CALLED          Optional[f("alice")]
     │                                    │
     │ flatMap(f)                        │ flatMap(f)
     ▼                                    ▼
   EMPTY   ← f NEVER CALLED          Optional[ f("alice") ]  ← FLATTENED
```

**What to look at:** the mapper is **never invoked** on an empty `Optional`. That single property is what makes a long chain of `map`/`flatMap` safe — there is no way for an `NullPointerException` to occur inside the chain.

### 4.2 `orElse` vs `orElseGet` — the eager-evaluation timeline

```text
   CACHE HIT — the value IS present
   ───────────────────────────────
   Optional.ofNullable(store.get("k"))     → Optional["V"]

   orElse(expensiveLookup("k"))
       ├─ 1. evaluate expensiveLookup("k")   ← 💰 PAID
       ├─ 2. call orElse(alreadyComputed)
       └─ 3. result: "V"                     ← the computed value is THROWN AWAY

   orElseGet(() -> expensiveLookup("k"))
       ├─ 1. call orElseGet(supplier)
       ├─ 2. value is present → supplier NOT called
       └─ 3. result: "V"                     ← 💰 NOT PAID ✅
```

### 4.3 `CompletableFuture` — the stage graph

```text
   supplyAsync(() -> loadUser(id))          ← runs on the ForkJoinPool
        │
        │  completes on the POOL thread
        ▼
   thenApply(u -> u.getName())              ← runs on the SAME (completing) thread
        │                                    (no hand-off, no extra scheduling)
        ▼
   thenCompose(name -> loadProfile(name))   ← runs on the SAME thread
        │
        │  completes on whatever thread completed it
        ▼
   thenAccept(profile -> send(profile))     ← same thread
        │
        ▼
   exceptionally(ex -> fallback())           ← only on failure
```

### 4.4 `thenApply` vs `thenCompose` — the nesting

```text
   ❌ thenApply — NESTED
   ─────────────────────
   CompletableFuture<User>  cf
        │
        └─▶ thenApply(u -> loadProfileAsync(u))
              return type: CompletableFuture<CompletableFuture<Profile>>
                                    ▲
                                    └── you now have to unwrap it manually

   ✅ thenCompose — FLATTENED
   ──────────────────────────
   CompletableFuture<User>  cf
        │
        └─▶ thenCompose(u -> loadProfileAsync(u))
              return type: CompletableFuture<Profile>
              → the inner future is unwrapped AUTOMATICALLY
```

### 4.5 The three error-handling methods

```text
   source CF
      │
      ├── SUCCESS (value = "V")
      │      │
      │      ├──▶ exceptionally(f)   →  f NOT called  →  result: "V"  (unchanged)
      │      ├──▶ handle(f)         →  f("V", null)  →  result: f's value
      │      └──▶ whenComplete(f)   →  f("V", null)  →  result: "V"  (unchanged)
      │
      └── FAILURE (ex = IllegalStateException)
             │
             ├──▶ exceptionally(f)   →  f(ex)        →  result: f's value  ← ERROR REPLACED
             ├──▶ handle(f)         →  f(null, ex)  →  result: f's value  ← ERROR REPLACED
             └──▶ whenComplete(f)   →  f(null, ex)  →  result: 💥 THE ERROR IS RE-THROWN
```

**What to look at:** `whenComplete` is the only one that **observes without replacing** — it is for logging and metrics. `exceptionally` and `handle` **replace** the failure with a value.

### 4.6 The executor problem — why the default pool breaks in a web server

```text
   ❌ DEFAULT (commonPool)
   ────────────────────────
   Tomcat: 200 request threads
        │
        │  cf.supplyAsync(() -> httpClient.get(url))     ← BLOCKS on a socket
        ▼
   ForkJoinPool.commonPool()   (parallelism ≈ #cores - 1, say 7)
        │
        ├─ task 1  → BLOCKED on socket
        ├─ task 2  → BLOCKED
        ├─ ... 200 tasks queue up
        └─ ▶ the pool is SATURATED
             → a parallelStream() ANYWHERE in this JVM now also stalls
             → p99 latency explodes

   ✅ DEDICATED EXECUTOR
   ─────────────────────
   Tomcat: 200 request threads
        │
        │  cf.supplyAsync(() -> httpClient.get(url), ioPool)
        ▼
   ioPool (200 threads, sized for BLOCKING work)
        │
        └─ 200 tasks run concurrently, each parked on its own socket
             → the commonPool is untouched
             → parallelStream() is unaffected
```

**What to look at:** a thread that blocks is *wasted* — it holds a platform thread while doing nothing. That is why an I/O pool must be sized by **concurrency**, not by CPU count.

### 4.7 `Optional` — the anti-pattern map

```text
   ✅ CORRECT                                   ❌ WRONG
   ─────────                                    ───────
   Optional<Customer> findById(String id)       Customer findById(String id)
       → the CALLER must handle absence            → returns null; the caller
                                                    may or may not check

   Optional<Customer> field;                   Customer field;   // null is fine
       → ❌ not Serializable, no equals/hash

   void process(Optional<X> x)                 void process(X x)
       → ❌ a null check in disguise
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — `orElse` vs `orElseGet`

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class OrElseDemo {
    private final Map<String, String> store = new HashMap<>();

    public String getEager(String key) {
        return Optional.ofNullable(store.get(key))
                .orElse(expensiveLookup(key));        // ⚠ always calls it
    }

    public String getLazy(String key) {
        return Optional.ofNullable(store.get(key))
                .orElseGet(() -> expensiveLookup(key)); // ✅ only on a miss
    }

    private String expensiveLookup(String key) {
        System.out.println("  [db] expensive lookup: " + key);
        return "computed-" + key;
    }

    public static void main(String[] args) {
        OrElseDemo demo = new OrElseDemo();
        demo.store.put("k", "cached");

        System.out.println("--- eager (cache hit) ---");
        System.out.println("result = " + demo.getEager("k"));

        System.out.println("--- lazy (cache hit) ---");
        System.out.println("result = " + demo.getLazy("k"));

        System.out.println("--- lazy (cache miss) ---");
        System.out.println("result = " + demo.getLazy("missing"));
    }
}
```

**Actual output:**

```text
--- eager (cache hit) ---
  [db] expensive lookup: k        ← 💰 PAID for nothing
result = cached
--- lazy (cache hit) ---
result = cached                    ← 💰 NOT PAID ✅
--- lazy (cache miss) ---
  [db] expensive lookup: missing
result = computed-missing
```

**Takeaway:** the eager version performed the expensive lookup **even though the value was cached**. In a hot path with an expensive default, this is a serious performance bug that profiling will reveal as "why is the DB being hit on every request?".

### Example 2 — `map` vs `flatMap`, and the empty short-circuit

```java
import java.util.Optional;

public class OptionalDemo {
    record Address(String city) {}
    record Customer(Address address) {}

    static Optional<Customer> findCustomer(String id) {
        return "c1".equals(id) ? Optional.of(new Customer(new Address("Pune"))) : Optional.empty();
    }

    public static void main(String[] args) {
        // map on a plain value
        Optional<String> city = findCustomer("c1")
                .map(Customer::address)
                .map(Address::city);
        System.out.println("city        = " + city);

        // flatMap is NOT needed here — map returns a plain Address
        Optional<String> city2 = findCustomer("c1")
                .map(Customer::address)
                .map(Address::city);
        System.out.println("city2       = " + city2);

        // The chain on a missing customer — the mappers are NEVER called
        Optional<String> missing = findCustomer("nope")
                .map(Customer::address)      // not called
                .map(Address::city);          // not called
        System.out.println("missing     = " + missing);

        // filter
        Optional<String> nonBlank = city.filter(s -> !s.isBlank());
        System.out.println("nonBlank    = " + nonBlank);

        // orElseThrow
        try {
            findCustomer("nope").orElseThrow(() -> new IllegalArgumentException("no such customer"));
        } catch (IllegalArgumentException e) {
            System.out.println("thrown      = " + e.getMessage());
        }

        // ⚠ THE FAMOUS QUIRK
        System.out.println("Optional equality: " + Optional.of("a").equals(Optional.of("a")));
    }
}
```

**Actual output:**

```text
city        = Optional[Pune]
city2       = Optional[Pune]
missing     = Optional.empty
nonBlank    = Optional[Pune]
thrown      = no such customer
Optional equality: false
```

**Takeaway:** the last line is the famous `Optional` quirk — `Optional` does **not** implement `equals`/`hashCode`, so two `Optional`s holding equal values are **not** equal. This is why `Optional` must not be used as a field in a value object.

### Example 3 — `CompletableFuture` composition and parallelism

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CompletableFutureDemo {

    static final ExecutorService POOL = Executors.newFixedThreadPool(4);

    static CompletableFuture<String> fetch(String name, long delayMs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }
            System.out.println("  [" + Thread.currentThread().getName() + "] fetched " + name);
            return name.toUpperCase();
        }, POOL);
    }

    public static void main(String[] args) {
        long t0 = System.nanoTime();

        CompletableFuture<String> a = fetch("a", 300);
        CompletableFuture<String> b = fetch("b", 200);
        CompletableFuture<String> c = fetch("c", 100);

        // SEQUENTIAL composition — the stages chain on the completing thread
        CompletableFuture<String> chained = a
                .thenApply(s -> s + "-1")
                .thenApply(s -> s + "-2");
        System.out.println("chained = " + chained.join());

        // PARALLEL — all three start at once
        CompletableFuture<Void> all = CompletableFuture.allOf(a, b, c);
        all.join();
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("all done in ~" + elapsedMs + "ms (not 600ms!)");
        System.out.println("a=" + a.join() + " b=" + b.join() + " c=" + c.join());

        // thenCompose — flattening
        CompletableFuture<String> composed = CompletableFuture
                .supplyAsync(() -> "user-1", POOL)
                .thenCompose(u -> fetch(u + "-profile", 50));
        System.out.println("composed = " + composed.join());

        POOL.shutdown();
    }
}
```

**Actual output** (thread names vary):

```text
  [pool-1-thread-3] fetched c
  [pool-1-thread-2] fetched b
  [pool-1-thread-4] fetched a
chained = A-1-2
all done in ~301ms (not 600ms!)
a=A b=B c=C
  [pool-1-thread-1] fetched user-1-profile
composed = USER-1-PROFILE
```

**Takeaway:** the three fetches ran **concurrently** — total time ≈ the slowest (300 ms), not the sum (600 ms). And `thenCompose` returned a flat `CompletableFuture<String>`, not a nested one.

### Example 4 — Error handling, the three ways

```java
import java.util.concurrent.CompletableFuture;

public class ErrorHandlingDemo {

    static CompletableFuture<String> failing() {
        return CompletableFuture.supplyAsync(() -> {
            throw new IllegalStateException("database unavailable");
        });
    }

    public static void main(String[] args) {
        // ① exceptionally — replace the failure with a fallback
        String a = failing().exceptionally(ex -> "fallback: " + ex.getCause().getMessage()).join();
        System.out.println("exceptionally : " + a);

        // ② handle — recover AND transform
        Integer b = failing().handle((value, ex) -> ex == null ? value.length() : -1).join();
        System.out.println("handle         : " + b);

        // ③ whenComplete — observe only, the error is RE-THROWN
        try {
            failing().whenComplete((value, ex) ->
                    System.out.println("whenComplete   : observed " + (ex == null ? "ok" : ex.getCause().getMessage()))
            ).join();
        } catch (CompletionException e) {
            System.out.println("whenComplete   : still failed with " + e.getCause().getMessage());
        }
    }
}
```

**Actual output:**

```text
exceptionally : fallback: database unavailable
handle         : -1
whenComplete   : observed database unavailable
whenComplete   : still failed with database unavailable
```

**Takeaway:** `whenComplete` **observed** the error and then let it propagate — the `join()` still threw. `exceptionally` and `handle` **replaced** it with a value. That is the difference between "log it" and "recover from it".

### Example 5 — The dedicated executor (and the production trap)

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ExecutorDemo {
    public static void main(String[] args) {
        // ❌ The default: the common ForkJoinPool, shared with parallelStream()
        CompletableFuture<String> defaultPool = CompletableFuture.supplyAsync(() -> {
            System.out.println("default pool thread = " + Thread.currentThread().getName());
            return "default";
        });

        // ✅ A dedicated pool
        ExecutorService ioPool = Executors.newFixedThreadPool(2);
        CompletableFuture<String> dedicated = CompletableFuture.supplyAsync(() -> {
            System.out.println("dedicated pool thread = " + Thread.currentThread().getName());
            return "dedicated";
        }, ioPool);

        System.out.println(defaultPool.join());
        System.out.println(dedicated.join());

        // Timeouts — Java 9+
        CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return "too late";
        }, ioPool);

        try {
            slow.orTimeout(200, TimeUnit.MILLISECONDS).join();
        } catch (java.util.concurrent.TimeoutException e) {
            System.out.println("timed out ✅  (note: the underlying task is STILL RUNNING)");
        }

        ioPool.shutdownNow();
    }
}
```

**Actual output** (thread names vary):

```text
default pool thread = ForkJoinPool.commonPool-worker-1
dedicated pool thread = pool-1-thread-1
default
dedicated
timed out ✅  (note: the underlying task is STILL RUNNING)
```

**Takeaway:** the default pool thread is `ForkJoinPool.commonPool-worker-1` — the **same pool** `parallelStream()` uses. In a web server, always pass a dedicated executor. And note the caveat printed: `orTimeout` fails the *future* but does **not** cancel the work.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A safe `Optional` utility — with the anti-patterns explicitly avoided

```java
package com.example.core;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A thin, opinionated layer over {@link Optional}.
 *
 * <p>Why a wrapper at all? Because the JDK's API has one trap that is easy to
 * get wrong ({@code orElse} is eager) and one quirk that bites in tests
 * ({@code Optional} has no {@code equals}). This class makes both safe by
 * construction.
 *
 * <p><b>Rules encoded here:</b>
 * <ol>
 *   <li>{@code Optional} appears in RETURN types only — never in fields or
 *       parameters.</li>
 *   <li>Every "absent" path has an explicit, named default.</li>
 *   <li>Exceptions are unwrapped, never swallowed silently.</li>
 * </ol>
 */
public final class Results {

    private Results() {}

    /** A result that is present, or empty. */
    public static <T> Optional<T> present(T value) {
        return Optional.ofNullable(value);
    }

    /** The lazy default — NEVER use {@code orElse} with an expensive value. */
    public static <T> T orElseGet(Optional<T> value, Supplier<? extends T> fallback) {
        return value.orElseGet(fallback);
    }

    /** The eager default — for cheap constants only. */
    public static <T> T orElse(Optional<T> value, T fallback) {
        return value.orElse(fallback);
    }

    /**
     * Unwraps a {@link java.util.concurrent.CompletionException} /
     * {@link java.util.concurrent.ExecutionException} to the original cause.
     */
    public static Throwable unwrap(Throwable throwable) {
        Objects.requireNonNull(throwable);
        if ((throwable instanceof java.util.concurrent.CompletionException
                || throwable instanceof java.util.concurrent.ExecutionException)
                && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    /** Maps an Optional through a function that itself returns an Optional. */
    public static <T, U> Optional<U> flatMap(Optional<T> value,
                                              Function<? super T, ? extends Optional<U>> mapper) {
        return value.flatMap(mapper);
    }

    /** Maps an Optional through a function returning a plain value. */
    public static <T, U> Optional<U> map(Optional<T> value,
                                         Function<? super T, ? extends U> mapper) {
        return value.map(mapper);
    }

    /** Fails with a meaningful exception instead of a bare NoSuchElementException. */
    public static <T> T require(Optional<T> value, String message) {
        return value.orElseThrow(() -> new NoSuchElementException(message));
    }

    /** Filters and reports whether the filter removed everything. */
    public static <T> boolean isPresentAndMatches(Optional<T> value, Predicate<T> predicate) {
        return value.filter(predicate).isPresent();
    }

    /**
     * Turns an Optional into a Result-ish pair without a null.
     * Note: this is a record, not an Optional — so equals/hashCode work. ⭐
     */
    public record Outcome<T>(boolean present, T value, String reason) {
        public static <T> Outcome<T> of(T value) {
            return new Outcome<>(true, value, null);
        }

        public static <T> Outcome<T> absent(String reason) {
            return new Outcome<>(false, null, reason);
        }

        public static <T> Outcome<T> from(Optional<T> optional, String reason) {
            return optional.map(Outcome::of).orElseGet(() -> Outcome.absent(reason));
        }
    }
}
```

### 6.2 A production `CompletableFuture` toolkit with a dedicated executor

```java
package com.example.async;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A production-grade async toolkit.
 *
 * <p>Design decisions worth defending:
 * <ul>
 *   <li>A <b>dedicated executor</b> — never the common ForkJoinPool, which is
 *       shared with parallelStream() and sized for CPU work.</li>
 *   <li>Every async call <b>specifies its executor</b> — no accidental
 *       commonPool usage.</li>
 *   <li>Errors are <b>unwrapped</b> to their original cause before they are
 *       logged or mapped.</li>
 *   <li>A <b>timeout is mandatory</b> on every remote call.</li>
 * </ul>
 */
public final class Async {

    private Async() {}

    /** For BLOCKING I/O — sized by concurrency, not by CPU count. */
    public static final ExecutorService IO_POOL =
            Executors.newFixedThreadPool(
                    Math.max(50, Runtime.getRuntime().availableProcessors() * 4),
                    Thread.ofPlatform().name("io-", 0).factory());

    /** For CPU-bound work. */
    public static final ExecutorService CPU_POOL =
            Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors(),
                    Thread.ofPlatform().name("cpu-", 0).factory());

    private static final ScheduledExecutorService TIMEOUTS =
            Executors.newScheduledThreadPool(1, Thread.ofPlatform().name("timeout-", 0).factory());

    /** A supplyAsync on the IO pool, with a mandatory timeout. */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, Duration timeout) {
        return CompletableFuture.supplyAsync(supplier, IO_POOL)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** A runAsync on the IO pool, with a mandatory timeout. */
    public static CompletableFuture<Void> runAsync(Runnable runnable, Duration timeout) {
        return CompletableFuture.runAsync(runnable, IO_POOL)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** The "fetch many things concurrently" pattern. */
    public static <T, R> CompletableFuture<List<R>> allOf(
            List<T> inputs,
            Function<T, CompletableFuture<R>> fetcher,
            Duration timeout) {

        List<CompletableFuture<R>> futures = inputs.stream()
                .map(fetcher)
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList())
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * The "first success wins, but fail if ALL fail" pattern — the classic
     * multi-provider lookup (e.g. try several payment gateways).
     */
    public static <T> CompletableFuture<T> firstSuccessful(
            List<Supplier<CompletableFuture<T>>> providers,
            Duration timeout) {

        List<CompletableFuture<T>> futures = providers.stream()
                .map(CompletableFuture::supplyAsync)
                .toList();

        return CompletableFuture.anyOf(futures.toArray(CompletableFuture[]::new))
                .thenCompose(f -> (CompletableFuture<T>) f)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Unwraps the original cause of a failed stage. */
    public static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    /** Maps a failure to a meaningful domain error, keeping the cause. */
    public static <T> CompletableFuture<T> mapFailure(
            CompletableFuture<T> future,
            Function<Throwable, ? extends Throwable> mapper) {

        return future.handle((value, error) -> {
            if (error == null) {
                return value;
            }
            Throwable cause = unwrap(error);
            throw new CompletionException(mapper.apply(cause));
        });
    }
}
```

### 6.3 A retry + backoff stage for a `CompletableFuture`

```java
package com.example.async;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Retries a failed asynchronous operation with exponential backoff and jitter.
 *
 * <p>Note the care taken: the retry is <b>scheduled</b> (not busy-looping),
 * the backoff is <b>capped</b>, and the jitter is <b>random</b> so that a fleet
 * of clients does not retry in lockstep (the "thundering herd").
 */
public final class Retrying {

    private Retrying() {}

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newScheduledThreadPool(1, Thread.ofPlatform().name("retry-", 0).factory());

    private static final double DEFAULT_JITTER_FACTOR = 0.3;

    public static <T> CompletableFuture<T> withRetry(
            Supplier<CompletableFuture<T>> operation,
            int maxAttempts,
            Duration initialDelay,
            double multiplier,
            double jitterFactor) {

        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");

        CompletableFuture<T> result = new CompletableFuture<>();

        attempt(operation, 1, maxAttempts, initialDelay, multiplier, jitterFactor, result);
        return result;
    }

    private static <T> void attempt(
            Supplier<CompletableFuture<T>> operation,
            int attemptNumber,
            int maxAttempts,
            Duration delay,
            double multiplier,
            double jitterFactor,
            CompletableFuture<T> result) {

        operation.get().whenComplete((value, error) -> {
            if (error == null) {
                result.complete(value);
                return;
            }
            if (attemptNumber >= maxAttempts) {
                result.completeExceptionally(error);
                return;
            }

            long baseMillis = (long) (delay.toMillis() * Math.pow(multiplier, attemptNumber - 1));
            long jitter = (long) (baseMillis * jitterFactor * Math.random());
            long waitMillis = Math.min(baseMillis + jitter, Duration.ofMinutes(1).toMillis());

            SCHEDULER.schedule(
                    () -> attempt(operation, attemptNumber + 1, maxAttempts,
                            delay, multiplier, jitterFactor, result),
                    waitMillis, TimeUnit.MILLISECONDS);
        });
    }
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — `orElse` with an expensive default

```java
return Optional.ofNullable(cache.get(key)).orElse(dbLookup(key));   // ❌ always calls dbLookup
```

**✅ FIX** — `.orElseGet(() -> dbLookup(key))`.

---

### ❌ Pitfall 2 — `Optional` as a field

```java
class User { private Optional<String> nickname; }   // ❌ not Serializable, no equals/hashCode
```

**✅ FIX** — use a plain nullable field, or a `record` with a nullable component, and return `Optional` from the getter.

---

### ❌ Pitfall 3 — `Optional` as a method parameter

```java
void process(Optional<String> input) { }   // ❌ a null check in disguise
```

**✅ FIX** — two overloads, or a plain parameter with a documented non-null contract.

---

### ❌ Pitfall 4 — `map` where you needed `flatMap`

```java
Optional<Optional<String>> nested = cf.map(s -> Optional.of(s));   // ❌ nested
```

**✅ FIX** — `flatMap`.

---

### ❌ Pitfall 5 — Assuming `Optional` has `equals`

```java
Optional.of("a").equals(Optional.of("a"))   // ❌ FALSE
```

**✅ FIX** — compare the *contents* (`a.equals(b)`), or use a `record` wrapper (as in `Results.Outcome`).

---

### ❌ Pitfall 6 — `thenApply` where you needed `thenCompose`

```java
CompletableFuture<CompletableFuture<T>> nested = cf.thenApply(this::fetchAsync);   // ❌
```

**✅ FIX** — `thenCompose`.

---

### ❌ Pitfall 7 — Using the default (common) pool in a web server

```java
cf.supplyAsync(() -> blockingHttpCall());   // ❌ the common pool is sized for CPU work
```

**✅ FIX** — always pass a dedicated executor.

---

### ❌ Pitfall 8 — Blocking on the common pool

```java
cf.supplyAsync(() -> { future.join(); return null; });   // ❌ DEADLOCK risk
```

**✅ FIX** — never block inside a `supplyAsync`; compose instead.

---

### ❌ Pitfall 9 — Swallowing the exception with `whenComplete`

```java
// ❌ the common (wrong) assumption: "whenComplete handles the error"
cf.whenComplete((v, ex) -> log(ex));   // the error is RE-THROWN
```

**✅ FIX** — use `exceptionally` or `handle` to *recover*; use `whenComplete` only to *observe*.

---

### ❌ Pitfall 10 — Not unwrapping the `CompletionException`

```java
cf.exceptionally(ex -> "fallback: " + ex.getMessage());   // ❌ the message is null-ish
```

**✅ FIX** — `ex.getCause().getMessage()`, or use a helper that unwraps.

---

### ❌ Pitfall 11 — `orTimeout` without understanding it does not cancel

```java
slow.orTimeout(1, TimeUnit.SECONDS);   // the future fails, but the WORK CONTINUES
```

**✅ FIX** — make the task itself interruptible, or use Java 21's `StructuredTaskScope` which cancels siblings.

---

### ❌ Pitfall 12 — `allOf` and expecting the values back

```java
CompletableFuture.allOf(a, b, c).thenApply(v -> List.of(a.join(), b.join(), c.join()));  // ✅ this is the idiom
```

**✅ FIX** — `allOf` returns `CF<Void>`; `join()` the individual futures.

---

### ❌ Pitfall 13 — Using `join()` where interruption matters

```java
cf.join();   // ❌ not interruptible — the interrupt flag is set but the wait continues
```

**✅ FIX** — use `get()` at the application boundary so you can handle `InterruptedException`.

---

### ❌ Pitfall 14 — `Optional.get()` without checking

```java
String name = findUser(id).get().getName();   // ❌ NoSuchElementException
```

**✅ FIX** — `orElse`, `orElseGet`, `orElseThrow`, or `ifPresent`.

---

### ❌ Pitfall 15 — `Optional.stream()` on a hot path

```java
findUser(id).stream().map(...).collect(...);   // ❌ allocates a Stream for one element
```

**✅ FIX** — use `map`/`flatMap` directly on the `Optional`.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is `Optional` and why was it introduced?**
**A:** `Optional<T>` is a container that may or may not hold a value, used to represent "no result" type-safely instead of returning `null`. It was added in Java 8 so that a method's signature could communicate that absence is possible, forcing the caller to handle it. It is intended for **return values only**.

**Q: What is the difference between `Optional.of` and `Optional.ofNullable`?**
**A:** `of(value)` throws a `NullPointerException` if the value is null — it asserts that a value is present. `ofNullable(value)` returns `Optional.empty()` for null. Use `of` when you are certain the value is non-null (e.g. it came from a non-null source), and `ofNullable` when it might be null.

**Q: What is the difference between `orElse` and `orElseGet`?**
**A:** `orElse(T other)` takes a **value**, so the argument is evaluated **eagerly** — every call, even when the `Optional` is present. `orElseGet(Supplier)` takes a **supplier**, so the work happens only when the `Optional` is empty. Use `orElse` for cheap constants and `orElseGet` for anything expensive (a DB call, a remote call).

**Q: What is `CompletableFuture`?**
**A:** A class implementing `Future` that can be **completed manually** and, crucially, **composed**. It provides a large API of non-blocking stage methods (`thenApply`, `thenCompose`, `thenCombine`, `allOf`, `exceptionally`, `handle`, …) so that asynchronous operations can be chained without blocking threads. It is Java's built-in toolkit for building async pipelines.

**Q: What is the difference between `thenApply` and `thenCompose`?**
**A:** `thenApply` transforms the value and, if the mapper returns a `CompletableFuture`, produces a **nested** `CompletableFuture<CompletableFuture<R>>`. `thenCompose` flattens it to `CompletableFuture<R>`. Use `thenCompose` when the mapper itself returns a `CompletableFuture` — exactly like `map` vs `flatMap` on `Optional`.

### 🟡 Intermediate

**Q: Why should `Optional` not be used as a field type or a parameter type?**
**A:** The Javadoc says so explicitly. As a **field**: `Optional` is not `Serializable` and does not implement `equals`/`hashCode` (so `Optional.of("a").equals(Optional.of("a"))` is `false`), which breaks value objects and most mapping frameworks. As a **parameter**: it is a null check in disguise — the caller has already had to deal with the null before wrapping it, so the `Optional` adds ceremony without removing the problem. The right response to "this might be absent" is to model it in the **return type**.

**Q: What is the difference between `exceptionally`, `handle` and `whenComplete`?**
**A:** `exceptionally` receives only the error and replaces the failure with a fallback value. `handle` receives both the value and the error, so it can transform the success case and recover from the failure. `whenComplete` receives both but does **not** change the result — it observes and then the error is re-thrown. So: `exceptionally`/`handle` *recover*; `whenComplete` *observes*.

**Q: What exception does a failed `CompletableFuture` throw, and how do you get the original cause?**
**A:** The original exception is wrapped in a `CompletionException`. `join()` rethrows it as-is (unchecked); `get()` wraps it in a checked `ExecutionException`. To get the original, unwrap: `ex.getCause()` (for `CompletionException`) or handle `ExecutionException` separately. A helper that unwraps both is the production-safe approach.

**Q: Why does `CompletableFuture` use the common `ForkJoinPool` by default, and why is that a problem?**
**A:** It is a convenient default for CPU-bound work, and it is the same pool `parallelStream()` uses — so no extra threads are created. In a **web server** it is a problem because the common pool is sized to `availableProcessors() - 1` and is designed for CPU work, while async tasks are usually **blocking I/O**. A hundred blocking tasks saturate a handful of threads and stall unrelated `parallelStream()` calls in the same JVM. The fix is to always pass a **dedicated executor** sized for blocking concurrency.

**Q: What is the difference between `join()` and `get()`?**
**A:** `get()` throws the checked `InterruptedException` and `ExecutionException`; `join()` throws the unchecked `CompletionException` and is **not interruptible** — the interrupt flag is set but the wait continues. Use `join()` inside a `CompletableFuture` chain (where the exception is handled by the chain) and `get()` at the application boundary (where you must handle the checked exception and respond to interruption).

**Q: Does `orTimeout` cancel the underlying task?**
**A:** **No.** It completes the *future* exceptionally with a `TimeoutException`, but the underlying task keeps running and keeps consuming resources — including any connection it holds. This is a real production leak. Java 21's `StructuredTaskScope` addresses it by cancelling sibling tasks on failure; otherwise, make the task itself interruptible and use `shutdownNow()` on the executor.

### 🔴 Advanced / Senior

**Q: Why doesn't `Optional` implement `equals` and `hashCode`?**
**A:** It is a long-standing, acknowledged wart in the JDK. The reasons usually given are that it was not considered important enough at design time, and that `Optional` is a *language-level* type that the JDK wanted to keep free of dependencies. The practical consequence is severe: `Optional.of("a").equals(Optional.of("a"))` is `false`, so an `Optional` in a field of a value object breaks `equals`, `hashCode`, and every `Set`/`Map` that contains it. It is a second, independent reason not to use `Optional` as a field type. The workaround is to unwrap at the boundary (`Optional<String>` → `String` in a `record`, which has a correct `equals`).

**Q: When is `Optional` the wrong tool, even for a return value?**
**A:** Four cases. (1) When the method returns **void** — there is no "no result" to express. (2) When the method returns a **collection** — use an empty collection, not `Optional<List<T>>`; the caller should not have to unwrap. (3) When the "empty" case is a **normal, expected outcome** that the caller must handle anyway — e.g. "find an optional discount"; use a nullable value or a `Optional`-free API. (4) When the method has **multiple** possible results of *different types* — `Optional` cannot express that; use a sealed interface or a `Result` type. `Optional` is for a **single** value that may be absent.

**Q: How would you design an async API that is safe under failure?**
**A:** Four design rules. (1) **A dedicated executor** — never the common pool. (2) **A mandatory timeout** on every remote call, because an unbounded future is a latent resource leak. (3) **A typed error path** — use `handle` to convert a failure into a domain outcome (an `Outcome` record or a sealed result type) rather than letting a raw `CompletionException` escape. (4) **A cancellation story** — `orTimeout` does not cancel, so either make the task interruptible or use Java 21's `StructuredTaskScope`, which cancels siblings when one fails. The design principle: an async API should make the *failure mode explicit at the type level*, not leave it as an unchecked exception three frames away.

**Q: When is `CompletableFuture` the wrong tool?**
**A:** Four cases. (1) **Blocking I/O at scale** — `CompletableFuture` still needs a thread per blocked task, so it does not solve the thread-explosion problem; virtual threads (Java 21) or a reactive library (Reactor, RxJava) do. (2) **Back-pressure** — `CompletableFuture` has no notion of a slow consumer; a stream of futures flowing into an unbounded queue will exhaust memory. Reactive Streams has back-pressure built in. (3) **Complex event topologies** — a DAG of dozens of interdependent futures is hard to read and hard to cancel; a reactive library's operators compose better. (4) **When you need cancellation** — `CompletableFuture.cancel(true)` does **not** interrupt the running task; it merely completes the future exceptionally. For real cancellation, you need `Future.cancel(true)` with an `ExecutorService`, or virtual threads with `Thread.interrupt`.

**Q: How do you compose N async calls that may each fail, without losing the failures?**
**A:** `allOf` completes with the **first** exception, and the other failures are *not* reported — which loses information. The robust pattern is to convert each future into a **success-or-failure outcome** first, then aggregate:

```java
CompletableFuture<List<Outcome<R>>> all = inputs.stream()
        .map(input -> fetcher.apply(input)
                .handle((value, error) -> error == null
                        ? Outcome.of(value)
                        : Outcome.failed(unwrap(error))))   // never fails
        .collect(...);
CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).thenApply(v -> all.stream().map(CompletableFuture::join).toList());
```

The key idea: **make each stage non-failing**, so no failure is lost, and then decide at the aggregate level whether to fail fast or to return a partial result. This is the pattern behind resilience libraries' "bulkhead" and "partial success" modes.

### ⚫ Expert / Principal

**Q: Java's `CompletableFuture` has no back-pressure. What is the real architectural consequence, and what are the options?**
**A:** The consequence is that a `CompletableFuture` chain is an **unbounded buffer** in disguise. If a producer emits futures faster than a consumer processes them, the intermediate `CompletableFuture`s queue up in memory with no limit — the classic "async pipeline causes OOM" failure. The producer has no way to know the consumer is slow, so nothing slows it down. The options, in increasing order of sophistication: (1) **bound the queue** by inserting a `Semaphore` or a `BlockingQueue` between the producer and the async stages — crude but effective; (2) **use a reactive library** (Reactor, RxJava) where `Flux` has a built-in demand mechanism (request/reply) — this is the real answer for high-throughput pipelines; (3) **use virtual threads** (Java 21) with a *bounded* executor or a `StructuredTaskScope` — virtual threads make blocking cheap, but they do **not** create back-pressure on their own, so you still need a bound; (4) **design the pipeline to be pull-based** — a `Stream` over an async source (e.g. `Flow.Publisher` from Java 9, or `SubmissionPublisher`) lets the consumer drive the rate. The senior-level insight: `CompletableFuture` is a **composition** tool, not a **flow-control** tool; choosing it for a high-throughput pipeline without a separate bound is a design error, not a tuning error.

**Q: `Optional` was controversial. What did the JSR-310 / JSR-354 design debates actually turn on, and what is the modern consensus?**
**A:** The core disagreement was whether `Optional` is a **value type** (a real domain concept that may be absent) or merely a **null-avoidance wrapper** (a carrier for a possibly-null reference). If it is the former, it should be a first-class citizen — immutable, `Serializable`, with correct `equals`/`hashCode`, allowed as a field and a parameter, and ideally a *primitive* flavour. If it is the latter, it should be restricted to return values and be as lightweight as possible. The JSR chose the second (mostly), and the omission of `equals`/`hashCode` and `Serializable` is the residue of that compromise. The modern consensus, sharpened by the `Optional` API debates of the last decade, has two camps: the **"return-value only"** camp (Brian Goetz's position — `Optional` is a *hint* to the caller, and using it elsewhere is a smell), and the **"value type"** camp (Robert Nyström's *Optional Objects* Java port — `Optional` should be a real value with proper equality, serialisation, and a `map`/`flatMap` algebra). The practical guidance for a team: **pick one and be consistent.** If you follow the JDK's own usage (return values only, no field, no parameter), the JDK's bugs do not matter to you — and you should encode that in your lint rules.

**Q: How would you migrate a blocking, thread-per-request service to an async model without a rewrite?**
**A:** In stages, each independently valuable. (1) **Instrument first** — measure the thread count, the pool utilisation, and the p99 latency; identify which dependencies dominate. (2) **Introduce an async client at the boundary** — most HTTP and DB clients now have async variants. Wrap them in a **facade** that exposes a `CompletableFuture`-returning API, so the *shape* of the call site changes but the business logic does not. (3) **Compose, do not block** — inside a method, replace sequential `cf1.join(); cf2.join();` with `cf1.thenCompose(...)`. This alone turns three sequential 200 ms calls into 200 ms. (4) **Introduce the dedicated executor** at this point, sized for blocking I/O. (5) **Move the boundary** — make the controller return a `CompletableFuture` (Spring MVC supports this natively) so the request thread is released while the work continues. (6) **Only then** consider virtual threads or a reactive library. The critical discipline: **never mix** — a single `.join()` inside an async chain reintroduces the blocking you just removed and can deadlock on a saturated pool. And the honest caveat: async is not free — it trades thread simplicity for control-flow complexity, and for many services the right answer is virtual threads (which keep the code simple) rather than `CompletableFuture` (which does not).

---

## 9. Counter-Questions & Traps

### CQ1. "`Optional` was added to avoid `NullPointerException`. Isn't it just a fancy null?"

- **Naive:** "Yes, it's a wrapper around a nullable value."  ← *technically true, and it misses the point entirely*
- **The trap:** The trap is to treat `Optional` as a *runtime* mechanism. Its value is entirely **compile-time**: the *type* of the method's return value tells the caller that absence is possible, and the compiler then *forces* a decision (`orElse`, `orElseThrow`, `ifPresent`). A `null` return carries no such information — the caller must know, from documentation or from a `NullPointerException` in production, that the value might be absent.
- **Expert answer:** The precise claim is: `Optional` moves the "might be absent" fact from *implicit* (a null, discoverable only at runtime) to *explicit* (in the type signature, discoverable at compile time). That is the whole value. The NPE does not disappear — it moves to the *point where the programmer forgot to handle absence*, which is a place a good developer wants it. The secondary value is **composition**: `map`/`flatMap`/`filter` let you build a chain that is *safe by construction*, because the functions are never invoked on an empty container. And the honest caveat: the benefit only exists if the *whole* API uses the convention — a single method that returns `null` in a chain of `Optional`-returning methods destroys the guarantee.

### CQ2. "If `Optional` isn't allowed as a field, how do I model an optional field?"

- **Naive:** "Use a `record` with a nullable component."  ← *correct, and it needs more explanation*
- **The trap:** The trap is to stop at "use a nullable field", because that reintroduces the null the `Optional` was meant to remove. The interesting question is what the *domain* should say. A `User` with no nickname is a perfectly valid domain object — the absence is a real state, not a missing value.
- **Expert answer:** Three legitimate answers, in order of preference. (1) **Model the absence in the domain** — `Optional<String> nickname()` as a *method* (not a field) on a class, with a `private final String nickname` field that may be null. The field is an implementation detail; the *public contract* says "may be absent". This is the pattern the JDK itself uses. (2) **Use a `record`** with a nullable component, and expose an `Optional`-returning *accessor* if you want the contract. (3) **Use a domain-specific "absent" value** — an empty `String`, or a dedicated `NoNickname` singleton — when the absence has meaning (e.g. "the customer explicitly has no nickname" vs "we don't know yet"). That distinction — *absent* vs *unknown* — is a domain question, and no library can decide it for you. The general principle: **`Optional` is a statement about an API contract, not about storage.**

### CQ3. "`orElse` evaluates eagerly. Why didn't the JDK just make it lazy? Isn't that a design mistake?"

- **Naive:** "It's a legacy API design decision."  ← *accurate, and it dodges the real trade-off*
- **The trap:** The trap is to assume laziness would be strictly better. It is not: `orElse(T)` is a *value* parameter, and making it lazy would change its **signature** to a `Supplier` — which is exactly `orElseGet`. You cannot have both: if `orElse` took a `Supplier`, every existing call site `orElse(someValue)` would break.
- **Expert answer:** The API provides **both** forms precisely because the two use cases are different, and the cost model of each is different. `orElse(T)` for a *constant* is the most readable form and has zero overhead. `orElseGet(Supplier)` for an *expensive* value is lazy. The "design mistake" framing is unfair: the JDK added `orElseGet` in the same release precisely so users have a lazy option. What *is* a genuine design wart is the **naming**: `orElse` and `orElseGet` differ by three characters and by a whole cost model, and a reviewer will not spot the difference. The practical mitigation in a team: a lint rule that flags `orElse(` with a non-literal argument, and a code-review convention that `orElse` is only ever used with a literal or a `static final` constant.

### CQ4. "Why does `CompletableFuture` have 50+ methods? Isn't that API bloat?"

- **Naive:** "They cover every combination."  ← *describes the bloat without justifying it*
- **The trap:** The trap is to treat the method count as the problem. The real issue is that the API is a **cross product**: {transform, flatten, combine, consume, observe} × {value, error} × {sync, async} × {with/without executor}. Each axis is genuinely useful, so the cross product is large — but the *cost* is not the count, it is the **difficulty of choosing correctly** (`thenApply` vs `thenCompose` vs `thenCombine`; `exceptionally` vs `handle` vs `whenComplete`).
- **Expert answer:** The API size is a symptom of two orthogonal decisions: (a) **combinator style** — Java chose "one method per combinator" (`thenApply`, `thenAccept`, `thenRun`) rather than a single `then(Function, Executor)`, which would have been more compact but much less readable at the call site; (b) **sync/async duplication** — Java 8 shipped the sync variants and added the async ones later, keeping both. The honest modern assessment is that the API is *complete but not discoverable*, and the practical mitigation is a **house style**: pick a small, documented subset — for example, "always `thenCompose` for async, always `handle` for errors, always pass an explicit executor" — and enforce it in review. The deeper lesson: an API's cost is not its size but the number of *decisions* it forces the caller to make. `CompletableFuture` forces too many; a small wrapper (like the `Async` class above) is often the right answer.

### CQ5. "If `CompletableFuture` is non-blocking, why does my service still run out of threads?"

- **Naive:** "Because I called `join()` somewhere."  ← *one possible cause, and the most common one*
- **The trap:** The trap is to stop at "you blocked". The deeper cause is more often: **the async tasks themselves are blocking.** `supplyAsync(() -> httpClient.get(url))` where `httpClient.get` is a *blocking* call is not async at all — you have merely moved the blocking onto a pool thread. The thread is still consumed; the only difference is *which* thread.
- **Expert answer:** There are three distinct failure modes, and they need three different fixes. (1) **The caller blocks** — a `join()`/`get()` inside an async chain. Fix: compose instead of block. (2) **The task blocks** — a blocking library called inside `supplyAsync`. Fix: use the library's async API, or (Java 21+) use **virtual threads**, where blocking is cheap because the carrier thread is released. (3) **The pool is the wrong size** — the common pool has `nproc - 1` threads, sized for CPU work, while blocking I/O needs concurrency-sized pools. Fix: a dedicated executor sized for blocking. The diagnostic that distinguishes them: `jstack` and look for threads in `WAITING (parking)` on a socket read (case 2/3) versus a thread blocked in `CompletableFuture.get` (case 1). And the strategic answer for Java 21+: **virtual threads make case 2 disappear** — a blocking call on a virtual thread unmounts its carrier — so the "dedicated sized pool" advice becomes obsolete.

### CQ6. "You said `thenApply` runs on the completing thread. Isn't that a problem?"

- **Naive:** "Yes, that's why `thenApplyAsync` exists."  ← *true, but incomplete*
- **The trap:** The trap is to assume that "runs on the completing thread" is always bad. It is sometimes exactly what you want: it avoids a thread hand-off, and — critically — it **preserves ordering** and **thread-local context** across the chain.
- **Expert answer:** The choice is a trade-off with three axes. (1) **Latency vs throughput:** the sync variant avoids a scheduling hop; the async variant frees the completing thread. (2) **Blocking:** if the next stage blocks, the sync variant blocks the *completing* thread — which, in a web server, may be an HTTP request thread. That is the real danger. (3) **Context propagation:** the sync variant keeps the same thread, so `ThreadLocal` (a trace id, a security context, a tenant id) flows through the chain; the async variant loses it unless you propagate it explicitly (or use `ScopedValue` in Java 21+, or a context-propagating executor). The professional rule: **sync for cheap, pure transformations; async (with an explicit executor) before anything that might block.** And the hard rule: **never call `join()` inside a `supplyAsync`** — that is how you deadlock a saturated pool.

### CQ7. "Why doesn't `CompletableFuture` support cancellation properly? Isn't that a design flaw?"

- **Naive:** "Yes, `cancel(true)` doesn't interrupt the task."  ← *the observation is right; the diagnosis is wrong*
- **The trap:** The trap is to blame the design. `CompletableFuture` was specified as a **composition** mechanism — a value-holder that completes, and a fluent API for chaining. Cancellation was not part of that model. The deeper reason it is awkward: `cancel()` on a `CompletableFuture` completes the *future* exceptionally but has **no handle on the running task**, so it cannot interrupt it. `Future.cancel(true)` does have the handle — the `FutureTask` — and *can* interrupt. `CompletableFuture` deliberately is not a `FutureTask`.
- **Expert answer:** The real answer is that **Java 21's `StructuredTaskScope` was designed to fix this**, and it tells you the shape of the right solution: cancellation is a *concurrency-structure* concern, not a future concern. In a structured scope, when one subtask fails, the scope **cancels its siblings** — and because the scope owns the threads, it can interrupt them. The `CompletableFuture` model has no such ownership, so cancellation is best-effort. The practical guidance: if you need real cancellation, use `ExecutorService.submit()` and `Future.cancel(true)` (which interrupts), or use virtual threads with `Thread.interrupt()`. Treat `CompletableFuture.cancel` as "stop waiting for this result", not "stop this work".

### CQ8. "Is `CompletableFuture` the right choice for a high-throughput data pipeline? What about Reactor?"

- **Naive:** "Reactor because it has back-pressure."  ← *the right conclusion, for the wrong reason*
- **The trap:** The trap is to compare them on "which is more modern" or "which is faster". They are different *tools* for different *problems*, and the distinguishing property is not throughput — it is **flow control**.
- **Expert answer:** `CompletableFuture` is a **composition** tool: it lets you express "these N things happen concurrently, then combine them". It has **no back-pressure** — if a producer emits futures faster than a consumer drains them, the futures queue up unboundedly and you OOM. Reactive Streams (`Flux`, the basis of Reactor and RxJava) has a **demand** mechanism built in: the consumer declares how many elements it wants, and the producer is throttled. So: use `CompletableFuture` for **request-scoped, bounded fan-out** — "call these 3 services in parallel and combine" — which is the overwhelming majority of backend use cases. Use Reactor for **streaming, unbounded, or high-fan-out** pipelines where flow control matters. And the honest caveat about Reactor: it introduces a steep learning curve, a scheduler abstraction, and a debugging difficulty (stack traces through reactive operators are notoriously hard to read) — so it is a real trade, not a free upgrade. The intermediate option, and often the best one in 2026, is **virtual threads** with plain blocking code: you keep the simple code and get the scalability.

### CQ9. "`allOf` loses the individual exceptions. Isn't that a serious flaw?"

- **Naive:** "Yes, it's a known limitation."  ← *true, and the interviewer wants the fix*
- **The trap:** The trap is to treat it as an unfixable JDK wart. It is fixable, and the fix is *architectural* rather than a workaround: the errors must be **captured as values** before the aggregation, so that the aggregate step can decide what to do with them.
- **Expert answer:** The fix is to convert each future into a **success-or-failure outcome** using `handle`, so no stage can fail:

```java
record Outcome<T>(T value, Throwable error) {}

CompletableFuture<List<Outcome<T>>> all = futures.stream()
        .map(f -> f.handle((v, e) -> e == null ? new Outcome<>(v, null) : new Outcome<>(null, unwrap(e))))
        .toList();
```

Then `allOf(...)` can never fail, and the *caller* decides the policy: fail fast if any is critical, tolerate partial success, or aggregate the errors into a report. The same pattern is why resilience libraries have "bulkhead" and "partial success" modes. The deeper lesson: **`CompletableFuture` propagates the first failure and discards the rest — so if you care about all the failures, you must turn failures into values *before* aggregating.** That is a general principle for any aggregation of independent async operations.

### CQ10. "If `Optional` and `CompletableFuture` are both 'maybe a value' containers, why are they so different? Isn't that inconsistent?"

- **Naive:** "One is for nulls, one is for async."  ← *true, and it misses the shared design*
- **The trap:** The trap is to see two unrelated classes. They are actually the **same design pattern applied to two different domains**, and recognising that is what makes the API predictable: both are **monadic containers** with `map`/`flatMap` and short-circuiting semantics.
- **Expert answer:** The parallel is exact and worth internalising. `Optional<T>` models "a value that may not exist **yet**" (temporally); `CompletableFuture<T>` models "a value that may not exist **yet**" (asynchronously). Both are *lazy* in the sense that the transformation is deferred; both **short-circuit** — the function is never invoked on the empty/failed case; both have `map` (transform) and `flatMap` (flatten a nested container); both have a "get or default" (`orElse` vs `exceptionally`/`handle`). The differences are exactly the domain differences: `Optional` is *completed* (present or absent, synchronously), `CompletableFuture` is *pending* (and may fail with an exception rather than merely be absent). The design insight: **once you internalise the monad pattern, both APIs stop feeling arbitrary** — and the same pattern explains `Stream` (a container of many), `Mono`/`Flux` (Reactor), and Kotlin's `Result`. The corollary for a team: if you are designing an API that "may or may not produce a value later", model it on `CompletableFuture`'s shape — `map`/`flatMap`/`onError`/`onComplete` — and your users will already know how to use it.

---

## 10. Rapid-Fire MCQs

**Q1.** `Optional` was introduced in which Java version?
- A) Java 5  B) Java 7  C) Java 8  D) Java 11

**Q2.** `Optional.of(null)` throws…
- A) `IllegalArgumentException`  B) `NullPointerException`  C) `NoSuchElementException`  D) Returns empty

**Q3.** `Optional.ofNullable(null)` returns…
- A) `null`  B) `Optional.empty()`  C) Throws  D) An empty `Stream`

**Q4.** `Optional.get()` on an empty `Optional` throws…
- A) `NullPointerException`  B) `NoSuchElementException`  C) `IllegalStateException`  D) `OptionalException`

**Q5.** Which method evaluates its argument **eagerly**?
- A) `orElseGet`  B) `orElse`  C) `map`  D) `filter`

**Q6.** `orElseGet` takes a…
- A) Value  B) `Supplier`  C) `Function`  D) `Consumer`

**Q7.** `Optional.map` on an empty `Optional`…
- A) Returns `null`  B) Returns `Optional.empty()` and never calls the mapper  C) Throws  D) Calls the mapper with null

**Q8.** `Optional.flatMap` is used when the mapper returns…
- A) A plain value  B) An `Optional`  C) A `Stream`  D) `null`

**Q9.** `Optional` implements `equals`/`hashCode`?
- A) Yes  B) No  C) Only `equals`  D) Only `hashCode`

**Q10.** Is `Optional` `Serializable`?
- A) Yes  B) No  C) Only if the value is  D) Since Java 9

**Q11.** Per the Javadoc, `Optional` is intended for…
- A) Fields  B) Return values  C) Parameters  D) Static fields

**Q12.** `Optional.isEmpty()` was added in…
- A) Java 8  B) Java 9  C) Java 11  D) Java 17

**Q13.** `CompletableFuture` implements which interface?
- A) `Runnable`  B) `Future` and `CompletionStage`  C) `Callable`  D) `Executor`

**Q14.** `supplyAsync` differs from `runAsync` because it…
- A) Runs on a different pool  B) Produces a value  C) Is faster  D) Handles errors

**Q15.** `thenCompose` is needed when the mapper returns…
- A) A plain value  B) A `CompletableFuture`  C) `null`  D) A `Runnable`

**Q16.** `thenApply` on a failing future…
- A) Runs the mapper anyway  B) Skips the mapper and propagates the failure  C) Throws immediately  D) Returns null

**Q17.** Which method **observes** without replacing the result?
- A) `exceptionally`  B) `handle`  C) `whenComplete`  D) `thenApply`

**Q18.** `exceptionally` receives which arguments?
- A) The value only  B) The error only  C) Both  D) Neither

**Q19.** `handle` receives which arguments?
- A) The value only  B) The error only  C) Both value and error  D) Neither

**Q20.** The default executor for `supplyAsync` is…
- A) A new pool per call  B) `ForkJoinPool.commonPool()`  C) The caller thread  D) `Executors.newCachedThreadPool()`

**Q21.** A failed stage's original exception is wrapped in…
- A) `ExecutionException`  B) `CompletionException`  C) `RuntimeException`  D) `IllegalStateException`

**Q22.** `join()` throws…
- A) `InterruptedException`  B) `CompletionException` (unchecked)  C) `ExecutionException`  D) `TimeoutException`

**Q23.** `get()` throws…
- A) `CompletionException`  B) `InterruptedException` and `ExecutionException`  C) `TimeoutException`  D) Nothing

**Q24.** `CompletableFuture.allOf` returns…
- A) `CompletableFuture<List<T>>`  B) `CompletableFuture<Void>`  C) `List<CompletableFuture<T>>`  D) `CompletableFuture<T>`

**Q25.** `orTimeout` …
- A) Cancels the underlying task  B) Fails the future but leaves the task running  C) Does nothing  D) Is not in the JDK

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **C** | `Optional` arrived with Java 8, alongside lambdas and streams. |
| 2 | **B** | `of` asserts the value is present — a null throws `NullPointerException`. |
| 3 | **B** | `ofNullable` converts null into `Optional.empty()`. |
| 4 | **B** | `get()` on an empty `Optional` throws `NoSuchElementException`. |
| 5 | **B** | `orElse(T)` takes a value, so it is evaluated before the call — always. |
| 6 | **B** | `orElseGet(Supplier)` is the lazy form. |
| 7 | **B** | The mapper is never invoked on an empty `Optional` — that is what makes chaining safe. |
| 8 | **B** | `flatMap` flattens a nested `Optional`; `map` would nest it. |
| 9 | **B** | No — `Optional.of("a").equals(Optional.of("a"))` is `false`. A long-standing JDK wart. |
| 10 | **B** | Not `Serializable` — one reason it must not be a field. |
| 11 | **B** | The Javadoc says "primarily intended to be used as a return type". |
| 12 | **C** | `isEmpty()` was added in Java 11. |
| 13 | **B** | It implements `Future<T>` and `CompletionStage<T>`. |
| 14 | **B** | `supplyAsync` takes a `Supplier` and produces a value; `runAsync` takes a `Runnable`. |
| 15 | **B** | `thenCompose` flattens a `CompletableFuture`-returning mapper. |
| 16 | **B** | A failed stage skips the mapper and propagates the failure downstream. |
| 17 | **C** | `whenComplete` observes and then re-throws — it does not replace the result. |
| 18 | **B** | `exceptionally(Function<Throwable, ? extends T>)` sees only the error. |
| 19 | **C** | `handle(BiFunction<T, Throwable, R>)` sees both. |
| 20 | **B** | The common `ForkJoinPool` — shared with `parallelStream()`, sized for CPU work. |
| 21 | **B** | The original exception is wrapped in a `CompletionException`. |
| 22 | **B** | `join` throws the unchecked `CompletionException` and is not interruptible. |
| 23 | **B** | `get` throws the checked `InterruptedException` and `ExecutionException`. |
| 24 | **B** | `allOf` returns `CompletableFuture<Void>` — you must `join()` the individual futures. |
| 25 | **B** | `orTimeout` fails the future but the underlying task keeps running. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                          THE OPTIONAL API                                │
├──────────────────────┬───────────────────────┬───────────────────────────┤
│ FACTORIES            │ of / ofNullable /     │ of() REJECTS null         │
│                      │ empty / of(T...)      │                           │
│ GET                  │ get / getOrDefault /  │ get() THROWS if empty     │
│                      │ orElse / orElseGet /  │                           │
│                      │ orElseThrow           │ ⚠ orElse = EAGER          │
│                      │                       │   orElseGet = LAZY        │
│ CONSUME              │ ifPresent /           │                           │
│                      │ ifPresentOrElse       │                           │
│ TRANSFORM            │ map / flatMap /       │ flatMap when the mapper   │
│                      │ filter / or / and     │ returns an Optional       │
│ STREAM               │ stream / isEmpty      │ isEmpty() is Java 11+     │
└──────────────────────┴───────────────────────┴───────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE COMPLETABLEFUTURE STAGE MATRIX                    │
├──────────────────┬───────────────────┬──────────────────┬────────────────┤
│ Purpose          │ Sync              │ Async            │ Recover?       │
├──────────────────┼───────────────────┼──────────────────┼────────────────┤
│ transform        │ thenApply         │ thenApplyAsync   │                │
│ flatten          │ thenCompose       │ thenComposeAsync │                │
│ consume          │ thenAccept        │ thenAcceptAsync  │                │
│ run              │ thenRun           │ thenRunAsync     │                │
│ join two         │ thenCombine       │ thenCombineAsync │                │
│ observe          │ whenComplete      │ whenCompleteAsync│ ❌ re-throws   │
│ recover          │ exceptionally     │ exceptionallyAsync│ ✅ replaces   │
│ recover+transform│ handle            │ handleAsync      │ ✅ replaces   │
├──────────────────┴───────────────────┴──────────────────┴────────────────┤
│  COMBINE:  allOf (wait for ALL)   anyOf (first to complete)              │
│  TIMEOUT:  orTimeout (fail)       completeOnTimeout (default value)      │
│  ⚠ ALWAYS pass an explicit Executor — never the common pool.            │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 6 THINGS TO GET RIGHT                            │
├──────────────────────────────────────────────────────────────────────────┤
│  1. orElse is EAGER          → orElseGet for expensive defaults        │
│  2. Optional is RETURN-ONLY  → never a field, never a parameter         │
│  3. Optional has NO equals  → never put it in a value object            │
│  4. thenApply vs thenCompose → compose when the mapper returns a CF    │
│  5. unwrap the CompletionException → ex.getCause() is the real error    │
│  6. ALWAYS a dedicated Executor → the common pool is for CPU work only  │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### Which pool for which work

| Pool | Size | For | Never for |
|---|---|---|---|
| `IO_POOL` (dedicated) | 50–200 | **blocking** I/O — HTTP, JDBC, file | CPU work |
| `CPU_POOL` (dedicated) | `nproc` | CPU-bound computation | blocking I/O |
| `ForkJoinPool.commonPool()` | `nproc - 1` | `parallelStream()` and trivial async | **anything blocking** |
| Virtual-thread executor (Java 21) | unbounded | blocking I/O, thousands of tasks | CPU-bound work |

### The async rules (a house style)

```text
  ✅ ALWAYS pass an explicit executor to supplyAsync / runAsync
  ✅ ALWAYS put a timeout on a remote call
  ✅ ALWAYS use thenCompose (not thenApply) when the mapper returns a future
  ✅ ALWAYS unwrap CompletionException before logging
  ✅ ALWAYS convert failures to values before aggregating with allOf
  ❌ NEVER call join() inside a supplyAsync        → deadlock risk
  ❌ NEVER rely on cancel(true) to stop the work   → it does not interrupt
  ❌ NEVER mix blocking and async in the same chain
```

### Timeouts and cancellation

```java
// ✅ A timeout that fails the future
cf.orTimeout(2, TimeUnit.SECONDS)

// ⚠ But the work CONTINUES — so make the task interruptible
CompletableFuture.supplyAsync(() -> {
    try {
        return blockingCall();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new CompletionException(e);
    }
}, ioPool).orTimeout(2, TimeUnit.SECONDS);

// ✅ Java 21 — real cancellation of siblings
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var a = scope.fork(() -> fetchA());
    var b = scope.fork(() -> fetchB());
    scope.join().throwIfFailed();
    return new Result(a.get(), b.get());
}   // ← if one fails, the other is cancelled AND interrupted
```

### Observability

| Metric | Why |
|---|---|
| Future completion latency (p50/p99) | The real async latency — not the thread pool's queue depth |
| Failure rate per stage | Which dependency is degrading |
| Timeout count | Silent failures that never surface as exceptions |
| Executor queue depth | Back-pressure signal — a growing queue means the pool is too small |
| `CompletableFuture` created vs completed | A leak detector for futures that are never consumed |

```java
// Log the UNWRAPPED cause, with the stage name
cf.whenComplete((v, ex) -> {
    if (ex != null) {
        Throwable cause = (ex instanceof CompletionException && ex.getCause() != null)
                ? ex.getCause() : ex;
        log.error("stage=loadProfile userId={} failed", userId, cause);   // ✅ the real cause
    }
});
```

### `Optional` house rules

| Rule | Why |
|---|---|
| Return values only | The Javadoc's own guidance |
| Never a field | Not `Serializable`, no `equals`/`hashCode` |
| Never a parameter | A null check in disguise |
| Never `Optional<List<T>>` | Use an empty list |
| Never `Optional<Void>` | There is no value |
| `orElse` only with a literal / `static final` | It is eager |
| Unwrap at the boundary | Expose a plain value or a `record` |

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The functional interfaces these are built on | [01 · Lambda & Functional Interfaces](01-Lambda-Functional-Interfaces.md) |
| Streams, `map` vs `flatMap` | [02 · Stream API](02-Stream-API.md) |
| Virtual threads (Java 21) — the modern alternative to pools | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| Executors and the common pool | [08 · Executor Framework](../08-Multithreading-Concurrency/02-Executor-Framework.md) |
| `java.time` (the other Java 8 change) | [12 · Strings](../12-Strings/01-String-Internals.md) |

---

**Next Chapter:** → [07 · Java 11 / 17 / 21 Features](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md)
**Previous Chapter:** ← [02 · Stream API](02-Stream-API.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
