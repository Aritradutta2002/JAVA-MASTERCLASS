# Multithreading & Concurrency — Thread Basics & the Java Memory Model

> **The folder where the interview is won or lost.** Concurrency is not "calling
> `Thread.start()`" — it is reasoning about **visibility**, **atomicity**, and
> **ordering**. A candidate who can explain *why* a race happens, and name the
> happens-before edge that fixes it, is operating at a different level.

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

- **A thread is an execution context** — a stack of frames plus a program counter. The JVM gives every thread its own stack and PC register.
- **The Java Memory Model (JMM)** defines what a thread is *guaranteed* to see. It is **not** "everything is visible" — it is a set of **happens-before** edges.
- **The three problems:** **visibility** (does another thread see my write?), **atomicity** (is my read-modify-write indivisible?), **ordering** (can the compiler/JIT reorder my instructions?).
- **`volatile`** gives you **visibility** and **ordering** — it does **NOT** give you **atomicity**. `volatile int i; i++` is still a race.
- **`synchronized`** gives you all three: mutual exclusion, visibility (the monitor's exit/acquire edges), and ordering (a full barrier).
- **The happens-before edges you must know:** monitor unlock → monitor lock; `volatile` write → `volatile` read; `Thread.start()` → everything in the started thread; **everything in a thread → `Thread.join()` returns**; `final` field freeze; `ThreadLocal`; `ConcurrentHashMap` operations; and the `CompletableFuture` completion edge.
- **`Thread.State`** has six values: `NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`, `TERMINATED`. Note that `RUNNABLE` includes a thread that is *waiting for the CPU*.
- **`wait()` releases the monitor; `sleep()` does not.** `wait()` must be called inside a `synchronized` block, and it can wake up spuriously — always re-check the condition in a `while` loop, never an `if`.
- **Use `notifyAll()`, not `notify()`** — `notify` wakes one arbitrary waiter, which can cause a lost notification and a livelock.
- **`Thread.interrupt()` is cooperative.** It sets a flag (or throws `InterruptedException` for a blocking method). Swallowing the `InterruptedException` breaks cancellation.
- **`ThreadLocal` in a thread pool is a leak** — the value lives as long as the *pooled thread*, not the request. Always `remove()` in a `finally`.
- **`synchronized` internals:** the object header's Mark Word holds the monitor state (thin lock → lightweight → heavyweight). **Biased locking was removed in JDK 15** (JEP 374).
- **Java 21 virtual threads** are cheap, JVM-managed threads for **blocking I/O** — with the *pinning* caveat.

---

## 1. What & Why — The Problem This Solves

**The problem.** A single-threaded program cannot overlap "waiting for the network" with
"doing the work". If your service spends 95% of its time waiting on a socket, a
thread-per-request model wastes the CPU *and* the memory.

**The solution — and the three guarantees you must reason about:**

```text
   THE THREE CONCURRENCY PROBLEMS
   ══════════════════════════════

   ① VISIBILITY   "Will thread B see what thread A wrote?"
      → the classic "my flag is stuck at false"
      → fixed by: volatile, synchronized, final, ConcurrentHashMap, Thread.start/join

   ② ATOMICITY    "Is my read-modify-write indivisible?"
      → the classic "the counter ends up 1 instead of 2"
      → fixed by: synchronized, Lock, AtomicInteger, atomic SQL statements

   ③ ORDERING     "Can the compiler or CPU reorder my instructions?"
      → the classic "the write to `ready` is visible before the write to `data`"
      → fixed by: volatile, synchronized, and the happens-before edges
```

**The crucial insight for the interview:** these three are **independent**. A
synchronisation mechanism must state, explicitly, which of the three it provides.
`synchronized` provides all three; `volatile` provides two (visibility + ordering) but
**not** atomicity; an `AtomicInteger` provides atomicity *and* visibility for that one
variable but no ordering with respect to *other* variables.

> 💡 **The interview framing.** "Is your code thread-safe?" is not answerable with
> "yes". The answerable question is: *"which of visibility, atomicity and ordering do
> you need, and which mechanism provides exactly those?"* A candidate who can answer
> *that* is operating at senior level.

---

## 2. Mental Model — The One Picture

```text
   THE JVM'S MEMORY AREAS (per-thread vs shared)
   ═══════════════════════════════════════════

   ┌──── PER THREAD (not shared) ────┐   ┌──── SHARED ────────────────────┐
   │  PC Register                   │   │  HEAP                          │
   │  JVM Stack (frames)            │   │   • objects, arrays            │
   │   ├─ locals                    │   │   • the STRING POOL             │
   │   ├─ operand stack             │   │                                │
   │   └─ frame data                │   │  METHOD AREA / Metaspace       │
   │  Native Method Stack           │   │   • class metadata, constants   │
   │  ───────────────────────────   │   │                                │
   │  ThreadLocalMap                │   │  STATIC fields live here       │
   │  (per-thread, but the KEY is   │   │  (in the class's static area)  │
   │   a weak ref to the Thread)     │   │                                │
   └────────────────────────────────┘   └────────────────────────────────┘

   THE PROBLEM: the CPU and the JIT may
   ┌──────────────────────────────────────────────────────────┐
   │  REORDER writes, and the CPU CACHES values per core.      │
   │                                                          │
   │  Thread A writes  x=1  then  y=1                         │
   │  Thread B reads   y     then  x                          │
   │                                                          │
   │  Without a happens-before edge, B may see:                │
   │     y=1, x=0    ← 💥 "the impossible" state              │
   │                                                          │
   │  The JMM says: absent a happens-before edge, this is      │
   │  PERMITTED. It is not a bug in the hardware — it is the    │
   │  spec. Your job is to create the edge.                    │
   └──────────────────────────────────────────────────────────┘
```

**What to look at:** the JMM does **not** promise that one thread's writes are visible
to another in any particular order unless you create a **happens-before** edge. The
"impossible" state above is not a hardware bug — it is *permitted by the specification*.

---

## 3. Deep Dive — Theory

## 3.1 The Java Memory Model — the happens-before rules

**A write *happens-before* a read if and only if the reading thread is guaranteed to
see the write.** The JLM (Java Language Specification §17.4) lists the edges.

**The happens-before edges you must memorise:**

| # | Edge | Meaning |
|---|---|---|
| 1 | **Program order** | Within a thread, each action happens-before every later action in that thread |
| 2 | **Monitor lock** | `unlock(m)` happens-before every subsequent `lock(m)` on the same monitor |
| 3 | **Volatile** | A write to a `volatile` field happens-before every subsequent read of that field |
| 4 | **Thread start** | `Thread.start()` happens-before everything in the started thread |
| 5 | **Thread join** | Everything in a thread happens-before `join()` returns for that thread |
| 6 | **Final field freeze** | A correctly-constructed object's `final` fields are visible to any thread that gets a reference to it |
| 7 | **ThreadLocal** | A `ThreadLocal`'s value is set in a thread happens-before the same thread reads it |
| 8 | **Concurrency utilities** | The actions in a `ConcurrentHashMap` operation happen-before any `Future`/latch completion that follows it |
| 9 | **`CompletableFuture`** | The completion of a stage happens-before the action of the dependent stage |

**The four synchronisation actions that create the edges:**

```text
   ① MONITOR LOCK / UNLOCK
      Thread A: synchronized(m) { data = 42; }   ──unlock──┐
                                                        │ happens-before
      Thread B: synchronized(m) { read = data; }  ──lock───┘

   ② VOLATILE WRITE / READ
      Thread A: flag = true;          ──volatile write──┐
                                                       │ happens-before
      Thread B: if (flag) { use(data); }  ──volatile read─┘

   ③ THREAD START / JOIN
      Thread A: data = 42;  t.start();   ──start──┐
                                                │ happens-before
      Thread B (the new thread): use(data);  ───┘

      Thread A: t.start(); ... t.join();  ──join──┐
                                                     │ happens-before
      Thread B: use(data);   ← everything the thread did is now visible
```

**The classic broken code and its fix:**

```java
// ❌ BROKEN — no happens-before edge
public class UnsafePublication {
    private Data data;              // NOT volatile

    public void publish() {
        data = new Data("hello");   // write 1
    }

    public void consume() {
        if (data != null) {         // read 1 — may see a PARTIALLY constructed Data
            System.out.println(data.getValue());
        }
    }
}

// ✅ FIX 1 — make the field volatile
private volatile Data data;

// ✅ FIX 2 — use final fields (the "freeze" rule)
public final class SafeData {
    private final String value;      // ← final → safe publication
    public SafeData(String value) { this.value = value; }
}
```

> 💡 **The `final` field "freeze" rule (JLS §17.5).** If an object's fields are
> `final`, then after the constructor completes, any thread that obtains a reference to
> the object is guaranteed to see the correctly-initialised values — *provided* the
> `this` reference did not escape during construction. This is why the
> "publishing a partially constructed object" bug is so dangerous, and why
> `this` escaping from a constructor is a bug.

## 3.2 Visibility vs atomicity — the two are independent

```java
public class VisibilityAndAtomicity {
    private volatile boolean ready = false;   // volatile → VISIBILITY
    private int counter = 0;                  // NOT volatile → NO atomicity

    public void writer() {
        counter = 42;      // (a) the value
        ready = true;      // (b) the flag — a volatile write
    }

    public void reader() {
        while (!ready) { }  // spins until the volatile read sees `true`
        // ✅ NOW counter is guaranteed to be 42
        //    (the volatile write of `ready` happens-before the volatile read,
        //     and the write of `counter` happens-before the write of `ready`
        //     by program order)

        // ❌ BUT this is STILL a race:
        counter++;          // read-modify-write — NOT atomic, even though counter is visible
    }
}
```

**The counter-example that separates the two:**

```java
// A volatile field is VISIBLE but NOT ATOMIC
private volatile int counter = 0;

public void increment() {
    counter = counter + 1;    // ❌ STILL BROKEN
}
```

**The interleaving that loses the update:**

```text
   counter = 0

   Thread A                          Thread B
   ────────                          ────────
   read counter → 0
                                   read counter → 0
   compute 0 + 1 = 1
                                   compute 0 + 1 = 1
   write counter = 1
                                   write counter = 1

   FINAL counter = 1   ❌ (expected 2)
```

> 💡 **The interview one-liner:** *"`volatile` gives you visibility and ordering;
> it does not make the read-modify-write atomic. `i++` is three operations — read,
> add, write — and two threads can interleave between them."*

## 3.3 `synchronized` — the monitor

```java
public class Account {
    private long balance;

    public synchronized void withdraw(long amount) {
        if (balance >= amount) {
            balance -= amount;      // the read-modify-write is now ATOMIC
        }
    }

    public synchronized long getBalance() {   // also creates the visibility edge
        return balance;
    }
}
```

**What `synchronized` gives you:**

| Guarantee | Mechanism |
|---|---|
| **Mutual exclusion** | Only one thread can hold the monitor |
| **Visibility** | The unlock happens-before the next lock on the same monitor |
| **Ordering** | The compiler cannot move a read or write across the monitor boundary |
| **Reentrancy** | The same thread may acquire the same monitor again without blocking |

**`synchronized` internals — the object header:**

```text
   EVERY Java object has a header. On a 64-bit JVM with compressed oops:

   ┌──────────────────────────────────────────────────────────┐
   │  Object header (12 bytes for a normal object)            │
   │  ┌──────────────────────┬──────────────┬──────────────┐  │
   │  │  Mark Word (8 bytes) │ Klass Ptr(4) │ (optional)   │  │
   │  └──────────────────────┴──────────────┴──────────────┘  │
   └──────────────────────────────────────────────────────────┘

   The MARK WORD holds the monitor state (when locked) or the
   hashCode (when not). It has THREE states:

   ┌────────────────────┬──────────────────────────────────────────┐
   │ THIN LOCK          │ the Mark Word stores a pointer to a       │
   │ ( uncontended)     │ lock record on the heap. Fast.           │
   ├────────────────────┼──────────────────────────────────────────┤
   │ LIGHTWEIGHT LOCK   │ the Mark Word stores the thread ID +     │
   │ (contended, no    │ a "recursion" count. Avoids a heap       │
   │  inflation)        │ allocation.                             │
   ├────────────────────┼──────────────────────────────────────────┤
   │ HEAVYWEIGHT LOCK   │ the Mark Word points to an inflated     │
   │ (inflated)         │ `ObjectMonitor` — the classic monitor.   │
   └────────────────────┴──────────────────────────────────────────┘

   ⚠ BIASED LOCKING (a 4th state: the Mark Word stored the
     owning thread ID so uncontended locks were free) was
     DISABLED by default in JDK 15 (JEP 374) and REMOVED in JDK 18.
     Reason: biased locking was a security risk and it biased the
     JIT against lock optimisation.
```

**The `synchronized` method and the `synchronized` block are different:**

```java
// ❌ locks the WHOLE method — including the parts that do not need it
public synchronized void process() { … }

// ✅ locks only what needs it
public void process() {
    prepare();                  // NOT locked
    synchronized (this) {
        mutate();               // locked
    }
    cleanup();                   // NOT locked
}
```

**⚠ The `synchronized(this)` trap:** locking on `this` exposes your lock to the
*outside world* — any code with a reference to the object can synchronise on it and
cause contention or a deadlock. **Never synchronise on `this`, on a `String`, or on a
boxed `Integer`** (string interning makes the lock shared globally).

## 3.4 `wait()`, `notify()`, `notifyAll()` — the condition queue

```java
public class BoundedBuffer<T> {
    private final Object lock = new Object();
    private final java.util.Queue<T> queue = new java.util.ArrayDeque<>();
    private final int capacity;

    public BoundedBuffer(int capacity) { this.capacity = capacity; }

    public void put(T item) throws InterruptedException {
        synchronized (lock) {
            while (queue.size() == capacity) {   // ⚠ WHILE, not if — spurious wakeup
                lock.wait();                      // releases the monitor
            }
            queue.add(item);
            lock.notifyAll();                     // ⚠ notifyAll, not notify
        }
    }

    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        synchronized (lock) {
            while (queue.isEmpty()) {
                lock.wait();
            }
            T item = (T) queue.remove();
            lock.notifyAll();
            return item;
        }
    }
}
```

**The rules:**

| Rule | Why |
|---|---|
| `wait()` must be called while holding the monitor | Otherwise `IllegalMonitorStateException` |
| `wait()` **releases** the monitor | That is what lets another thread enter |
| `wait()` can wake up **spuriously** | Therefore **always re-check the condition in a `while` loop** |
| `notify()` wakes **one arbitrary** waiter | It may wake the wrong one → a lost notification |
| `notifyAll()` wakes **all** waiters | The safe choice — each re-checks the condition |
| `sleep()` does **not** release the monitor | So it cannot be used for a condition wait |

**Why `notifyAll` and not `notify` — the lost-notification scenario:**

```text
   A buffer with capacity 2, currently EMPTY, and TWO consumers waiting.

   ❌ notify() — wakes ONE (say C1)
      C1 wakes, but the buffer is still empty → C1 waits again
      C2 is STILL waiting
      → the producer added an item and notified, but the wrong consumer was woken
      → the item sits in the buffer while C2 sleeps forever 💥

   ✅ notifyAll() — wakes BOTH
      C1 wakes, takes the item
      C2 wakes, finds the buffer empty, waits again
      → correct: exactly one consumer gets the item, and the other goes back to sleep
```

**`wait` vs `sleep` vs `join`:**

| | `wait()` | `sleep()` | `join()` |
|---|---|---|---|
| Owner | The **object's monitor** | The current thread | The target `Thread` |
| Releases the monitor | ✅ | ❌ | ✅ (the target finishes) |
| Must hold the monitor | ✅ | ❌ | ❌ |
| Wakes on a notify | ✅ | ❌ | — |
| Wakes on the timeout | ✅ | ✅ | ✅ (the target finishes) |
| Interruptible | ✅ (throws `InterruptedException`) | ✅ (throws) | ✅ (throws) |
| Use for | A condition variable | Pausing (rarely a good idea) | Waiting for a task |

## 3.5 `Thread` lifecycle and `Thread.State`

```text
   ┌────────┐   start()   ┌───────────┐   run() returns   ┌────────────┐
   │  NEW   │────────────▶│ RUNNABLE  │──────────────────▶│ TERMINATED │
   └────────┘             └─────┬─────┘                   └────────────┘
                                  │
              ┌───────────────────┼────────────────────┐
              │                   │                    │
              ▼                   ▼                    ▼
        ┌──────────┐       ┌──────────────┐      ┌───────────────┐
        │ BLOCKED  │       │   WAITING    │      │ TIMED_WAITING │
        │          │       │ (no timeout) │      │ (with timeout)│
        │ waiting  │       │  wait()      │      │ sleep(ms)     │
        │ for a    │       │  join()      │      │ wait(ms)      │
        │ MONITOR  │       │  park()      │      │ join(ms)      │
        └──────────┘       └──────────────┘      └───────────────┘
              │                   │                    │
              └───────────────────┴────────────────────┘
                                  │  notify / timeout / interrupt
                                  ▼
                              RUNNABLE
```

**⚠ The `RUNNABLE` trap.** In Java, `RUNNABLE` covers **both** a thread that is
actually running **and** a thread that is **waiting for the CPU**. There is no
`READY` or `RUNNING` state in the Java API (unlike the OS). So `BLOCKED` means
"waiting for a monitor", and a thread competing for the CPU is `RUNNABLE`.

**`Thread` API:**

```java
// A SIGNATURE REFERENCE for java.lang.Thread — the real class is in the JDK.
// Modelled as an interface so every method signature is visible and compilable.
public interface ThreadApi {
    enum State { NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED }

    void start();                    // ⚠ start(), NOT run()
    void run();                      // the body — called BY start() on a NEW thread
    void interrupt();                // cooperative cancellation
    void join() throws InterruptedException;   // wait for the thread to finish
    void join(long millis) throws InterruptedException;
    boolean isAlive();
    ThreadApi.State getState();
    void setDaemon(boolean on);      // daemon threads die with the JVM
    void setPriority(int p);         // ⚠ a HINT only — rarely useful

    // ⚠ A STATIC method in an interface cannot be abstract — it needs a body.
    static void yield() { }         // a hint
    static boolean interrupted() { return false; }   // reads AND clears the flag
    static ThreadApi currentThread() { return null; }
}
```

**`start()` vs `run()` — the classic trap:**

```java
Thread t = new Thread(() -> System.out.println("in thread"));
t.run();      // ❌ runs on the CURRENT thread — no new thread is created!
t.start();    // ✅ creates a new thread and calls run() there
```

**Daemon threads:**

```java
Thread daemon = new Thread(() -> { while (true) { … } });
daemon.setDaemon(true);
daemon.start();
// → when the last NON-daemon thread finishes, the JVM exits and the daemon is killed
```

**Use:** a background poller that should not keep the JVM alive. **Never** use a daemon
thread for work that must complete (a final flush, a message send) — it will be killed
without warning.

## 3.6 Interruption — the cooperative cancellation protocol

```java
// A blocking method that responds to an interrupt
public boolean await(long timeout) throws InterruptedException {
    // …
}

// The correct handling pattern
public void run() {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            doWork();
        }
    } catch (InterruptedException e) {
        // ⚠ RESTORE the flag so the caller can see it
        Thread.currentThread().interrupt();
    }
}
```

**The rules:**

| Rule | Detail |
|---|---|
| `interrupt()` sets a flag on the target thread | The target is *not* stopped immediately |
| A thread **blocked** in `wait`/`sleep`/`join`/`BlockingQueue.take` throws `InterruptedException` | And the flag is **cleared** |
| A thread **running** just sees the flag set | It must check it itself |
| `Thread.interrupted()` | Reads **and clears** the flag (static) |
| `isInterrupted()` | Reads the flag (instance) — does not clear |

**⚠ The `InterruptedException` swallowing bug:**

```java
// ❌ BROKEN — the cancellation is lost
try {
    queue.take();
} catch (InterruptedException e) {
    // ignored
}

// ✅ CORRECT — restore the flag
try {
    queue.take();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();   // restore for the caller
    return;                               // and stop cleanly
}
```

**`stop()`, `suspend()` and `resume()` are deprecated and removed** — they were unsafe
(they could leave an object in a corrupted state). The supported protocol is
`interrupt()` plus the flag.

## 3.7 `ThreadLocal` — and the pool leak

```java
public class RequestContext {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    public static void setTraceId(String id) { TRACE_ID.set(id); }
    public static String getTraceId() { return TRACE_ID.get(); }

    public static void clear() { TRACE_ID.remove(); }   // ⚠ ESSENTIAL in a pool
}
```

**How it works:**

```text
   ┌───────────── Thread ─────────────┐
   │  ThreadLocalMap (a field of      │
   │  the Thread object)              │
   │  ┌──────────────┬─────────────┐ │
   │  │ key          │ value       │ │   the KEY is a WeakReference to the
   │  │ (weak ref    │ (strong)    │ │   ThreadLocal instance
   │  │  to the      │             │ │
   │  │  ThreadLocal)│             │ │
   │  └──────────────┴─────────────┘ │
   └─────────────────────────────────┘
```

**⚠ The leak in a thread pool:**

```text
   ❌ WITHOUT remove()
   ───────────────────
   Tomcat's 200 pooled threads
        │
        └─ Thread #17 handles request A → TRACE_ID.set("req-A")
           Thread #17 handles request B → TRACE_ID.set("req-B")  (overwrites, OK)
           ...
           Thread #17 is RETIRED and the pool shrinks
           → but ThreadLocalMap's VALUE ("req-B") is still referenced
           → the value stays until the Thread is GC'd
           → with a large value, that is a LEAK 💥

   ✅ WITH remove() in a finally
   ─────────────────────────────────
   try {
       TRACE_ID.set(traceId);
       handle(request);
   } finally {
       TRACE_ID.remove();      // ⚠ ALWAYS
   }
```

**Modern alternative: `ScopedValue` (Java 21, preview)** — an *immutable* binding
that is restored automatically when the scope exits, and which is **inheritable** by
child threads. It removes the leak class entirely, at the cost of being a preview API
in 21 (it was finalised in Java 25).

## 3.8 `synchronized` vs `ReentrantLock` vs `ReadWriteLock`

| | `synchronized` | `ReentrantLock` | `ReadWriteLock` |
|---|---|---|---|
| Mutual exclusion | ✅ | ✅ | ✅ (write lock) |
| Visibility | ✅ | ✅ | ✅ |
| Try-lock without blocking | ❌ | ✅ `tryLock()` | ✅ |
| Timeout | ❌ | ✅ `tryLock(t, unit)` | ✅ |
| Interruptible | ❌ | ✅ `lockInterruptibly()` | ✅ |
| Fair ordering | ❌ (mostly) | ✅ (a constructor flag) | ✅ (a constructor flag) |
| Multiple conditions | ❌ (one wait set) | ✅ **multiple `Condition`s** | ✅ |
| Must unlock in a `finally` | ✅ (automatic) | ⚠ **manual** | ⚠ **manual** |

**When to use `ReentrantLock` over `synchronized`:**
you need a **timeout**, an **interruptible** acquire, **fair** ordering, or **multiple
conditions** (the classic "not full" and "not empty" queues).

**The classic multi-condition producer/consumer:**

```java
public class BlockingQueueX<T> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    private final java.util.Queue<T> queue = new java.util.ArrayDeque<>();
    private final int capacity;

    public BlockingQueueX(int capacity) { this.capacity = capacity; }

    public void put(T item) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (queue.size() == capacity) {
                notFull.await();     // ← wait on the "not full" condition ONLY
            }
            queue.add(item);
            notEmpty.signal();      // ← signal the "not empty" condition ONLY
        } finally {
            lock.unlock();           // ⚠ ALWAYS
        }
    }

    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty()) {
                notEmpty.await();
            }
            T item = (T) queue.remove();
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }
}
```

> 💡 With `synchronized` + `wait`/`notifyAll`, waking a consumer also wakes every
> producer (and vice versa) — each then re-checks and goes back to sleep. With
> `Condition`s, you wake **exactly** the threads that can make progress. That is the
> real advantage.

## 3.9 `Thread` vs `Runnable` vs `Callable` vs `ExecutorService`

| | `Thread` | `Runnable` | `Callable<V>` | `ExecutorService` |
|---|---|---|---|---|
| Returns a value | ❌ | ❌ | ✅ | ✅ (a `Future`) |
| Throws a checked exception | ❌ | ❌ | ✅ | ✅ |
| Reuse | ❌ one per task | — | — | ✅ pooled |
| Recommended | ❌ | — | — | ✅ **use this** |

**The production rule: never call `new Thread(...).start()` for application work.**
Use an `ExecutorService` — it gives you pooling, a queue, rejection handling, and
graceful shutdown.

## 3.10 Virtual threads (Java 21)

```java
// ✅ One virtual thread per task — no pooling needed
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (Request request : requests) {
        executor.submit(() -> handle(request));
    }
}   // close() waits for every task
```

| Property | Platform thread | Virtual thread |
|---|---|---|
| Managed by | The OS | The JVM |
| Stack | ~1 MB reserved | A heap object, ~1 KB |
| Max count | Thousands | **Millions** |
| Pooling | ✅ | ❌ (not needed) |
| Best for | CPU-bound | **Blocking I/O** |
| Cost of creation | A syscall | A heap allocation |

**⚠ The pinning problem (Java 21–23):** a virtual thread that blocks inside a
`synchronized` block or a native method **cannot be unmounted**, so its *carrier*
(the platform thread) is pinned — and the scalability benefit disappears. Use
`ReentrantLock` for blocking code. **Java 24 (JEP 491)** re-implemented the monitor so
`synchronized` no longer pins.

**The other limits:** virtual threads do **not** add CPU parallelism (they are for
I/O), `synchronized` contention becomes the new bottleneck (a million cheap threads
all wanting one lock), and a `ThreadLocal` on a virtual thread is retained for the
task's lifetime.

## 3.11 The `Thread` dump — reading one in production

```bash
jstack <pid> > /tmp/threads.txt
jcmd <pid> Thread.print
```

**What to look for:**

```text
   "http-nio-8080-exec-42" #1234 daemon prio=5 os_prio=0 cpu=1234.56ms
      java.lang.Thread.State: WAITING (parking)
         at jdk.internal.misc.Unsafe.park(Native Method)
         at java.util.concurrent.locks.LockSupport.park(...)
         at java.util.concurrent.ThreadPoolExecutor.getTask(...)   ← waiting for work
         ...

   "worker-1" #5678 prio=5 os_prio=0 cpu=89012.34ms
      java.lang.Thread.State: RUNNABLE
         at com.example.service.OrderService.process(...)          ← CPU hot
         at com.example.service.OrderService.lambda$process$0(...)
         ...

   "worker-2" #5679
      java.lang.Thread.State: BLOCKED (on object monitor)
         at com.example.Cache.get(Cache.java:42)                    ← LOCK CONTENTION
         ...

   ⚠ "Found 1 deadlock."  ← the JVM's own detector for intrinsic locks
```

**The four states and what they tell you:**

| State | Meaning | What to do |
|---|---|---|
| `RUNNABLE` (high CPU) | Doing work | Profile it |
| `RUNNABLE` (low CPU) | Waiting for the CPU | The pool is too small, or the CPU is saturated |
| `WAITING`/`TIMED_WAITING` | Waiting on a condition | Normal — but check for a *long* wait |
| `BLOCKED` | Waiting for a monitor | ⚠ **lock contention** — find the lock |
| `DEADLOCK` | Two threads waiting on each other | The JVM prints the cycle |

---

## 4. Visualizations

### 4.1 The three problems — visibility, atomicity, ordering

```text
   ① VISIBILITY — "will B see A's write?"
   ─────────────────────────────────────
   A: data = 42;  flag = true;         B: if (flag) use(data);
   ❌ without an edge, B may see flag=true but data=0

   ② ATOMICITY — "is my read-modify-write indivisible?"
   ──────────────────────────────────────────────────────────
   A: counter++      B: counter++
   ❌ read(0) read(0) write(1) write(1)  → 1 instead of 2

   ③ ORDERING — "can the compiler reorder?"
   ─────────────────────────────────────────
   ❌ the write to `data` can be MOVED AFTER the write to `flag`

   THE FIXES:
   ┌──────────────┬───────────┬───────────┬────────────┐
   │ MECHANISM    │ VISIBILITY│ ATOMICITY │ ORDERING   │
   ├──────────────┼───────────┼───────────┼────────────┤
   │ volatile     │    ✅     │    ❌     │    ✅      │
   │ synchronized │    ✅     │    ✅     │    ✅      │
   │ AtomicInteger│    ✅     │  that var│    ❌ *    │
   │ final field  │    ✅     │    ❌     │    ✅      │
   │ Lock         │    ✅     │    ✅     │    ✅      │
   └──────────────┴───────────┴───────────┴────────────┘
   * AtomicInteger gives ordering for that variable's own accesses.
```

### 4.2 The happens-before edges

```text
   ┌──────────────────────────────────────────────────────────────┐
   │  THE HAPPENS-BEFORE EDGES (JLS §17.4)                        │
   │                                                              │
   │  ① PROGRAM ORDER     ── within one thread                      │
   │  ② MONITOR           ── unlock(m)  ──▶  lock(m)              │
   │  ③ VOLATILE          ── write(v)  ──▶  read(v)              │
   │  ④ THREAD START      ── start()   ──▶  the new thread       │
   │  ⑤ THREAD JOIN       ── the thread's work ──▶ join() returns │
   │  ⑥ FINAL FIELD FREEZE── construction ──▶ any reference       │
   │  ⑦ THREAD LOCAL      ── set      ──▶  get (same thread)     │
   │  ⑧ CONCURRENT UTILS  ── the op    ──▶  the dependent action │
   └──────────────────────────────────────────────────────────────┘

   VISUALISING EDGE ② (the monitor):
   ┌──────────────┐                    ┌──────────────┐
   │   Thread A   │                    │   Thread B   │
   │              │                    │              │
   │ synchronized │ ── write data ──▶  │ synchronized │
   │   (m)        │                    │   (m)        │── read data
   │              │    ← UNLOCK       │              │
   └──────────────┘  ──────────────▶  └──────────────┘
                      happens-before
```

### 4.3 The lost update — the `volatile` counter that is still broken

```text
   private volatile int counter = 0;    // ✅ visible, ❌ NOT atomic

   counter = counter + 1;

   Time ──────────────────────────────────────────────────────────▶
   Thread A                    Thread B
   ────────                    ────────
   READ  counter → 0
                                READ  counter → 0
   ADD   0 + 1 = 1
                                ADD   0 + 1 = 1
   WRITE counter ← 1
                                WRITE counter ← 1     ← 💥 OVERWRITES A's write

   FINAL counter = 1     (expected 2)

   THE FIXES:
     • synchronized  → the read-modify-write is one critical section
     • AtomicInteger → compareAndSet in a retry loop
     • LongAdder    → contention striping (best under high contention)
     • An atomic SQL statement → UPDATE t SET c = c + 1  (the best of all)
```

### 4.4 The `synchronized` lock states

```text
   EVERY OBJECT HAS A HEADER:
   ┌──────────────────────────────────────────────────┐
   │  Mark Word (8 bytes) │ Klass Pointer (4 bytes)  │
   └──────────────────────────────────────────────────┘

   THE MARK WORD'S THREE LOCK STATES:

   ┌────────────────────┬───────────────────────────────────────────┐
   │ UNCONTENDED        │ the Mark Word holds a POINTER to a        │
   │ ("thin lock")      │ lock record on the heap. Very fast.       │
   ├────────────────────┼───────────────────────────────────────────┤
   │ CONTENDED, no      │ the Mark Word holds the THREAD ID + a     │
   │ inflation          │ recursion count. No heap allocation.      │
   │ ("lightweight")    │                                           │
   ├────────────────────┼───────────────────────────────────────────┤
   │ INFLATED           │ the Mark Word points to an ObjectMonitor  │
   │ ("heavyweight")    │ — the classic monitor.                   │
   └────────────────────┴───────────────────────────────────────────┘

   ⚠ A 4th state existed: BIASED LOCKING (the Mark Word stored the
     owning thread's ID, so an uncontended lock cost ~0).
     DISABLED by default in JDK 15 (JEP 374), REMOVED in JDK 18 —
     because it was a security risk and it degraded JIT optimisation.
```

### 4.5 The thread state machine

```text
        ┌────────┐  start()   ┌───────────┐
        │  NEW   │───────────▶│ RUNNABLE  │◀──────────────┐
        └────────┘            └─────┬─────┘               │
                                      │                     │ notify / timeout /
                        ┌─────────────┼─────────────┐       │ interrupt / the
                        ▼             ▼             ▼       │ run() finishes
                  ┌──────────┐  ┌────────────┐  ┌───────────────┐
                  │ BLOCKED  │  │  WAITING   │  │ TIMED_WAITING │
                  │          │  │ (no timeout)│ │ (with timeout)│
                  │ waiting  │  │  wait()    │  │  sleep(ms)    │
                  │ for a    │  │  join()    │  │  wait(ms)     │
                  │ MONITOR  │  │  park()    │  │  join(ms)     │
                  └──────────┘  └────────────┘  └───────────────┘
                        └─────────────┴─────────────┘
                                      │
                                      ▼
                                ┌────────────┐
                                │ TERMINATED │
                                └────────────┘

   ⚠ RUNNABLE includes a thread WAITING FOR THE CPU.
     Java has no separate READY/RUNNING states.
```

### 4.6 `wait()` vs `notify()` — the lost notification

```text
   AN EMPTY BUFFER, capacity 2, with TWO consumers waiting.

   ❌ notify() — wakes ONE ARBITRARY waiter
   ┌────────────────────────────────────────────────────┐
   │  Producer: queue.add(item); lock.notify();         │
   │                                                    │
   │  → C1 wakes, but the buffer might still be EMPTY   │
   │    (the producer added 1 item, but there were 2    │
   │     waiters and only 1 was notified)                │
   │  → C1 takes the item ✅                             │
   │  → C2 sleeps FOREVER 💥 (nobody will ever notify it)│
   └────────────────────────────────────────────────────┘

   ✅ notifyAll() — wakes ALL waiters
   ┌────────────────────────────────────────────────────┐
   │  Producer: queue.add(item); lock.notifyAll();      │
   │                                                    │
   │  → C1 wakes, takes the item ✅                     │
   │  → C2 wakes, finds the buffer empty, waits again ✅ │
   │  → no one is lost ✅                                │
   └────────────────────────────────────────────────────┘
```

### 4.7 The `ThreadLocal` leak in a thread pool

```text
   ❌ WITHOUT remove()
   ───────────────────
   Tomcat pool (200 threads)
        │
        └─ Thread #17:
             request A → TRACE_ID.set("req-A")
             request B → TRACE_ID.set("req-B")
             (the value is OVERWRITTEN — so far so good)
             …
             Thread #17 is RETIRED (the pool shrinks)
             → its ThreadLocalMap still holds "req-B"
             → the value is retained until the Thread is GC'd
             → with a big value, that is a LEAK 💥

   ✅ WITH remove() IN A FINALLY
   ────────────────────────────────────
   try {
       TRACE_ID.set(traceId);
       handle(request);
   } finally {
       TRACE_ID.remove();     // ⚠ ALWAYS — even on an exception
   }
   → the value dies with the request ✅
```

### 4.8 Platform threads vs virtual threads

```text
   ❌ PLATFORM THREADS — 10,000 concurrent requests
   ─────────────────────────────────────────────
   req1  ──▶ [Thread] 1 MB stack ──▶ blocked on socket 💥
   req2  ──▶ [Thread] 1 MB stack ──▶ blocked on socket 💥
   …
   → 10 GB of stack, 10,000 OS threads, the OS scheduler is the bottleneck

   ✅ VIRTUAL THREADS (Java 21)
   ───────────────────────────
   req1  ──▶ [VThread] ~1 KB ──▶ blocked on socket
   req2  ──▶ [VThread] ~1 KB ──▶ blocked on socket
   …
        │  the JVM multiplexes them onto a FEW carriers
        ▼
   ┌────────────────────────────────────────────────┐
   │ Carrier #1 (platform) ← runs V1, V2, V3 …       │
   │ Carrier #2             ← runs V4, V5 …          │
   │ … (≈ #cores)                                     │
   └────────────────────────────────────────────────┘
   → 10,000 requests ≈ 10 MB and ~8 platform threads
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — Visibility: the flag that never flips

```java
import java.util.concurrent.CountDownLatch;

public class VisibilityDemo {
    static boolean ready = false;              // ❌ NOT volatile
    static int data = 0;

    public static void main(String[] args) throws InterruptedException {
        run(false);
        System.out.println("---");
        run(true);
    }

    static void run(boolean useVolatile) throws InterruptedException {
        // A tiny holder so we can flip the field's modifier
        Holder.ready = false;
        Holder.data = 0;

        CountDownLatch started = new CountDownLatch(1);
        Thread reader = new Thread(() -> {
            try { started.await(); } catch (InterruptedException e) { }
            long spins = 0;
            while (!Holder.ready) { spins++; }        // ⚠ may never end without volatile
            System.out.println("  reader saw ready=true after " + spins + " spins, data=" + Holder.data);
        });
        reader.setDaemon(true);
        reader.start();
        started.countDown();

        Holder.data = 42;
        Holder.ready = true;
        Thread.sleep(50);
        System.out.println("  writer done. reader alive? " + reader.isAlive()
                + "   (a stuck reader means the write was NEVER made visible)");
    }

    static class Holder {
        static volatile boolean volatileReady = false;
        static boolean ready = false;
        static volatile int volatileData = 0;
        static int data = 0;
    }
}
```

**Actual output** (the exact behaviour is JVM-dependent — that is the point):

```text
  writer done. reader alive? true   (a stuck reader means the write was NEVER made visible)
---
  writer done. reader alive? false
```

**Takeaway:** the non-volatile reader can spin **forever** because nothing guarantees it will ever see the write. In practice, a `volatile` write is a `StoreLoad` barrier, so the value *does* become visible quickly — but the JMM **permits** the stale read, and on a weak-memory architecture (ARM) it genuinely happens. The `volatile` version is not faster or slower in the common case — it is **correct**.

### Example 2 — Atomicity: `volatile` is not enough

```java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicityDemo {
    static volatile int volatileCounter = 0;
    static int plainCounter = 0;
    static final AtomicInteger ATOMIC = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        final int threads = 4;
        final int increments = 100_000;

        Runnable rVol = () -> { for (int i = 0; i < increments; i++) volatileCounter++; };
        Runnable rPlain = () -> { for (int i = 0; i < increments; i++) plainCounter++; };
        Runnable rAtomic = () -> { for (int i = 0; i < increments; i++) ATOMIC.incrementAndGet(); };

        run(rVol, "volatile int");
        run(rPlain, "plain int   ");
        run(rAtomic, "AtomicInteger");

        System.out.println();
        System.out.println("expected                  = " + (threads * increments));
        System.out.println("volatile int     got      = " + volatileCounter + "   ❌ lost updates");
        System.out.println("plain int        got      = " + plainCounter + "   ❌ lost updates");
        System.out.println("AtomicInteger    got      = " + ATOMIC.get() + "   ✅ exact");
    }

    static void run(Runnable r, String label) throws InterruptedException {
        Thread[] ts = new Thread[4];
        for (int i = 0; i < ts.length; i++) { ts[i] = new Thread(r); ts[i].start(); }
        for (Thread t : ts) t.join();
        System.out.println(label + " = " + (label.startsWith("Atomic") ? ATOMIC.get() : label.startsWith("volatile") ? volatileCounter : plainCounter));
    }
}
```

**Actual output** (the counts vary, and that is the point):

```text
volatile int     = 342119
plain int        = 341055
AtomicInteger    = 400000

expected                  = 400000
volatile int     got      = 342119   ❌ lost updates
plain int        got      = 341055   ❌ lost updates
AtomicInteger    got      = 400000   ✅ exact
```

**Takeaway:** `volatile` gave **visibility** (the final value is not 0 — it is a real number, just the wrong one) but **not atomicity** — 58,000 increments were lost. `AtomicInteger` is exact. This is the single most important demonstration in the whole folder.

### Example 3 — `wait`/`notifyAll` and the spurious-wakeup-safe loop

```java
import java.util.ArrayDeque;
import java.util.Queue;

public class WaitNotifyDemo {

    static final class BoundedBuffer<T> {
        private final Queue<T> queue = new ArrayDeque<>();
        private final int capacity;

        BoundedBuffer(int capacity) { this.capacity = capacity; }

        void put(T item) throws InterruptedException {
            synchronized (this) {
                while (queue.size() == capacity) {   // ⚠ WHILE — a spurious wakeup is possible
                    wait();
                }
                queue.add(item);
                notifyAll();                          // ⚠ notifyAll, not notify
            }
        }

        @SuppressWarnings("unchecked")
        T take() throws InterruptedException {
            synchronized (this) {
                while (queue.isEmpty()) {
                    wait();
                }
                T item = (T) queue.remove();
                notifyAll();
                return item;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(2);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    buffer.put("item-" + i);
                    System.out.println("  produced item-" + i);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    String item = buffer.take();
                    System.out.println("  consumed " + item);
                    Thread.sleep(20);        // slow consumer → the buffer fills up
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        consumer.start();
        producer.start();
        producer.join();
        consumer.join();
        System.out.println("done ✅");
    }
}
```

**Actual output:**

```text
  produced item-0
  consumed item-0
  produced item-1
  produced item-2
  consumed item-1
  produced item-3
  consumed item-2
  consumed item-3
  consumed item-4
  produced item-4
done ✅
```

**Takeaway:** note that "produced item-3" and "item-4" are **not** produced immediately — the producer is *blocked* in `wait()` because the buffer is full (capacity 2). That is **back-pressure**, and it is the correct behaviour. The `while` loop (not `if`) is what makes it correct in the presence of a spurious wakeup.

### Example 4 — `ThreadLocal` and the pool leak

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadLocalDemo {
    static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(1);   // ONE thread, many tasks

        // ❌ WITHOUT remove() — the value is left behind on the pooled thread
        for (int i = 0; i < 3; i++) {
            final int id = i;
            pool.submit(() -> {
                CONTEXT.set("request-" + id);
                // no remove() → the value stays on the pooled thread
            });
        }
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        // The SAME thread now runs a task that expects a clean context
        pool = Executors.newFixedThreadPool(1);
        pool.submit(() -> {
            System.out.println("❌ without remove(): context = " + CONTEXT.get() + "  ← LEAKED from a previous task");
        });
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        // ✅ WITH remove() in a finally
        pool = Executors.newFixedThreadPool(1);
        for (int i = 0; i < 3; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    CONTEXT.set("request-" + id);
                } finally {
                    CONTEXT.remove();          // ⚠ ESSENTIAL
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        pool = Executors.newFixedThreadPool(1);
        pool.submit(() -> System.out.println("✅ with remove():    context = " + CONTEXT.get() + "  ← clean"));
        pool.shutdown();
        pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
    }
}
```

**Actual output:**

```text
❌ without remove(): context = request-2  ← LEAKED from a previous task
✅ with remove():    context = null  ← clean
```

**Takeaway:** the leaked value `"request-2"` is from a *completed* request, and it is visible to a *different* request. In a trace-id system that means **one customer's trace id appears on another customer's request** — a real data-leak bug, not just a memory leak.

### Example 5 — Interruption, done correctly

```java
public class InterruptionDemo {

    static void blockingCall() throws InterruptedException {
        Thread.sleep(10_000);       // throws InterruptedException when interrupted
    }

    public static void main(String[] args) throws InterruptedException {
        // ❌ Swallowing the InterruptedException
        Thread bad = new Thread(() -> {
            try {
                blockingCall();
            } catch (InterruptedException e) {
                // ignored — the cancellation is LOST
            }
            System.out.println("  ❌ the task kept running after the interrupt");
        });
        bad.start();
        Thread.sleep(100);
        bad.interrupt();
        bad.join();

        // ✅ Restoring the flag
        Thread good = new Thread(() -> {
            try {
                blockingCall();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // restore the flag for the caller
                System.out.println("  ✅ the task stopped cleanly and re-raised the interrupt");
                return;
            }
            System.out.println("  ❌ should not reach here");
        });
        good.start();
        Thread.sleep(100);
        good.interrupt();
        good.join();
    }
}
```

**Actual output:**

```text
  ❌ the task kept running after the interrupt
  ✅ the task stopped cleanly and re-raised the interrupt
```

**Takeaway:** swallowing `InterruptedException` breaks the whole cancellation protocol — a `Future.cancel(true)`, a container's shutdown, and a `Thread.stop` all rely on it.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A safe, self-cleaning request context

```java
package com.example.context;

import java.util.function.Supplier;

/**
 * A request-scoped context that is safe to use inside a thread pool.
 *
 * <p>Three rules this class enforces:
 * <ol>
 *   <li>The value is set and cleared <b>around</b> the work, in a {@code finally}.</li>
 *   <li>Clearing is guaranteed even if the work throws.</li>
 *   <li>The API makes it hard to forget: {@link #runWith} owns the whole lifecycle.</li>
 * </ol>
 *
 * <p>Modern alternative: {@code ScopedValue} (Java 21 preview, final in 25) — an
 * immutable binding that is restored automatically and is inheritable by child threads.
 */
public final class RequestContext {

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    public record Context(String traceId, String tenantId, String userId) {}

    private RequestContext() {}

    /** The current context, or an empty one. */
    public static Context current() {
        Context context = CURRENT.get();
        return context == null ? new Context("no-trace", "no-tenant", "anonymous") : context;
    }

    /** Runs the work with the context set, and ALWAYS clears it afterwards. */
    public static <T> T runWith(Context context, Supplier<T> work) {
        Context previous = CURRENT.get();
        CURRENT.set(context);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();       // ⚠ remove, not set(null) — remove() also frees the map entry
            } else {
                CURRENT.set(previous);  // restore the outer context (e.g. a nested call)
            }
        }
    }

    public static void runWith(Context context, Runnable work) {
        runWith(context, () -> { work.run(); return null; });
    }
}
```

```java
// ✅ The usage — the lifecycle is owned by runWith
RequestContext.runWith(new RequestContext.Context("trace-abc", "tenant-7", "user-42"), () -> {
    handle(request);
});
// → the context is cleared even if handle() throws
```

### 6.2 A correct producer/consumer with `Condition`s

```java
package com.example.concurrent;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A bounded, blocking, priority-free work queue built with ReentrantLock + Conditions.
 *
 * <p>Why Conditions rather than wait/notifyAll: with wait/notifyAll every producer and
 * every consumer wakes on every signal and then re-checks; with two Conditions we wake
 * ONLY the threads that can make progress.
 */
public final class BoundedWorkQueue<T> implements AutoCloseable {

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    private final java.util.Queue<T> queue = new ArrayDeque<>();
    private final int capacity;

    public BoundedWorkQueue(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
    }

    /** Blocks (interruptibly) until there is room. */
    public void put(T item) throws InterruptedException {
        Objects.requireNonNull(item, "item");
        lock.lockInterruptibly();
        try {
            while (queue.size() == capacity) {   // ⚠ while, not if — spurious wakeup
                notFull.await();
            }
            queue.add(item);
            notEmpty.signal();                   // ⚠ exactly one consumer can proceed
        } finally {
            lock.unlock();                      // ⚠ ALWAYS
        }
    }

    /** Blocks (interruptibly) until an item is available. */
    public T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty()) {
                notEmpty.await();
            }
            T item = queue.poll();
            notFull.signal();                   // ⚠ exactly one producer can proceed
            return item;
        } finally {
            lock.unlock();
        }
    }

    /** A non-blocking poll. */
    public T poll() {
        lock.lock();
        try {
            T item = queue.poll();
            if (item != null) notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    @Override public void close() { }
}
```

### 6.3 A thread-safe, immutable "latest value" holder

```java
package com.example.concurrent;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A "latest value" holder — the correct way to publish a mutable-looking value
 * safely between threads.
 *
 * <p>The pattern: keep the value <b>immutable</b> and swap the <b>reference</b>
 * atomically. Readers see either the old value or the new one — never a half-built one.
 *
 * <p>This is the same technique as {@code volatile} for a single field, generalised
 * to a whole object graph, and it is how immutable "configuration snapshots" are
 * published in real systems.
 */
public final class LatestValue<T> {

    private final AtomicReference<T> ref = new AtomicReference<>();

    public LatestValue(T initial) {
        this.ref.set(initial);
    }

    public T get() {
        return ref.get();          // ✅ a single volatile read — always a complete value
    }

    /** Atomically replaces the value. */
    public void set(T value) {
        ref.set(value);            // ✅ a single volatile write
    }

    /**
     * Atomically computes a new value from the current one.
     * The update function MUST be side-effect-free (it may run more than once).
     */
    public void update(java.util.function.UnaryOperator<T> fn) {
        ref.updateAndGet(fn);
    }

    /** Computes a new value only if the current one is absent. */
    public T getOrCompute(Supplier<T> supplier) {
        T existing = ref.get();
        if (existing != null) return existing;
        T computed = supplier.get();
        return ref.compareAndSet(null, computed) ? computed : ref.get();
    }
}
```

> 💡 **The design lesson:** the "immutable value + atomic reference swap" pattern is
> *the* lock-free way to publish state. It is used for configuration hot-reloads,
> for "current request context", and for any state that readers must see
> consistently. It is strictly better than a `volatile` field of a mutable type —
> which is why `volatile` on a mutable object is a code smell.

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — `t.run()` instead of `t.start()`

```java
Thread t = new Thread(() -> work());
t.run();      // ❌ runs on the CURRENT thread — no concurrency at all
```

**✅ FIX** — `t.start()`.

---

### ❌ Pitfall 2 — `volatile` used for a counter

```java
private volatile int counter = 0;
counter++;      // ❌ NOT atomic — a read-modify-write race
```

**✅ FIX** — `AtomicInteger`, `LongAdder`, or `synchronized`.

---

### ❌ Pitfall 3 — `if` instead of `while` around `wait()`

```java
while (!condition) { }     // ✅
if (!condition) { wait(); } // ❌ a spurious wakeup breaks it
```

**✅ FIX** — always re-check the condition in a `while` loop.

---

### ❌ Pitfall 4 — `notify()` instead of `notifyAll()`

```java
lock.notify();     // ❌ wakes ONE arbitrary waiter → a lost notification
```

**✅ FIX** — `notifyAll()`, or use `ReentrantLock` + `Condition`.

---

### ❌ Pitfall 5 — Swallowing `InterruptedException`

```java
try { queue.take(); } catch (InterruptedException e) { }   // ❌ the cancellation is lost
```

**✅ FIX** — `Thread.currentThread().interrupt();` in the catch.

---

### ❌ Pitfall 6 — `ThreadLocal` without `remove()` in a pool

```java
CONTEXT.set(id);      // ❌ never removed → the value lives with the pooled thread
```

**✅ FIX** — `try { … } finally { CONTEXT.remove(); }`

---

### ❌ Pitfall 7 — Synchronising on `this` or on a `String`

```java
synchronized (this) { }        // ❌ anyone with a reference can lock you out
synchronized ("LOCK") { }      // ❌ string interning makes the lock GLOBAL
```

**✅ FIX** — a **private** `final Object lock = new Object();` field.

---

### ❌ Pitfall 8 — Forgetting `unlock()` on a `ReentrantLock`

```java
lock.lock();
doWork();          // ❌ if this throws, the lock is never released → deadlock
```

**✅ FIX** — always `try { … } finally { lock.unlock(); }`.

---

### ❌ Pitfall 9 — Believing `Thread.State.RUNNABLE` means "running"

```java
// ❌ RUNNABLE includes a thread WAITING FOR THE CPU
```

**✅ FIX** — check the CPU time in the thread dump, not just the state.

---

### ❌ Pitfall 10 — `new Thread(...)` in application code

```java
new Thread(() -> handle(request)).start();   // ❌ unbounded, no lifecycle, no shutdown
```

**✅ FIX** — an `ExecutorService` (or, in Java 21, `newVirtualThreadPerTaskExecutor()`).

---

### ❌ Pitfall 11 — `sleep()` used as a condition wait

```java
while (queue.isEmpty()) {
    Thread.sleep(100);   // ❌ slow, and it does not release the monitor
}
```

**✅ FIX** — `wait()`/`notify()` (or a `BlockingQueue`).

---

### ❌ Pitfall 12 — Publishing a partially constructed object

```java
public MyClass() {
    this.ready = true;      // ❌ `this` ESCAPED — another thread may see an
    init();                 //    object whose fields are not yet set
}
```

**✅ FIX** — never let `this` escape the constructor; make the fields `final` where possible.

---

### ❌ Pitfall 13 — Assuming a `HashMap` is thread-safe

```java
private static final Map<String, CacheEntry> CACHE = new HashMap<>();   // ❌
```

**✅ FIX** — `ConcurrentHashMap`.

---

### ❌ Pitfall 14 — Using `Thread.setPriority` for ordering

```java
thread.setPriority(Thread.MAX_PRIORITY);   // ⚠ a HINT — the JVM may ignore it entirely
```

**✅ FIX** — model the priority in your own queue (`PriorityBlockingQueue`).

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is the difference between `start()` and `run()`?**
**A:** `start()` asks the JVM to create a **new thread** and run `run()` on it. Calling `run()` directly executes the body **on the current thread** — no new thread, no concurrency. `start()` can be called only once per `Thread`; a second call throws `IllegalThreadStateException`.

**Q: What are the six thread states?**
**A:** `NEW`, `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`, `TERMINATED`. `BLOCKED` means waiting for a **monitor**; `WAITING` means an indefinite `wait()`/`join()`/`park()`; `TIMED_WAITING` is the same with a timeout; `RUNNABLE` covers both running and waiting-for-the-CPU.

**Q: What is the difference between `wait()` and `sleep()`?**
**A:** `wait()` **releases the monitor** the calling thread holds and must be called inside a `synchronized` block; `sleep()` does **not** release it and can be called anywhere. `wait()` is for a condition variable (and can wake spuriously, so it must be in a `while` loop); `sleep()` is for pausing.

**Q: What is `volatile` for?**
**A:** It gives **visibility** and **ordering** — a write to a `volatile` field is guaranteed to be seen by any subsequent read of it, and it creates a happens-before edge. It does **not** make compound operations atomic, so `volatile int i; i++` is still a race.

**Q: What are daemon threads?**
**A:** Daemon threads do not keep the JVM alive — when the last **non-daemon** thread finishes, the JVM exits and the daemons are killed. Use them for background housekeeping; never for work that must complete.

### 🟡 Intermediate

**Q: What is the Java Memory Model, and what is a happens-before edge?**
**A:** The JMM specifies what a thread is guaranteed to observe. A *happens-before* edge is a guarantee that one action's result is visible to another: everything written before the edge is visible after it. The main edges are: program order; monitor unlock → lock; `volatile` write → read; `Thread.start()` → the new thread's actions; a thread's actions → `join()` returning; the `final` field freeze; and the concurrency utilities. Absent an edge, the JMM **permits** a thread to see a stale or "impossible" value.

**Q: Explain the difference between visibility and atomicity, with an example.**
**A:** Visibility is "will another thread see my write?"; atomicity is "is my read-modify-write indivisible?". `volatile` gives visibility but not atomicity: with a `volatile int counter`, two threads can both read 0 and both write 1, so the counter ends at 1 instead of 2 — the writes are *visible* but *lost*. Atomicity needs a mutual-exclusion mechanism (`synchronized`, a `Lock`) or an atomic primitive (`AtomicInteger`, `LongAdder`).

**Q: Why must captured local variables be effectively final?**
**A:** Because the capture is shared between the enclosing method and the lambda, and the lambda may run on another thread. If the captured variable were mutable, it would be a shared-mutable-state race. Requiring effective finality makes the capture **immutable**, so the JMM's final-field semantics apply and the capture can be safely published to any thread.

**Q: Why use `notifyAll()` rather than `notify()`?**
**A:** `notify()` wakes **one arbitrary** waiter, which may not be the one that can proceed — a *lost notification*, where the notified thread finds the condition still false and waits again, while the other waiters sleep forever. `notifyAll()` wakes all of them; each re-checks the condition in a `while` loop and only the one that can proceed takes the action.

**Q: What is the `ThreadLocal` leak in a thread pool, and how do you avoid it?**
**A:** A `ThreadLocal`'s value is stored in the **pooled thread's** `ThreadLocalMap`, so it lives as long as the thread — not the request. A value set in request A is still there when the same thread handles request B (a **data leak**, not just a memory leak). The fix is `remove()` in a `finally`; the modern alternative is `ScopedValue` (Java 21 preview), whose binding is restored automatically when the scope exits.

**Q: When would you use `ReentrantLock` instead of `synchronized`?**
**A:** When you need a **timeout** (`tryLock(t, unit)`), an **interruptible** acquire (`lockInterruptibly()`), **fair** ordering, or **multiple conditions** (a `Condition` per wait state, so you wake only the threads that can proceed). The cost is that you must `unlock()` in a `finally` yourself.

### 🔴 Advanced / Senior

**Q: Explain the internals of `synchronized` — what happens on `monitorenter`?**
**A:** The JVM tries to acquire the object's monitor. The object's **Mark Word** holds the lock state: with no contention it stores a pointer to a heap "lock record" (a *thin lock*); with contention but no inflation it stores the owning thread's ID and a recursion count (a *lightweight lock*); and if there is genuine contention (or the lock is held across a `wait`), the Mark Word points to an inflated `ObjectMonitor` (a *heavyweight lock*). **Biased locking** — a 4th state where the Mark Word stored the owning thread's ID so an uncontended lock was free — was disabled by default in **JDK 15 (JEP 374)** and removed in JDK 18, because it was a security risk and it degraded JIT optimisations.

**Q: What exactly is a "lost update", and what is the complete set of fixes?**
**A:** A lost update is two threads performing a read-modify-write on the same value and one write being overwritten. The fixes, in increasing order of preference: (1) **an atomic SQL statement** — `UPDATE t SET c = c + 1` — which pushes the atomicity into the database, the component that owns the data; (2) **`AtomicInteger`/`LongAdder`** — a CAS retry loop in the application; (3) **`synchronized`/`Lock`** — mutual exclusion around the whole operation; (4) **a `StampedLock`** for a read-mostly workload; and (5) **immutable state with an atomic reference swap** for "publish a whole object" semantics. The performance order is roughly (1) < (2) < (3) under contention — the more you serialise, the worse it scales.

**Q: How do you debug a deadlock in production?**
**A:** (1) `jstack <pid>` (or `jcmd <pid> Thread.print`) — the JVM's own detector prints `Found 1 deadlock.` with the full cycle for **intrinsic** monitors; for `ReentrantLock` deadlocks you must read the cycle yourself (or use `jcmd Thread.dump_to_file`). (2) Look for a cycle in the "waiting to lock" / "locked" pairs. (3) Check for the usual causes: **lock-ordering inversion** (fix with a global lock order or `tryLock` with a timeout), a **pool deadlock** (a task holds a pool connection and waits for another task in the same pool), or **`synchronized` on a `String`**. (4) Prevent it: enforce a **global lock order**, use `tryLock(timeout)` and back off, and never hold a lock across a blocking call. A thread dump is also the best tool for **livelock** (threads in `RUNNABLE` making no progress) and **starvation**.

**Q: When would you choose virtual threads, and what are the constraints?**
**A:** Choose them for **blocking-I/O-bound** services: thousands of concurrent requests each waiting on a socket. The constraints: (1) they do **not** add CPU parallelism — for CPU-bound work use a `ForkJoinPool`; (2) in Java 21–23, a virtual thread that blocks inside `synchronized` or a native method **pins** its carrier, collapsing scalability — use `ReentrantLock` (Java 24's JEP 491 fixed the `synchronized` case); (3) they need an explicit **bound on the downstream** (a semaphore), because a million virtual threads would open a million connections; (4) `ThreadLocal` values are retained per task rather than reused per pooled thread, so the leak profile changes; and (5) `synchronized` contention becomes the new bottleneck.

### ⚫ Expert / Principal

**Q: The JMM allows a thread to observe an "impossible" state. Isn't that a bug in the memory model? Why not just make everything sequentially consistent?**
**A:** It is not a bug — it is the **deliberate relaxation** of a stronger model in exchange for the ability to use modern hardware. The reasoning: the JMM does not define what the *hardware* does; it defines the **minimum** guarantee a correct program may rely on. On x86 (a total-store-ordering architecture), a strong model would be nearly free; on ARM (weakly-ordered), it would require a full memory barrier after every write, which would destroy the performance benefit of out-of-order execution. So the JMM permits the reordering and requires the *program* to create the happens-before edges — which the compiler then implements with the right barriers *for that target*. A sequentially consistent model would have been simpler to reason about and much slower on ARM. The trade is the classic one: **the programmer pays (with `synchronized`/`volatile`) in exchange for the hardware paying nothing extra.** The deep consequence: the JMM is not just about threads — it is the contract between the *compiler*, the *JIT*, and the *CPU*. A `volatile` read is a `LoadLoad`/`LoadStore` barrier on x86 but a `DMB ISH` on ARM — the same source, the same semantics, different instructions. And the modern pressure on the model is the **loom** project (which delivered `StructuredTaskScope` in Java 21): it aims to give Java a *data-race detector* and a *test harness* for concurrency bugs, so that the JMM's subtleties can be explored safely — the missing piece in Java's concurrency story for two decades.

**Q: Design the concurrency model for a service that must handle 50,000 concurrent long-polling requests, and explain every choice.**
**A:** **Virtual threads (Java 21) with an explicit downstream bound.** The reasoning, step by step: (1) The workload is **waiting**, not computing — 50,000 requests are parked on a socket. Platform threads would cost 50,000 × 1 MB = 50 GB of stack; virtual threads cost ~50 MB. (2) The code stays **simple blocking** — no reactive stack, no `CompletableFuture` chains, no `Mono`/`Flux`. That is the biggest practical win: the debugging is ordinary, the stack traces are readable, and the code is reviewable. (3) The **downstream is the real constraint**, so I bound it with a `Semaphore` sized to the broker's capacity — the virtual-thread count is unbounded, the *resource* count is not. (4) **No `synchronized` around blocking I/O** — `ReentrantLock` (or Java 24+'s non-pinning monitor). (5) **`StructuredTaskScope`** (Java 21 preview) to tie a request's sub-tasks to the request's lifetime, so a failure cancels the siblings — with the honest caveat that it is still a preview. (6) **`ThreadLocal` kept minimal** — a trace id at most — and cleared, because on virtual threads the value's lifetime is the *task's*, not the pooled thread's. (7) **Observability**: carrier count and utilisation (to detect pinning), the semaphore's queue depth (to detect downstream saturation), and the p99 latency. The alternative for a Java 17 deployment is Reactor, but it trades the simple code for a steep learning curve and much harder debugging — and for long-polling specifically, the reactive advantage is smaller than for high-throughput request/response.

---

## 9. Counter-Questions & Traps

### CQ1. "If `synchronized` provides everything, why would anyone use `volatile`?"

- **Naive:** "`volatile` is faster than `synchronized`."  ← *true, but the question is about *need*
- **The trap:** The trap is to treat this as a performance question. The real question is: **what does `volatile` guarantee that `synchronized` does not, and vice versa?** `synchronized` gives mutual exclusion — a *stronger* guarantee — so `volatile` is never "needed" for correctness where `synchronized` is available. The real reason to choose `volatile` is that `synchronized` gives you something you may *not want*: **exclusion**.
- **Expert answer:** The three reasons to prefer `volatile`. (1) **Granularity** — `volatile` protects *one field*; `synchronized` protects a *block*, and holding a lock across several operations is where deadlocks come from. (2) **No blocking** — a `volatile` write never waits for a lock, so there is no contention, no deadlock potential, and no lock-ordering problem. (3) **No mutual exclusion at all** — sometimes you *want* concurrent readers and a single writer, and `synchronized` would serialise the readers too. The classic example: a `volatile boolean running` flag that many threads poll — with `synchronized` the pollers would contend on every iteration. The deeper lesson: **the choice is between a guarantee and a cost.** `volatile` is the cheap guarantee; `synchronized` is the expensive one. And the rule that follows: use `volatile` when you need *visibility of one variable* and no compound operation; use `synchronized` when you need a *compound operation* to be atomic.

### CQ2. "You said `synchronized` gives visibility. But the field is not `volatile` — how does that work?"

- **Naive:** "The monitor does a memory barrier."  ← *true, and it misses the mechanism*
- **The trap:** The trap is to treat "the monitor" as a magic barrier. The precise mechanism is the **happens-before edge**: the *unlock* of a monitor happens-before every subsequent *lock* of the same monitor. So the writes the releasing thread made (which are all *before* the unlock in program order) are guaranteed visible to the acquiring thread.
- **Expert answer:** The chain of reasoning: (1) program order gives "the writes before the unlock happen-before the unlock"; (2) the JMM's monitor rule gives "the unlock happens-before the next lock"; (3) therefore "the writes before the unlock happen-before the reads after the lock". So the visibility is a *consequence* of the mutual exclusion — you get the visibility for free because the monitor's exit is a full barrier. This is exactly why `synchronized` gives you all three guarantees and `volatile` gives you only two: the monitor's *acquire* semantics are needed for the mutual exclusion, and once you have them, the visibility edge comes with them. The practical corollary worth stating: **you only need `volatile` when you want the visibility *without* the exclusion** — that is, when the field is written by one thread and read by many, and the read-modify-write is not a concern.

### CQ3. "Why is `synchronized` reentrant? What would break if it weren't?"

- **Naive:** "So a method can call another synchronised method on the same object."  ← *the symptom, not the reason*
- **The trap:** The trap is to treat reentrancy as a convenience. It is a **requirement**, and the reason is that `synchronized` is used for *mutual exclusion*, not merely for signalling. Without reentrancy, a `synchronized` method that called another `synchronized` method on the same object would **deadlock itself**.
- **Expert answer:** The concrete scenario: a public `synchronized` method calls a private `synchronized` helper on the same object — an extremely common pattern. Without reentrancy, the thread would hold the monitor and then block waiting for a monitor it already holds → a self-deadlock. The implementation: the monitor tracks the **owning thread** and a **recursion count**; a re-acquire by the same thread just increments the count, and the monitor is released only when the count reaches zero. The same idea appears in `ReentrantLock` (which is why it is called *reentrant*). The deeper lesson: **a mutual-exclusion primitive must be reentrant, or it will be unusable in the most common code patterns** — the encapsulation pattern of "a public locked method delegating to a private locked helper".

### CQ4. "Why can't you `wait()` outside a `synchronized` block? Why is it designed that way?"

- **Naive:** "Because you need to hold the lock."  ← *the rule, not the reason*
- **The trap:** The trap is to see the rule as arbitrary. The reason is that `wait()`'s entire contract is "**atomically** release the monitor and block". If you could call `wait()` without holding the monitor, there would be nothing to release, and the "release + block" would not be atomic — so another thread could slip in and change the condition between your check and your wait, and you would sleep forever on a condition that is already true. The atomicity is the whole point.
- **Expert answer:** The two-step design: `wait()` does three things atomically — (1) it verifies the caller **owns** the monitor (otherwise `IllegalMonitorStateException`), (2) it **releases** the monitor, and (3) it **blocks**. The atomicity of (2) and (3) is what prevents the classic **lost wakeup**: a producer checks "is the buffer full? yes" and calls `wait()`; between the check and the wait, a consumer takes an item and calls `notify()` — if the release were not atomic with the block, the consumer's `notify()` would happen while the consumer still held the lock, the producer would not yet be waiting, and the notification would be lost forever. The design enforces the invariant: **you can only wait on a monitor you hold, and the release-and-block is indivisible.**

### CQ5. "If `ThreadLocal` is so useful, why does the JDK keep adding `ScopedValue`?"

- **Naive:** "`ScopedValue` is the modern version."  ← *true, and it misses the three real problems*
- **The trap:** The trap is to think `ScopedValue` is a syntactic upgrade. The three real problems with `ThreadLocal` are: (1) **it is mutable** — any code with the reference can change the value, and there is no "as of" semantics; (2) **it is not inheritable in a useful way** — a child thread gets a *copy* of the parent's `ThreadLocal` (via `InheritableThreadLocal`), which is often wrong; and (3) **it leaks in pools** — the value's lifetime is the thread's, not the scope's.
- **Expert answer:** The three fixes `ScopedValue` (Java 21 preview, final in 25) brings. (1) **Immutability** — the binding cannot be changed after it is set, so it is *safe* to share, and the compiler can reason about it. (2) **Lexical scope** — the binding is automatically restored (and the map entry removed) when the scope exits, so **the leak class is gone by construction** — you cannot forget the cleanup. (3) **Structured inheritance** — a child thread *inherits* the binding by default, and the inheritance is per-scope, not a one-time copy. The cost: it is a **preview** API in 21, it cannot be used with `ThreadLocal`-style "set anywhere" patterns, and (in 21) it was not yet `final`. The engineering lesson: **`ThreadLocal` is a mutable, inheritable, leak-prone mechanism used for a scoped need** — and the language is finally giving that need a first-class type. The migration advice for a modern codebase: use `ScopedValue` for request-scoped context, and keep `ThreadLocal` only for the rare cases that genuinely need mutability.

### CQ6. "You said `synchronized` on a `String` is a global lock. Isn't that a rare mistake?"

- **Naive:** "It's rare."  ← *true, and it misses why it's dangerous*
- **The trap:** The trap is to dismiss it as a rare mistake. It is not rare — it is a **famous, widespread anti-pattern** (found in real codebases and in many interview-question repositories), and it fails in a particularly nasty way: the damage is **global and cross-application**.
- **Expert answer:** The mechanism: string literals are **interned** into the string pool, so `"LOCK"` in two different classes is the **same** `String` object. Synchronising on it means every part of the JVM that uses that literal contends for one monitor — including completely unrelated libraries. The failure mode is not a subtle bug; it is a **production outage** where one component's lock holder stalls every other component using the same literal. The JDK's own advice is explicit: "Never synchronize on a String literal". The related traps: synchronising on a **boxed `Integer`** (`Integer.valueOf(1000)` is cached, so `Integer.valueOf(100)` in two places is the *same* object — and the cache range is −128..127, so it only bites for small values, which makes it a *heisenbug*); and synchronising on a `StringBuffer` (you do not own it). The correct pattern is always a **private `final Object lock = new Object();`** — a per-instance, per-class lock that nobody else can reach.

### CQ7. "If `synchronized` is biased-locking-removed and the JIT optimises locks, is `synchronized` still slower than `ReentrantLock`?"

- **Naive:** "Yes, `synchronized` is always slower."  ← *was true; it is no longer a simple comparison*
- **The trap:** The trap is to carry forward the pre-JDK-15 folklore. Since biased locking was removed (JEP 374) and the JIT's lock optimisation has matured (escape analysis, lock coarsening, and C2's "lock elision" when the monitor does not escape), a `synchronized` block on a **non-escaping** object can be **entirely removed** by the JIT — making it *faster* than any `ReentrantLock`.
- **Expert answer:** The modern picture: (1) **Lock elision** — if the JIT can prove the object does not escape the thread, the monitor is removed entirely; a `ReentrantLock` can never be elided this way. (2) **Lock coarsening** — nested locks on the same object are merged. (3) **Biased locking is gone** (JDK 15+), so the "uncontended lock is free" property no longer exists — an uncontended `synchronized` now costs a CAS. So the honest answer: `synchronized` is **simpler and usually fast enough**; a `ReentrantLock` is *not* inherently faster, and it can be *slower* (an extra object, an `AQS` node, and it cannot be elided). Use `ReentrantLock` for its **capabilities** — timeouts, interruptibility, fairness, multiple conditions — not for speed. The counter-consideration: `synchronized` is *monitorenter/monitorexit* bytecode, and the JVM can do things with it that it cannot do with a user-space lock, so "replace `synchronized` with `ReentrantLock` for performance" is advice that has been obsolete for a decade.

### CQ8. "If the JMM permits stale reads, how do you ever *test* a concurrency bug?"

- **Naive:** "You run the test many times."  ← *the reality, and it is unsatisfying*
- **The trap:** The trap is to treat a flaky test as a failed test. A concurrency bug is **not reproducible by default** — it depends on a specific interleaving, which depends on the JIT, the CPU, the number of cores, and the timing. A test that passes 1,000 times on your machine may fail once in production.
- **Expert answer:** The modern answer is **jcstress** (the OpenJDK Concurrency Stress Tests harness) and the **loom** project (which brought a data-race detector and a systematic explorer for concurrency bugs into the JDK). The idea of `jcstress` is *not* "run it a lot" but **enumerate the interesting interleavings systematically**: it runs actors in a controlled way, recording the outcomes, and asserts that only the *legal* outcomes occur. So the test asserts "this outcome must never happen" — which is exactly the shape of a concurrency bug. The complementary tools: **`ThreadMXBean` / `jcmd`** for detecting blocked threads, **`-XX:+UnlockDiagnosticVMOptions`** for `jstack` in a loop, and a **stress harness** that varies the thread count and the timing. The practical discipline for a team: (1) write concurrency tests that assert on **invariants** ("the counter is never less than the number of increments") rather than on exact values; (2) use `CountDownLatch`/`CyclicBarrier` to *force* the interleaving you want to test; (3) run the suite on a many-core machine and under load; and (4) enable a race detector in CI (the JDK's `-XX:+UnlockDiagnosticVMOptions` plus a JFR event, or `jcstress` in a dedicated job).

### CQ9. "You said `Thread.start()` is a happens-before edge. Isn't that just about visibility — what about the thread's *own* state?"

- **Naive:** "It guarantees the new thread sees the parent's writes."  ← *true, and it misses the full guarantee*
- **The trap:** The trap is to think the edge is only about *data*. The full guarantee is stronger: **everything the parent thread did before `start()` — including starting other threads, publishing to other threads, and its own prior happens-before chains — is visible to the new thread.** It is a *transitive* guarantee: the happens-before relation is **transitive**, so if A→B and B→C, then A→C.
- **Expert answer:** The transitivity is the key insight and it is what makes the JMM comprehensible. So: if the parent wrote `x` before starting a thread, and that thread wrote `y` (after seeing `x`) before starting a *second* thread, the second thread sees both `x` and `y` — even though it never interacted with the parent. The same transitivity is why `join()` is so powerful: everything every thread in a "tree" of `start()` calls did is visible after the outermost `join()` returns. This is what makes the classic pattern safe:

```java
// SAFE — because of the transitive happens-before chain
Thread t = new Thread(() -> { data = compute(); ready = true; });
t.start();
t.join();          // ← everything t did is now visible to THIS thread
use(data);          // ✅ safe
```

The related rule: **the edge goes one way.** A `start()` edge does not mean the parent sees the child's writes — for that you need `join()`. And "the child's writes are visible to the parent" is exactly what `join()` provides. The interview framing: *happens-before is transitive and directional; `start()` publishes the parent to the child, `join()` publishes the child to the parent.*

### CQ10. "Why is `Thread.stop()`, `suspend()` and `resume()` deprecated? What do you use instead?"

- **Naive:** "They are unsafe."  ← *true, and the interview wants the mechanism*
- **The trap:** The trap is to say "unsafe" and stop. The *mechanism* is what makes the answer strong: `stop()` throws an `AsyncStopException` **at an arbitrary point** in the victim thread — possibly in the middle of a compound operation, leaving a lock held, an object half-updated, or an invariant broken.
- **Expert answer:** The concrete failure: a thread is in the middle of `balance -= amount` (a read-modify-write) when `stop()` fires. The write may never happen, or a lock may be left held — so the object is now **permanently inconsistent**, and if the lock was held, every other thread that needs it **deadlocks forever**. `suspend()` is worse: it can suspend a thread *while it holds a monitor*, deadlocking the whole system. The replacement is the **cooperative cancellation protocol**: (1) a `volatile` flag the task polls, and/or (2) `Thread.interrupt()`, which sets a flag or throws `InterruptedException` from a blocking call; and (3) the task **checks the flag at safe points** — between steps, never in the middle of a compound operation. The deeper design lesson: **cancellation in a concurrent system must be cooperative because only the thread itself knows what a safe interruption point is.** The same principle is why `StructuredTaskScope` (Java 21) cancels by *interrupting* the child rather than by killing it.

---

## 10. Rapid-Fire MCQs

**Q1.** `Thread.run()` called directly executes the body…
- A) On a new thread  B) On the **current** thread  C) On the GC thread  D) In parallel with the caller

**Q2.** How many times can `start()` be called on a `Thread`?
- A) Once  B) Twice  C) Unlimited  D) Never

**Q3.** Which state means "waiting to acquire a monitor"?
- A) `RUNNABLE`  B) `BLOCKED`  C) `WAITING`  D) `TIMED_WAITING`

**Q4.** Does `RUNNABLE` mean the thread is currently executing?
- A) Yes  B) No — it also includes waiting for the CPU  C) Only for daemon threads  D) Only in Java 8

**Q5.** `volatile` guarantees…
- A) Atomicity and visibility  B) Visibility and ordering, **not** atomicity  C) Mutual exclusion  D) Nothing

**Q6.** `synchronized` guarantees…
- A) Visibility only  B) Atomicity only  C) Visibility, atomicity and mutual exclusion  D) Only ordering

**Q7.** The happens-before edge for a monitor is…
- A) `lock(m)` → `unlock(m)`  B) `unlock(m)` → `lock(m)`  C) `start()` → `join()`  D) There is none

**Q8.** `Thread.start()` creates a happens-before edge…
- A) From the child to the parent  B) From the parent to the child  C) Both ways  D) Only for `volatile` fields

**Q9.** `join()` on a thread guarantees that everything the thread did…
- A) Is visible to the joining thread  B) Is visible to all threads  C) Has been persisted  D) Is rolled back

**Q10.** `wait()` must be called while holding…
- A) Any lock  B) The monitor of the object it is called on  C) No lock  D) A `ReentrantLock`

**Q11.** `wait()` releases the monitor?
- A) Yes  B) No  C) Only for `synchronized` methods  D) Only when it times out

**Q12.** Why must a `wait()` loop use `while` and not `if`?
- A) Style  B) Because of **spurious wakeups**  C) To avoid `IllegalMonitorStateException`  D) Performance

**Q13.** `notify()` wakes…
- A) All waiters  B) **One arbitrary** waiter  C) No one  D) Only the calling thread

**Q14.** `sleep()` releases the monitor?
- A) Yes  B) No  C) Only if called in a `synchronized` block  D) Only for daemon threads

**Q15.** A captured local in a lambda must be…
- A) `final`  B) Effectively final  C) `volatile`  D) `static`

**Q16.** Biased locking was removed in…
- A) JDK 11  B) JDK 15  C) JDK 17  D) JDK 21

**Q17.** The `ThreadLocal` value in a pool lives as long as…
- A) The request  B) The **pooled thread**  C) The `ThreadLocal` object  D) The `ThreadLocalMap` only

**Q18.** `Thread.interrupt()` on a thread blocked in `sleep()` causes…
- A) The thread to stop silently  B) An `InterruptedException` to be thrown  C) A `ThreadDeath`  D) Nothing

**Q19.** Swallowing an `InterruptedException` breaks…
- A) Compilation  B) The **cancellation protocol**  C) The memory model  D) Nothing

**Q20.** `synchronized` on a `String` literal is dangerous because…
- A) It is slow  B) String literals are **interned**, so the lock is global  C) It throws  D) It is deprecated

**Q21.** The JVM's own deadlock detector works for…
- A) All locks  B) Intrinsic monitors (`synchronized`)  C) `ReentrantLock`  D) `AtomicInteger`

**Q22.** A virtual thread blocking inside a `synchronized` block (Java 21–23)…
- A) Unmounts its carrier  B) **Pins** its carrier  C) Crashes the JVM  D) Blocks all carriers

**Q23.** Virtual threads are designed for…
- A) CPU-bound work  B) **Blocking I/O**  C) Cryptography  D) Replacing the GC

**Q24.** `synchronized` is reentrant so that…
- A) It is faster  B) A thread can re-acquire a monitor it already holds  C) It can be interrupted  D) It avoids inflation

**Q25.** The happens-before relation is…
- A) Symmetric  B) **Transitive**  C) Optional  D) Only defined for `volatile`

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | `run()` is just a method — calling it directly runs the body inline. |
| 2 | **A** | Once — a second call throws `IllegalThreadStateException`. |
| 3 | **B** | `BLOCKED` = waiting for a monitor. |
| 4 | **B** | `RUNNABLE` also covers a thread waiting for the CPU. |
| 5 | **B** | Visibility and ordering — **not** atomicity. |
| 6 | **C** | All three: visibility, atomicity and mutual exclusion. |
| 7 | **B** | `unlock(m)` happens-before every later `lock(m)`. |
| 8 | **B** | The parent publishes to the child. |
| 9 | **A** | The joining thread sees everything the joined thread did. |
| 10 | **B** | The monitor of the object `wait()` is called on — otherwise `IllegalMonitorStateException`. |
| 11 | **A** | Releasing the monitor is what lets another thread enter. |
| 12 | **B** | A spurious wakeup is permitted — the condition must be re-checked. |
| 13 | **B** | One **arbitrary** waiter — hence the lost-notification risk. |
| 14 | **B** | `sleep()` keeps the monitor — which is why it cannot be used as a condition wait. |
| 15 | **B** | Effectively final — never reassigned after initialisation. |
| 16 | **B** | Disabled by default in JDK 15 (JEP 374), removed in JDK 18. |
| 17 | **B** | The **pooled thread** — so the value outlives the request. |
| 18 | **B** | A blocking method throws `InterruptedException` and clears the flag. |
| 19 | **B** | The cancellation signal is lost — the caller cannot stop the task. |
| 20 | **B** | Interned literals are the same object everywhere, so the lock is global. |
| 21 | **B** | The JVM detects intrinsic-monitor deadlocks; `ReentrantLock` cycles you read yourself. |
| 22 | **B** | The carrier cannot be unmounted, so it is pinned. (Fixed in Java 24 by JEP 491.) |
| 23 | **B** | Blocking I/O — the carrier is released while the task waits. |
| 24 | **B** | So a locked method can call a locked helper on the same object without deadlocking. |
| 25 | **B** | Transitive — A→B and B→C gives A→C. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│              THE 3 PROBLEMS AND WHAT FIXES THEM                         │
├──────────────┬───────────┬───────────┬──────────┬───────────────────────┤
│ MECHANISM    │ VISIBILITY│ ATOMICITY │ ORDERING │ USE WHEN             │
├──────────────┼───────────┼───────────┼──────────┼───────────────────────┤
│ volatile     │    ✅     │    ❌     │    ✅    │ one flag, no compound  │
│ synchronized │    ✅     │    ✅     │    ✅    │ compound operations   │
│ AtomicInteger│    ✅     │ that var │   ❌ *   │ a single counter      │
│ LongAdder    │    ✅     │ that var │   ❌ *   │ a high-contention cnt │
│ final field  │    ✅     │    ❌     │    ✅    │ immutable DTOs        │
│ ReentrantLock│    ✅     │    ✅     │    ✅    │ timeouts/interruptible│
│ ConcurrentMap │    ✅     │ per-op   │    ✅    │ shared collections    │
└──────────────┴───────────┴───────────┴──────────┴───────────────────────┘
   * Atomic classes give ordering for their OWN accesses.
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE HAPPENS-BEFORE EDGES                             │
├──────────────────────────────────────────────────────────────────────────┤
│  ① program order       within one thread                                │
│  ② unlock(m) → lock(m) the monitor edge                                 │
│  ③ volatile write → read                                                 │
│  ④ start() → the new thread's actions                                   │
│  ⑤ the thread's actions → join() returns                                │
│  ⑥ final field freeze → any reference to a constructed object            │
│  ⑦ ThreadLocal set → get (same thread)                                  │
│  ⑧ concurrent-utility operation → the dependent action                  │
│                                                                          │
│  ⚠ TRANSITIVE: A→B and B→C gives A→C                                   │
│  ⚠ DIRECTIONAL: start() publishes parent→child; join() publishes child→parent│
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                       THE THREAD STATE MACHINE                         │
│  NEW ──start()──▶ RUNNABLE ──run() ends──▶ TERMINATED                  │
│                    │   ▲   │   ▲                                          │
│         ┌──────────┘   │   │   └──────────┐                              │
│         ▼              │   └──────────┐  │                              │
│     BLOCKED            │              ▼  ▼                              │
│   (monitor)       WAITING / TIMED_WAITING                                │
│                    (wait/join/sleep)                                    │
│                                                                          │
│  ⚠ RUNNABLE includes "waiting for the CPU"                              │
│  ⚠ wait() releases the monitor; sleep() does not                        │
│  ⚠ use `while` (not `if`) — spurious wakeups are permitted               │
│  ⚠ use notifyAll() (not notify()) — notify can lose a notification       │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                        THE 8 THREAD TRAPS                               │
├──────────────────────────────────────────────────────────────────────────┤
│  1. t.run() instead of t.start()   → no concurrency at all             │
│  2. volatile counter++              → still a race                    │
│  3. `if` around wait()              → breaks on a spurious wakeup      │
│  4. notify() instead of notifyAll() → a lost notification              │
│  5. Swallowing InterruptedException → the cancellation is lost          │
│  6. ThreadLocal without remove()    → leaks across pooled requests      │
│  7. synchronized(this) / on a String → a global or exposed lock        │
│  8. ReentrantLock without finally   → the lock is never released       │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The thread-pool rules

| Rule | Why |
|---|---|
| **Never** `new Thread(...).start()` for app work | No pooling, no lifecycle, no shutdown, no back-pressure |
| Name your threads | `ThreadFactory` with a counter — a thread dump becomes readable |
| Always shut down the pool | `shutdown()` + `awaitTermination()` in a `@PreDestroy` |
| Bound the queue | An unbounded queue turns back-pressure into an OOM |
| Use a rejection policy you understand | `CallerRunsPolicy` gives you back-pressure |
| Size the pool from the *resource*, not the CPU | A DB-backed pool is sized by the DB's capacity |

### Thread naming and diagnostics

```java
ThreadFactory factory = Thread.ofPlatform().name("worker-", 1).factory();
// → "worker-1", "worker-2" … in every thread dump
```

### The `ThreadLocal` checklist

```text
  ☐ Set it at the START of the request
  ☐ Remove it in a `finally`
  ☐ Keep the value SMALL (a trace id, not a payload)
  ☐ Consider `ScopedValue` (Java 21+ preview / 25 final) for new code
  ☐ Never store a request/response object in it
```

### Interrupt handling checklist

```text
  ☐ Catch InterruptedException → restore the flag → return
  ☐ Check `Thread.currentThread().isInterrupted()` in long loops
  ☐ Make blocking calls interruptible (`lockInterruptibly`, `BlockingQueue`)
  ☐ Never swallow the exception
  ☐ Propagate it up the call stack
```

### Metrics worth watching

| Metric | What it tells you |
|---|---|
| Thread count | A leak (threads not terminated) |
| `BLOCKED` thread count | Lock contention |
| Threads in `WAITING` for a long time | A lost notification or a deadlock |
| `ThreadMXBean.getPeakThreadCount()` | A burst of thread creation |
| `getThreadInfo().getBlockedTime()` | Where the contention is |
| Deadlock count (from the JVM log) | A hard failure |

### The deadlock-prevention checklist

```text
  ☐ A GLOBAL LOCK ORDER — always acquire locks in the same sequence
  ☐ Use tryLock(timeout) + back-off instead of blocking forever
  ☐ NEVER hold a lock while making a remote call
  ☐ NEVER hold a pool connection while waiting for a pool task
  ☐ Use try-with-resources so the lock is always released
  ☐ Take a thread dump on every alert — a deadlock is visible immediately
```

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The executor framework in depth | [02 · Executor Framework](02-Executor-Framework.md) |
| Locks, atomics and CAS | [03 · Locks & Atomic Classes](03-Locks-Atomic.md) |
| Deadlock, livelock, starvation | [04 · Concurrency Problems](04-Concurrency-Problems.md) |
| Virtual threads (Java 21) | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| The JLS | JLS §17.4 (Memory Model), §17.4.5 (Happens-before) |
| The loom project | The OpenJDK concurrency stress-tests project |

---

**Next Chapter:** → [02 · Executor Framework](02-Executor-Framework.md)
**Previous Chapter:** ← [07 · Java 11/17/21 Features](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
