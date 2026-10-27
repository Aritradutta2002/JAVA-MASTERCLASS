# 01 — System Design Fundamentals

> The mental models that make every architecture interview answer coherent.

| # | Chapter | Core question | Level |
|---|---|---|---|
| 01 | [System Design Framework and Trade-Offs](./01-System-Design-Framework-and-Trade-Offs.md) | How do I turn ambiguity into a defensible design? | Beginner → Expert |
| 02 | [Scalability and Capacity Planning](./02-Scalability-and-Capacity-Planning.md) | Which resource saturates first? | Intermediate → Expert |
| 03 | [Performance, Latency, and Throughput](./03-Performance-Latency-and-Throughput.md) | Why is the tail slow? | Beginner → Advanced |
| 04 | [Availability, Reliability, and Fault Tolerance](./04-Availability-Reliability-and-Fault-Tolerance.md) | How does the system behave when reality fails? | Beginner → Expert |
| 05 | [Consistency, Partitioning, and Distributed Basics](./05-Consistency-Partitioning-and-Distributed-Basics.md) | Which facts may disagree, and where is state owned? | Intermediate → Expert |
| 06 | [Monolith vs Microservices](./06-Monolith-vs-Microservices.md) | Which boundary and deployment model fits? | Beginner → Advanced |

## Recommended Order

1. Framework → 2. Scalability → 3. Performance → 4. Reliability → 5. Distributed basics → 6. Architecture organization.

## Most Asked in Interviews

| Question | Common trap |
|---|---|
| How do you start? | Jumping directly to components |
| How do you scale? | “Add more servers” without a bottleneck |
| How do you calculate availability? | Ignoring correlated dependencies and failure domains |
| What is strong consistency? | Using the term without a precise model |
| Why microservices? | Treating distribution as a goal rather than a trade-off |

## Weakest Areas to Brush Up

- [ ] I can calculate QPS, storage, bandwidth, and concurrency with units.
- [ ] I can name a rejected design and its cost.
- [ ] I can describe graceful degradation for one critical flow.
- [ ] I can distinguish latency, throughput, and availability.

## Coding / Design Drill

- [ ] Design a rate limiter for 20k requests/s in 45 minutes.
- [ ] Redraw its failure paths without notes.
- [ ] Score it with the [Interview Checklist](../INTERVIEW-CHECKLIST.md).

**Next:** [02 — Distributed Systems](../02-Distributed-Systems/README.md) · **Up:** [Hub](../README.md)
