# System Design Interview Checklist

> Use this as a pre-interview readiness gate and a post-interview improvement log. Evidence means a diagram, calculation, implementation, recorded answer, or written decision—not merely a checked box.

## 1. Fundamentals Gate

- [ ] I can distinguish latency from throughput and average from percentile latency.
- [ ] I can explain availability, reliability, durability, consistency, and fault tolerance without slogans.
- [ ] I can calculate average and peak QPS, storage, bandwidth, and concurrent connections.
- [ ] I can explain vertical and horizontal scaling and their state implications.
- [ ] I can identify a hot partition, hot key, connection bottleneck, and noisy-neighbor problem.
- [ ] I can state a rejected architecture and the condition that would change my choice.
- [ ] I can produce a 45-minute design that starts with requirements, not boxes.

## 2. Distributed Systems Gate

- [ ] I can state CAP correctly: partition-time choice between consistency and availability.
- [ ] I can use PACELC to discuss latency cost during normal operation.
- [ ] I can distinguish linearizability, serializability, causal consistency, read-your-writes, and eventual consistency.
- [ ] I can explain Raft terms, log replication, commit rules, and leader election at a useful level.
- [ ] I can explain why quorum intersection matters and what `W + R > N` does not prove.
- [ ] I can compare 2PC, XA, saga choreography/orchestration, and transactional outbox.
- [ ] I can explain split brain, fencing tokens, leases, vector clocks, and clock uncertainty.
- [ ] I can explain at-least-once, at-most-once, and exactly-once only with an explicit boundary.

## 3. Data and Cache Gate

- [ ] I can select SQL/NoSQL from access pattern, consistency, and scale evidence.
- [ ] I can model keys, indexes, ownership, and the source of truth.
- [ ] I can explain replication lag, read-after-write, quorum reads, and read repair.
- [ ] I can choose a partition key and detect skew/hot partitions.
- [ ] I can explain isolation levels, write skew, deadlocks, and connection-pool sizing.
- [ ] I can compare cache-aside, write-through, write-back, and read-through.
- [ ] I can handle eviction, invalidation, stampedes, hot keys, and warming.
- [ ] I can explain Redis persistence, replication, failover, and cluster trade-offs.

## 4. Messaging and API Gate

- [ ] I can select Kafka, RabbitMQ, ActiveMQ, or SQS from workload requirements.
- [ ] I can design event envelopes, schema evolution, ordering keys, retry, and DLQ.
- [ ] I can make a consumer idempotent with durable state.
- [ ] I can explain event sourcing and CQRS benefits, costs, and rebuild paths.
- [ ] I can select REST, GraphQL, gRPC, WebSockets, or SSE and defend the choice.
- [ ] I can design gateway policies, load balancing, discovery, rate limits, and breakers.
- [ ] I can specify timeouts, deadlines, retries, jitter, and backpressure.

## 5. Security Gate

- [ ] I can distinguish authentication, authorization, federation, and identity proofing.
- [ ] I can explain JWT benefits, revocation, key rotation, and algorithm confusion risks.
- [ ] I can identify OAuth 2.0 roles and the authorization-code + PKCE flow.
- [ ] I can distinguish OAuth 2.0 from OpenID Connect and describe SSO.
- [ ] I can threat-model a public API using assets, actors, trust boundaries, and abuse cases.
- [ ] I can layer WAF, rate limiting, quotas, bot controls, and DDoS protection.
- [ ] I can explain secrets management, encryption at rest/in transit, and key rotation.
- [ ] I can prevent confused-deputy and object-level authorization failures.

## 6. Platform and Reliability Gate

- [ ] I can define service boundaries and data ownership.
- [ ] I can plan a strangler migration with compatibility and rollback.
- [ ] I can explain config, discovery, tracing, service mesh, saga, and outbox costs.
- [ ] I can map a service to AWS, Azure, or GCP without assuming every region exposes identical services.
- [ ] I can explain Docker/Kubernetes scheduling, probes, disruption, and rollout behavior.
- [ ] I can compare blue-green, canary, rolling, and feature-flag releases.
- [ ] I can distinguish SLI, SLO, SLA, error budget, and burn rate.
- [ ] I can lead an incident timeline, mitigation, communication, and recovery.
- [ ] I can state RTO/RPO and validate them against backup and failover exercises.

## 7. HLD Case-Study Gate

For each of the 20 systems, I can produce:

- [ ] Requirements and out-of-scope list.
- [ ] Capacity estimate with assumptions and units.
- [ ] API and core data model.
- [ ] Detailed architecture and critical sequence.
- [ ] First bottleneck and scaling path.
- [ ] Consistency and failure behavior.
- [ ] Security, abuse controls, and observability.
- [ ] At least one rejected alternative and its cost.
- [ ] Production incident scenario and recovery.
- [ ] 45-minute spoken design.

## 8. LLD Gate

- [ ] I can extract classes and interfaces from requirements.
- [ ] I can justify each SOLID decision and avoid over-engineering.
- [ ] I can select patterns by forces and name when not to use them.
- [ ] I can draw class and sequence diagrams.
- [ ] I can model state transitions and invalid transitions.
- [ ] I can implement Java 17 code with clear contracts.
- [ ] I can state time/space and concurrency behavior.
- [ ] I can test normal, boundary, invalid, and concurrent cases.
- [ ] I can explain how the design evolves into services.

## 9. Case Interview Rubric

Score each dimension from 0–3.

| Dimension | 0 | 1 | 2 | 3 |
|---|---|---|---|---|
| Requirements | Assumes | Asks few | Clarifies scale/constraints | Explicit scope, NFRs, out-of-scope |
| Estimation | None | Wrong units | Plausible estimate | Estimate drives decisions |
| Data | No model | Names database | Models entities/access | Ownership, indexes, partitions, growth |
| Architecture | Boxes only | Some relationships | Coherent request path | Justified boundaries and failure paths |
| Scalability | “Add servers” | One pattern | Names bottleneck | Partitioning, skew, limits, evolution |
| Reliability | Ignores failure | Mentions failover | Behavior per failure | Detection, mitigation, recovery, runbook |
| Security | Ignored | Generic | Boundary controls | Threat model and abuse controls |
| Trade-offs | No alternatives | Names one | States one cost | Alternatives and change conditions |
| Communication | Silent | Unexplained | Some rationale | Collaborative, visible reasoning |

**Target:** 20+ points across nine dimensions, with no zero in requirements, data, architecture, or communication.

## 10. Pre-Interview Checklist

- [ ] Interviewer format and duration confirmed.
- [ ] Whiteboard/diagram tool tested.
- [ ] Screen-share fallback prepared.
- [ ] Camera/audio/notification state checked.
- [ ] Three domains selected for targeted review.
- [ ] Capacity constants and five formulas recalled.
- [ ] One case study practiced end to end.
- [ ] One incident story prepared with decision and outcome.
- [ ] Questions for the interviewer prepared.

## 11. Post-Interview Review

Within 24 hours, record:

1. Which requirement was missed?
2. Which estimate was wrong and why?
3. Which component was unjustified?
4. Which failure mode was ignored?
5. Which terminology was imprecise?
6. Which follow-up exposed a gap?
7. What concrete artifact will prove improvement?
8. When will the artifact be completed?

## 12. Mastery Levels

| Level | Evidence |
|---|---|
| 🟢 Understand | Can follow explanation and identify the right chapter |
| 🟡 Apply | Can design a small component or answer with guidance |
| 🔴 Design | Can independently produce an end-to-end design under time pressure |
| ⚫ Review | Can challenge a design, quantify risks, and guide a team |
| ⬛ Lead | Can set strategy, manage trade-offs, and explain organizational impact |

Your target should match the role: **Apply** for junior, **Design** for mid-level, and **Review/Lead** for senior/staff and architecture roles.
