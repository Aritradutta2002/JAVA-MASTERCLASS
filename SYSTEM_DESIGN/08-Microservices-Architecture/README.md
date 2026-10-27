# 08 — Microservices Architecture

> Boundaries, migration, communication, platform primitives, resilience, and distributed debugging.

| # | Chapter | Core question | Level |
|---|---|---|---|
| 01 | [Service Boundaries and DDD](./01-Service-Boundaries-and-DDD.md) | What owns this business capability? | Intermediate → Expert |
| 02 | [Monolith to Microservices Migration](./02-Monolith-to-Microservices-Migration.md) | How do we migrate reversibly? | Advanced → Expert |
| 03 | [Service Communication, Sagas, and Outbox](./03-Service-Communication-Sagas-and-Outbox.md) | How do workflows survive partial failure? | Advanced → Expert |
| 04 | [API Gateway, Discovery, and Service Mesh](./04-API-Gateway-Discovery-and-Service-Mesh.md) | Which layer owns which policy? | Advanced → Expert |
| 05 | [Resilience Patterns and Fault Isolation](./05-Resilience-Patterns-and-Fault-Isolation.md) | How do we bound propagation? | Advanced → Expert |
| 06 | [Observability, Debugging, and Distributed Tracing](./06-Observability-Debugging-and-Distributed-Tracing.md) | How do we reconstruct cross-service failure? | Advanced → Expert |

## Boundary Test

A candidate service passes only if it has a coherent business capability, owned data, an accountable team, an independently evolvable contract, and a benefit that exceeds network/operational cost.

## Practical Drill

- [ ] Context-map e-commerce and identify an ACL.
- [ ] Plan a strangler migration with measurable gates.
- [ ] Trace checkout across HTTP, Kafka, database, and cache.

**Previous:** [07 — Security](../07-Security-Design/README.md) · **Next:** [09 — Cloud](../09-Cloud-and-Infrastructure-Design/README.md) · **Up:** [Hub](../README.md)
