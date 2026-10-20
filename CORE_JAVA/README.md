# ☕ CORE JAVA — The Complete Interview Masterclass

> **Everything you need to crack a Java backend / full-stack interview — in depth.**
>
> 18 topics · 44 deep-dive chapters · 1,300+ interview questions with expert answers ·
> counter-questions (interviewer push-back) · visualisations · dry-run traces · MCQ banks.
>
> 📌 **Every chapter follows the [Interview Notes Standard](./STANDARD.md)** — so you always
> know exactly what you will find: TL;DR → theory → visuals → worked examples → pitfalls →
> level-wise Q&A → **counter-questions** → rapid-fire MCQs → cheat sheet.

---

## 🚀 Start Here (pick your path)

| You are… | Do this |
|----------|---------|
| 🟢 **Fresher (0–1 yr)** | Start at [01 · OOP](./01-Core-Java-OOP/README.md) → [04 · Collections](./04-Collections-Framework/README.md) → [03 · Exceptions](./03-Exception-Handling/README.md) → [06 · Java 8](./06-Java-8-Features/README.md). Use the TL;DR + Cheat Sheet only. |
| 🟡 **Mid (2–5 yr)** | Everything, in order. Focus on [08 · Concurrency](./08-Multithreading-Concurrency/README.md), [02 · JVM](./02-Java-Memory-Model/README.md), [06 · Streams](./06-Java-8-Features/02-Stream-API.md), [05 · Generics](./05-Generics/README.md). |
| 🔴 **Senior (5+ yr)** | [02 · JVM Internals](./02-Java-Memory-Model/03-JVM-Internals.md), [08 · Concurrency](./08-Multithreading-Concurrency/), [09 · Patterns](./09-Design-Patterns/README.md), [10 · SOLID](./10-SOLID-Principles/README.md), [16 · JDBC](./16-JDBC/README.md), [07 · Java 21](./07-Java-11-17-21-Features/README.md). |
| ⚫ **Mock interview tonight** | [🔥 Question Bank](#-the-question-bank) + the Counter-Questions section of every chapter. |
| 🏃 **30-minute refresher** | [12 · Strings](./12-Strings/01-String-Internals.md) → [13 · Enums](./13-Enums/01-Enum-Deep-Dive.md) → [01 · Interface vs Abstract](./01-Core-Java-OOP/06-OOP-Interface-vs-Abstract-Class.md) → [04 · Maps](./04-Collections-Framework/02-Map-Implementations.md). |

---

## 📚 Chapter Index

| # | Topic | Chapters | Level | Priority | Index |
|---|-------|----------|-------|----------|-------|
| 01 | **Core Java & OOP** | 13 | 🟢→🔴 | ⭐⭐⭐⭐⭐ | [Open](./01-Core-Java-OOP/README.md) |
| 02 | **Java Memory Model & JVM** | 3 | 🟡→⚫ | ⭐⭐⭐⭐⭐ | [Open](./02-Java-Memory-Model/README.md) |
| 03 | **Exception Handling** | 1 | 🟢→🔴 | ⭐⭐⭐⭐ | [Open](./03-Exception-Handling/README.md) |
| 04 | **Collections Framework** | 3 | 🟢→🔴 | ⭐⭐⭐⭐⭐ | [Open](./04-Collections-Framework/README.md) |
| 05 | **Generics** | 1 | 🟡→🔴 | ⭐⭐⭐⭐ | [Open](./05-Generics/README.md) |
| 06 | **Java 8 Features** | 3 | 🟡→🔴 | ⭐⭐⭐⭐⭐ | [Open](./06-Java-8-Features/README.md) |
| 07 | **Java 11 / 17 / 21** | 1 | 🟡→⚫ | ⭐⭐⭐⭐⭐ | [Open](./07-Java-11-17-21-Features/README.md) |
| 08 | **Multithreading & Concurrency** | 4 | 🟡→⚫ | ⭐⭐⭐⭐⭐ | [Open](./08-Multithreading-Concurrency/README.md) |
| 09 | **Design Patterns** | 1 (all 23) | 🟡→🔴 | ⭐⭐⭐⭐⭐ | [Open](./09-Design-Patterns/README.md) |
| 10 | **SOLID Principles** | 1 | 🟡→🔴 | ⭐⭐⭐⭐ | [Open](./10-SOLID-Principles/README.md) |
| 11 | **Data Structures & Algorithms** | 1 | 🟢→🔴 | ⭐⭐⭐⭐⭐ | [Open](./11-DSA/README.md) |
| 12 | **Strings & String Internals** | 1 | 🟡→🔴 | ⭐⭐⭐⭐⭐ | [Open](./12-Strings/README.md) |
| 13 | **Enums** | 1 | 🟡→🔴 | ⭐⭐⭐⭐ | [Open](./13-Enums/README.md) |
| 14 | **Reflection & Annotations** | 1 | 🟡→🔴 | ⭐⭐⭐ | [Open](./14-Reflection-Annotations/README.md) |
| 15 | **I/O & NIO** | 1 | 🟡→🔴 | ⭐⭐⭐⭐ | [Open](./15-IO-NIO/README.md) |
| 16 | **JDBC** | 1 | 🟡→🔴 | ⭐⭐⭐⭐ | [Open](./16-JDBC/README.md) |
| 17 | **Serialization** | 3 | 🟡→🔴 | ⭐⭐⭐⭐ | [Open](./17-Serialization/README.md) |
| 18 | **Testing** | 4 | 🟢→🔴 | ⭐⭐⭐⭐ | [Open](./18-Testing/README.md) |

---

## 🔥 The Question Bank

### Top 40 Questions by Frequency (must-master)

| # | Question | Chapter | Trap in the naive answer |
|---|----------|---------|--------------------------|
| 1 | How does `HashMap` work internally? | [04/02](./04-Collections-Framework/02-Map-Implementations.md) | Saying "it uses buckets" without the hash-spreading or the 8/6 treeify thresholds |
| 2 | `HashMap` vs `ConcurrentHashMap`? | [04/02](./04-Collections-Framework/02-Map-Implementations.md) | "CHM is thread-safe" — the real answer is *lock-free reads, bin-level locks on writes, no nulls* |
| 3 | Interface vs Abstract class? | [01/06](./01-Core-Java-OOP/06-OOP-Interface-vs-Abstract-Class.md) | "Interfaces give multiple inheritance" — only one of six reasons |
| 4 | Where are local/static/instance variables stored? | [02/01](./02-Java-Memory-Model/01-Memory-Areas.md) | Conflating "reference in stack, object in heap" with static variables (**Metaspace**) |
| 5 | Explain the Java Memory Model / happens-before. | [08/01](./08-Multithreading-Concurrency/01-Thread-Basics.md) | Saying `synchronized` and `volatile` are interchangeable |
| 6 | `volatile` vs `synchronized` vs `AtomicInteger`? | [08/03](./08-Multithreading-Concurrency/03-Locks-Atomic.md) | Missing that `volatile` gives **visibility but not atomicity** |
| 7 | How do Streams work internally? | [06/02](./06-Java-8-Features/02-Stream-API.md) | Only describing the API, not the sink chain + `Spliterator` |
| 8 | Is `parallelStream` always faster? | [06/02](./06-Java-8-Features/02-Stream-API.md) | Saying "yes, it's multi-threaded" |
| 9 | Explain `ThreadPoolExecutor`'s 7 parameters. | [08/02](./08-Multithreading-Concurrency/02-Executor-Framework.md) | Guessing the queue/rejection-policy behaviour |
| 10 | How do you prevent a deadlock? | [08/04](./08-Multithreading-Concurrency/04-Concurrency-Problems.md) | "Use `ConcurrentHashMap`" — unrelated |
| 11 | What causes `OutOfMemoryError`? | [02/01](./02-Java-Memory-Model/01-Memory-Areas.md) | Confusing heap, metaspace, direct-buffer and stack OOM — 4 distinct causes |
| 12 | Minor vs major GC? | [02/02](./02-Java-Memory-Model/02-Garbage-Collection.md) | "Minor collects young gen, major collects old gen" — true but not *why* |
| 13 | `==` vs `equals` for Strings? | [12/01](./12-Strings/01-String-Internals.md) | Not mentioning the string pool |
| 14 | Why use `StringBuilder` in loops? | [12/01](./12-Strings/01-String-Internals.md) | Not quantifying the O(n²) copying |
| 15 | `orElse` vs `orElseGet`? | [06/03](./06-Java-8-Features/03-Optional-CompletableFuture.md) | `orElse` **always evaluates** its argument |
| 16 | `join` vs `get` on `CompletableFuture`? | [06/03](./06-Java-8-Features/03-Optional-CompletableFuture.md) | `get` throws checked `InterruptedException`/`ExecutionException` |
| 17 | What are the 4 transaction isolation levels? | [16/01](./16-JDBC/01-JDBC-Basics.md) | Listing them without the anomalies each prevents |
| 18 | Is `PreparedStatement` injection-safe? | [16/01](./16-JDBC/01-JDBC-Basics.md) | **No** — concatenated identifiers/`LIKE` patterns are still injectable |
| 19 | Why `try-with-resources` over `finally`? | [03/01](./03-Exception-Handling/01-Exception-Handling.md) | Not mentioning **suppressed exceptions** |
| 20 | Why does `final` not guarantee thread safety? | [01/07](./01-Core-Java-OOP/07-OOP-Keywords-final-static-volatile-transient.md) | `final` gives *safe publication*, not *mutual exclusion* |
| 21 | `List<? extends Number>` vs `List<? super Integer>`? | [05/01](./05-Generics/01-Generics.md) | The classic PECS trap |
| 22 | What is type erasure? | [05/01](./05-Generics/01-Generics.md) | Not explaining bridge methods |
| 23 | `equals`/`hashCode` contract? | [01/08](./01-Core-Java-OOP/08-OOP-equals-hashCode-toString.md) | Only "they must be equal" — need symmetry/transitivity/consistency |
| 24 | Why is the `hashCode` contract a *bug source*? | [01/08](./01-Core-Java-OOP/08-OOP-equals-hashCode-toString.md) | Mutable keys → unreachable entries in a `HashMap` |
| 25 | Can you break the Singleton? | [09/01](./09-Design-Patterns/01-Design-Patterns.md) | Missing reflection / `readResolve` / deserialisation attacks |
| 26 | What is `ConcurrentModificationException`? | [04/03](./04-Collections-Framework/03-Set-Queue-Utilities.md) | Not saying fail-fast is *best-effort*, not guaranteed |
| 27 | `ArrayList` vs `LinkedList`? | [04/01](./04-Collections-Framework/01-List-Implementations.md) | Ignoring node memory overhead and cache locality |
| 28 | `PriorityQueue` — is it stable? | [04/03](./04-Collections-Framework/03-Set-Queue-Utilities.md) | **No** — it is a binary heap |
| 29 | Why `wait/notify` instead of `sleep`? | [08/01](./08-Multithreading-Concurrency/01-Thread-Basics.md) | `sleep` doesn't release the monitor |
| 30 | Why `notifyAll` over `notify`? | [08/01](./08-Multithreading-Concurrency/01-Thread-Basics.md) | Lost-notification + spurious wakeup |
| 31 | What is a `record`? | [07/01](./07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) | Not listing exactly which members are generated |
| 32 | What are virtual threads? | [07/01](./07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) | Not mentioning the **pinning** problem |
| 33 | What is `sealed` for? | [07/01](./07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) | Missing compiler-checked exhaustiveness |
| 34 | Explain all 23 GoF patterns. | [09/01](./09-Design-Patterns/01-Design-Patterns.md) | Memorising names without "when NOT to use" |
| 35 | Explain each SOLID principle. | [10/01](./10-SOLID-Principles/01-SOLID-Principles.md) | Definitions without ❌→✅ refactors |
| 36 | What is `readResolve` vs `readObject`? | [17/02](./17-Serialization/02-Custom-Serialization.md) | `readResolve` **replaces**, `readObject` **mutates** |
| 37 | Is Java deserialisation safe? | [17/03](./17-Serialization/03-Serialization-Best-Practices.md) | Not naming gadget chains / `ObjectInputFilter` |
| 38 | Mock vs stub vs spy? | [18/02](./18-Testing/02-Mockito-Mocking.md) | Treating them as synonyms |
| 39 | JUnit 4 vs JUnit 5? | [18/01](./18-Testing/01-JUnit-Basics.md) | Missing the Jupiter platform + Vintage engine |
| 40 | What is the test pyramid? | [18/04](./18-Testing/04-Testing-Best-Practices.md) | Describing only the shape, not the anti-pattern |

> 💡 **How to use the bank:** don't memorise answers. For each question, say the *naive* answer
> first, then name the trap, then give the expert answer. That structure alone impresses
> interviewers more than any fact you can recite.

---

## 🧭 The Counter-Question Technique

The single biggest difference between a mid-level and a senior candidate is **how they handle push-back**.

Every chapter in this repo has a **"Counter-Questions & Traps"** section with **8+ probes** of these four kinds:

| Probe type | Example | How to answer |
|-----------|---------|---------------|
| **"Why not X?"** | "Isn't `HashMap` enough? Why CHM?" | Name the naive answer → name the trap → give the expert answer |
| **"What if…?"** | "What happens if the queue is full?" | Trace the exact code path in `ThreadPoolExecutor` |
| **"How would you find out?"** | "How do you diagnose this leak?" | Name the tool (`jcmd`, `jmap`, MAT) **and** the signal you expect to see |
| **"Is that true?"** | "Is `final` enough for thread safety?" | Correct the premise politely, then explain the precise guarantee |

---

## 🎯 Interview Frequencies by Company Type

```
FAANG / Product      Collections ███████  Concurrency ███████  JVM █████
                     Java 8 ███████      OOP █████           DS&A ███████

Banking / FinTech    Concurrency ███████ JVM ███████         Collections ███████
                     JDBC ███████        Design █████        Exceptions █████

Indian Product Co.   OOP ███████         Collections ███████ Java 8 ███████
                     Multithreading ███████  DS&A ███████    SQL/JDBC █████

Contract / Product    OOP ███████         Java 8 ███████      Collections █████
Dev (US clients)     Design █████        Exceptions █████     Strings █████
```

---

## 🧪 How to Practise (a real 4-week plan)

```
WEEK 1 — Foundations
  Mon-Tue  01 OOP (01→06)            Wed  03 Exceptions + 13 Enums
  Thu-Fri  12 Strings                Sat-Sun 04 Collections (01→03)

WEEK 2 — Modern Java
  Mon-Wed  06 Java 8 (01→03)        Thu  05 Generics
  Fri-Sun  07 Java 11/17/21         Daily: 20 MCQs from the chapters read

WEEK 3 — Depth
  Mon-Wed  08 Concurrency (01→04)   Thu-Fri  02 JVM + Memory Model
  Sat-Sun  16 JDBC + 15 IO/NIO      Daily: 1 coding drill per chapter

WEEK 4 — Design & Polish
  Mon-Tue  09 Design Patterns        Wed  10 SOLID
  Thu-Fri  17 Serialization + 18 Testing
  Sat-Sun  11 DSA full sprint + 14 Reflection
  FINAL   Mock interview: 3 rounds, 45 min each, recorded & reviewed
```

### Daily Mechanics (60–90 min)

```
15 min  Read ONE chapter's TL;DR + Cheat Sheet
25 min  Read the Deep Dive + trace every diagram out loud
20 min  Answer the Counter-Questions WITHOUT looking
15 min  Do the Rapid-Fire MCQs (closed-book)
15 min  Write & run one code sample; predict output BEFORE running
```

---

## 🧰 Cheat-Sheet One-Pagers

| Topic | Cheat Sheet |
|-------|-------------|
| Collections complexity | [04/03](./04-Collections-Framework/03-Set-Queue-Utilities.md) |
| HashMap internals | [04/02](./04-Collections-Framework/02-Map-Implementations.md) |
| JVM memory map | [02/01](./02-Java-Memory-Model/01-Memory-Areas.md) |
| GC collectors | [02/02](./02-Java-Memory-Model/02-Garbage-Collection.md) |
| ThreadPoolExecutor params | [08/02](./08-Multithreading-Concurrency/02-Executor-Framework.md) |
| Locks & atomics | [08/03](./08-Multithreading-Concurrency/03-Locks-Atomic.md) |
| Stream operators | [06/02](./06-Java-8-Features/02-Stream-API.md) |
| Functional interfaces | [06/01](./06-Java-8-Features/01-Lambda-Functional-Interfaces.md) |
| Interface vs Abstract | [01/06](./01-Core-Java-OOP/06-OOP-Interface-vs-Abstract-Class.md) |
| Pattern selection | [09/01](./09-Design-Patterns/01-Design-Patterns.md) |
| SOLID ↔ code smells | [10/01](./10-SOLID-Principles/01-SOLID-Principles.md) |

---

## 📐 The Documentation Standard

Every chapter here follows [`STANDARD.md`](./STANDARD.md) — read it once and you know exactly
what every file contains and how to review it:

```
TL;DR → What & Why → Mental Model → Deep Dive → Visualizations →
Worked Examples (output + dry run) → Production Code → Pitfalls →
Q&A by level → Counter-Questions & Traps → Rapid-Fire MCQs →
Cheat Sheet → Production Notes → Next Steps
```

---

## 🔗 Related Folders

| Folder | Contents |
|--------|----------|
| [`SPRING_SPRINGBOOT`](../SPRING_SPRINGBOOT/) | Spring & Spring Boot interview notes |
| [`DATABASE_CACHING`](../DATABASE_CACHING/) | SQL, indexing, Redis, caching patterns |
| [`SYSTEM_DESIGN`](../SYSTEM_DESIGN/) | System design round preparation |
| [`JAVA_DSA`](../JAVA_DSA/) | Full problem-by-problem DSA solutions |
| [`CLOUD_DEVOPS`](../CLOUD_DEVOPS/) | Docker, K8s, CI/CD |
| [`DOCKER_JAVA_BACKEND`](../DOCKER_JAVA_BACKEND/) | Containerising Java apps |

---

## 📝 Conventions

| Marker | Meaning |
|--------|---------|
| 🟢 | Beginner question |
| 🟡 | Intermediate question |
| 🔴 | Advanced / Senior question |
| ⚫ | Expert / Principal question |
| ❌ → ✅ | Common mistake and its fix |
| `▶` | The line an interviewer is listening for |
| 💡 | Real-world production note |

---

**Maintained for Java interviews · Standard: [STANDARD.md](./STANDARD.md) · Updated: 2026**
