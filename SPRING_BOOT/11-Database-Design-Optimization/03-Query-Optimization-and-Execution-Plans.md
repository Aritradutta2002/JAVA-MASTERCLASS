# 03 · Query Optimisation & Execution Plans

> **Level:** Intermediate → Expert
> **Interview Frequency:** ★★★★★ ("This query is slow — what do you do?")
> **Prerequisite:** [02 · Indexing Deep Dive](./02-Indexing-Deep-Dive.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. The Method](#1-the-method)
- [2. Mental Model — The Planner's Job](#2-mental-model--the-planners-job)
- [3. Reading EXPLAIN](#3-reading-explain)
- [4. Join Algorithms](#4-join-algorithms)
- [5. The Usual Suspects](#5-the-usual-suspects)
- [6. N+1 Queries](#6-n1-queries)
- [7. Pagination Done Right](#7-pagination-done-right)
- [8. Batch Operations](#8-batch-operations)
- [9. Statistics & Plan Stability](#9-statistics--plan-stability)
- [10. Code Examples](#10-code-examples)
- [11. Common Mistakes](#11-common-mistakes)
- [12. Interview Questions & Model Answers](#12-interview-questions--model-answers)
- [13. Self-Check & Revision Card](#13-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> Optimising is a **loop**, not a checklist: measure (`EXPLAIN ANALYZE`) → find the node where
> actual time diverges from estimate → fix that one thing → measure again.
>
> The four things that are almost always wrong: **missing index** (seq scan where a seek
> would do), **bad estimate** (stale or correlated stats ⇒ wrong join order), **N+1** (a
> query in a loop), and **unbounded result sets** (`SELECT *` + `OFFSET 100000`).
>
> Read the plan from the **inside out**: the innermost node with the biggest
> `actual time × loops` is where the time goes. `rows=` vs `actual rows=` differing by 100x
> means the *estimate* is the bug, not the index.
>
> `OFFSET` pagination degrades linearly because the DB must produce and discard every skipped
> row. **Keyset pagination** (`WHERE (placed_at, id) < (?, ?) ORDER BY placed_at DESC, id DESC
> LIMIT 20`) is O(page size) forever.

---

## 1. The Method

A repeatable procedure beats random index-adding:

```
1. REPRODUCE   get the exact SQL (not the ORM abstraction) and realistic parameters
2. MEASURE     EXPLAIN (ANALYZE, BUFFERS) — actual time, not estimates
3. LOCATE      find the node with max (actual time × loops)
4. HYPOTHESISE one cause: missing index? bad estimate? N+1? unbounded rows?
5. FIX ONE     change one thing
6. REMEASURE   compare, keep or revert
7. VERIFY      under production-like data volume, with a plan assertion if possible
```

**Step 1 is the one people skip.** Hibernate logs the generated SQL
(`spring.jpa.show-sql` is not enough — use `logging.level.org.hibernate.SQL=DEBUG` with
`org.hibernate.orm.jdbc.bind=TRACE` for parameters). You cannot tune a query you haven't
seen.

**Step 7 matters because plans change with data volume.** A query that's fast on 10k dev rows
may seq-scan on 10M rows — or vice versa (a plan that's fine at scale is terrible on a tiny
table). Always test near production size.

---

## 2. Mental Model — The Planner's Job

The planner is an **optimiser with incomplete information**. It does three things:

```
1. TRANSFORM   rewrite the query (flatten subqueries, push predicates down, inline views)
2. ENUMERATE   consider join orders and access paths
3. COST        estimate cost of each using STATISTICS, pick the cheapest
```

Every performance bug is one of:

| Bug class | Symptom in the plan |
|---|---|
| **Missing/wrong index** | `Seq Scan` on a large table with a selective filter |
| **Bad estimate** | `rows=10` vs `actual rows=50000` |
| **Wrong join order** | a huge intermediate result being joined/hashed |
| **N+1 / chatty SQL** | many fast queries instead of one (not visible in a single plan) |
| **Unbounded work** | `Sort` over millions of rows, `OFFSET` deep, `SELECT *` |

**The insight to state:** the planner's decisions are only as good as its statistics. If you
see a wildly wrong estimate, don't add an index — **fix the information** (ANALYZE, extended
statistics, expression index, or rewrite the predicate into something it can estimate).

---

## 3. Reading EXPLAIN

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) <query>;
```

- `EXPLAIN` alone = estimates only (safe in prod).
- `ANALYZE` = actually executes (dangerous for `UPDATE`/`DELETE` — wrap in a transaction and
  roll back).
- `BUFFERS` = cache hits vs disk reads. High `read` means cold cache.
- `VERBOSE` = output columns and schema-qualified names.

### The node types

| Node | Meaning | Watch for |
|---|---|---|
| `Seq Scan` | full table read | on a big table with a selective filter |
| `Index Scan` | index seek + heap fetches | many heap fetches for scattered rows |
| `Index Only Scan` | index only | `Heap Fetches` > 0 means vacuum needed |
| `Bitmap Index Scan` + `Bitmap Heap Scan` | collect row ids, then fetch in block order | good for medium selectivity |
| `Nested Loop` | for each outer row, probe inner | fine for small outer, terrible for large |
| `Hash Join` | build a hash of the smaller side, probe | needs memory (`work_mem`) |
| `Merge Join` | both sides sorted, merge | good when both are index-ordered |
| `Sort` | explicit sort | `external merge Disk:` means `work_mem` too small |
| `HashAggregate` / `GroupAggregate` | aggregation | `GroupAggregate` needs sorted input |
| `Limit` | stops early | if a `Sort` is below it, there's no early stop |
| `Gather` / `Gather Merge` | parallel workers | check worker count and skew |

### What to look at, in order

1. **`actual time` × `loops`** — the true total. A node with `actual time=0.05` but `loops=50000`
   costs 2.5 s.
2. **`rows=` vs `actual rows=`** — a 10x+ gap means bad estimates.
3. **`Rows Removed by Filter`** — reading rows just to throw them away. This is the signature of
   a missing or wrong index.
4. **`Sort Method: external merge Disk:`** — sort spilling; raise `work_mem` or add an index
   matching the `ORDER BY`.
5. **`Buffers: shared read=`** — actual disk I/O; if high, the working set doesn't fit in cache.
6. **`Heap Fetches`** — index-only scans that still hit the heap.

### A worked example

```
Nested Loop  (cost=1.43..8241.20 rows=200) (actual time=0.08..4120.55 rows=48000 loops=1)
  ->  Index Scan on customers  (actual time=0.05..0.20 rows=40 loops=1)
        Index Cond: (region = 'EU')
  ->  Seq Scan on orders  (actual time=0.02..78.00 rows=1200 loops=40)
        Filter: (customer_id = customers.id)
        Rows Removed by Filter: 998800
```

Reading it:
- The inner `Seq Scan` runs **40 times** (loops=40), 78 ms each → **3.1 s** of the 4.1 s total.
- It's a `Seq Scan` because there's no index on `orders.customer_id`.
- `rows=1200` estimated vs 1200 actual per loop, so estimates are fine — the *access path* is
  the problem.

Fix: `CREATE INDEX ON orders (customer_id);` → the inner node becomes an `Index Scan` at
~0.1 ms per loop.

---

## 4. Join Algorithms

| Algorithm | Cost model | Good when | Bad when |
|---|---|---|---|
| **Nested Loop** | outer rows × inner probe | outer is tiny, inner is indexed | outer is large (probes multiply) |
| **Hash Join** | build hash of smaller side, then scan larger | no useful index, large both sides | hash doesn't fit in `work_mem` |
| **Merge Join** | both inputs sorted, single pass | both already sorted (index or `Sort`) | requires sorting (expensive) |

**Nested loop blowup** is the classic production incident: the planner estimates 10 outer
rows, gets 50,000, and the inner probe runs 50,000 times. It shows as
`loops=50000` with a small per-loop time.

```sql
-- fix: give the planner better information, or force the join strategy for a bad case
SET LOCAL enable_nestloop = off;   -- diagnose only, never a permanent fix
```

`SET LOCAL` inside a transaction is for diagnosis. The real fix is statistics or a rewrite.

### Subquery strategies

- **`IN (subquery)`** — Postgres often turns this into a semi-join (good).
- **`EXISTS`** — semi-join, stops at the first match. Best when you only need existence.
- **`NOT IN`** — **dangerous with NULLs**: `x NOT IN (1, NULL)` is never true. Use
  `NOT EXISTS`.
- **`DISTINCT` on a join** — usually a sign the join fanned out; consider `EXISTS` instead.

```sql
-- ❌ can return zero rows unexpectedly if any subquery value is NULL
SELECT * FROM orders WHERE customer_id NOT IN (SELECT customer_id FROM blocked);

-- ✅ NULL-safe
SELECT * FROM orders o
WHERE NOT EXISTS (SELECT 1 FROM blocked b WHERE b.customer_id = o.customer_id);
```

---

## 5. The Usual Suspects

| Symptom | Likely cause | Fix |
|---|---|---|
| `Seq Scan` + high `Rows Removed by Filter` | missing index | add the right composite |
| Estimate off by 100x | stale stats / correlation | `ANALYZE`, extended statistics |
| `Nested Loop` with big `loops` | underestimated outer side | fix stats, or rewrite |
| `Sort ... external merge Disk` | `work_mem` too small | raise per-session, or index the ORDER BY |
| `OFFSET 500000` slow | linear skip cost | keyset pagination |
| `SELECT *` with wide rows | unnecessary I/O | select needed columns |
| `COUNT(*)` on a huge table for pagination | full scan | approximate count, or cursor style |
| Same query in a loop | N+1 | join/fetch, `IN` batch, `@EntityGraph` |
| `OR` across columns | can't use one index | `UNION ALL` of two indexed queries |
| `LIKE '%x%'` | no prefix order | trigram GIN |
| Dead tuples / bloat | autovacuum lagging | tune autovacuum, `VACUUM` |
| Lock waits | long transactions | short transactions, appropriate isolation |
| Too many round trips (small queries) | chatty app | batch, or pipeline |

### Rewriting `OR`

```sql
-- ❌ may seq-scan (or use two index scans + BitmapOr, which is fine actually — check the plan)
WHERE status = 'PAID' OR status = 'SHIPPED'

-- ✅ same semantics, single index range
WHERE status IN ('PAID','SHIPPED')

-- ❌ OR across different columns, often a seq scan
WHERE customer_id = 42 OR email = 'ada@x.io'

-- ✅ explicit union of two indexed paths
SELECT * FROM orders WHERE customer_id = 42
UNION
SELECT * FROM orders WHERE email = 'ada@x.io';
```

Always verify — `BitmapOr` sometimes handles the first case well. **Read the plan; don't
theorise.**

---

## 6. N+1 Queries

The single most common performance bug in Spring applications.

```java
List<Order> orders = orderRepo.findAll();                 // 1 query
for (Order o : orders) {
    o.getCustomer().getName();                            // +N queries (LAZY)
    o.getLines().size();                                  // +N more
}
// 1 + 2N queries for a page of 100 orders = 201 queries
```

### Detection

- `spring.jpa.properties.hibernate.generate_statistics=true` — the log prints
  `X JDBC statements executed` per session.
- A datasource proxy (p6spy, datasource-proxy) that counts statements per request.
- Your APM: 200 spans called `SELECT` under one endpoint is a smoking gun.
- Micrometer/Pinpoint query counts, or simply `pg_stat_statements` showing one query executed
  millions of times.

### Fixes, ranked

**1. `JOIN FETCH` / `@EntityGraph`** — one query, but watch for cartesian products on multiple
collections.

```java
@Query("""
    select distinct o from Order o
    join fetch o.lines
    where o.customer.id = :customerId
    """)
List<Order> findWithLines(Long customerId);
```

**2. `@EntityGraph`** for a declarative version:

```java
@EntityGraph(attributePaths = {"lines", "customer"})
List<Order> findByCustomerId(Long customerId);
```

**3. Batch loading** — turn N queries into N/batchSize queries:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 50
```

This is often the **best** fix: it works for lazily-loaded collections everywhere without
touching queries, and it handles multiple collections without a cartesian explosion.

**4. DTO projection** — don't load entities you won't mutate:

```java
@Query("""
    select new com.acme.dto.OrderRow(o.id, o.status, c.name, count(l))
    from Order o join o.customer c left join o.lines l
    where o.customer.id = :id
    group by o.id, o.status, c.name
    """)
List<OrderRow> rows(Long id);
```

**5. Two-step fetch** — ids first, then `WHERE id IN (...)`.

The `MultipleBagFetchException` (two `List` collections fetched eagerly) is the signal you need
batch fetching or `Set` instead of `List`.

---

## 7. Pagination Done Right

### OFFSET degrades linearly

```sql
SELECT * FROM orders ORDER BY placed_at DESC LIMIT 20 OFFSET 500000;
```

The DB must generate and discard 500,000 rows. Latency grows with the offset — page 1 is
1 ms, page 25,000 is 800 ms. It also returns duplicates/gaps if rows are inserted between
requests.

### Keyset (cursor) pagination

```sql
-- page 1
SELECT id, placed_at, total_cents
FROM orders
WHERE customer_id = 42
ORDER BY placed_at DESC, id DESC
LIMIT 20;

-- next page: pass the last row's (placed_at, id)
SELECT id, placed_at, total_cents
FROM orders
WHERE customer_id = 42
  AND (placed_at, id) < ('2026-03-11 09:14:22+00', 98765)
ORDER BY placed_at DESC, id DESC
LIMIT 20;
```

- Constant cost per page, regardless of depth.
- Requires a **stable, unique sort key** — hence `(placed_at, id)`, not `placed_at` alone
  (ties would skip or repeat rows).
- Needs an index matching the sort: `(customer_id, placed_at DESC, id DESC)`.
- Trade-off: no jumping to page N (fine for infinite scroll, not for "page 500" UIs).

### Total counts

`SELECT COUNT(*)` over millions of rows is a full scan. Options:

- **Approximate** (`reltuples` from `pg_class`, or `EXPLAIN` estimate).
- **Cached counters** maintained on insert/delete.
- **"Load more" UI** that doesn't need a total.
- For filtered counts, a supporting index-only scan may be acceptable.

```sql
-- rough estimate, no scan
SELECT reltuples::bigint AS estimate FROM pg_class WHERE relname = 'orders';
```

---

## 8. Batch Operations

```java
// ❌ N round trips
for (Order o : orders) repo.save(o);

// ✅ one batch
repo.saveAll(orders);
```

`saveAll` still does per-row inserts unless batching is configured:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
          batch_versioned_data: true
        order_inserts: true
        order_updates: true
```

For bulk loads, JDBC batching beats JPA:

```java
jdbc.batchUpdate("INSERT INTO order_items (order_id, line_no, product_id, quantity) VALUES (?,?,?,?)",
    items.stream().map(i -> new Object[]{i.orderId(), i.lineNo(), i.productId(), i.quantity()}).toList());
```

**`COPY` for large imports** — 10–100x faster than batched inserts for millions of rows:

```sql
COPY orders (id, customer_id, total_cents) FROM STDIN WITH (FORMAT csv);
```

Postgres `CopyManager` (pgjdbc) exposes it from Java. Always `ANALYZE` afterwards.

---

## 9. Statistics & Plan Stability

### Fix the information, not the plan

```sql
ANALYZE orders;                        -- refresh stats
ALTER TABLE orders ALTER COLUMN status SET STATISTICS 500;   -- more histogram buckets

-- correlated columns: let the planner know
CREATE STATISTICS orders_city_country (dependencies)
  ON city, country FROM addresses;
ANALYZE addresses;
```

Extended statistics fix the classic `city='Paris' AND country='France'` estimate that's wrong
by 100x because independence is assumed.

### Per-session tuning

```sql
SET LOCAL work_mem = '64MB';           -- for one transaction (report queries)
SET LOCAL enable_seqscan = off;        -- DIAGNOSIS ONLY
```

Never set `enable_*` flags permanently — you're overriding the optimiser globally and will
cause worse plans elsewhere.

### Plan stability across versions

- Major version upgrades can flip plans; test the top-20 queries' plans before and after.
- Plans are **data-dependent** — a plan that's good at 1M rows may be terrible at 50M.
- Save baseline plans (`EXPLAIN (FORMAT JSON)`) for critical queries and diff them in CI.

### Pooling and prepared statements

With **PgBouncer in transaction mode**, server-side prepared statements can break. Set
`prepareThreshold=0` or use `plan_cache_mode=force_generic_plan` carefully — generic plans can
be much worse for skewed data. This is a real production gotcha worth mentioning.

---

## 10. Code Examples

### 10.1 JPA: a fast list query that avoids N+1

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
        select new com.acme.dto.OrderRow(
            o.id, o.status, o.totalCents, o.placedAt, c.name,
            (select count(l) from OrderLine l where l.order = o))
        from Order o
        join o.customer c
        where o.customer.id = :customerId
          and o.placedAt < :cursor
        order by o.placedAt desc, o.id desc
        """)
    List<OrderRow> page(@Param("customerId") Long customerId,
                        @Param("cursor") Instant cursor,
                        Pageable pageable);
}
```

Note: DTO projection, no entity graph, keyset cursor, and a correlated subquery for the count
(cheap with an index on `order_line(order_id)`).

### 10.2 Keyset pagination controller

```java
@GetMapping("/orders")
List<OrderRow> page(@RequestParam Long customerId,
                    @RequestParam(required = false) Instant cursor,
                    @RequestParam(defaultValue = "20") int size) {
    Instant from = cursor == null ? Instant.now().plusSeconds(1) : cursor;
    return repo.page(customerId, from, PageRequest.of(0, size));   // never OFFSET
}
```

### 10.3 Detecting N+1 in tests

```java
@Component
class QueryCounter extends StatementInspector {
    private final ThreadLocal<Integer> count = ThreadLocal.withInitial(() -> 0);

    @Override public String inspect(String sql) { count.set(count.get() + 1); return sql; }
    public void reset() { count.set(0); }
    public int get() { return count.get(); }
}
```

```java
@Test
void orderListExecutesOneQuery() {
    counter.reset();
    orderService.listOrders(customerId);
    assertThat(counter.get()).isLessThanOrEqualTo(2);   // 1 + batch fetch
}
```

This is the only way to keep N+1 from creeping back — a plan assertion for query *count*.

### 10.4 Plan assertion for a critical query

```java
@Test
void orderSummaryPlanIsIndexOnly() {
    String plan = jdbc.queryForObject(
        "EXPLAIN (FORMAT JSON) SELECT id, total_cents FROM orders WHERE customer_id = ? AND status = ?",
        String.class, 42L, "PAID");
    assertThat(plan).contains("Index Only Scan");
}
```

### 10.5 Finding the worst queries in production

```sql
SELECT calls, mean_exec_time, total_exec_time,
       round(100 * total_exec_time / sum(total_exec_time) OVER (), 2) AS pct,
       left(query, 120) AS query
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;
```

Requires the `pg_stat_statements` extension — the single most valuable observability tool on
Postgres. Install it in every environment.

---

## 11. Common Mistakes

| Mistake | Consequence | Fix |
|---|---|---|
| Adding indexes without reading the plan | indexes that don't help, write cost | `EXPLAIN ANALYZE` first |
| Tuning on dev data | plan differs at scale | prod-like volume |
| `EXPLAIN ANALYZE` on a `DELETE` in prod | deletes rows | wrap in a transaction and roll back |
| Ignoring `loops` | misreads total cost | multiply time × loops |
| Fixing estimates with `enable_*` flags | globally worse plans | fix stats |
| `OFFSET` deep pagination | linear degradation | keyset |
| `COUNT(*)` for every page | full scans | approximate or cached |
| `SELECT *` in JPA with lazy fields | accidental N+1, fat rows | DTO projections |
| `saveAll` without batching configured | N round trips | `batch_size` + `order_inserts` |
| Per-request `SET` of session params | plan churn, pool noise | use the pool's defaults |
| `NOT IN` with NULLs | zero rows | `NOT EXISTS` |
| Believing `BitmapOr` never works | over-rewriting | read the plan |
| Assuming prepared statements are always better | generic plans on skewed data | test both cache modes |
| No `pg_stat_statements` | flying blind | install it |

---

## 12. Interview Questions & Model Answers

**Q1. A query got slow. Walk me through your process.**
> Reproduce with the real SQL and real parameters, then `EXPLAIN (ANALYZE, BUFFERS)`. Find the
> node with the largest `actual time × loops` — that's the bottleneck. Compare `rows` with
> `actual rows` to see whether the estimate or the access path is wrong. If it's an estimate
> problem I fix statistics or the predicate; if it's an access path problem I add or reshape an
> index. Then re-measure — one change at a time. Finally I verify at production volume, because
> plans are data-dependent.

**Q2. What does a large `Rows Removed by Filter` tell you?**
> The engine is reading rows only to discard them, which means the predicate isn't being used to
> seek. That's almost always a missing or mismatched index — a function on the column, a leading
> wildcard, a composite index whose prefix doesn't match, or simply no index at all.

**Q3. `rows=10` but `actual rows=50000` — what do you do?**
> The planner is working with wrong information, so any plan it picks is a guess. I check stale
> stats (`ANALYZE`), correlated columns (extended statistics), and expressions without stats
> (expression index or a rewritten predicate). I don't add an index to force a plan — I fix the
> estimate and let the planner choose.

**Q4. Why is OFFSET pagination slow?**
> The database must produce and discard every skipped row, so cost grows linearly with depth —
> page 25,000 reads 500,000 rows. Keyset pagination seeks directly using the last row's sort key,
> so every page costs the same. The trade-off is no random page jumps, which is fine for
> infinite scroll and wrong for a "go to page 500" UI.

**Q5. What is an N+1 query and how do you find it?**
> One query to load a list plus one per element to load an association — 1 + N. I find it with
> Hibernate statistics, a datasource proxy counting statements per request, or APM showing many
> identical SELECTs under one endpoint. I fix it with `join fetch`, `@EntityGraph`, or preferably
> `default_batch_fetch_size`, which turns N queries into N/batchSize without touching the
> queries, and DTO projections when I don't need entities at all.

**Q6. Nested loop or hash join — which is better?**
> Neither in the abstract. A nested loop with an indexed inner side is optimal for a small outer
> input and terrible for a large one because probes multiply. A hash join is better when both
> sides are large and no index helps, as long as the hash fits in `work_mem`. Merge joins win
> when both inputs are already sorted. So the real question is whether the planner's estimate of
> the outer side's size is correct.

**Q7. How do you keep plans stable across deploys and upgrades?**
> Treat plans as artifacts: capture `EXPLAIN (FORMAT JSON)` for critical queries, assert the
> expected access path in CI, and diff before/after a major version upgrade. Also
> `pg_stat_statements` for regressions in production, and per-column statistics targets on
> skewed columns so estimates don't drift as data grows.

**Q8. How would you load 5 million rows fast?**
> `COPY` rather than inserts — 10–100x faster. If it must go through the app, JDBC batch with a
> batch size of a few hundred, no indexes until after the load (create them afterwards
> concurrently), and `ANALYZE` at the end. Never row-by-row `save()`.

**Q9. What's the first thing you'd install on a new Postgres?**
> `pg_stat_statements`. Without it you're guessing which queries matter; with it you get calls,
> total time and rows per query, which tells you where to spend effort. Then autovacuum tuning and
> a slow-query log threshold.

---

## 13. Self-Check & Revision Card

- [ ] I can run `EXPLAIN (ANALYZE, BUFFERS)` and read `actual time × loops`.
- [ ] I can distinguish an estimate problem from an access-path problem.
- [ ] I can name the three join algorithms and when each wins.
- [ ] I can find and fix an N+1 with three different techniques.
- [ ] I can write keyset pagination and explain why `OFFSET` degrades.
- [ ] I can fix a bad estimate with `ANALYZE` and extended statistics.
- [ ] I can bulk load with batching or `COPY`.

```
┌────────────────── QUERY OPTIMISATION — 60s RECALL ──────────────────────────┐
│ LOOP: reproduce → EXPLAIN ANALYZE → biggest (time × loops) → fix ONE → repeat │
│                                                                              │
│ ESTIMATE WRONG? (rows vs actual rows 100x) → ANALYZE / extended stats /      │
│   expression index. Don't force plans.                                       │
│ ACCESS PATH WRONG? (Rows Removed by Filter, Seq Scan) → index shape.         │
│                                                                              │
│ JOINS: nested loop = small outer + indexed inner (blows up if estimate bad)  │
│        hash join = large both sides, fits work_mem                           │
│        merge join = both already sorted                                      │
│                                                                              │
│ N+1: 1 + N queries → join fetch / @EntityGraph / default_batch_fetch_size /  │
│ DTO projection. Detect with statistics or a statement counter in tests.       │
│                                                                              │
│ PAGINATION: OFFSET = O(offset). KEYSET = O(limit) forever, needs (sort, id)   │
│ + matching index. COUNT(*) is a scan → approximate or cache.                 │
│                                                                              │
│ BULK: JDBC batch or COPY, indexes after load, ANALYZE at the end.            │
│ TOOL: pg_stat_statements — install it everywhere.                            │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [02 · Indexing Deep Dive](./02-Indexing-Deep-Dive.md) · **Next:** [04 · Transactions, Locking & Isolation](./04-Transactions-Locking-and-Isolation.md)
