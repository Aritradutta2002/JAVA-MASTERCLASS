# 02 — Spring Core & Framework · Interview Notes Index

> Spring Boot is **not** magic — it is Spring Framework plus a classpath-based
> auto-configuration layer. The single idea that makes the whole framework click:
> **the container owns a registry of `BeanDefinition`s (recipes), creates beans from
> them, wires them by type, proxies them, and destroys them in dependency order.**
> Everything in this folder — scopes, injection, AOP, configuration — is a detail
> of that one mechanism.

| Priority | Chapters | Level | Read Time | Why It Matters |
|----------|----------|-------|-----------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · IoC Container & ApplicationContext](01-IoC-Container-and-ApplicationContext.md) | 🟢→🔴 | 45 min | The container itself: `BeanFactory` vs `ApplicationContext`, `BeanDefinition`, the 3-level singleton cache, the 12-step `refresh()`, the event bus |
| ⭐⭐⭐⭐⭐ | [02 · Dependency Injection & Bean Lifecycle](02-Dependency-Injection-and-Bean-Lifecycle.md) | 🟢→⚫ | 50 min | Constructor vs setter vs field, `@Autowired` resolution, circular dependencies, **every** lifecycle callback in order, `@PreDestroy` vs `DisposableBean` |
| ⭐⭐⭐⭐⭐ | [03 · Bean Scopes, Component Scanning & Configuration](03-Bean-Scopes-Component-Scanning-and-Configuration.md) | 🟡→⚫ | 45 min | The 6 scopes and who destroys them, the prototype-in-singleton trap, `@ComponentScan` filters, `@Configuration` full vs lite mode, the 3 extension points |
| ⭐⭐⭐⭐⭐ | [04 · AOP, Proxy Mechanics & Spring Modules](04-AOP-Proxy-Mechanics-and-Spring-Modules.md) | 🔴→⚫ | 50 min | Advice order, JDK proxy vs CGLIB, the interceptor chain, **self-invocation**, and the Spring module dependency map |

> **Total:** ~4 chapters · 190 min of reading · 70+ interview questions · 55+ counter-questions · 48 MCQs · 16+ production debugging scenarios

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| `BeanFactory` vs `ApplicationContext`? | Listing interfaces instead of explaining `BeanDefinition` → `BeanDefinitionRegistry` → the 3 singleton caches, and what the facade **adds** (lifecycle, events, resources, i18n, environment, AOP integration) |
| Constructor vs setter vs field injection? | "Constructor is best" — without noting that **field injection breaks the proxy-based AOP contract** and leaves the object invalid between construction and injection |
| Explain the full bean lifecycle | Missing the `BeanPostProcessor` phase — post-processors are **not** lifecycle callbacks, and the **proxy is created in `postProcessAfterInitialization`**, i.e. *last* |
| How does Spring resolve a circular dependency? | "It doesn't" — the real answer is **3 levels of early reference**, and it only works for **singletons + setter/field** injection |
| What are the bean scopes and who destroys them? | Forgetting that the container **does not destroy prototypes** — `registerDisposableBeanIfNecessary` skips them explicitly |
| Why isn't my prototype a new instance per call? | It *is* — unless it is **injected into a singleton**, in which case it is created once and held forever |
| Why is my bean registered twice? | Not "duplicate `@Component`s" — it is **overlapping component scans** (`@SpringBootApplication` already scans your package) |
| `proxyBeanMethods = true` vs `false`? | "For performance" — it is for **inter-bean method interception**: in lite mode an inter-`@Bean` call returns a **different instance** |
| `BeanFactoryPostProcessor` vs `BeanPostProcessor`? | Swapping them — one mutates **definitions**, the other mutates **instances** |
| JDK dynamic proxy vs CGLIB? | "CGLIB for classes, JDK for interfaces" — the rule is: **JDK proxy if the target implements ≥ 1 interface**, else CGLIB |
| Why doesn't `@Transactional` work on a self-invoked method? | "Because of the proxy" — without saying **the call never leaves the target, so the advice never runs** |
| What are the 5 advice types and their order? | Listing them without the order: `@Around` → `@Before` → target → `@AfterReturning`/`@AfterThrowing` → `@After` (finally) → `@Around` |
| `BeanDefinitionRegistryPostProcessor` vs `BeanFactoryPostProcessor` vs `BeanPostProcessor`? | Treating them as interchangeable — they form a **strictly decreasing** power set: add definitions → mutate definitions → replace instances |
| `@Import` vs `@ComponentScan`? | Not knowing that `@Import` has **three shapes** (configuration class, `ImportSelector`, `ImportBeanDefinitionRegistrar`) and that `DeferredImportSelector` is what makes Boot auto-configuration work |

---

## 🧠 The Spring Core, in One Picture

```text
                        ┌───────────────────────────────────────────────┐
                        │            ApplicationContext                │
                        │  facade: lifecycle · events · resources ·     │
                        │          i18n · environment · AOP integration  │
                        └───────────────────────┬───────────────────────┘
                                        │ extends
                        ┌───────────────▼───────────────────────┐
                        │        BeanFactory (the container)      │
                        │                                       │
                        │  ┌─────────────────────────────────┐  │
                        │  │  BeanDefinitionRegistry         │  │
                        │  │  beanDefinitionMap              │  │
                        │  │  "orderService" → BeanDefinition │  │
                        │  └───────────────┬─────────────────┘  │
                        │                  │                    │
                        │            ┌─────▼────────────────────┐│
                        │            │  DefaultListableBean-    ││
                        │            │  Factory                 ││
                        │            │  ┌────────────────────┐  ││
                        │            │  │ 3 singleton caches  │  ││
                        │            │  │ ① singletonObjects │  ││
                        │            │  │ ② earlySingletonObj│  ││
                        │            │  │ ③ singletonFactories│ ││
                        │            │  └────────────────────┘  ││
                        │            └──────────────────────────┘│
                        └───────────────────────────────────────┘

   THE THREE THINGS YOU MUST BE ABLE TO EXPLAIN
   ═══════════════════════════════════════════════

   ① WIRING      "who gets what"     → BeanDefinition + constructor/setter/field injection
   ② LIFECYCLE   "when"              → instantiate → populate → initialise → proxy → destroy
   ③ PROXYING    "how advice runs"   → JDK proxy (interfaces) or CGLIB (subclass)

   ★ THE ONE-LINE VERSION FOR THE INTERVIEW
   ──────────────────────────────────────────
   "The container stores BeanDefinitions (recipes), creates beans from them,
    injects their collaborators by type, wraps eligible beans in a proxy so
    cross-cutting advice can run, and destroys them in reverse dependency
    order at shutdown."
```

> **Caption:** `ApplicationContext` **extends** `BeanFactory` — it is a facade over
> the container, not a wrapper around one. The container stores **recipes**
> (`BeanDefinition`s) and **products** (the three singleton caches), and the three
> things you must be able to explain in an interview — **wiring**, **lifecycle** and
> **proxying** — are all consequences of that single registry.

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **The 3-level singleton cache** — `singletonObjects` → `earlySingletonObjects` → `singletonFactories`. If you cannot draw this from memory, the circular-dependency question is a coin flip.
- [ ] **The full lifecycle order** — especially *where* `@PostConstruct` sits relative to the `Aware` callbacks and the proxy, and *why* `@PostConstruct` runs before the proxy exists.
- [ ] **`@Autowired` resolution order** — type → `@Primary` → `@Qualifier` → **name match** → `NoUniqueBeanDefinitionException`; and that `NoUniqueBeanDefinitionException` **extends** `NoSuchBeanDefinitionException`.
- [ ] **The prototype-in-singleton trap** — three causes (shared instance, no destruction, GC blocked by the strong reference) and three fixes (`ObjectProvider`, scoped proxy, `BeanPostProcessor` tracker).
- [ ] **Component scanning overlap** — how `@SpringBootApplication`'s own scan plus a wider `@ComponentScan` produces `BeanDefinitionOverrideException`.
- [ ] **`@Configuration` full vs lite mode** — what CGLIB does for you, and what silently breaks when you turn it off.
- [ ] **The JDK-vs-CGLIB decision rule** and the two failure modes it causes (`final` class; a method not on the interface).
- [ ] **Self-invocation** — the four fixes, and which of `@Transactional` / `@Cacheable` / `@Async` it breaks.
- [ ] **The three extension points and their order** — including `PriorityOrdered` vs `Ordered` vs unordered.
- [ ] **The Spring module map** — which artifact owns `@Transactional`, `@Value`, `@ComponentScan` and the event system.

---

## 🧪 Coding Drill

- [ ] Write a bean implementing `BeanNameAware`, `BeanFactoryAware`, `ApplicationContextAware`, `InitializingBean` and `DisposableBean`, with `@PostConstruct` and `@PreDestroy` — **predict** the callback order, then run it and diff.
- [ ] Break a **constructor** circular dependency and capture the exact `BeanCurrentlyInCreationException` message; then fix it with `@Lazy` and explain what the injected object actually is.
- [ ] Implement a `BeanDefinitionRegistryPostProcessor` that registers one bean definition per entry in a properties file — and prove they are `lazyInit`.
- [ ] Switch a `@Configuration` class between `proxyBeanMethods = true` and `false` and observe the **identity** of the objects returned by an inter-`@Bean` call.
- [ ] Write an `@Around` aspect that times `com.example.service..*` and prove with `AopUtils.isJdkDynamicProxy` / `isCglibProxy` which proxy type was chosen for an interface-backed bean and a class-only bean.
- [ ] Reproduce the **self-invocation** bug and assert `TransactionSynchronizationManager.isActualTransactionActive()` is `false` — then fix it and assert `true`.
- [ ] Write a startup assertion that fails if any `@Transactional` bean is **not** proxied (`AopUtils.isAopProxy`) — this catches `final` classes and a missing `@EnableAspectJAutoProxy`.
- [ ] Build a `SmartLifecycle` pair (consumer phase 300, `DataSource` phase 100) and prove the **stop** order is the reverse of the start order.
- [ ] Print `beanFactory.getBeanDefinitionNames()` with scope + lazy flag at startup, and diff the output between two releases to catch a scan regression.

---

**Related folders:** ← [01 · Spring Boot Fundamentals](../01-Spring-Boot-Fundamentals/README.md) · → [03 · Spring MVC](../03-Spring-MVC/README.md) · ↑ [Master Index](../README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
