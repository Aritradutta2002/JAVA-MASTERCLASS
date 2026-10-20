# 06 — Java 8 Features · Interview Notes Index

> **The most commercially relevant folder.** Java 8 changed the language permanently:
> functional programming became mainstream, and the "write once, stream everywhere" style
> is now the default expectation in code reviews.

| Priority | Chapters | Why It Matters |
|----------|----------|----------------|
| ⭐⭐⭐⭐⭐ | [02 · Stream API](02-Stream-API.md) | Coding round favourite; internals = senior question |
| ⭐⭐⭐⭐⭐ | [01 · Lambda & Functional Interfaces](01-Lambda-Functional-Interfaces.md) | The foundation of everything modern Java |
| ⭐⭐⭐⭐⭐ | [03 · Optional & CompletableFuture](03-Optional-CompletableFuture.md) | Async programming, the hottest topic |

---

## 📚 Recommended Reading Order

```
01 Lambdas & Functional Interfaces ──▶ 02 Stream API ──▶ 03 Optional & CompletableFuture
```

---

## 🎯 Most Asked in Interviews

| Question | Chapter | Watch Out For |
|----------|---------|--------------|
| Functional interface vs lambda vs method reference | [01](01-Lambda-Functional-Interfaces.md) | The 4 method-reference forms |
| Which functional interfaces are built in? | [01](01-Lambda-Functional-Interfaces.md) | 43 in `java.util.function` |
| Why can't a lambda capture a mutable local variable? | [01](01-Lambda-Functional-Interfaces.md) | Effectively-final |
| What is `this` inside a lambda? | [01](01-Lambda-Functional-Interfaces.md) | It is the **enclosing** object's `this` — unlike an inner class |
| How do Streams work internally? | [02](02-Stream-API.md) | Sink chain + Spliterator |
| Is `parallelStream` always faster? | [02](02-Stream-API.md) | **No** — small N, shared state, boxing |
| What is `Collectors.partitioningBy` vs `groupingBy`? | [02](02-Stream-API.md) | Partitioning is always binary |
| When would you use `Optional`? | [03](03-Optional-CompletableFuture.md) | Return values only — **not** fields or params |
| `orElse` vs `orElseGet`? | [03](03-Optional-CompletableFuture.md) | `orElse` is **always evaluated** |
| How does `CompletableFuture` handle exceptions? | [03](03-Optional-CompletableFuture.md) | `CompletionException` wrapping |
| `join` vs `get`? | [03](03-Optional-CompletableFuture.md) | `join` → unchecked; `get` → checked |

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **Stream laziness** — nothing executes until a terminal op; `filter` on an infinite stream is safe, `forEach` is not.
- [ ] **Stateful vs stateless** — `sorted()` and `distinct()` are stateful barriers.
- [ ] **`Collectors.toMap` duplicate keys** — throws `IllegalStateException` without a merge function.
- [ ] **`parallelStream` + `ForkJoinPool.commonPool()`** — shared across the whole JVM.
- [ ] **Mutating the source inside a stream** — the #1 cause of `ConcurrentModificationException`.

---

## 🧪 Coding Drill (do these without looking)

1. Given `List<Order>`, compute total revenue per customer, sorted descending — one stream pipeline.
2. Find the second-highest salary using a stream, in one pass, without sorting the whole list.
3. Convert a `List<String>` of CSV lines to a `Map<String, Integer>` with `Collectors.groupingBy` + `mapping` + `counting`.
4. Flatten a nested `List<List<T>>` and remove duplicates preserving order.
5. Write a `CompletableFuture` pipeline: fetch user → fetch orders → combine, with proper exception handling.

---

**Related folders:** → [07 · Java 11/17/21](../07-Java-11-17-21-Features/README.md) · → [08 · Concurrency](../08-Multithreading-Concurrency/README.md) · ← [05 · Generics](../05-Generics/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
