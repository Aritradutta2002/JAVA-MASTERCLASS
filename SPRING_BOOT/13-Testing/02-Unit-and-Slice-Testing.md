# 02 · Unit & Slice Testing with JUnit 5, Mockito & Spring Boot Test

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★☆ ("How do you test a controller / repository?")
> **Prerequisite:** [01 · Testing Strategy & the Test Pyramid](./01-Testing-Strategy-and-the-Test-Pyramid.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. The Dependency Set](#1-the-dependency-set)
- [2. JUnit 5 Essentials](#2-junit-5-essentials)
- [3. AssertJ — Assertions That Read](#3-assertj--assertions-that-read)
- [4. Mockito — Correctly](#4-mockito--correctly)
- [5. The Spring Test Context](#5-the-spring-test-context)
- [6. Slice Tests](#6-slice-tests)
- [7. Testing Security](#7-testing-security)
- [8. Testing Exceptions & Validation](#8-testing-exceptions--validation)
- [9. Common Spring Test Mistakes](#9-common-spring-test-mistakes)
- [10. Code Examples](#10-code-examples)
- [11. Interview Questions & Model Answers](#11-interview-questions--model-answers)
- [12. Self-Check & Revision Card](#12-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> **JUnit 5** runs the tests; **AssertJ** makes the assertions readable; **Mockito** replaces the
> dependencies you don't control; **`@SpringBootTest` slices** exercise the wiring.
>
> The four annotations you actually use: `@WebMvcTest` (controller + MVC, mock the service),
> `@DataJpaTest` (repositories + real SQL), `@SpringBootTest` (full context, use sparingly), and
> plain JUnit for domain logic.
>
> **The most important performance decision in a Spring test suite is context caching.**
> Every distinct `@MockBean`/`@DynamicPropertySource`/configuration combination creates a **new
> ApplicationContext**. Ten different combinations = ten context startups = a slow suite. Group
> your tests so they share a context.
>
> **`@MockBean` is deprecated in Boot 3.4+** in favour of `@MockitoBean` (and `@SpyBean` →
> `@SpyitoBean`). Using the new one signals you're current.

---

## 1. The Dependency Set

```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>   <!-- brings JUnit 5, AssertJ, Mockito, Hamcrest, JSONassert, XmlUnit -->
  </dependency>
  <dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.awaitility</groupId>
    <artifactId>awaitility</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

```xml
<!-- surefire for unit tests; failsafe for integration tests -->
<plugin>
  <artifactId>maven-failsafe-plugin</artifactId>
  <executions>
    <execution>
      <goals><goal>integration-test</goal><goal>verify</goal></goals>
    </execution>
  </executions>
</plugin>
```

`*Test` → surefire (unit). `*IT` → failsafe (integration). Splitting them lets CI run the fast
suite on every push and the slow one on a merge.

---

## 2. JUnit 5 Essentials

### 2.1 Lifecycle

```java
class OrderServiceTest {

    @BeforeAll  static void beforeAll() { /* once, static */ }
    @BeforeEach void setUp() { /* fresh fixtures per test */ }
    @AfterEach  void tearDown() { }
    @AfterAll   static void afterAll() { }

    @Test void aTest() { }
}
```

**Default lifecycle is `PER_METHOD`** — a new test instance per method. That's what you want:
no state leaks between tests. `@TestInstance(PER_CLASS)` exists for expensive setup, but then
you own the isolation.

### 2.2 Assertions and grouping

```java
@Test
void orderTotals() {
    var order = Order.withItems(item("A", 10), item("B", 20));

    assertAll("totals",
        () -> assertThat(order.subtotal()).isEqualTo(Money.of("30.00")),
        () -> assertThat(order.tax()).isEqualTo(Money.of("6.00")),
        () -> assertThat(order.total()).isEqualTo(Money.of("36.00")));
}
```

`assertAll` reports **all** failures, not just the first — saves a test cycle.

### 2.3 Nested tests

```java
class CouponServiceTest {

    @Nested
    class WhenCouponIsExpired {
        @Test void isNotApplied() { }
        @Test void returnsOriginalTotal() { }
    }

    @Nested
    class WhenCouponIsValid {
        @Test void isApplied() { }
    }
}
```

Groups related behaviours and makes the failure output readable. Slightly overused in practice —
use it when a class genuinely has distinct scenarios.

### 2.4 Parameterised tests

```java
@ParameterizedTest(name = "{0} cents → {1} discount")
@CsvSource({ "0,0.00", "9999,0.00", "10000,5.00", "10001,5.00" })
void discount(long cents, String expected) { ... }

@ParameterizedTest
@MethodSource("ordersWithExpectedTotals")
void totals(Order order, String expected) { ... }

static Stream<Arguments> ordersWithExpectedTotals() { ... }
```

### 2.5 Tags and conditional execution

```java
@Tag("slow")
@Test void hitsTheRealApi() { }

@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
@Test void onlyInCi() { }
```

```xml
<excludedGroups>slow</excludedGroups>   <!-- in the fast profile -->
```

### 2.6 Timeouts — assert behaviour, not speed

```java
@Test
@Timeout(value = 2, unit = TimeUnit.SECONDS)   // catches infinite loops, not perf regressions
void doesNotHang() { }
```

Don't use `@Timeout` as a performance assertion; use it to fail fast on a deadlock.

---

## 3. AssertJ — Assertions That Read

### 3.1 Why not `assertEquals`

```java
// ❌ "expected: <3> but was: <2>" — which field?
assertEquals(3, order.items().size());

// ✅ "expected size: 3 but was: 2 in: [item A, item B]"
assertThat(order.items()).hasSize(3);
```

AssertJ's failure messages include the **actual collection contents**, which is often enough to
diagnose the bug without a debugger.

### 3.2 The fluent API you'll actually use

```java
assertThat(order.status()).isEqualTo(SHIPPED);
assertThat(order.total()).isEqualByComparingTo("36.00");        // BigDecimal-safe!
assertThat(items).hasSize(3).extracting(Item::sku).containsExactly("A", "B", "C");
assertThat(items).extracting(Item::price).allMatch(p -> p.isPositive());
assertThat(exception).isInstanceOf(OutOfStockException.class)
                     .hasMessageContaining("SKU-42")
                     .hasFieldOrPropertyWithValue("sku", "SKU-42");
assertThat(optional).isPresent().get().extracting(Order::id).isEqualTo(42L);
assertThatThrownBy(() -> service.place(cart)).isInstanceOf(OutOfStockException.class);
assertThatCode(() -> service.place(cart)).doesNotThrowAnyException();
```

### 3.3 The `BigDecimal` trap

```java
// ❌ fails: 10.0 is not equal to 10.00 (scale differs)
assertThat(total).isEqualTo(new BigDecimal("10.00"));

// ✅ compares numerically
assertThat(total).isEqualByComparingTo("10.00");

// ✅ best: compare Money values that encapsulate the scale
assertThat(total).isEqualTo(Money.of("10.00"));
```

That single trap causes more false test failures in money code than anything else.

### 3.4 Soft assertions

```java
var softly = new SoftAssertions();
softly.assertThat(order.id()).isNotNull();
softly.assertThat(order.items()).isNotEmpty();
softly.assertThat(order.total()).isPositive();
softly.assertAll();      // reports everything at once
```

Useful in integration tests where several things should hold and you want all the failures.

---

## 4. Mockito — Correctly

### 4.1 The basics

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository repo;
    @Mock PaymentClient payments;
    @InjectMocks OrderService service;      // or construct it explicitly — see 4.5

    @Test
    void placesOrder() {
        given(repo.findById(42L)).willReturn(Optional.of(anOrder()));
        given(payments.charge(any())).willReturn(PaymentResult.success());

        var result = service.place(42L);

        assertThat(result.status()).isEqualTo(PAID);
        then(repo).should().save(any(Order.class));
    }
}
```

`given`/`willReturn`/`then`/`should` come from **BDDMockito** and read far better than
`when(...).thenReturn(...)`/`verify(...)`.

### 4.2 Argument matchers

```java
given(repo.findByStatus(eq(SHIPPED), any(Pageable.class))).willReturn(page);
given(repo.save(argThat(o -> o.total().isPositive()))).willReturn(saved);

// capture what was passed
var captor = ArgumentCaptor.forClass(Order.class);
then(repo).should().save(captor.capture());
assertThat(captor.getValue().status()).isEqualTo(PENDING);
```

**The classic error:** mixing matchers with raw values — `any()`, then `42L`, throws
`InvalidUseOfMatchersException`. All arguments must be matchers or all raw.

### 4.3 Verifying interactions

```java
then(payments).should().charge(argThat(r -> r.amount().equals(Money.of("36.00"))));
then(payments).should(times(1)).charge(any());          // exactly once
then(payments).should(never()).refund(any());
thenNoMoreInteractions(payments);                        // nothing else happened
```

**Use interaction verification sparingly.** Verify the interactions that are the *point* of the
test (a charge happened, a refund did not). Verifying every call makes the test a copy of the
implementation.

### 4.4 Stubbing exceptions (for failure paths)

```java
given(payments.charge(any())).willThrow(new ProviderUnavailableException("timeout"));

assertThatThrownBy(() -> service.place(42L))
    .isInstanceOf(OrderFailedException.class);
then(repo).should().save(argThat(o -> o.status() == PAYMENT_FAILED));
```

### 4.5 Prefer constructor injection over `@InjectMocks`

```java
// ✅ explicit, no reflection, and the compiler checks the signature
class OrderServiceTest {
    OrderRepository repo = mock(OrderRepository.class);
    PaymentClient payments = mock(PaymentClient.class);
    OrderService service = new OrderService(repo, payments);
}
```

`@InjectMocks` uses reflection and silently fails to inject when field names change — you get a
null dependency and an NPE in an unrelated test. Explicit construction is boring and correct.

### 4.6 Strict stubs

`MockitoExtension` defaults to `Strictness.STRICT_STUBS`: an unused stub **fails the test**.

That's a feature — it catches copy-pasted stubs that no longer apply and dead code paths. If you
need to relax it for a specific test, `@MockitoSettings(strictness = Strictness.LENIENT)`, but
reaching for that is usually a sign the test is doing too much.

### 4.7 Don't mock what you don't own

```java
// ❌ mocking a third-party client at the HTTP layer is fragile
given(restTemplate.postForObject(anyString(), any(), eq(ChargeResponse.class)))
    .willReturn(response);

// ✅ mock at your own boundary interface
given(paymentGateway.charge(any())).willReturn(ChargeResult.success());
```

Wrap the third party in your own interface, mock that. Then a library upgrade can't break your
tests.

---

## 5. The Spring Test Context

### 5.1 How it works

```
@Test ──► TestContextManager ──► cache lookup by (config classes + properties + mocks + customizers)
                                        │
                                   hit  │  reuse the running context
                                        │  miss
                                        ▼
                                 build a NEW ApplicationContext   ← expensive (~0.5–3 s)
```

**Context caching is the single biggest factor in Spring test-suite speed.** A suite with 50
distinct contexts takes minutes; one with 3 takes seconds.

### 5.2 What busts the cache

| Change | New context? |
|---|---|
| Different `@MockitoBean` set | **yes** |
| `@TestPropertySource` with different values | **yes** |
| Different `@SpringBootTest(classes = ...)` | **yes** |
| `@DynamicPropertySource` | **yes** |
| `@ActiveProfiles` different | **yes** |
| Same annotations, different test class | **no — reused** |

**So:** standardise your test properties (one `application-test.yml`), avoid per-test
`@TestPropertySource`, and group tests that use the same mock set.

```java
// ❌ three classes, three contexts
@SpringBootTest @TestPropertySource(properties = "feature.x=true")  class A {}
@SpringBootTest @TestPropertySource(properties = "feature.x=false") class B {}
@SpringBootTest @TestPropertySource(properties = "other=1")         class C {}

// ✅ one context
@SpringBootTest
@ActiveProfiles("test")
abstract class IntegrationTestBase { }        // extend it everywhere
```

### 5.3 `@DirtiesContext` — the last resort

```java
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
```

Marks the context unusable, forcing a rebuild. Each use adds seconds. Use it only when a test
genuinely corrupts global state that can't be reset (a static cache, a system property).

### 5.4 `@Transactional` in tests

```java
@SpringBootTest
@Transactional     // rolls back after each test — no cleanup needed
class OrderIntegrationTest { }
```

**Pros:** fast isolation, no truncation.

**Cons — and they matter:**
- The transaction is never actually committed, so **commit-time behaviour isn't tested**
  (`afterCommit` hooks, `@TransactionalEventListener`, deferred constraints, some triggers).
- Lazy loading may work in tests and fail in production (the session is open longer).
- Some DB features (unique constraint conflicts across connections, `REQUIRES_NEW`) behave
  differently.

**For repository tests, `@DataJpaTest` is transactional by default and that's fine.** For service
tests that assert on commit-time behaviour, use `@Commit` on the specific test or turn off the
rollback — or accept it and add one non-transactional end-to-end test.

---

## 6. Slice Tests

### 6.1 `@WebMvcTest` — controller layer only

Loads MVC infrastructure: controllers, `@ControllerAdvice`, filters, `WebMvcConfigurer`. **Not**
`@Service`, `@Repository`, or the JPA layer.

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean OrderService orders;           // Boot 3.4+; @MockBean before that

    @Test
    void getOrder_whenFound_returns200() throws Exception {
        given(orders.find(42L)).willReturn(anOrder());

        mvc.perform(get("/api/orders/42").accept(APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.id").value(42))
           .andExpect(jsonPath("$.status").value("PAID"))
           .andExpect(jsonPath("$.total").value(36.00));
    }

    @Test
    void getOrder_whenMissing_returns404() throws Exception {
        given(orders.find(99L)).willReturn(null);

        mvc.perform(get("/api/orders/99"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.error").value("not_found"));
    }
}
```

**What this actually tests:** routing, serialisation, validation, exception mapping, content
types, security rules. That's the wiring a unit test can't see — and it's where most controller
bugs are.

**What to assert:** status, key fields, error shape. Not the whole JSON body (brittle) unless the
contract requires it (use `content().json(expected, JSONCompareMode.LENIENT)`).

### 6.2 `@DataJpaTest` — repositories with real SQL

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)     // use Testcontainers, not H2
class OrderRepositoryTest {

    @Autowired OrderRepository repo;
    @Autowired TestEntityManager em;

    @Test
    void findByCustomerAndStatus_returnsNewestFirst() {
        var customer = em.persist(Customer.of("ada"));
        em.persist(anOrder(customer, PAID, "2026-03-01"));
        em.persist(anOrder(customer, PAID, "2026-03-11"));
        em.flush();

        var results = repo.findTop10ByCustomerIdAndStatusOrderByPlacedAtDesc(customer.getId(), PAID);

        assertThat(results).hasSize(2)
            .extracting(Order::placedAt).isSortedAccordingTo(Comparator.reverseOrder());
    }
}
```

**`@DataJpaTest` replaces your datasource with an embedded H2 by default** — that's the problem.
H2 accepts SQL Postgres rejects, has different type semantics (`text`, `jsonb`, `timestamptz`,
`citext` all differ), and different query plans. **Always use Testcontainers with the real
database** ([ch 03](./03-Testcontainers-and-Contract-Testing.md)).

### 6.3 `@JsonTest` — serialisation only

```java
@JsonTest
class OrderJsonTest {
    @Autowired JacksonTester<Order> json;

    @Test
    void serialisesMoneyAsStringWithTwoDecimals() throws Exception {
        var order = anOrder();
        assertThat(json.write(order)).hasJsonPathStringValue("@.total", "36.00");
    }

    @Test
    void ignoresUnknownFields() throws Exception {
        json.parse("{\"id\":1,\"unknown\":true}");
    }
}
```

Fast, no DB, and it catches the `BigDecimal`/`LocalDateTime`/enum-shape bugs that unit tests
can't.

### 6.4 `@RestClientTest` — HTTP clients

```java
@RestClientTest(InventoryClient.class)
class InventoryClientTest {
    @Autowired InventoryClient client;
    @Autowired MockRestServiceServer server;

    @Test
    void mapsProviderError_toDomainException() {
        server.expect(requestTo("/stock/SKU-1"))
              .andRespond(withStatus(SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.stock("SKU-1"))
            .isInstanceOf(InventoryUnavailableException.class);
    }
}
```

No real network, and it verifies your mapping of provider errors to domain exceptions — which is
exactly the logic that's usually untested.

### 6.5 The slice table

| Annotation | Loads | Mock/real | Use for |
|---|---|---|---|
| `@WebMvcTest` | MVC, filters, advice | mock services | controllers, validation, error mapping |
| `@DataJpaTest` | JPA + datasource | real DB (Testcontainers!) | repositories, queries, constraints |
| `@JsonTest` | Jackson | — | serialisation shape |
| `@RestClientTest` | RestTemplate/RestClient | fake server | client error mapping |
| `@SpringBootTest` | everything | real or mocked | full flows, wiring, security |
| plain JUnit | nothing | mocks | domain logic |

---

## 7. Testing Security

### 7.1 With `spring-security-test`

```java
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerSecurityTest {

    @Autowired MockMvc mvc;
    @MockitoBean OrderService orders;

    @Test
    void anonymous_isRejected() throws Exception {
        mvc.perform(get("/api/orders/42")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customer_canReadOwnOrder() throws Exception {
        given(orders.find(42L)).willReturn(anOrderOf("ada"));
        mvc.perform(get("/api/orders/42").with(user("ada")))
           .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customer_cannotReadSomeoneElsesOrder() throws Exception {
        given(orders.find(42L)).willReturn(anOrderOf("bob"));
        mvc.perform(get("/api/orders/42").with(user("ada")))
           .andExpect(status().isForbidden());
    }
}
```

**Test the negative cases.** Authorisation regressions are silent; the only defence is a test that
asserts a 403/401.

### 7.2 With JWT

```java
@Test
void rejectsExpiredToken() throws Exception {
    var token = JwtTestSupport.expiredToken("ada", "CUSTOMER");
    mvc.perform(get("/api/orders/42").header(AUTHORIZATION, "Bearer " + token))
       .andExpect(status().isUnauthorized());
}
```

### 7.3 What to test

| Test | Why |
|---|---|
| anonymous → 401 | the endpoint is protected |
| wrong role → 403 | role rules work |
| another user's resource → 403 | ownership checks work |
| valid token → 200 | you didn't over-restrict |
| expired/malformed token → 401 | token validation works |
| CSRF disabled for stateless APIs | no accidental breakage |

---

## 8. Testing Exceptions & Validation

### 8.1 Controller validation

```java
@Test
void createOrder_whenQuantityIsZero_returns400() throws Exception {
    mvc.perform(post("/api/orders")
            .contentType(APPLICATION_JSON)
            .content("""
                {"sku":"SKU-1","quantity":0}
                """))
       .andExpect(status().isBadRequest())
       .andExpect(jsonPath("$.errors[0].field").value("quantity"))
       .andExpect(jsonPath("$.errors[0].message").value("must be greater than 0"));
}
```

This tests the `@ControllerAdvice` mapping too — which is frequently broken and rarely tested.

### 8.2 A stable error contract

```java
record ApiError(String code, String message, List<FieldError> errors, String traceId) {}
```

Assert on `code` and the presence of `traceId`, not on the exact message text (which changes).

### 8.3 Testing service exceptions

```java
@Test
void place_whenPaymentFails_marksOrderFailed_andDoesNotReserveStock() {
    given(payments.charge(any())).willThrow(new ProviderUnavailableException("x"));

    assertThatThrownBy(() -> service.place(cart))
        .isInstanceOf(OrderFailedException.class);

    then(inventory).should(never()).reserve(any());     // the important assertion
    assertThat(repo.findById(orderId)).get()
        .extracting(Order::status).isEqualTo(PAYMENT_FAILED);
}
```

The `never()` assertion is the one that catches the real bug — a failed payment that still
reserved stock.

---

## 9. Common Spring Test Mistakes

| Mistake | Consequence | Fix |
|---|---|---|
| `@SpringBootTest` for everything | 5-minute suite | slices; plain JUnit for logic |
| A new context per test class | minutes of startup | standardise properties, share a base class |
| `@DataJpaTest` on H2 | SQL works in test, fails in prod | `@AutoConfigureTestDatabase(replace = NONE)` + Testcontainers |
| `@MockBean` where `@MockitoBean` is available | deprecated warnings | use the new annotation |
| `@InjectMocks` with renamed fields | silent nulls, confusing NPEs | explicit constructor |
| Mocking your own types | tests mirror implementation | fakes or real instances |
| `@Transactional` on a test that checks `afterCommit` | the hook never fires | non-transactional test or `@Commit` |
| Asserting the whole JSON body | every field change breaks tests | `jsonPath` on the contract fields |
| `assertEquals` on `BigDecimal` | scale mismatch | `isEqualByComparingTo` |
| Testing only the happy path | authorisation/validation bugs ship | negative cases |
| `Thread.sleep` in async tests | flaky and slow | Awaitility |
| Same `@TestPropertySource` value repeated | not a bug, but a new context if values differ | centralise |

---

## 10. Code Examples

### 10.1 A complete controller slice

```java
@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class OrderControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean OrderService orders;

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void get_returnsOrder() throws Exception {
        given(orders.find(42L)).willReturn(
            new OrderView(42L, "PAID", new BigDecimal("36.00"), Instant.parse("2026-03-11T10:00:00Z")));

        mvc.perform(get("/api/orders/42").accept(APPLICATION_JSON))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
           .andExpect(jsonPath("$.id").value(42))
           .andExpect(jsonPath("$.status").value("PAID"))
           .andExpect(jsonPath("$.total").value(36.00));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void get_whenNotFound_returns404WithErrorCode() throws Exception {
        given(orders.find(99L)).willReturn(null);

        mvc.perform(get("/api/orders/99"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("order_not_found"));
    }

    @Test
    void get_withoutAuth_returns401() throws Exception {
        mvc.perform(get("/api/orders/42")).andExpect(status().isUnauthorized());
    }
}
```

### 10.2 A repository test against real Postgres

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
class OrderRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired OrderRepository repo;
    @Autowired TestEntityManager em;

    @Test
    void findsOnlyPendingOrders_forTheCustomer_newestFirst() {
        var c = em.persist(Customer.of("ada"));
        em.persist(order(c, PENDING, "2026-03-01"));
        em.persist(order(c, PENDING, "2026-03-11"));
        em.persist(order(c, PAID,    "2026-03-05"));
        em.flush();

        var pending = repo.findByCustomerIdAndStatusOrderByPlacedAtDesc(c.getId(), PENDING);

        assertThat(pending).hasSize(2)
            .extracting(Order::placedAt)
            .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void rejectsDuplicateOrderNumber() {
        em.persist(orderWithNumber("ORD-1"));
        em.flush();

        assertThatThrownBy(() -> {
            repo.saveAndFlush(orderWithNumber("ORD-1"));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

The second test is the valuable one: **constraints are behaviour**, and only a real database
enforces them.

### 10.3 A domain unit test with a fake

```java
class InventoryTest {

    @Test
    void reserve_whenNotEnoughStock_doesNotChangeReserved() {
        var inv = new Inventory("SKU-1", onHand(5), reserved(0));

        assertThatThrownBy(() -> inv.reserve(6))
            .isInstanceOf(InsufficientStockException.class);

        assertThat(inv.reserved()).isEqualTo(0);       // nothing changed
        assertThat(inv.available()).isEqualTo(5);
    }

    @ParameterizedTest
    @CsvSource({ "5,5,0", "5,4,1", "5,6,-1" })
    void available_isOnHandMinusReserved(int onHand, int reserved, int available) {
        var inv = new Inventory("SKU-1", onHand(onHand), reserved(reserved));
        assertThat(inv.available()).isEqualTo(available == -1 ? -1 : available);
    }
}
```

### 10.4 Awaitility for async assertions

```java
@Test
void publishesEventAsynchronously() {
    service.place(cart);

    await().atMost(Duration.ofSeconds(2))
           .pollInterval(Duration.ofMillis(50))
           .untilAsserted(() ->
               assertThat(eventCapture.events()).extracting(Event::type).contains("OrderPlaced"));
}
```

### 10.5 Test configuration base class

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

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
}
```

**Static containers are shared across all subclasses** (Testcontainers keeps them running for the
JVM), and the same annotations mean **one cached Spring context** for the whole suite. This single
pattern can cut a suite from minutes to tens of seconds.

---

## 11. Interview Questions & Model Answers

**Q1. How would you test a REST controller?**
> `@WebMvcTest` with `MockMvc` and the service mocked via `@MockitoBean`. That exercises the parts
> a unit test can't see: routing, JSON serialisation, validation, exception mapping, security
> rules and content types. I'd assert status codes, the contract fields via `jsonPath`, and the
> error shape — and I'd test the negative cases, especially 401/403, because authorisation
> regressions are silent.

**Q2. Why not test repositories with H2?**
> Because H2 isn't the database I run. It differs on types like `jsonb`, `timestamptz` and
> `citext`, on SQL syntax, on constraint behaviour, and on query plans. So a query can pass in H2
> and fail in Postgres. I use `@DataJpaTest` with `@AutoConfigureTestDatabase(replace = NONE)` and
> a Testcontainers Postgres, which also lets me test constraints and native queries for real.

**Q3. How do you keep a Spring test suite fast?**
> Context caching is the main lever. Every distinct combination of `@MockitoBean`, property
> sources and configuration creates a new ApplicationContext, so I standardise test properties in
> one profile, share a base class with the container definitions, and use slices instead of
> `@SpringBootTest` wherever possible. Then split unit (`*Test`, surefire) from integration
> (`*IT`, failsafe) so the fast suite runs on every push.

**Q4. `@MockBean` or `@MockitoBean`?**
> `@MockitoBean` in Boot 3.4+ — `@MockBean` and `@SpyBean` are deprecated because they were tied
> to the old test framework integration. Functionally similar, but the new ones don't force a
> Spring-specific mock factory.

**Q5. Why is `@Transactional` on a test sometimes wrong?**
> Because the transaction never commits, so commit-time behaviour is never exercised —
> `afterCommit` hooks, `@TransactionalEventListener`, deferred constraints and some triggers. Lazy
> loading can also succeed in the test because the session stays open. So it's fine for repository
> tests where I want isolation, but for anything asserting post-commit effects I need a real
> commit, either with `@Commit` or a non-transactional test.

**Q6. How do you test that a service is really transactional?**
> With an integration test against a real database: make the second operation throw inside the
> transactional method and assert that the first write isn't there. That proves rollback actually
> happens — and it catches the self-invocation problem, which no unit test or annotation check
> would find.

**Q7. Mockito mocks or hand-written fakes?**
> Fakes for my own types, because I can assert on state and the test survives refactoring. Mocks
> for things I don't own or can't run — third-party clients, and to simulate failures. And I try
> to mock at my own interface boundary rather than at the HTTP library, so a library upgrade
> doesn't break the tests.

**Q8. How do you test security rules?**
> `spring-security-test` with `@WithMockUser` for role checks and `with(user("ada"))` for identity,
> plus real JWT tokens for the token path. I test the negative cases: anonymous → 401, wrong role
> → 403, another user's resource → 403, expired token → 401. Those are the tests that stop an
> authorisation bug from shipping.

---

## 12. Self-Check & Revision Card

- [ ] I can pick the right slice annotation for each layer.
- [ ] I can explain why context caching dominates suite speed.
- [ ] I can explain why H2 is the wrong choice for repository tests.
- [ ] I can write a controller test with the negative cases.
- [ ] I can test security with `@WithMockUser` and real tokens.
- [ ] I know when `@Transactional` on a test hides a bug.
- [ ] I know `isEqualByComparingTo` for `BigDecimal`.
- [ ] I prefer constructor injection over `@InjectMocks`.

```
┌────────────────── UNIT & SLICE TESTING — 60s RECALL ────────────────────────┐
│ SLICES: @WebMvcTest (controller, mock service) · @DataJpaTest (repo, REAL DB)│
│         @JsonTest (serialisation) · @RestClientTest (client error mapping)   │
│         @SpringBootTest (everything, sparingly) · plain JUnit (domain)       │
│                                                                              │
│ CONTEXT CACHE is the speed lever: every different @MockitoBean set /         │
│ property source = a NEW context. Standardise properties + base class.        │
│                                                                              │
│ @DataJpaTest defaults to H2 — WRONG. replace=NONE + Testcontainers.          │
│ Constraints are behaviour: test them against a real DB.                      │
│                                                                              │
│ TEST THE NEGATIVES: 401 · 403 · 400 validation · 404 · expired token.        │
│ THE FORGOTTEN ASSERTION: after a failure, state did NOT change.              │
│                                                                              │
│ AssertJ: isEqualByComparingTo for BigDecimal (scale trap!).                  │
│ Mockito: BDD given/when/also; strict stubs; mock YOUR boundary, not theirs.  │
│ Prefer constructor injection over @InjectMocks (silent nulls).               │
│                                                                              │
│ @Transactional tests never commit ⇒ afterCommit/events untested.             │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [01 · Testing Strategy & the Test Pyramid](./01-Testing-Strategy-and-the-Test-Pyramid.md) · **Next:** [03 · Testcontainers & Contract Testing](./03-Testcontainers-and-Contract-Testing.md)
