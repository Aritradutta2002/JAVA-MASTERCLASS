# 01 — Architecture & the Security Filter Chain

> **Level:** Beginner → Expert
> **Interview Frequency:** ★★★★★ ("explain the Spring Security filter chain", "in what order do the filters run?", "why does CORS have to run before authentication?")
> **Prerequisites:** [03 · Spring MVC — DispatcherServlet](../03-Spring-MVC/01-DispatcherServlet-and-Complete-Request-Flow.md) · [04 · JPA — Transactions](../04-Spring-Data-JPA-Hibernate/03-Transactions-Propagation-and-Isolation.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents

- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — The One Picture](#2-mental-model--the-one-picture)
- [3. Deep Dive — Theory (Beginner → Advanced)](#3-deep-dive--theory-beginner--advanced)
  - [3.1 Beginner Level](#31-beginner-level)
  - [3.2 Intermediate Level](#32-intermediate-level)
  - [3.3 Advanced Level](#33-advanced-level)
  - [3.4 Expert Level](#34-expert-level)
- [4. Internal Working Mechanism](#4-internal-working-mechanism)
- [5. Step-by-Step Execution Flow](#5-step-by-step-execution-flow)
- [6. Visualizations](#6-visualizations)
- [7. Production-Level Use Cases & Real Industry Examples](#7-production-level-use-cases--real-industry-examples)
- [8. Worked Examples (with Output + Dry Run)](#8-worked-examples-with-output--dry-run)
- [9. Code from Scratch — Production-Grade](#9-code-from-scratch--production-grade)
- [10. Common Mistakes & Gotchas](#10-common-mistchas--gotchas)
- [11. Performance Considerations](#11-performance-considerations)
- [12. Security Considerations](#12-security-considerations)
- [13. Best Practices](#13-best-practices)
- [14. Hands-On Exercises & Code Challenges](#14-hands-on-exercises--code-challenges)
- [15. Interview Q&A — Level-wise](#15-interview-qa--level-wise)
- [16. Scenario-Based & Real-Experience Scenarios (3–5 yrs)](#16-scenario-based--real-experience-scenarios-35-yrs)
- [17. Counter-Questions, Tricky Questions & Follow-Ups](#17-counter-questions-tricky-questions--follow-ups)
- [18. Frequently Asked Production Issues & Debugging Scenarios](#18-frequently-asked-production-issues--debugging-scenarios)
- [19. Architecture Design Questions](#19-architecture-design-questions)
- [20. Rapid-Fire MCQs](#20-rapid-fire-mcqs)
- [21. Cheat Sheet / One-Page Summary](#21-cheat-sheet--one-page-summary)
- [22. Real-World Production Notes](#22-real-world-production-notes)
- [23. Follow-up Learning](#23-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

- **Spring Security is a servlet filter chain, not an annotation.** Every request is intercepted by `FilterChainProxy` *before* `DispatcherServlet` is reached — which is why security decisions never appear in your controller code.
- **`FilterChainProxy` holds a `List<SecurityFilterChain>`.** On each request it picks the **first** chain whose `matches(request)` returns `true` — first match wins. That is the whole multi-chain routing algorithm.
- **`DelegatingFilterProxy` is the bridge.** The servlet container knows only `jakarta.servlet.Filter`; Spring registers a `Filter` bean named `springSecurityFilterChain` (order **-100**) that delegates to the real `FilterChainProxy`.
- **The filter order is a contract, not an implementation detail.** `CorsFilter` runs **before** authentication (a preflight `OPTIONS` carries no credentials), and `ExceptionTranslationFilter` runs **after** authentication but **before** `AuthorizationFilter` (it needs to know *who* you are to turn a denial into a 401 or a 403).
- **`AuthorizationFilter` is the LAST filter in the chain** — it makes the final access decision. In 6.x it replaced the removed `FilterSecurityInterceptor`.
- **`SecurityContextHolder` is a `ThreadLocal` by default** — it answers "who is the current user *right now*". **`SecurityContextRepository`** answers "where is that context *stored*" — session, request attribute, or nowhere (stateless).
- **Two different `SecurityContext` mechanisms exist in 6.x.** `SecurityContextHolderFilter` (new) delegates to a `SecurityContextRepository` and loads **lazily**; `SecurityContextPersistenceFilter` (removed in 6.0) loaded eagerly and wrote back at the end of the request.
- **`@EnableWebSecurity` + a `SecurityFilterChain` bean is the 6.x model.** `WebSecurityConfigurerAdapter` was removed in 6.0, `HttpSecurity.and()` was removed, and `authorizeRequests` was replaced by `authorizeHttpRequests`.
- **Multiple chains are matched by `securityMatcher`/`requestMatchers` and ordered with `@Order`.** A stateless `/api/**` chain and a session-based `/ui/**` chain coexist happily — but `csrf().disable()` must be scoped to **one** chain, never applied globally.
- **A `403` from the filter and a `403` from `@PreAuthorize` are different events.** URL rules run first (filter level); method rules run last (innermost, around the bean method).
- **The default Boot behaviour is "secure everything with a generated password".** If you never define a `SecurityFilterChain`, Boot logs `Using generated security password: <uuid>` and every endpoint requires that password.

---

## 1. What & Why — The Problem This Solves

**The problem.** A web application has at least four different kinds of "you may not do that":

1. **Anonymous** — you are not logged in at all.
2. **Wrong user** — you are logged in, but this is not your data.
3. **Wrong role** — you are logged in, but your role does not grant this operation.
4. **Cross-site forgery** — you *are* logged in, and that is exactly the problem: somebody else's page is using your ambient session cookie.

These four checks have to happen **before** your business logic runs, they must be **uniform** (not "remember to add the check in every controller"), and they must produce **correct HTTP semantics** so that a client can react (401 → send credentials; 403 → give up).

If you implement that with `@PreAuthorize` annotations sprinkled over controllers, you get three classic failures:

```text
   THE THREE FAILURES OF "SECURITY IN THE CONTROLLER"
   ─────────────────────────────────────────────────────
   1. FORGOTTEN CHECKS
      New endpoint added on a Friday. No annotation. It is public.
      The security review catches it 3 weeks later — if at all.

   2. WRONG ERROR SEMANTICS
      Controller throws AccessDeniedException → 500 (or a stack trace),
      because nothing translated it into an HTTP response.
      The mobile client retries forever.

   3. INCONSISTENT BEHAVIOUR
      /api/orders        → requires a role
      /api/v2/orders     → requires nothing (new controller, new team)
      The client cannot build a single rule.
```

**The solution** is to put the checks in a **filter chain** that every request must walk through. Because the chain runs *before* the dispatcher, the rules are **unbypassable** — a developer cannot forget them, and a framework-level default protects endpoints that do not even exist yet.

**The analogy — the airport terminal.** Your request is a traveller. The filter chain is the terminal, and the terminal works in a **fixed order**:

```text
   T1  Ticket issued?          (SecurityContextHolderFilter / BearerTokenAuthenticationFilter)
   T2  Is the bag open?        (CsrfFilter — is this really you, from this site?)
   T3  Are you from a friendly country?  (CorsFilter — may the browser even read this?)
   T4  Do you have a boarding pass?       (Authentication — 401 if not)
   T5  Is your pass for THIS gate?        (Authorization — 403 if not)
   T6  Boarding.                            (DispatcherServlet → your code)

   The order is not negotiable: you cannot check the boarding pass before
   checking the ticket, and you cannot check "may this browser read the
   response" after you have already thrown the traveller out of the building.
```

**Why "before authentication" for CORS?** A CORS **preflight** is an `OPTIONS` request issued by the browser *without* the credentials of the actual request. If `UsernamePasswordAuthenticationFilter` ran first, the preflight would be rejected with a 401 and the real request would never be sent. So `CorsFilter` must be positioned **before** the authentication filters — it answers the preflight itself and short-circuits the chain.

**Why "after authentication" for `ExceptionTranslationFilter`?** The filter's job is to translate an exception into a response. But *which* response? A 401 or a 403? That depends on whether you are anonymous. To know that, the authentication filters must already have run. And it must be *before* `AuthorizationFilter`, because `AuthorizationFilter` is the filter that *throws* the `AccessDeniedException` it has to translate.

---

## 2. Mental Model — The One Picture

```text
   THE WHOLE OF SPRING SECURITY, IN ONE PICTURE
   ════════════════════════════════════════════

   ┌───────────────────────────────────────────────────────────────────────────┐
   │  TOMCAT                                                                    │
   │   FilterChainProxy  (the "springSecurityFilterChain" Filter bean)        │
   │   │   holds List<SecurityFilterChain>, e.g.                              │
   │   │     chain #1  @Order(1)  securityMatcher("/api/**")  → STATELESS     │
   │   │     chain #2  @Order(2)  (no matcher = catch-all)   → SESSION UI    │
   │   │   Request arrives → try chain #1.matches(req)?                       │
   │   │        YES → use chain #1's filters                                │
   │   │        NO  → try chain #2.matches(req)? → yes → use chain #2        │
   │   │   Only ONE chain ever runs for a request. FIRST MATCH WINS.         │
   │   ▼  the chosen chain's filters, in the canonical order:                 │
   │   SecurityContextHolderFilter → HeaderWriterFilter → CorsFilter →        │
   │   CsrfFilter → LogoutFilter → BearerTokenAuthenticationFilter →          │
   │   UsernamePasswordAuthenticationFilter → … → AnonymousAuthenticationFilter│
   │   → SessionManagementFilter → ExceptionTranslationFilter →              │
   │   AuthorizationFilter                                                   │
   │   ▼  only if EVERY filter called chain.doFilter(req, res)                │
   └───────────────────────────────────────────────────────────────────────────┘
                       │
                       ▼
   ┌───────────────────────────────────────────────────────────────────────────┐
   │  DispatcherServlet → HandlerMapping → Controller → Service → Repository  │
   │   … and INSIDE the service call, one more interceptor:                    │
   │   AuthorizationManagerBeforeMethodInterceptor  ← @PreAuthorize             │
   └───────────────────────────────────────────────────────────────────────────┘

   WHERE IS THE USER RIGHT NOW?
   ┌───────────────────────────┬───────────────────────────────────────────────┐
   │ SecurityContextHolder     │ A ThreadLocal. WHERE the context lives NOW.   │
   │ (location)               │ Default strategy = ThreadLocalSecurityContext │
   │                           │ HolderStrategy.                               │
   ├───────────────────────────┼───────────────────────────────────────────────┤
   │ SecurityContextRepository │ WHERE the context is PERSISTED.               │
   │ (persistence)             │ session / request attribute / nowhere.        │
   └───────────────────────────┴───────────────────────────────────────────────┘
```

**What to look at:** the chain is a **single path** through a fixed sequence of filters, and the
only branching is at the very top (which chain?) and at the very bottom (the access decision).
Everything in between is linear — which is why the *order* is the contract.

---

## 3. Deep Dive — Theory (Beginner → Advanced)

### 3.1 Beginner Level

#### The module map

| Module | Contains | When you need it |
|---|---|---|
| `spring-security-core` | `Authentication`, `GrantedAuthority`, `SecurityContextHolder`, `AuthenticationManager`, `UserDetailsService`, `PasswordEncoder` | Always — it is the transitive base |
| `spring-security-web` | The filter chain, `FilterChainProxy`, `UsernamePasswordAuthenticationFilter`, `AuthorizationFilter`, `SecurityFilterChain` | Any servlet app (it comes with `spring-boot-starter-security`) |
| `spring-security-config` | `@EnableWebSecurity`, `HttpSecurity`, the lambda DSL, `AuthenticationManagerBuilder` | Always, in practice — it is the DSL layer |
| `spring-security-oauth2-client` | OAuth2/OIDC login (the "Login with Google" flow) | Only for social/federated login |
| `spring-security-oauth2-resource-server` | JWT + opaque-token validation (`BearerTokenAuthenticationFilter`) | Only for stateless token APIs — see [06 · JWT](../06-JWT-Authentication/README.md) |
| `spring-security-oauth2-jose` | JWT encoding/decoding (Nimbus) | Pulled in by the resource server |
| `spring-security-messaging` | Security for STOMP/WebSocket messages | Only for WebSocket apps |
| `spring-security-acl` | ACL domain objects (`AclPermissionEvaluator`) | Rarely — heavy |

#### The absolute minimum configuration

```java
package com.example.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```

#### The four nouns you must know on day one

```java
// 1. Authentication — WHO you are (an immutable value object once authenticated)
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
// auth.getName()        → "aritra"
// auth.getAuthorities() → List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))

// 2. GrantedAuthority — a single permission/role string
GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_ADMIN");

// 3. SecurityContextHolder — WHERE the current Authentication lives (ThreadLocal by default)
SecurityContextHolder.getContext().setAuthentication(auth);

// 4. AuthenticationManager — HOW credentials are verified
Authentication ok = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken("aritra", "secret"));
```

**The `Authentication` interface, in full:**

```java
package org.springframework.security.core;

public interface Authentication extends Principal, Serializable {
    String getName();                       // inherited from Principal
    Object getPrincipal();                 // the "user" object (usually UserDetails)
    Object getCredentials();               // the raw secret (usually null once authenticated)
    Collection<? extends GrantedAuthority> getAuthorities();
    boolean isAuthenticated();
    void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException;
}
```

**`Principal` is just `getName()`.** That is why `request.getUserPrincipal().getName()` returns
the username and why `Authentication.getName()` is the canonical identity accessor.

#### The `Authentication` implementations you will meet

| Class | Meaning | `isAuthenticated()` |
|---|---|---|
| `UsernamePasswordAuthenticationToken` | Username + password credentials (or the result of a successful authentication) | `false` before verification, `true` after |
| `AnonymousAuthenticationToken` | "Nobody in particular" — the placeholder the `AnonymousAuthenticationFilter` installs | `true` (deliberately!) |
| `BearerTokenAuthenticationToken` | A validated OAuth2/JWT bearer token | `true` |
| `RememberMeAuthenticationToken` | A cookie-based "remember me" identity | `true` |
| `TestingAuthenticationToken` | Used by `@WithMockUser` and Spring Security Test | `true` |
| `PreAuthenticatedAuthenticationToken` | Set by an upstream proxy that already authenticated the caller | `true` |

> ⚠️ **The `AnonymousAuthenticationToken.isAuthenticated() == true` trap.** A naive check
> `SecurityContextHolder.getContext().getAuthentication().isAuthenticated()` returns **true** for
> an anonymous request! The correct check is:
>
> ```java
> Authentication auth = SecurityContextHolder.getContext().getAuthentication();
> boolean isAnon = auth instanceof AnonymousAuthenticationToken;
> boolean isUser = auth != null && auth.isAuthenticated() && !isAnon;
> ```
>
> Spring gives you exactly this: `new AuthenticationTrustResolver().isAnonymous(auth)`.

#### `permitAll` / `authenticated` / `hasRole` / `denyAll`

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health").permitAll()      // no authentication needed
    .requestMatchers("/api/admin/**").hasRole("ADMIN")      // needs authority ROLE_ADMIN
    .requestMatchers("/api/orders/**").hasAuthority("SCOPE_order:read")  // exact authority
    .anyRequest().authenticated()                          // needs *any* authenticated user
);
```

**The `ROLE_` prefix rule.** `hasRole("ADMIN")` is shorthand for "the authority `ROLE_ADMIN`".
`hasAuthority("ROLE_ADMIN")` is the same thing written out. The trap: `hasRole("ROLE_ADMIN")`
looks for `ROLE_ROLE_ADMIN` and matches **nobody**.

### 3.2 Intermediate Level

#### `FilterChainProxy` → `DelegatingFilterProxy` → the filters

```text
   TOMCAT's filter list (by registration order)
   ─────────────────────────────────────────────
   order -100 : DelegatingFilterProxy  (bean name "springSecurityFilterChain")
                   │  delegates to the FilterChainProxy BEAN
                   ▼
   order  0   : your other servlet filters (registered via FilterRegistrationBean)
```

`DelegatingFilterProxy` is a **servlet-API-to-Spring bridge**: Tomcat knows only
`jakarta.servlet.Filter`, but the real object is a Spring bean. The proxy looks the bean up by
name in the `ApplicationContext` and forwards `doFilter` to it.

**Two ways it gets registered:**

| Mechanism | Used by | Detail |
|---|---|---|
| `AbstractSecurityWebApplicationInitializer` | Plain Spring (non-Boot) | Registers a `DelegatingFilterProxy` for the bean named `springSecurityFilterChain` |
| `SecurityFilterAutoConfiguration` | **Spring Boot** | Registers a `DelegatingFilterProxyRegistrationBean` named `springSecurityFilterChain`, with order `SecurityProperties.DEFAULT_FILTER_ORDER = -100` |

**Why -100?** Because the security filter must run **before** any application filter that might
read the `SecurityContext` (a request-logging filter, a tenant filter, a tracing filter). If your
own filter needs the authenticated user, register it at order `-90` or later.

#### The `SecurityContextHolder` vs the `SecurityContextRepository`

This is the single most confused pair in Spring Security. Separate the two questions:

| Question | Answered by | Type |
|---|---|---|
| "Who is the current user **right now**?" | `SecurityContextHolder` | A `ThreadLocal` (by default) |
| "Where is that context **kept** between requests?" | `SecurityContextRepository` | A strategy object |

```java
// WHERE IT LIVES
public final class SecurityContextHolder {
    private static ThreadLocal<SecurityContext> contextHolder = new ThreadLocal<>();
    public static SecurityContext getContext() { ... }
    public static void setContext(SecurityContext context) { ... }
    public static void clearContext() { ... }
}
```

```java
// WHERE IT IS STORED
public interface SecurityContextRepository {
    SecurityContext loadDeferredContext(HttpServletRequest request);
    void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response);
}
```

**The three implementations you must know:**

| Implementation | Stores the context in | Use for |
|---|---|---|
| `RequestAttributeSecurityContextRepository` | The current HTTP request's attributes | Per-request only — **never persisted** |
| `HttpSessionSecurityContextRepository` | The `HttpSession` | Classic session-based apps (the default) |
| `NullSecurityContextRepository` | **Nowhere** — always returns an empty context | Stateless APIs (JWT) |
| `DelegatingSecurityContextRepository` | A chain: request attribute **first**, session **second** | The 6.x default — writes to the request, falls back to the session |

**The `DelegatingSecurityContextRepository` order matters.** It *saves* to the first repository
that "claims" the context, and *loads* from the first that has one. The default pairing is
`(RequestAttributeSecurityContextRepository, HttpSessionSecurityContextRepository)`, so a
request-scoped context wins over a stale session one.

#### The `SecurityContextHolderFilter` vs `SecurityContextPersistenceFilter`

| | `SecurityContextPersistenceFilter` (≤5.x) | `SecurityContextHolderFilter` (6.x) |
|---|---|---|
| Loads the context | **Eagerly**, at the start of the chain | **Lazily**, on the first `getContext()` call (`loadDeferredContext`) |
| Persists the context | Writes back to the repository at the **end** of the request | Writes back explicitly, only when the context was **actually modified** |
| Repository | Fixed `HttpSessionSecurityContextRepository` | Configurable `SecurityContextRepository` (defaults to `DelegatingSecurityContextRepository`) |
| Cost for a stateless API | A session lookup on **every** request | **Zero** — nothing loads unless someone asks |
| Status in 6.x | **Removed** | Current |

**Why laziness matters.** In a stateless JWT API, if the context were loaded eagerly on every
request, Spring would hit the session store (or create a session) for every single API call — a
large, silent performance tax and a source of `JSESSIONID` cookies nobody asked for. The 6.x
design means a stateless chain configured with `NullSecurityContextRepository` performs **no**
context I/O at all.

#### The `AuthenticationManager` family

```java
public interface AuthenticationManager {
    Authentication authenticate(Authentication authentication) throws AuthenticationException;
}
```

| Implementation | Role |
|---|---|
| `ProviderManager` | Holds a `List<AuthenticationProvider>`; delegates to the ones that `supports()` the token |
| `DaoAuthenticationProvider` | The default: loads a `UserDetails` and checks the password |
| `AnonymousAuthenticationProvider` | Creates the `AnonymousAuthenticationToken` |
| `RememberMeAuthenticationProvider` | Validates the remember-me cookie |
| `PreAuthenticatedAuthenticationProvider` | Trusts an upstream proxy's authentication |
| `OpaqueTokenAuthenticationProvider` | Validates an opaque (reference) token via an introspection endpoint |
| `JwtAuthenticationProvider` | Validates a JWT (used by the resource server) |

**`ProviderManager` in detail:**

```java
public class ProviderManager implements AuthenticationManager {
    private final List<AuthenticationProvider> providers;

    public Authentication authenticate(Authentication authentication, HttpServletRequest request) {
        AuthenticationException lastException = null;
        for (AuthenticationProvider provider : getProviders()) {
            if (!provider.supports(authentication.getClass())) {
                continue;                                   // ← the router
            }
            try {
                Authentication result = provider.authenticate(authentication);
                if (result != null) {
                    if (result.getClass() == authentication.getClass()) {
                        provider.eraseCredentials(authentication);   // scrub the raw secret
                    }
                    return result;                          // ← first non-null wins
                }
            } catch (InternalAuthenticationServiceException e) {
                throw e;                                    // fatal — do not try the next provider
            } catch (AuthenticationException e) {
                lastException = e;                          // remember, try the next provider
            }
        }
        if (lastException == null) {
            lastException = new ProviderNotFoundException(getProviderIds());
        }
        throw lastException;
    }
}
```

**Three things an interviewer wants you to say about this code:**

1. **`supports()` is the router.** A provider that does not support the token type is skipped
   entirely.
2. **The first non-null result wins.** `null` means "I cannot handle this, try the next one".
3. **Exceptions are *collected*, not thrown immediately** — a provider that throws
   `AuthenticationException` is remembered as `lastException` and the loop continues, so a
   second provider may still succeed. Only `InternalAuthenticationServiceException` aborts
   immediately.

> ▶ **The credential-erasure line is a real security feature.** After a successful
> authentication, `eraseCredentials` calls `eraseCredentials()` on the token, which nulls the
> raw password. Otherwise the plaintext password would sit in the `SecurityContext` for the
> whole request and could be logged by a serialising filter.

### 3.3 Advanced Level

#### The `AnonymousAuthenticationFilter` — why it must exist

Without it, an unauthenticated request would leave `SecurityContextHolder` **empty**, and every
piece of code that calls `SecurityContextHolder.getContext().getAuthentication().getName()`
would NPE. `AnonymousAuthenticationFilter` installs an `AnonymousAuthenticationToken`:

```java
public final class AnonymousAuthenticationToken extends UsernamePasswordAuthenticationToken {
    public static final String ANONYMOUS_USER = "anonymousUser";
    private static final List<GrantedAuthority> ROLE_ANONYMOUS =
            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"));
    // principal = "anonymousUser", credentials = null, authorities = [ROLE_ANONYMOUS]
}
```

Its `isAuthenticated()` is **true** — deliberately, so that `authenticated()` in the URL rules
means "some *real* identity is present", enforced by the `AuthenticationTrustResolver`:

```java
public class AuthenticationTrustResolverImpl implements AuthenticationTrustResolver {
    public boolean isAnonymous(Authentication authentication) {
        if ((authentication == null) || (authentication instanceof AnonymousAuthenticationToken)) {
            return true;
        }
        List<GrantedAuthority> authorities = authentication.getAuthorities();
        for (GrantedAuthority authority : authorities) {
            if ("ROLE_ANONYMOUS".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
```

**Note the second check:** a *real* authentication that also carries `ROLE_ANONYMOUS` is treated
as anonymous. That is the `AuthenticationTrustResolver`, and it is exactly what
`ExceptionTranslationFilter` uses to pick 401 vs 403.

#### The `ExceptionTranslationFilter` — the 401/403 decision

```java
public class ExceptionTranslationFilter extends GenericFilterBean {
    @Override
    public void doFilter(req, res, chain) {
        try {
            chain.doFilter(req, res);
        } catch (AccessDeniedException ex) {
            if (this.authenticationTrustResolver.isAnonymous(getAuthentication())) {
                this.authenticationEntryPoint.commence(req, res, ex);   // → 401
            } else {
                this.accessDeniedHandler.handle(req, res, ex);           // → 403
            }
        } catch (AuthenticationException ex) {
            this.authenticationEntryPoint.commence(req, res, ex);       // → 401
        }
    }
}
```

**The decision, in one table:**

| Situation | `Authentication` | Handler | Status |
|---|---|---|---|
| No credentials at all, protected resource | `AnonymousAuthenticationToken` | `AuthenticationEntryPoint` | **401** |
| Bad password | `AuthenticationException` from the provider | `AuthenticationEntryPoint` | **401** |
| Valid user, missing authority | a real `Authentication` | `AccessDeniedHandler` | **403** |
| Valid user, missing method-level authority | a real `Authentication` | `AccessDeniedHandler` (via the exception resolver) | **403** |

The canonical status semantics (from the folder's pinned table):

| Code | Use for |
|---|---|
| 200 | OK, successful read/update |
| 201 | Created — include `Location` header |
| 202 | Accepted — async processing started |
| 204 | No content — successful delete |
| 400 | Malformed request / validation failure |
| 401 | **Not authenticated** (no/invalid credentials) |
| 403 | **Authenticated but not authorized** |
| 404 | Resource not found |
| 409 | Conflict (duplicate, version mismatch) |
| 422 | Semantically invalid (well-formed but wrong) |
| 429 | Rate limited — include `Retry-After` |
| 500 | Unhandled server error |
| 503 | Temporarily unavailable (circuit open, overloaded) |

> ▶ **The line to say out loud in an interview:** *"401 means 'I don't know who you are — send
> credentials'. 403 means 'I know exactly who you are, and the answer is still no'."* That
> sentence, plus the anonymous-token reason, is worth more than listing the filter names.

#### `AuthorizationFilter` and the `AuthorizationManager` SPI

```java
public class AuthorizationFilter extends OncePerRequestFilter {
    private final AuthorizationManager<HttpServletRequest> authorizationManager;

    @Override
    protected void doFilterInternal(req, res, chain) {
        AuthorizationDecision decision = this.authorizationManager.check(this::getAuthentication, req);
        this.eventPublisher.publishAuthorizationEvent(this, req, decision.getResult());
        if (decision.isGranted()) {
            chain.doFilter(req, res);
        } else {
            throw new AccessDeniedException("Access Denied");
        }
    }
}
```

The default `AuthorizationManager` for `authorizeHttpRequests` is a
`RequestMatcherDelegatingAuthorizationManager`: it holds a `Map<RequestMatcher, AuthorizationManager<?>>`
and picks the **first matcher that matches the request**; the matching manager decides.

**This is the second "first match wins" in Spring Security** — the first is the *chain* routing,
this is the *rule* routing inside the chain.

#### `RequestMatcher` types

| Matcher | Matches on | Notes |
|---|---|---|
| `PathPatternRequestMatcher` | A servlet path pattern | **The modern choice.** `PathPatternRequestMatcher.withDefaults().matcher("/admin/**")` |
| `AntPathRequestMatcher` | An Ant-style path | **Deprecated in Spring Security 6.5** — use `PathPatternRequestMatcher`. Present but not preferred in 6.3 |
| `MvcRequestMatcher` | Path + method + params via `HandlerMappingIntrospector` | **Deprecated in 6.5** |
| `RegexRequestMatcher` | A regex against the servlet path | Rare |
| `MediaTypeRequestMatcher` | The `Content-Type` / `Accept` header | Used for API-vs-browser content negotiation |
| `HeaderRequestMatcher` | A header's value | Used for API-key headers |
| `IpAddressRequestMatcher` | The client IP | Used for IP allowlists |
| `AndRequestMatcher` / `OrRequestMatcher` / `NegatedRequestMatcher` | Composition | The combinators |

> ⚠️ **Version callout.** `PathPatternRequestMatcher` was introduced in **Spring Security 6.5**
> (Spring Boot 3.5). Boot 3.3 ships Spring Security 6.3, where `AntPathRequestMatcher` is the
> common idiom and `PathPatternRequestMatcher` does not yet exist. In 6.5 `AntPathRequestMatcher`
> and `MvcRequestMatcher` are **deprecated**. So: on Boot 3.3 use `AntPathRequestMatcher` (or
> the plain string overload of `requestMatchers`, which is the recommended path either way); on
> Boot 3.5+ prefer `PathPatternRequestMatcher`.

**The `requestMatchers(...)` overloads on the DSL:**

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/**").authenticated()                        // path only
    .requestMatchers(HttpMethod.GET, "/api/orders/**").authenticated() // method + path
    .requestMatchers(HttpMethod.POST, "/api/orders").hasRole("ADMIN")  // method + path + role
    .requestMatchers(DispatcherType.ERROR, "/error").permitAll()       // dispatcher type
    .requestMatchers(new AntPathRequestMatcher("/internal/**")).permitAll()
    .anyRequest().authenticated());
```

#### `authorizeHttpRequests` vs the deprecated `authorizeRequests`

```java
// ✅ Current (6.x)
http.authorizeHttpRequests(auth -> auth.requestMatchers("/x").permitAll());

// ❌ Deprecated since 5.8, REMOVED in 6.0
http.authorizeRequests(auth -> auth.antMatchers("/x").permitAll());
```

**What actually changed (and why it matters):**

| Aspect | `authorizeRequests` (old) | `authorizeHttpRequests` (6.x) |
|---|---|---|
| Underlying model | A `FilterSecurityInterceptor` + an `AccessDecisionManager` chain (voters) | A single `AuthorizationManager` per rule |
| Extension point | `AccessDecisionManager` + `RoleVoter` | `AuthorizationManager<RequestAuthorizationContext>` |
| Rules | `antMatchers(...)`, `regexMatchers(...)`, `mvcMatchers(...)` | `requestMatchers(...)` only |
| `hasRole` semantics | `RoleVoter` with `ROLE_` prefix | Built into the authorization manager — **same prefix rule** |

**The migration pain point:** custom `AccessDecisionManager`s and custom `AccessDecisionVoter`s
from 5.x have **no 1:1 replacement**. You rewrite them as an `AuthorizationManager`.

#### Multiple chains and `@Order`

```java
@Bean
@Order(1)
SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .httpBasic(withDefaults());
    return http.build();
}

@Bean
@Order(2)
SecurityFilterChain uiChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/login").permitAll()
            .anyRequest().authenticated())
        .formLogin(withDefaults());
    return http.build();
}
```

**The routing rules:**

1. The chains are sorted by `@Order` (lowest first).
2. For each request, the **first** chain whose `matches(request)` returns `true` is selected.
3. `securityMatcher("/api/**")` builds a path matcher; `securityMatcher("/api/**", "/admin/**")`
   accepts several.
4. A chain with **no** `securityMatcher` matches everything — so it must be the **last** one
   (highest `@Order` number).
5. **Only one chain runs.** The others are ignored for that request — including their CSRF
   setting, their session policy, and their authentication mechanisms.

> ▶ **The interview sentence:** *"A `SecurityFilterChain` is a security policy for a URL
> namespace. Order matters because the router is a first-match-wins list — `securityMatcher`
> is not a filter, it is a routing predicate."*

#### `SecurityContextHolderStrategy` and the inheritability trap

```java
public final class SecurityContextHolder {
    private static ThreadLocal<SecurityContext> contextHolder = new ThreadLocal<>();
}
```

The strategy is pluggable via `SecurityContextHolder.setStrategy(...)`:

| Strategy | Behaviour | Use when |
|---|---|---|
| `ThreadLocalSecurityContextHolderStrategy` (default) | One context per thread | **Always**, on a servlet container |
| `InheritableThreadLocalSecurityContextHolderStrategy` | The child thread inherits the parent's context | **Never on a thread pool** |
| `DelegatingSecurityContextHolderStrategy` | Delegates to another strategy | Rare, custom |

**Why `MODE_INHERITABLETHREADLOCAL` is unsafe in production:** with an inheritable
`ThreadLocal`, a thread created *inside* a request inherits the authenticated context. With a
**thread pool** (`@Async`, `CompletableFuture`, Tomcat's worker threads being reused), the
context from request A can leak into request B — **request B executes as request A's user**.
That is a textbook privilege-escalation / confused-deputy bug. The fix is to *explicitly*
propagate the context:

```java
SecurityContext context = SecurityContextHolder.getContext();
CompletableFuture.supplyAsync(() -> {
    SecurityContextHolder.setContext(context);   // explicit, not inherited
    try {
        return doWork();
    } finally {
        SecurityContextHolder.clearContext();     // always clean up
    }
});
```

#### Where the `SecurityContext` is *cleared*

`SecurityContextHolderFilter` clears the context at the end of the request. If you ever see a
stale user on a thread pool, the cause is almost always **a thread you submitted work to
without clearing the context yourself** — the filter only cleans up the *container's* thread.

### 3.4 Expert Level

#### The canonical filter order and the contract behind it

| # | Filter | Purpose |
|---|---|---|
| 1 | `DisableEncodeUrlFilter` | (removed in 6.x) |
| 2 | `WebAsyncManagerIntegrationFilter` | WebAsync integration |
| 3 | `SecurityContextHolderFilter` | Loads `SecurityContext` (was `SecurityContextPersistenceFilter`) |
| 4 | `HeaderWriterFilter` | Security headers (X-Content-Type-Options, HSTS, …) |
| 5 | `CorsFilter` | CORS — **must come before authentication** |
| 6 | `CsrfFilter` | CSRF protection |
| 7 | `LogoutFilter` | Logout handling |
| 8 | `BearerTokenAuthenticationFilter` | OAuth2 / JWT resource server |
| 9 | `UsernamePasswordAuthenticationFilter` | Form login |
| 10 | `BasicAuthenticationFilter` | HTTP Basic |
| 11 | `RequestCacheAwareFilter` | Request cache |
| 12 | `SecurityContextHolderAwareRequestFilter` | Servlet API bridge |
| 13 | `AnonymousAuthenticationFilter` | Anonymous auth token |
| 14 | `SessionManagementFilter` | Session control |
| 15 | `ExceptionTranslationFilter` | Turns exceptions into 401/403 |
| 16 | `AuthorizationFilter` | **Final authorization decision** (was `FilterSecurityInterceptor`) |

> ▶ The two facts interviewers want: `CorsFilter` runs **before** authentication, and
> `ExceptionTranslationFilter` is what converts an `AccessDeniedException` into a 403
> (and an unauthenticated call into a 401).

**What each filter OWNS and what breaks if it is moved** is laid out as a table in
[§6.3](#63-the-canonical-filter-order-and-what-breaks-if-you-move-it). The four constraints
that are asked in almost every interview:

| Filter | OWNS | Breaks if moved |
|---|---|---|
| `SecurityContextHolderFilter` | Loads/clears the `SecurityContext` | Runs after authentication → the loaded context **overwrites** the fresh one → every call is 401 |
| `CorsFilter` | CORS preflight + `Access-Control-*` headers | Runs after authentication → the **preflight gets a 401** → the browser reports a CORS error and the real request is never sent |
| `ExceptionTranslationFilter` | `AccessDeniedException` → 403, `AuthenticationException` → 401 | Runs after `AuthorizationFilter` → the denial escapes → a **500 with a stack trace** instead of a 403 |
| `AuthorizationFilter` | The final access decision | Not last → later filters act on requests that were never authorized |

**The `OrderedFilter` registry** assigns each built-in filter an internal order int —
`REACTOR_CONTEXT` (-3200) → `HTTP_BASIC` (-3100) → `FORM_LOGIN` (-2900) → `LOGOUT` (-2700) →
`SECURITY_CONTEXT` (-1800) → `SECURITY_CONTEXT_AWARE` (-1700) → `ANONYMOUS` (-1600) →
`SESSION_MANAGEMENT` (-1500) → `EXCEPTION_TRANSLATION` (-1400) → `AUTHORIZATION` (-1300).

> ⚠️ These values are **internal** and change between releases. Never hard-code an order
> number in application code — always use `addFilterBefore/After(..., SomeKnownFilter.class)`.
> If you *must* control the absolute order of your own filter, use `FilterRegistrationBean`
> with an explicit `setOrder(...)` — but that is the **servlet** order, not the security order.

#### `addFilterBefore` / `addFilterAfter` / `addFilterAt`

```java
http.addFilterBefore(new TenantContextFilter(), SecurityContextHolderFilter.class);
```

**What the three methods actually do:** they insert your filter **relative to a known Spring
filter class** in the chain. The insertion is resolved by comparing the **registered order**
values (`OrderedFilter` constants).

> ⚠️ The anchor class expresses a **dependency**, not a preference. Before
> `SecurityContextHolderFilter` means "before the context is loaded" — so the context is still
> empty in your filter. If you need the identity, anchor **after** the authentication filter.

#### `OncePerRequestFilter` vs a plain `Filter`

```java
public abstract class OncePerRequestFilter extends GenericFilterBean {
    // Guarantees doFilter() runs ONCE per request, even if the filter is registered
    // in the chain more than once (e.g. an ERROR dispatch).
}
```

**Why it matters for security filters:** a `Filter` that runs twice can authenticate twice, log
twice, or — worse — apply a security decision twice with different state. The `shouldNotFilter`
hook is the standard way to skip a filter for a subset of requests:

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return path.startsWith("/actuator/health") || path.startsWith("/static/");
}
```

#### `ObjectPostProcessor` — the one extension point nobody knows

Every filter Spring Security creates is passed through an `ObjectPostProcessor`:

```java
public interface ObjectPostProcessor<T> {
    <O extends T> O postProcess(O object);
}
```

**You can use it to inject dependencies into Spring Security's own filters** — the filter
instance is created by Spring Security, not by you, so you cannot `@Autowired` it:

```java
@Bean
ObjectPostProcessor<UsernamePasswordAuthenticationFilter> filterPostProcessor(
        AuditService auditService) {
    return new ObjectPostProcessor<>() {
        @Override
        public <O extends UsernamePasswordAuthenticationFilter> O postProcess(O filter) {
            filter.setAuthenticationSuccessHandler((req, res, auth) -> {
                auditService.recordLogin(auth.getName(), req.getRemoteAddr());
                new SavedRequestAwareAuthenticationSuccessHandler().onAuthenticationSuccess(req, res, auth);
            });
            return filter;
        }
    };
}
```

> ▶ **Interview nugget:** *"Spring Security's filters are constructed by Spring Security, not by
> the container — so `@Autowired` does not work on them. The `ObjectPostProcessor` is the
> sanctioned hook for injecting collaborators into the framework's own filters."*

#### Servlet dispatch types and the `DispatcherType` matcher

A servlet request can be dispatched multiple times: `REQUEST`, `FORWARD`, `INCLUDE`, `ERROR`,
`ASYNC`. Filters run for **every** dispatch unless they are explicitly scoped:

```java
@Bean
FilterRegistrationBean<MyFilter> myFilter() {
    FilterRegistrationBean<MyFilter> reg = new FilterRegistrationBean<>(new MyFilter());
    reg.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
    reg.addUrlPatterns("/api/*");
    reg.setOrder(-90);   // after the security chain (-100)
    return reg;
}
```

**The classic bug:** a filter registered for all dispatcher types runs again on the `ERROR`
dispatch, sees a null `Authentication`, and logs a spurious "unauthenticated access" for every
500. Always scope `DispatcherType`.

---

## 4. Internal Working Mechanism

**The exact call chain for one HTTP request.** Names are the real Spring Security 6.x classes.

1. **Tomcat** accepts the socket, creates the request/response objects, and builds the **servlet
   filter list** in registration order.
2. **`DelegatingFilterProxy`** (registered under the name `springSecurityFilterChain`, order
   `-100`) receives `doFilter(request, response, chain)`.
3. The proxy looks up the bean named `springSecurityFilterChain` in the `ApplicationContext` —
   that bean is a **`FilterChainProxy`** — and calls `doFilter` on it.
4. **`FilterChainProxy.doFilter`** calls `getFilterChain(request)`, which iterates
   `this.filterChains` in `@Order` sequence and returns the **first** chain whose
   `matches(request)` is `true`. **No match → the request is rejected.**
5. `FilterChainProxy` builds a **`VirtualFilterChain`** over the selected chain's
   `List<SecurityFilter>` and starts walking it.
6. **`SecurityContextHolderFilter`** asks its `SecurityContextRepository` for a **deferred**
   context (`loadDeferredContext`). Nothing is loaded yet — the supplier is lazy.
7. **`HeaderWriterFilter`** registers the `HeaderWriter`s; the headers are actually written on
   the **response commit**, so they are present even on an error response.
8. **`CorsFilter`** — if the request is a preflight, it writes the `Access-Control-*` headers
   and **does not call `chain.doFilter`** → the chain stops here.
9. **`CsrfFilter`** loads the token from the `CsrfTokenRepository` and compares it with the
   submitted token. A mismatch throws `InvalidCsrfTokenException` (an `AccessDeniedException`).
10. **`BearerTokenAuthenticationFilter`** (or **`UsernamePasswordAuthenticationFilter`**) — if
    credentials are present, it builds an **unauthenticated** token and calls
    `AuthenticationManager.authenticate(...)`.
11. **`ProviderManager`** iterates its providers, calling `supports()` then `authenticate()`.
    `DaoAuthenticationProvider` calls the **`UserDetailsService`**, then
    `PasswordEncoder.matches(...)`, then the user-state checks, and returns a **fully
    authenticated** `UsernamePasswordAuthenticationToken`.
12. On success the token is put into the `SecurityContext`, and the
    `SecurityContextRepository.saveContext(...)` writes it to the session (or not, in a
    stateless chain).
13. **`AnonymousAuthenticationFilter`** — if the context is still empty, it installs an
    `AnonymousAuthenticationToken`.
14. **`SessionManagementFilter`** enforces the session creation policy and session fixation
    protection.
15. **`ExceptionTranslationFilter`** wraps the rest of the chain in a `try/catch` for
    `AccessDeniedException` and `AuthenticationException`.
16. **`AuthorizationFilter`** evaluates the `AuthorizationManager` for the request. Denied →
    it throws `AccessDeniedException`, which propagates **up** to step 15, which turns it into a
    401 or a 403.
17. Granted → the chain continues to the **`DispatcherServlet`**, which runs the controller.
18. On the way out, `SecurityContextHolderFilter` **clears** the `ThreadLocal`, and
    `HeaderWriterFilter` writes the security headers onto the committed response.

**The extension points a developer actually implements:**

| Extension point | Interface | For what |
|---|---|---|
| The chain itself | `SecurityFilterChain` | A URL-scoped security policy |
| A custom filter | `jakarta.servlet.Filter` / `OncePerRequestFilter` | Cross-cutting pre/post work (tenant, tracing, audit) |
| Authentication | `AuthenticationProvider` | A non-password scheme (API key, OTP, SAML assertion) |
| User lookup | `UserDetailsService` | Where the user record comes from |
| Password hashing | `PasswordEncoder` | How the secret is verified |
| Access rules | `AuthorizationManager<RequestAuthorizationContext>` | A custom URL rule |
| Method rules | `AuthorizationManager<MethodInvocation>` / `@PreAuthorize` | Fine-grained, object-level rules |
| 401 rendering | `AuthenticationEntryPoint` | The JSON body of an unauthenticated error |
| 403 rendering | `AccessDeniedHandler` | The JSON body of a forbidden error |
| Post-authentication hook | `AuthenticationSuccessHandler` / `AuthenticationFailureHandler` | Issue a JWT, write an audit row |
| Post-login hook | `AuthenticationEventPublisher` | Lockout counters, alerting |
| Header injection into Spring Security's filters | `ObjectPostProcessor` | Wiring collaborators into framework filters |

---

## 5. Step-by-Step Execution Flow

**A `GET /api/orders/42` with no credentials, against a stateless API chain.**

1. **Socket accept** — Tomcat accepts the connection, parses the request line and headers.
2. **Servlet filter list built** — `DelegatingFilterProxy` (order -100) is first.
3. **Chain routing** — `FilterChainProxy` asks chain #1 (`securityMatcher("/api/**")`) →
   `matches()` → **true**. Chain #1's filters are selected; chain #2 is ignored entirely.
4. **`SecurityContextHolderFilter`** → `NullSecurityContextRepository` (stateless) → **nothing
   loaded**, no session touched.
5. **`HeaderWriterFilter`** — arms the header writers.
6. **`CorsFilter`** — the request has no `Origin` header (it is `curl`), so it is not a CORS
   request: it passes straight through.
7. **`CsrfFilter`** — skipped: CSRF is disabled **for this chain only**.
8. **No `Authorization` header** → `BearerTokenAuthenticationFilter` passes; no form post →
   `UsernamePasswordAuthenticationFilter` passes.
9. **`AnonymousAuthenticationFilter`** — the context is empty, so an
   `AnonymousAuthenticationToken` ("anonymousUser", `ROLE_ANONYMOUS`) is installed.
10. **`SessionManagementFilter`** — `SessionCreationPolicy.STATELESS` → **no session is
    created**, no `JSESSIONID` cookie is written.
11. **`ExceptionTranslationFilter`** — arms its catch blocks.
12. **`AuthorizationFilter`** — the rule for `/api/**` is `authenticated()`. The
    `AuthorizationManager` sees the `AnonymousAuthenticationToken` and **denies**.
13. **`AccessDeniedException`** is thrown and propagates **up** to `ExceptionTranslationFilter`.
14. **`ExceptionTranslationFilter`** — `authenticationTrustResolver.isAnonymous(auth)` is
    **true** → it calls `authenticationEntryPoint.commence(...)`.
15. **`BearerTokenAuthenticationEntryPoint`** writes **401** with a
    `WWW-Authenticate: Bearer` header and an empty body.
16. The chain unwinds. `SecurityContextHolderFilter` clears the `ThreadLocal`. The
    `DispatcherServlet` is **never reached** — no controller method runs.

**The same request, but with a valid JWT:** steps 1–7 are identical; step 8 now produces a
**fully authenticated** `BearerTokenAuthenticationToken` with authority `SCOPE_order:read`, so
the `AnonymousAuthenticationFilter` does nothing; step 12 is satisfied and the request reaches
`OrderController.findById(42)`. The response is **200**.

**The same request, with a valid JWT that lacks the required authority:** everything up to step
12 is identical except the `Authentication` is a **real** token, not anonymous. `hasAuthority(
"SCOPE_order:write")` denies → `AccessDeniedException` → `isAnonymous(auth)` is **false** →
`AccessDeniedHandler.handle(...)` → **403**.

---

## 6. Visualizations

### 6.1 The full request through the chain (sequence)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant T as Tomcat
    participant D as DelegatingFilterProxy
    participant P as FilterChainProxy
    participant S as SecurityContextHolderFilter
    participant B as BearerTokenAuthenticationFilter
    participant A as AnonymousAuthenticationFilter
    participant E as ExceptionTranslationFilter
    participant Z as AuthorizationFilter
    participant D2 as DispatcherServlet

    C->>T: GET /api/orders/42 (no credentials)
    T->>D: doFilter (order -100)
    D->>P: doFilter (lookup bean "springSecurityFilterChain")
    P->>P: getFilterChain(req) → first matching chain
    P->>S: doFilter
    S->>S: loadDeferredContext() (lazy, nothing read yet)
    S->>B: doFilter
    B->>B: no Authorization header → pass
    B->>A: doFilter
    A->>A: context empty → install AnonymousAuthenticationToken
    A->>E: doFilter
    E->>Z: doFilter
    Z->>Z: rule = authenticated(); token is anonymous → DENY
    Z--xE: throw AccessDeniedException
    E->>E: trustResolver.isAnonymous(auth) == true
    E-->>C: 401 + WWW-Authenticate: Bearer
    E-->>D2: chain stops — controller never runs
```

**Caption:** The `throw` from `AuthorizationFilter` travels **up** the stack to
`ExceptionTranslationFilter`, which is why that filter must sit *outside* `AuthorizationFilter`
but *inside* the authentication filters.

### 6.2 Which chain handles this request? (flowchart, first match wins)

```mermaid
flowchart TD
    A[Request arrives] --> B[FilterChainProxy.getFilterChain]
    B --> C{chain @Order(1)<br/>securityMatcher '/api/**'<br/>matches?}
    C -- yes --> H[Use chain #1<br/>STATELESS API<br/>csrf disabled]
    C -- no --> D{chain @Order(2)<br/>securityMatcher '/ui/**'<br/>matches?}
    D -- yes --> I[Use chain #2<br/>SESSION UI<br/>csrf enabled]
    D -- no --> E{chain @Order(3)<br/>no matcher = catch-all<br/>matches?}
    E -- yes --> J[Use chain #3<br/>DEFAULT policy]
    E -- no --> K[NO CHAIN MATCHED<br/>request rejected]

    style H fill:#d4edda
    style I fill:#d1ecf1
    style J fill:#fff3cd
    style K fill:#f8d7da
```

**What to look at:** the chain with **no** `securityMatcher` matches everything, so it must be
last (`@Order(3)` here). If you forget the `@Order` values, the ordering is undefined and the
result depends on bean-name ordering — a genuinely non-deterministic bug.

### 6.3 The canonical filter order and what breaks if you move it

```text
┌────┬──────────────────────────────────┬────────────────────────────────────────────┬──────────────────────────────────────────────┐
│ #  │ FILTER                           │ WHAT IT OWNS                               │ WHAT BREAKS IF IT IS MOVED                   │
├────┼──────────────────────────────────┼────────────────────────────────────────────┼──────────────────────────────────────────────┤
│  1 │ DisableEncodeUrlFilter          │ (removed in 6.x)                           │ n/a                                          │
│  2 │ WebAsyncManagerIntegrationFilter│ Propagates SecurityContext into async work │ Async thread has NO authentication          │
│  3 │ SecurityContextHolderFilter     │ Loads/clears the SecurityContext           │ Runs after auth → the loaded context         │
│    │                                  │                                            │ OVERWRITES the new one → every call is 401   │
│  4 │ HeaderWriterFilter              │ Security response headers                  │ Runs last → error responses have no         │
│    │                                  │                                            │ nosniff / HSTS                               │
│  5 │ CorsFilter                      │ CORS preflight + Access-Control headers     │ Runs after auth → PREFLIGHT GETS 401 →      │
│    │                                  │                                            │ browser shows "CORS error", real call never  │
│    │                                  │                                            │ happens                                       │
│  6 │ CsrfFilter                      │ CSRF token load + compare                  │ Runs after context clear → token never      │
│    │                                  │                                            │ compared → all writes 403                   │
│  7 │ LogoutFilter                    │ POST /logout, session invalidation          │ Runs after context clear → cannot sign out  │
│  8 │ BearerTokenAuthenticationFilter │ Validates JWT / opaque token                │ Runs after AuthorizationFilter → token never│
│    │                                  │                                            │ read → all JWT requests 401                 │
│  9 │ UsernamePasswordAuthenticationFilter│ Form login POST                         │ Runs after AuthorizationFilter → login POST│
│    │                                  │                                            │ rejected before it can authenticate         │
│ 10 │ BasicAuthenticationFilter       │ Authorization: Basic header                │ Basic auth never attempted                  │
│ 11 │ RequestCacheAwareFilter         │ Saves the request for replay after login   │ After login the user is dumped on "/"        │
│ 12 │ SecurityContextHolderAwareRequestFilter │ Servlet API bridge (getUserPrincipal) │ getUserPrincipal() returns null             │
│ 13 │ AnonymousAuthenticationFilter   │ Installs AnonymousAuthenticationToken      │ Empty SecurityContext → NPE everywhere      │
│ 14 │ SessionManagementFilter         │ SessionCreationPolicy, fixation, maxSessions│ Stateless API starts issuing JSESSIONID and  │
│    │                                  │                                            │ holding server-side sessions                │
│ 15 │ ExceptionTranslationFilter      │ AccessDeniedException → 403,                │ Runs after AuthorizationFilter → denial    │
│    │                                  │ AuthenticationException → 401               │ escapes → 500 + stack trace                 │
│ 16 │ AuthorizationFilter             │ THE FINAL ACCESS DECISION                  │ Not last → later filters act on unauthorised│
│    │                                  │                                            │ requests                                     │
└────┴──────────────────────────────────┴────────────────────────────────────────────┴──────────────────────────────────────────────┘
```

**Caption:** the order is not alphabetical, not historical — it is a set of **dependencies**.
Each row's "what breaks" column is the interview answer.

### 6.4 The class relationships (graph)

```mermaid
graph TD
    subgraph Servlet["Servlet container layer"]
        FR[FilterRegistrationBean<br/>order -100]
        DFP[DelegatingFilterProxy<br/>'springSecurityFilterChain']
        FR --> DFP
    end

    subgraph SpringSecurity["Spring Security layer"]
        FCP[FilterChainProxy<br/>holds List&lt;SecurityFilterChain&gt;]
        SFC[SecurityFilterChain<br/>matches() + getFilters()]
        SF[SecurityFilter<br/>extends jakarta.servlet.Filter]
        OPF[OncePerRequestFilter]
        VFC[FilterChainProxy.VirtualFilterChain]

        FCP -->|selects the first match| SFC
        SFC -->|getFilters| SF
        SF -->|subclass| OPF
        FCP -->|builds| VFC
        VFC -->|invokes in order| SF
    end

    subgraph Context["Identity layer"]
        SCH[SecurityContextHolder<br/>ThreadLocal]
        SCR[SecurityContextRepository<br/>session / request / null]
        SC[SecurityContext]
        AUTH[Authentication]
        AG[GrantedAuthority]

        SCR -->|loadDeferredContext| SC
        SCH --> SC
        SC --> AUTH
        AUTH --> AG
    end

    DFP -->|delegates doFilter| FCP
    SCR -.->|implemented by| SCH

    style FCP fill:#ffeeba
    style SCR fill:#d1ecf1
```

**Caption:** `FilterChainProxy` is the only class the servlet container knows about
(`DelegatingFilterProxy`); everything else is ordinary Spring beans that Spring Security
composes into it.

---

## 7. Production-Level Use Cases & Real Industry Examples

### 7.1 E-commerce platform — two chains, one WAR

**Domain:** e-commerce. A storefront SPA on `shop.example.com`, a partner integration API on
`api.example.com`, both in the **same** Spring Boot application, deployed as 12 pods behind an
ALB.

**Scale:** 40 000 orders/minute at peak (Black Friday), 3 200 requests/second on the API chain,
p99 latency budget **180 ms**.

```java
@Bean
@Order(1)
SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher("/api/**")
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/api/orders").hasAuthority("SCOPE_order:write")
            .requestMatchers("/api/orders/**").hasAuthority("SCOPE_order:read")
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())          // ← scoped to THIS chain only
        .headers(h -> h.frameOptions(FrameOptionsConfig::deny));
    return http.build();
}

@Bean
@Order(2)
SecurityFilterChain storefrontChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/", "/assets/**", "/favicon.ico").permitAll()
            .anyRequest().authenticated())
        .oauth2Login(withDefaults())            // the SPA logs in via OIDC
        .sessionManagement(s -> s.sessionFixation(sf -> sf.migrateSession()));
    return http.build();
}
```

**Why two chains rather than one:** the API chain must be **stateless** (no server-side session
memory across 12 pods, no `JSESSIONID` on thousands of calls/second) and must **not** require a
CSRF token (a bearer token is not attached automatically by a browser, so there is no ambient
credential to abuse). The storefront chain is a **cookie session** — the browser attaches the
cookie automatically, so CSRF protection is mandatory. Putting `csrf.disable()` on the whole app
would silently remove protection from the storefront; putting it nowhere would break the API.

**Measured effect:** the API chain's p99 dropped from **240 ms to 165 ms** after moving to
stateless (no session lookup, no `Set-Cookie`, no sticky-session requirement), and the ALB
round-robin could finally be used instead of IP stickiness.

### 7.2 Banking — audit trail and "know your customer" gates

**Domain:** retail banking. A customer-facing API plus an internal back-office application.

**Scale:** 18 000 daily active users, 60 000 transactions/day, **zero** tolerance for an
unauthorised balance read.

```java
@Bean
@Order(1)
SecurityFilterChain customerApiChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/customer/**")
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/customer/health").permitAll()
            .requestMatchers("/api/customer/accounts/**").hasAuthority("SCOPE_account:read")
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .requiresChannel(c -> c.anyRequest().requiresSecure());   // TLS only
    return http.build();
}
```

**The object-level check is the one that matters** — it lives in the service layer (see
[03 · Authorization](03-Authorization-Roles-Permissions-and-Method-Security.md)):

```java
@PreAuthorize("hasAuthority('SCOPE_account:read') and @accountGuard.isOwner(authentication, #accountId)")
public AccountSummary summarize(@AuthenticationPrincipal Jwt jwt, Long accountId) { ... }
```

**The filter chain does the *coarse* gate (is this a valid token, is the scope right). The
method rule does the *fine* gate (is this account yours).** Removing either one leaves a hole:
remove the filter rule and unauthenticated calls hit the DB; remove the method rule and any
authenticated customer can read any account by changing the id — the classic IDOR.

### 7.3 Multi-tenant SaaS — one chain, tenant resolved per request

**Domain:** B2B SaaS (expense management). 400 tenants, 90 000 users.

**Scale:** 2 800 requests/second, 120 tenant-scoped queries/second.

```java
@Bean
@Order(1)
SecurityFilterChain tenantChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .addFilterBefore(new TenantResolutionFilter(), BearerTokenAuthenticationFilter.class);
    return http.build();
}
```

**Why the tenant filter goes *after* `BearerTokenAuthenticationFilter`:** the tenant is derived
from the **validated token's** `tenant_id` claim — never from a client-supplied header. Placing
it before the token filter would force you to trust an `X-Tenant-Id` header, which is
attacker-controlled.

**The rule every SaaS team needs:** *the tenant id is server-derived from the token, and every
repository query is scoped by it.* A `findById(id)` that is not scoped by the tenant is an IDOR
waiting to happen.

---

## 8. Worked Examples (with Output + Dry Run)

### 8.1 A complete stateless REST API chain — proving 401 vs 403

**Problem.** Build the security configuration for a stateless REST API (JWT bearer tokens),
with public endpoints and role-protected endpoints. Prove the difference between "not
authenticated" and "authenticated but not allowed".

**Step 1 — the configuration.**

```java
package com.example.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ApiSecurityConfig {

    @Bean
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```

**Step 2 — the startup log (Boot 3.3, `logging.level.org.springframework.security=INFO`).**

```text
2026-03-04 09:12:41.118  INFO 1 --- [main] o.s.s.web.DefaultSecurityFilterChain : Will secure any request with [AuthorizationFilter, ...]
2026-03-04 09:12:41.201  INFO 1 --- [main] o.s.s.web.DefaultSecurityFilterChain : Will secure any request with [DisableEncodeUrlFilter, WebAsyncManagerIntegrationFilter, SecurityContextHolderFilter, HeaderWriterFilter, CorsFilter, CsrfFilter, LogoutFilter, UsernamePasswordAuthenticationFilter, BasicAuthenticationFilter, RequestCacheAwareFilter, SecurityContextHolderAwareRequestFilter, AnonymousAuthenticationFilter, SessionManagementFilter, ExceptionTranslationFilter, AuthorizationFilter]
2026-03-04 09:12:41.905  INFO 1 --- [main] com.example.api.Application           : Started Application in 3.412 seconds (process running for 3.9)
```

**Step 3 — the three curl calls.**

```bash
# (1) public endpoint, no credentials
curl -i http://localhost:8080/api/public/products

# (2) protected endpoint, no credentials
curl -i http://localhost:8080/api/orders

# (3) protected endpoint, authenticated but WITHOUT the ADMIN role
curl -i -u aritra:secret http://localhost:8080/api/admin/users
```

**Step 4 — the actual responses.**

```text
HTTP/1.1 200
Content-Type: application/json
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Cache-Control: no-cache, no-store, max-age=0, must-revalidate

{"products":[{"id":1,"name":"Widget"}]}
```

```text
HTTP/1.1 401
WWW-Authenticate: Basic realm="Realm"
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Cache-Control: no-cache, no-store, max-age=0, must-revalidate

```

```text
HTTP/1.1 403
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Cache-Control: no-cache, no-store, max-age=0, must-revalidate

```

**Dry run — request (2), step by step:**

| Step | What happens | Where |
|---|---|---|
| 1 | `DelegatingFilterProxy` (order -100) → `FilterChainProxy` | Servlet container |
| 2 | Chain #1 matches `/api/**` → its filters are selected | `FilterChainProxy.getFilterChain` |
| 3 | `SecurityContextHolderFilter` → `NullSecurityContextRepository` (stateless) → **nothing loaded**, no session touched | filter #3 |
| 4 | `HeaderWriterFilter` arms the writers | filter #4 |
| 5 | `CorsFilter` — no `Origin` header → passes through | filter #5 |
| 6 | `CsrfFilter` — **disabled for this chain** → skipped | filter #6 |
| 7 | `BasicAuthenticationFilter` — no `Authorization` header → passes | filter #10 |
| 8 | `AnonymousAuthenticationFilter` → installs `AnonymousAuthenticationToken` | filter #13 |
| 9 | `SessionManagementFilter` — `STATELESS` → **no session, no cookie** | filter #14 |
| 10 | `ExceptionTranslationFilter` arms its catch | filter #15 |
| 11 | `AuthorizationFilter` — rule `anyRequest().authenticated()`; the token is anonymous → **denied** → throws `AccessDeniedException` | filter #16 |
| 12 | The exception propagates **up** to `ExceptionTranslationFilter`, which checks `isAnonymous(auth)` → **true** → `authenticationEntryPoint.commence(...)` | filter #15 |
| 13 | `BasicAuthenticationEntryPoint` writes **401** + `WWW-Authenticate: Basic realm="Realm"` and an **empty body** | entry point |
| 14 | The `DispatcherServlet` is never reached | — |

**Dry run — request (3):** identical up to step 11, except that
`BasicAuthenticationFilter` decoded the header, called `ProviderManager` →
`DaoAuthenticationProvider` → `InMemoryUserDetailsManager` → `PasswordEncoder.matches` and
installed a **real** `Authentication` with authority `ROLE_USER`. At step 11 the rule for
`/api/admin/**` is `hasRole("ADMIN")` → the granted authority is `ROLE_USER`, so it is
**denied**. At step 12 `isAnonymous(auth)` is **false** → `AccessDeniedHandler.handle(...)` →
**403**, empty body.

**Takeaway.** The *only* difference between the 401 and the 403 is whether the `Authentication`
in the context was an `AnonymousAuthenticationToken` at the moment `ExceptionTranslationFilter`
ran. Everything else is identical.

### 8.2 Two chains — proving which chain handled each request

**Problem.** One application serves a public read-only API and an authenticated admin UI. Prove
that the two requests are handled by two different chains, with different CSRF and session
behaviour.

**The configuration** (same shape as §7.1):

```java
@Bean
@Order(1)
SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable());
    return http.build();
}

@Bean
@Order(2)
SecurityFilterChain uiChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/login", "/css/**").permitAll()
            .anyRequest().authenticated())
        .formLogin(Customizer.withDefaults());
    return http.build();
}
```

**The curl calls.**

```bash
curl -i http://localhost:8080/api/public/products
curl -i http://localhost:8080/admin/panel
```

**The responses.**

```text
HTTP/1.1 200
Content-Type: application/json
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Cache-Control: no-cache, no-store, max-age=0, must-revalidate

{"products":[]}
```

```text
HTTP/1.1 302
Location: http://localhost:8080/login
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Cache-Control: no-cache, no-store, max-age=0, must-revalidate

```

**Dry run — how the routing is proven:**

| Observation | Which chain it proves |
|---|---|
| The API response has **no** `Set-Cookie` | The API chain ran with `STATELESS` — a session was never created |
| The UI response is a **302 to `/login`** | The UI chain's `formLogin` entry point ran — the UI chain's `AuthenticationEntryPoint` |
| The UI response has **no** `XSRF-TOKEN` cookie yet | CSRF token generation happens on the *form* render, not on the redirect |
| `logging.level.org.springframework.security=TRACE` shows `Securing POST /api/... with chain #1` vs `Securing GET /admin/... with chain #2` | Direct proof of the first-match-wins routing |

**The decisive experiment:** add `http.csrf(csrf -> csrf.disable())` **only** to the API chain
and send a `POST /api/x` — it succeeds. Now send a `POST /admin/x` — it fails with **403** and
the response contains `Set-Cookie: XSRF-TOKEN=...`. One application, two chains, two different
CSRF policies. That experiment is the single best way to *feel* first-match-wins.


### 8.3 A custom `OncePerRequestFilter` that resolves the tenant

**Problem.** A multi-tenant application must expose the resolved tenant to the service layer.
The tenant comes from a **validated** token claim, but the filter needs to run before the
controller — and the service needs it without re-parsing the token.

**Step 1 — the tenant holder.**

```java
package com.example.tenant;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() { }

    public static void set(String tenantId) { CURRENT.set(tenantId); }
    public static String get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
```

**Step 2 — the filter.**

```java
package com.example.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TenantResolutionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                String tenantId = jwt.getClaimAsString("tenant_id");
                if (tenantId != null && !tenantId.isBlank()) {
                    TenantContext.set(tenantId);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // ALWAYS clear: the container thread is reused by the next request.
            TenantContext.clear();
        }
    }
}
```

**Step 3 — wire it inside the chain, after the token filter.**

```java
@Bean
@Order(1)
SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .addFilterBefore(new TenantResolutionFilter(), BearerTokenAuthenticationFilter.class);
    return http.build();
}
```

> ⚠️ **Why `addFilterBefore(..., BearerTokenAuthenticationFilter.class)` and not the reverse?**
> The tenant comes from the token's claim. If the filter ran **before** the token filter, the
> `Authentication` would still be anonymous and the tenant would be `null`. The ordering
> constraint is exactly the reason the chain has a fixed order.

**Step 4 — the startup log (the filter list now shows the new entry).**

```text
2026-03-04 09:12:41.201  INFO 1 --- [main] o.s.s.web.DefaultSecurityFilterChain : Will secure any request with [DisableEncodeUrlFilter, WebAsyncManagerIntegrationFilter, SecurityContextHolderFilter, HeaderWriterFilter, CorsFilter, CsrfFilter, LogoutFilter, TenantResolutionFilter, BearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter, BasicAuthenticationFilter, RequestCacheAwareFilter, SecurityContextHolderAwareRequestFilter, AnonymousAuthenticationFilter, SessionManagementFilter, ExceptionTranslationFilter, AuthorizationFilter]
```

**Note the position:** `TenantResolutionFilter` sits **between** `LogoutFilter` and
`BearerTokenAuthenticationFilter` — exactly where `addFilterBefore` was told to put it.

**Step 5 — the observable behaviour.**

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/tenant/me
```

```text
HTTP/1.1 200
Content-Type: application/json

{"tenantId":"acme-corp","userId":"u-10293"}
```

**Dry run:**

| Step | What happens |
|---|---|
| 1 | `BearerTokenAuthenticationFilter` validates the JWT and installs a `BearerTokenAuthenticationToken` whose principal is the `Jwt` |
| 2 | `TenantResolutionFilter` runs, reads `SecurityContextHolder.getContext().getAuthentication()` |
| 3 | `authentication.getPrincipal() instanceof Jwt` → **true** |
| 4 | `jwt.getClaimAsString("tenant_id")` → `"acme-corp"` |
| 5 | `TenantContext.set("acme-corp")` — the `ThreadLocal` now holds the tenant |
| 6 | `filterChain.doFilter(...)` → the controller runs and reads `TenantContext.get()` |
| 7 | The `finally` block runs `TenantContext.clear()` — **even if the controller threw** |
| 8 | Tomcat reuses this thread for the next request → **no tenant leak** |

**The leak test (do it in the drill):** send a request with a valid token, then a request with
**no** token, then hit an endpoint that prints `TenantContext.get()`. If the third response
shows `"acme-corp"`, your `finally` block is missing. This is the exact bug that
`MODE_INHERITABLETHREADLOCAL` and a missing `clear()` produce in production.

---

## 9. Code from Scratch — Production-Grade

### 9.1 The two-chain configuration (the one you will actually ship)

```java
package com.example.platform;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two chains: a stateless token API and a session-based UI.
 *
 * WHY two and not one:
 *   - The API must not create sessions (12 pods, no sticky sessions, 3200 rps).
 *   - The UI IS cookie-driven, so it needs CSRF protection and session fixation defence.
 *   - `csrf.disable()` is therefore scoped to the API chain — never applied globally.
 */
@Configuration
public class SecurityConfig {

    /** Stateless JSON API. */
    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Bearer tokens are not attached automatically by the browser, so there is no
            // ambient credential for a third-party site to abuse → CSRF is not applicable.
            // This is scoped to THIS chain; the UI chain below keeps CSRF enabled.
            .csrf(csrf -> csrf.disable())
            .headers(h -> h
                    .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                    .contentTypeOptions(Customizer.withDefaults()))
            .requiresChannel(c -> c.anyRequest().requiresSecure());   // TLS only, in prod
        return http.build();
    }

    /** Browser UI — server-rendered pages with a session cookie. */
    @Bean
    @Order(2)
    SecurityFilterChain uiChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error", "/css/**", "/js/**", "/favicon.ico").permitAll()
                .anyRequest().authenticated())
            .formLogin(Customizer.withDefaults())
            .sessionManagement(s -> s
                    .sessionFixation(sf -> sf.migrateSession())
                    .sessionConcurrency(sc -> sc.maximumSessions(1)));
        return http.build();
    }
}
```

### 9.2 A JSON `AuthenticationEntryPoint` and `AccessDeniedHandler`

```java
package com.example.platform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * WHY a custom entry point: the default (Basic / Bearer) writes an EMPTY body.
 * A JSON API whose clients see an empty 401 usually display "something went wrong"
 * instead of "please sign in" — a real usability and support-cost problem.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);           // 401
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
    }
}

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);              // 403
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Insufficient permissions\"}");
    }
}
```

**Wiring them (per chain — do not apply them globally if you have two different clients):**

```java
.exceptionHandling(ex -> ex
        .authenticationEntryPoint(jsonAuthenticationEntryPoint)
        .accessDeniedHandler(jsonAccessDeniedHandler))
```

### 9.3 A custom `RequestMatcher` — "an API request is any request that asks for JSON"

```java
package com.example.platform;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

/**
 * Matches requests that look like API traffic: the path starts with /api/ OR the caller
 * declared it wants JSON. Used to pick a chain for "the same URL, different client".
 */
public final class ApiRequestMatcher implements RequestMatcher {

    private static final String[] API_PREFIXES = {"/api/", "/internal/"};

    @Override
    public boolean matches(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (StringUtils.hasText(path)) {
            for (String prefix : API_PREFIXES) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaTypeNames.APPLICATION_JSON);
    }

    private static final class MediaTypeNames {
        static final String APPLICATION_JSON = "application/json";
    }
}
```

```java
http.securityMatcher(new ApiRequestMatcher())
```

> ▶ **When to use a custom `RequestMatcher` over `securityMatcher("/api/**")`:** when the
> routing depends on something other than the path — a header, a content type, a tenant, the
> `Host`. The moment the rule stops being "the URL starts with X", a custom matcher is the
> honest answer, and it is **testable** (it is a pure function of the request).

### 9.4 Registering a custom filter in the right place

```java
package com.example.platform;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
public class FilterConfig {

    /**
     * A servlet-container filter (NOT part of the security chain).
     * Order -90 → runs AFTER the security chain (-100), so the SecurityContext is populated.
     */
    @Bean
    FilterRegistrationBean<RequestAuditFilter> requestAuditFilter() {
        FilterRegistrationBean<RequestAuditFilter> registration =
                new FilterRegistrationBean<>(new RequestAuditFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(-90);          // just after springSecurityFilterChain
        registration.setDispatcherTypes(jakarta.servlet.DispatcherType.REQUEST);
        return registration;
    }
}
```

**And the in-chain variant, when the filter must only run for one chain:**

```java
http.addFilterBefore(new AuditFilter(), AuthorizationFilter.class);
```


## 10. Common Mistakes & Gotchas

| ❌ Wrong | ✅ Right | Why it breaks |
|---|---|---|
| `http.authorizeRequests(...)` | `http.authorizeHttpRequests(...)` | `authorizeRequests` was **removed in 6.0**; the old `AccessDecisionManager` model no longer exists |
| Extending `WebSecurityConfigurerAdapter` | Declaring a `SecurityFilterChain` bean | `WebSecurityConfigurerAdapter` was **removed in 6.0** — there is no replacement base class |
| `http.authorizeRequests().and().formLogin()` | Lambda DSL — one chained call, no `and()` | `HttpSecurity.and()` was **removed in 6.0**; `and()` existed only because the builder API returned a different type |
| One global `csrf().disable()` | Disable CSRF **per chain** | A global disable removes CSRF protection from the cookie-based UI, leaving a live CSRF hole |
| `hasRole("ROLE_ADMIN")` | `hasRole("ADMIN")` | `hasRole` prepends `ROLE_` → `hasRole("ROLE_ADMIN")` looks for `ROLE_ROLE_ADMIN` and matches nobody |
| `@Component` on a filter that is also added to the chain | A plain bean, or only one of the two | The filter is registered **twice** → it runs twice → double logging, double auth attempts |
| Relying on the default Boot security | Define an explicit `SecurityFilterChain` | Boot generates a random password and protects everything; a forgotten endpoint is accidentally public |
| `SecurityContextHolder.getContext().getAuthentication().isAuthenticated()` | Also check `!(auth instanceof AnonymousAuthenticationToken)` | `AnonymousAuthenticationToken.isAuthenticated()` is **true** → anonymous requests look authenticated |
| Registering a filter with `FilterRegistrationBean` at order `-200` | Order `-90` or later | At `-200` it runs **before** the security chain → the `SecurityContext` is not populated yet |
| Putting a `@PreAuthorize` check only on the controller | Put it on the **service** method | A second controller (admin API, batch job, message consumer) can reach the same service without the check |
| Assuming `SecurityContextHolder` survives `@Async` | Propagate it explicitly | `@Async` runs on a different thread with a different `ThreadLocal` → `Authentication` is `null` |
| `SecurityContextHolder.clearContext()` in a `finally` — but forget it in the error path | Always `try/finally` | A leaked context on a pooled thread is a privilege-escalation bug |
| `@Order` on a `SecurityFilterChain` bean but no `securityMatcher` on the first chain | Give every chain a matcher except the last | A chain without a matcher matches **everything**, so it swallows all traffic if ordered first |
| `AntPathRequestMatcher` on Spring Security 6.5+ | `PathPatternRequestMatcher` | `AntPathRequestMatcher` is **deprecated in 6.5** |
| `MvcRequestMatcher` for method+path rules | `requestMatchers(HttpMethod.GET, "/x/**")` | `MvcRequestMatcher` is **deprecated in 6.5** and pulls in the whole MVC infrastructure |
| Assuming `permitAll()` also disables CSRF | Configure them separately | `permitAll()` is an **authorization** rule; CSRF is an independent **filter** |

---

## 11. Performance Considerations

**The security chain is on the hot path of every single request.** At 3 200 rps, 5 ms of filter
overhead is 16 seconds of CPU per second — more than a core.

| Knob | Default | Cost | How to measure |
|---|---|---|---|
| `SecurityContextHolder` strategy | `ThreadLocal` | ~nanoseconds | Negligible; the cost is the **repository I/O**, not the holder |
| `NullSecurityContextRepository` (stateless) | — | **0 I/O** | Compare p99 before/after the switch |
| `HeaderWriterFilter` | ~8 headers | ~50 µs | Negligible |
| `AnonymousAuthenticationFilter` | 1 token allocation | ~1 µs | Negligible |
| `AuthorizationFilter` rule matching | Linear scan over the rule map | O(n) in the number of rules | Keep rules < 30 per chain; `RequestMatcherDelegatingAuthorizationManager` scans in order |
| Method security (`@PreAuthorize`) | SpEL evaluation | **10–200 µs** per evaluation (SpEL is not free) | `spring.security.filter.order`? No — use a Micrometer timer around the method |
| Custom `RequestMatcher` | Depends | Keep it O(1) — no DB calls in a matcher | A matcher that hits the DB turns the chain into a per-request query |

**The three biggest real-world costs:**

1. **The session store.** With Spring Session + Redis, every request in a session-based chain
   does **2 Redis round trips** (read + write). At 3 000 rps that is 6 000 Redis ops/second —
   usually the single largest latency contributor after the database. Mitigation: a stateless
   API chain for machine traffic; `CookieCsrfTokenRepository` instead of session-backed CSRF;
   short session TTLs.

2. **SpEL in method security.** `@PreAuthorize` parses and evaluates an expression **per
   invocation**. Cache the parsed expression (Spring Security does this internally via the
   `PrePostAnnotationSecurityMetadataSource` cache) but the *evaluation* still runs. For hot
   paths, a simple `hasAuthority('X')` is ~10 µs; a complex SpEL with a method call on a bean
   is ~100 µs+.

3. **Password hashing.** bcrypt with cost 10 is **~80–120 ms** of CPU on a modern server. That
   is why login must be **rare** (no repeated hashing per request), why the cost factor is
   tuned to your hardware, and why a login endpoint needs a **rate limit** (see
   [05 · Production Patterns](05-Production-Security-Patterns-and-Best-Practices.md)).

**Tuning knobs:**

| Knob | Where | Default | Guidance |
|---|---|---|---|
| `server.servlet.session.timeout` | `application.yml` | 30 min | 15 min for an admin tool, 8 h for a "remember me" flow |
| `spring.session.timeout` | Spring Session | server default | Match the servlet timeout |
| `spring.session.redis.namespace` | Spring Session | `spring:session` | Namespace per environment |
| `SecurityContextHolder` strategy | Code | `ThreadLocal` | Never `INHERITABLETHREADLOCAL` behind a pool |
| bcrypt cost | `DelegatingPasswordEncoder` | **10** | 12 if your hardware is fast and logins are rare |

**What to measure:**

| Metric | Source | Signal |
|---|---|---|
| Filter chain duration | Micrometer timer around the chain | p99 > 5 ms → investigate the session store |
| `spring.security.authenticate.success` | Actuator/Micrometer | Track the rate; a spike means a credential-stuffing attempt |
| `spring.security.authenticate.failure` | Actuator/Micrometer | Any sustained non-zero rate is an attack signal |
| 401 rate per endpoint | Micrometer | > 1 % → a broken client or an attack |
| 403 rate per endpoint | Micrometer | A sudden spike on one object id → an IDOR probe |

---

## 12. Security Considerations

**The chain is a security boundary, so the chain itself has to be secure.**

| Threat | Attack | Spring-specific defence |
|---|---|---|
| **Chain-order bypass** | Move `CorsFilter` after authentication so a preflight 401s, or move `AuthorizationFilter` earlier so some filters act before the check | Keep the canonical order; put custom filters in with `addFilterBefore/After` against a **named** Spring filter, never by index |
| **Unauthenticated access to a new endpoint** | A developer adds `POST /api/export` and forgets the rules | `anyRequest().authenticated()` as the **last** rule of every chain — deny by default |
| **Anonymous treated as authenticated** | `isAuthenticated()` returns `true` for `AnonymousAuthenticationToken` | Always use `AuthenticationTrustResolver.isAnonymous(auth)` or check the token type |
| **Filter ordering vs the servlet container** | A `@Component` filter runs before the security chain and reads a `SecurityContext` that is not there yet | Register at order `-90`; scope `DispatcherType` to `REQUEST` |
| **Thread-pool context leak** | `MODE_INHERITABLETHREADLOCAL` or a missing `clear()` in an async task | Never use the inheritable strategy; `try/finally` around the context |
| **CSRF on the API chain** | A global `csrf().disable()` leaves the cookie-based UI unprotected | Scope CSRF configuration per chain |
| **CORS as a security control** | `allowedOrigins("*")` with credentials — Spring *throws* on that combination, but `allowedOriginPatterns("*")` allows it | `allowedOriginPatterns` + `allowCredentials(true)` = **any origin with cookies** — never in production |
| **Header stripping** | A downstream proxy removes `X-Frame-Options` | Write the headers at the app (`HeaderWriterFilter`) **and** at the edge |
| **`requiresSecure()` behind a proxy** | The LB terminates TLS, so the app thinks the request is HTTP → HSTS is not written and `requiresSecure` fails | `server.forward-headers-strategy=framework` (or `native`) |
| **Debug logging in production** | `logging.level.org.springframework.security=DEBUG` prints the request, the token and the authorities | Never `DEBUG` in production; `TRACE` can log the token |
| **Actuator exposure** | `/actuator/env`, `/actuator/heapdump`, `/actuator/mappings` leak secrets and routes | Restrict the exposure list and put the actuator on a separate management port |
| **Filter ordering with `FilterRegistrationBean`** | An application filter with order `-1000` runs before the security chain and can be used to short-circuit it | Keep application filters at `-90` or later |

**The specific rule that catches people:** never let an application filter or an interceptor
**write to the `SecurityContext` from an untrusted source**. The tenant example in §8.3 reads the
tenant from a **validated JWT claim**. The same filter reading an `X-Tenant-Id` header would let
a caller choose their own tenant — a complete multi-tenancy bypass.

---

## 13. Best Practices

1. **Deny by default.** End every chain's rule list with `anyRequest().authenticated()`. A new
   endpoint is protected by default, not by accident.
2. **One chain per security policy**, not per controller. `securityMatcher` + `@Order` is the
   routing mechanism.
3. **Never apply `csrf().disable()` globally.** Scope it to the chain that uses bearer tokens and
   write a comment saying *why* (see §9.1).
4. **Always define a `PasswordEncoder` bean.** Without one, Boot falls back to
   `PasswordEncoderFactories.createDelegatingPasswordEncoder()` — say it explicitly so the
   algorithm is a decision, not an accident.
5. **Use `AuthenticationTrustResolver` (or check for `AnonymousAuthenticationToken`)**, never
   bare `isAuthenticated()`.
6. **Return JSON from the 401/403 handlers** for API chains and redirect for UI chains — one
   per chain, not globally.
7. **Put the security rules on the service layer for object-level checks**, and on the URL for
   coarse gating. Both. See
   [03 · Authorization](03-Authorization-Roles-Permissions-and-Method-Security.md).
8. **Never do I/O in a `RequestMatcher`.** A matcher that queries the database turns every
   request into an extra query, and it runs *before* authentication.
9. **Register application filters at order `-90` or later** and scope the `DispatcherType`.
10. **Do not put a `@Component` filter inside the chain** — pick one registration mechanism.
11. **Always `try/finally` around any `SecurityContextHolder` mutation** in your own code.
12. **Propagate the `SecurityContext` explicitly into `@Async` work** — never rely on
    inheritance.
13. **Restrict the actuator** to a management port and an explicit allowlist; never
    `include: "*"`.
14. **Set `server.forward-headers-strategy`** when behind a load balancer, so `requiresSecure()`
    and HSTS behave correctly.
15. **Keep the rule list short.** If a chain has more than ~30 rules, split it.
16. **Log authorization denials** (user, path, method, required authority) — that log is your
    earliest warning of an attack, and your first question in an incident.
17. **Test the chain routing.** A `@SpringBootTest` with `MockMvc` that asserts 401 vs 403 vs 200
    per path is the cheapest regression test in the codebase.

---

## 14. Hands-On Exercises & Code Challenges

### 🛠️ Hands-on exercises

#### Exercise 1 — Prove the chain routing (first match wins)

**Goal:** prove that two `SecurityFilterChain` beans route by `securityMatcher` and that only
one chain ever runs.

**Steps:**
1. Create a Spring Boot 3.3 project with `spring-boot-starter-security` and
   `spring-boot-starter-web`.
2. Define chain #1 (`@Order(1)`) with `securityMatcher("/api/**")`,
   `SessionCreationPolicy.STATELESS` and `csrf().disable()`.
3. Define chain #2 (`@Order(2)`) with `formLogin()` and CSRF left enabled.
4. Start the app with `logging.level.org.springframework.security=TRACE`.
5. `curl -i http://localhost:8080/api/x` and `curl -i http://localhost:8080/admin/x`.
6. Grep the log for `Securing` lines.

**Acceptance criteria:**
- [ ] The log contains exactly **one** `Securing` line per request, naming the chain index.
- [ ] The `/api/x` response has **no** `Set-Cookie`.
- [ ] The `/admin/x` response is a **302** to `/login`.
- [ ] A `POST /admin/x` without a CSRF token returns **403**.

#### Exercise 2 — Implement a custom `RequestMatcher` and unit-test it

**Goal:** write a matcher that treats a request as "internal traffic" when it carries a valid
internal header **and** comes from a loopback address.

**Steps:**
1. Write `InternalRequestMatcher implements RequestMatcher`.
2. Return `true` only when the header `X-Internal-Call` equals `true` **and**
   `request.getRemoteAddr()` is a loopback address.
3. Write a plain JUnit 5 test using `MockHttpServletRequest` (no Spring context).
4. Wire it: `http.securityMatcher(new InternalRequestMatcher())` on the highest-order chain.

**Acceptance criteria:**
- [ ] The matcher is a **pure function** of the request — no database, no network.
- [ ] The unit test has **4 cases**: correct header + loopback → `true`; correct header +
  non-loopback → `false`; no header → `false`; header present but `false` → `false`.
- [ ] The test runs in **< 50 ms** with no Spring context loaded.

#### Exercise 3 — Implement and verify a tenant filter

**Goal:** build the tenant filter from §8.3 and prove there is no thread-local leak.

**Steps:**
1. Implement `TenantContext` (a `ThreadLocal` holder) and `TenantResolutionFilter`.
2. Add it to the API chain with `addFilterBefore(..., BearerTokenAuthenticationFilter.class)`.
3. Add an endpoint `GET /api/tenant/me` that returns `TenantContext.get()`.
4. Call it with a valid token, then call it **without** a token.

**Acceptance criteria:**
- [ ] The first call returns the tenant from the token claim.
- [ ] The second call returns `null` — **not** the previous tenant.
- [ ] The filter appears in the startup log between `LogoutFilter` and
      `BearerTokenAuthenticationFilter`.

### 💻 Code challenges

#### Challenge 1 — A chain router that keeps the rules testable

**Task:** implement a `SecurityFilterChain` factory that builds a chain per "profile"
(`API`, `UI`, `PUBLIC`) from a small configuration object, so that adding a profile does not
require editing the security class.

**Acceptance criteria:**
- [ ] A `record SecurityProfile(String name, List<String> matchers, boolean stateless, boolean csrfEnabled, boolean formLogin)` drives the configuration.
- [ ] The factory produces exactly one `SecurityFilterChain` bean per profile, ordered by the order in the profile list.
- [ ] Each chain calls `securityMatcher(...)` with its matchers (or is left as the catch-all if the list is empty).
- [ ] A test asserts that a request to `/api/x` reaches the `API` chain and `/admin/x` reaches the `UI` chain (assert on the presence/absence of `Set-Cookie` and the 302).
- [ ] No chain other than the matched one has its filters applied (assert via a custom marker filter that records which chain ran).

**Stretch goal:** support a per-profile `AuthenticationEntryPoint` so the UI chain redirects and
the API chain returns JSON.

#### Challenge 2 — A single-request security-context tracer

**Task:** write a `OncePerRequestFilter` that logs every filter-relevant event of the request:
the selected chain, whether an `Authentication` was found, the authorities, and the final
authorization outcome — in **one** structured log line.

**Acceptance criteria:**
- [ ] The log line is **structured JSON** (or a single `key=value` line) containing: `uri`, `method`, `chainIndex`, `authenticated` (boolean), `authorities`, `anonymous` (boolean).
- [ ] It uses `AuthenticationTrustResolver` — never a bare `isAuthenticated()`.
- [ ] It **never** logs the token, the password, or the `Authorization` header.
- [ ] The log line is emitted **once** per request (use `OncePerRequestFilter` and prove it with a request that triggers an `ERROR` dispatch — the line must not appear twice).

**Stretch goal:** correlate it with an existing MDC trace id and add it to the
`AuthenticationSuccessEvent` / `AuthenticationFailureEvent` listeners.

---

## 15. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is Spring Security?**
**A:** A framework that provides authentication, authorization and protection against common
attacks, implemented as a **servlet filter chain** that every request passes through before your
controller runs. It is not an annotation you add — it is a pipeline of 16 ordered filters that
Spring Boot wires for you the moment `spring-boot-starter-security` is on the classpath.

**Q: What is the difference between authentication and authorization?**
**A:** Authentication answers *"who are you?"* — it validates credentials and produces an
`Authentication` object. Authorization answers *"may you do this?"* — it compares that
identity against rules (roles, authorities, ownership). In the chain, authentication happens
around filter #9/#10 and authorization is the final decision at filter #16.

**Q: What happens if you add `spring-boot-starter-security` and write no security code?**
**A:** Boot auto-configures a chain that requires **authentication for every endpoint** and
generates a random password, printed once in the startup log as `Using generated security
password: <uuid>`. It also protects `/actuator/**` and generates a CSRF token. So the default is
"secure", but it is *uniformly* secure — not what most applications actually want.

**Q: What is a `SecurityFilterChain`?**
**A:** A bean that holds a list of `SecurityFilter`s and decides **which requests it applies
to** via `matches(request)`. You declare one per security policy. `FilterChainProxy` keeps all
of them and picks the first one that matches — so with several chains you must give them
`@Order` values, and the catch-all chain must be last.

**Q: What does `permitAll()` do?**
**A:** It makes the matched URLs accessible **without authentication**. It is an *authorization*
rule only — it does not disable CSRF, does not change the session policy, and does not stop the
authentication filters from running (they simply find no credentials and pass through).

**Q: What is the difference between `hasRole("ADMIN")` and `hasAuthority("ADMIN")`?**
**A:** `hasRole` prepends the `ROLE_` prefix, so `hasRole("ADMIN")` requires the authority
`ROLE_ADMIN`; `hasAuthority("ADMIN")` requires exactly `ADMIN`. Use `hasRole` for roles and
`hasAuthority` for everything else (scopes, permissions).

### 🟡 Intermediate

**Q: Explain the Spring Security filter chain in the order the filters run.**
**A:** The chain is `SecurityContextHolderFilter` → `HeaderWriterFilter` → `CorsFilter` →
`CsrfFilter` → `LogoutFilter` → the authentication filters (`BearerTokenAuthenticationFilter`,
`UsernamePasswordAuthenticationFilter`, `BasicAuthenticationFilter`) → `RequestCacheAwareFilter`
→ `SecurityContextHolderAwareRequestFilter` → `AnonymousAuthenticationFilter` →
`SessionManagementFilter` → `ExceptionTranslationFilter` → `AuthorizationFilter`. The two
constraints that matter: `CorsFilter` is **before** authentication (a preflight carries no
credentials) and `ExceptionTranslationFilter` is **after** authentication but **before**
`AuthorizationFilter` (it needs to know who you are to choose 401 vs 403).

**Q: What is `FilterChainProxy` and `DelegatingFilterProxy`?**
**A:** `DelegatingFilterProxy` is the servlet-API bridge — it is registered with the container as
a plain `jakarta.servlet.Filter` named `springSecurityFilterChain` (order -100) and forwards to
the Spring bean of the same name. That bean is a `FilterChainProxy`, which holds the
`List<SecurityFilterChain>`, picks the first matching chain and walks its filters with a
`VirtualFilterChain`.

**Q: `SecurityContextHolder` vs `SecurityContextRepository` — what is the difference?**
**A:** `SecurityContextHolder` is *where the current context lives right now* — a `ThreadLocal`
by default. `SecurityContextRepository` is *where the context is persisted between requests* —
the session, the request attribute, or nowhere (`NullSecurityContextRepository` for stateless
apps). In 6.x the `SecurityContextHolderFilter` delegates the load and the save to the
repository, and the load is **lazy**.

**Q: How do you protect `/api/**` differently from the rest of the app?**
**A:** Declare two `SecurityFilterChain` beans with different `@Order` values, give the first
one `securityMatcher("/api/**")`, and configure the API chain as stateless with CSRF disabled
while the second (catch-all) chain keeps the session-based, CSRF-protected behaviour. Only one
chain ever runs for a request, and the first match wins.

**Q: What is the `AnonymousAuthenticationFilter` for?**
**A:** It installs an `AnonymousAuthenticationToken` when no authentication is present, so that
`SecurityContextHolder.getContext().getAuthentication()` never returns `null` — otherwise every
piece of code reading the current user would NPE on public endpoints. Note that this token's
`isAuthenticated()` is `true`, which is why the framework uses `AuthenticationTrustResolver` to
tell "anonymous" apart from "real".

**Q: When is a 401 returned and when is it a 403?**
**A:** `ExceptionTranslationFilter` decides. If the `Authentication` is anonymous (an
`AnonymousAuthenticationToken`, or null) it calls the `AuthenticationEntryPoint` → **401**. If
the user is genuinely authenticated but lacks the authority, the `AccessDeniedHandler` runs →
**403**. The practical rule: a 401 means "send credentials", a 403 means "sending credentials
will not help".

### 🔴 Advanced / Senior

**Q: What changed between the 5.x and 6.x security models?**
**A:** Two things. First, the `AccessDecisionManager`/`AccessDecisionVoter` model was replaced
by a single `AuthorizationManager` per rule, and `WebSecurityConfigurerAdapter` was removed
entirely — `HttpSecurity` is now configured with the lambda DSL and built into a
`SecurityFilterChain` bean. Second, the context handling was reworked: the eager
`SecurityContextPersistenceFilter` was replaced by the lazy `SecurityContextHolderFilter` that
delegates to a pluggable `SecurityContextRepository`, which is what makes a genuinely stateless
chain cheap.

**Q: How do you implement multi-tenancy without trusting a client-supplied header?**
**A:** Resolve the tenant **server-side** from the validated credential — a JWT `tenant_id`
claim or a `TenantContext` looked up from the authenticated principal. A custom filter placed
after `BearerTokenAuthenticationFilter` (`addFilterBefore(..., BearerTokenAuthenticationFilter.class)`)
sets the tenant into a `ThreadLocal` and clears it in a `finally`. Every repository query is then
scoped by that tenant, so an ID from another tenant simply is not found.

**Q: Why does the order of the rules inside `authorizeHttpRequests` matter?**
**A:** Because the rule list is a **routing table**, not a set of independent checks. The
underlying `RequestMatcherDelegatingAuthorizationManager` scans the matchers **in order** and
stops at the first one that matches. If `anyRequest().authenticated()` appears first, every
later rule is dead code. So the rules must be ordered from most specific to least specific.

**Q: How would you debug a request that is being rejected before it reaches your controller?**
**A:** Turn on `logging.level.org.springframework.security=TRACE`, which prints `Securing
POST /x with chain #N`, the matched `RequestMatcher`, the authorities of the current
authentication and the decision of each authorization manager. Then confirm the chain index
matches the chain you expected — if it does not, the routing is wrong (a `securityMatcher` that
matches too broadly, or a missing `@Order`).

**Q: What is `ObjectPostProcessor` used for?**
**A:** Spring Security constructs its own filters, so you cannot `@Autowired` them. The
`ObjectPostProcessor` is the sanctioned hook to inject a collaborator into a framework filter —
for example, setting an `AuthenticationSuccessHandler` on the
`UsernamePasswordAuthenticationFilter` without re-declaring the whole filter.

### ⚫ Expert / Principal

**Q: Design the security configuration for a system that serves three different clients.**
**A:** Three chains, three policies, ordered by specificity: (1) a **machine** chain for
`/api/**` — stateless, JWT, CSRF disabled, JSON 401/403, `requiresSecure`; (2) a **partner**
chain for `/partner/**` — stateless, `client_credentials` token, an IP allowlist via a custom
`RequestMatcher` or `IpAddressRequestMatcher`, and a rate limit; (3) a **browser** chain for
everything else — session cookie, CSRF enabled, form or OIDC login, redirecting 401 to the
login page. The trade-offs: more chains means more places to get the order wrong, so I pin the
order with explicit `@Order` values and test the routing with `MockMvc` per chain. I also keep
the shared parts (headers, `AuthenticationManager`, `PasswordEncoder`) in shared beans rather
than duplicating them.

**Q: Why is `csrf().disable()` safe for a bearer-token API, and what is the exact argument?**
**A:** CSRF exploits the fact that a browser **automatically attaches** ambient credentials
(cookies) to a cross-site request. A bearer token is **not** attached automatically — the
application's JavaScript must read it and put it in the `Authorization` header, and a
cross-origin page cannot read the token. So there is no ambient credential for the attacker to
reuse, and the CSRF attack does not apply. The argument breaks if the token is stored in a
**cookie** — then it is ambient, and CSRF protection is required again. That is the whole
reason the API chain and the UI chain are separate: the *transport* of the credential decides
whether CSRF applies.

**Q: How do you make a `RequestMatcher` safe to use as a routing predicate?**
**A:** Keep it a **pure, O(1) function of the request** — path, method, headers, remote
address. Never do I/O in it: it runs before authentication, for every request, and it cannot
consult the tenant or the user because those are not resolved yet. If a rule genuinely needs
data from the database, it belongs in the authorization manager, not in the matcher. And I
unit-test the matcher with `MockHttpServletRequest` — no Spring context — so the routing
decision is verified in isolation.

**Q: What is the risk model of `MODE_INHERITABLETHREADLOCAL`, and what is the correct
alternative?**
**A:** With an inheritable `ThreadLocal`, a thread created inside a request inherits the
request's `SecurityContext`. Behind a **thread pool** — which is exactly what `@Async`,
`CompletableFuture` and every executor provide — the worker thread may already have inherited a
context from an **earlier, different request**. The result is that request B executes with
request A's identity: a confused-deputy privilege escalation. The correct approach is explicit
propagation: capture the context, set it in the worker, and clear it in a `finally`. Spring
already does this for servlet async work via `WebAsyncManagerIntegrationFilter`; for your own
executors you must do it yourself.

---

## 16. Scenario-Based & Real-Experience Scenarios (3–5 yrs)

### Scenario 1 — "Every endpoint returns 401 after the frontend migration"

**The situation.** You release a new Angular SPA on Monday. The backend was unchanged. On
Tuesday the support channel fills with "everything is broken, we get 401 everywhere". The
browser console shows the failing call is `GET /api/orders` with **no** `Authorization` header
and a `credentials: 'include'` fetch option.

**Your first move.** Enable `logging.level.org.springframework.security=DEBUG` on one instance
and watch a single request. You see:

```text
Securing GET /api/orders with chain #1
...
AuthorizationFilter: Authorization denied
```

and — critically — **no** `Set-Cookie` and no `WWW-Authenticate` header, which would be there
if the API chain's `BearerTokenAuthenticationEntryPoint` had run.

**The trap.** You assume the API chain's `oauth2ResourceServer` was misconfigured, and you
start rewriting the `JwtDecoder`. The real signal is that **no** entry point fired — the request
never even reached the API chain's authentication filter. You were debugging the wrong chain.

**The resolution.** You check the chain's `securityMatcher`. It is `securityMatcher("/api/**")`
on the chain with `@Order(1)` — correct — but there is **another** chain with `@Order(0)`
(added for a legacy internal endpoint during the sprint) that has **no** `securityMatcher`, so
it matches everything and swallows `/api/**`. The legacy chain uses form login and CSRF, so the
SPA's `OPTIONS` preflight is answered with 302 and the bearer token is never read.

**The lesson.** *A chain without a `securityMatcher` is a catch-all, and the catch-all must be
last.* When you add a chain, always give it a matcher unless it is genuinely the default, and
always give it an explicit `@Order`. The 30-second diagnostic for "everything is 401" is the
`Securing ... with chain #N` log line.

### Scenario 2 — "The security filter I wrote never sees the user"

**The situation.** A colleague adds a `TenantContextFilter` to compute the tenant for
multi-tenancy. It reads `SecurityContextHolder.getContext().getAuthentication()` and it is
always `null`. They conclude that "Spring Security does not populate the context for custom
filters" and start passing the user manually through method parameters.

**Your first move.** You look at the startup log line:

```text
Will secure any request with [... SecurityContextHolderFilter, ... AuthorizationFilter]
```

Your colleague's filter is **not in that list** — because it is a `@Component`, so Boot
registered it as a plain servlet filter. You check the order: Boot registers `@Component`
filters at the default order, which is **after** the security chain... but their filter is at
order `Ordered.LOWEST_PRECEDENCE`? No — you check `FilterRegistrationBean` and find they set
`setOrder(-1000)`, "to make sure it runs first".

**The trap.** They made it run **before** the security chain, so the `SecurityContext` is
definitely empty. The intuition ("I need the user, so I must run early") is exactly backwards:
the security context is populated **by** the security chain, so a filter that needs the user
must run **after** it.

**The resolution.** You move the filter into the security chain with
`http.addFilterAfter(new TenantContextFilter(), BearerTokenAuthenticationFilter.class)` (or
`addFilterBefore(..., BearerTokenAuthenticationFilter.class)` if it only prepares state), and
remove the `@Component` so it is not registered twice. The filter now appears in the startup
log, between two known filters.

**The lesson.** *Filters that need the authenticated identity live inside the security chain;
filters that need to run before security are registered at an order between -100 and 0.* And
never put a `@Component` filter into the chain — you get it twice.

### Scenario 3 — "Under load, one customer's data shows up in another's request"

**The situation.** A P1 from a data-protection review: user A occasionally sees user B's order
details. It happens about **4 times a day**, always on the same two endpoints, always under
load (peak traffic), never in staging.

**Your first move.** You look for the classic confused-deputy pattern. You find an
`@Async` audit-logging task that reads `SecurityContextHolder.getContext()` inside the async
method:

```java
@Async
public void auditAsync(Long orderId) {
    String user = SecurityContextHolder.getContext().getAuthentication().getName();  // ← stale
    auditService.write(orderId, user);
}
```

You also find that `SecurityContextHolder` was configured with
`MODE_INHERITABLETHREADLOCAL` — "to make the async code see the user", per a Stack Overflow
answer from 2019.

**The trap.** The inheritance *is* the bug, not the fix. With an inheritable `ThreadLocal` and
a **reused** pool thread, the context that the worker sees is whatever was last set on that
thread — possibly user B's context from a request that finished seconds ago. Under load, the
pool is hot and the bleed is frequent; in staging the pool is cold and the bug never appears.

**The resolution.** You remove the inheritable strategy, and you propagate the context
explicitly:

```java
SecurityContext context = SecurityContextHolder.getContext();
CompletableFuture.runAsync(() -> {
    SecurityContextHolder.setContext(context);
    try { auditService.write(orderId, context.getAuthentication().getName()); }
    finally { SecurityContextHolder.clearContext(); }
}, taskExecutor);
```

Better still, you pass the **data** (the username) as a method argument — the audit row does not
need the security context, it needs a name. The context is not a message-passing mechanism.

**The lesson.** *A `ThreadLocal` on a pooled thread is a shared mutable slot. Never rely on
inheritance; pass what you need explicitly.* The "works in staging" pattern for this class of
bug is always about pool warmth.


## 17. Counter-Questions, Tricky Questions & Follow-Ups

### CQ1. "Isn't the `SecurityContextHolder` just a global variable?"

- **Naive answer:** "Yes, it's a static field, so it's shared across the application." ← wrong,
  and the interviewer is testing exactly this
- **The trap:** It is static, but it holds a **`ThreadLocal`**. The context is per **thread**,
  not per application. Two threads serving two requests have two independent contexts. The
  moment you move work to another thread — `@Async`, a `CompletableFuture`, a thread pool — the
  context does **not** follow, and you get either a `null` authentication or (with the
  inheritable strategy) a **leaked** one.
- **Expert answer:** "It is a static holder of a `ThreadLocal`, so the value is scoped to the
  current thread. That is what makes it safe for a servlet container where one thread handles
  one request at a time, and what makes it dangerous everywhere else. The correct mental model
  is: *the `SecurityContextHolder` is a per-thread mailbox, and the `SecurityContextRepository`
  is the postal service that moves mail between requests.* The first is where the mail is right
  now, the second is where it is stored. For async work I capture the context and set it
  explicitly in the worker, with a `finally` that clears it — never inheritance."

### CQ2. "If `CorsFilter` just adds headers, why does it have to run before authentication?"

- **Naive answer:** "Because it's about the request headers, so it should come early." ← the
  right instinct, the wrong reason
- **The trap:** It is not about *adding* headers — it is about **answering the preflight**. A
  CORS preflight is an `OPTIONS` request sent by the browser **without** the credentials of the
  real request. If authentication runs first, the preflight is rejected (401) and the browser
  reports a CORS error and never sends the real request. `CorsFilter` handles the preflight
  itself and **short-circuits** the chain — it does not call `chain.doFilter`.
- **Expert answer:** "Because the preflight is unauthenticated by design. `OPTIONS` requests
  are sent without the `Authorization` header and without the cookies, precisely so that the
  server can answer 'is this origin allowed?' without authenticating. If `CorsFilter` ran after
  `UsernamePasswordAuthenticationFilter` or `BearerTokenAuthenticationFilter`, the preflight
  would be answered with a 401 and the browser would surface a CORS error — the real request
  never leaves the browser. So the rule is: **`CorsFilter` before the authentication filters.**
  The same reasoning explains why `CsrfFilter` sits before authentication too: it must reject a
  forged request *before* any credential is processed."

### CQ3. "Why not just write one `SecurityFilterChain` and use `requestMatchers` for everything?"

- **Naive answer:** "One chain is simpler, so use one." ← it *is* simpler, and it is wrong for
  almost every real system
- **The trap:** A single chain forces one **session policy**, one **CSRF policy**, one
  **authentication mechanism** and one **entry point** onto every URL. A stateless JWT API and a
  cookie-based admin UI cannot coexist: the API must not create sessions, the UI must, and the
  UI must have CSRF protection while the API must not.
- **Expert answer:** "Because a chain is a *policy*, and policies differ by client. The API
  chain is stateless, bearer-token, CSRF-disabled, JSON 401. The UI chain is session-based,
  cookie-based, CSRF-protected, redirecting 401. Merging them means picking the worst of both:
  either the API starts creating sessions and issuing `JSESSIONID` cookies, or the UI loses
  CSRF protection. The cost of multiple chains is the routing — first match wins — which I
  manage with explicit `@Order` values and one `MockMvc` test per chain. One chain is only
  correct when the whole application has exactly one kind of client."

### CQ4. "You said the `AuthorizationFilter` is last. What if I want to run my audit filter after
the authorization decision?"

- **Naive answer:** "Register the audit filter with `FilterRegistrationBean` at a high order
  number so it runs last." ← plausible, but it does not do what you want
- **The trap:** A servlet-container filter at a high order runs after the **entire** security
  chain, including the `DispatcherServlet` — so it sees the response, but it does **not** see
  the authorization *decision*. If you want to log "user X was denied access to URL Y", the
  denial happened inside the chain and was turned into a 401/403 by `ExceptionTranslationFilter`
  — the filter sees only the status code.
- **Expert answer:** "There are three options depending on what I need. For 'was this request
  allowed?', I need the decision itself: implement `AuthorizationManager` for the rule and
  publish a Spring `AuthorizationEvent` — `AuthorizationFilter` publishes it for every decision,
  and a `@EventListener` receives it. For 'what was the outcome and the user', a
  `FilterRegistrationBean` at a high order plus reading the `SecurityContext` and the response
  status is enough. For 'what did the user do', the right place is not a filter at all — it is an
  audit log written in the service layer, where you know the domain effect. Filters see HTTP;
  they do not see business outcomes."

### CQ5. "Can I just add my filter to the chain with `addFilterBefore(myFilter, SecurityContextHolderFilter.class)`?"

- **Naive answer:** "Yes, that's the documented way." ← true, and the placement is usually wrong
- **The trap:** It is *possible* but it is rarely what you want. Before
  `SecurityContextHolderFilter` means **before the context is loaded** — so
  `SecurityContextHolder.getContext().getAuthentication()` is `null` in your filter. The moment
  you want the user, you want to be **after** the authentication filters, not before the context
  loader.
- **Expert answer:** "The anchor class is the API — I anchor on the *last* filter I need to be
  after. If my filter needs the identity, I anchor it after
  `BearerTokenAuthenticationFilter` or `UsernamePasswordAuthenticationFilter`. If it needs the
  tenant claim from a validated JWT, `addFilterBefore(..., BearerTokenAuthenticationFilter.class)`
  is wrong and `addFilterAfter(..., BearerTokenAuthenticationFilter.class)` is right. The
  mental model: *the anchor expresses a dependency, not a preference.* And I never mix
  registration mechanisms — a filter added to the chain must not also be a `@Component`, or it
  runs twice."

### CQ6. "Is `hasRole("ADMIN")` the same as `hasAuthority("ROLE_ADMIN")`?"

- **Naive answer:** "No, `hasRole` is about roles and `hasAuthority` is about permissions." ← the
  classic confusion
- **The trap:** They are **the same check**. `hasRole` is a convenience wrapper that prepends
  the `ROLE_` prefix (`RoleVoter.DEFAULT_ROLE_PREFIX`) and then does the authority comparison.
  The genuinely dangerous variant is `hasRole("ROLE_ADMIN")`, which looks for
  `ROLE_ROLE_ADMIN` and matches **nobody** — and it fails *silently*, as a 403, which people
  then debug by "loosening" the rule.
- **Expert answer:** "They are equivalent: `hasRole('ADMIN')` requires the granted authority
  `ROLE_ADMIN`. The right mental model is that there is only **one** kind of thing —
  a `GrantedAuthority` string — and `hasRole` is sugar that adds the prefix. I use `hasRole`
  for roles and `hasAuthority` for scopes and permissions, purely for readability. The rule I
  follow: **never write the prefix twice**, and if a rule suddenly matches nobody, check for
  `ROLE_ROLE_` in the source before changing anything else."

### CQ7. "Why does `SecurityContextHolderFilter` load the context lazily? What is the downside?"

- **Naive answer:** "Because lazy loading is faster." ← directionally right, missing the reason
- **The trap:** The real reason is **architectural**: laziness is what makes a stateless chain
  genuinely stateless. If the context were loaded eagerly on every request, the
  `SecurityContextRepository` would be consulted on **every** request — in a session-based chain
  that means a session read (and, in Spring Session, a Redis round trip) for requests that never
  look at the user.
- **Expert answer:** "Laziness means the repository is only touched if something actually asks
  for the context. In a stateless chain configured with `NullSecurityContextRepository`, the
  load is a no-op — zero I/O, no session, no `JSESSIONID`. That is exactly the 5.x→6.x
  performance win that makes stateless APIs cheap. The downside is that the *cost* of the
  context load moves from the filter entry to the first use, so a request that reads the user
  late pays the latency later — you lose the ability to attribute the cost to the filter. And
  you must be careful that the deferred supplier is only invoked once per request and that the
  context is cleared in a `finally`, because the container thread is reused."

### CQ8. "What if I need the authenticated user in an `@Async` method?"

- **Naive answer:** "Set `MODE_INHERITABLETHREADLOCAL` so the async thread inherits it." ← the
  most dangerous answer in this chapter
- **The trap:** Inheritance works for a **newly created** thread, but an executor **reuses**
  threads. A reused worker may already carry a context inherited from a **previous, unrelated
  request**. The result is that request B executes under request A's identity — a
  confused-deputy privilege escalation that is nearly impossible to reproduce in staging.
- **Expert answer:** "I never use the inheritable strategy on a pooled executor. The correct
  pattern is explicit propagation: capture the `SecurityContext` on the calling thread, set it
  in the worker, and clear it in a `finally`. Even better, I pass the **data** the async task
  needs — the username, the order id — instead of the context, because a security context is not
  a message-passing mechanism. For servlet `Callable`/`DeferredResult` work, Spring already does
  the propagation for me via `WebAsyncManagerIntegrationFilter`; for `CompletableFuture` and
  `@Async` I do it explicitly, or I use a `TaskDecorator` that applies it uniformly."


## 18. Frequently Asked Production Issues & Debugging Scenarios

### 18.1 "Every endpoint returns 401 after a dependency upgrade"

**Symptom:** after upgrading Spring Boot 3.2 → 3.3 (or a `spring-security` minor bump), every
request returns 401, including the ones that worked yesterday.

**Diagnostic command / tool:**

```bash
# 1. The chain list and the routing decision — the single most useful line
curl -s -o /dev/null -w "%{http_code}\n" https://api.example.com/api/orders

# 2. In the app: turn the framework's own logging up
#    logging.level.org.springframework.security=TRACE
```

**Expected signal in the log:**

```text
Securing GET /api/orders with chain #1
...
AuthorizationFilter: Authorization failed for request
```

**And the tell-tale:** the response has **no** `WWW-Authenticate` header, which means the
chain's `AuthenticationEntryPoint` is not the one you expect — i.e. **a different chain handled
the request**.

**Fix:** inspect the `SecurityFilterChain` beans for a chain with a **higher priority** (lower
`@Order`) than your API chain and a broad or absent `securityMatcher`. The classic cause is a
chain added for a new feature without a matcher. Give every chain an explicit matcher and an
explicit `@Order`, and add the routing test.

### 18.2 "My custom filter is not in the security chain"

**Symptom:** a `OncePerRequestFilter` that logs the current user always logs `null`, and the
filter never appears in the startup log.

**Diagnostic command / tool:**

```bash
# The startup log line is the ground truth — grep for it
kubectl logs deploy/api | grep "Will secure any request with"
```

**Expected signal:** your filter is **absent** from the list.

```text
Will secure any request with [DisableEncodeUrlFilter, WebAsyncManagerIntegrationFilter,
SecurityContextHolderFilter, HeaderWriterFilter, CorsFilter, CsrfFilter, LogoutFilter,
UsernamePasswordAuthenticationFilter, BasicAuthenticationFilter, RequestCacheAwareFilter,
SecurityContextHolderAwareRequestFilter, AnonymousAuthenticationFilter, SessionManagementFilter,
ExceptionTranslationFilter, AuthorizationFilter]
```

**Fix:** add it to the chain explicitly —
`http.addFilterAfter(new MyFilter(), BearerTokenAuthenticationFilter.class)` — and **remove the
`@Component`** so it is not registered twice. If you want it to run for every URL regardless of
chain, keep it a `@Component` and register it at order `-90` via a `FilterRegistrationBean`.

### 18.3 "The `SecurityContext` is null in an async thread"

**Symptom:** inside an `@Async` method, `SecurityContextHolder.getContext().getAuthentication()`
is `null` (or, worse, a *different* user's identity).

**Diagnostic command / tool:**

```java
// A one-line probe at the top of the async method:
log.info("thread={} auth={}", Thread.currentThread().getName(),
        SecurityContextHolder.getContext().getAuthentication());
```

**Expected signal:** the thread name is a **pool thread** (`pool-1-thread-3`), not
`http-nio-8080-exec-7`. A `ThreadLocal` is per thread, so the value cannot have travelled.

**Fix:** propagate explicitly (see CQ8), or use a `TaskDecorator`:

```java
@Bean
TaskDecorator securityContextPropagatingDecorator() {
    return runnable -> {
        SecurityContext context = SecurityContextHolder.getContext();
        return () -> {
            SecurityContextHolder.setContext(context);
            try { runnable.run(); } finally { SecurityContextHolder.clearContext(); }
        };
    };
}
```

**And check the strategy:** if
`SecurityContextHolder.getStrategy()` is an
`InheritableThreadLocalSecurityContextHolderStrategy`, remove it — it is the cause of the
*bleed* variant of this bug, not of the *null* variant.

placed above the `permitAll()` rule. Reorder from most specific to least specific.


## 19. Architecture Design Questions

### 19.1 Design the security architecture for a multi-tenant B2B SaaS

**The prompt:** "We have 400 tenants, 90 000 users, a React SPA, a public marketing site, and
a partner integration API. All on the same domain. Where does security live?"

**Reference design:**

```text
                    ┌───────────────────────────────────────────────────┐
  BROWSER ────────► │  /            marketing chain   (@Order 3)       │
                    │  permitAll, static, CSP strict, no session        │
  SPA ─────────────► │  /app/**       browser chain    (@Order 2)       │
                    │  OIDC login → session cookie (HttpOnly, Secure,  │
                    │  SameSite=Lax), CSRF enabled, redirecting 401    │
  PARTNER ─────────► │  /partner/**   machine chain   (@Order 1)       │
                    │  client_credentials JWT, IP allowlist matcher,   │
                    │  rate limit, JSON 401/403, no session             │
  SPA (API calls) ─► │  /api/**       API chain       (@Order 4)        │
                    │  JWT bearer, STATELESS, CSRF disabled             │
                    └───────────────────────────────────────────────────┘
                                      │
                    TENANT RESOLUTION (one filter, in the API/partner chains)
                    tenant id comes ONLY from the validated token claim
                                      │
                    EVERY REPOSITORY QUERY is scoped by the tenant id
                    findByIdAndTenantId(id, tenantId) — never findById(id)
```

**Data flow for one API call:** the JWT is validated by `BearerTokenAuthenticationFilter` →
the `tenant_id` claim is read by `TenantResolutionFilter` → the `SecurityContext` holds a
`Jwt` principal whose `tenant_id` is authoritative → the service layer queries with the tenant
scope → the method-level rule `@PreAuthorize("@tenantGuard.canAccess(#id, authentication)")`
adds a second, object-level check.

**The trade-offs to say out loud:**

| Decision | Trade-off |
|---|---|
| One chain per client type | More routing to get right; mitigated with explicit `@Order` + one `MockMvc` test per chain |
| Tenant from the token claim, not a header | Requires every client to get a token with a tenant claim; partners must be issued one per tenant |
| Tenant-scoped repository methods | More verbose queries (`findByIdAndTenantId`); the alternative — filter in the service — is one forgotten `if` away from an IDOR |
| CSRF disabled on the API chain | Correct for bearer tokens, **wrong** the moment the API starts using cookies — the rule must be revisited if the token moves to a cookie |
| Method-level tenant check in addition to the scoped query | Two checks, one defence in depth; the query scope is the real protection, the annotation catches "you asked for an id that is not yours" explicitly |

### 19.2 Design the migration from a monolith's session security to a stateless API

**The prompt:** "We have a Spring Boot 2 monolith with JSP pages, `WebSecurityConfigurerAdapter`
and form login. We are extracting a REST API that mobile clients will use. How do we get from
there to a stateless JWT API without breaking the monolith?"

**Reference design — a three-phase migration, each phase independently shippable:**

```text
PHASE 0 — Make the current state explicit (no behaviour change)
─────────────────────────────────────────────────────────────
  • Move the existing WebSecurityConfigurerAdapter configuration into a
    SecurityFilterChain bean (Boot 2.7+/3.x supports both during the move).
  • Extract the user store into a UserDetailsService that reads from the
    EXISTING database. No schema change.
  • Add a UserDetailsService-backed PasswordEncoder = DelegatingPasswordEncoder,
    with the upgradeEncoding path so legacy hashes migrate on next login.
  • Add a /actuator/health endpoint on a separate management port.

PHASE 1 — Add the API chain beside the existing UI chain
─────────────────────────────────────────────────────────
  @Order(1) apiChain   → securityMatcher("/api/**") — STATELESS, CSRF disabled
  @Order(2) uiChain    → the existing JSP session behaviour, unchanged

  The monolith keeps working. The API accepts a session cookie AND (later) a token.
  Endpoint: /api/v1/** (versioned prefix) so the routing is unambiguous.

PHASE 2 — Introduce tokens without removing sessions
────────────────────────────────────────────────────
  • /api/v1/auth/login issues a JWT; the /api/v1/auth/refresh rotates it.
  • The API chain's oauth2ResourceServer validates it.
  • The UI chain is untouched — the monolith still uses sessions.
  • Both chains share ONE AuthenticationManager, ONE PasswordEncoder and ONE
    UserDetailsService, so a user is the same user on both sides.

PHASE 3 — Cut over
─────────────────
  • The mobile client stops sending cookies; the API chain becomes token-only.
  • The old /api (non-versioned) endpoints are removed.
  • The monolith's session config is frozen; new endpoints go under /api/v1.
```

**The trade-offs to say out loud:**

| Decision | Trade-off |
|---|---|
| Versioned `/api/v1` prefix rather than reusing `/api` | A second URL namespace to remember; but it makes the routing explicit and lets both chains coexist during the migration |
| Share the `AuthenticationManager`/`UserDetailsService` across both chains | One source of truth for users and password hashing; but the token issuer must live outside the security config, or you get a circular dependency |
| Keep CSRF on the UI chain during the whole migration | The monolith is protected; the cost is remembering that `csrf().disable()` is scoped, not global |
| `DelegatingPasswordEncoder` with `upgradeEncoding` | Legacy hashes keep working; the cost is a re-hash on each user's next login (one bcrypt operation, ~100 ms, once) |
| Do **not** remove sessions in the same release as the token rollout | Slower migration, but each step is reversible; a combined cutover is how you get a 3 a.m. rollback |


## 20. Rapid-Fire MCQs

**Q1.** Spring Security's request interception happens in…
- A) An `HandlerInterceptor`  B) **A servlet `Filter` chain**  C) An `@Aspect`  D) A `BeanPostProcessor`

**Q2.** The class that holds the `List<SecurityFilterChain>` is…
- A) `SecurityContextHolder`  B) **`FilterChainProxy`**  C) `DelegatingFilterProxy`  D) `AuthorizationFilter`

**Q3.** The servlet-API bridge registered as `springSecurityFilterChain` is…
- A) `FilterChainProxy`  B) `SecurityContextHolderFilter`  C) **`DelegatingFilterProxy`**  D) `ExceptionTranslationFilter`

**Q4.** `SecurityContextHolder` stores the context in a…
- A) Static field  B) Session map  C) **A `ThreadLocal`**  D) Request attribute

**Q5.** In Spring Security 6.x, the filter that loads the `SecurityContext` is…
- A) `SecurityContextPersistenceFilter`  B) **`SecurityContextHolderFilter`**  C) `SecurityContextHolderAwareRequestFilter`  D) `SessionManagementFilter`

**Q6.** `DelegatingSecurityContextRepository` combines…
- A) A session and a cookie  B) **A request attribute and a session**  C) Two sessions  D) A JWT and a session

**Q7.** For a stateless JWT API, the correct repository is…
- A) `HttpSessionSecurityContextRepository`  B) `RequestAttributeSecurityContextRepository`  C) **`NullSecurityContextRepository`**  D) `DelegatingSecurityContextRepository`

**Q8.** The filter that installs an `AnonymousAuthenticationToken` is…
- A) `AuthorizationFilter`  B) `SessionManagementFilter`  C) **`AnonymousAuthenticationFilter`**  D) `ExceptionTranslationFilter`

**Q9.** `AnonymousAuthenticationToken.isAuthenticated()` returns…
- A) `false`  B) **`true`**  C) `null`  D) It throws

**Q10.** The filter that converts an `AccessDeniedException` into a 403 is…
- A) `AuthorizationFilter`  B) `HeaderWriterFilter`  C) **`ExceptionTranslationFilter`**  D) `CsrfFilter`

**Q11.** A request with no credentials to a protected URL returns…
- A) 403  B) 500  C) **401**  D) 302

**Q12.** A valid user without the required role returns…
- A) 401  B) **403**  C) 404  D) 400



















### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | Security runs as servlet filters, before the `DispatcherServlet` — that is why it cannot be bypassed by forgetting an annotation. |
| 2 | **B** | `FilterChainProxy` holds the `List<SecurityFilterChain>` and picks the first matching chain. |
| 3 | **C** | `DelegatingFilterProxy` is the servlet-API bridge; the bean it delegates to is the `FilterChainProxy`. |
| 4 | **C** | A `ThreadLocal` gives per-request isolation on a container thread — and breaks on thread hand-off. |
| 5 | **B** | `SecurityContextPersistenceFilter` was replaced in 6.x by the lazy, repository-backed `SecurityContextHolderFilter`. |
| 6 | **B** | The default pair is `RequestAttributeSecurityContextRepository` then `HttpSessionSecurityContextRepository`. |
| 7 | **C** | `NullSecurityContextRepository` never reads or writes a context — zero I/O, no session, no cookie. |
| 8 | **C** | It installs the placeholder identity that makes every later `getAuthentication()` call safe. |
| 9 | **B** | It is deliberately `true` — which is exactly why the framework uses `AuthenticationTrustResolver` instead. |
| 10 | **C** | `ExceptionTranslationFilter` catches the denial and calls the `AccessDeniedHandler` (403) or the entry point (401). |
| 11 | **C** | Anonymous + protected resource → the entry point → 401. |
| 12 | **B** | Authenticated but lacking the authority → `AccessDeniedHandler` → 403. |

---

## 21. Cheat Sheet / One-Page Summary

```text
┌────────────────────────────────────────────────────────────────────────────────────┐
│                    THE CHAIN IN ONE LINE                                          │
│  DelegatingFilterProxy(-100) → FilterChainProxy → [FIRST MATCHING chain] →        │
│  SecurityContextHolder → HeaderWriter → CORS → CSRF → Logout → AUTH →            │
│  RequestCache → ContextAware → Anonymous → SessionMgmt → ExceptionTranslation →    │
│  AuthorizationFilter → DispatcherServlet → Controller                              │
└────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────┐
│  POSITION IN CHAIN        OWNS                          BREAKS IF MOVED          │
├───────────────────────────┼──────────────────────────────┼─────────────────────────┤
│ SecurityContextHolderFilter│ load/clear the context      │ context overwritten    │
│ HeaderWriterFilter         │ security response headers   │ error responses bare   │
│ CorsFilter                 │ preflight + ACAO headers     │ preflight 401 → CORS   │
│ CsrfFilter                 │ CSRF token compare           │ all writes 403         │
│ LogoutFilter               │ /logout, session kill       │ cannot sign out        │
│ Bearer/UsernamePassword    │ credential validation        │ every request 401      │
│ AnonymousAuthentication    │ anonymous placeholder        │ NPE everywhere         │
│ SessionManagementFilter    │ session policy / fixation    │ stateless API leaks     │
│ ExceptionTranslationFilter │ AccessDenied → 403/401       │ denial becomes a 500   │
│ AuthorizationFilter        │ FINAL access decision        │ later filters unvetted │
└───────────────────────────┴──────────────────────────────┴─────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────┐
│  WHO DECIDES WHAT                                                                │
├───────────────────────────┬────────────────────┬─────────────────────────────────────┤
│ AuthenticationManager      │ "who is this?"    │ ProviderManager → DaoAuthProvider  │
│ AuthenticationProvider     │ "verify the proof"│ supports() → first non-null wins    │
│ UserDetailsService         │ "find the user"   │ LOOKUP ONLY — not the mechanism     │
│ AuthorizationManager       │ "may they?"       │ URL level (AuthorizationFilter)     │
│ @PreAuthorize              │ "may they, here?" │ METHOD level (innermost interceptor)│
└───────────────────────────┴────────────────────┴─────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────┐
│  401 vs 403 — THE DECISION                                                       │
│  ExceptionTranslationFilter catches the exception:                                │
│    isAnonymous(authentication)?  → AuthenticationEntryPoint → 401 (send creds)    │
│    otherwise                     → AccessDeniedHandler      → 403 (give up)     │
│  Anonymous == AnonymousAuthenticationToken || auth == null                        │
│  ⚠ isAuthenticated() is TRUE for anonymous — never use it alone                  │
└────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────┐
│  MULTI-CHAIN ROUTING                                                             │
│  @Order(1) securityMatcher("/api/**")   → stateless, csrf disabled              │
│  @Order(2) securityMatcher("/ui/**")    → session, csrf enabled                 │
│  @Order(3) no matcher (CATCH-ALL)       → must be LAST                          │
│  ⚠ only ONE chain runs; the others' filters are absent from that request        │
│  ⚠ csrf().disable() scoped to ONE chain, never global                          │
└────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────┐
│  THE 5 DIAGNOSTIC COMMANDS                                                       │
├────────────────────────────────────────────────────────────────────────────────────┤
│ logging.level.org.springframework.security=TRACE   → "Securing X with chain #N"   │
│ kubectl logs deploy/api | grep "Will secure any request with"  → chain list    │
│ curl -i -X OPTIONS $URL -H "Origin: ..."            → the preflight status      │
│ curl -i $URL -o /dev/null -w "%{http_code}"        → 200/302/401/403            │
│ Print SecurityContextHolder.getStrategy()          → must be ThreadLocal, NOT   │
│                                                    → InheritableThreadLocal    │
└────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 22. Real-World Production Notes

| Setting | Default | Deviation rule |
|---|---|---|
| `logging.level.org.springframework.security` | `INFO` | `DEBUG` in a controlled debug session; **never** `TRACE` in production (it prints authorities and can print tokens) |
| `SecurityContextHolder` strategy | `ThreadLocal` | Never `INHERITABLETHREADLOCAL` behind a pool; explicit propagation instead |
| `SessionCreationPolicy` | `IF_REQUIRED` | `STATELESS` for every API chain; `IF_REQUIRED`/`ALWAYS` only for a browser UI |
| `sessionFixation` | `migrateSession` | Keep the default; never `none` |
| `csrf` | enabled | Disabled **only** on a bearer-token chain, with a comment explaining why |
| `formLogin` | enabled when a `UserDetailsService` exists | Disable it on API chains (`http.formLogin(AbstractHttpConfigurer::disable)`) or the login page leaks into the API surface |
| `httpBasic` | enabled when no other mechanism is configured | Disable on browser chains; keep it for a machine chain if you want it |
| `requiresChannel` | `http` | `requiresSecure()` in production — behind a proxy also set `server.forward-headers-strategy=framework` |
| `maximumSessions` | unlimited | Set `1` for admin tools; requires a `SessionRegistry` bean |
| `server.servlet.session.timeout` | 30 min | 15 min for admin; 8 h only with "remember me" |
| Actuator exposure | `health` only | Explicit allowlist; never `*`; put it on a separate management port |

**Metrics worth watching:**

| Metric | Source | Signal |
|---|---|---|
| `spring.security.authenticate.success` | Actuator/Micrometer | Baseline of legitimate logins; a sudden rise is a credential-stuffing signal |
| `spring.security.authenticate.failure` | Actuator/Micrometer | Any sustained rate > 0.1 % of logins is an attack |
| 401 rate per endpoint | Micrometer + your own filter | A spike on one endpoint = a broken client or a scanner |
| 403 rate per endpoint | Micrometer | A spike concentrated on one **object id** = an IDOR probe |
| Filter chain duration (p99) | A `OncePerRequestFilter` timer | > 5 ms → the session store is the suspect |
| Authorizations per request | Custom `AuthorizationEvent` listener | A request hitting many rules = a routing problem |
| Distinct `Remote-Addr` per 401 burst | Log aggregation | A burst from one IP = a scanner; from many = a botnet |

**Patterns I have seen work:**

- ✅ One `SecurityFilterChain` per client type, each with an explicit `@Order` **and** an
  explicit matcher.
- ✅ A JSON `AuthenticationEntryPoint` and `AccessDeniedHandler` for API chains, and redirecting
  ones for the browser chain.
- ✅ A `MockMvc` test per chain that asserts 200 / 302 / 401 / 403 — it is the cheapest
  regression test in the codebase and it catches the routing bug that pages take.
- ✅ An `AuthorizationEvent` listener that logs denials with the required authority — the
  earliest warning of an attack and the first question in an incident.
- ✅ `server.forward-headers-strategy` set in every deployment behind a proxy.
- ✅ A `FilterRegistrationBean` for request-logging at order `-90`, so it can log the username.
- ❌ A global `csrf().disable()`.
- ❌ `hasRole("ROLE_ADMIN")`.
- ❌ An `AccessDeniedException` caught by a `@ControllerAdvice` and turned into a 500 (it hides
  the fact that it was an authorization decision).
- ❌ Logging the `Authorization` header at `TRACE` in production.

---

## 23. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The authentication flow, `UserDetailsService`, providers | [02 · Authentication Flow & Providers](02-Authentication-Flow-UserDetailsService-and-Providers.md) |
| Roles, permissions, `@PreAuthorize`, the role hierarchy | [03 · Authorization & Method Security](03-Authorization-Roles-Permissions-and-Method-Security.md) |
| Password hashing, sessions, CSRF, CORS, security headers | [04 · Password Encoding, Sessions, CSRF & CORS](04-Password-Encoding-Sessions-CSRF-and-CORS.md) |
| OWASP mapping, IDOR, mass assignment, hardening checklist | [05 · Production Security Patterns](05-Production-Security-Patterns-and-Best-Practices.md) |
| Stateless JWT resource servers, token validation | [06 · JWT Authentication](../06-JWT-Authentication/README.md) |
| OAuth2 / OIDC login flows, PKCE | 07 · OAuth2 & OpenID Connect (sibling folder) |
| The servlet request lifecycle the filters sit inside | [03 · Spring MVC — DispatcherServlet](../03-Spring-MVC/01-DispatcherServlet-and-Complete-Request-Flow.md) |
| Filter vs interceptor vs aspect | [03 · Spring MVC — Filters & Interceptors](../03-Spring-MVC/04-Filters-Interceptors-ArgumentResolvers-and-File-Transfer.md) |
| Transactions — why a long authorization query inside a transaction is expensive | [04 · JPA — Transactions](../04-Spring-Data-JPA-Hibernate/03-Transactions-Propagation-and-Isolation.md) |

---

**Next Chapter:** → [02 · Authentication Flow, UserDetailsService & Providers](02-Authentication-Flow-UserDetailsService-and-Providers.md)
**Previous Chapter:** ← [Master Index](../README.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026


