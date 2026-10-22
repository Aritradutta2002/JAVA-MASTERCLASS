# 03 · RabbitMQ & Message Queue Patterns

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★☆☆ ("Kafka or RabbitMQ — and why?")
> **Prerequisite:** [02 · Producers, Consumers & Spring Kafka](./02-Producers-Consumers-and-Spring-Kafka.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The AMQP Model](#2-mental-model--the-amqp-model)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
- [4. Internal Mechanism](#4-internal-mechanism)
- [5. Step-by-Step — A Message's Life](#5-step-by-step--a-messages-life)
- [6. Architecture Diagrams & Patterns](#6-architecture-diagrams--patterns)
- [7. Production Use Cases](#7-production-use-cases)
- [8. Code Examples — Spring AMQP](#8-code-examples--spring-amqp)
- [9. Common Mistakes & Gotchas](#9-common-mistakes--gotchas)
- [10. Performance](#10-performance)
- [11. Security](#11-security)
- [12. Best Practices Checklist](#12-best-practices-checklist)
- [13. Exercises & Challenges](#13-exercises--challenges)
- [14. Interview Questions & Model Answers](#14-interview-questions--model-answers)
- [15. Self-Check & Revision Card](#15-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> RabbitMQ is a **message broker**, not a log. Messages are **routed** from an **exchange**
> to **queues** by **bindings**; consumers compete on a queue; each message is **deleted on
> ack**. No replay.
>
> Routing types: `direct` (exact key), `topic` (`orders.*.eu`), `fanout` (broadcast),
> `headers`. The **default exchange** routes by queue name — convenient, and the reason
> people think there's no exchange at all.
>
> Delivery guarantees: **publisher confirms** (`ConfirmCallback`) prove the broker took it;
> **persistent messages + durable queues + `delivery_mode=2`** survive a broker restart;
> **manual ack** after processing gives at-least-once. Prefetch (`basic.qos`) is your flow
> control — the single most important consumer setting.
>
> The RabbitMQ-native superpowers Kafka lacks: **per-message TTL, delayed messages (via TTL +
> DLX or the delayed-exchange plugin), priorities, and a dead-letter exchange with a
> requeue loop.** That's why retry queues are idiomatic here.

---

## 1. What & Why

Kafka keeps the log; RabbitMQ delivers the message. That single difference creates the
whole feature set:

| Need | Kafka | RabbitMQ |
|---|---|---|
| Task distribution ("do this once") | awkward | native — competing consumers on a queue |
| Complex routing (per-region, per-severity) | consumer-side filtering | exchange bindings |
| Per-message TTL / delay / priority | not supported | native |
| Replay history | native | not possible (message gone) |
| Fan-out to N independent consumers | cheap (N groups) | N queues bound to one exchange |
| Ordering | per partition, strong | per queue, with a single consumer |
| Throughput ceiling | very high (100k+/s) | high (tens of thousands/s) |

**Choose RabbitMQ when:** you need per-job retries with delays, priority lanes, complex
routing trees, request/reply RPC over messaging, or a lower operational bar for a
medium-throughput system.

**Don't** try to use RabbitMQ as an event log (no replay, messages vanish) and **don't** use
Kafka as a task queue with per-message scheduling (you end up building a scheduler on top).

---

## 2. Mental Model — The AMQP Model

```
                       ┌──────────────────────────────────────┐
  publisher ──basic──► │            EXCHANGE                  │
   (message     publish│  direct | topic | fanout | headers   │
    + routing key)     └───────────────┬──────────────────────┘
                                       │ bindings (pattern → queue)
                     ┌─────────────────┼─────────────────┐
                     ▼                 ▼                 ▼
                 ┌───────┐         ┌───────┐         ┌───────┐
                 │queue A│         │queue B│         │queue C│
                 └───┬───┘         └───┬───┘         └───┬───┘
                     │                 │                 │
                 consumer1         consumer2          consumer3
                 (competing consumers on the SAME queue split the work)
```

Key vocabulary:

- **Connection** — a TCP connection. **Channel** — a lightweight multiplexed session on it.
  Always pool channels, never connections-per-message.
- **Exchange** — routing logic. **Binding** — a rule: exchange + pattern → queue.
- **Routing key** — a string the publisher supplies; the binding pattern is matched against it.
- **Queue** — the actual buffer; durable or transient, exclusive or shared.
- **Consumer** — a subscriber; `basic.ack` / `nack` / `reject` determine the message's fate.
- **vhost** — a namespace with its own permissions.

**The one-liner:**

> *Kafka: the consumer remembers where it was. RabbitMQ: the queue forgets the message the
> moment you acknowledge it.*

---

## 3. Deep Dive — Theory

### 3.1 Exchange types

| Type | Matching | Typical use |
|---|---|---|
| `direct` | routing key equals binding key | command/task routing (`order.create`) |
| `topic` | `*` = one word, `#` = zero or more | `orders.eu.created`, `*.error.#` |
| `fanout` | ignores key — all bound queues | broadcast/config invalidation |
| `headers` | matches message headers, not key | migration, multi-criteria routing |
| `x-consistent-hash` (plugin) | hash of key → sharded queues | partitioned ordering |

**Default exchange** `""` — every queue is auto-bound to it with its own name as the
routing key. `rabbitTemplate.convertAndSend("my-queue", msg)` works because of this. Handy
for simple cases; you lose routing flexibility.

### 3.2 Durability, persistence, confirms

Three independent things people conflate:

1. **Durable queue** — survives broker restart (metadata).
2. **Persistent message** — `deliveryMode=2`; survives restart (the message body is written
   to disk).
3. **Publisher confirm** — the broker acknowledges it *took responsibility*. Without
   confirms, a publish to a non-existent exchange silently disappears.

For a message to survive, you need **all three** plus a **durable exchange**. And you need
`publisher-confirm-type: correlated` in Spring so you can inspect the ack/nack.

**Unroutable messages**: `mandatory=true` + `ReturnsCallback` tells you a message matched no
queue. Otherwise it's dropped silently — a very common production mystery.

### 3.3 Acknowledgements and prefetch

- `basic.ack` — processed, delete.
- `basic.nack(requeue=true)` — put it back; may loop forever.
- `basic.nack(requeue=false)` → dead-letter exchange (if configured).
- `basic.reject` — same as nack for one message.

**Prefetch (`basic.qos`, `prefetchCount`)** caps unacked messages per consumer. Default is
unlimited in raw AMQP (Spring sets 250 in recent versions). Too high → one consumer hoards
the queue and memory balloons. Too low → idle consumers, poor throughput. Set it to
`max(batchSize, throughput × latency)`.

`prefetchSize` is ignored by RabbitMQ — don't set it.

### 3.4 Dead-lettering and retry loops

A queue can declare `x-dead-letter-exchange` + `x-dead-letter-routing-key`. Messages are
dead-lettered when:

- rejected/nacked with `requeue=false`,
- TTL expires,
- queue length limit exceeded,
- quorum queue delivery limit exceeded.

**The canonical retry pattern** — a chain of delay queues:

```
main-queue ──(fail)──► retry-1 (TTL 1s, DLX→main) ──► back to main-queue
                     retry-2 (TTL 10s, DLX→main)
                     retry-3 (TTL 60s, DLX→main) ──► parking-lot (final DLT, alert)
```

Each retry queue has **no consumer** — messages age out via TTL and are dead-lettered back.
This is exactly what `spring-retry`'s `RetryInterceptorBuilder` + `RepublishMessageRecoverer`
or the **delayed message plugin** (`x-delayed-message` exchange) implement more cleanly.

**Warning (the classic bug):** a per-message TTL in one queue expires **in order** — a
message with a 60 s TTL at the head blocks a 1 s TTL message behind it (head-of-line
blocking). Use **separate queues per delay**, or the delayed-exchange plugin.

### 3.5 Ordering

RabbitMQ orders messages within a **single queue with a single consumer**. With competing
consumers, order is not preserved because deliveries interleave. For ordered processing:
one queue + one consumer (limits throughput), or **consistent-hash exchange** to shard by
key so each shard is single-consumer.

Also: a `nack(requeue=true)` puts the message back — often at the head, ahead of newer
messages — so a poison message can be retried forever while starving the queue.

### 3.6 Quorum vs classic queues

| | Classic (mirrored) | Quorum |
|---|---|---|
| Replication | classic mirroring (deprecated) | Raft-based |
| Durability | good | stronger, safer under partition |
| Performance | higher throughput | slightly lower, more predictable |
| Features | all | no exclusive/non-durable, no `x-max-priority` historically |
| Use | transient, high throughput | **default for durable production data (RabbitMQ 3.8+)** |

Declare quorum queues with `x-queue-type: quorum`. They also support **delivery limits**
(`x-delivery-limit`) which auto-dead-letter a poison message after N attempts — a much
better default than infinite requeue.

### 3.7 Streams (RabbitMQ 3.9+)

RabbitMQ Streams bring a Kafka-like append-only log: replay, multiple consumers with
offsets, high throughput. If you want offset-based replay *and* AMQP routing, streams are
the bridge — but for event streaming most teams still choose Kafka.

---

## 4. Internal Mechanism

- **Connections** are TCP + AMQP handshake; **channels** multiplex frames. Multiple
  channels share one connection. Concurrency issue: a channel must not be used from
  multiple threads simultaneously — Spring's `CachingConnectionFactory` handles this by
  giving each thread its own channel from a pool.
- **Erlang processes** back each queue; each queue is a single Erlang process, which is why
  **a queue is single-threaded** — one hot queue can't use more than one core. More queues,
  more parallelism.
- **Message store** — classic queues use an index + segment files (`msg_store_persistent`),
  flushed lazily; quorum queues use a Raft log.
- **Flow control** — when the broker's memory or disk alarms trigger, publishers are
  **blocked** (connection-level credit). A `blocked` connection in production usually means
  a consumer is too slow.
- **`basic.get`** — synchronous single-message fetch; it's slow and discouraged. It's for
  debugging, not consumption.

---

## 5. Step-by-Step — A Message's Life

```
producer                        broker                          consumer
   │                              │                                │
   │ basic.publish(exchange,key)  │                                │
   ├─────────────────────────────►│ match bindings                 │
   │                              ├──► enqueue to queue(s)         │
   │◄── basic.ack (confirm) ──────┤   (persisted if mode=2)        │
   │                              │                                │
   │                              ├── deliver (prefetch window) ──►│
   │                              │                                │ process
   │                              │◄── basic.ack ──────────────────┤
   │                              ├── delete message               │
   │                              │                                │
   │                              │  (if nack requeue=false and     │
   │                              │   DLX set → dead letter)        │
```

Notice the confirm arrives **before** any consumer sees it — that's the broker taking
ownership, not the business logic completing.

---

## 6. Architecture Diagrams & Patterns

### 6.1 Work queue (competing consumers)

```
producer ──► exchange ──► [ queue: image-resize ] ──┬── worker 1
                                                    ├── worker 2
                                                    └── worker 3
     each message processed by EXACTLY ONE worker — automatic load balancing
```

### 6.2 Fan-out

```
                                  ┌── queue: email   → email-service
producer ──► fanout exchange ─────┼── queue: audit   → audit-service
                                  └── queue: search  → search-indexer
     each message goes to ALL bound queues — broadcast
```

### 6.3 Topic routing

```
binding: orders.eu.#      ← all EU orders
binding: *.created        ← all created events
binding: orders.*.cancelled

publish routing key "orders.eu.cancelled" → matches #1 and #3
```

### 6.4 Retry ladder with parking lot

```
                    ┌──────────── fail ────────────┐
                    ▼                              │
  main-queue ──► retry-1s ──► retry-10s ──► retry-60s ──► parking-lot
   (consume,      (TTL, DLX→main)  (TTL)      (TTL)        (alert + manual)
    attempt+1)
```

### 6.5 Request/reply (RPC over messaging)

```
client ──► rpc.exchange ──► [ rpc.queue ] ──► server
   ▲                                            │
   └──── reply-to=amq.rabbitmq.reply-to ◄───────┘
        correlationId must match
```

Spring: `RabbitTemplate.convertSendAndReceive` handles reply queue + correlation for you.
Use it only when a REST call isn't viable (long-running work, cross-DC with no inbound
connectivity).

---

## 7. Production Use Cases

- **Image/video processing pipeline** — heavy jobs, per-job retries, priority lanes for paid users.
- **Email/SMS fan-out** — topic exchange by type, per-provider queues, DLX on permanent failures.
- **Order orchestration steps in a legacy stack** — delayed retries for payment authorization.
- **Scheduled jobs (delayed messages)** — "release the reservation in 15 minutes" as a delayed message, no scheduler service needed.
- **Cross-service broadcast** — config invalidation via fanout.
- **Legacy integration** — AMQP/JMS is often the only option; Spring AMQP wraps it cleanly.

---

## 8. Code Examples — Spring AMQP

### 8.1 Topology as code

```java
@Configuration
class RabbitTopology {

    public static final String ORDERS_EXCHANGE = "orders.exchange";
    public static final String ORDERS_QUEUE    = "orders.queue";
    public static final String DLX             = "orders.dlx";
    public static final String PARKING_LOT     = "orders.parking-lot";
    public static final String RETRY_1S        = "orders.retry.1s";
    public static final String RETRY_10S       = "orders.retry.10s";

    @Bean
    TopicExchange ordersExchange() { return ExchangeBuilder.topicExchange(ORDERS_EXCHANGE).durable(true).build(); }

    @Bean
    DirectExchange deadLetterExchange() { return ExchangeBuilder.directExchange(DLX).durable(true).build(); }

    @Bean
    Queue ordersQueue() {
        return QueueBuilder.durable(ORDERS_QUEUE)
            .quorum()                                          // Raft-replicated, safer
            .deadLetterExchange(DLX)
            .deadLetterRoutingKey(PARKING_LOT)
            .deliveryLimit(5)                                  // poison-message guard
            .build();
    }

    @Bean
    Binding ordersBinding() {
        return BindingBuilder.bind(ordersQueue()).to(ordersExchange()).with("order.#");
    }

    @Bean Queue parkingLot() {
        return QueueBuilder.durable(PARKING_LOT).quorum().build();
    }

    @Bean Binding parkingBinding() {
        return BindingBuilder.bind(parkingLot()).to(deadLetterExchange()).with(PARKING_LOT);
    }

    /** Retry queues have NO consumers: TTL expiry dead-letters them back to the main queue. */
    @Bean Queue retry1s() {
        return QueueBuilder.durable(RETRY_1S).quorum()
            .ttl(1_000).deadLetterExchange("").deadLetterRoutingKey(ORDERS_QUEUE).build();
    }

    @Bean Queue retry10s() {
        return QueueBuilder.durable(RETRY_10S).quorum()
            .ttl(10_000).deadLetterExchange("").deadLetterRoutingKey(ORDERS_QUEUE).build();
    }
}
```

### 8.2 Producer with confirms and returns

```yaml
spring:
  rabbitmq:
    host: ${RABBIT_HOST}
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true
```

```java
@Component
class OrderEventPublisher {
    private final RabbitTemplate rabbit;

    OrderEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
        rabbit.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) log.error("broker NACKed {}: {}", correlation, cause);
        });
        rabbit.setReturnsCallback(r ->
            log.error("UNROUTABLE {} -> {}", r.getRoutingKey(), r.getMessage()));
    }

    void publish(OrderEvent e) {
        var msg = MessageBuilder.withBody(writeJson(e))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setDeliveryMode(MessageDeliveryMode.PERSISTENT)     // survives restart
            .setMessageId(e.eventId())
            .setHeader("x-retry-count", 0)
            .build();
        rabbit.send(ORDERS_EXCHANGE, "order.created", msg);
    }

    private byte[] writeJson(Object o) { /* ObjectMapper */ return null; }
}
```

### 8.3 Consumer with manual ack, prefetch, and bounded retry

```java
@Configuration
class RabbitConsumerConfig {
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory cf) {
        var f = new SimpleRabbitListenerContainerFactory();
        configurer.configure(f, cf);
        f.setPrefetchCount(20);                // the most important knob
        f.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        f.setConcurrentConsumers(4);
        f.setMaxConcurrentConsumers(8);
        f.setDefaultRequeueRejected(false);    // rejected → DLX, not an infinite loop
        return f;
    }
}
```

```java
@Component
class OrderListener {
    @RabbitListener(queues = RabbitTopology.ORDERS_QUEUE)
    public void on(OrderEvent e, Message raw, Channel channel,
                   @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            orderService.handle(e);            // idempotent by e.eventId()
            channel.basicAck(tag, false);
        } catch (TransientException ex) {
            int attempts = retryCount(raw) + 1;
            if (attempts <= 2) {
                channel.basicPublish("", RETRY_1S, retryHeader(raw, attempts), raw.getBody());
                channel.basicAck(tag, false);  // consumed: re-published into a delay queue
            } else {
                channel.basicNack(tag, false, false);   // → DLX / parking lot
            }
        } catch (Exception ex) {
            channel.basicNack(tag, false, false);       // permanent → DLX
        }
    }
}
```

### 8.4 Declarative retry with spring-retry (simpler)

```java
@Configuration
class RetryConfig {
    @Bean
    RetryOperationsInterceptor retryInterceptor(RabbitTemplate template) {
        return RetryInterceptorBuilder.stateless()
            .maxAttempts(3)
            .backOffOptions(500, 2.0, 5_000)   // initial, multiplier, max
            .recoverer(new RepublishMessageRecoverer(template, DLX, PARKING_LOT))
            .build();
    }

    @Bean
    SimpleRabbitListenerContainerFactory factory(ConnectionFactory cf,
                                                 RetryOperationsInterceptor interceptor) {
        var f = new SimpleRabbitListenerContainerFactory();
        f.setConnectionFactory(cf);
        f.setAdviceChain(interceptor);
        f.setPrefetchCount(20);
        return f;
    }
}
```

> Caveat: `spring-retry` retries **in-memory**, blocking the consumer thread and violating
> ordering (retried message completes after later ones). For long backoffs, use the delay-queue
> ladder instead.

### 8.5 Idempotent consumer

```java
@Transactional
public void handle(OrderEvent e) {
    int inserted = jdbc.update("""
        INSERT INTO processed_messages (consumer, message_id) VALUES ('orders', ?)
        ON CONFLICT DO NOTHING
        """, e.eventId());
    if (inserted == 0) return;
    apply(e);
}
```

`eventId` is `MessageProperties.messageId` — set by the producer, stable across redeliveries.

### 8.6 Delayed messages with the plugin

```java
@Bean
CustomExchange delayedExchange() {
    var args = Map.of("x-delayed-type", "topic");
    return new CustomExchange("delayed.exchange", "x-delayed-message", true, false, args);
}
// publish with header x-delay = 900_000 (15 min)
```

This removes the retry-ladder topology entirely — but requires the plugin to be installed
(often not allowed in managed offerings). Fall back to the TTL+DLX ladder.

---

## 9. Common Mistakes & Gotchas

| Mistake | Consequence | Fix |
|---|---|---|
| Assuming publish succeeded | Message silently dropped (no exchange/queue) | publisher confirms + `mandatory` + returns |
| Non-persistent message on durable queue | Lost on restart | `deliveryMode=2` |
| `autoAck=true` | Message deleted before processing → loss on crash | manual ack after handling |
| `requeue=true` for any error | Infinite poison loop, queue starvation | `requeue=false` + DLX |
| Per-message TTL in one queue | Head-of-line blocking | One queue per delay / plugin |
| Unlimited prefetch | One consumer hoards; memory spike | prefetch 10–50 |
| Channel shared across threads | Frame interleaving corruption | One channel per thread (Spring pools) |
| Classic mirrored queues in production | Deprecated, unsafe under partition | Quorum queues |
| One giant queue for everything | Single Erlang process = single core | Split queues, shard by key |
| `basic.get` in a loop | Slow polling, ordering issues | `@RabbitListener` push |
| DLX without a consumer | Data rot | Alert on parking-lot depth |
| `@RabbitListener` on a self-invoked method | No proxy → no `@Transactional` | Separate bean |

---

## 10. Performance

- **Prefetch** is the throughput knob. `prefetch × consumers` should roughly equal the
  number of messages you want in flight.
- **Many queues beat one big queue** — each queue is one Erlang process.
- **Quorum queues** cost ~2–3x classic throughput but give real safety; use classic only for
  ephemeral data.
- **Channel pooling** — one connection, many channels; `CachingConnectionFactory` default
  channel cache is 25.
- **Lazy queues** (`x-queue-mode=lazy`) keep messages on disk for huge backlogs — slower,
  but memory-safe.
- **Batch publish** with `BatchingRabbitTemplate` when publishing thousands of small messages.
- **Monitor**: queue depth (and *depth trend*), `messages_unacknowledged`, consumers per
  queue (0 consumers with backlog = incident), publish rate vs deliver rate, blocked
  connections (memory/disk alarm).

---

## 11. Security

- Distinct users per service, scoped to vhost + resource: **configure/write** for producers,
  **read** for consumers. Nobody needs `administrator`.
- TLS on AMQP port 5671; disable plaintext 5672 in prod.
- Rotate credentials; use `spring.rabbitmq.ssl.*` with a truststore.
- **No row-level authorization** — a consumer with `read` on a queue sees every tenant's
  message. Isolate sensitive streams into separate vhosts/queues.
- Poison message payloads in the parking lot are often the most sensitive; restrict access
  and set a retention policy.
- Don't log message bodies (PII); log `messageId` + routing key only.

---

## 12. Best Practices Checklist

- [ ] Quorum queues for durable data; durable exchanges.
- [ ] `publisher-confirm-type: correlated` + returns callback + `mandatory: true`.
- [ ] `deliveryMode=PERSISTENT` for anything that matters.
- [ ] Manual ack after successful processing; `defaultRequeueRejected=false`.
- [ ] Prefetch tuned and documented.
- [ ] DLX configured on every business queue, with a **consumed** parking lot + alert.
- [ ] `x-delivery-limit` (quorum) to cap poison retries.
- [ ] Retry via delay queues or the delayed plugin, not unbounded requeue.
- [ ] Idempotent consumers keyed on `messageId`.
- [ ] `concurrentConsumers`/`maxConcurrentConsumers` sized to queue count, not arbitrarily.
- [ ] Monitor depth, unacked, consumer count; alert on `consumers == 0 && depth > 0`.
- [ ] Credentials scoped per service; TLS enabled.

---

## 13. Exercises & Challenges

1. **Lose a message on purpose** — publish to a non-existent routing key with and without
   `mandatory`; observe the difference.
2. **Confirm the confirm path** — simulate a broker NACK (publish to a quorum queue with the
   leader down) and log the callback.
3. **Poison loop** — throw on every delivery with `requeue=true`; watch the redelivery count
   explode; fix with `requeue=false`.
4. **Retry ladder** — implement the three delay queues and verify the delay ordering with
   timestamps.
5. **Head-of-line blocking** — put a 60 s TTL message ahead of a 1 s one in the same queue;
   observe the delay; fix with separate queues.
6. **Prefetch experiment** — measure throughput at prefetch 1, 10, 100, 1000.
7. **RPC** — implement `convertSendAndReceive` with a 2 s timeout and a non-existent server;
   observe `ReplyTimeoutException`.
8. **Challenge** — write a `Testcontainers` test that proves a message rejected three times
   lands in the parking lot exactly once.

---

## 14. Interview Questions & Model Answers

**Q1. Kafka vs RabbitMQ — how do you choose?**
> By whether you need a log or a queue. Kafka retains and replays with per-partition ordering
> and very high throughput, and fans out to independent consumer groups cheaply — good for
> event streams, CDC, event sourcing. RabbitMQ routes per-message and deletes on ack — good
> for task distribution, complex routing, TTL/delay/priority, and lower-volume request/reply.
> I'd add: RabbitMQ's retry/delay support is far better; Kafka's replay is unmatched.

**Q2. How do you guarantee a published message isn't lost?**
> Four things together: durable exchange, durable queue, persistent message
> (`deliveryMode=2`), and publisher confirms so I know the broker accepted it. Plus
> `mandatory` + returns so I know it was routed. For end-to-end, the publish must be atomic
> with the DB write — that's an outbox, same as Kafka.

**Q3. What is prefetch and why does it matter?**
> `basic.qos` limits unacknowledged messages per consumer. Too high and one consumer hoards
> the queue, hurting latency and memory; too low and consumers idle between round trips.
> It's the main flow-control and fairness lever.

**Q4. Why does `nack(requeue=true)` cause problems?**
> The message is immediately redelivered, usually to the same consumer, fails again, and
> loops. It also sits at the head of the queue, starving newer messages. The fix is
> `requeue=false` with a DLX and a delay ladder, plus `x-delivery-limit` on quorum queues.

**Q5. How do you implement delayed messages?**
> Either the `x-delayed-message` plugin, or a queue whose messages have a TTL and whose DLX
> points back at the main queue. The TTL approach needs **one queue per delay value** —
> mixed TTLs in one queue cause head-of-line blocking because only the head is expired.

**Q6. How do you preserve ordering?**
> Only one consumer per queue preserves order; that caps throughput. To scale, shard with a
> consistent-hash exchange so each key always lands on the same queue with a single consumer.
> Also remember that requeued messages and in-memory retries break ordering.

**Q7. Classic vs quorum queues?**
> Quorum queues are Raft-replicated, safer under partition, and support delivery limits —
> they're the default for durable production data, at some throughput cost. Classic mirrored
> queues are deprecated. Classic non-mirrored is still fine for ephemeral, high-throughput
> data.

**Q8. What does a "blocked" connection mean?**
> The broker hit a memory or disk alarm and is applying flow control, so publishers block.
> Almost always a symptom of slow consumers or unbounded queue growth — fix the consumers,
> not the alarm threshold.

---

## 15. Self-Check & Revision Card

- [ ] I can draw exchange → binding → queue → consumer from memory.
- [ ] I can name the four exchange types and one use case each.
- [ ] I can list the three conditions for surviving a broker restart.
- [ ] I can explain prefetch and pick a value with reasoning.
- [ ] I can explain the retry ladder and the TTL head-of-line trap.
- [ ] I can justify RabbitMQ over Kafka for a given workload, and vice versa.

```
┌────────────────────────── RABBITMQ — 60s RECALL ─────────────────────────────┐
│ Broker, not log. exchange + binding + routing key → queue → consumer.        │
│ Deleted on ack. NO REPLAY.                                                   │
│                                                                              │
│ SURVIVE RESTART = durable exchange + durable queue + deliveryMode=2          │
│ KNOW IT ARRIVED = publisher confirms + mandatory + returns                   │
│                                                                              │
│ prefetch = unacked window = the throughput/fairness knob.                    │
│                                                                              │
│ RETRY: requeue=false → DLX. Delay ladder one-queue-per-TTL (head-of-line!)   │
│ or x-delayed-message plugin. Quorum + x-delivery-limit caps poison loops.    │
│                                                                              │
│ ORDER: single queue + single consumer, or consistent-hash exchange sharding. │
│ QUORUM (Raft) for durable data; classic only for ephemeral.                  │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [02 · Producers, Consumers & Spring Kafka](./02-Producers-Consumers-and-Spring-Kafka.md) · **Next:** [04 · Reliable Messaging & Event-Driven Architecture](./04-Reliable-Messaging-and-Event-Driven-Architecture.md)
