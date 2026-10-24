# 06 · NoSQL Selection, Polyglot Persistence & Spring Data Integration

> **Level:** Mid → Staff (the "which one, and how does it fit my Spring app?" chapter)  
> **Interview Frequency:** ★★★★☆ (this is the design-round chapter)  
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. The Selection Framework](#1-the-selection-framework)
- [2. The Decision Matrix](#2-the-decision-matrix)
- [3. The Selection Flowchart](#3-the-selection-flowchart)
- [4. Polyglot Persistence & CQRS](#4-polyglot-persistence--cqrs)
- [5. What Belongs Where — A Reference Architecture](#5-what-belongs-where--a-reference-architecture)
- [6. Spring Data — The Common Architecture](#6-spring-data--the-common-architecture)
- [7. Spring Data MongoDB — Production Config](#7-spring-data-mongodb--production-config)
- [8. Spring Data Cassandra — Production Config](#8-spring-data-cassandra--production-config)
- [9. DynamoDB from Spring SDK v2](#9-dynamodb-from-spring-sdk-v2)
- [10. Redis & Spring Cache Abstraction](#10-redis--spring-cache-abstraction)
- [11. Cross-Cutting Concerns](#11-cross-cutting-concerns)
- [12. Migration Strategies](#12-migration-strategies)
- [13. Level-Wise Interview Questions & Answers](#13-level-wise-interview-questions--answers)
- [14. Counter-Questions & Interviewer Traps](#14-counter-questions--interviews-traps)
- [15. Rapid-Fire MCQs](#15-rapid-fire-mcqs)
- [16. One-Page Cheat Sheet](#16-one-page-cheat-sheet)
- [17. Follow-up Learning](#17-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

1. **Select by access pattern, then consistency, then scale, then operations — in that order.** "Which NoSQL?" is unanswerable without knowing the queries first.
2. **A polyglot architecture is normal, not exotic:** Postgres for the ledger, DynamoDB for carts, Elasticsearch for search, Redis for hot state, Cassandra for event logs. Each store earns its place with a specific access pattern.
3. **Keep Spring Data behind your own repository interfaces** — never leak `MongoRepository`, `CassandraRepository`, or the DynamoDB SDK into service/domain layers.
4. **CQRS is the bridge between stores:** the write model stays transactional and authoritative, and read models are projections kept current by streams/CDC.
5. **Migration between NoSQL engines is a query-pattern migration**, not a schema migration: the hard part is discovering every access pattern the old store implicitly supported.

---

## 1. The Selection Framework

Run these questions in order; a "no" prunes the branch.

```text
1. ACCESS PATTERN   What are the exact queries? Key lookup? Time range?
                    Traversal? Text search? Aggregation?
                      └── Unknown/changing  -> Postgres (schema-first, retry NoSQL later)

2. CONSISTENCY      Must concurrent invariants hold? Money, inventory, seats?
                      └── Strong + multi-entity -> Postgres / CockroachDB / Spanner
                      └── Eventual acceptable   -> Cassandra / DynamoDB / Mongo

3. SCALE            One node enough? Write volume? Item count? Geography?
                      └── Massive write throughput, unbounded -> Cassandra
                      └── Huge + simple key lookups          -> DynamoDB
                      └── Flexible nested documents          -> MongoDB

4. LATENCY          Sub-ms reads -> Redis in front
                    Regional latency -> multi-DC/multi-region design

5. OPERATIONS       Who runs it on-call? (Managed DynamoDB ≪ self-hosted Cassandra)
```

**The three most common selection errors:**
1. Choosing a store from a blog post instead of from the query list.
2. Choosing NoSQL for a dataset that comfortably fits one Postgres node.
3. Choosing a document store because "schema changes weekly" — then discovering the access pattern is a join.

---

## 2. The Decision Matrix

| Need | Answer | Why |
|---|---|---|
| Financial ledger, invoicing, accounting | **PostgreSQL** | ACID + arbitrary SQL + reporting in one box |
| Inventory "must not oversell" at scale | DynamoDB conditional write **or** Postgres | Linearizable counter is the requirement |
| Shopping cart (read-your-writes, multi-device) | **DynamoDB** (simple PK + conditional merge) or Mongo | Conflict resolution, regional reads |
| User profiles / catalogue / content | **MongoDB** | Nested documents + flexible schema |
| Event log / IoT / telemetry / time-series | **Cassandra** + TWCS | Write-optimised, TTL, bounded partitions |
| Session state, rate limits, leaderboards, locks | **Redis** | Sub-ms, data structures, rebuildable |
| Full-text / fuzzy / faceted search | **Elasticsearch** | Inverted index + relevance; derived from the SoR |
| Social graph / fraud rings / recommendations | **Neo4j** | Variable-depth traversal |
| Gaming state, ad-tech counters | **DynamoDB** / Redis | Massive key lookups, atomic counters |
| Multi-region writes with local latency | **DynamoDB Global Tables** or Cassandra multi-DC | Active-active, tunable |
| Strict multi-region consistency | **CockroachDB / Spanner** | They trade latency for linearizability |
| Ad-hoc analytics on the operational store | **S3 + Athena / Redshift** | Never query the operational store for reports |

---

## 3. The Selection Flowchart

```
                 ┌─────────────────────────────────┐
                 │ 1. Do you know the queries?      │
                 └───────────────┬─────────────────┘
                       NO        │        YES
                        │        │         │
          Postgres (+ revisit later)        │
                                            ▼
                 ┌─────────────────────────────────┐
                 │ 2. Do invariants span entities? │
                 └───────────────┬─────────────────┘
                       YES       │        NO
                         │       │         │
              Cockroach/Postgres │          ▼
                                ┌──────────────────────────┐
                                │ 3. What is the shape?    │
                                └────────────┬─────────────┘
              ┌───────────────┬──────────────┼───────────────┬───────────────┐
              ▼               ▼              ▼               ▼               ▼
        Nested docs    Key lookup,     Append-only      Traversal,      Sub-ms hot
        flexible       flexible        time-range       variable-depth  state, rebuildable
              │        access patterns     │            relationships         │
              ▼               ▼              ▼               ▼               ▼
         MongoDB       DynamoDB       Cassandra+TWCS      Neo4j            Redis
                                  (or TimescaleDB/
                                   InfluxDB)
```

---

## 4. Polyglot Persistence & CQRS

### 4.1 CQRS in the NoSQL world

```
   ┌──────────────┐     writes       ┌───────────────────────────────┐
   │   Client     │ ───────────────► │  WRITE MODEL (authoritative)  │
   └──────────────┘                  │  Postgres / DynamoDB / Mongo  │
                                     └───────────────┬───────────────┘
                                                     │ CDC / streams
                                                     ▼
   ┌──────────────┐     reads        ┌───────────────────────────────┐
   │   Client     │ ◄─────────────── │  READ MODELS (projections)    │
   └──────────────┘                  │  Elasticsearch / Redis /      │
                                     │  Cassandra query tables /     │
                                     │  DynamoDB GSIs                │
                                     └───────────────────────────────┘
```

**Why it works for NoSQL:** a single store forces a single shape on both writes and reads. Cassandra's query tables are *exactly* read models materialised by the application; DynamoDB GSIs are read models materialised by the engine; Elasticsearch is a read model materialised by a stream. CQRS is not an add-on — the best NoSQL schemas are already CQRS.

**The costs:** eventual consistency between models (design for it, show "processing" states), reconciliation jobs, and debugging "why did my write not appear" (lag, not loss).

### 4.2 Change data capture options

| Mechanism | Latency | Notes |
|---|---|---|
| **DynamoDB Streams → Lambda** | ms | Ordered per key; perfect for projections |
| **MongoDB Change Streams** | ms | Resume-token aware; also feeds rollups |
| **Debezium on Postgres WAL** | ms-seconds | Universal CDC into Kafka |
| **Cassandra CDC (commitlog)** | seconds | Less mature; often dual-write is simpler |
| **Dual-write from the app** | ms | **Consistency hazard** without an outbox; use only with the transactional-outbox pattern |

---

## 5. What Belongs Where — A Reference Architecture

A realistic e-commerce platform (the answer to *"design this system"*):

```text
+---------------------------------------------------------------+
| Product                               Postgres     (source of  |
| catalogue (source of truth)                         truth)   |
|         │                                                    |
|         └─► Elasticsearch (search/autocomplete/facets)        |
|                                                                |
| Orders & payments                Postgres (ACID ledger)        |
|         └─► DynamoDB/Redis (denormalised "order status" view) |
|                                                                |
| Carts                             DynamoDB (regional reads,    |
|                                    conditional merge)          |
|                                                                |
| Inventory reservations            Postgres  (SELECT ...        |
|                                    FOR UPDATE) or DynamoDB     |
|                                    conditional decrement       |
|                                                                |
| Event log / clickstream           Cassandra + TTL + TWCS       |
|                                                                |
| Sessions, rate limits             Redis                         |
|                                                                |
| Search index / facets            Elasticsearch (derived)      |
+---------------------------------------------------------------+
```

**The interview sentence:** *"I keep money and inventory invariant in one ACID store, because that is where correctness is cheapest. Everything else is a read-optimised projection or an eventually-consistent cache. The reason this is not 'NoSQL everywhere' is that each store earns a place with a specific access pattern — and the ledger would be much harder to get right in a last-write-wins store."*

---

## 6. Spring Data — The Common Architecture

```java
// Domain code depends on your interface, never on a Spring Data type
public interface OrderRepository {                 // your abstraction
    Optional<Order> findById(OrderId id);
    Page<Order> recentForUser(UserId id, Instant since, Pageable p);
    void save(Order order);
}

@Repository
public class MongoOrderRepository implements OrderRepository {   // adapter
    private final SpringMongoOrderRepository delegate;          // MongoRepository impl
    public Optional<Order> findById(OrderId id) {
        return delegate.findById(id.value()).map(OrderMapper::toDomain);
    }
}
```

**Rules:**
- One repository interface per bounded context; adapters per persistence technology.
- Never return Spring Data entities outside the adapter (mapping at the boundary prevents persistence concepts leaking into the domain).
- One transaction manager per store — **Spring cannot span Postgres and Mongo atomically**; use a saga/outbox instead.
- Prefer `@Transactional` on Postgres/JPA paths; treat MongoDB/Cassandra/DynamoDB as their own consistency islands.

---

## 7. Spring Data MongoDB — Production Config

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://m1:27017,m2:27017,m3:27017/shop?replicaSet=rs0&w=majority&readConcern=majority&maxPoolSize=100
      auto-index-creation: false      # ⚠ NEVER on in production
```

```java
@Configuration
@EnableMongoAuditing
public class MongoConfig extends AbstractMongoClientConfiguration {
    @Override
    protected String getDatabaseName() { return "shop"; }

    @Bean
    MongoCustomConversions customConversions() {           // BigDecimal <-> Decimal128
        return new MongoCustomConversions(List.of(
            new BigDecimalToDecimal128Converter(),
            new Decimal128ToBigDecimalConverter()));
    }
}
```

**Production checklist:**
- `auto-index-creation: false` — indexes are DDL; build them in migration scripts, not at boot.
- Set `w: majority` and `readConcern: majority` in the URI.
- Size `maxPoolSize` from the same core-count math as HikariCP (→ Ch. 09 SQL).
- Enable the slow-query log: `operationProfiling.slowOpThresholdMs: 100`, and alert on `COLLSCAN`.
- Prefer `MongoTemplate` for pipelines; repositories for CRUD.

---

## 8. Spring Data Cassandra — Production Config

```yaml
spring:
  cassandra:
    contact-points: 10.0.1.10,10.0.1.11,10.0.1.12
    local-datacenter: dc1
    keyspace-name: shop
    schema-action: NONE                 # ⚠ CQL scripts in version control
    request:
      timeout: 10s
      consistency: LOCAL_QUORUM
      serial-consistency: LOCAL_SERIAL
      page-size: 5000
```

```java
@Configuration
public class CassandraConfig extends AbstractCassandraConfiguration {
    @Override protected String getKeyspaceName() { return "shop"; }
    @Override protected String getLocalDataCenter() { return "dc1"; }
    @Override protected SchemaAction getSchemaAction() { return SchemaAction.NONE; }

    @Bean
    CqlSessionFactoryBean<?> session() { /* tuning, reconnection, load-balancing */ return null; }
}
```

**Production checklist:**
- **`local-datacenter` is mandatory** — a missing value breaks the driver's routing.
- **Schema via migration tooling** (`cqlsh` scripts, or `schema-action: NONE`), because `CREATE_IF_NOT_EXISTS` at boot races across app instances.
- Consistency defaults per repository method via `@Consistency(LOCAL_QUORUM)`.
- Watch driver metrics: `cql-requests`, `errors.timeout`, `bytes-sent` — rising timeouts mean partitions or consistency levels are wrong.
- `page-size` controls server-side paging; too small = latency, too large = memory.

---

## 9. DynamoDB from Spring SDK v2

Spring Data does not ship a DynamoDB repository; the AWS SDK v2 **Enhanced Client** is the idiomatic Java layer (see [Chapter 04](./04-DynamoDB-Key-Value-Design-Partition-Math-and-Single-Table-Modelling.md) for the full model).

```java
@Configuration
public class DynamoConfig {
    @Bean DynamoDbClient client(Environment env) {
        return DynamoDbClient.builder()
            .region(Region.of(env.getProperty("aws.region")))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .overrideConfiguration(o -> o
                .apiCallTimeout(Duration.ofSeconds(3))
                .apiCallAttemptTimeout(Duration.ofSeconds(2))
                .retryPolicy(RetryPolicy.builder()
                    .numRetries(5)
                    .retryCondition(RetryCondition.defaultRetryCondition()
                        .or(RetryCondition.defaultRetryCondition()))
                    .backoffStrategy(FullJitterBackoffStrategy.create())
                    .throttlingBackoffStrategy(
                       AdaptiveRetryStrategy
                         .builder()
                         .build()))
                    .build()))
            .build();
    }
}
```

**Rules:**
- Always configure **jittered backoff** and a retry policy that includes `ProvisionedThroughputExceeded`.
- Hide the SDK behind your domain repository; map at the boundary.
- Every access pattern must be a `Query` on a table or GSI — if not, the design is wrong, not the code.
- Put `ProjectionExpression` in every query.
- Use `TableSchema.fromBean` for mapping, but construct keys explicitly in domain factory methods (never let the caller build key strings).

---

## 10. Redis & Spring Cache Abstraction

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        RedisCacheConfiguration cfg = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();            // avoid null-poisoned caches
        return RedisCacheManager.builder(cf).cacheDefaults(cfg)
            .withCacheConfiguration("products",                // per-cache TTLs
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(1)))
            .build();
    }
}
```

Patterns (fully covered in [Spring Boot 10-Caching](../../SPRING_BOOT/10-Caching/01-Caching-Fundamentals-and-Patterns.md)):
- **Cache-aside** — the default: read cache → miss → load from store → populate.
- **Write-through** — write cache + store synchronously.
- **Write-behind** — write cache, async flush (risk of loss).
- **Read-through** — the cache library loads it for you.
- **Refresh-ahead** — proactively refresh hot keys before expiry (prevents the stampede).

**Spring gotchas:**
- `@Cacheable` uses the method arguments as the key — complex objects need a custom `KeyGenerator` or SpEL key.
- `@CacheEvict(allEntries = true)` on a distributed Redis flushes **all app instances'** entries (it is a Redis `SCAN`+`DEL`).
- Cache keys from user-controlled input are a security boundary (key smashing / cross-tenant data leakage).

---

## 11. Cross-Cutting Concerns

| Concern | Approach |
|---|---|
| **Transactions across stores** | Impossible atomically; use the **outbox** + saga. Write to Postgres + outbox row in one tx; a relay publishes the event |
| **Idempotency** | Natural keys with conditional writes (DynamoDB `attribute_not_exists`, Mongo unique index) |
| **Read-your-writes across stores** | Read the projection from the authoritative store for a TTL, or use a "write epoch" client-side |
| **Observability** | Micrometer timers per repository method; tag by store; alert on p99 per store, not per endpoint |
| **Schema evolution** | Version documents (Mongo), add columns (Cassandra/CQL), keep old readers working |
| **Backfills** | Batched, throttled, resumable; never a single giant `updateMany` |
| **Cost control** | DynamoDB budget alarms per table; Cassandra compaction/size alerts; Redis memory alerts |
| **Testing** | Testcontainers for Mongo/Cassandra/Redis; LocalStack for DynamoDB — never test against a shared dev cluster |

---

## 12. Migration Strategies

### 12.1 SQL → NoSQL (the classic rewrite)

1. **Enumerate every query first** — mine the slow-query log, ORM call sites, and reporting jobs. This list *is* the new schema.
2. **Derive the key model** per access pattern (partition keys, sort keys, item collections).
3. **Dual-write** (preferably with an **outbox**, never a naive dual-write) into the new store.
4. **Backfill** existing data in batches, in dependency order, resumable, with verification counts.
5. **Shadow-read** and compare correctness for every access pattern, continuously.
6. **Cut reads over** per endpoint, with a rollback flag.
7. **Stop dual-writing**, then remove the old path.

### 12.2 NoSQL → NoSQL

Same shape, but the risk is *access-pattern discovery*, because NoSQL made the old assumptions invisible. A Cassandra → ScyllaDB move is a CQL-compatible lift-and-shift; an RDS → DynamoDB move is a redesign.

### 12.3 The reversibility rule

**Make every migration reversible**: keep the outbox, keep the old store available, keep both read paths behind a feature flag. A migration you cannot roll back is a deployment, not a migration.

---

## 13. Level-Wise Interview Questions & Answers

### 🟢 Junior (0–2 Years)

**Q1. When would you use MongoDB instead of a relational database?**
A: When the data is naturally nested and self-contained, the schema changes faster than I can migrate, and my access is by document or an indexed field. When I need joins, ad-hoc reporting, or strong multi-entity invariants, a relational database stays a better fit.

### 🟡 Mid-Level (2–5 Years)

**Q2. How do you pick between MongoDB, Cassandra and DynamoDB?**
A: **MongoDB** when documents are nested, schema is flexible, and I need rich secondary queries. **Cassandra** when writes are unbounded and time-series-shaped, and every query is keyed. **DynamoDB** when access is simple key lookup at massive scale and I want zero operational burden (managed). The deciding inputs are the query list, the consistency requirement, and the write shape.

**Q3. What is polyglot persistence?**
A: Using multiple storage technologies in one system, each chosen for what it is good at — a relational ledger, a search index, a cache, an event store. The risk is operational complexity and cross-store consistency, which is why the authoritative store and the projections must be clearly separated.

**Q4. How do you keep an Elasticsearch index consistent with Postgres?**
A: Never dual-write blindly. Capture changes from the WAL (Debezium) or from an outbox event, order them per document, and re-index on restart. Use a natural external version/id so events are idempotent. Design so the index is disposable: if it is wrong or lost, rebuilding from Postgres must be a job, not a project.

### 🔴 Senior (5–8 Years)

**Q5. Design the write path for "add to cart" across web and mobile with offline edits.**
A: The cart is a convergible aggregate. Model it as a **per-user cart with a vector/epoch** and merge with a deterministic rule (last-writer-wins per SKU with a client timestamp, plus a conflict resolution on quantity: sum for adds, explicit remove wins). DynamoDB with conditional writes handles the merge atomically per user; conflicts across devices are user-visible only when timestamps are equal, which I break with a device id. The important part is a **convergence rule, not a distributed lock** — offline clients cannot be forced into a lock.

**Q6. Your team wants to replace the Postgres reporting DB with MongoDB. What do you tell them?**
A: First, enumerate the queries — a reporting workload is aggregation + joins + ad-hoc filters, exactly where document stores are weakest and where MongoDB will force a `$lookup` on every row. The right answer is either a read-replica with materialised reporting views in Postgres, or an actual analytics store. I would only move to MongoDB if the reports are genuinely document-shaped (JSON blobs with nested filters) and the reporting SQL is a mess for reasons the document model would actually fix.

**Q7. How do you test a multi-store system without a shared dev cluster?**
A: Testcontainers for Postgres, Mongo, Cassandra, Redis; LocalStack for DynamoDB; ES with a test container. Contract tests per repository adapter, scenario tests across a single store, and only the cross-store coordination logic (the outbox/saga) tested against real containers. Shared dev clusters create flaky tests and reproduce only in production bugs.

### ⚫ Principal / Staff (8+ Years)

**Q8. Justify keeping Postgres for payments while moving everything else to NoSQL.**
A: Payments need serializable invariants across entities (account, ledger, idempotency key) with exact constraints and full auditability. Postgres gives me that with mature tooling, and I can shard/read-replica it. Re-implementing that correctness in DynamoDB means conditional writes plus retries plus reconciliation, which is a correctness project with no ceiling. The ledger is a *correctness* problem, not a *scale* problem, so the right store is the one where correctness is cheapest to prove.

**Q9. You inherit a system with six NoSQL stores and no documentation. How do you get it under control?**
A: Instrument first: query logs, keyspace metrics, and code-level call graphs — build the map of "which code path reads which store with what key". Then classify each store by *the access pattern it actually serves* (not the one it was named for). Where two stores serve the same pattern, that is duplication to remove; where an access pattern is served by the wrong store, that is a migration candidate. The goal is a documented, minimal polyglot — not a single-store monoculture, because too many stores with unclear ownership is the same operational risk as one fragile store.

---

## 14. Counter-Questions & Interviewer Traps

**Trap 1: "We'll just use MongoDB for everything."** — Correct until the first report needs a join or the first hot path needs sub-ms latency. The answer is polyglot with clear ownership.

**Trap 2: "Dual-write to Postgres and Cassandra in one service method."** — Without an outbox, the second write can fail and now you have divergence with no reconciliation path. Use the transactional outbox pattern.

**Trap 3: "NoSQL means we don't need migrations."** — You need *more* migration discipline: every document/row is a contract your code must read, and backfills are now manual, not automatic.

**Trap 4: "Eventual consistency is fine for everything."** — Name the read-your-writes cases. Every user-facing "I just did it" flow needs a consistency plan.

**Trap 5: "Cassandra gives us strong consistency, so no data can be lost."** — Only at `QUORUM`+ with `R+W>RF` *and* repair discipline. `ONE` plus a missed repair during an outage is a data-loss recipe.

**Trap 6: "Managed services like DynamoDB mean no modelling work."** — They remove operational work, not modelling. A DynamoDB migration is mostly modelling, not ops.

---

## 15. Rapid-Fire MCQs

**Q1. The first question in selecting a NoSQL store is:**
- [ ] A) Which is cheapest
- [ ] B) What are the access patterns
- [ ] C) Which is most popular
- [ ] D) Which supports SQL
* **Answer:** **B.**

**Q2. Cross-store atomicity is achieved by:**
- [ ] A) `@Transactional` across repositories
- [ ] B) An outbox + saga
- [ ] C) A distributed transaction manager
- [ ] D) It is impossible to do at all
* **Answer:** **B.**

**Q3. Correctness-critical multi-entity invariants belong in:**
- [ ] A) Cassandra
- [ ] B) DynamoDB
- [ ] C) PostgreSQL (or a CP distributed SQL store)
- [ ] D) Redis
* **Answer:** **C.**

**Q4. Which statement about NoSQL schemas is TRUE?**
- [ ] A) They do not need migrations
- [ ] B) Schemas live in the application and versioning is manual
- [ ] C) They enforce constraints automatically
- [ ] D) They are identical to relational schemas
* **Answer:** **B.**

**Q5. Elasticsearch in an architecture is best described as:**
- [ ] A) The system of record
- [ ] B) A derived, disposable read model
- [ ] C) A transactional store
- [ ] D) A queue
* **Answer:** **B.**

**Q6. A feature flag per read path during a NoSQL migration gives you:**
- [ ] A) Faster queries
- [ ] B) Rollback capability per endpoint
- [ ] C) Free consistency
- [ ] D) Better sharding
* **Answer:** **B.**

---

## 16. One-Page Cheat Sheet

```text
+----------------------------+--------------------------------------------------+
| CONCEPT                    | RULE                                             |
+----------------------------+--------------------------------------------------+
| Selection order            | Access pattern -> consistency -> scale -> ops   |
| Query list                 | Mine slow logs, ORM call sites, reports FIRST   |
| Money / inventory          | Postgres / CockroachDB / Spanner (CP + ACID)    |
| Nested flexible docs       | MongoDB                                          |
| Append-only time-series    | Cassandra + TWCS + TTL                          |
| Simple key lookup at scale | DynamoDB (conditional writes for invariants)    |
| Sub-ms rebuildable state   | Redis (never the only copy of money)           |
| Text search                | Elasticsearch (derived, disposable)            |
| Variable-depth traversal   | Neo4j / graph                                   |
| Abstraction                | Own repository interfaces; adapters per store  |
| Transaction                | One per store; cross-store = outbox + saga     |
| CQRS                       | Authoritative write model + streamed projections|
| Migration                  | Enumerate queries -> dual-write -> backfill -> |
|                            | shadow-read -> cutover -> rollback-ready      |
| Spring config hazards      | auto-index-creation, schema-action on boot,    |
|                            | missing local-datacenter, no jittered retry     |
+----------------------------+--------------------------------------------------+
```

---

## 17. Follow-up Learning
- **Previous Chapter:** ← [05 · Redis, Graph & Time-Series Stores](./05-Redis-Graph-and-TimeSeries-NoSQL-Interview-Deep-Dive.md)
- **SQL fundamentals & joins:** [../01-SQL-Databases/01-SQL-Fundamentals-Data-Types-Constraints-and-CRUD.md](../01-SQL-Databases/01-SQL-Fundamentals-Data-Types-Constraints-and-CRUD.md)
- **Spring Boot Data JPA & transactions:** [../../SPRING_BOOT/04-Spring-Data-JPA-Hibernate/](../../SPRING_BOOT/04-Spring-Data-JPA-Hibernate/)
- **Caching patterns in Spring:** [../../SPRING_BOOT/10-Caching/](../../SPRING_BOOT/10-Caching/)
- **Master Kit Index & Study Roadmap:** ← [README](./README.md)
