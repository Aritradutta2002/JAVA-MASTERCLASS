# 02 · Stateless Authentication & the Complete Flow

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★☆ ("Walk me through your authentication flow")
> **Prerequisite:** [01 · JWT Architecture](./01-JWT-Architecture-and-Anatomy.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Token's Lifecycle](#2-mental-model--the-tokens-lifecycle)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The stateless vs the stateful](#31-the-stateless-vs-the-stateful)
  - [3.2 The three architectures](#32-the-three-architectures)
  - [3.3 The complete authentication flow](#33-the-complete-authentication-flow)
  - [3.4 The request's flow (the hot path)](#34-the-requests-flow-the-hot-path)
  - [3.5 The refresh's flow](#35-the-refreshs-flow)
  - [3.6 The `SecurityContext` in the stateless mode](#36-the-securitycontext-in-the-stateless-mode)
  - [3.7 The `BearerTokenAuthenticationFilter`](#37-the-bearertokenauthenticationfilter)
  - [3.8 The custom `AuthenticationConverter`](#38-the-custom-authenticationconverter)
  - [3.9 The error's contract](#39-the-errors-contract)
  - [3.10 The `BearerTokenResolver` (the multi-token's sources)](#310-the-bearertokenresolver-the-multi-tokens-sources)
- [4. Visualizations](#4-visualizations)
- [5. Worked Examples](#5-worked-examples)
- [6. Code from Scratch — Production-Grade](#6-code-from-scratch--production-grade)
- [7. Common Pitfalls & Gotchas](#7-common-pitfalls--gotchas)
- [8. Interview Q&A — Level-wise](#8-interview-qa--level-wise)
- [9. Counter-Questions & Traps](#9-counter-questions--traps)
- [10. Rapid-Fire MCQs](#10-rapid-fire-mcqs)
- [11. Hands-On Exercises](#11-hands-on-exercises)
- [12. Code Challenges](#12-code-challenges)
- [13. Scenario-Based Questions](#13-scenario-based-questions)
- [14. Tricky Interview Questions](#14-tricky-interview-questions)
- [15. Follow-Up Questions Asked by Interviewers](#15-follow-up-questions-asked-by-interviewers)
- [16. Real 3–5 Year Scenarios](#16-real-35-year-scenarios)
- [17. Frequently Asked Production Issues](#17-frequently-asked-production-issues)
- [18. Debugging Scenarios](#18-debugging-scenarios)
- [19. Architecture Design Questions](#19-architecture-design-questions)
- [20. Cheat Sheet — One-Page Summary](#20-cheat-sheet--one-page-summary)
- [21. Follow-up Learning](#21-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

- **Stateless authentication** = the server verifies the token's signature with no session
  store. The `SecurityContext` is created **per request** and thrown away.
- **The three architectures:**
  1. **The fully stateless** — the JWT is verified locally; nothing is stored. The
     "revocation" is the TTL's bound.
  2. **The hybrid (the standard)** — the stateless access token + the stateful refresh token
     (the opaque, the hashed, the rotating, the revocable). **This is what you should build.**
  3. **The stateful with the opaque token** — the server looks the token up (the instant
     revocation, the attributes are changeable); the "JWT-like" but not a JWT.
- **The full flow:** `login → verify credentials → issue the access + the refresh → the
  client stores them → the client sends the access token → the server verifies (the
  signature, the `exp`, the `iss`, the `aud`) → the `Authentication` is built → the
  authorization → the response`.
- **The refresh's flow:** `the access token expires → the client calls /refresh with the
  refresh token → the server validates + rotates it → the new pair → the old is invalidated`.
  **The rotation's reuse's detection** is the security's core: if an already-used refresh
  token comes back, the whole family is revoked (the theft's signal).
- **In Spring:** the `spring-boot-starter-oauth2-resource-server` gives you the
  `BearerTokenAuthenticationFilter` → the `JwtAuthenticationProvider` → the
  `JwtAuthenticationConverter` → the `SecurityContext`.
- **The one-liner:** *"The statelessness is in the request path — the token is verified
locally, no session store, any instance can serve any request. The state lives in the refresh
token, so the session can still be revoked. That's the hybrid, and it's what production
actually uses."*

---

## 1. What & Why

### The stateful model (the session)

```text
   login ──► the server creates a session (Redis/DB)  ◄── the state lives here
              the client gets "sessionId=abc" (the cookie)
   request ──► the server LOOKS UP the session  ◄── the DB/Redis hit on EVERY request
```

**The problems:** the shared store (the availability, the latency, the cost), the sticky
sessions (or the store), and the "the state doesn't scale horizontally without a dependency".

### The stateless model (the JWT)

```text
   login ──► the server signs a token (the private key)  ◄── no state
              the client gets "eyJhbGci..." 
   request ──► the server VERIFIES the signature (the public key, cached)  ◄── no lookup
```

**The gain:** any instance can serve any request → the horizontal scaling is trivial, the
server's state is minimal, and the verification is CPU-only.

**The remaining need:** the long-lived identity (the "the user stays logged in for 30 days")
requires something stateful. **That's the refresh token's job.**

---

## 2. Mental Model — The Token's Lifecycle

```text
  ┌─────────────┐   login (credentials)   ┌──────────────┐
  │   CLIENT    │ ──────────────────────► │  AUTH SERVER  │
  │             │ ◄────────────────────── │              │
  └─────────────┘   access (5-15 min)      └──────────────┘
        │            refresh (7-30 days)         │
        │                                         ▼
        │  Authorization: Bearer <access>    ┌──────────────┐
        │ ─────────────────────────────────► │ RESOURCE     │
        │                                    │ SERVER       │
        │  (verify: sig + exp + iss + aud)  │ (no session) │
        │                                    └──────────────┘
        │
        │  (access expired)  POST /oauth2/token {refresh_token}
        │ ─────────────────────────────────►  → rotate → new access + new refresh
        │ ◄─────────────────────────────────
```

**The invariant:** the **access token** is short and self-contained; the **refresh token** is
long, opaque, and stored. The exchange happens only when the access token dies.

---

## 3. Deep Dive — Theory

### 3.1 The stateless vs the stateful

| Aspect | Stateful (the session) | Stateless (the JWT) |
|--------|------------------------|----------------------|
| The server's storage | The session's store (Redis/DB) | None (for the access token) |
| The per-request's cost | A store's lookup | The CPU's signature's verification |
| The horizontal scaling | The shared store (or the sticky sessions) | Any instance can serve |
| The revocation | **Instant** | The TTL's bound (or the blocklist) |
| The data's freshness | Immediate | Until the next issuance |
| The client's storage | A small cookie (the id) | The token (the bigger) |
| The attack's surface | The session's fixation, the hijack | The token's theft, the replay |
| The audit's trail | The store's state | The claims (self-describing) |

### 3.2 The three architectures

#### (a) The fully stateless

```text
   login → the JWT (the access, 15 min)
   request → verify locally
   refresh → a new JWT issued by... whom?
```
**Problem:** with no refresh token, the user re-authenticates every 15 minutes. **Only
acceptable** for: the machine-to-machine (the client credentials, the tokens issued by the
platform), the short-lived's impersonation, and the "the API's key" model (the AWS's SigV4 is
a cousin of this).

#### (b) The hybrid — **the production's standard**

```text
   the ACCESS token:  the JWT, the stateless, the short (5-15 min)
   the REFRESH token: the opaque, the random, the HASHED in the DB, the rotating, the revocable
```

| Property | The access | The refresh |
|----------|-----------|-------------|
| The format | The JWT (the signed's claims) | The opaque's random string (or a JWT with a server-side's state) |
| The lifetime | 5-15 min | 7-30 days |
| The storage (the client) | The memory (the SPA) / the memory + the secure's storage (the mobile) | The httpOnly's cookie (the SPA) / the Keychain (the mobile) |
| The storage (the server) | None | The hashed row (the DB) + the rotation's family |
| The revocation | The TTL's bound | **Instant** (the row's deletion) |
| The reuse's detection | N/A | ✅ (the rotation) |

**The hybrid's core insight:** move the *statefulness* to the operation that happens **rarely**
(the refresh — once per 10-15 minutes) instead of the operation that happens on **every
request** (the verification). The hot path stays stateless; the cold path carries the state.

#### (c) The opaque tokens (the server-side's session, the Redis)

```text
   the access token: "a7f3d9e2b1c8..." (random) → the Redis's lookup
   the refresh token: "b2c4..." → the Redis's lookup
```
**The pros:** the instant's revocation, the attributes are changeable, the data's privacy.
**The cons:** the store's dependency, the lookup's latency, the store's cost.
**Where it's right:** the high-security (the admin's console), the sessions that need the
server's control (the "the seat's count", the "the device's listing"), and the apps where
the revocation's immediacy outweighs the statelessness.

### 3.3 The complete authentication flow

```text
  POST /api/auth/login  { "email": "...", "password": "..." }
      │
      ▼
  ① the controller's @Valid → the DTO's validation (the format)
      │
      ▼
  ② the AuthenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password))
      │
      ├─ the DaoAuthenticationProvider
      │     ├─ the UserDetailsService.loadUserByUsername(email)   → the DB
      │     └─ the PasswordEncoder.matches(raw, encoded)         → the BCrypt
      │
      └─ the result: an authenticated Authentication (the principal, the authorities)
      │
      ▼
  ③ the JwtService.issue(principal) → the access token + the refresh token
      │
      ├─ the access token: the JWT, signed, the claims (the sub, the exp, the scope, the jti)
      └─ the refresh token: the opaque, the random; the HASHED row is saved
           (the userId, the hash, the familyId, the expiresAt, the revoked=false)
      │
      ▼
  ④ the response
      { "accessToken": "eyJ...",
        "refreshToken": "a7f3d9e2...",
        "tokenType": "Bearer",
        "expiresIn": 900 }
```

**The design's decision: where does the refresh token go?**
- **The SPA → the httpOnly's cookie** (the `Path=/api/auth`, the `Secure`, the `SameSite=Strict`)
  → the JS can't read it → the XSS's theft is limited to the requests the XSS can make (the
  CSRF's protection then applies to the refresh's endpoint).
- **The mobile → the secure's storage** (the Keychain/Keystore, the encrypted's shared
  preferences).
- **The pure's API's clients → the client's memory** (the machine's store).

### 3.4 The request's flow (the hot path)

```text
  GET /api/orders   Authorization: Bearer eyJhbGci...
      │
      ▼
  ① the BearerTokenAuthenticationFilter
      ├─ the BearerTokenResolver extracts the token (the header)
      ├─ if no token → the chain continues (the SecurityContext is empty) → the 401 later
      └─ if a token:
           ├─ the JwtDecoder.decode(token)  → the Jwt object
           │     ├─ the signature's verification (the JWKS's cached public key)
           │     ├─ the exp/nbf/iss/aud's validation
           │     └─ the failure → the AuthenticationException → the entry point → the 401
           └─ the JwtAuthenticationConverter → the AbstractAuthenticationToken
                (the principal = the sub, the authorities = the scopes/roles)
      │
      ▼
  ② the SecurityContextHolder.setContext(authentication)
      │
      ▼
  ③ the AuthorizationFilter → the AuthorizationManager → the decision
      │
      ▼
  ④ the controller → the @AuthenticationPrincipal Jwt jwt
```

**The key's insight:** the `SecurityContext` is created **per request** by the filter and
discarded at the end of the request. **Nothing persists.** That's the statelessness.

### 3.5 The refresh's flow

```text
  POST /api/auth/refresh   (the refresh token in the body or the cookie)
      │
      ▼
  ① the hash the presented token → look up the row (SELECT ... WHERE token_hash = ?)
      │
      ├─ not found → 401 (the invalid's token)
      ├─ found but revoked → ⚠ THE REUSE'S DETECTION
      │      └─ revoke the ENTIRE family (all the descendants) → 401
      ├─ found but expired → 401
      └─ found and valid:
           ├─ the atomic's update:  UPDATE refresh_tokens
           │      SET token_hash = <new>, expires_at = ..., revoked = true, version = version + 1
           │      WHERE id = ? AND version = <expected> AND revoked = false
           │      → if 0 rows updated → someone else used it concurrently → the reuse's detection
           ├─ insert the new row with the SAME familyId
           └─ issue a new access token
      │
      ▼
  200 { accessToken, refreshToken, expiresIn }
```

**The atomic's update is essential.** Without the version check, two concurrent refreshes both
succeed and both are "valid" — and neither is detected as a reuse.

### 3.6 The `SecurityContext` in the stateless mode

```java
// in the stateless mode, the context is per-request
SecurityContext context = SecurityContextHolder.createEmptyContext();
context.setAuthentication(authentication);
SecurityContextHolder.setContext(context);
```

**The `SecurityContextHolder`'s strategies:**

| Strategy | Storage | Use |
|----------|---------|-----|
| `ThreadLocalSecurityContextHolderStrategy` (the default) | The thread's local | The web (per-request) |
| `InheritableThreadContextHolderStrategy` | The inheritable's thread's local | The async (with caveats) |
| `MODE_INHERITABLETHREADLOCAL` | Both | The legacy |
| `GlobalSecurityContextHolderStrategy` | A static (single) | The standalone's clients (the CLI) |

**In the stateless mode, the `RequestAttributeSecurityContextRepository` is the efficient
choice** — it stores the context as a request's attribute (no map's lookup, no leak's risk
between the requests on a pooled thread):
```java
http.securityContext(context -> context
    .securityContextRepository(new RequestAttributeSecurityContextRepository())
    .requireExplicitSave(false));   // the default in Boot 3: no explicit save needed
```

> ⚠ **The `requireExplicitSave(true)`** is the Spring Security 6 default for the *new* projects
> — it means the filter does NOT auto-save the context, and the code must call
> `SecurityContextHolder.saveContext(...)`. For a stateless resource server, set it to `false`
> (or don't use the form login) or you'll get the "the context is empty" confusion.

### 3.7 The `BearerTokenAuthenticationFilter`

Spring's `oauth2ResourceServer().jwt()` installs:
```text
BearerTokenAuthenticationFilter
   └─ the BearerTokenResolver (extracts the token)
   └─ the AuthenticationManager (the JwtAuthenticationProvider)
        └─ the JwtDecoder (NimbusJwtDecoder) → the Jwt
        └─ the JwtAuthenticationConverter → the JwtAuthenticationToken
```

**The `BearerTokenResolver`'s default:** the `Authorization: Bearer <token>` header. It also
supports the form's parameter and the query's parameter (the `setAllowUriQueryParameter`) — the
latter is dangerous (the tokens in the logs, the browser's history).

### 3.8 The custom `AuthenticationConverter`

```java
@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        // the scopes
        String scope = jwt.getClaimAsString("scope");
        if (scope != null) {
            Arrays.stream(scope.split(" "))
                  .filter(StringUtils::hasText)
                  .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                  .forEach(authorities::add);
        }
        // the roles
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                 .forEach(authorities::add);
        }
        return authorities;
    }
}
```

### 3.9 The error's contract

A well-designed API returns a **consistent, machine-readable** error body:

```json
{
  "code": "TOKEN_EXPIRED",
  "message": "The access token has expired",
  "timestamp": "2026-01-20T10:15:30Z",
  "path": "/api/orders",
  "traceId": "8f1c..."
}
```

**The codes the client must distinguish:**

| The code | The meaning | The client's action |
|----------|-------------|---------------------|
| `TOKEN_MISSING` | No header | Send the token |
| `TOKEN_EXPIRED` | The `exp` passed | **Refresh** (and retry once) |
| `TOKEN_INVALID` | The signature/format's failure | Re-authenticate (the token is broken) |
| `TOKEN_REVOKED` | The blocklist / the version | Re-authenticate |
| `REFRESH_INVALID` | The refresh's rejection | Re-authenticate (the login) |
| `FORBIDDEN` | Authenticated but not allowed | **Never** retry |

**The distinction between `TOKEN_EXPIRED` and `TOKEN_INVALID` is critical** — the client
refreshes on the first and re-authenticates on the second. Collapsing them into a generic 401
is a classic production bug (the client loops on the refresh, or logs the user out
unnecessarily).

### 3.10 The `BearerTokenResolver` (the multi-token's sources)

```java
@Bean
BearerTokenResolver bearerTokenResolver() {
    DefaultBearerTokenResolver resolver = new DefaultBearerTokenResolver();
    resolver.setAllowFormParameter(false);        // never in a form body
    resolver.setAllowUriQueryParameter(false);    // never in the URL
    return resolver;
}
```

**Why the query's parameter is dangerous:** the token ends up in the access's logs, the
proxy's logs, the browser's history, and the `Referer` header. Some legacy clients require it
(there's an RFC 6750's allowance for `access_token` in the query) — if you must, limit it and
document it.

---

## 4. Visualizations

### 4.1 The token's lifecycle

```text
   ┌───────────────────────────────────────────────────────────────┐
   │  the ACCESS token (the JWT)                                    │
   │  · self-contained, the signed's claims                        │
   │  · short (5-15 min)                                           │
   │  · sent on EVERY request (the Authorization header)           │
   │  · verified locally (no store)                                │
   └───────────────────────────────────────────────────────────────┘
        │ expired
        ▼
   ┌───────────────────────────────────────────────────────────────┐
   │  the REFRESH token (the opaque)                               │
   │  · random, the server-side's state (hashed row)               │
   │  · long (7-30 days)                                           │
   │  · sent ONLY to the refresh's endpoint                        │
   │  · rotates on every use → the reuse's detection                │
   └───────────────────────────────────────────────────────────────┘
```

### 4.2 The complete flow (the sequence)

```mermaid
sequenceDiagram
    autonumber
    participant C as the Client
    participant A as the Auth service
    participant DB as the DB
    participant R as the Resource server

    C->>A: POST /api/auth/login {email, password}
    A->>A: validate the DTO
    A->>A: AuthenticationManager.authenticate(...)
    A->>DB: loadUserByUsername(email)
    DB-->>A: the user + the BCrypt hash
    A->>A: passwordEncoder.matches()
    A->>A: JwtService.issue() → the access (JWT) + the refresh (opaque)
    A->>DB: INSERT refresh_tokens (hash, familyId, expiresAt)
    A-->>C: 200 {accessToken, refreshToken, expiresIn}

    C->>R: GET /api/orders  (Authorization: Bearer accessToken)
    R->>R: verify the signature (the cached JWKS) + exp/iss/aud
    R->>R: build the Authentication → the SecurityContext
    R-->>C: 200 OK

    Note over C,A: 15 minutes later — the access token expired
    C->>A: POST /api/auth/refresh {refreshToken}
    A->>DB: SELECT WHERE token_hash = hash(presented)
    DB-->>A: the row (familyId, version, revoked)
    A->>DB: UPDATE (atomic: WHERE version = expected AND revoked = false)
    A->>DB: INSERT the new row (the same family)
    A-->>C: 200 {a new accessToken, a new refreshToken}
```

### 4.3 The three architectures compared

```text
   (a) FULLY STATELESS            (b) HYBRID (recommended)         (c) OPAQUE
   ┌──────────────┐               ┌──────────────┐                ┌──────────────┐
   │  access JWT  │               │  access JWT  │                │  access      │
   │  (verify)    │               │  (verify)    │                │  "a7f3..."   │
   └──────────────┘               │  refresh     │                │  (lookup)    │
   no refresh                    │  (hashed DB) │                └──────────────┘
   → re-login every 15 min       └──────────────┘                the Redis
                                  ✅ the sweet spot
```

### 4.4 The refresh's rotation (the reuse's detection)

```text
   familyId = F1
   ┌────────┐  used  ┌────────┐  used  ┌────────┐
   │  R1    │───────►│  R2    │───────►│  R3    │
   └────────┘        └────────┘        └────────┘
   (revoked)         (revoked)         (active)

   ⚠ if R2 is presented AGAIN (after it was used):
      → the theft's signal → revoke R3 (the whole family) → force a re-login
```

---

## 5. Worked Examples

### 5.1 A dry run of the login

```text
POST /api/auth/login
{"email": "alice@example.com", "password": "Str0ng!Pass"}

  ① the DTO's validation: @Email ok, @NotBlank ok
  ② the AuthenticationManager:
       loadUserByUsername("alice@example.com") →
         UserDetails{username: "alice@example.com",
                     password: "$2a$12$XYZ...",   ← the BCrypt
                     authorities: [ROLE_USER, SCOPE_orders:read]}
       passwordEncoder.matches("Str0ng!Pass", "$2a$12$XYZ...") → true
  ③ the issue:
       the access token's claims:
         { "sub": "42", "iss": "https://auth.example.com", "aud": "orders-api",
           "iat": 1735689000, "exp": 1735689900, "jti": "b1c2...",
           "scope": "orders:read", "roles": ["USER"] }
       the access token: "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiI0MiJ9.SflKxwRJ..."
       the refresh token: "7f2a9c1e4b8d6f0a3c5e7b9d1f3a5c7e"   (the 32-byte random, base64url)
       the DB's row: { id: 91, userId: 42, tokenHash: "sha256(7f2a...)",
                       familyId: "F1", version: 1, revoked: false,
                       expiresAt: 2026-02-20T10:15:30Z, createdAt: ... }
  ④ the response: 200 { "accessToken": "...", "refreshToken": "7f2a...", "expiresIn": 900 }
```

### 5.2 A dry run of the request

```text
GET /api/orders   Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...

  the BearerTokenAuthenticationFilter:
    the resolver → the token
    the JwtDecoder.decode:
       the header's alg = RS256 (in the allowed list ✓)
       the header's kid = "k1" → the JWKS's cache → the public key
       the signature's verification → ✓
       the validators:
         the exp (1735689900) > now (1735689200) ✓  (the skew's tolerance: 60s)
         the iss == "https://auth.example.com" ✓
         the aud contains "orders-api" ✓
    the converter → the JwtAuthenticationToken
       the principal: "42" (the sub)
       the authorities: [SCOPE_orders:read, ROLE_USER]
    the SecurityContextHolder.setContext(...)
  the AuthorizationFilter: anyRequest().authenticated() → the context is authenticated ✓
  the controller: @AuthenticationPrincipal Jwt jwt → jwt.getSubject() == "42"
```

### 5.3 A dry run of the refresh (and the reuse's detection)

```text
POST /api/auth/refresh   {"refreshToken": "7f2a9c1e..."}

  ① the hash: sha256("7f2a9c1e...") = "9b1c..." → SELECT
       → the row { id: 91, familyId: "F1", version: 1, revoked: false, expiresAt: future }
  ② the atomic's update:
       UPDATE refresh_tokens
          SET token_hash = sha256(new), revoked = true, version = 2
        WHERE id = 91 AND version = 1 AND revoked = false
       → 1 row updated ✓
  ③ INSERT { id: 92, token_hash: sha256(new), familyId: "F1", version: 1, revoked: false }
  ④ the response: 200 { a new access token, the new refresh token }

  --- now the attacker replays the OLD token ---
POST /api/auth/refresh   {"refreshToken": "7f2a9c1e..."}     ← the same old one

  ① the hash → SELECT → the row { id: 91, revoked: TRUE }   ← it was already used!
  ② THE REUSE'S DETECTION → revoke the whole family:
       UPDATE refresh_tokens SET revoked = true WHERE family_id = 'F1'
     → now R3 (the legitimate user's current token) is dead too
  ③ the response: 401 { "code": "REFRESH_REUSE_DETECTED" }
     → the user is forced to re-login; the alert fires
```

---

## 6. Code from Scratch — Production-Grade

### 6.1 The `SecurityConfig` (the resource server)

```java
package com.example.config;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())          // the bearer token in the header
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(context -> context
                .securityContextRepository(new RequestAttributeSecurityContextRepository()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }
}
```

### 6.2 The `JwtService` (the issuer)

```java
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.private-key}")
    private Resource privateKeyResource;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    void init() throws Exception {
        try (InputStream in = privateKeyResource.getInputStream()) {
            this.privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(in.readAllBytes()));
        }
        try (InputStream in = publicKeyResource.getInputStream()) {
            this.publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(in.readAllBytes()));
        }
    }

    public String generateAccessToken(UserPrincipal user, String tokenId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .header(headers -> headers
                        .keyId(properties.getKeyId())
                        .algorithm(SignatureAlgorithm.RS256)
                        .type("JWT"))
                .subject(String.valueOf(user.getId()))
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .id(tokenId)
                .claim("scope", String.join(" ", user.getScopes()))
                .claim("roles", user.getRoles())
                .claim("tenantId", user.getTenantId())
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashRefreshToken(String token) {
        return DigestUtils.sha256Hex(token);
    }
}
```

### 6.3 The `AuthService` (the login & the refresh)

```java
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String tokenId = UUID.randomUUID().toString();
        String accessToken = jwtService.generateAccessToken(principal, tokenId);

        String rawRefresh = jwtService.generateRefreshToken();
        RefreshToken token = RefreshToken.builder()
                .userId(principal.getId())
                .tokenHash(jwtService.hashRefreshToken(rawRefresh))
                .familyId(UUID.randomUUID().toString())
                .expiresAt(clock.instant().plus(Duration.ofDays(30)))
                .userAgent(httpRequest.getHeader("User-Agent"))
                .ipAddress(extractIp(httpRequest))
                .build();
        refreshTokenRepository.save(token);

        return new AuthResponse(accessToken, rawRefresh, 900);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String hash = jwtService.hashRefreshToken(rawRefreshToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException());

        if (existing.isRevoked()) {
            // ⚠ THE REUSE'S DETECTION — the token was already used.
            // The whole family is compromised (the thief may be racing the legitimate user).
            refreshTokenRepository.revokeFamily(existing.getFamilyId());
            throw new RefreshTokenReuseDetectedException();
        }
        if (existing.getExpiresAt().isBefore(clock.instant())) {
            throw new InvalidRefreshTokenException("The refresh token has expired");
        }

        // the ATOMIC's rotation
        String newRaw = jwtService.generateRefreshToken();
        boolean rotated = refreshTokenRepository.rotate(existing.getId(), existing.getVersion(),
                jwtService.hashRefreshToken(newRaw), clock.instant().plus(Duration.ofDays(30)));
        if (!rotated) {
            // someone else rotated it concurrently → the reuse's detection
            refreshTokenRepository.revokeFamily(existing.getFamilyId());
            throw new RefreshTokenReuseDetectedException();
        }

        UserPrincipal principal = loadPrincipal(existing.getUserId());
        String accessToken = jwtService.generateAccessToken(principal, UUID.randomUUID().toString());
        return new AuthResponse(accessToken, newRaw, 900);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        jwtService.hashRefreshToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash)
                .ifPresent(refreshTokenRepository::revoke);
    }
}
```

### 6.4 The `RefreshTokenRepository` (the atomic's rotation)

```java
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private final JdbcClient jdbc;

    public Optional<RefreshToken> findByTokenHash(String hash) {
        return jdbc.sql("SELECT * FROM refresh_tokens WHERE token_hash = :hash")
                .param("hash", hash)
                .query(RefreshToken.class)
                .optional();
    }

    /**
     * The ATOMIC's rotation: the version's check + the revoked's check in the WHERE clause.
     * The concurrent's refresh → 0 rows updated → the reuse's detection.
     */
    public boolean rotate(Long id, int expectedVersion, String newHash, Instant newExpiresAt) {
        int updated = jdbc.sql("""
                        UPDATE refresh_tokens
                           SET token_hash = :newHash,
                               expires_at = :expiresAt,
                               revoked = TRUE,
                               version = version + 1
                         WHERE id = :id
                           AND version = :expectedVersion
                           AND revoked = FALSE
                        """)
                .param("newHash", newHash)
                .param("expiresAt", newExpiresAt)
                .param("id", id)
                .param("expectedVersion", expectedVersion)
                .update();
        return updated == 1;
    }

    public void revokeFamily(String familyId) {
        jdbc.sql("UPDATE refresh_tokens SET revoked = TRUE WHERE family_id = :familyId")
                .param("familyId", familyId)
                .update();
    }
}
```

### 6.5 The `AuthController`

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }
}
```

### 6.6 The error's handlers (the consistent's contract)

```java
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String code = "TOKEN_INVALID";
        if (authException instanceof InsufficientAuthenticationException) {
            code = "TOKEN_MISSING";
        } else if (authException instanceof OAuth2AuthenticationException oae) {
            if (oae.getError() instanceof InvalidBearerTokenException) {
                String description = oae.getError().getDescription();
                if (description != null && description.contains("expired")) {
                    code = "TOKEN_EXPIRED";
                }
            }
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getHeader("WWW-Authenticate");   // the RFC 6750's challenge
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of(code, "Authentication required", request.getRequestURI()));
    }
}
```

```java
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of("FORBIDDEN", "You do not have permission to access this resource",
                        request.getRequestURI()));
    }
}
```

### 6.7 The client's refresh's interceptor (the SPA)

```javascript
// the axios instance
let refreshPromise = null;

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    if (error.response?.status !== 401 || original._retried) {
      return Promise.reject(error);
    }
    if (error.response?.data?.code === 'TOKEN_INVALID' ||
        error.response?.data?.code === 'TOKEN_REVOKED') {
      // the token is broken → re-authenticate (do NOT refresh)
      redirectToLogin();
      return Promise.reject(error);
    }

    original._retried = true;
    // the SINGLE-FLIGHT: all the concurrent 401s share one refresh
    if (!refreshPromise) {
      refreshPromise = axios.post('/api/auth/refresh', {}, { withCredentials: true })
        .finally(() => { refreshPromise = null; });
    }
    try {
      const { data } = await refreshPromise;
      api.defaults.headers.common.Authorization = `Bearer ${data.accessToken}`;
      return api(original);          // the retry
    } catch (refreshError) {
      redirectToLogin();
      return Promise.reject(refreshError);
    }
  }
);
```

---

## 7. Common Pitfalls & Gotchas

| # | ❌ Wrong | ✅ Right | Why |
|---|----------|----------|-----|
| 1 | The refresh token as a JWT | The opaque (the revocable) | The rotation's detection needs the state |
| 2 | The refresh token stored raw | Hashed (SHA-256) | A DB's leak = a takeover |
| 3 | The rotation without the version's check | The atomic's `UPDATE ... WHERE version = ?` | The concurrent's refresh isn't detected |
| 4 | The reuse only revokes the presented token | Revoke the **whole family** | The thief may be racing the user |
| 5 | The generic 401 for everything | The distinct's codes (`EXPIRED` vs `INVALID`) | The client's behaviour differs |
| 6 | The refresh's 500 logs the user out | The retry for the transient's failures | The auth server's blip ≠ the invalid's session |
| 7 | The multi-tab's refresh race | The single-flight + the cookie's approach | The mass's logout |
| 8 | The access token in the `localStorage` | The in-memory + the httpOnly's refresh | The XSS's theft |
| 9 | The `SecurityContext` saved explicitly with the stateless chain | `requireExplicitSave(false)` | The "the context is empty" |
| 10 | The `@PreAuthorize` on the `hasRole` with the mismatched prefix | The consistent's prefixing | The 403 for the admins |
| 11 | The `open-in-view` + the entity's lazy in the DTO's mapping | The DTO's projection | The 500s in the serialization |
| 12 | The refresh's endpoint without the rate limiting | The limit | The brute force |
| 13 | The access token's TTL of 24h | 5-15 min | The theft's window |
| 14 | The logout that only clears the client | The refresh's row's revocation | The stolen token survives |
| 15 | The same refresh token for all the devices | Per-device rows (the device's listing) | The "the logout this device" |

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner
**Q: What does "stateless authentication" mean?**
**A:** The server doesn't keep a session's state for the authenticated requests — it verifies
the token's signature (and the claims) on each request, with no store's lookup. The
`SecurityContext` is created per request and discarded.

**Q: How does the server know the user is logged in on the next request?**
**A:** The client sends the token in the `Authorization: Bearer` header. The server verifies the
signature with the issuer's public key and the claims' validity, and builds the
`Authentication` for that request.

### 🟡 Intermediate
**Q: Explain the login flow in a Spring Boot app with JWT.**
**A:** The controller receives the credentials → the `AuthenticationManager` →
the `DaoAuthenticationProvider` → the `UserDetailsService` (the DB) + the
`PasswordEncoder.matches` → an authenticated `Authentication` → the `JwtService` issues the
access token (the signed's claims) and a refresh token (the opaque, whose hash is stored in
the DB) → the response returns both.

**Q: Why is the access token short-lived and the refresh token long-lived?**
**A:** The access token is the hot path — it must be verifiable without state, so its claims
are a snapshot; a short TTL bounds the damage if it's stolen and keeps the permissions fresh.
The refresh token is the cold path — it can afford a store's lookup, and that state is what
makes the session revocable and the theft detectable.

**Q: How does the refresh work?**
**A:** The client sends the refresh token to the refresh's endpoint. The server hashes it,
looks up the row, and performs the **atomic rotation** (marking the old as revoked and
inserting the new with the same family id). If an already-used token is presented, the reuse
is detected and the entire family is revoked.

### 🔴 Advanced / Senior
**Q: How do you handle the refresh-token race between two concurrent requests?**
**A:** The server-side atomic's rotation (the `UPDATE ... WHERE id = ? AND version = ? AND
revoked = false` — only one of the concurrent rotations affects a row → the loser detects it
→ the reuse's detection). The client-side single-flight (all the concurrent 401s share one
refresh's promise) prevents the race from happening in the first place, and the cross-tab
coordination (a `BroadcastChannel` or the refresh token in an httpOnly cookie) handles the
multi-tab case. The layering is: the client reduces the race, the server detects it.

**Q: How would you implement a "log out from all devices"?**
**A:** The user's token's version counter (or a `sessions_revoked_at` timestamp) in the user
record, embedded in the access token as a claim (`ver`) and checked at verification (a cached
read). Bumping the version invalidates every outstanding access token immediately; the
refresh tokens are revoked in bulk (the family or the user's rows). The alternative is a
blocklist per device, but the version counter is a single field and a single increment.

**Q: How do you make the refresh token device-bound?**
**A:** Bind it to a device's identifier: a per-device row with a device's id, a refresh token's
hash, the last-used, the device's name, and the location; the rotation's reuse is detected per
family; the "revoke this device" deletes the row; the "revoke all" bumps the version. For
the stronger binding, a DPoP (the proof-of-possession) binds the token to the client's key —
the refresh must be accompanied by a signature over the request with the device's key.

### ⚫ Expert / Principal
**Q: Design the stateless authentication's architecture for a platform with the SPAs, the
mobile, the partners, and the internal services.**
> (The surfaces' profiles, the token's storage per surface, the key's management, the
revocation's strategy, the audit, the incident's runbook — as in the Expert Q&A of chapter 01.)

---

## 9. Counter-Questions & Traps

### CQ1. "If the access token is stateless, why do you need a refresh token at all?"
- **Naive answer:** "To keep the user logged in."  ← the trap
- **The trap:** The interviewer wants the architectural reasoning.
- **Expert answer:** The refresh token isn't just about convenience — it **reintroduces the
state in the cold path**, which buys three things the stateless access token cannot have:
(1) **the revocation** — the session can be killed (the row's deletion) without waiting for
the access token's TTL; (2) **the theft's detection** — the rotation's reuse's detection
catches the attacker within one refresh cycle (minutes) instead of never; (3) **the session's
management** — the device's listing, the "the logout everywhere", the seat's count, the
concurrency's limits, the risk's scoring. So the design is **not** "the stateless token +
a convenience's refresh"; it's "**the stateless hot path + the stateful control plane**". The
state lives where it's needed (rarely) and doesn't live where it's expensive (every request).

### CQ2. "What if the user's permissions change — the token has the old roles?"
- **Naive answer:** "They get the new roles when the token refreshes."  ← partially right
- **The trap:** The interviewer wants the staleness's window and the mitigations.
- **Expert answer:** That's the correct baseline — the window is the access token's TTL (5-15
minutes). For most apps, that's an acceptable bound. When it isn't (the admin's role, the
financial's permissions, the compliance's requirements), the mitigations, in increasing cost:
(1) the **shorter TTL** (the high-privilege tokens get 60 seconds); (2) the **the user's
version counter** (a claim in the token; the check at verification — a cached read; the
revocation is instant for everyone); (3) the **the event-driven's invalidation** (the role's
change → the event → the auth service's cache's bump → the version's increment → the tokens
die); (4) the **the fine-grained's check against the DB** for the sensitive operations (never
trust the token's role alone for the money). The mature design: the **coarse roles in the
token** (the fast path) + the **the version's check** (the instant's revocation) + the
**the sensitive's checks against the source of truth**.

### CQ3. "Why not just use the access token with a 30-day expiry and skip the refresh?"
- **Naive answer:** "Simpler."  ← the trap
- **The trap:** The interviewer wants the security's consequences enumerated.
- **Expert answer:** You'd give up: (1) the **the revocation's immediacy** (a stolen token is
valid for 30 days — and you cannot kill it without the state, which defeats the purpose);
(2) the **the theft's detection** (nothing to detect — the token just works); (3) the
**the permissions' freshness** (a revoked role is live for 30 days); (4) the **the session's
management** (no device's listing, no "the logout everywhere"). The counter-argument in
favour: for a **purely internal, single-user-per-token** scenario, a long-lived token is
fine (and simpler). The real reason people *think* it's simpler: the refresh's rotation,
the storage, the race's handling are genuinely non-trivial work — but that work buys the
security's properties, and skipping it is choosing a weaker system, not a simpler one.

### CQ4. "The `SecurityContext` is a `ThreadLocal` — is the stateless mode still safe?"
- **Naive answer:** "Yes, because it's cleared after the request."  ← the trap
- **The trap:** The interviewer wants the *why* it's safe (or where it isn't).
- **Expert answer:** In the stateless mode it's safe because the context is **rebuilt from
scratch per request** by the filter — there's no "the previous's user" to inherit; the
thread's local is overwritten at the start of each request and cleared in the `finally`. The
danger zones are: (1) the **the async** — a new thread doesn't inherit the context (the lost
identity) or, with the inheritable's strategy, could inherit a stale one; (2) the **the
thread pool's leak** — if the context isn't cleared, the next request on that thread could
see the previous user's identity. The mitigations: the `SecurityContextHolderFilter` clears
it in a `finally` (the framework's default), the `RequestAttributeSecurityContextRepository`
(the per-request's storage → no thread's state at all), and the `TaskDecorator` for the
async. The key's insight: **the stateless mode is actually *safer* here than the stateful
one**, because the context's lifetime is exactly one request.

### CQ5. "Why does the refresh token need a `familyId`?"
- **Naive answer:** "To group them."  ← the trap
- **The trap:** The interviewer wants the reuse's detection's mechanism.
- **Expert answer:** The family is the **lineage** of the rotations — every token derived from
one login shares the family id. It exists to make the **the reuse's detection's blast radius
correct**: when an already-used token is presented, you don't just reject it — you know it was
stolen (the legitimate user's copy was already consumed), so you **revoke the entire family**,
including the token the legitimate user is currently holding. The consequence is a forced
re-login — which is the *correct* outcome, because you can't tell which copy is the
attacker's. The alternative (revoking only the presented token) would leave the attacker
holding a valid successor — the detection would be useless.

---

## 10. Rapid-Fire MCQs

**1.** In the stateless mode, the `SecurityContext` is:
A) Stored in the Redis  B) Created per request  C) Persisted in the DB  D) Shared between the users
**✅ B**

**2.** The refresh token should be:
A) A JWT  B) An opaque, random string  C) The same as the access  D) Base64 of the password
**✅ B**

**3.** The refresh token is stored in the DB:
A) In the plain  B) Hashed  C) Encrypted (the reversible)  D) Not at all
**✅ B**

**4.** The rotation's reuse's detection means:
A) The token is expired  B) An already-used token was presented → revoke the family  C) The signature is invalid  D) The user is locked out
**✅ B**

**5.** The "the family" is:
A) The user's roles  B) The lineage of the rotations from one login  C) The set of the devices  D) The token's claims
**✅ B**

**6.** The access token's TTL should be:
A) 30 days  B) 5-15 minutes  C) 24 hours  D) 1 year
**✅ B**

**7.** The `SecurityContextRepository` recommended for the stateless mode:
A) `HttpSessionSecurityContextRepository`  B) `RequestAttributeSecurityContextRepository`  C) `NullSecurityContextRepository`  D) `DelegatingSecurityContextRepository`
**✅ B**

**8.** The `SessionCreationPolicy` for a stateless API:
A) `IF_REQUIRED`  B) `ALWAYS`  C) `STATELESS`  D) `NEVER`
**✅ C**

**9.** The refresh's rotation must be:
A) Non-atomic  B) Atomic (a version's check in the WHERE clause)  C) Asynchronous  D) Cached
**✅ B**

**10.** The 401's error's body should distinguish:
A) Nothing  B) The expired vs the invalid (the client's behaviour differs)  C) The user's role  D) The server's version
**✅ B**

**11.** The `BearerTokenResolver` should allow the token in the query's parameter:
A) Always  B) Never (the logs, the history, the Referer)  C) Only in the dev  D) Only for the mobile
**✅ B**

**12.** The `jti` claim in the access token is used for:
A) The encryption  B) The unique id (the audit, the blocklist, the replay)  C) The audience  D) The issuer
**✅ B**

**13.** In the hybrid architecture, the state is in:
A) The access token  B) The refresh token's storage  C) The `SecurityContext`  D) The `ThreadLocal`
**✅ B**

**14.** The "the logout everywhere" is best implemented with:
A) The `jti`'s blocklist per token  B) The user's version/token's counter  C) The `exp`'s change  D) The `iss`'s change
**✅ B**

**15.** The `requireExplicitSave` should be `false` for the stateless chain:
A) True  B) False  C) Depends on the DB  D) Only in the prod
**✅ B**

---

## 11. Hands-On Exercises

**🧪 Lab 1 — The full loop**
1. Implement the login, the refresh, and the logout.
2. Use `curl` to log in, call a protected endpoint, wait for the expiry (use a 30-second TTL
   in the test profile), refresh, and call again.

**🧪 Lab 2 — The reuse's detection**
1. Refresh once (the token rotates).
2. Present the **old** refresh token again → observe the 401 and the family revocation.
3. Try the **new** token → also rejected (the family is dead).

**🧪 Lab 3 — The concurrent's refresh**
1. Fire two refreshes with the same token in parallel.
2. Assert: exactly one succeeds; the other detects the reuse.

**🧪 Lab 4 — The claims' mapping**
1. Map the `scope` and the `roles` to the authorities.
2. Test with `@WithMockUser` and with a real JWT.

---

## 12. Code Challenges

### Challenge 1 — The full auth's service (medium-hard)
**Goal:** The login, the refresh (with the rotation + the reuse's detection), the logout, the
device's listing.
**Requirements:** the hashed's storage, the atomic's rotation, the family's revocation, the
transactions, the tests, the metrics.

### Challenge 2 — The client's SDK (medium)
**Goal:** A typed client with the automatic's refresh, the single-flight, the retry's
protection, and the error's handling.
**Requirements:** the TS types, the interceptor, the `AbortController`'s handling, the
tests.

### Challenge 3 — The "the logout everywhere" (hard)
**Goal:** The version's counter + the bulk's revocation + the propagation.
**Requirements:** the claim, the cached's verification, the bulk's update, the event's
invalidation, the tests.

---

## 13. Scenario-Based Questions

**S1.** *"We're moving from the session-based auth to JWT. What could break for the users?"**
>
> **Ideal answer:** (1) **The inventory of the behaviours** that change: (a) the **the
existing sessions** — every user is logged out at the cutover (unless you migrate the
sessions → the token's issuance on the first request → the "the session's bridge": a filter
that exchanges a valid session for a JWT). (b) the **the browser's tab's behaviour** — the
session's cookie is shared across the tabs; the JWT in the memory is per-tab → the multi-tab
users experience the logouts (the fix: the refresh token in the httpOnly's cookie, shared;
the access token in the memory, per-tab). (c) the **the CSRF** — the session's cookie's
endpoints were protected by the CSRF; the header-based JWT is not (the exposure: any
state-changing endpoint that still accepts the cookie's auth must keep the CSRF). (d) the
**the mobile's apps** — the versioned API's clients; the old versions keep the session's
auth (the backend must support both during the migration). (e) the **the integrations** — the
partner's scripts using the session's cookie break (the API's versioning, the the parallel's
support). (f) the **the XSS's exposure changes** — if you put the token in the `localStorage`,
the theft's surface changes (the session's cookie was httpOnly). (g) the **the clock's skew**
and the **the token's expiry's handling** — the app must handle the 401's refresh (the
old app just got a redirect). (2) **The migration's plan**:
(a) **the parallel's operation** — the server accepts both (the session's cookie AND the
bearer) for a transition's window; the new's clients are rolled out first (the SPA's release,
the mobile's gradual's rollout); (b) the **the bridge's endpoint** — `POST /api/auth/exchange`
(the session → the token's pair) so the logged-in users get a token without re-login;
(c) the **the mobile's versioning** — the API's version 2 accepts the tokens; the version 1
(the sessions) is deprecated with a date; (d) the **the metric** — the session's usage's
count → the target's zero → the session's support's removal. (3) **The rollback's plan** —
the flag (the server's config to accept the sessions) stays until the migration is proven.
(4) The senior framing: "**the migration is a compatibility's problem, not a token's problem** —
the JWT itself is trivial; the hard part is that the session and the token have different
*sharing* semantics (the session's cookie is shared across the tabs and the device's state;
the token in the memory is per-tab) and different *theft* surfaces. So: run both in parallel,
give the users a bridge (the session → the token's exchange) so nobody is logged out, watch
the session's usage's metric fall to zero, and only then remove the session's code. The
metric is the migration's real control — without it you'll never know when you're safe to
delete the old path."

**S2.** *"The API's latency is 40ms at p50 but 800ms at p99, and the p99 correlates with the
auth service's restarts."**
>
> **Ideal answer:** (1) **The diagnosis**: the **the issuer-uri's eager's discovery** — with
`spring.security.oauth2.resourceserver.jwt.issuer-uri`, Spring Boot fetches the issuer's
metadata at the **startup** and caches the JWKS with a TTL (the default 5 minutes for the
JWK's set). If the verifier's cache **expires** and the fetch **fails or is slow** (the auth
service's restart, a network's blip, the DNS's hiccup), the verification is delayed by the
remote's call — and Nimbus's `RemoteJWKSet` **fails open** (it uses the cached keys) but
**after** the failed fetch's attempt → the latency's spike. So: the p99 spikes whenever the
JWKS's cache expires and the auth service is slow/unavailable. (2) **The mitigations**:
(a) **the cache's TTL tuning** — `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` +
the `NimbusJwtDecoder`'s `setCacheTTL(...)` (e.g. 10-30 min) → the fewer refreshes, the
fewer spikes. The trade-off: a longer TTL means a revoked/rotated key is honoured longer (the
rotation's window). (b) **the local's cache of the JWKS** — the Nimbus's cache is in-memory
per instance; a shared's cache (the Redis) or a sidecar (the Envoy's JWKS's cache) reduces the
load on the auth service. (c) **the auth service's HA** — the multiple's replicas, the health's
checks, the readiness's probes (so the traffic doesn't hit a restarting instance); (d) **the
circuit breaker** on the JWKS's fetch (the fail-fast → the cached keys); (e) **the prefetch**
— warm the cache at the startup (the `issuer-uri`'s discovery does that) and keep the TTL
longer than the auth's expected's outage. (3) **The observability**: (a) the **the JWKS's
fetch's metrics** (the Nimbus's `JWKSetCache` doesn't expose much → wrap the fetch in your
own `RestOperations` with the Micrometer's timers, or use a custom `JWKSource` with the
metrics); (b) the **the verification's latency's histogram** (the `JwtDecoder`'s timing, split
by the outcome); (c) the **the correlation** with the auth service's restarts (the p99's
spike aligns with the cache's expiry + the auth's deploys). (4) **The deeper's fix**: the
**the auth service's availability** is the real issue — the auth service shouldn't be a
single's point of failure for the API's hot path. The architectures: the **the local's
verification** (the asymmetric's keys → no dependency at the request time — this is the
point of the asymmetric's algorithms) and the **the JWKS's high-availability** (the replicated
auth service, the multiple's JWKS's endpoints). (5) The senior framing: "**the asymmetric
verification is supposed to make the hot path independent of the issuer** — the p99 tells you
the cache's design is the weak link, not the crypto. The fix is a longer JWKS's cache TTL
(the refreshes are rare and the rotation is the graceful), the auth service's HA, and the
metrics on the fetch. And I'd check whether the Nimbus's fail-open is actually what you want:
it's the right default for the availability, but the rotation's window becomes the cache's TTL
— so the rotation's plan has to account for it."

---

## 14. Tricky Interview Questions

**T1. "Why is the refresh token not a JWT?"**
> Because the whole point of the refresh token is to be **stateful** — revocable, rotatable,
and detectable-on-reuse. A JWT is self-contained; you can't revoke it, and the rotation's
reuse's detection is impossible without the state. (You *can* have a JWT refresh token, but
then you must keep the state server-side to detect the reuse — at which point the opaque
token is simpler.)

**T2. "What's the difference between the 'the access token expired' and the 'the token is
invalid' from the client's perspective?"**
> The expired → refresh and retry (the user stays logged in). The invalid → the token is
broken or forged → re-authenticate (the user logs in again). Collapsing them causes either
mass re-logins or an infinite refresh loop. This is why the error's body carries a stable
machine-readable code.

**T3. "Can the access token be used at multiple APIs?"**
> Yes, if the `aud` covers them — but that's a security decision. The single's audience (one
API per token) gives the **the least privilege** and prevents the token's reuse across the
services (if API A is compromised, its token can't be used on API B). The multi-audience is
convenient for the microservices (one token for all) but increases the blast radius.

**T4. "Why does the stateless mode still need the `SecurityContextHolder`?"**
> Because the framework's model is uniform — the `Authentication` object is the interface
between the security's subsystem and the application. The stateless mode just creates it per
request instead of loading it from the session.

**T5. "What happens if the client sends two different access tokens concurrently?"**
> Both are verified independently; each request gets its own `SecurityContext`. The only
interaction is if one is invalid (the 401) and the other is valid (the 200) — which is normal
during a refresh's window. The "the last one's wins" issue only arises with the *cookie*-based
storage (the race) — the header-based is per-request and safe.

**T6. "Why is the `SecurityContext` cleared at the end of the request?"**
> The thread is returned to the pool. If the context isn't cleared, the next request on that
thread inherits the previous user's identity — a **cross-request's data leak** (and a
cross-tenant's leak in a multi-tenant app). The `SecurityContextHolderFilter` clears it in a
`finally` block.

**T7. "Is the `BearerTokenAuthenticationFilter` a `OncePerRequestFilter`?"**
> No — it extends `OncePerRequestFilter`? Let me be precise: `BearerTokenAuthenticationFilter`
extends `OncePerRequestFilter` in recent versions (historically it was a plain `Filter`).
Its key property is that it only acts **if a token is present**; otherwise it passes through
(the anonymous's authentication is set later by the `AnonymousAuthenticationFilter`).

**T8. "How do you handle the 'the token is valid but the user was deleted'?"**
> The token's claims are a snapshot; the user might be deleted or disabled after its issuance.
The options: (1) the **the user's version/status's check** at the verification (a cached
read — the DB hit per request, which is the cost of the stateless design); (2) the **the
short TTL** (bounded staleness); (3) the **the event-driven's invalidation** (the user's
deactivation → the event → the version's bump → the tokens die). Most apps accept the TTL's
bound, except for the high-value actions, where a re-check is warranted.

---

## 15. Follow-Up Questions Asked by Interviewers

**F1** (after the flow) → *"Where would you add the MFA in this flow?"*
→ In the login, between the password's verification and the token's issuance: the password
succeeds → the app creates the **MFA's challenge** (the short-lived, the Redis, the attempt
counter) → the client completes the challenge → **then** the tokens are issued. The tokens
themselves can carry the `amr`/`acr` claims to record that the MFA happened (for the step-up's
checks).

**F2** (after the refresh) → *"What if the refresh token is stolen but the attacker doesn't use
it — and the legitimate user refreshes?"*
→ The attacker's later use is detected as a reuse (the token was already consumed) → the
family is revoked → both parties are logged out. That's the correct and safe outcome: the
system cannot distinguish the thief from the user, so it invalidates both and forces a
re-login. The **the device's binding** (the DPoP) mitigates this: the stolen token is useless
without the device's key.

**F3** (after the statelessness) → *"How do you implement the 'the soft logout' (the user
closes the browser but stays logged in)?"*
> The refresh token's persistence in the client (the `localStorage` with a TTL, or the
"the remember me" cookie with a max-age) vs the session-storage (the browser's session). The
token's lifetime is the decision, and the server's `maxAge` on the cookie is the mechanism.

**F4** (after the errors) → *"What if the auth service is down and the client needs to
refresh?"**
> The client should **not** log the user out on a 5xx — only on a definitive 401. The access
token's remaining lifetime is the buffer (a 15-minute access token survives a 5-minute
outage). The client should retry with the backoff and show a "the session's refreshing"
state. The server's side: the refresh's endpoint should be highly available and the errors
should be distinguishable (the 503 vs the 401).

---

## 16. Real 3–5 Year Scenarios

**R1. "We're launching the mobile app with JWT auth. The users keep getting logged out."**
>
> **Ideal answer:** (1) **The hypothesis's list** (the mobile's specifics): (a) the **the
device's clock** — a mobile's clock can be wrong (the user's manual change, the timezone's
bug, the factory's reset) → the `exp` is compared to the server's time → a device 5 minutes
fast → the token appears valid for 5 extra minutes (harmless); a device 5 minutes **slow** →
the token appears expired immediately after issuance → the constant's refreshes. The
mitigation: the generous `ClockSkew` (60s), and ideally the **the server-issued `exp`**
based on the server's clock (which it is) + the client's clock's offset's detection (the
client can measure the offset from the response's `Date` header and correct). (b) the **the
battery's optimization** — the OS kills the app in the background → on resume, the in-memory
access token is gone → the app must restore it from the secure's storage; if the
implementation kept the access token only in memory and the refresh token in the Keychain,
the recovery works — but many teams store **both** in the Keychain and then the access token
is fine too; the bug is usually the **the app not handling the resume** (the state is null →
the app shows the login even though the refresh token is valid). (c) the **the network's
switching** (the WiFi → the cellular) → an in-flight request fails → the app treats it as the
401 and logs out (the fix: only the 401 triggers the re-auth; the network's error is a
retry). (d) the **the refresh's race** (the app's background's refresh + the foreground's
refresh) → the single-flight; the mobile's apps often fire the refresh from multiple places
(the API's interceptor, the push's handler, the screen's focus) → the same race as the web.
(e) the **the server's clock's skew** across the pods (the same as the web's). (f) the **the
install's upgrade** (the app's version's mismatch with the API's version → the old's tokens
are rejected).
(2) **The diagnosis's method**: (a) the **the correlation's analysis** — the logouts' rate vs
the app's version, the device's OS, the network's type, the time of day; (b) the **the
structured's logs** — the refresh's endpoint's logs with the reason (the expired, the
invalid, the reuse's detected, the 500) and the client's metadata (the app's version, the
device's clock's offset); (c) the **the server's timestamp** in the response vs the device's
clock (the offset's measurement). (3) **The fixes**: (a) the **the clock's offset's
correction** in the client (the server's `Date` header → the offset → the local's
adjustment); (b) the **the single-flight's refresh** (a shared's coroutine/actor per app);
(c) the **the resume's handling** (the app's `onResume` → the token's restoration → the
network's check → the silent's refresh); (d) the **the network's error's distinction** (only
the 401's a logout signal); (e) the **the server's refresh's robustness** (the retry for the
transient's failures, the generous's `ClockSkew`).
(4) **The systemic**: (a) the **the client SDK** (a shared's library so the behaviour is
consistent across the apps) — the bug's root cause is usually the per-team's reimplementation;
(b) the **the observability** (the refresh's success/failure's ratio, the reason's
distribution, the client's version in the log) — you can't see the mobile's issues without
the client's metadata; (c) the **the staged's rollout** (the app's release is a gradual's
percentage — the crash-free's rate and the login's rate as the release's gates).
(5) The senior framing: "**the mobile's 'random logouts' are almost always a client-state
bug, not a server's bug** — the in-memory token's loss on the background's kill, the
un-coordinated refresh, the network's error being mistaken for a 401, or the device's clock.
The server-side's contract must make the diagnosis possible: a stable reason's code on the
refresh's failures, and the client's version + clock's offset in the logs. And the biggest
lever is the **the shared's client SDK** — otherwise every team reimplements the token's
lifecycle and every one of them has its own bug. I'd also push the server to distinguish
'definitive rejection' from 'temporary failure', because a client that logs the user out on a
503 is the single most common cause of these tickets."

**R2. "We need to add 'remember me' — a 30-day session. How?"**
>
> **Ideal answer:** (1) **The decision's framing**: the "remember me" is a **the refresh
token's lifetime's extension** — not a new mechanism. (a) The **the persistent's refresh
token** (the `Max-Age` on the cookie) — the 30-day's persistence; the access token stays
short. (b) The **the "the trusted device"** concept — a long-lived refresh token with a
**the device's identifier**, the last-used, the location, and the ability to revoke it
individually. (c) The **the absolute's expiry** (the 30 days) even if the client keeps
sending it — the server's row's `expiresAt` is the authority, not the client's persistence.
(2) **The implementation**: the login's request carries a `rememberMe=true` → the refresh
token's row gets the 30-day's expiry (instead of, say, 12 hours) and is marked as a
"remembered" session → the response sets the cookie with the `Max-Age=30d`. The device's
list's UI shows the remembered devices (the user-agent, the IP, the last-used) with the
"revoke" action. (3) **The security's considerations** (this is where the interview's value
is): (a) the **the extended's token is a higher-value target** → the extra's care: the
**the rotation's reuse's detection** (already there), the **the device's binding** (the
DPoP or the device's key), the **the notification** (a new device → the email's alert —
essential for the "remember me" — if an attacker gets the long-lived token, the user is
notified); (b) the **the "the remembered device" is a persistent's credential** → the
**the risk's scoring** (the geo, the device's fingerprint) — the "the impossible's travel"
→ the step-up or the re-authentication; (c) the **the scope's limitation** — the remembered
session's token should carry **the same scopes** but perhaps a **shorter's access TTL** and
the **no the sensitive's operations** (the payments) — or the step-up's requirement for them;
(d) the **the storage** — the httpOnly, the Secure, the `SameSite=Lax` (the `Strict` breaks
the OAuth2's redirect), the `Path=/api/auth` (so it's only sent to the auth's endpoints);
(e) the **the XSS's risk** — the cookie's httpOnly protects it, but the CSRF's token must
accompany the refresh's request (a cross-site's POST to /refresh would rotate the token —
the "the login's CSRF" is a real attack: the attacker forces the victim to log into the
attacker's account!). **That's a great point to raise**: the refresh's endpoint (and the
login's) is a **CSRF's target** even with the httpOnly's cookie — the fix is the CSRF's
token (the double-submit) or the `SameSite=Strict` (or the Origin's check). (4) **The
user's controls**: the "the devices' page" (the list, the revoke, the "the revoke all"), the
"the sign out everywhere", the activity's log. (5) The senior framing: "**the 'remember me' is
the refresh token's lifetime, but the security's treatment should scale with it** — a
30-day's token is a standing's credential, so it gets the device's visibility, the new
device's notification, the per-device's revocation, and the risk's scoring. And the subtle
bug: the refresh's endpoint is a **CSRF's target** even with the httpOnly's cookie — a
cross-site's POST can force the victim's browser to rotate the token into the attacker's
session (the 'login's CSRF'). So the refresh's endpoint needs the CSRF's protection (the
double-submit or the Origin's check) — that's the detail most implementations miss."

---

## 17. Frequently Asked Production Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| The random's logouts (the web) | The multi-tab's refresh race | The single-flight; the cookie's approach |
| The random's logouts (the mobile) | The background's kill / the network's error as a 401 | The resume's handling; the error's distinction |
| The 500s from the refresh | The auth service's outage | The retry; the client's distinction |
| The mass's logout after the deploy | The secret's rotation without the dual-key's window | The two-phase's rotation |
| The 401s with the "the audience's invalid" | The `aud` mismatch | The `JwtAudienceValidator`; the config |
| The slow's first requests (the p99) | The JWKS's cache's expiry + a slow's fetch | The longer's `cacheTTL`; the auth's HA |
| The "the user was deleted but the token works" | The claims' snapshot | The version's check; the short's TTL |
| The refresh's 401 loop | The generic's error's code | The distinct's codes |

---

## 18. Debugging Scenarios

**D1. "Decode the token and see the claims:"**
```bash
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq .
```

**D2. "Log the refresh's outcome with the reason:"**
```java
log.info("refresh outcome={} userId={} family={} device={} ip={}",
        outcome, userId, familyId, userAgent, ip);
```

**D3. "Find the concurrent's rotations:"**
```sql
SELECT family_id, COUNT(*) AS rotations, MIN(created_at), MAX(created_at)
  FROM refresh_tokens
 GROUP BY family_id HAVING COUNT(*) > 1;
```

**D4. "Check the token's remaining's life:"**
```java
log.info("expiresIn={}s", Duration.between(Instant.now(), jwt.getExpiresAt()).getSeconds());
```

**D5. "Verify the atomic's rotation's query:"**
```sql
-- the manual's simulation
UPDATE refresh_tokens SET revoked = TRUE, version = version + 1
 WHERE id = 91 AND version = 1 AND revoked = FALSE;
-- the 0 rows → someone else won the race
```

---

## 19. Architecture Design Questions

**Q1. 🏗️ Design the authentication's flow for a multi-tenant SaaS with the SPAs and the
mobile apps.**
> **Answer structure:** (1) The **the identity's provider** (the OIDC, the tenant's claim, the
MFA's policies per tier); (2) the **the token's issuance** (the access's TTL, the claims per
tenant, the refresh's family's model); (3) the **the client's storage** (the SPA: the BFF's
httpOnly's cookies; the mobile: the Keychain); (4) the **the tenant's isolation in the
tokens** (the `tenantId` claim + the DB's RLS + the query's scoping — the token's tenant must
match the URL's tenant); (5) the **the revocation's strategy** (the version's counter, the
per-device's rows); (6) the **the audit** (the issuance, the refresh, the revocation, the
anomalies — the impossible's travel); (7) the **the incident's runbook**.

**Q2. 🏗️ Design the "the session's management" dashboard (the devices, the activity, the
revoke).**
> (1) The **the data's model** (the session's rows: the user, the device, the last-used, the
IP, the geo, the user-agent, the created, the revoked); (2) the **the API's surface** (the
list, the revoke one, the revoke all, the "the suspicious's activity's report"); (3) the **the
alerts** (the new's device, the new's geography, the impossible's travel, the volume); (4)
the **the privacy** (the IP's retention, the GDPR's erasure — the session's rows are the
personal's data); (5) the **the UX** (the notifications, the "the new's sign-in" email, the
suspicious's action's "it wasn't me" → the revoke all).

---

## 20. Cheat Sheet — One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  the ARCHITECTURE: the STATELESS access + the STATEFUL refresh (the hybrid) │
│  the state lives in the COLD path (the refresh), not the HOT path (the req) │
├──────────────────────────────────────────────────────────────────────────────┤
│  the LOGIN:  the DTO's validation → the AuthenticationManager → the issue   │
│  the REQUEST: the BearerTokenAuthenticationFilter → the verify → the ctx   │
│  the REFRESH: the hash → the lookup → the ATOMIC's rotation → the new pair  │
│  the ROTATION's REUSE's DETECTION → revoke the WHOLE FAMILY → the 401      │
├──────────────────────────────────────────────────────────────────────────────┤
│  the ERROR's CODES: TOKEN_MISSING | TOKEN_EXPIRED (refresh!) |             │
│  TOKEN_INVALID (re-auth!) | TOKEN_REVOKED | FORBIDDEN (never retry)        │
├──────────────────────────────────────────────────────────────────────────────┤
│  ⚠ The refresh token: the OPAQUE + the HASHED + the ROTATING + the revocable│
│  ⚠ The rotation MUST be ATOMIC (the version's check) or the race is missed │
│  ⚠ The reuse's detection MUST revoke the FAMILY (the thief may be racing) │
│  ⚠ The client: the SINGLE-FLIGHT's refresh; only the 401's a logout signal │
│  ⚠ The 5xx from the auth service must NOT log the user out                │
│  ⚠ The SecurityContext: the per-request's creation + the TaskDecorator     │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 21. Follow-up Learning

- → [03 · Access & Refresh Tokens, Rotation, Revocation](./03-Access-Refresh-Tokens-and-Rotation.md)
- → [04 · Security Risks, Algorithms & Production Design](./04-Security-Risks-and-Production-Design.md)
- → [05 · Spring Security](../05-Spring-Security/README.md) — the filter chain.

---
**Next Chapter:** → [03 · Access & Refresh Tokens, Rotation, Revocation](./03-Access-Refresh-Tokens-and-Rotation.md)
**Previous Chapter:** ← [01 · JWT Architecture](./01-JWT-Architecture-and-Anatomy.md)
