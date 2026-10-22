# 07 · Security Best Practices — The Consolidated Checklist

> **Level:** All levels (the reference chapter)
> **Interview Frequency:** ★★★★★ (the summary chapter — the "what would you do?" question)
> **Prerequisite:** [06 · Production Security Patterns](./06-Production-Security-Patterns.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. The Master Checklist](#1-the-master-checklist)
  - [1.1 Dependencies & Build](#11-dependencies--build)
  - [1.2 Secrets & Configuration](#12-secrets--configuration)
  - [1.3 Authentication](#13-authentication)
  - [1.4 Passwords](#14-passwords)
  - [1.5 Tokens & Sessions](#15-tokens--sessions)
  - [1.6 Authorization](#16-authorization)
  - [1.7 Data Access & IDOR](#17-data-access--idor)
  - [1.8 Multi-Tenancy](#18-multi-tenancy)
  - [1.9 Web Security (CSRF, CORS, headers)](#19-web-security-csrf-cors-headers)
  - [1.10 Input & Output](#110-input--output)
  - [1.11 File Uploads](#111-file-uploads)
  - [1.12 Logging & Auditing](#112-logging--auditing)
  - [1.13 Rate Limiting & Abuse](#113-rate-limiting--abuse)
  - [1.14 Error Handling](#114-error-handling)
  - [1.15 API Documentation](#115-api-documentation)
  - [1.16 Infrastructure & Deployment](#116-infrastructure--deployment)
  - [1.17 Dependencies & Supply Chain](#117-dependencies--supply-chain)
  - [1.18 Testing](#118-testing)
- [2. The Anti-Patterns Catalogue](#2-the-anti-patterns-catalogue)
- [3. The Configuration Reference](#3-the-configuration-reference)
- [4. The Pre-Production Verification](#4-the-pre-production-verification)
- [5. The Incident Response Cheatsheet](#5-the-incident-response-cheatsheet)
- [6. Interview Q&A — Level-wise](#6-interview-qa--level-wise)
- [7. Counter-Questions & Traps](#7-counter-questions--traps)
- [8. Tricky Interview Questions](#8-tricky-interview-questions)
- [9. Follow-Up Questions Asked by Interviewers](#9-follow-up-questions-asked-by-interviewers)
- [10. Real 3–5 Year Experience Scenarios](#10-real-35-year-experience-scenarios)
- [11. Frequently Asked Production Issues](#11-frequently-asked-production-issues)
- [12. Debugging Scenarios](#12-debugging-scenarios)
- [13. Architecture Design Questions](#13-architecture-design-questions)
- [14. Rapid-Fire MCQs](#14-rapid-fire-mcqs)
- [15. Cheat Sheet — One-Page Summary](#15-cheat-sheet--one-page-summary)
- [16. Follow-up Learning](#16-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

This chapter is the **consolidated, copy-pasteable** view of everything in chapters 01–06:
dependencies, secrets, authentication, passwords, tokens, authorization, data access,
multi-tenancy, web protections, logging, rate limiting, error handling, infrastructure,
the supply chain, and testing.

**Ten non-negotiables:**

1. **Password hashing** — BCrypt/Argon2 through the `PasswordEncoder` (never plain MD5/SHA).
2. **Default-deny** at the URL level; method-level checks for fine-grained rules; **query-level
   ownership** checks for IDOR.
3. **Secrets out of the code** — env vars or a vault, rotated, exposed as little as possible.
4. **CSRF on** for cookie-based flows; off only for header-based bearer chains.
5. **CORS with explicit origins**, never `*` together with credentials.
6. **Validation of input** at the boundary; **encoding of output** at the edge.
7. **Rate limiting** on login and on expensive endpoints.
8. **Audit** of authentication events, authorization denials, and sensitive data access.
9. **Security headers** — CSP, HSTS, frame options, referrer policy.
10. **Dependency updates** — automated scanning, because unpatched CVEs are how breaches happen.

---

## 1. The Master Checklist

### 1.1 Dependencies & Build

- [ ] The Spring Boot version is **current and supported** (inside the free support window).
- [ ] `spring-boot-starter-parent` (or the BOM import) manages dependency versions.
- [ ] **No known CVEs** in the dependency tree (`mvn dependency-check` / `osv-scanner`).
- [ ] `spring-boot-starter-actuator` is exposed **only on the management port** (or otherwise
  restricted) — never publicly.
- [ ] The actuator endpoint exposure is explicit:
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: health,info,metrics,prometheus
    endpoint:
      health:
        show-details: when-authorized     # never "always" in prod
  ```
- [ ] `devtools` stays in the `runtime` scope (dev only) — it must not land in the production
  artifact.
- [ ] `lombok` is `provided` scope, so it does not leak into the runtime.

### 1.2 Secrets & Configuration

- [ ] **No secret in the code** — not in `application.yml`, not in committed test fixtures.
- [ ] Secrets are externalised: env vars, a vault, or the cloud secret manager.
- [ ] `spring.config.import` is configured for the vault / config server.
- [ ] **Encrypted properties** (Jasypt) if they must live in the config file.
- [ ] A **rotation procedure** is documented and has been exercised.
- [ ] The production profile is **strict** — unknown properties fail fast
  (`spring.config.validate`, or `@ConfigurationProperties` with JSR-380 validation).
- [ ] The **actuator `env`/`beans`/`configprops` endpoints are hidden** — they dump the
  configuration, sometimes including secrets.
- [ ] Logs never print the properties (`debug` level off in production).

### 1.3 Authentication

- [ ] `UserDetailsService` (or an `AuthenticationProvider`) is implemented — **never** the
  default one with the generated password.
- [ ] **Enumeration-safe errors** — the same generic "invalid credentials" for a wrong user
  *and* a wrong password (identical response body, comparable timing).
- [ ] **MFA** for admins and for sensitive operations.
- [ ] **Account lockout / backoff** on repeated failures.
- [ ] **Session fixation** protection (`sessionFixation().changeSessionId()`) — the default in
  Spring Security, but verify nobody disabled it.
- [ ] **Concurrent session control** (`maximumSessions`) if the app is session-based.
- [ ] **Logout** invalidates the session and clears the context (`invalidateHttpSession`,
  `clearAuthentication`, `deleteCookies("JSESSIONID")`).
- [ ] **Remember-me** is off by default; if enabled, its key is rotated and the token is
  short-lived.

### 1.4 Passwords

- [ ] **`DelegatingPasswordEncoder`** (or BCrypt/Argon2 directly) — `{bcrypt}` prefixed.
  ```java
  @Bean
  PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder(12);   // or Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
  }
  ```
- [ ] **Never** MD5/SHA-1/SHA-256 (fast hashes make brute force cheap). Never a bare hash.
- [ ] Salting is automatic (BCrypt's internal salt) — never a shared salt.
- [ ] The **cost factor** is calibrated so verification takes ~100–250 ms on production
  hardware (measure it; do not guess).
- [ ] The **password policy**: length (12+, or passphrases), a breached-password check (the
  Have I Been Pwned k-anonymity API), no composition rules (per NIST guidance), no forced
  expiry except for admins, no reuse of breached passwords, and **MFA instead of complexity
  rules**.
- [ ] The **reset flow**: random, single-use, short-expiry token; sessions invalidated after
  the reset; a notification sent to the old address.

### 1.5 Tokens & Sessions

- [ ] **Short-lived access tokens** (5–15 min) and **rotating refresh tokens** (with reuse
  detection).
- [ ] Refresh tokens are **stored hashed** in the database (SHA-256 of the token) — the
  classic bug is storing them raw, where one database leak becomes account takeover.
- [ ] Refresh tokens are **bound** to user + device + family (rotation lineage), so reuse
  detection revokes the whole family.
- [ ] **Cookies** (when used): `HttpOnly`, `Secure`, `SameSite=Lax|Strict`, `Path=/`, and the
  narrowest possible `Domain`.
- [ ] **JWT claims**: `iss`, `aud`, `exp`, `iat`, `nbf`, `sub`, `jti`, `scope`/`roles`,
  `amr`/`acr` (authentication methods).
- [ ] An **algorithm allow-list** — the decoder must reject `none` and HS256/RS256 confusion:
  ```java
  @Bean
  JwtDecoder jwtDecoder() {
      NimbusJwtDecoder decoder = NimbusJwtDecoder
              .withPublicKey(publicKey).build();
      decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("https://issuer"));
      return decoder;
  }
  ```
  (With a symmetric key use `NimbusJwtDecoder.withSecretKey(...)` — and **never** accept both
  symmetric and asymmetric keys at once.)
- [ ] The **`kid` header** is present (key rotation).
- [ ] **Clock skew** is bounded (`setClockSkew(Duration.ofSeconds(60))`).

### 1.6 Authorization

- [ ] **Default-deny**: `.anyRequest().authenticated()`.
- [ ] **Method-level security** is enabled (`@EnableMethodSecurity`) and used for non-trivial
  rules.
- [ ] **Ownership checks at the data-access layer** (in the query, not the controller).
- [ ] **Separation of duties** (initiator ≠ approver) for money-moving and other sensitive
  operations.
- [ ] **Least privilege** — narrow roles; service accounts scoped to what they need.
- [ ] **`@PreAuthorize` over `@PostAuthorize`** for writes: pre means "don't even try"; post
  means "the action already happened, then it was denied" — with side effects already applied.
- [ ] **Privilege-escalation paths are closed**: users cannot set their own roles (roles come
  from the database, a trusted source).
- [ ] **Admin endpoints are separated** — different chain, different port, network isolation,
  MFA.

### 1.7 Data Access & IDOR

> **IDOR is the most common real-world breach.** It bypasses authentication entirely.

- [ ] Every query touching a user-owned resource includes the **owner predicate**:
  ```java
  Optional<Order> findByIdAndUserId(Long id, Long userId);   // NOT findById(id)
  ```
- [ ] **Existence vs permission** — return **404** (not 403) for a resource that isn't yours
  (403 confirms existence, which enables enumeration).
- [ ] **Spring Data specifications** for dynamic filters:
  ```java
  Specification<Order> spec = (root, query, cb) ->
          cb.and(cb.equal(root.get("userId"), currentUserId), ...);
  ```
- [ ] **Mass assignment is prevented** — bind DTOs, never entities:
  ```java
  @PostMapping
  OrderResponse create(@Valid @RequestBody CreateOrderRequest request) { ... }   // DTO
  ```
- [ ] **Query parameters are bound** (`@RequestParam`), never concatenated.
- [ ] **JPQL and native queries are parameterised.**

### 1.8 Multi-Tenancy

- [ ] Tenant id is **derived from the authentication**, never from the request body or path
  (unless a path tenant id is verified against the caller's membership).
- [ ] **Query-level scoping** — `@TenantId` (Hibernate 6) or an explicit specification.
- [ ] **Database row-level security** as the last line of defence (PostgreSQL RLS).
- [ ] **Cache keys include the tenant id.**
- [ ] **Tests prove cross-tenant access fails.**
- [ ] **Background jobs set the tenant context** (`@Async`, schedulers, message consumers).

### 1.9 Web Security (CSRF, CORS, headers)

- [ ] **CSRF is enabled** for cookie-based endpoints; the token comes from
  `CookieCsrfTokenRepository` (SPA-friendly) or `HttpSessionCsrfTokenRepository`.
- [ ] CSRF is **disabled only** for stateless bearer chains — and that decision is documented.
- [ ] **CORS**:
  ```java
  @Bean
  CorsConfigurationSource corsConfigurationSource() {
      CorsConfiguration configuration = new CorsConfiguration();
      configuration.setAllowedOrigins(List.of("https://app.example.com"));   // exact
      configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
      configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-TOKEN"));
      configuration.setAllowCredentials(true);
      configuration.setMaxAge(Duration.ofHours(1));
      UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
      source.registerCorsConfiguration("/api/**", configuration);
      return source;
  }
  ```
- [ ] **Security headers**: CSP, HSTS (including subdomains), `X-Content-Type-Options:
  nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`.
- [ ] **`X-Request-Id`** is generated and propagated.
- [ ] **Error pages** do not leak stack traces.

### 1.10 Input & Output

- [ ] **Validation at the boundary** with `jakarta.validation`:
  ```java
  public record CreateUserRequest(
      @NotBlank @Size(min = 8, max = 72) String password,
      @NotBlank @Email String email,
      @NotNull @Positive Long tenantId) {}
  ```
- [ ] **`@Validated`** on the class for method-parameter validation.
- [ ] **Output encoding** — template-engine auto-escaping (the Thymeleaf default), sane JSON
  serialisation, no raw string concatenation into markup.
- [ ] **SQL/HQL injection** — parameterised queries everywhere.
- [ ] **Command injection** — never run a shell with user input (`ProcessBuilder` with an
  argument array, never a shell string).
- [ ] **Deserialisation** — Jackson's `activateDefaultTyping` is off; polymorphic
  `@JsonTypeInfo` is restricted.
- [ ] **SSRF** — validate URLs (scheme, host allow-list, block private IP ranges) whenever the
  app fetches user-supplied URLs.
- [ ] **XXE** — XML parsers have external entities disabled (`DocumentBuilderFactory`
  features, Jackson's `XMLInputFactory`).
- [ ] **Mass assignment** — DTOs only.
- [ ] **Paging limits** — cap the `Pageable` size (`maxPageSize`).

### 1.11 File Uploads

- [ ] **Size limit** (`spring.servlet.multipart.max-file-size`) and a **type allow-list**
  (extension + magic bytes, not the client-controlled `Content-Type` header).
- [ ] **Storage isolation** — files live outside the web root, or are served through a
  controller that checks authorization; never put the user's filename in the path.
- [ ] **Path traversal** — generated names (UUID) plus a canonical-path check.
- [ ] **Malware scanning** (ClamAV or a provider) for user uploads.
- [ ] **Separate CDN/domain** for public assets, so they don't share the app origin's cookies.
- [ ] **Image re-encoding** — strips payloads and avoids ImageMagick/Ghostscript vulnerabilities.

### 1.12 Logging & Auditing

- [ ] **Audit events**: authentication success/failure, authorization denials, privilege
  changes, sensitive data access, configuration changes.
- [ ] **Redaction** — passwords, tokens, card numbers, masked PII:
  ```java
  log.info("user={} action={}", maskEmail(email), "LOGIN");
  ```
- [ ] **Correlation** (`X-Request-Id` in the MDC).
- [ ] **Append-only audit store** (database triggers, hash chain, off-box shipping).
- [ ] **Alerting**: failed-login spikes, denial spikes, privileged actions, off-hours activity.
- [ ] **Log retention** and access control (PII in logs is still PII).

### 1.13 Rate Limiting & Abuse

- [ ] **Login** is limited (per account and per IP).
- [ ] **MFA verification** is limited (a 6-digit code is brute-forceable).
- [ ] **Password reset** and **registration** are limited (enumeration, spam).
- [ ] **Expensive endpoints** have cost-based limits plus a concurrency bulkhead.
- [ ] **`429` responses carry `Retry-After`.**
- [ ] The limiter is **distributed** (Redis) in a clustered deployment.
- [ ] **Quotas** per plan/tenant enforced at the gateway.

### 1.14 Error Handling

- [ ] **Stack traces are never returned** — `server.error.include-stacktrace=never`, and
  `@ExceptionHandler`s that return safe messages.
- [ ] **404 vs 403** — 404 for resources that aren't yours.
- [ ] **Validation errors are structured** (field + message) and do not leak internals.
- [ ] **Error codes are stable** — clients depend on them.

### 1.15 API Documentation

- [ ] The **OpenAPI spec** is generated (springdoc) and **versioned**.
- [ ] The **spec does not expose internals** — internal endpoints, admin routes, data models
  with sensitive fields.
- [ ] The **spec is accurate** — a test asserts documented endpoints exist (springdoc coverage
  check).
- [ ] **Docs are protected** — the internal spec sits behind auth or isn't deployed publicly.
- [ ] **Deprecation policy** — sunset headers and a migration guide.

### 1.16 Infrastructure & Deployment

- [ ] **TLS everywhere** — TLS 1.2+ (1.3 preferred), HSTS, certificate automation
  (cert-manager/Let's Encrypt), renewal monitoring.
- [ ] **Containers run as non-root**, with a read-only filesystem and a minimal base image.
- [ ] **Secret isolation** — Kubernetes Secrets are base64, not encryption, so add etcd
  encryption at rest, the external-secrets operator, or a vault.
- [ ] **Network segmentation** — management port, database, and internal services are not public.
- [ ] **Database credentials are least-privilege** — the app user cannot run DDL, read the
  audit history, or see other schemas.
- [ ] **API gateway** — TLS termination, rate limiting, WAF.
- [ ] **Environment parity** — production-like staging with production-shaped (anonymised) data.

### 1.17 Dependencies & Supply Chain

- [ ] **Automated updates** (Dependabot/Renovate) with a green CI.
- [ ] **SCA in CI** (`dependency-check`, `osv-scanner`, `trivy`).
- [ ] **SAST** (Semgrep rules, SpotBugs).
- [ ] **Secret scanning** (Gitleaks, pre-commit hook).
- [ ] **Container scanning** (Trivy, Grype).
- [ ] **SBOM** (CycloneDX) generated and archived.
- [ ] **Provenance/signing** of artifacts (Sigstore, SLSA).

### 1.18 Testing

- [ ] **Authentication tests** — unauthenticated → 401, wrong role → 403, right role → 200.
- [ ] **Authorization tests** — the **IDOR test** (another user's resource → 404).
- [ ] **Multi-tenancy tests** — cross-tenant access → 404.
- [ ] **CSRF test** — POST without a token → 403.
- [ ] **CORS test** — wrong origin → no `Access-Control-Allow-Origin`.
- [ ] **Header test** — CSP and HSTS are present.
- [ ] **Rate-limiter test** — 429 after the limit.
- [ ] **Validation test** — invalid input → 400 with field errors.
- [ ] **Password test** — encoder round-trip and weak-password rejection.
- [ ] A **security regression suite in CI** — a test that fails if the chain's configuration
  is loosened (a "security guard" test):
  ```java
  @Test
  void actuatorShouldNotBePublic() throws Exception {
      mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
  }
  ```

---

## 2. The Anti-Patterns Catalogue

| # | Anti-pattern | Symptom | Fix |
|---|--------------|---------|-----|
| 1 | `findById(id)` then check the owner in the controller | IDOR | Owner predicate in the query |
| 2 | `@PreAuthorize` after the side effect | The action already happened | Pre-check |
| 3 | `permitAll()` on `/**` in production | Wide open | Default-deny + a profile guard |
| 4 | CSRF disabled globally | Cookie endpoints exposed | Per-chain configuration |
| 5 | CORS `*` with `allowCredentials(true)` | Browser rejects / misconfiguration | Explicit origins |
| 6 | Hardcoded JWT secret | Token forgery | Env var + rotation |
| 7 | Refresh token stored raw | One DB leak = takeover | Hashed storage |
| 8 | `md5`/`sha256` for passwords | Rainbow tables | BCrypt/Argon2 |
| 9 | `user.getPassword()` in the log | Credentials in logs | Redaction |
| 10 | Entity bound directly from the request | Mass assignment | DTOs |
| 11 | Public `/actuator/**` | Config dump | Management port + restriction |
| 12 | Stack traces in responses | Info leak | `@RestControllerAdvice` |
| 13 | `permitAll` for the H2 console | Production DB access | Profile guard |
| 14 | `spring.jpa.show-sql=true` in production | Data in logs | Profile guard |
| 15 | Blindly trusting `X-Forwarded-For` | Rate-limiter bypass / IP spoofing | Trusted proxy list only |
| 16 | `SecurityContext` in `@Async` without a decorator | Lost or wrong identity | `TaskDecorator` |
| 17 | One chain for both web and API | CSRF/session mess | Multiple chains with `@Order` |
| 18 | Inconsistent `ROLE_USER` / `ROLE_ADMIN` | 403 for admins | Consistent prefixing |
| 19 | Rate limiting only in the browser | Trivial bypass | Server side |
| 20 | Trusting a client-supplied `userId` | IDOR | Derive from authentication |

---

## 3. The Configuration Reference

### 3.1 A minimal, production-shaped `application.yml`

```yaml
server:
  port: 8080
  forward-headers-strategy: framework
  error:
    include-stacktrace: never
    include-message: never
  servlet:
    session:
      cookie:
        http-only: true
        secure: true
        same-site: lax
      timeout: 30m
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

spring:
  application:
    name: myapp
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:20}
      connection-timeout: 30000
      leak-detection-threshold: 60000
  jpa:
    open-in-view: false          # prevents LazyInitializationException surprises
    hibernate:
      ddl-auto: validate          # never "update" in production
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false

management:
  server:
    port: 9090                  # separate management port
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true

logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{requestId:-},%X{traceId:-}]"
  level:
    root: INFO
    org.springframework.security: INFO
```

### 3.2 The recommended starters

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

---

## 4. The Pre-Production Verification

A **runnable checklist** — run it before every release.

```bash
# ① Unauthenticated access is rejected
curl -s -o /dev/null -w "%{http_code}\n" https://api.example.com/api/orders    # 401

# ② Security headers are present
curl -sI https://api.example.com/api/orders | grep -iE \
  "strict-transport-security|content-security-policy|x-frame-options|x-content-type-options|referrer-policy"

# ③ CORS enforces the origin allow-list
curl -sI -H "Origin: https://evil.com" https://api.example.com/api/orders \
  | grep -i "access-control-allow-origin"        # (nothing)

# ④ The allowed origin works
curl -sI -H "Origin: https://app.example.com" https://api.example.com/api/orders \
  | grep -i "access-control-allow-origin"        # https://app.example.com

# ⑤ Actuator is not public
curl -s -o /dev/null -w "%{http_code}\n" https://api.example.com/actuator/env  # 404 / 401

# ⑥ Error responses don't leak
curl -s https://api.example.com/api/orders/abc | jq .   # no stack trace, no SQL

# ⑦ Rate limiting works
for i in $(seq 1 10); do
  curl -s -o /dev/null -w "%{http_code} " -X POST https://api.example.com/api/auth/login \
    -H 'Content-Type: application/json' -d '{"email":"a@b.com","password":"x"}'
done                                            # ... 429
```

---

## 5. The Incident Response Cheatsheet

| Incident | Immediate action | Containment | Prevention |
|----------|------------------|-------------|------------|
| **Account compromise** | Invalidate sessions and tokens | Rotate password/MFA, review the audit trail | MFA, alerting |
| **Token leak** | Blocklist the `jti` / bump the token version | Rotate the signing key, review usage | httpOnly cookies, short TTL |
| **Secret leak** | Rotate the credential | Redeploy, review access | Vault, secret scanning |
| **Dependency CVE** | Assess exposure | Patch, mitigate | Automated updates |
| **IDOR discovered** | Disable the endpoint | Audit access logs, notify affected users | Ownership queries, tests |
| **Data breach** | Isolate, preserve evidence | Assess scope, notify (GDPR 72h) | Encryption, least privilege |
| **Credential stuffing** | Rate limit / block | Review, enforce MFA | Lockout, monitoring |

---

## 6. Interview Q&A — Level-wise

### 🟢 Beginner
**Q: What are the most important security practices in a Spring Boot app?**
**A:** (1) Password hashing (BCrypt via `PasswordEncoder`), (2) default-deny authorization at
both URL and method level, (3) input validation with `jakarta.validation`, (4) correct CSRF and
CORS configuration, (5) secrets out of the code, (6) security headers, (7) auditing logins,
(8) dependency updates.

**Q: Why is `open-in-view=false` a security/robustness recommendation?**
**A:** It is mainly a **performance and correctness** setting — lazy loading outside the
transaction causes `LazyInitializationException` and accidental N+1s — but it also prevents the
view layer from silently triggering queries. It is usually listed under performance; be honest
about that in an interview.

### 🟡 Intermediate
**Q: How do you prevent IDOR?**
**A:** An ownership predicate in the query (`findByIdAndUserId`), never `findById` followed by
a check; DTOs to prevent mass assignment; 404 (not 403) for resources that aren't yours; and
automated tests asserting that cross-user access is rejected.

**Q: What is the difference between authentication and authorization in Spring Security?**
**A:** **Authentication** answers "who are you" (`AuthenticationManager` →
`AuthenticationProvider` → the `Authentication` token). **Authorization** answers "what may you
do" (`AuthorizationManager`, historically `AccessDecisionManager`, with `@PreAuthorize` /
`@PostAuthorize` at method level). `SecurityContext` holds the result.

**Q: When would you use `@PreAuthorize` vs `@PostAuthorize`?**
**A:** `@PreAuthorize` runs before the method — use it for writes and sensitive operations,
where side effects must not happen. `@PostAuthorize` runs after the method returns, typically
to filter a returned collection ("only the objects I own") — read-only, side-effect-free.
The classic mistake is `@PostAuthorize` on a write, when the money has already moved.

### 🔴 Advanced / Senior
**Q: How do you design security for a multi-tenant SaaS?**
**A:** The identity layer (SSO, tenant claim), tenant resolution (subdomain or header,
validated against membership), **query-level scoping** (`@TenantId` or an explicit
specification), **database RLS** as the last defence, **tenant-aware cache keys**,
**tenant context in background jobs**, and **cross-tenant tests** at every layer. The
principle: tenant identity is derived from authentication, never from client input.

**Q: How do you handle security configuration across environments?**
**A:** Same code, externalised values (origins, secrets, limits) via per-environment profiles;
**fail-fast validation** (`@ConfigurationProperties` validation and startup assertions such as
"`permitAll` on `/**` is forbidden outside dev"); a **pipeline security smoke test** against
the deployed environment; and **IaC policy checks**. The dangerous pattern is a permissive dev
config leaking into production — defend against it with automated checks, not vigilance.

**Q: How do you rotate secrets without downtime?**
**A:** The dual-key pattern: deploy the new key alongside the old, consumers accept both while
verifying, issuers switch to the new, the old is retired after the maximum TTL. Examples: JWT
signing keys (`kid` + multiple JWKS keys), database passwords (the app accepts both, the role
is changed, the old credential is revoked), API consumer secrets (accept both, mark the new one
active, retire the old).

### ⚫ Expert / Principal
**Q: Design security governance for a company.**
> (1) A **policy document** (acceptable use, data classification, access model, incident
response, vendor requirements); (2) the **identity lifecycle** (joiner/mover/leaver, access
reviews, service-account inventory); (3) **automated controls** (SSO enforcement, SCIM,
policy-as-code, IaC checks, CI SAST/SCA/secret scanning); (4) **detective controls** (SIEM,
UEBA, access analytics, anomaly detection); (5) **evidence collection** (audit logs,
compliance reports, metric retention); (6) **response readiness** (playbooks, exercises, RACI,
communication plan); (7) **metrics** (MTTD, MTTR, patch latency, MFA coverage,
least-privilege coverage, incident count).

---

## 7. Counter-Questions & Traps

### CQ1. "Is the IDOR check in the controller good enough?"
- **Naive answer:** "Yes, I check the owner in the controller."  ← the trap
- **The trap:** The interviewer wants you to think at the data-access layer.
- **Expert answer:** It *works* if done correctly, but it is **fragile by design**:
(1) **omission is silent** — a new endpoint with `findById` and no check is a breach waiting
to happen, and no compiler warns you; (2) **the two-step** (load, then check) is a classic
TOCTOU and costs an extra query and an extra materialised object; (3) **service methods**
called from elsewhere — batch jobs, event consumers, admin paths — may bypass the controller
entirely, so the check is missing there. Putting the **ownership predicate in the repository**
(`findByIdAndOwnerId`) makes the secure path the **default** and the insecure path **impossible
to express**. Best of both: the scoped repository method **plus** the service contract, plus a
test asserting a cross-user request returns 404.

### CQ2. "Why do I need method-level security if I have URL-level rules?"
- **Naive answer:** "For finer rules."  ← the trap
- **The trap:** The interviewer wants the *where the logic lives* argument.
- **Expert answer:** URL rules answer "**which endpoints**"; method rules answer "**which
operations, on which data**". The crucial cases: (1) **the same endpoint with different
operations** (`PATCH /orders/{id}` that can change status vs address — same URL, different
rules → method level); (2) **data-dependent rules** (ownership, tenant, state) which a URL
matcher cannot express; (3) **non-controller code** — `@Scheduled` jobs, `@EventListener`
handlers, service methods called from multiple entry points — where method-level security is
the **only** enforcement point; (4) **defence in depth** — the URL matcher is the coarse gate,
the method guard is the fine one at the data boundary. The pairing: URL for "which endpoints
need auth", method for "what exactly is allowed".

### CQ3. "Do I need CSRF protection if I use a JWT in the Authorization header?"
- **Naive answer:** "No, CSRF is for cookies."  ← mostly right, but incomplete
- **The trap:** The interviewer wants the nuance.
- **Expert answer:** Correct for **pure** header-based auth: CSRF exploits the browser's
ambient credentials (cookies); a token in a header must be deliberately attached by JavaScript,
which a cross-site page cannot do without XSS. **But**: (1) many apps end up in a **mixed**
mode — the JWT in an httpOnly cookie, which is the most XSS-resistant option → CSRF is needed
again; (2) **same-origin XSS** is the bigger threat, and CSRF protection doesn't help there;
(3) **sibling subdomain cookies** — a compromised sibling subdomain reintroduces CSRF;
(4) mobile and CLI clients don't care either way. The rule: **CSRF protection follows the
credential's transport** — cookie credentials → CSRF; header credentials → no CSRF needed.
Keep `SameSite` on cookies anyway, as a second layer.

### CQ4. "Should I use `@Transactional` for the security audit log?"
- **Naive answer:** "Yes, so the log is written with the transaction."  ← the trap
- **The trap:** The interviewer wants you to reason about audit durability and coupling.
- **Expert answer:** Usually **no** for **authentication events** — the audit must be written
**even if the business transaction rolls back** (the failed login is exactly the event you
need, and a successful login isn't inside a business transaction at all). If the audit shares
the transaction, a rollback erases the trail. The patterns: (1) a **separate transaction**
(`@Transactional(propagation = REQUIRES_NEW)`); (2) **async** (queue → writer), trading
durability; (3) a **plain auto-commit write** for auth events. For **business events**
("the order was created") the opposite often applies: you *want* atomicity with the business
transaction, so the audit exists only if the business action happened. The nuance:
**security events and business events have different transactional semantics**, and the
interviewer is testing whether you know which is which.

### CQ5. "Why is `permitAll()` on a static path safe?"
- **Naive answer:** "It's just the CSS."  ← the trap
- **The trap:** The interviewer wants the "safe vs harmless" distinction.
- **Expert answer:** It's safe *only if* that path serves static assets and nothing else. The
risks: (1) **path matching is loose** — broad patterns are greedy and ordering matters;
(2) **uploads served from the same tree** — a stored-XSS vector, since an uploaded HTML/JS file
served from the app origin can steal sessions; this is a classic real breach; (3) **directory
listing**; (4) **path collisions** with API routes. The rule: static assets come from a
**different origin** (a CDN), or at minimum with correct content types, CSP, and `nosniff`;
**uploads are never served from the app origin** — serve them through a controller that checks
authorization, or via a signed URL.

---

## 8. Tricky Interview Questions

**T1. "What's the difference between `Authentication` and `Authorization` in Spring's code,
concretely?"**
> `Authentication` = **principal + credentials + authorities** (the token object, the result of
authentication). `Authorization` = **the decision** (an `AuthorizationDecision` with
`isGranted()`). `SecurityContext` holds the `Authentication`; the `AuthorizationManager`
produces decisions.

**T2. "Why does Spring Security's `AuthenticationManager` have a `ProviderManager` inside?"**
> Because `AuthenticationManager` is the **facade** (single entry point for authentication) and
`ProviderManager` is the **strategy** (an ordered list of `AuthenticationProvider`s, each
declaring `supports()` for a token type). The separation means you can plug in a new provider
(OTP, SAML, LDAP, custom) without changing callers. `ProviderManager` implements "the first
provider that supports the token and doesn't return null wins", with `eraseCredentials` after
success and a `parentAuthenticationManager` fallback (often the one from
`AuthenticationConfiguration`).

**T3. "What happens if your `AuthenticationProvider` returns `null`?"**
> `ProviderManager` moves to the **next provider**; if none handles it and there is no parent,
it throws `ProviderNotFoundException` → 401. Returning `null` is the "I don't handle this token
type" signal, which is different from throwing.

**T4. "Why is `SecurityContextHolder`'s default `ThreadLocal` strategy a problem for async?"**
> `ThreadLocal` binds the context to **the thread**, so a new thread (`@Async`,
`CompletableFuture`, `@Scheduled`, message consumer) doesn't inherit it. Consequences: the
context is **empty** (`IllegalStateException` when reading the authentication) or, worse,
**leaked** — a pooled thread keeps the previous request's context, so the next request on that
thread sees the previous user's identity (**cross-request data leak**). Fixes: a
`TaskDecorator` to propagate the context, explicit passing, or `MODE_INHERITABLETHREADLOCAL`
(inheritable, but with its own pooling pitfalls). The **leak** is the scary one, and it's why
`SecurityContextHolder` must be cleared in a `finally`.

**T5. "What's the difference between 403 and 401?"**
> **401 Unauthorized** = "I don't know who you are" (authentication failed: token missing,
expired, invalid) → the client should **re-authenticate** or refresh. **403 Forbidden** = "I
know who you are, but you're not allowed" → the client should **not** retry; the same
credentials will fail again. Common bugs: returning 403 when the token expired (the client
loops instead of refreshing), and returning 401 when the user is authenticated but lacks the
role (the client tries to log in again, creating a refresh loop). `AuthenticationEntryPoint`
handles 401; `AccessDeniedHandler` handles 403.

**T6. "How do you test that authorization actually works, beyond the unit test?"**
> (1) An **integration test** (`@SpringBootTest` + `MockMvc` or `WebTestClient`) with the
**real security chain** (not a mock) and `@WithMockUser` for roles; (2) a **matrix test**
(roles × endpoints → expected status), table-driven and compact; (3) **Testcontainers** tests
against a real database (RLS, native queries); (4) **negative tests** (another user's resource
→ 404, expired token → 401, missing CSRF → 403); (5) a **guard test** that fails if the chain
configuration is loosened (e.g. actuator becomes `permitAll`).

**T7. "What is the risk of the BCrypt cost being too high?"**
> **Login latency** and CPU saturation under load — a login storm can exhaust the pool; also a
higher **offline attack cost** for the defender, **migration cost** (re-encoding on next
login), and **CI slowness** for hashing tests. Balance: measure on production hardware, target
~100–250 ms, and add **login rate limiting** so attack cost stays bounded. **Argon2id** with
tuned memory is current best practice (memory-hardness defeats GPU/ASIC attacks); BCrypt with
a sensible cost is a fine default.

**T8. "How do you safely allow file uploads from users?"**
> Size limit; type allow-list (extension + magic bytes — never the client-controlled
`Content-Type`); **re-encoding** images to strip payloads; **malware scanning**; **storage
isolation** (never the app origin — signed URLs or a controller with an authorization check);
**generated names** (UUID) to defeat path traversal; and no-execution headers
(`Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`).

---

## 9. Follow-Up Questions Asked by Interviewers

**F1** (after the checklist) → *"Which of these would you drop for a 5-person startup?"*
→ MFA (demand-driven), automated key rotation (a documented manual procedure is enough), SIEM
(logs + metric alerts suffice), WORM audit (database triggers suffice), service mesh (no).
The non-negotiables stay: password hashing, default-deny plus ownership checks, externalised
secrets, dependency updates, login rate limiting, tested backups.

**F2** (after IDOR) → *"How do you enforce ownership across the whole service?"*
→ Scoped repository methods, Spring Data specifications for dynamic cases, `@TenantId`/`@Where`
for the automatic ones, and an **ArchUnit-style test** that fails if a controller method
returns an entity without a scoped lookup. The architectural rule: "an entity never leaves the
service boundary without its owner predicate".

**F3** (after rate limiting) → *"What if Redis is down?"*
→ The fail-open vs fail-closed decision. For **login limiting**, either fail-**closed** (login
blocked — an availability cost) or fail-open with a local fallback (an in-memory limiter —
weaker but available). The mature approach: a **local in-memory limiter as the primary** (fast,
no dependency) plus a **distributed counter as the secondary** (accuracy across instances), so
a Redis outage degrades accuracy, not protection.

**F4** (after secrets) → *"How do you rotate a secret that 20 services use?"*
→ The dual-key pattern with config reload (Spring Cloud refresh, `@RefreshScope`): consumers
accept both, producers switch, the old key is revoked after the TTL. Alternative: vault dynamic
secrets, where database credentials are generated and leased, making rotation transparent.

---

## 10. Real 3–5 Year Experience Scenarios

**R1. "We have a security audit next week. What do you do in the first 48 hours?"**
>
> **Ideal answer:** (1) **Preparation:** (a) an **inventory** — endpoints (OpenAPI spec),
identities (users, service accounts, API clients), data (PII classification and flows), infra
(public exposure); (b) **automated checks** — SCA, SAST, secret scanning, container scanning,
DAST (OWASP ZAP against staging), and a **config review** (`application.yml` in production,
Kubernetes manifests, Terraform); (c) **high-risk quick wins** — an audit of `permitAll`, the
actuator exposure, `show-sql`, `ddl-auto: update`, hardcoded secrets, CORS `*`, disabled CSRF,
debug logs, default passwords. (2) **Triage:** findings → severity × exploitability → fix
effort → ordering. The **critical 48-hour fixes** are the cheap, high-impact ones: exposed
actuator, permissive CORS, missing CSRF, hardcoded secrets, `permitAll` on admin routes, debug
logs containing credentials, missing login rate limiting, `show-sql` in production.
(3) **Evidence preparation:** architecture diagram (trust boundaries, data flows, identity
flows), data-flow diagram (PII paths, stores, transfers, retention map), threat model (top 5
threats and mitigations), access-control matrix (roles × resources), logging evidence (audit
sample, alerting setup), incident response plan (contacts, playbooks), and policies (security,
access, password, data). (4) **Honesty:** gaps that can't be fixed in 48 hours get a
**documented plan with dates and owners** — a good auditor respects a well-reasoned,
prioritised plan far more than a rushed, incomplete fix. (5) **The lesson:** audits find
**process gaps** more often than technical ones — "the secrets are in the vault but there is no
rotation procedure", "logging exists but nobody reviews it", "CI runs SCA but it doesn't block
the merge". (6) The senior framing: "**an audit is a forcing function, not a threat** — the 48
hours should produce a prioritised, evidenced plan, not panic. The biggest win in the first week
is usually not a code fix but *making security signals visible*: login failures, denials,
privilege changes, the audit trail. An auditor can verify a control that produces evidence; a
control that 'exists' but leaves no trace is, correctly, treated as absent."

**R2. "A junior dev added an endpoint that leaks other users' data. How do you prevent this
from recurring?"**
>
> **Ideal answer:** (1) **Immediate** (fix + impact assessment): review the endpoint, fix it
with an owner predicate, then assess exposure — was data exposed? Consult audit and access
logs: who called it, how much data, over what period? That determines notification obligations
if PII leaked. (2) **Systemic — tooling over vigilance**, because humans will eventually fail:
(a) a **code review checklist** where the ownership predicate is a mandatory item, with a
security reviewer for sensitive endpoints; (b) **automated tests** — an **ArchUnit-style rule**
that fails the build if a controller returns an entity fetched with an unscoped repository
method, or a convention that every user-owned entity query is scoped (methods without
`AndUserId` become a suspect list); (c) **static analysis** (Semgrep rules for JPA `findById`
in controllers, DTO requirements); (d) an **architectural decision** — a service-layer boundary
where controllers receive DTOs only, so the entity never crosses it and the whole IDOR class
is structurally prevented; (e) **security testing** — an automated IDOR sweep that attempts
horizontal and vertical escalation across the API, run in CI or nightly. (3) **Culture:** a
**blameless postmortem** (the system failed — the checklist lacked the item, the tooling didn't
catch it), team education (IDOR, OWASP API Top 10, pairing), and a named "authz review"
responsibility. (4) The senior framing: "**a human review will eventually miss an IDOR — the
fix is structural**: make the entity never cross the controller boundary (DTOs only), make the
scoped repository method the only one that exists for owned entities, and add a build-failing
rule for unscoped access. Then the review checklist is a backstop, not the primary control.
And the response matters: assess exposure honestly (audit logs tell you who called it), and
keep the postmortem blameless — the system allowed it, so the system must change."

**R3. "Our JWT secret is in the Git history. Rotation will invalidate tokens — what's the
plan?"**
>
> **Ideal answer:** (1) **Immediate:** (a) **Assess** — exposure window (when was it committed,
is the repo public, is it forked?), and assume compromise. (b) **Rotate** — but note that
rotating the signing secret invalidates every issued token, so users are logged out (usually an
acceptable cost for a security rotation). Mechanics: generate the new secret, deploy it, old
tokens fail verification → 401 → the client refreshes... **but refresh tokens are signed with
the same secret**, so they fail too and users must re-login. For a **graceful rotation**, use a
**dual-key window**: the decoder accepts both new and old secrets for a bounded period (e.g. 15
minutes), the issuer signs with the new key, and old access tokens expire naturally. Refresh
tokens should be **explicitly invalidated** — delete their rows, forcing re-login — or keep a
short dual-key window for refresh too, accepting the risk. (2) **Remediation:** (a) **history
rewrite** (`git filter-repo` / BFG) is necessary but insufficient — forks, clones, CI caches,
images, cached GitHub views all persist, so **rotation is the real fix** and the rewrite is
hygiene; (b) **prevention** — a vault, secret scanning (pre-commit hook, CI check, GitHub push
protection), `.gitignore` discipline, credential hygiene (no personal tokens, short-lived and
scoped deploy tokens). (3) **Communication:** internal notice (rotate local copies), and a
customer-facing security advisory if it's a customer-facing secret. (4) **Audit trail:** an
incident record (timeline, exposure assessment, actions, lessons). (5) The senior framing:
"**treat it as compromised from the moment it entered history** — the rewrite is hygiene, not
the fix, because you cannot enumerate the copies (forks, CI caches, laptops, images). The
rotation is the fix; the only question is whether it forces re-login. My default: rotate the
signing key immediately (access tokens are short-lived, so the 15-minute annoyance is the right
trade) and delete refresh tokens. If the blast radius demands grace, use a dual-key window that
is tightly bounded, revoked the moment it closes. Then the systemic fix: a vault, pre-commit
scanning, and a CI block — because the same commit will happen again otherwise."

---

## 11. Frequently Asked Production Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| 401 on the SPA pre-flight | CORS filter ordering | `http.cors()` before the auth filter |
| 403 for admins | `ROLE_` prefix mismatch | Consistent prefixing |
| Random logouts in a cluster | Local sessions | Spring Session with Redis |
| 429 for a whole office | Per-IP key behind NAT | Per-user key + trusted proxy |
| Slow login | Hash cost / Argon2 memory | Calibrate the cost |
| `LazyInitializationException` | `open-in-view=true` + access outside the transaction | `open-in-view=false` + fetch join |
| `InvalidCsrfTokenException` in the SPA | Deferred token (6.x) | `SpaCsrfTokenRequestHandler` |
| 404 for another user's resource | Ownership predicate (correct!) | Expected — confirm it's intentional |
| `AccessDeniedException` in async code | Lost security context | `TaskDecorator` |
| `InvalidSignatureException` after deploy | Secret rotation with a cached key | `kid` + JWKS cache TTL |

---

## 12. Debugging Scenarios

**D1. "Is the security filter chain even running?"**
```properties
logging.level.org.springframework.security=DEBUG
```

**D2. "What are the effective authorities?"**
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
log.info("principal={} authorities={}", auth.getPrincipal(), auth.getAuthorities());
```

**D3. "Which matcher rejected the request?"**
```java
@Bean
AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
    return new SpringAuthorizationEventPublisher(publisher);
}
```
→ the `AuthorizationEvent` in the log names the object and the decision.

**D4. "Is the CSRF token being set?"**
```bash
curl -sI -c cookies.txt https://api.example.com/api/orders | grep -i set-cookie
```

**D5. "Are the security headers present?"**
```bash
curl -sI https://api.example.com/ | grep -iE "content-security|strict-transport|x-frame|x-content-type|referrer"
```

---

## 13. Architecture Design Questions

**Q1. 🏗️ Design the security architecture for a fintech (transfers, accounts, compliance).**
> (1) **Identity** (SSO, mandatory MFA, step-up authentication for transfers, device
fingerprinting, session risk scoring); (2) **Authorization** (separation of duties — initiator
≠ approver for large transfers, transaction limits, dual control); (3) **Data protection**
(encryption at rest, field-level encryption for the PAN, tokenisation, masking in logs,
pseudonymisation in analytics); (4) **Audit** (immutable and complete — every balance read,
every instruction, correlated, replicated off-box); (5) **Fraud controls** (velocity checks,
device reputation, impossible travel, a manual review queue); (6) **Resilience** (availability
requirements, degraded modes, a manual fallback for transfers).

**Q2. 🏗️ Design security monitoring for a platform.**
> (The signals, the detection rules, the pipeline, the response, and the governance — see
chapter 06, architecture design question.)

**QQ3. 🏗️ Design multi-tenant isolation for a B2B SaaS.**
> (Tenant resolution → claim → query scoping → RLS → cache → jobs → tests → monitoring — see
chapter 06, the multi-tenant section.)

---

## 14. Rapid-Fire MCQs

**1.** The default `PasswordEncoder` in Spring Security 6:
A) MD5  B) BCrypt  C) SHA-256  D) No encoding
**✅ B**

**2.** `SecurityContextHolder`'s default strategy:
A) Global  B) ThreadLocal  C) Inheritable  D) Session
**✅ B**

**3.** 403 means:
A) Not authenticated  B) Authenticated but not allowed  C) Server error  D) Not found
**✅ B**

**4.** `@PreAuthorize` vs `@PostAuthorize`:
A) Identical  B) Pre runs before execution (writes), post runs after (reads)  C) Post is faster  D) Pre is for read-only
**✅ B**

**5.** `open-in-view=false`:
A) Disables views  B) Closes the persistence context after the transaction (surfacing lazy errors)  C) Disables JPA  D) Enables second-level cache
**✅ B**

**6.** The primary layer of IDOR prevention:
A) Frontend  B) Ownership predicate in the query  C) Gateway  D) Obfuscated ids
**✅ B**

**7.** Refresh token storage:
A) Plain  B) Hashed  C) Encrypted (reversible)  D) As a JWT
**✅ B**

**8.** CSP's `frame-ancestors` protects against:
A) XSS  B) Clickjacking  C) MIME sniffing  D) Referrer leakage
**✅ B**

**9.** The drawback of `SameSite=Strict`:
A) CSRF  B) The OAuth2 redirect flow and cross-site navigation  C) CORS  D) XSS
**✅ B**

**10.** `AuthenticationEntryPoint` vs `AccessDeniedHandler`:
A) 401 vs 403  B) Login vs logout  C) JWT vs session  D) Filter vs interceptor
**✅ A**

**11.** Audit events and the business transaction:
A) Always the same  B) Security events are often written outside the business transaction  C) Never  D) Audit events are always in a transaction
**✅ B**

**12.** BCrypt salting:
A) It isn't salted  B) It is salted automatically, per password  C) It uses a global salt  D) The salt is the username
**✅ B**

**13.** `X-Content-Type-Options: nosniff` protects against:
A) XSS  B) MIME sniffing  C) CSRF  D) Caching
**✅ B**

**14.** The `spring.security.acl` module:
A) It's the default  B) It's a legacy/optional module — most apps use method security  C) It replaces JWT  D) It's required for REST
**✅ B**

**15.** `hasRole('ADMIN')` vs `hasAuthority('ROLE_ADMIN')`:
A) They're identical  B) `hasRole` prefixes `ROLE_` automatically  C) `hasAuthority` is deprecated  D) Both are invalid
**✅ B**

---

## 15. Cheat Sheet — One-Page Summary

```text
┌───────────────────────────────────────────────────────────────────────────────┐
│  THE 10 NON-NEGOTIABLES                                                       │
│  ① BCrypt/Argon2 via the PasswordEncoder    ② default-deny + IDOR handling   │
│  ③ secrets out of the code                  ④ CSRF configured per chain      │
│  ⑤ CORS with explicit origins               ⑥ validation + output encoding   │
│  ⑦ rate limiting on login                   ⑧ auditing of security events    │
│  ⑨ security headers                         ⑩ dependency updates             │
├───────────────────────────────────────────────────────────────────────────────┤
│  CONFIG SNIPPETS                                                              │
│  CSRF: off ONLY for header-based bearer chains                                │
│  CORS: exact origins + allowCredentials(true) + never "*"                    │
│  Sessions: STATELESS for the API, IF_REQUIRED for the web                     │
│  Chains: @Order(1) API, @Order(2) web — the FIRST match wins                  │
│  JWT: iss/aud/exp/sub/jti/scope + kid + an algorithm allow-list               │
│  Headers: CSP + HSTS + frame options + referrer policy                        │
│  Actuator: separate port + restricted exposure + no env/beans                 │
├───────────────────────────────────────────────────────────────────────────────┤
│  ⚠ IDOR is the #1 real breach — put the owner predicate in the QUERY          │
│  ⚠ Refresh tokens are stored HASHED; access tokens are SHORT-LIVED           │
│  ⚠ MFA is for humans; machines get scoped credentials                         │
│  ⚠ Tenant id is DERIVED, never supplied by the client                         │
│  ⚠ Security config is the SAME code everywhere (only values differ)          │
│  ⚠ Tests must prove cross-user/tenant/role access is REJECTED                 │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## 16. Follow-up Learning

- → [06 · JWT Authentication](../06-JWT-Authentication/README.md) — token design.
- → [07 · OAuth2 & OIDC](../07-OAuth2-OpenID-Connect/README.md) — identity providers.
- → [13 · Testing](../13-Testing/README.md) — security testing in depth.
- → [14 · Deployment & DevOps](../14-Deployment-DevOps/README.md) — secrets, CI/CD,
  monitoring.

---
**Previous Chapter:** ← [06 · Production Security Patterns](./06-Production-Security-Patterns.md)
**Next Topic:** → [06 · JWT Authentication](../06-JWT-Authentication/README.md)

