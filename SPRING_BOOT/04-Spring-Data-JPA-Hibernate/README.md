# 04 — Spring Data JPA & Hibernate · Interview Notes Index

> Spring Data JPA is where "writing Java" becomes "talking to a database" — and where most
> production incidents live. The single idea that makes it click: **Hibernate does not execute
> your repository method; it translates it into SQL, tracks every entity it touched in a
> per-transaction identity map, and flushes the difference at commit time.** Everything in these
> five chapters — the N+1, the `LazyInitializationException`, the "my change was not saved", the
> optimistic-lock failure — is a consequence of that one model.

| Priority | Chapter | Level | Read Time | Why It Matters |
|----------|---------|-------|-----------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · ORM Theory & JPA Internals](01-ORM-Theory-and-JPA-Internals.md) | 🟢→⚫ | ~45 min | The spec-vs-implementation split, `EntityManagerFactory` vs `EntityManager`, identifier strategies, and why `ddl-auto=update` is banned in production |
| ⭐⭐⭐⭐⭐ | [02 · Entity Lifecycle, Persistence Context & Caches](02-Entity-Lifecycle-Persistence-Context-and-Caches.md) | 🟡→⚫ | ~50 min | The four entity states, the identity map, `merge` returning a *different* object, and the 1st vs 2nd level cache |
| ⭐⭐⭐⭐⭐ | [03 · Transactions, Propagation & Isolation](03-Transactions-Propagation-and-Isolation.md) | 🟡→⚫ | ~50 min | Proxy-based `@Transactional`, self-invocation, the 7 propagation behaviours, and why a checked exception does **not** roll back |
| ⭐⭐⭐⭐⭐ | [04 · Lazy/Eager, N+1 & Fetch Strategies](04-Lazy-Eager-N-plus-1-and-Fetch-Strategies.md) | 🟡→⚫ | ~50 min | The single most-asked performance question in Java interviews, with six fixes and their real trade-offs |
| ⭐⭐⭐⭐ | [05 · Hibernate Internals & Performance Tuning](05-Hibernate-Internals-and-Performance-Tuning.md) | ⚫ | ~45 min | `ActionQueue`, dirty checking, the flush order, `StatisticsMBean`, and the batching knobs — where senior candidates separate from mid-level ones |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| What is the difference between JPA and Hibernate? | Saying "JPA is an API, Hibernate is an implementation" and stopping there — the expert answer adds **JPA is the *standard* (Jakarta Persistence 3.1), Hibernate 6 is *one* implementation** (EclipseLink, OpenJPA are others), Spring Data JPA is a **third** layer on top of the standard, and Spring Data JPA is *not* the same thing as Spring Data JDBC |
| `EntityManagerFactory` vs `EntityManager`? | "One is a factory" — the real answer: the EMF is **thread-safe, expensive, application-scoped**; the EM is **not thread-safe, transaction-scoped**, and is the *only* thing you may pass between threads |
| What are the four entity states? | Listing transient/persistent/detached/removed but missing that **`merge` returns a NEW managed copy** — your detached object is never the one that gets updated |
| First-level vs second-level cache? | Calling the 1st-level cache "a cache" — it is a **per-transaction identity map**; the 2nd-level cache is a **shared, cross-transaction** store that needs a distributed provider to be correct in a multi-instance deployment |
| Why does `LazyInitializationException` happen? | Blaming lazy loading — the real cause is that **the persistence context closes at the transaction boundary**; lazy loading itself is fine, it just needs an open session |
| What is the N+1 problem and how do you fix it? | "Use eager loading" — which produces a **cartesian explosion** on collections and can be *slower* than N+1; the fix is a join fetch, an `@EntityGraph`, `@BatchSize`, or a DTO projection |
| `JOIN FETCH` vs `@EntityGraph` vs `@BatchSize`? | Not knowing that `@EntityGraph` on a **collection still needs `distinct`** in the JPQL, and that `Page` + join fetch forces **in-memory pagination** (`HHH000104`) |
| What does `REQUIRES_NEW` actually cost? | Not saying it **suspends the current transaction and needs a *second* DB connection** — in a loop that is connection-pool exhaustion |
| Why doesn't `@Transactional` work on my method? | "Because of the proxy" without saying **the call never leaves the target object**, so the advice never runs — and that `private`/`final` methods cannot be advised at all |
| A checked exception was thrown — why didn't it roll back? | Spring rolls back on `RuntimeException`/`Error` **only**; a checked exception commits the transaction unless you set `rollbackFor = Exception.class` |
| `save()` vs `saveAndFlush()` vs `flush()`? | Not explaining that `save()` on a **new** entity triggers an immediate `INSERT` (to get the generated id) while `save()` on a **detached** entity performs a `merge` |
| Why is `ddl-auto=update` banned in production? | Saying "it's not production-grade" — the expert answer is **it never alters an existing column**, so a renamed column silently leaves the old one behind and the new one NULL, and it can **drop/recreate** objects it misreads |

---

## 🧠 The One Picture — Where You Are In The Stack

```text
   ┌───────────────────────────────────────────────────────────────────────────┐
   │  YOUR CODE                                                                │
   │                                                                           │
   │   OrderService                                                           │
   │        │  findByCustomerId(...)                                           │
   │        ▼                                                                  │
   │   OrderRepository extends JpaRepository<Order, Long>   ← Spring Data JPA  │
   │        │  (a JDK dynamic proxy — you never implement it)                   │
   │        ▼                                                                  │
   │   SimpleJpaRepository  →  EntityManager                                  │
   │        │                                                                  │
   │        ▼                                                                  │
   │   jakarta.persistence.EntityManager   ← JPA STANDARD (the contract)       │
   │        │                                                                  │
   │        ▼                                                                  │
   │   org.hibernate.SessionImpl           ← HIBERNATE (the implementation)   │
   │        │                                                                  │
   │        ├──▶ PersistenceContext  (1st-level cache = identity map)         │
   │        ├──▶ ActionQueue        (pending INSERTs/UPDATEs/DELETEs)          │
   │        └──▶ JdbcCoordinator    → JDBC → HikariCP → PostgreSQL            │
   └───────────────────────────────────────────────────────────────────────────┘

   THE FIVE THINGS THAT GO WRONG IN PRODUCTION — and where they live:
   ┌────────────────────────────────┬───────────────────────────────────────────┐
   │ SYMPTOM                        │ ROOT CAUSE LIVES IN                         │
   ├────────────────────────────────┼───────────────────────────────────────────┤
   │ N+1 / slow endpoint            │ Translation + fetch plan (ch. 04)          │
   │ LazyInitializationException   │ Transaction boundary (ch. 03 + 04)        │
   │ "My change was not saved"      │ Persistence context + merge (ch. 02)       │
   │ Optimistic lock failure        │ @Version + flush-time check (ch. 01/02)   │
   │ Connection pool exhausted      │ Propagation REQUIRES_NEW (ch. 03)         │
   └────────────────────────────────┴───────────────────────────────────────────┘
```

> **Caption:** Every symptom above is a *consequence* of the model, not a random bug. Learn the
> model and the symptoms become predictable — which is exactly what the interviewer is testing.

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **The owning side of a relationship** — `mappedBy` on the inverse side, `@JoinColumn` on the owner. Get this backwards and Hibernate writes *two* foreign key columns (or none).
- [ ] **`@EntityManagerFactory` vs `@EntityManager` injection** — injecting the EM directly into a singleton bean gives you a `SharedEntityManager` that is a *proxy*, and injecting it into a `@RequestScope` bean gives you a *different* session than the one your repository used.
- [ ] **Identifier strategies** — `IDENTITY` kills JDBC batching; `SEQUENCE` needs a pool sized for your concurrency; `TABLE` is a bottleneck.
- [ ] **The identity map** — one managed instance per identity per persistence context; `find` twice = one query.
- [ ] **`merge` vs `persist`** — `merge` returns a *new* object; mutating the detached one changes nothing.
- [ ] **Flush vs commit** — `flush` sends SQL, `commit` ends the transaction; auto-flush before overlapping queries.
- [ ] **The rollback rule** — `RuntimeException`/`Error` roll back; checked exceptions do **not**.
- [ ] **`Pageable` + `JOIN FETCH`** — the count query and the in-memory pagination warning (`HHH000104`).
- [ ] **Second-level cache invalidation** across instances — why it is usually skipped in a multi-node deployment.
- [ ] **`open-in-view`** — Boot logs a warning at startup for a reason; know why it is a smell, not just that it is "slow".

---

## 🧪 Coding Drill

- [ ] Map a 4-entity domain (`Customer` → `Order` → `OrderItem` → `Product`) with **one** `@OneToMany`, **one** `@ManyToMany` and **one** `@ManyToOne`, and verify with `show-sql` that the `ORDER_ITEM` table has exactly **one** FK to `PRODUCT`.
- [ ] Add `@Version` to an entity, open two transactions, and make both update the same row — capture the exact `ObjectOptimisticLockingFailureException` and the `update ... where id=? and version=?` SQL.
- [ ] Write a repository method that returns an `interface` projection and count the SQL statements — prove the N+1 is **gone** because no entity was ever loaded.
- [ ] Turn on `spring.jpa.properties.hibernate.generate_statistics=true`, call one endpoint 100 times, and read `entityLoadCount` vs `queryExecutionCount` from `/actuator/metrics/hibernate.statistics`.
- [ ] Flip `spring.jpa.open-in-view=false` and run the whole integration test suite — list every `LazyInitializationException` you get; that list *is* your N+1 report.
- [ ] Reproduce the "checked exception does not roll back" bug, then fix it with `rollbackFor = Exception.class` and prove it with two `SELECT` counts.

---

**Related folders:** ← [03 · Spring MVC](../03-Spring-MVC/README.md) · → [11 · Database Design & Optimization](../11-Database-Design-Optimization/README.md) · ↑ [Master Index](../README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
