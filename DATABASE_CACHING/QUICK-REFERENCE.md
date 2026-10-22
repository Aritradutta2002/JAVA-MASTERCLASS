# Database & Caching Quick Reference

## 🔥 Most Asked Topics & Deep-Dive Chapters

| Topic | Frequency | Key Points | Masterclass Chapter |
|---|:---:|---|---|
| **Storage & WAL** | ⭐⭐⭐⭐⭐ | Pages, Slot Directory, Clock-Sweep, ARIES Recovery | [01 · Storage Internals](./01-SQL-Databases/01-Relational-Engine-and-Storage-Internals.md) |
| **B+ Tree Indexing** | ⭐⭐⭐⭐⭐ | Splits, Leftmost Prefix, Covering `INCLUDE`, GIN/BRIN | [02 · Indexing Architecture](./01-SQL-Databases/02-Indexing-Architecture-and-B-Trees.md) |
| **Advanced SQL** | ⭐⭐⭐⭐⭐ | Window Frames (`ROWS` vs `RANGE`), Recursive CTEs, LATERAL | [03 · Advanced SQL & CTEs](./01-SQL-Databases/03-Advanced-SQL-Window-Functions-and-CTEs.md) |
| **Query Plans** | ⭐⭐⭐⭐⭐ | CBO models, Hash vs Merge vs Nested Loop, EXPLAIN BUFFERS | [04 · Query Planner & EXPLAIN](./01-SQL-Databases/04-Query-Planner-Optimizer-and-Explain.md) |
| **ACID & MVCC** | ⭐⭐⭐⭐⭐ | 6 Anomalies, Snapshot Isolation, Gap Locks, Deadlocks | [05 · Transactions & MVCC](./01-SQL-Databases/05-Transactions-ACID-MVCC-and-Locking.md) |
| **Schema Design** | ⭐⭐⭐⭐ | 1NF-BCNF, UUIDv7 vs BIGINT, Expand-Contract Migrations | [06 · Schema & Migrations](./01-SQL-Databases/06-Schema-Design-Modeling-and-Migrations.md) |
| **Replication & Scale**| ⭐⭐⭐⭐⭐ | WAL Streaming, HikariCP sizing formula, PgBouncer | [07 · Replication & Scaling](./01-SQL-Databases/07-Replication-High-Availability-and-Scaling.md) |
| **Senior Q&A** | ⭐⭐⭐⭐⭐ | 40+ Senior/Principal Q&A, Naive → Trap → Masterclass | [08 · Senior Interview Q&A](./01-SQL-Databases/08-Senior-Backend-Interview-QA-and-Scenarios.md) |
| **Whiteboard Drills**| ⭐⭐⭐⭐⭐ | 10 Hard Drills: Gaps & Islands, Sessionization, Hierarchies | [09 · Complex SQL Drills](./01-SQL-Databases/09-Complex-SQL-Interview-Drills.md) |
| **NoSQL Types** | ⭐⭐⭐⭐ | Document, key-value, column-family, graph | [NoSQL Databases](./02-NoSQL-Databases/) |
| **Caching Patterns** | ⭐⭐⭐⭐⭐ | Cache-aside, write-through, write-behind, TTL | [Caching Strategies](./04-Caching-Strategies/) |
| **Redis** | ⭐⭐⭐⭐ | Data structures, persistence, pub/sub, use cases | [Caching Strategies](./04-Caching-Strategies/) |

---

## 📊 Comparison Tables

### SQL vs NoSQL

| Aspect | SQL | NoSQL |
|--------|-----|-------|
| Schema | Fixed schema (relational invariants) | Flexible schema (application-validated) |
| Scaling | Vertical scale-up, read replicas, partitioning | Horizontal scale-out (sharding by partition key) |
| Transactions | Full ACID guaranteed | BASE (Basically Available, Soft-state, Eventual consistency) |
| Joins | Fully supported in engine | Limited, client-side, or denormalized |
| Use Case | Core transactional systems, ledgers, complex relations | High throughput, flexible documents, timeseries |

### NoSQL Database Types

| Type | Example | Data Model | Use Case |
|------|---------|------------|----------|
| Document | MongoDB | JSON documents | Flexible schemas, nested data |
| Key-Value | Redis, DynamoDB | Key-value pairs | Caching, session storage |
| Column-Family | Cassandra | Wide columns | Time-series, high write throughput |
| Graph | Neo4j | Nodes and edges | Social networks, recommendations |

### Transaction Isolation Levels & Real Anomalies

| Level | Dirty Read | Non-Repeatable Read | Phantom Read | Write Skew | Lost Update |
|---|:---:|:---:|:---:|:---:|:---:|
| **Read Uncommitted** | ❌ Allowed | ❌ Allowed | ❌ Allowed | ❌ Allowed | ❌ Allowed |
| **Read Committed** | ✅ Prevented | ❌ Allowed | ❌ Allowed | ❌ Allowed | ❌ Allowed |
| **Repeatable Read (ANSI)** | ✅ Prevented | ✅ Prevented | ❌ Allowed | ❌ Allowed | ❌ Allowed |
| **PostgreSQL Repeatable Read**| ✅ Prevented | ✅ Prevented | ✅ Prevented (SI) | ❌ Allowed | ✅ Prevented |
| **MySQL InnoDB Repeatable Read**| ✅ Prevented | ✅ Prevented | ✅ Prevented (Gap Locks) | ❌ Allowed | ✅ Prevented |
| **Serializable (SSI)** | ✅ Prevented | ✅ Prevented | ✅ Prevented | ✅ Prevented | ✅ Prevented |

### Caching Patterns

| Pattern | Write Flow | Read Flow | Use Case |
|---------|-----------|-----------|----------|
| **Cache-Aside** | Write to DB only | Read cache, on miss load from DB & populate cache | Most common default |
| **Write-Through** | Write to cache and DB synchronously | Read from cache | Read-heavy, strong cache consistency |
| **Write-Behind** | Write to cache, async flush to DB | Read from cache | Write-heavy, risk of data loss on crash |
| **Read-Through** | Write to DB | Cache library loads from DB transparently | Clean separation |

---

## 🎯 Checklist

### SQL Fundamentals & Advanced Analytics
- [ ] Write complex SELECT queries and master the 12-step logical execution order
- [ ] Use INNER, LEFT, RIGHT, and FULL OUTER joins
- [ ] Understand GROUP BY, HAVING, and multi-dimensional GROUPING SETS/ROLLUP/CUBE
- [ ] Use window functions with explicit frames (`ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`)
- [ ] Write Recursive CTEs with path tracking for organizational hierarchies
- [ ] Implement Keyset / Cursor pagination for multi-million row datasets

### Indexing Physics
- [ ] Master B+ tree branching factor and tree height math
- [ ] Optimize composite index ordering: Equality columns first, range columns last
- [ ] Create covering indexes with `INCLUDE` to enable Index-Only Scans
- [ ] Know when NOT to index: low selectivity, small tables, high write churn
- [ ] Leverage specialized indexes: GIN (JSONB/Arrays), BRIN (large append-only logs)
- [ ] Monitor and eliminate unused or duplicate indexes

### Transactions & Concurrency
- [ ] Understand ACID physical mechanics (WAL, Undo logs, ARIES recovery)
- [ ] Identify all 6 concurrency anomalies (including Write Skew and Lost Updates)
- [ ] Contrast PostgreSQL Heap MVCC bloat with MySQL InnoDB Undo Log chains
- [ ] Handle deadlocks using deterministic global lock ordering (`ORDER BY id ASC`)
- [ ] Build lock-free concurrent queues using `SELECT ... FOR UPDATE SKIP LOCKED`
- [ ] Guard against PostgreSQL Transaction ID Wraparound emergencies

### Query Optimization
- [ ] Read and analyze `EXPLAIN (ANALYZE, BUFFERS)` execution plans
- [ ] Contrast the 3 physical join algorithms: Nested Loop, Hash Join, Sort-Merge Join
- [ ] Identify and fix on-disk memory spills (`Sort Method: external merge Disk`)
- [ ] Solve the N+1 query problem using JOIN FETCH or batch fetching
- [ ] Collect extended statistics (`CREATE STATISTICS`) for correlated columns

### NoSQL & Distributed Scale
- [ ] Choose the right NoSQL engine (Document vs Key-Value vs Column-Family vs Graph)
- [ ] Apply the CAP theorem and PACELC trade-offs to system architectures
- [ ] Size HikariCP connection pools using the formula: `(Cores * 2) + Spindles`
- [ ] Deploy PgBouncer in transaction pooling mode to multiplex database connections
- [ ] Implement Declarative Table Partitioning for massive time-series tables

### Caching Strategies
- [ ] Implement the Cache-Aside pattern with appropriate TTLs
- [ ] Prevent Cache Stampede, Cache Penetration, and Cache Breakdown
- [ ] Choose optimal eviction policies (LRU, LFU, FIFO)
- [ ] Use appropriate Redis data structures (Strings, Hashes, Lists, Sets, Sorted Sets)
- [ ] Monitor cache hit ratios and memory fragmentation

---

## ⚡ Code Snippets

### SQL Join Examples
```sql
-- INNER JOIN: Only matching rows
SELECT o.id, o.total, c.name
FROM orders o
INNER JOIN customers c ON o.customer_id = c.id;

-- LEFT JOIN: All from left, matching from right
SELECT c.name, o.id
FROM customers c
LEFT JOIN orders o ON c.id = o.customer_id;
```

### Keyset Pagination (Cursor over Timestamps & IDs)
```sql
SELECT id, title, created_at
FROM articles
WHERE (created_at, id) < ('2026-03-15 10:00:00+00', 49201)
ORDER BY created_at DESC, id DESC
LIMIT 20;
-- Requires: CREATE INDEX idx_keyset ON articles (created_at DESC, id DESC);
```

### Creating Production Indexes
```sql
-- Standard B-Tree index
CREATE INDEX idx_email ON users(email);

-- Composite index (equality first, range last)
CREATE INDEX idx_tenant_status_date ON orders(tenant_id, status, created_at);

-- Covering index with INCLUDE (Index-Only Scan)
CREATE INDEX idx_user_orders ON orders(user_id) INCLUDE (order_date, total);

-- Partial index for high-cardinality filtering
CREATE INDEX idx_unprocessed_jobs ON jobs(id) WHERE status = 'PENDING';
```

### High-Throughput Task Queue (`SKIP LOCKED`)
```sql
SELECT id, payload 
FROM task_queue 
WHERE status = 'PENDING' 
ORDER BY id 
LIMIT 1 
FOR UPDATE SKIP LOCKED;
```

### Transaction Example
```sql
BEGIN TRANSACTION;

UPDATE accounts SET balance = balance - 100 WHERE id = 1;
UPDATE accounts SET balance = balance + 100 WHERE id = 2;

COMMIT;
-- Or ROLLBACK if error
```

### Active Blocking Lock Detection (PostgreSQL)
```sql
SELECT 
    blocked_locks.pid     AS blocked_pid,
    blocking_locks.pid    AS blocking_pid,
    blocked_activity.query AS blocked_query,
    blocking_activity.query AS blocking_query
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks blocking_locks 
    ON blocking_locks.locktype = blocked_locks.locktype
    AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
    AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
    AND blocking_locks.pid != blocked_locks.pid
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocked_locks.pid
WHERE NOT blocked_locks.granted;
```

### Spring Data JPA with Caching
```java
@Service
public class UserService {
    
    @Cacheable(value = "users", key = "#id")
    public User findById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found"));
    }
    
    @CachePut(value = "users", key = "#user.id")
    public User update(User user) {
        return userRepository.save(user);
    }
    
    @CacheEvict(value = "users", key = "#id")
    public void delete(Long id) {
        userRepository.deleteById(id);
    }
}
```

### Redis Cache-Aside Pattern
```java
@Service
public class ProductService {
    
    @Autowired
    private RedisTemplate<String, Product> redisTemplate;
    
    @Autowired
    private ProductRepository productRepository;
    
    public Product getProduct(Long id) {
        String key = "product:" + id;
        
        // Try cache first
        Product product = redisTemplate.opsForValue().get(key);
        
        if (product == null) {
            // Cache miss - load from database
            product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
            
            // Store in cache with TTL
            redisTemplate.opsForValue().set(key, product, 1, TimeUnit.HOURS);
        }
        
        return product;
    }
}
```

### HikariCP Configuration
```properties
# Sized for 8-16 Core DB server with SSDs: (Cores * 2) + 1
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=10
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

---

## 📝 Common Interview Questions

1. **Q**: Difference between INNER JOIN and LEFT JOIN?  
   **A**: INNER returns only matching rows; LEFT returns all left rows plus matching right rows (nulls if no match).

2. **Q**: What is the N+1 query problem and how do you resolve it?  
   **A**: Loading parent entities in 1 query, then triggering N separate queries for each child relation. Fix via `JOIN FETCH`, `@EntityGraph`, or batch fetching (`hibernate.default_batch_fetch_size`).

3. **Q**: Explain ACID properties at the kernel level.  
   **A**: Atomicity (WAL & undo logs), Consistency (schema constraints & invariants), Isolation (MVCC & locks), Durability (`fsync` to non-volatile disk).

4. **Q**: Explain Write Skew and why Repeatable Read permits it.  
   **A**: Write Skew occurs when transactions read overlapping state, enforce an invariant, but write to disjoint rows. Under Snapshot Isolation / Repeatable Read, neither transaction detects a row write conflict, allowing both to commit and corrupt the invariant. Resolved via `SERIALIZABLE` or `SELECT ... FOR UPDATE`.

5. **Q**: Why does UUIDv4 degrade B+ tree performance compared to UUIDv7?  
   **A**: Random UUIDv4 inserts land unpredictably across leaf pages, triggering frequent 50/50 page splits, reducing page fill factor to ~67%, and thrashing the buffer pool. UUIDv7 embeds a millisecond timestamp prefix, ensuring monotonic append-only inserts.

6. **Q**: How should you size a database connection pool?  
   **A**: Use the HikariCP formula: `Pool Size = (CPU Cores * 2) + Spindles`. Over-allocating connections causes CPU context switching and latch contention, degrading throughput.

7. **Q**: Difference between Redis and Memcached?  
   **A**: Redis supports complex data structures, persistence (RDB/AOF), replication, and pub/sub. Memcached is a simpler, multi-threaded pure key-value memory cache.

---

## 🔧 Essential Redis Commands

```bash
# String operations
SET key value
GET key
SETEX key 3600 value  # Set with TTL

# Hash operations
HSET user:1 name "John"
HGET user:1 name
HGETALL user:1

# List operations
LPUSH mylist "item1"
RPUSH mylist "item2"
LRANGE mylist 0 -1

# Set operations
SADD myset "member1"
SMEMBERS myset

# Sorted set operations
ZADD leaderboard 100 "player1"
ZRANGE leaderboard 0 -1 WITHSCORES

# TTL operations
EXPIRE key 3600
TTL key
```

---

*Quick reference for last-minute review*  
*PostgreSQL 15+ | MySQL 8.0+ | MongoDB 6+ | Redis 7+*
