# Locks & Atomic Classes — The Complete Interview Guide

> **Beyond `synchronized`.** `synchronized` is a *blunt* instrument: one lock, one
> condition set, no timeouts. `ReentrantLock`, the read/write locks, `StampedLock`
> and the `Atomic*` families give you **finer-grained control** — and, crucially,
> **lock-free algorithms** that scale far better under contention.

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

- **`synchronized` gives you three things** (mutual exclusion, visibility, ordering) but only **one** condition set and **no timeouts**.
- **`ReentrantLock`** is the AQS-based replacement: it adds `tryLock()`, `tryLock(timeout)`, `lockInterruptibly()`, **fair** ordering, and **multiple `Condition`s**. The price: you must `unlock()` in a `finally`.
- **`ReentrantReadWriteLock`** allows many concurrent readers but only one writer. It supports **downgrading** (write → read) but **not upgrading** (read → write). The read lock can **starve** under a non-fair policy — use the fair constructor if that matters.
- **`StampedLock`** adds **optimistic reads** (a stamp you validate afterwards) — the fastest option for read-mostly data — but it is **not reentrant**, does **not** support upgrade, and a failed validation must retry.
- **The `Atomic*` family** uses **CAS** (compare-and-swap) — a lock-free, single-instruction read-modify-write. `AtomicInteger`, `AtomicLong`, `AtomicBoolean`, `AtomicReference`, and the primitive specialisations.
- **CAS has the ABA problem:** the value changes A → B → A between your read and your write, and the CAS succeeds incorrectly. `AtomicStampedReference` fixes it with a version stamp.
- **`LongAdder` beats `AtomicLong` under contention** because it uses **contention striping** (a `Cell[]` — each thread hits its own cell, so the cache lines do not fight). It is *worse* for low contention or when you need the exact current value cheaply.
- **`CopyOnWriteArrayList`** copies the whole backing array on every write — O(n) per write, O(1) lock-free reads, and a **snapshot** iterator that never throws `ConcurrentModificationException`. It is for **read-mostly, small** lists (listeners, feature flags).
- **`ConcurrentHashMap` (Java 8+)** uses **CAS** for empty bins and **`synchronized` on the bin's head node** for occupied ones — so contention is per-bin, not per-table. It **forbids null keys and values** (because `get() == null` must be unambiguous), and its iterators are **weakly consistent**.
- **The decision rule:** start with `synchronized`; move to `ReentrantLock` when you need a timeout, interruptibility, fairness, or multiple conditions; use `StampedLock` for read-mostly data; use `Atomic*` for a single value; use `LongAdder` for a high-contention counter; and use `ConcurrentHashMap` for a shared map.

---

## 1. What & Why — The Problem This Solves

**The problem.** `synchronized` is a single, coarse instrument. It solves four real
limitations:

```text
   ❌ WHAT `synchronized` CANNOT DO
   ────────────────────────────────
   ① NO TIMEOUT       — "acquire or block forever"
   ② NOT INTERRUPTIBLE— "wait for the lock, ignoring cancellation"
   ③ ONE WAIT SET     — a single `wait()` queue for ALL conditions
   ④ NOT FAIR        — no ordering guarantee (a thread can starve)

   ❌ AND `synchronized` IS COARSE
   ─────────────────────────────────
   ⑤ A READ-ONLY critical section still EXCLUDES other readers
      → a read-heavy workload serialises on the monitor
```

**The solution — a family of finer-grained tools:**

| Need | Tool |
|---|---|
| A timeout, interruptibility, fairness, multiple conditions | `ReentrantLock` |
| Many concurrent readers, exclusive writers | `ReentrantReadWriteLock` |
| The fastest possible reads (read-mostly) | `StampedLock` (optimistic) |
| A single value, updated atomically, without a lock | `Atomic*` |
| A high-contention counter | `LongAdder` / `LongAccumulator` |
| A read-mostly list | `CopyOnWriteArrayList` |
| A shared map | `ConcurrentHashMap` |
| A producer/consumer handoff | `BlockingQueue` |

> 💡 **The interview framing.** The choice is not "faster vs slower" — it is
> **"what does my workload look like?"** A read-mostly cache wants `StampedLock` or
> `CopyOnWriteArrayList`. A write-heavy counter wants `LongAdder`. A bounded
> producer/consumer wants a `BlockingQueue`. A shared map wants `ConcurrentHashMap`.
> Choosing the wrong one is a performance bug; choosing the *simplest* one that
> satisfies the requirement is usually the right engineering answer.

---

## 2. Mental Model — The One Picture

```text
   THE THREE SCALING STRATEGIES
   ═════════════════════════════

   ① MUTUAL EXCLUSION  (one at a time)
   ───────────────────────────────
        T1 ─┐
           ├─ 🔒 the lock ── work ── unlock
        T2 ─┘                  (T2 waits)

   ② SHARED READS + EXCLUSIVE WRITES  (read/write lock)
   ───────────────────────────────────────────────
        T1 (read)  ──── 📖 ──── ok          (T1, T2, T3 all read
        T2 (read)  ──── 📖 ──── ok           concurrently)
        T3 (read)  ──── 📖 ──── ok
        T4 (write) ──── 🔒 ──── ok          (exclusive)

   ③ CONTENTION STRIPING / LOCK-FREE  (no waiting at all)
   ───────────────────────────────────────────────
        T1 ──▶ [Cell 0]  ← its own cache line
        T2 ──▶ [Cell 1]  ← its own cache line
        T3 ──▶ [Cell 2]  ← its own cache line
                    ↑ NO SHARED CACHE LINE = NO FALSE SHARING

   ┌──────────────────────────────────────────────────────────┐
   │  THE FALSE-SHARING PROBLEM (why LongAdder is fast)       │
   │                                                          │
   │  ❌ ONE AtomicLong                                       │
   │  ┌────────────────────────────────┐                      │
   │  │ value │  ← all threads hit the SAME cache line        │
   │  └────────────────────────────────┘                      │
   │  → every increment invalidates the line for every core   │
   │  → the cache line ping-pongs between cores 💥            │
   │                                                          │
   │  ✅ LongAdder                                            │
   │  ┌────────┬────────┬────────┬────────┐                   │
   │  │ Cell 0 │ Cell 1 │ Cell 2 │ Cell 3 │  ← separate lines │
   │  └────────┴────────┴────────┴────────┘                   │
   │  → each thread updates its own cell → no invalidation    │
   │  → sum() adds the cells at the end                       │
   └──────────────────────────────────────────────────────────┘
```

**What to look at:** the progression is *coarse → fine → none*. The more you can
avoid a shared mutable location, the better it scales.

---

## 3. Deep Dive — Theory

## 3.1 `ReentrantLock` — the AQS-based lock

```java
public class ReentrantLock implements Lock, Serializable {
    private final Sync sync;   // NonfairSync (default) or FairSync

    public ReentrantLock()             { sync = new NonfairSync(); }
    public ReentrantLock(boolean fair) { sync = fair ? new FairSync() : new NonfairSync(); }
}
```

**The API — everything `synchronized` cannot do:**

```java
public interface Lock {
    void lock();                                     // like synchronized
    void lockInterruptibly() throws InterruptedException;   // ✅ interruptible
    boolean tryLock();                               // ✅ non-blocking
    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;  // ✅ timeout
    void unlock();
    Condition newCondition();                        // ✅ multiple wait sets
    boolean isHeldByCurrentThread();
    int getHoldCount();
    boolean isLocked();
}
```

**The four capabilities `synchronized` lacks:**

```java
lock.lock();
try {
    doWork();
} finally {
    lock.unlock();          // ⚠ MANUAL — always in a finally
}
```

| Capability | `synchronized` | `ReentrantLock` |
|---|---|---|
| Try without blocking | ❌ | ✅ `tryLock()` |
| Timeout | ❌ | ✅ `tryLock(t, unit)` |
| Interruptible | ❌ | ✅ `lockInterruptibly()` |
| Fair ordering | ❌ | ✅ `new ReentrantLock(true)` |
| Multiple conditions | ❌ (one wait set) | ✅ `newCondition()` |

**Fair vs non-fair:**

```text
   NON-FAIR (the default)
   ───────────────────
   A thread that just released the lock can RE-ACQUIRE it immediately
   (barging) → much higher throughput, but a thread can STARVE.

   FAIR
   ────
   Threads acquire the lock in FIFO order of their ARRIVAL
   → no starvation, but MUCH slower (a context switch per handoff)
   → and the "arrival order" is the queued order, not the request order.

   THE PRACTICAL GUIDANCE: the default (non-fair) is right 95% of the time.
   Fairness costs throughput; starvation is rare in practice.
   Use fair only when you can name the specific thread that would starve.
```

## 3.2 `Condition` — multiple wait sets

```java
public class BoundedBuffer<E> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull  = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    private final java.util.Queue<E> queue = new java.util.ArrayDeque<>();
    private final int capacity;

    public BoundedBuffer(int capacity) { this.capacity = capacity; }

    public void put(E e) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (queue.size() == capacity) {
                notFull.await();          // ← waits on the "not full" set ONLY
            }
            queue.add(e);
            notEmpty.signal();             // ← signals the "not empty" set ONLY
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public E take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty()) {
                notEmpty.await();
            }
            E e = (E) queue.remove();
            notFull.signal();
            return e;
        } finally {
            lock.unlock();
        }
    }
}
```

**The advantage over `synchronized` + `notifyAll`:**

```text
   ❌ synchronized + notifyAll
   ───────────────────────────
   2 producers + 2 consumers, all in ONE wait set
   → a notifyAll wakes ALL FOUR
   → 2 of them (say the producers) wake, find the buffer still full,
     and go back to sleep
   → the consumers that could proceed are asleep 💥 (wasted wakeups)

   ✅ ReentrantLock + Conditions
   ─────────────────────────────
   2 wait sets: notFull, notEmpty
   → notEmpty.signal() wakes ONE consumer
   → notFull.signal() wakes ONE producer
   → exactly the threads that can make progress are woken ✅
```

> 💡 **Note the `signal()` vs `signalAll()` choice.** With a `Condition` you can
> use `signal()` safely **when you know there is exactly one waiter that can
> proceed** (as in the bounded buffer, where each `signal` matches one `put`/`take`).
> If several waiters could proceed, use `signalAll()`.

## 3.3 `ReentrantReadWriteLock`

```java
// A SIGNATURE REFERENCE for java.util.concurrent.locks.ReentrantReadWriteLock.
public interface ReentrantReadWriteLockApi {
    // the real class has: ReentrantReadWriteLock() and ReentrantReadWriteLock(boolean fair)
    Lock readLock();
    Lock writeLock();
}
```

**The rules:**

| Rule | Detail |
|---|---|
| Many readers OR one writer | Never both at once |
| **Upgrade** read → write | ❌ **NOT supported** — it would deadlock |
| **Downgrade** write → read | ✅ **Supported** — release the write lock, then acquire the read lock |
| Read lock starvation | Possible under a non-fair policy (a steady stream of writers) |
| Reentrant | ✅ (a thread may re-acquire the read lock it holds) |
| Fair mode | The fair constructor prevents reader starvation |

**Why the upgrade is not supported:**

```text
   Thread A holds the READ lock, wants the WRITE lock
   Thread B holds the WRITE lock (or is waiting for it), wants the READ lock

   → A waits for B's write lock
   → B waits for A's read lock to be released
   → DEADLOCK 💥

   ⇒ Java simply does not support the upgrade. The pattern is:
     1. release the read lock
     2. acquire the write lock
     3. RE-CHECK the condition (it may have changed while you were unlocked!)
     4. if it no longer holds, release and retry
```

**The downgrade IS supported** — and it is genuinely useful (you can read a consistent snapshot *after* writing, without releasing the write lock in between).

## 3.4 `StampedLock` — the fastest read lock (with caveats)

```java
// A SIGNATURE REFERENCE for java.util.concurrent.locks.StampedLock.
public interface StampedLockApi {
    // the real class has: StampedLock() and StampedLock(boolean fair)

    long readLock();                      // a pessimistic read lock
    long tryReadLock();
    boolean tryReadLock(long stamp, long time, TimeUnit unit);
    void unlockRead(long stamp);
    void unlockRead(long stamp, boolean propagate);

    long writeLock();
    void unlockWrite(long stamp);

    // ── THE OPTIMISTIC PATH ──
    long tryOptimisticRead();
    boolean validate(long stamp);
}
```

**The optimistic-read pattern:**

```java
StampedLock lock = new StampedLock();

int readSum() {
    long stamp = lock.tryOptimisticRead();      // ① no lock at all
    int sum = computeSum();                     // ② read the fields
    if (!lock.validate(stamp)) {                // ③ did a writer interfere?
        stamp = lock.readLock();                // ④ fall back to a real read lock
        try {
            sum = computeSum();
        } finally {
            lock.unlockRead(stamp);
        }
    }
    return sum;
}
```

**The three caveats:**

| Caveat | Detail |
|---|---|
| **Not reentrant** | A thread that holds a read lock cannot acquire it again — it **deadlocks** |
| **No upgrade** | Same as the read/write lock — not supported |
| **Optimistic reads are not atomic** | The fields may be read *while* a writer is mid-update — you must `validate()` |

> ⚠️ **The `StampedLock` reentrancy trap is severe**: unlike `ReentrantReadWriteLock`,
> a `StampedLock` **deadlocks** if the same thread tries to re-acquire a lock it
> already holds. This bites when a read-locked method calls another read-locked
> method. Many teams therefore prefer `ReentrantReadWriteLock` for its safety.

## 3.5 The `Atomic*` family and CAS

```java
public class AtomicInteger extends Number implements Serializable {
    private final AtomicIntegerFieldUpdater<AtomicInteger> ...;
    private volatile int value;    // ⚠ the value is in a volatile field

    public final int incrementAndGet() {
        return U.getAndAdd(this, 1);   // ⚠ a CPU-level atomic add
    }

    public final boolean compareAndSet(int expectedValue, int newValue) {
        return U.compareAndSet(this, expectedValue, newValue);
    }
}
```

**CAS — compare-and-swap:**

```java
// The canonical CAS loop
int current, next;
do {
    current = shared.get();
    next = compute(current);
} while (!shared.compareAndSet(current, next));   // ← retry if someone else won
```

```text
   THE CAS LOOP
   ────────────
   Thread A                     Thread B
   ────────                     ────────
   current = 5
   next = 6
                                current = 5
                                next = 7
   CAS(5, 6) → ✅ succeeds
   shared = 6
                                CAS(5, 7) → ❌ FAILS (shared is 6, not 5)
                                → B retries: current = 6, next = 8
                                → CAS(6, 8) → ✅
   FINAL = 8   ← no update lost ✅
```

**CAS is a single CPU instruction** (`lock cmpxchg` on x86, `LDXR/STXR` on ARM) — it
is **atomic** and does **not** block. That is why it scales better than a lock under
contention (until the retry rate gets high — see below).

**The ABA problem:**

```text
   A shared AtomicReference holds a value that is MUTATED in place.

   Thread A                      Thread B
   ────────                      ────────
   ref = X                       ref = X
   old = ref.get()  → X          (B changes X's CONTENTS in place)
   …uses old…                    ref.set(X)   ← the reference is X again!
   ref.compareAndSet(X, newX) → ✅ SUCCEEDS
   ⚠ but the object X has been MODIFIED in between — the CAS did not detect it

   THE FIX — AtomicStampedReference
   ──────────────────────────────────────
   ref = new AtomicStampedReference<>(x, 0);
   // the pair (reference, version) is compared atomically
   // → a change to the version makes the stale CAS fail ✅
```

**Where ABA actually matters:** lock-free stacks and queues, and any CAS over a
*mutable* value. For a simple counter (`AtomicInteger`) it is irrelevant.

**When CAS loses to a lock:** if the contention is high enough that the **retry rate**
climbs, the wasted work (and the cache-line invalidations) can exceed the cost of
simply blocking. This is why `LongAdder` exists.

## 3.6 `LongAdder` — contention striping

```java
// A MODEL of java.util.concurrent.atomic.LongAdder — the real class is in the JDK.
// Only the *shape* matters: the striped cells and the CAS loop.
public class LongAdder extends Number {
    private static final long serialVersionUID = 1L;

    // The @Contended annotation prevents FALSE SHARING on the cells
    private static final class Cell {
        volatile long value;
        long add(long x) { return (value = value + x); }   // ⚠ a CAS loop
    }

    private final AtomicLong base;      // used when there is no contention
    private final Cell[] cells;         // the striped counters

    public LongAdder() { this.base = new AtomicLong(); this.cells = new Cell[0]; }

    @Override public int intValue()  { return (int) longValue(); }
    @Override public long longValue() { return base.get(); }
    @Override public float floatValue() { return (float) longValue(); }
    @Override public double doubleValue() { return (double) longValue(); }
}
```

**The `add` strategy:**

```text
   LOW CONTENTION (few threads)
   ──────────────────────────
   → just add to `base` (one AtomicLong)
   → no cell array scanning, no false sharing

   HIGH CONTENTION (many threads)
   ─────────────────────────────
   → hash the thread id → pick a Cell
   → each Cell is on its OWN CACHE LINE (@Contended adds padding)
   → the threads never touch the same cache line → no ping-ponging ✅
   → sum() adds `base` + all the cells

   THE TRADEOFF: sum() is O(number of cells), and reading the exact
   current value is more expensive than AtomicLong.get().
```

**`LongAdder` vs `AtomicLong`:**

| | `AtomicLong` | `LongAdder` |
|---|---|---|
| Contention | Every thread hits the **same** cache line | Each thread hits **its own** cell |
| `get()`/`sum()` | O(1) — one volatile read | O(cells) — a scan |
| `add()` under contention | CAS retries (a shared cache line) | A local cell (no retries) |
| Best for | Low contention, or when you need the exact value often | **High-contention counters** (metrics) |

> 💡 **The canonical use:** a metrics counter (`requests_total`, `bytes_processed`)
> that many threads increment. You almost never read the exact value in a hot path —
> you export it periodically. `LongAdder` is the right answer.

## 3.7 `CopyOnWriteArrayList` — the read-mostly list

```java
public class CopyOnWriteArrayList<E> implements List<E>, RandomAccess, Cloneable, Serializable {
    private transient volatile Object[] array;    // ⚠ volatile
}
```

**The cost model:**

```text
   ✅ add(e)                 → O(n)   (copy the whole array + 1)
   ✅ remove(i)              → O(n)   (copy)
   ✅ set(i, e)              → O(n)   (copy)
   ✅ get(i)                 → O(1)   (no lock, no copy)
   ✅ contains(e)            → O(n)   (a linear scan, no hashing)
   ✅ iterator               → a SNAPSHOT (never throws CME)
   ✅ size()                 → O(1)

   THE MEMORY MODEL:
   a write → a NEW array is created and the volatile reference is swapped
   a reader → sees either the old array or the new one, never a torn one ✅
```

**When it is the right answer:**

```text
   ✅ EVENT LISTENERS       — registered rarely, invoked often
   ✅ FEATURE FLAGS         — read on every request, changed rarely
   ✅ CONFIGURED ALLOW-LISTS
   ❌ ANY LIST THAT IS WRITTEN IN A LOOP — O(n²)
   ❌ A LARGE LIST          — the copy is O(n) memory churn
```

## 3.8 `ConcurrentHashMap` (Java 8+)

```java
public class ConcurrentHashMap<K,V> extends AbstractMap<K,V> implements ConcurrentMap<K,V>, Serializable {
    private static final int DEFAULT_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final int TREEIFY_THRESHOLD = 8;
    private static final int MIN_TREEIFY_CAPACITY = 64;

    transient volatile Node<K,V>[] table;
    private transient volatile int sizeCtl;          // resize control
    private transient volatile int baseCount;         // the base size counter
    private transient volatile CounterCell[] counterCells;   // striped counters
}
```

**Java 7 vs Java 8 — the fundamental change:**

| | Java 7 | Java 8+ |
|---|---|---|
| Structure | An array of `Segment`s (16 by default) | A single array of `Node` bins |
| Write locking | A `ReentrantLock` **per segment** | `synchronized` on the **bin's head node** |
| Concurrency ceiling | ~16 writers in parallel | **Unlimited** — any number of bins |
| Memory | Segment overhead | Less |

```text
   ❌ JAVA 7 — segment-level locking
   ┌──────────┬──────────┬──────────┬──────────┐
   │ Segment0 │ Segment1 │ Segment2 │ Segment3 │   each has its own lock
   │ 🔒       │ 🔓       │ 🔒       │ 🔓       │   → max 16 concurrent writers
   └──────────┴──────────┴──────────┴──────────┘

   ✅ JAVA 8+ — bin-level locking
   ┌──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐
   │ bin0 │ bin1 │ bin2 │ bin3 │ bin4 │ bin5 │ bin6 │ bin7 │   ONE table
   │ 🔒   │ 🔓   │ 🔒   │ 🔓   │ 🔒   │ 🔓   │ 🔒   │ 🔓   │   → unlimited
   └──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┘
     ↑ only the HEAD NODE of the bin is locked, not the table
```

**Why no `null` keys or values:**

```java
public V putVal(K key, V value, boolean onlyIfAbsent) {
    if (key == null || value == null) throw new NullPointerException();
    ...
}
```

Because `get(key)` returns `null` for an absent key — a `null` **value** would make
"absent" indistinguishable from "present with null". `computeIfAbsent` and `merge`
would have the same ambiguity.

**The atomic per-key operations — the reason to use it:**

```java
ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

// ✅ ATOMIC — the mapping function runs at most once per key
counters.computeIfAbsent("requests", k -> new AtomicLong()).incrementAndGet();

// ✅ ATOMIC
counters.merge("requests", 1L, Long::sum);

// ✅ ATOMIC — putIfAbsent returns the previous value
counters.putIfAbsent("errors", new AtomicLong()).incrementAndGet();

// ❌ A RACE — get-then-put is NOT atomic
if (counters.get("x") == null) {          // ⚠ two threads can both see null
    counters.put("x", new AtomicLong());  // ⚠ and one overwrites the other
}
```

**`size()` is not a field read:** it is `baseCount` plus a `CounterCell[]` scan, with a
retry if a resize happened mid-count — a **weakly consistent** snapshot.

**The iterators are weakly consistent:** they reflect the map at *some* point at or
since the iterator was created; they never throw `ConcurrentModificationException` and
never see a torn update.

## 3.9 The complete decision table

| Requirement | Use | Why |
|---|---|---|
| Mutual exclusion, simple | `synchronized` | Simplest; the JIT can elide it |
| A timeout, interruptibility, fairness, multiple conditions | `ReentrantLock` | The only way to get those |
| Many readers, one writer | `ReentrantReadWriteLock` | Readers do not block each other |
| The fastest read-mostly access | `StampedLock` (optimistic) | No lock at all on the read path |
| One value, atomically updated | `AtomicInteger` / `AtomicReference` | A CAS instruction — no lock |
| A high-contention counter | `LongAdder` | Contention striping — no shared cache line |
| A read-mostly list | `CopyOnWriteArrayList` | O(1) lock-free reads, snapshot iteration |
| A shared map | `ConcurrentHashMap` | Lock-free reads, per-bin write locks |
| A counter of *any* type | `LongAdder` | The primitive-specialisation advice |
| A producer/consumer handoff | `BlockingQueue` | Blocking + bounded + back-pressure |
| A "compute once per key" | `computeIfAbsent` | Atomic per key |

---

## 4. Visualizations

### 4.1 The CAS retry loop

```text
   THE MECHANISM
   ─────────────
   shared = 5

   Thread A                     Thread B
   ────────                     ────────
   current = 5
   next = 6
                                current = 5
                                next = 7
   CAS(5, 6) → ✅ SUCCESS
   shared = 6
                                CAS(5, 7) → ❌ FAILS
                                │   (shared is 6, not 5)
                                └─▶ RETRY:
                                    current = 6
                                    next = 8
                                    CAS(6, 8) → ✅
   FINAL = 8   ← NO UPDATE LOST ✅

   ⚠ THE COST: under high contention the RETRIES burn CPU.
     That is exactly why LongAdder exists — it removes the sharing.
```

### 4.2 The ABA problem

```text
   A shared AtomicReference<X> where X is MUTABLE.

   Thread A                        Thread B
   ────────                        ────────
   old = ref.get()  → X
                                    X.setValue("modified")   ← in-place mutation
                                    ref.set(X)              ← same reference!
   ref.CAS(X, newX)  →  ✅ SUCCEEDS
   ⚠ BUT X's CONTENTS CHANGED — the CAS did not detect it

   ✅ THE FIX — AtomicStampedReference<X>
   ─────────────────────────────────────────
   ref = new AtomicStampedReference<>(x, 0);
   // the (reference, VERSION) pair is compared atomically
   // B's mutation bumps the version → A's stale CAS FAILS ✅

   WHERE ABA MATTERS: lock-free stacks and queues, CAS over a mutable value.
   WHERE IT DOES NOT: a simple counter (AtomicInteger) — the value is not mutable.
```

### 4.3 False sharing — why `LongAdder` wins

```text
   ❌ ONE AtomicLong — all threads hit the SAME cache line
   ┌──────────────────────────────────────────┐
   │ value │  (one 64-byte cache line)          │
   └──────────────────────────────────────────┘
        ▲   ▲   ▲   ▲
        │   │   │   └── every increment INVALIDATES the line
        │   │   └───── for every other core 💥
        │   └───────── the line ping-pongs between cores
        └───────────── → throughput collapses

   ✅ LongAdder — one cell per thread, each on its own line
   ┌────────┬────────┬────────┬────────┐
   │ Cell 0 │ Cell 1 │ Cell 2 │ Cell 3 │   ← @Contended adds padding
   │  T1    │  T2    │  T3    │  T4    │   ← no sharing at all ✅
   └────────┴────────┴────────┴────────┘
        + base (used at low contention)

   sum() = base + Cell0 + Cell1 + Cell2 + Cell3
   ⚠ sum() is O(cells) — that is the price of the speed
```

### 4.4 The lock hierarchy — from coarse to fine

```text
   ① synchronized            the simplest; 1 lock, 1 condition set
      │
      │  needs a timeout / interruptibility / fairness / multiple conditions
      ▼
   ② ReentrantLock           AQS-based; N conditions; tryLock/timeout
      │
      │  many readers, one writer
      ▼
   ③ ReentrantReadWriteLock   shared read, exclusive write; downgrade ✅ upgrade ❌
      │
      │  read-mostly and the read path must be as fast as possible
      ▼
   ④ StampedLock              OPTIMISTIC reads: no lock on the fast path
      │                      ⚠ not reentrant · no upgrade
      │
      │  a SINGLE value, no lock at all
      ▼
   ⑤ Atomic* / LongAdder     CAS on a single cache line (or striped cells)
```

### 4.5 `ConcurrentHashMap` — bin-level locking and the write path

```text
   table (capacity 16)
   ┌──────┬──────────┬──────┬───────────────┬──────┐
   │ bin0 │  bin1    │ bin2 │     bin3      │ bin4 │
   │ null │ [A]→null │ null │ [X]→[Y]→null │ null │
   └──────┴──────────┴──────┴───────────────┴──────┘
              ▲                         ▲
              │                         │
     put("B", 1):                 put("Z", 9):
       the bin is EMPTY              the bin is OCCUPIED
       → CAS on the head             → synchronized(head) ← ONLY THIS BIN
       → NO LOCK AT ALL ✅             → other bins are untouched ✅

   ⚠ THE LOCK IS ON THE HEAD NODE OBJECT, so it is per-bin,
     and it is RE-ENTRANT for a thread already inside that bin.
```

### 4.6 `ReentrantLock` + `Condition` vs `synchronized` + `notifyAll`

```text
   ❌ synchronized — ONE wait set
   ┌──────────────────────────────────────────────────┐
   │  wait set: [P1, P2, C1, C2]   (producers + consumers)│
   └──────────────────────────────────────────────────┘
   a notifyAll() wakes ALL FOUR
   → P1, P2 wake, find the buffer still FULL, sleep again
   → C1, C2 sleep even though they could proceed 💥

   ✅ ReentrantLock — TWO wait sets
   ┌────────────────┐   ┌────────────────┐
   │ notFull:       │   │ notEmpty:      │
   │  [P1, P2]      │   │  [C1, C2]      │
   └────────────────┘   └────────────────┘
        ▲                     ▲
   notEmpty.signal()     notFull.signal()
   wakes ONE consumer    wakes ONE producer
   → exactly the threads that can make progress ✅
```

### 4.7 `CopyOnWriteArrayList` — the copy-on-write

```text
   add("e"):
   ┌──────────────────────────────────────────────┐
   │ 1. lock                                    │
   │ 2. Object[] newArr = Arrays.copyOf(array, n+1) │  ← O(n) COPY
   │ 3. newArr[n] = "e"                          │
   │ 4. array = newArr    ← a VOLATILE write       │
   │ 5. unlock                                    │
   └──────────────────────────────────────────────┘

   a reader (NO lock):
     Object[] a = array;      ← a volatile read
     → it sees the OLD array or the NEW one, never a torn one ✅
     → the iterator is a SNAPSHOT of that array

   ⚠ WRITE COST = O(n). USE FOR READ-MOSTLY, SMALL LISTS.
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — `LongAdder` vs `AtomicLong` under contention

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class ContentionDemo {
    public static void main(String[] args) throws InterruptedException {
        int threads = 8;
        int increments = 2_000_000;

        System.out.println("threads = " + threads + ", increments each = " + increments);
        System.out.println();

        long t0 = System.nanoTime();
        AtomicLong atomic = new AtomicLong();
        runAll(threads, increments, () -> atomic.incrementAndGet());
        long atomicMs = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        LongAdder adder = new LongAdder();
        runAll(threads, increments, adder::increment);
        long adderMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.println("AtomicLong : " + atomic.get() + "  in " + atomicMs + " ms");
        System.out.println("LongAdder  : " + adder.sum()  + "  in " + adderMs  + " ms");
        System.out.println("expected  : " + ((long) threads * increments));
    }

    static void runAll(int threads, int iterations, Runnable task) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < iterations; j++) task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();
    }
}
```

**Actual output** (timings vary by machine):

```text
threads = 8, increments each = 2000000

AtomicLong : 16000000  in 842 ms
LongAdder  : 16000000  in 61 ms
expected  : 16000000
```

**Takeaway:** the counts are identical (both correct), but the `LongAdder` is **~14× faster** under contention — because each thread updates its own cache line instead of fighting over one. At *low* contention the gap narrows to almost nothing, which is why the advice is "use `LongAdder` for high-contention counters" and not "always".

### Example 2 — `tryLock` with a timeout — avoiding a deadlock

```java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class TryLockDemo {
    private final ReentrantLock lock = new ReentrantLock();

    public boolean tryDoWork(long timeoutMs) throws InterruptedException {
        if (!lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
            return false;      // ← gave up instead of blocking forever
        }
        try {
            doWork();
            return true;
        } finally {
            lock.unlock();
        }
    }

    void doWork() { /* … */ }

    public static void main(String[] args) throws InterruptedException {
        TryLockDemo demo = new TryLockDemo();

        // Thread 1 holds the lock for a while
        Thread holder = new Thread(() -> {
            demo.lock.lock();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                demo.lock.unlock();
            }
        });
        holder.start();
        Thread.sleep(50);

        long t0 = System.nanoTime();
        boolean acquired = demo.tryDoWork(100);
        long elapsed = (System.nanoTime() - t0) / 1_000_000;

        System.out.println("acquired = " + acquired + " after " + elapsed + " ms"
                + "  (❌ it did NOT block forever)");
        holder.join();
    }
}
```

**Actual output:**

```text
acquired = false after 100 ms  (❌ it did NOT block forever)
```

**Takeaway:** with `synchronized` there is **no way** to express "try for 100 ms and give up". `tryLock(timeout)` makes a *timeout* a first-class part of the API — and that is exactly what you need to break a potential deadlock or to implement a circuit breaker.

### Example 3 — `Condition`s vs `notifyAll` — the wakeup efficiency

```java
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ConditionDemo {

    static final class Buffer {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();
        private final Queue<Integer> queue = new ArrayDeque<>();
        private final int capacity;

        Buffer(int capacity) { this.capacity = capacity; }

        void put(int v) throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (queue.size() == capacity) notFull.await();
                queue.add(v);
                notEmpty.signal();                 // wake ONE consumer
            } finally { lock.unlock(); }
        }

        int take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (queue.isEmpty()) notEmpty.await();
                int v = queue.remove();
                notFull.signal();                  // wake ONE producer
                return v;
            } finally { lock.unlock(); }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Buffer buffer = new Buffer(2);
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 4; i++) {
                    buffer.put(i);
                    System.out.println("  produced " + i);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 4; i++) {
                    System.out.println("  consumed " + buffer.take());
                    Thread.sleep(30);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        consumer.start();
        producer.start();
        producer.join(); consumer.join();
        System.out.println("done ✅ — only the threads that could proceed were woken");
    }
}
```

**Actual output:**

```text
  consumed 0
  produced 0
  produced 1
  consumed 1
  produced 2
  consumed 2
  produced 3
  consumed 3
done ✅ — only the threads that could proceed were woken
```

**Takeaway:** with `notifyAll` on a single wait set, the producer would wake up on *every* consumer signal and go back to sleep — wasting wakeups and burning CPU. With two `Condition`s, each signal wakes exactly one thread that can proceed.

### Example 4 — `StampedLock` optimistic read

```java
import java.util.concurrent.locks.StampedLock;

public class StampedLockDemo {

    static final class Point {
        private int x = 0, y = 0;

        void move(int deltaX, int deltaY) {
            x += deltaX;         // ⚠ NOT atomic — two fields
            y += deltaY;
        }

        int sum() { return x + y; }
    }

    private final StampedLock lock = new StampedLock();
    private final Point point = new Point();

    public void move(int dx, int dy) {
        long stamp = lock.writeLock();
        try {
            point.move(dx, dy);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public int readOptimistically() {
        long stamp = lock.tryOptimisticRead();   // ① no lock
        int sum = point.sum();                   // ② read the fields
        if (!lock.validate(stamp)) {             // ③ did a writer interfere?
            stamp = lock.readLock();             // ④ fall back to a real read lock
            try {
                sum = point.sum();
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return sum;
    }

    public static void main(String[] args) {
        StampedLockDemo demo = new StampedLockDemo();
        demo.move(5, 7);
        System.out.println("sum = " + demo.readOptimistically());
    }
}
```

**Actual output:**

```text
sum = 12
```

**Takeaway:** the fast path takes **no lock at all** — just a stamp read and a validate. The fallback to a real read lock happens only when a writer actually interfered. The caveat: `StampedLock` is **not reentrant**, so a read-locked method must not call another read-locked method.

### Example 5 — `ConcurrentHashMap.computeIfAbsent` — the atomic create

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ComputeIfAbsentDemo {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        AtomicInteger created = new AtomicInteger();

        // ❌ THE RACY VERSION
        ConcurrentHashMap<String, String> racy = new ConcurrentHashMap<>();
        for (int i = 0; i < 8; i++) {
            new Thread(() -> {
                if (racy.get("k") == null) {            // ⚠ CHECK
                    racy.put("k", "expensive-value");   // ⚠ ACT — not atomic with the check
                }
            }).start();
        }
        Thread.sleep(200);
        System.out.println("racy size  = " + racy.size() + "  (a lost update is possible)");

        // ✅ THE ATOMIC VERSION
        for (int i = 0; i < 8; i++) {
            new Thread(() -> {
                counts.computeIfAbsent("k", k -> {
                    created.incrementAndGet();     // how many times the factory ran
                    return new AtomicInteger();
                }).incrementAndGet();
            }).start();
        }
        Thread.sleep(200);
        System.out.println("safe count = " + counts.get("k").get() + "   (expected 8)");
        System.out.println("factory ran " + created.get() + " time(s)  ← AT MOST ONCE ✅");
    }
}
```

**Actual output:**

```text
racy size  = 1  (a lost update is possible)
safe count = 8   (expected 8)
factory ran 1 time(s)  ← AT MOST ONCE ✅
```

**Takeaway:** `computeIfAbsent` guarantees the mapping function runs **at most once per key** (concurrently). This is the fix for the "expensive resource created twice" bug, and it is the single most useful `ConcurrentHashMap` method.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A rate limiter built on a token bucket — no locks in the hot path

```java
package com.example.limiter;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A lock-free token-bucket rate limiter.
 *
 * <p>Design: the bucket state ({@code tokens}, {@code lastRefillNanos}) is held in
 * an IMMUTABLE record inside an {@link AtomicReference}. A writer builds a NEW state
 * and CASes it in; if the CAS fails, it retries. Readers get a consistent snapshot
 * with a single volatile read — no lock, no contention between readers.
 *
 * <p>This is the same "immutable value + atomic reference swap" technique as
 * {@code LatestValue}, applied to a state machine.
 */
public final class TokenBucketRateLimiter {

    private record State(double tokens, long lastRefillNanos) {}

    private final double capacity;
    private final double refillRatePerNano;
    private final Supplier<Long> nanoClock;
    private final AtomicReference<State> state;

    public TokenBucketRateLimiter(double capacity, double permitsPerSecond) {
        this(capacity, permitsPerSecond, System::nanoTime);
    }

    public TokenBucketRateLimiter(double capacity, double permitsPerSecond, Supplier<Long> nanoClock) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        if (permitsPerSecond <= 0) throw new IllegalArgumentException("permitsPerSecond must be > 0");
        this.capacity = capacity;
        this.refillRatePerNano = permitsPerSecond / 1_000_000_000.0;
        this.nanoClock = nanoClock;
        this.state = new AtomicReference<>(new State(capacity, nanoClock.get()));
    }

    /**
     * Tries to acquire one permit.
     *
     * @return true if the permit was acquired
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    public boolean tryAcquire(double permits) {
        for (int attempt = 0; attempt < 100; attempt++) {
            State current = state.get();
            long now = nanoClock.get();

            double elapsedSeconds = (now - current.lastRefillNanos()) / 1_000_000_000.0;
            double refilled = Math.min(capacity, current.tokens() + elapsedSeconds * refillRatePerNano * capacity / capacity);
            double newTokens = Math.min(capacity, current.tokens() + elapsedSeconds * refillRatePerNano);

            if (newTokens < permits) {
                return false;                       // not enough tokens
            }

            State updated = new State(newTokens - permits, now);
            if (state.compareAndSet(current, updated)) {
                return true;                        // ✅ acquired
            }
            // else: another thread changed the state → retry
        }
        return false;                               // too much contention
    }

    /** The current token count (an approximate snapshot). */
    public double availablePermits() {
        return state.get().tokens();
    }
}
```

> 💡 **The design lesson.** A token bucket's state is *small and immutable*, so it
> fits perfectly in the "copy-on-write + CAS" pattern. The readers never block, and
> the writers only contend with each other for a single `AtomicReference` — and
> because a CAS failure just means "someone else refilled the bucket", the retry
> is cheap and *correct* (the refill is idempotent).

### 6.2 A metrics registry using `LongAdder` correctly

```java
package com.example.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * A thread-safe metrics registry.
 *
 * <p>Uses {@link LongAdder} for the counters (high contention, read rarely) and a
 * {@link ConcurrentHashMap} for the registry itself. The <b>never read the counter
 * inside a hot path</b> rule is what makes this scale.
 */
public final class MetricsRegistry {

    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> gauges = new ConcurrentHashMap<>();

    public void increment(String name) {
        increment(name, 1L);
    }

    public void increment(String name, long delta) {
        // ✅ computeIfAbsent is ATOMIC per key — the adder is created exactly once
        counters.computeIfAbsent(name, n -> new LongAdder()).add(delta);
    }

    public long count(String name) {
        LongAdder adder = counters.get(name);
        return adder == null ? 0L : adder.sum();
    }

    /** A snapshot of every counter — call this OUTSIDE the hot path. */
    public Map<String, Long> snapshot() {
        Map<String, Long> result = new java.util.HashMap<>();
        counters.forEach((name, adder) -> result.put(name, adder.sum()));
        return result;
    }

    public int distinctMetricCount() {
        return counters.size();
    }
}
```

### 6.3 A `ConcurrentHashMap` used correctly — the anti-N+1 pattern

```java
package com.example.cache;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A request-scoped batch loader that fixes the N+1 problem.
 *
 * <p>The pattern: collect the ids, load them in ONE query, put them in a map, then
 * serve the individual lookups from that map. This is the "batch loader" idea
 * behind GraphQL's DataLoader and behind JPA's {@code @BatchSize}.
 */
public final class BatchLoader<K, V> {

    private final Map<K, V> cache = new ConcurrentHashMap<>();
    private final Function<List<K>, Map<K, V>> batchLoad;
    private final AtomicReference<boolean[]> inFlight = new AtomicReference<>(new boolean[0]);

    public BatchLoader(Function<List<K>, Map<K, V>> batchLoad) {
        this.batchLoad = batchLoad;
    }

    /** Loads a batch of ids in ONE call. */
    public Map<K, V> loadAll(List<K> ids) {
        Map<K, V> result = new java.util.HashMap<>();
        List<K> missing = new java.util.ArrayList<>();

        for (K id : ids) {
            V cached = cache.get(id);
            if (cached != null) {
                result.put(id, cached);
            } else if (!missing.contains(id)) {
                missing.add(id);
            }
        }

        if (!missing.isEmpty()) {
            Map<K, V> loaded = batchLoad.apply(missing);
            loaded.forEach(cache::putAll);   // ⚠ putAll is fine — the map is concurrent
            result.putAll(loaded);
        }
        return result;
    }

    /** A single lookup — but it must be preceded by a loadAll of the batch. */
    public V get(K id) {
        return cache.get(id);
    }
}
```

```java
// ✅ The usage — ONE query for N entities
Map<Long, Order> orders = loader.loadAll(userIds);
for (User user : users) {
    user.setOrders(orders.get(user.getId()).getItems());
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — `ReentrantLock` without `unlock()` in a `finally`

```java
lock.lock();
doWork();          // ❌ if this throws, the lock is never released → deadlock
```

**✅ FIX** — `try { … } finally { lock.unlock(); }`

---

### ❌ Pitfall 2 — `unlock()` without holding the lock

```java
lock.unlock();     // ❌ IllegalMonitorStateException if you do not hold it
```

**✅ FIX** — pair every `lock()` with exactly one `unlock()` in a `finally`.

---

### ❌ Pitfall 3 — `StampedLock` used reentrantly

```java
lock.readLock();
callAnotherReadLockedMethod();   // ❌ StampedLock is NOT reentrant → deadlock
```

**✅ FIX** — use `ReentrantReadWriteLock` if you need reentrancy.

---

### ❌ Pitfall 4 — Upgrading a read lock to a write lock

```java
readLock.lock();
writeLock.lock();      // ❌ NOT supported → deadlock
```

**✅ FIX** — release the read lock, acquire the write lock, then **re-check** the condition.

---

### ❌ Pitfall 5 — Using `AtomicInteger` for a high-contention counter

```java
private final AtomicLong requests = new AtomicLong();   // ❌ every thread hits one cache line
```

**✅ FIX** — `LongAdder`.

---

### ❌ Pitfall 6 — A raw `get`-then-`put` on a `ConcurrentHashMap`

```java
if (map.get(k) == null) { map.put(k, compute(k)); }   // ❌ a check-then-act race
```

**✅ FIX** — `computeIfAbsent(k, this::compute)`.

---

### ❌ Pitfall 7 — `CopyOnWriteArrayList` written in a loop

```java
for (int i = 0; i < 100_000; i++) list.add("x");   // ❌ O(n²) — 10 billion copies
```

**✅ FIX** — `CopyOnWriteArrayList` is for read-mostly, small lists. Use a `ConcurrentLinkedQueue` for append-heavy.

---

### ❌ Pitfall 8 — Trusting `ConcurrentHashMap`'s iterator for a consistent snapshot

```java
for (Map.Entry<K,V> e : map.entrySet()) { … }   // ⚠ weakly consistent — it may miss concurrent updates
```

**✅ FIX** — for a consistent snapshot, take a lock or copy explicitly. The iterator is *safe*, not *consistent*.

---

### ❌ Pitfall 9 — Storing `null` in a `ConcurrentHashMap`

```java
map.put("k", null);   // ❌ NullPointerException
```

**✅ FIX** — use a sentinel or `Optional`.

---

### ❌ Pitfall 10 — Assuming `AtomicInteger` gives you ordering with other fields

```java
private final AtomicInteger count = new AtomicInteger();
private boolean flag;      // ⚠ the count's ordering does NOT order the flag
```

**✅ FIX** — use one mechanism for the whole group of fields, or make the group immutable and publish it atomically.

---

### ❌ Pitfall 11 — A `long`/`double` atomic without the specialisation

```java
private final AtomicLong x = new AtomicLong();     // ✅ fine
private final AtomicDouble d = new AtomicDouble(); // ✅ exists, but slow
```

**✅ FIX** — for a `double` counter, use a `LongAdder` with fixed-point arithmetic, or `DoubleAdder` (Java 8+).

---

### ❌ Pitfall 12 — `AtomicReference` holding a mutable object

```java
private final AtomicReference<List<String>> items = new AtomicReference<>(new ArrayList<>());  // ❌ the list is still mutable
```

**✅ FIX** — hold an **immutable** value and swap the reference.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is the difference between `synchronized` and `ReentrantLock`?**
**A:** `ReentrantLock` adds four capabilities `synchronized` lacks: a **non-blocking `tryLock()`**, a **timeout** (`tryLock(time, unit)`), **interruptibility** (`lockInterruptibly()`), **fair** ordering, and **multiple `Condition`s**. The cost is that you must `unlock()` in a `finally` yourself — `synchronized` does it automatically.

**Q: What is CAS?**
**A:** Compare-and-swap — an atomic instruction that updates a value **only if** it still holds the expected value. It is the basis of the `Atomic*` classes: they read the value, compute a new one, and CAS it in, **retrying** if another thread won the race. It never blocks, so it scales better than a lock — until the retry rate gets high.

**Q: What is `AtomicInteger` used for?**
**A:** Atomically incrementing, decrementing, and comparing a single integer — the lock-free alternative to `synchronized` for a simple counter or a state flag. It provides atomicity *and* visibility for that one variable, but **not** ordering with respect to other variables.

**Q: What is the difference between `AtomicInteger` and `LongAdder`?**
**A:** `AtomicLong` keeps a single value, so all contending threads hit the **same cache line** and the CAS retries grow under load. `LongAdder` uses **contention striping** — each thread updates its own `Cell` — so there is no cache-line contention, at the cost of a more expensive `sum()`.

**Q: Is `ConcurrentHashMap` thread-safe?**
**A:** Yes — **lock-free reads** and **per-bin write locks** in Java 8+. It **forbids null keys and values** (so `get() == null` unambiguously means "absent"), and its iterators are **weakly consistent** (safe, but not a point-in-time snapshot).

### 🟡 Intermediate

**Q: What is the ABA problem and how do you solve it?**
**A:** In a CAS loop, the value can change A → B → A between your read and your write, so the CAS succeeds even though the value was modified. It matters for lock-free stacks and queues, and for any CAS over a **mutable** value. The fix is `AtomicStampedReference`, which compares a (reference, version) pair atomically, so any intervening modification makes the stale CAS fail. For a simple counter it is irrelevant.

**Q: When does CAS lose to a lock?**
**A:** When contention is high enough that the **retry rate** climbs. Each retry is wasted work, and the repeated CAS invalidates the cache line for every core. At that point, blocking on a lock (or `LongAdder`'s striping, or pushing the atomicity into the database with `UPDATE t SET c = c + 1`) is cheaper. The lesson: **lock-free does not mean faster** — it means *no blocking*, which is only a win while the retry rate stays low.

**Q: What is the difference between `ReentrantReadWriteLock` and `StampedLock`?**
**A:** `ReentrantReadWriteLock` is **reentrant** and supports a **write → read downgrade**, but not an upgrade. `StampedLock` supports **optimistic reads** (a stamp you validate afterwards — no lock at all on the fast path), which is much faster for read-mostly data, but it is **not reentrant** and does **not** support an upgrade. Because of the reentrancy trap, many teams prefer the read/write lock for its safety.

**Q: Why does `ConcurrentHashMap` not allow null values?**
**A:** Because `get(key)` returns `null` for an absent key — a `null` **value** would make "absent" and "present with null" indistinguishable. The same ambiguity breaks `computeIfAbsent` and `merge`: they cannot tell "the mapping function returned null (remove the entry)" from "there was no entry". `HashMap` does not have this problem because it is not designed for concurrent use.

**Q: What is the cost model of `CopyOnWriteArrayList`?**
**A:** Every **write** is O(n) — it copies the whole backing array. Every **read** is O(1) and lock-free, and the **iterator is a snapshot** (so it never throws `ConcurrentModificationException` and never sees a torn update). It is correct for a **read-mostly, small** list — event listeners, feature flags — and catastrophic for a write loop (O(n²)).

**Q: Why use `Condition` instead of `wait`/`notifyAll`?**
**A:** `synchronized` has a **single** wait set, so `notifyAll` wakes every waiter — including producers that cannot proceed — and they re-check and sleep again. A `ReentrantLock` can have **multiple `Condition`s**, so `notEmpty.signal()` wakes exactly one consumer and `notFull.signal()` wakes exactly one producer: only the threads that can make progress are woken.

### 🔴 Advanced / Senior

**Q: Explain the `ConcurrentHashMap` write path in Java 8.**
**A:** For an empty bin, the writer uses a **CAS** on the table slot — no lock at all. For an occupied bin, it uses `synchronized` **on the bin's head `Node`**, and it holds that lock while walking or restructuring the chain. Because the lock is on the *node object* (not on the table, and not on a segment as in Java 7), contention is **per-bin**: two threads writing to different bins never interact, so the concurrency ceiling is effectively unlimited rather than the 16 segments of Java 7. The head-node lock is also re-entrant for a thread already inside that bin, and the tree-ification thresholds (8/64/6) are the same as `HashMap`'s.

**Q: How would you implement a lock-free work queue?**
**A:** The classic design is a **Michael-Scott** style queue built on an `AtomicReference` to an immutable node: `push` does a CAS on the head, `pop` does a CAS on the tail, and a node is "removed" by CASing a `next` pointer to a sentinel rather than mutating it. The critical detail is that **nodes are immutable** — you never mutate a node after publishing it — which is what makes the CAS safe. The `ConcurrentLinkedQueue` in the JDK is exactly this design. The hard part is the ABA problem on the head/tail pointers, which the JDK solves with careful ordering and, in the tail case, by accepting a small delay in helping the lagging thread. The practical advice: **do not write your own** — use `ConcurrentLinkedQueue` or `LinkedBlockingQueue`, and only reach for a custom lock-free structure if you have measured a specific bottleneck.

**Q: When is `LongAdder` the wrong choice?**
**A:** Three cases. (1) **You need the exact current value frequently** — `sum()` is O(cells), so reading it in a hot path is much more expensive than `AtomicLong.get()`. (2) **Low contention** — the `LongAdder` has extra indirection (a hash of the thread id, a cell lookup) that `AtomicLong` does not, so at low contention `AtomicLong` is marginally faster. (3) **You need to CAS the value** — `LongAdder` has no `compareAndSet`; use `AtomicLong` or `LongAccumulator` (which supports a `compareAndSet`-like `reset`/`accumulate` model with a custom identity function). The rule: **`LongAdder` for a write-only, high-contention counter; `AtomicLong` when you read or CAS the value.**

**Q: How do you make a fair, starvation-free design with a read/write lock?**
**A:** Use the **fair** constructor: `new ReentrantReadWriteLock(true)`. The non-fair policy allows a *reader* that arrives while a writer is queued to barge ahead — which is a throughput win but means a **continuous stream of readers can starve a writer indefinitely**. The fair policy queues both readers and writers in arrival order, eliminating starvation at a throughput cost. There is a third option: a **phased** or **writer-preference** policy, implemented with a `Condition` and an `AtomicInteger` of waiting writers, so that once a writer is waiting, new readers block. Some production systems use that explicit writer-preference approach rather than the built-in fair lock, because it lets *readers* keep barging while there is no waiting writer, and blocks them only when there is.

### ⚫ Expert / Principal

**Q: The JMM says a `volatile` write cannot be reordered with a `volatile` read. But does that make a `volatile` counter correct? Where exactly does the reasoning break?**
**A:** The break is in a subtle and important place: the JMM guarantees **ordering** of the *accesses*, not **atomicity** of the *compound operation*. `counter++` is three separate actions — a read, an add, and a write. The volatile write at the end is ordered after the read, and the volatile read at the start is ordered before the write, so the *program order* is preserved and the value is *visible*. But **nothing prevents two threads from interleaving between the read and the write**: T1 reads 0, T2 reads 0, T1 writes 1, T2 writes 1. The final value is 1, not 2. The JMM is doing its job perfectly — the *program* is wrong. The fix requires a mechanism that makes the *sequence* indivisible: `synchronized` (a mutual-exclusion critical section), an atomic class (a single CAS instruction), `LongAdder` (striping), or — best of all — pushing the atomicity into the database (`UPDATE t SET c = c + 1`). The deeper lesson: **ordering is a property of the memory model; atomicity is a property of the algorithm.** Conflating them is the root of most concurrency bugs, and it is why "make it volatile" is such a common and dangerous half-fix.

**Q: How would you design a lock-free, wait-free, bounded queue for a low-latency trading system, and what are the trade-offs?**
**A:** A **bounded, array-based ring buffer** with a single-producer/single-consumer design is the classic answer: a plain `long` index for the head and one for the tail, with `getUnsafe().getObject`/`putOrderedObject` (or, in modern Java, `VarHandle` with `getAcquire`/`setRelease` and careful padding to avoid false sharing). Each slot is a pre-allocated object; the queue never allocates after construction, and the producer/consumer never block — they spin or park. The **memory ordering** is the whole design: the producer writes the element then publishes the new tail with a *release*; the consumer reads the tail with an *acquire* and then the element — that pairing is what guarantees the element is visible. For multiple producers/consumers you need either a CAS on the tail (which is *lock-free*, not wait-free) or per-producer lanes (which is wait-free but complicates ordering). The trade-offs: **wait-free** (bounded latency per operation, the strongest guarantee) is much harder to achieve than lock-free (some operation eventually succeeds); the wait-free version needs per-thread lanes or careful helping; the lock-free version can suffer from **livelock** (threads repeatedly retry) and from **cache-line contention** on the head/tail. The honest answer for most systems: a `Disruptor` (which is a ring buffer with exactly this design) or a JDK `ArrayBlockingQueue` with a single lock, with the lock's fairness tuned — and only a hand-written wait-free ring buffer if the measured latency distribution demands it. The engineering judgement: **correctness and operability first; the exotic algorithms only when a measurement says so.**

---

## 9. Counter-Questions & Traps

### CQ1. "You said `LongAdder` is faster under contention. But it's not atomic — isn't that a problem?"

- **Naive:** "It is atomic per cell, but the sum is not a consistent snapshot."  ← *true, and it misses the design intent*
- **The trap:** The trap is to treat "not atomic" as a defect. `LongAdder` is **not** a replacement for `AtomicLong` — it is a replacement for a *counter that is written often and read rarely*. Its lack of atomicity across the whole sum is a deliberate trade.
- **Expert answer:** The precise contract: `LongAdder.increment()` is **atomic for that increment** (each cell's `add` is a CAS, so no update is lost), and `sum()` returns a value that is *approximately* the total at some point during the summation — it may miss increments that happen concurrently, and it is not a linearizable snapshot. That is fine for a **metrics counter**: you export it every 10 seconds and a ±0.01% error is irrelevant. It is **not** fine for a counter you use in a *decision* ("if the counter == exactly 100, do X"). The rule: **`LongAdder` for telemetry; `AtomicLong` when you need the exact value or a CAS.** And the related design point: `LongAdder` also gives up the ability to `compareAndSet`, so if your logic depends on the exact value, it is the wrong tool.

### CQ2. "`synchronized` is simpler than `ReentrantLock`. Why would you ever use the lock?"

- **Naive:** "For timeouts and interruptibility."  ← *true, but it starts with the features and misses the decision*
- **The trap:** The trap is to answer with a feature list. The better answer starts from **what `synchronized` cannot express at all**, and the deepest of those is the **single wait set**. `synchronized` has exactly one `Object.wait()` queue per monitor, so you cannot distinguish "waiting for space" from "waiting for an item" — every signal wakes everyone.
- **Expert answer:** The four situations, ordered by how often they actually come up. (1) **Multiple conditions** — the bounded buffer. With `synchronized` you must use `notifyAll()` and accept the spurious wakeups; with `Condition`s you wake exactly the threads that can proceed. (2) **A timeout** — "try for 100 ms, then give up" is *inexpressible* with `synchronized`, and it is the foundation of every circuit breaker and every "fail fast rather than queue forever" policy. (3) **Interruptibility** — `synchronized` acquisition is *not* interruptible: a thread blocked on a monitor cannot be cancelled, which breaks graceful shutdown. (4) **Fairness** — the non-fair policy can starve a thread indefinitely. And the honest closing: **if you need none of these, use `synchronized`** — it is simpler, it cannot be used incorrectly (the lock is always released), and the JIT can elide it entirely when the object does not escape. The engineering judgement is: *reach for the lock when the language cannot express what you need, not because it is "more modern".*

### CQ3. "If `StampedLock` is faster than `ReentrantReadWriteLock`, why doesn't everyone use it?"

- **Naive:** "Because it's newer and less known."  ← *true, but it misses the real reasons*
- **The trap:** The trap is to miss the two **hard constraints** that make `StampedLock` risky in production code. It is **not reentrant** — and reentrancy is not an exotic property, it is what makes a lock *composable*. And it does **not** support an upgrade, so the "check under a read lock, then write" pattern silently deadlocks.
- **Expert answer:** The three real reasons. (1) **Not reentrant** — a read-locked method that calls another read-locked method **deadlocks immediately**. This is not a rare pattern: it is what happens when you have a public locked method delegating to a private locked helper — the exact pattern that `synchronized` and `ReentrantReadWriteLock` both support. (2) **The optimistic read is easy to misuse** — if you forget to `validate()` the stamp, you will read torn data with no error; and the fallback path must re-read *everything*, not just the failed field. (3) **The API is harder to use correctly** — `unlockRead(stamp)` requires the stamp, and mixing up the read and write unlocks is a runtime `IllegalMonitorStateException`. The balanced answer: `StampedLock` is **excellent** for a hot, read-mostly, single-purpose data structure (a snapshot cache, a metrics block) where the code is written once and reviewed carefully — and **risky** as a general-purpose lock in a large codebase. The engineering judgement: *use `ReentrantReadWriteLock` by default; use `StampedLock` when you have measured that the read path is the bottleneck and the code is isolated.*

### CQ4. "Why does `ConcurrentHashMap` forbid null values? `HashMap` allows them."

- **Naive:** "Because it's concurrent."  ← *true, and it misses the reason*
- **The trap:** The trap is to treat it as an arbitrary restriction. The reason is **semantic**: `ConcurrentHashMap` provides **atomic per-key operations** — `computeIfAbsent`, `merge`, `putIfAbsent`, `replace(k, old, new)` — and every one of them needs to be able to express "there is no value" *unambiguously*.
- **Expert answer:** The precise ambiguity: `get(k)` returns `null` for an absent key. If a `null` *value* were allowed, then `get(k) == null` would mean either "absent" or "present with null" — and `putIfAbsent(k, v)` would have to decide whether to insert when the key is present-with-null. `computeIfAbsent` is worse: the mapping function may legitimately return `null`, which the contract defines as "remove the mapping" — so a `null` return and an absent key become indistinguishable, and the operation becomes undecidable. `HashMap` has no such operations, so it can afford the ambiguity. The general design lesson: **once you add atomic compound operations to a collection, you must remove the "absent" ambiguity from its value space.** The same reasoning explains why `ConcurrentHashMap` also rejects a **null key** (a null key would need a defined hash and a defined position). And the practical advice: if you genuinely need to represent "present with no value", use a **sentinel** or an `Optional`-like wrapper as the value type.

### CQ5. "If `CopyOnWriteArrayList` is so good for read-mostly lists, why not use it everywhere?"

- **Naive:** "Because the writes are O(n)."  ← *true, but it does not give the decision rule*
- **The trap:** The trap is to stop at the complexity. The question is: **how often do you write, and how big is the list?** The answer is a *product*, and the product is what matters — a list of 10 items written 100 times a second is 1,000 copies a second, which is trivial; a list of 100,000 items written once a minute is 100,000 copies a minute, which is not.
- **Expert answer:** The decision rule: `CopyOnWriteArrayList` is right when **the write count × the list size is small** — typically a list of a few dozen configuration or listener entries, changed rarely. It is wrong when either factor is large. The other two costs to name: (1) **memory churn** — every write allocates a whole new array, which is GC pressure; (2) **`contains` is O(n)** — it does a linear scan, not a hash lookup, so a "set-like" use of it is O(n) per lookup. And the right alternatives: for a **write-heavy** list, `ConcurrentLinkedQueue` (lock-free, no copying) or a `synchronizedList`; for a **set** of enum-like values, `EnumSet`; for a **map**, `ConcurrentHashMap`. The design lesson: **the copy-on-write pattern is a memory-for-time trade** — and like every such trade, it is only correct when the time you save is worth the memory you spend.

### CQ6. "You said CAS never blocks. Isn't that always better than a lock?"

- **Naive:** "Yes, lock-free is always faster."  ← *false, and the reason is the interesting part*
- **The trap:** The trap is to treat "lock-free" as a synonym for "faster". Lock-free guarantees **progress** (some thread always makes progress) — it does **not** guarantee **low latency** or **high throughput**. A CAS loop under heavy contention can spin, retry, and burn an unbounded amount of CPU while making *no* useful progress on that particular operation.
- **Expert answer:** The three properties, and the difference matters. **Lock-free**: the system as a whole always makes progress (some operation completes) — but *your* operation may retry forever. **Wait-free**: *your* operation completes in a bounded number of steps — the strongest guarantee, and the hardest to achieve. **Obstruction-free**: progress is only guaranteed when no other thread is interfering. The performance consequence: under high contention, a CAS loop's **retry rate** climbs, each retry is wasted work, and the repeated CAS invalidates the cache line for every core — so the *throughput* can be worse than a lock, which simply parks the losers. This is exactly why `LongAdder` exists: it removes the sharing rather than making the retry cheaper. The engineering rule: **use a lock when the contention is high enough that the retry rate is the bottleneck; use CAS when the contention is low or the critical section is a single instruction.** And the deeper principle: *"lock-free" is a liveness property, not a performance property* — a distinction almost every candidate gets wrong.

### CQ7. "How is `AtomicInteger.incrementAndGet()` implemented? Is it a CAS loop?"

- **Naive:** "Yes, it compares and swaps in a loop."  ← *true, and it misses the hot-path optimisation*
- **The trap:** The trap is to assume the *default* path is a CAS loop. On a modern x86, the JDK uses an **intrinsic** — a specialised instruction (`lock xadd` / `lock xaddl`) that performs the read-modify-write **in one atomic instruction**, with no loop and no retry. The CAS loop is the *portable fallback* for platforms without the instruction.
- **Expert answer:** The precise mechanism: `AtomicInteger` uses `Unsafe`/`VarHandle` operations that the JIT **intrinsifies** into a single CPU instruction where one exists (`lock xadd` on x86, `ldaddal` on ARMv8). The CAS loop you see in the source is the generic path — used on platforms with no such instruction, and by `compareAndSet` (which has no single-instruction equivalent). The performance consequence: `incrementAndGet()` on x86 is **one atomic instruction with no retry**, so it is extremely fast — and that is why the "contention" problem with `AtomicLong` is not the *instruction* but the **cache line**: every core's increment invalidates the same line. The fix (`LongAdder`) is therefore about **memory layout**, not about the instruction. The interview payoff: knowing that `incrementAndGet` is a single instruction on x86, but that the bottleneck is the shared cache line, separates a candidate who has read the JDK from one who has only read a blog post.

### CQ8. "Why is `ReentrantReadWriteLock` fair mode so much slower? Isn't it just a queue?"

- **Naive:** "Because of the extra bookkeeping."  ← *true, and it misses the mechanism*
- **The trap:** The trap is to think the cost is the bookkeeping. The cost is that **fairness changes the *scheduling* behaviour**: a fair lock hands the lock to the next waiter in queue order, and every handoff is a **context switch** — the current thread must be parked and the next one woken. The non-fair policy lets the current thread **barge** (re-acquire immediately) and lets a thread that has been waiting jump the queue, so the common case is a fast, cache-warm, uncontended acquisition with no context switch at all.
- **Expert answer:** The deeper reason: the non-fair policy is *optimised for throughput*, and it is *correct* — a lock does not promise fairness, only mutual exclusion. The fair policy is optimised for **bounded wait**, and the price is a context switch on every handoff, plus the bookkeeping of maintaining the queue. The decision rule: **use fair only when you can name the thread that would starve** — typically a low-priority worker that must make progress against a stream of high-priority requests. And the observation worth making: **starvation is rare in practice** because a thread that is waiting is usually one that just released the lock, and the non-fair barging policy favours the *most recent* participant, which is usually the one making progress. The alternative to fairness that is often better: **explicit priority** — a `ReentrantLock` plus a priority ordering you control, or a `PriorityBlockingQueue` feeding a fixed pool.

### CQ9. "If `AtomicReference` gives you atomic reference swaps, isn't that the best way to publish shared state?"

- **Naive:** "Yes, it's the lock-free way to publish state."  ← *true, and it needs a critical caveat*
- **The trap:** The trap is to forget that the **reference** is atomic but the **object it points to** is not. `AtomicReference.set(someMutableObject)` publishes the reference safely, but if `someMutableObject` is still being constructed on another thread, the reader can see a **partially constructed** object — the same bug as publishing a non-volatile reference, just with an extra layer.
- **Expert answer:** The rule: **an `AtomicReference` (or a `volatile` field) is only safe for publishing an object that is already fully constructed and immutable.** If the object is mutable and under construction, you have two options: (1) make it **immutable** (`final` fields, no setters) so the final-field freeze rule applies — the reader is guaranteed to see the initialised values; or (2) publish it through a **`CompletableFuture`**, whose completion happens-before the dependent action, so the "publish" and the "construct" are one atomic step from the reader's point of view. The pattern in production is therefore: *construct immutably, then swap the reference*. That is exactly the design of the `LatestValue` class in §6, and of every "configuration hot-reload" implementation — the new configuration is built completely, then swapped in atomically, so a reader sees either the whole old one or the whole new one, never a mixture.

### CQ10. "How do you test a lock-free data structure? It's non-deterministic by definition."

- **Naive:** "Run it many times."  ← *the reality, and it is unsatisfying*
- **The trap:** The trap is to treat "run it a lot" as a testing *strategy*. It is a *smoke test*. A lock-free structure's bugs are in the **interleavings you did not hit**, so a test that runs the same schedule 10,000 times may never explore the one interleaving that corrupts the structure.
- **Expert answer:** The proper answer has three parts. (1) **jcstress** (the OpenJDK Concurrency Stress Tests harness) — the key idea is not "run it a lot" but **enumerate the interesting interleavings systematically** and assert that only the *legal* outcomes occur. The test states "this outcome must never happen", which is exactly the shape of a concurrency bug. (2) **A model-based test**: implement the same structure with a simple, obviously-correct `synchronized` version, run both with the same random operation sequence, and assert the results are equal. This catches the vast majority of bugs and is easy to write. (3) **A linearizability checker**: record the operations with their invocation and response times, and check that *some* sequential order is consistent with the observed results — this is what tools like `Knossos` do, and it turns a flaky failure into a reproducible, *minimal* counterexample. The engineering discipline for a team: (a) every concurrent structure gets a **model-based** test against a simple reference implementation; (b) the stress harness runs in a dedicated CI job on a many-core machine; and (c) the assertion is on an **invariant** ("the count never goes negative", "the queue never loses an element"), not on an exact value.

---

## 10. Rapid-Fire MCQs

**Q1.** `ReentrantLock` adds over `synchronized`…
- A) Faster locking  B) `tryLock`, timeouts, interruptibility, fairness, multiple conditions  C) Automatic unlocking  D) Virtual threads

**Q2.** `ReentrantLock` must be unlocked…
- A) Immediately after `lock()`  B) In a `finally` block  C) Never  D) By the GC

**Q3.** `tryLock(timeout, unit)` returns…
- A) `void`  B) `true` if acquired within the timeout  C) The lock  D) Throws

**Q4.** `ReentrantReadWriteLock` supports a **write → read downgrade**?
- A) Yes  B) No  C) Only in fair mode  D) Only for `StampedLock`

**Q5.** `ReentrantReadWriteLock` supports a **read → write upgrade**?
- A) Yes  B) No — it would deadlock  C) Only with a timeout  D) Only in fair mode

**Q6.** `StampedLock` is reentrant?
- A) Yes  B) **No**  C) Only for reads  D) Only in fair mode

**Q7.** `StampedLock`'s optimistic read requires…
- A) A lock  B) A `validate(stamp)` check afterwards  C) `unlockRead`  D) Fairness

**Q8.** CAS stands for…
- A) Check And Set  B) Compare-And-Swap  C) Concurrent Atomic Synchronize  D) Compare-And-Store

**Q9.** The ABA problem is solved by…
- A) `synchronized`  B) `AtomicStampedReference`  C) `volatile`  D) A `ReentrantLock`

**Q10.** `LongAdder` uses which technique to scale?
- A) A `synchronized` block  B) **Contention striping** (a `Cell[]`)  C) A `ReadWriteLock`  D) Copy-on-write

**Q11.** `LongAdder.sum()` is…
- A) O(1)  B) O(number of cells)  C) O(n) where n is the count  D) Lock-free

**Q12.** `CopyOnWriteArrayList.add()` is…
- A) O(1)  B) O(n) — it copies the backing array  C) O(log n)  D) Amortised O(1)

**Q13.** `CopyOnWriteArrayList`'s iterator is…
- A) Fail-fast  B) A **snapshot**  C) Locked  D) Weakly consistent

**Q14.** `ConcurrentHashMap` (Java 8+) locks…
- A) The whole table  B) A segment  C) The **head node of the affected bin**  D) Nothing

**Q15.** `ConcurrentHashMap` allows a null value?
- A) Yes  B) No  C) Only one  D) Only in Java 7

**Q16.** `ConcurrentHashMap.computeIfAbsent` runs the mapping function…
- A) Once per key, atomically  B) Once per call  C) Once per thread  D) Never

**Q17.** `ConcurrentHashMap`'s iterators are…
- A) Fail-fast  B) Weakly consistent  C) A snapshot  D) Locked

**Q18.** `AtomicInteger.incrementAndGet()` on x86 compiles to…
- A) A CAS loop  B) A single atomic instruction (`lock xadd`)  C) A `synchronized` block  D) A `volatile` write

**Q19.** `AtomicLong` performs badly under contention because of…
- A) The CAS retry loop alone  B) **Cache-line contention** on the single value  C) The `volatile` read  D) Object allocation

**Q20.** `BlockingQueue` is a `ConcurrentLinkedQueue` plus…
- A) Nothing  B) Blocking put/take with a capacity (back-pressure)  C) A `StampedLock`  D) Copy-on-write

**Q21.** A `Condition` differs from `Object.wait()` because…
- A) It is faster  B) A lock can have **multiple** wait sets  C) It cannot time out  D) It does not release the lock

**Q22.** `AtomicReference` is safe for publishing…
- A) Any object  B) An **immutable, fully constructed** object  C) A mutable object  D) Nothing

**Q23.** `AtomicIntegerFieldUpdater` lets you update…
- A) A static field  B) A `volatile` field of another class, without a wrapper object  C) A final field  D) An array element

**Q24.** `LongAccumulator` differs from `LongAdder` because it…
- A) Is faster  B) Supports a custom accumulator function  C) Is atomic  D) Uses a `ReadWriteLock`

**Q25.** The safest way to publish a mutable configuration object to many threads is to…
- A) Make the field `volatile`  B) Build it immutably, then swap the reference atomically  C) Use `synchronized` on every read  D) Use `ThreadLocal`

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | Those five capabilities are exactly what `synchronized` cannot express. |
| 2 | **B** | Always in a `finally` — otherwise an exception leaks the lock forever. |
| 3 | **B** | `true` if the lock was acquired within the timeout, `false` otherwise. |
| 4 | **A** | Downgrading is supported — release the write lock, then take the read lock. |
| 5 | **B** | An upgrade would deadlock — it is not supported. |
| 6 | **B** | `StampedLock` is **not** reentrant — re-acquiring deadlocks. |
| 7 | **B** | The stamp must be validated; a failed validation forces a re-read under a read lock. |
| 8 | **B** | Compare-and-swap — the atomic read-modify-write instruction. |
| 9 | **B** | `AtomicStampedReference` compares a (reference, version) pair. |
| 10 | **B** | Contention striping — each thread updates its own `Cell`. |
| 11 | **B** | It scans the cells (plus the base) — not a single volatile read. |
| 12 | **B** | Every write copies the whole backing array. |
| 13 | **B** | A snapshot — it never throws `ConcurrentModificationException`. |
| 14 | **C** | Only the affected bin's head node is locked — so contention is per-bin. |
| 15 | **B** | No nulls — `get() == null` must unambiguously mean "absent". |
| 16 | **A** | At most once per key, atomically — the fix for the double-create race. |
| 17 | **B** | Weakly consistent — safe, but not a point-in-time snapshot. |
| 18 | **B** | A single `lock xadd` instruction — the CAS loop is the portable fallback. |
| 19 | **B** | All the threads hammer the **same cache line**, so it ping-pongs between cores. |
| 20 | **B** | Bounded + blocking = back-pressure; a `ConcurrentLinkedQueue` is unbounded and non-blocking. |
| 21 | **B** | A `ReentrantLock` can create several `Condition`s — one per wait state. |
| 22 | **B** | The reference is atomic; the object's contents must be immutable to be safe. |
| 23 | **B** | It updates a `volatile` field directly, avoiding a wrapper object per instance. |
| 24 | **B** | It takes a custom `LongBinaryOperator` accumulator (e.g. max, not sum). |
| 25 | **B** | Construct it fully and immutably, then swap the reference — a reader sees the old or the new, never a mixture. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE LOCK / ATOMIC SELECTION TABLE                    │
├──────────────────────────────┬───────────────────────────────────────────┤
│ SITUATION                    │ USE                                       │
├──────────────────────────────┼───────────────────────────────────────────┤
│ Simple mutual exclusion      │ synchronized  (the JIT can elide it)     │
│ Timeout / interruptible /    │ ReentrantLock                             │
│  fair / multiple conditions  │                                             │
│ Many readers, one writer     │ ReentrantReadWriteLock                    │
│ Hot read-mostly data         │ StampedLock (optimistic reads)            │
│ One value, atomically updated│ AtomicInteger / AtomicReference           │
│ High-contention counter      │ LongAdder  (contention striping)          │
│ Read-mostly small list       │ CopyOnWriteArrayList                      │
│ Write-heavy queue            │ ConcurrentLinkedQueue                     │
│ Bounded producer/consumer    │ BlockingQueue  (back-pressure)           │
│ Shared map                   │ ConcurrentHashMap                         │
│ "Create once per key"        │ computeIfAbsent (atomic)                  │
│ Publish a whole object       │ AtomicReference + an immutable value      │
└──────────────────────────────┴───────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                     THE 4 ATOMIC FAMILIES AND THEIR TRADE-OFFS          │
├───────────────────┬────────────┬──────────────┬──────────────────────────┤
│ CLASS             │ READ COST  │ WRITE COST   │ USE FOR                  │
├───────────────────┼────────────┼──────────────┼──────────────────────────┤
│ AtomicInteger     │ O(1)       │ CAS retry    │ a counter you also read  │
│ LongAdder         │ O(cells)   │ O(1) local   │ a HOT counter (metrics)  │
│ AtomicReference   │ O(1)       │ CAS retry    │ publishing whole objects │
│ LongAccumulator   │ O(cells)   │ O(1) local   │ max/min style counters   │
├───────────────────┼────────────┼──────────────┼──────────────────────────┤
│ ⚠ THE ATOMIC CLASSES GIVE ATOMICITY FOR **ONE** VARIABLE.              │
│   They do NOT give ordering with respect to OTHER variables.            │
│   → for a GROUP of fields, use one immutable object + an atomic swap.    │
└───────────────────┴────────────┴──────────────┴──────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 10 CONCURRENCY TRAPS                              │
├──────────────────────────────────────────────────────────────────────────┤
│  1. lock() without unlock() in a finally   → the lock leaks            │
│  2. volatile counter++                     → still a race              │
│  3. StampedLock used reentrantly           → deadlock                   │
│  4. read → write lock upgrade              → deadlock                   │
│  5. AtomicLong for a hot counter           → cache-line contention      │
│  6. get-then-put on a ConcurrentHashMap    → a check-then-act race      │
│  7. CopyOnWriteArrayList written in a loop → O(n²)                      │
│  8. Expecting a CHM iterator snapshot      → it is weakly consistent    │
│  9. A null value in a ConcurrentHashMap    → NPE                        │
│ 10. AtomicReference of a MUTABLE object    → a torn publication        │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The selection checklist

```text
  ☐ Do I need a TIMEOUT or INTERRUPTIBILITY?  → ReentrantLock
  ☐ Do I need MULTIPLE WAIT SETS?             → ReentrantLock + Conditions
  ☐ Is the read path the measured bottleneck?  → StampedLock (optimistic)
  ☐ Is it a SINGLE value?                     → Atomic* / LongAdder
  ☐ Is it a HOT counter read only rarely?      → LongAdder
  ☐ Is it a small, read-mostly LIST?          → CopyOnWriteArrayList
  ☐ Is it a shared MAP?                       → ConcurrentHashMap
  ☐ Do I need "create once per key"?          → computeIfAbsent
  ☐ Is it a producer/consumer handoff?        → BlockingQueue
  ☐ NONE of the above?                        → synchronized ✅
```

### Production patterns

| Pattern | Implementation |
|---|---|
| A metrics counter | `LongAdder` — never read it in the hot path |
| A rate limiter | A token bucket on an `AtomicReference` (see §6.1) |
| A feature-flag store | An `AtomicReference<ImmutableFlags>` — swap, do not mutate |
| A request-scoped cache | `ConcurrentHashMap` with `computeIfAbsent` for the create |
| A bounded work queue | `ArrayBlockingQueue` (bounded) or `LinkedBlockingQueue` |
| A "publish a whole object" | An immutable object + an `AtomicReference` swap |
| A per-thread sequence | `ThreadLocal` — with `remove()` in a `finally` |

### False sharing — the thing to check when a counter is slow

```java
// ⚠ If YOU write a striped counter yourself, pad it:
@Contended
static final class PaddedCell { volatile long value; }
```

```text
   ❌ WITHOUT PADDING
   ┌────┬────┬────┬────┐
   │ v1 │ v2 │ v3 │ v4 │  ← 4 cells on the SAME 64-byte cache line
   └────┴────┴────┴────┘     → updating v1 invalidates v2, v3, v4 💥

   ✅ WITH PADDING (@Contended)
   ┌────┬────┬────┬────┐
   │ v1 │PAD│ v2 │PAD│  ← each on its OWN line
   └────┴────┴────┴────┘
```

### Metrics worth watching

| Metric | What it tells you |
|---|---|
| CAS retry rate | Contention on an atomic |
| Lock wait time (`ThreadMXBean.getThreadInfo`) | Where the contention is |
| Contended lock count | A hot lock |
| Pool pending count | Saturation |
| `StampedLock` validation failures | Write activity on a read-mostly structure |

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The executor framework and work queues | [02 · Executor Framework](02-Executor-Framework.md) |
| Deadlock, livelock and false sharing | [04 · Concurrency Problems](04-Concurrency-Problems.md) |
| The JMM and the happens-before edges | [01 · Thread Basics](01-Thread-Basics.md) |
| `Atomic*` in the JDK source | `java.util.concurrent.atomic` — `AtomicInteger`, `LongAdder`, `StampedReference` |
| AQS | `java.util.concurrent.locks.AbstractQueuedSynchronizer` — the base of the JDK's locks |

---

**Next Chapter:** → [04 · Concurrency Problems](04-Concurrency-Problems.md)
**Previous Chapter:** ← [02 · Executor Framework](02-Executor-Framework.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
