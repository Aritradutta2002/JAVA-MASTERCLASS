# 🌱 SPRING_BOOT — The Complete Spring Boot Interview Masterclass

> **Everything you need to crack a Spring Boot / Java backend interview at the 3–4+ years level —
> in depth.**
>
> 14 topic areas · 57 deep-dive chapters · 400+ interview questions with expert answers ·
> counter-questions (interviewer push-back) · visualisations · dry-run traces · MCQ banks ·
> production incident walkthroughs · system design solutions.
>
> 📌 **Every chapter follows the [SPRING_BOOT Standard](./STANDARD.md)** — so you always know
> exactly what you will find: TL;DR → theory → internal mechanism → execution flow → visuals →
> worked examples → pitfalls → performance → security → exercises → level-wise Q&A →
> **counter-questions** → debugging scenarios → architecture design → MCQs → cheat sheet.

**Target stack:** Spring Boot 3.3+ · Spring Framework 6.1+ · Java 17+ · Jakarta EE 10

---

## 🚀 Start Here (pick your path)

| You are… | Do this |
|----------|---------|
| 🟢 **Fresher (0–1 yr)** | Start at [01 · Boot Fundamentals](./01-Spring-Boot-Fundamentals/README.md) → [02 · Spring Core](./02-Spring-Core-Framework/README.md) → [03 · Spring MVC](./03-Spring-MVC/README.md) → [05 · Security](./05-Spring-Security/README.md). Use the TL;DR + Cheat Sheet only. |
| 🟡 **Mid (2–5 yr)** | Everything in order. Focus on [04 · JPA](./04-Spring-Data-JPA-Hibernate/README.md) (N+1, transactions), [05 · Security](./05-Spring-Security/README.md), [06 · JWT](./06-JWT-Authentication/README.md), [08 · Microservices](./08-Microservices-with-Spring-Boot/README.md). |
| 🔴 **Senior (5+ yr)** | [02 · AOP & Proxy Mechanics](./02-Spring-Core-Framework/04-AOP-Proxy-Mechanics-and-Spring-Modules.md), [04 · Hibernate Internals](./04-Spring-Data-JPA-Hibernate/05-Hibernate-Internals-and-Performance-Tuning.md), [08 · Resilience4j](./08-Microservices-with-Spring-Boot/03-Circuit-Breaker-Resilience4j-and-Fault-Tolerance.md), [12 · System Design](./12-System-Design-for-Java-Backend/README.md). |
| ⚫ **Mock interview tonight** | [🔥 Question Bank](#-the-question-bank) + the Counter-Questions section of every chapter. |
| 🏃 **30-minute refresher** | [05/01 · Security Filter Chain](./05-Spring-Security/01-Architecture-and-Security-Filter-Chain.md) → [06/01 · JWT Architecture](./06-JWT-Authentication/01-JWT-Architecture-Statelessness-and-Signature-Algorithms.md) → [04/03 · Transactions](./04-Spring-Data-JPA-Hibernate/03-Transactions-Propagation-and-Isolation.md) → [03/01 · DispatcherServlet](./03-Spring-MVC/01-DispatcherServlet-and-Complete-Request-Flow.md). |

---

## 📚 Chapter Index

| # | Topic | Chapters | Level | Priority | Index |
|---|-------|----------|-------|----------|-------|
| 01 | **Spring Boot Fundamentals** | 5 | 🟢→🔴 | ⭐⭐⭐⭐⭐ | [Open](./01-Spring-Boot-Fundamentals/README.md) |
| 02 | **Spring Core & Framework** | 4 | 🟡→⚫ | ⭐⭐⭐⭐⭐ | [Open](./02-Spring-Core-Framework/README.md) |
| 03 | **Spring MVC** | 4 | 🟢→🔴 | ⭐⭐⭐⭐⭐ | [Open](./03-Spring-MVC/README.md) |
| 04 | **Spring Data JPA & Hibernate** | 5 | 🟡→⚫ | ⭐⭐⭐⭐⭐ | [Open](./04-Spring-Data-JPA-Hibernate/README.md) |
| 05 | **Spring Security** | 5 | 🟡→⚫ | ⭐⭐⭐⭐⭐ | [Open](./05-Spring-Security/README.md) |
| 06 | **JWT Authentication** | 4 | 🟡→🔴 | ⭐⭐⭐⭐⭐ | [Open](./06-JWT-Authentication/README.md) |
| 07 | **OAuth2 & OpenID Connect** | 4 | 🟡→🔴 | ⭐⭐⭐⭐ | [Open](./07-OAuth2-OpenID-Connect/README.md) |
| 08 | **Microservices with Spring Boot** | 4 | 🟡→⚫ | ⭐⭐⭐⭐⭐ | [Open](./08-Microservices-with-Spring-Boot/README.md) |
| 09 | **Messaging Systems** | 4 | 🟡→🔴 | ⭐⭐⭐⭐ | [Open](./09-Messaging-Systems/README.md) |
| 10 | **Caching** | 3 | 🟡→🔴 | ⭐⭐⭐⭐⭐ | [Open](./10-Caching/README.md) |
| 11 | **Database Design & Optimization** | 4 | 🟡→🔴 | ⭐⭐⭐⭐⭐ | [Open](./11-Database-Design-and-Optimization/README.md) |
| 12 | **System Design for Java Backend** | 4 | 🟡→🔴 | ⭐⭐⭐⭐⭐ | [Open](./12-System-Design-for-Java-Backend/README.md) |
| 13 | **Testing** | 3 | 🟢→🔴 | ⭐⭐⭐⭐⭐ | [Open](./13-Testing/README.md) |
| 14 | **Deployment & DevOps** | 4 | 🟢→🔴 | ⭐⭐⭐⭐ | [Open](./14-Deployment-and-DevOps/README.md) |

---

## 🔥 The Question Bank

### Top 40 Questions by Frequency (must-master)

| # | Question | Chapter | Trap in the naive answer |
|---|----------|---------|--------------------------|
| 1 | What exactly does Spring Boot *auto-configure*, and how does it decide? | [01/02](./01-Spring-Boot-Fundamentals/02-Auto-Configuration-and-Conditional-Internals.md) | Saying "it scans the classpath" — the truth is `AutoConfiguration.imports` + `@Conditional` evaluation in a defined order |
| 2 | Explain the IoC container: `BeanFactory` vs `ApplicationContext` | [02/01](./02-Spring-Core-Framework/01-IoC-Container-and-ApplicationContext.md) | Listing interfaces instead of explaining `BeanDefinition` → `BeanDefinitionRegistry` → singleton cache |
| 3 | Constructor vs setter vs field injection? | [02/02](./02-Spring-Core-Framework/02-Dependency-Injection-and-Bean-Lifecycle.md) | "Constructor is best" without noting *field injection breaks the proxy-based AOP contract* |
| 4 | Full bean lifecycle with all callbacks | [02/02](./02-Spring-Core-Framework/02-Dependency-Injection-and-Bean-Lifecycle.md) | Missing `BeanPostProcessor` (post-processors are **not** lifecycle callbacks) |
| 5 | How does Spring resolve a circular dependency? | [02/02](./02-Spring-Core-Framework/02-Dependency-Injection-and-Bean-Lifecycle.md) | "It doesn't" — the real answer is **3 levels of early reference**, and it only works for *singleton* + *setter/field* injection |
| 6 | Bean scopes and the prototype-in-singleton trap | [02/03](./02-Spring-Core-Framework/03-Bean-Scopes-Component-Scanning-and-Configuration.md) | Not mentioning that the container does **not** manage prototype destruction |
| 7 | `BeanFactoryPostProcessor` vs `BeanPostProcessor` | [02/03](./02-Spring-Core-Framework/03-Bean-Scopes-Component-Scanning-and-Configuration.md) | Swapping the two — one mutates *definitions*, the other mutates *instances* |
| 8 | Why does `@Configuration` class get CGLIB-proxied? | [02/03](./02-Spring-Core-Framework/03-Bean-Scopes-Component-Scanning-and-Configuration.md) | "For performance" — it is for **inter-bean method call interception** (`proxyBeanMethods = true`) |
| 9 | JDK dynamic proxy vs CGLIB — when does Spring pick which? | [02/04](./02-Spring-Core-Framework/04-AOP-Proxy-Mechanics-and-Spring-Modules.md) | Saying "CGLIB for classes, JDK for interfaces" without the rule: **JDK proxy if the target implements ≥1 interface** |
| 10 | Why does `@Transactional` not work on a self-invoked method? | [02/04](./02-Spring-Core-Framework/04-AOP-Proxy-Mechanics-and-Spring-Modules.md) | "Because of the proxy" — without saying the call never leaves the target, so the advice never runs |
| 11 | What is the complete HTTP request lifecycle in Spring Boot? | [01/05](./01-Spring-Boot-Fundamentals/05-Internal-Working-Flow-and-Request-Lifecycle.md) | Stopping at "DispatcherServlet → Controller → Response" |
| 12 | What are the 9 `DispatcherServlet` strategies? | [03/01](./03-Spring-MVC/01-DispatcherServlet-and-Complete-Request-Flow.md) | Listing only `HandlerMapping`/`HandlerAdapter`/`ViewResolver` |
| 13 | `Filter` vs `Interceptor` vs `Aspect` — when does each run? | [03/04](./03-Spring-MVC/04-Filters-Interceptors-ArgumentResolvers-and-File-Transfer.md) | Claiming AOP runs "around everything" — it runs **only around the controller method** |
| 14 | `@RestController` vs `@Controller`? | [03/02](./03-Spring-MVC/02-Controllers-REST-APIs-and-Content-Negotiation.md) | Only "one returns a body" — `@ResponseBody` is the actual difference |
| 15 | `@Valid` vs `@Validated` — what breaks? | [03/03](./03-Spring-MVC/03-Validation-and-Global-Exception-Handling.md) | Not naming that **method-level validation needs `@Validated` on the class** |
| 16 | How do you design a global error response? | [03/03](./03-Spring-MVC/03-Validation-and-Global-Exception-Handling.md) | Hand-rolling a JSON shape — the modern answer is `ProblemDetail` (RFC 7807) |
| 17 | What is the N+1 problem and how do you fix it? | [04/04](./04-Spring-Data-JPA-Hibernate/04-Lazy-Eager-N-plus-1-and-Fetch-Strategies.md) | "Use eager loading" — which produces a **cartesian explosion** on collections |
| 18 | Why does `LazyInitializationException` happen? | [04/04](./04-Spring-Data-JPA-Hibernate/04-Lazy-Eager-N-plus-1-and-Fetch-Strategies.md) | Blaming lazy loading itself — the cause is the **session closing at the transaction boundary** |
| 19 | `@EntityGraph` vs join fetch vs `BatchSize` — when? | [04/04](./04-Spring-Data-JPA-Hibernate/04-Lazy-Eager-N-plus-1-and-Fetch-Strategies.md) | Not knowing that `@EntityGraph` on a *collection* still needs `distinct` on the JPQL |
| 20 | Explain the 7 transaction propagation behaviours | [04/03](./04-Spring-Data-JPA-Hibernate/03-Transactions-Propagation-and-Isolation.md) | Listing names without saying `REQUIRES_NEW` **needs a second DB connection** |
| 21 | `JpaTransactionManager` vs `DataSourceTransactionManager`? | [04/03](./04-Spring-Data-JPA-Hibernate/03-Transactions-Propagation-and-Isolation.md) | Not mentioning that a `DataSourceTransactionManager` does **not** enlist JPA in the tx |
| 22 | First-level vs second-level cache | [04/02](./04-Spring-Data-JPA-Hibernate/02-Entity-Lifecycle-Persistence-Context-and-Caches.md) | Calling the 1st-level cache "a cache" — it is a **per-transaction identity map** |
| 23 | What are the four entity states? | [04/02](./04-Spring-Data-JPA-Hibernate/02-Entity-Lifecycle-Persistence-Context-and-Caches.md) | Missing **detached** and that `merge` returns a *different* instance |
| 24 | `save` vs `saveAndFlush` vs `persist` | [04/03](./04-Spring-Data-JPA-Hibernate/03-Transactions-Propagation-and-Isolation.md) | Not explaining that `save` on a new entity triggers an immediate `INSERT` to obtain the id |
| 25 | Explain the Spring Security filter chain | [05/01](./05-Spring-Security/01-Architecture-and-Security-Filter-Chain.md) | Listing filters without the **ordering contract** (CORS before auth, `ExceptionTranslationFilter` last-ish) |
| 26 | `UserDetailsService` vs `AuthenticationProvider`? | [05/02](./05-Spring-Security/02-Authentication-Flow-UserDetailsService-and-Providers.md) | Treating `UserDetailsService` as *the* authentication mechanism — it is only a **user lookup** |
| 27 | 401 vs 403 — when is each returned? | [05/02](./05-Spring-Security/02-Authentication-Flow-UserDetailsService-and-Providers.md) | Not explaining that `ExceptionTranslationFilter` decides based on whether the `Authentication` is anonymous |
| 28 | `@PreAuthorize` vs URL security — which wins? | [05/03](./05-Spring-Security/03-Authorization-Roles-Permissions-and-Method-Security.md) | Not knowing the URL rules run **first**, and method security runs **last** (deepest) |
| 29 | Which password encoder should I use? | [05/04](./05-Spring-Security/04-Password-Encoding-Sessions-CSRF-and-CORS.md) | Recommending raw `BCrypt` — the production answer is `DelegatingPasswordEncoder` |
| 30 | CSRF vs CORS — are they the same? | [05/04](./05-Spring-Security/04-Password-Encoding-Sessions-CSRF-and-CORS.md) | Saying "CORS protects against CSRF" — **it does not** |
| 31 | JWT structure and the three parts | [06/01](./06-JWT-Authentication/01-JWT-Architecture-Statelessness-and-Signature-Algorithms.md) | Not saying the payload is **base64url, not encrypted** |
| 32 | HS256 vs RS256 — when does it matter? | [06/01](./06-JWT-Authentication/01-JWT-Architecture-Statelessness-and-Signature-Algorithms.md) | Missing that HS256 requires the **verifier to hold the signing key** (a secret-sharing problem) |
| 33 | How do you revoke a JWT? | [06/03](./06-JWT-Authentication/03-Token-Revocation-Storage-and-Production-Design.md) | "You can't" — the answer is a **short TTL + refresh-token denylist** |
| 34 | Access vs refresh token — different lifetimes, storage, transport? | [06/02](./06-JWT-Authentication/02-Access-Refresh-Tokens-and-Token-Rotation.md) | Not mentioning that refresh tokens must be **stored hashed server-side** |
| 35 | What is token rotation and reuse detection? | [06/02](./06-JWT-Authentication/02-Access-Refresh-Tokens-and-Token-Rotation.md) | Missing the **family invalidation** consequence |
| 36 | OAuth2 vs OIDC — what is the difference? | [07/03](./07-OAuth2-OpenID-Connect/03-Client-Credentials-Refresh-Tokens-and-OpenID-Connect.md) | "OIDC is OAuth2 with a token" — it is an **identity layer with an ID token and userinfo endpoint** |
| 37 | Why is PKCE mandatory now? | [07/02](./07-OAuth2-OpenID-Connect/02-Authorization-Code-and-PKCE-Flows.md) | Not saying it closes the **authorization code interception** attack on public clients |
| 38 | Monolith vs microservices — when do you NOT split? | [08/01](./08-Microservices-with-Spring-Boot/01-Monolith-vs-Microservices-and-Service-Discovery.md) | Answering "always microservices" — the trap is the **distributed monolith** |
| 39 | Circuit breaker states and the failure modes it fixes | [08/03](./08-Microservices-with-Spring-Boot/03-Circuit-Breaker-Resilience4j-and-Fault-Tolerance.md) | Not mentioning that **timeouts must come first** — a breaker without a timeout is useless |
| 40 | Kafka ordering guarantee — across what boundary? | [09/03](./09-Messaging-Systems/03-Consumer-Groups-Partitions-and-Ordering-Guarantees.md) | "Kafka guarantees ordering" — it guarantees it **within a partition only** |
| 41 | How do you make a message consumer idempotent? | [09/04](./09-Messaging-Systems/04-DLQ-Retry-Idempotency-and-Event-Driven-Design.md) | Not naming the **idempotency key + dedup table** pattern |
| 42 | Cache-aside vs write-through — when? | [10/02](./10-Caching/02-Cache-Aside-Pattern-and-Distributed-Caching.md) | Not naming the **stale window** trade-off that cache-aside accepts |
| 43 | How do you prevent a cache stampede? | [10/02](./10-Caching/02-Cache-Aside-Pattern-and-Distributed-Caching.md) | Only saying "use a lock" — not the **single-flight / stale-while-revalidate** variants |
| 44 | Composite index and the leftmost prefix rule | [11/01](./11-Database-Design-and-Optimization/01-Indexing-and-Query-Optimization.md) | Not explaining **why** `WHERE b = ?` cannot use `(a, b)` |
| 45 | What causes a deadlock and how do you find it? | [11/03](./11-Database-Design-and-Optimization/03-Deadlocks-and-Concurrency-Troubleshooting.md) | Not naming the **wait-for graph** or `SHOW ENGINE INNODB STATUS` |
| 46 | How do you size a connection pool? | [11/04](./11-Database-Design-and-Optimization/04-Connection-Pooling-HikariCP-and-Database-Scaling.md) | "Set it to 20" — not **Little's law** (`pool ≈ concurrent requests × latency`) |
| 47 | Design a URL shortener | [12/02](./12-System-Design-for-Java-Backend/02-URL-Shortener-and-Rate-Limiter.md) | Not covering **key generation at scale** and **hot-key redirects** |
| 48 | Token bucket vs sliding window for rate limiting? | [12/02](./12-System-Design-for-Java-Backend/02-URL-Shortener-and-Rate-Limiter.md) | Not mentioning the **Redis atomicity** requirement |
| 49 | Unit vs integration vs E2E — where does each live? | [13/01](./13-Testing/01-JUnit5-and-Mockito.md) | Describing the pyramid's shape without the **slice-test** concept |
| 50 | Why is your integration test flaky? | [13/02](./13-Testing/02-Integration-Testing-and-Testcontainers.md) | Not knowing the three usual causes: **time, shared state, ordering** |
| 51 | How do you containerise a Spring Boot app properly? | [14/01](./14-Deployment-and-DevOps/01-Docker-and-Docker-Compose.md) | Not mentioning the **layered JAR / `jarmode=layertools`** extraction |
| 52 | Liveness vs readiness probe — what happens if you get it wrong? | [14/02](./14-Deployment-and-DevOps/02-Kubernetes-Basics.md) | Not knowing that a **failing liveness probe on a slow warm-up causes a crash loop** |
| 53 | What are the four golden signals? | [14/04](./14-Deployment-and-DevOps/04-Monitoring-Logging-and-Observability.md) | Not being able to name **latency, traffic, errors, saturation** |
| 54 | Structured logging and correlation IDs | [14/04](./14-Deployment-and-DevOps/04-Monitoring-Logging-and-Observability.md) | Not knowing that the **correlation ID must be propagated to every downstream call** |

> 💡 **How to use the bank:** don't memorise answers. For each question, say the *naive*
> answer first, then name the trap, then give the expert answer. That structure alone
> impresses interviewers more than any fact you can recite.

---

## 🧭 The Counter-Question Technique

The single biggest difference between a mid-level and a senior candidate is **how they handle
push-back**.

Every chapter in this folder has a **"Counter-Questions, Tricky Questions & Follow-Ups"**
section with **8+ probes** of these four kinds:

| Probe type | Example | How to answer |
|-----------|---------|---------------|
| **"Why not X?"** | "Isn't eager loading simpler than `@EntityGraph`?" | Name the naive answer → name the trap → give the expert answer |
| **"What happens if…?"** | "What happens if the DB is down and the cache is empty?" | Trace the exact code path, name the fallback and the user-visible symptom |
| **"How would you find out?"** | "How do you diagnose a connection-pool exhaustion?" | Name the tool (`SHOW PROCESSLIST`, Hikari MBean, actuator `/metrics`) **and** the signal you expect to see |
| **"Is that true?"** | "Is a JWT really stateless?" | Correct the premise politely, then explain the precise guarantee and its hidden cost |

---

## 🎯 Interview Frequencies by Company Type

```
FAANG / Product      JPA/Hibernate ███████  Security ███████  Distributed █████
                     Spring MVC  ███████    JWT      █████    Caching      █████
                     Transactions █████     Kafka    █████    System Design ████

Banking / FinTech    Transactions ███████ Security ███████   JPA/Hibernate ███████
                     SQL/JDBC     ███████   Caching   █████   Kafka         █████

Indian Product Co.   Spring MVC  ███████   JPA/Hibernate ███████  Security   █████
                     Transactions █████     SQL/JDBC   █████    Docker/K8s  █████

Contract / Product   Spring MVC  ███████   Transactions █████  JPA/Hibernate ████
Dev (US clients)     Security    █████     Testing    █████   Docker       ████
```

---

## 🧪 How to Practise (a real 8-week plan)

```
WEEK 1 — Boot & Core Foundations
  Mon-Tue  01 Boot Fundamentals (01→03)        Wed-Thu  01 Boot (04→05)
  Fri-Sun  02 Spring Core (01→03)              Daily: 20 MCQs from the chapters read

WEEK 2 — Core & Web Layer
  Mon      02 Spring Core (04) AOP & proxies
  Tue-Thu  03 Spring MVC (01→03)               Fri-Sun  03 Spring MVC (04)

WEEK 3 — Persistence
  Mon-Tue  04 JPA (01→02)                      Wed-Thu  04 JPA (03)
  Fri-Sun  04 JPA (04→05)                      Daily: 1 coding drill per chapter

WEEK 4 — Security (the deep one)
  Mon-Tue  05 Security (01→03)                 Wed     05 Security (04→05)
  Thu-Sun  06 JWT (01→04)                      Fri: build a JWT flow end-to-end

WEEK 5 — OAuth2 & Microservices
  Mon-Tue  07 OAuth2 & OIDC (01→04)            Wed-Thu  08 Microservices (01→03)
  Fri-Sun  08 Microservices (04)

WEEK 6 — Messaging & Caching
  Mon-Tue  09 Messaging (01→03)                Wed     09 Messaging (04)
  Thu-Fri  10 Caching (01→03)                  Sat-Sun  11 Database Design (01→03)

WEEK 7 — Database, System Design & Testing
  Mon      11 Database Design (04)             Tue-Wed  12 System Design (01→03)
  Thu-Fri  12 System Design (04)               Sat-Sun  13 Testing (01→03)

WEEK 8 — DevOps & Polish
  Mon-Tue  14 DevOps (01→03)                   Wed     14 DevOps (04)
  Thu-Fri  Full mock interviews: 4 rounds × 45 min, recorded & reviewed
  Sat-Sun  Revisit every §17 counter-question bank, closed-book
```

### Daily mechanics (60–90 min)

```
15 min  Read ONE chapter's TL;DR + Cheat Sheet
25 min  Read the Deep Dive + trace every diagram out loud
20 min  Answer the Counter-Questions WITHOUT looking
15 min  Do the Rapid-Fire MCQs (closed-book)
15 min  Write & run one code sample; predict output BEFORE running
```

---

## 🧰 Cheat-Sheet One-Pagers

| Topic | Cheat Sheet |
|-------|-------------|
| Spring Boot auto-configuration | [01/02](./01-Spring-Boot-Fundamentals/02-Auto-Configuration-and-Conditional-Internals.md) |
| Bean lifecycle | [02/02](./02-Spring-Core-Framework/02-Dependency-Injection-and-Bean-Lifecycle.md) |
| Bean scopes | [02/03](./02-Spring-Core-Framework/03-Bean-Scopes-Component-Scanning-and-Configuration.md) |
| AOP proxy mechanics | [02/04](./02-Spring-Core-Framework/04-AOP-Proxy-Mechanics-and-Spring-Modules.md) |
| Request lifecycle | [01/05](./01-Spring-Boot-Fundamentals/05-Internal-Working-Flow-and-Request-Lifecycle.md) |
| DispatcherServlet strategies | [03/01](./03-Spring-MVC/01-DispatcherServlet-and-Complete-Request-Flow.md) |
| Filter vs Interceptor vs Aspect | [03/04](./03-Spring-MVC/04-Filters-Interceptors-ArgumentResolvers-and-File-Transfer.md) |
| Entity states | [04/02](./04-Spring-Data-JPA-Hibernate/02-Entity-Lifecycle-Persistence-Context-and-Caches.md) |
| Transaction propagation | [04/03](./04-Spring-Data-JPA-Hibernate/03-Transactions-Propagation-and-Isolation.md) |
| N+1 & fetch strategies | [04/04](./04-Spring-Data-JPA-Hibernate/04-Lazy-Eager-N-plus-1-and-Fetch-Strategies.md) |
| Security filter chain | [05/01](./05-Spring-Security/01-Architecture-and-Security-Filter-Chain.md) |
| Password encoding | [05/04](./05-Spring-Security/04-Password-Encoding-Sessions-CSRF-and-CORS.md) |
| JWT structure & algorithms | [06/01](./06-JWT-Authentication/01-JWT-Architecture-Statelessness-and-Signature-Algorithms.md) |
| OAuth2 flows | [07/02](./07-OAuth2-OpenID-Connect/02-Authorization-Code-and-PKCE-Flows.md) |
| Circuit breaker | [08/03](./08-Microservices-with-Spring-Boot/03-Circuit-Breaker-Resilience4j-and-Fault-Tolerance.md) |
| Kafka partitions & ordering | [09/03](./09-Messaging-Systems/03-Consumer-Groups-Partitions-and-Ordering-Guarantees.md) |
| Cache patterns | [10/02](./10-Caching/02-Cache-Aside-Pattern-and-Distributed-Caching.md) |
| Indexing rules | [11/01](./11-Database-Design-and-Optimization/01-Indexing-and-Query-Optimization.md) |
| Isolation levels | [11/02](./11-Database-Design-and-Optimization/02-ACID-Isolation-Levels-and-Locks.md) |
| Connection pool sizing | [11/04](./11-Database-Design-and-Optimization/04-Connection-Pooling-HikariCP-and-Database-Scaling.md) |
| REST API design | [12/01](./12-System-Design-for-Java-Backend/01-API-Design-Best-Practices-and-Scalability-Patterns.md) |
| Test pyramid | [13/01](./13-Testing/01-JUnit5-and-Mockito.md) |
| Docker layered JAR | [14/01](./14-Deployment-and-DevOps/01-Docker-and-Docker-Compose.md) |
| K8s probes | [14/02](./14-Deployment-and-DevOps/02-Kubernetes-Basics.md) |
| Golden signals | [14/04](./14-Deployment-and-DevOps/04-Monitoring-Logging-and-Observability.md) |

---

## 📐 The Documentation Standard

Every chapter here follows [`STANDARD.md`](./STANDARD.md) — read it once and you know exactly
what every file contains and how to review it:

```
TL;DR → What & Why → Mental Model → Deep Dive (4 levels) → Internal Mechanism →
Execution Flow → Visualizations → Production Use Cases → Worked Examples →
Production Code → Mistakes → Performance → Security → Best Practices →
Exercises & Challenges → Level-wise Q&A → Scenarios → Counter-Questions →
Production Issues & Debugging → Architecture Design → MCQs → Cheat Sheet →
Production Notes → Next Steps
```

---

## 📋 Most Asked Topics (the 30-second list)

If you only have 30 seconds, these are the ones that come up again and again:

1. **Dependency Injection** — core concept of Spring, different injection types
2. **Spring Boot Auto-configuration** — how Boot configures beans automatically
3. **REST API Design** — building RESTful services with Spring MVC
4. **Spring Data JPA** — repository pattern and query methods
5. **Spring Security** — authentication, authorization, JWT, OAuth2
6. **Transaction Management** — `@Transactional` annotation and propagation
7. **AOP** — `@Aspect`, `@Before`, `@After`, cross-cutting concerns
8. **Bean Lifecycle & Scopes** — instantiation, callbacks, the five scopes
9. **N+1 Problem** — the most common JPA performance bug in production
10. **Security Filter Chain** — the exact order and what each filter owns

---

## 🔗 Related Folders

| Folder | Contents |
|--------|----------|
| [`CORE_JAVA`](../CORE_JAVA/) | Java language, JVM, concurrency, collections — the foundation this folder assumes |
| [`JAVA_DSA`](../JAVA_DSA/) | Full problem-by-problem DSA solutions |
| [`DATABASE_CACHING`](../DATABASE_CACHING/) | SQL, indexing, Redis, caching patterns (placeholder — the Spring view lives in [10-Caching](./10-Caching/) and [11-Database](./11-Database-Design-and-Optimization/)) |
| [`SYSTEM_DESIGN`](../SYSTEM_DESIGN/) | System design round preparation (placeholder — the Spring view lives in [12-System-Design](./12-System-Design-for-Java-Backend/)) |
| [`CLOUD_DEVOPS`](../CLOUD_DEVOPS/) | Cloud & DevOps (placeholder — the Spring view lives in [14-Deployment-and-DevOps](./14-Deployment-and-DevOps/)) |
| [`DOCKER_JAVA_BACKEND`](../DOCKER_JAVA_BACKEND/) | Containerising Java apps (placeholder — see [14/01](./14-Deployment-and-DevOps/01-Docker-and-Docker-Compose.md)) |

---

## 📝 Conventions

| Marker | Meaning |
|--------|---------|
| 🟢 | Beginner question |
| 🟡 | Intermediate question |
| 🔴 | Advanced / Senior question |
| ⚫ | Expert / Principal question |
| ❌ → ✅ | Common mistake and its fix |
| `▶` | The line an interviewer is listening for |
| 💡 | Real-world production note |
| ⚠️ | Version-specific warning (Spring Boot 3.x differences) |

---

**Maintained for Java backend interviews · Standard: [STANDARD.md](./STANDARD.md) · Spring Boot 3.3+ | Framework 6.1+ | Java 17+ · Updated: 2026**
