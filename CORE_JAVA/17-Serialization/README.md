# 17 — Serialization · Interview Notes Index

> Java's built-in serialization is **legacy but still asked**, and — more importantly — it is
> a **critical security topic**: unsafe deserialisation is one of the highest-severity
> vulnerability classes in the Java ecosystem (CWE-502).

| Priority | Chapters | Why It Matters |
|----------|----------|----------------|
| ⭐⭐⭐⭐ | [01 · Serialization Basics](01-Serialization-Basics.md) | Mechanism, `serialVersionUID`, `transient`, stream format |
| ⭐⭐⭐⭐ | [02 · Custom Serialization](02-Custom-Serialization.md) | `writeObject`/`readObject`/`readResolve`/`writeReplace`/`Externalizable` |
| ⭐⭐⭐⭐ | [03 · Best Practices](03-Serialization-Best-Practices.md) | Security, performance, alternatives (JSON/Protobuf/Jackson) |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| What does `serialVersionUID` do? | A compatibility fingerprint. Mismatch → `InvalidClassException`. |
| What happens if you don't declare it? | The compiler generates one from the class signature — so *any* change breaks deserialisation. |
| What is `transient`? | The field is **not** serialised and is `null`/default after deserialisation. |
| Can you deserialise a `final` field? | Not via the default mechanism — it is set by the constructor, not the stream. Hence `readResolve`/`readObject` workarounds. |
| `readResolve` vs `readObject`? | `readResolve` **replaces** the instance; `readObject` **mutates** it. |
| Why is Java serialization a security risk? | Untrusted data → gadget chains → RCE. Java 9+ added `ObjectInputFilter`; Java 17 hardened the JDK further. |
| What is a deserialisation gadget chain? | A chain of existing classes whose `readObject` methods chain together to execute code. |
| Why prefer Jackson/JSON? | Human-readable, schema-agnostic, no `serialVersionUID` churn, faster, safer. Java serialization is slow and brittle. |

---

## 🧠 The Serialized Stream Format

```
┌──────────────────────────────────────────────────────────┐
│  STREAM_HEADER   magic (0xACED) + version (0x0005)      │
├──────────────────────────────────────────────────────────┤
│  CLASS DESC      class name, serialVersionUID, flags,     │
│                  field descriptors (name, type, flags)    │
├──────────────────────────────────────────────────────────┤
│  OBJECT DATA     superclass data, then fields, then       │
│                  custom writeObject data                 │
├──────────────────────────────────────────────────────────┤
│  ... repeats per object in the graph ...                 │
└──────────────────────────────────────────────────────────┘
```

> **Caption:** The format is a *graph walk*, not a flat record — that is why object identity
> (`==` vs `equals`) and cyclic references survive a round trip.

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **`serialVersionUID` compatibility rules** — what is and isn't a breaking change.
- [ ] **Inheritance & serialisation** — a non-serialisable superclass needs an explicit no-arg constructor.
- [ ] **`readObjectNoData`** — called when the stream contains no data for the class.
- [ ] **`ObjectInputFilter`** — the JDK's allow-list defence.
- [ ] **Performance** — Java serialization is significantly slower than Jackson/Protobuf.
- [ ] **Records & serialisation** — records get a canonical constructor; `readObject` interplay.

---

**Related folders:** → [18 · Testing](../18-Testing/README.md) · ← [16 · JDBC](../16-JDBC/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
