# 14 — Reflection & Annotations · Interview Notes Index

> Reflection is "Java's escape hatch from its own type system". Interviewers love it because
> the *right answer* is nuanced: reflection is powerful, slow, and dangerous — and the
> modern replacement is usually **method handles** or **compile-time code generation**.

| Priority | Chapter | Why It Matters |
|----------|---------|----------------|
| ⭐⭐⭐⭐ | [01 · Reflection & Annotations](01-Reflection-and-Annotations.md) | `Class` internals, annotation retention, dynamic proxies, JPMS strong encapsulation |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| `getDeclaredMethod` vs `getMethod`? | `getDeclaredX` = this class only; `getX` = this class **+ inherited public**. |
| Why does `setAccessible(true)` fail on JDK 17? | **Module system** strong encapsulation — need `--add-opens`. |
| What is the performance cost of reflection? | It bypasses the JIT's inlining; ~10–100× slower than direct calls. Use `MethodHandle` for hot paths. |
| `@Retention` values? | `SOURCE` (compile-time only), `CLASS` (in `.class`, not at runtime), `RUNTIME` (reflectable), `DEFAULT`. |
| Is `@Inherited` what I think? | Only applies to **class** inheritance, **not** interfaces — an annotation on an interface is NOT inherited by implementing classes. |
| Can annotations have values/defaults? | Yes — elements with defaults, and `value()` shorthand. |
| How does Spring implement `@Transactional`? | **AOP + dynamic proxy** around the bean (or CGLIB for classes). |
| What is a dynamic proxy? | `java.lang.reflect.Proxy` implementing an interface, dispatching to an `InvocationHandler`. |

---

## 🧠 Annotation Retention Visual

```
SOURCE  ──▶  only visible to the compiler (e.g. @Override, @SuppressWarnings)
             ❌ not in .class  ❌ not reflectable

CLASS    ──▶  stored in the .class file  ✅ reflection CANNOT read it at runtime
             (default retention)

RUNTIME  ──▶  stored in .class AND reflectable via getAnnotations()
             (e.g. @Entity, @Component, @Test)
```

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **`MethodHandles` vs reflection** — the modern, JIT-friendly alternative.
- [ ] **Annotation processors (APT)** — how Lombok and MapStruct work.
- [ ] **Repeated annotations** — `@Repeatable` + the container annotation.
- [ ] **Reflection performance** — why frameworks cache `Method` objects and use `setAccessible` once.
- [ ] **Security implications** — reflection bypasses access control, which is why it is restricted post-Java 9.

---

**Related folders:** → [15 · IO & NIO](../15-IO-NIO/README.md) · ← [13 · Enums](../13-Enums/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
