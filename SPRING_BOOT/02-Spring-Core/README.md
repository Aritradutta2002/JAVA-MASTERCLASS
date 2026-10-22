# 02 · Spring Core & Framework — Interview Notes Index

> The Spring Framework is the **foundation** every Spring Boot service stands on. Candidates who
> "know Spring" but can't explain the container, the DI resolution algorithm, the bean lifecycle
> or the proxy mechanism are memorising annotations, not understanding the framework.

| Priority | Chapters | Why It Matters |
|----------|----------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · IoC Container & ApplicationContext](01-IoC-Container-and-ApplicationContext.md) | `BeanFactory`, `BeanDefinition`, the container's internals |
| ⭐⭐⭐⭐⭐ | [02 · Dependency Injection](02-Dependency-Injection.md) | Constructor vs field, the resolution algorithm, qualifiers |
| ⭐⭐⭐⭐⭐ | [03 · Bean Lifecycle & Scopes](03-Bean-Lifecycle-and-Scopes.md) | The 10-step order, `SmartLifecycle`, lazy init |
| ⭐⭐⭐⭐⭐ | [04 · AOP & Proxies](04-AOP-and-Proxies.md) | How `@Transactional` actually works, self-invocation |
| ⭐⭐⭐⭐ | [05 · Modules & Internal Architecture](05-Spring-Modules-Overview.md) | The ecosystem map, `refresh()`, auto-config internals |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| What is the IoC container? | Saying "it's a factory" and stopping — the value is the **lifecycle + proxying** |
| Constructor vs setter vs field injection? | Not mentioning **`final`**, testability, and the **half-constructed object** problem |
| How does Spring pick a bean when several match? | Forgetting the **by-name fallback** (the 4th tie-breaker) |
| Explain the bean lifecycle | Mixing up `BeanFactoryPostProcessor` (definitions) and `BeanPostProcessor` (instances) |
| What is a `BeanDefinition`? | Saying "the bean" — it's **metadata**; the object doesn't exist yet |
| How does `@Transactional` work? | Not mentioning the **proxy** and the **self-invocation** failure |
| What are the bean scopes? | Not saying that **`prototype` beans get no `@PreDestroy`** |
| What is the difference between Spring AOP and AspectJ? | Not mentioning the **join-point** limitation (method execution only) |
| What are `BeanFactory` and `ApplicationContext`? | Not saying `ApplicationContext` **extends** it |
| What is a `FactoryBean`? | Not mentioning the `&` prefix to get the factory itself |

---

## 🧠 The One Mental Model

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  Spring = METADATA → INSTANCES → PROXIES                                   │
│                                                                              │
│  @Component / @Bean ──► BeanDefinition ──► BeanPostProcessor ──► PROXY       │
│  (declaration)            (metadata)          (decorate)         (advised)  │
│                                                                              │
│  The container hands out the PROXY.                                         │
│  Everything you annotate (@Transactional, @Cacheable, @Async,              │
│  @PreAuthorize) is an ADVISOR on that proxy.                                │
│  If you bypass the proxy, you bypass the framework.                          │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## ⚠️ Weakest Areas to Brush Up

- [ ] The **exact** `refresh()` step order and what happens in each.
- [ ] The **constructor selection rule** (2 constructors + a no-arg → the no-arg wins!).
- [ ] **`@Qualifier` vs `@Primary`** — and the by-name fallback.
- [ ] The **proxy creation point** (`postProcessAfterInitialization`) and what that means for
      `@PostConstruct`.
- [ ] **Self-invocation** — the single most common AOP bug.
- [ ] `ObjectProvider` vs `@Autowired(required=false)` vs `Optional<T>`.
- [ ] **`prototype` in a `singleton`** — the shared-state bug.
- [ ] `SmartLifecycle#getPhase()` — the only documented ordering contract.
- [ ] The difference between `spring.factories` and `AutoConfiguration.imports` (Boot 3).
- [ ] `Environment` / `PropertySource` ordering and relaxed binding.

---

## 🧪 Coding Drill

- [ ] Write a `DefaultListableBeanFactory` unit test with `registerSingleton` — no Spring context.
- [ ] Prove the constructor-selection rule with 3 constructors (one `@Autowired`, one no-arg).
- [ ] Write an `ApplicationContextRunner` test for a custom auto-configuration (applied + back-off).
- [ ] Demonstrate the self-invocation bug and fix it by splitting the class.
- [ ] Implement a `SmartLifecycle` bean and assert the start order with two other components.
- [ ] Write a `BeanPostProcessor` that logs every bean created and its scope.

---

## ⏱️ Suggested Schedule (Week 2)

```
Mon       01 IoC Container & ApplicationContext
Tue       02 Dependency Injection
Wed       03 Bean Lifecycle & Scopes
Thu       04 AOP & Proxies
Fri-Sat   05 Modules & Internal Architecture
Sun       Review + MCQ sprint
```

---
**Related folders:** ← [Topic 01 · Spring Boot Fundamentals](../01-Spring-Boot-Fundamentals/README.md) · → [Topic 03 · Spring MVC](../03-Spring-MVC/README.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md)
