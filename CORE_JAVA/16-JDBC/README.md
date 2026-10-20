# 16 — JDBC · Interview Notes Index

> JDBC is the layer every backend interview eventually touches — because *database access*
> is where real production bugs (leaks, N+1, SQL injection, transaction bugs) live.

| Priority | Chapter | Why It Matters |
|----------|---------|----------------|
| ⭐⭐⭐⭐ | [01 · JDBC Basics](01-JDBC-Basics.md) | 6-step flow, `PreparedStatement`, transactions, isolation levels, pooling |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| Why `PreparedStatement` over `Statement`? | Parameter binding prevents SQL injection **and** lets the DB cache the execution plan. |
| Is `PreparedStatement` always injection-safe? | **No** — if you build the SQL by concatenating a parameter into the *query text* (table/column names, `LIKE` patterns). |
| What are the 4 transaction isolation levels? | READ UNCOMMITTED, READ COMMITTED, REPEATABLE READ, SERIALIZABLE — each trades isolation for concurrency. |
| What is a dirty read? | Reading uncommitted data from another transaction. |
| What does `autocommit=false` + `commit()` give you? | A transaction boundary — atomicity across multiple statements. |
| Why do we need connection pooling? | Opening a TCP+auth round trip per query is ~ms-scale; pools keep connections warm. |
| What leaks in a JDBC app? | Unclosed `ResultSet`/`Statement`/`Connection` → pool exhaustion. Always try-with-resources. |
| What is the N+1 problem? | One query for a list, then one query per row → use a JOIN or a batch IN-query. |
| `execute` vs `executeQuery` vs `executeUpdate`? | `execute` is the general one; the others are typed shortcuts. |

---

## 🧠 The 6-Step Flow

```
1. Load driver      Class.forName("...")     [optional since JDBC 4.0 — ServiceLoader]
2. Get connection   DriverManager.getConnection(url, user, pass)
3. Create statement conn.prepareStatement(sql)
4. Bind & execute   ps.setString(1, val); ps.executeQuery()
5. Process results  while (rs.next()) { rs.getString("col"); }
6. Close (reversed) try-with-resources: rs → stmt → conn
```

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **Isolation levels + the anomalies each prevents** — dirty read, non-repeatable read, phantom read.
- [ ] **Resource close order** — reverse of creation; try-with-resources gives it to you free.
- [ ] **Batch operations** — `addBatch()`/`executeBatch()` for bulk inserts.
- [ ] **Generated keys** — `RETURN_GENERATED_KEYS`.
- [ ] **HikariCP** — `maximumPoolSize` sizing, `leakDetectionThreshold`, `connectionTimeout`.
- [ ] **Why `DriverManager` is not used in Spring** — Spring uses `DataSource` directly.

---

**Related folders:** → [17 · Serialization](../17-Serialization/README.md) · ← [15 · I/O & NIO](../15-IO-NIO/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
