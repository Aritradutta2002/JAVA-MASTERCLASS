# 08 — Microservices with Spring Boot

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★★ (the topic that decides senior vs. mid offers)
> **Prerequisite:** [07 · OAuth2 & OIDC](../07-OAuth2-OIDC/README.md)
> **Last Updated:** 2026

---

## 🎯 Why This Topic Is Separate

Anyone can split a monolith into ten Spring Boot apps. That is the easy part and it is
usually the wrong part. What interviewers actually probe is whether you know **when not
to split**, and whether you can name the five problems you just bought yourself:

1. **The network is now in your call stack.** Every in-process method call became a
   remote call that can be slow, fail, retry, or arrive twice.
2. **There is no shared transaction.** ACID stops at the service boundary; you now need
   outbox, idempotency and sagas.
3. **Nothing knows where anything is.** Hosts change; you need discovery and a gateway.
4. **One slow service can kill the fleet.** You need timeouts, circuit breakers, bulkheads.
5. **You cannot see a request.** You need correlation ids, tracing, metrics.

This topic covers all five, and it is deliberately blunt about cost. The correct answer
to "should we go microservices?" is often **"no, not yet."**

---

## 📚 Chapters

| # | Chapter | Core Idea | Difficulty |
|---|---------|-----------|------------|
| 01 | [Monolith vs Microservices & When to Use Which](./01-Monolith-vs-Microservices-and-When-to-Use-Which.md) | The real trade-offs, the modular monolith, decomposition by business capability, Conway's law, the migration path | ⭐⭐ |
| 02 | [Service Communication & API Contracts](./02-Service-Communication-and-API-Contracts.md) | REST vs gRPC vs messaging, sync vs async, contract-first OpenAPI, versioning, backward compatibility | ⭐⭐⭐ |
| 03 | [Service Discovery, Config & API Gateway](./03-Service-Discovery-Config-and-API-Gateway.md) | Eureka/Consul/K8s DNS, Spring Cloud Config, the gateway as edge concern, BFF | ⭐⭐⭐ |
| 04 | [Circuit Breaker & Resilience](./04-Circuit-Breaker-and-Resilience.md) | Timeouts, retries with jitter, circuit breakers, bulkheads, rate limiting, graceful degradation | ⭐⭐⭐⭐ |
| 05 | [Distributed Tracing & Observability](./05-Distributed-Tracing-and-Observability.md) | Micrometer Tracing, OpenTelemetry, W3C `traceparent`, span design, logs/metrics/traces together, SLOs | ⭐⭐⭐⭐ |
| 06 | [Distributed Data Management & Saga Patterns](./06-Distributed-Data-Management-and-Saga-Patterns.md) | The dual-write problem, transactional outbox, CDC, idempotent consumers, choreography vs orchestration, compensations | ⭐⭐⭐⭐⭐ |

---

## 🧠 The One Mental Model That Covers Everything

```text
        A MICROSERVICE IS A BOUNDARY, NOT A DEPLOYMENT UNIT.

   ┌──────────────────────────────────────────────────────────────┐
   │  the boundary you actually care about:                       │
   │                                                              │
   │   • one team owns it                                         │
   │   • one database, owned exclusively                          │
   │   • one business capability                                  │
   │   • its own release cadence                                  │
   └──────────────────────────────────────────────────────────────┘

   Everything else is a consequence, not a goal:

   separate repo?       maybe
   separate process?    usually
   separate database?   ALWAYS (or you didn't split anything)
   separate team?       ideally, one service per team — not one team per service
```

**The corollary that wins interviews:**

```text
   distributed system cost  ∝  (number of boundaries)²

   2 services →  1 integration
   5 services → 10 integrations
   10 services → 45 integrations
```

Every boundary you add multiplies the number of failure paths, contract negotiations and
deployment orderings. That is why **"start with a modular monolith"** is a technically
correct answer, not a cop-out.

---

## 📊 The Design Decision Matrix

| Decision | Default | Alternative | Trade-off |
|---|---|---|---|
| **Architecture** | Modular monolith | Microservices | Team autonomy vs operational cost |
| **Decomposition** | By business capability | By technical layer (**wrong**) | Change locality vs reuse |
| **Sync style** | REST for queries | gRPC for internal hot paths | Tooling vs latency/typing |
| **Async style** | Kafka for domain events | RabbitMQ for task queues | Replay/ordering vs routing flexibility |
| **Discovery** | Kubernetes DNS | Eureka / Consul | Simplicity vs client-side balancing |
| **Config** | K8s ConfigMap + Secrets | Spring Cloud Config | Native vs dynamic refresh |
| **Edge** | Spring Cloud Gateway | Ingress + per-service auth | Central policy vs blast radius |
| **Resilience** | Timeout + retry + breaker | Bulkhead + rate limit too | Latency under failure vs complexity |
| **Tracing** | OpenTelemetry → OTLP | Micrometer-only | Vendor neutrality vs simplicity |
| **Consistency** | Eventual + outbox | Single-writer per aggregate | Scale vs strictness |
| **Saga style** | Choreography (≤3 steps) | Orchestration (branches/audit) | Coupling vs visibility |

---

## ⚠️ The Traps (the ones that cost people interviews)

| Trap | Wrong answer | Right answer |
|---|---|---|
| "Microservices are more scalable" | "Yes, always" | A **modular monolith scales fine** for most teams; microservices scale *organisations*, not throughput |
| "We share one database for simplicity" | "It's pragmatic" | You have a **distributed monolith** — coupled deploys, coupled schema, worst of both |
| "Each service has its own DB, so consistency is fine" | "Yes" | **Eventual consistency** — needs outbox, idempotency, sagas |
| "We call 5 services synchronously on the request path" | "It's simple" | Latency multiplies and **availability multiplies down** |
| "Retry on any error" | "Yes, always retry" | Retrying non-idempotent POSTs **duplicates side effects**; classify errors |
| "Circuit breaker handles slowness" | "Yes" | It doesn't help if **timeouts are missing** — the breaker never trips in time |
| "The gateway validates JWTs, so services don't need to" | "Correct" | Defence in depth: services still enforce **authorisation** and are reachable internally |
| "Kafka gives exactly-once" | "Yes" | At-least-once + **idempotent consumer** = effectively once |
| "We'll add tracing later" | "Sure" | Retrofitting correlation ids means **re-touching every call site** |
| "More services = more senior" | "Obviously" | The senior answer is often **fewer, better-bounded** services |
| "Service per entity (User, Order, Item)" | "Yes, DDD" | That is **CRUD partitioning**, not decomposition; it creates chatty coupling |
| "Shared library for domain models" | "DRY" | Couples releases; share **contracts**, not entities |

---

## 🎤 The 20 Questions to Expect

| # | Question | Where |
|---|---|---|
| 1 | Monolith vs microservices — how do you decide? | [Ch 01](./01-Monolith-vs-Microservices-and-When-to-Use-Which.md) |
| 2 | What is a modular monolith and why start there? | [Ch 01](./01-Monolith-vs-Microservices-and-When-to-Use-Which.md) |
| 3 | How do you decompose a monolith into services? | [Ch 01](./01-Monolith-vs-Microservices-and-When-to-Use-Which.md) |
| 4 | What is the strangler fig pattern? | [Ch 01](./01-Monolith-vs-Microservices-and-When-to-Use-Which.md) |
| 5 | REST or gRPC between services? | [Ch 02](./02-Service-Communication-and-API-Contracts.md) |
| 6 | Sync or async communication? | [Ch 02](./02-Service-Communication-and-API-Contracts.md) |
| 7 | How do you version an API without breaking clients? | [Ch 02](./02-Service-Communication-and-API-Contracts.md) |
| 8 | What is a contract-first approach? | [Ch 02](./02-Service-Communication-and-API-Contracts.md) |
| 9 | How does service discovery work? | [Ch 03](./03-Service-Discovery-Config-and-API-Gateway.md) |
| 10 | Why do you need an API gateway? | [Ch 03](./03-Service-Discovery-Config-and-API-Gateway.md) |
| 11 | What is the BFF pattern? | [Ch 03](./03-Service-Discovery-Config-and-API-Gateway.md) |
| 12 | What is a circuit breaker and what are its states? | [Ch 04](./04-Circuit-Breaker-and-Resilience.md) |
| 13 | What is the retry storm / thundering herd problem? | [Ch 04](./04-Circuit-Breaker-and-Resilience.md) |
| 14 | How do you prevent cascading failures? | [Ch 04](./04-Circuit-Breaker-and-Resilience.md) |
| 15 | How do you trace a request across 8 services? | [Ch 05](./05-Distributed-Tracing-and-Observability.md) |
| 16 | What are the three pillars of observability? | [Ch 05](./05-Distributed-Tracing-and-Observability.md) |
| 17 | How do you keep data consistent across services? | [Ch 06](./06-Distributed-Data-Management-and-Saga-Patterns.md) |
| 18 | What is the transactional outbox pattern? | [Ch 06](./06-Distributed-Data-Management-and-Saga-Patterns.md) |
| 19 | Choreography or orchestration for sagas? | [Ch 06](./06-Distributed-Data-Management-and-Saga-Patterns.md) |
| 20 | How do you make a consumer idempotent? | [Ch 06](./06-Distributed-Data-Management-and-Saga-Patterns.md) |

---

## 🛠️ The Code You'll Actually Write

| Class / Component | What it does | Where |
|---|---|---|
| `@EnableEurekaClient` / K8s DNS config | Registration and lookup | [Ch 03](./03-Service-Discovery-Config-and-API-Gateway.md) |
| `RouteLocator` / gateway YAML | Edge routing, filters, auth | [Ch 03](./03-Service-Discovery-Config-and-API-Gateway.md) |
| `WebClient` + `@LoadBalanced` | Client-side load-balanced calls | [Ch 02](./02-Service-Communication-and-API-Contracts.md) |
| `RestClient` with timeouts | The sane default for sync calls | [Ch 02](./02-Service-Communication-and-API-Contracts.md) |
| `@CircuitBreaker`, `@Retry`, `@Bulkhead` | Resilience4j annotations | [Ch 04](./04-Circuit-Breaker-and-Resilience.md) |
| `TimeLimiter` + `ThreadPoolBulkhead` | Isolation of slow dependencies | [Ch 04](./04-Circuit-Breaker-and-Resilience.md) |
| `Tracer` / `ObservationRegistry` | Manual spans for business operations | [Ch 05](./05-Distributed-Tracing-and-Observability.md) |
| `OutboxWriter` + `OutboxRelay` | Atomic DB write + event publish | [Ch 06](./06-Distributed-Data-Management-and-Saga-Patterns.md) |
| `@KafkaListener` + `processed_events` | Idempotent consumer | [Ch 06](./06-Distributed-Data-Management-and-Saga-Patterns.md) |
| `OrderSagaOrchestrator` | Persisted saga with compensations | [Ch 06](./06-Distributed-Data-Management-and-Saga-Patterns.md) |
| `DefaultErrorHandler` + DLT | Retry classification and dead letters | [Ch 06](./06-Distributed-Data-Management-and-Saga-Patterns.md) |

---

## 🔗 The Spring Boot 3 Setup (the minimum)

```yaml
spring:
  application:
    name: order-service
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 500ms
            readTimeout: 1200ms
resilience4j:
  circuitbreaker:
    instances:
      inventoryClient:
        slidingWindowSize: 20
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 5
  retry:
    instances:
      inventoryClient:
        maxAttempts: 3
        waitDuration: 200ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions: [java.io.IOException, java.util.concurrent.TimeoutException]
management:
  tracing:
    sampling:
      probability: 0.1        # 100% in staging, sampled in prod
  otlp:
    tracing:
      endpoint: ${OTLP_ENDPOINT:http://otel-collector:4318/v1/traces}
```

```java
@Configuration
class ResilienceConfig {
    @Bean
    RestClient inventoryClient(RestClient.Builder builder) {
        return builder
            .baseUrl("http://inventory-service")
            .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                    .withConnectTimeout(Duration.ofMillis(500))
                    .withReadTimeout(Duration.ofMillis(1200))))
            .build();
    }
}
```

> **Non-negotiable:** a timeout on *every* outbound call. A default `RestClient`/`WebClient`
> with no read timeout is the single most common cause of thread-pool exhaustion in
> production microservices.

---

## 📐 The Reference Architecture

```text
                          ┌──────────────┐
   mobile / web ─────────▶│ API GATEWAY  │  authN, rate limit, routing, CORS
                          └──────┬───────┘
                                 │
        ┌────────────────────────┼────────────────────────┐
        ▼                        ▼                        ▼
  ┌───────────┐            ┌───────────┐            ┌───────────┐
  │  order    │            │  payment  │            │ inventory │
  │  -------  │  sync/REST │  -------  │  sync/REST │  -------  │
  │  own DB   │◀──────────▶│  own DB   │◀──────────▶│  own DB   │
  └─────┬─────┘            └─────┬─────┘            └─────┬─────┘
        │ outbox                 │ outbox                 │ outbox
        └────────────┬───────────┴────────────────────────┘
                     ▼
              ┌──────────────┐        ┌────────────────────┐
              │    KAFKA     │───────▶│ consumers/projections│
              │ domain.events│        └────────────────────┘
              └──────────────┘
                     │
        ┌────────────▼─────────────┐
        │ OpenTelemetry collector  │──▶ traces, metrics, logs
        └──────────────────────────┘
```

**Rules the diagram encodes:**
- Clients talk to the gateway, services talk to services, **nobody talks to another DB**.
- Every state change that others care about leaves via **outbox → broker**, not by
  synchronous fan-out.
- Cross-cutting concerns (authN, rate limits, tracing) live at the edge and in the
  library — not copy-pasted per service.

---

## ✅ The Self-Check

- [ ] I can argue **against** microservices with a straight face, and name the trigger to split.
- [ ] I can explain a **modular monolith** and how to keep its boundaries honest.
- [ ] I can decompose by **business capability** and explain why "service per entity" is wrong.
- [ ] I can choose **REST vs gRPC vs messaging** with concrete criteria.
- [ ] I can version an API and keep old clients working.
- [ ] I can explain **client-side vs server-side discovery**.
- [ ] I can justify an **API gateway** and name what must *not* live there.
- [ ] I can name the four Resilience4j patterns and when each applies.
- [ ] I can explain why **timeouts precede circuit breakers**.
- [ ] I can describe **W3C `traceparent`** propagation and span design.
- [ ] I can articulate **SLO/error budget** thinking vs raw metrics.
- [ ] I can explain the **dual-write problem** and fix it with an outbox.
- [ ] I can make a consumer **idempotent** with the dedupe row in the right transaction.
- [ ] I can choose **choreography vs orchestration** and name the pivot transaction.
- [ ] I know **exactly-once delivery does not exist**.

---

## 🧪 The Coding Drill

- [ ] Build **three services** (order, payment, inventory) with **separate databases** and
      prove no service can read another's schema.
- [ ] Add a **gateway** and route `/api/orders/**`; verify auth is enforced at the edge
      *and* that a direct service call is still authorised.
- [ ] Break `inventory-service` (add 10s of latency) and prove the **circuit breaker**
      opens and the order endpoint degrades gracefully instead of timing out.
- [ ] Kill `payment-service` and prove **retries with jitter** don't create a thundering herd.
- [ ] Add **Micrometer Tracing** and show one trace spanning all three services in Jaeger.
- [ ] Implement **transactional outbox** and kill the app mid-publish; prove the event
      still ships after restart.
- [ ] Replay an event twice and prove the consumer state is **unchanged**.
- [ ] Implement a **saga with a refund compensation** and force stock failure; prove the
      order ends `CANCELLED` with a matching refund.

---

**Related folders:** ← [07 · OAuth2 & OIDC](../07-OAuth2-OIDC/README.md) · → [09 · Messaging Systems](../09-Messaging-Systems/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
