# 01 — Spring Boot Fundamentals · Interview Notes Index

> Spring Boot is **not a new framework** — it is *Spring Framework* with five
> conveniences glued on: an embedded server, curated starters, conditional
> auto-configuration, externalised configuration, and production-ready
> endpoints. Once you can explain **what those five conveniences actually do
> under the hood**, every other Spring topic (MVC, Security, JPA, Microservices)
> becomes a detail instead of a mystery.

| Chapter | Priority | Level | Read Time | Why It Matters |
|---------|----------|-------|-----------|----------------|
| [01 · Core Concepts & Architecture](01-Core-Concepts-and-Architecture.md) | ⭐⭐⭐⭐⭐ | 🟢→🔴 | ~55 min | The vocabulary every other Spring interview question assumes: Boot vs Spring vs Jakarta, the 5 core features, `@SpringBootApplication`, and where the application boundary is drawn. |
| [02 · Auto-Configuration & Conditional Internals](02-Auto-Configuration-and-Conditional-Internals.md) | ⭐⭐⭐⭐⭐ | 🟡→⚫ | ~60 min | **The #1 most-asked Boot question.** How `@Conditional` decides, why `AutoConfiguration.imports` replaced `spring.factories`, and how to debug "my bean was never created". |
| [03 · Starters, Embedded Servers & Configuration Properties](03-Starters-Embedded-Servers-and-Configuration-Properties.md) | ⭐⭐⭐⭐ | 🟢→🔴 | ~50 min | Why a "starter" is usually just a POM, how BOM-managed versions work, Tomcat vs Jetty vs Undertow, and type-safe `@ConfigurationProperties` with validation. |
| [04 · Profiles, Environment, Actuator & DevTools](04-Profiles-Environment-Actuator-and-DevTools.md) | ⭐⭐⭐⭐ | 🟡→🔴 | ~50 min | Property precedence, profile resolution, the Actuator endpoint catalogue and its security traps, and how DevTools' restart classloader actually works. |
| [05 · Internal Working Flow & Request Lifecycle](05-Internal-Working-Flow-and-Request-Lifecycle.md) | ⭐⭐⭐⭐⭐ | 🔴→⚫ | ~70 min | The deepest chapter: `SpringApplication.run` phase by phase, the 12 steps of `refresh()`, the servlet container boot, and the full HTTP request chain down to the byte write. |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| What exactly does Spring Boot *add* to Spring Framework? | Saying "it removes XML" — XML was already optional since Spring 3.x and is **gone entirely** in Boot 3 (no more `spring-beans.xml`). The real answer is the **five conveniences**: embedded server, starters, auto-configuration, externalised config, production-ready endpoints. |
| What does `@SpringBootApplication` actually contain? | Naming the three annotations is only half. You must also say **what each one does** and, crucially, that **the package of the `@SpringBootApplication` class becomes the component-scan base** — which is why `main()` must live *above* your controllers. |
| How does Boot know which beans to auto-configure? | "It scans the classpath." Wrong. It reads `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, then evaluates **`@Conditional`** annotations in a defined order — and it does **not** scan for *your* beans. |
| Why did `spring.factories` break in Boot 3? | Because it was used for *two unrelated things* (auto-config registration **and** general SPI). Boot 3 removed auto-config registration from it and moved it to the dedicated `.imports` file — `spring.factories` still works for other SPIs. |
| Is a starter a jar containing code? | In Boot 3, **most starters contain no code at all** — they are dependency-only POMs. A starter *with* code is a **library** that also carries an `AutoConfiguration.imports` file. |
| Spring Boot vs Spring Framework vs Spring Boot 3 vs Micronaut? | Treating them as competitors. Boot 3 is Spring Framework 6 on **Jakarta EE 10** (a `javax` → `jakarta` package rename), while Micronaut/Quarkus are *different* frameworks that compete on **startup time and memory**, not on feature set. |
| `@Value` or `@ConfigurationProperties`? | Saying "`@Value` is for a single value and `@ConfigurationProperties` for many" — the real difference is **type safety, relaxed binding, validation and IDE metadata**. `@Value` cannot validate and does not support JSR-303. |
| What is the default Actuator web exposure? | Saying "all endpoints". The default is **`health` only**. `management.endpoints.web.exposure.include=*` is a **critical security finding** in most codebases. |
| Why is my `application.yml` value ignored? | 99% of the time it is **property-source precedence** (a command-line arg or env var outranks the file), or the value is in the **wrong profile document**. |
| What happens between the socket accept and the response write? | Stopping at "DispatcherServlet → Controller → Response". The full chain is **Tomcat connector → `CoyoteAdapter` → `StandardWrapperValve` → Spring Security `FilterChainProxy` → `DispatcherServlet` → `HandlerMapping` → `HandlerAdapter` → controller → `HttpMessageConverter` → write**. |

---

## 🧠 The One Picture — Boot Is Spring + 5 Conveniences

```text
        ┌────────────────────────────────────────────────────────────────┐
        │                    SPRING BOOT = SPRING + 5 CONVENIENCES        │
        └────────────────────────────────────────────────────────────────┘

                          ┌───────────────────────────┐
                          │    SPRING FRAMEWORK 6.x   │
                          │  (the IoC container,      │
                          │   DI, AOP, MVC, TX, ...)  │
                          └───────────────────────────┘
                                     ▲
                                     │  everything still works exactly the same
                                     │
   ┌─────────────────────────────────┴─────────────────────────────────┐
   │                                                                   │
   │  ① EMBEDDED SERVER        ② STARTERS              ③ AUTO-CONFIG  │
   │  Tomcat in your fat jar    curated dependency     conditional     │
   │  no WAR deployment         sets + managed         defaults        │
   │                            versions                                     │
   │  ④ EXTERNALISED CONFIG    ⑤ PRODUCTION-READY                        │
   │  12+ config sources,       Actuator: health,                        │
   │  env vars, profiles        metrics, info                            │
   └─────────────────────────────────────────────────────────────────┘

   THE ONE-LINE ANSWER:
   "Boot did not change what Spring does — it removed everything you would
    otherwise have to configure by hand, and it made the defaults sensible."
```

**Caption:** Everything you already know about Spring (IoC, DI, AOP, MVC, transactions)
still applies unchanged. Boot is a *convenience layer* around it — remove the
conveniences and you have plain Spring Framework.

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **The five Boot features, from memory.** If you cannot list embedded server / starters / auto-config / externalised config / Actuator without hesitation, you will lose the opening question of the interview.
- [ ] **`@SpringBootApplication` → its three parts → what each part does.** Most candidates can name the three annotations but cannot explain *why the main class's package matters*.
- [ ] **`AutoConfiguration.imports` vs `spring.factories`.** The Boot 2 → Boot 3 change is a classic "what changed in the last two years" question.
- [ ] **`@ConditionalOnMissingBean` and ordering.** Why a `@Bean` of your own can silently *lose* to an auto-configuration, and what `@AutoConfigureBefore` fixes.
- [ ] **The 12 steps of `AbstractApplicationContext.refresh()`.** At the senior level this separates "I use Spring" from "I know Spring".
- [ ] **Property-source precedence order.** If you cannot rank command-line args vs `application.yml` vs `@PropertySource`, debugging config becomes guesswork.
- [ ] **Actuator exposure defaults.** `health` only over the web — everything else is opt-in, and exposing `*` is a CVE waiting to happen.
- [ ] **Tomcat vs Jetty vs Undertow.** Thread models differ, and Undertow is **deprecated as of Boot 3.4** — do not propose it in a new design.

---

## 🧪 Coding Drill

- [ ] **Prove the embedded server.** Run your app with `-Dserver.port=0`, read the startup log, find the `Tomcat started on port 34961` line, and `curl` the random port. This is the single best 2-minute demonstration of what Boot gave you.
- [ ] **Prove the five features.** For each feature, name the concrete artefact it produced in your project: `spring-boot-starter-web` (starter), `DispatcherServlet` (auto-config), `application.yml` (externalised config), `Tomcat` (embedded server), `/actuator/health` (production-ready).
- [ ] **Read the condition report.** Start your app with `--debug` and open `/actuator/conditions`. Find one auto-configuration that matched and one that did not, and explain *why* in terms of the condition class that fired.
- [ ] **Convert a plain Spring app to Boot.** Take a `web.xml` + `DispatcherServlet` + XML-configured app and rebuild it as a Boot app. Diff the two `pom.xml` files and write down every dependency the Boot version *removed*.
- [ ] **Build a multi-module Maven project.** `api` / `application` / `domain` / `infrastructure`, prove that `domain` does **not** depend on `spring-boot-starter-web`, and write down how you enforced it (BOM vs `maven-enforcer-plugin`).
- [ ] **Trace one request end to end.** Turn on `logging.level.org.springframework.web=DEBUG`, send one `curl`, and annotate every log line with the component that produced it.

---

## 📐 Where These Chapters Sit in the Bigger Picture

```text
   SPRING_BOOT
       │
       ├── 01-Spring-Boot-Fundamentals   ◀── YOU ARE HERE
       │      the vocabulary + the internals that everything else assumes
       │
       ├── 02-Spring-Core-Framework      the IoC container behind Boot
       ├── 03-Spring-MVC                 the DispatcherServlet in detail
       ├── 04-Spring-Data-JPA-Hibernate  persistence
       ├── 05-Spring-Security            the filter chain in front of everything
       ├── 06-JWT-Authentication         stateless auth
       ├── 08-Microservices-with-Spring-Boot   where Boot conventions scale
       └── 13-Testing / 14-Deployment    how you ship and verify it
```

**Related folders:** → [02 · Spring Core & Framework](../02-Spring-Core-Framework/README.md) · ↑ [Master Index](../README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
