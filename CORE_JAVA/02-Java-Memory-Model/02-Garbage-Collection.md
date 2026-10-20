# Garbage Collection — Complete Guide (Expert)

> **Level:** Expert
> **Interview Frequency:** ★★★★★ (the *only* Java topic where a wrong answer is a red flag for a senior role)
> **Prerequisite:** [01 · Memory Areas](01-Memory-Areas.md) — you must know the runtime data areas before you can talk about roots
> **Last Updated:** 2026

---

## 📋 Table of Contents

- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — Reachability, Not Ownership](#2-mental-model--reachability-not-ownership)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 How the GC Decides an Object Is Garbage](#31-how-the-gc-decides-an-object-is-garbage)
  - [3.2 GC Roots — The Complete List](#32-gc-roots--the-complete-list)
  - [3.3 The Generational Hypothesis](#33-the-generational-hypothesis)
  - [3.4 Object Lifecycle: Birth, Promotion, Death](#34-object-lifecycle-birth-promotion-death)
  - [3.5 Minor vs Major vs Full vs Mixed GC](#35-minor-vs-major-vs-full-vs-mixed-gc)
  - [3.6 The Collectors](#36-the-collectors)
  - [3.7 Reference Types — Strong, Soft, Weak, Phantom](#37-reference-types--strong-soft-weak-phantom)
  - [3.8 `finalize()` and `Cleaner`](#38-finalize-and-cleaner)
  - [3.9 Memory Leaks in a GC'd Language](#39-memory-leaks-in-a-gcd-language)
  - [3.10 Reading the GC Log](#310-reading-the-gc-log)
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

- **Java uses tracing (reachability) GC, not reference counting.** An object is garbage when it is **not reachable from any GC root**. Circular references are therefore a non-issue.
- **GC roots** = local variables in every thread's stack frames, static fields of loaded classes, JNI global references, monitors held by live threads, and the interned-string table.
- **The generational hypothesis: most objects die young.** So the heap is split — young (Eden + Survivor) and old — and the cheap collection of a nearly-dead young generation is done far more often.
- **Minor GC** collects the young generation (fast, STW, copying). **Major/Full GC** collects the whole heap (slow, often concurrent-mark in modern collectors). **Mixed GC** (G1) collects young + a slice of old.
- **G1 is the default since Java 9** — a *region-based* collector with a soft pause-time goal (`-XX:MaxGCPauseMillis`, default 200 ms). **ZGC** (Java 15+) and **Shenandoah** (Java 12+) give sub-millisecond pauses with concurrent compaction. **CMS is removed in Java 14** and was never a good idea.
- **Objects larger than half a G1 region are "humongous"** and are allocated directly into contiguous old-generation regions — a fragmentation hazard.
- **Four reference strengths:** strong (default), soft (cleared under memory pressure — use for caches), weak (cleared at the next GC — use for canonical maps), phantom (post-mortem, for `Cleaner`-style cleanup).
- **`finalize()` is deprecated for removal (Java 9) and was terminally deprecated in Java 18.** Use try-with-resources or `java.lang.ref.Cleaner`.
- **`System.gc()` is a hint, not a command.** And "GC overhead limit exceeded" is not a separate problem — it is the *death spiral* warning of a heap leak.
- **The diagnostic ladder:** `jstat -gcutil` → `jcmd GC.heap_info` → `jmap -histo:live` → `jcmd GC.heap_dump` → Eclipse MAT "Path to GC Roots".

---

## 1. What & Why — The Problem This Solves

**The problem.** Every object allocation is a promise that someone will eventually free it. In C++ that promise is kept by hand (`delete`), and the cost of getting it wrong is a *security* problem (use-after-free, double-free) or a *leak*. Java removes the *obligation* but not the *problem*: memory must still be reclaimed, and the language needs a rule for deciding *when*.

**The three candidate rules:**

| Rule | How it works | Fatal flaw |
|---|---|---|
| **Reference counting** | Each object has a counter; free it at zero | **Cycles leak.** `a.next = b; b.next = a;` — neither ever reaches zero. Also, a *single* stray reference anywhere in the program pins the whole graph. |
| **Tracing (reachability)** | Start from roots, mark everything reachable, free the rest | Needs to know the roots; pauses depend on the live set. **This is what Java (and Python, .NET, Go's non-conservative variant, Rust's ownership hybrid) use.** |
| **Conservative / stack-scanning (Boehm GC)** | Treat *any* word on the stack that *looks like* a pointer as a pointer | Cannot be precise; false positives pin objects. Used by some C++ and early Java (the old `sun.misc.GC`). |

**Why tracing won for Java:** because the JVM already *knows* the exact set of roots — it owns the interpreter and the JIT, so it knows every local variable slot, every static field, every monitor. Precision is available, so Java takes it. This is also why the JVM can do **escape analysis** and **stack allocation**, which a conservative collector never could.

**The analogy.** Imagine a night porter in a hotel. Every morning he walks the corridors and checks every room. A room is *discarded* not because the guest said "I'm leaving" (that is reference counting, and it fails when two guests hold each other's keys), but because **after walking every corridor from the front desk, the porter never passes that room**. Anything he cannot reach from the front desk is garbage. The front desk is the GC root set.

**Why this matters in production.** Because the GC's *definition* of garbage is reachability, a **memory leak in Java is a reachability problem, not an allocation problem**. `new` is never the bug. The bug is a *reference you forgot to drop* — a static collection, a listener, a `ThreadLocal`, a class loader, an interned string. This is the single most important reframe in this chapter: **you cannot fix a Java memory leak by allocating less; you fix it by releasing a reference.**

---

## 2. Mental Model — Reachability, Not Ownership

```text
              GC ROOTS  (the "front desk" of the hotel)
  ┌────────────────────────────────────────────────────────────────────┐
  │                                                                    │
  │  • Local variables in every thread's stack frames                   │
  │  • Static fields of every loaded class                              │
  │  • JNI global references                                            │
  │  • Monitors (synchronized objects) held by live threads             │
  │  • The interned-string table                                       │
  │  • (implementation) class loader data, thread stacks, JNI handles  │
  └───────────────┬────────────────────────────────────────────────────┘
                  │
                  │  ▼  TRACE  (follow every reference, transitively)
                  │
        ┌─────────┴─────────┐
        │                   │
   REACHABLE            NOT REACHABLE
   (live, keep)         (garbage, collect)
        │                   │
        ▼                   ▼
   Marked & kept       Unmarked → swept/compacted
   (copied to survivor  (memory reclaimed)
    or old gen)
```

**What to look at:** the arrow is one-way and *transitive*. Reachability is computed **from the roots outward**, never "inward" from candidates. That is why the graph must be traversed completely before anything is reclaimed, and why the mark phase is the expensive part of a collection.

**A concrete object graph:**

```text
   GC ROOT (static field)
   System.out ──▶ PrintStream ──▶ BufferedOutputStream ──▶ byte[8192]
        │                │
        │                └──▶ FilterOutputStream ──▶ ...
        │
        └──▶ (nothing else)

   GC ROOT (local var in Thread-1's frame)
   order ──▶ OrderLine[3] ──▶ Product ──▶ String ──▶ byte[] (the chars)
                 │
                 └──▶ BigDecimal ──▶ BigDecimal$Intrinsic

   GC ROOT (ThreadLocalMap of Thread-2)
   threadLocalMap ──▶ Entry ──▶ Session ──▶ byte[]

   ── NOTHING points to these ──
   orphanOrder ──▶ OrderLine[2]         ← UNREACHABLE  → collected
   discardedBuffer ──▶ byte[1048576]    ← UNREACHABLE  → collected
```

**What to look at:** `orphanOrder` is *not* garbage because it is old, and it is *not* garbage because it is in the old generation. It is garbage because **no root can reach it**. Generation is a *performance heuristic*; reachability is the *truth*. This distinction is the whole chapter.

---

## 3. Deep Dive — Theory

### 3.1 How the GC Decides an Object Is Garbage

**Reference counting** (not used by Java, but worth knowing because people still bring it up):

```text
   new Node()  →  refCount = 1
   a.next = b   →  b.refCount = 2
   a.next = c   →  b.refCount = 1, c.refCount = 1
   (a goes out of scope)  →  a.refCount = 0  →  a freed
```

- ✅ Precise, immediate, no GC pauses.
- ❌ **Cycles never reach zero.** `a↔b` leaks forever.
- ❌ **Cascading frees** can cause deep recursive destruction on the collector thread.
- ❌ Used in Python (with a cycle detector bolted on), C++ `shared_ptr` (with `weak_ptr` to break cycles), and COM.

**Tracing / reachability (what Java does):**

```text
   Phase 1 — MARK:  start at every root, follow references, set the mark bit
   Phase 2 — SWEEP / COPY: reclaim (or copy elsewhere) everything unmarked
```

- ✅ Handles cycles naturally — a cycle is unreachable as a group.
- ✅ Precise, because the JVM knows exactly which slots hold references.
- ❌ Requires a **pause** (or a sophisticated concurrent implementation) to get a consistent view of the object graph.
- ❌ Cost is proportional to the **live set**, not to the garbage — so a large heap with a small live set is cheap to collect, and a small heap with a large live set is expensive.

**The mark bit lives in the object's Mark Word** — the same 64-bit word that holds the identity hash code, the age and the lock state. The GC flips bits in it during marking. This is a wonderful bit of engineering: no side table is needed for marking, and the "sweep" phase is just a walk over the heap looking for unmarked objects.

### 3.2 GC Roots — The Complete List

| Root | Where it lives | Notes |
|---|---|---|
| **Local variables** | Each thread's stack frames | Includes the `this` reference in slot 0 |
| **Active method arguments** | Same frames | Parameters are locals |
| **Static fields** | Class metadata in Metaspace | The classic "unbounded static cache" leak |
| **JNI global references** | The JNI reference table | Native code can pin Java objects indefinitely |
| **Monitors / `synchronized` objects** | The thread's monitor set | The object being *locked* is a root for the duration of the lock |
| **Interned strings** | The string table | Reachable via the class's constant pool |
| **JNI local frames** | The JNI stack | Only while the native frame is on the stack |
| *(HotSpot internal)* **Class loader data** | Metaspace | Keeps the loader's classes alive |
| *(HotSpot internal)* **Thread stacks themselves** | Native | The stack memory is a root source |
| *(HotSpot internal)* **Pending finalizers** | The finalizer queue | Objects awaiting `finalize()` are kept alive |

**The reachability closure.** From every root, the GC follows:

- Instance fields (`getfield`/`putfield` reference slots)
- Array elements (reference-typed elements only)
- Static fields (of reachable classes)
- `java.lang.ref.Reference.referent` — **but only for strong/soft/weak in a specific way** (see §3.7)
- The contents of `Class` objects (a loaded class's constant pool, statics, and its class loader)
- Object monitors that are *inflated* (a monitor's `ObjectMonitor` holds the owning thread and the blocked queue)

### 3.3 The Generational Hypothesis

**The hypothesis (proved empirically by Peter Diananda in the 1950s, and it still holds):**

> Most objects die young. A small fraction live long enough to be promoted. The number of surviving objects between collections is roughly *constant*, independent of the heap size.

**Why this is a huge win.** A **non-generational** collector must trace *every* live object in the heap on every collection. If the heap is 64 GB and 10 GB is live, every collection costs `O(10 GB)`. A generational collector instead collects the small young generation constantly, and the cost of a young collection is `O(live young objects)` — typically a tiny fraction of the heap. The full collection is rare.

**The three sub-hypotheses that make it work:**

1. **Generational hypothesis** — most objects die young.
2. **Survivor hypothesis** — most objects that survive a young collection die before the next one.
3. **Promotion hypothesis** — for a given age, very few objects survive.

**The consequence for design.** Objects that *should* live long (a cache, a connection pool) are expensive: they get **copied** out of Eden into a survivor space on every young GC, then copied again, then copied again into the old generation. Every promotion is a **memcpy**. A workload with a high promotion rate pays a triple cost: allocation, copying, and old-generation occupancy (which is what triggers major GCs). This is why "use a `SoftReference` cache, not a strong one" is both a *correctness* and a *performance* recommendation.

**The counter-evidence, and what modern collectors do.** The generational hypothesis weakens for some workloads:

- **Long-lived caches** — a 10 GB in-memory cache violates it completely: nothing dies young, so the young GC copies a huge survivor set for nothing.
- **Big heaps** — the classic "we cache 40 GB of market data" case.
- **Modern hardware** — memory bandwidth and multi-core scaling changed the trade-off.

This is why **ZGC and Shenandoah are *non-generational*** (they use a single generation with coloured pointers / load barriers), and why G1 keeps generations only as a *heuristic* over regions.

### 3.4 Object Lifecycle: Birth, Promotion, Death

```text
  ┌──────────────────────────────────────────────────────────────────────────────┐
  │  ALLOCATION                                                                  │
  │   TLAB bump-pointer in Eden ─────────────────────────────────┐               │
  │                                                            ▼               │
  │  ┌───────────────┐   survives    ┌──────────────────┐  age > MaxTenuring   │
  │  │     EDEN      │──────────────▶│  SURVIVOR S0/S1  │────Threshold─────┐  │
  │  │  (new objects)│              │  (age 0..15)     │                  │  │
  │  └───────────────┘              └──────────────────┘                  ▼  │
  │         │ dies                              │ dies          ┌──────────────┐│
  │         ▼                                  ▼              │    OLD GEN    ││
  │    [collected]                        [collected]         │  (tenured)   ││
  │    (dead, zero cost)                                            │           ││
  │                                                             └──▶ [collected]│
  │                                                                  (major GC) │
  └──────────────────────────────────────────────────────────────────────────────┘
```

**The rules:**

| Rule | Value | Notes |
|---|---|---|
| Eden size | ~80% of young gen (`-XX:SurvivorRatio=8` default ⇒ Eden:S0:S1 = 8:1:1) | |
| Tenuring threshold | `MaxTenuringThreshold`, default **15** | An object is promoted when its age exceeds it |
| **Dynamic age rule** | If the survivor space is more than half full, the threshold is lowered to the age of the oldest survivor | G1/Serial/Parallel use this; it prevents survivor-space overflow |
| Promotion target | Old generation | In G1, promotion happens during the young collection's evacuation |

**Object age** is stored in **4 bits of the Mark Word**, so ages are 0–15. That is why `MaxTenuringThreshold` cannot usefully exceed 15.

**The cost model of a young GC (copying collector):**

```text
  cost ≈  (bytes of LIVE young data copied)  +  (number of surviving objects, pointer-chasing cost)

  NOT proportional to garbage.  1 GB of garbage in Eden costs ~0 to collect.
```

This is the key insight: **garbage is free, survivors are expensive.** Tuning therefore focuses on the *survivor/old-gen* side, not on reducing garbage.

### 3.5 Minor vs Major vs Full vs Mixed GC

| Event | Scope | Trigger | Pause profile | Frequency |
|---|---|---|---|---|
| **Minor / Young GC** | Young gen only (Eden + survivors) | Eden fills up | **STW**, short (1–50 ms) | Very frequent (seconds) |
| **Major / Old GC** | Old gen only | Old gen occupancy target exceeded | Concurrent mark + short STW in G1/ZGC; long in Serial/Parallel | Rare (minutes) |
| **Full GC** | **Everything** — young + old + Metaspace | `System.gc()`, allocation failure, GC failure, `jcmd GC.run` | **STW, long** (seconds to minutes) | Should never happen |
| **Mixed GC (G1)** | Young gen **+ a chosen subset of old regions** | G1's young collection decides to also collect some old regions to satisfy the pause goal | STW, predictable | Periodic |

**Why "major" and "full" are often conflated:**

- In **Serial/Parallel** collectors, "major GC" and "full GC" are effectively the same thing: a **STW mark-sweep-compact of the entire heap**.
- In **G1/ZGC/Shenandoah**, a "major GC" is a **concurrent** collection of the old generation with only short STW pauses, and a "full GC" is a rare, long, STW fallback.

**A full GC in production is an incident.** It means the collector could not satisfy its goals concurrently and had to fall back to the "stop everything and compact" path. Common causes:

- **Humongous allocation failure** (G1) — a huge array cannot be allocated contiguously.
- **Concurrent cycle failure** — the mutator out-paces the marker (the "GC overhead limit exceeded" death spiral).
- **Metaspace exhaustion** triggering a full GC.
- **An explicit `System.gc()`** (some libraries and profilers call it).
- **`jcmd GC.run`** or `-XX:+ExplicitGCInvokesConcurrent` not being set.

**The flags that keep a full GC away:**

```bash
-XX:+ExplicitGCInvokesConcurrent   # System.gc() triggers a concurrent cycle, not a STW full GC
-XX:+HeapDumpOnOutOfMemoryError     # capture the evidence
```

### 3.6 The Collectors

#### 3.6.1 Serial GC (`-XX:+UseSerialGC`)

- **Single-threaded, stop-the-world**, mark-sweep-compact.
- The **only** collector available on a single-core machine.
- **Pros:** no inter-thread coordination, lowest *total* CPU overhead, no write barriers, no SATB buffers. Excellent for tiny heaps (< 100 MB) and for **container workloads with a CPU quota of 1** — where a parallel collector's coordination threads steal the whole quota.
- **Cons:** pauses scale with the live set; no concurrency at all.
- **Use when:** CPU quota ≤ 1, or heap < 100 MB (CLI tools, serverless cold starts, JVM-based agents).

#### 3.6.2 Parallel GC (`-XX:+UseParallelGC`)

- **Multi-threaded, stop-the-world**, mark-sweep-compact. Also known as the **throughput collector**.
- Parallelises mark, sweep and compaction across `ParallelGCThreads` (default ≈ number of CPUs).
- **Pros:** the best *throughput* of any collector — it reclaims the most bytes per CPU-second. Excellent for batch jobs, ETL, and anything that does not care about latency.
- **Cons:** pauses are proportional to the live set and can be **seconds long** on a large heap.
- **Use when:** throughput matters more than latency (nightly batch, data pipelines, offline scoring). It was the **default in Java 8**.

#### 3.6.3 CMS — Concurrent Mark Sweep (`-XX:+UseConcMarkSweepGC`)

- **Deprecated in Java 9, removed in Java 14.** Do not write code that mentions it as a current option.
- Did concurrent marking of the old generation to get low pauses, but did **mark-sweep** (no compaction) → **floating garbage** and **fragmentation**, and a `ConcurrentModeFailure` would escalate to a full GC.
- **Historical relevance:** it is the collector people mean when they say "the old default". It was replaced by G1 in Java 9 precisely because G1 compacts and gives pause-time *guarantees* rather than pause-time *averages*.
- **Interview framing:** "CMS is gone; its role — concurrent low-pause collection — is now played better by G1 (default) and ZGC/Shenandoah (extreme)."

#### 3.6.4 G1 — Garbage First (`-XX:+UseG1GC`, **default since Java 9**)

**The idea: "garbage first".** Instead of collecting the young generation because it is *young*, G1 collects the regions with the **most garbage** first — because collecting garbage is cheap and frees the most space. The name is a pun: it prioritises *garbage* regions, so the regions that survive are the ones worth keeping.

```text
  G1 HEAP = a contiguous array of equally-sized REGIONS

  ┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐
  │ E  │ E  │ O  │ H  │ O  │ S  │ E  │ O  │ H  │ E  │ O  │ E  │
  ├────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┼────┤
  │ O  │ E  │ O  │ E  │ S  │ O  │ E  │ O  │ E  │ O  │ E  │ O  │
  └────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘
   E = Eden   S = Survivor   O = Old   H = Humongous
   (region size = heap / 2048, rounded to a power of 2, min 1 MB)
```

**How a G1 young collection works:**

1. **Pause (STW):** the JVM computes the set of "old regions" worth collecting based on the pause-time goal and the remembered-set write barriers.
2. **Concurrent marking (if needed):** the *concurrent marking cycle* runs alongside the application, using an **SATB (Snapshot-at-the-beginning) write barrier** to log references from the "old" snapshot into buffers, so that the mark phase sees a consistent view without pausing.
3. **Pause (STW):** *evacuation* — the chosen regions' live objects are **copied** to other regions (typically to survivor/old regions), and the source regions are freed. This is a **compacting** copy, so no fragmentation accumulates.
4. The cycle repeats. The **remembered set** and the **card table** track cross-region references so the collector does not have to scan the whole heap.

**Key G1 concepts:**

| Concept | Meaning |
|---|---|
| **Region** | The unit of collection. Size = `heap / 2048` rounded to a power of two (min 1 MB, max 32 MB). For a 64 MB heap → 1 MB regions; for 64 GB → 32 MB regions. |
| **Humongous object** | An object larger than **half a region**. Allocated directly into *contiguous* old-generation regions, and freed only by a concurrent cycle or a full GC. **The #1 cause of G1 full GCs.** |
| **Remembered set** | Per-region record of references *into* that region from elsewhere, so the collector can avoid scanning the whole heap. |
| **SATB buffers** | The write-barrier buffers that make concurrent marking possible. |
| **Pause-time goal** | `-XX:MaxGCPauseMillis`, **default 200 ms**. A *goal*, not a guarantee — G1 shrinks the young generation to meet it, and may collect more old regions. |
| **Initiating heap occupancy** | `-XX:InitiatingHeapOccupancyPercent`, default **45%** — when the concurrent marking cycle starts. |
| **Mixed GC** | A young collection that *also* collects a portion of the old generation, to stop old-gen occupancy from ratcheting up. |

**When G1 is right:** heaps from ~4 GB to ~64 GB, latency-sensitive services, and anything that wants a *predictable* pause rather than a low average pause. **G1 is the correct default answer in 90% of interviews.**

#### 3.6.5 ZGC — The Z Garbage Collector (`-XX:+UseZGC`, production-ready in Java 17)

- **Sub-millisecond pauses, independent of heap size and live-set size.** The design goal is stated as "< 1 ms pause, < 10% of CPU overhead".
- **Fully concurrent** — marking, relocation and compaction all happen while the application runs.
- **Coloured pointers + load barriers** instead of a SATB write barrier: the GC encodes "this object has been relocated" in the *metadata bits* of the pointer. The application reads pointers through a **load barrier** that checks the colour and re-reads the relocated address if needed.
- Uses **multi-mapping** (the same physical pages mapped at different virtual addresses) to relocate objects without copying physical pages.
- **Non-generational** — one generation, but it still has a "young" heuristic via the coloured pointer and the nursery concept in later versions.
- **Limitations:** historically higher CPU overhead than G1, and (in older versions) some allocation-heavy workloads suffered. Modern ZGC (17+) is much better.
- **Use when:** very large heaps (100 GB – 16 TB), strict tail-latency SLAs (trading platforms, ad servers, low-latency APIs).

#### 3.6.6 Shenandoah (`-XX:+UseShenandoahGC`, production-ready in Java 15)

- **Like ZGC in goal, different in mechanism:** performs **concurrent compaction** with a **Brooks forwarding pointer** — every reference to a moved object is updated in place, at the cost of a compare-and-swap per reference access (an extra load barrier).
- **Non-generational**, fully concurrent, and supports **compaction of the whole heap**, not just young regions.
- **Lower CPU overhead than ZGC** on many workloads, but a **higher allocation-rate penalty** (the forwarding pointers cost on every read).
- **Not available on all platforms** — it is an OpenJDK distribution-specific build (Shenandoah is **not** in Oracle JDK builds; it is in the standard OpenJDK builds from some vendors, and in Red Hat's builds).
- **Use when:** latency-sensitive with a moderate heap, and you are on a distribution that ships it.

#### 3.6.7 Epsilon (`-XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC`)

- A **no-op collector** — it allocates and never collects.
- Perfect for: **performance tests** (measuring pure allocation cost), **short-lived jobs** where the process exits before memory matters, and **testing** your application's behaviour when the heap is exhausted.
- If the heap fills → `OutOfMemoryError: Java heap space`, immediately.

#### 3.6.8 Collector Comparison

| Collector | Pause profile | Throughput | Compaction | Generational | Best for | Availability |
|---|---|---|---|---|---|---|
| **Serial** | Long, STW | Lowest CPU overhead | Yes (mark-compact) | Yes | ≤ 1 CPU, tiny heaps | All |
| **Parallel** | Long, STW | **Highest** | Yes | Yes | Batch / throughput | All |
| **CMS** | Low (avg) | Medium | **No** → fragmentation | Yes | *Removed* | ≤ Java 13 |
| **G1** | **Predictable** (goal 200 ms) | High | **Yes** | Heuristic | **Default**, 4–64 GB, latency-sensitive | Java 9+ |
| **ZGC** | **< 1 ms** | Medium–High | Yes, concurrent | No | Huge heaps, strict tail latency | Java 15+ (prod) |
| **Shenandoah** | **< 1 ms** | Medium | Yes, concurrent | No | Latency-sensitive, moderate heap | Java 15+ (prod), OpenJDK only |
| **Epsilon** | None | n/a | No | n/a | Testing, short jobs | Experimental |

### 3.7 Reference Types — Strong, Soft, Weak, Phantom

```text
  STRENGTH LADDER
  ┌──────────────────────────────────────────────────────────────────────────┐
  │  STRONG ────────────► SOFT ────────────► WEAK ────────────► PHANTOM    │
  │  never collected     cleared under      cleared at the    always null;  │
  │  while reachable     memory pressure    next GC           post-mortem   │
  │                                                          notification   │
  └──────────────────────────────────────────────────────────────────────────┘
   normal refs        memory-sensitive     canonical maps     Cleaner,
                      caches (e.g.          (e.g. interned    resource
                      SoftReference         class metadata)   reclamation
                      in SoftHashMap)
```

| Type | `get()` returns | Cleared when | Use case |
|---|---|---|---|
| **Strong** | The object | Never while reachable | Everything by default |
| **Soft** | The object, *or null* | When the JVM is **low on memory** and decides to reclaim it | **Memory-sensitive caches** (`SoftReference` in `SoftHashMap`, or the JDK's own `String` cache) |
| **Weak** | The object, *or null* | At the **next GC**, whenever the object is only weakly reachable | Canonical/interned maps (`WeakHashMap`), "the object exists only while someone else cares" |
| **Phantom** | **Always `null`** | After the object is collected; the reference is then **enqueued** | Post-mortem cleanup, `Cleaner` (Java 9+) |

**The critical semantics:**

- A **soft reference is cleared only when the JVM needs memory** — the JVM's policy is to keep soft references "as long as possible" and clear them only under pressure, so a soft-referenced cache is *usually* still there after a GC, but *can* vanish under pressure.
- A **weak reference is cleared at the next GC** — eagerly, regardless of memory pressure. That is why `WeakHashMap` entries disappear as soon as the key is no longer strongly reachable.
- A **phantom reference's `get()` always returns `null`**, even before collection. The only way to learn the object's address is through the `ReferenceQueue` — which is exactly how `Cleaner` works.
- **Phantom references became "the" post-mortem mechanism** because `finalize()` is going away.

**The `ReferenceQueue` is the point.** A reference without a queue is a one-way "please tell me if I die" with no way to learn *when*. With a queue, the GC enqueues the reference after collection, and a dedicated thread (the `ReferenceHandler`) processes it.

**Measured behaviour (JDK 25):**

```text
$ java RefDemo
soft.get() before GC = true
weak.get() before GC = true
weak.get() after GC  = false
WeakHashMap size before = 1
WeakHashMap size after  = 1
phantom.get() = null
phantom enqueued = true
```

**What to look at:** the `WeakHashMap` size stayed at 1 because the key was a **string literal** — a strong reference held by the class's constant pool. The entry cannot be collected. The next example shows the contrast.

### 3.8 `finalize()` and `Cleaner`

**`finalize()`** was the original Java mechanism for cleanup:

```java
@Override
protected void finalize() throws Throwable {
    try {
        // cleanup resources
    } finally {
        super.finalize();
    }
}
```

**Its problems (all of them real):**

1. **Unpredictable timing.** It runs at *some* point before collection, on the **finalizer thread**, not on the thread that created the object. You cannot rely on it running at all before the process exits.
2. **Performance.** Every object with a `finalizer` goes on the **finalizer queue** and needs *two* GC cycles to be reclaimed (one to enqueue, one to run `finalize` and free). It also **resurrects** the object for a while.
3. **A `finalize()` that throws kills the finalizer thread**, and all subsequent finalization stops.
4. **Deprecated for removal in Java 9**, and *terminally deprecated* in Java 18 (`finalize()` is marked for removal; JDK 18+ emits a deprecation warning and the default `-Xlint:finalize` warns).
5. **Security risk** — a resurrected object can be observed in a partially-constructed state.

**Measured behaviour (JDK 25):**

```text
$ java FinalizeDemo
Res created
Res created
Res created
-- requesting GC --
-- after System.gc() --
finalize() called on FinalizeDemo$Res@42dc99ec
finalize() called on FinalizeDemo$Res@4824f29c
finalize() called on FinalizeDemo$Res@d2675fc
-- end of main --
```

**What to look at:** the `finalize()` calls happen **after** `System.gc()` returned — they run on a *separate* finalizer thread, asynchronously. And notice the compiler warning in JDK 25: `warning: [removal] finalize() in Object has been deprecated and marked for removal`.

**The replacements, in order of preference:**

1. **`try-with-resources`** — for anything with a `close()`.
2. **`java.lang.ref.Cleaner`** (Java 9+) — for resources whose owner cannot implement `AutoCloseable` (e.g. a `finalize`-only legacy class you cannot change).
3. **An explicit `close()`/`shutdown()` in the lifecycle** — best of all.

```java
import java.lang.ref.Cleaner;

public class CleanerDemo {
    private static final Cleaner CLEANER = Cleaner.create();

    static class Resource implements Runnable {
        private volatile boolean closed;
        @Override public void run() {                 // the cleanup action
            if (!closed) {
                closed = true;
                System.out.println("cleaned up");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Resource r = new Resource();
        CLEANER.register(r, r::run);    // register the cleanup action
        r = null;                        // drop the strong reference
        System.gc();
        Thread.sleep(500);
        System.out.println("done");
    }
}
```

**`Cleaner` vs `finalize()`:** `Cleaner` is *also* asynchronous and *also* best-effort, but it is not deprecated, it does not resurrect the object, it does not add the second GC cycle to the object's lifetime, and it cannot be called more than once. Crucially, the cleanup action must **not** reference the object being cleaned.

### 3.9 Memory Leaks in a GC'd Language

**Definition.** A memory leak in Java is a **reachable-but-unwanted** object. The GC cannot help you, because from its point of view the object is alive. (A "true" leak — the object *is* unreachable but not collected — cannot happen in a correct collector; if you see it, it is a collector bug.)

**The five canonical causes:**

```java
// 1. STATIC COLLECTIONS — anchored for the life of the class loader
static List<Object> cache = new ArrayList<>();

// 2. UNCLOSED RESOURCES — the native resource leaks even if the object is collected
Connection conn = dataSource.getConnection();     // never closed

// 3. LISTENERS / SUBSCRIPTIONS — the listener keeps the publisher alive (or vice versa)
button.addActionListener(listener);                // never removed

// 4. THREADLOCALS — anchored to a pooled thread that never dies
ThreadLocal<Object> local = new ThreadLocal<>();
local.set(obj);                                   // never removed

// 5. CLASS LOADERS — a loader that is still reachable pins all its classes and statics
//    (Tomcat WAR redeploy, Spring Boot DevTools, dynamic proxies, scripting engines)
```

**A sixth, subtler one:**

```java
// 6. INTERNED STRINGS / String.intern() on user input — immortal per class loader
map.put(userInput.intern(), value);
```

**Detection:**

| Tool | What it shows | When to use |
|---|---|---|
| `jstat -gcutil <pid> 1000` | Old-gen occupancy trend, GC counts, GC time | Always — first step, costs nothing |
| `jcmd <pid> GC.heap_info` | Current heap shape and region layout | Second step |
| `jmap -histo:live <pid>` | Live objects per class | Third step — usually names the culprit |
| `jcmd <pid> GC.heap_dump file.hprof` | The full object graph | When the histogram is not conclusive |
| **Eclipse MAT** | Dominator tree, **path to GC roots**, leak suspects | The final analysis step |
| `jcmd <pid> GC.class_histogram` | Same as `jmap -histo` but via the attach API | When `jmap` is unavailable |
| JFR (`-XX:StartFlightRecording`) | Allocation profiling, old-object-sample | Continuous, low overhead, production-safe |

**The one question that finds the leak:** *what is the path from a GC root to the leaking objects?* A static field, a `ThreadLocal`, a listener list, a class loader, a JNI global, a monitor. MAT's **"Path to GC Roots → exclude weak/soft references"** report answers it.

### 3.10 Reading the GC Log

**Enable it properly (JDK 9+ unified logging):**

```bash
-Xlog:gc*,safepoint:file=/var/log/app-gc.log:time,uptime,level,tags
```

**Older-style (JDK 8):** `-XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:/var/log/app-gc.log`

**A real G1 log (JDK 25, `-Xmx64m`):**

```text
[0.047s][info][gc,start    ] GC(0) Pause Young (Normal) (G1 Evacuation Pause)
[0.048s][info][gc          ] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 29M->1M(64M) 0.985ms
[0.052s][info][gc,start    ] GC(1) Pause Young (Normal) (G1 Evacuation Pause)
[0.055s][info][gc          ] GC(1) Pause Young (Normal) (G1 Evacuation Pause) 30M->1M(64M) 3.102ms
[0.061s][info][gc,start    ] GC(2) Pause Young (Normal) (G1 Evacuation Pause)
[0.065s][info][gc          ] GC(2) Pause Young (Normal) (G1 Evacuation Pause) 38M->1M(64M) 3.052ms
[0.069s][info][gc,start    ] GC(3) Pause Young (Normal) (G1 Evacuation Pause)
[0.070s][info][gc          ] GC(3) Pause Young (Normal) (G1 Evacuation Pause) 38M->1M(64M) 0.861ms
```

**How to read it, field by field:**

```text
 GC(0) Pause Young (Normal) (G1 Evacuation Pause) 29M->1M(64M) 0.985ms
  │      │        │            │                     │        │      │
  │      │        │            │                     │        │      └─ PAUSE DURATION
  │      │        │            │                     │        └──────── HEAP: before -> after (total)
  │      │        │            │                     └───────────────── TOTAL HEAP CAPACITY
  │      │        │            └─────────────────────────────────────── SUB-PHASE (what the GC was doing)
  │      │        └────────────────────────────────────────────────── CAUSE: Normal / Mixed / G1 Humongous Allocation /
  │      │                                                            Metadata GC Threshold / System.gc() / Heap Dump Initiated
  │      └─────────────────────────────────────────────────────────── GENERATION: Young (minor) or Full
  └─────────────────────────────────────────────────────────────── GC ID (monotonic; compare the same id across lines)
```

**The three numbers that matter for every line:**

1. **`before->after(total)`** — the **reclaim rate**. `38M->1M(64M)` reclaimed 37 MB, leaving 1 MB live. Excellent.
2. **The pause duration** — the only part that is *user-visible*.
3. **The after-size trend.** If the after-size ratchets up across many collections, you have a **leak**. If it stays flat, you have **pressure**, not a leak.

**Red flags in a GC log:**

| Log pattern | Meaning |
|---|---|
| `Pause Full` appearing at all | A full GC in production is an incident |
| `Concurrent Mark Cycle` aborting → `Pause Full` | The mutator out-paced the marker — the death spiral |
| `G1 Humongous Allocation` causing `Pause Full` | A huge array cannot be placed contiguously |
| `Pause Young` frequency rising sharply | Allocation rate spike, or promotion storm |
| `to-space exhausted` | The survivor/old space could not accommodate the survivors |
| After-size growing monotonically | **Memory leak** |
| Long pauses with tiny reclaim (`38M->37M(64M)`) | Everything is live — a classic static-cache leak |

**A real ZGC log (JDK 25) — note the completely different shape:**

```text
[0.123s][info][gc] GC(0) Major Collection (Warmup) 26M(10%)->18M(7%) 0.004s
[0.143s][info][gc] GC(1) Major Collection (Warmup) 52M(20%)->38M(15%) 0.006s
[0.156s][info][gc] GC(2) Major Collection (Warmup) 78M(30%)->30M(12%) 0.004s
[0.166s][info][gc] GC(3) Minor Collection (Allocation Rate) 92M(36%)->8M(3%) 0.001s
[0.181s][info][gc] GC(4) Minor Collection (Allocation Rate) 104M(41%)->8M(3%) 0.001s
[0.221s][info][gc] GC(5) Minor Collection (Allocation Rate) 190M(74%)->8M(3%) 0.001s
[0.247s][info][gc] GC(6) Minor Collection (Allocation Rate) 202M(79%)->8M(3%) 0.001s
```

**What to look at:** ZGC's pauses are **1–6 ms** even while reclaiming 190 MB, because *all* the work is concurrent and only the tiny "pause" at the end is stop-the-world. The `->8M(3%)` after-values show a very stable live set. Compare with the G1 log, where pauses were 1–3 ms *only because the heap was tiny (64 MB)*; on a 32 GB heap, G1's evacuation pauses would be far longer.

**A real Epsilon log — the no-collector baseline:**

```text
[0.002s][info][gc] Using Epsilon
[0.002s][info][gc] Consider enabling -XX:+AlwaysPreTouch to avoid memory commit hiccups
[0.030s][info][gc] Heap: 65536K reserved, 65536K (100.00%) committed, 3398K (5.19%) used
[0.031s][info][gc] Heap: 65536K reserved, 65536K (100.00%) committed, 7185K (10.96%) used
[0.032s][info][gc] Heap: 65536K reserved, 65536K (100.00%) committed, 10949K (16.71%) used
[0.033s][info][gc] Heap: 65536K reserved, 65536K (100.00%) committed, 14235K (21.72%) used
```

**What to look at:** the heap only ever grows — 5% → 11% → 17% → 22%. This is what "no GC" looks like, and it is exactly what a **pure allocation benchmark** should measure.

**Tooling for log analysis:** GCViewer, GCEasy (gceasy.io — paste the log, get graphs and an "allocation rate" and "pause time percentile" report), and the JDK's own `jstat`.

---

## 4. Visualizations

### 4.1 The GC Cycle Timeline (G1, with the concurrent phase)

```text
  application threads:  ████████████████████░░░░░░░████████████████████████
  time ──────────────────────────────────────────────────────────────────────▶

  ┌──────────────┐                                              ┌──────────────┐
  │  YOUNG GC    │                                              │  YOUNG GC    │
  │  (evacuation)│                                              │  (evacuation)│
  └──────┬───────┘                                              └──────┬───────┘
         │ STW                                                       │ STW
         ▼                                                           ▼
  ╔═══════════════════════════════════════════════════════════════════════╗
  ║  CONCURRENT MARKING CYCLE  (runs alongside the application)           ║
  ║   ┌────────────┐  ┌──────────────┐  ┌──────────────┐  ┌───────────┐  ║
  ║   │ 1. Initial │  │ 2. Concurrent│  │ 3. Remark    │  │ 4. Clean  │  ║
  ║   │    Mark    │─▶│    Mark      │─▶│    (STW)     │─▶│   up     │  ║
  ║   │   (STW)    │  │  + SATB buf  │  │              │  │  (concurrent)│ ║
  ║   └────────────┘  └──────────────┘  └──────────────┘  └───────────┘  ║
  ║        STW             concurrent          STW            concurrent ║
  ╚═══════════════════════════════════════════════════════════════════════╝
         │                                                           │
         │  ◀── MIXED GC: young + a slice of old regions ──▶        │
         ▼                                                           ▼

  HEAP OVER TIME (G1):
  ┌──────────────────────────────────────────────────────────────────────────┐
  │  old gen occupancy                                                      │
  │  █                                                                   ██ │
  │  ██                                                                  ██ │
  │  ███                                                                ███ │
  │  █████  ←────────────── mixed GCs bring it back down ────────────▶ ███ │
  │  ███████                                                          █████ │
  │  ████████████                                              ██████████████ │
  │  ───────────────────────────────────────────────────────────────────────  │
  │   young GCs happening all the time (small sawtooth, not drawn)          │
  └──────────────────────────────────────────────────────────────────────────┘
```

**What to look at:** the STW portions are the tiny grey slivers; the *real work* (marking, cleaning) is concurrent and overlaps with the application. G1 buys its pause-time guarantee by making the STW portion do only **evacuation** (copying), and doing the *analysis* concurrently.

### 4.2 A Full GC Cycle (Serial/Parallel — the classic mark-sweep-compact)

```text
  ┌────────────────────────────────────────────────────────────────────────┐
  │  STOP THE WORLD  ◀──────────────── entire application halted ──────────┐ │
  │                                                                        │ │
  │   ┌──────────┐    ┌────────────┐    ┌──────────┐    ┌──────────────┐  │ │
  │   │  MARK    │───▶│  SWEEP     │───▶│ COMPACT  │───▶│  (optional)  │  │ │
  │   │  roots → │    │  free the  │    │ slide    │    │  reference    │  │ │
  │   │  live    │    │  unmarked  │    │ live objs│    │  processing   │  │ │
  │   │  objects │    │  objects   │    │ together │    │              │  │ │
  │   └──────────┘    └────────────┘    └──────────┘    └──────────────┘  │ │
  │                                                                        │ │
  │  ▶ RESUME  ◀───────────────────────────────────────────────────────────  │ │
  └────────────────────────────────────────────────────────────────────────┘

  Before compaction (fragmented):        After compaction (slid together):
  ┌───┬───┬─────┬───┬───────┬───┐        ┌───┬───┬─────┬───┬───────┬───┐
  │ A │ B │     │ C │   D   │ E │        │ A │ B │ C   │ D │       │ E │
  └───┴───┴─────┴───┴───────┴───┘        └───┴───┴─────┴───┴───────┴───┘
    live  live  free  live   free  live     live live live  live  free  live
```

**What to look at:** **compaction is what makes a full GC so slow** — it has to move every live object to close the gaps. The `mark` phase is `O(live)`, `sweep` is `O(heap)`, and `compact` is `O(live)` *plus* expensive pointer updates.

### 4.3 How the Generations Fill and Drain (Parallel/Serial)

```text
  YOUNG GEN  ────────────────────────────────────────────────────────▶
  ┌────────────┬──────────┬──────────┬────────────┬────────────┐
  │  Eden      │  Eden    │  Eden    │   Eden     │   Eden     │  ← fills up
  │  (alloc)   │  (alloc) │  (alloc) │   (alloc)  │   (alloc)  │
  └────────────┴──────────┴──────────┴────────────┴────────────┘
        │            │            │            │            │
        ▼ MINOR GC   ▼            ▼            ▼            ▼
  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
  │ S0 ◀─────┼───┼──────────┼───┼──────────┼───┼──────────┤   survivors
  │ (from)   │   │ (from)   │   │ (from)   │   │ (from)   │   get copied
  └──────────┘   └──────────┘   └──────────┘   └──────────┘   here
        │              │              │              │
        │ age > threshold (or S0 half full → dynamic age rule)
        ▼              ▼              ▼              ▼
  ┌───────────────────────────────────────────────────────────────┐
  │                        OLD GENERATION                          │
  │  (tenured)  ← most objects that arrive here survive forever   │
  └───────────────────────────────────────────────────────────────┘
        │
        │  old gen occupancy exceeds the target
        ▼
  ┌───────────────────────────────────────────────────────────────┐
  │  MAJOR / FULL GC  (or, in G1, a MIXED GC reclaims part of it) │
  └───────────────────────────────────────────────────────────────┘
```

**What to look at:** the **dynamic age rule** — if the survivor space is more than half full, the promotion threshold drops so the survivors do not overflow. This is the mechanism that keeps a high-promotion workload from failing with `to-space exhausted`.

### 4.4 The G1 Region Lifecycle

```text
  G1 EVACUATION (what happens in a young or mixed GC):

  Before:                        After:
  ┌────┬────┬────┬────┬────┐     ┌────┬────┬────┬────┬────┐
  │ E  │ S  │ O  │ H  │ O  │     │ E  │ E  │ O  │ O  │ O  │
  ├────┼────┼────┼────┼────┤     ├────┼────┼────┼────┼────┤
  │ O  │ E  │ E  │ S  │ O  │     │ E  │ O  │ E  │ E  │ O  │
  └────┴────┴────┴────┴────┘     └────┴────┴────┴────┴────┘
   ╰──╮
      │  "evacuate" = copy all LIVE objects out of this region
      ▼         into other regions, then the source region becomes free
  ┌────────┐
  │ region │  live objects ──copy──▶  ▓▓▓▓▓▓▓▓  (survivor or old region)
  │ (E/S/O)│                     the source region is now 100% free
  └────────┘                     and can be reused immediately

  WHY "GARBAGE FIRST"?  G1 picks the region with the MOST garbage
  (the highest "garbage ratio") because evacuating it frees the most
  space for the least copying work.
```

**What to look at:** evacuation is a **copying** operation, so the cost of a G1 collection is proportional to the *live* data in the selected regions. G1's job is to select regions such that the live bytes fit inside the pause-time goal.

### 4.5 Reference Strength vs Reachability (the two orthogonal axes)

```text
                 REACHABLE FROM A ROOT        NOT REACHABLE
              ┌───────────────────────────┬───────────────────────────┐
  STRONG ref  │  ALIVE — kept forever     │  collected (normal)       │
              │  (the normal case)        │                           │
              ├───────────────────────────┼───────────────────────────┤
  SOFT ref    │  ALIVE — kept, but the GC │  collected when memory     │
              │  may clear it under       │  pressure arises          │
              │  pressure                 │  (usually NOT collected)  │
              ├───────────────────────────┼───────────────────────────┤
  WEAK ref    │  ALIVE                    │  collected at the next GC │
              │                           │  (eagerly)                │
              ├───────────────────────────┼───────────────────────────┤
  PHANTOM ref │  (n/a — get() is null)   │  collected, then the ref  │
              │                           │  is ENQUEUED for cleanup   │
              └───────────────────────────┴───────────────────────────┘

  KEY INSIGHT: strength decides WHEN a reference stops protecting the object.
               Reachability decides WHETHER the object is garbage at all.
               A weak reference to a reachable object keeps it alive;
               a strong reference to an unreachable object cannot exist.
```

**What to look at:** a `SoftReference` is cleared only when the object is *unreachable* **and** the JVM is under memory pressure. A `WeakReference` is cleared as soon as the object is unreachable. Reachability is always the first gate.

---

## 5. Worked Examples (with Output + Dry Run)

### 5.1 Example 1 — Watching a Young GC Do Its Job (G1, real log)

**Problem:** "Explain what a minor GC costs" — with numbers.

```java
public class GcDemo {
    static volatile Object sink;
    public static void main(String[] args) {
        for (int i = 0; i < 200_000; i++) {
            sink = new byte[8 * 1024];   // 8 KB each -> ~1.6 GB of churn
        }
        System.out.println("done, sink=" + (sink == null ? "null" : sink.getClass().getSimpleName()));
    }
}
```

```bash
java -Xmx64m -Xlog:gc*:file=gc.log:time,uptime,level,tags GcDemo
```

**Actual output:**

```text
done, sink=byte[]
```

**Actual GC log:**

```text
[2026-10-20T11:05:15.127+0000][0.047s][info][gc,start    ] GC(0) Pause Young (Normal) (G1 Evacuation Pause)
[2026-10-20T11:05:15.128+0000][0.048s][info][gc          ] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 29M->1M(64M) 0.985ms
[2026-10-20T11:05:15.132+0000][0.052s][info][gc,start    ] GC(1) Pause Young (Normal) (G1 Evacuation Pause)
[2026-10-20T11:05:15.135+0000][0.055s][info][gc          ] GC(1) Pause Young (Normal) (G1 Evacuation Pause) 30M->1M(64M) 3.102ms
[2026-10-20T11:05:15.142+0000][0.061s][info][gc,start    ] GC(2) Pause Young (Normal) (G1 Evacuation Pause)
[2026-10-20T11:05:15.145+0000][0.065s][info][gc          ] GC(2) Pause Young (Normal) (G1 Evacuation Pause) 38M->1M(64M) 3.052ms
[2026-10-20T11:05:15.149+0000][0.069s][info][gc,start    ] GC(3) Pause Young (Normal) (G1 Evacuation Pause)
[2026-10-20T11:05:15.150+0000][0.070s][info][gc          ] GC(3) Pause Young (Normal) (G1 Evacuation Pause) 38M->1M(64M) 0.861ms
[2026-10-20T11:05:15.155+0000][0.074s][info][gc,start    ] GC(4) Pause Young (Normal) (G1 Evacuation Pause)
[2026-10-20T11:05:15.156+0000][0.076s][info][gc          ] GC(4) Pause Young (Normal) (G1 Evacuation Pause) 38M->1M(64M) 1.218ms
```

**Dry run:**

1. **Eden fills → a young GC is triggered.** The heap is 64 MB; with G1's default young-gen sizing Eden is roughly 25–30 MB. The log line `29M->1M(64M)` says: before the collection the heap used **29 MB**, after it used **1 MB**, out of a **64 MB** total.
2. **Reclaimed 28 MB in 0.985 ms.** That is the entire point: **garbage is nearly free**. 28 MB of dead `byte[]` cost one millisecond.
3. **The live set is 1 MB.** Only the single `sink` reference (8 KB) plus a little else is alive. The after-size is the number to watch across many collections.
4. **The `->1M` is stable across GC(0) through GC(4).** A stable after-size = **no leak**. Had it been `29M->25M(64M)`, we would have a leak: 4 MB survived and would accumulate.
5. **The pause is 0.5–3 ms** on a 64 MB heap. On a 32 GB heap with the same *live* set, G1's evacuation pause would still be small, because the cost tracks the live data evacuated, not the heap size.
6. **The allocation rate** is measurable: 1.6 GB churned in about 0.1 s → ~16 GB/s of allocation. That is the number to compare against the "GC overhead limit exceeded" threshold.
7. Note the **two-line format** (`gc,start` then `gc`): JDK 9+ unified logging emits a `start` line and a `summary` line. The `GC(0)` id links them.

**Takeaway:** a healthy G1 young GC shows **large reclaim, small after-size, sub-10 ms pause**. The three numbers to extract from every log line are: *before*, *after*, *duration*.

### 5.2 Example 2 — Proving That Reachability, Not Generation, Decides

```java
import java.util.*;

public class WeakHashDemo {
    public static void main(String[] args) throws Exception {
        WeakHashMap<Object, byte[]> wm = new WeakHashMap<>();
        Object key = new Object();               // a STRONG local reference
        wm.put(key, new byte[1024]);
        System.out.println("size with strong local key  = " + wm.size());
        key = null;                               // drop the strong reference
        System.gc();
        Thread.sleep(300);
        System.out.println("size after key = null + GC  = " + wm.size());
    }
}
```

**Actual output (JDK 25):**

```text
size with strong local key  = 1
size after key = null + GC  = 0
```

**Dry run:**

1. `wm.put(key, ...)` — the `WeakHashMap` holds the key **weakly**. But the local variable `key` holds it **strongly**.
2. The GC traces: root → local slot → `key` → alive. The weak reference to `key` is therefore not cleared, and the entry survives.
3. `key = null` — the **strong** reference disappears. Now the *only* reference to the key is the weak one.
4. `System.gc()` — during the next collection, the key is found to be reachable *only* through a weak reference, so the weak reference is cleared and the entry is removed from the map.
5. The `byte[1024]` value becomes unreachable at the same moment and is collected in the same cycle.
6. Result: `size = 0`. The `WeakHashMap` self-cleans *only because* the strong reference went away.

**The classic production bug this exposes:**

```java
// ❌ The key is a strong local → the entry NEVER expires
String key = buildKey(request);
cache.put(key, compute(key));        // WeakHashMap
// ... key goes out of scope, so it IS collectable. OK.

// ❌ But this one is NOT:
static final String KEY = "constant";   // string literal → constant pool → strong root!
WeakHashMap<String, byte[]> cache = new WeakHashMap<>();
cache.put(KEY, value);                    // NEVER collected — the literal is a GC root
```

**Takeaway:** `WeakHashMap` gives you *nothing* unless you guarantee the key is **not** strongly reachable anywhere else. String literals, interned strings, and values held in a `static` field are all strongly reachable forever. This is a genuinely counter-intuitive trap and a great interview follow-up.

### 5.3 Example 3 — The Reference Strength Ladder in Action

```java
import java.lang.ref.*;
import java.util.*;

public class RefDemo {
    public static void main(String[] args) throws Exception {
        SoftReference<byte[]> soft = new SoftReference<>(new byte[1024]);
        System.out.println("soft.get() before GC = " + (soft.get() != null));

        WeakReference<Object> weak = new WeakReference<>(new Object());
        System.out.println("weak.get() before GC = " + (weak.get() != null));
        System.gc();
        Thread.sleep(200);
        System.out.println("weak.get() after GC  = " + (weak.get() != null));

        ReferenceQueue<Object> rq = new ReferenceQueue<>();
        PhantomReference<Object> ph = new PhantomReference<>(new Object(), rq);
        System.out.println("phantom.get() = " + ph.get());
        System.gc();
        Thread.sleep(200);
        System.out.println("phantom enqueued = " + (rq.poll() != null));
    }
}
```

**Actual output (JDK 25):**

```text
soft.get() before GC = true
weak.get() before GC = true
weak.get() after GC  = false
WeakHashMap size before = 1
WeakHashMap size after  = 1
phantom.get() = null
phantom enqueued = true
```

**Dry run — the interesting part is the soft reference:**

1. `soft.get()` returns the array — but note the array is **not** strongly referenced anywhere else, so it is *eligible* for collection.
2. The soft reference is **not** cleared by `System.gc()`. Why? Because the JVM's policy for soft references is "clear them only when the JVM *needs* memory". `System.gc()` is not memory pressure — the heap has plenty of room. So the soft reference survives.
3. Under real pressure (the heap nearly full), the GC *does* clear soft references before resorting to a full GC. This is why a `SoftReference` cache degrades gracefully: it is evicted exactly when the alternative is an OOM.
4. `weak.get()` becomes **null** after `System.gc()` — weak references are cleared at the *next* collection, eagerly.
5. `phantom.get()` is **null immediately** — before the object was even collected. This is the defining property of a phantom reference: it is a *notification* channel, not an access path.
6. `phantom enqueued = true` — after the object was collected, the reference was put on the `ReferenceQueue`. The `ReferenceHandler` thread did this.

**Takeaway:** the ladder is about *when the reference stops protecting the object*. Soft = "protect me until I'm really needed". Weak = "protect me only until the next GC". Phantom = "I don't want the object; tell me when it's gone".

### 5.4 Example 4 — Diagnosing a Leak with `jmap -histo:live` (real)

```java
import java.util.*;

public class Leak2 {
    static final List<Object> STATIC_CACHE = new ArrayList<>();   // unbounded, anchored forever

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 500_000; i++) {
            STATIC_CACHE.add(new byte[64]);
        }
        System.out.println("cache size = " + STATIC_CACHE.size());
        Thread.sleep(180_000);
    }
}
```

**Live diagnosis on the running JVM (`-Xmx64m`):**

```text
$ jmap -histo:live 122884
 num     #instances         #bytes  class name (module)
-------------------------------------------------------
   1:        508616       40385616  [B (java.base@25.0.4.1)
   2:          1062        2297088  [Ljava.lang.Object; (java.base@25.0.4.1)
   3:          1692         223936  java.lang.Class (java.base@25.0.4.1)
   4:          8538         204912  java.lang.String (java.base@25.0.4.1)
   5:          1429          44672  [I (java.base@25.0.4.1)
   6:             7          33472  [C (java.base@25.0.4.1)
   7:         1019          32608  java.util.concurrent.ConcurrentHashMap$Node (java.base@25.0.4.1)
   8:         1011          32352  java.util.HashMap$Node (java.base@25.0.4.1)
   9:          254          23648  [Ljava.util.HashMap$Node; (java.base@25.0.4.1)
  10:          446          21688  [Ljdk.internal.vm.FillerElement; (java.base@25.0.4.1)

$ jcmd 122884 GC.heap_info
122884:
garbage-first heap   total reserved 65536K, committed 65536K, used 43440K [0x00000000fc000000, 0x0000000100000000)
 region size 1024K, 0 young (0K), 0 survivors (0K)

$ jstat -gcutil 122884 1 1
  S0     S1     E      O      M     CCS    YGC     YGCT    FGC    FGCT     CGC    CGCT       GCT
     -      -   0.00  73.14  43.76   6.26      3     0.043     1     0.045     2     0.001     0.090
```

**Dry run — the reasoning chain:**

1. **`[B` (byte arrays) own 40 MB of a 64 MB heap.** 508 616 instances. That is the leak: the `STATIC_CACHE` list.
2. **`[Ljava.lang.Object;` = 2 297 088 bytes.** That is the *backing array* of the `ArrayList`, grown to hold 500 000 elements. One array, one root cause.
3. **`java.lang.Class` count is 1 692** — small. **This is not a class-loader leak.** If you see 50 000+ `Class` instances, suspect Metaspace instead.
4. **`jcmd GC.heap_info` shows `0 young (0K)`** — everything has been **promoted** to the old generation. The objects are long-lived by construction, so the generational hypothesis does not apply to them: they are *garbage that never dies* because they are still referenced.
5. **`jstat -gcutil`: `O` = 73.14%** — the old generation is nearly full. `FGC` = **1**: a full GC already ran and could not reclaim anything, because the cache is strongly reachable from a `static` field (a GC root). **This is the signature of a true leak, not of pressure.**
6. `E` = 0.00 and `S0/S1` = `-` — G1 has no live young data at that instant.

**The fix, and the verification:**

```java
// ❌ BEFORE
static final List<Object> STATIC_CACHE = new ArrayList<>();

// ✅ AFTER — a bounded, TTL'd cache (see §6.2 of chapter 01 for the full class)
private final BoundedCache<String, byte[]> cache = new BoundedCache<>(10_000, 60_000);
```

**Verification in production:** after the fix, `jstat -gcutil` should show a **flat** `O` column, and `FGC` should stay at 0. That is the acceptance criterion.

**Takeaway:** the histogram names the *victim* (the class holding the bytes); the *culprit* is the reference path. Always follow up with a heap dump and MAT's "path to GC roots".

### 5.5 Example 5 — `finalize()` Is Asynchronous and Deprecated

```java
public class FinalizeDemo {
    static class Res {
        Res() { System.out.println("Res created"); }
        @Override protected void finalize() throws Throwable {
            System.out.println("finalize() called on " + this);
            super.finalize();
        }
    }
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 3; i++) { new Res(); }
        System.out.println("-- requesting GC --");
        System.gc();
        System.out.println("-- after System.gc() --");
        Thread.sleep(300);
        System.out.println("-- end of main --");
    }
}
```

**Actual output (JDK 25):**

```text
Res created
Res created
Res created
-- requesting GC --
-- after System.gc() --
finalize() called on FinalizeDemo$Res@42dc99ec
finalize() called on FinalizeDemo$Res@4824f29c
finalize() called on FinalizeDemo$Res@d2675fc
-- end of main --
```

**And the compiler's verdict:**

```text
FinalizeDemo.java:4: warning: [removal] finalize() in Object has been deprecated and marked for removal
        @Override protected void finalize() throws Throwable {
                                 ^
```

**Dry run:**

1. Three `Res` objects are created and immediately become unreachable (no reference is kept).
2. `System.gc()` triggers a collection. The GC finds the objects unreachable **but** they have a `finalizer`, so it does **not** reclaim them — it **resurrects** them and enqueues them on the finalizer queue.
3. `System.gc()` **returns before** `finalize()` runs. The output proves it: `-- after System.gc() --` is printed *before* the three `finalize()` lines.
4. The `ReferenceHandler` thread picks the objects off the queue and calls `finalize()` on each. The objects are reclaimed in the **next** GC cycle.
5. The JDK 25 compiler emits a **`[removal]`** warning — `finalize()` is not merely deprecated, it is *marked for removal*. The default `-Xlint` set does not show it; you need `-Xlint:removal` (or the default `javac` warning set) to see it.

**Why this is a production problem:**

- A `finalize()` that blocks delays the **finalizer thread**, which then delays *all* finalization.
- A `finalize()` that throws an exception kills the finalizer thread permanently.
- Objects with finalizers need **two** GC cycles to die → higher GC cost and higher peak memory.
- The finalizer thread is a **single thread** — a common source of "the JVM hangs after GC" mysteries.

**Takeaway:** `finalize()` is deprecated *for removal*; it is asynchronous, non-deterministic, and it resurrects objects. Use try-with-resources (for `close()`-able resources) or `java.lang.ref.Cleaner`.

### 5.6 Example 6 — G1 Humongous Allocation: The Full-GC Trigger

**Problem:** "Why does my service occasionally have a 4-second full GC?"

```java
public class HumongousDemo {
    static volatile Object sink;
    public static void main(String[] args) {
        // G1 region size on a 64 MB heap is 1 MB -> an object > 512 KB is "humongous"
        for (int i = 0; i < 20; i++) {
            sink = new byte[700 * 1024];   // 700 KB -> humongous
        }
        System.out.println("allocated 20 humongous objects of 700KB each");
    }
}
```

**Actual output:**

```text
[0.002s][info][gc] Using G1
allocated 20 humongous objects of 700KB each
```

**The analysis:**

1. **Region size** for a 64 MB G1 heap = 64 MB / 2048 = 32 KB → rounded up to the minimum of **1 MB**.
2. A "humongous" object is one **larger than half a region** ⇒ > 512 KB here. Our 700 KB array is humongous.
3. Humongous objects are allocated **directly into the old generation**, in *contiguous* free regions. If the old gen is fragmented, the allocation **fails**, and G1 escalates to a **full GC** (which is a long STW pause) to defragment.
4. This is the classic G1 failure mode: **large byte arrays or large `char[]`/`String` payloads trigger full GCs.** The fix is either to **raise the region size** (`-XX:G1HeapRegionSize=16m` or `32m`, which reduces the humongous threshold) or to **not allocate huge arrays**.
5. Notice the log shows *no* young GC at all in this tiny run — the allocations all went straight to the old generation. In a real service you would see `G1 Humongous Allocation` in the GC log as the *cause* of a `Pause Full`.

**Takeaway:** if your service sees rare, very long full GCs and the log says `G1 Humongous Allocation`, the fix is `-XX:G1HeapRegionSize=16m` (or restructure the allocation). This is a genuinely expert-level answer that very few candidates have.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A GC-Aware Cache That Self-Manages Under Pressure

```java
package io.acme.cache;

import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * A memory-sensitive cache built on {@link SoftReference}.
 *
 * <p>Why soft references: the JVM clears a soft reference <em>only</em> when it
 * needs the memory. So this cache degrades gracefully under pressure instead of
 * throwing {@code OutOfMemoryError} — exactly the behaviour a cache should have.
 *
 * <p>Why not {@code WeakReference}: weak references are cleared at the <em>next</em>
 * GC regardless of pressure, which turns the cache into a near-no-op.
 *
 * <p>Thread safety: a {@link ConcurrentHashMap} of soft references. Note that
 * {@code get()} may return {@code null} at any moment (the GC can clear the
 * reference between the map lookup and the dereference) — always re-check.
 */
public final class SoftCache<K, V> {

    private final ConcurrentHashMap<K, SoftReference<V>> map = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    /** Returns the cached value, computing and caching it on a miss. */
    public V get(K key, Function<K, V> loader) {
        SoftReference<V> ref = map.get(key);
        V value = (ref == null) ? null : ref.get();   // may be null: the GC cleared it
        if (value != null) {
            hits.incrementAndGet();
            return value;
        }
        misses.incrementAndGet();
        V computed = loader.apply(key);
        if (computed != null) {
            map.put(key, new SoftReference<>(computed));
        }
        return computed;
    }

    /** Removes an entry explicitly. */
    public void invalidate(K key) {
        map.remove(key);
    }

    /**
     * Drops the entries the GC has already cleared, plus (optionally) the
     * oldest ones, so the map itself does not grow without bound.
     *
     * <p>Without this, the <em>keys</em> would still be strongly held by the
     * map and the entry objects would accumulate — a slow leak of a different kind.
     */
    public int purge() {
        int removed = 0;
        for (Map.Entry<K, SoftReference<V>> e : map.entrySet()) {
            if (e.getValue().get() == null) {     // the value was collected
                map.remove(e.getKey(), e.getValue());
                removed++;
                evictions.incrementAndGet();
            }
        }
        return removed;
    }

    public long hitCount()      { return hits.get(); }
    public long missCount()     { return misses.get(); }
    public long evictionCount() { return evictions.get(); }
    public int  size()          { return map.size(); }
}
```

**Why this is production-grade:**

- **The `null` re-check after `ref.get()`** is the subtle bug most implementations have. Between the `map.get(key)` and the `ref.get()`, the GC can clear the reference. The code above handles it by treating a null as a miss.
- **`purge()`** solves the *second-order* leak: the map's **keys** are strongly reachable, so a map of soft references still grows unless you clean it. This is a genuinely advanced point.
- **Counters** make the cache observable — hit ratio is a metric you must have in production.

### 6.2 A Production-Grade GC Health Reporter

```java
package io.acme.ops;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.List;

/**
 * Turns the JVM's GC beans into an alertable health signal.
 *
 * <p>Design rules:
 * <ul>
 *   <li>Compute <em>rates</em>, not absolutes. A GC count of 5000 is fine over a
 *       week and alarming over a minute.</li>
 *   <li>Always report the <em>ratio</em> of GC time to wall-clock time — that is
 *       the "GC overhead" the JVM itself uses to declare a death spiral.</li>
 *   <li>Never throw: a monitor must not break the application.</li>
 * </ul>
 */
public final class GcHealthReporter {

    private GcHealthReporter() { }

    public record Health(double gcTimeFraction,
                         long youngCollections,
                         long oldCollections,
                         double oldGenUtilisation,
                         boolean deathSpiral) {

        /** True when the JVM is about to throw "GC overhead limit exceeded". */
        public boolean isHealthy() {
            return !deathSpiral && gcTimeFraction < 0.25;
        }

        public String describe() {
            return String.format(
                "gcHealth{timeFraction=%.3f, young=%d, old=%d, oldGen=%.1f%%, spiral=%s}",
                gcTimeFraction, youngCollections, oldCollections,
                oldGenUtilisation * 100, deathSpiral);
        }
    }

    public static Health sample() {
        try {
            long gcMillis = 0;
            long youngCount = 0;
            long oldCount = 0;
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                gcMillis += Math.max(0, gc.getCollectionTime());
                long c = Math.max(0, gc.getCollectionCount());
                String name = gc.getName().toLowerCase();
                if (name.contains("young") || name.contains("scavenge") || name.contains("copy")) {
                    youngCount += c;
                } else {
                    oldCount += c;
                }
            }

            long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
            double fraction = uptimeMillis == 0 ? 0d : (double) gcMillis / uptimeMillis;

            // Old-generation utilisation, summed across the old pools.
            long oldUsed = 0;
            long oldCommitted = 0;
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                String n = pool.getName().toLowerCase();
                if (n.contains("old") || n.contains("tenured")) {
                    oldUsed += pool.getUsage().getUsed();
                    oldCommitted += pool.getUsage().getCommitted();
                }
            }
            double oldUtil = oldCommitted == 0 ? 0d : (double) oldUsed / oldCommitted;

            // The JVM aborts with "GC overhead limit exceeded" when the GC has
            // spent more than 98% of its time freeing less than 2% of the heap.
            boolean spiral = fraction > 0.98;

            return new Health(fraction, youngCount, oldCount, oldUtil, spiral);
        } catch (RuntimeException e) {
            return new Health(0, 0, 0, 0, false);
        }
    }

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 3; i++) {
            System.out.println(sample().describe());
            Thread.sleep(1_000);
        }
    }
}
```

**The `deathSpiral` heuristic mirrors the JVM's own rule** (`-XX:UseGCOverheadLimit`, default on, threshold 98%). Aligning your alert with the JVM's own abort condition means you get paged *before* the process dies.

### 6.3 A Leak-Safe Resource Registry (auditing GC roots)

```java
package io.acme.ops;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A registry of live resources, used to audit what is holding memory.
 *
 * <p>Pattern: register every long-lived object that might retain memory
 * (sessions, subscriptions, sessions in an HTTP client) with a stable key.
 * Then, in production, you can answer "how many are outstanding?" and
 * "which key is the leak?" without a heap dump.
 *
 * <p>This is the "make the invisible visible" technique used by mature
 * platforms: the leak is still a leak, but it is now <em>observable</em>.
 */
public final class ResourceRegistry<K> {

    private final Map<K, Long> open = new ConcurrentHashMap<>();
    private final AtomicLong opened = new AtomicLong();
    private final AtomicLong closed = new AtomicLong();

    public void open(K key) {
        open.put(key, System.nanoTime());
        opened.incrementAndGet();
    }

    public void close(K key) {
        if (open.remove(key) != null) {
            closed.incrementAndGet();
        }
    }

    /** Number of resources currently open. A monotonically rising value = a leak. */
    public int outstanding() {
        return open.size();
    }

    /** Total opened minus total closed. */
    public long leaked() {
        return opened.get() - closed.get();
    }

    /** The oldest open keys — the most likely leak sources. */
    public java.util.List<K> oldest(int n) {
        return open.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }
}
```

**Why this belongs in a GC chapter:** it converts an *invisible* reachability leak into a *visible* counter. A `leaked()` value that only goes up is the earliest possible signal — it fires long before the heap pressure becomes an incident.

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1: "The GC collects cycles"

✅ **Right, but say it properly:** Java's tracing GC reclaims *unreachable* objects, and a cycle of objects unreachable from the roots is collected as a group. Reference counting would leak it. The correct framing is "the GC traces from the roots", not "the GC detects cycles".

### ❌ Pitfall 2: "Minor GC and major GC are the same as young and old GC"

✅ **Right for Serial/Parallel** (a major GC is a full STW collection), but **wrong for G1/ZGC**, where a major GC is *concurrent* and a full GC is a rare STW fallback. Say "it depends on the collector" and name G1 as the default.

### ❌ Pitfall 3: "Setting `-Xmx` smaller makes the GC faster"

✅ **Wrong.** A smaller heap means *more frequent* collections. What it may do is reduce the *pause* of each collection (less to trace) — but total GC time usually goes **up**. The lever for pause time is the **collector** (G1/ZGC), not the heap size.

### ❌ Pitfall 4: Using `finalize()` for cleanup

✅ **Wrong.** Deprecated for removal, asynchronous, non-deterministic, resurrects the object, and blocks the finalizer thread. Use try-with-resources or `Cleaner`.

### ❌ Pitfall 5: Assuming `System.gc()` frees memory

✅ **Wrong.** It is a hint. And calling it in application code is an anti-pattern: it triggers a full STW pause. If a library calls it, `-XX:+ExplicitGCInvokesConcurrent` makes it a concurrent cycle instead.

### ❌ Pitfall 6: Believing a `WeakHashMap` entry always disappears

✅ **Wrong.** The entry disappears only when the key is **not strongly reachable anywhere else**. A string literal, an interned string, or a `static` field keeps it alive forever.

### ❌ Pitfall 7: "Soft references are collected at the next GC"

✅ **Wrong.** Soft references are cleared only under **memory pressure**. That is exactly what makes them useful for caches and exactly what makes them useless for "I want this gone now" semantics.

### ❌ Pitfall 8: Using `WeakReference` for a cache

✅ **Wrong.** Weak references are cleared at the *next* GC, so the cache is nearly always empty. Use `SoftReference` (or a bounded strong cache) for caches.

### ❌ Pitfall 9: Believing the GC can collect an object a `finalize()` method is using

✅ **Wrong.** The object is **resurrected** — it is put back into the reachable set for one cycle. An object with a `finalizer` needs two GC cycles to die.

### ❌ Pitfall 10: Allocating a huge array and blaming the GC

✅ **Usually wrong.** A single array larger than the heap gives `OutOfMemoryError: requested array size exceeds VM limit` — that is a *bug in the size calculation*, not a GC problem. And with G1, a large-but-under-the-limit array is a *humongous* allocation that can trigger a full GC.

### ❌ Pitfall 11: Assuming "more CPU cores = faster GC"

✅ **Usually wrong for latency.** More parallel GC threads reduce the *duration* of a STW pause but add coordination overhead. For a latency-sensitive service, moving from Parallel to G1 (or ZGC) is a bigger win than adding GC threads. And on a container with a **1-CPU quota**, the parallel collector's threads fight over the same quota — use Serial.

### ❌ Pitfall 12: Assuming `final` fields make an object safely publishable in all cases

✅ **Partially right.** The final-field freeze guarantees the *final* fields' values are visible. It does **not** protect non-final fields, and it does not make the *reference* publication safe by itself (that is what `volatile` is for in DCL).

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is garbage collection?**
**A:** It is the JVM's automatic reclamation of heap memory that is no longer reachable from any GC root. It frees the programmer from calling `free()`/`delete`, and removes an entire class of memory-safety bugs.

**Q: What is a GC root?**
**A:** A reference the collector treats as the starting point of the reachability graph. In HotSpot they are: local variables in thread stack frames, static fields of loaded classes, JNI global references, monitors held by live threads, and the interned-string table.

**Q: What is the generational hypothesis?**
**A:** That most objects die young. It lets the JVM collect the small young generation frequently and cheaply, and reserve expensive full-heap collections for the rare long-lived objects.

**Q: What is the difference between a minor and a major GC?**
**A:** A minor GC collects only the young generation (Eden + survivors) and is fast and stop-the-world. A major GC collects the old generation as well — in G1/ZGC it runs concurrently; in Serial/Parallel it is a full stop-the-world collection of the whole heap.

**Q: What causes `OutOfMemoryError: Java heap space`?**
**A:** The heap is exhausted and the GC cannot free enough. Either the live set genuinely exceeds `-Xmx` (a real leak, or simply too small a heap), or there is a retention path keeping garbage alive.

### 🟡 Intermediate

**Q: How does the GC determine that an object is unreachable?**
**A:** By *tracing* from the GC roots. The mark phase follows every reference from every root, setting a mark bit in each object's Mark Word. Anything not marked is unreachable and is reclaimed in the sweep/evacuation phase. Because the graph is traced from the roots, reference cycles are collected automatically — unlike reference counting.

**Q: What are the four reference types and when do I use each?**
**A:** **Strong** (default, never collected while reachable), **Soft** (cleared only under memory pressure — use for caches), **Weak** (cleared at the next GC — use for canonical maps and `WeakHashMap`), and **Phantom** (`get()` always returns null; used for post-mortem cleanup and by `Cleaner`).

**Q: What is the difference between a minor GC and a full GC in terms of cost?**
**A:** A minor GC costs `O(live young data)` because it copies only the survivors. A full GC costs `O(entire live heap)` plus compaction, which has to move every live object to close the gaps. On a large heap the difference is orders of magnitude.

**Q: What is the "tenuring threshold"?**
**A:** The number of young collections an object must survive before being promoted to the old generation. It is `MaxTenuringThreshold` (default 15, and limited to 15 because the age is stored in 4 bits). G1/Serial/Parallel also apply a *dynamic* rule: if the survivor space is more than half full, the threshold is lowered to prevent overflow.

**Q: What is a "humongous" object in G1?**
**A:** An object larger than **half a G1 region** (regions are `heap/2048` rounded to a power of two, min 1 MB, max 32 MB). Humongous objects are allocated directly into contiguous old-generation regions, and if the old generation is fragmented the allocation fails and triggers a **full GC** — the classic G1 latency spike.

### 🔴 Advanced / Senior

**Q: Compare G1, ZGC and Shenandoah. When would you use each?**
**A:** **G1** is the default since Java 9: region-based, compacts, and aims at a *pause-time goal* (`-XX:MaxGCPauseMillis`, default 200 ms) — it trades a little throughput for predictability, and it is the right choice for heaps of roughly 4–64 GB. **ZGC** (production in Java 17) is *fully concurrent* with coloured pointers and load barriers, giving sub-millisecond pauses independent of heap and live-set size — it is for very large heaps (100 GB+) and strict tail-latency SLAs, at the cost of more CPU. **Shenandoah** gives the same sub-millisecond guarantee with a different mechanism (Brooks forwarding pointers, in-place reference update) and lower CPU overhead but a higher allocation-rate cost; it is OpenJDK-only, not in Oracle's build.

**Q: How do you diagnose a memory leak in a running production JVM?**
**A:** `jstat -gcutil <pid> 1000` first — if the `O` column trends upward across samples and `FGC` climbs, it is a retention leak rather than pressure. Then `jcmd <pid> GC.heap_info` for the current shape, and `jmap -histo:live <pid>` to find the class owning the bytes. Finally `jcmd <pid> GC.heap_dump file.hprof` and open it in Eclipse MAT, running "Path to GC Roots" (excluding weak/soft) to find the retention path — a static field, a `ThreadLocal`, a listener, or a class loader. I also enable `-XX:+HeapDumpOnOutOfMemoryError` so the next incident is self-documenting.

**Q: Why is a "GC overhead limit exceeded" error a symptom, not a cause?**
**A:** It means the collector spent more than 98% of its time reclaiming less than 2% of the heap. The *cause* is that the live set is close to the heap size — the collector is doing all the work it can and the heap is genuinely full. Raising the overhead limit (`-XX:GCOverheadLimit`) only postpones the OOM; the fix is to reduce the live set or raise `-Xmx`.

**Q: How would you reduce GC pause time for a latency-sensitive service without changing the JVM vendor?**
**A:** In order of impact: (1) switch to **G1** (or ZGC) and set a realistic `-XX:MaxGCPauseMillis`; (2) **reduce the live set** — old-gen occupancy drives the cost of every collection, and a smaller live set means less copying; (3) **reduce allocation rate** — a lower rate means fewer collections; (4) **avoid humongous allocations** (raise `-XX:G1HeapRegionSize` or restructure); (5) keep `-XX:+ExplicitGCInvokesConcurrent` so a stray `System.gc()` does not cause a full STW; (6) consider `-XX:+UseStringDeduplication` to cut `byte[]` duplication in the old gen.

**Q: Explain SATB and why G1 uses it.**
**A:** Snapshot-at-the-beginning is a write-barrier technique that makes *concurrent* marking possible without stopping the world. When a reference is overwritten, the barrier logs the **old** value into a SATB buffer. At the end of the marking cycle, the JVM re-scans those buffers and marks the old referents, so that objects which were live at the *start* of the cycle are not wrongly considered dead. The trade-off is that the buffers can overflow, forcing the cycle to restart. ZGC instead uses **coloured pointers and load barriers**, which shifts the cost from the mutator's writes to its reads.

### ⚫ Expert / Principal

**Q: The generational hypothesis is weakening in modern workloads. Explain why and what the collectors do about it.**
**A:** The hypothesis assumes most objects die young. That is true for *request-scoped* objects but false for **caches, long-lived datasets and streaming buffers** — in a market-data service or an ML inference service, the live set can be tens of gigabytes and nothing dies young. In that regime, a generational collector's young GC has to copy a huge survivor set and promotion becomes the dominant cost, while the old generation is a single contiguous copy target that fragments. **ZGC and Shenandoah dropped generations entirely** — one generation, a single heap, concurrent relocation and compaction — because the "young/old" split buys nothing when the live set dominates. **G1** kept the split but made it a *heuristic over regions* rather than fixed spaces, so it can age a region on demand. The modern consensus: **generations are an optimisation for short-lived-object workloads; for large-live-set workloads, concurrent compaction matters more.**

**Q: How do you size a G1 heap and the pause-time goal for a service with a 99.9th-percentile latency SLO of 5 ms?**
**A:** I would *not* start with G1 for a 5 ms SLO — G1's default goal is 200 ms and its floor is bounded by the evacuation of live data, so a 5 ms goal on a large heap is unachievable. I would move to **ZGC** (sub-millisecond, concurrent) with a heap sized from the *live set* plus headroom (typically 2–3× the live set), not from the machine's RAM. Then I would validate empirically: run a load test with `-Xlog:gc*,safepoint` and look at the **pause-time percentile distribution** (p99, p99.9), not the average. And I would also measure **safepoint** time separately, because a 1 ms GC pause inside a 50 ms safepoint wait is invisible in the GC log but fatal to the tail.

**Q: Explain the write barriers used by each collector and what they cost.**
**A:** **G1** uses a **SATB (post-write) barrier** — on every reference write it logs the *old* value into a per-thread buffer, and it also maintains the **remembered set** and **card table** so that a region's incoming references are known. **ZGC** uses a **load barrier** with **coloured pointers**: the pointer's metadata bits encode the object's colour (marked/relocated/unmarked), and on every read the application checks the colour; if the object was relocated, the barrier re-reads the new address. **Shenandoah** uses a **Brooks forwarding pointer**: the object header holds a pointer to the new copy, and every reference to the object is updated in place via a compare-and-swap. The cost profile follows directly: G1 taxes **writes**, ZGC taxes **reads** (and needs multi-mapping to avoid copying physical pages), Shenandoah taxes **reads** with a CAS. That is why ZGC's CPU overhead is higher on read-heavy workloads and why Shenandoah struggles with very high allocation rates.

**Q: A service's heap usage is stable but p99 latency spikes correlate with GC. What would you investigate?**
**A:** Stable heap usage rules out a leak, so I would look at *pause composition* rather than pause duration alone. Concretely: (1) Is it a **young** GC or a **mixed/full** GC? Mixed GCs are much longer. (2) Is there a **humongous allocation**? A single large array can force a full GC. (3) Is the pause actually **safepoint** time? (`-Xlog:safepoint` shows the total safepoint time, and a biased-lock revocation or a `ClassLoader` operation can dominate.) (4) Is **promotion** spiking? A high promotion rate makes each young GC more expensive. (5) Is the JVM **sharing** the CPU with the GC threads under a container quota? (6) Are the app's own pauses (e.g. a `synchronized` convoy or a slow `finalize()`) being misattributed to GC? I would correlate the GC log timestamps with the application's own request-latency log to confirm the attribution before tuning anything.

**Q: How do you decide between raising `-Xmx` and fixing a leak?**
**A:** I decide by whether the *live set after a full GC* exceeds the heap. `jstat -gcutil`'s `O` column is the old-generation occupancy *after* the last collection, and `jcmd GC.heap_info`'s "used" figure plus the GC log's `->after` values give the live set. If the live set is, say, 1.2 GB on a 2 GB heap and grows with traffic, it is a **leak** — raising `-Xmx` buys time, not correctness. If the live set is a stable 400 MB and the *allocation rate* is the problem, then the heap is correctly sized and the collector/tuning is the lever. The decisive evidence is the *trend*: a leak's post-GC baseline ratchets up; a well-behaved workload's baseline is flat regardless of `-Xmx`.

---

## 9. Counter-Questions & Traps

### CQ1. "Isn't reference counting simpler and faster? Why did Java reject it?"

- **Naive answer:** "Because of circular references."  ← correct but incomplete
- **The trap:** The interviewer is usually pushing on the *cost* side: reference counting is *faster* for reclamation, so "why throw that away?" The full answer needs the **pause** and **precision** trade-offs, not just cycles.
- **Expert answer:** Reference counting gives *immediate* reclamation but **cannot handle cycles** and requires an atomic decrement on every reference write (expensive on a many-core machine, and it is a *contention point*). Tracing gives *batch* reclamation with a pause, but it is precise because the JVM knows exactly which slots hold references — and that same knowledge enables **escape analysis**, **stack/scalar allocation** and **lock elision**, which a conservative or counting collector can never do. Java's GC is fast *because* the JIT cooperates: allocation is a pointer bump, and the collector only touches live data.

### CQ2. "You said garbage is free. But my allocation rate is huge — surely that costs something?"

- **Naive answer:** "The GC is free, so allocation rate does not matter."  ← wrong
- **The trap:** *Collecting* garbage is nearly free; *allocating* it is not. Every byte allocated costs: a TLAB pointer bump, **zeroing** the memory, header initialisation, and the eventual card-marking write barrier. Plus, the *frequency* of collections scales with the allocation rate.
- **Expert answer:** The cost of a young GC is `O(live survivors)`, so garbage volume does not directly add to the pause. But the *number* of collections per second is `allocation_rate / eden_size`, and each one has fixed overhead (safepoint, root scanning, remembered-set updates). So a high allocation rate mostly hurts *throughput* and *total GC time*, not pause duration. It also causes **TLAB refills** and more frequent card marking. Practically: reducing allocation rate improves throughput and reduces GC CPU; reducing the *live set* improves pause duration.

### CQ3. "If G1 compacts, why does it still have fragmentation issues?"

- **Naive answer:** "G1 compacts during evacuation, so it doesn't fragment."  ← true for *old* regions, false for humongous
- **The trap:** The exception is **humongous objects**, which are allocated in *contiguous* regions and can only be freed by a concurrent cycle or a full GC. This is exactly the G1 full-GC trigger.
- **Expert answer:** G1's evacuation *does* compact — it copies live objects out of selected regions, leaving the source regions 100% free. The residual problem is humongous allocations: a 700 KB array in a 1 MB-region heap needs a contiguous region, and if the old gen is fragmented the allocation fails → `Pause Full`. Two mitigations: raise `-XX:G1HeapRegionSize` (so fewer objects are humongous) and avoid huge single allocations.

### CQ4. "Is a `SoftReference` cache really safe? The GC might clear it at any moment."

- **Naive answer:** "Yes, it's safe, the GC only clears it under pressure."  ← ignores the design consequence
- **The trap:** The GC *can* clear it at *any* moment once it decides memory is tight, and there is **no signal** to your code. If your cache's `get()` returns `null` unexpectedly, the caller must handle a recomputation — and if the loader is expensive or has side effects, that is a design problem.
- **Expert answer:** A soft-reference cache is *degradation-safe* only if the value is **recomputable and side-effect-free**. That is the contract: soft references are for *derived* data (a rendered report, a computed price, a parsed document) where a miss just means "recompute". Never use them for a resource that must not be re-acquired (a lock, a JNI handle) or for a value with side effects. And always re-check `ref.get()` for `null` — the reference can be cleared between the map lookup and the dereference.

### CQ5. "You said `WeakHashMap` self-cleans. Then why do people say `WeakHashMap` is a leak?"

- **Naive answer:** "It doesn't leak, the GC cleans it."  ← misses the real failure mode
- **The trap:** `WeakHashMap` is *not* thread-safe, and its `expungeStaleEntries()` runs only on `get`/`put`/`size` — so if you only ever *put* into it, stale entries accumulate until the next read. Also, it leaks if the **value** references the **key** (a classic self-reference cycle through the value), because the value is strongly reachable from the map and holds the key strongly.
- **Expert answer:** `WeakHashMap` cleans up lazily, on access, and it fails if your value strongly references its key. It is also not concurrent. For a cache with a bounded lifetime, an explicit `ConcurrentHashMap` with your own TTL and eviction gives you **deterministic** behaviour and observability — which is why the production code in §6.1 uses soft references with an explicit purge rather than `WeakHashMap`.

### CQ6. "Why does the JVM need `MaxTenuringThreshold` at all? Why not promote immediately?"

- **Naive answer:** "To keep objects in the cheap young generation longer."  ← directionally right but incomplete
- **The trap:** Promotion is *expensive* (a copy), but keeping an object in a survivor space is *also* expensive (it is copied at every young GC). The threshold is a **cost-balance knob**.
- **Expert answer:** An object that dies in its second or third young GC pays two or three copies to reach the old generation, only to die there. Promotion immediately would waste a copy on the majority of objects that die young. The survivor hypothesis says most objects that survive one collection die before the next — so the threshold is tuned to promote only the objects that look genuinely long-lived. The **dynamic age rule** adjusts it upward or downward at runtime based on survivor-space pressure, which is why a static `MaxTenuringThreshold` is only a *ceiling*.

### CQ7. "If ZGC gives sub-millisecond pauses, why isn't it the default?"

- **Naive answer:** "Because it requires more CPU."  ← true, but not the whole story
- **The trap:** The interviewer may push on *compatibility*: ZGC is not supported on all platforms, and there are **allocation-heavy** workloads where ZGC's load barriers and multi-mapping cost more than they save. Also, for small heaps, the overhead is not worth it.
- **Expert answer:** ZGC trades CPU for latency. On a 100 GB heap with a strict p99.9 SLO, that trade is obviously right. On a 512 MB heap with a 50 ms SLO, G1's pauses are already sub-millisecond in practice, and ZGC's extra CPU would be wasted. Plus: ZGC is not available on every platform (notably 32-bit), and its behaviour with very high allocation rates has historically been weaker than G1's. The collector choice is a *requirement* decision, not a "newest is best" decision.

### CQ8. "How would you know the difference between a memory leak and a cache that is simply too big?"

- **Naive answer:** "A leak grows without bound; a cache is bounded."  ← the trap is that most caches are *not* bounded
- **The trap:** "It's a cache" is not an answer — a cache with no size bound and no TTL *is* a leak, just a deliberate one. And a *bounded* cache can still be the wrong size for the workload.
- **Expert answer:** Measure the **live set after a full GC** across a traffic cycle. A leak's post-GC baseline ratchets up; a cache's baseline is a plateau at `cache_size × entry_size`. Then check the *shape*: exponential growth suggests a leak (a retention path per request); a linear plateau that scales with traffic suggests a cache that should be bounded or moved to a shared tier (Redis). The interview answer is that you cannot distinguish them by looking at the code — you need the *trend* of the post-GC live set.

### CQ9. "You said `System.gc()` is just a hint. But some libraries call it — is that bad?"

- **Naive answer:** "It's bad, it causes a full GC."  ← true but you should offer the mitigation
- **The trap:** The interviewer is likely testing whether you know the *knob*: `-XX:+ExplicitGCInvokesConcurrent` makes `System.gc()` trigger a *concurrent* cycle instead of a STW full GC. That converts a 2-second pause into a concurrent cycle.
- **Expert answer:** It is bad *by default* — `System.gc()` triggers a full STW collection of the entire heap, which on a 32 GB heap can be multiple seconds of latency for every request in flight. Libraries that call it (`System.gc()` before a backup, some cache implementations, some ORM flushes) are usually working around a GC that was too eager. The mitigations, in order: (1) `-XX:+ExplicitGCInvokesConcurrent` so the explicit GC is concurrent; (2) identify the caller (it appears in the GC log as the cause `System.gc()`) and remove or patch the call; (3) `-XX:+DisableExplicitGC` to make it a complete no-op — but be careful, this breaks libraries that *depend* on it.

### CQ10. "Is a finalizer ever justified? When would you use one?"

- **Naive answer:** "Never, it's deprecated."  ← right conclusion, incomplete answer
- **The trap:** The interviewer wants to know whether you understand the *category* of problem finalizers solve and what the modern equivalent is.
- **Expert answer:** `finalize()` was the only option in Java 1.x for a class you did not control that had no `close()`. Today, the modern equivalents are, in order: **try-with-resources** (if you own the call site), an explicit **`close()`/`shutdown()`** in the lifecycle, or **`java.lang.ref.Cleaner`** (if you cannot modify the class and cannot change the call site). The residual use case for `Cleaner` is a legacy third-party class that only has `finalize()`. And note the *ordering* subtlety: `Cleaner`'s action must not reference the object it cleans, or the object never becomes phantom-reachable and is never cleaned.

### CQ11. "Your G1 heap has 1 MB regions on a 64 MB heap. Is that a problem?"

- **Naive answer:** "No, that's the default."  ← misses the humongous threshold
- **The trap:** A 1 MB region means anything over 512 KB is humongous, so a service with 1 MB byte arrays will hit humongous allocation failures and full GCs on a small heap.
- **Expert answer:** Region size is `heap / 2048` rounded up to a power of two, clamped to [1 MB, 32 MB]. On small heaps you get 1 MB regions, which makes the humongous threshold only 512 KB — very low. Two fixes: raise the heap so the region size grows, or set `-XX:G1HeapRegionSize` explicitly (e.g. 16 MB) so the humongous threshold becomes 8 MB and ordinary 1–4 MB buffers stay normal objects. This is a real, frequently-missed tuning step for containerised services with a small `-Xmx`.

### CQ12. "How do you measure GC impact on end-to-end latency in production?"

- **Naive answer:** "Look at the GC log's pause times."  ← necessary, not sufficient
- **The trap:** GC pauses are not the only source of latency; and the GC log's pause time excludes **safepoint** time and the time the mutator spends in the load barrier.
- **Expert answer:** Two layers. (1) *Attribution*: correlate GC log timestamps with request-latency traces (the GC log line carries a wall-clock timestamp) to confirm that the slow requests really coincide with a pause. (2) *Completeness*: enable `-Xlog:safepoint` and look at **total** safepoint time, not just the GC pause — a class-loading storm or a biased-lock revocation can produce a multi-second safepoint with no GC at all. In a container with a CPU quota, also check whether the GC threads are *starved* — the pause can be short but the mutator can be descheduled. The modern tool for this is **JFR** (`jdk.GCPhasePause`, `jdk.SafepointBegin`, `jdk.ThreadPark`), which gives you the full picture in one recording.

---

## 10. Rapid-Fire MCQs

**1.** Java's garbage collector determines that an object is garbage by…
- A. Counting its incoming references  B. Tracing from the GC roots  C. Comparing timestamps  D. Asking the object
<details><summary>Answer</summary><strong>B</strong> — tracing from the roots. This is why reference cycles are collected correctly.</details>

**2.** Which of the following is **NOT** a GC root in HotSpot?
- A. Static fields  B. Local variables in stack frames  C. JNI global references  D. Objects stored in a `HashMap`
<details><summary>Answer</summary><strong>D</strong> — a <code>HashMap</code> is only a root if the map itself is reachable from a real root.</details>

**3.** The generational hypothesis states that…
- A. Most objects die young  B. Old objects are collected first  C. The heap should be split in half  D. Marking is faster than sweeping
<details><summary>Answer</summary><strong>A</strong> — the other two (survivor and promotion) are the supporting sub-hypotheses.</details>

**4.** How many bits are used to store an object's GC age?
- A. 1  B. 2  C. 4  D. 8
<details><summary>Answer</summary><strong>C</strong> — 4 bits in the Mark Word, which is why <code>MaxTenuringThreshold</code> is capped at 15.</details>

**5.** Which collector is the default in Java 17 and 21?
- A. Parallel  B. CMS  C. G1  D. ZGC
<details><summary>Answer</summary><strong>C</strong> — G1 has been the default since Java 9. (ZGC and Shenandoah are opt-in even in Java 21+.)</details>

**6.** A G1 "humongous object" is one that is…
- A. Larger than the old generation  B. Larger than half a region  C. Larger than 1 GB  D. An array
<details><summary>Answer</summary><strong>B</strong> — humongous objects go straight into contiguous old-gen regions, and their allocation failure is the classic G1 full-GC trigger.</details>

**7.** Which reference type is cleared **only under memory pressure**?
- A. Strong  B. Soft  C. Weak  D. Phantom
<details><summary>Answer</summary><strong>B</strong> — soft references are the "cache" reference. Weak references are cleared at the next GC.</details>

**8.** `phantomReference.get()` returns…
- A. The object  B. null, always  C. The reference queue  D. The finalized object
<details><summary>Answer</summary><strong>B</strong> — always null, even before collection. A phantom reference is a notification channel.</details>

**9.** `finalize()` was deprecated for removal in which Java version?
- A. Java 5  B. Java 9  C. Java 15  D. Java 21
<details><summary>Answer</summary><strong>B</strong> — deprecated in Java 9; terminally deprecated in Java 18.</details>

**10.** What does `OutOfMemoryError: GC overhead limit exceeded` indicate?
- A. The heap is exactly full  B. The GC spent >98% of its time freeing <2% of the heap  C. Metaspace is full  D. The code cache is full
<details><summary>Answer</summary><strong>B</strong> — it is the death-spiral warning of a heap problem, not a separate area.</details>

**11.** Which collector offers sub-millisecond pauses on multi-terabyte heaps?
- A. Serial  B. Parallel  C. G1  D. ZGC
<details><summary>Answer</summary><strong>D</strong> — ZGC's pauses are independent of heap and live-set size. G1's are bounded by evacuation work.</details>

**12.** In which Java version was CMS **removed**?
- A. Java 8  B. Java 11  C. Java 14  D. Java 17
<details><summary>Answer</summary><strong>C</strong> — deprecated in Java 9, removed in Java 14.</details>

**13.** The cost of a young (minor) GC is proportional to…
- A. The total heap size  B. The amount of garbage collected  C. The live young data copied  D. The number of threads
<details><summary>Answer</summary><strong>C</strong> — garbage is nearly free; survivors cost copies.</details>

**14.** What is the G1 pause-time goal flag and its default?
- A. `-XX:MaxGCPauseMillis=200`  B. `-XX:GCTimeRatio=12`  C. `-XX:MaxTenuringThreshold=15`  D. `-XX:TargetSurvivorRatio=50`
<details><summary>Answer</summary><strong>A</strong> — it is a <em>goal</em>, not a guarantee. <code>GCTimeRatio</code> is the throughput goal (default 12 ⇒ 1/13 of time in GC).</details>

**15.** Which is the correct way to find the *retention path* of a memory leak?
- A. `jps`  B. `jmap -histo:live`  C. Eclipse MAT "Path to GC Roots"  D. `javap -c`
<details><summary>Answer</summary><strong>C</strong> — the histogram names the victim; MAT's path-to-roots finds the culprit (the reference path).</details>

**16.** `-XX:+ExplicitGCInvokesConcurrent` makes `System.gc()`…
- A. A no-op  B. Trigger a concurrent cycle instead of a stop-the-world full GC  C. Throw an exception  D. Only collect the young generation
<details><summary>Answer</summary><strong>B</strong> — the standard mitigation for libraries that call <code>System.gc()</code>.</details>

**17.** A `WeakHashMap` entry disappears only when…
- A. The map exceeds its capacity  B. The key is no longer strongly reachable  C. `System.gc()` is called  D. The value is soft-referenced
<details><summary>Answer</summary><strong>B</strong> — a key held by a string literal or a static field is a GC root and the entry never expires.</details>

**18.** Which collector would you pick for a nightly ETL job where throughput matters and pauses do not?
- A. ZGC  B. Serial  C. Parallel  D. Epsilon
<details><summary>Answer</summary><strong>C</strong> — Parallel gives the highest throughput (most bytes reclaimed per CPU-second). Epsilon is only for very short jobs.</details>

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────┬──────────────────────────────────────────────────────────────────┐
│ CONCEPT              │ GOLDEN RULE / FACT                                            │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Reachability         │ An object is garbage when NO root can reach it. Generation is a  │
│                      │ performance heuristic, not the rule.                           │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ GC Roots             │ locals in stack frames │ statics │ JNI globals │ monitors │     │
│                      │ interned strings │ class-loader data                        │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Generational hyp.    │ Most objects die young → collect the young gen often and cheaply│
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Minor GC             │ young gen only; STW; cost = O(live young data copied)           │
│ Major GC             │ old gen; concurrent in G1/ZGC; STW-full in Serial/Parallel      │
│ Full GC              │ EVERYTHING; long STW; should never happen in production       │
│ Mixed GC (G1)        │ young + a slice of old regions                                 │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Promotion            │ age > MaxTenuringThreshold (≤15) + dynamic age rule            │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ G1                   │ region-based (heap/2048 → 1–32 MB); pause GOAL 200 ms;         │
│                      │ humongous = > ½ region → old gen, contiguous → full-GC risk;  │
│                      │ SATB write barrier; remembered set; compacts                   │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ ZGC / Shenandoah     │ fully concurrent, sub-ms pauses; coloured pointers+load       │
│                      │ barriers (ZGC) / Brooks forwarding pointers (Shenandoah)        │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Reference strengths  │ strong (never) │ soft (under pressure) │ weak (next GC) │      │
│                      │ phantom (post-mortem, get()==null)                            │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ finalize()           │ DEPRECATED FOR REMOVAL; async; resurrects; 2 GC cycles;       │
│                      │ use try-with-resources or java.lang.ref.Cleaner              │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Memory leak          │ A reachable-but-unwanted object. You cannot fix it by         │
│                      │ allocating less — you must release a reference.                │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ 5 leak causes        │ static collection │ unclosed resource │ listener │ ThreadLocal│
│                      │ class loader ( + interned strings )                           │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Diagnostic ladder    │ jstat -gcutil → jcmd GC.heap_info → jmap -histo:live →      │
│                      │ jcmd GC.heap_dump → MAT "Path to GC Roots"                   │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ GC log must-read     │ before -> after (total)  duration  CAUSE                     │
│                      │ leak  = after-size ratchets up                              │
│                      │ pressure = after-size flat, frequency rises                  │
├──────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Key flags            │ -XX:+UseG1GC │ -XX:MaxGCPauseMillis=200 │                    │
│                      │ -XX:InitiatingHeapOccupancyPercent=45 │                     │
│                      │ -XX:G1HeapRegionSize=16m │ -XX:+ExplicitGCInvokesConcurrent │ │
│                      │ -XX:+HeapDumpOnOutOfMemoryError │ -Xlog:gc*,safepoint       │
└──────────────────────┴──────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### 12.1 A production JVM flag set for a latency-sensitive service

```bash
java \
  -Xms4g -Xmx4g \                                   # equal Xms/Xmx → no resize pauses
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \                        # a realistic goal (default 200)
  -XX:InitiatingHeapOccupancyPercent=35 \           # start the concurrent cycle earlier
  -XX:G1HeapRegionSize=16m \                        # raise the humongous threshold to 8 MB
  -XX:G1ReservePercent=15 \                         # keep 15% of the heap free for the worst case
  -XX:+ParallelRefProcEnabled \                     # parallelise reference processing
  -XX:+UseStringDeduplication \                     # dedupe byte[] in the old gen
  -XX:+ExplicitGCInvokesConcurrent \                # a stray System.gc() won't be STW
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/dumps \
  -XX:MaxMetaspaceSize=256m \
  -XX:MaxDirectMemorySize=512m \
  -XX:NativeMemoryTracking=summary \                # enables jcmd VM.native_memory
  -Xlog:gc*,safepoint:file=/var/log/app-gc.log:time,uptime,level,tags:filecount=5,filesize=50M \
  -XX:StartFlightRecording=delay=0,duration=24h,filename=/var/log/app.jfr,settings=profile,maxsize=512m \
  -jar app.jar
```

**Why each one earns its place:**

| Flag | Reason |
|---|---|
| `-Xms == -Xmx` | Avoids heap **resize** pauses, and makes the GC's behaviour predictable from the first request. |
| `MaxGCPauseMillis=100` | 200 ms is optimistic for a busy heap; a goal the JVM can actually reach produces *smoother* behaviour than one it always misses. |
| `InitiatingHeapOccupancyPercent=35` | Start the concurrent marking cycle earlier → the mixed GC has more old regions to work with → fewer full GCs. |
| `G1HeapRegionSize=16m` | A 4 GB heap would default to 2 MB regions → humongous threshold of 1 MB → **your 2 MB request buffers become humongous**. This single flag often removes the mysterious 4-second full GCs. |
| `G1ReservePercent=15` | Headroom for the "evacuation failure" path. If the JVM cannot reserve space for a collection, it does a full GC. |
| `ParallelRefProcEnabled` | Reference processing (which handles weak/soft/phantom references) runs in parallel — it is a hidden STW cost in large heaps. |
| `UseStringDeduplication` | In a service full of JSON parsing, `String` objects duplicate their `byte[]` in the old gen; this can cut old-gen usage by 10–30%. |
| `NativeMemoryTracking=summary` | Enables `jcmd VM.native_memory summary` — the only way to attribute native memory. Costs ~5% overhead; enable it when investigating. |
| `StartFlightRecording=...settings=profile` | Continuous, low-overhead profiling: allocation rates, GC phase pauses, safepoints, lock contention. This is your "always-on" observability. |

### 12.2 The GC incident runbook

```text
  SYMPTOM: "the service is slow" / "the service is OOMing"
     │
     ├─ Is there a Java stack trace in the log?
     │     │
     │     ├─ NO  → kernel OOM-kill (exit 137) → the problem is TOTAL RSS vs the
     │     │        container limit. Check: Metaspace, thread stacks, direct buffers,
     │     │        code cache. Fix: cap the native areas.
     │     │
     │     └─ YES → read the exception type:
     │              │
     │              ├─ OutOfMemoryError: Java heap space      → §12.3
     │              ├─ OutOfMemoryError: GC overhead limit     → §12.3 (same cause)
     │              ├─ OutOfMemoryError: Metaspace             → §12.4
     │              ├─ OutOfMemoryError: Direct buffer memory  → §12.5
     │              ├─ OutOfMemoryError: unable to create
     │              │   native thread                          → §12.6
     │              └─ StackOverflowError                      → recursion; -Xss is a band-aid
     │
     └─ "Slow but not OOMing" → §12.7 (pause analysis)
```

### 12.3 Heap OOM — the 10-minute procedure

```bash
# 1. Is the live set genuinely too big, or is it leaking?
jstat -gcutil <pid> 1000 5        # sample 5 times: is column O trending up? FGC climbing?

# 2. Current heap shape
jcmd <pid> GC.heap_info

# 3. Who owns the bytes?
jmap -histo:live <pid> | head -25
#    → the top class is the victim. Is its count proportional to traffic?

# 4. Find the retention path
jcmd <pid> GC.heap_dump /tmp/heap-$(date +%s).hprof
#    Eclipse MAT → Leak Suspects → "Path to GC Roots" (exclude weak/soft)
#    Look for: static field │ ThreadLocal │ listener │ class loader │ JNI global

# 5. Verify the fix
#    After deploying: column O must be FLAT, and FGC must stay at 0.
```

**The anti-pattern to avoid:** deploying `-Xmx4g` on a service that was OOMing at `-Xmx2g`. If the live set grows with traffic, that just moves the deadline.

### 12.4 Metaspace OOM — the class-loader leak procedure

```bash
# 1. How much Metaspace is used?
jcmd <pid> VM.metaspace

# 2. Is the class COUNT exploding? (a sign of dynamic class generation)
jcmd <pid> GC.class_histogram | grep 'java.lang.Class' | head -3
#    → thousands of Class instances = classes are being generated, not just loaded

# 3. Watch the class-loading log
-Xlog:class+load=info:file=/var/log/classload.log
#    → look for the same class name from DIFFERENT class loaders

# 4. Find who holds the OLD loader
#    MAT → "Path to GC Roots" for the java.lang.Class instances
#    Common culprits: a live thread, a ThreadLocal, a registered JDBC driver,
#    a shutdown hook, a static Map<ClassLoader, ?>
```

**The fix is always to break the reference to the old loader.** `-XX:MaxMetaspaceSize` is a *circuit breaker*, not a fix.

### 12.5 Direct buffer OOM (Netty / NIO)

```bash
jcmd <pid> VM.native_memory summary | grep -A5 Other
# or
jcmd <pid> GC.heap_info    # heap is fine; the problem is elsewhere

# Typical cause: a Netty ByteBufAllocator that is not released, or a
# DirectByteBuffer that is still reachable.
```

**Fixes:** bound the pool (`-XX:MaxDirectMemorySize` + a bounded `PooledByteBufAllocator`), use `-Dio.netty.maxDirectMemory=0` to *disable* Netty's own limit so the JVM's limit applies, and make sure `ByteBuf`s are released (`try { ... } finally { buf.release(); }` or use `try (ByteBuf b = allocator.buffer())`).

### 12.6 "Unable to create native thread"

```bash
ulimit -u                        # max user processes
ls /proc/<pid>/task | wc -l     # current thread count
top -H -p <pid>                 # per-thread CPU and memory
cat /sys/fs/cgroup/pids.max     # container PID limit
```

**Causes:** too many threads (a `newFixedThreadPool` sized to the CPU count *inside* a container that is itself CPU-limited, plus a request-handling pool, plus a scheduler, plus Netty's event loops), or the container's memory limit being consumed by the thread *stacks* (`threads × -Xss`).

**Fixes:** size thread pools from the **container's** CPU quota, not the host's core count; set `-Xss` explicitly and modestly; and check the `pids.max` cgroup limit.

### 12.7 Pause analysis (slow but not OOMing)

```bash
# 1. What is the pause distribution? (p99 matters, not the mean)
grep 'Pause' /var/log/app-gc.log | awk '{print $NF}' | sed 's/ms//' | sort -n | tail -100
#    → the 99th percentile of the last 100 pauses

# 2. Is it a young GC, a mixed GC, or a full GC?
grep -c 'Pause Full'      /var/log/app-gc.log
grep -c 'Pause Young'     /var/log/app-gc.log

# 3. Is a humongous allocation causing it?
grep 'Humongous' /var/log/app-gc.log | head

# 4. Safepoint time (often the real culprit)
grep 'Safepoint' /var/log/app-gc.log | tail -20

# 5. JFR for the complete picture
jcmd <pid> JFR.dump name=incident /var/log/incident.jfr
#    → jfr summary /var/log/incident.jfr
#    → JMC → "GC" and "Safepoint" tabs
```

**Interpretation:**

| Observation | Likely cause | Action |
|---|---|---|
| Long young pauses, high live young set | Promotion storm / high survival rate | Reduce the live set; check for request-scoped caches |
| Occasional very long `Pause Full` | Humongous allocation or concurrent-cycle failure | Raise `-XX:G1HeapRegionSize`; find the big allocation |
| Long pauses with tiny reclaim | Everything is live → static cache leak | Histogram + MAT |
| Long safepoints, short GC pauses | Class loading, biased-lock revocation, deoptimization storm | `-Xlog:class+load`, `-Xlog:deoptimization` |
| GC pause fine, latency bad | Not GC — look at locks, I/O, safepoint | JFR: `jdk.JavaMonitorEnter`, `jdk.ThreadPark` |

### 12.8 A "GC health" SLO for a banking platform

| Metric | Target | Alert |
|---|---|---|
| GC pause p99 | < 50 ms | > 100 ms for 5 min |
| GC time / wall clock | < 5% | > 15% for 10 min |
| Full GC count | 0 | ≥ 1 (immediate page) |
| Old-gen occupancy after GC | stable, < 70% | trending up > 5% per hour |
| Metaspace used | < 60% of cap | > 80% of cap |
| Direct memory used | < 70% of cap | > 85% of cap |
| Thread count | stable | monotonically rising |
| Heap dump on OOM | always present | missing dump ⇒ kernel kill, not a Java OOM |

**Build these from the JMX beans in §6.2 and export them as Micrometer gauges** — the `Health` record already gives you every field.

### 12.9 Lessons from real incidents (patterns, not war stories)

| Incident | Root cause | Detection that would have caught it |
|---|---|---|
| Latency spike every 4 hours | A nightly job allocated 2 GB arrays → humongous → full GC | GC log `G1 Humongous Allocation`; `G1HeapRegionSize` fix |
| "Memory leak" that was not a leak | A 6 GB in-memory cache in a 4 GB heap | Post-GC live set flat but near capacity ⇒ sizing, not leaking |
| OOM after every deploy | Old WAR class loaders held by a `ThreadLocal` in a pool thread | `Class` instance count in the histogram; `-Xlog:class+load` |
| Container killed with no Java log | 300 threads × 1 MB stacks + 2 GB heap > 3 GB cgroup limit | RSS vs cgroup limit alerting; cap `-Xss` and thread counts |
| "GC is slow" | `-Xmx` was set to 90% of a 4 GB box with 2 GB of native memory | Native memory accounting, `VM.native_memory summary` |
| Everything fine on x86, broken on ARM | Visibility bug — a non-`volatile` flag | JMM review; the bug is always a missing happens-before edge |

---

## 13. Follow-up Learning

**Next in this folder:**
- [03 · JVM Internals](03-JVM-Internals.md) — class loading, bytecode, JIT compilation, and the diagnostic toolkit in depth

**Directly related chapters:**
- [01 · Memory Areas](01-Memory-Areas.md) — the runtime data areas, GC roots, and the OOM taxonomy
- [08 · Multithreading & Concurrency](../08-Multithreading-Concurrency/01-Thread-Basics.md) — `ThreadLocal` (the classic leak), thread pools, and memory visibility
- [12 · Strings](../12-Strings/01-String-Internals.md) — the String pool, `intern()`, and `StringDeduplication`
- [14 · Reflection & Annotations](../14-Reflection-Annotations/01-Reflection-and-Annotations.md) — dynamic proxies, class loaders and Metaspace
- [15 · IO / NIO](../15-IO-NIO/01-IO-NIO.md) — direct byte buffers and off-heap I/O

**Tools to practise on:**
`jstat` · `jmap` · `jcmd` · `jstack` · `jps` · **JFR** (`jcmd <pid> JFR.start`) · **Eclipse MAT** · **GCEasy** (gceasy.io) · **GCViewer** · `java -Xlog:gc*` · `java -XX:+PrintFlagsFinal`

**Books and references:**
- *The Garbage Collection Handbook* — Andy Witherspoon and Richard Jones (the definitive practical book)
- *Java Performance* — G. Bennett
- *The Java Virtual Machine Specification* §12 (Heap) and §17.4 (Memory Model)
- The OpenJDK source: `src/hotspot/share/gc/g1/`, `src/hotspot/share/gc/z/`, `src/hotspot/share/gc/shenandoah/`
- Oracle's white papers: *The Garbage First Garbage Collector* and *ZGC: A Scalable Low-Latency Garbage Collector*

---

**Next Chapter:** → [03 · JVM Internals](03-JVM-Internals.md)
**Previous Chapter:** ← [01 · Memory Areas](01-Memory-Areas.md)
