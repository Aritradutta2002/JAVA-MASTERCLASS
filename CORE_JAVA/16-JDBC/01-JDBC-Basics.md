# JDBC — The Complete Interview Guide

> **JDBC is where "Java knowledge" becomes "backend engineering".** Every JDBC
> question is really about one of four things: **resource management**, **SQL
> injection**, **transactions**, or **performance**. Get those four right and
> JDBC is easy.

## Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — The One Picture](#2-mental-model--the-one-picture)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
- [4. Visualizations](#4-visualizations)
- [5. Worked Examples (with Output + Dry Run)](#5-worked-examples-with-output--dry-run)
- [6. Code from Scratch — Production-Grade](#6-code-from-scratch--production-grade)
- [7. Common Pitfalls & Gotchas](#7-common-pitfalls--gotchas)
- [8. Interview Q&A — Level-wise](#8-interview-qa--level-wise)
- [9. Counter-Questions & Traps](#9-counter-questions--traps)
- [10. Rapid-Fire MCQs](#10-rapid-fire-mcqs)
- [11. Cheat Sheet / One-Page Summary](#11-cheat-sheet--one-page-summary)
- [12. Real-World Production Notes](#12-real-world-production-notes)
- [13. Follow-up Learning](#13-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

- **JDBC = Java Database Connectivity.** A **specification** (the API in `java.sql`) plus **driver implementations** (the vendor JARs) — the classic "SPI" pattern.
- **The 6 steps:** `DriverManager.getConnection` → `prepareStatement` → bind parameters → `executeQuery`/`executeUpdate` → read the `ResultSet` → **close in reverse order**.
- **Use `try-with-resources` for `Connection`, `Statement` and `ResultSet`.** It closes them in reverse order and **suppresses** the close-failure exception.
- **`PreparedStatement` is not just for injection safety** — it lets the driver send the SQL and the parameters separately, so the database can **cache the execution plan** and reuse it.
- **`PreparedStatement` is NOT always injection-safe.** If you build the SQL by concatenating a parameter into the *text*, it is still injectable — especially identifiers (table/column names) and `LIKE` patterns.
- **Always bind a `LIKE` pattern as `"%" + term + "%"`** — never as a pre-built pattern.
- **Transactions:** `setAutoCommit(false)` → execute → `commit()` / `rollback()`. The **isolation level** is set with `setTransactionIsolation(...)`.
- **The 4 isolation levels:** READ UNCOMMITTED, READ COMMITTED, REPEATABLE READ, SERIALIZABLE — each trades isolation for concurrency. MySQL's InnoDB defaults to REPEATABLE READ; PostgreSQL and SQL Server default to READ COMMITTED.
- **The anomalies:** dirty read, non-repeatable read, phantom read.
- **A `ResultSet` is forward-only by default** (TYPE_FORWARD_ONLY). Scrollable result sets are possible but expensive.
- **The N+1 problem** — one query for a list, then one per row. The single most common performance bug in Java backends.
- **Use a connection pool (HikariCP)** — never `DriverManager.getConnection` per request.
- **Always close the `ResultSet`, then the `Statement`, then the `Connection`** — in that order.

---

## 1. What & Why — The Problem This Solves

**The problem.** Every relational database speaks its own wire protocol, and every application should not have to know the wire protocol of every database.

**The solution — a three-layer architecture:**

```text
   ┌─────────────────────────────────────────────────────────────┐
   │  YOUR CODE                                                 │
   │  Connection / PreparedStatement / ResultSet  (java.sql)     │  ← THE API
   └──────────────────────────┬──────────────────────────────────┘
                              │  the JDBC SPI
   ┌──────────────────────────┴──────────────────────────────────┐
   │  DRIVER MANAGER  →  loads the right driver, hands out a    │
   │                     Connection                              │
   └──────────┬──────────────────────────────┬──────────────────┘
              ▼                              ▼
   ┌────────────────────┐        ┌────────────────────┐
   │  MySQL Connector  │        │  PostgreSQL JDBC   │
   │  (com.mysql.cj.*)  │        │  (org.postgresql.*)│
   │  → speaks MySQL's  │        │  → speaks the PG   │
   │    wire protocol   │        │    wire protocol   │
   └────────────────────┘        └────────────────────┘
```

**The design pattern in play:** this is the classic **Service Provider Interface (SPI)**.
The JDK defines the *interface*; the vendors provide the *implementation*; the JDK's
`ServiceLoader` (or, in JDBC's case, `DriverManager`) finds them at runtime via a
`META-INF/services/java.sql.Driver` file on the classpath.

> 💡 **The interview framing.** JDBC's *value* is not that it hides the wire protocol —
> it is that it gives you a **consistent contract** (`Connection` → `Statement` →
> `ResultSet`, with a defined resource lifecycle and a defined transaction model).
> Every ORM (Hibernate, MyBatis, jOOQ, Spring Data) is built *on top of* that contract.

---

## 2. Mental Model — The One Picture

```text
   THE JDBC OBJECT GRAPH — and how it maps to a real connection
   ═══════════════════════════════════════════════════════════

   DriverManager
      │  getConnection(url, user, password)
      ▼
   ┌──────────────┐        1 connection = 1 TCP socket + 1 session
   │  Connection  │        (expensive to open! ~ms)
   │              │
   │  autoCommit  │◄── true by default → each statement is its own transaction
   │  isolation   │◄── the transaction isolation level
   │  readOnly    │
   │  holdability │
   └──────┬───────┘
          │  createStatement() / prepareStatement(sql)
          ▼
   ┌──────────────┐
   │  Statement   │  the SQL + the execution
   │              │
   │  addBatch()  │◄── for bulk operations
   │  execute()   │
   └──────┬───────┘
          │  executeQuery() / executeUpdate()
          ▼
   ┌──────────────┐
   │  ResultSet   │  a CURSOR over the rows
   │              │  • forward-only by default
   │  next()      │  • one row at a time
   │  getString() │
   │  getInt()    │
   └──────────────┘

   THE RESOURCE LIFECYCLE (must be closed in REVERSE order):
   ┌──────────────┐
   │  Connection  │  ──closed LAST
   └──────┬───────┘
          │  ┌──────────────┐
          └──┤  Statement   │  ──closed 2nd
             └──────┬───────┘
                    │  ┌──────────────┐
                    └──┤  ResultSet   │  ──closed FIRST
                       └──────────────┘

   try-with-resources does this FOR YOU, in reverse, automatically.
```

**What to look at:** the three objects form a **stack** — a `ResultSet` belongs to a `Statement`, a `Statement` belongs to a `Connection`. That is why the close order is reverse-of-creation, and why closing the `Connection` closes the rest.

---

## 3. Deep Dive — Theory

## 3.1 The JDBC architecture

```java
// The API (java.sql)
public interface Driver {
    Connection connect(String url, Properties info) throws SQLException;
    boolean acceptsURL(String url) throws SQLException;
}

public interface Connection extends Wrapper, AutoCloseable {
    Statement createStatement() throws SQLException;
    PreparedStatement prepareStatement(String sql) throws SQLException;
    CallableStatement prepareCall(String sql) throws SQLException;
    void setAutoCommit(boolean autoCommit) throws SQLException;
    boolean getAutoCommit() throws SQLException;
    void commit() throws SQLException;
    void rollback() throws SQLException;
    void setTransactionIsolation(int level) throws SQLException;
    DatabaseMetaData getMetaData() throws SQLException;
    // ...
}
```

**`DriverManager` and the driver-loading change:**

| Java version | How the driver is found |
|---|---|
| Java 6 and earlier | `Class.forName("com.mysql.cj.jdbc.Driver")` — **explicit** |
| Java 6+ (ServiceLoader) | **Automatic** — the JAR has `META-INF/services/java.sql.Driver` |
| Java 9+ (modules) | The `java.sql` module declares `uses java.sql.Driver`; the driver module `provides` it |

```java
// ❌ UNNECESSARY since Java 6
Class.forName("com.mysql.cj.jdbc.Driver");

// ✅ Since Java 6 the ServiceLoader finds it — just call getConnection
Connection conn = DriverManager.getConnection(url, user, password);
```

> 💡 **A modern best practice:** in a Spring Boot application you do **not** use
> `DriverManager` at all — you declare a `DataSource` bean, and Spring's
> `DataSourceAutoConfiguration` builds a **HikariCP pool** from your `spring.datasource.*`
> properties. The driver is registered automatically by the JDBC starter.

## 3.2 The 6 steps — the canonical flow

```java
// ① Get a connection
try (Connection conn = dataSource.getConnection()) {

    // ② Create a statement (PREPARED — for parameters)
    try (PreparedStatement ps = conn.prepareStatement(
            "SELECT id, name, email FROM users WHERE status = ? AND age > ?")) {

        // ③ Bind the parameters (1-based!)
        ps.setString(1, "ACTIVE");
        ps.setInt(2, 18);

        // ④ Execute
        try (ResultSet rs = ps.executeQuery()) {

            // ⑤ Process the results
            while (rs.next()) {
                long id = rs.getLong("id");
                String name = rs.getString("name");
                System.out.println(id + " " + name);
            }
        }
    }
}   // ⑥ Everything is closed, in reverse order, automatically
```

**The parameter indices are 1-based** — a very common off-by-one bug. `setString(0, ...)` throws `SQLException: Invalid parameter index`.

## 3.3 `Statement` vs `PreparedStatement` vs `CallableStatement`

| | `Statement` | `PreparedStatement` | `CallableStatement` |
|---|---|---|---|
| SQL | Built at runtime (concatenation) | SQL with `?` placeholders | A stored procedure call |
| Parameters | ❌ none | ✅ `setXxx(int, value)` | ✅ `setXxx` for IN/OUT params |
| Injection risk | ❌ **HIGH** | ✅ low (see below) | ✅ low |
| Plan caching | ❌ every execution | ✅ the plan is reused | ✅ |
| Batch | ✅ `addBatch` | ✅ `addBatch` | ✅ |
| Extra results | ❌ | ❌ | ✅ `getMoreResults()` |
| Use for | DDL, and rare dynamic SQL | **95% of your code** | Stored procedures |

**The three `execute` methods:**

| Method | Returns | Use when |
|---|---|---|
| `executeQuery(sql)` | a `ResultSet` | the statement is a `SELECT` |
| `executeUpdate(sql)` | the affected-row count | `INSERT`/`UPDATE`/`DELETE`/`DDL` |
| `execute(sql)` | a `boolean` (true = a `ResultSet`) | you do not know the statement type |

**`executeUpdate` returns:**
- `INSERT` → the number of rows inserted (usually 1)
- `UPDATE`/`DELETE` → the number of rows **affected** (not the number matched!) — a classic surprise when a `WHERE` clause matches but the values are identical and the DB reports 0 changed rows.

## 3.4 SQL injection — the real story

```java
// ❌ CATASTROPHIC
String sql = "SELECT * FROM users WHERE name = '" + name + "'";
// name = "'; DROP TABLE users; --"
// → SELECT * FROM users WHERE name = ''; DROP TABLE users; --'
```

```java
// ✅ SAFE
String sql = "SELECT * FROM users WHERE name = ?";
try (PreparedStatement ps = conn.prepareStatement(sql)) {
    ps.setString(1, name);
    // ...
}
```

**Why `PreparedStatement` is safe:** the SQL text and the parameter values are sent to the
database **separately**. The database parses the SQL **once** and the parameter is treated
as a *value*, never as SQL syntax. So it cannot change the statement's structure.

**But `PreparedStatement` is NOT unconditionally safe:**

```java
// ❌ STILL INJECTABLE — the parameter is in the SQL TEXT
String sql = "SELECT * FROM " + tableName + " WHERE id = ?";
// tableName = "users; DROP TABLE orders; --"

// ❌ STILL INJECTABLE — a LIKE pattern built by concatenation
String sql = "SELECT * FROM products WHERE name LIKE '%" + term + "%'";
// term = "%' OR '1'='1"   → matches everything
```

**✅ The correct fixes:**

```java
// ① Identifiers (table/column names) — you cannot bind them.
//    Validate against an ALLOW-LIST:
private static final Set<String> ALLOWED_TABLES = Set.of("users", "orders", "products");
if (!ALLOWED_TABLES.contains(tableName)) {
    throw new IllegalArgumentException("Unknown table: " + tableName);
}

// ② LIKE patterns — bind the VALUE, and add the wildcards in SQL:
String sql = "SELECT * FROM products WHERE name LIKE ?";
ps.setString(1, "%" + term + "%");     // ✅ the wildcards are in the VALUE
```

> 💡 **The one-liner:** *the database parses the SQL and binds the values separately;
> anything you put into the SQL **text** is parsed as SQL.* So the rule is: **never
> interpolate user input into the SQL text — and if you must interpolate something,
> it must be from a hard-coded allow-list.**

**The second-order defence:** a **read-only database user** for the reporting queries, and
**least privilege** — so that even an injection cannot do damage.

## 3.5 Transactions and the four isolation levels

```java
try (Connection conn = dataSource.getConnection()) {
    conn.setAutoCommit(false);            // ① turn off auto-commit
    try {
        try (PreparedStatement debit = conn.prepareStatement(
                "UPDATE accounts SET balance = balance - ? WHERE id = ?")) {
            debit.setBigDecimal(1, amount);
            debit.setLong(2, fromId);
            debit.executeUpdate();
        }
        try (PreparedStatement credit = conn.prepareStatement(
                "UPDATE accounts SET balance = balance + ? WHERE id = ?")) {
            credit.setBigDecimal(1, amount);
            credit.setLong(2, toId);
            credit.executeUpdate();
        }
        conn.commit();                     // ② commit
    } catch (SQLException e) {
        conn.rollback();                   // ③ roll back on failure
        throw e;
    }
}   // ④ the connection is returned to the pool
```

**⚠ The classic trap:** with `autoCommit = true` (the default), **each statement is its own
transaction**. So two `executeUpdate` calls in a row are **not** atomic — if the second
fails, the first is already committed. That is a data-corruption bug waiting to happen.

**⚠ The rollback trap:** if the `Connection` comes from a **pool** and you do not
`rollback()` before returning it, the pooled connection may carry an open transaction
into the next borrower. The fix is `setAutoCommit(true)` in a `finally`, or (better) let
the pool reset it — HikariCP does this automatically on return.

**The four isolation levels:**

| Level | Dirty read | Non-repeatable read | Phantom read | Concurrency |
|---|---|---|---|---|
| **READ UNCOMMITTED** | ✅ possible | ✅ possible | ✅ possible | Highest |
| **READ COMMITTED** | ❌ | ✅ possible | ✅ possible | High |
| **REPEATABLE READ** | ❌ | ❌ | ✅ possible | Medium |
| **SERIALIZABLE** | ❌ | ❌ | ❌ | Lowest |

**The anomalies:**

| Anomaly | Definition | Prevented by |
|---|---|---|
| **Dirty read** | Reading a value another transaction has written but not committed | READ COMMITTED and above |
| **Non-repeatable read** | Re-reading a row gives a *different* value (someone committed an update) | REPEATABLE READ and above |
| **Phantom read** | Re-running a query returns *more* rows (someone committed an insert) | SERIALIZABLE |

```java
conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
```

**What the databases actually default to:**

| Database | Default isolation | Note |
|---|---|---|
| **PostgreSQL** | READ COMMITTED | The most common modern default |
| **MySQL (InnoDB)** | **REPEATABLE READ** | A legacy choice; it can cause gap locks |
| **Oracle** | READ COMMITTED | |
| **SQL Server** | READ COMMITTED | With **read committed snapshot** off by default |
| **H2** | READ COMMITTED | |

> 💡 **The important point:** "REPEATABLE READ" in MySQL is not the same as the SQL
> standard's REPEATABLE READ — InnoDB's version uses **next-key locks** (gap locks),
> which can cause **deadlocks** on concurrent inserts into the same range. This is
> why many teams explicitly set READ COMMITTED on MySQL.

## 3.6 `ResultSet` — types and access

```java
// The default is TYPE_FORWARD_ONLY, CONCUR_READ_ONLY
ResultSet rs = ps.executeQuery();

// Scrollable (expensive! the driver may materialise everything)
conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
rs.beforeFirst(); rs.next(); rs.last(); rs.getRow();
```

**Column access:**

```java
rs.getInt("id");        // by NAME (case-insensitive in most drivers)
rs.getInt(1);           // by INDEX (1-based)
rs.getLong("id");
rs.getBigDecimal("total");
rs.getString("name");
rs.getBoolean("active");
rs.getDate("created_at");
rs.getTimestamp("created_at");
rs.getObject("payload");   // the driver decides the type

int id = rs.getInt("id");
boolean wasNull = rs.wasNull();   // ⚠ getInt returns 0 for SQL NULL — check wasNull()!
```

**⚠ The `wasNull()` trap:** `getInt` on a SQL `NULL` returns **0**, not an exception. If 0
is a legitimate value, you cannot distinguish it from `NULL` without `wasNull()`. The
cleaner solution: use `getObject("id", Integer.class)` which returns `null` for SQL `NULL`.

**The `ResultSet` is a cursor — one row at a time.** Reading it lazily is a *feature*:
it lets you process a million-row result with a constant memory footprint.

**Resource warning:** in some drivers, closing the `Statement` closes the `ResultSet`.
Always use try-with-resources for both.

## 3.7 Batch operations

```java
try (Connection conn = dataSource.getConnection();
     PreparedStatement ps = conn.prepareStatement(
             "INSERT INTO audit_log (actor, action, ts) VALUES (?, ?, ?)")) {

    for (AuditEvent event : events) {
        ps.setString(1, event.actor());
        ps.setString(2, event.action());
        ps.setTimestamp(3, Timestamp.from(event.timestamp()));
        ps.addBatch();                       // ① accumulate
    }

    int[] counts = ps.executeBatch();        // ② flush — one round trip
    // counts[i] = the rows affected by batch i, or SUCCESS_NO_INFO (-2)
}
```

**Why batching is much faster:**

```text
   ❌ WITHOUT BATCH — one network round trip per row
   ──────────────────────────────────────────────
   INSERT row 1  ──▶ [network] ──▶ [DB]   (~1 ms each)
   INSERT row 2  ──▶ [network] ──▶ [DB]
   INSERT row 3  ──▶ [network] ──▶ [DB]
   1,000 rows = 1,000 round trips ≈ 1,000 ms

   ✅ WITH BATCH — one round trip for all
   ───────────────────────────────────
   INSERT row 1..1000 ──▶ [network] ──▶ [DB]   (~5 ms)
   1,000 rows = 1 round trip ≈ 5 ms
   → 100–200× faster for bulk inserts
```

> ⚠️ **A batch is still one transaction** (unless you commit between batches). A huge
> batch can blow up the database's transaction log. The production pattern is to
> **chunk** the batch (e.g. 500–1,000 rows) and commit per chunk.

## 3.8 Connection pooling

```java
// ❌ NEVER in production — a new TCP connection + auth per request
Connection conn = DriverManager.getConnection(url, user, password);

// ✅ Use a pool
HikariDataSource dataSource = new HikariDataSource();
dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/app");
dataSource.setUsername("app");
dataSource.setPassword("secret");
dataSource.setMaximumPoolSize(20);
dataSource.setMinimumIdle(5);
dataSource.setConnectionTimeout(30000);
dataSource.setPoolName("app-pool");

try (Connection conn = dataSource.getConnection()) { ... }
```

**Sizing the pool** — the classic rule of thumb and the modern correction:

```text
   THE OLD RULE:  connections ≈ cores × 2 + effective_cores
   THE MODERN RULE: start SMALL and measure
     • a database is a SHARED, finite resource
     • too many connections → the DB spends its time context-switching
     • more connections than the DB can serve → latency EXPLODES
     • the DB's own max_connections is the ceiling

   A BETTER POOL SIZING FORMULA (from the "Database Connections" articles):
     connections = ( (core_count * 2) + effective_spindle_count )
   For a cloud database with a network hop:
     connections ≈  (cores * 2) + 1     ... and then MEASURE
```

**The key modern insight:** with a **connection pool**, the pool size is a **throughput
knob**, and making it larger rarely makes things faster — it usually makes the database
slower. Measure, and watch the pool's *pending* count as the saturation signal.

**HikariCP's most important setting:**

```java
dataSource.setConnectionTimeout(30000);   // how long to wait for a connection
// ⚠ IF EXCEEDED → SQLException: Connection is not available, request timed out
//   This is the signal that the pool is TOO SMALL (or a query is too slow)
```

## 3.9 The N+1 problem

```text
   ❌ THE N+1 PROBLEM
   ────────────────────
   // 1 query for the users
   SELECT * FROM users WHERE tenant_id = ?

   // then 1 query PER USER for their orders
   for (User user : users) {
       List<Order> orders = orderRepo.findByUserId(user.getId());   // ← N queries!
       user.setOrders(orders);
   }
   // 1 + N round trips. For 1,000 users = 1,001 queries.

   ✅ FIX 1 — a JOIN
   SELECT u.*, o.* FROM users u LEFT JOIN orders o ON o.user_id = u.id WHERE u.tenant_id = ?

   ✅ FIX 2 — a batched IN query (usually better than a JOIN for large results)
   SELECT * FROM orders WHERE user_id IN (?, ?, ?, …)

   ✅ FIX 3 — @EntityGraph / a fetch join (Hibernate)
   @Query("select distinct u from User u left join fetch u.orders where u.tenantId = :t")

   ✅ FIX 4 — a DataLoader (GraphQL) — batches across a whole request
```

**How to detect it:** a query log showing the *same* statement repeated with different
parameters, or a slow endpoint whose latency scales with the number of rows returned.
The tools: Hibernate's statistics (`hibernate.generate_statistics`, and
`sessionFactory.getStatistics().getQueryExecutionCount()`), or a datasource proxy like
**P6Spy** / **datasource-proxy**.

## 3.10 `DatabaseMetaData` and `ResultSetMetaData`

```java
try (Connection conn = dataSource.getConnection()) {
    DatabaseMetaData meta = conn.getMetaData();
    System.out.println("DB: " + meta.getDatabaseProductName());
    System.out.println("Version: " + meta.getDatabaseProductVersion());
    System.out.println("Driver: " + meta.getDriverName() + " " + meta.getDriverVersion());

    // List the tables
    try (ResultSet tables = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
        while (tables.next()) {
            System.out.println("table: " + tables.getString("TABLE_NAME"));
        }
    }
}

try (ResultSet rs = ps.executeQuery()) {
    ResultSetMetaData rsm = rs.getMetaData();
    int columns = rsm.getColumnCount();
    for (int i = 1; i <= columns; i++) {
        System.out.println(rsm.getColumnLabel(i) + " : " + rsm.getColumnTypeName(i));
    }
}
```

## 3.11 `CallableStatement` — stored procedures

```java
try (Connection conn = dataSource.getConnection();
     CallableStatement cs = conn.prepareCall("{call calculate_bonus(?, ?)}")) {

    cs.setInt(1, employeeId);
    cs.setInt(2, 2024);
    cs.execute();

    // Read the OUT parameters
    int bonus = cs.getInt(3);
    System.out.println("bonus = " + bonus);
}
```

**The trade-off:** stored procedures keep the business logic in the database (one place,
consistent performance, no network round trip per step) but they move logic out of the
application, out of version control, and out of your unit tests. Most modern teams keep
the logic in Java and use SQL for what SQL is good at.

## 3.12 `DataSource` vs `DriverManager`

| | `DriverManager` | `DataSource` |
|---|---|---|
| Pooling | ❌ none | ✅ (HikariCP, etc.) |
| Distributed transactions | ❌ | ✅ (JTA — the XA `DataSource`) |
| JNDI lookup | ❌ | ✅ |
| Connection properties | via `Properties` | via the pool config |
| Use in Spring | ❌ | ✅ **the only option** |

> 💡 **In Spring Boot you never touch `DriverManager`.** You declare
> `spring.datasource.url/username/password` and the auto-configuration builds a
> `HikariDataSource` — which Spring injects wherever a `DataSource` is needed. The
> pool's `maximum-pool-size` is set with `spring.datasource.hikari.maximum-pool-size`.

---

## 4. Visualizations

### 4.1 The JDBC object graph and the resource lifecycle

```text
   ┌──────────────────────────────────────────────────────────────────┐
   │  try-with-resources — the closures happen in REVERSE order      │
   └──────────────────────────────────────────────────────────────────┘

   try (Connection c = …) {          ← c is closed LAST
       try (PreparedStatement p = …) {   ← p is closed 2nd
           try (ResultSet r = …) {        ← r is closed FIRST
               …
           }
       }
   }

   WHY REVERSE?  a ResultSet belongs to a Statement, which belongs to a Connection.
   Closing the ResultSet first releases the cursor before the statement is torn down.

   ⚠ AND IF close() THROWS, try-with-resources SUPPRESSES it:
     the primary exception is thrown, and the close failure is attached
     via getSuppressed() — you never lose the real error.
```

### 4.2 SQL injection — the two-part statement

```text
   ❌ THE VULNERABLE STATEMENT
   ───────────────────────────
   "SELECT * FROM users WHERE name = '" + name + "'"

   name = "x' OR '1'='1"

   The DB receives:
   ┌────────────────────────────────────────────────────────────┐
   │ SELECT * FROM users WHERE name = 'x' OR '1'='1'            │
   └────────────────────────────────────────────────────────────┘
   → returns EVERY user 💥

   ✅ THE PREPARED STATEMENT
   ────────────────────────
   SQL text:  "SELECT * FROM users WHERE name = ?"
   Parameter: "x' OR '1'='1"

   The DB receives them SEPARATELY:
   ┌────────────────────────────────────────────────────────────┐
   │ SQL text:  SELECT * FROM users WHERE name = ?               │
   │ parameter: x' OR '1'='1                                    │
   └────────────────────────────────────────────────────────────┘
   → the parameter is bound as a VALUE, never parsed as SQL ✅
   → returns only the user whose name is literally "x' OR '1'='1"
```

### 4.3 The four isolation levels — the anomalies grid

```text
   ┌──────────────────────┬────────────┬──────────────┬──────────────┬─────────┐
   │ ISOLATION LEVEL       │ DIRTY READ │ NON-REPEAT.  │ PHANTOM READ │ CONCURR.│
   ├──────────────────────┼────────────┼──────────────┼──────────────┼─────────┤
   │ READ UNCOMMITTED      │  ✅ YES    │   ✅ YES     │   ✅ YES     │ HIGHEST │
   │ READ COMMITTED        │  ❌ no     │   ✅ YES     │   ✅ YES     │ HIGH    │
   │ REPEATABLE READ       │  ❌ no     │   ❌ no      │   ✅ YES     │ MEDIUM  │
   │ SERIALIZABLE          │  ❌ no     │   ❌ no      │   ❌ no      │ LOWEST  │
   └──────────────────────┴────────────┴──────────────┴──────────────┴─────────┘

   A DIRTY READ:  T1 writes 100, T2 reads 100, T1 ROLLS BACK
                  → T2 saw a value that never existed 💥

   A NON-REPEATABLE READ:  T1 reads balance=100, T2 commits balance=200,
                           T1 reads again → 200 💥

   A PHANTOM READ:  T1 runs "SELECT * FROM orders WHERE total > 50" (3 rows),
                    T2 inserts a 4th matching row and commits,
                    T1 runs it again → 4 rows 💥
```

### 4.4 The N+1 problem — 1 + N round trips

```text
   ❌ N+1
   ──────
   SELECT * FROM users WHERE tenant_id = 42          ──▶ [DB]   1 query
        │
        ├─ user 1: SELECT * FROM orders WHERE user_id=1  ──▶ [DB]
        ├─ user 2: SELECT * FROM orders WHERE user_id=2  ──▶ [DB]
        ├─ user 3: SELECT * FROM orders WHERE user_id=3  ──▶ [DB]
        └─ …
   → 1 + N round trips.  For 1,000 users: 1,001 queries, 1,001 × ~1 ms = 1 second 💥

   ✅ FIXED WITH A JOIN
   ────────────────────
   SELECT u.*, o.* FROM users u
     LEFT JOIN orders o ON o.user_id = u.id
    WHERE u.tenant_id = 42                        ──▶ [DB]   1 query
   → 1 round trip, but a LARGE result set (row multiplication) ⚠
```

### 4.5 Batch vs no batch

```text
   ❌ ONE BY ONE — 1,000 round trips
   ────────────────────────────────────
   INSERT … ──▶[net]──▶[DB]   (1 ms)
   INSERT … ──▶[net]──▶[DB]   (1 ms)
   × 1,000                                                       ≈ 1,000 ms

   ✅ BATCH — 1 round trip
   ──────────────────────────
   INSERT …×1000 ──▶[net]──▶[DB]                              ≈ 5 ms
```

### 4.6 The connection pool — the saturation signal

```text
   THE POOL
   ─────────
   HikariCP (max=20)
        │
        ├─ connection 1  ──▶ [DB]
        ├─ connection 2  ──▶ [DB]
        ├─ …
        ├─ connection 20 ──▶ [DB]      ← 20 concurrent DB sessions
        │
        └─ WAITING QUEUE  ──▶ (requests 21, 22, … wait here)
                │
                └─ ⚠ IF THE QUEUE GROWS → the pool is SATURATED
                     OR a query is slow
                     → "Connection is not available, request timed out"

   THE MODERN INSIGHT:
     the pool size is a THROUGHPUT knob, and a BIGGER pool is usually SLOWER
     (the DB context-switches between sessions)
     → start small (10–20), MEASURE, and watch the pending count
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — The 6 steps, with try-with-resources

```java
import java.sql.*;

public class JdbcBasicsDemo {
    // A tiny in-memory stand-in so the example runs without a database.
    // In production this is a HikariCP DataSource.

    public static void main(String[] args) throws SQLException {
        // Simulated connection — the real code is dataSource.getConnection()
        Connection conn = FakeDb.connect();
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, name, email, active FROM users WHERE age > ?")) {

                ps.setInt(1, 18);

                try (ResultSet rs = ps.executeQuery()) {
                    System.out.printf("%-6s %-10s %-20s %s%n", "id", "name", "email", "active");
                    while (rs.next()) {
                        System.out.printf("%-6d %-10s %-20s %s%n",
                                rs.getLong("id"),
                                rs.getString("name"),
                                rs.getString("email"),
                                rs.getBoolean("active"));
                    }
                }
            }
        } finally {
            conn.close();
        }
        System.out.println("all resources closed ✅");
    }
}
```

**The (simulated) output:**

```text
id     name       email                active
1      Alice      alice@example.com    true
2      Bob        bob@example.com      true
3      Carol      carol@example.com    false
all resources closed ✅
```

**Dry run — what happens at each step:**

| Step | Code | What the driver does |
|---|---|---|
| ① | `dataSource.getConnection()` | Takes a connection from the pool (or opens a socket) |
| ② | `conn.prepareStatement(sql)` | Sends the SQL text; the DB **parses** it and prepares a plan |
| ③ | `ps.setInt(1, 18)` | Binds the parameter **locally** — it is NOT sent yet |
| ④ | `ps.executeQuery()` | Sends the SQL **and** the bound parameters; the DB executes and returns a cursor |
| ⑤ | `rs.next()` | Fetches the next row; `false` at the end |
| ⑥ | closes (reverse) | `rs` → `ps` → `conn`; the connection returns to the pool |

**Takeaway:** the parameter is bound at step ③ but only *sent* at step ④ — that separation is exactly why injection does not work.

### Example 2 — SQL injection, blocked

```java
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class InjectionDemo {

    /** Simulates what the database does with a bound parameter. */
    static String executeWithBinding(String sqlTemplate, String param) {
        // The DB parses the TEMPLATE and binds the VALUE — the value is never SQL
        return "SQL: " + sqlTemplate + "  |  param: " + param
             + "  →  matched rows: " + (param.contains("'") ? 0 : 1);
    }

    static String executeWithConcatenation(String sql) {
        return "SQL: " + sql + "  →  matched rows: ALL 💥";
    }

    public static void main(String[] args) {
        String malicious = "x' OR '1'='1";

        // ❌ Concatenation
        String badSql = "SELECT * FROM users WHERE name = '" + malicious + "'";
        System.out.println("❌ " + executeWithConcatenation(badSql));

        // ✅ Binding
        String template = "SELECT * FROM users WHERE name = ?";
        System.out.println("✅ " + executeWithBinding(template, malicious));

        // ✅ Even a LIKE pattern is safe if you bind the VALUE
        String likeTemplate = "SELECT * FROM products WHERE name LIKE ?";
        System.out.println("✅ " + executeWithBinding(likeTemplate, "%" + malicious + "%"));

        // ❌ But a LIKE pattern built by concatenation is NOT safe
        String badLike = "SELECT * FROM products WHERE name LIKE '%" + malicious + "%'";
        System.out.println("❌ " + executeWithConcatenation(badLike));
    }
}
```

**Actual output:**

```text
❌ SQL: SELECT * FROM users WHERE name = 'x' OR '1'='1'  →  matched rows: ALL 💥
✅ SQL: SELECT * FROM users WHERE name = ?  |  param: x' OR '1'='1  →  matched rows: 0
✅ SQL: SELECT * FROM products WHERE name LIKE ?  |  param: %x' OR '1'='1%  →  matched rows: 0
❌ SQL: SELECT * FROM products WHERE name LIKE '%x' OR '1'='1%'  →  matched rows: ALL 💥
```

**Takeaway:** the difference between the safe and unsafe versions is *exactly* whether the input ended up in the SQL **text** or in a **bound parameter**.

### Example 3 — Transactions, and what happens without one

```java
import java.sql.*;

public class TransactionDemo {
    public static void main(String[] args) throws SQLException {
        Connection conn = FakeDb.connect();

        // ❌ WITHOUT a transaction — each statement is its own transaction
        conn.setAutoCommit(true);
        System.out.println("autoCommit = " + conn.getAutoCommit()
                + "  → each UPDATE commits IMMEDIATELY ⚠");
        System.out.println("   debit 100 → committed");
        System.out.println("   credit 100 → 💥 FAILS → the debit is NOT rolled back 💥");

        // ✅ WITH a transaction
        conn.setAutoCommit(false);
        System.out.println("\nautoCommit = " + conn.getAutoCommit()
                + "  → the statements are ONE unit of work ✅");
        try {
            System.out.println("   debit 100 → pending");
            System.out.println("   credit 100 → pending");
            conn.commit();
            System.out.println("   commit ✅ → both applied together");
        } catch (SQLException e) {
            conn.rollback();
            System.out.println("   rollback ✅ → neither applied");
        }

        // The isolation level
        conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        System.out.println("\nisolation = " + conn.getTransactionIsolation()
                + "  (TRANSACTION_READ_COMMITTED = " + Connection.TRANSACTION_READ_COMMITTED + ")");
        conn.setAutoCommit(true);
        conn.close();
    }
}
```

**Actual output:**

```text
autoCommit = true  → each UPDATE commits IMMEDIATELY ⚠
   debit 100 → committed
   credit 100 → 💥 FAILS → the debit is NOT rolled back 💥

autoCommit = false  → the statements are ONE unit of work ✅
   debit 100 → pending
   credit 100 → pending
   commit ✅ → both applied together

isolation = 2  (TRANSACTION_READ_COMMITTED = 2)
```

**Takeaway:** the `autoCommit = true` default is the single most common data-integrity bug in hand-written JDBC. The `debit` succeeds, the `credit` fails, and the money has vanished.

### Example 4 — Batch insert: the round-trip difference

```java
import java.sql.*;

public class BatchDemo {
    public static void main(String[] args) throws SQLException {
        Connection conn = FakeDb.connect();

        // ❌ One statement per row
        long t0 = System.nanoTime();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO audit_log (actor, action) VALUES (?, ?)")) {
            for (int i = 0; i < 1000; i++) {
                ps.setString(1, "user" + i);
                ps.setString(2, "login");
                ps.executeUpdate();               // ← 1,000 round trips
            }
        }
        long individual = (System.nanoTime() - t0) / 1_000_000;

        // ✅ Batched
        long t1 = System.nanoTime();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO audit_log (actor, action) VALUES (?, ?)")) {
            for (int i = 0; i < 1000; i++) {
                ps.setString(1, "user" + i);
                ps.setString(2, "login");
                ps.addBatch();                    // ← accumulate
            }
            int[] counts = ps.executeBatch();     // ← 1 round trip
            System.out.println("batch results: " + counts.length + " statements, first count = " + counts[0]);
        }
        long batched = (System.nanoTime() - t1) / 1_000_000;

        System.out.println("individual : " + individual + " ms  (1,000 round trips)");
        System.out.println("batched    : " + batched + " ms  (1 round trip)");
        conn.close();
    }
}
```

**Actual output** (timings vary):

```text
batch results: 1000 statements, first count = 1
individual : 63 ms  (1,000 round trips)
batched    : 4 ms  (1 round trip)
```

**Takeaway:** the batch is roughly **15× faster** here — and the gap grows with the row count and with the network latency. This is the single biggest easy win in JDBC performance.

### Example 5 — The `wasNull()` trap

```java
import java.sql.*;

public class WasNullDemo {
    public static void main(String[] args) throws SQLException {
        try (Connection conn = FakeDb.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, discount FROM orders WHERE id = ?")) {

            ps.setLong(1, 100L);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    int discount = rs.getInt("discount");   // ⚠ 0 if the SQL value is NULL

                    System.out.println("id       = " + id);
                    System.out.println("discount = " + discount + "  ← is it really 0, or NULL?");

                    if (rs.wasNull()) {
                        System.out.println("  → wasNull() says the SQL value was NULL 💡");
                    } else {
                        System.out.println("  → the value really is " + discount);
                    }

                    // ✅ The cleaner way — the typed getObject returns null for SQL NULL
                    Integer clean = rs.getObject("discount", Integer.class);
                    System.out.println("getObject(Integer.class) = " + clean + "  ← null-safe ✅");
                }
            }
        }
    }
}
```

**Actual output:**

```text
id       = 100
discount = 0  ← is it really 0, or NULL?
  → wasNull() says the SQL value was NULL 💡
getObject(Integer.class) = null  ← null-safe ✅
```

**Takeaway:** `getInt` cannot distinguish `0` from `NULL`. Use `wasNull()` immediately after, or — better — `getObject(name, Integer.class)`, which returns `null` for SQL `NULL` and boxes the value otherwise.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A production repository — every pitfall handled

```java
package com.example.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.sql.DataSource;

/**
 * A production-grade JDBC repository.
 *
 * <p>Every rule an interviewer will look for is encoded here:
 * <ul>
 *   <li>Resources via <b>try-with-resources</b> — closed in reverse, automatically.</li>
 *   <li><b>PreparedStatement</b> everywhere, with bound parameters.</li>
 *   <li>A <b>transaction</b> for multi-statement operations, with a rollback in a catch.</li>
 *   <li><b>Batching</b> for bulk writes.</li>
 *   <li>Domain exceptions, not raw {@link SQLException}, at the boundary.</li>
 *   <li>A <b>DataSource</b> (a pool), never {@code DriverManager}.</li>
 * </ul>
 */
public class JdbcUserRepository {

    private final DataSource dataSource;

    public JdbcUserRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    // ── Domain types ──────────────────────────────────────────────────────
    public record User(long id, String name, String email, Instant createdAt) {}

    // ── Read ──────────────────────────────────────────────────────────────
    public Optional<User> findById(long id) {
        String sql = "SELECT id, name, email, created_at FROM users WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("findById(" + id + ") failed", e);
        }
    }

    public List<User> findByTenant(String tenantId, int limit) {
        String sql = """
                SELECT id, name, email, created_at
                  FROM users
                 WHERE tenant_id = ?
                 ORDER BY created_at DESC
                 LIMIT ?
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tenantId);
            ps.setInt(2, limit);          // ⚠ LIMIT cannot be bound in some DBs — see below

            try (ResultSet rs = ps.executeQuery()) {
                List<User> users = new ArrayList<>();
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
                return users;
            }
        } catch (SQLException e) {
            throw new DataAccessException("findByTenant failed", e);
        }
    }

    // ── Write (single) ────────────────────────────────────────────────────
    public long insert(String name, String email) {
        String sql = "INSERT INTO users (name, email, created_at) VALUES (?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name);
            ps.setString(2, email);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new DuplicateEmailException(email, e);
        } catch (SQLException e) {
            throw new DataAccessException("insert failed", e);
        }
    }

    // ── Write (transactional, multiple statements) ────────────────────────
    /** Move a user between tenants — two statements, ONE transaction. */
    public void moveToTenant(long userId, String fromTenant, String toTenant) {
        String update = "UPDATE users SET tenant_id = ? WHERE id = ? AND tenant_id = ?";
        String audit  = "INSERT INTO tenant_moves (user_id, from_tenant, to_tenant, moved_at) "
                     + "VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);                     // ① one unit of work
            try {
                try (PreparedStatement ps = conn.prepareStatement(update)) {
                    ps.setString(1, toTenant);
                    ps.setLong(2, userId);
                    ps.setString(3, fromTenant);
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException(
                                "No such user " + userId + " in tenant " + fromTenant);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(audit)) {
                    ps.setLong(1, userId);
                    ps.setString(2, fromTenant);
                    ps.setString(3, toTenant);
                    ps.setTimestamp(4, Timestamp.from(Instant.now()));
                    ps.executeUpdate();
                }
                conn.commit();                               // ② both applied
            } catch (SQLException | RuntimeException e) {
                conn.rollback();                             // ③ neither applied
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);      // ④ restore for the pool
            }
        } catch (SQLException e) {
            throw new DataAccessException("moveToTenant failed", e);
        }
    }

    // ── Bulk write ────────────────────────────────────────────────────────
    public int insertAll(List<User> users) {
        if (users.isEmpty()) {
            return 0;
        }
        String sql = "INSERT INTO users (name, email, created_at) VALUES (?, ?, ?)";
        int inserted = 0;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (User user : users) {
                ps.setString(1, user.name());
                ps.setString(2, user.email());
                ps.setTimestamp(3, Timestamp.from(user.createdAt()));
                ps.addBatch();
            }
            int[] counts = ps.executeBatch();
            for (int count : counts) {
                if (count > 0) inserted += count;   // SUCCESS_NO_INFO (-2) means "unknown"
            }
            return inserted;
        } catch (SQLException e) {
            throw new DataAccessException("insertAll failed", e);
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────
    private static User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getTimestamp("created_at").toInstant());
    }

    // ── Domain exceptions ─────────────────────────────────────────────────
    public static class DataAccessException extends RuntimeException {
        public DataAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class DuplicateEmailException extends DataAccessException {
        public DuplicateEmailException(String email, Throwable cause) {
            super("A user with the email " + email + " already exists", cause);
        }
    }
}
```

> ⚠️ **About the `LIMIT ?` parameter.** Most databases (PostgreSQL, MySQL, SQL Server 2012+)
> support a bound `LIMIT ?`. **Oracle does not** — it needs the value inlined and validated.
> If you must support Oracle, validate the number as a `long` and inline it — never a string.

### 6.2 A safe identifier helper (for the cases you cannot bind)

```java
package com.example.repo;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Safe handling of the one thing you cannot bind: SQL IDENTIFIERS.
 *
 * <p>Table and column names cannot be parameterised, so the ONLY safe approach is a
 * strict ALLOW-LIST. Anything else is an injection vector.
 */
public final class SqlIdentifiers {

    private SqlIdentifiers() {}

    /** A conservative allow-list of the tables this application may query. */
    private static final Set<String> READABLE_TABLES = Set.of(
            "users", "orders", "order_items", "products", "payments", "audit_log");

    private static final Pattern VALID_IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    /** Validates and returns a table name, or throws. */
    public static String table(String candidate) {
        String normalised = candidate == null ? "" : candidate.toLowerCase(Locale.ROOT);
        if (!VALID_IDENTIFIER.matcher(normalised).matches()) {
            throw new IllegalArgumentException("Invalid table name: " + candidate);
        }
        if (!READABLE_TABLES.contains(normalised)) {
            throw new IllegalArgumentException("Table not allowed: " + candidate);
        }
        return normalised;
    }

    /**
     * Validates a sort column. The direction is NOT a string — it is an enum,
     * so it cannot be injected at all.
     */
    public enum Direction { ASC, DESC }

    public static String orderBy(String column, Direction direction) {
        String normalised = column == null ? "" : column.toLowerCase(Locale.ROOT);
        if (!VALID_IDENTIFIER.matcher(normalised).matches()) {
            throw new IllegalArgumentException("Invalid column name: " + column);
        }
        if (!Set.of("id", "name", "email", "created_at", "total").contains(normalised)) {
            throw new IllegalArgumentException("Column not allowed: " + column);
        }
        return normalised + " " + direction.name();   // ✅ the direction is an enum
    }
}
```

```java
// ✅ The correct usage
String sql = "SELECT * FROM " + SqlIdentifiers.table(tableName)
           + " ORDER BY " + SqlIdentifiers.orderBy(sortColumn, Direction.DESC)
           + " LIMIT 50";
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — String-concatenated SQL

```java
"SELECT * FROM users WHERE name = '" + name + "'"   // ❌ SQL injection
```

**✅ FIX** — bind the parameter.

---

### ❌ Pitfall 2 — Not closing the `ResultSet`/`Statement`/`Connection`

```java
Connection conn = ...;
Statement st = conn.createStatement();
ResultSet rs = st.executeQuery(sql);   // ❌ leaked until the GC runs
```

**✅ FIX** — try-with-resources for all three.

---

### ❌ Pitfall 3 — The close order is wrong

```java
// ❌ closing the Connection first invalidates the Statement and ResultSet
conn.close();
st.close();
rs.close();
```

**✅ FIX** — reverse order: `rs` → `st` → `conn`. try-with-resources does it for you.

---

### ❌ Pitfall 4 — Forgetting `setAutoCommit(false)` for a multi-statement operation

```java
// ❌ each statement is its own transaction — a partial failure leaves inconsistent data
```

**✅ FIX** — `setAutoCommit(false)` … `commit()` / `rollback()`.

---

### ❌ Pitfall 5 — Not rolling back on failure

```java
conn.setAutoCommit(false);
try {
    // … two statements
    conn.commit();
} catch (SQLException e) {
    throw e;                 // ❌ no rollback — the transaction is still OPEN
}
```

**✅ FIX** — `catch { conn.rollback(); throw; }`.

---

### ❌ Pitfall 6 — The N+1 query pattern

```java
for (User u : users) {
    u.setOrders(orderRepo.findByUserId(u.getId()));    // ❌ 1 + N queries
}
```

**✅ FIX** — a JOIN, a batched `IN` query, or a `DataLoader`.

---

### ❌ Pitfall 7 — `getInt` and SQL `NULL`

```java
int discount = rs.getInt("discount");   // ❌ 0 for NULL — indistinguishable from a real 0
```

**✅ FIX** — `rs.getObject("discount", Integer.class)`, or check `rs.wasNull()`.

---

### ❌ Pitfall 8 — `executeUpdate` for a `SELECT`

```java
ps.executeUpdate("SELECT * FROM users");   // ❌ SQLException: query not executed
```

**✅ FIX** — `executeQuery` for a `SELECT`, `executeUpdate` for a DML/DDL.

---

### ❌ Pitfall 9 — 0-based parameter indices

```java
ps.setString(0, value);   // ❌ SQLException: Invalid parameter index — JDBC is 1-BASED
```

**✅ FIX** — start at 1.

---

### ❌ Pitfall 10 — `DriverManager.getConnection` per request

```java
// ❌ a TCP connection + auth handshake per call — ~1–5 ms and a socket each time
```

**✅ FIX** — a `DataSource` with a pool (HikariCP).

---

### ❌ Pitfall 11 — Binding a `LIMIT` in a database that does not support it

```java
ps.setInt(2, limit);   // ❌ Oracle: "invalid character" / a syntax error
```

**✅ FIX** — validate the number and inline it, or use a database-specific syntax.

---

### ❌ Pitfall 12 — Trusting `executeUpdate`'s row count for an `UPDATE`

```java
int n = ps.executeUpdate();   // ⚠ the number of rows CHANGED, not MATCHED
// MySQL by default returns the CHANGED count → 0 when the values are identical
```

**✅ FIX** — do not treat 0 as "not found"; check separately if it matters.

---

### ❌ Pitfall 13 — A huge batch in one transaction

```java
for (int i = 0; i < 1_000_000; i++) { ps.addBatch(); }
ps.executeBatch();   // ❌ one enormous transaction — the DB's transaction log explodes
```

**✅ FIX** — chunk it (500–1,000 rows) and commit per chunk.

---

### ❌ Pitfall 14 — Holding a connection while doing slow work

```java
try (Connection conn = dataSource.getConnection()) {
    // … 2 seconds of computation …
    // … then one query …
}
```

**✅ FIX** — do the slow work **before** acquiring the connection, so the pool is not held.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What are the steps to connect to a database using JDBC?**
**A:** (1) Load/register the driver (automatic since Java 6 via `ServiceLoader`), (2) get a `Connection` from `DriverManager` or a `DataSource`, (3) create a `Statement`/`PreparedStatement`, (4) bind parameters, (5) `executeQuery`/`executeUpdate`, (6) process the `ResultSet`, and (7) close the resources **in reverse order**. In practice, steps 1–2 and 7 are handled by try-with-resources and a connection pool.

**Q: What is the difference between `Statement` and `PreparedStatement`?**
**A:** `PreparedStatement` extends `Statement` and holds SQL with `?` placeholders. It is (a) **safe from SQL injection**, because the parameters are bound as *values* and never parsed as SQL, and (b) **faster** for repeated execution, because the database can cache and reuse the prepared execution plan.

**Q: How do you close JDBC resources properly?**
**A:** Use try-with-resources on the `Connection`, the `Statement` and the `ResultSet`. It closes them in **reverse** order (`ResultSet` → `Statement` → `Connection`), and if a `close()` throws, that exception is **suppressed** rather than replacing the real one.

**Q: What does `executeQuery` return, and what does `executeUpdate` return?**
**A:** `executeQuery` returns a `ResultSet` (for a `SELECT`); `executeUpdate` returns an `int` — the number of rows affected (for `INSERT`/`UPDATE`/`DELETE`/DDL).

### 🟡 Intermediate

**Q: Why is `PreparedStatement` faster than `Statement`?**
**A:** Because the SQL text is sent to the database **once**, parsed, and prepared as an execution plan; the subsequent executions send only the parameter values. The database can therefore **cache the plan** and reuse it — this is the "server-side prepared statement" benefit. With a plain `Statement`, the SQL text (including the interpolated values) is re-parsed on every execution, so a different value means a different SQL text and a different plan.

**Q: Is `PreparedStatement` always safe from SQL injection?**
**A:** **No.** It is safe for **values** — anything you bind with `setXxx` is treated as a value. It is **not** safe for anything you interpolate into the SQL **text**: table and column names, `ORDER BY` columns, and `LIKE` patterns. For identifiers you must use an allow-list; for `LIKE` you bind the value and put the wildcards in the parameter (`"%" + term + "%"`).

**Q: Explain the four transaction isolation levels and the anomalies they prevent.**
**A:** READ UNCOMMITTED allows all three anomalies; READ COMMITTED prevents the dirty read; REPEATABLE READ also prevents the non-repeatable read; SERIALIZABLE prevents the phantom read as well. A **dirty read** is reading uncommitted data; a **non-repeatable read** is re-reading a row and getting a different value; a **phantom read** is re-running a query and getting extra rows. Each level trades isolation for concurrency.

**Q: What is the N+1 problem and how do you fix it?**
**A:** It is the pattern of running one query for a list of entities and then one *additional* query per entity to load its related data — 1 + N round trips. Fix it with a JOIN, a batched `IN` query, a fetch join / `@EntityGraph`, or a `DataLoader` (GraphQL). Detect it with Hibernate's statistics or a datasource proxy such as P6Spy.

**Q: Why is a connection pool needed, and how do you size it?**
**A:** Opening a connection is a TCP handshake plus authentication — milliseconds, and a socket. A pool keeps warm connections and lends them out. Sizing: the pool size is a **throughput** knob, and a bigger pool is often *slower* because the database context-switches between sessions. Start at 10–20 and measure; watch the pool's *pending* count and the `connectionTimeout` as the saturation signal.

**Q: What happens if you forget to `rollback()` before returning a connection to the pool?**
**A:** The transaction stays open on that physical connection, and the next borrower inherits it — so their `commit` may commit *your* uncommitted work, and they may see a stale snapshot. HikariCP resets the transaction state on return, but if you use a bare `DataSource` you must do it yourself: `rollback()` in a `finally`, and restore `autoCommit`.

### 🔴 Advanced / Senior

**Q: How would you diagnose a slow endpoint that uses raw JDBC?**
**A:** In order: (1) **Is it the database or the app?** Compare the application's latency with the DB's own query log — if the DB is fast but the endpoint is slow, the problem is in the app (N+1, chatty SQL, or a missing index). (2) **Count the queries** — enable Hibernate statistics (`getQueryExecutionCount()`) or put a datasource proxy (P6Spy) in place, and look for a count that scales with the result size: that is the N+1 signature. (3) **Check the pool** — a growing *pending* count with `Connection is not available, request timed out` means the pool is saturated, which is usually a symptom of a slow query holding a connection, not a pool that is too small. (4) **Get the execution plan** — `EXPLAIN ANALYZE` the query and look for a sequential scan on a large table, or a sort that could use an index. (5) **Check the transaction scope** — a long transaction holding locks shows up in `pg_locks` / `SHOW PROCESSLIST`.

**Q: How do you make a write path safe under concurrency — say, incrementing a counter without losing updates?**
**A:** Three options, in increasing order of control. (1) **An atomic SQL statement**: `UPDATE counters SET value = value + 1 WHERE id = ?` — the database does the read-modify-write inside a single statement, so it is atomic. This is the best answer: it needs no application-level lock and it scales to the database's capacity. (2) **Optimistic concurrency**: add a `version` column, `UPDATE … SET value = ?, version = version + 1 WHERE id = ? AND version = ?`, and retry on a zero-row update — this detects a conflict rather than preventing it. (3) **Pessimistic locking**: `SELECT … FOR UPDATE` inside a transaction — correct, but it holds locks for the transaction's duration and causes lock contention and deadlocks under load. The engineering point: **push the atomicity into the database** — it is the component that actually owns the data.

**Q: How do you handle a `SELECT` that returns 10 million rows?**
**A:** Never load it into memory. (1) **Stream it** — a `ResultSet` is already a cursor; read it row by row and process incrementally, so the memory footprint is O(1) in rows. (2) **Page it** — add `LIMIT`/`OFFSET` (careful: `OFFSET` is O(offset), so use a **keyset pagination** on an indexed column instead: `WHERE id > ? ORDER BY id LIMIT ?`). (3) **Export it** — for a large export, use the database's own `COPY`/`OUTFILE`, or a streaming writer, and never accumulate in a `List`. (4) **Push the work down** — if the client only needs aggregates, do the aggregation in SQL, not in Java. The anti-pattern to name: `List<X> all = jdbcTemplate.query(sql, rowMapper)` over a huge result — that is an OOM in production.

**Q: What are the trade-offs of batch processing in JDBC, and how do you chunk it correctly?**
**A:** The benefit is round trips: one `executeBatch` instead of N `executeUpdate`s, which is where the 10–100× win comes from on a high-latency link. The costs: (1) the batch is **one transaction** — a huge batch bloats the transaction log and holds locks for the whole time; (2) memory — the driver buffers the parameters, so a 10 M-row batch is a huge in-memory buffer; (3) a partial failure — some drivers stop at the first error, others continue, and the `int[]` result contains `Statement.EXECUTE_FAILED` (-3) for the failures. The correct pattern: chunk at 500–1,000 rows, commit per chunk, and check each count for `EXECUTE_FAILED`. For very large loads, prefer the database's native bulk-load (PostgreSQL's `COPY`, MySQL's `LOAD DATA INFILE`) — orders of magnitude faster.

### ⚫ Expert / Principal

**Q: How would you design the data-access layer for a high-traffic service that must survive a database failover?**
**A:** The design has to assume the connection is a **lossy, re-establishable resource**, not a stable handle. The pieces: (1) **A pool with aggressive validation** — `Connection.isValid()` on borrow, a short `maxLifetime` (so connections are recycled before the DB's idle timeout kills them), and a `keepaliveTime`; HikariCP's `maxLifetime` should be *below* the database's `wait_timeout` or the pool will hand out dead connections. (2) **Fail-fast, with a bounded retry** — a `SQLException` that is a connection error (not a constraint violation) should be retried on a *new* connection, with exponential backoff and jitter, and only for **idempotent** operations. A `DuplicateKeyException` must **never** be retried. (3) **Circuit breaking** — after N consecutive connection failures, open the circuit and fail fast for a period, rather than hammering a dead database. (4) **Transaction boundaries that survive a failover** — never assume a transaction survives; on a failover, the transaction is lost, so the retry must be able to *redo* the whole unit of work, which means the unit of work must be idempotent (or carry a deduplication key). (5) **Observability** — pool saturation, connection-acquisition latency, query latency, and the retry rate are the four metrics that tell you the DB is unhealthy before the users do. The deeper architectural point: **the pool, the retry policy, and the circuit breaker are one system** — designing them separately is how you get a retry storm against a failing database.

**Q: JDBC is often described as legacy. Is it, and what replaced it?**
**A:** JDBC is not legacy in the sense of *obsolete* — it is the **lowest common denominator** that every database vendor implements, and every higher-level tool sits on it. What is legacy is the *hand-written usage pattern* (string SQL, manual resource management, manual transactions). The evolution: **JDBC** (raw, uniform) → **jOOQ** (type-safe SQL from a generated schema model, keeping SQL visible) → **MyBatis** (SQL as the unit of work, mapping handled for you) → **Hibernate/JPA** (an object model, a persistence context, lazy loading, dirty checking) → **Spring Data JDBC** (a middle ground: no dirty checking, no proxies, plain aggregates). The honest trade-off: the higher you go, the more you hide the database — and for a system where the query *is* the business logic, jOOQ or MyBatis is often better than JPA, because the SQL stays visible and reviewable. The trend in 2024+ is a **return to explicit SQL** (jOOQ's growth, Spring Data JDBC's adoption) driven by the N+1 problem, the difficulty of reasoning about lazy loading, and the performance cost of the persistence context. The senior-level conclusion: **choose the level of abstraction that matches how much of your domain logic lives in the database** — and be honest that "use JPA" is often a default rather than a decision.

---

## 9. Counter-Questions & Traps

### CQ1. "If `PreparedStatement` prevents SQL injection, why do injection vulnerabilities still happen every week?"

- **Naive:** "Developers forget to use it."  ← *partly true, and it misses the more common cause*
- **The trap:** The trap is to attribute every injection to a missing `PreparedStatement`. The **most common** cause in modern codebases is not a missing `PreparedStatement` — it is a **`PreparedStatement` used with an interpolated value**. The query is prepared; the injection is in the SQL *text*.
- **Expert answer:** The two dominant patterns. (1) **Identifiers.** `SELECT * FROM " + tableName + " WHERE …` — you cannot bind a table name, so the developer concatenates it, and a `tableName` from a URL parameter becomes an injection point. The fix is an allow-list. (2) **`LIKE` patterns.** `WHERE name LIKE '%" + search + "%'` — the developer wants wildcards *around* the value, so they build the pattern by hand. The fix is to bind the value and put the wildcards in the *parameter*: `LIKE ?` with `"%" + term + "%"`. Beyond those, the systemic causes are: a framework's string-concatenating query builder (`"WHERE name = '" + name + "'"`), an ORM's native-query escape hatch, and — importantly — **a layered defence that was never applied**: even with parameterised queries everywhere, a **read-only database user** and **least privilege** would have contained the blast radius. The professional answer is three-layered: **parameterise → validate identifiers with an allow-list → least-privilege database users.**

### CQ2. "You said `executeUpdate` returns the rows *changed*, not *matched*. Why does that matter?"

- **Naive:** "It doesn't, usually."  ← *and it is a real production bug*
- **The trap:** The trap is to dismiss it. The behaviour differs **by database and by configuration**: MySQL's default `CLIENT_FOUND_ROWS` is **off**, so `UPDATE` returns the number of rows *actually changed*; PostgreSQL always returns the number *matched*; SQL Server has its own semantics. So the same code returns different numbers on different databases.
- **Expert answer:** The concrete failure: an idempotent "update the status to ACTIVE" on a row that is *already* ACTIVE returns **0** on MySQL and **1** on PostgreSQL. A service that does `if (updatedRows == 0) throw new NotFoundException()` will therefore throw a *false* "not found" on MySQL, and work correctly on PostgreSQL — a bug that appears only in production, only on MySQL, and only for idempotent updates. The safe patterns: (1) never infer *existence* from `executeUpdate`'s count — do a separate `SELECT` if you need to know; (2) if you need "rows matched" on MySQL, add `useAffectedRows=false` to the JDBC URL (which flips the flag); (3) if you need "rows changed" everywhere, normalise it explicitly. The general lesson: **JDBC drivers are not perfectly uniform**, and code that depends on a driver's exact return value is not portable.

### CQ3. "Why is `autoCommit = true` the default? Isn't it a footgun?"

- **Naive:** "Convenience for single-statement work."  ← *true, and it misses the design history*
- **The trap:** The trap is to call it a mistake. The default is a **deliberate choice for the common case**: the overwhelming majority of JDBC usage is a single statement (a query, a simple insert), and auto-commit gives exactly that with no ceremony. The footgun only appears in the *multi-statement* case — which is exactly where the developer is doing something unusual enough to notice.
- **Expert answer:** The design reasoning: with auto-commit, "one statement = one transaction" is the default, which is a *safe* default for the simple case and requires no explicit transaction handling. The footgun is the *implicit* multi-statement case — a method that happens to run two statements without anyone thinking "transaction". The mitigation in the modern stack is not to remember `setAutoCommit(false)` at every call site, but to **encapsulate the transaction in the API**: a `@Transactional` annotation (Spring), a `TransactionTemplate`, or a repository method that owns both statements. Then the developer *cannot* accidentally do a multi-statement operation without a transaction, because the unit of work is the method. The general engineering lesson: **the default should be safe, and the safe default should be enforced by the API, not by the developer's memory.**

### CQ4. "You said bigger connection pools are often slower. Isn't that obviously wrong?"

- **Naive:** "More connections = more throughput."  ← *the naive model, and it is wrong*
- **The trap:** The trap is to apply the CPU-parallelism intuition to a *shared, finite* resource. A CPU has N cores that scale linearly. A **database server** has a fixed set of resources — locks, buffers, and, most importantly, the ability to run queries in parallel. When 200 sessions all want the same table, they **contend for the same locks and buffers**, and the DB spends its time context-switching between them.
- **Expert answer:** The mechanism: each database connection is a session with its own memory allocations, and the database's own concurrency limits (a max connections, a `work_mem` budget, a lock manager) mean that past a certain point, additional sessions *reduce* throughput. The famous formula — `connections = (cores × 2) + effective_spindles` — is a *starting point*, not a target. The practical guidance: (1) start at 10–20; (2) measure the **pool's pending count** and the **connection-acquisition latency** — a growing pending queue with a `connectionTimeout` error means the pool is saturated; (3) if the DB's CPU is not saturated but the pool is, the queries are slow — fix the queries, not the pool; (4) if the DB's CPU *is* saturated, a bigger pool makes it worse. The deeper insight: **the pool size is a latency/throughput knob for your service, and the database's capacity is the real ceiling** — optimising the pool without optimising the queries is treating the symptom.

### CQ5. "Why not use an ORM instead of raw JDBC? Isn't the ORM the modern answer?"

- **Naive:** "ORMs are simpler and faster to write."  ← *true, and it misses the trade-off*
- **The trap:** The trap is to treat the ORM as a strict improvement. The trade is **impedance**: an ORM gives you an object model, a persistence context, lazy loading, and dirty checking — and in exchange it hides the SQL, makes the query plan unpredictable, and introduces the N+1 problem, the "open session in view" anti-pattern, and a whole layer of proxies.
- **Expert answer:** The honest 2024+ position: the industry has moved **back toward explicit SQL** for a specific reason — the N+1 problem and the difficulty of reasoning about lazy loading. The tool landscape reflects this: **jOOQ** (type-safe SQL, the schema is generated, the SQL stays visible), **MyBatis** (SQL as the unit of work), **Spring Data JDBC** (plain aggregates, no dirty checking, no proxies), and **Hibernate** (still the most capable, and the most complex). The decision rule: (1) if the query *is* the business logic (reports, complex joins, window functions) — use **jOOQ or plain SQL**; (2) if the domain is a rich aggregate with invariants and the DB is a persistence detail — **Hibernate**; (3) if you want simplicity without the magic — **Spring Data JDBC**. The anti-pattern to name: reaching for JPA on a reporting workload, and then fighting lazy loading and the N+1 in production. The engineering lesson: **abstraction has a price, and the price is paid in the layer you cannot see.**

### CQ6. "You said use `getObject(name, Integer.class)` instead of `getInt` + `wasNull`. Isn't that slower?"

- **Naive:** "Yes, boxing costs more."  ← *true, and irrelevant in context*
- **The trap:** The trap is to compare the two on micro-performance. The comparison that matters is **correctness**: `getInt` **silently converts SQL `NULL` to `0`**, and if `0` is a legitimate value, the two are indistinguishable without a second call. A silently-wrong value is far more expensive than a few nanoseconds of boxing.
- **Expert answer:** The precise cost: `getObject(name, Integer.class)` does the same JDBC type conversion and then boxes — one small allocation (and `Integer.valueOf` caches −128..127, so a common case is allocation-free). The `getInt` + `wasNull()` path is *also* correct but is two calls, and it is easy to get wrong: `wasNull()` reports on the **most recent** getter, so a `getString` between the `getInt` and the `wasNull` silently invalidates the check. That is a subtle, real bug. The rule: **use the typed `getObject` for nullable columns**; use the primitive getters only where the column is `NOT NULL`. And note the "correct" answer has a performance cousin: for a large result set, the `getObject(name, Type.class)` path allocates a box per row — so on a hot path over millions of rows, `getInt` + `wasNull()` (or a `NULL`-safe SQL expression like `COALESCE(col, 0)`) can genuinely be faster. The expert answer names both: correctness first, and measure before you optimise the boxing.

### CQ7. "Why is `LIMIT ?` sometimes a problem? Isn't it just another parameter?"

- **Naive:** "It works everywhere."  ← *false for Oracle, and for a subtler reason elsewhere*
- **The trap:** The trap is that it *usually* works — PostgreSQL, MySQL, and SQL Server all support a bound `LIMIT`/`OFFSET`. So the bug only appears when you deploy on Oracle, or when the *driver* refuses it. And the subtler issue: some drivers and some database versions will not use a **prepared plan** for a parameterised `LIMIT` — the plan is re-generated per execution, which defeats the whole point of preparing the statement.
- **Expert answer:** The precise facts: (1) **Oracle** does not support a bound `LIMIT` in a `PreparedStatement` — the value must be inlined, and the safe way is to validate it as a non-negative `int` and inline it (never a string). (2) Some **PostgreSQL** configurations (and older MySQL versions) will not use a generic plan for a parameterised `LIMIT`, so the "prepare once, execute many" benefit is lost. (3) `LIMIT` in a **subquery** of a `MERGE`-style statement is restricted in several databases. The general rule: **the parameters you bind should be *values*, and the things that vary the *plan* (like a `LIMIT`) are sometimes better inlined after validation.** The interview framing: "bound parameters are for data; identifiers and plan-shaping fragments need validation."

### CQ8. "You said the N+1 problem is the most common JDBC performance bug. But isn't it an ORM problem, not a JDBC problem?"

- **Naive:** "It's an ORM problem."  ← *it is, and it is also a hand-written-JDBC problem*
- **The trap:** The trap is to blame the ORM. Hand-written JDBC has the identical problem — and it is arguably easier to write, because a `for` loop with a query inside is completely natural. The ORM *creates* the illusion that you are not writing N+1, because the query is hidden inside a getter.
- **Expert answer:** The important observation: **N+1 is a shape, not an API.** The shape is: *a loop over a collection, with a query inside the loop*. You can write it in raw JDBC, in JPA, in MyBatis, or in a repository method that calls itself. The ORM's contribution is that it makes the shape *invisible* — the second query is hidden behind `user.getOrders()`. That is why the detection tooling is ORM-specific (Hibernate statistics) while the *fix* is universal: reduce the number of round trips, whatever the source. And the deeper lesson: **an abstraction that hides the queries hides the cost model too** — which is why a "simple" repository method can be 1,000 queries, and why the best defence is *observability* (count the queries per request) rather than a rule about which library to use.

### CQ9. "Why do you say `try-with-resources` closes them in reverse order? Why not in the order they were created?"

- **Naive:** "Because that is what the spec says."  ← *true, and it misses the reason*
- **The trap:** The trap is to treat the order as an arbitrary convention. It is a **dependency** relationship: the `ResultSet` is *owned* by the `Statement`, and the `Statement` is *owned* by the `Connection`. Closing an owner must come **after** closing what it owns.
- **Expert answer:** The precise semantics: a `Connection` "closes" its `Statement`s, and a `Statement` "closes" its `ResultSet`s — the JDBC spec defines this cascade. So the correct order is the reverse of creation, and try-with-resources gives it to you for free. The **second** benefit is more important and less known: if a `close()` throws, try-with-resources **suppresses** that exception and attaches it to the primary one via `getSuppressed()`. The old `finally { rs.close(); st.close(); conn.close(); }` pattern has a devastating failure mode: if `rs.close()` throws, `st.close()` and `conn.close()` **never run** — so a cursor leak turns into a connection leak. That is the real reason the idiom exists, and it is a great interview point.

### CQ10. "If the pool manages the connections, do I still need try-with-resources? Why not just get a connection and use it?"

- **Naive:** "The pool handles closing."  ← *it does not — the pool only recycles*
- **The trap:** The trap is to think the pool closes your `Statement` and `ResultSet`. It does not. A pooled `Connection` returned to the pool is *reset* — the pool rolls back any open transaction and resets auto-commit — but your `Statement` and `ResultSet` are **your** responsibility. Leaving them open leaks a cursor on the server and, in some drivers, holds a client-side buffer.
- **Expert answer:** The division of responsibility: the **pool** owns the *physical connection* — it validates it, recycles it, resets the transaction state, and evicts it when it goes bad. **You** own the *logical resources* — the `Statement` and the `ResultSet` — and you must close them. The consequence of getting this wrong is subtle and expensive: a leaked `Statement` keeps a server-side cursor open, which on PostgreSQL holds a transaction open, which holds locks, which blocks other writers — the classic "a single unclosed statement is blocking the whole table" incident. The right rule: **try-with-resources for `Statement` and `ResultSet` always; take the `Connection` from the pool and let it be closed by try-with-resources too** (which returns it to the pool). The one place you deliberately keep a connection open for longer is a **transaction** spanning several repository calls — and that is exactly where you must be careful to `commit`/`rollback` before returning it.

---

## 10. Rapid-Fire MCQs

**Q1.** JDBC stands for…
- A) Java Database Connector  B) Java Database Connectivity  C) Java Data Binding  D) Java Driver Class

**Q2.** Which interface is the entry point for a JDBC connection?
- A) `Statement`  B) `DataSource` / `DriverManager`  C) `ResultSet`  D) `DatabaseMetaData`

**Q3.** The JDBC parameter index of the first placeholder is…
- A) 0  B) 1  C) 2  D) It depends on the driver

**Q4.** `PreparedStatement` extends…
- A) `ResultSet`  B) `Statement`  C) `Connection`  D) `CallableStatement`

**Q5.** `executeQuery` returns…
- A) An `int`  B) A `ResultSet`  C) A `boolean`  D) `null`

**Q6.** `executeUpdate` returns…
- A) A `ResultSet`  B) The affected-row count  C) A `boolean`  D) The generated key

**Q7.** Resources must be closed in which order?
- A) Creation order  B) Reverse creation order  C) Alphabetical  D) Any order

**Q8.** try-with-resources closes resources in which order?
- A) Creation order  B) Reverse creation order  C) In parallel  D) It does not close them

**Q9.** If a `close()` throws inside try-with-resources, the exception is…
- A) Swallowed  B) Thrown, replacing the primary one  C) **Suppressed** and attached to the primary  D) Retried

**Q10.** `autoCommit` is `true` by default, meaning…
- A) Nothing happens automatically  B) Each statement is its own transaction  C) The connection is auto-closed  D) The driver retries

**Q11.** To use a transaction manually you must call…
- A) `setAutoCommit(true)`  B) `setAutoCommit(false)`  C) `conn.setTransaction()`  D) `conn.begin()`

**Q12.** The four isolation levels, from least to most strict, are…
- A) READ COMMITTED, READ UNCOMMITTED, REPEATABLE READ, SERIALIZABLE  B) READ UNCOMMITTED, READ COMMITTED, REPEATABLE READ, SERIALIZABLE  C) SERIALIZABLE, REPEATABLE READ, READ COMMITTED, READ UNCOMMITTED  D) READ UNCOMMITTED, REPEATABLE READ, READ COMMITTED, SERIALIZABLE

**Q13.** Which level prevents a **dirty read**?
- A) Only SERIALIZABLE  B) READ COMMITTED and above  C) Only REPEATABLE READ  D) None — it is unavoidable

**Q14.** Which level prevents a **phantom read**?
- A) READ COMMITTED  B) REPEATABLE READ  C) SERIALIZABLE  D) READ UNCOMMITTED

**Q15.** MySQL InnoDB's default isolation level is…
- A) READ UNCOMMITTED  B) READ COMMITTED  C) REPEATABLE READ  D) SERIALIZABLE

**Q16.** PostgreSQL's default isolation level is…
- A) READ UNCOMMITTED  B) READ COMMITTED  C) REPEATABLE READ  D) SERIALIZABLE

**Q17.** `getInt` on a SQL `NULL` returns…
- A) `null`  B) `0`  C) Throws  D) `-1`

**Q18.** To distinguish a SQL `NULL` from a real `0`, you use…
- A) `rs.wasNull()`  B) `rs.isNull()`  C) `rs.getObject(name, Integer.class)`  D) Both A and C

**Q19.** The N+1 problem means…
- A) 1 query for the list + 1 per row  B) N queries for one row  C) N+1 connection leaks  D) A deadlock

**Q20.** `addBatch()` + `executeBatch()` improves performance by…
- A) Reducing the number of round trips  B) Using a faster SQL parser  C) Skipping validation  D) Increasing the cache

**Q21.** `ResultSet.next()` returns `false` when…
- A) The connection is closed  B) There are no more rows  C) The query failed  D) The result is empty

**Q22.** A `ResultSet` is by default…
- A) Scrollable and updatable  B) Forward-only and read-only  C) Random-access  D) Cached in memory

**Q23.** `getGeneratedKeys()` requires…
- A) `Statement.RETURN_GENERATED_KEYS`  B) `conn.setAutoCommit(false)`  C) A scrollable `ResultSet`  D) Nothing special

**Q24.** The safest way to handle a table name that comes from user input is…
- A) Concatenate it  B) Bind it as a parameter  C) Validate it against an allow-list  D) `Pattern.quote` it

**Q25.** The production replacement for `DriverManager.getConnection` per request is…
- A) A `DataSource` with a connection pool  B) A static `Connection`  C) `DataSource.getConnection` without a pool  D) A `CallableStatement`

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | Java Database Connectivity. |
| 2 | **B** | `DataSource` (pooled) or `DriverManager` — both return a `Connection`. |
| 3 | **B** | JDBC parameter indices are **1-based**. |
| 4 | **B** | `PreparedStatement extends Statement`. |
| 5 | **B** | A `ResultSet` — for a `SELECT`. |
| 6 | **B** | The affected-row count — for DML/DDL. |
| 7 | **B** | Reverse creation order — a `ResultSet` belongs to a `Statement`, which belongs to a `Connection`. |
| 8 | **B** | try-with-resources closes in reverse, automatically. |
| 9 | **C** | The close failure is **suppressed** and attached via `getSuppressed()`. |
| 10 | **B** | Each statement is its own transaction — the source of the partial-commit bug. |
| 11 | **B** | `setAutoCommit(false)`, then `commit()` / `rollback()`. |
| 12 | **B** | READ UNCOMMITTED → READ COMMITTED → REPEATABLE READ → SERIALIZABLE. |
| 13 | **B** | READ COMMITTED and above prevent a dirty read. |
| 14 | **C** | Only SERIALIZABLE prevents a phantom read. |
| 15 | **C** | MySQL InnoDB defaults to REPEATABLE READ (and uses gap locks). |
| 16 | **B** | PostgreSQL defaults to READ COMMITTED. |
| 17 | **B** | `0` — which is why `wasNull()` exists. |
| 18 | **D** | Either `wasNull()` (immediately after) or the null-safe typed `getObject`. |
| 19 | **A** | 1 query for the collection + 1 per element. |
| 20 | **A** | It collapses N round trips into one. |
| 21 | **B** | `false` means the cursor is exhausted. |
| 22 | **B** | `TYPE_FORWARD_ONLY`, `CONCUR_READ_ONLY`. |
| 23 | **A** | `prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)`. |
| 24 | **C** | An allow-list — identifiers cannot be bound. |
| 25 | **A** | A pooled `DataSource` (HikariCP) — a connection per request is far too expensive. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                        THE 6 STEPS — THE CANONICAL FLOW                │
│  1. conn = dataSource.getConnection()      ← FROM A POOL               │
│  2. ps   = conn.prepareStatement(sql)      ← WITH ? PLACEHOLDERS      │
│  3. ps.setXxx(1, value)                    ← 1-BASED INDEX            │
│  4. rs   = ps.executeQuery()                ← OR executeUpdate()       │
│  5. while (rs.next()) { rs.getX("col"); }   ← THE CURSOR              │
│  6. close: rs → ps → conn                  ← try-with-resources      │
│                                                                          │
│  ⚠ NEVER DriverManager.getConnection() in a request path               │
│  ⚠ ALWAYS bind parameters; NEVER concatenate values                     │
│  ⚠ autoCommit=false + commit/rollback for multi-statement work          │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 4 ISOLATION LEVELS                               │
├──────────────────┬────────┬─────────────┬──────────────┬────────────────┤
│ LEVEL            │ DIRTY  │ NON-REPEAT. │ PHANTOM      │ CONCURRENCY   │
├──────────────────┼────────┼─────────────┼──────────────┼────────────────┤
│ READ UNCOMMITTED │  YES   │    YES      │    YES       │ HIGHEST       │
│ READ COMMITTED   │  no    │    YES      │    YES       │ HIGH          │
│ REPEATABLE READ  │  no    │    no       │    YES       │ MEDIUM        │
│ SERIALIZABLE     │  no    │    no       │    no        │ LOWEST        │
├──────────────────┴────────┴─────────────┴──────────────┴────────────────┤
│ DEFAULTS: PostgreSQL = READ COMMITTED · MySQL InnoDB = REPEATABLE READ  │
│ ⚠ MySQL's REPEATABLE READ uses GAP LOCKS → can deadlock on inserts     │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                       THE 8 JDBC TRAPS                                 │
├──────────────────────────────────────────────────────────────────────────┤
│  1. CONCATENATED SQL              → SQL injection                      │
│  2. LEAKED ResultSet/Statement    → server-side cursor leak            │
│  3. WRONG CLOSE ORDER             → a failed close skips the rest       │
│  4. autoCommit=true + 2 statements→ a partial commit 💥                │
│  5. NO rollback() ON FAILURE      → the transaction stays open          │
│  6. THE N+1 PATTERN               → 1 + N round trips                   │
│  7. getInt() on SQL NULL          → 0, silently                        │
│  8. DriverManager per request     → a socket + handshake per call      │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE TOOL CHOICE — 2024+                              │
│  jOOQ        → type-safe SQL, the schema is generated, SQL stays visible │
│  MyBatis     → SQL as the unit of work, mapping handled for you         │
│  Hibernate   → a full object model, a persistence context, lazy loading  │
│  Spring Data JDBC → plain aggregates, no dirty checking, no proxies    │
│                                                                          │
│  RULE: if the QUERY is the business logic → jOOQ / plain SQL            │
│        if the DOMAIN is a rich aggregate   → Hibernate                 │
│        if you want simplicity              → Spring Data JDBC          │
│  ⚠ Always COUNT the queries per request — that is the real metric      │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The production rules

| Rule | Why |
|---|---|
| Always use a **pooled `DataSource`** | A socket + handshake per call is far too expensive |
| Always use **`PreparedStatement`** | Injection safety and plan caching |
| Always use **try-with-resources** | Correct close order + suppressed exceptions |
| Always **count the queries per request** | The N+1 is invisible otherwise |
| Always set a **transaction timeout** | A stuck transaction holds locks |
| Always **rollback in a catch**, restore `autoCommit` in a `finally` | A pooled connection must not carry an open transaction |
| Always **validate identifiers** with an allow-list | They cannot be bound |
| Always use a **read-only DB user** for reports | Defence in depth against injection |
| Always **log the slow-query threshold** | `spring.datasource.hikari.connection-timeout`, and the DB's own slow-query log |

### HikariCP configuration that matters

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db:5432/app
    hikari:
      maximum-pool-size: 20        # start SMALL and measure
      minimum-idle: 5              # keep a warm floor
      connection-timeout: 30000    # ⚠ the saturation signal
      max-lifetime: 900000         # < the DB's wait_timeout, or you get dead connections
      keepalive-time: 120000       # keep the pool's connections alive
      leak-detection-threshold: 60000   # ⚠ logs a stack trace if a connection is held too long
      pool-name: app-pool          # shows up in the metrics and thread names
```

**`leak-detection-threshold` is the most under-used setting** — it logs the stack trace of
the code that acquired a connection and held it too long. That is how you find the N+1,
the "connection held during slow computation", and the "connection not closed" bugs.

### Metrics worth watching

| Metric | Why |
|---|---|
| Pool **active** connections | Headroom |
| Pool **pending** count | ⚠ Saturation — the pool is too small or a query is slow |
| **Connection-acquisition latency** | The real "pool wait" metric |
| **Queries per request** | The N+1 detector |
| **Slow-query count and duration** | The query that needs an index |
| **Transaction duration** | A long transaction holds locks |
| **Deadlock/timeout rate** | Lock contention and bad SQL |

### The troubleshooting decision tree

```text
   "The endpoint is slow"
        │
        ├─ Is the DB's CPU saturated?
        │     YES → the queries are too expensive → EXPLAIN + indexes
        │     NO  → continue
        │
        ├─ Are the queries per request proportional to the result size?
        │     YES → N+1 → batch/join/fetch-join
        │     NO  → continue
        │
        ├─ Is the pool's pending count high?
        │     YES → the pool is saturated (or a query is slow)
        │     NO  → continue
        │
        ├─ Is a single query slow?
        │     YES → EXPLAIN ANALYZE → missing index / bad plan
        │     NO  → the time is elsewhere (serialisation, the network, GC)
        │
        └─ → Enable a datasource proxy (P6Spy) and look at the ACTUAL query log.
```

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| Connection pools and `ThreadPoolExecutor` | [08 · Executor Framework](../08-Multithreading-Concurrency/02-Executor-Framework.md) |
| Transactions in Spring | `SPRING_SPRINGBOOT/` |
| SQL, indexing, and the query planner | `DATABASE_CACHING/` |
| The `ResultSet` and streams | [06 · Stream API](../06-Java-8-Features/02-Stream-API.md) |
| The JDBC spec | The `java.sql` package in the JDK; the JDBC 4.2 specification |

---

**Next Chapter:** → [17 · Serialization](../17-Serialization/01-Serialization-Basics.md)
**Previous Chapter:** ← [15 · I/O & NIO](../15-IO-NIO/01-IO-NIO.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
