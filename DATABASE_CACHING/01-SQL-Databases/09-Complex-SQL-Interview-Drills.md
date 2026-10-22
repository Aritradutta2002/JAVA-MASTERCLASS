# 09 · Complex SQL Interview Drills & Problem Solving

> **Level:** Intermediate → Expert  
> **Interview Frequency:** ★★★★★ (The top live coding challenges in FAANG / Tier-1 technical screens)  
> **Target Databases:** PostgreSQL & MySQL 8.0+  
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. The 4 Rules for Whiteboard SQL Interviews](#0-the-4-rules-for-whiteboard-sql-interviews)
- [Drill 01: The Gaps & Islands Problem (Active Login Streaks)](#drill-01-the-gaps--islands-problem-active-login-streaks)
- [Drill 02: Running Totals with Sliding Window Frames](#drill-02-running-totals-with-sliding-window-frames)
- [Drill 03: Top N per Category (`DENSE_RANK` vs `LATERAL`)](#drill-03-top-n-per-category-dense_rank-vs-lateral)
- [Drill 04: Recursive Org Chart Hierarchy with Path & Depth](#drill-04-recursive-org-chart-hierarchy-with-path--depth)
- [Drill 05: Sessionization of Web Clickstreams (30-Min Inactivity Gap)](#drill-05-sessionization-of-web-clickstreams-30-min-inactivity-gap)
- [Drill 06: Keyset Pagination with Multi-Column Tie Breaking](#drill-06-keyset-pagination-with-multi-column-tie-breaking)
- [Drill 07: High-Performance Deduplication (Retaining Latest Record)](#drill-07-high-performance-deduplication-retaining-latest-record)
- [Drill 08: The N-th Highest Salary (Graceful Tie & Null Handling)](#drill-08-the-n-th-highest-salary-graceful-tie--null-handling)
- [Drill 09: Pivot & Transpose without Vendor-Specific Functions](#drill-09-pivot--transpose-without-vendor-specific-functions)
- [Drill 10: Detecting Overlapping Time Ranges & Booking Conflicts](#drill-10-detecting-overlapping-time-ranges--booking-conflicts)

---

## 0. The 4 Rules for Whiteboard SQL Interviews

1. **Clarify Edge Cases First:** Always ask the interviewer: *"Can there be duplicate timestamps?", "How do we handle ties?", "Can values be NULL?", "What should return if the table is empty?"*
2. **Never Default to `SELECT *`:** Explicitly project only the columns required.
3. **Avoid Subqueries in `SELECT`:** A subquery in the `SELECT` projection executes once per row ($O(N)$), degrading execution. Use Window Functions or Joins.
4. **State the Supporting Index:** When you finish writing your query, finish with the masterstroke: *"To execute this query in sub-millisecond time at 10M rows, I would back it with a composite index on `(col1, col2 DESC)`."*

---

## Drill 01: The Gaps & Islands Problem (Active Login Streaks)

### The Challenge:
Given a table of user login dates, find the longest consecutive streak of active login days for each user.

### Mock Schema & Data:
```sql
CREATE TABLE user_logins (
    user_id INT,
    login_date DATE
);
INSERT INTO user_logins VALUES 
    (1, '2026-03-01'), (1, '2026-03-02'), (1, '2026-03-03'), -- Streak: 3 days
    (1, '2026-03-05'), (1, '2026-03-06'),                     -- Streak: 2 days
    (2, '2026-03-01'), (2, '2026-03-03');                     -- Streaks: 1 day each
```

### Expected Output:
| user_id | max_streak_days |
|:---:|:---:|
| 1 | 3 |
| 2 | 1 |

### The Expert Solution:
```sql
WITH deduplicated_logins AS (
    -- 1. Eliminate multiple logins on the same day
    SELECT DISTINCT user_id, login_date 
    FROM user_logins
),
numbered_logins AS (
    -- 2. Subtract ROW_NUMBER() from login_date to form the "Island Key"
    SELECT 
        user_id,
        login_date,
        login_date - (ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY login_date))::int AS island_group
    FROM deduplicated_logins
),
streak_lengths AS (
    -- 3. Count rows in each island
    SELECT 
        user_id,
        island_group,
        COUNT(*) AS streak_length
    FROM numbered_logins
    GROUP BY user_id, island_group
)
-- 4. Select the maximum streak per user
SELECT 
    user_id, 
    MAX(streak_length) AS max_streak_days
FROM streak_lengths
GROUP BY user_id;
```

### The "Aha!" Mathematical Secret:
If dates are consecutive (`2026-03-01, 2026-03-02, 2026-03-03`) and row numbers are consecutive (`1, 2, 3`), their difference is **constant**:
- `2026-03-01 - 1 = 2026-02-28`
- `2026-03-02 - 2 = 2026-02-28`
- `2026-03-03 - 3 = 2026-02-28`
All consecutive dates collapse into the exact same `island_group`! Once dates skip to `2026-03-05` (row 4):
- `2026-03-05 - 4 = 2026-03-01` (A new island is born!).

---

## Drill 02: Running Totals with Sliding Window Frames

### The Challenge:
Calculate the running cumulative account balance after each transaction. When multiple transactions occur on the exact same date, calculate the balance strictly ordered by `transaction_id`.

### Mock Schema & Data:
```sql
CREATE TABLE transactions (
    id INT PRIMARY KEY,
    account_id INT NOT NULL,
    txn_date DATE NOT NULL,
    amount NUMERIC(10,2) NOT NULL
);
INSERT INTO transactions VALUES 
    (1, 101, '2026-01-01',  500.00),
    (2, 101, '2026-01-02', -100.00),
    (3, 101, '2026-01-02',  250.00), -- Same date tie!
    (4, 101, '2026-01-03',  -50.00);
```

### Expected Output:
| id | txn_date | amount | cumulative_balance |
|:---:|:---:|:---:|:---:|
| 1 | 2026-01-01 | 500.00 | 500.00 |
| 2 | 2026-01-02 | -100.00 | 400.00 |
| 3 | 2026-01-02 | 250.00 | 650.00 |
| 4 | 2026-01-03 | -50.00 | 600.00 |

### The Expert Solution:
```sql
SELECT 
    id,
    account_id,
    txn_date,
    amount,
    SUM(amount) OVER (
        PARTITION BY account_id 
        ORDER BY txn_date ASC, id ASC
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS cumulative_balance
FROM transactions;
```
> **Why `ROWS` is Mandatory:** If you omit `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`, SQL defaults to `RANGE`. Under `RANGE`, transactions 2 and 3 would be treated as duplicate peers, and both rows would display `$650.00`, ruining the intermediate balance audit!

---

## Drill 03: Top N per Category (`DENSE_RANK` vs `LATERAL`)

### The Challenge:
Find the top 2 highest-earning employees in every department. If salaries are tied, include all tied employees.

### Mock Schema & Data:
```sql
CREATE TABLE employees (
    id INT PRIMARY KEY,
    name VARCHAR(50),
    dept_id INT,
    salary NUMERIC(10,2)
);
INSERT INTO employees VALUES
    (1, 'Alice',   10, 90000),
    (2, 'Bob',     10, 85000),
    (3, 'Charlie', 10, 85000), -- Tied for 2nd place!
    (4, 'David',   10, 70000),
    (5, 'Eve',     20, 95000),
    (6, 'Frank',   20, 80000);
```

### Approach 1: Window Function (`DENSE_RANK`)
```sql
WITH ranked_employees AS (
    SELECT 
        id, name, dept_id, salary,
        DENSE_RANK() OVER (
            PARTITION BY dept_id 
            ORDER BY salary DESC
        ) AS salary_rank
    FROM employees
)
SELECT id, name, dept_id, salary, salary_rank
FROM ranked_employees
WHERE salary_rank <= 2
ORDER BY dept_id, salary_rank, id;
```

### Approach 2: High-Performance `CROSS JOIN LATERAL` (Scale Optimization)
When `employees` has 50M rows and `departments` has 500 rows, `DENSE_RANK()` scans 50M rows. `LATERAL` with an index on `(dept_id, salary DESC)` reads only $500 \times 2 = 1,000$ index keys:
```sql
SELECT d.id AS dept_id, e.id AS employee_id, e.name, e.salary
FROM departments d
CROSS JOIN LATERAL (
    SELECT id, name, salary
    FROM employees
    WHERE dept_id = d.id
    ORDER BY salary DESC
    LIMIT 2
) e;
```

---

## Drill 04: Recursive Org Chart Hierarchy with Path & Depth

### The Challenge:
Given an employee table where each employee reports to a `manager_id`, generate an organizational tree listing each employee's depth level and full management reporting path (e.g. `"CEO -> VP -> Senior Manager -> Alice"`).

### Mock Schema & Data:
```sql
CREATE TABLE org_chart (
    id INT PRIMARY KEY,
    name VARCHAR(50),
    manager_id INT
);
INSERT INTO org_chart VALUES 
    (1, 'Satya', NULL),
    (2, 'Scott', 1),
    (3, 'Amy',   1),
    (4, 'Kevin', 2),
    (5, 'Julia', 4);
```

### The Expert Solution:
```sql
WITH RECURSIVE employee_hierarchy AS (
    -- 1. Anchor Member: Find Root Executives (manager_id IS NULL)
    SELECT 
        id,
        name,
        manager_id,
        1 AS depth,
        CAST(name AS TEXT) AS hierarchy_path
    FROM org_chart
    WHERE manager_id IS NULL

    UNION ALL

    -- 2. Recursive Member: Join subordinates to parents
    SELECT 
        e.id,
        e.name,
        e.manager_id,
        h.depth + 1,
        h.hierarchy_path || ' -> ' || e.name
    FROM org_chart e
    INNER JOIN employee_hierarchy h ON e.manager_id = h.id
)
SELECT id, name, depth, hierarchy_path
FROM employee_hierarchy
ORDER BY depth, id;
```

### Output:
| id | name | depth | hierarchy_path |
|:---:|:---:|:---:|:---|
| 1 | Satya | 1 | Satya |
| 2 | Scott | 2 | Satya -> Scott |
| 3 | Amy | 2 | Satya -> Amy |
| 4 | Kevin | 3 | Satya -> Scott -> Kevin |
| 5 | Julia | 4 | Satya -> Scott -> Kevin -> Julia |

---

## Drill 05: Sessionization of Web Clickstreams (30-Min Inactivity Gap)

### The Challenge:
A web session is defined as a series of page views by a user where the time between consecutive clicks does not exceed **30 minutes**. If a user is inactive for >30 minutes, the next click starts a brand new session. Assign a unique `session_id` to each page view.

### Mock Schema & Data:
```sql
CREATE TABLE page_views (
    user_id INT,
    view_time TIMESTAMP
);
INSERT INTO page_views VALUES 
    (1, '2026-03-01 10:00:00'),
    (1, '2026-03-01 10:15:00'), -- Gap: 15 mins (Same Session 1)
    (1, '2026-03-01 10:25:00'), -- Gap: 10 mins (Same Session 1)
    (1, '2026-03-01 11:30:00'), -- Gap: 65 mins (New Session 2!)
    (1, '2026-03-01 11:45:00'); -- Gap: 15 mins (Same Session 2)
```

### The Expert Solution:
```sql
WITH time_differences AS (
    -- Step 1: Fetch timestamp of previous click
    SELECT 
        user_id,
        view_time,
        LAG(view_time) OVER (
            PARTITION BY user_id 
            ORDER BY view_time
        ) AS prev_view_time
    FROM page_views
),
session_starts AS (
    -- Step 2: Mark '1' if gap > 30 mins (or if it is the first click)
    SELECT 
        user_id,
        view_time,
        CASE 
            WHEN prev_view_time IS NULL THEN 1
            WHEN view_time - prev_view_time > INTERVAL '30 minutes' THEN 1
            ELSE 0
        END AS is_new_session
    FROM time_differences
)
-- Step 3: Cumulative sum of 'is_new_session' generates the session ID!
SELECT 
    user_id,
    view_time,
    SUM(is_new_session) OVER (
        PARTITION BY user_id 
        ORDER BY view_time
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS session_id
FROM session_starts;
```

---

## Drill 06: Keyset Pagination with Multi-Column Tie Breaking

### The Challenge:
Given a table of 50 million articles sorted by `published_at DESC`, paginate the results in batches of 20. Ensure pagination is completely stable even if multiple articles share the exact same `published_at` timestamp.

### The Production Query:
```sql
-- Client passes the cursor values of the 20th item from the previous page:
-- Cursor: last_published_at = '2026-03-15 14:00:00+00', last_id = 948210

SELECT id, title, published_at, author_id
FROM articles
WHERE (published_at, id) < ('2026-03-15 14:00:00+00'::timestamptz, 948210)
ORDER BY published_at DESC, id DESC
LIMIT 20;
```

### Required Supporting B+ Tree Index:
```sql
CREATE INDEX idx_articles_keyset ON articles (published_at DESC, id DESC);
```
* **Performance Guarantee:** B+ tree seeks directly to `(published_at, id)` in **0.05ms**, then streams 20 contiguous leaf keys. Zero offset skipping waste!

---

## Drill 07: High-Performance Deduplication (Retaining Latest Record)

### The Challenge:
Due to a distributed webhook retry glitch, duplicate customer records were inserted. Retain only the record with the latest `updated_at` (break ties using the highest `id`), and delete all other duplicate rows efficiently.

### The Expert Solution:
```sql
WITH duplicate_records AS (
    SELECT 
        id,
        ROW_NUMBER() OVER (
            PARTITION BY email 
            ORDER BY updated_at DESC, id DESC
        ) AS row_num
    FROM customers
)
DELETE FROM customers
WHERE id IN (
    SELECT id 
    FROM duplicate_records 
    WHERE row_num > 1
);
```

---

## Drill 08: The N-th Highest Salary (Graceful Tie & Null Handling)

### The Challenge:
Find the $N$-th highest distinct salary from the `salaries` table. If there is no $N$-th highest salary (e.g. table has fewer than $N$ records), return `NULL`.

### The Expert Solution:
```sql
-- Parameter: N = 2
SELECT (
    SELECT DISTINCT salary 
    FROM employees
    ORDER BY salary DESC
    OFFSET 1 LIMIT 1 -- (N - 1)
) AS nth_highest_salary;
```
> **Why Wrap in Outer Scalar `SELECT (...)`:** If you run plain `SELECT DISTINCT salary ... OFFSET 1 LIMIT 1` on a 1-row table, SQL returns **an empty result set (0 rows)**, which causes client ORMs to throw errors. Wrapping it in `SELECT ( ... ) AS nth_highest_salary` guarantees SQL returns **1 row with value `NULL`**.

---

## Drill 09: Pivot & Transpose without Vendor-Specific Functions

### The Challenge:
Given monthly sales by department, transpose the rows into columns (`Q1`, `Q2`, `Q3`, `Q4`) using standard ANSI SQL without proprietary `PIVOT` keywords.

### Mock Schema & Data:
```sql
CREATE TABLE quarterly_sales (
    dept_name VARCHAR(50),
    quarter VARCHAR(10),
    revenue NUMERIC(10,2)
);
INSERT INTO quarterly_sales VALUES 
    ('Engineering', 'Q1', 10000), ('Engineering', 'Q2', 15000),
    ('Marketing',   'Q1', 5000),  ('Marketing',   'Q3', 8000);
```

### The Expert Solution (Conditional Aggregation):
```sql
SELECT 
    dept_name,
    COALESCE(SUM(revenue) FILTER (WHERE quarter = 'Q1'), 0) AS q1_revenue,
    COALESCE(SUM(revenue) FILTER (WHERE quarter = 'Q2'), 0) AS q2_revenue,
    COALESCE(SUM(revenue) FILTER (WHERE quarter = 'Q3'), 0) AS q3_revenue,
    COALESCE(SUM(revenue) FILTER (WHERE quarter = 'Q4'), 0) AS q4_revenue,
    SUM(revenue) AS total_annual_revenue
FROM quarterly_sales
GROUP BY dept_name;
```

---

## Drill 10: Detecting Overlapping Time Ranges & Booking Conflicts

### The Challenge:
Given a table of hotel room reservations with `check_in` and `check_out` dates, write a query to detect if a new booking request (`room_id = 101, [2026-04-10, 2026-04-15)`) conflicts with any existing confirmed reservation.

### The Overlap Invariant Formula:
Two intervals $[A_{\text{start}}, A_{\text{end}})$ and $[B_{\text{start}}, B_{\text{end}})$ overlap if and only if:
$$\text{Start}_A < \text{End}_B \ \mathbf{AND} \ \text{End}_A > \text{Start}_B$$

### Standard SQL Solution:
```sql
SELECT id, room_id, check_in, check_out
FROM reservations
WHERE room_id = 101
  AND status = 'CONFIRMED'
  AND check_in < '2026-04-15'::date
  AND check_out > '2026-04-10'::date;
```

### The Staff Engineer Solution: PostgreSQL Range Types & GiST Exclusion
Instead of writing queries that can suffer race conditions, enforce it at the database constraint level:
```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservations (
    id SERIAL PRIMARY KEY,
    room_id INT NOT NULL,
    booking_dates DATERANGE NOT NULL,
    -- Physically prevents overlapping bookings at the kernel level!
    EXCLUDE USING gist (room_id WITH =, booking_dates WITH &&)
);
```

---

## 11. Follow-up Learning
- **Next Chapter:** → [Master Kit Index & Study Roadmap](./README.md)
- **Previous Chapter:** ← [08 · Senior Backend Interview Q&A & Scenarios](./08-Senior-Backend-Interview-QA-and-Scenarios.md)
