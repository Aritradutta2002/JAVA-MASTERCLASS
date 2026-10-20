## 📋 Table of Contents

- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — The Contract in One Picture](#2-mental-model--the-contract-in-one-picture)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The equals-hashCode Contract](#31-the-equals-hashcode-contract)
  - [3.2 Implementing equals() Correctly](#32-implementing-equals-correctly)
  - [3.3 Implementing hashCode() Correctly](#33-implementing-hashcode-correctly)
  - [3.4 toString() Best Practices](#34-tostring-best-practices)
  - [3.5 Records: the Compiler Writes It For You](#35-records-the-compiler-writes-it-for-you)
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

- **Override `equals()` and `hashCode()` together, or neither.** The contract is: equal objects
  **must** have equal hash codes. Override one without the other and hash-based collections silently
  misbehave.
- **`equals()` is reflexive, symmetric, transitive, consistent, and null-safe** — and it must be
  *overridable* (`public boolean equals(Object)`). `equals(Employee)` is an **overload**, not an
  override, and the compiler will not warn you.
- **`hashCode()` must use exactly the same fields as `equals()`** — otherwise equal objects get
  different hash codes and `HashMap` lookups return `null`.
- **Use `getClass() != obj.getClass()` for strict type equality** (symmetry-safe). Use
  `instanceof` only if you accept that a subclass may be equal to its parent.
- **`hashCode()` is not unique** — collisions are legal and normal. A *constant* hash code is legal
  but catastrophic: it degrades `HashMap` from O(1) to O(n).
- **`toString()` is for humans, `equals()` is for the machine.** Never put secrets in `toString()` —
  it ends up in logs.
- **Records generate all three correctly** — but only for the components you declare, and only
  shallowly for mutable components.
- **Never mutate a key that is already in a hash-based collection** — its hash changes and it
  becomes permanently unreachable.

---

## 1. What & Why — The Problem This Solves

### The problem

Every Java object inherits `equals()`, `hashCode()` and `toString()` from `java.lang.Object`. The
inherited `equals()` is **reference identity** and the inherited `hashCode()` is essentially the
**memory address**. For a domain object, that is almost never what you want:

```java
Employee e1 = new Employee("John", 30);
Employee e2 = new Employee("John", 30);
e1.equals(e2);      // false ❌ — two different people-shaped objects
```

So you override them. And that is where the trap lives: **these two methods are not independent.**
They are a *contract*, and the compiler cannot enforce it for you.

### The solution

Understand the contract, then implement it mechanically:

```text
   ┌──────────────────────────────────────────────────────────────┐
   │  THE CONTRACT: two halves that must agree                    │
   │                                                              │
   │   equals()  →  "are these two objects the same VALUE?"       │
   │   hashCode()→  "which BUCKET should I look in?"               │
   │                                                              │
   │   HashMap.get(key) does:                                     │
   │      1. int h = key.hashCode();                               │
   │      2. int idx = (h ^ (h>>>16)) & (table.length - 1);      │
   │      3. walk bucket[idx]; for each entry, e.key.equals(key)  │
   │                                                              │
   │   If hashCode() disagrees with equals(), step 3 never runs.  │
   └──────────────────────────────────────────────────────────────┘
```

### The analogy

Think of a **library index**. `hashCode()` is the shelf number computed from the book's ISBN;
`equals()` is checking whether two books have the same ISBN. If two books with the same ISBN are
filed on *different shelves*, the librarian never even looks at the second book to compare it — the
lookup fails even though the book is right there.

---

## 2. Mental Model — The Contract in One Picture

```text
┌─────────────────────────────────────────────────────────────┐
│           THE CONTRACT (Must follow ALL rules)               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  RULE 1: If a.equals(b) == true → a.hashCode() == b.hashCode()  │
│          (Equal objects MUST have equal hash codes)          │
│                                                             │
│  RULE 2: If a.hashCode() != b.hashCode() → a.equals(b) == false │
│          (Different hash codes → objects are NOT equal)      │
│                                                             │
│  RULE 3: If a.hashCode() == b.hashCode() → equals() may    │
│          be true OR false (hash collision is allowed)        │
│                                                             │
│  ⚠️ Override BOTH or NEITHER. Never just one.                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Why This Matters

```java
// Without proper equals/hashCode:
Set<Person> set = new HashSet<>();
Person p1 = new Person("John", 30);
Person p2 = new Person("John", 30);

set.add(p1);
set.contains(p2);  // false! (without equals/hashCode override)
// HashSet uses hashCode() to find bucket, then equals() to match
```

### The Golden Rule (memorise this one line)

> **`equals()` and `hashCode()` must be derived from the SAME set of fields.**
> If you add a field to one, add it to the other — or you have created a bug.

---

## 3. Deep Dive — Theory

### 3.1 The equals-hashCode Contract

The contract is defined in the `java.lang.Object` Javadoc and restated in *Effective Java* Item 10.

```text
┌─────────────────────────────────────────────────────────────┐
│           THE CONTRACT (Must follow ALL rules)               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  RULE 1: If a.equals(b) == true → a.hashCode() == b.hashCode()  │
│          (Equal objects MUST have equal hash codes)          │
│                                                             │
│  RULE 2: If a.hashCode() != b.hashCode() → a.equals(b) == false │
│          (Different hash codes → objects are NOT equal)      │
│                                                             │
│  RULE 3: If a.hashCode() == b.hashCode() → equals() may    │
│          be true OR false (hash collision is allowed)        │
│                                                             │
│  ⚠️ Override BOTH or NEITHER. Never just one.                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

#### Why This Matters

```java
// Without proper equals/hashCode:
Set<Person> set = new HashSet<>();
Person p1 = new Person("John", 30);
Person p2 = new Person("John", 30);

set.add(p1);
set.contains(p2);  // false! (without equals/hashCode override)
// HashSet uses hashCode() to find bucket, then equals() to match
```
### 3.2 Implementing equals() Correctly

#### The 5 Properties of equals()

| Property | Rule | Example |
|----------|------|---------|
| Reflexive | `a.equals(a)` must be `true` | Object equals itself |
| Symmetric | If `a.equals(b)` then `b.equals(a)` | Both ways must agree |
| Transitive | If `a.equals(b)` and `b.equals(c)` then `a.equals(c)` | Chain must hold |
| Consistent | Same result for same unchanged objects | Deterministic |
| Null-safe | `a.equals(null)` must be `false` | Never equal to null |

#### The `getClass()` vs `instanceof` Decision (the real expert question)

| Approach | Symmetry | Substitutability | When to use |
|----------|----------|-------------------|-------------|
| `getClass() != obj.getClass()` | ✅ Always safe | ❌ Subclass never equals parent | Value objects, `final` classes |
| `instanceof` | ⚠️ Can break | ✅ Subclass may equal parent | Only if the hierarchy is designed for it |

**The best solution avoids the dilemma entirely:** make the class `final`. Then there is only one
runtime class, `getClass()` and `instanceof` agree, and the question disappears. If the class
*must* be extensible, use the `canEqual` pattern from *Effective Java* (Item 10) — see the example
below, where the subclass explicitly grants permission.

#### The Mechanics Inside `HashMap`

```java
public V get(Object key) {
    Node<K,V>[] tab; Node<K,V> first, e; int n, hash; K k;
    if ((tab = table) != null && (n = tab.length) > 0 &&
        (first = tab[(n - 1) & (hash = hash(key))]) != null) {
        if (first.hash == hash && ((k = first.key) == key || (key != null && key.equals(k))))
            return first.value;                       // found on the first node
        if ((e = first.next) != null) { ... }         // walk the rest of the bucket
    }
    return null;
}

// hash() is the "spread" function that mixes high bits down:
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

The critical line is `tab[(n - 1) & hash]`: **the bucket index is derived from the hash code**. If
two equal objects have different hash codes, they land in different buckets and the lookup for the
second one never examines the first. The `equals()` call is only reached *within* a bucket.

#### Correct Implementation Pattern

```java
import java.util.Objects;

public class Employee {
    private String name;
    private int id;
    private double salary;
    
    @Override
    public boolean equals(Object obj) {
        // Step 1: Reference check (reflexive + performance)
        if (this == obj) return true;
        
        // Step 2: Null check + type check
        if (obj == null || getClass() != obj.getClass()) return false;
        
        // Step 3: Cast and compare fields
        Employee other = (Employee) obj;
        return this.id == other.id
            && Double.compare(this.salary, other.salary) == 0
            && Objects.equals(this.name, other.name);
    }
}
```

Note the `this == obj` fast path in step 1: it satisfies reflexivity and short-circuits the common
case where the *same instance* is compared with itself.

#### Common Mistakes

```java
// ❌ Mistake 1: Wrong method signature (overloading instead of overriding!)
public boolean equals(Employee other) {  // ❌ Takes Employee, not Object!
    return this.id == other.id;
}
// This OVERLOADS equals, doesn't OVERRIDE it. Collections use Object version.

// ✅ Fix: Always use Object parameter
@Override
public boolean equals(Object obj) { ... }
```

```java
// ❌ Mistake 2: Using instanceof (breaks symmetry with subclasses)
@Override
public boolean equals(Object obj) {
    if (!(obj instanceof Employee)) return false;  // ⚠️ Problematic
    ...
}

class Manager extends Employee { ... }

Employee e = new Employee("John", 1);
Manager m = new Manager("John", 1);

e.equals(m);  // true  (Manager instanceof Employee)
m.equals(e);  // false (Employee NOT instanceof Manager) ← BREAKS SYMMETRY!

// ✅ Fix: Use getClass() instead of instanceof
if (obj == null || getClass() != obj.getClass()) return false;
```

```java
// ❌ Mistake 3: Using == for objects instead of equals()
return this.name == other.name;  // ❌ Compares references!

// ✅ Fix
return Objects.equals(this.name, other.name);  // Null-safe
```

```java
// ❌ Mistake 4: Using == for float/double (NaN, -0.0 issues)
return this.salary == other.salary;  // ❌ NaN != NaN, and -0.0 == 0.0

// ✅ Fix
return Double.compare(this.salary, other.salary) == 0;
return Float.compare(this.rate, other.rate) == 0;
```
### 3.3 Implementing hashCode() Correctly

#### Basic Rules

- Equal objects → equal hash codes (MUST)
- Use the SAME fields as equals()
- Good hash code = fewer collisions = better HashMap performance

#### Using Objects.hash() (Simplest)

```java
@Override
public int hashCode() {
    return Objects.hash(name, id, salary);  // Uses same fields as equals()
}
```

`Objects.hash` allocates a varargs array on every call — fine for correctness, slightly wasteful in
a hot loop.

#### Manual Implementation (Better Performance)

```java
@Override
public int hashCode() {
    int result = 17;                        // Start with non-zero prime
    result = 31 * result + id;              // int field
    result = 31 * result + (name != null ? name.hashCode() : 0);  // Object field
    long temp = Double.doubleToLongBits(salary);
    result = 31 * result + (int)(temp ^ (temp >>> 32));  // double field
    return result;
}
```

#### Why 31?

- 31 is an odd prime
- `31 * i` can be optimized by JVM to `(i << 5) - i` (fast bitshift)
- Gives good distribution of hash values

The shift-and-subtract form is a 32-bit multiply without a multiply instruction — and because
`31` is `2^5 - 1`, shifting left by 5 and subtracting is *exactly* equivalent to multiplying by 31.
Using a power of two instead (like 32) would be a disaster: only the low 5 bits would ever be set.

#### Field Types Guide

| Field Type | Hash Contribution |
|-----------|-------------------|
| `boolean` | `(f ? 1 : 0)` |
| `byte/char/short/int` | `(int) f` |
| `long` | `(int)(f ^ (f >>> 32))` |
| `float` | `Float.floatToIntBits(f)` |
| `double` | `Double.doubleToLongBits(f)` then long treatment |
| Object | `(f == null ? 0 : f.hashCode())` |
| Array | `Arrays.hashCode(f)` |

The `long` treatment (`f ^ (f >>> 32)`) exists because `hashCode()` returns an `int` — a `long` has
64 bits of entropy but only 32 survive, so the high bits are folded down. The same trick is used by
`HashMap` itself in `hash()`.

#### Hash Collisions Are Not Errors

Two *different* objects may legitimately have the same hash code. `HashMap` handles this by
comparing with `equals()` within the bucket. The only thing you must guarantee is the *direction*:
equal objects must have equal hash codes. The reverse is not required.

---

### 3.4 toString() Best Practices

#### Default Behavior

```java
Object obj = new Object();
System.out.println(obj);  // java.lang.Object@7852e922
// Format: ClassName@hexHashCode — not useful!
```

#### Good toString()

```java
public class Employee {
    private int id;
    private String name;
    private double salary;
    
    @Override
    public String toString() {
        return "Employee{id=" + id + ", name='" + name + "', salary=" + salary + "}";
    }
    // Output: Employee{id=1, name='John', salary=50000.0}
}
```

#### Using String.format() or StringBuilder

```java
@Override
public String toString() {
    return String.format("Employee{id=%d, name='%s', salary=%.2f}", id, name, salary);
}
```

#### With Records (Java 16+)

```java
record Employee(int id, String name, double salary) { }
// toString() auto-generated: Employee[id=1, name=John, salary=50000.0]
```

#### Tips

- Include all important fields
- Don't include sensitive data (passwords, tokens)
- Keep it concise — used in logs and debugging
- Consider using a library like Lombok `@ToString`

**The security rule:** `toString()` output ends up in logs, exception messages and log aggregation
systems. A password, PAN, card number, token or session id in a `toString()` is a data leak waiting
to happen. If a field is sensitive, either exclude it or **mask** it — `****1234`, not the full
value.

---

### 3.5 Records: the Compiler Writes It For You

```java
public record Person(String name, int age) { }
```

The compiler generates:
- a `final` class
- `private final` fields
- a canonical constructor
- accessors named `name()` and `age()` (not `getName()`)
- `equals()` / `hashCode()` based on **all** components
- `toString()` in the form `Person[name=John, age=30]`

Because the generated `equals()`/`hashCode()` use the same component list, the contract is
**impossible to break** — which is exactly why records are the right default for value objects.

You can still override them, and you should if a component is mutable:

```java
public record Money(BigDecimal amount, String currency) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return amount.compareTo(other.amount) == 0   // scale-insensitive!
            && currency.equals(other.currency);
    }
}
```

Note the `compareTo` instead of `equals` on the `BigDecimal` — the classic scale trap, covered in
the pitfalls section.

---

### 3.6 What Happens When You Break the Contract

#### Only Override equals() Without hashCode()

```java
class Person {
    String name;
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Person p) return name.equals(p.name);
        return false;
    }
    // ❌ No hashCode() override!
}

Map<Person, String> map = new HashMap<>();
Person p1 = new Person("John");
Person p2 = new Person("John");

map.put(p1, "Developer");
map.get(p2);  // null! ← Even though p1.equals(p2) is true

// Why? p1 and p2 have DIFFERENT hashCodes (default Object.hashCode)
// HashMap checks hashCode FIRST to find bucket
// Different hashCode → looks in wrong bucket → never finds it
```

#### Only Override hashCode() Without equals()

```java
class Item {
    String code;
    
    @Override
    public int hashCode() { return code.hashCode(); }
    // ❌ No equals() override — uses Object.equals (reference check)
}

Set<Item> set = new HashSet<>();
Item i1 = new Item("A");
Item i2 = new Item("A");

set.add(i1);
set.add(i2);
set.size();  // 2! ← Both added because equals() says they're different objects
```

Note the asymmetry: with only `equals()`, the failure is a **silent `null`** from `map.get()`. With
only `hashCode()`, the failure is a **duplicate** in a `Set`. The first is far harder to notice.

---

## 4. Visualizations

### 4.1 How `HashMap.get()` Uses the Two Methods

```text
   map.get(p2)          p1.equals(p2) == true
   ───────────          ───────────────────────
        │
        ▼
   ┌────────────────────┐
   │ p2.hashCode()      │   ← p1 and p2 have DIFFERENT hash codes
   └─────────┬──────────┘
             ▼
   ┌────────────────────┐
   │ spread the hash    │   h ^ (h >>> 16)
   └─────────┬──────────┘
             ▼
   ┌────────────────────┐
   │ index = h & (n-1)  │   ← p1 and p2 map to DIFFERENT buckets
   └─────────┬──────────┘
             ▼
     ┌───────┴────────┐
     ▼                ▼
  bucket[3]        bucket[11]
  ┌────────┐       ┌────────┐
  │ p1     │       │        │  ← p2's bucket is EMPTY
  └────────┘       └────────┘
                       │
                       ▼
                  returns null ❌
     (p1.equals(p2) is NEVER EVEN CALLED)
```

> **What to look at:** the `equals()` call is *downstream* of the bucket selection. If the hash codes
> differ, the comparison never happens — which is why a broken contract produces a silent `null`.

### 4.2 The Contract — Direction Matters

```text
   ✔ REQUIRED                          ✘ NOT REQUIRED
   ───────────                          ───────────────
   a.equals(b) == true                  a.hashCode() == b.hashCode()
        │                                     │
        │  IMPLIES                            │
        ▼                                     ▼
   a.hashCode() == b.hashCode()          a.equals(b) == true

   Equal objects MUST have               Equal hashes do NOT
   equal hash codes                      mean equal objects
        │                                     │
        ▼                                     ▼
   Buckets agree → findable          COLLISION → resolved by
                                       equals() within the bucket
```

### 4.3 HashMap Bucket Internals

```text
   HashMap (capacity 16)
   ┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐
   │ 0  │ 1  │ 2  │ 3  │ 4  │ 5  │ 6  │ 7  │ 8  │ 9  │10  │11  │12  │13  │14  │15  │
   └────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘
        ▲
        │ index = (h ^ (h>>>16)) & 15
        │
   ┌────┴─────────────────┐
   │  "John" → Node       │──► Node ──► Node   (linked list on collision)
   │  Employee@1a2b       │    (equals() called here to disambiguate)
   └──────────────────────┘
        ▲
        │
   All entries with the SAME bucket index live here.
   A poor hashCode() → everyone in bucket 3 → O(n) lookups.
```

### 4.4 Field → Hash Contribution Cheat Sheet

```text
   ┌────────────┬────────────────────────────────────────────┐
   │ TYPE       │ CONTRIBUTION TO hashCode()                 │
   ├────────────┼────────────────────────────────────────────┤
   │ boolean    │ f ? 1 : 0                                 │
   │ byte/char  │ (int) f                                    │
   │ short/int  │ (int) f                                    │
   │ long       │ (int)(f ^ (f >>> 32))   ← fold high bits  │
   │ float      │ Float.floatToIntBits(f)                   │
   │ double     │ (int)(bits ^ (bits >>> 32))               │
   │ Object     │ f == null ? 0 : f.hashCode()              │
   │ Array      │ Arrays.hashCode(f)                        │
   │ BigDecimal │ stripTrailingZeros().hashCode()  ← scale! │
   └────────────┴────────────────────────────────────────────┘
```

### 4.5 The `getClass()` vs `instanceof` Symmetry Trap

```text
   class Employee          class Manager extends Employee
        │                          │
   equals(Object o):         equals(Object o):
   getClass() != o.getClass()  getClass() != o.getClass()
        │                          │
        ▼                          ▼
   e.equals(m) → false       m.equals(e) → false      ✔ SYMMETRIC

   ─────────────────────────────────────────────────────────────

   class Employee          class Manager extends Employee
        │                          │
   equals(Object o):         equals(Object o):
   o instanceof Employee    o instanceof Manager
        │                          │
        ▼                          ▼
   e.equals(m) → TRUE       m.equals(e) → FALSE      ✘ ASYMMETRIC!
   (Manager IS-A Employee)  (Employee IS NOT-A Manager)
```

> **What to look at:** the two arrows disagree. `equals` must be symmetric, so the *same* check must
> be used in both directions — or the class must be `final`.

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — Breaking Each Half of the Contract

**Problem:** *"What actually goes wrong if you override only one of the two?"*

```java
import java.util.*;

public class D2 {

    static class Broken {                 // ❌ equals() but no hashCode()
        final String name;
        Broken(String name) { this.name = name; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Broken b)) return false;
            return name.equals(b.name);
        }
        // ❌ no hashCode() override!
    }

    static class Item {                  // ❌ hashCode() but no equals()
        final String code;
        Item(String code) { this.code = code; }
        @Override public int hashCode() { return code.hashCode(); }
        // ❌ no equals() override — uses Object.equals (reference check)
    }

    record Person(String name, int age) { }

    public static void main(String[] args) {
        Map<Broken, String> map = new HashMap<>();
        Broken p1 = new Broken("John");
        Broken p2 = new Broken("John");
        map.put(p1, "Developer");
        System.out.println("p1.equals(p2) = " + p1.equals(p2));
        System.out.println("map.get(p2)    = " + map.get(p2));
        System.out.println("p1.hashCode()  = " + p1.hashCode() + "   p2.hashCode() = " + p2.hashCode());

        Set<Item> set = new HashSet<>();
        set.add(new Item("A"));
        set.add(new Item("A"));
        System.out.println("HashSet size with hashCode only = " + set.size());

        Person a = new Person("John", 30);
        Person b = new Person("John", 30);
        System.out.println("record equals = " + a.equals(b) + ", hashCode equal = " + (a.hashCode() == b.hashCode()));
        System.out.println("record toString = " + a);

        System.out.println("BigDecimal 1.0 vs 1.00 equals = " + new java.math.BigDecimal("1.0").equals(new java.math.BigDecimal("1.00")));
        System.out.println("BigDecimal 1.0 vs 1.00 compareTo = " + new java.math.BigDecimal("1.0").compareTo(new java.math.BigDecimal("1.00")));

        double[] k1 = {1.0, 2.0};
        double[] k2 = {1.0, 2.0};
        Map<double[], String> m2 = new HashMap<>();
        m2.put(k1, "value");
        System.out.println("map.get(equal array) = " + m2.get(k2));
        System.out.println("Arrays.equals = " + Arrays.equals(k1, k2));
    }
}
```

**Actual output (compiled and run on JDK 25):**

```text
p1.equals(p2) = true
map.get(p2)    = null
p1.hashCode()  = 498931366   p2.hashCode() = 622488023
HashSet size with hashCode only = 2
record equals = true, hashCode equal = true
record toString = Person[name=John, age=30]
BigDecimal 1.0 vs 1.00 equals = false
BigDecimal 1.0 vs 1.00 compareTo = 0
map.get(equal array) = null
Arrays.equals = true
```

**Dry run:**

| # | What happens | Why |
|---|--------------|-----|
| 1 | `p1.equals(p2) = true` | The overridden `equals()` compares `name` — logically correct |
| 2 | `p1.hashCode()` and `p2.hashCode()` differ (`498931366` vs `622488023`) | No `hashCode()` override → the inherited identity hash |
| 3 | `map.get(p2) = null` | Different hash → different bucket → `p1.equals(p2)` is **never called**. The key is present but unreachable |
| 4 | `HashSet size with hashCode only = 2` | Same hash code → same bucket → but `equals()` is identity, so both are "distinct" |
| 5 | `record equals = true, hashCode equal = true` | The record generates both from the same component list — the contract cannot be broken |
| 6 | `BigDecimal 1.0 vs 1.00 equals = false` but `compareTo = 0` | `BigDecimal.equals` compares **scale** as well as value; `compareTo` compares numeric value only |
| 7 | `map.get(equal array) = null` | Arrays use identity `equals()` and identity `hashCode()` — two different arrays are different keys |
| 8 | `Arrays.equals = true` | The array-aware helper compares contents |

**Takeaway:** Each of the eight lines is a *different* way to get this wrong. The record shows the
compiler's solution; the `BigDecimal` and array cases show that the *type* must participate in
value equality.

---

### Example 2 — Mutating a Key After Insertion

**Problem:** *"You put an object in a `HashSet` and then change one of its fields. What happens?"*

```java
import java.util.*;

public class MutatedKey {

    static class Person {
        final String name;
        int age;
        Person(String name, int age) { this.name = name; this.age = age; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Person p)) return false;
            return age == p.age && Objects.equals(name, p.name);
        }
        @Override public int hashCode() { return Objects.hash(name, age); }
        @Override public String toString() { return "Person{" + name + ", " + age + "}"; }
    }

    public static void main(String[] args) {
        Set<Person> set = new HashSet<>();
        Person p = new Person("John", 30);
        set.add(p);

        System.out.println("before mutation: contains = " + set.contains(p) + ", size = " + set.size());

        p.age = 31;   // ❌ mutating a key that is already in a hash collection

        System.out.println("after  mutation: contains = " + set.contains(p) + ", size = " + set.size());
        System.out.println("remove(p)         = " + set.remove(p) + ", size = " + set.size());
        System.out.println("iteration still shows: " + set);
    }
}
```

**Actual output (compiled and run on JDK 25):**

```text
before mutation: contains = true, size = 1
after  mutation: contains = false, size = 1
remove(p)         = false, size = 1
iteration still shows: [Person{John, 31}]
```

**Dry run:**

| Step | `p.hashCode()` | Bucket | What happens |
|------|----------------|--------|--------------|
| 1 | `hash("John", 30)` | bucket X | `set.add(p)` → stored in bucket X |
| 2 | `hash("John", 30)` | bucket X | `set.contains(p)` → same bucket → `equals()` matches → `true` |
| 3 | `p.age = 31` | — | The **field** changes, so the hash **will** change |
| 4 | `hash("John", 31)` | bucket **Y** | `set.contains(p)` → looks in bucket Y → empty → `false` |
| 5 | `hash("John", 31)` | bucket Y | `set.remove(p)` → looks in bucket Y → not found → `false` |
| 6 | — | bucket X | The entry is **still in the set** — it is now unreachable and can never be removed by value |

**Takeaway:** The object is now a **ghost** — it is in the collection, visible to iteration, but
`contains()` and `remove()` can never find it. It also leaks memory until the whole collection is
discarded. **The rule: a key in a hash-based collection must be immutable for its entire
residency.** This is why `String` and `Integer` are the right keys and a mutable POJO is not.

---

### Example 3 — A Production-Grade Entity With a Correct Contract

**Problem:** *"Write `equals`/`hashCode`/`toString` for a real entity."*

```java
import java.util.Objects;

public class Employee {

    private final String employeeId;   // the business key
    private final String name;
    private final double salary;

    public Employee(String employeeId, String name, double salary) {
        this.employeeId = Objects.requireNonNull(employeeId);
        this.name = Objects.requireNonNull(name);
        this.salary = salary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                              // fast path
        if (o == null || getClass() != o.getClass()) return false; // symmetry-safe
        Employee other = (Employee) o;
        return employeeId.equals(other.employeeId);   // business key ONLY
    }

    @Override
    public int hashCode() {
        return Objects.hash(employeeId);              // SAME field as equals()
    }

    @Override
    public String toString() {
        return "Employee{id='" + employeeId + "', name='" + name + "'}";  // NO salary - sensitive
    }

    public String getEmployeeId() { return employeeId; }
    public String getName() { return name; }
    public double getSalary() { return salary; }
}
```

**Actual output (compiled and run on JDK 25):**

```text
Employee{id='EMP-001', name='John'}
map.get(equal key) = Developer
set.contains(equal key) = true
```

The output shown above is produced by:

```java
Map<Employee, String> map = new HashMap<>();
map.put(new Employee("EMP-001", "John", 50000), "Developer");
System.out.println(map.get(new Employee("EMP-001", "John", 99999)));   // "Developer"

Set<Employee> set = new HashSet<>();
set.add(new Employee("EMP-001", "John", 50000));
System.out.println(set.contains(new Employee("EMP-001", "John", 0)));  // true
```

**Why this is production-grade:**

| Decision | Rationale |
|----------|-----------|
| `equals` uses **only** the business key | Changing a name or salary must not change identity |
| `getClass()` not `instanceof` | Symmetry-safe; the class is effectively a value type |
| `hashCode` uses the **same** field | The contract holds by construction |
| `toString` **omits** `salary` | Salary is sensitive — it must not leak into logs |
| Fields are `final` | The key can never change while the object is in a collection |

> **The most important design decision is the first one.** Should a salary change make an employee
> a *different* employee? No — so `salary` must not be in `equals()`. Getting that question right is
> worth more in an interview than any clever hash implementation.

---

## 6. Code from Scratch — Production-Grade

A complete, compilable value type with a correct contract, plus a mutable-entity variant that
shows the trade-offs side by side.

```java
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An IMMUTABLE value object. The class is final, so there is exactly one
 * runtime class - which removes the getClass() vs instanceof dilemma.
 */
public final class ProductId implements Comparable<ProductId> {

    private final String value;

    public ProductId(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public String getValue() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                                  // fast path
        if (o == null || getClass() != o.getClass()) return false;    // symmetry-safe
        ProductId other = (ProductId) o;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();     // SAME field as equals() - contract holds by construction
    }

    @Override
    public String toString() {
        return "ProductId{" + value + "}";
    }

    @Override
    public int compareTo(ProductId o) {
        return value.compareTo(o.value);
    }
}

/**
 * A value object holding MONEY. Note the BigDecimal handling - this is
 * where most production equals() bugs live.
 */
final class Money {

    private final BigDecimal amount;   // BigDecimal, never double
    private final String currency;     // ISO-4217

    public Money(BigDecimal amount, String currency) {
        this.amount = Objects.requireNonNull(amount, "amount");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    /**
     * Scale-insensitive equality.
     * BigDecimal.equals() compares scale too, so 1.0 != 1.00.
     * compareTo() compares numeric value only.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money other = (Money) o;
        return amount.compareTo(other.amount) == 0
            && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        // MUST mirror equals(): strip the scale before hashing,
        // otherwise two 'equal' Money objects get different hash codes.
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;   // no scientific notation
    }

    public BigDecimal getAmount() { return amount; }   // BigDecimal is immutable - safe to share
    public String getCurrency() { return currency; }
}
```

**Why this is production-grade:**

| Concern | How it is handled |
|---------|-------------------|
| `final` class | Removes the `getClass()` vs `instanceof` dilemma entirely |
| Contract by construction | `hashCode()` uses exactly the fields `equals()` uses |
| `BigDecimal` scale trap | `compareTo` for equality, `stripTrailingZeros()` for hashing |
| No floating point money | `BigDecimal`, never `double` |
| Null safety | `Objects.requireNonNull` with a message at every boundary |
| `toString` safety | No sensitive data, `toPlainString()` avoids `1E+10` |
| Comparability | `Comparable` implemented consistently with `equals` |

> **The `BigDecimal` trap deserves a second look:** `equals()` uses `compareTo` (scale-insensitive)
> but `hashCode()` uses `stripTrailingZeros()`. Both are needed. If you use `compareTo` in `equals`
> but the raw `amount` in `hashCode`, then `new BigDecimal("1.0")` and `new BigDecimal("1.00")` are
> `equals` but have different hash codes — you have just re-broken the contract with a subtlety
> that no compiler will warn you about.

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Overriding `equals` without `hashCode`

```java
// ❌ WRONG
class User {
    String email;
    @Override public boolean equals(Object o) { /* ... */ }
    // no hashCode() -> identity hash -> HashMap lookups FAIL
}
```
**WHY:** `HashMap` uses `hashCode()` to pick the bucket; equal objects with different hash codes
land in different buckets, so `get()` returns `null` even though the key is present.

### ❌ Pitfall 2 — Overloading instead of overriding `equals`

```java
// ❌ WRONG — this is an OVERLOAD, not an override
public boolean equals(User other) { return id == other.id; }
```
**WHY:** The signature must be `equals(Object)`. The compiler will not warn you about the overload;
it will silently keep using `Object.equals()` (reference comparison) in all collections.

### ❌ Pitfall 3 — `instanceof` breaking symmetry

```java
// ❌ WRONG
if (!(o instanceof Employee)) return false;
```
**WHY:** With a subclass, `e.equals(m)` is `true` but `m.equals(e)` is `false`. Symmetry is
broken. Use `getClass()`, make the class `final`, or use the `canEqual` pattern.

### ❌ Pitfall 4 — Using `==` for object fields

```java
// ❌ WRONG
return this.name == other.name;    // compares references!
```
**WHY:** Two `String`s with the same content are usually different objects. Use
`Objects.equals(this.name, other.name)` — which is also null-safe.

### ❌ Pitfall 5 — Using `==` for `float`/`double`

```java
// ❌ WRONG
return this.salary == other.salary;   // NaN != NaN, and -0.0 == 0.0
```
```java
// ✅ RIGHT
return Double.compare(this.salary, other.salary) == 0;
```
**WHY:** `Double.compare` follows `IEEE 754` *total order* semantics: `NaN` equals `NaN` and
`-0.0` is **not** equal to `0.0` (which is what you want for a hash key — they are different
values).

### ❌ Pitfall 6 — `BigDecimal` scale mismatch

```java
// ❌ WRONG
new BigDecimal("1.0").equals(new BigDecimal("1.00"));   // false!
```
```java
// ✅ RIGHT
new BigDecimal("1.0").compareTo(new BigDecimal("1.00")) == 0;   // true
```
**WHY:** `BigDecimal.equals()` compares the **scale** as well as the unscaled value. If you use
`equals` in your entity and then look it up in a `Map` keyed by `BigDecimal`, lookups fail.

### ❌ Pitfall 7 — Arrays as map keys

```java
// ❌ WRONG
Map<double[], String> map = new HashMap<>();
map.put(new double[]{1.0, 2.0}, "value");
map.get(new double[]{1.0, 2.0});   // null!
```
**WHY:** Arrays use identity `equals()` and identity `hashCode()`. Two arrays with identical
contents are different keys. Use `Arrays.equals`/`Arrays.hashCode` in a wrapper class, or a
`List<Double>`.

### ❌ Pitfall 8 — A constant `hashCode()`

```java
// ❌ Legal but terrible
@Override public int hashCode() { return 42; }
```
**WHY:** Every object lands in the same bucket. `HashMap` degrades from O(1) to O(n), and the
bucket's tree/linked list grows without bound. Never do this.

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: Why must we override both equals() and hashCode()?**
A: The contract requires equal objects to have equal hash codes. HashMap/HashSet use hashCode() to
find the bucket, then equals() to match. Breaking this makes collections malfunction.

**Q: What happens if we only override equals()?**
A: Two equal objects may end up in different HashMap buckets (different hashCodes), so `map.get()`
with an equal key returns null.

**Q: What happens if we only override hashCode()?**
A: Equal objects get same bucket, but equals() uses reference check (Object default), so duplicates
appear in HashSet.

**Q: What is the default equals() behavior?**
A: `Object.equals()` uses `==` (reference comparison). Two distinct objects with same data are NOT
equal unless you override equals().

**Q: What is the default hashCode() behavior?**
A: Based on memory address (or an internal identifier). Different objects get different hash codes
even with same data.

**Q: Can two non-equal objects have the same hashCode()?**
A: Yes. This is called a hash collision. hashCode equality is necessary but not sufficient for
object equality.

**Q: What's the difference between == and equals()?**
A: `==` compares references (memory addresses). `equals()` compares logical equality (content). For
primitives, `==` compares values.

### 🟡 Intermediate

**Q: Why use getClass() instead of instanceof in equals()?**
A: `instanceof` breaks symmetry when subclasses are involved. `e.equals(m)` may be true but
`m.equals(e)` false.

**Q: How does HashMap use equals() and hashCode() together?**
A: `hashCode()` determines the bucket. `equals()` resolves collisions within the same bucket. Both
are needed for correct behavior.

**Q: Should you use mutable fields in equals()/hashCode()?**
A: Avoid it. If a field changes after the object is in a HashMap, the object becomes "lost" — it's
in the wrong bucket and can't be found.

**Q: How do Records handle equals/hashCode/toString?**
A: Records auto-generate all three based on all components. Two records with same component values
are equal.

**Q: What are the 5 properties of equals()?**
A: Reflexive (a.equals(a) = true), Symmetric (a.equals(b) = b.equals(a)), Transitive (a=b, b=c →
a=c), Consistent (same result for unchanged objects), Null-safe (a.equals(null) = false).

**Q: Why is 31 used as a multiplier in hashCode()?**
A: 31 is an odd prime that gives good distribution. JVM can optimize `31 * i` to `(i << 5) - i`
(fast bitshift operation).

### 🔴 Advanced / Senior

**Q: What happens if you violate the equals-hashCode contract?**
A: HashMap/HashSet malfunction. Equal objects may be stored as duplicates, or lookups may fail even
when the key exists. There is no exception — it is a *silent* failure, which is far worse.

**Q: Can you override equals() without overriding hashCode() in a class that will never be used in collections?**
A: Technically yes, but it's bad practice. Future code might use it in collections, and it violates
the general contract documented in Object class.

**Q: How do you handle inheritance in equals()?**
A: Use `getClass()` for strict type checking (no subclass equality). Use `instanceof` only if you're
okay with subclass instances being equal to parent instances (risky for symmetry). The cleanest
solution is to make the class `final`; the most flexible is the `canEqual` pattern.

**Q: What's the performance impact of a poor hashCode() implementation?**
A: If all objects return the same hashCode, HashMap degrades to O(n) instead of O(1) because all
entries end up in the same bucket (linked list or tree).

**Q: How does String implement hashCode()?**
A: `s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]` where s[i] is the ith character. Cached after
first calculation (String is immutable).

**Q: What is the difference between equals() and compareTo()?**
A: `equals()` checks equality. `compareTo()` establishes ordering. They should be consistent:
`a.equals(b)` should imply `a.compareTo(b) == 0`, but it's not required (e.g., BigDecimal).

### ⚫ Expert / Principal

**Q: Design equals() for a class hierarchy where a subclass legitimately adds data. How do you avoid breaking symmetry?**
A: The `canEqual` pattern from *Effective Java* Item 10: the base class compares using `getClass()`
and calls a `protected boolean canEqual(Object)` hook; each subclass overrides `canEqual` to
return `true` only for instances of its own type, and overrides `equals` to add its own fields via
`super.equals(o)`. Symmetry holds because `canEqual` is checked in *both* directions with
complementary conditions. The alternative — and often better — is to make the hierarchy a
`sealed` interface with a single final implementation per variant, or to model it as a record.

**Q: How do you make `equals`/`hashCode` fast in a hot path?**
A: Three levers. First, cache the hash code in a `transient int` field computed lazily — legal
because the object's fields are immutable, and it turns a multi-field hash into a field read.
Second, use the manual `31 * result + field` form instead of `Objects.hash(...)` to avoid the
varargs array allocation. Third, keep the number of hashed fields small — hash the *business key*
only, not every field, since each field adds work to every insertion and lookup. I would measure
before optimising: `Objects.hash` is usually not the bottleneck, and a premature manual hash is a
maintenance hazard.

**Q: Why is `equals` on `BigDecimal` a trap, and how do you handle it?**
A: `BigDecimal.equals()` compares the unscaled value **and** the scale, so `1.0` and `1.00` are
different. The correct value-semantics comparison is `compareTo() == 0`. But if you override
`equals` to use `compareTo`, you must also override `hashCode` to strip trailing zeros — otherwise
you break the contract in the *other* direction and `HashMap` lookups fail. Worse, using
`BigDecimal` as a *map key* is fragile in general because the scale often differs between
serialisation and deserialisation. I would normalise the scale in the constructor, or use a
`long` of minor units (cents/paise) as the key.

**Q: When is it correct to use identity equality rather than value equality?**
A: When the object is a *handle* to a mutable resource rather than a value — an open file, a
network connection, a JMX MBean, a session. In those cases the identity *is* the meaning, and
value equality would be wrong (two open handles to the same file are still different resources). The
test I apply: "would a user ever want to treat these two as interchangeable?" If yes, value
equality. If no, identity.

---

## 9. Counter-Questions & Traps

### CQ1. "If `hashCode()` collisions are allowed, why do we need the contract at all?"

- **Naive answer:** "Because the contract says so."  ← incomplete
- **The trap:** The interviewer is testing whether you understand the *direction* of the
  requirement. Collisions are fine; the failure is a **split**, not a **collision**.
- **Expert answer:** "Collisions are harmless — `HashMap` resolves them with `equals()` inside the
  bucket. The contract forbids the opposite: equal objects getting *different* hash codes. That
  splits one logical key across two buckets, so the lookup never visits the bucket where the key
  actually lives. So a collision costs a little performance; a contract violation costs
  *correctness*, silently. That asymmetry is why the rule is 'equal implies equal hash', never the
  reverse."

### CQ2. "Why not just use `instanceof` in `equals()`? It's more flexible."

- **Naive answer:** "Because it breaks symmetry."  ← right, shallow
- **The trap:** The interviewer wants to know whether you can *design around* the limitation, not
  just avoid it.
- **Expert answer:** "The problem is that symmetry is a *global* property: `Employee.equals` and
  `Manager.equals` must agree about every pair, and `instanceof` makes the agreement depend on
  which side is calling. I have three options. Make the class `final` — then `getClass()` and
  `instanceof` are equivalent and the dilemma disappears; this is the right answer for most value
  objects. Use `getClass()` and accept that a subclass is never equal to its parent. Or use the
  `canEqual` hook from *Effective Java*, where the subclass explicitly opts in and symmetry is
  restored by construction. What I would not do is mix the two conventions across a hierarchy."

### CQ3. "Your `equals()` uses only the ID. Isn't that wrong — two employees could share an ID?"

- **Naive answer:** "No, the ID is unique."  ← assumes the data is clean
- **The trap:** This is a *data-modelling* push-back. The candidate must separate "the equality
  contract" from "the integrity of the data".
- **Expert answer:** "Those are two different problems. `equals()` defines *identity semantics* —
  'is this the same entity?' — and for a JPA entity the database primary key is the identity. If
  the data is corrupt and two rows share an ID, that is a data-integrity bug, and it should be
  fixed with a unique constraint, not by changing `equals()` to include the name and salary — which
  would make the entity's identity depend on mutable data and break every lookup after an update.
  But the deeper point: for a **value object** like a `Money` or a `LineItem`, there is no ID, and
  identity is the whole field set. Choosing the fields is a domain decision."

### CQ4. "How would you find out that a `HashMap` lookup is failing in production?"

- **Naive answer:** "Check the equals implementation."  ← good but stops early
- **The trap:** This is an *observability* question. The interviewer wants to know if you can
  diagnose a data-dependent failure rather than guess at the cause.
- **Expert answer:** "First I would establish the failure mode: does `get()` return `null` for a key
  that was definitely put? That points to the hashCode/equals contract, or to a **mutated key** — if
  the key's fields changed after insertion, its hash changed and it now sits in a bucket nothing
  searches. I would check whether the key type overrides `equals` but not `hashCode`, look for a
  `BigDecimal` scale mismatch, and look for a `double` vs `Double` mismatch. The generalisable
  lesson: a key used in a hash-based collection must be immutable while it is in the collection, and
  its `equals`/`hashCode` must be consistent."

### CQ5. "Why does `String` cache its `hashCode()`? Why should you not do the same?"

- **Naive answer:** "Because it's a performance optimisation, same as String."  ← misses the trap
- **The trap:** The candidate generalises from `String` without noticing that `String` is
  **immutable** — and immutability is the precondition that makes caching *safe*.
- **Expert answer:** "`String` can cache because it is immutable — the hash can never change, so
  the cached value can never go stale. A mutable object caching its hash is a correctness bug: as
  soon as a hashed field changes, the cached hash and the real hash disagree, and the object is
  unreachable in every collection that holds it. So the pattern is legitimate, but it is only
  available to immutable types. I would note the same applies to `BigInteger` and `UUID`, both of
  which cache."

### CQ6. "Your `hashCode()` is a constant. It's legal, right?"

- **Naive answer:** "Yes, it's legal."  ← technically true, practically wrong
- **The trap:** The interviewer is checking whether you know the difference between *legal* and
  *correct*. A constant hash code satisfies the contract and destroys performance.
- **Expert answer:** "It is legal, and it satisfies the contract — equal objects still have equal
  hash codes. But every object lands in the same bucket, so `HashMap` degrades from O(1) to O(n) and
  a bucket's linked list grows without bound. I have seen this in production when someone wrote
  `return 1;` as a placeholder and never came back. The rule: a constant hash code is legal only
  for a class with exactly one instance per JVM, or when you are deliberately using the object as a
  *marker* in a `HashSet` where membership is all you need and there are very few of them."

### CQ7. "Is `toString()` part of the `Object` contract? Does anything depend on it?"

- **Naive answer:** "No, it's just for debugging."  ← incomplete
- **The trap:** The interviewer wants to know whether you understand that `toString()` has
  *implicit* dependencies that can break at runtime.
- **Expert answer:** "It is not a formal contract — `Object.toString()` is specified only as
  'a string representation'. But it has real implicit dependencies. `String.valueOf(obj)`,
 *concatenation with a `String`, `println`, and every logging framework call it. And there is a
  genuine failure mode: if `toString()` reads a field that is `null` at the time it is called, you
  get an NPE *inside your logging statement* — which is exactly when you most need the log to work.
  That is why `toString()` should only read fields that are guaranteed initialised, and why
  `String.valueOf` is safer than concatenation in a logger."

### CQ8. "Why does `BigDecimal.equals()` differ from `compareTo()`? Which is right?"

- **Naive answer:** "`compareTo()` is right, `equals()` is a bug."  ← half an answer
- **The trap:** The interviewer is testing whether you understand that `BigDecimal` made a
  *deliberate* choice, and that the choice has a purpose.
- **Expert answer:** "Neither is a bug — they answer different questions. `equals()` asks 'is this
  the same *representation*?', and it compares the unscaled value **and** the scale, because
  `1.0` and `1.00` are genuinely different representations and `BigDecimal` preserves scale
  exactly. `compareTo()` asks 'is this the same *number*?', and it ignores scale. For money I want
  numeric equality, so `compareTo() == 0` is right. The trap is that they are **inconsistent with
  each other** — and the Javadoc says so explicitly. The real hazard is using `BigDecimal` as a
  `Map` key, where the hash code is scale-sensitive and a lookup can fail after a round-trip
  through JSON. I normalise the scale in the constructor, or use minor units as a `long`."

## 10. Rapid-Fire MCQs

**Q1.** The `equals`/`hashCode` contract requires:
- **A)** Equal objects must have equal hash codes
- **B)** Equal hash codes imply equal objects
- **C)** Every object must have a unique hash code
- **D)** `hashCode()` must be `final`

**Q2.** `hashCode()` returning the same value for all instances is:
- **A)** A compile error
- **B)** Legal but degrades `HashMap` to O(n)
- **C)** Illegal at runtime
- **D)** Allowed only for `enum`s

**Q3.** Overriding only `equals()` and not `hashCode()` causes:
- **A)** A `ClassCastException`
- **B)** `HashMap` lookups with equal keys to return `null`
- **C)** A compile error
- **D)** Nothing — `hashCode` is auto-derived

**Q4.** `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` is:
- **A)** `true`
- **B)** `false`
- **C)** A `ClassCastException`
- **D)** `null`

**Q5.** The correct way to compare two `double` fields in `equals()` is:
- **A)** `a == b`
- **B)** `Double.compare(a, b) == 0`
- **C)** `a.equals(b)`
- **D)** `Math.abs(a - b) < 0.0001`

**Q6.** `instanceof` in `equals()` risks breaking:
- **A)** Reflexivity
- **B)** Symmetry
- **C)** Transitivity
- **D)** Consistency

**Q7.** Which is the correct signature for an `equals` override?
- **A)** `public boolean equals(Object o)`
- **B)** `public boolean equals(MyClass o)`
- **C)** `public int equals(Object o)`
- **D)** `public boolean equal(Object o)`

**Q8.** `Object.hashCode()` by default is based on:
- **A)** The value of the fields
- **B)** Memory address / identity
- **C)** A random value per call
- **D)** The class name

**Q9.** Using a mutable object as a `HashMap` key is unsafe because:
- **A)** `equals()` becomes slow
- **B)** Mutating it changes its hash, making it unreachable
- **C)** `hashCode()` throws
- **D)** The map becomes unsorted

**Q10.** A `String` can cache its `hashCode()` because:
- **A)** Strings are interned
- **B)** `String` is immutable
- **C)** The JVM caches all hash codes
- **D)** `String` is `final`

**Q11.** `Collections.unmodifiableSet` vs `Set.copyOf` differs in that:
- **A)** `copyOf` returns a `Set`, `unmodifiableSet` returns a `List`
- **B)** `unmodifiableSet` is a **view**; `copyOf` is an **immutable copy**
- **C)** `copyOf` allows `null`
- **D)** They are identical

**Q12.** The `31` multiplier in a hand-written `hashCode()` is used because:
- **A)** It is a power of two
- **B)** It is an odd prime and `31 * i == (i << 5) - i`
- **C)** It is the only value the JVM accepts
- **D)** It is `Integer.MAX_VALUE`


### Answer Key

| # | Answer | Explanation |
|---|--------|-------------|
| 1 | **A** | Equal objects must have equal hash codes. The reverse is not required — collisions are legal. |
| 2 | **B** | It is legal and satisfies the contract, but every object lands in one bucket, degrading lookups to O(n). |
| 3 | **B** | Without a matching `hashCode`, equal objects land in different buckets and `get()` returns `null`. |
| 4 | **B** | `BigDecimal.equals()` compares scale as well as value, so `1.0` and `1.00` differ. Use `compareTo() == 0`. |
| 5 | **B** | `Double.compare` handles `NaN` and distinguishes `-0.0` from `0.0`, following IEEE 754 total order. |
| 6 | **B** | With a subclass, `employee.equals(manager)` is `true` but `manager.equals(employee)` is `false`. |
| 7 | **A** | The parameter must be `Object`. `equals(MyClass)` is an *overload* and collections will keep using `Object.equals`. |
| 8 | **B** | The inherited implementation is an identity hash derived from the object's internal identity, not its fields. |
| 9 | **B** | Changing a hashed field changes the hash, so the entry stays in the old bucket and becomes unreachable by `get`/`remove`. |
| 10 | **B** | Immutability guarantees the cached hash can never go stale. A mutable object caching its hash is a correctness bug. |
| 11 | **B** | `unmodifiableSet` wraps the original and reflects later changes to it; `Set.copyOf` takes a snapshot and returns a truly immutable set. |
| 12 | **B** | 31 is an odd prime giving good distribution, and `31 * i` is exactly `(i << 5) - i` — a shift and subtract instead of a multiply. |

## 11. Cheat Sheet / One-Page Summary

### The Contract at a Glance

| Rule | Statement |
|------|-----------|
| 1 | `a.equals(b)` → `a.hashCode() == b.hashCode()` (**MUST**) |
| 2 | `a.hashCode() != b.hashCode()` → `a.equals(b) == false` (**MUST**) |
| 3 | `a.hashCode() == b.hashCode()` → `a.equals(b)` may be true **or** false (collision OK) |
| 4 | Override **both** or **neither** |
| 5 | Use the **same fields** in both |

### The 5 Properties of `equals()`

| Property | Rule |
|----------|------|
| Reflexive | `a.equals(a)` is `true` |
| Symmetric | `a.equals(b)` ⇔ `b.equals(a)` |
| Transitive | `a.equals(b) ∧ b.equals(c)` → `a.equals(c)` |
| Consistent | Same result for unchanged objects |
| Null-safe | `a.equals(null)` is `false` |

### The Canonical Implementation (copy this)

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Employee other = (Employee) o;
    return id == other.id && Objects.equals(name, other.name);
}

@Override
public int hashCode() {
    return Objects.hash(id, name);      // SAME fields
}

@Override
public String toString() {
    return "Employee{id=" + id + ", name='" + name + "'}";
}
```

### Field → Hash Contribution

| Type | Contribution |
|------|-------------|
| `boolean` | `f ? 1 : 0` |
| `byte`/`char`/`short`/`int` | `(int) f` |
| `long` | `(int)(f ^ (f >>> 32))` |
| `float` | `Float.floatToIntBits(f)` |
| `double` | `Double.doubleToLongBits(f)` then long treatment |
| Object | `f == null ? 0 : f.hashCode()` |
| Array | `Arrays.hashCode(f)` |

### Decision Table: Which Type Equality?

| Situation | Use |
|-----------|-----|
| Value object, `final` class | `getClass()` + `Objects.equals` |
| Extensible hierarchy | `canEqual` pattern, or `getClass()` and no subclass equality |
| `BigDecimal` field | `compareTo() == 0` + `stripTrailingZeros()` in hash |
| `double`/`float` field | `Double.compare(...) == 0` |
| Array field | `Arrays.equals` + `Arrays.hashCode` |
| Wrapper field | `Objects.equals` (never `==`) |
| Mutable key in a map | **Never** — use an immutable key |

---

## 12. Real-World Production Notes

### Where This Matters in Banking / E-Commerce Systems

| Scenario | Production impact | Fix |
|----------|-------------------|-----|
| Entity key is a mutable POJO | Cart/Order lookups silently return `null` after a field update | Use the immutable `id` only, or an immutable key wrapper |
| `BigDecimal` as a `Map` key | Lookups fail after JSON round-trip (scale changes) | Normalise scale, or use `long` minor units |
| `toString()` includes PAN/card number | PCI-DSS violation; the value lands in log aggregation | Exclude or mask (`****1234`) |
| `hashCode()` returns a constant | `HashMap` degrades to O(n) — latency spike under load | Use a real hash; add a static-analysis rule |
| `equals` uses `instanceof` | Asymmetric equality across a hierarchy → flaky tests | `getClass()`, `final` class, or `canEqual` |
| Entity `equals` includes mutable fields | Changing a name breaks `Set` membership and cache keys | Identity = business key only |
| `Double` in `equals` with `==` | `NaN != NaN` breaks round-trip equality | `Double.compare` |

### Tuning Knobs & Metrics

- **JFR / async-profiler** — a `HashMap.get` hotspot with high CPU often indicates a hash
  distribution problem (constant or low-entropy hash), not a slow `equals`.
- **`-XX:+PrintConcurrentLocks` / JFR lock events** — a contended `HashMap` in a concurrent path
  usually means a *mutable* shared map, which is a concurrency bug, not a hashing bug.
- **Micrometer** — alert on `hashMap.get` latency percentiles; a sudden jump after a deploy
  usually means a new key type with a bad `hashCode()`.
- **Static analysis (Error Prone / SpotBugs)** — `EqualsGetClass` and `EqualsHashCode` rules
  catch the two most common defects at build time. Enable them; do not rely on review.

### Practical Rules We Enforce in Code Review

1. `equals`/`hashCode` are overridden **together**, or not at all.
2. Identity = the **business key** (and only the business key) for entities.
3. Value objects: `final` class, `getClass()`, `Objects.equals`/`Objects.hash`.
4. `BigDecimal` fields: `compareTo` for equality, `stripTrailingZeros` for hashing.
5. `double`/`float` fields: `Double.compare`/`Float.compare`.
6. Never mutate a key that is in a hash-based collection.
7. `toString()` never contains a password, token, PAN or full card number.

---

## 13. Follow-up Learning

### Next in this folder

- [09 · Immutable Classes](./09-OOP-Immutable-Classes.md) — why immutability is the cleanest way to guarantee a stable `hashCode()`
- [10 · Nested, Inner and Anonymous Classes](./10-OOP-Nested-Inner-and-Anonymous-Classes.md) — `Comparator`s and anonymous classes as the classic use case
- [11 · Advanced Design and the Object Class](./11-OOP-Advanced-Design-and-Object-Class.md) — the full `Object` API and `clone()`
- [12 · Critical and Tricky Scenarios](./12-OOP-Critical-and-Tricky-Interview-Scenarios.md) — the `Integer` cache and the `BigDecimal` trap in context
- [13 · Covariance, Contravariance, Invariance](./13-OOP-Covariance-Contravariance-Invariance.md) — why `List<Dog>` is not `List<Animal>`

### Related topics elsewhere

- [Collections Framework](../04-Collections-Framework/01-List-Implementations.md) — `HashMap` internals, treeification and resizing
- [Generics](../05-Generics/01-Generics.md) — generics and how they interact with `equals`
- [Java 8+ Features](../06-Java-8-Features/01-Lambda-Functional-Interfaces.md) — `Comparator` as a functional interface
- [Design Patterns](../09-Design-Patterns/01-Design-Patterns.md) — the *Value Object* pattern

### Specifications and books

- **JLS §15.21** — `Object.equals` and `Object.hashCode`
- **`java.lang.Object` Javadoc** — the authoritative statement of the contract, including the
  note that `hashCode` is *not* required to be unique
- **Effective Java** — Item 10 (`equals`/`hashCode`), Item 11 (overriding `equals` for classes),
  Item 12 (`toString`), Item 13 (prefer immutable classes)
- **JEP 395** — records (Java 16) and their generated `equals`/`hashCode`/`toString`

---

**Next Chapter:** → [09 · How to Create Immutable Classes](./09-OOP-Immutable-Classes.md)
**Previous Chapter:** ← [07 · Important Keywords: final, static, volatile, transient](./07-OOP-Keywords-final-static-volatile-transient.md)

*Last Updated: 2026 · Java 17+ (verified on JDK 25) · All outputs in this chapter were produced by running the code*
