# 01 · SQL Fundamentals, Data Types, Constraints & CRUD

> **Level:** Beginner → Intermediate (the non-negotiable foundation)  
> **Interview Frequency:** ★★★★★ (Screened for in every loop, from intern to principal)  
> **Target Databases:** PostgreSQL 15+ & MySQL 8.0+ (differences are always flagged)  
> **Practice Schema:** [`practice-schema.sql`](./practice-schema.sql) — run this file first, every example below works against it  
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Five SQL Sublanguages](#1-what--why--the-five-sql-sublanguages)
- [2. Mental Model — Tables, Rows, Keys & Relations](#2-mental-model--tables-rows-keys--relations)
- [3. Data Types — The Complete PostgreSQL / MySQL Map](#3-data-types--the-complete-postgresql--mysql-map)
- [4. Constraints & Data Integrity](#4-constraints--data-integrity)
- [5. CRUD — The Complete Read & Write Playbook](#5-crud--the-complete-read--write-playbook)
- [6. Filtering — The `WHERE` Clause Deep Dive](#6-filtering--the-where-clause-deep-dive)
- [7. Expressions, Functions & Conditional Logic](#7-expressions-functions--conditional-logic)
- [8. Sorting, Pagination & Deduplication](#8-sorting-pagination--deduplication)
- [9. NULL Semantics — The #1 Interview Trap](#9-null-semantics--the-1-interview-trap)
- [10. Views, Sequences, Identity Columns & CTAS](#10-views-sequences-identity-columns--ctas)
- [11. SQL Style & Production Anti-Patterns](#11-sql-style--production-anti-patterns)
- [12. Level-Wise Interview Questions & Answers](#12-level-wise-interview-questions--answers)
- [13. Counter-Questions & Interviewer Traps](#13-counter-questions--interviewer-traps)
- [14. Rapid-Fire MCQs](#14-rapid-fire-mcqs)
- [15. One-Page Cheat Sheet](#15-one-page-cheat-sheet)
- [16. Real-World Production Post-Mortems](#16-real-world-production-post-mortems)
- [17. Follow-up Learning](#17-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

1. **SQL has five sublanguages:** `DDL` (define), `DQL` (query), `DML` (modify), `DCL` (grant), `TCL` (commit/rollback). Interviewers test which bucket `TRUNCATE`, `MERGE`, and `CREATE INDEX` belong to.
2. **`DELETE` is DML, `TRUNCATE` is DDL.** `TRUNCATE` cannot be rolled back in MySQL, fires no `ON DELETE` triggers, cannot have a `WHERE` clause, and is far faster because it deallocates whole pages instead of logging each row.
3. **Constraints are the only real boundary enforcement.** Application-level validation is a convenience; `NOT NULL`, `UNIQUE`, `PRIMARY KEY`, `FOREIGN KEY`, and `CHECK` are the database's contract. Every production incident traceable to "bad data" is a missing constraint.
4. **Never use `FLOAT`/`DOUBLE` for money.** IEEE-754 cannot represent 0.1 exactly. Use `NUMERIC(p, s)` / `DECIMAL(p, s)`.
5. **NULL is not zero, empty string, or "false".** NULL means *unknown*, and SQL uses **three-valued logic** (`TRUE`, `FALSE`, `UNKNOWN`). Every `WHERE` clause discards `UNKNOWN` — which is why `NOT IN` over a set containing a single NULL returns zero rows.
6. **Upserts differ per engine:** PostgreSQL uses `INSERT ... ON CONFLICT (...) DO UPDATE`, MySQL uses `INSERT ... ON DUPLICATE KEY UPDATE`. MySQL's `REPLACE INTO` is a `DELETE` + `INSERT` and will fire both triggers.
7. **`LIMIT`/`OFFSET` is the wrong pagination for deep pages.** `OFFSET 100000` still *scans and discards* 100,000 rows. Use keyset (seek) pagination — taught fully in [Chapter 05](./05-Advanced-SQL-Window-Functions-and-CTEs.md).
8. **A `DELETE`/`UPDATE` without `WHERE` is a one-row disaster at intern scale and a full-table disaster at company scale.** Always `BEGIN; ... SELECT ...;` first.
9. **Always qualify columns** (`e.dept_id`, not `dept_id`) and never `SELECT *` in application code — column order changes, `SELECT *` defeats covering indexes, and wildcard projections block index-only scans.
10. **DDL is auto-committing in MySQL and transactional in PostgreSQL.** PostgreSQL can roll back a failed `ALTER TABLE`; MySQL DDL causes an implicit commit.

---

## 1. What & Why — The Five SQL Sublanguages

SQL is not one language — it is five, and interviewers use the classification to test depth. The ANSI/ISO standard defines them as:

| Sublanguage | Full name | Purpose | Representative statements |
|---|---|---|---|
| **DDL** | Data Definition Language | Define / alter the *structure* of stored objects | `CREATE`, `ALTER`, `DROP`, `TRUNCATE`, `RENAME`, `COMMENT` |
| **DQL** | Data Query Language | *Only* retrieval — no data change | `SELECT` (and `WITH ... SELECT`) |
| **DML** | Data Manipulation Language | Manipulate the *contents* of tables | `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `CALL`, `EXPLAIN PLAN`, `LOCK TABLE` |
| **DCL** | Data Control Language | Grant / revoke authority | `GRANT`, `REVOKE` (PostgreSQL/MySQL) |
| **TCL** | Transaction Control Language | Group DML into atomic units | `COMMIT`, `ROLLBACK`, `SAVEPOINT`, `RELEASE`, `SET TRANSACTION` |

> **Interview note:** `SELECT` is often grouped under DML in textbooks ("DML = read + write"). The correct and modern answer is: **DQL is retrieval-only; DML changes rows.** If the interviewer says "DML includes SELECT", agree that some vendors/textbooks lump it, then explain the distinction — that is the senior answer.

### The three that actually trip people up

```sql
-- 1) TRUNCATE is DDL, not DML
TRUNCATE TABLE orders RESTART IDENTITY CASCADE;
--   -> cannot have WHERE, cannot fire ON DELETE triggers, no per-row undo,
--      deallocates whole pages. Fastest possible "empty this table".

-- 2) MERGE is DML with a conditional branch (a single atomic upsert)
MERGE INTO accounts a
USING transactions t ON a.account_id = t.account_id
WHEN MATCHED   THEN UPDATE SET a.balance = a.balance + t.amount
WHEN NOT MATCHED THEN INSERT (account_id, balance) VALUES (t.account_id, t.amount);

-- 3) CREATE INDEX is DDL and (in MySQL) implicitly commits your open transaction
CREATE INDEX idx_orders_customer ON orders (customer_id);
```

---

## 2. Mental Model — Tables, Rows, Keys & Relations

Before writing a single query you must be able to speak the relational model fluently. Interviewers frequently open with vocabulary.

### 2.1 Core vocabulary

| Relational term | SQL term | Meaning |
|---|---|---|
| **Relation** | Table | A set of tuples with a fixed set of attributes |
| **Tuple** | Row | One record; **no intrinsic ordering** |
| **Attribute** | Column | Named, typed, ordered-by-declaration domain |
| **Degree** | — | Number of attributes (columns) |
| **Cardinality** | — | Number of tuples (rows) currently in the relation |
| **Domain** | Data type + `CHECK` | The set of legal values for an attribute |
| **View** | Virtual relation | A named, stored query — no rows of its own (a materialized view does cache them) |

> **Interview gem:** "Is a table ordered?" **No.** The rows of a table form a *set*. Any order you observe is an accident of storage, an index scan, or a plan choice — never a guarantee. `ORDER BY` is the only contract, and without a *deterministic* total ordering (unique tiebreaker) even `ORDER BY` does not guarantee a stable order across executions.

### 2.2 Keys — the complete taxonomy

| Key | Definition | Example on our schema |
|---|---|---|
| **Super key** | Any set of columns that uniquely identifies a row | `(email)`, `(email, salary)`, `(employee_id, hire_date)` |
| **Candidate key** | A *minimal* super key | `employees.email`, `(first_name, last_name)` |
| **Primary key** | The *chosen* candidate key — `NOT NULL` + `UNIQUE`, one per table | `employees.employee_id` |
| **Alternate key** | A candidate key that was not chosen as PK | `employees.email` |
| **Natural key** | A key with real-world meaning | `email`, `isbn`, `pan_number`, `country_code` |
| **Surrogate key** | A meaningless, system-generated key | `employee_id` (identity/serial/sequence) |
| **Composite key** | A key made of two or more columns | `order_items(order_id, product_id)` |
| **Foreign key** | A column (or set) referencing a candidate key in another table | `employees.dept_id → departments.dept_id` |
| **Unique key** | Enforced uniqueness, still allows **multiple NULLs** | `customers.email UNIQUE` |

**Natural vs surrogate — the senior answer:** prefer **surrogate** keys (immutable, engine-friendly, short) for internal joins and relationships; keep the natural key as a `UNIQUE` constraint for business semantics. Natural keys can be re-assigned (think of a phone number being recycled, or an email being changed by a user), and joining on long natural keys bloats secondary indexes.

### 2.3 Relationships and cardinality notation

```
departments 1 ────< employees        One-to-Many   (a dept has many employees)
employees   1 ────< employees        Self / One-to-Many (manager relationship)
customers   1 ────< orders           One-to-Many
orders      N ────> products         Many-to-Many, realized via a BRIDGE table
       (order_items)
```

Rules of thumb:
- **1:1** is rare and usually a vertical partition for performance or security (rarely-hot columns split off).
- **1:N** is the normal case; the FK always lives on the "many" (child) side.
- **N:M** **must** use a bridge/associative table (`order_items`); a comma-separated list column is 1NF violation and cannot be joined.

---

## 3. Data Types — The Complete PostgreSQL / MySQL Map

The single most common cause of "the query is slow" or "the number is wrong" in code review is a wrong data type. A wrongly typed column **cannot be indexed efficiently**, cannot be compared correctly, and silently corrupts arithmetic.

### 3.1 The comparison map

| Family | PostgreSQL | MySQL 8.0+ | Interview-critical notes |
|---|---|---|---|
| **Small int** | `SMALLINT` (2B) | `SMALLINT` (2B) | |
| **Integer** | `INTEGER`/`INT` (4B) | `INT` (4B) | `INT UNSIGNED` doubles the ceiling |
| **Big int** | `BIGINT` (8B) | `BIGINT` (8B) | Default for all surrogate IDs |
| **Auto-incr.** | `GENERATED ALWAYS AS IDENTITY` / `SERIAL` | `AUTO_INCREMENT` | `SERIAL` creates a sequence + default; prefer `IDENTITY` in PG 10+ |
| **Exact decimal** | `NUMERIC(p,s)` / `DECIMAL(p,s)` | `DECIMAL(p,s)` / `NUMERIC(p,s)` | **The only correct money type** |
| **Approximate** | `REAL`, `DOUBLE PRECISION` | `FLOAT`, `DOUBLE` | Never for money; fine for scientific |
| **Money shortcut** | `MONEY` (deprecated practice) | `DECIMAL` | Avoid `MONEY` — it carries locale/rounding baggage |
| **Fixed char** | `CHAR(n)` / `BPCHAR(n)` | `CHAR(n)` | Space-padded; right for fixed-width codes (`CHAR(2)` country) |
| **Variable char** | `VARCHAR(n)` / `TEXT` | `VARCHAR(n)` / `TEXT` | Same row-size math in PG 16+ (no TOAST penalty difference) |
| **Unbounded text** | `TEXT` | `TEXT` / `TINYTEXT..LONGTEXT` | PG `TEXT` and `VARCHAR` are effectively identical |
| **Boolean** | `BOOLEAN` (`TRUE/FALSE`, `t/f`, `1/0`) | `BOOLEAN` (alias for `TINYINT(1)`) | PG indexes booleans efficiently with partial indexes |
| **Date** | `DATE` | `DATE` | No time component |
| **Time** | `TIME [WITHOUT/WITH TIME ZONE]` | `TIME` | |
| **Timestamp (no TZ)** | `TIMESTAMP` (naive) | `DATETIME` | **Avoid for global systems** — no zone awareness |
| **Timestamp (with TZ)** | `TIMESTAMPTZ` (stores UTC, renders in session TZ) | `TIMESTAMP` (stores UTC, converts on session) | **Default choice** |
| **Interval** | `INTERVAL` | via `TIMESTAMPDIFF()` | |
| **UUID** | `UUID` (16 bytes) | `BINARY(16)` / `CHAR(36)` | Prefer **UUIDv7** (time-ordered) not v4 — see [Chapter 08](./08-Schema-Design-Modeling-and-Migrations.md) |
| **JSON** | `JSON` (text, validated) / `JSONB` (binary, indexable) | `JSON` (binary internally) | **PG: prefer `JSONB`** — smaller, faster, GIN-indexable |
| **Array** | `INT[]`, `TEXT[]` | *(none)* | MySQL has no array type; use a child table |
| **Enum** | `ENUM` (custom type) or `CHECK` | `ENUM('a','b')` | Adding values to MySQL `ENUM` is a table rebuild |
| **Binary** | `BYTEA` | `BLOB` / `VARBINARY(n)` | Store files in object storage, keep only a reference |
| **Network** | `INET`, `CIDR`, `MACADDR` | — | |
| **Range** | `INT4RANGE`, `TSTZRANGE`, `DATERANGE` | — | Powerful for scheduling/booking (exclusion constraints) |
| **Geometric** | `POINT`, `POLYGON` + PostGIS | Spatial types + SPATIAL index | |

### 3.2 The timestamp decision (asked in ~40% of loops)

```sql
-- Global product, users across time zones  ->  ALWAYS TIMESTAMPTZ (PG) / TIMESTAMP (MySQL)
CREATE TABLE audit_log (
    event_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type   VARCHAR(50) NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now()   -- stored as UTC internally
);

-- PostgreSQL renders it in the SESSION zone, not the server zone
SET timezone = 'Asia/Kolkata';
SELECT occurred_at FROM audit_log LIMIT 1;   -- 2026-10-23 18:30:00+05:30

-- The naive trap: opening a legal appointment window across DST
-- '2026-03-08 02:30:00' in America/New_York does not exist (spring-forward).
-- TIMESTAMPTZ rejects/absorbs it correctly; TIMESTAMP silently stores an
-- impossible local time and your "closing soon" cron misfires.
```

**Rule:** `TIMESTAMPTZ` for anything that happened in the real world (created_at, occurred_at); plain `DATE`/`TIMESTAMP` only for pure business dates (birth_date, financial period_end) that must not shift with the server's zone.

### 3.3 Money and precision — the arithmetic that ends interviews

```sql
-- WRONG: IEEE-754 binary floating point cannot represent 0.1
SELECT 0.1::FLOAT + 0.2::FLOAT;          -- 0.30000000000000004
SELECT CAST(0.1 AS NUMERIC) + 0.2;       -- 0.3  exactly

-- Money column: NUMERIC(12,2) -> 12 total digits, 2 after the decimal
-- Max value 9,999,999,999.99
ALTER TABLE products ALTER COLUMN price TYPE NUMERIC(12, 2);

-- Scale matters when dividing: NUMERIC division keeps a controlled scale
SELECT (total_amount * 18) / 100.0 AS gst_18pct FROM orders;
-- Avoid: total_amount / 3 * 3 != total_amount for exact decimals.
--         NUMERIC(12,2)/NUMERIC has implementation-defined scale -> round explicitly:
SELECT ROUND(total_amount / 3, 2) FROM orders;
```

### 3.4 `VARCHAR` vs `CHAR` vs `TEXT`

| Scenario | Correct choice | Why |
|---|---|---|
| Country code, currency code, status codes | `CHAR(2)` / `CHAR(3)` | Fixed width, comparable, space padded |
| Names, emails, titles | `VARCHAR(n)` | Variable, `NOT NULL` + length guard |
| Long descriptions, JSON blobs | `TEXT` | No pointless length planning |
| Indexed email | `VARCHAR(255)` + `UNIQUE` | `TEXT` cannot be a PK in MySQL without a prefix length |

> Always add `NOT NULL` unless "missing" is a genuine business state. A nullable column costs the engine an extra null-bitmap decision on every row and invites three-valued-logic bugs.

---

## 4. Constraints & Data Integrity

Constraints are the database's enforcement layer. They are also the **second-most-asked interview topic after joins**.

### 4.1 The six constraint families

```sql
CREATE TABLE employees (
    employee_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,  -- 1. PK
    first_name  VARCHAR(50)  NOT NULL,                             -- 2. NOT NULL
    last_name   VARCHAR(50)  NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,                      -- 3. UNIQUE (allows many NULLs)
    hire_date   DATE         NOT NULL CHECK (hire_date <= CURRENT_DATE),  -- 5. CHECK
    salary      NUMERIC(12,2) NOT NULL CHECK (salary > 0 AND salary < 10000000),
    commission  NUMERIC(12,2) CHECK (commission IS NULL OR (commission >= 0 AND commission < salary)),
    dept_id     BIGINT NOT NULL REFERENCES departments(dept_id)   -- 4. FOREIGN KEY
                             ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT uq_employee_name UNIQUE (first_name, last_name)   -- named composite UNIQUE
);
-- 6. EXCLUSION (PostgreSQL only): prevents overlapping ranges
CREATE TABLE room_bookings (
    room_id INT, during TSTZRANGE,
    EXCLUDE USING GIST (room_id WITH =, during WITH &&)
);
```

| Constraint | Guarantees | Interview nuance |
|---|---|---|
| `NOT NULL` | A value is always present | Required for PK columns; also enables better planner stats |
| `UNIQUE` | Duplicates are rejected (across non-NULL values) | Creates an implicit index; **multiple NULLs allowed** (SQL standard) |
| `PRIMARY KEY` | `NOT NULL` + `UNIQUE`, exactly one per table | In InnoDB the PK *is* the clustered index — the row storage order |
| `FOREIGN KEY` | Referential integrity | Requires an index on the child column, or every parent delete scans the child |
| `CHECK` | Row-level boolean predicate | Cannot reference other rows/tables (that needs a trigger); evaluated on INSERT/UPDATE |
| `EXCLUSION` | No two rows satisfy a conflict predicate at once | `&&` on ranges = booking double-booking prevention; GIST only |
| `DEFAULT` | A value when none is supplied | Not a constraint, but belongs to integrity design |

### 4.2 Referential actions — `ON DELETE` / `ON UPDATE`

```sql
FOREIGN KEY (dept_id) REFERENCES departments (dept_id)
    ON DELETE RESTRICT      -- reject the parent delete if children exist (our default)
    ON UPDATE CASCADE       -- if the parent PK changes, children follow
```

| Action | Parent row deleted/updated | Typical use |
|---|---|---|
| `NO ACTION` / `RESTRICT` | The statement **fails** | `RESTRICT` = "this is a live business entity, do not delete it" |
| `CASCADE` | Children are deleted/updated too | Owned child records (`order_items` when `orders` is deleted) |
| `SET NULL` | Child FK becomes `NULL` | Column **must be nullable**; "orphan but keep" (`employees.manager_id` on manager exit) |
| `SET DEFAULT` | Child FK set to its `DEFAULT` | Rare; needs a sentinel row (e.g. `dept_id = 0` = "unassigned") |

**Deferred constraints:** `DEFERRABLE INITIALLY DEFERRED` constraints are checked at `COMMIT` — required when you insert parents and children in one transaction in an arbitrary order, and for self-referencing FKs. MySQL does **not** support deferrable constraints; you must order your statements or disable `FOREIGN_KEY_CHECKS` per session.

```sql
-- PostgreSQL: insert a manager and their report in one transaction, any order
BEGIN;
INSERT INTO employees (first_name, last_name, email, hire_date, salary, manager_id, dept_id)
VALUES ('Dev','Patel','dev.patel@corp.io','2023-08-30',76000, 1, 3);
INSERT INTO employees (first_name, last_name, email, hire_date, salary, manager_id, dept_id)
VALUES ('Ravi','Kumar','ravi.kumar@corp.io','2024-02-01',70000, 100, 3); -- manager added above
COMMIT;   -- fk_employee_manager is DEFERRABLE INITIALLY DEFERRED -> validated here
```

### 4.3 Adding constraints to a live table

```sql
-- Postgres: validate without taking a long ACCESS EXCLUSIVE lock on the table
ALTER TABLE orders ADD CONSTRAINT chk_total_nonneg CHECK (total_amount >= 0) NOT VALID;
ALTER TABLE orders VALIDATE CONSTRAINT chk_total_nonneg;   -- takes only SHARE UPDATE EXCLUSIVE

-- Adding NOT NULL requires a default + backfill strategy (see Chapter 08)
ALTER TABLE customers ADD COLUMN loyalty_tier VARCHAR(20);
UPDATE customers SET loyalty_tier = 'BRONZE' WHERE loyalty_tier IS NULL;
ALTER TABLE customers ALTER COLUMN loyalty_tier SET DEFAULT 'BRONZE';
ALTER TABLE customers ALTER COLUMN loyalty_tier SET NOT NULL;
```

---

## 5. CRUD — The Complete Read & Write Playbook

### 5.1 `INSERT` — every production form

```sql
-- 1) Single row
INSERT INTO departments (dept_name, location, budget)
VALUES ('Security', 'Hyderabad', 900000.00);

-- 2) Multi-row (one round trip, one atomic statement)
INSERT INTO departments (dept_name, location, budget) VALUES
    ('Legal',  'Delhi',    1200000.00),
    ('Finance','Mumbai',   2500000.00);

-- 3) INSERT ... SELECT  (ETL / migration / backfill)
INSERT INTO archive_orders (order_id, customer_id, order_date, status, total_amount, archived_at)
SELECT o.order_id, o.customer_id, o.order_date, o.status, o.total_amount, now()
FROM orders o
WHERE o.status = 'CANCELLED' AND o.order_date < CURRENT_DATE - INTERVAL '2 years';

-- 4) RETURNING  (PostgreSQL only): get generated keys without a second query
INSERT INTO customers (full_name, email, country)
VALUES ('Wayne Enterprises', 'ap@wayne.com', 'US')
RETURNING customer_id, created_at;
--   MySQL has NO equivalent: use LAST_INSERT_ID() (last insert id of the session)
--   or re-SELECT. MariaDB 10.5+ does support RETURNING.
--   UPDATE/DELETE ... RETURNING also exists in PostgreSQL only.

-- 5) UPSERT — PostgreSQL
INSERT INTO order_items (order_id, product_id, quantity, unit_price)
VALUES (2, 2, 4, 1299.00)
ON CONFLICT (order_id, product_id)          -- the constraint that detects the conflict
DO UPDATE SET quantity   = EXCLUDED.quantity + order_items.quantity,
              unit_price = EXCLUDED.unit_price;   -- accumulate instead of overwrite
-- ON CONFLICT DO NOTHING  -> idempotent insert

-- 6) UPSERT — MySQL
INSERT INTO order_items (order_id, product_id, quantity, unit_price)
VALUES (2, 2, 4, 1299.00)
ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity);
--   VALUES(col) = "the value that would have been inserted" (MySQL 8.0.20+ deprecates this alias)

-- 7) MySQL REPLACE INTO — a DELETE + INSERT: it fires BOTH delete and insert triggers
--    and re-assigns a NEW auto_increment value. Avoid it. Use the upsert above.
REPLACE INTO products (product_id, product_name, category, price) VALUES (1, 'Laptop Pro 15"','Electronics',199000);
```

**Interview question: "What is the difference between `ON CONFLICT DO NOTHING` and `INSERT IGNORE`?"** `DO NOTHING` ignores **only the conflict you named**; errors like check/not-null violations still fail. `INSERT IGNORE` swallows **all** errors (bad data, FK violations, check failures), demoting them to warnings — which is how "silent data loss" incidents start. Prefer `DO NOTHING`.

### 5.2 `UPDATE` — every production form

```sql
-- 1) Basic
UPDATE products SET price = price * 1.10 WHERE category = 'Electronics';

-- 2) Update with a join (PostgreSQL: FROM; MySQL: JOIN)
UPDATE employees e
SET salary = e.salary * 1.05
FROM departments d
WHERE d.dept_id = e.dept_id AND d.location = 'Bengaluru' AND e.salary < 150000;

UPDATE employees e
JOIN departments d ON d.dept_id = e.dept_id
SET e.salary = e.salary * 1.05
WHERE d.location = 'Bengaluru' AND e.salary < 150000;

-- 3) Correlated subquery form (portable, but slower without an index)
UPDATE orders o
SET total_amount = (SELECT COALESCE(SUM(oi.quantity * oi.unit_price), 0)
                    FROM order_items oi WHERE oi.order_id = o.order_id);

-- 4) RETURNING the changed rows (audit without a second pass)
UPDATE orders SET status = 'CANCELLED'
WHERE status = 'PENDING' AND order_date < CURRENT_DATE - 30
RETURNING order_id, customer_id, total_amount;

-- 5) Conditional column update — the "don't overwrite with NULL" pattern
UPDATE customers
SET email     = COALESCE(NULLIF($1, ''), email),      -- keep old if new value empty
    country   = COALESCE(NULLIF($2, ''), country)
WHERE customer_id = $3;
```

**Trap:** the ANSI standard says a multi-table `UPDATE`'s `SET` column must be **uniquely qualified**. MySQL allows `UPDATE a JOIN b SET a.x = ...`; PostgreSQL requires aliases in multi-table updates.

### 5.3 `DELETE` vs `TRUNCATE` — the definitive table

| Dimension | `DELETE` (DML) | `TRUNCATE` (DDL) |
|---|---|---|
| `WHERE` clause | ✅ supported | ❌ not allowed |
| Triggers | ✅ fires row triggers | ❌ does not |
| `ON DELETE CASCADE` | ✅ propagated | ❌ ignored (CASCADE keyword needed in PG to empty children too) |
| Transactional / rollbackable (PostgreSQL) | ✅ yes | ✅ yes (DDL is transactional in PG) |
| Transactional / rollbackable (MySQL) | ✅ yes | ❌ **implicit commit — cannot roll back** |
| Identity counter reset | ❌ no | ✅ with `RESTART IDENTITY` (PG) / by recreating in MySQL |
| Speed | Slow (row-by-row undo logging) | **Very fast** (deallocates extents/pages) |
| Locking | Row locks | Table/metadata lock, briefly blocks readers |
| Use case | Delete *some* rows, keep audit | Empty a staging/temp table completely |

```sql
-- DELETE with a subquery (portable "delete the duplicates except the newest")
DELETE FROM orders o
WHERE o.order_id NOT IN (SELECT MAX(order_id) FROM orders GROUP BY customer_id);
-- NOT IN + NULL = deletes nothing! See Chapter 02 for the join-safe version.

-- TRUNCATE variants
TRUNCATE TABLE order_items;                                   -- fast empty
TRUNCATE TABLE orders RESTART IDENTITY CASCADE;               -- PG: reset ID seq, empty child tables
TRUNCATE TABLE orders, order_items;                           -- empty several in one statement
```

### 5.4 `SELECT` — the core read shape

```sql
SELECT DISTINCT e.email          AS email,          -- AS names the output column
       e.first_name || ' ' || e.last_name AS full_name,
       CASE WHEN e.salary >= 150000 THEN 'SENIOR' ELSE 'MID' END AS band,
       e.commission
FROM   employees AS e
WHERE  e.dept_id = 1
ORDER BY e.salary DESC
LIMIT  10 OFFSET 20;
```

Clause roles (the full logical execution order lives in [Chapter 05](./05-Advanced-SQL-Window-Functions-and-CTEs.md)):

| Clause | Role |
|---|---|
| `SELECT` | Projection — *which* columns/expressions, and aliasing |
| `FROM` | The source relations (tables, views, subqueries, function calls) |
| `WHERE` | Row filter, applied **before** grouping — *cannot* use aggregates or `SELECT` aliases |
| `GROUP BY` | Collapses rows into groups |
| `HAVING` | **Group** filter — *can* use aggregates |
| `WINDOW` | Window function definitions |
| `ORDER BY` | Final sort — *can* use `SELECT` aliases, `DISTINCT` expressions, and column positions |
| `LIMIT` / `OFFSET` / `FETCH FIRST` | Row-count cap |
| `DISTINCT` | Deduplication of the projected row set (requires a sort or hash) |
| `UNION ...` | Set operations over whole result sets |

```sql
-- PostgreSQL-only: DISTINCT ON — "first row per group" in one pass
SELECT DISTINCT ON (customer_id) customer_id, order_id, order_date, total_amount
FROM   orders
ORDER  BY customer_id, order_date DESC;
--   The DISTINCT ON columns MUST be a leftmost prefix of the ORDER BY.

-- Standard LIMIT/OFFSET vs ANSI SQL:2008 FETCH FIRST
SELECT ... ORDER BY order_id DESC LIMIT 10 OFFSET 30;
SELECT ... ORDER BY order_id DESC OFFSET 30 ROWS FETCH FIRST 10 ROWS ONLY;
```

### 5.5 The `MERGE` statement

`MERGE` is the standard "insert if missing, update if present" in one atomic statement. **PostgreSQL 15+ supports `MERGE`**; MySQL does not (use `ON DUPLICATE KEY UPDATE`).

```sql
MERGE INTO products AS p
USING (SELECT $1::BIGINT AS product_id, $2::NUMERIC AS price) AS src
ON p.product_id = src.product_id
WHEN MATCHED     THEN UPDATE SET price = src.price, is_discontinued = FALSE
WHEN NOT MATCHED THEN INSERT (product_id, product_name, price)
                        VALUES (src.product_id, 'New', src.price);
```

> **Interview trap:** `MERGE` without `WHEN NOT MATCHED THEN INSERT` silently *drops* unmatched source rows. In PostgreSQL, a `MERGE` that matches the same target row twice raises an error ("MERGE command cannot affect row a second time").

---

## 6. Filtering — The `WHERE` Clause Deep Dive

### 6.1 The operator catalog

| Category | Operators / predicates |
|---|---|
| Comparison | `=`, `<>`/`!=`, `<`, `<=`, `>`, `>=` |
| Range | `BETWEEN a AND b` (**inclusive**, `a <= x AND x <= b`), `NOT BETWEEN` |
| Set membership | `IN (list)`, `NOT IN (list)` — ⚠ NULL-sensitive |
| Pattern | `LIKE`, `ILIKE` (PG only), `NOT LIKE`, `SIMILAR TO` |
| Null test | `IS NULL`, `IS NOT NULL` — **`= NULL` is always UNKNOWN** |
| Boolean | `AND`, `OR`, `NOT`, `IS TRUE`, `IS FALSE`, `IS DISTINCT FROM` |
| Quantified | `= ANY(...)`, `> ALL(...)`, `<> SOME(...)` |
| Existence | `EXISTS (subquery)`, `NOT EXISTS (subquery)` |
| Regex | `~`, `~*`, `!~` (PG POSIX); `REGEXP` / `RLIKE` (MySQL) |
| Type test | `ISNULL()`, `COALESCE(a, b, c)` (returns first non-NULL) |
| Array (PG) | `@>`, `<@`, `&&`, `= ANY(arr)` |

```sql
-- IS DISTINCT FROM: NULL-safe inequality (works like != but treats NULL as a value)
SELECT * FROM employees WHERE commission IS DISTINCT FROM 0;
--   catches BOTH commission = 500 AND commission IS NULL.
--   'WHERE commission <> 0' would silently drop every NULL commission.

-- ANY / ALL over an array (PG)
SELECT product_name, price FROM products
WHERE price > ALL (SELECT price FROM products WHERE category = 'Electronics');

-- Range join: quantity buckets
SELECT order_id, quantity,
       CASE WHEN quantity BETWEEN 1 AND 5 THEN 'SMALL'
            WHEN quantity BETWEEN 6 AND 20 THEN 'MEDIUM'
            ELSE 'BULK' END AS bucket
FROM order_items;
```

### 6.2 `LIKE` — the sargability warning

```sql
WHERE email LIKE '%@corp.io'          -- trailing wildcard: CANNOT use a b-tree index
WHERE email LIKE 'asha%'              -- prefix match: CAN use the index (range scan)
WHERE email ILIKE '%Corp%'            -- PG case-insensitive regex; full scan by design
WHERE product_name ~ '^[A-Z][a-z]+'   -- PG POSIX regex match
```

**Rule:** `col LIKE '%x'` is not sargable — the engine cannot narrow an index range. If you need suffix search, use a **reverse column** or a trigram index (`pg_trgm`'s GIN index with `gin_trgm_ops`).

### 6.3 Boolean precedence

`NOT` → `AND` → `OR`. Parentheses are documentation, not decoration.

```sql
-- DANGEROUS: AND binds tighter, so this is (a AND b) OR c
WHERE dept_id = 1 OR dept_id = 2 AND salary > 100000;

-- INTENDED
WHERE dept_id IN (1, 2) AND salary > 100000;
```

### 6.4 Writing NULL-safe filters

```sql
-- Find employees with no commission:
WHERE commission IS NULL                    -- correct
-- WHERE commission = NULL                  -- always UNKNOWN, returns 0 rows
-- WHERE COALESCE(commission, 0) = 0        -- correct but blocks index usage on commission
-- WHERE NOT (commission > 0)               -- WRONG: NULL > 0 is UNKNOWN, NOT UNKNOWN is UNKNOWN
```

---

## 7. Expressions, Functions & Conditional Logic

### 7.1 The function tables you should know cold

**String functions**

| PostgreSQL | MySQL | Result |
|---|---|---|
| `\|\|` / `concat(a,b)` | `concat(a,b)` | Concatenation (`\|\|` is `OR` in MySQL!) |
| `length(s)` / `char_length(s)` | `char_length(s)` | Character count |
| `upper`, `lower`, `initcap` (PG) | `upper`, `lower` | Case conversion |
| `substring(s from 2 for 4)` / `substr(s,2,4)` | `substring(s,2,4)` | Substring (1-indexed) |
| `split_part(s, ',', 2)` | `substring_index(s, ',', 2)` | Split by delimiter |
| `trim(s)`, `ltrim`, `rtrim` | same | Whitespace removal |
| `lpad`, `rpad` | `lpad`, `rpad` | Padding |
| `position('x' in s)` | `instr(s,'x')` / `locate` | First occurrence index |
| `regexp_replace(s, pat, rep)` | `regexp_replace` (8.0+) | Regex substitution |
| `to_char(v, format)` | `format()` | Formatting |

**Numeric functions**

| Function | Result |
|---|---|
| `round(x[, d])` | Rounding (banker's rounding in PG for `NUMERIC` ties: `round(0.5)=1`, `round(2.5)=2` ⚠) |
| `trunc(x, d)` / `floor` / `ceil` | Truncation / floor / ceiling |
| `abs`, `sign`, `mod`, `power`, `sqrt` | Math |
| `greatest(a,b,...)` / `least(a,b,...)` | Max/min across arguments (ignore NULLs in PG) |
| `div(a, b)` (PG) | Integer division |

**Date/time functions**

| PostgreSQL | MySQL | Result |
|---|---|---|
| `now()`, `current_timestamp` | `now()` | Current timestamp with zone |
| `current_date` | `curdate()` | Today |
| `date_trunc('month', ts)` | `date_format(ts, '%Y-%m-01')` | Truncate to unit |
| `date_part('year', ts)` / `extract(YEAR FROM ts)` | `year(ts)`, `month(ts)` | Extract part |
| `age(ts1, ts2)` | `timestampdiff(YEAR, a, b)` / `datediff(a,b)` | Interval / day difference |
| `ts + INTERVAL '3 days'` | `date_add(ts, INTERVAL 3 DAY)` | Arithmetic |
| `to_char(ts, 'YYYY-MM-DD HH24:MI')` | `date_format(ts, '%Y-%m-%d %H:%i')` | Formatting |
| `to_date(s, fmt)` / `to_timestamp(s, fmt)` | `str_to_date(s, fmt)` | Parsing |

```sql
-- Business-realistic date math
SELECT order_id, order_date,
       extract(MONTH FROM order_date)      AS order_month,
       date_trunc('month', order_date)      AS month_start,
       order_date + INTERVAL '30 days'      AS due_date,
       date_part('day', order_date)         AS day_of_month,
       to_char(order_date, 'Mon YYYY')      AS label,
       now() - order_date                   AS age
FROM   orders
WHERE  order_date >= date_trunc('year', now())
ORDER  BY order_date DESC;
```

### 7.2 NULL-handling functions

| Function | Semantics |
|---|---|
| `COALESCE(a, b, ...)` | First **non-NULL** argument; left to right, short-circuits |
| `NULLIF(a, b)` | `NULL` if `a = b`, else `a` — the classic divide-by-zero guard |
| `ISNULL()` / `ifnull()` (MySQL) | Two-arg `COALESCE` |
| `NVL(a, b)` (Oracle) | Two-arg `COALESCE` |
| `GREATEST`/`LEAST` | Return `NULL` if **any** argument is NULL (standard behavior) |
| `nullif(commission, 0)` + `coalesce(..., 0)` | Neutralize sentinel values |

```sql
-- Convert "0 means unset" into a real NULL, then fall back to a default
SELECT COALESCE(NULLIF(commission, 0), 0) AS commission_clean FROM employees;

-- Safe percentage: divide by zero protection
SELECT order_id,
       ROUND(100.0 * quantity * unit_price / NULLIF(total_amount, 0), 2) AS pct_of_order
FROM order_items;
```

### 7.3 `CASE` expressions — conditional logic in a row

```sql
-- Simple CASE (compares one expression to values)
SELECT CASE status
         WHEN 'PENDING'   THEN 'Awaiting payment'
         WHEN 'PAID'      THEN 'Paid, not yet shipped'
         WHEN 'SHIPPED'   THEN 'In transit'
         WHEN 'DELIVERED' THEN 'Complete'
         ELSE 'Voided'
       END AS status_label
FROM orders;

-- Searched CASE (independent predicates) — preferred by most style guides
SELECT CASE
         WHEN total_amount = 0            THEN 'NO_VALUE'
         WHEN total_amount < 5000         THEN 'SMALL'
         WHEN total_amount < 100000       THEN 'MEDIUM'
         ELSE 'LARGE'
       END AS order_size,
       COUNT(*) AS order_count
FROM orders
GROUP BY 1;    -- positional GROUP BY is legal but avoid it in production code
```

`CASE` never returns "nothing": if no branch matches and there is no `ELSE`, it returns `NULL`. **`CASE` evaluation order is guaranteed to short-circuit** — put the most selective / cheapest predicate first, and remember it cannot contain DDL/DCL.

### 7.4 Casting and type conversion

```sql
-- PostgreSQL: ::type, CAST(), or the function form
SELECT '2026-10-23'::DATE, '42'::INTEGER, 123::TEXT, now()::DATE;
SELECT CAST('42.5' AS NUMERIC(10,2));
SELECT to_date('23/10/2026', 'DD/MM/YYYY');
SELECT '42abc'::INTEGER;        -- ERROR: invalid input syntax
-- MySQL: CAST(x AS SIGNED), CONVERT(x, type), implicit coercion is more lenient
SELECT CAST('42' AS UNSIGNED), '42abc' + 0;   -- MySQL: 42 (with warning)
```

**Interview trap:** implicit string→number coercion differs across engines. PostgreSQL **errors**; MySQL coerces with a warning. Never rely on it: always `CAST` explicitly when comparing a string column to a number, otherwise the index becomes unusable (`WHERE varchar_col = '42'` is fine, `WHERE varchar_col = 42` is a bug waiting for a bad row).

---

## 8. Sorting, Pagination & Deduplication

```sql
-- 1) NULLS placement is engine-defined by default for ASC/DESC
SELECT first_name, commission FROM employees ORDER BY commission;          -- PG: NULLs LAST for ASC
SELECT first_name, commission FROM employees ORDER BY commission DESC NULLS LAST;   -- explicit & portable intent

-- 2) Deterministic ordering: the unique tiebreaker
SELECT order_id, order_date, total_amount
FROM   orders
ORDER  BY order_date DESC, order_id DESC;    -- order_id breaks the tie -> stable across runs

-- 3) Multi-key with ASC/DESC and expressions
SELECT product_name, price, stock_qty
FROM   products
ORDER  BY is_discontinued, price DESC, product_name ASC;

-- 4) LIMIT with a filter — "top 3 most expensive in-stock"
SELECT product_name, price FROM products
WHERE  stock_qty > 0 AND NOT is_discontinued
ORDER  BY price DESC
LIMIT  3;

-- 5) Deep-page pagination is the trap (OFFSET N still *materializes and discards* N rows)
SELECT order_id FROM orders ORDER BY order_id LIMIT 20 OFFSET 100000;  -- scans 100,020 rows

-- 6) Keyset / seek pagination — constant time regardless of depth (see Chapter 05)
SELECT order_id, order_date, total_amount
FROM   orders
WHERE  (order_date, order_id) < ('2025-06-11', 9)     -- row-value comparison
ORDER  BY order_date DESC, order_id DESC
LIMIT  20;
```

**`DISTINCT` costs a sort or a hash.** Prefer `GROUP BY` on a unique column, or better, eliminate the need by fixing the query (usually a missing join condition caused row multiplication — see [Chapter 02](./02-Joins-Set-Operations-and-Subqueries.md)).

---

## 9. NULL Semantics — The #1 Interview Trap

SQL uses **three-valued logic**: every predicate evaluates to `TRUE`, `FALSE`, or `UNKNOWN`. Only `TRUE` passes a `WHERE` clause.

| Expression | Result | Why |
|---|---|---|
| `NULL = NULL` | **UNKNOWN** | NULL means *unknown*; unknown ≠ unknown |
| `NULL <> 5` | **UNKNOWN** | |
| `NULL AND FALSE` | **FALSE** | `FALSE` dominates anything |
| `NULL AND TRUE` | **UNKNOWN** | |
| `NULL OR TRUE` | **TRUE** | `TRUE` dominates anything |
| `NULL OR FALSE` | **UNKNOWN** | |
| `NOT (NULL)` | **UNKNOWN** | |
| `NULL IS NULL` | **TRUE** | `IS NULL` is a dedicated predicate, not a comparison |

```sql
-- The classic production bug: NOT IN with a subquery that can return NULL.
-- (Our departments table is clean, so force the bad case with an explicit NULL.)
SELECT COUNT(*) FROM employees WHERE dept_id NOT IN (SELECT NULL::BIGINT);  -- 0, silently
--   for a row whose dept_id is NOT in the list (say 99):
--   99 NOT IN (1, 2, 3, 4, NULL)
--   -> (99<>1) AND (99<>2) AND (99<>3) AND (99<>4) AND (99<>NULL)
--   -> TRUE AND TRUE AND TRUE AND TRUE AND UNKNOWN  ->  UNKNOWN  ->  row dropped
--   -> ZERO ROWS. The whole query silently returns nothing.

-- Fix #1 (preferred): NOT EXISTS
SELECT e.* FROM employees e
WHERE NOT EXISTS (SELECT 1 FROM departments d WHERE d.dept_id = e.dept_id);

-- Fix #2: filter NULLs out of the subquery
WHERE dept_id NOT IN (SELECT dept_id FROM departments WHERE dept_id IS NOT NULL);

-- Fix #3 (best for a fixed set): LEFT JOIN + IS NULL
SELECT e.* FROM employees e
LEFT JOIN departments d ON d.dept_id = e.dept_id
WHERE d.dept_id IS NULL;
```

**Additional NULL gotchas:**
| Situation | Behavior |
|---|---|
| `COUNT(*)` | Counts **all rows**, including NULLs |
| `COUNT(col)` | Counts **non-NULL** values only |
| `SUM(col)` | Returns `NULL` (not 0) when all rows are NULL → wrap in `COALESCE(SUM(col), 0)` |
| `AVG(col)` | Ignores NULLs — the denominator is the non-NULL count |
| `UNIQUE` constraint | Allows **multiple NULLs** in PostgreSQL/MySQL (SQL standard) |
| `GROUP BY` | NULLs form **one** group |
| `ORDER BY` | NULLs sort as a group (largest in PG ASC, smallest in PG DESC by default) |
| Primary key | Cannot be NULL by definition |
| `DISTINCT` | NULLs are treated as **equal** to each other (one row survives) |
| Comparison/`IN`/`NOT IN`/`<>` | Any NULL operand ⇒ UNKNOWN |
| String concat `\|\|` (PG) | `NULL \|\| 'x'` = **`'x'`** (NULL is treated as empty string by `\|\|`) |

---

## 10. Views, Sequences, Identity Columns & CTAS

```sql
-- 1) VIEW: a stored query. No storage, always live data, but no caching.
CREATE OR REPLACE VIEW v_active_employees AS
SELECT e.employee_id, e.first_name || ' ' || e.last_name AS full_name, d.dept_name, e.salary
FROM   employees e JOIN departments d ON d.dept_id = e.dept_id
WHERE  e.hire_date <= CURRENT_DATE;

-- 2) MATERIALIZED VIEW: caches the result physically (must be refreshed)
CREATE MATERIALIZED VIEW mv_sales_by_month AS
SELECT date_trunc('month', order_date) AS m, SUM(total_amount) AS revenue, COUNT(*) AS orders
FROM orders GROUP BY 1
WITH DATA;
CREATE UNIQUE INDEX ON mv_sales_by_month (m);            -- required for CONCURRENTLY
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_sales_by_month;  -- non-blocking refresh

-- 3) SEQUENCE: backing generator for IDs
CREATE SEQUENCE order_seq START WITH 1000 INCREMENT BY 1 CACHE 20 NO MINVALUE;
SELECT nextval('order_seq'), currval('order_seq'), lastval();
--   CACHE trades durability (a crash can skip values) for speed.
--   currval is session-scoped; lastval requires at least one nextval in the session.

-- 4) IDENTITY columns (preferred over SERIAL): permissions are tied to the column
CREATE TABLE invoices (invoice_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, ...);
INSERT INTO invoices (...) VALUES (...) RETURNING invoice_id;

-- 5) CTAS: bulk-build a new table from a query
CREATE TABLE orders_2025 AS
SELECT * FROM orders WHERE order_date >= DATE '2025-01-01' AND order_date < DATE '2026-01-01';

-- 6) TEMP tables: session-private scratch space (dropped at disconnect)
CREATE TEMP TABLE tmp_high_value_customers ON COMMIT DROP AS
SELECT customer_id FROM customers WHERE is_active;   -- ON COMMIT PRESERVE ROWS | DROP | DELETE ROWS
```

**Interview question: "View vs materialized view vs table?"** A **view** stores the query (always current, no storage, executes every time). A **materialized view** stores the *result* (fast reads, must be refreshed, stale until then). A **table** stores the data and is authoritative. Use views for access control + reuse (`GRANT SELECT ON v_x`) and materialized views for expensive aggregations refreshed on a schedule.

---

## 11. SQL Style & Production Anti-Patterns

| # | Anti-pattern | Why it is dangerous | Correct form |
|---|---|---|---|
| 1 | `SELECT *` in app code | Breaks on column reorder, defeats covering index-only scans, ships PII | List columns explicitly |
| 2 | `UPDATE`/`DELETE` without `WHERE` | Full-table mutation | Always `WHERE` + a dry-run `SELECT` first |
| 3 | String concatenation of SQL | **SQL injection** | Parameterized queries (`$1`/`?`) / JPA/NamedParameterJdbcTemplate |
| 4 | `FLOAT` for money | Rounding corruption | `NUMERIC(p, s)` |
| 5 | `NOT IN (subquery)` | NULL ⇒ zero rows | `NOT EXISTS` |
| 6 | `WHERE function(col) = ...` | Not sargable; index unusable | Rewrite to `col = ...` range, add an expression index |
| 7 | `TRUNCATE` on a live table (MySQL) | Implicit commit, cannot roll back, table lock | `DELETE` with batches |
| 8 | Storing dates as `VARCHAR` | Wrong ordering, wrong arithmetic, no index range scans | `DATE` / `TIMESTAMPTZ` |
| 9 | `INSERT IGNORE` / `REPLACE INTO` (MySQL) | Silent data loss, trigger chaos | `ON CONFLICT DO NOTHING/UPDATE` |
| 10 | Oracle `(+)` outer-join syntax | Deprecated, ambiguous with multi-join | ANSI `LEFT JOIN` |
| 11 | Implicit comma joins with `WHERE` | Easy to forget a predicate ⇒ accidental `CROSS JOIN` | Explicit `JOIN ... ON` |
| 12 | Missing FK index on child columns | Every parent `DELETE`/`UPDATE` does a full child scan | Index every FK column |
| 13 | `ORDER BY RANDOM()` / `RAND()` | Full table sort + table scan | Deterministic keyset sampling or `TABLESAMPLE` |
| 14 | `NULL = NULL` style checks | Always UNKNOWN | `IS NULL` / `IS NOT DISTINCT FROM` |

---

## 12. Level-Wise Interview Questions & Answers

### 🟢 Junior (0–2 Years)

**Q1. What are the different types of SQL statements?**
A: **DDL** (`CREATE`, `ALTER`, `DROP`, `TRUNCATE`) defines structure. **DQL** (`SELECT`) retrieves. **DML** (`INSERT`, `UPDATE`, `DELETE`, `MERGE`) modifies contents. **DCL** (`GRANT`, `REVOKE`) controls access. **TCL** (`COMMIT`, `ROLLBACK`, `SAVEPOINT`) manages transactions.

**Q2. What is the difference between `DELETE` and `TRUNCATE`?**
A: `DELETE` is DML — selective (`WHERE`), fires triggers, row-by-row undo logging, rollbackable in both engines. `TRUNCATE` is DDL — empties the whole table by deallocating pages, ignores triggers, no `WHERE`, cannot roll back in MySQL (implicit commit), and can reset identity counters.

**Q3. What is a primary key? Can a table have two?**
A: A primary key uniquely identifies each row and must be `NOT NULL` + `UNIQUE`. A table can have **only one** primary key, but it may be **composite** (multiple columns) and there can be many other candidate keys.

**Q4. What is a foreign key?**
A: A column (or set) in a child table that references a candidate key (usually the PK) in a parent table, enforcing referential integrity. Its actions (`ON DELETE CASCADE`/`SET NULL`/`RESTRICT`) define what happens to children when the parent is removed.

**Q5. How do you insert multiple rows with one statement?**
A: `INSERT INTO t (a, b) VALUES (1, 2), (3, 4), (5, 6);` — or `INSERT INTO t SELECT ... FROM other_table;` for ETL.

**Q6. What is the difference between `HAVING` and `WHERE`?**
A: `WHERE` filters **rows** before grouping (no aggregates allowed). `HAVING` filters **groups** after `GROUP BY` (aggregates allowed).

**Q7. How do you find rows where a column has no value?**
A: `WHERE col IS NULL` — never `WHERE col = NULL`, which is always `UNKNOWN` and returns zero rows.

### 🟡 Mid-Level (2–5 Years)

**Q8. `VARCHAR` vs `CHAR` vs `TEXT` — when do you use each?**
A: `CHAR(n)` for fixed-width codes (padded, comparable); `VARCHAR(n)` for variable short strings with a business length cap; `TEXT` for long/unbounded content. In PostgreSQL 16+, `TEXT` and `VARCHAR` have identical performance; in MySQL, `TEXT` cannot be indexed without a prefix length and cannot have a default.

**Q9. Why should you not use `FLOAT` for monetary values?**
A: `FLOAT`/`DOUBLE` are IEEE-754 binary approximations — 0.1 has no exact binary representation, so sums accumulate rounding error (`0.1 + 0.2 = 0.30000000000000004`). Use `NUMERIC`/`DECIMAL` which store exact base-10 values with defined precision and scale.

**Q10. What is `ON CONFLICT` / upsert, and why is `INSERT IGNORE` risky?**
A: `INSERT ... ON CONFLICT (unique_cols) DO UPDATE/NOTHING` (PostgreSQL) or `ON DUPLICATE KEY UPDATE` (MySQL) is an atomic "insert-or-update". `INSERT IGNORE` swallows **all** errors — check violations, FK failures, data truncation — hiding real bugs and silently losing data.

**Q11. What is `COALESCE` and how is it different from `NULLIF`?**
A: `COALESCE(a, b, c)` returns the first non-NULL argument (left to right). `NULLIF(a, b)` returns `NULL` if `a = b`, otherwise `a` — the standard pattern is `COALESCE(NULLIF(commission, 0), 0)` to convert a sentinel `0` into a real `NULL` and back to a default.

**Q12. How do you update rows in a table using values from another table?**
A: PostgreSQL: `UPDATE t SET col = s.col FROM source s WHERE t.id = s.id;`. MySQL: `UPDATE t JOIN source s ON ... SET t.col = s.col;`. Portable alternative: a correlated subquery in `SET`.

**Q13. What does `EXPLAIN` on a join-less query tell you about `SELECT *`?**
A: A `SELECT *` prevents **index-only scans** because columns outside the index force heap fetches; it also increases I/O and network payload. `EXPLAIN (ANALYZE, BUFFERS)` will show `Heap Fetches` greater than zero where a covering index would show zero (fully covered in [Chapter 06](./06-Query-Planner-Optimizer-and-Explain.md)).

**Q14. What is the difference between a view and a materialized view?**
A: A view stores the query text — always current, zero storage, recomputed on every read. A materialized view stores the **result** — fast reads, but stale until `REFRESH`, and it needs a `UNIQUE` index to support `REFRESH ... CONCURRENTLY`.

### 🔴 Senior (5–8 Years)

**Q15. Explain why `NOT IN (SELECT ...)` is dangerous and what you use instead.**
A: If the subquery can return any `NULL`, `x NOT IN (1, 2, NULL)` evaluates to `UNKNOWN` for every `x`, so the query returns **zero rows** — a silent, catastrophic bug. Use `NOT EXISTS` (best: NULL-safe and usually index-driven), or `LEFT JOIN ... WHERE right.pk IS NULL`.

**Q16. Why is `TIMESTAMPTZ` the default for "when did this happen" columns?**
A: `TIMESTAMPTZ` normalizes everything to UTC on write and renders in the session zone on read, so the same event displays correctly for users in any time zone and arithmetic across DST boundaries is correct. Plain `TIMESTAMP`/`DATETIME` stores whatever local time you gave it — a `02:30` on spring-forward day simply does not exist in a DST zone, and your TTL/expiry jobs silently misfire.

**Q17. A `DELETE` of 5 million rows on a 200 million-row table is blocking the app. What do you do?**
A: (1) Batch it: `DELETE ... WHERE pk IN (SELECT ... LIMIT 10000)` in a loop, committing between batches to keep transactions short and lock windows small. (2) Consider `DELETE ... WHERE ctid IN (SELECT ctid ... LIMIT n)` to avoid re-scanning. (3) Prefer **partition drop**/`TRUNCATE` of a partition if the deletion is a time-range purge. (4) Ensure the FK child relationships are indexed so the delete does not scan children. (5) Monitor replication lag — each deleted row is WAL.

**Q18. What is the difference between `RESTRICT` and `NO ACTION` on a `FOREIGN KEY`?**
A: They are nearly identical: both fail the statement if child rows exist. `NO ACTION` is **deferred** — the check happens at the end of the statement (or at `COMMIT` if the constraint is `DEFERRABLE`), so a statement that removes the parent and then re-inserts it in the same transaction succeeds. `RESTRICT` is checked **immediately** and cannot be deferred.

**Q19. How would you store "a product has a set of tags" in PostgreSQL vs MySQL?**
A: PostgreSQL: a native `TEXT[]` array column with a `GIN` index (`CREATE INDEX ... USING GIN (tags)`), or a normalized `product_tags` table. MySQL: no array type — either a normalized junction table (preferred, joinable) or `JSON` with a multi-valued index (`CAST(tags AS CHAR(255) ARRAY)`, MySQL 8.0.17+). Normalization stays the correct default when you need to join or aggregate by tag.

### ⚫ Principal / Staff (8+ Years)

**Q20. Design a zero-downtime change of `orders.status` from `VARCHAR` to an `ENUM` on a 24/7 system.**
A: Do **not** `ALTER COLUMN TYPE` directly (rewrites the table, holds `ACCESS EXCLUSIVE`, doubles storage temporarily). Expand-contract: (1) add a new column `status_new` with the enum type + a default, backfill in batches, dual-write in the app, (2) switch reads, (3) drop the old column, (4) rename. If an enum is genuinely wanted, a `TEXT` + `CHECK (status IN (...))` is friendlier: adding a value is a metadata-only `ALTER TABLE ... ADD CONSTRAINT ... NOT VALID` + `VALIDATE`, whereas adding an `ENUM` value in Postgres is also O(1) but cannot be done inside a transaction that uses the value, and in MySQL it rebuilds the table.

**Q21. Why is `NUMERIC` arithmetic in a GROUP BY on 100M rows slower than `BIGINT`, and how do you fix it?**
A: `NUMERIC` is variable-width with arbitrary precision — arithmetic allocates, normalizes, and carries in base-10000 limbs. On 100M rows this multiplies CPU and temp space. Fix: store money in the **smallest unit** as `BIGINT` (`amount_paise BIGINT`) and format in the application layer, keeping `NUMERIC` only on the boundary of presentation; or constrain scale to 2 and rely on `NUMERIC(12,2)` inlining. The `BIGINT` in paise pattern is what payment ledgers (Stripe, Razorpay) actually do.

**Q22. Your service uses `INSERT ... ON CONFLICT DO UPDATE` on a hot unique key. Under concurrency you see deadlocks. Why, and what do you change?**
A: Multiple concurrent upserts on the *same* speculative insertion tuple can deadlock on the unique index's "wait for the in-progress inserter" protocol, especially with a `DO UPDATE` that touches the row in a different physical order. Changes: (1) make the conflicting transaction short and always update rows in a **deterministic order** (e.g. `ORDER BY id`) — deadlocks almost always come from inconsistent lock ordering; (2) reduce the transaction footprint (no extra locks between upserts); (3) retry on SQLSTATE `40P01` with jitter; (4) if duplicate-key contention is extreme, insert into a staging table and upsert serially. This is detailed in [Chapter 07](./07-Transactions-ACID-MVCC-and-Locking.md).

---

## 13. Counter-Questions & Interviewer Traps

**Trap 1: "Is `TRUNCATE` transactional?"**
The correct answer is engine-dependent: **PostgreSQL — yes** (DDL is transactional, `TRUNCATE` can be rolled back); **MySQL — no** (it causes an implicit commit and cannot be rolled back). Answering an unqualified "yes" or "no" is a fail.

**Trap 2: "Can a primary key be NULL?"**
No — a PK is `NOT NULL` by definition. A *candidate* key column may be NULL in theory, but then it isn't a candidate key in the strict sense.

**Trap 3: "Can a `UNIQUE` column contain multiple NULLs?"**
In PostgreSQL and MySQL, **yes** — NULLs are considered distinct. (In some engines — e.g. Microsoft SQL Server — only one NULL is allowed.) This is the number-one "gotcha" about `UNIQUE` constraints.

**Trap 4: "Which is faster: `TRUNCATE` or `DELETE`?"**
`TRUNCATE` — it deallocates whole pages/extents instead of generating per-row undo records. But note the trade-offs: no triggers, no `WHERE`, no rollback (MySQL).

**Trap 5: "Does `CHECK` constraint validation happen before or after the statement?"**
Before — a row-level `CHECK` is evaluated for each affected row as part of the statement, and the statement fails atomically (PostgreSQL) or the offending row is rejected (MySQL). `CHECK` cannot reference other rows or tables; that requires a trigger.

**Trap 6: "What is the default value of a column with no `DEFAULT`?"**
`NULL` — if it is nullable. If `NOT NULL` with no default, an `INSERT` that omits the column **fails**.

**Trap 7: "Can you `ROLLBACK` after `CREATE INDEX`?"**
PostgreSQL: yes (transactional DDL). MySQL: **no** — DDL performs an implicit commit, so any open transaction is committed first.

---

## 14. Rapid-Fire MCQs

**Q1. Which statement is DDL?**
- [ ] A) `UPDATE`
- [ ] B) `INSERT`
- [ ] C) `TRUNCATE`
- [ ] D) `SELECT`
* **Answer:** **C.** `TRUNCATE` deallocates storage and is Data Definition Language.

**Q2. Which expression is NULL-safe for "commission is not zero"?**
- [ ] A) `WHERE commission <> 0`
- [ ] B) `WHERE NOT (commission = 0)`
- [ ] C) `WHERE commission IS DISTINCT FROM 0`
- [ ] D) `WHERE commission != 0`
* **Answer:** **C.** A/B/D return `UNKNOWN` when `commission IS NULL`. `IS DISTINCT FROM` treats NULL as a value and matches it.

**Q3. What does `SELECT COUNT(*), COUNT(commission) FROM employees` return for 10 rows with 2 NULL commissions?**
- [ ] A) 10, 10
- [ ] B) 10, 8
- [ ] C) 8, 10
- [ ] D) 8, 8
* **Answer:** **B.** `COUNT(*)` counts rows; `COUNT(col)` counts non-NULL values.

**Q4. Which is the correct enum-enforcement approach in PostgreSQL?**
- [ ] A) `ENUM` type only
- [ ] B) `VARCHAR` + `CHECK (col IN (...))`
- [ ] C) `TINYINT` + application mapping
- [ ] D) Both A and B are valid; B is friendlier to extend
* **Answer:** **D.** Both enforce the domain; the `CHECK` form allows metadata-only additions via `NOT VALID` + `VALIDATE`.

**Q5. What is the result of `SELECT 0.1::FLOAT + 0.2::FLOAT = 0.3::FLOAT`?**
- [ ] A) TRUE
- [ ] B) FALSE
- [ ] C) UNKNOWN
- [ ] D) Error
* **Answer:** **B.** IEEE-754 cannot represent the operands exactly; the sum is `0.30000000000000004`.

**Q6. `INSERT ... ON CONFLICT DO NOTHING` vs `INSERT IGNORE` (MySQL) — what is the key difference?**
- [ ] A) They are identical
- [ ] B) `DO NOTHING` ignores only the named conflict; `IGNORE` downgrades all errors to warnings
- [ ] C) `IGNORE` is faster
- [ ] D) `DO NOTHING` cannot be rolled back
* **Answer:** **B.**

**Q7. Which pagination form scales to page 100,000?**
- [ ] A) `LIMIT 20 OFFSET 100000`
- [ ] B) `LIMIT 100000, 20`
- [ ] C) `WHERE (sort_key, id) < (:last_key, :last_id) ORDER BY sort_key DESC, id DESC LIMIT 20`
- [ ] D) Both A and B
* **Answer:** **C.** `OFFSET` scans and discards every skipped row; keyset pagination seeks directly.

---

## 15. One-Page Cheat Sheet

```text
+----------------------+---------------------------------------------------------------+
| Concept              | Rule                                                          |
+----------------------+---------------------------------------------------------------+
| Sublanguages         | DDL (structure) | DQL (SELECT) | DML (INSERT/UPDATE/DELETE)   |
|                      | DCL (GRANT)     | TCL (COMMIT/ROLLBACK/SAVEPOINT)          |
| DELETE vs TRUNCATE   | DML+WHERE+triggers+rollback  vs  DDL+fast+no WHERE+no trigger |
|                      | TRUNCATE: rollback only in Postgres, NOT MySQL               |
| Money type           | NUMERIC(p,s) / DECIMAL(p,s)  --  NEVER FLOAT/DOUBLE           |
| Timestamps           | TIMESTAMPTZ (PG) / TIMESTAMP (MySQL) = UTC-normalized         |
|                      | Plain TIMESTAMP/DATETIME only for pure business dates        |
| IDs                  | BIGINT IDENTITY/SERIAL (PG) / BIGINT AUTO_INCREMENT          |
|                      | UUIDv7 (time-ordered), NOT UUIDv4                            |
| JSON                 | PostgreSQL: JSONB (binary, GIN-indexable)                    |
| Constraints          | PK = NOT NULL + UNIQUE + one per table                       |
|                      | UNIQUE allows MULTIPLE NULLs (PG/MySQL)                     |
|                      | FK actions: RESTRICT | NO ACTION | CASCADE | SET NULL          |
| NOT IN + NULL        | Returns ZERO ROWS -> use NOT EXISTS or LEFT JOIN ... IS NULL |
| COUNT(*) vs COUNT(c) | Rows vs non-NULL values                                      |
| SUM of all-NULLs     | NULL -> wrap in COALESCE(SUM(x), 0)                          |
| Sargable predicates  | col = x, col > x, col LIKE 'pref%'  (NOT col LIKE '%suf')   |
| Upsert               | PG: ON CONFLICT (cols) DO UPDATE                             |
|                      | MySQL: ON DUPLICATE KEY UPDATE   (avoid REPLACE INTO)        |
| Batched deletes      | pk IN (SELECT ... LIMIT n) in a loop, commit per batch        |
| Omit SELECT *        | Named projections keep index-only scans + stable contracts   |
+----------------------+---------------------------------------------------------------+
```

---

## 16. Real-World Production Post-Mortems

### Incident 1: The `NOT IN` That Returned Zero Rows — and No One Noticed

A payments reconciliation job ran `WHERE merchant_id NOT IN (SELECT merchant_id FROM merchants)` nightly. A single merchant row got a `NULL` id from a bad import. The `NOT IN` silently returned zero rows, the report showed "0 unreconciled merchants," the alert threshold was never crossed, and **₹4.1 crore of unreconciled settlements went unnoticed for 9 days.** The fix was a one-line rewrite to `NOT EXISTS`, plus a `NOT NULL` + `UNIQUE` constraint on `merchants.merchant_id` so the bad import could never happen again.

### Incident 2: `FLOAT` Balance Column Drift

A wallet stored `balance FLOAT`. After 18 months of accruals, `SUM(balance)` disagreed with the ledger by ₹1,247.62 — a one-cent-per-transaction rounding error multiplied across millions of micro-credits. Recovery required a full ledger rebuild (`NUMERIC(18,2)` + immutable audit rows) and a two-week reconciliation freeze.

### Incident 3: `TRUNCATE` in a Transaction — MySQL Implicit Commit

A nightly job wrapped `TRUNCATE staging_imports;` and a `DELETE FROM live_table WHERE ...;` in one "atomic" transaction. In MySQL, `TRUNCATE` **implicitly committed**, which committed and exposed the half-finished `DELETE`, and made the subsequent `ROLLBACK` a no-op. The result: 3.2M live rows deleted without the intended audit insert having been committed. **Lesson: never mix DDL and DML in one transaction in MySQL; PostgreSQL-isms (transactional DDL) do not port.**

---

## 17. Follow-up Learning
- **Next Chapter:** → [02 · Joins, Set Operations & Subqueries](./02-Joins-Set-Operations-and-Subqueries.md) — every join type, Venn-diagram truth, anti/semi-joins, `UNION`/`INTERSECT`/`EXCEPT`, and correlated subqueries
- **Master Kit Index & Study Roadmap:** ← [01-SQL-Databases README](./README.md)
- **Deep engine mechanics:** [03 · Relational Engine & Storage Architecture Internals](./03-Relational-Engine-and-Storage-Internals.md)
- **Schema modeling & normalization:** [08 · Schema Design, Modeling & Zero-Downtime Migrations](./08-Schema-Design-Modeling-and-Migrations.md)
