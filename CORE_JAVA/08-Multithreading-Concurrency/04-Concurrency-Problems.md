# Multithreading & Concurrency - Problems & Synchronizers (In-Depth Masterclass)

## 1. Concurrency Hazards: Race Conditions, Deadlocks & Livelocks

Multi-threaded concurrent systems are prone to four primary hazards: **Race Conditions**, **Deadlocks**, **Livelocks**, and **Thread Starvation**.

### 1. Race Conditions: Check-Then-Act & Read-Modify-Write
A race condition occurs when program correctness depends on the non-deterministic relative timing or interleaving of threads.

#### Hazard A: Check-Then-Act
```java
// ❌ BROKEN: Time-Of-Check to Time-Of-Use (TOCTOU) Race Condition
if (!map.containsKey(key)) {          // Step 1: Check
    map.put(key, computeValue());     // Step 2: Act (Thread B can insert between 1 and 2!)
}

// ✅ CORRECT: Atomic composite operation
map.computeIfAbsent(key, k -> computeValue());
```

#### Hazard B: Read-Modify-Write
`count++` requires three machine operations: read `count` into register $\to$ increment register $\to$ write back to memory. Two threads reading simultaneously will both write the same incremented value, losing updates.

---

### 2. Deadlock Deep Dive & The 4 Coffman Conditions

A deadlock occurs when two or more threads are permanently blocked, each waiting for a lock held by another.

```
       Thread 1                  Thread 2
   ┌──────────────┐          ┌──────────────┐
   │ Holds Lock A │          │ Holds Lock B │
   └──────┬───────┘          └──────┬───────┘
          │ Waiting for             │ Waiting for
          ▼                         ▼
   ┌──────────────┐          ┌──────────────┐
   │    Lock B    │          │    Lock A    │
   └──────────────┘          └──────────────┘
```

#### The 4 Coffman Conditions (All 4 MUST hold simultaneously for deadlock to occur):
1. **Mutual Exclusion:** Resources cannot be shared; only one thread can hold a resource at a time.
2. **Hold and Wait:** A thread holds at least one resource while waiting to acquire additional resources held by others.
3. **No Preemption:** Resources cannot be forcibly taken away from a thread holding them; they must be released voluntarily.
4. **Circular Wait:** A closed chain of threads exists where each thread holds a resource requested by the next thread.

#### Breaking Coffman Conditions to Prevent Deadlock:
- **Break Circular Wait (Most Common):** Enforce a **global strict lock ordering**. If all threads acquire Lock A before Lock B, circular wait is impossible.
- **Break No Preemption:** Use `ReentrantLock.tryLock(timeout, unit)`. If a thread cannot acquire the second lock within the timeout, it releases the first lock and backs off.

#### Lock Ordering Tie-Breaking Pattern
When transferring money between two accounts, ordering by account ID prevents deadlocks:
```java
public void transferMoney(Account from, Account to, BigDecimal amount) {
    Account firstLock = from.getId() < to.getId() ? from : to;
    Account secondLock = from.getId() < to.getId() ? to : from;

    synchronized (firstLock) {
        synchronized (secondLock) {
            from.debit(amount);
            to.credit(amount);
        }
    }
}
```

#### Diagnosing Deadlocks in Production:
1. **Command Line:** Run `jstack <PID>` in terminal. The JVM automatically performs cycle-detection on intrinsic locks and displays:
   `Found 1 deadlock.` with full stack traces.
2. **Programmatic Detection:**
```java
ThreadMXBean bean = ManagementFactory.getThreadMXBean();
long[] deadlockedThreadIds = bean.findDeadlockedThreads();
if (deadlockedThreadIds != null) {
    ThreadInfo[] infos = bean.getThreadInfo(deadlockedThreadIds);
    // Alert on-call team!
}
```

---

### 3. Livelock vs Starvation

- **Livelock:** Threads continuously change their states in response to each other, but make zero forward progress (analogous to two polite people repeatedly stepping in the same direction to let each other pass in a hallway).
  - *Solution:* Introduce **randomized backoff jitter** before retrying lock acquisition.
- **Starvation:** A runnable thread is perpetually denied CPU execution or lock acquisition because other threads monopolize the resource.
  - *Causes:* Default unfair locks (`ReentrantLock(false)`), CPU priority skewing.
  - *Solution:* Enable fair queuing (`new ReentrantLock(true)`).

---

## 2. Advanced Concurrency Synchronizers

The `java.util.concurrent` package provides high-level coordination primitives built on top of AQS.

### Synchronizers Comparison Matrix

| Synchronizer | Reusable? | Coordination Model | Typical Production Use Case |
| :--- | :--- | :--- | :--- |
| **`CountDownLatch`** | ❌ No (One-time) | One or more threads wait until $N$ events occur | Application startup: waiting for DB, cache, and message queue to initialize |
| **`CyclicBarrier`** | ✅ Yes | $N$ threads wait for **each other** to reach a barrier | Multi-phase parallel simulation / matrix computations |
| **`Semaphore`** | ✅ Yes | Manages a fixed pool of $N$ permits | Database connection pool limits, API rate limiters |
| **`Phaser`** | ✅ Yes | Flexible barrier with dynamic registration of parties | Multi-stage batch processing pipelines with variable threads |
| **`Exchanger`** | ✅ Yes | Bidirectional atomic swap between exactly 2 threads | Double-buffering pipelines (producer fills, consumer drains) |

---

### 1. CountDownLatch Deep Dive
A countdown gate: threads calling `await()` block until the latch count reaches zero via `countDown()`.

```java
public class ServiceInitializer {
    public static void main(String[] args) throws InterruptedException {
        int services = 3;
        CountDownLatch latch = new CountDownLatch(services);

        ExecutorService executor = Executors.newFixedThreadPool(services);
        executor.submit(new InitTask("Database", 2000, latch));
        executor.submit(new InitTask("Cache", 1000, latch));
        executor.submit(new InitTask("Kafka", 1500, latch));

        // Main thread waits with timeout:
        boolean initialized = latch.await(5, TimeUnit.SECONDS);
        if (!initialized) {
            throw new RuntimeException("System initialization timed out!");
        }
        System.out.println("All services initialized! Ready to accept traffic.");
        executor.shutdown();
    }
}
```

---

### 2. CyclicBarrier Deep Dive
A synchronization point where a fixed number of threads (`parties`) must all arrive before any can proceed.

```java
public class MatrixSolver {
    public static void main(String[] args) {
        int parties = 3;
        // Barrier action runs when all parties arrive, executed by the LAST arriving thread!
        CyclicBarrier barrier = new CyclicBarrier(parties, () -> {
            System.out.println("=== Phase complete! Aggregating partial results ===");
        });

        for (int i = 0; i < parties; i++) {
            new Thread(new Worker(barrier)).start();
        }
    }
}
```

---

### 3. Semaphore Deep Dive (Rate Limiter / Pool Governor)
Controls concurrent access to a finite set of resources via permits.

```java
public class DatabaseConnectionPool {
    private final Semaphore semaphore;

    public DatabaseConnectionPool(int maxConnections) {
        this.semaphore = new Semaphore(maxConnections, true); // Fair semaphore
    }

    public void executeQuery(String sql) throws InterruptedException {
        semaphore.acquire(); // Acquires permit or blocks
        try {
            Connection conn = getConnection();
            conn.run(sql);
        } finally {
            semaphore.release(); // Returns permit
        }
    }
}
```

---

## 3. Concurrent Collections Under the Hood

### 1. ConcurrentHashMap Internals (Java 8+)
Unlike Java 7's segmented locking (`Segment[]`), Java 8+ `ConcurrentHashMap` uses a fine-grained synchronization scheme:

```
┌────────────────────────────────────────────────────────┐
│             ConcurrentHashMap Bucket Array             │
├────────────┬────────────┬────────────┬─────────────────┤
│ Bucket 0   │ Bucket 1   │ Bucket 2   │ Bucket 3        │
│ [ null ]   │ [ Node ]   │ [ Node ]   │ [ TreeBin ]     │
└────────────┼────────────┼────────────┼─────────────────┘
             │            │
             ▼            ▼
       CAS Insertion   synchronized(firstNode)
```

1. **Lock-Free Reads:** `get()` never acquires a lock. The `val` and `next` pointers in `Node` are declared **`volatile`**, ensuring instant memory visibility.
2. **CAS for Empty Buckets:** If `table[i] == null`, inserting a node uses lock-free `CAS` (`compareAndSetObject`).
3. **Synchronized Bucket Head:** If a collision occurs (`table[i] != null`), it synchronizes **only on the first `Node` of that bucket**:
   `synchronized (node)`
   Threads inserting into different buckets execute completely concurrently without blocking!
4. **TreeBin Escalation:** When a bucket chain exceeds 8 nodes and capacity $\ge 64$, the linked list converts to a Red-Black Tree (`TreeBin`).
5. **Concurrent Resizing (`transfer`):** Multiple threads collaborate to migrate elements from the old table to the new doubled table using `ForwardingNode` sentinels.
6. **Concurrent Counter (`CounterCell`):** `size()` uses cell striping (identical to `LongAdder`), avoiding a single volatile bottleneck.

> **Why are `null` keys and values strictly forbidden in `ConcurrentHashMap`?**
> In a concurrent environment, if `map.get(key)` returns `null`, you cannot tell whether the key is mapped to `null` or the key is not in the map. In single-threaded `HashMap`, you would disambiguate via `map.containsKey(key)`. But in `ConcurrentHashMap`, another thread could insert or delete the key between the `get()` call and the `containsKey()` call! To eliminate this race condition, `null` keys and values throw `NullPointerException`.

---

### 2. CopyOnWriteArrayList
- **Underlying structure:** `private transient volatile Object[] array;`
- **Writes:** Mutating operations (`add`, `set`, `remove`) acquire an internal lock (`ReentrantLock`), allocate a clone of the entire array with length $\pm 1$, modify the clone, and set the volatile `array` pointer.
- **Reads:** Reads access the current array without locking. Iterators operate on a snapshot that never reflects subsequent writes and never throws `ConcurrentModificationException`.

---

### 3. ConcurrentSkipListMap & ConcurrentSkipListSet
- Provides a thread-safe, sorted `NavigableMap` / `NavigableSet`.
- Backed by a **Skip List** (a probabilistic alternative to balanced trees).
- Uses lock-free CAS operations for all reads and mutations.
- Offers guaranteed **$O(\log n)$** time complexity for `get()`, `put()`, `remove()`.

---

## 4. Classic Concurrency Coding Patterns

### Pattern 1: Bounded-Buffer Producer-Consumer (ReentrantLock + Conditions)
```java
public class BoundedBlockingQueue<T> {
    private final Object[] items;
    private int head = 0, tail = 0, count = 0;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull  = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    public BoundedBlockingQueue(int capacity) {
        this.items = new Object[capacity];
    }

    public void put(T x) throws InterruptedException {
        lock.lock();
        try {
            while (count == items.length) {
                notFull.await(); // Wait until space is available
            }
            items[tail] = x;
            if (++tail == items.length) tail = 0;
            count++;
            notEmpty.signal(); // Notify waiting consumers
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0) {
                notEmpty.await(); // Wait until items are available
            }
            T x = (T) items[head];
            items[head] = null; // Prevent memory leak
            if (++head == items.length) head = 0;
            count--;
            notFull.signal(); // Notify waiting producers
            return x;
        } finally {
            lock.unlock();
        }
    }
}
```

---

### Pattern 2: Alternating Sequence Printing (Odd / Even by 2 Threads)
```java
public class OddEvenPrinter {
    private int count = 1;
    private final int max;
    private final Object lock = new Object();

    public OddEvenPrinter(int max) { this.max = max; }

    public void printOdd() {
        synchronized (lock) {
            while (count <= max) {
                while (count % 2 == 0) { // If even, wait
                    try { lock.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                if (count <= max) {
                    System.out.println("Odd Thread: " + count++);
                    lock.notifyAll();
                }
            }
        }
    }

    public void printEven() {
        synchronized (lock) {
            while (count <= max) {
                while (count % 2 != 0) { // If odd, wait
                    try { lock.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                if (count <= max) {
                    System.out.println("Even Thread: " + count++);
                    lock.notifyAll();
                }
            }
        }
    }
}
```

---

## 5. Senior / FAANG Interview Questions & Answers

### Q1: What is False Sharing in multi-threaded programming, and how is it addressed in Java?
**Answer:**
Modern CPU caches operate on **cache lines** (typically 64 bytes). When two distinct variables processed by two different CPU cores reside on the same 64-byte cache line, modifying variable A on Core 0 invalidates the entire cache line on Core 1, forcing Core 1 to reload variable B from L3/RAM even though variable B never changed.
Java solves this via padding:
- Prior to Java 8: Manual padding by declaring dummy `long p1, p2, p3, p4, p5, p6, p7;` variables.
- Java 8+: The `@jdk.internal.vm.annotation.Contended` annotation inserts 128 bytes of padding around the target field (used in `LongAdder.Cell` and `Thread.threadLocalRandomSeed`).

### Q2: Why is `ConcurrentHashMap.size()` an estimate rather than a strict snapshot?
**Answer:**
To count entries without stopping the world, `ConcurrentHashMap` aggregates its `baseCount` and active `CounterCell[]` array values without acquiring global locks. While `size()` sums these cells, other concurrent threads may insert or delete entries in other buckets. Hence, `size()` returns a weakly consistent estimate of current entries, which is acceptable in high-throughput concurrent systems.

### Q3: What is the difference between `ArrayBlockingQueue` and `LinkedBlockingQueue` in terms of lock contention?
**Answer:**
- `ArrayBlockingQueue` uses a **single `ReentrantLock`** for both producers (`put`) and consumers (`take`). A producer putting an item competes directly for the lock with a consumer taking an item.
- `LinkedBlockingQueue` uses **two separate locks**: a `takeLock` and a `putLock`. Producers and consumers can operate concurrently without lock contention, yielding significantly higher throughput under balanced load.

---

## 6. Summary Cheat Sheet

```
┌───────────────────────────┬────────────────────────────────────────────────────────┐
│ Concurrency Hazard / Tool │ Key Defense / Principle                                │
├───────────────────────────┼────────────────────────────────────────────────────────┤
│ Deadlock Prevention       │ Strict global lock ordering; breaks circular wait      │
│ Deadlock Detection        │ jstack <pid> or ThreadMXBean.findDeadlockedThreads()   │
│ CountDownLatch            │ One-time countdown gate; threads wait for events       │
│ CyclicBarrier             │ Reusable sync barrier; threads wait for each other     │
│ Semaphore                 │ Permit pool; ideal for rate limiters and resource pools│
│ ConcurrentHashMap         │ CAS for empty bucket + synchronized on bin head node   │
│ False Sharing             │ Cache line bouncing solved by @Contended padding       │
└───────────────────────────┴────────────────────────────────────────────────────────┘
```
