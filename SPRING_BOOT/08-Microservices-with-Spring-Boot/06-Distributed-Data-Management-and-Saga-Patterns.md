# 06 · Distributed Data Management & Saga Patterns

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★★ ("How do you keep data consistent across services without 2PC?")
> **Prerequisite:** [05 · Distributed Tracing & Observability](./05-Distributed-Tracing-and-Observability.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — The Consistency Spectrum](#2-mental-model--the-consistency-spectrum)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 Why ACID doesn't cross service boundaries](#31-why-acid-doesnt-cross-service-boundaries)
  - [3.2 The dual-write problem](#32-the-dual-write-problem)
  - [3.3 CAP, PACELC and what you're actually choosing](#33-cap-pacelc-and-what-youre-actually-choosing)
  - [3.4 Sagas: choreography vs orchestration](#34-sagas-choreography-vs-orchestration)
  - [3.5 Compensating transactions](#35-compensating-transactions)
  - [3.6 Transactional outbox](#36-transactional-outbox)
  - [3.7 CDC and log tailing](#37-cdc-and-log-tailing)
  - [3.8 Idempotency and exactly-once delivery](#38-idempotency-and-exactly-once-delivery)
  - [3.9 Event sourcing and CQRS](#39-event-sourcing-and-cqrs)
  - [3.10 Data ownership, duplication and read models](#310-data-ownership-duplication-and-read-models)
- [4. Internal Working Mechanism](#4-internal-working-mechanism)
- [5. Step-by-Step Execution Flow — Order Saga End to End](#5-step-by-step-execution-flow--order-saga-end-to-end)
- [6. Architecture Diagrams](#6-architecture-diagrams)
- [7. Production Use Cases](#7-production-use-cases)
- [8. Code Examples — Production-Grade](#8-code-examples--production-grade)
- [9. Common Mistakes & Gotchas](#9-common-mistakes--gotchas)
- [10. Performance Considerations](#10-performance-considerations)
- [11. Security Considerations](#11-security-considerations)
- [12. Best Practices Checklist](#12-best-practices-checklist)
- [13. Hands-On Exercises](#13-hands-on-exercises)
- [14. Code Challenges](#14-code-challenges)
- [15. Interview Questions & Model Answers](#15-interview-questions--model-answers)
- [16. Self-Check](#16-self-check)
- [17. Revision Card](#17-revision-card)

---

## 0. TL;DR — The 30-Second Answer

> A microservice owns its data; **no distributed transaction manager coordinates writes across services.** ACID exists only *inside* one service's database. Across services you get **eventual consistency**.
>
> The pattern is: local transaction writes business data **and** an event to an **outbox table in the same DB transaction** — atomic. A relay (or CDC) publishes that event. The downstream SQL `UPDATE` that consumes the event is idempotent because a `processed_events` table had the event id inserted in the same transaction.
>
> Multi-step business rules use a **saga** — a sequence of local transactions, each paired with a **compensating action** if a later step fails. Sagas come in two shapes: **choreography** (services react to each other's events, no central point) and **orchestration** (a state machine drives the steps and knows the rollback path). Choreography for 2–3 steps, orchestration for 4+ or when you need an explicit audit trail.
>
> Saga != ACID transaction. There is **no isolation** — an intermediate state is observable. You handle that with semantic locks, "pending" statuses, and idempotent consumers — not by hoping nobody reads the half-done state.

---

## 1. What & Why — The Problem This Solves

A monolith places the order, takes the payment and reserves the stock in one database:

```java
@Transactional
public void placeOrder(Cart cart) {
    orderRepo.save(order);         // INSERT
    paymentService.charge(cart);   // UPDATE balance
    inventory.reserve(cart);       // UPDATE stock
}
```

One `commit`, one `rollback`. If stock reservation throws, the payment rows vanish. That guarantee is the **transactional atomicity** of a single ACID database, and nothing more.

Split that code into `order-service`, `payment-service`, `inventory-service`, each with its own DB (which they must, or you haven't actually decoupled anything), and the guarantee disappears. There is no coordinator that can atomically commit three databases. The naive port is:

```java
// BROKEN: three network calls pretending to be one transaction
orderRepo.save(order);
paymentClient.charge(order);     // succeeds
inventoryClient.reserve(order);  // THROWS — payment already taken
```

Now you have a customer charged for stock you don't have. The `@Transactional` annotation did nothing — it committed the order row, and the two remote calls were never part of it.

Candidate fixes and why each fails:

| Approach | Why it fails |
|---|---|
| **XA / two-phase commit (JTA)** | Blocking, coordinator is a SPOF, locks held across network round trips, and most modern stores (Cassandra, Kafka, DynamoDB, Mongo with replica sets in practice) don't support it. Latency and availability collapse. |
| **Try to retry the rollback** | A refund is not a rollback. The customer already saw a charge; the ledger has a debit with no offsetting entry. |
| **Shared database across services** | Works, but re-couples everything: schema migrations block all teams, and you lose independent deployability — the main reason you split services. |
| **"Just use a message broker"** | Doesn't solve it either — you've moved the dual write, you haven't removed it (see §3.2). |

**What you actually need:** a way to (a) make the *local* state change and the *event describing it* atomic, (b) make consumers tolerate duplicates and reordering, and (c) express a business process spanning services as a sequence of compensable steps. Those three are the whole subject of this chapter.

---

## 2. Mental Model — The Consistency Spectrum

Stop thinking "transaction or no transaction." Think of a **spectrum of guarantees**, and pick the weakest one your business can live with.

```
STRONG ──────────────────────────────────────────────────────► WEAK
  │              │                 │                │
  │              │                 │                │
Single-DB    2PC / XA         Saga + Outbox     Eventual /
ACID         (blocking)       (the default)      "best effort"
  │              │                 │                │
  │              │                 │                │
atomic        atomic but        atomic per        may never
rollback,     unavailable       step, global      converge;
isolation     when coordinator  consistency lags  manual fix
guaranteed    is down           by milliseconds    required
```

Three questions decide where you land:

1. **Can the business survive a window where state is inconsistent?** Almost always yes — a few hundred milliseconds of "order PLACED, stock not yet reserved" is fine if you *display* it as pending.
2. **Can it survive permanent inconsistency?** Almost never — so you need compensation, retries, and a dead-letter path. Not "best effort."
3. **Who is the source of truth for each fact?** One service, exactly one. Everything else is a replica of that fact and must be treated as stale.

The third question is the one candidates skip. In the saga model, `inventory-service` owns "how many units exist." `order-service` may cache that number for display, but it never *decides* it. Writing to another service's data (even via its API "just this once") is how distributed systems rot.

**One-line mental model:**

> *Every cross-service step is a local transaction plus a message. The message is the contract. If the message can be lost, duplicated, or reordered, your design must already handle it.*

---

## 3. Deep Dive — Theory

### 3.1 Why ACID doesn't cross service boundaries

ACID's atomicity is implemented by the database writing a **write-ahead log** and then, at commit, flipping a single durable marker. All rows are in one storage engine with one log. There is no such single log across three services on three hosts.

The closest analogue is **two-phase commit**:

1. **Prepare** — coordinator asks every participant "can you commit?" Each participant takes locks and writes its log, then votes yes/no.
2. **Commit/Abort** — if all voted yes, coordinator tells everyone to commit.

Failure modes that kill it at scale:

- **Coordinator crash between prepare and commit** → participants hold locks indefinitely (the *in-doubt* state) until a manual or timeout resolution. Locks mean blocked readers/writers on hot rows.
- **Blocking by design** — locks are held across two network round trips. P99 latency becomes the sum of the slowest participant.
- **Availability multiplication** — if each participant is 99.9% available, three of them in a blocking protocol give you ~99.7%, and worse under partition because a partitioned participant can't vote.
- **No support in modern stores** — Kafka, Cassandra, DynamoDB, Redis have no XA. Adding XA to a greenfield design usually means downgrading your datastore choices.

XA has legitimate niches (a legacy JMS + DB pair inside one team's boundary, low volume, strong audit needs). It is not a microservices strategy.

### 3.2 The dual-write problem

This is the bug that bites everyone who first "solves" the problem with a broker:

```java
@Transactional
public void placeOrder(Order order) {
    orderRepo.save(order);                       // (1) DB commit
    kafkaTemplate.send("orders", toEvent(order)); // (2) broker send — NOT in the tx
}
```

Both writes can't be atomic, so there are two failure windows:

- **(1) commits, then the JVM dies before (2)** → order exists, no event, inventory never reserved. Silent data loss.
- **(2) succeeds, then (1) rolls back** → the event is out, but there is no order. Downstream creates a phantom.

The tempting patch — send inside the transaction — makes it *worse*: the broker send isn't transactional with the DB, so a later rollback still leaves a published event. Ordering also becomes undefined because the send may reach the broker before the row is visible to anyone else.

Correct shapes:

| Shape | Idea | Cost |
|---|---|---|
| **Transactional outbox** | Write the event into a table in the *same* local transaction; a relay publishes later. | Extra table, relay process, at-least-once. |
| **CDC / log tailing** | Read the DB's commit log and emit changes downstream. | No app code, but couples you to the log format and requires ops maturity. |
| **Listen to yourself** | Some brokers support transactional producers; still not atomic with the DB. Insufficient alone. | — |

### 3.3 CAP, PACELC and what you're actually choosing

**CAP** — under a network **P**artition you must choose **C**onsistency (refuse the write) or **A**vailability (accept it and reconcile later). Since partitions are inevitable, the real question is what you do when one happens, not whether you "have CAP."

**PACELC** extends it to the normal case: **if Partition → A or C, Else → Latency or Consistency.** This is the more useful lens day to day: with no partition, choosing strong consistency costs latency (quorum round trips, cross-region replication).

Map that onto microservices data:

- **Inside one service**: pick a single-node ACID DB with synchronous commit. Consistency is cheap; you're not geo-distributed yet.
- **Across services**: choose availability + reconciliation. Money-moving ledgers may demand stricter treatment (see below), but the default is eventual.
- **Reads across services**: always stale. Bound the staleness (SLA: "search index ≤ 2s behind"), or don't serve the read from a replica at all.

For financial balances specifically, the usual production compromise is: **one service owns the balance, and all mutations for a given account are serialised through it** (single-writer per account, optimistic locking, or a partitioned queue). That's consistency *by ownership*, not by distributed transactions — and it's what interviewers want to hear.

### 3.4 Sagas: choreography vs orchestration

A **saga** is a sequence of local transactions `T1..Tn`, where each `Ti` has a compensating action `Ci` that semantically undoes it. If `Tk` fails, run `C(k-1) ... C1` in reverse order.

Two coordination styles:

**Choreography** — each service publishes an event; others subscribe and react. No coordinator.

```
order-service:  T1 create ORDER_PENDING  → publishes OrderCreated
payment-service: T2 charge               → publishes PaymentCharged | PaymentFailed
inventory:      T3 reserve               → publishes StockReserved | StockRejected
order-service:  T4 confirm               → publishes OrderConfirmed
```

- ✅ Simple, no new component, naturally decoupled.
- ❌ The end-to-end flow exists only in the wiring — nobody can read it in one place. Testing "what happens if step 3 fails" requires simulating the whole chain. Cyclic event dependencies are a real risk.

**Orchestration** — a dedicated component (a state machine, often with **Spring Statemachine**, **Camunda/Zeebe**, **Temporal**, or a hand-written process manager) calls each participant and decides the next step or the compensation path.

```
Orchestrator:  call payment → charge ok
               call inventory → rejected
               call payment.compensate(refund) → ok
               mark order CANCELLED
```

- ✅ Flow is explicit, testable, observable, easy to add timeouts and retries per step.
- ❌ Another service to run; risk of turning into a mini-ESB where all business logic lives.

**Decision rule:** choreography under ~4 steps with a simple linear happy path; orchestration when the flow has branches, humans in the loop, long waits (days), or auditors asking "show me the state of order 123."

**Pivot transaction:** the step after which the business outcome is decided (payment captured). Steps before it are compensable; steps after it must be *retryable to completion* (you can't un-charge a customer who already has the goods). Naming the pivot explicitly is a strong interview signal.

### 3.5 Compensating transactions

A compensation is **not** a rollback — it's a *new business fact* that offsets an earlier one.

| Forward action | Compensation | Notes |
|---|---|---|
| Charge card | Refund | Different ledger entry; customer may see both |
| Reserve stock | Release reservation | Must be idempotent — release twice = negative reserved |
| Create shipment | Cancel shipment | May be impossible once handed to carrier → whole design changes |
| Send email | **None** | Irreversible. Never place irreversible actions before the pivot. |

Three properties every compensation needs:

1. **Idempotent** — may run more than once (retries, duplicate events).
2. **Commutative where possible** — refund twice must not double-refund; use a unique `compensation_id`.
3. **Semantically visible** — the compensation itself emits an event (`OrderCancelled`, `PaymentRefunded`) so downstream projections update. Silent compensations create ghost records.

And the harsh truth: compensations can **fail**. A refund API can be down for an hour. So compensations need their own retry, backoff, dead-letter queue and alerting — a saga stuck mid-compensation is a business incident, not just a technical one.

### 3.6 Transactional outbox

The core trick. Write the event into a table in the same DB and the same transaction as the business data:

```sql
CREATE TABLE outbox (
  id             UUID PRIMARY KEY,
  aggregate_type VARCHAR(64)  NOT NULL,
  aggregate_id   VARCHAR(64)  NOT NULL,
  event_type     VARCHAR(128) NOT NULL,
  payload        JSONB        NOT NULL,
  headers        JSONB        NOT NULL DEFAULT '{}',
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  published_at   TIMESTAMPTZ,                     -- NULL = not yet published
  attempts       INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
```

Now the write is atomic: either both the order row and the outbox row exist, or neither. Publishing becomes a **separate, retryable** concern.

A relay polls unpublished rows and publishes them:

```sql
-- relay claims a batch atomically; SKIP LOCKED lets multiple relays run safely
SELECT * FROM outbox
WHERE published_at IS NULL
ORDER BY created_at
LIMIT 100
FOR UPDATE SKIP LOCKED;
```

Key design points (this is where most implementations go wrong):

- **Poll with `SKIP LOCKED`**, not `SELECT` — otherwise two relay instances publish the same row twice. (Duplicates are still possible on crash-after-publish; see §3.8.)
- **Ordering**: publishing in `created_at` order per aggregate is usually enough. Global ordering requires a single relay or partitioning by aggregate key on the broker — pick one deliberately.
- **Never delete rows synchronously.** Mark `published_at`, then archive with a scheduled job.
- **Table growth**: an outbox is a *log*, and logs grow. Partition by day, retain 3–7 days, or the table becomes the biggest thing in your DB.
- **One transaction, one event per aggregate is ideal.** Multiple events in one transaction can be published out of order by concurrent relays unless you key the partition.
- **Don't put huge payloads in the outbox.** Publish an id + minimal state; let consumers fetch the rest if they need it. Otherwise you're writing blobs twice and your DB grows for no reason.

### 3.7 CDC and log tailing

Instead of an app-managed table, read the database's own commit log with **Debezium** (Kafka Connect) — Postgres WAL, MySQL binlog, Mongo oplog — and publish row-level change events.

Trade-offs:

| | Outbox | CDC |
|---|---|---|
| App changes | Needs explicit outbox writes | None (or minimal) |
| Event semantics | Business events (`OrderPlaced`) | Row deltas (`orders` row, before/after) |
| Coupling | DB table you own | DB internals + connector version |
| Ordering | Whatever you implement | Commit-log order, per-partition |
| Best for | Domain events, clean contracts | Legacy DBs, cache invalidation, search sync |

CDC gives you *data* changes, not *business* events. Downstream services shouldn't be decoding your `orders.status` column — that's schema coupling. Use CDC for infrastructure sync (search index, cache, analytics replica) and the outbox for domain events.

### 3.8 Idempotency and exactly-once delivery

**There is no exactly-once delivery over an unreliable network.** What exists is **at-least-once delivery + idempotent consumers = effectively once.** Any claim otherwise in an interview is a red flag.

Three layers:

1. **Producer side** — assign a stable `eventId` (UUID) at creation, in the outbox row. Retries reuse the *same* id.
2. **Consumer side** — a dedupe table written in the same transaction as the effect:

```sql
CREATE TABLE processed_events (
  consumer    VARCHAR(64) NOT NULL,
  event_id    UUID        NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (consumer, event_id)
);
```

```java
@Transactional
public void on(StockReserveRequested evt) {
    int inserted = jdbc.update("""
        INSERT INTO processed_events (consumer, event_id) VALUES (?, ?)
        ON CONFLICT DO NOTHING
        """, "inventory", evt.eventId());
    if (inserted == 0) return;   // already done — ack and move on
    // ... apply the effect in the same transaction
}
```

Because the dedupe insert and the effect share a transaction, a crash before commit replays cleanly, and a duplicate after commit is rejected.

3. **HTTP side** — an `Idempotency-Key` header on `POST` endpoints, with the key + stored response cached (24h). Retrying a payment call returns the original result rather than charging twice.

Natural idempotency is better than a dedupe table when available: `UPDATE accounts SET balance = 42` is idempotent, `UPDATE accounts SET balance = balance - 10` is not. State-based assignments beat deltas.

### 3.9 Event sourcing and CQRS

**Event sourcing** stores the sequence of events as the source of truth and derives state by replay. Instead of `UPDATE orders SET status='SHIPPED'`, you append `OrderShipped`.

- ✅ Perfect audit trail, time travel (rebuild state at any instant), natural fit for outbox/CDC, easy to publish every change.
- ❌ Event schema evolution is hard (old events must replay forever), queries need projections, snapshots required for performance, and the team must unlearn CRUD.

**CQRS** separates the write model from one or more read models optimised per query. Frequently paired with event sourcing but independent of it: you can have CQRS with a plain relational write side plus an Elasticsearch read side.

The combination is powerful and expensive. **Don't event-source your first microservice.** Use it where the audit trail *is* the product (ledgers, compliance, collaborative editing), and use outbox+projections elsewhere.

### 3.10 Data ownership, duplication and read models

Because you cannot join across services, you duplicate data — deliberately, with rules:

- **One owner per fact.** `customer-service` owns the email; others store a copy with a `synced_at`.
- **Duplication is a cache, not a second truth.** Never write to the copy; never trust it for decisions that must be exact (e.g. credit limits) — call the owner.
- **Join by id, resolve by API** for low-volume exact reads; **replicate** for high-volume lists (an order list page cannot make 50 HTTP calls).
- **Contract of the copy:** document the staleness SLA and what happens if it's breached.
- **Deletion is a fact too.** `CustomerDeleted` must propagate, or you keep serving deleted customers forever. GDPR deletes need an explicit erasure fan-out.

---

## 4. Internal Working Mechanism

### 4.1 Outbox relay, mechanically

1. Business transaction inserts `orders` row + `outbox` row. Commit → both visible.
2. Relay thread wakes (poll every 100–500ms, or on notify) and runs `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 100`.
3. For each row: `producer.send(topic, key=aggregateId, value=payload, headers={eventId})`, wait for ack (`acks=all`).
4. On ack, `UPDATE outbox SET published_at = now()` for that batch (one statement), commit.
5. On send failure: increment `attempts`, leave `published_at` NULL, back off. After N attempts → alert; do **not** drop silently.

Crash between step 3 and 4 → the event is published but the row stays unpublished → republished on restart → **duplicate**. This is expected; consumers dedupe.

### 4.2 Saga orchestrator, mechanically

State machine with a persisted current step:

```
CREATED → PAYMENT_PENDING → PAYMENT_DONE → STOCK_PENDING → STOCK_DONE → CONFIRMED
                  ↓ fail                        ↓ fail
             CANCELLED                     COMPENSATING_PAYMENT → CANCELLED
```

- Each transition is persisted (a `saga_instance` row with `step`, `payload`, `attempts`, `next_retry_at`) **before** the outbound call — never after. Otherwise a crash loses the fact that you called.
- Timeouts on every step; a step that never answers must not stall the saga forever.
- A scheduled reaper scans `next_retry_at <= now()` and resumes. This is why orchestrators survive restarts and why "in-memory saga" designs fail.
- Compensations are themselves steps with retries and their own DLQ.

### 4.3 Consumer, mechanically

1. Broker delivers `(topic, partition, offset, event)`.
2. Handler begins a DB transaction: insert into `processed_events` `ON CONFLICT DO NOTHING`.
3. If inserted → apply effect → commit → then commit the offset (or let Spring Kafka's `ack-mode` do it after the listener returns).
4. If not inserted → commit offset, return.
5. If the handler throws → do **not** commit the offset; retry per container policy (`DefaultErrorHandler` with `FixedBackOff`/`ExponentialBackOff`), then route to DLT (`DeadLetterPublishingRecoverer`) after max attempts.

**Ordering vs dedupe hazard:** dedupe requires the effect and the marker in one transaction — so the offset commit must happen *after* the DB commit, not before. `ack-mode=BATCH` (default) commits offsets after the poll batch completes, which is generally safe; `RECORD` + manual ack is more explicit. If you ack before the DB commit, you lose events.

---

## 5. Step-by-Step Execution Flow — Order Saga End to End

Scenario: place an order → charge → reserve stock → confirm. Stock is insufficient at step 3.

```
Client        order-svc      outbox      broker     payment-svc    inventory-svc
  │               │             │           │            │              │
  │ POST /orders  │             │           │            │              │
  ├──────────────►│             │           │            │              │
  │               │ BEGIN TX    │           │            │              │
  │               │ INSERT order(PENDING)   │            │              │
  │               │ INSERT outbox(OrderCreated)           │              │
  │               │ COMMIT ────►│           │            │              │
  │◄──202 + id────┤             │           │            │              │
  │               │             │ poll      │            │              │
  │               │             ├──publish─►│            │              │
  │               │             │ ◄──ack────┤            │              │
  │               │             │ mark sent │            │              │
  │               │             │           ├─deliver───►│              │
  │               │             │           │            │ dedupe check │
  │               │             │           │            │ charge (local tx)
  │               │             │           │            │ + outbox(PaymentCharged)
  │               │             │           │◄──publish──┤              │
  │               │             │           ├──────────────────────────►│
  │               │             │           │            │              │ dedupe
  │               │             │           │            │              │ reserve → FAIL
  │               │             │           │            │              │ + outbox(StockRejected)
  │               │◄────────────┴───────────┴───────────────────────────┤
  │               │ StockRejected → start compensation                 │
  │               │ orchestrator: REFUND_PENDING                        │
  │               ├─────────────────────────►│ payment-svc refund       │
  │               │◄── Refunded ─────────────┤ (idempotent, keyed)      │
  │               │ update order → CANCELLED, publish OrderCancelled    │
  │◄── GET /orders/{id} → CANCELLED, refunded=true                     │
```

Note the observable intermediate state: between the charge and the refund the customer's card is debited and the order says `PENDING`. That window is the isolation gap; you make it survivable by (a) showing `PENDING`/`CANCELLING` truthfully in the UI, (b) refunding automatically, (c) alerting when a saga sits in `*_PENDING` beyond SLA.

---

## 6. Architecture Diagrams

```
┌──────────────────────────────── OUTBOX + RELAY ────────────────────────────────┐
│                                                                                │
│  ┌─────────────── order-service ───────────────┐                               │
│  │                                             │                               │
│  │  @Transactional                             │      ┌──────────────┐         │
│  │  ┌──────────┐   ┌────────────┐              │      │    Kafka     │         │
│  │  │ orders   │   │  outbox    │              │      │  orders.v1   │         │
│  │  └──────────┘   └─────┬──────┘              │      └──────┬───────┘         │
│  │        ▲              │                     │             ▲                 │
│  │        │        ┌─────▼──────┐              │             │                 │
│  │     TX commits  │  Relay     │──────────────┼─────────────┘                 │
│  │     both rows   │ (SKIP      │  at-least-once, keyed by aggregateId          │
│  │     or neither  │  LOCKED)   │              │                               │
│  │                 └────────────┘              │                               │
│  └─────────────────────────────────────────────┘                               │
└────────────────────────────────────────────────────────────────────────────────┘
                │                                    │
                ▼                                    ▼
       ┌─────────────────┐                  ┌──────────────────┐
       │ payment-service │                  │ inventory-service│
       │ dedupe table    │                  │ dedupe table     │
       │ local tx        │                  │ local tx         │
       └─────────────────┘                  └──────────────────┘


┌──────────────────── ORCHESTRATION SAGA ────────────────────────┐
│                                                                │
│  ┌──────────────────────────────────────────────────┐          │
│  │  OrderSagaOrchestrator                           │          │
│  │  saga_instance(order_id, step, attempts, retry_at)│          │
│  │                                                  │          │
│  │  PAYMENT ──fail──► COMPENSATE_NONE ──► CANCELLED │          │
│  │     │ok                                          │          │
│  │  STOCK ────fail──► COMPENSATE_REFUND ─► CANCELLED│          │
│  │     │ok                                          │          │
│  │  CONFIRM ─────────► CONFIRMED (pivot passed)     │          │
│  └──────────────────────────────────────────────────┘          │
│        │              │               │                        │
│        ▼              ▼               ▼                        │
│   payment-svc   inventory-svc   shipping-svc                   │
└────────────────────────────────────────────────────────────────┘
```

---

## 7. Production Use Cases

- **E-commerce checkout** — order / payment / inventory / shipping saga. The canonical example; almost every large retailer runs choreography for the happy path and an orchestrator for refunds and partial cancellations.
- **Fintech ledger** — double-entry ledger service is the single owner of balances; all money movements are idempotent commands keyed by a transfer id. Sagas here are *strictly* orchestrated with an audit trail, because ops must be able to answer "what happened to transfer 88?" exactly.
- **Travel booking** — flights + hotel + car rental. Long-running (minutes to hours), human intervention on failure, classic use case for **Temporal/Camunda** rather than hand-rolled state machines.
- **SaaS subscription lifecycle** — signup → trial → charge → provision → email. Compensations (deprovision, credit) must be idempotent because webhooks from Stripe arrive multiple times.
- **Search/analytics sync** — CDC from Postgres into Elasticsearch/Kafka. Not a saga, but the same "no dual write" discipline.
- **Inventory reservation with expiry** — reserve stock for 15 minutes; a scheduler releases expired reservations. A perfect example of a compensation driven by *time*, not failure.

---

## 8. Code Examples — Production-Grade

### 8.1 Outbox entity and writer

```java
@Entity
@Table(name = "outbox")
public class OutboxEvent {
    @Id private UUID id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    @JdbcTypeCode(SqlTypes.JSON) private String payload;
    private Instant createdAt;
    private Instant publishedAt;
    private int attempts;

    // getters/setters
}
```

```java
@Component
public class OutboxWriter {
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;

    public OutboxWriter(OutboxRepository outbox, ObjectMapper mapper) {
        this.outbox = outbox;
        this.mapper = mapper;
    }

    /** MUST be called inside the business @Transactional method. */
    public void append(String aggregateType, String aggregateId,
                       String eventType, Object event) {
        var e = new OutboxEvent();
        e.setId(UUID.randomUUID());
        e.setAggregateType(aggregateType);
        e.setAggregateId(aggregateId);
        e.setEventType(eventType);
        e.setPayload(writeJson(event));
        e.setCreatedAt(Instant.now());
        outbox.save(e);
    }

    private String writeJson(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("unserializable event", ex); }
    }
}
```

```java
@Service
public class OrderService {
    private final OrderRepository orders;
    private final OutboxWriter outbox;

    @Transactional   // <-- the whole point: order row + event row commit together
    public Order placeOrder(Cart cart) {
        var order = Order.pending(cart);
        orders.save(order);
        outbox.append("Order", order.getId().toString(), "OrderCreated",
                new OrderCreated(order.getId(), cart.items(), order.getTotal()));
        return order;
    }
}
```

### 8.2 Relay with `SKIP LOCKED` and batched acknowledgement

```java
@Component
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;

    @Scheduled(fixedDelayString = "${outbox.poll-ms:250}")
    public void drain() {
        List<Row> batch = tx.execute(s -> jdbc.query("""
                SELECT id, aggregate_id, event_type, payload
                FROM outbox
                WHERE published_at IS NULL
                ORDER BY created_at
                LIMIT 100
                FOR UPDATE SKIP LOCKED
                """, (rs, i) -> new Row(
                    rs.getObject("id", UUID.class),
                    rs.getString("aggregate_id"),
                    rs.getString("event_type"),
                    rs.getString("payload"))));
        if (batch == null || batch.isEmpty()) return;

        var sent = new ArrayList<UUID>(batch.size());
        for (Row r : batch) {
            try {
                kafka.send("domain.events", r.aggregateId(),
                        r.payload(),
                        // keying by aggregate id preserves per-aggregate order
                        headers(r.eventType(), r.id()))
                     .get(5, TimeUnit.SECONDS);   // wait for ack before marking
                sent.add(r.id());
            } catch (Exception ex) {
                log.error("publish failed for outbox {}", r.id(), ex);
                jdbc.update("UPDATE outbox SET attempts = attempts + 1 WHERE id = ?", r.id());
                break; // preserve ordering: stop this batch, retry later
            }
        }
        if (!sent.isEmpty()) {
            jdbc.batchUpdate("UPDATE outbox SET published_at = now() WHERE id = ?",
                    sent.stream().map(id -> new Object[]{id}).toList());
        }
    }

    private ProducerRecord<String, String> record(Row r) { /* ... */ return null; }

    private org.apache.kafka.common.header.internals.RecordHeaders headers(String type, UUID id) {
        var h = new org.apache.kafka.common.header.internals.RecordHeaders();
        h.add("eventId", id.toString().getBytes(StandardCharsets.UTF_8));
        h.add("eventType", type.getBytes(StandardCharsets.UTF_8));
        return h;
    }

    record Row(UUID id, String aggregateId, String eventType, String payload) {}
}
```

> Note: `SKIP LOCKED` inside a `TransactionTemplate` holds row locks while the Kafka ack is awaited. Keep batches small (100) and the poll interval short; or claim rows by setting a `claimed_at` column and committing before sending, which removes lock-while-sending entirely. The second variant is preferable at high throughput.

### 8.3 Idempotent consumer with retry and DLT

```java
@Component
public class StockReservationListener {
    private final JdbcTemplate jdbc;
    private final InventoryService inventory;

    @KafkaListener(topics = "domain.events", groupId = "inventory-svc")
    @Transactional
    public void on(ConsumerRecord<String, String> rec, Acknowledgment ack)
            throws JsonProcessingException {

        UUID eventId = UUID.fromString(
            new String(rec.headers().lastHeader("eventId").value(), StandardCharsets.UTF_8));

        int inserted = jdbc.update("""
            INSERT INTO processed_events (consumer, event_id) VALUES ('inventory', ?)
            ON CONFLICT DO NOTHING
            """, eventId);
        if (inserted == 0) { ack.acknowledge(); return; }

        var evt = new ObjectMapper().readValue(rec.value(), OrderCreated.class);
        inventory.reserve(evt.orderId(), evt.items());

        ack.acknowledge();   // transactional: marker + effect already committed with this tx
    }
}
```

```java
@Configuration
public class KafkaErrorHandling {
    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (rec, ex) -> new TopicPartition("domain.events.DLT", rec.partition()));
        var backoff = new ExponentialBackOffWithMaxRetries(5);
        backoff.setInitialInterval(500);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(10_000);
        var handler = new DefaultErrorHandler(recoverer, backoff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class); // poison pill
        return handler;
    }
}
```

### 8.4 Orchestrated saga with persisted state

```java
@Entity
@Table(name = "saga_instance")
public class OrderSaga {
    @Id private UUID orderId;
    @Enumerated(EnumType.STRING) private SagaStep step;
    @Enumerated(EnumType.STRING) private SagaStatus status;
    private int attempts;
    private Instant nextRetryAt;
    private Instant updatedAt;
    @JdbcTypeCode(SqlTypes.JSON) private String context;
    @Version private long version;   // optimistic locking: two workers can't drive the same saga
}

public enum SagaStep { START, PAYMENT, STOCK, CONFIRM, REFUND, RELEASE_STOCK, DONE }
public enum SagaStatus { RUNNING, COMPENSATING, COMPLETED, FAILED }
```

```java
@Service
public class OrderSagaOrchestrator {
    private final SagaRepository sagas;
    private final PaymentClient payments;
    private final InventoryClient inventory;

    @Transactional
    public void start(UUID orderId, OrderContext ctx) {
        var saga = new OrderSaga();
        saga.setOrderId(orderId);
        saga.setStep(SagaStep.PAYMENT);
        saga.setStatus(SagaStatus.RUNNING);
        saga.setContext(write(ctx));
        sagas.save(saga);         // persisted BEFORE any outbound call
    }

    /** Called by a scheduler, or immediately after start(). Resumable at any step. */
    @Transactional
    public void advance(UUID orderId) {
        var saga = sagas.findById(orderId).orElseThrow();
        if (saga.getStatus() == SagaStatus.COMPLETED || saga.getStatus() == SagaStatus.FAILED) return;

        switch (saga.getStep()) {
            case PAYMENT -> {
                if (payments.charge(orderId, ctx(saga))) {
                    saga.setStep(SagaStep.STOCK);
                } else {
                    saga.setStatus(SagaStatus.FAILED);   // nothing to compensate yet
                }
            }
            case STOCK -> {
                if (inventory.reserve(orderId, ctx(saga))) {
                    saga.setStep(SagaStep.CONFIRM);
                } else {
                    saga.setStep(SagaStep.REFUND);
                    saga.setStatus(SagaStatus.COMPENSATING);
                }
            }
            case REFUND -> {
                payments.refund(orderId);                 // idempotent by orderId
                saga.setStep(SagaStep.DONE);
            }
            case CONFIRM -> { /* publish OrderConfirmed */ saga.setStatus(SagaStatus.COMPLETED); }
            case DONE -> saga.setStatus(SagaStatus.FAILED); // compensated and closed
            default -> throw new IllegalStateException("unexpected " + saga.getStep());
        }
        saga.setUpdatedAt(Instant.now());
        // a failed step is retried by the scheduler using nextRetryAt + attempts
    }

    @Scheduled(fixedDelay = 5000)
    public void retryStuck() {
        sagas.findByStatusInAndNextRetryAtBefore(
                List.of(SagaStatus.RUNNING, SagaStatus.COMPENSATING), Instant.now())
             .forEach(s -> advance(s.getOrderId()));   // exceptions -> backoff + alert
    }
}
```

### 8.5 Idempotency key on the HTTP edge

```java
@Component
public class IdempotencyFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        if (!"POST".equals(req.getMethod())) { chain.doFilter(req, res); return; }
        String key = req.getHeader("Idempotency-Key");
        if (key == null) { chain.doFilter(req, res); return; }

        var existing = jdbc.query("SELECT response FROM idempotency WHERE key = ?",
                rs -> rs.next() ? rs.getString(1) : null, key);
        if (existing != null) {
            res.setStatus(200);
            res.setContentType("application/json");
            res.getWriter().write(existing);
            return;
        }
        // claim the key; unique constraint makes concurrent duplicates fail fast
        try {
            jdbc.update("INSERT INTO idempotency (key, created_at) VALUES (?, now())", key);
        } catch (DuplicateKeyException dup) {
            res.setStatus(409);
            res.getWriter().write("{\"error\":\"request in flight\"}");
            return;
        }
        var wrapper = new ContentCachingResponseWrapper(res);
        chain.doFilter(req, wrapper);
        jdbc.update("UPDATE idempotency SET response = ?, status = ? WHERE key = ?",
                new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8),
                res.getStatus(), key);
        wrapper.copyBodyToResponse();
    }
}
```

---

## 9. Common Mistakes & Gotchas

| Mistake | Why it breaks | Fix |
|---|---|---|
| Publishing to the broker inside `@Transactional` and calling it atomic | Broker isn't in the DB transaction | Outbox table |
| `@Transactional` on a method that calls remote services | Rollback cannot un-charge a card; the annotation only guards the DB | Compensations |
| Outbox relay using plain `SELECT` | Two relay instances publish the same row | `FOR UPDATE SKIP LOCKED` or a claim column |
| Consumer acking before the DB commit | Event lost on crash | Commit DB first, then offset |
| Dedupe with a Redis `SETNX` outside the DB transaction | Redis succeeds, DB rolls back → event skipped forever | Dedupe row in the same DB transaction |
| Unbounded outbox table | Table bloat, slow `VACUUM`, runaway storage | Partition by day, archive published rows after 7 days |
| Treating a compensation as a rollback | Refund ≠ reversal of the record; audit breaks | Explicit compensating event + ledger entry |
| Irreversible action before the pivot | Can't compensate a shipped parcel | Order steps so irreversible ones come last |
| Global ordering assumed | Kafka orders per partition only | Key by aggregate id; don't rely on global order |
| Saga state in memory | Restart mid-saga loses progress forever | Persist `saga_instance` before each call |
| Compensations without retries | One refund outage leaves money stuck | Retry + DLQ + alert on `COMPENSATING` age |
| Reading another service's DB | Schema coupling, breaks independent deploy | API or event-fed replica |
| `UPDATE balance = balance - 10` as "idempotent" | Duplicate event double-debits | Idempotency key / state assignment |
| Retrying non-transient errors | Poison pill loops forever | Classify errors; not-retryable → DLT |
| Missing timeouts on saga steps | Saga hangs indefinitely | Per-step timeout + reaper |

---

## 10. Performance Considerations

- **Outbox poll cost** — an index on `(created_at) WHERE published_at IS NULL` keeps the poll cheap; without it each poll is a sequential scan and the table becomes a hotspot.
- **Lock-while-sending** — holding DB locks during Kafka waits caps your throughput to `batchSize / ackLatency`. At >1k events/s, switch to a claim-then-commit relay.
- **Batch publishing** — `kafkaTemplate.send` per record is fine; batch with `linger.ms=5..20` and `batch.size=16KB` for throughput, keeping `acks=all` for durability.
- **Consumer dedupe table growth** — partition by month, TTL at 30 days (event ids from older retries won't return; Kafka retention bounds this).
- **Saga latency** — each step is a network hop; a 5-step saga has P99 ≈ sum of P99s. Parallelise independent steps (reserve stock and charge card can run concurrently if both are pre-pivot).
- **Retry storms** — exponential backoff *with jitter*. Without jitter, all failed consumers retry in lockstep and hammer the recovering service.
- **CDC lag** — a slow Debezium snapshot can add minutes of replication lag; monitor `MilliSecondsBehindSource`.
- **Exactly-once illusion cost** — Kafka transactions add latency and complexity; at-least-once + dedupe is almost always cheaper and simpler.

---

## 11. Security Considerations

- **Outbox payloads are data at rest** — they may contain PII. Encrypt sensitive fields, and honour erasure requests (GDPR) across the outbox and any event store; "immutable log" is not a legal excuse.
- **Event contracts are trust boundaries** — a consumer must validate schema and authorization; anyone who can produce to `domain.events` can claim an `OrderCreated`. Use ACLs on topics (Kafka ACLs / mTLS / SASL), one topic family per producer.
- **Idempotency keys must be scoped** to the caller — a global key namespace lets one tenant block another (or replay another's response).
- **Replay is an attack** — an attacker who captures a `PaymentCharged` event and replays it should be blocked by dedupe; that's a security property, not just a correctness one.
- **Saga state contains business context** — encrypt `context`, restrict DB access, and never log full payloads (they show up in tracing spans).
- **Compensation authorization** — refunds initiated by an orchestrator need service credentials with the narrowest scope (`payments:refund`), not a god-mode token.
- **Audit trail** — every saga transition should be append-only and tamper-evident; this is often a compliance requirement, not a nicety.

---

## 12. Best Practices Checklist

- [ ] Each service owns its schema; no cross-service SQL, ever.
- [ ] Every state change that others care about goes through an outbox, in the same transaction.
- [ ] Outbox relay uses `SKIP LOCKED` (or claim-and-commit) and publishes with `acks=all`.
- [ ] Every event has a stable `eventId` and an `eventType` header.
- [ ] Every consumer is idempotent, with the dedupe marker in the same DB transaction as the effect.
- [ ] Offsets commit after the DB commit; failures retry with exponential backoff + jitter, then DLT.
- [ ] Sagas persist their step *before* calling out; a scheduler can resume them.
- [ ] Every forward step has a defined compensation (or an explicit "irreversible, must be last" note).
- [ ] The pivot transaction is documented.
- [ ] Staleness SLA is written down for every replicated/cached fact.
- [ ] Alerting covers: outbox lag, DLT depth, sagas stuck in `COMPENSATING`, dedupe-table growth.
- [ ] Timeouts on every outbound call, at or below the saga step's SLA.
- [ ] HTTP `POST` endpoints accept `Idempotency-Key` where retries are plausible.

---

## 13. Hands-On Exercises

1. **Outbox from scratch** — implement order creation with an outbox table, a `@Scheduled` relay, and a Kafka consumer. Kill the app between the DB commit and the publish; verify the event still ships after restart.
2. **Duplicate injection** — configure the relay to publish twice (or replay a batch) and prove the consumer's state is unchanged.
3. **`SKIP LOCKED` proof** — run two relay instances and assert each outbox row is published once (count publishes per id).
4. **Saga by hand** — build the refund compensation with a failing `PaymentClient` for the first 3 attempts; confirm the scheduler retries and the order ends `CANCELLED`.
5. **Compensation failure drill** — make refunds fail permanently; confirm an alert fires and the saga is visible in a "stuck" dashboard, and that a manual retry endpoint completes it.
6. **Chaos** — kill `inventory-service` for 60 seconds under load; verify no lost events, no double reservations, and bounded recovery time.
7. **Ordering test** — publish two events for the same aggregate concurrently; prove per-aggregate order is preserved by keying on `aggregateId`.

---

## 14. Code Challenges

1. **Idempotent refund API** — accept an `Idempotency-Key`, persist the response, return the original body for repeats, and return `409` while a concurrent request is in flight.
2. **Outbox archiver** — a job that moves rows with `published_at < now() - 7 days` into `outbox_archive` and deletes them in bounded batches without blocking writers.
3. **Dead-letter replay tool** — an admin endpoint that re-publishes messages from the DLT (with dedupe) and reports how many were duplicates.
4. **Saga with timeout** — add a `deadline` to each step; when exceeded, treat as failure and compensate. Prove a hung step doesn't block other orders.
5. **Projection rebuild** — drop a read model table and rebuild it from the event log; measure the time and verify it matches the incremental state exactly.
6. **CDC vs outbox comparison** — wire Debezium on the same DB, and explain (in writing) why the outbox events are the better domain contract.

---

## 15. Interview Questions & Model Answers

**Q1. Why can't you use a distributed transaction across microservices?**
> XA/2PC requires a coordinator and blocking locks held across network round trips. The coordinator is a SPOF, in-doubt transactions block rows indefinitely, and availability multiplies down with each participant. Most modern stores don't implement it. So we accept eventual consistency and use outbox + idempotent consumers + sagas.

**Q2. How do you atomically update the DB and publish an event?**
> You don't — you make the event part of the DB write. Write the event into an `outbox` table inside the same transaction as the business change. A relay then publishes and marks `published_at`. If the app dies between commit and publish, the row survives and gets published on restart. Delivery is at-least-once, so consumers must dedupe.

**Q3. Choreography or orchestration?**
> Choreography for short linear flows — no new component, services stay decoupled, but the flow is implicit and hard to trace. Orchestration when the flow branches, involves long waits or humans, or needs an auditable state machine. Orchestration also gives you a single place for retries and timeouts, at the cost of another service and the risk of a god-service.

**Q4. What is a compensating transaction and how is it different from rollback?**
> Rollback is the DB discarding uncommitted work. A compensation is a *new* committed business fact that offsets a previous one — a refund, a stock release. It's visible in the ledger, it must be idempotent, and it may itself fail and need retries.

**Q5. How do you guarantee exactly-once processing?**
> You can't, over an unreliable network. You get at-least-once delivery plus idempotent consumers. Dedupe by `(consumer, eventId)` inserted in the same DB transaction as the effect, so the check and the work are atomic.

**Q6. What is the isolation problem in sagas?**
> A saga has no isolation — intermediate states are visible to other transactions. Someone can read an order that's placed but not paid. We handle it with semantic locks (statuses like `PENDING`), by ordering steps so risky reads are avoided, and by never letting a half-done aggregate be treated as valid by downstream logic.

**Q7. Where do you put the pivot transaction, and why does it matter?**
> The pivot is the point of no return — usually payment capture. Steps before it are compensable; steps after it must be retried until they succeed, because there's no way to undo them. Anything irreversible (shipping, emailing) has to come after the pivot.

**Q8. How do you handle outbox table growth?**
> Partition by day, archive published rows after a retention window (7 days is typical), and never scan for unpublished rows without a partial index on `published_at IS NULL`. Relays should delete/archive in bounded batches so writers aren't blocked.

**Q9. Someone suggests reading another service's database for a join. What do you say?**
> No. It couples deployments and lets their schema changes break you. If you need the data at volume, subscribe to their events and keep a local read model with a documented staleness SLA; if you need it exactly, call their API.

**Q10. How do you know a saga is stuck in production?**
> Persisted state plus metrics: gauge of sagas by step, age histogram of instances in `RUNNING`/`COMPENSATING`, DLT depth, outbox lag. Alert when a saga in a compensating state exceeds its SLA — that's money sitting in limbo.

---

## 16. Self-Check

- [ ] I can explain the dual-write problem and why `@Transactional` doesn't help.
- [ ] I can write the outbox table + relay from memory, including `SKIP LOCKED`.
- [ ] I can explain why the dedupe row must share a transaction with the effect.
- [ ] I can sketch a saga state machine and name the pivot.
- [ ] I can argue choreography vs orchestration with trade-offs, not preferences.
- [ ] I can describe how compensations fail and what happens then.
- [ ] I can state the staleness SLA for a replicated read model.

---

## 17. Revision Card

```
┌──────────────────────── DISTRIBUTED DATA — 60s RECALL ────────────────────────┐
│                                                                              │
│ ACID stops at one DB.  Across services: eventual consistency.                │
│                                                                              │
│ DUAL WRITE (DB + broker) is the classic bug → use TRANSACTIONAL OUTBOX.      │
│   row + event in one tx → relay publishes → at-least-once → dedupe.          │
│                                                                              │
│ IDEMPOTENT CONSUMER = (consumer, eventId) unique row in the SAME tx          │
│   as the effect. No exactly-once delivery exists.                            │
│                                                                              │
│ SAGA = local txns + compensations.                                           │
│   choreography: <4 steps, implicit flow                                    │
│   orchestration: branches/long waits/audit, persisted state, reaper          │
│   PIVOT: after it, retry forever; before it, compensate.                     │
│                                                                              │
│ COMPENSATION ≠ rollback: refund, release stock — idempotent, visible, may    │
│ itself fail → retry + DLQ + alert.                                           │
│                                                                              │
│ NO ISOLATION in saga → semantic locks (PENDING), truthful UI, SLA alerts.     │
│                                                                              │
│ Data: one owner per fact. Copies are caches with a staleness SLA. Never join  │
│ across services; replicate via events or call the API.                       │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Next:** [07 · Microservices — README](./README.md) | **Previous:** [05 · Distributed Tracing & Observability](./05-Distributed-Tracing-and-Observability.md)
