# Access & Refresh Tokens and Token Rotation — The Complete Interview Guide

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★★ (every backend interview asks about refresh tokens; almost every candidate gets the *rotation with reuse detection* part wrong)
> **Prerequisites:** [01 · JWT Architecture, Statelessness & Signature Algorithms](./01-JWT-Architecture-Statelessness-and-Signature-Algorithms.md) · [README](./README.md)
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

- **Two tokens, two jobs.** The **access token** (5–15 min, stateless, carries the authorisation claims, sent as `Authorization: Bearer <jwt>`) is the *fast, disposable* credential. The **refresh token** (7–30 days, opaque or a JWT, stored **hashed** server-side, never used as an API credential) is the *slow, revocable, powerful* credential.
- **The refresh token must never be used to call an API.** If a refresh token is accepted by an API endpoint, you have built a 30-day bearer credential with the privileges of the user. It belongs at exactly one endpoint: `/refresh`.
- **Refresh-token rotation means every refresh issues a NEW refresh token and invalidates the old one.** The old token is marked `usedAt` and points at its replacement via `replacedById`.
- **Reuse detection is the point of rotation.** Presenting a token that has already been used means **two parties hold it** — one is the thief. The server revokes the **entire token family** (`familyId`) and forces a re-login. Without this, rotation adds nothing against a stolen token.
- **The refresh token is stored as a hash.** A 256-bit `SecureRandom` token hashed with **SHA-256** is correct — there is no dictionary to attack, so a slow KDF buys nothing. (The opposite is true for a password: low entropy ⇒ you need bcrypt/Argon2.) Store the **hash**, not the token.
- **Transport is a security decision, not a convenience one.** Browser SPA → `HttpOnly; Secure; SameSite=Strict` cookie (XSS cannot read it). Native mobile → request body + the platform keystore. **`localStorage` is the worst option** — any XSS on the page can read it.
- **A cookie-based refresh endpoint needs CSRF protection**, because the browser auto-attaches the cookie. `SameSite=Strict` plus a custom header, or a CSRF token — see topic 05.
- **Three expiry models:** *absolute* (hard stop, safest), *sliding* (extends on use, most convenient, longest exposure), *idle* (expires after N minutes of inactivity). Production usually combines **absolute + idle**.
- **The client must refresh once and retry once.** A naive client that retries in a loop turns one expired token into an infinite 401 storm against your auth server.
- **Device/session management is a first-class feature**, not a nice-to-have: "log out this device", "log out everywhere", "list my devices". It falls out of the refresh-token table almost for free.

### The canonical lifetimes (reproduced verbatim from the repository standard)

| Token | TTL | Storage | Sent as |
|---|---|---|---|
| Access token | **5–15 min** | Server: none (stateless) | `Authorization: Bearer <jwt>` |
| Refresh token | **7–30 days** | Server: **hashed** in DB/Redis | `HttpOnly; Secure; SameSite=Strict` cookie |
| ID token (OIDC) | 5–60 min | none | never sent to the browser JS |

### The canonical HTTP statuses (reproduced verbatim from the repository standard)

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

## 1. What & Why — The Problem This Solves

**The problem.** You want the *convenience* of a long-lived login ("stay signed in for a
month") with the *security* of a short-lived credential ("if my token leaks, the damage
is capped at 15 minutes"). A single token cannot give you both.

```text
   THE IMPOSSIBLE SINGLE-TOKEN SETTINGS
   ─────────────────────────────────────────────────────────────────────
   TTL = 5 minutes   →  the user is logged out every 5 minutes. Unusable.
   TTL = 30 days     →  a leaked token is valid for 30 days. And you CANNOT
                       revoke it, because a signed JWT cannot be un-signed.
   TTL = 30 days AND revocable →  you are back to server-side state for
                       every request… which is what you were trying to avoid.
```

**The solution — split the credential by *frequency* and by *lifetime*.**

```text
                    ┌───────────────────────────────────────────────┐
                    │               ACCESS TOKEN                    │
                    │  lifetime: 5–15 minutes                       │
                    │  storage  : NOWHERE (stateless)               │
                    │  sent as  : Authorization: Bearer <jwt>       │
                    │  used at  : EVERY API endpoint                │
                    │  power    : "this user may do X"              │
                    │  revocable: NO — expires on its own          │
                    └───────────────────────────────────────────────┘
                                        ▲
                                        │ "your access token expired;
                                        │  here is a new one"
                    ┌───────────────────────────────────────────────┐
                    │              REFRESH TOKEN                    │
                    │  lifetime: 7–30 days                         │
                    │  storage  : HASHED, server-side               │
                    │  sent as  : HttpOnly cookie (web)             │
                    │             / request body (native mobile)    │
                    │  used at  : EXACTLY ONE endpoint (/refresh)  │
                    │  power    : "mint a new access token"         │
                    │  revocable: YES — delete the row              │
                    └───────────────────────────────────────────────┘
```

**Why this works.** The two tokens have **opposite** properties:

| | Access token | Refresh token |
|---|---|---|
| Used on the hot path | ✅ every request | ❌ never |
| Needs server state to verify | ❌ no | ✅ yes |
| Lifetime | short (5–15 min) | long (7–30 days) |
| Revocable | ❌ no | ✅ yes |
| Blast radius if stolen | **15 minutes, one service's worth of authority** | **30 days, full account** |
| Sent automatically by the browser | ❌ no (header) | ✅ yes (cookie) |

The access token is designed to be **cheap and throwaway**. The refresh token is designed
to be **rare and precious**. By never letting the long-lived token touch the hot path,
you get a system whose per-request cost is a signature verification, and whose revocable
power lives in exactly one table.

**The analogy — the hotel key card.** The access token is the **room card**: it opens one
door for a few hours, it is stamped with the date, and if you lose it, the problem is
bounded. The refresh token is the **front-desk key**: it can be re-issued into new room
cards, it is kept at the desk (behind a counter, logged), and it is *the thing the hotel
can confiscate*. Nobody keeps the front-desk key in their pocket and uses it to open the
room door 400 times a day — that is exactly the mistake of using a refresh token as an
API credential.

**What breaks with the two-token model?** Two things, both of which you must design for:
(1) **the client must handle a 401 by refreshing and retrying** — and must do it *once*,
not in a loop; (2) **the refresh endpoint becomes a password-equivalent endpoint** and
must be rate-limited, audited and protected against brute force.

---

## 2. Mental Model — The One Picture

```text
   THE TWO-TOKEN LIFECYCLE — ONE PICTURE
   ════════════════════════════════════════

   LOGIN                API CALL              EXPIRED              REFRESH
   ─────                ────────              ───────              ───────
   username+password    Authorization:        401 invalid_token    POST /refresh
        │               Bearer eyJ…                │              (cookie/body)
        ▼                     │                     │                   │
   ┌─────────┐                │                     │                   ▼
   │ /login  │                ▼                     │            ┌──────────┐
   │ 200 OK  │         ┌────────────┐               │            │ validate │
   │         │         │ API 200 OK │               │            │  + rotate │
   │ access  │         └────────────┘               │            └────┬─────┘
   │ refresh │                                    │                 │
   │ (cookie)│                                    │      ┌──────────┴──────────┐
   └─────────┘                                    │      ▼                     ▼
        │                                         │  new access token    new refresh token
        │                                         │  (sent in body)      (set as cookie)
        │                                         │      │                     │
        │                                         │      └────► client stores │
        │                                         │            access token   │
        │                                         │                         │
        └─────────────────────────────────────────┴─────────────────────────┘

   ★ THE INVARIANTS THAT MATTER
   ──────────────────────────────
   ① The access token is NEVER an API credential for /refresh-style power.
   ② The refresh token is NEVER accepted by a business endpoint.
   ③ Every refresh INVALIDATES the presented refresh token.
   ④ Presenting an already-used refresh token revokes the WHOLE FAMILY.
   ⑤ Only a HASH of the refresh token is ever stored.
```

**What to look at:** the loop on the right is the *only* place a refresh token is ever
presented. Everything else in the system flows from those five invariants. If you cannot
state them, you do not yet understand refresh tokens — and the interview will find out.

---

## 3. Deep Dive — Theory (Beginner → Advanced)

### 3.1 Beginner Level

**The two-token model, defined.**

| | **Access token** | **Refresh token** |
|---|---|---|
| **Purpose** | Carries *authorisation* — "this caller may do X" | Carries *renewal* — "this caller may get a new access token" |
| **Format** | A **JWT** (signed) | **Opaque random string** (recommended) or a JWT (signed) |
| **Lifetime** | **5–15 min** | **7–30 days** |
| **Server state** | **None** | **Hashed row per token** |
| **Transport** | `Authorization: Bearer <jwt>` | `HttpOnly; Secure; SameSite=Strict` cookie (web) · request body (native) |
| **Scope of use** | Every API endpoint | Exactly one endpoint: `/refresh` |
| **Revocable** | ❌ No (only by expiry) | ✅ Yes — delete the row |
| **Rotated** | ❌ No | ✅ Yes — every use issues a replacement |
| **Leak impact** | Bounded: one service, ≤15 min | Severe: full account, up to 30 days |

**Why the access token must be a JWT and the refresh token usually is not.**

The access token is on the hot path — every one of your 25 services must verify it in
under a millisecond, ideally with **no network call**. A JWT does that. The refresh token
is presented **once every ~10 minutes per user** — a tiny fraction of the traffic — so
there is no performance reason to make it self-contained. And there is a strong
*security* reason **not** to: an **opaque** token is a random string with no claims in it,
so even if it leaks, the attacker learns nothing from its contents. A JWT refresh token
would expose the user's identity, tenant and roles to anyone who steals it.

> 💡 **The interview line.** "The access token is a JWT because it has to be verified
> cheaply on every request by many services. The refresh token is an **opaque random
> string** because it is used rarely, so it can afford a database lookup — and because
> opacity means a leak tells the attacker nothing."

**The third token: the ID token.**

An **ID token** is an OIDC concept (topic 07). It is issued by the identity provider to
the **client**, and it asserts *who the user is* — it is **not** an API credential and it
must never be sent to your API. Mixing ID tokens and access tokens up is the **confused
deputy** bug in its most common form.


### 3.2 Intermediate Level

**The complete refresh flow.**

1. The client calls `POST /api/auth/login` with credentials.
2. The server verifies the credentials (and MFA), then **issues a pair**: a short-lived
   access token and a long-lived refresh token.
3. The access token is returned in the JSON body. The refresh token is set as an
   `HttpOnly` cookie (web) — **never** in the JSON body, because the body is readable by
   JavaScript.
4. The client calls the API with `Authorization: Bearer <access token>`.
5. When the access token expires, the API returns **401** with
   `WWW-Authenticate: Bearer error="invalid_token", error_description="..."`.
6. The client calls `POST /api/auth/refresh`, sending the refresh token (cookie
   automatically, or body for native apps).
7. The server looks up the token's hash, checks it is **active**, **not expired** and
   **not already used**, **rotates** it (marks it used, creates a replacement in the same
   family), and returns a **new** access token + sets a **new** refresh cookie.
8. The client retries the original API call **once** with the new access token.

**Refresh-token rotation — the model.**

```text
   WITHOUT ROTATION (dangerous)
   ────────────────────────────
   refresh token "R1" (valid 30 days)
        │  used at T=0        → new access token
        │  used at T=10d       → new access token      ← R1 is STILL valid
        │  used at T=20d       → new access token      ← a thief can do this too
   ⇒ one leaked token works for the full 30 days, with no signal.

   WITH ROTATION + REUSE DETECTION (correct)
   ──────────────────────────────────────────────
   family F1:  R1 ──▶ R2 ──▶ R3 ──▶ R4
              used   used   used   current
              │      │      │
              └──────┴──────┴──▶ each link is marked usedAt + replacedById

   Legit client: presents R1 → server rotates to R2 → client presents R2 → rotates to R3.
   Thief:        presents R1 again  → ALREADY USED ⇒ REUSE DETECTED
                                        ⇒ revoke the WHOLE family F1
                                        ⇒ every token in F1 dies immediately
                                        ⇒ force re-login
```

**The three expiry models.**

| Model | Definition | Pro | Con |
|---|---|---|---|
| **Absolute** | Hard expiry from issue; never extended | ✅ Bounded exposure; simplest to reason about | ❌ The user is logged out at a fixed moment regardless of activity |
| **Sliding** | Expiry extends on every use | ✅ Active users never log out | ❌ An attacker with a stolen token keeps it alive **forever** by using it — the worst property |
| **Idle** | Expires after N of inactivity | ✅ Reasonable UX; a dormant session dies | ❌ A thief who uses the token keeps it alive too |

> ⚠️ **Pure sliding expiry is dangerous.** If the server extends the expiry whenever a
> refresh token is used, a stolen refresh token that the thief uses once a week stays valid
> indefinitely — the attacker's usage **keeps the victim's session alive**, so the user
> never notices anything is wrong. **Production answer: absolute + idle.** The absolute
> expiry is the hard ceiling (e.g. 30 days), and the idle expiry (e.g. 7 days) is
> refreshed on each use.

**The storage model — why a hash, and why SHA-256 is fine here.**

```text
   WHAT WE STORE                              WHAT WE NEVER STORE
   ─────────────                              ───────────────────
   token_hash = SHA-256(rawToken)            ✗ the raw token
   id, user_id, family_id,                    ✗ anything that lets you
   issued_at, expires_at, used_at,               reconstruct the token
   revoked_at, replaced_by_id,
   device_info, ip

   WHY A HASH AND NOT A SLOW KDF?
   ─────────────────────────────
   A password is LOW entropy → an attacker can try billions of candidates
   → you need bcrypt/Argon22id to make each guess expensive.

   A refresh token is 256 bits of SecureRandom → 2^256 candidates
   → guessing is already infeasible
   → SHA-256 (or even SHA-512) is enough, and it is FAST.
   ─────────────────────────────────────────────────────────────
   The rule: KDF strength must match entropy. Fast hash for random secrets;
   slow KDF for human-chosen secrets.
```

**The transport decision — three options and their exposure.**

| Option | XSS exposure | CSRF exposure | Works with cookies? | Use for |
|---|---|---|---|---|
| **`HttpOnly; Secure; SameSite=Strict` cookie** | ✅ **None** — JS cannot read it | ⚠️ **Yes** — the browser auto-attaches it → needs CSRF protection | ✅ | Browser SPAs and web apps |
| **Request body / header** | ❌ **Full** — JS can read it | ✅ **None** — a cross-site form cannot set a custom header or a JSON content-type | ❌ | Native mobile apps (iOS/Android), CLI clients |
| **`localStorage` / `sessionStorage`** | ❌ **Full** — any XSS reads it | ✅ None | ❌ | **Avoid.** Occasionally unavoidable (some legacy clients) — then add a short TTL and rotate aggressively |

> 🎯 **The trap people fall into.** "Putting the refresh token in `localStorage` is fine
> because the access token is short-lived." No — the refresh token is the *long-lived*
> credential. An XSS that reads `localStorage` gets a **30-day** credential, not a
> 15-minute one. The whole point of the split is defeated.

**Why a cookie-based refresh endpoint needs CSRF protection.**

The browser automatically attaches the refresh cookie to any request to your origin — a
malicious page cannot read the cookie, but it **can** cause the browser to **send** it.
That is exactly the CSRF shape: the browser performs an authenticated action the user did
not intend. Mitigations:

1. **`SameSite=Strict`** on the cookie — the browser will not attach it to a cross-site
   request. (Modern browsers default to `Lax`; `Strict` is stricter but can break
   legitimate top-level navigations.)
2. **A custom header** — the refresh request sends `X-Requested-With: XMLHttpRequest` (or
   a CSRF token). A cross-origin form **cannot** set a custom header, so the request is
   rejected by CORS before it reaches your handler. This is the cheapest and most robust
   defence for a JSON API.
3. **A real CSRF token** — Spring Security's `CookieCsrfTokenRepository` /
   `HttpSessionCsrfTokenRepository` with `CsrfFilter`. See topic 05.

> 📌 Note the interaction: you disable CSRF for the **bearer-token API** (there is no
> cookie to ride on), but you must **re-enable** it — or use the custom-header rule — for
> the **cookie-based refresh endpoint**. A blanket `.csrf(csrf -> csrf.disable())` across
> the whole application is a real vulnerability.

### 3.3 Advanced Level

**The refresh-token record — the anatomy.**

| Column | Type | Purpose |
|---|---|---|
| `id` | UUID / `BIGINT` | Primary key |
| `user_id` | `BIGINT` | Whose session this is — for "revoke all my sessions" |
| `token_hash` | `CHAR(64)` / `BINARY(32)` | `SHA-256(rawToken)` — the **lookup key**. Must be **unique** and **indexed** |
| `family_id` | `UUID` | The **rotation chain**. All descendants share it. This is the theft-detection unit |
| `issued_at` | `TIMESTAMP` | When it was minted |
| `expires_at` | `TIMESTAMP` | Absolute expiry for this token |
| `used_at` | `TIMESTAMP` | When it was **rotated**. `NULL` = active. Non-NULL = consumed ⇒ reuse ⇒ theft signal |
| `revoked_at` | `TIMESTAMP` | Explicitly revoked (logout, admin action, family revoked) |
| `replaced_by_id` | FK → `id` | The token that superseded this one — forms the chain |
| `device_info` | `VARCHAR(255)` | "Chrome 133 / macOS" — for the device list UI |
| `ip` | `VARCHAR(45)` | The issuing IP — for the device list UI and for anomaly detection |

**Rotation with reuse detection — the algorithm.**

```text
   onRefresh(rawToken):
   ① hash = SHA-256(rawToken)
   ② row = repository.findByTokenHash(hash)
   ③ if row == null            → 401  (unknown token — brute force or garbage)
   ④ if row.revokedAt != null  → 401  (already revoked)
   ⑤ if row.expiresAt < now    → 401  (expired)
   ⑥ if row.usedAt != null     → ⚠️ REUSE DETECTED
        └─► revokeEntireFamily(row.familyId)
        └─► revokeAllOtherSessions(row.userId)   (optional, stricter)
        └─► audit log: SECURITY_EVENT refresh_token_reuse
        └─► 401
   ⑦ mark row.usedAt = now
   ⑧ create the replacement token in the SAME family, with the same absolute expiry
   ⑨ issue a new access token
   ⑩ return { accessToken, refreshToken } + set the refresh cookie
```

**Why step ⑥ must revoke the whole family.** A single stolen token used twice proves
that *two* parties hold it. You cannot tell which one is the thief. But you **can** kill
the chain: the legitimate client holds the newest token in the family; revoking the whole
family forces **both** parties to re-authenticate. The legitimate user re-logs in
(mild inconvenience); the thief is evicted (the point). This is the standard "refresh
token rotation with reuse detection" pattern from the OAuth 2.0 Security BCP (RFC 9700).

**Device and session management.**

Because every refresh token is a row with `device_info` and `ip`, the "list my devices"
screen is a single query:

```sql
SELECT id, device_info, ip, issued_at, last_used_at
FROM refresh_token
WHERE user_id = ? AND revoked_at IS NULL AND used_at IS NULL
ORDER BY last_used_at DESC;
```

- **"Log out this device"** → revoke one row.
- **"Log out everywhere"** → revoke all rows for the user **and** bump the user's token
  version (which kills all outstanding *access* tokens too — see chapter 03).
- **"Log out everywhere except here"** → revoke all rows except the current one, and bump
  the version *then* re-issue a fresh access token for the current device.

**Refresh-token reuse detection in the presence of concurrency.** Two legitimate tabs
refreshing at the same instant will present the **same** refresh token twice — and the
second one will be flagged as reuse and revoke the family. This is a **real** production
bug. The mitigations:

1. A **short grace window** on `usedAt` (e.g. 10 seconds): a token used within the
   window returns the *same* replacement instead of triggering reuse detection.
2. A **client-side refresh lock** — the client serialises refreshes (a single in-flight
   promise that all callers await).
3. Store the **replacement token's raw value** (or its id) so the grace-window path can
   return the identical successor.

Option 2 is the right first move — it fixes the client, and the server-side grace window
is the belt-and-braces for genuinely parallel clients.

**The absolute-vs-relative lifetime distinction.**

- **Absolute lifetime** — the family expires at `issuedAt + 30 days`, no matter how often
  it is refreshed. This bounds the total exposure of a stolen refresh token: **30 days,
  max, regardless of activity.**
- **Relative (sliding) lifetime** — each token gets its own 7-day expiry from the moment
  it is issued. Convenient, but a stolen token can be kept alive indefinitely by use.

Production answer: **both**. `expires_at` on each row = `min(now + 7 days,
family_absolute_expiry)`. That gives the user a 7-day rolling window *and* a hard 30-day
ceiling.

### 3.4 Expert Level

**Why not make the refresh token a JWT?**

It is done, and it works, but it trades away a real property. A JWT refresh token is
**self-contained** — which means the server can verify it without a lookup — but then:

1. **It cannot be revoked individually** unless you keep a denylist, which is the state
   you were trying to avoid. (You can revoke the *family* with a family-version claim,
   but then you have a per-family lookup on the refresh path anyway.)
2. **It leaks its contents.** A stolen refresh token tells the attacker the user's id,
   tenant, roles and family id. An opaque token tells them nothing.
3. **It is longer.** A JWT refresh token with 7 claims is ~600 bytes; an opaque
   256-bit token is 43 characters.

The rare case where a JWT refresh token is the right answer is a **stateless refresh
service** — one where the refresh endpoint must scale to millions of refreshes per second
with no database. Then a signed refresh token with a `family_id` and a `token_version`
claim, plus a small Redis denylist for revoked families, is a legitimate design.

**Token rotation and the "refresh token reuse interval" (RFC 9700).** RFC 9700
(Best Current Practice for OAuth 2.0 Security) recommends that clients be able to
distinguish a **replay** from a **legitimate race**, and that servers implement
**refresh token rotation with reuse detection**. It also warns against **refresh token
families growing unboundedly** — each rotation creates a new row, so a 30-day session
that refreshes every 10 minutes creates **4,320 rows**. The mitigations: keep the
**absolute** expiry, prune old rows with `usedAt < now - 7 days`, and/or store only the
**current** token per family plus a counter, rather than every link in the chain.

**The audit trail.** Every refresh operation should record: the user, the family id, the
device, the IP, the outcome (rotated / reuse-detected / expired / unknown), and the
timestamp. The **reuse-detection** events in particular are the single most valuable
security signal in the system — a spike means either a bug (parallel refreshes) or a
compromise. You must be able to tell those two apart, which is why the event carries the
device and IP.

---

## 4. Internal Working Mechanism

**The Spring Boot call chain for `POST /api/auth/refresh`.**

```
POST /api/auth/refresh
Cookie: rt=<raw refresh token>          (or body: {"refreshToken": "..."})

FilterChainProxy
  └─ CorsFilter                          ← MUST run before auth (the refresh call is cross-origin)
  └─ CsrfFilter                          ← ENABLED for this endpoint if the token is in a cookie
  └─ (no BearerTokenAuthenticationFilter hit — there is no Authorization header)
  └─ AuthorizationFilter                  ← .requestMatchers("/api/auth/**").permitAll()
  └─ DispatcherServlet
       └─ HandlerMapping → HandlerAdapter → AuthController.refresh()
            └─ AuthService.refresh(rawToken, request)
                 ├─ ① String hash = TokenHasher.sha256Hex(rawToken)
                 ├─ ② RefreshToken row = refreshTokenRepository.findByTokenHash(hash)
                 │       └─ (Spring Data JPA → SELECT ... WHERE token_hash = ? LIMIT 1)
                 │       └─ a UNIQUE index on token_hash makes this O(log n)
                 ├─ ③ if (row == null)            throw InvalidRefreshTokenException
                 ├─ ④ if (row.getRevokedAt() != null) throw InvalidRefreshTokenException
                 ├─ ⑤ if (row.getExpiresAt().isBefore(now)) throw InvalidRefreshTokenException
                 ├─ ⑥ if (row.getUsedAt() != null) {
                 │       ├─ refreshTokenRepository.revokeFamily(row.getFamilyId(), now);
                 │       ├─ auditService.record(RefreshTokenReuseEvent.of(row, request));
                 │       └─ throw InvalidRefreshTokenException;
                 │   }
                 ├─ ⑦ Transactional:
                 │       ├─ row.setUsedAt(now);
                 │       ├─ row.setReplacedById(null);        // set after the new row exists
                 │       ├─ RefreshToken next = newToken(same family, same absolute expiry);
                 │       ├─ refreshTokenRepository.save(next);
                 │       └─ row.setReplacedById(next.getId());
                 │       └─ refreshTokenRepository.save(row);
                 ├─ ⑧ String accessToken = tokenService.issueAccessToken(user, device);
                 ├─ ⑨ build the response with Set-Cookie
                 └─ ⑩ return { accessToken, expiresIn }   (the refresh token is NOT in the body)
```

**The classes you would actually write.**

| Class | Responsibility | Key method |
|---|---|---|
| `TokenHasher` | Hash a raw refresh token | `sha256Hex(String raw)` |
| `RefreshTokenIssuer` | Mint a new raw token + its hash | `IssuedRefreshToken issue(UUID familyId, Instant absoluteExpiry)` |
| `RefreshTokenRepository` | Persistence + the family-revocation query | `findByTokenHash`, `revokeFamily` |
| `RefreshTokenService` | The state machine: issue → rotate → detect reuse | `issue`, `rotate`, `revokeFamily`, `revokeAllForUser` |
| `AuthController` | HTTP surface | `login`, `refresh`, `logout`, `sessions` |
| `CookieFactory` | Build the `Set-Cookie` header | `refreshCookie(String raw, Duration ttl)` |
| `AuditService` | Record security events | `record(RefreshTokenReuseEvent)` |

**Where the transaction boundary goes.** Steps ⑦ and ⑧ must be **one transaction**: if
the row is marked used but the successor is not saved (or the access token mint fails
after), the user is locked out with a consumed token and no successor. Annotate the
**service** method `@Transactional` — not the controller. And note that the *audit* write
in step ⑥ should be in its **own** transaction (`REQUIRES_NEW`) so it survives a rollback
of the main flow.

**The `SecurityContext` on the refresh path.** The refresh endpoint is **unauthenticated**
— it is authorised by *possession of the refresh token*, not by a bearer credential. So
`.requestMatchers("/api/auth/refresh").permitAll()` is correct, and the endpoint must do
its own, strict validation.

**The cookie attributes, precisely.**

```java
ResponseCookie cookie = ResponseCookie.from("rt", rawToken)
        .httpOnly(true)                    // JS cannot read it → survives XSS
        .secure(true)                     // HTTPS only → survives network sniffing
        .sameSite("Strict")               // not sent cross-site → survives CSRF
        .path("/api/auth")                // scope: only the auth endpoints receive it
        .maxAge(Duration.ofDays(30))      // matches the absolute expiry
        .build();
```

> ⚠️ **`path("/api/auth")` is a genuine hardening step** — the refresh cookie is then
> never attached to business API calls, so an XSS on a business page cannot cause the
> cookie to be replayed to a different endpoint. Verify your CORS setup still works with
> a path-scoped cookie.

**Why `Secure` is not optional.** Without `Secure`, the cookie is sent over plain HTTP,
and a single `http://` link or a misconfigured proxy leaks a 30-day credential.

---

## 5. Step-by-Step Execution Flow

**The full client lifecycle — login, use, refresh, logout.**

1. **`POST /api/auth/login`** with `{ "username": "...", "password": "..." }`.
   - The server verifies the password with a **slow KDF** (bcrypt/Argon2 — see topic 05),
     checks MFA if enabled, and checks the account is not locked.
   - It loads the user, computes the roles/authorities, and **issues a pair**.
   - **Response 200** with `{"accessToken": "<jwt>", "expiresIn": 600, "tokenType": "Bearer"}`
     and a `Set-Cookie: rt=<raw>; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=2592000`.
   - The refresh token is **not** in the body.
   - A **refresh-token row** is inserted: `family_id = <new UUID>`, `token_hash =
     SHA-256(raw)`, `expires_at = now + 30 days`, `used_at = NULL`, `revoked_at = NULL`.
   - An **audit event** `LOGIN_SUCCESS` is recorded.

2. **`GET /api/orders/42`** with `Authorization: Bearer <jwt>`.
   - The resource server validates locally. **No call to the auth server.**
   - **200 OK**.

3. **The access token expires** (or is revoked). The next API call returns
   **401** with `WWW-Authenticate: Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: Jwt expired at ..."`.

4. **The client calls `POST /api/auth/refresh`** (cookie attached automatically, or the
   token in the body for native apps).
   - The server hashes the token, looks it up, and runs the state machine.
   - On success it returns **200** with a **new** access token and sets a **new**
   refresh cookie; the old token's row is marked `used_at`.

5. **The client retries the original request once** with the new access token → **200**.

6. **`POST /api/auth/logout`**.
   - The server revokes the presented refresh token (`revoked_at = now`) and **deletes
     the cookie** (`Max-Age=0`).
   - The access token, if still live, keeps working until it expires — that is the
     stateless trade-off (chapter 03 covers the fix).
   - **204 No Content**.

7. **"Log out everywhere"** → revoke every family for the user **and** increment the
   user's token version, which invalidates every outstanding access token.

---

## 6. Visualizations

### 6.1 The full login → API → 401 → refresh → rotate → retry flow (Mermaid sequence)

```mermaid
sequenceDiagram
    autonumber
    participant UI as Browser / App
    participant API as Resource API
    participant AUTH as Auth Service
    participant DB as Refresh Token Store

    UI->>AUTH: POST /api/auth/login {username, password}
    AUTH->>AUTH: verify password (bcrypt/Argon2), load authorities
    AUTH->>DB: INSERT refresh_token (family=F1, token_hash=SHA256(raw), used_at=NULL)
    AUTH-->>UI: 200 OK {accessToken: "<jwt>", expiresIn: 600}
    AUTH-->>UI: Set-Cookie: rt=<raw>; HttpOnly; Secure; SameSite=Strict; Path=/api/auth

    UI->>API: GET /api/orders/42<br/>Authorization: Bearer <jwt>
    API->>API: verify signature + exp/iss/aud locally
    API-->>UI: 200 OK

    Note over UI,API: 10 minutes later — the access token expires

    UI->>API: GET /api/orders/42<br/>Authorization: Bearer <expired jwt>
    API-->>UI: 401 Unauthorized<br/>WWW-Authenticate: Bearer error="invalid_token"

    UI->>AUTH: POST /api/auth/refresh<br/>Cookie: rt=<raw>  (auto-attached)
    AUTH->>DB: SELECT ... WHERE token_hash = SHA256(raw)
    DB-->>AUTH: row(family=F1, used_at=NULL, revoked_at=NULL)
    AUTH->>DB: UPDATE row SET used_at=now<br/>INSERT new row (family=F1, used_at=NULL)
    AUTH-->>UI: 200 OK {accessToken: "<new jwt>", expiresIn: 600}
    AUTH-->>UI: Set-Cookie: rt=<new raw>; HttpOnly; Secure; SameSite=Strict

    UI->>API: GET /api/orders/42 (RETRY ONCE)<br/>Authorization: Bearer <new jwt>
    API-->>UI: 200 OK
```

**What to look at:** the client **retries exactly once**. If the retry also returns 401,
the client must **not** loop — it must clear the session and send the user to the login
page. An unbounded retry loop turns one expired token into a self-inflicted DDoS against
your own auth service.

### 6.2 Refresh-token reuse detection — the stolen token (Mermaid sequence)

```mermaid
sequenceDiagram
    autonumber
    participant L as Legit Client
    participant T as Thief (stolen token)
    participant AUTH as Auth Service
    participant DB as Refresh Token Store
    participant S as All other devices

    Note over L,T: The thief stole R1 (e.g. from a compromised laptop)

    L->>AUTH: POST /refresh  (token = R1)
    AUTH->>DB: SELECT WHERE token_hash=SHA256(R1)
    DB-->>AUTH: row(R1, family=F1, used_at=NULL)
    AUTH->>DB: UPDATE R1 SET used_at=now
    AUTH->>DB: INSERT R2 (family=F1, used_at=NULL)
    AUTH-->>L: 200 OK {accessToken, refreshToken=R2}

    Note over T: The thief still holds R1 (it is not "used up" from his point of view)

    T->>AUTH: POST /refresh  (token = R1)   ← REUSE!
    AUTH->>DB: SELECT WHERE token_hash=SHA256(R1)
    DB-->>AUTH: row(R1, family=F1, used_at=2026-01-14T10:02Z)  ← ALREADY USED
    Note over AUTH: ⚠️ REUSE DETECTED<br/>Two parties hold this token ⇒ one is a thief
    AUTH->>DB: UPDATE refresh_token SET revoked_at=now WHERE family_id=F1
    Note over DB: R1, R2, R3 … ALL revoked
    AUTH->>S: revoke every other session of this user (optional)
    AUTH->>AUTH: audit: SECURITY_EVENT refresh_token_reuse (user, device, ip)
    AUTH-->>T: 401 Unauthorized error="invalid_grant"

    Note over L: The legit client's next refresh (R2) also fails…
    L->>AUTH: POST /refresh (token = R2)
    AUTH-->>L: 401 Unauthorized — family F1 is revoked
    Note over L: The user is forced to log in again.<br/>The thief is evicted. The incident is logged.
```

**What to look at:** the legitimate client **also** gets locked out — and that is the
*intended* behaviour. You cannot identify the thief, so you evict both. The cost is one
re-login; the benefit is that a stolen token is worth **one** use at most.

### 6.3 The refresh-token record (ASCII)

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│                       refresh_token  (Postgres)                              │
├──────────────────────────────────────────────────────────────────────────────┤
│  id                BIGSERIAL PK                                             │
│                    └─ the surrogate key; referenced by replaced_by_id         │
│                                                                              │
│  user_id           BIGINT NOT NULL                                          │
│                    └─ whose session; drives "log out everywhere"            │
│                                                                              │
│  token_hash        CHAR(64) NOT NULL UNIQUE INDEX  ◄── THE LOOKUP KEY        │
│                    └─ SHA-256(rawToken) hex.  The raw token is NEVER stored. │
│                       UNIQUE + INDEX → the SELECT is O(log n), not a scan.    │
│                                                                              │
│  family_id         UUID NOT NULL INDEX      ◄── THE THEFT-DETECTION UNIT     │
│                    └─ every token in a rotation chain shares it.             │
│                       Revoking a family kills the whole chain.              │
│                                                                              │
│  issued_at         TIMESTAMPTZ NOT NULL                                      │
│                    └─ when it was minted                                     │
│                                                                              │
│  expires_at        TIMESTAMPTZ NOT NULL  ◄── absolute + idle                 │
│                    └─ min(now + idleTTL, family_absolute_expiry)             │
│                                                                              │
│  used_at           TIMESTAMPTZ NULL      ◄── NULL = ACTIVE, set = ROTATED    │
│                    └─ set the moment it is exchanged. A non-NULL used_at    │
│                       means "this token was already spent" ⇒ reuse signal.   │
│                                                                              │
│  revoked_at        TIMESTAMPTZ NULL      ◄── logout / admin / family revoke │
│                                                                              │
│  replaced_by_id    BIGINT NULL FK → id                                     │
│                    └─ the successor token. Forms the chain for forensics.   │
│                                                                              │
│  device_info       VARCHAR(255)                                             │
│                    └─ "Chrome 133 / macOS 15" — powers the device list UI   │
│                                                                              │
│  ip                VARCHAR(45)                                              │
│                    └─ the issuing IP — anomaly detection + the device list   │
│                                                                              │
│  last_used_at      TIMESTAMPTZ NULL                                         │
│                    └─ when it was last exchanged — "active sessions" sorting│
└──────────────────────────────────────────────────────────────────────────────┘

   ★ THE THREE COLUMNS THAT CARRY THE SECURITY
   ─────────────────────────────────────────────
   token_hash   → you never store a usable credential
   family_id    → you can kill an entire stolen chain in one UPDATE
   used_at      → you can DETECT that a token was presented twice
```

**What to look at:** the three bolded columns. `token_hash` protects the database,
`family_id` makes theft *containable*, and `used_at` makes theft *detectable*. Everything
else is bookkeeping or UX.

### 6.4 The refresh-token state machine (Mermaid flowchart)

```mermaid
stateDiagram-v2
    [*] --> ACTIVE : issue(raw, family=F, hash=SHA256(raw))

    ACTIVE --> ROTATED : POST /refresh with this token<br/>(used_at = now, successor created in F)
    ACTIVE --> EXPIRED : expires_at < now
    ACTIVE --> REVOKED : logout / admin revoke / "log out everywhere"

    ROTATED --> REVOKED : REUSE DETECTED<br/>(presented after used_at)<br/>→ revoke the WHOLE family F
    ROTATED --> EXPIRED : expires_at < now

    EXPIRED --> [*]
    REVOKED --> [*]

    note right of ROTATED
        Presenting a ROTATED token means
        two parties hold it ⇒ theft.
        Revoke every token in family F.
    end note
```

**What to look at:** the only edge that *adds* safety is `ROTATED → REVOKED`. Every other
edge just expires something. Rotation **without** reuse detection would leave the
`ROTATED` state as a dead end, and the thief's copy of the token would be harmless only
because it was already used — which is exactly the case the thief will exploit.

---

## 7. Production-Level Use Cases & Real Industry Examples

**1. E-commerce — a browser SPA with silent refresh.**
A fashion e-commerce site serves 2M monthly actives from a React SPA. The access token
lives in **memory only** (a JS variable in a Zustand store — not `localStorage`); the
refresh token is an `HttpOnly` cookie scoped to `Path=/api/auth`. The client uses a
**single-flight refresh lock**: if two components 401 at the same time, one refresh
request goes out and both await it. The refresh endpoint is rate-limited to **20
requests/minute per IP** and **10/minute per session**. Scale: ~8,000 refreshes/minute at
peak; the refresh-token table holds ~3.1M rows (one per active device), pruned nightly.

**2. Banking — the strictest setting.**
A retail bank uses a **15-minute** access token, a **30-day** refresh token with a
**7-day idle** expiry, and **mandatory device binding**: the refresh token row records the
device fingerprint, and a refresh from a *different* device forces a **re-authentication
with MFA**. "Log out everywhere" is a **regulatory requirement** and is implemented with
both family revocation **and** a token-version bump. Every refresh is audited with the
device, IP and geolocation; a refresh from a **new country** triggers a **step-up** and
a notification email. Scale: 300,000 customers, ~40 refreshes/day each → ~140
refreshes/second at peak.

---

**3. A B2B SaaS — shared devices and a long idle life.**
An accounting SaaS is used from shared office PCs. The access token is **10 minutes**;
the refresh token is **12 hours idle / 30 days absolute**. Because the PC is shared, the
product ships a **"this is a shared device"** toggle that shortens the refresh TTL to
**2 hours** and disables "remember me". Revoking one session does **not** touch the
others — each browser profile is its own family.

**4. A mobile app — the native pattern.**
An iOS/Android app cannot use a cookie safely (the OS keychain, not the cookie jar, is
the right store). The refresh token is returned in the **response body** of the login
call, stored in the **iOS Keychain** / **Android EncryptedSharedPreferences**, and sent
in the **request body** of `/refresh`. Because there is no cookie, **CSRF does not
apply** — a native app is not a browser. The refresh token is still hashed server-side
and still rotated with reuse detection. Scale: 4M devices, ~1.2M refreshes/hour at peak.

## 8. Worked Examples (with Output + Dry Run)

### 8.1 Worked Example 1 — A `RefreshTokenService` with issue → rotate → reuse detection

**Problem.** Implement the state machine correctly and see exactly what it writes and
logs.

**Code.**

```java
package com.example.auth.token;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;          // 256 bits of entropy
    private static final Duration IDLE_TTL = Duration.ofDays(7);
    private static final Duration ABSOLUTE_TTL = Duration.ofDays(30);

    private final RefreshTokenRepository repository;
    private final RefreshTokenAudit audit;

    public RefreshTokenService(RefreshTokenRepository repository, RefreshTokenAudit audit) {
        this.repository = repository;
        this.audit = audit;
    }

    /** Mint a brand-new token — the first link of a new family. */
    @Transactional
    public IssuedRefreshToken issue(Long userId, String deviceInfo, String ip) {
        Instant now = Instant.now();
        UUID familyId = UUID.randomUUID();
        IssuedRefreshToken issued = mint(familyId, now.plus(ABSOLUTE_TTL), now);
        RefreshToken row = RefreshToken.issue(
                userId, familyId, issued.rawTokenHash(), issued.rawToken(),
                now, issued.expiresAt(), deviceInfo, ip);
        repository.save(row);
        log.info("REFRESH_ISSUE user={} family={} device={} ip={}",
                userId, familyId, deviceInfo, ip);
        return issued;
    }

    /**
     * Rotate: validate, then exchange the presented token for a new one in the SAME family.
     */
    @Transactional
    public RotationResult rotate(String rawToken, String deviceInfo, String ip) {
        Instant now = Instant.now();
        String hash = sha256Hex(rawToken);
        RefreshToken row = repository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("unknown token"));

        if (row.getRevokedAt() != null) {
            throw new InvalidRefreshTokenException("token revoked");
        }
        if (row.getExpiresAt().isBefore(now)) {
            throw new InvalidRefreshTokenException("token expired");
        }

        if (row.getUsedAt() != null) {
            // ── REUSE DETECTION ────────────────────────────────────────────
            int revoked = repository.revokeFamily(row.getFamilyId(), now);
            audit.reuseDetected(row, deviceInfo, ip, revoked);
            log.warn("SECURITY_EVENT refresh_token_reuse user={} family={} device={} ip={} familySize={}",
                    row.getUserId(), row.getFamilyId(), deviceInfo, ip, revoked);
            throw new InvalidRefreshTokenException("token already used");
        }

        IssuedRefreshToken issued = mint(row.getFamilyId(), row.getExpiresAt(), now);
        RefreshToken next = RefreshToken.issue(
                row.getUserId(), row.getFamilyId(), issued.rawTokenHash(), issued.rawToken(),
                now, issued.expiresAt(), deviceInfo, ip);
        repository.save(next);

        row.setUsedAt(now);
        row.setReplacedById(next.getId());
        repository.save(row);

        log.info("REFRESH_ROTATE user={} family={} old={} new={}",
                row.getUserId(), row.getFamilyId(), row.getId(), next.getId());
        return new RotationResult(row.getUserId(), issued, row.getFamilyId());
    }

    /** Revoke every family of a user — the "log out everywhere" primitive. */
    @Transactional
    public int revokeAllForUser(Long userId) {
        int n = repository.revokeAllForUser(userId, Instant.now());
        log.info("REFRESH_REVOKE_ALL user={} count={}", userId, n);
        return n;
    }

    private IssuedRefreshToken mint(UUID familyId, Instant absoluteExpiry, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = min(absoluteExpiry, now.plus(IDLE_TTL));
        return new IssuedRefreshToken(raw, sha256Hex(raw), expiresAt);
    }

    /**
     * WHY SHA-256 and not bcrypt: a refresh token is 256 bits of SecureRandom, so there is
     * no dictionary to attack — a slow KDF would only add latency to the refresh path.
     * A PASSWORD is the opposite: low entropy, so it needs bcrypt/Argon2.
     */
    static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
```

**The entity and the repository.**

```java
package com.example.auth.token;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_token", indexes = {
        @Index(name = "ux_refresh_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "ix_refresh_token_user", columnList = "user_id"),
        @Index(name = "ix_refresh_token_family", columnList = "family_id")
})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @Column(name = "ip", length = 45)
    private String ip;

    // getters / setters
}
```

```java
package com.example.auth.token;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** WHY a bulk UPDATE and not a load-and-save loop: one statement, one transaction. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now "
            + "WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now "
            + "WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Query("SELECT t FROM RefreshToken t WHERE t.userId = :userId "
            + "AND t.revokedAt IS NULL AND t.usedAt IS NULL AND t.expiresAt > :now "
            + "ORDER BY t.issuedAt DESC")
    List<RefreshToken> findActiveSessions(@Param("userId") Long userId, @Param("now") Instant now);
}
```

**Actual expected output.**

```text
2026-01-14 10:00:00.114 INFO  c.e.a.t.RefreshTokenService : REFRESH_ISSUE user=42957 family=0f9c1e3a-77b2-4d1e-9f30-1a2b3c4d5e6f device="Chrome 133 / macOS 15" ip=203.0.113.24
2026-01-14 10:10:03.902 INFO  c.e.a.t.RefreshTokenService : REFRESH_ROTATE user=42957 family=0f9c1e3a-77b2-4d1e-9f30-1a2b3c4d5e6f old=1041 new=1042
2026-01-14 10:20:11.455 INFO  c.e.a.t.RefreshTokenService : REFRESH_ROTATE user=42957 family=0f9c1e3a-77b2-4d1e-9f30-1a2b3c4d5e6f old=1042 new=1043
2026-01-14 10:35:02.001 INFO  c.e.a.t.RefreshTokenService : REFRESH_ISSUE user=42957 family=88d4f2b1-0c3a-4e5b-8a71-9b0c1d2e3f40 device="iPhone 16 / iOS 19" ip=198.51.100.7
2026-01-14 10:35:44.220 WARN  c.e.a.t.RefreshTokenService : SECURITY_EVENT refresh_token_reuse user=42957 family=0f9c1e3a-77b2-4d1e-9f30-1a2b3c4d5e6f device="Chrome 133 / macOS 15" ip=203.0.113.24 familySize=3
```

**The exact rows after the sequence above.**

```text
 id | user_id | token_hash (first 16) | family_id    | issued_at            | expires_at            | used_at              | revoked_at | replaced_by_id
----+---------+-----------------------+--------------+----------------------+-----------------------+----------------------+------------+---------------
1041 | 42957  | a3f19c2b7e5d04a8      | 0f9c1e3a-…   | 2026-01-14 10:00:00  | 2026-01-21 10:00:00  | 2026-01-14 10:10:03 | NULL       | 1042
1042 | 42957  | 7c02e9a41f8b6d31      | 0f9c1e3a-…   | 2026-01-14 10:10:03 | 2026-01-21 10:00:00  | 2026-01-14 10:20:11 | NULL       | 1043
1043 | 42957  | 55b8d0e71c9f2a46      | 0f9c1e3a-…   | 2026-01-14 10:20:11 | 2026-01-21 10:00:00  | NULL                 | NULL       | NULL
1044 | 42957  | e10c7b4d92a6f338      | 88d4f2b1-…   | 2026-01-14 10:35:02 | 2026-02-13 10:35:02  | NULL                 | NULL       | NULL
```

**Dry run — the reuse-detection path, one step at a time.**

1. **10:00:00** — `issue()` is called. `RANDOM.nextBytes(new byte[32])` produces 32 bytes
   of CSPRNG output; base64url without padding turns them into a **43-character** raw
   token. `sha256Hex(raw)` produces a **64-character** hex hash. The row is inserted with
   `used_at = NULL`, `revoked_at = NULL`, `expires_at = min(now+30d, now+7d) = now+7d`.
   *Note: the first token's `expires_at` is `now + 7 days`, because the idle TTL (7 d) is
   shorter than the absolute TTL (30 d) — and the **absolute** ceiling of 30 days is
   carried by the family.*
2. **10:10:03** — the client refreshes with the raw token. The hash matches row 1041.
   `used_at` is NULL → not a reuse. A **new** token is minted **in the same family**, its
   row is inserted, then row 1041 is updated with `used_at` and `replaced_by_id = 1042`.
   *The order matters: the successor must exist before the predecessor is marked used,
   otherwise a crash between the two leaves the user with a consumed token and no
   successor.*
3. **10:20:11** — same thing for row 1042 → row 1043.
4. **10:35:02** — a **second device** (iPhone) logs in. A **new family** `88d4f2b1-…`
   is created. Families are per device session, so the two devices are independent.
5. **10:35:44** — the thief presents the **original** token (row 1041's raw value).
   The hash lookup finds row 1041. `revoked_at` is NULL, `expires_at` is in the future,
   **but `used_at` is set** → **REUSE DETECTED**.
   - `revokeFamily(familyId)` issues
     `UPDATE refresh_token SET revoked_at = now WHERE family_id = ? AND revoked_at IS NULL`
     → it matches rows **1041, 1042 and 1043** → `familySize=3`.
   - An audit event is recorded and a `WARN` is logged with the user, family, device and
     IP.
   - A `401` is returned.
6. **Consequence:** the **legitimate** client's next refresh (with row 1043's token) also
   fails, because row 1043 is now revoked. The user is forced to log in again. That is
   the intended trade-off — the thief is evicted and the incident is on the record.

**Takeaway.** Rotation alone does not stop a thief; **rotation + reuse detection +
family revocation** does. And the cost of that protection is one extra login for the
victim.

---

### 8.2 Worked Example 2 — The client-side "retry once after refresh" pattern

**Problem.** The client must refresh automatically on a 401 and retry **exactly once** —
without two parallel requests triggering a false reuse-detection event.

**Code — a Spring `RestClient` interceptor, and the single-flight lock.**

```java
package com.example.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * WHY an interceptor rather than logic in every caller: the refresh-and-retry behaviour
 * must be IDENTICAL everywhere, and it must happen exactly once per request.
 */
public class BearerRefreshInterceptor implements org.springframework.http.client.ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(BearerRefreshInterceptor.class);

    private final TokenClient tokenClient;   // holds the current access token + the refresh logic
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile CompletableFuture<String> inFlight;

    @Override
    public org.springframework.http.client.ClientHttpRequestExecution intercept(
            org.springframework.http.HttpRequest request,
            byte[] body,
            org.springframework.http.client.ClientHttpRequestExecution execution) {

        String token = tokenClient.currentAccessToken();
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);

        if (response.getStatusCode().value() != 401) {
            return response;
        }

        // ── ONE 401 → refresh ONCE → retry ONCE ───────────────────────────────
        // WHY the lock: two parallel 401s must NOT both call /refresh with the same
        // refresh token, or the server sees a "reuse" and revokes the whole family.
        if (!refreshing.compareAndSet(false, true)) {
            // Another thread is already refreshing — wait for it and retry with the new token.
            String newToken = awaitInFlight();
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + newToken);
            return execution.execute(request, body);
        }

        try {
            String newToken = tokenClient.refresh();     // ONE call to /refresh
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + newToken);
            org.springframework.http.client.ClientHttpResponse retried = execution.execute(request, body);
            // WHY exactly one retry: if the retried request also 401s, the token is
            // genuinely invalid — looping would hammer the auth server and could
            // trigger rate limiting.
            return retried;
        } finally {
            refreshing.set(false);
        }
    }

    private String awaitInFlight() {
        try {
            return inFlight.join();
        } catch (java.util.concurrent.CompletionException ex) {
            throw new IllegalStateException("token refresh failed", ex.getCause());
        }
    }
}
```

**The `TokenClient` — one place that owns the tokens.**

**Actual expected output — the exact request sequence.**

```text
# ── Scenario A: a normal, unexpired token ───────────────────────────────────
GET /api/orders/42                       Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
→ 200 OK

# ── Scenario B: the access token expired ───────────────────────────────────
GET /api/orders/42                       Authorization: Bearer eyJhbGciOiJSUzI1NiJ9... (EXPIRED)
→ 401 Unauthorized   WWW-Authenticate: Bearer error="invalid_token"

POST /api/auth/refresh                   Content-Type: application/json
                                        {"refreshToken":"kQ8s...43 chars"}
→ 200 OK  {"accessToken":"eyJhbGciOiJSUzI1NiJ9...NEW","expiresIn":600,
           "refreshToken":"Rt7x...43 chars NEW"}

GET /api/orders/42                       Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...NEW   ← RETRY ONCE
→ 200 OK

# ── Scenario C: the retry ALSO 401s (token genuinely invalid) ──────────────
GET /api/orders/42                       Authorization: Bearer eyJ... (EXPIRED)
→ 401 Unauthorized
POST /api/auth/refresh                   {"refreshToken":"..."}
→ 200 OK  {"accessToken":"eyJ...NEW"}
GET /api/orders/42                       Authorization: Bearer eyJ...NEW
→ 401 Unauthorized                        ← the token is valid but the CALL is not authorised
→ NO second refresh, NO loop. The client surfaces the error.
```

**Dry run — step by step, scenario B.**

1. The interceptor runs on **every** outgoing request and sets
   `Authorization: Bearer <currentAccessToken>`.
2. The response is 401. The interceptor checks `statusCode == 401` → yes.
3. `refreshing.compareAndSet(false, true)` succeeds → this thread owns the refresh.
4. `tokenClient.refresh()` checks the local expiry with a **60-second margin** — if the
   token is still fresh it returns it without a network call. (In scenario B it is
   expired, so it posts.)
5. `POST /api/auth/refresh` with the refresh token in the body. The server rotates it
   and returns a **new** pair. The client stores both.
6. The interceptor sets the **new** access token on the request and calls
   `execution.execute(...)` **again** — the retry.
7. The retried request returns **200**.
8. `finally` resets `refreshing` to `false`.

**Why the lock matters.** If two requests 401 simultaneously, the second thread fails
the `compareAndSet` and waits for the first thread's refresh instead of issuing its own.
Without it, both threads would present the **same** refresh token, the server would see
the second one as a **reuse**, and it would revoke the whole family — logging the user
out. This is the single most common self-inflicted bug in refresh implementations.

**Takeaway.** The refresh path needs: **one** refresh call per burst, **exactly one**
retry, and a **margin** on the local expiry so you refresh proactively rather than
reactively.

---

### 8.3 Worked Example 3 — "List my devices" and "revoke one"

**Problem.** A user wants to see where they are logged in and to kill a session they do
not recognise.

**Code — the service and the controller.**

```java
package com.example.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth/sessions")
public class DeviceSessionController {

    private final RefreshTokenRepository repository;

    public DeviceSessionController(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<RefreshToken> list(@AuthenticationPrincipal Jwt jwt) {
        // WHY the subject comes from the VALIDATED token, never from a header.
        return repository.findActiveSessions(Long.valueOf(jwt.getSubject()), Instant.now());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        // WHY user_id is in the WHERE clause: an IDOR guard. A mismatch must be 404, not 403.
        int n = repository.revokeByIdAndUserId(id, Long.valueOf(jwt.getSubject()), Instant.now());
        if (n == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found");
        }
        return ResponseEntity.noContent().build();
    }
}
```

**Actual expected output — `GET /api/auth/sessions`.**

```json
[
  {
    "id": 1044,
    "deviceInfo": "iPhone 16 / iOS 19",
    "ip": "198.51.100.7",
    "issuedAt": "2026-01-14T10:35:02Z",
    "expiresAt": "2026-02-13T10:35:02Z",
    "usedAt": null,
    "current": true
  },
  {
    "id": 1043,
    "deviceInfo": "Chrome 133 / macOS 15",
    "ip": "203.0.113.24",
    "issuedAt": "2026-01-14T10:20:11Z",
    "expiresAt": "2026-01-21T10:00:00Z",
    "usedAt": null,
    "current": false
  }
]
```

**Actual expected output — `DELETE /api/auth/sessions/1043`.**

```text
HTTP/1.1 204 No Content
```

**Dry run — step by step.**

1. `GET /api/auth/sessions` requires an access token. The `BearerTokenAuthenticationFilter`
   authenticates it and puts a `JwtAuthenticationToken` in the context.
2. `@AuthenticationPrincipal Jwt jwt` injects the validated `Jwt`. `jwt.getSubject()` is
   `"42957"` — the **string** user id from the `sub` claim. `Long.valueOf(...)` converts
   it. *Note: this is why the `sub` claim should be a **stable, parseable** identifier —
   a UUID string or a numeric string, never a display name.*
3. `findActiveSessions(userId, now)` runs:
   `SELECT * FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL AND used_at IS NULL AND expires_at > ? ORDER BY issued_at DESC`
   → rows **1044** and **1043**. Note that **1041 and 1042 are excluded** — they were
   rotated (`used_at` is set), and a rotated token is *not* an active session. Only the
   **current** link of each family appears.
4. The list is mapped to `ActiveSession` records.
5. The `current` flag is computed by comparing the session id with the one attached to
   the caller's refresh cookie — the API does not know which session the caller is using
   from the access token alone, so the client should send the session id, or the server
   derives it from the refresh cookie.
6. `DELETE /api/auth/sessions/1043` → `revokeByIdAndUserId(1043, 42957, now)`.
   The `AND user_id = ?` clause is the **IDOR guard** — if a user passes another user's
   session id, the update matches **0 rows** and the server returns **404**, not 403.
   (Returning 403 would leak the existence of the id.)
7. `204 No Content` — no body, per the canonical status table.

**Takeaway.** The device list is a **query on the refresh-token table** — almost free.
The important correctness details are the **IDOR guard** on the revoke path and the
filtering that hides **rotated** tokens from the list (they are not sessions).

---

## 9. Code from Scratch — Production-Grade

**The complete refresh flow — controller, service and cookie handling.**

```java
package com.example.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Duration REFRESH_COOKIE_TTL = Duration.ofDays(30);

    private final AuthService authService;
    private final RefreshTokenService refreshTokens;
    private final TokenService tokenService;

    public AuthController(AuthService authService, RefreshTokenService refreshTokens, TokenService tokenService) {
        this.authService = authService;
        this.refreshTokens = refreshTokens;
        this.tokenService = tokenService;
    }

    /** ── LOGIN ─────────────────────────────────────────────────────────── */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest servletRequest) {
        LoginResult result = authService.authenticate(request.username(), request.password());
        IssuedRefreshToken refresh = refreshTokens.issue(
                result.userId(), deviceInfo(servletRequest), clientIp(servletRequest));
        String accessToken = tokenService.issueAccessToken(result);

        return ResponseEntity.ok()
                // WHY the refresh token is NOT in the body: the body is readable by
                // JavaScript. In a browser it belongs in an HttpOnly cookie.
                .body(new LoginResponse(accessToken, 600, "Bearer"))
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refresh.rawToken()).toString());
    }

    /** ── REFRESH ───────────────────────────────────────────────────────── */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            // WHY the cookie is @RequestParam(required=false): native clients send the
            // token in the body instead, and this endpoint serves BOTH.
            @CookieValue(name = "rt", required = false) String cookieToken,
            @RequestBody(required = false) RefreshRequest body,
            HttpServletRequest servletRequest) {

        String raw = (cookieToken != null) ? cookieToken : (body == null ? null : body.refreshToken());
        if (raw == null || raw.isBlank()) {
            throw new InvalidRefreshTokenException("no refresh token presented");
        }

        RotationResult rotation = refreshTokens.rotate(raw, deviceInfo(servletRequest), clientIp(servletRequest));
        String accessToken = tokenService.issueAccessToken(rotation.userId());

        return ResponseEntity.ok()
                .body(new RefreshResponse(accessToken, 600))
                .header(HttpHeaders.SET_COOKIE, refreshCookie(rotation.issued().rawToken()).toString());
    }

    /** ── LOGOUT (this device) ──────────────────────────────────────────── */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "rt", required = false) String cookieToken) {
        if (cookieToken != null) {
            refreshTokens.revokeByRawToken(cookieToken);
        }
        // WHY maxAge(0): this DELETES the cookie in the browser. Without it, the
        // raw token stays in the cookie jar and will be presented again.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("").maxAge(Duration.ZERO).toString())
                .build();
    }

    /** ── LOGOUT EVERYWHERE ─────────────────────────────────────────────── */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        int sessions = refreshTokens.revokeAllForUser(userId);
        // WHY the token-version bump: revoking the refresh tokens stops FUTURE refreshes,
        // but the access tokens already issued are still valid. The version bump kills
        // those too (see chapter 03).
        tokenService.bumpTokenVersion(userId);
        return ResponseEntity.noContent()
                .header("X-Revoked-Sessions", String.valueOf(sessions))
                .build();
    }

    private ResponseCookie refreshCookie(String rawToken) {
        return ResponseCookie.from("rt", rawToken)
                .httpOnly(true)             // JS cannot read it → survives XSS
                .secure(true)              // HTTPS only → survives sniffing
                .sameSite("Strict")        // not sent cross-site → survives CSRF
                .path("/api/auth")         // only the auth endpoints ever receive it
                .maxAge(REFRESH_COOKIE_TTL)
                .build();
    }

    private String deviceInfo(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua == null ? "unknown" : (ua.length() > 200 ? ua.substring(0, 200) : ua);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

**The DTOs.**
```java
package com.example.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}

public record RefreshRequest(String refreshToken) {
}

public record LoginResponse(String accessToken, long expiresIn, String tokenType) {
}

public record RefreshResponse(String accessToken, long expiresIn) {
}
```

**The security configuration — CSRF on the refresh endpoint, off on the API.**
```java
package com.example.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class AuthSecurityConfig {

    @Bean
    SecurityFilterChain authChain(HttpSecurity http) throws Exception {
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // WHY CSRF is ON for the auth endpoints: the refresh token lives in a
                // cookie and the browser attaches cookies automatically, so a cross-site
                // page could make the browser call /refresh. The API endpoints stay
                // CSRF-free — they authenticate with the Authorization header, which a
                // browser never attaches on its own.
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository())
                        .ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
                        .permitAll()
                        .requestMatchers("/api/**").authenticated());
        return http.build();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        // WHY withHttpOnlyFalse: the SPA must READ the token to echo it in a header.
        // The CSRF token is not a credential — the refresh cookie is — so it is fine.
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        return repository;
    }
}
```

**The access-token issuer.**

```java
package com.example.auth;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    /** WHY 10 minutes: the TTL is the revocation window. Keep it <= 15. */
    static final Duration ACCESS_TTL = Duration.ofMinutes(10);

    private final JwtEncoder jwtEncoder;
    private final TokenVersionService tokenVersions;

    public TokenService(JwtEncoder jwtEncoder, TokenVersionService tokenVersions) {
        this.jwtEncoder = jwtEncoder;
        this.tokenVersions = tokenVersions;
    }

    public String issueAccessToken(LoginResult user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://auth.acme.com")
                .subject(String.valueOf(user.userId()))
                .audience(List.of("orders-api", "payments-api"))
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TTL))
                .id(java.util.UUID.randomUUID().toString())
                .claim("roles", user.roles())
                .claim("ver", tokenVersions.current(user.userId()))   // the logout-everywhere lever
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type("at+jwt").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public void bumpTokenVersion(Long userId) {
        tokenVersions.increment(userId);
    }
}
```

---

## 10. Common Mistakes & Gotchas

### ❌ Mistake 1 — Storing the refresh token in plaintext

```java
// ❌ A database dump hands the attacker 30-day credentials
refreshToken.setTokenHash(rawToken);
```
**✅ right.** Hash it.

```java
refreshToken.setTokenHash(sha256Hex(rawToken));
```

---

### ❌ Mistake 2 — Using bcrypt for the refresh token

```java
// ❌ ~100 ms per hash — on a path that runs for every logged-in user
String hash = passwordEncoder.encode(rawToken);
```
**✅ right.** SHA-256 for a 256-bit random token; bcrypt/Argon2 only for a *password*.

---

### ❌ Mistake 3 — Rotating without reuse detection

```java
// ❌ The thief's copy still works — rotation alone protects nothing
row.setUsedAt(now);
```
**✅ right.** Detect `usedAt != null` → revoke the family → audit.

---

### ❌ Mistake 4 — Returning the refresh token in the JSON body of a browser login

```java
// ❌ Readable by any XSS on the page
return ResponseEntity.ok(new LoginResponse(accessToken, refreshToken));
```
**✅ right.** The browser path uses an `HttpOnly` cookie. The body is for native clients.

---

### ❌ Mistake 5 — Storing the refresh token in `localStorage`

```javascript
// ❌ Any XSS reads a 30-day credential
localStorage.setItem('refreshToken', data.refreshToken);
```
**✅ right.** `HttpOnly` cookie for the browser; the OS keychain for native.

---

### ❌ Mistake 6 — The client retries in a loop

```javascript
// ❌ One expired token → an infinite 401 storm against your own auth server
while (true) { try { return await call(); } catch (e) { await refresh(); } }
```
**✅ right.** Refresh once, retry once, then surface the error.

---

### ❌ Mistake 7 — Not guarding the revoke endpoint against IDOR

```java
// ❌ Any user can revoke any session by guessing an id
repository.revokeById(sessionId, now);
```
**✅ right.** Scope by `user_id` too, and return **404** when it does not match.

---

## 11. Performance Considerations

---

**The volumes that decide the architecture.**

| Metric | Formula | Example (100k DAU, 10-min access token) |
|---|---|---|
| Refresh requests/second | `users × 144 / 86,400` | ≈ **167/s** average |
| Peak refresh rate | ~5× average | **~800/s** |
| Rows created per day | refreshes/day | **144,000/day** ≈ 5.2M rows/month |
| Rows per family over 30 days | `30 × 144` | **4,320** |

**The key realisation: the refresh path is NOT the hot path.** At 167/s average, even
Postgres handles this trivially — one indexed `SELECT`, one `INSERT` and one `UPDATE` is
~1–3 ms of database time, which is 0.5% of a 10-minute access-token lifetime. Teams that
reach for Redis to "scale the refresh path" are optimising the wrong 1% of their traffic.

**The per-request cost of the refresh endpoint.**

| Step | Cost |
|---|---|
| SHA-256 of a 43-char token | **< 1 µs** |
| `SELECT ... WHERE token_hash = ?` (unique index) | **0.3–1.5 ms** |
| `INSERT` the successor | 0.5–2 ms |
| `UPDATE` the predecessor | 0.3–1 ms |
| Access-token signing (RS256 / ES256) | 0.7–1.2 ms / 0.02–0.1 ms |

**Where the real cost is — the table growth.** 5.2M rows/month with 4,320 rows per
family is the problem, not the query latency. The mitigations: prune nightly
(`expires_at < now() - 7 days`), partition by month so pruning is a `DROP PARTITION`,
or store only the **current** token per family plus a generation counter — one row
instead of 4,320.

**The refresh-storm risk.** If the auth server has an outage, every client with an
expired access token retries `/refresh` — a thundering herd. Mitigations: exponential
backoff with jitter, a circuit breaker, and **proactive refresh** at 80% of the TTL so
the retries are spread out instead of synchronised on a single expiry boundary.

**What to measure.**

| Metric | Target |
|---|---|
| `auth.refresh.duration` (p99) | < **50 ms** |
| `auth.refresh.count` by outcome | rotated vs reuse_detected vs expired vs unknown |
| `auth.refresh.reuse_detected` | **≈ 0** — a non-zero value is an incident or a client bug |
| `refresh_token.rowcount` per family | ≤ **100** |
| `auth.login.count` / `auth.refresh.count` | ~1:144 for a 10-minute TTL |

## 12. Security Considerations

**The threat model for the refresh flow.**

| Threat | Attack | The defence |
|---|---|---|
| **Refresh token theft (XSS)** | Malicious script reads the token | `HttpOnly` cookie; **never** `localStorage`; strict CSP; output encoding |
| **Refresh token theft (device)** | The device is stolen / left unlocked | Short idle TTL; device fingerprinting; re-authentication to view sensitive data |
| **Refresh token in a URL** | `?refresh_token=…` lands in logs and history | Never in a URL — cookie or body only |
| **CSRF on the refresh endpoint** | A malicious page triggers a refresh | `SameSite=Strict` + a CSRF token or a custom header + strict CORS |
| **Replay of a rotated token** | The thief reuses the stolen token | **Reuse detection** → revoke the family |
| **Token guessing** | Brute force the refresh endpoint | 256-bit random tokens + **rate limiting** on `/refresh` |
| **Database dump** | The attacker reads the table | Store the **hash** only |
| **Privilege escalation via a stale token** | The user's role changed 5 minutes ago | Short access TTL; a token-version bump on role change |
| **Refresh after logout** | The token is presented after logout | `revoked_at` set at logout → **401** |

**The rules.**

1. **Store only the hash.** Never the raw refresh token.
2. **Rotate on every use, and detect reuse.**
3. **Revoke the family on reuse**, and audit the event.
4. **`HttpOnly; Secure; SameSite=Strict`** for the browser path.
5. **CSRF protection on the cookie-based refresh endpoint.**
6. **Rate-limit `/refresh`** — it is a password-equivalent endpoint. 20/min per IP,
   10/min per session, and a stricter limit per account.
7. **Never log the raw token.** Log the hash prefix (`a3f19c2b7e5d04a8`) if you need
   correlation.
8. **Never put the refresh token in a URL.**
9. **Absolute + idle expiry**, never pure sliding.
10. **Prune the table**, and alert on the row count per family.
11. **On logout, revoke the refresh token *and* bump the token version** so the live
    access tokens die too.
12. **Detect impossible travel** — a refresh from a new country should trigger a
    notification, even if it is technically valid.

**Why the refresh token must not be an API credential.** If `/api/orders` accepts the
refresh token, then the refresh token becomes a **30-day bearer credential** with the
user's full authority — and it is sent in a header on every request, so it lands in
access logs, APM traces and proxy logs. The two-token design only works if the long-lived
credential touches exactly one endpoint. The implementation-level guard is to give the
refresh token a **different audience** or a **different `typ`** (`"rt+jwt"` vs `"at+jwt"`)
and to have the API validate that.

---

## 13. Best Practices

1. **The refresh token is opaque**, 256 bits from `SecureRandom`, base64url-encoded.
2. **Stored as a SHA-256 hash**, in a `CHAR(64)` column with a **unique index**.
3. **Rotated on every single use**, with a **family id** and **reuse detection**.
4. **Reuse detection revokes the whole family** and writes an audit event.
5. **Absolute expiry + idle expiry**, never pure sliding.
6. **`HttpOnly; Secure; SameSite=Strict; Path=/api/auth`** for the browser path.
7. **CSRF protection on the cookie-based refresh endpoint**; off for the bearer API.
8. **The refresh token is never in a JSON body for a browser client**, and never in
   `localStorage`.
9. **The client refreshes once and retries once**, with a single-flight lock.
10. **Rate-limited** `/refresh` endpoint, by IP and by account.
11. **Nightly pruning** of expired rows, with a per-family row-count alert.
12. **The logout endpoint revokes the refresh token** *and* bumps the token version.
13. **The device list is a query on the refresh-token table**, with `user_id` scoping on
    every mutation (IDOR-safe).
14. **Every refresh is audited** with the user, family, device, IP and outcome.
15. **The refresh endpoint is `permitAll` in Spring Security** but does its own strict
    validation — possession of the token *is* the authorisation.
16. **No refresh token is ever accepted by a business endpoint**, and the access token is
    never accepted at `/refresh`.

---

## 14. Hands-On Exercises & Code Challenges

### Exercise 1 — Implement rotation with reuse detection

**Steps.**
1. Create the `RefreshToken` entity and the repository with the three indexes.
2. Implement `issue()` — mint a random token, hash it, insert the row with a new
   `family_id`.
3. Implement `rotate()` — the full state machine from §4.
4. Write an integration test (`@SpringBootTest`) that issues, rotates twice, and asserts
   the three rows' `used_at` / `replaced_by_id` values.
5. Then present the **first** token again and assert that the whole family is revoked.

**Acceptance criteria.**
- `POST /refresh` with a valid token returns **200** and a new pair.
- Presenting a consumed token returns **401** and revokes **every** row in the family.
- The raw token is **not** present in any row (assert with a SQL query or by inspecting
  the entity).
- The service method is `@Transactional` and the audit write is `REQUIRES_NEW`.

---

### Exercise 2 — Write the test that proves a stolen token revokes the family

**Steps.**
1. Use the `@SpringBootTest` slice from Exercise 1.
2. Log in as user A → capture token `R1`.
3. Refresh with `R1` → capture `R2`. Assert `R1`'s row has `used_at != null`.
4. Simulate the thief: refresh with `R1` again → assert **401**.
5. Assert that the **legitimate** client's `R2` also now fails → **401**.
6. Assert the audit log contains a `refresh_token_reuse` event with the user id.

**Acceptance criteria.**
- Steps 4, 5 and 6 all pass.
- The test asserts on **behaviour**, not on implementation details.
- The test has a comment explaining *why* step 5 is the **desired** outcome.

---

### Exercise 3 — Break the transport and find the bug

**Steps.**
1. Deploy the app and log in from a browser. Inspect the `Set-Cookie` header.
2. Remove `httpOnly(true)` and use `document.cookie` in the console — the token is now
   readable by JavaScript. Write down what an XSS could now steal.
3. Remove `sameSite("Strict")` and post a form from `evil.example.com` to `/api/auth/refresh`
   — observe whether the cookie is attached.
4. Remove the CSRF protection from the auth endpoints and repeat step 3.

**Acceptance criteria.**
- You can explain, in one paragraph, the difference between **reading** a cookie
  (`HttpOnly`) and **causing the browser to send** it (`SameSite`/CSRF).
- You can show a cross-origin request failing with a CORS preflight error.

**Stretch goal.** Reproduce the full attack in a headless browser: serve a page from a
second origin that submits the form, and show the refresh succeeding without CSRF.

---

### Code Challenge 1 — A complete, production-grade session service

**Problem.** Build the whole session service: issue, rotate, reuse detection, revoke one,
revoke all, list devices, and a nightly pruning job — with the audit trail.

**Acceptance criteria.**
- `RefreshToken`, `RefreshTokenRepository`, `RefreshTokenService`, `DeviceSessionService`
  and a `RefreshTokenAudit` component.
- The `RefreshToken` entity has the unique index on `token_hash` and indexes on
  `user_id` and `family_id`.
- `rotate()` is `@Transactional`; the audit write uses `REQUIRES_NEW`.
- `revokeByIdAndUserId(...)` scopes by `user_id` — and the controller returns **404**
  (not 403) when it matches nothing.
- A `GET /api/auth/sessions` endpoint that returns only **active** sessions
  (`used_at IS NULL AND revoked_at IS NULL AND expires_at > now`).
- A scheduled pruning job that deletes rows where `expires_at < now - 7 days`, with the
  delete count logged.
- Unit tests for: issue → rotate → reuse detection; the IDOR guard; the pruning query.

---

### Code Challenge 2 — A client that refreshes correctly under concurrency

**Problem.** Implement a client-side token manager and a `RestClient` interceptor that
refresh on a 401, retry once, and never issue two concurrent refreshes.

**Acceptance criteria.**
- A **single-flight** mechanism: concurrent 401s result in **exactly one** `/refresh`
  call (assert with a mock server's call counter).
- **Exactly one** retry per request — assert that a second 401 does not trigger a second
  refresh.
- A proactive refresh with a **60-second margin** on the local expiry.
- Exponential backoff with jitter after a failed refresh (3 attempts, capped).
- A test that fires **10 parallel** requests against a server that returns 401 once, and
  asserts **1** refresh and **10** successful retries.

**Stretch goal.** Add a "refresh queue" so that a refresh failure does not cause an
immediate stampede — the queued requests all fail with a single **401** and the client
signals "session expired" to the UI exactly once.

---

## 15. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: Why do we need two tokens instead of one long-lived one?**
**A:** Because they solve opposite problems. The access token is used on **every** API
call, so it must be verifiable cheaply and without server state — a JWT. The refresh
token is used **rarely**, so it can afford a database lookup, and that lookup is what
makes it **revocable**. A single long-lived token would either be slow to verify (if
stateful) or unrevocable (if stateless). The split gives you a cheap hot path and a
revocable root of trust.

**Q: What is a refresh token and how is it different from an access token?**
**A:** The refresh token is a long-lived credential (7–30 days) whose only job is to
exchange for a new access token. It is stored **hashed** server-side, so it can be
revoked; it is delivered in an `HttpOnly` cookie for browsers; and it is presented to
**exactly one** endpoint. If it were accepted by business APIs, you would have a 30-day
bearer credential with the user's full authority.

**Q: Where is the refresh token stored in a browser application?**
**A:** In an **`HttpOnly; Secure; SameSite=Strict`** cookie, scoped to the auth path.
`HttpOnly` means JavaScript cannot read it, so an XSS cannot steal it directly;
`SameSite=Strict` means the browser will not attach it to a cross-site request, which
blocks CSRF. The worst option is `localStorage`, which any XSS on the page can read.

**Q: What happens when the access token expires?**
**A:** The API returns **401** with `WWW-Authenticate: Bearer error="invalid_token"`. The
client notices the 401, calls the refresh endpoint with its refresh token, receives a new
access token, and retries the original request **once**. The user sees nothing — the
whole exchange happens in the background.

### 🟡 Intermediate

**Q: Explain refresh-token rotation and why it matters.**
**A:** Rotation means that every time a refresh token is used, the server issues a **new**
refresh token and **invalidates** the presented one. The old token is marked
`used_at` and points to its successor. The point is that the token has a very short
useful life — if an attacker steals it, they get **one** use at most, and that use is
**detectable**. Without rotation, a stolen token is valid for its entire 30-day life
and there is no signal at all.

**Q: What is refresh-token reuse detection?**
**A:** When a token that has already been used is presented again, it means **two
parties hold it** — the legitimate client and somebody else. The server cannot tell
which is the thief, so it revokes the **entire token family** (all the descendants of
that token, tracked by `family_id`), forces a re-login, and records a security audit
event. The legitimate user is logged out too — that is the intended cost.

**Q: How should the refresh token be stored server-side, and why not a slow KDF?**
**A:** As a **SHA-256 hash** of the raw token, with a unique index. The reason a slow KDF
is wrong here is entropy: a password is low-entropy, so bcrypt/Argon2 makes each guess
expensive; a refresh token is **256 bits of `SecureRandom`**, so guessing is already
infeasible and a slow KDF would only add latency to the refresh path. The rule is *KDF
strength must match entropy*. Storing the hash also means a database dump yields nothing
usable.

**Q: Why does a cookie-based refresh endpoint need CSRF protection when the API does not?**
**A:** Because the API authenticates with the `Authorization` header, which a browser
never attaches automatically — there is nothing for a cross-site page to ride on. The
refresh endpoint authenticates with a **cookie**, which the browser *does* attach
automatically to any request to your origin. A malicious page cannot read the cookie but
can cause the browser to send it. The defences are `SameSite=Strict`, a CSRF token, or
requiring a custom header that a cross-origin form cannot set.

**Q: What is the difference between absolute, sliding and idle expiry for a refresh token?**
**A:** **Absolute** is a hard ceiling from the issue date and is never extended — it
bounds the total exposure of a stolen token. **Sliding** extends the expiry on every
use, which is convenient but dangerous: a thief who uses the token keeps the victim's
session alive, so the user never notices. **Idle** expires the token after a period of
inactivity. Production combines them: `expires_at = min(now + idle_ttl,
family_absolute_expiry)` — so an active user is kept logged in, but the total exposure is
capped.

**Q: How would you implement "log out everywhere"?**
**A:** Two mechanisms. First, revoke **every** refresh-token row for the user — that
kills all future refreshes. Second, because the access tokens already issued are still
valid (they are stateless), bump a **per-user token version** stored in Redis and put
that counter in the access token as a `ver` claim; the resource server compares the
claim against the counter and rejects on a mismatch. Together they invalidate both the
long-lived and the short-lived credentials.

---

### 🔴 Advanced / Senior

reuse detection. The grace window needs the successor to be retrievable, which means
either storing it or keeping the family row's "current token" pointer. Without either
layer, legitimate concurrency logs the user out and revokes the family — which is one of
the most common self-inflicted bugs in refresh implementations.

**Q: Why store the refresh token hashed rather than as a JWT?**
**A:** Three reasons. **Revocation**: a JWT is self-verifying, so invalidating one
requires a denylist — server state on the refresh path, which defeats the purpose.
**Leakage**: an opaque token reveals nothing about the user if stolen; a JWT refresh
token exposes the subject, tenant and roles. **Size**: 43 characters versus ~600. The
one case where a JWT refresh token is right is a **stateless refresh service** that
must scale to millions of refreshes per second with no database — then you accept the
denylist and the leakage in exchange for the scale.

**Q: Where should the refresh-token store live — Postgres or Redis?**
**A:** Postgres by default. Refresh traffic is roughly `users / access_token_TTL` — with
100,000 daily actives and a 10-minute access token that is ~170 requests/second average,
which is nothing for a database. Redis is tempting but adds a dependency and a failure
mode to a path that does not need it, and it breaks the transaction with the user row.
Redis becomes right when the refresh volume is genuinely large, or when you already run
it and the refresh tokens are short-lived. The thing that actually needs attention in
Postgres is **table growth** — 4,320 rows per family over 30 days — which you solve
with pruning, partitioning, or by storing only the current token per family.

**Q: How do you prevent the refresh-token table from growing without bound?**
**A:** Four levers. **Prune** rows where `expires_at < now - 7 days` in a nightly job.
**Cap** the chain: store only the current token per family plus a generation counter, so
reuse detection becomes "is the presented generation older than the family's current
one" — one row per family. **Partition** by month so pruning is a `DROP PARTITION`
rather than a bloating `DELETE`. And **alert** on the rows-per-family distribution: a
family with thousands of links means either a long-lived session or someone deliberately
inflating your table.

**Q: How do you implement device/session management, and why does it come for free?**
**A:** It comes for free because every refresh token is already a row with `user_id`,
`device_info` and `ip`. "List my devices" is a `SELECT` of the active rows; "log out
this device" is an `UPDATE` scoped by `user_id` (an IDOR guard — return **404**, not
403, when it does not match); "log out everywhere" is a bulk `UPDATE` plus a token
version bump. The design decisions are what to show the user — device label, last-used
time, whether it is the current session — and the security decisions are what happens to
the **access** tokens, which the refresh revocation alone does not kill.

**Q: How do you detect a stolen refresh token?**
**A:** Three signals, in increasing order of cost. **Reuse detection** is the strong
one: a token presented twice means it was copied. **Anomaly detection**: the same token
family refreshing from two countries, or from a device fingerprint that changed without
a re-login, or at an IP that is on a known proxy list. **Notification**: tell the user
"a new device signed in", which costs nothing to implement and is the most effective
control of all, because the user sees the theft. The server-side response to a confirmed
theft is to revoke the family and require re-authentication with MFA.

---

### ⚫ Expert / Principal

country. The audit trail records every issuance, rotation, rejection and revocation with
the device and IP, retained for the regulatory period — which is also what makes
incident response possible at all.

**Q: What is the theoretical limit of refresh-token rotation, and how would you push it?**
**A:** Rotation bounds the *use* of a stolen token to one application, but it does not
bound the *lifetime* of the stolen credential's family, and it does not help if the
attacker rotates faster than you detect. The limit is that rotation is **detection-based**:
you find out when the *legitimate* client is disrupted. You can push it by (a) shortening
the access-token TTL so each rotation buys less, (b) adding **binding** — a
device-fingerprint-derived secret mixed into the token derivation, so a stolen token is
useless on a different device, (c) using **DPoP** (RFC 9449), where the token is bound to
a client-held key pair and the proof-of-possession signature is required on every
request, which turns a bearer token into a *proof-of-possession* token and closes the
XSS-theft problem entirely, and (d) using sender-constrained tokens in the OAuth2 sense.
DPoP is the real answer to "the token was stolen but the attacker has no key" — it is
however a significant client-side change and does not help against a compromised client
runtime.

**Q: How would you migrate an existing opaque-session system to the two-token model
without a big-bang cutover?**
**A:** **Dual-read, single-write.** Phase 1: the auth server issues the pair *and* keeps
the legacy session cookie alive, so both credential types work. Phase 2: migrate the
clients — web first (cookie), then mobile (body). Each client reports which credential
it is using, so the server knows the migration progress. Phase 3: instrument and watch
the legacy-session rate until it reaches a threshold you choose. Phase 4: stop *issuing*
legacy sessions; existing ones expire naturally over their maximum lifetime. Phase 5:
reject legacy sessions. The key design point is that the **refresh-token table is
populated on first use**, not at migration — so you avoid a backfill of millions of rows,
and the backfill cost is proportional to the number of *active* sessions rather than the
number of users. I would also introduce the token version at phase 1, because it changes
the access-token claim set and re-issuing those is the expensive part.

**Q: When would you choose a JWT refresh token over an opaque one, and what would you have to
build to make it safe?**
**A:** Only when the refresh endpoint has to scale to millions of calls per second with no
database — then a signed refresh token with a `family_id` and a `token_version` claim, plus a
small Redis denylist for revoked families, is a legitimate design. The price is three things you
must build: a **denylist** (because a self-verifying token cannot be revoked individually),
**leakage** (a stolen JWT refresh token hands over the subject, the tenant and the roles), and a
**larger token**. For everything below a few hundred refreshes per second, the database lookup
is cheap and the opaque token is strictly safer — it leaks nothing and it revokes in one
`DELETE`.

**Q: Why does the OAuth 2.0 Security BCP require refresh-token rotation, and what does it
say about the alternatives?**
**A:** RFC 9700 requires that refresh tokens be **bound to the client** and that
implementations either detect replay or use **sender-constrained** tokens. The reason is
that a refresh token is a long-lived bearer credential: once it leaks, an attacker has
30 days of access, and rotation without reuse detection does not help, because the
attacker can just keep using their copy and the server cannot tell. The BCP's
recommendation is rotation **with** reuse detection — on replay, revoke the entire token
family, because the legitimate client and the attacker are indistinguishable. The BCP
also explicitly warns about the **unbounded family growth** that rotation creates, which
is why the recommendation includes pruning and a maximum family size. The alternative it
blesses — **sender-constrained** tokens (mTLS or DPoP) — removes the bearer problem
entirely, at the cost of client complexity.

## 16. Scenario-Based & Real-Experience Scenarios

### Scenario 1 — "Users are being logged out every few minutes"

**The situation.** After the release of the refresh flow, support tickets arrive:
*"I get signed out every 2–5 minutes."* The login rate has tripled. No error in the
server logs; the 401s are "correct".

**Your first move.**

```bash
# 1. What is the ratio? A refresh bug shows up as an abnormal refresh/login ratio.
curl -s localhost:8080/actuator/metrics/auth.refresh.count | jq '.measurements'
curl -s localhost:8080/actuator/metrics/auth.login.count | jq '.measurements'

# 2. Are the reuse-detection events firing? (They should be ~0.)
grep -c "refresh_token_reuse" app.log

# 3. Is the client refreshing more than once per expiry?
grep -E "POST /api/auth/refresh" access.log | awk '{print $1}' | uniq -c | sort -rn | head
```

**Expected signal.** `auth.refresh.count` is **~3×** the expected rate, and the access
log shows **two or three** `POST /api/auth/refresh` calls with the **same session** within
a few seconds. The reuse counter is non-zero.

**The trap.** Assuming "the token TTL is too short" and raising it to an hour — which
makes the *exposure* worse and hides the bug.

**The resolution.** The client has **multiple components** refreshing independently. Two
tabs (or a React component that remounts) issue concurrent refreshes with the same
refresh token; the second one is flagged as reuse; the family is revoked; the user is
logged out; they log in again and the cycle repeats. The fix is a **client-side
single-flight lock** plus a **server-side grace window**. The follow-up is the metric:
`auth.refresh.reuse_detected` must be **zero** in steady state, and any non-zero value
pages someone.

**The lesson.** A "the user is logged out too often" symptom in a refresh system is
almost never a TTL problem — it is a **concurrency** problem, and the fix is a lock, not
a longer TTL.

---

### Scenario 2 — "The refresh token is rejected after yesterday's deploy"

**The situation.** Since the 14:00 deploy yesterday, **every** refresh returns **401**.
Logins work fine; only `/refresh` fails. The error is `InvalidRefreshTokenException`.

**Your first move.**

```bash
# 1. Is the raw token still in the database under its hash?
#    (careful — do NOT log the raw token)
grep -E "REFRESH_(ISSUE|ROTATE)" app.log | tail -5

# 2. Did the hashing change between the releases?
git diff HEAD~1 HEAD -- src/main/java | grep -A5 -i "hash\|sha\|digest"

# 3. Was the token-hash column changed (e.g. a new column, a new length)?
git diff HEAD~1 HEAD -- '**/migration/**' '**/V*__*.sql'

# 4. Is the secret/pepper changing?
grep -rn "pepper\|HMAC\|SecretKey" src/main/java/ | head
```

**Expected signal.** The diff shows a new line in `TokenHasher`:

```java
-    return sha256Hex(raw);
+    return hmacSha256Hex(raw, pepper);
```

Someone added an **HMAC pepper** for defence in depth. That is not inherently wrong — but
the **pepper changed** in the deploy (it was read from a config value that was rotated),
so every stored hash no longer matches.

**The trap.** "We cannot recover the tokens, so force everyone to log in again." That is
the *symptom-level* fix and it is the wrong one — you have just logged out every user
of a 2M-user platform because of a config mistake.

**The resolution.** Two options, in order of preference:
(a) **Roll back the pepper change** — it is a recent, unverified change and the blast
radius of the rollback is zero.
(b) If the pepper must change, run a **dual-read migration**: hash with the old pepper
and the new pepper and accept either, then re-hash lazily on the next successful
refresh. That is why the lookup should store enough to try both during a migration
window.

**The lesson.** Any change to a *derivation* is a **data migration**, not a code change.
Peppers, salts, KDF parameters and hash algorithms all have that property, and they need
a dual-read window. And the "log everyone out" fix is a last resort, not a first move.

---

### Scenario 3 — "A user reports a session they don't recognise"

**The situation.** A user emails: *"There's a device in the security list I don't
recognise — 'Chrome 133 / Windows 10'."* They last logged in from their Mac. The
refresh-token table has two active rows for them.

**Your first move.**

```sql
-- 1. Which sessions are active, when were they created, and from where?
SELECT id, device_info, ip, issued_at, expires_at, last_used_at, family_id
FROM refresh_token
WHERE user_id = 42957 AND revoked_at IS NULL AND used_at IS NULL
ORDER BY issued_at DESC;
```

**Expected signal.** The unfamiliar session was created **14 hours ago** from an IP
belonging to a **different country**, and the `device_info` shows a **Windows** user agent
while the user is on a Mac. The unfamiliar session's `family_id` has been **rotated
several times** (many `used_at` rows) — the attacker is actively using it.

**The trap.** Deleting the unfamiliar row and moving on. The attacker's access token may
still be live, and the attacker still holds a valid refresh token in a *different* family
if they created one.

**The resolution.**
1. **Revoke the unfamiliar family** — and every family for that user.
2. **Bump the token version** so all outstanding access tokens die immediately.
3. **Force re-authentication with MFA** for the next login.
4. **Check the audit trail** for the logins: when, from where, with what MFA state.
5. **Notify the user** — and consider whether the account needs a temporary lock.

**The lesson.** The device list is not a cosmetic feature — it is the **detection and
containment** surface for account compromise. And revoking a refresh token alone is not
enough: the access token lives until it expires unless you also bump the token version.

---

## 17. Counter-Questions, Tricky Questions & Follow-Ups

### CQ1. "Why not just make the access token last 30 days and skip refresh tokens entirely?"

- **Naive answer:** "Because that's less secure."  ← *true, but it misses the mechanism*
- **The trap:** The trap is treating the TTL as a magic security dial. The real reason
  refresh tokens exist is that they let you **decouple the *authority* from the
  *ability to renew***. A long-lived token is a long-lived *grant*; a long-lived refresh
  token is a long-lived *ability to get a new grant*, and that ability can be revoked
  instantly, audited, and bound to a device.
- **Expert answer:** The refresh token is a **revocable root of trust**; the access token
  is a **cached, short-lived derivative** of it. Because the refresh token lives in a
  server-side store, you can revoke it in one `UPDATE` — instantly, globally, with an
  audit trail. A 30-day access token has no such lever: it is valid everywhere until it
  expires, and if it leaks, every copy of it is live for 30 days. So the split is not
  about "short tokens are nicer" — it is about **where the revocable power lives**.

### CQ2. "You said sliding expiry is dangerous. But isn't that what 'remember me' means?"

- **Naive answer:** "No, 'remember me' is a long absolute expiry."  ← *true, and it misses the third option*
- **The trap:** The trap is thinking there are only two designs. There are three
  dimensions — absolute, sliding, idle — and the safest combination is **absolute + idle**.
  Pure sliding is the one to avoid.
- **Expert answer:** "Remember me" is best modelled as a **long absolute lifetime** (30
  days) with a **short idle timeout** (7 days) — the user's intent is "keep me signed in
  for a month, as long as I am actually using it". Pure sliding would give a thief who
uses the token once a week an **indefinite** credential, and — crucially — the thief's
usage would keep the victim's session alive, so the victim would never see an anomaly.
The combination `expires_at = min(now + idle_ttl, family_absolute_expiry)` gives the
UX people expect without ever removing the ceiling.

### CQ3. "If the refresh token is hashed with SHA-256, why not just use SHA-256 for
passwords too? It's faster."

- **Naive answer:** "Because SHA-256 is too fast for passwords."  ← *true, but incomplete*
- **The trap:** The trap is stopping at "fast = bad". The precise principle is that
  **the work factor must be calibrated to the attacker's *expected* effort**, and that
  expected effort is a function of **entropy**, not of the data type. A 256-bit random
  token has ~2^256 possible values — no attacker will ever brute-force it, so the KDF's
  cost buys nothing and only adds latency. A human-chosen password has maybe 2^20
  effective bits — so a fast hash lets the attacker try billions of candidates.
- **Expert answer:** The correct framing is **entropy-matched work factors**. SHA-256 is
  appropriate for high-entropy random secrets (session ids, API keys, refresh tokens);
  bcrypt/Argon2id is appropriate for low-entropy human secrets (passwords). The
  consequences differ too: for a refresh token, a stolen *hash* is useless, so the
  defence is the entropy; for a password, the hash is the target, so the defence is the
  work factor plus salting (which prevents precomputation across users). If you use
  SHA-256 for passwords, a single GPU can try ~10^10 candidates/second; with Argon2id
  tuned to ~100 ms you get ~10 per second per core — a difference of a billion.

### CQ4. "Why does reuse detection revoke the whole family instead of just the reused token?"

- **Naive answer:** "Because the token is compromised."  ← *true, and it misses the reasoning*
- **The trap:** The trap is thinking the *reused* token is the only compromised artifact.
  It is not: the **whole family** is suspect, because the attacker may hold a *later*
  token in the chain, not the one they replayed. Revoking only the replayed token would
  let the attacker continue with the successor they already obtained.
- **Expert answer:** Reuse detection means the attacker and the legitimate client both
  hold a copy of some token. We cannot tell who is who. The family is the **unit of
  compromise**: revoking the entire family forces **both** parties to re-authenticate,
  which evicts the attacker and costs the victim one login. Revoking only the replayed
  token would be *worse than useless* — it would remove the evidence trail (the family
  continues happily) while giving the attacker a fresh successor if they had already
rotated past the token they replayed. The family is also the natural **audit unit**:
"family F1 was compromised" is a meaningful statement; "token 1041 was replayed" is
not.

### CQ5. "Why not return the refresh token in the JSON body and store it in memory on the
client?"

- **Naive answer:** "It's simpler and avoids CSRF."  ← *true, and it is right for native apps*
- **The trap:** The trap is applying the native-app pattern to the browser. In a native
  app, the OS keychain protects the token and there is no ambient browser behaviour, so
  the body is correct. In a browser, "store it in memory" only protects against
  `localStorage` theft — it does **nothing** against XSS that runs while the app is
open, which is exactly when XSS runs.
- **Expert answer:** The threat model for a browser is different: the attacker's script
**executes in your page** and can call your API, read your DOM, and — if the token is
reachable — exfiltrate it. An `HttpOnly` cookie is **not reachable** by JavaScript at
all, which is the only thing that closes the gap. The CSRF cost is a header you must
send anyway (`Content-Type: application/json` already forces a CORS preflight for a
cross-origin request, which a cross-origin *form* cannot pass). So: cookie + CSRF
protection for browsers; body + keychain for native. The decision is **not** "which is
simpler" — it is "who can read the token where it lives".

### CQ6. "Why not use the access token as the refresh token?"

- **Naive answer:** "Because the access token expires quickly."  ← *true, but shallow*
- **The trap:** The trap is missing the **privilege** dimension. The access token
carries the user's **authorisation claims** and is accepted by the API. The refresh
token carries **renewal** power only. Using one for the other means a credential that
the API accepts (and that lands in every access log) also has the power to mint new
credentials.
- **Expert answer:** Two separate reasons, and the second is the dangerous one. First,
lifetime: the access token's 5–15-minute TTL is far too short for a session. Second —
and this is the one that matters — **capability separation**. The refresh token is
accepted by exactly one endpoint and can only mint access tokens; the access token is
accepted by business endpoints and can do business things. Mixing them means a
credential that appears in every access log, in every APM trace and in every proxy log
also has renewal power. Capability separation is a general principle: give each
credential the **minimum** authority it needs, and keep the two lifetimes independent so
that one can be tuned without affecting the other.

### CQ7. "How do you know your refresh-token rotation isn't creating false positives?"

- **Naive answer:** "By the number of reuse-detection events."  ← *true, but it is a lagging indicator*
- **The trap:** The trap is trusting the counter alone. In a low-traffic system, a bug
that triggers reuse detection twice a week looks like "zero" on a dashboard. You need
a **rate** and a **context**, not a count.
- **Expert answer:** I track `auth.refresh.reuse_detected` as a **ratio** to
`auth.refresh.count`, and I alert when it exceeds ~0.1% — because the expected steady
state is exactly zero, and any non-zero value should be investigated once. Crucially,
I also correlate the event with the **device and IP** of the two presentations: if the
same device and IP present the same token twice within a few seconds, it is a **client
concurrency bug**; if the second presentation comes from a different IP, it is a **real
compromise**. That single distinction turns a vague alert into an actionable one, and
it is why the audit event carries the device and IP rather than just the user id.

### CQ8. "What if the refresh token database is down? Can you fall back to something else?"

- **Naive answer:** "No, we return 500 and the user has to log in again."  ← *true, and it misses the availability gradient*
- **The trap:** The trap is treating the refresh path as all-or-nothing. There is a
graceful middle: the **access token is still valid** for up to its TTL, so the API keeps
working — only the *renewal* fails.
- **Expert answer:** The impact depends on the token lifetimes. With a 10-minute access
token, a refresh-store outage means **users are logged out after at most 10 minutes**,
and the API itself keeps serving — the users who already hold a valid access token are
fine. With a 24-hour access token, the same outage is a 24-hour outage. So the
availability of the refresh store is **directly multiplied by the access-token TTL**,
and that is the number to reason about in a design review. The mitigations are: run the
refresh store with replicas and a failover, make the refresh endpoint return **503** (not
500 — it is "temporarily unavailable" per the canonical status table) so clients back off
instead of retrying hard, and keep the client backoff **exponential with jitter** so a
store recovery does not produce a thundering herd.

## 18. Frequently Asked Production Issues & Debugging Scenarios

### Issue 1 — Users are logged out every few minutes

**Symptom.** Support tickets: *"logged out every 2–5 minutes."* The login rate is 3×
normal. The 401s are legitimate — the access token really did expire.

**Diagnostic commands.**

```bash
# 1. Is the refresh rate abnormal?
curl -s localhost:8080/actuator/metrics/auth.refresh.count | jq '.measurements'
curl -s localhost:8080/actuator/metrics/auth.refresh.reuse_detected | jq '.measurements'

# 2. Are there concurrent refreshes from the same session?
grep "POST /api/auth/refresh" access.log \
  | awk '{print $6}' | sort | uniq -c | sort -rn | head -20

# 3. Do the reuse events come from the same device as the successful rotations?
grep "refresh_token_reuse" app.log | tail -20 | awk '{print $NF}'
```

**Expected signal.** The access log shows **two or three** refresh calls within a
second for the same session, and the reuse-detection events carry the **same device and
IP** as the successful rotations. That combination identifies a **client concurrency
bug**, not a compromise.

**The fix.** A client-side single-flight lock (one refresh, many waiters) plus a
server-side **grace window** of ~10 seconds on `used_at`. Add the
`auth.refresh.reuse_detected` **ratio** to the dashboard and alert above 0.1%.

---

### Issue 2 — The refresh token is rejected after a deploy

**Symptom.** Since the deploy, every `/refresh` returns **401** with
`InvalidRefreshTokenException`. Logins still work.

**Diagnostic commands.**

```bash
# 1. Did the hashing change?
git diff HEAD~1 HEAD -- src/main/java | grep -B3 -A8 -i "sha\|hash\|digest\|pepper"

# 2. Do the stored hashes match what we compute now?
#    (compare a hash prefix from the DB with a locally computed one)
psql -d app -c "SELECT left(token_hash,16) FROM refresh_token WHERE user_id=42957 ORDER BY issued_at DESC LIMIT 1;"

# 3. Was the pepper/secret rotated?
git diff HEAD~1 HEAD -- src/main/resources/ | grep -i "pepper\|secret"

# 4. Did the column type change (e.g. CHAR(64) -> VARCHAR(64) with padding)?
git diff HEAD~1 HEAD -- '**/db/migration/**'
```

**Expected signal.** The diff shows the hash function or the **pepper** changed. The
stored hashes were computed with the old derivation, so every lookup misses.

**The fix.** **Roll back** the derivation change if it was unverified, or run a
**dual-read** migration (accept the old and new derivation during a window, then
re-hash lazily on the next successful refresh). A derivation change is a **data
migration**, not a code change — and "log everyone out" is the last resort, not the
first.

---

### Issue 3 — The cookie is not sent by the browser

**Symptom.** The login works, the cookie is in the browser's storage, but `/refresh`
returns **401** with "no refresh token presented".

**Diagnostic commands.**

```javascript
// 1. Inspect the cookie in the browser console:
document.cookie
//    → "" if HttpOnly is set (that is EXPECTED — the console cannot see HttpOnly cookies)

// 2. Check it in the Application tab: Application → Cookies → your-origin → rt
//    Look at: Domain, Path, Secure, SameSite, Expires

// 3. Check what the server actually received:
curl -v -X POST https://app.acme.com/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -b 'rt=<value>' -d '{}'
```

**Expected signal.** The cookie has **`Path=/api`** (or `/`) while the request goes to
`/api/auth/refresh` — that matches, so this is not it. The real finding: **`SameSite=None`
without `Secure`**, or the site is served over **HTTP** in the dev environment and
`Secure` cookies are silently dropped. Or: the cookie's `Domain` is `app.acme.com` while
the API is on `api.acme.com` — a **cross-subdomain** mismatch, so the browser never
sends it.

**The fix.** Align `Domain` with the API host (or make the API same-origin via a reverse
proxy — the usual production choice), ensure `Secure`, and use `SameSite=Strict` for a
same-site API. Remember: **`SameSite=None` requires `Secure`**, and browsers reject the
cookie otherwise.

---

## 19. Architecture Design Questions

### 19.1 Design the token lifecycle for a browser SPA + mobile app with device management

**The prompt.** "We have a React SPA and an iOS/Android app. Users must be able to see
their devices and kill sessions. We are a mid-size company — 200k MAU, 12 engineers, no
platform team."

**Reference design.**

```text
                    ┌──────────────────────────────────────────────┐
                    │             AUTH SERVICE (Boot 3)             │
   SPA (browser)    │  POST /api/auth/login                       │
   ────────────────▶│  POST /api/auth/refresh  (cookie OR body)    │
                    │  POST /api/auth/logout                      │
                    │  GET/DELETE /api/auth/sessions              │
                    │  POST /api/auth/logout-all                  │
                    └───────────────┬──────────────────────────────┘
                                    │
              ┌─────────────────────┼──────────────────────┐
              ▼                     ▼                      ▼
      refresh_token (Postgres)   access token (JWT, RS256)  Redis
      · token_hash UNIQUE        · 10 min TTL              · token version
      · family_id INDEX           · ver claim                  (per user)
      · used_at / revoked_at     · aud = service list       · rate-limit
      · device_info / ip         · roles claim              · (optional)
      └────────────────────────────────────────────────────────┘

   BROWSER                            NATIVE APP
   ────────                           ─────────
   refresh token: HttpOnly cookie     refresh token: response body
   access token:  in-memory only      access token: in-memory
   CSRF: on for /api/auth/*           CSRF: N/A (not a browser)
   refresh: single-flight lock        refresh: single-flight lock
```

**The decisions and their reasons.**

1. **Postgres for refresh tokens, not Redis** — 200k MAU × 144 refreshes/day = 29M
   refreshes/month ≈ **34/s average**, ~170/s peak. Trivial for Postgres, and it keeps
   one fewer dependency for a 12-person team.
2. **RS256 access tokens** with a JWKS endpoint, because the mobile app and any future
   partner need to verify without a shared secret.
3. **A token version in Redis** for "log out everywhere" — O(1) and independent of the
   refresh-token table.
4. **The refresh token in a cookie for the browser, in the body for native** — the
   endpoint accepts **both**, chosen by which is present.
5. **A nightly pruning job** deleting rows older than 7 days.
6. **A family row cap of 200** — if a family exceeds it, revoke the family (this is
   both a table-growth defence and an attack signal).
7. **Rate limits**: 20/min per IP and 10/min per account on `/refresh`.

**The trade-offs the interviewer wants to hear.**

| Decision | Gain | Cost |
|---|---|---|
| Postgres over Redis | One fewer dependency, transactional with the user row | Slightly higher latency; needs pruning |
| One endpoint for cookie and body | One code path, one audit trail | A body-borne token must never be logged |
| In-memory access token (SPA) | XSS cannot steal it from storage | A page reload loses it → refresh on load |
| Token version in Redis | Instant "log out everywhere" | One extra hop on the hot path |
| Family cap of 200 | Bounds the table | A very long-lived session gets logged out |

---

### 19.2 Design the refresh mechanism for a platform with heterogeneous clients

**The prompt.** "We serve a browser SPA, an iOS app, an Android app, a partner's
server-to-server integration, and a CLI tool. All need long-lived sessions. Design the
token system."

**Reference design.**

| Client | Access token transport | Refresh token transport | Refresh trigger | Notes |
|---|---|---|---|---|
| **Browser SPA** | `Authorization` header, in-memory | `HttpOnly` cookie (`SameSite=Strict`) | On 401 → refresh → retry once | CSRF-protected; `Path=/api/auth` |
| **iOS / Android** | `Authorization` header, in-memory | **Body** + Keychain / EncryptedSharedPreferences | On 401 → refresh → retry once | No cookie; no CSRF |
| **Partner server-to-server** | `Authorization` header | **Body**, stored encrypted at rest | Proactive, at 80% of TTL | Client-credentials-like; `aud` = partner's own API |
| **CLI tool** | `Authorization` header | **Body**, in an OS keychain / `~/.config` with 0600 | Proactive | Machine identity; `sub` = the service account |

**The shared pieces.**

- One `/refresh` endpoint that accepts the token from **either** the cookie **or** the
  body, with the source recorded in the audit log.
- One token **format** for all clients (opaque, hashed server-side) — the client type is
  metadata, not a different credential.
- One **rotation + reuse-detection** state machine, shared by all clients.
- **Different rate limits** per client type: the browser SPA is the most tolerant (many
  tabs), a partner's server is the most restricted (a high rate looks like a bug).

**The key design decision — how the server knows it is talking to which client.**

The server does **not** need to know, and that is the point. It stores the `device_info`
and the client type is inferred from the **transport** (cookie ⇒ browser; body ⇒
native/CLI). But where it *does* matter is **policy**:

1. **Binding**: a partner's server-to-server token is bound to the **client id** and can
   only request scopes it was granted; a browser token cannot request `admin:write`.
2. **Step-up**: a browser token that wants to move money requires a fresh MFA within the
   last 5 minutes (an `auth_time` claim).
3. **Absolute lifetime**: a partner's token gets a **30-day absolute** ceiling; a browser
   token on a shared device gets **2 hours**.

**The trade-offs.**

| Decision | Gain | Cost |
|---|---|---|
| One endpoint, two transports | One code path, one audit trail | A body-borne token must be redacted everywhere |
| Per-client-type policy | Correct security posture per client | Policy configuration becomes a matrix |
| No client-type claim in the token | Cannot be spoofed | The server infers from the transport — slightly fragile |
| Partner tokens with a longer ceiling | Fewer refreshes for unattended clients | A stolen partner token is worth more |

**What I would say at the end.** "The transport is a *client* concern; the token is a
*server* concern; the *policy* is where they meet. I would never encode the client type
in the token as a security control, because the token's contents are only as trustworthy
as the issuer — but I would record the transport in the audit log, because that is what
tells me whether a reuse-detection event is a browser race or a real compromise."

---

## 20. Rapid-Fire MCQs

**1.** What is the typical access-token lifetime?
- A) 5–15 minutes
- B) 1 hour
- C) 24 hours
- D) 7 days

**2.** Where must the refresh token be stored server-side?
- A) In plaintext, so it can be compared
- B) **Hashed**
- C) Encrypted with the access token's key
- D) In a JWT claim

**3.** How many endpoints should accept a refresh token?
- A) All `/api/**` endpoints
- B) Every authenticated endpoint
- C) **Exactly one (`/refresh`)**
- D) The login and refresh endpoints

**4.** What does "refresh-token rotation" mean?
- A) The refresh token's TTL is extended on use
- B) **Every use issues a new refresh token and invalidates the presented one**
- C) The access token is rotated
- D) The signing key is rotated

**5.** What does reuse detection do?
- A) Returns 401 and nothing else
- B) **Revokes the entire token family and logs a security event**
- C) Extends the refresh token's expiry
- D) Deletes the user's account

**6.** Why is SHA-256 acceptable for hashing a refresh token but not for a password?
- A) SHA-256 is stronger than bcrypt
- B) **Because a refresh token has 256 bits of entropy, so there is no dictionary to attack; a password does**
- C) SHA-256 is faster
- D) bcrypt cannot be used in Java

**7.** Which cookie attribute stops JavaScript from reading the refresh token?
- A) `Secure`
- B) `SameSite`
- C) **`HttpOnly`**
- D) `Path`

**8.** Which attribute stops the browser sending the cookie cross-site (CSRF)?
- A) `HttpOnly`
- B) `Secure`
- C) `Path`
- D) **`SameSite`**

**9.** Where is the `refresh_token` cookie's raw value normally kept in a browser SPA?
- A) `localStorage`
- B) `sessionStorage`
- C) A JS variable in memory
- D) **In an `HttpOnly` cookie the JavaScript cannot read**

**10.** In the browser, what is the most secure place to keep the **access** token?
- A) `localStorage`
- B) A JS variable in memory (not in web storage)
- C) In a URL query parameter
- D) In a `<meta>` tag

**11.** What happens when a refresh token that has already been used is presented again?
- A) It is accepted and a new token is issued
- B) It returns 200 with the same token
- C) **Reuse is detected, the family is revoked and 401 is returned**
- D) The user's password is reset

**12.** Which expiry model is the most dangerous on its own?
- A) Absolute
- B) **Sliding (a stolen token stays alive indefinitely if used)**
- C) Idle
- D) Absolute + idle

**13.** "Log out everywhere" additionally requires bumping the **token version**, because…
- A) The refresh tokens cannot be deleted fast enough
- B) **Already-issued access tokens remain valid until they expire**
- C) The signing key must change
- D) Redis needs to be flushed

### Answer Key

| # | Answer | One-line reason |
|---|--------|-----------------|
| 1 | **A** | 5–15 min is the standard — the TTL is the revocation window. |
| 2 | **B** | Only the hash is stored; a dump then yields nothing usable. |
| 3 | **C** | One endpoint — otherwise it becomes a 30-day API credential. |
| 4 | **B** | Rotation bounds the *use* of a stolen token to one application. |
| 5 | **B** | You cannot tell the thief from the user, so you evict both. |
| 6 | **B** | Entropy-matched work factors — a fast hash is fine for random secrets. |
| 7 | **C** | `HttpOnly` removes the token from the JS-visible surface entirely. |
| 8 | **D** | `SameSite` stops the browser attaching it to cross-site requests. |
| 9 | **D** | The browser sends it; JavaScript cannot read it. |
| 10 | **B** | In-memory keeps it out of `localStorage` and out of the DOM. |
| 11 | **C** | Two parties hold the token ⇒ compromise ⇒ evict both. |
| 12 | **B** | A thief's usage keeps the victim's session alive — no anomaly is visible. |
| 13 | **B** | A stateless access token stays valid until `exp`; revocation needs state. |

> 📌 **The three to re-read:** **Q6** (entropy vs work factor), **Q12** (why sliding is
dangerous) and **Q13** (rotation without pruning is a write-amplification bug).

---

## 21. Cheat Sheet / One-Page Summary

| Area | Fact |
|---|---|
| **Access token** | 5–15 min · stateless · `Authorization: Bearer <jwt>` · **not** revocable |
| **Refresh token** | 7–30 days · **hashed** server-side · `HttpOnly; Secure; SameSite=Strict` cookie · **revocable** |
| **Storage** | `SHA-256(raw)` in a `CHAR(64)` column, **unique index** |
| **Why SHA-256** | 256 bits of entropy ⇒ no dictionary to attack ⇒ a slow KDF buys nothing |
| **Rotation** | Every use → a NEW token; the old one is marked `used_at` + `replaced_by_id` |
| **Reuse detection** | A used token presented again ⇒ revoke the **whole family** + audit |
| **Family** | `family_id` = one login session's rotation chain = the unit of compromise |
| **Expiry models** | Absolute ✅ · Sliding ❌ (a stolen token lives forever) · Idle ✅ |
| **Transport (browser)** | `HttpOnly; Secure; SameSite=Strict; Path=/api/auth` |
| **CSRF** | Required on the cookie-based `/refresh`; not needed on the bearer API |
| **Client behaviour** | 401 → refresh **once** → retry **once** → single-flight lock |
| **Logout** | Revoke the refresh token **+** bump the token version |
| **"Log out everywhere"** | Revoke every family **+** increment the user's token version |

---

## 22. Real-World Production Notes

| Knob / Behaviour | Default | When to change it |
|---|---|---|
| Access-token TTL | 10 min | 5 min for high-value APIs; 15 min for low-privilege high-volume |
| Refresh-token absolute TTL | 30 days | 7 days for regulated apps; 90 days only for low-risk consumer |
| Refresh-token idle TTL | 7 days | 2 hours for shared devices; 1 day for corporate |
| Refresh token entropy | 256 bits | Never lower. 128 bits is acceptable but 256 is free |
| Refresh token length | 43 chars (base64url of 32 bytes) | — |
| Hash | SHA-256 hex (`CHAR(64)`) | SHA-512 if you ever want to move to a longer token |
| Rotation | On every use | Always — it is the whole point |
| Reuse grace window | 0 s (or 10 s with a client lock) | 10 s if you cannot control every client |
| Family row cap | 200 | Lower for high-security apps; revoke the family when exceeded |
| Cookie `Path` | `/api/auth` | `/` if you need the cookie elsewhere — but then it rides on every request |
| `SameSite` | `Strict` | `None` + `Secure` only for a genuinely cross-site API (and then you need CSRF tokens) |
| CSRF on `/api/auth/*` | Enabled | Never disable it globally when the refresh token is a cookie |
| Pruning window | 7 days after expiry | Shorter if the table is large; run it nightly |
| Rate limit `/refresh` | 20/min per IP, 10/min per account | Stricter for partner/CLI clients |
| Access token storage (SPA) | In-memory | `localStorage` only as an absolute last resort |
| Refresh request retries | 1, with a 60 s margin | Proactive refresh at 80% of the TTL reduces 401s |

**Metrics worth having on a dashboard.**

| Metric | Why |
|---|---|
| `auth.refresh.count` by outcome | The primary flow health signal |
| `auth.refresh.reuse_detected` | Must be ≈ 0 — a **ratio**, not a count, is the alert |
| `auth.refresh.duration` (p99) | Detects index and pool problems |
| `auth.login.count` / `auth.refresh.count` | A rising ratio = a refresh bug (users re-logging in) |
| `refresh_token.rows_per_family` (p99) | Detects unbounded chain growth |
| `refresh_token.table_size` | Storage growth |
| `refresh_token.pruned.count` | Proves the pruning job runs |
| `auth.refresh.client_type` | Browser vs native — separates concurrency bugs from attacks |

---

## 23. Follow-up Learning

**Next in this folder**

- → [03 · Token Revocation, Storage & Production Design](./03-Token-Revocation-Storage-and-Production-Design.md) —
  the six revocation strategies, the token-version claim, denylist TTL maths, the
  key-rotation runbook.
- → [04 · Security Risks, Complete Flows & Real Examples](./04-Security-Risks-Complete-Flows-and-Real-Examples.md) —
  the 11 attacks, the complete login/refresh/logout flows, the hardening checklist.

**Related folders**

- [05 · Spring Security](../05-Spring-Security/02-Authentication-Flow-UserDetailsService-and-Providers.md) — password encoding, CSRF,
  CORS, sessions and the `SecurityContext`.
- [07 · OAuth2 & OpenID Connect](../07-OAuth2-OpenID-Connect/03-Client-Credentials-Refresh-Tokens-and-OpenID-Connect.md) — the
  authorization code + PKCE flow, refresh-token rotation in an OAuth2 context, DPoP and
  sender-constrained tokens.

**Source pointers (RFCs and Spring Security 6.1)**

| Document | Why read it |
|---|---|
| RFC 9700 — OAuth 2.0 Security BCP | **The** normative source on refresh-token rotation and reuse detection |
| RFC 6749 §6 — Refresh Token | The OAuth2 refresh-token grant |
| RFC 6750 — Bearer Token Usage | The `Authorization` header and `WWW-Authenticate` |
| RFC 7636 — Proof Key for Code Exchange | Why native clients use PKCE |
| RFC 9449 — DPoP | Sender-constrained tokens — the strongest answer to token theft |
| Spring Security `ResponseCookie` | Building the `Set-Cookie` header |
| Spring Security `CookieCsrfTokenRepository` | CSRF for the cookie-based refresh endpoint |
| Spring Security `SessionCreationPolicy.STATELESS` | Why a JWT API should not create a session |
| Spring Data JPA `@Modifying` | The bulk family-revocation query |

---

**Next Chapter:** → [03 · Token Revocation, Storage & Production Design](./03-Token-Revocation-Storage-and-Production-Design.md)
**Previous Chapter:** ← [01 · JWT Architecture, Statelessness & Signature Algorithms](./01-JWT-Architecture-Statelessness-and-Signature-Algorithms.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
