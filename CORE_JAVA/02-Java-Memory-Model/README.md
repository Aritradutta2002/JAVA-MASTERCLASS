# 02 — Java Memory Model & JVM Internals · Interview Notes Index

> **Where senior interviews separate themselves from juniors.**
> Knowing *where a variable lives* is junior. Knowing **why the GC behaves the way it does**,
> and proving it with a heap dump, is senior.

| Priority | Chapters | Why It Matters |
|----------|----------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · Memory Areas](01-Memory-Areas.md) | Stack vs Heap — asked constantly |
| ⭐⭐⭐⭐⭐ | [02 · Garbage Collection](02-Garbage-Collection.md) | GC algorithm + tuning questions |
| ⭐⭐⭐⭐⭐ | [03 · JVM Internals](03-JVM-Internals.md) | Class loading, JIT, diagnostics |

---

## 📚 Recommended Reading Order

```
01 Memory Areas ──▶ 02 Garbage Collection ──▶ 03 JVM Internals
```

Read **01** as the vocabulary (stack frame, heap, metaspace, GC roots).
Read **02** as the algorithm (how unreachable objects get collected).
Read **03** as the machine (how bytecode becomes native code and how you debug it).

---

## 🎯 Most Asked in Interviews

| Question | Chapter |
|----------|---------|
| Where are local variables, instance variables and static variables stored? | [01](01-Memory-Areas.md) |
| What causes `StackOverflowError` vs `OutOfMemoryError`? | [01](01-Memory-Areas.md) |
| Difference between PermGen and Metaspace? | [01](01-Memory-Areas.md) |
| Explain minor GC vs major GC with a diagram. | [02](02-Garbage-Collection.md) |
| What are GC roots? How is an object "reachable"? | [02](02-Garbage-Collection.md) |
| G1 vs ZGC vs Parallel GC — when do you pick each? | [02](02-Garbage-Collection.md) |
| What happens from `.java` → `.class` → execution? | [03](03-JVM-Internals.md) |
| C1 vs C2 JIT, tiered compilation, deoptimisation? | [03](03-JVM-Internals.md) |
| How do you diagnose a memory leak in production? | [02](02-Garbage-Collection.md) + [03](03-JVM-Internals.md) |

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **GC roots & reachability** — most candidates memorise "young/old gen" without understanding *why* objects move.
- [ ] **Metaspace vs Heap** — a common trap: `OutOfMemoryError: Metaspace` is *not* a heap error.
- [ ] **JIT tiering** — why the first 10,000 calls are slow (interpreter → C1 → C2).
- [ ] **Volatile vs atomicity** — the JMM happens-before edge, covered in [08 Concurrency](../08-Multithreading-Concurrency/01-Thread-Basics.md).

---

## 🔧 Diagnostics Toolkit (memorise these commands)

| Command | Answers |
|---------|---------|
| `jcmd <pid> GC.heap_info` | Heap usage snapshot |
| `jcmd <pid> GC.class_histogram` | Top classes by instance count (leak suspect) |
| `jcmd <pid> GC.heap_dump /tmp/h.hprof` | Full heap dump for MAT |
| `jmap -histo:live <pid>` | Live-object histogram (forces full GC) |
| `jstat -gcutil <pid> 1000` | GC frequency & time per generation |
| `jstack <pid>` | Thread dump → detects deadlock |
| `jcmd <pid> Compiler.codecache` | JIT code cache usage |
| `-XX:+PrintGCDetails -Xlog:gc*` | GC log analysis |

---

**Related folders:** → [08 · Concurrency](../08-Multithreading-Concurrency/README.md) · ← [01 · OOP](../01-Core-Java-OOP/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
