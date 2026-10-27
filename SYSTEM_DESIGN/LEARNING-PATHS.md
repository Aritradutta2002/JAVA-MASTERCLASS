# Learning Paths

> Select a path by interview target and available time. A path is a sequence of outputs, not a passive reading list.

## Path A — First System Design Interview (4 weeks)

**Target:** junior backend candidate who knows basic databases, HTTP, and one backend framework.

| Week | Study | Produce | Self-check |
|---|---|---|---|
| 1 | 01 fundamentals | One-page URL-shortener design with estimate, API, schema, diagram, failure mode | Explain read/write ratio and first bottleneck |
| 2 | 03 database + 04 caching | Compare PostgreSQL and Redis for one workload | State cache truth, invalidation, and stampede behavior |
| 3 | 02 distributed systems + 05 messaging | CAP/PACELC explanation and one retry/DLQ design | Correct the “pick two of three” misconception |
| 4 | 11 case studies + mock | Two timed 45-minute designs and a spoken review | Follow requirements → estimate → design → failure → trade-offs |

### Minimum Mastery

- [ ] Define latency, throughput, availability, reliability, and consistency.
- [ ] Estimate QPS, storage, bandwidth, and connection count with units.
- [ ] Propose an API and schema for URL shortener or rate limiter.
- [ ] Explain cache-aside and when stale data is harmful.
- [ ] Describe behavior during a database outage.
- [ ] Name one rejected design and why.

## Path B — Java Backend Engineer (8–12 weeks)

**Target:** 3–5 years of Java/Spring production experience.

| Week | Core | Java / Spring Connection | Practical deliverable |
|---|---|---|---|
| 1 | 01 fundamentals | JVM, thread pool, connection pool as capacity boundaries | Service capacity worksheet |
| 2 | 03 databases | Spring transactions, Hikari sizing, N+1, isolation | Query and transaction review |
| 3 | 04 caching | Spring Cache annotations, Redis topology, stampedes | Cache policy table |
| 4 | 05 messaging | Spring Kafka/Rabbit, transactional outbox, DLQ | Idempotent consumer + test |
| 5 | 06 APIs | Spring MVC, WebFlux, HTTP clients, deadlines | REST/gRPC API comparison |
| 6 | 07 security | Spring Security, JWT resource server, OAuth2/OIDC | Threat model and auth sequence |
| 7 | 08 microservices | Resilience4j, discovery, gateway, tracing | Failure-isolation review |
| 8 | 09 cloud + 10 reliability | Docker/K8s, metrics, tracing, SLOs | Deployment and SLO card |
| 9–10 | 11 HLD cases | Map one Spring Boot service boundary per case | Three timed designs |
| 11–12 | 12 LLD + practice | Domain modeling, concurrency, assertions | One complete LLD implementation |

### Java-Specific Production Drills

- [ ] Size a Hikari pool from database capacity, not instance count.
- [ ] Demonstrate duplicate delivery and prove idempotency with a unique constraint.
- [ ] Trace a request across logs, metrics, and spans using one correlation identifier.
- [ ] Implement cache invalidation without a database/Redis dual-write race.
- [ ] Explain where Spring transactions stop and an outbox/saga must begin.
- [ ] Set connect, read, and total timeouts so retries do not amplify load.

## Path C — Senior, Staff, and Architecture (10–14 weeks)

**Target:** 5–10 years of experience; HLD, architecture review, production, and leadership rounds.

| Week | Core | Architecture Emphasis | Deliverable |
|---|---|---|---|
| 1–2 | 01–02 | Quantified trade-offs, consistency semantics, coordination | Architecture decision record |
| 3 | 03–04 | Data ownership, tiering, cache correctness | Data topology review |
| 4 | 05 | Delivery semantics, schema evolution, backpressure | Event contract and DLQ policy |
| 5 | 06–07 | API abuse boundaries, identity, encryption, threat model | Security review packet |
| 6 | 08 | Boundaries, sagas, outbox, mesh cost | Migration plan |
| 7 | 09 | Region/AZ failure, deployment, autoscaling, cost | Multi-region options paper |
| 8 | 10 | SLIs, error budgets, incident command, DR | SLO and runbook package |
| 9–11 | 11 HLD | 20 systems, one deep-dive each | Case-study portfolio |
| 12–14 | 12–13 | LLD leadership, cross-team APIs, mock reviews | Design-review simulation |

### Staff-Level Themes

- [ ] Compare build/buy/operate and managed versus self-hosted costs.
- [ ] Design an organizationally safe migration with dual writes, backfills, and rollback.
- [ ] Explain how platform defaults affect developer velocity and system risk.
- [ ] Define data ownership and deletion/compliance behavior across services.
- [ ] Design multi-region behavior with explicit RTO/RPO and conflict semantics.
- [ ] Discuss cost, supportability, and incident load as architecture constraints.
- [ ] Make and defend a decision when requirements conflict.

## Path D — FAANG / Product Company (6 weeks)

| Week | Focus | Mandatory practice |
|---|---|---|
| 1 | Framework, estimates, data | 8 timed whiteboard designs |
| 2 | Databases, caching, queues | Redesign the same prompt three ways |
| 3 | Distributed systems | CAP, quorum, ordering, idempotency drills |
| 4 | HLD cases 1–10 | Two designs/day with spoken follow-ups |
| 5 | HLD cases 11–20 | Architecture review and failure injection |
| 6 | Mock interviews | 3 × 45 minutes + 2 × senior/leadership rounds |

### Interview Rubric

| Dimension | Weak | Strong |
|---|---|---|
| Structure | Jumps to components | Clarifies, estimates, then designs |
| Requirements | Assumes everything | Names scale, consistency, durability, geography |
| Data | Names a database | Models access paths and ownership |
| Scale | Says “add more servers” | Names bottleneck and partitioning strategy |
| Failure | Ignores outages | Explains degradation, detection, and recovery |
| Trade-offs | One “right” design | States alternatives and conditions |
| Communication | Silent | Thinks aloud and invites collaboration |

## Path E — Fintech, Banking, and Enterprise (8 weeks)

**Target:** correctness, auditability, and regulated architecture dominate.

- [ ] Double-entry ledger invariants and reconciliation.
- [ ] Idempotency at API, workflow, and provider boundaries.
- [ ] Authorization by policy and segregation of duties.
- [ ] Tamper-evident audit trails and retention.
- [ ] Regional data residency and privacy boundaries.
- [ ] Availability tiers: read, transact, settle, and report separately.
- [ ] Change control, rollback, and proof of deployment.

## Path F — Engineering Leadership Preparation (4 weeks)

**Target:** staff/lead/manager interview combining technical judgment and organizational leadership.

| Theme | Preparation |
|---|---|
| Decision quality | Write ADRs with context, options, decision, consequences |
| Technical strategy | Compare 12/18/24-month platform direction under constraints |
| Team design | Clarify ownership and on-call boundaries without silos |
| Risk | Identify blast radius, reversibility, and migration controls |
| Incident leadership | Run a severity-1 simulation with timeline and comms |
| Mentoring | Give architecture feedback tied to explicit principles |
| Communication | Present a design to a skeptical executive in five minutes |

## Weekly Execution Template

1. **Learn (90 min):** one chapter; write the invariant in one sentence.
2. **Reproduce (60 min):** redraw every diagram without the notes.
3. **Apply (90 min):** one design or implementation exercise.
4. **Interview (45 min):** answer questions aloud under time pressure.
5. **Review (30 min):** correct errors in the checklist and schedule spaced review.

## Spaced Review Schedule

| Review | Timing | Task |
|---|---|---|
| R1 | 24 hours | Recall diagram and three trade-offs without notes |
| R2 | 3 days | Redo capacity estimate and one failure scenario |
| R3 | 1 week | Answer a random case-study question |
| R4 | 2 weeks | Implement or redesign one weak area |
| R5 | 1 month | Complete a full mock interview |

## Completion Evidence

Do not mark a topic “done” because it was read. Attach evidence: a diagram you drew; a calculation you can reproduce; code that compiles; an incident you can diagnose; a recorded mock answer; a written trade-off you can defend.
