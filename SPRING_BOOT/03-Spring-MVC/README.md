# 03 — Spring MVC · Interview Notes Index

> Spring MVC is where the **HTTP contract** lives. Every interview that touches a backend asks
> something here — "how does a request flow through Spring?", "how do you handle the
> exceptions?", "how do you avoid the N+1 in the response?".
> The strongest answers show a candidate understands the **DispatcherServlet**, the **argument
> resolvers**, and — crucially — can explain the **trade-offs** (DTOs vs entities, `RestClient`
> vs `RestTemplate`, the filter vs the interceptor).

| Priority | Chapters | Why It Matters |
|----------|----------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · Request Flow & DispatcherServlet](01-Request-Flow-and-DispatcherServlet.md) | The full request lifecycle, the internal machinery |
| ⭐⭐⭐⭐⭐ | [02 · Controllers & REST APIs](02-Controllers-and-REST-APIs.md) | The HTTP contract, the DTO pattern, the status codes |
| ⭐⭐⭐⭐⭐ | [03 · Validation](03-Validation.md) | The input contract, the groups, the custom constraints |
| ⭐⭐⭐⭐⭐ | [04 · Exception Handling](04-Exception-Handling.md) | The error contract, `ProblemDetail`, the transaction interaction |
| ⭐⭐⭐⭐ | [05 · Filters, Interceptors & Files](05-Filters-Interceptors-and-Files.md) | The extension points, the uploads/downloads |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| Walk me through a request in Spring MVC. | Most people stop at "the DispatcherServlet calls the controller". Name the **nine** steps: the filter → the interceptor → the handler mapping → the argument resolution → the handler → the return value handling → the serialisation → the interceptor's `afterCompletion` → the response commit. |
| Why return a DTO instead of the entity? | Four reasons, not one: the **over-exposure** (the internal fields leak), the **coupling** (the DB change breaks the API), the **lazy loading** (`LazyInitializationException` with `open-in-view=false`), and the **N+1** (the serialisation triggers a query per row). |
| `@Controller` vs `@RestController`? | `@RestController` = `@Controller` + `@ResponseBody` — the return value is the **body**, not a view name. |
| How do you handle the exceptions? | One **global `@RestControllerAdvice`** → a `ProblemDetail` (RFC 9457) everywhere. The local `@ExceptionHandler` only for the genuinely local cases. And the **security** exceptions are handled by Spring Security, not the advice. |
| 401 vs 403? | **401** = not authenticated (with a `WWW-Authenticate` header). **403** = authenticated but not allowed. The Spring Security defaults match this. |
| How do you avoid the N+1? | The **join fetch** / `@EntityGraph` / the **DTO projection** — and the mapping to the DTO **inside** the transaction. |
| Filter vs interceptor? | The filter is the **servlet** level (before/after everything, including the static resources); the interceptor is the **Spring MVC** level (around the handler, it knows the handler, it sees the exception). |
| What happens with a checked exception in a `@Transactional` method? | The transaction **commits**! The default rollback rule is for the `RuntimeException` only. Use `rollbackFor = Exception.class`. |
| `RestTemplate` or `RestClient`? | `RestClient` (Boot 3.2+) — fluent, immutable, easier to test. But the real issue is the **misconfiguration**: the default timeouts are **infinite**. |
| How do you validate the request? | The **Bean Validation** annotations on the DTO + `@Valid` on the `@RequestBody` → a `MethodArgumentNotValidException` → a 400 with the field violations. The **groups** for the operation-specific rules; a **class-level** constraint for the cross-field rules. |

---

## 🧠 The Request Lifecycle — One Picture

```text
  HTTP Request
      │
      ▼
  ┌─ SERVLET FILTERS ────────────────────────────────────────────┐
  │  CORS → Security → CorrelationId → RateLimit → RequestLog    │
  └───────────────────────────┬───────────────────────────────────┘
                              ▼
  ┌─ DispatcherServlet ────────────────────────────────────────┐
  │  HandlerMapping → HandlerAdapter                            │
  │      │                                                      │
  │      ├─ Interceptor.preHandle()                             │
  │      ├─ ArgumentResolvers (@RequestBody, @PathVariable, …)   │
  │      ├─ Controller.method()                                  │
  │      ├─ Interceptor.postHandle()                            │
  │      └─ @ResponseBody → Jackson → the bytes                 │
  │                                                              │
  │  On error: @RestControllerAdvice → ProblemDetail            │
  │  Interceptor.afterCompletion(ex)                            │
  └───────────────────────────┬───────────────────────────────────┘
                              ▼
  ┌─ Response ──────────────────────────────────────────────────┐
  │  the filters unwind → the container commits the response    │
  └──────────────────────────────────────────────────────────────┘
```

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **The `HandlerExceptionResolver` chain** — the order, and who wins.
- [ ] **`ProblemDetail`** — the five standard fields + the extension properties.
- [ ] **The validation groups** — and the fact that `@Validated` is required on the class for
      the method-level validation.
- [ ] **`Pageable`/`Sort`** — the auto-configured parameters, and the **SQL injection** via the
      sort property.
- [ ] **`OncePerRequestFilter`** — and the double execution with the async dispatch.
- [ ] **The multipart configuration** — the size limits, the temp file, the streaming.
- [ ] **`@RestControllerAdvice` + `ResponseEntityExceptionHandler`** — the difference between the
      two, and why you extend the latter.
- [ ] **The `produces`/`consumes` conditions** — and the 404-vs-406 subtlety.
- [ ] **The `ArgumentResolver` precedence** — the built-ins win for the types they support.

---

## 🧪 Coding Drill

- [ ] Write a `ProblemDetail`-based global error handler with 6 exception types and a catch-all
      that leaks nothing.
- [ ] Build a `@CurrentUser` argument resolver and prove it works (and that it doesn't without
      the registration).
- [ ] Write a `RestClient` with the connect + read timeouts and a circuit breaker.
- [ ] Implement a `Pageable`-based list endpoint with an **allow-listed** sort (and a test that
      proves the SQL injection is blocked).
- [ ] Write a `OncePerRequestFilter` that adds the correlation id and prove the MDC doesn't leak
      between the requests.
- [ ] Build a streaming file upload + download and verify the `Content-Disposition` and the
      `nosniff` headers.

---

**Related folders:** ← [02 · Spring Core](../02-Spring-Core/README.md) · → [04 · Spring Data JPA & Hibernate](../04-Spring-Data-JPA-Hibernate/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
