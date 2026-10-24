# 01 · NoSQL Foundations, Data Models & Consistency Theory

> **Level:** Beginner → Senior (the theory every NoSQL question starts from)  
> **Interview Frequency:** ★★★★☆ (asked in nearly every backend loop that touches non-RDBMS systems)  
> **Focus Systems:** MongoDB, Apache Cassandra, Amazon DynamoDB, Redis, Neo4j  
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — Why NoSQL Exists](#1-what--why--why-nosql-exists)
- [2. The Four (+1) NoSQL Data Model Families](#2-the-four-1-nosql-data-model-families)
- [3. ACID vs BASE — The Correct Framing](#3-acid-vs-base--the-correct-framing)
- [4. CAP Theorem — The Truth (Not "Pick 2 of 3")](#4-cap-theorem--the-truth-not-pick-2-of-3)
- [5. PACELC — The Theorem Interviewers Actually Want](#5-pacelc--the-theorem-interviewers-actually-want)
- [6. Consistency Models — The Ladder](#6-consistency-models--the-ladder)
- [7. Linearizability vs Serializability vs Causal Consistency](#7-linearizability-vs-serializability-vs-causal-consistency)
- [8. The Consistency Decision Matrix](#8-the-consistency-decision-matrix)
- [9. Data Model Deep Comparison](#9-data-model-deep-comparison)
- [10. Level-Wise Interview Questions & Answers](#10-level-wise-interview-questions--answers)
- [11. Counter-Questions & Interviewer Traps](#11-counter-questions--interviewer-traps)
- [12. Rapid-Fire MCQs](#12-rapid-fire-mcqs)
- [13. One-Page Cheat Sheet](#13-one-page-cheat-sheet)
- [14. Real-World Production Post-Mortems](#14-real-world-production-post-mortems)
- [15. Follow-up Learning](#15-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

1. **NoSQL is not "SQL without SQL"** — it is a family of databases that relax **one or more** of: the relational model, fixed schema, ACID transactions, or strong consistency, in exchange for horizontal scale, low latency, or flexible data shapes.
2. **"CAP: pick any 2" is a simplification Eric Brewer himself called misleading.** The accurate statement: **when a network partition occurs, you must choose between consistency and availability.** When there is no partition you can have both — which is what a single-node RDBMS does.
3. **PACELC is the theorem senior interviewers probe for:** if there is a **P**artition, you trade **A**vailability vs **C**onsistency; **E**lse (normal operation) you trade **L**atency vs **C**onsistency. Every NoSQL database sits somewhere on this line.
4. **Eventual consistency ≠ weak.** Cassandra at `QUORUM` and MongoDB with `majority` write/read concerns give *tunably strong* behaviour; the model just makes the trade-off explicit instead of hidden.
5. **The five families:** **key-value** (DynamoDB, Redis), **document** (MongoDB, Couchbase), **wide-column** (Cassandra, HBase, ScyllaDB), **graph** (Neo4j, Neptune), and **search/analytics** (Elasticsearch, OpenSearch) — each optimises a different access pattern.
6. **There are no joins in Cassandra/DynamoDB.** Query-first modelling is mandatory: you design the table *for the query*, and denormalise deliberately. This is the single biggest mental shift from SQL.
7. **"Schemaless" is a trap.** Flexible schema means *no enforced* schema — which means **your application** must enforce it, version it, and migrate every existing document. Every NoSQL data-quality incident traces back to this.

---

## 1. What & Why — Why NoSQL Exists

The relational model was designed in 1970 for **data integrity on a single machine**. Four decades later, internet-scale workloads hit limits it was never designed for:

| Relational assumption | Internet-scale reality |
|---|---|
| Data fits on one machine | Data is petabytes |
| Scale **up** (bigger box) | Scale **out** (commodity nodes) |
| Schema designed once, stable | Schema evolves weekly; semi-structured logs/events |
| Joins are cheap | Joins across shards are catastrophic |
| ACID, one consistent snapshot | Globally distributed users, 100ms budget |

NoSQL databases answer this with a different bargain, not a "better" one:

```text
RDBMS:    Integrity + Flexibility of query  →  paid for with scaling complexity
NoSQL:    Scale + Predictable latency       →  paid for with modelling discipline
                                                      and weakened guarantees
```

> **The senior answer:** *"I don't choose NoSQL because it's fashionable. I choose it when the dominant access pattern is a single-key lookup at scale, or the data is naturally a tree/document, or the write volume exceeds what one primary can absorb. I keep my accounting ledger in Postgres and my shopping carts in DynamoDB — polyglot persistence."*

---

## 2. The Four (+1) NoSQL Data Model Families

```
+----------------+---------------------------+---------------------------+---------------------------+
| FAMILY         | DATA MODEL                | QUERY PRIMITIVE          | EXAMPLES                  |
+----------------+---------------------------+---------------------------+---------------------------+
| Key-Value      | opaque value at a key     | GET(key), range(key)     | DynamoDB, Redis, Riak     |
| Document       | nested JSON/BSON docs     | by field incl. nested    | MongoDB, Couchbase, Fauna |
| Wide-Column    | rows keyed by partition,  | by partition key only    | Cassandra, HBase, Scylla  |
|                | grouped into column       | (clustering cols order)  |                           |
|                | families                  |                           |                           |
| Graph          | nodes + typed edges       | traverse edges           | Neo4j, Amazon Neptune     |
| Search/        | inverted index of docs    | relevance scoring, agg   | Elasticsearch, OpenSearch |
|   Analytics    |                           |                           |                           |
+----------------+---------------------------+---------------------------+---------------------------+
```

### 2.1 Key-value stores
The simplest model: `GET(key) → value`, `PUT(key, value)`. The value is **opaque** — the database cannot inspect it. This is why it scales best and why it supports no secondary queries. Redis is "key-value" but with *structured* values (hashes, sorted sets) — it is more precisely a **data-structure server**, and it sits in memory, making it the primary caching tool rather than a system of record.

### 2.2 Document stores
Each record is a self-describing document (JSON in Couchbase/Fauna, BSON in MongoDB). Fields and nested structures can differ between documents, and _secondary indexes can be built on any field_, so you can query by nested attributes. This is the most "relational-like" NoSQL family: the closest thing to a table is a collection, and `$lookup` even offers a left outer join.

### 2.3 Wide-column stores
**Not a columnar/analytic database** — the name is historical and confuses everyone (this is the #1 terminology interview answer). Data is a sparse, two-dimensional map: rows are grouped by a **partition key**, each row contains arbitrary columns. Physically it is a **distributed sorted map**: partition key → hash → node; clustering columns sort the row *within* the partition. You **must** know your partition key; there is no ad-hoc querying.

### 2.4 Graph stores
Nodes (entities) and edges (relationships, both directional and typed, both carrying properties). Optimised for **variable-depth traversal** ("friends of friends who bought X"). A graph database performs this in constant time per hop, whereas SQL recursive CTEs re-scan at each depth — a difference that is academic at depth 3 and catastrophic at depth 6.

### 2.5 Search / analytics engines
Inverted indexes with relevance scoring (BM25/TF-IDF), aggregations, and near-real-time ingestion. They are a **query layer over a system of record**, not usually a system of record themselves (re-indexing cost, no strong transactions).

---

## 3. ACID vs BASE — The Correct Framing

NoSQL is often described as "BASE instead of ACID". Accept that framing only loosely:

| ACID (relational) | BASE (common NoSQL default) |
|---|---|
| **A**tomicity | **B**asically **A**vailable — system keeps responding during failures |
| **C**onsistency | **S**oft state — state may change over time without input |
| **I**solation | **E**ventual consistency — replicas converge if writes stop |
| **D**urability | |

**The nuance senior candidates must add:** BASE describes the *availability-first* extreme, not a universal law of NoSQL:
- **MongoDB** provides multi-document ACID transactions (4.0+) and snapshot isolation.
- **Cassandra** offers **LWT** (lightweight transactions) = compare-and-set = linearizable consistency per a row (at ~4 round trips).
- **DynamoDB** provides ACID transactions across up to 100 items.
- **Tunably strong consistency** exists everywhere — the defaults are just availability-first.

---

## 4. CAP Theorem — The Truth (Not "Pick 2 of 3")

The Gilbert–Lynch proof states that in an **asynchronous network** with unreliable message delivery, it is impossible to simultaneously provide all three of:

- **C — Consistency:** every read receives the most recent write *or* an error (linearizability of reads/writes).
- **A — Availability:** every request receives a non-error response, without guarantee it contains the most recent write.
- **P — Partition tolerance:** the system continues to operate despite an arbitrary number of dropped/delayed messages between nodes.

### 4.1 The precise statement

```text
                     NO PARTITION
                          |
   Consistency + Availability   (single-node RDBMS, single-rack cluster)
                          |
                     PARTITION HAPPENS
                          |
            +-------------+-------------+
            |                           |
     CP systems                    AP systems
     (reject writes to             (serve possibly stale
      preserve correctness)         reads to stay up)
      e.g. etcd, ZooKeeper,        e.g. Cassandra default,
           HBase, MongoDB CP          DynamoDB default,
           configs                    Riak
```

### 4.2 What the theorem does **not** say

| Myth | Reality |
|---|---|
| "Databases pick 2 of 3 at design time." | Brewer's 2012 clarification: this framing is misleading. Partitions are a *runtime event*, and the choice is *per operation*, not per product. |
| "NoSQL is AP, so it cannot be consistent." | Cassandra's `QUORUM`, MongoDB's `majority` read/write concern, and DynamoDB's strongly-consistent reads are all available. |
| "A CA database is a 'better' RDBMS." | "CA" simply means **partitions do not occur** — true for a single node, false for any distributed system. A single-node Postgres is CA *by virtue of not being distributed*. |
| "P is optional." | P is non-negotiable for any multi-node system; you only ever *choose* between C and A when P happens. |

> **Screen-ready answer:** *"CAP says that during a partition you must choose. I don't choose between 'available' and 'consistent' globally — I choose per operation. My product catalogue can serve slightly stale reads at ONE; my order creation uses QUORUM + a lightweight transaction. Cassandra gives me both levers; that's tunable consistency, not a compromise baked into the product."*

---

## 5. PACELC — The Theorem Interviewers Actually Want

CAP only describes behaviour **during failures**. PACELC (Abadi, 2012) extends it to normal operation, which is where you live 99.99% of the time:

```text
if Partition:
    choose Availability  vs  Consistency        (the CAP trade)
Else (normal operation):
    choose Latency       vs  Consistency        (the PACELC trade)
```

**Where real systems sit:**

| System | Class | Meaning |
|---|---|---|
| **DynamoDB (default)** | PA/EL | During a partition, stays available; normally returns in single-digit ms with eventual consistency |
| **Cassandra (`ONE`)** | PA/EL | One replica ack, no read repair → lowest latency |
| **Cassandra (`QUORUM`)** | PA/EC-ish (PC-ish on failure) | Two replicas ack → strong-ish but adds a round trip |
| **MongoDB (majority)** | PC/EC | Journal-majority commit; trade is latency, and it blocks lagging nodes |
| **etcd / ZooKeeper** | PC/EC | Raft/ZAB; consistency even in partitions → coordinator bottle-neck |
| **Single-node Postgres** | CA + EC locally | No partitions; strong, but a single point of failure |

**Interview answer:** *"CAP describes failure behaviour; PACELC describes the everyday cost. Both DynamoDB and Cassandra are PA/EL by default, which is why their latency profile is flat and their reads can be stale. MongoDB is PC/EC — more consistent, higher tail latency because writes wait for a majority. PostgreSQL is 'E' — every commit is fully consistent, and the price is a single primary."*

---

## 6. Consistency Models — The Ladder

These are the guarantees a *store* makes about read ordering. Ordered from weakest to strongest:

| Model | Guarantee | Where it exists |
|---|---|---|
| **Eventual** | If writes stop, replicas converge; reads may be stale *indefinitely* during writes | DynamoDB default, Cassandra default |
| **Causal** | Causally-related operations are seen in order; concurrent ones may be unordered | Cassandra doesn't natively; Spanner/MongoDB approx., Riak |
| **Read-your-writes (session)** | A client always sees its own previous writes | DynamoDB strongly-consistent reads, Cassandra `QUORUM` + `QUORUM`, Spanner |
| **Monotonic reads** | Once you see a value, you never see an older one | Cassandra repair + `QUORUM` reads; Spanner |
| **Monotonic writes** | Writes from the same client are applied in order | Cassandra uses client timestamps |
| **Consistent prefix** | Reads see a causal prefix of the write history | Stronger than eventual |
| **Linearizable** | All operations appear to execute atomically at a single point in time | Cassandra LWT (`IF`), etcd, Spanner true-time, single-node RDBMS |
| **Serializable** | Transactions behave as if executed one at a time | Postgres SERIALIZABLE, CockroachDB, MongoDB multi-doc txns |

---

## 7. Linearizability vs Serializability vs Causal Consistency

The most-asked consistency question at senior level. Get these three definitions exactly right:

```
LINEARIZABILITY     — single-object, real-time ordering.
                      Every op appears to take effect instantly between its
                      invocation and response, at a single point on the timeline.
                      "Did my read see the latest write?"

SERIALIZABILITY     — multi-object, transactional isolation.
                      A group of ops behaves as if executed in *some* serial order;
                      conflicting transactions never interleave.
                      "Did my two-account transfer run atomically against
                       concurrent transfers?"

CAUSAL CONSISTENCY   — relative ordering of *related* events only.
                      If A → B (A causes B), everyone sees A before B.
                      Concurrent ops (unrelated) can be seen in any order.
```

**Critical:** they are **independent** — a system can be linearizable but not serializable (a linearizable register, no multi-object atomicity), and serializable but not linearizable (strict serializable ordering without real-time guarantees).

**How real stores provide them:**

| Store | Linearizability | Serializability |
|---|---|---|
| Cassandra | **LWT** (`IF NOT EXISTS`, `IF col = x`) — Paxos-based, ~4 RTT, **do not use on hot paths** | No multi-partition transactions |
| MongoDB | Single-document ops are linearizable | **Multi-document transactions** (4.0+), snapshot isolation, shard-aware in 4.2+ |
| DynamoDB | Strongly-consistent reads (single item) | **Transactions** across ≤100 items; conditional writes = LWT |
| PostgreSQL | Full linearizability (single primary) | Full (SSI at SERIALIZABLE) |
| Spanner | **External consistency = linearizability + serializability** via TrueTime | True serializable |

---

## 8. The Consistency Decision Matrix

Use this to answer "which consistency does my feature need?" in an interview:

| Feature | Required model | Typical store config |
|---|---|---|
| Product catalogue read | Eventual (staleness OK) | DynamoDB eventually-consistent, Cassandra `ONE` |
| Shopping cart | Session (read-your-writes) + conflict resolution | DynamoDB with strongly-consistent reads or conditional writes |
| Inventory decrement (must not oversell) | Linearizable counter | Cassandra LWT / DynamoDB conditional write / Postgres `UPDATE ... WHERE qty > 0` |
| Order status page | Read-your-writes for the buyer; eventual for others | MongoDB `majority` r/w |
| Bank ledger | Serializable + durable | PostgreSQL, CockroachDB, Spanner |
| Like/follower counts | Eventual / CRDT | Redis HINCRBY, Cassandra counters (approximate), DynamoDB atomic counters |
| User profile | Read-your-writes | Any store with session consistency |

> **The interview sentence:** *"I pick the weakest model that still makes the feature correct, because every step up the ladder costs latency or throughput. Most of my reads are eventual; only the inventory decrement needs linearizability, and that's a single conditional write, not a distributed transaction."*

---

## 9. Data Model Deep Comparison

| Dimension | MongoDB | Cassandra | DynamoDB | Redis |
|---|---|---|---|---|
| **Family** | Document | Wide-column | Key-value | Data-structure server |
| **Language** | MQL / aggregation | CQL | PartiQL / SDK API | Redis commands |
| **Write path** | WiredTiger journal/WAL | Commit log → memtable → SSTable | Partitioned LSM, 3 AZ replication | Memory (+ AOF/RDB) |
| **Joins** | ✅ `$lookup` (left outer) | ❌ none | ❌ none | ❌ none |
| **Secondary indexes** | ✅ any field, multikey, partial | ⚠ limited (SAI in 5.0; avoid) | ✅ GSI (own partition key) | ❌ none |
| **Transactions** | ✅ multi-doc, snapshot | ⚠ LWT only (per row) | ✅ ≤100 items | ✅ MULTI/EXEC + Lua |
| **Consistency default** | Strong (primary) | Tunable (`ONE`) | Eventual | Strong (single node) |
| **Scale-out model** | Sharding (ranged/hashed) | Peer-to-peer ring, linear | Managed partitions | Cluster: 16384 slots |
| **Ad-hoc queries** | ✅ rich | ❌ partition-key-first | ⚠ via GSI only | ❌ |
| **Best for** | Content, catalogues, user profiles | Time-series, event logs, IoT, messaging | Shopping carts, session, gaming | Cache, counters, leaderboards, pub/sub |
| **Watch out for** | Unbounded docs/arrays, hot shards | Tombstones, unbounded partitions, `ALLOW FILTERING` | Hot partitions, item size limits, denormalisation discipline | Not a system of record (durability trade-offs) |

---

## 10. Level-Wise Interview Questions & Answers

### 🟢 Junior (0–2 Years)

**Q1. What is NoSQL and how does it differ from SQL?**
A: NoSQL databases store and retrieve data using models other than the fixed relational table — documents, key-values, wide columns, or graphs. They typically trade part of ACID or strong consistency for horizontal scalability, flexible schemas, and predictable latency. SQL keeps strict schemas and rich joins; NoSQL pushes modelling work into the application.

**Q2. Name four types of NoSQL databases with an example of each.**
A: Key-value (Redis, DynamoDB), document (MongoDB, Couchbase), wide-column (Cassandra, HBase), graph (Neo4j, Neptune). Search engines (Elasticsearch) are a fifth, specialised family.

**Q3. What does "eventual consistency" mean?**
A: If writes stop, all replicas will eventually converge to the same value. During active writes, a read may return a stale value. It does not mean "random" or "broken" — it means the system converges and clients can choose how strong they need reads to be.

**Q4. Does NoSQL support transactions?**
A: It depends on the engine. MongoDB supports multi-document ACID transactions, DynamoDB supports transactions across up to 100 items, and Cassandra only has row-level lightweight transactions (compare-and-set). So "NoSQL has no transactions" is outdated.

**Q5. Why is Cassandra not a "columnar" database?**
A: The "wide-column" name is historical — it stores sparse column families grouped by partition key. Analytic columnar formats (Parquet, Vertica) store data column-by-column for scan efficiency. Cassandra is a **row-partitioned, write-optimised LSM store**, closer to a distributed sorted map.

### 🟡 Mid-Level (2–5 Years)

**Q6. What is CAP theorem, stated correctly?**
A: In an asynchronous network where messages can be lost, a distributed system cannot simultaneously guarantee that every read sees the latest write (Consistency) and every request gets a non-error response (Availability) when a network partition occurs (Partition tolerance). During a partition you must pick. Availability is not a property you trade away permanently — the choice recurs every time a partition happens.

**Q7. What is PACELC?**
A: An extension of CAP: if a **P**artition occurs, trade **A**vailability vs **C**onsistency; **E**lse (normal operation), trade **L**atency vs **C**onsistency. It is more useful than CAP because normal operation is the common case — e.g. MongoDB waits for a journal majority (latency cost) while Cassandra at `ONE` does not.

**Q8. Why are joins impossible in Cassandra?**
A: Because data is distributed by partition key across a ring; the rows a join needs may live on different nodes, so a join would require multi-node round trips and broadcast on every query — destroying Cassandra's write-optimised design. Instead you denormalise into query-specific tables and materialised views (used carefully), designed per query.

**Q9. How do you choose a partition key in Cassandra?**
A: The partition key must distribute writes evenly (no hot partitions), keep partitions bounded in size (no unbounded growth), and match the query. Test with actual cardinality — `userId` even, `status` disastrous (every row lands in a few partitions). If a query filters by a non-key column, you either add a query table or accept a full scan (`ALLOW FILTERING` — a production red flag).

**Q10. What is a secondary index and why is it limited in wide-column stores?**
A: An index maps a non-key attribute to primary keys. In Cassandra the indexed value lives on a different node than the data, so index queries fan out across the cluster and can't guarantee page locality — hence Cassandra's guidance against indexing on high-cardinality or frequently-updated columns. MongoDB can index any field because the document lives on one shard; DynamoDB uses GSIs which are separate tables with their own partition keys.

**Q11. When would you pick MongoDB over PostgreSQL?**
A: When the data is naturally hierarchical/nested, the schema evolves faster than you can migrate, most access is by a single document or an indexed field, and you need horizontal scale-out with flexible documents. I would not pick it when I need rich ad-hoc joins, complex reporting, or strong transactional invariants across many entities.

**Q12. What is read-your-writes consistency, and how do you provide it?**
A: A session guarantee: after a client writes a value, every subsequent read from that client returns it (or newer). Provide it by routing the client's reads to the same replica (`QUORUM`+`QUORUM` in Cassandra), using strongly-consistent reads (DynamoDB with `ConsistentRead=true`), or by pinning a session to the primary (MongoDB).

### 🔴 Senior (5–8 Years)

**Q13. Compare Cassandra `QUORUM` and `ONE` — what are you actually trading?**
A: With `RF=3`, `QUORUM` requires acknowledgements from 2 replicas on write AND 2 on read, guaranteeing an overlap (`R + W > RF`) so reads see the latest write. That costs an extra round trip, and if any two replicas are slow, write latency rises. `ONE` is fastest but permits stale reads and loses writes during a simultaneous node failure without a read repair. I pick per query: `ONE` for telemetry, `QUORUM` for state that must be read-after-write.

**Q14. How does Cassandra handle two concurrent writes to the same row?**
A: Every cell carries a timestamp (client-supplied or server-assigned). On read/compaction the value with the **highest timestamp wins** — last-write-wins. It does not read the old value, so it's fast, but conflicting concurrent writes silently lose data unless you use LWT (`IF`) or an application-level CRDT/locking strategy. This is why "user updated their profile twice at once" loses one update in Cassandra.

**Q15. What is a lightweight transaction in Cassandra and when should you use it?**
A: LWT uses Paxos (`IF NOT EXISTS` / `IF col = value`), giving linearizable consistency for that single row, at the cost of ~4 round trips and much lower throughput. Use it for correctness-critical uniqueness (username reservation, inventory decrement) and never on hot paths — a username-claim table is fine, a "like button" table is not.

**Q16. How does DynamoDB's hot partition problem occur and how do you fix it?**
A: A partition has a fixed throughput ceiling (3000 RCU / 1000 WCU per partition); one hot item (celebrity's profile, jumbo cart) concentrates all traffic on it, so the table throttles while sitting at 15% utilisation. Fixes: choose a high-cardinality partition key; add a **random suffix / write-sharding** (distribute the item across `N` computed partitions and read them all); use an on-demand or adaptive-capacity table; move hot items to a dedicated "hot item" table. GSIs make it worse because their partition key is chosen independently.

**Q17. Explain DynamoDB single-table design and why it works.**
A: Put all entity types in one table and use a generic primary key plus a sort-key prefix convention (`USER#123` / `ORDER#456`) so that one `Query` returns an entire item collection with a single partition access. Combined with **sparse GSIs** (an item appears in the index only if it has the indexed attribute), you get many access patterns without extra tables or transactions. The cost is modelling discipline and readability — a powerful technique that juniors frequently misuse.

**Q18. How does MongoDB handle a hot shard and rebalance?**
A: Shards hold **chunks** (default 128MB) of the shard key range; the balancer migrates them. If the shard key is monotonically increasing (`ObjectId`-ish order or timestamps), all writes land on the last shard, which becomes hot — that's the "bad shard key" failure mode. Fix by picking a **hashed shard key** or a compound key combining a high-cardinality field with the write pattern. Because the shard key is immutable after creation, the only remedy for a bad one is a dump/restore or resync.

**Q19. What's the consistency story of MongoDB replica sets?**
A: A replica set is single-primary; reads go to the primary by default (strongly consistent) and can be directed to secondaries with read concerns (`primary`, `primaryPreferred`, `secondary`, `secondaryPreferred`, `nearest`). Write concern `w:1` means the primary only (risk of rollback on failover); `w:"majority"` waits for a journaled majority, avoiding rollback of acknowledged writes. `readConcern: "majority"` reads only majority-committed data, so a failover never serves stale reads.

**Q20. How would you model a Cassandra table for "the last 100 sensor readings per device, most recent first"?**
A: `PRIMARY KEY ((device_id), reading_time DESC)` — partition on `device_id` (bounded per device), cluster on `reading_time DESC` so the most recent rows are stored first (cheap `LIMIT 100` reads). Any query by sensor-type or region becomes a *separate* query table with that column as part of the partition key. There is no partial index that makes this work well.

### ⚫ Principal / Staff (8+ Years)

**Q21. Justify the "no distributed transactions" stance in Cassandra for a payments ledger.**
A: A ledger is exactly where eventual consistency and last-write-wins are unacceptable: two concurrent debits of the same account must not both succeed, and balances must be exactly checkable. Cassandra's LWT gives me row-level linearizability, so I'd model one row per account id and do `UPDATE ... SET bal = ? WHERE id = ? IF bal = ?`. That works, but at ~4 RTT with Paxos it caps throughput and adds coordinator failure modes. For a true multi-account transfer I would not use Cassandra — I'd use CockroachDB/Spanner or Postgres, and use Cassandra only for the append-only audit event log. The correct architecture splits by correctness requirement, not by fashion.

**Q22. Explain the tension between tunable consistency and latency SLOs at p99.9.**
A: In Cassandra, `LOCAL_QUORUM` at `RF=3` waits for 2 replicas; if the slowest replica degrades (GC pause, disk flush), the p99.9 write latency jumps to the slowest-2 path. Raising to `ONE` buys latency but reintroduces read-repair cost and stale-read risk at scale, with the real cost surfacing as downstream compensation logic. My approach: keep `LOCAL_QUORUM` for state, offload non-critical paths, and monitor `ReadRepairChance`, `coordinator` and `messaging timeouts` — because the consistency level is a per-query dial that shows up in your SLO, not a config flag.

**Q23. When is DynamoDB the wrong choice despite its scale?**
A: When you need ad-hoc analytic queries, relational integrity across many entities, joins, or complex scanning. A key-value store with indexes is a lookup engine; an analytics workload with filters across attributes will degrade to table scans that consume the whole table's RCU. Also when the access pattern isn't yet understood — DynamoDB's modelling cost is front-loaded; you pay it in design, not in operation. If the access patterns are still churning weekly, a document store or Postgres fits better, and migration cost is real.

**Q24. How do you migrate between NoSQL engines (e.g. Cassandra → ScyllaDB, or Postgres → DynamoDB) safely?**
A: Dual-write or CDC (Debezium/Canal) into the new store, backfill the existing data, then shadow-read-compare correctness before cutover; keep the old store authoritative until the comparison window is clean, then flip reads, then stop dual-writes, then decommission. With a schema-first store like Cassandra, the new table design must be **query-driven and validated against the actual query list**, because the hardest part of the migration is discovering the query patterns the old application implicitly relied on.

---

## 11. Counter-Questions & Interviewer Traps

**Trap 1: "So NoSQL doesn't support ACID?"** — Dated claim. MongoDB (multi-doc, 4.0+), DynamoDB (transactions), and CockroachDB/Yugabyte do. The truthful answer is "NoSQL systems offer *tunable* guarantees; ACID is available where the product chose to build it."

**Trap 2: "Is Cassandra eventually consistent or strongly consistent?"** — **Both, per operation.** `ONE` reads are eventually consistent; `QUORUM` reads with `R + W > RF` are strongly consistent; LWT is linearizable. The correct answer always names the *operation*, not the database.

**Trap 3: "Which is better, MongoDB or Cassandra?"** — The wrong question. They serve different shapes: MongoDB is a query-flexible document store; Cassandra is a write-optimised, partition-key-driven store. The right answer is about *access patterns, consistency, and operational cost*.

**Trap 4: "CAP says all NoSQL is AP, so it can lose my writes."** — Writes are lost only when configured that way. Cassandra at `LOCAL_QUORUM` with hinted handoff and read repair does not silently lose acknowledged writes; and DynamoDB replicates synchronously across 3 AZs.

**Trap 5: "Why not just shard Postgres?"** — You can, and many teams do, but you lose cross-shard joins, distributed transactions, global uniqueness, and you inherit per-shard failover complexity. NoSQL stores are engineered with those constraints from the ground up (peer-to-peer rings, partition-aware clients, no-join designs).

**Trap 6: "Eventual consistency means my data will be wrong."** — It means reads *may* be stale. You decide how stale is acceptable per operation, and you can always turn the dial up.

---

## 12. Rapid-Fire MCQs

**Q1. During a network partition, a distributed system must choose between:**
- [ ] A) Schema and index
- [ ] B) Consistency and Availability
- [ ] C) Latency and Throughput
- [ ] D) ACID and BASE
* **Answer:** **B** — partition tolerance is mandatory for distributed systems.

**Q2. Cassandra's default read/write consistency level is:**
- [ ] A) ALL
- [ ] B) QUORUM
- [ ] C) ONE
- [ ] D) SERIAL
* **Answer:** **C** — fastest, eventually consistent; `R + W > RF` makes it strong.

**Q3. Which store supports multi-document ACID transactions?**
- [ ] A) Redis
- [ ] B) Cassandra
- [ ] C) MongoDB
- [ ] D) Elasticsearch
* **Answer:** **C** — MongoDB 4.0+ (and DynamoDB too, ≤100 items).

**Q4. DynamoDB's default read consistency is:**
- [ ] A) Strong
- [ ] B) Eventual
- [ ] C) Linearizable
- [ ] D) Serializable
* **Answer:** **B** — strongly consistent reads are available on tables (not GSIs).

**Q5. PACELC's "Else" branch describes:**
- [ ] A) Partition behaviour
- [ ] B) The Latency vs Consistency trade in normal operation
- [ ] C) Data model choice
- [ ] D) Replication factor
* **Answer:** **B.**

**Q6. A Cassandra query that requires reading a column not in the partition key needs:**
- [ ] A) A secondary index
- [ ] B) `ALLOW FILTERING`
- [ ] C) A query-specific table designed for that access pattern
- [ ] D) A full cluster scan
* **Answer:** **C** — query tables are the modelling answer; B is a red flag.

**Q7. Which store uses a peer-to-peer ring with no primary per row-set?**
- [ ] A) MongoDB
- [ ] B) PostgreSQL
- [ ] C) Cassandra
- [ ] D) Redis Cluster (leader-based slots)
* **Answer:** **C** — Cassandra is symmetric peer-to-peer; DynamoDB is managed.

**Q8. Redis Cluster uses how many hash slots?**
- [ ] A) 1024
- [ ] B) 4096
- [ ] C) 16384
- [ ] D) 65536
* **Answer:** **C** — 16384 slots; minimum 3 masters for a fault-tolerant cluster.

---

## 13. One-Page Cheat Sheet

```text
+----------------------------+-------------------------------------------------------+
| CONCEPT                    | THE RULE                                              |
+----------------------------+-------------------------------------------------------+
| NoSQL families             | Key-value | Document | Wide-column | Graph | Search     |
| "Wide-column" means        | Partitioned sorted map, NOT columnar analytics        |
| CAP, stated correctly      | During a partition: choose C or A. P is mandatory.    |
| CAP myth                   | "Pick 2 of 3 at design time" — misleading (Brewer 2012)|
| PACELC                     | P → A/C trade; Else → L/C trade                       |
| Eventual consistency       | Converges if writes stop; reads MAY be stale          |
| Strong consistency         | DynamoDB ConsistentRead, Mongo majority, Cassandra    |
|                            | QOURUM (R+W>RF)                                       |
| Linearizable (single obj)  | Cassandra LWT, DynamoDB conditional write, etcd       |
| Serializable (multi-obj)   | Postgres SERIALIZABLE, Mongo txn, DynamoDB txn        |
| No joins                   | Cassandra/DynamoDB/Redis — denormalise per query      |
| Cassandra partition key    | Even distribution + bounded size + matches the query  |
| Cassandra clustering cols  | Order rows INSIDE the partition (on-disk sort)        |
| Last-write-wins            | Cassandra timestamp wins; concurrent writes lost      |
| LWT (IF)                   | Linearizable, ~4 RTT, hot-path poison                 |
| LSM write path             | commitlog -> memtable -> SSTable -> compaction        |
| DynamoDB item limit        | 400KB; partition key <= 2048 bytes                    |
| DynamoDB GSI vs LSI        | GSI: own partition key, add anytime; LSI: same PK,    |
|                            | 10GB per partition, defined at creation               |
| DynamoDB single-table      | Type-prefixed keys + sparse GSIs, one Query per       |
|                            | access pattern                                        |
| Redis 16384 slots          | Min 3 masters + 3 replicas for fault tolerance        |
| Redis eviction             | noeviction | allkeys-lru | volatile-lru | allkeys-lfu  |
+----------------------------+-------------------------------------------------------+
```

---

## 14. Real-World Production Post-Mortems

### Incident 1: Cassandra tombstone resurrection
A user-deletion flow used `DELETE FROM events WHERE user_id = ?` plus a soft-delete flag. Three months later, deleted rows reappeared in a "deleted users" analytics dashboard. `gc_grace_seconds` is 10 days — the deletion was written as a tombstone, but a replica was down for 12 days, missed the delete, came back, and served pre-delete data, which the read-repair then propagated backwards. **Fix:** two-phase deletion (mark deleted + `USING TIMESTAMP` far in the future), plus a repair-heavy decommission process.

### Incident 2: DynamoDB hot partition during a flash sale
A "product of the hour" campaign routed all traffic to a single partition (`PRODUCT#001`), throttling at ~3% of table capacity. **Fix:** write-sharding with `PRODUCT#001#<0..9>` computed from a request attribute, reading all shards and merging, plus moving the item to a dedicated table and monitoring `ThrottledRequests` per partition. The incident exposed that SLOs based on table-level averages hide partition-level ceilings.

### Incident 3: MongoDB unbounded array growth
An event-collection schema embedded every notification into the user document. A power user accumulated 1M entries; documents exceeded comfortable sizes, the oplog entry exceeded 16MB, writes slowed, and replication lagged. **Fix:** the bucketing pattern (fixed-size array documents) plus a TTL index; the key lesson is that *flexible schema* is not *unbounded schema* — document size limits force the same modelling discipline as relational design.

---

## 15. Follow-up Learning
- **Next Chapter:** → [02 · MongoDB — Document Model, Schema Patterns & Indexing](./02-MongoDB-Document-Model-Schema-Patterns-and-Indexing.md)
- **SQL masterclass fundamentals:** [../01-SQL-Databases/01-SQL-Fundamentals-Data-Types-Constraints-and-CRUD.md](../01-SQL-Databases/01-SQL-Fundamentals-Data-Types-Constraints-and-CRUD.md)
- **Caching patterns (Redis in depth):** [../../SPRING_BOOT/10-Caching/](../../SPRING_BOOT/10-Caching/)
- **Master Kit Index & Study Roadmap:** ← [README](./README.md)
