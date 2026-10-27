# 03 · Concurrency and Thread Safety

> **Level:** Advanced → Expert  
> **Invariant:** Concurrent commands must not create an invalid aggregate or duplicate business effect.  
> **Java target:** JDK 17

## 1. Requirements

Model concurrent domain commands with explicit atomicity, linearization points, and idempotency.

### Non-functional requirements

- Deterministic domain behavior and explicit error results.
- Bounded memory/time complexity stated for operations.
- Public behavior documented; concurrency guarantees are explicit.
- No framework, database, network, or clock dependency in the core model.

### Out of scope

Persistence, user-interface rendering, and distributed deployment are discussed only as LLD-to-HLD evolution.

## 2. Domain Model

Thread-safe collections protect their own data, not multi-step domain invariants.

```mermaid
classDiagram
    class DomainService
    class DomainModel
    class Repository
    class Clock
    DomainService --> DomainModel : enforces
    DomainService --> Repository : stores
    DomainService --> Clock : reads time
    Repository <|.. InMemoryRepository
    Clock <|.. SystemClock
```

**Caption:** dependencies point toward domain abstractions; infrastructure implements ports so the model is testable.

### Responsibilities

| Type | Responsibility | Must not do |
|---|---|---|
| Application service | Orchestrate use case and transaction boundary | Put business rules in controllers |
| Domain model | Validate invariants and state transitions | Depend on framework/database |
| Repository port | Store/retrieve aggregate | Decide whether a command is valid |
| Clock port | Supply current instant | Hide clock calls in unrelated code |

## 3. State and Lifecycle

Locks, atomics, immutability, and queues address different contention shapes.

```mermaid
stateDiagram-v2
    [*] --> New
    New --> Active : activate()
    Active --> Suspended : suspend()
    Suspended --> Active : resume()
    Active --> Closed : close()
    Suspended --> Closed : close()
    Closed --> [*]
```

**Caption:** terminal states reject further transitions; every transition is checked in the domain model rather than inferred by the caller.

## 4. Pattern Selection

Use immutable snapshots and narrow synchronized critical sections; avoid global locks.

| Pattern | Why it fits here | Rejected alternative |
|---|---|---|
| Domain Model | Rules and lifecycle are nontrivial | Transaction Script for simple CRUD |
| Repository port | Storage is replaceable | Direct collection dependency |
| Strategy | Policy variants need runtime selection | Long conditional chain |
| State pattern | Transition rules vary by state | Scattered boolean flags |
| Factory/Builder | Construction needs validation | Constructor with many nullable fields |

## 5. Concurrency and Complexity

Version checks/CAS support optimistic concurrency; command IDs support idempotent retries.

### Complexity table

| Operation | Time | Space | Notes |
|---|---:|---:|---|
| Create | O(1) | O(1) | ID supplied/generated outside core |
| Lookup | O(1) average | O(1) | Hash index by identity |
| Update | O(1) average | O(1) | Domain validation before mutation |
| History/list | O(n) | O(n) returned | Bounded/paginated at application edge |

**Concurrency contract:** aggregate instances are not thread-safe unless the implementation explicitly says so. Repositories may synchronize their index. If concurrent commands target one aggregate, serialize by aggregate ID in the application service or use optimistic version checks.

## 6. Java 17 Implementation

The runnable source is under [`implementations/`](./implementations/). Public methods return values or typed domain exceptions; they do not print hidden side effects.

```java
// Representative style: validate first, mutate second, record third.
public Result execute(Command command) {
    Objects.requireNonNull(command, "command");
    DomainState before = state;
    requireAllowed(before, command);
    state = transition(before, command);
    return Result.success(before, state);
}
```

**Caption:** the transition is atomic with respect to the aggregate because validation happens before the state assignment.

## 7. LLD to HLD Evolution

Move in-memory maps to partitioned stores with version columns; use lease/fencing only when external side effects need ownership.

```mermaid
flowchart LR
    Client --> Gateway[API Gateway]
    Gateway --> App[Application Service]
    App --> Domain[Domain Model]
    App --> Repo[(Database / Cache)]
    App --> Events[Outbox / Events]
```

**Caption:** the same domain model can remain inside a service while its repository, clock, and event ports become distributed adapters.

## 8. Common Mistakes

Assuming `ConcurrentHashMap` makes a multi-map operation atomic; locking after reading; double-checked publication without safe publication; ignoring duplicate requests.

- Returning a mutable internal collection without defensive copying.
- Letting null bypass validation or create ambiguous “not found” behavior.
- Performing I/O inside a domain invariant.
- Retrying a non-idempotent command without a stable request identity.
- Using `double` for money or timestamps without an explicit representation contract.
- Assuming collection iteration order is a business guarantee.

## 9. Interview Preparation

### Beginner

What is the linearization point?

### Intermediate

Why is a concurrent map not enough?

### Advanced / Senior

How do you make retries safe?

### Expert / Follow-up

When is a queue better than a lock?

### Counter questions

- **“Why not getters/setters?”** — Setters permit invalid states; commands express allowed transitions.
- **“Why not a shared database singleton?”** — It couples lifecycle, concurrency, and test isolation.
- **“How would this scale?”** — First preserve aggregate invariants; only then partition storage and distribute coordination.

## 10. Hands-On Exercises

['- [ ] Prove a lost-update bug then fix with version/CAS.\n- [ ] Implement idempotent command handling.\n- [ ] Run a concurrency stress test and assert invariants.']

## 11. Revision Card

- **Invariant:** Concurrent commands must not create an invalid aggregate or duplicate business effect..
- **Core:** Thread-safe collections protect their own data, not multi-step domain invariants.
- **State:** Locks, atomics, immutability, and queues address different contention shapes.
- **Concurrency:** Version checks/CAS support optimistic concurrency; command IDs support idempotent retries.
- **Evolution:** Move in-memory maps to partitioned stores with version columns; use lease/fencing only when external side effects need ownership.

**Related:** [LLD Index](./README.md) · **Case Studies](../11-HLD-Case-Studies/README.md)
