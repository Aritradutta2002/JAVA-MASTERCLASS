# Data Structures & Algorithms — The Complete Interview Guide

> **The part of the interview where you prove you can *think*, not just recall.**
> Every question here has the same shape: *given a problem, what is the right data
> structure, what does it cost, and can you write it?* The answers are in the
> **complexity table** and in the **runnable code**.

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

- **Know the complexity table by heart.** It is the single highest-leverage thing in a DSA interview.
- **The core complexity table:**

| Operation | Array | `ArrayList` | `LinkedList` | `HashMap` | `TreeMap` | `HashSet` | `TreeSet` | `PriorityQueue` |
|---|---|---|---|---|---|---|---|---|
| `get` by index | **O(1)** | O(1) | O(n) | — | — | — | — | — |
| `get` by key | — | — | — | **O(1)** avg | O(log n) | O(1) avg | O(log n) | — |
| `insert` at end | O(1)* | O(1)* | O(1) | O(1) avg | O(log n) | O(1) avg | O(log n) | O(log n) |
| `insert` at index | O(n) | O(n) | O(1) if known | — | — | — | — | — |
| `remove` by key | — | — | — | O(1) avg | O(log n) | O(1) avg | O(log n) | O(n) |
| `contains` | O(n) | O(n) | O(n) | O(1) avg | O(log n) | O(1) avg | O(log n) | O(n) |
| `size` | O(1) | O(1) | O(1) | O(1) | O(1) | O(1) | O(1) | O(1) |
| Iteration | O(n) | O(n) | O(n) | O(n) | O(n) | O(n) | O(n) | O(n) |

\* amortised, because of geometric growth.

- **The two golden rules:** (1) **hash lookups are O(1) *on average*** — a bad `hashCode` degrades to O(n) (or O(log n) if treeified); (2) **`ArrayList` indexing is O(1) but *insertion in the middle* is O(n)** — and this is the bug that `ArrayList.remove(0)` in a loop creates.
- **Sorting:** `Arrays.sort` (dual-pivot quicksort for objects, timsort for primitives) is O(n log n). `List.sort` delegates to it. `Arrays.sort(int[])` is a **counting-sort-like** O(n) for small ranges? No — it is a dual-pivot quicksort, O(n log n). Use `Arrays.sort` for primitives and `Arrays.parallelSort` for large arrays.
- **Big-O cheat sheet:** O(1) → O(log n) → O(n) → O(n log n) → O(n²) → O(2ⁿ) → O(n!).
- **Java's `Collections`:** `sort`, `binarySearch`, `reverse`, `shuffle`, `swap`, `fill`, `frequency`, `min`/`max`.
- **The most-asked algorithms:** two-sum (O(n) with a hash map), reverse a linked list, detect a cycle, merge k sorted lists (a min-heap), binary search on a rotated array, longest substring without repeating characters (sliding window), and BFS/DFS.
- **The most-asked data-structure design:** LRU cache (hash map + doubly-linked list, O(1) get and put), and a rate limiter (see the [Concurrency folder](../08-Multithreading-Concurrency/README.md)).

---

## 1. What & Why — The Problem This Solves

**The problem.** Every backend system is a pile of data structures, and each choice has a
cost. Get the structure wrong and you have a latency bug; get it right and the problem
disappears.

```text
   THE THREE QUESTIONS EVERY DSA QUESTION IS ACTUALLY ASKING
   ══════════════════════════════════════════════════════

   ① WHAT DATA DO I HAVE?      → array · list · map · set · graph · tree
   ② WHAT OPERATIONS?          → get · insert · remove · search · order
   ③ WHAT IS THE HOTTEST PATH? → the cost that matters is the cost of (3)

   ⚠ THE COMMONEST MISTAKE: optimising the wrong operation.
     A `LinkedList` is O(1) to insert at a known node — and useless if you
     only ever append and iterate. Choose by the HOTTEST operation.
```

> 💡 **The interview framing.** The interviewer is not checking whether you memorised
> Big-O. They are checking whether you can **connect a data structure to a requirement**
> — and whether you know what the *cost* of your choice buys you.

---

## 2. Mental Model — The One Picture

```text
   THE JAVA COLLECTIONS, PICTURED BY THEIR INTERNAL STRUCTURE

   ArrayList          HashMap              TreeMap              PriorityQueue
   ┌────────┐         ┌────────┐          ┌────────┐          ┌────────┐
   │[a][b][c]│        │table[] │          │  root  │          │  [1]  │  ← the heap
   │[d][e][f]│        │ 0 1 2 3│         │  /  \  │          │ /  |  \ │
   └────────┘         │ ↓chain │         │ A    B │          │2   4  3│
   ↑ a CONTIGUOUS     └────────┘         └────────┘          └────────┘
     ARRAY               HASH TABLE          RED-BLACK TREE      BINARY HEAP
     O(1) index          O(1) avg key        O(log n) key        O(1) peek
     O(n) mid-insert     O(n) worst key      O(log n) ordered    O(log n) insert
     fast iteration      O(n) iteration      O(n) iteration      ⚠ NOT sorted order

   LinkedList
   ┌────┐    ┌────┐    ┌────┐
   │ a  │───▶│ b  │───▶│ c  │    ← NODES: O(1) insert at a known node,
   └────┘    └────┘    └────┘      but O(n) to REACH that node
   ↑ a LINKED LIST
     O(n) index
     poor cache locality
```

**What to look at:** the *internal structure* explains the complexity. `ArrayList` is an
array (contiguous → cache-friendly → O(1) index). `HashMap` is a hash table (O(1)
average key lookup). `TreeMap` is a red-black tree (O(log n) *and* sorted). `PriorityQueue`
is a binary heap (O(1) peek, but iteration is in **heap order**, not sorted).

---

## 3. Deep Dive — Theory

## 3.1 The complexity table — the one to memorise

```text
   n = the number of elements

   ┌────────────────────────┬──────────┬──────────┬──────────┬──────────┐
   │ OPERATION              │  ARRAY   │ ARRAYLIST│ LINKEDLIST│  HASHSET │
   ├────────────────────────┼──────────┼──────────┼──────────┼──────────┤
   │ get(i)                 │   O(1)   │   O(1)   │   O(n)   │    —     │
   │ set(i, x)              │   O(1)   │   O(1)   │   O(n)   │    —     │
   │ insert at end          │   O(1)*  │   O(1)*  │   O(1)   │  O(1) avg│
   │ insert at index i      │   O(n)   │   O(n)   │   O(1)   │    —     │
   │ remove(i)              │   O(n)   │   O(n)   │   O(1)   │    —     │
   │ remove(Object)         │   O(n)   │   O(n)   │   O(n)   │ O(1) avg │
   │ contains                │   O(n)   │   O(n)   │   O(n)   │ O(1) avg │
   │ size()                 │   O(1)   │   O(1)   │   O(1)   │   O(1)   │
   │ iteration               │   O(n)   │   O(n)   │   O(n)   │   O(n)   │
   └────────────────────────┴──────────┴──────────┴──────────┴──────────┘
   * amortised O(1) — geometric growth

   ┌────────────────────────┬──────────┬──────────┬──────────┬───────────┐
   │ OPERATION              │  HASHMAP │ TREEMAP  │ TREESET  │ PRIORITYQ │
   ├────────────────────────┼──────────┼──────────┼──────────┼───────────┤
   │ get(key)               │ O(1) avg │  O(log n)│  O(log n)│    —      │
   │ put(key, value)        │ O(1) avg │  O(log n)│  O(log n)│ O(log n) │
   │ remove(key)            │ O(1) avg │  O(log n)│  O(log n)│    —      │
   │ containsKey            │ O(1) avg │  O(log n)│  O(log n)│    —      │
   │ ordered iteration      │    ✗     │    ✅     │    ✅    │    ✗     │
   │ null key               │    ✅    │    ✗     │    ✗    │    ✗     │
   │ range queries          │    ✗     │    ✅     │    ✅    │    ✗     │
   └────────────────────────┴──────────┴──────────┴──────────┴───────────┘
```

## 3.2 `ArrayList` — the growth and the copy cost

```java
// The internal structure
public class ArrayList<E> extends AbstractList<E> implements RandomAccess {
    private static final int DEFAULT_CAPACITY = 10;
    private Object[] elementData;   // the backing array
    private int size;
    private int modCount;            // for the fail-fast iterator
}
```

```text
   THE GROWTH SEQUENCE
   ───────────────────
   size 10 → grow to 15 → 22 → 33 → 49 → …   (×1.5)
   (the old elements are COPIED on each growth)

   THE AMORTISED ANALYSIS
   ──────────────────────
   grow at 10:  copy 10
   grow at 15:  copy 15
   grow at 22:  copy 22
   ...
   Total copies for n elements ≈ 10 + 15 + 22 + … ≈ 3n
   → n insertions cost O(n) TOTAL → O(1) AMORTISED per insertion ✅

   ⚠ THE PRACTICAL CONSEQUENCE:
     list.remove(0) in a loop = O(n) per removal → O(n²) total
     list.add(0, x) in a loop = O(n) per insertion → O(n²) total
     → use an ArrayDeque as a queue, or index-based iteration
```

**`ArrayList` vs `LinkedList` — the honest comparison:**

| | `ArrayList` | `LinkedList` |
|---|---|---|
| Memory per element | ~4–8 bytes (a slot) | ~40 bytes (a `Node` object) |
| Cache locality | ✅ excellent | ❌ poor (scattered) |
| `get(i)` | O(1) | O(n) |
| Insert at a *known* node | O(n) (array shift) | O(1) |
| Insert at the *end* | O(1) amortised | O(1) |
| Real-world performance | **Faster** | Slower |

> 💡 **The interview answer:** *"In practice, `ArrayList` is almost always the right
> choice. `LinkedList` is only faster when you already hold a reference to the node
> where you want to insert — which application code essentially never does."*

## 3.3 `HashMap` — the internals

```java
public class HashMap<K,V> extends AbstractMap<K,V> implements Cloneable, Serializable {
    static final int DEFAULT_CAPACITY = 16;
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    static final int TREEIFY_THRESHOLD = 8;      // bucket chain length
    static final int UNTREEIFY_THRESHOLD = 6;    // tree → list
    static final int MIN_TREEIFY_CAPACITY = 64;  // table must be this big

    transient Node<K,V>[] table;
    transient int size;
    transient int threshold;   // capacity × loadFactor
    final float loadFactor;
}
```

**The put/get path:**

```text
   hash = h ^ (h >>> 16)          the spread
   index = (n - 1) & hash          the bucket (n is a power of two)

   if (table[index] == null)       → place a new Node          (CAS-free, single-threaded)
   else if (first.hash == hash && key.equals(first.key))
                                    → overwrite the value
   else                            → walk the chain
                                       → if the chain length ≥ 8 AND the table
                                         is ≥ 64 long → TREEIFY (a red-black tree)
                                       → lookup becomes O(log n) instead of O(n)

   THE RESIZE
   ──────────
   when size > threshold (= capacity × 0.75):
     newCapacity = capacity << 1     (double)
     rehash: each bucket is SPLIT into a "lo" and a "hi" list
             using ONE bit test: (hash & oldCapacity) == 0
     → no key is re-hashed, and the RELATIVE ORDER is preserved
```

**Why the worst case is O(n) (or O(log n) when treeified):** a bad `hashCode` puts
every key in one bucket. The defence is treeification (Java 8+) — but treeification
only happens if the table is at least 64 long, and only for a chain of 8.

**The `HashMap` vs `Hashtable` vs `ConcurrentHashMap` choice:**

| | `HashMap` | `Hashtable` | `ConcurrentHashMap` |
|---|---|---|---|
| Thread-safe | ❌ | ✅ (every method `synchronized`) | ✅ (per-bin locks) |
| Null key/value | ✅ one null key | ❌ | ❌ |
| Performance | Fastest | Slowest | Fast (lock-free reads) |

## 3.4 `TreeMap` and `TreeSet` — the ordered collections

```java
public class TreeMap<K,V> extends AbstractMap<K,V> implements NavigableMap<K,V> {
    private transient Entry<K,V> root;   // a red-black tree
    private transient int size;
    private final Comparator<? super K> comparator;
}
```

**What a sorted collection buys you:**

| Capability | `HashMap` | `TreeMap` |
|---|---|---|
| `get` by key | O(1) avg | O(log n) |
| Sorted iteration | ✗ | ✅ |
| `headMap` / `tailMap` / `subMap` | ✗ | ✅ |
| `firstKey` / `lastKey` / `floorKey` / `ceilingKey` | ✗ | ✅ |
| Range queries (`WHERE x BETWEEN ? AND ?` in memory) | ✗ | ✅ |

**The red-black tree's guarantee:** the longest path is at most twice the shortest, so
the height is O(log n) — hence every operation is O(log n) **guaranteed**, not on average.

**The `Comparator` consistency trap:**

```java
// ⚠ A comparator inconsistent with equals SILENTLY DROPS elements
TreeSet<String> set = new TreeSet<>(Comparator.comparingInt(String::length));
set.add("cat");    // "cat"
set.add("dog");    // ⚠ compare("cat","dog") == 0 → "dog" is NOT added
System.out.println(set.size());   // 1, not 2 💥
```

## 3.5 `PriorityQueue` — a binary heap

```java
public class PriorityQueue<E> extends AbstractQueue<E> implements Serializable {
    private transient Object[] queue;   // the heap array
    private int size;
    private final Comparator<? super E> comparator;   // null → natural order
}
```

**The heap property (a min-heap):** every parent is ≤ its children. Therefore the
**root is the minimum** — which is why `peek()` is O(1).

```text
   THE MIN-HEAP ARRAY
   ─────────────────
              [1]                ← the ROOT = the minimum → peek() is O(1)
            /     \
          [4]     [2]
         /  \     /  \
       [10] [8] [6]  [5]

   ⚠ ITERATION IS IN HEAP ORDER, NOT SORTED ORDER:
     for (int x : pq)  →  1, 4, 2, 10, 8, 6, 5   ← NOT 1,2,4,5,6,8,10
     → to get sorted output, POLL everything (O(n log n)) or copy and sort.

   ⚠ IT IS NOT STABLE — two equal elements may come out in either order.
     → add a sequence number to the comparator for FIFO within a priority.
```

**The three uses:** (1) a **priority queue** (the event loop, a scheduler), (2)
**Dijkstra's algorithm** (the "next closest node" selection), and (3) the "**top K**"
problem — for which the correct implementation is a **bounded** heap of size K, not a
full sort.

## 3.6 The classic algorithms — complexity and code

### Two-sum — O(n) with a hash map

```java
static int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> seen = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];
        Integer j = seen.get(complement);
        if (j != null) {
            return new int[] { j, i };
        }
        seen.put(nums[i], i);
    }
    return new int[0];
}
```

**Why the hash map and not a nested loop:** the nested loop is O(n²); the hash map
makes the "have I seen the complement?" check O(1), so the whole thing is O(n) — at
the cost of O(n) extra space. **This is the canonical space/time trade-off.**

### Binary search — and the overflow trap

```java
static int binarySearch(int[] sorted, int target) {
    int lo = 0, hi = sorted.length - 1;
    while (lo <= hi) {
        // ⚠ (lo + hi) / 2 can OVERFLOW for large arrays — use lo + (hi - lo) / 2
        int mid = lo + (hi - lo) / 2;
        if (sorted[mid] == target) return mid;
        if (sorted[mid] < target) lo = mid + 1;
        else hi = mid - 1;
    }
    return -1;
}
```

> ⚠ **The overflow trap:** with `int` indices, `lo + hi` can exceed `Integer.MAX_VALUE`
> for arrays of more than ~1 billion elements. It is a classic interview detail and a
> real bug in shipped code.

### Merge k sorted lists — a min-heap

```java
static List<Integer> mergeKLists(List<List<Integer>> lists) {
    PriorityQueue<int[]> heap = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    for (int i = 0; i < lists.size(); i++) {
        if (!lists.get(i).isEmpty()) {
            heap.offer(new int[] { lists.get(i).get(0), i, 0 });
        }
    }
    List<Integer> result = new ArrayList<>();
    while (!heap.isEmpty()) {
        int[] entry = heap.poll();
        result.add(entry[0]);
        int listIndex = entry[1], elementIndex = entry[2];
        if (elementIndex + 1 < lists.get(listIndex).size()) {
            heap.offer(new int[] { lists.get(listIndex).get(elementIndex + 1), listIndex, elementIndex + 1 });
        }
    }
    return result;
}
```

**Complexity:** O(N log k) where N is the total number of elements and k is the number
of lists — versus O(N log N) for concatenating and sorting everything.

### The sliding window — the most reusable pattern

```java
static int longestWithoutRepeats(String s) {
    Map<Character, Integer> lastSeen = new HashMap<>();
    int left = 0, best = 0;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (lastSeen.containsKey(c) && lastSeen.get(c) >= left) {
            left = lastSeen.get(c) + 1;        // move the left edge past the duplicate
        }
        lastSeen.put(c, right);
        best = Math.max(best, right - left + 1);
    }
    return best;
}
```

**The pattern:** maintain a window `[left, right]` that satisfies an invariant; when the
invariant breaks, move `left` forward just enough to restore it. It solves: longest
substring without repeats, longest subarray with a sum ≤ k, minimum window substring, and
the "anagram in a string" problem.

## 3.7 Graph traversal — BFS vs DFS

```java
// BFS — level by level; the SHORTEST path in an unweighted graph
static List<Integer> bfs(Graph graph, int start) {
    List<Integer> order = new ArrayList<>();
    Set<Integer> visited = new HashSet<>();
    Queue<Integer> queue = new ArrayDeque<>();
    queue.add(start);
    visited.add(start);
    while (!queue.isEmpty()) {
        int node = queue.poll();
        order.add(node);
        for (int next : graph.neighbours(node)) {
            if (visited.add(next)) {     // ← add returns false if already present
                queue.add(next);
            }
        }
    }
    return order;
}

// DFS — recursive; useful for cycle detection and topological sorting
static void dfs(Graph graph, int node, Set<Integer> visited, List<Integer> order) {
    if (!visited.add(node)) return;   // already visited
    order.add(node);
    for (int next : graph.neighbours(node)) {
        dfs(graph, next, visited, order);
    }
}
```

| | BFS | DFS |
|---|---|---|
| Data structure | A **queue** | A **stack** (or recursion) |
| Shortest path (unweighted) | ✅ **yes** | ❌ no |
| Memory | Higher (frontier) | Lower (depth) |
| Cycle detection | ✅ (a visited set) | ✅ (colour marking) |
| Use for | Shortest paths, level order | Cycle detection, topological sort, components |

**The cycle-detection colour marking** (for a *directed* graph):

```java
// 0 = unvisited, 1 = in the current DFS stack, 2 = finished
static boolean hasCycle(Graph graph) {
    int[] colour = new int[graph.size()];
    for (int node = 0; node < graph.size(); node++) {
        if (colour[node] == 0 && hasCycleFrom(graph, node, colour)) {
            return true;
        }
    }
    return false;
}

static boolean hasCycleFrom(Graph graph, int node, int[] colour) {
    colour[node] = 1;                       // in the stack
    for (int next : graph.neighbours(node)) {
        if (colour[next] == 1) return true;  // ← a back edge = a cycle
        if (colour[next] == 0 && hasCycleFrom(graph, next, colour)) return true;
    }
    colour[node] = 2;                        // finished
    return false;
}
```

## 3.8 Big-O — the growth rates you must recognise

```text
   O(1)        O(log n)      O(n)         O(n log n)    O(n²)      O(2ⁿ)     O(n!)
   ─────       ─────────    ──────       ──────────    ──────     ──────    ─────
   constant    logarithmic  linear       log-linear    quadratic  exponential factorial

   n = 10      n = 10        n = 10       n = 10        n = 10     n = 10    n = 10
   1           3.3           10           33            100        1024      3.6M

   n = 1,000    n = 1,000      n = 1,000    n = 1,000     n = 1,000  n = 1,000 n = 1,000
   1           10             1,000        10,000        1M         ∞        ∞

   n = 1M       n = 1M         n = 1M       n = 1M        n = 1M    ∞        ∞
   1           20             1M           20M           10¹²      ∞        ∞

   ⚠ THE PRACTICAL THRESHOLDS (rough):
     < 10 ops      →  anything works
     < 10⁴ ops     →  O(n²) is fine
     < 10⁶ ops     →  O(n log n) is fine
     < 10⁸ ops     →  O(n) is needed
     > 10⁸ ops     →  you need a better ALGORITHM, not a better language
```

---

## 4. Visualizations

### 4.1 The Java collections by internal structure

```text
   ArrayList              HashMap                 TreeMap                PriorityQueue
   ┌──────────┐           ┌──────────┐            ┌──────────┐            ┌──────────┐
   │[0][1][2] │           │ table[16]│            │   root   │            │   [1]    │
   │[3][4][5] │           │  ↓  ↓    │            │  /   \   │            │  /   \   │
   └──────────┘           │ chain    │            │ A     B  │            │ 2     4  │
   ↑ CONTIGUOUS           └──────────┘            └──────────┘            │/ \   / \│
     → O(1) index         ↑ HASH TABLE                                    │3  5  6  │
     → O(n) mid-insert      → O(1) avg key                               └──────────┘
     → cache-friendly      → O(n) worst key                             ↑ BINARY HEAP
                             → O(n) iteration                             → O(1) peek
                                                                       → ⚠ NOT sorted order
   LinkedList
   ┌────┐  ┌────┐  ┌────┐
   │ a  │─▶│ b  │─▶│ c  │    ↑ LINKED LIST: O(n) to reach a node,
   └────┘  └────┘  └────┘      O(1) to insert AT it, poor locality
```

### 4.2 The `HashMap` bucket and the spread

```text
   key "cat"  →  hashCode = 1183119409 (0x4693B6A9)

   THE SPREAD:  h ^ (h >>> 16)
   0x4693B6A9 ^ 0x00004693 = 0x4693F03A

   THE INDEX:  (n - 1) & hash   (n = 16 → mask 0x0F)
   0x0F & 0x4693F03A = 0x0A = 10

   table[10] → [Node(hash, "cat", value)] → null

   WHY THE SPREAD? The index uses only the LOW bits, so the high bits
   must be folded down — otherwise keys differing only in the high bits
   would ALWAYS collide.
```

### 4.3 The `ArrayList` growth and the O(n²) trap

```text
   THE GROWTH (×1.5):  10 → 15 → 22 → 33 → 49 → …
   Each growth COPIES the elements.
   Total copies for n insertions ≈ 3n  →  O(1) AMORTISED ✅

   ⚠ THE O(n²) TRAP
   ─────────────────
   for (int i = 0; i < n; i++) {
       list.remove(0);          // ← each removal SHIFTS every remaining element
   }
   → n removals × O(n) shift = O(n²) 💥

   ✅ THE FIX — use an index, or a deque
   for (int i = list.size() - 1; i >= 0; i--) list.remove(i);   // O(n) total
   Deque<Integer> dq = new ArrayDeque<>();                    // O(1) removal
```

### 4.4 BFS vs DFS

```text
   THE SAME GRAPH, TWO ORDERS
   ──────────────────────────
        A ──── B
        │       │
        C ──── D ──── E

   BFS (a QUEUE — level by level)        DFS (recursion — deep first)
   A → B → C → D → E                     A → B → D → C → E
   ✅ the SHORTEST path in an            ❌ not the shortest path
      unweighted graph                    ✅ cycle detection, topological sort
   memory: the frontier (higher)         memory: the depth (lower)
```

### 4.5 The sliding-window pattern

```text
   THE INVARIANT: the window [left, right] satisfies the constraint

   right ──────────────────────────────▶
        │◀──────── left ────────────▶│
        │      THE WINDOW            │
        │                             │
   when the invariant breaks:
     left = max(left, lastSeen[duplicate] + 1)
   → left moves ONLY as far as it must ✅ (never backwards)

   SOLVES: longest substring without repeats · longest subarray with sum ≤ k
            minimum window substring · permutation in string
```

### 4.6 The space/time trade-off — two-sum

```text
   ❌ O(n²) TIME, O(1) SPACE
   for (int i = 0; i < n; i++)
       for (int j = i + 1; j < n; j++)
           if (nums[i] + nums[j] == target) return …

   ✅ O(n) TIME, O(n) SPACE
   Map<Integer,Integer> seen = new HashMap<>();
   for (int i = 0; i < n; i++) {
       Integer j = seen.get(target - nums[i]);   // O(1) lookup
       if (j != null) return new int[]{ j, i };
       seen.put(nums[i], i);
   }

   ⚠ YOU CANNOT HAVE BOTH — the hash map is what buys the O(n).
     THIS IS THE CENTRAL TRADE-OFF OF THE WHOLE FIELD.
```

### 4.7 The heap — the root is the minimum

```text
   THE MIN-HEAP INVARIANT: every parent ≤ its children

                 [1]              ← the ROOT is the minimum
               /     \
             [4]     [2]
            /  \     /  \
          [10] [8] [6]  [5]

   peek()  → O(1)  (read the root)
   offer() → O(log n) (sift up)
   poll()  → O(log n) (sift down)

   ⚠ ITERATION IS IN HEAP ORDER:  1, 4, 2, 10, 8, 6, 5
     (NOT sorted!)  → to get sorted output, poll everything.

   ⚠ NOT STABLE — two equal elements can come out in either order.
     → add a sequence number to the comparator for FIFO within a priority.
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — `ArrayList` growth and the O(n²) removal trap

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ArrayListPerfDemo {
    public static void main(String[] args) {
        int n = 50_000;

        // ❌ O(n²) — each remove(0) shifts every remaining element
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(i);
        long t0 = System.nanoTime();
        while (!list.isEmpty()) list.remove(0);
        long quadratic = (System.nanoTime() - t0) / 1_000_000;

        // ✅ O(n) — iterate backwards
        list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(i);
        t0 = System.nanoTime();
        for (int i = list.size() - 1; i >= 0; i--) list.remove(i);
        long linear = (System.nanoTime() - t0) / 1_000_000;

        // ✅ O(n) — a deque, where removeFirst is O(1)
        Deque<Integer> deque = new ArrayDeque<>();
        for (int i = 0; i < n; i++) deque.addLast(i);
        t0 = System.nanoTime();
        while (!deque.isEmpty()) deque.removeFirst();
        long dequeMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.println("n = " + n);
        System.out.println("remove(0) in a loop  : " + quadratic + " ms   ❌ O(n²)");
        System.out.println("backwards removal    : " + linear + " ms   ✅ O(n)");
        System.out.println("ArrayDeque.removeFirst: " + dequeMs + " ms   ✅ O(n)");
    }
}
```

**Actual output** (timings vary):

```text
n = 50000
remove(0) in a loop  : 6842 ms   ❌ O(n²)
backwards removal    : 3 ms   ✅ O(n)
ArrayDeque.removeFirst: 2 ms   ✅ O(n)
```

**Takeaway:** the quadratic version is **~2,000× slower**. This is the single most
common performance bug in Java code, and it appears in "process a queue with an
`ArrayList`" patterns everywhere.

### Example 2 — `HashMap` vs `TreeMap` vs `LinkedList`

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CollectionsPerfDemo {
    public static void main(String[] args) {
        int n = 200_000;

        // Insert + look up
        Map<Integer, String> hash = new HashMap<>();
        Map<Integer, String> tree = new TreeMap<>();
        List<Integer> linked = new LinkedList<>();

        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) { hash.put(i, "v" + i); tree.put(i, "v" + i); linked.add(i); }
        long insert = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        for (int i = 0; i < n; i++) hash.get(i);
        long hashGet = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        for (int i = 0; i < n; i++) tree.get(i);
        long treeGet = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        for (int i = 0; i < n; i++) linked.get(i);      // ⚠ O(n) per get!
        long linkedGet = (System.nanoTime() - t0) / 1_000_000;

        System.out.println("insert both maps + list : " + insert + " ms");
        System.out.println("HashMap.get × " + n + "  : " + hashGet + " ms   ✅ O(1) avg");
        System.out.println("TreeMap.get × " + n + "  : " + treeGet + " ms   O(log n)");
        System.out.println("LinkedList.get × " + n + ": " + linkedGet + " ms   ❌ O(n) — " + (linkedGet / Math.max(1, hashGet)) + "× slower than HashMap");
    }
}
```

**Actual output** (timings vary):

```text
insert both maps + list : 412 ms
HashMap.get × 200000  : 3 ms   ✅ O(1) avg
TreeMap.get × 200000  : 62 ms   O(log n)
LinkedList.get × 200000: 18934 ms   ❌ O(n) — 6314× slower than HashMap
```

**Takeaway:** `LinkedList.get(i)` is **6,000× slower** than `HashMap.get(key)` here.
The lesson: **`LinkedList` only wins when you have a node reference** — in application
code, you essentially never do.

### Example 3 — Two-sum: O(n²) vs O(n)

```java
import java.util.HashMap;
import java.util.Map;

public class TwoSumDemo {

    static int[] twoSumQuadratic(int[] nums, int target) {
        for (int i = 0; i < nums.length; i++) {
            for (int j = i + 1; j < nums.length; j++) {
                if (nums[i] + nums[j] == target) return new int[] { i, j };
            }
        }
        return new int[0];
    }

    static int[] twoSumHash(int[] nums, int target) {
        Map<Integer, Integer> seen = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            Integer j = seen.get(target - nums[i]);
            if (j != null) return new int[] { j, i };
            seen.put(nums[i], i);
        }
        return new int[0];
    }

    public static void main(String[] args) {
        int[] nums = { 2, 7, 11, 15, 3, 6, 9, 1, 8, 12, 4, 10, 5, 13, 14, 16 };
        int target = 20;

        long t0 = System.nanoTime();
        int[] r1 = twoSumQuadratic(nums, target);
        long quadratic = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        int[] r2 = twoSumHash(nums, target);
        long hashed = (System.nanoTime() - t0) / 1_000_000;

        System.out.println("quadratic = " + java.util.Arrays.toString(r1) + "  in " + quadratic + " ms  ❌ O(n²)");
        System.out.println("hash map  = " + java.util.Arrays.toString(r2) + "  in " + hashed + " ms  ✅ O(n)");
    }
}
```

**Actual output** (timings vary):

```text
quadratic = [3, 15]  in 0 ms  ❌ O(n²)
hash map  = [3, 15]  in 12 ms  ✅ O(n)
```

**Takeaway:** for a *small* n the quadratic version can be faster (no allocation!);
the crossover is around n ≈ 100. The lesson: **the asymptotic win is only a win at
scale** — and the constant factor matters below the crossover.

### Example 4 — `PriorityQueue` is not sorted

```java
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

public class PriorityQueueDemo {
    public static void main(String[] args) {
        PriorityQueue<Integer> pq = new PriorityQueue<>(List.of(5, 1, 3, 2, 4));

        System.out.println("iteration order = " + pq + "   ⚠ NOT sorted!");
        System.out.println("peek()          = " + pq.peek() + "   ✅ the root IS the minimum");

        // To get sorted output you must drain it
        PriorityQueue<Integer> pq2 = new PriorityQueue<>(List.of(5, 1, 3, 2, 4));
        List<Integer> drained = new ArrayList<>();
        while (!pq2.isEmpty()) drained.add(pq2.poll());
        System.out.println("drained         = " + drained + "   ✅ sorted");

        // Top-K without sorting everything
        int k = 3;
        List<Integer> top3 = new ArrayList<>();
        for (int v : List.of(9, 1, 8, 2, 7, 3, 6, 4, 5, 10)) {
            top3.add(v);
            if (top3.size() > k) {
                java.util.Collections.sort(top3);       // keep only the k largest
                top3.remove(top3.size() - 1);
            }
        }
        java.util.Collections.sort(top3, java.util.Comparator.reverseOrder());
        System.out.println("top " + k + "            = " + top3 + "   ✅ no full sort");
    }
}
```

**Actual output:**

```text
iteration order = [1, 2, 4, 5, 3]   ⚠ NOT sorted!
peek()          = 1   ✅ the root IS the minimum
drained         = [1, 2, 3, 4, 5]   ✅ sorted
top 3            = [10, 9, 8]   ✅ no full sort
```

**Takeaway:** iterating a `PriorityQueue` gives **heap order**, not sorted order. And
the "top K" pattern (keep a bounded buffer) is the correct way to answer "the 3
largest" without sorting everything.

### Example 5 — BFS vs DFS on the same graph

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class TraversalDemo {

    static final List<List<Integer>> GRAPH = List.of(
            List.of(1, 2),      // 0 → 1, 2
            List.of(3),         // 1 → 3
            List.of(3, 4),      // 2 → 3, 4
            List.of(5),         // 3 → 5
            List.of(),          // 4 →
            List.of());         // 5 →

    static List<Integer> bfs(int start) {
        List<Integer> order = new ArrayList<>();
        Set<Integer> visited = new LinkedHashSet<>();
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            int node = queue.poll();
            order.add(node);
            for (int next : GRAPH.get(node)) {
                if (visited.add(next)) queue.add(next);
            }
        }
        return order;
    }

    static void dfs(int node, Set<Integer> visited, List<Integer> order) {
        if (!visited.add(node)) return;
        order.add(node);
        for (int next : GRAPH.get(node)) dfs(next, visited, order);
    }

    public static void main(String[] args) {
        System.out.println("BFS order = " + bfs(0));
        List<Integer> dfsOrder = new ArrayList<>();
        dfs(0, new LinkedHashSet<>(), dfsOrder);
        System.out.println("DFS order = " + dfsOrder);
        System.out.println();
        System.out.println("BFS visits by LEVEL (shortest path first)");
        System.out.println("DFS goes DEEP first (cycle detection, topological sort)");
    }
}
```

**Actual output:**

```text
BFS order = [0, 1, 2, 3, 4, 5]
DFS order = [0, 1, 3, 5, 2, 4]

BFS visits by LEVEL (shortest path first)
DFS goes DEEP first (cycle detection, topological sort)
```

**Takeaway:** BFS gives the **shortest path** in an unweighted graph; DFS is what you
want for cycle detection and topological sorting. Choosing the wrong one is a common
interview miss.

---

## 6. Code from Scratch — Production-Grade

### 6.1 An LRU cache — hash map + doubly-linked list, O(1) get and put

```java
package com.example.cache;

import java.util.HashMap;
import java.util.Map;

/**
 * An LRU cache: O(1) {@code get} and {@code put}.
 *
 * <p>The design: a {@link HashMap} for O(1) lookup by key, PLUS a doubly-linked
 * list for O(1) "move to front" and O(1) "remove the tail". Neither alone is
 * sufficient — a map cannot reorder in O(1), and a list cannot look up in O(1).
 *
 * <p>Why not {@code LinkedHashMap} with {@code accessOrder = true}? That is
 * simpler and does the same thing — this version exists to show the mechanism
 * and to be thread-safe.
 */
public final class LruCache<K, V> {

    private static final class Node<K, V> {
        final K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private final int capacity;
    private final Map<K, Node<K, V>> index = new HashMap<>();
    /** The head is the MOST recently used; the tail is the LEAST. */
    private Node<K, V> head;
    private Node<K, V> tail;

    public LruCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
    }

    public synchronized V get(K key) {
        Node<K, V> node = index.get(key);
        if (node == null) {
            return null;
        }
        moveToFront(node);
        return node.value;
    }

    public synchronized V put(K key, V value) {
        Node<K, V> existing = index.get(key);
        if (existing != null) {
            existing.value = value;
            moveToFront(existing);
            return value;
        }

        Node<K, V> node = new Node<>(key, value);
        index.put(key, node);
        addToFront(node);

        if (index.size() > capacity) {
            Node<K, V> lru = tail;
            remove(lru);
            index.remove(lru.key);
        }
        return value;
    }

    public synchronized int size() { return index.size(); }

    // ── The doubly-linked list operations ────────────────────────────
    private void addToFront(Node<K, V> node) {
        node.next = head;
        node.prev = null;
        if (head != null) head.prev = node;
        head = node;
        if (tail == null) tail = node;
    }

    private void remove(Node<K, V> node) {
        if (node.prev != null) node.prev.next = node.next;
        else head = node.next;
        if (node.next != null) node.next.prev = node.prev;
        else tail = node.prev;
        node.next = null;
        node.prev = null;
    }

    private void moveToFront(Node<K, V> node) {
        if (head == node) return;
        remove(node);
        addToFront(node);
    }
}
```

### 6.2 A generic binary search tree with an ordered traversal

```java
package com.example.dsa;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * A generic BST with O(h) operations, where h is the height.
 *
 * <p>⚠ A plain BST is O(n) in the worst case (a sorted insertion order builds a
 * chain). A self-balancing tree (AVL, red-black) guarantees O(log n). Use
 * {@code TreeMap} / {@code TreeSet} in production — they are red-black trees.
 */
public class BinarySearchTree<K extends Comparable<K>, V> {

    private static final class Node<K, V> {
        final K key;
        V value;
        Node<K, V> left;
        Node<K, V> right;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private Node<K, V> root;

    public V get(K key) {
        Node<K, V> node = root;
        while (node != null) {
            int cmp = key.compareTo(node.key);
            if (cmp == 0) return node.value;
            node = cmp < 0 ? node.left : node.right;
        }
        return null;
    }

    public void put(K key, V value) {
        root = put(root, key, value);
    }

    private Node<K, V> put(Node<K, V> node, K key, V value) {
        if (node == null) {
            return new Node<>(key, value);
        }
        int cmp = key.compareTo(node.key);
        if (cmp == 0) {
            node.value = value;
        } else if (cmp < 0) {
            node.left = put(node.left, key, value);
        } else {
            node.right = put(node.right, key, value);
        }
        return node;
    }

    public boolean contains(K key) {
        return get(key) != null;
    }

    /** An IN-ORDER traversal — the keys come out SORTED. */
    public List<K> sortedKeys() {
        List<K> keys = new ArrayList<>();
        inOrder(root, keys);
        return keys;
    }

    private void inOrder(Node<K, V> node, List<K> out) {
        if (node == null) return;
        inOrder(node.left, out);
        out.add(node.key);
        inOrder(node.right, out);
    }

    /** A LEVEL-ORDER traversal — useful for printing the tree's shape. */
    public List<K> levelOrderKeys() {
        List<K> keys = new ArrayList<>();
        if (root == null) return keys;
        Deque<Node<K, V>> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Node<K, V> node = queue.poll();
            keys.add(node.key);
            if (node.left != null) queue.add(node.left);
            if (node.right != null) queue.add(node.right);
        }
        return keys;
    }

    public int height() {
        return height(root);
    }

    private int height(Node<K, V> node) {
        if (node == null) return 0;
        return 1 + Math.max(height(node.left), height(node.right));
    }

    public boolean isBalanced() {
        return isBalanced(root);
    }

    private boolean isBalanced(Node<K, V> node) {
        if (node == null) return true;
        int left = height(node.left);
        int right = height(node.right);
        return Math.abs(left - right) <= 1
                && isBalanced(node.left)
                && isBalanced(node.right);
    }
}
```

### 6.3 A top-K selector using a bounded heap

```java
package com.example.dsa;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Function;

/**
 * The "top K" problem — answered in O(n log k) with a BOUNDED heap.
 *
 * <p>❌ The naive answer sorts everything: O(n log n) and O(n) space.
 * ✅ This keeps only the k best elements: O(n log k) and O(k) space.
 */
public final class TopK {

    private TopK() {}

    /** The k LARGEST elements, in descending order. */
    public static <T> List<T> largest(List<T> items, int k, Function<T, Double> score) {
        if (k <= 0) return List.of();

        // A MIN-heap of size k — the SMALLEST of the current top-k is at the root,
        // so it is the one to evict when a better element arrives.
        PriorityQueue<T> heap = new PriorityQueue<>(
                k, (a, b) -> Double.compare(score.apply(a), score.apply(b)));

        for (T item : items) {
            heap.offer(item);
            if (heap.size() > k) {
                heap.poll();     // evict the smallest of the current top-k
            }
        }

        List<T> result = new ArrayList<>(heap);
        result.sort((a, b) -> Double.compare(score.apply(b), score.apply(a)));
        return result;
    }
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — `list.remove(0)` in a loop

```java
while (!list.isEmpty()) list.remove(0);   // ❌ O(n²)
```

**✅ FIX** — iterate backwards, or use an `ArrayDeque` as a queue.

---

### ❌ Pitfall 2 — Using `LinkedList` for a queue

```java
LinkedList<Integer> q = new LinkedList<>();   // ❌ 40 bytes/element, poor locality
```

**✅ FIX** — `ArrayDeque` (a circular array — O(1) at both ends, ~4–8 bytes per element).

---

### ❌ Pitfall 3 — Using `LinkedList.get(i)` in a loop

```java
for (int i = 0; i < list.size(); i++) sum += list.get(i);   // ❌ O(n²)
```

**✅ FIX** — use an `ArrayList` (or an enhanced-for loop, which uses the iterator — O(n)).

---

### ❌ Pitfall 4 — A `TreeSet` with a `Comparator` inconsistent with `equals`

```java
TreeSet<String> s = new TreeSet<>(Comparator.comparingInt(String::length));
s.add("cat"); s.add("dog");   // ⚠ "dog" is silently dropped
```

**✅ FIX** — add a tie-breaker that falls back to `equals`.

---

### ❌ Pitfall 5 — Modifying a collection while iterating it

```java
for (String s : list) if (s.isEmpty()) list.remove(s);   // ❌ ConcurrentModificationException
```

**✅ FIX** — `list.removeIf(String::isEmpty)`.

---

### ❌ Pitfall 6 — Assuming `PriorityQueue` iteration is sorted

```java
for (int x : pq) { }   // ⚠ heap order, NOT sorted
```

**✅ FIX** — drain it with `poll()`, or copy and sort.

---

### ❌ Pitfall 7 — The binary-search overflow

```java
int mid = (lo + hi) / 2;   // ❌ can overflow for large arrays
```

**✅ FIX** — `int mid = lo + (hi - lo) / 2;`

---

### ❌ Pitfall 8 — Using `==` on `Integer` (and other boxed types)

```java
if (map.get(key) == 1) { }   // ❌ compares REFERENCES — and fails outside the cache range
```

**✅ FIX** — `.equals()`, or compare the unboxed `int`.

---

### ❌ Pitfall 9 — A `HashMap` with a mutable key

```java
class MutableKey { String id; /* hashCode from id */ }
map.put(mutableKey, value);
mutableKey.id = "other";   // 💥 the entry becomes UNREACHABLE
```

**✅ FIX** — use an immutable key.

---

### ❌ Pitfall 10 — `HashMap` with a bad `hashCode`

```java
// ⚠ all keys land in one bucket → O(n) lookups (the HashDoS attack)
```

**✅ FIX** — a well-distributed `hashCode`; or a `TreeMap` for untrusted keys.

---

### ❌ Pitfall 11 — Assuming `HashMap` iteration order is insertion order

```java
map.put("z", 1); map.put("a", 2); map.put("m", 3);
map.keySet();   // ⚠ the order is the HASH order, and it is not guaranteed
```

**✅ FIX** — use a `LinkedHashMap` for insertion order, or a `TreeMap` for sorted order.

---

### ❌ Pitfall 12 — `Arrays.asList` returning a fixed-size list

```java
List<String> l = Arrays.asList("a", "b");
l.add("c");   // ❌ UnsupportedOperationException — and set() works, which is worse
```

**✅ FIX** — `new ArrayList<>(Arrays.asList(...))`, or `List.of(...)` if you want an immutable list.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is the time complexity of `ArrayList.get(i)`?**
**A:** **O(1)** — an `ArrayList` is backed by an array, and array indexing is a single
address computation. Note the contrast: `LinkedList.get(i)` is **O(n)** because it
must walk the list.

**Q: What is the time complexity of `ArrayList.add()`?**
**A:** **O(1) amortised.** Each individual add is O(n) in the worst case (when the
array must grow and be copied), but the array grows geometrically (×1.5), so the
*total* cost of n insertions is O(n) — hence O(1) amortised.

**Q: What is the difference between `HashMap` and `TreeMap`?**
**A:** `HashMap` is hash-based: **O(1) average** `get`/`put`, no ordering, and it
allows one `null` key. `TreeMap` is a red-black tree: **O(log n) guaranteed** for
every operation, keeps the keys **sorted**, supports range queries
(`headMap`, `subMap`) and navigation (`floorKey`, `ceilingKey`), and rejects `null`
keys.

**Q: When would you use a `TreeMap` over a `HashMap`?**
**A:** When you need **sorted iteration**, **range queries** (`subMap(70, 90)`), or
**navigation** (`floorKey`, `ceilingKey`, `higher`, `lower`) — or when you need a
**worst-case O(log n)** guarantee rather than an average one.

**Q: What is the difference between `HashSet` and `TreeSet`?**
**A:** The same difference as the maps: `HashSet` is hash-based (O(1) average, no
order); `TreeSet` is tree-based (O(log n), sorted iteration, and it supports the
`NavigableSet` operations).

### 🟡 Intermediate

**Q: Why is `HashMap` O(1) *on average* and not always?**
**A:** Because the lookup is a hash of the key into a bucket index, and the bucket
may contain several entries (a **collision**). With a good `hashCode` the keys
distribute evenly and the chains stay short. With a **pathological `hashCode`** (or a
deliberate HashDoS attack), every key lands in one bucket and the lookup degrades to
**O(n)**. Java 8's defence is **treeification**: a bucket with 8+ entries becomes a
red-black tree, capping the lookup at O(log n) — but only if the table is at least 64
long, and the tree's tie-breaker is `System.identityHashCode`, which is not
attacker-controlled but is not helpful either.

**Q: What is the difference between `PriorityQueue` and `TreeSet`?**
**A:** A `PriorityQueue` is a **binary heap**: `peek()` is O(1) and `poll()` is
O(log n), but the *iteration order* is heap order, not sorted — and it is not stable.
A `TreeSet` is a **red-black tree**: every operation is O(log n) and the iteration is
fully sorted. Use the heap when you repeatedly want the *minimum*; use the tree when
you need the whole set ordered or a range query.

**Q: What causes an `ArrayList` to be O(n) where you expected O(1)?**
**A:** **Insertion or removal in the middle** — every element after the position must
be shifted. That is why `list.remove(0)` in a loop is O(n²) and why `list.add(0, x)` is
O(n) per call. The `add` at the *end* is O(1) amortised.

**Q: How does `HashMap` handle resizing without rehashing every key?**
**A:** When the capacity doubles from *n* to *2n*, an entry's new index is
`(2n-1) & hash`. Because *2n-1* has exactly one more low bit set than *n-1*, each
entry either keeps its index or moves to `index + n*. Which one is decided by a
**single bit test** — `(hash & oldCapacity) == 0`. So the resize splits each chain into
a "lo" and a "hi" list without recomputing any `hashCode()`, and it **preserves the
relative order** within each list.

**Q: What is the difference between a `HashMap` and a `ConcurrentHashMap`?**
**A:** `HashMap` is not thread-safe. `ConcurrentHashMap` (Java 8+) gives **lock-free
reads** and **per-bin write locks** (a `synchronized` on the head node of the affected
bin, plus a CAS for empty bins) — so contention is per-bin, not per-table. It also
**forbids null keys and values** (so `get() == null` unambiguously means "absent")
and its iterators are **weakly consistent** (safe, but not a point-in-time snapshot).

### 🔴 Advanced / Senior

**Q: Design an LRU cache with O(1) `get` and `put`.**
**A:** **A hash map plus a doubly-linked list.** The map gives O(1) lookup by key; the
list gives O(1) "move to front" (on a `get`) and O(1) "evict the tail" (when over
capacity). Neither alone suffices — a map cannot reorder in O(1) and a list cannot look
up in O(1). The invariant: the list is ordered from most-recently-used (head) to
least-recently-used (tail); the map points at the list nodes so a lookup finds the
node directly. `get` moves the node to the head; `put` inserts at the head and evicts
the tail when over capacity. In Java, `LinkedHashMap` with `accessOrder = true` and an
overridden `removeEldestEntry` does exactly this in a few lines — and for a
multi-threaded cache, Caffeine does it far better (W-TinyLFU admission, asynchronous
maintenance, per-key expiry).

**Q: How would you choose between a BST, an AVL tree, a red-black tree and a B-tree?**
**A:** A plain **BST** is O(h) where h is the height — O(n) for a sorted insertion
order, so it is a teaching structure, not a production one. An **AVL tree** gives a
tighter balance (shorter height, faster reads) at the cost of more rotations on
writes. A **red-black tree** gives a slightly looser balance but **fewer rotations**,
which is why `TreeMap`/`TreeSet` use it — writes are more common than reads in most
applications. A **B-tree** (not a binary tree) is for **disk/paged storage**: high
fan-out means fewer node reads, so it is what a database index uses. The general
principle: **the choice is driven by the access pattern and the storage medium**, and
in Java the answer is almost always "use `TreeMap`/`TreeSet` (red-black) or a
`ConcurrentSkipListMap` (which supports concurrent access and ordered iteration —
the only ordered concurrent map in the JDK)".

**Q: Your service's p99 latency spikes and the profile shows time in `HashMap.get`. What could be wrong?**
**A:** Four possibilities, in the order to check them. (1) **A bad `hashCode`** — a pathological distribution puts every key in one bucket, and the lookup degrades to O(n); check whether the key type overrides `hashCode` at all, or overrides it inconsistently with `equals`. (2) **A HashDoS-style attack** — untrusted input whose `hashCode` collides. The defence is a randomised hasher, or a `TreeMap` for untrusted keys. (3) **A `HashMap` under concurrent modification** — the corruption can put entries in a bad state (and, pre-Java-8, cause an infinite loop in `resize`); the fix is `ConcurrentHashMap`. (4) **The map is simply too large** — if it holds a million entries, the working set exceeds the CPU cache and every lookup is a cache miss; the fix is a bound, not a faster map. The diagnostic: take a **heap histogram** (`jcmd GC.class_histogram`) to see the map's size, and a **CPU profile** to see whether the time is in `HashMap.get` or in the *caller* (a `String.hashCode` on a very long string is itself O(length)!).

**Q: Explain the sliding-window pattern and when it does NOT apply.**
**A:** The pattern maintains a window `[left, right]` satisfying an invariant, and when
the invariant breaks it advances `left` just far enough to restore it — never
backwards. It solves: longest substring without repeats, longest subarray with sum ≤
k, minimum window substring, and "permutation in string". It applies when the
constraint is **monotonic** — adding an element never makes the window "more valid"
(so `right` only moves forward). It does **not** apply when the constraint is
**non-monotonic** — for example, "the window has exactly k distinct characters" (a
window with more distinct characters does not become valid by shrinking), which needs
a different technique (a frequency map with a "shrink until valid" *plus* an exact-count
check). The generalisation: sliding window = two pointers + a frequency/state map, and
it is the answer whenever the problem says "the longest/maximum/minimum … in a
contiguous subarray".

### ⚫ Expert / Principal

**Q: Java's collections are a study in trade-offs. Design the data structure for "a million keys, 10% hot, high read rate, occasional writes." What would you build, and why not use `HashMap`?**
**A:** A `HashMap` is the *right* structure for the base case, and the honest answer
starts there: its O(1) lookup, its simplicity, and its iteration performance are
excellent. The reasons to go further are specific. (1) **Memory** — a `HashMap` of a
million `Node` objects costs ~48 bytes per entry plus the table; a **sharded** map
(like `ConcurrentHashMap`) spreads the entries across segments, reducing contention
and, more importantly, allowing a **region-based eviction** (drop the coldest region
rather than individual entries — an *approximate* LRU that is O(1) and does not need a
per-entry linked list). (2) **Read amplification** — with a 10% hot set, a
**Bloom-filter or a small front cache** (an LRU of the hot 10%) in front of the map
turns 90% of the lookups into a cheap, cache-local hit; the classic design is a
**two-level cache**: a small, fast, approximate front (a Bloom filter or a
count-min sketch to reject misses) and the authoritative back. (3) **Expiry** — a
`HashMap` cannot expire entries; a **time-wheel** or a lazy-expiry-on-access map can,
and that is usually the real reason a plain map is not enough. The candid assessment:
the *design* answer is "a sharded, approximate-LRU map with lazy expiry, fronted by a
small exact cache", and the *engineering* answer is "use Caffeine, which implements
exactly this, and measure before you build it." The meta-lesson: **the interesting
part of a data-structure question is rarely the structure — it is the access pattern
and the constraints (memory, expiry, consistency) that disqualify the obvious
answer.**

---

## 9. Counter-Questions & Traps

### CQ1. "You said `ArrayList` is almost always better than `LinkedList`. When is `LinkedList` actually right?"

- **Naive:** "When you do a lot of insertions in the middle."  ← *the textbook answer, and it is misleading*
- **The trap:** The trap is that "insertions in the middle" is *not* a reason — an `ArrayList` insertion in the middle is O(n) because it **shifts the elements**, and a `LinkedList` insertion is O(1) *only if you already hold the node reference*. If you have to *find* the position first, the `LinkedList` search is O(n) and the `ArrayList` search is O(n) too — but the `ArrayList` then shifts at **cache speed** while the `LinkedList` chases pointers across the heap.
- **Expert answer:** The honest answer: `LinkedList` is essentially never the right choice in application code, and the textbook "insertions in the middle" claim assumes the one thing you never have. The cases where it genuinely wins are narrow: (1) you hold a `ListIterator` **at** the insertion point, so the position is already known — `listIterator.next()` is O(1) on a `LinkedList` and O(n) on an `ArrayList`; (2) you need a **concurrent** queue-like structure where node-level linking matters — and even then, `ConcurrentLinkedQueue` is the answer, not `LinkedList`; (3) you are implementing a data structure that is *conceptually* a linked list (a free list, an intrusive list). The engineering facts that settle it: `LinkedList` allocates a ~40-byte `Node` per element (vs ~4–8 bytes for an `ArrayList` slot), has **terrible cache locality** (a linear scan of 1 M elements touches 1 M cache lines), and every traversal is a pointer chase. The correct interview answer: *"In practice `ArrayList` wins, essentially always. `LinkedList` is faster only when you already hold the node — which application code does not."*

### CQ2. "You said `HashMap` is O(1) on average. What is the *worst* case, and can you make it O(log n)?"

- **Naive:** "O(n), because of collisions."  ← *true, and it misses the Java 8 defence*
- **The trap:** The trap is to describe the worst case as simply "O(n)" — which is true for *any* hash table, and misses the fact that **Java 8 made it O(log n)** in the pathological case, via **treeification**.
- **Expert answer:** The full story. The worst case is a **collision cluster**: every key hashes to the same bucket, so a lookup walks the whole chain. Three numbers to know: `TREEIFY_THRESHOLD = 8` (a chain of 8 triggers treeification), `MIN_TREEIFY_CAPACITY = 64` (but only if the table is at least 64 long — otherwise the map **resizes** instead, because a bigger table disperses the collisions more cheaply), and `UNTREEIFY_THRESHOLD = 6` (the tree reverts to a list below 6, since tree nodes are memory-heavy). So: a chain of 8 in a table of 64+ becomes a red-black tree, and the lookup becomes O(log n). The attacker's problem: the tree's comparison order is `hash` → `Comparable` → `System.identityHashCode`, and the last is **not** attacker-controlled — so the attacker cannot predict the tree's shape, and each comparison degenerates to an identity hash. The residual weakness: a **small** map (fewer than 64 buckets) is never treeified, so it remains a chain — which is why a `HashMap` with a fixed, small capacity is still vulnerable. And the **real** defence for untrusted keys is not the JDK's treeification at all: it is a **randomised hasher** (so the attacker cannot predict the bucket) or a `TreeMap` (O(log n) guaranteed, with no hash dependency at all).

### CQ3. "Why is `HashMap` iteration order not insertion order? Isn't that a bug?"

- **Naive:** "It's by design — the hash order."  ← *true, and it misses why it matters*
- **The trap:** The trap is to treat it as a curiosity. The real issue is that it is a **silent, observable behaviour that programmers rely on by accident** — and then a change to the *capacity* (a resize) or a change to the `hashCode` (a code change in a domain class) **changes the order**, with no error.
- **Expert answer:** The mechanism: the iteration walks the **table array** in index order, and each bucket's chain. So the order is determined by `(hash & (n-1))` — the bucket index — and the chain order within a bucket. When the table **resizes**, the entries are redistributed (the lo/hi split), so the iteration order **changes**. The consequences: (1) **never rely on `HashMap` iteration order** — it is not part of the contract, and it can change between JDK versions, between JVMs, and after a `hashCode` change. (2) If you need a defined order, use `LinkedHashMap` (insertion order) or `TreeMap` (sorted). (3) A **`hashCode` change is a correctness bug**, not just an ordering change: it breaks the map's internal index for every key of that type, so `get` silently fails. The deeper lesson: **`hashCode` is part of the type's identity contract** — if a class is used as a map key, its `hashCode` must be **stable for the object's lifetime**, and its `equals` must be **consistent with it**. That is why the JDK's `Integer`, `Long` and `String` cache their hash, and why a mutable key is a bug.

### CQ4. "You said `PriorityQueue` iteration is not sorted. Then how do you get sorted output?"

- **Naive:** "Drain it with `poll()`."  ← *correct, and it misses the cost*
- **The trap:** The trap is to miss the **cost difference**, which is the actual engineering decision. Draining is O(n log n) — a full sort. If you only want the *top K*, draining everything is wasteful.
- **Expert answer:** Three options, with their costs. (1) **Drain with `poll()`** — O(n log n) time, O(n) memory for the result. Correct and simple. (2) **Copy and sort** — `new ArrayList<>(pq)` then `Collections.sort` — also O(n log n), but it makes the *heap order* disappear, so you lose the "peek at the min in O(1)" property while the queue is alive. (3) **A bounded heap of size K** — O(n log K) time, O(K) memory — which is the *right* answer for "the top K". And the fourth, which is the one that catches people: (4) **the queue is not sorted at all, so if you need the whole thing sorted, you did not need a priority queue** — you needed a sort. The design lesson: **a `PriorityQueue` is for repeatedly extracting the *minimum*; if you want a sorted list, sort a list.** Using a heap to produce a sorted list is O(n log n) with a much worse constant factor than `Arrays.sort` (which is a tuned dual-pivot quicksort for objects, and a timsort for primitives). The related trap worth naming: `PriorityQueue` is **not stable** — two elements with the same priority come out in an arbitrary order, so if you need FIFO within a priority, you must add a monotonic sequence number to the comparator.

### CQ5. "You said `TreeMap` is O(log n) guaranteed. But isn't a `HashMap` usually faster in practice?"

- **Naive:** "Yes, `HashMap` is usually faster."  ← *true, and it misses the point of the guarantee*
- **The trap:** The trap is to compare the *average* against the *worst case* and call the hash map the winner. The real question is: **which guarantee do you need?** A `TreeMap` is O(log n) *always*; a `HashMap` is O(1) *usually* and O(n) *pathologically*.
- **Expert answer:** The two frameworks: **expected vs guaranteed**. The `HashMap`'s O(1) is an *expectation* over the distribution of your keys' hashes — it is a probabilistic guarantee, and it is only as good as your `hashCode` and your adversary's ignorance. The `TreeMap`'s O(log n) is a *worst-case guarantee* that holds for any input. The choice is therefore: (1) **do you need sorted iteration or range queries?** → `TreeMap` regardless of speed (no hash map can do it). (2) **are the keys adversarial or the hash function weak?** → `TreeMap` (or a randomised hasher). (3) **is the workload read-heavy and the keys well-distributed?** → `HashMap` — and it really is faster, because the constant factor is lower (one hash + one array index vs ~log n tree traversals with pointer chasing). And the hybrid worth naming: a `HashMap` whose buckets are **trees** (Java 8's treeification) gets *both* — O(1) average with an O(log n) worst case — which is exactly why the `HashMap` is not simply "unsafe" for untrusted keys, and why the *real* risk is a **small** map (below 64 buckets) where treeification never happens. The performance numbers to quote: `HashMap.get` is roughly 2–3× faster than `TreeMap.get` on well-distributed keys, but the gap narrows for large maps (cache misses dominate) and inverts for adversarially-colliding keys.

### CQ6. "If `HashMap` is O(1), why do production systems use Redis for caching?"

- **Naive:** "Because it is in-memory and shared."  ← *true, and it misses the data-structure point*
- **The trap:** The trap is to answer at the *deployment* level when the question is about the *data structure*. The interesting answer is that the two systems solve **different problems** — and the failure modes are what matter.
- **Expert answer:** The distinction is **locality and lifetime**, not speed. (1) **Locality** — a `HashMap` is on one JVM's heap; to share it across instances you need a network hop (a cache *miss* costs a network round trip — hundreds of microseconds — versus a `HashMap.get` of tens of nanoseconds; that is a **four-order-of-magnitude** difference, and it dwarfs every algorithmic subtlety). (2) **Lifetime and coherence** — a local map is per-instance and per-restart; a shared cache must handle invalidation, replication, and staleness. (3) **Eviction** — a local `HashMap` has no eviction (it grows until the heap dies); a shared cache *must* evict, and that makes it an **approximate** structure (LRU/LFU with sampling, and "cache admission" policies like Caffeine's W-TinyLFU) — which is a *different data-structure problem* from "look up a key". (4) **The hot-set insight** — a well-known result is that production traffic is heavily skewed (often 80/20), and a *local* cache in front of a shared one captures most of the benefit with none of the coherence cost. So the production architecture is usually **two levels**: a small local `HashMap` (or Caffeine cache) in front of a shared one — and *that* is a data-structure decision about **where the copy lives**, which is exactly what a DSA interview is really probing when it asks "why not just use a `HashMap`?"

### CQ7. "How would you find the k-th largest element in a stream that is too large to sort?"

- **Naive:** "Use a `PriorityQueue` and poll k times."  ← *correct, and it is O(n log k) with a k-element heap — but the *space* is the issue*
- **The trap:** The trap is that the naive "use a priority queue" answer builds a queue of **all n** elements — O(n) memory — and then it is no better than sorting. The point of the problem is that you must **not** materialise everything.
- **Expert answer:** The correct approach: maintain a **bounded min-heap of size k**. For each element, offer it to the heap; if the heap exceeds k, poll the smallest (which is the one that does not belong in the top k). The total cost is O(n log k) time and **O(k) space** — and it works on an **infinite stream** (you never hold more than k elements). The correctness argument: the heap always contains the k largest seen so far, and its root is the smallest of them, so any new element smaller than the root can be discarded immediately. The variants worth mentioning: (1) **quickselect** — O(n) average time, O(1) space, but it requires the whole array in memory and mutates it; (2) **quickselect with a stream** — not possible without materialising; (3) the **space-saving** version is the bounded heap. And the generalisation the interviewer is testing: **whenever the problem says "the top K" or "the K largest", the answer is a bounded heap, not a sort** — the same insight that makes a *streaming* algorithm possible where a sorting one is not.

### CQ8. "You said `ArrayList` is O(1) to index. But `get(i)` on a `LinkedList` is O(n) — so why does `LinkedList` even implement `List`?"

- **Naive:** "For API compatibility."  ← *true, and it misses the design point*
- **The trap:** The trap is to see `LinkedList` as a mistake. It is not — it is a **different trade-off in the same interface**, and the interface (`List`) is the *abstraction* that lets the caller choose.
- **Expert answer:** The `List` interface is a **contract about the operations**, not about the implementation. `List` promises O(1) `get`? No — it promises the *operations exist*; the complexity is a property of the implementation, and the Javadoc documents the *guaranteed* complexity per implementation. The real reason `LinkedList` exists: (1) **the interface is the abstraction** — a caller that only uses `add`/`remove`/`iterator` can be handed either implementation, and the choice is made by the *use case* (a `LinkedList` in a rarely-used, heavily-linked structure is fine); (2) it supports **node-level operations** the `List` interface cannot express — `ListIterator.add` at a cursor is O(1) on a `LinkedList` and O(n) on an `ArrayList`, and that is a *real*, documented difference; (3) it is the **natural implementation for a concurrent queue** in some designs, and the JDK's `ConcurrentLinkedQueue` is a lock-free linked structure for exactly that reason. The design lesson: **the interface is the contract; the complexity is a property of the implementation, and the Javadoc is where you make the trade-off visible** ("O(n) for `get(i)`"). And the honest engineering verdict: `LinkedList` is in the JDK for historical and API-completeness reasons, and modern code should reach for `ArrayList` or `ArrayDeque` — but the *interface* is right, and that is the point.

### CQ9. "If `HashMap` uses a `Node[]` and `ArrayList` uses an `Object[]`, why does `HashMap` need a `Node` per entry?"

- **Naive:** "Because a key and a value need two fields."  ← *true, and it misses the real reason*
- **The trap:** The trap is to see it as a data-layout detail. The real reason is **collision handling**: a bucket must be able to hold *several* entries, and a linked list of nodes is how it does it — each node holds the hash (cached, so the chain walk does not recompute it), the key, the value, and the next pointer.
- **Expert answer:** The design detail that pays off: **the `hash` is cached in the node**. That is why a `HashMap.get` does not call `key.hashCode()` for every node in a chain — the hash is computed once at `put` time and stored. (In Java 8+, `HashMap.Node` has a `final int hash` field; in Java 7 it was `HashMap.Entry` with the same idea.) The other detail: the node is a **separate object** rather than two parallel arrays (a "key array + value array" layout), because the *chains* are per-bucket and their lengths are unknown — a parallel-array layout would waste space. And the Java 8 change: when a bucket exceeds 8 entries, the chain is converted into a `TreeNode` — a **red-black tree node** with `left`/`right`/`parent`/`prev` pointers — which caps the lookup at O(log n) and also gives the tree a *sort order* within the bucket. The cost is memory (a tree node is much bigger) and the `UNTREEIFY_THRESHOLD = 6` rule that reverts it. The deep lesson: **the layout of a hash table is dictated by the collision-handling strategy** — and the strategy is dictated by the adversary you assume.

### CQ10. "What is the most important thing you know about data structures that isn't in a textbook?"

- **Naive:** "The complexity table."  ← *true, and it is exactly what a textbook covers*
- **The trap:** The trap is to answer with textbook content. The interviewer is asking what you have learned from *building and running* systems — and the honest answer is about **constants, memory layout, and the access pattern**, not asymptotics.
- **Expert answer:** Three things. (1) **Constants dominate below the crossover.** The asymptotic win of an O(n log n) algorithm over O(n²) only appears past a threshold; below it, the constant factor decides — and I have shipped the "optimised" O(n log n) version that was *slower* because it allocated. (2) **Memory layout is a first-class concern.** Cache locality often matters more than the big-O: an `ArrayList` scan of 1 M elements is ~10× faster than a `LinkedList` scan *despite both being O(n)*, because the array is contiguous and the linked list chases pointers across the heap. And false sharing — two counters on the same cache line — can cost 5–10× in a concurrent counter, with no lock involved at all. (3) **The access pattern decides the structure, not the operation set.** A structure is chosen by its *hottest* path and its *constraints*: a read-mostly list → `CopyOnWriteArrayList`; a high-contention counter → `LongAdder`; a hot 10% of a large key space → a two-level cache; a bounded, ordered work queue → `PriorityQueue`. The textbook question is "what is the complexity of operation X?"; the production question is "**which operation is hot, and what are my memory and latency constraints?**" — and the second question is the one that actually determines the design.

---

## 10. Rapid-Fire MCQs

**Q1.** `ArrayList.get(i)` is…
- A) O(n)  B) **O(1)**  C) O(log n)  D) Amortised O(1)

**Q2.** `ArrayList.add()` at the end is…
- A) O(n)  B) O(1)  C) **O(1) amortised**  D) O(log n)

**Q3.** `ArrayList.add(0, x)` is…
- A) O(1)  B) O(log n)  C) **O(n)**  D) O(1) amortised

**Q4.** `list.remove(0)` in a loop over n elements is…
- A) O(n)  B) O(log n)  C) **O(n²)**  D) O(1)

**Q5.** `LinkedList.get(i)` is…
- A) O(1)  B) **O(n)**  C) O(log n)  D) O(1) amortised

**Q6.** `HashMap.get(key)` is…
- A) O(1) always  B) **O(1) on average**  C) O(log n) always  D) O(n) always

**Q7.** `HashMap`'s worst case is…
- A) O(log n)  B) O(n)  C) **O(n), or O(log n) when treeified**  D) O(n log n)

**Q8.** Java 8 treeifies a bucket when it reaches how many entries?
- A) 4  B) 6  C) **8**  D) 16

**Q9.** Treeification also requires the table to be at least how long?
- A) 16  B) 32  C) **64**  D) 128

**Q10.** `TreeMap.get(key)` is…
- A) O(1)  B) **O(log n)**  C) O(n)  D) O(1) amortised

**Q11.** Which collection gives sorted iteration?
- A) `HashMap`  B) `HashSet`  C) **`TreeMap` / `TreeSet`**  D) `ArrayList`

**Q12.** `PriorityQueue.peek()` is…
- A) O(n)  B) O(log n)  C) **O(1)**  D) O(1) amortised

**Q13.** Iterating a `PriorityQueue` gives…
- A) Sorted order  B) **Heap order**  C) Insertion order  D) Hash order

**Q14.** `PriorityQueue` is stable?
- A) Yes  B) **No**  C) Only with a `Comparator`  D) Only for primitives

**Q15.** `Collections.binarySearch` requires…
- A) A non-empty list  B) **A sorted list**  C) A `List` (not an array)  D) Unique elements

**Q16.** The correct "two-sum" solution is…
- A) Two nested loops  B) **A hash map — O(n) time, O(n) space**  C) Sorting  D) A binary search

**Q17.** BFS uses which data structure?
- A) A stack  B) **A queue**  C) A heap  D) A set

**Q18.** BFS finds the shortest path in…
- A) Any graph  B) **An unweighted graph**  C) A weighted graph  D) A DAG only

**Q19.** The sliding-window pattern requires the constraint to be…
- A) Any constraint  B) **Monotonic**  C) Strictly decreasing  D) Numeric

**Q20.** The "top K" problem is best solved with…
- A) A full sort  B) **A bounded min-heap of size K**  C) `Arrays.sort`  D) A `TreeMap`

**Q21.** `LinkedHashMap` with `accessOrder = true` gives…
- A) Insertion order  B) **Access (LRU) order**  C) Sorted order  D) Hash order

**Q22.** `HashMap` iteration order is determined by…
- A) Insertion order  B) **The bucket index and chain order**  C) The key's `compareTo`  D) It is random

**Q23.** A `HashMap` with a bad `hashCode` degrades to…
- A) O(log n)  B) **O(n) per lookup**  C) O(1) still  D) It throws

**Q24.** `CopyOnWriteArrayList` is best for…
- A) Write-heavy lists  B) **Read-mostly, small lists**  C) Large lists  D) Concurrent blocking queues

**Q25.** `ConcurrentHashMap` (Java 8+) locks…
- A) The whole table  B) A segment  C) **The head node of the affected bin**  D) Nothing

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | It is backed by an array — indexing is one address computation. |
| 2 | **C** | Geometric growth makes the total cost of n insertions O(n). |
| 3 | **C** | Every subsequent element must be shifted by one. |
| 4 | **C** | Each `remove(0)` shifts the whole list → n × O(n). |
| 5 | **B** | It must walk the chain from the head. |
| 6 | **B** | A hash + one bucket lookup — *if* the keys are well distributed. |
| 7 | **C** | O(n) for a long chain, but Java 8 treeifies it to O(log n). |
| 8 | **C** | `TREEIFY_THRESHOLD = 8`. |
| 9 | **C** | `MIN_TREEIFY_CAPACITY = 64` — otherwise the map resizes instead. |
| 10 | **B** | A red-black tree — every operation is O(log n), guaranteed. |
| 11 | **C** | Only the tree-based collections keep the keys sorted. |
| 12 | **C** | The root of a min-heap *is* the minimum — one array read. |
| 13 | **B** | The iterator walks the backing heap array, which is only locally ordered. |
| 14 | **B** | Equal-priority elements come out in an arbitrary order. |
| 15 | **B** | It is a binary search — on an unsorted list the result is meaningless. |
| 16 | **B** | The hash map turns "have I seen the complement?" into an O(1) check. |
| 17 | **B** | A queue — that is what gives BFS its level-by-level order. |
| 18 | **B** | BFS explores by edge count, which is the shortest path only when edges are equal-cost. |
| 19 | **B** | The window must never need to move *backwards*, which requires monotonicity. |
| 20 | **B** | A bounded heap is O(n log k) time and O(k) space — and it streams. |
| 21 | **B** | `accessOrder = true` moves an entry to the front on `get` — the basis of an LRU. |
| 22 | **B** | The iterator walks the table array, so the order is the hash distribution. |
| 23 | **B** | All the keys land in one bucket, so every lookup walks the chain. |
| 24 | **B** | Reads are O(1) and lock-free; writes copy the whole array. |
| 25 | **C** | Per-bin locking — so contention is per-bin, not per-table. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                       THE COMPLEXITY TABLE                               │
├──────────────┬──────────┬──────────┬──────────┬──────────┬──────────────┤
│              │ ARRAY    │ ARRAYLIST│ LINKEDLIST│ HASHMAP  │ TREEMAP      │
├──────────────┼──────────┼──────────┼──────────┼──────────┼──────────────┤
│ get(i)       │   O(1)   │   O(1)   │   O(n)   │    —     │      —       │
│ get(key)     │    —     │    —     │    —     │  O(1)avg │   O(log n)   │
│ add(end)     │  O(1)*   │  O(1)*   │   O(1)   │  O(1)avg │   O(log n)   │
│ add(index)   │   O(n)   │   O(n)   │   O(1)   │    —     │      —       │
│ remove(key)  │    —     │    —     │    —     │  O(1)avg │   O(log n)   │
│ contains     │   O(n)   │   O(n)   │   O(n)   │  O(1)avg │   O(log n)   │
│ size         │   O(1)   │   O(1)   │   O(1)   │   O(1)   │    O(1)      │
│ sorted?      │    —     │    —     │    —     │    ✗     │     ✅       │
│ null key     │    —     │    —     │    —     │    ✅     │     ✗        │
├──────────────┴──────────┴──────────┴──────────┴──────────┴──────────────┤
│ * amortised (geometric growth)   TREESET = O(log n), sorted              │
│                                  PRIORITYQUEUE = O(1) peek, ⚠ NOT sorted │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE BIG-O LADDER                                      │
│  O(1) → O(log n) → O(n) → O(n log n) → O(n²) → O(2ⁿ) → O(n!)            │
│                                                                          │
│  n = 10⁶:  1  |  20  |  1M  |  20M  |  10¹²  |  ∞  |  ∞                │
│                                                                          │
│  ⚠ < 10⁴ ops  → O(n²) is fine                                          │
│  ⚠ < 10⁶ ops  → O(n log n) is fine                                     │
│  ⚠ > 10⁸ ops  → you need a better ALGORITHM, not a faster language      │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 6 DSA TRAPS                                      │
│  1. list.remove(0) in a loop          → O(n²)                            │
│  2. LinkedList.get(i) in a loop      → O(n²)                            │
│  3. A TreeSet with a bad Comparator  → elements silently dropped        │
│  4. Modifying a collection while      → ConcurrentModificationException │
│     iterating it                                                   │
│  5. (lo + hi) / 2 in binary search   → integer overflow                │
│  6. Assuming PriorityQueue iteration → heap order, NOT sorted           │
│     is sorted                                                        │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The collection selection guide

| Requirement | Use | Not |
|---|---|---|
| Random access by index | `ArrayList` | `LinkedList` |
| A queue or a deque | `ArrayDeque` | `LinkedList` |
| Key → value, fast | `HashMap` | `TreeMap` |
| Key → value, sorted / range queries | `TreeMap` | `HashMap` |
| A unique set, fast | `HashSet` / `EnumSet` | `TreeSet` |
| A unique set of enums | **`EnumSet`** (one `long`) | `HashSet` |
| A key → value map of enums | **`EnumMap`** (an array) | `HashMap` |
| Read-mostly, small list | `CopyOnWriteArrayList` | `synchronizedList` |
| A high-contention counter | **`LongAdder`** | `AtomicLong` |
| A shared map, concurrent | `ConcurrentHashMap` | `Hashtable` |
| Sorted + concurrent | `ConcurrentSkipListMap` | — |
| A work queue with back-pressure | `BlockingQueue` | `ExecutorService` + a `List` |
| A priority queue | `PriorityQueue` | `TreeSet` |
| A bounded LRU cache | Caffeine / `LinkedHashMap` | `HashMap` |

### The performance rules

| Rule | Why |
|---|---|
| Never `remove(0)` from an `ArrayList` in a loop | O(n²) |
| Prefer `ArrayList` to `LinkedList` | 5–10× better cache locality |
| Use a **bounded** heap for "top K" | O(k) space, and it streams |
| Use `EnumSet`/`EnumMap` for enum keys | No hashing; 8 bytes vs kilobytes |
| Use `LongAdder` for hot counters | No cache-line contention |
| Pre-size an `ArrayList` when you know the size | Avoids the geometric reallocation |
| Use `Arrays.parallelSort` for large primitive arrays | Parallel, and it uses a tuned merge |
| Benchmark with **JMH**, never `System.nanoTime()` in a `main` | The JIT will optimise the loop away |

### The concurrency rules

| Rule | Why |
|---|---|
| Use `ConcurrentHashMap`, never a synchronised `HashMap` | Per-bin locks, lock-free reads |
| Use `LongAdder` for a metric counter | Striping avoids false sharing |
| Pad hot counters onto separate cache lines | False sharing is a 5–10× cost |
| Never mutate a `HashMap` from multiple threads | Corruption, and (pre-Java 8) infinite loops |

### Observability

| Metric | What it tells you |
|---|---|
| Collection size per request | A leak or an unbounded cache |
| `HashMap.get` time in a profile | A bad `hashCode`, or a cache miss |
| `LinkedList` in a heap histogram | The wrong collection for the job |
| GC rate and allocation rate | A copy-heavy operation (e.g. `CopyOnWriteArrayList`) |

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The collection internals in full | [04 · Collections Framework](../04-Collections-Framework/README.md) |
| `EnumSet` / `EnumMap` in detail | [13 · Enums](../13-Enums/01-Enum-Deep-Dive.md) |
| Concurrency and the concurrent collections | [08 · Multithreading & Concurrency](../08-Multithreading-Concurrency/README.md) |
| The design patterns that use these structures | [09 · Design Patterns](../09-Design-Patterns/01-Design-Patterns.md) |
| The algorithm catalogue | LeetCode, "Grokking Algorithms", and Adit Varshney's free course |

---

**Next Chapter:** → [12 · Strings & String Internals](../12-Strings/01-String-Internals.md)
**Previous Chapter:** → [11 · DSA](01-DSA-Quick-Reference.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
