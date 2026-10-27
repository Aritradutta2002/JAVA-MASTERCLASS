# 02 — Distributed Systems

> What happens when messages are delayed, duplicated, reordered, lost, or partitioned.

| # | Chapter | Core question | Level |
|---|---|---|---|
| 01 | [CAP and PACELC](./01-CAP-and-PACELC.md) | What do we sacrifice during and outside a partition? | Beginner → Expert |
| 02 | [Consistency Models](./02-Consistency-Models.md) | What can a read observe? | Intermediate → Expert |
| 03 | [Distributed Transactions and 2PC](./03-Distributed-Transactions-and-2PC.md) | How do multiple owners commit or compensate? | Advanced → Expert |
| 04 | [Consensus, Raft, Paxos, and Quorums](./04-Consensus-Raft-Paxos-and-Quorums.md) | How do nodes agree on an ordered log? | Advanced → Expert |
| 05 | [Partitions, Split Brain, and Leader Election](./05-Partitions-Split-Brain-and-Leader-Election.md) | Who is allowed to act? | Intermediate → Expert |
| 06 | [Vector Clocks and Clock Synchronization](./06-Vector-Clocks-and-Clock-Synchronization.md) | How do we order events without trusting time? | Advanced → Expert |
| 07 | [Distributed Locks and Coordination](./07-Distributed-Locks-and-Coordination.md) | How do processes coordinate scarce work? | Advanced → Expert |

## Most Asked in Interviews

| Question | Expert answer must include |
|---|---|
| Explain CAP. | Partition-time C-vs-A choice; operation scope; not “pick two” |
| What is Raft? | Terms, elections, majority replication, log safety, snapshots |
| Why use saga instead of 2PC? | Ownership, atomicity scope, blocking/coupling, compensation semantics |
| How do you avoid split brain? | Epoch/fencing/quorum and the protected resource |
| How do you lock across services? | Lease safety, expiry, fencing, critical-section boundary |

## Weakest Areas to Brush Up

- [ ] I can draw a Raft election and commit timeline.
- [ ] I can explain why `W + R > N` is not a complete linearizability proof.
- [ ] I can design a fenced lock for an external API.
- [ ] I can compare vector clocks and HLC.

## Design Drill

- [ ] Choose a consistency policy for payment, inventory, likes, and follower count.
- [ ] Explain the partition behavior and repair path for each.
- [ ] Trace one duplicate command through your design.

**Previous:** [01 — Fundamentals](../01-System-Design-Fundamentals/README.md) · **Next:** [03 — Database Design](../03-Database-Design/README.md) · **Up:** [Hub](../README.md)
