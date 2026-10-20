# 10 — SOLID Principles · Interview Notes Index

> SOLID is where interviews stop being about syntax and start being about **design judgement**.
> The question is never "what does SRP mean?" — it's *"show me where this codebase violates
> the Open/Closed Principle and how you'd fix it."*

| Priority | Chapter | Why It Matters |
|----------|---------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · SOLID Principles — Complete Guide](01-SOLID-Principles.md) | 5 principles with ❌→✅ refactors, Spring examples, counter-questions |

---

## 🎯 The 5 Principles — One-Line Each

| # | Principle | One-Line Definition | Smell It Fixes | Spring Example |
|---|-----------|---------------------|----------------|----------------|
| S | **Single Responsibility** | One reason to change. | The "god class" with 40 methods | `@Service` per use case |
| O | **Open/Closed** | Open for extension, closed for modification. | 20 `if (type == ...)` branches | `Strategy` + `@Qualifier` |
| L | **Liskov Substitution** | Subtypes must be substitutable. | `ArrayList.add` throwing `UnsupportedOperationException` | Spring's `List` contract |
| I | **Interface Segregation** | Small, focused interfaces. | A 20-method interface with 1 impl | `Iterable`, `AutoCloseable` |
| D | **Dependency Inversion** | Depend on abstractions. | `new PostgresRepository()` inside a service | Constructor injection |

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **LSP is not about inheritance only** — it is about *behavioural substitutability*.
- [ ] **ISP does not mean "many tiny interfaces"** — it means *client-driven* segregation.
- [ ] **DIP is not "always use an interface"** — it is about *direction of dependency* in the module/package graph.
- [ ] **SRP is about reasons to change, not the number of methods** — a 100-method class with one reason to change is fine.
- [ ] **"Is inheritance always bad?"** — composition over inheritance is a default, not a law.

---

## 🔁 The Related Design Laws (bonus)

| Law | Meaning |
|-----|---------|
| **DRY** | Don't Repeat Yourself — but beware premature abstraction (rule of three). |
| **KISS** | Keep It Simple Stupid. |
| **YAGNI** | You Aren't Gonna Need It — don't build for imagined futures. |
| **Tell, Don't Ask** | Ask an object to do something rather than inspecting its state and deciding for it. |
| **Law of Demeter** | Talk only to your immediate friends (no `a.getB().getC().doX()` chains). |

---

## ✅ Self-Check Checklist

- [ ] I can write a violating class for each of the 5 principles and then the refactored version.
- [ ] I can cite a real Spring/JDK example for each principle.
- [ ] I can explain how SOLID principles interact (e.g. DIP + ISP → many small interfaces).
- [ ] I can answer "does using interfaces everywhere violate YAGNI?" with a balanced argument.
- [ ] I can point at a code smell in a well-known open-source project and explain the violation.

---

**Related folders:** → [11 · DSA](../11-DSA/README.md) · ← [09 · Design Patterns](../09-Design-Patterns/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
