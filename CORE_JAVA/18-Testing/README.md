# 18 — Testing · Interview Notes Index

> Testing is asked in **every** backend interview, usually as "how do you test X?".
> The strongest answers show a candidate understands the *test pyramid*, the *test doubles*
> taxonomy, and — crucially — can name the **smells** in their own tests.

| Priority | Chapters | Why It Matters |
|----------|----------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · JUnit Basics](01-JUnit-Basics.md) | JUnit 5 (Jupiter) full API, lifecycle, assertions |
| ⭐⭐⭐⭐⭐ | [02 · Mockito & Mocking](02-Mockito-Mocking.md) | Mocks/stubs/spies, argument captors, static mocking |
| ⭐⭐⭐⭐ | [04 · Testing Best Practices](04-Testing-Best-Practices.md) | Test pyramid, smells, coverage vs mutation, flaky tests |
| ⭐⭐⭐ | [03 · TestNG](03-TestNG.md) | XML suites, groups, parallel execution, data providers |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| Mock vs stub vs spy vs fake? | They are **not** interchangeable. Mock = behaviour verification; Stub = canned return; Spy = real object + recording; Fake = working simplified impl (in-memory DB). |
| Why is over-mocking a code smell? | If you mock everything, you test the **mocks**, not the system — and you cannot refactor without breaking tests. |
| How do you test a `void` method? | `verify(mock).doSomething()` — you verify the *interaction*, not a return value. |
| `when` vs `doReturn` — when must I use `doReturn`? | When the method is `final`, `private`, or **when the call is a real object method** (spies) — `when(spy.getX())` actually *calls* `getX()`. |
| What is the AAA pattern? | Arrange, Act, Assert — one per test, one reason to fail. |
| JUnit 4 vs JUnit 5? | Different packages (`org.junit.Test` → `org.junit.jupiter.api.Test`), lifecycle hooks, and JUnit 5 is built on the **Jupiter platform** with a vintage engine for JUnit 4. |
| How do you test concurrency? | Don't assert on timing. Use latches/barriers to make interleavings deterministic; assert on invariants, not order. |
| Coverage target? | Coverage is a **smell indicator**, not a goal — 100% line coverage with zero assertions is worthless. Use **mutation testing**. |

---

## 🧠 The Test Pyramid

```
                 ╱╲
                ╱  ╲          E2E / UI tests
               ╱    ╲         few, slow, brittle, $$$   (~5%)
              ╱──────╲
             ╱        ╲       Integration tests
            ╱          ╲      DB, HTTP, containers        (~15%)
           ╱────────────╲
          ╱              ╲    Unit tests
         ╱                ╲   pure logic, fast, many     (~80%)
        ╱──────────────────╲
```

> **Caption:** Invert the pyramid for an **API-first** company and you get the "ice cream cone"
> anti-pattern — slow, flaky E2E tests and almost no unit tests. Interviewers ask this to
> see whether you can *diagnose* a test suite, not just write tests.

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **Strict stubs** — Mockito's default `STRICT_STUBS` fails on unnecessary stubbing (a useful signal).
- [ ] **`@Nested` + `@ParameterizedTest`** — the modern way to structure a test class.
- [ ] **Test data builders** — over `new` in every test.
- [ ] **Time and randomness** — inject `Clock` and `Random`; never call `LocalDateTime.now()` in business logic.
- [ ] **Flaky test triage** — quarantine, `retry` rules, and why retries hide bugs.
- [ ] **Contract & integration testing** — Testcontainers, WireMock.

---

## 🧪 Coding Drill

- [ ] Write a JUnit 5 test class for a `ShoppingCart` using `@Nested` per scenario and `@ParameterizedTest` for boundaries.
- [ ] Mock a repository with `ArgumentCaptor` and assert the saved entity.
- [ ] Write a `@Spy`-based test that verifies a collaborator was called *and* stubs its return safely.
- [ ] Use `mockStatic` to mock `UUID.randomUUID()` — then explain why this is a smell.
- [ ] Write a test for a method that depends on the current time — and inject a `Clock` to make it deterministic.

---

**Related folders:** ← [17 · Serialization](../17-Serialization/README.md) · → [Master Index](../README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
