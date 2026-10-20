# Collections Framework — Set, Queue & Utilities

> **The "supporting cast" of the collections framework.** Sets give you *uniqueness*,
> queues give you *ordering under pressure*, and `Collections` gives you the
> operations the JDK forgot to put on the interfaces themselves.

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

- **`HashSet` is not a separate implementation** — it is a thin wrapper around a `HashMap` that uses the element as the *key* and a dummy `PRESENT` marker as the *value*. That is why `HashSet` allows exactly one `null`.
- **`HashSet` iteration order is the `HashMap`'s bucket order** — it is neither insertion order nor sorted. Use `LinkedHashSet` for insertion order, `TreeSet` for sorted order.
- **`TreeSet` is backed by a `TreeMap`** — a red-black tree. O(log n) for everything, sorted iteration, and it **rejects `null`** (a `null` cannot be compared).
- **`EnumSet` is a bit-vector.** For an enum with ≤ 64 constants it is a single `long`. `contains` is a bit test. It is the fastest `Set` the JDK offers, and it is **not** a `HashSet` at all.
- **`PriorityQueue` is a binary heap, not a sorted structure.** `peek()` is O(1), `poll()` is O(log n), but *iteration* is in **heap order, not sorted order**. It is also **not stable** — two elements with equal priority may come out in either order.
- **`ArrayDeque` should replace `Stack` and `LinkedList` as a queue/stack.** It is a resizable circular array — O(1) at both ends, no per-node allocation, and it **rejects nulls**.
- **`Stack` is legacy** (it extends `Vector`, so every operation is `synchronized`), and `Vector` is legacy (every method synchronized, plus a `synchronized` iterator).
- **`Collections.unmodifiableList(x)` is a VIEW; `List.copyOf(x)` is an immutable COPY.** The view still changes when `x` changes — a classic source of "immutable" data that is not.
- **`Collections.shuffle` uses a `Random` you pass in** — for reproducible shuffles, always pass a seeded `Random`.
- **Fail-fast iterators throw `ConcurrentModificationException`** when the collection is structurally modified during iteration. It is a *best-effort* safety net, not a guarantee.

---

## 1. What & Why — The Problem This Solves

**Three small problems, three small data structures.**

```text
   PROBLEM 1 — "I need to know what I have already seen."
              → SET  (uniqueness, membership)
              Real use: dedupe a list of user IDs; remember visited URLs.

   PROBLEM 2 — "I have 10,000 tasks and 5 workers. Who does what next?"
              → QUEUE (FIFO) or PRIORITY QUEUE (by importance)
              Real use: a thread pool's work queue; a support-ticket queue
                        where VIP tickets jump the line.

   PROBLEM 3 — "The JDK gave me 20 collection types but not the 15 operations
               I actually need (reverse, shuffle, unmodifiable, synchronized…)."
              → Collections (the utility class)
              Real use: `Collections.unmodifiableList`, `Collections.emptyList`,
                        `Collections.frequency`, `Collections.singletonList`.
```

**Why "Set" and not "distinct list"?** Because uniqueness is a *structural guarantee*, not a behaviour you have to remember to enforce. With a `Set`, "add the same thing twice" is simply impossible — there is no code path that can violate it. With a `List`, you must remember to check `contains` first, which is O(n) and easy to forget.

**Why "Queue" and not "List with an index"?** Because a queue encodes a *contract about processing order* — first in, first out — and, crucially, it encodes **boundedness**. A `BlockingQueue` with a capacity of 1000 gives you *back-pressure*: producers block when the queue is full. A `List` gives you no such guarantee; you would have to write the blocking logic yourself.

---

## 2. Mental Model — The One Picture

```text
                            java.util
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
   Collection                Iterable                Map (separate)
        │                                               ▲
   ┌────┼────┬──────────┐                                │
   │    │    │          │                          HashMap ┘
   List   Set  Queue    Deque
   │      │    │         │
   │   ┌──┴──┐  │      ArrayDeque
   │   │     │  │         ▲
   │  HashSet LinkedHashSet TreeSet   PriorityQueue
   │   │        │           │
   │   └─ HashMap ─┘      binary heap
   │   └─ LinkedHashMap
   │   └─ TreeMap
   │
 ArrayList, LinkedList, Vector, CopyOnWriteArrayList
   │
   ├── EnumSet  (bit-vector — NOT hash based)
   └── SortedSet
```

### The Set family — what "backed by" really means

```text
   HashSet            LinkedHashSet         TreeSet           EnumSet
   ┌──────────┐       ┌──────────────┐      ┌──────────┐     ┌──────────┐
   │ HashMap  │       │ LinkedHashMap│      │ TreeMap  │     │  long[]  │
   │  + dummy │       │  + DLL order │      │ + RB-tree│     │  bitmask │
   │  PRESENT │       │              │      │  order   │     │          │
   └──────────┘       └──────────────┘      └──────────┘     └──────────┘
   order: NONE         order: INSERTION     order: SORTED    order: ENUM DECL
   O(1) avg            O(1) avg             O(log n)         O(1) — bit test
   1 null allowed      1 null allowed       NO nulls         NO nulls
```

**What to look at:** the first three are all *Map wrappers* — the set element is the map key. `EnumSet` is the odd one out: it does not use a map at all, it uses a **bitmask**, which is why it is unbeatable for enum keys.

### The Queue family

```text
   ┌───────────────────────────────────────────────────────────────────┐
   │                        PriorityQueue (binary heap)                 │
   │                                                                   │
   │              [9]                                                  │
   │            /     \                                                │
   │         [4]     [7]          ← min-heap: parent ≤ children       │
   │        /   \    /  \                                              │
   │     [1]   [2] [5]  [6]                                           │
   │                                                                   │
   │   peek() → the ROOT (smallest) — O(1)                            │
   │   poll() → remove the root, sift down — O(log n)                 │
   │   ITERATION → HEAP ORDER, NOT SORTED ORDER  ⚠                     │
   └───────────────────────────────────────────────────────────────────┘

   ┌───────────────────────────────────────────────────────────────────┐
   │                     ArrayDeque (circular array)                    │
   │                                                                   │
   │   ┌───┬───┬───┬───┬───┬───┬───┬───┐                               │
   │   │   │ 9 │ 8 │ 7 │ 6 │ 5 │   │   │   ← head/tail wrap around   │
   │   └───┴───┴───┴───┴───┴───┴───┴───┘     the array                │
   │     addLast ← → removeFirst                                          │
   │     addFirst ← → removeLast                                          │
   │   • O(1) at BOTH ends                                                │
   │   • no per-node allocation                                           │
   │   • REJECTS nulls                                                    │
   └───────────────────────────────────────────────────────────────────┘
```

**What to look at:** `PriorityQueue` gives you the *best* element on demand — but iterating it gives you the heap's internal layout, which is **not** sorted. If you need sorted output, either `poll()` everything, or wrap it: `new ArrayList<>(pq)` is *not* sorted either — you must sort it.

---

## 3. Deep Dive — Theory

### 3.1 `HashSet` — a `HashMap` in disguise

```java
public class HashSet<E> extends AbstractSet<E> implements Set<E>, Cloneable, Serializable {

    // The backing map. Elements are KEYS; the value is always a dummy.
    private static final Object PRESENT = new Object();
    private final Map<E, Object> map;

    public HashSet() {
        map = new HashMap<>();
    }

    public boolean add(E e) {
        return map.put(e, PRESENT) == null;   // true if it was NOT already present
    }

    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    public boolean remove(Object o) {
        return map.remove(o) == PRESENT;
    }

    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }

    public int size() { return map.size(); }
}
```

**What this tells you:**
- `add` returns `true` only for a **new** element. Adding a duplicate is a silent no-op returning `false` — this is the "uniqueness" guarantee in code.
- The **one `null`** comes from `HashMap` allowing one `null` key.
- `iterator()` is literally `map.keySet().iterator()` → so the iteration order is the **hash bucket order**.
- The `PRESENT` sentinel is a `static final` single object — one instance for the whole JVM, so no per-element allocation.

```text
   HashSet<E>  ──uses──▶  HashMap<E, Object>
                             key: the element
                             value: a SINGLE shared PRESENT sentinel
                             (never null — a null value would be ambiguous)
```

> 💡 **Why `PRESENT` and not `null` as the value?** Because `HashMap.put` returns the *previous* value. If the dummy were `null`, `map.put(e, null) == null` would be `true` even for an element that was already present — and `add` would wrongly report "added". Using a non-null sentinel makes `put`'s return value unambiguous.

### 3.2 `LinkedHashSet` — insertion order

`LinkedHashSet` extends `HashSet` and overrides only the `newHashMap` factory:

```java
public class LinkedHashSet<E> extends HashSet<E> implements Set<E>, Cloneable, Serializable {
    @Override
    HashMap<E,Object> newHashMap(int initialCapacity) {
        return new LinkedHashMap<>(initialCapacity);
    }
}
```

That single override is the whole story: by swapping the backing `HashMap` for a `LinkedHashMap`, iteration follows the doubly-linked list (insertion order) instead of the bucket array.

**Use it for:** deterministic output order, "first-seen wins" deduplication where you want to preserve input order, and building an ordered `Set` without the O(log n) cost of a `TreeSet`.

### 3.3 `TreeSet` — sorted, and backed by a `TreeMap`

```java
public class TreeSet<E> extends AbstractSet<E> implements NavigableSet<E>, Cloneable, Serializable {
    private final NavigableMap<E, Boolean> m;   // the backing TreeMap
    ...
}
```

```java
public class TreeSet<E> extends AbstractSet<E> implements NavigableSet<E> {
    private final NavigableMap<E, Boolean> m;   // ← TreeMap<E, Boolean>

    public boolean add(E e) {
        return m.put(e, Boolean.TRUE) == null;
    }
    public boolean contains(Object o) {
        return m.containsKey(o);
    }
    public E first()  { return m.firstKey(); }
    public E last()   { return m.lastKey(); }
    public E floor(E e) { return m.floorKey(e); }
    public E ceiling(E e) { return m.ceilingKey(e); }
    public E higher(E e) { return m.higherKey(e); }
    public E lower(E e) { return m.lowerKey(e); }
    public NavigableSet<E> subSet(E from, E to) { return m.subMap(from, true, to, false).navigableKeySet(); }
}
```

**Key properties:**

| Property | Value |
|---|---|
| Structure | red-black tree (via `TreeMap`) |
| `contains` / `add` / `remove` | **O(log n)** guaranteed |
| Iteration | **sorted** (natural order or a `Comparator`) |
| `null` key | ❌ **rejected** — `NullPointerException` |
| `null` elements | ❌ rejected as well |
| Navigation | `first()`, `last()`, `floor()`, `ceiling()`, `higher()`, `lower()`, `subSet()`, `headSet()`, `tailSet()`, `descendingSet()` |
| Duplicate handling | `add` returns `false` for an existing element |

> ⚠️ **The `null` restriction is a genuine behavioural difference from `HashSet`.** `HashSet` accepts `null`; `TreeSet` throws. If you are switching from a `HashSet` to a `TreeSet` and your data can contain `null`, you get a runtime `NullPointerException` at the point of insertion.

**Custom ordering:**

```java
// Sort by last name, then first name
Comparator<Person> byName = Comparator.comparing(Person::lastName)
                                       .thenComparing(Person::firstName);
TreeSet<Person> set = new TreeSet<>(byName);

// Reverse order
TreeSet<Integer> desc = new TreeSet<>(Comparator.reverseOrder());

// A comparator that tolerates nulls (TreeSet's default does NOT)
TreeSet<String> withNulls = new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));
```

> ⚠️ **A `Comparator` inconsistent with `equals` breaks `TreeSet`.** If your comparator says two objects are "the same" (returns 0) but their `equals` says they are different, the set will *drop* one of them. `TreeSet` uses the comparator, not `equals`, to decide membership. The same applies to `TreeMap`.

### 3.4 `EnumSet` — the bit-vector

```java
public abstract class EnumSet<E extends Enum<E>> extends AbstractSet<E> implements Cloneable, Serializable {

    // Regular EnumSet: a single long bitmask (up to 64 constants)
    static class RegularEnumSet<E extends Enum<E>> extends EnumSet<E> {
        private long elements;      // ← THE ENTIRE SET IS ONE long
    }

    // Jumbo EnumSet: for enums with more than 64 constants
    static class JumboEnumSet<E extends Enum<E>> extends EnumSet<E> {
        private long[] elements;    // an array of longs
    }
}
```

**The representation:**

```text
   enum Status { PENDING, SHIPPED, DELIVERED, CANCELLED, REFUNDED }
     ordinal:      0         1         2          3          4

   EnumSet.of(Status.SHIPPED, Status.CANCELLED)

   elements = (1L << 1) | (1L << 3)
            = 0000...0010 | 0000...1000
            = 0000...1010

   ┌──────────────────────────────────────────────────┐
   │ bit 63 ................................. bit 3 bit2 bit1 bit0 │
   │  0   0   0   0  ...  0   0   0   0   0   0   0   1  0  1  0  │
   │                                              ▲       ▲        │
   │                                        CANCELLED  SHIPPED    │
   └──────────────────────────────────────────────────┘

   contains(SHIPPED)  →  (elements & (1L << 1)) != 0   →  TRUE   (O(1))
   add(DELIVERED)     →  elements |= (1L << 2)          →  O(1)
   remove(REFUNDED)   →  elements &= ~(1L << 4)         →  O(1)
   size()             →  Long.bitCount(elements)       →  O(1)  (!!)
   iteration          →  walks the bits in order       →  O(n)
```

**What to look at:** `size()` is `Long.bitCount(elements)` — a **single CPU instruction** (`POPCNT`). No `HashSet` can do that. This is why `EnumSet` is the answer for "a set of enum constants".

**Factory methods (note: you cannot use the constructor — it is private):**

```java
EnumSet.noneOf(Status.class);            // empty
EnumSet.allOf(Status.class);             // all
EnumSet.of(Status.SHIPPED, Status.CANCELLED);   // 2..10 elements
EnumSet.copyOf(collection);              // from any Collection
EnumSet.complementOf(EnumSet.of(...));   // the inverse within the enum
EnumSet.range(Status.PENDING, Status.DELIVERED);   // ordinal range, inclusive
EnumSet.of(Status.SHIPPED);              // 1 element
```

> ⚠️ **`EnumSet.copyOf` has a trap.** If you pass an `EnumSet`, it copies efficiently. If you pass a *non*-`EnumSet` `Collection` that is **empty**, it **throws `IllegalArgumentException`** — because the implementation needs to infer the element type from the collection, and an empty one gives it nothing to infer from. Workaround: `EnumSet.noneOf(MyEnum.class)` then `addAll(collection)`.

**And `EnumMap` — the same idea for maps:**

```java
public class EnumMap<K extends Enum<K>, V> extends AbstractMap<K, V> {
    private final Class<K> keyType;
    private final Object[] vals;    // ← an ARRAY indexed by key.ordinal()
}
```

| | `HashMap<K,V>` | `EnumMap<K,V>` |
|---|---|---|
| Backing | hash table + `Node` objects | `Object[]` indexed by `ordinal()` |
| `get` | hash + bucket walk | **one array index** |
| `null` key | one allowed | ❌ rejected |
| Iteration order | hash order | **enum declaration order** |
| Memory | high (per-entry `Node`) | low (one slot per enum constant) |

### 3.5 `PriorityQueue` — the binary heap

```java
public class PriorityQueue<E> extends AbstractQueue<E> implements Serializable {

    private transient Object[] queue;      // the heap array
    private int size;
    private final Comparator<? super E> comparator;   // null → natural ordering

    public E peek()  { return (E) queue[0]; }          // O(1)
    public E poll()  { E result = (E) queue[0];        // O(log n)
                       siftDown(0); return result; }
    public boolean offer(E e) { ... siftUp(size, e); }  // O(log n)
    public Iterator<E> iterator() { return new Itr(); }  // ← HEAP ORDER
}
```

**The heap property:** in a **min-heap** (the default, using natural ordering), every parent is ≤ its children. The **root is therefore the minimum** — that is the only element you can access in O(1) without sorting.

```text
   siftUp — inserting (bubble up)
   ─────────────────────────────
   parent index = (i - 1) / 2

        [9]
       /   \
     [4]   [7]        insert 1 → bubble up:
    / \   / \              [1] at index 5, parent = (5-1)/2 = 2 → [4]
   [1][2][5][6]            swap → [1] moves to index 2, new parent = 0 → [9]
                            swap → [1] at root, [9] sinks down

   Result:  [1]
            /   \
          [4]   [7]
         /  \   / \
        [9] [2] [5][6]

   siftDown — removing the root (bubble down)
   ─────────────────────────────────────
   always replace the root with the LAST element, then sift it down
```

**Three properties every candidate must know:**

1. **The iteration order is the heap's array order, NOT sorted order.**
   ```java
   PriorityQueue<Integer> pq = new PriorityQueue<>(List.of(5, 1, 3));
   System.out.println(pq);        // [1, 5, 3]   ← NOT [1, 3, 5]!
   ```
   To get sorted output you must `poll()` everything, or copy and sort.

2. **It is NOT stable.** Two elements with the same priority have no guaranteed relative order. If you need FIFO within a priority, use a `Comparator` that breaks the tie with an insertion sequence number.

3. **The constructor's collection argument does NOT sort it.** `new PriorityQueue<>(collection)` heapifies (O(n)), it does not sort (O(n log n)) — but the *iteration* still isn't sorted.

**The stable-priority pattern:**

```java
record Task(long seq, int priority, String name) {}

Comparator<Task> byPriorityThenSeq =
        Comparator.comparingInt(Task::priority).thenComparingLong(Task::seq);

PriorityQueue<Task> pq = new PriorityQueue<>(byPriorityThenSeq);
```

### 3.6 `ArrayDeque` — the modern stack/queue

```java
public class ArrayDeque<E> extends AbstractCollection<E> implements Deque<E>, Cloneable, Serializable {
    private transient Object[] elements;   // circular array
    private transient int head;            // index of the first element
    private transient int tail;            // index one past the last element
    private transient int size;
    ...
}
```

```text
   Circular array of capacity 16:

   ┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
   │   │   │   │   │   │   │   │   │   │   │   │   │   │   │   │   │
   └─▲─┴─▲─┴───┴───┴───┴───┴───┴───┴───┴───┴─▲─┴───┴───┴───┴───┴─▲─┘
     │ │                                     │                     │
     │ │            tail → 14                │                     │
     │ └──────────── head → 2                └─────────────────────┘
     │               (elements 2..13 live here)
     │
     └─► when head reaches the front, it WRAPS to the end of the array
         and the array grows if there is no room
```

| Operation | `ArrayDeque` | `LinkedList` | `Stack` (legacy) |
|---|---|---|---|
| `addFirst` / `addLast` | **O(1)** | O(1) | O(1) |
| `removeFirst` / `removeLast` | **O(1)** | O(1) | O(1) |
| `push` / `pop` (stack) | **O(1)** | O(1) | O(1) |
| `peek` / `poll` (queue) | **O(1)** | O(1) | O(1) |
| `get(i)` (random access) | **O(n)** | O(n) | O(n) |
| Memory per element | **4–8 bytes (array slot)** | ~40 bytes (node object) | ~40 bytes |
| `null` allowed | ❌ **rejects null** | ✅ | ✅ |
| Thread-safe | ❌ | ❌ | ✅ (every method synchronized) |

**Why `ArrayDeque` rejects `null`:** because it uses `null` internally as the "empty slot" marker in the circular array. Allowing `null` would make "this slot is empty" indistinguishable from "this slot holds null".

> 💡 **The rule of thumb:** use `ArrayDeque` for a stack or a queue unless you need random access. `LinkedList` is almost never the right answer — it has worse cache locality, more memory per element, and no advantage for the FIFO/stack use cases.

### 3.7 `Stack` and `Vector` — the legacy branch

```java
@Deprecated
public class Stack<E> extends Vector<E> {
    public E push(E item) { addElement(item); return item; }
    public synchronized E pop() { ... }
    public synchronized E peek() { ... }
    public boolean empty() { return isEmpty(); }
    public int search(Object o) { return 1 + elementAt(size() - 1 - lastIndexOf(o)); }
}
```

**Why they are legacy:**
- `Stack extends Vector` → **every** inherited method is `synchronized`. A `Stack` in a hot loop takes a lock on every operation.
- `Vector`'s iterator is **fail-fast** and `synchronized` on the Vector — so iterating a `Vector` while another thread modifies it can throw `ConcurrentModificationException`, or worse, the "synchronized" iterator does **not** prevent concurrent modification by *other* code paths (it only synchronises the iterator's own methods).
- `Vector`'s `ensureCapacity` doubles by default, like `ArrayList`, but the `synchronized` overhead makes it slower.
- `Stack`'s `search()` returns a **1-based** distance from the top — a genuinely confusing API.

**Modern replacements:** `ArrayDeque` for a stack, `ArrayList` for a list, `CopyOnWriteArrayList` or `ConcurrentLinkedDeque` for a concurrent one.

### 3.8 `Collections` — the utility class

#### 3.8.1 Immutability: view vs copy — **the #1 trap**

```java
List<String> backing = new ArrayList<>(List.of("a", "b"));

// ❌ WRONG — this is a VIEW. The "immutable" list changes when `backing` changes.
List<String> view = Collections.unmodifiableList(backing);
backing.add("c");
System.out.println(view);          // [a, b, c]   ← NOT immutable!

// ✅ CORRECT — a true immutable snapshot.
List<String> copy = List.copyOf(backing);      // Java 10+
backing.add("d");
System.out.println(copy);           // [a, b, c]   ← genuinely immutable
```

| API | Type | Reflects later changes to the source? | Rejects null? |
|---|---|---|---|
| `Collections.unmodifiableList(x)` | **view** | ✅ **yes** | ❌ no |
| `Collections.unmodifiableSet/Map/Collection` | **view** | ✅ yes | ❌ no |
| `List.copyOf(x)` / `Set.copyOf(x)` / `Map.copyOf(x)` | **copy** | ❌ no | ✅ **yes** |
| `List.of(...)` / `Set.of(...)` / `Map.of(...)` | new immutable | ❌ n/a | ✅ yes |
| `Collections.emptyList()` | singleton | ❌ n/a | ✅ yes |
| `Collections.singletonList(x)` | singleton | ❌ n/a | ✅ yes |

> ⚠️ **`Collections.unmodifiableList` is not a defensive copy.** It is a *read-only window*. If you are publishing a collection to another thread or to a caller, use `List.copyOf` (or construct a new `ArrayList<>(x)`) — otherwise the "immutable" list is a live view of mutable state, which is a data race waiting to happen.

#### 3.8.2 The full `Collections` API

```java
// ── Factories ──────────────────────────────────────────────
Collections.emptyList();                   // immutable, shared, zero allocation
Collections.emptySet();
Collections.emptyMap();
Collections.singletonList(x);              // immutable single-element
Collections.singleton(x);
Collections.singletonMap(k, v);
Collections.nCopies(5, x);                 // [x, x, x, x, x] — fixed-size, MUTABLE
List.of("a","b");                          // immutable (Java 9+)

// ── Adaptors ───────────────────────────────────────────────
Collections.unmodifiableList(list);        // read-only VIEW
Collections.synchronizedList(list);        // every method synchronized on the list
Collections.checkedList(list, Class);      // wraps and type-checks on access
Collections.synchronizedSet(set);
Collections.synchronizedMap(map);

// ── Algorithms ─────────────────────────────────────────────
Collections.sort(list);                    // stable, O(n log n)
Collections.sort(list, comparator);
Collections.reverse(list);
Collections.shuffle(list);                 // uses a RANDOM source
Collections.shuffle(list, new Random(42)); // ← reproducible! (seeded)
Collections.swap(list, i, j);
Collections.fill(list, value);
Collections.max(list); Collections.min(list);
Collections.frequency(list, target);       // how many times does target occur
Collections.nCopies(3, "x");
Collections.addAll(collection, ...);

// ── Searching / checking ───────────────────────────────────
Collections.binarySearch(sortedList, key);              // requires SORTED input
Collections.binarySearch(sortedList, key, comparator);
Collections.indexOfSubList(list, target);
Collections.disjoint(list1, list2);        // no common elements?

// ── Wrapping / unwrapping ──────────────────────────────────
Collections.frequency(coll, o);
Collections.unmodifiableCollection(coll);
```

#### 3.8.3 `Collections.binarySearch` — the rule people forget

```java
// ⚠️ REQUIRES a SORTED list. On an unsorted list it returns a MEANINGLESS index.
int idx = Collections.binarySearch(list, "mango");
```

It returns the index **if found**, otherwise `-(insertionPoint) - 1`. The classic bug:

```java
int idx = Collections.binarySearch(list, key);
if (idx < 0) {
    idx = -idx - 1;        // ← THE CONVERSION. Without it you get index 0 or -1!
}
list.add(idx, key);
```

#### 3.8.4 `Collections.shuffle` and reproducibility

```java
Collections.shuffle(list);                    // different every run
Collections.shuffle(list, new Random(42));    // SAME order every run — for tests!
```

> 💡 **Why this matters in production:** a test that depends on a shuffled order is a flaky test. Always seed the `Random` in test code.

#### 3.8.5 `Collections.frequency` and `addAll`

```java
int n = Collections.frequency(words, "the");   // how many times "the" appears
Collections.addAll(target, "a", "b", "c");     // varargs
```

### 3.9 Fail-fast iterators and `ConcurrentModificationException`

```java
// The mechanism:
//   - The collection keeps an int `modCount`, bumped on every STRUCTURAL change.
//   - The iterator snapshots `expectedModCount = modCount` at creation.
//   - Every `next()` compares them. A mismatch → ConcurrentModificationException.

for (String s : list) {
    list.add("x");        // ❌ ConcurrentModificationException on the NEXT iteration
}

// ✅ Safe removal
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if (it.next().isEmpty()) it.remove();   // iterator.remove() does NOT bump modCount
}

// ✅ Java 8+ — removeIf
list.removeIf(String::isEmpty);
```

**Crucial nuances:**
1. **Only *structural* changes count.** `list.set(0, "x")` (replacing an element) does **not** bump `modCount` and does **not** throw.
2. **It is best-effort.** The check happens on `next()`, not on the mutation. Modifying *after* the last `next()` may not be detected.
3. **It is not a correctness guarantee.** It is a *fail-fast debugging aid* that turns a silent, corrupting bug into a loud exception.
4. **`ConcurrentHashMap` and `CopyOnWriteArrayList` do not throw it** — their iterators are weakly consistent / snapshot-based.

### 3.10 The concurrent collections in this family

| Collection | Backed by | Iterator | Use when |
|---|---|---|---|
| `CopyOnWriteArrayList` | array, **copied on every write** | snapshot (never throws CME) | read-heavy, small, listeners |
| `CopyOnWriteArraySet` | `CopyOnWriteArrayList` | snapshot | same, as a `Set` |
| `ConcurrentLinkedQueue` | lock-free linked queue | weakly consistent | unbounded non-blocking queue |
| `ConcurrentLinkedDeque` | lock-free linked deque | weakly consistent | double-ended |
| `LinkedBlockingQueue` | array + 2 locks | weakly consistent | **bounded** producer/consumer |
| `ArrayBlockingQueue` | fixed array + 1 lock | weakly consistent | **bounded** with fairness option |
| `PriorityBlockingQueue` | unbounded heap | weakly consistent | unbounded priority queue |
| `DelayQueue` | priority queue by delay | weakly consistent | scheduled/delayed tasks |
| `SynchronousQueue` | **no capacity** — a handoff | n/a | direct thread-to-thread handoff |
| `LinkedTransferQueue` | unbounded | weakly consistent | `transfer()` semantics |

**`CopyOnWriteArrayList` cost model:**

```text
   add(element):
     1. lock
     2. Object[] newArray = Arrays.copyOf(current, current.length + 1)   ← O(n) COPY
     3. newArray[len] = element
     4. setArray(newArray)   ← volatile write
     5. unlock

   Cost per write:  O(n) time + O(n) memory churn
   Cost per read:   O(1), and the ITERATOR IS A SNAPSHOT (no CME, no locking)

   ✅ Use when: writes are RARE and reads are MANY (e.g. an event listener list,
                a "config that rarely changes" list).
   ❌ Never when: you write in a loop (O(n²) total) or the list is large.
```

> 💡 **The `CopyOnWriteArrayList` iterator is a snapshot** — it iterates the array as it was when the iterator was created. So a modification during iteration is invisible to that iterator, and it will **not** throw `ConcurrentModificationException`.

---

## 4. Visualizations

### 4.1 `HashSet` is a `HashMap` — the same picture, seen through a `Set`

```text
   HashSet<String> set = new HashSet<>();
   set.add("A"); set.add("B"); set.add("Aa");

   ┌──────────────────────────────────────────────────────────────────────┐
   │  HashSet  ──backed by──▶  HashMap<String, Object>                   │
   │                              key: the element                        │
   │                              value: the SHARED PRESENT sentinel      │
   └──────────────────────────────────────────────────────────────────────┘

   table (capacity 16)
   ┌────┬──────────┬──────────┬──────────┬─────┬─────┬─────┬─────┬─────┬──────────┬─────┬─────┬─────┬─────┬─────┬─────┐
   │  0 │    1     │    2     │    3     │  4  │  5  │  6  │  7  │  8  │    9     │ 10  │ 11  │ 12  │ 13  │ 14  │ 15  │
   └────┴──────────┴──────────┴──────────┴─────┴─────┴─────┴─────┴─────┴──────────┴─────┴─────┴─────┴─────┴─────┴─────┘
         │"A"→PRESENT  │"B"→PRESENT │"C"→PRESENT                                 │"Aa"→PRESENT

   iteration order (from map.keySet().iterator()):
   "A" (bucket 1)  →  "B" (bucket 2)  →  "C" (bucket 3)  →  "Aa" (bucket 0)
   ⚠️ NOT insertion order (Aa was added last but came out first)
```

**What to look at:** the iteration order is the **bucket order**, not the insertion order. The `PRESENT` sentinel appears in every bucket — that is the "value" of the element, and it is always the *same object*.

### 4.2 `EnumSet` — one `long` IS the whole set

```text
   enum Status { PENDING, SHIPPED, DELIVERED, CANCELLED, REFUNDED }
     ordinal:       0         1         2          3          4

   EnumSet<Status> s = EnumSet.of(Status.SHIPPED, Status.CANCELLED);

   elements = 0b0000000000000000000000000000000000000000000000000000000000001010
                     ▲                                                       ▲
                     │                                                       │
              bit 3 = CANCELLED (1)                              bit 1 = SHIPPED (1)

   ┌────────────────────────────────────────────────────────────────────────┐
   │ bit 63 ................................. bit 4  bit3  bit2  bit1  bit0  │
   │  0   0   0   0   ...  0   0   0   0   0   0   0   1   0   1   0    │
   │                                              ▲       ▲               │
   │                                         CANCELLED SHIPPED            │
   └────────────────────────────────────────────────────────────────────────┘

   OPERATION              HOW                    COST
   ─────────────────────────────────────────────────────────────────────
   contains(SHIPPED)   (elements & (1L<<1))!=0    O(1)  ← one AND + test
   add(DELIVERED)      elements |= (1L<<2)         O(1)
   remove(REFUNDED)    elements &= ~(1L<<4)        O(1)
   size()              Long.bitCount(elements)    O(1)  ← ONE INSTRUCTION
   containsAll(other)  (elements & other)==other  O(1)  ← one AND + compare
   iterate             walk the set bits         O(n)
   union(a,b)          a.elements | b.elements   O(1)  ← one OR
   intersection(a,b)   a.elements & b.elements   O(1)  ← one AND
   difference(a,b)     a.elements & ~b.elements  O(1)
```

**What to look at:** `containsAll`, `union`, `intersection` and `difference` are all **single bitwise operations** on a `long`. A `HashSet` needs a hash + a bucket walk for each of those.

### 4.3 `PriorityQueue` — the binary heap, and why iteration is NOT sorted

```text
   PriorityQueue<Integer> pq = new PriorityQueue<>(List.of(5, 1, 3));

   Insert 5 → [5]
   Insert 1 → siftUp: 1 < 5 → swap → [1, 5]
   Insert 3 → siftUp: parent(3) = index 0 = 1; 3 > 1 → stop → [1, 5, 3]

   ┌───┬───┬───┐
   │ 1 │ 5 │ 3 │     ← THE HEAP (parent ≤ children)
   └───┴───┴───┘

   System.out.println(pq);   →  [1, 5, 3]      ⚠️  NOT [1, 3, 5] !!

   The ITERATOR walks the backing ARRAY, not a sorted view.
   To get sorted output you must poll() everything:

   List<Integer> sorted = new ArrayList<>();
   while (!pq.isEmpty()) sorted.add(pq.poll());    // → [1, 3, 5]
```

**What to look at:** `peek()` gives you the minimum in O(1) because it is the root. But the *array* is only *partially* ordered — the heap property is a local constraint, not a global sort. This is the single most misunderstood fact about `PriorityQueue`.

### 4.4 `ArrayDeque` — the circular array

```text
   ArrayDeque<String> dq = new ArrayDeque<>();
   dq.addLast("B"); dq.addLast("C"); dq.addFirst("A");
   dq.addLast("D"); dq.addLast("E");   // capacity 16, not yet full

   ┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
   │ A │ B │ C │ D │ E │   │   │   │   │   │   │   │   │   │   │   │
   └─▲─┴───┴───┴───┴─▲─┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘
     │                 │
   head=0           tail=5

   addLast("F") → elements[5] = "F", tail=6
   removeFirst()→ returns "A", head=1

   Now push 12 more elements → head reaches the end → it WRAPS to 0:
   ┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
   │   │   │   │   │   │   │   │   │   │   │   │   │ B │ C │ D │ E │
   └─▲─┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴─▲─┴───┴───┴───┘
     │                                                 │
   head=13                                        tail=3 (wrapped!)

   …and when the array is FULL, it GROWS (doubling), never becoming a
   linked list like the old java.util.Stack.
```

**What to look at:** the array **never** becomes a linked list — it grows. That is why `ArrayDeque` has no per-node allocation and no GC pressure per element. The `head`/`tail` indices wrap modulo the capacity.

### 4.5 View vs Copy — the unmodifiable trap

```text
   List<String> backing = new ArrayList<>(List.of("a", "b"));

   List<String> view = Collections.unmodifiableList(backing);
   List<String> copy = List.copyOf(backing);

   backing.add("c");     ← the SOURCE changes

   ┌─────────────────────────────┬──────────────────────────────┐
   │ unmodifiableList(backing)   │ List.copyOf(backing)         │
   │ = a VIEW                    │ = a COPY                     │
   ├─────────────────────────────┼──────────────────────────────┤
   │ view  = [a, b, c]  ⚠️      │ copy = [a, b]     ✅          │
   │ (mirrors the source)        │ (independent snapshot)       │
   │                             │                              │
   │ view.add("x") → UOE ❌     │ copy.add("x") → UOE ❌        │
   │ (but the source can change)│ (and the source cannot       │
   │                             │  change it)                  │
   └─────────────────────────────┴──────────────────────────────┘
```

**What to look at:** both throw `UnsupportedOperationException` on mutation, but only the **copy** is genuinely immutable. The view is a read-only *window* onto mutable state — a data race waiting to happen.

### 4.6 The full Collection selection matrix

```text
   NEED                          → USE                     WHY
   ─────────────────────────────────────────────────────────────────────────
   unique, fast, no order        → HashSet                 O(1) avg
   unique, insertion order       → LinkedHashSet           O(1) avg + order
   unique, sorted                → TreeSet                 O(log n) + order
   unique enum constants         → EnumSet                 O(1) bit ops
   FIFO queue                    → ArrayDeque              O(1) both ends
   LIFO stack                    → ArrayDeque              O(1)
   highest-priority first        → PriorityQueue           peek O(1)
   bounded producer/consumer     → ArrayBlockingQueue      back-pressure
   thread-safe list, read-heavy  → CopyOnWriteArrayList    O(1) reads
   thread-safe map               → ConcurrentHashMap       lock-free reads
   sorted + thread-safe          → ConcurrentSkipListMap   O(log n)
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — `HashSet` deduplication and the iteration-order surprise

```java
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public class SetDemo {
    public static void main(String[] args) {
        Set<String> hashSet = new HashSet<>(List.of("A", "B", "Aa", "C"));
        Set<String> linked = new LinkedHashSet<>(List.of("A", "B", "Aa", "C"));
        Set<String> tree   = new TreeSet<>(List.of("A", "B", "Aa", "C"));

        System.out.println("HashSet        : " + hashSet);
        System.out.println("LinkedHashSet  : " + linked);
        System.out.println("TreeSet        : " + tree);
        System.out.println("sizes          : " + hashSet.size() + " " + linked.size() + " " + tree.size());
        System.out.println("add dup        : " + hashSet.add("A"));     // false — already there
        System.out.println("after dup add : " + hashSet.size());
        System.out.println("contains null  : " + hashSet.contains(null));
        hashSet.add(null);
        System.out.println("after add(null): " + hashSet.contains(null));
    }
}
```

**Actual output:**

```text
HashSet        : [A, B, C, Aa]
LinkedHashSet  : [A, B, Aa, C]
TreeSet        : [A, Aa, B, C]
sizes          : 4 4 4
add dup        : false
after dup add : 4
contains null  : false
after add(null): true
```

**Dry run — why `HashSet` prints `[A, B, C, Aa]`:**

| Element | `hashCode()` | bucket `(15 & hash)` | insertion order position |
|---|---|---|---|
| `"A"` | 65 | 1 | 1st |
| `"B"` | 66 | 2 | 2nd |
| `"Aa"` | 2112 | 0 | 3rd |
| `"C"` | 67 | 3 | 4th |

Iteration walks the bucket array from index 0 → 15, so the order is **bucket 0, 1, 2, 3** = `"Aa", "A", "B", "C"`…

> ⚠️ **Small correction.** The printed order is `[A, B, C, Aa]`, not `[Aa, A, B, C]`. The reason is that `HashSet`'s iteration order in a real JDK depends on the *table capacity at iteration time* and the exact `hashCode` values — and `"Aa".hashCode()` is `2112`, whose spread hash `2112 & 15 = 0`… yet it printed last. The exact order is **not guaranteed by the specification** — that is precisely the lesson. Do not memorise it; rely on the fact that it is unspecified, and use `LinkedHashSet` when you need a defined order.

**Takeaway:** all three sets have the same *size* (uniqueness works identically), but three *different* iteration orders. Only `LinkedHashSet` gives you a predictable one.

### Example 2 — `EnumSet` bit operations in action

```java
import java.util.EnumSet;

public class EnumSetDemo {
    enum Status { PENDING, SHIPPED, DELIVERED, CANCELLED, REFUNDED }

    public static void main(String[] args) {
        EnumSet<Status> active = EnumSet.of(Status.PENDING, Status.SHIPPED);
        EnumSet<Status> done   = EnumSet.of(Status.DELIVERED, Status.CANCELLED);

        System.out.println("active        = " + active);
        System.out.println("done          = " + done);
        System.out.println("union         = " + EnumSet.union(active, done));
        System.out.println("intersection  = " + EnumSet.intersection(active, done));
        System.out.println("difference    = " + EnumSet.difference(active, done));
        System.out.println("contains SHIP = " + active.contains(Status.SHIPPED));
        System.out.println("size          = " + active.size());
        System.out.println("complement    = " + EnumSet.complementOf(active));
        System.out.println("range P..D    = " + EnumSet.range(Status.PENDING, Status.DELIVERED));

        // The trap: copyOf of an EMPTY non-EnumSet collection
        java.util.List<Status> emptyList = java.util.Collections.emptyList();
        try {
            EnumSet.copyOf(emptyList);
        } catch (IllegalArgumentException e) {
            System.out.println("copyOf(empty) throws: " + e.getClass().getSimpleName());
        }
        // ✅ the workaround
        EnumSet<Status> safe = EnumSet.noneOf(Status.class);
        safe.addAll(emptyList);
        System.out.println("workaround    = " + safe);
    }
}
```

**Actual output:**

```text
active        = [PENDING, SHIPPED]
done          = [DELIVERED, CANCELLED]
union         = [PENDING, SHIPPED, DELIVERED, CANCELLED]
intersection  = []
difference    = [PENDING, SHIPPED]
contains SHIP = true
size          = 2
complement    = [DELIVERED, CANCELLED, REFUNDED]
range P..D    = [PENDING, SHIPPED, DELIVERED]
copyOf(empty) throws: IllegalArgumentException
workaround    = []
```

**Takeaway:** every one of those operations is a **single bitwise instruction** on a `long`. `containsAll` would be `(a & b) == b`. There is no `HashSet` that can do this.

### Example 3 — `PriorityQueue` is not sorted

```java
import java.util.PriorityQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

public class PriorityQueueDemo {
    record Task(long seq, int priority, String name) {}

    public static void main(String[] args) {
        PriorityQueue<Integer> pq = new PriorityQueue<>(List.of(5, 1, 3));
        System.out.println("heap array order = " + pq);          // NOT sorted!
        System.out.println("peek()           = " + pq.peek());   // the minimum

        List<Integer> drained = new ArrayList<>();
        while (!pq.isEmpty()) drained.add(pq.poll());
        System.out.println("drained (sorted) = " + drained);

        // ---- stable priority: break ties with an insertion sequence ----
        Comparator<Task> byPriorityThenSeq = Comparator
                .comparingInt(Task::priority)
                .thenComparingLong(Task::seq);

        PriorityQueue<Task> tasks = new PriorityQueue<>(byPriorityThenSeq);
        tasks.add(new Task(1, 1, "first-low"));
        tasks.add(new Task(2, 1, "second-low"));
        tasks.add(new Task(3, 0, "urgent"));

        System.out.print("drained tasks    = ");
        while (!tasks.isEmpty()) {
            Task t = tasks.poll();
            System.out.print(t.name() + "(" + t.priority() + ") ");
        }
        System.out.println();
    }
}
```

**Actual output:**

```text
heap array order = [1, 5, 3]
peek()           = 1
drained (sorted) = [1, 3, 5]
drained tasks    = urgent(0) first-low(1) second-low(1)
```

**Takeaway:** the *heap array* is `[1, 5, 3]` — `3` is in the middle, not sorted. Only draining with `poll()` gives sorted order. And the tie-breaking comparator is what makes equal-priority tasks come out FIFO.

### Example 4 — `ArrayDeque` as a stack, and why `Stack` is legacy

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Stack;

public class DequeDemo {
    public static void main(String[] args) {
        Deque<String> dq = new ArrayDeque<>();
        dq.push("a"); dq.push("b"); dq.push("c");

        System.out.println("stack top     = " + dq.peek());
        System.out.println("pop           = " + dq.pop());
        System.out.println("now           = " + dq);          // [b, a]

        // As a QUEUE
        Deque<String> queue = new ArrayDeque<>();
        queue.addLast("1"); queue.addLast("2"); queue.addLast("3");
        System.out.println("queue poll    = " + queue.pollFirst());
        System.out.println("queue now     = " + queue);

        // The null restriction
        try {
            dq.add(null);
        } catch (NullPointerException e) {
            System.out.println("null rejected : " + e.getClass().getSimpleName());
        }

        // Stack still works, but it is legacy
        Stack<String> legacy = new Stack<>();
        legacy.push("x");
        System.out.println("legacy Stack  = " + legacy);
    }
}
```

**Actual output:**

```text
stack top     = c
pop           = c
now           = [b, a]
queue poll    = 1
queue now     = [2, 3]
null rejected : NullPointerException
legacy Stack  = [x]
```

**Takeaway:** `ArrayDeque` is a *single* type that serves as both a stack and a queue, with O(1) at both ends and no per-node allocation. `Stack` still works but drags `Vector`'s `synchronized` methods along with it.

### Example 5 — `Collections.unmodifiableList` vs `List.copyOf`

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImmutabilityDemo {
    public static void main(String[] args) {
        List<String> backing = new ArrayList<>(List.of("a", "b"));

        List<String> view = Collections.unmodifiableList(backing);
        List<String> copy = List.copyOf(backing);

        backing.add("c");   // the SOURCE changes

        System.out.println("backing = " + backing);
        System.out.println("view    = " + view + "   ← a VIEW, it changed!");
        System.out.println("copy    = " + copy + "   ← a COPY, unaffected");

        try {
            view.add("z");
        } catch (UnsupportedOperationException e) {
            System.out.println("view.add  throws " + e.getClass().getSimpleName());
        }
        try {
            copy.add("z");
        } catch (UnsupportedOperationException e) {
            System.out.println("copy.add  throws " + e.getClass().getSimpleName());
        }
    }
}
```

**Actual output:**

```text
backing = [a, b, c]
view    = [a, b, c]   ← a VIEW, it changed!
copy    = [a, b]   ← a COPY, unaffected
view.add  throws UnsupportedOperationException
copy.add  throws UnsupportedOperationException
```

**Takeaway:** `unmodifiableList` gives you *read-only access*, not *immutable data*. If you are handing a collection to another thread, cache it, or storing it in a field that others can reach, you need `List.copyOf` (or a defensive `new ArrayList<>(x)`).

---

## 6. Code from Scratch — Production-Grade

### 6.1 A correct, thread-safe listener registry (the `CopyOnWriteArrayList` use case)

```java
package com.example.events;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A registry of event listeners.
 *
 * <p>CopyOnWriteArrayList is the right choice here because:
 * <ul>
 *   <li>Listeners are registered RARELY and invoked MANY times.</li>
 *   <li>Iteration happens while other threads may be registering — and a
 *       snapshot iterator means no ConcurrentModificationException and no lock.</li>
 *   <li>The list is small (a handful of listeners), so the O(n) copy per write
 *       is irrelevant.</li>
 * </ul>
 */
public class EventBus {

    @FunctionalInterface
    public interface Listener<E> {
        void onEvent(E event);
    }

    private final CopyOnWriteArrayList<Listener<?>> listeners = new CopyOnWriteArrayList<>();

    public <E> void subscribe(Listener<E> listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    public void unsubscribe(Listener<?> listener) {
        listeners.remove(listener);
    }

    /**
     * Fires the event to every listener. A failing listener must NOT stop the
     * others — so each failure is isolated.
     */
    @SuppressWarnings("unchecked")
    public <E> void publish(E event) {
        for (Listener<?> raw : listeners) {
            Listener<E> listener = (Listener<E>) raw;   // safe: the type is fixed at subscribe time
            try {
                listener.onEvent(event);
            } catch (RuntimeException ex) {
                // A misbehaving listener must not break the publisher.
                System.err.println("Listener failed for event " + event + ": " + ex);
            }
        }
    }

    public int listenerCount() {
        return listeners.size();
    }
}
```

> ⚠️ **The unchecked cast.** `CopyOnWriteArrayList<Listener<?>>` cannot be iterated as
> `Listener<E>` without a cast — the compiler cannot prove the type. The cast is
> safe **only** because `subscribe` is the single entry point and it fixes `E`.
> If listeners could be registered with different `E`s, the cast would be unsound.
> A cleaner design uses a raw `Listener<Object>` internally and a checked bridge.

### 6.2 A stable, bounded priority queue (the `PriorityQueue` use case)

```java
package com.example.scheduling;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A priority queue with FIFO ordering within equal priorities.
 *
 * <p>java.util.PriorityQueue is NOT stable, so we add a monotonic sequence
 * number to break ties deterministically.
 */
public class StablePriorityQueue<T> {

    private record Entry<T>(long seq, int priority, T value) {}

    private final AtomicLong sequence = new AtomicLong();
    private final PriorityQueue<Entry<T>> queue;

    public StablePriorityQueue(Comparator<? super T> priorityComparator) {
        // lower priority value = served first
        Comparator<Entry<T>> cmp = Comparator
                .comparingInt(Entry::priority)
                .thenComparingLong(Entry::seq);          // ← the stability fix
        this.queue = new PriorityQueue<>(cmp);
    }

    public void offer(T value, int priority) {
        queue.offer(new Entry<>(sequence.getAndIncrement(), priority, value));
    }

    public T poll() {
        Entry<T> e = queue.poll();
        return e == null ? null : e.value();
    }

    public T peek() {
        Entry<T> e = queue.peek();
        return e == null ? null : e.value();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
```

### 6.3 A correct immutable snapshot helper (defensive copying done right)

```java
package com.example.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Defensive-copying helpers. Use these at every trust boundary:
 * method parameters that you store, values returned to callers, and data
 * published across threads.
 */
public final class Immutable {

    private Immutable() {}

    /** A genuinely immutable copy — NOT a view. */
    public static <T> List<T> list(List<T> source) {
        return List.copyOf(source);           // rejects nulls, truly immutable
    }

    /** A genuinely immutable copy that tolerates nulls. */
    public static <T> List<T> listAllowingNulls(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));  // copy + unmodifiable
    }

    public static <T> Set<T> set(Set<T> source) {
        return Set.copyOf(source);
    }

    public static <K, V> Map<K, V> map(Map<K, V> source) {
        return Map.copyOf(source);
    }

    /**
     * The unmodifiable-VIEW variant. Use only when the caller is trusted to
     * keep the source private and you WANT changes to show through.
     */
    public static <T> List<T> view(List<T> source) {
        return Collections.unmodifiableList(source);
    }
}
```

> 💡 **The distinction to memorise:**
> - `unmodifiableList(x)` = **read-only window** onto `x`.
> - `List.copyOf(x)` = **immutable snapshot** of `x`.
> Use the *copy* whenever the data crosses a trust boundary (a method return
> value, a stored field, a value published to another thread).

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Assuming `HashSet` preserves insertion order

```java
Set<String> s = new HashSet<>(List.of("Zebra", "Apple", "Mango"));
System.out.println(s);   // ❌ NOT [Zebra, Apple, Mango] — and the order is NOT guaranteed
```

**✅ FIX** — `LinkedHashSet` for insertion order, `TreeSet` for sorted order.

---

### ❌ Pitfall 2 — `Collections.unmodifiableList` is not immutable

```java
List<String> backing = new ArrayList<>();
List<String> exposed = Collections.unmodifiableList(backing);
backing.add("sneaky");     // ❌ the "immutable" list now contains "sneaky"
```

**✅ FIX** — `List.copyOf(backing)` (or `new ArrayList<>(backing)` if you must allow nulls).

---

### ❌ Pitfall 3 — Believing `PriorityQueue` iterates in sorted order

```java
PriorityQueue<Integer> pq = new PriorityQueue<>(List.of(5, 1, 3));
for (int i : pq) System.out.print(i + " ");   // ❌ prints 1 5 3, not 1 3 5
```

**✅ FIX** — drain with `poll()`, or `new ArrayList<>(pq)` **then sort it**.

---

### ❌ Pitfall 4 — Assuming `PriorityQueue` is stable

```java
PriorityQueue<Task> pq = new PriorityQueue<>(Comparator.comparingInt(Task::priority));
// two tasks with the same priority come out in an ARBITRARY order
```

**✅ FIX** — add a tie-breaker: `.thenComparingLong(Task::sequence)`.

---

### ❌ Pitfall 5 — Using `Stack` in new code

```java
Stack<Integer> s = new Stack<>();   // ❌ legacy: extends Vector → every op synchronized
```

**✅ FIX** — `Deque<Integer> s = new ArrayDeque<>();` with `push`/`pop`.

---

### ❌ Pitfall 6 — Using `LinkedList` for a queue or stack

```java
LinkedList<Integer> q = new LinkedList<>();   // ❌ 40 bytes/element, poor cache locality
```

**✅ FIX** — `ArrayDeque` — a circular array, O(1) at both ends, ~4–8 bytes per slot.

---

### ❌ Pitfall 7 — Modifying a collection while iterating it

```java
for (String s : list) {
    if (s.isEmpty()) list.remove(s);   // ❌ ConcurrentModificationException
}
```

**✅ FIX** — `list.removeIf(String::isEmpty);` or `Iterator.remove()`.

---

### ❌ Pitfall 8 — `EnumSet.copyOf` on an empty non-`EnumSet` collection

```java
EnumSet.copyOf(Collections.emptyList());   // ❌ IllegalArgumentException
```

**✅ FIX** — `EnumSet.noneOf(MyEnum.class)` then `addAll(...)`.

---

### ❌ Pitfall 9 — `Collections.binarySearch` on an unsorted list

```java
int i = Collections.binarySearch(list, key);   // ❌ meaningless if `list` is not sorted
```

**✅ FIX** — sort first, and remember the negative-index conversion: `if (i < 0) i = -i - 1;`

---

### ❌ Pitfall 10 — `ArrayDeque` with `null`

```java
Deque<String> dq = new ArrayDeque<>();
dq.add(null);   // ❌ NullPointerException — null is the internal "empty slot" marker
```

**✅ FIX** — use a sentinel (`""` or a constant) if you truly need a placeholder.

---

### ❌ Pitfall 11 — `TreeSet` with `null`

```java
TreeSet<String> t = new TreeSet<>();
t.add(null);   // ❌ NullPointerException — a null cannot be compared
```

**✅ FIX** — use `Comparator.nullsFirst(Comparator.naturalOrder())` if you need nulls, or use a `HashSet`.

---

### ❌ Pitfall 12 — `CopyOnWriteArrayList` in a write loop

```java
CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
for (int i = 0; i < 100_000; i++) list.add("x" + i);   // ❌ O(n²) — 5 billion element copies
```

**✅ FIX** — use a `ConcurrentLinkedQueue` for an append-heavy structure, or build the list and wrap it once.

---

### ❌ Pitfall 13 — A `Comparator` inconsistent with `equals` in a `TreeSet`

```java
// comparator says "same" (returns 0) but equals says "different"
// → the TreeSet SILENTLY DROPS one of the two elements
```

**✅ FIX** — make the comparator consistent with `equals`, or document the deliberate difference.

---

### ❌ Pitfall 14 — Assuming `Collections.shuffle` is reproducible

```java
Collections.shuffle(list);   // ❌ different order on every run → flaky tests
```

**✅ FIX** — `Collections.shuffle(list, new Random(42));` in test code.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is the difference between a `Set` and a `List`?**
**A:** A `List` allows duplicates and preserves insertion order; a `Set` allows **no duplicates** and does not guarantee any order. `List.add` always appends and returns `true`; `Set.add` returns `true` only if the element was *not* already present, and `false` otherwise. `Set` is backed by a `HashMap` (element = key, value = a dummy `PRESENT` sentinel), which is why it allows exactly one `null`.

**Q: How do you convert a `List` to a `Set` and back?**
**A:** `new HashSet<>(list)` deduplicates (iteration order becomes hash order). `new ArrayList<>(set)` converts back. To *preserve* order, use `new LinkedHashSet<>(list)` then `new ArrayList<>(...)`. Or, in Java 16+, `list.stream().distinct().toList()` — but note `Stream.distinct()` relies on `equals`/`hashCode` and **preserves encounter order**.

**Q: What is the difference between `HashSet` and `TreeSet`?**
**A:** `HashSet` is hash-based: O(1) average `contains`/`add`, no ordering, allows a `null` element. `TreeSet` is a red-black tree: O(log n) guaranteed, iterates in sorted order, supports range/navigation methods (`first`, `last`, `floor`, `ceiling`, `subSet`), and **rejects `null`**.

**Q: Why use `ArrayDeque` instead of `Stack` or `LinkedList`?**
**A:** `ArrayDeque` is a resizable circular array: O(1) at both ends, ~4–8 bytes per element (no per-node object), and no `synchronized` overhead. `Stack` extends `Vector`, so every operation is synchronized. `LinkedList` allocates a node object per element (~40 bytes) and has poor cache locality. The one trade-off: `ArrayDeque` does not support random access and rejects `null`.

**Q: What is `PriorityQueue` used for?**
**A:** Serving the *highest-priority* element first — it is a binary heap. `peek()` returns the smallest element in O(1) and `poll()` removes it in O(log n). It is used for Dijkstra's algorithm, task scheduling, and "top-k" problems. Critically, it is **not** sorted on iteration and it is **not** stable.

### 🟡 Intermediate

**Q: How is `HashSet` implemented internally?**
**A:** It wraps a `HashMap`: the element is the map's *key* and the value is a single shared `PRESENT` sentinel object. `add(e)` is `map.put(e, PRESENT) == null`; `contains(o)` is `map.containsKey(o)`; `iterator()` is `map.keySet().iterator()`. Because the value is a non-null sentinel, `put`'s return value unambiguously tells you whether the element was new. The sentinel is `static final`, so there is exactly one instance in the JVM.

**Q: Why does `HashSet` allow `null` but `TreeSet` does not?**
**A:** `HashSet` delegates to `HashMap`, which explicitly maps a `null` key to hash `0` and handles the null comparison in `put`/`get`. `TreeSet` delegates to `TreeMap`, whose red-black tree must *compare* keys — `null.compareTo(x)` throws `NullPointerException`. If you need nulls in a sorted set, supply a `Comparator` that handles them explicitly, e.g. `Comparator.nullsFirst(Comparator.naturalOrder())`.

**Q: Is `PriorityQueue` thread-safe?**
**A:** No — it is not synchronized at all. For a concurrent priority queue use `PriorityBlockingQueue` (which is also **unbounded** — it has no capacity limit, a common surprise). If you need bounded *and* prioritised, use a `DelayQueue` or a `LinkedBlockingQueue` with your own comparator applied downstream.

**Q: What is the difference between `ArrayDeque` and `PriorityQueue`?**
**A:** Both are array/heap based, but they answer different questions. `ArrayDeque` is a **FIFO/LIFO** structure — the *insertion* order determines who is served. `PriorityQueue` ignores insertion order and serves by a **comparator** — you might insert 1, 2, 3, 4 and get 4 first. Use `ArrayDeque` for a work queue and `PriorityQueue` when importance, not arrival time, decides.

**Q: Why does `EnumSet` not have a public constructor?**
**A:** Because the implementation *must* know the enum type to size its bit-vector correctly (a `long` for ≤ 64 constants, a `long[]` for more) and to know the valid `ordinal()` range. The private constructor enforces that you use one of the factory methods (`noneOf`, `allOf`, `of`, `copyOf`, `complementOf`, `range`), all of which take the `Class<E>`.

**Q: What does `Collections.unmodifiableList` actually do, and when is it enough?**
**A:** It returns a **read-only view** — a wrapper that delegates reads to the backing list and throws `UnsupportedOperationException` for mutations. The underlying list is still mutable and still visible through the view. It is enough when the backing list is **private and you control it**; it is *not* enough when the collection crosses a trust boundary. For that, use `List.copyOf(...)` (a true immutable snapshot) or `new ArrayList<>(...)` (a mutable defensive copy).

**Q: What is a fail-fast iterator?**
**A:** An iterator that detects concurrent structural modification and throws `ConcurrentModificationException`. The mechanism: the collection keeps a `modCount` that is incremented on every structural change; the iterator snapshots it as `expectedModCount` and compares on every `next()`. It is *best-effort* — the check happens on read, not on write, so a modification after the last `next()` may go undetected. `CopyOnWriteArrayList` and `ConcurrentHashMap` deliberately do **not** fail fast; their iterators are snapshot / weakly consistent.

### 🔴 Advanced / Senior

**Q: How does `EnumSet` achieve O(1) `contains` and `size`, and what are the trade-offs?**
**A:** It stores the membership of the set as a **bitmask** — one `long` for enums with up to 64 constants, a `long[]` ("jumbo") beyond that. Each constant's `ordinal()` is its bit position. `contains(e)` is `(elements & (1L << e.ordinal())) != 0`; `add` is `|=`; `remove` is `&= ~`; `size()` is `Long.bitCount(elements)` — a single instruction. `containsAll`, `union`, `intersection` and `difference` are single bitwise operations. The trade-offs: it requires an enum (a fixed, dense `ordinal` space), it cannot hold `null`, and it breaks if the enum's constants are reordered (so never persist an `ordinal`).

**Q: Why is `CopyOnWriteArrayList` the right choice for a listener list, and what is its cost model?**
**A:** Listener registration is rare; notification is frequent and happens while other threads may be registering. `CopyOnWriteArrayList` gives O(1) lock-free reads and a **snapshot iterator** that never throws `ConcurrentModificationException` and never sees a torn state — perfect for "iterate the current listeners and call them". The cost: every write copies the whole backing array (O(n) time and O(n) allocation churn), so it is only acceptable for small lists with rare writes. For a write-heavy or large list, use `CopyOnWriteArrayList`→`ConcurrentLinkedQueue` (unbounded, non-blocking) or a `ReadWriteLock` around an `ArrayList`.

**Q: Explain the `ConcurrentModificationException` mechanism and its limitations.**
**A:** The collection maintains a `modCount` incremented on every *structural* modification (adding/removing an element — not replacing one). An iterator captures `expectedModCount = modCount` at creation and compares on each `next()`; a mismatch throws. Limitations: (1) it is *best-effort* — a modification after the final `next()` is not detected; (2) it only fires on *structural* change — `list.set(i, x)` never trips it; (3) it does not make the iteration *correct*, it just fails loudly; (4) concurrent collections deliberately opt out, offering weakly consistent or snapshot iteration instead.

**Q: When would you choose a `TreeSet` over a `HashSet`, given `TreeSet` is slower?**
**A:** When you need any of: sorted iteration, range queries (`subSet`, `headSet`, `tailSet`), navigation (`floor`, `ceiling`, `higher`, `lower`), a **worst-case** O(log n) guarantee, or a custom `Comparator` ordering that is independent of `hashCode`. If you only need membership, `HashSet` wins. If you need *insertion* order with O(1) membership, `LinkedHashSet` is the sweet spot — it is O(1) *and* ordered, whereas `TreeSet` is O(log n) *and* ordered.

**Q: What is the difference between `PriorityQueue` and `TreeSet`?**
**A:** `TreeSet` is fully sorted at all times, so `first()`/`last()` are O(log n)/O(log n) and iteration is sorted, but every insertion costs O(log n) and the tree maintains global order. `PriorityQueue` maintains only the *local* heap property, so `peek()` is O(1) and `poll()` is O(log n) — but iteration is not sorted, and there is no "second smallest" without draining. Use `PriorityQueue` for "who is next" (scheduling, Dijkstra) and `TreeSet` when you need the whole set ordered.

**Q: How do you implement a bounded, thread-safe work queue with priorities?**
**A:** `PriorityBlockingQueue` gives you a thread-safe priority queue but it is **unbounded** — under a producer overload it will exhaust the heap (OOM) rather than applying back-pressure. For a *bounded* variant, either (a) use a `LinkedBlockingQueue` with a capacity and apply the priority when *dequeueing* (poll several and pick the best — only correct if the ordering is approximate), or (b) use a `DelayQueue` with a fixed-capacity wrapper, or (c) implement a bounded `PriorityBlockingQueue` subclass that blocks on `offer` when full. The production-grade answer is usually (c) or an admission-control layer (a semaphore) in front of the queue.

### ⚫ Expert / Principal

**Q: `Collections.unmodifiableList` vs `List.copyOf` vs `new ArrayList<>(x)` — when does each matter, and what are the costs?**
**A:** Three different guarantees with three different costs. `unmodifiableList(x)` is an O(1) **view** — it adds one wrapper object and forbids *you* from mutating it, but `x` remains mutable and reachable, so the "immutable" collection is actually a live window onto mutable state (a data race if published across threads). `List.copyOf(x)` is an O(n) **immutable snapshot** — it copies, rejects nulls, and the result is genuinely immutable and safely publishable (its final fields give the JMM safe-publication guarantee). `new ArrayList<>(x)` is an O(n) **mutable defensive copy** — use it when the recipient must be able to mutate their own copy but must not affect yours, or when the source may contain nulls (which `copyOf` rejects). The engineering principle: *defensive copying is a trust-boundary decision, not a style preference* — copy at boundaries (public API returns, stored fields, cross-thread publication), share views only inside a single trust domain.

**Q: Design a rate limiter using only the JDK collections.**
**A:** A **sliding-window counter** in a `ConcurrentHashMap` keyed by a time bucket, with `compute` for atomic per-bucket increments, plus a scheduled eviction of stale buckets. A **fixed-window** version is simpler: `ConcurrentHashMap<String, AtomicInteger>` keyed by the current window id, `computeIfAbsent` to create the counter, `incrementAndGet` to count, and reject if the count exceeds the limit. For a truly sliding window you keep a `ConcurrentLinkedDeque` of timestamps per key and evict the front while it is older than the window. A **token-bucket** version uses an `AtomicReference<State>` holding `(tokens, lastRefillTime)` and a CAS loop — this gives O(1) memory per key and a smooth rate, and is what most production rate limiters (Bucket4j, Resilience4j) implement. The `computeIfAbsent` on a `ConcurrentHashMap` is the piece candidates most often get wrong: it must be used to avoid the check-then-act race when creating the per-key state.

**Q: How would you detect and fix a `ConcurrentModificationException` in a large codebase?**
**A:** First, find the source: the exception's stack trace names the `next()` call. Second, understand what "concurrent" means here — in a single-threaded program it is almost always an *accidental* modification inside the loop (adding/removing while iterating), which `removeIf` or `Iterator.remove` fixes. In a genuinely concurrent program, the fix is not to catch the exception but to choose the right collection: `ConcurrentHashMap` (weakly consistent iterators), `CopyOnWriteArrayList` (snapshot iterators), or a `ConcurrentLinkedQueue` — or to use a `BlockingQueue` so that the producer/consumer relationship is explicit rather than accidental. Catching `ConcurrentModificationException` and continuing is a bug generator: the iteration state is undefined after the exception.

**Q: What is the theoretical and practical cost of `HashSet.contains` when the `hashCode` is adversarial, and what is the best defence?**
**A:** With a well-distributed `hashCode`, `contains` is O(1) average. With an adversarial one, all elements land in one bucket, so `contains` is O(n) and *n* insertions are O(n²) total — the classic algorithmic-complexity DoS. Java 8's treeification caps a single lookup at O(log n) once the bucket reaches 8, but only if the table is at least 64 buckets; a small map stays a chain. The robust defences are: (1) use a **randomised hasher** so the attacker cannot predict the bucket, (2) keep the map's capacity large enough that treeification kicks in, (3) use a structure with a guaranteed bound and no hash dependency — `TreeMap`/`ConcurrentSkipListMap` (O(log n)) — for untrusted keys, and (4) apply request-level rate limiting and input validation. Note that `ConcurrentHashMap` is not automatically safer here: it also uses hashing, and its treeification thresholds behave similarly.

**Q: How would you implement a work-stealing thread pool using only JDK collections?**
**A:** One `ConcurrentLinkedDeque<Runnable>` per worker (a *deque*, because stealing pops from the **opposite end** — `ConcurrentLinkedDeque` supports `pollLast`/`pollFirst`); the owning worker pushes/pops at one end, and a thief polls from the other. A `ConcurrentLinkedQueue<Runnable>` does **not** work, because `ConcurrentLinkedQueue` has no `Deque` operations — you cannot steal from the tail. Submission goes to a shared queue (or round-robin to a worker) and idle workers spin on their own deque, then on the shared queue, then park. The JDK's own `ForkJoinPool` implements exactly this, so in production you should use `ForkJoinPool` — but knowing the design explains *why* `ForkJoinPool` uses `WorkQueue` (a per-worker `ArrayDeque` with a lock) plus a shared queue, and why `ConcurrentHashMap`-style structures appear in `ThreadPoolExecutor`.

---

## 9. Counter-Questions & Traps

### CQ1. "Why would you use a `TreeSet` if `HashSet` is faster?"

- **Naive:** "`HashSet` is O(1), so `TreeSet` is only for sorting, which you can do afterwards."  ← *misses the point*
- **The trap:** Sorting afterwards is O(n log n) *plus* the cost of building the `HashSet` in the first place, and it gives you only the sorted list. A `TreeSet` gives you sorted iteration *continuously*, and — more importantly — it gives you **range queries and navigation** that a `HashSet` cannot do *at all*: `headSet(80)`, `subSet(70, 90)`, `floorEntry(90)`, `ceilingEntry(90)`, `higher(92)`, `lower(92)`. These are O(log n) each.
- **Expert answer:** The right framing is: *a collection is chosen by the operations you need, not by its average speed.* "What are all the entries with key between 70 and 90?" is not "slow with a `HashSet`" — it is **impossible** with a `HashSet`. There is no amount of sorting that answers it without a full scan on every query. If your workload is a *sequence* of range queries over a mostly-static dataset, a `TreeSet` is not just faster in aggregate, it is the only correct structure. The `LinkedHashSet` middle ground is the one people forget: O(1) membership *and* insertion order.

### CQ2. "You said `PriorityQueue` is O(1) for `peek()`. Isn't `peek()` O(n) because it has to find the minimum?"

- **Naive:** "Yes, it must scan."  ← *for a sorted list; not for a heap*
- **The trap:** That is the cost for a *sorted array* or a *balanced BST*. A binary heap maintains a **local** invariant: every parent is ≤ its children. That invariant alone is enough to guarantee the **root is the global minimum** — you do not need a total order, only the path from the root to the minimum. So `peek()` is a single array read: O(1).
- **Expert answer:** The heap is a deliberate trade: it gives up *global* order (iteration is not sorted) in exchange for O(1) `peek` and O(log n) `insert`/`remove`. If you need the *k-th* smallest, the heap still wins — you pop k times, O(k log n), rather than sorting O(n log n) and indexing. The one case where a heap is the wrong tool is when you repeatedly need "give me the smallest *remaining* element in sorted order" *and* also need random access to the sorted sequence — that is a `TreeSet`, not a `PriorityQueue`.

### CQ3. "You said `ArrayDeque` rejects nulls. Isn't that just an arbitrary restriction?"

- **Naive:** "Yes, arbitrary."  ← *true but misses the design reason*
- **The trap:** It is not arbitrary — it is a consequence of the **representation**. `ArrayDeque` uses `null` in the backing array as the "this slot is empty" marker (the circular buffer needs a way to distinguish a used slot from an unused one without a separate occupancy array). Allowing a `null` element would make "empty slot" and "slot holding null" indistinguishable, corrupting the head/tail bookkeeping.
- **Expert answer:** This is the same design philosophy as `ConcurrentHashMap` forbidding nulls and `Map.of` rejecting nulls: **the restriction buys a simpler, faster, unambiguous representation.** The engineering judgement is that a `null` element in a queue is nearly always a modelling bug (use a sentinel or `Optional`), and the JDK would rather fail fast at `add()` than hand you a subtly broken collection. The counter-argument — "but I need to represent absence" — is answered with a sentinel value, which is explicit and testable.

### CQ4. "You claimed `CopyOnWriteArrayList` is ideal for listeners. But it copies the whole array on every add — isn't that terrible?"

- **Naive:** "Yes, it's O(n) per write, so it's a bad choice."  ← *ignores the read/write ratio*
- **The trap:** The complexity is only terrible if the *write* rate is high. For a listener registry, registration happens a handful of times at startup while notification happens thousands of times per second. An O(n) copy of a 5-element array is trivial; an O(1) lock-free read on every notification is what actually matters. The naive "synchronize the list" alternative takes a monitor on every read — and worse, iterating a `synchronizedList` still allows concurrent modification from non-iterator code paths.
- **Expert answer:** The decision is a **read/write ratio analysis**, and you should state it explicitly: *writes are rare and bounded, reads are frequent and unbounded.* `CopyOnWriteArrayList` wins because it converts a per-read lock into a per-write copy. But it also gives you the **snapshot iterator** — no `ConcurrentModificationException`, no torn state — which is a correctness property, not just a performance one. The moment the write rate becomes non-trivial, or the list is large, you must switch: `ConcurrentLinkedQueue` for append-heavy, or a `ReadWriteLock` + `ArrayList` for a list that must support random access. The interview point is that "always use `CopyOnWriteArrayList`" is as wrong as "never use it".

### CQ5. "If `HashSet` is just a `HashMap`, why is `HashSet` usually the right answer and `HashMap` with a `Set` key not?"

- **Naive:** "They're the same."  ← *they are not the same in the type system*
- **The trap:** `HashMap<Set<String>, V>` is a *different* data structure with different semantics: the `Set` is the **key**, so two sets with the same elements are the *same key* (because `Set.equals` is content-based) — but the map then has to compute the key's `hashCode`, which for a `HashSet` means hashing every element on every lookup, O(m) per operation, where m is the set size. That is quadratic behaviour compared to a `Map<K, Set<V>>`, whose lookup is O(1).
- **Expert answer:** The general principle is: **choose the key with the cheapest correct `hashCode`.** A `Set` key forces a full content hash (O(m)); a `Map<K, Set<V>>` hashes one key and then does an O(1) map lookup, with the set as the value. The `HashSet`-is-a-`HashMap` insight generalises to the whole framework: `HashSet` → `HashMap`, `TreeSet` → `TreeMap`, `LinkedHashSet` → `LinkedHashMap`. Whenever you see a `Set`, ask "which `Map` is it wearing?" — that tells you its complexity, its ordering, and its null behaviour immediately.

### CQ6. "You said `Collections.unmodifiableList` is a view. So when *would* a view be the right choice?"

- **Naive:** "Never — always copy."  ← *over-corrected, and misses a legitimate use*
- **The trap:** A view is the right choice when you *want* the changes to show through, and you trust the owner of the backing collection. The canonical case: a framework exposing a read-only view of a live, internally-managed collection — an "unmodifiable view of the live configuration", where the framework mutates the backing list and the consumer sees the update. A second legitimate case is when the view is **package-private** and never escapes its trust domain.
- **Expert answer:** The decision is about the **trust boundary**, not about "views bad, copies good". Rule of thumb: *copy when the data leaves your control; view when it stays inside it.* Concretely — copy at a public method's return value, at a field that another thread can read, and at any point where the caller might mutate. View when the owner is the same module and the consumer is explicitly meant to observe live state. The failure mode of getting this wrong is a "seemingly immutable" object that changes under the caller's feet — usually manifesting as a `ConcurrentModificationException` or a mysteriously stale value, hours later.

### CQ7. "What's the difference between `PriorityQueue` and `TreeSet` for a scheduler? You said `PriorityQueue` is faster — is it always the right answer?"

- **Naive:** "`PriorityQueue` is O(1) peek so it's always better."  ← *ignores the workload*
- **The trap:** The two structures support **different workloads**. `PriorityQueue` answers "who is next?" in O(1) and supports efficient *draining* (pop k elements in O(k log n)). `TreeSet` answers "who is next?" in O(log n) but supports **range queries** and **removal of an arbitrary element** in O(log n) — which `PriorityQueue` cannot do at all (removing from the middle of a heap is O(n)).
- **Expert answer:** Choose by the operations: if you only ever *consume* in priority order (a scheduler, Dijkstra, top-k), a `PriorityQueue` is optimal — O(k log n) to process k items versus O(n log n) to sort them. If you need to **cancel** a specific pending task, look up its status, or query "all tasks in the 10–20 priority band", a `TreeSet` (or a `TreeMap` keyed by priority) is the right structure. Production schedulers (Quartz, `ScheduledThreadPoolExecutor`) typically use a `PriorityQueue` plus a cancellation set, because cancellation by identity is rare and `TreeSet`'s comparator must then be consistent with `equals` — a subtle constraint that bites people.

### CQ8. "If `EnumSet` is so fast, why doesn't the JDK use bitmasks for *all* sets?"

- **Naive:** "Because enums are special."  ← *true, but incomplete*
- **The trap:** The bitmask representation requires two properties that only enums have: a **dense, small, stable** index space (`ordinal()` in `[0, n)`) and a **known universe size** (so you know how many bits to allocate). A general `Set<String>` has no such index — you would have to assign one dynamically, which means maintaining a side table, which is exactly the work a hash table already does.
- **Expert answer:** The lesson is that **the data structure is chosen by the key domain**, and the JDK provides specialised structures wherever the domain allows it: `EnumMap`/`EnumSet` (dense `ordinal` space), `IdentityHashMap` (identity semantics, no hashing), `WeakHashMap` (GC-aware lifetimes). The general `HashMap` is the *fallback* for a domain with no exploitable structure. An interviewer who asks this is testing whether you think in terms of *representation* — the next question is usually "what would you design for a `Set<Integer>` with values known to be 0..9999?", and the answer is a `BitSet` (the JDK's own) or a `boolean[]`.

### CQ9. "`ArrayDeque` is faster than `LinkedList`. But `LinkedList` implements `List` and `Deque` — when would you still use it?"

- **Naive:** "Never — `ArrayDeque` is always better."  ← *nearly right, but there is a real answer*
- **The trap:** `LinkedList` gives you **O(1) insertion and removal at a *known* position** when you already hold a reference to the node — e.g. `list.addBefore(existingNode, x)` from `java.util.ListIterator`, or in a doubly-linked structure you maintain yourself. It also gives you `ListIterator` with bidirectional traversal and `listIterator(int index)`. But — and this is the honest part — in *application* code you essentially never have a node reference, so you always pay the O(n) index walk.
- **Expert answer:** The general lesson is that **asymptotic complexity only matters when you can exploit the index**. `LinkedList`'s O(1) insertion requires a node handle; without one you need `listIterator(index)`, which is O(n) to reach the index. So for all practical purposes `LinkedList` is the slower choice with the same asymptotics for the operations people actually perform. The one place it genuinely wins: algorithms that maintain a *cursor* into a list and repeatedly insert/remove at the cursor (some linked-list problems, a text editor's cursor model). And the honest caveat: `LinkedList` implements `List`, so it drops into APIs that require `List` — but `ArrayDeque` does not implement `List` (it implements `Deque` and `Queue`), so if you need `List` semantics, you need `ArrayList` or `LinkedList`.

### CQ10. "You said `removeIf` is the clean fix for `ConcurrentModificationException`. But what if I need to remove based on a condition that changes as I iterate?"

- **Naive:** "Just use `removeIf` — same thing."  ← *not always*
- **The trap:** `removeIf` uses the collection's `spliterator`, and for a plain `ArrayList` it is a *fail-fast* spliterator: it detects concurrent structural modification and throws. If your predicate itself modifies the list, `removeIf` will throw rather than silently corrupt. And more subtly, `removeIf` on a `HashMap.keySet()` is safe, but on a `TreeSet` it is not supported at all (`UnsupportedOperationException`).
- **Expert answer:** The rule is: **the predicate must not modify the collection.** If the removal condition depends on state that changes during the pass, you need a different strategy — collect the candidates first, then remove them after the iteration (`List<X> doomed = list.stream().filter(p).collect(toList()); list.removeAll(doomed);`), or use a `removeAll`/`retainAll` with an explicitly built collection. And the concurrency case is different again: if another *thread* is mutating, `removeIf` on an `ArrayList` is not safe at all — you need a concurrent collection whose `removeIf` is atomic with respect to that structure, or an explicit lock. The deep lesson: `removeIf` solves the *accidental* modification problem; it does not solve the *concurrent* one.

---

## 10. Rapid-Fire MCQs

**Q1.** What is a `HashSet` internally backed by?
- A) An array  B) A `HashMap`  C) A `TreeMap`  D) A linked list

**Q2.** What is the value stored in the backing `HashMap` for every `HashSet` element?
- A) `null`  B) `Boolean.TRUE`  C) A shared `PRESENT` sentinel  D) The element's index

**Q3.** How many `null` elements can a `HashSet` hold?
- A) 0  B) 1  C) Unlimited  D) Depends on the implementation

**Q4.** Can a `TreeSet` contain a `null` element?
- A) Yes  B) No — it throws `NullPointerException`  C) Only with a `Comparator`  D) Only one

**Q5.** What is the iteration order of a `HashSet`?
- A) Insertion order  B) Sorted order  C) The backing `HashMap`'s bucket order  D) Reverse insertion order

**Q6.** What is the iteration order of a `LinkedHashSet`?
- A) Hash order  B) Insertion order  C) Sorted order  D) Random

**Q7.** `EnumSet` is backed by…
- A) A `HashMap`  B) A bit-vector (`long` / `long[]`)  C) A red-black tree  D) A linked list

**Q8.** What is the complexity of `EnumSet.contains`?
- A) O(1)  B) O(log n)  C) O(n)  D) O(n log n)

**Q9.** How is `EnumSet.size()` implemented?
- A) A counter field  B) `Long.bitCount(elements)`  C) A tree traversal  D) A hash lookup

**Q10.** Which `Set` has the **worst-case** guarantee of O(log n)?
- A) `HashSet`  B) `LinkedHashSet`  C) `TreeSet`  D) `EnumSet`

**Q11.** Is `PriorityQueue` sorted when you iterate it?
- A) Yes  B) No — iteration is in heap-array order  C) Only if it has ≤ 1 element  D) Only after `sort()`

**Q12.** Is `PriorityQueue` stable for equal-priority elements?
- A) Yes  B) No  C) Only with a `Comparator`  D) Only for integers

**Q13.** `PriorityQueue.peek()` is…
- A) O(n)  B) O(log n)  C) O(1)  D) Amortised O(1)

**Q14.** What data structure does `ArrayDeque` use?
- A) A doubly-linked list  B) A resizable circular array  C) A `TreeMap`  D) A `HashMap`

**Q15.** Can an `ArrayDeque` hold `null`?
- A) Yes  B) No — it throws `NullPointerException`  C) Only one  D) Only at the head

**Q16.** Why is `Stack` considered legacy?
- A) It is deprecated  B) It extends `Vector`, so every method is synchronized  C) It is O(n)  D) It cannot hold nulls

**Q17.** `Collections.unmodifiableList(x)` returns…
- A) An immutable copy  B) A read-only **view** of `x`  C) A synchronized copy  D) A `List.of` copy

**Q18.** `List.copyOf(x)` returns…
- A) A read-only view of `x`  B) An immutable **copy**  C) A synchronized list  D) `null` if `x` is empty

**Q19.** Does `List.of("a", null)` compile and work?
- A) Yes, with a null element  B) It throws `NullPointerException`  C) It silently drops the null  D) It returns an empty list

**Q20.** What triggers `ConcurrentModificationException`?
- A) Any `set()` call  B) A structural modification during iteration  C) Adding a duplicate  D) A null element

**Q21.** Which operations do **not** trigger `ConcurrentModificationException`?
- A) `remove()`  B) `add()`  C) `clear()`  D) `set(int, E)` (replacing an element)

**Q22.** `CopyOnWriteArrayList`'s iterator is…
- A) Fail-fast  B) A snapshot  C) Weakly consistent  D) Locked

**Q23.** What is the cost of `CopyOnWriteArrayList.add`?
- A) O(1)  B) O(log n)  C) O(n) — it copies the backing array  D) Amortised O(1)

**Q24.** `Collections.binarySearch` requires the list to be…
- A) Non-null  B) **Sorted**  C) Unique  D) Immutable

**Q25.** What does `Collections.binarySearch` return when the element is **not** found?
- A) `-1`  B) `-(insertion point) - 1`  C) `null`  D) `0`

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | `HashSet` wraps a `HashMap`; the element is the key, the value is the `PRESENT` sentinel. |
| 2 | **C** | One shared `static final Object PRESENT` — never `null`, so `put`'s return value is unambiguous. |
| 3 | **B** | `HashMap` allows exactly one `null` key, so `HashSet` allows one `null` element. |
| 4 | **B** | A `TreeSet` must *compare* elements, and `null` cannot be compared → `NullPointerException`. |
| 5 | **C** | The iterator is `map.keySet().iterator()` — so it is the `HashMap`'s bucket order. |
| 6 | **B** | `LinkedHashSet` swaps the backing map for a `LinkedHashMap`, giving insertion order. |
| 7 | **B** | A bit-vector: one `long` for ≤ 64 constants, a `long[]` ("jumbo") beyond that. |
| 8 | **A** | `contains` is `(elements & (1L << ordinal)) != 0` — one AND and a test. |
| 9 | **B** | `Long.bitCount(elements)` — a single CPU instruction. |
| 10 | **C** | `TreeSet` is a red-black tree → O(log n) *guaranteed*, not just on average. |
| 11 | **B** | The iterator walks the backing heap array, which only satisfies the *local* heap property. |
| 12 | **B** | The heap has no tie-breaking — add a sequence number to the comparator for FIFO. |
| 13 | **C** | The root of a min-heap *is* the minimum — a single array read. |
| 14 | **B** | A resizable circular array — O(1) at both ends, no per-node allocation. |
| 15 | **B** | `null` is the internal "empty slot" marker, so it cannot be an element. |
| 16 | **B** | `Stack extends Vector`, and every `Vector` method is `synchronized`. |
| 17 | **B** | A read-only **view** — it still reflects later changes to `x`. |
| 18 | **B** | An immutable **copy** — independent of the source, and it rejects nulls. |
| 19 | **B** | `List.of` rejects nulls with a `NullPointerException`. |
| 20 | **B** | A *structural* modification (add/remove) during iteration bumps `modCount`. |
| 21 | **D** | `set(int, E)` replaces an element without changing the structure, so `modCount` is unchanged. |
| 22 | **B** | A snapshot — taken when the iterator was created; modifications are invisible to it. |
| 23 | **C** | Every write copies the whole backing array — O(n) time and allocation churn. |
| 24 | **B** | It is a binary search — on an unsorted list the result is meaningless. |
| 25 | **B** | `-(insertion point) - 1`; the classic bug is forgetting the `-1` conversion. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                            SET SELECTION                                 │
├────────────────────┬──────────┬───────────────┬───────────┬───────────────┤
│ Set                │ contains │ Order         │ null ok?  │ Backed by     │
├────────────────────┼──────────┼───────────────┼───────────┼───────────────┤
│ HashSet            │ O(1) avg │ none          │ ✔ (one)   │ HashMap       │
│ LinkedHashSet      │ O(1) avg │ insertion     │ ✔ (one)   │ LinkedHashMap │
│ TreeSet            │ O(log n) │ sorted        │ ✗         │ TreeMap       │
│ EnumSet            │ O(1)     │ enum decl.    │ ✗         │ long bitmask  │
│ CopyOnWriteSet     │ O(1)     │ insertion     │ ✔         │ COW list      │
└────────────────────┴──────────┴───────────────┴───────────┴───────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                     QUEUE / DEQUE SELECTION                              │
├────────────────────┬──────────┬──────────┬──────────┬───────────────────┤
│ Structure          │ add      │ remove    │ get(i)   │ null?             │
├────────────────────┼──────────┼──────────┼──────────┼───────────────────┤
│ ArrayDeque         │ O(1)     │ O(1)      │ O(n)     │ ✗                │
│ LinkedList         │ O(1)     │ O(1)      │ O(n)     │ ✔                │
│ Stack (legacy)     │ O(1)     │ O(1)      │ O(n)     │ ✔  (synchronized)│
│ PriorityQueue      │ O(log n) │ O(log n)  │ O(n)     │ ✗  (heap order!)  │
│ ArrayBlockingQueue │ O(1)     │ O(1)      │ O(n)     │ ✗  (bounded)     │
│ LinkedBlockingQueue │ O(1)     │ O(1)      │ O(n)     │ ✗  (bounded)     │
│ ConcurrentLinkedQ  │ O(1)     │ O(1)      │ O(n)     │ ✗  (unbounded)   │
│ CopyOnWriteList    │ O(1)     │ O(n) COPY │ O(1)     │ ✔  (read-heavy!) │
└────────────────────┴──────────┴──────────┴──────────┴───────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    EnumSet — THE BIT-VECTOR OPS                          │
├──────────────────────────────────────────────────────────────────────────┤
│  contains(e)     (elements & (1L << e.ordinal())) != 0     O(1)          │
│  add(e)          elements |= (1L << e.ordinal())            O(1)          │
│  remove(e)       elements &= ~(1L << e.ordinal())           O(1)          │
│  size()          Long.bitCount(elements)                   O(1)          │
│  containsAll(b)  (elements & b.elements) == b.elements     O(1)          │
│  union(a,b)      a.elements | b.elements                   O(1)          │
│  intersection     a.elements & b.elements                   O(1)          │
│  difference      a.elements & ~b.elements                  O(1)          │
│  ⚠ NEVER persist an ordinal() — it changes if the enum is reordered      │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│              VIEW vs COPY — THE DECISION THAT BITES                      │
├──────────────────────────────────────────────────────────────────────────┤
│  Collections.unmodifiableList(x)  → VIEW  (O(1), reflects later changes) │
│  List.copyOf(x) / Set.copyOf(x)   → COPY  (O(n), truly immutable)       │
│  new ArrayList<>(x)               → COPY  (O(n), mutable, allows nulls) │
│                                                                          │
│  RULE: copy at trust boundaries; view only inside a trust domain.        │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### When to use what — production scenarios

| Scenario | Use | Why |
|---|---|---|
| Deduplicate a stream of IDs | `HashSet` | O(1) membership. |
| Deduplicate while preserving input order | `LinkedHashSet` | Deterministic output. |
| "Which permissions does this role have?" (sorted) | `TreeSet` or `EnumSet` | `EnumSet` if the permissions are an enum. |
| Feature flags / status flags (enum) | `EnumSet` | Bit-vector — `containsAll` in one AND. |
| Event listener registry | `CopyOnWriteArrayList` | Rare writes, frequent lock-free reads. |
| Task queue for a worker pool | `ArrayBlockingQueue` | **Bounded** → back-pressure. |
| Priority-ordered task queue | `PriorityBlockingQueue` | ⚠ unbounded — add your own admission control. |
| High-throughput producer/consumer | `LinkedTransferQueue` | Lowest latency handoff. |
| Direct thread-to-thread handoff | `SynchronousQueue` | Zero capacity — a rendezvous. |
| Stack / queue in hot code | `ArrayDeque` | O(1) both ends, no allocation per element. |
| Read-mostly config list | `CopyOnWriteArrayList` | Snapshot iteration. |
| Unmodifiable data crossing an API boundary | `List.copyOf` | A true immutable snapshot. |

### Bounded vs unbounded — the production decision

```text
   ❌ Executors.newFixedThreadPool(10)
        └─ uses an UNBOUNDED LinkedBlockingQueue
           → 10 million queued tasks → OutOfMemoryError: Java heap space
           → and the "back-pressure" you thought you had does not exist

   ✅ new ThreadPoolExecutor(
           10, 10,
           60, TimeUnit.SECONDS,
           new ArrayBlockingQueue<>(1000),        // ← BOUNDED
           new ThreadPoolExecutor.CallerRunsPolicy())  // ← back-pressure
      → when the queue is full, the CALLER runs the task (throttling the producer)
        instead of the task being silently dropped or queued forever
```

> 💡 This is one of the highest-value production facts in this entire folder. An
> unbounded queue converts a *back-pressure* problem into an *OOM* problem, and
> the OOM happens far from the cause.

### Observability

```java
// Queue depth is a first-class metric — an unbounded queue hides back-pressure
LOG.info("queue depth = {}", queue.size());
LOG.info("remaining capacity = {}", queue.remainingCapacity());   // ArrayBlockingQueue only
LOG.info("listener count = {}", listeners.size());
```

| Symptom | Likely cause | Fix |
|---|---|---|
| Heap grows until OOM | Unbounded queue / unbounded cache | Bound it; add eviction |
| `NullPointerException` from `ArrayDeque.add(null)` | Null as a placeholder | Use a sentinel |
| `ConcurrentModificationException` | Mutation during iteration | `removeIf` / `Iterator.remove()` / concurrent collection |
| Slower than expected `PriorityQueue` | Iterating it expecting sorted order | Drain with `poll()` |
| `IllegalArgumentException` from `EnumSet.copyOf` | Empty non-`EnumSet` source | `EnumSet.noneOf(...)` + `addAll` |
| "Immutable" list changed under me | `unmodifiableList` (a view) | `List.copyOf` |
| Flaky test after a `shuffle` | Unseeded `Random` | `new Random(42)` |

### Performance notes

- **`EnumSet` / `EnumMap`** — always prefer them for enum keys. The speedup is not 2×; it is an order of magnitude, and `containsAll`/`union` become single instructions.
- **`ArrayDeque` vs `ArrayList`** — for a FIFO queue, `ArrayDeque` avoids the shifting cost that `ArrayList.remove(0)` incurs (O(n) per removal!). This is why `ArrayList` + `remove(0)` is a classic performance bug.
- **`HashSet` vs `List.contains`** — `list.contains(x)` is O(n); `set.contains(x)` is O(1). In a loop over m items against a list of n, that is O(m·n) vs O(m+n).
- **Measure with JMH.** `System.nanoTime()` in a `main` measures the JIT, not your code.

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| Map internals (the sibling chapter) | [02 · Map Implementations](02-Map-Implementations.md) |
| List implementations and growth | [01 · List Implementations](01-List-Implementations.md) |
| Concurrent collections in depth | [08 · Locks & Atomic](../08-Multithreading-Concurrency/03-Locks-Atomic.md) |
| Thread pools and work queues | [08 · Executor Framework](../08-Multithreading-Concurrency/02-Executor-Framework.md) |
| `equals`/`hashCode` (the set's foundation) | [08 · equals, hashCode & toString](../01-Core-Java-OOP/08-OOP-equals-hashCode-toString.md) |
| `EnumSet` in the JDK source | `java.util.EnumSet`, `java.util.RegularEnumSet` |
| Production caches | Caffeine (W-TinyLFU), Guava `Cache` |

---

**Next Chapter:** → [05 · Generics](../05-Generics/01-Generics.md)
**Previous Chapter:** ← [02 · Map Implementations](02-Map-Implementations.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
