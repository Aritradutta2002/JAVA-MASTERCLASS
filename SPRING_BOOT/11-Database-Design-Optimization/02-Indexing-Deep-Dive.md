# 02 · Indexing Deep Dive

> **Level:** Intermediate → Expert
> **Interview Frequency:** ★★★★★ ("Why isn't this index being used?")
> **Prerequisite:** [01 · Schema Design & Normalization](./01-Schema-Design-and-Normalization.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Sorted Copy](#2-mental-model--the-sorted-copy)
- [3. B-Tree Internals](#3-b-tree-internals)
- [4. Composite Indexes & the Leftmost Prefix](#4-composite-indexes--the-leftmost-prefix)
- [5. Covering Indexes & Index-Only Scans](#5-covering-indexes--index-only-scans)
- [6. The Other Index Types](#6-the-other-index-types)
- [7. Selectivity, Cardinality and Cost](#7-selectivity-cardinality-and-cost)
- [8. When Indexes Are Ignored](#8-when-indexes-are-ignored)
- [9. Index Maintenance & Cost](#9-index-maintenance--cost)
- [10. Code Examples](#10-code-examples)
- [11. Common Mistakes & Gotchas](#11-common-mistakes--gotchas)
- [12. Interview Questions & Model Answers](#12-interview-questions--model-answers)
- [13. Self-Check & Revision Card](#13-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> An index is a **sorted copy of a column (or several) with pointers to rows**. Sorted means
> O(log N) seek instead of O(N) scan. That's the whole idea; everything else is detail.
>
> A **composite index** is sorted by `(a, b, c)` — so it can serve `a`, `a,b`, `a,b,c` and
> range scans on the **last** used column. It **cannot** skip `a`. That's the leftmost-prefix
> rule, and it's the #1 interview question here.
>
> **Covering index**: if the index contains every column the query needs, the engine never
> touches the table (index-only scan). Adding `INCLUDE` columns gets this without widening the
> sort key.
>
> Indexes are only used when the planner **estimates** selectivity as high enough. A condition
> matching 40% of rows will use a sequential scan because it's genuinely faster — the index
> isn't "ignored", it's correctly rejected.
>
> Every index costs **write throughput and disk**. Audit `pg_stat_user_indexes`; drop the ones
> with `idx_scan = 0`.

---

## 1. What & Why

### 1.1 The scan problem

Without an index, `WHERE email = 'ada@x.io'` reads every row: a **sequential scan**, O(N). On
10M rows that's ~1–3 seconds and a huge chunk of buffer cache.

With a B-tree index: descend 3–4 pages, then one heap fetch. ~0.1 ms. That is a
**3–4 orders of magnitude** difference — the single largest lever in database performance.

### 1.2 What an index actually stores

```
 index entry:  (key value, heap tuple id / row pointer)
 sorted by:    key value, then by the table's row pointer (for uniqueness of the sort)
```

Nothing else — no copy of the row (unless it's a covering/include index or a clustered/index-
organised table).

### 1.3 The three questions every index decision answers

1. **Which query does this index serve?** (name the query, not the table)
2. **What's the leftmost column, and is it always supplied?**
3. **What does it cost?** (write amplification, disk, vacuum work)

---

## 2. Mental Model — The Sorted Copy

Think of an index as a **second copy of one column, kept in sorted order, in a slim file with
pointers back to the row**.

```
  TABLE (heap, unordered)              INDEX on email (sorted)
  ┌────┬─────────────────────┐        ┌──────────────────┬─────────┐
  │ 7  │ zoe@x.io            │        │ ada@x.io         │ → row 42│
  │ 42 │ ada@x.io            │        │ bob@x.io         │ → row 15│
  │ 15 │ bob@x.io            │        │ zoe@x.io         │ → row 7 │
  └────┴─────────────────────┘        └──────────────────┴─────────┘
       must read all 3                    binary search → 1 page → 1 row
```

**Why sorting gives you more than equality:**

| Query | Index usable? |
|---|---|
| `WHERE email = ?` | ✅ seek |
| `WHERE email > 'b' AND email < 'm'` | ✅ range scan |
| `ORDER BY email` | ✅ already sorted — no sort step |
| `WHERE email LIKE 'ada%'` | ✅ prefix matches the sort order |
| `WHERE email LIKE '%ada%'` | ❌ can't binary-search a wildcard prefix |
| `WHERE lower(email) = ?` | ❌ unless the index is on `lower(email)` |

**The one-liner:**

> *An index only helps if the query can use sorted order. Anything that destroys the order
> (a leading wildcard, a function on the column, a `NOT`, an `OR` across columns) kills the
> benefit.*

---

## 3. B-Tree Internals

Postgres calls it a B-tree (technically a B+ tree variant): all values in leaves, internal
nodes hold separators, leaves linked for range scans.

```
                    ┌──────────────┐
                    │  root: 300   │
                    └──┬───────┬───┘
              ┌────────┘       └────────┐
        ┌─────▼─────┐            ┌──────▼────┐
        │ <300: 120 │            │ >300: 480 │
        └──┬─────┬──┘            └───┬───┬───┘
      ┌────┘     └───┐        ┌──────┘   └────┐
   ┌──▼──┐      ┌───▼─┐   ┌──▼──┐       ┌────▼┐
   │leaf │◄────►│leaf │◄─►│leaf │◄─────►│leaf │
   │ 1-80│      │81-120│  │301-400│    │>480 │
   └─────┘      └─────┘   └──────┘      └─────┘
```

- **Height is logarithmic.** A page holds hundreds of keys, so 10M rows is ~3–4 levels deep.
  That's 3–4 page reads per lookup, mostly cached after warm-up.
- **Range scans** seek to the start leaf, then walk the linked list — sequential I/O, fast.
- **Inserts** may split a page (a new page + a parent update) — random inserts (UUIDs) split
  more often than monotonic ones.
- **Updates to indexed columns** move the entry (delete + insert). Updates to non-indexed
  columns don't touch the index — unless it's a **HOT update**, which requires free space on
  the page (`fillfactor`).

### Postgres-specific facts

- **Heap + index are separate** — an index hit is followed by a **heap fetch**, which may need
  to visit many pages for scattered rows. This is why "index scan" can be slower than a
  sequential scan when many rows match.
- **`n_distinct` and `correlation`** in `pg_stats` drive the planner's choice.
- **Visibility map** — index-only scans only avoid the heap visit if the page is all-visible
  (i.e. recently vacuumed). A table that hasn't been vacuumed gets *heap fetches* even on an
  index-only scan.
- **BRIN** for huge append-only tables — tiny index (a few KB) with per-block ranges.

---

## 4. Composite Indexes & the Leftmost Prefix

```sql
CREATE INDEX idx_a ON orders (customer_id, status, placed_at);
```

This index is sorted by `customer_id`, then `status`, then `placed_at`. It can serve:

| Query | Usable? | Why |
|---|---|---|
| `WHERE customer_id = 5` | ✅ | prefix |
| `WHERE customer_id = 5 AND status = 'PAID'` | ✅ | prefix |
| `WHERE customer_id = 5 AND status = 'PAID' AND placed_at > '2026-01-01'` | ✅ | full key + range |
| `WHERE customer_id = 5 AND placed_at > '2026-01-01'` | ⚠️ | uses `customer_id` only; `placed_at` can't be range-scanned after skipping `status` (but Postgres can filter, and can do an "index skip" style scan in limited cases) |
| `WHERE status = 'PAID'` | ❌ | not a prefix |
| `WHERE placed_at > '2026-01-01'` | ❌ | not a prefix |

**The rule:** equality columns first (in any order among themselves), then **one** range
column last. `(a, b, c)` with `a = ? AND c > ?` cannot range-scan `c`.

**Equality-before-range is not a style preference — it's the sort order.** Once you do a range
scan on `b`, the index ordering by `c` is no longer globally sorted, so it can only be filtered,
not seeked.

### Column order heuristics

1. **Equality columns first**, ordered by selectivity (most selective first is *usually* best,
   but the difference is small if all are equality).
2. **The range/sort column last.**
3. If a query has `ORDER BY x`, put `x` after the equality columns to eliminate the sort.
4. Don't duplicate: `(a,b)` and `(a)` — the second is redundant.
5. `(b,a)` is **not** the same as `(a,b)`; if both query shapes exist, you may need both (or
   reorder the query).

### The "index skip scan" nuance

Modern Postgres (18+) has limited skip-scan support; older versions cannot seek past a missing
leading column. MySQL 8 has skip scan for low-cardinality leading columns. Don't rely on it —
design the index for the query.

---

## 5. Covering Indexes & Index-Only Scans

If the index contains all columns the query needs, the engine can answer from the index alone.

```sql
-- query needs only these three columns
SELECT customer_id, status, placed_at FROM orders WHERE customer_id = 5 AND status = 'PAID';

-- covering index (all three in the key)
CREATE INDEX orders_cover ON orders (customer_id, status, placed_at);
```

```sql
-- or keep the sort key narrow and INCLUDE the payload
CREATE INDEX orders_cover ON orders (customer_id, status) INCLUDE (placed_at);
```

`INCLUDE` columns are stored in the leaf but **not** used for ordering — they add size without
helping seeks. Ideal when you want a covering index without a fat sort key.

**Verify it in the plan:** `Index Only Scan` with `Heap Fetches: 0`. If `Heap Fetches` is high,
the table needs vacuuming (visibility map).

**Cost:** a covering index is wider → fewer entries per page → more pages → more cache pressure.
Only cover the hot queries; don't cover everything.

---

## 6. The Other Index Types

| Type | Structure | Use for |
|---|---|---|
| **B-tree** | sorted | `=`, `<`, `>`, `BETWEEN`, `ORDER BY`, prefix `LIKE` — 95% of indexes |
| **Hash** | hash table | equality only; rarely better than B-tree (no range, no order) |
| **GIN** | inverted index | `jsonb`, `text[]`, full-text `tsvector` |
| **GiST** | balanced tree of predicates | geometry, ranges, `EXCLUDE` constraints |
| **BRIN** | per-block min/max | huge append-only tables (time-series), tiny footprint |
| **SP-GiST** | space-partitioned | points, quadtrees, text prefix trees |
| **Partial** | B-tree on a subset | `WHERE status = 'PENDING'` — small and hot |
| **Expression** | B-tree on an expression | `lower(email)`, `date_trunc('day', ts)` |

### Partial indexes — underused and excellent

```sql
-- only 0.1% of orders are pending; index only those
CREATE INDEX orders_pending_idx ON orders (placed_at)
WHERE status = 'PENDING';
```

- Tiny (fits in memory), fast to scan, and the planner uses it when the query has the same
  predicate. Perfect for work queues and "unprocessed" flags.
- Also the correct way to make a unique constraint apply only to live rows:
  `UNIQUE (email) WHERE deleted_at IS NULL`.

### Expression indexes

```sql
CREATE INDEX customers_lower_email_idx ON customers (lower(email));
-- query must use the same expression to match
SELECT * FROM customers WHERE lower(email) = 'ada@x.io';
```

Mismatch (`WHERE email ILIKE '...'`) won't use it. Prefer storing a normalised column
(`email_normalized`) if multiple code paths query it — clearer and harder to get wrong.

### JSONB with GIN

```sql
CREATE INDEX orders_meta_idx ON orders USING gin (metadata jsonb_path_ops);
SELECT * FROM orders WHERE metadata @> '{"channel":"mobile"}';
```

`jsonb_path_ops` is smaller and faster for `@>` containment; the default `jsonb_ops` also
supports key-existence queries.

### Text search

```sql
ALTER TABLE products ADD COLUMN search tsvector
  GENERATED ALWAYS AS (to_tsvector('english', name || ' ' || description)) STORED;
CREATE INDEX products_search_idx ON products USING gin (search);
```

A generated column keeps it in sync automatically — much better than a trigger.

---

## 7. Selectivity, Cardinality and Cost

**Selectivity** = fraction of rows a predicate matches. High selectivity (few rows) → index.
Low selectivity (many rows) → sequential scan is genuinely cheaper.

```
Index scan cost ≈ log(N) seek + (matchingRows × heapFetchCost) + random I/O
Seq scan cost   ≈ tablePages × seqPageCost        (sequential, cheap per page)

crossover is typically around 5–20% of the table (depends on correlation/clustering)
```

**Cardinality** = number of distinct values.

```sql
-- low cardinality (2 distinct values): a plain index is nearly useless
CREATE INDEX orders_paid_idx ON orders (is_paid);        -- ❌ planner will ignore it
CREATE INDEX orders_paid_pending_idx ON orders (placed_at) WHERE is_paid = false;  -- ✅
```

A boolean column index only makes sense if one value is rare — and then a **partial index** is
better.

### The planner's estimates

`EXPLAIN` shows `rows=` estimates vs actual (`EXPLAIN ANALYZE`). A 100x misestimate usually
means:

- **Stale statistics** → `ANALYZE table`.
- **Correlated columns** → the planner multiplies independent selectivities: `city='Paris' AND
  country='France'` estimates as 1/n_city × 1/n_country, which is wrong by orders of
  magnitude. Fix with `CREATE STATISTICS ... (dependencies)` or a rewritten query.
- **Function on a column** → no stats for the expression without an expression index.
- **Skewed data** → default `default_statistics_target` too low; increase per column.

---

## 8. When Indexes Are Ignored

| Cause | Why | Fix |
|---|---|---|
| Predicate matches most of the table | seq scan is cheaper | Accept it, or use a partial index |
| Function on the indexed column | `WHERE lower(email)=?` isn't `email` | Expression index or a normalised column |
| Leading wildcard `LIKE '%x'` | can't binary-search | Trigram (`pg_trgm` GIN) or full-text |
| Type mismatch | `where id = '42'` vs `int` may force a cast | Match types exactly |
| `OR` across different columns | may need two index scans + union | Rewrite as `UNION ALL`, or add a composite |
| `NOT IN` / `<>` | low selectivity, no ordering | Rewrite; often inherently slow |
| Stale statistics | bad estimates | `ANALYZE`, autovacuum tuning |
| Implicit cast from a parameter (JDBC `setObject`) | Postgres can't infer the type | Cast explicitly in the query |
| Very wide index, hot table | planner balances cost | Consider `INCLUDE`-slim index |
| Index on an expression the query doesn't match exactly | e.g. `date(ts)` vs `ts::date` | Use the identical expression |
| Too many indexes | planner may pick a bad one | Audit and drop unused |
| `SELECT *` on a narrow query | can't be covered | Select only needed columns |

**The interview-winning line:** "The index isn't being ignored — the planner costed it and chose
otherwise. My job is to find out whether the estimate is wrong (fix stats/query) or whether the
predicate genuinely matches too many rows (then a different index shape or a different query is
the answer)."

---

## 9. Index Maintenance & Cost

### Write amplification

Every `INSERT` writes N index entries; every `UPDATE` of an indexed column deletes and
re-inserts. 8 indexes on a write-heavy table can double or triple write cost.

```
INSERT INTO orders (...)  →  1 heap write + 8 index inserts + WAL for all of it
```

### Finding unused and duplicate indexes

```sql
-- unused (idx_scan = 0 since last stats reset)
SELECT relname AS table, indexrelname AS index, idx_scan, pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes
WHERE idx_scan = 0 AND indexrelid NOT IN (SELECT conindid FROM pg_constraint)
ORDER BY pg_relation_size(indexrelid) DESC;

-- duplicate (same leading columns)
SELECT indrelid::regclass, array_agg(indexrelid::regclass)
FROM pg_index GROUP BY indrelid, indkey HAVING count(*) > 1;
```

Do **not** drop a "0 scans" index blindly — it may serve a monthly report or a unique
constraint. Check `pg_stat_database.stats_reset` first.

### Bloat

- `UPDATE`s leave dead tuples; indexes accumulate dead entries until `VACUUM`.
- `REINDEX CONCURRENTLY` rebuilds without blocking writes.
- `fillfactor` < 100 leaves space for HOT updates (good for frequently-updated columns).

### Creating indexes without downtime

```sql
CREATE INDEX CONCURRENTLY idx_orders_placed ON orders (placed_at);
```

- Doesn't block writes, but takes longer, can't run inside a transaction, and on failure leaves
  an **invalid** index (`indisvalid = false`) — check `pg_index` and drop/retry.

### Sizing

```sql
SELECT pg_size_pretty(pg_relation_size('orders')) AS table_size,
       pg_size_pretty(pg_indexes_size('orders')) AS index_size;
```

A healthy OLTP table is often 1–2x its data size in indexes. If indexes are 10x the table,
you're over-indexed.

---

## 10. Code Examples

### 10.1 Diagnosing one slow query

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, total_cents, placed_at
FROM orders
WHERE customer_id = 42 AND status = 'PAID'
ORDER BY placed_at DESC
LIMIT 20;
```

Plan without a good index:

```
Limit  (cost=182340.11..182340.16 rows=20) (actual time=812.3..812.4 rows=20)
  ->  Sort  (actual time=812.3..812.3)  Sort Key: placed_at DESC
        Sort Method: top-N heapsort  Memory: 26kB
        ->  Seq Scan on orders  (cost=0.00..172000.00 rows=12500) (actual time=0.02..780.0)
              Filter: ((customer_id = 42) AND (status = 'PAID'))
              Rows Removed by Filter: 9987500
```

With the right index:

```sql
CREATE INDEX orders_cust_status_placed_idx
  ON orders (customer_id, status, placed_at DESC)
  INCLUDE (total_cents);
```

```
Limit  (actual time=0.045..0.062 rows=20)
  ->  Index Only Scan using orders_cust_status_placed_idx
        Index Cond: ((customer_id = 42) AND (status = 'PAID'))
        Heap Fetches: 0
```

812 ms → 0.06 ms. Note the three wins stacked: composite key (seek), `DESC` ordering matching
the query (no sort), and `INCLUDE` (no heap fetch).

### 10.2 Partial index for a work queue

```sql
CREATE INDEX outbox_unpublished_idx
  ON outbox (created_at)
  WHERE published_at IS NULL;
```

The relay's `SELECT ... WHERE published_at IS NULL ORDER BY created_at LIMIT 100` now scans a
tiny index regardless of how many published rows exist. Without it, the index grows forever and
the query degrades.

### 10.3 Trigram index for `LIKE '%term%'`

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX products_name_trgm_idx ON products USING gin (name gin_trgm_ops);

SELECT * FROM products WHERE name ILIKE '%wireless%';   -- now indexable
```

Trade-off: a GIN trigram index is large and slower to update than a B-tree. Use it for
user-facing search boxes, not for every text column.

### 10.4 JPA: make the query match the index

```java
public interface OrderRepository extends JpaRepository<Order, Long> {
    // generates: WHERE customer_id = ? AND status = ? ORDER BY placed_at DESC
    // matches (customer_id, status, placed_at DESC) exactly
    List<Order> findTop20ByCustomerIdAndStatusOrderByPlacedAtDesc(Long customerId, OrderStatus status);
}
```

Derived queries generate SQL you must check against your index. `@Query` with an explicit
column list is safer than `SELECT o` when you want a covering index.

```java
@Query("""
    select new com.acme.dto.OrderSummary(o.id, o.totalCents, o.placedAt)
    from Order o
    where o.customer.id = :customerId and o.status = :status
    order by o.placedAt desc
    """)
List<OrderSummary> summaries(@Param("customerId") Long id, @Param("status") OrderStatus s, Pageable p);
```

### 10.5 Verify in tests, not in production

```java
@SpringBootTest
class OrderQueryPlanTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void summaryQueryUsesIndex() {
        var plan = jdbc.queryForList("""
            EXPLAIN (FORMAT JSON) SELECT id, total_cents, placed_at FROM orders
            WHERE customer_id = 42 AND status = 'PAID' ORDER BY placed_at DESC LIMIT 20
            """).toString();
        assertThat(plan).contains("Index Only Scan").doesNotContain("Seq Scan");
    }
}
```

A plan assertion in CI catches the regression where someone adds a column and silently breaks
the covering index.

---

## 11. Common Mistakes & Gotchas

| Mistake | Consequence | Fix |
|---|---|---|
| Index every column | write amplification, bloat, bad plans | Index for queries, audit unused |
| `(a,b)` when queries only filter `b` | unusable | Reorder or add `(b)` |
| Equality columns after a range column | range column can't seek | Equality first, range last |
| Index on `boolean` | planner ignores it | Partial index |
| Indexing `email` but querying `lower(email)` | no match | Expression index or normalised column |
| `LIKE '%x%'` with a B-tree | full scan | Trigram GIN |
| `SELECT *` | can't be covered; fat heap fetches | Select needed columns |
| No `ANALYZE` after a bulk load | bad estimates, wrong plans | `ANALYZE` (or wait for autovacuum) |
| `CREATE INDEX` (non-concurrent) on a live table | blocks writes | `CONCURRENTLY` |
| Forgetting the FK child index | parent deletes scan the child | `CREATE INDEX ON child(fk)` |
| Duplicate/redundant indexes | pure write cost | Audit `pg_stat_user_indexes` |
| Assuming index-only means no heap access | `Heap Fetches` high after bloat | Vacuum; check the visibility map |
| Over-long composite keys | index bigger than the table | Narrow the key, `INCLUDE` the rest |
| Random UUID PK | page splits, bloat | UUIDv7 or bigint |
| Testing only with tiny data | plans differ at scale | Test with production-like volumes |

---

## 12. Interview Questions & Model Answers

**Q1. How does a B-tree index make a query faster?**
> It keeps the column values in sorted order in a separate structure with pointers to rows, so
> a lookup is a logarithmic descent (3–4 pages for millions of rows) instead of a linear scan.
> Sorted order also gives free range scans and can eliminate a sort for `ORDER BY`. The trade is
> extra storage and extra work on every write.

**Q2. Explain the leftmost-prefix rule.**
> A composite index `(a, b, c)` is sorted by `a`, then `b`, then `c`. You can use it if your
> query supplies `a`, or `a` and `b`, or all three — you can't seek on `b` without `a` because
> the entries aren't contiguous in `b` order. Also, only one range column can be seeked, and it
> must be last: after a range on `b`, ordering by `c` is only within each value of `b`, so `c`
> can filter but not seek.

**Q3. Why isn't my index being used?**
> First, is the estimate wrong — stale stats, correlated columns, a function on the column, or a
> type mismatch from an implicit cast? Second, is the predicate genuinely unselective — matching
> 30% of rows, where a sequential scan really is cheaper? Third, does the index expression
> exactly match the query — `lower(email)` vs `email`, or a leading-wildcard `LIKE`? The planner
> isn't ignoring the index; it's costing it. My job is to determine which of those three is
> happening.

**Q4. What's a covering index?**
> An index containing every column the query needs, so the engine answers from the index without
> touching the table — an index-only scan. I'd use `INCLUDE` for the payload columns so the sort
> key stays narrow. The cost is a wider index and more cache pressure, so I only cover the hot
> queries, and I verify with `Heap Fetches: 0`, which also requires the table to be vacuumed.

**Q5. When would you use a partial index?**
> When queries consistently filter on one value of a low-cardinality column — pending jobs,
> unpublished outbox rows, active records. It's tiny, stays in memory, and the planner uses it
> when the predicate matches. It's also how you make a unique constraint apply only to live rows
> in a soft-delete schema.

**Q6. How many indexes is too many?**
> When write throughput or disk becomes the bottleneck. Every index costs an entry write per
> insert and an entry move per indexed-column update, plus WAL. I audit `pg_stat_user_indexes`
> for `idx_scan = 0` and check for prefix-duplicate indexes; on a write-heavy table I'd rather
> have three well-chosen composite indexes than eight single-column ones.

**Q7. How do you add an index to a 500M-row production table?**
> `CREATE INDEX CONCURRENTLY`, which doesn't block writes. It takes longer and can't run inside
> a transaction, and if it fails it leaves an invalid index I must drop and retry. On huge
> tables I'd also consider whether a partial or BRIN index would be smaller and sufficient.

**Q8. What's the difference between a B-tree and a hash index?**
> Hash only supports equality — no ranges, no ordering — so it rarely beats a B-tree, which
> also handles equality. B-tree is the default for a reason. Hash shows up in special cases
> (very long keys, memory-constrained) and in other engines' internals.

**Q9. Why does a query get slower over time with the same index?**
> Bloat and statistics drift. Dead tuples accumulate in the heap and index, the visibility map
> goes stale so index-only scans start doing heap fetches, and estimates degrade as data
> distribution changes. The fix is autovacuum tuning, `REINDEX CONCURRENTLY` for index bloat,
> and `ANALYZE` after bulk changes.

**Q10. How would you index a `status` column with 5 values?**
> Not with a plain index — 20% of rows per value means a seq scan wins. If queries are "all
> PENDING jobs ordered by time", a partial index `(created_at) WHERE status = 'PENDING'` gives a
> tiny index for the hot path. For reporting across statuses, a composite `(status, created_at)`
> helps when combined with other filters, or I let the aggregate scan the table.

---

## 13. Self-Check & Revision Card

- [ ] I can explain an index as a sorted copy with pointers, and why that's enough.
- [ ] I can state the leftmost-prefix rule and the one-range-column-last rule.
- [ ] I can design a composite index for a given query, column order included.
- [ ] I can explain covering/`INCLUDE` and `Heap Fetches`.
- [ ] I can name partial, expression, GIN, BRIN and when each applies.
- [ ] I can diagnose "index not used" in three steps.
- [ ] I can find unused/duplicate indexes with a query.
- [ ] I can add an index without downtime.

```
┌──────────────────────── INDEXING — 60s RECALL ───────────────────────────────┐
│ Index = sorted copy of columns + row pointers. O(log N) seek vs O(N) scan.   │
│ Sorted ⇒ equality, range, ORDER BY free, prefix LIKE. Wildcard breaks it.    │
│                                                                              │
│ COMPOSITE (a,b,c): serves a | a,b | a,b,c. CANNOT skip a.                    │
│ EQUALITY FIRST, ONE RANGE LAST. After a range, later cols filter only.       │
│                                                                              │
│ COVERING: all needed cols in the index ⇒ Index Only Scan (Heap Fetches: 0,   │
│ needs vacuum). INCLUDE keeps the sort key narrow.                            │
│                                                                              │
│ TYPES: B-tree (default) · partial (low-cardinality hot subset) ·            │
│ expression (lower(email)) · GIN (jsonb, arrays, trigram) · BRIN (big append) │
│                                                                              │
│ NOT USED: unselective · function on col · leading wildcard · type mismatch · │
│ stale stats · correlated columns. The planner COSTED it.                     │
│                                                                              │
│ COST: +1 index write per INSERT, +WAL, +disk. Audit idx_scan = 0.            │
│ CREATE INDEX CONCURRENTLY for production.                                    │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [01 · Schema Design & Normalization](./01-Schema-Design-and-Normalization.md) · **Next:** [03 · Query Optimisation & Execution Plans](./03-Query-Optimization-and-Execution-Plans.md)
