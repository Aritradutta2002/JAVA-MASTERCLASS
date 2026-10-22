# 09 — Messaging Systems

> **Level:** Intermediate → Expert
> **Interview Frequency:** ★★★★★ (Kafka questions appear in nearly every senior backend loop)
> **Prerequisite:** [08 · Microservices](../08-Microservices-with-Spring-Boot/README.md)
> **Last Updated:** 2026

---

## 🎯 Why This Topic Is Separate

Async messaging is the load-bearing wall of microservices, and it is where the most
confident-sounding wrong answers live. The three that matter:

1. **"Kafka guarantees ordering."** It guarantees ordering **within a partition**. Say
   otherwise and the interview is effectively over.
2. **"Kafka gives exactly-once."** It gives effectively-once *inside Kafka*, and nothing at
   all about your database. End-to-end is your consumer's idempotency problem.
3. **"Just add a broker and it's decoupled."** You've added a second write that isn't
   atomic with the first — the dual-write problem ([Topic 08 ch 06](../08-Microservices-with-Spring-Boot/06-Distributed-Data-Management-and-Saga-Patterns.md)).

This topic covers the log model, the producer/consumer configuration that determines
correctness, the broker that *isn't* Kafka (and when it's the better pick), and the
reliability/schema discipline that keeps the system honest after six months of deploys.

---

## 📚 Chapters

| # | Chapter | Core Idea | Difficulty |
|---|---------|-----------|------------|
| 01 | [Kafka Architecture & Fundamentals](./01-Kafka-Architecture-and-Fundamentals.md) | Partitions, offsets, replicas, ISR, consumer groups, retention, compaction, KRaft | ⭐⭐⭐ |
| 02 | [Producers, Consumers & Spring Kafka](./02-Producers-Consumers-and-Spring-Kafka.md) | acks, idempotence, batching, ack modes, error handling, DLT, `@RetryableTopic` | ⭐⭐⭐⭐ |
| 03 | [RabbitMQ & Message Queue Patterns](./03-RabbitMQ-and-Message-Queue-Patterns.md) | Exchanges, bindings, prefetch, TTL/DLX retry ladders, quorum queues, when *not* Kafka | ⭐⭐⭐ |
| 04 | [Reliable Messaging & Event-Driven Architecture](./04-Reliable-Messaging-and-Event-Driven-Architecture.md) | Delivery guarantees, idempotency, schema evolution, event design, replay, DLQ ops | ⭐⭐⭐⭐⭐ |

---

## 🧠 The One Mental Model That Covers Everything

```text
              LOG                                  QUEUE
   ┌─────────────────────────────┐      ┌─────────────────────────────┐
   │  KEEP the data              │      │  DELETE on ack              │
   │  consumer owns an OFFSET    │      │  broker owns the message    │
   │  replay = move a number     │      │  replay = impossible        │
   │  order per PARTITION        │      │  order per QUEUE            │
   │  fan-out is free (groups)   │      │  fan-out = N queues         │
   └─────────────────────────────┘      └─────────────────────────────┘
             KAFKA                              RABBITMQ / SQS

   Pick LEFT  when you need history, replay, throughput, streams.
   Pick RIGHT when you need routing, TTL/delay/priority, task distribution.
```

**And the rule that applies to both:**

```text
   delivery  =  AT-LEAST-ONCE   (always)
   effect    =  IDEMPOTENT      (your job)
   result    =  effectively-once
```

---

## 📊 The Design Decision Matrix

| Decision | Default | Alternative | Trade-off |
|---|---|---|---|
| **Broker** | Kafka | RabbitMQ / SQS+SN SNS | Replay & throughput vs routing & per-message features |
| **Message key** | aggregate id | null (max throughput) | Ordering vs parallel spread |
| **Partition count** | enough for peak consumers | minimal, grow later | Parallelism vs key remapping |
| **Durability** | `acks=all`, RF=3, minISR=2 | `acks=1` | Safety vs latency |
| **Delivery** | at-least-once | at-most-once | Duplicates vs loss |
| **Consumer ack** | manual, after commit | batch (default) | Correctness vs throughput |
| **Retries** | bounded + DLT | infinite requeue | Recover vs poison loop |
| **Long retry delays** | `@RetryableTopic` / delay queue | in-memory sleep | Non-blocking vs ordering |
| **Schema** | registry + `BACKWARD` | JSON + tolerant reader | Safety vs tooling cost |
| **Payload** | fat enough to avoid callbacks | thin + API call | Coupling vs bytes |
| **Atomic publish** | outbox | direct send | Correctness vs simplicity |

---

## ⚠️ The Traps (the ones that cost people interviews)

| Trap | Wrong answer | Right answer |
|---|---|---|
| "Kafka orders messages" | "Yes, it's a queue" | **Per partition only** — key determines partition |
| "Kafka is exactly-once" | "Yes, with transactions" | Effectively-once **within Kafka**; DB writes need idempotency/outbox |
| "More consumers = more throughput" | "Yes, add replicas" | Capped by **partition count** per group |
| "RabbitMQ can replay" | "Yes, it stores messages" | Messages are **deleted on ack**; no replay |
| "Just check Redis before processing" | "That's the dedupe" | Dedupe must be **in the same transaction** as the effect |
| "Retry until it works" | "Reliability" | Poison messages block the partition/queue; bound + DLT |
| "Delete the old field" | "It's unused" | **Two-phase**: consumers tolerate absence first |
| "Committed offset means processed" | "Yes" | Commit **after** the side effect, never before |
| "Broker restart is fine, it's durable" | "Yes" | Only if queue/exchange durable **and** message persistent |
| "DLQ is where problems go away" | "Yes, it's safe" | Unread DLQ = silent data loss |
| "Ordering is preserved through retries" | "Yes" | Retries reorder events; need `aggregateVersion` guard |
| "`nack(requeue=true)` handles failures" | "Yes" | Immediate redelivery → infinite loop + starvation |
| "Kafka = RabbitMQ with more throughput" | "Basically" | Different model: log vs broker; different guarantees |

---

## 🎤 The 20 Questions to Expect

| # | Question | Where |
|---|---|---|
| 1 | Explain Kafka's architecture | [Ch 01](./01-Kafka-Architecture-and-Fundamentals.md) |
| 2 | How does Kafka guarantee ordering? | [Ch 01](./01-Kafka-Architecture-and-Fundamentals.md) |
| 3 | What is a consumer group and how does rebalancing work? | [Ch 01](./01-Kafka-Architecture-and-Fundamentals.md) |
| 4 | What is ISR and why does it matter? | [Ch 01](./01-Kafka-Architecture-and-Fundamentals.md) |
| 5 | What do `acks` and `min.insync.replicas` do together? | [Ch 02](./02-Producers-Consumers-and-Spring-Kafka.md) |
| 6 | What happens when a consumer crashes mid-batch? | [Ch 02](./02-Producers-Consumers-and-Spring-Kafka.md) |
| 7 | How do you handle a poison message? | [Ch 02](./02-Producers-Consumers-and-Spring-Kafka.md) |
| 8 | How do you scale consumers? | [Ch 02](./02-Producers-Consumers-and-Spring-Kafka.md) |
| 9 | Kafka vs RabbitMQ — how do you choose? | [Ch 03](./03-RabbitMQ-and-Message-Queue-Patterns.md) |
| 10 | What is prefetch? | [Ch 03](./03-RabbitMQ-and-Message-Queue-Patterns.md) |
| 11 | How do you implement delayed messages? | [Ch 03](./03-RabbitMQ-and-Message-Queue-Patterns.md) |
| 12 | How do you guarantee no message is lost? | [Ch 04](./04-Reliable-Messaging-and-Event-Driven-Architecture.md) |
| 13 | How do you guarantee no duplicate processing? | [Ch 04](./04-Reliable-Messaging-and-Event-Driven-Architecture.md) |
| 14 | What is exactly-once, really? | [Ch 04](./04-Reliable-Messaging-and-Event-Driven-Architecture.md) |
| 15 | How do you evolve an event schema? | [Ch 04](./04-Reliable-Messaging-and-Event-Driven-Architecture.md) |
| 16 | How do you handle out-of-order events? | [Ch 04](./04-Reliable-Messaging-and-Event-Driven-Architecture.md) |
| 17 | What's in your DLQ process? | [Ch 04](./04-Reliable-Messaging-and-Event-Driven-Architecture.md) |
| 18 | How do you replay events safely? | [Ch 04](./04-Reliable-Messaging-and-Event-Driven-Architecture.md) |
| 19 | Event vs command? | [Ch 04](./04-Reliable-Messaging-and-Event-Driven-Architecture.md) |
| 20 | How do you make the DB write and the publish atomic? | [Topic 08 ch 06](../08-Microservices-with-Spring-Boot/06-Distributed-Data-Management-and-Saga-Patterns.md) |

---

## 🛠️ The Code You'll Actually Write

| Class | What it does | Where |
|---|---|---|
| `NewTopic` bean | Declares partitions, replicas, minISR, retention as code | [Ch 01](./01-Kafka-Architecture-and-Fundamentals.md) |
| `KafkaTemplate` | Producer with acks/idempotence headers | [Ch 02](./02-Producers-Consumers-and-Spring-Kafka.md) |
| `@KafkaListener` + `ConcurrentKafkaListenerContainerFactory` | Consumers, concurrency, ack mode | [Ch 02](./02-Producers-Consumers-and-Spring-Kafka.md) |
| `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` | Retry classification, DLT routing | [Ch 02](./02-Producers-Consumers-and-Spring-Kafka.md) |
| `@RetryableTopic` / `@DltHandler` | Non-blocking retries via retry topics | [Ch 02](./02-Producers-Consumers-and-Spring-Kafka.md) |
| `ErrorHandlingDeserializer` | Poison-pill-safe deserialization | [Ch 02](./02-Producers-Consumers-and-Spring-Kafka.md) |
| `TopicExchange` / `QueueBuilder` / `Binding` | RabbitMQ topology as code, DLX wiring | [Ch 03](./03-RabbitMQ-and-Message-Queue-Patterns.md) |
| `RabbitTemplate` with confirms/returns | Guaranteed publish | [Ch 03](./03-RabbitMQ-and-Message-Queue-Patterns.md) |
| `RetryOperationsInterceptor` + `RepublishMessageRecoverer` | Declarative retry → parking lot | [Ch 03](./03-RabbitMQ-and-Message-Queue-Patterns.md) |
| `processed_events` dedupe | Idempotent consumer | [Ch 04](./04-Reliable-Messaging-and-Event-Driven-Architecture.md) |
| Event envelope + `aggregateVersion` | Identity, tracing, stale-event guard | [Ch 04](./04-Reliable-Messaging-and-Event-Driven-Architecture.md) |
| DLT replay admin endpoint | Safe reprocessing with dedupe | [Ch 04](./04-Reliable-Messaging-and-Event-Driven-Architecture.md) |

---

## 🔗 The Spring Boot 3 Setup (the minimum)

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS}
    producer:
      acks: all
      enable-idempotence: true
      compression-type: lz4
      properties:
        linger.ms: 10
    consumer:
      enable-auto-commit: false        # the single most important line
      auto-offset-reset: earliest
      max-poll-records: 100
      properties:
        partition.assignment.strategy: >
          org.apache.kafka.clients.consumer.CooperativeStickyAssignor
    listener:
      ack-mode: manual_immediate
      concurrency: 4
```

```java
@Configuration
class KafkaConfig {
    @Bean
    NewTopic orders() {
        return TopicBuilder.name("orders.v1")
            .partitions(12).replicas(3)
            .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2")
            .build();
    }

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> t) {
        return new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(t), new ExponentialBackOffWithMaxRetries(4));
    }
}
```

---

## 📐 The Reference Architecture

```text
   ┌─────────────┐   outbox    ┌─────────┐   ┌──────────────┐   ┌──────────────┐
   │order-service├────────────►│  KAFKA  ├──►│inventory-svc │   │payment-svc   │
   │  own DB     │  relay      │orders.v1│   │ dedupe + tx  │   │ dedupe + tx  │
   └─────────────┘             │ 12 parts│   └──────────────┘   └──────────────┘
                               │ RF=3    │
                               │ minISR=2│   ┌──────────────┐
                               │         ├──►│search-indexer│  (own group)
                               │         │   └──────────────┘
                               │         │
                               │         ├──►│  orders.DLT  │──► alert + replay tool
                               └─────────┘   └──────────────┘
                                    ▲
                     schema registry│ (BACKWARD, CI-gated)
                                    │
                     each consumer: idempotent · versioned · lag-monitored
```

**Rules the diagram encodes:**
- Producers publish via **outbox**, never from the request thread with a DB write.
- Every consumer is in **its own group** and is **idempotent**.
- Failures end up in a **DLT with an owner**, not in a topic nobody reads.
- Contracts live in a **registry** and breaking changes fail CI.

---

## ✅ The Self-Check

- [ ] I can explain partition, offset, replica, ISR and consumer group without notes.
- [ ] I can state Kafka's exact ordering guarantee.
- [ ] I know why `acks=all` needs `min.insync.replicas`.
- [ ] I can pick an ack mode and defend it.
- [ ] I can compute whether a handler will trip `max.poll.interval.ms`.
- [ ] I can describe the full error path down to the DLT.
- [ ] I can compare Kafka and RabbitMQ by workload, not by hype.
- [ ] I can explain prefetch and the TTL head-of-line trap.
- [ ] I can state the seven reliability boxes.
- [ ] I can make a consumer idempotent correctly (same transaction).
- [ ] I can evolve a schema without breaking consumers.
- [ ] I can guard against out-of-order events.
- [ ] I can run a replay without duplicating side effects.
- [ ] I never claim exactly-once end to end.

---

## 🧪 The Coding Drill

- [ ] Produce 10k keyed records; assert per-key order and observe partition distribution.
- [ ] Kill the consumer between DB commit and ack; prove the dedupe table prevents a double
      effect.
- [ ] Send malformed JSON; prove it lands in the DLT instead of looping.
- [ ] Force a rebalance with a 30s handler and watch duplicates appear; then fix it.
- [ ] Build the RabbitMQ retry ladder (1s/10s/60s) and measure the delays.
- [ ] Store a `processed_events` table and re-run a batch twice; assert identical state.
- [ ] Publish events out of order and prove the version guard rejects stale ones.
- [ ] Break a schema in CI and confirm the registry rejects it.

---

**Related folders:** ← [08 · Microservices](../08-Microservices-with-Spring-Boot/README.md) · → [10 · Caching](../10-Caching/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
