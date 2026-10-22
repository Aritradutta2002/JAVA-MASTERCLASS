# SPRING_BOOT — Quick Reference

> **Last-minute revision sheet.** Every table here is a compressed version of a full chapter —
> use it the night before an interview, then open the linked chapter for the *why*.
>
> The comparison tables and code snippets in this file are carried over and extended from the
> former `SPRING_SPRINGBOOT/QUICK-REFERENCE.md`.

---

## 🔥 Most Asked Topics

| Topic | Frequency | Key Points | Deep dive |
|-------|-----------|------------|-----------|
| Dependency Injection | ⭐⭐⭐⭐⭐ | IoC container, `@Autowired`, constructor injection | [02/02](./02-Spring-Core-Framework/02-Dependency-Injection-and-Bean-Lifecycle.md) |
| Spring Boot Auto-config | ⭐⭐⭐⭐⭐ | `@SpringBootApplication`, `@EnableAutoConfiguration` | [01/02](./01-Spring-Boot-Fundamentals/02-Auto-Configuration-and-Conditional-Internals.md) |
| REST Controllers | ⭐⭐⭐⭐⭐ | `@RestController`, `@RequestMapping`, `@PathVariable` | [03/02](./03-Spring-MVC/02-Controllers-REST-APIs-and-Content-Negotiation.md) |
| Spring Data JPA | ⭐⭐⭐⭐⭐ | `JpaRepository`, query methods, `@Transactional` | [04](./04-Spring-Data-JPA-Hibernate/README.md) |
| Spring Security | ⭐⭐⭐⭐⭐ | Authentication, Authorization, JWT, OAuth2 | [05](./05-Spring-Security/README.md) |
| AOP | ⭐⭐⭐⭐⭐ | `@Aspect`, `@Before`, `@After`, cross-cutting concerns | [02/04](./02-Spring-Core-Framework/04-AOP-Proxy-Mechanics-and-Spring-Modules.md) |
| Bean Scopes | ⭐⭐⭐⭐ | Singleton, Prototype, Request, Session, Application | [02/03](./02-Spring-Core-Framework/03-Bean-Scopes-Component-Scanning-and-Configuration.md) |
| Transaction Propagation | ⭐⭐⭐⭐ | REQUIRED, REQUIRES_NEW, NESTED, … | [04/03](./04-Spring-Data-JPA-Hibernate/03-Transactions-Propagation-and-Isolation.md) |
| N+1 Problem | ⭐⭐⭐⭐ | join fetch, `@EntityGraph`, `@BatchSize` | [04/04](./04-Spring-Data-JPA-Hibernate/04-Lazy-Eager-N-plus-1-and-Fetch-Strategies.md) |
| JWT | ⭐⭐⭐⭐ | HS256/RS256, access vs refresh, rotation, revocation | [06](./06-JWT-Authentication/README.md) |

---

## 📊 Comparison Tables

### Dependency Injection Types

| Type | Annotation | Use Case | Pros | Cons |
|------|-----------|----------|------|------|
| Constructor | `@Autowired` (optional since 4.3) | Mandatory dependencies | Immutable, testable, proxy-safe | Verbose for many deps |
| Setter | `@Autowired` | Optional dependencies | Flexible | Mutable state, half-initialised beans |
| Field | `@Autowired` | Quick prototyping | Concise | **Breaks encapsulation, breaks AOP self-injection, not testable without reflection** |

> ▶ **Interviewer line:** *"Constructor injection — it makes the dependency explicit, the bean
> immutable, and lets the container fail fast at startup instead of at first use."*

### Bean Scopes

| Scope | Lifecycle | Use Case | Managed by container? |
|-------|-----------|----------|-----------------------|
| Singleton | One instance per container (default) | Stateless services | ✅ Yes |
| Prototype | New instance per injection point / lookup | Stateful, per-use objects | ⚠️ Destruction **not** managed |
| Request | One per HTTP request | Request-scoped data | ✅ (web-aware context) |
| Session | One per HTTP session | Session-scoped data | ✅ (web-aware context) |
| Application | One per `ServletContext` | Cross-request singletons | ✅ |
| WebSocket | One per WebSocket session | WebSocket handlers | ✅ |

### Spring vs Spring Boot

| Aspect | Spring | Spring Boot |
|--------|--------|---------------|
| Configuration | XML or Java config | Auto-configuration + starters |
| Dependencies | Manual management | Starter dependencies + BOM |
| Embedded Server | External server (WAR) needed | Embedded Tomcat/Jetty/Undertow |
| Production Ready | Manual setup | Actuator built-in |
| Bean Wiring | Explicit `@Configuration` | Component scan + conditional auto-config |
| Startup | Manual lifecycle | `SpringApplication.run` + failure analysis |

### Lazy vs Eager Fetching

| Aspect | LAZY | EAGER |
|--------|------|-------|
| When loaded | On first access | At query time |
| Query count | Higher (risk of N+1) | Lower |
| Memory | Lower per query | Higher per query |
| `LazyInitializationException` | Possible outside session | Never |
| Use for | Rarely-used associations (`@ManyToOne` on a page) | Always-needed associations, `@ManyToOne` default |

### Cache-aside vs Write-through vs Write-back

| Pattern | Read path | Write path | Stale window | Complexity |
|---------|-----------|------------|--------------|------------|
| Cache-aside | App reads cache → miss → read DB → populate | App writes DB → evict cache | Yes (TTL) | Low ✅ |
| Write-through | Cache | App writes cache → cache writes DB synchronously | No | Medium |
| Write-around | Cache → miss → read DB (bypass write) | Cache only new values | No (on write) | Medium |
| Write-back | Cache | Async batch to DB | Yes (until flush) | High |
| Read-through | Cache loads from DB itself | App writes DB → evict | Yes (TTL) | Low |

### Kafka vs RabbitMQ

| Aspect | Kafka | RabbitMQ |
|--------|-------|----------|
| Model | Distributed commit log | Message broker with queues |
| Ordering | Per partition | Per queue (with caveats) |
| Throughput | Very high (batching) | Moderate |
| Routing | Consumer groups only | Rich (direct/topic/fanout/headers) |
| Message priority | No | Yes |
| Best for | Event streaming, high volume, replay | Work queues, task distribution, RPC |

### Resilience4j Decorators

| Decorator | Order | Purpose |
|-----------|-------|---------|
| `@TimeLimiter` | Outermost | Bounds total time |
| `@Retry` | Outer | Retries transient failures |
| `@CircuitBreaker` | Middle | Stops calling a failing dependency |
| `@RateLimiter` | Inner | Limits call rate |
| `@Bulkhead` | Innermost | Isolates concurrency |

### HTTP Status Codes for REST APIs

| Code | Meaning | Spring mapping |
|------|---------|----------------|
| 200 | OK | `ResponseEntity.ok()` |
| 201 | Created | `.status(HttpStatus.CREATED)` + `Location` |
| 202 | Accepted | `HttpStatus.ACCEPTED` |
| 204 | No Content | `HttpStatus.NO_CONTENT` |
| 400 | Bad Request | `MethodArgumentNotValidException` |
| 401 | Unauthorized | `AuthenticationException` → `AuthenticationEntryPoint` |
| 403 | Forbidden | `AccessDeniedException` → `AccessDeniedHandler` |
| 404 | Not Found | custom `NotFoundException` |
| 409 | Conflict | `DataIntegrityViolationException` |
| 422 | Unprocessable | semantic validation failure |
| 429 | Too Many Requests | rate-limit filter |
| 500 | Internal Server Error | `catch-all` handler |
| 503 | Service Unavailable | circuit-open fallback |

---

## 📝 Common Interview Questions

1. **Q**: What is Dependency Injection?
   **A**: A design pattern where objects receive their dependencies from an external source (the
   IoC container) rather than constructing them with `new`. In Spring the container resolves the
   dependency graph from `BeanDefinition`s and injects the collaborators, which makes the
   components decoupled, swappable and unit-testable.

2. **Q**: Difference between `@Component`, `@Service`, `@Repository`?
   **A**: All three are stereotype annotations that make the class a bean candidate for component
   scanning; `@Service` and `@Repository` are semantic markers for the layer. The functional
   difference is that `@Repository` triggers `PersistenceExceptionTranslationPostProcessor`, so
   JPA exceptions are translated into Spring's unchecked `DataAccessException` hierarchy.

3. **Q**: How does Spring Boot auto-configuration work?
   **A**: `@EnableAutoConfiguration` imports the candidates listed in
   `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Each
   candidate is a `@Configuration` class whose beans are guarded by `@Conditional...`
   annotations evaluated against the classpath, the property values and the existing beans.
   A bean is registered only when all its conditions match; the evaluation is recorded in the
   `ConditionEvaluationReport`.

4. **Q**: What is `@Transactional` propagation?
   **A**: It defines how a transactional method behaves when it is called inside another
   transaction. `REQUIRED` joins the existing transaction, `REQUIRES_NEW` suspends it and starts
   a new one (which requires a **second connection** from the pool), and `NESTED` creates a
   savepoint inside the current transaction.

5. **Q**: Difference between `@RestController` and `@Controller`?
   **A**: `@RestController` is `@Controller` + `@ResponseBody` on every method, so the return
   value is serialised into the response body by an `HttpMessageConverter` instead of being
   resolved as a view name.

6. **Q**: Why does a self-invoked `@Transactional` method not roll back?
   **A**: `@Transactional` is implemented by a proxy. A call from `this.method()` inside the same
   object never leaves the target instance, so the proxy — and therefore the transaction advice —
   is never invoked.

7. **Q**: Why use a refresh token at all if access tokens are short-lived?
   **A**: Because refresh lets you revoke access without a server-side session. The short-lived
   access token is stateless and cheap; the long-lived refresh token is stored hashed
   server-side, so it can be rotated and revoked on logout, password change or compromise.

8. **Q**: What is the N+1 problem?
   **A**: Loading a list of entities and then touching a lazy association on each one issues
   1 + N queries. The fix is to fetch the association eagerly in the same query — join fetch,
   `@EntityGraph`, or `@BatchSize` — while being careful that fetching a *collection* with a join
   produces a cartesian product and needs `distinct`.

9. **Q**: How do you size a database connection pool?
   **A**: With Little's law: `pool size ≈ concurrent requests × average transaction latency`.
   A typical web service gets by with 10–20 connections per instance; going higher usually
   makes the database slower rather than the application faster, because each connection is a
   server-side process.

10. **Q**: Why is `@Transactional` on a `@PostMapping` not enough for a read-only query?
    **A**: A transaction opened in the `REQUIRED` propagation will *join* an existing
    transaction, so the read-only hint is ignored. Use `@Transactional(readOnly = true,
    propagation = REQUIRES_NEW)` to get a separate read-only transaction with a dedicated
    read replica.

---

## ✅ Checklist

### Spring Boot Fundamentals
- [ ] Understand auto-configuration and `@Conditional`
- [ ] Know starter dependencies and the BOM
- [ ] Understand `application.yml` precedence and profiles
- [ ] Know Actuator endpoints and how to expose them safely
- [ ] Can trace `SpringApplication.run` → `refresh()` → embedded server start

### Core Spring
- [ ] Understand IoC and DI
- [ ] Know the bean lifecycle and all its callbacks
- [ ] Understand AOP concepts and proxy mechanics
- [ ] Know different bean scopes
- [ ] Know `BeanFactoryPostProcessor` vs `BeanPostProcessor`

### Spring MVC
- [ ] Build REST APIs
- [ ] Handle exceptions globally
- [ ] Validate request data
- [ ] Understand content negotiation
- [ ] Know the 9 `DispatcherServlet` strategies
- [ ] Know Filter vs Interceptor vs Aspect ordering

### Spring Data JPA
- [ ] Use `JpaRepository`
- [ ] Write custom queries
- [ ] Understand `@Transactional`
- [ ] Handle relationships
- [ ] Diagnose and fix the N+1 problem
- [ ] Know the 4 entity states and `merge` semantics

### Spring Security
- [ ] Configure authentication
- [ ] Implement authorization
- [ ] Use JWT tokens
- [ ] Understand OAuth2 flows
- [ ] Know the filter chain order and the role of each filter
- [ ] Distinguish 401 from 403 precisely

### Microservices & Messaging
- [ ] Service discovery and gateway
- [ ] Circuit breaker and timeout ordering
- [ ] Kafka partitions, consumer groups, ordering
- [ ] Idempotent consumers, DLQ, retry

### Testing & DevOps
- [ ] Unit vs integration vs slice tests
- [ ] Testcontainers for real dependencies
- [ ] Multi-stage Docker build and layered JAR
- [ ] Liveness vs readiness probes
- [ ] Metrics, logs, traces — the three pillars

---

## ⚡ Code Snippets

### Basic REST Controller
```java
package com.example.order.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    // Constructor injection: explicit, immutable, testable.
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.list(page, size));
    }
}
```

### Spring Data JPA Repository
```java
package com.example.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Derived query — Spring Data parses the method name.
    List<User> findByLastName(String lastName);

    // JPQL — portable across JPA providers.
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmail(@Param("email") email);

    // Explicit JPQL with a join fetch to avoid the N+1 problem.
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") Long id);

    // Spring Data JPA 3.x pagination — the count query is derived for you.
    Page<User> findByActiveTrue(Pageable pageable);
}
```

### Transaction Management
```java
package com.example.order.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransferService {

    private final AccountRepository accountRepository;

    public TransferService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional // REQUIRED: both operations share ONE transaction and ONE connection.
    public void transferMoney(Long fromId, Long toId, BigDecimal amount) {
        accountRepository.debit(fromId, amount);
        accountRepository.credit(toId, amount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeAuditLog(Long accountId, String message) {
        // Commits independently of the outer transaction.
        // WARNING: requires a SECOND connection — check the pool size.
        auditRepository.save(new AuditLog(accountId, message));
    }
}
```

### Global Exception Handler
```java
package com.example.order.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        problem.setType(URI.create("https://errors.example.com/not-found"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
           .forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // Never leak the message — log it, return a generic body.
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal server error");
        problem.setProperty("traceId", MDC.get("traceId"));
        return problem;
    }
}
```

### JWT Filter (Spring Boot 3 / Security 6)
```java
package com.example.order.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(7);
            try {
                // validate() verifies signature, exp, nbf and the issuer.
                var claims = jwtService.validate(token);
                var authentication = jwtService.toAuthentication(claims);
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException ex) {
                // Do NOT throw — let the entry point produce a 401.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
```

---

## ⚡ The 30-Second Cheat Sheet

| Question | Answer in one line |
|----------|--------------------|
| Auto-config? | `AutoConfiguration.imports` + `@Conditional` evaluated against classpath/properties/beans |
| DI type to use? | Constructor — immutable, fail-fast, proxy-safe |
| Bean lifecycle? | Instantiate → populate → aware callbacks → `BeanPostProcessor` → init → use → destroy |
| AOP proxy? | JDK proxy if the target implements an interface, else CGLIB |
| `@Transactional` broken? | Self-invocation bypasses the proxy |
| N+1 fix? | Join fetch / `@EntityGraph` / `@BatchSize` — never blanket eager loading |
| Propagation? | `REQUIRED` joins, `REQUIRES_NEW` needs a 2nd connection, `NESTED` = savepoint |
| 401 vs 403? | No/invalid credentials vs valid credentials, insufficient authority |
| JWT stateless? | The **access** token is; revocation forces a denylist |
| HS256 vs RS256? | HS256 needs the secret in every verifier; RS256 lets verifiers hold only the public key |
| OAuth2 vs OIDC? | OIDC adds identity: ID token + userinfo endpoint |
| Kafka ordering? | Within a partition only |
| Cache stampede? | Single-flight / distributed lock / stale-while-revalidate |
| Pool size? | Little's law: concurrency × latency |
| Liveness vs readiness? | Liveness = restart me; readiness = route traffic to me |

---

*Quick reference for last-minute review · Spring Boot 3.3+ | Spring Framework 6.1+ | Java 17+*
*Full depth → [SPRING_BOOT/README.md](./README.md) · Standard → [SPRING_BOOT/STANDARD.md](./STANDARD.md)*
