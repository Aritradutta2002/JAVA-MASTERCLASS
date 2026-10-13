# Java 8 Features - Optional & CompletableFuture (In-Depth Masterclass)

## 1. Optional Deep Dive

`Optional<T>` is a single-value container that either contains a non-null reference of type `T` or is empty.

### Design Intent & Philosophy (Brian Goetz's Specification)
> *"Optional is intended to provide a limited mechanism for library method return types where there needed to be a clear way to represent 'no result', and where using `null` for that would be overwhelmingly likely to cause errors."* — **Brian Goetz, Java Language Architect**

`Optional` was **never** designed as a general-purpose replacement for `null`. It was designed specifically to eliminate `NullPointerException` bugs on **method return types**.

---

## 2. Optional Creation & API Mechanics

### Creation Factories
```java
// 1. Definite non-null value (Throws NPE immediately if val is null!)
Optional<String> opt = Optional.of(val); 

// 2. Potentially null value (Returns Optional.empty() if val is null)
Optional<String> opt = Optional.ofNullable(val);

// 3. Explicit empty container (Cached singleton, zero allocation)
Optional<String> empty = Optional.empty();
```

### Extraction & Fallback Methods
```java
// ❌ ANTI-PATTERN: Defeats the purpose of Optional, throws NoSuchElementException
String val = opt.get(); 

// 1. orElse: Eagerly evaluates default value (DANGER!)
String val = opt.orElse(expensiveDatabaseQuery()); // ALWAYS executes even if opt is present!

// 2. orElseGet: Lazily evaluates Supplier only when empty (RECOMMENDED)
String val = opt.orElseGet(() -> expensiveDatabaseQuery());

// 3. orElseThrow: Custom exception on absent value
User user = opt.orElseThrow(() -> new UserNotFoundException(userId));

// 4. ifPresent & ifPresentOrElse (Java 9+)
opt.ifPresent(name -> System.out.println("Hello " + name));

opt.ifPresentOrElse(
    name -> System.out.println("Hello " + name),
    () -> System.out.println("User is anonymous")
);
```

### Transformation: `map` vs `flatMap`
```java
public class User {
    private Address address;
    public Optional<Address> getAddress() { return Optional.ofNullable(address); }
}

public class Address {
    private String street;
    public Optional<String> getStreet() { return Optional.ofNullable(street); }
}

// ❌ Using map produces nested Optionals: Optional<Optional<Address>>
Optional<Optional<Address>> nested = optUser.map(User::getAddress);

// ✅ flatMap flattens the result into a single Optional<T>:
Optional<String> street = optUser
    .flatMap(User::getAddress)
    .flatMap(Address::getStreet)
    .filter(s -> !s.isBlank());
```

### Modern Additions (Java 9–11)
```java
// 1. or(): Provide alternative Optional fallback (Java 9)
Optional<Config> config = loadLocalConfig()
    .or(() -> loadRemoteConfig())
    .or(() -> loadDefaultConfig());

// 2. stream(): Converts Optional to Stream of 0 or 1 element (Java 9)
List<User> validUsers = userIds.stream()
    .map(this::findById)             // Stream<Optional<User>>
    .flatMap(Optional::stream)        // Stream<User> (unwraps present, discards empty!)
    .toList();

// 3. isEmpty(): Inverse of isPresent() (Java 11)
if (opt.isEmpty()) { ... }
```

---

## 3. Optional Anti-Patterns & Memory Impact

### ❌ Anti-Pattern 1: Optional as Class Fields
```java
public class Customer {
    private Optional<String> middleName; // ❌ VIOLATION!
}
```
**Why this is prohibited:**
1. `Optional` does **NOT implement `java.io.Serializable`**. If you attempt to serialize `Customer` (e.g. over RMI, session storage, or caching), the JVM throws `NotSerializableException`.
2. Heavy memory overhead: An `Optional` object instance takes **16 to 24 bytes** of heap memory just to wrap a 4-byte pointer.
3. JPA / Hibernate does not support mapping `Optional` entity attributes natively.

### ❌ Anti-Pattern 2: Optional as Method Parameters
```java
public void findUsers(String role, Optional<Integer> minAge) { ... } // ❌ VIOLATION!
```
**Why this is prohibited:**
- Forces the caller to wrap inputs: `findUsers("ADMIN", Optional.of(21))`.
- The caller can still pass `findUsers("ADMIN", null)`, resulting in an NPE when the method calls `minAge.isPresent()`.
- Use method overloading or nullable annotations (`@Nullable Integer minAge`) instead.

### ❌ Anti-Pattern 3: Primitive Boxing Overhead
Using `Optional<Integer>` or `Optional<Double>` allocates both the wrapper object and the boxed primitive.
- **Solution:** Use specialized primitive optionals: `OptionalInt`, `OptionalLong`, `OptionalDouble`.
- Note: Primitive optionals do not have `map`, `flatMap`, or `filter` methods.

---

## 4. CompletableFuture Deep Dive

`CompletableFuture<T>` (implements `Future<T>` and `CompletionStage<T>`) is the cornerstone of non-blocking, asynchronous reactive pipelines in Java.

### Limitations of Legacy `Future<T>` Solved by CompletableFuture

```
┌───────────────────────────────────────┬───────────────────────────────────────┐
│ Legacy Future<T>                      │ CompletableFuture<T>                  │
├───────────────────────────────────────┼───────────────────────────────────────┤
│ Blocking get() stalls calling thread  │ Non-blocking functional callbacks     │
│ Cannot be manually resolved           │ Manually complete via .complete(v)    │
│ Cannot chain stages without blocking  │ Declarative monadic pipelines         │
│ No native exception recovery          │ exceptionally(), handle() fallbacks   │
│ Cannot combine multiple futures       │ allOf(), anyOf(), thenCombine()       │
└───────────────────────────────────────┴───────────────────────────────────────┘
```

---

## 5. Threading Model: The ForkJoinPool Trap

```java
// Defaults to ForkJoinPool.commonPool():
CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> fetchHttpData());

// Custom dedicated ThreadPool:
ExecutorService ioPool = Executors.newFixedThreadPool(50);
CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> fetchHttpData(), ioPool);
```

### ⚠️ The Fatal `commonPool()` Production Outage Trap
By default, `CompletableFuture.supplyAsync()` and all `*Async()` methods run on `ForkJoinPool.commonPool()`.
- The `commonPool` has a parallelism level equal to:
  $$\text{Runtime.getRuntime().availableProcessors()} - 1$$
- On an 8-core CPU server, the pool has only **7 worker threads**.
- If your asynchronous task makes a blocking network call (HTTP request, database query, RPC) that blocks for 2 seconds, **just 7 concurrent requests will completely saturate and freeze the entire JVM common pool!**
- This starves all parallel streams and other async computations throughout your application.

> **Production Law:** NEVER execute blocking I/O on `ForkJoinPool.commonPool()`. Always pass an explicit, bounded custom `ExecutorService` configured for I/O operations.

### Execution Thread Rules for Chained Stages
```java
CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> "Hello", ioPool);

// Case 1: Synchronous stage (thenApply)
cf.thenApply(s -> s + " World");
// -> Executes in the SAME thread that completed cf.
// -> If cf is ALREADY completed when thenApply is called, it executes in the CALLER'S thread!

// Case 2: Asynchronous stage without executor (thenApplyAsync)
cf.thenApplyAsync(s -> s + " World");
// -> Submits the task to ForkJoinPool.commonPool().

// Case 3: Asynchronous stage with dedicated executor
cf.thenApplyAsync(s -> s + " World", computePool);
// -> Submits task to computePool.
```

---

## 6. CompletionStage Pipeline Matrix

```
                          CompletableFuture Pipeline
                                      │
        ┌─────────────────────────────┼─────────────────────────────┐
        │                             │                             │
   Transform                     Compose                       Combine
(thenApply: T -> U)       (thenCompose: T -> CF<U>)     (thenCombine: CF<A> + CF<B>)
```

### Complete Chaining API

| Operation | Input Parameter | Return Type | Role |
| :--- | :--- | :--- | :--- |
| **`thenApply`** | `Function<T, U>` | `CompletableFuture<U>` | Transform result ($T \to U$) |
| **`thenAccept`** | `Consumer<T>` | `CompletableFuture<Void>` | Consume result (terminal side-effect) |
| **`thenRun`** | `Runnable` | `CompletableFuture<Void>` | Run action after completion |
| **`thenCompose`** | `Function<T, CompletableFuture<U>>` | `CompletableFuture<U>` | **Monadic Bind (flatMap)**: chains dependent async future |
| **`thenCombine`** | `CompletableFuture<U>, BiFunction<T,U,V>` | `CompletableFuture<V>` | Combines two independent futures when **both** complete |
| **`thenAcceptBoth`** | `CompletableFuture<U>, BiConsumer<T,U>` | `CompletableFuture<Void>` | Consumes both results |
| **`applyToEither`** | `CompletableFuture<T>, Function<T,U>` | `CompletableFuture<U>` | Executes on whichever future finishes **first** |
| **`acceptEither`** | `CompletableFuture<T>, Consumer<T>` | `CompletableFuture<Void>` | Consumes first completed result |

---

## 7. Exception Handling in Asynchronous Pipelines

In an asynchronous pipeline, exceptions thrown inside stages are captured by the `CompletableFuture` and wrapped in a `CompletionException` (or `ExecutionException` if using `.get()`). Downstream stages are skipped until an exception handler is reached.

```mermaid
graph LR
    A["Stage 1 (Throws RuntimeException)"] -->|Exception Propagated| B["Stage 2 (Skipped!)"]
    B -->|Exception Propagated| C["exceptionally / handle (Catches & Recovers)"]
    C -->|Healthy Value| D["Stage 3 (Executes Normally)"]
```

### The 3 Error Handling Paradigms

#### 1. `exceptionally(Function<Throwable, T> fn)`
Catches exceptions and returns a default fallback value. Only executes if an upstream error occurred:
```java
CompletableFuture<String> recovered = CompletableFuture.supplyAsync(() -> {
    if (true) throw new RuntimeException("Service down");
    return "Data";
}).exceptionally(ex -> {
    log.error("Failed to fetch data", ex);
    return "Cached-Fallback"; // Recovery value
});
```

#### 2. `handle(BiFunction<T, Throwable, R> fn)`
Always executes, whether the previous stage succeeded or threw an exception. Allows inspection of both result and throwable:
```java
CompletableFuture<String> handled = future.handle((result, throwable) -> {
    if (throwable != null) {
        log.error("Error occurred", throwable);
        return "Recovered";
    }
    return result.toUpperCase();
});
```

#### 3. `whenComplete(BiConsumer<T, Throwable> action)`
Performs side effects (logging, metric recording) without altering the outcome. The original result or exception is propagated downstream untouched:
```java
future.whenComplete((result, ex) -> {
    if (ex != null) metricRegistry.counter("errors").increment();
    else metricRegistry.counter("success").increment();
});
```

---

## 8. Multi-Future Aggregation: Fan-Out / Fan-In

### `allOf`: Parallel Scatter-Gather
`CompletableFuture.allOf()` returns a `CompletableFuture<Void>` that finishes when **all** input futures complete.

```java
public <T> CompletableFuture<List<T>> sequence(List<CompletableFuture<T>> futures) {
    // Fan-out: wait for all to complete
    CompletableFuture<Void> allDone = CompletableFuture.allOf(
        futures.toArray(new CompletableFuture[0])
    );

    // Fan-in: collect results without any thread blocking
    return allDone.thenApply(v ->
        futures.stream()
            .map(CompletableFuture::join) // join() never blocks here because all are finished!
            .toList()
    );
}
```

### `anyOf`: Redundant Fastest-Response Pattern
`CompletableFuture.anyOf()` returns immediately as soon as **any** single future finishes:
```java
CompletableFuture<Object> fastest = CompletableFuture.anyOf(
    fetchFromPrimaryCache(),
    fetchFromSecondaryCache(),
    fetchFromFallbackService()
);
```

---

## 9. Modern Java 9+ Timeout Capabilities

Prior to Java 9, enforcing timeouts required scheduling an external `ScheduledExecutorService` task to call `cf.completeExceptionally()`. Java 9 built timeout support directly into `CompletableFuture`:

```java
CompletableFuture<String> query = CompletableFuture.supplyAsync(this::remoteRpc, ioPool)
    // Option A: Abort pipeline with TimeoutException if it takes > 2 seconds
    .orTimeout(2, TimeUnit.SECONDS)
    
    // Option B: Fallback to default value on timeout without throwing
    .completeOnTimeout("DEFAULT_DATA", 2, TimeUnit.SECONDS);
```

---

## 10. Senior / FAANG Interview Questions & Answers

### Q1: What is the exact difference between `.get()` and `.join()`?
**Answer:**
Both methods block the calling thread until the future completes.
- `.get()` is declared in the `Future<T>` interface and throws **checked exceptions**: `InterruptedException` (if the thread was interrupted) and `ExecutionException` (wrapping the root cause). It requires explicit `try-catch` blocks.
- `.join()` is declared in `CompletableFuture<T>` and throws **unchecked exceptions**: `CompletionException`. It is tailored for use inside functional lambdas and Stream pipelines.

### Q2: Why is `thenCompose()` used instead of `thenApply()` when chaining microservice calls?
**Answer:**
If microservice client methods return `CompletableFuture<T>`, calling `serviceA().thenApply(a -> serviceB(a))` produces a nested `CompletableFuture<CompletableFuture<B>>`. To extract the value, you would have to call `join()` twice. `thenCompose()` acts as a monadic `flatMap`: it flattens the nested future, returning a clean `CompletableFuture<B>`.

### Q3: How do you manually complete a `CompletableFuture` from an asynchronous callback?
**Answer:**
You construct an uncompleted future using `new CompletableFuture<T>()` and pass it to an asynchronous event handler:
```java
public CompletableFuture<Response> sendRequest(Request req) {
    CompletableFuture<Response> future = new CompletableFuture<>();
    asyncClient.execute(req, new Callback() {
        @Override
        public void onSuccess(Response res) { future.complete(res); }
        @Override
        public void onFailure(Throwable err) { future.completeExceptionally(err); }
    });
    return future;
}
```

### Q4: If an exception is thrown in a stage, which thread executes `.exceptionally()`?
**Answer:**
The thread that executed the failed stage will execute downstream `.exceptionally()` or `.handle()` handlers synchronously as the exception propagates down the dependency stack. If `.exceptionallyAsync()` (Java 12+) is used, execution is scheduled on the fork-join pool or specified executor.

---

## 11. Quick Reference Cheat Sheet

```
┌────────────────────────┬────────────────────────────────────────────────────────┐
│ Pattern                │ Recommended Practice                                   │
├────────────────────────┼────────────────────────────────────────────────────────┤
│ Optional Return        │ Use exclusively for method return types representing absence │
│ Optional Fields/Params │ Anti-pattern; use @Nullable or overloading instead     │
│ Default Computation    │ Use orElseGet(Supplier), NOT orElse(method())          │
│ CompletableFuture Pool │ ALWAYS pass dedicated custom Executor for blocking I/O │
│ Dependent Async Task   │ thenCompose() (flatMap)                                │
│ Independent Async Merge│ thenCombine()                                          │
│ Aggregation of All     │ CompletableFuture.allOf() + stream().map(CF::join)     │
│ Timeout Handling       │ .orTimeout(dur, unit) or .completeOnTimeout(...) (Java 9+) │
└────────────────────────┴────────────────────────────────────────────────────────┘
```
