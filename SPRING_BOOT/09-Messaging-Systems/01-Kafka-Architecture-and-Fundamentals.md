# 01 · Kafka Architecture & Fundamentals

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★★ ("Explain how Kafka guarantees ordering")
> **Prerequisite:** [08 · Distributed Data Management & Saga Patterns](../08-Microservices-with-Spring-Boot/06-Distributed-Data-Management-and-Saga-Patterns.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Commit Log](#2-mental-model--the-commit-log)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
- [4. Internal Working Mechanism](#4-internal-working-mechanism)
- [5. Step-by-Step — A Message's Journey](#5-step-by-step--a-messages-journey)
- [6. Architecture Diagrams](#6-architecture-diagrams)
- [7. Production Use Cases](#7-production-use-cases)
- [8. Code Examples](#8-code-examples)
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

> Kafka is a **distributed, partitioned, replicated append-only log** — not a queue.
> Producers append records to a **partition**; consumers **pull** at their own offset and
> never delete data (the broker expires it by time/size).
>
> A **topic** is split into **partitions**. Ordering is guaranteed **only within a partition**.
> The partition is chosen by hashing the **key**, so all events for one aggregate land in
> one partition and stay ordered. No key → round-robin/sticky → no ordering.
>
> **Replication factor 3, `acks=all`, `min.insync.replicas=2`** is the production durability
> baseline. One replica is the **leader** (serves reads/writes), the others **follow** by
> fetching. **ISR** = replicas caught up enough to be elected leader. If ISR < `min.insync.replicas`,
> writes fail — that's the trade you want (fail loudly, don't lose data).
>
> Consumers are grouped. Within a group, **each partition is assigned to exactly one
> consumer** — the max useful parallelism equals the partition count. Different groups
> read independently (broadcast semantics). Offsets are committed to `__consumer_offsets`.
>
> Kafka does **not** give you exactly-once delivery across systems. It gives at-least-once
> (default) or effectively-once *within Kafka* via idempotent producer + transactions.
> End-to-end exactly-once is your consumer's idempotency problem.

---

## 1. What & Why

### 1.1 The problem

A traditional queue (RabbitMQ, SQS) deletes a message once it's consumed. That is correct
for **work distribution** — "resize this image once." It is wrong for three common needs:

1. **Multiple independent consumers of the same event.** With a queue you either fan out
   to N queues (N copies, N couplings) or the first consumer wins. With a log, each group
   keeps its own offset.
2. **Replay.** A new service joins, or a bug corrupts a projection — you need to re-read
   history. A queue has already deleted it.
3. **Throughput with ordering.** Writes need to be sequential per key, but the system must
   handle 100k+ msg/s.

Kafka's answer: keep the log, let consumers track their own position.

### 1.2 What Kafka actually is

| Aspect | Kafka | Classic queue (RabbitMQ/SQS) |
|---|---|---|
| Retention | Time/size based; data stays after consumption | Deleted on ack |
| Consumer model | Pull; consumer owns the offset | Push (mostly) |
| Replay | Yes, reset the offset | No |
| Ordering | Per partition | Per queue (single consumer) |
| Fan-out | Cheap — groups share the log | Expensive — one queue per subscriber |
| Throughput | Very high (sequential IO, batching, zero-copy) | High but lower ceiling |
| Routing | Topic + key → partition | Exchanges/bindings, rich routing |
| Per-message TTL/priority/delay | Not native | Native |

**Choose Kafka** for event streams, event sourcing, CDC pipelines, audit logs, high
throughput, and any consumer that may need history.
**Choose RabbitMQ** for task queues, complex routing, per-message TTL/retry/delay, and
lower-volume request/reply work.

### 1.3 The five parts you must name

- **Broker** — a Kafka server; holds partitions and replicas.
- **Topic** — a named logical stream, split into partitions.
- **Partition** — an ordered, immutable log with an offset per record; the unit of
  parallelism and ordering.
- **Replica** — a copy of a partition on another broker; one leader + N-1 followers.
- **Consumer group** — a set of consumers sharing partition assignments for a topic.

---

## 2. Mental Model — The Commit Log

```
topic: orders  (3 partitions)

 partition 0:  [ 0 ][ 1 ][ 2 ][ 3 ][ 4 ][ 5 ][ 6 ]  ← append only, offset per record
                                                     consumer A at offset 4
 partition 1:  [ 0 ][ 1 ][ 2 ][ 3 ]                 consumer B at offset 1
 partition 2:  [ 0 ][ 1 ][ 2 ][ 3 ][ 4 ]            consumer C at offset 5

   writes always go to the END.
   readers never move the log; they move a NUMBER.
   retention deletes from the FRONT (by age/size), never from the middle.
```

Three consequences that answer half the interview questions:

1. **The log is the source of truth; consumer position is metadata.** Resetting an offset
   is O(1) and non-destructive — that's replay.
2. **Ordering is a property of the log, not the topic.** Two records are ordered iff they
   are in the same partition. Say this precisely.
3. **Partition count sets your ceiling.** Both throughput and consumer parallelism are
   bounded by it. You can add partitions, but that **breaks key→partition mapping**, so
   ordering across the change is no longer guaranteed for existing keys.

**The one-liner:**

> *Kafka trades per-message features (TTL, priority, selective ack) for a durable ordered
> log that many readers can re-read cheaply.*

---

## 3. Deep Dive — Theory

### 3.1 Partitions and keys

```java
// key present → hash(key) % numPartitions → same key, same partition, ordered
producer.send(new ProducerRecord<>("orders", orderId, eventJson));

// key null → sticky partitioning (batches to one partition, rotates) → no ordering guarantee
producer.send(new ProducerRecord<>("orders", eventJson));
```

Rules:

- **Key by aggregate id** (`orderId`, `accountId`) when order matters for that aggregate.
- **Key by tenant** to keep per-tenant order; **hot keys** become hot partitions. If one
  tenant is 40% of traffic, its partition is your bottleneck — sometimes you *want*
  to break ordering for that tenant (e.g. key by `tenantId:shard`).
- **No key = maximum throughput, zero ordering.** Fine for metrics, not for state changes.
- **Changing partition count breaks the hash** for existing keys. Plan capacity up front;
  if you must grow, key with an explicit bucket (`key + "-" + (hash % 64)`) so you can
  expand without remapping, at the cost of ordering granularity.

### 3.2 Replication, leader/follower and ISR

For each partition: one **leader** (all reads and writes), N-1 **followers** that fetch
from the leader like consumers.

- **ISR (In-Sync Replicas)** — followers whose lag is within `replica.lag.time.max.ms`
  (default 30s). Only ISR members are eligible for leader election, which is what prevents
  silent data loss on failover.
- **`acks`** — how many replicas must acknowledge before the producer call returns:
  - `acks=0` — fire and forget. Data loss on broker failure. Never for business data.
  - `acks=1` — leader only. Lost if the leader dies before followers fetch.
  - `acks=all` (`-1`) — leader + all ISR. The production default.
- **`min.insync.replicas=2`** with `acks=all` — the write fails (`NotEnoughReplicas`)
  if fewer than 2 replicas are in sync. **This combination is the standard durability
  contract.** Two brokers down out of three → writes fail → your app knows, instead of
  silently losing data.

**`unclean.leader.election.enable=false`** (default since 0.11): never elect a
non-ISR replica. Availability loses to consistency here, deliberately — an out-of-date
leader would silently drop writes that the other replicas had.

### 3.3 Consumer groups and rebalancing

- A group has one **group coordinator** broker.
- Partitions are assigned by a **partition assignor** (`RangeAssignor`, `CooperativeStickyAssignor`).
- **Rebalance** happens on join/leave/crash, or when topic partition count changes.
- During a rebalance, assignment is recomputed; `CooperativeStickyAssignor` (incremental
  cooperative rebalancing) avoids the **stop-the-world** pause of the eager protocol.
  Use it: `partition.assignment.strategy=org.apache.kafka.clients.consumer.CooperativeStickyAssignor`.

**Heartbeat vs poll:** the consumer must call `poll()` within `max.poll.interval.ms`
(default 5 min) or it's considered dead and the group rebalances. `session.timeout.ms`
(default 45s in modern clients) is the heartbeat-level failure detection. **A slow handler
= a rebalance = a duplicate-processing storm.** Tune `max.poll.records` down, or hand off
long work to another thread.

**More consumers than partitions → idle consumers.** That's not an error, it's wasted
capacity. Partition count is your parallelism budget.

### 3.4 Offsets and delivery semantics

| Semantic | How | Consequence |
|---|---|---|
| **At-most-once** | commit offset *before* processing | Crash → message lost |
| **At-least-once** | commit offset *after* processing | Crash → duplicate → need idempotency |
| **Effectively-once (Kafka-internal)** | transactions + `read_committed` | Atomic Kafka-to-Kafka, `isolation.level=read_committed` |

The default Spring Kafka behaviour with `enable.auto.commit=true` (client auto-commit) is
**at-least-once-ish but dangerous**: offsets commit on a timer, so a crash after commit
but before processing loses messages. Prefer `enable.auto.commit=false` with
`AckMode.MANUAL_IMMEDIATE` or `AckMode.RECORD` (Spring commits after the listener returns).

`auto.offset.reset` only applies **when there is no committed offset**: `earliest`,
`latest`, `none` (throw). A new group with `latest` starts at the end — a classic
"why is my consumer not receiving anything" bug.

### 3.5 Log storage internals

A partition is a directory of **segment** files:

```
orders-0/
  00000000000000000000.log     ← records
  00000000000000000000.index   ← offset → file position
  00000000000000000000.timeindex
  00000000000000123456.log     ← next segment (rolls at segment.bytes / segment.ms)
```

- Writes are **sequential appends** — hence high throughput on spinning disks.
- Reads use the offset index to seek directly, then scan.
- **Retention**: `retention.ms` (default 7 days) or `retention.bytes`; the **active**
  segment is never deleted.
- **Compaction** (`cleanup.policy=compact`): keeps the **latest record per key** forever.
  Perfect for "current state" topics (e.g. `customer-profile`), wrong for event streams.
  `delete,compact` gives both.
- **Zero-copy** (`sendfile`) moves bytes from page cache to the socket without user-space
  copies — a big part of Kafka's throughput.
- **Page cache** does the caching; the JVM heap should *not* be huge (6–8 GB is typical
  for brokers). Over-sizing heap steals page cache and destroys performance.

### 3.6 Exactly-once, honestly

Kafka supports two things:

1. **Idempotent producer** (`enable.idempotence=true`, default since 3.0) — a producer id +
   sequence number per partition lets the broker dedupe retries. This fixes *producer
   retry duplication* only.
2. **Transactions** — `transactional.id`, `initTransactions`, `beginTransaction`,
   `send`, `sendOffsetsToTransaction`, `commitTransaction`. Gives atomic
   "consume-transform-produce" **within Kafka**, visible to `read_committed` consumers.

It does **not** make your `INSERT INTO orders` atomic with the Kafka write. That's the
outbox pattern ([Topic 08 ch 06](../08-Microservices-with-Spring-Boot/06-Distributed-Data-Management-and-Saga-Patterns.md)).
Saying "Kafka gives exactly-once" without that caveat is the classic wrong answer.

### 3.7 KRaft — no ZooKeeper

Since Kafka 3.3 (production-ready) and the default in 4.0, cluster metadata lives in
Kafka itself via the **KRaft** controller quorum, replacing ZooKeeper. Benefits: one
system to operate, faster controller failover, support for far more partitions. For
interviews: know that ZooKeeper is legacy, and that a KRaft cluster runs
`process.roles=broker,controller` or separate controller nodes (3 or 5 for fault
tolerance).

---

## 4. Internal Working Mechanism

### 4.1 Produce path

1. Producer serializes key/value, computes partition (`hash(key) % partitions`, or sticky
   for null keys).
2. Record goes into the **accumulator**, batched per partition (`batch.size`, `linger.ms`).
3. A **sender thread** drains batches to the partition's **leader**.
4. The broker **appends to the leader's log** and writes to the page cache; replication
   happens by followers fetching.
5. If `acks=all`, the leader waits until all ISR have appended, then responds.
6. Failures are retried per `retries`/`delivery.timeout.ms`; `max.in.flight.requests.per.connection`
   >1 with idempotence still preserves order (idempotent producer allows up to 5).

**`delivery.timeout.ms` ≥ `linger.ms` + `request.timeout.ms`** — the total budget. If
exceeded, the send fails permanently and lands in the callback.

### 4.2 Consume path

1. Consumer sends `Fetch` to the leader for each assigned partition (from its position).
2. Broker returns records; **zero-copy** path for uncompressed/`sendfile`-eligible data.
3. Consumer deserializes, processes, advances position in memory.
4. Offset commits go to `__consumer_offsets` (a compacted topic) — either auto or manual.
5. `poll()` loop must stay under `max.poll.interval.ms`.

### 4.3 Rebalance protocol (cooperative)

```
JoinGroup ──► leader consumer computes assignment ──► SyncGroup ──► assignments
     ▲                                                                  │
     └──────────── on member change (incremental: only moved partitions)─┘
```

With `CooperativeStickyAssignor`, members keep their partitions and only revoked ones are
reassigned in a second round — no full stop.

### 4.4 Failure and recovery

- **Broker down** → controller elects a new leader from ISR for affected partitions;
  clients get `NotLeaderForPartition` and refresh metadata.
- **Consumer down** → session timeout → rebalance → its partitions move.
- **Disk full** → broker goes read-only (`log.dir` full) → producers get errors. Monitor
  disk.

---

## 5. Step-by-Step — A Message's Journey

```
Producer                  Leader (broker 1)        Follower (broker 2,3)      Consumer
   │                             │                        │                      │
   │ send(key=order-42)          │                        │                      │
   ├────────────────────────────►│                        │                      │
   │                             │ append to log          │                      │
   │                             │ offset 1_234_567       │                      │
   │                             ├───fetch────────────────►│ replicate            │
   │                             │◄──ack───────────────────┤                      │
   │◄─── ack (acks=all) ─────────┤                        │                      │
   │                             │                        │                      │
   │                             │◄────────────── fetch(offset=1_234_560) ───────┤
   │                             ├────── batch + high watermark ────────────────►│
   │                             │                        │            process   │
   │                             │◄────────── commit(1_234_568) ─────────────────┤
   │                             │ write __consumer_offsets                      │
```

Note the consumer is **one message behind** what it just processed at any instant: it
commits *after* processing. That gap is exactly where duplicates come from.

---

## 6. Architecture Diagrams

```
┌─────────────────────────── KAFKA CLUSTER (KRaft) ───────────────────────────┐
│                                                                             │
│  broker 1                broker 2                 broker 3                  │
│  ┌────────────────┐      ┌────────────────┐      ┌────────────────┐        │
│  │ orders-0 LEADER│      │ orders-0 FOLL  │      │ orders-0 FOLL  │        │
│  │ orders-1 FOLL  │      │ orders-1 LEADER│      │ orders-1 FOLL  │        │
│  │ orders-2 FOLL  │      │ orders-2 FOLL  │      │ orders-2 LEADER│        │
│  └────────────────┘      └────────────────┘      └────────────────┘        │
│           ▲                      ▲                      ▲                   │
│           └──────── controller quorum (3 nodes) ───────┘                   │
└─────────────────────────────────────────────────────────────────────────────┘
        ▲                                        ▲
        │ produce (acks=all)                     │ consume (pull)
        │                                        │
┌───────────────┐  ┌───────────────┐   ┌──────────────────────────────────────┐
│ order-service │  │catalog-service│   │ group: inventory  → partitions 0,1,2 │
│ (producer)    │  │(producer)     │   │ group: analytics  → partitions 0,1,2 │
└───────────────┘  └───────────────┘   │ group: search     → partitions 0,1,2 │
                                        └──────────────────────────────────────┘
        each GROUP reads the whole topic independently
        WITHIN a group, one partition → one consumer
```

---

## 7. Production Use Cases

- **Order/event pipeline** — `orders.v1` keyed by `orderId`; inventory, notification,
  analytics, search-index consumers each in their own group.
- **CDC into Kafka** — Debezium tails the Postgres WAL, emits row changes; downstream
  builds read models without touching the source DB.
- **Audit / compliance log** — an immutable, replayable record of every state change
  with long retention; the log *is* the evidence.
- **Metrics/telemetry ingestion** — high throughput, no ordering requirement, null keys,
  short retention.
- **Cache invalidation bus** — compacted topic holding "current entity version" so a new
  cache node can warm up from the log.
- **Event sourcing store** — append-only per-aggregate stream, replayed to rebuild state.

---

## 8. Code Examples

### 8.1 Topic as code (durability contract explicit)

```java
@Configuration
class TopicConfig {
    @Bean
    NewTopic ordersTopic() {
        return TopicBuilder.name("orders.v1")
            .partitions(12)                 // sets max consumer parallelism
            .replicas(3)                    // survive one broker loss
            .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
            .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")   // 7 days
            .config(TopicConfig.CLEANUP_POLICY_CONFIG, "delete")
            .build();
    }
}
```

### 8.2 Producer with a key, acks=all, idempotence

```yaml
spring:
  kafka:
    bootstrap-servers: kafka-1:9092,kafka-2:9092,kafka-3:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      enable-idempotence: true          # dedupes retries at the broker
      compression-type: lz4
      properties:
        linger.ms: 10                   # small batching win; latency cost ~10ms
        batch.size: 32768
        max.in.flight.requests.per.connection: 5
        delivery.timeout.ms: 120000
```

```java
@Service
public class OrderPublisher {
    private final KafkaTemplate<String, OrderEvent> kafka;

    public OrderPublisher(KafkaTemplate<String, OrderEvent> kafka) { this.kafka = kafka; }

    public void publish(OrderEvent e) {
        // key = aggregate id → same partition → per-order ordering
        kafka.send("orders.v1", e.orderId(), e)
             .whenComplete((res, ex) -> {
                 if (ex != null) log.error("publish failed order={}", e.orderId(), ex);
                 else log.debug("order={} p={} off={}", e.orderId(),
                         res.getRecordMetadata().partition(),
                         res.getRecordMetadata().offset());
             });
    }
}
```

> In a service that owns data, publish via the **outbox**, not directly from the request
> thread — see Topic 08 ch 06. Direct publish is right only when the event has no DB
> write to be atomic with.

### 8.3 Consumer with manual ack and idempotency

```java
@KafkaListener(topics = "orders.v1", groupId = "inventory-svc",
               containerFactory = "manualAckFactory")
@Transactional
public void on(ConsumerRecord<String, OrderEvent> rec, Acknowledgment ack) {
    if (!dedupe.firstTime("inventory", rec.headers().lastHeader("eventId"))) {
        ack.acknowledge();
        return;
    }
    inventory.reserve(rec.value());
    ack.acknowledge();     // after the DB transaction commits
}
```

```java
@Bean
ConcurrentKafkaListenerContainerFactory<String, OrderEvent> manualAckFactory(
        ConsumerFactory<String, OrderEvent> cf) {
    var f = new ConcurrentKafkaListenerContainerFactory<String, OrderEvent>();
    f.setConsumerFactory(cf);
    f.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    f.getContainerProperties().setSyncCommits(true);
    f.setConcurrency(6);                     // ≈ partitions / instances
    return f;
}
```

### 8.4 Reading a specific partition and seeking (replay)

```java
@Bean
ApplicationRunner replay(@Qualifier("kafkaListenerContainerFactory")
                         ConcurrentKafkaListenerContainerFactory<String, OrderEvent> f) {
    return args -> {
        var container = f.createContainer(
            new org.apache.kafka.clients.consumer.ConsumerRecord<>(),
            new MessageListener<String, OrderEvent>() {
                public void onMessage(ConsumerRecord<String, OrderEvent> r) { /* reprocess */ }
            }, new NewTopic("orders.v1", 12, (short) 3));
        container.start();
    };
}
```

Simpler and safer in practice: a **replay topic** + a dedicated consumer group with
`auto.offset.reset=earliest`, rather than seeking a live production group.

### 8.5 Health/observability

```java
@Component
class ConsumerLagMonitor {
    private final KafkaListenerEndpointRegistry registry;
    private final MeterRegistry meters;

    @Scheduled(fixedDelay = 15000)
    void report() {
        registry.getListenerContainers().forEach(c -> {
            var p = (org.springframework.kafka.listener.KafkaMessageListenerContainer<?, ?>) c;
            p.metrics().forEach((k, v) -> meters.gauge("kafka.consumer." + k,
                    v, Number::doubleValue));
        });
    }
}
```

---

## 9. Common Mistakes & Gotchas

| Mistake | Consequence | Fix |
|---|---|---|
| Null key + expecting ordering | Records spread across partitions, reordered | Key by aggregate id |
| `acks=1` on business data | Silent loss when leader dies | `acks=all` + `min.insync.replicas=2` |
| `replicas=1` in production | Broker loss = data loss | RF 3 |
| `auto.offset.reset=latest` for a new group | "Consumer receives nothing" | `earliest` where history matters |
| Auto-commit while processing | Loss (commit before processing) | Manual/record ack after success |
| Slow handler exceeding `max.poll.interval.ms` | Rebalance storm + duplicates | Lower `max.poll.records`, async work, raise interval carefully |
| More consumers than partitions | Idle consumers | Increase partitions (planning) or reduce instances |
| Increasing partitions for one keyed stream | Key→partition mapping breaks; ordering lost | Bucket keys or accept a reorder across the change |
| Retry loop inside the listener forever | Partition stuck, lag grows | Bounded retries + DLT |
| Huge messages (MBs) | Broker pressure, latency | Object store + reference id |
| `transactional.id` shared by instances | Fencing — one instance kills the other | Unique id per instance |
| Compaction on an event stream | Events silently vanish | `delete` for events, `compact` for state |
| Assuming global ordering | Bugs in 100% of multi-partition designs | Order only per key |
| Unbounded consumer-lag alerts missing | You learn from customers | Alert on `records-lag-max` |

---

## 10. Performance Considerations

- **Partition count** — throughput ceiling. Rule of thumb: start with `max(target throughput / partition throughput)` and grow with headroom; too many partitions hurts (controller load, rebalance time, file handles).
- **Batching** — `linger.ms=5..20` + `batch.size=32–64KB` gives large throughput gains for a few ms of latency. Compression (`lz4`/`zstd`) is nearly free CPU for big wins.
- **Consumer concurrency** — set to partitions/instances. Over-setting wastes threads.
- **Heap vs page cache** — keep broker heap at 6–8 GB; let the OS cache the log.
- **`fetch.min.bytes` / `fetch.max.wait.ms`** — small `min.bytes` with no wait makes a chatty consumer; tune for throughput on backfill jobs.
- **Rebalance cost** — cooperative assignor, static membership (`group.instance.id`) to avoid rebalances on rolling restarts.
- **`max.poll.records`** — the real batch-size knob for processing; large batches improve throughput, increase rebalance risk.
- **Tiered storage** (KIP-405) — offload old segments to object storage; long retention without huge local disks.
- **Monitor**: `records-lag-max` per partition, `under-replicated-partitions`, `offline-partitions`, produce/fetch latency, disk usage, ISR shrink rate.

---

## 11. Security Considerations

- **TLS everywhere** (`security.protocol=SSL` or `SASL_SSL`) — plaintext Kafka is sniffable and injectable.
- **Authentication**: SASL/SCRAM, mTLS, or OIDC (Kafka 3.x+). One credential per service.
- **Authorization**: ACLs at topic + group level. A producer should not be able to read; a consumer should not be able to write. Deny `*` groups.
- **Per-tenant topics or filtering** — Kafka ACLs are topic-level, not record-level. There is **no row-level security**: any consumer with topic read access sees every tenant's events. Isolate sensitive streams into separate topics.
- **PII in events** — events are copied, replayed, and retained for days. Minimise fields, encrypt sensitive payloads, and honour erasure (a compacted topic with the key nulled is one approach; a truly immutable log needs field-level crypto with key destruction).
- **Schema registry** — enforce compatibility so a malicious/buggy producer can't inject fields consumers trust implicitly.
- **DLT access** — dead letters often contain the most sensitive records; restrict them.
- **Audit** — enable broker authorizer logs; alert on new producer client-ids writing to money topics.

---

## 12. Best Practices Checklist

- [ ] RF=3, `min.insync.replicas=2`, `acks=all`, `unclean.leader.election=false`.
- [ ] Topics created as code with explicit partitions, retention, cleanup policy.
- [ ] Every state-change event is **keyed by aggregate id**.
- [ ] Null keys only where ordering is provably irrelevant.
- [ ] `enable.idempotence=true` on producers.
- [ ] `enable.auto.commit=false`; ack after successful processing.
- [ ] Consumer idempotent (dedupe table in the same transaction as the effect).
- [ ] Bounded retries → DLT; not-retryable exceptions skip retries.
- [ ] `max.poll.interval.ms` and handler duration reconciled; long work offloaded.
- [ ] Cooperative sticky assignor + static membership ids.
- [ ] Consumer lag alerting per group; under-replicated partition alerting.
- [ ] Schema registry with `BACKWARD` compatibility; `eventId`, `eventType`, `occurredAt` in headers.
- [ ] Topic naming convention (`domain.entity.vN`) and documented ownership.

---

## 13. Hands-On Exercises

1. **Prove per-partition ordering** — publish 1000 records with key `A` and 1000 with key `B`
   concurrently; assert each key's sequence is intact but interleaving is arbitrary.
2. **Break ordering** — repeat with null keys; explain the observed interleaving.
3. **Rebalance observation** — run 3 consumers in one group, kill one, watch assignment move
   with `kafka-consumer-groups.sh --describe`.
4. **Lag injection** — make the listener sleep 2s; watch `records-lag-max` climb and
   recover after restart.
5. **Duplicate proof** — kill the app mid-batch; confirm at least one event is delivered twice
   and that the dedupe table prevents a double side effect.
6. **Retention** — set `retention.ms=60000`, produce, wait, confirm old segments are deleted.
7. **Compaction** — create a compacted topic, publish 3 values for one key, restart a
   consumer at `earliest`, confirm only the latest value survives.

---

## 14. Code Challenges

1. **Keyed ordering test** using `Testcontainers` Kafka: assert per-key monotonic sequence.
2. **Idempotent producer demo** — force retries with a flaky proxy, prove the broker's log
   has no duplicates with `enable.idempotence=true`.
3. **Transactional produce** — atomically produce to two topics and abort; prove `read_committed`
   consumers see nothing.
4. **Manual rebalance-free rolling deploy** — configure `group.instance.id` and prove a rolling
   restart causes no rebalance.
5. **Lag dashboard** — a `@Scheduled` job that exports per-partition lag as Micrometer gauges
   and a Grafana query.
6. **DLT replay tool** — republish from DLT with dedupe, reporting duplicate count.

---

## 15. Interview Questions & Model Answers

**Q1. Explain Kafka's architecture in 60 seconds.**
> Brokers store topics; each topic is split into partitions, each partition is an ordered
> append-only log replicated across brokers with one leader and followers. Producers append
> to the leader's partition, chosen by hashing the key. Consumers pull, tracking an offset
> per partition; a consumer group splits partitions among its members so each partition has
> exactly one consumer in that group. Metadata is managed by a KRaft controller quorum.

**Q2. How does Kafka guarantee ordering?**
> It doesn't — partitions do. Records in one partition are ordered by offset. Ordering across
> a topic requires one partition (which caps throughput) or a key that maps related records
> to the same partition. So we key by aggregate id and accept that different orders may
> interleave.

**Q3. What happens if a consumer crashes?**
> Its session times out or heartbeats stop; the group coordinator triggers a rebalance and
> its partitions move to other members. Since the offset was committed after processing, the
> last batch may be reprocessed — hence idempotent consumers. With `max.poll.interval.ms`
> exceeded, it's also evicted even if the process is alive.

**Q4. What is ISR and why does it matter?**
> In-Sync Replicas — followers caught up within `replica.lag.time.max.ms`. Only ISR members
> can be elected leader, so a lagging replica can't cause silent data loss. Combined with
> `acks=all` and `min.insync.replicas`, ISR is the mechanism that turns replication into a
> durability guarantee.

**Q5. How do you achieve exactly-once?**
> Within Kafka: idempotent producer plus transactions, with consumers at `read_committed`.
> Across systems: you can't — you get at-least-once plus idempotent consumers, or an outbox
> so the DB write and the event are atomic.

**Q6. How do you handle a poison message?**
> Bound the retries, classify errors (deserialization/schema/validation are not retryable),
> and route to a DLT with the original headers and exception. Then a replay tool with dedupe
> so reprocessing is safe.

**Q7. Kafka vs RabbitMQ?**
> Kafka is a retained, partitioned log: replay, cheap fan-out, very high throughput, but no
> per-message TTL/priority and only per-partition ordering. RabbitMQ is a broker with rich
> routing, per-message TTL, dead-letter exchanges and priorities: better for task queues and
> complex routing at lower volume. Pick by whether you need history/replay or routing.

**Q8. What happens when you add partitions?**
> Parallelism grows, and the key→partition hash changes for existing keys. So ordering for a
> given key is broken across the change. If you need long-term stability, bucket keys
> explicitly or plan partition counts generously up front.

**Q9. How do you size consumer concurrency?**
> Equal to partitions divided by instances — more threads than partitions is wasted. Also
> check `max.poll.interval.ms` against handler latency; if processing is slow, lower
> `max.poll.records` or offload work.

**Q10. Why not use a huge broker heap?**
> Kafka relies on the OS page cache for reads. A large heap steals RAM from page cache,
> causes GC pauses and hurts throughput. 6–8 GB heap on a large-RAM host is the norm.

---

## 16. Self-Check

- [ ] I can explain partition, offset, replica, ISR, consumer group without notes.
- [ ] I can state exactly what ordering guarantee Kafka gives.
- [ ] I can explain `acks=all` + `min.insync.replicas` together.
- [ ] I can describe the producer path and the consumer path.
- [ ] I know why auto-commit is dangerous and what replaces it.
- [ ] I can explain rebalancing and how to avoid it on deploys.
- [ ] I can compare Kafka and RabbitMQ by use case.
- [ ] I can state the limits of Kafka's exactly-once.

---

## 17. Revision Card

```
┌──────────────────────────── KAFKA — 60s RECALL ───────────────────────────────┐
│ Log, not queue: append-only, retention by time/size, consumers own offsets.   │
│                                                                               │
│ ORDERING = per partition only. key → hash → partition. Same key, same order.  │
│ PARALLELISM ≤ partition count (per group).                                    │
│                                                                               │
│ DURABILITY: RF=3, acks=all, min.insync.replicas=2, no unclean election.       │
│ ISR = caught-up replicas; only they can become leader.                        │
│                                                                               │
│ DELIVERY: at-most-once (commit first) | at-least-once (commit after) |        │
│ effectively-once only inside Kafka (idempotent producer + transactions).     │
│                                                                               │
│ REBALANCE: member change → reassignment. CooperativeSticky + group.instance.id│
│ to avoid pauses. Slow poll > max.poll.interval.ms ⇒ eviction + duplicates.    │
│                                                                               │
│ STORAGE: segments, offset index, cleanup=delete|compact, page cache, zero-copy│
│ KRaft replaced ZooKeeper.                                                     │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

**Next:** [02 · Producers, Consumers & Spring Kafka](./02-Producers-Consumers-and-Spring-Kafka.md)
