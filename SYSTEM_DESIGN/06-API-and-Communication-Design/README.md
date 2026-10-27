# 06 — API and Communication Design

> Resource contracts, typed RPC, live connections, routing, and traffic control.

| # | Chapter | Best fit | Level |
|---|---|---|---|
| 01 | [REST and API Contracts](./01-REST-and-API-Contracts.md) | Public resource APIs | Beginner → Expert |
| 02 | [GraphQL](./02-GraphQL.md) | Client-shaped graph reads | Intermediate → Expert |
| 03 | [gRPC and Protobuf](./03-gRPC-and-Protobuf.md) | Internal typed RPC | Intermediate → Expert |
| 04 | [WebSockets and SSE](./04-WebSockets-and-SSE.md) | Live updates | Intermediate → Expert |
| 05 | [API Gateway and Reverse Proxy](./05-API-Gateway-and-Reverse-Proxy.md) | Edge policy/routing | Intermediate → Expert |
| 06 | [Load Balancers and Service Discovery](./06-Load-Balancers-and-Service-Discovery.md) | Endpoint selection | Intermediate → Expert |
| 07 | [Rate Limiting and Circuit Breakers](./07-Rate-Limiting-and-Circuit-Breakers.md) | Admission/failure isolation | Advanced → Expert |

## Protocol Decision

| Need | Default | Reconsider when |
|---|---|---|
| Public CRUD/contracts | REST | GraphQL for varied shapes; gRPC for internal |
| Internal low-latency call | gRPC | REST for debuggable edge APIs |
| Server push | SSE | Bidirectional interaction needs WebSocket |
| Edge routing/auth | Gateway | Business auth stays with owner |

## Practical Drill

- [ ] Design checkout APIs with idempotency and cursor pagination.
- [ ] Define deadline/retry budgets across HTTP → gRPC → DB.
- [ ] Model a WebSocket reconnect/resume protocol.

**Previous:** [05 — Messaging](../05-Messaging-and-Event-Driven-Systems/README.md) · **Next:** [07 — Security](../07-Security-Design/README.md) · **Up:** [Hub](../README.md)
