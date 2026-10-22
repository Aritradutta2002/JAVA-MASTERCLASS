# 01 · Spring Boot Fundamentals — Interview Notes Index

> Spring Boot is not a new framework — it is **Spring Framework + auto-configuration + starters +
> embedded servers + externalized config + production-ready features**. Every interview asks at
> least one Boot question; the strong candidates can explain **why** the defaults exist and **when**
> to override them.

| Priority | Chapters | Why It Matters |
|----------|----------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · Architecture & Internals](01-Architecture-and-Internals.md) | `refresh()`, `SpringApplication.run()`, `@SpringBootApplication` |
| ⭐⭐⭐⭐⭐ | [02 · Auto-Configuration & Starters](02-Auto-Configuration-and-Starters.md) | **The** Boot question — conditions, back-off, `imports` |
| ⭐⭐⭐⭐⭐ | [05 · Startup & Request Lifecycle](05-Request-Lifecycle-and-Startup-Flow.md) | "Walk me through a request" + "why is startup slow" |
| ⭐⭐⭐⭐⭐ | [03 · Configuration & Profiles](03-Configuration-and-Profiles.md) | 17-source precedence, relaxed binding, secrets |
| ⭐⭐⭐⭐ | [04 · Actuator & Observability](04-Actuator-and-Observability.md) | Health groups, liveness vs readiness, metrics |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| What does `@SpringBootApplication` include? | Forgetting that the `@ComponentScan` root is **the app class's package** — the #1 "my bean is null" cause |
| How does auto-configuration work? | Saying "it scans the classpath" — the mechanism is **import selection + conditional evaluation + property binding** |
| How do I override an auto-configured bean? | By **type**, not by name — `@ConditionalOnMissingBean` matches types |
| Explain the bean lifecycle | Not distinguishing `BeanFactoryPostProcessor` (definitions) from `BeanPostProcessor` (instances) |
| What is the order of property sources? | Guessing — the top 4 are CLI args → `SPRING_APPLICATION_JSON` → env vars → packaged `application.yml` |
| `@Value` vs `@ConfigurationProperties`? | Not mentioning **relaxed binding** (binder-only) and **startup validation** |
| What is the difference between liveness and readiness? | Not saying that a **shared dependency in liveness causes restart storms** |
| Where does the transaction start? | At the **service proxy**, not the controller — and self-invocation breaks it |
| Filter vs interceptor? | Getting the order or the "skips static/error/async" detail wrong |
| How are exceptions handled? | Forgetting the resolver **order** and that a controller-local handler wins over an advice |

---

## 🧠 The One Mental Model

```
┌─────────────────────────────────────────────────────────────────┐
│  Spring Boot = REVERSIBLE DEFAULTS                               │
│                                                                 │
│   "If the classpath suggests you want it, and you haven't       │
│    configured it yourself, I'll configure it sensibly —         │
│    and I'll tell you exactly what I did."                       │
│                                                                 │
│   Starter        = what's ON the classpath                     │
│   Auto-config    = what BEANS get created because of that       │
│   @Conditional   = the GATE that decides                         │
│   Report (--debug) = the AUDIT trail                            │
└─────────────────────────────────────────────────────────────────┘
```

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **The `refresh()` step order** — especially ④⑤ (BFP), ⑥ (BPP), ⑨ (server created), ⑪
      (singletons), ⑫ (server starts).
- [ ] **`@ConditionalOnMissingBean` matching by type** — and the *ordering* problem with
      `@ConditionalOnBean`.
- [ ] **Boot 3's `AutoConfiguration.imports`** vs the removed `spring.factories` key.
- [ ] **`open-in-view=false`** and why it matters for connection-pool exhaustion.
- [ ] **`ddl-auto=validate`** in production, migrations via Flyway.
- [ ] **Health groups** — liveness must contain only `livenessState`.
- [ ] **Metric cardinality** — the #1 metrics incident.
- [ ] **Graceful shutdown** and `SmartLifecycle` phases.

---

## 🧪 Coding Drill

- [ ] Build a `ApplicationContextRunner` test suite for a custom auto-configuration: applied,
      backed-off, and missing-class cases.
- [ ] Bind a `Duration` from `"30s"`, from `"30"` with `@DurationUnit`, and prove that `@Value`
      needs the suffix.
- [ ] Start an app with `--debug` and explain every line under *Negative matches* for
      `DataSourceAutoConfiguration`.
- [ ] Write a filter that assigns a request id, and prove the MDC is cleared by sending two
      requests on the same thread.
- [ ] Add a health group where liveness = `livenessState` only, then stop the DB and observe
      readiness → DOWN, liveness → UP.

---

## ⏱️ Suggested Schedule (Week 1)

```
Mon-Tue   01 Architecture & Internals
Wed       02 Auto-Configuration & Starters
Thu       03 Configuration & Profiles
Fri       04 Actuator & Observability
Sat-Sun   05 Startup & Request Lifecycle  ← the chapter that ties it all together
```

---
**Related folders:** ← [Master Index](../README.md) · → [02 · Spring Core](../02-Spring-Core/README.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md)
