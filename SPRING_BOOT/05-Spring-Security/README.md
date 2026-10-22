# 05 — Spring Security · Interview Notes Index

> Spring Security is not "an annotation you put on a controller" — it is a **servlet filter
> chain** that every request walks through *before* your code ever runs. The single idea that
> makes it click: **authentication answers "who is this?", authorization answers "may they do
> this?", and both are decisions made by filters and interceptors, not by your business code.**
> Everything in these five chapters — the 401 vs 403 split, the role hierarchy, the CSRF token
> round trip, the `DelegatingPasswordEncoder` prefix — is a consequence of that model.

| Priority | Chapter | Level | Read Time | Why It Matters |
|----------|---------|-------|-----------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · Architecture & Security Filter Chain](01-Architecture-and-Security-Filter-Chain.md) | 🟢→⚫ | ~55 min | `FilterChainProxy` → `DelegatingFilterProxy` → 16 ordered filters → `AuthorizationFilter`. The **ordering contract** is the most-asked Spring Security question in interviews |
| ⭐⭐⭐⭐⭐ | [02 · Authentication Flow, UserDetailsService & Providers](02-Authentication-Flow-UserDetailsService-and-Providers.md) | 🟢→🔴 | ~55 min | `UsernamePasswordAuthenticationFilter` → `ProviderManager` → `DaoAuthenticationProvider` → `UserDetailsService` → `PasswordEncoder`. Why a `UserDetailsService` is only a **lookup**, not the authentication mechanism |
| ⭐⭐⭐⭐⭐ | [03 · Authorization, Roles, Permissions & Method Security](03-Authorization-Roles-Permissions-and-Method-Security.md) | 🟡→🔴 | ~50 min | `hasRole` vs `hasAuthority`, the role hierarchy, `@PreAuthorize`/`@PostAuthorize`/`@PreFilter`, and the fact that **URL rules run first and method rules run deepest** |
| ⭐⭐⭐⭐ | [04 · Password Encoding, Sessions, CSRF & CORS](04-Password-Encoding-Sessions-CSRF-and-CORS.md) | 🟡→🔴 | ~50 min | `DelegatingPasswordEncoder` and the `{id}` prefix, session fixation, the CSRF token lifecycle, and why **CORS is not a CSRF defence** |
| ⭐⭐⭐⭐ | [05 · Production Security Patterns & Best Practices](05-Production-Security-Patterns-and-Best-Practices.md) | 🔴→⚫ | ~50 min | OWASP Top 10 → Spring defence mapping, IDOR/BOLA, mass assignment, secret management, rate limiting, and a ~30-item hardening checklist |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| Explain the Spring Security filter chain. | Listing the filters without the **ordering contract** — an interviewer wants "CORS runs *before* authentication" and "`ExceptionTranslationFilter` converts the `AccessDeniedException` into a 403". Filter *names* alone score half marks |
| `UserDetailsService` vs `AuthenticationProvider`? | Treating `UserDetailsService` as *the* authentication mechanism — it is only a **user lookup**. The **verification** (password match, user-state checks) happens in the `AuthenticationProvider`, which *may* use a `UserDetailsService` but does not have to |
| 401 vs 403 — when is each returned? | Answering "401 = no token, 403 = bad token". The real rule: `ExceptionTranslationFilter` decides **based on whether the `Authentication` is an `AnonymousAuthenticationToken`** — anonymous → 401 (and the client should authenticate), authenticated-but-insufficient → 403 (and retrying will not help) |
| `@PreAuthorize` vs URL security — which wins? | Saying "method security is stronger so it runs first". The truth: **URL rules are evaluated by `AuthorizationFilter` at the filter level (first), method rules by `AuthorizationManagerBeforeMethodInterceptor` around the bean method (last, innermost)** — so a URL denial gives a 403 from the filter, a method denial throws inside the controller |
| `hasRole("ADMIN")` vs `hasAuthority("ROLE_ADMIN")`? | Saying they are "different". They are equivalent — `hasRole` prepends `ROLE_`. The trap is `hasRole("ROLE_ADMIN")`, which looks for `ROLE_ROLE_ADMIN` and matches nobody |
| Which password encoder should I use? | Recommending a raw `BCryptPasswordEncoder` bean. The production answer is `PasswordEncoderFactories.createDelegatingPasswordEncoder()` — the `{id}` prefix is what lets you **upgrade the algorithm without invalidating every existing password** |
| CSRF vs CORS — are they the same? | Saying "CORS protects against CSRF" — **it does not**. CORS is a *browser read-access* control; CSRF is a *write-access* control. CORS does not stop a form on `evil.com` from POSTing to your API |
| Why did I get a 403 on my SPA POST? | Not knowing the CSRF token must be **echoed back** in the `X-XSRF-TOKEN` header (cookie-based) or the `_csrf` parameter (form-based). The cookie alone is not enough |
| Where does the 401 come from? | Not knowing that `ExceptionTranslationFilter` → `AuthenticationEntryPoint.commence()`. If you never customised the entry point, you get an **empty 401 body** — which is why the frontend sees "no error message" |
| How do I protect a single endpoint differently from the rest? | Reaching for `WebSecurityConfigurerAdapter` (removed in 6.0). The 6.x answer is **multiple `SecurityFilterChain` beans with `@Order`** — first match wins |

---

## 🧠 The One Picture — Where Security Lives In The Request

```text
   ┌───────────────────────────────────────────────────────────────────────────────┐
   │  BROWSER / CLIENT                                                            │
   │      GET /api/orders/42   Cookie: JSESSIONID=abc123                          │
   └───────────────────────────────────┬───────────────────────────────────────────┘
                                       │ ▼
   ┌───────────────────────────────────────────────────────────────────────────────┐
   │  SERVLET CONTAINER (Tomcat)                                                  │
   │                                                                               │
   │   FilterChainProxy  (order -100)  ← "springSecurityFilterChain"               │
   │   │                                                                           │
   │   ├─ SecurityContextHolderFilter   ← LOAD the SecurityContext (who am I?)     │
   │   ├─ HeaderWriterFilter            ← security headers (nosniff, HSTS, DENY)  │
   │   ├─ CorsFilter                    ← CORS (BEFORE auth: preflight has no JWT) │
   │   ├─ CsrfFilter                    ← CSRF token validation                     │
   │   ├─ BearerTokenAuthenticationFilter / UsernamePasswordAuthenticationFilter  │
   │   ├─ AnonymousAuthenticationFilter ← an AnonymousAuthenticationToken if none  │
   │   ├─ ExceptionTranslationFilter    ← AuthenticationException→401 /           │
   │   │                                   AccessDeniedException→403                │
   │   └─ AuthorizationFilter           ← THE FINAL ACCESS DECISION                │
   │   │                                                                           │
   │   ▼ (chain continues only if every filter called chain.doFilter)              │
   │   DispatcherServlet → Controller → Service → Repository                       │
   │                                                                               │
   │   … and INSIDE the controller, ONE more check:                               │
   │   AuthorizationManagerBeforeMethodInterceptor  ← @PreAuthorize (innermost)    │
   └───────────────────────────────────────────────────────────────────────────────┘

   THE THREE THINGS THAT GO WRONG IN PRODUCTION — and where they live:
   ┌──────────────────────────────────┬─────────────────────────────────────────────┐
   │ SYMPTOM                          │ ROOT CAUSE LIVES IN                          │
   ├──────────────────────────────────┼─────────────────────────────────────────────┤
   │ "Everything returns 401"         │ Missing entry point / wrong chain (ch. 01)   │
   │ "The rule is not applied"        │ Chain ORDER — first match wins (ch. 01)     │
   │ "401 vs 403 confusion"           │ ExceptionTranslationFilter (ch. 01, 02)     │
   │ "Ownership check missing"        │ No method-level rule (ch. 03)              │
   │ "Password always fails"          │ Encoder prefix mismatch (ch. 04)           │
   │ "SPA gets 403 on POST"           │ CSRF token not echoed (ch. 04)              │
   └──────────────────────────────────┴─────────────────────────────────────────────┘
```

> **Caption:** Filters decide **who** and **whether**, the interceptor decides **whether this
> object**. The stack is *nested*: URL rules are the outer gate, method rules the inner one.
> Learn the nesting and the 401/403 puzzle becomes mechanical.

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **The filter order itself** — recite the 16 filters from the canonical table in [ch. 01](./01-Architecture-and-Security-Filter-Chain.md) without looking. Interviewers ask for the *contract*, not the list.
- [ ] **`SecurityContextHolder` vs `SecurityContextRepository`** — the first is a `ThreadLocal` (where it lives *right now*); the second is *where it is persisted* (session, request attribute, or nowhere). Mixing them up is why "the context is null in an async thread".
- [ ] **Why `AnonymousAuthenticationFilter` exists** — without it, `SecurityContextHolder` would be empty and every "is authenticated" check would NPE.
- [ ] **First-match-wins chain routing** — `@Order` on the `SecurityFilterChain` bean is *not* decoration; it is the routing algorithm.
- [ ] **`ProviderManager` provider selection** — `supports()` decides, the first non-null result wins, and a `ProviderManager` can hold several providers.
- [ ] **`DaoAuthenticationProvider`'s two checks** — the *user-state* checks (disabled/locked/expired) and the *password* check. They throw different exceptions and map to different HTTP responses.
- [ ] **`hasRole` vs `hasAuthority` vs `hasAnyRole`** — the `ROLE_` prefix rule and the `ROLE_ROLE_ADMIN` trap.
- [ ] **The role hierarchy** — `ROLE_ADMIN > ROLE_MANAGER > ROLE_USER`, and the fact that the hierarchy is a **bean** that must be registered.
- [ ] **`@PreAuthorize` vs `@PostAuthorize`** — pre filters the *input*, post filters the *output* (row-level security on the returned collection).
- [ ] **The `{id}` password prefix** — `{bcrypt}$2a$10$...`. Without it, `DelegatingPasswordEncoder.matches` cannot even dispatch to the right encoder.
- [ ] **CSRF token lifecycle** — created → rendered → echoed back → compared. A SPA must read the cookie *and* send the header.
- [ ] **`allowedOrigins("*")` + `allowCredentials(true)`** — this throws `IllegalArgumentException` at runtime, not at compile time.
- [ ] **IDOR / BOLA** — the client supplies the id, the service never checks the owner. The single most common real-world breach.

---

## 🧪 Coding Drill

- [ ] Write **two** `SecurityFilterChain` beans (one `/api/**` stateless, one everything-else session-based) and prove the routing with `curl -i` — capture the exact `Set-Cookie` presence/absence in each response.
- [ ] Turn on `logging.level.org.springframework.security=DEBUG` and reproduce **one 401** and **one 403**; paste the two filter traces and mark the line where `ExceptionTranslationFilter` fires.
- [ ] Implement a custom `OncePerRequestFilter` that reads an `X-Tenant-Id` header and sets a `TenantContext`; prove it runs **before** `AuthorizationFilter` by logging the filter name.
- [ ] Break the chain on purpose (put a `permitAll()` rule *after* `anyRequest()`), observe the "first match wins" behaviour, and fix it.
- [ ] Write a `UserDetailsService` against a JPA repository, then deliberately return a `{noop}`-prefixed hash and observe the exact `IllegalArgumentException` from `DelegatingPasswordEncoder`.
- [ ] Configure a role hierarchy (`ROLE_ADMIN > ROLE_MANAGER > ROLE_USER`) and prove with one `curl` that a `ROLE_MANAGER` token passes a `hasRole("ADMIN")` rule.
- [ ] Add `@PreAuthorize("returnObject.owner == authentication.name")` and reproduce the **method-level** 403 (a body + a stack trace) versus the **URL-level** 403 (an empty body) — the difference in the response proves which layer denied.
- [ ] Write the ~30-item hardening checklist from [ch. 05](./06-Production-Security-Patterns.md) against **your own** service and mark each line pass/fail.

---

**Related folders:** ← [04 · Spring Data JPA & Hibernate](../04-Spring-Data-JPA-Hibernate/README.md) · → [06 · JWT Authentication](../06-JWT-Authentication/README.md) · ↑ [Master Index](../README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
