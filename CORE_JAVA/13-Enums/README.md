# 13 — Enums · Interview Notes Index

> Enums are a small, precise topic. Know the **internals** (what the compiler generates)
> and the **performance story** (`EnumSet`/`EnumMap` beat `HashSet`/`HashMap`), and you
> will ace this round.

| Priority | Chapter | Why It Matters |
|----------|---------|----------------|
| ⭐⭐⭐⭐ | [01 · Enum Deep Dive](01-Enum-Deep-Dive.md) | Compiler internals, constant-specific bodies, `EnumMap`/`EnumSet`, ordinal traps |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| What does the compiler generate for an enum? | A `final` class extending `java.lang.Enum` with `public static final` constants + a `$VALUES` array. |
| Can you extend an enum? | **No** — implicitly `final`. But an enum *can* implement an interface. |
| Can you `new` an enum? | **No** — the constructor is implicitly `private`. |
| Is `ordinal()` safe to persist? | **No** — it changes if you reorder the constants. Use a stable `code` field. |
| `==` or `equals` for enums? | `==` — enums are singletons per constant. |
| `EnumSet` vs `HashSet`? | `EnumSet` is a **bit-vector** — O(1) `contains`, far smaller. Use it. |
| `EnumMap` vs `HashMap`? | `EnumMap` is a **single array** indexed by `ordinal()` — cache-friendly, O(1). |
| What if a `switch` on an enum misses a constant? | `NullPointerException` (unboxing a null enum) or no branch matched. |
| How do you serialise an enum? | By `name` — deserialisation uses `readResolve` → canonical instance. |

---

## 🧠 Enum Internals — Visual

```
public enum Color { RED, GREEN, BLUE }

compiles to (simplified):

public final class Color extends Enum<Color> {
    public static final Color RED   = new Color("RED", 0);
    public static final Color GREEN = new Color("GREEN", 1);
    public static final Color BLUE  = new Color("BLUE", 2);

    private static final Color[] $VALUES = { RED, GREEN, BLUE };  // clone()d by values()

    private final String name;
    private final int    ordinal;

    private Color(String name, int ordinal) { super(name, ordinal); }  // implicitly private
    public static Color[] values() { return $VALUES.clone(); }
    public static Color valueOf(String n) { return Enum.valueOf(Color.class, n); }
}
```

> **Caption:** `values()` clones a `$VALUES` array on every call → it is **O(n)**, which is why
> you cache it in a `static final` field for hot loops. `valueOf()` is O(1) via a name hash map.

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **Constant-specific class bodies** — an enum with an abstract method and a body per constant.
- [ ] **Enum with an interface** — the classic "each constant has a different behaviour" trick.
- [ ] **`EnumSet` regular vs jumbo** — 64 elements vs `long[]` bit-vectors.
- [ ] **`EnumMap` and `null` keys** — `EnumMap` supports null keys; `HashMap` supports one null key; `EnumSet` supports neither.
- [ ] **Switch on enum with `default`** — required to avoid the "unhandled constant" bug.

---

**Related folders:** → [14 · Reflection & Annotations](../14-Reflection-Annotations/README.md) · ← [12 · Strings](../12-Strings/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
