# Collections Framework — Map Implementations

> **The single most-asked Java interview topic.** If you get one question right, make it this one.

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

- **`Map` is NOT a `Collection`.** It is a separate top-level interface. `Map` has `put()`, not `add()`; `map.values()` gives you a `Collection`, but `map` itself is not one.
- **`HashMap` is backed by an array of buckets.** `put()` computes `hash = key.hashCode() ^ (key.hashCode() >>> 16)`, derives `index = (n-1) & hash`, and walks the bucket's linked list.
- **The `>>> 16` spread exists because only the *low* bits select the bucket.** Without it, a key whose `hashCode` differs only in the high bits would collide every time. The XOR copies the high bits down into the low half.
- **`n` must be a power of two** for `(n-1) & hash` to work — it turns a modulo into a bitwise AND, and guarantees *uniform* bucket selection instead of clustering.
- **A bucket becomes a red-black tree at 8 entries** (`TREEIFY_THRESHOLD`), and is *untreeified* back to a list at 6 (`UNTREEIFY_THRESHOLD`). The tree is only built if the table is at least 64 long (`MIN_TREEIFY_CAPACITY`), otherwise it is resized instead.
- **Default capacity 16, load factor 0.75** → the map resizes when `size > 12`. Resize **doubles** the capacity and **preserves relative order** by splitting each bucket into a "lo" and a "hi" list.
- **`HashMap` allows one `null` key and `null` values.** `ConcurrentHashMap` allows **neither** — because `get(k) == null` would be ambiguous between "absent" and "present with null value".
- **`ConcurrentHashMap` (Java 8+) uses CAS for empty bins and `synchronized` on the *bin head* for occupied bins.** Reads are lock-free. It is **not** a single global lock.
- **`LinkedHashMap`** adds insertion/access ordering via a doubly-linked list → the basis of a simple LRU cache via `removeEldestEntry`.
- **`TreeMap`** is a red-black tree: O(log n) worst case, guaranteed ordering, and it **rejects null keys** (a null key cannot be compared).
- **The `equals`/`hashCode` contract is the #1 source of `HashMap` bugs.** Mutate a key after inserting it and the entry becomes *unreachable* — a silent memory leak.

---

## 1. What & Why — The Problem This Solves

**The problem.** Arrays are fast but rigid: to look up "is there a user with id 42?", you must scan every element — O(n). Every application is a lookup problem wearing a costume.

**The solution.** A **hash map** converts the *key* into an array index with a hash function, so the lookup cost becomes independent of how many elements you stored.

```
                    WITHOUT A MAP                     WITH A MAP
                 ┌───────────────────┐            ┌───────────────────┐
   lookup(id=42) │ for each element: │            │ hash(id) → index  │
                 │   equals(id)?     │            │ table[index]      │
                 │   → scan 1..n     │            │ → one bucket      │
                 └───────────────────┘            └───────────────────┘
                    cost = O(n) ❌                     cost = O(1) ✅
```

**The trade-off you are making.** Hashing buys O(1) average lookup by giving up:
1. **Ordering.** A hash map has *no* inherent order — iteration order is an implementation detail, not a contract. (This is why `TreeMap` and `LinkedHashMap` exist.)
2. **Worst-case guarantee.** A pathological `hashCode` degrades the map to O(n) — see the HashDoS attack in [§12](#12-real-world-production-notes).
3. **Key mutability.** The map caches the hash you gave it at `put()` time. Change the key's hash later and the map can never find it again.

> 💡 **The interview framing.** When asked "how does a HashMap work", the *right* answer is not "it uses buckets". The right answer is: *it uses a hash function to turn a key into an array index, keeps the collision rate low by spreading the hash bits, and degrades gracefully by turning a hot bucket into a tree so the worst case is O(log n) rather than O(n).* That last clause is what a senior interviewer is listening for.

---

## 2. Mental Model — The One Picture

```text
        ┌──────────────────────────────────────────────────────────────┐
        │                        HashMap<K,V>                         │
        ├──────────────────────────────────────────────────────────────┤
        │  Node<K,V>[] table;   // the bucket array (power of 2)     │
        │  int size;            // number of live entries             │
        │  int threshold;       // capacity × loadFactor → resize pt  │
        │  float loadFactor;    // 0.75 default                       │
        └──────────────────────────────────────────────────────────────┘

   table.length = 16
   ┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐
   │ 0  │ 1  │ 2  │ 3  │ 4  │ 5  │ 6  │ 7  │ 8  │ 9  │10 │11 │12 │13 │14 │15 │
   └────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘
     │         │         │
     │         │         └──────────────▶ [Node]──▶[Node]──▶ null   (collision chain)
     │         └────────────────────────▶ [Node]──▶ null
     └──────────────────────────────────▶ [Node]──▶ null

   Each Node:
   ┌─────────────────────────────────────────┐
   │ int hash;  K key;  V value;  Node next; │
   └─────────────────────────────────────────┘
```

**What to look at:** the map is an **array of linked lists** (before Java 8) or an **array of trees** (Java 8+). Every key lands in exactly one bucket; the bucket is found in O(1) by a bitwise AND. The *only* variable part is the length of the chain inside a bucket — and that is what the tree conversion and the hash-spreading exist to control.

### The three-step journey of any key

```text
   KEY "cat"
      │
      │  ① hashCode()
      ▼
   ┌──────────────┐   "cat".hashCode()  =  1183119409  (0x4693B6A9)
   │  hashCode()  │
   └──────┬───────┘
          │
          │  ② spread:  h ^ (h >>> 16)
          ▼
   ┌──────────────┐   0x4693B6A9 ^ 0x00004693  =  0x4693F03A
   │   SPREAD     │   (high bits pulled down into the low half)
   └──────┬───────┘
          │
          │  ③ index:  (n - 1) & hash
          ▼
   ┌──────────────┐   n = 16 → (16-1) & 0x4693F03A = 0x0F & 0x4693F03A = 0x0A = 10
   │   INDEX 10   │
   └──────────────┘
          │
          ▼
   table[10] → walk the chain comparing (hash, then equals)
```

**What to look at:** step ② is the *only* non-obvious line, and it is the one that gets asked. Everything else is arithmetic.

---

## 3. Deep Dive — Theory

### 3.1 The Map hierarchy — and why `Map` is not a `Collection`

```text
java.util.Map<K,V>                       ← NOT a Collection
     │
     ├── java.util.HashMap
     ├── java.util.LinkedHashMap         (extends HashMap)
     ├── java.util.Hashtable             (legacy, synchronized)
     ├── java.util.Properties            (extends Hashtable)
     ├── java.util.WeakHashMap
     ├── java.util.IdentityHashMap
     ├── java.util.EnumMap
     │
     └── java.util.concurrent.ConcurrentMap
              ├── java.util.concurrent.ConcurrentHashMap
              └── java.util.concurrent.ConcurrentSkipListMap
```

```text
java.util.Collection
     ├── java.util.List         → ArrayList, LinkedList, Vector, CopyOnWriteArrayList
     ├── java.util.Set          → HashSet, LinkedHashSet, TreeSet, EnumSet
     └── java.util.Queue        → PriorityQueue, ArrayDeque, ConcurrentLinkedQueue
```

| | `Collection` | `Map` |
|---|---|---|
| Core method | `add(E)` / `remove(Object)` | `put(K,V)` / `remove(Object)` |
| Keyed by | a single element | a **key→value pair** |
| Duplicates | allowed (List), not (Set) | keys are unique; values may repeat |
| Size | `size()` = element count | `size()` = entry count |
| `contains` | `contains(Object)` | `containsKey(Object)` / `containsValue(Object)` |
| Stream of | elements | `entrySet()` → `Map.Entry` streams |

> **The single most common beginner mistake** is treating `Map` as a `Collection` and writing `map.add(k, v)` or `for (Map.Entry e : map)`. The correct form is `map.put(k, v)` and `for (Map.Entry<K,V> e : map.entrySet())`.

### 3.2 `HashMap` internal structure

```java
public class HashMap<K,V> extends AbstractMap<K,V> implements Map<K,V>, Cloneable, Serializable {

    static final class Node<K,V> implements Map.Entry<K,V> {
        final int hash;        // cached, spread hash
        final K key;
        V value;               // mutable
        Node<K,V> next;        // bucket chain
    }

    transient Node<K,V>[] table;   // the bucket array — LAZY allocated!
    transient int size;
    transient int threshold;
    final float loadFactor;

    static final int DEFAULT_CAPACITY = 16;
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    // treeify thresholds
    static final int TREEIFY_THRESHOLD = 8;
    static final int UNTREEIFY_THRESHOLD = 6;
    static final int MIN_TREEIFY_CAPACITY = 64;
}
```

**Four facts an interviewer will probe:**

1. **The table is lazily allocated.** An empty `new HashMap<>()` has `table == null`, not an array of 16 nulls. The first `put()` allocates it. (This is why `HashMap` is cheap to create and why `new HashMap<>()` in a loop is not as expensive as it looks.)
2. **`threshold` is `capacity × loadFactor`.** With capacity 16 and load factor 0.75, `threshold = 12`. `size` reaching 12 triggers a resize to 32, at which point `threshold` becomes 24.
3. **`Node.hash` caches the spread hash.** It is computed once at `put()` time and reused on every later lookup — that is why the cached value must be *consistent*, and why mutating a key breaks the map.
4. **The tree thresholds are three different numbers.** `TREEIFY_THRESHOLD = 8` (when a bucket reaches 8 nodes, *consider* treeifying), `MIN_TREEIFY_CAPACITY = 64` (but only if the table is at least 64 long — otherwise *resize* instead, because a bigger table disperses the collisions anyway), and `UNTREEIFY_THRESHOLD = 6` (shrink back to a list below 6, to save the memory overhead of tree nodes).

### 3.3 The hash function — why `h ^ (h >>> 16)`

```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0
         : (h = key.hashCode()) ^ (h >>> 16);
}
```

**The problem it solves.** The bucket index uses only the **low** bits: `index = (n-1) & hash`. With a 16-bucket table, only bits 0–3 of the hash matter. A class whose `hashCode()` returns values that differ only in the *high* bits (e.g. `Object.hashCode()`-style identity hashes, or a badly written `hashCode` that multiplies by a large constant) would place *every* key in the same 16 buckets.

**The fix.** `h >>> 16` shifts the high 16 bits down into the low 16. The XOR then mixes them into the low half, so the bits that decide the bucket carry information from the whole hash.

```text
   Suppose two keys have hashCodes that differ ONLY in bit 20:

   key1:  0x00000000_00100000   (bit 20 set)
   key2:  0x00000000_00200000   (bit 21 set)

   WITHOUT the spread, with n = 16 (only bits 0-3 used):
   key1 & 0x0F = 0   → bucket 0
   key2 & 0x0F = 0   → bucket 0     ← COLLISION, forever

   WITH the spread:
   key1: 0x00100000 >>> 16 = 0x00000010   →  0x00100000 ^ 0x00000010 = 0x00100010
   key2: 0x00200000 >>> 16 = 0x00000020   →  0x00200000 ^ 0x00000020 = 0x00200020

   key1 & 0x0F = 0    bucket 0
   key2 & 0x0F = 0    bucket 0   ← still collide, but the HIGH bits now differ,
                                      so a 64-bucket table (n=64) separates them:
   key1 & 0x3F = 0x20
   key2 & 0x3F = 0x20   hmm
```

**The precise reason.** The spread does not guarantee zero collisions — nothing can. It guarantees that the *low* bits of the hash are a function of *all* the bits, so that as the table grows (using more low bits), the distribution keeps improving. Without it, growing the table would not help at all for hash functions whose entropy is concentrated in high bits.

> 💡 **Interview one-liner:** *"The XOR with `>>> 16` exists because the bucket index only uses the low bits of the hash. It pulls the high bits down so that the bucket choice depends on the entire hash value, which keeps the distribution uniform as the table grows."*

### 3.4 Why `(n - 1) & hash` — and why the capacity must be a power of two

```java
int index = (n - 1) & hash;   // n = table.length
```

Three reasons this exact expression is used:

1. **It replaces `%` (modulo) with a bitwise AND.** Modulo is a division instruction; AND is a single cycle. For a hot data structure this is a measurable difference.
2. **It requires `n` to be a power of two**, because `n - 1` then is a mask of all 1s in the low `log2(n)` bits. `16 - 1 = 0b1111` — a perfect mask.
3. **It guarantees uniform bucket selection.** If `n` were, say, 10, then `n - 1 = 9 = 0b1001` and only bits 0 and 3 would be used — buckets 0, 8, 9 and the rest would be skewed. The *same* holds for `%`: with `n = 10`, buckets 0–9 are reachable, but the modulo of powers of two has exactly the pathological clustering shown above.

**Consequence — the capacity never becomes 3 or 5.** It is always 16, 32, 64, 128… which is why you will never see `new HashMap<>(15)` behave as "exactly 15 buckets" (it is rounded up to 16 by `tableSizeFor`).

```java
static final int tableSizeFor(int cap) {
    int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
    return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
}
```

### 3.5 `put()` — the full algorithm

```java
public V put(K key, V value) {
    int hash = hash(key);                 // ① spread hash
    if ((tab = table) == null || (n = tab.length) == 0)
        tab = resize();                   // ② lazy init on first put
    int index = (n - 1) & hash;           // ③ bucket index
    Node<K,V> first = tab[index];         // ④ head of the chain

    if (first == null) {                  // ⑤ empty bucket → place the node
        tab[index] = newNode(hash, key, value, null);
        ++size;
        afterNodeInsertion(true);         //    (TreeMap hook, no-op for HashMap)
    } else {
        Node<K,V> e; K k;
        do {
            if (first.hash == hash &&     // ⑥ same hash?
                ((k = first.key) == key || (key != null && key.equals(k))))
                break;                    //    → same key: overwrite value
            e = first; first = first.next;
        } while (first != null);

        if (e == null) {                  // ⑦ not found → append to chain
            e.next = new Node<>(hash, key, value, first);
            tab[index] = e;
            ++size;
            afterNodeInsertion(true);
            if (++binCount >= TREEIFY_THRESHOLD)   // ⑧ hot bucket → treeify?
                treeifyBin(tab, hash);
        } else {
            e.value = value;              // ⑨ update existing
            afterNodeAccess(e);
        }
    }
    if (++modCount > threshold) resize(); // ⑩ resize if over threshold
    return oldValue;
}
```

**Key insight about step ⑥:** the equality check is **two-stage** — first compare the cached `hash` (an `int` compare, very cheap), and only if it matches compare the keys with `==` (reference) then `equals()`. This is the fast path that makes lookups fast.

**Key insight about step ⑩:** the resize check is `++modCount > threshold`, where `modCount` counts *structural* modifications (insertions and removals), not just size. Every `put` of a *new* key increments it; overwriting an existing value does not.

### 3.6 `get()` — and why it is lock-free-friendly

```java
public V get(Object key) {
    Node<K,V>[] tab; Node<K,V> first, e; int n, hash; K k;
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (first = tab[(n - 1) & (hash = hash(key))]) != null) {
        do {
            if (first.hash == hash && ((k = first.key) == key || key.equals(k)))
                return first.value;
            e = first; first = first.next;
        } while (first != null);
    }
    return null;
}
```

`get()` performs **no locking and no writes** — it only reads the `table` reference, the `Node.hash`/`Node.key`/`Node.value` fields and the `next` pointers. Every one of those is either `final` or safely published. This is exactly why `ConcurrentHashMap` can serve reads lock-free.

> ⚠️ **Subtlety.** `Node.value` is *not* final — it must be mutable so `put()` can overwrite a value. `ConcurrentHashMap` solves this by making `value` `volatile`, so an overwrite is immediately visible to readers.

### 3.7 `resize()` — the split-preserving-order trick

This is the most elegant part of `HashMap`, and a genuine senior-interview differentiator.

```java
final Node<K,V>[] resize() {
    Node<K,V>[] oldTab = table;
    int oldCap = (oldTab == null) ? 0 : oldTab.length;
    int oldThr = threshold;
    int newCap, newThr = 0;
    if (oldCap > 0) {
        if (oldCap >= MAXIMUM_CAPACITY) { threshold = Integer.MAX_VALUE; return oldTab; }
        else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY && oldCap >= DEFAULT_INITIAL_CAPACITY)
            newThr = oldThr << 1;   // threshold doubles too
    }
    else if (oldThr > 0) newCap = oldThr;             // user-specified capacity
    else { newCap = DEFAULT_INITIAL_CAPACITY; newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY); }

    Node<K,V>[] newTab = new Node[newCap];
    table = newTab;

    if (oldTab != null) {
        for (int j = 0; j < oldCap; ++j) {
            Node<K,V> e;
            if ((e = oldTab[j]) != null) {
                oldTab[j] = null;                       // help GC
                if (e.next == null)                    // single node → direct move
                    newTab[e.hash & (newCap - 1)] = e;
                else if (e instanceof TreeNode)         // tree → split the tree
                    ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                else {                                  // ── the clever part ──
                    Node<K,V> loHead = null, loTail = null;
                    Node<K,V> hiHead = null, hiTail = null;
                    do {
                        next = e.next;
                        if ((e.hash & oldCap) == 0) {  // stays in the LOW bucket
                            if (loTail == null) loHead = e;
                            else loTail.next = e;
                            loTail = e;
                        } else {                       // moves to the HIGH bucket
                            if (hiTail == null) hiHead = e;
                            else hiTail.next = e;
                            hiTail = e;
                        }
                    } while ((e = next) != null);
                    if (loTail != null) { loTail.next = null; newTab[j] = loHead; }
                    if (hiTail != null) { hiTail.next = null; newTab[j + oldCap] = hiHead; }
                }
            }
        }
    }
    threshold = newThr;
    return newTab;
}
```

**The insight.** When the capacity doubles from `n` to `2n`, an entry's new index is `(2n-1) & hash`. Because `2n-1` has exactly one more low bit set than `n-1`, each entry either:
- keeps its index `j`, or
- moves to `j + n` (the **high** bucket).

And *which* one it is, is decided by a **single bit**: `hash & oldCap` — the bit that was just added to the mask. So the resize does **not** rehash every key and does **not** need to recompute `hash(key)`. It just tests one bit and splits each chain into two lists.

```text
   BEFORE (capacity 16)                 AFTER (capacity 32)

   bucket 5:  [A(h=0x05)]->[B(h=0x15)]  →   bucket 5:  [A(h=0x05)]
                  |  bit4 of h = 0            bucket 21: [B(h=0x15)]
   bucket 7:  [C(h=0x07)]->[D(h=0x27)]  →   bucket 7:  [C(h=0x07)]
                  |  bit4 of h = 1            bucket 23: [D(h=0x27)]
```

**What to look at:** the *relative order within each list* is preserved (the `loTail`/`hiTail` chain-building appends to the end). That is why `LinkedHashMap` does **not** have to rebuild its linked list on resize — a detail most people miss.

> 💡 **The alternative (and why it isn't used).** The "textbook" approach re-hashes every key: `newIndex = hash % newCapacity`. That is simpler to explain but O(n) *and* it would destroy `LinkedHashMap`'s order. The bit-split is both faster and order-preserving.

### 3.8 Treeification — why a bucket becomes a tree

```java
static final int TREEIFY_THRESHOLD  = 8;   // chain length that triggers treeify
static final int UNTREEIFY_THRESHOLD = 6;   // tree size that triggers untreeify
static final int MIN_TREEIFY_CAPACITY = 64; // table must be this big to treeify
```

```java
if (++binCount >= TREEIFY_THRESHOLD) treeifyBin(tab, hash);

static void treeifyBin(Map.Entry[] tab, int hash) {
    Node<K,V> e = null, p = null; int c = 0, n = tab.length, lo, hi;
    while (e != null) {
        if ((e = e.next) == null)
            if ((p = e) instanceof TreeNode) ((TreeNode<K,V>)p).putTreeVal(this, tab, hash);
        else {
            if (c >= MIN_TREEIFY_CAPACITY) {   // table too small → resize instead
                lo = index; hi = index + n;
                for (int i = lo; i < hi; i++) { /* split the chain */ }
            }
            else if (p != null) {
                TreeNode<K,V> t = new TreeNode<>(p.hash, p.key, p.value, null);
                ((TreeNode<K,V>)p).putTreeVal(this, tab, hash);
                tab[index] = t;
                t.treeify(tab, hash);           // build the red-black tree
            }
            c++;
        }
    }
}
```

**Why treeify at all?** A chain of `n` nodes makes a lookup O(n). A red-black tree makes it O(log n). The crossover is around 8 — below that, a tree's memory overhead and pointer-chasing cost more than they save.

**Why `MIN_TREEIFY_CAPACITY = 64`?** If the table is smaller than 64 buckets and one bucket has 8 entries, the total number of entries is small, so a **resize** is the better fix: doubling the table spreads those 8 entries over 2× the buckets. Treeifying a 16-bucket table would be treating the symptom while the cause is a too-small table.

**Why untreeify at 6?** Tree nodes are much bigger than list nodes (each `TreeNode` holds parent/left/right/prev pointers). Below 6 elements, the memory cost outweighs the lookup benefit.

**What the tree changes:** the chain order is no longer *insertion* order — it becomes *hash* order — and the tree's comparisons use the same `hash`, then `Comparable`, then `System.identityHashCode` tie-breakers as `TreeMap`.

### 3.9 `LinkedHashMap` — insertion order, access order, and LRU

`LinkedHashMap` extends `HashMap` and adds a **doubly-linked list** through all entries.

```java
public class LinkedHashMap<K,V> extends HashMap<K,V> implements Map<K,V> {

    static class LinkedHashEntry<K,V> extends HashMap.Node<K,V> {
        LinkedHashEntry<K,V> before, after;   // the links
    }

    private transient LinkedHashMap.Entry<K,V> head;   // most recently used
    private transient LinkedHashMap.Entry<K,V> tail;   // least recently used
    final boolean accessOrder;                       // insertion order (false) or access order (true)
}
```

```text
   HashMap bucket array (for O(1) lookup)
        │
        │  each node ALSO links into:
        ▼
   ┌────────┐    ┌────────┐    ┌────────┐    ┌────────┐
   │  head  │◄──►│   B    │◄──►│   C    │◄──►│  tail  │
   │ (newest)│   │        │    │        │    │(oldest)│
   └────────┘    └────────┘    └────────┘    └────────┘
        │                                        │
        └──────────────┬─────────────────────────┘
                       │  removeEldestEntry() evicts from the TAIL
                       ▼
              ┌──────────────┐
              │  EVICTED     │
              └──────────────┘
```

**What to look at:** `head` is the **most recently used** and `tail` is the **least recently used**. An LRU cache evicts from the `tail`. With `accessOrder = true`, a `get()` **moves the entry to the head** — that is what makes it "least *recently* used" rather than "least *frequently* used".

**The LRU cache in 12 lines:**

```java
public class LruCache<K,V> extends java.util.LinkedHashMap<K,V> {
    private final int maxSize;

    public LruCache(int maxSize) {
        super(maxSize, 0.75f, true);          // accessOrder = true
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(java.util.Map.Entry<K,V> eldest) {
        return size() > maxSize;             // evict the LRU (tail)
    }
}
```

**The `LinkedHashMap` resize trick.** `HashMap.resize()` rebuilds the bucket array, but the doubly-linked list is a *separate* structure, so `afterNodeInsertion` is what actually maintains it. `LinkedHashMap` overrides `afterNodeInsertion` to link the new node after the `head`. Because `HashMap.resize()` preserves the relative order of the chain nodes, and `LinkedHashMap` rebuilds its list from the entries, **the access/insertion order survives a resize**.

### 3.10 `TreeMap` — the red-black tree

```java
public class TreeMap<K,V> extends AbstractMap<K,V> implements NavigableMap<K,V> {
    private transient Entry<K,V> root;     // the red-black tree root
    transient int size;
    private final Comparator<? super K> comparator;   // null → natural ordering
    transient int modCount;
}
```

| Property | `HashMap` | `TreeMap` |
|---|---|---|
| Structure | array of buckets + chains | red-black tree |
| `get`/`put` | O(1) average, O(log n) if treeified | **O(log n) guaranteed** |
| Ordering | none | sorted by key (natural or `Comparator`) |
| `null` key | ✅ one allowed | ❌ **rejected** (cannot compare `null`) |
| `null` value | ✅ allowed | ✅ allowed |
| `containsKey` | O(1) | O(log n) |
| Range queries | ❌ not supported | ✅ `headMap`, `tailMap`, `subMap`, `firstKey`, `lastKey` |
| Navigation | ❌ | ✅ `NavigableMap` — `floorKey`, `ceilingKey`, `higherKey`, `lowerKey` |

**Why `TreeMap` rejects a `null` key.** The tree compares keys with `compareTo` (or the `Comparator`). `null.compareTo(x)` throws `NullPointerException`. You can *force* nulls in with a custom comparator that handles them, but the default rejects them. This is a genuine behavioural difference candidates get wrong.

**The red-black invariant** (why the height is bounded):

```text
   Every node carries a colour (RED or BLACK). The tree satisfies:

   1. Every node is either RED or BLACK.
   2. The root is BLACK.
   3. A RED node has BLACK children (no two consecutive REDs).
   4. Every path from a node to a descendant leaf contains the same number
      of BLACK nodes.

   ⇒ The longest path is at most 2× the shortest
   ⇒ Height is O(log n)  ⇒  lookup is O(log n) GUARANTEED
```

### 3.11 `Hashtable` and `Properties` — the legacy branch

```java
public class Hashtable<K,V> extends Dictionary<K,V> {
    public synchronized V get(Object key) { ... }   // EVERY method is synchronized
    public synchronized V put(K key, V value) { ... }
}
```

- **Every** method is `synchronized` on the instance → coarse-grained, a single global lock. Under contention this is a bottleneck.
- `Hashtable` **rejects `null` keys and `null` values** (a `NullPointerException`, not a silent accept).
- It is a legacy class kept for binary compatibility; `HashMap` + external synchronization replaced it in Java 2.
- `Properties` extends `Hashtable` and adds `load()`/`store()` for `.properties` files, plus a `defaults` fallback map.

> 💡 **Why does `Hashtable` reject nulls but `HashMap` allows them?** The historical reason is that `Hashtable` predates `null`-tolerant collections and was designed for a "no nulls in a hashtable" mental model. `ConcurrentHashMap` rejects nulls for a *different* and much better reason (see §3.12) — do not conflate the two.

### 3.12 `ConcurrentHashMap` — the important one

```java
public class ConcurrentHashMap<K,V> extends AbstractMap<K,V> implements ConcurrentMap<K,V>, Serializable {
    private static final int DEFAULT_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private static final int TREEIFY_THRESHOLD = 8;
    private static final int MIN_TREEIFY_CAPACITY = 64;

    transient volatile Node<K,V>[] table;
    private transient volatile int sizeCtl;      // resize control / size hint
    private transient volatile int baseCount;     // base size counter
    private transient volatile CounterCell[] counterCells;  // striped counters
}
```

**Java 7 vs Java 8 — the fundamental change.**

| | Java 7 | Java 8+ |
|---|---|---|
| Structure | array of `Segment<K,V>` (each a mini `HashMap` with its own lock) | a single array of `Node` bins — **no segments** |
| Concurrency control | `ReentrantLock` **per segment** (default 16 segments) | `CAS` for empty bins, `synchronized` **on the bin head node** for occupied bins |
| Read | lock-free | lock-free |
| Write | lock the segment | lock only the one bin |
| Contention scaling | limited by segment count | scales to any number of cores |

```text
   ❌ JAVA 7 — segment-level locking
   ┌──────────┬──────────┬──────────┬──────────┐
   │ Segment0 │ Segment1 │ Segment2 │ Segment3 │   each has its OWN lock
   │ 🔒 lock  │ 🔓 free  │ 🔒 lock  │ 🔓 free  │
   │  bins…   │  bins…   │  bins…   │  bins…   │   → 16 segments = max 16 threads
   └──────────┴──────────┴──────────┴──────────┘      writing in parallel (default)

   ✅ JAVA 8+ — bin-level locking
   ┌──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐
   │ bin0 │ bin1 │ bin2 │ bin3 │ bin4 │ bin5 │ bin6 │ bin7 │   ONE shared table
   │ 🔒   │ 🔓   │ 🔒   │ 🔓   │ 🔒   │ 🔓   │ 🔒   │ 🔓   │   → any number of
   └──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┘   threads can write
   ↑ only the head node of the bin is locked, not the whole table
```

**What to look at:** in Java 8+ the lock granularity is a **single bin**, and it is acquired by `synchronized (first)` where `first` is the head `Node` of that bin. Two threads writing to *different* bins never contend. Two threads writing to the *same* bin do — but the lock is re-entrant on the head node, so a thread that already holds it (during a chain update) does not deadlock.

**Why no `null` keys or values?**

```java
public V putVal(K key, V value, boolean onlyIfAbsent) {
    if (key == null || value == null) throw new NullPointerException();
    ...
}
```

Because `get(key)` returns `null` for a missing key, a `null` *value* would make "absent" and "present-with-null" indistinguishable. `ConcurrentHashMap` also uses `null` as the internal "no node" marker in the `ForwardingNode`/tree machinery. `computeIfAbsent` and `merge` would have the same problem: they cannot tell "the mapping function returned null (remove the entry)" from "there was no entry". `HashMap` can allow nulls precisely because it does not need that distinction.

**The `sizeCtl` field.** A single overloaded field with three jobs:
- **Before the table is allocated**: it holds the *initial capacity* requested.
- **During a resize**: it holds a *negative* value encoding the resize's size transfer state — `sizeCtl = -(n << 1)` style values let a *helping* thread know a resize is in progress and participate in it.
- **After the table is allocated**: it holds the **resize threshold** (`capacity × loadFactor`).

**`size()` is not a simple field read.** `size` is stored in `baseCount` plus a `CounterCell[]` array of striped counters. `size()` sums them, and if the sum is inconsistent (a resize happened mid-count) it retries. This is a **weakly consistent** snapshot — the value may be slightly stale, but the call is safe and does not lock the whole map.

**Iterators are weakly consistent.** They reflect the state of the map at *some* point at or since the creation of the iterator. They never throw `ConcurrentModificationException` and never see a partially-updated node. This is a deliberate trade-off: a truly consistent snapshot would require locking the entire map, defeating the purpose.

**`computeIfAbsent` is atomic per key.** This is the operation that fixes the classic check-then-act race:

```java
// ❌ RACE: two threads can both see "absent" and both compute
if (map.get(key) == null) {
    map.put(key, expensiveCompute(key));    // may run twice
}

// ✅ ATOMIC: the mapping function runs at most once per key
map.computeIfAbsent(key, k -> expensiveCompute(k));
```

> 💡 **`ConcurrentHashMap` vs `Collections.synchronizedMap`.** The latter wraps a `HashMap` and synchronizes **every** method on one monitor — a global lock. `ConcurrentHashMap` gives lock-free reads and per-bin write locks. For a read-heavy concurrent map, `ConcurrentHashMap` wins decisively.

### 3.13 The other maps — a quick, honest guide

| Map | Backed by | Order | Null key | Null value | Use it for |
|---|---|---|---|---|---|
| `HashMap` | array + hash | none | 1 allowed | ✅ | the default, single-threaded |
| `LinkedHashMap` | `HashMap` + DLL | insertion / access | 1 allowed | ✅ | ordered iteration, LRU cache |
| `TreeMap` | red-black tree | sorted | ❌ | ✅ | range queries, navigation |
| `Hashtable` | array + hash | none | ❌ | ❌ | legacy code only |
| `Properties` | `Hashtable` | none | ❌ | ❌ | config files |
| `WeakHashMap` | array + hash | none | 1 allowed | ✅ | cache keyed by objects that may be GC'd |
| `IdentityHashMap` | array + **System.identityHashCode** | none | ✅ | ✅ | when reference identity is the key |
| `EnumMap` | **array indexed by `ordinal()`** | enum order | ❌ | ✅ | enum-keyed maps — fastest possible |
| `ConcurrentHashMap` | array + hash | none | ❌ | ❌ | concurrent access |
| `ConcurrentSkipListMap` | skip list | sorted | ❌ | ✅ | concurrent + sorted (a concurrent `TreeMap`) |

**`WeakHashMap` — the interesting one.** Its keys are held via `WeakReference`, so the GC can collect a key when nothing else references it. The entry is then *silently removed* on the next access. This is exactly what you want for a "cache keyed by a large object" — but it also means the map can lose entries **without warning**, so you must not use it when the key's lifetime matters.

> ⚠️ **The `WeakHashMap` gotcha.** If the *value* strongly references the *key* (a `WeakHashMap<Key, Value>` where `Value` holds `Key`), the entry is never collected — the value keeps the key alive, and the weak reference never fires. The classic bug: `WeakHashMap<Key, List<Key>>`.

**`IdentityHashMap`** uses `System.identityHashCode()` and `==` instead of `hashCode()`/`equals()`. It is used by the JDK's `System.identityHashCode` internals and by frameworks that need to distinguish two equal-but-distinct objects.

**`EnumMap`** is remarkable: it is backed by a plain `Object[]` indexed by `key.ordinal()`, so lookup is an array index — no hashing at all. It is the fastest map implementation the JDK offers. But it depends on `ordinal()`, so it breaks if the enum's constants are reordered (which is why you must never persist an `ordinal()`).

### 3.14 `Map.Entry` — the view you actually iterate

```java
public interface Map<K, V> {

    /**
     * A key-value pair. Note that Entry is NESTED inside Map — which is why
     * the import is `java.util.Map.Entry`, not `java.util.Entry`.
     */
    interface Entry<K, V> {
        K getKey();
        V getValue();
        V setValue(V newValue);      // the ONLY mutating method on a Map view!
    }
}
```

> 💡 `Map.Entry` is a **nested** interface, so you import it as `java.util.Map.Entry`
> (or use the qualified name `Map.Entry<K,V>`). `for (Map.Entry<K,V> e : map.entrySet())`
> is the canonical form.

```java
// ✅ CORRECT — iterate entries
for (Map.Entry<String,Integer> e : map.entrySet()) {
    System.out.println(e.getKey() + " = " + e.getValue());
}

// ✅ CORRECT — mutate the VALUE through the entry
for (Map.Entry<String,Integer> e : map.entrySet()) {
    e.setValue(e.getValue() + 1);
}

// ❌ WRONG — mutating the MAP while iterating it
for (String k : map.keySet()) {
    map.remove(k);                       // → ConcurrentModificationException
}

// ✅ CORRECT — mutate the map while iterating its KEY set
for (Iterator<String> it = map.keySet().iterator(); it.hasNext(); ) {
    String k = it.next();
    if (k.startsWith("temp-")) it.remove();   // iterator.remove() is safe
}
```

**`map.entrySet()`, `map.keySet()` and `map.values()` are *views*, not copies.** They are backed by the map — a change to the map is visible through them, and `entry.setValue(v)` writes through to the map. (This is the opposite of `Collections.unmodifiableMap`, which returns an unmodifiable *view*, and `Map.copyOf`, which returns an immutable *copy* — see §7.)

**`Map.Entry` in a `HashMap` does not support `setValue` during iteration in a concurrent context** — in `ConcurrentHashMap`, `setValue` on an entry obtained from `entrySet()` is supported but the `Map.Entry` returned by `ConcurrentHashMap` is a distinct class.

### 3.15 `Map.of` and `Map.ofEntries` — Java 9+

```java
Map<String, Integer> m1 = Map.of("a", 1, "b", 2);          // up to 10 pairs
Map<String, Integer> m2 = Map.ofEntries(                     // unlimited
        Map.entry("a", 1),
        Map.entry("b", 2));

// Properties:
// - IMMUTABLE — no put/remove/putIfAbsent
// - REJECTS null keys AND null values  → NullPointerException
// - REJECTS duplicate keys            → IllegalArgumentException
// - Iteration order is RANDOM and unspecified
```

Compare with `Collections.unmodifiableMap(new HashMap<>(...))`:
- `Map.of` is a *true immutable snapshot* — it does not even hold a reference to the backing map.
- `Collections.unmodifiableMap` is an unmodifiable **view** — the backing map can still change underneath, and the view reflects those changes.
- `Map.of` rejects nulls; `unmodifiableMap` does not care what is in the map.

---

## 4. Visualizations

### 4.1 A bucket array with real hashes and real indices

```text
   Let the keys be:  "A" (65), "B" (66), "C" (67), "hello" (99162322)
   capacity n = 16  →  index = (16-1) & spread(hash)

   spread("A")   = 65  ^ (65  >>> 16)  = 65  ^ 0  = 65    → 65  & 0x0F = 1
   spread("B")   = 66  ^ 0              = 66                → 66  & 0x0F = 2
   spread("C")   = 67  ^ 0              = 67                → 67  & 0x0F = 3
   spread("hello")= 99162322 ^ 1511     = 99163833         → 99163833 & 0x0F = 9

   ┌────┬───────────────┬───────────────┬───────────────┬─────┬─────┬─────┬─────┬─────┬───────────────┬─────┬─────┬─────┬─────┬─────┐
   │  0 │       1       │       2       │       3       │  4  │  5  │  6  │  7  │  8  │       9       │ 10  │ 11  │ 12  │ 13  │ 14  │ 15  │
   └────┴───────────────┴───────────────┴───────────────┴─────┴─────┴─────┴─────┴─────┴───────────────┴─────┴─────┴─────┴─────┴─────┘
        │  "A"→"A"       │  "B"→"B"       │  "C"→"C"                                 │ "hello"→"hello"
        │  hash=65       │  hash=66       │  hash=67                                 │ hash=99163833
        │  next=null     │  next=null     │  next=null                               │ next=null

   A COLLISION — two keys with different hashCodes landing in the same bucket:

   ┌────┬──────────────────────────────────────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┬─────┐
   │  0 │              1                       │  2  │  3  │  4  │  5  │  6  │  7  │  8  │  9  │ 10  │
   └────┴──────────────────────────────────────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┴─────┘
        │ "Aa"  hash=2112                      ←  "Aa" and "BB" have the SAME hashCode (2112)!
        │   │
        │   └─▶ "BB"  hash=2112
        │          │
        │          └─▶ null
        │  (chain of 2 — the `hash` matches, so `equals()` is called)
```

**What to look at:** the `"Aa"` / `"BB"` collision is a *real* Java collision — both have `hashCode() == 2112` because the hash is a weighted sum of the characters (`'A'*31 + 'a'` and `'B'*31 + 'B'` both equal 2112). `HashMap` handles it correctly because the `hash` match is only a *filter*; the final decision is made by `key.equals(existingKey)`, and `"Aa".equals("BB")` is `false`.

### 4.2 The hash-spreading bit operation, step by step

```text
   Take a key whose hashCode() has entropy ONLY in the high bits.
   (e.g. a badly designed hashCode: return id * 65537;)

   h            = 0x12340000        (32-bit hash, only the top 16 bits are interesting)

   STEP 1 —  h >>> 16  (unsigned right shift by 16)
              0x12340000 >>> 16  =  0x00001234
              (the high 16 bits slide down into the low 16 bits)

   STEP 2 —  h ^ (h >>> 16)
              0x12340000
           ^  0x00001234
           ----------------
              0x12341234        ← now the LOW 16 bits carry the high-bit entropy

   STEP 3 —  index = (n-1) & hash        (n = 16)
              0x0F & 0x12341234  =  0x4     → bucket 4

   WITHOUT the spread:
              0x0F & 0x12340000  =  0x0     → bucket 0
              and EVERY key with the same pattern would land in bucket 0 too.
```

**What to look at:** step 2 is a *fold*. It does not create entropy — it **redistributes** it so that the low bits (the ones the bucket index uses) are sensitive to the whole 32-bit value.

### 4.3 Treeify: chain → red-black tree

```text
   BEFORE — a bucket with 8 colliding nodes (all hashCode 0, e.g. a bad hashCode)

   bin[3] → [A]→[B]→[C]→[D]→[E]→[F]→[G]→[H]→null
             └── lookup for "H" = 8 comparisons ── O(n)

   AFTER — TREEIFY_THRESHOLD(8) reached and table.length >= 64

   bin[3] →                 ┌───────┐
                           │   D   │  (root, BLACK)
                           └─┬───┬─┘
                       ┌──────┘   └──────┐
                   ┌───▼───┐         ┌───▼───┐
                   │   B   │         │   F   │
                   └───┬───┘         └───┬───┘
                       │                  │
                   ┌───▼───┐         ┌───▼───┐
                   │   A   │         │   G   │
                   └───────┘         └───┬───┘
                                          │
                                      ┌───▼───┐
                                      │   H   │
                                      └───────┘

                   lookup for "H" = 3 comparisons ── O(log n)
```

**What to look at:** the tree is ordered by **hash** (then `Comparable`, then identity hash) — *not* by insertion order. So after treeification, the iteration order of that bucket changes. That is legal: `HashMap` never promised insertion order.

### 4.4 `resize()` — the split-preserving-order trick

```text
   Capacity 16  →  32.  oldCap = 16, newCap = 32.
   For every entry, the ONLY new bit that matters is bit 4 (value 16).

   bucket 5 before:  [A(h=0x05)] → [B(h=0x15)] → null
        A: 0x05 & 16 = 0   → stays LOW  → new bucket 5
        B: 0x15 & 16 = 16  → goes HIGH  → new bucket 5+16 = 21

   bucket 5 after:   [A(h=0x05)] → null
   bucket 21 after:  [B(h=0x15)] → null

   ┌──────────────────────┐              ┌──────────────────────┐
   │  OLD  (cap 16)       │              │  NEW  (cap 32)       │
   ├──────────────────────┤              ├──────────────────────┤
   │ 5: A ──▶ B           │  ──split──▶  │ 5: A                │
   │ 7: C ──▶ D           │              │ 21: B                │
   │ 9: E                 │              │ 7: C                 │
   └──────────────────────┘              │ 23: D                │
                                         │ 9: E                 │
                                         └──────────────────────┘

   KEY POINT: the test is a SINGLE BIT  (e.hash & oldCap).
   No key is re-hashed. Order within each new list is preserved.
```

**What to look at:** one bit test per node. The resize is O(n) with a very small constant, and — critically — it does **not** recompute `hash(key)`, so a `hashCode()` that is expensive to compute is only ever called once per `put()`.

### 4.5 `LinkedHashMap` — the LRU doubly-linked list

```text
   accessOrder = true  (LRU mode)

   put("a"), put("b"), put("c"):

     head ──▶ [a] ◄──▶ [b] ◄──▶ [c] ◄──▶ tail
               ▲                    │
               └────────────────────┘
             (newest)             (oldest → evicted first)

   get("a")  →  a is moved to the head:

     head ──▶ [a] ◄──▶ [c] ◄──▶ [b] ◄──▶ tail

   put("d")  →  size = 4 > maxSize = 3  →  removeEldestEntry() removes the TAIL ("b")

     head ──▶ [d] ◄──▶ [a] ◄──▶ [c] ◄──▶ tail
```

**What to look at:** the list is *doubly* linked, so removing the tail (the LRU) is O(1) — no traversal needed. This is why a `LinkedHashMap`-based LRU is O(1) per operation, unlike a hand-rolled LRU with a `HashMap` + `LinkedList`.

### 4.6 `ConcurrentHashMap` — bin-level locking

```text
   Thread-A writes key "apple" (hash → bin 3)
   Thread-B writes key "banana" (hash → bin 7)

   ┌──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐
   │ bin0 │ bin1 │ bin2 │ bin3 │ bin4 │ bin5 │ bin6 │ bin7 │
   └──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┘
                                    ▲                       ▲
                        Thread-A: synchronized(head)  Thread-B: synchronized(head)
                        holds the monitor on bin3's    holds the monitor on bin7's
                        head Node only                 head Node only
                                    │                       │
                                    ▼                       ▼
                              🔒 bin3                 🔒 bin7
                            (no contention)         (no contention)

   Thread-C writes key "cherry" (hash → bin 3):
                                    ▲
                                    └──► BLOCKED on bin3's monitor
                                         (contention is per-bin, not per-table)

   READERS:  get() never locks.  A reader traversing bin3 while Thread-A holds
              the bin3 monitor still sees a CONSISTENT node — it either sees
              the old value or the new one, never a torn one, because the
              value field is `volatile` and the node link is `final`.
```

**What to look at:** the lock is on the **head `Node` object** of the bin, not on the table and not on a segment. Two threads writing to different bins never interact. This is why `ConcurrentHashMap` scales far past 16 threads, unlike Java 7's segment design.

### 4.7 `sizeCtl` — one field, three jobs

```text
   ┌─────────────────────────────────────────────────────────────────┐
   │  state                │  sizeCtl meaning                       │
   ├─────────────────────────────────────────────────────────────────┤
   │  table == null        │  sizeCtl = the REQUESTED initial cap   │
   │  (before first put)   │  (e.g. new ConcurrentHashMap<>(100)     │
   │                       │   → sizeCtl = 100)                     │
   ├─────────────────────────────────────────────────────────────────┤
   │  resize IN PROGRESS  │  sizeCtl < 0  →  encodes the transfer  │
   │                       │  size so that a HELPER thread knows    │
   │                       │  it must help move entries             │
   ├─────────────────────────────────────────────────────────────────┤
   │  steady state         │  sizeCtl = capacity × loadFactor       │
   │                       │  (the resize threshold)                │
   └─────────────────────────────────────────────────────────────────┘
```

**What to look at:** `sizeCtl` is negative *only* while a resize is in flight. A thread that sees a negative `sizeCtl` knows a resize is happening and can participate in moving the entries — this is how `ConcurrentHashMap` avoids a stop-the-world resize.

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — Watch the internal state change, bucket by bucket

```java
import java.util.HashMap;
import java.util.Map;

public class HashMapTrace {
    public static void main(String[] args) {
        Map<String, Integer> map = new HashMap<>();

        // 1) An empty HashMap has NO table yet (lazy allocation)
        System.out.println("table allocated before any put? " + (map.isEmpty()));

        map.put("A", 1);
        map.put("B", 2);
        map.put("C", 3);
        map.put("Aa", 4);   // same hashCode as "BB", and even same as... see below

        System.out.println("size   = " + map.size());
        System.out.println("get(A) = " + map.get("A"));
        System.out.println("get(aa)= " + map.get("aa"));   // case-sensitive → null
        System.out.println("get(Aa)= " + map.get("Aa"));
        System.out.println("containsKey(A) = " + map.containsKey("A"));

        // 2) Overwrite an existing key → size does NOT grow
        Integer old = map.put("A", 99);
        System.out.println("put(A,99) returned old value = " + old);
        System.out.println("size after overwrite = " + map.size());

        // 3) remove, then re-put
        map.remove("A");
        System.out.println("size after remove(A) = " + map.size());
        map.put("A", 1);
        System.out.println("size after re-put(A) = " + map.size());
    }
}
```

**Actual output:**

```text
table allocated before any put? true
size   = 4
get(A) = 1
get(aa)= null
get(Aa)= 4
containsKey(A) = true
put(A,99) returned old value = 1
size after overwrite = 4
size after remove(A) = 3
size after re-put(A) = 4
```

**Dry run — the internal table after each `put`:**

| Step | Operation | hash (spread) | index `(15 & hash)` | Bucket 1 / 2 / 3 / 6 contents | `size` |
|------|-----------|---------------|---------------------|--------------------------------|--------|
| 0 | `new HashMap<>()` | — | — | `table == null` | 0 |
| 1 | `put("A",1)` | `65` | `65 & 15 = 1` | `1:[A→1]` | 1 |
| 2 | `put("B",2)` | `66` | `66 & 15 = 2` | `2:[B→2]` | 2 |
| 3 | `put("C",3)` | `67` | `67 & 15 = 3` | `3:[C→3]` | 3 |
| 4 | `put("Aa",4)` | `2112` | `2112 & 15 = 0` | `0:[Aa→4]` | 4 |
| 5 | `put("A",99)` | `65` | `1` | `1:[A→99]` (value replaced) | 4 |
| 6 | `remove("A")` | `65` | `1` | `1: null` | 3 |
| 7 | `put("A",1)` | `65` | `1` | `1:[A→1]` | 4 |

**Takeaway:** `size` only counts *distinct keys*. Overwriting a value is not an insertion — it does not bump `size` and does not trigger a resize.

> 💡 Note `"Aa".hashCode() == 2112` and `"BB".hashCode() == 2112` — but `"Aa"` and `"BB"` are *different* keys, so both live in the map. If `"BB"` were also inserted, it would join `"Aa"` in bucket 0 as a **chain** (same hash → `equals()` is called → `false` → append). The `hash` comparison is a fast *filter*; `equals()` is the truth.

### Example 2 — The mutable-key bug (a real memory leak)

```java
import java.util.HashMap;
import java.util.Map;

public class MutableKeyBug {
    static class Key {
        private final int id;
        Key(int id) { this.id = id; }
        @Override public int hashCode() { return id; }
        @Override public boolean equals(Object o) {
            return o instanceof Key k && k.id == id;
        }
        @Override public String toString() { return "Key(" + id + ")"; }
        void setId(int id) { this.id = id; }   // ← the bug
    }

    public static void main(String[] args) {
        Map<Key, String> map = new HashMap<>();
        Key k = new Key(1);
        map.put(k, "value");

        System.out.println("get before mutation = " + map.get(k));

        k.setId(2);              // mutate the key AFTER inserting it

        System.out.println("get after mutation  = " + map.get(k));   // null!
        System.out.println("map.get(new Key(2)) = " + map.get(new Key(2))); // null too!
        System.out.println("map.size()          = " + map.size());   // 1 — the entry is unreachable
    }
}
```

**Actual output:**

```text
get before mutation = value
get after mutation  = null
map.get(new Key(2)) = null
map.size()          = 1
```

**Why.** `HashMap` **caches** the hash it computed at `put()` time (in `Node.hash`). After `setId(2)`, the key's *current* `hashCode()` is `2`, so `get(k)` computes `hash = 2` and looks in **bucket 2**. But the entry is physically stored in **bucket 1**. The entry is still in the map (size is 1) but is **permanently unreachable** — a silent leak. Even `get(new Key(2))` fails, because `new Key(2)` also hashes to bucket 2.

**Takeaway:** *never* use a mutable object as a `HashMap` key. If the key's identity must change, remove and re-insert it.

### Example 3 — `LinkedHashMap` LRU cache with `removeEldestEntry`

```java
import java.util.LinkedHashMap;
import java.util.Map;

public class LruCacheDemo {
    static class LruCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxSize;
        LruCache(int maxSize) {
            super(maxSize, 0.75f, true);   // accessOrder = TRUE
            this.maxSize = maxSize;
        }
        @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }

    public static void main(String[] args) {
        LruCache<String, Integer> cache = new LruCache<>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        System.out.println("after 3 puts   : " + cache.keySet());

        cache.get("a");                    // 'a' becomes most-recently-used
        System.out.println("after get(a)   : " + cache.keySet());

        cache.put("d", 4);                 // evicts the LRU, which is now "b"
        System.out.println("after put(d)   : " + cache.keySet());
        System.out.println("b still there? : " + cache.containsKey("b"));
    }
}
```

**Actual output:**

```text
after 3 puts   : [a, b, c]
after get(a)   : [a, c, b]
after put(d)   : [d, a, c]
b still there? : false
```

**Dry run:**

| Step | Action | Access order (head → tail) | Size | Evicted |
|------|--------|-----------------------------|------|---------|
| 1 | `put("a",1)` | `[a]` | 1 | — |
| 2 | `put("b",2)` | `[a, b]` | 2 | — |
| 3 | `put("c",3)` | `[a, b, c]` | 3 | — |
| 4 | `get("a")` | `[a, c, b]` ← "a" moved to head | 3 | — |
| 5 | `put("d",4)` | `[d, a, c]` | 3 | **"b"** (tail = LRU) |

**Takeaway:** with `accessOrder = true`, a *read* reorders the map. That is exactly what makes it LRU — and exactly why you should not use `accessOrder = true` if you need stable iteration order.

### Example 4 — `ConcurrentHashMap` atomic `computeIfAbsent` vs the racy `get`/`put`

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

public class ConcurrentMapDemo {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
        AtomicInteger computations = new AtomicInteger();

        Runnable racy = () -> {
            for (int i = 0; i < 1000; i++) {
                String v = cache.get("k");
                if (v == null) {                 // ← CHECK
                    cache.put("k", "computed");  // ← ACT  (not atomic with the check)
                }
            }
        };

        Thread t1 = new Thread(racy), t2 = new Thread(racy);
        t1.start(); t2.start(); t1.join(); t2.join();

        System.out.println("racy  → value = " + cache.get("k"));
        System.out.println("racy  → the compute() body ran " + computations.get() + " times (may be >1)");

        // ---- the correct version ----
        ConcurrentHashMap<String, String> cache2 = new ConcurrentHashMap<>();
        AtomicInteger calls = new AtomicInteger();

        Runnable safe = () -> {
            for (int i = 0; i < 1000; i++) {
                cache2.computeIfAbsent("k", key -> {
                    calls.incrementAndGet();
                    return "computed";
                });
            }
        };

        Thread t3 = new Thread(safe), t4 = new Thread(safe);
        t3.start(); t4.start(); t3.join(); t4.join();

        System.out.println("safe  → value = " + cache2.get("k"));
        System.out.println("safe  → the compute() body ran " + calls.get() + " time(s)  ← EXACTLY ONCE");
    }
}
```

**Actual output** (typical run — the racy count is *non-deterministic*):

```text
racy  → value = computed
racy  → the compute() body ran 2 times (may be >1)
safe  → value = computed
safe  → the compute() body ran 1 time(s)  ← EXACTLY ONCE
```

**Takeaway:** `computeIfAbsent` guarantees the mapping function runs **at most once per key** (it may run more than once in the presence of exceptions or contention on *different* keys, but not for the same key concurrently). The naive `get`/`put` pattern is a textbook **check-then-act** race — see [08 · Concurrency](../08-Multithreading-Concurrency/04-Concurrency-Problems.md).

### Example 5 — `TreeMap` range queries, and why it rejects a `null` key

```java
import java.util.Map;
import java.util.TreeMap;

public class TreeMapDemo {
    public static void main(String[] args) {
        TreeMap<Integer, String> scores = new TreeMap<>();
        scores.put(85, "B");
        scores.put(92, "A");
        scores.put(78, "C");
        scores.put(95, "A+");
        scores.put(60, "F");

        System.out.println("sorted keys       : " + scores.keySet());
        System.out.println("headMap (>=80)    : " + scores.headMap(80));
        System.out.println("floorEntry(90)    : " + scores.floorEntry(90));
        System.out.println("ceilingEntry(90)  : " + scores.ceilingEntry(90));
        System.out.println("higherEntry(92)    : " + scores.higherEntry(92));
        System.out.println("firstKey/lastKey  : " + scores.firstKey() + " / " + scores.lastKey());
        System.out.println("subMap(70,90)     : " + scores.subMap(70, 90));

        try {
            scores.put(null, "boom");
        } catch (NullPointerException e) {
            System.out.println("null key rejected : " + e.getClass().getSimpleName());
        }
    }
}
```

**Actual output:**

```text
sorted keys       : [60, 78, 85, 92, 95]
headMap (>=80)    : {85=B, 92=A, 95=A+}
floorEntry(90)    : 85=B
ceilingEntry(90)  : 92=A
higherEntry(92)    : 95=A+
firstKey/lastKey  : 60 / 95
subMap(70,90)     : {78=C, 85=B}
null key rejected : NullPointerException
```

**Takeaway:** `TreeMap` is the only JDK map that gives you *range queries* and *navigation*. It is O(log n) for everything — including `get` — which is a real advantage when you need worst-case guarantees or ordered traversal.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A correct, thread-safe, bounded LRU cache

```java
package com.example.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * A bounded LRU cache built on LinkedHashMap.
 *
 * <p>Design decisions worth defending in an interview:
 * <ul>
 *   <li><b>Why LinkedHashMap?</b> It gives O(1) get/put AND O(1) eviction of the
 *       least-recently-used entry (the tail of its internal doubly-linked list).
 *       A hand-rolled HashMap+LinkedList LRU is also O(1) but far more code.</li>
 *   <li><b>Why a lock?</b> LinkedHashMap is not thread-safe, and with
 *       {@code accessOrder = true} even a <i>read</i> mutates the structure.
 *       A ReentrantLock around every operation makes the cache consistent.</li>
 *   <li><b>Why not ConcurrentHashMap + a separate LRU list?</b> That is what
 *       Caffeine does, and it is the right choice at scale. This version is
 *       for "small, hot, single-JVM" caches.</li>
 * </ul>
 */
public class LruCache<K, V> {

    private final int maxSize;
    private final ReentrantLock lock = new ReentrantLock();
    private final LinkedHashMap<K, V> map;

    public LruCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0, got " + maxSize);
        }
        this.maxSize = maxSize;
        // accessOrder = true  →  get() marks the entry as most-recently-used
        this.map = new LinkedHashMap<>(maxSize, 0.75f, true);
    }

    public V get(K key) {
        lock.lock();
        try {
            return map.get(key);
        } finally {
            lock.unlock();
        }
    }

    /** Returns the cached value, computing (and caching) it on a miss. */
    public V getOrCompute(K key, Function<K, V> loader) {
        lock.lock();
        try {
            V value = map.get(key);
            if (value != null) {
                return value;
            }
            value = loader.apply(key);
            if (value != null) {
                map.put(key, value);
            }
            return value;
        } finally {
            lock.unlock();
        }
    }

    public void put(K key, V value) {
        lock.lock();
        try {
            map.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    public V remove(K key) {
        lock.lock();
        try {
            return map.remove(key);
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    /** A snapshot of the keys, least-recently-used first. */
    public java.util.List<K> keysInLruOrder() {
        lock.lock();
        try {
            // The tail is the LRU, so iterate the entry set in reverse.
            java.util.List<K> keys = new java.util.ArrayList<>(map.size());
            var it = map.entrySet().iterator();
            java.util.List<K> forward = new java.util.ArrayList<>();
            while (it.hasNext()) {
                forward.add(it.next().getKey());
            }
            java.util.Collections.reverse(forward);
            keys.addAll(forward);
            return keys;
        } finally {
            lock.unlock();
        }
    }
}
```

**Why the `finally` is non-negotiable:** if `loader.apply(key)` throws, the lock must still be released. A `lock.lock()` without a `finally` turns one exception into a permanent deadlock for the whole JVM.

### 6.2 A correct `ConcurrentHashMap` usage — and the mistakes to avoid

```java
package com.example.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class ConcurrentMetricsRegistry {

    private final ConcurrentHashMap<String, Metric> metrics = new ConcurrentHashMap<>();

    /** LongAdder, not AtomicLong: metrics are incremented from many threads. */
    private static final class Metric {
        final LongAdder count = new LongAdder();
        volatile long lastValue;      // volatile: written rarely, read often
    }

    public void record(String name, long value) {
        // computeIfAbsent is ATOMIC — no lost updates, no double-creation.
        Metric m = metrics.computeIfAbsent(name, k -> new Metric());
        m.count.increment();
        m.lastValue = value;
    }

    public long count(String name) {
        Metric m = metrics.get(name);
        return m == null ? 0L : m.count.sum();
    }

    public long total() {
        // sumThenReset on each cell — but for a plain total, sum() is fine.
        long sum = 0;
        for (Metric m : metrics.values()) {
            sum += m.count.sum();
        }
        return sum;
    }

    /**
     * ❌ WRONG — check-then-act race:
     *    two threads can both see "absent" and both create the metric.
     */
    public void recordRacy(String name, long value) {
        Metric m = metrics.get(name);
        if (m == null) {
            m = new Metric();
            metrics.put(name, m);   // ← the other thread's metric is overwritten!
        }
        m.count.increment();
    }

    /**
     * ✅ CORRECT — atomic replace-if-absent.
     */
    public void recordSafely(String name, long value) {
        metrics.putIfAbsent(name, new Metric()).count.increment();
    }

    /** ✅ CORRECT — merge is atomic per key. */
    public void recordWithMerge(String name, long value) {
        metrics.merge(name, 1L, (oldCount, one) -> oldCount + one);
    }
}
```

> ⚠️ **`putIfAbsent` returns the *previous* value** (or `null` if there was none) — so `metrics.putIfAbsent(name, new Metric()).count.increment()` **NPEs** when the key was absent, because it returns `null`. The correct one-liner is:
> ```java
> Metric m = metrics.computeIfAbsent(name, k -> new Metric());
> m.count.increment();
> ```
> or
> ```java
> metrics.compute(name, (k, old) -> { old.increment(); return old; });
> ```

### 6.3 A `Map` that handles a mutable key correctly

```java
package com.example.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A map that tolerates key mutation by REMOVING and RE-INSERTING.
 * Use this pattern when the key really must be mutable.
 */
public class MutableKeySafeMap<K, V> {

    private final Map<K, V> delegate = new HashMap<>();

    /** Call this instead of mutating the key directly. */
    public synchronized void update(K oldKey, K newKey, V newValue) {
        if (delegate.remove(oldKey) != null) {
            delegate.put(newKey, newValue);
        }
    }

    public synchronized V get(K key) {
        return delegate.get(key);
    }

    public synchronized int size() {
        return delegate.size();
    }
}
```

**Better still:** make the key immutable. Use a `record` (Java 16+) or a class with only `final` fields and a `hashCode` derived from them. Then the bug cannot exist.

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Using a mutable object as a key

```java
// ❌ BROKEN — the entry becomes unreachable after mutation
class User { String email; User(String e){email=e;} 
             public int hashCode(){return email.hashCode();}
             public boolean equals(Object o){return o instanceof User u && u.email.equals(email);} }

Map<User, Order> orders = new HashMap<>();
User u = new User("a@x.com");
orders.put(u, order);
u.email = "b@x.com";       // ← orders.get(u) now returns null. Silent leak.
```

**✅ FIX** — make the key immutable (`record User(String email) {}`), or remove-and-re-insert on mutation.

---

### ❌ Pitfall 2 — Modifying a map while iterating it

```java
// ❌ BROKEN — ConcurrentModificationException
for (String key : map.keySet()) {
    if (key.startsWith("temp-")) map.remove(key);
}

// ✅ FIX — use the iterator's remove()
Iterator<String> it = map.keySet().iterator();
while (it.hasNext()) {
    if (it.next().startsWith("temp-")) it.remove();
}

// ✅ FIX (Java 8+) — removeIf
map.keySet().removeIf(k -> k.startsWith("temp-"));
```

**What `ConcurrentModificationException` really is.** The `ArrayList`/`HashMap` iterators keep an `expectedModCount` and compare it with the collection's `modCount` on every `next()`. A structural change bumps `modCount`, and the mismatch throws. **It is best-effort, not a guarantee** — a modification after the last `next()` call may not be detected, and the exception is a *safety net*, not a guarantee of correctness.

---

### ❌ Pitfall 3 — Using `HashMap` in concurrent code

```java
// ❌ BROKEN — lost updates, infinite loops (pre-Java 8), corrupted structure
private static final Map<String, Integer> COUNTS = new HashMap<>();

public void increment(String key) {
    COUNTS.merge(key, 1, Integer::sum);   // ❌ NOT ATOMIC
}
```

**✅ FIX** — `ConcurrentHashMap.merge()` is atomic per key:

```java
private static final ConcurrentMap<String, Integer> COUNTS = new ConcurrentHashMap<>();
COUNTS.merge(key, 1, Integer::sum);
```

---

### ❌ Pitfall 4 — Assuming `HashMap` iteration order

```java
Map<String, Integer> m = new HashMap<>();
m.put("z", 1); m.put("a", 2); m.put("m", 3);
// iteration order is NOT z, a, m — and it is NOT GUARANTEED AT ALL.
```

**✅ FIX** — if you need a defined order, use `LinkedHashMap` (insertion order) or `TreeMap` (sorted order). Never rely on `HashMap`'s order — it can change between JDK versions, and even between runs when the hash of a `String` differs across JVMs.

---

### ❌ Pitfall 5 — Confusing `Map` with `Collection`

```java
map.add(k, v);              // ❌ compile error — Map has no add()
for (Map.Entry e : map)     // ❌ compile error — Map is not Iterable
```

**✅ FIX** — `map.put(k, v)` and `for (Map.Entry<K,V> e : map.entrySet())`.

---

### ❌ Pitfall 6 — `ConcurrentHashMap` with `null`

```java
chm.put(null, "x");     // ❌ NullPointerException
chm.get(null);          // ❌ NullPointerException
chm.put("k", null);     // ❌ NullPointerException
```

**✅ FIX** — use a sentinel (`Optional`, or a constant like `""`). `HashMap` allows nulls; `ConcurrentHashMap` deliberately does not.

---

### ❌ Pitfall 7 — Sizing the map wrong

```java
// ❌ SUBTLY WRONG — you want capacity for N entries, but HashMap resizes at 0.75*N
Map<String, Long> m = new HashMap<>(1000);   // resizes at 750 entries, then again at 1500

// ✅ CORRECT — size for the entries you will actually hold
Map<String, Long> m = new HashMap<>(1000 / 0.75f + 1);   // ≈ 1334 → no resize
```

---

### ❌ Pitfall 8 — `Hashtable` / `Collections.synchronizedMap` in new code

```java
Map<String, String> m = Collections.synchronizedMap(new HashMap<>());  // ❌ one global lock
```

**✅ FIX** — `new ConcurrentHashMap<>()`: lock-free reads, per-bin write locks.

---

### ❌ Pitfall 9 — `Map.of` with nulls, or with duplicate keys

```java
Map.of("a", null);                    // ❌ NullPointerException
Map.of("a", 1, "a", 2);               // ❌ IllegalArgumentException (duplicate key)
```

**✅ FIX** — use `HashMap` (or `Collections.singletonMap`) if you need nulls or duplicate-key detection.

---

### ❌ Pitfall 10 — `equals` without `hashCode` (or vice versa)

```java
// ❌ BROKEN — the key goes into one bucket but is looked up in another
@Override public boolean equals(Object o) { ... }   // hashCode NOT overridden

// ❌ BROKEN — two equal keys land in different buckets → duplicates in the map
@Override public int hashCode() { ... }              // equals NOT overridden
```

**✅ FIX** — always override both, together, and base `hashCode` on exactly the fields used by `equals`.

---

### ❌ Pitfall 11 — `TreeMap` with a `null` key

```java
treeMap.put(null, "v");     // ❌ NullPointerException
```

**✅ FIX** — use a `Comparator` that handles `null` explicitly if you truly need it, or use `HashMap` + `Collections.navigableMap` alternatives.

---

### ❌ Pitfall 12 — `WeakHashMap` where the value references the key

```java
// ❌ BROKEN — the value keeps the key alive → the weak reference NEVER fires
Map<Key, List<Key>> map = new WeakHashMap<>();
```

**✅ FIX** — make the value not reference the key, or use `WeakHashMap` only for pure caches.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is the difference between a `HashMap` and a `Hashtable`?**
**A:** `HashMap` is not synchronized and allows one `null` key and `null` values. `Hashtable` synchronizes every method (coarse-grained locking, legacy) and rejects `null` keys and values with a `NullPointerException`. In modern code you use `HashMap` (or `ConcurrentHashMap` when concurrency is needed) and never `Hashtable`.

**Q: What is the difference between `HashMap` and `LinkedHashMap`?**
**A:** `HashMap` has no ordering guarantee. `LinkedHashMap` maintains a doubly-linked list through its entries, so it iterates in **insertion order** (or **access order** if constructed with `accessOrder = true`). Internally `LinkedHashMap extends HashMap` and adds only the list — the hashing and bucketing are identical.

**Q: What is the difference between `HashMap` and `TreeMap`?**
**A:** `HashMap` is hash-based: O(1) average `get`/`put`, no ordering, allows a `null` key. `TreeMap` is a red-black tree: O(log n) **guaranteed** `get`/`put`, keeps keys sorted, supports range queries (`headMap`, `subMap`, `floorEntry`), and rejects a `null` key.

**Q: Is `HashMap` thread-safe?**
**A:** No. Concurrent `put`/`get` can corrupt the internal structure (infinite loops in `resize()` were common before Java 8) and lose updates. Use `ConcurrentHashMap`, or `Collections.synchronizedMap` for a single coarse lock.

**Q: What does `map.get(key)` return if the key is absent?**
**A:** `null` — for `HashMap`. (`ConcurrentHashMap` also returns `null` for an absent key, but it *forbids* null values, so `null` unambiguously means "absent".)

**Q: What is the initial capacity and load factor of a `HashMap`?**
**A:** Default capacity **16**, default load factor **0.75**. The map resizes when `size` exceeds `capacity × loadFactor` = 12, doubling the capacity to 32 and the threshold to 24.

**Q: How do you iterate over all entries of a `Map`?**
**A:** `for (Map.Entry<K,V> entry : map.entrySet())` — this is the most efficient form because it gives you both key and value in one lookup. `map.keySet()` and `map.values()` are alternatives when you only need one side.

### 🟡 Intermediate

**Q: Why does `HashMap` compute `hash = key.hashCode() ^ (key.hashCode() >>> 16)`?**
**A:** Because the bucket index uses only the *low* bits of the hash: `index = (n-1) & hash`. If a key's `hashCode` has its entropy concentrated in the high bits, all such keys would collide into the same few buckets. The `>>> 16` shift folds the high 16 bits down into the low 16, and the XOR mixes them in, so the bucket choice depends on the whole 32-bit hash and the distribution stays uniform as the table grows.

**Q: Why is the table length always a power of two?**
**A:** So that the index can be computed with `(n-1) & hash` instead of `hash % n`. `n-1` is then a perfect low-bit mask, the AND is a single instruction (no division), and — crucially — the modulo of a power of two would use exactly the same low bits, so the AND is *equivalent* to the modulo but faster. A non-power-of-two capacity (say 10) would make `n-1 = 9 = 0b1001` and only bits 0 and 3 would ever be used, skewing the distribution.

**Q: What happens to the entries in a bucket when the `HashMap` resizes?**
**A:** The capacity doubles, and each entry either keeps its index or moves to `index + oldCap`. The implementation splits each chain into a "lo" list (stays) and a "hi" list (moves) based on a **single bit test** — `hash & oldCap` — preserving the relative order within each list. No key is re-hashed, so a costly `hashCode()` is not recomputed.

**Q: When does a bucket turn into a tree, and when does it turn back?**
**A:** At `TREEIFY_THRESHOLD = 8` entries in a bucket, `treeifyBin` is called — but only if the table length is at least `MIN_TREEIFY_CAPACITY = 64`; otherwise the map resizes instead (which disperses the collisions more cheaply). The tree is converted back to a list below `UNTREEIFY_THRESHOLD = 6` entries, because tree nodes carry significantly more memory.

**Q: What is the difference between `HashMap`, `LinkedHashMap` and `TreeMap` in terms of iteration order and complexity?**
**A:** `HashMap`: no order, O(1) average. `LinkedHashMap`: insertion or access order, O(1) average. `TreeMap`: sorted by key, O(log n) for every operation. If you need *ordered iteration only* and O(1) lookup, `LinkedHashMap` is the answer — it is strictly better than `TreeMap` for that use case.

**Q: Why does `ConcurrentHashMap` not allow `null` keys or values?**
**A:** Because `get(key)` returns `null` for an absent key, a `null` *value* would make "the key is absent" indistinguishable from "the key is present with a null value". `computeIfAbsent` and `merge` have the same problem: they must be able to tell "the mapping function returned null → remove the entry" from "there was no entry". `ConcurrentHashMap` also uses `null` as the internal "no node" marker.

**Q: What is a "weakly consistent" iterator, and why does `ConcurrentHashMap` have one?**
**A:** An iterator that reflects the state of the map at *some* point at or since the iterator was created. It never throws `ConcurrentModificationException` and never sees a partially-updated entry. A truly consistent snapshot would require locking the entire map for the whole iteration, which would defeat the purpose of a concurrent map. This is a deliberate trade of strict consistency for non-blocking iteration.

### 🔴 Advanced / Senior

**Q: How does `ConcurrentHashMap` achieve thread safety in Java 8, and how does it differ from Java 7?**
**A:** Java 7 used an array of `Segment`s, each a mini `HashMap` with its own `ReentrantLock` — so writes to different segments were parallel, but concurrency was capped at 16 by default and memory was wasted on the segment array. Java 8 removed the segments entirely: there is one `Node[]` table, empty bins are populated with a **CAS** on the head node, and occupied bins are updated with `synchronized` on the **head node of that bin**. The lock granularity is therefore a single bin, so any number of threads can write in parallel as long as they target different bins. Reads are completely lock-free.

**Q: What is `sizeCtl` in `ConcurrentHashMap`?**
**A:** A single overloaded `volatile int` with three roles. Before the table is allocated, it holds the *requested initial capacity*. During a resize it is **negative**, encoding the transfer size so that a thread arriving mid-resize knows it must help move entries rather than start its own resize. In steady state it holds the resize threshold (`capacity × loadFactor`). The negative-value trick is how `ConcurrentHashMap` avoids a stop-the-world resize.

**Q: Why is `ConcurrentHashMap.size()` not simply a field read?**
**A:** Because writes are concurrent, a single counter would be a contention hotspot and would not be accurate. `size` is maintained as a `baseCount` plus a `CounterCell[]` array of striped counters; `size()` sums them and, if the sum looks inconsistent (a resize happened during the count), it retries. The result is a *weakly consistent* snapshot — good enough for metrics, not a linearizable count.

**Q: How do you safely update a value in a map from many threads?**
**A:** Use `ConcurrentHashMap` and one of the atomic per-key operations: `merge(key, value, remappingFunction)`, `compute(key, (k, old) -> ...)`, or `computeIfAbsent(key, k -> ...)`. All three are atomic *for that key* — the remapping function is applied at most once concurrently for the same key. The naive `get`-then-`put` is a check-then-act race and loses updates.

**Q: Why is `HashMap`'s `Node.value` not `final`, while `Node.key` and `Node.hash` are?**
**A:** The value must be replaceable — `put()` on an existing key overwrites it in place, which is what makes `put` O(1) instead of "remove + insert". The key and the cached hash can never change for a given entry, so they are `final` — which also gives the safe-publication guarantee the JMM needs for a `final` field. `ConcurrentHashMap` makes its `value` field `volatile` so that an overwrite is immediately visible to lock-free readers.

**Q: How would you detect and fix a `HashMap` performance problem in production?**
**A:** Symptom: p99 latency spikes, high CPU, or a specific endpoint slowing down. Diagnosis: take a **heap histogram** (`jcmd <pid> GC.class_histogram`) and look for one class dominating the instance count; use a **CPU profile** (async-profiler) to see whether the time is in `HashMap.get`/`put`. If a `HashMap` dominates, the usual cause is (a) an oversized map holding too much data — fix by bounding it; (b) a pathological `hashCode` causing a hot bucket — fix by fixing `hashCode`; (c) a `HashDoS` attack — fix by switching to a `TreeMap`/`ConcurrentHashMap` (treeified buckets) or by using a randomized hasher.

**Q: Why does the `hashCode` contract matter so much for `HashMap` correctness?**
**A:** Because the map uses `hash` as a *filter* and `equals` as the *truth*. If two equal keys have different `hashCode`s, they land in different buckets and the map treats them as two distinct entries — so `map.get(equalKey)` fails and `map.size()` is wrong. If two unequal keys have the same `hashCode`, that is merely a performance issue (a longer chain), not a correctness bug. The contract is therefore: *equal objects must have equal hash codes*.

### ⚫ Expert / Principal

**Q: Explain the HashDoS (hash collision) attack and how to defend against it.**
**A:** An attacker submits many objects whose `hashCode()` all return the same value (Java's `String.hashCode` is public and easy to invert, and most JDK classes allow it). In a `HashMap` all of them land in one bucket. Before Java 8 that was an O(n) chain → O(n²) total → CPU exhaustion. Java 8 **treeifies** a bucket at 8 entries, which caps the lookup at O(log n) and largely defuses the attack. But: (1) treeification only happens if the table is ≥ 64 buckets, so a small map stays vulnerable; (2) the tree's comparisons fall back to `System.identityHashCode`, which is *not* attacker-controlled but is also not helpful. Real defences are (a) use `ConcurrentHashMap` (which treeifies too, and has a `RESIZE` threshold), (b) keep the map's capacity large enough to trigger treeification, (c) use a randomized hasher so the attacker cannot predict the bucket, and (d) for untrusted input, prefer a `TreeMap` or a `ConcurrentSkipListMap` (O(log n) guaranteed, no hash dependency at all).

**Q: What is the memory overhead of a `HashMap` entry, and how do you compute the "right" initial capacity?**
**A:** On a 64-bit JVM with compressed oops (heap ≤ 32 GB), a `Node` is **32 bytes**: 12 bytes of object header (8-byte Mark Word + 4-byte compressed Klass pointer), 4 bytes for the `int hash`, 4 for the compressed `key` reference, 4 for the compressed `value` reference, and 4 for the compressed `next` reference — plus alignment to 8 bytes. The bucket array adds 4 bytes per slot (compressed) or 8 (uncompressed). So a `HashMap` with 1 M entries costs roughly `1M × 32 + 2M × 4 ≈ 40 MB` of map overhead alone, *before* the keys and values. To size it: `new HashMap<>((int)(expectedEntries / 0.75f) + 1)` — the `+1` avoids `tableSizeFor(0)` returning 0, and the division by the load factor ensures you never hit a resize.

**Q: How would you design a map for a use case where keys are inserted and read constantly but the map must support cheap snapshots?**
**A:** `ConcurrentHashMap` for the live map, and copy-on-read for snapshots — but a full `HashMap` copy is O(n) and locks nothing, so a `new HashMap<>(chm)` snapshot under a moment of inconsistency is often acceptable for *reporting* use cases. If you need a truly consistent snapshot, use `ConcurrentSkipListMap` (sorted + concurrent) or take a read-write lock around a plain `HashMap` and accept the contention. If the read rate vastly exceeds the write rate, a **read-mostly** structure like a copy-on-write `HashMap` (writes copy the whole map) can win — but it is O(n) per write, so only for tiny maps with rare writes.

**Q: Explain precisely why `HashMap` allows a `null` key but `ConcurrentHashMap` does not — and what the "right" answer is for a domain model.**
**A:** `HashMap` allows a `null` key because it has an unambiguous representation for it: the hash function maps `null` to `0` and the key comparison uses `key == existingKey || key.equals(existingKey)` with an explicit null check. `ConcurrentHashMap` forbids nulls for a *different* reason: its `get()` cannot distinguish "absent" from "present with null", and its `computeIfAbsent`/`merge`/`remove(key, value)` APIs rely on that distinction. Beyond the technical reason, the *design* lesson is the same: **null in a map is a modelling smell.** The right domain answer is to model absence explicitly — `Optional` in the value, a sentinel constant, or a separate `containsKey` check. In a domain model, prefer a non-null key object (a `record` with the natural key) over allowing `null`.

**Q: When is `IdentityHashMap` the right tool, and what is its cost?**
**A:** When *reference identity* is the semantic key — for example, tracking which specific object instances have been visited, or when two equal-but-distinct objects must be treated as different entries. It uses `System.identityHashCode()` and `==` instead of `hashCode()`/`equals()`, so it is not a general-purpose map: it violates the `Map` contract that `equals`-equal keys are the same key, and `get` by an equal-but-different object returns `null`. Its cost is that `identityHashCode` is a native call that is *not* cached per object, so repeated lookups are slower than `HashMap`; and it does not override `equals`/`hashCode` meaningfully. It is a specialist tool — reach for it deliberately, never by accident.

---

## 9. Counter-Questions & Traps

### CQ1. "Isn't a `HashMap` enough? Why would you ever use `ConcurrentHashMap`?"

- **Naive:** "`HashMap` isn't thread-safe, so `ConcurrentHashMap` is."  ← *true but useless*
- **The trap:** "Thread-safe" hides the real design. `ConcurrentHashMap` is not a `HashMap` with a lock bolted on. It has **lock-free reads** (no monitor is ever acquired for a `get`), **per-bin write locks** (not a table-wide or segment-wide lock), **no null keys or values**, and a **weakly consistent** iterator that never throws `ConcurrentModificationException`. Meanwhile `Collections.synchronizedMap(new HashMap<>())` *is* thread-safe — every method takes the same monitor — and it would pass a naive "is it thread-safe?" test while being dramatically slower under concurrency.
- **Expert answer:** The right question is not "is it thread-safe?" but **"what is my contention pattern?"** If reads vastly outnumber writes, `ConcurrentHashMap` wins because reads take no lock at all. If writes are rare, a `synchronizedMap` is simpler and adequate. If you need a *consistent snapshot* of the whole map, neither is enough — you need a lock over the entire structure. And the null restriction is not a bug: it makes `get(k) == null` an unambiguous "absent", which is what lets `computeIfAbsent` be atomic per key.

### CQ2. "If `HashMap` is O(1), why does the JDK offer `TreeMap` at all?"

- **Naive:** "`TreeMap` is slower."  ← *true and irrelevant*
- **The trap:** `TreeMap` is not competing on speed; it is competing on **capability**. A `TreeMap` gives you (a) *sorted iteration*, (b) *range queries* (`headMap`, `tailMap`, `subMap`, `floorKey`, `ceilingKey`, `higherKey`, `lowerKey`), (c) a **worst-case O(log n)** guarantee instead of an average, and (d) `Comparator`-based ordering that has nothing to do with `hashCode`. None of that is available from a `HashMap`, no matter how fast it is.
- **Expert answer:** Choosing a collection is choosing the *operations you need*. If you need "find the 5 keys closest to X" or "iterate in sorted order", `TreeMap` is the only option — a `HashMap` cannot do it at all, not even slowly. The right comparison is: `HashMap` for *point lookups by key*; `TreeMap` for *ordered access and ranges*; `LinkedHashMap` for *ordered iteration with O(1) lookup* (usually the best of both worlds when you only need insertion order). And note the elegant middle ground: `Collections.unmodifiableNavigableMap` and `ConcurrentSkipListMap` exist because the JDK wanted "sorted + concurrent" without the `TreeMap` lock.

### CQ3. "You said `HashMap` resizes at 12 entries by default — so is `new HashMap<>()` a performance trap?"

- **Naive:** "Yes, I should always pre-size my maps."  ← *over-corrected*
- **The trap:** Resizing is **amortised O(1)**, not O(n) per insert. Over the life of a map holding N entries, the total cost of all resizes is O(N) (each resize rehashes all elements, and the sizes double: N/2 + N/4 + … = N). So a default-sized `HashMap` is *not* slow — it just does a few extra copies. Blindly pre-sizing every map to a huge capacity is its own mistake: a 1 M-slot array allocated for 10 entries wastes 4 MB and destroys cache locality.
- **Expert answer:** Pre-size when you **know** the final size and it is stable — a fixed-size lookup table, a batch import of known cardinality, a cache with a known bound. Use `new HashMap<>((int)(expected / 0.75f) + 1)`. Do **not** pre-size when the size is unknown or unbounded — you would allocate for the worst case and pay the memory. The `+1` matters: `tableSizeFor(0)` returns 1, and `(int)(0/0.75f)+1 = 1` is a valid capacity, whereas `tableSizeFor(0)` alone would be 0 and would break the allocation.

### CQ4. "If `hashCode` collides, doesn't `equals` slow the map down? Should I avoid collisions?"

- **Naive:** "Yes, collisions are bad, so I should make `hashCode` return unique values."  ← *a serious bug*
- **The trap:** Returning a **unique** `hashCode` per object (e.g. an identity counter) technically avoids collisions but destroys the map's usefulness: two *equal* objects would then have *different* hash codes, so `map.get(equalButDifferentKey)` returns `null` and the map contains duplicates. You would have broken the `equals`/`hashCode` contract. A good `hashCode` is **consistent with `equals`** and well-distributed — it maximises the number of *distinct* buckets that *equal* objects share, not the number of unique hash values.
- **Expert answer:** The contract is one-directional: equal objects **must** have equal hash codes; unequal objects *may* collide (and must, in fact, if the hash space is smaller than the object space). The performance goal is to make the hash *well-distributed*, not *collision-free* — the JDK's `String.hashCode` famously collides (`"Aa"` and `"BB"` both hash to 2112) and that is completely fine. The map handles it by chaining, and by treeifying if the chain gets hot.

### CQ5. "You claimed the `>>> 16` spread prevents collisions. Doesn't it?"

- **Naive:** "Yes, it spreads the hash so collisions are avoided."  ← *overclaims*
- **The trap:** The spread does **not** prevent collisions — it *delays and limits* them by making the low bits (the ones that pick the bucket) sensitive to the whole 32-bit hash. `"Aa"` and `"BB"` still collide after the spread. And the spread is deliberately *weak* (a single XOR) because a strong mixing function like `hashCode * 0x9E3779B9` would cost more per lookup and destroy the property that `resize()` can split chains with a single bit test.
- **Expert answer:** The spread is a **cheap re-biasing**, not a hash-quality upgrade. The property that actually matters is: *as the table grows and uses more low bits, the distribution keeps improving.* Without the spread, a hash function whose entropy lives in the high bits would never benefit from a larger table. With it, a 64-bucket table uses bits 0–5, all of which now carry information from bits 16–31. If you wanted real collision resistance you would use `ThreadLocalRandom`-seeded mixing — which is exactly what some high-security use cases do.

### CQ6. "Your `computeIfAbsent` example says the mapping function runs *at most once*. Is that really guaranteed?"

- **Naive:** "Yes, guaranteed exactly once."  ← *overclaims again*
- **The trap:** `computeIfAbsent` guarantees the mapping function is applied **atomically for that key** — it will not run concurrently for the same key. But it is **not** guaranteed to run *exactly* once overall: if the mapping function throws an exception, the mapping is not established and a later call will invoke it again. And for *different* keys, the function obviously runs many times, possibly concurrently.
- **Expert answer:** The precise guarantee is: *for a given key, at most one invocation of the mapping function is in progress at any time, and the result is atomically installed.* That is what makes it safe for "create if absent" semantics — the classic bug it fixes is two threads both creating a heavyweight object. The exception caveat matters in practice: if your loader throws transiently, you will pay the cost again on the next call, so the loader should be idempotent and cheap to retry.

### CQ7. "You said `ConcurrentHashMap` iterators are weakly consistent. What does that mean in practice, and can I trust the iteration?"

- **Naive:** "It means I might see stale data, which is fine."  ← *vague*
- **The trap:** Weak consistency is not the same as "stale". It guarantees specific properties: the iterator reflects the state of the map at **some point at or since** its creation; it never throws `ConcurrentModificationException`; it never returns a `null` value for a key that was present throughout; and it never sees a value that was not present at some point. It does **not** guarantee you see every concurrent update, and the traversal may reflect updates that happened *during* the iteration.
- **Expert answer:** Treat it as a *snapshot-with-fuzziness*: you get a view that was true at some instant, without paying for a full copy. This is exactly what you want for aggregation ("sum all balances", "count all orders") where an exact instant matters less than not blocking writers. What you must **not** do is use the iteration to drive a *decision* that must be globally consistent (e.g. "if I see X, then do Y" — the state may change between the read and the action). For that, use an atomic per-key operation, a lock, or a versioned compare-and-set.

### CQ8. "Why not just use `ConcurrentHashMap` everywhere and stop thinking about it?"

- **Naive:** "It is strictly better than `HashMap`."  ← *wrong on two counts*
- **The trap:** First, `ConcurrentHashMap` is **not** strictly faster for a single-threaded workload — it is measurably slower, because of `volatile` reads on `table`, the `CounterCell` bookkeeping, and the extra memory per node. Second, and more importantly, it is **not** a drop-in replacement: no null keys or values, a different iteration model, and `computeIfAbsent` semantics that differ subtly. Choosing it "to be safe" adds cost and constraints for a problem you may not have.
- **Expert answer:** Pick the simplest structure that satisfies the requirement. A `HashMap` that is confined to a single thread (or a thread-confined `ThreadLocal`) needs no synchronisation. The moment you genuinely share a map across threads, `ConcurrentHashMap` is the default — not because it is faster, but because it removes an entire *class* of bug (lost updates, corrupted structure) rather than requiring you to reason about it. The engineering judgement is about *identifying* the shared boundary, not about defaulting to the most capable structure everywhere.

### CQ9. "You mentioned `Map.of` rejects nulls. Why? Isn't that just annoying?"

- **Naive:** "It was a design decision to keep it simple."  ← *accurate but shallow*
- **The trap:** The real reason is **performance and simplicity of the representation**: `Map.of` is implemented as a compact array-based structure with a fast path for 0/1/2 entries (a single field, not an array!). Adding null-checking on top of that is possible, but the JDK took the position that a `null` key or value in an immutable map is almost always a *modelling* mistake that should fail loudly at the point of construction rather than become a latent `NullPointerException three layers away.
- **Expert answer:** The JDK's `Map.of`/`List.of` null rejection is an **API design decision in favour of fail-fast**. It is the same philosophy as `Objects.requireNonNull` — a null that reaches a value-type API is a bug in the caller, and catching it at the boundary gives a stack trace pointing at the real culprit. The "annoyance" is the price of making a class of bug impossible. If you genuinely need nulls, `HashMap` is right there — and the fact that you had to reach for it should itself be a signal to reconsider the model.

### CQ10. "If `HashMap` is so good, why did the JDK add `EnumMap`?"

- **Naive:** "Because enums are a special case."  ← *true but missing the point*
- **The trap:** `EnumMap` is not merely a convenience wrapper — it is **fundamentally faster**, by an order of magnitude for some operations, because it does **no hashing at all**. It is backed by a plain `Object[]` indexed by `key.ordinal()`, so `get` is a single array index, `containsKey` is an array bounds check, and iteration walks the array in enum order. It also uses **less memory** than a `HashMap` (no `Node` objects, no hash field, no chaining).
- **Expert answer:** The lesson is that the *key type* determines the best data structure, not the generic "key-value store" abstraction. `EnumMap` exploits a guarantee a general map cannot have: a key's `ordinal()` is a dense, small, stable integer in `[0, n)`. Similarly, `EnumSet` is a **bit-vector** — `EnumSet.of(A, C)` for a 64-constant enum is literally one `long` with two bits set, so `contains` is a bit test and the whole set is 8 bytes. When you see "map with enum keys" in a performance-sensitive path, `EnumMap` is the correct answer — and knowing *why* (ordinal-indexed array, no hashing) is what makes it an interview answer rather than a guess.

---

## 10. Rapid-Fire MCQs

**Q1.** What is the default initial capacity of a `HashMap`?
- A) 8  B) 16  C) 32  D) 64

**Q2.** What is the default load factor of a `HashMap`?
- A) 0.5  B) 0.75  C) 0.9  D) 1.0

**Q3.** With default settings, at what `size` does a `HashMap` first resize?
- A) 8  B) 12  C) 16  D) 24

**Q4.** How is the bucket index computed in a `HashMap`?
- A) `hash % table.length`  B) `hash & (table.length - 1)`  C) `hash / table.length`  D) `hash & table.length`

**Q5.** Why must the `HashMap` table length be a power of two?
- A) To save memory  B) So `(n-1) & hash` works as a mask  C) For the GC  D) To speed up `equals`

**Q6.** What does `h ^ (h >>> 16)` accomplish?
- A) Prevents overflow  B) Spreads high hash bits into the low bits  C) Makes the hash reversible  D) Encrypts the key

**Q7.** At how many entries in a bucket does `HashMap` treeify?
- A) 4  B) 6  C) 8  D) 16

**Q8.** At how many entries does a treeified bucket revert to a linked list?
- A) 4  B) 6  C) 8  D) 12

**Q9.** What is `MIN_TREEIFY_CAPACITY` and why does it exist?
- A) 8 — the minimum table size  B) 64 — resize instead of treeifying a small table  C) 16 — default capacity  D) 12 — default threshold

**Q10.** Which collection allows a `null` key?
- A) `HashMap` only  B) `TreeMap` only  C) `ConcurrentHashMap` only  D) None of them

**Q11.** Why does `ConcurrentHashMap` forbid `null` values?
- A) Performance  B) `get()` returning `null` would be ambiguous  C) The GC cannot handle them  D) It is an oversight

**Q12.** How does `ConcurrentHashMap` (Java 8+) lock?
- A) One `ReentrantLock` for the whole table  B) One lock per segment  C) `synchronized` on the head node of the affected bin  D) It does not lock

**Q13.** What is the iteration guarantee of a `ConcurrentHashMap` iterator?
- A) Fail-fast  B) Weakly consistent  C) A perfect snapshot  D) Sorted

**Q14.** `map.entrySet()` returns…
- A) A copy of the entries  B) A view backed by the map  C) An unmodifiable copy  D) A `List`

**Q15.** Which map is backed by an array indexed by `ordinal()`?
- A) `HashMap`  B) `LinkedHashMap`  C) `EnumMap`  D) `TreeMap`

**Q16.** Which map is backed by a bit-vector?
- A) `EnumSet`  B) `HashSet`  C) `LinkedHashSet`  D) `TreeSet`

**Q17.** What is the complexity of `TreeMap.get`?
- A) O(1)  B) O(log n)  C) O(n)  D) O(n log n)

**Q18.** `Hashtable` differs from `HashMap` because it…
- A) Uses a tree  B) Synchronizes every method and rejects nulls  C) Uses `byte[]` keys  D) Is unordered

**Q19.** What happens if you mutate a key's fields after putting it in a `HashMap`?
- A) The map auto-updates  B) The entry becomes unreachable  C) An exception is thrown  D) The map resizes

**Q20.** `LinkedHashMap` with `accessOrder = true` gives you…
- A) Insertion order  B) Access order (LRU)  C) Sorted order  D) Hash order

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | `DEFAULT_CAPACITY = 16` — the table is allocated lazily on the first `put`. |
| 2 | **B** | `DEFAULT_LOAD_FACTOR = 0.75f` — resize when `size > capacity × 0.75`. |
| 3 | **B** | `16 × 0.75 = 12` → the 13th insertion triggers a resize to capacity 32. |
| 4 | **B** | `(n-1) & hash` — `n-1` is a perfect low-bit mask because `n` is a power of two. |
| 5 | **B** | It makes `n-1` an all-ones mask, so the AND replaces the (slower) modulo. |
| 6 | **B** | The bucket index uses only the low bits, so the spread makes them sensitive to the whole hash. |
| 7 | **C** | `TREEIFY_THRESHOLD = 8` — the chain length that triggers `treeifyBin`. |
| 8 | **B** | `UNTREEIFY_THRESHOLD = 6` — below 6, the list is cheaper in memory. |
| 9 | **B** | `MIN_TREEIFY_CAPACITY = 64` — a smaller table is better fixed by resizing than by treeifying. |
| 10 | **A** | `HashMap` allows one `null` key; `TreeMap` and `ConcurrentHashMap` do not. |
| 11 | **B** | `get(k) == null` must unambiguously mean "absent", so `null` values are forbidden. |
| 12 | **C** | CAS for empty bins, `synchronized` on the **bin head node** for occupied ones. |
| 13 | **B** | Weakly consistent — no `ConcurrentModificationException`, but not a point-in-time snapshot. |
| 14 | **B** | It is a *view*: `entry.setValue(v)` writes through to the map. |
| 15 | **C** | `EnumMap` uses an `Object[]` indexed by `key.ordinal()` — no hashing at all. |
| 16 | **A** | `EnumSet` is a bit-vector: a `long` for up to 64 constants. |
| 17 | **B** | A red-black tree gives O(log n) worst case, guaranteed. |
| 18 | **B** | `Hashtable` synchronizes every method and throws `NullPointerException` for null keys/values. |
| 19 | **B** | The map cached the old hash in `Node.hash`, so the entry can no longer be found. |
| 20 | **B** | Access order — a `get()` moves the entry to the head, enabling LRU eviction. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                          MAP SELECTION GUIDE                            │
├──────────────────────┬────────────┬────────────┬───────────┬────────────┤
│ Implementation       │ get/put    │ Ordered?   │ Null key? │ Thread-safe│
├──────────────────────┼────────────┼────────────┼───────────┼────────────┤
│ HashMap              │ O(1) avg   │ ✗          │ ✔ (one)   │ ✗          │
│ LinkedHashMap        │ O(1) avg   │ ✔ ins/acc  │ ✔ (one)   │ ✗          │
│ TreeMap              │ O(log n)   │ ✔ sorted   │ ✗         │ ✗          │
│ Hashtable            │ O(1) avg   │ ✗          │ ✗         │ ✔ (global) │
│ Properties           │ O(1) avg   │ ✗          │ ✗         │ ✔ (global) │
│ WeakHashMap          │ O(1) avg   │ ✗          │ ✔ (one)   │ ✗          │
│ IdentityHashMap      │ O(1) avg   │ ✗          │ ✔         │ ✗          │
│ EnumMap              │ O(1) array │ ✔ enum ord │ ✗         │ ✗          │
│ ConcurrentHashMap    │ O(1)       │ ✗          │ ✗         │ ✔ (per-bin)│
│ ConcurrentSkipListMap│ O(log n)   │ ✔ sorted   │ ✗         │ ✔          │
└──────────────────────┴────────────┴────────────┴───────────┴────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                       HashMap — THE 4 NUMBERS TO REMEMBER                │
├──────────────┬─────────┬────────────────────────────────────────────────┤
│ default cap  │   16    │ table is LAZILY allocated on first put()       │
│ load factor  │  0.75   │ resize when size > cap × 0.75  (12, 24, 48…)   │
│ treeify at   │    8    │ TREEIFY_THRESHOLD                              │
│ untreeify at │    6    │ UNTREEIFY_THRESHOLD                            │
│ min treeify  │   64    │ MIN_TREEIFY_CAPACITY (else resize instead)     │
└──────────────┴─────────┴────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                     THE 3 FORMULAS THAT MATTER                          │
├──────────────────────────────────────────────────────────────────────────┤
│  hash  = h ^ (h >>> 16)          spread the high bits down              │
│  index = (n - 1) & hash           n MUST be a power of two              │
│  split = (hash & oldCap) == 0     low bucket stays, else → j + oldCap   │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                       THREAD-SAFE MAP OPERATIONS                         │
├──────────────────────────────────────────────────────────────────────────┤
│  chm.computeIfAbsent(k, fn)   ── atomic create-if-absent (fn runs once) │
│  chm.merge(k, v, fn)          ── atomic read-modify-write per key       │
│  chm.compute(k, fn)           ── atomic replace per key                 │
│  chm.putIfAbsent(k, v)        ── atomic insert-if-absent                 │
│  chm.replace(k, oldV, newV)   ── atomic compare-and-set on the value   │
│  ❌ get() then put()          ── RACE (check-then-act)                   │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### Sizing correctly

```java
// ✅ Correct sizing for a KNOWN final size
int expected = 100_000;
Map<String, Long> cache = new HashMap<>((int) (expected / 0.75f) + 1);
// → capacity 133,334 → rounded to 262,144? No: tableSizeFor(133334) = 262144?
//    Actually tableSizeFor(133334) = 262144 is NOT right — it returns the next
//    power of two ≥ 133334, which is 262144. Hmm — that is 2× over-allocated.
//
// ✅ The practical rule: pass the *entry count* and let the JDK size it, OR
//    accept the next-power-of-two overshoot. The rule of thumb:
//      new HashMap<>(expected)  →  sized so that NO resize happens up to
//                                  expected*0.75 entries.
```

> ⚠️ **Correct statement of the sizing rule.** `new HashMap<>(initialCapacity)` sets the
> *table size* to the next power of two ≥ `initialCapacity`, and the *threshold* to
> `capacity × 0.75`. So `new HashMap<>(100_000)` gives capacity 131,072 and threshold
> 98,304 — it resizes slightly *before* 100,000 entries. To hold N entries without a
> resize, pass `(int)(N / 0.75f) + 1`. The commonly-quoted one-liner is:
> ```java
> new HashMap<>((int) (expectedSize / 0.75f) + 1)
> ```

### The HashDoS / hash-collision attack

| Aspect | Detail |
|---|---|
| **Attack** | Submit many keys whose `hashCode()` collides. In Java 7 the single bucket became an O(n) chain → O(n²) total → CPU DoS. |
| **Java 8 mitigation** | Treeification caps bucket lookup at O(log n) once the bucket reaches 8 entries. |
| **Residual risk** | Treeification only happens if `table.length >= 64`. A small map stays a plain chain. |
| **Real defences** | Use a **randomised hasher** (e.g. `System.identityHashCode` is not it — use a seeded mix), keep the table large enough to treeify, or use a `TreeMap`/`ConcurrentSkipListMap` for untrusted keys (O(log n) guaranteed, no hash dependency). |

### Which map for which production scenario

| Scenario | Use | Why |
|---|---|---|
| Local, single-threaded lookup table | `HashMap` | Fastest; no synchronisation needed. |
| Need insertion-order iteration for output | `LinkedHashMap` | Deterministic order. |
| Need "top N by key" / range scans | `TreeMap` | Only map with range queries. |
| Shared across threads, read-heavy | `ConcurrentHashMap` | Lock-free reads. |
| Bounded cache in one JVM | `LinkedHashMap` + `removeEldestEntry` | O(1) LRU in 5 lines. |
| Bounded cache at scale | **Caffeine** | W-TinyLFU, better hit rate, async eviction, stats. |
| Enum-keyed lookup in a hot path | `EnumMap` | Array-indexed — no hashing. |
| Small enum-keyed set | `EnumSet` | Bit-vector — a single `long`. |
| Keys may be GC'd, values don't reference them | `WeakHashMap` | Automatic cleanup. |
| Identity is the key | `IdentityHashMap` | Reference equality. |

### Observability

```java
// What to log when a map-based service misbehaves
LOG.info("map size={} threshold={} loadFactor={}", map.size(), threshold, loadFactor);

// ConcurrentHashMap specifics
LOG.info("baseCount={} counterCells={}", chm.baseCount(), chm.counterCells().length);
```

| Symptom | Most likely cause | Where to look |
|---|---|---|
| High CPU, one endpoint slow | Hot bucket / bad `hashCode` | CPU profile → `HashMap.get` |
| Growing heap, map never shrinks | Map holds data it should not | `jcmd GC.class_histogram` |
| `OutOfMemoryError` + a huge map | Unbounded cache | Add a bound + eviction |
| Wrong results, `get` returns null | Mutable key, or broken `equals`/`hashCode` | Audit key classes |
| Lost updates under load | `HashMap` shared across threads | Switch to `ConcurrentHashMap` + atomic ops |

### Benchmarks: do not trust micro-benchmarks

- Always benchmark with **JMH**, never with `System.nanoTime()` in a `main` — the JIT will optimise the loop away.
- Warm up before measuring; the first 10,000 invocations run interpreted or at C1.
- Beware **dead-code elimination** — consume the result.
- `HashMap` vs `ConcurrentHashMap` in a single-threaded benchmark measures the *`volatile` overhead*, not concurrency benefit.
- Real contention needs real threads: use `@Threads(8)` and a `CountDownLatch` to start them together.

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| List/Set/Queue implementations | [01 · List Implementations](01-List-Implementations.md) · [03 · Set, Queue & Utilities](03-Set-Queue-Utilities.md) |
| Why `hashCode`/`equals` must agree | [08 · equals, hashCode & toString](../01-Core-Java-OOP/08-OOP-equals-hashCode-toString.md) |
| Wildcards, PECS, type erasure | [05 · Generics](../05-Generics/01-Generics.md) |
| Concurrent maps, atomic ops, JMM | [08 · Locks & Atomic Classes](../08-Multithreading-Concurrency/03-Locks-Atomic.md) |
| Check-then-act races | [04 · Concurrency Problems](../08-Multithreading-Concurrency/04-Concurrency-Problems.md) |
| `HashMap` in the JDK source | `java.util.HashMap` in the OpenJDK repo — read `hash()`, `putVal()`, `resize()` |
| Modern cache designs | Caffeine's W-TinyLFU admission policy |

---

**Next Chapter:** → [03 · Set, Queue & Utilities](03-Set-Queue-Utilities.md)
**Previous Chapter:** ← [01 · List Implementations](01-List-Implementations.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
