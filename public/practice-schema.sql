-- ============================================================================
-- 01-SQL-Databases · PRACTICE SCHEMA (used by Chapter 01 & Chapter 02)
-- Target: PostgreSQL 15+  (MySQL 8.0+ notes are given inline as comments)
--
-- A compact but realistic "order management" schema with 6 tables:
--   departments 1---N employees N---1 (self join via manager_id)
--   customers   1---N orders 1---N order_items N---1 products
--
-- Run this file once, then every query in the masterclass chapters runs as-is.
-- ============================================================================

DROP TABLE IF EXISTS order_items, orders, products, customers, employees, departments CASCADE;

CREATE TABLE departments (
    dept_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,   -- MySQL: INT AUTO_INCREMENT
    dept_name   VARCHAR(100)   NOT NULL,
    location    VARCHAR(100),
    budget      NUMERIC(14, 2) DEFAULT 0,                           -- NULL = budget not set yet
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now()              -- MySQL: DATETIME / TIMESTAMP
);

CREATE TABLE employees (
    employee_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name  VARCHAR(50)  NOT NULL,
    last_name   VARCHAR(50)  NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    hire_date   DATE         NOT NULL,
    salary      NUMERIC(12, 2) NOT NULL CHECK (salary > 0),
    commission  NUMERIC(12, 2),                                    -- NULL = no commission
    manager_id  BIGINT,                                             -- self-reference (FK below)
    dept_id     BIGINT NOT NULL REFERENCES departments (dept_id)
                               ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_employee_manager
        FOREIGN KEY (manager_id) REFERENCES employees (employee_id)
        ON DELETE SET NULL,                                        -- manager leaves -> report keeps row
    CONSTRAINT uq_employee_name UNIQUE (first_name, last_name)
);

CREATE TABLE customers (
    customer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name   VARCHAR(150) NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    country     CHAR(2)      NOT NULL DEFAULT 'IN',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE products (
    product_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_name VARCHAR(200) NOT NULL,
    category    VARCHAR(50)  NOT NULL,
    price       NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    stock_qty   INTEGER      NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
    is_discontinued BOOLEAN  NOT NULL DEFAULT FALSE
);

CREATE TABLE orders (
    order_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id  BIGINT NOT NULL REFERENCES customers (customer_id),
    order_date   DATE   NOT NULL DEFAULT CURRENT_DATE,
    status       VARCHAR(20) NOT NULL
                 CHECK (status IN ('PENDING','PAID','SHIPPED','DELIVERED','CANCELLED')),
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0)
);

CREATE TABLE order_items (
    order_item_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id      BIGINT NOT NULL REFERENCES orders (order_id)   ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products (product_id),
    quantity      INTEGER     NOT NULL CHECK (quantity > 0),
    unit_price    NUMERIC(10, 2) NOT NULL,
    CONSTRAINT uq_order_product UNIQUE (order_id, product_id)    -- one line per product per order
);

-- ---------------------------------------------------------------------------
-- SEED DATA
-- ---------------------------------------------------------------------------
INSERT INTO departments (dept_name, location, budget) VALUES
    ('Engineering', 'Bengaluru', 12500000.00),
    ('Sales',      'Mumbai',    4800000.00),
    ('Support',    'Pune',     1900000.00),
    ('Research',   'Bengaluru',  6200000.00),   -- will stay empty (for OUTER JOIN drills)
    ('Marketing',  'Delhi',     NULL);          -- NULL budget (for NULL drills)

INSERT INTO employees (first_name, last_name, email, hire_date, salary, commission, manager_id, dept_id) VALUES
    ('Asha',  'Verma',  'asha.verma@corp.io',  '2015-03-01', 210000, NULL, NULL, 1),
    ('Rahul', 'Mehta',  'rahul.mehta@corp.io', '2017-06-15', 155000, 12000, 1,    1),
    ('Neha',  'Iyer',   'neha.iyer@corp.io',   '2018-01-20', 142000, 8000,  1,    1),
    ('Vikram','Rao',    'vikram.rao@corp.io',  '2019-09-10', 138000, 9500,  2,    1),
    ('Priya', 'Nair',   'priya.nair@corp.io',  '2020-02-11', 126000, NULL, 2,    2),
    ('Arjun', 'Sharma', 'arjun.sharma@corp.io','2021-07-01', 118000, 15000, 5,   2),
    ('Kavya', 'Menon',  'kavya.menon@corp.io', '2021-11-23', 102000, NULL, 5,    2),
    ('Rohit', 'Gupta',  'rohit.gupta@corp.io', '2022-04-05',  98000, NULL, 3,    3),
    ('Sneha', 'Jain',   'sneha.jain@corp.io',  '2023-01-16',  89000, NULL, 3,    3),
    ('Dev',   'Patel',  'dev.patel@corp.io',   '2023-08-30',  76000, 4000,  3,    3),
    ('Ishita','Bose',   'ishita.bose@corp.io', '2024-05-20', 135000, NULL, 8,    3);
    -- Ishita out-earns her manager Rohit (98,000) -> the "salary > manager" self-join drill.
    -- NOTE: dept_id = 4 (Research) has ZERO employees by design.

INSERT INTO customers (full_name, email, country, is_active) VALUES
    ('Acme Corp',      'billing@acme.com',     'US', TRUE),
    ('Globex India',   'ap@globex.in',         'IN', TRUE),
    ('Initech',        'ops@initech.com',      'US', FALSE),
    ('Umbrella Ltd',   'payments@umbrella.co', 'GB', TRUE),
    ('Soylent Co',     'ap@soylent.com',       'IN', TRUE),
    ('Hooli',          'ap@hooli.com',         'US', TRUE),
    ('Stark Industries','finance@stark.com',   'US', TRUE);   -- no orders by design

INSERT INTO products (product_name, category, price, stock_qty, is_discontinued) VALUES
    ('Laptop Pro 14"',      'Electronics', 189000.00, 42,  FALSE),
    ('Wireless Mouse',      'Electronics',  1299.00, 350, FALSE),
    ('Mechanical Keyboard', 'Electronics',  8499.00, 88,  FALSE),
    ('Standing Desk',       'Furniture',   42500.00, 15,  FALSE),
    ('Ergonomic Chair',     'Furniture',   28900.00, 22,  FALSE),
    ('USB-C Hub',           'Accessories',   3999.00, 210, FALSE),
    ('Legacy Monitor',      'Electronics',   9990.00, 0,   TRUE),   -- discontinued, NEVER SOLD
    ('Noise Cancelling Headset','Accessories',15999.00,64, FALSE),  -- NEVER SOLD (anti-join drill)
    ('Laptop Sleeve',       'Accessories',    999.00, 120, FALSE);   -- sold in order 5 only

INSERT INTO orders (customer_id, order_date, status, total_amount) VALUES
    (1, '2025-01-15', 'DELIVERED', 189000.00),
    (1, '2025-02-10', 'SHIPPED',    9598.00),
    (2, '2025-01-22', 'DELIVERED', 71399.00),
    (3, '2025-03-02', 'CANCELLED',  0.00),
    (4, '2025-03-18', 'PAID',      42500.00),
    (5, '2025-04-01', 'DELIVERED', 12398.00),
    (2, '2025-04-19', 'DELIVERED', 56799.00),
    (6, '2025-05-05', 'PENDING',   15999.00),
    (1, '2025-06-11', 'DELIVERED', 28900.00),
    (4, '2025-06-30', 'SHIPPED',    3999.00);

INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES
    (1, 1, 1, 189000.00),
    (2, 2, 2,   1299.00),
    (2, 3, 1,   8499.00),          -- note: manual price differs from product price
    (3, 5, 1,  28900.00),
    (3, 6, 4,   3999.00),          -- 4 x 3999 = 15996, not 71399 (order total is authoritative-agnostic)
    (4, 4, 1,  42500.00),          -- CANCELLED order still has line items (data-realism drill)
    (5, 9, 1,    999.00),
    (6, 3, 2,   8499.00),          -- 16998
    (7, 1, 3,  189000.00),
    (8, 5, 2,  28900.00),
    (9, 2, 5,   1299.00),
    (10, 6, 1,  3999.00);

-- ---------------------------------------------------------------------------
-- CONVENIENCE VIEWS used by the chapters
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_order_revenue AS
SELECT o.order_id,
       o.customer_id,
       o.order_date,
       o.status,
       SUM(oi.quantity * oi.unit_price) AS line_total
FROM orders o
JOIN order_items oi ON oi.order_id = o.order_id
GROUP BY o.order_id, o.customer_id, o.order_date, o.status;

CREATE OR REPLACE VIEW v_active_customers AS
SELECT customer_id, full_name, country
FROM customers
WHERE is_active;

-- ---------------------------------------------------------------------------
-- SANITY CHECK
-- ---------------------------------------------------------------------------
SELECT 'departments'  AS tbl, COUNT(*) FROM departments
UNION ALL SELECT 'employees',   COUNT(*) FROM employees
UNION ALL SELECT 'customers',   COUNT(*) FROM customers
UNION ALL SELECT 'products',    COUNT(*) FROM products
UNION ALL SELECT 'orders',      COUNT(*) FROM orders
UNION ALL SELECT 'order_items', COUNT(*) FROM order_items;
