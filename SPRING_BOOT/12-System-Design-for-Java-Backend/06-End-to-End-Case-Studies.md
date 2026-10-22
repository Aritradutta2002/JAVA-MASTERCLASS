# 06 · End-to-End Case Studies

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★☆ (the "design X end to end" round)
> **Prerequisite:** [05 · Capacity Estimation](./05-Capacity-Estimation-and-Back-of-Envelope.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. What These Case Studies Prove](#0-what-these-case-studies-prove)
- [1. E-Commerce Checkout](#1-e-commerce-checkout)
- [2. Payment System](#2-payment-system)
- [3. Ticket Booking (High Contention)](#3-ticket-booking-high-contention)
- [4. Metrics & Monitoring Platform](#4-metrics--monitoring-platform)
- [5. The Cross-Cutting Checklist](#5-the-cross-cutting-checklist)
- [6. What Interviewers Actually Score](#6-what-interviewers-actually-score)
- [7. Revision Card](#7-revision-card)

---

## 0. What These Case Studies Prove

The classic problems in [ch 03](./03-Classic-Design-Problems.md) test whether you can structure a
design. These four test whether you can **integrate everything** — data ownership, sagas,
idempotency, contention, backpressure, and failure recovery — in one coherent story.

Each one is chosen because it has a **different dominant difficulty**:

| Case study | Dominant difficulty | The topic it stresses |
|---|---|---|
| E-commerce checkout | distributed transaction | sagas, outbox, compensation |
| Payments | correctness under retry | idempotency, exactly-once illusion, ledger |
| Ticket booking | contention | locking, isolation, overselling |
| Metrics platform | write volume + cardinality | ingestion, aggregation, downsampling |

**If you can explain the dominant difficulty of each in two sentences, you understand the
system.** Everything else is the standard skeleton.

---

## 1. E-Commerce Checkout

### 1.1 Requirements

**Functional**
- Add to cart → checkout → reserve stock → take payment → create order → notify
- Apply discounts/coupons; compute tax
- Cancel before shipping
- Order status is visible and truthful at all times

**Non-functional**
- 20k checkouts/s peak (Black Friday), 2k average
- Checkout p99 < 2 s perceived; the user must never be double-charged
- No overselling (**hard** — you cannot sell stock you don't have)
- Availability 99.99% — checkout is revenue
- Money must reconcile exactly, at month end, to the cent

### 1.2 The dominant difficulty

> **Checkout is a distributed transaction across four ownership boundaries** — cart, inventory,
> payment, order — and there is no XA. So it's a **saga with compensations**, and the pivot is
> **payment capture**: before it, everything is compensable; after it, everything must be
> retried to completion.

### 1.3 Estimates

```
20k checkouts/s peak × 3 items ≈ 60k inventory ops/s   ← the real load
20k payments/s                                        ← provider will rate-limit you
order rows: 20k/s × 300 B = 6 MB/s → 500 GB/day → 180 TB/year
```

**Implications:**
- 60k inventory ops/s means inventory **cannot** be a naive `UPDATE ... WHERE stock > 0` on one
  row per item at peak — you need per-warehouse rows, partitioning by SKU, and reserved stock as
  a separate table.
- 20k payments/s will exceed any provider's limit → **queue payments** and confirm
  asynchronously, showing "processing".
- 180 TB/year of orders → partition by month, archive after 2 years.

### 1.4 Data model

```sql
-- inventory: per warehouse, with a reservation table (never mutate stock directly)
inventory (sku, warehouse_id, on_hand, reserved, version,
           PRIMARY KEY (sku, warehouse_id))
reservations (id, order_id, sku, warehouse_id, qty, status, expires_at)

-- orders
orders (id, public_id, customer_id, status, total_cents, currency, placed_at, version)
order_items (order_id, line_no, sku, qty, unit_price_cents)

-- payments: append-only, never update an amount
payments (id, order_id, provider_ref, amount_cents, currency, status, created_at)
refunds  (id, payment_id, amount_cents, reason, created_at)

-- outbox for every state change
outbox (id, aggregate_type, aggregate_id, event_type, payload, published_at)
```

**The three schema decisions that matter:**

1. **`on_hand` and `reserved` are separate columns.** Available = `on_hand - reserved`. Reserving
   is `UPDATE ... SET reserved = reserved + ? WHERE sku = ? AND on_hand - reserved >= ?` — one
   atomic statement, and the `WHERE` is the oversell guard.
2. **Payments are append-only.** A refund is a new row, never an edit. That's what makes
   reconciliation possible.
3. **Reservations have `expires_at`.** A saga that dies leaves stock reserved forever unless
   something releases it — a scheduled job expires reservations after 15 minutes.

### 1.5 The saga

```
                    PIVOT
                      │
  validate ─► reserve stock ─► charge ─► create order ─► notify
      │            │             │            │
   (no side     (compensate:  (compensate:  (retry to
    effect)      release)      refund)       completion)
```

| Step | Action | Compensation |
|---|---|---|
| 1. Validate cart, price, tax | read-only | none |
| 2. Reserve inventory | `reserved += qty` with the guard | `reserved -= qty` |
| 3. Charge payment | idempotent charge keyed by `order_id` | **refund** |
| 4. Create order | insert | cancel |
| 5. Notify | async | none (email is irreversible; it's after the pivot) |

**Ordering rule:** irreversible actions (charge, email, ship) come **after** the pivot, and
everything before it is compensable. Note that the charge *is* the pivot, so its compensation is
a refund — a new business fact, not a rollback.

### 1.6 The oversell problem, precisely

```sql
-- ❌ two concurrent requests both see 1 available and both decrement
SELECT on_hand - reserved FROM inventory WHERE sku = 'X';   -- 1
UPDATE inventory SET reserved = reserved + 1 WHERE sku = 'X';  -- both succeed
-- reserved = 2 with on_hand = 1

-- ✅ the guard is in the WHERE, and the row count is the answer
UPDATE inventory
SET reserved = reserved + :qty, version = version + 1
WHERE sku = :sku AND on_hand - reserved >= :qty;
-- 0 rows ⇒ insufficient stock ⇒ fail the saga, compensate nothing
```

**Why not `SELECT ... FOR UPDATE`?** You can, and it's correct, but at 60k ops/s the lock
contention on hot SKUs (a Black Friday doorbuster) becomes the bottleneck. The atomic update
holds the lock for microseconds instead of for the transaction's duration.

**For genuinely hot SKUs** (one item, thousands of buyers per second), even that serialises.
Options:
- **Queue per SKU**: a single-threaded consumer per hot SKU processes reservations in order. Simple, bounded, and the throughput ceiling is explicit.
- **Pre-split inventory into N buckets** (`sku-bucket-0..9`), each with its own row; a request picks a random bucket and retries others on failure. Trades exactness of allocation for throughput.
- **Virtual waiting room** at the edge — the real Black Friday answer: admit a bounded number of users into checkout at a time.

**Saying "a virtual waiting room" for a doorbuster is the senior answer** — it's how real
retailers survive, and it reframes the problem from "make the DB faster" to "control admission".

### 1.7 Payment idempotency

```java
@Transactional
public void handle(OrderPlaced e) {
    // 1. claim the event — same transaction as the effect
    if (!dedupe.claim("payment", e.eventId())) return;

    // 2. idempotent charge, keyed by order id at the provider
    var result = gateway.charge(new ChargeRequest(
        idempotencyKey: "order-" + e.orderId(),
        amount: e.totalCents(), currency: e.currency()));

    // 3. record + outbox, atomically
    payments.insert(result);
    outbox.append("Order", e.orderId(), result.success() ? "PaymentCaptured" : "PaymentFailed",
                  result);
}
```

Three layers of idempotency: the dedupe row, the provider's idempotency key, and the unique
constraint on `payments.order_id`. Any one alone would be sufficient most of the time; all three
is what you do with money.

### 1.8 Failure modes

| Failure | Behaviour | Recovery |
|---|---|---|
| Inventory service down | checkout fails fast (circuit breaker) — better than overselling | retry; user sees "try again" |
| Payment provider down | queue the payment, show "processing" | async retry with backoff; status endpoint |
| Payment succeeds, order insert fails | refund (compensation) or retry the insert | saga state persists; a reaper resumes |
| Notification down | no user impact | retry; it's after the pivot |
| Saga owner crashes mid-flight | saga stuck in a step | persisted state + scheduler resumes |
| Reservation never released | stock locked 15 min | `expires_at` + release job |

**The statement to make:** "Every intermediate state is truthful in the UI — `PENDING`,
`PAYMENT_PROCESSING`, `CANCELLING`. The user never sees `CONFIRMED` for something that might be
refunded."

### 1.9 Reading path & caching

- Product pages: CDN + Redis, 5-minute staleness is fine.
- **Stock on the product page is a lie** — it's approximate. The authoritative check happens at
  reservation. Never promise stock from a cached value.
- Order status: read-your-writes; the user's own order must show immediately after checkout,
  so read it from the primary (or from a saga state) for the first minute.

### 1.10 Trade-off summary

> "I chose a saga with an atomic inventory reservation rather than a distributed transaction,
> because XA blocks under load and most of these systems don't support it. The cost is a visible
> intermediate state and the need for compensations — including a refund, which is a new business
> fact, not a rollback. I put the pivot at payment capture, so anything after it must be retried
> to completion, and I never put email or shipping before it. For a doorbuster I'd add a virtual
> waiting room rather than try to make a single SKU row handle 10k concurrent decrements."

---

## 2. Payment System

### 2.1 Requirements

- Accept a charge, refund, and payout; report balances exactly
- Never double-charge, never lose a charge
- Reconcile with the provider daily, to the cent
- Audit every state change permanently
- Availability 99.99%; **correctness is non-negotiable**

### 2.2 The dominant difficulty

> **Money is the one domain where the "exactly-once doesn't exist" answer must be paired with
> "so we make every effect idempotent and every dollar traceable."** The system must be
> **auditable**: you can reconstruct, from the data alone, why every balance is what it is.

### 2.3 The ledger model

**Never store a mutable `balance` as the source of truth.** Use double-entry:

```sql
CREATE TABLE ledger_entries (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  transaction_id uuid        NOT NULL,
  account_id     bigint      NOT NULL,
  amount_cents   bigint      NOT NULL,     -- signed: debit negative, credit positive
  currency       char(3)     NOT NULL,
  created_at     timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ledger_tx_balanced CHECK (amount_cents <> 0)
);
CREATE INDEX ledger_account_time_idx ON ledger_entries (account_id, created_at);

-- every transaction's entries must sum to zero in each currency
CREATE TABLE ledger_transactions (
  id uuid PRIMARY KEY, kind text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
```

Balances are **derived**, cached, and periodically reconciled:

```sql
SELECT account_id, currency, SUM(amount_cents) AS balance
FROM ledger_entries WHERE account_id = ? GROUP BY account_id, currency;
```

**Why this matters:** an `UPDATE balance = balance - 100` loses the *why*. A ledger lets you
answer "why is this account 4,217 cents?" by listing the entries. It also makes double-entry
invariants checkable: every transaction nets to zero, so a bug can't create money — it can only
misplace it, which is detectable.

**The cached balance** is an optimisation with a reconciliation job:

```sql
-- find accounts where the cached balance disagrees with the ledger
SELECT a.account_id, a.balance, l.actual
FROM account_balances a
JOIN (SELECT account_id, SUM(amount_cents) actual FROM ledger_entries GROUP BY account_id) l
  USING (account_id)
WHERE a.balance <> l.actual
LIMIT 100;
```

If you can't write that query, you don't have a ledger.

### 2.4 Idempotency, three layers

| Layer | Mechanism | Protects against |
|---|---|---|
| **Client** | `Idempotency-Key` header, response cached 24 h | double-click, retried HTTP |
| **Service** | unique constraint on `(merchant_id, idempotency_key)` | concurrent duplicates |
| **Provider** | idempotency key passed to Stripe/Adyen | our retry after a timeout |

**The subtle one:** a timeout is not a failure. If the charge times out, the money may have
moved. The **only** correct behaviour is to retry with the same idempotency key (or query the
provider by key), never to issue a new charge.

```java
try {
    return gateway.charge(req, idempotencyKey);
} catch (TimeoutException e) {
    // the charge may have succeeded — DO NOT generate a new key
    return gateway.queryByKey(idempotencyKey)          // authoritative
        .orElseGet(() -> gateway.charge(req, idempotencyKey));   // same key: safe retry
}
```

**That code block is the answer to "how do you handle a payment timeout?"** Most candidates say
"retry", which is wrong without the key.

### 2.5 Reconciliation

Daily:
- Pull the provider's settlement report.
- Match by `provider_ref` and amount.
- Classify: **matched**, **in provider but not us** (missed webhook → recover), **in us but not
  provider** (a charge we think succeeded — investigate immediately), **amount mismatch**.
- Alert on any nonzero mismatch; a rising rate is a bug, not noise.

**Say this out loud:** "Reconciliation is not optional reporting — it's the mechanism that
detects a broken idempotency path, and it's how you find the charge that vanished."

### 2.6 Webhooks

- **Verify the signature** (HMAC with a shared secret + timestamp to prevent replay).
- **Be idempotent**: the same webhook will arrive multiple times. Dedupe by `event_id`.
- **Respond 200 fast**, then process asynchronously — providers retry aggressively on non-2xx
  and will eventually disable your endpoint.
- **Don't trust the webhook's amount**; fetch the authoritative object from the provider API.
- **Handle out-of-order webhooks** — `payment_succeeded` may arrive before `payment_created`.
  Use state machines with monotonically increasing states, and ignore transitions backwards.

### 2.7 Failure modes

| Failure | Behaviour | Recovery |
|---|---|---|
| Provider timeout | unknown state | query by idempotency key |
| Webhook lost | our state lags | reconciliation catches it |
| Duplicate webhook | dedupe rejects | — |
| Partial saga (charged, order failed) | refund compensation | saga reaper |
| Ledger invariant broken | a bug | alert; every transaction must net to zero |
| DB failover with async replication | may lose the last commit | sync replication or accept RPO=0 via provider as truth |

**The RPO answer for money:** "for payments I'd use synchronous replication, or treat the
provider as the source of truth and reconcile — the one thing I won't do is accept silent loss."

### 2.8 Trade-off summary

> "Append-only double-entry ledger with derived, reconciled balances. Idempotency at three
> layers, with the rule that a timeout is an unknown, not a failure. Daily reconciliation against
> the provider, because that's the only thing that catches a broken idempotency path. I'd accept
> slightly higher write latency for synchronous replication here — correctness beats throughput
> when the data is money."

---

## 3. Ticket Booking (High Contention)

### 3.1 Requirements

- Browse events and seats; hold seats; pay; confirm
- **Never sell the same seat twice** (hard requirement)
- Handle a 100k-user spike at on-sale time for a 20k-seat venue
- Holds expire (a user who doesn't pay releases the seat)
- Reading seat maps must be fast even during the spike

### 3.2 The dominant difficulty

> **Contention, not throughput.** The system is small — thousands of seats — but thousands of
> users want the *same* rows at the *same* second. Every correctness mechanism (locks, atomic
> updates, isolation) becomes a serialisation point, so the design is about **controlling
> admission and reducing the critical section**, not about scaling the database.

### 3.3 The seat-hold model

```sql
CREATE TABLE seats (
  event_id   bigint NOT NULL,
  seat_id    text   NOT NULL,
  status     text   NOT NULL DEFAULT 'AVAILABLE',   -- AVAILABLE | HELD | SOLD
  held_by    uuid,
  held_until timestamptz,
  version    bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (event_id, seat_id)
);

-- atomic hold: the guard is the state transition
UPDATE seats
SET status = 'HELD', held_by = :holdId, held_until = now() + interval '10 minutes',
    version = version + 1
WHERE event_id = :event AND seat_id = ANY(:seats)
  AND (status = 'AVAILABLE' OR (status = 'HELD' AND held_until < now()));
-- rows updated must equal seats requested, else release and fail
```

**The three key properties:**
1. The transition is **conditional** — `AVAILABLE`, or an expired hold. The row count is the
   answer.
2. **No read-then-write.** The check and the write are one statement.
3. Expired holds are reclaimable without a separate cleanup pass (the `WHERE` allows it), though
   you also want a background job to mark them `AVAILABLE` for accurate seat-map reads.

### 3.4 Reducing contention

| Technique | Effect |
|---|---|
| **Virtual waiting room** | admit N users at a time; the queue is the load shedder |
| **Seat map from cache** | reads never touch the DB; ~1 s staleness accepted |
| **Hold in Redis with a TTL** | `SET seat:{event}:{seat} holdId NX PX 600000` — atomic, no DB |
| **Batch row locking** | lock a whole row of seats in one statement instead of 20 |
| **Best-available allocation** | let the system pick seats instead of the user, reducing conflicts |
| **Per-event sharding** | each event is independent; shard by `event_id` |
| **Queue per event** | single-threaded consumer per event → fully serialised, zero conflicts |

**The Redis hold is the strongest answer** for the hold phase: `SET NX PX` is atomic, costs one
round trip, and removes the DB from the hot path entirely. Then the payment path converts the
Redis hold into a durable `SOLD` row with a conditional update, and if that fails, the hold is
released.

**But say the trade-off:** Redis holds can be lost on a crash (a seat is freed but the user thinks
they have it), so the durable confirmation must be the authority and the client must handle "your
hold expired".

### 3.5 The on-sale spike

```
100k users arrive in 10 seconds for a 20k-seat venue
```

No database will handle 10k concurrent attempts on 20k rows. The answers, in order:

1. **Virtual waiting room** — the real solution. Issue signed queue tokens, admit at a rate the
   system can serve (say 2k/s), show position. This converts a thundering herd into a controlled
   stream.
2. **Cache the seat map aggressively** — the browsing phase is 90% of traffic and never needs
   strong consistency.
3. **Hold in Redis** — the critical section is a single `SET NX`.
4. **Idempotent payment** — the user might submit twice when the UI lags.

### 3.6 Payment and confirmation

```
hold (Redis, 10 min TTL) ──► pay ──► conditional UPDATE seats SET status='SOLD'
                                          WHERE status='HELD' AND held_by=:holdId
                                    │ 0 rows ⇒ the hold expired ⇒ refund
                                    └─ 1 row ⇒ confirm, publish OrderConfirmed
```

The conditional update is the **only** place a seat becomes sold. If the payment succeeded but
the hold expired, the compensation is a refund — and the user is told honestly.

### 3.7 Failure modes

| Failure | Behaviour | Recovery |
|---|---|---|
| Mass hold, no payment | seats held 10 min | TTL expiry + reclaim job |
| Redis lost | holds gone | client sees "hold expired"; durable seats intact |
| Payment succeeds, seat taken | 0 rows on the conditional update | automatic refund |
| Spike overwhelms the queue | queue itself is the shedder | return a position, never a 500 |
| Duplicate submit | idempotency key | one charge |

### 3.8 Trade-off summary

> "The system is small and highly contended, so I optimised for a tiny critical section rather
> than throughput: conditional single-statement transitions, Redis holds with TTLs, and a virtual
> waiting room to control admission. Reads come from a cached seat map with ~1 s staleness, which
> is acceptable because the authoritative decision happens at hold time. The cost of Redis holds
> is that they're not durable, so the durable confirmation is the source of truth and the client
> must handle expiry."

---

## 4. Metrics & Monitoring Platform

### 4.1 Requirements

- Ingest metrics from 100k hosts, 100 metrics each, every 10 s → **1M samples/s**
- Query: "p99 latency for service X over the last hour", with 10 s resolution
- Retain raw data 2 weeks, 1-minute rollups 1 year, 1-hour rollups 5 years
- Dashboards p99 < 500 ms
- Must survive a scrape storm (all hosts retrying at once)

### 4.2 The dominant difficulty

> **Write volume plus cardinality.** 1M samples/s with unbounded label combinations (user ids,
> URLs as labels) will destroy any store; the design is about **cardinality control and
> hierarchical downsampling**, not about raw throughput.

### 4.3 Data model — and the cardinality trap

```
series identity = metric name + label set
  http_requests_total{service="api", instance="host-1", status="200", path="/orders"}
```

**Cardinality = the number of distinct series.** This is the number that kills the system:

```
100k hosts × 100 metrics = 10M series          ← already large
+ path="/orders/42" (unbounded) → 10M series per path × 100k paths = 1B series
```

**The rule to state:** *"Never put unbounded values in labels."* No user ids, no URLs, no
request ids, no timestamps. Where you need that granularity, use **exemplars** (a sample trace id
attached to a histogram bucket), not a new label.

**Guardrails:**
- A per-metric series limit, enforced at ingest (drop and increment a `series_dropped` metric).
- A cardinality dashboard with alerts.
- Relabeling at the collector to collapse high-cardinality values.

### 4.4 Architecture

```
 agents (100k hosts)                     ── scrape every 10s
        │
        ▼
 ┌──────────────────┐
 │ collectors       │  relabel, drop, sample, cardinality limits
 │ (stateless, HA)  │
 └────────┬─────────┘
          ▼
 ┌──────────────────┐   write    ┌──────────────────────────────────┐
 │ queue (Kafka)    │───────────►│ time-series DB                   │
 │ partition by     │            │ (Prometheus remote / Mimir /     │
 │ series hash      │            │  VictoriaMetrics / TimescaleDB)  │
 └──────────────────┘            └────────────┬─────────────────────┘
                                             │
                        ┌────────────────────┼─────────────────────┐
                        ▼                    ▼                     ▼
                   raw (2 weeks)      1m rollups (1 year)   1h rollups (5 years)
                                             │
                                             ▼
                                        query API ──► dashboards
```

**Why a TSDB and not Postgres:** column-oriented per series, delta-of-delta timestamps,
Gorilla-style compression (a sample can be ~2 bytes), and time-bucketed indexes. A relational
store at 1M inserts/s is not viable.

### 4.5 Downsampling

```
raw       10s resolution, 2 weeks     ← debugging incidents
1-minute  1 year                      ← capacity trends, SLOs
1-hour    5 years                     ← annual reporting
```

**Storage math:**

```
raw:        1M samples/s × 2 B × 86,400 × 14 ≈ 2.4 TB  (compressed)
1-min:      1M/6 = 167k/s × 2 B × 86,400 × 365 ≈ 10.5 TB
1-hour:     167k/60 = 2.8k/s × 2 B × 86,400 × 365 × 5 ≈ 880 GB
```

Without downsampling, 5 years of raw data would be ~310 TB. **Downsampling is a 100x cost
reduction** — that's the answer to "how do you afford retention".

**Percentiles don't average.** You cannot compute a true p99 from 1-minute p99s. Store
**histogram buckets** (or a sketch like t-digest/DDSketch) and merge them. Saying this is a strong
signal — it's the mistake every naive metrics system makes.

### 4.6 Query path

- **Query the coarsest resolution that satisfies the range** — a 30-day chart reads 1-hour
  rollups, not raw.
- **Cache query results** for common dashboard ranges (30–60 s TTL).
- **Bound the work** — a query for 100k series over a year must be rejected or downsampled
  server-side, not executed.
- **Fan-out with limits** — cap the number of series per query; return a truncated result with a
  warning rather than timing out.

### 4.7 Ingest resilience

- **Collectors are stateless** — scale horizontally, behind an LB with least-connections.
- **Kafka buffers scrape storms** — a burst fills the queue instead of dropping samples.
- **Backpressure at the collector** — reject with 429; the agent retries with jitter (all agents
  retrying in lockstep is the scrape storm).
- **Sampling** — for very high-volume metrics, sample 1 in N and scale; document it.

### 4.8 Failure modes

| Failure | Behaviour | Recovery |
|---|---|---|
| Collector down | agents retry, some samples lost | HA collectors, jittered retries |
| Kafka full | collectors reject 429 | agents buffer locally, then drop oldest |
| TSDB node down | that shard's queries fail | replication factor ≥ 2 |
| Cardinality explosion | memory blowup | limits at ingest + alerts |
| Query too expensive | dashboard timeout | resolution selection + caps |

### 4.9 Trade-off summary

> "Metrics is a write-heavy, high-cardinality problem, so the design is: stateless collectors
> with cardinality limits, Kafka to absorb storms, a compressed TSDB partitioned by series, and
> hierarchical downsampling that cuts storage 100x over 5 years. Percentiles are stored as
> histograms because they don't average. The single most important guardrail is refusing
> unbounded label values, because cardinality — not volume — is what kills these systems."

---

## 5. The Cross-Cutting Checklist

For **any** end-to-end design, verify these twelve. Each one is a scored question:

| # | Check | Where it appears |
|---|---|---|
| 1 | **Data ownership** — one service per fact, no shared DB | [T08 ch06](../08-Microservices-with-Spring-Boot/06-Distributed-Data-Management-and-Saga-Patterns.md) |
| 2 | **Atomic write + publish** — outbox, not dual-write | [T09 ch04](../09-Messaging-Systems/04-Reliable-Messaging-and-Event-Driven-Architecture.md) |
| 3 | **Idempotency** — every mutating path | [T09 ch04](../09-Messaging-Systems/04-Reliable-Messaging-and-Event-Driven-Architecture.md) |
| 4 | **Compensations** — every pre-pivot step | [T08 ch06](../08-Microservices-with-Spring-Boot/06-Distributed-Data-Management-and-Saga-Patterns.md) |
| 5 | **Caching with a staleness budget** | [T10 ch03](../10-Caching/03-Cache-Consistency-Invalidation-and-Stampedes.md) |
| 6 | **Timeouts everywhere** | [T08 ch04](../08-Microservices-with-Spring-Boot/04-Circuit-Breaker-and-Resilience.md) |
| 7 | **Circuit breakers, bulkheads, rate limits** | [T08 ch04](../08-Microservices-with-Spring-Boot/04-Circuit-Breaker-and-Resilience.md) |
| 8 | **Bounded queues + backpressure** | [T12 ch02](./02-Scalability-Availability-and-Reliability.md) |
| 9 | **Tracing + metrics + structured logs** | [T08 ch05](../08-Microservices-with-Spring-Boot/05-Distributed-Tracing-and-Observability.md) |
| 10 | **Partitioning for retention** | [T11 ch05](../11-Database-Design-Optimization/05-Scaling-Partitioning-and-Replication.md) |
| 11 | **Degradation plan** — what breaks, what still works | [T12 ch02](./02-Scalability-Availability-and-Reliability.md) |
| 12 | **The trade-off you rejected, stated explicitly** | [T12 ch01](./01-System-Design-Framework.md) |

**If you cover these twelve, you have covered the interview.** Each case study in this chapter is
just a different combination of them, with one dominant difficulty.

---

## 6. What Interviewers Actually Score

| Signal | Strong | Weak |
|---|---|---|
| **Framing** | "checkout is a distributed transaction, so it's a saga" | draws boxes immediately |
| **Numbers** | "20k checkouts/s × 3 items = 60k inventory ops/s, so no single-row lock" | no arithmetic |
| **Ownership** | "inventory is the only owner of stock; the product page cache lies" | one shared DB |
| **Correctness** | atomic conditional update, row count checked | `if (stock > 0)` in Java |
| **Failure** | "if the provider times out, the charge may have succeeded — query by key" | "retry" |
| **Contention** | "a virtual waiting room controls admission" | "add a bigger DB" |
| **Cardinality** | "no unbounded label values" | stores user ids as labels |
| **Trade-offs** | names the rejected alternative | presents choices as obvious |
| **Honesty** | "exactly-once doesn't exist; here's the idempotency design" | claims exactly-once |

**The single highest-value habit in these case studies:** identify the **dominant difficulty**
in the first two minutes and say it out loud. Everything after that is you explaining how you
handled it — which is exactly the story the interviewer wants to hear.

---

## 7. Revision Card

```
┌────────── END-TO-END CASE STUDIES — 60s RECALL ──────────────────────────────┐
│                                                                              │
│ CHECKOUT   dominant = DISTRIBUTED TRANSACTION                                │
│   saga; pivot = payment capture; compensate release/refund                   │
│   OVERSELL guard = UPDATE ... WHERE on_hand - reserved >= qty (row count!)   │
│   on_hand and reserved SEPARATE; reservations expire; payments append-only   │
│   doorbuster ⇒ VIRTUAL WAITING ROOM, not a faster row lock                   │
│                                                                              │
│ PAYMENTS   dominant = CORRECTNESS UNDER RETRY                                │
│   double-entry append-only ledger; balances derived + reconciled             │
│   idempotency ×3 (client key · unique constraint · provider key)             │
│   TIMEOUT ≠ FAILURE → query by idempotency key, never a new key              │
│   daily provider reconciliation is the bug detector                          │
│                                                                              │
│ BOOKING    dominant = CONTENTION                                            │
│   conditional single-statement transitions; Redis hold SET NX PX             │
│   durable confirmation = authority; expired hold ⇒ refund                    │
│   on-sale spike ⇒ waiting room + cached seat map                             │
│                                                                              │
│ METRICS    dominant = VOLUME + CARDINALITY                                   │
│   never unbounded labels; series limits at ingest                            │
│   Kafka to absorb storms; TSDB + hierarchical downsampling (100x saving)     │
│   percentiles DON'T average ⇒ store histograms/sketches                      │
│                                                                              │
│ ALWAYS: state the dominant difficulty in the first 2 minutes.                │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [05 · Capacity Estimation](./05-Capacity-Estimation-and-Back-of-Envelope.md) · **Up:** [README](./README.md)
