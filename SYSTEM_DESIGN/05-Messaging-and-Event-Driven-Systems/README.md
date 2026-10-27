# 05 — Messaging and Event-Driven Systems

> Durable propagation, delivery semantics, workflow correctness, and poison-message recovery.

| # | Chapter | Core decision | Level |
|---|---|---|---|
| 01 | [Messaging Fundamentals and Pub/Sub](./01-Messaging-Fundamentals-and-Pub-Sub.md) | Queue or pub/sub? | Intermediate → Expert |
| 02 | [Apache Kafka](./02-Kafka.md) | Partitioned replayable log | Intermediate → Expert |
| 03 | [RabbitMQ and ActiveMQ](./03-RabbitMQ-and-ActiveMQ.md) | Routing, ack, prefetch, DLQ | Intermediate → Expert |
| 04 | [Amazon SQS and Cloud Queues](./04-SQS-and-Cloud-Queues.md) | Managed standard/FIFO queues | Intermediate → Expert |
| 05 | [Event Sourcing and CQRS](./05-Event-Sourcing-and-CQRS.md) | History and query separation | Advanced → Expert |
| 06 | [DLQ, Retry, Idempotency, and Delivery Semantics](./06-DLQ-Retry-Idempotency-and-Delivery-Semantics.md) | At-least/at-most/exactly-once boundary | Advanced → Expert |

## Most Asked

- What guarantee does this broker provide at this boundary?
- How do you prevent duplicate business effects?
- How do you handle poison messages and replay?
- When is Kafka better than a queue?
- How does outbox make DB + event atomic?

## Practical Drill

- [ ] Implement outbox relay + consumer inbox dedup in Java.
- [ ] Model an order saga and its compensation failures.
- [ ] Load-test a slow consumer and plot lag recovery.

**Previous:** [04 — Caching](../04-Caching/README.md) · **Next:** [06 — API Design](../06-API-and-Communication-Design/README.md) · **Up:** [Hub](../README.md)
