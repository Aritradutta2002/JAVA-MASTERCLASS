# 01 — 45-Minute System Design Framework

## Timer

| Minutes | Activity | Required output |
|---:|---|---|
| 0–5 | Clarify scope and non-functional requirements | In-scope/out-of-scope, correctness, scale |
| 5–8 | Estimate | QPS, storage, bandwidth, concurrency |
| 8–12 | API | Resources, idempotency, errors, pagination |
| 12–17 | Data | Owner, schema, access paths, partition key |
| 17–27 | Architecture | Request path, async path, trust boundaries |
| 27–39 | Deep dive and failure | Bottleneck, overload, dependency loss, recovery |
| 39–43 | Scale and security | Partition/hot key, abuse, multi-tenant |
| 43–45 | Summary | Decisions, costs, change conditions |

## Opening Questions

1. Who are the actors and what is the one core workflow?
2. Which action must never be wrong or duplicated?
3. What can be eventually consistent, and for how long?
4. What are average, peak, and growth rates?
5. Is the workload read- or write-heavy? What is the object size?
6. Are there tenants, regions, compliance, or budget constraints?
7. What is explicitly out of scope?

## Architecture Checklist

- [ ] Every component maps to a requirement.
- [ ] One authoritative owner for each fact.
- [ ] Derived stores have rebuild/reconciliation paths.
- [ ] The first bottleneck is named and measured.
- [ ] Timeouts and retries have a shared budget.
- [ ] Overload has admission control and shedding order.
- [ ] Failure preserves the core invariant.
- [ ] Authentication, authorization, and tenant isolation are visible.
- [ ] SLOs are user-outcome based.
- [ ] Rejected alternatives and cost are explicit.

## Spoken Transitions

- “I’ll model this as a write path and derived read path because…”
- “The first constraint I expect is…; I’ll validate it by…”
- “If this dependency partitions, I preserve… by…; recovery is…”
- “I’m choosing… over… because…; I would revisit this when…”

## Failure Injection Prompts

- The database is 500 ms slower.
- One availability zone disappears.
- The queue is delayed by ten minutes.
- The cache is empty after deployment.
- A hot tenant is ten times average.
- A provider accepts the request but the response is lost.

For each, state symptom, evidence, immediate mitigation, data repair, and prevention.

## Self-Score

| Dimension | 0 | 1 | 2 | 3 |
|---|---:|---:|---:|---:|
| Requirements | | | | |
| Estimate | | | | |
| API/data | | | | |
| Architecture | | | | |
| Failure | | | | |
| Security | | | | |
| Trade-offs | | | | |
| Communication | | | | |

**Target:** 18/24, with no zero in requirements, failure, or trade-offs.
