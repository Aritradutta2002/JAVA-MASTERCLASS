# 08 — Microservices with Spring Boot · Interview Notes Index

> Microservices are **not** a technology — they are a way of *organising teams around business
> capabilities* and accepting that your calls will now cross a network. The single idea that makes
> the whole topic click: **every remote call is a distributed-systems problem wearing a method
> signature.** The moment `orderService.place(order)` becomes an HTTP call to another JVM, you have
> lost in-process transactions, in-process stack traces, and in-process function calls — and you
> must buy back **timeouts, retries, circuit breakers, idempotency, tracing and deployment
> independence** with the code you write in this folder.

| Priority | Chapters | Level | Read Time | Why It Matters |
|----------|----------|-------|-----------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · Monolith vs Microservices & Service Discovery](01-Monolith-vs-Microservices-and-Service-Discovery.md) | 🟢→⚫ | 60 min | The honest comparison, the **modular monolith** as the default, the **distributed monolith** anti-pattern, DDD bounded contexts, Conway's Law, and Eureka / K8s DNS / Consul service discovery with the `ServiceInstanceListSupplier` internals |
| ⭐⭐⭐⭐ | [02 · Service Communication & API Contracts](02-Service-Communication-and-API-Contracts.md) | 🟡→🔴 | 50 min | REST vs gRPC vs messaging, synchronous vs asynchronous calls, contract-first OpenAPI, versioning, and backward compatibility |
| ⭐⭐⭐⭐ | [03 · Service Discovery, Config & API Gateway](03-Service-Discovery-Config-and-API-Gateway.md) | 🟡→🔴 | 50 min | Eureka, Consul, Kubernetes DNS, Spring Cloud Config, gateway routing, rate limiting, and the BFF pattern |
| ⭐⭐⭐⭐⭐ | [04 · Circuit Breaker & Resilience](04-Circuit-Breaker-and-Resilience.md) | 🟡→🔴 | 55 min | Timeouts, retries with jitter, circuit breakers, bulkheads, rate limiting, and graceful degradation |
| ⭐⭐⭐⭐ | [05 · Distributed Tracing & Observability](05-Distributed-Tracing-and-Observability.md) | 🔴→⚫ | 45 min | Micrometer Tracing, OpenTelemetry, W3C `traceparent`, span design, logs/metrics/traces, and SLOs |
| ⭐⭐⭐⭐⭐ | [06 · Distributed Data Management & Saga Patterns](06-Distributed-Data-Management-and-Saga-Patterns.md) | 🔴→⚫ | 50 min | Dual writes, transactional outbox, CDC, idempotent consumers, choreography, orchestration, and compensations |

> **Total:** 6 chapters · ~300 min of reading · 70+ interview questions · 38 counter-questions ·
> 52 MCQs · 18 production debugging scenarios

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| Monolith vs microservices — when do you **not** split? | Answering "always microservices" — the trap is the **distributed monolith**: services that must be deployed together and share a database, so you pay every distributed-systems cost and get **none** of the independence benefit |
| What is a bounded context and how does it decide a service boundary? | Describing DDD abstractly — the practical rule is **split by business capability, never by table**; "one service per database table" is the classic wrong answer |
| What exactly does a service registry do? | Saying "it stores IPs" — the real content is the **lease/heartbeat contract** (renew every 30 s, expire after 90 s) and the fact that the registry is a **single point of failure** you must design around |
| What is Eureka's self-preservation mode? | "It keeps dead instances alive" — the point is **why**: during a network partition, too many renewals fail, Eureka would evict everyone, so it deliberately keeps possibly-dead instances so the cluster can heal |
| Why is a circuit breaker useless without a timeout? | Explaining only the states — the real answer: with no timeout the call **never returns**, so the breaker never records a failure and stays CLOSED forever while your thread pool dies |
| What is the correct order of resilience patterns? | Listing them alphabetically — the answer is a **dependency order**: timeout → retry → circuit breaker → bulkhead → fallback, because a retry without a timeout multiplies latency and a breaker without a timeout never trips |
| Why did Netflix retire Zuul? | "Because it was slow" — the real reason is **scope**: Zuul 1 was tied to Netflix's own stack and never became a general Spring Cloud gateway; `spring-cloud-gateway` replaced it, and Zuul was **removed from `spring-cloud-netflix`** |
| Why is Spring Cloud Gateway reactive and not Spring MVC? | "For performance" — the reason is that **blocking in a Netty event loop stalls every other request on that thread**; a blocking JDBC call in a gateway filter is the classic production bug |
| What is the dual-write problem? | Describing "writing to DB and to Kafka" — the trap is that it is **two operations**, and a crash between them leaves the state and the event permanently divergent; the fix is the **transactional outbox** |
| What is a saga and how does it roll back? | Saying "it rolls back" — the trap: a saga **does not roll back**, it issues **new forward compensating actions**; and a compensation can itself fail, which is the dead-letter case nobody mentions |
| Why set `FAIL_ON_UNKNOWN_PROPERTIES=false`? | "To be lenient" — the real reason is the **rolling deployment**: the producer adds a field first, and the older consumers must not break when they see it |
| How do you trace a request across 5 services? | "Use logs" — the real answer is **context propagation**: the W3C `traceparent` header must be forwarded on every hop, and the trace id must be put in the MDC so logs and traces join up |

---

## 🧠 The Microservices World, in One Picture

```text
                       ┌───────────────────────────────────────────────────────────┐
                       │                     THE EDGE                            │
                       │   API Gateway (Spring Cloud Gateway — REACTIVE, Netty)  │
                       │   • routing + path rewrite      • rate limit (429)       │
                       │   • OAuth2 token validation    • retry + circuit breaker │
                       │   • request/response transform • canary weighted routing  │
                       └───────────────┬───────────────────────────┬───────────────┘
                                       │ traceparent: 00-4bf92f…-00f067…-01
                                       ▼                           ▼
        ┌──────────────────────────────────────┐   ┌──────────────────────────────────────┐
        │        ORDERS SERVICE                 │   │        PAYMENTS SERVICE              │
        │  ┌────────────────────────────────┐  │   │  ┌────────────────────────────────┐  │
        │  │ @CircuitBreaker inventoryApi    │  │   │  │ @CircuitBreaker ledgerApi       │  │
        │  │ @Retry      (maxAttempts=3)     │  │   │  │ @Bulkhead (thread pool)         │  │
        │  │ @TimeLimiter(2s)  ← OUTERMOST  │  │   │  │ @TimeLimiter(2s)                │  │
        │  └────────────────────────────────┘  │   │  └────────────────────────────────┘  │
        │  owns DB: orders_db (nobody else)     │   │  owns DB: payments_db                 │
        └───────────────┬──────────────────────┘   └───────────────┬──────────────────────┘
                        │                                           │
        ┌───────────────▼───────────────┐           ┌───────────────▼───────────────┐
        │   SERVICE DISCOVERY            │           │   SERVICE DISCOVERY            │
        │  Eureka │ K8s DNS │ Consul     │           │  Eureka │ K8s DNS │ Consul     │
        │  lease 30s / expiry 90s        │           │  lease 30s / expiry 90s        │
        └───────────────────────────────┘           └───────────────────────────────┘

  ★ THE ONE-LINE VERSION FOR THE INTERVIEW
  ──────────────────────────────────────────
  "Splitting a monolith buys me INDEPENDENT DEPLOYMENT and SCALING PROFILE ISOLATION,
   and it charges me NETWORK FAILURE, DISTRIBUTED TRANSACTIONS and OBSERVABILITY TAX.
   So I split only where a business boundary AND a scaling boundary AND a team boundary
   all line up — and where they don't line up, I build a MODULAR MONOLITH and keep the
   in-process call."
```

> **Caption:** The **left column is the benefit** and the **right column is the bill**. Every
> architectural decision in this folder is a judgement about whether the benefit on the left
> outweighs the bill on the right for *this particular boundary* — and the three mechanisms in
> the middle (gateway, discovery, resilience) are the price of admission for having crossed
> the network boundary at all.

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **The modular monolith vs the microservices spectrum.** Not "monolith bad, microservices good" — the honest answer is that a well-modularised monolith is the *default*, and a monolith with **no module boundaries** is the actual anti-pattern.
- [ ] **The distributed monolith.** The definition (services that must be deployed together *and* share a database) and why it is strictly worse than either extreme — all the distributed-systems costs, none of the independence.
- [ ] **Conway's Law and the inverse Conway Manoeuvre.** The law states architecture follows team structure; the *manoeuvre* is deliberately using architecture to change how teams communicate.
- [ ] **Bounded contexts.** The heuristic, the "split by table" anti-pattern, and how a shared database quietly welds two services back into one.
- [ ] **The cost ledger with numbers.** Same-DC REST ≈ 1–5 ms; cross-region ≈ 30–80 ms; a saga replaces an ACID transaction with eventual consistency and a compensation path.
- [ ] **Self-registration vs third-party registration**, and client-side vs server-side load balancing — and which one Spring Cloud LoadBalancer actually implements.
- [ ] **Eureka's lease numbers**: renewal every **30 s**, expiration after **90 s**, registry fetch every **30 s**. Know why 90 > 30 × 2 (one missed heartbeat is tolerated, two is not).
- [ ] **Self-preservation mode and the 85 % threshold.** What it protects against (a network partition evicting the whole cluster) and what it costs you (stale instances, calls to dead pods).
- [ ] **`ServiceInstanceListSupplier`** — the extension point that replaced Ribbon. Know `DiscoveryClientServiceInstanceListSupplier`, the caching supplier and the zone-preference supplier.
- [ ] **K8s-native discovery vs Eureka.** `K8sServiceInstanceListSupplier`, the DNS-based `SimpleDiscoveryClient`, and why most K8s shops delete Eureka entirely.
- [ ] **Spring Cloud Gateway is WebFlux-based.** You cannot put a blocking JDBC call in a gateway filter without stalling the Netty event loop.
- [ ] **Gateway predicates vs filters** — `Path`, `Method`, `Header`, `Query`, `Host`, `Cookie`; `GlobalFilter` vs `GatewayFilter`; `NettyRoutingFilter` as the terminal filter.
- [ ] **`StripPrefix` vs `RewritePath`** — the single most common cause of a gateway 404 while the service is perfectly healthy.
- [ ] **The Config Server's `{application}` / `{profile}` / `{label}` resolution** and what `/order-service/prod/main` actually maps to in the Git repo.
- [ ] **The bootstrap context.** Why `spring-cloud-starter-bootstrap` exists: the config server's *own* properties must resolve before the main `Environment` is ready.
- [ ] **`{cipher}` config encryption** and the symmetric-key caveat — change the key and the value is unrecoverable.
- [ ] **The resilience pattern order**: timeout → retry → circuit breaker → bulkhead → fallback, and *why* each one depends on the previous.
- [ ] **The three breaker states** and the exact transition conditions, including `minimumNumberOfCalls` and why a breaker must see N calls before it can trip.
- [ ] **Retry amplification.** 3 layers × 3 attempts = **27×** load on the dependency. This is the number that ends careers.
- [ ] **The bulkhead types.** The **semaphore** bulkhead blocks the *caller's* thread; the **thread-pool** bulkhead rejects fast.
- [ ] **`RestClient`** (Spring Framework 6.1 / Boot 3.2+) — the modern synchronous client and the recommendation for new code over `RestTemplate`.
- [ ] **Every outbound call needs a connect timeout AND a read timeout.** "No timeout" turns a slow dependency into a Tomcat thread-pool exhaustion.
- [ ] **`FAIL_ON_UNKNOWN_PROPERTIES=false`** and the "evolve the contract, never break it" rule for rolling deployments.
- [ ] **Saga choreography vs orchestration**, and that a saga's rollback is a *new forward action*, not a rollback.
- [ ] **The transactional outbox** and the dual-write problem it solves.
- [ ] **W3C `traceparent` format** — `00-<trace-id>-<span-id>-<flags>` — and that it must be forwarded on every hop.

---

## 🧪 Coding Drill

- [ ] Stand up a single-node **Eureka Server** (`@EnableEurekaServer`) and two **Eureka Client** instances of the same service on different ports; prove from the Eureka dashboard that both instances appear, and that stopping one removes it within ~90 s.
- [ ] Flip `eureka.server.renewal-percent-threshold` to `0.99`, kill the network to the server, and capture the **exact self-preservation warning** from the log.
- [ ] Implement a custom `ServiceInstanceListSupplier` that prefers instances in the **local zone**, and prove the traffic split with the `X-Instance` response header.
- [ ] Write a `@LoadBalanced RestTemplate` call and log the **resolved URI** with a `ClientHttpRequestInterceptor` — then delete Eureka and observe the exact failure message.
- [ ] Build a **Spring Cloud Gateway** with three routes: a path rewrite, a `RequestRateLimiter` keyed by a header, and a `Retry` filter — then fire 20 requests and capture the exact **429** body and `Retry-After` header.
- [ ] Stand up a **Spring Cloud Config Server** on a Git backend and `curl` `/order-service/prod/main`; then change the file, commit, and prove the client picks it up via `/actuator/refresh` + `@RefreshScope`.
- [ ] Configure a **Resilience4j** breaker with `slidingWindowType=TIME_BASED`, `minimumNumberOfCalls=10`, `failureRateThreshold=50`, and attach a `CircuitBreakerEventConsumer` that logs every state transition.
- [ ] Write a **chaos test** with WireMock that fails 100 % of calls for 30 s and assert the breaker moves `CLOSED → OPEN → HALF_OPEN` and that the fallback returns the degraded payload.
- [ ] Build a `RestClient` with a **connect timeout + read timeout** and a `ClientHttpRequestInterceptor` that copies the inbound `traceparent` header outbound; assert the value survives the hop.
- [ ] Implement a **transactional outbox**: a publisher that polls un-published rows with `SELECT ... FOR UPDATE SKIP LOCKED` and publishes them, then marks them published.
- [ ] Prove the **`FAIL_ON_UNKNOWN_PROPERTIES`** behaviour with a test: deserialise a JSON body containing a field the DTO does not have, with the flag both on and off.
- [ ] Write a saga with **3 steps and 1 compensation**, and assert the exact database state after the failure — the order row must be `COMPENSATED`, not `PENDING`.

---

**Related folders:** ← [07 · OAuth2 & OpenID Connect](../07-OAuth2-OpenID-Connect/README.md) · → [09 · Messaging Systems](../09-Messaging-Systems/README.md) · ↑ [Master Index](../README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
