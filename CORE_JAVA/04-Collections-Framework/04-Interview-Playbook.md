# Collections Framework — Interview Playbook & Cross-Implementation Guide

> **Level:** Intermediate → Expert
> **Interview Frequency:** ★★★★★ (cross-implementation questions distinguish memorised definitions from engineering judgement)
> **Prerequisite:** [01 · List Implementations](./01-List-Implementations.md), [02 · Map Implementations](./02-Map-Implementations.md), and [03 · Set, Queue & Utilities](./03-Set-Queue-Utilities.md)
> **Last Updated:** 2026
>
> This playbook connects the three implementation chapters into one decision system. Use it when
> an interviewer asks, "Which one would you choose, and why?" rather than only asking for an API.

---

## 📋 Table of Contents

- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — Start From the Requirement](#1-what--why--start-from-the-requirement)
- [2. Mental Model — Contracts, Not Implementations](#2-mental-model--contracts-not-implementations)
- [3. Deep Dive — The Four Cross-Cutting Theories](#3-deep-dive--the-four-cross-cutting-theories)
  - [3.1 Complexity Means More Than O(1)](#31-complexity-means-more-than-o1)
  - [3.2 Equality and Hashing](#32-equality-and-hashing)
  - [3.3 Iteration, Views, and Mutation](#33-iteration-views-and-mutation)
  - [3.4 Concurrency Is a Contract, Not a Label](#34-concurrency-is-a-contract-not-a-label)
- [4. Visualizations](#4-visualizations)
- [5. Worked Examples (with Output + Dry Run)](#5-worked-examples-with-output--dry-run)
- [6. Code from Scratch — Production-Grade](#6-code-from-scratch--production-grade)
- [7. Common Pitfalls & Gotchas](#7-common-pitfalls--gotchas)
- [8. Interview Q&A — Level-wise](#8-interview-qa--level-wise)
- [9. Counter-Questions & Traps — Push-Back Practice](#9-counter-questions--traps--push-back-practice)
- [10. Rapid-Fire MCQs](#10-rapid-fire-mcqs)
- [11. Cheat Sheet / One-Page Summary](#11-cheat-sheet--one-page-summary)
- [12. Real-World Production Notes](#12-real-world-production-notes)
- [13. Follow-up Learning](#13-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

- **Choose by the dominant operation and required guarantees**, not by a memorised ranking of
  implementations. Point lookup, ordered traversal, range access, uniqueness, FIFO, priority,
  blocking, boundedness, and snapshot semantics are different requirements.
- **`HashMap`/`HashSet` lookup is expected O(1), not guaranteed O(1).** The expectation assumes a
  good `hashCode`, a reasonably distributed hash, and an acceptable load factor. Worst-case lookup
  can degrade to O(n) (or O(log n) for a treeified bucket).
- **`TreeMap`/`TreeSet` lookup is guaranteed O(log n)** because the red-black tree bounds its height.
  Buy that guarantee when keys are adversarial, ranges are required, or sorted order is essential.
- **`ArrayList` tail insertion is amortised O(1); `LinkedList` index lookup is O(n).** The linked
  list's O(1) insertion is useful only when a node/iterator position is already held.
- **`Map` is not a `Collection`.** `Map` associates keys with values; `Collection` holds elements.
  `Map.keySet()`, `values()`, and `entrySet()` are collection-like **views** backed by the map.
- **Equality and hashing are one contract.** If `a.equals(b)` is true, then `a.hashCode()` must
  equal `b.hashCode()`. Hash-based collections compare by `equals`; identity-based ones do not.
- **Fail-fast is a best-effort bug detector, not a thread-safety mechanism.** `ConcurrentHashMap`
  iterators are weakly consistent; `CopyOnWriteArrayList` iterators are snapshots.
- **Thread-safe does not mean transactionally consistent.** Atomic `put` does not make a
  `get`-then-`put` sequence atomic. Use `computeIfAbsent`, `merge`, `compute`, or an explicit lock.
- **An unmodifiable collection is not necessarily an immutable copy.** `unmodifiable*` creates a
  view; `List.copyOf`, `Set.copyOf`, and `Map.copyOf` create immutable snapshots and reject nulls.
- **Production defaults:** `ArrayList` for general sequences, `HashMap` for local lookup,
  `ArrayDeque` for stack/FIFO work, `HashSet` for membership, `TreeMap`/`TreeSet` only for ordered
  access, and bounded blocking queues for producer/consumer back-pressure.

---

# Collections Framework — Interview Playbook & Cross-Implementation Guide
## 1. What & Why — Start From the Requirement

Most collection mistakes begin with a name: "This needs a Map", "This should be a HashSet". A
strong answer starts with constraints:

```text
REQUIREMENT QUESTION                 CONSEQUENCE
──────────────────────────────────   ──────────────────────────────────────────────
Duplicate values allowed?            List / queue; Set removes duplicates
Need key → value lookup?             Map; HashMap for expected O(1)
Need sorted traversal or ranges?      TreeMap / TreeSet / sorted navigable map
Need insertion order too?             LinkedHashMap / LinkedHashSet
Need FIFO?                            Queue; ArrayDeque or blocking queue
Need highest priority first?          PriorityQueue
Multiple producers/consumers?         BlockingQueue family
Shared across threads?                Concurrent collections + atomic operations
Must not change after publication?    Immutable copy; do not merely wrap
Collection may grow without bound?    Add an explicit bound/admission policy
Keys can be hostile or collide?       Tree map or protected hash design
```

### The interview answer pattern

For any "which collection?" question, use this sequence:

1. **Name the dominant operation.** "Lookup by customer ID", not "store customers".
2. **State the required order.** None, encounter, sorted, insertion, access, or priority.
3. **State the data contract.** Duplicates, nulls, mutability, and key identity.
4. **State the concurrency and lifetime model.** Thread-confined, shared, bounded, immutable.
5. **Choose the simplest structure that satisfies all of them.**
6. **Defend the trade-off.** Include memory, worst case, consistency, and operational cost.

> **Interview one-liner:** "I would not choose from the interface name alone. I choose from the
> dominant operation and the guarantees the caller needs, then verify null, ordering, iteration,
> concurrency, and growth semantics."

### A compact decision tree

```text
START: What does the data model require?
│
├─ key → value associations?
│  ├─ ordered/ranges/hostile keys? ─────► TreeMap or ConcurrentSkipListMap
│  ├─ insertion order? ─────────────────► LinkedHashMap
│  ├─ enum keys? ───────────────────────► EnumMap
│  ├─ shared across threads? ───────────► ConcurrentHashMap
│  └─ ordinary local lookup? ───────────► HashMap
│
├─ ordered elements without key-value pairs?
│  ├─ sorted + ranges? ─────────────────► TreeSet / NavigableSet
│  ├─ insertion order? ─────────────────► LinkedHashSet
│  ├─ enum domain? ─────────────────────► EnumSet
│  └─ membership only? ─────────────────► HashSet
│
├─ sequence or work queue?
│  ├─ random access by index? ─────────► ArrayList
│  ├─ stack or non-blocking FIFO? ──────► ArrayDeque
│  ├─ priority order? ──────────────────► PriorityQueue
│  ├─ blocking producer/consumer? ──────► ArrayBlockingQueue / LinkedBlockingQueue
│  └─ sorted queue? ────────────────────► PriorityBlockingQueue (unbounded!)
│
└─ uniqueness?
   └─ use Set, then decide ordering and concurrency
```

> **Caption:** Each branch adds a *guarantee* that the previous branch did not provide. A more
> complex collection is justified only when that guarantee matters.

---

## 2. Mental Model — Contracts, Not Implementations

The interface says **what** an abstraction does; the implementation decides **how** and with what
guarantees. The most useful hierarchy view is therefore split into two questions:

```text
WHAT ABSTRACTION?                      HOW IMPLEMENTED?
────────────────────                   ─────────────────────
Collection                             Array-backed / linked / hash / tree / heap
  ├─ List                              ordered sequence, duplicates allowed
  ├─ Set                               unique elements
  ├─ Queue                             FIFO / priority order
  └─ Deque                             two-ended FIFO
Map (separate root)                    hash table / ordered map / array / skip list
```

### The one-table contract model

| Abstraction | Duplicate elements | Guaranteed order | Null elements | Typical lookup |
|---|---:|---|---|---|
| `List` | Yes | Encounter order | Usually yes | O(n) linear; O(1) only for `RandomAccess` |
| `Set` | No | Not from `Set` alone | Implementation-dependent | Implementation-dependent |
| `Queue` | Yes | FIFO for basic queue | Implementation-dependent | Usually O(1) at head/tail |
| `Deque` | Yes | FIFO/LIFO at ends | Implementation-dependent | Usually O(1) at ends |
| `Map` | Unique keys; duplicate values | Not from `Map` alone | Implementation-dependent | Implementation-dependent |

> **Caption:** The interface often cannot answer "Can it contain null?" or "What is the lookup
> complexity?" Those are implementation contracts. Always state the concrete class.

### Equality changes the data model

```text
equals-based semantics (HashMap/HashSet)     identity-based semantics (IdentityHashMap)
─────────────────────────────────────────     ───────────────────────────────────────────
new String("A") equals new String("A")       references are different keys
hashCode is derived from value fields        System.identityHashCode
deduplicates equal objects                  preserves two equal-but-distinct objects
most domain models use this                  identity-sensitive internals use this
```

> **Caption:** `HashMap` answers "which value?"; `IdentityHashMap` answers "which object?".

### Mutable keys break the map's index

```text
INSERT:  key.hashCode() = 1  ─────────────► bucket 1 stores (key, value)

MUTATE:  key.hashCode() = 2

LOOKUP:  key.hashCode() = 2  ─────────────► search bucket 2
                                           entry is still in bucket 1
                                           RESULT: unreachable entry
```

> **Caption:** A `HashMap` caches the hash in its node. A key whose equality fields change while
> it is a map key violates the map's lookup invariant. Use immutable keys (for example, records).

---

## 3. Deep Dive — The Four Cross-Cutting Theories

### 3.1 Complexity Means More Than O(1)

Interviewers use O notation loosely; candidates should be precise.

#### Expected, amortised, and worst case

| Label | Meaning | Example | Correct phrase |
|---|---|---|---|
| **O(1) worst case** | Bounded by a constant for every input | `ArrayList.get(i)` | "O(1)" |
| **Expected O(1)** | O(1) on average under hash assumptions | `HashMap.get` | "expected O(1)" |
| **Amortised O(1)** | O(1) average per operation over a sequence | `ArrayList.add` at tail | "amortised O(1)" |
| **O(log n) worst case** | Bounded by tree height | `TreeMap.get` | "guaranteed O(log n)" |
| **O(n) worst case** | Linear under a valid but unlucky input | `HashMap.get` before/without treeification | "can degrade to O(n)" |

**Hash-map average case depends on assumptions.** It assumes:

1. `hashCode()` is distributed well enough across buckets.
2. The key's equality fields do not mutate after insertion.
3. The load factor remains reasonable (default 0.75).
4. The table has not accumulated adversarial collisions.

The table's O(1) lookup does **not** mean the key's `hashCode()` is O(1). A user's
`hashCode()` implementation is part of the cost model.

#### Amortised analysis, correctly

For geometric growth, the copy costs form a series:

```text
capacity:      1 → 2 → 4 → 8 → 16 → 32
copied slots:    1   2   4   8   16   32
                             └──── total < 2n
```

Thus `n` tail insertions perform fewer than roughly `2n` element copies in total, plus `n`
constant-time appends. The **amortised** cost is O(1) per insertion, but an individual resize is
still O(n). Pre-sizing removes the spikes and the temporary old/new array overlap; it does not
change the asymptotic result.

#### The “hot operation” rule

| Requirement | Best structure | Why |
|---|---|---|
| get(i) by index | `ArrayList` | contiguous reference |
| append + iterate | `ArrayList` | cache-friendly |
| stack / deque | `ArrayDeque` | O(1) at both ends |
| known-node insertion | `LinkedList` (rare) | link splice is O(1) |
| membership | `HashSet` | expected O(1) |
| sorted membership / ranges | `TreeSet` | O(log n), navigable |
| key lookup | `HashMap` | expected O(1) |
| sorted key lookup / ranges | `TreeMap` | O(log n), navigable |
| cross-thread map | `ConcurrentHashMap` | per-bin write locking |
| blocking producer/consumer | `ArrayBlockingQueue` | bounded back-pressure |

> **Interview trap:** "LinkedList inserts are O(1), so it is faster for insertions." The answer is
> false for ordinary insertion by index: finding that index is O(n). The linked list's advantage
> exists only when the caller already holds a cursor/node position.

### 3.2 Equality and Hashing

The `equals` contract is the foundation of every hash-based collection:

```java
@Override
public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof Money money)) return false;
    return amountMinor == money.amountMinor
        && currency.equals(money.currency);
}

@Override
public int hashCode() {
    return Objects.hash(amountMinor, currency);
}
```

Four rules matter:

1. **Reflexive:** `x.equals(x)` is true for non-null `x`.
2. **Symmetric:** `x.equals(y)` implies `y.equals(x)`.
3. **Transitive:** if `x` equals `y` and `y` equals `z`, then `x` equals `z`.
4. **Consistent:** repeated calls return the same answer while equality-relevant state is unchanged.

The hash rule is narrower: equal objects must have equal hash codes. Unequal objects **may**
share a hash code; that is a collision, and `equals` resolves it.

#### Hash-based lookup in two phases

```text
key.hashCode() → bucket index → walk bucket → compare hash → compare equals
                                 ▲                          ▲
                                 │                          └── identity OR equals, by implementation
                                 └── hash equality is only a fast filter
```

A matching hash does not imply equality. If the hash matches but `equals` does not, the key is
different and the implementation keeps both entries in the same bucket.

#### Mutable, inconsistent, and expensive keys

| Key defect | Consequence | Prevention |
|---|---|---|
| Equality fields mutate | Entry becomes unreachable | Immutable key or remove/reinsert |
| `equals` overridden, `hashCode` not | Equal keys land in different buckets | Override both together |
| `hashCode` changes for equal objects | Contract violation | Derive it from the same fields as `equals` |
| Expensive `hashCode` | Every lookup pays the calculation | Cache safely, use a suitable domain key |
| Hash flooding | Buckets become hot/collide | Randomised hash or tree-backed map |

> **Important distinction:** `TreeMap`/`TreeSet` use `Comparator`, not `hashCode`. A comparator
> inconsistent with `equals` can cause two `equals`-different objects to be treated as one key.
> That is sometimes intentional (case-insensitive names), but it must be documented.

---

### 3.3 Iteration, Views, and Mutation

#### Fail-fast is not fail-safe

`ArrayList`, `HashMap`, and `HashSet` iterators remember an expected modification count. A
structural change alters the live count; a later `next()` may throw
`ConcurrentModificationException`. This is a **best-effort detector for accidental mutation**,
not a memory model guarantee and not a concurrency control mechanism.

```java
// Preferred: one structural pass
values.removeIf(value -> value.isExpired());

// Also valid: iterator owns removal
Iterator<Value> it = values.iterator();
while (it.hasNext()) {
    if (it.next().isExpired()) it.remove();
}

// Correct when the source must stay live: iterate a snapshot
for (Value value : List.copyOf(values)) {
    // safe: changing `values` does not change this iteration
}
```

#### Iteration guarantee matrix

| Collection / iterator | Guarantee during concurrent mutation | CME? | Snapshot? |
|---|---|---:|---:|
| `ArrayList` iterator | Best-effort fail-fast | Possible | No |
| `HashMap` iterator | Best-effort fail-fast | Possible | No |
| `CopyOnWriteArrayList` iterator | Fixed snapshot | No | Yes |
| `ConcurrentHashMap` iterator | Weakly consistent | No | No |
| `ConcurrentLinkedQueue` iterator | Weakly consistent | No | No |
| `ConcurrentSkipListMap` iterator | Weakly consistent | No | No |

**Weakly consistent** means the iterator reflects some state consistent with the map at or after
iterator creation; it does not throw CME, and it may or may not show a concurrent update. It is
**not** a point-in-time snapshot.

#### Views versus copies

```text
SOURCE MUTABLE LIST: [a, b]
        │
        ├─ Collections.unmodifiableList(source) ──► VIEW  ──► sees later source changes
        └─ List.copyOf(source) ─────────────────► COPY  ──► independent immutable snapshot
```

The same rule applies to `Map.keySet()`, `Map.values()`, `Map.entrySet()`, `List.subList()`,
`NavigableMap.subMap()`, and the unmodifiable wrappers. They are live views unless the API
explicitly says copy. A view also often retains its backing collection, so returning a
`subList` can unintentionally retain a much larger parent structure.

### 3.4 Concurrency Is a Contract, Not a Label

There are three different questions:

```text
1. Is a single operation safe?              → does the collection prevent corruption?
2. Is a compound operation atomic?          → is check-then-act indivisible?
3. What does iteration observe?             → snapshot, weak consistency, or no guarantee?
```

A collection can answer yes to (1) and no to (2).

#### The classic check-then-act race

```java
// ❌ Two threads can both observe "absent", then overwrite each other.
if (!map.containsKey(key)) {
    map.put(key, expensiveValue);
}

// ✅ Atomic per-key computeIfAbsent on ConcurrentHashMap.
AtomicReference<BigDecimal> ref = map.computeIfAbsent(key, k -> expensiveValue);
```

`computeIfAbsent` provides atomicity for the mapping of one key. The mapping function should be
short, must not recursively update the same map, and must avoid long blocking work because it may
run while a bin lock is held.

#### Concurrent collection choices

| Need | Use | Important caveat |
|---|---|---|
| Shared general map | `ConcurrentHashMap` | No null key/value; per-key atomic operations |
| Shared sorted map | `ConcurrentSkipListMap` | O(log n); lock-free reads; sorted traversal |
| Shared queue | `ConcurrentLinkedQueue` | Non-blocking, unbounded |
| Bounded blocking queue | `ArrayBlockingQueue` | Fixed capacity; fair/non-fair lock options |
| Bounded blocking queue | `LinkedBlockingQueue` | Optional capacity; one linked node per element |
| Read-mostly list | `CopyOnWriteArrayList` | O(n) per write; snapshot iterator |
| Concurrent ordered set | `ConcurrentSkipListSet` | O(log n), sorted |
| Compute-if-absent cache | `ConcurrentHashMap` | Bound it; atomic API does not prevent unbounded growth |

> **Interview distinction:** `HashMap` plus an external lock can be correct, but every access must
> use the same lock and iteration must occur under it. `ConcurrentHashMap` removes the global lock
> for ordinary operations, but you still need atomic compound operations or a wider transaction
> when several keys must change together.

---

## 4. Visualizations

### 4.1 The family as four data models

```text
SEQUENCE MODEL                    KEY–VALUE MODEL
┌──────────────────┐              ┌──────────────────────────┐
│ 0  1  2  3       │              │ key ──► value            │
│ A  B  C  D       │              │ id1 ─► Alice             │
└──────────────────┘              │ id2 ─► Bob               │
ArrayList / LinkedList            └──────────────────────────┘
                                  HashMap / TreeMap

SET MODEL                         WORK-ORDER MODEL
┌──────────────────┐              ┌──────────────────────────┐
│ unique elements │              │ root = highest priority   │
│ A  C  F          │              │ children form a heap      │
└──────────────────┘              └──────────────────────────┘
HashSet / TreeSet                 PriorityQueue
```

> **Caption:** The structural model determines which operations can be efficient. Membership is
> natural for a set; key/value association is natural for a map; priority retrieval is natural
> for a heap.

### 4.2 Hash lookup: bucket, chain, tree

```text
hash = h ^ (h >>> 16)        spread high bits into the low bits
index = (table.length - 1) & hash

table.length = 16 (mask 0x0F)

┌─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┐
│  0  │  1  │  2  │  3  │  4  │  5  │  6  │ ... │
└──┬──┴──┬──┴─────┴─────┴─────┴──┬──┴─────┴─────┘
   │     │                         │
   │     └─► [KeyB] ─► [KeyC]      └─► root
   │          chain (hash/equals)        │
   │                                    ├─ left
   │                                    └─ right  (treeified bucket)
```

> **Caption:** The spread does not prevent collisions. It makes bucket selection sensitive to
> more hash bits so distribution improves as the table grows.

### 4.3 `ArrayList` growth and copying

```text
capacity 3, size 3: [A][B][C][ ]

add D → capacity full → allocate 6 → copy

old array                 new array
[A][B][C][ ]     ───►     [A][B][C][D][ ][ ]
                         size=4, capacity=6
```

```text
remove(1) from [A][B][C]

copy left:  [A][B][C] → [A][C][C]
clear tail:            → [A][C][ ]
size becomes 2
```

> **Caption:** Middle removal is O(n) because references must move. Clearing the vacated tail slot
> prevents the removed element from being retained by the backing array.

### 4.4 Atomicity is per operation, not per intent

```text
INTENDED TRANSACTION: "increment count if present"

check ───────────► put
   │                 │
   └── not atomic ──┘     two threads can interleave here

ATOMIC PER-KEY UPDATE:
merge(key, 1, Integer::sum)

one operation on one key ──► linearised under the map's per-key mechanism
```

> **Caption:** `ConcurrentHashMap` prevents structural corruption and supplies atomic compound
> operations. It does not make arbitrary multi-key business transactions atomic.

### 4.5 View, copy, and publication

```text
                    ┌──────────────────────┐
mutable source ────►│ ArrayList (backing)  │
       │            └──────────┬───────────┘
       │                       │
       ├─ unmodifiableList ────┼──► VIEW: read-only API, live backing
       │                       │
       └─ List.copyOf ─────────┴──► COPY: immutable, independent snapshot

PUBLISH THE COPY ──► safe to share between threads
PUBLISH THE VIEW ──► source can still change underneath
```

> **Caption:** “Read-only” describes the operations exposed through the wrapper. “Immutable” means
> the reachable collection state cannot change.

### 4.6 Queue-family selection

```text
Need to hand work between threads?
│
├─ Must block when empty?       ─► BlockingQueue
│  ├─ fixed capacity + array    ─► ArrayBlockingQueue
│  ├─ linked nodes / optional cap ► LinkedBlockingQueue
│  └─ priority order            ─► PriorityBlockingQueue (usually unbounded)
│
├─ Never block, fail/poll empty? ─► ConcurrentLinkedQueue
│
└─ Single-threaded FIFO/stack?   ─► ArrayDeque
```

> **Caption:** Blocking is a semantic requirement, not merely a performance characteristic.

---

## 5. Worked Examples (with Output + Dry Run)

### Scenario 1 — Deduplicate orders but preserve first-seen order

**Requirement:** Remove duplicate customer IDs while retaining the order in which customers first
appeared.

```java
List<String> ids = List.of("c3", "c1", "c3", "c2", "c1");

LinkedHashSet<String> unique = new LinkedHashSet<>(ids);
List<String> result = new ArrayList<>(unique);

System.out.println(result);
```

**Output**

```text
[c3, c1, c2]
```

**Dry run:** `c3` is inserted → `c1` is inserted → `c3` is rejected as a duplicate → `c2` is
inserted. The linked entries remain in encounter order: `c3, c1, c2`.

**Why:** `HashSet` would deduplicate in unspecified/hash-bucket order. `LinkedHashSet` preserves
encounter order with expected O(1) membership. If sorted output were required, use `TreeSet` and
accept O(log n) operations.

### Scenario 2 — Aggregate orders by customer and keep chronology

**Requirement:** Group order IDs by customer ID; within each group, retain input order.

```java
record Order(String customerId, String orderId) {}

Map<String, List<String>> byCustomer = new LinkedHashMap<>();
for (Order order : List.of(
        new Order("c1", "o1"),
        new Order("c2", "o2"),
        new Order("c1", "o3"))) {
    byCustomer.computeIfAbsent(order.customerId(), id -> new ArrayList<>())
              .add(order.orderId());
}

System.out.println(byCustomer);
```

**Output**

```text
{c1=[o1, o3], c2=[o2]}
```

**Dry run:** `c1/o1` creates group `c1`; `c2/o2` creates group `c2`; `c1/o3` finds the existing
`c1` group and appends `o3`. The final groups are `c1=[o1,o3]` and `c2=[o2]`.

**Why:** `LinkedHashMap` retains customer encounter order. The list value retains order chronology.
Use `HashMap` instead if customer-group order is irrelevant.

### Scenario 3 — Find the five largest payments without sorting everything

**Requirement:** Return the five largest payments by amount.

```java
record Payment(String id, BigDecimal amount) {}

PriorityQueue<Payment> top = new PriorityQueue<>(
        Comparator.comparing(Payment::amount));
for (Payment payment : List.of(
        new Payment("p1", new BigDecimal("10")),
        new Payment("p2", new BigDecimal("50")),
        new Payment("p3", new BigDecimal("20")),
        new Payment("p4", new BigDecimal("40")),
        new Payment("p5", new BigDecimal("60")),
        new Payment("p6", new BigDecimal("5")))) {
    top.offer(payment);
    if (top.size() > 5) top.poll();
}

List<Payment> result = new ArrayList<>(top);
result.sort(Comparator.comparing(Payment::amount).reversed());
System.out.println(result);
```

**Output for this small input**

```text
[p5, p2, p4, p3, p1]
```

**Dry run:** Each payment is offered to the heap. After `p6` makes the heap size six, `poll()`
removes its smallest candidate (`p6`, amount 5). The final heap therefore contains the five
largest payments; sorting the copied list yields `p5, p2, p4, p3, p1`.

**Why:** The heap keeps only the best candidates and performs O(log k) work per item for a
top-`k` result. The final sort is needed only when the returned list must be ordered; the heap
alone is not sorted. A full sort is simpler when k is close to n or when memory is constrained.

### Scenario 4 — A thread-safe per-user counter registry

**Requirement:** Multiple request threads update counters concurrently without losing updates.

```java
ConcurrentMap<String, LongAdder> counters = new ConcurrentHashMap<>();
counters.computeIfAbsent("requests", id -> new LongAdder()).increment();
System.out.println(counters.get("requests").sum());
```

**Output**

```text
1
```

**Dry run:** `computeIfAbsent("requests", ...)` finds no entry, creates one `LongAdder`, and
installs it atomically. `increment()` changes the counter from 0 to 1, and `sum()` returns 1.

**Why:** `computeIfAbsent` atomically creates the counter. `LongAdder` scales better than
`AtomicLong` under heavy increment contention. For a strict current value, `AtomicLong` or
`AtomicReference` may be simpler; metrics usually favour `LongAdder`.

### Scenario 5 — A bounded work queue with back-pressure

**Requirement:** Producers submit tasks; consumers process them. When 100 tasks are waiting,
producers must slow down rather than grow memory without limit.

```java
BlockingQueue<String> queue = new ArrayBlockingQueue<>(100);
queue.offer("task-1");
System.out.println(queue.remainingCapacity());
System.out.println(queue.take());
```

**Output**

```text
99
task-1
```

**Dry run:** `offer("task-1")` succeeds because 1 of 100 slots is used, so remaining capacity is 99.
`take()` waits for and removes the head element, returning `task-1`.

**Why:** `ArrayBlockingQueue` has fixed capacity. `put` blocks when full; `offer` can fail or use
a timeout. This converts unbounded heap growth into visible producer back-pressure. Note that a
fixed-capacity queue only bounds the waiting tasks; it does not bound total application memory.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A reusable bounded cache with explicit eviction

```java
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Function;

public final class BoundedCache<K, V> {
    private final int maxSize;
    private final LinkedHashMap<K, V> entries;

    public BoundedCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        this.maxSize = maxSize;
        this.entries = new LinkedHashMap<>(maxSize, 0.75f, true);
    }

    public V get(K key, Function<K, V> loader) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(loader, "loader");
        V value = entries.get(key);
        if (value != null) {
            return value;
        }
        V loaded = Objects.requireNonNull(loader.apply(key), "loader returned null");
        entries.put(key, loaded);
        evictIfNeeded();
        return loaded;
    }

    private void evictIfNeeded() {
        while (entries.size() > maxSize) {
            var iterator = entries.entrySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    public int size() {
        return entries.size();
    }
}
```

> **Scope:** This cache is safe only when confined to one thread or externally synchronised. The
> bounded `LinkedHashMap` shows the policy (access-order LRU); production libraries add atomicity,
> statistics, expiry, and eviction tuning.

### 6.2 Atomic per-key counters without lost updates

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

public final class MetricRegistry {
    private final ConcurrentMap<String, LongAdder> counters = new ConcurrentHashMap<>();

    public void increment(String metric) {
        counters.computeIfAbsent(metric, ignored -> new LongAdder()).increment();
    }

    public long count(String metric) {
        LongAdder adder = counters.get(metric);
        return adder == null ? 0L : adder.sum();
    }
}
```

`computeIfAbsent` prevents two threads from creating separate counters for the same name.
`LongAdder` is appropriate for high-contention event counts; use `AtomicLong` when exact
read-modify-write semantics and a single current value are required.

### 6.3 A stable priority queue for fair scheduling

```java
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class StablePriorityQueue<T> {
    private record Entry<T>(long sequence, int priority, T value) {}

    private final AtomicLong sequence = new AtomicLong();
    private final PriorityQueue<Entry<T>> queue;

    public StablePriorityQueue() {
        Comparator<Entry<T>> comparator = Comparator
                .comparingInt((Entry<T> entry) -> entry.priority())
                .thenComparingLong(entry -> entry.sequence());
        this.queue = new PriorityQueue<>(comparator);
    }

    public void offer(T value, int priority) {
        queue.offer(new Entry<>(sequence.getAndIncrement(), priority, value));
    }

    public T poll() {
        Entry<T> entry = queue.poll();
        return entry == null ? null : entry.value();
    }
}
```

The sequence number breaks ties deterministically, producing FIFO order among equal priorities.
`PriorityQueue` itself is not stable and is not thread-safe.

### 6.4 Defensive snapshots at API boundaries

```java
import java.util.List;
import java.util.Map;

public final class OrderResponse {
    private OrderResponse() {}

    public static List<String> immutableOrderIds(Map<String, String> orders) {
        return List.copyOf(orders.keySet());
    }

    public static Map<String, String> immutableOrders(Map<String, String> orders) {
        return Map.copyOf(orders);
    }
}
```

Use a copy when data crosses a trust boundary or is published to another thread. A wrapper only
blocks mutation through that wrapper; it does not freeze the backing state.

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Treating an interface as a guarantee

**Problem:** “`List.get(i)` is O(1)” is false for every `List`; it is true for `ArrayList`, not
`LinkedList`.

**✅ Fix:** State the concrete implementation and distinguish guaranteed, expected, and amortised
complexity.

### ❌ Pitfall 2 — Using a mutable map key

**Problem:** Changing the key's equality fields after insertion makes the cached entry unreachable.

**✅ Fix:** Use an immutable key. If mutation is unavoidable, remove with the old value and
reinsert with the new value under the same lock.

### ❌ Pitfall 3 — Assuming hash order is stable

**Problem:** `HashMap`/`HashSet` iteration order is unspecified and can change after resizing.

**✅ Fix:** Use `LinkedHashMap`/`LinkedHashSet` for insertion order or `TreeMap`/`TreeSet` for
sorted order.

### ❌ Pitfall 4 — Removing during an enhanced-for loop

**Problem:** Structural mutation can trigger `ConcurrentModificationException` and, in concurrent
code, is not detected reliably.

**✅ Fix:** Prefer `removeIf`; otherwise use `Iterator.remove()`; use a snapshot only when live
iteration and separate removal are both required.

### ❌ Pitfall 5 — Calling an unmodifiable wrapper “immutable”

**Problem:** `Collections.unmodifiableList(source)` is a view; `source` can still change it.

**✅ Fix:** Use `List.copyOf`, `Set.copyOf`, `Map.copyOf`, or an explicit defensive copy at a
publication/trust boundary.

### ❌ Pitfall 6 — Relying on `remove(int)` for `List<Integer>`

**Problem:** `remove(1)` removes index 1; `remove(Integer.valueOf(1))` removes the value 1.

**✅ Fix:** Box the index explicitly and add a comment when the value overload is intended.

### ❌ Pitfall 7 — Treating `PriorityQueue` as sorted

**Problem:** Its iterator exposes heap-array order, not global sorted order.

**✅ Fix:** Drain with `poll()` when order matters, or copy and sort explicitly.

### ❌ Pitfall 8 — Assuming `TreeSet` always rejects null

**Problem:** Natural ordering cannot compare `null`, but a custom comparator can define null
ordering explicitly.

**✅ Fix:** State the comparator. `Comparator.nullsFirst(Comparator.naturalOrder())` supports null
for `TreeSet` and `TreeMap`.

### ❌ Pitfall 9 — Using `ConcurrentHashMap` for a multi-key transaction

**Problem:** Atomic per-key operations do not make several key changes appear as one atomic
transaction.

**✅ Fix:** Use a lock, an immutable aggregate replaced atomically, or a transactional store when
global consistency is required.

### ❌ Pitfall 10 — Unbounded queues and caches

**Problem:** An unbounded work queue or cache converts back-pressure into heap exhaustion.

**✅ Fix:** Bound the queue/cache, expose depth and rejection metrics, and define overload
behaviour (block, fail fast, shed load, or run in caller).

### ❌ Pitfall 11 — Blocking inside `computeIfAbsent`

**Problem:** Long-running mapping functions can hold a bin lock and stall unrelated keys that map
to the same bin.

**✅ Fix:** Keep the function short and non-blocking; create expensive resources outside the map
operation or use a dedicated per-key future/lock pattern.

### ❌ Pitfall 12 — Choosing `LinkedList` from a textbook O(1) claim

**Problem:** Locating the insertion position by index is O(n), and nodes have poor locality.

**✅ Fix:** Default to `ArrayList`; use `ArrayDeque` for stack/queue semantics; use `LinkedList`
only with a documented cursor/node-position requirement.

---


## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is the difference between `List`, `Set`, and `Map`?**
**A:** A `List` is an ordered sequence that allows duplicates. A `Set` contains unique elements
but does not define an order by itself. A `Map` associates unique keys with values and is not a
`Collection`; it has `put` rather than `add`.

**Q: Why choose `ArrayList` over `LinkedList`?**
**A:** `ArrayList` stores references in a contiguous array, so indexed access is O(1), iteration is
cache-friendly, and each element has no per-node object overhead. `LinkedList` allocates a node
per element and index lookup is O(n). Its O(1) insertion only wins when a cursor/node position is
already held.

**Q: What is the difference between `HashMap` and `TreeMap`?**
**A:** `HashMap` provides expected O(1) lookup and iteration in unspecified hash order. `TreeMap`
uses a red-black tree, provides guaranteed O(log n) lookup, iterates in sorted order, and supports
range and navigation operations.

**Q: Is `HashMap` thread-safe?**
**A:** No. Concurrent structural modification can lose updates or corrupt internal state. Use a
`ConcurrentHashMap` for shared access and its atomic compound operations, or confine the map to
one thread.

**Q: What does `ConcurrentModificationException` mean?**
**A:** It usually means a collection was structurally modified while an iterator was in use. The
check is best-effort and mainly catches same-thread mutation bugs; it is not a concurrency
guarantee.

### 🟡 Intermediate

**Q: Why does `HashMap` use `h ^ (h >>> 16)`?**
**A:** The bucket index uses only the table's low mask bits. The XOR shift folds high hash bits
into the low half so bucket selection depends on more of the hash as the table grows. It improves
distribution; it does not guarantee that collisions disappear.

**Q: What exactly happens when a default `HashMap` grows?**
**A:** Its threshold is 12, so inserting the 13th distinct entry grows the table from capacity 16
to 32 and the threshold to 24. Java 8+ reuses cached hashes and splits each chain into low and
high lists based on one newly used bit.

**Q: When is `TreeMap` better than `HashMap`?**
**A:** When sorted iteration, predecessor/successor navigation, range queries, or a guaranteed
O(log n) worst case are required. If only point lookup is needed and keys have good hashes,
`HashMap` is simpler and usually faster.

**Q: Why does `HashSet` allow null while `TreeSet` usually rejects it?**
**A:** `HashSet` delegates to `HashMap`, which defines null-key handling. A naturally ordered
`TreeSet` must compare elements and natural ordering cannot compare null. A custom comparator can
explicitly define null ordering, so the exact answer depends on the comparator.

**Q: What is the difference between `PriorityQueue` and `TreeSet`?**
**A:** A `PriorityQueue` exposes only the smallest or largest element efficiently; `peek` is O(1)
and `poll` is O(log n), but iteration is heap order. A `TreeSet` keeps every element sorted,
supports range/navigation operations, and membership is O(log n).

**Q: Is `CopyOnWriteArrayList` a good general-purpose thread-safe list?**
**A:** It is good for small, read-mostly collections such as listener registries because reads are
fast and iterators are snapshots. Every write copies the entire backing array, so frequent writes
become O(n) each and create allocation pressure.

### 🔴 Advanced / Senior

**Q: How would you choose a collection for a service receiving 10,000 events per second?**
**A:** Start from the dominant operation and consistency requirement. A bounded queue with
back-pressure is appropriate if producers must wait; a concurrent queue if losing work is
unacceptable and producers should not block; a concurrent map if the real requirement is keyed
aggregation. I would also measure queue depth, rejection rate, processing latency, and heap growth
because the collection choice is part of a back-pressure design.

**Q: Why can `computeIfAbsent` still cause a performance incident?**
**A:** The mapping function can be expensive or blocking while the implementation works on the
affected key/bin. It can also recursively update the same map and cause illegal state or deadlock.
The fix is a short, side-effect-free mapping function; expensive creation should happen outside or
through a carefully designed per-key coordination mechanism.

**Q: How do you build an LRU cache, and what are its production limitations?**
**A:** A small single-JVM cache can extend `LinkedHashMap` in access-order mode and override
`removeEldestEntry`. In a multi-threaded or distributed service, prefer a proven cache such as
Caffeine because it adds atomicity, expiry, bounded maintenance, statistics, and better eviction
behaviour.

**Q: How would you debug a suspected `HashMap` performance problem?**
**A:** First establish the symptom with allocation and latency profiles rather than assuming the
map is the cause. Inspect key `hashCode` implementations for poor distribution or expense, check
the entry count and retained size, look for accidental unbounded growth, and compare CPU samples
with `HashMap.get` on the stack. A `TreeMap` or a randomised hash may be appropriate if collisions
are adversarial.

**Q: Is `Collections.synchronizedMap(new HashMap<>())` obsolete?**
**A:** It is correct when every operation uses the wrapper and compound actions are protected by
the same lock, but it serialises all access. For shared maps in modern Java, `ConcurrentHashMap`
normally provides better scalability and atomic per-key operations. The synchronized wrapper is
still useful when a single coarse-grained lock is intentional.

### ⚫ Expert / Principal

**Q: How do you choose between consistency, availability, and latency in a shared map?**
**A:** The collection cannot invent a distributed consistency model. `ConcurrentHashMap` provides
atomicity within one JVM, not durable state or cross-node transactions. For multi-node invariants,
choose the consistency boundary deliberately—atomic updates to one external system, optimistic
version checks, idempotent operations, sagas, or a transactional store—and design the map as a
local coordination cache rather than the source of truth.

**Q: What does it mean to choose a collection by its access pattern rather than its asymptotic
complexity?**
**A:** The asymptotic label hides constants, memory locality, allocation behaviour, contention,
and tail latency. For example, `ArrayList` shifts contiguous references very quickly, whereas a
linked list chases pointers across the heap. A bounded concurrent structure may outperform an
unbounded one even with a theoretically better operation count because the latter fails under
overload.

**Q: How would you make a collection choice resilient to hostile keys?**
**A:** First identify whether keys are trusted and whether hash flooding is in the threat model.
For untrusted keys, use a tree-backed map for worst-case O(log n), a key type with a
well-distributed or randomised hash, or a hardened map implementation. Also isolate the cost of
the key's `hashCode` and `equals` methods so they cannot become an amplification vector.

**Q: How do you reason about a collection that can grow without bound?**
**A:** Treat capacity as a reliability boundary, not a tuning detail. Define the maximum useful
state, the admission policy, the eviction or rejection semantics, the metrics, and the failure
mode. An unbounded map or queue is acceptable only when an upstream invariant makes growth
impossible and is observable and tested.

---

## 9. Counter-Questions & Traps — Push-Back Practice

### CQ1. “Isn't `HashMap` fast enough for everything?”

- **Naive:** “Yes, O(1) beats O(log n).”
- **Trap:** The average hides ordering, ranges, worst-case hash collisions, and concurrent atomicity.
- **Expert answer:** `HashMap` is the right default for ordinary point lookup. I would move to
  `TreeMap` for sorted/range access or adversarial keys, and to `ConcurrentHashMap` for shared
  state. The choice follows the guarantee the caller needs.

### CQ2. “If `ArrayList.add` is amortised O(1), why pre-size it?”

- **Naive:** “Amortised means it is already optimal.”
- **Trap:** It hides individual O(n) growth spikes, temporary old/new array overlap, and tail latency.
- **Expert answer:** Pre-size when the final count is known and stable. It removes predictable
  latency spikes and unnecessary copying. Do not pre-size unbounded data merely on a guess.

### CQ3. “Why not use `LinkedList` for constant-time insertion?”

- **Naive:** “Its insertion complexity is O(1).”
- **Trap:** Finding the insertion position by index is O(n); ordinary application code does not
  hold an internal node.
- **Expert answer:** The O(1) claim applies to a known cursor/node. `ArrayList` normally wins
  because contiguous shifting and traversal are cache-friendly. `LinkedList` is justified only by
  a documented cursor-based requirement.

### CQ4. “If `ConcurrentHashMap` is thread-safe, why isn't my code safe?”

- **Naive:** “All operations on a `ConcurrentHashMap` are atomic.”
- **Trap:** Individual operations are safe; a sequence such as `get` then `put` is not atomic.
- **Expert answer:** Use `computeIfAbsent`, `merge`, or `compute` for a per-key compound update.
  If several keys must change together, use a wider lock or transactional boundary.

### CQ5. “Isn't `unmodifiableList` enough to make data immutable?”

- **Naive:** “The wrapper throws on `add`.”
- **Trap:** It blocks mutation only through the wrapper; the backing source can still change.
- **Expert answer:** Use `List.copyOf` when publishing an immutable snapshot. The distinction is
  operation restriction versus state immutability.

### CQ6. “Why not use `CopyOnWriteArrayList` everywhere and stop worrying?”

- **Naive:** “It is thread-safe and has a fast iterator.”
- **Trap:** Every write copies the entire list, making writes O(n) and writers effectively
  serialised.
- **Expert answer:** It fits small, read-mostly structures such as listener registries. For
  write-heavy data, choose a concurrent queue or a different synchronisation strategy.

### CQ7. “Does `ConcurrentModificationException` protect concurrent code?”

- **Naive:** “Yes, it detects concurrent modification.”
- **Trap:** The check is best-effort and not based on a concurrency guarantee.
- **Expert answer:** It primarily catches same-thread structural mutation during iteration. Use
  the concurrent collection's explicit iterator contract for cross-thread access.

### CQ8. “Can a `TreeSet` always hold null?”

- **Naive:** “No, ordered sets reject null.”
- **Trap:** Natural ordering rejects null, but a custom comparator can define null ordering.
- **Expert answer:** State the comparator. `Comparator.nullsFirst(Comparator.naturalOrder())`
  supports null; the default natural-order comparator does not.

### CQ9. “Why use `TreeMap` if `HashMap` is faster?”

- **Naive:** “There is no reason; O(log n) is always worse.”
- **Trap:** It compares lookup only and ignores capability and worst-case guarantees.
- **Expert answer:** `TreeMap` provides sorted traversal, navigation, ranges, and O(log n) worst-case
  lookup. It trades constant factors for required capabilities.

### CQ10. “Is an unbounded queue simply a tuning issue?”

- **Naive:** “Increase heap or tune the pool.”
- **Trap:** It converts overload into unbounded retention and turns a back-pressure signal into an
  OOM far from the cause.
- **Expert answer:** Bound the queue, choose an explicit rejection policy, expose queue depth and
  rejections, and design producers and consumers around back-pressure.

---

### Scenario Drill Bank — Model Answers

### Drill 1 — “Deduplicate and preserve order”

**Prompt:** “Given customer IDs, remove duplicates but preserve first occurrence. What collection
would you use?”

**Model answer:** “I would use `LinkedHashSet`: it preserves encounter order while giving expected
O(1) membership. If the result only needs uniqueness and not order, `HashSet` is simpler. If the
result needs sorted order, I would use `TreeSet` and accept O(log n) insertion.”

### Drill 2 — “Group while preserving order”

**Prompt:** “Group orders by customer and preserve chronology within each customer.”

**Model answer:** “I would use `LinkedHashMap<String, List<Order>>` and `computeIfAbsent` to
create the group's list lazily. The map preserves customer encounter order and each list preserves
order chronology. If customer-group order is irrelevant, I would use `HashMap` to avoid the extra
linked-list overhead.”

### Drill 3 — “Top K without a full sort”

**Prompt:** “Return the top 10 orders by amount from a stream of one million orders.”

**Model answer:** “I would maintain a min-heap of at most 10 elements. After each insertion, if
size exceeds 10 I remove the smallest, leaving the best 10. This uses O(n log k) time and O(k)
memory. I would sort the final 10 only if the output contract requires ordered results.”

### Drill 4 — “Concurrent lazy initialisation”

**Prompt:** “Create a customer session lazily without creating it twice.”

**Model answer:** “On `ConcurrentHashMap`, use `computeIfAbsent`. The mapping operation is atomic
for the key, so two callers cannot both install different sessions for the same key. The mapping
function must be short and must not recursively update the same map. For a distributed system, the
map alone is insufficient; I would also need a database uniqueness constraint or distributed
coordination.”

### Drill 5 — “A queue that causes OOM”

**Prompt:** “Our thread pool memory grows until OOM. Which collection is the likely cause?”

**Model answer:** “I would inspect the executor's work queue. `Executors.newFixedThreadPool` uses
an unbounded `LinkedBlockingQueue`, so rejected work is actually retained and can grow until the
heap fails. I would construct the executor with a bounded `ArrayBlockingQueue` and an explicit
rejection policy, then expose queue depth and rejection metrics.”

### Drill 6 — “A map entry cannot be found”

**Prompt:** “We inserted a key, changed one of its fields, and now `get` returns null.”

**Model answer:** “The key's hash probably changed after insertion. `HashMap` cached the old
spread hash and stored the entry in the old bucket, while lookup now searches the new bucket. I
would make the key immutable or remove and reinsert it atomically when changing an equality field.”

### Drill 7 — “Remove while iterating”

**Prompt:** “Our cleanup loop sometimes throws `ConcurrentModificationException`.”

**Model answer:** “The loop mutates the collection structurally while iterating it. I would use
`removeIf` for a condition-based cleanup or `Iterator.remove` when the iteration owns the
removal. If concurrent writers exist, a fail-fast iterator is the wrong contract; I would use the
appropriate concurrent collection and design for its weaker iteration consistency.”

### Drill 8 — “Publish configuration safely”

**Prompt:** “A configuration list is rebuilt while request threads read it.”

**Model answer:** “I would build a complete list, validate it, and publish an immutable snapshot
using `List.copyOf`. Readers then see one stable state without locks. An unmodifiable wrapper is
insufficient because the source can still change underneath it.”

---

## 10. Rapid-Fire MCQs

**Q1.** Which interface is not a `Collection`?
- A) `List` B) `Set` C) `Map` D) `Queue`

**Q2.** `ArrayList.get(index)` is…
- A) O(1) B) O(log n) C) O(n) D) amortised O(n)

**Q3.** `LinkedList.get(index)` is…
- A) O(1) B) O(log n) C) O(n) D) amortised O(1)

**Q4.** `ArrayList.add` at the tail is…
- A) always O(1) B) always O(n) C) amortised O(1) D) O(log n)

**Q5.** `HashMap.get` is…
- A) guaranteed O(1) B) expected O(1) C) guaranteed O(log n) D) O(n)

**Q6.** `TreeMap.get` is…
- A) expected O(1) B) amortised O(1) C) guaranteed O(log n) D) O(1)

**Q7.** With default `HashMap` settings, which insertion first triggers a resize?
- A) 12th B) 13th C) 16th D) 17th

**Q8.** A hash collision means…
- A) keys are equal B) hashes are equal but keys may differ C) map is corrupt D) resize occurs

**Q9.** Equal objects must have…
- A) equal hash codes B) identical references C) the same class D) equal `toString`

**Q10.** `PriorityQueue.peek()` is…
- A) O(1) B) O(log n) C) O(n) D) O(n log n)

**Q11.** `PriorityQueue.remove(Object)` is…
- A) O(1) B) O(log n) C) O(n) D) O(1) amortised

**Q12.** `PriorityQueue` iteration is…
- A) sorted B) reverse sorted C) heap-array order D) insertion order

**Q13.** `Collections.unmodifiableList(source)` is…
- A) an immutable copy B) a live view C) a synchronized copy D) a deep copy

**Q14.** `List.copyOf(source)` is…
- A) a live view B) an immutable copy C) mutable D) null-tolerant

**Q15.** `ConcurrentHashMap` iteration is…
- A) fail-fast B) weakly consistent C) a point-in-time snapshot D) unordered and unsupported

**Q16.** `CopyOnWriteArrayList` iteration is…
- A) fail-fast B) weakly consistent C) a snapshot D) locked

**Q17.** `CopyOnWriteArrayList.add` is…
- A) O(1) B) O(log n) C) O(n) D) amortised O(1)

**Q18.** `ConcurrentHashMap.computeIfAbsent` makes which operation atomic?
- A) mapping one key B) every map operation C) iteration D) serialization

**Q19.** `TreeSet` with natural ordering accepts null?
- A) Yes B) No C) Only one D) Only with `identityHashCode`

**Q20.** Which queue family provides producer back-pressure?
- A) `PriorityQueue` B) `ConcurrentLinkedQueue` C) `BlockingQueue` D) `ArrayDeque`

### Answer key

| # | Answer | One-line reason |
|---|---|---|
| 1 | C | `Map` is a separate top-level interface. |
| 2 | A | Contiguous backing array gives constant-time indexed access. |
| 3 | C | Linked-list index access requires traversal. |
| 4 | C | Geometric growth makes tail insertion amortised O(1). |
| 5 | B | Hash lookup is expected O(1) under good distribution. |
| 6 | C | Red-black tree height is bounded by O(log n). |
| 7 | B | Threshold is 12; growth occurs when size exceeds it. |
| 8 | B | Equal hashes are only a filter; `equals` decides key identity. |
| 9 | A | The hash-code contract requires it for equal objects. |
| 10 | A | The heap root is directly accessible. |
| 11 | C | Removing an arbitrary value requires a heap scan. |
| 12 | C | The iterator walks the internal heap array. |
| 13 | B | The wrapper reflects later changes to its source. |
| 14 | B | `copyOf` creates an independent immutable snapshot. |
| 15 | B | Concurrent map iterators are weakly consistent, not snapshots. |
| 16 | C | The iterator captures the backing array at creation. |
| 17 | C | Every write copies all n references. |
| 18 | A | The mapping function is applied atomically for one key. |
| 19 | B | Natural ordering cannot compare null. |
| 20 | C | Blocking queues can block producers when capacity is exhausted. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                       CHOOSE BY THE DOMINANT OPERATION                    │
├──────────────────────────────┬───────────────────────────────────────────┤
│ Indexed sequence              │ ArrayList                                │
│ Stack / FIFO (single thread)  │ ArrayDeque                               │
│ Unique membership             │ HashSet                                  │
│ Unique + encounter order      │ LinkedHashSet                            │
│ Unique + sorted / ranges      │ TreeSet (or EnumSet)                     │
│ Key → value lookup            │ HashMap                                  │
│ Key → value + insertion order │ LinkedHashMap                            │
│ Key → value + ranges          │ TreeMap (or EnumMap)                     │
│ Highest priority first        │ PriorityQueue                            │
│ Shared map                    │ ConcurrentHashMap                        │
│ Shared sorted map             │ ConcurrentSkipListMap                    │
│ Blocking producer/consumer    │ ArrayBlockingQueue / LinkedBlockingQueue │
└──────────────────────────────┴───────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                         COMPLEXITY PHRASING                              │
├──────────────────────┬───────────────────────────────────────────────────┤
│ ArrayList.get(i)     │ O(1) worst case                                  │
│ ArrayList tail add   │ amortised O(1); one resize can be O(n)           │
│ HashMap get/put      │ expected O(1); bad hash distribution can be O(n) │
│ TreeMap get/put      │ guaranteed O(log n)                              │
│ PriorityQueue peek   │ O(1)                                             │
│ PriorityQueue poll   │ O(log n)                                         │
│ PriorityQueue remove │ O(n) for an arbitrary value                      │
│ CopyOnWrite add      │ O(n)                                             │
└──────────────────────┴───────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                       THE FIVE CONTRACT CHECKS                           │
├──────────────────────────────────────────────────────────────────────────┤
│ 1. Order: none / encounter / insertion / sorted / access / priority?   │
│ 2. Identity: equals-based or reference-based?                          │
│ 3. Nulls: allowed, forbidden, or comparator-defined?                  │
│ 4. Mutation/iteration: live, snapshot, fail-fast, weakly consistent?  │
│ 5. Concurrency/growth: shared? atomic? bounded? back-pressure needed? │
└──────────────────────────────────────────────────────────────────────────┘
```

### Thirty-second answer template

> “I would choose **[implementation]** because the dominant operation is **[operation]** and the
> caller needs **[guarantee]**. Its trade-off is **[cost]**. I would also verify **[null/order/
> iteration/concurrency/bound]** semantics before publishing it.”

---

## 12. Real-World Production Notes

### Capacity, retention, and back-pressure

- **Pre-size known finite collections.** It avoids growth spikes, temporary old/new storage, and
  unnecessary table splits.
- **Bound caches and queues.** A cache without eviction is a delayed OOM; an unbounded work queue
  turns overload into memory exhaustion. Define maximum size and overload behaviour explicitly.
- **Watch retained size, not only `size()`.** A list with 10 elements can retain a much larger
  backing array; a map can retain large values; a sublist or unmodifiable view can retain its
  entire parent structure.
- **Separate waiting work from in-flight work.** A bounded queue limits waiting tasks, but the
  executor's active workers and task-local state also consume memory.

### Metrics worth exporting

| Metric | What it reveals | Possible action |
|---|---|---|
| Cache size / hit ratio | Working-set size and effectiveness | Tune bound or eviction policy |
| Queue depth | Producer/consumer imbalance | Scale consumers or apply back-pressure |
| Rejection count | Overload and policy activation | Alert, shed load, or scale upstream |
| Collection operation latency p95/p99 | Tail-cost or contention regression | Profile hashes, locks, or GC |
| Allocation rate by collection type | Rebuilds in hot loops | Hoist or reuse collection |
| CME count | Mutation-during-iteration bugs | Fix ownership of mutation |

### API and publication rules

1. Prefer the narrowest interface that expresses the requirement (`List`, `Map`, `Queue`).
2. Return immutable snapshots from public APIs; do not leak mutable backing state.
3. Treat mutable objects used as keys as immutable by convention or remove/reinsert them safely.
4. Do not expose `HashMap` iteration order as an API contract.
5. Use `equals` and `hashCode` together, based on the same logical fields.
6. Never let an unbounded collection's lifetime exceed the business entity it caches.
7. In concurrent code, make the compound operation explicit: `merge`, `compute`, or a lock.

### Framework and library context

The JDK collections are the right default for in-process data. At larger scale, specialised
libraries add capabilities the JDK intentionally does not: Caffeine provides a bounded concurrent
cache with expiry and eviction policies; Guava offers cache and immutable-collection utilities;
Akka and Kafka provide durable or distributed queues where process loss matters. Use a JDK queue
for work within one process; use a broker when tasks must survive process failure, scale
horizontally, or be replayed.

### Java version notes

- **Java 8:** `computeIfAbsent`, `merge`, `removeIf`, and `ConcurrentHashMap` bin-level locking
  are available.
- **Java 9:** `List.of`, `Set.of`, and `Map.of` introduce immutable factories that reject nulls.
- **Java 10:** `List.copyOf`, `Set.copyOf`, and `Map.copyOf` introduce immutable copy factories.
- **Java 21+:** Sequenced collections (`List.getFirst`, `getLast`, `reversed`, and related
  operations) improve expressiveness but do not change the underlying data-structure trade-offs.
- **Future:** keep the API behind small abstractions when a specialised concurrent or persistent
  implementation may replace the JDK collection later.

---

## 13. Follow-up Learning

### Next in this folder

- [01 · List Implementations](./01-List-Implementations.md) — array growth, linked nodes, fail-fast
  iteration, and copy-on-write trade-offs.
- [02 · Map Implementations](./02-Map-Implementations.md) — `HashMap` buckets, resize splitting,
  treeification, `LinkedHashMap`, `TreeMap`, and `ConcurrentHashMap`.
- [03 · Set, Queue & Utilities](./03-Set-Queue-Utilities.md) — sets, heaps, deques, blocking
  queues, `Collections`, views, and immutable snapshots.

### Related topics elsewhere

- [Generics](../05-Generics/01-Generics.md) — PECS, wildcards, and safe collection APIs.
- [Java 8 Features — Stream API](../06-Java-8-Features/02-Stream-API.md) — collectors, laziness,
  and how streams interact with collection boundaries.
- [Concurrency — Locks & Atomic Classes](../08-Multithreading-Concurrency/03-Locks-Atomic.md) —
  atomic compound operations, memory visibility, and concurrent collections.
- [Concurrency — Executor Framework](../08-Multithreading-Concurrency/02-Executor-Framework.md) —
  work queues, rejection policies, and back-pressure.
- [OOP — equals, hashCode, toString](../01-Core-Java-OOP/08-OOP-equals-hashCode-toString.md) —
  the equality contract in full.
- [DSA — Complexity Reference](../11-DSA/01-DSA-Quick-Reference.md) — amortised analysis and
  data-structure trade-offs.

---

**Next Chapter:** → [05 · Generics](../05-Generics/01-Generics.md)
**Previous Chapter:** ← [03 · Set, Queue & Utilities](./03-Set-Queue-Utilities.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
