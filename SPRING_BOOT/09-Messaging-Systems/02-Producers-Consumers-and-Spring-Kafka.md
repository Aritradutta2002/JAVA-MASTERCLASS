# 02 · Producers, Consumers & Spring Kafka

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★★ ("How do you ensure a message is processed exactly once?")
> **Prerequisite:** [01 · Kafka Architecture & Fundamentals](./01-Kafka-Architecture-and-Fundamentals.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Three Knobs](#2-mental-model--the-three-knobs)
- [3. Deep Dive — Producer Configuration](#3-deep-dive--producer-configuration)
- [4. Deep Dive — Consumer Configuration](#4-deep-dive--consumer-configuration)
- [5. Spring Kafka Internals](#5-spring-kafka-internals)
- [6. Step-by-Step — Error Handling Flow](#6-step-by-step--error-handling-flow)
- [7. Architecture Diagrams](#7-architecture-diagrams)
- [8. Code Examples](#8-code-examples)
- [9. Common Mistakes & Gotchas](#9-common-mistakes--gotchas)
- [10. Performance Tuning](#10-performance-tuning)
- [11. Security](#11-security)
- [12. Best Practices Checklist](#12-best-practices-checklist)
- [13. Exercises & Challenges](#13-exercises--challenges)
- [14. Interview Questions & Model Answers](#14-interview-questions--model-answers)
- [15. Self-Check & Revision Card](#15-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> Three producer knobs decide everything: **`acks`** (durability), **`linger.ms` + `batch.size`**
> (throughput vs latency), **`enable.idempotence`** (retry duplication).
>
> Three consumer knobs decide correctness: **`enable.auto.commit=false`** with an ack mode
> that commits *after* processing, **`max.poll.records` vs `max.poll.interval.ms`** (rebalance
> safety), and **`auto.offset.reset`** (only used when no offset exists).
>
> Spring Kafka wraps this with `KafkaTemplate`, `@KafkaListener`, `ConcurrentKafkaListenerContainerFactory`,
> and `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`. Use them — but know that
> `AckMode.BATCH` (the default) commits offsets after the whole poll batch is processed, so
> a failure halfway through means the earlier records **also** get redelivered. That's why
> consumers must be idempotent, not why you should switch to `MANUAL_IMMEDIATE` blindly
> (it's slower).
>
> **`KafkaTemplate.send` returns a `CompletableFuture`.** Ignoring it means you never learn
> about failures. And **never** call `.get()` on a request thread without a timeout.

---

## 1. What & Why

Writing `kafkaTemplate.send(topic, value)` and `@KafkaListener` is five lines. Making it
**durable, ordered, retry-safe and observable** is where the interview is won. The gap:

| Naive setup | What breaks in production |
|---|---|
| default producer, no acks config | Silent loss on leader failover |
| `send()` without a callback | Failures invisible forever |
| default consumer, auto-commit | Message lost on crash before processing |
| exception in listener → retry forever | Partition blocked, lag explodes |
| retry loop without backoff | Thundering herd on the downstream DB |
| one consumer, 12 partitions | 1/12 of the throughput |
| 8 consumers, 3 partitions | 5 idle consumers, wasted money |
| `max.poll.records=500` with slow SQL | Rebalance storm, duplicate storms |

This chapter is the configuration and error-handling surface that turns the five lines into
something you'd run on Black Friday.

---

## 2. Mental Model — The Three Knobs

```
                          PRODUCER
        ┌─────────────────────────────────────────────┐
        │  DURABILITY     acks=all + idempotence      │
        │  THROUGHPUT     linger.ms, batch.size,      │
        │                 compression                 │
        │  LATENCY        linger.ms = 0 (no batching) │
        └─────────────────────────────────────────────┘
                            ▲
                            │ cannot have all three at once:
                            │ pick two, or accept the trade
                            ▼
                          CONSUMER
        ┌─────────────────────────────────────────────┐
        │  CORRECTNESS    acks after processing,      │
        │                 idempotent handler          │
        │  THROUGHPUT     concurrency, max.poll.records│
        │  STABILITY      poll interval vs work time  │
        └─────────────────────────────────────────────┘
```

**The single most useful sentence:**

> *Batching is the only free lunch Kafka gives you; everything else is a trade between
> durability, latency and throughput.*

---

## 3. Deep Dive — Producer Configuration

### 3.1 `acks`

| Value | Guarantee | Use |
|---|---|---|
| `0` | none — fire and forget | metrics, logs where loss is acceptable |
| `1` | leader's local log | never for business state |
| `all` / `-1` | leader + all ISR | default for anything that matters |

With `acks=all` but `min.insync.replicas=1`, the broker still accepts writes when only the
leader is in sync — you get acknowledgement without redundancy. **Set both.**

### 3.2 Idempotence

`enable.idempotence=true` (on by default from Kafka 3.0 with `acks=all`) makes the broker
dedupe retries using a producer id + per-partition sequence. It eliminates the "retry after
timeout but the first attempt actually succeeded" duplicate.

Constraints it imposes: `acks=all`, `retries>0`, `max.in.flight.requests.per.connection<=5`.
Transactions (`transactional.id`) build on top for atomic multi-topic writes.

### 3.3 Batching and latency

- `batch.size` — max bytes per partition batch (default 16 KB; 32–64 KB is typical).
- `linger.ms` — how long to wait to fill a batch (default 0; 5–20 ms is the usual sweet spot).
- `compression.type` — `lz4` (best speed), `zstd` (best ratio). Compression happens per
  batch, so batching + compression compound.

Latency budget: a record waits up to `linger.ms` before the send even starts. If your
endpoint SLA is 100 ms and `linger.ms=20`, you've spent a fifth of it batching. For
fire-and-forget events that's fine; for request-path latency measurement it isn't.

### 3.4 Ordering with retries

Without idempotence, `max.in.flight.requests.per.connection > 1` + retries can reorder
batches. With idempotence, up to 5 in-flight requests preserve order per partition. If you
need absolute ordering and can't use idempotence, set in-flight to 1 (slower).

### 3.5 Keys

```java
// keyed: ordered per aggregate, lands on one partition
kafka.send("orders.v1", order.getId().toString(), event);

// custom partitioner when the default hashing gives you hot partitions
@Bean
ProducerFactory<String, OrderEvent> pf(KafkaProperties props) {
    var cfg = props.buildProducerProperties(null);
    return new DefaultKafkaProducerFactory<>(cfg,
        new StringSerializer(),
        new JsonSerializer<>(),
        // supplier of extra config per instance if needed
        null);
}
```

For hot keys, append a shard suffix (`tenantId + ":" + (seq % 8)`) and make the consumer
tolerate out-of-order across shards.

### 3.6 Headers as contract

Put `eventId`, `eventType`, `schemaVersion`, `traceparent` in headers, not in the payload.
Headers are cheap to read (routing, dedupe, tracing) and survive serialization changes.

---

## 4. Deep Dive — Consumer Configuration

### 4.1 Ack modes in Spring Kafka

| `AckMode` | When offset is committed | Notes |
|---|---|---|
| `RECORD` | after each record is processed | simple, a bit chatty |
| `BATCH` (default) | after the whole poll batch | a mid-batch failure redelivers the whole batch |
| `TIME` | every `ackTime` ms | odd semantics, avoid |
| `COUNT` | every N records | tuning knob for throughput |
| `COUNT_TIME` | whichever first | — |
| `MANUAL` | you call `acknowledge()`, batch queued | commit at batch end |
| `MANUAL_IMMEDIATE` | you call `acknowledge()` → commit now | most control, slowest |

`enable.auto.commit` must be `false` for any of these to be meaningful.

**Correctness rule:** commit only after the side effect is durable. If your handler writes
to Postgres in `@Transactional`, ack after the transaction commits — which is what
`MANUAL_IMMEDIATE` inside a `@Transactional` listener gives you when ack is the last line.

### 4.2 Poll loop constraints

```
     poll() ──► records ──► process (must finish in < max.poll.interval.ms)
       ▲                                          │
       └──────────────────────────────────────────┘
                heartbeat thread runs separately
                (session.timeout.ms governs liveness)
```

- `max.poll.records` — how many records a poll returns. Bigger = better throughput, longer processing time per poll.
- `max.poll.interval.ms` — the hard deadline. Exceeded → consumer evicted → rebalance → duplicates.
- `fetch.min.bytes` / `fetch.max.wait.ms` — broker-side batching for the fetch.

**The math to quote in an interview:** if p99 handling is 120 ms/record and
`max.poll.records=500`, one poll can take 60 s. With `max.poll.interval.ms=300000` (5 min)
you're safe; with the older default of 30 s you'd rebalance constantly. Either lower the
batch or raise the interval.

### 4.3 `auto.offset.reset`

Only consulted when the group has **no committed offset** (brand-new group or expired
offsets after `offsets.retention.minutes`). `earliest` for backfills and new services that
must see history; `latest` for real-time-only consumers. A new group with `latest` silently
skips everything already in the topic — the number one "my consumer gets nothing" cause.

### 4.4 Rebalance listeners

```java
@Bean
ConsumerAwareRebalanceListener rebalanceLogger() {
    return new ConsumerAwareRebalanceListener() {
        public void onPartitionsRevokedBeforeCommit(Consumer<?, ?> c,
                Collection<TopicPartition> parts) {
            log.info("REVOKING {}", parts);   // flush here
        }
        public void onPartitionsAssigned(Consumer<?, ?> c,
                Collection<TopicPartition> parts) {
            log.info("ASSIGNED {}", parts);
        }
    };
}
```

Use revocation to flush in-flight state. If your consumer keeps per-partition caches, clear
them on assignment or you'll serve stale data after a rebalance.

### 4.5 Deserialization and error handling

- `ErrorHandlingDeserializer` wraps the real deserializer so a poison record becomes a
  `DeserializationException` you can route to a DLT instead of an infinite loop.
- `JsonDeserializer` with `spring.json.trusted.packages` and **no** type headers
  (`use-type-headers=false`) is the safer production default: trusting producer-supplied
  class names is a deserialization-gadget risk.

---

## 5. Spring Kafka Internals

### 5.1 The container

`KafkaMessageListenerContainer` owns one consumer thread (or several per `concurrency`).
`ConcurrentMessageListenerContainer` spawns `concurrency` child containers, each with its
own consumer, all in the same group — so `concurrency` is your in-process parallelism, and
it only helps up to the partition count.

```
ConcurrentMessageListenerContainer (concurrency=4)
 ├── KafkaMessageListenerContainer #0 → consumer, partitions {0,1}
 ├── #1 → {2,3}
 ├── #2 → {4,5}
 └── #3 → {6,7}
```

### 5.2 `DefaultErrorHandler`

Behaviour for the non-batch listener:

1. Exception thrown → `BackOff` decides whether to retry.
2. Retries happen **in memory** (the poll loop is paused for that partition).
3. After retries exhausted → `DeadLetterPublishingRecoverer` publishes to `<topic>.DLT`
   with headers `kafka_dlt-exception-*`, then the record is committed (skipped).
4. `addNotRetryableExceptions(...)` short-circuits known-poison types.

Important detail: in-memory retries **block the partition**. A retry with a 10 s backoff and
5 attempts blocks that partition for ~50 s per record — which will blow
`max.poll.interval.ms` in a multi-partition container. For long backoffs, prefer
**`@RetryableTopic`** (non-blocking retries via retry topics) or push the work to a
separate retry queue.

### 5.3 `@RetryableTopic`

```java
@RetryableTopic(
    attempts = "4",
    backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 15000),
    autoCreateTopics = "false",
    topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    exclude = {IllegalArgumentException.class, DeserializationException.class})
@KafkaListener(topics = "orders.v1", groupId = "inventory-svc")
public void on(OrderEvent e) { ... }

@DltHandler
public void dlt(OrderEvent e, @Header(KafkaHeaders.EXCEPTION_MESSAGE) String err) {
    log.error("dead letter {}", e, err);
}
```

This creates `orders.v1-retry-0`, `-retry-1`, … with their own delays. The main partition is
not blocked; the retry topics act as a delayed queue. Cost: more topics, and ordering is
relaxed for retried records (they land later than subsequent events). Note that trade
explicitly — it's a great senior-level point.

### 5.4 Batch listeners

`@KafkaListener(batch = "true")` receives `List<ConsumerRecord<...>>` (or `List<OrderEvent>`
with `ListDeserializer`). Better throughput for non-blocking writes; ack commits the whole
batch. Use batch upserts (`JdbcTemplate.batchUpdate`) rather than per-record saves.

### 5.5 Transactions

```java
@Bean
KafkaTransactionManager<String, String> ktm(ProducerFactory<String, String> pf) {
    return new KafkaTransactionManager<>(pf);
}

@KafkaListener(topics = "in")
@Transactional("ktm")   // consume → transform → produce atomically (inside Kafka)
public void forward(String v) { kafka.send("out", v.toUpperCase()); }
```

Remember: this is atomic **within Kafka only**. Chaining a `KafkaTransactionManager` with a
`DataSourceTransactionManager` is **not** real XA and does not give you cross-system
atomicity. Use the outbox instead.

---

## 6. Step-by-Step — Error Handling Flow

```
record arrives
   │
   ▼
deserialize ──fail──► DeserializationException ──► DLT (not retryable)
   │ok
   ▼
listener invoked
   │
   ├─ throws RetryableException ──► backoff retry N times (in memory / retry topics)
   │        │ exhausted
   │        ▼
   │   DeadLetterPublishingRecoverer ──► orders.v1.DLT (keeps original headers)
   │
   ├─ throws IllegalArgumentException (not retryable) ──► DLT immediately
   │
   └─ success ──► acknowledge ──► offset commit
```

---

## 7. Architecture Diagrams

```
   PRODUCER SIDE                              CONSUMER SIDE
 ┌──────────────────────┐                  ┌───────────────────────────────┐
 │ KafkaTemplate        │                  │ ConcurrentMessageListener     │
 │  ├ accumulate batch  │                  │  Container (concurrency=4)    │
 │  ├ compress          │                  │   ├ consumer 0 → parts 0,1    │
 │  └ send(acks=all)    │                  │   ├ consumer 1 → parts 2,3    │
 │                      │                  │   ├ consumer 2 → parts 4,5    │
 │ callback:            │                  │   └ consumer 3 → parts 6,7    │
 │  success → metrics   │                  │                               │
 │  failure → outbox    │                  │ ErrorHandler: retry → DLT     │
 │            retry     │                  │ Ack: after handler returns    │
 └──────────────────────┘                  └───────────────────────────────┘
```

---

## 8. Code Examples

### 8.1 Full producer configuration

```java
@Configuration
class KafkaProducerConfig {
    @Bean
    ProducerFactory<String, Object> producerFactory(KafkaProperties props) {
        Map<String, Object> cfg = props.buildProducerProperties(null);
        cfg.put(ProducerConfig.ACKS_CONFIG, "all");
        cfg.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        cfg.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        cfg.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        cfg.put(ProducerConfig.BATCH_SIZE_CONFIG, 64 * 1024);
        cfg.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);
        cfg.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        var pf = new DefaultKafkaProducerFactory<String, Object>(cfg);
        pf.setKeySerializer(new StringSerializer());
        pf.setValueSerializer(new JsonSerializer<>());
        return pf;
    }

    @Bean
    KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        var t = new KafkaTemplate<>(pf);
        t.setObservationEnabled(true);   // Micrometer tracing on produce
        return t;
    }
}
```

### 8.2 Publishing with headers and a real callback

```java
@Service
public class EventPublisher {
    private final KafkaTemplate<String, Object> template;
    private final MeterRegistry meters;

    public void publish(String topic, String key, Object payload, String type) {
        var record = new ProducerRecord<String, Object>(topic, null, key, payload);
        record.headers()
              .add("eventId", UUID.randomUUID().toString().getBytes(UTF_8))
              .add("eventType", type.getBytes(UTF_8))
              .add("schemaVersion", "1".getBytes(UTF_8));

        template.send(record).whenComplete((res, ex) -> {
            if (ex != null) {
                meters.counter("kafka.publish.failures", "topic", topic).increment();
                // do NOT swallow: route to outbox retry / DLQ table
                throw new IllegalStateException("publish failed: " + topic, ex);
            }
            meters.timer("kafka.publish.latency", "topic", topic)
                  .record(Duration.ofMillis(res.getRecordMetadata().timestamp() == -1
                      ? 0 : System.currentTimeMillis() - res.getRecordMetadata().timestamp()));
        });
    }
}
```

### 8.3 Consumer with manual ack, dedupe, and DLT

```java
@Configuration
class KafkaConsumerConfig {

    @Bean
    ConsumerFactory<String, Object> consumerFactory(KafkaProperties props) {
        Map<String, Object> cfg = props.buildConsumerProperties(null);
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        cfg.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000);
        cfg.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
                "org.apache.kafka.clients.consumer.CooperativeStickyAssignor");
        // poison-safe deserialization
        cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        cfg.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        cfg.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        cfg.put(JsonDeserializer.TRUSTED_PACKAGES, "com.acme.events");
        cfg.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(cfg);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> cf, KafkaTemplate<String, Object> template) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(cf);
        factory.setConcurrency(4);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setSyncCommits(true);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setCommonErrorHandler(errorHandler(template));
        return factory;
    }

    DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
            (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", rec.partition()));

        var backOff = new ExponentialBackOffWithMaxRetries(4);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(15_000);

        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(
            DeserializationException.class,
            IllegalArgumentException.class,
            org.springframework.messaging.converter.MessageConversionException.class);
        handler.setRetryListeners((rec, ex, attempt) ->
            log.warn("retry attempt {} for {}-{}@{}", attempt, rec.topic(),
                     rec.partition(), rec.offset(), ex));
        return handler;
    }
}
```

```java
@Component
public class InventoryListener {
    private final JdbcTemplate jdbc;
    private final InventoryService inventory;

    @KafkaListener(topics = "orders.v1", groupId = "inventory-svc")
    @Transactional
    public void on(ConsumerRecord<String, OrderCreated> rec, Acknowledgment ack) {
        UUID eventId = UUID.fromString(
            new String(rec.headers().lastHeader("eventId").value(), UTF_8));

        int inserted = jdbc.update("""
            INSERT INTO processed_events (consumer, event_id) VALUES ('inventory', ?)
            ON CONFLICT DO NOTHING
            """, eventId);
        if (inserted == 0) { ack.acknowledge(); return; }

        inventory.reserve(rec.value().orderId(), rec.value().items());
        ack.acknowledge();     // last statement: DB committed first
    }
}
```

### 8.4 Batch listener with batch upsert

```java
@KafkaListener(topics = "clicks.v1", groupId = "analytics", batch = "true")
@Transactional
public void onBatch(List<ConsumerRecord<String, Click>> records, Acknowledgment ack) {
    jdbc.batchUpdate(
        "INSERT INTO clicks (id, user_id, url, ts) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
        records.stream().map(r -> new Object[]{
            r.value().id(), r.value().userId(), r.value().url(), r.value().ts()
        }).toList());
    ack.acknowledge();
}
```

### 8.5 Graceful shutdown

```yaml
spring:
  kafka:
    listener:
      immediate-stop: false          # let the current batch finish
    consumer:
      properties:
        group.instance.id: ${HOSTNAME}   # static membership → fewer rebalances
```

```java
@Bean
ApplicationListener<ContextClosedEvent> drain(KafkaListenerEndpointRegistry registry) {
    return e -> registry.getListenerContainers().forEach(c -> {
        log.info("stopping {} — draining", c.getListenerId());
        c.stop();     // waits for the in-flight record to finish, then commits
    });
}
```

---

## 9. Common Mistakes & Gotchas

| Mistake | Consequence | Fix |
|---|---|---|
| Ignoring `send()`'s future | Failures silent | Attach callback; route failures to outbox |
| `.get()` on request thread | Thread pinned by broker latency | `whenComplete` or a separate publisher thread |
| Retry in listener with in-memory backoff of minutes | Partition blocked, rebalance | `@RetryableTopic` / delayed retry topic |
| Same default group id across services | They steal each other's partitions | One group id per consumer service |
| `concurrency` > partitions | Idle threads | Size to partitions/instances |
| `max.poll.records` too high + slow handler | Rebalance storm | Lower records or offload work |
| Trusting type headers from producers | Deserialization gadget risk | `use-type-headers=false` + trusted packages |
| Deserialization failure not wrapped | Infinite retry on a poison record | `ErrorHandlingDeserializer` + DLT |
| Committing the offset inside the try, before the DB commit | Message lost on rollback | Ack after commit |
| DLT with no retry/replay story | Data rot in a topic nobody reads | DLT monitor + replay tool + dedupe |
| `@KafkaListener` on a class, using `this.method()` | No proxying → no transaction | Self-inject or split the service |
| Rebalance clearing in-flight state incorrectly | Duplicate or lost side effects | Flush on revoke, rebuild on assign |

---

## 10. Performance Tuning

**Producer**
- `linger.ms=5–20`, `batch.size=32–64 KB`, `compression=lz4` → 3–10x throughput vs defaults.
- `max.in.flight=5` (with idempotence) is safe; more parallelism, order preserved.
- Reuse `KafkaTemplate` (it wraps a thread-safe producer). Never create a producer per request.

**Consumer**
- `concurrency = min(partitions/instances, cpu-bound threads)`.
- Batch mode + `batchUpdate` for DB-heavy consumers.
- `fetch.min.bytes=1MB` and `fetch.max.wait.ms=500` for high-throughput backfills.
- Avoid per-record HTTP calls; batch remote calls or buffer locally.
- Keep the handler fast (< 10% of `max.poll.interval.ms`), or run a lower
  `max.poll.records`.

**Operational**
- Watch `records-lag-max`, `fetch-latency-avg`, `commit-latency-avg`, rebalance rate.
- Alert on rebalance frequency; frequent rebalances mean the handler or the network is
  sick, and they cost duplicates.
- Prefer static membership during rolling deploys to avoid N rebalances.

---

## 11. Security

- SASL/SCRAM or mTLS for brokers; never anonymous PLAINTEXT outside a laptop.
- Distinct credentials per service; ACLs limited to the topics it actually needs.
- Do not log payloads — events contain PII and the logs outlive the retention policy.
- Validate the event **schema and semantics** in the consumer; a malicious producer inside
  the cluster is still a threat.
- DLT access is privileged: dead letters are the rawest data you hold.
- Mask secrets in `ProducerRecord` headers; `traceparent` is fine, an API key is not.

---

## 12. Best Practices Checklist

- [ ] `acks=all`, `enable.idempotence=true`, compression on.
- [ ] Every `send()` has a callback; failures are visible in metrics.
- [ ] `enable.auto.commit=false`; ack after the durable side effect.
- [ ] `ErrorHandlingDeserializer` in front of every deserializer.
- [ ] Not-retryable exceptions configured; DLT publishing verified by a test.
- [ ] Retries bounded and, for long delays, non-blocking (retry topics).
- [ ] `concurrency` sized to partitions; documented.
- [ ] Static membership for rolling restarts.
- [ ] Consumer idempotent (dedupe in the same transaction as the effect).
- [ ] DLT monitored with alerting + a replay procedure.
- [ ] `max.poll.records` × p99 handler time < `max.poll.interval.ms` / 2.
- [ ] Graceful shutdown drains in-flight records.

---

## 13. Exercises & Challenges

1. **Fail a publish** — stop the broker, publish, verify the callback records the failure
   and the outbox retries after restart.
2. **Poison pill** — send malformed JSON; prove it lands in the DLT instead of looping.
3. **Retry verification** — make the listener throw a retryable exception twice then
   succeed; count the invocations.
4. **Rebalance storm** — set `max.poll.records=500` and sleep 1s/record; observe rebalances;
   fix by lowering records.
5. **Non-blocking retry** — convert the previous test to `@RetryableTopic` and show the
   main partition keeps flowing during the delay window.
6. **Batch vs single** — measure throughput for 10k records with `batch=true` + `batchUpdate`
   vs per-record saves.
7. **Static membership** — rolling restart with and without `group.instance.id`; compare
   rebalance counts.
8. **Challenge** — write a `KafkaTestUtils`-based test asserting the DLT receives exactly
   one record with the original headers.

---

## 14. Interview Questions & Model Answers

**Q1. What does `acks=all` actually wait for?**
> For the leader plus all in-sync replicas to append the record. It is bounded by
> `min.insync.replicas`: if the ISR shrinks below that, the broker rejects the write rather
> than accepting it without redundancy. That's the durability contract, and it's why you set
> both.

**Q2. Why is the default `AckMode.BATCH` risky?**
> Offsets are committed after the entire poll batch is handled. If record 40 of 100 throws
> and you don't stop, records 1–39 are already committed but 40–100 get redelivered — or,
> worse, if you commit after a partially failed batch you lose the unprocessed tail. The
> practical rule is: be idempotent, and commit only after the work is durable.

**Q3. How do you avoid blocking a partition on retries?**
> Don't sleep in the listener. Use `@RetryableTopic` so retries go to delay topics and the
> main partition keeps consuming, or push the record to a delayed retry queue. In-memory
> `DefaultErrorHandler` backoff blocks the partition and can trip
> `max.poll.interval.ms`.

**Q4. How do you scale a consumer?**
> Partitions are the unit. Add consumers up to the partition count, then increase partitions
> (which changes key mapping and therefore ordering). Also raise `concurrency` for
> in-process parallelism, and fix the handler if it's the bottleneck.

**Q5. What causes rebalances, and how do you reduce them?**
> Member join/leave/crash, `max.poll.interval.ms` exceeded, partition count changes. Reduce
> by: cooperative sticky assignor, static membership ids, right-sizing `max.poll.records`,
> keeping handlers fast, and avoiding deploy churn.

**Q6. Where does Spring commit the offset in `MANUAL_IMMEDIATE`?**
> At the `acknowledge()` call, synchronously with the broker when `syncCommits=true`. So the
> call order matters: DB commit first, then `acknowledge()`.

**Q7. Is `ChainedKafkaTransactionManager` distributed transaction support?**
> No. It chains local transaction managers; it does not make the DB and Kafka writes atomic
> under failure. The outbox is the correct pattern for cross-system atomicity.

**Q8. How do you trace a message end-to-end?**
> `traceparent` header propagation. Spring Kafka has observation support —
> enable `observationEnabled` on the template and container; Micrometer creates producer and
> consumer spans linked to the parent, so the consumer span shows the producer's trace id.

---

## 15. Self-Check & Revision Card

- [ ] I can name the three producer knobs and their trade-offs.
- [ ] I can name the three correctness consumer settings.
- [ ] I can explain each `AckMode` and pick one with justification.
- [ ] I can compute whether my handler will trip `max.poll.interval.ms`.
- [ ] I can describe the error-handling flow down to the DLT.
- [ ] I know why `@RetryableTopic` exists and its ordering trade-off.
- [ ] I can explain why chained transaction managers are not XA.

```
┌──────────────────── SPRING KAFKA — 60s RECALL ───────────────────────────────┐
│ PRODUCER: acks=all + idempotence + linger 10ms + lz4. Always handle the      │
│ CompletableFuture.                                                           │
│                                                                              │
│ CONSUMER: auto-commit=false, ack AFTER the durable write, dedupe in the same │
│ transaction, ErrorHandlingDeserializer, bounded retries → DLT.               │
│                                                                              │
│ BATCH ack (default) commits the whole poll batch → idempotency is mandatory. │
│ MANUAL_IMMEDIATE = control, slower.                                          │
│                                                                              │
│ max.poll.records × p99 handler < max.poll.interval.ms (else rebalance storm).│
│                                                                              │
│ Long backoff ⇒ @RetryableTopic (non-blocking), not in-memory sleep.          │
│                                                                              │
│ Kafka transactions = atomic inside Kafka only. DB + Kafka = OUTBOX.          │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [01 · Kafka Architecture](./01-Kafka-Architecture-and-Fundamentals.md) · **Next:** [03 · RabbitMQ & Message Queue Patterns](./03-RabbitMQ-and-Message-Queue-Patterns.md)
