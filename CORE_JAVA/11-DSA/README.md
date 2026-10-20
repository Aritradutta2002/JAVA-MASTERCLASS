# 11 — Data Structures & Algorithms · Interview Notes Index

> A **reference**, not a textbook. Use it to (a) refresh complexity, (b) memorise the
> *approach fingerprint* of each canonical problem, and (c) practise dry-run narration —
> because in a live interview, thinking out loud is what buys you time.

| Priority | Chapter | Why It Matters |
|----------|---------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · DSA Quick Reference](01-DSA-Quick-Reference.md) | ~35 canonical problems with approach + complexity + Java solution |

---

## 🧠 The Complexity Ladder (visualise this, don't memorise it)

```
n elements
   │
   │  O(n!)  permutations            ─  1.6M ops @ n=10
   │  O(2ⁿ)  subsets / fib(n)        ─  1.0K ops @ n=10
   │  O(n²)  nested loops            ─  100  ops @ n=10
   │  O(n log n) merge/quick sort    ─  33   ops @ n=10
   │  O(n)   single pass              ─  10   ops @ n=10
   │  O(log n) binary search          ─  4    ops @ n=10
   │  O(1)   hash lookup / array idx  ─  1    op  @ n=10
   └────────────────────────────────────────── log₂ n
```

> **Caption:** The single most valuable interview skill is **recognising which rung** a problem sits on.
> A nested loop over the same array is almost always a missed O(n) hash pass or a missed two-pointer.

---

## 🎯 Pattern Fingerprints (how to recognise the problem)

| You see… | Think… | Classic |
|-----------|--------|---------|
| "sorted array", "find target", "O(log n)" | Binary search | Search insert position |
| "subarray with sum/contiguous" | Sliding window (fixed or variable) | Longest substring without repeat |
| "pair/triple with a target sum" | Two pointers | Two Sum II, 3Sum |
| "frequency/count/lookup by key" | HashMap | Two Sum, subarray sum equals K |
| "top-k / k-th largest" | Heap or Quickselect | Kth largest element |
| "tree, ancestor, subtree" | DFS/BFS on tree | Binary tree traversal |
| "shortest path, unweighted" | BFS | Shortest path in grid |
| "weighted path, min cost" | Dijkstra | Cheapest route |
| "dependencies, ordering" | Topological sort | Course schedule |
| "overlapping intervals" | Sort by start + sweep | Merge intervals |
| "choose/not-choose, max value" | DP (knapsack) | Coin change |
| "count ways to reach n" | DP (fibonacci shape) | Climbing stairs |

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **Narrating your approach out loud** — interviewers grade your thinking, not just the answer.
- [ ] **Dry-running on paper** before coding.
- [ ] **Edge cases**: empty, single element, duplicates, negatives, overflow, already sorted, reverse sorted.
- [ ] **Modifying the input** — usually allowed unless stated otherwise; ask.
- [ ] **Two-pointer vs sliding window** — the classic mix-up.
- [ ] **HashMap `O(1)` is amortised** and requires a good `hashCode`.

---

## 🧪 Practice Drill

- [ ] Explain how you'd detect a cycle in a linked list — both with a set and with Floyd's.
- [ ] Implement binary search on a rotated sorted array.
- [ ] Implement LRU cache from scratch (`LinkedHashMap` + `removeEldestEntry`).
- [ ] Implement a `ConcurrentHashMap`-backed rate limiter (sliding window).
- [ ] Merge `k` sorted lists using a heap — and explain why it's better than repeated pairwise merge.

---

**Related folders:** → [12 · Strings](../12-Strings/README.md) · ← [10 · SOLID](../10-SOLID-Principles/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
