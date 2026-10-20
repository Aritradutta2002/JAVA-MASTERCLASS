# 05 — Generics · Interview Notes Index

> Generics look easy and are one of the most frequently mis-answered topics.
> The interview is not about *syntax* — it is about **type safety, erasure, and variance**.

| Priority | Chapter | Why It Matters |
|----------|---------|----------------|
| ⭐⭐⭐⭐ | [01 · Generics — Complete Guide](01-Generics.md) | Erasure, wildcards, PECS, bounded types |

---

## 📚 What to Master

| Concept | Interview Weight | Why |
|---------|------------------|-----|
| Type erasure + bridge methods | ★★★★★ | Explains "why can't I `new T[]`?" and "why is `List<String>` not a `List<Object>`?" |
| Wildcards: `? extends` vs `? super` | ★★★★★ | The single most-asked generics question |
| PECS (Producer Extends, Consumer Super) | ★★★★★ | Memorise the mnemonic, explain the reason |
| Generic methods vs generic classes | ★★★★☆ | `Collections.<String>sort` style inference |
| Bounded type parameters `<T extends Number>` | ★★★★☆ | |
| Raw types & unchecked warnings | ★★★☆☆ | Legacy interop |
| Generic arrays & heap pollution | ★★★☆☆ | |
| Type tokens (`TypeReference`) | ★★☆☆☆ | Advanced, appears in Jackson/Gson interviews |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| Why is `List<String>` not assignable to `List<Object>`? | It's *not* generic — it's invariant. Java doesn't support covariance for mutable generics. |
| When do you use `? extends`? | When you only **read** from the collection (Producer). |
| When do you use `? super`? | When you only **write** into the collection (Consumer). |
| Why can't you create `new T[]`? | Type erasure — the runtime has no `T`. Use `new Object[]` + cast, or `List<T>`. |
| Why do raw types still compile? | Backward compatibility (pre-Java 5 code). They generate unchecked warnings. |
| What are bridge methods? | Compiler-generated methods to preserve polymorphism after erasure (e.g. `Comparable.compareTo`). |
| Is `Collection<?>` the same as `Collection<Object>`? | **No.** `Collection<?>` is a *wildcard* — read-only view of unknown type; `Collection<Object>` is a concrete type. |
| What is heap pollution? | Unchecked cast corrupts the type-safety of a generic variable at runtime. |

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **Erasure + `instanceof` + casts** — the "cannot check generic type at runtime" limitation.
- [ ] **Bounded wildcards vs bounded type parameters** — `List<? extends Number>` vs `<T extends Number>`.
- [ ] **Method reference + generic inference** — `<String>String::valueOf` ambiguity.
- [ ] **Static generic methods** — type parameters cannot be on static *fields*, only on methods/classes.

---

## ✅ Self-Check Checklist

- [ ] I can explain PECS without the mnemonic — using an `Animal`/`Dog` example and a `Consumer`/`Producer` role split.
- [ ] I can write a generic `copy` method that works for any supertype.
- [ ] I can explain what happens at runtime with `List<Integer> l; l.add("x")` and why the compiler stops it.
- [ ] I can state the erasure of `List<String>` and of `T extends Number`.
- [ ] I know why `Enum<E extends Enum<E>>` is written that way.

---

**Related folders:** → [06 · Java 8 Features](../06-Java-8-Features/README.md) · ← [04 · Collections](../04-Collections-Framework/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
