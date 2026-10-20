# 07 — Java 11 / 17 / 21 Features · Interview Notes Index

> **Modern Java.** Interviewers use this chapter to separate candidates who are still on Java 8
> from those who ship modern code. Records, sealed types and pattern matching are now the
> default way to model closed domain hierarchies.

| Priority | Chapter | Why It Matters |
|----------|---------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · Java 11, 17 & 21 Features](01-Java-11-17-21-Features.md) | Records, sealed classes, pattern matching, virtual threads |

---

## 🎯 Most Asked in Interviews

| Question | Version | Trap |
|----------|---------|------|
| What is a `record` and what does it generate? | 16 | `equals`/`hashCode`/`toString` + **final fields**, but **not** a general immutable class |
| Can a record be `extends`ed? | — | No — records are implicitly `final` |
| What are `sealed` classes for? | 17 | Exhaustiveness for pattern matching; the compiler can check "did you handle all cases?" |
| What is pattern matching for `switch`? | 21 (preview 17/18/19/20) | Java 21 finalised switch patterns; record patterns also 21 |
| What are virtual threads? | 21 | Cheap threads, **not** cheap CPU. Pinning problem! |
| `var` — is it a keyword? | 10 | It's a **reserved type name**, not a keyword — you can still name a variable `var`... no, you can't as a type. Explain the restriction. |
| `List.of` vs `Arrays.asList`? | 9 vs 1.2 | `List.of` is immutable + rejects nulls; `Arrays.asList` is a fixed-size *view* |
| `Stream.toList()` vs `collect(Collectors.toList())` | 16 | `toList()` allows nulls, returns an unmodifiable list |

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **Virtual thread pinning** — a `synchronized` block or native call inside a virtual thread pins the carrier thread.
- [ ] **Record mutability traps** — a record can still hold a mutable `List` field; the record is only *shallowly* immutable.
- [ ] **Sealed interfaces vs enums** — when to use which (sealed interfaces allow behaviour per subtype).
- [ ] **Text blocks** — the incidental-whitespace rules trip people up.
- [ ] **Switch expression exhaustiveness** — a `sealed` hierarchy + `switch` pattern is checked by the compiler.

---

## 📅 Version Feature Map (what landed when)

```
Java 9   ─ modules, var, private interface methods, List.of, try-with-resources enhancement
Java 10  ─ var (official), Local Variable Type Inference
Java 11  ─ var in lambda params, String.stripIndent/indent/isBlank, HttpClient,
           Files.readString, Collection.toArray(IntFunction)
Java 12  ─ switch expressions (preview), text blocks (preview)
Java 13  ─ text blocks (final), switch expressions (2nd preview)
Java 14  ─ switch expressions (final), records (preview), helpful NPEs (final)
Java 15  ─ records (2nd preview), sealed classes (preview), text blocks (final)
Java 16  ─ records (final), instanceof pattern matching (final), Stream.toList(),
           Stream.iterate(3-arg), DayOfWeek enum
Java 17  ─ sealed classes (final), switch patterns (preview), records (final),
           pattern matching for switch (preview), text blocks (final)
Java 18  ─ UTF-8 by default, simple web server (preview)
Java 19  ─ record patterns (preview), virtual threads (preview)
Java 20  ─ switch patterns (2nd preview), virtual threads (2nd preview)
Java 21  ─ switch patterns (FINAL), record patterns (FINAL), virtual threads (FINAL),
           sequenced collections (preview), pattern matching for switch (FINAL)
```

---

**Related folders:** → [08 · Concurrency](../08-Multithreading-Concurrency/README.md) · ← [06 · Java 8 Features](../06-Java-8-Features/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
