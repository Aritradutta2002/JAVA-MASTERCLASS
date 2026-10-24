# 01 · SQL Databases & Relational Mastery — Interview Ready Kit

> **Level:** Beginner → Principal / Staff Backend Engineer (0–10+ Years Experience)  
> **Interview Frequency:** ★★★★★ (Mandatory in 100% of Backend, Systems, and Data Engineering loops)  
> **Focus Engines:** PostgreSQL (Heap Engine) & MySQL 8.0+ (InnoDB)  
> **Prerequisites:** None — Chapter 01 starts from zero syntax  
> **Last Updated:** 2026  
> **Chapters:** 11 + a runnable [`practice-schema.sql`](./practice-schema.sql)

---

## 🎯 Executive Overview

This kit is designed specifically for **backend developers at every level** preparing for technical interviews at top-tier tech companies (FAANG, high-growth startups, and fintech enterprises). It starts at **zero syntax** (Chapter 01) and climbs to engine physics (Chapters 03–09).

**Where do I start?**
- 0–2 years → Chapters **01 → 02** (fundamentals + joins), then skim the rest.
- 2–5 years → Chapters **01 → 02 → 05**, then **04** and **06**.
- 5–10+ years → Chapters **03 → 06 → 07 → 09**, then **10 → 11** for the live coding drills.

In senior loops, interviewers no longer *ask* you to write a simple `INNER JOIN` — they *watch* whether you write it correctly under pressure (Chapter 02). Once that is settled, they test whether you understand:
- How bytes are laid out on 8KB/16KB disk pages and buffer pool frames.
- Why random UUIDv4 primary keys cause catastrophic 50/50 B+ tree page splits.
- How the Cost-Based Optimizer calculates costs and why it ignores indexes under low selectivity.
- How PostgreSQL MVCC (Heap tuples + `xmin`/`xmax`) differs from MySQL InnoDB MVCC (Clustered Index + Undo Logs).
- Why Repeatable Read permits Write Skew and how Gap Locks prevent Phantom Reads.
- How to size connection pools using the HikariCP formula (→(\text{Cores} \times 2) + \text{Spindles}→).
- How to write advanced analytical window functions, recursive CTEs, and sessionization queries on the whiteboard.

---

## 📚 Master Chapter Index

| Chapter | Title | Core Focus Areas | Difficulty |
|:---:|---|---|:---:|
| **01** | [SQL Fundamentals, Data Types, Constraints & CRUD](./01-SQL-Fundamentals-Data-Types-Constraints-and-CRUD.md) | DDL/DQL/DML/DCL/TCL, keys, type map, FK actions, upserts, three-valued logic, views | ⭐⭐⭐ |
| **02** | [Joins, Set Operations & Subqueries](./02-Joins-Set-Operations-and-Subqueries.md) | Every join type, Venn-diagram truth, semi/anti-joins, `EXISTS` vs `IN`, `UNION`/`INTERSECT`/`EXCEPT`, correlated subqueries, 25 drills | ⭐⭐⭐⭐⭐ |
| **03** | [Relational Engine & Storage Internals](./03-Relational-Engine-and-Storage-Internals.md) | Page layout, Slot directory, Buffer Pool clock-sweep, WAL, ARIES recovery, Doublewrite buffer | ⭐⭐⭐⭐ |
| **04** | [Indexing Architecture & B+ Tree Physics](./04-Indexing-Architecture-and-B-Trees.md) | B+ Tree branching factor, Page splits, Clustered vs Heap, Leftmost prefix, Covering (`INCLUDE`), GIN/BRIN | ⭐⭐⭐⭐⭐ |
| **05** | [Advanced SQL, Window Functions & CTEs](./05-Advanced-SQL-Window-Functions-and-CTEs.md) | Execution order, Window frames (`ROWS` vs `RANGE`), Recursive CTEs, `LATERAL` joins, Cube/Rollup | ⭐⭐⭐⭐ |
| **06** | [Query Planner, Optimizer & Execution Plans](./06-Query-Planner-Optimizer-and-Explain.md) | CBO cost models, Join algorithms (Hash, Merge, Nested Loop), `EXPLAIN (ANALYZE, BUFFERS)`, `work_mem` | ⭐⭐⭐⭐⭐ |
| **07** | [Transactions, ACID, MVCC & Locking](./07-Transactions-ACID-MVCC-and-Locking.md) | 6 Anomalies, Snapshot Isolation vs SSI, Postgres bloat vs InnoDB Undo logs, Gap Locks, Deadlocks | ⭐⭐⭐⭐⭐ |
| **08** | [Schema Design, Modeling & Migrations](./08-Schema-Design-Modeling-and-Migrations.md) | 1NF-BCNF, UUIDv7 vs BIGINT, Financial `DECIMAL`, JSONB, Expand-Contract zero-downtime DDL | ⭐⭐⭐⭐ |
| **09** | [Replication, High Availability & Scaling](./09-Replication-High-Availability-and-Scaling.md) | WAL streaming vs Binlog, Read-after-write consistency, HikariCP sizing math, PgBouncer, Partitioning | ⭐⭐⭐⭐⭐ |
| **10** | [Senior Interview Q&A & Counter-Questions](./10-Senior-Backend-Interview-QA-and-Scenarios.md) | 40+ Senior/Principal Q&A, Naive answer → Trap → Masterclass answer, Black Friday triage | ⭐⭐⭐⭐⭐ |
| **11** | [Complex SQL Interview Drills & Solutions](./11-Complex-SQL-Interview-Drills.md) | 10 classic hard whiteboard SQL drills: Gaps & Islands, Sessionization, Hierarchies, Running Totals | ⭐⭐⭐⭐⭐ |

---

## 🧠 The 5 Invariants of Senior SQL Mastery

```text
1. THE DISK REALITY INVARIANT:
   Databases never read or write rows; they read and write Pages (8KB/16KB).
   Every millisecond of latency is caused by disk page seeks and cache misses.

2. THE WRITE-AHEAD LOG INVARIANT:
   Random I/O destroys write throughput. Durability is achieved via sequential WAL
   flushes (fsync); data pages are flushed lazily and asynchronously by checkpointers.

3. THE SELECTIVITY CUTOFF INVARIANT:
   An index is not always faster than a sequential scan. If a query matches >15% of a
   table, the optimizer intentionally ignores the index to avoid random I/O thrashing.

4. THE MVCC ASYMMETRY INVARIANT:
   Postgres stores old versions in the heap (requiring VACUUM to prevent bloat);
   MySQL InnoDB stores old versions in Undo Logs (requiring purge threads).

5. THE CONCURRENCY INVARIANT:
   Repeatable Read does NOT mean Serializable. Snapshot Isolation prevents Phantoms
   without locking, but still permits Write Skew.
```

---

## 🗓️ The 5-Week Interview Study Plan

### Week 0: SQL Fundamentals & Joins (the non-negotiable base)
- [ ] Read [Chapter 01: SQL Fundamentals, Data Types, Constraints & CRUD](./01-SQL-Fundamentals-Data-Types-Constraints-and-CRUD.md)
  - Memorise which sublanguages `TRUNCATE`, `MERGE`, `GRANT`, and `SAVEPOINT` belong to.
  - Know the six constraint families and the four `ON DELETE` actions cold.
  - Prove to yourself that `NOT IN` with a NULL returns zero rows.
  - Run every example against [`practice-schema.sql`](./practice-schema.sql).
- [ ] Read [Chapter 02: Joins, Set Operations & Subqueries](./02-Joins-Set-Operations-and-Subqueries.md)
  - Write all 25 drills in the Drill Bank without looking at the answers.
  - Explain why the Venn diagram is right about *which rows* and wrong about *how many*.
  - Rewrite every `NOT IN` you have ever written as `NOT EXISTS`.
  - Be able to derive "latest row per group" in three different ways, on a whiteboard.

### Week 1: Storage Mechanics & Index Physics
- [ ] Read [Chapter 03: Engine & Storage Internals](./03-Relational-Engine-and-Storage-Internals.md)
  - Memorize the 3 phases of the ARIES recovery algorithm.
  - Understand how the slot directory decouples row movement from secondary index pointers.
- [ ] Read [Chapter 04: Indexing Architecture & B+ Trees](./04-Indexing-Architecture-and-B-Trees.md)
  - Calculate tree height for 100M rows with 8KB pages.
  - Explain why UUIDv4 causes 50/50 page splits and why UUIDv7 eliminates them.
  - Practice identifying leftmost prefix violations.

### Week 2: Query Mastery & Plan Execution
- [ ] Read [Chapter 05: Advanced SQL, Window Functions & CTEs](./05-Advanced-SQL-Window-Functions-and-CTEs.md)
  - Memorize the 12-step logical execution order.
  - Practice Recursive CTEs with cycle detection.
- [ ] Read [Chapter 06: Query Planner & Execution Plans](./06-Query-Planner-Optimizer-and-Explain.md)
  - Learn to read `EXPLAIN (ANALYZE, BUFFERS)` without looking at guides.
  - Contrast the 3 physical join algorithms: Hash Join vs Sort-Merge vs Nested Loop.
  - Understand why memory spills to disk degrade query latency by 50x.

### Week 3: Concurrency, MVCC & Schema Migrations
- [ ] Read [Chapter 07: Transactions, ACID, MVCC & Locking](./07-Transactions-ACID-MVCC-and-Locking.md)
  - Explain Write Skew using the On-Call Doctor example.
  - Contrast PostgreSQL heap bloat with MySQL InnoDB Undo Log chains.
  - Trace how Gap Locks cause deadlocks on concurrent `SELECT ... FOR UPDATE`.
- [ ] Read [Chapter 08: Schema Design & Zero-Downtime Migrations](./08-Schema-Design-Modeling-and-Migrations.md)
  - Master the 4-phase Expand-Contract migration pattern.
  - Understand CPU struct alignment padding inside table rows.

### Week 4: Scaling, Interview Traps & Live Coding Drills
- [ ] Read [Chapter 09: Replication, High Availability & Scaling](./09-Replication-High-Availability-and-Scaling.md)
  - Master the HikariCP sizing formula (→(\text{Cores} \times 2) + \text{Spindles}→).
  - Explain the 3 solutions to the Read-Your-Own-Writes anomaly.
  - Contrast Declarative Partitioning with Horizontal Sharding.
- [ ] Practice [Chapter 10: Senior Q&A & Counter-Questions](./10-Senior-Backend-Interview-QA-and-Scenarios.md)
  - Rehearse the *Naive → Trap → Masterclass* answer flow for the top 15 questions.
- [ ] Code all 10 problems in [Chapter 11: Complex SQL Interview Drills](./11-Complex-SQL-Interview-Drills.md)
  - Solve Gaps & Islands and Sessionization without looking at the solutions.

---

## 🏆 The Top-10 Quick-Fire Interview Question Bank

1. **Why does `SELECT COUNT(*)` take several seconds in PostgreSQL on an unindexed table while taking milliseconds in MySQL MyISAM?**  
   *Answer:* MyISAM stored row counts in the table header, but lacked MVCC and transaction isolation. PostgreSQL uses MVCC; every transaction may have a different view of which rows are committed, requiring a visibility check on every tuple.
2. **What happens if a transaction in PostgreSQL stays open for 48 hours without committing or rolling back?**  
   *Answer:* It pins the global `xmin` horizon, preventing `autovacuum` from removing dead tuples across the entire database, causing massive table and index bloat, and threatening transaction ID wraparound shutdown.
3. **What is the difference between `ROWS` and `RANGE` in window frame specifications?**  
   *Answer:* `ROWS` counts discrete physical rows. `RANGE` evaluates duplicate ordering values as peers, calculating aggregates across all duplicates at once and defaulting to unbounded preceding.
4. **How does PostgreSQL's `CREATE INDEX CONCURRENTLY` work without locking writes?**  
   *Answer:* It executes two separate table scans: the first builds the index while concurrent writes continue logging to WAL, and the second scan catches up on concurrent mutations before making the index visible.
5. **Why should you never use `FLOAT` for financial balances?**  
   *Answer:* IEEE-754 binary floating-point representations cannot accurately represent base-10 fractions (e.g. 0.1), resulting in rounding discrepancies. Use `DECIMAL` / `NUMERIC`.
6. **When does a Hash Join perform an on-disk multi-batch spill?**  
   *Answer:* When the hash table constructed on the inner relation exceeds the allocated `work_mem`.
7. **What is an Index-Only Scan and what PostgreSQL structure determines its validity?**  
   *Answer:* A scan where all projected and filtered columns reside in the index. The **Visibility Map (VM)** must confirm that the heap page is `all-visible` to avoid reading the heap.
8. **Why does HikariCP recommend a connection pool of only 20-30 connections for a 16-core server?**  
   *Answer:* Exceeding physical core capacity forces the OS kernel to spend CPU cycles on thread context switching and cache invalidation rather than executing SQL.
9. **How do Gap Locks in MySQL InnoDB prevent Phantom Reads?**  
   *Answer:* By placing shared or exclusive locks on the open intervals between existing index records, blocking concurrent transactions from inserting new rows into the scanned range.
10. **What is the Expand-Contract pattern for zero-downtime column renames?**  
    *Answer:* A 4-phase rollout: Expand (add new column and dual-write trigger) → Backfill (async update historical data) → Switch Reads (deploy app reading new column) → Contract (drop old column and trigger).

---

## 🔗 Related Masterclasses
- **Database & Caching Root:** [DATABASE_CACHING/](../README.md)
- **High-Density Cheat Sheet:** [DATABASE_CACHING/QUICK-REFERENCE.md](../QUICK-REFERENCE.md)
- **Spring Boot Data JPA & Hibernate:** [SPRING_BOOT/04-Spring-Data-JPA-Hibernate/](../../SPRING_BOOT/04-Spring-Data-JPA-Hibernate/)
- **Spring Boot Database Optimization:** [SPRING_BOOT/11-Database-Design-and-Optimization/](../../SPRING_BOOT/11-Database-Design-Optimization/)
