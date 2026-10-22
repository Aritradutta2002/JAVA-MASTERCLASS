# 04 · Spring Data JPA & Hibernate

> **The persistence layer** — from the ORM theory to the Hibernate internals, the caching
> layers, the transactions, and the production tuning.
> **Interview Frequency:** ★★★★★ (Every backend interview asks about the JPA — and the N+1
> is the most common performance question)

| Priority | Chapter | What It Covers |
|----------|---------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · ORM Theory & JPA Fundamentals](./01-ORM-Theory-and-JPA-Fundamentals.md) | The ORM theory, the JPA vs the Hibernate, the spec vs the provider, the setup |
| ⭐⭐⭐⭐⭐ | [02 · JPA Internals & the Persistence Context](./02-JPA-Internals-and-Persistence-Context.md) | The unit of work, the identity map, the dirty checking, the action queue, the proxies |
| ⭐⭐⭐⭐⭐ | [03 · Entity Lifecycle & Mappings](./03-Entity-Lifecycle-and-Mappings.md) | The states, the callbacks, the relationship mappings, the cascade, the fetch types |
| ⭐⭐⭐⭐⭐ | [04 · Caching, Fetching & the N+1](./04-Caching-Fetching-and-N-plus-1.md) | The three cache levels, the fetch strategies, the fix ladder, the stampede |
| ⭐⭐⭐⭐⭐ | [05 · Transactions & Query Optimisation](./05-Transactions-and-Query-Optimization.md) | The propagation, the isolation, the rollback rules, the query tuning, the pool |
| ⭐⭐⭐⭐ | [06 · Hibernate Internals & Performance Tuning](./06-Hibernate-Internals-and-Performance-Tuning.md) | The `EntityPersister`, the SQM, the enhancement, the observability |

---

## 🎯 The Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| What is the N+1 and how do you fix it? | Saying only "use `join fetch`" — the strong answer names the **cause**, the **detection**, and the **fix ladder** (the projection → the entity graph → the batching) with the **trade-offs**. |
| `persist` vs `merge`? | The **return value** of `merge` — the managed instance, not your original. The classic bug: ignoring it. |
| What is the persistence context? | The **three** properties: the identity map, the dirty checking, the deferred flush. Not just "a cache". |
| What is the first-level vs the second-level cache? | The L2's **cluster-unawareness** — the stale reads across the instances. The modern answer: the Redis with the explicit invalidation. |
| `@Transactional` propagation? | `REQUIRES_NEW` takes a **second connection** (the pool exhaustion risk) and `NESTED` uses a **savepoint** (the same connection). |
| When does the transaction roll back? | The default is the `RuntimeException`/`Error` — a **checked exception commits**! (`rollbackFor = Exception.class`) |
| Why doesn't my `@Transactional` work? | The **self-invocation** (the proxy is bypassed) — the #1 cause. |
| `LAZY` vs `EAGER`? | The **defaults** (the one-to-many and the one-to-one are eager!) and the fact that the lazy is the better default. |
| `join fetch` + `Pageable`? | It **breaks the pagination** (the in-memory pagination, the `HHH000104`) — the `@EntityGraph` is the safe answer. |
| What's the difference between `REQUIRED` and `REQUIRES_NEW`? | The suspension + the new connection; and the "the inner commits independently". |

---

## 🧠 The Mental Models

```text
  THE PERSISTENCE CONTEXT = THE UNIT OF WORK
    the identity map  (the same row → the same object)
    the dirty tracker (the snapshot → the changed columns)
    the action queue  (the ordered pending SQL)

  THE FLUSH ORDER
    the orphan removals → the inserts → the updates
    → the collection ops → the deletes

  THE N+1 FIX LADDER
    ① the DTO projection   ② the @EntityGraph
    ③ the join fetch       ④ the @BatchSize    ⑤ the SUBSELECT

  THE CACHE LEVELS
    L1 — the persistence context (the transaction)
    L2 — the Hibernate region (⚠ not cluster-aware)
    L3 — the Redis (the explicit invalidation, the TTL)
```

---

## ⚠️ The Weakest Areas to Brush Up

- [ ] **The `@EntityGraph` vs the `join fetch` vs the projection** — and their pagination
  behaviours.
- [ ] **The `@SQLRestriction` (Hibernate 6.4+)** — the modern soft delete.
- [ ] **The `@MapsId` + the `@EmbeddedId`** — the composite keys without the duplicated columns.
- [ ] **The `REQUIRES_NEW` vs the `NESTED`** — the savepoint vs the new transaction.
- [ ] **The `UnexpectedRollbackException`** — the rollback-only marker and the swallowed
  exception.
- [ ] **The bytecode enhancement** — what it changes and what it costs.
- [ ] **The `JdbcObserver` (Hibernate 6.6)** and the `StatementInspector` — the observability
  hooks.
- [ ] **The `EXPLAIN (ANALYZE, BUFFERS)`** — reading the plan (the `Seq Scan`, the stale
  statistics, the `rows removed by Filter`).

---

## 🧪 Coding Drill

- [ ] Write a repository method that returns a **DTO projection** with the customer name and
  the line count — and assert the **query count is 1** in a `@DataJpaTest` (with the
  `NOT_SUPPORTED` propagation!).
- [ ] Implement a **soft delete** with the `@SQLRestriction` + the **partial unique index** and
  write the restore.
- [ ] Build a **transaction propagation demo** — the `REQUIRES_NEW` (the audit survives the
  rollback) and the `NESTED` (the savepoint) — with the log output proving it.
- [ ] Reproduce the **N+1** and fix it three ways (the projection, the `@EntityGraph`, the
  `@BatchSize`) — measuring the query count each time.
- [ ] Write a **bulk import** with the chunked transactions, the checkpoint, and the
  idempotency.
- [ ] Add the **`@Version`** and write the **concurrency test** (two threads, the same row) —
  assert that one succeeds and the other gets the conflict.

---

## 🔗 The Neighbours

- → [03 · Spring MVC](../03-Spring-MVC/README.md) — the web layer above this.
- → [05 · Spring Security](../05-Spring-Security/README.md) — the identity, the tenant
  isolation.
- → [11 · Database Design & Optimisation](../11-Database-Design-Optimization/README.md) — the
  indexes, the isolation, the locks.
- → [10 · Caching & Performance](../10-Caching/README.md) — the Redis, the distributed caching.

---

**Standard followed:** [../STANDARD.md](../STANDARD.md)
**Last Updated:** 2026
