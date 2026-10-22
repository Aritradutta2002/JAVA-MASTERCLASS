# 01 · Schema Design & Normalization

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★☆ ("Design the schema for X")
> **Prerequisite:** [10 · Caching](../10-Caching/README.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — Integrity First, Performance Second](#2-mental-model--integrity-first-performance-second)
- [3. Normal Forms, Practically](#3-normal-forms-practically)
- [4. Denormalisation — When and Why](#4-denormalisation--when-and-why)
- [5. Keys: Natural, Surrogate, Composite](#5-keys-natural-surrogate-composite)
- [6. Data Types & Constraints](#6-data-types--constraints)
- [7. Relationships & Cardinality](#7-relationships--cardinality)
- [8. Modelling Patterns](#8-modelling-patterns)
- [9. Money, Time and Other Traps](#9-money-time-and-other-traps)
- [10. Schema Evolution & Migrations](#10-schema-evolution--migrations)
- [11. Code Examples](#11-code-examples)
- [12. Common Mistakes & Gotchas](#12-common-mistakes--gotchas)
- [13. Interview Questions & Model Answers](#13-interview-questions--model-answers)
- [14. Self-Check & Revision Card](#14-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> Design for **integrity first, then performance**. Normalise to 3NF by default — it eliminates
> update anomalies and is the cheapest place to prevent bad data. Denormalise only with a
> **measured** query that needs it, and then own the consistency (triggers, application code,
> or a materialised view — and state which).
>
> **Surrogate keys** (`bigint identity` / UUID) for primary keys, with a **natural unique
> constraint** on the real-world identifier. Never expose sequential ids to the public if
> enumeration matters — use UUIDs externally or a separate public id.
>
> **Constraints are documentation the database enforces**: `NOT NULL`, `UNIQUE`, `CHECK`,
> `FOREIGN KEY`. Adding them later is expensive; adding them never is how you get orphan rows
> and duplicate customers.
>
> Money is `NUMERIC(19,4)` (never `float`), time is `timestamptz` in UTC, enums are lookup
> tables or constrained `varchar` (not `ENUM(1,2,3)`), booleans are `boolean` (not `char(1)`).

---

## 1. What & Why

A schema is the longest-lived artifact in a system. Code gets rewritten every two years; the
`orders` table survives a decade, with five applications built on it. That asymmetry is why
schema decisions are worth more deliberation than code decisions.

Three costs you pay forever if you get it wrong:

1. **Data corruption you can't detect.** Without constraints, "email" becomes `NULL` for 3% of
   rows and you discover it during a billing run.
2. **Query patterns the indexes can't serve.** A schema shaped around "save the form as JSON"
   makes every report a full scan.
3. **Migrations you can't run.** Adding `NOT NULL` to a 400M-row table without a rewrite is a
   project, not a ticket.

**The two questions to ask before writing DDL:**

- *What facts does this table assert?* (Each row = one fact, with a stable identity.)
- *What must never be true?* (That's your constraint list.)

---

## 2. Mental Model — Integrity First, Performance Second

```
        ┌──────────────────────────────────────────────────┐
        │  THE DATABASE IS THE LAST LINE OF DEFENCE        │
        │                                                  │
        │  validation in the app   → skippable (new client)│
        │  validation in the API   → skippable (batch job) │
        │  validation in the DB    → ALWAYS ENFORCED       │
        └──────────────────────────────────────────────────┘

   normalise ──► one fact in one place ──► no update anomalies
        │
        └─ only AFTER this does denormalisation make sense,
           and then the duplication is a deliberate, owned cache
```

**The single most useful rule:**

> *Every row must have a stable primary key, and every non-key column must depend on the key,
> the whole key, and nothing but the key.* — that's 2NF/3NF in one sentence, and saying it
> this way in an interview reads as understanding rather than memorisation.

---

## 3. Normal Forms, Practically

### 1NF — atomic values

No repeating groups, no comma-separated lists.

```sql
-- ❌ violates 1NF
CREATE TABLE orders (id bigint primary key, item_ids text);   -- "1,2,3"

-- ✅
CREATE TABLE order_items (
  order_id bigint not null references orders(id),
  item_id  bigint not null references items(id),
  quantity int    not null check (quantity > 0),
  primary key (order_id, item_id)
);
```

`jsonb` columns are a legitimate 1NF exception when the data is genuinely a document (an
audit payload, a webhook body) and you never query into it relationally. If you filter or join
on a JSON field, it belongs in a column.

### 2NF — no partial dependency

Only applies to composite keys. Every non-key column must depend on the **whole** key.

```sql
-- ❌ customer_name depends only on customer_id, not (order_id, item_id)
CREATE TABLE order_items (
  order_id bigint, item_id bigint, customer_id bigint, customer_name text,
  primary key (order_id, item_id)
);

-- ✅
CREATE TABLE order_items (order_id bigint, item_id bigint, primary key (order_id, item_id));
CREATE TABLE orders (id bigint primary key, customer_id bigint not null);
CREATE TABLE customers (id bigint primary key, name text not null);
```

### 3NF — no transitive dependency

A non-key column must not depend on another non-key column.

```sql
-- ❌ city and postcode depend on zip, not on customer_id
CREATE TABLE customers (id bigint primary key, zip text, city text, postcode text);

-- ✅
CREATE TABLE customers (id bigint primary key, address_id bigint not null);
CREATE TABLE addresses (id bigint primary key, zip text, city text, postcode text);
```

### Beyond 3NF (know the names, rarely apply)

- **BCNF** — every determinant is a candidate key. Relevant when a table has overlapping
  composite candidate keys.
- **4NF** — no independent multi-valued dependencies (two separate many-to-many lists in one
  table).
- **5NF** — join dependencies. Academic in practice.

### The anomalies 3NF prevents

| Anomaly | Example | Cost |
|---|---|---|
| **Update** | Customer moves; `customer_city` must change in 200 order rows; 3 fail | inconsistent data |
| **Insert** | Can't record a warehouse until it has stock | lost capability |
| **Delete** | Deleting the last order erases the only record of a customer | data loss |

These are the reasons to answer "3NF" when asked, and to explain the *anomaly* rather than
reciting definitions.

---

## 4. Denormalisation — When and Why

Denormalise when **all four** are true:

1. There's a measured performance problem (a query plan, not a hunch).
2. The join or aggregate is on a hot path (page load, not a nightly report).
3. You can name the consistency mechanism.
4. You can name the repair procedure when it drifts.

### Legitimate forms

| Form | Example | Consistency owner |
|---|---|---|
| **Materialised aggregate** | `orders.total_cents` computed from items | application, in the same transaction |
| **Denormalised read column** | `customers.display_name` copied to `orders` | snapshot semantics — deliberately frozen at order time |
| **Materialised view** | nightly rollup table | refresh job (document the lag) |
| **JSONB cache column** | `orders.shipping_snapshot` | immutable by design |
| **Counter column** | `posts.comment_count` | trigger or app, reconciled by a job |

**Snapshot collation vs live denormalisation.** Copying `customer_name` into `orders` is not a
bug — it's *correct*: an invoice must show the name at purchase time, not after a name change.
Copying `customer_email` into `orders` **for display elsewhere** is a bug. Know which one
you're doing and say so; this distinction is a strong interview signal.

### The reconciliation requirement

If you denormalise, you need a job that detects drift:

```sql
-- find orders whose total doesn't match their items
SELECT o.id, o.total_cents, SUM(oi.unit_price_cents * oi.quantity) AS actual
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
GROUP BY o.id, o.total_cents
HAVING o.total_cents <> SUM(oi.unit_price_cents * oi.quantity)
LIMIT 100;
```

If you can't write that query, you don't own the denormalisation.

---

## 5. Keys: Natural, Surrogate, Composite

| Key type | Example | Pros | Cons |
|---|---|---|---|
| **Surrogate** | `bigint GENERATED ALWAYS AS IDENTITY` | small, stable, never changes, good index locality | meaningless; needs a join to be useful |
| **Natural** | `iso_country_code char(2)` | meaningful, no join | changes (email changes, domains move) |
| **UUID v4** | `gen_random_uuid()` | globally unique, no coordination, non-enumerable | 16 bytes, random insert order → index bloat |
| **UUID v7 / ULID** | time-ordered | non-enumerable **and** insert-friendly | newer, less tooling |

### The recommendation

```sql
CREATE TABLE customers (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id   uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE,   -- what the API exposes
  email       citext NOT NULL,                                  -- natural key candidate
  created_at  timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT customers_email_key UNIQUE (email)
);
```

- **Surrogate `bigint` PK** for joins and FK locality (8 bytes, monotonic).
- **`public_id` UUID** for anything external — prevents enumeration and lets you change the
  internal representation later.
- **Natural unique constraint** on email so duplicates are impossible.

### UUID trade-off to state

Random UUIDs as a clustered primary key in Postgres/MySQL cause **page splits** and index
bloat because inserts land at random positions. Time-ordered UUIDv7 fixes that. If you must use
UUID PKs, use v7 (or `uuid_generate_v7` / ULID) or store a `bigint` and add the UUID as a
secondary unique column.

### Composite keys

Right for pure join tables (`order_id, item_id`), wrong when the composite contains a mutable
component. `PRIMARY KEY (tenant_id, id)` is common for multi-tenant schemas and gives good
locality for tenant-scoped queries.

---

## 6. Data Types & Constraints

### Type choices that matter

| Data | Right | Wrong | Why |
|---|---|---|---|
| Money | `numeric(19,4)` | `float`, `double` | binary floats can't represent 0.10 |
| Money (cache) | `bigint` minor units | `numeric` | exact, fast, no rounding surprises |
| Timestamps | `timestamptz` (UTC) | `timestamp` (no tz) | DST and multi-region bugs |
| Date only | `date` | `timestamp` at midnight | precision invites bugs |
| True/false | `boolean` | `char(1)` `'Y'/'N'` | clarity, index size, no third state |
| Enumerations | lookup table or `text` + `CHECK` | MySQL `ENUM`, or `int` codes | adding a value is a migration; ints are unreadable |
| Short strings | `varchar(n)` | `char(n)` (pads) | padding wastes space and confuses comparisons |
| Identifiers | `bigint` / `uuid` | `varchar(36)` | 4x–8x storage and index size |
| JSON documents | `jsonb` | `json` (text) | `jsonb` is indexed and parsed |
| IP addresses | `inet` | `varchar(45)` | validation and subnet operators |

### Constraints as design

```sql
CREATE TABLE subscriptions (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  customer_id  bigint NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
  plan         text   NOT NULL CHECK (plan IN ('basic','pro','enterprise')),
  started_at   timestamptz NOT NULL DEFAULT now(),
  ended_at     timestamptz,
  seats        int NOT NULL DEFAULT 1 CHECK (seats BETWEEN 1 AND 1000),
  CONSTRAINT sub_ends_after_start CHECK (ended_at IS NULL OR ended_at > started_at),
  CONSTRAINT one_active_per_customer EXCLUDE USING gist (
      customer_id WITH =, tstzrange(started_at, ended_at) WITH &&
  )
);
```

That `EXCLUDE` constraint enforces "one active subscription per customer" **in the database** —
the kind of invariant most teams check in Java and get wrong under concurrency.

### `NOT NULL` is a performance feature

- `NULL` columns need a null bitmap and complicate index-only scans.
- Three-valued logic (`x <> 5` excludes NULLs) causes silent query bugs.
- Every `NOT NULL` you add is a bug you can never ship again.

Prefer `NOT NULL` with a sensible default; use `NULL` only when "unknown" is a real state.

### Foreign keys

- Enforce them. `ON DELETE RESTRICT` (default) protects you; `CASCADE` is convenient and
  dangerous (a delete can nuke unrelated data).
- FKs create an index requirement on the child column — **Postgres does not create it
  automatically**, so `DELETE` on the parent does a full scan of the child. Add the index.
- For very high write volume, some teams drop FKs and enforce in the app; if you do, say why
  and add the reconciliation query.

---

## 7. Relationships & Cardinality

| Relationship | Implementation | Note |
|---|---|---|
| 1:1 | FK + `UNIQUE` on the child, or same table | often just split by access pattern/size |
| 1:N | FK on the "many" side | index it |
| M:N | join table with a composite PK | add columns for attributes of the relationship |
| M:N with history | join table with `valid_from`/`valid_to` | temporal modelling |
| Self-referencing | `parent_id` FK to same table | watch for cycles; recursive CTEs to query |

**A join table is a first-class entity** when it has attributes: `enrollments(student_id,
course_id, enrolled_at, grade)`. Name it for the relationship (`enrollments`), not
`student_course`.

---

## 8. Modelling Patterns

### 8.1 Soft delete

```sql
deleted_at timestamptz
```

- ✅ Undo, audit, FK integrity preserved.
- ❌ **Every query needs `WHERE deleted_at IS NULL`** — forget once and you leak deleted data.
  Partial indexes and views help; consider `CREATE VIEW active_orders AS SELECT * FROM orders
  WHERE deleted_at IS NULL`.
- ❌ Unique constraints must become partial (`UNIQUE (email) WHERE deleted_at IS NULL`).

### 8.2 Temporal / history tables

Two approaches:

- **Row versioning** — `orders_history` with `valid_from`/`valid_to`, written by a trigger.
- **Event sourcing** — an append-only event table; current state is a projection.

For audit requirements (who changed what when), a trigger-written history table is the lowest-
effort correct answer, and it survives application bugs.

### 8.3 Multi-tenancy

| Approach | Isolation | Cost |
|---|---|---|
| **Shared table + `tenant_id`** | logical only | cheapest, risk of missing a filter |
| **Schema per tenant** | good | migration complexity at scale |
| **Database per tenant** | strongest | expensive, hard to aggregate |

For shared tables, make `tenant_id` the leading column of every index (and often of the PK) so
tenant queries stay local, and enforce the filter with **Row Level Security** rather than
trusting developers.

### 8.4 Polymorphic associations

```sql
-- ❌ no referential integrity possible
comments (id, target_type text, target_id bigint)

-- ✅ separate nullable FKs with a check exactly one is set
comments (id, post_id bigint null references posts, photo_id bigint null references photos,
          CHECK (num_nonnulls(post_id, photo_id) = 1))
```

The second form gives you real FKs and indexable columns. It's more columns; it's also correct.

### 8.5 Audit columns

```sql
created_at timestamptz NOT NULL DEFAULT now(),
updated_at timestamptz NOT NULL DEFAULT now(),
created_by bigint,
updated_by bigint,
version    bigint NOT NULL DEFAULT 0        -- optimistic locking
```

`updated_at` should be set by a trigger, not by every code path — otherwise one forgotten
`UPDATE` breaks your cache invalidation and your "changed since" sync.

---

## 9. Money, Time and Other Traps

### Money

```sql
-- ✅ exact, 4 decimal places (supports unit prices and tax)
amount_cents bigint        -- or numeric(19,4)
currency     char(3) NOT NULL REFERENCES currencies(code)
```

- **Never `float`/`double`** for money. `0.1 + 0.2 != 0.3` and it will show up in a ledger.
- **Always store the currency** alongside the amount. Multi-currency systems that forget this
  cannot be fixed later without guessing.
- **Rounding**: decide where rounding happens (per line, per invoice) and do it once, in the
  same place, with a documented rule.

### Time

```sql
occurred_at  timestamptz NOT NULL     -- an instant, stored in UTC
business_day date        NOT NULL     -- a calendar day, no timezone
```

- **`timestamptz` for instants** — Postgres converts to UTC and back using the session
  timezone. `timestamp` without tz stores a wall-clock reading and is wrong the moment you
  have two regions.
- **`date` for calendar days** — a "birthday" or "billing period start" is not an instant.
- **Store the user's timezone** if you render local times (a separate `text`/`interval`-style
  column), don't infer it.
- **DST**: a "daily at 2am" job is wrong twice a year. Model schedules as local time + zone,
  and expect 23- and 25-hour days.

### Other traps

| Trap | Problem | Fix |
|---|---|---|
| `varchar(255)` everywhere | arbitrary; encourages oversized indexes | size to reality |
| Storing derived values with no owner | drift | compute, or document the owner + reconciliation |
| `int` PKs on high-volume tables | 2.1B ceiling | `bigint` from day one |
| Comma-separated tags | no index, no FK, no query | join table (or `text[]` with a GIN index) |
| `status int` magic numbers | unreadable, unvalidated | lookup table or `CHECK` |
| Case-sensitive emails | duplicates (`Ada@x.io` vs `ada@x.io`) | `citext` or a unique index on `lower(email)` |

---

## 10. Schema Evolution & Migrations

### The compatibility rule

Deploy code and schema **independently**. That means every migration must be compatible with
the *previous* and *next* version of the code:

```
expand  →  migrate data  →  contract
(add nullable col)   (backfill)     (add NOT NULL / drop old col)
```

Never in one deploy: drop a column the running code still reads.

### Adding a `NOT NULL` column to a large table

```sql
-- step 1: nullable, no default (fast — metadata only in PG 11+)
ALTER TABLE orders ADD COLUMN channel text;

-- step 2: backfill in batches (don't lock the table with one UPDATE)
UPDATE orders SET channel = 'web' WHERE channel IS NULL AND id BETWEEN 1 AND 100000;

-- step 3: after the app writes it, add the constraint without a long lock
ALTER TABLE orders ADD CONSTRAINT orders_channel_nn CHECK (channel IS NOT NULL) NOT VALID;
ALTER TABLE orders VALIDATE CONSTRAINT orders_channel_nn;   -- brief SHARE UPDATE EXCLUSIVE
```

`NOT VALID` + `VALIDATE` is the key trick: it avoids a full-table lock while still enforcing
the constraint for new rows immediately.

### Flyway conventions

```
V1__init.sql
V2__add_orders_channel.sql
V3__backfill_orders_channel.sql
R__order_summary_view.sql        -- repeatable: view/function definitions
```

- One change per migration, named for intent.
- **Never edit an applied migration** — checksums will fail and you'll be tempted to `repair`,
  which hides the problem.
- Migrations must be idempotent-safe (`CREATE INDEX CONCURRENTLY IF NOT EXISTS`) and must not
  depend on data that a later migration creates.
- `CREATE INDEX CONCURRENTLY` cannot run inside a transaction — Flyway needs
  `flyway.mixed=true` or a separate script.

---

## 11. Code Examples

### 11.1 A well-formed e-commerce core

```sql
CREATE TABLE customers (
  id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id  uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE,
  email      citext NOT NULL,
  name       text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT customers_email_unique UNIQUE (email)
);

CREATE TABLE products (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  sku         text NOT NULL UNIQUE,
  name        text NOT NULL,
  price_cents bigint NOT NULL CHECK (price_cents >= 0),
  currency    char(3) NOT NULL,
  active      boolean NOT NULL DEFAULT true
);

CREATE TABLE orders (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  public_id     uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE,
  customer_id   bigint NOT NULL REFERENCES customers(id),
  status        text NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING','PAID','SHIPPED','CANCELLED')),
  total_cents   bigint NOT NULL CHECK (total_cents >= 0),
  currency      char(3) NOT NULL,
  placed_at     timestamptz NOT NULL DEFAULT now(),
  version       bigint NOT NULL DEFAULT 0
);
CREATE INDEX orders_customer_placed_idx ON orders (customer_id, placed_at DESC);

CREATE TABLE order_items (
  order_id         bigint NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  line_no          int NOT NULL,
  product_id       bigint NOT NULL REFERENCES products(id),
  quantity         int NOT NULL CHECK (quantity > 0),
  unit_price_cents bigint NOT NULL CHECK (unit_price_cents >= 0),  -- snapshot at purchase
  PRIMARY KEY (order_id, line_no)
);
```

Note what the schema encodes:
- `unit_price_cents` is a **snapshot** — product prices change, orders don't.
- `total_cents` is denormalised but owned by the same transaction that inserts items.
- `public_id` keeps internal ids private.
- Status is a checked `text`, readable in queries and validated in the DB.

### 11.2 JPA mapping that matches

```java
@Entity
@Table(name = "orders")
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Version private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();
}
```

`@Enumerated(EnumType.STRING)` (never `ORDINAL` — reordering the enum silently corrupts data).
`@Version` for optimistic locking. `LAZY` by default on associations.

### 11.3 Postgres constraint enforcing a business rule

```sql
-- refunds can never exceed the order total
CREATE OR REPLACE FUNCTION check_refund_total() RETURNS trigger AS $$
BEGIN
  IF (SELECT COALESCE(SUM(amount_cents),0) FROM refunds WHERE order_id = NEW.order_id)
     > (SELECT total_cents FROM orders WHERE id = NEW.order_id) THEN
    RAISE EXCEPTION 'refund exceeds order total for order %', NEW.order_id;
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER refunds_total_guard
BEFORE INSERT ON refunds
FOR EACH ROW EXECUTE FUNCTION check_refund_total();
```

Caveat: triggers under concurrency need a lock or a serialisable isolation level to be truly
safe. State that — it shows you know a trigger isn't magic.

---

## 12. Common Mistakes & Gotchas

| Mistake | Consequence | Fix |
|---|---|---|
| No `FOREIGN KEY`s | orphan rows, orphaned money | Add FKs; index the child column |
| FK without child index | `DELETE` on parent scans the child | `CREATE INDEX ON child(parent_id)` |
| `float` for money | rounding errors in ledgers | `numeric`/`bigint` cents |
| `timestamp` without tz | multi-region chaos, DST bugs | `timestamptz` |
| `varchar(255)` default | oversized indexes, no validation | size to actual max |
| Email not unique / case-insensitive | duplicate customers | `citext` or unique on `lower(email)` |
| Natural key as PK that changes | cascade chaos | Surrogate PK + unique natural key |
| Random UUID PK | index bloat, write amplification | UUIDv7 or surrogate + UUID column |
| Soft delete with plain unique index | can't re-create a deleted record | Partial unique index |
| JSON for everything | no constraints, no indexes, no queries | Columns for queried data, JSON for payloads |
| Status as `int` | unreadable, unvalidated | `text` + `CHECK`, or lookup table |
| No `updated_at` trigger | cache invalidation and sync broken | Trigger maintains it |
| Denormalised counter, no reconciliation | silent drift | Reconciliation query + job |
| `ON DELETE CASCADE` everywhere | one delete wipes half the DB | `RESTRICT` by default |
| Editing an applied migration | checksum mismatch, drift between envs | New migration |

---

## 13. Interview Questions & Model Answers

**Q1. How do you decide how far to normalise?**
> 3NF by default, because it prevents update, insert and delete anomalies with zero ongoing
> effort. I denormalise only when I have a query plan showing the join or aggregate is the
> bottleneck on a hot path, and then I choose the consistency owner — application transaction,
> trigger, or a refresh job — plus a reconciliation query that detects drift. I'd rather
> denormalise a snapshot (unit price at purchase) than a live copy (customer's current email).

**Q2. Surrogate or natural primary key?**
> Surrogate `bigint` for the PK — small, stable, good index locality — plus a unique constraint
> on the natural key so duplicates are impossible. For external identifiers I add a UUID
> `public_id` so APIs don't expose sequential ids and I can change the internal representation
> later.

**Q3. Why not UUID primary keys?**
> Random UUIDv4s scatter inserts across the index, causing page splits and write amplification,
> and they're 2x the size of a bigint which inflates every FK and index. If the requirement is
> non-enumerable ids, I use UUIDv7/ULID, which are time-ordered and insert-friendly, or keep a
> bigint PK with a UUID column.

**Q4. How do you model a many-to-many with attributes?**
> A join table that's a first-class entity — `enrollments(student_id, course_id, enrolled_at,
> grade)` with a composite PK and FKs. Named for the relationship, not the two tables.

**Q5. How do you store money?**
> `numeric(19,4)` or `bigint` minor units, never floating point. Always with a `currency` column
> — an amount without a currency is uninterpretable and can't be fixed later. Rounding rules are
> defined once (per line or per total) and applied in one place.

**Q6. How do you add a `NOT NULL` column to a 400M-row table?**
> Expand/contract. Add it nullable (metadata-only in modern Postgres), backfill in batches so I
> never hold a long lock, switch the app to write it, then add the constraint as `NOT VALID` and
> `VALIDATE` it — which takes a brief lock, not a full-table one. Adding `NOT NULL` with a
> default on an old Postgres version rewrites the table, which is the thing to avoid.

**Q7. Soft delete or hard delete?**
> Soft delete when I need undo, audit, or to preserve FK references — but then every query must
> filter, so I add a view or a partial index and make the unique constraints partial too. Hard
> delete when the data is genuinely disposable and GDPR erasure applies. I'd say the cost out
> loud: soft delete leaks if one query forgets the filter.

**Q8. How do you enforce a rule like "one active subscription per customer"?**
> A partial unique index (`UNIQUE (customer_id) WHERE ended_at IS NULL`) or a range exclusion
> constraint. Doing it in Java has a race: two concurrent requests both see none active and both
> insert. Constraints are the only correct place for invariants that must hold under concurrency.

---

## 14. Self-Check & Revision Card

- [ ] I can state 1NF/2NF/3NF as rules, not definitions, with an example each.
- [ ] I can name the three anomalies 3NF prevents.
- [ ] I can justify denormalisation with a query plan and a consistency owner.
- [ ] I can choose between surrogate, natural and UUID keys with trade-offs.
- [ ] I can pick correct types for money, time, enums and booleans.
- [ ] I can list the constraints I'd add and why.
- [ ] I can run an expand/contract migration on a big table.
- [ ] I know the soft-delete and multi-tenancy trade-offs.

```
┌─────────────────── SCHEMA DESIGN — 60s RECALL ───────────────────────────────┐
│ 3NF default: non-key cols depend on THE KEY, THE WHOLE KEY, NOTHING BUT.     │
│ Anomalies prevented: update · insert · delete.                               │
│ Denormalise only with a plan + named consistency owner + reconciliation query.│
│                                                                              │
│ PK: bigint identity + UNIQUE natural key + uuid public_id.                   │
│ UUIDv4 PK = index bloat. Use v7/ULID if you must.                            │
│                                                                              │
│ MONEY numeric(19,4) or bigint cents + currency. NEVER float.                 │
│ TIME timestamptz (instant) vs date (calendar day). UTC everywhere.           │
│ ENUM: text + CHECK or lookup table, never ordinal ints.                      │
│                                                                              │
│ FKs ALWAYS, and INDEX THE CHILD COLUMN (Postgres won't).                     │
│ Soft delete ⇒ partial unique index + views + "don't forget the filter" risk. │
│                                                                              │
│ MIGRATE: expand → backfill in batches → contract. NOT VALID + VALIDATE.      │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Next:** [02 · Indexing Deep Dive](./02-Indexing-Deep-Dive.md)
