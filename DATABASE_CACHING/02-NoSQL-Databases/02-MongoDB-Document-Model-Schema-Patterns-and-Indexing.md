# 02 · MongoDB — Document Model, Schema Patterns & Indexing

> **Level:** Junior → Principal (the document store interview chapter)  
> **Interview Frequency:** ★★★★☆ (the most likely NoSQL system in Java/Spring shops)  
> **Target:** MongoDB 6.0+ / 7.0+ (with Spring Data MongoDB)  
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. The Document Model](#1-the-document-model)
- [2. Schema Design — Embedding vs Referencing](#2-schema-design--embedding-vs-referencing)
- [3. The Schema Design Patterns Arsenal](#3-the-schema-design-patterns-arsenal)
- [4. Indexing Architecture & the ESR Rule](#4-indexing-architecture--the-esr-rule)
- [5. Index Types — The Complete Catalog](#5-index-types--the-complete-catalog)
- [6. The Aggregation Pipeline](#6-the-aggregation-pipeline)
- [7. Transactions, Read & Write Concerns](#7-transactions-read--write-concerns)
- [8. Replica Sets, WiredTiger & Sharding](#8-replica-sets-wiredtiger--sharding)
- [9. Spring Data MongoDB — The Java Side](#9-spring-data-mongodb--the-java-side)
- [10. Level-Wise Interview Questions & Answers](#10-level-wise-interview-questions--answers)
- [11. Counter-Questions & Interviewer Traps](#11-counter-questions--interviewer-traps)
- [12. Rapid-Fire MCQs](#12-rapid-fire-mcqs)
- [13. One-Page Cheat Sheet](#13-one-page-cheat-sheet)
- [14. Real-World Production Post-Mortems](#14-real-world-production-post-mortems)
- [15. Follow-up Learning](#15-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

1. **MongoDB stores documents (BSON) in collections.** A document is a self-contained record; `_id` is mandatory and automatically indexed; the default 16MB document size is a *modelling constraint*, not a technical footnote.
2. **"Schemaless" is a misnomer.** The database does not enforce a schema, so **your application** enforces it — usually via JSON Schema validators, POJO mappings, or migration code that rewrites history.
3. **Embedding vs referencing is the core design decision.** Embed when data is read together and the "many" side is bounded (an order with its lines); reference when the "many" side is unbounded or needs its own lifecycle (a user's orders).
4. **The ESR rule for compound indexes:** **E**quality fields first, then **S**ort fields, then **R**ange fields. Violating it forces an in-memory sort or blocks index use entirely.
5. **Multikey indexes on arrays explode silently** — one document with a 1000-element array creates 1000 index entries; any update to the array rewrites them all. This is the #1 MongoDB performance killer.
6. **`$lookup` is a left outer join** — and it does not scale like a relational join: it cannot use an index on the *foreign* collection efficiently and is the classic bottleneck that forces a schema redesign.
7. **Write concern `w:"majority"` + `readConcern:"majority"` avoids rollback of acknowledged writes on failover.** The defaults (`w:1`, `readConcern:"local"`) are fast but can serve stale data after a primary step-down.
8. **Shard key selection is effectively irreversible** — pick a high-cardinality, evenly-distributed key; monotonically increasing keys (ObjectId/timestamp-only) hot-spot the newest chunk.

---

## 1. The Document Model

```javascript
// A BSON document — ordered key/value pairs, values are typed (not just strings)
{
  _id: ObjectId("6709c1f2a3b4c5d6e7f80910"),
  orderNumber: "ORD-2026-004821",
  customer: { firstName: "Asha", lastName: "Verma", email: "asha@corp.io" }, // subdocument
  items: [                                     // array of subdocuments
    { sku: "LT-PRO-14", qty: 1, price: 189000.00, name: "Laptop Pro 14\"" },
    { sku: "USB-HUB",  qty: 2, price: 3999.00,  name: "USB-C Hub" }
  ],
  total: { currency: "INR", amount: 196998.00 },
  status: "PAID",
  tags: ["expedited", "corporate"],            // array of scalars
  placedAt: ISODate("2026-10-24T09:15:00Z")
}
```

### 1.1 `_id` and `ObjectId`

`ObjectId` is a **12-byte** value (3 sections), designed to be generated client-side and roughly time-ordered:

```text
[ 4-byte seconds-since-epoch ][ 5-byte per-process random ][ 3-byte counter ]
   sortable-ish by creation time   unique per machine/process   246k/sec
```

- It is the **default `_id`**, indexed automatically, and (nearly) time-ordered — but *only* to the second, so 1000 documents written in the same second are not intrinsically ordered.
- `_id` cannot be changed after insertion; it can be any type (string, UUID, custom compound).
- If you use a **random UUID** (`UUID.randomUUID()`), you lose time-ordering and randomise shard placement — a "bad shard key" case. Prefer a time-ordered UUID v7 or ObjectId.

### 1.2 BSON type discipline

| Java type | BSON type | Watch out |
|---|---|---|
| `String` | string | Use `Decimal128` for money, **never `Double`** |
| `BigDecimal` | `Decimal128` | Canonical for financial data |
| `Double` | double | IEEE-754; rounding error accumulates |
| `int` / `long` | int32 / int64 | Overflow at document validation time otherwise |
| `boolean` | bool | |
| `Instant` / `OffsetDateTime` | date (UTC, ms) | Store UTC, render local — same rule as SQL |
| `enum` | string or int | String is debuggable; int is compact |
| `byte[]` | binary | |

---

## 2. Schema Design — Embedding vs Referencing

The decision tree senior engineers run in their heads:

```
              +--------------------------------------------------+
              | Is the "many" side bounded and small (< ~100)?   |
              +--------------------------------------------------+
                    |                          |
                   YES                        NO
                    |                          |
        +-----------+-----------+     refs are needed:
        | Is it read together? |     +-------+--------------+
        +-----------+-----------+     |                      |
              |            |    Unbounded?        Needs own lifecycle
             YES           NO  (reviews/orders)   (user ↔ order ↔ product)
              |            |         |                      |
         EMBED it      Reference   Reference              Reference
                        (cheaper)  (correct)              (correct)
```

### 2.1 Embedding (denormalise inside the document)

```javascript
// Order with its embedded line items: ONE read, ONE round trip, atomic
db.orders.insertOne({
  orderNumber: "ORD-100", status: "PAID",
  customer: { customerId: "C-1", name: "Asha Verma" },   // duplicated, not referenced
  items: [{ sku: "LT-PRO-14", qty: 1, price: 189000 }],
  total: { amount: 189000, currency: "INR" },
  placedAt: new Date()
});
```
**Use when:** the sub-document is only meaningful inside the parent, cardinality is bounded, and updates are whole-document. Line items are the textbook case.

### 2.2 Referencing (normalise)

```javascript
{ _id: "U-1", name: "Asha", orders: ["ORD-1", "ORD-2"] }        // one-to-few
{ _id: "O-501", customerId: "U-1", items: [...] }              // the ref lives on the many side
```
**Use when:** the child is unbounded (a user has 10k orders), the child needs independent access, or the same child is shared by many parents (products ↔ orders).

### 2.3 The cardinality rule of thumb

| Relationship | Strategy |
|---|---|
| 1:few, read together | **Embed** the few |
| 1:many (bounded, ~<100) | **Embed** as an array |
| 1:many (unbounded) | **Child references** (`customerId` on the child) |
| 1:many, many → 1 (classic) | **Parent references** (`orderIds` array on the parent) |
| many:many | **Two collections + array of ids** or a bridge collection |

> **Interview answer:** *"I embed by default and reference when a boundary is crossed — unbounded growth, independent lifecycle, or shared data. Embedding buys atomic single-document writes and one-round-trip reads; referencing buys compact documents and no duplicated-write fan-out."*

---

## 3. The Schema Design Patterns Arsenal

Naming these patterns separates senior from mid-level:

| Pattern | Shape | Problem solved |
|---|---|---|
| **Polymorphic** | Heterogeneous documents in one collection (different shapes per device bank) | Schema evolution without migrations |
| **Attribute** | `{ "attrs": [ {k: "color", v: "red", u: "hex"} ] }` | Sparse/rare attributes; great for product catalogues |
| **Bucket** | Fixed-size array groups (`readings` arrays of 100) | Bounded growth for time-series; no unbounded arrays |
| **Computed / Rollup** | Pre-aggregated counters (`salesByMonth`) | Reporting without aggregations |
| **Document Versioning** | `{ schemaVersion: 2, ... }` or `{v: 2, ... , history: []}` | Migrations read-time rather than write-time |
| **Outlier / Subset** | Flag common fields first, rare fields in a `extra` bucket | Avoids bloating 99% of docs for 1% edge cases |
| **Archive** | Cold documents moved to `*_archive` | Collection size management |
| **Approximation** | HyperLogLog (`PFADD`, MongoDB does not natively; Redis does) | Cardinality estimates |
| **Extended Reference** | Copy frequently-needed foreign fields into the parent | Removes a `$lookup` from a hot read path |
| **Tree: parent/child refs** | `{ _id, parentId }` | Hierarchies (cheap, no recursion) |
| **Tree: materialized path** | `path: "/,a,b,c/"` | Subtree queries via regex on the path |
| **Tree: nested sets** | `{left, right}` | No parent traversal; write-heavy |

### 3.1 The bucketing pattern concretely

```javascript
// BAD: document grows forever — the "unbounded array" catastrophe
{ _id: "sensor-1", readings: [ {t: ..., v: ...}, ... ] }   // reaches 16MB

// GOOD: bucket — bounded array, indexed by time
{ _id: "sensor-1#2026-10-24", sensorId: "sensor-1",
  startTime: ISODate("2026-10-24T00:00Z"), endTime: ISODate("2026-10-24T01:00Z"),
  readings: [ {t: ..., v: ...}, ... ]   // ≤ N readings per bucket
}
// Query "sensor-1 last 24h" = one range query on _id prefix + $unwind
```

---

## 4. Indexing Architecture & the ESR Rule

MongoDB indexes are B-tree structures; every query should either use an index or be a deliberate full collection scan (`COLLSCAN`).

### 4.1 The ESR Rule (compound index field order)

```text
E — EQUALITY fields first      (equality predicates: = , $in, $eq)
S — SORT   fields second       (ORDER BY / sort in the pipeline)
R — RANGE  fields last         ($gt, $lt, $between, $regex-prefix)
```

```javascript
// Query: find PAID orders for a customer in a date range, newest first
db.orders.find({
    customerId: "C-1",          // EQUALITY
    status: "PAID",             // EQUALITY
    placedAt: {$gte: d1, $lte: d2}   // RANGE
}).sort({ placedAt: -1 });      // SORT

// ❌ Wrong order: { placedAt: 1, status: 1, customerId: 1 }  -> range field first,
//    so status/customerId cannot be used for equality -> in-memory SORT (blocks index)
// ✅ Right order: { customerId: 1, status: 1, placedAt: -1 }
db.orders.createIndex({ customerId: 1, status: 1, placedAt: -1 });
```

The range field **must come last** because a B-tree can only support *one* inequality bound on the scanned range; preceding fields give it no narrowing.

### 4.2 Covered queries

If every field returned **and** filtered exists in the index, MongoDB never touches the documents (`FETCH` → 0). `explain("executionStats").executionStages.nReturned` and `totalDocsExamined` are the proof:

```javascript
db.orders.createIndex({ customerId: 1, status: 1, placedAt: -1 });
db.orders.find({ customerId: "C-1", status: "PAID" },
              { _id: 0, placedAt: 1 });   // FETCH stage disappears -> IXSCAN only
```

### 4.3 Sargability — the Mongo edition

| Anti-pattern | Why it kills the index | Fix |
|---|---|---|
| `$where` / JS expression | No index support | `$expr` or restructure |
| `$regex: "x$"` (trailing) | Cannot narrow the key | Prefix regex, or text index |
| `$or` (on different fields) | Multiple index scans | Compound index, or two queries |
| Missing index on `$elemMatch` field | Multikey scan of all entries | Compound multikey index |
| `$exists: false` on a large collection | Non-selective | Maintain a flag instead |
| Sorting without a supporting index | `SORT` stage, 32MB or `allowDiskUse` error | ESR-compliant compound index |

---

## 5. Index Types — The Complete Catalog

| Index | Use | Notes |
|---|---|---|
| **Single field** | `{ field: 1 }` | Default first move |
| **Compound** | `{ a: 1, b: -1 }` | **ESR order**; leftmost-prefix applies to `_id` and prefix fields |
| **Multikey** | auto on arrays | Index entry per element; array update cost |
| **Hashed** | `{ field: "hashed" }` | For sharding (unbounded ranges), **equality only** |
| **Text** | `{ $** }` weights | Stemming, stop words, language; one text index per collection |
| **Wildcard** | `{ "$**": 1 }` | Flexible attributes |
| **2dsphere** | Geo | `$near`, `$geoWithin` |
| **TTL** | `{ expireAfterSeconds }` | Deletes docs after time — background passes only, not exact |
| **Partial** | `partialFilterExpression` | Index only the useful subset — **huge win** (`{ status: "PAID" }` on 2% of rows) |
| **Unique** | `unique: true` | `sparse: true` for "only enforce when present" |
| **Clustered (6.0+)** | | Colocates collection data within the index |
| **Columnstore (6.0+)** | Analytics | Columnar compression for aggregations |

```javascript
// The most valuable index in most production apps: partial + compound
db.orders.createIndex(
  { customerId: 1, placedAt: -1 },
  { partialFilterExpression: { status: { $in: ["PAID", "SHIPPED"] } },
    name: "idx_open_orders_by_customer" }
);
```

---

## 6. The Aggregation Pipeline

```javascript
db.orders.aggregate([
  { $match: { status: { $in: ["PAID","SHIPPED"] }, placedAt: { $gte: start } } },  // 1. index-use
  { $unwind: "$items" },                                                            // 2. one row/item
  { $lookup: {                                                                      // 3. left outer join
      from: "products", localField: "items.sku",
      foreignField: "_id", as: "product" } },
  { $unwind: { path: "$product", preserveNullAndEmptyArrays: true } },
  { $addFields: { lineTotal: { $multiply: ["$items.qty", "$items.price"] } } },
  { $group: {
      _id: { month: { $dateToString: { format: "%Y-%m", date: "$placedAt" } },
             category: "$product.category" },
      revenue: { $sum: "$lineTotal" },
      orders: { $addToSet: "$_id" } } },
  { $project: { revenue: 1, orderCount: { $size: "$orders" }, _id: 0 } },
  { $sort: { "revenue": -1 } }
], { allowDiskUse: true });     // 4. allowDiskUse + hint() are the escape valves
```

**Pipeline rules interviewers test:**
- `$match` **as early as possible** — it prunes before expensive stages (`$lookup`, `$group`).
- Stages can reorder for optimisation (`$project`/`$addFields` can be pushed), but `$lookup` before `$unwind` on the wrong field is a classic self-inflicted explosion.
- A `$group` is a **barrier**: it must consume its whole input before emitting (memory-bound, spills at 100MB per stage by default).
- `explain("executionStats")` shows `IXSCAN` (index) or `COLLSCAN`, and `totalDocsExamined` / `totalKeysExamined` are your ratio metrics.
- `$merge`/`$out` write results back — for pre-aggregation (the rollup pattern).
- **`allowDiskUse`** is required when any stage exceeds its memory limit (sorts, groups, `$lookup`).

---

## 7. Transactions, Read & Write Concerns

```javascript
const session = db.getMongo().startSession();
session.startTransaction({ readConcern: { level: "snapshot" },
                          writeConcern: { w: "majority" } });
try {
  const orders = session.getDatabase("shop").orders;
  orders.updateOne({ _id: orderId }, { $inc: { retries: 1 } }, { session });
  orders.insertOne({ ...audit }, { session });
  session.commitTransaction();
} catch (e) { session.abortTransaction(); }
finally { session.endSession(); }
```

| Concern | Options | Trade |
|---|---|---|
| **Write concern** | `w: 1` (primary only), `w: "majority"` (journaled majority) | `w:1` can **roll back** on failover; `majority` costs latency |
| **Journal** | `{ j: true }` | fsync before ack — durability |
| **Read concern** | `local`, `majority`, `snapshot`, `linearizable`, `available` | `majority` = non-stale on failover; `linearizable` per-document |
| **Read preference** | `primary`, `primaryPreferred`, `secondary`, `secondaryPreferred`, `nearest` | `secondary`/`nearest` = stale reads but cheaper, useful for regional latency |
| **Transaction scope** | Multi-doc, snapshot isolation (4.0); **sharded** (4.2+) | Retryable-result writes; **max 16MB oplog per transaction**, ~1000 ops guidelines |

> **Interview answer:** *"Single-document operations remain my atomic unit — transactions are for the minority of multi-entity invariants. I use `w:'majority'` with `readConcern:'majority'` for anything the customer must immediately see, and `w:1`/`nearest` for telemetry and non-critical paths."*

---

## 8. Replica Sets, WiredTiger & Sharding

### 8.1 Architecture

```
            +-----------+     writes
 client --> |  PRIMARY  | ---> oplog -------+
            +-----------+        |          |
                                  v          v
                            +-----------+  +-----------+
                            | SECONDARY |  | SECONDARY |  (arbiter optional: votes only)
                            +-----------+  +-----------+
```

- **Oplog** (capped collection) — every replicated op; lag = the window of stale reads on secondaries.
- **Election:** majority vote; `electionTimeoutMillis` (default 10s) — failover typically < 12s, and **without `w:"majority"` an unacknowledged write can vanish**.
- **WiredTiger** storage: document-level locking (not collection-level), MVCC, a cache (default 50% of RAM − 1GB), compression, checkpoints every 60s.
- **Hidden/delayed members** for backup windows; **hidden** for analytics/backup; `chainingAllowed` avoids hub load.

### 8.2 Sharding

```text
sh.shardCollection("shop.orders", { customerId: "hashed" })   // recommended default
// vs
sh.shardCollection("shop.orders", { placedAt: 1 })            // BAD: monotonic -> hot last chunk
```

| Shard key property | Why |
|---|---|
| **High cardinality** | Prevents a small key set concentrating writes |
| **Non-monotonic (hashed / random-ish)** | Prevents the "last chunk is hot" problem |
| **Matches query filters** | Targeted queries avoid scatter-gather across all shards |
| **Immutable** | Cannot be changed except by dump/restore |

- Chunks default to **128MB**; the balancer migrates chunks between shards; `jumbo` chunks (un-splittable due to identical shard-key values) block balancing.
- `mongos` routes by shard key; a query without the shard key is a **scatter-gather** (broadcast to every shard) — acceptable for analytics, fatal for a hot read path.

---

## 9. Spring Data MongoDB — The Java Side

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://mongo1:27017,mongo2:27017,mongo3:27017/shop?replicaSet=rs0&w=majority&readConcern=majority
      # or: host/port/database + authentication-database
```

```java
@Document(collection = "orders")                       // POJO mapping, no schema enforced
@CompoundIndex(name = "idx_cust_status_date",           // declared in code, verified on startup
    def = "{'customerId': 1, 'status': 1, 'placedAt': -1}")
public class Order {
    @Id private String id;                             // maps to _id
    private String orderNumber;
    private String customerId;
    @Indexed private String status;
    private List<LineItem> items = new ArrayList<>();  // embedded subdocuments
    private Instant placedAt;
    // BigDecimal -> Decimal128 automatically (configure via converters if needed)
}

public interface OrderRepository extends MongoRepository<Order, String> {
    // Derived query -> ESR index used
    List<Order> findByCustomerIdAndStatusOrderByPlacedAtDesc(String customerId, String status);

    @Query("{ 'customerId': ?0, 'placedAt': { $gte: ?1 } }")
    Page<Order> recentOrders(String customerId, Instant since, Pageable pageable);

    @Aggregation(pipeline = { "{ $match: { 'status': 'PAID' } }", "{ $group: ... }" })
    List<RevenueRow> revenueByMonth();
}

// Transactional template
@Transactional
public void placeOrder(Order o) { orderRepository.insert(o); auditRepository.insert(...); }
```

**Rules for Spring Data MongoDB:**
- `@CompoundIndex` **auto-creates** the index on application startup (great in dev, **dangerous in prod** — build indexes deliberately, ideally with `CREATE INDEX` on a schedule).
- `MongoTemplate` for anything the repository DSL cannot express — pipelines, updates with positional operators, bulk writes.
- `@Query` with explicit JSON keeps the query and index contract in one place.
- Beware **`Pageable` on unbounded sorts**: `PageRequest(0, 20)` sorts the full result — use keyset pagination for deep pages.
- Enable the **slow-query log** (`operationProfiling.slowOpThresholdMs`) and alert on `COLLSCAN` plans.

---

## 10. Level-Wise Interview Questions & Answers

### 🟢 Junior (0–2 Years)

**Q1. What is a document in MongoDB?**
A: A set of key-value pairs with dynamic schema — stored as BSON, self-contained, and required to have an `_id` field which acts as its primary key.

**Q2. Can a document have different fields from other documents in the same collection?**
A: Yes — that is the "flexible schema" property. It also means no database-level validation, so the application (or optional JSON-Schema validator) must enforce the shape.

**Q3. What is `_id`?**
A: The mandatory primary key, indexed automatically by default. If you don't supply it, MongoDB generates an `ObjectId` — a 12-byte value containing a timestamp, a per-process random value, and a counter.

### 🟡 Mid-Level (2–5 Years)

**Q4. When do you embed, and when do you reference?**
A: Embed when the sub-document is small, bounded, only meaningful inside the parent, and read together (order → items). Reference when the related data is unbounded (user → orders), has its own lifecycle, or is shared between many parents (products). Referencing costs an extra read or a `$lookup`; embedding costs document-size risk and duplicate-write fan-out.

**Q5. What is the ESR rule?**
A: For a compound index, put **E**quality fields first, then **S**ort fields, then **R**ange fields. Range fields must be last because an index can only support one inequality bound; putting a range field before an equality field prevents the equality fields from being used at all.

**Q6. Why is `$lookup` dangerous?**
A: It performs a left outer join, but unlike a relational join it cannot use an index efficiently on the joined collection and executes per outer document — so on large inputs it becomes the dominant cost, often forcing an embedding/denormalisation redesign.

**Q7. What is a multikey index and its cost?**
A: MongoDB automatically indexes each element of an array field, creating one index entry per element. A document with a 1000-element array creates 1000 index keys, so any array update rewrites many index entries — the main reason unbounded arrays are forbidden.

**Q8. What write concern prevents losing acknowledged writes?**
A: `w: "majority"` — the write is acknowledged only after a majority of the replica set journals it, so a primary step-down cannot roll it back. `w:1` acknowledges on the primary alone and can be lost.

**Q9. What makes a good shard key?**
A: High cardinality, evenly distributed, and matching the query filters — ideally a hashed value or a compound key. Bad keys are monotonically increasing (ObjectId-only, timestamps) which hot-spots the newest chunk, or low-cardinality (status) which concentrates all data on one shard.

### 🔴 Senior (5–8 Years)

**Q10. You have a topic with 30M documents where a reporting aggregation times out. What do you do?**
A: (1) `explain("executionStats")` — if `COLLSCAN`, build an ESR compound index with a partial filter. (2) Reorder the pipeline so `$match` precedes `$group`/`$lookup`. (3) Use `allowDiskUse` and `hint()`. (4) Move the `$group` into an incremental pre-aggregation (the rollup/computed pattern) with a change stream feeding it, rather than recomputing 30M rows per request. (5) If it is still not enough, run it on a hidden secondary or offload to an analytical store.

**Q11. A multi-document transaction fails intermittently under load. Why?**
A: MongoDB transactions use optimistic concurrency — a write conflict (WriteConflict/TransientTransactionError) aborts the transaction, and the oplog has a 16MB per-transaction limit. The fixes are: keep transactions short, retry on `TransientTransactionError` with jitter, avoid holding long-running transactions (they block WiredTiger checkpoints and inflate the cache), and **prefer single-document atomic operations**, which are always atomic and require no transaction at all.

**Q12. How do you deploy a schema change to 500M documents with zero downtime?**
A: Treat it as an application-level migration: add the new field/version, **dual-write** in the application, backfill with a batched `updateMany` (`bulkWrite` in `{ordered:false}` chunks, off-peak, monitored), switch reads to tolerate both shapes, then remove the old field. Do **not** use a single large `updateMany` — it holds locks and bloats the oplog. Optionally use a change stream to make the backfill incremental/restartable.

**Q13. How does an ObjectId-only shard key hurt, and how do you fix it live?**
A: All inserts land in the last chunk, so one shard absorbs all writes while the others idle. There is no in-place fix — `sh.shardCollection` keys are immutable. Mitigations: enable the balancer with a smaller chunk size to split faster (only partial relief), add a **hashed** secondary index and re-shard via dump/insert, or move new writes to a hashed key with a dual-write period. This is why shard key selection is a one-way door.

### ⚫ Principal / Staff (8+ Years)

**Q14. Your application reads mostly from secondaries for latency. How do you handle read-after-write?**
A: The client pins a session: after a write, direct that session's reads to the **primary** for a short TTL (or use a read preference that prefers the primary until the oplog catches up), or use a **causal consistency session** (`client.startSession({ causalConsistency: true })`) which guarantees the client's own writes are visible in subsequent reads. Without this, "I just paid for my order but it still says PENDING" is the standard regional-incident.

**Q15. When is MongoDB the wrong choice for a 200M-document time-series dataset?**
A: When the query pattern is "aggregate over a time window across many streams" with no filter — that is an analytical scan, and MongoDB's row-oriented storage plus per-stage memory limits will struggle against purpose-built time-series engines (TimescaleDB, InfluxDB, or Cassandra with TTL). I'd use the **bucketing pattern** to shrink documents, pre-aggregate with change streams, and archive to cold storage — but I'd be honest with the team that we are fighting the storage model.

**Q16. Design a MongoDB schema for a multi-tenant SaaS where each tenant has a different schema and queries are always tenant-scoped.**
A: Per-tenant flexible shape inside one collection, with the **tenantId as the leading field of every compound index** (equality first, then the tenant's access pattern), a partial compound index for the common hot paths, and JSON-Schema validators per tenant-version. If tenant sizes differ by 1000×, switch to per-tenant collections/databases to prevent a few whales dominating a shard, with routing metadata in a control-plane document — a "database-per-tenant" pattern with a shard-key of tenantId, explicitly moving the isolation boundary into the topology.

---

## 11. Counter-Questions & Interviewer Traps

**Trap 1: "So MongoDB is schemaless, we don't need migrations?"** — You need *more* migration discipline, not less: every existing document is a contract your code must read. Schema-less means the schema lives in your application instead of the database.

**Trap 2: "Does `w:"majority"` guarantee durability?"** — It guarantees the write survives a failover of the primary, but you should also set `j: true` for a true fsync before acknowledgement; and with `writeConcern` at `"majority"` + `j:true` you pay two latencies.

**Trap 3: "Can a transaction span shards?"** — Since 4.2, yes — distributed transactions exist, but they are limited (oplog 16MB, retryable-write semantics) and slow; the correct default is still single-document atomicity.

**Trap 4: "Is an index always faster?"** — No. Indexes slow writes, cost RAM, and a `COLLSCAN` beats an index on a low-selectivity predicate. The selectivity cutoff rule from the SQL chapters applies here too.

**Trap 5: "Can I change the shard key later?"** — No. It is immutable; changing it is a data migration. This is why shard-key choice is the most consequential irreversible decision in MongoDB.

**Trap 6: "Does `readPreference: secondary` give fresh reads?"** — No — secondaries replicate the oplog asynchronously, so reads can be arbitrarily stale. Never route read-after-write traffic there.

---

## 12. Rapid-Fire MCQs

**Q1. The default `_id` type is:**
- [ ] A) A random UUID
- [ ] B) ObjectId (12 bytes)
- [ ] C) An auto-increment int
- [ ] D) A ULID
* **Answer:** **B.**

**Q2. ESR order means:**
- [ ] A) Equality → Sort → Range
- [ ] B) Equality → Range → Sort
- [ ] C) Range → Sort → Equality
- [ ] D) Sort → Range → Equality
* **Answer:** **A.**

**Q3. `$lookup` implements:**
- [ ] A) An inner join
- [ ] B) A left outer join
- [ ] C) A full outer join
- [ ] D) A cross join
* **Answer:** **B.**

**Q4. Which write concern prevents losing acknowledged writes on failover?**
- [ ] A) `w: 1`
- [ ] B) `w: 0`
- [ ] C) `w: "majority"`
- [ ] D) `j: false`
* **Answer:** **C.**

**Q5. A monotonically increasing shard key causes:**
- [ ] A) Even distribution
- [ ] B) All writes to the newest shard/chunk
- [ ] C) Smaller collections
- [ ] D) Faster elections
* **Answer:** **B.**

**Q6. A clustered/covered query is one where:**
- [ ] A) Documents are sorted in memory
- [ ] B) Every returned and filtered field is in the index, so no document fetch
- [ ] C) A `$group` is used
- [ ] D) `$lookup` is avoided
* **Answer:** **B.**

**Q7. Which index would you use for "the 2% of orders that are unsettled"?**
- [ ] A) A full compound index
- [ ] B) A partial index with `partialFilterExpression`
- [ ] C) A text index
- [ ] D) A hashed index
* **Answer:** **B.**

**Q8. Maximum document size:**
- [ ] A) 1MB
- [ ] B) 4MB
- [ ] C) 16MB
- [ ] D) Unlimited
* **Answer:** **C.**

---

## 13. One-Page Cheat Sheet

```text
+---------------------------+------------------------------------------------------+
| CONCEPT                   | RULE                                                 |
+---------------------------+------------------------------------------------------+
| Document                  | BSON, must have _id, max 16MB                        |
| _id default               | ObjectId: 4B ts + 5B random + 3B counter             |
| Money                     | Decimal128 / BigDecimal — never Double               |
| Embed when                | bounded + read together + only meaningful in parent   |
| Reference when            | unbounded / own lifecycle / shared by many parents    |
| Bounded array limit       | ~100 elements: beyond it, consider bucketing          |
| ESR compound index        | Equality, Sort, Range (range LAST)                   |
| Covered query             | all filtered+projected fields in index -> no FETCH    |
| Multikey cost             | one index entry per array element                    |
| Partial index             | index only the useful subset (huge RAM/Write win)    |
| $lookup                   | left outer join; index-hostile on large inputs       |
| Pipeline order            | $match first, $group is a barrier, allowDiskUse late |
| Write concern             | w:1 fast/unsafe vs w:majority safe                   |
| Read concern              | snapshot/majority for stale-free fails; local stale  |
| Read preference           | secondary/nearest = cheaper + STALE                  |
| Transactions              | multi-doc + sharded since 4.2; 16MB oplog cap; retry |
| Shard key                 | immutable; high cardinality, non-monotonic           |
| Hot shard symptom         | monotonic key -> newest chunk absorbs all writes     |
| Scatter-gather            | query without shard key -> hits every shard          |
| Spring Data               | @CompoundIndex auto-creates on startup (dev only!)  |
+---------------------------+------------------------------------------------------+
```

---

## 14. Real-World Production Post-Mortems

### Incident 1: The `$lookup` that ate the order-list API
An order-history endpoint joined `orders` → `customers` → `products` with two `$lookup`s. Latency was 400ms p99. `explain` showed each `$lookup` doing a `COLLSCAN` per outer document. **Fix:** embedded the customer snapshot into the order at write time (the extended-reference pattern), reducing the hot read to a single index scan at 12ms.

### Incident 2: Array index explosion after a bug
A "recent searches" array was appended to the user profile document with a `$push` unbounded by design. A client bug pushed 400k entries in a night; documents crossed 10MB, index entries reached 4M+, and the oplog churned. **Fix:** capped the array with `$slice` on `$push`, switched to a separate time-series collection with a TTL index.

### Incident 3: Shard key regret
Collections were sharded on `createdAt` (monotonic). Eight months in, the last shard held 90% of writes. The fix was a full **reshard**: dual-write into a hashed-key collection, backfill, verify, cut over — three weeks of engineering. **Lesson:** one line at `sh.shardCollection()` cost a quarter.

---

## 15. Follow-up Learning
- **Next Chapter:** → [03 · Cassandra — Column-Family, Partitioning & Tunable Consistency](./03-Cassandra-Column-Family-Partitioning-and-Tunable-Consistency.md)
- **Previous Chapter:** ← [01 · NoSQL Foundations, Data Models & Consistency Theory](./01-NoSQL-Foundations-Data-Models-CAP-and-Consistency.md)
- **SQL fundamentals & normal forms:** [../01-SQL-Databases/01-SQL-Fundamentals-Data-Types-Constraints-and-CRUD.md](../01-SQL-Databases/01-SQL-Fundamentals-Data-Types-Constraints-and-CRUD.md)
- **Master Kit Index & Study Roadmap:** ← [README](./README.md)
