# 11 — Database Design & Optimisation

> **Level:** Intermediate → Expert
> **Interview Frequency:** ★★★★★ (every backend loop has at least one schema or query question)
> **Prerequisite:** [10 · Caching](../10-Caching/README.md)
> **Last Updated:** 2026

---

## 🎯 Why This Topic Is Separate

The database is the component most likely to be the bottleneck and the least likely to be
understood by the people adding load to it. Most "we need to scale the database" conversations
are really "someone wrote a query without an index."

This topic is deliberately ordered the way you debug:

1. **Schema** — the shape determines which queries are cheap forever.
2. **Indexes** — the largest single lever, and the most misunderstood.
3. **Query plans** — the evidence, not the guess.
4. **Transactions** — where correctness bugs live.
5. **Scaling** — the last resort, in the cheapest order.

The sentence that wins the topic: *"Before I change the architecture I want to know whether the
bottleneck is CPU, IO, locks, or connections — because only one of those justifies sharding."*

---

## 📚 Chapters

| # | Chapter | Core Idea | Difficulty |
|---|---------|-----------|------------|
| 01 | [Schema Design & Normalization](./01-Schema-Design-and-Normalization.md) | Normal forms as anomalies, denormalisation with an owner, keys, types, constraints, migrations | ⭐⭐⭐ |
| 02 | [Indexing Deep Dive](./02-Indexing-Deep-Dive.md) | B-tree structure, leftmost prefix, covering/`INCLUDE`, partial/expression/GIN/BRIN, why indexes are ignored | ⭐⭐⭐⭐ |
| 03 | [Query Optimisation & Execution Plans](./03-Query-Optimization-and-Execution-Plans.md) | Reading `EXPLAIN ANALYZE`, join algorithms, N+1, keyset pagination, statistics | ⭐⭐⭐⭐ |
| 04 | [Transactions, Locking & Isolation](./04-Transactions-Locking-and-Isolation.md) | Isolation levels and Postgres's deviations, MVCC, lost updates, write skew, deadlocks, `@Transactional` traps | ⭐⭐⭐⭐⭐ |
| 05 | [Scaling, Partitioning & Replication](./05-Scaling-Partitioning-and-Replication.md) | The escalation ladder, replicas and lag, partitioning vs sharding, PgBouncer, PITR | ⭐⭐⭐⭐⭐ |

---

## 🧠 The One Mental Model That Covers Everything

```text
             THE DATABASE COSTS ARE SET BY THE SCHEMA AND THE INDEX.
             EVERYTHING ELSE IS A CONSEQUENCE.

   ┌─────────────────────────────────────────────────────────────────┐
   │  SCHEMA      → which queries are expressible                    │
   │  INDEX       → which queries are CHEAP                          │
   │  PLAN        → proof of what actually happens                   │
   │  TRANSACTION → proof that concurrent writers can't corrupt it   │
   │  SCALING     → what to do when all of the above is already right │
   └─────────────────────────────────────────────────────────────────┘
```

**The debugging loop that applies to all five chapters:**

```text
   measure  →  EXPLAIN ANALYZE  →  find the biggest (actual time × loops)
        ▲                                        │
        └──── fix ONE thing, remeasure ──────────┘
```

---

## 📊 The Design Decision Matrix

| Decision | Default | Alternative | Trade-off |
|---|---|---|---|
| **Normal form** | 3NF | Denormalised read columns | Integrity vs read speed |
| **Primary key** | `bigint` identity | UUID / UUIDv7 | Locality vs non-enumerability |
| **External id** | Separate `uuid` column | Expose the PK | Security vs simplicity |
| **Money** | `numeric(19,4)` / cents | — | **never float** |
| **Timestamps** | `timestamptz` (UTC) | `date` for calendar days | Correctness |
| **Enums** | `text` + `CHECK` | Lookup table | Simplicity vs extensibility |
| **Index type** | B-tree | Partial / GIN / BRIN | Generality vs size |
| **Index column order** | Equality first, range last | — | Usability |
| **Pagination** | Keyset | `OFFSET` | Performance vs page jumps |
| **Isolation** | Read Committed | Serializable | Throughput vs correctness |
| **Lost-update fix** | Atomic SQL | `@Version` / `FOR UPDATE` | Contention profile |
| **Read scaling** | Replica + sticky writes | Single primary | Capacity vs staleness |
| **Write scaling** | Vertical | Sharding | Simplicity vs permanent cost |
| **Pooling** | PgBouncer transaction mode | App pool only | Multiplexing vs session state |
| **Connection count** | ~2–4× cores | — | More is slower |

---

## ⚠️ The Traps (the ones that cost people interviews)

| Trap | Wrong answer | Right answer |
|---|---|---|
| "Just add an index" | "Always helps" | Indexes serve **specific queries**; unselective ones are ignored |
| "Index every column" | "Coverage" | Write amplification; audit `idx_scan = 0` |
| "The planner ignored my index" | "A bug" | It **costed** it — estimate wrong or predicate unselective |
| "Composite indexes are interchangeable" | "(a,b) = (b,a)" | **Leftmost prefix** — order matters |
| "Two range columns in one index" | "Works" | Only **one** range column, and it's last |
| "`OFFSET` is fine" | "It's standard SQL" | Costs **O(offset)**; keyset is O(limit) |
| "Repeatable Read prevents lost updates" | "Yes" | It **raises 40001** — you must retry |
| "Postgres Repeatable Read allows phantoms" | "Like the standard" | It's **snapshot isolation** — no phantoms |
| "Serializable is just safer" | "Use it everywhere" | Aborts under contention; needs retries |
| "`@Transactional` rolls back on any exception" | "Yes" | Only **runtime** exceptions by default |
| "`@Transactional` on a private method works" | "Sure" | Proxy-based — **no transaction at all** |
| "Replicas scale writes" | "They scale the DB" | Replicas are **read-only**; writes hit one primary |
| "Replicas are backups" | "We have HA" | A `DROP TABLE` replicates instantly — need **PITR** |
| "Shard when it's slow" | "Yes" | Follow the ladder; most cases end at indexes/cache |
| "Partitioning adds throughput" | "It splits the table" | Same server, same CPU — **operational** win only |
| "More connections = more throughput" | "Yes" | ~2–4× cores; more causes contention |
| "`float` for money" | "It's a number" | Binary floats lose cents — `numeric`/`bigint` |

---

## 🎤 The 20 Questions to Expect

| # | Question | Where |
|---|---|---|
| 1 | Design the schema for an e-commerce system | [Ch 01](./01-Schema-Design-and-Normalization.md) |
| 2 | Explain normalisation and when you'd denormalise | [Ch 01](./01-Schema-Design-and-Normalization.md) |
| 3 | Surrogate vs natural key? | [Ch 01](./01-Schema-Design-and-Normalization.md) |
| 4 | How do you store money/time/enums? | [Ch 01](./01-Schema-Design-and-Normalization.md) |
| 5 | How does a B-tree index work? | [Ch 02](./02-Indexing-Deep-Dive.md) |
| 6 | Explain the leftmost-prefix rule | [Ch 02](./02-Indexing-Deep-Dive.md) |
| 7 | What is a covering index? | [Ch 02](./02-Indexing-Deep-Dive.md) |
| 8 | Why isn't my index being used? | [Ch 02](./02-Indexing-Deep-Dive.md) |
| 9 | When would you use a partial index? | [Ch 02](./02-Indexing-Deep-Dive.md) |
| 10 | How do you read an `EXPLAIN` plan? | [Ch 03](./03-Query-Optimization-and-Execution-Plans.md) |
| 11 | Nested loop vs hash join? | [Ch 03](./03-Query-Optimization-and-Execution-Plans.md) |
| 12 | What is an N+1 query and how do you fix it? | [Ch 03](./03-Query-Optimization-and-Execution-Plans.md) |
| 13 | Why is `OFFSET` pagination slow? | [Ch 03](./03-Query-Optimization-and-Execution-Plans.md) |
| 14 | Explain isolation levels | [Ch 04](./04-Transactions-Locking-and-Isolation.md) |
| 15 | How do you prevent lost updates? | [Ch 04](./04-Transactions-Locking-and-Isolation.md) |
| 16 | What is write skew? | [Ch 04](./04-Transactions-Locking-and-Isolation.md) |
| 17 | How does MVCC work and what does it cost? | [Ch 04](./04-Transactions-Locking-and-Isolation.md) |
| 18 | The DB is at 100% CPU — what do you do? | [Ch 05](./05-Scaling-Partitioning-and-Replication.md) |
| 19 | Partitioning vs sharding? | [Ch 05](./05-Scaling-Partitioning-and-Replication.md) |
| 20 | How many connections should Postgres have? | [Ch 05](./05-Scaling-Partitioning-and-Replication.md) |

---

## 🛠️ The Code You'll Actually Write

| Tool / Annotation | What it does | Where |
|---|---|---|
| `EXPLAIN (ANALYZE, BUFFERS)` | The evidence | [Ch 03](./03-Query-Optimization-and-Execution-Plans.md) |
| `CREATE INDEX ... INCLUDE` / `CONCURRENTLY` | Covering indexes without downtime | [Ch 02](./02-Indexing-Deep-Dive.md) |
| `CREATE INDEX ... WHERE` | Partial index for a hot subset | [Ch 02](./02-Indexing-Deep-Dive.md) |
| `pg_stat_statements` / `pg_stat_activity` | Find the real bottleneck | [Ch 03](./03-Query-Optimization-and-Execution-Plans.md), [Ch 05](./05-Scaling-Partitioning-and-Replication.md) |
| `@Version` | Optimistic locking | [Ch 04](./04-Transactions-Locking-and-Isolation.md) |
| `@Lock(PESSIMISTIC_WRITE)` | `SELECT ... FOR UPDATE` | [Ch 04](./04-Transactions-Locking-and-Isolation.md) |
| `FOR UPDATE SKIP LOCKED` | Work queue without contention | [Ch 04](./04-Transactions-Locking-and-Isolation.md) |
| `@Transactional(readOnly, timeout, rollbackFor)` | Correct transaction semantics | [Ch 04](./04-Transactions-Locking-and-Isolation.md) |
| `default_batch_fetch_size` | Kill N+1 without touching queries | [Ch 03](./03-Query-Optimization-and-Execution-Plans.md) |
| `AbstractRoutingDataSource` | Read/write splitting | [Ch 05](./05-Scaling-Partitioning-and-Replication.md) |
| `PARTITION BY RANGE` + maintenance job | Retention in O(1) | [Ch 05](./05-Scaling-Partitioning-and-Replication.md) |
| `idle_in_transaction_session_timeout` | Prevent bloat-for-days | [Ch 05](./05-Scaling-Partitioning-and-Replication.md) |

---

## 🔗 The Setup (the minimum)

```sql
-- the three extensions/limits that pay for themselves immediately
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

ALTER SYSTEM SET idle_in_transaction_session_timeout = '10s';
ALTER SYSTEM SET lock_timeout = '2s';
ALTER SYSTEM SET statement_timeout = '30s';
```

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc.batch_size: 50
        order_inserts: true
        order_updates: true
        default_batch_fetch_size: 50
    open-in-view: false            # transactions belong to the service layer
  datasource:
    hikari:
      maximum-pool-size: 20
      connection-timeout: 2000
      max-lifetime: 1800000
      leak-detection-threshold: 20000
```

---

## 📐 The Reference Architecture

```text
                        ┌──────────────────────────────────────┐
   writes ─────────────►│  PRIMARY  (one writer)               │
                        │  short tx · atomic SQL · @Version    │
                        └───────────────┬──────────────────────┘
                                        │ WAL (async)
              ┌─────────────────────────┼─────────────────────────┐
              ▼                         ▼                         ▼
      ┌───────────────┐        ┌───────────────┐        ┌───────────────┐
      │ replica: user │        │ replica:      │        │ replica:      │
      │ reads         │        │ reporting     │        │ failover      │
      └───────────────┘        └───────────────┘        └───────────────┘
              ▲                         ▲
              │ readOnly tx             │ heavy aggregates (never the primary)
   ┌──────────┴──────────┐
   │  APP (PgBouncer in  │   small pools · statement/lock timeouts
   │  transaction mode)  │
   └─────────────────────┘
```

**Rules the diagram encodes:**
- **One primary writer**; replicas absorb reads, reporting and failover.
- Read-your-writes routes to the **primary** for a short window after a write.
- Reporting never touches the primary.
- Timeouts are set at the connection level, so no query can run forever.

---

## ✅ The Self-Check

- [ ] I can design a 3NF schema and name the anomalies it prevents.
- [ ] I can justify every denormalisation with an owner and a reconciliation query.
- [ ] I can explain a B-tree index and the leftmost-prefix rule precisely.
- [ ] I can design a covering index and verify `Heap Fetches: 0`.
- [ ] I can diagnose "index not used" in three steps.
- [ ] I can read a plan and find the node that costs the time.
- [ ] I can fix an N+1 three different ways.
- [ ] I can write keyset pagination.
- [ ] I can explain isolation levels including Postgres's deviations.
- [ ] I can fix lost updates three ways and pick between them.
- [ ] I can name write skew and the only fix.
- [ ] I can list how `@Transactional` silently fails.
- [ ] I can recite the scaling ladder and diagnose the bottleneck.
- [ ] I can explain partitioning vs sharding and what each costs.
- [ ] I can size a connection pool with numbers.
- [ ] I can describe PITR and why replicas aren't backups.

---

## 🧪 The Coding Drill

- [ ] Load 10M rows; write a query that seq-scans; add the right composite index and compare
      plans before/after.
- [ ] Prove the leftmost-prefix rule: `(a,b)` fails for `WHERE b = ?`.
- [ ] Build a covering index and show `Index Only Scan … Heap Fetches: 0`.
- [ ] Explain why an index on `is_paid` is ignored; replace it with a partial index.
- [ ] Reproduce an N+1 with Hibernate statistics; fix it with `default_batch_fetch_size`.
- [ ] Implement `OFFSET` vs keyset pagination at offset 500k and time both.
- [ ] Reproduce a lost update with two threads; fix it with an atomic `UPDATE` and prove it.
- [ ] Reproduce write skew under Repeatable Read; show Serializable prevents it.
- [ ] Create a deadlock with reversed lock order; fix by sorting ids.
- [ ] Partition a table by month, prove pruning with `EXPLAIN`, and drop an old partition.
- [ ] Measure connection overhead: 200 app connections vs PgBouncer with 20.

---

**Related folders:** ← [10 · Caching](../10-Caching/README.md) · → [12 · System Design for Java Backend](../12-System-Design-for-Java-Backend/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
