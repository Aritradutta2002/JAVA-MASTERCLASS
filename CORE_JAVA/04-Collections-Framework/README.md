# 04 — Collections Framework · Interview Notes Index

> **The #1 most-asked folder in Java interviews.** HashMap internals alone appear in
> nearly every interview loop. Read this folder three times: once to understand,
> once to memorise the internals, once to defend the trade-offs.

| Priority | Chapters | Why It Matters |
|----------|----------|----------------|
| ⭐⭐⭐⭐⭐ | [02 · Map Implementations](02-Map-Implementations.md) | HashMap / CHM / TreeMap / LinkedHashMap internals |
| ⭐⭐⭐⭐⭐ | [01 · List Implementations](01-List-Implementations.md) | ArrayList vs LinkedList vs Vector, growable arrays |
| ⭐⭐⭐⭐ | [03 · Set, Queue & Utilities](03-Set-Queue-Utilities.md) | HashSet, TreeSet, PriorityQueue, ArrayDeque, Collections |
| ⭐⭐⭐⭐⭐ | (cross-link) | → [05 · Generics](../05-Generics/README.md) — why `List<?>` matters |

---

## 📚 Recommended Reading Order

```
01 Lists ──▶ 02 Maps ──▶ 03 Sets/Queues/Utilities ──▶ 05 Generics
```

---

## 🧠 The One-Page Mental Model

```
Iterable
   └── Collection
         ├── List        → ArrayList, LinkedList, Vector, CopyOnWriteArrayList, Stack
         ├── Set         → HashSet, LinkedHashSet, TreeSet, EnumSet
         ├── Queue       → PriorityQueue, ArrayDeque, LinkedBlockingQueue
         └── Deque       → ArrayDeque

Map (separate hierarchy, NOT a Collection)
         ├── HashMap, LinkedHashMap, TreeMap, Hashtable, Properties
         ├── ConcurrentHashMap, ConcurrentMap
         └── EnumMap, WeakHashMap, IdentityHashMap
```

> **Caption:** The single most common mistake — treating `Map` as a `Collection`.
> `Map` has no `add()`, it has `put()`. `map.values()` gives you a `Collection`.

---

## 🎯 Most Asked in Interviews

| Question | Chapter | The Killer Follow-Up |
|----------|---------|----------------------|
| How does `HashMap` work internally? | [02](02-Map-Implementations.md) | "Why `hash ^ (hash >>> 16)`?" |
| Why does a bucket turn into a tree? | [02](02-Map-Implementations.md) | "What are the thresholds (8 / 6)?" |
| `HashMap` vs `ConcurrentHashMap`? | [02](02-Map-Implementations.md) | "How does CHM lock?" |
| Why is `ConcurrentHashMap` `get()` lock-free? | [02](02-Map-Implementations.md) | "What's `sizeCtl`?" |
| `ArrayList` vs `LinkedList`? | [01](01-List-Implementations.md) | "What's the real memory cost of a node?" |
| `HashSet` — how is it implemented? | [03](03-Set-Queue-Utilities.md) | "It wraps a `HashMap`." |
| `PriorityQueue` — stable? | [03](03-Set-Queue-Utilities.md) | "No — it's a heap." |
| `ConcurrentModificationException` — why? | [03](03-Set-Queue-Utilities.md) | "Fail-fast iterators." |
| `Collections.unmodifiableList` vs `List.of`? | [03](03-Set-Queue-Utilities.md) | "One is a *view*, the other a *copy*." |

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **Amortised vs worst-case** — `ArrayList.get()` is O(1), `add()` is amortised O(1) but O(n) on resize.
- [ ] **The `hashCode` contract** — mutable keys in a `HashMap` cause the entry to become unreachable (a classic leak).
- [ ] **Fail-fast iteration** — `ConcurrentModificationException` is *best-effort*, not guaranteed.
- [ ] **`IdentityHashMap` vs `HashMap`** — reference equality vs `equals()`.
- [ ] **Default `HashMap` capacity is 16, load factor 0.75** → resize at 12 entries.

---

## 📊 Complexity Cheat Sheet (memorise this table)

| Collection | get | put/add | remove | contains | iterate | Nulls | Thread-Safe |
|---|---|---|---|---|---|---|---|
| `ArrayList` | O(1)* | O(1)* | O(n) | O(n) | O(n) | ✅ | ❌ |
| `LinkedList` | O(n) | O(1) | O(1)* | O(n) | O(n) | ✅ | ❌ |
| `HashMap` | O(1) | O(1) | O(1) | O(1) | O(n) | 1 null key | ❌ |
| `LinkedHashMap` | O(1) | O(1) | O(1) | O(1) | O(n) | 1 null key | ❌ |
| `TreeMap` | O(log n) | O(log n) | O(log n) | O(log n) | O(n) | ❌ | ❌ |
| `HashSet` | O(1) | O(1) | O(1) | O(1) | O(n) | ✅ 1 null | ❌ |
| `TreeSet` | O(log n) | O(log n) | O(log n) | O(log n) | O(n) | ❌ | ❌ |
| `ArrayDeque` | O(n) | O(1) | O(1) | O(n) | O(n) | ❌ | ❌ |
| `PriorityQueue` | O(n) | O(log n) | O(log n) | O(n) | O(n) | ❌ | ❌ |
| `ConcurrentHashMap` | O(1) | O(1) | O(1) | O(1) | O(n) | ❌ | ✅ |

\* amortised

---

**Related folders:** → [05 · Generics](../05-Generics/README.md) · → [08 · Concurrency](../08-Multithreading-Concurrency/README.md) · ← [03 · Exception Handling](../03-Exception-Handling/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
