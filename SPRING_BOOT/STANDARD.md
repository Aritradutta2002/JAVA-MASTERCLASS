# SPRING_BOOT — Interview Notes Standard (Engineering Guideline)

> **Every chapter file under `SPRING_BOOT/**` MUST follow this structure.**
> This extends [`CORE_JAVA/STANDARD.md`](../CORE_JAVA/STANDARD.md) — read that one once too.
> Where the two differ, **this file wins** for the `SPRING_BOOT/` folder.

---

## 0. Non-negotiable rules

1. **Never delete existing correct content.** We are *upgrading*, not rewriting. Merge, deepen,
   reorganise, add. Preserve any existing good tables, code and ASCII art.
2. **Examples must be correct.** Code blocks marked `java` must be logically correct for
   **Spring Boot 3.3+ / Spring Framework 6.1+ / Java 17+**. No pseudo-code, no invented APIs,
   no `com.example.foo` classes that do not exist in the same chapter. If a class is used,
   either show it or import it from a real Spring/Jakarta artifact.
3. **Explain WHY, not just WHAT.** Every rule needs the reasoning a senior interviewer is
   listening for: *"because … , therefore …"*.
4. **Visuals must be ASCII/Unicode art or Mermaid** — always inside fenced code blocks with a
   language tag (`text` for art, `mermaid` for graphs). Never ASCII art without a fence.
5. **No external links required to understand the topic.** Links are a bonus, not the answer.
6. **Length target: 1,200–1,500 lines per chapter.** Chapters covering internals
   (Security filter chain, JWT, JPA/Hibernate internals) may reach 1,800+. A chapter under
   1,200 lines is considered incomplete.
7. Use **emoji section markers** consistently so scanning a 1,400-line file is fast.
8. **Anchor links in the Table of Contents must match heading text exactly** (GitHub slug rules:
   lowercase, spaces → `-`, drop punctuation, em-dashes → nothing).
9. **Numbers, not adjectives.** "p99 latency 240 ms", "TTL 15 minutes", "HTTP 429" — a
   concrete figure beats a vague claim every time.
10. **Every chapter must be independently answerable.** A reader who lands on chapter 07 of
    06-JWT must not need to open the previous file to follow the argument. Cross-link for
    depth, never for comprehension.

---

## 1. Version target (pinned for the whole folder)

| Layer | Version | Why |
|---|---|---|
| Java | **17+** | Spring Boot 3 baseline; records, sealed types, switch patterns available |
| Spring Boot | **3.3+** | `RestClient`, `@ServiceConnection`, virtual-thread support |
| Spring Framework | **6.1+** | `SecurityFilterChain` lambda DSL, `AuthorizationManager` |
| Jakarta EE | **10** | `jakarta.*` packages — **never** `javax.*` in any snippet |
| Hibernate | **6.2+** (Boot 3.3) / **6.6** (Boot 3.4) | JPA 3.1 |

**Version-specific callouts are mandatory.** When a behaviour differs by version, say so
inline, e.g.:

> ⚠️ **Boot 3.4 changed this.** `@MockBean` is deprecated in favour of `@MockitoBean`
> (Spring Framework 6.2). Code in this chapter targets 3.3.

Never present a deprecated API as current. If an API was removed, name the replacement.

---

## 2. Required file skeleton (24 sections, in this order)

```markdown
# <Chapter Title>

> **Level:** Beginner | Intermediate | Advanced | Expert
> **Interview Frequency:** ★★★★☆ (1–2 lines on what interviewers ask about this)
> **Prerequisites:** [link](...) · [link](...)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why--the-problem-this-solves)
- ...
- [23. Follow-up Learning](#23-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer
(8–12 bullets a candidate can literally recite)

## 1. What & Why — The Problem This Solves
(problem first, then the solution; an analogy; what breaks without it)

## 2. Mental Model — The One Picture
(the single ASCII or Mermaid diagram that makes the topic click)

## 3. Deep Dive — Theory (Beginner → Advanced)
### 3.1 Beginner Level
### 3.2 Intermediate Level
### 3.3 Advanced Level
### 3.4 Expert Level
(theory, internals, class names, method names, version differences)

## 4. Internal Working Mechanism
(what Spring actually calls, in what order — classes, interfaces, callbacks)

## 5. Step-by-Step Execution Flow
(numbered flow: 1. … 2. … — for a request, a startup, or a lifecycle)

## 6. Visualizations
(architecture diagram, sequence diagram, flowchart, timeline — ASCII and Mermaid)

## 7. Production-Level Use Cases & Real Industry Examples
(where this is used in real banking/e-commerce/SaaS systems, with numbers)

## 8. Worked Examples (with Output + Dry Run)
(each: problem → code → actual output → step-by-step dry run → takeaway)

## 9. Code from Scratch — Production-Grade
(realistic, complete, correct code with WHY comments)

## 10. Common Mistakes & Gotchas
(❌ wrong → ✅ right, with the reason it breaks)

## 11. Performance Considerations
(latency, memory, throughput, tuning knobs, what to measure)

## 12. Security Considerations
(threats, attack path, the Spring-specific defence)

## 13. Best Practices
(the rules a code reviewer would enforce)

## 14. Hands-On Exercises & Code Challenges
(≥3 exercises + ≥2 challenges, each with acceptance criteria)

## 15. Interview Q&A — Level-wise
### 🟢 Beginner
### 🟡 Intermediate
### 🔴 Advanced / Senior
### ⚫ Expert / Principal
(each: **Q:** … **A:** … 2–6 sentences — the "ideal answer")

## 16. Scenario-Based & Real-Experience Scenarios (3–5 yrs)
(≥3 real situations a 3–5 year developer is actually handed)

## 17. Counter-Questions, Tricky Questions & Follow-Ups
(≥8, each: naive answer → the trap → expert answer)

## 18. Frequently Asked Production Issues & Debugging Scenarios
(≥3 incidents; each names the exact diagnostic tool/command and the expected signal)

## 19. Architecture Design Questions
(≥2 design prompts with a reference design and the trade-offs)

## 20. Rapid-Fire MCQs
(≥10 questions, options A/B/C/D, then an answer key with one-line explanations)

## 21. Cheat Sheet / One-Page Summary
(the table to screenshot)

## 22. Real-World Production Notes
(tuning knobs, metrics, defaults, when to deviate)

## 23. Follow-up Learning
(next chapter + related topics + source pointers)

---
**Next Chapter:** → [link](...)
**Previous Chapter:** ← [link](...)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
```

---

## 3. Section-by-section rules

### §0 TL;DR
- 8–12 bullets. Each bullet is a **complete, quotable statement**, not a topic label.
- Include at least one **hard number** and one **trade-off** (what it costs you).

### §3 Deep Dive
- All four bands must be present and must **build** — beginner terms are reused at advanced level.
- Name real classes and interfaces (`DispatcherServlet`, `HandlerMapping`, `BeanPostProcessor`,
  `DefaultListableBeanFactory`, `FilterChainProxy`, `EntityManagerFactory`).
- Quote the actual API signature when it matters.

### §4 Internal Working Mechanism
- This is the section that separates a 3-year candidate from a 5-year one.
- Answer: *which class calls which, in what order, and where the extension point is.*
- Use a numbered call chain. Name the interface a developer would implement to hook in.

### §5 Step-by-Step Execution Flow
- Numbered, unambiguous, and **traceable to the mechanism section** (same numbering).
- For a request: say exactly what happens between socket accept and response write.

### §6 Visualizations
- At least **3** diagrams. At least **1 Mermaid** (`mermaid`) and at least **1 ASCII** (`text`).
- For any topic with a request or event flow: **≥1 sequence diagram** and **≥1 flowchart**.
- **Every diagram gets a caption line immediately below** — either
  `**Caption:** …` or `**What to look at:** …`.

### §7 Production-Level Use Cases
- At least 3 real systems. Name the domain (banking, e-commerce, SaaS, logistics, ad-tech).
- Include scale numbers ("40k orders/min peak", "p99 < 120 ms", "2000 users/second").

### §8 Worked Examples
- At least 3. Each shows: the problem, the code, the **actual expected output**, and a
  **dry run** that walks the numbers or steps one at a time.
- Output blocks must be in a ```text fence and be genuinely what the code produces.

### §9 Production Code
- Complete, correct, importable. Prefer small focused classes over one giant class.
- Comment with **why**, not what: `// FIX: use constructor injection so the bean is immutable`.

### §14 Exercises & Challenges
- **Hands-on exercises**: 3+ steps each, e.g. "add a `@Bean` for `ObjectMapper` with
  `JavaTimeModule` and prove the ISO-8601 output with a test."
- **Code challenges**: 2+ each with an explicit *acceptance criteria* bullet list and a
  *stretch goal*.

### §15 Interview Q&A
- **≥4 questions per band** (so ≥16 total). Answers are the "ideal answer" — what you would
  say out loud, 2–6 sentences.
- No answer may be a bare definition. Every answer must include the *why* or the *trade-off*.

### §16 Scenarios
- 3+ scenarios written as a **situation you were handed**, not a question.
- Each: the situation, your first move, the trap, the resolution, the lesson.

### §17 Counter-Questions
- **≥8** per chapter, mixing the four probe types:
  - *"Why not X?"* — design alternatives
  - *"What happens if…?"* — failure modes
  - *"How would you find out?"* — debugging and observability
  - *"Is that true?"* — challenging a wrong premise
- Format:

```markdown
### CQ1. "Isn't `HashMap` enough? Why ConcurrentHashMap?"

- **Naive answer:** "HashMap is not thread-safe, so we use ConcurrentHashMap."  ← incomplete
- **The trap:** CHM's real win is *lock-free reads + fine-grained locking on writes*,
  not merely "thread safety". Also, `ConcurrentHashMap` **does not allow null keys/values**
  because `get(key)==null` is ambiguous with an absent key.
- **Expert answer:** <full paragraph>
```

### §18 Production Issues & Debugging
- Each incident names: **symptom → diagnostic tool/command → the signal you expect → fix**.
- Use real commands: `jcmd <pid> Thread.print`, `jstack`, `SHOW ENGINE INNODB STATUS`,
  `pg_stat_activity`, `EXPLAIN ANALYZE`, actuator `/actuator/heapdump`, `kill -3 <pid>`,
  `curl -X POST /actuator/refresh`, `docker logs`, `kubectl top pod`, `redis-cli info`,
  `kafka-consumer-groups --describe`.

### §19 Architecture Design Questions
- 2+ prompts. Each gives a **reference design** (components + data flow) and the
  **trade-offs** an interviewer wants to hear spoken.

### §20 MCQs
- ≥10, A/B/C/D. Answer key at the end with a one-line reason for each.
- At least 3 must be **tricky** — the obvious answer is wrong.

### §21 Cheat Sheet
- One screen. Tables, not paragraphs.

### §22 Production Notes
- Tuning knobs with **default value** and **when to change it**.

---

## 4. Canonical reference tables (reuse verbatim — do not re-derive)

These are the pinned facts for the folder. If a chapter contradicts one of these, the chapter
is wrong.

### 4.1 Spring Security filter order (Boot 3.x / Framework 6.x)

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

### 4.2 Transaction propagation

| Propagation | Existing tx? | Behaviour |
|---|---|---|
| `REQUIRED` (default) | yes | Join |
| `REQUIRES_NEW` | yes | **Suspend** current, start new (needs a **second connection**) |
| `SUPPORTS` | yes | Join if present, else non-transactional |
| `NOT_SUPPORTED` | yes | **Suspend** current, run non-transactionally |
| `MANDATORY` | yes | Join; **throw** if none exists |
| `NEVER` | yes | **Throw** if one exists |
| `NESTED` | yes | Savepoint within the current transaction |

### 4.3 Isolation levels (weakest → strongest)

| Level | Dirty read | Non-repeatable | Phantom | Serializable |
|---|---|---|---|---|
| `READ UNCOMMITTED` | possible | possible | possible | no |
| `READ COMMITTED` | prevented | possible | possible | no |
| `REPEATABLE READ` | prevented | prevented | possible (MySQL: no) | no |
| `SERIALIZABLE` | prevented | prevented | prevented | yes |

### 4.4 JWT lifetimes (recommended production defaults)

| Token | TTL | Storage | Sent as |
|---|---|---|---|
| Access token | **5–15 min** | Server: none (stateless) | `Authorization: Bearer <jwt>` |
| Refresh token | **7–30 days** | Server: **hashed** in DB/Redis | `HttpOnly; Secure; SameSite=Strict` cookie |
| ID token (OIDC) | 5–60 min | none | never sent to the browser JS |

### 4.5 HTTP status cheat-sheet for REST APIs

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

---

## 5. Visualisation rules

| Purpose | Use |
|---|---|
| Request / call flow | Numbered arrows between boxes, or a Mermaid `sequenceDiagram` |
| Architecture | Mermaid `graph TD` / `flowchart LR` with subgraphs |
| Decision logic | Mermaid `flowchart TD` with labelled edges |
| Bean lifecycle | Timeline with `──▶` and phase labels |
| Filter chain | Ordered list with a `│` column showing pass-through |
| Cache flow | Before/after side-by-side |
| State machine | Box + labelled arrows |

**Every diagram needs a caption line below it.** Example:

```text
┌─────────┐
│  TOMCAT │
└─────────┘
```
**Caption:** The embedded container owns the socket; Spring never sees raw bytes.

---

## 6. Folder `README.md` template (one per topic folder)

```markdown
# NN — <Topic> · Interview Notes Index

> <2–3 sentences: what this topic is, and the single idea that makes it click.>

| Priority | Chapters | Why It Matters |
|----------|----------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · Name](01-Name.md) | <one line> |
| ⭐⭐⭐⭐ | [02 · Name](02-Name.md) | <one line> |

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| <question> | <the thing people get wrong> |

---

## 🧠 <One topic diagram>

---

## ⚠️ Weakest Areas to Brush Up

- [ ] <gap 1>
- [ ] <gap 2>

---

## 🧪 Coding Drill

- [ ] <concrete, verifiable drill>

---

**Related folders:** ← [prev](../NN-.../README.md) · → [next](../NN-.../README.md) · ↑ [Master Index](../README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
```

---

## 7. Cross-linking rules

- A chapter links to **later** chapters freely (forward links) and to **earlier** chapters
  only when the earlier chapter is the canonical source for that fact.
- Use **relative** links: `[04 · Transactions](../04-Spring-Data-JPA-Hibernate/03-Transactions-Propagation-and-Isolation.md)`.
- Link to a **specific chapter file**, not just the folder, whenever you reference a
  specific fact. A reader should land on the exact explanation.
- Never link to `SPRING_SPRINGBOOT/` — that folder is retired.
- Sibling placeholder folders (`DATABASE_CACHING`, `SYSTEM_DESIGN`, `CLOUD_DEVOPS`,
  `DOCKER_JAVA_BACKEND`) may be referenced with a single pointer line where the topic
  overlaps. They are **not** the source of truth for Spring-specific depth.

---

## 8. Review checklist — a chapter is "done" only when every box is ticked

- [ ] TOC anchors resolve to real headings in the same file
- [ ] All 24 sections present, in order, with the exact numbering
- [ ] §3 has all four level bands (3.1–3.4)
- [ ] Line count ≥ 1,200
- [ ] ≥8 counter-questions in §17, each with naive answer + trap + expert answer
- [ ] ≥10 MCQs in §20 with a complete answer key
- [ ] ≥3 diagrams, ≥1 `mermaid` and ≥1 `text`, each with a caption line
- [ ] ≥1 sequence diagram **and** ≥1 flowchart (for request/event-flow topics)
- [ ] ≥3 worked examples with real output and a dry run
- [ ] ≥4 interview questions per level band in §15
- [ ] ≥3 real-experience scenarios in §16
- [ ] ≥3 debugging scenarios in §18, each naming the diagnostic tool
- [ ] ≥2 architecture design questions in §19
- [ ] ≥3 exercises and ≥2 code challenges with acceptance criteria in §14
- [ ] §11 Performance and §12 Security both non-empty and topic-specific
- [ ] Every ```java block is correct for Spring Boot 3.3+ / Java 17+; `jakarta.*`, never `javax.*`
- [ ] No `@MockBean` presented as current (use `@MockitoBean` for 3.4+; note the deprecation)
- [ ] No truncated sentences, no `TODO`, no "as described above" without a reference
- [ ] Footer present: Next Chapter · Previous Chapter · Standard followed
