# 03 — Spring MVC · Interview Notes Index

> Spring MVC is where a Java backend interview is actually won or lost. The one idea that makes it
> click: **`DispatcherServlet` is a strategy plug-point.** It holds one instance of each of the nine
> strategy families and asks them, in order, three questions — *which handler? how do I call it?
> what if it breaks?* Every behaviour you can customise in Spring MVC — argument binding, content
> negotiation, error mapping, file transfer, correlation ids — is the answer to one of those
> questions at one of four levels: **container (`Filter`) → MVC (`HandlerInterceptor`) → method
> (`@Aspect`) → reflection (argument resolvers and return-value handlers).**

| Priority | Chapters | Level | Read Time | Why It Matters |
|----------|----------|-------|-----------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · DispatcherServlet & Complete Request Flow](01-DispatcherServlet-and-Complete-Request-Flow.md) | Beginner → Expert | ~35 min | The 9 strategies, the 8 steps of `doDispatch`, the async model — asked in nearly every round |
| ⭐⭐⭐⭐⭐ | [02 · Controllers, REST APIs & Content Negotiation](02-Controllers-REST-APIs-and-Content-Negotiation.md) | Beginner → Advanced | ~30 min | `ResponseEntity`, converters, pagination, versioning, and the DTO boundary that keeps entities out of the contract |
| ⭐⭐⭐⭐⭐ | [03 · Validation & Global Exception Handling](03-Validation-and-Global-Exception-Handling.md) | Beginner → Advanced | ~35 min | `@Valid` vs `@Validated`, the resolver chain, and the RFC 7807 error contract every client codes against |
| ⭐⭐⭐⭐ | [04 · Filters, Interceptors, Argument Resolvers & File Transfer](04-Filters-Interceptors-ArgumentResolvers-and-File-Transfer.md) | Intermediate → Advanced | ~40 min | The three cross-cutting levels and their exact order — the classic MVC deep-dive question |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| What are the 9 `DispatcherServlet` strategies? | Listing only `HandlerMapping` / `HandlerAdapter` / `ViewResolver`. The real nine also include `MultipartResolver`, `LocaleResolver`, `RequestToViewNameTranslator`, `FlashMapManager` and `ThemeResolver` — and they are all loaded from `DispatcherServlet.properties` |
| `Filter` vs `Interceptor` vs `Aspect` — when does each run? | Claiming AOP runs "around everything". It wraps **only the controller method** — it cannot see argument resolution or the serialised response, and it does not run for a 404 |
| `@RestController` vs `@Controller`? | Saying "one returns a body". `@RestController` **is** `@Controller` + `@ResponseBody`; there is no performance difference |
| `@Valid` vs `@Validated` — what breaks? | Not naming that **method-level validation needs `@Validated` on the class** and produces a `ConstraintViolationException` → **500** instead of 400 |
| How do you design a global error response? | Hand-rolling a JSON shape. The modern answer is **`ProblemDetail` (RFC 7807)** plus extension properties (`code`, `errors`, `traceId`) |
| How is the `HttpMessageConverter` chosen? | Same answer for both directions. The **inbound** path is a *lookup* by `Content-Type` (no match → **415**); the **outbound** path is a *scan* of the converter list (no match → **406**) |
| Why not return the JPA entity? | Only saying "it leaks the schema". The real costs are **mass assignment** and the **N+1 / `LazyInitializationException`** that Jackson triggers while serialising lazy associations |
| `Page` vs `Slice`? | Treating them as interchangeable. `Page` runs a **count query**; `Slice` does not. On a 12 M-row table the count alone can cost 80–200 ms |
| Why does my `ResponseStatusException` return 500? | Not knowing that `ExceptionHandlerExceptionResolver` runs **before** `ResponseStatusExceptionResolver` — so a catch-all `@ExceptionHandler(Exception.class)` swallows it |
| What is the order of the `HandlerExceptionResolver`s? | Saying "Spring picks the best one". It is **first-match-wins in a fixed order**: `ExceptionHandler` → `ResponseStatus` → `Default` — and if none handles it, the exception is rethrown to the container |
| Why must `AccessDeniedException` be rethrown from an advice? | Not knowing that `ExceptionTranslationFilter` lives **outside** the `DispatcherServlet` — an advice that handles it produces a 500 instead of a 403 |
| Where does the correlation id come from? | Only MDC. It must be **propagated to every downstream call**, or it correlates exactly one hop |
| Why is the request body empty in my filter? | Believing the framework rewinds it. The servlet input stream is **single-pass**; `ContentCachingRequestWrapper` caches what is read — it does not rewind it |
| `preHandle` returning `false` — what happens? | Thinking the exception resolvers still run. They do **not**: the handler is skipped, no argument resolution happens, and the interceptor owns the whole response |
| Does `@Transactional` cover the request body? | No. The transaction opens in the **aspect**, which runs **after** the body was deserialised |

---

## 🧠 The One Picture — Spring MVC, end to end

```text
   HTTP REQUEST
       │
       ▼
   ╔══════════════════════════════════════════════════════════════════════════╗
   ║  CONTAINER LEVEL — servlet Filter                                        ║
   ║  correlation id · security chain · CORS · CSRF · rate limit · tracing     ║
   ║  ┌────────────────────────────────────────────────────────────────────┐  ║
   ║  │  DispatcherServlet — THE FRONT CONTROLLER                          │  ║
   ║  │                                                                    │  ║
   ║  │   HandlerMapping ──► which method?   (RequestMappingInfo matches)   │  ║
   ║  │   HandlerAdapter ──► how to call it?  (resolve args → invoke)       │  ║
   ║  │   ┌──────────────────────────────────────────────────────────────┐  │  ║
   ║  │   │  MVC LEVEL — HandlerInterceptor                               │  │  ║
   ║  │   │    preHandle (can VETO)                                      │  │  ║
   ║  │   │    ┌────────────────────────────────────────────────────┐    │  │  ║
   ║  │   │    │  ARGUMENT RESOLUTION                                 │    │  │  ║
   ║  │   │    │    @PathVariable @RequestParam @RequestBody        │    │  │  ║
   ║  │   │    │    @Valid ──► 400 on failure                       │    │  │  ║
   ║  │   │    │    ┌──────────────────────────────────────────┐    │    │  │  ║
   ║  │   │    │    │  METHOD LEVEL — @Aspect                │    │    │  │  ║
   ║  │   │    │    │    @Transactional @PreAuthorize        │    │    │  │  ║
   ║  │   │    │    │    ┌──────────────────────────────┐    │    │    │  │  ║
   ║  │   │    │    │    │   CONTROLLER METHOD          │    │    │    │  │  ║
   ║  │   │    │    │    └──────────────────────────────┘    │    │    │  │  ║
   ║  │   │    │    └──────────────────────────────────────────┘    │    │  │  ║
   ║  │   │    │  RETURN VALUE HANDLING ──► Jackson writes the body  │    │  │  ║
   ║  │   │    └────────────────────────────────────────────────────┘    │  │  ║
   ║  │   │    postHandle (the body is ALREADY written)                  │  │  ║
   ║  │   └──────────────────────────────────────────────────────────────┘  │  ║
   ║  │   afterCompletion (ALWAYS — receives the exception)               │  ║
   ║  │                                                                    │  ║
   ║  │   EXCEPTION PATH: ExceptionHandlerExceptionResolver               │  ║
   ║  │                 → ResponseStatusExceptionResolver                  │  ║
   ║  │                 → DefaultHandlerExceptionResolver (else rethrow)   │  ║
   ║  └────────────────────────────────────────────────────────────────────┘  ║
   ╚══════════════════════════════════════════════════════════════════════════╝
       │  ← the filter unwinds (finally blocks, MDC cleanup)
       ▼
   HTTP RESPONSE

   THE NINE STRATEGIES (one instance of each, inside the DispatcherServlet)
   ─────────────────────────────────────────────────────────────────────────
    HandlerMapping · HandlerAdapter · HandlerExceptionResolver · ViewResolver
    MultipartResolver · LocaleResolver · RequestToViewNameTranslator
    FlashMapManager · ThemeResolver
```

> **Caption:** Filters see **every** request but not the handler; interceptors see the **handler**
> but not unmatched requests; aspects see the **method** but neither the request nor the response.
> Choosing the wrong level is not a style choice — it produces silent gaps in your observability
> and your security.

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **The nine strategies by name and order** — most candidates can name three. Write them down and
      the *order* of the four `HandlerMapping` implementations (`RequestMapping` 0 → `BeanNameUrl` 2 →
      `RouterFunction` 3 → `SimpleUrl` `MAX-1`).
- [ ] **`RequestMappingInfo`'s comparison algorithm** — path, method, params, headers, consumes,
      produces — and the fact that **incomparable** mappings are an **ambiguity** that aborts startup.
- [ ] **The exact order of `Filter` → `Interceptor` → `Aspect`** and *what each one can see*. Draw it
      from memory; then draw the async variant.
- [ ] **`@Valid` vs `@Validated`** — which package, which position, which exception, which status.
      Get the `ConstraintViolationException` → **500** case right.
- [ ] **The `HandlerExceptionResolver` order** and the fact that a method-level `@ExceptionHandler`
      beats an advice, which beats `ResponseStatusExceptionResolver`.
- [ ] **`ProblemDetail` / RFC 7807** — the six standard fields and which ones are *stable* versus
      *per-occurrence*. Then the extensions you add (`code`, `errors`, `traceId`).
- [ ] **The converter selection asymmetry** — inbound is a lookup (415), outbound is a scan (406).
- [ ] **`Page` vs `Slice` vs cursor pagination** — and why offset pagination degrades linearly.
- [ ] **Async MVC** — `WebAsyncManager`, `startAsync()` releasing the Tomcat thread, the 30 s default
      timeout, and the `AsyncRequestTimeoutException` → **503** mapping.
- [ ] **The multipart memory model** — the *container* parses it, `file-size-threshold` decides
      memory vs disk, and `getOriginalFilename()` is untrusted input.
- [ ] **`@Transactional` is an aspect** — which means it does **not** cover the request-body
      deserialisation, and it does not work on self-invocation.

---

## 🧪 Coding Drill

- [ ] Write a `HandlerInterceptor` that logs the resolved `HandlerMethod`, the status and the
      duration, and prove the exact log line for a 200 and for a 400.
- [ ] Write a `HandlerMethodArgumentResolver` for a custom `@CurrentUser` annotation that resolves
      the domain `User` from the `SecurityContext`, and prove the resolution path in the DEBUG log.
- [ ] Write a `OncePerRequestFilter` that generates a correlation id, puts it in the MDC, **clears it
      in a `finally`**, and **propagates it to a downstream HTTP call** with a `RestClient`
      interceptor.
- [ ] Build a `@RestControllerAdvice` extending `ResponseEntityExceptionHandler` and assert the
      **exact key set** of the error body for a validation failure, a malformed body, a wrong verb
      and a business-rule failure.
- [ ] Write a custom `@StrongPassword` constraint with its `ConstraintValidator`, and prove the exact
      failure message in the 400 body.
- [ ] Implement a `HandlerMethodReturnValueHandler` that writes CSV for a `@Csv`-annotated return
      type, and register it without disturbing the default chain.
- [ ] Serve a 180 MB export with `StreamingResponseBody` and prove the response is **chunked** and
      that the headers had to be set **before** the first byte.
- [ ] Register three filters with known orders and prove the entry/exit order **from the logs**.

---

**Related folders:** ← [02 · Spring Core & Framework](../02-Spring-Core-Framework/README.md) · → [04 · Spring Data JPA & Hibernate](../04-Spring-Data-JPA-Hibernate/README.md) · ↑ [Master Index](../README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
