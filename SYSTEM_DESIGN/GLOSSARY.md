# System Design Glossary

> Precise language prevents most interview mistakes. Terms below are expanded in linked chapters.

## A–C

| Term | Precise meaning | Common trap |
|---|---|---|
| **Availability** | Probability that a correctly formed request receives a non-error response within a stated interval. | Treating a running process as automatically available |
| **ACID** | Atomicity, consistency, isolation, durability—properties of database transactions. | Assuming ACID spans independent services without XA |
| **Affordability** | Cost envelope in money, operating effort, latency, and organizational complexity. | Optimizing only infrastructure cost |
| **Agreement** | Nodes durably decide the same value for the same log position. | Confusing agreement with progress/liveness |
| **API Gateway** | Edge component that routes, authenticates, aggregates, limits, or transforms API traffic. | Making it owner of all business logic |
| **Availability zone (AZ)** | Isolated failure domain within a region with independent power/networking paths. | Treating an AZ as a single machine |
| **Backpressure** | Mechanism that slows or rejects upstream work when downstream capacity is reached. | Assuming queues remove admission control |
| **BASE** | Informal family emphasizing availability, soft state, and eventual convergence. | Treating BASE as a formal ACID counterpart |
| **Bounded staleness** | Guarantee that a read is no older than a specified maximum under stated conditions. | Equating it with linearizability |
| **Bulkhead** | Isolation boundary preventing one failing dependency from consuming all resources. | Creating a pool without ownership and metrics |
| **Cache stampede** | Many concurrent requests miss the same expired/hot key and load it simultaneously. | Adding a TTL and calling the problem solved |
| **CAP** | During a network partition, choose linearizable consistency or availability for the affected operation. | “Pick two of CAP” as a design-time slogan |
| **Causal consistency** | Preserves causal relationships; concurrent writes may be reordered. | Calling it strong consistency |
| **Circuit breaker** | State machine stopping calls to a failing dependency, then probing recovery. | Configuring it without timeout and bulkhead behavior |
| **Compensation** | Domain action semantically reversing a completed saga step. | Treating compensation as database rollback |
| **Consistency model** | Contract describing which observed writes a read may or must see. | Saying “consistent” without naming the model |
| **CQRS** | Separate command and query models, often with separate stores and projections. | Splitting every read/write for no measured reason |
| **Data ownership** | Exactly one bounded context is authoritative for a business fact. | Multiple services writing the same tables |

## D–H

| Term | Precise meaning | Common trap |
|---|---|---|
| **Data partitioning** | Divides data into independently addressable subsets by key, time, tenant, or dimension. | Assuming partitions distribute without a routing key |
| **Dead letter queue (DLQ)** | Durable holding area for messages that exhausted processing or retry policy. | Using it as an unmonitored garbage dump |
| **Distributed transaction** | Transaction whose participants coordinate across process or service boundaries. | Assuming local transactions remain globally ACID |
| **Durability** | Committed fact survives the failures in the durability contract. | Claiming durability without replication and region assumptions |
| **Eventual consistency** | If writes stop, replicas/read models eventually converge. | Giving no convergence bound or conflict behavior |
| **Event sourcing** | Persists domain events as truth and reconstructs state from them. | Equating it with an audit log |
| **Exactly once** | Guarantee scoped to a particular processing boundary and failure model. | Claiming an entire workflow is exactly once |
| **Failover** | Transitioning traffic or ownership to an alternate healthy path. | Treating it as instantaneous and free |
| **Fan-out** | One request/event causes work at many consumers. | Ignoring downstream load |
| **Fault domain** | Boundary within which failures correlate, such as an AZ. | Deploying replicas in the same domain |
| **Fencing token** | Monotonically increasing credential invalidating stale lease holders. | Using a TTL lease without fencing the resource |
| **Hot key** | One key receiving a disproportionate share of traffic. | Solving only average throughput |
| **Horizontal scaling** | Adding instances or partitions to increase capacity. | Assuming state moves automatically |
| **Idempotency** | Repeating an operation has the same intended effect as executing it once. | Using request IDs without scope and durable result |
| **Inverted index** | Mapping terms to documents for fast text retrieval. | Calling every index an inverted index |

## I–P

| Term | Precise meaning | Common trap |
|---|---|---|
| **Idempotent consumer** | Repeated event processing does not repeat the business effect. | Deduplicating only in memory |
| **Leader election** | Protocol coordinating which node acts as leader for a term. | Assuming election guarantees linearizable work |
| **Lease** | Time-bounded right to perform a protected operation. | Assuming leases cannot overlap after a pause |
| **Linearizability** | Each operation appears to take effect at one instant between invocation and response. | Treating serializability as equivalent |
| **Load shedding** | Intentionally rejecting/degrading work to preserve system health. | Calling generic HTTP 500 load shedding |
| **MTTR** | Mean time to restore service after failure. | Measuring only detection time |
| **Multi-region** | Serving from or replicating across two or more geographic regions. | Calling two AZs multi-region |
| **N+1 query** | One data access plus N accesses from lazy per-item loading. | Treating ORM convenience as free at scale |
| **Network partition** | Nodes that should communicate cannot reliably exchange messages. | Modeling only process crashes |
| **Outbox** | Transactionally records a business change and event to publish later. | Claiming DB write and broker publish are atomic without outbox/CDC |
| **PACELC** | Partition: availability vs consistency; else: latency vs consistency. | Using it as a product label |
| **Partition tolerance** | Continue according to a chosen policy when communication is interrupted. | Partitioning a healthy system deliberately |
| **Paxos** | Consensus family based on intersecting quorums of acceptors. | Treating Paxos as one simple algorithm |
| **Pipeline** | Sequence of stages with buffering between them. | Assuming stages have equal capacity |
| **Primary key** | Candidate key selected to identify a row uniquely. | Assuming it automatically distributes writes |

## Q–Z

| Term | Precise meaning | Common trap |
|---|---|---|
| **Quorum** | Subset large enough that intersecting quorums share a member. | Assuming `W + R > N` proves strong consistency |
| **Raft** | Consensus protocol around leader election, log replication, and safety. | Treating election as the entire protocol |
| **RPO** | Maximum acceptable data loss measured in time after an incident. | Reporting RPO without backup/replication design |
| **RTO** | Target time to restore a service or business function. | Equating RTO with detection time |
| **Saga** | Distributed business transaction of local steps and compensating actions. | Assuming every failure can be rolled back |
| **Service discovery** | Mechanism through which clients locate healthy instances. | Coupling clients to instance addresses |
| **Service mesh** | Data/control plane for service-to-service policy and telemetry. | Adding a mesh without policy need |
| **Sharding** | Distributing a dataset across independently owned partitions. | Treating replicas as shards |
| **Single writer** | One leader accepts writes for a partition. | Assuming it cannot scale sufficiently |
| **SLO** | Target for a service-level indicator over a window. | Measuring implementation instead of user value |
| **Split brain** | Multiple nodes independently believe they are authoritative for the same state/role. | Assuming fencing is unnecessary with TTLs |
| **Throughput** | Work completed per unit time. | Reporting average latency as throughput |
| **Token bucket** | Replenishing allowance with bounded burst capacity. | Equating token and fixed-window behavior |
| **Vertical scaling** | Increasing CPU, memory, I/O, or hardware of one node. | Assuming it solves indefinite growth |
| **Virtual waiting room** | Edge admission control bounding concurrent work entering a constrained path. | Treating queue depth alone as backpressure |
| **Write-through** | Cache write synchronously updates cache and durable store before success. | Assuming durability without the backing-store guarantee |
| **Write-back** | Cache write acknowledges first and persists later. | Ignoring data loss during cache failure |
| **Zero-downtime migration** | Phased change preserving service while compatibility is maintained. | Calling migration zero downtime without rollback |
