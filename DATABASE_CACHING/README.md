# Database & Caching

## Overview

This area covers database technologies and caching strategies for Java and backend applications. Topics include SQL databases (PostgreSQL, MySQL), NoSQL databases (MongoDB, Cassandra, DynamoDB), query optimization, caching strategies (Redis, Memcached), and connection pooling.

## Quick Navigation

| # | Topic | Priority | Status |
|---|-------|----------|--------|
| 01 | [SQL Databases & Engine Architecture](./01-SQL-Databases/) | ⭐⭐⭐⭐⭐ | 🟢 **Complete (9 Chapters)** |
| 02 | [NoSQL Databases](./02-NoSQL-Databases/) | ⭐⭐⭐⭐ | 🚧 Planned |
| 03 | [Query Optimization](./03-Query-Optimization/) | ⭐⭐⭐⭐⭐ | 🚧 Planned |
| 04 | [Caching Strategies](./04-Caching-Strategies/) | ⭐⭐⭐⭐⭐ | 🚧 Planned |
| 05 | [Connection Pooling](./05-Connection-Pooling/) | ⭐⭐⭐ | 🚧 Planned |

---

## 🚀 01 · SQL Databases Interview Ready Kit (Chapters)

The complete deep-dive SQL and Relational Engine Masterclass for experienced backend engineers (3–10+ years):

| # | Chapter | Key Highlights | Link |
|---|---------|----------------|------|
| **01** | **Relational Engine & Storage Internals** | Pages, Slot Directory, Clock-sweep, WAL, ARIES Recovery, Doublewrite Buffer | [Open Chapter](./01-SQL-Databases/01-Relational-Engine-and-Storage-Internals.md) |
| **02** | **Indexing Architecture & B+ Tree Physics** | Branching factor math, Page splits, Clustered vs Heap, Leftmost prefix, Covering `INCLUDE` | [Open Chapter](./01-SQL-Databases/02-Indexing-Architecture-and-B-Trees.md) |
| **03** | **Advanced SQL, Window Functions & CTEs** | Execution order, Window frames (`ROWS` vs `RANGE`), Recursive CTEs, `LATERAL` joins | [Open Chapter](./01-SQL-Databases/03-Advanced-SQL-Window-Functions-and-CTEs.md) |
| **04** | **Query Planner, Optimizer & Execution Plans** | CBO models, Join algorithms (Hash, Merge, Nested Loop), `EXPLAIN (ANALYZE, BUFFERS)` | [Open Chapter](./01-SQL-Databases/04-Query-Planner-Optimizer-and-Explain.md) |
| **05** | **Transactions, ACID, MVCC & Locking** | 6 Anomalies, Snapshot Isolation vs SSI, Postgres bloat vs InnoDB Undo logs, Gap Locks | [Open Chapter](./01-SQL-Databases/05-Transactions-ACID-MVCC-and-Locking.md) |
| **06** | **Schema Design, Modeling & Migrations** | 1NF-BCNF, UUIDv7 vs BIGINT, Financial `DECIMAL`, JSONB, Expand-Contract DDL | [Open Chapter](./01-SQL-Databases/06-Schema-Design-Modeling-and-Migrations.md) |
| **07** | **Replication, High Availability & Scaling** | WAL streaming vs Binlog, Read-after-write consistency, HikariCP sizing, PgBouncer | [Open Chapter](./01-SQL-Databases/07-Replication-High-Availability-and-Scaling.md) |
| **08** | **Senior Interview Q&A & Counter-Questions** | 40+ Senior/Principal Q&A, Naive answer → Trap → Masterclass answer | [Open Chapter](./01-SQL-Databases/08-Senior-Backend-Interview-QA-and-Scenarios.md) |
| **09** | **Complex SQL Interview Drills & Solutions** | 10 classic hard whiteboard SQL drills: Gaps & Islands, Sessionization, Hierarchies | [Open Chapter](./01-SQL-Databases/09-Complex-SQL-Interview-Drills.md) |

👉 **[Open SQL Databases Master Guide & Study Plan](./01-SQL-Databases/README.md)**

---

## Learning Path

### Week 1-2: SQL Databases & Engine Mechanics
- [x] Relational database concepts & page layouts
- [x] ACID properties, WAL, and ARIES recovery
- [x] Advanced SQL query fundamentals, window frames & recursive CTEs
- [x] Indexing strategies, B+ tree math, and covering indexes
- [x] PostgreSQL heap vs MySQL InnoDB clustered index specifics
- [x] Query optimization and `EXPLAIN (ANALYZE, BUFFERS)`
- [x] Transaction isolation levels, MVCC, and gap locking

### Week 3: NoSQL Databases
- [ ] NoSQL types and use cases
- [ ] CAP theorem
- [ ] MongoDB document model
- [ ] Cassandra column-family model
- [ ] DynamoDB key-value model

### Week 4: Optimization & Caching
- [ ] Query optimization techniques
- [ ] Explain plans and query analysis
- [ ] N+1 query problem
- [ ] Caching patterns (cache-aside, write-through)
- [ ] Redis and Memcached

### Week 5: Advanced Topics
- [x] Connection pooling with HikariCP & PgBouncer
- [x] Database replication and horizontal sharding
- [x] Consistency models and read-your-own-writes
- [ ] Cache invalidation strategies

---

## Interview Tips by Experience Level

### Junior (0-2 years)
- Focus: SQL basics, CRUD operations, basic indexing
- Depth: Write queries, understand primary/foreign keys
- Key Topics: SELECT, JOIN, WHERE, indexes, transactions

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

1. **SQL Fundamentals & Advanced Analytics** - Execution order, Joins, Window Functions, Recursive CTEs
2. **Indexing Physics** - B-tree mechanics, page splits, composite leftmost prefix, covering indexes, GIN/BRIN
3. **Transactions & MVCC** - ACID, 6 concurrency anomalies, Snapshot Isolation vs SSI, deadlocks, gap locks
4. **Query Optimization** - CBO, Hash vs Merge vs Nested Loop joins, `EXPLAIN (ANALYZE, BUFFERS)`
5. **High Availability & Scale** - Primary-Replica, WAL streaming, HikariCP sizing formula, PgBouncer, Sharding
6. **NoSQL Databases** - Document stores, key-value stores, CAP theorem
7. **Caching Strategies** - Cache-aside, write-through, write-behind, Redis data structures

---

*PostgreSQL 15+ | MySQL 8.0+ | Redis 7+*
