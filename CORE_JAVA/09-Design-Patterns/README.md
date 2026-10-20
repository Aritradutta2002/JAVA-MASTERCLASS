# 09 — Design Patterns · Interview Notes Index

> Design patterns are **not** about memorising 23 names. They are about recognising
> *forces* in code — and being able to justify, with trade-offs, why you picked one over another.
> Interviewers reward the candidate who says "**this pattern, and here's what I'd do instead**".

| Priority | Chapter | Why It Matters |
|----------|---------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · Design Patterns — Complete Guide](01-Design-Patterns.md) | All 23 GoF + Spring/Java real-world usage + counter-questions |

---

## 🎯 The 5 That Actually Get Asked

| Pattern | Frequency | The Interview Angle |
|---------|-----------|---------------------|
| **Singleton** | ★★★★★ | "How do you make it thread-safe? Why is `volatile` needed in DCL?" |
| **Factory / Abstract Factory** | ★★★★★ | "How does Spring create beans? How do you hide the `new`?" |
| **Builder** | ★★★★★ | "Why not a constructor with 15 params? How does it differ from a record?" |
| **Strategy** | ★★★★★ | "How do you avoid a giant `if-else`? How does Spring pick a strategy?" |
| **Observer** | ★★★★☆ | "How do you implement event-driven flows? Kafka vs in-JVM listeners?" |

Also high-frequency: **Proxy** (Spring AOP), **Decorator** (`BufferedReader`), **Adapter**,
**Flyweight** (String pool), **Template Method** (Spring `AbstractApplicationContext`).

---

## 🧭 Choosing a Pattern — Decision Flowchart

```
                    Need to hide "new" / decouple creation?
                              │
                 ┌────────────┴────────────┐
                 ▼                         ▼
         Same product type?          Different product families?
                 │                         │
                 ▼                         ▼
          Factory Method            Abstract Factory
                 │
        Need many optional args /    Yes ──▶ Builder
        immutable object?
                 │
        Need to create copies?  ──▶ Prototype
                 │
        Exactly one instance?  ──▶ Singleton
```

```
                    Structure problems
        ┌───────────────┼────────────────┐
        ▼               ▼                ▼
  Interface shape   Wrap & delegate   Simplify a facade
  mismatch?           + add behaviour
        │               │                │
        ▼               ▼                ▼
     Adapter        Decorator         Facade
                                    Proxy (same shape, different impl)
```

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **"When NOT to use a pattern"** — over-engineering is the #1 criticism. Have a real answer.
- [ ] **Strategy vs Command** — both wrap behaviour; Strategy has one interchangeable algorithm, Command is a request object with lifecycle.
- [ ] **Decorator vs Proxy** — decorator has the *same type*, proxy implements a *different interface*.
- [ ] **Abstract Factory vs Factory Method** — AF creates a family, FM creates one product.
- [ ] **Observer vs Pub/Sub** — in-process vs brokered.
- [ ] **Why `enum` is the best Singleton** — plus the real-world objection that singletons hurt testability and microservices.

---

## ✅ Self-Check Checklist

- [ ] I can name all 23 GoF patterns and categorise them correctly.
- [ ] For each of Singleton, Factory, Builder, Strategy, Observer, Proxy — I can give a **Spring or JDK** example.
- [ ] I can implement each of the 5 core patterns in Java without looking it up.
- [ ] I can answer "why is a singleton an anti-pattern?" with a nuanced answer.
- [ ] I can explain how the Strategy pattern relates to `Comparator` and Spring's `@Qualifier`.

---

**Related folders:** → [10 · SOLID Principles](../10-SOLID-Principles/README.md) · ← [08 · Concurrency](../08-Multithreading-Concurrency/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
