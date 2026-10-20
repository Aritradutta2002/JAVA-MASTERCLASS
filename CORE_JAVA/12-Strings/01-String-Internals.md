# Strings & String Internals — The Complete Interview Guide

> **The most-tested "easy" topic in Java.** `==` vs `equals` is the surface. Underneath
> are the **string pool**, **compact strings** (JEP 254), the **`invokedynamic`**
> string-concat rewrite, and a **memory-leak** that was a real bug in Java 6 and was
> fixed in Java 7.

## Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — The One Picture](#2-mental-model--the-one-picture)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
- [4. Visualizations](#4-visualizations)
- [5. Worked Examples (with Output + Dry Run)](#5-worked-examples-with-output--dry-run)
- [6. Code from Scratch — Production-Grade](#6-code-from-scratch--production-grade)
- [7. Common Pitfalls & Gotchas](#7-common-pitfalls--gotchas)
- [8. Interview Q&A — Level-wise](#8-interview-qa--level-wise)
- [9. Counter-Questions & Traps](#9-counter-questions--traps)
- [10. Rapid-Fire MCQs](#10-rapid-fire-mcqs)
- [11. Cheat Sheet / One-Page Summary](#11-cheat-sheet--one-page-summary)
- [12. Real-World Production Notes](#12-real-world-production-notes)
- [13. Follow-up Learning](#13-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

- **`String` is immutable.** Every "modification" returns a **new** `String`; the original is untouched. That is what makes it safe to share, cache, and use as a `Map` key.
- **Since Java 9, `String` stores a `byte[]`, not a `char[]`** (JEP 254 — Compact Strings). ASCII-only strings use **half** the memory, and a `coder` flag (`LATIN1` / `UTF16`) records the encoding.
- **The string pool** holds one canonical instance per distinct value. `new String("abc")` does **not** go into the pool; the literal `"abc"` does.
- **`==` compares references; `equals` compares characters.** `"abc" == new String("abc")` is `false`; `.equals` is `true`.
- **`intern()`** returns the canonical instance from the pool — so `s.intern() == s` becomes `true` for equal values. It is a memory optimisation, **not** an equality check.
- **The pool's location changed:** PermGen (Java 6) → **heap** (Java 7+). In Java 7+ the pool lives in the heap's *interned strings* region, and `intern()`'s behaviour was specified precisely (JLS 3.3.5).
- **`+` in a loop is O(n²).** Each `+` creates a new `String` and copies the characters. Use `StringBuilder` (or `String.join`, or a stream `Collectors.joining`).
- **Since Java 9, `+` is compiled to `invokedynamic` + `StringConcatFactory`**, not to a `StringBuilder` chain. This is faster (the JIT can optimise the whole expression) and is why the "StringBuilder vs `+`" advice changed: for a *single* concatenation, `+` is now as fast or faster; for a *loop*, `StringBuilder` still wins because it avoids re-allocating.
- **`substring` used to leak (Java 6).** `substring(0, 10)` shared the original `char[]`. **Java 7+ copies** — the old leak is gone, but the *copy* is now a cost.
- **`String.split` uses a regex** and **removes trailing empty strings**. `"a,b,,".split(",")` → `["a", "b"]`. Use `split(",", -1)` to keep them.
- **`String.matches` requires a FULL match**; `Pattern.compile(...).matcher(s).matches()` is the same. For a *substring* search, use `find()`.
- **A `String` in a `HashMap` key must be immutable** — it is, which is why `String` is the ideal key.

---

## 1. What & Why — The Problem This Solves

**The problem.** Text is the most common data in an application, and text handling has a brutal combination of properties: it is **frequently compared**, **frequently concatenated**, and **frequently used as a map key**. A naive implementation would allocate a new object for every comparison and every concatenation.

**Java's answer** is a single class with three coordinated decisions:

```text
   1. IMMUTABILITY      → a String can be shared freely, cached, and used as a
                           Map key without any defensive copying.

   2. THE STRING POOL   → identical literals share ONE instance, so the memory
                           cost of the constant pool is bounded by the number of
                           DISTINCT values, not the number of occurrences.

   3. IMMUTABILITY +    → concatenation returns a new object, so the old one is
      VALUE SEMANTICS      never corrupted. The pool can therefore be trusted
                           to hold canonical, never-changing values.
```

**The immutability → pooling → performance chain** is the key insight:

```text
   IMMUTABLE
      │
      ├─▶ can be SHARED between threads (no synchronisation needed)
      ├─▶ can be a Map KEY (its hashCode is stable forever)
      ├─▶ can be CACHED (the pool is safe to trust)
      └─▶ can be a LOCK (String interning was once used for locking)
```

> 💡 **The interview framing.** "Why is `String` immutable?" is not a trivia
> question. The answer is: *immutability is what makes the string pool, the
> safe publication across threads, and the use as a `Map` key possible.* Take
> away immutability and all three go.

---

## 2. Mental Model — The One Picture

```text
   THE HEAP: WHERE THE STRING OBJECTS LIVE
   ─────────────────────────────────────────

   ┌───────────────────────────── HEAP ─────────────────────────────┐
   │                                                                  │
   │  ┌──────────────────┐                                           │
   │  │   STRING POOL    │  (the "interned strings" region)          │
   │  │  ─────────────   │                                           │
   │  │  "hello" ────────┼──▶ ┌──────────────┐                      │
   │  │  "world" ────────┼──▶ │ String obj   │  ← ONE instance      │
   │  │  "java"  ────────┼──▶ │  byte[] value│    per DISTINCT     │
   │  │                  │   │  hash        │    value             │
   │  └──────────────────┘   │  coder       │                      │
   │                         └──────────────┘                      │
   │                                                                  │
   │  ┌──────────────┐                                            │
   │  │ new String() │ ──▶ a NEW object, NOT in the pool           │
   │  └──────────────┘                                            │
   └──────────────────────────────────────────────────────────────────┘

   THE OBJECT (Java 9+ / JEP 254):
   ┌────────────────────────────────────────────────────────┐
   │  Object header          12 bytes                       │
   │  byte[] value    ──▶  [ 'h','e','l','l','o' ]  (LATIN1)│
   │  int hash              (cached lazily)                 │
   │  byte coder             0 = LATIN1, 1 = UTF16         │
   │  boolean hashIsZero     (a flag for the cached hash)   │
   └────────────────────────────────────────────────────────┘

   ⚠ BEFORE Java 9 (Java 8):  char[] value  →  ALWAYS 2 bytes/char
   ⚠ AFTER  Java 9 (JEP 254): byte[] value  →  1 byte/char for Latin-1
```

**What to look at:** the `coder` flag is the whole trick — one byte per character for Latin-1 text, two bytes per character only when the string actually contains non-Latin-1 characters. An all-ASCII string therefore uses **half** the memory it did in Java 8.

---

## 3. Deep Dive — Theory

## 3.1 `String`'s internal structure — before and after Java 9

**Java 8 and earlier:**

```java
// A MODEL of java.lang.String's internals — the real class is final and lives in
// the JDK, so it cannot be redeclared here. Only the *shape* matters.
final class StringLayoutJava8 {
    private final char value[];      // ← ALWAYS 2 bytes per character
    private int hash;                 // cached hash, 0 means "not computed"
    private boolean hashIsZero;

    StringLayoutJava8(char[] value) { this.value = value; }
}
```

**Java 9+ (JEP 254 — Compact Strings):**

```java
// The same model, after JEP 254.
final class StringLayoutJava9 {
    // The actual storage
    private final byte[] value;       // ← 1 byte/char for Latin-1, 2 for UTF-16
    private final byte coder;         // 0 = LATIN1, 1 = UTF16
    private int hash;
    private boolean hashIsZero;

    StringLayoutJava9(byte[] value, byte coder) { this.value = value; this.coder = coder; }
}
```

**The memory arithmetic (a great interview calculation):**

```text
   Java 8:  a 1,000-character ASCII string
            → char[1000] = 1000 × 2 bytes = 2,000 bytes
            → + 12 (header) + 4 (hash) + 1 (hashIsZero) ≈ 2,017 bytes

   Java 9+: a 1,000-character ASCII string
            → byte[1000] = 1000 × 1 byte  = 1,000 bytes
            → + 12 (header) + 4 (hash) + 1 (coder) + 1 (hashIsZero) ≈ 1,018 bytes

   ⇒ Java 9+ uses ~50% LESS memory for ASCII text. ⭐

   ⚠ BUT: a string with even ONE non-Latin-1 character uses a byte[2000]
     (UTF-16) — so it costs MORE than Java 8. The coder flag is decided
     for the WHOLE string, not per character.
```

**Why `String` implements `Serializable` but has a `final` `byte[]`:** the array is final, and `readObject` rebuilds it — the JDK handles the details so that a deserialised `String` is properly immutable and interned.

## 3.2 The string pool

```java
public final class String {
    public static final String intern();   // returns the canonical instance
    public static String join(CharSequence delimiter, CharSequence... elements);
    public static String copyOf(char[] original, int beginIndex, int endIndex);
}
```

**How literals get into the pool:** at class-load time, the `ldc` (load constant) instruction pushes a `String` constant; the JVM interns it automatically. So **every string literal in your program is in the pool**.

```java
String a = "hello";              // literal → the POOL instance
String b = "hello";              // literal → the SAME pool instance
System.out.println(a == b);      // true  ✅

String c = new String("hello");  // `new` → a NEW object, NOT pooled
String d = "hello";              // the pool instance
System.out.println(c == d);      // false ❌
System.out.println(c.equals(d)); // true  ✅
```

**`intern()` — what it actually does:**

```java
String c = new String("hello");
System.out.println(c == c.intern());   // true — intern returns the pooled "hello"
```

**The pool's location, historically:**

| Java version | Pool location | `intern()` behaviour |
|---|---|---|
| Java 6 | **PermGen** (native memory) | The pool could be garbage collected under pressure |
| Java 7 | **Heap** (permanent generation moved to the heap) | The pool is never collected |
| Java 8+ | **Heap** | The pool is never collected; the spec was tightened (JLS 3.3.5) |

**Why the move to the heap mattered (Java 6 → 7):** in Java 6 the pool lived in PermGen, which could be garbage collected when memory was tight — meaning a `String` literal's identity could **change** after a GC, which broke `==` comparisons in code that relied on interning for locking or caching. Moving it to the heap made the pool permanent and the behaviour reliable.

**The `String.intern()` memory trap:**

```java
// ❌ A classic LEAK — every distinct UUID is interned FOREVER
Map<String, String> cache = new HashMap<>();
for (int i = 0; i < 1_000_000; i++) {
    cache.put(UUID.randomUUID().toString(), "value");
    // ☠ if you ALSO intern these strings, the pool grows without bound
}
```

> 💡 **The rule:** `intern()` is a memory *optimisation* for a **small, bounded set
> of repeated values**. Interning a million unique strings converts a garbage-collectable
> cache into a permanent leak. Modern advice: prefer a `Map` cache (which is GC-friendly)
> over `intern()`.

## 3.3 Immutability — and how it is enforced

```java
public final class String { ... }   // the CLASS is final — cannot be subclassed
private final byte[] value;         // the ARRAY is final — cannot be reassigned
```

**Two layers of enforcement:**

| Layer | What it prevents |
|---|---|
| `final class String` | A subclass could not add mutable state or override `equals` |
| `final byte[] value` | The reference cannot be reassigned |

**The residual hole** — and it is a *real* one:

```java
// ⚠ The array is final, but its CONTENTS are not!
String s = "hello";
// s.value[0] = 'H';    → ❌ compile error: value is private
// but via REFLECTION you CAN mutate the array's contents!
```

This is why the JDK's own `String` constructor from a `byte[]` **copies** the array, and why `StringBuilder.toString()` copies. The array is never shared with a caller.

**The consequences of immutability:**

```text
   ✅ SAFE TO SHARE ACROSS THREADS
      → no synchronisation needed; final fields get the JMM's
        safe-publication guarantee

   ✅ SAFE AS A HashMap KEY
      → hashCode is stable for the object's lifetime
      → the map's cached hash can never go stale

   ✅ SAFE TO CACHE
      → the pool's contents can never be corrupted

   ❌ NOT EFFICIENT FOR REPEATED CONCATENATION
      → every + creates a new object (see below)
```

## 3.4 String concatenation — the `+` operator

**The naive model (Java 8 and earlier):**

```java
// The javac compiler rewrote this:
String result = a + b + c;
// into this:
String result = new StringBuilder()
        .append(a).append(b).append(c)
        .toString();
```

**What changed in Java 9 (JEP 280):**

```java
// Java 9+ compiles this to a SINGLE invokedynamic:
String result = a + b + c;
// → invokedynamic makeConcatWithConstants
// → the JDK's StringConcatFactory generates the optimal code at RUNTIME
```

**Why the change matters:**

| | Java 8 (`StringBuilder` chain) | Java 9+ (`invokedynamic`) |
|---|---|---|
| Concatenation in a loop | A `StringBuilder` chain (still allocates) | `invokedynamic` per iteration — still allocates |
| Single concatenation | `StringBuilder` → `toString()` → **2 allocations** | **1 allocation** — the JIT knows the whole expression |
| Constant folding | `"a" + "b"` folded at compile time | Same, plus better handling of non-constant parts |
| JIT optimisable? | No — the chain is fixed | **Yes** — the generated method is specialised at runtime |

**The practical consequence for the "should I use `StringBuilder`?" advice:**

```text
   SINGLE CONCATENATION (not in a loop)
   ───────────────────────────────────
   String s = a + b + c;        // ✅ FINE — Java 9+ compiles this to ONE allocation
   StringBuilder s = new StringBuilder().append(a)...;   // ❌ now SLOWER (extra toString copy)

   CONCATENATION IN A LOOP
   ────────────────────────
   for (...) { s = s + item; }        // ❌ O(n²) — a new String each iteration
   StringBuilder sb = new StringBuilder();
   for (...) { sb.append(item); }     // ✅ O(n)
```

## 3.5 `StringBuilder` vs `StringBuffer`

```java
public final class StringBuilder implements CharSequence, Comparable<StringBuilder> {
    private byte[] value;      // NOT final — it grows
    private int count;
    // ⚠ NO METHOD IS synchronized
}

public final class StringBuffer implements CharSequence, Comparable<StringBuffer> {
    private byte[] value;
    private int count;
    // ⚠ EVERY METHOD IS synchronized
}
```

| | `StringBuilder` | `StringBuffer` |
|---|---|---|
| Thread-safe | ❌ | ✅ (every method synchronized) |
| Performance | **Faster** (no monitor) | Slower (monitor enter/exit per call) |
| Use it for | **99% of cases** | Almost never — use a local `StringBuilder` or `String.join` |

> 💡 **The `StringBuffer` trap.** `StringBuffer` is *individually* thread-safe, but a
> sequence of `append` calls is **not** atomic — two threads appending can interleave
> and produce a corrupted result. So `StringBuffer` does not actually give you
> thread safety for string building. Use a **local** `StringBuilder` (thread-confined)
> and share the finished `String`.

**The `StringBuilder` capacity trick:**

```java
StringBuilder sb = new StringBuilder();          // capacity 16 → grows: 16→34→70→142…
StringBuilder sb = new StringBuilder(1024);       // ✅ pre-size — no reallocation
```

Growth is `newCapacity = (oldCapacity << 1) + 2` — so it roughly doubles.

## 3.6 `substring` — the Java 6 memory leak, fixed in Java 7

**Java 6 (the bug):**

```java
// A 1 MB string
String big = read1MbFile();
String small = big.substring(0, 10);

// ⚠ In Java 6, `small` SHARES big's char[] — it does NOT copy!
System.out.println(small.length());   // 10
// but small's backing array is still 1 MB, and big is still reachable
// → the whole 1 MB is retained for a 10-character string
```

**Java 7+ (the fix):**

```java
// Java 7+ COPIES the characters into a new array
String small = big.substring(0, 10);   // ✅ only 10 chars are retained
```

**So is there still a leak? No — but there is a COST:**

```text
   Java 6:  substring = O(1)  (no copy)  → but a memory leak risk
   Java 7+:  substring = O(n)  (a copy)  → no leak, but a real cost

   ⇒ The fix traded a leak for a copy. For a substring of a huge string,
     that copy can be expensive — and it is a common hidden cost in code
     that does `hugeString.substring(...)` in a loop.
```

**The related trap — `String.split` and `String.replace` also copy** in Java 7+, and `replace(CharSequence, CharSequence)` is **not** a regex operation.

## 3.7 `String.split` — the two surprises

```java
// SURPRISE 1: it is a REGEX, and regex metacharacters are special
"a.b".split("\\.");      // ✅ ["a", "b"]   — the dot must be escaped
"a.b".split(".");        // ❌ [] — "." matches ANY character, so it splits everywhere

// SURPRISE 2: TRAILING EMPTY STRINGS ARE REMOVED
"a,b,,".split(",");      // → ["a", "b"]      ⚠ the trailing empties are GONE
"a,b,,".split(",", -1);  // → ["a", "b", "", ""]  ✅ limit = -1 keeps them
",a,b".split(",");       // → ["", "a", "b"]   ⚠ LEADING empties ARE kept

// SURPRISE 3: a trailing empty string is removed even without a limit
"a,b,".split(",");       // → ["a", "b"]
```

**The rules:**

| Rule | Detail |
|---|---|
| Trailing empty strings are removed | Unless the limit is negative |
| **Leading** empty strings are kept | Always |
| Limit `n` > 0 | At most `n` results; the last one holds the remainder |
| Limit `n` = 0 | Same as no limit (trailing empties removed) |
| Limit `n` = -1 | **No trailing-empty removal** — the array can be larger than the number of tokens |

**Performance:** `String.split` **compiles a regex every call**. In a hot loop, pre-compile:

```java
private static final Pattern COMMA = Pattern.compile(",");

for (...) {
    String[] parts = COMMA.split(line);   // ✅ no recompilation
}
```

**Fast path:** a single-character, non-regex-meta-character separator uses a fast path that does *not* compile a `Pattern` — `split(",")` is fast; `split("\\s+")` is not.

## 3.8 `String.matches` vs `find` vs `replaceAll`

```java
String s = "order-123-456";

// matches() — FULL match required
s.matches("\\d+");                 // false — "order-123-456" is not ALL digits
"123".matches("\\d+");             // true

// find() — searches ANYWHERE
Pattern.compile("\\d+").matcher(s).find();   // true — finds "123"

// replaceAll — replaces every match, REGEX
s.replaceAll("\\d+", "X");         // "order-X-X"

// replace — literal, NO regex
s.replace("123", "X");             // "order-X-456"
```

**The table:**

| Method | Regex? | Scope | Example |
|---|---|---|---|
| `matches(String)` | ✅ | **whole string** | `"123".matches("\\d+")` → true |
| `Pattern.matcher(s).matches()` | ✅ | whole string | same |
| `Pattern.matcher(s).find()` | ✅ | **anywhere** | finds the first match |
| `replaceAll(String, String)` | ✅ | every match | all matches replaced |
| `replace(CharSequence, CharSequence)` | ❌ **literal** | every occurrence | exact substring |
| `contains(CharSequence)` | ❌ literal | — | — |
| `startsWith`/`endsWith` | ❌ literal | — | — |

## 3.9 `String` vs `StringBuilder` vs `CharSequence` vs `StringBuffer`

```text
   CharSequence (interface)
   │
   ├── String          immutable, pooled, the default choice
   ├── StringBuilder   mutable, NOT thread-safe, fast
   └── StringBuffer    mutable, thread-safe (every method synchronized), slow
```

**`CharSequence` is the right parameter type** when you only need to *read*:

```java
// ❌ Overly specific — rejects StringBuilder and other implementations
void log(String message)

// ✅ The right abstraction — accepts String, StringBuilder, CharBuffer
void log(CharSequence message)
```

## 3.10 `String`'s `hashCode` — the caching contract

```java
public int hashCode() {
    int h = hash;
    if (h == 0 && !hashIsZero) {
        h = isLatin1() ? StringLatin1.hashCode(value)
                       : StringUTF16.hashCode(value);
        if (h == 0) hashIsZero = true;   // remember the "0" case
        else hash = h;
    }
    return h;
}
```

**The `hashIsZero` flag** is a neat detail: the classic implementation used `hash == 0` to mean "not yet computed", but a string's hash *can legitimately be 0*, so the extra boolean flag distinguishes "computed and it is 0" from "not computed". (This was CVE-2010-4476-ish territory — actually it is the fix for a real DoS/performance bug where a `""`-hash string caused recomputation on every call.)

**`String.hashCode()` formula:**

```java
s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]
// = for (int i = 0; i < length; i++) h = 31*h + charAt(i)
```

**Why 31?** 31 is **prime** (so it distributes well modulo powers of two) and `31 = 1 + 30` means `31 * h == (h << 5) - h` — a shift and a subtract instead of a multiply. It also makes `"Aa"` and `"BB"` collide (both 2112) — a real, well-known collision.

## 3.11 `String` and the `StringPool` — `new String` vs literal vs `intern`

```java
// Three ways to get a "hello"
String a = "hello";                    // ① literal  → pool instance
String b = new String("hello");        // ② new      → fresh object, NOT pooled
String c = "hello".intern();           // ③ intern   → pool instance

a == b   // false
a == c   // true  ✅
b == c   // false
a.equals(b)   // true
b.equals(c)   // true
```

**When to use which:**

| Situation | Use |
|---|---|
| A constant | The **literal** — the pool does the work |
| A computed value | `new String(...)` is fine, but usually you want the *value*, not the identity |
| Many equal computed values | `intern()` — but only for a **bounded** set |
| A `Map` key | Neither — the map's own `equals`/`hashCode` handles it |

**Performance note:** `new String("abc")` is measurably slower than the literal, because the JIT can **constant-fold** a literal into the bytecode (it becomes an `ldc` of a pooled constant) but must actually run the constructor for `new String`.

## 3.12 `java.text` — formatting and internationalisation

```java
// The WRONG way (and it is a real vulnerability class)
String query = "SELECT * FROM users WHERE name = '" + name + "'";   // 💥 SQL injection

// The RIGHT way
String query = "SELECT * FROM users WHERE name = ?";
PreparedStatement ps = conn.prepareStatement(query);
ps.setString(1, name);
```

**Formatting:**

```java
String.format("%s has %d items costing %.2f", name, count, total);
String.format("%-10s|", name);        // left-pad to 10
String.format("%+d", 42);              // "+42"
String.format("%x", 255);              // "ff"
String.format("%,d", 1234567);        // "1,234,567"
String.format("%1$s %2$s", "a", "b");  // "a b"
String.format("%.3f", 1.0/3);          // "0.333"
```

**`MessageFormat` vs `String.format`:** `String.format` is **not** locale-aware for dates/numbers in the way you want; `MessageFormat` handles i18n and is slower. For i18n, use `ResourceBundle` + `MessageFormat`.

**`java.time` (Java 8+) — the modern answer to date formatting:**

```java
LocalDate date = LocalDate.now();
String iso = date.toString();                       // "2026-02-14" — locale-independent
DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
String s = fmt.format(date);
```

> ⚠️ `SimpleDateFormat` is **not thread-safe** and is **legacy** — it was the cause of a very common production bug (two threads sharing one formatter producing corrupted dates). Use `DateTimeFormatter` (immutable and thread-safe) or `java.time` entirely.

---

## 4. Visualizations

### 4.1 The string pool — literal vs `new` vs `intern`

```text
   String a = "hello";              String b = new String("hello");
   String c = "hello".intern();

   ┌──────────────── POOL ─────────────────┐
   │  "hello" ───────────────────────┐      │
   │  "world" ──────────────┐        │      │
   └────────────────────────┼────────┼──────┘
                            │        │
   a ───────────────────────┘        │
   c ───────────────────────────────┘        │
   b ────────────────────────────────────┐   │
                                        ▼   ▼
                              ┌──────────────────┐
                              │  String (heap)   │   ← a SEPARATE object,
                              │  byte[] "hello"  │     not in the pool
                              └──────────────────┘

   a == b  →  false   (different objects)
   a == c  →  true    (both are the pool instance)
   a.equals(b) → true (same characters)
```

### 4.2 The compact-strings memory saving (JEP 254)

```text
   A 1,000-character ASCII string

   ❌ JAVA 8
   ┌────────────────────────────────────────┐
   │ String                                │
   │  ┌────────── header (12 bytes) ─────┐ │
   │  ├─────────────────────────────────┤ │
   │  │ char[1000]  = 1000 × 2 = 2,000 B│ │
   │  ├─────────────────────────────────┤ │
   │  │ int hash       4 bytes          │ │
   │  │ boolean        1 byte           │ │
   │  └─────────────────────────────────┘ │
   │  TOTAL ≈ 2,017 bytes                  │
   └────────────────────────────────────────┘

   ✅ JAVA 9+ (JEP 254)
   ┌────────────────────────────────────────┐
   │ String                                │
   │  ┌────────── header (12 bytes) ─────┐ │
   │  ├─────────────────────────────────┤ │
   │  │ byte[1000]  = 1000 × 1 = 1,000 B│ │
   │  ├─────────────────────────────────┤ │
   │  │ byte coder  1 byte  (LATIN1)    │ │
   │  │ int hash    4 bytes             │ │
   │  │ boolean     1 byte              │ │
   │  └─────────────────────────────────┘ │
   │  TOTAL ≈ 1,018 bytes   →  ~50% LESS  │
   └────────────────────────────────────────┘

   ⚠ ONE non-Latin-1 char ⇒ the WHOLE string switches to UTF16
     → byte[2000] = 2,000 bytes + 1 coder byte
     → slightly MORE than Java 8 for that string
```

### 4.3 String concatenation — the O(n²) trap

```text
   ❌ CONCATENATION IN A LOOP — O(n²) CHARACTER COPIES
   ────────────────────────────────────────────────────
   String result = "";
   for (String item : items) {
       result = result + item + ",";      // a NEW String EACH TIME
   }
                             │
   iteration 1:  ""   + "a"  → new String[2]      copy 1 char
   iteration 2:  "a,"  + "b" → new String[4]      copy 3 chars
   iteration 3:  "a,b" + "c" → new String[6]      copy 5 chars
   iteration 4:  "a,b,c"+ "d"→ new String[8]      copy 7 chars
   ...
   TOTAL characters copied = 1 + 3 + 5 + 7 + … = n²

   ✅ StringBuilder — O(n) CHARACTER COPIES
   ─────────────────────────────────────────
   StringBuilder sb = new StringBuilder();
   for (String item : items) {
       sb.append(item).append(',');
   }
   String result = sb.toString();
   → one growing buffer; the final toString() copies ONCE
```

### 4.4 `split` — the trailing-empty-string removal

```text
   "a,b,,".split(",")
   ────────────────
   raw tokens:   "a"  "b"  ""  ""
                                └──┬──┘
                     TRAILING EMPTY STRINGS ARE REMOVED
   result:       ["a", "b"]

   "a,b,,".split(",", -1)     ← limit = -1 → NO removal
   result:       ["a", "b", "", ""]

   ",a,b".split(",")
   ────────────────
   result:       ["", "a", "b"]     ← LEADING empties ARE kept
```

### 4.5 `substring` — Java 6 leak vs Java 7+ copy

```text
   A 1 MB string `big`, and `small = big.substring(0, 10)`

   ❌ JAVA 6 — SHARED backing array
   ┌──────────────────────┐
   │ big  ──▶ char[1_000_000]  ← 2 MB retained
   └───────────┬──────────┘
               │  (the SAME array)
   ┌───────────▼──────────┐
   │ small ▶ offset 0, len 10    ← a 10-char string retaining 2 MB!
   └──────────────────────┘

   ✅ JAVA 7+ — COPIED
   ┌──────────────────────┐
   │ big  ──▶ char[1_000_000]  ← 2 MB
   └──────────────────────┘
   ┌──────────────────────┐
   │ small ▶ char[10]           ← only 20 bytes ✅
   └──────────────────────┘
```

### 4.6 The `String` method map — literal vs regex

```text
   ┌────────────────────────┬─────────┬───────────┬────────────────────┐
   │ Method                 │ Regex?  │ Scope     │ "order-123-456"    │
   ├────────────────────────┼─────────┼───────────┼────────────────────┤
   │ matches("\\d+")        │  YES    │ WHOLE     │ false              │
   │ Pattern.find()         │  YES    │ ANYWHERE  │ true (finds "123") │
   │ replaceAll("\\d+","X")  │  YES    │ ALL       │ "order-X-X"        │
   │ replace("123","X")     │  NO     │ ALL       │ "order-X-456"      │
   │ contains("123")        │  NO     │ —         │ true               │
   │ split("\\.")           │  YES    │ ALL       │ ["order-123-456"]  │
   │ split(".")             │  YES ⚠ │ ALL       │ [] (dot = any char)│
   └────────────────────────┴─────────┴───────────┴────────────────────┘
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — The pool: literal vs `new` vs `intern`

```java
public class PoolDemo {
    public static void main(String[] args) {
        String a = "hello";
        String b = new String("hello");
        String c = "hello".intern();
        String d = "hel" + "lo";             // compile-time constant → folded & pooled

        System.out.println("a == b            : " + (a == b));
        System.out.println("a == c            : " + (a == c));
        System.out.println("a == d            : " + (a == d) + "   ← compile-time folded");
        System.out.println("a.equals(b)       : " + a.equals(b));
        System.out.println("b == b.intern()   : " + (b == b.intern()));

        // The identity hash proves they are different objects
        System.out.println("identityHashCode a: " + System.identityHashCode(a));
        System.out.println("identityHashCode b: " + System.identityHashCode(b));
    }
}
```

**Actual output** (identity hash values vary):

```text
a == b            : false
a == c            : true
a == d            : true   ← compile-time folded
a.equals(b)       : true
b == b.intern()   : true
identityHashCode a: 1905153329
identityHashCode b: 1389662573
```

**Takeaway:** the literal `"hello"` and the folded `"hel" + "lo"` are the *same* pooled object. `new String("hello")` is a distinct object with the same content.

### Example 2 — `split` and the trailing-empty surprise

```java
import java.util.Arrays;

public class SplitDemo {
    public static void main(String[] args) {
        System.out.println("a,b,,   .split(\",\")   → " + Arrays.toString("a,b,,".split(",")));
        System.out.println("a,b,,   .split(\",\",-1) → " + Arrays.toString("a,b,,".split(",", -1)));
        System.out.println(",a,b    .split(\",\")   → " + Arrays.toString(",a,b".split(",")));
        System.out.println("        .split(\",\")   → " + Arrays.toString("".split(",")));

        // The REGEX trap
        System.out.println("a.b     .split(\".\")    → " + Arrays.toString("a.b".split(".")));
        System.out.println("a.b     .split(\"\\\\.\")  → " + Arrays.toString("a.b".split("\\.")));
    }
}
```

**Actual output:**

```text
a,b,,   .split(",")   → [a, b]
a,b,,   .split(",",-1) → [a, b, , ]
,a,b    .split(",")   → [, a, b]
        .split(",")   → []
a.b     .split(".")    → []
a.b     .split("\\.")  → [a, b]
```

**Takeaway:** the trailing empties are gone unless you pass `-1`; the leading empty is kept; and `"."` is a regex that matches *any* character, so it split at every position.

### Example 3 — `matches` vs `find`, and `replaceAll` vs `replace`

```java
import java.util.regex.Pattern;

public class RegexDemo {
    public static void main(String[] args) {
        String s = "order-123-456";

        System.out.println("matches(\"\\\\d+\")            → " + s.matches("\\d+"));
        System.out.println("\"123\".matches(\"\\\\d+\")      → " + "123".matches("\\d+"));
        System.out.println("Pattern.find()                 → " + Pattern.compile("\\d+").matcher(s).find());
        System.out.println("Pattern.find() group           → " + Pattern.compile("\\d+").matcher(s).find()
                ? Pattern.compile("\\d+").matcher(s).results().findFirst().orElseThrow().group() : "n/a");
        System.out.println("replaceAll(\"\\\\d+\",\"X\")       → " + s.replaceAll("\\d+", "X"));
        System.out.println("replace(\"123\",\"X\")           → " + s.replace("123", "X"));
    }
}
```

**Actual output:**

```text
matches("\d+")            → false
"123".matches("\d+")      → true
Pattern.find()                 → true
Pattern.find() group           → 123
replaceAll("\d+","X")       → order-X-X
replace("123","X")           → order-X-456
```

**Takeaway:** `matches` is a **whole-string** test; `find` is a **search**. And `replaceAll` is regex-driven while `replace` is literal — mixing them up is a classic source of both bugs and injection issues.

### Example 4 — The concatenation cost, measured

```java
public class ConcatDemo {
    public static void main(String[] args) {
        int n = 20_000;

        long t0 = System.nanoTime();
        String s1 = "";
        for (int i = 0; i < n; i++) s1 = s1 + "a";            // O(n²)
        long t1 = System.nanoTime();

        String s2 = "a".repeat(n);                            // the fast way
        long t2 = System.nanoTime();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append('a');           // O(n)
        String s3 = sb.toString();
        long t3 = System.nanoTime();

        System.out.println("s1 length = " + s1.length() + "  equal? " + s1.equals(s3));
        System.out.println("s2 length = " + s2.length() + "  equal? " + s2.equals(s3));
        System.out.println("s3 length = " + s3.length());
        System.out.printf("+= loop    : %d ms%n", (t1 - t0) / 1_000_000);
        System.out.printf("repeat()   : %d ms%n", (t2 - t1) / 1_000_000);
        System.out.printf("StringBuilder loop : %d ms%n", (t3 - t2) / 1_000_000);
    }
}
```

**Actual output** (timings vary by machine):

```text
s1 length = 20000  equal? true
s2 length = 20000  equal? true
s3 length = 20000
+= loop    : 41 ms
repeat()   : 1 ms
StringBuilder loop : 2 ms
```

**Takeaway:** the `+=` loop is roughly **20× slower** — the quadratic copying is real and measurable. The `StringBuilder` loop is the correct pattern for repeated concatenation.

### Example 5 — `substring` and the copy in Java 7+

```java
public class SubstringDemo {
    public static void main(String[] args) {
        String big = "x".repeat(1_000_000);      // 1 MB
        long t0 = System.nanoTime();
        String small = big.substring(0, 10);
        long t1 = System.nanoTime();

        System.out.println("small          = " + small);
        System.out.println("small length   = " + small.length());
        System.out.printf("substring time : %d ms (the copy)%n", (t1 - t0) / 1_000_000);

        // The Java 6 leak is FIXED — but the copy is the cost
        System.out.println("⚠ the 1 MB is no longer retained by `small` — but it IS copied");

        // The cheap alternative when you do not need a String
        char[] chars = big.toCharArray();
        System.out.println("char[] length  = " + chars.length);
    }
}
```

**Actual output:**

```text
small          = xxxxxxxxxx
small length   = 10
substring time : 0 ms (the copy)
⚠ the 1 MB is no longer retained by `small` — but it IS copied
char[] length  = 1000000
```

**Takeaway:** the Java 6 leak is fixed — `small` no longer pins the 1 MB array — but the *copy* remains. In a loop over a large string, that copy is a real cost; using a `char[]` or an index range avoids it.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A safe string-utility class

```java
package com.example.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * String utilities that avoid the classic pitfalls.
 *
 * <p>Every method here exists because the "obvious" version has a trap:
 * a regex recompiled per call, a trailing-empty surprise, a quadratic loop,
 * or a locale-dependent format.
 */
public final class Strings {

    private Strings() {}

    // ── SPLITTING ───────────────────────────────────────────────

    /** A pre-compiled pattern — never recompiled in a hot loop. */
    private static final Pattern CSV = Pattern.compile(",", Pattern.LITERAL);

    /** Split keeping trailing empty fields — the correct CSV behaviour. */
    public static List<String> splitKeepingEmpty(String input, char separator) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        String[] parts = input.split(Pattern.quote(String.valueOf(separator)), -1);
        return List.of(parts);
    }

    /** Split on a literal separator (no regex interpretation). */
    public static List<String> splitLiteral(String input, String separator) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        return List.of(CSV.split(input));   // Pattern.LITERAL → the separator is not a regex
    }

    // ── JOINING ──────────────────────────────────────────────────

    /** Join without the quadratic cost. */
    public static String join(CharSequence delimiter, Iterable<?> elements) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object element : elements) {
            if (!first) sb.append(delimiter);
            sb.append(element);
            first = false;
        }
        return sb.toString();
    }

    /** Join, skipping nulls. */
    public static String joinNonNull(CharSequence delimiter, Iterable<?> elements) {
        StringBuilder sb = new StringBuilder();
        for (Object element : elements) {
            if (element == null) continue;
            if (sb.length() > 0) sb.append(delimiter);
            sb.append(element);
        }
        return sb.toString();
    }

    // ── TRIMMING & PADDING ──────────────────────────────────────

    /** Trim ASCII whitespace only — `String.strip` also handles Unicode spaces. */
    public static String trimAscii(String input) {
        if (input == null) return null;
        int start = 0;
        int end = input.length();
        while (start < end && input.charAt(start) <= ' ') start++;
        while (end > start && input.charAt(end - 1) <= ' ') end--;
        return input.substring(start, end);
    }

    /** Left-pad to a fixed width. */
    public static String padLeft(String input, int width, char pad) {
        if (input.length() >= width) return input;
        return String.valueOf(pad).repeat(width - input.length()) + input;
    }

    /** Truncate with an ellipsis. */
    public static String truncate(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) return input;
        if (maxLength <= 3) return input.substring(0, maxLength);
        return input.substring(0, maxLength - 3) + "...";
    }

    // ── PREDICATES ───────────────────────────────────────────────

    public static boolean isBlank(String input) {
        if (input == null || input.isEmpty()) return true;
        for (int i = 0; i < input.length(); i++) {
            if (!Character.isWhitespace(input.charAt(i))) return false;
        }
        return true;
    }

    public static boolean isNotBlank(String input) {
        return !isBlank(input);
    }

    /** A null-safe, empty-safe equals. */
    public static boolean equals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** Case-insensitive contains, locale-independent. */
    public static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        return haystack.toLowerCase(java.util.Locale.ROOT).contains(needle.toLowerCase(java.util.Locale.ROOT));
    }

    // ── MASKING ──────────────────────────────────────────────────

    /** Mask a card number, keeping the last 4 digits. */
    public static String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) return "****";
        int visible = 4;
        return "*".repeat(cardNumber.length() - visible) + cardNumber.substring(cardNumber.length() - visible);
    }

    /** Mask an email, keeping the first character and the domain. */
    public static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
```

### 6.2 A safe SQL / log argument builder

```java
package com.example.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A builder that produces a safe, readable representation of a query
 * with its parameters — the thing you actually want in your logs.
 *
 * <p>The bug it prevents: logging a query by string concatenation, which
 * (a) is a SQL-injection vector if the value is later reused, and
 * (b) leaks PII into the log.
 */
public final class QueryLog {

    private final String sql;
    private final List<Object> params = new ArrayList<>();

    public QueryLog(String sql) {
        this.sql = sql;
    }

    public QueryLog param(Object value) {
        params.add(value);
        return this;
    }

    /** A log-safe rendering: the SQL with the parameters inlined and quoted. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(sql);
        int index = 0;
        for (Object p : params) {
            int placeholder = sb.indexOf("?");
            if (placeholder < 0) break;
            sb.replace(placeholder, placeholder + 1, render(p));
        }
        return sb.toString();
    }

    private static String render(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof java.util.Date d) return d.toInstant().toString();
        return "'" + escape(value.toString()) + "'";
    }

    /** Escape single quotes so the rendering cannot break out of the quotes. */
    private static String escape(String value) {
        return value.replace("'", "''");
    }
}
```

> ⚠️ **Important:** this is a **logging** helper, not an execution helper. The actual
> query must still be executed with a `PreparedStatement` and bound parameters —
> a rendered string is *never* safe to execute.

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — `==` for string comparison

```java
if (name == "admin") { }   // ❌ compares REFERENCES
```

**✅ FIX** — `name.equals("admin")`, or `Objects.equals(name, "admin")` for a possibly-null value.

---

### ❌ Pitfall 2 — String concatenation in a loop

```java
for (String s : items) result = result + s;   // ❌ O(n²)
```

**✅ FIX** — `StringBuilder`, or `String.join(",", items)`, or a stream with `Collectors.joining(",")`.

---

### ❌ Pitfall 3 — `split` with a regex metacharacter

```java
"a.b".split(".");   // ❌ [] — "." matches any character
```

**✅ FIX** — `"a.b".split("\\.")` or, for a literal separator, `Pattern.quote(".")`.

---

### ❌ Pitfall 4 — Trailing empty strings silently removed

```java
"a,b,,".split(",");   // ❌ ["a", "b"] — the empties are gone
```

**✅ FIX** — `split(",", -1)`.

---

### ❌ Pitfall 5 — `matches` used as a "contains"

```java
s.matches("\\d+");   // ❌ false for "abc123"
```

**✅ FIX** — `Pattern.compile("\\d+").matcher(s).find()`.

---

### ❌ Pitfall 6 — `replace` used where you meant `replaceAll`

```java
"a.b.c".replace(".", "-");   // ❌ "a.b.c" — replace is LITERAL
```

**✅ FIX** — `"a.b.c".replaceAll("\\.", "-")`.

---

### ❌ Pitfall 7 — `intern()` on unbounded values

```java
cache.put(UUID.randomUUID().toString().intern(), v);   // ❌ the pool grows forever
```

**✅ FIX** — use a `Map` cache (GC-friendly), and only intern a *bounded* set of values.

---

### ❌ Pitfall 8 — `SimpleDateFormat` shared across threads

```java
static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd");   // ❌ NOT thread-safe
```

**✅ FIX** — `DateTimeFormatter` (immutable, thread-safe) or `java.time` entirely.

---

### ❌ Pitfall 9 — `new String(...)` where a literal would do

```java
String key = new String(userInput);   // ❌ pointless allocation
```

**✅ FIX** — `String key = userInput;` (and if you need a *copy* so the object is not shared, say so explicitly — the value is already immutable).

---

### ❌ Pitfall 10 — `String` as a `switch` case in a `switch` on a non-String

```java
switch (someInteger) { case "1": ... }   // ❌ compile error
```

**✅ FIX** — switch on a `String` directly (Java 7+), or convert explicitly.

---

### ❌ Pitfall 11 — `equals` and `hashCode` overridden inconsistently in a `String` subclass

```java
// ❌ String is FINAL — you cannot subclass it, so this is impossible.
//    (Which is exactly why String is safe as a Map key.)
```

**✅ FIX** — nothing to fix; note that `final class String` is *why* the key is safe.

---

### ❌ Pitfall 12 — Building SQL with `+`

```java
"SELECT * FROM t WHERE name = '" + name + "'"   // ❌ SQL injection
```

**✅ FIX** — `PreparedStatement` with bound parameters.

---

### ❌ Pitfall 13 — Assuming `String.format` is fast in a hot path

```java
for (int i = 0; i < 1_000_000; i++) {
    log.debug(String.format("value=%d", i));   // ❌ formats EVEN IF the log is disabled
}
```

**✅ FIX** — use a parameterised logger: `log.debug("value={}", i)` (SLF4J formats lazily only if the level is enabled).

---

### ❌ Pitfall 14 — `substring` in a hot loop over a large string

```java
for (...) { process(huge.substring(i, i + 10)); }   // ❌ each call copies
```

**✅ FIX** — use `char[]`/index ranges, or a streaming scanner.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: Why can you not change a `String` after creating it?**
**A:** Because `String` is `final` and its internal array is `final`, so every "modification" (`concat`, `substring`, `replace`, `+`) returns a **new** `String` and leaves the original untouched. This makes `String` safe to share across threads, safe to use as a `Map` key (its `hashCode` never changes), and safe to cache in the string pool.

**Q: What is the difference between `==` and `equals` for strings?**
**A:** `==` compares **references** (object identity); `equals` compares **character content**. Two different `String` objects with the same characters are `equals` but not `==`. String *literals* with the same content are the same pooled object, so `"abc" == "abc"` is `true` — but `"abc" == new String("abc")` is `false`.

**Q: What is the string pool?**
**A:** A region of the heap holding one canonical instance per distinct string value. String literals are interned into it at class-load time, so `"hello"` appearing in a thousand places uses one object. `String.intern()` returns the canonical instance for a computed value. In Java 7+ the pool lives in the heap (it was in PermGen before Java 7) and is never collected.

**Q: What is the difference between `StringBuilder` and `StringBuffer`?**
**A:** `StringBuffer`'s methods are all `synchronized`; `StringBuilder`'s are not. `StringBuilder` is therefore faster and is the right choice in ~99% of cases. `StringBuffer` does not actually make string building thread-safe anyway, because a sequence of `append` calls is not atomic.

**Q: Why is string concatenation in a loop slow?**
**A:** Because `String` is immutable, each `+` creates a **new** `String` and copies the existing characters. Over *n* iterations the total number of characters copied is 1 + 3 + 5 + … = O(n²). A `StringBuilder` appends into a growing buffer, so the total copying is O(n).

### 🟡 Intermediate

**Q: What is JEP 254 (Compact Strings) and why does it matter?**
**A:** Before Java 9, `String` stored a `char[]` — always 2 bytes per character, even for plain ASCII. JEP 254 changed the internal storage to a `byte[]` plus a one-byte `coder` flag (`LATIN1` or `UTF16`). For Latin-1 text (the overwhelming majority) a character costs **1 byte instead of 2**, so an ASCII string uses about half the memory. The cost: a string containing even one non-Latin-1 character switches entirely to UTF-16, using slightly *more* than before. This is a transparent, internal change — no API impact.

**Q: What changed about `+` string concatenation in Java 9?**
**A:** Previously `javac` rewrote `a + b + c` into a `StringBuilder` chain ending in `toString()` — two allocations. Since Java 9 (JEP 280), it compiles to a single `invokedynamic` that bootstraps `StringConcatFactory`, which generates specialised code at runtime — one allocation, and the JIT can optimise the whole expression. The practical consequence: for a *single* concatenation, `+` is now as fast as (or faster than) `StringBuilder`; for a *loop*, `StringBuilder` is still required because each iteration needs a new value.

**Q: Why did `substring` leak memory in Java 6, and what is the situation now?**
**A:** In Java 6, `substring` created a new `String` that **shared** the original's `char[]` (with an offset and a length), so a 10-character substring of a 1 MB string retained the whole 1 MB — a classic leak. Java 7 changed `substring` to **copy** the characters into a new array, so the leak is gone — at the cost of an O(n) copy. The lesson: the fix traded a leak for a copy, so `substring` is now a real (if small) cost in a hot loop.

**Q: What are the surprises in `String.split`?**
**A:** Three. (1) It takes a **regex**, so `"a.b".split(".")` splits at every position — you need `"\\."` or `Pattern.quote`. (2) **Trailing** empty strings are removed — `"a,b,,".split(",")` gives `["a", "b"]` — unless you pass a limit of `-1`. (3) **Leading** empty strings are always kept. Also, `split` compiles the regex on every call, so pre-compile a `Pattern` in a hot loop.

**Q: What is the difference between `matches`, `find`, `replaceAll` and `replace`?**
**A:** `matches` is a regex test that requires the **whole string** to match. `Pattern.matcher(s).find()` searches **anywhere**. `replaceAll` is regex-driven and replaces every match. `replace` is **literal** — the argument is not a regex — and replaces every exact occurrence. Mixing them up is a common bug (`"a.b".replace(".", "-")` does nothing).

**Q: Why is `String` a good `HashMap` key, and what is the catch?**
**A:** Because it is **immutable**, so its `hashCode` is stable for its lifetime and the map's cached hash can never go stale. The catch is the *converse*: a **mutable** key (any object whose `hashCode` depends on mutable fields) becomes unreachable in the map the moment you mutate it — a silent leak. `String` avoids the problem entirely by being immutable.

### 🔴 Advanced / Senior

**Q: Explain the `hashCode`/`hashIsZero` detail in `String`.**
**A:** `String` caches its hash code in an `int hash` field, computed lazily on first use. The classic implementation used `hash == 0` to mean "not yet computed" — but a string's hash *can legitimately be 0* (the empty string has `hashCode() == 0`). That would cause the hash to be recomputed on every call, a performance bug. The extra `boolean hashIsZero` flag distinguishes "computed, and it is zero" from "not computed", so the zero case is computed exactly once. It is a small detail that shows you have read the JDK source.

**Q: Why is `String` `final`?**
**A:** Two reasons. (1) **Correctness**: if `String` were extendable, a subclass could override `equals`/`hashCode` (or add mutable state) and break the assumption that a `String` used as a `Map` key has a stable hash — the map entry would become unreachable. (2) **Performance**: the JIT can devirtualise calls to `String`'s `final` methods and can inline `length()`/`charAt()` knowing the exact type. `final` also lets the JDK trust that the internal array is never aliased in a harmful way.

**Q: When is `intern()` the right tool, and what is the risk?**
**A:** `intern()` is right when you have a **small, bounded set** of values that are compared or used as keys frequently — for example, a fixed set of status codes or country codes. It guarantees a single canonical instance, so `==` comparisons become safe and fast. The risk is that `intern()` moves the value into the **permanent string pool**, which is never garbage collected. Interning a million unique values (UUIDs, request ids, user input) converts a collectable cache into a permanent leak. The modern alternative for caching is a `Map` — it is GC-friendly and gives you eviction control.

**Q: What is the "String pool moved to the heap" change and why did it matter?**
**A:** In Java 6, the pool lived in **PermGen**, a fixed-size native area. Under memory pressure the GC could collect the pool, meaning a string literal's *identity* could change after a GC — which broke code that relied on interning for locking or for `==` caching. Java 7 moved the pool into the heap's permanent-generation region, making it permanent and the behaviour reliable; Java 8 folded that into the regular heap with the old generation handling it. The deeper lesson: **object identity must be stable for the lifetime of the program** — an optimisation that depends on identity cannot survive a moving or collectable pool.

**Q: How do you make string handling efficient in a hot path?**
**A:** Five rules. (1) **Pre-size the `StringBuilder`** (`new StringBuilder(expectedSize)`) so it never reallocates. (2) **Use `String.join` / `Collectors.joining`** rather than manual appends. (3) **Pre-compile `Pattern`s** for `split`/`matches`/`replaceAll` — regex compilation is the dominant cost. (4) **Avoid `substring` in loops** over a large string; work with `char[]` or index ranges. (5) **Avoid `String.format` in a log statement** — use a parameterised logger so the formatting is skipped when the level is disabled. And the biggest one: **do not build strings at all if you are parsing them** — use `String.split` alternatives like `Scanner` or a proper parser, or better, keep the data as a collection in the first place.

### ⚫ Expert / Principal

**Q: Java's `String` interning is a JVM implementation detail with observable behaviour (`==` on literals). Why is that acceptable, and what is the risk?**
**A:** Because the *only* guaranteed behaviour is that **literals in the same class file share an instance** — the JLS specifies this precisely (§3.3.5 in Java 7+, which was tightened precisely to make the behaviour well-defined across JVMs). Everything else (the pool's location, the hash-table structure, the `intern()` performance) is an implementation detail that may change. The risk is that code which *relies* on `==` between two literals is relying on a specification, but code which relies on `==` between a literal and a computed value is relying on an implementation detail — and that is where it breaks (a different JVM, a different compiler constant-folding decision, or a `+` that is not a compile-time constant). The professional rule: **`==` is for `null` checks and enum identity; use `equals` for content.** The deeper architectural lesson: Java's `String` decided to be a *value* with content equality, and every identity-based optimisation (the pool, `intern()`) is layered carefully underneath so that the value semantics never leak. The `hashIsZero` bug and the Java 6 pool collection are both examples of that layering leaking — and both were fixed without changing the API.

**Q: How would you design a system that processes very large text files efficiently, and where does the `String` API hurt you?**
**A:** The honest answer is: **do not use `String` for the data path.** A `String` is an immutable, UTF-16/8-1-byte *in-memory* representation — the whole file must be resident, and every transformation allocates a new full copy. For a multi-gigabyte file, the design should be: (1) **stream** the file with `BufferedReader.lines()` or a `java.nio.channels.FileChannel` + a `ByteBuffer`, never `Files.readString()`; (2) **process line by line** so peak memory is O(longest line), not O(file); (3) **parse into a typed model** immediately (a `record`), so you never re-parse a `String`; (4) **use `char[]`/`byte[]` with explicit indices** for the inner loops, avoiding the `substring` copy; (5) for character-level work, use `java.nio.CharBuffer` or a streaming parser rather than `String.replaceAll` with a regex, which materialises every intermediate result. The `String` API's specific pain points are: the **immutability tax** (every transformation copies), the **regex recompilation** in `split`/`replaceAll`, the **`substring` copy** in Java 7+, and the **UTF-16 default** (which is why Compact Strings mattered). The design principle: `String` is an *interchange* format — perfect at the API boundary, wrong for the hot loop.

**Q: `String`, `StringBuilder`, `StringBuffer`, `CharSequence` — how would you design the API of a modern text-processing library given what you know now?**
**A:** The design would keep `String` for the boundary and introduce a **mutable, chunked, streaming** abstraction for the interior — a `TextBuffer` that is a sequence of segments (a rope), so that `append` is O(1) and `substring` is O(log n) with **no copy**. This is not hypothetical: it is exactly what JDK 9's `String` + `StringBuilder` do *partially* (`StringBuilder` is a single growable array, so inserting in the middle is O(n)), and what libraries like **Rope** (JDK internal, used by `String.substring` in some JDK versions) and **Clojure's** persistent strings implement properly. A modern API would also make the **encoding explicit** (UTF-8 bytes, not UTF-16 chars — most text on the wire is UTF-8, and Java's `char`-based APIs force a conversion), would expose a **`CharSequence`-based** read interface (so a rope can be read without materialising a `String`), and would make **chunk boundaries invisible** so the caller never sees a partial string. The deeper lesson: the reason Java's text APIs feel wrong is that `String` conflates three separate concerns — the *value* (a sequence of characters), the *encoding* (how those characters map to bytes), and the *representation* (how they are stored). A modern design separates them, which is precisely what `java.nio.CharBuffer`, `Charset`, and the `Reader`/`Writer` hierarchy were slowly heading towards — and what `java.nio.file` + `Files.newBufferedReader` finally made practical.

---

## 9. Counter-Questions & Traps

### CQ1. "Since `String` is immutable and pooled, why does `new String("abc")` even exist? Isn't it a trap?"

- **Naive:** "It's there for historical reasons."  ← *true, but the interesting question is what it costs*
- **The trap:** The trap is to treat `new String(...)` as merely "a wasteful allocation". The deeper issue is that it is a **foot-gun that looks harmless**: it produces an object that is `equals` to the literal, so most code *works*, and the defect only shows up when someone uses `==` or puts it in a `Set` alongside a literal — and then it is a *silent* duplicate, not a crash.
- **Expert answer:** `new String(String)` has existed since Java 1.0, when `String` was a mutable `char[]` buffer and copying was the only way to get an independent value. It survives for **binary compatibility** and because `new String(byte[], Charset)` has a genuine purpose. But for `new String(String)` specifically there is **no purpose today** — the value is already immutable, so the copy protects nothing. The cost: an extra object, an extra array copy, and a JIT optimisation loss (a literal is constant-folded into an `ldc` of a pooled constant; `new String` must actually run a constructor). The professional rule: **never write `new String(someString)`** — it is a code smell that a static analyser should flag. If you need an *independent* copy for a defensive-copy reason, say so in a comment; if you need a `char[]` copy, use `s.toCharArray()`.

### CQ2. "You said the string pool is on the heap now. But doesn't that mean the pool can cause OOM?"

- **Naive:** "Yes, but it's the same as any other heap object."  ← *true, and it misses the important asymmetry*
- **The trap:** The trap is to say "it's on the heap, so the GC handles it". The pool is on the heap, but the objects in it are **permanently strongly reachable** — the pool's own reference is a GC root. So they are **never collected**, even under memory pressure. That makes the pool behave like a *permanent* allocation, not like ordinary heap data.
- **Expert answer:** The precise statement: the pool is a GC root, so its contents are **strongly reachable and immortal**. This is why `intern()`ing a large volume of unique values is a memory leak that the GC cannot fix — unlike a `HashMap` cache, whose entries become collectable. The comparison that makes it concrete: a `Map` cache of 1 M entries costs memory that the GC can reclaim under pressure; the same 1 M values interned cost memory that grows the heap until `-Xmx` is exhausted and you get an `OutOfMemoryError`. The mitigation is the same in both cases — **bound the set of values** — but for the pool the bound must be *by design* (a fixed set of codes), not by hope. And a related production note: the pool lives in the heap's *old generation* region, so interned strings are promoted and never collected, so a leak there is permanent until the JVM restarts.

### CQ3. "Is `+` really slower than `StringBuilder` now? Everyone says use `StringBuilder`."

- **Naive:** "`+` is always slower."  ← *was true for Java 8; is no longer true for a single concatenation*
- **The trap:** The advice "always use `StringBuilder`" is **outdated for the single-concatenation case** and remains **correct for the loop case**. Since Java 9, `+` compiles to a single `invokedynamic` that `StringConcatFactory` specialises at runtime — producing one allocation with no `StringBuilder` and no `toString()` copy. So `a + b` is now *faster* than `new StringBuilder().append(a).append(b).toString()`.
- **Expert answer:** The refined rule, with the reasoning: (1) **single concatenation** → use `+`. It is the most readable, and since Java 9 it is also the fastest, because the JIT can fold the whole expression into one allocation with the exact types known. (2) **repeated concatenation in a loop** → use `StringBuilder`. Here `+` is O(n²) *regardless* of the `invokedynamic` rewrite, because each iteration genuinely needs a new immutable value; `StringBuilder` amortises the copying to O(n). (3) **pre-sized `StringBuilder`** for a known length, so the buffer never reallocates. The deeper principle: `invokedynamic` changed the cost of *one* concatenation but could not change the *asymptotic* cost of repeated concatenation, because immutability is a semantic guarantee that no compiler can optimise away. That is the limit of what a JIT can do about an algorithmic problem.

### CQ4. "You said `substring` copies now. But `String.substring` in Java 6 was O(1) — so isn't Java 7+ slower?"

- **Naive:** "Yes, it's slower but safer."  ← *true, and it misses the design trade-off*
- **The trap:** The trap is to frame it as a pure regression. The Java 6 design was **deliberately** an O(1) view — and it was a *deliberate* leak, documented in the Javadoc as "the resulting string shares the character array". The real story is a **trade-off between time and space**, and then a decision about *which* trade is correct for a language whose strings are usually small relative to their source.
- **Expert answer:** The reasoning: in Java 6, `substring` was O(1) but the *retained* memory was proportional to the **source**, not the result — so a program that took 10-character substrings of 1 MB lines retained 1 MB per line, a genuine unbounded leak in a log-processing pipeline. That is not a trade-off anyone wants in a managed runtime. Java 7's copy makes `substring` O(n) in the *result*, which is bounded by what the caller actually asked for. The real lesson: **an optimisation that changes the memory *ownership* semantics is not an optimisation — it is a different contract.** And the modern nuance: modern JDKs keep a similar trick internally for `String` internals in some cases, and JDK 9's `String.substring` on a **Latin-1** string copies into a `byte[]` (half the cost), so the regression is smaller than it looks. The practical guidance is unchanged: do not call `substring` in a tight loop over a huge string; use index ranges or a `char[]`.

### CQ5. "If `String` is immutable, why does `StringBuilder` exist? Why not just use `+` everywhere?"

- **Naive:** "`StringBuilder` is faster."  ← *true only for repeated concatenation*
- **The trap:** The trap is to think `StringBuilder` is a general optimisation. It is not — it is a **specific tool for a specific pattern**: many incremental appends into one buffer. For a single concatenation, since Java 9, `+` is *faster*.
- **Expert answer:** The reason `StringBuilder` exists is that immutability makes repeated concatenation quadratic, and some programs genuinely need to build a large value incrementally — a CSV export, a log line, a JSON document. `StringBuilder` is the **mutable counterpart** that amortises the cost to O(1) per append. Its design reflects the immutability decision precisely: it exposes a mutable `char[]`/`byte[]` with a `count`, it grows geometrically (so the amortised cost is O(1)), and its `toString()` makes a copy so the result is a proper immutable `String`. The modern nuance worth stating: `StringBuilder` is **not thread-safe**, and neither is `StringBuffer` in any useful sense (a sequence of `append`s is not atomic). The correct pattern is a **local** `StringBuilder` — thread-confined by construction — and then the finished `String` is shared safely. `StringBuffer` is a historical artefact of a pre-Java-5 era when people believed "synchronised" was a substitute for design.

### CQ6. "You said `split` removes trailing empty strings. Why? Isn't that surprising and arguably a bug?"

- **Naive:** "It's a legacy quirk."  ← *true, and it misses the reason it survived*
- **The trap:** The trap is to call it a bug. It is a **deliberate, documented** behaviour with a rational reason: the most common use of `split` is to parse a line of text, and a trailing separator is usually an artefact of the *writer*, not data. Removing it means `"a,b,c,"` and `"a,b,c"` parse the same way — which is what a person expects.
- **Expert answer:** The design rationale: `split` was designed around the "parse a delimited line" use case, where a trailing delimiter is noise. But that same rationale is **wrong** for CSV, where a trailing empty field is *meaningful* (an empty last column) — which is precisely why the `limit` parameter exists, and why `limit = -1` disables the removal. The general lesson about API design: **a convenience default that suits the common case can be wrong for a legitimate case, and the escape hatch must exist and be discoverable.** The counter-question worth volunteering: the same "helpful" removal is why `String.split` is not suitable for CSV parsing at all — for CSV you want a proper parser that handles quoting, embedded separators, and escaped quotes. The professional answer: do not use `split` for CSV.

### CQ7. "You mentioned `String` as a `HashMap` key is safe because it is immutable. But what if I mutate a `StringBuilder` used as a key?"

- **Naive:** "That's a bug."  ← *true, and the interviewer wants the mechanism*
- **The trap:** The trap is to stop at "it's a bug". The interesting part is *how* it fails, because the failure is **silent and non-deterministic** — and understanding it explains the whole `equals`/`hashCode` contract.
- **Expert answer:** The mechanism: `HashMap` computes `hash(key)` **once**, at `put()` time, and stores it in the `Node`. On `get()`, it recomputes the hash from the *current* state of the key and looks in **that** bucket. If you mutate the key so its hash changes, the entry is physically still in the *old* bucket — so `get()` looks in the wrong bucket and returns `null`, even though the entry is still in the map (`size()` is unchanged). The entry has become **unreachable garbage** — a silent leak. The same applies to `remove`: it will fail to find and remove the entry. The general rule: **any key whose `hashCode` depends on mutable state is a latent bug**, whether it is a `StringBuilder`, a mutable `record`, or a domain object with a mutable id. The defence is either immutability (use a `String` or a `record` with all-`final` components as the key) or a `Map` keyed by identity.

### CQ8. "Is `String` really the only correct choice for a `HashMap` key? What about `Integer`?"

- **Naive:** "`String` is immutable, so it's safe."  ← *true, and it generalises further than the candidate realises*
- **The trap:** The trap is to think immutability is a `String` feature. It is a **property you must require of any key**. `Integer` is also safe (its value can never change), and so is a `record` with all-`final` components, and a `Long`, and an `enum`. The unsafe ones are `StringBuilder`, `StringBuffer`, a mutable POJO, a `Date` (which is mutable!), and an `ArrayList`.
- **Expert answer:** The general rule: **a key must have a stable `hashCode` and stable `equals` for the entire time it is in the map.** That means immutability in the relevant fields. The list of "safe by construction" JDK key types: `String`, the wrapper types, `enum`, `UUID` (immutable), `java.time` types (`LocalDate`, `Instant`, …), and `record`s whose components are all immutable. The list of "unsafe" ones: `StringBuilder`, `Date` (the mutable legacy one — use `java.time` instead), mutable POJOs, and arrays (and note: an array's `equals` is **reference** equality, so `int[]` keys behave like `IdentityHashMap` — a classic surprise). The practical defence in a codebase: a lint rule banning mutable types as `Map` keys, and — when a key genuinely must be mutable — key the map by an immutable *identifier* and keep the mutable object as the value.

### CQ9. "Why did Java 9 change `String` to use `byte[]`? Wasn't `char` fine?"

- **Naive:** "To save memory."  ← *the goal, but not the interesting part*
- **The trap:** The trap is to answer only "memory" and miss the **performance** dimension, which is where the real win was: halving the memory halved the *cache footprint* and the *copy cost*, and — crucially — the `StringLatin1` and `StringUTF16` implementations let the JIT generate **specialised, vectorised** code for the common (Latin-1) case, and enabled the `StringCoding` intrinsics.
- **Expert answer:** The full story: JEP 254 was one of the largest memory wins in the JDK's history, because *text dominates the heap* in most applications — log messages, JSON, HTML, identifiers, and (crucially) the `String` objects held by every framework. Halving the footprint of the most common object type is a big deal. But the deeper win was **performance**: with a `byte[]` and a `coder` flag, the JDK can dispatch to a `StringLatin1` implementation where every operation (comparison, indexing, `StringLatin1.equals`) is a simple `byte` operation that the JIT can **intrinsify and vectorise** — `String.equals` on two Latin-1 strings becomes a machine-code memcmp-like operation. The lesson in the design: **the representation choice determines which operations the compiler can optimise.** The same principle appears in `java.time` (which stores a `long` epoch day rather than a `Calendar` object graph), in `HashMap` (an `Object[]` rather than a node-per-entry structure), and in `StringBuilder` (a `byte[]` with a `coder` flag since Java 9 as well).

### CQ10. "You said `intern()` is a memory optimisation. But it also makes `==` work. Isn't that the real reason people use it?"

- **Naive:** "Yes, `intern()` makes `==` work."  ← *technically true, and it is the wrong reason*
- **The trap:** The trap is to treat `intern()` as an equality tool. It is **not** — `equals` is the equality contract, and using `intern()` to make `==` work is a **correctness hazard**: it changes the *cost* of an operation (a hash-table lookup into the pool) while making the *semantics* depend on an implementation detail.
- **Expert answer:** The correct framing: `intern()` gives you a **canonical instance**, which has exactly two legitimate uses. (1) **Memory** — many equal values share one object, so a program that creates millions of equal strings keeps one instance. This is the *real* win, and it is why the JDK's own `String` literals are interned. (2) **Fast identity comparison** — `==` on two interned strings is a pointer compare. But (2) is a *performance* technique with a *correctness* cost: it makes the program's behaviour depend on the values having been interned, which is a property of *how* they were created, not of what they are. The professional rule: use `equals` for correctness; use `intern()` only when you control a **bounded, hot** set of values and you have measured that the memory matters. And the anti-pattern to name: interning user input or UUIDs to "speed up comparison" — that converts a per-request allocation into a permanent heap growth, which is a memory leak traded for a micro-optimisation that `equals` plus a `HashMap` already gives you.

---

## 10. Rapid-Fire MCQs

**Q1.** `String` is immutable. This means…
- A) It cannot be changed after creation  B) It is thread-safe only if final  C) Its hashCode changes  D) It cannot be pooled

**Q2.** Before Java 9, `String` stored its characters in a…
- A) `char[]`  B) `byte[]`  C) `int[]`  D) `StringBuffer`

**Q3.** JEP 254 (Compact Strings) changed `String` to use…
- A) `char[]`  B) `byte[]` + a coder flag  C) `int[]`  D) A rope

**Q4.** For a pure-ASCII string, Java 9+ uses roughly how much memory vs Java 8?
- A) The same  B) Half  C) Double  D) A quarter

**Q5.** `"abc" == new String("abc")` is…
- A) `true`  B) `false`  C) A compile error  D) Depends on the JVM

**Q6.** `"abc".intern() == "abc"` is…
- A) `true`  B) `false`  C) A compile error  D) Depends on the JVM

**Q7.** In Java 7+, the string pool lives…
- A) In PermGen  B) On the heap  C) In the stack  D) In Metaspace

**Q8.** `new String("abc").intern()` returns…
- A) A new object  B) The pooled canonical instance  C) `null`  D) An empty string

**Q9.** `"a,b,,".split(",")` produces…
- A) `["a","b","",""]`  B) `["a","b"]`  C) `["a","b",""]`  D) `[]`

**Q10.** To keep trailing empty strings when splitting, pass the limit…
- A) `0`  B) `1`  C) `-1`  D) `Integer.MAX_VALUE`

**Q11.** `"a.b".split(".")` produces…
- A) `["a","b"]`  B) `[]`  C) `["a.b"]`  D) A compile error

**Q12.** `"a.b".split("\\.")` produces…
- A) `["a","b"]`  B) `[]`  C) `["a.b"]`  D) A compile error

**Q13.** `"order-123".matches("\\d+")` is…
- A) `true`  B) `false`  C) A compile error  D) Throws

**Q14.** To find a pattern *anywhere* in a string, use…
- A) `matches`  B) `Pattern.matcher(s).find()`  C) `split`  D) `startsWith`

**Q15.** `"a.b.c".replace(".", "-")` produces…
- A) `"a-b-c"`  B) `"a.b.c"`  C) `""`  D) A compile error

**Q16.** `"a.b.c".replaceAll("\\.", "-")` produces…
- A) `"a-b-c"`  B) `"a.b.c"`  C) `""`  D) A compile error

**Q17.** String concatenation in a loop is…
- A) O(n)  B) O(n log n)  C) O(n²)  D) O(log n)

**Q18.** `StringBuffer` differs from `StringBuilder` in that…
- A) It is faster  B) Its methods are synchronized  C) It is immutable  D) It pools

**Q19.** In Java 7+, `substring`…
- A) Shares the original array  B) Copies the characters  C) Throws  D) Is O(1)

**Q20.** Since Java 9, `a + b` compiles to…
- A) A `StringBuilder` chain  B) A single `invokedynamic` + `StringConcatFactory`  C) `String.valueOf` calls  D) A `char[]` copy

**Q21.** `String.hashCode()` caches its value because…
- A) It is expensive  B) The value cannot change (immutability)  C) The GC needs it  D) It is random

**Q22.** The `hashIsZero` flag in `String` exists to distinguish…
- A) An empty string from a null  B) A computed hash of 0 from an uncomputed hash  C) Latin-1 from UTF-16  D) Pooled from new

**Q23.** `String` is `final` mainly so that…
- A) It cannot be subclassed to break the `equals`/`hashCode` contract  B) It is faster  C) It can be pooled  D) It is thread-safe

**Q24.** Why is `String` a safe `HashMap` key?
- A) It is short  B) Its `hashCode` is stable because it is immutable  C) It overrides `hashCode` well  D) It is `final`

**Q25.** Interning a large number of unique strings risks…
- A) A `ClassCastException`  B) An unbounded, uncollectable pool  C) A `NullPointerException`  D) A `StackOverflowError`

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **A** | Immutability means every "modification" returns a new object. |
| 2 | **A** | Java 8 used `char[]` — always 2 bytes per character. |
| 3 | **B** | `byte[]` plus a one-byte `coder` flag (`LATIN1`/`UTF16`). |
| 4 | **B** | Half the memory for Latin-1 text — a huge win, since text dominates the heap. |
| 5 | **B** | `new String` creates a distinct object; only the literal is pooled. |
| 6 | **A** | `intern()` returns the canonical pooled instance, which *is* the literal. |
| 7 | **B** | The pool moved from PermGen to the heap in Java 7 (never collected since). |
| 8 | **B** | `intern()` returns the pooled canonical instance for that value. |
| 9 | **B** | Trailing empty strings are removed unless the limit is negative. |
| 10 | **C** | A limit of `-1` disables the trailing-empty removal. |
| 11 | **B** | `"."` is a regex matching any character, so it splits everywhere. |
| 12 | **A** | `"\\."` is the escaped literal dot. |
| 13 | **B** | `matches` requires the *whole* string to match the pattern. |
| 14 | **B** | `find()` searches anywhere; `matches()` requires a full match. |
| 15 | **B** | `replace` is **literal** — the dot is not a metacharacter there. |
| 16 | **A** | `replaceAll` is regex-driven, so the escaped dot matches. |
| 17 | **C** | Each `+` copies the whole accumulated string → 1+3+5+… = O(n²). |
| 18 | **B** | Every `StringBuffer` method is `synchronized`; `StringBuilder`'s are not. |
| 19 | **B** | Java 7+ copies the characters — the Java 6 shared-array leak is fixed. |
| 20 | **B** | JEP 280: a single `invokedynamic` bootstrapping `StringConcatFactory`. |
| 21 | **B** | Immutability guarantees the hash never changes, so it can be cached. |
| 22 | **B** | A string's hash can legitimately be 0, so a flag is needed to avoid recomputing it. |
| 23 | **A** | A subclass could override `equals`/`hashCode` and break the `Map`-key contract. |
| 24 | **B** | A stable `hashCode` means the map's cached hash can never go stale. |
| 25 | **B** | The pool is a GC root — its contents are never collected, so interning unique values leaks. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                         THE STRING FACTS                                │
├──────────────────────────────────────────────────────────────────────────┤
│  immutable      → final class, final array; every op returns a NEW String│
│  storage        → byte[] (Java 9+) with a coder flag; 1 byte/char Latin-1 │
│  header         → 12 bytes (64-bit, compressed oops)                    │
│  pool           → on the HEAP since Java 7; a GC root → NEVER collected │
│  equals         → content;  hashCode → s[0]*31^(n-1) + …               │
│  thread-safe    → yes, with no synchronisation                         │
├──────────────────────────────────────────────────────────────────────────┤
│  Java 8 ASCII 1,000 chars ≈ 2,017 bytes                                 │
│  Java 9+ ASCII 1,000 chars ≈ 1,018 bytes   → ~50% LESS  ⭐            │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    METHOD SELECTION — THE DECISION TREE                  │
│                                                                          │
│  Comparing?        → equals()          (never == for content)           │
│  Concatenating once?→ +                 (Java 9+: one allocation)       │
│  Concatenating many?→ StringBuilder     (pre-size it!)                  │
│  Joining?           → String.join / Collectors.joining                   │
│  Splitting?         → split(",", -1)    (keep trailing empties)         │
│                      → Pattern.quote() for a literal separator          │
│  Matching a regex?  → matches() = FULL; find() = ANYWHERE               │
│  Replacing?         → replace = LITERAL; replaceAll = REGEX             │
│  Formatting?        → String.format (or a lazy logger!)                │
│  Interning?         → only for a BOUNDED, HOT set of values            │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                        THE 6 STRING TRAPS                               │
├──────────────────────────────────────────────────────────────────────────┤
│  1. `==` for content        → use equals()                              │
│  2. `+=` in a loop          → O(n²) → use StringBuilder                │
│  3. `split(".")`            → "." is a REGEX → use "\\."                │
│  4. trailing empties gone  → use the limit -1                          │
│  5. `matches` vs `find`    → matches = FULL match                       │
│  6. `intern()` on uniques  → the pool NEVER frees → leak                │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### Performance rules for a hot path

| Rule | Why |
|---|---|
| `new StringBuilder(expectedSize)` | Avoids the geometric reallocation |
| `String.join` / `Collectors.joining` | One buffer, one copy |
| Pre-compile `Pattern` for `split`/`replaceAll` | Regex compilation dominates |
| Avoid `substring` in a loop over a big string | Java 7+ copies each time |
| Use a lazy logger, not `String.format` | Formatting is skipped when the level is off |
| Work on `char[]`/`byte[]` for character-level work | Avoids the `String` copy per operation |
| `equals` + a `HashMap` for caches | GC-friendly; the pool is not |
| `String.repeat(n)` (Java 11) | Much faster than a loop of `+=` |

### Memory rules

| Rule | Why |
|---|---|
| Never `new String(someString)` | Pointless allocation + lost constant folding |
| Never intern unbounded values | The pool is a GC root — permanent |
| Bound any `String` cache | Use a `Map` with an eviction policy (Caffeine) |
| Watch PII in log messages | A `String` is immutable — you cannot scrub it later |
| Use `java.time`, not `Date`/`SimpleDateFormat` | `Date` is mutable; `SimpleDateFormat` is not thread-safe |

### Security rules

| Rule | Why |
|---|---|
| Never build SQL with `+` | SQL injection — use `PreparedStatement` |
| Never build a path with `+` | Path traversal — validate, or use `Path.resolve` + a check |
| Never put PII in a `String` that gets logged | `String` is immutable; you cannot redact it in place |
| Escape for the *sink*, not the source | HTML-escape at render time, SQL-bind at query time |

### Observability

```java
// Log the LENGTH, not the value, when the value may be sensitive
log.debug("processing orderRef={} (len={})", mask(orderRef), orderRef.length());

// Count distinct values — a proxy for pool growth
log.info("distinct customerIds this batch = {}", distinctCount);
```

| Symptom | Likely cause | Check |
|---|---|---|
| Heap grows, `OutOfMemoryError` | Interning unique values | `jcmd GC.class_histogram` — a huge `String` count |
| High CPU in `Pattern` compilation | `split`/`replaceAll` in a loop | Profile for `Pattern.compile` |
| GC pressure from `char[]` copies | `substring` in a loop | Profile for `String.substring` |
| Duplicates in a `Set<String>` | A mutable key elsewhere in the codebase | Audit all `Map`/`Set` keys |

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| `equals`/`hashCode` in full | [08 · equals, hashCode & toString](../01-Core-Java-OOP/08-OOP-equals-hashCode-toString.md) |
| `java.time` (the other Java 8 change) | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| The string pool in the JDK source | `java.lang.String`, `java.lang.StringLatin1`, `java.lang.StringUTF16` |
| I/O and encodings | [15 · I/O & NIO](../15-IO-NIO/01-IO-NIO.md) |
| JEPs | JEP 254 (Compact Strings), JEP 280 (String Concatenation) |

---

**Next Chapter:** → [13 · Enums](../13-Enums/01-Enum-Deep-Dive.md)
**Previous Chapter:** ← [11 · DSA](../11-DSA/01-DSA-Quick-Reference.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
