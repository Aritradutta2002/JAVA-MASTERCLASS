# 05 — Spring Security (Very Deep) · Interview Notes Index

> Spring Security is the topic where "it works" and "it's secure" diverge the most.
> A 3–4 year candidate is judged on: **the filter chain's internals**, the **authentication
> vs authorization** distinction, the **CSRF/CORS** trade-offs, the **IDOR** awareness, and
> the ability to talk about **production patterns** (the MFA, the audit, the rate limiting,
> the multi-tenancy).

| Priority | Chapters | Why It Matters |
|----------|----------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · Security Architecture & Filter Chain](01-Security-Architecture-and-Filter-Chain.md) | The architecture, the filter order, `SecurityContextHolder` |
| ⭐⭐⭐⭐⭐ | [02 · Authentication Flow & UserDetails](02-Authentication-Flow-and-UserDetails.md) | `AuthenticationManager`, `AuthenticationProvider`, the encoders |
| ⭐⭐⭐⭐⭐ | [03 · Authorization, Roles & Permissions](03-Authorization-Roles-and-Permissions.md) | URL vs method level, the `AuthorizationManager`, the guards |
| ⭐⭐⭐⭐⭐ | [04 · CORS, CSRF & Session Management](04-CORS-CSRF-and-Session-Management.md) | The three most-misconfigured headers in Spring apps |
| ⭐⭐⭐⭐ | [05 · Method-Level Security & Custom Guards](05-Method-Level-Security-and-Guards.md) | `@PreAuthorize`, the custom expressions, the SpEL |
| ⭐⭐⭐⭐ | [06 · Production Security Patterns](06-Production-Security-Patterns.md) | The MFA, the audit, the rate limiting, the tenancy, the impersonation |
| ⭐⭐⭐⭐ | [07 · Security Best Practices](07-Security-Best-Practices.md) | The consolidated checklist — the reference chapter |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| Explain the Spring Security filter chain. | The order of the filters, and *where* `SecurityContextHolderFilter` sits relative to the authentication filters. |
| `AuthenticationManager` vs `AuthenticationProvider`? | The manager is the **facade**; the provider is the **strategy**. A provider returning `null` means "I don't handle this token" — the manager moves on. |
| `Authentication` vs `Authorization`? | The token object (principal + credentials + authorities) vs the *decision* (`AuthorizationDecision.isGranted()`). |
| `@PreAuthorize` vs `@PostAuthorize`? | `@PostAuthorize` on a **write** means the side effect already happened. Use `@PreAuthorize` for mutations. |
| Why disable CSRF for a JWT API? | Correct only for **header-based** bearer tokens. If the token is in a cookie, CSRF applies. |
| Why can't I use `*` with `allowCredentials(true)`? | The CORS spec forbids it; the browser rejects the response. Use explicit origins. |
| 401 vs 403? | 401 = "I don't know who you are" (re-authenticate); 403 = "I know, but no" (don't retry). Returning 403 for an expired token causes client refresh loops. |
| How do you prevent IDOR? | The ownership predicate **in the query** (`findByIdAndOwnerId`), not a check in the controller. Return 404, not 403. |
| Where does `SecurityContextHolder` store the context? | `ThreadLocal` by default → lost in `@Async`/schedulers and, worse, **leaked** across pooled threads if not cleared. |
| `BCrypt` vs `Argon2`? | Never MD5/SHA. Argon2id is memory-hard (better against GPU attacks); BCrypt is the pragmatic default. |
| How do you store the refresh token? | **Hashed** (SHA-256) in the DB, bound to the user + device + rotation family. Raw storage = a DB leak = an account takeover. |
| What is the `ROLE_` prefix convention? | `hasRole('ADMIN')` adds `ROLE_`; `hasAuthority('ROLE_ADMIN')` doesn't. Be consistent or admins get 403. |

---

## 🧠 The Mental Model

```text
   the request
      │
      ▼
 ┌───────────────────────────────────────────────────────────┐
 │  the FilterChainProxy                                    │
 │   ├─ chain #1  securityMatcher("/api/**")  @Order(1)      │
 │   └─ chain #2  (default)                  @Order(2)      │
 ├───────────────────────────────────────────────────────────┤
 │  SecurityContextHolderFilter  (the context is loaded)    │
 │  CorsFilter → CsrfFilter → ... → AuthenticationFilter    │
 │  → AuthorizationFilter → ExceptionTranslationFilter       │
 └───────────────────────────────────────────────────────────┘
      │
      ▼
   the controller → the service → the DB
```

> **Caption:** The chain is *selective* — the first `SecurityFilterChain` whose
> `securityMatcher` matches wins. The security's rules are therefore a **composition of
> independent chains**, not one global config.

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **The filter's order** — knowing exactly where `SecurityContextHolderFilter` and
  `AuthorizationFilter` sit, and why `http.cors()` must be called on the chain.
- [ ] **`SecurityContextHolder` in the async** — the `TaskDecorator`, and the cross-request's
  leak.
- [ ] **The `AuthenticationProvider`'s contract** — the `supports()`, the `null` return, the
  `ProviderManager`'s ordering, the `eraseCredentials`.
- [ ] **The `@PreAuthorize`'s SpEL** — the custom expressions, the `@bean` references, the
  `returnObject`/`filterObject` (the list's filtering).
- [ ] **The session's fixation** — `changeSessionId()` (the default, but verify it's not off).
- [ ] **The `WebSecurityCustomizer`** (`@EnableWebSecurity(debug = true)`) and the
  `FilterChainProxy`'s log — the fastest debugging tool in Spring Security.
- [ ] **The multiple chains** — the `securityMatcher` + the `@Order` (the first match wins).

---

## 🧪 Coding Drill

- [ ] Write a `SecurityFilterChain` for an API (the stateless, the bearer) and a separate one
  for the web (the session, the form login) — and prove the routing with a test.
- [ ] Implement a custom `AuthenticationProvider` for a one-time-password (the `supports()`,
  the `null` on the unsupported token) and register it in the `ProviderManager`.
- [ ] Add a custom method-level guard: `@PreAuthorize("@orderGuard.canModify(authentication, #orderId)")`
  — and write the test for the other user's order.
- [ ] Configure the CORS with the explicit origins, the credentials, and the `X-CSRF-TOKEN`
  header — then write the test that the wrong origin gets no `Access-Control-Allow-Origin`.
- [ ] Implement the `@TenantId`-style scoping and write the cross-tenant's test.
- [ ] Add the `AuthenticationEventPublisher` and log the failed logins; then write the lockout
  with the Redis.

---

## 🔐 The Production Checklist (the short version)

- [ ] The `PasswordEncoder` (the BCrypt/Argon2) — never a plain hash.
- [ ] The default-deny at the URL level + the method level for the fine rules.
- [ ] The ownership's predicate **in the query** (the IDOR).
- [ ] The CSRF on for the cookies; off only for the header-based chains.
- [ ] The CORS's explicit origins.
- [ ] The stateless API chain + the session's web chain (the `SecurityContext` is per-request
  in both).
- [ ] The rate limiting on the login (+ the per-account, the lockout).
- [ ] The MFA for the admins and the sensitive operations.
- [ ] The audit of the authentication's events and the authorization's denials.
- [ ] The security's headers (the CSP, the HSTS, the frame options).
- [ ] The secrets in the vault; the actuator restricted; the `X-Request-Id` everywhere.
- [ ] The dependency's updates automated (the SCA in the CI).

---

**Related folders:** ← [04 · Spring Data JPA](../04-Spring-Data-JPA-Hibernate/README.md) ·
→ [06 · JWT Authentication](../06-JWT-Authentication/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
