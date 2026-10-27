# 06 — Design Review and Counter Questions

## Review Order

1. Restate requirements and invariants.
2. Trace one read and one write end to end.
3. Identify authoritative and derived state.
4. Find the first constrained resource.
5. Inject dependency loss, overload, and duplicate delivery.
6. Inspect tenant/security boundaries.
7. Challenge one decision with a credible alternative.

## Adversarial Prompts

- “Your cache and database can disagree. Which is true?”
- “The client timed out. Did the payment happen?”
- “A partition allows both sides to accept writes. What is the invariant?”
- “Your queue accepted the event. Where is the proof it was processed?”
- “One tenant is 20% of traffic. Which partition absorbs it?”
- “How does a user know the action succeeded after a failover?”
- “Your retry budget is 3; the gateway, mesh, and service each retry. What is amplification?”
- “Why does this derived index need its own consistency contract?”
- “What can you turn off without losing money or authorization?”
- “Show that two concurrent requests cannot double-book the last room.”

## Architecture Review Rubric

| Area | Evidence of quality |
|---|---|
| Correctness | Invariant, atomicity, idempotency, conflict/repair |
| Scale | Estimate, constrained resource, skew, failover headroom |
| Reliability | Timeout, retry budget, overload, dependency loss, recovery |
| Consistency | Per-operation model, staleness, read-your-write |
| Security | Identity, object/tenant authorization, secrets, abuse |
| Operations | SLOs, signals, runbook, rollout/rollback, ownership |
| Evolution | Staged path, migration, cost/team consequence |

## Counter-Question Templates

### “Why not simpler?”
> The simpler design is sufficient until [measured limit]. At current scale it reduces [complexity] but violates [requirement]. The advanced choice costs [X], so revisit if [condition].

### “What if the dependency is down?”
> Core writes fail closed when [invariant] cannot be preserved; optional [feature] degrades. Detection is [signal/time]. Recovery is [action], verified by [evidence].

### “Is that exactly once?”
> Exactly once is claimed only within [boundary]. Before it, [duplicate/loss semantics]; after it, [business effect] is protected by [idempotency/constraint].

### “How do you know?”
> User SLI shows [symptom], service/dependency signals localize it, and [trace/reconciliation] proves the cause/recovery.

## Adversarial Scoring

| Dimension | 0 | 1 | 2 | 3 |
|---|---:|---:|---:|---:|
| Identifies contradiction | | | | |
| Preserves core invariant | | | | |
| Bounds propagation | | | | |
| Provides repair/evidence | | | | |
| Revises design honestly | | | | |

## Practice Loop

- Review one HLD case using the rubric.
- Write two objections the author could not answer immediately.
- Revise the design and record the changed decision.
- Redraw only the affected path; avoid rewriting the entire solution.
