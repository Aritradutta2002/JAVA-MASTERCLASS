# 05 — Mock Interview Rubric

## Setup

- 45 minutes, shared whiteboard/editor.
- Interviewer may inject one failure after minute 25.
- Candidate asks clarifying questions without prompting.
- No implementation code unless requested; API and schema must be precise.

## Rubric

| Dimension | Weight | Weak | Strong |
|---|---:|---|---|
| Requirements | 12 | Assumes features/scale | Names actors, invariants, NFRs, scope |
| Capacity | 12 | Incorrect/no units | Correct math, assumptions, sanity checks |
| API/contracts | 10 | Vague CRUD | Idempotency, errors, pagination, compatibility |
| Data/ownership | 12 | Shared “database” | Owner, access path, consistency, migration |
| Architecture | 12 | Product-name collage | Component responsibility and request paths |
| Scalability | 10 | “Add servers” | First bottleneck, partition, skew, growth |
| Failure/reliability | 15 | One happy-path outage | Detect, mitigate, recover, reconcile |
| Security | 7 | Authentication only | Object/tenant auth, abuse, secrets, privacy |
| Trade-offs | 10 | One “right” answer | Alternatives, cost, revisit condition |

## Senior/Staff Add-ons

- [ ] Organizes around business capability and team ownership.
- [ ] Quantifies operational/cost consequences.
- [ ] Distinguishes roadmap investment from error-budget policy.
- [ ] Addresses migration, observability, and multi-region evolution.
- [ ] Can challenge an interviewer’s assumption respectfully.

## Failure-Injection Grading

| Element | Points |
|---|---:|
| Identifies affected invariant | 3 |
| Names evidence/signal | 2 |
| Immediate safe mitigation | 3 |
| Recovery/repair path | 3 |
| Prevention/test | 2 |

## Debrief Template

1. **Best moment:** one decision that anchored the design.
2. **Weakest assumption:** what was not validated.
3. **Missing invariant:** what the design could violate.
4. **Unquantified bottleneck:** first resource to measure.
5. **Untested failure:** next game-day scenario.
6. **Next answer to rewrite:** the 90-second response.
7. **Next implementation drill:** topic and acceptance test.

## Pass Criteria

- 70/100 overall and no safety/security requirement completely absent.
- Correct order: clarify → estimate → API/data → architecture → failure.
- At least two explicitly rejected alternatives.
- At least one recovery that checks state, not only infrastructure health.

## Common Feedback Traps

- “Use Kafka” without retention/ordering/duplicate requirements.
- “Use Redis” without truth/invalidation/stampede.
- “Use Kubernetes” without failure domains/capacity.
- “Use microservices” without ownership/operational cost.
- “Eventually consistent” without staleness and repair.
