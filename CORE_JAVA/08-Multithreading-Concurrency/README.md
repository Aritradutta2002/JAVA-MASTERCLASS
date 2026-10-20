# 08 — Multithreading & Concurrency · Interview Notes Index

> **The highest-difficulty folder and the highest ROI.** Every mid/senior Java interview
> has a concurrency round. Candidates who can reason about *happens-before* and *visibility*
> instead of reciting "use `synchronized`" stand out immediately.

| Priority | Chapters | Why It Matters |
|----------|----------|----------------|
| ⭐⭐⭐⭐⭐ | [03 · Locks & Atomic Classes](03-Locks-Atomic.md) | `ReentrantLock`, CAS, `LongAdder`, `StampedLock` |
| ⭐⭐⭐⭐⭐ | [04 · Concurrency Problems](04-Concurrency-Problems.md) | Deadlock, livelock, starvation, false sharing |
| ⭐⭐⭐⭐⭐ | [01 · Thread Basics](01-Thread-Basics.md) | JMM, happens-before, `volatile`, `synchronized`, `wait/notify` |
| ⭐⭐⭐⭐⭐ | [02 · Executor Framework](02-Executor-Framework.md) | `ThreadPoolExecutor` parameters, rejection, `CompletableFuture` |

---

## 📚 Recommended Reading Order

```
01 Thread Basics ──▶ 02 Executor Framework ──▶ 03 Locks & Atomic ──▶ 04 Concurrency Problems
```

Read **01** first — every later chapter assumes you can reason about happens-before edges.

---

## 🧠 The One-Page Mental Model

```
┌───────────────────────────────────────────────────────────────────┐
│                        YOUR THREAD CODE                          │
└───────────────────────────────┬───────────────────────────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        ▼                       ▼                       ▼
   SHARED STATE          SYNCHRONISATION          TASK SCHEDULING
   (visibility)          (mutual exclusion)      (how work runs)
        │                       │                       │
   volatile               synchronized            ExecutorService
   final fields           ReentrantLock           ThreadPoolExecutor
   Atomic*                ReadWriteLock           ForkJoinPool
                          StampedLock             Virtual Threads
```

> **Caption:** Most "my code is thread-safe" bugs are **visibility** bugs, not mutual-exclusion bugs.
> `volatile` fixes visibility; `synchronized`/`Lock` fixes both.

---

## 🎯 Most Asked in Interviews

| Question | Chapter | Killer Follow-Up |
|----------|---------|------------------|
| Explain the Java Memory Model / happens-before | [01](01-Thread-Basics.md) | "What does `volatile` guarantee?" |
| `volatile` vs `synchronized` vs `AtomicInteger`? | [03](03-Locks-Atomic.md) | "Which is fastest and why?" |
| Does `synchronized` guarantee visibility? | [01](01-Thread-Basics.md) | Yes — that's the monitor's exit/acquire edge |
| Why `notifyAll` over `notify`? | [01](01-Thread-Basics.md) | Lost-notification + spurious wakeup |
| `wait` vs `sleep` vs `join`? | [01](01-Thread-Basics.md) | Different owners, different interruptibility |
| Explain all 7 `ThreadPoolExecutor` parameters | [02](02-Executor-Framework.md) | "What happens when the queue is full?" |
| Which rejection policy should I use? | [02](02-Executor-Framework.md) | `CallerRunsPolicy` for back-pressure |
| How does `ConcurrentHashMap` handle concurrency? | [03](03-Locks-Atomic.md) | "CAS + synchronized on the bin head" |
| `LongAdder` vs `AtomicLong`? | [03](03-Locks-Atomic.md) | Contention striping |
| `StampedLock` vs `ReentrantReadWriteLock`? | [03](03-Locks-Atomic.md) | Optimistic reads, but **not reentrant** |
| How do you prevent a deadlock? | [04](04-Concurrency-Problems.md) | Break one of the 4 Coffman conditions |
| What is false sharing? | [04](04-Concurrency-Problems.md) | Cache-line padding |
| What is the `ThreadLocal` memory-leak in a pool? | [02](02-Executor-Framework.md) | Always `remove()` in a `finally` |

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **Visibility vs atomicity** — `volatile int i; i++` is still a race.
- [ ] **`ThreadLocal` in a thread pool** — the value lives as long as the pooled thread.
- [ ] **Unbounded queues** — `Executors.newFixedThreadPool` uses an *unbounded* `LinkedBlockingQueue` → OOM risk.
- [ ] **Double-checked locking without `volatile`** — the classic "safe until Java 5" bug.
- [ ] **`SimpleDateFormat` is not thread-safe** — use `DateTimeFormatter` (immutable, thread-safe).
- [ ] **Virtual thread pinning** — `synchronized` inside a virtual thread pins the carrier.

---

## 🧪 Coding Drill

1. Implement a bounded thread pool with a custom rejection handler that blocks the caller.
2. Write a correct producer/consumer with `BlockingQueue` and show the deadlock-free variant.
3. Implement a lock-free counter using `AtomicLong` and a `LongAdder` and benchmark the difference under contention.
4. Write a `ThreadLocal` context holder (e.g. trace-id) that is safe inside a thread pool, with cleanup.
5. Produce a deadlock with two locks, detect it with `jstack`, then fix it with lock ordering.

---

**Related folders:** → [09 · Design Patterns](../09-Design-Patterns/README.md) · → [04 · Collections](../04-Collections-Framework/README.md) · ← [07 · Java 11/17/21](../07-Java-11-17-21-Features/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
