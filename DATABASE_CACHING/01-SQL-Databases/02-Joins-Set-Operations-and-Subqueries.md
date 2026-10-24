# 02 · Joins, Set Operations & Subqueries

> **Level:** Beginner → Principal (the highest-volume SQL interview topic in existence)  
> **Interview Frequency:** ★★★★★ — asked in 100% of loops; *"write me a LEFT JOIN"* is the most common live-coding filter  
> **Target Databases:** PostgreSQL 15+ & MySQL 8.0+ (engine-specific forms are always flagged)  
> **Practice Schema:** [`practice-schema.sql`](./practice-schema.sql) — every query in this chapter runs against it as-is  
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Join Mental Model (and Why Venn Diagrams Lie)](#1-what--why--the-join-mental-model-and-why-venn-diagrams-lie)
- [2. The Canonical Practice Schema](#2-the-canonical-practice-schema)
- [3. Join Types — The Complete Masterclass](#3-join-types--the-complete-masterclass)
- [4. Semi-Joins, Anti-Joins & the NULL Trap](#4-semi-joins-anti-joins--the-null-trap)
- [5. Self-Joins, Hierarchies & Org Charts](#5-self-joins-hierarchies--org-charts)
- [6. `USING` vs `ON` vs `NATURAL` — Column Hygiene](#6-using-vs-on-vs-natural--column-hygiene)
- [7. Multi-Way Joins, Join Order & The Fan-Out Trap](#7-multi-way-joins-join-order--the-fan-out-trap)
- [8. Physical Join Algorithms — What the Engine Actually Does](#8-physical-join-algorithms--what-the-engine-actually-does)
- [9. Legacy Syntax & The Precedence Trap](#9-legacy-syntax--the-precedence-trap)
- [10. Set Operations — `UNION`, `INTERSECT`, `EXCEPT`](#10-set-operations--union-intersect-except)
- [11. Subqueries — Every Position, Every Form](#11-subqueries--every-position-every-form)
- [12. The Join Drill Bank — 25 Interview Queries With Solutions](#12-the-join-drill-bank--25-interview-queries-with-solutions)
- [13. Level-Wise Interview Questions & Answers](#13-level-wise-interview-questions--answers)
- [14. Counter-Questions & Interviewer Traps](#14-counter-questions--interviewer-traps)
- [15. Rapid-Fire MCQs](#15-rapid-fire-mcqs)
- [16. One-Page Cheat Sheet](#16-one-page-cheat-sheet)
- [17. Real-World Production Post-Mortems](#17-real-world-production-post-mortems)
- [18. Follow-up Learning](#18-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

1. **A join is not a Venn diagram — it is a Cartesian product filtered by a predicate.** `A JOIN B ON pred` = for every pair of rows, keep it if `pred` is `TRUE`. Inner join ⇒ *both* sides survive; outer join ⇒ the **null-preserving side** survives with `NULL`s filled in.
2. **`LEFT JOIN` keeps every row of the left table and pads unmatched right-side columns with `NULL`.** "Outer" is optional and implied by `LEFT`/`RIGHT`/`FULL`.
3. **A predicate against the null-preserved table, placed in `WHERE`, silently downgrades a `LEFT JOIN` into an `INNER JOIN`.** Conditions on the optional side belong in the `ON` clause. This is the single most common production join bug.
4. **`NOT IN` + `NULL` = zero rows.** Use `NOT EXISTS` or `LEFT JOIN ... IS NULL`.
5. **`USING (col)` merges the join column into a single output column; `ON` keeps both.** `NATURAL JOIN` auto-joins on all same-named columns and is banned in production code — a future column rename silently changes your results.
6. **Anti-join = "find what is missing"**: `LEFT JOIN ... WHERE right.pk IS NULL`, `NOT EXISTS`, or `EXCEPT`.
7. **Semi-join = "find what exists, without multiplying rows"**: `EXISTS` (or `IN`, or `JOIN` + `DISTINCT` — the last is the worst).
8. **Self-join is not a new join type** — it is a table joined to itself with **different aliases**, and it is the only clean way to talk about hierarchies (manager→employee, parent→child).
9. **Aggregate before you join, or you will multiply rows.** Joining a 1:N relation and then summing the "1" side over-counts by the fan-out factor.
10. **Set operations (`UNION`/`INTERSECT`/`EXCEPT`) require the same number of compatible-typed columns**, sort/order at the end of the whole statement, and `UNION` costs a dedup sort while `UNION ALL` does not.

---

## 1. What & Why — The Join Mental Model (and Why Venn Diagrams Lie)

The Venn diagram taught in tutorials shows two circles for `A JOIN B`:

```
   INNER JOIN            LEFT JOIN               FULL OUTER JOIN
   ┌───┬───┐             ┌───┬───┐               ┌───┬───┐
   │ A │ B │  overlap    │ A │ B │  all of A,     │ A │ B │  all of A,
   └───┴───┘  only       └───┴───┘  + overlap     └───┴───┘  all of B
                                          (B padded with NULLs)
```

**It is a useful picture of *which rows* survive, and a disastrously wrong picture of *how many rows* you get.** A Venn diagram shows sets; a join produces a **multiset**. If the right table has three matching rows for one left row, `LEFT JOIN` emits **three** rows — you get fan-out. Every "why are my totals 3x too big?" interview question is really "you forgot the Venn diagram hides multiplicity".

The rigorous mental model:

```text
A { ⋈ θ } B   ≡   σ_θ ( A × B )        -- selection of a Cartesian product
                                   -- "for each pair (a,b), keep it iff a.θ b is TRUE"

INNER:  the JOIN predicate must be TRUE for the pair to survive.
OUTER:  the pair is NOT required; the surviving side is padded with NULLs,
        so the LEFT/RIGHT/FULL decides *which side is null-preserving*.
CROSS:  no predicate at all -> |A| × |B| rows.
```

### 1.1 The null-preserving table

| Join | Preserves | Unmatched rows produce |
|---|---|---|
| `INNER` | neither | nothing (they vanish) |
| `LEFT [OUTER]` | **left** only | left row once, right columns = `NULL` |
| `RIGHT [OUTER]` | **right** only | right row once, left columns = `NULL` |
| `FULL [OUTER]` | **both** | rows from each unmatched side, other side `NULL` |
| `CROSS` | both (all pairs) | — every combination |

**Portability rule:** `A LEFT JOIN B` is *always* equal to `B RIGHT JOIN A`. Senior reviewers insist on `LEFT JOIN` only — because reading order should match the "primary" entity, and because mixing `LEFT` and `RIGHT` in one query is a readability fault that hides bugs.

---

## 2. The Canonical Practice Schema

Every query below runs against `practice-schema.sql` (6 tables, 11 employees, 5 departments, 7 customers, 9 products, 10 orders, 12 order lines):

```text
departments (dept_id PK, dept_name, location, budget)         <- 'Research' has ZERO employees
   │  1
   │
   └──< employees (employee_id PK, first_name, last_name, email UQ,
                   hire_date, salary, commission NULL-able, manager_id → employees, dept_id → departments)
                     │ manager_id → employees (self-reference)
                     └── 1:N on itself (Asha manages Rahul/Neha, ... Ishita earns more than her manager Rohit)

customers (customer_id PK, full_name, email UQ, country, is_active)   <- 'Hooli'/'Stark' has NO orders
   │  1
   └──< orders (order_id PK, customer_id → customers, order_date, status, total_amount)
              │  1
              └──< order_items (order_item_id PK, order_id → orders, product_id → products,
                                quantity, unit_price, UNIQUE(order_id, product_id))

products (product_id PK, product_name, category, price, stock_qty, is_discontinued)
          <- 'Legacy Monitor' & 'Noise Cancelling Headset' are NEVER SOLD (anti-join drills)
```

Row counts to remember (you can verify with the sanity check at the end of the schema file):
`departments = 5`, `employees = 11`, `customers = 7`, `products = 9`, `orders = 10`, `order_items = 12`.

---

## 3. Join Types — The Complete Masterclass

### 3.1 `INNER JOIN` — only matching pairs

```sql
SELECT e.employee_id, e.first_name, e.last_name, d.dept_name, d.location
FROM   employees AS e
INNER JOIN departments AS d ON d.dept_id = e.dept_id;   -- INNER is optional and implied
```

| Property | Value |
|---|---|
| Rows kept | Every pair where the `ON` predicate is `TRUE` |
| Unmatched left rows | ❌ discarded |
| Unmatched right rows | ❌ discarded |
| `NULL` comparison in `ON` (`NULL = NULL`) | evaluates to `UNKNOWN` → row dropped |
| Planner freedom | ✅ may reorder joins, choose any algorithm |

**Interview answer:** "Inner join returns rows where the join condition is satisfied in *both* tables. It is the default join type, and the most common mistake is assuming it is 'safe' — it silently drops unmatched rows, which is exactly why reporting queries fail on a missing lookup row."

### 3.2 `LEFT OUTER JOIN` — keep everything on the left

```sql
-- Every department, with a headcount, INCLUDING departments with no employees
SELECT d.dept_id,
       d.dept_name,
       COUNT(e.employee_id)          AS headcount,   -- COUNT(col) skips NULLs -> correct 0
       COALESCE(SUM(e.salary), 0)    AS payroll
FROM   departments AS d
LEFT   JOIN employees AS e ON e.dept_id = d.dept_id
GROUP  BY d.dept_id, d.dept_name
ORDER  BY d.dept_id;

/* result:
 dept_id | dept_name    | headcount | payroll
       1 | Engineering  |         4 |  645000
       2 | Sales        |         3 |  346000
       3 | Support      |         4 |  398000
       4 | Research     |         0 |       0     <-- LEFT JOIN's whole point
       5 | Marketing    |         0 |       0
*/
```

**`COUNT(*)` vs `COUNT(e.employee_id)` is the classic pairing here.** With `COUNT(*)`, the empty department still counts as **1** (the padded NULL row). With `COUNT(e.employee_id)` it correctly reports 0.

**The `WHERE` downgrade trap:**

```sql
-- INTENT: "all departments, plus employees hired after 2020 (or none if nobody qualifies)"
SELECT d.dept_name, e.first_name
FROM   departments d
LEFT   JOIN employees e ON e.dept_id = d.dept_id AND e.hire_date > DATE '2020-01-01';   -- ✅ correct

-- BUG: the filter moves to WHERE, and NULL hire_date fails the predicate -> INNER JOIN behaviour
SELECT d.dept_name, e.first_name
FROM   departments d
LEFT   JOIN employees e ON e.dept_id = d.dept_id
WHERE  e.hire_date > DATE '2020-01-01';    -- ❌ Marketing & Research disappear entirely
```

> **Rule of thumb:** any predicate that tests a column of the *nullable side* must live in `ON`, otherwise you have written an inner join with extra steps.

### 3.3 `RIGHT OUTER JOIN` — keep everything on the right

```sql
-- Same shape as the LEFT JOIN, written from the employees' point of view
SELECT d.dept_name, COUNT(*) AS matched_pairs
FROM   employees e
RIGHT  OUTER JOIN departments d ON d.dept_id = e.dept_id
GROUP  BY d.dept_name;
```

Semantically identical to the `LEFT JOIN` in §3.2 with the tables swapped. **Style guidance: avoid `RIGHT JOIN`** — it forces the reader to reverse the mental model. Interviewers sometimes *deliberately* hand you `RIGHT JOIN` code and ask "what's wrong with this?" — the answer is readability, not correctness.

### 3.4 `FULL OUTER JOIN` — keep everything on both sides

```sql
-- Departments with no employees AND employees with no department, in one result set
SELECT COALESCE(d.dept_name, '(no dept)')   AS dept_name,
       COALESCE(e.first_name || ' ' || e.last_name, '(no employee)') AS person,
       CASE WHEN d.dept_id IS NULL THEN 'ORPHAN_EMPLOYEE'
            WHEN e.employee_id IS NULL THEN 'EMPTY_DEPARTMENT'
            ELSE 'MATCHED' END AS status
FROM   departments d
FULL   OUTER JOIN employees e ON e.dept_id = d.dept_id;

-- "Only the mismatches on both sides" — the reconciliation pattern
SELECT d.dept_name, e.first_name
FROM   departments d
FULL   OUTER JOIN employees e ON e.dept_id = d.dept_id
WHERE  d.dept_id IS NULL OR e.employee_id IS NULL;

-- MySQL has NO FULL OUTER JOIN in any 8.0.x — emulate with UNION ALL of two anti-joins:
--   LEFT JOIN departments d ON ... WHERE d.dept_id IS NULL
--   UNION ALL
--   LEFT JOIN employees   e ON ... WHERE e.employee_id IS NULL
```

### 3.5 `CROSS JOIN` — the unfiltered Cartesian product

```sql
-- |employees| × |departments| = 11 × 5 = 55 rows
SELECT e.first_name, d.dept_name
FROM   employees e
CROSS  JOIN departments d;

-- Equivalent legacy forms (avoid them):
--   SELECT ... FROM employees e, departments d WHERE 1 = 1;
--   INNER JOIN ... ON TRUE

-- Legitimate uses: (a) generating a calendar/date dimension, (b) building all
-- combinations of a small dimension (shirt_size × colour), (c) test-data seeding.
```

### 3.6 Non-equi (θ) joins and range joins

```sql
-- Salary band via a range join — joins do not have to be equality!
SELECT e.first_name, e.salary, b.band_label
FROM   employees e
JOIN   (VALUES ('JUNIOR', 0::NUMERIC, 90000),
               ('MID',   90000, 130000),
               ('SENIOR',130000, 9999999))
       AS b(band_label, min_salary, max_salary)
  ON   e.salary >= b.min_salary AND e.salary < b.max_salary;

-- Booking-overlap anti-join (a INTERSECTS b) uses the && operator on ranges
SELECT a.room_id, a.during, b.during
FROM   room_bookings a
JOIN   room_bookings b ON a.room_id = b.room_id AND a.during && b.during AND a.booking_id <> b.booking_id;
```

### 3.7 Multi-column and composite-key joins

```sql
-- order_items has a composite UNIQUE (order_id, product_id) -> join on BOTH columns
SELECT oi.order_id, oi.product_id, oi.quantity
FROM   order_items oi
JOIN   order_lines_import imp
  ON   imp.order_id = oi.order_id
 AND   imp.product_id = oi.product_id;

-- Joining on a subset of the key silently multiplies rows: joining on order_id alone
-- would return (order_id, product_id) × every import row for that order.
```

### 3.8 `LATERAL` / `CROSS APPLY` — a correlated join

```sql
-- Top 3 most recent orders per customer, without a window function
SELECT c.customer_id, c.full_name, recent.order_id, recent.order_date, recent.total_amount
FROM   customers c
CROSS  JOIN LATERAL (
    SELECT o.order_id, o.order_date, o.total_amount
    FROM   orders o
    WHERE  o.customer_id = c.customer_id          -- references the preceding row!
    ORDER  BY o.order_date DESC, o.order_id DESC
    LIMIT  3
) AS recent;
-- LEFT JOIN LATERAL  -> keeps customers with no orders (padded NULLs)
-- MySQL 8.0.14+ supports LATERAL too; SQL Server uses CROSS APPLY / OUTER APPLY.
```

---

## 4. Semi-Joins, Anti-Joins & the NULL Trap

Two join shapes matter so much that they have names:

- **Semi-join** — "give me the rows of A that have **at least one** match in B, but do **not** multiply A rows."
- **Anti-join** — "give me the rows of A that have **no** match in B."

### 4.1 Semi-join — three ways, ranked

```sql
-- 1) EXISTS (best: NULL-safe, short-circuits, planner can use a semi-join plan)
SELECT c.customer_id, c.full_name
FROM   customers c
WHERE  EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.customer_id);

-- 2) IN (correct when the subquery column is NOT NULL)
SELECT c.customer_id, c.full_name
FROM   customers c
WHERE  c.customer_id IN (SELECT o.customer_id FROM orders o);

-- 3) JOIN + DISTINCT (works, but materialises the duplicates before deduping)
SELECT DISTINCT c.customer_id, c.full_name
FROM   customers c JOIN orders o ON o.customer_id = c.customer_id;
```

**Why `EXISTS` wins:** it can stop at the first match (a "semi-join" plan: stop scanning the inner relation after one row), it is immune to the `NULL` problem, and it never creates a row-multiplication intermediate that must later be deduplicated. `IN` is fine — and often planned identically — but it builds the subquery's result set and cannot short-circuit.

### 4.2 Anti-join — three ways, ranked

```sql
-- ❌ THE BUG: NOT IN over a subquery that can contain a NULL
SELECT product_id, product_name FROM products
WHERE  product_id NOT IN (SELECT product_id FROM order_items WHERE product_id IS NOT NULL);
--   (this one is safe ONLY because the NULLs were filtered out)

-- 1) NOT EXISTS (best: NULL-safe, index-driven)
SELECT p.product_id, p.product_name
FROM   products p
WHERE  NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = p.product_id);

-- 2) LEFT JOIN + IS NULL (very explicit, and the planner often converts it to an anti-join)
SELECT p.product_id, p.product_name
FROM   products p
LEFT   JOIN order_items oi ON oi.product_id = p.product_id
WHERE  oi.product_id IS NULL;      -- ⚠ test the JOINED column, not oi.order_item_id, or you'll match 1-to-N padding

-- 3) EXCEPT (declarative, needs the same column list, sorts/dedups both sides)
SELECT product_id, product_name FROM products
EXCEPT
SELECT p.product_id, p.product_name
FROM   products p JOIN order_items oi ON oi.product_id = p.product_id;
```

**`WHERE oi.order_item_id IS NULL` is a subtle but deadly mistake:** if the join column is `product_id` and you test a *different* right-side column, it happens to work only because that column is NULL for padded rows — but if it is a nullable *natural* column, you will filter out real matches. Always test the column you joined on.

### 4.3 The `NOT IN` + `NULL` catastrophe, proven

```sql
-- setup (illustrative): the dept set contains one NULL
SELECT dept_id FROM departments;        -- {1, 2, 3, 5, NULL}

SELECT first_name FROM employees
WHERE  dept_id NOT IN (1, 2, 3, 5, NULL);
--   (dept_id = 4) NOT IN (1,2,3,5,NULL)
--   -> (4<>1) AND (4<>2) AND (4<>3) AND (4<>5) AND (4<>NULL)
--   -> TRUE AND TRUE AND TRUE AND TRUE AND UNKNOWN
--   -> UNKNOWN -> NOT returned.  ZERO ROWS.
```

**Screen-ready answer:** "`NOT IN` fails if the subquery returns a single `NULL`, because `x <> NULL` is `UNKNOWN`, and `UNKNOWN` never passes `WHERE`. `NOT EXISTS` is NULL-safe and short-circuits, so it is the default. `LEFT JOIN ... IS NULL` is equivalent but can mislead readers, so I use it only when I want to show the missing side explicitly."

---

## 5. Self-Joins, Hierarchies & Org Charts

A **self-join** is just an ordinary join with the same table referenced twice through **different aliases**. It is not a distinct join *type* — it is a naming requirement.

```sql
-- (a) Each employee with their manager's name (INNER: drops the CEO)
SELECT e.first_name  AS employee,
       e.salary      AS emp_salary,
       m.first_name  AS manager,
       m.salary      AS mgr_salary
FROM   employees e
JOIN   employees m ON m.employee_id = e.manager_id;

-- (b) LEFT JOIN version: managers with no manager still appear
SELECT e.first_name AS employee, m.first_name AS manager
FROM   employees e
LEFT   JOIN employees m ON m.employee_id = e.manager_id;

-- (c) The classic "employees who earn more than their manager"
SELECT e.first_name || ' ' || e.last_name AS employee,
       e.salary                             AS employee_salary,
       m.first_name || ' ' || m.last_name   AS manager,
       m.salary                             AS manager_salary
FROM   employees e
JOIN   employees m ON m.employee_id = e.manager_id
WHERE  e.salary > m.salary;
--   result: Ishita Bose (135,000) > Rohit Gupta (98,000)

-- (d) Two levels up (grandparent)
SELECT e.first_name, m.first_name AS manager, g.first_name AS grand_manager
FROM   employees e
LEFT   JOIN employees m ON m.employee_id = e.manager_id
LEFT   JOIN employees g ON g.employee_id = m.manager_id;

-- (e) Who reports to whom, with the level of depth (recursion needs a recursive CTE — see Chapter 05)
WITH RECURSIVE org AS (
    SELECT employee_id, first_name, manager_id, 0 AS depth
    FROM   employees WHERE manager_id IS NULL
    UNION ALL
    SELECT e.employee_id, e.first_name, e.manager_id, o.depth + 1
    FROM   employees e JOIN org o ON o.employee_id = e.manager_id
)
SELECT depth, COUNT(*) AS headcount FROM org GROUP BY depth ORDER BY depth;
```

**Interview question:** *"How do you find duplicate rows using a self-join?"*

```sql
SELECT c1.customer_id AS dup_1, c2.customer_id AS dup_2, c1.email
FROM   customers c1
JOIN   customers c2 ON c1.email = c2.email AND c1.customer_id < c2.customer_id;
--   or the modern, cheaper form with a window function:
SELECT customer_id, email FROM (
    SELECT customer_id, email, ROW_NUMBER() OVER (PARTITION BY email ORDER BY customer_id) AS rn
    FROM   customers
) t WHERE rn > 1;
```

---

## 6. `USING` vs `ON` vs `NATURAL` — Column Hygiene

| Syntax | Join predicate | Output columns | Verdict |
|---|---|---|---|
| `ON a.x = b.x` | Explicit | both `a.x` and `b.x` | ✅ Default. Unambiguous. |
| `USING (x)` | Equi-join on the named column(s) | **one** merged column | ✅ Fine in PostgreSQL for simple FKs |
| `NATURAL JOIN` | All identically-named columns | merged | ❌ **Banned in production** — schema drift changes results silently |

```sql
-- USING: the shared column appears once (great for DISTINCT/EXISTS output hygiene)
SELECT e.first_name, d.dept_name
FROM   employees e
JOIN   departments d USING (dept_id);

-- NATURAL: joins on dept_id AND any future same-named column. If someone later adds
-- a "location" column to both tables, your join results CHANGE with no code change.
SELECT * FROM employees e NATURAL JOIN departments d;   -- ❌ never do this

-- Mixed qualification gotcha:
SELECT dept_id FROM employees e JOIN departments d ON d.dept_id = e.dept_id;
-- ^ ambiguous? No — after USING the unqualified name is legal, after ON it is ambiguous and errors.
```

**Stylistic rules interviewers reward:**
1. Always alias tables with a short, meaningful alias (`e`, `d`, `oi`) and **always qualify every column** — it prevents ambiguity the moment a third table joins.
2. Put **the most selective predicate first** in the `ON` clause? — No: the `ON` clause order does not matter logically; the optimizer normalises it. Readability matters, not order. (Conditional predicates in a `LEFT JOIN ... ON` *do* matter semantically — §3.2.)
3. Never `SELECT *` in a join — you get duplicated keys, ambiguous column names in ORMs, and ambiguous `ORDER BY` in set operations.

---

## 7. Multi-Way Joins, Join Order & The Fan-Out Trap

### 7.1 Joining four tables

```sql
SELECT c.full_name,
       o.order_id,
       o.order_date,
       o.status,
       p.product_name,
       oi.quantity,
       oi.unit_price,
       (oi.quantity * oi.unit_price) AS line_total
FROM   customers  c
JOIN   orders     o  ON o.customer_id  = c.customer_id
JOIN   order_items oi ON oi.order_id    = o.order_id
JOIN   products   p  ON p.product_id   = oi.product_id
WHERE  o.status <> 'CANCELLED'
ORDER  BY c.full_name, o.order_id, oi.order_item_id;
```

The join order **as written** is not the execution order for `INNER JOIN`s — the optimiser chooses it. It is *not* free to reorder `OUTER JOIN`s: once you write `A LEFT JOIN B`, the engine may not swap A and B, because that would change which rows are preserved.

### 7.2 The fan-out (row multiplication) trap

```sql
-- ❌ WRONG "customer spend": total_amount is repeated once per order line
SELECT o.customer_id, SUM(o.total_amount) AS wrong_total
FROM   orders o
JOIN   order_items oi ON oi.order_id = o.order_id
GROUP  BY o.customer_id;
--   order 7 has 3 line items -> its 567,000 total is added THREE times.

-- ✅ CORRECT: aggregate each side, then join
SELECT o.customer_id, SUM(o.total_amount) AS correct_total
FROM   orders o
GROUP  BY o.customer_id;

-- ✅ CORRECT for "spend by line item": aggregate the child side, then join
SELECT o.customer_id, o.order_id, SUM(oi.quantity * oi.unit_price) AS line_value
FROM   orders o
JOIN   order_items oi ON oi.order_id = o.order_id
GROUP  BY o.customer_id, o.order_id;

-- ✅ CORRECT when you genuinely need both grains: aggregate the 1:N first in a derived table
SELECT o.customer_id,
       o.total_amount,
       COALESCE(agg.line_count, 0) AS lines,
       COALESCE(agg.line_value, 0)  AS value
FROM   orders o
LEFT   JOIN (SELECT oi.order_id,
                    COUNT(*)                          AS line_count,
                    SUM(oi.quantity * oi.unit_price)  AS line_value
             FROM   order_items oi
             GROUP  BY oi.order_id) agg
       ON agg.order_id = o.order_id;
```

> **The interview sentence to memorise:** *"A join is a multiplication. If you join a one-to-many relationship and then aggregate from the 'one' side without `DISTINCT`, every parent row is counted once per child. Aggregate the child first, or use `COUNT(DISTINCT ...)`."*

### 7.3 Many-to-many and bridge tables

```sql
-- orders ⋈ products is N:M. order_items IS the bridge.
-- Revenue per category requires walking the bridge:
SELECT p.category,
       COUNT(DISTINCT o.order_id)                    AS orders_touching,
       SUM(oi.quantity * oi.unit_price)              AS revenue
FROM   products    p
JOIN   order_items oi ON oi.product_id = p.product_id
JOIN   orders      o  ON o.order_id    = oi.order_id
WHERE  o.status IN ('PAID', 'SHIPPED', 'DELIVERED')  -- exclude CANCELLED
GROUP  BY p.category
ORDER  BY revenue DESC;

-- A missing bridge row is the standard "did we lose a product?" alert:
SELECT p.product_id, p.product_name
FROM   products p
LEFT   JOIN order_items oi ON oi.product_id = p.product_id
WHERE  oi.product_id IS NULL;                        -- Legacy Monitor, Noise Cancelling Headset
```

### 7.4 The "fill the gaps" pattern — `LEFT JOIN` to a generated series

```sql
-- Zero-order months must still appear in a revenue report
SELECT m.month, COALESCE(r.revenue, 0) AS revenue
FROM   (SELECT DATE '2025-01-01' + (n || ' months')::INTERVAL AS month
        FROM   generate_series(0, 11) AS n) m
LEFT   JOIN (SELECT date_trunc('month', order_date) AS month, SUM(total_amount) AS revenue
             FROM   orders GROUP BY 1) r
       ON r.month = m.month
ORDER  BY m.month;
-- MySQL: replace generate_series with a recursive CTE or a numbers table.
```

---

## 8. Physical Join Algorithms — What the Engine Actually Does

The join *type* (logical/semantic) is independent of the join *algorithm* (physical). Interviewers ask both, and the full cost model lives in [Chapter 06](./06-Query-Planner-Optimizer-and-Explain.md) — but you must know the three algorithms.

| Algorithm | Requires | Best when | Cost shape |
|---|---|---|---|
| **Nested Loop** | An index / small inner relation | Outer is small, inner has an index on the join key, **correlated** subqueries, `LIMIT` queries that stop early | `O(outer × lookup)` — can stop after the first rows |
| **Hash Join** | Equality predicate, `work_mem` for the build side | Large unsorted inputs, equi-joins, star-schema facts | `O(n + m)` until it **spills to disk** (multi-batch, 50× slower) |
| **Sort-Merge (Merge) Join** | Both inputs sorted on the join key (or an explicit sort) | Inputs already ordered by an index, output must be sorted, inequality range joins | `O(n log n + m log m)` sort cost |

```text
Nested Loop:                Hash Join:                 Sort-Merge:
outer	  inner             build side -> hash table   sort both sides
  a  ▸ lookup b(i)            b1 -> 0x1A               a1 b2
  b  ▸ lookup b(i)            b2 -> 0x2F               a2 b2
  c  ▸ lookup b(i)            probe side streams       a3 b2
  ... (early exit on LIMIT)   and probes             ...
```

**What a senior must say:**
- "Hash join builds a hash table on the **smaller** (build) side; if it exceeds `work_mem`, it writes batches to disk and performance collapses — that is why `work_mem` and `SHOW BUFFERS` matter."
- "Nested loop is the only algorithm that can **stop early** — which is why `LIMIT 1` correlated lookups are fast."
- "Sort-merge is chosen when an index provides the ordering for free (e.g. joining on an ordered PK) or when the query needs sorted output anyway."
- "A merge/hash join cannot be used for a `LEFT JOIN LATERAL` correlated subquery — that is always a nested loop."

---

## 9. Legacy Syntax & The Precedence Trap

```sql
-- ❌ Legacy comma join: easy to forget a predicate -> silent CROSS JOIN
SELECT e.first_name, d.dept_name
FROM   employees e, departments d
WHERE  e.dept_id = d.dept_id;
--   drop the WHERE line and you get 11 × 5 = 55 rows, not an error.

-- ❌ Oracle's (+) outer-join operator: deprecated in 9i, ambiguous, and cannot express
--    FULL OUTER JOIN or multiple outer conditions cleanly
SELECT e.first_name, d.dept_name FROM employees e, departments d
WHERE  e.dept_id = d.dept_id (+);

-- ✅ ANSI-92 explicit syntax, always
SELECT e.first_name, d.dept_name
FROM   employees e
LEFT   JOIN departments d ON d.dept_id = e.dept_id;
```

**The precedence trap:** the comma operator has **lower precedence** than `JOIN ... ON`. Mixing them is a spec-level trap:

```sql
-- ANSI says: this parses as departments CROSS JOIN (employees JOIN projects),
-- NOT (departments, employees) JOIN projects — the ON clause "leaks" onto the last table.
FROM   departments d, employees e JOIN projects p ON p.dept_id = e.dept_id;
-- Many engines reject it; some accept it and mean something you did not intend.
-- RULE: never mix comma joins with JOIN ... ON.
```

---

## 10. Set Operations — `UNION`, `INTERSECT`, `EXCEPT`

Set operations combine **whole result sets**, row-wise, not table-wise. They are the post-join tool for reconciliation and diffing.

| Operator | Result | Duplicate handling | MySQL 8.0.31+ |
|---|---|---|---|
| `UNION` | rows in A **or** B | **removes** duplicates (sort/hash) | ✅ |
| `UNION ALL` | rows in A **or** B | keeps all — **fastest** (no dedup) | ✅ |
| `INTERSECT` | rows in A **and** B | removes duplicates | ✅ (8.0.31+) |
| `EXCEPT` / `MINUS` | rows in A **but not** B | removes duplicates | ✅ (`EXCEPT` 8.0.31; `MINUS` in Oracle only) |

### 10.1 Hard rules (frequently tested)

1. **Same number of columns** in every branch — otherwise a syntax error.
2. **Compatible types, positionally** — the first query defines the column names and types; a `VARCHAR` + `INTEGER` union fails or coerces by engine rules.
3. `ORDER BY` / `LIMIT` apply to the **whole combined statement**, not to a branch. Put them at the very end and prefer **positional** `ORDER BY 1` (or the first query's alias) because typed names are not inherited from branch 2.
4. **Precedence:** `INTERSECT` binds **tighter** than `UNION` and `EXCEPT` (ANSI). Use parentheses to make intent explicit.
5. `UNION` removes duplicates using **NULL-safe** equality: `NULL` and `NULL` are duplicates.
6. Duplicate removal ignores the values, but `SELECT DISTINCT` + `UNION ALL` is **not** the same as `UNION` (it dedups per branch, not across the union).

```sql
-- 1) Combine two disjoint populations, ALL rows (the fast, common case)
SELECT customer_id, 'placed an order' AS note FROM orders
UNION ALL
SELECT customer_id, 'is inactive'    AS note FROM customers WHERE NOT is_active;

-- 2) UNION (dedup) — customers who are both inactive AND have an order
SELECT customer_id FROM customers WHERE NOT is_active
UNION
SELECT customer_id FROM orders;

-- 3) INTERSECT — products that HAVE been ordered
SELECT product_id FROM products
INTERSECT
SELECT product_id FROM order_items;

-- 4) EXCEPT — products never ordered (identical to the NOT EXISTS anti-join in §4.2)
SELECT product_id, product_name FROM products
EXCEPT
SELECT p.product_id, p.product_name
FROM   products p JOIN order_items oi ON oi.product_id = p.product_id;

-- 5) Precedence made explicit
( SELECT product_id FROM products
  INTERSECT
  SELECT product_id FROM order_items )
EXCEPT
SELECT product_id FROM products WHERE is_discontinued;

-- 6) ORDER BY + LIMIT on the whole statement (positional is safest)
SELECT product_id, product_name, price FROM products WHERE category = 'Electronics'
UNION ALL
SELECT product_id, product_name, price FROM products WHERE category = 'Furniture'
ORDER BY 3 DESC
LIMIT 5;

-- 7) MySQL 8.0.31+ emulation of INTERSECT / EXCEPT
--    INTERSECT: INNER JOIN on all columns, or DISTINCT + IN
--    EXCEPT  : LEFT JOIN ... WHERE right.pk IS NULL   (the anti-join from §4.2)
```

---

## 11. Subqueries — Every Position, Every Form

A subquery is a nested `SELECT`. Its **position** determines its allowed shape.

| Position | Name | Rows allowed | Notes |
|---|---|---|---|
| `FROM` | **derived table** / inline view | many | Must be **aliased**; can be joined and grouped |
| `SELECT` | **scalar subquery** | ≤ 1 column, ≤ 1 row at runtime | `NULL` when no row; errors only if >1 row is *returned* |
| `WHERE` / `HAVING` | row / set predicate | many (with `IN`/`EXISTS`) | |
| `JOIN ... ON` | correlated join | many | `LATERAL` needed to reference outer columns in PG/MySQL 8 |

### 11.1 Scalar subqueries in the `SELECT` list

```sql
-- Correlated (re-evaluated per outer row): read as "one lookup column"
SELECT d.dept_name,
       (SELECT COUNT(*)              FROM employees e WHERE e.dept_id = d.dept_id) AS headcount,
       (SELECT COALESCE(SUM(e.salary), 0) FROM employees e WHERE e.dept_id = d.dept_id) AS payroll,
       (SELECT MIN(e.salary)         FROM employees e WHERE e.dept_id = d.dept_id) AS min_salary
FROM   departments d
ORDER  BY d.dept_id;
-- If the index on employees(dept_id) exists, the planner turns this into a nested-loop
-- semi/hash aggregation. With a large table it is often faster to LEFT JOIN + GROUP BY.
```

**Rules:** a scalar subquery returning more than one row **fails** at runtime in PostgreSQL/MySQL ("more than one row returned by a subquery used as an expression"); returning zero rows yields `NULL`, so wrap in `COALESCE` when you need `0`.

### 11.2 Derived tables in `FROM`

```sql
-- Aggregate first, then join: the fan-out fix from §7.2, in derived-table form
SELECT d.dept_name, agg.payroll, RANK() OVER (ORDER BY agg.payroll DESC) AS pay_rank
FROM   departments d
JOIN   (SELECT dept_id, SUM(salary) AS payroll, COUNT(*) AS headcount
        FROM   employees
        GROUP  BY dept_id) agg
  ON   agg.dept_id = d.dept_id
WHERE  agg.payroll > 200000;

-- CTE form (identical semantics, better readability; PostgreSQL 12+ inlines CTEs)
WITH payroll AS (
    SELECT dept_id, SUM(salary) AS payroll FROM employees GROUP BY dept_id
)
SELECT d.dept_name, p.payroll
FROM   departments d JOIN payroll p ON p.dept_id = d.dept_id;
```

### 11.3 Correlated vs non-correlated

| | Non-correlated | Correlated |
|---|---|---|
| References outer columns | ❌ | ✅ (evaluated once per outer row) |
| Can be executed independently | ✅ | ❌ |
| Typical shape | `WHERE x IN (SELECT ...)` | `WHERE EXISTS (SELECT 1 ... WHERE t.id = x.id)` |

### 11.4 `EXISTS` vs `IN` vs `= ANY` vs `JOIN`

```sql
-- Semantically equivalent for "sold products", best → worst
SELECT p.product_name FROM products p
WHERE  EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = p.product_id);

SELECT p.product_name FROM products p
WHERE  p.product_id IN (SELECT oi.product_id FROM order_items oi);

SELECT p.product_name FROM products p
WHERE  p.product_id = ANY (SELECT oi.product_id FROM order_items);

-- ALL / SOME (Oracle-style) quantified comparison
SELECT product_name, price FROM products p
WHERE  price > ALL (SELECT price FROM products WHERE category = 'Electronics');
```

| Situation | Winner |
|---|---|
| Subquery column is `NOT NULL` and small | `IN` (equivalent plan) |
| Subquery column may contain NULL | `EXISTS` |
| Need a column *from the right table* in the output | `JOIN` (semi-joins cannot project B's columns) |
| Parent row must appear once per match, deduped | `EXISTS` |
| Comparing against a computed threshold per row | `EXISTS` |

### 11.5 The "latest row per group" family (a guaranteed interview question)

```sql
-- 1) PostgreSQL DISTINCT ON — cleanest on PG
SELECT DISTINCT ON (customer_id) customer_id, order_id, order_date, total_amount
FROM   orders
ORDER  BY customer_id, order_date DESC, order_id DESC;

-- 2) Window function — portable, single pass (full treatment in Chapter 05)
SELECT customer_id, order_id, order_date, total_amount
FROM  (SELECT o.*, ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY order_date DESC, order_id DESC) AS rn
       FROM   orders o) t
WHERE  rn = 1;

-- 3) Correlated self-join — works everywhere, usually the slowest without an index
SELECT o.*
FROM   orders o
JOIN  (SELECT customer_id, MAX(order_date) AS max_date FROM orders GROUP BY customer_id) m
  ON   m.customer_id = o.customer_id AND m.max_date = o.order_date;
--   ⚠ ties on order_date produce multiple rows -> add a tiebreaker (order_id) or use 2).

-- 4) LATERAL / CROSS APPLY — the top-N pattern (§3.8)
```

---

## 12. The Join Drill Bank — 25 Interview Queries With Solutions

> **How to use this bank:** cover the right-hand side, run the query against `practice-schema.sql`, and verify against the *expected result* note. These 25 cover practically every join question asked in 0–5 year loops.

**D1. List every employee with their department name and location.**
```sql
SELECT e.first_name, e.last_name, d.dept_name, d.location
FROM   employees e JOIN departments d ON d.dept_id = e.dept_id
ORDER  BY e.employee_id;          -- 11 rows (no join on manager)
```

**D2. Headcount per department, including departments with nobody.**
```sql
SELECT d.dept_name, COUNT(e.employee_id) AS headcount
FROM   departments d LEFT JOIN employees e ON e.dept_id = d.dept_id
GROUP  BY d.dept_name ORDER BY d.dept_name;     -- Research = 0, Marketing = 0
```

**D3. Employees whose department is missing from the departments table.**
```sql
SELECT e.* FROM employees e
LEFT   JOIN departments d ON d.dept_id = e.dept_id
WHERE  d.dept_id IS NULL;        -- anti-join; expected 0 rows (dept_id is NOT NULL)
```

**D4. Departments with no employees AND employees with no department.**
```sql
SELECT COALESCE(d.dept_name,'(none)'), COALESCE(e.first_name,'(none)')
FROM   departments d FULL OUTER JOIN employees e ON e.dept_id = d.dept_id
WHERE  d.dept_id IS NULL OR e.employee_id IS NULL;   -- 2 empty departments
```

**D5. All employees with their manager's name (keep managers with no manager).**
```sql
SELECT e.first_name AS employee, m.first_name AS manager
FROM   employees e LEFT JOIN employees m ON m.employee_id = e.manager_id
ORDER  BY e.employee_id;         -- Asha has NULL manager
```

**D6. Employees who earn more than their manager.**
```sql
SELECT e.first_name AS employee, e.salary AS emp_salary, m.first_name AS manager, m.salary AS mgr_salary
FROM   employees e JOIN employees m ON m.employee_id = e.manager_id
WHERE  e.salary > m.salary;      -- Ishita 135000 > Rohit 98000
```

**D7. Orders with their customer and line-item detail (inner).**
```sql
SELECT c.full_name, o.order_id, o.order_date, o.status, p.product_name, oi.quantity
FROM   customers c
JOIN   orders o     ON o.customer_id = c.customer_id
JOIN   order_items oi ON oi.order_id = o.order_id
JOIN   products p   ON p.product_id = oi.product_id
ORDER  BY o.order_id, oi.order_item_id;      -- 12 rows
```

**D8. Revenue per customer, **including customers who never ordered**.**
```sql
SELECT c.customer_id, c.full_name,
       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS revenue,
       COUNT(o.order_id)                              AS order_count
FROM   customers c
LEFT   JOIN orders o     ON o.customer_id = c.customer_id
LEFT   JOIN order_items oi ON oi.order_id    = o.order_id
GROUP  BY c.customer_id, c.full_name
ORDER  BY revenue DESC;      -- Stark Industries: 0 revenue, 0 orders
```

**D9. Customers who have never placed an order.**
```sql
-- NOT EXISTS
SELECT c.full_name FROM customers c
WHERE  NOT EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.customer_id);   -- Stark Industries
-- LEFT JOIN + IS NULL
SELECT c.full_name FROM customers c
LEFT   JOIN orders o ON o.customer_id = c.customer_id
WHERE  o.order_id IS NULL;
-- EXCEPT
SELECT full_name FROM customers EXCEPT SELECT c.full_name FROM customers c JOIN orders o ON o.customer_id = c.customer_id;
```

**D10. Products that were never sold.**
```sql
SELECT p.product_id, p.product_name FROM products p
LEFT   JOIN order_items oi ON oi.product_id = p.product_id
WHERE  oi.product_id IS NULL;      -- Legacy Monitor, Noise Cancelling Headset
```

**D11. Products sold in **more than one** order.**
```sql
SELECT p.product_name, COUNT(DISTINCT oi.order_id) AS order_count
FROM   products p
JOIN   order_items oi ON oi.product_id = p.product_id
GROUP  BY p.product_name
HAVING COUNT(DISTINCT oi.order_id) > 1
ORDER  BY order_count DESC, product_name;
--   Laptop (orders 1,7), Mouse (2,9), Keyboard (2,6), Chair (3,8), USB-C Hub (3,10)
```

**D12. Every employee × every department (the CROSS JOIN drill).**
```sql
SELECT e.first_name, d.dept_name
FROM   employees e CROSS JOIN departments d;    -- 11 × 5 = 55 rows
```

**D13. Salary bands via a range (non-equi) join.**
```sql
SELECT e.first_name, e.salary, b.band
FROM   employees e
JOIN   (VALUES ('LOW', 0::NUMERIC, 90000),
               ('MID', 90000, 130000),
               ('HIGH', 130000, 9999999)) AS b(band, lo, hi)
  ON   e.salary >= b.lo AND e.salary < b.hi
ORDER  BY e.salary;
```

**D14. Inactive customers who have placed an order.**
```sql
SELECT c.full_name, o.order_id, o.status
FROM   customers c JOIN orders o ON o.customer_id = c.customer_id
WHERE  c.is_active = FALSE;       -- Initech, order 4 (CANCELLED)
```

**D15. Customers who appear in orders but are flagged inactive (set-difference style).**
```sql
SELECT customer_id FROM orders
INTERSECT
SELECT customer_id FROM customers WHERE NOT is_active;      -- {3}
```

**D16. Every country with orders and total active-customer counts (LEFT JOIN to a dimension).**
```sql
SELECT c.country,
       COUNT(DISTINCT c.customer_id)                                        AS customers,
       COUNT(DISTINCT o.order_id) FILTER (WHERE o.status <> 'CANCELLED')     AS live_orders,
       COALESCE(SUM(o.total_amount) FILTER (WHERE o.status <> 'CANCELLED'), 0) AS revenue
FROM   customers c
LEFT   JOIN orders o ON o.customer_id = c.customer_id
GROUP  BY c.country
ORDER  BY country;      -- MySQL: replace FILTER (WHERE ...) with SUM(CASE WHEN ... THEN 1 END)
```

**D17. Order totals **including** cancelled orders' zeroed contribution.**
```sql
SELECT o.order_id, o.status, o.total_amount,
       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS line_value,
       COUNT(oi.order_item_id)                       AS line_count
FROM   orders o
LEFT   JOIN order_items oi ON oi.order_id = o.order_id
GROUP  BY o.order_id, o.status, o.total_amount
ORDER  BY o.order_id;    -- no order loses its row; order 4 (CANCELLED) keeps its line
```

**D18. Top 3 most recent orders per customer.**
```sql
SELECT c.customer_id, c.full_name, t.order_id, t.order_date, t.total_amount
FROM   customers c
CROSS  JOIN LATERAL (
    SELECT o.order_id, o.order_date, o.total_amount
    FROM   orders o
    WHERE  o.customer_id = c.customer_id
    ORDER  BY o.order_date DESC, o.order_id DESC
    LIMIT  3
) t
ORDER  BY c.customer_id, t.order_date DESC;    -- LEFT JOIN LATERAL keeps customers with no orders
```

**D19. Revenue per product category.**
```sql
SELECT p.category,
       COUNT(DISTINCT o.order_id) AS orders,
       SUM(oi.quantity * oi.unit_price) AS revenue
FROM   products p
JOIN   order_items oi ON oi.product_id = p.product_id
JOIN   orders o       ON o.order_id = oi.order_id
WHERE  o.status IN ('PAID','SHIPPED','DELIVERED')
GROUP  BY p.category
ORDER  BY revenue DESC;
```

**D20. The fan-out trap — "correct customer spend" (compare with the wrong version in §7.2).**
```sql
SELECT o.customer_id, SUM(o.total_amount) AS correct_spend
FROM   orders o
GROUP  BY o.customer_id
ORDER  BY correct_spend DESC;    -- Acme Corp: 189000 + 9598 + 28900 = 227498
```

**D21. Employees with no commission vs with commission.**
```sql
SELECT e.first_name, e.commission,
       COALESCE(e.commission, 0) AS commission_or_zero,
       CASE WHEN e.commission IS NULL THEN 'NO_COMMISSION' ELSE 'COMMISSIONED' END AS kind
FROM   employees e
ORDER  BY kind, e.first_name;
```

**D22. Departments whose average salary exceeds the company average.**
```sql
SELECT d.dept_name, AVG(e.salary) AS avg_salary
FROM   departments d
JOIN   employees e ON e.dept_id = d.dept_id
GROUP  BY d.dept_name
HAVING AVG(e.salary) > (SELECT AVG(salary) FROM employees)
ORDER  BY avg_salary DESC;
```

**D23. Orders whose `total_amount` matches the sum of their line items, and those that do not.**
```sql
SELECT o.order_id, o.total_amount,
       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS line_sum,
       o.total_amount = COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS consistent
FROM   orders o
LEFT   JOIN order_items oi ON oi.order_id = o.order_id
GROUP  BY o.order_id, o.total_amount
ORDER  BY o.order_id;    -- ⚠ this is the fan-out-safe form: aggregate the child, then compare
```

**D24. Count of employees per manager, including managers with no reports.**
```sql
SELECT m.first_name AS manager, COUNT(e.employee_id) AS reports
FROM   employees m
LEFT   JOIN employees e ON e.manager_id = m.employee_id
GROUP  BY m.employee_id, m.first_name
ORDER  BY reports DESC, manager;   -- managers with 0 reports still appear
```

**D25. Two-level hierarchy: employee → manager → grand-manager.**
```sql
SELECT e.first_name  AS employee,
       m.first_name  AS manager,
       g.first_name  AS grand_manager
FROM   employees e
LEFT   JOIN employees m ON m.employee_id = e.manager_id
LEFT   JOIN employees g ON g.employee_id = m.manager_id
ORDER  BY e.employee_id;
```

---

## 13. Level-Wise Interview Questions & Answers

### 🟢 Junior (0–2 Years)

**Q1. What is the difference between `INNER` and `LEFT JOIN`?**
A: `INNER JOIN` returns only rows where the predicate matches in **both** tables — unmatched rows on either side disappear. `LEFT JOIN` returns **every** row from the left table; when there is no match, the right-side columns are filled with `NULL`.

**Q2. Can you have a join without an `ON` clause?**
A: Yes — `CROSS JOIN` (or the legacy comma join `FROM a, b`) produces the Cartesian product of both tables. It is useful for generating combinations, and a bug when the `ON` clause is omitted.

**Q3. What does `SELECT * FROM a JOIN b USING (id)` return vs `ON`?**
A: With `USING`, the shared column appears **once** in the output. With `ON`, you get **both** `a.id` and `b.id`.

**Q4. How do you find rows in table A that have no matching row in table B?**
A: `LEFT JOIN ... WHERE b.pk IS NULL`, or `NOT EXISTS`, or `EXCEPT`. Not `NOT IN` if B can contain NULLs.

**Q5. What is a self-join?**
A: A table joined to itself using **two different aliases**, needed for hierarchies (employee/manager), comparing rows within the same table, or finding duplicates.

**Q6. What is the difference between `UNION` and `UNION ALL`?**
A: `UNION` removes duplicate rows (requiring a sort or hash); `UNION ALL` concatenates as-is and is faster. Use `UNION ALL` unless you specifically need deduplication.

### 🟡 Mid-Level (2–5 Years)

**Q7. Why is `WHERE right.col IS NULL` different from putting the filter in the `ON` clause of a `LEFT JOIN`?**
A: In `WHERE`, the `NULL` produced by an unmatched row fails the predicate, so those rows are removed — the query behaves as an `INNER JOIN`. In `ON`, the predicate only controls whether a *match* exists; unmatched left rows are preserved.

**Q8. `EXISTS` vs `IN` — which is faster?**
A: Often identical — modern optimisers convert both to a semi-join. The real criteria are correctness and shape: `EXISTS` is **NULL-safe** and short-circuits at the first match; `IN` materialises the subquery and deduplicates it. Use `JOIN` when you need columns from the right table.

**Q9. What is an anti-join, and how do you write it in three ways?**
A: "Rows in A with no match in B": `LEFT JOIN ... IS NULL`, `NOT EXISTS`, and `EXCEPT`. (`NOT IN` is the fourth and is invalid when B contains NULLs.)

**Q10. How do you prevent a join from multiplying rows when aggregating?**
A: Aggregate the many-side into a derived table/CTE first, then join the aggregated result to the one-side; or use `COUNT(DISTINCT ...)`. The symptom of forgetting is totals that are exactly N times too large.

**Q11. When would you use `RIGHT` or `FULL OUTER JOIN`?**
A: `RIGHT` is semantically identical to a `LEFT` with the tables swapped — I avoid it for readability. `FULL OUTER JOIN` is for reconciliation reports where **both** sides may have unmatched rows (e.g., comparing two snapshots / two regions), and it is not available in MySQL at all — emulate it with `UNION ALL` of two anti-joins.

**Q12. How do you get "the latest record per group" efficiently?**
A: PostgreSQL: `DISTINCT ON (key) ... ORDER BY key, ts DESC`. Portable: `ROW_NUMBER() OVER (PARTITION BY key ORDER BY ts DESC)` filtered to `rn = 1` in a subquery/CTE. For top-N with a small N, `LATERAL ... LIMIT N` with a supporting index is fastest because it reads only N rows per parent.

**Q13. Can you join three or more tables?**
A: Yes — write successive `JOIN` clauses, each with its own `ON`. The engine reorders inner joins freely but preserves the order of outer joins, because reordering an outer join would change which rows survive.

**Q14. What is a bridge/junction table?**
A: The associative table that resolves a many-to-many relationship (e.g. `order_items` between `orders` and `products`). It typically has a composite unique key on both FKs and carries the relationship's own attributes (quantity, unit_price).

### 🔴 Senior (5–8 Years)

**Q15. Why can a `LEFT JOIN` produce more rows than the left table?**
A: Because a join is a Cartesian product filtered by a predicate — if the right table has *k* matches for a left row, *k* rows are produced. Venn diagrams show sets, but joins produce multisets. `DISTINCT`, `GROUP BY`, or aggregation-before-join is the fix when you need one row per parent.

**Q16. Explain when the optimiser picks a Hash Join over a Nested Loop.**
A: Nested loop is chosen when the outer input is small and the inner build/probe can use an index (or is tiny), and when the query can stop early (`LIMIT`). Hash join is chosen for large, unsorted, equality-join inputs, building a hash table on the smaller relation. The risk with hash joins is `work_mem` exhaustion: when the hash table no longer fits, it spills to multi-batch disk files and latency collapses.

**Q17. A query with five `LEFT JOIN`s takes 40 seconds. Where do you look first?**
A: (1) `EXPLAIN (ANALYZE, BUFFERS)` — look for a nested loop exploding over a large outer side, hash joins spilling, or a sort. (2) Every `LEFT JOIN` must have an index on its **right-side join key**. (3) The order of the left joins is fixed by you — making the *null-preserving* side the smallest drivable table matters. (4) Consider whether the outer joins can be rewritten: a `LEFT JOIN` to a table where the predicate in `WHERE` removes NULLs is an inner join in disguise. (5) Consider `LATERAL` for top-N.

**Q18. How does the planner treat `A LEFT JOIN B` differently from `A JOIN B`?**
A: It may not reorder `A LEFT JOIN B` (swapping would change semantics) and may not convert it into a hash/sort-merge semi-join for an anti-join. It also cannot push predicates from `B`'s `WHERE` into the join — it must apply them after the null preservation, which is why `WHERE b.col IS NULL` (anti) is a different plan from `WHERE b.col = x` (inner).

**Q19. What is the most dangerous single character in a join query, and why?**
A: The comma in a legacy comma join (`FROM a, b`) — forgetting the join predicate produces a `CROSS JOIN` that returns `|a| × |b|` rows and can bring production down. That is why ANSI `JOIN ... ON` is mandatory.

**Q20. How would you compare two snapshots of the same table to find inserted, deleted, and changed rows?**
A: A `FULL OUTER JOIN` on the PK with `WHERE left.pk IS NULL OR right.pk IS NULL` for inserts/deletes, plus a column-by-column predicate to detect changes. Equivalent: `EXCEPT` in both directions. This is the standard data-migration and ETL reconciliation query.

### ⚫ Principal / Staff (8+ Years)

**Q21. You need "every customer, their last order, and the count of orders" in one query over 500M rows. How do you design it?**
A: A single query mixing two grains is the fan-out trap — the count multiplies the last-order row. Do it in two steps with different index support: `SELECT ... (SELECT ... ORDER BY customer_id, order_date DESC LIMIT 1)` via a covering index `(customer_id, order_date DESC)` (or a `LATERAL` top-1), and a separate indexed `COUNT` — or a materialised summary table refreshed incrementally. On a 500M-row table, the real answer is usually "this query should not exist; it is a reporting job on a read replica feeding a cache."

**Q22. Why does `NOT EXISTS` sometimes become a hash anti-join and sometimes a nested-loop anti-join, and when does each hurt?**
A: The planner picks a hash anti-join when the inner set is large and unsorted (build the hash of the subquery, probe once); a nested-loop anti-join when an index exists on the inner join column. It hurts if the "inner" side is tiny and correlated with the *outer* side's predicate — then the anti-join is re-executed per outer row and an index on the inner key is mandatory. Cost-based, and the plan changes as statistics change, which is why plan baselines/pinned plans matter.

**Q23. Explain why a `LEFT JOIN` can prevent an index-only scan that an `INNER JOIN` would allow.**
A: With `LEFT JOIN`, the null-preserving side must be driven as the outer table, which can force a full scan of that side and heap fetches for columns not in the index; `INNER JOIN` gives the planner freedom to choose the driving side (and the inner one) based on selectivity. Rewriting `A LEFT JOIN B` where B's columns are required (`WHERE b.col IS NOT NULL`) as an `INNER JOIN` restores planner freedom. See [Chapters 04](./04-Indexing-Architecture-and-B-Trees.md) & [06](./06-Query-Planner-Optimizer-and-Explain.md).

---

## 14. Counter-Questions & Interviewer Traps

**Trap 1: "Draw me the Venn diagram of a `LEFT JOIN` — now tell me why it is wrong."**
It correctly shows *which rows survive*, but it implies set semantics. A `LEFT JOIN` can return **more rows than the left table** has (fan-out) — impossible in the Venn picture. Joins operate on multisets; Venn diagrams operate on sets.

**Trap 2: "`LEFT JOIN` then `WHERE right.col IS NULL` — is that an inner or outer join?"**
An **anti-join**, which is the intended use of the pattern (find missing rows). But `WHERE right.col = 5` immediately after a `LEFT JOIN` is an **inner join in disguise** — a bug in reporting queries.

**Trap 3: "Which is faster: `NOT EXISTS` or `NOT IN`?"**
Wrong question. `NOT IN` may return **no rows at all** (NULL bug) while `NOT EXISTS` is always correct. When `NOT IN` is safe (no NULLs), plans are usually identical — compare with `EXPLAIN`, not folklore.

**Trap 4: "Can you `ORDER BY` an individual branch of a `UNION`?"**
No. `ORDER BY` applies to the combined result. To order a branch you must wrap it in a subquery/CTE — and that ordering is not guaranteed to survive the `UNION`.

**Trap 5: "`SELECT a.* FROM a LEFT JOIN b ON a.id = b.id WHERE b.id IS NULL` — what does it return?"**
Rows of `a` with **no match** in `b`. This is the canonical anti-join, and one of the two highest-frequency interview queries (the other is "latest row per group").

**Trap 6: "What happens if you join on a nullable column?"**
Matches still occur for equal values, but `NULL = NULL` is `UNKNOWN`, so rows with `NULL` in the join column on **both** sides **do not match** in an inner join — they pair only in a full outer join's "both NULL" case, and SQL Server/PostgreSQL will not even match them there.

**Trap 7: "Can `ON` and `WHERE` both contain conditions? Should they?"**
Both are legal. `ON` filters **pairs before null preservation**; `WHERE` filters the **final result**. For an outer join, right-side conditions belong in `ON`; for an inner join the split is purely stylistic.

---

## 15. Rapid-Fire MCQs

**Q1. Which join returns all rows from the left table, with NULLs on the right when unmatched?**
- [ ] A) `INNER JOIN`
- [ ] B) `RIGHT JOIN`
- [ ] C) `LEFT JOIN`
- [ ] D) `CROSS JOIN`
* **Answer:** **C.**

**Q2. `SELECT COUNT(*) FROM departments d LEFT JOIN employees e ON e.dept_id = d.dept_id` for 5 departments (2 empty) and 11 employees returns:**
- [ ] A) 11
- [ ] B) 13
- [ ] C) 5
- [ ] D) 55
* **Answer:** **B.** 11 matched pairs + 2 padded rows for the empty departments = 13.

**Q3. Which is NULL-safe for finding unmatched rows?**
- [ ] A) `WHERE id NOT IN (SELECT id FROM b)`
- [ ] B) `WHERE id NOT EXISTS (SELECT 1 FROM b WHERE b.id = a.id)`
- [ ] C) `WHERE NOT EXISTS (SELECT 1 FROM b WHERE b.id = a.id)`
- [ ] D) `WHERE id != ALL (SELECT id FROM b)`
* **Answer:** **C.**

**Q4. `A CROSS JOIN B` on 11 rows and 5 rows returns:**
- [ ] A) 11
- [ ] B) 5
- [ ] C) 16
- [ ] D) 55
* **Answer:** **D** — the Cartesian product.

**Q5. With `USING (dept_id)`, the output contains:**
- [ ] A) two `dept_id` columns
- [ ] B) one merged `dept_id` column
- [ ] C) no `dept_id`
- [ ] D) an error in MySQL
* **Answer:** **B.**

**Q6. Combining `SELECT ... FROM a` (3 columns) with `SELECT ... FROM b` (4 columns) via `UNION` gives:**
- [ ] A) the first statement's columns
- [ ] B) an error
- [ ] C) 7 columns
- [ ] D) NULL-padded result
* **Answer:** **B** — a `UNION` requires the same number of compatible columns.

**Q7. Which set operation keeps rows present in **both** queries?**
- [ ] A) `UNION`
- [ ] B) `UNION ALL`
- [ ] C) `INTERSECT`
- [ ] D) `EXCEPT`
* **Answer:** **C.**

**Q8. A `LEFT JOIN` followed by `WHERE e.salary > 100000` (on the *nullable* side) behaves like:**
- [ ] A) a `LEFT JOIN`
- [ ] B) an `INNER JOIN`
- [ ] C) a `CROSS JOIN`
- [ ] D) an error
* **Answer:** **B** — the predicate eliminates NULL-padded rows.

**Q9. Which algorithm can stop early and exploit `LIMIT 1`?**
- [ ] A) Hash join
- [ ] B) Sort-merge join
- [ ] C) Nested loop
- [ ] D) Both A and B
* **Answer:** **C.**

**Q10. To find "customers with more than one order", you need:**
- [ ] A) `LEFT JOIN` with `COUNT(DISTINCT order_id)`
- [ ] B) `INNER JOIN` with `GROUP BY customer_id HAVING COUNT(order_id) > 1`
- [ ] C) `CROSS JOIN`
- [ ] D) Both A and B work
* **Answer:** **D** — but with a `LEFT JOIN` you must use `COUNT(order_id)`, not `COUNT(*)`, or the zero-order customer counts as 1.

---

## 16. One-Page Cheat Sheet

```text
+---------------------------+-----------------------------------------------------------+
| JOIN TYPE                 | SEMANTICS                                                  |
+---------------------------+-----------------------------------------------------------+
| INNER JOIN                | Only pairs where ON is TRUE. Unmatched vanish.            |
| LEFT [OUTER] JOIN         | All left rows; unmatched right columns = NULL.            |
| RIGHT [OUTER] JOIN        | All right rows; unmatched left columns = NULL.            |
| FULL [OUTER] JOIN         | All rows of both; unmatched padded with NULL.             |
|                           | MySQL: not available in any version.                   |
| CROSS JOIN                | Cartesian product |A| x |B|. No ON clause.                 |
| SELF JOIN                 | Same table twice with different aliases (hierarchies).   |
| LATERAL / CROSS APPLY     | Subquery referencing columns from earlier FROM items.     |
| NATURAL JOIN              | Joins all same-named columns. BANNED.                     |
| USING (c)                 | Equi-join on c; c appears ONCE in output.                 |
+---------------------------+-----------------------------------------------------------+
| PATTERN                   | SQL                                                        |
+---------------------------+-----------------------------------------------------------+
| Missing right rows        | LEFT JOIN b ON ... WHERE b.pk IS NULL   (anti-join)       |
| Missing right rows (safe) | WHERE NOT EXISTS (SELECT 1 FROM b WHERE b.fk = a.pk)       |
| Rows with >=1 match       | WHERE EXISTS (SELECT 1 FROM b WHERE b.fk = a.pk)          |
| Both-sided mismatch       | FULL OUTER JOIN ... WHERE a.pk IS NULL OR b.pk IS NULL    |
| Rows in A not in B        | A EXCEPT B                                                |
| Rows in A and B           | A INTERSECT B                                             |
| Top-N per parent          | LATERAL (... ORDER BY ... LIMIT N)  or ROW_NUMBER()=k     |
| Latest row per group (PG) | SELECT DISTINCT ON (key) ... ORDER BY key, ts DESC       |
| Zero-fill report gaps     | LEFT JOIN a generated series / calendar dimension         |
| A minus A (duplicates)    | SELF JOIN on a.key AND a.pk < b.pk                        |
| No NULL comparisons       | IS NULL / IS NOT DISTINCT FROM  (never = NULL)            |
| Filter the nullable side  | Put the predicate in ON, not WHERE                       |
+---------------------------+-----------------------------------------------------------+
| PITFALLS                   | FIX                                                        |
+---------------------------+-----------------------------------------------------------+
| NOT IN + NULL             | 0 rows -> NOT EXISTS / LEFT JOIN...IS NULL                |
| LEFT JOIN + WHERE right   | silently becomes INNER JOIN (bug)                         |
| Join a 1:N then SUM       | over-counts by fan-out -> aggregate child first           |
| comma join, missing WHERE | CROSS JOIN -> always ANSI JOIN ... ON                     |
| Comma + JOIN ON mix       | ON leaks onto the last table -> never mix                  |
| COUNT(*) in LEFT JOIN     | counts padded rows -> COUNT(right.pk)                     |
| UNION instead of UNION ALL| needless sort/dedup cost                                 |
| ORDER BY in a UNION branch| ignored -> ORDER BY the whole statement                   |
+---------------------------+-----------------------------------------------------------+
```

---

## 17. Real-World Production Post-Mortems

### Incident 1: The Filter-in-WHERE That Cut Revenue Reporting by 40%

A nightly dashboard aggregated "orders per region" with `regions r LEFT JOIN orders o ON o.region_id = r.id WHERE o.status = 'COMPLETED'`. Because `o.status` was tested in `WHERE`, every region with no completed order **lost its row**, and regions with only pending orders disappeared entirely. Finance saw 40% less revenue for three weeks before someone noticed a missing country. **Fix:** move the predicate into the `ON` clause (`ON o.region_id = r.id AND o.status = 'COMPLETED'`), keep the `LEFT JOIN`, and add a unit test asserting a zero-sales region still appears.

### Incident 2: The Accidental `CROSS JOIN` (60 Trillion Rows)

A refactor dropped one predicate from a three-table join (`FROM a, b, c WHERE a.id = b.id AND b.id = c.id` → the `c` predicate was lost in a merge conflict). The query became `a × b × c` = 5M × 800k × 120 = **4.8 × 10^14 rows**. Postgres taxed the CPU for 22 minutes, saturating the replica's CPU and blocking every other query on it; the incident ended with a `pg_terminate_backend`. **Fixes:** mandatory ANSI `JOIN ... ON`, a `statement_timeout` of 30s for the reporting role, and a CI check that rejects comma joins via `pg_query` parse inspection.

### Incident 3: Fan-Out Triple-Counting of Money

A settlement service joined `payments` to `payment_events` and summed `payments.amount` — counting every payment once per status event. ₹2.6 crore of "extra" settlement flowed through reconciliation. **Fix:** aggregate `payment_events` per payment first, then join; plus a golden-ratio invariant test: `SUM(payments.amount)` in the join query must equal `SUM(amount)` on the base table.

---

## 18. Follow-up Learning
- **Next Chapter:** → [03 · Relational Engine & Storage Architecture Internals](./03-Relational-Engine-and-Storage-Internals.md) — how pages, buffer pools and the WAL make joins fast or slow
- **Previous Chapter:** ← [01 · SQL Fundamentals, Data Types, Constraints & CRUD](./01-SQL-Fundamentals-Data-Types-Constraints-and-CRUD.md)
- **Master Kit Index & Study Roadmap:** ← [01-SQL-Databases README](./README.md)
- **Window functions, recursive CTEs & execution order:** [05 · Advanced SQL, Window Functions & CTEs](./05-Advanced-SQL-Window-Functions-and-CTEs.md)
- **How the optimiser picks hash vs merge vs nested loop:** [06 · Query Planner, Optimizer & Execution Plans](./06-Query-Planner-Optimizer-and-Explain.md)
- **10 hard whiteboard drills (gaps & islands, sessionization, org charts):** [11 · Complex SQL Interview Drills & Problem Solving](./11-Complex-SQL-Interview-Drills.md)
