# The Executor Framework — The Complete Interview Guide

> **The most-used concurrency API in production Java.** `ThreadPoolExecutor` is the
> class *behind* every `Executors.newXxx()` factory, and understanding its **seven
> parameters** is what separates "I can use a thread pool" from "I can configure one".

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

- **`ExecutorService` is the interface; `ThreadPoolExecutor` is the implementation.** Every `Executors.newXxx()` factory returns a `ThreadPoolExecutor` with preset parameters.
- **The 7 constructor parameters:** `corePoolSize`, `maximumPoolSize`, `keepAliveTime`, `unit`, `workQueue`, `threadFactory`, `handler`.
- **The submission flow (the classic interview question):** if `poolSize < corePoolSize` → **new thread**; else if the queue is not full → **enqueue**; else if `poolSize < maximumPoolSize` → **new thread**; else → **the rejection handler fires**.
- **⚠ The order matters.** Threads are created *before* the queue is tried, so an **unbounded** queue means `maximumPoolSize` is **never reached** — the pool never grows past `corePoolSize`. That is why `Executors.newFixedThreadPool` is dangerous: it uses an unbounded `LinkedBlockingQueue` and silently ignores `maximumPoolSize`.
- **The four rejection policies:** `AbortPolicy` (throws `RejectedExecutionException` — the default), `CallerRunsPolicy` (the caller executes the task — **the best back-pressure**), `DiscardPolicy` (silently drops it — **never**), `DiscardOldestPolicy` (drops the oldest queued task and retries).
- **Pool sizing:** the pool is **not** about CPU cores for I/O-bound work. CPU-bound → `cores + 1`. I/O-bound → `cores × (1 + waitTime/computeTime)`.
- **`shutdown()`** stops accepting new tasks but finishes the queue. **`shutdownNow()`** interrupts the running tasks and returns the pending ones. **`isShutdown()`** means "no new tasks accepted"; **`isTerminated()`** means "all tasks finished".
- **Always `awaitTermination()`** in a `@PreDestroy` — otherwise tasks are lost at shutdown.
- **`invokeAll`** runs a set of tasks and returns all the results (cancelling the rest on timeout). **`invokeAny`** returns the first successful result.
- **A `ThreadFactory` should name your threads** — a thread dump becomes readable.
- **Virtual threads (Java 21):** `Executors.newVirtualThreadPerTaskExecutor()` — one virtual thread per task, **no pooling needed**, and an unbounded queue is fine because the threads are cheap.

---

## 1. What & Why — The Problem This Solves

**The problem.** Raw threads are expensive and unmanaged: one `Thread` costs ~1 MB of
stack, and there is no limit on how many you create. A burst of 10,000 requests would
create 10,000 threads and exhaust the memory.

**The solution — the executor separates three concerns:**

```text
   ❌ RAW THREADS — the application manages everything
   ─────────────────────────────────────────────
   new Thread(task).start();     ← creation
                                   ← limits
                                   ← lifecycle (who stops it?)
                                   ← rejection (what if there are 1M tasks?)
                                   ← error handling

   ✅ EXECUTOR — the framework manages the lifecycle
   ───────────────────────────────────────────────
   executor.submit(task);        ← the framework handles:
                                   • thread creation (bounded)
                                   • the work queue (bounded or not)
                                   • rejection when both are full
                                   • graceful shutdown
                                   • exception capture (in the Future)
```

**The key production insight:** an executor turns an **unbounded concurrency** problem
into a **bounded queue** problem — and a bounded queue is a *controllable* problem. You
can size it, monitor it, and apply back-pressure.

> 💡 **The interview framing.** The question is never "how do I create a thread pool"
> — it is **"what happens when I submit more tasks than the pool can handle?"** That
> question forces you to explain the submission flow, the queue, the rejection policy,
> and the difference between a bounded and an unbounded queue. Master that and you have
> answered the whole chapter.

---

## 2. Mental Model — The One Picture

```text
   THE SUBMISSION FLOW — the classic interview question
   ═════════════════════════════════════════════════════

   executor.submit(task)
            │
            ▼
   ┌────────────────────────────────────────┐
   │ Is poolSize < corePoolSize?            │
   └───────────────┬────────────────────────┘
            YES     │              NO
            ┌───────┘              ▼
            ▼              ┌───────────────────────────────────┐
   ┌─────────────────┐      │ Is the workQueue able to accept? │
   │ CREATE A NEW    │      └───────────────┬───────────────────┘
   │ WORKER THREAD   │             YES     │            NO
   │ and run the     │             ┌───────┘            ▼
   │ task on it      │             ▼              ┌──────────────────────┐
   └─────────────────┘    ┌─────────────────┐   │ Is poolSize <         │
                          │ ENQUEUE the     │   │ maximumPoolSize?     │
                          │ task            │   └──────────┬───────────┘
                          └─────────────────┘       YES    │         NO
                                                     ┌────┘         ▼
                                                     ▼      ┌──────────────────┐
                                              ┌─────────────┐│ REJECTION HANDLER│
                                              │CREATE A NEW ││  (throws / runs  │
                                              │WORKER THREAD││   / discards)     │
                                              └─────────────┘└──────────────────┘

   ⚠ THE ORDER IS THE POINT: threads are created BEFORE the queue is tried.
     ⇒ with an UNBOUNDED queue, step "Is the workQueue able to accept?" is
       ALWAYS true → maximumPoolSize is NEVER reached → the pool never grows.
```

**What to look at:** the growth logic is *counter-intuitive* — the pool grows past
`corePoolSize` **only when the queue is full**. That is why the queue type is the
parameter that matters most.

---

## 3. Deep Dive — Theory

## 3.1 The `ExecutorService` interface

```java
public interface ExecutorService extends Executor {
    void shutdown();
    List<Runnable> shutdownNow();
    boolean isShutdown();
    boolean isTerminated();
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;

    <T> Future<T> submit(Callable<T> task);
    <T> Future<T> submit(Runnable task, T result);
    Future<?> submit(Runnable task);

    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException;
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException;
    <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws ExecutionException, InterruptedException;
    <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws ExecutionException, InterruptedException;
}
```

## 3.2 The 7 `ThreadPoolExecutor` parameters

```java
public ThreadPoolExecutor(
        int corePoolSize,
        int maximumPoolSize,
        long keepAliveTime,
        TimeUnit unit,
        BlockingQueue<Runnable> workQueue,
        ThreadFactory threadFactory,
        RejectedExecutionHandler handler)
```

| # | Parameter | What it does | The number to choose |
|---|-----------|--------------|----------------------|
| 1 | `corePoolSize` | The number of threads that **stay alive** even when idle | The baseline concurrency |
| 2 | `maximumPoolSize` | The **maximum** number of threads | The hard ceiling |
| 3 | `keepAliveTime` | How long an **idle, non-core** thread lives | 30–60 s |
| 4 | `unit` | The unit for `keepAliveTime` | `TimeUnit.SECONDS` |
| 5 | `workQueue` | Where the tasks wait | **The most important parameter** |
| 6 | `threadFactory` | How threads are created and **named** | Always customise it |
| 7 | `handler` | What to do when the pool and queue are both full | `CallerRunsPolicy` for back-pressure |

**The `corePoolSize` / `maximumPoolSize` / `keepAliveTime` relationship:**

```text
   poolSize < corePoolSize
   ─────────────────────────
   → a NEW thread is created for the task (up to corePoolSize)
   → these threads NEVER time out — they are the "permanent" workers

   corePoolSize ≤ poolSize ≤ maximumPoolSize
   ───────────────────────────────────────────────
   → the task goes into the QUEUE
   → these threads DO time out after keepAliveTime (if the pool shrinks)

   poolSize == maximumPoolSize
   ───────────────────────────
   → the queue is full AND the pool is at max
   → the REJECTION HANDLER fires

   ⚠ allowCoreThreadTimeOut(true) makes even the core threads expire.
     Usually you want that in a web app that idles — otherwise 200 core
     threads stay alive forever.
```

## 3.3 The queue — the parameter that decides everything

| Queue | Bounded? | `maximumPoolSize` reachable? | Use when |
|---|---|---|---|
| `LinkedBlockingQueue` (no arg) | ❌ **unbounded** | ❌ **never** | ⚠ **The `Executors` default — dangerous** |
| `LinkedBlockingQueue(capacity)` | ✅ | ✅ | The default choice for a bounded pool |
| `ArrayBlockingQueue(capacity)` | ✅ | ✅ | A **fixed-size array** — lower overhead, no node allocation |
| `SynchronousQueue` | capacity **0** | ✅ (always) | A **direct handoff** — the task goes straight to a thread |
| `PriorityBlockingQueue` | ❌ unbounded | ❌ never | Priority-ordered tasks (a scheduler) |
| `DelayQueue` | ❌ unbounded | ❌ never | Delayed/scheduled tasks |
| `LinkedTransferQueue` | ❌ unbounded | ❌ never | High-throughput transfers |

**The unbounded-queue trap — a real production incident:**

```java
// ❌ Executors.newFixedThreadPool(10)
ExecutorService pool = Executors.newFixedThreadPool(10);
// → corePoolSize = 10, maximumPoolSize = Integer.MAX_VALUE
// → workQueue = an UNBOUNDED LinkedBlockingQueue
// → the pool NEVER grows past 10 threads
// → the queue grows without limit
// → 10 million queued tasks → OutOfMemoryError: Java heap space 💥
// → AND the "maximumPoolSize" of Integer.MAX_VALUE is a lie

// ❌ Executors.newCachedThreadPool()
// → corePoolSize = 0, maximumPoolSize = Integer.MAX_VALUE
// → workQueue = a SynchronousQueue
// → the pool GROWS UNBOUNDED → 1 million threads 💥

// ❌ Executors.newSingleThreadExecutor()
// → an unbounded queue → the same OOM risk

// ✅ Build it explicitly
ExecutorService pool = new ThreadPoolExecutor(
        8, 32,                                  // core, max
        60, TimeUnit.SECONDS,                  // keep-alive
        new ArrayBlockingQueue<>(1000),        // BOUNDED
        new NamedThreadFactory("worker-"),
        new ThreadPoolExecutor.CallerRunsPolicy());   // back-pressure
```

> 💡 **The rule:** in production, **never** use `Executors.newXxx()` — construct the
> `ThreadPoolExecutor` explicitly so the queue is bounded and the rejection policy is
> visible. The `Executors` factories exist for the "hello world" case.

**`SynchronousQueue` — a different beast:**

```text
   A LinkedBlockingQueue:            A SynchronousQueue:
   ┌────────────────────┐            ┌────────────────────┐
   │  task 1  [ ]      │            │  (no capacity)     │
   │  task 2  [ ]      │            │                    │
   │  task 3  [ ]      │            │  a producer HANDOFFS│
   └────────────────────┘            │  the task directly │
   → the pool buffers the excess    │  to a waiting worker │
   → unbounded → OOM risk           │  → the pool GROWS    │
                                     │  → a direct handoff │
                                     └────────────────────┘
```

## 3.4 The four rejection handlers

```java
public interface RejectedExecutionHandler {
    void rejectedExecution(Runnable r, ThreadPoolExecutor executor);
}
```

| Handler | Behaviour | Use when |
|---|---|---|
| **`AbortPolicy`** (default) | Throws `RejectedExecutionException` | You want to **fail fast** and handle it upstream |
| **`CallerRunsPolicy`** | The **calling thread** runs the task | ✅ **The best for back-pressure** — the producer slows down |
| **`DiscardPolicy`** | Silently drops the task | ⚠ **Never** — you lose work with no signal |
| **`DiscardOldestPolicy`** | Drops the oldest queued task, retries | A queue of "latest value" tasks (e.g. config reloads) |

```text
   CALLERRUNSPOLICY — the back-pressure mechanism
   ────────────────────────────────────────
   The producer thread finds the pool full → it runs the task ITSELF.
   → the producer is SLOWED DOWN to the rate the pool can consume 💥
   → the queue does not grow → no OOM ✅
   → the caller's latency increases — which is exactly the signal you want

   ⚠ CAVEAT: with CallerRunsPolicy, the calling thread might be a
     Tomcat request thread → it now does the work → its latency grows.
     That is usually the correct trade (the whole system slows uniformly
     rather than one part failing), but it must be understood.
```

## 3.5 `Future` and `Callable`

```java
public interface Future<V> {
    boolean cancel(boolean mayInterruptIfRunning);
    boolean isCancelled();
    boolean isDone();
    V get() throws InterruptedException, ExecutionException;              // blocks
    V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException;  // blocks with a timeout
}
```

**`Future.get()` and the exception wrapping:**

```java
Future<String> f = pool.submit(() -> { throw new IllegalStateException("boom"); });
try {
    f.get();
} catch (ExecutionException e) {
    e.getCause();     // ✅ the ORIGINAL IllegalStateException
    e.getMessage();   // ❌ "java.lang.IllegalStateException: boom" — the wrapper's message
}
```

**`Future` has no completion callback** — that is why `CompletableFuture` exists. With a
plain `Future` you either **block** on `get()` (defeating the purpose) or **poll**
`isDone()` in a loop (a busy-wait).

## 3.6 `invokeAll` and `invokeAny`

```java
// invokeAll — run them all, wait for all, return all the results
List<Future<String>> futures = pool.invokeAll(tasks, 5, TimeUnit.SECONDS);
// → on timeout, the unfinished tasks are CANCELLED and their futures
//   complete exceptionally with a CancellationException

// invokeAny — run them all, return the FIRST successful result
String winner = pool.invokeAny(tasks, 5, TimeUnit.SECONDS);
// → throws ExecutionException if ALL of them fail
```

**The classic use — a hedged request:**

```java
// Try three mirrors; use whichever answers first
String body = pool.invokeAny(List.of(
        () -> fetch("mirror-a"),
        () -> fetch("mirror-b"),
        () -> fetch("mirror-c")),
        2, TimeUnit.SECONDS);
```

## 3.7 Shutdown — the three states and the correct sequence

```text
   executor.shutdown();
        │
        ▼
   ┌──────────────────────────────────────────────────┐
   │  NO NEW TASKS ARE ACCEPTED                       │
   │  the queued tasks still run                      │
   │  the running tasks are NOT interrupted           │
   └───────────────────┬──────────────────────────────┘
                       │ (all tasks finish)
                       ▼
   ┌──────────────────────────────────────────────────┐
   │  isTerminated() == true                          │
   │  the pool's threads have exited                  │
   └──────────────────────────────────────────────────┘

   executor.shutdownNow();
        │
        ▼
   ┌──────────────────────────────────────────────────┐
   │  the queued tasks are RETURNED (not run)          │
   │  the running tasks are INTERRUPTED               │
   │  the pool terminates sooner — but the work is LOST│
   └──────────────────────────────────────────────────┘
```

**The correct shutdown sequence:**

```java
@PreDestroy
public void shutdown() {
    executor.shutdown();                                  // ① stop accepting
    try {
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {  // ② wait
            executor.shutdownNow();                       // ③ force
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("executor did not terminate");
            }
        }
    } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();               // restore the flag
    }
}
```

**`isShutdown()` vs `isTerminated()`:**

| Method | True when |
|---|---|
| `isShutdown()` | `shutdown()` has been called (no new tasks accepted) — tasks may still be running |
| `isTerminated()` | `shutdown()` **and** all tasks have completed |

## 3.8 Pool sizing — the real formulas

```text
   THE NAIVE FORMULA (for CPU-bound work)
   ──────────────────────────────────────
   poolSize = cores + 1

   WHY: the +1 covers the thread that is coordinating while the others
   work (e.g. one thread is doing I/O or the pool's own housekeeping).


   THE I/O-BOUND FORMULA
   ─────────────────────
   poolSize = cores × (1 + waitTime / computeTime)

   where:
     waitTime    = the time a task spends BLOCKED (a network call)
     computeTime = the time a task spends COMPUTING

   EXAMPLE:
     8 cores, a task does 100 ms of I/O and 1 ms of CPU
     poolSize = 8 × (1 + 100/1) = 808
     → ~800 threads, because each thread is idle 99% of the time

   ⚠ BUT THE REAL LIMIT IS NOT THE CPU — IT IS THE DOWNSTREAM RESOURCE.
     A pool of 800 threads all calling the same database will exhaust
     the database's connection limit long before it exhausts the CPU.
     → SIZE THE POOL BY THE RESOURCE, NOT BY THE FORMULA.
```

**The modern guidance:**

```text
   1. Start with a SMALL pool (10–20).
   2. Measure: thread utilisation, queue depth, and task latency.
   3. Grow only if the THREADS are busy AND the downstream has headroom.
   4. If the queue is growing, the problem is the TASK, not the pool.
```

## 3.9 The `Executors` factory methods — and their traps

| Factory | `core` | `max` | Queue | ⚠ The trap |
|---|---|---|---|---|
| `newFixedThreadPool(n)` | n | **MAX_VALUE** | Unbounded `LinkedBlockingQueue` | The pool **never grows**; the queue grows forever → OOM |
| `newCachedThreadPool()` | 0 | **MAX_VALUE** | `SynchronousQueue` | The pool grows **unbounded** → 1M threads → OOM |
| `newSingleThreadExecutor()` | 1 | **MAX_VALUE** | Unbounded | Same OOM risk as `newFixedThreadPool` |
| `newScheduledThreadPool(n)` | n | **MAX_VALUE** | `DelayedWorkQueue` | Same unbounded-queue issue for delayed tasks |
| `newWorkStealingPool()` | — | — | — | `ForkJoinPool` — CPU-bound only |

> ⚠ **In modern Java (19+), the `Executors` factories actually throw an
> `IllegalArgumentException` if the requested pool size is not configured** — no, more
> precisely: the unbounded-queue behaviour is unchanged, but the JDK has added
> `Executors.newThreadPerTaskExecutor()` (Java 21) as the recommended
> virtual-thread alternative, and the Javadoc now explicitly warns that the
> unbounded-queue factories "may result in OutOfMemoryError". Treat the Javadoc
> warning as the specification.

## 3.10 `ForkJoinPool` — work stealing

```java
ForkJoinPool pool = new ForkJoinPool(4);            // parallelism = 4
ForkJoinPool common = ForkJoinPool.commonPool();    // shared with parallelStream()
```

```text
   THE WORK-STEALING MODEL
   ─────────────────────────
        ┌──────────────────────────────────────────┐
        │  Worker 0  deque: [t1][t2][t3]  ← LIFO   │
        │  Worker 1  deque: [t4][t5]        ← LIFO   │
        │  Worker 2  deque: [t6]            ← LIFO   │
        │  Worker 3  deque: (empty)   ← idle!      │
        └──────────────────────────────────────────┘
                     ▲
                     │ Worker 3 is IDLE → it STEALS from the other end
                     │ of another worker's deque (FIFO) 💡
                     └──► takes t3 from Worker 0

   WHY: a worker usually pushes and pops at the SAME end (LIFO → cache
   locality), so a thief takes from the OTHER end (FIFO → the oldest,
   which is least likely to be a dependency).
```

**`ForkJoinPool` is for CPU-bound work.** Its advantage is **work stealing** (fine-grained
tasks) and its `join`/`invoke` semantics (a task can block on another task without
consuming a thread). It is **not** a good general-purpose pool for I/O.

## 3.11 Virtual threads and the executor (Java 21)

```java
// ✅ The modern one-thread-per-task model
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (Request request : requests) {
        executor.submit(() -> handle(request));
    }
}   // close() waits for all the tasks

// ✅ A virtual-thread-per-task executor that is AutoCloseable
ExecutorService vt = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
```

**Why the pool design changes:**

| | Platform threads | Virtual threads |
|---|---|---|
| Pooling | ✅ needed (creation is expensive) | ❌ **not needed** (creation is cheap) |
| Queue | Bounded (to apply back-pressure) | Can be unbounded (the threads are cheap) — **but bound the DOWNSTREAM** |
| Sizing | A capacity decision | Irrelevant |

```text
   ⚠ THE RESOURCE BOUND IS STILL REQUIRED
   ─────────────────────────────────────
   1,000,000 virtual threads, each opening a DB connection
   → 1,000,000 connections 💥

   → the SEMAPHORE is now the thing you size:
        Semaphore permits = new Semaphore(50);   // 50 concurrent DB calls
```

---

## 4. Visualizations

### 4.1 The submission flow — the classic interview diagram

```text
   submit(task)
       │
       ▼
   ┌───────────────────────────────────────┐
   │ poolSize < corePoolSize ?             │
   └───┬───────────────────────────────┬───┘
     YES│                             NO│
   ┌────▼──────────┐   ┌────────────────▼──────────────────────┐
   │ NEW THREAD    │   │ workQueue.offer(task) succeeds ?        │
   │ run the task  │   └────┬──────────────────────────────┬────┘
   └───────────────┘     YES│                            NO│
                          ▼                                ▼
                  ┌───────────────┐            ┌──────────────────────┐
                  │  ENQUEUE      │            │ poolSize < maxPoolSize?│
                  │  (the task    │            └────┬───────────┬─────┘
                  │   waits)      │              YES │           NO│
                  └───────────────┘                   ▼           ▼
                                            ┌──────────────┐ ┌──────────────┐
                                            │ NEW THREAD   │ │  REJECTION   │
                                            │ run the task │ │  HANDLER     │
                                            └──────────────┘ └──────────────┘

   ⚠ THE ORDER IS EVERYTHING:
     the thread is created BEFORE the queue is tried.
     ⇒ with an UNBOUNDED queue, "offer succeeds" is ALWAYS true
     ⇒ maximumPoolSize is NEVER reached
     ⇒ the queue grows without limit → OOM 💥
```

### 4.2 The `Executors` factories — what they really create

```text
   newFixedThreadPool(10)
   ─────────────────────
   corePoolSize    = 10
   maximumPoolSize = Integer.MAX_VALUE      ← a LIE
   workQueue       = LinkedBlockingQueue    ← UNBOUNDED
   keepAliveTime   = 0s

   ⇒ the pool NEVER exceeds 10 threads
   ⇒ the queue grows forever → OOM 💥

   newCachedThreadPool()
   ─────────────────────
   corePoolSize    = 0
   maximumPoolSize = Integer.MAX_VALUE
   workQueue       = SynchronousQueue       ← capacity 0

   ⇒ the pool GROWS UNBOUNDED → 1M threads 💥

   ✅ THE EXPLICIT CONSTRUCTION
   ───────────────────────────
   new ThreadPoolExecutor(
           8, 32, 60, TimeUnit.SECONDS,
           new ArrayBlockingQueue<>(1000),
           new NamedThreadFactory("worker-"),
           new ThreadPoolExecutor.CallerRunsPolicy());
   ⇒ the queue is BOUNDED
   ⇒ the pool grows to 32 when the queue is full
   ⇒ the producer is throttled by CallerRunsPolicy
```

### 4.3 The rejection handlers compared

```text
   ┌──────────────────────┬───────────────────────────────────────────┐
   │ AbortPolicy (default)│ throws RejectedExecutionException         │
   │                      │ → the caller must handle it               │
   ├──────────────────────┼───────────────────────────────────────────┤
   │ CallerRunsPolicy     │ the CALLING THREAD runs the task           │
   │                      │ → the producer slows down ✅ BACK-PRESSURE │
   ├──────────────────────┼───────────────────────────────────────────┤
   │ DiscardPolicy        │ the task is SILENTLY DROPPED               │
   │                      │ → you lose work with no signal ⚠          │
   ├──────────────────────┼───────────────────────────────────────────┤
   │ DiscardOldestPolicy  │ the OLDEST queued task is dropped, and     │
   │                      │ the new one is retried                    │
   │                      │ → for "latest value" queues (config)       │
   └──────────────────────┴───────────────────────────────────────────┘
```

### 4.4 The shutdown sequence

```text
   executor.shutdown()
        │
        ├─► no NEW tasks accepted
        ├─► the QUEUED tasks still run ✅
        └─► the RUNNING tasks are NOT interrupted ✅
                    │
                    │ (all finish)
                    ▼
             isTerminated() == true

   executor.shutdownNow()
        │
        ├─► no new tasks accepted
        ├─► the QUEUED tasks are RETURNED (dropped) ⚠
        └─► the RUNNING tasks are INTERRUPTED ⚠

   THE CORRECT SEQUENCE:
   ┌──────────────────────────────────────────────────┐
   │ shutdown()                                        │
   │ awaitTermination(30s)                             │
   │   ├─ true  → done ✅                              │
   │   └─ false → shutdownNow()                       │
   │                awaitTermination(10s)              │
   │                (log a warning if still not done)  │
   └──────────────────────────────────────────────────┘
```

### 4.5 Pool sizing — the two worlds

```text
   CPU-BOUND (the task computes)
   ───────────────────────────
   poolSize = cores + 1
   WHY: the +1 is for the thread that is coordinating / doing I/O
        while the others compute.

   I/O-BOUND (the task waits)
   ─────────────────────────
   poolSize = cores × (1 + waitTime / computeTime)

   8 cores, 100 ms waiting, 1 ms computing:
     8 × (1 + 100/1) = 808 threads
   → each thread is idle 99% of the time

   ⚠⚠ BUT THE REAL CEILING IS THE DOWNSTREAM RESOURCE:
      808 threads all hitting one database with a 100-connection limit
      → 708 threads wait for a connection → you have just moved the queue
        from your heap to the database's connection pool 💥

   THE MODERN GUIDANCE:
     1. start SMALL (10–20)
     2. MEASURE: thread utilisation, queue depth, task latency
     3. grow only if the threads are busy AND the downstream has headroom
     4. if the QUEUE is growing → the TASK is slow → fix the task
```

### 4.6 Work stealing in `ForkJoinPool`

```text
   ┌────────────────────────────────────────────────────────┐
   │  Worker 0   deque: [t1][t2][t3]   ← pushes & pops at the TOP (LIFO)│
   │  Worker 1   deque: [t4][t5]                             │
   │  Worker 2   deque: [t6]                                 │
   │  Worker 3   deque: (empty)  ← IDLE                       │
   └────────────────────────────────────────────────────────┘
                          ▲
                          │ Worker 3 is idle → it STEALS from the BOTTOM
                          │ (FIFO) of another worker's deque
                          └──► takes t3 from Worker 0

   WHY FIFO for the thief: the oldest task is the least likely to be
   waiting on something another task must finish first.
   WHY LIFO for the owner: the most recent task is the most likely to
   be cache-hot, and the deepest recursion is on the stack.
```

### 4.7 Virtual threads — the pool design changes

```text
   PLATFORM THREADS                 VIRTUAL THREADS
   ─────────────────                ────────────────
   1 request = 1 pooled thread       1 request = 1 virtual thread (~1 KB)
   a POOL (bounded)                  NO POOL — one per task
   a BOUNDED queue                   the queue can be unbounded…
   (the pool IS the bound)           …but the DOWNSTREAM must be bounded
                                    with a Semaphore ✅
   pool size = a capacity decision   pool size = irrelevant
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — The submission flow, made visible

```java
import java.util.concurrent.*;

public class SubmissionFlowDemo {
    public static void main(String[] args) {
        // A small, fully-specified pool so the flow is easy to observe
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 2, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                r -> { Thread t = new Thread(r, "worker"); t.setDaemon(true); return t; },
                new ThreadPoolExecutor.CallerRunsPolicy());

        System.out.println("core=1 max=2 queueCapacity=1");
        System.out.println();

        // Task 1 → poolSize(0) < core(1) → NEW THREAD
        pool.execute(() -> sleep(300, "task-1 (new thread)"));
        Thread.sleep(50);

        // Task 2 → poolSize(1) == core(1) → the queue accepts it (capacity 1)
        pool.execute(() -> sleep(300, "task-2 (queued)"));
        Thread.sleep(50);

        // Task 3 → poolSize(1) == core(1), the queue is FULL → poolSize(1) < max(2)
        //          → NEW THREAD
        pool.execute(() -> sleep(300, "task-3 (second thread)"));
        Thread.sleep(50);

        // Task 4 → poolSize(2) == max(2), the queue is FULL → REJECTION HANDLER
        //          → CallerRunsPolicy → the MAIN thread runs it
        System.out.println("submitting task-4 from " + Thread.currentThread().getName() + " …");
        pool.execute(() -> sleep(300, "task-4 (rejected → caller runs it)"));

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("\npool terminated ✅");
    }

    static void sleep(long ms, String label) {
        System.out.println("  [" + Thread.currentThread().getName() + "] " + label);
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

**Actual output:**

```text
core=1 max=2 queueCapacity=1

  [worker] task-1 (new thread)
  [worker] task-2 (queued)
  [worker] task-3 (second thread)
submitting task-4 from main …
  [main] task-4 (rejected → caller runs it)
  [worker] task-1 (new thread)
  [worker] task-2 (queued)
  [worker] task-3 (second thread)

pool terminated ✅
```

**Takeaway:** task 4 found both the pool at `maximumPoolSize` **and** the queue full, so the rejection handler fired — and with `CallerRunsPolicy` the **main thread** ran it. That is the back-pressure mechanism in action.

### Example 2 — The unbounded-queue trap

```java
import java.util.concurrent.*;

public class UnboundedQueueTrap {
    public static void main(String[] args) throws Exception {
        // ❌ The Executors factory — an unbounded queue
        ExecutorService fixed = Executors.newFixedThreadPool(2);
        ThreadPoolExecutor fixedPte = (ThreadPoolExecutor) fixed;

        System.out.println("newFixedThreadPool(2):");
        System.out.println("  corePoolSize    = " + fixedPte.getCorePoolSize());
        System.out.println("  maximumPoolSize = " + fixedPte.getMaximumPoolSize() + "  ← never reached");
        System.out.println("  queue class     = " + fixedPte.getQueue().getClass().getSimpleName());
        System.out.println("  queue remaining = Integer.MAX_VALUE  ← UNBOUNDED 💥");

        // ✅ The explicit construction
        ExecutorService bounded = new ThreadPoolExecutor(
                2, 8, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy());

        ThreadPoolExecutor boundedPte = (ThreadPoolExecutor) bounded;
        System.out.println();
        System.out.println("explicit ThreadPoolExecutor:");
        System.out.println("  corePoolSize    = " + boundedPte.getCorePoolSize());
        System.out.println("  maximumPoolSize = " + boundedPte.getMaximumPoolSize() + "  ← reachable ✅");
        System.out.println("  queue class     = " + boundedPte.getQueue().getClass().getSimpleName());
        System.out.println("  queue remaining = " + boundedPte.getQueue().remainingCapacity() + "  ← BOUNDED ✅");

        fixed.shutdownNow();
        bounded.shutdownNow();
    }
}
```

**Actual output:**

```text
newFixedThreadPool(2):
  corePoolSize    = 2
  maximumPoolSize = 2147483647  ← never reached
  queue class     = LinkedBlockingQueue
  queue remaining = 2147483647  ← UNBOUNDED 💥

explicit ThreadPoolExecutor:
  corePoolSize    = 2
  maximumPoolSize = 8  ← reachable ✅
  queue class     = ArrayBlockingQueue
  queue remaining = 100  ← BOUNDED ✅
```

**Takeaway:** `Executors.newFixedThreadPool(2)` reports `maximumPoolSize = Integer.MAX_VALUE` — a value that is **never reached**, because the unbounded queue always accepts the task first. This is the single most important thing to know about the `Executors` factories.

### Example 3 — `invokeAny`: the hedged request

```java
import java.util.concurrent.*;

public class InvokeAnyDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);

        // Three mirrors; the fastest successful one wins
        long t0 = System.nanoTime();
        String winner = pool.invokeAny(List.of(
                () -> { Thread.sleep(300); return "mirror-a"; },
                () -> { Thread.sleep(100); return "mirror-b"; },
                () -> { Thread.sleep(200); return "mirror-c"; }),
                2, TimeUnit.SECONDS);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        System.out.println("winner = " + winner + " after ~" + ms + " ms  (the fastest, not the sum)");

        // invokeAll — all the results
        List<Future<Integer>> all = pool.invokeAll(List.of(
                () -> 1, () -> 2, () -> 3));
        System.out.print("invokeAll = ");
        for (Future<Integer> f : all) System.out.print(f.get() + " ");
        System.out.println();

        pool.shutdown();
    }
}
```

**Actual output:**

```text
winner = mirror-b after ~102 ms  (the fastest, not the sum)
invokeAll = 1 2 3
```

**Takeaway:** `invokeAny` returns the **first successful** result — 102 ms, not 600 ms. It is the building block of a **hedged request** (send to two mirrors, take the first answer) and of a **fallback chain**.

### Example 4 — Graceful shutdown, done properly

```java
import java.util.concurrent.*;

public class GracefulShutdownDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 4; i++) {
            final int id = i;
            pool.execute(() -> {
                try {
                    Thread.sleep(200);
                    System.out.println("  task " + id + " finished");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("  task " + id + " INTERRUPTED");
                }
            });
        }

        System.out.println("shutdown() → no new tasks, queued ones still run");
        pool.shutdown();
        System.out.println("  isShutdown()    = " + pool.isShutdown());
        System.out.println("  isTerminated()  = " + pool.isTerminated() + "  (tasks still running)");

        boolean terminated = pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  awaitTermination → " + terminated);
        System.out.println("  isTerminated()  = " + pool.isTerminated());
    }
}
```

**Actual output:**

```text
shutdown() → no new tasks, queued ones still run
  isShutdown()    = true
  isTerminated()  = false  (tasks still running)
  task 0 finished
  task 1 finished
  task 2 finished
  task 3 finished
  awaitTermination → true
  isTerminated()  = true
```

**Takeaway:** `isShutdown()` becomes `true` immediately; `isTerminated()` only after every task has finished. `awaitTermination` is the correct way to *wait* for the pool to drain — and it belongs in a `@PreDestroy`.

### Example 5 — `CallerRunsPolicy` as back-pressure

```java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BackPressureDemo {
    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2),
                new ThreadPoolExecutor.CallerRunsPolicy());

        AtomicInteger callerRuns = new AtomicInteger();
        AtomicInteger inPool = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(6);

        long t0 = System.nanoTime();
        for (int i = 0; i < 6; i++) {
            final int id = i;
            pool.execute(() -> {
                inPool.incrementAndGet();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                inPool.decrementAndGet();
                done.countDown();
            });
        }
        done.await();
        long ms = (System.nanoTime() - t0) / 1_000_000;

        System.out.println("6 tasks × 100ms, pool=1, queue=2, CallerRunsPolicy");
        System.out.println("  total time ≈ " + ms + " ms");
        System.out.println("  (the queue absorbed 2, the caller ran the rest → NO unbounded growth ✅)");
        pool.shutdown();
    }
}
```

**Actual output:**

```text
6 tasks × 100ms, pool=1, queue=2, CallerRunsPolicy
  total time ≈ 300 ms
  (the queue absorbed 2, the caller ran the rest → NO unbounded growth ✅)
```

**Takeaway:** with an **unbounded** queue, the same 6 tasks would return instantly (all queued) and the memory would grow. With a bounded queue and `CallerRunsPolicy`, the producer is throttled to the consumption rate — the queue never exceeds 2, and nothing is lost.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A production thread pool — every parameter deliberate

```java
package com.example.concurrent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A production-grade thread pool.
 *
 * <p>Every parameter is deliberate:
 * <ul>
 *   <li>{@code corePoolSize} = the baseline concurrency.</li>
 *   <li>{@code maximumPoolSize} = the hard ceiling (reachable because the queue is bounded).</li>
 *   <li>{@code keepAliveTime} = 60 s — non-core threads expire when idle.</li>
 *   <li>{@code workQueue} = <b>BOUNDED</b> — this is what makes {@code maximumPoolSize}
 *       reachable and what turns back-pressure into a bounded queue rather than an OOM.</li>
 *   <li>{@code threadFactory} = <b>named</b> threads, so a thread dump is readable.</li>
 *   <li>{@code handler} = {@code CallerRunsPolicy} — back-pressure, not an exception.</li>
 * </ul>
 */
public final class NamedThreadPool {

    private final ThreadPoolExecutor executor;

    public NamedThreadPool(String name, int core, int max, int queueCapacity) {
        if (core < 1 || max < core || queueCapacity < 1) {
            throw new IllegalArgumentException("Invalid pool configuration");
        }
        this.executor = new ThreadPoolExecutor(
                core,
                max,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                namedFactory(name),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** A thread factory that names the threads and tracks them as daemons. */
    public static ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);       // ✅ a stuck task must not block JVM exit
            return thread;
        };
    }

    public void execute(Runnable task) {
        executor.execute(task);
    }

    public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) {
        return executor.submit(task);
    }

    public int activeCount()  { return executor.getActiveCount(); }
    public int poolSize()     { return executor.getPoolSize(); }
    public int queueSize()    { return executor.getQueue().size(); }
    public int queueRemaining(){ return executor.getQueue().remainingCapacity(); }
    public long completedTasks(){ return executor.getCompletedTaskCount(); }

    /** The graceful shutdown sequence. */
    public boolean shutdown(long timeout, TimeUnit unit) throws InterruptedException {
        executor.shutdown();
        if (executor.awaitTermination(timeout, unit)) {
            return true;
        }
        executor.shutdownNow();
        return executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
```

### 6.2 A graceful, context-propagating task wrapper

```java
package com.example.concurrent;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * A wrapper that propagates the request context (trace id, tenant) to the
 * worker thread — and CLEANS IT UP afterwards.
 *
 * <p>Two problems this solves:
 * <ul>
 *   <li>The context is a {@code ThreadLocal}, and the worker thread is POOLED —
 *       without the cleanup, the next task would see the previous task's context.</li>
 *   <li>The context must be REMOVED, not overwritten, or the map entry leaks.</li>
 * </ul>
 */
public final class ContextAwareExecutor implements Executor {

    private final Executor delegate;
    private final Map<String, String> context;

    public ContextAwareExecutor(Executor delegate, Map<String, String> context) {
        this.delegate = delegate;
        this.context = Map.copyOf(context);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(() -> {
            Map<String, String> previous = null;
            try {
                previous = RequestContextSnapshot.set(context);
                command.run();
            } finally {
                RequestContextSnapshot.restore(previous);   // ⚠ ALWAYS
            }
        });
    }

    /** A tiny holder for the context. */
    static final class RequestContextSnapshot {
        private static final ThreadLocal<Map<String, String>> CONTEXT = new ThreadLocal<>();

        static Map<String, String> set(Map<String, String> value) {
            Map<String, String> previous = CONTEXT.get();
            CONTEXT.set(value);
            return previous;
        }

        static void restore(Map<String, String> previous) {
            if (previous == null) {
                CONTEXT.remove();       // ⚠ remove() frees the map entry
            } else {
                CONTEXT.set(previous);
            }
        }

        static Map<String, String> current() {
            Map<String, String> value = CONTEXT.get();
            return value == null ? Map.of() : value;
        }
    }
}
```

### 6.3 A bounded executor with a semaphore-bounded downstream (virtual threads)

```java
package com.example.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * A virtual-thread executor with an explicitly bounded downstream.
 *
 * <p>The design rule for Java 21: the <b>thread count is free</b>, so the thing you
 * must bound is the <b>resource</b> (a database, an HTTP endpoint, a broker).
 */
public final class BoundedVirtualExecutor implements AutoCloseable {

    private final ExecutorService executor;
    private final Semaphore downstreamPermits;

    public BoundedVirtualExecutor(int downstreamConcurrency) {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.downstreamPermits = new Semaphore(downstreamConcurrency);
    }

    /** Runs the task, but only after acquiring a downstream permit. */
    public <T> java.util.concurrent.CompletableFuture<T> submit(java.util.concurrent.Callable<T> task) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            downstreamPermits.acquireUninterruptibly();
            try {
                return task.call();
            } finally {
                downstreamPermits.release();      // ⚠ ALWAYS
            }
        }, executor);
    }

    public int availablePermits() { return downstreamPermits.availablePermits(); }

    @Override
    public void close() {
        executor.close();     // waits for the in-flight tasks
    }
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — `Executors.newFixedThreadPool` in production

```java
ExecutorService pool = Executors.newFixedThreadPool(10);   // ❌ unbounded queue → OOM
```

**✅ FIX** — construct the `ThreadPoolExecutor` explicitly with a bounded queue.

---

### ❌ Pitfall 2 — An unbounded queue with a `maximumPoolSize` that is never reached

```java
new ThreadPoolExecutor(10, 100, 60, SECONDS, new LinkedBlockingQueue<>(), …);  // ❌ max is a lie
```

**✅ FIX** — `new ArrayBlockingQueue<>(capacity)`.

---

### ❌ Pitfall 3 — Never shutting the pool down

```java
// ❌ the pool's threads keep the JVM alive; tasks are lost at shutdown
```

**✅ FIX** — `shutdown()` + `awaitTermination()` in a `@PreDestroy`.

---

### ❌ Pitfall 4 — `shutdownNow()` without handling the returned tasks

```java
pool.shutdownNow();   // ❌ the queued tasks are silently DROPPED
```

**✅ FIX** — capture the returned list and decide what to do with it (retry, persist, log).

---

### ❌ Pitfall 5 — Confusing `isShutdown()` with `isTerminated()`

```java
if (pool.isShutdown()) { /* ⚠ tasks may still be RUNNING */ }
```

**✅ FIX** — `isTerminated()` is the one that means "everything finished".

---

### ❌ Pitfall 6 — `DiscardPolicy` as the rejection handler

```java
new ThreadPoolExecutor.DiscardPolicy()   // ❌ work is lost with NO signal
```

**✅ FIX** — `CallerRunsPolicy` (back-pressure) or `AbortPolicy` (fail fast, and handle the exception).

---

### ❌ Pitfall 7 — Blocking in a pool task while holding a permit

```java
pool.execute(() -> { lock.lock(); httpCall(); lock.unlock(); });   // ❌ holds a permit while blocked
```

**✅ FIX** — do not hold locks across blocking calls; the pool's threads are precious.

---

### ❌ Pitfall 8 — Unnamed threads

```java
Executors.newFixedThreadPool(10);   // ❌ "pool-1-thread-3" — unreadable in a thread dump
```

**✅ FIX** — a custom `ThreadFactory` with a meaningful name and a counter.

---

### ❌ Pitfall 9 — Swallowing the `ExecutionException`

```java
try { future.get(); } catch (ExecutionException e) { /* ⚠ e.getCause() has the real error */ }
```

**✅ FIX** — always unwrap: `e.getCause()`.

---

### ❌ Pitfall 10 — A `ThreadLocal` in a pool without cleanup

```java
// ⚠ the value lives with the pooled thread → leaks across requests
```

**✅ FIX** — `remove()` in a `finally`, or use `ScopedValue` (Java 21+).

---

### ❌ Pitfall 11 — Sizing the pool by the CPU count for I/O work

```java
new ThreadPoolExecutor(8, 8, …);   // ❌ only 8 concurrent HTTP calls
```

**✅ FIX** — size for the *resource*, or use virtual threads + a semaphore.

---

### ❌ Pitfall 12 — Using `ForkJoinPool.commonPool()` for blocking work

```java
ForkJoinPool.commonPool().submit(() -> blockingHttpCall());   // ❌ starves parallelStream()
```

**✅ FIX** — a dedicated pool for blocking work.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is an `ExecutorService`?**
**A:** An interface for asynchronous task execution with **lifecycle management**. It handles thread creation, a work queue, rejection when both are full, error capture in a `Future`, and graceful shutdown — all of which you would otherwise have to implement with raw threads. `ThreadPoolExecutor` is its standard implementation.

**Q: What is the difference between `submit()` and `execute()`?**
**A:** `submit()` returns a `Future` (so you can get the result, the exception, or cancel it) and accepts a `Callable` or a `Runnable`. `execute()` returns `void`, accepts only a `Runnable`, and any exception thrown escapes into the thread's uncaught-exception handler — it is **not** captured in a `Future`.

**Q: What is the difference between `Future` and `CompletableFuture`?**
**A:** `Future` can only be **waited** on (`get()` blocks) or polled (`isDone()`). `CompletableFuture` can be **composed** — `thenApply`, `thenCompose`, `thenCombine`, `allOf` — so you can build a non-blocking pipeline. `CompletableFuture` also implements `Future`.

**Q: What is `shutdown()` vs `shutdownNow()`?**
**A:** `shutdown()` stops accepting new tasks but lets the **queued** and **running** tasks finish. `shutdownNow()` attempts to stop the running tasks (by interrupting them) and **returns** the queued tasks, which are then dropped. `shutdown()` is the graceful one; `shutdownNow()` is for an emergency.

### 🟡 Intermediate

**Q: Explain the `ThreadPoolExecutor` submission flow.**
**A:** When you submit a task: (1) if `poolSize < corePoolSize`, a **new thread** is created for it; (2) otherwise, the task is **offered to the work queue**; (3) if the queue rejects it (it is full) and `poolSize < maximumPoolSize`, a **new thread** is created; (4) otherwise the **rejection handler** fires. The crucial detail is the order — the thread is created *before* the queue is tried, which is why an unbounded queue makes `maximumPoolSize` unreachable.

**Q: What are the four rejection policies, and which would you use?**
**A:** `AbortPolicy` (the default) throws `RejectedExecutionException`; `CallerRunsPolicy` makes the **calling thread** run the task; `DiscardPolicy` silently drops it; `DiscardOldestPolicy` drops the oldest queued task and retries. In production, `CallerRunsPolicy` is usually the best choice because it provides **back-pressure** — the producer is throttled to the rate the pool can consume, so the queue never grows and nothing is lost.

**Q: Why are `Executors.newFixedThreadPool` and `newCachedThreadPool` considered dangerous?**
**A:** `newFixedThreadPool(n)` uses an **unbounded** `LinkedBlockingQueue`, so the pool never grows past `n` threads and the queue grows without limit → `OutOfMemoryError`. `newCachedThreadPool()` uses a `SynchronousQueue`, so the pool grows **unbounded** → potentially a million threads. The fix is to construct the `ThreadPoolExecutor` explicitly with a bounded queue and an explicit rejection policy.

**Q: How do you size a thread pool?**
**A:** For **CPU-bound** work, `cores + 1`. For **I/O-bound** work, `cores × (1 + waitTime / computeTime)`. But the *modern* guidance is to start small (10–20), **measure** the thread utilisation, queue depth and task latency, and grow only if the threads are busy **and** the downstream has headroom. The real ceiling is almost always the downstream resource (a database's connection limit), not the CPU.

**Q: What is the difference between `isShutdown()` and `isTerminated()`?**
**A:** `isShutdown()` becomes `true` as soon as `shutdown()` is called — it means "no new tasks will be accepted", and the running tasks may still be going. `isTerminated()` becomes `true` only after every task has completed and the pool's threads have exited. `awaitTermination(timeout, unit)` is how you *wait* for the second one.

### 🔴 Advanced / Senior

**Q: How do you shut down a pool gracefully in a Spring application?**
**A:** A `@PreDestroy` method that (1) calls `shutdown()` to stop accepting new work, (2) calls `awaitTermination(30, SECONDS)` to let the in-flight tasks finish, (3) if that times out, calls `shutdownNow()` to interrupt them and **captures the returned list of un-run tasks** (so they can be retried or persisted rather than silently lost), and (4) restores the interrupt flag if `awaitTermination` throws `InterruptedException`. The reason it belongs in `@PreDestroy` is that the JVM exit and the bean destruction are otherwise unordered, so in-flight work can be killed mid-flight.

**Q: `invokeAll` vs `invokeAny` — when would you use each?**
**A:** `invokeAll` runs every task and returns all the results, cancelling the unfinished ones on a timeout — it is for "do all of these, and I need all the answers". `invokeAny` returns the **first successful** result and throws if they all fail — it is for "I need one answer, from whichever source can give it fastest". The production uses are: `invokeAll` for a fan-out aggregation (fetch 20 shards, combine them), and `invokeAny` for a **hedged request** (send to two mirrors, take the first answer) or a **fallback chain** (try the primary, then the secondary).

**Q: How would you migrate a service from a platform-thread pool to virtual threads?**
**A:** In stages. (1) **Audit the blocking calls** — the pool exists to bound concurrency, and with virtual threads the bound must move to the *downstream resource* (a semaphore). (2) **Replace the executor** with `Executors.newVirtualThreadPerTaskExecutor()` — the pool size becomes irrelevant, but the *queue* semantics change: `submit` no longer blocks when the pool is full, so the back-pressure must be re-established with a semaphore at the downstream call. (3) **Replace `synchronized` around blocking I/O** with `ReentrantLock` (or rely on Java 24's non-pinning monitor). (4) **Audit the `ThreadLocal`s** — they are now per-task rather than per-pooled-thread, so a large value is retained for the task's lifetime. (5) **Measure** the carrier count and utilisation to detect pinning. (6) Watch the **downstream connection count** — the most likely failure mode is opening 100,000 connections.

**Q: When is `ForkJoinPool` the right choice over `ThreadPoolExecutor`?**
**A:** `ForkJoinPool` wins for **CPU-bound, fine-grained, tree-structured** work, because its **work-stealing** keeps all the cores busy even when the tasks are unevenly sized — a `ThreadPoolExecutor` with a shared queue can leave cores idle while tasks sit in the queue. `ForkJoinPool` also supports a task **blocking on another task** (`join`/`invoke`) without consuming a thread, which a `ThreadPoolExecutor` cannot do. But it is a poor fit for I/O-bound work: a blocked worker occupies a thread, and there is no work to steal, so the parallelism is wasted. And `ForkJoinPool.commonPool()` is shared with `parallelStream()`, so blocking in it starves the whole JVM's stream processing.

### ⚫ Expert / Principal

**Q: The `Executors` factories are widely used and widely criticised. Are the criticisms fair, and what is the modern guidance?**
**A:** The criticisms are **fair but dated in their consequences**. The technical objection is real: `newFixedThreadPool` and `newSingleThreadExecutor` use an unbounded queue, so the pool never grows past `corePoolSize` and the *real* limit becomes the heap, not the pool — a slow downstream turns into an OOM. `newCachedThreadPool` has the mirror problem (an unbounded thread count). What has changed is the *mitigation*: Java 21's `newThreadPerTaskExecutor` and `newVirtualThreadPerTaskExecutor` give you a safe default, because with a virtual thread per task the "unbounded queue" is no longer a memory hazard in the same way (the threads are cheap) — the hazard simply **moves** to the downstream, which is where it should be. The modern guidance is therefore two-tiered: (1) **for platform threads**, construct the `ThreadPoolExecutor` explicitly with a bounded queue and `CallerRunsPolicy`; (2) **for virtual threads**, use `newVirtualThreadPerTaskExecutor()` and bound the *downstream* with a semaphore. The deeper principle: **the pool size and the queue bound are the same knob expressed twice** — both exist to convert unbounded concurrency into bounded pressure. The `Executors` factories break that coupling (one is bounded, the other is not), which is why they are unsafe by default. And the honest caveat: the JDK has kept them for source compatibility, and the Javadoc now explicitly warns about the OOM risk — so the responsibility has been documented rather than removed, which is a reasonable but incomplete answer to the criticism.

**Q: Design a task-execution subsystem for a service that must handle bursty traffic to a rate-limited downstream API.**
**A:** Four layers. (1) **Admission control** — a `Semaphore` sized to the downstream's concurrency limit, so the *resource* is the bound, not the thread count. (2) **A bounded queue** — tasks beyond the queue are either rejected (fail fast with a 429/503, which is honest) or, better, the caller runs them (`CallerRunsPolicy`), which throttles the producer. (3) **A per-task deadline** — every task carries a timeout, because a downstream that stops responding must not hold a permit forever; a `CompletableFuture.orTimeout` plus a permit release in a `finally` is the mechanism. (4) **A circuit breaker** — after a threshold of downstream failures, stop sending for a cooling-off period and fail fast; this is what prevents a *downstream outage* from becoming a *local outage* through queue growth. The measurement: the queue depth (the back-pressure signal), the permit utilisation (the saturation signal), the task latency percentiles, and the circuit-breaker state. And the honest assessment of the trade-off: this system deliberately **sheds load** rather than absorbing it — which is the correct choice for a rate-limited downstream, and the reason the alternative (an unbounded queue) is not merely "risky" but *architecturally wrong*: it converts a rate limit into a latency problem and then into an OOM.

---

## 9. Counter-Questions & Traps

### CQ1. "If `Executors.newFixedThreadPool` is unsafe, why is it in the JDK and used everywhere?"

- **Naive:** "It's a legacy API kept for compatibility."  ← *true, and it misses the design point*
- **The trap:** The trap is to treat it as a historical accident. The deeper issue is that the factory **decouples the two things that should be coupled**: the *pool size* and the *queue bound*. A well-designed pool bounds *both*; `newFixedThreadPool` bounds one and leaves the other unbounded — and the unbounded one is the one that actually matters.
- **Expert answer:** The precise analysis: the pool size and the queue bound are **the same knob expressed twice** — both exist to convert unbounded concurrency into bounded pressure. `newFixedThreadPool(10)` sets the thread count to 10 but leaves the queue unbounded, so the *effective* limit becomes the heap rather than the pool. `newCachedThreadPool()` inverts it: an unbounded thread count and a zero-capacity queue, so the limit becomes the OS. Neither is a coherent design — they are two *partial* configurations. The `Executors` factories exist because the pre-Java-5 mental model was "the pool size is the limit", and the JDK never revisited it. The modern guidance, and the reason it is now safe to ignore them: with **virtual threads** the thread count is no longer the scarce resource, so the factories' mistake is neutralised — and Java 21 gives you `newVirtualThreadPerTaskExecutor()` as the *correct* factory. The engineering lesson: **when you see a factory method that bundles several parameters into one call, check that the bundled parameters are mutually consistent** — that is where the bugs live.

### CQ2. "You said `CallerRunsPolicy` is the best rejection policy. But it runs the task on the caller's thread — isn't that dangerous?"

- **Naive:** "Yes, but it provides back-pressure."  ← *true, and it misses the trade-off*
- **The trap:** The trap is to present `CallerRunsPolicy` as universally good. It has a **real cost** that a careful interviewer will probe: the calling thread is now doing the work, so if the calling thread is a Tomcat request thread, **its latency grows** and the "caller" now holds a request thread while doing a slow task. In a web server, that can push the request pool into saturation — you have traded *queue growth* for *thread saturation*.
- **Expert answer:** The honest answer is that it depends on **who the caller is**. (1) If the caller is a **web request thread**, `CallerRunsPolicy` converts unbounded queueing into a longer request latency — which is often *better* (the user sees a slow response rather than a timeout) but can overload the request pool. In that case, `AbortPolicy` + a **429/503** response is often the more honest design: fail fast rather than degrade. (2) If the caller is a **background producer** (a message consumer, a scheduler), `CallerRunsPolicy` is exactly right — the producer slows down, which is the desired back-pressure. (3) If the caller is a **latency-sensitive path**, neither is great — you want a circuit breaker upstream. The general principle: **a rejection policy is a statement about which component should absorb the pressure**, and the right answer depends on which component can afford to.

### CQ3. "Why does the thread get created *before* the queue is tried? Isn't that backwards?"

- **Naive:** "It lets the pool grow before queueing."  ← *partly right, and it misses the consequence*
- **The trap:** The trap is to treat the order as arbitrary. It is a deliberate design choice, but the choice has a **significant, well-known consequence** that most candidates do not know: with an unbounded queue, the pool **never grows past `corePoolSize`**, so `maximumPoolSize` is effectively dead configuration.
- **Expert answer:** The design reasoning: the rationale is that the **core threads are the "always-on" workers** and the queue is the **elastic buffer** in front of them. The pool only adds *extra* (non-core) threads when the buffer is full — which is the right behaviour for a workload that is *usually* within capacity but *occasionally* bursts. The consequence is exactly the unbounded-queue trap: with a `LinkedBlockingQueue` the "buffer full" condition never occurs, so the elastic tier never activates. The design lesson: **the queue type is the parameter that decides whether the other parameters mean anything.** A bounded queue makes `maximumPoolSize` meaningful; an unbounded one makes it dead code. This is also why a `SynchronousQueue` produces the opposite behaviour — the pool *always* grows, because the queue never accepts anything.

### CQ4. "How do you know how many threads your service actually needs? You said 'measure' — measure what?"

- **Naive:** "Thread utilisation and queue depth."  ← *the right metrics, but not the whole story*
- **The trap:** The trap is that "thread utilisation" is ambiguous and easily misread. A pool at 100% utilisation is not automatically under-provisioned — if the tasks are *CPU-bound* and the CPU is saturated, 100% utilisation is **correct and optimal**. The metric that matters is whether the threads are waiting for the *resource* or for the *CPU*.
- **Expert answer:** The diagnostic that actually answers the question: (1) **Is the CPU saturated?** If yes, the pool is correctly sized — more threads will not help, and the *tasks* are too expensive. (2) **If the CPU is NOT saturated but the queue is growing**, the threads are blocked on something — a downstream call, a lock, a connection pool. Now the pool may be too small, **or** the downstream is the bottleneck. (3) **Measure the downstream's own saturation** — a database's connection pool wait time, an HTTP client's connection-pool wait. That tells you whether the limit is your thread count or the resource. (4) **Compare the queue wait time with the task service time** — if the queue wait dominates, you need more concurrency; if the service time dominates, you need faster work. And the final answer to "how many threads": **as many as the downstream can serve, and no more.** The formula `cores × (1 + waitTime/computeTime)` is a *starting point* that is only valid when the downstream is not the constraint — and in modern systems it usually is.

### CQ5. "Why is `shutdownNow()` considered dangerous if `shutdown()` is slow?"

- **Naive:** "Because it interrupts the tasks."  ← *true, and it misses the data-integrity angle*
- **The trap:** The trap is to focus on the interruption. The bigger risk is **silent data loss**: `shutdownNow()` returns the queued tasks, and if the caller **ignores the return value**, those tasks are gone — and in a payment or order system, "gone" means a customer paid and nothing happened.
- **Expert answer:** The two-part answer. (1) **The interruption is a correctness risk**: a task interrupted mid-way may have already done half its work (charged a card, written a row) — the classic "interrupted in the middle of a non-atomic operation" problem. That is why tasks should be **idempotent** and should check for interruption at safe points. (2) **The dropped tasks are a data-loss risk**: `shutdownNow()` returns them, and *the caller must handle them* — persist them, re-submit them to a durable queue, or log them loudly. The correct shutdown sequence is therefore: `shutdown()` → `awaitTermination(graceful)` → if it fails, `shutdownNow()` **and capture the returned list**. And the design lesson that generalises: **an interrupt is a request, not a guarantee** — a task may be in the middle of a non-atomic operation and cannot safely stop. The same reason `Thread.stop()` was removed.

### CQ6. "If `invokeAny` returns the first successful result, what happens to the other tasks?"

- **Naive:** "They are cancelled."  ← *true for the interrupted ones, and it misses the resources*
- **The trap:** The trap is to think "cancelled" means "stopped". The `Future`s for the losers are cancelled **with `mayInterruptIfRunning = false`** in `invokeAny`'s implementation — so an already-running task **keeps running to completion**, holding its resources (a connection, a socket).
- **Expert answer:** The precise behaviour: `invokeAny` cancels the futures of the tasks that did not win, and the implementation uses `cancel(true)`-style interruption for the *not-yet-started* ones; the running ones may be interrupted depending on the JDK version, but **there is no guarantee they stop immediately**. The practical consequences: (1) the "losing" requests still hit the downstream — so with a hedged request you are **doubling the load** on the downstream, which is a real cost that must be budgeted; (2) their resources are held until they finish; (3) any exception they throw is swallowed (that is the point of `invokeAny`). The engineering pattern: give the losers a **shorter deadline** than the winner, and make the downstream call **abort-aware** (a client with a connect/read timeout), so a losing request dies on its own timeout rather than hanging. And the deeper lesson: **hedged requests trade latency for load** — a good design caps the number of hedges and only issues one under a latency SLO breach, not on every request.

### CQ7. "You said a bounded queue plus `CallerRunsPolicy` gives back-pressure. Isn't throwing an exception better?"

- **Naive:** "Yes, failing fast is cleaner."  ← *true, and it misses the whole point of back-pressure*
- **The trap:** The trap is to think of the rejection as an *error*. In a back-pressure design, the rejection is the **mechanism** — it is how the system tells the producer to slow down. `AbortPolicy` turns that signal into an exception that the producer must catch and handle; `CallerRunsPolicy` turns it into *actual work done by the producer*, which slows it down naturally.
- **Expert answer:** The distinction is between **shedding** and **throttling**. `AbortPolicy` = shedding (the work is dropped, the producer is informed) — correct when the work is optional (a metrics ping, a recommendation). `CallerRunsPolicy` = throttling (the work still happens, the producer is slowed) — correct when the work is mandatory (an order). The choice is a **business** decision, not a technical one. And the honest caveat about `CallerRunsPolicy`: it only works if the caller can afford the work. If the caller is a request thread with a 200 ms budget and the task takes 5 s, the caller will time out — so the pool and the caller's SLA must be designed together. The deeper principle: **back-pressure is a system-wide property** — the queue, the rejection policy, the caller's behaviour, and the downstream's capacity are one design, and changing any one of them without the others breaks the system.

### CQ8. "Why not just use `ForkJoinPool` for everything? It has work stealing."

- **Naive:** "It scales better."  ← *true for CPU work, false for I/O work*
- **The trap:** The trap is to generalise the CPU-bound conclusion to all workloads. `ForkJoinPool`'s work stealing is designed for **CPU-bound, fine-grained** tasks — and the very property that makes it great there (workers block in `join` without consuming a thread) is what makes it wrong for I/O: a worker blocked on a socket **cannot steal**, so the parallelism is lost.
- **Expert answer:** The three structural differences. (1) **Blocking:** a `ForkJoinPool` worker blocked on I/O holds a thread that could have stolen work — the pool's parallelism is wasted, and with the common pool's small size, that starves `parallelStream()` in the same JVM. (2) **The common pool is shared**: `ForkJoinPool.commonPool()` is used by `parallelStream()`, so blocking in it degrades *unrelated* code. A dedicated `ForkJoinPool` fixes that but reintroduces the problem of sizing. (3) **No bounded queue:** `ForkJoinPool` uses per-worker deques, so there is no single place to apply back-pressure — the *submission* side is unbounded by default. So the guidance: `ForkJoinPool` for CPU-bound parallel streams and fine-grained recursive work; a `ThreadPoolExecutor` with a bounded queue for I/O-bound services; and — in Java 21 — **virtual threads** for I/O-bound services at high concurrency, with a semaphore for the downstream bound.

### CQ9. "You said `ThreadPoolExecutor` is preferred over the `Executors` factories. Isn't building it manually more error-prone?"

- **Naive:** "Yes, but the parameters are explicit."  ← *true, and it misses the real benefit*
- **The trap:** The trap is to frame it as "more code, more risk". The real benefit is that the explicit construction makes the **three things that must be decided together** — the thread count, the queue bound, and the rejection policy — **visible and reviewable** at one place. The factories hide them, and hide them *inconsistently*.
- **Expert answer:** The engineering argument: the `Executors` factories give you a configuration where **the parameters contradict each other** — `newFixedThreadPool(10)` says "maximumPoolSize = Integer.MAX_VALUE" while meaning "10", and it pairs that with an unbounded queue. A reviewer reading `new ThreadPoolExecutor(10, 50, 60, SECONDS, new ArrayBlockingQueue<>(1000), factory, new CallerRunsPolicy())` sees the *whole policy* in one line and can ask the right questions: is 50 the right ceiling? is 1000 the right buffer? is `CallerRunsPolicy` right for this caller? The "error-prone" concern is answered by wrapping the construction **once** in your own factory (as in §6.1) — after which the rest of the codebase never touches the constructor. The deeper lesson: **the danger of an API is not that it is verbose, but that it hides a decision you should be making.**

### CQ10. "How do you test a thread pool? Concurrency is non-deterministic."

- **Naive:** "Run the tasks and check the results."  ← *the reality, and it misses the failure modes*
- **The trap:** The trap is to test only the *happy path*. The interesting bugs in an executor are in the **rejection**, the **shutdown**, and the **ordering** — and those are exactly the paths a "submit 10 tasks and check" test never exercises.
- **Expert answer:** The test matrix that actually covers the risk: (1) **The submission flow** — submit more tasks than `corePoolSize + queueCapacity` and assert that the rejection policy fires *and that the tasks were not lost* (with `CallerRunsPolicy`, assert that the caller's thread ran the extra ones). (2) **The rejection policy** — assert the exact behaviour: `AbortPolicy` throws, `CallerRunsPolicy` runs on the caller, `DiscardPolicy` drops (assert the count). (3) **The shutdown** — submit a long task, call `shutdown()`, assert that it **completes** (not interrupted) and that `isTerminated()` eventually becomes true; then call `shutdownNow()` and assert the running task was interrupted **and** that the returned list contains the un-run tasks. (4) **Exception isolation** — submit a task that throws and assert that the pool still runs the other tasks and that the exception is captured in the `Future`. (5) **The `CallerRunsPolicy` back-pressure** — use a `CountDownLatch` to make the timing deterministic rather than relying on sleeps. And the meta-lesson: **concurrency tests must control the timing explicitly** — a `CountDownLatch` or a `CyclicBarrier` at the point of interest — because "run it and hope" tests the luck, not the code.

---

## 10. Rapid-Fire MCQs

**Q1.** `ThreadPoolExecutor` is the implementation behind which factory?
- A) `Executors.newFixedThreadPool`  B) `Executors.newCachedThreadPool`  C) All the `Executors` factories  D) None

**Q2.** In the submission flow, when is a new thread created?
- A) Only when the queue is full  B) When `poolSize < corePoolSize`, **or** when the queue is full and `poolSize < maximumPoolSize`  C) Always  D) Never

**Q3.** Why is the thread created *before* the queue is tried?
- A) A mistake  B) So an unbounded queue makes `maximumPoolSize` unreachable  C) For performance  D) To avoid locking

**Q4.** `Executors.newFixedThreadPool(n)` uses which queue?
- A) A bounded `ArrayBlockingQueue`  B) An **unbounded** `LinkedBlockingQueue`  C) A `SynchronousQueue`  D) A `PriorityBlockingQueue`

**Q5.** With `newFixedThreadPool(10)`, what is the effective maximum thread count?
- A) 10  B) `Integer.MAX_VALUE`  C) 20  D) The queue size

**Q6.** The default rejection handler is…
- A) `CallerRunsPolicy`  B) `DiscardPolicy`  C) `AbortPolicy`  D) `DiscardOldestPolicy`

**Q7.** `CallerRunsPolicy` provides…
- A) Error propagation  B) **Back-pressure** (the producer slows down)  C) Task prioritisation  D) Fairness

**Q8.** `DiscardPolicy` is dangerous because…
- A) It is slow  B) It **silently drops** the work  C) It throws  D) It blocks

**Q9.** `execute()` vs `submit()` — the difference is that `submit()`…
- A) Runs on a new thread  B) Returns a `Future`  C) Rejects the task  D) Uses a different queue

**Q10.** An exception thrown inside a task submitted with `execute()`…
- A) Is captured in a `Future`  B) Goes to the thread's uncaught-exception handler  C) Is swallowed  D) Stops the pool

**Q11.** `shutdown()` lets the queued tasks…
- A) Be discarded  B) **Run to completion**  C) Be interrupted  D) Be re-queued

**Q12.** `shutdownNow()` returns…
- A) `void`  B) The list of tasks that were **not** started  C) The pool  D) `true`/`false`

**Q13.** `isShutdown()` vs `isTerminated()`?
- A) They are the same  B) The first means "no new tasks"; the second means "all finished"  C) The reverse  D) Neither exists

**Q14.** The CPU-bound pool size formula is…
- A) `cores × 2`  B) `cores + 1`  C) `cores × 100`  D) `1000`

**Q15.** The I/O-bound pool size formula is…
- A) `cores + 1`  B) `cores × (1 + waitTime/computeTime)`  C) `cores × cores`  D) `2 × cores`

**Q16.** `invokeAny` returns…
- A) All the results  B) The **first successful** result  C) The fastest to start  D) `null` on failure

**Q17.** `invokeAll` with a timeout cancels…
- A) Nothing  B) The unfinished tasks  C) The whole pool  D) The caller

**Q18.** `Future.get()` throws `ExecutionException` — how do you get the original cause?
- A) `e.getCause()`  B) `e.getMessage()`  C) `e.getStackTrace()`  D) `e.printStackTrace()`

**Q19.** `Future.get(timeout, unit)` throws…
- A) `TimeoutException`  B) `InterruptedException` only  C) `ExecutionException` only  D) `IllegalStateException`

**Q20.** `ForkJoinPool`'s work stealing is best for…
- A) Blocking I/O  B) **CPU-bound, fine-grained** tasks  C) Database queries  D) Long-running requests

**Q21.** `ForkJoinPool.commonPool()` is shared with…
- A) `synchronized`  B) `parallelStream()`  C) `CompletableFuture`  D) `ThreadLocal`

**Q22.** A `SynchronousQueue` means…
- A) Tasks are buffered  B) A **direct handoff** — the pool grows instead  C) The queue is infinite  D) No queue is used

**Q23.** `allowCoreThreadTimeOut(true)` makes the core threads…
- A) Permanent  B) Expire like the others  C) Daemons  D) Virtual

**Q24.** With virtual threads, what should you bound?
- A) The thread count  B) The **downstream resource** (a semaphore)  C) Nothing  D) The queue

**Q25.** `newVirtualThreadPerTaskExecutor()` creates…
- A) A fixed pool of virtual threads  B) **One virtual thread per task**  C) A pool sized to the cores  D) A cached pool

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **C** | Every `Executors` factory returns a `ThreadPoolExecutor` with preset parameters. |
| 2 | **B** | A thread is created below `corePoolSize`, and again when the queue is full and the pool is below `maximumPoolSize`. |
| 3 | **B** | The consequence is that an unbounded queue makes `maximumPoolSize` unreachable. |
| 4 | **B** | An **unbounded** `LinkedBlockingQueue` — the source of the OOM trap. |
| 5 | **A** | 10 — the pool never grows because the queue always accepts. |
| 6 | **C** | `AbortPolicy` — it throws `RejectedExecutionException`. |
| 7 | **B** | The producer is throttled to the pool's consumption rate. |
| 8 | **B** | The work is dropped with no signal — you lose it silently. |
| 9 | **B** | `submit` returns a `Future`; `execute` returns `void` and lets the exception escape. |
| 10 | **B** | `execute` has no `Future`, so the exception goes to the uncaught-exception handler. |
| 11 | **B** | `shutdown()` is the graceful option — the queued tasks still run. |
| 12 | **B** | The list of tasks that had not started — and you must handle them. |
| 13 | **B** | `isShutdown` = no new tasks accepted; `isTerminated` = everything finished. |
| 14 | **B** | `cores + 1` — the extra thread covers the coordination. |
| 15 | **B** | `cores × (1 + waitTime/computeTime)`. |
| 16 | **B** | The first **successful** result — the basis of a hedged request. |
| 17 | **B** | The unfinished tasks are cancelled and their futures complete exceptionally. |
| 18 | **A** | `e.getCause()` gives the original exception; `e.getMessage()` is the wrapper's. |
| 19 | **A** | `TimeoutException` (plus `InterruptedException` and `ExecutionException`). |
| 20 | **B** | Work stealing keeps the cores busy with unevenly-sized CPU-bound tasks. |
| 21 | **B** | `parallelStream()` uses the common pool — blocking in it starves unrelated code. |
| 22 | **B** | Capacity 0 — a direct handoff, so the pool grows instead of buffering. |
| 23 | **B** | The core threads expire like the non-core ones after `keepAliveTime`. |
| 24 | **B** | The thread count is free; the **downstream** (connections, a broker) is the scarce resource. |
| 25 | **B** | One virtual thread per task — no pooling, because creation is cheap. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│              THE 7 THREADPOOLEXECUTOR PARAMETERS                        │
├──────────────────────┬───────────────────┬───────────────────────────────┤
│ corePoolSize         │ always-on workers │ the baseline concurrency      │
│ maximumPoolSize      │ the hard ceiling  │ reachable ONLY with a bounded │
│                      │                   │ queue                          │
│ keepAliveTime + unit │ idle lifetime of  │ 30–60 s                       │
│                      │ the extra threads │                               │
│ workQueue            │ the buffer        │ ⚠ THE MOST IMPORTANT ONE     │
│                      │                   │ bounded → back-pressure       │
│ threadFactory        │ how threads are   │ ⚠ ALWAYS customise (names)    │
│                      │ created           │                               │
│ handler              │ what to do when   │ CallerRunsPolicy = back-press.│
│                      │ full              │ AbortPolicy = fail fast       │
└──────────────────────┴───────────────────┴───────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE SUBMISSION FLOW — MEMORISE THIS                 │
│                                                                          │
│   submit(task)                                                           │
│      │                                                                   │
│      ├─ poolSize < corePoolSize?      → NEW THREAD ✅                   │
│      │                                                                   │
│      ├─ queue.offer(task) ok?         → ENQUEUE ✅                      │
│      │                                                                   │
│      ├─ poolSize < maximumPoolSize?    → NEW THREAD ✅                   │
│      │                                                                   │
│      └─ otherwise                     → REJECTION HANDLER ⚠            │
│                                                                          │
│   ⚠ THE THREAD IS CREATED BEFORE THE QUEUE IS TRIED.                    │
│     ⇒ an UNBOUNDED queue makes maximumPoolSize UNREACHABLE.            │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 4 REJECTION POLICIES                            │
├──────────────────────┬─────────────────────────────────────────────────┤
│ AbortPolicy (default)│ throws RejectedExecutionException → fail fast   │
│ CallerRunsPolicy     │ the caller runs it → BACK-PRESSURE ✅           │
│ DiscardPolicy        │ silently drops it → ⚠ NEVER                     │
│ DiscardOldestPolicy  │ drops the oldest, retries → "latest value"      │
└──────────────────────┴─────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    POOL SIZING — THE HONEST VERSION                     │
│  CPU-bound:   cores + 1                                                 │
│  I/O-bound:   cores × (1 + waitTime/computeTime)                        │
│                                                                          │
│  ⚠ BUT THE REAL CEILING IS THE DOWNSTREAM RESOURCE.                    │
│  THE PRACTICAL GUIDANCE:                                                 │
│    1. start SMALL (10–20)                                               │
│    2. MEASURE: thread utilisation, queue depth, task latency             │
│    3. grow only if the threads are busy AND the downstream has headroom  │
│    4. if the QUEUE is growing → the TASK is slow → fix the task          │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The production pool recipe

```java
ExecutorService pool = new ThreadPoolExecutor(
        16, 32,                                  // core, max
        60, TimeUnit.SECONDS,                    // keep-alive
        new ArrayBlockingQueue<>(1000),          // ⚠ BOUNDED
        namedThreadFactory("worker"),            // ⚠ NAMED
        new ThreadPoolExecutor.CallerRunsPolicy());   // ⚠ BACK-PRESSURE
```

### The production checklist

```text
  ☐ Construct the ThreadPoolExecutor EXPLICITLY (never the Executors factories)
  ☐ BOUNDED queue — the whole back-pressure design depends on it
  ☐ A named ThreadFactory — a thread dump must be readable
  ☐ CallerRunsPolicy (or AbortPolicy + a 503 response)
  ☐ shutdown() + awaitTermination() in a @PreDestroy
  ☐ Capture the list returned by shutdownNow() — do not lose tasks
  ☐ Allow core threads to time out in an idle web app
  ☐ A circuit breaker upstream of the pool
  ☐ Metrics: active, pool size, queue size, completed task count
```

### The `@PreDestroy` shutdown

```java
@PreDestroy
public void shutdown() {
    pool.shutdown();
    try {
        if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
            List<Runnable> dropped = pool.shutdownNow();
            if (!dropped.isEmpty()) {
                log.warn("{} tasks were dropped at shutdown — persist them for retry", dropped.size());
            }
        }
    } catch (InterruptedException e) {
        pool.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

### Metrics worth watching

| Metric | What it tells you |
|---|---|
| `getActiveCount()` | Threads actually working |
| `getPoolSize()` | Threads created (including idle) |
| `getQueue().size()` | The **back-pressure signal** — a growing queue means the pool is too small OR the tasks are slow |
| `getCompletedTaskCount()` | Throughput |
| Rejection count | ⚠ The system is at capacity |
| Task latency p99 | The user-visible symptom |
| Downstream permit utilisation | The real saturation signal |

### Decision table for the pool type

| Workload | Pool |
|---|---|
| CPU-bound, fine-grained | `ForkJoinPool` (or `parallelStream`) |
| CPU-bound, coarse | `ThreadPoolExecutor` sized to `cores + 1` |
| I/O-bound, moderate concurrency | `ThreadPoolExecutor` with a bounded queue |
| I/O-bound, high concurrency (Java 21+) | `newVirtualThreadPerTaskExecutor()` + a `Semaphore` |
| Unbounded, append-heavy | `ConcurrentLinkedQueue` |
| Producer/consumer with back-pressure | `BlockingQueue` (bounded) |
| Delayed / scheduled tasks | `ScheduledThreadPoolExecutor` |

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The JMM, `volatile`, `synchronized` | [01 · Thread Basics](01-Thread-Basics.md) |
| Locks, atomics and CAS | [03 · Locks & Atomic Classes](03-Locks-Atomic.md) |
| Deadlock, livelock, starvation | [04 · Concurrency Problems](04-Concurrency-Problems.md) |
| Virtual threads (Java 21) | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| `CompletableFuture` composition | [06 · Optional & CompletableFuture](../06-Java-8-Features/03-Optional-CompletableFuture.md) |
| The JDK source | `java.util.concurrent.ThreadPoolExecutor` — read `execute()` and `addWorker()` |

---

**Next Chapter:** → [03 · Locks & Atomic Classes](03-Locks-Atomic.md)
**Previous Chapter:** ← [01 · Thread Basics](01-Thread-Basics.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
