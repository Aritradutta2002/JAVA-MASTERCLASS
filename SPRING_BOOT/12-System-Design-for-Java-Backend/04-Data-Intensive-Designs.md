# 04 · Data-Intensive Designs

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★☆ ("Design a search / analytics / nearby-places system")
> **Prerequisite:** [03 · Classic Design Problems](./03-Classic-Design-Problems.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. The Pattern Behind All of These](#0-the-pattern-behind-all-of-these)
- [1. Search](#1-search)
- [2. Analytics & Metrics Pipeline](#2-analytics--metrics-pipeline)
- [3. Leaderboards & Counters](#3-leaderboards--counters)
- [4. Geo — "Restaurants Near Me"](#4-geo--restaurants-near-me)
- [5. Notifications](#5-notifications)
- [6. Choosing the Right Store](#6-choosing-the-right-store)
- [7. Interview Questions & Model Answers](#7-interview-questions--model-answers)
- [8. Revision Card](#8-revision-card)

---

## 0. The Pattern Behind All of These

Every "data-intensive" problem is the same shape:

```
   WRITE PATH (durable, normalised)          READ PATH (specialised, denormalised)

   service ──► Postgres (truth)              client ──► specialised store
       │           │
       │           └─► outbox / CDC ──► indexer ──► search index
       │                                            analytics store
       │                                            geo index
       │                                            leaderboard cache
       └────────────────────────────────────────────────────────┘
```

**Three invariants you should state for any of them:**

1. **The relational DB stays the source of truth.** The specialised store is **derived** and
   rebuildable.
2. **The pipeline is asynchronous** — so the derived store is **eventually consistent**, with an
   explicit staleness bound.
3. **The derived store must be rebuildable from scratch** — otherwise it's a second source of
   truth and you've lost the ability to fix a mapping bug.

Saying those three sentences frames the whole problem and is worth a lot.

---

## 1. Search

### 1.1 Requirements

- Full-text search over 50M products, with typo tolerance, ranking, facets, and filters
- Query p99 < 100 ms
- New/updated products visible within ~30 s
- 5k searches/s peak
- Search must not be able to take down the store

### 1.2 Why not `LIKE '%term%'`

| Approach | Problem |
|---|---|
| `LIKE '%term%'` | full scan; no ranking; no typo tolerance |
| Postgres full-text (`tsvector` + GIN) | works well up to millions of docs; ranking is basic; still on your primary |
| Trigram GIN | good for substring, no ranking/typo semantics |
| **Dedicated search** (Elasticsearch/OpenSearch) | inverted index, relevance scoring, facets, fuzzy, horizontal scale |

**The decision rule:** Postgres FTS is genuinely fine up to ~a few million rows and it keeps the
system simple. Reach for Elasticsearch when you need relevance tuning, faceting at scale, fuzzy
matching, or isolation from the primary. **Saying "I'd start with Postgres FTS and migrate when
relevance tuning becomes the requirement" is a better answer than jumping straight to
Elasticsearch.**

### 1.3 Architecture

```
        write path                                 read path
  ┌──────────────┐                            ┌──────────────┐
  │ product-svc  │                            │ search-api   │
  │  Postgres    │                            └──────┬───────┘
  │  (truth)     │                                   │
  └──────┬───────┘                                   ▼
         │ outbox                          ┌──────────────────┐
         ▼                                 │ Elasticsearch    │
  ┌──────────────┐    ┌──────────────┐     │ index: products  │
  │    Kafka     │───►│ indexer      │────►│ shards: 6        │
  │ products.v1  │    │ (idempotent) │     │ replicas: 1      │
  └──────────────┘    └──────────────┘     └──────────────────┘
```

**Idempotency is mandatory:** the indexer must upsert by document id (`PUT /index/_doc/{id}`),
not append. A replayed event must produce the same document.

### 1.4 The index design

```json
PUT /products
{
  "settings": { "number_of_shards": 6, "number_of_replicas": 1,
                "analysis": { "analyzer": { "custom": { "type": "custom",
                  "tokenizer": "standard",
                  "filter": ["lowercase", "asciifolding", "synonym_filter", "stemmer"] }}}},
  "mappings": {
    "properties": {
      "name":        { "type": "text", "analyzer": "custom",
                       "fields": { "keyword": { "type": "keyword" } } },
      "description": { "type": "text", "analyzer": "custom" },
      "brand":       { "type": "keyword" },
      "category":    { "type": "keyword" },
      "price":       { "type": "scaled_float", "scaling_factor": 100 },
      "stock":       { "type": "integer" },
      "updated_at":  { "type": "date" }
    }
  }
}
```

Design notes:
- **`text` for matching, `keyword` for filtering/facets/aggregations** — using one for both is
  the most common ES mistake.
- **Analyzer choices control relevance.** Synonyms and stemming are where quality comes from,
  not from the engine.
- **Shard on the read capacity you need**, not the write volume. Over-sharding is the classic
  ES performance problem.

### 1.5 Querying

```json
GET /products/_search
{
  "query": {
    "bool": {
      "must":   { "multi_match": { "query": "wireless headphones",
                                   "fields": ["name^3", "description"] } },
      "filter": [ { "term": { "category": "audio" } },
                  { "range": { "price": { "gte": 20, "lte": 200 } } },
                  { "range": { "stock": { "gt": 0 } } } ]
    }
  },
  "sort": [ "_score", { "updated_at": "desc" } ],
  "aggs": { "by_brand": { "terms": { "field": "brand", "size": 20 } } }
}
```

- **`must` affects score, `filter` doesn't** — put cheap numeric filters in `filter` so they're
  cached and don't distort relevance. That's a real performance win and a favourite follow-up.
- **Ranking:** `_score` from BM25, then a business boost (popularity, margin), then recency.

### 1.6 The hard parts

**Consistency.** The index lags the DB by the pipeline latency (target < 30 s). Consequences:
a newly created product may not be findable immediately — so the "product created" page should
not link to search. Bound it with a lag metric and an alert.

**Reindexing.** Mapping changes require a **new index + alias swap**:

```
1. create products_v2 with the new mapping
2. backfill from the DB (paginated, throttled)
3. dual-write new events to both indexes
4. compare counts and sample documents
5. atomically swap the alias products → products_v2
6. keep v1 for a day, then delete
```

**Never reindex in place on a live index** — a mapping change is not applied to existing
documents.

**Deleted documents.** A hard delete in the DB must produce a delete in the index, or search
shows ghosts. Soft deletes need a filter. This is where most search integrations silently rot.

**Search must not take down the store.** The indexer reads from Kafka, not from Postgres, so a
full reindex never touches the primary.

### 1.7 Failure

| Failure | Behaviour |
|---|---|
| ES cluster down | search returns an error or a degraded DB-backed result; **browsing still works** |
| Indexer lag | results are stale; alert on lag |
| Poison document | DLT; the rest of the index keeps flowing |
| Reindex mid-flight | alias points at the old index until the swap |

---

## 2. Analytics & Metrics Pipeline

### 2.1 Requirements

- Ingest 500k events/s (page views, clicks, purchases)
- Queries: counts by dimension, funnels, retention — over hours, days, months
- Dashboards must be fast (< 2 s), and **must never touch the OLTP database**
- Raw data retained 2 years for replay; aggregates retained indefinitely

### 2.2 Why not query the OLTP DB

```
OLTP (Postgres)                          OLAP (warehouse / columnar)
row-oriented, indexed for point reads    column-oriented, scans are cheap
optimised for: give me order 42          optimised for: sum revenue per country per day
✅ 1ms point reads                       ✅ 10M-row aggregate in 200 ms
❌ a full-table aggregate kills it       ❌ a point read needs a full column scan
```

**The insight:** they're different access patterns, so they're different storage layouts. Putting
analytics on the OLTP primary is how "the report killed production" happens.

### 2.3 Architecture

```
 client ──► collector (stateless, buffers)  ──► Kafka (events, 24h retention)
                                                    │
                        ┌───────────────────────────┼──────────────────────────┐
                        ▼                           ▼                          ▼
              ┌──────────────────┐        ┌─────────────────┐        ┌────────────────┐
              │ stream aggregator│        │ object storage  │        │ real-time OLAP │
              │ (1-min rollups)  │        │ (S3, raw/      │        │ (ClickHouse/   │
              └────────┬─────────┘        │  parquet)       │        │  Druid/Pinot)  │
                       │                  └────────┬────────┘        └───────┬────────┘
                       ▼                           │                         │
              ┌──────────────────┐                 ▼                         ▼
              │ warehouse tables │◄──── batch load (hourly) ──────► dashboards
              │ (daily rollups)  │
              └──────────────────┘
```

**The three-tier answer:**
- **Streaming aggregates** for "last 24 hours" (fast, approximate).
- **Object storage** as the immutable raw log (replay, backfill, audit).
- **Warehouse** for historical aggregates (accurate, slower to update).

### 2.4 The rollup table

```sql
CREATE TABLE events_daily (
  day          date           NOT NULL,
  country      text           NOT NULL,
  event_type   text           NOT NULL,
  count        bigint         NOT NULL,
  unique_users bigint         NOT NULL,
  PRIMARY KEY (day, country, event_type)
);
```

**Pre-aggregation is the whole trick.** A dashboard query over `events_daily` for a year touches
365 × dimensions rows instead of billions. Keep raw events for drill-down, aggregates for
dashboards.

**The trap:** unique counts can't be summed (`unique users in Jan + Feb ≠ unique in Jan+Feb`).
Use **HyperLogLog** sketches per bucket and merge them, or a dedicated approximate-distinct
function. Mentioning this is a strong signal.

### 2.5 Exactly-once is not required

Analytics tolerates duplicates and drops. So:

- **At-least-once ingestion** with dedupe by `event_id` where cheap.
- **Idempotent rollups** — recompute a window rather than increment a counter, so a replay is
  safe:

```sql
-- recompute instead of increment
INSERT INTO events_daily (day, country, event_type, count, unique_users)
SELECT day, country, event_type, count(*), hll_count(DISTINCT user_id)
FROM events
WHERE day = :day
GROUP BY day, country, event_type
ON CONFLICT (day, country, event_type) DO UPDATE
SET count = EXCLUDED.count, unique_users = EXCLUDED.unique_users;
```

That's the difference between a pipeline you can repair and one you can't.

### 2.6 Sampling

At 500k events/s, you may not need every event for every question. Sample 10% for exploratory
analysis, keep 100% for billing events. **State the sampling rate explicitly** so nobody
misreads a dashboard.

---

## 3. Leaderboards & Counters

### 3.1 Requirements

- Top 100 players by score, updated in real time
- A user's own rank
- 10M players, 5k score updates/s, 50k rank reads/s
- Updates visible within ~1 s
- Historical leaderboards (daily/weekly/all-time)

### 3.2 The structure

**Redis sorted sets are purpose-built for this:**

```bash
ZADD leaderboard:alltime 1500 player:42
ZREVRANGE leaderboard:alltime 0 99 WITHSCORES     # top 100
ZREVRANK leaderboard:alltime player:42            # my rank (0-based)
ZINCRBY leaderboard:alltime 50 player:42
```

All O(log N): 10M members = ~24 comparisons. Memory ≈ 10M × (16 B member + 8 B score + ~50 B
overhead) ≈ **~700 MB–1 GB**.

### 3.3 The scale problems

**Memory** — 1 GB per board × (all-time, daily × 30, weekly × 12) is a lot. Fixes:
- Keep only the **top N** in Redis; full history in the DB.
- Use one board per period with a TTL for automatic expiry:
  `ZADD lb:2026-03-11 ...` + `EXPIRE lb:2026-03-11 172800`.
- Shard by region/league and merge top-N at read time.

**Hot board** — the all-time board is read by everyone, so it's one hot shard. Fixes: replicate
the top-100 into a plain key (small, cheap), or cache the top-100 response at the edge for 1 s.
**Top-100 doesn't need to be live to the millisecond.**

**The user's own rank at scale** — `ZREVRANK` is fine, but if a user is rank 8,000,000 they
don't need the exact number; return a bucket ("top 10%"). Compute exact ranks only for the top
100k.

**Write amplification** — 5k updates/s on one key means one Redis shard handles all of them.
Shard the board by league/region and merge.

### 3.4 Exact vs approximate

| Need | Approach |
|---|---|
| Top 100 | exact, Redis sorted set |
| My exact rank | `ZREVRANK` (fine up to a few million) |
| My approximate rank | score buckets, or count players with a higher score via a second sorted set of scores only |
| Historical | per-period sorted sets with TTL, or DB aggregates |

### 3.5 Counters at scale

For "views" and "likes", don't `UPDATE ... SET count = count + 1` on the primary per event.

- **Redis `INCR`** on a shard of counters (`counter:post:42:{0..15}`), summed on read.
- **Batch flush** to the DB every N seconds or N increments.
- **Approximate display** — "1.2k views" doesn't need to be exact.

State the correctness trade: counts become eventually consistent and can be lost on a Redis
crash, which is fine for views and not fine for money.

---

## 4. Geo — "Restaurants Near Me"

### 4.1 Requirements

- 10M places; find places within 5 km of a lat/lng, sorted by distance
- Query p99 < 100 ms, 20k queries/s
- Place data changes rarely; location of the *user* changes constantly (but isn't stored)

### 4.2 Why a B-tree index doesn't work

`WHERE lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?` uses an index on `(lat, lng)` for the lat
range only, then filters longitude — a poor filter because a degree of latitude is ~111 km but a
degree of longitude shrinks toward the poles.

### 4.3 The approaches

| Approach | Idea | Use |
|---|---|---|
| **Geohash** | encode lat/lng into a string; nearby places share a prefix | prefix search; simple; boundary issues |
| **QuadTree / S2 / H3** | hierarchical cells | production geospatial, correct at boundaries |
| **PostGIS `GEOGRAPHY`** | R-tree (GiST) with real distance | simplest correct answer on Postgres |
| **Redis `GEOADD`/`GEOSEARCH`** | geohash inside a sorted set | fast, small datasets, cache-like |
| **Dedicated (Elasticsearch geo, Tile38)** | purpose-built | large scale, complex queries |

**The pragmatic answer:** PostGIS with a GiST index up to ~10M rows works well and keeps one
system. Beyond that, or if the query load is huge, move to a specialised service.

```sql
CREATE INDEX places_geo_idx ON places USING gist (location);

SELECT id, name,
       ST_Distance(location, ST_MakePoint(:lng, :lat)::geography) AS meters
FROM places
WHERE ST_DWithin(location, ST_MakePoint(:lng, :lat)::geography, 5000)
ORDER BY meters
LIMIT 50;
```

`ST_DWithin` uses the index (bounding box), then `ST_Distance` refines. Sorting by distance is
the expensive part — if you don't need exact ordering, sort by the bounding-box distance first.

### 4.4 Geohash mechanics (know this for the interview)

```
geohash("48.8584, 2.2945") = "u09tvw..."   (precision 5 ≈ 4.9 km × 4.9 km cell)

prefix length → cell size
   1 → 5000 km
   4 →   39 km
   5 →  4.9 km
   6 →  1.2 km
   7 →  150 m
```

**The boundary problem:** a query near a cell edge must search **9 cells** (the cell plus its 8
neighbours), or you'll miss places just across the boundary. That's the follow-up that separates
people who've read about geohash from people who've used it.

```java
// 9-cell neighbour query with Redis
List<String> cells = neighbours(geohash(lat, lng, 6));   // 9 prefixes
for (String c : cells) {
    results.addAll(redis.opsForGeo().radius("places:" + c, ...));
}
```

### 4.5 Scale

- **Shard by region/geohash prefix** — queries are naturally regional, so locality is excellent.
- **Cache per cell** — "top 50 restaurants in cell u09tv" is highly cacheable with a 5-minute TTL.
- **Hot cells** — a city centre is a hot shard; replicate it or cache the response.
- **Read-only replica** for geo search — it's pure read.

### 4.6 Related: ride-hailing / delivery matching

A different problem: find **moving** entities. That needs:
- **In-memory geo index** per cell (Redis GEO or a quadtree in memory).
- **Location updates** every few seconds — 1M drivers × 1 update/5 s = 200k writes/s, so batch
  and use a time-series-friendly store, not a relational table.
- **Matching** = nearest available driver, with availability state, which is a separate
  consistency problem (avoid double-assignment with an atomic claim).

---

## 5. Notifications

### 5.1 Requirements

- Email, SMS, push, in-app
- 50M notifications/day, with fan-out spikes (a marketing blast to 10M users)
- Must not duplicate ("you already got that email")
- Must not block the business flow (order confirmation can't fail because email is slow)
- Provider failover (APNs/FCM/SES can be down or rate-limited)

### 5.2 Architecture

```
  business service ──outbox──► Kafka `notifications` ──► notification-service
                                                              │
                                    ┌─────────────────────────┼──────────────────┐
                                    ▼                         ▼                  ▼
                            preferences/dedupe        channel routers      rate limits
                                    │                         │                  │
                                    └────────────► ┌──────────┼──────────┐       │
                                                   ▼          ▼          ▼       │
                                                 email       sms        push    │
                                                   │          │          │       │
                                                   ▼          ▼          ▼       │
                                                  SES      Twilio    FCM/APNs    │
                                                    │          │          │       │
                                                    └──────────┴──────────┴───────┘
                                                         delivery status → Kafka
                                                                │
                                                       retries / DLQ / analytics
```

### 5.3 The five requirements that matter

**1. Deduplication.** A notification id is derived from `(user, event, channel, day)`. Insert it
with a unique constraint before sending; a duplicate insert means "already sent". This is the
only way to survive Kafka redelivery.

**2. Preferences and quiet hours.** A user opting out must be honored *before* sending, and the
check must be fast — cache preferences per user in Redis.

**3. Rate limiting per user and per channel.** A bug that fans out 100 notifications to one user
is a support-ticket generator. Cap (e.g. 5 push/day/user) and aggregate ("3 people liked your
post").

**4. Provider failover.** Each channel has a primary and a fallback (SES → SendGrid, FCM →
APNs where applicable). Circuit breaker per provider; a provider outage routes to the fallback
or queues with a delay.

**5. Idempotency + at-least-once.** Providers can send twice; your dedupe prevents that at the
provider boundary by recording the send intent before calling.

### 5.4 Fan-out for a broadcast

10M users in one blast is a queue problem, not a request problem:

- Split into batches (e.g. 10k users per message), publish to a partitioned topic.
- Consumers process at a throttled rate so the provider's rate limit isn't exceeded.
- Monitor: send rate, provider error rate, bounce/complaint rate.
- **Never** `for (user : 10M) send()` in one process.

### 5.5 Failure

| Failure | Behaviour |
|---|---|
| Provider down | circuit breaker → fallback provider, or delayed retry |
| Provider rate-limits | throttled consumer, backoff |
| Duplicate event | dedupe insert rejects it |
| User opted out mid-flight | preference check at send time, not at enqueue time |
| Notification service down | Kafka buffers; nothing lost, delivery delayed |

**The key design statement:** "the business flow never waits for a notification — it publishes an
event and returns. Notification delivery is a separate, retryable, monitored pipeline."

---

## 6. Choosing the Right Store

| Access pattern | Store | Why |
|---|---|---|
| Point lookups by key | KV (Redis, DynamoDB) | O(1), horizontally scalable |
| Range scans, joins, transactions | Relational (Postgres/MySQL) | ACID, indexes, planner |
| Full-text relevance | Search (Elasticsearch) | inverted index, BM25, facets |
| Aggregates over billions of rows | Columnar (ClickHouse, BigQuery, Druid) | column scans, compression |
| Geo proximity | PostGIS / geo index | spatial index |
| Ranking / leaderboards | Redis sorted set | O(log N) rank ops |
| Time series | TSDB (TimescaleDB, Influx, Prometheus) | compression, downsampling |
| Large blobs | Object storage (S3) | cheap, durable, CDN-able |
| Graph traversal (deep) | Graph DB (Neo4j) | native traversal (usually a relational recursive CTE suffices) |
| Event log / replay | Kafka | retention, consumer groups |

**The rule to state:** *"I choose the store per access pattern and keep one relational source of
truth; everything else is derived and rebuildable."*

---

## 7. Interview Questions & Model Answers

**Q1. Why not just use `LIKE` for search?**
> No index can serve a leading wildcard, so it's a full scan; there's no ranking, no typo
> tolerance, no facets. Up to a few million rows Postgres full-text with a GIN index is a
> perfectly good answer — I'd only move to Elasticsearch when relevance tuning, facets at scale,
> or fuzzy matching become requirements, because it's another system to operate and keep in sync.

**Q2. How do you keep the search index in sync with the DB?**
> Outbox or CDC into Kafka, then an idempotent indexer that upserts by document id. That keeps
> the write path atomic and lets me replay to rebuild. The index is derived, so it's eventually
> consistent with a bounded lag — I'd monitor index lag and never make a user-facing guarantee
> that requires a just-created record to be searchable.

**Q3. Why can't analytics queries run on the OLTP database?**
> Different access pattern and different layout. The OLTP database is row-oriented and indexed
> for point reads; a report scans millions of rows and will evict the working set from cache and
> saturate CPU, which is how "the report killed production" happens. So analytics lives in a
> columnar store fed asynchronously, and dashboards read aggregates, not raw tables.

**Q4. How do you compute unique users in an analytics pipeline?**
> You can't sum daily uniques. I'd keep HyperLogLog sketches per bucket and merge them, which
> gives ~1–2% error in kilobytes instead of storing every user id. For anything that must be
> exact — billing — I'd keep the distinct ids for the period and accept the cost.

**Q5. How would you build a leaderboard for 10M users?**
> Redis sorted set: `ZADD` on score change and O(log N) `ZREVRANK` for a user's position. The
> problems are memory (~1 GB per board) and hot-key reads, so I'd keep the top N in Redis with
> per-period boards that expire via TTL, replicate the top-100 into a small key for edge caching,
> and return bucketed ranks for users far from the top.

**Q6. Why doesn't a B-tree index work for "near me"?**
> Because proximity is two-dimensional and a B-tree is one-dimensional. An index on `(lat, lng)`
> can range-scan latitude and then has to filter longitude by hand, and the number of kilometres
> per degree of longitude varies with latitude. A spatial index — GiST in PostGIS, or geohash /
> S2 / H3 cells — prunes in both dimensions.

**Q7. What's the geohash boundary problem?**
> Two points a few metres apart can land in different cells with completely different prefixes,
> so a prefix query misses them. The fix is to query the cell plus its eight neighbours, which
> covers a search radius up to the cell size. That's why geohash precision is chosen from the
> query radius, not arbitrarily.

**Q8. How do you avoid duplicate notifications?**
> A deterministic notification id from `(user, event, channel, window)` with a unique constraint,
> inserted before the send. Kafka redelivery then hits the constraint and skips. Combined with
> preference checks at send time and per-user rate caps, that covers the realistic failure modes.

**Q9. A marketing blast to 10M users — how?**
> Batched fan-out: one message per 10k users on a partitioned topic, throttled consumers so the
> provider's rate limit isn't exceeded, dedupe per user, and a kill switch plus a progress
> dashboard. Never a loop in a single process, and never the request path.

**Q10. How do you rebuild a derived store safely?**
> New index/schema, backfill from the source of truth in throttled pages, dual-write new events
> to both, compare counts and sample documents, then atomically swap an alias. Keep the old one
> for a day for rollback. The requirement that makes this possible is that the derived store holds
> no unique state — that's the design invariant I enforce from day one.

---

## 8. Revision Card

```
┌──────────── DATA-INTENSIVE — 60s RECALL ─────────────────────────────────────┐
│ ONE PATTERN: relational DB = TRUTH · specialised store = DERIVED,           │
│   fed ASYNC via outbox/CDC · eventually consistent · REBUILDABLE.            │
│                                                                              │
│ SEARCH: inverted index. Postgres FTS is fine to a few M rows; ES when you    │
│   need relevance/facets/fuzzy. text vs keyword. filter vs must (score!).      │
│   Mapping change = new index + alias swap. Deletes must propagate.           │
│                                                                              │
│ ANALYTICS: OLTP row store ≠ OLAP column store. Kafka → stream rollups +      │
│   raw in S3 + warehouse. PRE-AGGREGATE. Uniques need HLL (can't sum!).       │
│   Recompute windows, don't increment. Sample explicitly.                     │
│                                                                              │
│ LEADERBOARD: Redis ZSET, O(log N). Problems = memory + hot shard.            │
│   Top-N per period with TTL; cache top-100 at edge; bucket far ranks.        │
│                                                                              │
│ GEO: B-tree can't do 2D. PostGIS/GiST or geohash/S2/H3.                      │
│   GOTCHA: query the 9 neighbouring cells or miss edge cases.                 │
│                                                                              │
│ NOTIFICATIONS: outbox → Kafka → dedupe (deterministic id) → prefs →          │
│   rate cap → provider + fallback. Business flow NEVER waits.                 │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [03 · Classic Design Problems](./03-Classic-Design-Problems.md) · **Next:** [05 · Capacity Estimation](./05-Capacity-Estimation-and-Back-of-Envelope.md)
