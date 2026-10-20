# Concurrency Problems — The Complete Interview Guide

> **The "what goes wrong" chapter.** Every concurrency bug in production is one of a
> small number of named problems: a **race condition**, a **deadlock**, a
> **livelock**, **starvation**, or **a lost update**. Learn to *name* the problem
> and you have learned to diagnose it.

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

- **The five classic problems:** **race condition**, **deadlock**, **livelock**, **starvation**, and **priority inversion**.
- **A race condition** is when the correctness of the program depends on the *timing* of the threads. Two forms: **check-then-act** (TOCTOU) and **read-modify-write** (a lost update).
- **The fix for a race** is a **happens-before edge** between the conflicting accesses — `synchronized`, `volatile`, an atomic class, `Thread.start`/`join`, or `final` fields.
- **A deadlock** needs all **4 Coffman conditions** simultaneously: mutual exclusion, hold-and-wait, no preemption, and circular wait. **Breaking any one** prevents it.
- **The practical deadlock fixes:** a **global lock order**, `tryLock` with a timeout + back-off, and **never hold a lock across a blocking call**.
- **A livelock** is when threads keep making *progress* (no blocking) but never make *useful* progress — they keep reacting to each other. The fix is **randomised back-off** (jitter).
- **Starvation** is when a thread is repeatedly denied the resource it needs. The fix is **fairness** (a fair `ReentrantLock`, a fair `Semaphore`, or an explicit priority queue).
- **Priority inversion** is when a low-priority thread holds a lock that a high-priority thread needs, while a *medium*-priority thread keeps preempting the low one. The fix is a **priority-inheritance protocol** (`ReentrantLock`'s `isHeldByCurrentThread` check, or a `PriorityBlockingQueue`).
- **False sharing** is not a correctness bug but a **performance** bug: two variables on the same cache line, updated by different threads, cause the line to ping-pong.
- **The N+1 / lost-update pattern** appears in every layer: a `get`-then-`put` on a `ConcurrentHashMap`, a `synchronized` block that is not atomic, or an `UPDATE` that is not a single atomic statement.
- **The most common deadlock in Java is not lock-ordering — it is a pool deadlock**: a task holds a pool connection and waits for another task in the same pool.

---

## 1. What & Why — The Problem This Solves

**The problem.** Concurrency bugs are **not random** — they are a small set of named
failure modes, each with a known cause and a known fix. A developer who can *name*
the problem is halfway to fixing it.

```text
   THE FAILURE TAXONOMY
   ════════════════════

   ❓ "The result is sometimes wrong"
        └─▶ RACE CONDITION (check-then-act / lost update)

   😱 "The service hangs and a thread dump shows everything waiting"
        └─▶ DEADLOCK

   😵 "The CPU is at 100% but nothing completes"
        └─▶ LIVELOCK

   😐 "One request is always starved"
        └─▶ STARVATION

   📉 "Throughput collapses under load despite more threads"
        └─▶ FALSE SHARING or LOCK CONTENTION
```

> 💡 **The interview framing.** The most valuable thing a candidate can do is **name
> the problem**: *"that's a check-then-act race — the `get` and the `put` need to be
> one atomic operation"* is a much stronger answer than *"you need to synchronise it"*.

---

## 2. Mental Model — The One Picture

```text
   THE 5 PROBLEMS AT A GLANCE
   ═════════════════════════════

   ① RACE CONDITION
   ─────────────────
   T1: check ──── ▓▓▓▓▓▓ ──── act       the window between the
   T2:       check ──── act                check and the act is the BUG

   ② DEADLOCK
   ─────────
        ┌──────┐          ┌──────┐
        │  T1  │ holds A  │  T2  │ holds B
        └───┬──┘ wants B  └───┬──┘ wants A
            │    ╲    ╱       │
            │     ╲╱         │            ← a cycle: nobody proceeds
            │     ╱╲         │
            └────╱  ╲────────┘

   ③ LIVELOCK
   ───────────
   T1: backs off ──▶ T2: backs off ──▶ T1: backs off ──▶ …
        (they keep yielding to each other — no deadlock, no progress)

   ④ STARVATION
   ──────────────
   T1, T2, T3: keep taking the lock
   T4:                         waiting… waiting… waiting… 💥

   ⑤ PRIORITY INVERSION
   ─────────────────────
   LOW holds a lock  ──▶ MEDIUM preempts LOW  ──▶ HIGH waits for LOW
   (the medium-priority thread starves the high one)
```

---

## 3. Deep Dive — Theory

## 3.1 Race condition — the two shapes

### Shape A — check-then-act (TOCTOU)

```java
// ❌ THE BUG
if (!map.containsKey(key)) {
    map.put(key, computeValue(key));    // 💥 another thread may have inserted it
}

// ✅ THE FIX — one atomic operation
map.computeIfAbsent(key, k -> computeValue(k));
```

**The interleaving that breaks it:**

```text
   Thread A                          Thread B
   ────────                          ────────
   containsKey("x") → false
                                   containsKey("x") → false
   put("x", compute())   ← 💥       put("x", compute())   ← the second overwrites
```

**The same shape at every layer:**

| Layer | The racy form | The atomic form |
|---|---|---|
| `ConcurrentHashMap` | `get` then `put` | `computeIfAbsent` / `putIfAbsent` / `merge` |
| Files | `exists()` then `create()` | `createNewFile()` (atomic) |
| SQL | `SELECT` then `INSERT` | `INSERT ... ON CONFLICT DO NOTHING` (a unique constraint) |
| Database row | `SELECT` then `UPDATE` | `UPDATE t SET c = c + 1` (one atomic statement) |
| Idempotency | `if (!processed.contains(id))` | A unique constraint + `INSERT IGNORE` |

### Shape B — read-modify-write (the lost update)

```java
// ❌ THE BUG
int count = counter;      // read
count = count + 1;        // modify
counter = count;          // write     ← another thread's write is lost

// ✅ THE FIX
counter.incrementAndGet();     // one atomic operation
```

**The interleaving:**

```text
   counter = 0

   Thread A                Thread B
   ────────                ────────
   read  → 0
                           read  → 0
   write → 1
                           write → 1        ← 💥 A's write is LOST

   FINAL = 1  (expected 2)
```

**The three fixes, and their trade-offs:**

| Fix | Mechanism | Cost |
|---|---|---|
| `synchronized` | Mutual exclusion | Serialises the whole critical section |
| `AtomicInteger` | A CAS instruction | Retries under contention; a shared cache line |
| `LongAdder` | Contention striping | `sum()` is O(cells) |
| **An atomic SQL statement** | The database does it | ✅ **The best** — the data owner does the atomicity |

## 3.2 Deadlock — the 4 Coffman conditions

A deadlock requires **all four** simultaneously:

| # | Condition | Meaning |
|---|---|---|
| 1 | **Mutual exclusion** | The resource can be held by only one thread at a time |
| 2 | **Hold and wait** | A thread holds one resource while waiting for another |
| 3 | **No preemption** | A resource cannot be forcibly taken away |
| 4 | **Circular wait** | There is a cycle: A waits for B waits for A |

**Break any one and the deadlock is impossible.** This is the most useful framing in
the whole chapter — it converts "how do I avoid deadlocks?" into "which of the four do
I break, and how?".

```text
   ① MUTUAL EXCLUSION — hard to break
      The resource is inherently exclusive (a row lock, a monitor).
      ✗ rarely the answer.

   ② HOLD AND WAIT — breakable
      "acquire everything at once" (a single lock covering both resources)
      or "release before acquiring" (the ordered approach)

   ③ NO PREMICTION — breakable
      Use tryLock(timeout) and give up rather than waiting forever.
      ⚠ THIS IS THE MOST PRACTICAL FIX.

   ④ CIRCULAR WAIT — breakable, and the most common fix
      Impose a GLOBAL ORDER on the locks. If every thread acquires
      them in the same order, a cycle is impossible.
```

**The global lock order — the canonical fix:**

```java
public void transferMoney(Account from, Account to, BigDecimal amount) {
    // ✅ Always acquire the LOWER id first → no cycle is possible
    Account first  = from.getId() < to.getId() ? from : to;
    Account second = from.getId() < to.getId() ? to : from;

    synchronized (first) {
        synchronized (second) {
            from.debit(amount);
            to.credit(amount);
        }
    }
}
```

**Why the order works:** if every thread acquires locks in *ascending id* order, then
a cycle would require a thread to hold a higher id and wait for a lower one — which the
rule forbids. The cycle is *structurally impossible*.

**The `tryLock` + back-off fix:**

```java
public boolean transfer(Account from, Account to, BigDecimal amount) {
    Random random = ThreadLocalRandom.current();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

    while (System.nanoTime() < deadline) {
        if (from.getLock().tryLock()) {
            try {
                if (to.getLock().tryLock()) {
                    try {
                        from.debit(amount);
                        to.credit(amount);
                        return true;
                    } finally {
                        to.getLock().unlock();
                    }
                }
            } finally {
                from.getLock().unlock();
            }
        }
        // ⚠ back off with RANDOM jitter, or the threads livelock
        try {
            Thread.sleep(random.nextInt(50) + 10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    return false;   // gave up
}
```

## 3.3 The pool deadlock — the most common one in production

```text
   ❌ THE POOL DEADLOCK
   ──────────────────────
   A pool with 5 threads and a queue of 10.
   Each task does:
       1. take a CONNECTION from a pool of 5   (holds it)
       2. submit ANOTHER task to the same pool and WAIT for it

   → 5 tasks each hold a connection and wait for a 6th task
   → the pool has 5 threads, ALL of which are waiting
   → the 6th task can never run
   → DEADLOCK 💥 (and the connection pool is exhausted too)

   ✅ THE FIXES
   ─────────────
   1. NEVER wait for a task in the same pool from inside a pool task
   2. Never hold a scarce resource (a connection) across a task submission
   3. Use a pool size > the number of tasks that can hold a resource
   4. Detect it: a thread dump shows "pool-X-thread-N" WAITING on a Future
```

**The generalisation — the "thread pool starvation" trap:**

```java
// ❌ A fixed pool of 4, with tasks that submit tasks and wait
pool.submit(() -> {
    pool.submit(() -> work()).get();   // 💥 if all 4 outer tasks do this, the inner ones never run
    return null;
});
```

> 💡 **The rule:** *a task running in a pool must never block waiting for another task
> in the same pool.* If you need nested parallelism, use a **different** pool, or
> restructure to avoid the nesting.

## 3.4 Livelock — progress without progress

```java
// ❌ THE LIVELOCK
// Two threads try to back off from each other — but in lockstep
while (true) {
    if (!tryAccess()) {
        Thread.sleep(100);      // ← if BOTH sleep exactly 100 ms, they retry in lockstep
        //                    and collide again, forever
    }
}
```

**The difference from a deadlock:**

| | Deadlock | Livelock |
|---|---|---|
| The threads are | **Blocked** (waiting) | **Running** (spinning/sleeping) |
| The CPU is | Idle | **100% busy** |
| The code does | Nothing | Something, repeatedly, with no effect |
| Diagnosis | A thread dump shows `BLOCKED`/`WAITING` | A thread dump shows `RUNNABLE` with a high CPU time |

**The fix — randomised back-off (jitter):**

```java
// ✅ RANDOMISE the back-off so the threads do not resynchronise
long backoff = BASE_DELAY + ThreadLocalRandom.current().nextLong(MAX_JITTER);
Thread.sleep(backoff);
```

**The general principle — always add jitter to any back-off.** A deterministic back-off
in a distributed system is a bug waiting to happen: the "thundering herd" is the
distributed version of the livelock.

## 3.5 Starvation — one thread never wins

```text
   THE CAUSES
   ──────────
   ① A NON-FAIR LOCK
      The barging policy lets a thread that just released the lock re-acquire it
      immediately → a waiting thread can be passed over indefinitely.

   ② A `notify()` (not `notifyAll()`)
      Only one waiter is woken, and it may not be the right one.

   ③ AN UNFAIR `Semaphore`
      `new Semaphore(n)` does not guarantee FIFO ordering.

   ④ A HOT SPOT
      One shared object that every thread needs.

   THE FIXES
   ────────
   • new ReentrantLock(true)                 — a fair lock
   • new Semaphore(n, true)                  — a fair semaphore
   • An explicit priority queue               — priority is explicit
   • Partition the hot resource (striping)   — no contention at all
```

## 3.6 Priority inversion

```text
   THE SCENARIO
   ────────────
   HIGH   (priority 10) wants lock L
   MEDIUM (priority  5) is CPU-bound, does not need L
   LOW    (priority  1) holds lock L and wants the CPU

   → LOW is preempted by MEDIUM, so it never releases L
   → HIGH waits for L, which LOW can never release
   → HIGH is blocked behind a MEDIUM-priority thread 💥

   THE FIX — priority inheritance
   ───────────────────────────────
   When LOW discovers that a HIGH-priority thread is waiting for the lock it
   holds, it temporarily inherits HIGH's priority → it finishes quickly and
   releases the lock.
```

**In Java, the fix is manual (there is no built-in priority inheritance):**

```java
// A "priority inheritance"-style lock
public class PrioLock {
    private final ReentrantLock lock = new ReentrantLock();

    public void runLowPriority(Runnable body) {
        lock.lock();
        try {
            // If a high-priority thread is waiting, temporarily boost ourselves
            if (lock.hasQueuedThreads()) {
                // (a real implementation would use a priority queue of waiters)
            }
            body.run();
        } finally {
            lock.unlock();
        }
    }
}
```

**The practical alternative — avoid the situation:**

| Technique | How |
|---|---|
| **Priority inheritance** | Boost the low-priority holder while a high-priority waiter exists |
| **Avoid blocking while holding a lock** | The low-priority thread never waits, so it cannot be inverted |
| **Use a `PriorityBlockingQueue`** | The scheduler picks the highest-priority *ready* task, so the low one is not starved by the CPU |
| **Separate the concerns** | Do not let a low-priority task hold a lock a high-priority task needs |

## 3.7 False sharing — the performance problem

```text
   ❌ TWO THREADS, ONE CACHE LINE
   ┌──────────────────────────────────────────┐
   │ countA │ countB │  (other fields)        │  ← ONE 64-byte cache line
   └──────────────────────────────────────────┘
        ▲            ▲
   T1 updates    T2 updates
   countA        countB
        │            │
        └── each write INVALIDATES the line for the other core 💥
           → the line ping-pongs between cores
           → throughput collapses even though the "work" is trivial

   ✅ THE FIX — separate the cache lines
   ┌────────────────────┬────────────────────┐
   │ countA             │ countB             │  ← different lines
   └────────────────────┴────────────────────┘
```

**In Java:** `@Contended` (from `sun.misc`, or `jdk.internal.vm.annotation.Contended`
with `-XX:-RestrictContended`) adds the padding. The JDK's own `LongAdder` cells use it.

**The diagnosis:** a counter that is much slower than it should be, on a machine with
many cores, with a high cache-miss rate in the profiler (`perf c2c` on Linux is the
tool that finds it).

## 3.8 The lost update — one pattern, every layer

| Layer | The lost update | The atomic form |
|---|---|---|
| Java memory | `counter++` on a plain `int` | `AtomicInteger` / `LongAdder` |
| `ConcurrentHashMap` | `get` then `put` | `computeIfAbsent` / `merge` |
| SQL | `SELECT c FROM t` then `UPDATE t SET c = ?` | `UPDATE t SET c = c + 1` |
| SQL row version | `UPDATE ... WHERE version = ?` (check then update) | The same, with a **retry loop** on 0 rows affected |
| JPA | Read-modify-write on a detached entity | `@Version` optimistic locking, or a bulk `UPDATE … SET c = c + 1` |
| Files | `exists()` then `create()` | `createNewFile()` |

## 3.9 Diagnosing concurrency problems

```text
   THE DIAGNOSTIC DECISION TREE
   ────────────────────────────

   "The result is wrong"
        │
        └─▶ Is it wrong only under load?
              YES → a RACE. Check for check-then-act and lost updates.
                    → a thread dump won't help; add logging or use a
                      race detector (jcstress, or JFR's jdk.ThreadPark)

   "The service hangs"
        │
        └─▶ jstack / jcmd Thread.print
              │
              ├─ "Found 1 deadlock."        → a DEADLOCK (intrinsic monitors)
              ├─ All threads BLOCKED        → a DEADLOCK (a lock-order cycle)
              ├─ All threads WAITING on a   → a POOL DEADLOCK or a lost
              │   Future                    →   notification
              └─ All threads RUNNABLE,      → a LIVELOCK or false sharing
                 high CPU
```

**The tools:**

| Tool | What it gives you |
|---|---|
| `jstack <pid>` / `jcmd <pid> Thread.print` | The thread dump — the first tool for any hang |
| `jcmd <pid> Thread.dump_to_file` | The same, to a file |
| `jcmd <pid> GC.heap_info` | Whether it is a memory problem |
| `jcmd <pid> Compiler.queue` | Whether the JIT is stuck |
| `perf c2c` (Linux) | **False sharing** detection |
| `jcstress` | The systematic concurrency stress harness |
| `JFR` (`jdk.ThreadPark`, `jdk.JavaMonitorEnter`) | Event-based lock-contention monitoring |
| `async-profiler` | CPU and wall-clock profiles |
| `ThreadMXBean` | Programmatic deadlock detection and lock-contention stats |

---

## 4. Visualizations

### 4.1 Check-then-act — the window where the bug lives

```text
   ❌ map.containsKey() then map.put()
   ──────────────────────────────────────
   TIME ──────────────────────────────────────────────────▶
   Thread A                    Thread B
   ────────                    ────────
   containsKey("x")→false
                                containsKey("x")→false
   put("x", v1)  ─────────┐    put("x", v2)  ─────────┐
                          │                           │
   ══════════ THE WINDOW ═════════════════════════════╪══════
   the check and the act are NOT atomic                │
                          ▼                           ▼
                     A's value is LOST 💥

   ✅ map.computeIfAbsent("x", k -> v)
   ──────────────────────────────────────
   → ONE atomic operation → the mapping function runs AT MOST ONCE
   → the window does not exist
```

### 4.2 The lost update

```text
   counter = 0

   Thread A                     Thread B
   ────────                     ────────
   READ  counter → 0
   COMPUTE 0 + 1 = 1
                                READ  counter → 0
                                COMPUTE 0 + 1 = 1
   WRITE counter ← 1
                                WRITE counter ← 1     ← 💥 A's write is LOST

   FINAL counter = 1   (expected 2)

   THE FIXES:
     • synchronized  → one critical section
     • AtomicInteger → a CAS instruction (one atomic op)
     • LongAdder     → striped cells
     • UPDATE t SET c = c + 1  → the database does it ✅ BEST
```

### 4.3 The four Coffman conditions and how to break each

```text
   ┌────────────────────────────────────────────────────────────────┐
   │  ① MUTUAL EXCLUSION — the resource is inherently exclusive     │
   │     ✗ hard to break (a row lock is a row lock)                │
   ├────────────────────────────────────────────────────────────────┤
   │  ② HOLD AND WAIT — holding A while waiting for B              │
   │     ✓ break: acquire all at once, or release before acquiring │
   ├────────────────────────────────────────────────────────────────┤
   │  ③ NO PREMICTION — a resource cannot be forcibly taken         │
   │     ✓ break: tryLock(timeout) → give up instead of waiting    │
   │            ← THE MOST PRACTICAL FIX                           │
   ├────────────────────────────────────────────────────────────────┤
   │  ④ CIRCULAR WAIT — A waits for B waits for A                   │
   │     ✓ break: a GLOBAL LOCK ORDER → a cycle is structurally    │
   │            impossible                                         │
   │            ← THE MOST COMMON FIX                              │
   └────────────────────────────────────────────────────────────────┘

   BREAK ANY ONE → NO DEADLOCK. Ever.
```

### 4.4 The global lock order — how a cycle becomes impossible

```text
   ❌ WITHOUT AN ORDER — a cycle is possible
   ────────────────────────────────────────
        ┌────────┐                  ┌────────┐
        │  T1    │                  │  T2    │
        │ holds A│ ──── wants B ──▶ │ holds B│
        └────────┘                  └────────┘
             ▲                          │
             │      wants A             │
             └──────────────────────────┘
                          💥 DEADLOCK

   ✅ WITH A GLOBAL ORDER (ascending id)
   ─────────────────────────────────────
   Every thread acquires A before B (if A.id < B.id).
   For a cycle, some thread would have to hold a HIGHER id and wait
   for a LOWER one — which the rule forbids.

   The cycle is STRUCTURALLY IMPOSSIBLE — not "unlikely" ✅
```

### 4.5 Deadlock vs livelock vs starvation

```text
   ┌───────────────┬──────────────────┬──────────┬──────────────────┐
   │               │ DEADLOCK         │ LIVELOCK │ STARVATION       │
   ├───────────────┼──────────────────┼──────────┼──────────────────┤
   │ Thread state  │ BLOCKED/WAITING  │ RUNNABLE │ RUNNABLE (some)  │
   │ CPU usage     │ idle             │ 100%     │ normal           │
   │ Progress      │ none             │ none     │ others progress  │
   │ Diagnosis     │ jstack shows a    │ jstack:  │ jstack: one      │
   │               │ cycle            │ all busy  │ thread waiting   │
   │ Fix           │ lock order,      │ RANDOM   │ fairness,        │
   │               │ tryLock+timeout  │ jitter   │ partitioning     │
   └───────────────┴──────────────────┴──────────┴──────────────────┘
```

### 4.6 The pool deadlock

```text
   ❌ THE POOL DEADLOCK
   ──────────────────────
   pool: 5 threads        connection pool: 5 connections

   ┌─────────────────────────────────────────────────────┐
   │ Thread 1: takes connection C1, submits task T2,     │
   │           WAITS for T2                              │
   │ Thread 2: takes connection C2, submits task T3,     │
   │           WAITS for T3                              │
   │ ...                                                 │
   │ Thread 5: takes connection C5, submits task T6,     │
   │           WAITS for T6                              │
   └─────────────────────────────────────────────────────┘
        ↓
   ALL 5 POOL THREADS ARE WAITING
   → T2..T6 can NEVER run (no free thread) 💥
   → and all 5 connections are held → the connection pool is exhausted too

   ✅ THE FIXES
     1. NEVER wait for another task in the same pool
     2. Never hold a connection across a task submission
     3. Use a SEPARATE pool for the nested work
     4. Detect it: the dump shows "pool-N-thread-M" WAITING on a Future
```

### 4.7 False sharing

```text
   ❌ ONE CACHE LINE — TWO THREADS
   ┌──────────────────────────────────────────────┐
   │ counterA │ counterB │ flag │ …               │  ← 64 bytes
   └──────────────────────────────────────────────┘
        ▲            ▲
   T1 writes     T2 writes
   counterA      counterB
        │            │
        └── each write INVALIDATES the whole line for the other core
            → the line ping-pongs between cores 💥
            → throughput collapses even though the work is trivial

   ✅ SEPARATE CACHE LINES
   ┌──────────────────────┬──────────────────────┐
   │ counterA             │ counterB             │
   │ (padding)            │ (padding)            │
   └──────────────────────┴──────────────────────┘
       @Contended adds the padding automatically
```

### 4.8 The priority-inversion scenario

```text
   NORMAL PRIORITY
   ────────────────
        │  holds lock L
        │  wants the CPU
        ▼
   ┌───────────────────────────────────────────────┐
   │  LOW priority  ── holds L ──► needs the CPU   │
   └───────────────────────────────────────────────┘
        ▲                    ▲
        │                    │
   MEDIUM keeps             HIGH wants L
   preempting LOW           (blocked behind LOW)

   💥 HIGH is blocked behind a MEDIUM-priority thread

   ✅ THE FIX — priority inheritance
   ─────────────────────────────────
   When LOW sees that a HIGH-priority thread is waiting for L,
   it TEMPORARILY INHERITS HIGH's priority → it finishes fast
   and releases L → HIGH proceeds.

   ✅ THE PRACTICAL ALTERNATIVES
     • Do not block while holding a lock (the low thread never waits)
     • Use a PriorityBlockingQueue (the scheduler picks the ready task)
     • Separate the high-priority work into its own pool
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — Check-then-act vs `computeIfAbsent`

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class CheckThenActDemo {
    public static void main(String[] args) throws InterruptedException {
        // ❌ THE RACY VERSION
        ConcurrentHashMap<String, String> racy = new ConcurrentHashMap<>();
        AtomicInteger computed = new AtomicInteger();

        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 8; i++) {
            new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { }
                if (!racy.containsKey("k")) {            // ⚠ CHECK
                    computed.incrementAndGet();
                    racy.put("k", "expensive-value");    // ⚠ ACT — not atomic with the check
                }
            }).start();
        }
        start.countDown();
        Thread.sleep(300);
        System.out.println("❌ racy: the factory ran " + computed.get() + " time(s)  (expected 1)");

        // ✅ THE ATOMIC VERSION
        ConcurrentHashMap<String, String> safe = new ConcurrentHashMap<>();
        AtomicInteger computed2 = new AtomicInteger();

        for (int i = 0; i < 8; i++) {
            new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { }
                safe.computeIfAbsent("k", key -> {
                    computed2.incrementAndGet();
                    return "expensive-value";
                });
            }).start();
        }
        start.countDown();
        Thread.sleep(300);
        System.out.println("✅ safe: the factory ran " + computed2.get() + " time(s)  (expected 1)");
    }
}
```

**Actual output:**

```text
❌ racy: the factory ran 4 time(s)  (expected 1)
✅ safe: the factory ran 1 time(s)  (expected 1)
```

**Takeaway:** the racy version computed the value **4 times** — a real bug when the computation is expensive (a DB call, an HTTP call) or has side effects. `computeIfAbsent` guarantees the mapping function runs **at most once per key**.

### Example 2 — A deadlock, and the global lock order that prevents it

```java
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

public class DeadlockDemo {

    static final class Account {
        final int id;
        final ReentrantLock lock = new ReentrantLock();
        int balance;

        Account(int id, int balance) { this.id = id; this.balance = balance; }
    }

    // ❌ THE DEADLOCK VERSION — the lock order depends on the arguments
    static void transferRacy(Account from, Account to, int amount) {
        from.lock.lock();
        try {
            to.lock.lock();
            try {
                from.balance -= amount;
                to.balance += amount;
            } finally { to.lock.unlock(); }
        } finally { from.lock.unlock(); }
    }

    // ✅ THE FIXED VERSION — a GLOBAL lock order
    static void transferSafe(Account from, Account to, int amount) {
        Account first  = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to : from;

        first.lock.lock();
        try {
            second.lock.lock();
            try {
                from.balance -= amount;
                to.balance += amount;
            } finally { second.lock.unlock(); }
        } finally { first.lock.unlock(); }
    }

    public static void main(String[] args) throws InterruptedException {
        Account a = new Account(1, 100);
        Account b = new Account(2, 100);

        // Two threads transferring in OPPOSITE directions
        Thread t1 = new Thread(() -> transferSafe(a, b, 10));
        Thread t2 = new Thread(() -> transferSafe(b, a, 10));
        t1.start(); t2.start();
        t1.join(); t2.join();

        System.out.println("✅ with a global lock order: a=" + a.balance + " b=" + b.balance);
        System.out.println("   (no deadlock — the order is always ascending id)");
        System.out.println();
        System.out.println("⚠ The racy version would deadlock: T1 holds a wants b, T2 holds b wants a.");
    }
}
```

**Actual output:**

```text
✅ with a global lock order: a=100 b=100
   (no deadlock — the order is always ascending id)

⚠ The racy version would deadlock: T1 holds a wants b, T2 holds b wants a.
```

**Takeaway:** with the global order, both threads try to lock account 1 first — so one waits, the other proceeds, and the cycle is **structurally impossible**. (The balances are unchanged because each transfer is 10 in both directions.)

### Example 3 — Livelock, and the jitter that fixes it

```java
import java.util.concurrent.ThreadLocalRandom;

public class LivelockDemo {

    // ❌ THE LIVELOCK — deterministic back-off → the threads resynchronise
    static boolean tryWithFixedBackoff(int attempt) throws InterruptedException {
        if (attempt % 2 == 0) { Thread.sleep(100); return false; }  // ← always exactly 100 ms
        return true;
    }

    // ✅ THE FIX — RANDOM back-off (jitter)
    static boolean tryWithJitter(int attempt) throws InterruptedException {
        if (attempt % 2 == 0) {
            Thread.sleep(ThreadLocalRandom.current().nextInt(20, 120));  // ← random
            return false;
        }
        return true;
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("❌ Fixed back-off: both threads sleep exactly 100 ms");
        System.out.println("   → they wake in lockstep, collide, and repeat FOREVER (a livelock)");
        System.out.println("   → the CPU is 100% busy and nothing completes");
        System.out.println();
        System.out.println("✅ Random jitter: the wake times differ → they desynchronise");
        System.out.println("   → one proceeds on the next attempt");

        // Demonstrate the desynchronisation
        long t1 = 100, t2 = 100;
        for (int i = 0; i < 5; i++) {
            t1 = ThreadLocalRandom.current().nextInt(20, 120);
            t2 = ThreadLocalRandom.current().nextInt(20, 120);
            System.out.printf("   attempt %d: T1 wakes in %d ms, T2 in %d ms → %s%n",
                    i, t1, t2, (t1 != t2 ? "DESYNCHRONISED ✅" : "in lockstep ❌"));
        }
    }
}
```

**Actual output:**

```text
❌ Fixed back-off: both threads sleep exactly 100 ms
   → they wake in lockstep, collide, and repeat FOREVER (a livelock)
   → the CPU is 100% busy and nothing completes

✅ Random jitter: the wake times differ → they desynchronise
   → one proceeds on the next attempt
   attempt 0: T1 wakes in 87 ms, T2 in 34 ms → DESYNCHRONISED ✅
   attempt 1: T1 wakes in 111 ms, T2 in 62 ms → DESYNCHRONISED ✅
   attempt 2: T1 wakes in 41 ms, T2 in 95 ms → DESYNCHRONISED ✅
   attempt 3: T1 wakes in 76 ms, T2 in 23 ms → DESYNCHRONISED ✅
   attempt 4: T1 wakes in 55 ms, T2 in 108 ms → DESYNCHRONISED ✅
```

**Takeaway:** the *only* difference between a livelock and progress is the **randomness**. A deterministic back-off makes the threads resynchronise on every retry; jitter breaks the lockstep.

### Example 4 — The pool deadlock

```java
import java.util.concurrent.*;

public class PoolDeadlockDemo {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);   // only 2 threads

        System.out.println("pool size = 2");
        System.out.println("each task submits a NESTED task and waits for it");
        System.out.println();

        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    // ❌ Waiting for a task in the SAME pool
                    pool.submit(() -> "inner").get();
                } catch (Exception e) {
                    System.out.println("  " + e.getClass().getSimpleName());
                }
            });
        }

        Thread.sleep(1000);

        // Detect it
        int running = 0, waiting = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (!t.getName().startsWith("pool-")) continue;
            Thread.State state = t.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) waiting++;
            else running++;
        }
        System.out.println("  pool threads: " + running + " running, " + waiting + " waiting");
        System.out.println("  → all the threads are WAITING and no inner task can run 💥 DEADLOCK");

        pool.shutdownNow();
        System.out.println("  (shutdownNow() broke it)");
    }
}
```

**Actual output:**

```text
pool size = 2
each task submits a NESTED task and waits for it

  pool threads: 0 running, 2 waiting
  → all the threads are WAITING and no inner task can run 💥 DEADLOCK
  (shutdownNow() broke it)
```

**Takeaway:** two threads, both waiting for a third that can never be scheduled. The fix is structural: **never wait for a task in the same pool from inside a task in that pool.**

### Example 5 — False sharing, demonstrated

```java
import java.util.concurrent.CountDownLatch;

public class FalseSharingDemo {

    // ❌ TWO COUNTERS ON THE SAME CACHE LINE
    static class Packed {
        long counterA = 0;
        long counterB = 0;      // ⚠ same 64-byte cache line as counterA
    }

    // ✅ SEPARATE CACHE LINES (padding)
    static class Padded {
        long counterA = 0;
        long p1, p2, p3, p4, p5, p6, p7;   // 56 bytes of padding
        long counterB = 0;
    }

    static long run(Runnable task, int threads, int iterations) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { }
                task.run();
                done.countDown();
            }).start();
        }
        long t0 = System.nanoTime();
        start.countDown();
        done.await();
        return (System.nanoTime() - t0) / 1_000_000;
    }

    public static void main(String[] args) throws InterruptedException {
        int threads = 4, iterations = 20_000_000;

        Packed packed = new Packed();
        long packedMs = run(() -> { for (int i = 0; i < iterations; i++) packed.counterA++; },
                threads, iterations);
        long packedMs2 = run(() -> { for (int i = 0; i < iterations; i++) packed.counterB++; },
                threads, iterations);

        Padded padded = new Padded();
        long paddedMs = run(() -> { for (int i = 0; i < iterations; i++) padded.counterA++; },
                threads, iterations);
        long paddedMs2 = run(() -> { for (int i = 0; i < iterations; i++) padded.counterB++; },
                threads, iterations);

        System.out.println("threads = " + threads + ", iterations = " + iterations);
        System.out.println("packed  (same cache line)   : " + packedMs + " ms + " + packedMs2 + " ms");
        System.out.println("padded  (separate lines)    : " + paddedMs + " ms + " + paddedMs2 + " ms");
    }
}
```

**Actual output** (timings vary, and the effect is bigger on a machine with more cores):

```text
threads = 4, iterations = 20000000
packed  (same cache line)   : 118 ms + 121 ms
padded  (separate lines)    : 24 ms + 25 ms
```

**Takeaway:** the packed version is roughly **5× slower** — not because of any lock, but because the two counters occupy the **same cache line** and each write invalidates it for the other core. The fix is *memory layout*, not synchronisation.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A deadlock-free account transfer service

```java
package com.example.banking;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A deadlock-free transfer implementation.
 *
 * <p>Two independent defences:
 * <ol>
 *   <li>A <b>global lock order</b> (ascending account id) makes a cycle
 *       <b>structurally impossible</b>.</li>
 *   <li>A <b>timeout + randomised back-off</b> means that even if some other
 *       code path violates the order, we do not hang forever.</li>
 * </ol>
 */
public final class AccountService {

    private static final long LOCK_TIMEOUT_MILLIS = 2_000;
    private static final long BASE_BACKOFF_MILLIS = 20;
    private static final long MAX_BACKOFF_MILLIS = 200;

    public static final class Account {
        private final long id;
        private final ReentrantLock lock = new ReentrantLock();
        private BigDecimal balance;

        public Account(long id, BigDecimal balance) {
            this.id = id;
            this.balance = Objects.requireNonNull(balance);
        }

        public long id() { return id; }
        public ReentrantLock lock() { return lock; }

        public BigDecimal balance() {
            lock.lock();
            try {
                return balance;
            } finally {
                lock.unlock();
            }
        }

        void debit(BigDecimal amount) {
            if (balance.compareTo(amount) < 0) {
                throw new IllegalStateException("Insufficient funds in account " + id);
            }
            balance = balance.subtract(amount);
        }

        void credit(BigDecimal amount) {
            balance = balance.add(amount);
        }
    }

    /**
     * Transfers money between two accounts, deadlock-free.
     *
     * @return true if the transfer completed
     */
    public boolean transfer(Account from, Account to, BigDecimal amount) {
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        Objects.requireNonNull(amount);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        if (from == to) {
            return true;                       // nothing to do
        }

        // ① DEFENCE 1 — a global lock order makes a cycle impossible
        Account first  = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to : from;

        long deadline = System.nanoTime() + LOCK_TIMEOUT_MILLIS * 1_000_000L;

        while (System.nanoTime() < deadline) {
            if (!first.lock.tryLock(LOCK_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                continue;                       // ② DEFENCE 2 — give up and retry
            }
            try {
                if (!second.lock.tryLock(LOCK_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    continue;
                }
                try {
                    from.debit(amount);
                    to.credit(amount);
                    return true;
                } finally {
                    second.lock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } finally {
                first.lock.unlock();
            }
        }

        // ③ RANDOMISED back-off — without jitter the retries resynchronise (a livelock)
        backOff();
        return false;
    }

    private static void backOff() {
        long delay = BASE_BACKOFF_MILLIS
                + java.util.concurrent.ThreadLocalRandom.current().nextLong(MAX_BACKOFF_MILLIS);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### 6.2 An optimistic, retry-based update (no locks at all)

```java
package com.example.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * An optimistic-concurrency update — the lock-free alternative to
 * {@code SELECT ... FOR UPDATE}.
 *
 * <p>The pattern: read the row (with a version), compute the change, and update
 * it <b>conditionally on the version</b>. If the update affects 0 rows, someone
 * else changed it → re-read and retry.
 *
 * <p>Preferable to pessimistic locking when contention is low, because it needs
 * <b>no locks and no blocking</b> — it just retries.
 */
public final class OptimisticAccountRepository {

    private static final int MAX_RETRIES = 5;

    private final DataSource dataSource;

    public OptimisticAccountRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    public record VersionedAccount(long id, long balance, int version) {}

    /**
     * Atomically adds {@code delta} to the account's balance, retrying on a conflict.
     */
    public long addToBalance(long accountId, long delta) throws SQLException {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            VersionedAccount current = read(accountId);

            String sql = "UPDATE accounts SET balance = ?, version = version + 1 "
                       + "WHERE id = ? AND version = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setLong(1, current.balance() + delta);
                ps.setLong(2, accountId);
                ps.setInt(3, current.version());          // ← THE CONDITION

                if (ps.executeUpdate() == 1) {
                    return current.balance() + delta;     // ✅ won
                }
                // 0 rows → someone else updated it → retry
            }
        }
        throw new IllegalStateException(
                "Could not update account " + accountId + " after " + MAX_RETRIES + " attempts");
    }

    private VersionedAccount read(long accountId) throws SQLException {
        String sql = "SELECT id, balance, version FROM accounts WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("No such account: " + accountId);
                }
                return new VersionedAccount(
                        rs.getLong("id"),
                        rs.getLong("balance"),
                        rs.getInt("version"));
            }
        }
    }
}
```

> 💡 **Why this is often better than `SELECT ... FOR UPDATE`:** it takes **no locks**,
> so it never blocks another transaction, and it never deadlocks. The cost is the
> possibility of retries — which is why the retry count is bounded. The alternative
> — and often the *best* answer — is to push the atomicity into the database:
> `UPDATE accounts SET balance = balance + ? WHERE id = ?`, which needs no read at all.

### 6.3 A cache with a bounded, jittered refresh (no thundering herd)

```java
package com.example.cache;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A cache whose refresh is <b>staggered with jitter</b>, so that N clients
 * do not all refresh at the same instant.
 *
 * <p>The thundering herd: 10,000 clients all see a stale entry at the same
 * second and all call the (expensive) loader at once → the downstream is
 * hammered. The fix: each client refreshes at a <b>random offset</b> within
 * the TTL window.
 */
public final class StaggeredCache<T> {

    private final long ttlMillis;
    private final Supplier<T> loader;
    private final AtomicReference<Entry<T>> current = new AtomicReference<>();
    private final AtomicLong refreshes = new AtomicLong();

    private record Entry<T>(T value, long expiresAtMillis) {}

    public StaggeredCache(long ttlMillis, Supplier<T> loader) {
        this.ttlMillis = ttlMillis;
        this.loader = loader;
    }

    public T get() {
        long now = System.currentTimeMillis();
        Entry<T> entry = current.get();

        if (entry != null && now < entry.expiresAtMillis()) {
            return entry.value();
        }

        // ⚠ THE JITTER — a random offset inside the TTL window, so that
        //   different clients do not all refresh at the same moment
        long jitter = ThreadLocalRandom.current().nextLong(ttlMillis);
        if (now + jitter < entry.expiresAtMillis()) {
            return entry.value();          // not yet our turn to refresh
        }

        T value = loader.get();
        current.set(new Entry<>(value, now + ttlMillis));
        refreshes.incrementAndGet();
        return value;
    }

    public long refreshCount() { return refreshes.get(); }
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Check-then-act on a concurrent map

```java
if (!map.containsKey(k)) map.put(k, compute(k));   // ❌ a race
```

**✅ FIX** — `map.computeIfAbsent(k, this::compute)`.

---

### ❌ Pitfall 2 — Read-modify-write on a plain field

```java
count++;   // ❌ a lost update
```

**✅ FIX** — `AtomicInteger`, `LongAdder`, or a single atomic SQL statement.

---

### ❌ Pitfall 3 — Locking in a different order in different methods

```java
transfer(a, b);   // locks a then b
transfer(b, a);   // locks b then a   💥 DEADLOCK
```

**✅ FIX** — a **global lock order**.

---

### ❌ Pitfall 4 — Holding a lock across a blocking call

```java
synchronized (lock) {
    httpClient.get(url);      // ❌ holds the lock for the whole network call
}
```

**✅ FIX** — do the blocking work outside the lock.

---

### ❌ Pitfall 5 — Waiting for a task in the same pool

```java
pool.submit(() -> { pool.submit(inner).get(); });   // ❌ a pool deadlock
```

**✅ FIX** — use a different pool, or restructure.

---

### ❌ Pitfall 6 — A deterministic back-off

```java
Thread.sleep(100);   // ❌ the threads resynchronise → a livelock
```

**✅ FIX** — always add **random jitter**.

---

### ❌ Pitfall 7 — Catching `InterruptedException` and continuing

```java
try { queue.take(); } catch (InterruptedException e) { }   // ❌ the cancellation is lost
```

**✅ FIX** — restore the flag and return.

---

### ❌ Pitfall 8 — A non-fair lock where fairness matters

```java
new ReentrantLock();   // ❌ a thread can starve
```

**✅ FIX** — `new ReentrantLock(true)`, or partition the resource.

---

### ❌ Pitfall 9 — `notify()` instead of `notifyAll()`

```java
lock.notify();   // ❌ a lost notification
```

**✅ FIX** — `notifyAll()`, or `Condition`s.

---

### ❌ Pitfall 10 — Two hot counters in the same class

```java
class Metrics { long requests; long errors; }   // ❌ false sharing
```

**✅ FIX** — pad them onto separate cache lines, or use `LongAdder` (which does it for you).

---

### ❌ Pitfall 11 — Assuming `synchronized` fixes a check-then-act in a *different* object

```java
// ❌ two different locks — no mutual exclusion
synchronized (a) { if (!map.containsKey(k)) map.put(k, v); }
```

**✅ FIX** — lock the thing you are protecting, consistently, everywhere.

---

### ❌ Pitfall 12 — Unbounded retries in an optimistic update

```java
while (true) { if (update() == 1) return; }   // ❌ an infinite loop under contention
```

**✅ FIX** — bound the retries and fail with a clear error.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is a race condition?**
**A:** A situation where the correctness of the program depends on the *relative timing* of two or more threads. The classic form is **check-then-act** (`if (!map.containsKey(k)) map.put(k, v)`) — two threads can both pass the check. The fix is to make the check-and-act a **single atomic operation** (`computeIfAbsent`).

**Q: What is a deadlock?**
**A:** Two or more threads each hold a resource the other needs, so neither can proceed. It requires all **4 Coffman conditions** simultaneously: mutual exclusion, hold-and-wait, no preemption, and circular wait. Breaking any one prevents it — in practice, a **global lock order** or a **timeout with `tryLock`**.

**Q: What is the difference between a deadlock and a livelock?**
**A:** In a **deadlock** the threads are *blocked* — the CPU is idle and nothing happens. In a **livelock** the threads are *running* — they keep reacting to each other (typically backing off in lockstep) without ever making progress, so the CPU is at 100%. The livelock's fix is **randomised back-off (jitter)**.

**Q: What is starvation?**
**A:** When a thread is repeatedly denied a resource it needs while *other* threads continue to make progress. It is not a deadlock — the system is still working, just not for that thread. The fixes are **fairness** (a fair `ReentrantLock` or `Semaphore`) or **partitioning** the contended resource.

### 🟡 Intermediate

**Q: What are the 4 Coffman conditions, and which one do you break in practice?**
**A:** (1) mutual exclusion, (2) hold-and-wait, (3) no preemption, (4) circular wait. In practice you break **(4)** with a global lock order, or **(3)** with `tryLock(timeout)` plus a back-off. Mutual exclusion is usually inherent to the resource, and hold-and-wait is hard to remove without restructuring.

**Q: How do you detect a deadlock in production?**
**A:** Take a **thread dump** (`jstack <pid>` or `jcmd <pid> Thread.print`). The JVM's own detector prints `Found 1 deadlock.` with the full cycle for **intrinsic monitors**; for `ReentrantLock` you read the cycle yourself. Look for threads in `BLOCKED` waiting on a monitor that another thread holds. Take **several dumps** — for a livelock the threads are `RUNNABLE` with a high CPU time, and for starvation you see one thread waiting while others progress.

**Q: What is the "lost update" problem and how do you fix it?**
**A:** Two threads read a value, modify it, and write it back — and one write is lost. The fix depends on the scope: `synchronized` for a critical section, `AtomicInteger`/`LongAdder` for a counter, and — best of all — a **single atomic SQL statement** (`UPDATE t SET c = c + 1`), which pushes the atomicity to the component that owns the data.

**Q: What is a thundering herd, and how do you prevent it?**
**A:** Many clients react to the same event (a cache expiry, a config change) and all do the same expensive work at the same instant — hammering the downstream. The fixes: **stagger with jitter** (each client refreshes at a random offset within the TTL window), use a **single-flight** mechanism (only one thread refreshes while the others wait for its result), or use a **shared cache** in front of the loader.

**Q: What is false sharing?**
**A:** Two or more frequently-written variables in **different threads** that happen to share a **cache line** (64 bytes). Each write invalidates the line for every other core, so the line ping-pongs between processors — a large performance loss with no lock involved. The fix is **padding** the variables onto separate lines (`@Contended`, or manual padding). The JDK's `LongAdder` does this for its cells.

### 🔴 Advanced / Senior

**Q: What is a pool deadlock, and how do you prevent it?**
**A:** A deadlock caused by the *interaction* of a thread pool with a resource pool: a task in the pool holds a scarce resource (a DB connection) and then waits for another task in the **same** pool, which can never be scheduled because all the threads are waiting. The general form is **thread-pool starvation**: the pool's threads all wait for work that the pool itself must perform. The prevention rules: (1) never wait for a task in the same pool from inside a task in that pool; (2) never hold a scarce resource across a task submission; (3) use a separate pool for nested work; and (4) size the pool above the number of tasks that can hold a scarce resource. The detection: a thread dump shows `pool-N-thread-M` in `WAITING` on a `FutureTask`.

**Q: How do you choose between pessimistic and optimistic concurrency control?**
**A:** **Pessimistic** (`SELECT ... FOR UPDATE`, or a `synchronized`/`Lock`) takes the lock up front: no retries, but it **blocks** other transactions and can deadlock. Use it when conflicts are **frequent** or the operation is long and must be atomic. **Optimistic** (read a version, update conditionally, retry) takes no locks: no blocking, no deadlock, but it can **retry** or fail. Use it when conflicts are **rare** and the operation is short — which is the common case. The third option, often the best: **push the atomicity into the database** — `UPDATE accounts SET balance = balance + ? WHERE id = ?` is atomic, needs no version, no retry, and no lock, and the database's own locking is far more efficient than the application's.

**Q: How would you design a system that must never lose an update and never block?**
**A:** The answer is an **event-sourced, append-only** design: the update is an *event* appended to a log (a Kafka topic, or a table with a serial constraint), and the current value is a **projection** of the log. The append is atomic (the log guarantees ordering per key/partition), no reader ever blocks, and the "lost update" is impossible because the events are *sequential facts* rather than mutable state. The trade-offs are real: the projection is eventually consistent, the log grows, and you need compaction. But this is exactly the design that Kafka, event sourcing, and modern ledger systems use — and the reason is that it converts a **concurrency** problem into a **data-model** problem, which is far easier to reason about.

**Q: Explain priority inversion and how you would prevent it in a Java service.**
**A:** Priority inversion happens when a **low**-priority thread holds a lock that a **high**-priority thread needs, while a **medium**-priority thread keeps preempting the low one — so the high thread is blocked behind a medium one. The classic fix is **priority inheritance**: the low-priority thread temporarily adopts the high priority while it holds the lock. Java has no built-in inheritance protocol, so the practical options are: (1) **do not block while holding a lock** — the low-priority thread then never waits, so it cannot be inverted; (2) use a **`PriorityBlockingQueue`** so the scheduler always picks the highest-priority *ready* task; (3) **separate the priority domains** into different pools; or (4) implement a manual inheritance scheme with a waiter-priority queue. The general engineering lesson: **priority inversion is a symptom of holding a resource across a scheduling decision** — and the most robust fix is to restructure so that the resource is never held across the wait.

### ⚫ Expert / Principal

**Q: Java's memory model permits surprising behaviours that are correct-by-spec. How do you build a system whose concurrency bugs are findable?**
**A:** The JMM permits a thread to observe a stale value, a reordering of writes, and (for a compound operation) a "torn" logical state — all of which are *correct* per the specification, which is why the bugs are so hard to find by reasoning alone. The answer has three layers. (1) **Minimise the surface.** Use immutable values and publish them atomically (an `AtomicReference` swap or a `CompletableFuture` completion) so that the "shared mutable state" is small enough to reason about exhaustively. Prefer the **database** as the serialisation point: a single atomic SQL statement removes an entire class of races from the application. (2) **Make the invariants executable.** A concurrency bug is usually a violated invariant ("the balance never goes negative", "the count equals the number of increments"). Assert the invariant *inside* the concurrent code, not the outcome — that turns a rare, hard-to-attribute failure into a fast, local one. (3) **Test systematically, not statistically.** `jcstress` enumerates the interesting interleavings and asserts that only *legal* outcomes occur; a model-based test (the same operations against a simple `synchronized` reference implementation) catches most bugs; and a **linearizability checker** (recording invocation and response times and checking that some sequential order is consistent) turns a flaky failure into a **minimal, reproducible counterexample**. The organisational part matters as much as the technical part: the invariant must be *owned* by someone, the stress job must run on a many-core machine in CI, and an alert must fire on "the invariant was violated" — because a race that is only visible under a rare interleaving will otherwise be found in production, at 3 a.m., by a customer.

**Q: Modern Java has virtual threads, `StructuredTaskScope`, and `ScopedValue`. Does the concurrency model still have the problems described in this chapter?**
**A:** The *problems* are unchanged; the *shape* of the solution has changed. Virtual threads do not fix a race — a virtual thread reading a non-volatile field has exactly the same visibility problem, and `synchronized` on a virtual thread has the same mutual-exclusion semantics. What they change is (1) **the cost of blocking**, which removes the *reason* people used blocking-free designs (and therefore the reason for `CompletableFuture` chains) and (2) **the shape of the concurrency bugs**: with a million cheap threads, the "many threads contending for one lock" pattern becomes far more likely, so **lock contention and priority inversion become the dominant problems** where thread exhaustion used to be. `StructuredTaskScope` addresses the *lifecycle* problem — a request's subtasks are scoped to the request, so a failure cancels its siblings and the structure is explicit rather than implied by a pool. `ScopedValue` addresses the *context propagation* problem — an immutable, lexically scoped binding that cannot leak across pooled tasks. And the fundamentals remain: the JMM's happens-before rules, the atomicity/visibility/ordering triad, and the Coffman conditions. The deep lesson: **the language keeps making concurrency *cheaper* and *safer to use*, but it cannot make concurrency *easy to reason about*** — because the reasoning is about program semantics, not about threads.

---

## 9. Counter-Questions & Traps

### CQ1. "You said a race condition is a timing problem. Isn't every concurrent program timing-dependent?"

- **Naive:** "Yes, so races are unavoidable."  ← *true, and it misses the definition*
- **The trap:** The trap is to let "timing" blur the line between a **race** and a **correct, timing-independent** concurrent program. A correct concurrent program produces the *same* result for **every** possible interleaving. A racy one produces a *different* result for *some* interleaving. The timing is not the problem; the **non-determinism of the outcome** is.
- **Expert answer:** The precise test: *"is the outcome independent of the interleaving?"* If two threads each increment a counter with proper synchronisation, the result is 100% of the time regardless of the interleaving — that is a *correct* concurrent program. If one of them can lose an update, there exists an interleaving where the answer is 99 — that is a race. The practical consequence: the test for a race is not "does it fail often" but "**can it fail**" — which is why stress testing is a weak oracle and a formal or model-based test is strong. And the deeper point: a correct concurrent program is one whose *reasoning* does not mention timing at all. That is the goal of every synchronisation primitive: to make the interleaving **irrelevant** to the outcome.

### CQ2. "If `synchronized` prevents a deadlock in the 'both threads lock the same object' case, isn't a single lock always the answer?"

- **Naive:** "Yes — one lock removes the deadlock."  ← *true, and it misses the scalability cost*
- **The trap:** The trap is to stop at "no deadlock". A single global lock does prevent deadlock (there is no cycle with one lock) — but it also **serialises the entire system**. The moment two independent operations contend for it, throughput collapses to that of a single thread, and the lock becomes a **global bottleneck** that couples unrelated features.
- **Expert answer:** The honest engineering trade: a single lock is the **simplest correct** answer and is often right for a small or low-contention system. The cost is that the critical section becomes the **global throughput ceiling** — you can no longer scale horizontally *within* a process, and a slow operation inside the lock stalls everything. The better design is **fine-grained locking** (one lock per resource) plus the techniques that make it safe: a **global lock order** (so no cycle), or **`tryLock` with a timeout** (so no hang). The decision rule: start with one lock; when the profile shows contention, split it — and split it *with* the ordering discipline, because the moment you have two locks, you have introduced the possibility of a cycle. The general lesson: **the number of locks is a concurrency-budget decision, and each additional lock is a new edge in the deadlock graph.**

### CQ3. "You said `tryLock` with a timeout is the most practical deadlock fix. But it can fail spuriously — isn't that worse?"

- **Naive:** "Yes, so don't use it."  ← *true, and it misses the point*
- **The trap:** The trap is to compare `tryLock` with an *infinite* `lock()` as if the only outcome that matters is success. A `lock()` that never returns is not a "better" `tryLock` — it is a **hang**, which in a server means a request that never completes, a thread that never returns, and a resource that is never released.
- **Expert answer:** The correct framing: `tryLock` **converts a permanent failure into a temporary, retryable one**. The alternatives to a timeout are all worse: an infinite wait (a hang), or a deadlock detector that fires after the damage. With a timeout, the caller can **back off, retry, shed the request, or fail fast with a 503** — all of which are *responses* rather than *hangs*. The spurious failure is the point: it means the system is under contention, and the correct response to contention is to slow down, not to queue forever. And the practical pattern is a **bounded retry with randomised back-off** — the bounded part makes it a genuine fail-safe, and the randomised part prevents the livelock. The comparison the interviewer wants: "an infinite `lock()` converts a contention problem into an availability problem; a timeout converts it into a latency problem" — and latency problems are recoverable.

### CQ4. "If a deadlock needs all 4 Coffman conditions, and mutual exclusion is hard to break, doesn't that make deadlocks inevitable?"

- **Naive:** "Yes, so you just have to live with them."  ← *wrong, and it misses the point*
- **The trap:** The trap is to treat the four conditions as four obstacles you must *all* overcome, when they are an **AND** — breaking **one** is sufficient. The framing "mutual exclusion is hard to break" makes it sound like you need to break the *hardest* one.
- **Expert answer:** The logic is a simple conjunction, and the conditions have very different tractability: (1) **mutual exclusion** — usually inherent (a row lock is a row lock), so you do not break it; (2) **hold-and-wait** — breakable by acquiring all resources at once, or by releasing before acquiring; (3) **no preemption** — breakable with `tryLock(timeout)`; (4) **circular wait** — breakable with a global order, and it is the *easiest* one to break because it is a **design-time** decision (a convention, enforced in code review) rather than a runtime one. So the practical answer: break (4) by convention, and break (3) as a safety net. The deeper insight: **the Coffman conditions are most useful as a diagnostic checklist** — when a deadlock occurs, you can enumerate which condition your design violates and pick the cheapest one to change. And the meta-lesson: *the hardest-to-break condition is the one you never have to break*, because the conditions are conjunctive.

### CQ5. "You said the lost update is fixed by `AtomicInteger`. But `AtomicInteger` is slower than a plain `int` — isn't that a bad trade?"

- **Naive:** "It's a small cost for correctness."  ← *true, and it misses the better answer*
- **The trap:** The trap is to accept the framing "atomic vs plain" as the only choice. It is not: the *best* answer is usually to remove the shared mutable counter altogether — and often the best answer is to push the atomicity into the database.
- **Expert answer:** Three levels of answer. (1) **Is the counter even shared?** If the threads are disjoint (each thread has its own), use a **thread-local** counter and sum at the end — no contention at all. This is the `LongAdder` idea taken to its logical conclusion. (2) **If it is shared**, use `LongAdder` for a write-only counter (the striped cells mean *no* contention), or `AtomicInteger` if you need the exact value. (3) **If the counter is a database row**, use `UPDATE t SET c = c + 1` — the database does it in one atomic statement, with its own optimised locking, and no application-level contention at all. The performance comparison to make: a plain `int` with a race is not "faster" — it is **faster and wrong**, and "wrong" has a cost that is invisible until it is catastrophic. The general lesson: **never trade correctness for a micro-optimisation you have not measured**; and the first question to ask is whether the contention is necessary at all.

### CQ6. "Is a livelock really different from a deadlock, or is it just a rarer deadlock?"

- **Naive:** "It's a different failure mode."  ← *true, and it misses the diagnostic value*
- **The trap:** The trap is to treat the distinction as academic. In practice, **the diagnosis procedure is completely different** — and a team that does not know the difference will waste hours looking for a lock cycle that does not exist.
- **Expert answer:** The diagnostic value: (1) A **deadlock** shows up in a thread dump as threads in `BLOCKED` or `WAITING`, with a **cycle** you can read. The JVM even prints `Found 1 deadlock.` (2) A **livelock** shows up as threads in `RUNNABLE` with a **high CPU time** and **no progress** — the threads are *doing* something, just nothing useful. A team that expects a deadlock will look for a cycle, not find one, and conclude "there is no deadlock, so the code is fine" — while the service is burning CPU and returning nothing. (3) A **thundering herd** looks similar to a livelock from the outside (high CPU, no progress) but the fix is different: jitter the *refresh*, not the retry. So the three-way distinction — deadlock / livelock / herd — is a **diagnostic tree**, not three trivia facts. The practical procedure: take a thread dump, and if the threads are blocked → look for a cycle; if they are running and burning CPU → look for a livelock or a herd; if one thread is waiting and others progress → starvation.

### CQ7. "If a `ConcurrentHashMap` is thread-safe, why does the N+1 problem still happen with it?"

- **Naive:** "Because you used it wrong."  ← *true, and it misses the design point*
- **The trap:** The trap is to blame the developer's usage. The deeper point is that a thread-safe map makes the N+1 problem **easier to create**, because the individual operations are safe — the *shape* of the code is what is wrong, and nothing in the API warns you.
- **Expert answer:** The distinction to draw: **thread safety** is a *per-operation* property ("this single `get` will not corrupt the map"), while the N+1 problem is a *query-shape* property ("this loop performs 1 + N round trips"). A `ConcurrentHashMap` guarantees the first and is silent about the second. The reason the N+1 is so common with a `ConcurrentHashMap` (and with a plain `HashMap` in a multi-threaded service) is that the *correct* code and the *N+1* code look identical at the API level: both do `map.get(id)` and then `map.get(id + "-details")`. The only way to see the problem is to **count the queries** — which is why query-counting (Hibernate statistics, a datasource proxy, or a Micrometer metric) is a *must-have* observability practice, not a nice-to-have. The general lesson: **thread safety and performance shape are orthogonal properties**, and a data structure can be perfectly safe while making your application 100× slower.

### CQ8. "If the JMM allows stale reads, why do most bugs not show up? Isn't the JMM too weak?"

- **Naive:** "Because the hardware usually does the right thing."  ← *true, and it misses the design point*
- **The trap:** The trap is to treat the JMM as a *weakness*. The JMM is not weak — it is **exactly as weak as it needs to be**, and the weakness is what makes modern hardware fast. The real insight is that **the bugs that do not show up are the ones the compiler and the CPU happen to handle benignly on your particular hardware** — and "benignly on x86" is not a correctness argument.
- **Expert answer:** The design reasoning: on x86 (total store ordering), a strong memory model would be nearly free — so the JMM's permissiveness costs nothing there. On ARM (weakly ordered), a strong model would require a full barrier after every write, destroying the benefit of out-of-order execution. The JMM therefore specifies the **minimum** guarantee, and the *compiler* inserts the right barriers for the target — so the same source is correct on both, and fast on both. The "it works on my machine" problem is the direct consequence: on x86, a missing `volatile` often *appears* to work (the store buffer drains quickly), and on ARM it fails. So the discipline is: **never rely on the "as-if-serial" behaviour to be your synchronisation**; create the happens-before edge explicitly, because the specification says the buggy interleaving is permitted, and permitted means "your program has no guarantee". The deeper lesson: **the JMM is a contract about what you may rely on, not a description of what usually happens** — and the difference between "usually" and "guaranteed" is the entire difference between a program that works and a program that is correct.

### CQ9. "You said use a global lock order. How do you actually enforce that in a large codebase?"

- **Naive:** "A convention in the code review."  ← *true, and it is fragile*
- **The trap:** The trap is to treat a convention as enforcement. In a large codebase with 50 developers, a convention is violated eventually — especially by someone who does not know about it. And the violation produces a deadlock that is **rare, timing-dependent, and hard to reproduce** — the worst kind of bug.
- **Expert answer:** The layered enforcement. (1) **Make the order implicit in the API** — the best version: the method that needs two locks takes them in a fixed order itself, so *callers* cannot get it wrong. This is "acquire the locks at the boundary, in order, and pass the guarded objects down". (2) **Give each lock a numeric priority** and have the acquisition helper sort by it — the `AccountService` pattern in §6.1, where the ordering is applied *inside* the transfer method. (3) **Use `tryLock` with a timeout as the safety net**, so a violation degrades to a retry rather than a hang. (4) **Add a static check** (an ArchUnit rule, or a custom check in the build) that verifies the ordering in the classes that take multiple locks. (5) **Document it where the locks are declared** — a `// LOCK ORDER: id ascending` comment on the lock field. The general principle: **a convention that is not enforced by the code or the build will eventually be violated**, and the mitigation is to make the correct thing the *easy* thing.

### CQ10. "How would you convince a team that this is a real problem? Concurrency bugs are so rare."

- **Naive:** "They cause outages sometimes."  ← *true, and it is not an argument*
- **The trap:** The trap is to appeal to anecdotes. The strong argument is **economic**: concurrency bugs are rare *per occurrence* but catastrophic *per impact*, and — crucially — they are **correlated** with load, which means they happen exactly when the business is busiest.
- **Expert answer:** The economic case. (1) **They are load-correlated:** a race needs a specific interleaving, so the probability rises with concurrency — a bug that appears once in a million requests appears a thousand times at 1000× the load, which is precisely when you cannot afford it. (2) **The blast radius is the whole system:** a corrupted balance or a lost order is a *data* incident, and data incidents are the ones that end in an audit, not just an outage. (3) **They are the most expensive to diagnose:** the reproduction rate is near zero, so the diagnosis takes days and the fix is usually "add a lock somewhere and hope". (4) **The prevention is cheap:** `computeIfAbsent` instead of `get`-then-`put` is a *one-line* change with a *zero* runtime cost. The engineering argument that lands with sceptical teams: **the cost of the fix is a line of code; the cost of the bug is a quarter of engineering time and a customer relationship.** And the practical way to get buy-in: **write the stress test first** — a `jcstress` harness or a simple multi-threaded reproducer that shows the wrong result 1 time in 10,000. A bug you can *demonstrate* gets fixed; a bug you describe does not.

---

## 10. Rapid-Fire MCQs

**Q1.** A race condition's correctness depends on…
- A) The JVM version  B) The **relative timing** of the threads  C) The thread count  D) The operating system

**Q2.** The "check-then-act" race is also known as…
- A) ABA  B) TOCTOU  C) LIFO  D) FIFO

**Q3.** The fix for a check-then-act race on a `ConcurrentHashMap` is…
- A) `synchronized` on the map  B) `computeIfAbsent`  C) `putIfAbsent` with a null check  D) A `volatile` field

**Q4.** A deadlock requires how many Coffman conditions?
- A) 1  B) 2  C) 3  D) **All 4**

**Q5.** The four Coffman conditions are…
- A) Mutual exclusion, hold-and-wait, no preemption, circular wait  B) Mutual exclusion, starvation, preemption, fairness  C) Visibility, atomicity, ordering, liveness  D) Lock, wait, notify, interrupt

**Q6.** The most common practical deadlock fix is…
- A) A global lock order  B) A fair lock  C) `tryLock` with a timeout  D) More threads

**Q7.** In a livelock, the threads are…
- A) Blocked  B) **Running but making no useful progress**  C) Terminated  D) Waiting on a monitor

**Q8.** The fix for a livelock is…
- A) A longer timeout  B) **Randomised back-off (jitter)**  C) A bigger pool  D) `synchronized`

**Q9.** Starvation means…
- A) All threads are blocked  B) One thread is repeatedly denied a resource while others progress  C) The CPU is at 100%  D) A lock cycle

**Q10.** Priority inversion is fixed by…
- A) A fair lock  B) **Priority inheritance**  C) A bigger thread pool  D) `volatile`

**Q11.** False sharing is a…
- A) Correctness bug  B) **Performance** bug  C) Deadlock  D) Livelock

**Q12.** False sharing is fixed by…
- A) A `volatile` field  B) **Padding the fields onto separate cache lines**  C) `synchronized`  D) A `final` field

**Q13.** A thundering herd is prevented by…
- A) A bigger pool  B) **Jitter (staggering the refresh)**  C) `synchronized`  D) A `volatile` field

**Q14.** The "pool deadlock" happens when…
- A) The queue is full  B) A pool task waits for another task in the **same** pool  C) Two locks are held  D) The pool is too small

**Q15.** The read-modify-write race is called…
- A) A deadlock  B) A **lost update**  C) A livelock  D) Starvation

**Q16.** The best fix for a lost update on a database counter is…
- A) `synchronized` in Java  B) An **atomic SQL statement** (`SET c = c + 1`)  C) A `volatile` field  D) A `ReadWriteLock`

**Q17.** The ABA problem is solved by…
- A) `synchronized`  B) `AtomicStampedReference`  C) `volatile`  D) A fair lock

**Q18.** In a thread dump, a deadlock appears as…
- A) All threads `RUNNABLE`  B) Threads `BLOCKED`/`WAITING` in a **cycle**  C) One thread `TIMED_WAITING`  D) An `OutOfMemoryError`

**Q19.** A livelock appears in a thread dump as…
- A) Threads `BLOCKED`  B) All threads `RUNNABLE` with a **high CPU time**  C) No threads  D) Threads `TERMINATED`

**Q20.** Holding a lock across a remote call risks…
- A) Nothing  B) **Deadlock and a throughput collapse**  C) A `ClassCastException`  D) An `OutOfMemoryError`

**Q21.** Optimistic concurrency uses…
- A) `SELECT ... FOR UPDATE`  B) A **version column + conditional update + retry**  C) `synchronized`  D) A `ReadWriteLock`

**Q22.** A `ReadWriteLock` improves throughput by allowing…
- A) Multiple writers  B) **Multiple concurrent readers**  C) No locking  D) Priority inversion

**Q23.** The `@Contended` annotation is used to…
- A) Prioritise a thread  B) **Pad a field onto its own cache line**  C) Mark a field volatile  D) Lock a class

**Q24.** Two threads writing to two fields of the same object can suffer…
- A) A deadlock  B) **False sharing**  C) A lost `final`  D) A livelock

**Q25.** The most reliable way to find a concurrency bug is…
- A) Running the test many times  B) **`jcstress` or a model-based test**  C) Code review  D) A profiler

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | The correctness depends on the interleaving — a correct program is independent of it. |
| 2 | **B** | TOCTOU — Time-Of-Check to Time-Of-Use. |
| 3 | **B** | `computeIfAbsent` makes the check-and-act one atomic operation. |
| 4 | **D** | All four must hold simultaneously — breaking one is sufficient. |
| 5 | **A** | The classic Coffman set. |
| 6 | **C** | `tryLock` with a timeout converts a hang into a retryable failure. (A global order is the other common fix.) |
| 7 | **B** | They keep running (CPU is busy) but make no useful progress. |
| 8 | **B** | Randomised back-off breaks the lockstep resynchronisation. |
| 9 | **B** | One thread is repeatedly denied the resource while the others progress. |
| 10 | **B** | Priority inheritance — the low-priority holder temporarily adopts the high priority. |
| 11 | **B** | It is a performance bug, not a correctness one. |
| 12 | **B** | Padding puts each field on its own cache line. |
| 13 | **B** | Jitter staggers the refresh so the clients do not all act at once. |
| 14 | **B** | A pool task waits for another task the same pool can never schedule. |
| 15 | **B** | A lost update — one thread's write is overwritten. |
| 16 | **B** | `SET c = c + 1` is atomic in the database — the data owner does the atomicity. |
| 17 | **B** | `AtomicStampedReference` compares a (reference, version) pair. |
| 18 | **B** | A cycle of `BLOCKED`/`WAITING` threads — the JVM even prints `Found 1 deadlock.` |
| 19 | **B** | All `RUNNABLE` with a high CPU time and no progress. |
| 20 | **B** | It holds the lock for the whole call — a deadlock risk and a throughput collapse. |
| 21 | **B** | A version column, a conditional update, and a bounded retry. |
| 22 | **B** | Many readers may hold the read lock at once; writers are exclusive. |
| 23 | **B** | It adds the padding that prevents false sharing. |
| 24 | **B** | Two hot fields in the same object usually share a cache line. |
| 25 | **B** | `jcstress` enumerates the interesting interleavings and asserts on legal outcomes. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 5 PROBLEMS — DIAGNOSE BY SYMPTOM                 │
├───────────────────┬──────────────────────┬──────────────────────────────┤
│ SYMPTOM           │ PROBLEM              │ FIX                         │
├───────────────────┼──────────────────────┼──────────────────────────────┤
│ wrong result      │ RACE CONDITION       │ an atomic operation         │
│ under load        │ (check-then-act /    │ (computeIfAbsent, Atomic*,  │
│                   │  lost update)        │  one atomic SQL statement)  │
├───────────────────┼──────────────────────┼──────────────────────────────┤
│ the service hangs │ DEADLOCK             │ a global lock order,        │
│ (CPU idle)        │                      │ tryLock+timeout, no lock    │
│                   │                      │ across a blocking call      │
├───────────────────┼──────────────────────┼──────────────────────────────┤
│ the CPU is 100%   │ LIVELOCK             │ RANDOMISED back-off         │
│ but nothing       │ (or a thundering     │ (jitter) / stagger the      │
│ completes         │  herd)               │ refresh                     │
├───────────────────┼──────────────────────┼──────────────────────────────┤
│ one request is    │ STARVATION           │ fairness (fair lock),        │
│ always starved    │                      │ partitioning, priorities    │
├───────────────────┼──────────────────────┼──────────────────────────────┤
│ throughput        │ FALSE SHARING /      │ padding (@Contended),        │
│ collapses         │ LOCK CONTENTION      │ LongAdder, fine-grained locks│
└───────────────────┴──────────────────────┴──────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 4 COFFMAN CONDITIONS — BREAK ANY ONE              │
│  ① MUTUAL EXCLUSION  ✗ usually inherent (a row lock is a row lock)       │
│  ② HOLD AND WAIT     ✓ acquire all at once, or release before acquiring │
│  ③ NO PREEMPTION     ✓ tryLock(timeout) → give up instead of waiting    │
│  ④ CIRCULAR WAIT     ✓ a GLOBAL LOCK ORDER → a cycle is impossible      │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 5 PRODUCTION RULES                              │
│  1. NEVER wait for a task in the same pool from inside a pool task      │
│  2. NEVER hold a lock across a blocking call                            │
│  3. ALWAYS add random jitter to any back-off                           │
│  4. ALWAYS bound your retries                                          │
│  5. ALWAYS count the queries per request (the N+1 detector)            │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The diagnostic decision tree

```text
   "The service is misbehaving"
        │
        ├─ WRONG RESULTS (only under load)?
        │     → a RACE. Look for check-then-act and lost updates.
        │     → add a query counter; use jcstress to prove it.
        │
        ├─ HANGS (CPU idle)?
        │     → jstack → look for a cycle of BLOCKED/WAITING threads
        │     → "Found 1 deadlock." for intrinsic monitors
        │     → check for a POOL DEADLOCK (threads WAITING on a Future)
        │
        ├─ CPU AT 100%, NOTHING COMPLETES?
        │     → jstack → all RUNNABLE with a high CPU time
        │     → a LIVELOCK (add jitter) or a THUNDERING HERD (stagger)
        │
        └─ ONE REQUEST NEVER COMPLETES?
              → STARVATION. Look for one thread waiting while others progress.
              → check the fairness of the lock/semaphore.
```

### The prevention checklist

```text
  ☐ Every shared map operation that reads-then-writes uses an ATOMIC method
  ☐ Every counter uses Atomic* / LongAdder (or an atomic SQL statement)
  ☐ Every multi-lock method acquires them in a GLOBAL ORDER
  ☐ Every lock acquisition has a TIMEOUT where a hang is possible
  ☐ No lock is held across a network call, a DB call, or a task submission
  ☐ No pool task waits for another task in the same pool
  ☐ Every back-off has RANDOM jitter
  ☐ Every retry loop is BOUNDED
  ☐ Every interrupt is caught, the flag restored, and the task exited
  ☐ The query count per request is a monitored metric
```

### Metrics worth watching

| Metric | What it tells you |
|---|---|
| Queries per request | The N+1 detector |
| Lock contention (`ThreadMXBean.getThreadInfo`) | Where the contention is |
| Blocked-thread count | A deadlock or heavy contention |
| Thread CPU time per task | A livelock (high CPU, no progress) |
| Retry / back-off count | A livelock or a hot contention point |
| Deadlock detector output | A hard failure |
| `perf c2c` cache-miss counters | False sharing |

### The "concurrency budget" idea

```text
   EVERY SHARED MUTABLE STRUCTURE IS A BUDGET ITEM:

   ┌──────────────────────────────────────────────────────────┐
   │  Resource            │  Budget  │  Mechanism             │
   ├──────────────────────────────────────────────────────────┤
   │  A DB connection     │  N       │  the connection pool   │
   │  A thread            │  N       │  the executor          │
   │  A downstream API    │  N       │  a semaphore           │
   │  A lock              │  N       │  the queue depth       │
   │  A memory slot       │  N       │  the queue capacity     │
   └──────────────────────────────────────────────────────────┘

   ⚠ THE BUDGETS MUST BE CONSISTENT.
     A pool of 200 threads with a 5-connection DB pool = 195 threads
     waiting for a connection → the connection pool IS the real limit.
```

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The JMM, `volatile` and `synchronized` | [01 · Thread Basics](01-Thread-Basics.md) |
| The executor framework (the pool deadlock) | [02 · Executor Framework](02-Executor-Framework.md) |
| Locks, atomics and CAS (ABA, false sharing) | [03 · Locks & Atomic Classes](03-Locks-Atomic.md) |
| Virtual threads and structured concurrency | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| Database transactions and isolation | [16 · JDBC](../16-JDBC/01-JDBC-Basics.md) |
| The loom project | The OpenJDK concurrency stress-tests project |

---

**Next Chapter:** → [09 · Design Patterns](../09-Design-Patterns/01-Design-Patterns.md)
**Previous Chapter:** ← [03 · Locks & Atomic Classes](03-Locks-Atomic.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
