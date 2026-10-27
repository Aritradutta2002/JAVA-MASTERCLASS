# 12 — LLD Preparation

> Translate requirements into testable domain models, then evolve them toward distributed systems deliberately.

| # | Chapter | Implementation |
|---|---|---|
| 01 | [SOLID and Design Patterns](./01-SOLID-and-Design-Patterns.md) | Cross-cutting modeling guidance |
| 02 | [UML and Modeling Techniques](./02-UML-and-Modeling-Techniques.md) | Class, sequence, state, and component views |
| 03 | [Concurrency and Thread Safety](./03-Concurrency-and-Thread-Safety.md) | Atomicity, linearization, idempotency |
| 04 | [Parking Lot System](./04-Parking-Lot-System.md) | [ParkingLotSystem.java](./implementations/parkinglot/ParkingLotSystem.java) |
| 05 | [Elevator System](./05-Elevator-System.md) | [ElevatorSystem.java](./implementations/elevator/ElevatorSystem.java) |
| 06 | [Rate Limiter](./06-Rate-Limiter.md) | [RateLimiter.java](./implementations/ratelimiter/RateLimiter.java) |
| 07 | [URL Shortener](./07-URL-Shortener.md) | [UrlShortener.java](./implementations/urlshortener/UrlShortener.java) |
| 08 | [LRU Cache](./08-LRU-Cache.md) | [LruCache.java](./implementations/lrucache/LruCache.java) |
| 09 | [Chat System LLD](./09-Chat-System-LLD.md) | [ChatSystem.java](./implementations/chat/ChatSystem.java) |
| 10 | [Notification Dispatcher](./10-Notification-Dispatcher.md) | [NotificationDispatcher.java](./implementations/notification/NotificationDispatcher.java) |
| — | Banking ledger supporting model | [Ledger.java](./implementations/banking/Ledger.java) |

## Compile and Run

```bash
rm -rf /tmp/system-design-lld-classes
find SYSTEM_DESIGN/12-LLD-Preparation/implementations -name '*.java' -print0 \
  | xargs -0 javac --release 17 -d /tmp/system-design-lld-classes
java -cp /tmp/system-design-lld-classes banking.Ledger
java -cp /tmp/system-design-lld-classes chat.ChatSystem
```

## LLD Loop

1. Clarify actors, use cases, invariants, and non-functional constraints.
2. Sketch responsibilities before classes.
3. Draw state transitions and the critical sequence.
4. Choose the simplest pattern that names a real variability/invariant.
5. Specify concurrency and idempotency.
6. Implement domain behavior without framework dependencies.
7. Write deterministic examples and edge-case tests.
8. Explain how the design evolves at HLD scale.

## Interview Rubric

| Dimension | Weak | Strong |
|---|---|---|
| Requirements | Starts coding | Names actors, invariants, constraints |
| Model | DTOs/getters/setters | Behavior-rich objects with valid states |
| Patterns | Pattern names without reason | Pattern tied to a concrete variability |
| Concurrency | Ignored | Atomicity, linearization, and idempotency explicit |
| Evolution | “Use microservices” | Maps ports to database, queue, and services |
