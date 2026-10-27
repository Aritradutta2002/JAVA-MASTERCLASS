# 03 — Capacity Estimation Drills

## Formula Card

| Quantity | Formula |
|---|---|
| Average RPS | daily operations / 86,400 |
| Peak RPS | average × peak factor |
| Concurrency | RPS × latency seconds (Little’s Law) |
| Storage | items × bytes × retention × replicas |
| Bandwidth | RPS × bytes × 8 for bits/s |
| Queue drain | backlog / (processing rate − arrival rate) |

## Sanity Checks

- 1M operations/day ≈ 11.6/s.
- 100M operations/day ≈ 1,157/s.
- 1 billion operations/day ≈ 11,574/s.
- 1 KB × 1,000 RPS ≈ 1 MB/s ≈ 8 Mb/s.

## Drill 1 — URL Redirects

100M redirects/day, 10× peak, 200-byte response, 30-day analytics, 10k events/s peak.

**Acceptance:** average and peak RPS, edge/origin bandwidth, metadata and event storage, hot-key risk, cache headroom.

## Drill 2 — Payments

10M transactions/day, 1,000× peak, 1 KB record, 7-year retention, 3 replicas, 30% read replication.

**Acceptance:** average/peak TPS, ledger storage with indexes/replicas, read traffic, connection plan, ambiguity window.

## Drill 3 — Chat

100M messages/minute peak, 50 KB average, 50M sockets, 30-day retention.

**Acceptance:** messages/s, payload bandwidth, socket distribution, partition count, storage before replication/compression.

## Drill 4 — Video

20M concurrent streams at 8 Mbps average and 30% peak overhead.

**Acceptance:** aggregate Tb/s, regional distribution, origin fill bandwidth, per-user startup latency budget.

## Drill 5 — Rate Limiter

100k decisions/s, 50M instances, 100-byte policy update, local cache 60 seconds.

**Acceptance:** global coordination bandwidth, local-cache update rate, memory for policies, fallback semantics.

## Grading Rubric

| Level | Evidence |
|---|---|
| Weak | A number with no unit or assumption |
| Developing | Correct formula, weak assumption |
| Strong | Correct formula, explicit assumption, sanity check |
| Excellent | Sensitivity, skew, failure/failover multiplier |

## Common Traps

- Confusing daily and yearly rates.
- Ignoring replicas, indexes, protocol overhead, and backup copies.
- Assuming uniform key distribution.
- Counting average rather than peak concurrency.
- Estimating a single region when failover doubles load.
