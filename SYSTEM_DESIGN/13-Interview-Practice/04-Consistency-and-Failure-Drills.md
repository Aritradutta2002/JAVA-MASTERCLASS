# 04 — Consistency and Failure Drills

## Consistency Selection

| Domain | Likely choice | Why | Repair/visibility |
|---|---|---|---|
| Account balance | Linearizable/serializable | Money invariant | Ledger reconciliation |
| Social like | Unique atomic write, eventual count | Idempotent fact, aggregate derived | Counter rebuild |
| Product description | Authoritative strong, cached bounded stale | Catalog correctness | Cache version/invalidation |
| Feed | Eventual, versioned | Fan-out and ranking | Projection lag metric |
| Inventory hold | Atomic conditional | Prevent oversell | Expiry/release reconciliation |

## Failure Matrix

| Dependency | Failure symptom | Immediate mitigation | Recovery evidence |
|---|---|---|---|
| Database | Write/read errors, pool saturation | Shed optional traffic, fail protected writes | Failover, invariant/reconciliation |
| Cache | Miss spike/origin load | Open breaker, bound fallback, stale subset | Origin protected, hit ratio recovers |
| Queue | Publish backlog/lag | Buffer, bulkhead, degrade features | Oldest age returns to zero |
| IdP | Cannot authenticate | Cache bounded prior session or fail protected routes | Token validation resumes |
| Provider | Timeout/ambiguous success | Query by idempotency key, circuit breaker | Settlement/reconciliation |
| Region | Timeout/DNS/cache issues | Global route/failover | RTO/RPO evidence |

## Consistency Questions

1. What exactly does a read observe?
2. Is the inconsistency bounded by time, version, or never?
3. What happens to a read-your-write flow?
4. Can two concurrent writers conflict?
5. How is conflict detected and repaired?
6. Is the system choosing safety or availability during a partition?

## Failure Questions

1. How long until failure is detected?
2. What work is already in flight?
3. What is acknowledged but not durable?
4. Which degradation is safe?
5. What manual action may be required?
6. How do you prove no invariant was broken?

## Scenario Drills

- Payment succeeds externally but times out locally.
- Two services update the same order concurrently.
- A follower follows another follower during feed generation.
- Inventory is held but payment times out.
- A message arrives after its newer version.

For each, write: timeline, invariant, decision, recovery, metric.

## Scoring

- **Safety:** no invalid money, stock, access, or identity state.
- **Liveness:** work eventually progresses when the dependency recovers.
- **Evidence:** logs/metrics/traces and reconciliation identify state.
- **Boundaries:** retry, queue, and staleness limits are explicit.
