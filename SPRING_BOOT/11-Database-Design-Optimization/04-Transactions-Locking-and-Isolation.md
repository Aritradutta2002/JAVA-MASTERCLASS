# 04 · Transactions, Locking & Isolation

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★★ ("Explain isolation levels" — and the follow-up kills people)
> **Prerequisite:** [03 · Query Optimisation & Execution Plans](./03-Query-Optimization-and-Execution-Plans.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Four Phenomena](#2-mental-model--the-four-phenomena)
- [3. Isolation Levels, Precisely](#3-isolation-levels-precisely)
- [4. MVCC — How Postgres Actually Does It](#4-mvcc--how-postgres-actually-does-it)
- [5. Locking](#5-locking)
- [6. Deadlocks](#6-deadlocks)
- [7. Optimistic vs Pessimistic Concurrency](#7-optimistic-vs-pessimistic-concurrency)
- [8. Spring `@Transactional` Semantics](#8-spring-transactional-semantics)
- [9. Transaction Anti-Patterns](#9-transaction-anti-patterns)
- [10. Code Examples](#10-code-examples)
- [11. Common Mistakes](#11-common-mistakes)
- [12. Interview Questions & Model Answers](#12-interview-questions--model-answers)
- [13. Self-Check & Revision Card](#13-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> **ACID** is atomicity (all or nothing), consistency (constraints hold), isolation (concurrent
> transactions don't see each other's uncommitted work), durability (committed survives). The
> four phenomena isolation defends against: **dirty read**, **non-repeatable read**, **phantom
> read**, **lost update**.
>
> Postgres supports **Read Committed** (default), **Repeatable Read** (snapshot isolation, and
> it detects write conflicts), and **Serializable** (SSI — aborts one of a conflicting pair).
> Postgres has **no dirty reads at any level**, unlike the SQL standard's table.
>
> **MVCC** means readers don't block writers: each transaction sees a snapshot; `UPDATE`
> writes a new row version and marks the old one dead. `VACUUM` cleans the dead versions —
> a long-lived transaction prevents that and causes bloat.
>
> **Lost updates** are the real-world killer. Fix with **optimistic locking** (`@Version`),
> **pessimistic locking** (`SELECT ... FOR UPDATE`), or an **atomic SQL update**
> (`SET balance = balance - 100 WHERE balance >= 100`). The atomic update is the cheapest and
> most underused.

---

## 1. What & Why

Concurrency bugs are the most expensive class of bug: they're invisible in tests, appear under
load, corrupt data silently, and are almost impossible to reproduce. The database's transaction
machinery exists to make them the database's problem instead of yours — **but only if you use
it correctly.**

The failures people actually ship:

```java
// 1. LOST UPDATE — the classic
Account a = repo.find(id);          // balance 100
a.setBalance(a.getBalance() - 100); // 0
repo.save(a);                       // two concurrent requests both write 0, not -100

// 2. READ-MODIFY-WRITE across a network call
Stock s = repo.find(sku);           // 5
paymentClient.charge(...);          // 200 ms of "someone else may have bought it"
s.setQuantity(s.getQuantity() - 1);
repo.save(s);

// 3. CHECK-THEN-ACT
if (!repo.existsByEmail(email)) {   // both requests pass
    repo.save(new User(email));      // duplicate created
}

// 4. LONG TRANSACTION holding locks during an HTTP call
@Transactional
void process() { repo.update(...); otherService.call(); }   // locks held for seconds
```

Every one of these is fixed by a database-level mechanism, not by more Java.

---

## 2. Mental Model — The Four Phenomena

| Phenomenon | What happens | Example |
|---|---|---|
| **Dirty read** | read another transaction's **uncommitted** data | you see a balance that's about to be rolled back |
| **Non-repeatable read** | the same row read twice returns different values | total computed from two different snapshots |
| **Phantom read** | the same **query** returns different row sets | `COUNT(*)` changes mid-transaction |
| **Lost update** | two transactions read, both write, one overwrites the other | two withdrawals both succeed from one balance |

```
   T1                    T2
   BEGIN
   read x = 100
                        BEGIN
                        read x = 100
   x = x - 50
   write x = 50
                        x = x - 30
                        write x = 70
   COMMIT
                        COMMIT
   final balance = 70     ← 50 + 30 = 80 was deducted, 30 was lost
```

**The SQL standard's isolation table** (memorise the shape, then note Postgres's deviations):

| Level | Dirty read | Non-repeatable | Phantom | Lost update |
|---|---|---|---|---|
| Read Uncommitted | possible | possible | possible | possible |
| Read Committed | **not in PG** | possible | possible | possible |
| Repeatable Read | no | no | possible (**not in PG**) | possible (**not in PG**) |
| Serializable | no | no | no | no |

**Postgres's deviations are the interesting part:**
- It **never** allows dirty reads — `READ UNCOMMITTED` is silently treated as `READ COMMITTED`.
- Its **Repeatable Read is snapshot isolation**, which also prevents **phantoms**.
- Its **Serializable** is SSI (serializable snapshot isolation) — it detects conflicts and
  aborts, so you must be prepared to retry.

Saying "in Postgres, Repeatable Read also prevents phantoms because it's snapshot isolation"
is a strong, specific signal.

---

## 3. Isolation Levels, Precisely

### 3.1 Read Committed (default)

Each **statement** sees a snapshot taken at its start. Two `SELECT`s in one transaction can see
different data.

```sql
BEGIN;
SELECT balance FROM accounts WHERE id = 1;   -- 100 (snapshot A)
-- another tx commits balance = 70
SELECT balance FROM accounts WHERE id = 1;   -- 70 (snapshot B)
COMMIT;
```

- ✅ No dirty reads, no reader/writer blocking.
- ❌ Non-repeatable reads, phantoms, lost updates.
- **What most applications actually run.** Most bugs are lost updates under this level.

### 3.2 Repeatable Read (snapshot isolation)

The transaction sees **one snapshot** for its entire duration.

```sql
BEGIN ISOLATION LEVEL REPEATABLE READ;
SELECT balance FROM accounts WHERE id = 1;   -- 100
-- another tx commits 70
SELECT balance FROM accounts WHERE id = 1;   -- still 100
UPDATE accounts SET balance = 50 WHERE id = 1;
-- ERROR: could not serialize access due to concurrent update
COMMIT;
```

- ✅ No non-repeatable reads, **no phantoms** (snapshot), and Postgres **detects** the
  write-write conflict and raises `40001` instead of losing the update.
- ❌ You must **retry** on `40001`. If you don't handle it, you get 500s under contention.
- Used for reports that must be internally consistent, and for read-modify-write where you can't
  express it as a single atomic statement.

### 3.3 Serializable (SSI)

Adds predicate-level conflict tracking (Serializable Snapshot Isolation). It aborts a
transaction when the *combination* of reads and writes could produce a non-serialisable result.

- ✅ Real serialisability — the standard for correctness-critical logic.
- ❌ **More aborts**, especially with write skew patterns; requires retry logic and no long
  transactions. Adds some overhead.
- Use for: invariant enforcement across multiple rows (accounting transfers, "sum of allocations
  must equal total"), booking systems.

### 3.4 Write skew — the pattern people can't name

```
Doctor on call: at least 2 must be on call.
T1: count on-call = 2 → "safe to go off call" → set self off-call
T2: count on-call = 2 → "safe to go off call" → set self off-call
Both commit. Now 0 doctors on call.
```

Each transaction is individually correct; the **combination** violates the invariant. Snapshot
isolation **allows** this; only **Serializable** (or `SELECT ... FOR UPDATE` on the set) prevents
it. Being able to name write skew is a strong senior signal.

### 3.5 Choosing

| Need | Level |
|---|---|
| Normal CRUD | **Read Committed** (default) |
| Report that must be internally consistent | Repeatable Read |
| Read-modify-write on one row | Read Committed + atomic SQL / optimistic lock |
| Cross-row invariant (money, allocation, booking) | Serializable, or explicit locks + retry |
| "Just make it correct" for a critical path | Serializable + retry |

---

## 4. MVCC — How Postgres Actually Does It

```
   Row versions in the heap:

   xmin=100  balance=100  xmax=0     ← visible to tx >= 100
   xmin=120  balance=70   xmax=0     ← visible to tx >= 120

   T1 (snapshot at 110) sees 100.
   T2 (snapshot at 130) sees 70.
   Neither blocks the other. No lock required to read.
```

- Each `UPDATE` writes a **new row version**; the old one is marked dead (`xmax` set).
- `DELETE` marks dead; it isn't removed until `VACUUM`.
- **Readers never block writers and writers never block readers** — the headline benefit.
- **`VACUUM`** reclaims dead tuples. It cannot remove versions still visible to an open
  transaction (`xmin` horizon).

### Consequences you must know

| Consequence | Detail |
|---|---|
| **Bloat** | a long-running transaction (or a forgotten `idle in transaction`) prevents vacuum → table and index grow |
| **`UPDATE` is `INSERT`+`DELETE`** | it doubles the write volume; heavy updates on the same row cause churn |
| **`fillfactor`** | leave room on the page for HOT updates (e.g. 80 for hot tables) |
| **Counts are approximate** | `reltuples` is an estimate; `COUNT(*)` scans |
| **`SELECT ... FOR UPDATE`** | the only way to lock a row for a later update under Read Committed |
| **Monitoring** | `pg_stat_activity.state = 'idle in transaction'` is the #1 bloat cause |

### The long-transaction rule

> *A transaction should last milliseconds, not seconds. Every second it lives, it holds a
> snapshot, blocks vacuum, and (if it takes locks) blocks other writers.*

Practical rule: **never make an HTTP call inside a transaction.**

---

## 5. Locking

### 5.1 Row-level locks

```sql
SELECT * FROM accounts WHERE id = 1 FOR UPDATE;             -- exclusive row lock
SELECT * FROM accounts WHERE id = 1 FOR NO KEY UPDATE;      -- doesn't block FK checks
SELECT * FROM accounts WHERE id = 1 FOR SHARE;              -- shared, blocks writers
SELECT * FROM accounts WHERE id = 1 FOR UPDATE SKIP LOCKED; -- work queues
SELECT * FROM accounts WHERE id = 1 FOR UPDATE NOWAIT;      -- fail fast instead of waiting
```

- `FOR UPDATE` **must be inside a transaction** or it's released immediately.
- `SKIP LOCKED` is the canonical work-queue pattern: multiple workers grab different rows
  without blocking.
- `NOWAIT` converts a lock wait into an immediate error — useful for user-facing paths where
  waiting 5 s is worse than "try again".

### 5.2 Table-level locks

| Lock | Blocks |
|---|---|
| `ACCESS SHARE` | only `ACCESS EXCLUSIVE` (i.e. `ALTER`, `DROP`) |
| `ROW SHARE` | `EXCLUSIVE` |
| `ROW EXCLUSIVE` | (taken by `INSERT`/`UPDATE`/`DELETE`) |
| `SHARE` | writers |
| `ACCESS EXCLUSIVE` | **everything** — taken by `ALTER TABLE`, `TRUNCATE` |

**The classic outage:** `ALTER TABLE ... ADD COLUMN ... DEFAULT` (old PG) or a long
`CREATE INDEX` takes `ACCESS EXCLUSIVE` and queues behind a long-running query; every
subsequent query queues behind the `ALTER`. Everything stops. Monitoring `pg_locks` and setting
`lock_timeout` prevents it.

```sql
SET lock_timeout = '2s';   -- fail the DDL instead of stacking the whole table behind it
```

### 5.3 Advisory locks

```sql
SELECT pg_try_advisory_lock(hashtext('nightly-report'));
```

Application-level locks with the database as the coordinator — no table needed, released on
disconnect. Perfect for "only one instance should run this job".

---

## 6. Deadlocks

```
T1: LOCK A → LOCK B
T2: LOCK B → LOCK A
        ↑ circular wait
```

Postgres detects it after `deadlock_timeout` (1 s default) and aborts one transaction with
`40P01`. You must **retry**.

### Prevention

1. **Consistent lock ordering** — always lock rows/tables in the same order (e.g. by id
   ascending):

```java
// ❌ arbitrary order → deadlocks
for (Long id : ids) repo.findByIdForUpdate(id);

// ✅ deterministic order
for (Long id : ids.stream().sorted().toList()) repo.findByIdForUpdate(id);
```

2. **Short transactions** — the window shrinks.
3. **One statement instead of two** — atomic SQL avoids holding a lock across round trips.
4. **`ORDER BY` in a bulk `UPDATE ... WHERE id IN (...)`** — Postgres doesn't guarantee order,
   so explicit locking with a sorted query is safer.
5. **Retry with backoff** on `40001`/`40P01`.

### Retry template

```java
@Retryable(retryFor = {CannotAcquireLockException.class, PessimisticLockingFailureException.class},
           maxAttempts = 3, backoff = @Backoff(delay = 50, multiplier = 2, random = true))
@Transactional
public void transfer(Long from, Long to, long cents) { ... }
```

Note: the retry must be **outside** the transaction, or you retry inside a rolled-back
transaction and fail again. Put `@Retryable` on a method that *calls* the transactional method.

---

## 7. Optimistic vs Pessimistic Concurrency

### 7.1 Optimistic (version column)

```sql
UPDATE accounts SET balance = ?, version = version + 1
WHERE id = ? AND version = ?;
-- 0 rows updated ⇒ someone else changed it ⇒ retry or 409
```

```java
@Entity
class Account {
    @Version private long version;
}
```

```java
@Transactional
public void withdraw(Long id, BigDecimal amount) {
    for (int attempt = 0; attempt < 3; attempt++) {
        try {
            var a = repo.findById(id).orElseThrow();
            if (a.getBalance().compareTo(amount) < 0) throw new InsufficientFunds();
            a.setBalance(a.getBalance().subtract(amount));
            repo.saveAndFlush(a);     // throws OptimisticLockException on conflict
            return;
        } catch (OptimisticLockingFailureException e) { /* retry */ }
    }
    throw new ConflictException("too much contention");
}
```

- ✅ No locks held, scales for read-heavy workloads, no deadlocks.
- ❌ Under high contention, retries pile up; needs retry logic and a user-visible `409`.

### 7.2 Pessimistic (`SELECT ... FOR UPDATE`)

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select a from Account a where a.id = :id")
Optional<Account> findForUpdate(Long id);
```

- ✅ Deterministic — no retries, no wasted work.
- ❌ Holds locks across the transaction (keep it short!), risks deadlock and throughput collapse
  on hot rows.

### 7.3 Atomic SQL — the best option when it fits

```sql
UPDATE accounts SET balance = balance - 100
WHERE id = 42 AND balance >= 100;      -- 0 rows = insufficient funds
```

```java
@Modifying
@Query("update Account a set a.balance = a.balance - :amt where a.id = :id and a.balance >= :amt")
int withdraw(@Param("id") Long id, @Param("amt") BigDecimal amt);   // returns 0 or 1
```

- ✅ **One round trip, no read, no lock held, no retry loop, correct under any concurrency.**
- ✅ The `WHERE` clause *is* the precondition — the database arbitrates.
- ❌ Can't express complex multi-step logic; needs the invariant in one statement.

**Say this in an interview:** "For balance-style updates I don't read-then-write at all; I push
the precondition into the `UPDATE` and check the affected row count."

### 7.4 Comparison

| | Optimistic | Pessimistic | Atomic SQL |
|---|---|---|---|
| Locks held | none | yes (row) | brief row lock |
| Retry needed | yes | no | no |
| Contention | degrades (retries) | degrades (waits) | **best** |
| Complex logic | yes | yes | no |
| Deadlock risk | none | yes | low |
| Best for | read-heavy, low contention | complex invariants, high value | single-row arithmetic |

---

## 8. Spring `@Transactional` Semantics

### 8.1 The things that silently don't work

| Situation | Behaviour | Why |
|---|---|---|
| **Self-invocation** (`this.method()`) | **no transaction** | proxy is bypassed |
| **Private / final method** | no transaction (CGLIB can't override final; private not proxied) | proxy-based AOP |
| **Exception swallowed** | transaction still commits | rollback needs the exception to propagate |
| **Checked exception thrown** | **commit** | rollback only for `RuntimeException`/`Error` by default |
| **`@Transactional` on a `@Bean` return in the same config** | may not proxy | proxy creation order |
| **New thread inside the method** | different transaction (or none) | `ThreadLocal`-bound |
| **`@Transactional` on a `@RestController`** | works but bad layering | transaction should start at the service |

```java
// ✅ rollback for checked exceptions too
@Transactional(rollbackFor = Exception.class)
```

### 8.2 Propagation

| Propagation | Behaviour |
|---|---|
| `REQUIRED` (default) | join the existing transaction, or start one |
| `REQUIRES_NEW` | suspend the current one, start a separate one (its own commit/rollback) |
| `NESTED` | savepoint within the current transaction |
| `SUPPORTS` | join if present, otherwise run without a transaction |
| `NOT_SUPPORTED` | suspend any transaction |
| `MANDATORY` | fail if no transaction |
| `NEVER` | fail if a transaction exists |

`REQUIRES_NEW` is how you write an audit log that survives the outer rollback — but it needs a
**second connection**, so it can deadlock the pool if used carelessly.

### 8.3 Read-only and timeouts

```java
@Transactional(readOnly = true)   // hint: no dirty checking, allows replica routing in some setups
@Transactional(timeout = 5)       // seconds; a runaway query is killed
```

`readOnly = true` is a genuine optimisation for Hibernate (skips dirty checking/flush) and a
correctness signal. Set it on every read method.

### 8.4 Isolation in Spring

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
```

Works on the transaction's connection. Changing isolation mid-transaction (nested with a
different level) is a warning sign of a design problem.

### 8.5 Where the transaction should start and end

```
controller → service (@Transactional) → repository → DB
```

- **Never** open a transaction in the controller (you'll wrap view rendering).
- **Never** hold one across an HTTP call, a message publish, or user think-time.
- If you need "commit then publish", use the outbox
  ([Topic 09 ch 04](../09-Messaging-Systems/04-Reliable-Messaging-and-Event-Driven-Architecture.md)).

---

## 9. Transaction Anti-Patterns

| Anti-pattern | Problem | Fix |
|---|---|---|
| HTTP call inside `@Transactional` | locks held for the call's duration | move the call out, use outbox |
| `@Transactional` on a 5-second report | blocks vacuum, holds snapshot | read-only, no transaction, or a replica |
| `idle in transaction` connections | bloat, lock retention | tune pool, always close |
| Catching exceptions inside a transactional method | commits partial work | rethrow or mark rollback-only |
| `REQUIRES_NEW` for logging everywhere | 2x connections, deadlock risk | use async logging or a message |
| Retry **inside** the transaction | retries a doomed transaction | retry the transactional method |
| Fetching 10k entities then updating in a loop | N updates, long transaction | bulk update statement |
| `@Transactional` on a self-invoked method | no transaction at all | separate bean |
| Changing isolation per call | pool churn, plan churn | set at the connection/pool level |
| Uneven lock ordering | deadlocks | sort ids before locking |
| Ignoring `40001` | 500s under load | retry with backoff |

---

## 10. Code Examples

### 10.1 A correct transfer, three ways

**Atomic SQL (preferred):**

```java
@Transactional
public void transfer(Long from, Long to, BigDecimal amount) {
    int debited = jdbc.update("""
        UPDATE accounts SET balance = balance - ?
        WHERE id = ? AND balance >= ?
        """, amount, from, amount);
    if (debited == 0) throw new InsufficientFundsException(from);

    int credited = jdbc.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", amount, to);
    if (credited == 0) throw new IllegalStateException("missing destination " + to);
}
```

**Pessimistic, for a complex invariant:**

```java
@Transactional
public void transfer(Long from, Long to, BigDecimal amount) {
    var first  = repo.findForUpdate(Math.min(from, to));   // deterministic order → no deadlock
    var second = repo.findForUpdate(Math.max(from, to));
    var src = from.equals(first.getId()) ? first : second;
    if (src.getBalance().compareTo(amount) < 0) throw new InsufficientFundsException(from);
    src.setBalance(src.getBalance().subtract(amount));
    (src == first ? second : first).setBalance(
        (src == first ? second : first).getBalance().add(amount));
}
```

**Serializable, for a cross-row invariant:**

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public void allocate(...) { /* read a set, assert the invariant, write */ }
// with @Retryable on the caller for 40001
```

### 10.2 Optimistic locking with a friendly 409

```java
@Transactional
public OrderResponse update(Long id, UpdateOrder cmd, long expectedVersion) {
    var order = repo.findById(id).orElseThrow();
    if (order.getVersion() != expectedVersion) {
        throw new PreconditionFailedException("modified by someone else");
    }
    order.apply(cmd);                 // @Version increments on flush
    return OrderResponse.of(order);
}

@ExceptionHandler(OptimisticLockingFailureException.class)
ResponseEntity<?> conflict(Exception e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .header("Retry-After", "0")
        .body(Map.of("error", "concurrent modification, please retry"));
}
```

### 10.3 Work queue with `SKIP LOCKED`

```java
@Transactional
public List<Job> claim(int n) {
    return jdbc.query("""
        UPDATE jobs SET status = 'RUNNING', started_at = now(), worker = ?
        WHERE id IN (
            SELECT id FROM jobs
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT ?
            FOR UPDATE SKIP LOCKED
        )
        RETURNING id, payload
        """, rowMapper, workerId(), n);
}
```

Multiple workers, no blocking, no duplicates. This is the correct alternative to a naive
`SELECT ... WHERE status='PENDING'` + `UPDATE` loop.

### 10.4 Transaction boundaries around an external call

```java
// ❌ locks held across the network call
@Transactional
public void place(Order o) { repo.save(o); payment.charge(o); }

// ✅ short transaction, then external call, then a second short transaction
public void place(Order o) {
    var saved = tx.execute(s -> { var x = repo.save(o); repo.flush(); return x; });
    try {
        payment.charge(saved);
        tx.execute(s -> { saved.markPaid(); repo.save(saved); return null; });
    } catch (Exception e) {
        tx.execute(s -> { saved.markFailed(); repo.save(saved); return null; });
    }
}
```

Or, better: on outbox + saga ([Topic 08 ch 06](../08-Microservices-with-Spring-Boot/06-Distributed-Data-Management-and-Saga-Patterns.md)).

### 10.5 Detecting long transactions in production

```sql
SELECT pid, now() - xact_start AS duration, state, left(query, 80) AS query
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
  AND now() - xact_start > interval '30 seconds'
ORDER BY duration DESC;
```

Alert on this. A single `idle in transaction` connection can block vacuum, bloat a table, and
hold row locks for hours.

---

## 11. Common Mistakes

| Mistake | Consequence | Fix |
|---|---|---|
| Read-modify-write without a lock or version | lost updates | atomic SQL / `@Version` / `FOR UPDATE` |
| `check-then-act` (`exists` then `insert`) | duplicates | unique constraint + handle the violation |
| Calling a remote service inside a transaction | locks held for seconds | move it out |
| `@Transactional` on a self-invoked method | no transaction | separate bean |
| Checked exception → expected rollback | commits partial work | `rollbackFor = Exception.class` |
| Swallowing exceptions in a transactional method | commits anyway | rethrow |
| Retry inside the transaction | doomed retry | retry the outer method |
| Long-running transaction | bloat, lock retention, vacuum blocked | shorten; use `readOnly` |
| `SELECT ... FOR UPDATE` without a transaction | lock released immediately | keep it in a transaction |
| Inconsistent lock ordering | deadlocks | sort ids |
| `SERIALIZABLE` without retry logic | 500s | retry on `40001` |
| Never checking the affected row count | silent no-op updates | assert `rows == 1` |
| Using `REPEATABLE READ` to "fix" lost updates | fine, but only if you retry on conflict | handle `40001` |
| `DROP`/`ALTER` without `lock_timeout` | queues behind a long query, blocks everything | `lock_timeout` |

---

## 12. Interview Questions & Model Answers

**Q1. Explain isolation levels.**
> They trade concurrency for protection against four phenomena: dirty reads, non-repeatable
> reads, phantoms, and lost updates. Read Committed takes a snapshot per statement, so it allows
> non-repeatable reads and phantoms. Repeatable Read takes one snapshot for the whole
> transaction. Serializable adds conflict detection. In Postgres specifically: there are no
> dirty reads at any level, Repeatable Read is snapshot isolation so it also prevents phantoms,
> and both Repeatable Read and Serializable will abort one transaction on a write conflict — so
> you must retry.

**Q2. How do you handle lost updates?**
> Three options. Push the precondition into a single atomic statement —
> `UPDATE ... SET balance = balance - ? WHERE balance >= ?` and check the affected row count,
> which is the cheapest and my default. Optimistic locking with a `@Version` column for
> read-modify-write across a request, returning 409 on conflict. Pessimistic `FOR UPDATE` when
> the logic is complex and contention is low enough to tolerate waiting.

**Q3. What is write skew and how do you prevent it?**
> Two transactions each read a set, each concludes their write is safe, and both commit — the
> combination breaks an invariant although each transaction was individually valid. The classic
> is two doctors going off call when at least two must remain. Snapshot isolation allows this.
> The fixes are Serializable isolation, which detects it via predicate conflicts, or explicitly
> locking the set with `FOR UPDATE` so the second transaction sees the first one's change.

**Q4. Why not use Serializable everywhere?**
> Throughput and abort rate. SSI aborts transactions that could produce a non-serialisable
> history, so under contention your app sees a stream of `40001` errors and retries, and every
> retry is wasted work. Long transactions make it much worse. I use it surgically for the paths
> where an invariant spans rows, and Read Committed plus atomic statements everywhere else.

**Q5. How does MVCC work and what does it cost?**
> Each write creates a new row version; readers use a snapshot and therefore never block or get
> blocked by writers. The cost is dead tuples — old versions accumulate until `VACUUM` reclaims
> them, and vacuum can't remove anything still visible to an open transaction. So long-running
> or `idle in transaction` sessions cause bloat, and heavy updates on the same row churn it. That's
> why I keep transactions in the milliseconds and alert on anything over 30 seconds.

**Q6. How do you avoid deadlocks?**
> Consistent lock ordering above all — acquire row locks in a deterministic order, usually sorted
> by id. Then keep transactions short, prefer one atomic statement over a read-then-write, and
> retry on `40P01` with jittered backoff. I'd also say that deadlocks are a symptom; if they're
> frequent, the lock ordering or transaction length is the real bug.

**Q7. Why doesn't `@Transactional` work on my private method?**
> Spring's declarative transactions are proxy-based. A private method can't be overridden by
> CGLIB and a self-invocation never crosses the proxy, so no interception happens — the method runs
> with whatever transaction the caller had, or none. The fixes are to make the method public on a
> separate bean, or inject the proxy (`@Lazy self`).

**Q8. A `@Transactional` method throws a checked exception and the data is still committed. Why?**
> Spring only rolls back on unchecked exceptions by default. A checked exception is treated as a
> recoverable business outcome and the transaction commits. That's why I add
> `rollbackFor = Exception.class` when a checked exception should abort the work — or convert to a
> runtime exception.

**Q9. How would you implement "only one worker processes this job"?**
> `SELECT ... FOR UPDATE SKIP LOCKED` inside a transaction, or an atomic
> `UPDATE ... WHERE status = 'PENDING' RETURNING id`. Both let multiple workers grab different jobs
> without blocking on each other. An advisory lock works for "only one node runs this scheduled
> migration", and a unique constraint works when the job's identity is known.

**Q10. What does `readOnly = true` do?**
> For Hibernate it skips dirty checking and flush, which is a real saving on large result sets,
> and it signals intent. It can also route to a read replica in some setups. It doesn't prevent
> writes at the database level — it's a hint, not a guarantee.

---

## 13. Self-Check & Revision Card

- [ ] I can recite the four phenomena and the isolation table, including Postgres's deviations.
- [ ] I can explain MVCC and why long transactions cause bloat.
- [ ] I can name three ways to prevent lost updates and pick between them.
- [ ] I can explain write skew and why only Serializable prevents it.
- [ ] I can prevent deadlocks with lock ordering and short transactions.
- [ ] I know the six ways `@Transactional` silently fails.
- [ ] I can use `SKIP LOCKED` for a work queue.
- [ ] I know why `readOnly` and `timeout` matter.

```
┌────────────────── TRANSACTIONS — 60s RECALL ─────────────────────────────────┐
│ PHENOMENA: dirty · non-repeatable · phantom · LOST UPDATE (the real one).    │
│                                                                              │
│ PG LEVELS: RC = snapshot/statement (default, allows lost updates)            │
│            RR = snapshot/tx = SNAPSHOT ISOLATION (no phantoms; 40001 retry)    │
│            SERIALIZABLE = SSI, aborts write skew, needs retries              │
│            NO dirty reads at ANY level in Postgres.                          │
│                                                                              │
│ MVCC: writers don't block readers. UPDATE = new version + dead tuple.        │
│ VACUUM needs no long transactions ⇒ keep tx in MILLISECONDS.                 │
│                                                                              │
│ LOST UPDATE FIXES: atomic SQL (best) > optimistic @Version > FOR UPDATE.     │
│   UPDATE ... WHERE balance >= ? ; check rows affected.                       │
│                                                                              │
│ WRITE SKEW: only Serializable (or FOR UPDATE on the set) prevents it.        │
│ DEADLOCK: lock in sorted id order, short tx, retry 40P01/40001.              │
│                                                                              │
│ @Transactional FAILS: self-invocation · private · swallowed exception ·      │
│   checked exception (needs rollbackFor) · new thread.                        │
│ NEVER hold a transaction across an HTTP call. Use the outbox.                │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [03 · Query Optimisation & Execution Plans](./03-Query-Optimization-and-Execution-Plans.md) · **Next:** [05 · Scaling, Partitioning & Replication](./05-Scaling-Partitioning-and-Replication.md)
