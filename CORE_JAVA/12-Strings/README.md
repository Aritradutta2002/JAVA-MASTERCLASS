# 12 — Strings & String Internals · Interview Notes Index

> Strings are deceptively deep. `==` vs `equals` is the easy part. The hard parts —
> the string pool moving from PermGen to heap, the `invokedynamic` string-concat rewrite,
> and `substring` copying in Java 7+ — are what separate a senior from a mid-level candidate.

| Priority | Chapter | Why It Matters |
|----------|---------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · String Internals — Complete Guide](01-String-Internals.md) | Pool, immutability, concat, `substring`, `intern`, performance |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| `==` vs `equals` for Strings | `==` compares **references**; `equals` compares **characters**. |
| Where does the string pool live? | **PermGen (Java 6)** → **Heap (Java 7+)**; interned strings are in the heap's *interned* region since Java 7. |
| Is `String` really immutable? | Yes — but `substring` on Java 6 shared the backing `char[]` (a leak); Java 7+ copies. |
| Why is `StringBuffer` thread-safe but `StringBuilder` isn't? | `StringBuffer` methods are `synchronized`. |
| What is `intern()`? | Returns the canonical instance for a given value; `==` becomes true. |
| Why use `StringBuilder` in loops? | Each `+` creates a new `String` + new `char[]` → O(n²) copying. |
| Is `new String("abc") == "abc"`? | **No** — different references, equal values. |
| `split` vs `split("\\s+")` vs `Pattern`? | `split` uses regex; trailing empty strings are removed; `Pattern` is faster for repeated use. |

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **Compact strings (JDK 9+)** — `String` stores `byte[]` + a coder flag (`LATIN1`/`UTF16`), halving memory for ASCII.
- [ ] **`String.concat` vs `+`** — `+` is compiled to `invokedynamic` + `StringConcatFactory` since Java 9.
- [ ] **Memory arithmetic** — how many characters fit in 1 MB of `String` data (and why the answer differs between JDK 8 and 9+).
- [ ] **`String.matches` vs `Pattern.matches`** — `matches` requires a **full match**, `find` is a substring search.
- [ ] **Internationalisation** — `Locale`, `ResourceBundle`, `String.format` with `%` and `MessageFormat`.

---

**Related folders:** → [13 · Enums](../13-Enums/README.md) · ← [11 · DSA](../11-DSA/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
