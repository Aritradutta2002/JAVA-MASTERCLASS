# 04 · DynamoDB — Key-Value Design, Partition Math & Single-Table Modelling

> **Level:** Mid → Principal (the key-value interview chapter)  
> **Interview Frequency:** ★★★☆☆ (dominant where the shop runs on AWS)  
> **Target:** Amazon DynamoDB (with the AWS SDK v2 Enhanced Client / Spring Cloud AWS)  
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. The Data Model — Tables, Items, Attributes](#1-the-data-model--tables-items-attributes)
- [2. Primary Keys & Secondary Indexes](#2-primary-keys--secondary-indexes)
- [3. The Capacity Math Every Interviewer Asks About](#3-the-capacity-math-every-interviewer-asks-about)
- [4. Query, Scan & FilterExpressions](#4-query-scan--filterexpressions)
- [5. Single-Table Design](#5-single-table-design)
- [6. Advanced Modelling — Adjacency Lists, Sparse GSIs, Composite Sort Keys](#6-advanced-modelling--adjacency-lists-sparse-gsis-composite-sort-keys)
- [7. API Operations — Condition, Transaction, Batch](#7-api-operations--condition-transaction-batch)
- [8. Consistency, Transactions & Streams](#8-consistency-transactions--streams)
- [9. Hot Partitions, Throttling & Partition Math](#9-hot-partitions-throttling--partition-math)
- [10. TTL, Streams & Lambda Integration](#10-ttl-streams--lambda-integration)
- [11. Spring Cloud AWS / SDK v2 — The Java Side](#11-spring-cloud-aws--sdk-v2--the-java-side)
- [12. Level-Wise Interview Questions & Answers](#12-level-wise-interview-questions--answers)
- [13. Counter-Questions & Interviewer Traps](#13-counter-questions--interviews-traps)
- [14. Rapid-Fire MCQs](#14-rapid-fire-mcqs)
- [15. One-Page Cheat Sheet](#15-one-page-cheat-sheet)
- [16. Real-World Production Post-Mortems](#16-real-world-production-post-mortems)
- [17. Follow-up Learning](#17-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

1. **DynamoDB is a managed key-value/document hybrid with a 400KB item limit and a 2048-byte partition-key limit.** Every access pattern is a `Query` on a table or GSI — or it doesn't exist.
2. **Every table needs a partition key; every GSI needs its own.** Choose for cardinality and even traffic distribution, not for how you'd name the entity.
3. **Capacity is per-partition, not per-table:** a partition tops out at **3000 RCU / 1000 WCU** — a table sized to millions of RCU can still throttle on one hot item.
4. **`Query` reads items by partition key (cheap); `Scan` reads the whole table (expensive) and `FilterExpression` runs *after* the read but *before* the response — it does not save RCUs.**
5. **Single-table design** puts many entity types in one table with a type-prefixed partition key plus a sort-key hierarchy, so one `Query` returns a whole item collection — one round trip instead of N.
6. **`GSI` vs `LSI`:** a GSI has its **own partition key**, can be added any time, and can be eventually consistent (default) or strongly consistent; an LSI shares the table's partition key, must be created with the table, and caps a partition collection at **10GB**.
7. **Conditional writes** (`attribute_not_exists`, `attribute_exists`, comparisons) give you **optimistic concurrency** — this is the "no oversell" / idempotency pattern for DynamoDB.
8. **DynamoDB is not a relational database.** No joins, no aggregations, and modelling happens **up front**: the cost of a wrong key design is a migration, not an `ALTER TABLE`.

---

## 1. The Data Model — Tables, Items, Attributes

```
Table
 └── Item  (like a row, but only the attributes it needs; ≤ 400KB)
       ├── Attribute: Partition key (mandatory)
       ├── Attribute: Sort key (optional)
       └── Attribute: any other key/value pairs
```

### 1.1 Attribute types

| Type | Notes |
|---|---|
| `S` String | Max 400KB per value |
| `N` Number | Up to 38 digits of precision, arbitrary precision |
| `B` Binary | Base64 over the wire |
| `BOOL`, `NULL` | |
| `L` List | **Ordered** JSON array |
| `M` Map | Nested object |
| `SS`, `NS`, `BS` | String/Number/Binary **sets** (unordered, unique) |
| *(document)* | Nest arbitrarily — `L` of `M` etc. |

### 1.2 Naming conventions that make single-table design work

```text
PK  =  "USER#u-123"           (entity-type prefix + id)
SK  =  "PROFILE"              (the item's role within the collection)

Common example:
PK                 SK                 Type
USER#u-123         PROFILE            user metadata
USER#u-123         ORDER#2026-10-01   order (belongs to the user)
USER#u-123         ORDER#2026-10-05   another order of the same user
PRODUCT#p-9        METADATA           product metadata
PRODUCT#p-9        REVIEW#r-77        review on the product
```

The prefix hierarchy enables **hierarchical access patterns**: `Query(PK=USER#u-123, SK begins_with "ORDER#")` returns that user's orders in one call.

---

## 2. Primary Keys & Secondary Indexes

### 2.1 The table key

| Key type | Definition | Query |
|---|---|---|
| **Simple** | Partition key only (`PK = orderId`) | Exact item lookups only |
| **Composite** | Partition + sort key (`PK = userId, SK = orderDate`) | Partitions by user, range/sort by date |

### 2.2 Secondary indexes

| | **LSI** (Local Secondary Index) | **GSI** (Global Secondary Index) |
|---|---|---|
| Partition key | **Same as the table** | **Different** |
| Sort key | Optional (its own) | Optional (its own) |
| Created | Only with the table | **Any time** |
| Projection | Any subset | `KEYS_ONLY`, `INCLUDE`, `ALL` |
| Strong reads | ✅ | ❌ **eventually consistent only** |
| Limits | 10GB per partition collection, 5 per table | 20 per table (default quota) |
| Cost | Doubles table writes | Writes propagate asynchronously |
| Deleted | With the table | **Independently** |

**Sparse GSI trick:** because a GSI only contains items that have the indexed attributes, a `GSI` over `status` indexes *only the rows that have a `status`* — giving you "index a subset of items" for free. This is the mechanism behind single-table design's extra access patterns.

### 2.3 Choosing a partition key

```text
+----------------------------------------------+-------------------------------+
| Requirement                                  | Why                           |
+----------------------------------------------+-------------------------------+
| High cardinality                            | Avoids one partition holding  |
|                                              | many items                     |
| Uniform access distribution                  | Avoids 3000 RCU / 1000 WCU    |
|                                              | ceilings                      |
| Known at query time                         | You must query BY it           |
| Stable (rarely changes)                     | An update rewrites the item  |
+----------------------------------------------+-------------------------------+
```

**Bad keys:** `status` (few values), `date` (monotonic), `isActive`, `tenantId` where 99% of rows are one tenant.
**Good keys:** `userId`, `orderId`, `deviceId`, `(userId, bucket)` composite hashing.

---

## 3. The Capacity Math Every Interviewer Asks About

### 3.1 The units

```text
WRITES
  1 WCU  =  1 write per second for an item up to  1 KB
  Larger item  ->  round UP:  ceil(size_kb) WCU per write
  Examples: a 2KB item = 2 WCU; a 1.6KB item = 2 WCU

READS
  1 RCU (strongly consistent)  =  1 read/sec for an item up to  4 KB
  1 RCU (eventually consistent)=  1 read/sec for an item up to  8 KB (i.e. half cost)
  Larger item  ->  ceil(size_kb / 4) RCU   (strong)
                    ceil(size_kb / 8) RCU   (eventual)
```

**Worked example (the classic interview arithmetic):**

```text
Item size 1.2 KB, 50 reads/sec, strongly consistent
  -> ceil(1.2 / 4) = 1 RCU per read  (since 1.2KB falls in one 4KB block)
  -> 50 RCU

Item size 5 KB, 100 writes/sec
  -> ceil(5 / 1) = 5 WCU per write
  -> 500 WCU

Item size 7 KB, 10 reads/sec, eventually consistent
  -> ceil(7 / 8) = 1 RCU per read   (7KB < 8KB threshold)
  -> 10 RCU
```

### 3.2 Capacity modes

| Mode | Behaviour |
|---|---|
| **Provisioned** | You set RCU/WCU; **Auto Scaling** adjusts to target utilisation; cheaper at steady load |
| **On-demand** | Pay per request; no capacity to plan; handles spiky/unknown load; up to 2.5× more expensive |
| **Reserved capacity** | 1–3 year commitment discount on provisioned |

**Burst credit:** a provisioned table unused at its capacity accumulates burst credits, letting it absorb short spikes — but they deplete, and once gone, throttling.

### 3.3 The partition ceiling (the math nobody budgets for)

```text
  Table capacity      : 10,000 WCU
  # of partitions      : ceil(total / 1000 WCU) ...  (simplified: 10 partitions)
  Per-partition max    : 1000 WCU / 3000 RCU
  If 90% of traffic targets ONE partition key
      -> that partition caps at 1000 WCU while the table uses only 9000
      -> THROTTLED at 10% utilisation of provisioned capacity
```

This is why the plan isn't "size the table" but "verify the **distribution**".

---

## 4. Query, Scan & FilterExpressions

```java
// Query — the only efficient read. Needs the partition key.
QueryRequest q = QueryRequest.builder()
    .tableName("shop")
    .keyConditionExpression("PK = :pk AND begins_with(SK, :skp)")
    .expressionAttributeValues(Map.of(
        ":pk", AttributeValue.fromS("USER#u-123"),
        ":skp", AttributeValue.fromS("ORDER#")))
    .scanIndexForward(false)     // DESCENDING order on the sort key
    .limit(20)
    .build();

// Scan — reads the ENTIRE table, one page at a time. Avoid in hot paths.
// FilterExpression is applied AFTER the read is consumed -> NO RCU savings.
```

| Operation | Reads | RCU cost | Use |
|---|---|---|---|
| `GetItem` | One item by full key | 1 RCU per 4KB | Point lookups |
| `BatchGetItem` | Up to 100 items | Sum of individual reads | Parallel point lookups |
| `Query` | Items by partition key | Only items read | The default access pattern |
| `Scan` | Whole table | Every item scanned | Batch jobs, exports, admin |
| `FilterExpression` | Filters after retrieval | **No savings** | Narrowing output, not cost |
| `ProjectionExpression` | Only named attributes | Can halve RCU | Always project only what you need |

**Pagination:** `Query`/`Scan` return `LastEvaluatedKey` — treat it as an opaque cursor, **never** as a page number, and never assume result-set stability across pages (there is no offset in DynamoDB).

---

## 5. Single-Table Design

The pattern interviewers recognise as expertise — and the one juniors most often misapply.

```text
Multiple entity types, ONE table, hierarchical keys:

PK                     SK                       GSI1PK             GSI1SK
USER#u-123             PROFILE                  USER#u-123        "PROFILE"
USER#u-123             ORDER#2026-10-01#o-1     ORDER#o-1         "2026-10-01"
USER#u-123             ORDER#2026-10-05#o-2     ORDER#o-2         "2026-10-05"
PRODUCT#p-9            METADATA                 PRODUCT#p-9       "METADATA"
PRODUCT#p-9            REVIEW#r-77              REVIEW#r-77       "PRODUCT#p-9"
CART#c-99              ITEM#p-9                 CART#c-99         "ITEM#p-9"

Queries served:
  * user's profile          -> Query PK=USER#u-123, SK=PROFILE
  * user's orders (newest)  -> Query PK=USER#u-123, SK begins_with ORDER#
  * order by id             -> Query PK=USER#u-123, SK=ORDER#2026-10-01#o-1
  * order by id (any user)  -> Query GSI1PK=ORDER#o-1
  * product + its reviews   -> Query PK=PRODUCT#p-9
  * cart contents           -> Query PK=CART#c-99, SK begins_with ITEM#
```

**What it buys:** one round-trip per collection; every access pattern is a `Query`; atomic multi-entity writes via `TransactWriteItems`; no joins.

**What it costs:** modelling discipline, opaque schemas, difficult debugging, and a strong temptation to over-engineer. The correct guidance: **start with one table per entity, move to single-table when access-pattern fan-out becomes a real problem.**

---

## 6. Advanced Modelling — Adjacency Lists, Sparse GSIs, Composite Sort Keys

| Technique | Shape | Solves |
|---|---|---|
| **Adjacency list** | `PK = nodeId, SK = edgeType#targetId` | Graph-like traversal ("who follows X") |
| **Composite sort key** (multi-level) | `SK = CITY#office#DESK#id` | Range scans over any prefix of the hierarchy |
| **Sparse GSI** | Item present in GSI only when it has the attribute | "Open orders only" without a scan |
| **Time-bucketed / write-sharded** | `PK = itemId#<0..9>` from a hash of a request attr | Removes hot partitions |
| **GSI overloading** | Reuse one GSI for several access patterns | Fewer indexes = lower write amplification |
| **Time-series** | `PK = deviceId#date, SK = timestamp` | Bounded partitions, cheap range reads |
| **Materialised / denormalised copy** | Two items written in one transaction | Avoids a second query |

### 6.1 Write sharding concretely

```text
Problem:  a single celebrity item gets 20,000 reads/sec  (ceiling: 3,000 RCU)

Fix:  distribute across N computed partitions
  shard = hash(requestId) % 10
  PK    = "PROFILE#celeb#" + shard      → 10 partitions, each ~2,000 RCU
  Query all 10 shards + merge in the app (SingleTableDesign "broadcast query")

Better:  pre-compute the counter into 10 atomic-updated items and sum on read,
         or move the hot item into its own dedicated table/on-demand capacity.
```

---

## 7. API Operations — Condition, Transaction, Batch

### 7.1 Conditional writes

```java
// "Create only if absent" → idempotent order creation
PutItemRequest.builder()
    .tableName("shop")
    .item(item)
    .conditionExpression("attribute_not_exists(PK)")
    .build();
// -> ConditionalCheckFailedException if it already exists (safe retry → treat as success)

// "Decrement only if stock remains" → no oversell
UpdateItemRequest.builder()
    .tableName("shop")
    .key(Map.of("PK", av("INV#p-9")))
    .updateExpression("SET stock = stock - :d")
    .conditionExpression("stock >= :d")
    .expressionAttributeValues(Map.of(":d", av(1)))
    .build();
```

This is the primary correctness tool: **optimistic concurrency control** with `attribute_exists`, `attribute_not_exists`, and comparison expressions (`#stock > :zero`).

### 7.2 Transactions

```java
TransactWriteItemsRequest.builder()
    .transactItems(
        Put.builder().tableName("shop").item(orderItem).build(),                 // create order
        Update.builder().tableName("shop")                                        // decrement stock
               .key(Map.of("PK", av("INV#p-9")))
               .updateExpression("SET stock = stock - :q")
               .conditionExpression("stock >= :q")
               .build(),
        Put.builder().tableName("audit").item(auditItem).build())
    .build();
```

| Limit | Value |
|---|---|
| Items per transaction | **100** |
| Total size | **4 MB** |
| Cost | **2×** the equivalent non-transactional calls |
| Isolation | ACID, snapshot, all-or-nothing (serializable) |
| Conflicts | `TransactionCanceledException` → retry with jitter |

**Use transactions only for genuine multi-entity invariants** — they double cost and reduce throughput.

### 7.3 Batch

| API | Notes |
|---|---|
| `BatchWriteItem` | Up to **25** puts/deletes; no conditions; not atomic; retry `UnprocessedItems` |
| `BatchGetItem` | Up to **100** items; parallel point reads |
| `PartiQL` / `ExecuteStatement` | SQL-like `SELECT`/`INSERT`/`UPDATE` against a table or index (still one table at a time) |

---

## 8. Consistency, Transactions & Streams

```text
WRITE                                READ (default = eventual)
  └── 3 AZ replicas,                   └── served by one AZ replica
      synchronous across 3 AZs              → up to a few ms stale
  └── Quorum-style durable
  └── Strongly consistent read =        costs 2× RCU, reads the leader replica
     ConsistentRead = true                → linearizable for that item
```

- **Default is eventually consistent** — the read may not reflect a write from < ~20ms ago.
- **`ConsistentRead: true`** is available on **tables and LSIs**, but **not on GSIs**.
- Because replication is synchronous across 3 AZs, DynamoDB does not lose acknowledged writes in normal failure scenarios — the trade is *staleness*, not *loss*.
- **DynamoDB Streams** capture item-level changes (ordered per key) — the hook for Lambda, change data capture, materialised views, and cross-region replication (Global Tables).
- **Global Tables** (multi-region, multi-active) are last-writer-wins with vector clocks — concurrent edits in two regions can lose data; design for it.

---

## 9. Hot Partitions, Throttling & Partition Math

### 9.1 Diagnosis

```text
CloudWatch alarm to set:
  ThrottledRequests > 0 (any sustained period)     <- the symptom
  ConsumedReadCapacityUnits / ProvisionedReadCapacityUnits ≈ 1
  SystemErrors, SuccessfulRequestLatency p99 spikes
```

`ThrottledRequests` counts per-partition throttles, so a healthy average can hide 100% throttling on one key.

### 9.2 Fixes, in order of preference

| Symptom | Fix |
|---|---|
| Monotonic key (`ORDER#<timestamp>`) all writing to one partition | **Hash/randomise the suffix**: `ORDER#<shard>#<ts>` |
| One celebrity/hot item absorbing reads | **Write-shard the item** across N partitions; read-merge |
| Genuine high throughput on a single key | **Dedicated table** with on-demand capacity |
| Short spikes | **Burst credits** or switch to on-demand / enable adaptive capacity |
| Many small items under one tenant | Shard by tenant segment `(tenantId, segment)` |
| Analytics | Move to S3 + Athena/Redshift — DynamoDB is a lookup engine |

### 9.3 The calculation to say out loud

```text
Item 3 KB, 20,000 reads/sec, eventually consistent
  per-read RCU = ceil(3 / 8) = 1
  total        = 20,000 RCU
  partitions   = ceil(20,000 / 3,000) = 7 minimum by read throughput
  BUT: if all reads target one partition key, ONE partition throttles at 3,000 RCU
       -> we need sharding, not more capacity.
```

---

## 10. TTL, Streams & Lambda Integration

```java
// TTL — DynamoDB deletes expired items in the background (typically within 48h,
//       and free of charge). Use it instead of explicit deletes for time-bound data.
UpdateItemRequest.builder()
    .tableName("sessions")
    .key(Map.of("PK", av("SESSION#s-1")))
    .updateExpression("SET expiresAt = :ttl")
    .expressionAttributeValues(Map.of(
        ":ttl", av(Instant.now().plus(Duration.ofHours(2)).getEpochSecond())))
    .build();
// ItemAttributeName: "expiresAt" must be a NUMBER (epoch seconds) and is deleted
// within 48h, NOT at the exact second.

// Streams -> Lambda: the change-data-capture pattern
//  EventSourceMapping (batch size, parallelization factor, on-failure destination,
//  filtered view types INSERT | MODIFY | REMOVE | NEW_IMAGE)
```

| Feature | Detail |
|---|---|
| **TTL** | Free, background, ~48h latency; use for sessions, temp rows, versioned data |
| **Streams** | 24h retention, ordered per key; `NEW_IMAGE`, `OLD_IMAGE`, `KEYS_ONLY`, `NEW_AND_OLD_IMAGES` |
| **Point-in-time recovery** | Continuous backups, 35 days, restores to any second |
| **Global Tables** | Multi-region, multi-active, eventual, last-writer-wins |
| **DAX** | In-memory microsecond cache (write-through, eventually consistent) |
| **Export to S3** | Full table export without consuming RCU — the ETL path |

---

## 11. Spring Cloud AWS / SDK v2 — The Java Side

```java
@Configuration
public class DynamoConfig {
    @Bean
    DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
            .region(Region.EU_WEST_1)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .overrideConfiguration(o -> o.retryPolicy(RetryPolicy.builder()
                 .numRetries(5).backoffStrategy(FullJitterBackoffStrategy.create())))
            .build();
    }

    @Bean
    DynamoDbEnhancedClient enhanced(DynamoDbClient c) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(c).build();
    }
}

@DynamoDbBean
public class Order {
    @Getter @Setter @DynamoDbPartitionKey private String pk;      // USER#u-123
    @Getter @Setter @DynamoDbSecondaryPartitionKey(indexNames = "GSI_ORDER_BY_ID") private String gsiOrderPk;
    @Getter @Setter @DynamoDbSortKey private String sk;           // ORDER#2026-10-01#o-1
    @Getter @Setter @DynamoDbSecondarySortKey(indexNames = "GSI_ORDER_BY_DATE") private String orderDate;
    private String status; private BigDecimal total;

    // Static factory: key construction belongs in the domain, not the caller
    public static Order of(String userId, String orderId, String date, String status, BigDecimal total) {
        Order o = new Order();
        o.pk = "USER#" + userId; o.sk = "ORDER#" + date + "#" + orderId;
        o.gsiOrderPk = "ORDER#" + orderId; o.orderDate = date;
        o.status = status; o.total = total; return o;
    }
}

@Repository
public class OrderDao {
    private final DynamoDbTable<Order> table;
    public OrderDao(DynamoDbEnhancedClient client) {
        this.table = client.table("shop", TableSchema.fromBean(Order.class));
    }

    public List<Order> ordersForUser(String userId, int limit) {
        return table.query(r -> r.queryConditional(
                QueryConditional.sortBeginsWith(Key.builder()
                    .partitionValue("USER#" + userId).sortValue("ORDER#").build()))
            .scanIndexForward(false).limit(limit)).items().stream().toList();
    }

    public void placeOrderAtomically(Order order, InventoryItem inv) {
        client.transactWriteItems(b -> b
            .addPut(table, order)
            .addUpdate(invTable, inv.toUpdate()));    // conditional stock decrement
    }
}
```

**Operational rules:**
- Always set a **retry policy with jitter** and handle `ProvisionedThroughputExceededException` with backoff — never retry without jitter into a throttled partition.
- Enable **SDK metrics** (`software.amazon.awssdk.metrics`) and CloudWatch Insights; `User-Agent`/`RetryCount` appear in the metrics.
- **Never map a Java entity graph to DynamoDB and hope** — a `@OneToMany` ignored by the schema will silently truncate relationships; model intentionally.
- Keep `ProjectionExpression` in every `Query` — it is the cheapest RCU optimisation available.

---

## 12. Level-Wise Interview Questions & Answers

### 🟢 Junior (0–2 Years)

**Q1. What is an item and what's its size limit?**
A: An item is a DynamoDB row — a collection of attributes keyed by a partition key (and optional sort key). Maximum size is **400 KB**.

**Q2. What is the difference between `Query` and `Scan`?**
A: `Query` finds items by partition key (and optional sort-key conditions) and costs only for what it reads. `Scan` reads the entire table and `FilterExpression` reduces the *response*, not the consumed capacity.

### 🟡 Mid-Level (2–5 Years)

**Q3. What is the difference between a GSI and an LSI?**
A: A GSI is a separate index with its **own partition key**, creatable at any time, eventually or strongly consistent for queries on it, and deletable independently. An LSI shares the table's partition key, must be defined at table creation, supports strong consistency, and constrains each partition collection to **10GB**.

**Q4. How many WCUs does a 5 KB item consume per write?**
A: `ceil(5 / 1) = 5 WCU` (one WCU per KB, rounded up).

**Q5. How do you prevent overselling inventory?**
A: A conditional update: `SET stock = stock - :q` with `conditionExpression("stock >= :q")`. DynamoDB atomically evaluates the condition and the update; concurrent decrements serialise, and the loser gets `ConditionalCheckFailedException` — after which I retry or fail the order.

**Q6. What happens if a hot partition throttles while the table is at 30% utilisation?**
A: Each partition has its own ceiling (1000 WCU / 3000 RCU) independent of total table capacity, so one hot key throttles even when the table is mostly idle. Fix by sharding the key or using on-demand capacity.

**Q7. How does DynamoDB handle consistency?**
A: Replication is synchronous across 3 AZs, so acknowledged writes are durable. Reads are **eventually consistent by default**; `ConsistentRead=true` gives a linearizable read of that item at 2× RCU, but is not available on GSIs.

### 🔴 Senior (5–8 Years)

**Q8. Explain single-table design with a concrete example.**
A: Multiple entity types share one table, keyed by a type-prefixed partition key and a hierarchical sort key (`USER#u-1` + `ORDER#2026-10-01#o-9`). One `Query` returns an entire collection (a user's orders) in a single round trip; `GSI overloading` (sparse indexes) adds new access patterns without new tables; multi-entity writes become one `TransactWriteItems`. I start per-entity and consolidate when fan-out becomes a measured problem.

**Q9. Your `Query` returns a `LastEvaluatedKey` on page 1 but page 2 is empty. Why?**
A: Because pages are unstable cursors, not offsets: concurrent writes between pages shift the underlying partition, and pagination assumes no `LastEvaluatedKey` means the end. Always paginate to exhaustion, treat the key as opaque, and never build "page N" URLs from it.

**Q10. Design the DynamoDB schema for a chat system: rooms, members, messages.**
A: One table with `PK = ROOM#roomId, SK = MEMBER#userId | MESSAGE#timestamp#msgId`. Member items give membership lookups; messages are range-read by timestamp desc with `LIMIT`. A GSI over `MESSAGE#...` reverse-indexes "messages by id" for deep links. TTL on ephemeral messages; a second GSI for unread counts. No joins — every list is a `Query` on the room partition.

**Q11. When is DynamoDB the wrong tool?**
A: Ad-hoc analytics, relational integrity across many entities, complex reporting with aggregations, and workloads where access patterns are still churning weekly (the modelling cost is front-loaded). Also when item sizes approach 400 KB — that signals the wrong storage shape (move the blob to S3 and store the reference).

### ⚫ Principal / Staff (8+ Years)

**Q12. Multi-region writes with Global Tables: your stock counter diverges. What happened?**
A: Global Tables are **active-active, multi-master with last-writer-wins and vector-clock conflict resolution**. Two regions decremented concurrently; only one write survived the merge. For invariants that must not converge-by-luck, use a single-region authoritative table for the counter plus Global Tables for read-anywhere data, or serialise the mutation through a queue with a single consumer per key.

**Q13. Justify "no joins" to a team that wants to port a reporting dashboard to DynamoDB.**
A: The dashboard's queries filter across attributes and aggregate over time — precisely the pattern that becomes a `Scan` consuming the entire table's RCU. The correct answer is a **CQRS split**: DynamoDB remains the operational system of record, and the reads are served by a projected store (a stream-driven Elasticsearch/Red Athens/S3-Athena layer) designed for the query shape. Porting the SQL into a key-value store is the classic "wrong tool, right data" failure.

---

## 13. Counter-Questions & Interviewer Traps

**Trap 1: "GSIs are strongly consistent."** — No. GSI queries are **always eventually consistent**; `ConsistentRead` works on tables and LSIs only.

**Trap 2: "`FilterExpression` saves RCUs."** — It is evaluated after the read is consumed. Only **`ProjectionExpression`** and **fewer items read** reduce RCU.

**Trap 3: "Auto Scaling fixes everything."** — Auto Scaling tracks table-level metrics and target utilisation; it does not redistribute one partition key's traffic. Hot-key problems need key redesign.

**Trap 4: "DynamoDB is just a fast hash map."** — It is a fully managed, replicated, provisioned-throughput storage engine with ACID transactions, streams, TTL, PITR, and global tables — the constraints come from the key-value access pattern, not from capability.

**Trap 5: "Use `Scan` with `FilterExpression` for lookups."** — Works correctly on small tables and destroys performance on large ones (full-table RCU burn + latency). The answer to "query by attribute X" is a GSI, not a scan.

**Trap 6: "A transaction is the same cost as a batch."** — No: transactions cost roughly **2×** the equivalent individual calls and cap at 100 items / 4MB.

---

## 14. Rapid-Fire MCQs

**Q1. Maximum item size:**
- [ ] A) 64 KB
- [ ] B) 400 KB
- [ ] C) 1 MB
- [ ] D) 4 MB
* **Answer:** **B.**

**Q2. A strongly consistent read of a 6 KB item costs:**
- [ ] A) 1 RCU
- [ ] B) 2 RCU
- [ ] C) 6 RCU
- [ ] D) 8 RCU
* **Answer:** **B** — `ceil(6/4) = 2`.

**Q3. Which is TRUE about GSIs?**
- [ ] A) Strongly consistent reads
- [ ] B) Same partition key as the table
- [ ] C) Own partition key, eventually consistent, creatable anytime
- [ ] D) Deleted when the table is deleted
* **Answer:** **C.**

**Q4. Max items in a `TransactWriteItems`:**
- [ ] A) 10
- [ ] B) 25
- [ ] C) 100
- [ ] D) 400
* **Answer:** **C** (and 4 MB total).

**Q5. The fix for a hot partition is:**
- [ ] A) Scale up the table
- [ ] B) Add a GSI
- [ ] C) Write-shard the partition key
- [ ] D) Enable PITR
* **Answer:** **C.**

**Q6. `Scan` + `FilterExpression` vs `Query`:**
- [ ] A) Scan is cheaper when filtering
- [ ] B) Scan reads the whole table; the filter does not reduce consumed capacity
- [ ] C) `FilterExpression` makes Scan fast
- [ ] D) They are equivalent
* **Answer:** **B.**

**Q7. An LSI can be created:**
- [ ] A) Any time after table creation
- [ ] B) Only at table creation
- [ ] C) Only on on-demand tables
- [ ] D) Never
* **Answer:** **B.**

**Q8. TTL deletion:**
- [ ] A) Is immediate and exact
- [ ] B) Is free and typically completes within ~48 hours
- [ ] C) Consumes WCU
- [ ] D) Requires a Lambda trigger
* **Answer:** **B.**

---

## 15. One-Page Cheat Sheet

```text
+------------------------------+--------------------------------------------------+
| CONCEPT                      | RULE                                             |
+------------------------------+--------------------------------------------------+
| Item limit                   | 400 KB; partition key <= 2048 bytes              |
| Key                          | PK (mandatory) + SK (optional)                   |
| GSI                          | Own partition key, eventual only, add anytime    |
| LSI                          | Same partition key, table-creation only, 10GB    |
| Sparse GSI                   | Only items WITH the attribute are indexed       |
| WCU math                     | ceil(item_kb / 1) WCU per write                  |
| RCU math (strong)            | ceil(item_kb / 4) RCU per read                   |
| RCU math (eventual)          | ceil(item_kb / 8) RCU per read                   |
| Partition ceiling            | 3,000 RCU / 1,000 WCU PER PARTITION             |
| Throttling on 10% usage      | hot partition -> shard the key                  |
| Query                        | BY PARTITION KEY only; sort-key conds optional  |
| FilterExpression             | after read -> NO RCU savings                    |
| ProjectionExpression         | the cheapest RCU optimisation                   |
| Conditional write            | optimistic concurrency / no-oversell            |
| Transaction                  | <=100 items, <=4MB, ~2x cost, ACID              |
| BatchWriteItem               | <=25 items, not atomic, retry UnprocessedItems |
| Single-table design          | TYPE#id keys + hierarchical sort key + GSIs     |
| Hot-item fix                 | write-shard across N partitions, read-merge     |
| Pagination                   | opaque LastEvaluatedKey, not a page number      |
| TTL                          | free, background, ~48h                          |
| Global Tables                | active-active, last-writer-wins                 |
+------------------------------+--------------------------------------------------+
```

---

## 16. Real-World Production Post-Mortems

### Incident 1: The celebrity partition
A social-app profile table keyed on `userId` took a celebrity signup; 18,000 reads/sec hit one partition. The table was provisioned at 40,000 RCU and sat at 45% utilisation while `ThrottledRequests` climbed. **Fix:** an on-demand "hot profiles" table plus a request-level read cache (DAX in front of the hot path). The incident crystallised the distinction between *table capacity* and *partition capacity*.

### Incident 2: `FilterExpression` masking cost
A nightly export used `Scan` + `FilterExpression` on a 900M-item table, consuming the table's entire monthly capacity in one night. **Fix:** `Query` per time bucket with a projection, plus an S3 export (`ExportTableToPointInTime`) that consumes **zero RCU** — and a budget alarm set at 20% of monthly provisioned throughput.

### Incident 3: Transaction misuse
A team put every write path inside `TransactWriteItems` for "consistency". At 4× the traffic the bill tripled and throughput halved. **Fix:** converted to conditional single-item writes (`attribute_not_exists`) plus a batch job, reserving transactions for the twelve genuinely multi-entity operations.

---

## 17. Follow-up Learning
- **Next Chapter:** → [05 · Redis, Graph & Time-Series — NoSQL Interview Deep Dive](./05-Redis-Graph-and-TimeSeries-NoSQL-Interview-Deep-Dive.md)
- **Previous Chapter:** ← [03 · Cassandra — Column-Family, Partitioning & Tunable Consistency](./03-Cassandra-Column-Family-Partitioning-and-Tunable-Consistency.md)
- **Caching patterns (cache-aside, write-through):** [../../SPRING_BOOT/10-Caching/01-Caching-Fundamentals-and-Patterns.md](../../SPRING_BOOT/10-Caching/01-Caching-Fundamentals-and-Patterns.md)
- **Master Kit Index & Study Roadmap:** ← [README](./README.md)
