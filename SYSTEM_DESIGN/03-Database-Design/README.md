# 03 — Database Design

> Data modeling, access paths, transactions, and storage scale for backend architects.

| # | Chapter | Core idea | Level |
|---|---|---|---|
| 01 | [SQL vs NoSQL and Selection Strategy](./01-SQL-vs-NoSQL-and-Selection-Strategy.md) | Select from workload invariants, not brand familiarity | Intermediate → Expert |
| 02 | [Schema Design and Transactions](./02-Schema-Design-and-Transactions.md) | Make invalid states difficult | Intermediate → Expert |
| 03 | [Indexing and Query Optimization](./03-Indexing-and-Query-Optimization.md) | Read the plan, optimize the access path | Intermediate → Expert |
| 04 | [Replication, Partitioning, and Sharding](./04-Replication-Partitioning-and-Sharding.md) | Copies scale reads; ownership scales writes | Advanced → Expert |
| 05 | [Isolation, Deadlocks, and Connection Pooling](./05-Isolation-Deadlocks-and-Connection-Pooling.md) | Correct transactions under contention | Advanced → Expert |
| 06 | [Archival and Data Lifecycle](./06-Archival-and-Data-Lifecycle.md) | Hot, warm, cold, retention, deletion | Intermediate → Expert |
| 07 | [Database Comparison Matrix](./07-Database-Comparison-Matrix.md) | Compare guarantees, access, and operations | Intermediate → Expert |
| — | [PostgreSQL](./PostgreSQL.md) · [MySQL](./MySQL.md) · [MongoDB](./MongoDB.md) · [Cassandra](./Cassandra.md) · [DynamoDB](./DynamoDB.md) · [Elasticsearch](./Elasticsearch.md) · [Redis](./Redis.md) | Product profiles | Intermediate → Expert |

## Most Asked in Interviews

- How do you choose a database?
- How do you shard and avoid hot partitions?
- Why can `W + R > N` still be stale?
- How do you size a connection pool?
- How do you migrate a schema without downtime?

## Weakest Areas

- [ ] I can explain row, document, and partition access paths.
- [ ] I can interpret a query plan and identify N+1 work.
- [ ] I can distinguish replicas from shards.
- [ ] I can state a data retention/deletion propagation plan.

## Practical Drill

- [ ] Design an order schema for 10k writes/s and read-your-write checkout.
- [ ] Choose primary, derived, archive, and search stores.
- [ ] Demonstrate the load-shedding and migration behavior during a primary outage.

**Previous:** [02 — Distributed Systems](../02-Distributed-Systems/README.md) · **Next:** [04 — Caching](../04-Caching/README.md) · **Up:** [Hub](../README.md)
