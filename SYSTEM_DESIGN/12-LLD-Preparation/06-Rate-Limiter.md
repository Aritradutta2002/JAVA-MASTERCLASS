# 06 · Rate Limiter

> **Level:** Intermediate → Advanced  
> **Invariant:** Allowed requests never exceed the policy’s defined window semantics under concurrent checks.  
> **Java target:** JDK 17

## 1. Requirements

Consume quota under token, sliding-window, or fixed-window policies and report retry timing.

### Non-functional requirements

- Deterministic domain behavior and explicit error results.
- Bounded memory/time complexity stated for operations.
- Public behavior documented; concurrency guarantees are explicit.
- No framework, database, network, or clock dependency in the core model.

### Out of scope

Persistence, user-interface rendering, and distributed deployment are discussed only as LLD-to-HLD evolution.

## 2. Domain Model

RateLimiter uses Policy, Decision, Key, and Clock ports.

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

Decision is ALLOWED or REJECTED with remaining/retry metadata; no long lifecycle.

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

Strategy selects policy; decorator adds priority/concurrency; factory validates configuration.

| Pattern | Why it fits here | Rejected alternative |
|---|---|---|
| Domain Model | Rules and lifecycle are nontrivial | Transaction Script for simple CRUD |
| Repository port | Storage is replaceable | Direct collection dependency |
| Strategy | Policy variants need runtime selection | Long conditional chain |
| State pattern | Transition rules vary by state | Scattered boolean flags |
| Factory/Builder | Construction needs validation | Constructor with many nullable fields |

## 5. Concurrency and Complexity

Atomic compare-and-set/refill under lock or CAS; immutable decisions are safe to publish.

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

Redis implements atomic distributed bucket; local fallback and policy sync bound outage behavior.

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

Non-atomic get-then-set; using wall clock for durations; ambiguous retry consumption; unbounded key cardinality.

- Returning a mutable internal collection without defensive copying.
- Letting null bypass validation or create ambiguous “not found” behavior.
- Performing I/O inside a domain invariant.
- Retrying a non-idempotent command without a stable request identity.
- Using `double` for money or timestamps without an explicit representation contract.
- Assuming collection iteration order is a business guarantee.

## 9. Interview Preparation

### Beginner

Which policy allows bursts?

### Intermediate

Where is atomicity?

### Advanced / Senior

How do you handle time?

### Expert / Follow-up

Why strategy?

### Counter questions

- **“Why not getters/setters?”** — Setters permit invalid states; commands express allowed transitions.
- **“Why not a shared database singleton?”** — It couples lifecycle, concurrency, and test isolation.
- **“How would this scale?”** — First preserve aggregate invariants; only then partition storage and distribute coordination.

## 10. Hands-On Exercises

['- [ ] Implement token and sliding-window policies in Java.\n- [ ] Test boundary and concurrent behavior.\n- [ ] Model Redis atomic script and outage fallback.']

## 11. Revision Card

- **Invariant:** Allowed requests never exceed the policy’s defined window semantics under concurrent checks..
- **Core:** RateLimiter uses Policy, Decision, Key, and Clock ports.
- **State:** Decision is ALLOWED or REJECTED with remaining/retry metadata; no long lifecycle.
- **Concurrency:** Atomic compare-and-set/refill under lock or CAS; immutable decisions are safe to publish.
- **Evolution:** Redis implements atomic distributed bucket; local fallback and policy sync bound outage behavior.

**Related:** [LLD Index](./README.md) · **Case Studies](../11-HLD-Case-Studies/README.md)
