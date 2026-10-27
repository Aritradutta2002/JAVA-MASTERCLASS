# 04 — Caching

> Low-latency derived state with explicit freshness, invalidation, and overload behavior.

| # | Chapter | Core idea | Level |
|---|---|---|---|
| 01 | [Cache Patterns](./01-Cache-Patterns.md) | Choose the read/write consistency contract | Beginner → Expert |
| 02 | [Redis Internals and Distributed Cache](./02-Redis-Internals-and-Distributed-Cache.md) | Shards, replicas, persistence, memory | Intermediate → Expert |
| 03 | [Eviction, Invalidation, and Consistency](./03-Eviction-Invalidation-and-Consistency.md) | Bound staleness and prevent stale repopulation | Intermediate → Expert |
| 04 | [Stampede, Hot Keys, and Cache Warming](./04-Stampede-Hot-Keys-and-Cache-Warming.md) | Coalesce work and distribute popularity | Advanced → Expert |
| 05 | [Cache Production Operations](./05-Cache-Production-Operations.md) | Protect the origin during cache failure | Advanced → Expert |

## Most Asked in Interviews

- Cache-aside vs write-through vs write-back?
- How do you prevent a stampede?
- How do you invalidate without a dual-write race?
- How does Redis Cluster distribute and fail over?
- What happens when the cache is down?

## Weakest Areas

- [ ] I can name the source of truth and maximum staleness.
- [ ] I can design single-flight and a stale-while-revalidate policy.
- [ ] I can identify hot slots and high-cardinality keys.
- [ ] I can bound origin fallback during a cache outage.

## Practical Drill

- [ ] Implement a Java cache-aside helper with per-key single-flight and jittered TTL.
- [ ] Load-test a hot key expiring under 1k concurrent requests.
- [ ] Run a cache-failure game day and record origin protection evidence.

**Previous:** [03 — Database Design](../03-Database-Design/README.md) · **Next:** [05 — Messaging](../05-Messaging-and-Event-Driven-Systems/README.md) · **Up:** [Hub](../README.md)
