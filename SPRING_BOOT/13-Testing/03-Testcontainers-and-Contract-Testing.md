# 03 · Testcontainers & Contract Testing

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★☆ ("How do you test against real infrastructure / across services?")
> **Prerequisite:** [02 · Unit & Slice Testing](./02-Unit-and-Slice-Testing.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. Why Real Infrastructure](#1-why-real-infrastructure)
- [2. Testcontainers Basics](#2-testcontainers-basics)
- [3. Patterns That Keep It Fast](#3-patterns-that-keep-it-fast)
- [4. Testing Kafka, Redis and Multi-Service Flows](#4-testing-kafka-redis-and-multi-service-flows)
- [5. WireMock — Faking Third Parties Properly](#5-wiremock--faking-third-parties-properly)
- [6. Contract Testing](#6-contract-testing)
- [7. Testing Event Contracts](#7-testing-event-contracts)
- [8. What Contract Testing Doesn't Solve](#8-what-contract-testing-doesnt-solve)
- [9. Code Examples](#9-code-examples)
- [10. Common Mistakes](#10-common-mistakes)
- [11. Interview Questions & Model Answers](#11-interview-questions--model-answers)
- [12. Self-Check & Revision Card](#12-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> **Testcontainers** runs real Postgres, Kafka, Redis etc. in Docker for the duration of the test
> JVM. It eliminates the "works on H2, fails on Postgres" class of bug — different types, different
> SQL, different constraints, different plans. Use `@ServiceConnection` (Boot 3.1+) to wire it up
> with zero config.
>
> **Static containers** are shared across test classes and stopped once at JVM exit — that's the
> difference between a 30-second suite and a 5-minute one.
>
> **WireMock** fakes a third party at the HTTP level; it's how you test timeouts, 500s,
> malformed responses and retries. Mock at *your* boundary, and use WireMock when you need to
> prove the HTTP behaviour.
>
> **Contract testing** (Pact, or Spring Cloud Contract) replaces the "spin everything up" E2E test
> with a per-consumer contract verified by the provider in CI. It catches the real cross-service
> failure — a provider changing a field a consumer depends on — without a fragile integration
> environment.

---

## 1. Why Real Infrastructure

### 1.1 What H2 hides

```sql
-- works on H2, fails on Postgres
SELECT * FROM orders WHERE metadata @> '{"channel":"web"}';   -- jsonb operator
INSERT INTO t (id, ts) VALUES (1, now()) ON CONFLICT (id) DO UPDATE SET ts = now();
SELECT DISTINCT ON (customer_id) * FROM orders ORDER BY customer_id, placed_at DESC;
CREATE TABLE x (id bigint GENERATED ALWAYS AS IDENTITY);       -- identity semantics differ
```

| Difference | H2 | Postgres |
|---|---|---|
| `jsonb` | limited / different | full operators, GIN indexable |
| `timestamptz`, `citext`, arrays | approximated or missing | native |
| `ON CONFLICT`, `RETURNING` | partial | full |
| Case sensitivity | uppercase by default | lowercase folding |
| Constraint timing | immediate | deferrable |
| Query plans | different | different |
| Isolation behaviour | simplified | real MVCC |

**The cost of the difference:** a query passes CI, fails in production. That's the class of bug
Testcontainers eliminates outright.

### 1.2 The Docker reality

Testcontainers needs a Docker daemon. In CI that's `docker:dind`, a mounted socket, or a runner
with Docker. In a locked-down corporate environment it may be the reason tests are skipped —
**which is a real problem you should mention**, because "our tests need Docker" is a genuine
constraint.

---

## 2. Testcontainers Basics

### 2.1 The simplest form

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class OrderRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired OrderRepository repo;
    // ...
}
```

`@ServiceConnection` (Boot 3.1+) auto-configures `spring.datasource.*` from the container — no
`@DynamicPropertySource`, no URL juggling.

### 2.2 Without `@ServiceConnection`

```java
@DynamicPropertySource
static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
}
```

**Caveat:** `@DynamicPropertySource` creates a new context per distinct set — so use it once, on a
shared base class.

### 2.3 Static vs instance containers

```java
// ❌ started and stopped for EVERY test method → each test pays ~2–5 s
@Container PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

// ✅ started once for the class
@Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
```

And to share across classes, the container must be `static` **and** the lifecycle must be
JVM-scoped — Testcontainers keeps static containers running until the JVM exits, and Spring's
context cache reuses the same datasource, so subsequent classes skip the startup.

### 2.4 Reuse between runs

```properties
# ~/.testcontainers.properties
testcontainers.reuse.enable=true
```

```java
@Container
@ServiceConnection
static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
    .withReuse(true);
```

**A locally reused container keeps its data** — which is fast but can surprise you with leftover
rows. Use it for local dev, not as a substitute for proper isolation.

### 2.5 Container lifecycle in CI

```
mvn verify
  ├─ pull postgres:16-alpine       (cache in CI, else ~10 s)
  ├─ start container               (~2–4 s)
  ├─ run 200 repository tests      (shared container)
  └─ stop at JVM exit
```

On a warm CI cache, the whole thing adds ~5–10 s. That's usually worth it.

---

## 3. Patterns That Keep It Fast

### 3.1 The shared base class

```java
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @Container @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
}
```

**Every subclass shares one container set and one Spring context.** This is the single highest-impact
pattern in a Spring + Testcontainers suite.

### 3.2 Clean state without recreating the container

```java
@Autowired JdbcTemplate jdbc;

@BeforeEach
void clean() {
    // fast: truncate everything, keep the schema
    jdbc.execute("""
        TRUNCATE TABLE order_items, orders, customers RESTART IDENTITY CASCADE
        """);
}
```

`TRUNCATE ... CASCADE` is milliseconds. `DROP SCHEMA` + re-run migrations is seconds. Prefer
truncation between tests, and Flyway/Liquibase once per container.

### 3.3 Put your Flyway migrations in the test run

```java
@TestConfiguration
class FlywayTestConfig {
    @Bean Flyway flyway(DataSource ds) {
        return Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
    }
}
```

**Testing against your real migrations** catches migration bugs (a missing column, a bad
constraint) before they hit production — and it's free if you're already running a real DB.

### 3.4 Parallelism

```properties
# junit-platform.properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.config.strategy=dynamic
```

**Careful:** parallel tests sharing one database need isolation (transactions rolled back, or
separate schemas per test). Most teams parallelise the unit tests and keep integration tests
serial — that's a reasonable trade and worth stating.

---

## 4. Testing Kafka, Redis and Multi-Service Flows

### 4.1 Kafka

```java
@SpringBootTest
@Testcontainers
class OrderEventFlowTest extends IntegrationTestBase {

    @Autowired OrderService orders;
    @Autowired KafkaTemplate<String, OrderEvent> kafka;
    @Autowired ConsumerFactory<String, OrderEvent> consumerFactory;

    @Test
    void placingAnOrder_publishesOrderCreated() {
        try (var consumer = new KafkaTestUtils(consumerFactory)) {
            orders.place(cart);

            var record = consumer.nextRecord("orders.v1", Duration.ofSeconds(5));
            assertThat(record.value().orderId()).isEqualTo(cart.orderId());
        }
    }
}
```

Spring Kafka ships `KafkaTestUtils`:
- `consumerForTopics(...)` — a raw consumer positioned at the end
- `getSingleRecord(topic, timeout)` / `getRecords(topic, n, timeout)`
- `emptyTopic(topic)` — wait until the topic is drained (useful for setup)

**Assert on the event contract, not the internal payload class** — that's the thing other teams
depend on.

### 4.2 Redis

```java
@Autowired StringRedisTemplate redis;

@Test
void cachesProductAfterFirstRead() {
    productService.get(42L);
    assertThat(redis.hasKey("product:v1:42")).isTrue();
    assertThat(redis.getExpire("product:v1:42")).isPositive();
}
```

Also test the paths that matter: **the cache being unavailable**. You can't easily stop a shared
container mid-test, so use a separate test class with a container that isn't started, or a
`@MockitoBean` that throws `RedisConnectionFailureException` on the cache abstraction.

### 4.3 A full flow across the async boundary

```java
@Test
void placingOrder_resultsInReservedStock_andAConfirmationEvent() {
    given(inventory has 5 of SKU-1);

    orders.place(cartWith(sku1, qty2));

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
        assertThat(inventoryRepository.available("SKU-1")).isEqualTo(3);
        assertThat(outboxRepository.countByType("OrderConfirmed")).isEqualTo(1);
    });
}
```

**The `await()` is essential** — the consumer runs asynchronously, so a synchronous assertion will
be flaky. And asserting on the *effects* (stock, outbox) rather than the mechanism keeps the test
stable.

---

## 5. WireMock — Faking Third Parties Properly

### 5.1 When to use it

| Scenario | WireMock | Mockito |
|---|---|---|
| Timeouts, connection resets | ✅ real HTTP behaviour | ❌ can't simulate a real timeout |
| 500s, 429s, malformed JSON | ✅ | ✅ |
| Retry/backoff behaviour | ✅ (with delays + scenarios) | hard |
| Circuit breaker tripping | ✅ | hard |
| Serialisation of the request body | ✅ (verify what was sent) | ✅ |

**Rule:** mock your own interface for logic tests; use WireMock when the **HTTP behaviour**
is what you're testing.

### 5.2 Setup

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentFlowTest extends IntegrationTestBase {

    @Container
    static final WireMockContainer wiremock = new WireMockContainer("wiremock/wiremock:3.3.1");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("clients.payment.base-url", wiremock::getBaseUrl);
    }

    @BeforeEach
    void stubs() {
        wiremock.stubFor(post(urlEqualTo("/v1/charges"))
            .willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"id":"ch_1","status":"succeeded","amount":3600}
                    """)));
    }
}
```

### 5.3 Testing the hard paths

```java
@Test
void retriesOn503_thenSucceeds() {
    wiremock.stubFor(post(urlEqualTo("/v1/charges"))
        .inScenario("flaky")
        .whenScenarioStateIs(STARTED)
        .willReturn(aResponse().withStatus(503))
        .willSetStateTo("recovered"));

    wiremock.stubFor(post(urlEqualTo("/v1/charges"))
        .inScenario("flaky")
        .whenScenarioStateIs("recovered")
        .willReturn(aResponse().withStatus(201)
            .withBody("{\"id\":\"ch_1\",\"status\":\"succeeded\"}")));

    var result = paymentGateway.charge(req);

    assertThat(result.success()).isTrue();
    wiremock.verify(2, postRequestedFor(urlEqualTo("/v1/charges")));
}
```

```java
@Test
void tripsCircuitBreaker_afterRepeatedFailures() {
    wiremock.stubFor(post(urlEqualTo("/v1/charges"))
        .willReturn(aResponse().withStatus(500)));

    for (int i = 0; i < 20; i++) {
        assertThatThrownBy(() -> paymentGateway.charge(req)).isInstanceOf(...);
    }

    // now the breaker is open: no more HTTP calls should happen
    wiremock.resetRequests();
    assertThatThrownBy(() -> paymentGateway.charge(req))
        .isInstanceOf(CallNotPermittedException.class);
    wiremock.verify(0, postRequestedFor(urlEqualTo("/v1/charges")));
}
```

**That second test is the one nobody writes** — and it's the one that proves the resilience config
actually works.

### 5.4 Verify what you sent

```java
wiremock.verify(postRequestedFor(urlEqualTo("/v1/charges"))
    .withHeader("Idempotency-Key", matching("[a-f0-9-]{36}"))
    .withRequestBody(matchingJsonPath("$.amount", equalTo("3600")))
    .withRequestBody(matchingJsonPath("$.currency", equalTo("USD"))));
```

This catches the real integration bug: you sent the wrong field, or a float instead of cents.

---

## 6. Contract Testing

### 6.1 The problem it solves

```
team A (consumer)  ──REST──►  team B (provider)
```

Team B renames `total` to `totalAmount`. Team A's tests all pass (they mock B). Team A's
production breaks. Full E2E tests would catch it, but they're slow, flaky, and require all
services deployed together.

**Contract testing** flips it: the consumer declares what it needs; the provider verifies it can
still provide it — **in the provider's own CI, without deploying anything.**

### 6.2 Pact — consumer-driven

**Consumer side** — generate the contract from a test:

```java
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "inventory-service", port = "9999")
class InventoryClientContractTest {

    @Pact(consumer = "order-service")
    public RequestResponsePact stockAvailable(PactDslWithProvider builder) {
        return builder
            .given("SKU-1 has 5 units")
            .uponReceiving("a stock request")
                .path("/stock/SKU-1")
                .method("GET")
            .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                    .stringType("sku", "SKU-1")
                    .integerType("available", 5))
            .toPact();
    }

    @Test
    void readsAvailableStock(MockServer server) {
        var client = new InventoryClient(server.getUrl());
        assertThat(client.stock("SKU-1").available()).isEqualTo(5);
    }
}
```

**Provider side** — verify against the published pact:

```java
@Provider("inventory-service")
@PactBroker(url = "https://pact.example.com")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class InventoryProviderVerificationTest {

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext ctx) {
        ctx.verifyInteraction();
    }

    @State("SKU-1 has 5 units")
    void skuHasFiveUnits() { inventoryRepository.save(new Inventory("SKU-1", 5)); }
}
```

**The flow:**
1. Consumer test publishes the pact to a broker.
2. Provider CI pulls the pact and verifies its real implementation satisfies it.
3. If the provider would break a consumer, **its own build fails** — before deploy.
4. `can-i-deploy` gates the deployment.

### 6.3 Spring Cloud Contract

Write the contract in Groovy/YAML, and the framework **generates** the provider tests and the
consumer stubs:

```groovy
Contract.make {
    request {
        method 'GET'
        url '/stock/SKU-1'
    }
    response {
        status 200
        body([sku: 'SKU-1', available: 5])
        headers { contentType(applicationJson()) }
    }
}
```

- ✅ Less consumer-side work; the stubs are generated.
- ❌ Ties you to the framework; the contract lives with the provider rather than the consumer.

### 6.4 Pact vs Spring Cloud Contract

| | Pact | Spring Cloud Contract |
|---|---|---|
| Direction | consumer-driven | provider-driven (or consumer-written) |
| Broker | Pact Broker (rich, `can-i-deploy`) | Nexus/artifactory + stubs jar |
| Language | polyglot (JVM, JS, Go, …) | JVM-focused |
| Generated tests | on the provider | on both sides |
| Best for | many independent teams | a Spring-only estate |

### 6.5 When contract testing is worth it

- Multiple teams deploying independently.
- A shared internal API with real consumers.
- You want to remove an E2E environment from the critical path.

**When it's not:** a single team, a single deployable, or a public API with an OpenAPI spec and
generated clients (schema validation plus a few integration tests is enough).

---

## 7. Testing Event Contracts

Contracts aren't only HTTP. For Kafka events, the "contract" is the **schema**.

```java
@Test
void orderPlaced_matchesThePublishedSchema() throws Exception {
    var event = new OrderPlaced(...);
    var schema = JsonSchemaFactory.getInstance(V202012)
        .getSchema(getClass().getResourceAsStream("/schemas/order-placed.json"));

    var errors = schema.validate(mapper.readTree(mapper.writeValueAsString(event)));

    assertThat(errors).isEmpty();
}
```

Even better, put the schema in a **registry** and make CI fail on a breaking change
([Topic 09 ch 04](../09-Messaging-Systems/04-Reliable-Messaging-and-Event-Driven-Architecture.md)):

```bash
# CI step
curl -s -X POST "$REGISTRY/compatibility/subjects/orders.v1-value/versions/latest" \
  -H 'Content-Type: application/vnd.schemaregistry.v1+json' \
  -d "{\"schema\": $(jq -Rs . avro/OrderPlaced.avsc)}"
# nonzero exit ⇒ breaking change ⇒ build fails
```

**The three event-contract tests to have:**

1. **Schema validation** — the event matches the registered schema.
2. **Required fields present** — every consumer's needs are in the envelope.
3. **Backward compatibility** — the registry accepts the new version.

---

## 8. What Contract Testing Doesn't Solve

| Not solved | Why | What to do |
|---|---|---|
| Logic errors | a contract only checks shape | unit tests |
| Performance | no load in a contract test | load tests |
| Auth/permissions per consumer | contracts are per-pair, not per-role | integration + security tests |
| Multi-step flows | a contract is one interaction | a few E2E tests for critical journeys |
| Data consistency | not a schema concern | saga tests |
| Versioning policy | contracts don't decide deprecation | registry + explicit policy |

**The honest framing:** *"Contract testing replaces the E2E test that exists only to check a field
name. It doesn't replace the two or three E2E tests that prove the critical journey works
end-to-end."*

---

## 9. Code Examples

### 9.1 The full integration base with all dependencies

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class FullStackTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withReuse(true);

    @Container @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.0")).withReuse(true);

    @Container @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379).withReuse(true);

    @Autowired protected TestRestTemplate http;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper json;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE TABLE order_items, orders, customers RESTART IDENTITY CASCADE");
    }
}
```

### 9.2 A real end-to-end API test

```java
class CheckoutFlowTest extends FullStackTest {

    @Test
    void checkout_reservesStock_chargesAndConfirms() {
        // arrange via the API, not the repository — that's what a real client does
        var customerId = createCustomer("ada@example.com");
        var sku = createProduct("SKU-1", price("10.00"), stock(5));

        stubPaymentProvider(201, "{\"id\":\"ch_1\",\"status\":\"succeeded\"}");

        // act
        var response = http.postForEntity("/api/checkout", checkoutRequest(customerId, sku, 2),
                                          CheckoutResponse.class);

        // assert the response contract
        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody().status()).isEqualTo("CONFIRMED");

        // assert the side effects, asynchronously
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject(
                "SELECT on_hand - reserved FROM inventory WHERE sku = ?", Integer.class, sku))
                .isEqualTo(3);
        });
    }
}
```

### 9.3 Testing the outbox relay

```java
@Test
void relay_publishesUnpublishedEvents_andMarksThemSent() {
    jdbc.update("INSERT INTO outbox (id, event_type, payload) VALUES (gen_random_uuid(), 'OrderPlaced', '{}')");

    relay.drain();

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM outbox WHERE published_at IS NULL", Long.class)).isZero());
}
```

### 9.4 Failing fast when Docker isn't available

```java
@Testcontainers(disabledWithoutDocker = true)
class DockerRequiredTest { }
```

`disabledWithoutDocker = true` skips the class instead of failing the build on a laptop without
Docker. Be careful: on CI, Docker should always be present, and a silently skipped integration
test is a false sense of safety. **Set it, and also assert in CI that these tests ran.**

---

## 10. Common Mistakes

| Mistake | Consequence | Fix |
|---|---|---|
| `@Container` non-static | container restart per test | `static` |
| New container per class | minutes of startup | shared base class |
| `@DynamicPropertySource` per class with different values | new context each time | centralise |
| `@DataJpaTest` with H2 | dialect mismatch | `replace = NONE` + Testcontainers |
| No schema cleanup between tests | order-dependent failures | `TRUNCATE ... CASCADE` |
| `withReuse(true)` in CI | stale state, flaky | only locally |
| Integration tests skipped without Docker, silently | false green | assert they ran in CI |
| `Thread.sleep` after producing an event | flaky | Awaitility / `KafkaTestUtils` |
| Contract tests but no broker | no cross-team gating | Pact Broker + `can-i-deploy` |
| Contracts checked only on the consumer | provider breaks silently | provider verification in its CI |
| WireMock stubbing everything | you're testing WireMock | stub only what the scenario needs |
| E2E test asserting a field name only | slow and redundant | that's what contract tests are for |

---

## 11. Interview Questions & Model Answers

**Q1. Why use Testcontainers instead of H2?**
> H2 isn't the database I run. It differs on types like `jsonb`, `timestamptz` and `citext`, on
> SQL syntax such as `ON CONFLICT` and `DISTINCT ON`, on constraint timing, and on query plans. So
> H2 can pass a query that Postgres rejects. Testcontainers runs the real image, which also means I
> can test my actual Flyway migrations and real constraints — and constraints are behaviour.

**Q2. How do you keep a Testcontainers suite fast?**
> Static containers with a shared base class so the whole suite reuses one container set, one
> Spring context, `TRUNCATE CASCADE` between tests instead of recreating the schema, and
> `@ServiceConnection` instead of per-class property sources so the context isn't rebuilt. Locally
> I also enable reuse; in CI the image is cached. That gets container overhead down to a few
> seconds.

**Q3. How do you test a Kafka consumer end-to-end?**
> Produce the event, then use Awaitility to assert on the observable *effects* — the database row,
> the outbox entry — rather than on internal state. On the producer side, `KafkaTestUtils` gives me
> a consumer I can poll for the published record so I can assert the event contract. I avoid
> `Thread.sleep` entirely, because it's flaky in both directions.

**Q4. When do you use WireMock instead of a Mockito mock?**
> When the HTTP behaviour is what I'm testing: timeouts, retries, 503-then-success, circuit breaker
> tripping, or the exact request body I send. A Mockito mock of my client can't simulate a socket
> timeout. For logic tests I still mock my own interface, because it's faster and doesn't need a
> server.

**Q5. What problem does contract testing solve that integration tests don't?**
> Integration tests require all services deployed together, which is slow and flaky, and they're
> usually skipped in practice. A contract test lets the provider verify, in its own CI, that it
> still satisfies what each consumer actually uses — so a field rename breaks the provider's build
> instead of the consumer's production. It also makes the dependency explicit: you can see which
> consumers depend on a field before you remove it.

**Q6. Doesn't contract testing replace E2E tests?**
> Partly. It replaces the E2E tests that exist to check a response shape, which is where most of
> the flakiness comes from. It doesn't replace two or three E2E tests for the critical journeys,
> because contracts don't cover multi-step flows, ordering, auth per role, or data consistency.
> So I'd shrink E2E to a handful of smoke tests and move the rest into contracts and slices.

**Q7. How do you test a circuit breaker?**
> With WireMock returning 500s repeatedly until the breaker opens, then stubbing a different
> response and asserting that **no HTTP call is made** while it's open — I verify zero requests hit
> the stub. Then advance past the wait duration and assert the half-open probe recovers. Testing
> only "it throws" doesn't prove the breaker is doing anything.

**Q8. How do you test event schema compatibility?**
> Validate the produced event against the registered schema in a test, and run a compatibility
> check against the registry in CI so a breaking change fails the build. That way the contract is
> enforced at the same gate as the code, rather than discovered by a consumer at runtime.

---

## 12. Self-Check & Revision Card

- [ ] I can explain why H2 is the wrong choice with three concrete examples.
- [ ] I can set up `@ServiceConnection` with a shared static container.
- [ ] I can keep container overhead to seconds.
- [ ] I can test a Kafka producer and consumer.
- [ ] I can test a timeout and a circuit breaker with WireMock.
- [ ] I can explain consumer-driven contracts and the Pact flow.
- [ ] I can say what contract testing doesn't solve.
- [ ] I can test event schema compatibility.

```
┌──────────── TESTCONTAINERS & CONTRACTS — 60s RECALL ────────────────────────┐
│ H2 LIES: jsonb · timestamptz · ON CONFLICT · DISTINCT ON · constraints ·    │
│   plans. Real image or nothing. Test your real MIGRATIONS too.              │
│                                                                              │
│ SPEED: static containers + shared base class + ONE context +                │
│   TRUNCATE CASCADE between tests + @ServiceConnection.                      │
│   @DynamicPropertySource per class = new context each time.                 │
│                                                                              │
│ KAFKA TESTS: assert EFFECTS with Awaitility; producer side use              │
│   KafkaTestUtils. Never Thread.sleep.                                       │
│                                                                              │
│ WIREMOCK when HTTP behaviour is the subject: timeouts · 503→201 ·           │
│   retry counts · CIRCUIT BREAKER (verify ZERO calls while open).            │
│                                                                              │
│ CONTRACTS: consumer publishes pact → provider verifies in ITS CI →          │
│   can-i-deploy gate. Replaces the field-name E2E test, NOT the 2–3          │
│   critical journey smoke tests.                                             │
│                                                                              │
│ EVENTS: schema validation in tests + registry compatibility check in CI.    │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [02 · Unit & Slice Testing](./02-Unit-and-Slice-Testing.md) · **Next:** [04 · Performance, Load & CI Pipelines](./04-Performance-Load-and-CI-Pipelines.md)
