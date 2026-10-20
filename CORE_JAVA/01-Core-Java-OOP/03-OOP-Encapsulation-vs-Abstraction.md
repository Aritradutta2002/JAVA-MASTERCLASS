# Encapsulation vs Abstraction — The Complete Interview Guide

> **The most-confused pair in OOP.** They are *not* opposites — they solve
> **different problems**: encapsulation is about **protecting state**,
> abstraction is about **hiding complexity**. Confusing them is the fastest way
> to write a class with 40 getters and no invariants.

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

- **Encapsulation** = **data hiding**. Bundle the data with the code that protects it, and prevent outside code from putting the object into an invalid state.
- **Abstraction** = **implementation hiding**. Expose *what* a thing does, hide *how* it does it.
- **The one-line distinction:** *encapsulation is about the **data**; abstraction is about the **behaviour**.* Encapsulation keeps the *state* safe; abstraction keeps the *mechanism* out of sight.
- **Encapsulation is enforced by `private` fields + methods that maintain the invariant.** Abstraction is enforced by **interfaces and abstract classes** — a contract with no implementation detail.
- **A getter that returns a mutable field breaks encapsulation.** `getItems()` returning the live `List` lets the caller mutate the object's state behind its back. Return a **copy** (`List.copyOf`) or an unmodifiable view.
- **The tell for a broken encapsulation:** if a caller can put the object into an invalid state, the encapsulation is decorative.
- **The tell for a broken abstraction:** if the caller must know *how* the thing works (the algorithm, the storage, the protocol) to use it, the abstraction is leaky.
- **They work together:** an `interface` is the *abstract* boundary; the implementing class's `private` fields are the *encapsulated* state behind it.
- **The smell:** a class with 15 getters and 15 setters and no behaviour has **encapsulation without a purpose** — it is a data holder, and it should be a `record` or a DTO.
- **The rule:** *encapsulate the data, abstract the behaviour.*

---

## 1. What & Why — The Problem This Solves

**The problem #1 — invalid states.**

```java
// ❌ BEFORE — anyone can break the object
class BankAccount {
    public double balance;
}

BankAccount a = new BankAccount();
a.balance = -5000;      // 💥 an account with a negative balance
```

**The problem #2 — leaky complexity.**

```java
// ❌ BEFORE — the caller must know HOW the discount works
class PriceService {
    BigDecimal calculate(BigDecimal subtotal, String customerType, int monthsActive) {
        // the caller must know: the tiers, the thresholds, the rounding…
    }
}
```

**The two solutions:**

```text
   ENCAPSULATION solves problem #1 — the OBJECT cannot be corrupted
   ─────────────────────────────────────────────────────────────
   "An object must always be in a valid state. The class guarantees it."

   ABSTRACTION solves problem #2 — the CALLER need not know the mechanism
   ─────────────────────────────────────────────────────────────
   "A client depends on WHAT, never on HOW."
```

> 💡 **The interview framing.** The best answer connects them: *"encapsulation is
> about the object's internal consistency; abstraction is about the client's
> ignorance of the implementation. They are orthogonal: a class can be perfectly
> encapsulated and perfectly non-abstract (a `String`), or abstract and badly
> encapsulated (a DTO with public fields)."*

---

## 2. Mental Model — The One Picture

```text
   THE TWO LAYERS OF "HIDING"

   ┌──────────────────────────────────────────────────────────────┐
   │  THE CLIENT sees:                                           │
   │                                                              │
   │      account.withdraw(100);                                 │
   │      ↑ WHAT it does  (abstraction — the contract)            │
   └──────────────────────────────────────────────────────────────┘
        ║  ← the client does NOT know: how the money is checked,
        ║    how the balance is stored, whether it is a BigDecimal
        ║    or a long, whether there is a ledger…
        ║
   ┌───▼──────────────────────────────────────────────────────────┐
   │  THE OBJECT hides:                                          │
   │                                                              │
   │      private BigDecimal balance;    ← the DATA (encapsulated) │
   │      private boolean overdraft;                             │
   │      private List<Transaction> ledger;                      │
   │                                                              │
   │      private void ensureSufficient(BigDecimal amount) { … }  │
   │      public void withdraw(BigDecimal amount) { … }           │
   └──────────────────────────────────────────────────────────────┘

   ENCAPSULATION = the bottom box  (the data + the rules that protect it)
   ABSTRACTION   = the top edge   (the contract the client depends on)

   ❌ A GETTER-SETTER CLASS HAS NEITHER:
      the client can set balance = -5000  → the DATA is not protected
      the client must call setBalance()   → the MECHANISM is visible
```

**What to look at:** the two hiding mechanisms protect against **different adversaries** —
encapsulation against *other code corrupting the object*, abstraction against *other code
depending on the details*.

---

## 3. Deep Dive — Theory

## 3.1 Encapsulation — the definition and the mechanism

```java
public class BankAccount {

    // ① THE DATA IS PRIVATE — no one outside can touch it
    private final String id;
    private BigDecimal balance;
    private final List<Transaction> ledger = new ArrayList<>();
    private final AccountStatus status;

    // ② THE INVARIANT IS ENFORCED IN ONE PLACE
    //    "a non-frozen account never has a negative balance"
    private void requireSufficientFunds(BigDecimal amount) {
        if (status == AccountStatus.FROZEN) {
            throw new AccountFrozenException("Account " + id + " is frozen");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Balance " + balance + " is less than " + amount);
        }
    }

    // ③ THE BEHAVIOUR IS THE PUBLIC API — and it maintains the invariant
    public void withdraw(BigDecimal amount) {
        requireSufficientFunds(amount);
        balance = balance.subtract(amount);
        ledger.add(Transaction.withdrawal(amount, balance));
    }

    public void deposit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        balance = balance.add(amount);
        ledger.add(Transaction.deposit(amount, balance));
    }

    // ④ READING IS SAFE — a copy, or a derived value
    public BigDecimal balance() {
        return balance;   // ⚠ BigDecimal is immutable, so returning it is SAFE
    }

    public List<Transaction> ledger() {
        return List.copyOf(ledger);   // ✅ a COPY — the caller cannot mutate our state
    }

    public String id() { return id; }
}
```

**The three mechanisms of encapsulation:**

| Mechanism | What it does | Example |
|---|---|---|
| **Private state** | No external code can reference the field | `private BigDecimal balance;` |
| **Guarded mutation** | All state changes go through a method that maintains the invariant | `withdraw()` checks the funds first |
| **Defensive copies** | Mutable state is not handed out | `List.copyOf(ledger)` |

**The third one is the one people forget.** Two examples:

```java
// ❌ the caller mutates our internal list
public List<String> getTags() { return tags; }

// ✅ the caller gets an unmodifiable view (cheap) or a copy (safe)
public List<String> getTags() { return List.copyOf(tags); }        // a copy
public List<String> getTagsView() { return Collections.unmodifiableList(tags); }  // a view
```

```java
// ❌ the caller mutates our internal array
public byte[] getData() { return data; }

// ✅ a copy
public byte[] getData() { return data.clone(); }
```

> 💡 **The `unmodifiableList` vs `copyOf` distinction again:** a view still reflects
> later changes to the backing list — it is read-only, not immutable. If the object
> must be genuinely immutable, use `copyOf` (or clone).

## 3.2 The invariant — the heart of encapsulation

An **invariant** is a condition that must hold for the object's entire lifetime.

```text
   THE INVARIANT OF A BankAccount
   ───────────────────────────────
   • balance is never null
   • balance is never negative (unless the account is an overdraft facility)
   • the ledger's last entry's balance == the current balance
   • a FROZEN account's balance never changes
   • the id never changes

   THE INVARIANT IS ENFORCED:
     • by the CONSTRUCTOR (establish it)
     • by EVERY MUTATOR (preserve it)
     • and it is OBSERVABLE by the test suite
```

**The pattern that makes this work:**

```java
public class BankAccount {
    private BigDecimal balance;

    // ① ESTABLISH the invariant
    public BankAccount(BigDecimal openingBalance, boolean allowOverdraft) {
        if (openingBalance == null) {
            throw new IllegalArgumentException("Opening balance must not be null");
        }
        if (openingBalance.signum() < 0 && !allowOverdraft) {
            throw new IllegalArgumentException("Opening balance must not be negative");
        }
        this.balance = openingBalance;
    }

    // ② PRESERVE the invariant — every mutator re-checks
    public void withdraw(BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Balance " + balance + " < " + amount);
        }
        balance = balance.subtract(amount);
    }

    // ③ Never expose the raw state in a way that breaks the invariant
    public BigDecimal balance() { return balance; }
}
```

**The payoff:** a caller *cannot* create an invalid account. There is no sequence of
API calls that produces a negative balance. The invariant is a property of the **type**,
not of the callers' discipline. That is the difference between encapsulation and
"please don't do that".

## 3.3 Abstraction — the definition and the mechanism

```java
// ═══ THE ABSTRACTION — the contract the client depends on
public interface PaymentProcessor {
    PaymentResult charge(PaymentRequest request);
}

// ═══ THE IMPLEMENTATION — the mechanism the client never sees
public class StripePaymentProcessor implements PaymentProcessor {

    private final StripeClient stripe;
    private final String apiKey;
    private final RetryPolicy retryPolicy;      // the "HOW" — invisible to the client

    public StripePaymentProcessor(StripeClient stripe, String apiKey, RetryPolicy retryPolicy) {
        this.stripe = stripe;
        this.apiKey = apiKey;
        this.retryPolicy = retryPolicy;
    }

    @Override
    public PaymentResult charge(PaymentRequest request) {
        // the HTTP call, the retries, the error mapping — all invisible
        for (int attempt = 0; attempt < retryPolicy.maxAttempts(); attempt++) {
            try {
                return stripe.charge(apiKey, request);
            } catch (StripeTransientException e) {
                retryPolicy.backoff(attempt);
            }
        }
        return PaymentResult.failure("Charge failed");
    }
}
```

**The client code:**

```java
// ✅ The client depends on the ABSTRACTION
PaymentProcessor processor = new StripePaymentProcessor(stripe, key, retryPolicy);
PaymentResult result = processor.charge(request);
```

**What the client does NOT know:** that it is Stripe, that there is an HTTP call, that
there are 3 retries with exponential back-off, that a `StripeTransientException` is
retried but a `StripeCardException` is not, and that the API key is loaded from a
secret manager. **That ignorance is the abstraction working.**

**The three levels of abstraction:**

```text
   ① CONCRETE USE — the client knows the concrete class
      new StripePaymentProcessor(...).charge(request);
      → NO abstraction — the client is coupled to Stripe

   ② INTERFACE USE — the client knows the contract
      PaymentProcessor p = ...; p.charge(request);
      → ABSTRACTION ✅ — the client is coupled to the contract

   ③ ABSTRACT BEHAVIOUR — the client supplies the behaviour
      list.stream().map(this::toDto).collect(...)
      → ABSTRACTION of a different kind — the client defines the "what"
```

**The "leaky abstraction" test:** if the client must know *how* to use the abstraction
correctly — the order of calls, whether you must call `initialise()` first, whether
`close()` is mandatory — the abstraction is **leaky**.

## 3.4 The two, side by side

| | Encapsulation | Abstraction |
|---|---|---|
| **The question it answers** | "How do I keep this object valid?" | "What does this thing do?" |
| **What is hidden** | The **data** and the rules | The **implementation** and the mechanism |
| **Applied to** | Fields, invariants, mutation | The *interface* of a type |
| **Mechanism** | `private` + guarded mutators + defensive copies | Interfaces, abstract classes, `sealed` types |
| **Who benefits** | The **class author** (the invariant is guaranteed) | The **client** (the mechanism is hidden) |
| **The opposite failure** | A DTO with public fields (nothing is protected) | A client that must know the algorithm |
| **Enforced by** | The compiler (for `private`) + the design (for the invariant) | The compiler (for the type) + the design (for the contract) |
| **A `String`** | ✅ deeply encapsulated | ✅ deeply abstract (you never see the `char[]`) |
| **A DTO with public fields** | ❌ not encapsulated | ❌ not abstract (the fields ARE the API) |

## 3.5 The "getter-setter class" — the anti-pattern

```java
// ❌ THE ANTI-PATTERN — 30 lines of boilerplate, ZERO invariants
public class User {
    private String name;
    private int age;
    private boolean active;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    // … and 20 more
}
```

**Three problems:**

| Problem | Why it matters |
|---|---|
| **No invariant** | A user can have `age = -5` or `name = null` |
| **The API is a data structure, not a behaviour** | The caller assembles the object and can assemble it wrongly |
| **It is a DTO pretending to be a domain object** | Use a `record` (Java 16+) or a DTO |

**The modern answer:**

```java
// ✅ A record — immutable, with generated accessors and equals/hashCode
public record User(String name, int age, boolean active) {
    public User {                          // the compact constructor — validation
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (age < 0 || age > 150) {
            throw new IllegalArgumentException("age must be between 0 and 150");
        }
    }
}
```

```java
// ✅ A domain object with BEHAVIOUR and invariants
public class User {
    private final String name;
    private final LocalDate birthDate;
    private boolean suspended;

    public User(String name, LocalDate birthDate) {
        this.name = requireNonNull(name);
        this.birthDate = requireNonNull(birthDate);
    }

    public void suspend() { suspended = true; }
    public void reactivate() { suspended = false; }
    public boolean canOrder() { return !suspended && age() >= 18; }
    public int age() { return Period.between(birthDate, LocalDate.now()).getYears(); }

    // No setters — the only way to change the state is through the behaviour,
    // and every behaviour maintains the invariant.
}
```

> 💡 **The test:** *does the class have any behaviour beyond storing and returning
> data?* If not, it is a DTO — make it a `record` and stop pretending it is a domain
> object. The "getter-setter class" is the most common form of accidental
> non-encapsulation in production Java.

## 3.6 Encapsulation and the Liskov Substitution Principle

```java
// ❌ The setter BREAKS the subclass's invariant
public class Account {
    protected BigDecimal balance;

    public void setBalance(BigDecimal balance) {
        this.balance = balance;   // ⚠ anyone can set a negative balance
    }
}

public class PremiumAccount extends Account {
    @Override
    public void setBalance(BigDecimal balance) {
        // "premium accounts cannot go negative" — but the base class allows it
        super.setBalance(balance);
    }
}
```

**The fix — the subclass tightens the invariant through the base class's API:**

```java
public class Account {
    private BigDecimal balance;

    protected Account(BigDecimal opening) { this.balance = opening; }

    // The base class owns the mutation, and the SUBCLASS can add a rule
    protected void checkCanSet(BigDecimal newBalance) {
        if (newBalance.signum() < 0) {
            throw new IllegalArgumentException("Balance cannot be negative");
        }
    }

    public final void setBalance(BigDecimal balance) {
        checkCanSet(balance);          // ← the HOOK — the subclass extends the rule
        this.balance = balance;
    }
}
```

**The principle:** the *base class* owns the state and the mutation; the *subclass* may
tighten the rules through a protected hook. This keeps the invariant enforceable and
keeps the Liskov contract intact.

## 3.7 Encapsulation in the JDK — the best examples

| JDK class | How it encapsulates |
|---|---|
| `String` | `private final byte[] value`; you cannot mutate it; `substring` copies |
| `BigDecimal` | `private final BigInteger intVal` + `scale`; the scale is validated and cannot drift |
| `LocalDate` | All fields are `private final`; every mutator returns a **new** instance |
| `ArrayList` | `private transient Object[] elementData`; the size is managed internally |
| `Collections.unmodifiableList` | A wrapper that throws on every mutation — the *only* way to change the list is through the wrapper |
| `Thread` | `private volatile long stackTrace`; the state is managed by the JVM |

**The `LocalDate` pattern — immutability as the strongest encapsulation:**

```java
LocalDate date = LocalDate.of(2026, 2, 14);
LocalDate later = date.plusDays(30);   // ← a NEW object; `date` is unchanged
System.out.println(date);              // 2026-02-14
```

There is no setter, no `setYear`, no `setMonth` — **the invalid states do not exist**,
because there is no way to construct one. That is encapsulation at its strongest: the
API surface makes the bug impossible.

---

## 4. Visualizations

### 4.1 The two layers of hiding

```text
   ┌───────────────────────────────────────────────────────────────┐
   │  THE CLIENT (uses the abstraction)                            │
   │                                                               │
   │     account.withdraw(new BigDecimal("100"));                  │
   │     ^^^^^^^^ WHAT — the contract                              │
   │                                                               │
   │  THE CLIENT DOES NOT KNOW:                                   │
   │    ✗ how the funds are checked (a rule, a policy, a service?) │
   │    ✗ whether the balance is a BigDecimal or a long            │
   │    ✗ whether a ledger is maintained                          │
   │    ✗ what "frozen" means                                    │
   └───────────────────────────────────────────────────────────────┘
        ║  ← the ABSTRACTION boundary
   ┌───▼───────────────────────────────────────────────────────────┐
   │  THE OBJECT (hides the data)                                 │
   │                                                               │
   │     private BigDecimal balance;          ← the DATA          │
   │     private final List<Txn> ledger;                            │
   │     private boolean frozen;                                   │
   │                                                               │
   │     private void requireSufficient(BigDecimal a) { … }        │
   │     public void withdraw(BigDecimal a) {                        │
   │         requireSufficient(a);        ← the RULES              │
   │         balance = balance.subtract(a);                          │
   │     }                                                           │
   └───────────────────────────────────────────────────────────────┘
        ↑ the ENCAPSULATION boundary
```

### 4.2 Encapsulation broken — the getter/setter trap

```text
   ❌ A GETTER-SETTER CLASS
   ─────────────────────────
   class BankAccount {
       public double balance;                 ← PUBLIC FIELD
       public void setBalance(double b) {     ← ANY VALUE ALLOWED
           this.balance = b;
       }
   }

   Account a = new Account();
   a.setBalance(-5000);      → the INVARIANT IS BROKEN 💥
   a.setBalance(Double.NaN);  → 💥
   a.setBalance(0.0);         → 💥 (a zero balance is not the same as "not set")

   ✅ AN ENCAPSULATED CLASS
   ────────────────────────
   class BankAccount {
       private BigDecimal balance;            ← PRIVATE

       public void withdraw(BigDecimal amount) {
           if (balance.compareTo(amount) < 0) throw new InsufficientFundsException();
           balance = balance.subtract(amount);  ← ONLY THIS PATH CAN CHANGE IT
       }
       public BigDecimal balance() { return balance; }
   }

   There is NO API call that can produce a negative balance.
   The invariant is a property of the TYPE, not of the callers' discipline.
```

### 4.3 The leaky abstraction — the client knowing "how"

```text
   ❌ LEAKY — the client must know the mechanism
   ─────────────────────────────────────────────
   PriceService svc = new PriceService();
   BigDecimal d = svc.calculate(subtotal, "GOLD", 24);   // tier? threshold? rounding?
   // the client now depends on:
   //   • the customer-type strings
   //   • the parameter ORDER
   //   • the internal tier rules
   //   • the rounding convention

   ✅ SEALED — the client knows only "what"
   ──────────────────────────────────────
   interface PricingPolicy {
       BigDecimal price(Order order);
   }

   PriceService svc = new PriceService(new GoldPricingPolicy());
   BigDecimal d = svc.price(order);
   // the client depends on:
   //   • the PricingPolicy contract
   //   • nothing else
```

### 4.4 The invalid-state matrix

```text
   THE STATES A BankAccount CAN BE IN
   ─────────────────────────────────────
                    ┌──────────────────┐
        deposit ──▶ │  balance = X     │ ──▶ withdraw
                    │  (never < 0)     │
                    └──────────────────┘
                             │
                        freeze()
                             ▼
                    ┌──────────────────┐
                    │  FROZEN          │  ← withdraw is REJECTED
                    │  balance = X     │
                    └──────────────────┘

   ❌ WITH A PUBLIC FIELD, all of these are reachable:
        balance = -1      balance = NaN      balance = null
        balance = 0 (meaningless)
   → the TYPE has 5 reachable states, and only 1 is valid.

   ✅ WITH ENCAPSULATION, only the 2 valid states are reachable —
      because the only mutation paths are the guarded methods.
```

### 4.5 Encapsulation and inheritance — the protected-field trap

```text
   ❌ protected FIELDS — the subclass can corrupt the invariant
   ──────────────────────────────────────────────────────────
   class Account {
       protected BigDecimal balance;    // ⚠ any subclass may write it directly
   }
   class SloppyAccount extends Account {
       void hack() { balance = new BigDecimal("-9999"); }   // 💥 no validation
   }

   ✅ private FIELD + a protected HOOK — the base class owns the mutation
   ───────────────────────────────────────────────────────────────────
   class Account {
       private BigDecimal balance;              // the state is PRIVATE

       protected void checkCanSet(BigDecimal v) { }   // the subclass EXTENDS the rule
       public final void setBalance(BigDecimal v) {
           checkCanSet(v);                        // ← the hook
           this.balance = v;                      // ← only the base class writes
       }
   }
   class StrictAccount extends Account {
       @Override protected void checkCanSet(BigDecimal v) {
           if (v.signum() < 0) throw new IllegalArgumentException("no negatives");
       }
   }
   → the base class owns the state; the subclass tightens the rules. ✅
```

### 4.6 The JDK's encapsulation spectrum

```text
   MUTABLE & EXPOSED        MUTABLE & PROTECTED      IMMUTABLE
   ─────────────────        ───────────────────      ─────────────────────
   class Point {             class ArrayList {        class String {
     public int x;             private Object[] e;      private final byte[] v;
     public int y;             public int size();       public int length();
   }                          public Object get(int i); public String substring(..)
   }                          }                     }

   ❌ INVALID STATES         ✅ INVARIANT KEPT       ✅ ✅ INVALID STATES
   ❌ NO ABSTRACTION          ⚠ leaks the internals    ✅ PERFECT ABSTRACTION
   (the "getter-setter DTO")  (a mutable list)         (a String)

   THE SPECTRUM IS A DESIGN AXIS:
     mutable+exposed  → a DTO / a mutable POJO
     mutable+protected → a domain object with behaviour
     immutable         → a value object (String, LocalDate, BigDecimal)
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — Encapsulation: the invariant the type guarantees

```java
import java.math.BigDecimal;

public class EncapsulationDemo {

    static class InsufficientFundsException extends RuntimeException {
        InsufficientFundsException(String m) { super(m); }
    }

    static class BankAccount {
        private final String id;
        private BigDecimal balance;
        private boolean frozen;

        BankAccount(String id, BigDecimal opening) {
            if (opening == null) throw new IllegalArgumentException("opening must not be null");
            if (opening.signum() < 0) throw new IllegalArgumentException("opening must not be negative");
            this.id = id;
            this.balance = opening;
        }

        public void withdraw(BigDecimal amount) {
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            if (frozen) throw new IllegalStateException("account " + id + " is frozen");
            if (balance.compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                        "balance " + balance + " is less than " + amount);
            }
            balance = balance.subtract(amount);
        }

        public void deposit(BigDecimal amount) {
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            balance = balance.add(amount);
        }

        public void freeze() { frozen = true; }
        public BigDecimal balance() { return balance; }   // safe: BigDecimal is immutable
        public boolean isFrozen() { return frozen; }
    }

    public static void main(String[] args) {
        BankAccount a = new BankAccount("ACC-1", new BigDecimal("100.00"));

        a.withdraw(new BigDecimal("30.00"));
        System.out.println("balance after withdraw = " + a.balance());

        try {
            a.withdraw(new BigDecimal("1000.00"));
        } catch (InsufficientFundsException e) {
            System.out.println("rejected: " + e.getMessage());
        }

        try {
            new BankAccount("ACC-2", new BigDecimal("-1"));
        } catch (IllegalArgumentException e) {
            System.out.println("rejected at construction: " + e.getMessage());
        }

        a.freeze();
        try {
            a.deposit(new BigDecimal("10.00"));
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }
    }
}
```

**Actual output:**

```text
balance after withdraw = 70.00
rejected: balance 70.00 is less than 1000.00
rejected at construction: opening must not be negative
rejected: amount must be positive
```

**Takeaway:** there is **no API call** that can put the account into a negative balance.
The invariant is a property of the *type*.

### Example 2 — The getter/setter trap, and the record fix

```java
import java.util.List;

public class GetterSetterTrap {

    // ❌ THE ANTI-PATTERN
    static class MutableUser {
        private String name;
        private int age;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    // ✅ THE FIX — a record with a validating compact constructor
    record User(String name, int age) {
        User {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (age < 0 || age > 150) {
                throw new IllegalArgumentException("age must be between 0 and 150");
            }
        }
    }

    public static void main(String[] args) {
        MutableUser bad = new MutableUser();
        bad.setName("");
        bad.setAge(-5);
        System.out.println("❌ the mutable version: name='" + bad.getName() + "' age=" + bad.getAge()
                + "  ← an INVALID user, and nothing stopped it");

        try {
            new User("", 200);
        } catch (IllegalArgumentException e) {
            System.out.println("✅ the record: " + e.getMessage() + "  ← impossible to construct");
        }

        User ok = new User("Alice", 30);
        System.out.println("✅ valid user = " + ok);
    }
}
```

**Actual output:**

```text
❌ the mutable version: name='' age=-5  ← an INVALID user, and nothing stopped it
✅ the record: name must not be blank  ← impossible to construct
✅ valid user = User[name=Alice, age=30]
```

**Takeaway:** a getter-setter class has *no* invariant — every invalid state is
reachable. A `record` with a compact constructor makes the invalid states
**unconstructible**.

### Example 3 — The defensive copy

```java
import java.util.ArrayList;
import java.util.List;

public class DefensiveCopyDemo {

    static class Tags {
        private final List<String> tags = new ArrayList<>();

        void add(String tag) { tags.add(tag); }

        // ❌ LEAKS THE INTERNAL STATE
        List<String> getTagsUnsafe() { return tags; }

        // ✅ A COPY — a genuine snapshot
        List<String> getTagsCopy() { return List.copyOf(tags); }

        // ✅ AN UNMODIFIABLE VIEW — cheap, but still reflects later changes
        List<String> getTagsView() { return java.util.Collections.unmodifiableList(tags); }
    }

    public static void main(String[] args) {
        Tags t = new Tags();
        t.add("java");

        List<String> unsafe = t.getTagsUnsafe();
        unsafe.add("💥 INJECTED");
        System.out.println("❌ unsafe getter → internal state = " + t.getTagsUnsafe()
                + "   ← the caller corrupted our object");

        Tags t2 = new Tags();
        t2.add("java");
        List<String> copy = t2.getTagsCopy();
        try {
            copy.add("💥");
        } catch (UnsupportedOperationException e) {
            System.out.println("✅ copy        → " + t2.getTagsCopy() + "   ← unmodifiable");
        }

        Tags t3 = new Tags();
        t3.add("java");
        List<String> view = t3.getTagsView();
        t3.add("added-later");
        System.out.println("✅ view        → " + t3.getTagsView()
                + "   ← a VIEW reflects later changes (read-only, not immutable)");
    }
}
```

**Actual output:**

```text
❌ unsafe getter → internal state = [java, 💥 INJECTED]   ← the caller corrupted our object
✅ copy        → [java]   ← unmodifiable
✅ view        → [java, added-later]   ← a VIEW reflects later changes (read-only, not immutable)
```

**Takeaway:** returning the live `List` lets any caller break the object's invariant.
`copyOf` gives a true snapshot; `unmodifiableList` gives a read-only *view* that still
reflects the source.

### Example 4 — A leaky abstraction, and the fix

```java
import java.math.BigDecimal;

public class LeakyAbstractionDemo {

    // ❌ LEAKY — the client must know the MECHANISM
    static class LeakyPriceService {
        BigDecimal calculate(BigDecimal subtotal, String customerType, int monthsActive) {
            BigDecimal price = subtotal;
            if (customerType.equals("GOLD")) price = price.multiply(new BigDecimal("0.90"));
            else if (customerType.equals("SILVER")) price = price.multiply(new BigDecimal("0.95"));
            if (monthsActive > 12) price = price.multiply(new BigDecimal("0.99"));
            return price.setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }

    // ✅ SEALED — the contract, not the mechanism
    interface PricingPolicy {
        BigDecimal price(BigDecimal subtotal, int monthsActive);
    }

    record GoldPricingPolicy() implements PricingPolicy {
        @Override public BigDecimal price(BigDecimal subtotal, int monthsActive) {
            return subtotal.multiply(new BigDecimal("0.90"));
        }
    }

    record SilverPricingPolicy() implements PricingPolicy {
        @Override public BigDecimal price(BigDecimal subtotal, int monthsActive) {
            return subtotal.multiply(new BigDecimal("0.95"));
        }
    }

    static class PriceService {
        private final PricingPolicy policy;
        PriceService(PricingPolicy policy) { this.policy = policy; }
        BigDecimal price(BigDecimal subtotal, int monthsActive) {
            return policy.price(subtotal, monthsActive);
        }
    }

    public static void main(String[] args) {
        BigDecimal subtotal = new BigDecimal("100.00");

        BigDecimal leaky = new LeakyPriceService().calculate(subtotal, "GOLD", 24);
        System.out.println("❌ leaky   = " + leaky + "   (the client knew the tiers and the strings)");

        BigDecimal sealed = new PriceService(new GoldPricingPolicy()).price(subtotal, 24);
        System.out.println("✅ sealed  = " + sealed + "   (the client knew only the contract)");
        System.out.println("✅ and swapping the policy needs NO change to the service");
    }
}
```

**Actual output:**

```text
❌ leaky   = 89.10   (the client knew the tiers and the strings)
✅ sealed  = 90.00   (the client knew only the contract)
✅ and swapping the policy needs NO change to the service
```

**Takeaway:** the leaky version makes the *caller* responsible for knowing the rules;
the sealed version makes the *policy* responsible. The client cannot get the tier
strings wrong because it never sees them.

### Example 5 — Encapsulation and inheritance — the protected-field trap

```java
import java.math.BigDecimal;

public class EncapsulationInheritanceDemo {

    static class Account {
        private BigDecimal balance;                    // ⚠ NOT protected

        Account(BigDecimal opening) { this.balance = opening; }

        // The HOOK — the subclass EXTENDS the rule
        protected void checkCanSet(BigDecimal value) { }

        public final void setBalance(BigDecimal value) {
            checkCanSet(value);                        // ← the rule is extended, not bypassed
            this.balance = value;
        }

        public BigDecimal balance() { return balance; }
    }

    static class StrictAccount extends Account {
        StrictAccount(BigDecimal opening) { super(opening); }

        @Override
        protected void checkCanSet(BigDecimal value) {
            if (value.signum() < 0) {
                throw new IllegalArgumentException("a strict account cannot go negative");
            }
        }
    }

    public static void main(String[] args) {
        StrictAccount a = new StrictAccount(new BigDecimal("100"));
        a.setBalance(new BigDecimal("50"));
        System.out.println("balance = " + a.balance());

        try {
            a.setBalance(new BigDecimal("-1"));
        } catch (IllegalArgumentException e) {
            System.out.println("rejected by the subclass's rule: " + e.getMessage());
        }
    }
}
```

**Actual output:**

```text
balance = 50
rejected by the subclass's rule: a strict account cannot go negative
```

**Takeaway:** the base class owns the state and the mutation; the subclass tightens the
rules through a hook. The invariant survives the inheritance — which is exactly what
`protected` fields destroy.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A fully encapsulated domain object with an invariant

```java
package com.example.banking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A fully encapsulated account.
 *
 * <p>Design notes worth defending in an interview:
 * <ul>
 *   <li>Every field is {@code private}; there is <b>no setter</b>.</li>
 *   <li>The invariant ("a non-overdraft account never has a negative balance")
 *       is established in the constructor and preserved by every mutator.</li>
 *   <li>Mutable state (the ledger) is exposed only as an <b>immutable copy</b>.</li>
 *   <li>The class exposes <b>behaviour</b> ({@code withdraw}, {@code canAfford}),
 *       not data — so the caller cannot assemble an invalid account.</li>
 * </ul>
 */
public class Account {

    public enum Status { ACTIVE, FROZEN, CLOSED }

    private final String id;
    private final Instant openedAt;
    private BigDecimal balance;
    private Status status = Status.ACTIVE;
    private final List<Entry> ledger = new ArrayList<>();

    public record Entry(String type, BigDecimal amount, BigDecimal balanceAfter, Instant at) {}

    public Account(String id, BigDecimal openingBalance, Instant openedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        if (openingBalance == null) {
            throw new IllegalArgumentException("opening balance must not be null");
        }
        if (openingBalance.signum() < 0) {
            throw new IllegalArgumentException("opening balance must not be negative");
        }
        this.balance = openingBalance.setScale(2, RoundingMode.HALF_UP);
    }

    // ── BEHAVIOUR — the public API ────────────────────────────────────
    public void withdraw(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Account " + id + ": balance " + balance + " is less than " + amount);
        }
        balance = balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        ledger.add(new Entry("WITHDRAWAL", amount, balance, Instant.now()));
    }

    public void deposit(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        balance = balance.add(amount).setScale(2, RoundingMode.HALF_UP);
        ledger.add(new Entry("DEPOSIT", amount, balance, Instant.now()));
    }

    public boolean canAfford(BigDecimal amount) {
        return status == Status.ACTIVE
                && amount != null
                && amount.signum() > 0
                && balance.compareTo(amount) >= 0;
    }

    public void freeze() {
        requireActive();
        status = Status.FROZEN;
    }

    public void close() {
        status = Status.CLOSED;
    }

    // ── READ-ONLY ACCESS — no setters, no leaks ─────────────────────
    public String id() { return id; }
    public BigDecimal balance() { return balance; }        // BigDecimal is immutable → safe
    public Status status() { return status; }
    public Instant openedAt() { return openedAt; }
    public List<Entry> ledger() { return List.copyOf(ledger); }   // ✅ an immutable copy

    // ── THE INVARIANT HELPERS ────────────────────────────────────────
    private void requireActive() {
        if (status == Status.FROZEN) {
            throw new AccountFrozenException("Account " + id + " is frozen");
        }
        if (status == Status.CLOSED) {
            throw new IllegalStateException("Account " + id + " is closed");
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    @Override
    public String toString() {
        return "Account[id=" + id + ", balance=" + balance + ", status=" + status + "]";
    }

    // ── DOMAIN EXCEPTIONS ────────────────────────────────────────────
    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) { super(message); }
    }

    public static class AccountFrozenException extends RuntimeException {
        public AccountFrozenException(String message) { super(message); }
    }
}
```

### 6.2 An abstraction that hides the mechanism completely

```java
package com.example.payment;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The ABSTRACTION — the contract the application depends on.
 *
 * <p>Nothing here reveals that the implementation talks HTTP, retries three times,
 * or maps a vendor error code to a domain one.
 */
public interface PaymentGateway {

    /** The stable, persisted identifier of this gateway. */
    String code();

    /** The result of an attempt — never a vendor-specific type. */
    record PaymentResult(boolean success, String reference, String failureCode, String message) {
        public static PaymentResult approved(String reference) {
            return new PaymentResult(true, reference, null, "approved");
        }
        public static PaymentResult declined(String failureCode, String message) {
            return new PaymentResult(false, null, failureCode, message);
        }
    }

    /** Attempt a charge. Never throws for a business failure — that is what the result is for. */
    PaymentResult charge(String customerId, BigDecimal amount, String reference);

    /** Whether this gateway supports the given currency. */
    default boolean supports(String currency) {
        return java.util.Set.of("USD", "EUR", "INR").contains(currency);
    }
}
```

```java
package com.example.payment;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The IMPLEMENTATION — all the "how" lives here and is invisible to the client.
 */
public final class ResilientPaymentGateway implements PaymentGateway {

    private final VendorClient vendor;          // the raw HTTP client
    private final int maxAttempts;
    private final Duration initialBackoff;

    public ResilientPaymentGateway(VendorClient vendor, int maxAttempts, Duration initialBackoff) {
        this.vendor = Objects.requireNonNull(vendor);
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
    }

    @Override
    public String code() { return "VENDOR"; }

    @Override
    public PaymentResult charge(String customerId, BigDecimal amount, String reference) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                VendorResponse response = vendor.charge(customerId, amount, reference);
                if (response.isTransientFailure()) {
                    backoff(attempt);
                    continue;                 // retry
                }
                // ✅ the vendor's error model is MAPPED to the domain's — the client
                //    never sees a VendorException
                return response.isApproved()
                        ? PaymentResult.approved(response.reference())
                        : PaymentResult.declined(response.failureCode(), response.failureMessage());

            } catch (VendorUnavailableException e) {
                backoff(attempt);
            }
        }
        return PaymentResult.declined("GATEWAY_UNAVAILABLE", "All " + maxAttempts + " attempts failed");
    }

    private void backoff(int attempt) {
        long base = initialBackoff.toMillis() * (1L << (attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(base + 1);   // ⚠ jitter
        try {
            Thread.sleep(jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

> 💡 **The abstraction's real value is the error model.** The client sees
> `PaymentResult.declined("CARD_DECLINED", …)` — a *domain* failure code — and never
> a `VendorException`. That mapping is where the abstraction earns its keep: it lets
> the vendor change its API without touching the application.

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — The getter/setter class

```java
class User { private String name; public String getName() { return name; } public void setName(String n) { name = n; } }
// ❌ no invariant, no behaviour — it is a DTO pretending to be a domain object
```

**✅ FIX** — a `record` (with a compact constructor) if it is a data carrier; a class
with behaviour if it is a domain object.

---

### ❌ Pitfall 2 — Returning the live mutable state

```java
public List<String> getTags() { return tags; }   // ❌ the caller can mutate our state
```

**✅ FIX** — `List.copyOf(tags)` (a true snapshot) or `Collections.unmodifiableList(tags)` (a read-only view).

---

### ❌ Pitfall 3 — A `protected` field that a subclass can corrupt

```java
protected BigDecimal balance;   // ❌ any subclass may write it directly
```

**✅ FIX** — a `private` field plus a `protected` **hook** the subclass can override to *extend* the rule.

---

### ❌ Pitfall 4 — A setter that bypasses the invariant

```java
public void setBalance(BigDecimal b) { this.balance = b; }   // ❌ any value allowed
```

**✅ FIX** — remove the setter; expose intent-revealing methods (`withdraw`, `deposit`, `credit`).

---

### ❌ Pitfall 5 — A `Map` or `List` field exposed by reference

```java
public Map<String, Object> attributes() { return attributes; }   // ❌
```

**✅ FIX** — `Map.copyOf(attributes)`.

---

### ❌ Pitfall 6 — An "abstraction" that leaks the mechanism

```java
public BigDecimal calculate(String tier, int months) { … }   // ❌ the client must know the tiers
```

**✅ FIX** — a `PricingPolicy` interface with a `BigDecimal price(Order)` contract.

---

### ❌ Pitfall 7 — Assuming `private` gives thread safety

```java
private BigDecimal balance;   // ⚠ private ≠ thread-safe — a read-modify-write is still a race
```

**✅ FIX** — `synchronized`, an `AtomicReference`, or immutability.

---

### ❌ Pitfall 8 — Returning a mutable array

```java
public byte[] getData() { return data; }   // ❌ the caller can mutate our buffer
```

**✅ FIX** — `data.clone()`.

---

### ❌ Pitfall 9 — An interface with 50 methods (a "leaky" contract)

```java
public interface Repository {
    // 50 methods, of which the caller uses 3
}
```

**✅ FIX** — segregate the interface (ISP) into focused capabilities.

---

### ❌ Pitfall 10 — Confusing the two in a code review

```java
// ❌ "Add a getter for the balance" — that is a DATA leak, not an abstraction
```

**✅ FIX** — ask: *do you want the client to see the data, or to ask the object to do
something?* The first is a getter (and needs a copy); the second is a behaviour method.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is encapsulation?**
**A:** Bundling the data with the methods that operate on it, and **restricting direct
access to the internal state** so that the object cannot be put into an invalid state.
In Java it is enforced by `private` fields plus methods that maintain the class's
invariants. The tell: there is no sequence of API calls that produces an invalid object.

**Q: What is abstraction?**
**A:** Hiding the **implementation** behind a contract — exposing *what* a type does
while concealing *how* it does it. In Java it is expressed with interfaces and abstract
classes. The tell: the client can use the type without knowing the mechanism.

**Q: Are encapsulation and abstraction opposites?**
**A:** No — they solve **different problems** and are orthogonal. Encapsulation protects
the object's **state** from external corruption; abstraction protects the **client**
from implementation details. A `String` is deeply encapsulated *and* deeply abstract.
A DTO with public fields is neither.

**Q: How do you implement encapsulation in Java?**
**A:** Make the fields `private`, expose behaviour through public methods that
validate and maintain the invariant, provide a constructor that establishes the
invariant, and return **copies** (or unmodifiable views) of any mutable state you
expose.

### 🟡 Intermediate

**Q: What is an invariant, and how does it relate to encapsulation?**
**A:** An invariant is a condition that must hold for the object's entire lifetime
("the balance is never negative", "the id never changes"). Encapsulation is the
*mechanism* that enforces it: the constructor establishes it and every mutator
preserves it. The payoff is that the invariant becomes a property of the **type**, not
of the callers' discipline — no caller can produce an invalid object.

**Q: Why should a getter return a copy?**
**A:** Because a getter that returns the **live** mutable state leaks the encapsulation
— the caller can mutate the internal collection, breaking the invariant. Returning
`List.copyOf(x)` gives a true immutable snapshot; returning
`Collections.unmodifiableList(x)` gives a read-only *view* that still reflects later
changes to the source. Use the copy when the object must be genuinely immutable.

**Q: When is encapsulation a bad idea?**
**A:** For a **pure data carrier** — a DTO that crosses a serialisation boundary, a
JPA entity, a record. There, encapsulation buys nothing because the data *is* the API;
the value is in the serialisation, not in the protection. The JDK's own answer to
"immutable data carrier" is the `record`.

**Q: What is a "leaky abstraction", and how do you spot one?**
**A:** An abstraction whose client must know something about the implementation to use
it correctly — the order of calls, an `initialise()` step, a `close()` obligation, or
the internal tier rules. The tell is in the *client's* code: if the client has
"setup" steps or knowledge of the internals, the abstraction is leaking. The fix is
usually to move the orchestration *into* the abstraction (a facade) or to make the
abstraction's contract state its obligations explicitly.

### 🔴 Advanced / Senior

**Q: How do encapsulation and the Liskov Substitution Principle interact?**
**A:** A `protected` field or a `public` setter in a base class lets a subclass
**bypass or weaken** the base class's invariant — so a `PremiumAccount` that inherits
`setBalance` can produce a state the base class's contract forbids. The correct
pattern: the base class owns the state **privately** and the mutation; it exposes a
`protected` **hook** (an overridable validation method) that a subclass can *tighten*
but never *bypass*. The base class's `final` mutator then calls the hook, so every
subclass mutation goes through the rule. This keeps the invariant enforceable *and*
keeps the substitutability contract intact.

**Q: Is a class with only getters and setters encapsulated?**
**A:** Technically it hides the *fields* (so the representation could change), but it
is **encapsulation without a purpose** — because the setters let any caller set any
value, so no invariant is protected and no behaviour is offered. The honest
characterisation: it is a **data container**, and the language now has a better
expression of that — a `record`. The real question in a review is not "is it
encapsulated" but "**is this a domain object or a data carrier?**" A domain object
deserves behaviour and invariants; a data carrier deserves immutability.

**Q: How does immutability relate to encapsulation?**
**A:** Immutability is the **strongest** form of encapsulation: there is no mutation
path at all, so no invalid state is reachable. `String`, `BigDecimal`, `LocalDate` and
`record`s are all immutable. The practical benefits: thread safety without
synchronisation (the JMM's final-field semantics guarantee safe publication),
`Map`/`Set` key safety (the hash never changes), and freedom to cache and share.
The trade-off: an immutable object that must change is *replaced* rather than mutated
— which means an allocation per change, and a design where "changing" a value means
creating a new one. That is a good trade for values, and a poor one for large mutable
aggregates — which is why an entity in a domain model is usually mutable and
encapsulated, while a value is usually immutable.

**Q: How would you encapsulate a type that must interoperate with a framework that needs to set its fields?**
**A:** Keep the domain type **strictly encapsulated** and put the framework
annotations on a *separate* persistence type, with an explicit mapper. A JPA entity
with a public no-arg constructor and field access is, by necessity, not encapsulated
— it is a **data model**, not a domain model. The honest engineering answer is to
acknowledge the two roles rather than pretend one class is both: (1) a persistence
entity (mutable, framework-driven, no invariants) and (2) a domain model (encapsulated,
immutable or behaviour-rich) with a mapper between them. The cost is the mapping
layer; the benefit is that the domain rules cannot be bypassed by a framework writing
a field directly.

### ⚫ Expert / Principal

**Q: "Encapsulation" and "information hiding" are often used interchangeably. Are they the same, and does the distinction matter?**
**A:** They are related but **not** identical, and the distinction is the difference
between a *class-level* and a *module-level* property. **Encapsulation** is about
*this object's* state: the data is bundled with the operations that protect it, and
the invariant is maintained. **Information hiding** (Parnas, 1972) is about *design
modularity*: the client's knowledge of the system's internals is minimised so that a
change behind the boundary does not ripple outward. A class can be perfectly
encapsulated and still leak information (a public field's *type* reveals the
representation; a method's *name and parameters* reveal the mechanism). Conversely, a
module can hide its information perfectly through a coarse API while its internal
classes are freely mutable. The reason the distinction matters is **change
management**: encapsulation protects the *object*'s correctness; information hiding
protects the *system*'s evolvability. The evidence that a design has failed at
information hiding is a **ripple** — changing the storage from a `List` to a `Map`
forced edits in twenty call sites. The evidence that it has failed at encapsulation is
a **bug** — a negative balance, a null field, an inconsistent state. And the design
tool that serves both is the same: **a narrow, intention-revealing API with a
defensive boundary** — a `withdraw(amount)` method rather than a `getBalance()` /
`setBalance()` pair, a `Ledger` interface rather than a `List<Transaction>` field.
The deep lesson: *encapsulation is the local discipline; information hiding is the
architectural goal; and an API that is narrow in both respects is the one that lets
you change the implementation without changing the callers.*

---

## 9. Counter-Questions & Traps

### CQ1. "If `private` hides the fields, isn't the class already encapsulated? Why do you need getters at all?"

- **Naive:** "Encapsulation means using getters and setters."  ← *the most common misconception in OOP*
- **The trap:** The trap is to believe that **getters and setters are encapsulation**. A public getter for a mutable field is a *hole* in the encapsulation, not a feature of it. And a public setter that accepts any value is a hole too. The naive answer describes the **opposite** of encapsulation.
- **Expert answer:** The precise definition: encapsulation means **the object's state can only be changed through operations that maintain its validity**. A `getBalance()` on a `BigDecimal` field is *safe* (the value is immutable — returning it leaks nothing). A `getTags()` returning the live `List` is *unsafe* (the caller can mutate your state). A `setBalance(anything)` is *unsafe* (it bypasses the invariant). So the answer is not "use getters" but "**expose the operations, not the data**" — `withdraw(100)` rather than `getBalance()` + `setBalance()`. The test the interviewer is checking: *can any sequence of public calls put this object into an invalid state?* If yes, the encapsulation is decorative. And the sharper version of the question: a getter that returns a **copy** is fine; a getter that returns a **live reference** is a leak — the difference is not the `get` keyword, it is the *aliasing*.

### CQ2. "You said a getter-setter class is a DTO. But isn't a DTO exactly what an API needs?"

- **Naive:** "Yes, but use a record."  ← *correct, and it misses the distinction between a DTO and a domain object*
- **The trap:** The trap is to collapse "DTO" and "domain object" into one category. They have **opposite** requirements: a DTO wants to be a **transparent data carrier** (easy to serialise, easy to construct, no behaviour), while a domain object wants to be a **behaviour-rich, invariant-holding** thing. Using the same class for both is the mistake — and it is why a codebase ends up with entities that have 40 getters and no rules.
- **Expert answer:** The distinction, with the practical consequences. A **DTO** (a `record`, or a POJO for a framework that needs one) is about *transport*: it should be trivially constructible, trivially serialisable, and carry no rules. A **domain object** is about *behaviour and invariants*: it should be hard to construct incorrectly, and its methods should be the only way to change it. The cost of using one class for both is real: the DTO's mutability leaks into the domain (someone sets `status = PAID` directly, bypassing the transition rules), and the domain's invariants leak into the transport (the serialiser chokes on a computed field). The correct design is two types and a mapper — which is exactly the pattern the "entity vs DTO" split in a layered architecture is for. The nuance worth offering: **an immutable DTO is often good enough** — a `record` with a validating compact constructor gives you the DTO's simplicity with a slice of the domain's safety.

### CQ3. "If `String` is immutable, how do you 'change' it?"

- **Naive:** "You don't — you create a new String."  ← *correct, and it misses the design lesson*
- **The trap:** The trap is to treat immutability as a limitation to work around. The interesting question is what immutability *buys*, and why the JDK chose it for `String`, `BigDecimal`, `LocalDate` and `record`s — and why the *cost* (an allocation per change) is acceptable.
- **Expert answer:** The mechanics: `String` operations return **new** instances (`s + "x"`, `s.substring(1)`, `s.replace('a','b')`). The cost is an allocation and a copy per operation. The benefits, which is why the JDK pays it: (1) **thread safety without synchronisation** — the JMM's final-field semantics guarantee safe publication; (2) **safe as a `Map`/`Set` key** — the hash never changes; (3) **caching** — the string pool is only sound because the values never change; (4) **defensive copying becomes free** — you hand out the reference with no risk. The design lesson: **immutability is the cheapest possible form of thread safety and the strongest possible form of encapsulation**, and the allocation cost is usually irrelevant because (a) the JVM's escape analysis can scalar-replace a short-lived immutable object, and (b) a modern GC handles short-lived garbage extremely cheaply. The rule of thumb: *make a type immutable if it is a **value** (no identity, no lifecycle) and it is small; keep it mutable and encapsulated if it is an **entity** (it has an identity and a lifecycle).* A `String` is a value; a `BankAccount` is an entity — and the difference is exactly why one is immutable and the other is not.

### CQ4. "You said `protected` fields break encapsulation. But `protected` is for subclasses — isn't that the point?"

- **Naive:** "Yes, but subclasses need access."  ← *true, and it misses the alternative that exists*
- **The trap:** The trap is to think `protected` is the only way to let a subclass participate. The whole point of the **protected hook** pattern is that there is a *better* way: the base class owns the state and the mutation, and the subclass extends the *rule* through an overridable validation method. The subclass gets everything it needs — and cannot corrupt anything.
- **Expert answer:** The three levels of access, and what each really grants. `private` — nobody outside the class. `protected` — the subclass gets **direct write access** to the field, which means it can bypass every rule the base class enforces. `public` — everyone. The `protected` field is a *shortcut* that trades the invariant for convenience. The hook pattern is the alternative: `protected void checkCanSet(BigDecimal value) { }` gives the subclass a *say* in the rules without giving it a *way* to bypass them, and the base's mutator can be `final` so the mutation path itself is closed. The general principle: **grant the subclass the ability to *extend* the policy, not to *circumvent* it.** And there is a second reason `protected` fields are dangerous: they are part of the class's **public contract** for the rest of time — you cannot later make them `private` without breaking every subclass, so a `protected` field is a permanent design commitment.

### CQ5. "Does a `record` have encapsulation? It has accessors for everything."

- **Naive:** "Yes — the fields are private and final."  ← *true, and it misses the difference*
- **The trap:** The trap is to treat a record's generated accessors as equivalent to a
  getter-setter class. They are **not** the same: a record has **no setters**, so the
  state cannot change after construction — that is *stronger* encapsulation than a
  getter-only class, and much stronger than a getter-setter class. The subtlety is
  that a record's encapsulation is only as deep as its **components**.
- **Expert answer:** The precise difference. A **getter-setter class**: mutable state, settable to anything, no invariant. A **record**: immutable state, no setter, a validating compact constructor if you want one — so the invalid states are *unconstructible*. A record with a compact constructor is therefore *more* encapsulated than a classic domain object, because the classic one at least permits mutation (and must defend it). The limitation to name: **a record is only as deeply immutable as its components.** `record Order(String id, List<Item> items)` has an immutable *reference* to a mutable list — so the record is not deeply immutable, and `items()` must return a copy (`List.copyOf`) for the guarantee to hold. So the answer: a record gives you **reference immutability** and, with a compact constructor, **construction-time validation** — and you must still copy the mutable components. That is the same "shallow vs deep immutability" issue as in the [Enums chapter](../13-Enums/01-Enum-Deep-Dive.md), and it is worth being able to explain in both places.

### CQ6. "You said the abstraction hides the mechanism. But if the client has to know the order of calls, isn't that still a leak?"

- **Naive:** "Yes, so the abstraction is leaky."  ← *correct, and it misses where the fix belongs*
- **The trap:** The trap is to blame the abstraction when the problem is usually in the
  **design of the operations**. A "leaky abstraction" caused by an ordering requirement is
  almost always a signal that the operations are **not independently meaningful** — and
  the fix is usually to make them so, not to document the order.
- **Expert answer:** The diagnostic. If the client must call `initialise()` before
  `process()`, the real problem is that `process()` is not a valid operation on its
  own — so either (a) `initialise()` should be a *constructor* concern (and the
  construction should produce an object that is already valid), (b) the two operations
  should be **one** operation (a facade that does both), or (c) the API should return a
  *builder* that makes the order unobservable (`builder.initialise().process()` — the
  order is enforced by the type, not by the documentation). The principle: **an
  ordering requirement is a sign that the abstraction's operations are not
  independently meaningful.** The exceptions — where an order *is* intrinsic (a
  stream, a transaction, a `Result` that must be closed) — are best expressed by the
  *type* making the misuse impossible (`AutoCloseable` + try-with-resources), not by a
  javadoc note. And the honest admission: some abstractions genuinely are leaky, and
  the right response is to *document the leak explicitly* (as the JDK does for
  `Iterator.remove`, `Comparable`, and `AutoCloseable`) so that the client knows
  exactly what it is depending on.

### CQ7. "Doesn't making everything private and adding methods just move the complexity to the caller?"

- **Naive:** "Yes, that's a real cost."  ← *true, and it misses the accounting*
- **The trap:** The trap is to count the *lines* rather than the *coupling*. The
  complexity is not moved to the caller — it is **concentrated** into the class that
  owns the data. The caller still does one call; it just calls a method that also
  guarantees the result is sane.
- **Expert answer:** The accounting. With a public field, the caller writes
  `if (account.getBalance() > 0) { … }` — and *that check is duplicated at every call
  site*, and it is **not** the same check in each one (one might use `>=`, another
  `> 0`, a third might forget). With encapsulation, the caller writes `account.withdraw(100)`,
  and the check exists **once**, in the class that owns the balance. So the complexity
  did not move — it was **deduplicated**, and the *policy* moved from the call sites
  to the owner of the data, where it belongs. The remaining cost is real but small: the
  caller cannot *compose* the check with its own logic in a new way (it can no longer
  ask "would this succeed?" without a dry-run method), and the class grows. That cost
  is why **intent-revealing query methods** matter (`canAfford(amount)`) — they give
  the caller back the ability to ask without acting. The general principle: *encapsulation
  moves the "what" of the check to where the data lives, so the check cannot drift
  between call sites.* That is not a move of complexity; it is the elimination of a
  whole class of duplication bug.

### CQ8. "If `Optional` is the answer for 'maybe no value', why can't I use it for a field?"

- **Naive:** "Because the Javadoc says so."  ← *true, and it misses the reasons*
- **The trap:** The trap is to treat it as a style rule. The reasons are **technical**,
  and they are worth knowing because they also explain why `Optional` in a *parameter*
  is a smell.
- **Expert answer:** As a **field**: (1) `Optional` is **not `Serializable`**, so a
  DTO with an `Optional` field needs custom serialisation — and most frameworks
  (Jackson, JPA) need special support. (2) `Optional` does **not** implement
  `equals`/`hashCode`, so two `Optional`s holding equal values are **not equal** — an
  `Optional` field breaks the value semantics of the enclosing object and every
  `Set`/`Map` that contains it. (3) It adds a layer of indirection for no benefit —
  the field can simply be `null`, and the *getter* can return `Optional.ofNullable(x)`
  to express the absence in the API. As a **parameter**: it is a **null check in
  disguise** — the caller had to handle the null before wrapping it, and the callee
  unwraps it immediately. The correct use is exactly where the Javadoc says: as a
  **return type** that says "this may have no value", and in **stream pipelines**
  (`findFirst`, `min`, `max`), where the "maybe absent" is intrinsic to the operation.

### CQ9. "How do you know when a class has too many responsibilities?"

- **Naive:** "When it gets too big."  ← *a symptom, not the definition*
- **The trap:** The trap is to use a *size* metric. The definition of "too many
  responsibilities" is about **reasons to change** — and a class can be 500 lines with
  one reason to change (a long algorithm), and 60 lines with six (a mini god-object).
- **Expert answer:** The **actor test**: *name the people or teams who would ask you
  to change this class.* Each distinct answer is a distinct responsibility. The
  **change-friction test**: if the class is changed often, and the changes are
  unrelated to each other, it has several responsibilities. The **invariants test**:
  if the class maintains several unrelated invariants, it should be several classes —
  each invariant belongs with the data it constrains. And the practical refactoring
  signal: if a method needs a comment saying "and also does X", the class is doing
  two jobs. The corresponding move is not to split by *method* (which produces a class
  explosion) but to split by *reason to change* — and to extract **collaborators**
  (composition) rather than to create a shallow hierarchy of empty classes.

### CQ10. "Is `final` on a field the same as encapsulation?"

- **Naive:** "Yes — it prevents modification."  ← *true about the reference, and it misses the distinction*
- **The trap:** The trap is to think `final` guarantees the *object* is immutable. It
  guarantees only that the **reference** cannot be reassigned. If the field is a
  `List`, the list is still mutable.
- **Expert answer:** The two levels, with the terms: `final` gives you **reference
  immutability**; **deep immutability** requires that every object reachable from the
  field is also immutable. A `final List<String> tags` is *not* deeply immutable — you
  can still `tags.add("x")`. To get deep immutability you must (a) use an immutable
  collection type (`List.of`, `List.copyOf`), and/or (b) copy defensively at
  construction. And the connection to encapsulation: `final` is the *mechanism* that
  makes the **safe-publication** guarantee work (the JMM's final-field semantics) —
  so `final` is a *thread-safety* tool as much as an immutability one. The practical
  checklist for a truly immutable type: every field `final`; every field's type
  deeply immutable (or defensively copied in the constructor); no mutable state
  reachable; the class (or record) is `final` so it cannot be subclassed to add
  mutability. Get all four, and you have a type that is safe to share, safe to cache,
  and safe to use as a map key.

---

## 10. Rapid-Fire MCQs

**Q1.** Encapsulation is primarily about hiding…
- A) The implementation  B) **The data**  C) The class hierarchy  D) The algorithm

**Q2.** Abstraction is primarily about hiding…
- A) The data  B) The implementation  C) The fields  D) The dependency

**Q3.** Are encapsulation and abstraction opposites?
- A) Yes  B) **No — they are orthogonal**  C) Only in Java  C++  D) Only for interfaces

**Q4.** The *test* for correct encapsulation is…
- A) All fields are private  B) **No API sequence can create an invalid state**  C) There are getters  D) The class is `final`

**Q5.** An *invariant* is…
- A) A constant  B) **A condition that holds for the object's whole lifetime**  C) A `final` field  D) A hash code

**Q6.** Where is an invariant established?
- A) In the getters  B) **In the constructor**  C) In the `equals` method  D) In the `toString` method

**Q7.** A getter that returns the live internal `List`…
- A) Is fine  B) **Leaks the internal state**  C) Is a compile error  D) Makes the class final

**Q8.** `List.copyOf(x)` vs `Collections.unmodifiableList(x)`?
- A) The same  B) **`copyOf` is a copy; `unmodifiableList` is a view**  C) The reverse  D) Both are views

**Q9.** A getter-setter class with no invariants is really…
- A) Well encapsulated  B) **A data carrier — use a `record`**  C) An abstract class  D) A singleton

**Q10.** A `protected` field is dangerous because…
- A) It is slower  B) **A subclass can bypass the base class's invariants**  C) It is deprecated  D) It cannot be `final`

**Q11.** The `protected` hook pattern (`checkCanSet`) lets a subclass…
- A) Write the field directly  B) **Extend the validation rule without bypassing it**  C) Skip validation  D) Change the type

**Q12.** A `record` provides…
- A) Mutable state with setters  B) **Immutable state, generated accessors and `equals`/`hashCode`**  C) Inheritance  D) A no-arg constructor

**Q13.** A record is deeply immutable?
- A) Yes  B) **No — only if its components are**  C) Only if it is `sealed`  D) Only for primitives

**Q14.** `final` on a field guarantees…
- A) Deep immutability  B) **That the reference cannot be reassigned**  C) Thread safety  D) All of the above

**Q15.** `String` is immutable mainly so that it can be…
- A) Serialised  B) **Shared safely, cached, and used as a map key**  C) Finalised  D) Compared

**Q16.** A "leaky abstraction" is one where the client must know…
- A) The source code  B) **Implementation details to use it correctly**  C) The field names  D) The class hierarchy

**Q17.** The best fix for a leaky abstraction caused by an ordering requirement is to…
- A) Document the order  B) **Make the operations independently meaningful**  C) Add more getters  D) Make the class `final`

**Q18.** `Optional` as a field is discouraged mainly because it is…
- A) Slower  B) **Not `Serializable` and has no `equals`/`hashCode`**  C) `final`  D) Nullable

**Q19.** The number of "reasons to change" is best determined by…
- A) The number of methods  B) **The number of distinct actors/teams**  C) The line count  D) The number of fields

**Q20.** The strongest form of encapsulation is…
- A) `private` fields  B) Getters and setters  C) **Immutability**  D) `final` methods

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | Encapsulation protects the **data**; abstraction protects the **implementation**. |
| 2 | **B** | Abstraction hides the mechanism behind a contract. |
| 3 | **B** | They are orthogonal — a `String` is deeply both. |
| 4 | **B** | The real test: no sequence of API calls can create an invalid state. |
| 5 | **B** | A condition that must hold for the object's entire lifetime. |
| 6 | **B** | The constructor establishes it; every mutator preserves it. |
| 7 | **B** | The caller can mutate your internal collection and break the invariant. |
| 8 | **B** | `copyOf` is an immutable snapshot; `unmodifiableList` is a read-only *view*. |
| 9 | **B** | It has no behaviour and no invariant — it is a DTO. Use a `record`. |
| 10 | **B** | Direct write access lets a subclass bypass the base class's rules. |
| 11 | **B** | The base owns the mutation; the subclass extends the *policy*. |
| 12 | **B** | Final fields, a canonical constructor, accessors, and generated `equals`/`hashCode`. |
| 13 | **B** | Only as deep as its components — copy the mutable ones. |
| 14 | **B** | `final` fixes the *reference*; the referenced object may still be mutable. |
| 15 | **B** | Immutability makes sharing, pooling and map-key usage safe. |
| 16 | **B** | The client must know implementation details to use it correctly. |
| 17 | **B** | An ordering requirement means the operations are not independently meaningful. |
| 18 | **B** | It is not `Serializable` and has no `equals`/`hashCode` — it breaks value semantics. |
| 19 | **B** | Each distinct actor is a distinct reason to change. |
| 20 | **C** | Immutability removes the mutation path entirely — the invalid states do not exist. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    ENCAPSULATION vs ABSTRACTION                         │
├──────────────────────┬───────────────────────┬──────────────────────────┤
│                      │ ENCAPSULATION         │ ABSTRACTION              │
├──────────────────────┼───────────────────────┼──────────────────────────┤
│ Hides                │ the DATA              │ the IMPLEMENTATION       │
│ Question answered    │ "how do I keep this    │ "what does this thing   │
│                      │  object valid?"       │  do?"                    │
│ Beneficiary          │ the class author      │ the client               │
│ Mechanism            │ private fields,       │ interfaces, abstract     │
│                      │ guarded mutators,     │ classes, sealed types    │
│                      │ defensive copies      │                          │
│ Failure signature    │ an invalid state is   │ the client must know     │
│                      │ reachable             │ "how"                    │
│ The tell             │ "can a caller break   │ "must the caller know    │
│                      │  this?"               │  the internals?"         │
├──────────────────────┴───────────────────────┴──────────────────────────┤
│  THE RULE:  encapsulate the DATA, abstract the BEHAVIOUR               │
│  THE TELL:  getters+setters with no invariants = a DTO → use a record   │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE ENCAPSULATION CHECKLIST                           │
│  ☐ Fields are private (and final where the reference must not change)  │
│  ☐ The constructor ESTABLISHES the invariant                           │
│  ☐ Every mutator PRESERVES the invariant                              │
│  ☐ Mutable state is exposed only as a COPY or an unmodifiable view      │
│  ☐ A subclass extends the POLICY via a protected hook — never a field  │
│  ☐ There is no public setter that accepts any value                    │
│  ☐ The API exposes BEHAVIOUR ("withdraw"), not DATA ("get/setBalance") │
│                                                                          │
│  ⚠ IF ALL OF THESE ARE FALSE, YOU HAVE A DTO — USE A `record`.        │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The encapsulation smells in a code review

| Smell | Review comment | Fix |
|---|---|---|
| Public fields | "Anyone can set an invalid value." | `private` + a behaviour method |
| A getter returning a live collection | "This leaks our internal state." | `List.copyOf(x)` |
| A public setter on a domain object | "This bypasses the invariant." | Remove it; expose `withdraw`/`deposit` |
| A `protected` field | "A subclass can corrupt the invariant." | `private` + a protected hook |
| A getter-setter class | "Is this a domain object or a DTO?" | A `record` if a DTO; behaviour if a domain object |
| A `final` field holding a `List` | "The list is still mutable." | `List.copyOf` in the constructor |
| An `Optional` field | "It is not `Serializable` and has no `equals`." | A nullable field; `Optional` on the getter |

### The abstraction smells

| Smell | Review comment | Fix |
|---|---|---|
| A parameter list exposing internals | "The caller must know the tiers." | Pass a domain object |
| A client calling `init()` then `process()` | "These are not independently meaningful." | Combine them, or return a builder |
| A `Map<String, Object>` parameter | "The caller must know the keys." | A typed parameter |
| An interface with 50 methods | "Clients depend on methods they do not use." | Segregate it (ISP) |
| The client catching a vendor exception | "The abstraction leaks the vendor." | Map it to a domain error in the implementation |

### Production patterns

| Pattern | Example |
|---|---|
| **Immutable value** | `record Money(BigDecimal amount, String currency)` with a compact constructor |
| **Encapsulated entity** | `Account` with private fields, guarded mutators and a ledger |
| **Defensive copy on the way out** | `List.copyOf(ledger)` |
| **Defensive copy on the way in** | `items = List.copyOf(items)` in the constructor |
| **Domain error mapping** | The implementation maps a vendor error to `PaymentResult.declined(...)` |
| **The protected hook** | `protected void checkCanSet(BigDecimal v)` for subclass rules |
| **The intent-revealing query** | `canAfford(amount)` so the client can ask without acting |

### The checklist for a new domain class

```text
  ☐ What is the INVARIANT? Write it as a sentence.
  ☐ Where is it ESTABLISHED? (the constructor)
  ☐ Where is it PRESERVED? (every mutator)
  ☐ Is the state MUTABLE? If not, use a `record`.
  ☐ What MUTABLE state do I expose? Copy it.
  ☐ What BEHAVIOUR does this class own? That is the public API.
  ☐ Do I need a getter at all? Prefer a behaviour method.
  ☐ If a subclass may extend the rules → a protected HOOK, not a field.
```

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The sibling question (interface vs abstract class) | [06 · Interface vs Abstract Class](06-OOP-Interface-vs-Abstract-Class.md) |
| Inheritance and substitutability | [04 · Inheritance & the Diamond Problem](04-OOP-Inheritance-Types-and-Diamond-Problem.md) |
| Polymorphism and dispatch | [05 · Polymorphism Deep Dive](05-OOP-Polymorphism-Deep-Dive.md) |
| Records, immutability, and `java.time` | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| SOLID and dependency direction | [10 · SOLID Principles](../10-SOLID-Principles/01-SOLID-Principles.md) |
| The strategy pattern (an abstraction in the wild) | [09 · Design Patterns](../09-Design-Patterns/01-Design-Patterns.md) |

---

**Next Chapter:** → [04 · Inheritance Types & the Diamond Problem](04-OOP-Inheritance-Types-and-Diamond-Problem.md)
**Previous Chapter:** → [02 · Access Modifiers](02-OOP-Access-Modifiers.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
