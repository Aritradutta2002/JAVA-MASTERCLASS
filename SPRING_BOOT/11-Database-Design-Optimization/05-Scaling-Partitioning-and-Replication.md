# 05 · Scaling, Partitioning & Replication

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★☆ ("The DB is at 100% CPU — what do you do?")
> **Prerequisite:** [04 · Transactions, Locking & Isolation](./04-Transactions-Locking-and-Isolation.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. The Escalation Ladder](#1-the-escalation-ladder)
- [2. Mental Model — Reads and Writes Scale Differently](#2-mental-model--reads-and-writes-scale-differently)
- [3. Replication](#3-replication)
- [4. Read Replicas in Practice](#4-read-replicas-in-practice)
- [5. Partitioning](#5-partitioning)
- [6. Sharding](#6-sharding)
- [7. Connection Pooling & PgBouncer](#7-connection-pooling--pgbouncer)
- [8. Failover & Operations](#8-failover--operations)
- [9. When to Stop: Not Everything Is a Scaling Problem](#9-when-to-stop-not-everything-is-a-scaling-problem)
- [10. Code Examples](#10-code-examples)
- [11. Common Mistakes](#11-common-mistakes)
- [12. Interview Questions & Model Answers](#12-interview-questions--model-answers)
- [13. Self-Check & Revision Card](#13-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> Scale in this order — cheapest first, and **only after measuring**:
> **1)** fix queries and indexes → **2)** cache → **3)** pool connections (PgBouncer) →
> **4)** read replicas → **5)** table partitioning → **6)** sharding → **7)** a different
> datastore for a specific access pattern.
>
> **Reads** scale horizontally with replicas; **writes** do not. A single Postgres primary
> takes ~5–20k simple writes/s; beyond that you need partitioning (for maintenance/archival) or
> sharding (for throughput), which costs you cross-shard joins, transactions, and unique
> constraints.
>
> **Replication is asynchronous by default** — replicas lag, so reads can be stale and failover
> can lose recent commits. `synchronous_commit = remote_apply` fixes durability at the cost of
> write latency.
>
> **Partitioning** (one logical table split into physical ones, same server) is mostly an
> **operational** tool: drop a partition instead of `DELETE`, vacuum smaller chunks, keep hot
> data in cache. **Sharding** (across servers) is a **throughput** tool and a permanent
> architectural cost.

---

## 1. The Escalation Ladder

```
   ┌───────────────────────────────────────────────────────────────────┐
   │  0. MEASURE   pg_stat_statements, wait events, CPU, IO, locks      │
   │                                                                    │
   │  1. QUERY + INDEX        ← 80% of "the DB is slow" cases end here  │
   │  2. SCHEMA (denormalise the hot read, fix the N+1)                 │
   │  3. CACHE (Redis, CDN)   ← biggest win per unit of effort          │
   │  4. CONNECTION POOL (PgBouncer) ← often the real "CPU at 100%"     │
   │  5. VERTICAL (bigger box)  ← cheap, buys years, no code changes    │
   │  6. READ REPLICAS                                                │
   │  7. PARTITION (maintenance, retention, locality)                   │
   │  8. SHARD (write throughput) ← last resort, permanent cost         │
   │  9. SPECIALISED STORE (search, time-series, OLAP, KV)              │
   └───────────────────────────────────────────────────────────────────┘
```

**The rule to state in an interview:** "Before I change the architecture I want to know whether
the bottleneck is CPU, IO, locks, or connection count — because each has a different fix, and
only one of them justifies sharding."

### Diagnosing which one

```sql
-- top queries by total time
SELECT calls, mean_exec_time, total_exec_time, left(query, 100)
FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 10;

-- what are sessions waiting on?
SELECT wait_event_type, wait_event, count(*)
FROM pg_stat_activity WHERE state = 'active' GROUP BY 1,2 ORDER BY 3 DESC;

-- connection count vs max
SELECT count(*), current_setting('max_connections') FROM pg_stat_activity;
```

| Symptom | Real cause | Fix rung |
|---|---|---|
| High CPU, one query dominant | bad plan | 1 |
| High CPU spread evenly, small queries | too many round trips / N+1 | 1, 3 |
| `wait_event = IO` | working set > cache | 3, 5 |
| `LWLock`/`Lock` waits | long transactions, contention | schema + tx length |
| `ClientRead` waits | app is slow between queries | 4 (pool), app fix |
| Many connections, low CPU | connection overhead | 4 |
| Write WAL throughput saturated | write volume | 7, 8 |

---

## 2. Mental Model — Reads and Writes Scale Differently

```
   READS                                      WRITES
   ┌──────┐                                   ┌──────┐
   │ read │──► replica 1                       │write │──► PRIMARY (one writer)
   │ read │──► replica 2                       │write │        │
   │ read │──► replica 3                       │write │        └─► WAL ──► replicas
   └──────┘                                   └──────┘
   horizontal, cheap, stale-tolerant          vertical until you SHARD
```

- A replica **replays the primary's WAL**. It's read-only and typically milliseconds behind.
- Adding a replica multiplies read capacity and adds one more thing that can lag.
- **Writes hit one primary.** No amount of replicas helps write throughput.
- Therefore: **the first scaling question is always "what's the read:write ratio?"**

Typical ratios: social feed 100:1, analytics 1000:1, payments 3:1, IoT ingest 1:50. A
write-heavy system hits the sharding wall far sooner than a read-heavy one.

**Interview line:** "Replicas buy read throughput and failover, not write throughput — so if
the bottleneck is writes, replicas are the wrong tool."

---

## 3. Replication

### 3.1 Physical (streaming) vs logical

| | Physical / streaming | Logical |
|---|---|---|
| What's replicated | WAL blocks (byte-identical) | row changes (decoded) |
| Replica is | identical cluster, read-only | independent DB, writable, different version |
| Granularity | whole cluster | per table/schema |
| Use | HA, read scaling | migrations, selective replication, CDC |

### 3.2 Synchronous vs asynchronous

```conf
# primary
synchronous_standby_names = 'replica1'          # wait for replica1
synchronous_commit = on                          # on | remote_write | remote_apply | off
```

| Setting | Waits for | Durability | Latency |
|---|---|---|---|
| `off` | nothing (async) | loses recent commits on failover | lowest |
| `local` | local WAL flush | survives primary crash, not primary loss | low |
| `on` (default) | local + standby **write** | survives primary loss | + network |
| `remote_write` | standby OS buffer | slightly better | + network |
| `remote_apply` | standby **applied** (visible) | reads on the replica see the commit | highest |

**The trade-off:** synchronous replication converts a replica outage into a **write outage**
(unless you use `ANY 1 (r1, r2)` quorum). Most teams run async and accept a small data-loss
window (`recovery_target` / RPO of a second or two), or use sync with a quorum to keep
availability.

### 3.3 Replication lag

Causes: a long-running query on the replica (it can't apply while reading — `hot_standby_feedback`
trades bloat for query stability), network throughput, single-threaded apply, huge transactions.

```sql
-- on the primary: how far behind is each replica?
SELECT client_addr, state, sent_lsn, write_lsn, flush_lsn, replay_lsn,
       (sent_lsn - replay_lsn) AS lag_bytes
FROM pg_stat_replication;
```

**Monitoring `lag_bytes` and `replay_lsn` is table stakes.** Alert when lag exceeds the
read-your-writes budget.

### 3.4 Failover

- **Manual** — promote a replica (`pg_ctl promote`), repoint the app. Minutes of downtime.
- **Automated** — Patroni / repmgr / cloud-managed (RDS Multi-AZ, Aurora). Seconds.
- **Split-brain risk** — a partitioned primary keeps accepting writes while a new one is
  promoted. Mitigation: fencing (STONITH), `pg_rewind`, and a consensus store for leadership
  (which is exactly why Patroni uses etcd/Consul).

---

## 4. Read Replicas in Practice

### 4.1 Routing reads

```java
public abstract class RoutingDataSource extends AbstractRoutingDataSource {
    @Override protected Object determineCurrentLookupKey() {
        return ReadOnlyContext.isReadOnly() ? "replica" : "primary";
    }
}
```

Or with `AbstractRoutingDataSource` + `@Transactional(readOnly = true)` proxy, or Spring's
`LazyConnectionDataSourceProxy` so the routing decision happens after the transaction's
read-only flag is known.

### 4.2 The four hazards

| Hazard | Example | Mitigation |
|---|---|---|
| **Stale read** | user updates then reloads, sees the old value | route that user's reads to primary for N seconds |
| **Replica lag spike** | a batch job on the replica | monitor lag; fall back to primary above a threshold |
| **Monotonic reads violation** | user sees a value, refreshes, it's gone (different replica) | sticky to one replica per session |
| **Read-after-write in a transaction** | write then read inside one tx must see the write | transactions always use the primary |

### 4.3 Read-your-writes, concretely

```java
@Component
class PrimaryStickiness {
    private final Cache<Long, Instant> recentWrites = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(10)).maximumSize(100_000).build();

    void recordWrite(Long userId) { recentWrites.put(userId, Instant.now()); }

    boolean mustUsePrimary(Long userId) { return recentWrites.getIfPresent(userId) != null; }
}
```

The same problem the cache chapter calls "read-your-writes" — the replica is just another
stale copy.

---

## 5. Partitioning

Splitting one logical table into physical child tables, **on the same server**. The parent is
empty and routes by the partition key.

### 5.1 Strategies

| Strategy | Key | Use |
|---|---|---|
| **Range** | `created_at` | time-series, retention by dropping old partitions |
| **List** | `region` | a small, fixed set of values (tenants, regions) |
| **Hash** | `user_id` | even distribution, no natural range |

```sql
CREATE TABLE events (
  id          bigint NOT NULL,
  occurred_at timestamptz NOT NULL,
  payload     jsonb
) PARTITION BY RANGE (occurred_at);

CREATE TABLE events_2026_03 PARTITION OF events
  FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
```

### 5.2 What partitioning actually buys

- **Retention in O(1)** — `DROP TABLE events_2025_01` instead of a `DELETE` that bloats and
  needs vacuum.
- **Smaller indexes** — each partition has its own B-tree, so they fit in memory and vacuum
  completes fast.
- **Cheaper maintenance** — `VACUUM`, `REINDEX`, `ANALYZE` per partition.
- **Locality** — hot partitions can live on faster storage.
- **Pruning** — `WHERE occurred_at >= '2026-03-01'` scans only matching partitions.

### 5.3 What it does NOT buy

- **No extra write throughput** on one server — same disk, same CPU, same WAL.
- **No cross-partition optimisation for you** — a query without the partition key scans every
  partition (still usually fine, but plan accordingly).
- **No global unique constraints** — a `UNIQUE` must include the partition key.
- **No foreign keys** from a partitioned table to another table (PG < 12 restrictions; PG 12+
  allows FK from partitioned to non-partitioned but not the reverse in all cases).

### 5.4 Partition pruning

```sql
EXPLAIN SELECT * FROM events WHERE occurred_at >= '2026-03-15';
-- Append
--   -> Seq Scan on events_2026_03   (only this partition)
--   -> Seq Scan on events_2026_04
```

Pruning requires the predicate on the **partition key**, and it must be a constant or a
stable expression. `WHERE occurred_at::date = current_date` may defeat pruning because of the
cast — compare column-to-constant, not function(column)-to-value.

### 5.5 Automating partitions

Create future partitions ahead of time (a scheduled job or `pg_partman`). Missing the next
month's partition means **inserts fail** at midnight on the 1st — a classic incident.

---

## 6. Sharding

Splitting data **across servers**. This is a permanent architectural commitment.

### 6.1 Choosing a shard key

| Key | Pros | Cons |
|---|---|---|
| `user_id` | natural, most queries are per-user | cross-user queries fan out |
| `tenant_id` | isolation, easy routing | one big tenant = one hot shard |
| `order_id` hash | even distribution | per-customer queries fan out to all shards |
| geography | data residency, latency | uneven size, cross-region joins |

**The test:** does your **most frequent, most latency-sensitive query** include the shard key?
If not, you'll be fanning out on every request.

### 6.2 Strategies

- **Application-level routing** — the app computes the shard. Simple, no middleware, but every
  service must know the scheme.
- **Middleware** — Citus (Postgres extension), Vitess (MySQL), ProxySQL. Handles routing,
  aggregation, resharding.
- **Directory-based** — a lookup table maps key → shard. Flexible, but the directory becomes a
  dependency and a single point of contention.

### 6.3 What you give up

- **Cross-shard joins** — gone; you join in the app or duplicate data.
- **Cross-shard transactions** — 2PC or sagas
  ([Topic 08 ch 06](../08-Microservices-with-Spring-Boot/06-Distributed-Data-Management-and-Saga-Patterns.md)).
- **Global unique constraints** — need id allocation (Snowflake, ULID) or a directory.
- **Cross-shard aggregates** — `COUNT`, `SUM`, `ORDER BY ... LIMIT` need scatter-gather.
- **Operational complexity** — N times the backups, monitoring, migrations, failover drills.

### 6.4 Resharding

Adding shards means moving data. Options:

- **Consistent hashing / virtual nodes** — only 1/N of keys move.
- **Logical shards mapped to physical** — start with 256 logical shards on 4 servers; moving
  to 8 servers moves whole logical shards, no rehashing. **This is the technique to mention.**
- **Double-write + backfill + cut over** — the safe path for a live migration.

```java
// 256 logical shards, mapped to N physical servers
int logical  = Math.floorMod(hash(userId), 256);
int physical = ShardMap.serverFor(logical);     // table-driven, changeable
```

### 6.5 Don't shard first

Most systems that "need sharding" actually need: better indexes, a cache, a pooled connection,
or a bigger instance. State the ladder before you state the shard plan — that's the senior
answer.

---

## 7. Connection Pooling & PgBouncer

### 7.1 Why connections are expensive

Postgres forks a **process** per connection (~5–10 MB, plus per-connection caches). 500
connections = several GB of RAM and measurable context-switching. `max_connections` of 500 is
not a throughput feature; it's a foot-gun.

```
   200 app instances × pool size 20 = 4,000 connections → primary dies
   with PgBouncer: 200 × 20 → 200 server connections
```

### 7.2 PgBouncer modes

| Mode | Behaviour | Compatible with |
|---|---|---|
| **session** | one server connection per client session | everything (safe default) |
| **transaction** | server connection held only for the transaction | most apps; **breaks** session state |
| **statement** | per statement | nothing with multi-statement transactions |

**Transaction mode** is the big win (hundreds of clients → tens of server connections) but
breaks: prepared statements (unless `max_prepared_statements` is set in PgBouncer 1.21+),
`SET` session variables, advisory locks, and `LISTEN/NOTIFY`.

JDBC settings for transaction mode:

```properties
# disable server-side prepared statements
prepareThreshold=0
# or keep them, but configure pgBouncer max_prepared_statements
```

### 7.3 Sizing

```
server connections ≈ cores × 2..4        (plus some for maintenance)
app pool size      ≈ (server connections × 2) / instances
```

An 8-core primary wants ~16–32 active connections. More than that increases contention on
latches without adding throughput. **Smaller pools are usually faster** — a counter-intuitive
fact worth stating.

Also: set `pool.max-wait` short and `max-lifetime` below the DB/proxy idle timeout to avoid
using stale connections.

---

## 8. Failover & Operations

### 8.1 Backups

- **Logical** (`pg_dump`) — portable, slow to restore, good for small DBs and migrations.
- **Physical** (`pg_basebackup` / WAL archiving) — full cluster, fast restore, PITR.
- **PITR** — base backup + WAL archive → restore to any instant. **The only protection against
  "someone ran a bad DELETE at 14:03".**
- **Test restores.** A backup you've never restored is a hypothesis.

### 8.2 Zero-downtime migrations (recap)

```
expand (add nullable) → dual-write → backfill in batches → switch reads → contract
```

Plus `CREATE INDEX CONCURRENTLY` and `SET lock_timeout` on every DDL statement.

### 8.3 The operational checklist

- [ ] Monitoring: replication lag, connection count, long transactions, deadlocks, cache hit
      ratio, disk headroom, WAL generation rate.
- [ ] Alerts: lag > budget, `idle in transaction` > 30 s, disk > 80%, autovacuum lag.
- [ ] Failover drill run at least quarterly — an untested failover is a hypothesis too.
- [ ] PITR restore drill.
- [ ] `pg_stat_statements` and slow-query logging enabled.
- [ ] Connection pool limits agreed with the DB's capacity.

---

## 9. When to Stop: Not Everything Is a Scaling Problem

| Symptom | Might not be a scaling problem |
|---|---|
| "The DB is at 100% CPU" | one bad query (rung 1) |
| "Pages are slow" | N+1 in the ORM (rung 1) |
| "We need sharding" | no index on the hot query (rung 1) |
| "We need a bigger instance" | connection storm from a pool of 50 per pod (rung 4) |
| "We need a NoSQL store" | you need search, not a KV store (rung 9, but pick the right one) |
| "Reporting kills the DB" | run reports on a replica or a warehouse (rung 6/9) |

**The senior move:** name the measurement that would justify each rung. "I'd shard if write
throughput exceeded what a single primary can do after indexing, caching and pooling — and I'd
show the WAL rate and the write latency curve to prove it."

---

## 10. Code Examples

### 10.1 Read/write splitting with a routing datasource

```java
@Configuration
class DataSourceConfig {
    @Bean @Primary
    DataSource routing(DataSource primary, DataSource replica) {
        var ds = new AbstractRoutingDataSource() {
            @Override protected Object determineCurrentLookupKey() {
                return ReadOnlyContext.isReadOnly() ? "replica" : "primary";
            }
        };
        ds.setTargetDataSources(Map.of("primary", primary, "replica", replica));
        ds.setDefaultTargetDataSource(primary);
        ds.afterPropertiesSet();
        return ds;
    }

    @Bean
    LazyConnectionDataSourceProxy lazy(DataSource routing) {
        return new LazyConnectionDataSourceProxy(routing);   // routing decided at tx start
    }
}
```

```java
@Aspect @Component
class ReadOnlyRoutingAspect {
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    Object route(ProceedingJoinPoint pjp) throws Throwable {
        var tx = ((MethodSignature) pjp.getSignature()).getMethod()
                   .getAnnotation(Transactional.class);
        boolean readOnly = tx != null && tx.readOnly();
        ReadOnlyContext.set(readOnly);
        try { return pjp.proceed(); } finally { ReadOnlyContext.clear(); }
    }
}
```

**Caveat:** a `readOnly` transaction that writes will fail on the replica. Only mark it read-only
when it really is.

### 10.2 Partition maintenance job

```java
@Component
class PartitionMaintenance {
    @Scheduled(cron = "0 5 0 * * *")            // daily
    void ensureNextMonth() {
        var next = YearMonth.now().plusMonths(1);
        String name = "events_" + next;
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS %s PARTITION OF events
            FOR VALUES FROM ('%s') TO ('%s')
            """.formatted(name, next.atDay(1), next.plusMonths(1).atDay(1)));
        log.info("ensured partition {}", name);
    }

    @Scheduled(cron = "0 30 2 * * *")
    void dropExpired() {
        jdbc.queryForList("""
            SELECT inhrelid::regclass::text AS partition
            FROM pg_inherits WHERE inhparent = 'events'::regclass
            """, String.class)
           .stream()
           .filter(p -> p.matches("events_\\d{4}_\\d{2}"))
           .filter(p -> parse(p).isBefore(YearMonth.now().minusMonths(12)))
           .forEach(p -> jdbc.execute("DROP TABLE IF EXISTS " + p));
    }

    private YearMonth parse(String table) {
        var parts = table.replace("events_", "").split("_");
        return YearMonth.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
```

### 10.3 Setting statement and lock timeouts globally

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      connection-timeout: 2000
      max-lifetime: 1800000           # < DB/proxy idle timeout
      leak-detection-threshold: 20000 # log connections held > 20s
    hikari-data-source-properties:
      options: "-c statement_timeout=5000 -c lock_timeout=2000 -c idle_in_transaction_session_timeout=10000"
```

`idle_in_transaction_session_timeout` is the single best protection against the
bloat-for-days failure mode.

### 10.4 Shard routing behind an interface

```java
public interface OrderRepository {
    Order save(Order o);
    Optional<Order> find(long orderId);
}

@Component
class ShardedOrderRepository implements OrderRepository {
    private final Map<Integer, JdbcTemplate> shards;

    public Optional<Order> find(long orderId) {
        return shards.get(ShardMap.forKey(orderId)).query(
            "SELECT * FROM orders WHERE id = ?", mapper, orderId).stream().findFirst();
    }

    public Order save(Order o) {
        shards.get(ShardMap.forKey(o.getId())).update("INSERT INTO orders ...", ...);
        return o;
    }
}
```

Keep the interface clean so the rest of the app doesn't know about shards; the day you need
to cross shards, that interface is where the fan-out lives.

---

## 11. Common Mistakes

| Mistake | Consequence | Fix |
|---|---|---|
| Sharding before indexing/caching | complexity with no benefit | follow the ladder |
| Adding a replica to fix slow writes | no effect | replicas don't scale writes |
| Async replica used for read-your-writes | user sees stale data | stick to primary after writes |
| Sync replication with one standby | standby outage = write outage | sync quorum `ANY 1 (...)` or async |
| Partitioning for throughput | no gain on one server | shard, or fix queries |
| Missing next partition | inserts fail at midnight | automated partition creation |
| Partition key not in the predicate | scans every partition | include it, or a default partition |
| Pool size 50 × 50 pods | connection storm | PgBouncer, small pools |
| PgBouncer transaction mode + prepared statements | errors after a while | `prepareThreshold=0` or configure it |
| No `idle_in_transaction_session_timeout` | bloat, locks for hours | set it |
| No replication lag monitoring | silent stale reads | alert on `replay_lsn` lag |
| Untested failover | 4-hour outage | quarterly drills |
| No PITR | a bad `DELETE` is unrecoverable | WAL archiving + restore drill |
| Unbounded `max_connections` | OOM by fork storm | cap it, pool in front |

---

## 12. Interview Questions & Model Answers

**Q1. The database is at 100% CPU. What do you do?**
> First find out what it's doing: `pg_stat_statements` for the top queries by total time, and
> `pg_stat_activity` for wait events — is it CPU, IO, or locks? In most cases one query dominates,
> and the fix is an index or a rewrite, not hardware. If it's spread across many fast queries, it's
> usually N+1 or too many round trips, which means caching or batching. Only after those would I
> consider a bigger instance, then replicas, then sharding.

**Q2. When would you add a read replica?**
> When reads dominate and the primary's CPU is spent on read work. It multiplies read capacity and
> gives you a failover target. I'd pair it with lag monitoring and explicit handling of the stale-read
> windows — read-your-writes routes to the primary for a few seconds after a write, and reporting
> traffic goes to a dedicated replica so it can't affect latency for users.

**Q3. Replication lag is growing. What are the causes?**
> Long queries on the replica blocking replay, insufficient network bandwidth, huge transactions on
> the primary, or single-threaded apply. I check `pg_stat_replication` for `replay_lsn` lag, look for
> long-running read queries on the standby, and check whether `hot_standby_feedback` is holding back
> vacuum. Fixes: cancel the long query, raise `max_standby_streaming_delay`, use logical replication
> for selective replay, or parallelise apply.

**Q4. Partitioning or sharding?**
> Partitioning splits a table into physical parts on the same server — it's an operational tool for
> retention, smaller indexes and faster vacuum, and it does not add write throughput. Sharding splits
> data across servers — that's the throughput tool, and it costs cross-shard joins, transactions and
> global uniqueness. I'd partition first because it's reversible and often solves the actual problem,
> which is usually maintenance pain, not write volume.

**Q5. How do you choose a shard key?**
> Start from the most frequent, most latency-sensitive query and pick the key that appears in it, so
> requests stay on one shard. Then check distribution — no hot tenant — and check whether cross-shard
> operations are acceptable. For a multi-tenant SaaS that's usually tenant id; for consumer orders
> it's user id. And I'd use logical shards mapped to physical servers so resharding moves whole
> shards without rehashing.

**Q6. How many connections should Postgres have?**
> Far fewer than people think — roughly two to four times the core count of active work. Postgres
> forks a process per connection, so thousands of connections waste RAM and add context switching
> without adding throughput. I put PgBouncer in front in transaction mode so hundreds of app
> connections map to tens of server connections, and I keep the app pool small and short-lived.

**Q7. Synchronous or asynchronous replication?**
> Async by default: lowest write latency, but a failover can lose the last few commits. Sync
> guarantees the standby has the WAL before the commit returns, at the cost of write latency — and
> with a single standby, its outage becomes a write outage. So for durability I'd use sync with a
> quorum (`ANY 1 (r1, r2)`) so one replica failing doesn't stop writes, and I'd always state the RPO
> the business is signing up for.

**Q8. How do you handle a schema migration on a 1-billion-row table?**
> Expand/contract: add nullable columns (metadata-only), backfill in small batches with pauses so
> autovacuum can keep up, add indexes `CONCURRENTLY`, switch the application writes, then add
> constraints `NOT VALID` and `VALIDATE` them, and finally drop the old columns. Every DDL runs with
> `lock_timeout` so a blocked migration fails fast instead of queueing all traffic behind it.

**Q9. What's your disaster recovery plan?**
> PITR: a base backup plus continuous WAL archiving to object storage, with defined RPO/RTO, plus
> regular restore drills — including a "restore to 14:02 yesterday" test after a simulated bad
> delete. Replicas give HA, not backups: a `DROP TABLE` replicates instantly.

---

## 13. Self-Check & Revision Card

- [ ] I can recite the escalation ladder in order.
- [ ] I can diagnose CPU vs IO vs lock vs connection bottlenecks from `pg_stat_*`.
- [ ] I can explain why replicas scale reads but not writes.
- [ ] I can explain the sync/async replication trade-off and RPO.
- [ ] I can name the read-replica hazards and their mitigations.
- [ ] I can state what partitioning does and does not buy.
- [ ] I can choose a shard key and explain logical shards.
- [ ] I can size a connection pool and explain PgBouncer transaction mode.
- [ ] I can describe PITR and why replicas aren't backups.

```
┌──────────────── DB SCALING — 60s RECALL ─────────────────────────────────────┐
│ LADDER: measure → query/index → cache → PgBouncer → bigger box → replicas →  │
│         partition → SHARD → specialised store. Cheapest rung that works.     │
│                                                                              │
│ READS scale out (replicas). WRITES do not (one primary).                     │
│ Replica hazards: stale read · lag spike · monotonic reads · tx must be       │
│ primary. Fix: stick to primary for N s after a write.                        │
│                                                                              │
│ REPLICATION: async = fast, can lose last commits (RPO). sync = durable,      │
│ single standby outage = write outage ⇒ use quorum ANY 1 (...).               │
│                                                                              │
│ PARTITIONING = operational (DROP partition, small indexes, vacuum).          │
│   NOT throughput. Pruning needs the partition key in the predicate.          │
│ SHARDING = throughput, permanent cost: no cross-shard joins/tx/unique.       │
│   Pick the key from the hottest query. Use 256 logical shards.               │
│                                                                              │
│ CONNECTIONS: ~2–4× cores. PgBouncer transaction mode. SMALLER POOLS FASTER.  │
│                                                                              │
│ Replicas ≠ backups. Only PITR survives a bad DELETE. Test the restore.       │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [04 · Transactions, Locking & Isolation](./04-Transactions-Locking-and-Isolation.md) · **Up:** [README](./README.md)
