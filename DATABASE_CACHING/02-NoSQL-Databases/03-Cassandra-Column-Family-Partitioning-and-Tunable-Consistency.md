# 03 · Cassandra — Column-Family, Partitioning & Tunable Consistency

> **Level:** Mid → Principal (the wide-column interview chapter)  
> **Interview Frequency:** ★★★☆☆ (mandatory where time-series / write-heavy messaging is in scope)  
> **Target:** Apache Cassandra 4.0+ / 5.0 (ScyllaDB-compatible)  
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. The Wide-Column Data Model](#1-the-wide-column-data-model)
- [2. CQL & Table Design](#2-cql--table-design)
- [3. Query-First Modelling](#3-query-first-modelling)
- [4. The Ring, Tokens & Data Distribution](#4-the-ring-tokens--data-distribution)
- [5. Write Path — The LSM Tree](#5-write-path--the-lsm-tree)
- [6. Read Path — Why Reads Can Be Slow](#6-read-path--why-reads-can-be-slow)
- [7. Tunable Consistency — `ONE` vs `QUORUM` vs `ALL`](#7-tunable-consistency--one-vs-quorum-vs-all)
- [8. Tombstones, Compaction & GC Grace](#8-tombstones-compaction--gc-grace)
- [9. Lightweight Transactions (LWT)](#9-lightweight-transactions-lwt)
- [10. Time-Series, Counters & Anti-Patterns](#10-time-series-counters--anti-patterns)
- [11. Spring Data Cassandra — The Java Side](#11-spring-data-cassandra--the-java-side)
- [12. Level-Wise Interview Questions & Answers](#12-level-wise-interview-questions--answers)
- [13. Counter-Questions & Interviewer Traps](#13-counter-questions--interviewer-traps)
- [14. Rapid-Fire MCQs](#14-rapid-fire-mcqs)
- [15. One-Page Cheat Sheet](#15-one-page-cheat-sheet)
- [16. Real-World Production Post-Mortems](#16-real-world-production-post-mortems)
- [17. Follow-up Learning](#17-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

1. **Cassandra is a distributed sorted map, not a columnar database.** `PARTITION BY hash(partition key) → token → node`; **clustering columns** order rows *within* a partition on disk. "Wide-column" is a historical name.
2. **There are no joins, no ad-hoc queries, and no aggregations across partitions.** You design a table **per query**. A query on a non-partition-key column needs a new table or `ALLOW FILTERING` (a production red flag).
3. **The partition key decides distribution; the clustering columns decide order.** Choose it for *query access pattern, bounded partition size, and even write distribution* — in that order.
4. **Writes append to a commit log and a memtable, then flush to immutable SSTables.** There is **no read-before-write** — that is why Cassandra writes are fast and why conflicting concurrent writes resolve by **last-write-wins**.
5. **Consistency is a per-query dial:** `ONE`, `TWO`, `THREE`, `QUORUM`/`LOCAL_QUORUM`, `ALL`, `SERIAL` (LWT), plus `hinted handoff`, `read repair`, and `nodetool repair`. Set `R + W > RF` and reads see the latest write.
6. **Deletes are tombstones** — not immediate removal. Data survives until compaction after `gc_grace_seconds` (10 days default). Deletes are a common source of "resurrected" data and read-timeouts.
7. **LWT (`IF` / `IF NOT EXISTS`) gives linearizable consistency for one row** via Paxos, at ~4 round trips and much lower throughput. Correctness-critical, hot-path poison.
8. **Unbounded partitions are the classic failure mode.** A partition growing past ~100MB causes read-timeouts, compaction strain, and repair pain. Design for bounded partitions (time-bucketing, per-customer shards).

---

## 1. The Wide-Column Data Model

A Cassandra row is a **map of columns** grouped by partition; each row lives in exactly one partition on one node.

```text
   Partition key P1  ────────────────────────────────────────┐
   hash(P1) = token  -3294  ──────────────────────────────── │
                                                              │
   +----------------------------------------------------------+--+
   |  P1  |  clustering-1  |  clustering-2  |  ...  | value   |  |   row
   |      |  clustering-1' |  clustering-2' |  ...  | value'  |  |   row  (on-disk sorted by
   |      |  clustering-1''|  ...           |  ...  | value'' |  |         clustering columns)
   +----------------------------------------------------------+--+
     Partition key (hash)          Clustering columns (sorted)     Payload
```

- **Partition key** → hashed (Murmur3) → determines the owning node/replicas.
- **Clustering columns** → the sort order *inside* the partition on disk; a `LIMIT` on a sane partition is an on-disk range scan, not a full scan.
- Columns are **sparse** — different rows in a partition may have different column sets. There is no schema enforcement across partitions.

### 1.1 Physical layout

```text
Node → Token range → Partitions → Rows (sorted by clustering columns) → CQL columns
```

This is why Cassandra is described as a **distributed, partitioned, sorted map**: you must always know where your data lives, which means you must always know your partition key.

### 1.2 Cassandra vs columnar

| Question | Answer |
|---|---|
| "Is Cassandra a columnar/analytics database?" | **No.** Wide-column = row-partitioned LSM store. Columnar analytics = Parquet/Vertica/ClickHouse. |
| "Is it a key-value store?" | Effectively a **sorted map keyed by partition key**, with clustering columns adding sorted-range access. |
| "Why 'wide'?" | Rows can carry very many columns, and a partition can carry very many rows — "wide" refers to the sparse, flexible column family. |

---

## 2. CQL & Table Design

```sql
-- Partition on customer_id; cluster on order_time DESC: the newest orders read first
CREATE TABLE orders_by_customer (
    customer_id UUID,
    order_time  TIMESTAMP,
    order_id    UUID,
    status      TEXT,
    total       DECIMAL,
    PRIMARY KEY ((customer_id), order_time DESC)
) WITH CLUSTERING ORDER BY (order_time DESC, order_id DESC)
   AND compaction = { 'class': 'TimeWindowCompactionStrategy',
                      'compaction_window_unit': 'DAYS',
                      'compaction_window_size': 1 }
   AND gc_grace_seconds = 86400
   AND default_time_to_live = 0;

-- Shallow read: newest 20 orders for one customer — no scan
SELECT * FROM orders_by_customer WHERE customer_id = ? LIMIT 20;
```

### 2.1 The primary-key grammar (memorise this)

```sql
PRIMARY KEY ( (partition_key), clustering_col_1, clustering_col_2, ... )
--             ^ required       ^ optional, defines on-disk order
```

- **Composite partition key:** `PRIMARY KEY ((a, b), c)` — the partition is the composite `(a, b)`; both parts must appear with `=` in `WHERE`.
- **Compound clustering key:** additional `ORDER BY` in `CLUSTERING ORDER BY`; clustering comparisons must be in key order and contiguous.

### 2.2 The validity rules (this is what interviewers test)

```sql
-- ✅ Allowed: equality on all partition columns, then a prefix of clustering columns
SELECT * FROM t WHERE pk = ?;                          -- partition only
SELECT * FROM t WHERE pk = ? AND ck1 = ?;               -- full key prefix
SELECT * FROM t WHERE pk = ? AND ck1 > ?;               -- range on last clustering col

-- ❌ Illegal (InvalidRequest): clustering prefix skipped
SELECT * FROM t WHERE pk = ? AND ck2 = ?;

-- ❌ Illegal: non-key column
SELECT * FROM t WHERE status = ? AND customer_id = ?;

-- ⚠ Discouraged: full scan, "works" but does not scale
SELECT * FROM t WHERE status = ? ALLOW FILTERING;
```

### 2.3 Useful CQL features

| Feature | Purpose |
|---|---|
| `USING TTL n` | Row-level expiry (time-series) |
| `USING TIMESTAMP` | Explicit write timestamp for conflict resolution (LWT/anti-entropy) |
| `IF NOT EXISTS` / `IF x = y` | Lightweight transaction |
| `WRITETIME(col)` / `TTL(col)` | Inspect last-write time / remaining TTL |
| `STATIC` columns | A column shared by all rows in a partition |
| `COUNTER` | Approximate integer counter (read-before-write internally!) |
| `COLLECTION` (`set`, `list`, `map`) | Small collections only — never for hot or unbounded data |
| **Materialised views** | Auto-maintained secondary table — authoritative docs say use sparingly |
| **SAI indexes (5.0+)** | Storage-attached indexes for non-key querying |
| `PER PARTITION LIMIT` | Cap rows per partition in a query |
| `GROUP BY` | Group rows within a partition (not across) |

---

## 3. Query-First Modelling

The golden rule: **design the table for the query, not for the entity.**

```text
Step 1 — List every access pattern ("show the last 10 orders per customer",
        "all readings of sensor X in the last 6h", "orders by status").
Step 2 — For each pattern, derive the partition key (what filters with =).
Step 3 — Derive the clustering columns (ordering + range + LIMIT).
Step 4 — Verify: partition sizes bounded? writes evenly distributed?
Step 5 — Build ONE table per pattern. Duplicate data freely.
```

Worked example — three access patterns, three tables:

```sql
-- Q1: latest orders for a customer
CREATE TABLE orders_by_customer (... PRIMARY KEY ((customer_id), order_time DESC));

-- Q2: orders for a customer within a time window, ascending
CREATE TABLE orders_by_customer_asc (... PRIMARY KEY ((customer_id), order_time ASC));

-- Q3: orders by status (an access pattern with low selectivity -> a query table
--     that will be huge and slow; usually the answer is "don't do this")
CREATE TABLE orders_by_status (... PRIMARY KEY ((status), order_time DESC));
```

> **Senior answer:** *"In Cassandra the schema is the query plan. If I cannot express the query as `WHERE partition_key = ? [AND clustering prefix]`, the answer is a new table, not an index. Duplicating across query tables is expected — Cassandra treats storage as cheap and correctness of the access pattern as expensive."*

---

## 4. The Ring, Tokens & Data Distribution

```
        Murmur3 hash of partition key  ──►  token (int64)  ──►  node range
```

- The **token range** `(−2⁶³, 2⁶³−1]` is divided among nodes; a **vnodes** deployment (default 256 per node) makes balancing fine-grained and self-healing.
- **Replication factor (RF)** copies each partition to `RF` consecutive nodes (with the **Snitch** deciding what "consecutive" means: `SimpleSnitch` for one DC, `GossipingPropertyFileSnitch` for multi-DC/rack awareness).
- **Consistency level** then decides how many of the `RF` copies must respond:

```text
RF=3,  nodes A B C D E F  (each partition lives on 3 contiguous nodes)
CL=ONE        -> 1 replica
CL=TWO        -> 2 replicas
CL=QUORUM     -> 2 replicas   (floor(RF/2)+1 = 2)
CL=LOCAL_QUORUM-> 2 replicas in the local DC (multi-DC)
CL=ALL        -> 3 replicas
CL=SERIAL      -> QUORUM + a Paxos round
```

- **Hinted handoff:** if a replica is down, the coordinator stores the write and replays it when the node returns ( hints live 3h by default — `max_hint_window`).
- **Read repair:** during a read, the coordinator compares responses and repairs stragglers (`read_repair_chance`).
- **Anti-entropy:** `nodetool repair` compares Merkle trees per range — the authoritative reconciliation; **skip it and you lose data on permanent node failure**.

### 4.1 Coordinator and speculative execution

The client connects to a **coordinator** (not necessarily the owner). With `SPECULATIVE_EXECUTION` enabled, the coordinator re-issues the query to another replica if the first is slow, masking tail latency — an important production lever.

---

## 5. Write Path — The LSM Tree

```
   WRITE
     │
     ├─► 1. commitlog (fsync'd, sequential append = durability)  ◄── the "WAL"
     │
     └─► 2. memtable (in-memory sorted map, per table)
              │  flush at 1/4 of configured heap (or explicit nodetool flush)
              ▼
        3. SSTable (immutable, sorted, on-disk, with a Bloom filter +
                    partition index + compression)
              │
              ▼
        4. COMPACTION merges SSTables, drops deleted data (tombstones),
           and rewrites into fewer, larger files (STCS / LCS / TWCS)
```

**Why this is fast:**
- **Sequential writes only** — no random I/O, no read-before-write, no page splits.
- **Batched appends** scale linearly with nodes (each write is one commit-log append).
- **Deletes/updates are also appends** — new versions supersede old ones at read/compaction time.

**Why this costs you on reads:**
- A row may exist in several SSTables; the read must consult each and merge with the memtable → **read amplification**.
- Bloom filters, the partition summary/index, and row caches mitigate but do not eliminate it.

**Compaction strategies:**

| Strategy | Best for | Notes |
|---|---|---|
| **STCS** (default) | Write-heavy, uniform | Merges similar-sized tables |
| **LCS** | Read-heavy / updates | Many small tables; **high write amplification** |
| **TWCS** | **Time-series with TTL** | Buckets by time window; old buckets never recompact |
| **UCS** (4.0+) | Hybrid | Self-tuning |

---

## 6. Read Path — Why Reads Can Be Slow

```
   READ (pk = ?)
     │
     ├─► memtable                     (in-memory, sorted)
     ├─► row cache (if enabled)
     ├─► partition key cache → Bloom filter → SSTables (skip those without the row)
     │      └─► partition index → compression offset map → block cache → data
     └─► MERGE across all sources, apply tombstones, return
```

Failure modes to name in an interview:
1. **Unbounded partition** — a `LIMIT` still scans the whole partition if the clustering order doesn't match the query.
2. **Wide rows / wide partitions** — every read page-reads the full partition set.
3. **Tombstones** — each must be evaluated; `ALLOW FILTERING` scans indexes with tombstones.
4. **No Bloom filter hit** → the read still consults all SSTables if the Bloom filter is not sized correctly.
5. **Cross-node scatter** — querying without the partition key hits every node.

> **Rule of thumb:** a healthy read examines `totalDocsExamined` ≈ the rows you asked for. If it is 100×, your partition is too wide or your clustering columns are wrong.

---

## 7. Tunable Consistency — `ONE` vs `QUORUM` vs `ALL`

The core formula:

```text
   R + W > RF  ──► reads are guaranteed to overlap with the latest write
                            (given no clock skew and no tombstone weirdness)
```

| RF | For strong R-after-W you need |
|---|---|
| 3 | `R=2 AND W=2` (i.e. `QUORUM` + `QUORUM`) |
| 5 | `R=3 AND W=3` |

```sql
-- Read-your-writes for a shopping-cart update
CONSISTENCY QUORUM;      -- write side
UPDATE carts SET items = ? WHERE user_id = ?;
CONSISTENCY QUORUM;      -- read side
SELECT items FROM carts WHERE user_id = ?;

-- Battery-saving telemetry write (staleness acceptable)
CONSISTENCY ONE;
INSERT INTO readings (...) VALUES (...) USING TTL 86400;

-- Hard requirement: never return stale data, latency is secondary
CONSISTENCY ALL;   -- (and note: a single down replica = failed writes)
```

### 7.1 What each level actually buys

| Level | Latency | Availability | Use |
|---|---|---|---|
| `ONE` | Lowest | Highest | Telemetry, logs, counters, feeds |
| `LOCAL_QUORUM` | Moderate | High, partition-tolerant in multi-DC | Most operational reads/writes |
| `EACH_QUORUM` | Highest | Strong across DCs | Cross-region invariants |
| `ALL` | Highest | Fragile (one replica down = error) | Short, critical writes |
| `SERIAL` (LWT) | ~4 RTT | Contention-sensitive | Uniqueness, inventory decrement |

### 7.2 The honest trade-off

`QUORUM` buys correctness at the cost of **waiting for the second-slowest replica**, which pushes tail latency up. If your SLO is p99 < 20ms, `LOCAL_QUORUM` across 3 AZs is usually satisfied; across 3 *slow* nodes it is not. Monitor `CoordinatorReadLatency`/`WriteLatency` percentiles and the number of `read_repair`/`hinted_handoff` events — **the consistency level you choose is an SLO decision, not a config flag.**

---

## 8. Tombstones, Compaction & GC Grace

```sql
DELETE FROM events WHERE user_id = ? AND event_time = ?;   -- writes a TOMBSTONE
```

- A tombstone is a **deletion marker** with a timestamp, written to the same commit log/SSTables as data.
- The data is removed only when **compaction** runs **after `gc_grace_seconds` (10 days default)** has elapsed. The grace period exists so that a node that was *down* during the delete can be repaired rather than resurrecting the data.
- **Danger 1 — resurrection:** if a replica is down for longer than `gc_grace_seconds`, it misses the delete, comes back, and serves old rows; read repair propagates them.
- **Danger 2 — read timeouts:** each tombstone is an evaluated marker; a range query touching thousands of tombstones times out. Diagnose with `nodetool compactionstats` and `SSTableMetadata` / `tombstone` counters in `nodetool tablehistograms`.
- **Danger 3 — disk bloat:** tombstones consume space until compacted; range deletes over hot ranges create many.

**Safe deletion recipes:**
- Use `USING TTL` for automatically expiring time-series rows instead of explicit deletes.
- Batch deletes by partition and pause; never issue a huge range-delete storm.
- Set `gc_grace_seconds` lower **only** when you guarantee repair/anti-entropy discipline.
- Prefer **soft deletes** (`deleted_at` + a filter) with periodic physical cleanup.

---

## 9. Lightweight Transactions (LWT)

```sql
-- Linearizable "create only if absent"
INSERT INTO usernames (user_id, username)
VALUES (?, ?) IF NOT EXISTS;

-- Linearizable compare-and-set (inventory decrement)
UPDATE inventory SET stock = stock - 1
WHERE item_id = ?
IF stock >= 1;      -- returns [applied] | [applied=false, stock=current] | error

-- Batch Paxos (SERIAL) across partitions
BEGIN BATCH
  INSERT INTO order_lines (...) VALUES (...);
  INSERT INTO order_status (...) VALUES (...);
APPLY BATCH;       -- ⚠ logged batches: correctness at high cost
```

- Internally a **Paxos round (prepare/propose/commit)** — typically **4 round trips** to the coordinator and replicas.
- Guarantees **linearizability for a single partition/row**.
- **Never on a hot path**: throughput drops by orders of magnitude, and contention between LWT writers on the same key serialises them.

**When to use LWT:** username reservation, seat/inventory reservation, idempotency keys, "insert if not exists" schedulers. Everything else belongs at `QUORUM`.

---

## 10. Time-Series, Counters & Anti-Patterns

### 10.1 The canonical time-series table

```sql
CREATE TABLE sensor_readings (
    sensor_id  UUID,
    bucket     DATE,          -- e.g. day/week: BOUNDS the partition
    reading_ts TIMESTAMP,
    value      DOUBLE,
    PRIMARY KEY ((sensor_id, bucket), reading_ts)
) WITH CLUSTERING ORDER BY (reading_ts DESC)
   AND compaction = { 'class': 'TimeWindowCompactionStrategy',
                      'compaction_window_unit': 'DAYS', 'compaction_window_size': 1 }
   AND default_time_to_live = 2592000;   -- 30 days
```

The `(sensor_id, bucket)` composite partition key solves unbounded growth: every partition holds one day of one sensor.

### 10.2 Counters

```sql
UPDATE page_views SET count = count + 1 WHERE page_id = ?;
```
Counters are **eventually consistent and approximate** — they read-before-write internally, so they are *not* the fast-write path they appear to be, and concurrent increments can lose updates. Use them for approximate metrics; use a real ledger (Postgres/Kafka Streams) when the number must be exact.

### 10.3 Anti-pattern table

| Anti-pattern | Symptom | Fix |
|---|---|---|
| Partition key on a boolean/status | One partition holds all rows | Query tables / bucketed keys |
| Unbounded partition (`user_id` alone with 10M events) | Read timeouts, compaction pain | Bucket by time |
| `ALLOW FILTERING` on a big table | Full node scan per query | Query-specific table |
| High-frequency LWT on a hot key | p99 in seconds, saturated coordinators | Queue the mutation; `QUORUM` + application check |
| Large collections / static column updates | Writes ramp and compact constantly | Separate table |
| Unlogged batch across partitions | Partial-write risk | Logged batch, or avoid |
| `DROP`/`TRUNCATE` in production | Tombstone hopelessness | TTL-based expiry |
| No `nodetool repair` | Resurrection, unavailable data | Schedule repair (Reaper) |

---

## 11. Spring Data Cassandra — The Java Side

```yaml
spring:
  cassandra:
    contact-points: 10.0.1.10,10.0.1.11,10.0.1.12
    keyspace-name: shop
    local-datacenter: dc1
    schema-action: CREATE_IF_NOT_EXISTS   # dev only; prod: manage with migration scripts
    request:
      timeout: 10s
      consistency: local_quorum            # default per-operation can be overridden
      serial-consistency: serial
```

```java
@Table("orders_by_customer")
public class OrderByCustomer {
    @PrimaryKeyColumn(name = "customer_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private UUID customerId;

    @PrimaryKeyColumn(name = "order_time", ordinal = 1, type = PrimaryKeyType.CLUSTERED,
                      ordering = Ordering.DESCENDING)
    private Instant orderTime;

    @PrimaryKeyColumn(name = "order_id", ordinal = 2, type = PrimaryKeyType.CLUSTERED)
    private UUID orderId;

    private String status;
    private BigDecimal total;
}

public interface OrderByCustomerRepository extends CassandraRepository<OrderByCustomer, OrderKey> {
    // Derived query -> CQL: WHERE customer_id = ? ORDER BY order_time DESC LIMIT 20
    List<OrderByCustomer> findTop20ByKeyCustomerIdOrderByKeyOrderTimeDesc(UUID customerId);

    @AllowFiltering                                     // ⚠ red flag — justify it
    List<OrderByCustomer> findByStatus(String status);
}

// Per-query consistency override
@Consistency(ConsistencyLevel.LOCAL_QUORUM)
void save(CassandraTemplate template, OrderByCustomer o);
```

**Spring rules:** keep `schema-action` off in production; prefer **CQL scripts** in version control; set `local-datacenter` explicitly or the driver fails on multi-DC clusters; use `CqlTemplate`/`CassandraTemplate` for anything the DSL cannot express; watch `driver` metrics (`cql-requests`, `errors.timeout`) — a rising timeout rate means your partitions or consistency levels are wrong.

---

## 12. Level-Wise Interview Questions & Answers

### 🟢 Junior (0–2 Years)

**Q1. What is a partition key in Cassandra?**
A: The column (or columns) whose hash determines which node stores a row. Every `WHERE` clause must include it with `=` for an efficient query.

**Q2. What are clustering columns?**
A: Columns that determine the on-disk ordering of rows **within** a partition. They let you read a range or the newest N rows without scanning the whole partition.

### 🟡 Mid-Level (2–5 Years)

**Q3. Why can't Cassandra do joins?**
A: A row's data lives on a node decided by its partition key. A join needs data from multiple partitions on potentially different nodes, requiring a broadcast round-trip per query. Instead Cassandra asks you to denormalise into one table per query.

**Q4. How do reads see the latest write?**
A: Set `R + W > RF`. With `RF=3`, `QUORUM` on both sides (2 + 2) guarantees the read set overlaps the write set. Combined with hinted handoff and read repair, this covers replica lag and temporary outages.

**Q5. Why are Cassandra writes so fast?**
A: A write is an append to the commit log plus an insert into an in-memory memtable. There is no read-before-write, no random I/O, no index page splits. The read cost is deferred — reads merge memtable + SSTables.

**Q6. What is `ALLOW FILTERING` and when is it acceptable?**
A: It lets you query on a non-key column by scanning nodes and filtering in-memory. Acceptable only on small, filtered-by-partition-key queries or admin tooling. On production tables it is a full-scan smell that signals a missing query table.

**Q7. What happens on a node failure?**
A: The coordinator detects it and, if `R`/`W` is satisfiable by remaining replicas and no consistency ceiling is hit, the request still succeeds. Writes that would have gone to the down node are stored as **hints** and replayed on recovery within `max_hint_window` (3h default).

### 🔴 Senior (5–8 Years)

**Q8. Your p99 read latency tripled after releasing a new version. What do you check?**
A: (1) `nodetool tablehistograms` per table for wide partitions/rows and tombstone counts. (2) Whether a query switched to `ALLOW FILTERING`. (3) `ReadRepair`/`background` activity and pending compactions. (4) Whether compaction (LCS) is write-amplifying. (5) Driver-side: timeouts, retries and speculative execution. The most common cause is unbounded partitions created by a modelling regression.

**Q9. How do you model "the most recent 100 sensor readings across the last 24h" for 5M sensors?**
A: `PRIMARY KEY ((sensor_id, bucket_date), reading_ts DESC)` with `LIMIT 100`. Partition = (sensor, day) → bounded (≤ 5M/86400 ≈ 58 rows/sec), clustering desc so the newest rows are contiguous, TWCS + TTL for automatic expiry. Any cross-sensor query is a separate, deliberately coarser table (e.g. an hourly rollup), not an index.

**Q10. Explain tombstones to a product manager who asked "can we just delete user data?"**
A: A `DELETE` writes a tombstone, not an erasure. The rows remain until compaction after `gc_grace_seconds` (default 10 days). So "we deleted it" means "it will disappear everywhere within ~10 days, provided no replica missed it". For GDPR-style deletion, run a delete + `nodetool repair` verification, and prefer TTL-expired data going forward.

**Q11. When is Cassandra the wrong choice?**
A: When access patterns are unknown or ad-hoc (schema = query plan, so churn is expensive), when you need joins or aggregations across partitions, when writes need read-your-write semantics on hot rows at scale (LWT is too slow), or when datasets are small enough that a single Postgres + read replicas serves better with far less operational burden.

### ⚫ Principal / Staff (8+ Years)

**Q12. You must guarantee "exactly-once payment capture" in Cassandra. Design it.**
A: Not in Cassandra alone. Use LWT for the idempotency guard — `INSERT INTO captured (payment_id) VALUES (?) IF NOT EXISTS` — which is linearizable and single-partition; then append the audit event to an append-only table at `LOCAL_QUORUM` with a `USING TIMESTAMP` from an upstream event. The actual ledger and settlement must live in a CP store (Postgres/Spanner/CockroachDB) because a multi-step money invariant needs serializability, which Cassandra does not offer outside LWT.

**Q13. Multi-DC Cassandra: how do you keep a DC-local latency SLO while surviving a DC outage?**
A: Write at `LOCAL_QUORUM` (local DC only) for everyday latency, with `EACH_QUORUM` for the small set of invariants that must be consistent across DCs. Reads locally at `LOCAL_QUORUM`. Size for the outage: with `RF=3` and 3 DCs, each DC holds 1 replica, so `LOCAL_QUORUM` (2 of 3 in DC) cannot be met during a DC loss — either use `RF=5` spread as 2-2-1, or accept `TWO`. Add `SpeculativeExecution` for tail latency and monitor cross-DC `ReadRepair`. The design decision is made at RF/snitch level, not at the driver.

---

## 13. Counter-Questions & Interviewer Traps

**Trap 1: "Cassandra is a columnar database like Snowflake?"** — No. Wide-column vs columnar. If the interviewer is asking about analytics, the answer is "that's Parquet/ClickHouse territory; Cassandra is a write-optimised sorted map."

**Trap 2: "So it's eventually consistent, writes can be lost?"** — At `LOCAL_QUORUM` with hints+read repair, acknowledged writes are not lost in normal failure scenarios. The loss modes are: `w:1` + node failure before repair, and `gc_grace_seconds` expiry during a long outage.

**Trap 3: "Just add an index for that query."** — A secondary index in Cassandra fans out to the node holding the indexed value; on high-cardinality columns it is effectively a full scan. The right answer is a query table.

**Trap 4: "Use a logged batch to make these updates atomic."** — Logged batches guarantee atomicity but **not** isolation or performance; they cost roughly the sum of the individual writes and can create write hot-spots. Most "I need a batch" cases are solved by a single-partition row or by event-driven saga.

**Trape 5: "Deleting the row removes it immediately."** — It writes a tombstone. Data persists until post-`gc_grace_seconds` compaction.

**Trap 6: "Counters are a fast increment path."** — No — counters read-before-write internally, are constraint-bound (no TTL, no deletion, no collections), and are approximate under contention. Kafka Streams or a Redis `INCR` with periodic snapshots is faster and more accurate.

---

## 14. Rapid-Fire MCQs

**Q1. In Cassandra, `PRIMARY KEY ((a, b), c)` means:**
- [ ] A) `a` is the partition, `b` and `c` are clustering
- [ ] B) `(a, b)` is the composite partition key, `c` is clustering
- [ ] C) `a` and `b` are clustering, `c` is partition
- [ ] D) It's invalid syntax
* **Answer:** **B.**

**Q2. With `RF=3`, which pair gives strong read-after-write?**
- [ ] A) `R=1, W=1`
- [ ] B) `R=2, W=2`
- [ ] C) `R=3, W=1`
- [ ] D) `R=1, W=3`
* **Answer:** **B** — because `R + W > RF`.

**Q3. The write path is:**
- [ ] A) B-tree page insert with locking
- [ ] B) commit log → memtable → SSTable → compaction
- [ ] C) WAL + random page write
- [ ] D) direct synchronous replication
* **Answer:** **B.**

**Q4. A `DELETE` in Cassandra writes:**
- [ ] A) Nothing (immediate erasure)
- [ ] B) A tombstone marker
- [ ] C) A null value in the row cache
- [ ] D) An anti-entropy request
* **Answer:** **B.**

**Q5. `IF NOT EXISTS` provides:**
- [ ] A) Eventual consistency
- [ ] B) Linearizable, single-row consistency via Paxos
- [ ] C) Stronger than `ALL`
- [ ] D) Optimistic concurrency for the whole table
* **Answer:** **B** (a full `SERIAL`/LWT path).

**Q6. The best compaction strategy for TTL'd time-series data is:**
- [ ] A) LCS
- [ ] B) STCS
- [ ] C) TWCS
- [ ] D) UCS
* **Answer:** **C** — time-windowed, so expired buckets are dropped whole.

**Q7. `gc_grace_seconds` exists to:**
- [ ] A) Slow deletes gracefully
- [ ] B) Prevent resurrection when a downed node misses the delete
- [ ] C) Cap partition size
- [ ] D) Limit hinted handoff
* **Answer:** **B.**

**Q8. A query missing its partition key:**
- [ ] A) Returns an error, always
- [ ] B) Uses a secondary index if present, else scans
- [ ] C) Is automatically rewritten into a token-range query
- [ ] D) Is impossible to express
* **Answer:** **B** — with `ALLOW FILTERING` or an index; scans otherwise.

---

## 15. One-Page Cheat Sheet

```text
+----------------------------+--------------------------------------------------------+
| CONCEPT                    | RULE                                                   |
+----------------------------+--------------------------------------------------------+
| Data model                 | Distributed partitioned sorted map (NOT columnar)      |
| Partition key              | hash -> token -> node; REQUIRED in every WHERE with = |
| Clustering columns         | On-disk sort order within a partition; range/LIMIT    |
| Query-first modelling      | One table per query; denormalise freely               |
| WHERE legality             | all partition cols with =, then contiguous clustering |
| No joins / no ad-hoc       | Access by non-key column -> query table               |
| Write path                 | commitlog -> memtable -> SSTable -> compaction         |
| No read-before-write       | Fast writes; conflicts resolve by last-write-wins     |
| Consistency formula        | R + W > RF  => reads overlap the latest write         |
| RF=3 strong pair          | QUORUM write + QUORUM read (2 + 2)                    |
| Levels                     | ONE | QUORUM | LOCAL_QUORUM | EACH_QUORUM | ALL | SERIAL |
| Hinted handoff             | Stores writes for downed replicas (3h window)        |
| Read repair / nodetool     | Reconcile divergence; ignore it and data may vanish  |
| Deletes                    | Tombstones; removed after gc_grace (10d) + compaction |
| LWT (IF)                  | Linearizable single-row, ~4 RTT — hot-path poison     |
| Time-series table          | ((sensor_id, bucket_date), reading_ts DESC) + TWCS    |
| Unbounded partitions       | Read timeouts + compaction pain; bucket by time      |
| Counters                   | Approximate, read-before-write, never exact money    |
| Spring Data                | schema-action OFF in prod; set local-datacenter      |
+----------------------------+--------------------------------------------------------+
```

---

## 16. Real-World Production Post-Mortems

### Incident 1: The `user_id`-only partition
A messaging feed table used `PRIMARY KEY (user_id, message_ts DESC)`. Power users accumulated 4M messages; every read of "latest 50" scanned a 3GB partition, paged 200K rows, and timed out. **Fix:** `PRIMARY KEY ((user_id, week_bucket), message_ts DESC)` — partitions went from GB to MB, reads returned in 4ms. The lesson: unbounded partitions are the single most common Cassandra performance defect.

### Incident 2: `ALLOW FILTERING` in a hot path
A reporting screen queried `WHERE region = ? AND status = ? ALLOW FILTERING` across 40M rows. Every page load fanned out to all 12 nodes, saturating the cluster and starving the write path. **Fix:** a dedicated `events_by_region_status` query table fed by dual writes; the "quick" index had turned into a cluster-wide scan.

### Incident 3: Tombstone storm from a bulk delete
A data-retention job deleted 200M rows with range deletes. During the 10-day grace period, reads on the affected ranges timed out with "tombstone warn" histograms, and compaction could not keep up. **Fix:** switched the table to TTL-based expiry (TWCS) so future expiry is compaction-time and read-invisible, and ran the one-off delete surgically with `nodetool`-supervised batches plus continuous repair.

---

## 17. Follow-up Learning
- **Next Chapter:** → [04 · DynamoDB — Key-Value Design, Partition Math & Single-Table Modelling](./04-DynamoDB-Key-Value-Design-Partition-Math-and-Single-Table-Modelling.md)
- **Previous Chapter:** ← [02 · MongoDB — Document Model, Schema Patterns & Indexing](./02-MongoDB-Document-Model-Schema-Patterns-and-Indexing.md)
- **CAP / PACELC theory:** [01 · NoSQL Foundations, Data Models & Consistency Theory](./01-NoSQL-Foundations-Data-Models-CAP-and-Consistency.md)
- **Master Kit Index & Study Roadmap:** ← [README](./README.md)
