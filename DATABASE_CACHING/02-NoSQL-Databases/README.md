# 02 · NoSQL Databases — Interview Ready Kit

> **Level:** Beginner → Principal / Staff Backend Engineer (0–10+ Years Experience)  
> **Interview Frequency:** ★★★★☆ (mandatory wherever the shop is not pure-RDBMS)  
> **Focus Systems:** MongoDB, Apache Cassandra, Amazon DynamoDB, Redis, Neo4j, Elasticsearch  
> **Last Updated:** 2026  
> **Chapters:** 6

---

## 🎯 Executive Overview

This kit covers the non-relational half of the database interview — not as "SQL's replacement" but as a **different bargain**: each NoSQL family relaxes part of the relational model (joins, fixed schema, single-node ACID, strong consistency) in exchange for scale, latency, or data-shape fit.

It starts from theory (CAP/PACELC, consistency models) and lands on concrete engine mechanics (LSM trees, token rings, partition math, 16384 hash slots) and on the Java side (Spring Data MongoDB/Cassandra, AWS SDK v2 Enhanced Client, Spring Cache + Redis).

**Where do I start?**
- 0–2 years → **Chapter 01** (families, CAP, consistency), then skim **02**.
- 2–5 years → **01 → 02** (MongoDB) or **03** (Cassandra), depending on your shop.
- 5–10+ years → **04** (DynamoDB single-table + partition math) and **06** (selection + polyglot + CQRS).

---

## 📚 Master Chapter Index

| Chapter | Title | Core Focus Areas | Difficulty |
|:---:|---|---|:---:|
| **01** | [NoSQL Foundations, Data Models & Consistency Theory](./01-NoSQL-Foundations-Data-Models-CAP-and-Consistency.md) | 4+1 families, BASE, CAP stated correctly, PACELC, consistency ladder, linearizable vs serializable | ⭐⭐⭐ |
| **02** | [MongoDB — Document Model, Schema Patterns & Indexing](./02-MongoDB-Document-Model-Schema-Patterns-and-Indexing.md) | Embed vs reference, 12 schema patterns, ESR rule, multikey cost, `$lookup`, write/read concerns, shard keys, Spring Data | ⭐⭐⭐⭐ |
| **03** | [Cassandra — Column-Family, Partitioning & Tunable Consistency](./03-Cassandra-Column-Family-Partitioning-and-Tunable-Consistency.md) | Query-first modelling, ring/tokens, LSM write path, `R+W>RF`, tombstones, LWT/Paxos, TWCS, Spring Data | ⭐⭐⭐⭐ |
| **04** | [DynamoDB — Key-Value Design, Partition Math & Single-Table Modelling](./04-DynamoDB-Key-Value-Design-Partition-Math-and-Single-Table-Modelling.md) | GSI vs LSI, WCU/RCU math, 3000/1000 partition ceilings, write-sharding, conditional writes, single-table design, SDK v2 | ⭐⭐⭐⭐⭐ |
| **05** | [Redis, Graph & Time-Series Stores](./05-Redis-Graph-and-TimeSeries-NoSQL-Interview-Deep-Dive.md) | Data structures, `SCAN` vs `KEYS`, RDB/AOF, Sentinel/Cluster, eviction LRU vs LFU, Neo4j traversal, Elasticsearch inverted index, TimescaleDB | ⭐⭐⭐ |
| **06** | [NoSQL Selection, Polyglot Persistence & Spring Data Integration](./06-NoSQL-Selection-Polyglot-Persistence-and-Spring-Data-Integration.md) | Selection framework, decision matrix, CQRS + outbox, migration strategy, Spring production configs, cross-cutting concerns | ⭐⭐⭐⭐ |

---

## 🧠 The 5 Invariants of NoSQL Mastery

```text
1. THE ACCESS-PATTERN INVARIANT:
   NoSQL key design is a query plan, not a data model.
   If you cannot express your query by the key, the schema is wrong —
   not the engine.

2. THE PARTITION INVARIANT:
   Every store shards by a key you choose. Bad key = hot partition =
   10% utilisation with 100% throttling. Distribution is a design
   decision made at write time, not a knob turned at 3 a.m.

3. THE CAP INVARIANT:
   During a partition you choose Consistency or Availability. Not once,
   at design time — per operation, for the life of the system.

4. THE PACELC INVARIANT:
   Partition behaviour is rare; normal-operation behaviour is constant.
   If not partitioned, you may still trade Latency for Consistency
   (QUORUM, majority commit, cross-AZ replication).

5. THE DERIVED-STATE INVARIANT:
   Indexes, projections, caches and search clusters are disposable.
   There is exactly one system of record, and everything else must be
   rebuildable from it.
```

---

## 🗓️ The 2-Week NoSQL Study Plan

### Week 1: Theory + One Engine, Deep
- [ ] Read [Chapter 01](./01-NoSQL-Foundations-Data-Models-CAP-and-Consistency.md)
  - Be able to state CAP correctly and explain PACELC.
  - Define linearizable, serializable, and causal consistency in one sentence each.
- [ ] Read the engine chapter **your shop actually uses**:
  - [ ] [Chapter 02 · MongoDB](./02-MongoDB-Document-Model-Schema-Patterns-and-Indexing.md)
  - [ ] [Chapter 03 · Cassandra](./03-Cassandra-Column-Family-Partitioning-and-Tunable-Consistency.md)
  - [ ] [Chapter 04 · DynamoDB](./04-DynamoDB-Key-Value-Design-Partition-Math-and-Single-Table-Modelling.md)
- [ ] Do these drills for your engine:
  - Derive a partition/shard key for three real queries and state why it does not hot-spot.
  - Write the "no oversell" operation from scratch (conditional write / LWT).
  - Compute WCU/RCU, or `R+W>RF`, for a given item size and consistency level.

### Week 2: Other Engines + Selection & Spring
- [ ] Read [Chapter 05](./05-Redis-Graph-and-TimeSeries-NoSQL-Interview-Deep-Dive.md)
  - Explain why `KEYS *` takes down a Redis instance.
  - Justify LRU vs LFU for a specific cache.
- [ ] Read [Chapter 06](./06-NoSQL-Selection-Polyglot-Persistence-and-Spring-Data-Integration.md)
  - Answer "which store for X?" in the selection order: pattern → consistency → scale → ops.
  - Draw a CQRS read model behind a Postgres write model, with the outbox.
- [ ] Rehearse the reference architecture in Chapter 06 out loud.

---

## 🏆 The Top-10 Quick-Fire Interview Questions

1. **Is NoSQL eventually consistent or strongly consistent?** — Both, per operation. Cassandra's `QUORUM`, DynamoDB's `ConsistentRead`, MongoDB's `majority`. Name the operation, not the product.
2. **State the CAP theorem correctly.** — During a partition you choose Consistency or Availability. "Pick 2 of 3" is the misleading version Brewer himself corrected.
3. **What is PACELC?** — Partition → A/C trade; Else (normal) → L/C trade. It describes the everyday latency cost, not just failure behaviour.
4. **How do you prevent overselling in DynamoDB/Cassandra?** — Conditional write: `SET stock = stock - 1 IF stock >= 1`. Atomic, optimistic, no distributed lock.
5. **Why can't Cassandra join?** — Data is distributed by partition key across nodes; the rows a join needs live elsewhere. You denormalise into one table per query.
6. **What is an ESR index?** — Equality, Sort, Range — compound index field order in MongoDB; range must come last.
7. **How do you fix a hot partition?** — You cannot scale out of it; you shard the key (write-sharding), move the item, or re-key. Capacity does not help.
8. **What is the DynamoDB WCU of a 6KB write?** — `ceil(6/1) = 6 WCU`. Know the 1KB write / 4KB strong / 8KB eventual block sizes.
9. **What does Redis lose on Sentinel failover?** — Acknowledged writes can vanish, because replication is asynchronous. That is why money is not in Redis.
10. **How do you keep Elasticsearch consistent with Postgres?** — You don't dual-write: CDC/outbox ordered events, idempotent re-index, and the index is disposable.

---

## 🔗 Related Masterclasses
- **Database & Caching Root:** [../README.md](../README.md)
- **SQL Masterclass (11 chapters):** [../01-SQL-Databases/](../01-SQL-Databases/)
- **Caching patterns with Redis in Spring:** [../../SPRING_BOOT/10-Caching/](../../SPRING_BOOT/10-Caching/)
- **Spring Data JPA & Hibernate:** [../../SPRING_BOOT/04-Spring-Data-JPA-Hibernate/](../../SPRING_BOOT/04-Spring-Data-JPA-Hibernate/)
