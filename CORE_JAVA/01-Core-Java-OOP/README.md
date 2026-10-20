# 01 — Core Java OOP · Interview Notes Index

> **Pillar 1 of the interview.** Every Java interview — junior to principal — starts here.
> The goal of this folder is not "know the 4 pillars" but **be able to defend a design decision**
> when the interviewer pushes back.

| Priority | Chapters | Why It Matters |
|----------|----------|----------------|
| ⭐⭐⭐⭐⭐ | [06 · Interface vs Abstract Class](06-OOP-Interface-vs-Abstract-Class.md) | Asked in ~90% of interviews |
| ⭐⭐⭐⭐⭐ | [05 · Polymorphism Deep Dive](05-OOP-Polymorphism-Deep-Dive.md) | The heart of OOP; drives Spring DI |
| ⭐⭐⭐⭐⭐ | [08 · equals/hashCode/toString](08-OOP-equals-hashCode-toString.md) | Direct coding question + HashMap tie-in |
| ⭐⭐⭐⭐⭐ | [12 · Critical & Tricky Scenarios](12-OOP-Critical-and-Tricky-Interview-Scenarios.md) | The "gotcha" bank — read last |
| ⭐⭐⭐⭐ | [01 · Foundations & 4 Pillars](01-OOP-Foundations-and-4-Pillars.md) | Foundation for everything else |
| ⭐⭐⭐⭐ | [04 · Inheritance & Diamond Problem](04-OOP-Inheritance-Types-and-Diamond-Problem.md) | Composition-vs-inheritance debate |
| ⭐⭐⭐⭐ | [03 · Encapsulation vs Abstraction](03-OOP-Encapsulation-vs-Abstraction.md) | Classic 2-minute question |
| ⭐⭐⭐ | [07 · final/static/volatile/transient](07-OOP-Keywords-final-static-volatile-transient.md) | Keyword-level precision |
| ⭐⭐⭐ | [13 · Variance (Co/Contra/Invariant)](13-OOP-Covariance-Contravariance-Invariance.md) | Links to Generics + Spring |
| ⭐⭐⭐ | [11 · Advanced Design & Object Class](11-OOP-Advanced-Design-and-Object-Class.md) | `Object` method contracts |
| ⭐⭐ | [02 · Access Modifiers](02-OOP-Access-Modifiers.md) | Quick wins |
| ⭐⭐ | [09 · Immutable Classes](09-OOP-Immutable-Classes.md) | Links to Concurrency + records |
| ⭐⭐ | [10 · Nested, Inner & Anonymous Classes](10-OOP-Nested-Inner-and-Anonymous-Classes.md) | Lambdas & callbacks prep |

---

## 📚 Recommended Reading Order

```
Day 1  →  01 Foundations ──▶ 02 Access Modifiers ──▶ 03 Encapsulation vs Abstraction
Day 2  →  04 Inheritance ──▶ 05 Polymorphism ──▶ 06 Interface vs Abstract Class
Day 3  →  07 Keywords ──▶ 08 equals/hashCode ──▶ 09 Immutables
Day 4  →  10 Nested/Anonymous ──▶ 11 Advanced & Object ──▶ 13 Variance
Day 5  →  12 Critical & Tricky Scenarios   (the interview-simulation day)
```

---

## 🎯 Most Asked in Interviews

| Question | Chapter | Why It’s Asked |
|----------|---------|----------------|
| Interface vs Abstract class — when do you pick which? | [06](06-OOP-Interface-vs-Abstract-Class.md) | Design judgement |
| How does `HashMap` use `equals`/`hashCode`? | [08](08-OOP-equals-hashCode-toString.md) | Links 2 folders |
| Is `final` enough for thread safety? | [07](07-OOP-Keywords-final-static-volatile-transient.md) | Traps candidates |
| Why not multiple inheritance of state? | [04](04-OOP-Inheritance-Types-and-Diamond-Problem.md) | Diamond problem |
| How do you model a "payment method" that can be many things? | [06](06-OOP-Interface-vs-Abstract-Class.md) + [13](13-OOP-Covariance-Contravariance-Invariance.md) | Real-world modelling |
| What is the difference between compile-time and runtime polymorphism? | [05](05-OOP-Polymorphism-Deep-Dive.md) | Foundation check |
| Why did we move from `abstract class` to `interface` for services? | [06](06-OOP-Interface-vs-Abstract-Class.md) | Spring context |

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **Covariance/contravariance** — most candidates cannot state `List<? extends Number>` vs `List<? super Integer>` correctly.
- [ ] **`Object` method contracts** — `finalize()` is deprecated since Java 9; `clone()` is shallow; `equals` symmetry.
- [ ] **Anonymous vs lambda vs local class** — capture semantics differ.
- [ ] **Inner class retaining the outer instance** — memory leak in long-lived listeners.

---

## ✅ Self-Check Checklist

- [ ] I can explain the 4 pillars with a **different real-world example** each time (banking, e-commerce, gaming).
- [ ] I can argue **interface vs abstract class** without saying "interfaces are for multiple inheritance" as the *only* reason.
- [ ] I can write a correct `equals`/`hashCode` pair using `Objects.equals` + `Objects.hash` in under 2 minutes.
- [ ] I know why `hashCode` must be **consistent with** `equals` and what breaks otherwise.
- [ ] I can explain why `default` methods were added in Java 8 and what the conflict rule is.
- [ ] I can state the **Liskov Substitution Principle** with a code example that violates it.
- [ ] I can answer "is composition always better than inheritance?" with nuance.

---

**Related folders:** → [02 · Java Memory Model](../02-Java-Memory-Model/README.md) · → [10 · SOLID Principles](../10-SOLID-Principles/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
