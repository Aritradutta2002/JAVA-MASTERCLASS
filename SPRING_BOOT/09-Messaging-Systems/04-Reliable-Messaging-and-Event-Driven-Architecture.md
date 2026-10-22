# 04 · Reliable Messaging & Event-Driven Architecture

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★☆ ("How do you guarantee a message is never lost and never processed twice?")
> **Prerequisite:** [03 · RabbitMQ & Message Queue Patterns](./03-RabbitMQ-and-Message-Queue-Patterns.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Courier Analogy](#2-mental-model--the-courier-analogy)
- [3. Delivery Guarantees, Precisely](#3-delivery-guarantees-precisely)
- [4. The Reliability Checklist Seven](#4-the-reliability-checklist-seven)
- [5. Schema Evolution & Compatibility](#5-schema-evolution--compatibility)
- [6. Event Design](#6-event-design)
- [7. Event-Driven Architecture Patterns](#7-event-driven-architecture-patterns)
- [8. Operations: DLQ, Replay, Backfill](#8-operations-dlq-replay-backfill)
- [9. Code Examples](#9-code-examples)
- [10. Common Mistakes & Gotchas](#10-common-mistakes--gotchas)
- [11. Performance & Capacity](#11-performance--capacity)
- [12. Security & Compliance](#12-security--compliance)
- [13. Best Practices Checklist](#13-best-practices-checklist)
- [14. Exercises & Challenges](#14-exercises--challenges)
- [15. Interview Questions & Model Answers](#15-interview-questions--model-answers)
- [16. Self-Check & Revision Card](#16-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> **Exactly-once does not exist end-to-end.** Producers may retry, brokers may redeliver,
> consumers may crash between the write and the offset commit. The only honest formulation
> is: **at-least-once delivery + idempotent processing = effectively-once.**
>
> Reliability needs **seven** things, all of them: (1) atomic write+publish (outbox),
> (2) durable broker settings, (3) publisher confirms/acks, (4) idempotent producer,
> (5) at-least-once consumption with ack-after-commit, (6) idempotent consumer with a dedupe
> key in the same transaction, (7) bounded retries + DLT + alerting.
>
> **Schema evolution is the long-term killer.** You cannot redeploy all consumers atomically.
> Use a schema registry with `BACKWARD` compatibility (new consumers read old data), prefer
> **additive** changes, never reuse a field with a different type, and treat the event
> contract as a public API you're not allowed to break.
>
> **Event design rule:** name events in the **past tense** (`OrderPlaced`), include everything
> the consumer needs to act without calling back (fat events), and put identity +
> versioning in headers.

---

## 1. What & Why

It is easy to build a demo where a message is published and consumed. It is hard to build a
system where, across six months and forty deploys and three incidents, **every event was
processed exactly as intended.**

The failure catalogue is short and brutal:

| Failure | Result without defence |
|---|---|
| Producer timed out after the broker persisted | Duplicate |
| App crashed after the DB commit, before publish | Lost event |
| Consumer crashed after processing, before commit | Duplicate |
| Consumer crashed after commit, before processing | **Lost** (if auto-commit) |
| Schema changed, old consumer still deployed | Poison pill / silent misread |
| Consumer threw on a permanent error | Partition blocked, backlog |
| Retry storm after an outage | Cascading failure |
| Deleted field removed | Consumer NPEs |

Everything in this chapter is a defence against one of those rows. The organising principle:
**each stage either makes a step atomic or makes the next step idempotent.** There is no
third option.

---

## 2. Mental Model — The Courier Analogy

```
┌──────────────────────────────────────────────────────────────────┐
│  A COURIER CANNOT GUARANTEE "EXACTLY ONE" DELIVERY.              │
│                                                                  │
│  1. You write the package into your own ledger (the outbox)      │
│  2. You hand it to the courier with a tracking number            │
│  3. The courier signs for it (publisher confirm / acks=all)      │
│  4. The courier may deliver it twice (retry, crash)              │
│  5. The recipient keeps a list of tracking numbers already opened│
│     (idempotency / dedupe table)                                 │
│  6. Unopenable packages go to a shelf with an alarm              │
│     (DLQ + alerting)                                             │
└──────────────────────────────────────────────────────────────────┘
```

Steps 1 and 5 are the only places *you* control the guarantee. Steps 2–4 are the broker's
and the network's, and they are never perfect. Every "exactly once" claim reduces to "my
recipient ignores duplicates."

**The one-liner for interviews:**

> *Exactly-once isn't a delivery property; it's an effect property. Make the effect
> idempotent and the delivery semantics stop mattering.*

---

## 3. Delivery Guarantees, Precisely

### 3.1 The three levels

| Level | Producer setting | Consumer setting | Result |
|---|---|---|---|
| At-most-once | fire & forget (`acks=0`) | commit before processing | may lose, never duplicates |
| At-least-once | `acks=all` + retry | commit after processing | never loses, may duplicate |
| Effectively-once | at-least-once + idempotent producer | at-least-once + idempotent consumer | observable as once |

"Effectively-once" is the only one you can actually promise. Kafka's transactional producer
gives effective-once **within Kafka** (a consume-transform-produce cycle that commits
atomically and is only visible to `read_committed` consumers). It says nothing about your
Postgres write.

### 3.2 Where duplicates are born

```
T0  consumer fetches offset 100
T1  processes → UPDATE inventory SET reserved = reserved + 1
T2  COMMIT
T3  *** CRASH ***                  ← offset not committed
T4  restart → fetch offset 100 again
T5  processes again → +1 again     ← DUPLICATE EFFECT
```

The gap between T2 and T3 is unavoidable. Options:

1. **Dedupe table** written in the same transaction as the effect (the standard answer).
2. **Naturally idempotent operation** — `SET status='SHIPPED'` is safe; `SET x = x + 1` isn't.
3. **Conditional update** — `UPDATE ... WHERE version = ?` (optimistic), or an `INSERT ...
   ON CONFLICT DO NOTHING` on a business key.
4. **Message id as a business key** — `INSERT INTO payment (id) VALUES (messageId)` — the
   unique constraint *is* the dedupe.

### 3.3 Where losses are born

| Gap | Loss mechanism | Defence |
|---|---|---|
| Producer → broker | publish failed, never noticed | confirms / acks + outbox |
| Broker memory | non-persistent message, restart | persistence + replication |
| Broker disk | `acks=1`, leader dies | `acks=all`, `min.insync.replicas` |
| Broker → consumer | auto-commit before processing | manual ack after commit |
| Consumer | deserialization error swallowed | `ErrorHandlingDeserializer` + DLT |
| Consumer | exception caught and ignored in a try/catch | never swallow; rethrow |
| DLT | nobody consumes it | monitor + alert + replay tool |

That last row is underrated: a DLT is a **lie** if nobody looks at it. A "reliable" system
with 200k dead letters is a system losing 200k events politely.

### 3.4 Out-of-order delivery

At-least-once plus retries means events can arrive late relative to others:

```
t0: OrderPlaced(order 42)   → retried twice, lands at t5
t1: OrderCancelled(order 42) → lands at t2
```

If your consumer blindly applies "last write wins", the cancelled order gets resurrected.
Fixes: **version numbers** (`eventVersion`, monotonic per aggregate) so stale events are
dropped; **timestamps** compared per aggregate; or accepting-process side ordering via a
per-aggregate single consumer.

```java
@Transactional
public void apply(OrderEvent e) {
    var current = repo.findVersion(e.orderId());     // 0 if absent
    if (e.version() <= current) return;              // stale → ignore
    repo.apply(e);
    repo.setVersion(e.orderId(), e.version());
}
```

This is the single most-missed requirement in event-driven systems — mention it unprompted.

---

## 4. The Reliability Checklist Seven

```
1. ATOMIC WRITE         outbox row + business row in one DB transaction
2. DURABLE BROKER       RF>=3, acks=all, min.insync.replicas=2, persistent msgs
3. PUBLISH CONFIRMED    confirm callback / CompletableFuture checked
4. PRODUCER IDEMPOTENT  enable.idempotence / publisher confirms
5. ACK AFTER COMMIT     manual ack, offset committed post-transaction
6. CONSUMER IDEMPOTENT  (consumer,eventId) unique in the SAME transaction
7. BOUNDED RETRY + DLT  retryable vs permanent classified, DLT consumed + alerted
```

Everything else — schema registry, tracing, replay tooling — sits on top. Seven boxes; miss
any one and you have a documented, reproducible hole.

---

## 5. Schema Evolution & Compatibility

### 5.1 The problem

Producers and consumers deploy independently. At any instant, **N producer versions and M
consumer versions coexist**. The contract has to support the whole matrix during a rollout.

### 5.2 Compatibility modes (Schema Registry)

| Mode | Who can upgrade first | Rule |
|---|---|---|
| `BACKWARD` (default) | consumers first | new schema can read data written with the previous |
| `FORWARD` | producers first | old schema can read data written with the new |
| `FULL` | either | both directions |
| `NONE` | — | no checks (you'll regret it) |

For event-driven systems where consumers lag producers, **`BACKWARD`** is the sane default.

### 5.3 Safe vs breaking changes (Avro/JSON Schema/Protobuf)

| Change | Safe? |
|---|---|
| Add an **optional** field with a default | ✅ |
| Remove an optional field (after all consumers ignore it) | ⚠️ two-phase |
| Rename a field | ❌ (it's a remove + add) |
| Change a type (`int` → `string`, `int32` → `int64` ok in Avro) | ❌ |
| Reuse a field name for a different meaning | ❌ (worst kind — silent corruption) |
| Add an enum value | ✅ if consumers have a default branch |
| Make an optional field required | ❌ |

**Two-phase removal** (the discipline that avoids breakage):

1. Release consumers that tolerate the field being absent.
2. Wait for full rollout (verify via per-version consumer metrics).
3. Release producers that stop emitting it.

Skipping step 2 is how "we removed a field and everything broke" happens.

### 5.4 JSON, Avro, Protobuf

- **JSON** — readable, no registry required, but no enforced schema. Fine for low-volume internal events; enforce with JSON Schema at the producer if you must.
- **Avro** — compact, schema-registry native, great for Kafka. Best enforcement story.
- **Protobuf** — compact, strongly typed, excellent tooling, gRPC-friendly. Field numbers give you safe renames if you never reuse a number.

Whichever you pick, **never reuse a name or a number for a different meaning.** That's the
one rule that can't be enforced by tooling.

### 5.5 Event versioning strategies

| Strategy | Description | Verdict |
|---|---|---|
| **Topic versioning** (`orders.v1`, `orders.v2`) | New schema → new topic | Clean, but duplicates infra and splits consumers |
| **Envelope version** (`schemaVersion` header) | Same topic, consumers branch | Simple, but consumers carry legacy code forever |
| **Schema registry + compatibility** | Registry enforces evolution rules | Best default |
| **Fat event + tolerant reader** | Consumers ignore unknown fields | Necessary, not sufficient |

Use the registry **plus** tolerant readers (ignore unknown fields, default missing ones).
That combination survives almost everything.

---

## 6. Event Design

### 6.1 Naming: facts, not commands

| Bad | Why | Good |
|---|---|---|
| `CreateOrder` | a command; implies one receiver | `OrderPlaced` |
| `OrderUpdated` | says nothing; consumers must diff | `OrderShipped`, `OrderAddressChanged` |
| `OrderEvent` | one type, switch-on-action inside | separate types |
| `SendEmail` | command masquerading as event | `OrderConfirmed` (email service reacts) |

**Events are past-tense facts about something that already happened.** Commands are
imperative and have exactly one intended handler. Mixing them collapses your decoupling
into RPC.

### 6.2 Thin vs fat events

- **Thin** (`{orderId}`) — small, but every consumer calls back → N+1 API load and coupling.
- **Fat** (`{orderId, customerId, items, total, currency, placedAt}`) — consumers need no
  callback, but the contract is bigger and PII spreads.

**Default: fat enough that the consumer never has to call the producer.** Include the
identifiers it needs and the fields it acts on, not an entire aggregate dump.

### 6.3 The event envelope

```json
{
  "eventId":   "5b0f0a12-...",
  "eventType": "OrderPlaced",
  "eventVersion": 3,
  "occurredAt": "2026-03-11T09:14:22.117Z",
  "producer":  "order-service/2.14.0",
  "aggregateId": "order-42",
  "aggregateVersion": 7,
  "traceId":   "4bf92f3577b34da6a3ce929d0e0e4736",
  "correlationId": "req-88",
  "data": { "...": "..." }
}
```

- `eventId` — stable, assigned once, reused on retry → the dedupe key.
- `aggregateVersion` — for out-of-order detection.
- `occurredAt` — business time, not publish time.
- `traceId` — links to tracing ([Topic 08 ch 05](../08-Microservices-with-Spring-Boot/05-Distributed-Tracing-and-Observability.md)).

Put identity in **headers** on the wire (Kafka headers / AMQP properties) *and* keep them in
the body for storage and replay. Duplication is cheap; missing metadata isn't.

### 6.4 What not to put in an event

- Another service's internal ids that they may rename.
- Secrets, tokens, full PII sets.
- Whole entity graphs (bloats the log, leaks fields you'll regret).
- Computed values that will drift (send inputs + version; let consumers compute if they
  must, or send the value and accept staleness — decide explicitly).

---

## 7. Event-Driven Architecture Patterns

### 7.1 The core patterns

| Pattern | Shape | Use when |
|---|---|---|
| **Event notification** | `{id}` only | consumers rarely need detail |
| **Event-carried state transfer** | fat event | consumers need to avoid callbacks |
| **Event sourcing** | events as the source of truth | audit is a requirement |
| **CQRS** | separate read model | query needs differ from write needs |
| **Saga** | multi-step with compensation | cross-service business transaction |
| **Claim check** | event holds a pointer to payload in object storage | large payloads (images, documents) |

### 7.2 The event-driven maturity ladder

```
Level 0  RPC everywhere, one "events" topic for logging
Level 1  notifications on state change; consumers call back for detail
Level 2  state-transfer events; consumers keep local read models
Level 3  sagas with compensations; outbox; idempotency everywhere
Level 4  event sourcing for the domains where audit matters; projections rebuilt on demand
```

Most teams claim level 3 and are at level 1. The interview-winning move is describing
*your* actual level and what blocks the next one.

### 7.3 The traps of going event-driven

- **Eventual consistency leaks into the UI.** "Your order appears in history in ~2 seconds"
  must be a product decision, not a surprise.
- **Debugging becomes archaeology.** Without correlation ids and a trace, "why is this
  customer's balance wrong?" has no answer.
- **Consumers multiply.** Six teams subscribe to `OrderPlaced`; now changing it requires six
  conversations. A **public event contract** with a registry and a deprecation policy is the
  only defence.
- **Replay becomes a weapon.** Replaying 3 days of events into a consumer that sends emails
  is an incident. Every consumer needs a **replay mode** (suppress side effects, or write to
  a shadow table).
- **Distributed monolith.** Everything listens to everything and deploys together. If
  services can't be deployed independently, you didn't get the benefit.

---

## 8. Operations: DLQ, Replay, Backfill

### 8.1 DLQ discipline

A DLT/DLQ is a **queue like any other**: it needs a consumer, an owner, and an SLA.

- **Alert** on depth > 0 (or growth rate), with the exception class in the alert payload.
- **Triage UI** — a small admin endpoint listing `(topic, offset, eventId, exception, attempts)`.
- **Replay tool** — republish to the original topic (preserving `eventId` so dedupe protects
  you), then mark resolved. Include a dry-run mode.
- **Retention** — long enough to fix the bug (7–30 days), not "forever because we're scared."
- **Budgets** — a target like "DLQ cleared within 24h" forces the team to own it.

```java
@RestController
@RequestMapping("/admin/dlt")
class DltAdminController {
    /** Republish a specific dead letter back to its origin topic. Dedupe protects us. */
    @PostMapping("/{eventId}/replay")
    public void replay(@PathVariable UUID eventId) {
        var dead = dltStore.find(eventId);                 // stored body + headers
        kafka.send(dead.originTopic(), dead.key(), dead.payload());
    }
}
```

### 8.2 Replay vs backfill

- **Replay** — reprocess existing events because a consumer was buggy. Requires: retention
  window, idempotency, and suppressed external side effects.
- **Backfill** — load historical state into a *new* consumer/read model. Usually done by a
  separate job reading the source of truth, not by rewinding production consumers.
- **Shadow run** — run the new consumer in parallel writing to a shadow table; diff against
  the live one; cut over when identical. The professional way to change consumer logic.

### 8.3 Consumer lag as an SLO

```
lag = end offset - committed offset
```

Track p50/p99 across partitions, alert on *trend* (growing for > 10 min), not just absolute
size. A lag that plateaus at a high number may be acceptable; a lag that grows linearly means
you're losing ground — the consumer is slower than the producer, and you will never catch up.

**Capacity math to quote:** if the producer does 5k msg/s and the consumer does 3k msg/s,
you need to fix the consumer or add partitions; time-to-drain is
`lag / (produce - consume)` — infinite when produce ≥ consume.

---

## 9. Code Examples

### 9.1 Complete idempotent consumer

```java
@Component
public class PaymentEventListener {

    private final JdbcTemplate jdbc;
    private final PaymentService payments;

    @KafkaListener(topics = "orders.v1", groupId = "payment-svc")
    @Transactional
    public void on(ConsumerRecord<String, OrderPlaced> rec, Acknowledgment ack) {
        UUID eventId = header(rec, "eventId");

        // 1. Claim the event. Unique constraint is the lock.
        int claimed = jdbc.update("""
            INSERT INTO processed_events (consumer, event_id, received_at)
            VALUES ('payment', ?, now())
            ON CONFLICT DO NOTHING
            """, eventId);
        if (claimed == 0) { ack.acknowledge(); return; }   // already handled

        // 2. Apply effect — still in the same transaction.
        var e = rec.value();
        payments.charge(new ChargeRequest(
            e.orderId(), e.customerId(), e.total(), e.currency(), eventId));

        // 3. Commit; only then acknowledge.
        ack.acknowledge();
    }

    private UUID header(ConsumerRecord<?, ?> rec, String name) {
        var h = rec.headers().lastHeader(name);
        if (h == null) throw new IllegalArgumentException("missing header " + name);
        return UUID.fromString(new String(h.value(), StandardCharsets.UTF_8));
    }
}
```

### 9.2 Stale-event guard via aggregate version

```java
@Transactional
public void applyShipment(OrderShipped e) {
    var row = jdbc.queryForObject(
        "SELECT aggregate_version FROM order_projection WHERE order_id = ?",
        Integer.class, e.orderId());
    int current = row == null ? 0 : row;
    if (e.aggregateVersion() <= current) {
        log.debug("stale event {} v{} <= {}", e.orderId(), e.aggregateVersion(), current);
        return;
    }
    jdbc.update("""
        INSERT INTO order_projection (order_id, status, aggregate_version)
        VALUES (?, 'SHIPPED', ?)
        ON CONFLICT (order_id) DO UPDATE
        SET status = EXCLUDED.status, aggregate_version = EXCLUDED.aggregate_version
        """, e.orderId(), e.aggregateVersion());
}
```

### 9.3 Replay-mode-aware consumer

```java
public class ReplayAwareListener {
    private final boolean replayMode;   // from a config flag / header

    @KafkaListener(topics = "orders.v1", groupId = "notification-svc")
    public void on(OrderPlaced e) {
        if (replayMode) {
            log.info("[REPLAY] would notify customer {} for order {}", e.customerId(), e.orderId());
            return;                     // no email during replay — classic incident avoided
        }
        notifier.email(e.customerId(), "Order confirmed");
    }
}
```

### 9.4 Schema registry (Avro) producer/consumer

```java
// publisher
@Bean
ProducerFactory<String, OrderPlacedAvro> avroProducer(Properties props) {
    props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, registryUrl);
    props.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, false); // CI registers
    return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new KafkaAvroSerializer());
}
```

```yaml
# CI step — fails the build on a breaking change
schema-registry:
  script: |
    curl -s -X POST "$REGISTRY/subjects/orders.v1-value/versions" \
      -H "Content-Type: application/vnd.schemaregistry.v1+json" \
      -d "{\"schema\": $(jq -Rs . avro/OrderPlaced.avsc)}"
```

`AUTO_REGISTER_SCHEMAS=false` in production is deliberate: an accidental schema change
should fail the deploy, not silently mutate the registry.

### 9.5 Correlation id propagation

```java
@KafkaListener(topics = "orders.v1", groupId = "x")
public void on(ConsumerRecord<String, OrderPlaced> rec) {
    MDC.put("traceId", traceId(rec));
    MDC.put("orderId", rec.key());
    try {
        handle(rec.value());
    } finally {
        MDC.clear();     // never leak MDC across records on a pooled thread
    }
}
```

---

## 10. Common Mistakes & Gotchas

| Mistake | Consequence | Fix |
|---|---|---|
| Claiming exactly-once without qualifying scope | Design falls apart on the DB write | At-least-once + idempotent |
| Dedupe in Redis before the DB transaction | Skipped event on rollback | Dedupe row in the same DB tx |
| Committing offsets before processing | Silent loss | Ack after commit |
| Swallowing exceptions in the listener | Event lost, no trace | Rethrow; let the error handler route |
| `@Transactional` on a private/self-invoked method | No proxy, no tx | Separate bean |
| DLT with no owner | Gradual, invisible data loss | Alert + replay tool + SLA |
| Removing a field from an event immediately | Old consumers NPE | Two-phase removal |
| Reusing a field name for a new meaning | Silent corruption | New field, deprecate the old |
| No `aggregateVersion` | Out-of-order events resurrect stale state | Version guard |
| Replaying into a side-effect consumer | Emails/charges duplicated | Replay mode |
| Unbounded retry | Partition blocked / queue starved | Bounded + DLT |
| Testing only the happy path | Reliability claims unproven | Chaos tests (kill between commit and ack) |
| One topic for all events | Every consumer filters everything; ACLs impossible | Per-domain topics |

---

## 11. Performance & Capacity

- **Throughput math**: `partitions × per-partition throughput` = topic ceiling; `instances ×
  concurrency` ≤ partitions.
- **Idempotency cost**: one extra indexed insert per event. Negligible at 10k/s; the
  `processed_events` table needs partitioning and a TTL (30 days is typical).
- **Fat vs thin events**: fat events cut N+1 calls but increase bytes; measure both.
- **Compression** at the producer makes fat events nearly free.
- **Dedupe table growth**: partition by day, drop old partitions; never `DELETE` row by row.
- **Replay throughput** is usually limited by the *consumer's* dependencies (DB, external
  APIs), not by Kafka. Budget replay time = `backlog / safe consumer rate`; throttle
  replays so they don't starve live traffic.
- **Schema registry** adds a lookup per new schema id (cached client-side after first use).

---

## 12. Security & Compliance

- **Classification per field.** Events replicate PII across every consumer's storage. Tag
  fields and keep regulated data in a minimum number of topics.
- **Encryption in transit** (TLS/SASL) and at rest (broker disk + consumer DBs).
- **Field-level encryption** for sensitive identifiers when the log must be shared broadly.
- **GDPR/CCPA erasure** is hard with an immutable log: prefer storing a reference and
  keeping the PII in one service (crypto-shredding the key is the only true delete in an
  append-only log).
- **Retention** is a compliance control, not just disk hygiene — set the shortest retention
  that meets replay needs.
- **Replay authorization**: replaying a `PaymentCharged` event is a privileged action. Gate
  the replay tool behind an admin role and an audit log.
- **Contract review**: a schema change that widens data exposure (adding an email field)
  needs the same review as an API change.

---

## 13. Best Practices Checklist

- [ ] Never claim exactly-once; document at-least-once + idempotency.
- [ ] Outbox for any event tied to a DB write.
- [ ] Producer idempotence and confirms enabled; failures surfaced.
- [ ] Consumers ack after commit; dedupe row in the same transaction.
- [ ] Aggregate version guard for order-sensitive projections.
- [ ] Event envelope with `eventId`, `eventType`, `eventVersion`, `occurredAt`, `traceId`.
- [ ] Schema registry with `BACKWARD` compatibility; CI fails on breaking change.
- [ ] Additive-only changes; two-phase removal with rollout verification.
- [ ] Bounded retries, not-retryable classification, DLT with an owner and an alert.
- [ ] Replay tool with dry-run and a replay-mode flag for side-effecting consumers.
- [ ] Lag alerting on trend, not just absolute value.
- [ ] Consumer tests include duplicate delivery, out-of-order delivery, and poison payload.
- [ ] Public event contract documented and versioned like an API.

---

## 14. Exercises & Challenges

1. **Prove the duplicate** — kill the consumer between commit and ack; observe the redelivery;
   show the dedupe table prevents the second effect.
2. **Prove the loss** — set `ack-mode=BATCH` and throw mid-batch; observe which records are
   reprocessed and which are skipped; explain why with the offset model.
3. **Out-of-order** — publish `OrderPlaced` v7 after `OrderShipped` v8; prove the version
   guard rejects the stale event.
4. **Schema break** — add a field and remove one in a `BACKWARD` registry; read the error and
   fix the schema.
5. **Two-phase removal** — simulate old consumers by pinning a version; prove the producer
   can't remove the field yet.
6. **Replay drill** — replay 24h of events into a notification consumer with replay mode off;
   count the duplicate emails; then repeat with it on.
7. **DLQ workflow** — poison ten events, then clear the DLT with the replay tool and show zero
   duplicate side effects.
8. **Challenge** — build a `Testcontainers` harness that injects a crash after the DB commit
   and asserts convergence after restart.

---

## 15. Interview Questions & Model Answers

**Q1. How do you guarantee a message is neither lost nor processed twice?**
> You can't guarantee both simultaneously without assuming synchronous atomicity across
> systems, which doesn't exist. You guarantee *no loss* with at-least-once: durable broker,
> acks, outbox, ack-after-commit. Then you guarantee *no double effect* with an idempotent
> consumer whose dedupe row commits in the same transaction as the effect. The pair gives
> effectively-once.

**Q2. Where exactly can a message be lost or duplicated in your pipeline?**
> Lost: producer publish failure unnoticed, non-persistent message, `acks=1` leader failure,
> auto-commit before processing, swallowed exception, unconsumed DLT. Duplicated: producer
> retry after a timeout, consumer crash between commit and offset commit, rebalance while a
> batch is in flight, replay. Each has a specific defence.

**Q3. How do you evolve an event schema without breaking consumers?**
> A schema registry with `BACKWARD` compatibility, additive-only changes with defaults, and a
> two-phase removal where consumers tolerate the missing field before producers stop sending
> it. Never rename a field or reuse a name/number with a new meaning — that's the only
> failure tooling can't catch.

**Q4. How do you handle an event that arrives out of order?**
> Every event carries a per-aggregate version. The consumer compares it with the stored
> version and discards stale events. Without that, a retried `OrderPlaced` can overwrite a
> later `OrderCancelled` — the classic resurrection bug.

**Q5. What's in your DLQ process?**
> Owner, alert on depth or growth, a triage endpoint showing event id + exception + attempts,
> a replay tool with dry-run that preserves `eventId` so dedupe protects us, a retention
> window, and an SLA like "cleared within 24h". A DLT nobody consumes is a silent data-loss
> channel.

**Q6. How do you replay safely?**
> Only into consumers that are idempotent *and* have a replay mode that suppresses external
> side effects. Throttle the replay so live traffic isn't starved, and prefer a shadow table
> plus a diff before cutting over.

**Q7. What's the difference between an event and a command?**
> An event is a past-tense fact with zero or many interested consumers; a command is an
> imperative with exactly one intended handler. Publishing commands on a bus gives you RPC
> with worse tooling; the decoupling people expect from event-driven design comes from
> consumers reacting to facts.

**Q8. How do you stop an event-driven system becoming a distributed monolith?**
> Public event contracts with a registry, per-domain topics, no shared libraries containing
> domain entities, independent deployability as a hard requirement, and a hard rule that no
> consumer depends on another consumer's internal state. If two services must deploy
> together, they're one service.

**Q9. How do you confirm reliability in testing, not just in design?**
> Chaos tests at the exact boundaries: kill after DB commit before publish, kill between
> commit and ack, duplicate an event, deliver out of order, poison payload, broker
> restart. Then assert convergence — the final state is correct — rather than asserting a
> specific number of deliveries.

**Q10. What metric tells you a consumer is falling behind?**
> Consumer lag trend — p99 lag growing monotonically. Absolute lag can be a stable backlog;
> a linear increase means produce rate exceeds consume rate, and it will never self-heal.

---

## 16. Self-Check & Revision Card

- [ ] I can name the three delivery levels and which one is real.
- [ ] I can list the seven reliability boxes from memory.
- [ ] I can point at the exact code gap where duplicates are created.
- [ ] I can explain the dedupe-in-same-transaction requirement and why Redis-first is wrong.
- [ ] I can state the compatibility modes and the safe change list.
- [ ] I can describe two-phase field removal.
- [ ] I can design an envelope and justify each field.
- [ ] I can run a replay safely.
- [ ] I can explain a stale-event guard with versions.

```
┌────────────────── RELIABLE MESSAGING — 60s RECALL ───────────────────────────┐
│ NO exactly-once. At-least-once + IDEMPOTENT CONSUMER = effectively once.     │
│                                                                              │
│ 7 boxes: outbox · durable broker · confirms · idempotent producer ·          │
│          ack-after-commit · dedupe-in-tx · bounded retry + DLT.              │
│                                                                              │
│ Dedupe row MUST share the transaction with the effect (Redis-first = bug).   │
│                                                                              │
│ SCHEMA: registry + BACKWARD + additive only + two-phase removal.             │
│ Never reuse a name/number. Rename = remove + add.                            │
│                                                                              │
│ ORDER: aggregateVersion guard, or a retried OrderPlaced resurrects a         │
│ cancelled order.                                                             │
│                                                                              │
│ DLT is a queue: owner, alert, replay tool (dry-run, preserves eventId), SLA. │
│ Replay needs idempotency AND a side-effect suppression flag.                 │
│                                                                              │
│ Lag: alert on TREND. produce >= consume ⇒ never drains.                      │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [03 · RabbitMQ & Message Queue Patterns](./03-RabbitMQ-and-Message-Queue-Patterns.md) · **Up:** [README](./README.md)
