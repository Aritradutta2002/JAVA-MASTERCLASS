# 🚀 SPRING_BOOT — The 3–4+ Year Java Backend Interview Roadmap

> **The production-grade Spring Boot masterclass for mid-level and senior Java backend interviews.**
>
> 14 topics · 72 deep-dive chapters · 1,200+ interview questions with expert answers ·
> counter-questions · architecture diagrams · sequence diagrams · hands-on labs · code challenges.
>
> 📌 **Every chapter follows the [Interview Notes Standard](./STANDARD.md)** — so you always know
> exactly what you will find: TL;DR → theory → internals → visuals → production code → pitfalls →
> level-wise Q&A → **counter-questions** → MCQs → cheat sheet.

**Target version:** Spring Boot **3.3.x** · Spring Framework **6.1.x** · Java **17 / 21** · Jakarta EE 10
**Style:** 3–4+ years of real production experience, written the way a senior engineer would brief a mid-level hire.

---

## 🎯 Who This Is For

| You are… | Start here | Reality check |
|----------|-----------|---------------|
| 🟢 **Fresher / 0–1 yr** | [01 · Spring Boot Fundamentals](./01-Spring-Boot-Fundamentals/README.md) → [02 · Spring Core](./02-Spring-Core/README.md) → [03 · Spring MVC](./03-Spring-MVC/README.md) | Learn the *vocabulary*. You will be asked "what is a bean?", "what does `@Transactional` do?", "how does Spring Boot start?" — nothing deeper. |
| 🟡 **Mid / 2–4 yr** (this repo's sweet spot) | Everything, in order. Priority: [04 · JPA](./04-Spring-Data-JPA-Hibernate/README.md), [05 · Security](./05-Spring-Security/README.md), [06 · JWT](./06-JWT-Authentication/README.md), [12 · System Design](./12-System-Design-for-Java-Backend/README.md) | This is exactly the level the roadmap targets. Expect "why" questions, not "what". |
| 🔴 **Senior / 5–8 yr** | [08 · Microservices](./08-Microservices-with-Spring-Boot/README.md), [09 · Messaging](./09-Messaging-Systems/README.md), [11 · DB Optimisation](./11-Database-Design-Optimization/README.md), [14 · DevOps](./14-Deployment-DevOps/README.md) | You will be asked to *design and defend* trade-offs under production constraints. |
| ⚫ **Mock interview tonight** | **🔥 The Question Bank** below + the Counter-Questions section of every chapter | Practice the **naive → trap → expert** answer structure. That structure alone wins interviews. |
| 🏃 **30-minute refresher** | **🔥 The Question Bank** below → **🧰 One-Page Cheat Sheets** below | Screenshots-and-recall. Read this the morning of the interview. |

---

## 📚 Topic Index — The 14 Areas

| # | Topic | Chapters | Level | Priority | Index |
|---|-------|----------|-------|----------|-------|
| 01 | **Spring Boot Fundamentals** | 5 | 🟢→🔴 | ⭐⭐⭐⭐⭐ | [Open](./01-Spring-Boot-Fundamentals/README.md) |
| 02 | **Spring Core & Framework** | 5 | 🟢→🔴 | ⭐⭐⭐⭐⭐ | [Open](./02-Spring-Core/README.md) |
| 03 | **Spring MVC** | 5 | 🟢→🔴 | ⭐⭐⭐⭐⭐ | [Open](./03-Spring-MVC/README.md) |
| 04 | **Spring Data JPA & Hibernate** | 6 | 🟢→🔴 | ⭐⭐⭐⭐⭐ | [Open](./04-Spring-Data-JPA-Hibernate/README.md) |
| 05 | **Spring Security** | 7 | 🟢→⚫ | ⭐⭐⭐⭐⭐ | [Open](./05-Spring-Security/README.md) |
| 06 | **JWT Authentication** | 4 | 🟡→⚫ | ⭐⭐⭐⭐⭐ | [Open](./06-JWT-Authentication/README.md) |
| 07 | **OAuth2 & OpenID Connect** | 6 | 🟡→⚫ | ⭐⭐⭐⭐ | [Open](./07-OAuth2-OIDC/README.md) |
| 08 | **Microservices with Spring Boot** | 6 | 🟡→⚫ | ⭐⭐⭐⭐⭐ | [Open](./08-Microservices-with-Spring-Boot/README.md) |
| 09 | **Messaging Systems** | 4 | 🟡→🔴 | ⭐⭐⭐⭐⭐ | [Open](./09-Messaging-Systems/README.md) |
| 10 | **Caching** | 4 | 🟡→🔴 | ⭐⭐⭐⭐⭐ | [Open](./10-Caching/README.md) |
| 11 | **Database Design & Optimisation** | 5 | 🟡→🔴 | ⭐⭐⭐⭐⭐ | [Open](./11-Database-Design-Optimization/README.md) |
| 12 | **System Design for Java Backend** | 6 | 🟡→⚫ | ⭐⭐⭐⭐⭐ | [Open](./12-System-Design-for-Java-Backend/README.md) |
| 13 | **Testing** | 4 | 🟢→🔴 | ⭐⭐⭐⭐ | [Open](./13-Testing/README.md) |
| 14 | **Deployment & DevOps** | 5 | 🟡→🔴 | ⭐⭐⭐⭐ | [Open](./14-Deployment-DevOps/README.md) |

---

## 🔥 The Question Bank — Top 50 Must-Master Questions

> **How to use it:** never memorise. For each question say the **naive** answer first, then name
> the **trap**, then give the **expert** answer. Interviewers grade the third part.

| # | Question | Chapter | Trap in the naive answer |
|---|----------|---------|--------------------------|
| 1 | What happens between `main()` and the first HTTP request? | [01/05](./01-Spring-Boot-Fundamentals/05-Request-Lifecycle-and-Startup-Flow.md) | Saying "it starts Tomcat" — misses the `ApplicationContext` refresh, `BeanFactoryPostProcessor` and `ApplicationRunner` phases |
| 2 | How does Spring Boot know to configure Tomcat + JPA + Jackson? | [01/02](./01-Spring-Boot-Fundamentals/02-Auto-Configuration-and-Starters.md) | "Because of the starter" — the truth is `AutoConfiguration.imports` + `@ConditionalOn*` back-off |
| 3 | What is a `SpringApplication.run()` doing in phases? | [01/01](./01-Spring-Boot-Fundamentals/01-Architecture-and-Internals.md) | Treating it as a single method call |
| 4 | What is the difference between a starter and a dependency? | [01/02](./01-Spring-Boot-Fundamentals/02-Auto-Configuration-and-Starters.md) | Confusing "starter = jar with deps" with "starter = auto-config + deps" |
| 5 | How are properties resolved, and what wins? | [01/03](./01-Spring-Boot-Fundamentals/03-Configuration-and-Profiles.md) | Not knowing the 17-level precedence order |
| 6 | Why is `@Value` bad and `@ConfigurationProperties` good? | [01/03](./01-Spring-Boot-Fundamentals/03-Configuration-and-Profiles.md) | Missing relaxed binding and type-safe validation |
| 7 | What does the IOC container actually do? | [02/01](./02-Spring-Core/01-IoC-Container-and-ApplicationContext.md) | "It creates objects" — misses `BeanDefinition` → `BeanFactory` → `registerBeanPostProcessors` |
| 8 | Constructor vs setter vs field injection? | [02/02](./02-Spring-Core/02-Dependency-Injection.md) | Not mentioning that field injection breaks immutability and testability |
| 9 | Full bean lifecycle — all 15+ stages? | [02/03](./02-Spring-Core/03-Bean-Lifecycle-and-Scopes.md) | Forgetting `BeanPostProcessor` vs `BeanFactoryPostProcessor` |
| 10 | `@PostConstruct` vs `InitializingBean` vs `init-method`? | [02/03](./02-Spring-Core/03-Bean-Lifecycle-and-Scopes.md) | Not knowing the *order* |
| 11 | How does Spring AOP work? Proxy or weaving? | [02/04](./02-Spring-Core/04-AOP-and-Proxies.md) | Not knowing the CGLIB vs JDK proxy selection rule |
| 12 | Why doesn't `@Transactional` work on a private method? | [04/05](./04-Spring-Data-JPA-Hibernate/05-Transactions-and-Query-Optimization.md) | Proxy-based AOP cannot advise private/self-invocation |
| 13 | How does `DispatcherServlet` find a controller? | [03/01](./03-Spring-MVC/01-Request-Flow-and-DispatcherServlet.md) | "Spring scans and maps" — missing `RequestMappingHandlerMapping` |
| 14 | Filter vs Interceptor — when does each run? | [03/05](./03-Spring-MVC/05-Filters-Interceptors-and-Files.md) | Getting the order wrong |
| 15 | `@ControllerAdvice` vs `@ExceptionHandler`? | [03/04](./03-Spring-MVC/04-Exception-Handling.md) | Not knowing the `ExceptionHandlerExceptionResolver` order |
| 16 | What is a `BeanFactory` vs `ApplicationContext`? | [02/01](./02-Spring-Core/01-IoC-Container-and-ApplicationContext.md) | Not mentioning that `ApplicationContext` *is* a `BeanFactory` + more |
| 17 | What is the N+1 problem and how do you fix it? | [04/04](./04-Spring-Data-JPA-Hibernate/04-Caching-Fetching-and-N-plus-1.md) | Saying "use `@EntityGraph`" without mentioning join fetch + pagination trap |
| 18 | First-level vs second-level cache? | [04/04](./04-Spring-Data-JPA-Hibernate/04-Caching-Fetching-and-N-plus-1.md) | Not mentioning that L1 is the persistence context itself |
| 19 | `@Transactional` propagation — REQUIRES_NEW vs NESTED? | [04/05](./04-Spring-Data-JPA-Hibernate/05-Transactions-and-Query-Optimization.md) | Confusing `NESTED` (savepoint) with `REQUIRES_NEW` (new tx) |
| 20 | How does Spring Security's filter chain work? | [05/01](./05-Spring-Security/01-Security-Architecture-and-Filter-Chain.md) | Forgetting `SecurityContextHolder` + `SecurityContextPersistenceFilter` |
| 21 | Authentication vs Authorization? | [05/02](./05-Spring-Security/02-Authentication-Flow-and-UserDetails.md) | Not naming `AuthenticationManager` / `AuthenticationProvider` |
| 22 | `UserDetailsService` vs `AuthenticationProvider`? | [05/02](./05-Spring-Security/02-Authentication-Flow-and-UserDetails.md) | Treating them as the same interface |
| 23 | Why `BCrypt` over `SHA-256`? | [05/07](./05-Spring-Security/07-Security-Best-Practices.md) | Saying "because it's slow" without mentioning **salt** and **cost factor** |
| 24 | CSRF vs CORS — what is the difference? | [05/04](./05-Spring-Security/04-CORS-CSRF-and-Session-Management.md) | Conflating them; CORS is *not* a security control against CSRF |
| 25 | What are the 3 parts of a JWT? | [06/01](./06-JWT-Authentication/01-JWT-Architecture-and-Anatomy.md) | Not explaining why base64url and *not* encryption |
| 26 | HS256 vs RS256 — when do you need asymmetric? | [06/01](./06-JWT-Authentication/01-JWT-Architecture-and-Anatomy.md) | Not explaining the key-distribution problem |
| 27 | Access token vs refresh token — why both? | [06/03](./06-JWT-Authentication/03-Access-Refresh-Tokens-and-Rotation.md) | Not knowing refresh token reuse detection |
| 28 | How do you revoke a JWT? | [06/04](./06-JWT-Authentication/04-Security-Risks-and-Production-Design.md) | Only listing "short expiry" — that's mitigation, not revocation |
| 29 | What is `alg: none` and why is it a vuln? | [06/04](./06-JWT-Authentication/04-Security-Risks-and-Production-Design.md) | Not knowing the classic attack chain |
| 30 | Authorization Code vs PKCE vs Implicit? | [07/01](./07-OAuth2-OIDC/01-OAuth2-Architecture-and-Fundamentals.md) | Not knowing that Implicit is deprecated |
| 31 | What does the OIDC `id_token` contain? | [07/04](./07-OAuth2-OIDC/04-OpenID-Connect-and-Social-Login.md) | Confusing `id_token` with `access_token` |
| 32 | Monolith vs microservices — when do you NOT split? | [08/01](./08-Microservices-with-Spring-Boot/01-Monolith-vs-Microservices-and-When-to-Use-Which.md) | "Microservices are always better" |
| 33 | How does service discovery work? | [08/03](./08-Microservices-with-Spring-Boot/03-Service-Discovery-Config-and-API-Gateway.md) | Not mentioning client-side vs server-side load balancing |
| 34 | What does a circuit breaker actually do? | [08/04](./08-Microservices-with-Spring-Boot/04-Circuit-Breaker-and-Resilience.md) | Not describing the CLOSED → OPEN → HALF_OPEN state machine |
| 35 | What is the difference between a trace, a span and a metric? | [08/05](./08-Microservices-with-Spring-Boot/05-Distributed-Tracing-and-Observability.md) | Mixing them up |
| 36 | Kafka partition ordering — what is guaranteed? | [09/01](./09-Messaging-Systems/01-Kafka-Architecture-and-Fundamentals.md) | Not knowing that ordering is only *within* a partition |
| 37 | RabbitMQ vs Kafka — when do you use which? | [09/03](./09-Messaging-Systems/03-RabbitMQ-and-Message-Queue-Patterns.md) | Saying "Kafka is faster" without the throughput/latency/ordering trade-off |
| 38 | How do you make a consumer idempotent? | [09/04](./09-Messaging-Systems/04-Reliable-Messaging-and-Event-Driven-Architecture.md) | Not mentioning the idempotency key + unique constraint |
| 39 | Cache-aside vs write-through vs write-behind? | [10/01](./10-Caching/01-Caching-Fundamentals-and-Patterns.md) | Not mentioning the double-delete / race condition |
| 40 | What problem does a B-tree index solve? | [11/02](./11-Database-Design-Optimization/02-Indexing-Deep-Dive.md) | Saying "it makes lookups O(log n)" without the I/O cost reasoning |
| 41 | What is a write-ahead log? | [11/02](./11-Database-Design-Optimization/02-Indexing-Deep-Dive.md) | Not mentioning WAL + checkpoints + MVCC |
| 42 | How do you detect and fix a deadlock? | [11/04](./11-Database-Design-Optimization/04-Transactions-Locking-and-Isolation.md) | Not naming `SHOW ENGINE INNODB STATUS` / `pg_locks` |
| 43 | How many DB connections should a service have? | [11/05](./11-Database-Design-Optimization/05-Scaling-Partitioning-and-Replication.md) | Saying "more is better" — the formula is `cores × 2 + spindles` |
| 44 | Design a URL shortener. | [12/03](./12-System-Design-for-Java-Backend/03-Classic-Design-Problems.md) | Not mentioning the 62⁶ key space + base62 encoding |
| 45 | Design a rate limiter. | [12/03](./12-System-Design-for-Java-Backend/03-Classic-Design-Problems.md) | Not mentioning token bucket vs sliding window vs leaky bucket |
| 46 | Testcontainers vs mocked repository? | [13/03](./13-Testing/03-Testcontainers-and-Contract-Testing.md) | Mocking the DB in an "integration" test |
| 47 | What is a JVM container memory limit and how do you set `-Xmx`? | [14/01](./14-Deployment-DevOps/01-Containerizing-Spring-Boot-with-Docker.md) | Not explaining the container-aware heap sizing (`MaxRAMPercentage`) |
| 48 | What is a readiness probe vs a liveness probe? | [14/02](./14-Deployment-DevOps/02-Kubernetes-for-Java-Developers.md) | Using liveness for readiness — causes restart loops during dependency outages |
| 49 | Logs vs metrics vs traces — when do you use which? | [14/04](./14-Deployment-DevOps/04-Observability-SRE-and-Incident-Response.md) | Not knowing the "use a metric to alert, a trace to debug" rule |
| 50 | How do you make a Spring Boot app safe to deploy? | [14/03](./14-Deployment-DevOps/03-CICD-and-Release-Strategies.md) | Not mentioning blue/green, canary, and graceful shutdown |

---

## 🧭 The Counter-Question Technique

The single biggest difference between a mid-level and a senior candidate is **how they handle
push-back**. Every chapter here has a **"Counter-Questions & Traps"** section with **8+ probes**:

| Probe type | Example | How to answer |
|-----------|---------|---------------|
| **"Why not X?"** | "Isn't `@EntityGraph` enough? Why a projection?" | Naive answer → name the trap → expert answer |
| **"What if…?"** | "What happens if Redis is down?" | Trace the exact code path and the fallback |
| **"How would you find out?"** | "How do you diagnose this memory leak?" | Name the tool **and** the signal you expect |
| **"Is that true?"** | "Is `@Transactional` guaranteed to roll back?" | Correct the premise, then state the precise guarantee |

---

## 🗺️ The 8-Week Master Plan

```
WEEK 1 — Foundations
  Mon-Tue  01 Spring Boot Fundamentals (01→05)
  Wed-Thu  02 Spring Core (01→05)
  Fri-Sun  03 Spring MVC (01→05)

WEEK 2 — Persistence & Transactions
  Mon-Tue  04 JPA (01→03)
  Wed-Thu  04 JPA (04→06)
  Fri-Sun  11 Database Design (01→05)

WEEK 3 — Security
  Mon-Wed  05 Spring Security (01→07)
  Thu-Fri  06 JWT (01→04)
  Sat-Sun  07 OAuth2 / OIDC (01→06)

WEEK 4 — Distributed Systems
  Mon-Tue  08 Microservices (01→04)
  Wed-Thu  08 Microservices (05→06) + 09 Messaging (01→02)
  Fri-Sun  09 Messaging (03→04) + 10 Caching (01→04)

WEEK 5 — Design
  Mon-Tue  12 System Design (01→04)
  Wed-Thu  12 System Design (05→06)
  Fri-Sun  13 Testing (01→04)

WEEK 6 — Production
  Mon-Wed  14 DevOps (01→05)
  Thu-Fri  Full mock interview #1 (60 min, recorded)
  Sat-Sun  Fix every question you stumbled on

WEEK 7 — Hardening
  Mon-Wed  Re-read all Counter-Questions sections
  Thu-Fri  Mock interview #2 (system-design heavy)
  Sat-Sun  Rewrite 3 systems from scratch on paper

WEEK 8 — Final Polish
  Mon-Wed  One-Page Cheat Sheets drills
  Thu     Mock interview #3 (behavioural + system design)
  Fri-Sun  Rest. Seriously. Sleep is a technical skill.
```

### Daily Mechanics (90 min)

```
15 min  Read ONE chapter's TL;DR + Cheat Sheet
30 min  Read the Deep Dive + trace every diagram OUT LOUD
15 min  Answer the Counter-Questions WITHOUT looking
15 min  Do the Rapid-Fire MCQs (closed-book)
15 min  Write & run one code sample; predict the output BEFORE running
```

---

## 🧰 One-Page Cheat Sheets

| Topic | Cheat Sheet |
|-------|-------------|
| Auto-config back-off & conditionals | [01/02](./01-Spring-Boot-Fundamentals/02-Auto-Configuration-and-Starters.md) |
| Bean lifecycle (all stages) | [02/03](./02-Spring-Core/03-Bean-Lifecycle-and-Scopes.md) |
| AOP proxy rules | [02/04](./02-Spring-Core/04-AOP-and-Proxies.md) |
| DispatcherServlet resolution order | [03/01](./03-Spring-MVC/01-Request-Flow-and-DispatcherServlet.md) |
| Filter vs Interceptor | [03/05](./03-Spring-MVC/05-Filters-Interceptors-and-Files.md) |
| N+1 fixes | [04/04](./04-Spring-Data-JPA-Hibernate/04-Caching-Fetching-and-N-plus-1.md) |
| Transaction propagation table | [04/05](./04-Spring-Data-JPA-Hibernate/05-Transactions-and-Query-Optimization.md) |
| Security filter chain | [05/01](./05-Spring-Security/01-Security-Architecture-and-Filter-Chain.md) |
| JWT attack matrix | [06/04](./06-JWT-Authentication/04-Security-Risks-and-Production-Design.md) |
| OAuth2 flow selection | [07/01](./07-OAuth2-OIDC/01-OAuth2-Architecture-and-Fundamentals.md) |
| Circuit breaker states | [08/04](./08-Microservices-with-Spring-Boot/04-Circuit-Breaker-and-Resilience.md) |
| Kafka vs RabbitMQ | [09/03](./09-Messaging-Systems/03-RabbitMQ-and-Message-Queue-Patterns.md) |
| Cache patterns | [10/01](./10-Caching/01-Caching-Fundamentals-and-Patterns.md) |
| Isolation levels | [11/04](./11-Database-Design-Optimization/04-Transactions-Locking-and-Isolation.md) |
| HikariCP sizing | [11/05](./11-Database-Design-Optimization/05-Scaling-Partitioning-and-Replication.md) |
| System design checklist | [12/01](./12-System-Design-for-Java-Backend/01-System-Design-Framework.md) |
| JUnit 5 + Mockito traps | [13/02](./13-Testing/02-Unit-and-Slice-Testing.md) |
| Container JVM flags | [14/01](./14-Deployment-DevOps/01-Containerizing-Spring-Boot-with-Docker.md) |

---

## 📐 The Documentation Standard

Every chapter follows [`STANDARD.md`](./STANDARD.md) — read it once and you know exactly what every
file contains and how to review it:

```
TL;DR → What & Why → Mental Model → Deep Dive → Internal Working →
Visualizations → Production Code → Pitfalls → Performance → Security →
Best Practices → Hands-On → Challenges → Scenarios → Q&A by level →
Counter-Questions → MCQs → Cheat Sheet → Production Notes → Next Steps
```

---

## 🔗 Related Folders

| Folder | Contents |
|--------|----------|
| [`CORE_JAVA`](../CORE_JAVA/) | Java language deep-dives — the foundation this roadmap assumes |
| [`SPRING_SPRINGBOOT`](../SPRING_SPRINGBOOT/) | The original 7-topic Spring index (superseded by this folder) |
| [`DATABASE_CACHING`](../DATABASE_CACHING/) | SQL, indexing, Redis, caching patterns |
| [`SYSTEM_DESIGN`](../SYSTEM_DESIGN/) | System design round preparation |
| [`CLOUD_DEVOPS`](../CLOUD_DEVOPS/) | Docker, K8s, CI/CD |
| [`DOCKER_JAVA_BACKEND`](../DOCKER_JAVA_BACKEND/) | Containerising Java apps |
| [`JAVA_DSA`](../JAVA_DSA/) | Full problem-by-problem DSA solutions |

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
| 🔥 | "This one is asked in almost every interview" |
| 🧪 | Hands-on / lab |
| 🏗️ | Architecture design question |

---

**Maintained for Java interviews · Standard: [STANDARD.md](./STANDARD.md) · Spring Boot 3.3 · Updated: 2026**
