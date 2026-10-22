# Token Revocation, Storage & Production Design — The Complete Interview Guide

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★★ ("how do you revoke a JWT?" is the question that separates a candidate who has *used* JWTs from one who has only *read about* them)
> **Prerequisites:** [01 · JWT Architecture](./01-JWT-Architecture-Statelessness-and-Signature-Algorithms.md) · [02 · Access & Refresh Tokens](./02-Access-Refresh-Tokens-and-Token-Rotation.md)
> **Last Updated:** 2026

> **Target stack:** Spring Boot 3.3+ · Spring Framework 6.1+ · Spring Security 6.1+ · Java 17+ · Jakarta EE 10

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — The One Picture](#2-mental-model--the-one-picture)
- [3. Deep Dive — Theory (Beginner → Advanced)](#3-deep-dive--theory-beginner--advanced)
  - [3.1 Beginner Level](#31-beginner-level)
  - [3.2 Intermediate Level](#32-intermediate-level)
  - [3.3 Advanced Level](#33-advanced-level)
  - [3.4 Expert Level](#34-expert-level)
- [4. Internal Working Mechanism](#4-internal-working-mechanism)
- [5. Step-by-Step Execution Flow](#5-step-by-step-execution-flow)
- [6. Visualizations](#6-visualizations)
- [7. Production-Level Use Cases & Real Industry Examples](#7-production-level-use-cases--real-industry-examples)
- [8. Worked Examples (with Output + Dry Run)](#8-worked-examples-with-output--dry-run)
- [9. Code from Scratch — Production-Grade](#9-code-from-scratch--production-grade)
- [10. Common Mistakes & Gotchas](#10-common-mistakes--gotchas)
- [11. Performance Considerations](#11-performance-considerations)
- [12. Security Considerations](#12-security-considerations)
- [13. Best Practices](#13-best-practices)
- [14. Hands-On Exercises & Code Challenges](#14-hands-on-exercises--code-challenges)
- [15. Interview Q&A — Level-wise](#15-interview-qa--level-wise)
  - [🟢 Beginner](#-beginner)
  - [🟡 Intermediate](#-intermediate)
  - [🔴 Advanced / Senior](#-advanced--senior)
  - [⚫ Expert / Principal](#-expert--principal)
- [16. Scenario-Based & Real-Experience Scenarios](#16-scenario-based--real-experience-scenarios)
- [17. Counter-Questions, Tricky Questions & Follow-Ups](#17-counter-questions-tricky-questions--follow-ups)
- [18. Frequently Asked Production Issues & Debugging Scenarios](#18-frequently-asked-production-issues--debugging-scenarios)
- [19. Architecture Design Questions](#19-architecture-design-questions)
- [20. Rapid-Fire MCQs](#20-rapid-fire-mcqs)
- [21. Cheat Sheet / One-Page Summary](#21-cheat-sheet--one-page-summary)
- [22. Real-World Production Notes](#22-real-world-production-notes)
- [23. Follow-up Learning](#23-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

- **A signed JWT cannot be un-signed.** The signature is a mathematical fact; once it verifies, it verifies forever. What you *can* control is **acceptance** — the decision to honour a token that verifies. Revocation is a **stateful decision about acceptance**, not a change to the cryptography.
- **The six strategies, in increasing order of cost:** (1) **short TTL only** — no revocation, the blast radius *is* the TTL; (2) **denylist** — a `jti` in Redis with a TTL equal to the token's remaining life; (3) **allowlist / token version** — a per-user counter compared against a `ver` claim, **O(1) state that kills every token at once**; (4) **refresh-token denylist only** — the practical compromise; (5) **blacklist-on-logout** — the denylist populated only at logout; (6) **opaque tokens + introspection** — fully revocable but stateful, which defeats part of the point.
- **The default answer is (1) + (4):** a 5–15 minute access token and a revocable refresh token. Do not build a denylist until you have a *requirement* that the TTL cannot satisfy.
- **"Log out everywhere" is the token-version mechanism**, not a denylist of thousands of `jti`s. One counter per user in Redis; the `ver` claim in the token; a mismatch = revoked. **One write** invalidates **every** outstanding token.
- **The denylist TTL must equal the token's remaining lifetime.** A token with **8 minutes** left → `SETEX <key> 480 <value>`. Get this wrong in the direction of "no TTL" and the denylist grows without bound; get it wrong in the other direction and revocation stops working early.
- **The denylist adds a network hop to every authenticated request.** The engineering answer is to check it **only for long-lived tokens**, or to mirror it into a **Caffeine cache with a short TTL**, accepting a bounded revocation delay.
- **When Redis is down, you must choose fail-open or fail-closed — explicitly, per service.** Fail-open = the API stays up but a revoked token may work; fail-closed = the API is down but nothing revoked is honoured. For a public read API, fail-open on the *version check* is defensible; for a payments API, fail-closed.
- **JWT revocation is a database design problem.** The cryptography is settled; the questions are: where does the state live, what is its TTL, what does it cost per request, and what happens when it is unavailable.
- **Key rotation is a revocation problem too.** Publish the new public key **alongside** the old, sign with the new, wait **longer than the maximum token lifetime**, *then* remove the old. Skip the overlap and every live token dies at once — a **401 storm**.

---

## 1. What & Why — The Problem This Solves

**The problem.** A JWT's superpower is that verification needs no server state. Its
superpower is also its weakness: **there is no "revoke" operation on a signature.** You
cannot take a token back.

```text
   ❌ THE IMPOSSIBLE OPERATION
   ───────────────────────────────────────────────────────────────
   "Take this token back."

   The signature was produced by the private key. It verifies.
   It will verify tomorrow. It will verify next year.
   The only things that can stop it are:
     · a shorter exp  (so it dies sooner — you can only do this NEXT time)
     · a check on the server ("I know this one is cancelled")
   There is no third option. The signature is not a loan you can call in.
```

**What breaks if you ignore this.** Three failure modes, all of them real:

1. **A stolen token.** An attacker has a valid access token with a 15-minute TTL and the
   user's full authority. You cannot call it back. The blast radius is **15 minutes**.
2. **A terminated employee.** Their access token is valid until it expires, on **every**
   service, and you have no mechanism to stop it.
3. **A privilege change.** The user is promoted to `ADMIN` at 10:00 and demoted at 10:05.
   The token issued at 09:55 still carries `ADMIN` until 10:10.

**The solution — decide what "revocation" you actually need, then pay for exactly that.**

```text
   THREE DIFFERENT QUESTIONS, THREE DIFFERENT ANSWERS
   ────────────────────────────────────────────────────

   Q1. "How fast must a stolen token stop working?"
        → ANSWER: the access-token TTL. Shorten it. No state needed.

   Q2. "How do I kill every token a user has, right now?"
        → ANSWER: a token-version counter. ONE write, O(1) state.

   Q3. "How do I kill ONE specific token (that I know leaked)?"
        → ANSWER: a denylist entry keyed by jti, with a TTL.

   Q4. "Can I revoke instantly and with zero per-request cost?"
        → ANSWER: no, not with a self-contained token. Use an opaque token
          and an introspection endpoint (and pay the lookup on every request).
```

**The analogy — the badge and the access list.** A JWT is like a **visitor badge** with a
printed expiry date and a hologram. The hologram proves the building printed it. But the
building's security desk keeps a **revocation list**: "badge #A-4472 was reported lost —
destroy it on sight." The badge is still perfectly genuine; the desk simply refuses it
anyway. That desk list is your denylist — and note the two properties it must have:
it must be **consulted** (a cost), and it must **expire** (a cleanup, because a badge
that expired on its own no longer needs to be listed).

**What breaks with the denylist approach?** It reintroduces state on the hot path — the
thing you chose JWTs to avoid. That is not a failure, it is a **trade**: you are paying
0.5–5 ms per request (a Redis hop) for a revocation window that shrinks from "until
expiry" to "until the next request". The engineering skill is knowing **when that trade
is worth it**, and being able to quantify it.

> 💡 **The interview framing.** The question is not "how do you revoke a JWT?" — it is
> **"what is your revocation SLA, and what does it cost?"** Every answer in this chapter
> is a function of those two numbers.

---

## 2. Mental Model — The One Picture

```text
   "IS THIS TOKEN ACCEPTABLE?" — THE FOUR LAYERS
   ═════════════════════════════════════════════

   ┌──────────────────────────────────────────────────────────────────┐
   │ LAYER 1 — SIGNATURE        "is it genuine?"                      │
   │   CPU only.  50–150 µs.  No state.  Cannot fail for the right    │
   │   reason if you pinned the algorithm.                            │
   └───────────────────────────┬──────────────────────────────────────┘
                               ▼  passes
   ┌──────────────────────────────────────────────────────────────────┐
   │ LAYER 2 — CLAIMS           "is it currently in force?"           │
   │   exp / nbf / iss / aud.  CPU only.  1 µs.                     │
   │   ★ This is where MOST revocation happens — via a SHORT TTL.    │
   └───────────────────────────┬──────────────────────────────────────┘
                               ▼  passes
   ┌──────────────────────────────────────────────────────────────────┐
   │ LAYER 3 — REVOCATION LIST  "has this one been cancelled?"        │
   │   Redis GET denylist:<jti>                                      │
   │   ⚠ COSTS A NETWORK HOP ON EVERY REQUEST.                      │
   │   TTL on the entry = the token's remaining lifetime.            │
   └───────────────────────────┬──────────────────────────────────────┘
                               ▼  passes
   ┌──────────────────────────────────────────────────────────────────┐
   │ LAYER 4 — VERSION / SCOPE  "is this session still current?"     │
   │   token.ver == Redis GET ver:<userId>                            │
   │   O(1) state — one counter per USER, not per token.             │
   │   Bump it ⇒ every token of that user dies at once.              │
   └──────────────────────────────────────────────────────────────────┘

   ★ THE COST LADDER (add a network hop only as far up as you need)
   ───────────────────────────────────────────────────────────────────
   LAYER 2 only          →  0 ms extra      →  blast radius = TTL
   + LAYER 4             →  0.5–5 ms extra  →  blast radius = TTL
   + LAYER 3             →  0.5–5 ms extra  →  blast radius = ~0
   LAYER 3 instead of 2+4 →  0.5–5 ms extra  →  blast radius = ~0,
                                               but you lose statelessness
```

**What to look at:** the four layers are **ordered by cost**, and you should add them
**from the bottom up**. Most systems need layer 2 and layer 4 and never layer 3. The
layer you add is a *statement about your revocation SLA*, and you should be able to name
that SLA out loud.

---

## 3. Deep Dive — Theory (Beginner → Advanced)

### 3.1 Beginner Level

**Why a JWT cannot be revoked — the precise reason.**

The signature is `Sign(privateKey, header + "." + payload)`. Verification recomputes
`Verify(publicKey, header + "." + payload)` and compares. There is **no place in that
algorithm to record "this one has been cancelled"** — the signature is a function of the
bytes, not a lookup. To invalidate a token you would have to change the bytes (breaking
the signature) or change the key (breaking every other token).

**The three ways to actually stop a token.**

| Mechanism | What it changes | Cost | Reversible? |
|---|---|---|---|
| **Shorten `exp`** | The token's lifetime | Nothing today; everything tomorrow | Yes — but only for *future* tokens |
| **Change the signing key** | The key the token was signed with | Breaks **all** tokens, not one | Yes — but catastrophic |
| **Refuse to accept** | The *server's decision*, not the token | Depends on the mechanism | Yes — instantly |

The third is the only one that can revoke a **single, existing** token. It is also the
only one that costs something per request.

**The three scales of revocation.**

```text
   ONE TOKEN        →  denylist (keyed by jti)        →  cost: 1 entry per token
   ONE USER/SESSION →  token version / family revoke  →  cost: 1 counter per user
   ALL TOKENS       →  rotate the signing key         →  cost: everybody
```

**The vocabulary.**

| Term | Meaning |
|---|---|
| **Denylist / blocklist** | The set of tokens that are **refused**. You check membership. |
| **Allowlist** | The set of tokens that are **accepted**. Everything else is refused. |
| **Token version (`ver`)** | A counter in the token, compared against a stored counter. A mismatch = revoked. |
| **Introspection (RFC 7662)** | The server asks an authorisation server whether a token is active. Opaque tokens need this. |
| **`jti`** | The token's unique id — the natural denylist key. |

> 📌 **Note the naming.** The industry moved from "blacklist/whitelist" to
> "denylist/allowlist" because the old terms imply a default of inclusion. The
> **mechanism** is identical; only the vocabulary changed.

**`SessionRegistry` — Spring Security's abstraction, and why it does not help you here.**
`SessionRegistry` exists so Spring Security can track "authenticated sessions" for
features like concurrent-session control, `SessionInformationExpiredEvent` and
`maximumSessions`. It is populated by the **session** integration — with
`SessionCreationPolicy.STATELESS` and a JWT, there is **no `HttpSession`**, so the
registry stays **empty**. To use it with JWTs you must register the sessions yourself
(the `SessionRegistry` is a public API — you can call `registerSessionInformation(...)`
yourself), but that means building and maintaining your own session model. For JWTs, the
**refresh-token table is already that model** — use it.

### 3.2 Intermediate Level

**The six revocation strategies, compared.**

| # | Strategy | Mechanism | Per-request cost | Per-request state | Blast radius | Complexity | Use when |
|---|---|---|---|---|---|---|---|
| **1** | **Short TTL only** | Nothing. The `exp` does the work. | **0** | **None** | = the TTL | **Trivial** | **The default.** Almost always sufficient. |
| **2** | **Denylist** | `jti` → "revoked", checked with `GET denylist:<jti>` | **1 Redis GET (0.2–5 ms)** | 1 key per revoked token, TTL = remaining life | ≈ **0** (next request) | Medium | A specific token is known to be compromised. |
| **3** | **Allowlist / token version** | `ver` claim vs a per-user counter | **1 Redis GET (0.2–5 ms)** | **1 counter per user** | ≈ **0** (next request) | Medium | "Log out everywhere", role changes, bans. **The scalable one.** |
| **4** | **Refresh-token denylist only** | Revoke the refresh token; the access token lives out its TTL | **0** on the API path | 1 row per revoked refresh token | = the access-token TTL | Low | **The usual practical compromise.** |
| **5** | **Blacklist-on-logout** | Strategy 2, but entries are only written at logout | 1 Redis GET | 1 key per logout | = TTL, unless checked | Low–medium | Logout must invalidate immediately, but the API is high-volume. |
| **6** | **Opaque tokens + introspection** | The token is a random string; the server looks it up | **1 lookup per request, always** | 1 row per active token | **0** | High | High-value operations; OAuth2 introspection (topic 07). |

**Read the table as a decision tree.**

```text
   Do you need revocation faster than the access-token TTL?
   │
   ├─ NO  → STRATEGY 1 (short TTL only). Nothing to build. Stop here.
   │
   └─ YES → Do you need to revoke ALL of a user's tokens at once?
            │
            ├─ NO  → STRATEGY 2 (denylist by jti)
            │
            └─ YES → STRATEGY 3 (token version)  ← O(1) state, preferred
                      │
                      └─ Also: can you accept a per-request network hop?
                           │
                           ├─ NO  → STRATEGY 4/5 (revoke off the hot path)
                           └─ YES → STRATEGY 6 (opaque + introspection)
```

**The logout flow, end to end.**

```text
   POST /api/auth/logout   (Authorization: Bearer <jwt>, Cookie: rt=<refresh>)
   ─────────────────────────────────────────────────────────────────────────
   ① Verify the access token (it may already be expired — do NOT require it)
   ② Find the refresh token's row by hash
   ③ SET revoked_at = now            → the refresh token can never be used again
   ④ DELETE the refresh cookie        → Max-Age=0
   ⑤ Optionally: INCR ver:<userId>    → kills the outstanding ACCESS tokens
   ⑥ Record the audit event           → "LOGOUT"
   ⑦ 204 No Content
```

> ⚠️ **Step ① is the one people get wrong.** If the access token has already expired —
> which is *exactly* when a user usually clicks "log out" — and your logout endpoint
> requires a valid access token, the logout **fails with a 401** and the refresh token
> is never revoked. The refresh cookie alone must be sufficient to log out.

**"Log out everywhere" — the token-version mechanism, precisely.**

```text
   ISSUE
   ─────
   ver:<userId>  in Redis = 7              (the user's current token version)
   access token claim:  "ver": 7
   access token claim:  "sub": "42957"

   VERIFY (on every request)
   ────────────────────────
   claimVer = jwt.getClaimAsString("ver")   → "7"
   storedVer = redis.get("ver:42957")        → "7"
   if (!claimVer.equals(storedVer)) → 401    ← revoked, or a token from before the bump

   REVOKE ALL
   ──────────
   redis.incr("ver:42957")                  → 8
   …every access token in the wild still says "ver": 7  →  ALL of them now fail ✓
   …the next login issues a token with "ver": 8         →  works again ✓
```

**Why this is elegant.** The state is **one integer per user**. It does **not** grow
with the number of tokens, the number of logouts, or the number of devices. It is
**O(1) to revoke** — one `INCR`. And it is **idempotent** — bumping it twice is
harmless, which matters when two "log out everywhere" requests race.

**The `SessionRegistry` question, answered properly.** In a JWT system:

| | `HttpSession` world | JWT world |
|---|---|---|
| Where is the session id? | A cookie (`JSESSIONID`) | Nowhere — the token *is* the session |
| Where is the session state? | `HttpSession` / `SessionRegistry` | **Your** refresh-token table + **your** version counter |
| What does `SessionRegistry` do? | Tracks concurrent sessions, expires old ones | **Nothing** — it is not populated |
| What should you use instead? | — | The refresh-token table (a family = a session) |

If you *want* the Spring Security session features (concurrent session limits, "your
session was expired" events), you can register JWT sessions manually via
`SessionRegistry.registerSessionInformation(...)` — but then you are maintaining a
**second** session model alongside the refresh-token table. Do not do both.

### 3.3 Advanced Level

**The cost model — what a denylist really costs.**

```text
   PER AUTHENTICATED REQUEST (denylist check):
   ─────────────────────────────────────────
   ① Compute the denylist key       : jti is already in the token → ~0 µs
   ② Redis GET denylist:<jti>       : 0.2 ms (same-DC, pipelined)
                                       1–5 ms (cross-AZ, cross-region)
   ③ Parse the reply                : ~1 µs
   ④ Branch                         : ~0 µs
   ─────────────────────────────────────────
   TOTAL ADDED LATENCY (p50)        : 0.2–5 ms
   TOTAL ADDED LATENCY (p99)        : 1–25 ms   ← the tail is what hurts
   ADDED QPS ON REDIS               : 1 × your authenticated request rate
   FAILURE MODE                     : Redis down ⇒ ? (see below)
```

At **10,000 authenticated req/s**, the denylist adds **10,000 Redis GETs/s**. A single
Redis instance handles ~100,000 ops/s, so it is fine — but it is now a **hard
dependency of your API's request path**, and it must be replicated, monitored and
capacity-planned.

**The mitigation ladder for the denylist cost.**

| Technique | Latency | Revocation delay | Complexity |
|---|---|---|---|
| **Always check Redis** | 0.2–5 ms | ~0 (next request) | Low |
| **Local Caffeine cache of the denylist**, TTL 30 s | ~0 µs (local) | **up to 30 s** | Medium |
| **Check only long-lived tokens** (> 15 min TTL) | 0 for most traffic | = TTL for short ones | Low |
| **Check only for sensitive endpoints** | 0 for most traffic | = TTL elsewhere | Low |
| **Token version instead** | 0.2–5 ms | ~0 | Medium |

The **Caffeine mirror** deserves a note: each API instance keeps a local copy of the
denylist, refreshed every 30 seconds. A revocation therefore propagates within **30
seconds** — and **only to the instances that refresh**. On a 50-instance fleet, that is
50 refreshes every 30 s = ~1.7 GET/s from each instance — negligible. The trade is a
**bounded revocation delay of 30 s**, which is usually acceptable and is a *much* better
trade than 5 ms on every request.

**The "check only long-lived tokens" pattern.** If your access tokens live 5 minutes,
a denylist entry for one of them is only useful for 5 minutes — and the blast radius
is 5 minutes anyway. So the denylist adds nothing for short-lived tokens. It only earns
its keep when the access token is long-lived. **Conclusion: if you want instant
revocation, shorten the TTL; do not add a denylist.**

**Key rotation — the overlap window, precisely.**

```text
   T-0d  00:00   Generate key pair #2 (kid="key-2026-02").
   T-0d  00:01   PUBLISH key-2026-02 in the JWKS ALONGSIDE key-2026-01.
                 → the JWKS now has 2 keys; both verify.
   T-0d  00:02   Start SIGNING with key-2026-02.
                 → new tokens carry kid="key-2026-02"
                 → OLD tokens (kid="key-2026-01") still verify ✓
   T-0d  00:02   … 15 minutes of overlap must elapse …
   T-0d  00:17   Every token signed with key-2026-01 has now expired.
   T-0d  00:18   REMOVE key-2026-01 from the JWKS.
                 → now any token with kid="key-2026-01" → "no matching key" → 401

   THE OVERLAP WINDOW MUST BE ≥ (max token lifetime + clock skew)
   With a 15-minute access token and a 60-second leeway → 16 minutes.
```

**Why removing the old key early is a 401 storm.** At the moment you remove
`key-2026-01`, **every** token signed with it becomes unverifiable — instantly. If your
access-token TTL is 15 minutes and your request rate is 10,000/s, that is up to
**9,000,000 requests** that will fail with 401 in the next 15 minutes. And it looks
exactly like a **credential-stuffing attack** to your monitoring. This is why the
rotation runbook must be written down, and why the JWKS **key count** should be
alerted on.

**Storage choices — the real trade-offs.**

| | **Redis** | **Relational DB** | **Caffeine (in-memory)** |
|---|---|---|---|
| Read latency | **0.2–1 ms** | 0.3–2 ms | **< 1 µs** |
| Write latency | 0.2–1 ms | 1–5 ms | ~1 µs |
| **TTL / auto-expiry** | ✅ **Native** | ❌ Needs a cleanup job | ✅ Native |
| Durability | ⚠️ AOF/RDB — can lose recent writes | ✅ Strong | ❌ **Lost on restart** |
| Cross-instance consistency | ✅ | ✅ | ❌ **Per-instance only** |
| Transactional with other data | ❌ | ✅ Same transaction | ❌ |
| New dependency / failure mode | ✅ **Yes** | ❌ None | ❌ None (in-process) |
| Best for | Denylist, version counters, rate limits | Refresh tokens, durable revocation | A local mirror of a denylist |

**The Caffeine trap.** A per-instance denylist is **not** a denylist — a revocation
performed on instance A is invisible to instance B. That is only acceptable if you
accept the delay until every instance refreshes, and if you can tolerate a restart
losing the local copy (which means a revoked token becomes valid again until the
next refresh). Use Caffeine as a **cache in front of** a shared source of truth, never
as the source of truth.

**The "JWT revocation is a database design problem" framing.** Every design question
collapses into four:

1. **Where does the state live?** Redis (fast, TTL-native, new dependency) or the
   relational DB (durable, transactional, needs cleanup) or a local cache (fast,
   per-instance, not authoritative).
2. **What is the TTL?** Exactly the token's remaining lifetime — no more, no less.
3. **What does it cost per request?** One lookup (0.2–5 ms) or zero (if you don't check).
4. **What happens when it is unavailable?** Fail-open or fail-closed — chosen
   deliberately, per service, and documented.

### 3.4 Expert Level

**Fail-open vs fail-closed — the decision framework.**

When the revocation store is unavailable, you have two choices:

| | **Fail-open** (accept the token) | **Fail-closed** (reject the token) |
|---|---|---|
| Availability | ✅ The API keeps serving | ❌ The API is down for authenticated traffic |
| Security | ❌ A revoked token may be honoured | ✅ Nothing revoked is honoured |
| Worst case | An attacker uses a revoked token during the outage | The business is down |
| Right for | Public read APIs, low-value data, already-public information | Payments, PII, admin, writes |

The **sophisticated** answer is to make it **per-layer** and **per-endpoint**:

- The **signature and claim checks never fail open** — they are local and cannot fail.
- The **version check** can fail open for read endpoints and fail closed for write
  endpoints.
- The **denylist check** should generally fail closed for anything that mutates state,
  because a replayed write is much worse than a failed read.

**Never make this decision at runtime by accident.** The failure mode must be
**explicit in the code** (a `try/catch` with a documented policy), not an emergent
property of a library's default.

**The `ver` claim vs `jti` denylist — a precise comparison.**

| | Token version (`ver`) | `jti` denylist |
|---|---|---|
| State per user | **1 integer** | 1 entry per revoked token |
| State growth | **Constant** | Grows with revocations |
| Revoke ALL of a user | ✅ **One `INCR`** | ❌ Enumerate every token (you can't — you don't know them all!) |
| Revoke ONE token | ❌ Not possible | ✅ One `SET` |
| Revoke on a role change | ✅ One `INCR` | ❌ Impossible without knowing the tokens |
| Time to revoke | Immediate | Immediate |
| Per-request cost | 1 Redis GET | 1 Redis GET |

**The decisive observation**: with the version approach you can revoke a user's tokens
even when you have **no record** of which tokens exist — which is the whole point,
because you never issued them to a place you control. A `jti` denylist can only revoke
tokens you happen to know about.

> 🎯 **The interview line.** "A denylist can revoke a token *you know about*. The token
> version can revoke *every* token of a user, including the ones you have never seen —
> because it does not enumerate, it invalidates by comparison. That is why 'log out
> everywhere' is a version bump and not a denylist write."

**The `jti` denylist key design.**

```text
   key   : "denylist:jti:<jti>"
   value : "<reason>"            ← "explicit_logout" | "breach" | "admin" | "reuse_detected"
   TTL   : exp - now             ← EXACTLY the token's remaining lifetime
   type  : Redis STRING with EXPIRE (or a ZSET scored by expiry for batch cleanup)
```

Using the **`jti`** is right when the token is a JWT with a `jti`. For an **opaque**
token, the denylist key must be a **hash** of the token (so the denylist itself is not a
credential store) — `denylist:sha256:<hash>`.

**Why the TTL must be exactly the remaining lifetime — both directions.**

- **TTL too long** → the denylist grows without bound. A system with 10k logouts/day and
  a 30-day TTL accumulates 300k keys; with a bug (no TTL at all) it becomes a memory
  leak that takes down Redis.
- **TTL too short** → the entry disappears while the token is still valid, and a revoked
  token starts working again. This is the **silent security regression** that is much
  worse than the first.

**Key rotation + revocation — how they interact.** If you rotate the signing key, you
have two independent levers:

| Lever | Scope | Cost | Use for |
|---|---|---|---|
| **Rotate the key** | Every token signed with the old key | A 401 storm unless you overlap | A suspected **key compromise** |
| **Bump the token version** | Every token of one user | One `INCR` | A suspected **user compromise** |

They are complementary: a key compromise is platform-wide, a user compromise is scoped.
**Rotate the key** when the private key may be exposed; **bump the version** when one
user's tokens may be exposed.

**Revocation and the `aud`/`scope` interplay.** A token can be revoked *for one service*
without being revoked globally — by making the service's audience check stricter, or by
issuing the token with a shorter TTL for a specific `aud`. This is the **granularity
question**, and the answer in most systems is: revocation granularity follows the
**audience** granularity. If you need per-service revocation, you need per-service
tokens.

---

## 4. Internal Working Mechanism

**Where each revocation mechanism hooks into the Spring Security chain.**

```text
FilterChainProxy
  └─ BearerTokenAuthenticationFilter
       ├─ BearerTokenResolver.resolve(request)         → the raw token
       ├─ JwtDecoder.decode(token)
       │    ├─ NimbusJwtDecoder
       │    │    ├─ 1. parse header + payload
       │    │    ├─ 2. select the key from the JWK Set (by kid)
       │    │    ├─ 3. VERIFY THE SIGNATURE              ← hook: key rotation, compromise
       │    │    └─ 4. run the OAuth2TokenValidator chain ← hook: exp/nbf/iss/aud
       │    └─ YOUR DECORATOR  (implements JwtDecoder)  ← hook: denylist + version
       │         ├─ 5. delegate.decode(token)            → the real NimbusJwtDecoder
       │         ├─ 6. if (denylist.contains(jwt.getId())) → throw JwtValidationException
       │         └─ 7. if (!versionMatches(jwt))          → throw JwtValidationException
       └─ JwtAuthenticationConverter → JwtAuthenticationToken → SecurityContextHolder
```

**The three extension points.**

| Extension point | Interface | Use it for |
|---|---|---|
| **A `JwtDecoder` decorator** | `JwtDecoder` | The denylist and version checks — the cleanest place, because it sits *after* the signature is verified and *before* the `Jwt` is handed out |
| **An `OAuth2TokenValidator<Jwt>`** | `OAuth2TokenValidator<Jwt>` | The version check — it is a claim comparison and fits the existing chain perfectly |
| **A `BearerTokenAuthenticationFilter` subclass** | — | Rare. Only if you need to change *where* the token comes from |

> 📌 **Order matters.** The denylist check must run **after** the signature verification.
> If you check the denylist first, an attacker can send random `jti` values and probe
> your denylist — and you pay a Redis lookup for every unauthenticated request.

**The `OAuth2TokenValidator` route for the token version.**

```java
public final class TokenVersionValidator implements OAuth2TokenValidator<Jwt> {

    private final TokenVersionStore versions;

    @Override
    public OAuth2Result validate(Jwt jwt) {
        String claim = jwt.getClaimAsString("ver");
        String stored = versions.current(jwt.getSubject());
        if (claim == null || !claim.equals(stored)) {
            // WHY a generic error code: the response must not tell the attacker
            // whether the token was revoked, expired, or never existed.
            return OAuth2Result.failure(new OAuth2Error(
                    "invalid_token", "The access token is no longer valid", null));
        }
        return OAuth2Result.success();
    }
}
```

**Where the "log out everywhere" write happens.** It is a **write** to the version
store, and it must be **idempotent** and **atomic**:

```java
// WHY INCR and not SET: INCR is atomic and idempotent-safe under retries, and two
// concurrent "log out everywhere" requests must not lose one another's increment.
long newVersion = versions.increment(userId);
```

**The denylist write.** It must be **atomic with the TTL** — otherwise a crash between
the `SET` and the `EXPIRE` leaves a permanent entry. `SETEX` / `SET ... EX` does both in
one round trip; `SET` followed by `EXPIRE` does not.

---

## 5. Step-by-Step Execution Flow

**Request path with the token-version check enabled.**

1. Tomcat accepts the request.
2. `FilterChainProxy` runs the chain; `CorsFilter` → `CsrfFilter` → `BearerTokenAuthenticationFilter`.
3. `DefaultBearerTokenResolver` extracts the token from the `Authorization` header.
4. `JwtDecoder.decode(token)`:
   1. The header and payload are parsed.
   2. The key is selected from the JWK Set by `kid` (cached).
   3. The **signature is verified** with the pinned algorithm. A failure → 401.
   4. The **validator chain** runs: `JwtTimestampValidator` (exp/nbf), `JwtIssuerValidator`
      (iss), the `aud` `JwtClaimValidator`, and the **token-version validator**.
   5. The version validator does `GET ver:<userId>` in Redis and compares it with the
      `ver` claim. A mismatch → `JwtValidationException` → 401.
5. `JwtAuthenticationConverter` maps the claims to authorities.
6. `SecurityContextHolder` is populated.
7. `AuthorizationFilter` makes the authorization decision.
8. The controller runs.

**Logout path.**

1. `POST /api/auth/logout` arrives with the refresh cookie (and maybe an expired access token).
2. `CsrfFilter` validates the CSRF token (the refresh cookie is ambient).
3. The endpoint is `permitAll` — **logout must work with an expired access token**.
4. The service hashes the raw refresh token, finds the row, sets `revoked_at = now`.
5. Optionally: `INCR ver:<userId>` — this kills the outstanding access tokens.
6. The response sets the refresh cookie with `Max-Age=0`.
7. The audit event `LOGOUT` is recorded.
8. **204 No Content**.

---

## 6. Visualizations

### 6.1 The "is this token valid?" decision tree (Mermaid flowchart)

```mermaid
flowchart TD
    A["Bearer token presented"] --> B{"Header parseable?<br/>3 parts, valid JSON?"}
    B -->|No| Z1["401 invalid_token<br/>(malformed)"]
    B -->|Yes| C{"kid present?"}
    C -->|Yes| D{"kid in the JWK Set?"}
    C -->|No| E{"Try every key<br/>in the set"}
    D -->|No| F["Refresh the JWK Set<br/>(unknown-kid path)"]
    F --> G{"Found now?"}
    G -->|No| Z2["401 invalid_token<br/>(no matching key)"]
    G -->|Yes| H
    E --> H["VERIFY SIGNATURE<br/>with the PINNED algorithm"]
    D -->|Yes| H
    H -->|Mismatch| Z3["401 invalid_token<br/>(signature)"]
    H -->|Match| I["CLAIM CHECKS"]
    I --> I1{"exp + 60s > now?"}
    I1 -->|No| Z4["401 invalid_token<br/>(expired)"]
    I1 -->|Yes| I2{"nbf - 60s < now?"}
    I2 -->|No| Z5["401 invalid_token<br/>(not yet valid)"]
    I2 -->|Yes| I3{"iss == expected?"}
    I3 -->|No| Z6["401 invalid_token<br/>(issuer)"]
    I3 -->|Yes| I4{"aud contains THIS service?"}
    I4 -->|No| Z7["401 invalid_token<br/>(audience)"]
    I4 -->|Yes| J{"REVOCATION CHECKS"}
    J --> J1{"ver == ver:sub ?"}
    J1 -->|No| Z8["401 invalid_token<br/>(version revoked)"]
    J1 -->|Yes| J2{"jti in denylist?<br/>(optional)"]
    J2 -->|Yes| Z9["401 invalid_token<br/>(denylisted)"]
    J2 -->|No| K["✅ AUTHENTICATED"]
    K --> L["AuthorizationFilter<br/>→ 403 if unauthorised"]

    style Z1 fill:#ffd6d6,stroke:#c00
    style Z2 fill:#ffd6d6,stroke:#c00
    style Z3 fill:#ffd6d6,stroke:#c00
    style Z4 fill:#ffd6d6,stroke:#c00
    style Z5 fill:#ffd6d6,stroke:#c00
    style Z6 fill:#ffd6d6,stroke:#c00
    style Z7 fill:#ffd6d6,stroke:#c00
    style Z8 fill:#ffd6d6,stroke:#c00
    style Z9 fill:#ffd6d6,stroke:#c00
    style K fill:#d6ffd6,stroke:#0a0
```

**What to look at:** the **order**. The signature is verified *before* the claims, and
*both* are verified before the revocation checks — so an unauthenticated attacker never
causes a Redis lookup. The two revocation checks (`ver` and the `jti` denylist) sit at
the very end, after the token is known to be genuine, which is also where they belong
semantically: they are about **acceptance**, not authenticity.

### 6.2 The six strategies — cost and blast radius (ASCII)

```text
┌────┬──────────────────────────┬────────────────┬──────────────┬──────────────┬───────────┐
│ #  │ STRATEGY                 │ PER-REQUEST    │ STATE        │ BLAST        │ OPS       │
│    │                          │ COST           │              │ RADIUS       │ COMPLEXITY│
├────┼──────────────────────────┼────────────────┼──────────────┼──────────────┼───────────┤
│    │                          │                │              │              │           │
│ 1  │ SHORT TTL ONLY           │  0 ms          │  none        │  = TTL       │  trivial │
│    │ (the default)            │  0 bytes       │  0 keys      │  15 min      │  nothing │
│    │                          │                │              │              │  to build │
│    │                          │                │              │              │           │
├────┼──────────────────────────┼────────────────┼──────────────┼──────────────┼───────────┤
│    │                          │                │              │              │           │
│ 2  │ DENYLIST (by jti)        │  0.2–5 ms      │  1 key per   │  ≈ 0         │  medium  │
│    │                          │  1 Redis GET   │  revoked     │  (next req)  │  + a     │
│    │                          │                │  token       │              │  cleanup │
│    │                          │                │              │              │  job     │
│    │                          │                │              │              │           │
├────┼──────────────────────────┼────────────────┼──────────────┼──────────────┼───────────┤
│    │                          │                │              │              │           │
│ 3  │ TOKEN VERSION (ver)      │  0.2–5 ms      │  1 counter   │  ≈ 0         │  medium  │
│    │ ★ O(1) state             │  1 Redis GET   │  PER USER    │  (next req)  │           │
│    │ ★ kills ALL at once      │                │  (constant!) │              │           │
│    │                          │                │              │              │           │
├────┼──────────────────────────┼────────────────┼──────────────┼──────────────┼───────────┤
│    │                          │                │              │              │           │
│ 4  │ REFRESH DENYLIST ONLY    │  0 ms on API   │  1 row per   │  = access    │  low     │
│    │ (the practical compromise)│  0 ms on API  │  revoked RT  │  TTL         │           │
│    │                          │                │              │              │           │
├────┼──────────────────────────┼────────────────┼──────────────┼──────────────┼───────────┤
│    │                          │                │              │              │           │
│ 5  │ DENYLIST ON LOGOUT ONLY  │  0.2–5 ms      │  1 key per   │  = TTL       │  low–    │
│    │                          │  1 Redis GET   │  logout      │  unless      │  medium  │
│    │                          │                │              │  checked     │           │
│    │                          │                │              │              │           │
├────┼──────────────────────────┼────────────────┼──────────────┼──────────────┼───────────┤
│    │                          │                │              │              │           │
│ 6  │ OPAQUE + INTROSPECTION   │  0.5–5 ms      │  1 row per   │  0           │  high    │
│    │ (RFC 7662)               │  ALWAYS        │  active tok  │  (exact)     │           │
│    │                          │                │              │              │           │
└────┴──────────────────────────┴────────────────┴──────────────┴──────────────┴───────────┘

   ★ THE ONE-LINE SUMMARY
   ─────────────────────
   Strategy 1 is free. Strategies 3 and 4 cost nothing on the hot path.
   Strategies 2 and 5 cost one Redis GET per request.
   Strategy 6 costs a lookup per request AND makes you dependent on the auth server.
```

**What to look at:** rows 1, 3 and 4 are the ones most systems should choose. Row 3 is
the only one with a **constant** state size. Rows 2, 5 and 6 are the ones where the
per-request cost appears — and where the "is it worth it?" question must be answered
with a number.

### 6.3 "Log out everywhere" (Mermaid sequence)

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant API as Orders API
    participant V as Version Store (Redis)
    participant DB as Refresh Token Store
    participant A as Audit

    Note over U: The user clicks "Log out everywhere"

    U->>API: POST /api/auth/logout-all<br/>Authorization: Bearer <jwt>
    API->>API: verify signature + exp/iss/aud (LOCAL)
    API->>DB: UPDATE refresh_token SET revoked_at=now<br/>WHERE user_id=42957 AND revoked_at IS NULL
    DB-->>API: 3 rows revoked
    API->>V: INCR ver:42957          (7 → 8)  ← ATOMIC, idempotent
    V-->>API: 8
    API->>A: audit LOGOUT_ALL user=42957 sessions=3
    API-->>U: 204 No Content<br/>X-Revoked-Sessions: 3

    Note over U,V: The user's OTHER device still holds a valid access token…

    U->>API: GET /api/orders/42<br/>Authorization: Bearer <jwt with ver=7>
    API->>V: GET ver:42957  → 8
    API->>API: claim "7" != stored "8" → REVOKED
    API-->>U: 401 Unauthorized<br/>WWW-Authenticate: Bearer error="invalid_token"

    Note over U,V: The user logs in again → the new token carries ver=8 → works ✓
```

**What to look at:** the `INCR` is the **entire** mechanism. One write, constant state,
works for every token the user has ever received — including tokens on devices the
server has never seen. The `UPDATE` on the refresh-token table is the *second* half;
without it, the user could still refresh.

### 6.4 The key-rotation overlap window (Mermaid sequence)

```mermaid
sequenceDiagram
    autonumber
    participant OP as Operator / Runbook
    participant ISS as Issuer (private key #1 and #2)
    participant JWKS as JWKS endpoint (CDN)
    participant DEC as Resource Server (NimbusJwtDecoder)
    participant API as API client

    OP->>ISS: generate key pair #2 (kid="key-2026-02")
    ISS->>JWKS: ADD key-2026-02 (KEEP key-2026-01)
    OP->>DEC: (nothing to do — the decoder refreshes on unknown kid)
    Note over DEC: JWKS cache now holds 2 keys

    ISS->>ISS: start signing with key-2026-02
    ISS-->>API: new token (kid="key-2026-02")
    API->>DEC: decode(token)
    DEC->>JWKS: GET jwks (cache miss on key-2026-02 → refresh)
    JWKS-->>DEC: {key-2026-01, key-2026-02}
    DEC-->>API: ✅ valid

    Note over ISS,API: ── OVERLAP WINDOW ≥ max token TTL (15 min) + skew (60 s) ──

    API->>DEC: an OLD token (kid="key-2026-01") still arrives
    DEC->>DEC: kid="key-2026-01" is STILL in the JWK Set
    DEC-->>API: ✅ valid (this is why the window exists)

    OP->>JWKS: REMOVE key-2026-01   (T0 + 16 min or later)
    Note over DEC: JWKS now holds only key-2026-02
    API->>DEC: a token signed with key-2026-01 arrives (should be impossible now)
    DEC->>DEC: kid not found → refresh → still not found
    DEC-->>API: ❌ 401 invalid_token "no matching key(s) found"

    Note over OP: ⚠️ If the old key is removed EARLY, every live token dies at once<br/>= a 401 storm that looks exactly like a credential-stuffing attack.
```

**What to look at:** the window is measured from the moment you **start signing** with
the new key, and it must be **longer than the maximum lifetime of a token signed with
the old key**. The last exchange is the failure mode: remove the old key early and you
get a platform-wide 401.

---

## 7. Production-Level Use Cases & Real Industry Examples

**1. Banking — a token version plus a step-up claim.**
A retail bank uses a **5-minute** access token with a `ver` claim and a **per-user
version counter in Redis**. "Log out everywhere" is a **regulatory requirement**, so the
version check runs on **every** request to the payments and accounts APIs — a Redis GET
against a **replicated, same-region** cluster, with a **0.3 ms p99**. A customer
complaint ("I logged out everywhere but I am still logged in on my phone") was traced to
a **stale local cache**: the API had a 60-second Caffeine mirror of the version, and the
phone's request landed on a different instance. The fix was to reduce the mirror TTL to
**5 seconds** for the payments API. Scale: 300,000 customers, 1,200 req/s peak.

**2. Multi-tenant SaaS — a per-tenant denylist.**
A SaaS platform serves 4,000 tenants. When a tenant's compliance team reports a leak,
the platform must invalidate **that tenant's** tokens **immediately**, without touching
the other 3,999. The implementation is a denylist **sharded by tenant**:
`denylist:<tenantId>:<jti>`, with the TTL equal to the token's remaining life. A
"revoke this tenant" operation writes a **tenant-level epoch** (`tenant_epoch:<tenantId>`)
and every token carries the epoch it was issued under — so a tenant-wide revocation is
**one write** and a per-tenant revocation is **O(1)**, exactly like the user-level token
version, but sharded by tenant. Scale: 400k users, 8,000 req/s peak.

**3. An e-commerce platform — revocation off the hot path entirely.**
A high-volume e-commerce API serves **40,000 req/s peak**. The platform's decision:
**no revocation check on the hot path at all.** Access tokens live **10 minutes**,
the refresh token is revocable, and the denylist exists but is consulted **only on the
order-refund and gift-card endpoints** — the high-value operations. The blast radius for
a leaked access token is 10 minutes, which the risk register accepts, and the 40k req/s
hot path keeps **zero** additional network hops. Scale: 40k req/s peak, 2M daily actives.

**4. A service mesh — a token version, checked once per hop.**
An internal mesh issues **5-minute** service tokens. Each service registers its own
`ver` counter, and "revoke this workload" is an `INCR` on that workload's counter —
which the mesh's sidecar performs locally, with **no** network hop in the request path
(the sidecar holds the counter in memory and is notified of bumps via the control
plane). Scale: 50 services, 50,000 token issuances/s.

---

## 8. Worked Examples (with Output + Dry Run)

### 8.1 Worked Example 1 — Token-version-based "log out everywhere"

**Problem.** Implement instant, platform-wide revocation for one user, with **constant**
state.

**Code — the version store.**

```java
package com.example.auth.revocation;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisTokenVersionStore implements TokenVersionStore {

    private static final Duration TTL = Duration.ofDays(90);   // longer than any session

    private final StringRedisTemplate redis;

    public RedisTokenVersionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** WHY a default of "0" for a user who has never logged in: the claim in their
     *  first token is also "0", so the comparison works with no pre-seeding. */
    @Override
    public String current(String userId) {
        String value = redis.opsForValue().get(key(userId));
        return value == null ? "0" : value;
    }

    /** WHY INCR and not GET-then-SET: INCR is atomic, so two concurrent
     *  "log out everywhere" requests cannot lose one another's increment. */
    @Override
    public long increment(String userId) {
        Long next = redis.opsForValue().increment(key(userId));
        if (next != null && next == 1L) {
            // WHY set an expiry only on creation: the counter must outlive every
            // session, but it must not live forever for a user who never returns.
            redis.expire(key(userId), TTL);
        }
        return next == null ? 0 : next;
    }

    private String key(String userId) {
        return "ver:" + userId;
    }
}
```

**Code — the validator.**

```java
package com.example.auth.revocation;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class TokenVersionValidator implements OAuth2TokenValidator<Jwt> {

    private final TokenVersionStore versions;

    public TokenVersionValidator(TokenVersionStore versions) {
        this.versions = versions;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String claim = jwt.getClaimAsString("ver");
        String stored = versions.current(jwt.getSubject());

        if (claim == null || !claim.equals(stored)) {
            // WHY the generic message: the response must not reveal whether the token
            // was revoked, expired, or never existed. A distinguishable error is an oracle.
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "The access token is no longer valid", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
```

**Actual expected output — the Redis state and the HTTP responses.**

```text
# ── BEFORE logout-all ───────────────────────────────────────────────────────
$ redis-cli GET ver:42957
"7"

$ redis-cli --scan --pattern 'ver:*' | head
ver:42957

# The user's access token (still live, 6 minutes left):
#   {"sub":"42957","ver":"7","exp":1780123456,"roles":["CUSTOMER"]}

# ── The logout-all call ─────────────────────────────────────────────────────
POST /api/auth/logout-all   Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
HTTP/1.1 204 No Content
X-Revoked-Sessions: 3

$ redis-cli GET ver:42957
"8"

# ── AFTER logout-all: the SAME token is now rejected ───────────────────────
GET /api/orders/42   Authorization: Bearer eyJhbGciOiJSUzI1NiIs...  (ver=7)
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer error="invalid_token", error_description="The access token is no longer valid"
Content-Length: 0

# ── The user logs in again ──────────────────────────────────────────────────
POST /api/auth/login
HTTP/1.1 200 OK
{"accessToken":"eyJhbGciOiJSUzI1NiIs...","expiresIn":600,"tokenType":"Bearer"}
#   the new token carries "ver":"8"  → it matches the stored value → works ✓
```

**Dry run — step by step.**

1. **Before:** Redis holds `ver:42957 → "7"`. The access token's payload contains
   `"ver":"7"`. The comparison `claim.equals(stored)` → `"7".equals("7")` → **true** →
   the token is accepted.
2. **The `logout-all` request** arrives with a valid access token. The chain verifies
   the signature and the claims (all local), then the version validator compares
   `"7"` with `"7"` → passes → the request is authorised.
3. **The bulk revoke:** `UPDATE refresh_token SET revoked_at = now WHERE user_id = 42957
   AND revoked_at IS NULL` → **3 rows** (the phone, the laptop and the tablet). The
   response header `X-Revoked-Sessions: 3` reports it.
4. **The version bump:** `INCR ver:42957` → Redis atomically changes `"7"` to `"8"`.
   *Note the `expire` is only set when the counter is created (`next == 1L`) — resetting
   the TTL on every increment would keep a dead user's counter alive forever.*
5. **After:** the user's phone still holds an access token with `"ver":"7"`. It calls the
   API. The version validator reads `"8"` and compares → **mismatch** → 401. The token is
   **6 minutes from expiry** and is now **useless**.
6. **The user logs in again.** The issuer reads `versions.current("42957")` → `"8"` and
   puts `"ver":"8"` in the new token. The comparison now succeeds.
7. **The state size:** one integer. Whether the user has 1 or 10,000 live tokens,
   whether they have logged out 1 or 1,000 times — **one key, one value**.

**Takeaway.** Constant state, one atomic write, instant revocation of every token the
user has ever received — including ones the server has never seen. This is the answer
to "how do you implement log out everywhere".

---

### 8.2 Worked Example 2 — A denylist with correct TTL maths

**Problem.** Implement the denylist so that (a) a revoked token stops working, and (b)
the denylist does not grow without bound.

**Code.**

```java
package com.example.auth.revocation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenDenylistService {

    private static final Logger log = LoggerFactory.getLogger(TokenDenylistService.class);

    private final StringRedisTemplate redis;

    public TokenDenylistService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * WHY SETEX and not SET + EXPIRE: two commands means two round trips and a window
     * in which a crash leaves a permanent entry. SET key value EX ttl is atomic.
     */
    public void deny(String jti, Instant expiresAt, String reason) {
        if (jti == null || expiresAt == null) {
            return;    // a token with no jti cannot be denied individually
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;    // already expired — no need to deny it
        }
        String key = "denylist:jti:" + jti;
        redis.opsForValue().set(key, reason, ttl);
        log.info("DENYLIST_ADD jti={} ttlSeconds={} reason={}", jti, ttl.getSeconds(), reason);
    }

    public boolean isDenied(String jti) {
        if (jti == null) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey("denylist:jti:" + jti));
    }

    /** WHY this exists: a cleanup job for a store that has native TTLs is redundant —
     *  but you still want the COUNT, because a growing count is the signal that the
     *  TTLs are wrong. */
    public long denylistSize() {
        Long count = redis.getConnectionFactory().getConnection().dbSize();
        return count == null ? 0L : count;
    }
}
```

**The TTL maths, worked out.**

```text
   Scenario: a token issued at 10:00 with exp = 10:15 (15-minute TTL).
   It is denied at 10:07.

   remaining = exp - now
             = 10:15:00 - 10:07:00
             = 8 minutes
             = 480 seconds

   ⇒ SETEX denylist:jti:f492a307-8d9c-4b4a-918c-7c50b358ee0 480 "explicit_logout"

   ┌──────────────────────────────────────────────────────────────────────┐
   │  The TTL is the token's REMAINING lifetime, not its ORIGINAL lifetime. │
   │  Using 900 (the full 15-min TTL) would keep the entry alive 7 minutes │
   │  after the token died — harmless but wasteful.                        │
   │  Using 0 or no TTL would keep it forever — the denylist leaks.        │
   │  Using a value SHORTER than the remaining lifetime is the dangerous   │
   │  one: the token becomes usable again while still valid.               │
   └──────────────────────────────────────────────────────────────────────┘
```

**Actual expected output — the Redis commands and their replies.**

```text
$ redis-cli SETEX denylist:jti:f492a307-8d9c-4b4a-918c-7c50b358ee0 480 "explicit_logout"
OK

$ redis-cli TTL denylist:jti:f492a307-8d9c-4b4a-918c-7c50b358ee0
(integer) 480

$ redis-cli GET denylist:jti:f492a307-8d9c-4b4a-918c-7c50b358ee0
"explicit_logout"

$ redis-cli EXISTS denylist:jti:f492a307-8d9c-4b4a-918c-7c50b358ee0
(integer) 1

# … 8 minutes later, the token has expired and so has the entry …
$ redis-cli EXISTS denylist:jti:f492a307-8d9c-4b4a-918c-7c50b358ee0
(integer) 0
```

**Dry run — step by step.**

1. `deny(jti, exp, "explicit_logout")` is called at 10:07.
2. `ttl = Duration.between(now, exp)` → **480 seconds**. The guard rejects a
   non-positive TTL (the token is already dead — denying it is a no-op) and a null
   `jti` (a token without a `jti` cannot be denied individually — that is a **token
   issuance policy** issue: your issuer should always set a `jti`).
3. `redis.opsForValue().set(key, reason, ttl)` issues `SET key value EX 480` — one
   atomic command.
4. `isDenied(jti)` issues `EXISTS denylist:jti:<jti>` → `1` → the request is rejected
   with 401.
5. At 10:15, the token's `exp` passes. At that moment Redis expires the entry too.
   `EXISTS` returns `0`. **The denylist entry disappears exactly when the token dies** —
   never before, never after.

**The growth maths.** With 10,000 logout events per day and a 15-minute access token:

```text
   denylist size ≈ logouts_per_second × 900 (the TTL in seconds)
                ≈ 0.12/s × 900
                ≈ 107 entries at any moment
```

That is **tiny**. The denylist only becomes a problem if you set the TTL to the token's
*original* lifetime (15 min) instead of the remaining lifetime, or if you set no TTL at
all. **The TTL is the entire memory-management story.**

**Takeaway.** `SETEX` with the TTL equal to `exp - now` gives you exact revocation with
self-cleaning storage. The denylist is cheap — the expensive part is the **Redis GET on
every request**, not the storage.

---

### 8.3 Worked Example 3 — The key-rotation runbook

**Problem.** Rotate the signing key without a 401 storm. Here is the exact runbook.

**Step 0 — before anything: check the maximum token lifetime.**

```bash
# The overlap window MUST be >= max access-token TTL + clock skew.
grep -rn "ACCESS_TTL\|expiresAt" src/main/java/com/example/auth/TokenService.java
# → static final Duration ACCESS_TTL = Duration.ofMinutes(10);
# ⇒ overlap window = 10 min + 60 s skew = 11 minutes. Round up to 15 for safety.
```

**Step 1 — generate the new key pair and the new `kid`.**

```bash
# In the issuer's KMS / HSM (production). Locally, with OpenSSL:
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out key-2026-02.pem
openssl rsa -in key-2026-02.pem -pubout -out key-2026-02.pub.pem

# Derive the JWK `kid` and the modulus `n` for the JWKS.
python3 - <<'PY'
import json, subprocess
# (real code would use the KMS; this is the local equivalent)
print("kid = key-2026-02")
PY
```

**Step 2 — publish the NEW public key ALONGSIDE the old one. Do not remove anything.**

```bash
# GET the current JWKS
curl -s https://auth.acme.com/.well-known/jwks.json | jq .

# ── current state (1 key) ─────────────────────────────────────────────────
# {
#   "keys": [
#     { "kty": "RSA", "kid": "key-2026-01", "alg": "RS256", "use": "sig", "n": "…", "e": "AQAB" }
#   ]
# }

# POST the NEW key, KEEPING the old one
curl -sS -X POST https://auth.acme.com/admin/jwks \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
        "keys": [
          { "kty": "RSA", "kid": "key-2026-01", "alg": "RS256", "use": "sig", "n": "<OLD_MODULUS>", "e": "AQAB" },
          { "kty": "RSA", "kid": "key-2026-02", "alg": "RS256", "use": "sig", "n": "<NEW_MODULUS>", "e": "AQAB" }
        ]
      }'

# VERIFY both keys are live BEFORE signing with the new one
curl -s https://auth.acme.com/.well-known/jwks.json | jq '.keys | length, map(.kid)'
# → 2
# → [ "key-2026-01", "key-2026-02" ]
```

**Step 3 — switch the issuer to sign with the new key.**

```bash
# Point the issuer at the new key (KMS alias / secret name)
export SIGNING_KEY_ALIAS=key-2026-02
kubectl set env deployment/auth SIGNING_KEY_ALIAS=key-2026-02
kubectl rollout status deployment/auth
```

**Step 4 — verify, then WAIT for the overlap window.**

```bash
# Prove a NEW token verifies and carries the new kid
TOKEN=$(curl -s -X POST https://auth.acme.com/oauth2/token \
  -u client:$CLIENT_SECRET -d 'grant_type=password&username=demo&password=…' \
  | jq -r .access_token)
echo "$TOKEN" | cut -d. -f1 | tr '_-' '/+' | base64 -d 2>/dev/null | jq -r .kid
# → "key-2026-02"    ✓ the issuer switched

# Prove the OLD key still verifies (using a token minted before the switch)
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $OLD_TOKEN" \
  https://api.acme.com/api/orders/42
# → 200   ✓ the overlap is working

# ── WAIT: 15 minutes (max TTL 10 min + 60 s skew + margin) ───────────────
sleep 900
```

**Step 5 — remove the old key.**

```bash
curl -sS -X POST https://auth.acme.com/admin/jwks \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{
        "keys": [
          { "kty": "RSA", "kid": "key-2026-02", "alg": "RS256", "use": "sig", "n": "<NEW_MODULUS>", "e": "AQAB" }
        ]
      }'

curl -s https://auth.acme.com/.well-known/jwks.json | jq '.keys | map(.kid)'
# → [ "key-2026-02" ]    ✓ old key removed
```

**Actual expected output — the whole timeline.**

```text
T+00:00  PUBLISH key-2026-02 alongside key-2026-01      jwks keys = 2
T+00:01  Switch the issuer to sign with key-2026-02
T+00:02  New tokens carry kid="key-2026-02"             → 200 OK ✓
T+00:03  Old tokens carry kid="key-2026-01"             → 200 OK ✓  (overlap working)
T+05:00  …steady state, 0 new errors…
T+15:00  Every token signed with key-2026-01 has expired (max TTL 10 min + skew)
T+15:01  REMOVE key-2026-01                             jwks keys = 1
T+15:02  A straggler token with kid="key-2026-01"        → 401 "no matching key(s) found"
         (expected — its TTL was 10 min and it was minted at T+00:03, so it should
          already be dead. If this happens more than 2 minutes after removal, the
          max-TTL assumption was wrong: CHECK THE ISSUER's ACTUAL TTL.)
T+15:02  Metrics: 401 rate unchanged (0.00%) ✓
```

**Dry run — the failure case, for contrast.**

```text
   IF the old key is removed at T+00:05 instead of T+15:01:
   ─────────────────────────────────────────────────────────────
   T+00:05  REMOVE key-2026-01
   T+00:06  Every token minted in the last 10 minutes (which is ALL of them,
            because the TTL is 10 min) now fails:
              → 401 "no matching key(s) found"
   T+00:06  10,000 req/s × 60 s = 600,000 failed requests in the first minute
   T+00:07  The error-rate alert fires: "credential stuffing attack detected"
            (it is NOT an attack — it is a self-inflicted outage)
   T+00:08  The 401 storm triggers the WAF's rate limiter, which then blocks
            LEGITIMATE traffic too. The outage is now compounded.
```

**Takeaway.** The rotation is a **timeline**, not a switch. Publish → switch → wait →
remove. And the wait is bounded by the **maximum token lifetime** — which is why the
TTL must be a deliberate, documented number.

---

## 9. Code from Scratch — Production-Grade

**The complete revocation module: version store, denylist, validator and the filter
that composes them.**

```java
package com.example.auth.revocation;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * WHY this class owns a POLICY: the fail-open / fail-closed decision must be explicit
 * and per-endpoint, not an emergent property of a library's default. This class takes
 * the policy as a constructor argument so the decision is visible at the wiring site.
 */
@Component
public class RedisRevocationStore implements RevocationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRevocationStore.class);

    /** WHY 90 days: longer than any absolute session lifetime, so a counter is never
     *  lost mid-session; short enough that a churned user's key eventually goes. */
    private static final Duration VERSION_TTL = Duration.ofDays(90);

    private final StringRedisTemplate redis;
    private final boolean failOpen;

    public RedisRevocationStore(StringRedisTemplate redis,
                                @org.springframework.beans.factory.annotation.Value(
                                        "security.revocation.fail-open") boolean failOpen) {
        this.redis = redis;
        this.failOpen = failOpen;
    }

    /** ── Token version ─────────────────────────────────────────────────── */

    @Override
    public String currentVersion(String userId) {
        try {
            String value = redis.opsForValue().get("ver:" + userId);
            return value == null ? "0" : value;
        } catch (DataAccessException ex) {
            log.error("REVOCATION_UNAVAILABLE op=version user={}", userId, ex);
            // WHY the policy decision lives HERE and not in the validator: the
            // validator stays dumb, and the policy is auditable in one place.
            if (failOpen) {
                return "unavailable";       // the sentinel the validator tolerates
            }
            throw new RevocationUnavailableException("version store unavailable", ex);
        }
    }

    @Override
    public long incrementVersion(String userId) {
        Long next = redis.opsForValue().increment("ver:" + userId);
        if (next != null && next == 1L) {
            redis.expire("ver:" + userId, VERSION_TTL);
        }
        return next == null ? 0 : next;
    }

    /** ── Denylist ──────────────────────────────────────────────────────── */

    @Override
    public void deny(String jti, Instant expiresAt, String reason) {
        if (jti == null || expiresAt == null) {
            return;
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return;      // already expired — nothing to deny
        }
        // WHY SET with an expiry, in one command: SETEX / SET..EX is atomic, so a crash
        // can never leave an entry without a TTL (which would leak memory forever).
        redis.opsForValue().set("denylist:jti:" + jti, reason, ttl);
        log.info("DENYLIST_ADD jti={} ttlSeconds={} reason={}", jti, ttl.getSeconds(), reason);
    }

    @Override
    public boolean isDenied(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey("denylist:jti:" + jti));
        } catch (DataAccessException ex) {
            log.error("REVOCATION_UNAVAILABLE op=denylist jti={}", jti, ex);
            if (failOpen) {
                return false;
            }
            throw new RevocationUnavailableException("denylist unavailable", ex);
        }
    }
}
```

**The interface, so the policy can be swapped per service.**

```java
package com.example.auth.revocation;

import java.time.Instant;

public interface RevocationStore {

    String currentVersion(String userId);

    long incrementVersion(String userId);

    void deny(String jti, Instant expiresAt, String reason);

    boolean isDenied(String jti);
}
```

**The `JwtDecoder` decorator — the composition point.**

```java
package com.example.auth.revocation;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/**
 * WHY a decorator and not a validator: the denylist check needs the jti AND the exp
 * (to compute the TTL on write), and a validator only sees the parsed Jwt — which is
 * actually fine for the READ path. The decorator is used for the write path (logout).
 */
public final class RevokingJwtDecoder implements JwtDecoder {

    private final JwtDecoder delegate;
    private final RevocationStore store;

    public RevokingJwtDecoder(JwtDecoder delegate, RevocationStore store) {
        this.delegate = delegate;
        this.store = store;
    }

    @Override
    public Jwt decode(String token) throws JwtValidationException {
        Jwt jwt = delegate.decode(token);          // signature + claims — MUST come first
        String jti = jwt.getId();
        if (jti != null && store.isDenied(jti)) {
            throw new JwtValidationException("The access token has been revoked",
                    java.util.List.of(new org.springframework.security.oauth2.core.OAuth2Error(
                            "invalid_token", "The access token has been revoked", null)));
        }
        return jwt;
    }
}
```

**The wiring — where the fail-open/fail-closed decision is made.**

```java
package com.example.auth.config;

import com.example.auth.revocation.RevocationStore;
import com.example.auth.revocation.TokenVersionValidator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;

@Configuration
public class RevocationConfig {

    @Bean
    TokenVersionValidator tokenVersionValidator(RevocationStore store) {
        return new TokenVersionValidator(store);
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            RevocationStore store) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> defaultValidators =
                JwtValidators.createDefaultWithIssuer("https://auth.acme.com");
        OAuth2TokenValidator<Jwt> version = tokenVersionValidator(store);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidators, version));
        return decoder;
    }
}
```

**The logout service — using the denylist for a single-token revocation.**

```java
package com.example.auth;

import java.time.Instant;

import com.example.auth.revocation.RevocationStore;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogoutService {

    private final RevocationStore revocation;
    private final RefreshTokenService refreshTokens;
    private final TokenVersionStore versions;

    public LogoutService(RevocationStore revocation, RefreshTokenService refreshTokens,
                         TokenVersionStore versions) {
        this.revocation = revocation;
        this.refreshTokens = refreshTokens;
        this.versions = versions;
    }

    /**
     * WHY the access token is optional here: a user usually clicks "log out" AFTER the
     * access token has expired. Requiring a valid access token would make logout fail
     * exactly when it is needed most.
     */
    @Transactional
    public void logout(String rawRefreshToken, String accessToken, String userId) {
        if (rawRefreshToken != null) {
            refreshTokens.revokeByRawToken(rawRefreshToken);
        }
        if (accessToken != null) {
            // WHY deny it explicitly: the user asked to be logged out NOW. The token's
            // TTL would otherwise keep it alive for up to 15 more minutes.
            // The TTL on the denylist entry is the token's REMAINING lifetime, so the
            // entry dies exactly when the token would have.
            revocation.deny(accessToken.jti(), accessToken.expiresAt(), "explicit_logout");
        }
        if (userId != null) {
            // WHY bump the version too: it kills the access tokens on the user's OTHER
            // devices, which a per-token denylist entry cannot reach.
            versions.increment(userId);
        }
    }
}
```

---

## 10. Common Mistakes & Gotchas

### ❌ Mistake 1 — Believing a JWT can be revoked

```java
// ❌ There is no API for this. The signature verifies.
public void revoke(String token) { /* nothing to do */ }
```
**✅ right.** Revoke by **refusing to accept**: a denylist entry or a version bump.

---

### ❌ Mistake 2 — A denylist entry with no TTL

```java
// ❌ The denylist grows forever — a memory leak that eventually takes down Redis
redis.opsForValue().set("denylist:jti:" + jti, reason);
```
**✅ right.** `SET key value EX (exp - now)`.

---

### ❌ Mistake 3 — A denylist TTL longer than the token's life

```java
// ❌ Harmless but wasteful — the entry outlives the token by minutes
redis.opsForValue().set(key, reason, Duration.ofMinutes(15));   // the ORIGINAL TTL
```
**✅ right.** `Duration.between(now, expiresAt)` — the **remaining** lifetime.

---

### ❌ Mistake 4 — A denylist TTL shorter than the token's life

```java
// ❌ THE DANGEROUS ONE: the token becomes usable again while still valid
redis.opsForValue().set(key, reason, Duration.ofSeconds(60));
```
**✅ right.** Always derive the TTL from `exp`.

---

### ❌ Mistake 5 — Checking the denylist before verifying the signature

```java
// ❌ Unauthenticated traffic now causes a Redis lookup per request (DoS amplifier)
if (store.isDenied(jtiFromRawToken)) { throw ...; }
jwt = decoder.decode(token);
```
**✅ right.** Signature first, denylist last.

---

### ❌ Mistake 6 — Using `GET` + `SET` instead of an atomic `SETEX`

```java
// ❌ Two round trips, and a crash between them leaves a permanent entry
redis.opsForValue().set(key, value);
redis.expire(key, ttl);
```
**✅ right.** `redis.opsForValue().set(key, value, ttl)` — one atomic command.

---

## 11. Performance Considerations

**The per-request cost of each revocation layer.**

| Layer | Added latency (p50) | Added latency (p99) | Added network calls |
|---|---|---|---|
| Claims only (no revocation) | **0** | **0** | 0 |
| Token version (Redis GET, same DC) | 0.2–0.5 ms | 1–3 ms | 1 |
| Token version (cross-AZ Redis) | 1–3 ms | 5–15 ms | 1 |
| Token version (local Caffeine mirror, 30 s TTL) | **~0** | ~0 | 0 (amortised: 1 per 30 s per instance) |
| `jti` denylist (Redis `EXISTS`) | 0.2–0.5 ms | 1–3 ms | 1 |
| Introspection (RFC 7662 HTTP call) | 2–15 ms | 20–100 ms | 1 |

**At 40,000 req/s peak (the e-commerce case), the difference is stark:**

| Design | Redis GETs/s | Redis capacity needed | Added p99 |
|---|---|---|---|
| No revocation check | 0 | — | 0 ms |
| Version check on every request | 40,000 | ~1 instance (100k ops/s) | ~3 ms |
| Version check with a 30 s local mirror | 40,000 / 30 ≈ **1,333** (per instance) | negligible | **~0 ms** |
| Denylist on every request | 40,000 | ~1 instance | ~3 ms |

The **local mirror** reduces the Redis load by a factor of **30** and removes the
per-request latency entirely, at the cost of a bounded revocation delay. For a
high-volume API that is almost always the right trade.

**The Caffeine mirror design.**

```java
// WHY a 30-second TTL: the revocation delay is bounded by the TTL, and 30 s is
// short enough for "instant" in human terms while cutting Redis load by 30×.
Cache<String, String> versionCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(30))
        .maximumSize(100_000)
        .build();
```

> ⚠️ **The mirror's correctness caveat.** A local mirror means a revocation is not
> applied everywhere at once. On a 50-instance fleet, the revocation propagates within
> 30 s — but a request that lands on an instance whose cache is stale is honoured.
> For anything high-value, either shorten the TTL or **bypass the cache** on the
> sensitive endpoints. Make that a per-endpoint decision, not a global one.

**The write cost.**

| Operation | Cost | Frequency |
|---|---|---|
| `INCR ver:<userId>` | 0.2–1 ms | Logout-everywhere, role changes — rare |
| `SET denylist:jti:<jti> EX ttl` | 0.2–1 ms | Per logout — rare |
| `UPDATE refresh_token SET revoked_at` | 1–5 ms | Per logout — rare |

All the revocation **writes** are rare; all the revocation **reads** are per-request.
That asymmetry is why the design goal is always "**move the check off the hot path**".

**The denylist memory footprint.**

```text
   size ≈ revocations_per_second × ttl_seconds

   10 logouts/s   × 900 s  =  9,000 keys   ≈ 1.5 MB
   100 logouts/s  × 900 s  =  90,000 keys  ≈ 15 MB
   1,000/s        × 900 s  =  900,000 keys ≈ 150 MB
```

The TTL is the entire memory story. A denylist with **no TTL** grows at the raw
revocation rate forever — 1,000/s is 86M keys/day, and Redis starts evicting.

**The cleanup-job question.** If you use **Redis**, you do **not** need a cleanup job —
the TTL does it, atomically and for free. If you use the **relational DB**, you do need
one:

```sql
-- Nightly, in batches so you do not lock the table:
DELETE FROM token_denylist WHERE expires_at < now();
-- index on expires_at makes this a range scan + delete
```

**What to measure.**

| Metric | Target |
|---|---|
| `auth.revocation.check.duration` (p99) | < **3 ms** (Redis) or < **0.1 ms** (local mirror) |
| `auth.denylist.hit_rate` | ≈ 0 — a spike is an **attack signal** |
| `auth.denylist.size` | Grows with the revocation rate, bounded by the TTL |
| `ver:<userId>` cache hit rate | > **95%** with a 30 s mirror |
| `revocation.store.unavailable` | **Must page** if fail-closed |
| `jwks.key.count` | Should be **2** during a rotation, **1** otherwise |
| `auth.401.by_reason` | A spike in `version revoked` = a mass logout or a bug |

---

## 12. Security Considerations

**The threat model for revocation.**

| Threat | Attack | The defence |
|---|---|---|
| **Stolen access token** | Used until it expires | Short TTL + a denylist entry + a version bump |
| **Token replay after logout** | The user clicks logout, the attacker keeps using the token | Deny the `jti` **and** bump the version |
| **Stolen refresh token** | The attacker renews forever | Rotation + reuse detection + family revocation (chapter 02) |
| **Revocation store down** | Revoked tokens are honoured | An explicit fail-open/fail-closed policy |
| **Denylist probe** | The attacker tests whether a `jti` is denied | The signature is verified **first**; only genuine tokens reach the denylist |
| **Distinguishable errors** | The attacker learns *why* a token was rejected | One generic `invalid_token` for every failure |
| **Cache poisoning of the local mirror** | A stale mirror honours a revoked token | A short TTL; per-endpoint bypass for sensitive paths |
| **Privilege escalation via a stale version** | The user's role changed but the token still says the old one | Bump the version on every role change, not just on logout |
| **Denylist key injection** | A crafted `jti` containing `:` collides with another key's namespace | Validate the `jti` format at issuance (UUID); namespace the keys |
| **Denylist timing oracle** | The attacker measures `EXISTS` latency to enumerate | The key is unguessable (a UUID) and the check happens after signature verification |

**The rules.**

1. **Revoke by refusing to accept**, never by trying to un-sign.
2. **The denylist TTL = the token's remaining lifetime.** Never shorter.
3. **Verify the signature before any revocation lookup.**
4. **One generic error for every rejection reason.**
5. **The fail-open/fail-closed policy is explicit and per-service.**
6. **A local mirror is a cache, never the source of truth.**
7. **Bump the token version on every privilege change**, not only on logout.
8. **Always issue a `jti`** — without it, no per-token revocation is possible.
9. **The refresh token is revocable by default** — it is the revocable root of trust.
10. **Monitor the denylist hit rate** — it is a security signal, not just a metric.

**The "revocation is a database design problem" framing, restated.** The cryptography
is settled. The design questions are the four I listed in §3.3: where the state lives,
what the TTL is, what it costs per request, and what happens when it is unavailable.
A candidate who can answer those four with numbers has answered the question properly.

**The privilege-change case, which is often forgotten.** When a user's role changes —
promotion, demotion, adding a scope — the tokens they already hold are **stale**. Two
options:

- **Bump the version** → every outstanding token for that user dies, and they must
  re-authenticate. Simple, safe, and slightly disruptive (their current request may
  401 mid-session — the client handles it by refreshing).
- **Issue a short TTL for high-privilege tokens** → the staleness window is bounded by
  that TTL. Less disruptive, slower to converge.

Most systems do **both**: a version bump for demotions (kill it now) and a shorter TTL
for elevated roles (converge faster).

---

## 13. Best Practices

1. **Default to a short TTL and no revocation check.** Add machinery only with a reason.
2. **When you need "log out everywhere", use the token version** — constant state, one write.
3. **Use the `jti` denylist for *single, known-compromised* tokens**, not for bulk revocation.
4. **The denylist TTL = `exp - now`, always.** Set atomically with the value.
5. **Check the denylist only after the signature verifies.**
6. **One generic error message for every rejection.**
7. **Make the fail-open/fail-closed policy a configuration value, per service.**
8. **Mirror the version in a local Caffeine cache (30 s) on high-volume APIs**, and
   bypass the cache on sensitive endpoints.
9. **Always issue a `jti`.**
10. **Bump the version on privilege changes**, not just on logout.
11. **Do the denylist write and the refresh-token revoke in one logical operation.**
12. **Rotate the signing key with an overlap window ≥ max token TTL + skew.**
13. **Alert on the JWKS key count** — 2 during a rotation, 1 otherwise, 0 is an outage.
14. **Alert on the denylist hit rate** — a spike is an attack signal.
15. **Prefer Redis for the revocation store** (native TTL, low latency); use the
    relational DB only when you need transactional consistency, and then run a cleanup job.
16. **Never let a revoked token remain valid because of a cache** on a sensitive endpoint.

---

## 14. Hands-On Exercises & Code Challenges

### Exercise 1 — Implement the denylist with a correct TTL

**Steps.**
1. Implement `TokenDenylistService.deny(jti, expiresAt, reason)` using a single atomic
   `SET … EX`.
2. Write a test that denies a token with **8 minutes** remaining and asserts
   `redis-cli TTL` returns **480**.
3. Write a test that denies an **already-expired** token and asserts **no key is created**.
4. Write a test that denies a token with a **null** `jti` and asserts **no key is created**.
5. Use `redis-cli --scan --pattern 'denylist:*'` after 100 denials and assert the count.

**Acceptance criteria.**
- The TTL is always `exp - now`, never a constant.
- `Duration.between` never returns a negative duration into Redis.
- The test proves the entry **disappears** exactly when the token would have expired
  (use a short TTL and a `Thread.sleep` or a virtual clock).
- The test asserts the **atomic** behaviour: after `deny()` returns, the key already has
  a TTL (never a window where it has none).

**Stretch goal.** Implement the same service against **Postgres** with an `expires_at`
column and a nightly `DELETE`, and prove the row count is equivalent to the Redis key
count.

---

### Exercise 2 — Implement the token version and prove "log out everywhere"

**Steps.**
1. Implement `TokenVersionStore` on Redis with `INCR`.
2. Issue an access token with a `ver` claim.
3. Call `logout-all` and assert `ver:<userId>` incremented.
4. Replay the **old** access token and assert a **401**.
5. Log in again and assert the **new** token works.
6. Issue **ten** tokens, bump the version once, and assert **all ten** now fail.

**Acceptance criteria.**
- The version claim is read from the token with `jwt.getClaimAsString("ver")`.
- The comparison is **exact** (no numeric parsing).
- Step 6 proves the **constant-state** property: one increment, ten dead tokens, one
  Redis key.
- A test proves the version survives a **service restart** (it is in Redis, not memory).

**Stretch goal.** Add a `TokenVersionValidator` and prove it composes with
`JwtValidators.createDefaultWithIssuer(...)` inside a `DelegatingOAuth2TokenValidator`
— and that a token with a **wrong `aud`** is rejected by the **audience** validator
(the error must name the audience, not the version).

---

### Exercise 3 — Write the key-rotation runbook and execute it

**Steps.**
1. Write the runbook as a **script** (`rotate-key.sh`) with the five steps and the
   waits, as in §8.3.
2. Execute it against a local JWKS server.
3. Mint a token **before** the rotation and one **after**.
4. Assert the old token still works **during** the window and fails **after** the
   removal.
5. Reduce the wait to 0 and observe the **401 storm** in the logs — and capture the
   error-rate alert firing.

**Acceptance criteria.**
- The script is idempotent-safe: re-running it does not remove a key that is still in
  use.
- It **verifies** the JWKS contains both keys before switching the signer.
- It captures the `kid` of a pre-rotation token and of a post-rotation token.
- Step 5 produces a visible, alarming failure — you have *seen* the failure mode.

**Stretch goal.** Add a **key-count alert** (`jwks.key.count != 1` outside a rotation
window) and prove it fires if the old key is removed early.

---

### Code Challenge 1 — A revocation layer with an explicit, per-endpoint failure policy

**Problem.** Build a revocation layer where the **fail-open/fail-closed** decision is
made **per endpoint**, and where a high-volume path uses a local mirror while a
sensitive path does not.

**Acceptance criteria.**
- A `RevocationStore` interface with a Redis implementation.
- A `FailOpenRevocationStore` decorator and a `FailClosedRevocationStore` decorator —
  or a policy enum passed in.
- A `CaffeineMirroredRevocationStore` that caches the **version** for 30 seconds and
  **never** caches the denylist.
- A `RevocationFilter` (or a `JwtDecoder` decorator) whose behaviour is selected by the
  request path: `/api/payments/**` fails **closed**, `/api/catalog/**` fails **open**.
- Tests: (a) with Redis mocked to throw, `/api/payments` returns **401**;
  (b) `/api/catalog` returns **200**; (c) the mirror serves a stale version for up to
  30 seconds and then refreshes.
- A metric emitted on every fallback: `revocation.fail_open.used` — so the fail-open
  behaviour is **visible**, not silent.

**Stretch goal.** Add a **bounded** fail-open: fail open for at most **60 seconds** of
Redis unavailability, then fail closed automatically — so a long outage cannot silently
extend the revocation window indefinitely.

---

### Code Challenge 2 — A multi-tenant revocation design

**Problem.** A SaaS platform with 4,000 tenants must be able to revoke a single token,
a single tenant, or a single user — with O(1) state for the last two.

**Acceptance criteria.**
- Keys: `ver:user:<userId>`, `tenant_epoch:<tenantId>`, `denylist:jti:<jti>`.
- The token carries `sub`, `tenant_id` and `ver`.
- **Revoke one user** → `INCR ver:user:<userId>` — O(1), one key.
- **Revoke one tenant** → `INCR tenant_epoch:<tenantId>` — O(1), one key. The token
  carries the epoch it was issued under, so a mismatch revokes it.
- **Revoke one token** → `SET denylist:jti:<jti> EX <remaining>`.
- The validator checks all three, in that order, and produces one generic error.
- A test proves that revoking a tenant does **not** affect a token from another tenant.
- A test proves the **key count** is `users + tenants + revokedTokens` — i.e. the state
  does not grow with the number of tokens.

**Stretch goal.** Add a **shard** dimension so that a tenant's keys are namespaced by
hash so you can evict a whole tenant's state with one `SCAN` + `DEL` — and prove the
eviction takes under 100 ms for 10,000 keys.

---

## 15. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: How do you revoke a JWT?**
**A:** You cannot revoke the signature — once it verifies, it verifies forever. What you
control is whether the server **accepts** it. Practically, that means either a short
`exp` so the token dies on its own, a **denylist** of revoked `jti`s that the server
checks, or a **token version** counter compared against a claim. The short TTL is the
default; the denylist is for a specific known-compromised token; the version counter is
for "log out everywhere".

**Q: What is the difference between a denylist and an allowlist?**
**A:** A **denylist** says "these specific tokens are refused" and everything else is
accepted — it is additive, and its size scales with the number of *revocations*. An
**allowlist** says "only these tokens are accepted" — it is subtractive, and its size scales
with the number of *active tokens*. Allowlists are stricter (a token minted by a compromised
issuer is refused by default), but they need a lookup on every request and a write on every
issuance. The **token version** is the elegant middle: the default is accept, and the
allowlist is the user's current version — one integer, one comparison per request.

**Q: What is a denylist and how does it work?**
**A:** A store of the identifiers of tokens that must be refused. On each request, after
the signature verifies, the server checks whether the token's `jti` is in the list — a
single Redis `EXISTS`/`GET`. The entry's TTL must be exactly the token's **remaining**
lifetime, so the list self-cleans and never contains an entry for a token that would
have expired anyway.

**Q: What happens when the user clicks "log out"?**
**A:** The server revokes the **refresh token** (so no new access token can be minted),
deletes the refresh cookie, and — if you want it to be immediate — either adds the
access token's `jti` to the denylist or bumps the user's token version. Without one of
those, the access token simply remains valid until it expires.

### 🟡 Intermediate

**Q: Explain the token-version ("allowlist") approach.**
**A:** You store a counter per user in Redis and put its current value in the access
token as a `ver` claim. On every request you compare the claim with the stored counter;
a mismatch means the token was issued before a revocation and is refused. "Log out
everywhere" is a single atomic `INCR` — and because you never enumerate tokens, it
invalidates every token the user has ever received, including ones on devices the server
has never seen. The state is **one integer per user**, so it does not grow.

**Q: When would you use a denylist rather than a token version?**
**A:** When you need to revoke **one specific token** rather than everything a user
holds — for example, a token that was observed in a log file, or one named in a breach
notification. The denylist is keyed by `jti` and has one entry per revoked token. It is
also the right tool when the revocation is scoped to a *device* rather than a *user*.

**Q: How do you make the denylist check cheap?**
**A:** Three ways. **Shorten the access-token TTL** so the denylist's window is small
enough not to be worth a Redis lookup. **Mirror it locally** — a Caffeine cache with a
30-second TTL per instance, which bounds the revocation delay at 30 s while removing
the per-request network hop and cutting Redis load by 30×. **Check it only where it
matters** — sensitive endpoints only, not the whole API. And note that the denylist is
only useful for tokens that live longer than their revocation SLA.

**Q: What is the difference between a denylist and an allowlist?**
**A:** A denylist says "these specific tokens are refused" and everything else is
accepted — it is **additive** and scales with the number of *revocations*. An allowlist
says "only these tokens are accepted" — it is **subtractive** and scales with the number
of *active tokens*. Allowlists are stricter (a token minted by a compromised issuer is
refused by default) but require you to know about every token, so they need a lookup on
every request. The token-version approach is a hybrid: the *default* is accept, and the
allowlist is the user's current version — a single comparison per user.

**Q: How would you implement "log out everywhere"?**
**A:** Three parts. **Revoke every refresh-token row** for the user — one bulk
`UPDATE`. **Bump the token version** — one `INCR` — which invalidates every outstanding
access token. And **delete the cookies** on the client. The access-token part is the
one people miss: revoking the refresh tokens only stops *future* renewal; the access
tokens already in the wild are still valid until they expire.

---

### 🔴 Advanced / Senior

401 storm that looks exactly like a credential-stuffing attack to your monitoring.

**Q: How do you make "log out everywhere" work without a denylist of thousands of `jti`s?**
**A:** Use the **token version**: a counter per user, stored in Redis, and put its current
value in the access token as a `ver` claim. On every request the resource server compares
the claim with the stored counter; a mismatch means the token predates a revocation.
"Log out everywhere" is then a single atomic `INCR` — and because the mechanism never
enumerates tokens, it invalidates every token the user has ever received, including ones
held on devices the server has never seen. The state is **one integer per user**, constant
regardless of how many sessions or logouts exist.

**Q: What happens to revocation if your Redis cluster goes down?**
**A:** That is an explicit design decision, per service. **Fail closed** — reject every
authenticated request — which is correct for payments and anything with PII, at the
cost of the API being down. **Fail open** — accept the token — which keeps a public
read API alive at the cost of possibly honouring a revoked token during the outage.
The sophisticated answer is per-endpoint: fail open for reads, fail closed for writes.
Whatever you choose, it must be a **documented property of the code**, not an emergent
behaviour of a library's exception handling, and it must be **visible in a metric** so
you know when it is being used.

**Q: How do you choose between Redis, Postgres and an in-memory cache for the
revocation store?**
**A:** The deciding question is whether the store is **authoritative** or a **cache**. If
authoritative, it must be shared and durable: **Redis** for latency and native TTL, or
**Postgres** if you need transactional consistency with other data and can run a
cleanup job. If it is only a **cache** — a local mirror to save the per-request hop —
then **Caffeine** is right, and you accept the propagation delay. The trap is using
Caffeine as the source of truth: a revocation performed on one instance is invisible
to the others, and a restart loses the copy entirely.

**Q: What is the cost of the token-version check, and can you avoid it?**
**A:** One Redis `GET` — 0.2–0.5 ms in the same datacentre, 1–3 ms cross-AZ. At 40,000
req/s that is 40,000 GETs/s, which a single Redis handles, but it adds ~3 ms to your
p99 and makes Redis a hard dependency of the request path. You can avoid it with a
**local Caffeine mirror** of the version (a 30-second TTL), which reduces the Redis load
by 30× and removes the per-request latency, at the cost of a 30-second revocation
delay. On high-value endpoints you bypass the mirror and pay the full cost.

### ⚫ Expert / Principal

is acceptable. **Tier 3 (everything else)**: no revocation check at all, a 5-minute TTL,
and the token version checked only on the endpoints that mutate state. The state is
**one denylist** plus **one version counter per user**, and the failover story is that
the payments API degrades to 503 rather than becoming an open door. I would also make
the 60-second SLA a **measured SLO** — `time_from_report_to_first_rejected_request` —
with an alert, because an unmeasured SLA is a wish.

**Q: Design the revocation architecture for a payments platform where the compliance
requirement is "a token must be revocable within 60 seconds of a fraud report, and the API
must stay up during a revocation-store outage".**
**A:** The two requirements conflict, so the design separates them by **value tier**. The
**payments API** uses a Redis denylist with `SETEX` (TTL = the token's remaining life),
checked on every request, **fail closed** — because only a live check guarantees the 60-second
SLA. When Redis is unavailable it returns **503** (not 401): "we cannot check right now" and
"your token is invalid" are different failures and the client must handle them differently. The
**customer-account API** uses the same store through a **30-second Caffeine mirror** and
**fail open** — the data is not financial and a 30-second revocation delay is acceptable. The
**public catalogue** has no revocation check at all — a 10-minute TTL, and the data is public.
The state is one denylist plus one counter per user, and the failover story is that the
payments API degrades to 503 rather than becoming an open door.

**Q: How do you reason about the revocation/availability trade-off to a non-technical
stakeholder?**
**A:** Translate it into money and time. "We can make a stolen token useless **immediately** —
that costs one extra lookup per request, about 3 milliseconds and a measurable Redis bill. Or
we can make it useless after **15 minutes**, which costs nothing per request but means that
for up to 15 minutes after a compromise we do not know who did what. For the payments API I
would pay the 3 milliseconds; for the public catalogue I would not." Then I state the
unavailability side explicitly: "if the revocation store is down, the payments API returns 503
and no money moves, rather than 200 and money moves for someone it should not." That framing
turns a technical debate into a business decision, which is what actually gets made.

**Q: Why is "JWT revocation is a database design problem" the right framing?**
**A:** Because the cryptography is **settled** — the signature verifies or it does not,
and there is no third option. Everything that remains is a **state-management** problem:
where the state lives, what its lifetime is, what it costs to read on the hot path,
how it is cleaned up, and how the system behaves when it is unavailable. Each of those
is a database question: schema, index, TTL, transaction boundary, replica, failover
policy. Framing it as "security" invites hand-wavy answers ("we store revoked tokens in
a cache"); framing it as "data" forces you to say *which store, with what key, with
what TTL, and what does it cost per request* — which is the answer an interviewer is
actually grading.

**Q: How would you revoke tokens after a signing-key compromise, and how does that
differ from a user compromise?**
**A:** They are different scopes, and using the wrong one is a common and expensive
mistake. A **user compromise** (one account) is scoped: bump that user's token version
and revoke their refresh-token families. One `INCR`, one bulk `UPDATE`, seconds of
impact. A **key compromise** (the private key) is platform-wide: every token ever
signed with that key must die, which means rotating the key — and that is a
**coordinated, staged operation**, because rotating without an overlap window kills
every live token at once. The key-rotation runbook (publish → switch → wait → remove)
is the answer, and the overlap window must exceed the maximum token lifetime. A third
option, if you need an *immediate* kill, is to publish a JWKS with **zero** keys or a
deliberately invalid key, which is a deliberate, total, immediate outage of
authentication — the right move only when the key is actively being used by an attacker.
The lesson: **have both runbooks written and rehearsed**, because you cannot design them
under pressure.

## 16. Scenario-Based & Real-Experience Scenarios

### Scenario 1 — "Logout does not actually log the user out"

**The situation.** A support ticket: *"I clicked logout but the app still shows my
orders."* Reproduced on a phone browser. The user stays logged in for the full 15 minutes.

**Your first move.**

```bash
# 1. Is the access token still being accepted after logout?
#    Capture the token, call logout, then call the API with the SAME token:
TOKEN=$(curl -s -X POST https://app.acme.com/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"…"}' | jq -r .accessToken)
curl -s -o /dev/null -w 'before logout: %{http_code}\n' \
  -H "Authorization: Bearer $TOKEN" https://app.acme.com/api/orders/42
curl -s -o /dev/null -w 'logout:        %{http_code}\n' \
  -X POST -b "rt=$RT" https://app.acme.com/api/auth/logout
curl -s -o /dev/null -w 'after logout:  %{http_code}\n' \
  -H "Authorization: Bearer $TOKEN" https://app.acme.com/api/orders/42
# → before: 200   logout: 204   after: 200   ← the bug
```

**Expected signal.** The token still returns **200** after the logout. The refresh token
was revoked (the next refresh returns 401), but the access token was untouched.

**The trap.** "The access token is stateless, that's expected." It is expected — but it
is a **design choice**, and the product requirement ("log out means logged out") is not
satisfied. The fix exists and is cheap.

**The resolution.** Two options, and the choice is a product decision:
(a) **Deny the `jti`** — write `SETEX denylist:jti:<jti> <exp - now>` and check the
denylist on every request. Cost: one Redis GET per request.
(b) **Bump the token version** — `INCR ver:<userId>`. Cost: one Redis GET per request,
but it also logs the user out on **all** devices.
I would implement (b) for "log out everywhere" and (a) for "log out this device" — and
I would tell the product team that "log out" on a stateless API means "log out within
15 minutes" unless we pay for one of these.

**The lesson.** The gap between "the refresh token is revoked" and "the user is logged
out" is exactly the TTL. Name it, and make the trade explicit.

---

### Scenario 2 — "The denylist check adds 8 ms to every request"

**The situation.** After adding the denylist check, the API's p99 latency went from
**35 ms to 61 ms**. The team wants to remove the check.

**Your first move.**

```bash
# 1. Where is the 8 ms going? Network, or something else?
redis-cli --latency -h <redis-host>
redis-cli --latency-history -h <redis-host> | head -20
redis-cli info commandstats | grep -A2 cmdstat_get

# 2. Is the Redis in the same AZ / VPC as the API?
aws ec2 describe-route-tables | grep <redis-subnet>
kubectl get endpoints <redis-service> -o wide

# 3. Is the pool exhausted (a much bigger problem)?
curl -s localhost:8080/actuator/metrics/lettuce.pool.active | jq '.measurements'

# 4. What is the Redis hit rate? (Are we asking for keys that never exist?)
curl -s localhost:8080/actuator/metrics/jwt.denylist.check | jq '.measurements'
```

**Expected signal.** `redis-cli --latency` shows **p99 = 7.8 ms** from the API's
subnet, and `redis-cli info commandstats` shows `cmdstat_get` with a `usec_per_call`
around 7,500. The Redis instance is in a **different availability zone** from the API —
each request pays a cross-AZ hop. The hit rate on the denylist is **99.99% negative**:
almost every key does not exist.

**The trap.** "The Redis is slow, so let's raise the timeout." That makes it worse —
requests would queue.

**The resolution.** Three moves, in order of value:
1. **Move the Redis into the same AZ** as the API (or use a read replica in the same AZ).
   7.8 ms → ~0.3 ms.
2. **Add a local Caffeine mirror** with a 30-second TTL. The Redis load drops by ~30×
   and the per-request latency goes to **~0**. The revocation delay becomes 30 s —
   which must be checked against your SLA.
3. **Only check the denylist on sensitive endpoints**, and use the version counter (or
   nothing) elsewhere.

**The lesson.** Measure the *network*, not the Redis. And remember that a denylist that
is 99.99% "not found" is a **terrible use of a network hop** — that is the signal that
a local mirror is the right answer.

---

### Scenario 3 — "Every user got a 401 after the key rotation"

**The situation.** At 09:05 the on-call rotated the signing key following the runbook.
Within two minutes, **100%** of requests return 401 with
`"no matching key(s) found"`. The error-rate alert fired and the WAF started blocking
traffic.

**Your first move.**

```bash
# 1. What does the JWKS actually contain right now?
curl -s https://auth.acme.com/.well-known/jwks.json | jq '.keys | map(.kid)'

# 2. What kid does the failing token carry?
echo "$TOKEN" | cut -d. -f1 | tr '_-' '/+' | base64 -d 2>/dev/null | jq -r .kid

# 3. When did the old key disappear?
#    (the JWKS publish API should keep an audit trail)
curl -s -H "Authorization: Bearer $ADMIN" https://auth.acme.com/admin/jwks/history | jq .

# 4. What is the actual max token TTL? (the runbook's assumption)
grep -rn "ACCESS_TTL" src/main/java/com/example/auth/TokenService.java
```

**Expected signal.** The JWKS contains **only** `key-2026-02`. The failing tokens
carry `kid: "key-2026-01"`. The publish history shows the old key was removed at
**09:05**, and the runbook says the overlap was 15 minutes — but the issuer's actual
`ACCESS_TTL` is **45 minutes** (someone changed it two months ago and did not update
the runbook). The tokens signed with the old key are still within their 45-minute
lifetime.

**The trap.** "Just publish the old key back." That works — but the correct first move
is to **publish the old public key back immediately**, because every minute of delay is
more failed requests. The public key is public; re-publishing it is harmless and
instantly restores service.

**The resolution.**
1. **Immediately** re-publish `key-2026-01` alongside `key-2026-02`. Service is
   restored within seconds. *(This is why the removal step must be a separate, later,
   human action.)*
2. Wait the true maximum TTL (45 min + skew) from the moment the issuer switched.
3. Remove the old key — with a **verification step** that no live token carries the old
   `kid`.
4. **Fix the runbook**: derive the window from the *configured* TTL, and better, make
   the removal step **automated and conditional** — the issuer knows the max TTL, so the
   removal can be scheduled from the switch time rather than typed by a human.

**The lesson.** The overlap window must be derived from the **configured** TTL, not from
a number in a document. Automate the removal, or make it a two-person, verified step.
A human-typed "15 minutes" in a runbook is a latent outage.

---

## 17. Counter-Questions, Tricky Questions & Follow-Ups

### CQ1. "If the access token is only 5 minutes, why would you ever add a denylist?"

- **Naive answer:** "For instant revocation."  ← *true, and it misses the redundancy*
- **The trap:** The trap is not noticing that a 5-minute TTL **already** bounds the
  blast radius to 5 minutes. A denylist shrinks that to "the next request" — but if
  your SLA is "5 minutes", the denylist buys nothing. You are paying a per-request
  network hop to buy a capability you did not need.
- **Expert answer:** You add the denylist when your **revocation SLA is shorter than
  your TTL**. If the compliance requirement is "a token must be dead within 60 seconds",
  then a 5-minute token is already too long and you need a live check. The other
  legitimate reason is **granularity**: the denylist can revoke *one* token without
  touching the user's other sessions, which the version counter cannot do. So the
  decision rule is: *TTL against your SLA, and granularity against your product
  requirements*. If both are satisfied by the TTL alone, build nothing.

### CQ2. "Why not use an allowlist (only accept tokens you recorded) instead of a
denylist?"

- **Naive answer:** "An allowlist is more secure."  ← *true, and it misses the scaling*
- **The trap:** The trap is treating the allowlist as "the secure option" without
  pricing it. An allowlist requires you to **know** every valid token — which means a
  write on every issuance and a read on every request, and the store grows with the
  number of *active* tokens rather than the number of *revoked* ones.
- **Expert answer:** Allowlists are stricter — a token minted by a compromised issuer
  that you never recorded is refused by default — and that is genuinely valuable in
  high-value systems. But the cost model is inverted: the allowlist grows with
  **tokens issued**, the denylist grows with **tokens revoked**. For a system with 10M
  active sessions, the allowlist is 10M entries. The token-version approach is the
  elegant middle: it behaves like an allowlist (the default is "this user's tokens
  issued under the current version are accepted") while storing **one integer per
  user**. That is why I reach for the version first and the allowlist only in
  genuinely high-value, low-volume contexts.

### CQ3. "You said the token version gives constant state. But it still needs a Redis GET
on every request — isn't that the same cost as a denylist?"

- **Naive answer:** "Yes, it's the same cost."  ← *true, and it misses the difference*
- **The trap:** The trap is comparing the *cost* while ignoring the *cardinality* and
  the *operation*. Both are one Redis GET. But the denylist is a lookup **per token**
  (so its key space grows with revocations and its hit rate is essentially zero), while
  the version is a lookup **per user** (a tiny, hot key space with a ~100% hit rate).
- **Expert answer:** The costs are identical per request, but the *characteristics* are
  completely different. The version key is **tiny and hot** — a handful of keys for
  thousands of users — so it is served from cache, it has a ~100% hit rate, and it can
  be **mirrored locally** with a tiny memory footprint. The denylist is a **large, cold,
  mostly-miss** key space — a bad fit for caching and a source of memory pressure. So
  the version is the one worth optimising and the one you can put in front of a local
  cache cheaply. And the *capability* difference is decisive: the version can revoke
  tokens you have never seen.

### CQ4. "What if the token version counter is lost — say Redis is wiped?"

- **Naive answer:** "Then everyone's tokens are rejected."  ← *true, and it misses the
  silent failure*
- **The trap:** The trap is assuming "lost" means "reset to zero". If Redis is wiped
  and the counter resets to "0" while the tokens in the wild say "ver: 7", then **every
  outstanding token fails** — a mass 401. But if the fallback is "store nothing when
  Redis is unavailable" and the validator treats a missing value as "any version is
  fine", then **every revoked token becomes valid again** — which is the *silent*
  failure and the far more dangerous one.
- **Expert answer:** This is why the fallback must be **explicit**. My policy: when the
  version store is unavailable, the behaviour is the **fail-open/fail-closed** choice
  from §3.4, and it is per-service. For a payments API it is fail-closed (the API
  returns 503 — "temporarily unavailable" — not 401, because the client must know the
  difference). For a read API it may be fail-open. And for durability I would not rely
  on Redis alone for the counter: persist it in the relational DB (which is the source
  of truth for the user) and cache it in Redis, so a Redis wipe costs a cache miss, not
  a revocation reset. The general principle: **the revocation state must have a
  durability story**, and "it was in Redis" is not one.

### CQ5. "Is it safe to fail open on the version check?"

- **Naive answer:** "It depends."  ← *true, and too vague to be useful*
- **The trap:** The trap is treating "fail open" as a single global decision. It is
  **per-endpoint**, and the *right* answer is usually "yes for reads, no for writes".
- **Expert answer:** Fail open means a token revoked a moment ago may still work. The
  question is **what that token can do**. For a public catalogue, the data is public —
  there is nothing to protect, and the availability win is worth more than the
  revocation win. For a payment, a data export, or a permission change, no. The
  engineering discipline is: (1) make the policy a **configuration value** with an
  explicit default, (2) apply it **per endpoint**, not globally, (3) emit a **metric**
  every time the fallback is used, so the fallback is visible and alertable, and (4)
  never let the fallback be silent. An invisible fail-open is a security regression
  nobody will notice until an incident review.

### CQ6. "Why is the denylist nearly useless for a short-lived token, mathematically?"

- **Naive answer:** "Because the token expires soon anyway."  ← *true, and it misses the
  probability argument*
- **The trap:** The trap is stopping at "it expires soon". The interesting question is
  **how much of the window a denylist actually covers**, and it turns out to be a
  shrinking fraction.
- **Expert answer:** If a token has a TTL of *T* and you revoke it at a uniformly random
  moment, its **expected remaining life** is *T*/2 — so on average the denylist only
  buys you half the TTL, and if you revoke it at the *start* of its life, it buys you
  nothing at all. So the denylist's value depends entirely on *when* you detect the
  compromise, which you usually cannot control. For a 5-minute token, the average
  benefit is 2.5 minutes — for one Redis GET on every request. For a 24-hour token, the
  average benefit is 12 hours — for the same Redis GET. **The denylist's value scales
  with the TTL, and so does its cost** — which is exactly why the right move is to
  lengthen the token and shorten the TTL, not the other way round.

### CQ7. "Can you revoke a token without a per-request check?"

- **Naive answer:** "No."  ← *true, and it misses a class of solutions*
- **The trap:** The trap is assuming revocation requires a *lookup*. It requires a
  **decision**, and the decision can be encoded elsewhere.
- **Expert answer:** Several ways. **Shorten the TTL** — the check is "compare `exp` with
  now", which is free. **Change the key** — rotate the signing key, which invalidates
  every token without a per-request cost (at the price of a coordinated rotation and a
  401 storm without an overlap). **Change the trust anchor** — for example, issue tokens
  from a *different issuer* and retire the old one, which is the OIDC "issuer
  migration" pattern. **Change what the token means** — if the token carries a role id
  rather than the role itself, then revoking the *role* in the database revokes the
  authority, without touching the token. That last one is the most interesting: it
  moves revocation from the *token* layer to the *authorisation* layer, which is where
  it naturally belongs when the authority is stateful anyway.

### CQ8. "Why did you choose a `jti` denylist key rather than a token hash?"

- **Naive answer:** "Because `jti` is unique per token."  ← *true, and it misses the
  opaque case*
- **The trap:** The trap is that `jti` only exists if the **issuer** puts it there. For
  an **opaque** token there is no `jti` — you have to derive a key yourself, and the
  obvious choice is a hash of the token, which is exactly right. But a subtle point: if
  you use the **raw token** as the denylist key, the denylist becomes a **credential
  store** — anyone who can read Redis can use the tokens in it.
- **Expert answer:** The rule is: **the denylist key must be a non-reversible identifier
  of the token.** For a JWT with a `jti`, that is the `jti` itself (a UUID — not a
  credential). For an opaque token, it is `SHA-256(token)` — a hash, so that a Redis
  dump does not hand the attacker usable credentials. Both give the same property: the
  denylist can be read by anything that can read Redis without becoming a security
  incident. And I would namespace the keys (`denylist:jti:<jti>`) so that a crafted
  `jti` cannot collide with another namespace — and validate the `jti` format at
  issuance.

## 18. Frequently Asked Production Issues & Debugging Scenarios

### Issue 1 — Logout does not actually log the user out

**Symptom.** The access token still works after `/logout` returns 204.

**Diagnostic commands.**

```bash
# 1. Prove it: capture the token, log out, replay the token.
TOKEN=$(curl -s -X POST https://app.acme.com/api/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"demo","password":"…"}' | jq -r .accessToken)
curl -s -o /dev/null -w 'after logout: %{http_code}\n' \
  -H "Authorization: Bearer $TOKEN" https://app.acme.com/api/orders/42

# 2. Is the refresh token actually revoked?
psql -d app -c "SELECT revoked_at, used_at FROM refresh_token
                WHERE user_id=42957 ORDER BY issued_at DESC LIMIT 3;"

# 3. Is the denylist being written?
redis-cli --scan --pattern 'denylist:jti:*' | head
redis-cli GET "denylist:jti:$(echo $TOKEN | cut -d. -f1 >/dev/null; echo f492a307-8d9c-4b4a-918c-7c50b358ee0)"

# 4. Is the version counter moving?
redis-cli GET ver:42957
```

**Expected signal.** The refresh-token row has `revoked_at` set, but the denylist is
**empty** and `ver:42957` is unchanged. The logout service revokes the refresh token
and nothing else — the classic "I revoked the refresh token and thought that was
enough".

**The fix.** Add the access token's `jti` to the denylist (with the TTL = the token's
remaining life) and/or bump the version. Decide per product requirement.

---

### Issue 2 — The denylist check adds 8 ms to every request

**Symptom.** p99 latency rose by 8 ms after the denylist check was added.

**Diagnostic commands.**

```bash
# 1. Is it the network?
redis-cli --latency -h <host>
redis-cli info commandstats | grep cmdstat_get

# 2. Same AZ?
kubectl get endpoints <redis-service> -o wide

# 3. Pool exhaustion?
curl -s localhost:8080/actuator/metrics/lettuce.pool.active | jq '.measurements'

# 4. Hit rate — is the denylist 99.99% "not found"?
curl -s localhost:8080/actuator/metrics/jwt.denylist.check | jq '.measurements'
```

**Expected signal.** `redis-cli --latency` reports **p99 = 7.8 ms**; the Redis endpoint
resolves to a **different AZ**; and the denylist hit rate is **~0%** — almost every
check is a miss on a cold, large key space.

**The fix.** (1) Co-locate Redis with the API (7.8 ms → 0.3 ms). (2) Mirror the version
in a local Caffeine cache with a 30-second TTL (latency → 0, load ÷ 30). (3) Check the
denylist only on sensitive endpoints. The 99.99%-miss hit rate is the signal that a
network hop is the wrong shape for this lookup.

---

### Issue 3 — All users get 401 after a key rotation

**Symptom.** A platform-wide 401 storm, `no matching key(s) found`.

**Diagnostic commands.**

```bash
# 1. What is in the JWKS now, and what kid do the failing tokens carry?
curl -s https://auth.acme.com/.well-known/jwks.json | jq '.keys | map(.kid)'
echo "$TOKEN" | cut -d. -f1 | tr '_-' '/+' | base64 -d 2>/dev/null | jq -r .kid

# 2. When was the old key removed?
curl -s -H "Authorization: Bearer $ADMIN" https://auth.acme.com/admin/jwks/history | jq .

# 3. What is the ACTUAL max token TTL (not the runbook's number)?
grep -rn "ACCESS_TTL\|expiresAt" src/main/java/com/example/auth/TokenService.java
```

**Expected signal.** The JWKS has only `key-2026-02`; the failing tokens carry
`kid: "key-2026-01"`; and the configured TTL is **45 minutes**, while the runbook said
15. The old key was removed before the real maximum lifetime elapsed.

**The fix.** **Immediately** re-publish the old public key (it is public — re-publishing
is harmless) to restore service, wait the *true* maximum TTL, then remove it with a
verification step. Automate the removal from the switch time. The lesson: **derive the
window from the configuration, never from a typed number.**

---

## 19. Architecture Design Questions

### 19.1 Design the revocation architecture for a platform with three service tiers

**The prompt.** "We have three kinds of service: a public catalogue (60% of traffic), a
customer account API (30%), and a payments API (10%). Revocation requirements differ
per tier. Design it."

**Reference design.**

```text
┌────────────────────────────────────────────────────────────────────────────┐
│  TIER 1 — PUBLIC CATALOGUE        60% of traffic · ~12,000 req/s            │
│  ─────────────────────────────                                           │
│  Revocation: NONE (TTL only, 10 min)                                       │
│  Rationale: the data is public. A revoked token can read public data.      │
│  Cost: 0 extra latency, 0 extra state.                                     │
├────────────────────────────────────────────────────────────────────────────┤
│  TIER 2 — CUSTOMER ACCOUNT API    30% of traffic · ~6,000 req/s             │
│  ─────────────────────────────                                           │
│  Revocation: TOKEN VERSION via a local Caffeine mirror (30 s TTL)          │
│  Rationale: PII and order history. A 30-second revocation delay is fine.    │
│  Cost: ~0 latency, 1 Redis GET per instance per 30 s (≈200 GET/s total).  │
│  Fail policy: FAIL OPEN (the data is not financial).                       │
├────────────────────────────────────────────────────────────────────────────┤
│  TIER 3 — PAYMENTS API             10% of traffic · ~2,000 req/s             │
│  ─────────────────────────────                                           │
│  Revocation: TOKEN VERSION + jti DENYLIST, both live, no mirror            │
│  Rationale: instant revocation is a compliance requirement.               │
│  Cost: ~0.4 ms p99 (same-AZ Redis) × 2,000 req/s = 2,000 GET/s.          │
│  Fail policy: FAIL CLOSED → 503 Service Unavailable (NOT 401).            │
└────────────────────────────────────────────────────────────────────────────┘
```

**The decisions and their reasons.**

1. **Tier 1 has no revocation at all.** The data is public; a 10-minute TTL is a
   sufficient answer; and this tier carries 60% of the traffic, so a per-request check
   here would dominate the infrastructure cost for no benefit.
2. **Tier 2 uses the version with a local mirror.** The 30-second delay is acceptable
   for account data, and the mirror turns 6,000 req/s into ~200 Redis GET/s.
3. **Tier 3 has no mirror** — a fresh check on every request — because the SLA is
   instant.
4. **Tier 3 returns 503, not 401, when Redis is down.** A 401 tells the client "your
   token is invalid, log in again"; a 503 tells it "try again later". Conflating them
   causes a mass logout during a Redis incident.
5. **The denylist is checked only on Tier 3.** It is a cold, mostly-miss lookup, and
   Tier 3's traffic is small enough to afford it.

**The trade-offs the interviewer wants to hear.**

| Decision | Gain | Cost |
|---|---|---|
| No revocation on Tier 1 | Zero cost on 60% of traffic | Up to 10 minutes of stale authority |
| 30-second local mirror | Near-zero latency, 30× less Redis load | Up to 30 s of revocation delay |
| Live checks on Tier 3 | Instant revocation | ~0.4 ms p99 and a Redis dependency |
| 503 on failure | Correct client behaviour | Payments are unavailable during a Redis incident |
| One shared store, tiered policies | One operational surface | The policy configuration is a matrix |

**What I would say at the end.** "Revocation is not one mechanism — it is a **policy per
tier**, and the policy is expressed by two numbers: how fast must it be dead, and what
does it cost if the store is down. I would put the strictest policy where the money is,
and nowhere else."

---

### 19.2 Design the key-management and revocation system for a multi-tenant platform

**The prompt.** "We serve 4,000 tenants on one deployment. Tenants need independent
key rotation and independent revocation. A tenant must never be able to affect
another tenant. Design the key and revocation system."

**Reference design.**

```text
                    ┌──────────────────────────────────────────────┐
                    │                 ISSUER                        │
                    │  Per-tenant signing key pair (or per-tenant   │
                    │  kid under a shared pair — see trade-offs)    │
                    │  kid = "tenant-<tenantId>-v3"                 │
                    └───────────────┬──────────────────────────────┘
                                    │
                    ┌───────────────▼──────────────────────────────┐
                    │  JWKS endpoint — SHARDED                     │
                    │  GET /.well-known/jwks.json?tenant=<id>      │
                    │  GET /.well-known/jwks.json  → all tenants  │
                    │  (CDN-cached; the API requests only what it │
                    │   needs via the kid)                        │
                    └───────────────┬──────────────────────────────┘
                                    │
   ┌────────────────────────────────▼─────────────────────────────────┐
   │  REVOCATION — THREE INDEPENDENT SCOPES                            │
   │                                                                   │
   │  ① USER SCOPE     ver:user:<userId>          → INCR             │
   │  ② TENANT SCOPE   epoch:tenant:<tenantId>    → INCR             │
   │  ③ TOKEN SCOPE    denylist:jti:<jti>         → SETEX            │
   │                                                                   │
   │  The token carries: sub, tenant_id, ver, tenant_epoch            │
   │  The validator checks all three in that order.                    │
   └───────────────────────────────────────────────────────────────────┘
```

**The decisions and their reasons.**

1. **Per-tenant signing keys**, keyed by `kid = "tenant-<tenantId>-v3"`. A tenant can
   be rotated independently, and a compromised tenant's key does not affect the other
   3,999. *Trade-off:* 4,000 key pairs and a large JWKS. The mitigation is a **sharded
   JWKS** — the API fetches only the tenant ids it serves, and the CDN caches the
   shards.
2. **A tenant epoch**, so a tenant-wide revocation is **one `INCR`** rather than
   enumerating tokens. The token carries the epoch it was issued under.
3. **The user version**, for "log out this user's sessions everywhere".
4. **The `jti` denylist**, for a single known-compromised token.
5. **The tenant is derived from the *validated* claim** — never from an `X-Tenant-Id`
   header, which the client controls.

**The trade-offs.**

| Decision | Gain | Cost |
|---|---|---|
| Per-tenant keys | Independent rotation, blast-radius isolation | 4,000 key pairs; a large JWKS; more KMS cost |
| A shared key pair with per-tenant `kid` | One key pair, still per-tenant revocation | Rotation is platform-wide (you cannot rotate one tenant's key) |
| Sharded JWKS | The API fetches only what it needs | More endpoint complexity; the CDN key space grows |
| Three revocation scopes | Granular, O(1) per scope | Three keys per user and three lookups per request |
| Tenant from the claim | Cannot be spoofed | A token's tenant is fixed for its lifetime |

**What I would say at the end.** "Tenancy must be **sourced from the validated claim**,
because every other tenancy signal — a header, a sub-domain, a path prefix — is
client-controlled and therefore forgeable. And the revocation system must be
**hierarchical**: a user inside a tenant inside a platform. Each scope is one counter,
each counter is O(1), and the combination gives you three independent blast-radius
controls without ever enumerating a token."

---

## 20. Rapid-Fire MCQs

**1.** Can a signed JWT be revoked?
- A) Yes, by re-signing with an updated key
- B) **No — but the server can refuse to *accept* a valid token**
- C) Yes, by calling a revoke API on the issuer
- D) Yes, if the token has a `jti`

**2.** What is the default, simplest revocation strategy?
- A) A Redis denylist
- B) **A short TTL and nothing else**
- C) Introspection on every request
- D) Token introspection with a cache

**3.** What is the state size of the token-version approach?
- A) One entry per issued token
- B) One entry per revoked token
- C) **One counter per user**
- D) One entry per session

**4.** How do you implement "log out everywhere" with a token version?
- A) Add every `jti` to a denylist
- B) **Increment the user's version counter**
- C) Rotate the signing key
- D) Delete the user's rows

**5.** What TTL should a denylist entry have?
- A) The token's original lifetime
- B) **The token's remaining lifetime (`exp - now`)**
- C) A fixed 24 hours
- D) No TTL — clean it up with a job

**6.** A token with 8 minutes left is revoked. What `SETEX` value?
- A) 900
- B) 60
- C) **480**
- D) 0

**7.** What does `SessionRegistry` do in a stateless JWT setup?
- A) Tracks the JWT sessions automatically
- B) **Nothing — it is populated by the `HttpSession` integration, which never runs**
- C) Replaces the token version
- D) Caches the JWKS

**8.** When should the denylist check happen relative to the signature verification?
- A) Before, so revoked tokens are cheap to reject
- B) **After — otherwise unauthenticated traffic causes a lookup per request**
- C) It does not matter
- D) In parallel

**9.** Why must the `jti` denylist TTL never be shorter than the token's remaining life?
- A) Redis requires it
- B) **Otherwise the token becomes usable again while still valid**
- C) It uses more memory
- D) The JWT spec requires it

**10.** What is the per-request cost of a denylist check?
- A) 0
- B) **One Redis `EXISTS`/`GET` — 0.2–5 ms**
- C) A database scan
- D) A full token re-verification

**11.** What is the cheapest way to reduce the denylist's per-request cost?
- A) Use a faster Redis
- B) **Mirror it in a local Caffeine cache with a short TTL**
- C) Increase the token TTL
- D) Use `EXISTS` instead of `GET`

**12.** A Redis outage on the payments API. What should it return?
- A) 401 Unauthorized
- B) **503 Service Unavailable** (fail closed, but distinguish it from an invalid token)
- C) 200 OK
- D) 429 Too Many Requests

**13.** How long must the key-rotation overlap window be?
- A) 1 minute
- B) **At least the maximum token lifetime plus the clock-skew leeway**
- C) Until the next deploy
- D) 24 hours

### Answer Key

| # | Answer | One-line reason |
|---|--------|-----------------|
| 1 | **B** | The signature is permanent; acceptance is your decision. |
| 2 | **B** | A 5–15 min TTL *is* the revocation mechanism — build nothing extra. |
| 3 | **C** | One counter per user — constant, independent of the token count. |
| 4 | **B** | `INCR` invalidates every token the user has ever received. |
| 5 | **B** | The entry must die exactly when the token dies — never earlier. |
| 6 | **C** | 8 minutes = 480 seconds. |
| 7 | **B** | It is populated by the `HttpSession` integration, which stateless mode skips. |
| 8 | **B** | Otherwise every unauthenticated request costs a Redis hop. |
| 9 | **B** | A short TTL resurrects a revoked token — the silent security regression. |
| 10 | **B** | One Redis lookup — 0.2 ms same-DC, up to 5 ms cross-AZ. |
| 11 | **B** | A 30 s mirror removes the latency and cuts Redis load by 30×. |
| 12 | **B** | 503 says "retry later"; 401 would trigger a mass re-login. |
| 13 | **B** | The window must outlast the longest-lived token signed with the old key. |

> 📌 **The three to re-read:** **Q5** (the TTL must equal the remaining lifetime),
> **Q9** (a short TTL silently resurrects revoked tokens) and **Q12** (the key-rotation
> overlap window).

---

## 21. Cheat Sheet / One-Page Summary

| Area | Fact |
|---|---|
| **Core fact** | A signed JWT cannot be un-signed — revocation is about **acceptance** |
| **Strategy 1** | **Short TTL only** — 0 cost, 0 state, blast radius = TTL. **The default.** |
| **Strategy 2** | `jti` denylist — 1 Redis GET/request, 1 entry per revoked token, blast radius ≈ 0 |
| **Strategy 3** | Token version (`ver`) — 1 Redis GET/request, **1 counter per user**, blast radius ≈ 0. **Best for bulk.** |
| **Strategy 4** | Refresh-token denylist only — 0 cost on the API path, blast radius = access TTL |
| **Strategy 5** | Denylist on logout only — strategy 2, populated only at logout |
| **Strategy 6** | Opaque + introspection — fully revocable, 1 lookup/request, stateful |
| **"Log out everywhere"** | Revoke all refresh rows **+** `INCR ver:<userId>` |
| **The version bump** | `INCR` — atomic, idempotent, **constant state**, kills unseen tokens |
| **Denylist key** | `denylist:jti:<jti>` (JWT) or `denylist:sha256:<hash>` (opaque) |
| **Denylist TTL** | **`exp - now`** — set atomically with the value (`SET … EX`) |
| **Order of checks** | Signature → claims → **then** revocation. Never the other way round. |
| **Error message** | **One generic `invalid_token`** for every rejection reason |
| **Local mirror** | Caffeine, 30 s TTL → latency ≈ 0, Redis load ÷ 30, delay ≤ 30 s |
| **Fail-open** | Accept the token. Availability wins. **Per-service, explicit, metriced.** |
| **Fail-closed** | Reject. Security wins. Return **503**, not 401, so clients retry instead of re-logging-in |
| **Redis** | 0.2–5 ms/request; **native TTL**; new dependency; the usual choice |
| **Postgres** | Durable, transactional; **needs a cleanup job** |
| **Denylist memory** | `≈ revocations_per_second × ttl_seconds` — the TTL is the whole story |
| **Key rotation** | Publish **alongside** → switch → wait **> max TTL + skew** → remove |
| **Overlap window** | ≥ max access-token TTL + 60 s. Removing early = **401 storm** |
| **Rotation order** | Key rotation = platform-wide; **version bump = one user** |
| **`SessionRegistry`** | Empty in stateless mode — the refresh-token **family** is your session |
| **Status codes** | 401 = invalid token · 403 = authenticated but unauthorised · **503 = revocation store down** |

---

## 22. Real-World Production Notes

| Knob / Behaviour | Default | When to change it |
|---|---|---|
| Access-token TTL | 10 min | 5 min for high-value APIs. **This is the primary revocation control** |
| Clock-skew leeway | 60 s | 10 s if you control both clocks; never above 60 s |
| Denylist TTL | `exp - now` | **Never** shorter. Never omit |
| Denylist key | `denylist:jti:<jti>` | Namespace per issuer if you accept tokens from several |
| Local mirror TTL | 30 s | 5 s for high-value paths; 60 s for high-volume reads |
| Fail policy | fail-closed on writes, fail-open on reads | **Explicit, per-service, per-endpoint** |
| Fail-closed response | 503 | Never 401 — clients must not re-login because of a store outage |
| Version counter storage | Redis (durable) + DB as source of truth | — |
| `jti` on every token | required | Without it, per-token revocation is impossible |
| Key-rotation overlap | ≥ max TTL + skew | Derive it from the configuration, not from a document |
| JWKS key count | 1 (2 during rotation) | Alert on any other value |
| Denylist hit rate | ≈ 0 | A spike is an **attack signal** — page |
| Revocation-store latency | < 5 ms p99 | Investigate the network placement, not the Redis |
| Redis `maxmemory-policy` | `noeviction` on the revocation keyspace | Eviction would silently resurrect revoked tokens |
| Logout requirement | Refresh token only | Add the `jti` denylist and/or the version bump for immediacy |

**Metrics worth having on a dashboard.**

| Metric | Why |
|---|---|
| `auth.401.by_reason` | Splits signature / claims / version / denylist — the single most useful auth metric |
| `revocation.version.mismatch` | A mass logout, a privilege change, or a bug |
| `denylist.hit_rate` | ≈ 0 normally; a spike is an attack |
| `denylist.size` | Must be bounded by `rate × TTL` |
| `denylist.keys_without_ttl` | **Must be 0** — the invariant that prevents unbounded growth |
| `revocation.check.duration` (p99) | Detects a cross-AZ Redis or pool exhaustion |
| `revocation.fail_open.used` | Makes the fallback **visible** |
| `revocation.store.unavailable` | Pages if fail-closed |
| `jwks.key.count` | 1 normally, 2 during rotation |
| `jwks.unknown_kid.rate` | Rotation in progress, or an attack |
| `auth.logout.count` by scope | user / all — tracks the feature's usage |

---

## 23. Follow-up Learning

**Next in this folder**

- → [04 · Security Risks, Complete Flows & Real Examples](./04-Security-Risks-Complete-Flows-and-Real-Examples.md) —
  the 11 attacks, the complete login/refresh/logout flows, the claim→authority mapping,
  the hardening checklist.

**Related folders**

- [05 · Spring Security](../05-Spring-Security/01-Architecture-and-Security-Filter-Chain.md) — `SessionCreationPolicy`,
  the `SecurityContext`, `SessionRegistry`, CSRF and CORS.
- [07 · OAuth2 & OpenID Connect](../07-OAuth2-OpenID-Connect/03-Client-Credentials-Refresh-Tokens-and-OpenID-Connect.md) —
  introspection (RFC 7662), token revocation (RFC 7009), DPOP (RFC 9449) and
  sender-constrained tokens.
- [10 · Caching](../10-Caching/README.md) — the Caffeine mirror pattern and eviction
  policies.

**Source pointers (RFCs and Spring Security 6.1)**

| Document | Why read it |
|---|---|
| RFC 7009 — Token Revocation | The normative endpoint for revoking a refresh or access token |
| RFC 7662 — Token Introspection | The introspection endpoint behind opaque tokens |
| RFC 9700 — OAuth 2.0 Security BCP | Revocation, replay detection and token-binding guidance |
| RFC 9449 — DPoP | Sender-constrained tokens — revocation-proof by design |
| RFC 8725 — JWT BCP | The "always validate the algorithm and the claims" guidance |
| RFC 7519 §4.1.7 | The `jti` claim and its replay-detection purpose |
| Spring Security `OAuth2TokenValidator` | Where a custom revocation check belongs |
| Spring Security `SessionRegistry` | Why it is empty in a stateless setup |
| Spring `CacheManager` / Caffeine | The local mirror pattern |

---

**Next Chapter:** → [04 · Security Risks, Complete Flows & Real Examples](./04-Security-Risks-Complete-Flows-and-Real-Examples.md)
**Previous Chapter:** ← [02 · Access & Refresh Tokens and Token Rotation](./02-Access-Refresh-Tokens-and-Token-Rotation.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
