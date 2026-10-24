# Database & Caching

## Overview

This area covers database technologies and caching strategies for Java and backend applications. Topics include SQL databases (PostgreSQL, MySQL), NoSQL databases (MongoDB, Cassandra, DynamoDB), query optimization, caching strategies (Redis, Memcached), and connection pooling.

> **Current state:** 01-SQL-Databases 🟢 11 chapters · 02-NoSQL-Databases 🟢 6 chapters · Query optimization and caching live in [SPRING_BOOT/11](../SPRING_BOOT/11-Database-Design-Optimization/) and [SPRING_BOOT/10](../SPRING_BOOT/10-Caching/).

## Quick Navigation

| # | Topic | Priority | Status |
|---|-------|----------|--------|
| 01 | [SQL Databases & Relational Mastery](./01-SQL-Databases/) | ⭐⭐⭐⭐⭐ | 🟢 **Complete (11 Chapters)** |
| 02 | [NoSQL Databases](./02-NoSQL-Databases/) | ⭐⭐⭐⭐ | 🟢 **Complete (6 Chapters)** |
| 03 | [Query Optimization](../SPRING_BOOT/11-Database-Design-Optimization/03-Query-Optimization-and-Execution-Plans.md) | ⭐⭐⭐⭐⭐ | 🟢 Covered in [Ch. 06](./01-SQL-Databases/06-Query-Planner-Optimizer-and-Explain.md) + Spring Boot 11 |
| 04 | [Caching Strategies](../SPRING_BOOT/10-Caching/) | ⭐⭐⭐⭐⭐ | 🟢 Complete in [Spring Boot 10-Caching](../SPRING_BOOT/10-Caching/) (4 chapters) |
| 05 | [Connection Pooling](./01-SQL-Databases/09-Replication-High-Availability-and-Scaling.md) | ⭐⭐⭐⭐ | 🟢 HikariCP sizing + PgBouncer in [Ch. 09](./01-SQL-Databases/09-Replication-High-Availability-and-Scaling.md) |

---

## 🚀 01 · SQL Databases Interview Ready Kit (Chapters)

The complete SQL & Relational Masterclass, from fundamentals to principal level (0–10+ years):

| # | Chapter | Key Highlights | Link |
|---|---------|----------------|------|
| **01** | **SQL Fundamentals, Data Types, Constraints & CRUD** | DDL/DQL/DML/DCL/TCL, keys, FK actions, upserts, three-valued logic, data type map | [Open Chapter](./01-SQL-Databases/01-SQL-Fundamentals-Data-Types-Constraints-and-CRUD.md) |
| **02** | **Joins, Set Operations & Subqueries** | Inner/Left/Right/Full/Cross/Self, semi & anti-joins, `UNION`/`INTERSECT`/`EXCEPT`, `EXISTS`, correlated subqueries | [Open Chapter](./01-SQL-Databases/02-Joins-Set-Operations-and-Subqueries.md) |
| **03** | **Relational Engine & Storage Internals** | Pages, Slot Directory, Clock-sweep, WAL, ARIES Recovery, Doublewrite Buffer | [Open Chapter](./01-SQL-Databases/03-Relational-Engine-and-Storage-Internals.md) |
| **04** | **Indexing Architecture & B+ Tree Physics** | Branching factor math, Page splits, Clustered vs Heap, Leftmost prefix, Covering `INCLUDE` | [Open Chapter](./01-SQL-Databases/04-Indexing-Architecture-and-B-Trees.md) |
| **05** | **Advanced SQL, Window Functions & CTEs** | Execution order, Window frames (`ROWS` vs `RANGE`), Recursive CTEs, `LATERAL` joins | [Open Chapter](./01-SQL-Databases/05-Advanced-SQL-Window-Functions-and-CTEs.md) |
| **06** | **Query Planner, Optimizer & Execution Plans** | CBO models, Join algorithms (Hash, Merge, Nested Loop), `EXPLAIN (ANALYZE, BUFFERS)` | [Open Chapter](./01-SQL-Databases/06-Query-Planner-Optimizer-and-Explain.md) |
| **07** | **Transactions, ACID, MVCC & Locking** | 6 Anomalies, Snapshot Isolation vs SSI, Postgres bloat vs InnoDB Undo logs, Gap Locks | [Open Chapter](./01-SQL-Databases/07-Transactions-ACID-MVCC-and-Locking.md) |
| **08** | **Schema Design, Modeling & Migrations** | 1NF-BCNF, UUIDv7 vs BIGINT, Financial `DECIMAL`, JSONB, Expand-Contract DDL | [Open Chapter](./01-SQL-Databases/08-Schema-Design-Modeling-and-Migrations.md) |
| **09** | **Replication, High Availability & Scaling** | WAL streaming vs Binlog, Read-after-write consistency, HikariCP sizing, PgBouncer | [Open Chapter](./01-SQL-Databases/09-Replication-High-Availability-and-Scaling.md) |
| **10** | **Senior Interview Q&A & Counter-Questions** | 40+ Senior/Principal Q&A, Naive answer → Trap → Masterclass answer | [Open Chapter](./01-SQL-Databases/10-Senior-Backend-Interview-QA-and-Scenarios.md) |
| **11** | **Complex SQL Interview Drills & Solutions** | 10 classic hard whiteboard SQL drills: Gaps & Islands, Sessionization, Hierarchies | [Open Chapter](./01-SQL-Databases/11-Complex-SQL-Interview-Drills.md) |

📄 **Practice Schema:** [`01-SQL-Databases/practice-schema.sql`](./01-SQL-Databases/practice-schema.sql) — runnable 6-table PostgreSQL script that every example in Chapters 01 & 02 executes against

👉 **[Open SQL Databases Master Guide & Study Plan](./01-SQL-Databases/README.md)**

---

## Learning Path

### Week 1: SQL Fundamentals & Joins (start here)
- [x] SQL sublanguages (DDL, DQL, DML, DCL, TCL), keys & constraints
- [x] Data types: `NUMERIC` vs `FLOAT`, `TIMESTAMPTZ` vs `TIMESTAMP`, `JSONB`
- [x] Full CRUD playbook: upserts, `RETURNING`, batched deletes, `MERGE`
- [x] Every join type: Inner, Left, Right, Full Outer, Cross, Self, `LATERAL`
- [x] Semi-joins & anti-joins: `EXISTS` vs `IN` vs `NOT IN` (the NULL trap)
- [x] Set operations: `UNION`/`UNION ALL`, `INTERSECT`, `EXCEPT`
- [x] Subqueries: scalar, derived tables, correlated, quantified
- [x] Three-valued logic: why `NULL = NULL` is UNKNOWN

### Week 2: SQL Databases & Engine Mechanics
- [x] Relational database concepts & page layouts
- [x] ACID properties, WAL, and ARIES recovery
- [x] Advanced SQL query fundamentals, window frames & recursive CTEs
- [x] Indexing strategies, B+ tree math, and covering indexes
- [x] PostgreSQL heap vs MySQL InnoDB clustered index specifics
- [x] Query optimization and `EXPLAIN (ANALYZE, BUFFERS)`
- [x] Transaction isolation levels, MVCC, and gap locking

### Week 3: NoSQL Databases — [02-NoSQL-Databases](./02-NoSQL-Databases/) 🟢
- [x] NoSQL families, use cases, BASE and the CAP/PACELC trade-offs — [Ch. 01](./02-NoSQL-Databases/01-NoSQL-Foundations-Data-Models-CAP-and-Consistency.md)
- [x] MongoDB document model, schema patterns and ESR indexing — [Ch. 02](./02-NoSQL-Databases/02-MongoDB-Document-Model-Schema-Patterns-and-Indexing.md)
- [x] Cassandra column-family model, tokens and `R+W>RF` — [Ch. 03](./02-NoSQL-Databases/03-Cassandra-Column-Family-Partitioning-and-Tunable-Consistency.md)
- [x] DynamoDB key-value model, WCU/RCU and single-table design — [Ch. 04](./02-NoSQL-Databases/04-DynamoDB-Key-Value-Design-Partition-Math-and-Single-Table-Modelling.md)
- [x] Redis, graph, search and time-series stores — [Ch. 05](./02-NoSQL-Databases/05-Redis-Graph-and-TimeSeries-NoSQL-Interview-Deep-Dive.md)
- [x] Selection matrix, polyglot persistence & Spring Data integration — [Ch. 06](./02-NoSQL-Databases/06-NoSQL-Selection-Polyglot-Persistence-and-Spring-Data-Integration.md)

### Week 4: Optimization & Caching
- [ ] Query optimization & `EXPLAIN (ANALYZE, BUFFERS)` — [Ch. 06](./01-SQL-Databases/06-Query-Planner-Optimizer-and-Explain.md)
- [ ] The N+1 query problem — [Ch. 06](./01-SQL-Databases/06-Query-Planner-Optimizer-and-Explain.md) + [Spring Boot 04-JPA](../SPRING_BOOT/04-Spring-Data-JPA-Hibernate/)
- [ ] Caching patterns (cache-aside, write-through, write-behind) — [Spring Boot 10-Caching](../SPRING_BOOT/10-Caching/01-Caching-Fundamentals-and-Patterns.md)
- [ ] Redis data structures, consistency & cache stampedes — [Spring Boot 10-Caching](../SPRING_BOOT/10-Caching/)

### Week 5: Advanced Topics
- [x] Connection pooling with HikariCP & PgBouncer — [Ch. 09](./01-SQL-Databases/09-Replication-High-Availability-and-Scaling.md)
- [x] Database replication and horizontal sharding — [Ch. 09](./01-SQL-Databases/09-Replication-High-Availability-and-Scaling.md)
- [x] Consistency models and read-your-own-writes — [Ch. 09](./01-SQL-Databases/09-Replication-High-Availability-and-Scaling.md)
- [ ] Cache invalidation strategies — [Spring Boot 10-Caching 03](../SPRING_BOOT/10-Caching/03-Cache-Consistency-Invalidation-and-Stampedes.md)

---

## Interview Tips by Experience Level

### Junior (0-2 years)
- Focus: SQL basics, CRUD operations, joins, basic indexing
- Depth: Write correct joins, understand primary/foreign keys, spot NULL traps
- Key Topics: SELECT, JOINs (Inner/Left), WHERE, GROUP BY, indexes, transactions, constraints

### Mid-Level (2-5 years)
- Focus: Query optimization, NoSQL basics, caching patterns
- Depth: Optimize queries, choose right database, implement caching
- Key Topics: Explain plans, N+1 problem, Redis, cache patterns

### Senior / Staff (5-10+ years)
- Focus: Engine internals, MVCC, B+ tree page splits, isolation anomalies, distributed scaling
- Depth: Explain WAL/ARIES, analyze execution plans (`EXPLAIN BUFFERS`), debug deadlocks, design zero-downtime migrations
- Key Topics: Sharding, replication lag mitigation, HikariCP sizing math, Write Skew, Gap Locks

---

## Most Asked Topics

1. **SQL Fundamentals & Joins** - DDL/DML, every join type, anti/semi-joins, set ops, subqueries
2. **Advanced Analytics** - Execution order, Window Functions, Recursive CTEs, `LATERAL`
3. **Indexing Physics** - B-tree mechanics, page splits, composite leftmost prefix, covering indexes, GIN/BRIN
4. **Transactions & MVCC** - ACID, 6 concurrency anomalies, Snapshot Isolation vs SSI, deadlocks, gap locks
5. **Query Optimization** - CBO, Hash vs Merge vs Nested Loop joins, `EXPLAIN (ANALYZE, BUFFERS)`
6. **High Availability & Scale** - Primary-Replica, WAL streaming, HikariCP sizing formula, PgBouncer, Sharding
7. **NoSQL Databases** - Document, key-value, wide-column, graph, CAP/PACELC → [02-NoSQL-Databases](./02-NoSQL-Databases/)
8. **Caching Strategies** - Cache-aside, write-through, write-behind, Redis data structures → [Spring Boot 10-Caching](../SPRING_BOOT/10-Caching/)

---

*PostgreSQL 15+ | MySQL 8.0+ | Redis 7+*
