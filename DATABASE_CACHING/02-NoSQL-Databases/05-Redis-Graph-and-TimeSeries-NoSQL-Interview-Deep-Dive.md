# 05 · Redis, Graph & Time-Series Stores — NoSQL Interview Deep Dive

> **Level:** Mid → Senior (the "other families" chapter)  
> **Interview Frequency:** ★★★☆☆ (Redis is near-universal; graph/time-series decide specialist loops)  
> **Target:** Redis 7+, Neo4j 5, Elasticsearch 8, InfluxDB/TimescaleDB  
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. Redis — A Data-Structure Server, Not a Cache](#1-redis--a-data-structure-server-not-a-cache)
- [2. Redis Data Structures — The Interview Catalog](#2-redis-data-structures--the-interview-catalog)
- [3. Redis Architecture — Persistence, Replication, Sentinel & Cluster](#3-redis-architecture--persistence-replication-sentinel--cluster)
- [4. Redis Eviction, Memory & Performance Rules](#4-redis-eviction-memory--performance-rules)
- [5. Redis — What Cannot Be Done](#5-redis--what-cannot-be-done)
- [6. Graph Databases — Neo4j & the Property-Graph Model](#6-graph-databases--neo4j--the-property-graph-model)
- [7. Search Engines — Elasticsearch & the Inverted Index](#7-search-engines--elasticsearch--the-inverted-index)
- [8. Time-Series Stores — Cassandra, InfluxDB & TimescaleDB](#8-time-series-stores--cassandra-influxdb--timescaledb)
- [9. Level-Wise Interview Questions & Answers](#9-level-wise-interview-questions--answers)
- [10. Counter-Questions & Interviewer Traps](#10-counter-questions--interviewer-traps)
- [11. Rapid-Fire MCQs](#11-rapid-fire-mcqs)
- [12. One-Page Cheat Sheet](#12-one-page-cheat-sheet)
- [13. Real-World Production Post-Mortems](#13-real-world-production-post-mortems)
- [14. Follow-up Learning](#14-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

1. **Redis is not "a cache" — it is an in-memory data-structure server** (strings, hashes, lists, sets, sorted sets, streams, bitmaps, HyperLogLog, geo). Caching is only one of its uses, and calling it a cache triggers the "what happens on restart?" trap.
2. **Redis is single-threaded for command execution** — O(N) commands (`KEYS`, `SMEMBERS`, `HGETALL`, `SORT` without LIMIT) block the whole instance and are the most common cause of latency spikes.
3. **Redis Cluster shards 16384 hash slots across at least 3 masters** (plus replicas for fault tolerance); multi-key operations are only allowed when all keys hash to the same slot (hash tags `{...}`).
4. **Redis is eventually consistent to disk** — RDB snapshots (periodic, faster restart) and AOF (append-only, durable, `fsync` policies `always`/`everysec`/`no`). Losing up to 1s of writes with `appendfsync everysec` is the accepted default.
5. **Neo4j belongs where traversal depth varies** — "friends-of-friends-of-friends who bought X" is O(1) per hop in a graph and O(rows × depth) in SQL.
6. **Elasticsearch is an inverted index with relevance scoring**, not a search "database" — treat it as a derived query layer over a system of record, and re-index from that source of truth.
7. **Time-series stores solve the unbounded-partition problem** with time-bucketed storage, downsampling, retention policies and compression (Gorilla XOR / delta-of-delta for floats).

---

## 1. Redis — A Data-Structure Server, Not a Cache

```text
KEY (string) ──► VALUE (a typed structure)

"user:1001"          ──►  STRING      "Asha Verma"
"cart:1001"          ──►  HASH        { sku: "LT-PRO-14", qty: "1" }
"leaderboard:weekly" ──►  ZSET        { "alice": 9820, "bob": 9640 }
"queue:email"        ──►  LIST        [ "job-1", "job-2" ]      (LPUSH / BRPOP)
"session:s-77"       ──►  STRING      "{jwt...}"  EX 1800
"online:2026-10-24"  ──►  BITMAP      bit 1001 = 1
"visitors:page:9"    ──►  HYPERLOGLOG (approximate cardinality, 12KB max)
"geo:drivers"        ──►  GEOSET      lat/lon per member (GEOADD / GEOSEARCH)
"events:orders"      ──►  STREAM      id → {event...}  (consumer groups)
```

### 1.1 The three questions every Redis interviewer asks

1. **"Is Redis durable?"** — It can be: AOF with `appendfsync always` fsyncs every write (slow, durable); `everysec` (default, ≤1s loss); RDB snapshots (minutes of loss). Most caches run `appendfsync everysec` + RDB and accept the loss window.
2. **"Is Redis a database?"** — It can be a system of record for specific shapes (streams, leaderboards, session state) with persistence + replication, but it is memory-bound, single-node write-limited (Cluster helps), and its transactional model is not ACID across keys.
3. **"What happens when memory is full?"** — The `maxmemory-policy` decides: `noeviction` returns errors (correct for a system of record), `allkeys-lru`/`allkeys-lfu` evict (correct for a cache), `volatile-*` evicts only keys with a TTL.

---

## 2. Redis Data Structures — The Interview Catalog

| Structure | Commands | Complexity | Use |
|---|---|---|---|
| **String** | `SET`, `GET`, `INCR`, `SETEX` | O(1) | Caches, counters, locks (`SET NX EX`) |
| **Hash** | `HSET`, `HGETALL`, `HINCRBY` | O(1) field | Object caching (field-level updates) |
| **List** | `LPUSH`, `RPUSH`, `LRANGE`, `BRPOP` | O(1) ends, O(N) middle | Queues, recent-activity feeds |
| **Set** | `SADD`, `SMEMBERS`, `SINTER` | O(1) | Uniqueness, tags, tags-intersection |
| **Sorted Set (ZSet)** | `ZADD`, `ZRANGE ... BYSCORE`, `ZINCRBY` | O(log N) | Leaderboards, rate limiting, range-by-score |
| **Bitmap** | `SETBIT`, `BITCOUNT`, `BITOP` | O(1) | Presence/flags (Bloom-ish), retention cohorts |
| **HyperLogLog** | `PFADD`, `PFCOUNT` | O(1), ~0.81% error | Unique-visitor estimates in 12KB |
| **Stream** | `XADD`, `XREAD GROUP`, `XACK` | O(1) append | Event log + consumer groups |
| **Geo** | `GEOADD`, `GEOSEARCH` | O(N+log N) | "drivers within 5km" |

**The subtle answers that score points:**
- **`ZSET` vs `SET`:** a zset keeps a score so `ZRANGEBYSCORE` is O(log N + M) — the only structure with ordered range queries.
- **`HyperLogLog` is approximate**, safe for "~1.2M unique visitors today", never for billing.
- **Redis Lua scripting** runs atomically server-side: the standard implementation of "check-and-set" without a race (e.g. rate limiting, `INCR` with expiry in one script).
- **`SET key val NX EX 30` is the distributed-lock primitive** — but a naive lock without fencing is unsafe; the correct production answer mentions **Redlock** (or fencing tokens / a CP store like etcd for strict correctness).

---

## 3. Redis Architecture — Persistence, Replication, Sentinel & Cluster

### 3.1 Replication

```
   PRIMARY  ──►  REPLICA(s)      (asynchronous, propagation lag)
     └──► Sentinel watches replication + fails over + reconfigures clients
```

- Replication is **asynchronous**: a write acknowledged on the primary can be missing from the replica. Sentinel failover therefore **can lose acknowledged writes** — which is why Redis is not the ledger.
- A replica can serve reads (`READONLY`), and `WAIT n timeout` can be used to force the primary to wait for replicas — a rare, latency-expensive correctness lever.

### 3.2 Persistence

| Mode | Mechanism | Restart time | Data loss |
|---|---|---|---|
| **RDB** | Fork + point-in-time snapshot | Fast | Since the last snapshot |
| **AOF `always`** | fsync every write | Slowest | ~0 |
| **AOF `everysec`** (default) | fsync in a background thread | Fast | ≤ 1s |
| **AOF `no`** | OS decides | Fastest | Since last OS flush |
| **Hybrid (default)** | RDB + AOF | Fast | ≤ 1s |

`SAVE`/`BGSAVE` are operator commands; a fork on a large dataset can stall if `vm.overcommit_memory` is misconfigured — a classic production incident.

### 3.3 Redis Cluster

```text
16384 hash slots  ──►  distributed over M masters (min 3 for quorum)
   each master replicated (min 1 replica)
   → minimum production topology: 3 masters + 3 replicas = 6 nodes
   → high availability: any master can fail without data loss (replica promoted)

Key routing:  CRC16(key) mod 16384
Multi-key    only allowed within the same slot:
   {user:1001}:cart  and  {user:1001}:orders   → same slot thanks to the hash tag
Cross-slot multi-key ops → CROSSSLOT error (except a few commands)
```

- **Cluster bus (gossip)** handles failover detection; a node is `fail` when a majority of masters agree.
- **Resharding moves slots** between nodes with zero downtime; keys are migrated lazily (`MIGRATE`) or via `redis-cli --cluster reshard`.

---

## 4. Redis Eviction, Memory & Performance Rules

### 4.1 Eviction policies

| Policy | Behaviour |
|---|---|
| `noeviction` | Writes fail on OOM — **system-of-record mode** |
| `allkeys-lru` | Evict any key, LRU — **cache mode** |
| `volatile-lru` / `volatile-lfu` | Evict only keys with a TTL |
| `allkeys-random` | Random eviction (rarely right) |
| `volatile-ttl` | Evict the key with the nearest expiry |

**LRU vs LFU:** LRU evicts the least-*recently used*, so a cold key touched once survives a long-scan pattern; **LFU evicts the least-*frequently used***, which better models "hot" data. Redis 4.0+ offers both.

### 4.2 Performance rules

| Rule | Why |
|---|---|
| **Never `KEYS *` in production** | O(N) over the whole keyspace, blocking; use `SCAN` (cursor-based) |
| Avoid `SMEMBERS`/`HGETALL` on huge sets | O(N) response; paginate with `SSCAN`/`HSCAN` |
| Pipeline / `MGET` | Removes round trips; a Redis RTT is ~50µs, but 100 sequential calls is 5ms |
| Use `SCAN` with `COUNT` and an infinite loop guard | Cursor can return 0 items but is not finished |
| Lua or a transaction for read-modify-write | Prevents interleaving (e.g. `INCR` + `EXPIRE`) |
| Set `maxmemory` and the correct policy before you need them | Eviction is not a safety net |

---

## 5. Redis — What Cannot Be Done

| Limitation | Consequence |
|---|---|
| Memory-bound | Dataset must fit RAM (or use Redis on Flash for warm tiers) |
| Single-threaded command execution | One slow command delays everything; no parallel CPU for one instance |
| Async replication | Failover can lose acknowledged writes; no strong read-your-writes across failover |
| No ad-hoc queries | Key design is the query plan (like DynamoDB) |
| Only cross-slot-safe ops | Multi-key transactions need hash tags |
| Not ACID across keys | `MULTI/EXEC` is atomic, not isolated/consistent in the transactional sense |

> **The interview sentence:** *"Redis is where I keep state that must be sub-millisecond and can be rebuilt: sessions, rate-limit counters, leaderboards, hot reference data. It is never where I keep the only copy of money or a contract."*

---

## 6. Graph Databases — Neo4j & the Property-Graph Model

```text
Node         (Person {name: 'Asha', joined: 2015})
Relationship (Asha) -[:FOLLOWS {since: 2019}]-> (Rahul)
Degree       = number of relationships on a node (affects traversal cost)
Index        = always index the label + the property you filter by
```

**Cypher: the query language**

```cypher
// "Friends-of-friends who bought the product I bought, ranked by overlap"
MATCH (me:Person {id: $userId})-[:BOUGHT]->(p:Product)<-[:BOUGHT]-(other:Person)-[:FRIEND*1..2]-(me)
RETURN other.name, COUNT(p) AS overlap
ORDER BY overlap DESC LIMIT 20;
```

| Property | Detail |
|---|---|
| Traversal cost | O(1) per hop via the adjacency lists stored on disk per node |
| vs SQL | A 3-hop join in SQL re-scans per level; a graph follows pointers |
| Indexes | Schema + composite + full-text (Neo4j 5) |
| Transactions | Full ACID (Neo4j is CP) |
| Scaling | Write-scaling is the hard part; use read replicas + graph-parted datasets |
| Sizing | Micro-to-mid graphs fit one node; billions of edges need Neo4j Fabric / a sharded design |

**Choose a graph when:** relationships are first-class and the queries are variable-depth traversals with relationship filters (fraud rings, recommendations, access-control reachability, dependency graphs, org-charts with complex hierarchy rules).

**Don't choose a graph when:** the "graph" is a fixed 2-level hierarchy (a relational model with a self-join wins), or you only need one-hop lookups (a key-value store wins).

---

## 7. Search Engines — Elasticsearch & the Inverted Index

```text
document → analysis (tokenise, lowercase, stem) → inverted index
   "Red Running Shoes" → [red, running, shoe]
   term "shoe" → docIds [1, 7, 42]

Query = boolean of terms + relevance scoring (BM25) + filters (exact, range, geo)
```

| Concept | Detail |
|---|---|
| **Inverted index** | term → posting list of documents; the core of fast text search |
| **Analysis** | Standard/whitespace/keyword/ngram analyzers; debugging with `_analyze` |
| **Relevance** | BM25 (TF-IDF successor); `_score` vs filter context (no scoring, cacheable) |
| **Near-real-time** | ~1s refresh; `refresh_interval` is the read-freshness knob |
| **Shards & replicas** | Shard = unit of distribution and rebalancing; size shards 20–50GB |
| **Mapping** | Explicit mappings (schema on write) beat dynamic mapping (accidental explosion) |
| **Aggregations** | Bucket/metric agg — analytics in the query engine |
| **Not a system of record** | Rebuild from the source; a lost ES cluster must be re-indexed |

**The senior answer:** *"Elasticsearch is a derived read model behind my data. The authoritative store is Postgres/Mongo, and a stream or dual-write keeps the index current. I never accept a write only into Elasticsearch — the index is disposable."*

---

## 8. Time-Series Stores — Cassandra, InfluxDB & TimescaleDB

The defining property: **data is append-mostly, queried by time range, and has a retention policy.**

| Engine | Model | Bucketing | Compression | Query |
|---|---|---|---|---|
| **Cassandra + TWCS** | Wide-column, time-bucketed partitions | Composite `(deviceId, bucketDate)` | Zstd on SSTables | CQL, partition-first |
| **InfluxDB 3/TSM** | Measurement + tags + fields + time | Blocks/shards by time | Gorilla XOR + delta-of-delta for floats, RLE for ints | Flux/InfluxQL, tags are indexed |
| **TimescaleDB** | PostgreSQL hypertable (auto-partitioned chunks) | Chunk by `time` interval | Native PG columnar compression | **Full SQL** + time-series functions |
| **Prometheus** | Pull-based scrape + TSDB blocks | 2h blocks | Gorilla | PromQL |

**Core techniques across all of them:**
- **Downsampling / rollups** — store raw data for 7 days, 1-min rollups for 90 days, 1-hour for a year. This is the multi-tier retention that makes time-series cheap.
- **Retention enforcement by dropping whole chunks/buckets** (never per-row deletes).
- **Write batching** — append-only writes at 10k–100k points/sec per node.
- **Time-bucketed partitions** — the same anti-unbounded-partition lesson from Cassandra.

```sql
-- TimescaleDB: the SQL-native answer
CREATE TABLE readings (device_id UUID, ts TIMESTAMPTZ, value DOUBLE,
                       PRIMARY KEY (device_id, ts));
SELECT create_hypertable('readings', by_range('ts', INTERVAL '1 day'));

-- Continuous aggregate = a maintained rollup, refreshed incrementally
CREATE MATERIALIZED VIEW readings_hourly
WITH (timescaledb.continuous) AS
SELECT device_id, time_bucket('1 hour', ts) AS bucket,
       AVG(value), MAX(value), COUNT(*)
FROM readings GROUP BY device_id, bucket;

SELECT add_retention_policy('readings', INTERVAL '30 days');
```

---

## 9. Level-Wise Interview Questions & Answers

### 🟢 Junior (0–2 Years)

**Q1. What is Redis used for?**
A: Caching, session storage, rate limiting, leaderboards/ranking, counters, pub/sub, queues, and as a fast reference store — any state that must be sub-millisecond and can be rebuilt.

**Q2. What is an inverted index?**
A: A mapping from a term to the documents containing it. It turns "find all documents with 'shoe'" into a lookup rather than a scan, and it is the core of full-text search engines.

### 🟡 Mid-Level (2–5 Years)

**Q3. Why is Redis "single-threaded" and is that a problem?**
A: Commands are executed on one thread, which serialises all operations and keeps the model simple and atomic. It is a problem only for expensive commands — `KEYS`, `SMEMBERS`, `HGETALL`, or an un-bounded `SORT` — which block every other client. That is why production rules forbid those commands and prefer `SCAN`/pagination.

**Q4. What is the difference between RDB and AOF?**
A: RDB is a point-in-time snapshot (fast restart, loses data since the last snapshot). AOF logs every write (`everysec` by default, ≤1s loss; `always` is durable but slow). The default hybrid mode uses both: AOF for minimal loss, RDB for fast restarts.

**Q5. How many nodes does a fault-tolerant Redis Cluster need?**
A: 6 — 3 masters (so the cluster has a quorum for failover decisions) and 3 replicas (so each master has a failover target). 3 masters alone survive no failures.

**Q6. What is a hash tag and why does it exist?**
A: A substring of a key wrapped in `{}` — only that substring is hashed, so `{user:1}:cart` and `{user:1}:orders` land in the same slot, making multi-key operations legal in a cluster.

**Q7. When would you use a graph database over a relational one?**
A: When the queries are variable-depth traversals over relationships ("friends of friends who bought X", "which services depend on this service transitively", "who has access to this resource through delegation chains"). For a fixed 2-level hierarchy, a relational self-join is simpler.

### 🔴 Senior (5–8 Years)

**Q8. Your Redis primary failed and Sentinel promoted a replica; a confirmed order vanished. Explain.**
A: Redis replication is asynchronous, so writes acknowledged by the primary may not have reached the promoted replica. Sentinel failover is split-brain-aware (majority of Sentinels), but it does not guarantee no-loss. The fix is architectural: money/order state lives in a durable store, and Redis holds only derived state — or you accept `WAIT`/strong consistency costs and keep the durable store authoritative.

**Q9. Design a sliding-window rate limiter in Redis.**
A: A **ZSET sliding window**: `ZREMRANGEBYSCORE key 0 now-window`, `ZCARD key`, then `ZADD key now requestId` in a Lua script so the read-check-write is atomic and a single round trip; `EXPIRE key window` for cleanup. For high-volume counters, the **token-bucket as a hash + Lua** or the **GCRA algorithm** stored as a single string is cheaper.

**Q10. When is Elasticsearch the wrong tool for search?**
A: When exact-match/relational filtering dominates (a B-tree index in Postgres beats inverted-index scoring), when you need transactional consistency between write and read (refresh is near-real-time, not immediate), or when the data is small (an in-memory scan or Postgres full-text search is simpler and cheaper). Also when "search" means "report" — that is an analytics engine.

**Q11. Cassandra vs InfluxDB vs TimescaleDB for sensor data?**
A: Cassandra when write scale is enormous, the access pattern is strict (`device_id` partition), and I want to own retention/compaction. InfluxDB when I need a batteries-included time-series engine with downsampling and retention in one box. TimescaleDB when the team knows SQL, needs joins across the time-series data, and single-node/partitioned Postgres covers the volume — because the biggest cost in time-series is the operational one, and full SQL removes a whole class of modelling work.

### ⚫ Principal / Staff (8+ Years)

**Q12. Build a real-time leaderboard for 50M players with rank queries.**
A: A Redis ZSET for the top layer (top 10k / around-me queries at O(log N + M)) with a bounded write rate, backed by a periodic snapshot from the authoritative scoring store into the ZSET and reconciled by a stream. Global rank for 50M keys does not fit one ZSET's memory comfortably with scores per player at high cardinality, so the design splits: ZSET for the competitive head, and a persisted, partitioned rank table for full-range lookups, refreshed by a batch job. Point queries by player go to the durable store.

---

## 10. Counter-Questions & Interviewer Traps

**Trap 1: "Redis is a cache, so it can be restarted freely."** — It *can*, but then you lose what was not rebuilt: counters, locks, sessions, rate-limiters. The correct answer names the persistence mode and the rebuild path.

**Trap 2: "Use `KEYS pattern` to find the user's keys."** — `KEYS` blocks the whole server on O(N). Use `SCAN` (cursor-based, never guaranteed complete in one pass) and design keys with a prefix so the scan is bounded.

**Trap 3: "Multi-key operations always work in Redis Cluster."** — Only within one slot. Use hash tags or restructure.

**Trap 4: "Elasticsearch is the database."** — It is a derived search layer; the source of truth is elsewhere and the index is disposable/re-indexable.

**Trap 5: "Postgres can't do time-series."** — TimescaleDB or a time-partitioned Postgres table with a rollup table handles most workloads; dedicated engines win at extreme ingest or exotic functions.

**Trap 6: "A graph DB replaces Postgres."** — Graphs excel at traversal; they are weaker at set-based reporting and joins, which remain Postgres's strength. Polyglot is the answer.

---

## 11. Rapid-Fire MCQs

**Q1. Redis Cluster hash slots:**
- [ ] A) 256
- [ ] B) 1024
- [ ] C) 16384
- [ ] D) 65536
* **Answer:** **C.**

**Q2. The default AOF fsync policy is:**
- [ ] A) `always`
- [ ] B) `everysec`
- [ ] C) `no`
- [ ] D) `on-write`
* **Answer:** **B.**

**Q3. Which command is unsafe on a large Redis keyspace?**
- [ ] A) `GET`
- [ ] B) `SCAN`
- [ ] C) `KEYS *`
- [ ] D) `HGET`
* **Answer:** **C.**

**Q4. Minimum production Redis Cluster for fault tolerance:**
- [ ] A) 1 node
- [ ] B) 3 nodes
- [ ] C) 6 nodes (3 masters + 3 replicas)
- [ ] D) 12 nodes
* **Answer:** **C.**

**Q5. A hash tag `{...}` in a key controls:**
- [ ] A) TTL
- [ ] B) Which hash slot a key maps to
- [ ] C) Eviction priority
- [ ] D) Persistence
* **Answer:** **B.**

**Q6. Elasticsearch relevance scoring uses:**
- [ ] A) B+ tree ordering
- [ ] B) BM25
- [ ] C) Quorum voting
- [ ] D) LRU ranking
* **Answer:** **B.**

**Q7. HyperLogLog provides:**
- [ ] A) Exact cardinality
- [ ] B) Approximate cardinality in ~12KB with ~0.81% error
- [ ] C) Sorted membership
- [ ] D) Time-decaying counts
* **Answer:** **B.**

**Q8. TimescaleDB is fundamentally:**
- [ ] A) A new engine
- [ ] B) PostgreSQL with automatic time partitioning
- [ ] C) A columnar clone of Influx
- [ ] D) A Redis module
* **Answer:** **B.**

---

## 12. One-Page Cheat Sheet

```text
+---------------------------+----------------------------------------------------+
| CONCEPT                   | RULE                                               |
+---------------------------+----------------------------------------------------+
| Redis is                  | in-memory data-structure server (not "a cache")    |
| Structures                | String, Hash, List, Set, ZSet, Bitmap, HLL,        |
|                           | Stream, Geo                                       |
| Single-threaded exec      | O(N) commands block everything (KEYS/SMEMBERS)     |
| SCAN vs KEYS              | SCAN is cursor-based & non-blocking; use it        |
| Distributed lock          | SET key val NX EX  (+ Redlock/fencing for strict)  |
| Lua script                | atomic read-modify-write in one round trip         |
| Persistence               | RDB (snapshot) | AOF everysec (<=1s loss) | hybrid |
| Replication               | ASYNC -> failover can lose acknowledged writes     |
| Sentinel                  | monitoring + failover + client reconfiguration    |
| Cluster                   | 16384 slots; min 3 masters; hash tags for multi-key|
| Eviction                  | noeviction (SoR) | allkeys-lru/lfu (cache)          |
| LRU vs LFU                | recently-used vs frequently-used                   |
| Pipeline                  | batch round trips; MGET over N GETs                |
| Neo4j                     | nodes+typed edges, O(1)/hop, full ACID             |
| Graph when                | variable-depth traversal over relationships       |
| Elasticsearch             | inverted index + BM25; a derived read model        |
| Time-series               | time-bucketed partitions + rollups + retention    |
| TimescaleDB               | SQL, hypertables, continuous aggregates           |
+---------------------------+----------------------------------------------------+
```

---

## 13. Real-World Production Post-Mortems

### Incident 1: `KEYS *` in a health check
A "quick" diagnostics endpoint ran `KEYS session:*` every 30 seconds on a 12M-key instance. Because Redis executes commands single-threaded, the entire instance froze for 4–9 seconds per call, appearing as a Redis outage. **Fix:** replaced with `SCAN ... COUNT 100` in a loop with a cursor guard, then removed the endpoint; added a slowlog monitor (`SLOWLOG GET`) alert.

### Incident 2: LFU vs LRU misconfiguration
A reference-data cache with `allkeys-lru` evicted hot product rows after a nightly full-scan report touched every key once. Switched to `allkeys-lfu`, which evicted on frequency, and the hit rate went from 71% to 98%.

### Incident 3: Grafana + Elasticsearch mapping explosion
Dynamic mapping created a new field for every log attribute variant, exploding the index to 40k mappings and degrading query latency. **Fix:** explicit index templates, `dynamic: strict`, and an ILM policy that forces a re-index on mapping conflicts — plus a hard rule: the index is derived, the source of truth is the log pipeline.

---

## 14. Follow-up Learning
- **Next Chapter:** → [06 · NoSQL Selection, Polyglot Persistence & Spring Data Integration](./06-NoSQL-Selection-Polyglot-Persistence-and-Spring-Data-Integration.md)
- **Previous Chapter:** ← [04 · DynamoDB — Key-Value Design, Partition Math & Single-Table Modelling](./04-DynamoDB-Key-Value-Design-Partition-Math-and-Single-Table-Modelling.md)
- **Caching patterns with Redis in Spring:** [../../SPRING_BOOT/10-Caching/02-Redis-Data-Structures-and-Spring-Cache.md](../../SPRING_BOOT/10-Caching/02-Redis-Data-Structures-and-Spring-Cache.md)
- **Master Kit Index & Study Roadmap:** ← [README](./README.md)
