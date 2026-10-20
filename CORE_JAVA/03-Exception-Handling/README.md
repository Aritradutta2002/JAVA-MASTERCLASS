# 03 — Exception Handling · Interview Notes Index

> A small folder with a **large** interview footprint. Exception design is one of the fastest
> signals of whether a developer writes production code or just compiles classroom code.

| Priority | Chapter | Why It Matters |
|----------|---------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · Exception Handling — Complete Guide](01-Exception-Handling.md) | Hierarchy, try-with-resources, custom exceptions, best practices |

---

## 📚 What to Master

| Concept | Interview Weight |
|---------|------------------|
| Exception hierarchy (Throwable → Error/Exception → Runtime/Checked) | ★★★★★ |
| `try`/`catch`/`finally` execution order, incl. `finally` with `return` | ★★★★★ |
| try-with-resources, auto-closeable, **suppressed exceptions** | ★★★★★ |
| Custom exception design (business vs technical errors) | ★★★★☆ |
| Multi-catch, rethrow with precise types (`throws` inference) | ★★★☆☆ |
| Exception in static/instance initialiser | ★★★☆☆ |
| Exception as control flow — when **not** to use it | ★★★★☆ |

---

## 🎯 Most Asked in Interviews

| Question | Trap |
|----------|------|
| Does `finally` always execute? | No — `System.exit()`, JVM crash, `SIGKILL`. Also `finally` running on an `OutOfMemoryError` is not guaranteed. |
| Is `return` in `finally` a bad idea? | Yes — it silently swallows exceptions. Always explain. |
| Which exception can you catch but should never? | `Error` (`OutOfMemoryError`, `StackOverflowError`) — never catch it; let the JVM die. |
| `catch (Exception e)` — always wrong? | Not always, but it must be re-thrown or handled with a specific action. Explain the difference between catching-all-and-ignoring vs catching-all-and-wrapping. |
| Checked vs unchecked — the real rule? | Unchecked = programming error / unrecoverable, checked = recoverable condition the caller can act on. `IOException` is checked; `NullPointerException` is not. |
| Why is `SQLException` checked but `NullPointerException` isn't? | Historical design (JVM had no `finally`); a famous API wart. |

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **Suppressed exceptions** — `try-with-resources` keeps the primary exception and attaches the close failure.
- [ ] **Exception chaining** — `new MyException(msg, cause)` vs swallowing the cause.
- [ ] **Rethrow precision** — `catch (IOException | SQLException e) { throw new MyException(e); }` compiles with a precise `throws` clause.
- [ ] **Logging correctly** — log the throwable as the last argument, never `e.getMessage()` alone.

---

## ✅ Self-Check Checklist

- [ ] I can draw the full exception hierarchy from memory in 30 seconds.
- [ ] I can explain the exact output of a `try`/`finally` block that contains `return` — and why it is a bug.
- [ ] I can write a try-with-resources block where the close failure is preserved and explain what `getSuppressed()` returns.
- [ ] I can design a custom exception hierarchy for an e-commerce domain (`OrderException` → `PaymentFailedException`, `InventoryException`) and defend it.
- [ ] I can explain why catching `Throwable` is a code smell.

---

**Related folders:** → [04 · Collections](../04-Collections-Framework/README.md) · ← [02 · Java Memory Model](../02-Java-Memory-Model/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
