# Security Risks, Complete Flows & Real Examples — The Complete Interview Guide

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★★ (this is the chapter that turns "I have used JWTs" into "I can defend a JWT design")
> **Prerequisites:** [01 · JWT Architecture](./01-JWT-Architecture-Statelessness-and-Signature-Algorithms.md) · [02 · Access & Refresh Tokens](./02-Access-Refresh-Tokens-and-Token-Rotation.md) · [03 · Revocation](./03-Token-Revocation-Storage-and-Production-Design.md)
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

- **The whole JWT attack surface reduces to four questions:** *who signed this? is it for me? is it still valid? and what does it let me do?* Every attack below is a failure to answer one of them.
- **Algorithm confusion** is the headline attack, and its defence is one line: **never read `alg` from the token — pin it server-side.** The same pinning kills `alg: none`.
- **`exp` must be mandatory.** A token with no `exp` (or a year-future `exp`) is accepted forever, and no amount of key strength fixes that.
- **The payload is public.** Tokens leak through access logs, browser history, referrer headers, APM traces and `localStorage`. **Never put PII in a JWT** and **never put a token in a URL**.
- **The claim→authority mapping is a privilege boundary.** Mapping *every* claim to an authority means any claim the issuer names badly — or that an attacker can influence — becomes a privilege. Map **explicitly, by name**.
- **Validate `aud`.** A token minted for `billing-api` must not authenticate a call to `orders-api`. That is the **confused deputy** attack, and it is the most common real-world JWT bug in microservices.
- **A stateless JWT permits replay within its TTL.** The mitigations are a short TTL, a nonce/idempotency key for high-value operations, and DPoP for the strongest case — not a `jti` blacklist in the hot path.
- **The complete flow set is four:** login, refresh, logout, and service-to-service. The service-to-service flow is where the `act` (actor) claim and token exchange come in, and it is where "impersonation" bugs live.
- **Three real designs worth memorising:** a **banking** design (5-minute tokens, KMS keys, step-up re-authentication, `aud` per service, token version, a full audit trail), a **multi-tenant SaaS** design (per-tenant `kid`, tenant from the *validated* claim, per-tenant revocation), and a **service mesh** design (5-minute tokens, `aud` = target service, service-to-service grants, and the introspection trade-off).
- **The hardening checklist has 25 items.** If you can recite ten of them from memory, you will pass the security round of any interview.

---

## 1. What & Why — The Problem This Solves

**The problem.** A JWT is a **self-asserted credential**. The bearer hands you a blob
and asserts "the issuer said this is true." The entire security model of your API is
the set of checks you perform on that blob. Get one wrong and the attacker walks in
without ever breaking the cryptography.

```text
   THE TRUST BOUNDARY, IN ONE LINE
   ─────────────────────────────
   "The client says: I am user 42957, I act for tenant t_9931, I may do PAYMENT_INITIATE."
   "Your job: decide whether to BELIEVE that sentence."

   You are not protecting the token. You are protecting the DECISION.
```

**Why this is different from a session.** With a session, the client says *"my session
id is X"* and the server looks up what X means. The client is not asserting any
**facts** — it is asserting only **identity**, and the server supplies the facts. With a
JWT, the client asserts the **facts** too, and your job is to decide which of them to
believe. The attack surface is therefore **larger**: not just "is this really user
42957?" but "is it *really* user 42957, *really* for this service, *really* still
valid, and *really* allowed to do this?"

**The solution — a validation pipeline with a fixed order.**

```text
   ① IS IT GENUINE?      → signature verification (pinned algorithm, trusted key)
   ② IS IT FOR ME?       → iss + aud + typ
   ③ IS IT STILL VALID?  → exp + nbf (+ revocation state)
   ④ WHAT MAY IT DO?     → an explicit claim → authority mapping
   ⑤ WHO IS BEHIND IT?   → sub, tenant, act — all from the VALIDATED claims

   ⚠ THE ORDER IS THE DESIGN. Each step assumes the previous one passed.
```

**The analogy — the passport control desk, again, but with the attack list.** A border
post checks a passport in a fixed order: (1) is the document physically genuine — the
photo matches, the laminate is right; (2) is it **for this country** — is the visa
stamped; (3) is it **current** — has it expired; (4) what does it **permit** — the
visa class. Every fraud attempt against a border post is a failure at one of those four
steps, and each one has a name: **forgery**, **wrong document**, **expired**, and
**wrong permission**. The JWT attacks map exactly onto them.

**What breaks without this discipline?** In production, the same four bugs account for
almost every JWT incident: the algorithm was read from the token; the audience was not
checked; `exp` was optional; and every claim became an authority.

> 💡 **The interview framing.** "A JWT is not a magic credential — it is a **set of
> assertions from an untrusted party**, and your validation pipeline is what converts
> those assertions into authorisation. Every attack I know of against JWT is a bug in
> that pipeline, not a break in the cryptography."

---

## 2. Mental Model — The One Picture

```text
   THE FIVE GATES — AND WHICH ATTACK GETS THROUGH EACH
   ═══════════════════════════════════════════════════

   ┌──────────────────────────────────────────────────────────────────┐
   │ ① GENUINE?          signature verifies with the PINNED alg      │
   │    ⚠ algorithm confusion, alg:none  →  BLOCKED HERE             │
   ├──────────────────────────────────────────────────────────────────┤
   │ ② FOR ME?           iss == expected, aud contains me,           │
   │                      typ == at+jwt                               │
   │    ⚠ confused deputy, wrong-issuer token  →  BLOCKED HERE       │
   ├──────────────────────────────────────────────────────────────────┤
   │ ③ STILL VALID?      exp present & > now (60 s leeway),          │
   │                      nbf <= now, not revoked                    │
   │    ⚠ no-exp token, replay  →  BLOCKED HERE                      │
   ├──────────────────────────────────────────────────────────────────┤
   │ ④ WHAT MAY IT DO?   EXPLICIT claim → authority mapping          │
   │    ⚠ mass assignment / claim escalation  →  BLOCKED HERE        │
   ├──────────────────────────────────────────────────────────────────┤
   │ ⑤ WHO IS BEHIND IT? sub, tenant_id, act — from the VALIDATED    │
   │                      claims only, never from a request header   │
   │    ⚠ header spoofing, tenant spoofing  →  BLOCKED HERE         │
   └──────────────────────────────────────────────────────────────────┘

   ★ THE ONE-LINE VERSION FOR THE INTERVIEW
   ──────────────────────────────────────────
   "I treat a JWT as untrusted assertions. I verify the signature with a
    server-pinned algorithm, I check that the token was minted for my service
    and is still within its validity window, I map a named, explicit set of
    claims to authorities, and I derive identity and tenancy only from the
    validated token — never from a header the client controls."
```

**What to look at:** the gates are **ordered by trust**, and each gate is a place where
a *specific named attack* is stopped. If you can name the attack for each gate, you
will not forget the gate.

---

## 3. Deep Dive — Theory (Beginner → Advanced)

### 3.1 Beginner Level

**The attack catalogue — part 1 (the ones everyone must know).**

**Attack 1 — Algorithm confusion.**
*What happens:* the server publishes an RSA **public** key. The attacker sets
`"alg":"HS256"` in the header and signs the token with the **public key's bytes as the
HMAC secret** — which they can do, because the public key is public. If the server
reads `alg` from the token, it verifies with `HMAC(publicKeyBytes, …)` and the forgery
succeeds.
*Defence:* **pin the algorithm server-side.** Never read `alg` from the token.

**Attack 2 — `alg: none`.**
*What happens:* the attacker sends an unsigned token with `"alg":"none"`. A verifier that
dispatches on the header performs **no signature check at all**.
*Defence:* the same pinning. With `SignatureAlgorithm.RS256` configured, Nimbus rejects
`none` outright.

**Attack 3 — Missing claim validation.**
*What happens:* a token with no `exp`, or with `exp` a year in the future, is accepted
**forever**. A token with the wrong `iss` is accepted by the wrong platform. A token
with the wrong `aud` is accepted by the wrong service.
*Defence:* validate `iss`, `aud`, `exp` and `nbf`, with a 60-second leeway. **Require**
`exp` — its absence must be a failure.

**Attack 4 — Token leakage.**
*What happens:* the token ends up in an access log (because the `Authorization` header
was logged), in a URL (because someone put it in a query string), in the browser history,
in a `Referer` header, or in `localStorage` (where any XSS can read it).
*Defence:* the `Authorization` header only; never a URL; redact in logs; `HttpOnly`
cookies for the refresh token; `localStorage` is never the answer for a long-lived
credential.

### 3.2 Intermediate Level

**Attack 5 — XSS.**
*What happens:* a script running in your page steals the token.
*Defence:* `HttpOnly` + `Secure` + `SameSite=Strict` cookies (JS cannot read them); a
**strict CSP** (`script-src 'self'` — no `unsafe-inline`); output encoding; sanitising
rich-text input; never putting the token in `localStorage`.

**Attack 6 — CSRF.**
*What happens:* the browser auto-attaches the cookie, so a malicious page can cause an
authenticated request.
*Defence:* `SameSite=Strict`; a CSRF token; a custom header that a cross-origin form
cannot set; strict CORS. (See topic 05.)

**Attack 7 — Token guessing / brute force.**
*What happens:* the attacker tries to guess the token or the signing secret.
*Defence:* a **≥256-bit random secret**, a **≥2048-bit RSA key**, and **rate limiting**
on the token endpoints. A properly signed token is not guessable — the risk is a weak
*secret*, not a weak *algorithm*.

**Attack 8 — Mass assignment in the claims.**
*What happens:* the client cannot change a **signed** payload — that is what the
signature guarantees. But the *mapping* can be wrong: if the server maps **every** claim
to an authority, then a claim named `admin` (or `is_superuser`, or `role`) becomes a
privilege, whether the issuer meant it that way or not.
*Defence:* an **explicit, named** claim→authority mapping. Never a blanket map.

**Attack 9 — Denial of service.**
*What happens:* a 1 MB token with deeply nested JSON; or a flood of tokens with random
`kid`s forcing a JWKS fetch per request (the cache-miss DoS); or an unverified token
being fully parsed before the signature is checked.
*Defence:* cap the header and request size; validate the signature **before** heavy
parsing; rate-limit; keep the `kid` space small and structured; limit the JWKS refresh
rate.

**Attack 10 — The confused deputy.**
*What happens:* service A mints a token for `billing-api`; service B (which shares the
issuer) accepts it because `aud` was never checked. Now B acts on a token that was
never meant for it.
*Defence:* **validate `aud`** against **your own** service id — always, on every
service.

**Attack 11 — Replay.**
*What happens:* a captured access token is re-used within its TTL. A captured request is
re-played.
*Defence:* a short TTL; for high-value operations, a **nonce** or an **idempotency
key**; for the strongest case, **DPoP** (RFC 9449) — a client-held key pair, so a
stolen token is useless without the private key.

**The complete attack/defence table.**

| # | Attack | Mechanism | The defence | Where it lives |
|---|---|---|---|---|
| 1 | Algorithm confusion | Server uses the public key as an HMAC secret | **Pin the algorithm** | `NimbusJwtDecoder` builder |
| 2 | `alg: none` | Unsigned token accepted | **Pin the algorithm** | `NimbusJwtDecoder` builder |
| 3 | Missing claim validation | No `exp`, wrong `iss`/`aud` | Validate all four | `JwtValidators` + `JwtClaimValidator` |
| 4 | Token leakage | Logs, URLs, `localStorage`, history | Header only, redact, `HttpOnly` | Edge config + code review |
| 5 | XSS | Script reads the token | `HttpOnly`, strict CSP, encoding | Browser + headers |
| 6 | CSRF | Browser auto-attaches the cookie | `SameSite=Strict` + CSRF token | `Set-Cookie` + `CsrfFilter` |
| 7 | Brute force / weak secret | Guessing the token or the key | ≥256-bit secret, rate limits | Config + gateway |
| 8 | Mass assignment | Every claim → an authority | **Explicit named mapping** | `JwtAuthenticationConverter` |
| 9 | DoS | Huge tokens, JWKS cache-miss flood | Size caps, rate limits, verify-first | Gateway + decoder |
| 10 | Confused deputy | Missing `aud` check | **Validate `aud`** | `JwtClaimValidator` |
| 11 | Replay | Captured token re-used | Short TTL, nonce, DPoP | TTL + endpoint design |

### 3.3 Advanced Level

**The claim→authority mapping — the actual code, and the actual danger.**

```java
// ❌ THE DANGEROUS VERSION — every claim becomes a privilege
converter.setJwtGrantedAuthoritiesConverter(jwt -> jwt.getClaims().entrySet().stream()
        .map(entry -> new SimpleGrantedAuthority(entry.getKey().toUpperCase()))
        .toList());
```

What that code produces for a perfectly ordinary token:

```json
{ "sub": "user-42957", "aud": ["orders-api"], "exp": 1745006000,
  "roles": ["STAFF"], "email": "a@b.com", "tenant_id": "t_9931",
  "internal_debug": true, "can_impersonate": "yes" }
```

```text
Authorities granted:
  SUB, AUD, EXP, ROLES, EMAIL, TENANT_ID, INTERNAL_DEBUG, CAN_IMPERSONATE
```

Any claim whose name happens to match an authority your `hasAuthority(...)` rules check
becomes a privilege. If your rules check `hasAuthority("ADMIN")` and the issuer ever
emits an `admin` claim (even for a different purpose, even as a boolean), the user is an
admin. **This is the "mass assignment" vulnerability, and the payload being signed does
not protect you from it — the signer, not the attacker, is the risk.**

**The correct mapping.**

```java
JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
authorities.setAuthoritiesClaimName("roles");       // ONE named claim
authorities.setAuthorityPrefix("ROLE_");             // ONE prefix
authorities.setAuthoritiesClaimDelimiter(",");       // only if the claim is a comma string
```

Now the same token produces:

```text
Authorities granted:
  ROLE_STAFF
```

…and `internal_debug`, `can_impersonate` and `email` are **ignored** — because the
mapping names exactly one claim.

**The service-to-service flow — `act` and impersonation.**

Two patterns exist for a service acting on behalf of a user.

```text
   PATTERN A — TOKEN EXCHANGE (OAuth2 RFC 8693)
   ────────────────────────────────────────────
   Service A holds a token for itself.
   It calls the authorisation server's token endpoint with
   grant_type=urn:ietf:params:oauth:grant-type:token-exchange
     subject_token = <the user's token>
     requested_token_type = access token
   The AS mints a NEW token for Service B:
     sub = user-42957          ← the user
     aud = service-b
     act = { sub: service-a }  ← who is acting
   Service B validates it and sees: "this is user 42957, acting through service A".

   PATTERN B — DELEGATED CLAIM (a plain JWT with an `act` claim)
   ──────────────────────────────────────────────────────────────
   The issuer mints:
     sub = service-a
     act = { sub: user-42957 }
     aud = service-b
   Service B sees: "this is service A, acting for user 42957".
   ⚠ NOTE: here the USER is in `act`, not the service. Both conventions exist —
     RFC 8693 puts the ACTOR in `act` and the SUBJECT in `sub`.
```

**The impersonation bug.** A service that accepts an `act` claim **without validating
the actor** will believe anyone. The rules:

1. **`act` must be validated against the token's `iss`** — the actor must be a known
   issuer.
2. **The actor must be in the allowed delegation set** for the target service.
3. **The chain must be bounded** — no `act.act.act`.
4. **The audience must still be checked** — `aud` must be the target service.
5. **The impersonated subject's authority must be re-derived**, not taken from a claim
   the actor wrote.

**The multi-tenant trap.** The tenant must come from the **validated** claim:

```java
// ❌ The client controls this header completely
String tenantId = request.getHeader("X-Tenant-Id");

// ✅ From the validated token
String tenantId = jwt.getClaimAsString("tenant_id");
```

The header version is exploitable even if the token is perfectly signed: the attacker
holds a valid token for **their own** tenant and simply sends `X-Tenant-Id: t_9931`.

### 3.4 Expert Level

**RFC 8725 (JWT BCP) — the six rules that summarise the whole chapter.**

1. Perform **algorithmic verification** of the signature — pin the algorithm.
2. Validate the **issuer** and the **subject**.
3. Validate the **audience**.
4. Use and validate the **`exp`** and **`nbf`** claims.
5. Use a **strong key** and validate it against the issuer's published keys.
6. **Do not trust the JWT header** — the `kid` and `alg` are inputs to look up, not
   decisions to follow.

That is the checklist. Everything in this chapter is an elaboration of one of those six.

**What "the payload is public" means for your threat model, precisely.** The payload is
readable by:

- the client that holds the token (obviously);
- **every proxy, load balancer and CDN** on the path;
- **every log pipeline** that records the `Authorization` header (or the URL);
- **your own APM** (OpenTelemetry captures headers by default — check yours);
- **the browser** (and any extension, any `document.cookie` reader, any XSS);
- **the user** (they can decode it in the console — it is *their* data, which is fine).

So the rule is not "the payload is confidential to the client". The rule is: **the
payload must be safe to read by anyone who can see the request.** In practice that means
**no PII, no secrets, no internal-only notes, no stable identifiers that enable
enumeration** — and if you must carry a user reference, carry an **opaque internal id**,
not an email address or a national id.

**The `typ` header as a security control.** RFC 9068 defines `at+jwt` for OAuth2 access
tokens. If your API accepts **both** an ID token (from OIDC) and an access token, then
`typ` (or the claim set) is what tells them apart. Without it, an **ID token** — which
is meant for the *client*, and whose audience is the client — could be replayed at your
API. This is the confused deputy in its OIDC form, and it is a **real** bug in the wild.

**The `crit` header for proprietary extensions.** If you add a security-relevant header
parameter of your own (say, `tenant`), you must list it in `crit`, and the verifier
**MUST** reject a token with an unrecognised `crit` parameter. Spring Security 6 does
not enforce `crit` for you — you implement it as a custom validator. The alternative —
silently ignoring an unknown header — is how a "we added a header to force multi-tenancy"
feature silently stops working.

**Token size as a DoS vector.** A JWT is carried in a header. Tomcat's default
`maxHttpHeaderSize` is **8 KB** in Boot 3, and many ingresses cap at **4 KB**. A token
with 200 role names is ~8 KB and will be rejected by the proxy with a 400 that looks
like a client bug. The mitigations: put **role IDs** in the token (a handful), resolve
them to permissions at the edge, and **cap the claim count**. The security angle is that
an unbounded claim set is also an unbounded *parse* cost per request.

**What a compromised issuer does.** If the issuer's private key leaks, the attacker can
mint **any** token for **any** audience with **any** roles. The defences are **not** in
the resource server — they are: (1) the issuer's key is in a **KMS/HSM** and the signing
operation is auditable; (2) the **JWKS is short-lived and rotated**, and the rotation
has an overlap window; (3) the issuer applies **its own** policy (it will not mint an
`admin` role without the appropriate authorisation); (4) the resource server validates
`iss` and `aud`, so a leaked key for `auth.acme.com` does not mint tokens for
`partner.acme.com` if the issuers use different keys. That last point is the reason
**per-environment and per-partner keys** matter.

---

## 4. Internal Working Mechanism

**Where each defence lives in the Spring Security chain.**

```text
FilterChainProxy
  │
  ├─ CorsFilter                    ← DEFENCE 6 (CSRF/CORS): CORS must run BEFORE auth
  │                                 so a preflight OPTIONS is answered, not rejected.
  ├─ CsrfFilter                    ← DEFENCE 6: on for the cookie-based endpoints
  ├─ BearerTokenAuthenticationFilter
  │    ├─ DefaultBearerTokenResolver
  │    │     ← DEFENCE 4: the token is read from the HEADER only.
  │    │       Never from a query parameter.
  │    └─ JwtDecoder.decode(token)
  │         └─ NimbusJwtDecoder
  │              ├─ parse header + payload
  │              │    ← DEFENCE 9: cap the token size BEFORE this (at the gateway).
  │              ├─ select the key by kid
  │              │    ← DEFENCE 1/2: the ALGORITHM comes from the decoder CONFIG,
  │              │      never from the parsed header.
  │              ├─ VERIFY THE SIGNATURE      ← DEFENCE 1, 2
  │              └─ run the validator chain   ← DEFENCE 3
  │                   ├─ JwtTimestampValidator   (exp, nbf, 60 s leeway)
  │                   ├─ JwtIssuerValidator     (iss)
  │                   ├─ JwtClaimValidator      (aud contains THIS service)
  │                   └─ [custom] typ validator, tenant validator
  │    └─ JwtAuthenticationConverter
  │         └─ Converter<Jwt, Collection<GrantedAuthority>>
  │              ← DEFENCE 8: an EXPLICIT, NAMED claim → authority mapping.
  ├─ AuthorizationFilter
  │    ← DEFENCE 3/10: the final authorisation decision for THIS request.
  └─ Controller
       ← DEFENCE 5: derive identity and tenancy from the @AuthenticationPrincipal Jwt,
         never from a request header.
```

**The two classes you would write for this chapter.**

| Class | Responsibility | The attack it blocks |
|---|---|---|
| `TenantClaimValidator` | Rejects a token whose `tenant_id` is missing or not in the caller's allowed set | #10 (tenant spoofing), #8 |
| `ExplicitRolesConverter` | Maps **only** `roles` → `ROLE_*` and `scope` → `SCOPE_*` | #8 (mass assignment) |
| `AudienceValidator` | Checks `aud` contains this service's id | #10 (confused deputy) |
| `TypeValidator` | Checks `typ` is `at+jwt` when you accept more than one token type | #10 (ID-token replay) |
| `RevocationFilter` / validator | `exp`-independent revocation | #3 (replay after revoke) |

**The extension point that matters most.** `OAuth2TokenValidator<Jwt>` is the
**correct** place for every claim-level check. It composes with
`DelegatingOAuth2TokenValidator`, it runs **after** the signature is verified, and it
produces a single, consistent error shape. Writing these checks in a `Filter` or in
the controller means they can be bypassed by a code path that forgot them.

---

## 5. Step-by-Step Execution Flow

**The complete login flow.**

1. `POST /api/auth/login` with `{username, password}` (plus an MFA code if enabled).
2. The controller validates the request body (`@Valid` → **400** on failure).
3. The service looks up the user and verifies the password with a **slow KDF**
   (bcrypt/Argon2 — topic 05). A failure returns **401** (not 403 — we do not reveal
   whether the account exists).
4. It checks the account is not locked, not expired, and that MFA is satisfied.
5. It loads the user's roles/authorities and their tenant.
6. It **mints the access token** (a JWT with `iss`, `sub`, `aud`, `exp`, `iat`, `jti`,
   `roles`, `tenant_id`, `ver`) and signs it.
7. It **mints the refresh token** (256-bit random), hashes it, and inserts a
   `refresh_token` row with a new `family_id`.
8. The response is **200** with `{"accessToken": "...", "expiresIn": 600,
   "tokenType": "Bearer"}` and a `Set-Cookie` for the refresh token.
9. An audit event `LOGIN_SUCCESS` is recorded with the device and IP.

**The complete refresh flow.**

1. `POST /api/auth/refresh` with the refresh cookie (or the body for native clients).
2. `CsrfFilter` validates the CSRF token (the cookie is ambient).
3. The service hashes the raw token and looks up the row.
4. **Unknown / revoked / expired → 401.** **Already used → reuse detection**: revoke the
   whole family, audit, **401**.
5. Otherwise: mark the row used, create the successor in the same family, mint a new
   access token.
6. **200** with the new access token and a new refresh cookie.

**The complete logout flow.**

1. `POST /api/auth/logout` with the refresh cookie.
2. The service revokes the refresh token's row and (optionally) denies the access
   token's `jti` and/or bumps the user's token version.
3. The refresh cookie is deleted (`Max-Age=0`).
4. An audit event `LOGOUT` is recorded.
5. **204 No Content**.

**The complete service-to-service flow.**

1. Service A holds its own token (`sub: service-a`, `aud: service-b`).
2. It needs to act for a user. It calls the AS's token-exchange endpoint with
   `subject_token` = the user's token.
3. The AS validates the user's token, checks that Service A is allowed to delegate,
   and mints a new token: `sub: user-42957`, `aud: service-b`,
   `act: {sub: service-a}`.
4. Service A calls Service B with the new token.
5. Service B validates it, and — crucially — checks that `act.sub` is a known actor
   and that the actor is permitted to act for that subject.
6. Service B uses `sub` (the user) for the business decision and `act` for the audit
   trail.

---

## 6. Visualizations

### 6.1 The validation decision tree — and the attack each branch blocks (Mermaid flowchart)

```mermaid
flowchart TD
    A["Request with<br/>Authorization: Bearer …"] --> A0{"Token size<br/>≤ 8 KB?"}
    A0 -->|No| X0["413 / 400<br/>🛡 #9 DoS"]

    A0 -->|Yes| B{"3 parts,<br/>valid JSON?"}
    B -->|No| X1["401 invalid_token<br/>🛡 malformed"]
    B -->|Yes| C{"alg == the<br/>PINNED alg?"}
    C -->|No| X2["401 invalid_token<br/>🛡 #1 algorithm confusion<br/>🛡 #2 alg:none"]
    C -->|Yes| D{"kid resolves<br/>in the JWK Set?"}
    D -->|No| X3["401 invalid_token<br/>🛡 key confusion"]
    D -->|Yes| E{"Signature<br/>verifies?"}
    E -->|No| X4["401 invalid_token<br/>🛡 forgery"]
    E -->|Yes| F{"iss == expected?"}
    F -->|No| X5["401 invalid_token<br/>🛡 #3 wrong issuer"]
    F -->|Yes| G{"aud contains<br/>THIS service?"}
    G -->|No| X6["401 invalid_token<br/>🛡 #10 confused deputy"]
    G -->|Yes| H{"typ == at+jwt<br/>(if required)?"}
    H -->|No| X7["401 invalid_token<br/>🛡 ID-token replay"]
    H -->|Yes| I{"exp present AND<br/>exp + 60s > now?"}
    I -->|No| X8["401 invalid_token<br/>🛡 #3 no exp / expired"]
    I -->|Yes| J{"nbf − 60s ≤ now?"}
    J -->|No| X9["401 invalid_token<br/>🛡 future-dated"]
    J -->|Yes| K{"Not revoked?<br/>(version / denylist)"}
    K -->|No| X10["401 invalid_token<br/>🛡 #3 revoked"]
    K -->|Yes| L{"Explicit claim →<br/>authority mapping"}
    L -->|Only named claims| M["✅ Authenticated"]
    L -->|"⚠ blanket map"| X11["🚨 PRIVILEGE ESCALATION<br/>🛡 #8 mass assignment"]
    M --> N{"Authorized for<br/>this resource?"}
    N -->|No| X12["403 Forbidden<br/>(authenticated, not authorized)"]
    N -->|Yes| O["200 OK"]

    style X2 fill:#ffd6d6,stroke:#c00
    style X6 fill:#ffd6d6,stroke:#c00
    style X8 fill:#ffd6d6,stroke:#c00
    style X11 fill:#ffb3b3,stroke:#a00,stroke-width:3px
    style M fill:#d6ffd6,stroke:#0a0
    style O fill:#d6ffd6,stroke:#0a0
    style X12 fill:#fff3cd,stroke:#c80
```

**What to look at:** every rejection branch carries the **attack it blocks**. The
signature check (E) happens **before** every claim check, and the authority mapping
(L) happens **after** the token is known to be genuine — so the mapping can trust the
claims it is reading. The branch marked `X11` is the one that is invisible in testing
unless you write a test for it: a blanket mapping produces a **200** where it should
produce a **403**.

### 6.2 The complete login flow (Mermaid sequence)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant API as Auth API
    participant DB as Users DB
    participant RT as Refresh Token Store
    participant KMS as KMS (signing key)
    participant A as Audit Log

    C->>API: POST /api/auth/login<br/>{"username":"aarav@example.com","password":"…"}
    API->>API: @Valid → 400 if the body is malformed
    API->>DB: SELECT * FROM users WHERE email = ?
    DB-->>API: user(id=42957, roles=[STAFF,PAYMENT_INITIATE], tenant=t_9931)
    API->>API: verify password with Argon2id (slow KDF)
    alt password wrong / account locked
        API-->>C: 401 Unauthorized (same message for both cases)
    end
    API->>KMS: sign the JWS (RSA-PSS, private key never leaves the KMS)
    KMS-->>API: eyJhbGciOiJSUzI1NiIs…
    API->>RT: INSERT refresh_token (family=F1, token_hash=SHA256(raw), expires_at=now+7d)
    API->>A: LOGIN_SUCCESS user=42957 device="Chrome 133" ip=203.0.113.24
    API-->>C: 200 OK
    Note over C,API: {"accessToken":"eyJhbGciOiJSUzI1NiIs…",<br/>"expiresIn":600,"tokenType":"Bearer"}
    API-->>C: Set-Cookie: rt=…; HttpOnly; Secure; SameSite=Strict; Path=/api/auth

    Note over C: The JWT payload (readable by anyone who sees it):
    Note over C: {"iss":"https://auth.acme.com","sub":"42957",<br/>"aud":["orders-api"],"roles":["STAFF","PAYMENT_INITIATE"],<br/>"tenant_id":"t_9931","exp":1745006000,"jti":"f492a307-…"}
```

**What to look at:** the **claims that are added** and the **claims that are not**.
Present: `iss`, `sub`, `aud`, `roles`, `tenant_id`, `exp`, `jti`. Absent: the email,
the name, the address, the tenant's name — none of that belongs in a token. And note
that the signing key never leaves the KMS.

### 6.3 The service-to-service `act` / impersonation flow (Mermaid sequence)

```mermaid
sequenceDiagram
    autonumber
    participant A as Service A (orders)
    participant AS as Authorisation Server
    participant B as Service B (payments)
    participant U as User

    U->>A: POST /api/orders/42/pay  (user's token in the header)
    A->>A: validate the user token (aud=orders-api ✓)
    A->>AS: POST /oauth2/token<br/>grant_type=urn:ietf:params:oauth:grant-type:token-exchange<br/>subject_token=<user token><br/>requested_subject=service-b
    AS->>AS: validate the user token; check service-a is<br/>ALLOWED to delegate to service-b
    alt not allowed
        AS-->>A: 400 invalid_grant
    end
    AS-->>A: 200 OK {access_token: "eyJ…", expires_in: 120}
    Note over AS: new token: sub = user-42957 (the SUBJECT)<br/>aud = service-b (the target)<br/>act = { sub: service-a } (the ACTOR)

    A->>B: POST /api/payments/executes<br/>Authorization: Bearer eyJ…
    B->>B: validate signature, exp, iss
    B->>B: CHECK aud contains "service-b"   ← confused-deputy defence
    B->>B: CHECK act.sub == "service-a" is a known actor<br/>AND service-a may act for this subject
    alt act invalid
        B-->>A: 401 invalid_token (impersonation attempt)
    end
    B->>B: business decision uses sub (the USER),<br/>not act (the service)
    B-->>A: 200 OK {status:"EXECUTED"}
    B->>B: audit: actor=service-a, subject=user-42957, action=payment.execute
```

**What to look at:** the two checks in Service B that make the flow safe — the
**audience** check (this token was minted *for* B) and the **actor** check (`act.sub`
is a known service that is allowed to delegate for this subject). Without the second
check, a caller holding any valid token could set `act` and impersonate.

### 6.4 The hardening checklist, grouped (ASCII)

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ ① TOKEN ISSUE  (what the issuer must get right)                             │
├─────────────────────────────────────────────────────────────────────────────┤
│  ☐ Pin ONE algorithm; never mint with a caller-supplied alg                 │
│  ☐ Always set `jti` (UUID) — without it, per-token revocation is impossible  │
│  ☐ Always set `exp`; ≤ 15 minutes; never omit it                            │
│  ☐ Always set `iss` and `aud`; `aud` = the target service(s)               │
│  ☐ Set `typ` = "at+jwt" for access tokens                                   │
│  ☐ NO PII in the payload — no email, no name, no national id                │
│  ☐ Keep the payload small: role IDs, not role objects (≤ 20 claims)         │
│  ☐ Signing key in a KMS/HSM; never in source control                        │
│  ☐ Key ≥ 2048-bit RSA / P-256 / Ed25519; secret ≥ 256 bits                  │
│  ☐ `kid` on every token, matching a published JWK                           │
│  ☐ Rotate the signing key WITH an overlap window ≥ max TTL + skew           │
├─────────────────────────────────────────────────────────────────────────────┤
│ ② TOKEN VALIDATE  (what every resource server must get right)               │
├─────────────────────────────────────────────────────────────────────────────┤
│  ☐ PIN the algorithm server-side — never read `alg` from the token          │
│  ☐ Verify the signature BEFORE any claim is read or trusted                  │
│  ☐ Validate `iss` (exact string match)                                      │
│  ☐ Validate `aud` — YOUR service id must be in it                           │
│  ☐ Validate `typ` if you accept more than one token type                    │
│  ☐ REQUIRE `exp`; a missing `exp` is a rejection, not a pass                │
│  ☐ Validate `nbf`; allow ≤ 60 s of clock skew                              │
│  ☐ ONE generic `invalid_token` error for every rejection reason             │
│  ☐ Cap the token size (≤ 8 KB) at the gateway                              │
│  ☐ Rate-limit the JWKS refresh / validate the `kid` format                  │
├─────────────────────────────────────────────────────────────────────────────┤
│ ③ STORAGE  (what you keep and where)                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│  ☐ Refresh tokens stored HASHED (SHA-256 of a 256-bit random token)          │
│  ☐ Family tracking + reuse detection + family revocation                    │
│  ☐ Absolute expiry AND idle expiry — never pure sliding                    │
│  ☐ Denylist entries carry a TTL = the token's remaining lifetime            │
│  ☐ Revocation state has a durability story (not "it was in Redis")          │
│  ☐ Prune the refresh-token table; alert on rows-per-family                  │
├─────────────────────────────────────────────────────────────────────────────┤
│ ④ TRANSPORT  (how it travels)                                               │
├─────────────────────────────────────────────────────────────────────────────┤
│  ☐ Bearer in the `Authorization` header ONLY — never in a URL               │
│  ☐ Refresh token: `HttpOnly; Secure; SameSite=Strict` cookie (browsers)    │
│  ☐ Native apps: the request body + the OS keychain                         │
│  ☐ NEVER `localStorage` for a long-lived credential                        │
│  ☐ Redact the `Authorization` header in every log and trace                │
│  ☐ CSRF protection on the cookie-based refresh/logout endpoints             │
│  ☐ Strict CORS — a specific origin, never `*` with credentials             │
├─────────────────────────────────────────────────────────────────────────────┤
│ ⑤ OPERATIONS  (the runbook)                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│  ☐ Rate-limit `/login`, `/refresh`, `/token` (they are password-equivalent) │
│  ☐ Audit every login, refresh, rotation, reuse-detection and revocation    │
│  ☐ Alert on: denylist hit rate, unknown-`kid` rate, JWKS key count         │
│  ☐ Alert on: reuse-detection events (must be ≈ 0)                          │
│  ☐ A documented key-rotation runbook, rehearsed                             │
│  ☐ A documented fail-open / fail-closed policy for the revocation store    │
│  ☐ Step-up authentication for high-value operations                         │
│  ☐ A "log out everywhere" that also kills outstanding access tokens        │
└─────────────────────────────────────────────────────────────────────────────┘
```

**What to look at:** the five groups map onto the five gates in §2. Group ① is the
issuer's job, group ② is every resource server's job, and groups ③–⑤ are the
operational discipline. In an interview, reciting items from **group ②** is what marks
you out as someone who has actually deployed this.

---

## 7. Production-Level Use Cases & Real Industry Examples

**1. A banking application — high-value operations, step-up authentication.**

A retail bank with 300,000 customers and 12,000 API calls/second at peak.

- **Access token**: 5 minutes, `RS256` (RSA-PSS), private key in a **KMS** (HSM-backed).
- **Step-up authentication**: a transfer above ₹2,00,000 requires an `auth_time` claim
  within the last **5 minutes** and an `amr` value of `["otp","face"]`. The payments API
  checks `auth_time` — it does **not** issue a second token.
- **`aud` per service**: `retail-payments`, `retail-accounts`, `retail-cards`. A token
  for `retail-cards` cannot call `retail-payments`.
- **Token version** per customer for "log out everywhere" — the regulatory requirement.
- **Audit trail**: every authentication event, every step-up, every high-value
  operation — with the actor, the subject and the device. Retained for 8 years.
- **Key rotation**: quarterly, with a 15-minute overlap, executed by a runbook with a
  two-person approval.

**What went wrong in this design (and the fix):** the first version put the customer's
**account number** in the token payload, because "the payments service needs it". It
worked — and the token appeared in an internal support ticket. The fix was to replace
it with the **internal customer id**, and the payments service fetched the account
number from its own store. The lesson: **the payload is a broadcast channel; only put
things in it that every holder may see.**

**2. A multi-tenant SaaS — per-tenant keys and derived tenancy.**

A SaaS platform serving 4,000 tenants and 800,000 users.

- **Per-tenant signing keys**: `kid = "tenant-t_9931-v3"`. The JWKS is **sharded** by
  tenant and CDN-cached, so an API that serves 200 tenants fetches 200 shards, not
  4,000 keys.
- **The tenant is derived from the validated `tenant_id` claim**, never from an
  `X-Tenant-Id` header. The header is *read* — and used only to **reject** a mismatch,
  never to select the tenant.
- **Per-tenant revocation**: `INCR epoch:tenant:t_9931` invalidates every token of
  that tenant in one write.
- **Per-user revocation**: `INCR ver:user:88213`.
- **Authority mapping**: `roles` → `ROLE_*`, `permissions` → `PERM_*`, and **nothing
  else**. A tenant's admin flag lives in the tenant record, not in the token.
- Scale: 8,000 req/s peak; the JWKS shards are served from a CDN with a 60-second TTL.

**What went wrong (and the fix):** the first release derived the tenant from a
**subdomain** (`t9931.acme.com`). It worked until a customer used a custom domain —
and then the tenant resolution silently fell back to the default tenant, and one
customer's users could see another customer's data. The fix: **the tenant comes from
the token, and the host is only a routing hint.** The post-mortem action was a test that
asserts the tenant is never derived from the request.

**3. An internal service mesh — 5-minute tokens and the introspection trade-off.**

A 60-service mesh issuing 50,000 tokens/second from a central issuer.

- **Access token**: **5 minutes**, `ES256` (P-256 — small and fast), `aud` = the target
  service, `sub` = the calling service, `act` for delegation.
- **Service-to-service grant**: a workload identity (a SPIFFE/SPIRE-style id) exchanges
  a bootstrap credential for a short-lived token.
- **Introspection only where it matters**: the "payments-ledger" service uses **opaque**
  tokens with RFC 7662 introspection, because a revoked ledger write must never be
  honoured. Every other service uses the JWT path. The trade-off is explicit: the
  ledger service pays a lookup per request and gains exact revocation.
- **Key rotation**: the issuer rotates every **7 days**, with a 10-minute overlap, and
  the mesh's sidecars refresh their JWKS cache on the unknown-`kid` path.
- Scale: 50,000 token issuances/s, 2M verifications/s across the mesh, p99
  verification **< 0.4 ms** (ES256).

**What went wrong (and the fix):** the first mesh deployment let every service accept
**any** token from the issuer — no `aud` check — because "they are all internal". A
compromised low-trust service (a logging sidecar) minted a token with
`aud: ["payments-ledger"]` and moved money. The fix was the **audience check on every
service**, with a CI test that asserts each service's `aud` list. The lesson:
**"internal" is not a security boundary; the audience is.**

---

## 8. Worked Examples (with Output + Dry Run)

### 8.1 Worked Example 1 — The algorithm-confusion attack, demonstrated and blocked

**Problem.** Prove that a decoder which reads `alg` from the token can be forged
against, and that a pinned decoder cannot.

**Code.**

```java
package com.example.jwt.attack;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public final class AlgorithmConfusionDemo {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        // ── THE ATTACK ────────────────────────────────────────────────────────
        // Use the PUBLIC key's raw bytes as an HMAC secret.
        byte[] publicKeyBytes = publicKey.getEncoded();
        String forged = forgeWithPublicKeyAsHmacSecret(publicKeyBytes);

        // ── THE VULNERABLE DECODER ───────────────────────────────────────────
        // No .signatureAlgorithm(...) → the algorithm comes from the token header.
        JwtDecoder vulnerable = NimbusJwtDecoder.withPublicKey(publicKey).build();

        // ── THE FIXED DECODER ────────────────────────────────────────────────
        JwtDecoder pinned = NimbusJwtDecoder.withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        System.out.println("VULNERABLE decode → " + attempt(vulnerable, forged));
        System.out.println("PINNED     decode → " + attempt(pinned, forged));
    }

    private static String attempt(JwtDecoder decoder, String token) {
        try {
            var jwt = decoder.decode(token);
            return "ACCEPTED  subject=" + jwt.getSubject() + "  ← FORGED";
        } catch (Exception ex) {
            return "REJECTED  " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
    }

    private static String forgeWithPublicKeyAsHmacSecret(byte[] publicKeyBytes) throws Exception {
        long exp = System.currentTimeMillis() / 1000 + 600;
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://auth.acme.com")
                .subject("attacker")
                .audience(List.of("orders-api"))
                .expirationTime(new java.util.Date(exp * 1000))
                .claim("roles", List.of("ADMIN"))
                .build();

        // The forged header claims HS256 …
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).type("JWT").build();
        SignedJWT jwt = new SignedJWT(header, claims);
        // … and the "secret" is the PUBLIC key.
        jwt.sign(new MACSigner(publicKeyBytes));
        return jwt.serialize();
    }
}
```

**Actual expected output.**

```text
VULNERABLE decode → ACCEPTED  subject=attacker  ← FORGED
PINNED     decode → REJECTED  JwtValidationException: Failed to create JWS SignedJWT: The given public key is of unsupported type or the algorithm 'HS256' is not allowed
```

**Dry run — step by step.**

1. The server publishes `publicKey` at its JWKS endpoint. This is **normal, correct
   design**.
2. The attacker fetches the JWKS, extracts the RSA public key, and takes
   `publicKey.getEncoded()` — the **DER-encoded** SubjectPublicKeyInfo bytes. These are
   public.
3. The attacker builds a JWS whose header says `"alg":"HS256"`, whose payload claims
   `sub: attacker` and `roles: [ADMIN]`, and signs it with
   `MACSigner(publicKeyBytes)` — HMAC-SHA256 keyed with the public key.
4. **The vulnerable decoder**: `NimbusJwtDecoder.withPublicKey(publicKey).build()` —
   no algorithm was configured, so Nimbus uses the **header's** `alg`. It sees `HS256`,
   and because the key is an RSA key it *could* legitimately be an HMAC key (any byte
   array can be an HMAC key), so it computes `HMAC-SHA256(publicKeyBytes, signingInput)`
   and compares. **It matches.** The forged token is **accepted** with
   `roles: [ADMIN]`.
5. **The pinned decoder**: `.signatureAlgorithm(SignatureAlgorithm.RS256)` configures
   the JWS algorithm family as `Signature` and the algorithm as `RS256`. Nimbus now
   **refuses to construct** a `SignedJWT` for an `HS256` header with an RSA key:
   *"The given public key is of unsupported type or the algorithm 'HS256' is not
   allowed"*. Spring wraps it in a `JwtValidationException` → **401**.

**The exact HTTP response in production.**

```text
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer error="invalid_token", error_description="Failed to create JWS SignedJWT: The given public key is of unsupported type or the algorithm 'HS256' is not allowed"
Content-Length: 0
```

**Takeaway.** The difference between the two decoders is **one method call**. The
vulnerable one lets an attacker with a *public* key mint an admin token. This is why
"pin the algorithm" is the first item on every JWT checklist — and why it is the first
thing to check in a code review.

---

### 8.2 Worked Example 2 — A complete, explicit `JwtAuthenticationConverter`

**Problem.** Map only the claims you expect, and know exactly what authorities result.

**Code.**

```java
package com.example.auth.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * WHY this exists: the DEFAULT JwtAuthenticationConverter maps the "scope" claim to
 * SCOPE_* authorities and NOTHING else. Teams then add a blanket map "to get the roles
 * working" — and that blanket map is the mass-assignment vulnerability.
 */
public final class ExplicitAuthorityConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /** WHY a fixed set of source claims: every claim NOT listed here is ignored. */
    private static final String ROLES_CLAIM = "roles";
    private static final String SCOPE_CLAIM = "scope";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        // ── 1. roles → ROLE_* ────────────────────────────────────────────────
        // WHY getClaimAsStringList and not a raw map access: the claim may be absent,
        // a single string, or an array. The helper normalises all three safely.
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles != null) {
            for (String role : roles) {
                if (role != null && !role.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
        }

        // ── 2. scope → SCOPE_* ───────────────────────────────────────────────
        // WHY the standard helper for scope: it handles BOTH the "scope" and "scp"
        // claim names and the space-delimited string form — and its prefix is fixed
        // at SCOPE_, which is what Spring's OAuth2 conventions expect.
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        scopeConverter.setAuthoritiesClaimName(SCOPE_CLAIM);
        scopeConverter.setAuthorityPrefix("SCOPE_");
        authorities.addAll(scopeConverter.convert(jwt));

        // ── 3. EVERYTHING ELSE IS IGNORED ────────────────────────────────────
        //    "email", "tenant_id", "admin", "is_superuser", "debug" — none of them
        //    reach the authority set. That is the whole point.
        return authorities;
    }
}
```

**Wiring it.**

```java
@Bean
JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new ExplicitAuthorityConverter());
    converter.setPrincipalClaimName("sub");
    return converter;
}
```

**Actual expected output — the authorities for three different tokens.**

```text
TOKEN A: {"sub":"42957","aud":["orders-api"],"roles":["STAFF","PAYMENT_INITIATE"],
          "scope":"orders:read orders:write","tenant_id":"t_9931",
          "email":"aarav@example.com","admin":true,"is_superuser":true}
→ authorities = [ROLE_STAFF, ROLE_PAYMENT_INITIATE, SCOPE_orders:read, SCOPE_orders:write]

TOKEN B: {"sub":"42957","aud":["orders-api"],"roles":"STAFF"}
→ authorities = [ROLE_STAFF]

TOKEN C: {"sub":"42957","aud":["orders-api"],"scope":"orders:read"}
→ authorities = [SCOPE_orders:read]
```

**Dry run — step by step, for token A.**

1. `converter.convert(jwt)` is called by `JwtAuthenticationConverter.convert(...)`.
2. `jwt.getClaimAsStringList("roles")` → the claim is a JSON array `["STAFF",
   "PAYMENT_INITIATE"]` → the helper returns `List.of("STAFF", "PAYMENT_INITIATE")`.
   *Note it handles the string form too: token B's `"roles":"STAFF"` returns
   `List.of("STAFF")` rather than throwing.*
3. Each role becomes `ROLE_<role>` → `ROLE_STAFF`, `ROLE_PAYMENT_INITIATE`.
4. `scopeConverter.convert(jwt)` reads `scope` = `"orders:read orders:write"`, splits
   on the space, and prefixes each with `SCOPE_` → `SCOPE_orders:read`,
   `SCOPE_orders:write`.
5. The `LinkedHashSet` deduplicates (a role listed twice appears once) and preserves
   the insertion order — so the authorities list is **deterministic**, which matters for
   assertion-based tests.
6. **`email`, `tenant_id`, `admin` and `is_superuser` are never read.** They cannot
   become authorities because no code path turns them into one.

**The comparison with the blanket mapping.**

```text
TOKEN A under a BLANKET map (every claim → an authority):
→ [SUB, AUD, ROLES, SCOPE, TENANT_ID, EMAIL, ADMIN, IS_SUPERUSER]
  ⚠ "ADMIN" is now an authority. If any rule checks hasAuthority("ADMIN"),
    the user is an admin. The token is perfectly validly signed — the issuer
    emitted an "admin": true claim for its own reasons, and the SERVER turned
    it into a privilege.

TOKEN A under the EXPLICIT map:
→ [ROLE_STAFF, ROLE_PAYMENT_INITIATE, SCOPE_orders:read, SCOPE_orders:write]
  ✓ Nothing the server did not name explicitly became a privilege.
```

**Takeaway.** The security property is not "the payload is signed". It is **"the server
decides which claims become authority, by name, in code that a reviewer can read"**.

---

### 8.3 Worked Example 3 — A multi-tenant token with a forged `tenant_id`

**Problem.** A user of tenant `t_1001` tries to read tenant `t_9931`'s data by forging
the `tenant_id` claim.

**Code — the controller.**

```java
package com.example.tenant;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoices;

    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @GetMapping
    public java.util.List<Invoice> list(
            @AuthenticationPrincipal Jwt jwt,
            // WHY we read the header AT ALL: to REJECT a mismatch, never to select.
            @RequestHeader(name = "X-Tenant-Id", required = false) String claimedTenant) {

        // ① The tenant comes from the VALIDATED token — never from the header.
        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null) {
            throw new TenantMismatchException("token carries no tenant");
        }

        // ② The header may only be used to REJECT, never to select.
        if (claimedTenant != null && !claimedTenant.equals(tenantId)) {
            throw new TenantMismatchException("X-Tenant-Id does not match the token");
        }

        // ③ The query is scoped by the token's tenant, not by any client input.
        return invoices.listForTenant(tenantId);
    }
}
```

**The attacker's request.**

```text
GET /api/invoices
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
X-Tenant-Id: t_9931
```

where the token's payload is:

```json
{ "sub": "user-88213", "aud": ["orders-api"], "tenant_id": "t_1001",
  "roles": ["STAFF"], "exp": 1745006000 }
```

**Actual expected output.**

```text
HTTP/1.1 403 Forbidden
Content-Type: application/problem+json

{
  "type": "https://api.acme.com/problems/tenant-mismatch",
  "title": "Forbidden",
  "status": 403,
  "detail": "X-Tenant-Id does not match the token",
  "instance": "/api/invoices"
}
```

**Dry run — step by step.**

1. The `Authorization` header carries a token **signed by the legitimate issuer**. The
   signature verifies. The `iss` matches. `aud` contains `orders-api`. `exp` is in the
   future. The token is **genuine**.
2. `@AuthenticationPrincipal Jwt jwt` gives the controller the **validated** token.
3. `jwt.getClaimAsString("tenant_id")` → `"t_1001"` — the tenant the issuer actually
   bound to this user.
4. The header says `t_9931`. `claimedTenant` is not null and does not equal `t_1001` →
   `TenantMismatchException` → **403**.
5. Note what did **not** happen: the attacker did **not** have to forge a signature.
   The attack is purely about **which field the server trusts**. The token was
   completely valid — for a *different tenant*.

**The variant that the header check does not stop.**

```text
   GET /api/invoices
   Authorization: Bearer <valid token for t_1001>
   (no X-Tenant-Id header at all)

   → claimedTenant == null → the mismatch check is skipped
   → tenantId = "t_1001"  → the query is scoped to t_1001
   → the attacker sees their OWN tenant's invoices. ✅ Correct behaviour.

   ★ The security does not come from the header check. It comes from the fact that
     the QUERY is scoped by the token's tenant. The header check is only a defence
     against a client that is confused about which tenant it is talking to.
```

**Takeaway.** The tenant is a **claim**, validated as part of the token. The request
header is at best a **hint** and at worst an attack surface. And note the status code:
the token was valid, so this is **403** (authenticated but not authorised), **not 401**.

---

## 9. Code from Scratch — Production-Grade

**The hardened resource-server configuration — every defence in one file.**

```java
package com.example.orders.config;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class HardenedResourceServerConfig {

    private static final String THIS_SERVICE = "orders-api";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    @Bean
    SecurityFilterChain apiChain(HttpSecurity http,
                                 JwtAuthenticationConverter converter) throws Exception {
        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // WHY CSRF is off HERE: this API authenticates with the Authorization
                // header, which a browser never attaches automatically. There is no
                // ambient credential for a cross-site page to ride on.
                // The /api/auth/** endpoints (which DO use a cookie) re-enable it.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                // ★ DEFENCE 1 & 2: the algorithm is PINNED. The token's own header
                //   never chooses the verification path.
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        // ★ DEFENCE 3: timestamp + issuer. The default leeway is 60 s.
        OAuth2TokenValidator<Jwt> timestampAndIssuer = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(CLOCK_SKEW),
                new JwtIssuerValidator(issuer));

        // ★ DEFENCE 10 (confused deputy): the token must name THIS service.
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(THIS_SERVICE));

        // ★ DEFENCE 3: `exp` must be PRESENT. A token with no exp is rejected —
        //   it would otherwise be valid forever.
        OAuth2TokenValidator<Jwt> expMandatory = new OAuth2TokenValidator<>() {
            @Override
            public OAuth2TokenValidatorResult validate(Jwt jwt) {
                if (jwt.getExpiresAt() == null) {
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                            "invalid_token", "The token has no 'exp' claim", null));
                }
                return OAuth2TokenValidatorResult.success();
            }
        };

        // ★ DEFENCE 3 (token type): if this API also accepts ID tokens, `typ` is the
        //   discriminator. Without it, an ID token (audience = the client) could be
        //   replayed here.
        OAuth2TokenValidator<Jwt> type = new OAuth2TokenValidator<>() {
            @Override
            public OAuth2TokenValidatorResult validate(Jwt jwt) {
                String typ = jwt.getHeaders().get("typ");
                if (typ != null && !"at+jwt".equals(typ) && !"JWT".equals(typ)) {
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                            "invalid_token", "Unsupported token type", null));
                }
                return OAuth2TokenValidatorResult.success();
            }
        };

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampAndIssuer, audience, expMandatory, type));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        // ★ DEFENCE 8 (mass assignment): an EXPLICIT, NAMED mapping.
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
```

**The `SecurityContext` accessor — reading the validated claims, safely.**

```java
package com.example.orders.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * WHY this helper exists: the temptation in every controller is to read the tenant or
 * the user id from a request header, a query parameter, or a method argument that a
 * caller supplied. This class makes the VALIDATED token the only source.
 */
public final class CallerContext {

    private CallerContext() {
    }

    public static Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken();
        }
        throw new IllegalStateException("no JWT authentication on the current context");
    }

    public static String currentUserId() {
        return currentJwt().getSubject();
    }

    public static String currentTenantId() {
        String tenantId = currentJwt().getClaimAsString("tenant_id");
        if (tenantId == null) {
            // WHY throw rather than default: a missing tenant must never silently
            // become "the default tenant" — that is how one tenant reads another's data.
            throw new IllegalStateException("the token carries no tenant_id claim");
        }
        return tenantId;
    }

    /** WHY a helper for "acting as": the actor and the subject are different people. */
    public static String currentActor() {
        Jwt jwt = currentJwt();
        Map<String, Object> act = jwt.getClaim("act");
        if (act == null) {
            return jwt.getSubject();
        }
        Object actor = act.get("sub");
        return actor == null ? jwt.getSubject() : actor.toString();
    }
}
```

**The log redaction — preventing token leakage into the logs.**

```java
package com.example.orders.config;

import java.util.List;
import java.util.regex.Pattern;

import ch.qos.logback.classic.PatternLayoutEncoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WHY this class exists: the single most common token leak is a log line that prints
 * the Authorization header. The mitigation is a PATTERN that redacts the value at the
 * logging framework's level, so no individual log statement can leak it.
 */
@Configuration
@ConditionalOnProperty(name = "security.logging.redact-authorization", havingValue = "true",
        matchIfMissing = true)
public class LogRedactionConfig {

    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+");

    @Bean
    PatternLayoutEncoder redactingEncoder() {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n");
        encoder.setOutputPatternAsHeader(false);
        return encoder;
    }

    /** Use this wherever a request header must be logged. */
    public static String redact(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        return BEARER.matcher(headerValue).replaceAll("$1***REDACTED***");
    }

    public static List<String> sensitiveHeaders() {
        return List.of("authorization", "proxy-authorization", "cookie", "set-cookie");
    }
}
```

---

## 10. Common Mistakes & Gotchas

### ❌ Mistake 1 — Reading `alg` from the token

```java
// ❌ Enables algorithm confusion and alg:none
NimbusJwtDecoder.withPublicKey(publicKey).build();   // no algorithm configured
```
**✅ right.** Always configure the algorithm explicitly.

```java
NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS256).build();
```

---

### ❌ Mistake 2 — Not validating `aud`

```java
// ❌ Any token from the issuer works on any service
decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
```
**✅ right.** Add an audience validator.

---

### ❌ Mistake 3 — Accepting a token with no `exp`

```java
// ❌ Valid forever
// (JwtTimestampValidator passes when exp is absent)
```
**✅ right.** Add an explicit validator that **requires** `exp`.

---

### ❌ Mistake 4 — Mapping every claim to an authority

```java
// ❌ "admin": true becomes an authority
jwt.getClaims().keySet().stream().map(k -> new SimpleGrantedAuthority(k.toUpperCase()))
```
**✅ right.** Map **named** claims only.

---

### ❌ Mistake 5 — Deriving the tenant from a request header

```java
// ❌ The client fully controls this
String tenantId = request.getHeader("X-Tenant-Id");
```
**✅ right.** `jwt.getClaimAsString("tenant_id")`.

---

### ❌ Mistake 6 — Putting PII in the payload

```java
// ❌ Readable by every proxy, log and browser
.claim("email", "aarav@example.com").claim("nationalId", "4821-9933")
```
**✅ right.** An internal id and nothing more.

---

### ❌ Mistake 7 — Logging the `Authorization` header

```java
// ❌ A credential store in your log pipeline
log.info("request {} {}", uri, request.getHeader("Authorization"));
```
**✅ right.** Redact at the logging framework, and log the **length** if you need
correlation.

---

### ❌ Mistake 8 — Putting the token in a URL

```text
❌ GET /api/orders?access_token=eyJ…
   → the server access log, the browser history, and the Referer header all carry it
```
**✅ right.** The `Authorization` header, always.

---

### ❌ Mistake 9 — Storing the access token in `localStorage`

```javascript
// ❌ Any XSS on the page reads it
localStorage.setItem('accessToken', token);
```
**✅ right.** In memory, or an `HttpOnly` cookie (with CSRF protection).

---

### ❌ Mistake 10 — Distinguishable error messages

```java
// ❌ An oracle: the attacker learns WHY the token was rejected
throw new JwtValidationException("Audience mismatch");
throw new JwtValidationException("Token expired");
```
**✅ right.** One generic `invalid_token` for every reason.

---

### ❌ Mistake 11 — Not capping the token size

```text
❌ A 1 MB token → a huge JSON parse on every request, before the signature is checked
```
**✅ right.** Cap the header size at the gateway (and Tomcat), and verify the signature
before deep parsing.

---

## 11. Performance Considerations

**What each defence costs.**

| Defence | Added latency | Notes |
|---|---|---|
| Pin the algorithm | **0** | A configuration change, not a runtime cost |
| Signature verification (RSA-2048) | 50–150 µs | The irreducible cost of statelessness |
| Signature verification (ES256) | 40–80 µs | Smaller keys and signatures |
| Signature verification (EdDSA) | ~50 µs | Fastest modern option |
| `JwtTimestampValidator` | ~1 µs | Pure comparisons |
| `JwtIssuerValidator` | ~1 µs | A string comparison |
| `JwtClaimValidator` (aud) | ~1 µs | A list scan |
| **Revocation check (Redis)** | **0.2–5 ms** | The only defence that costs a network hop |
| **Explicit authority mapping** | ~5–20 µs | A handful of list operations |
| Local Caffeine mirror | ~0 | Removes the revocation hop |

**The headline number.** The entire cryptographic cost of a stateless API is
**~0.1 ms** per request. The entire *revocation* cost is **0.2–5 ms**. That is the
trade, stated as a number: **you can make verification 20× cheaper, at the price of
making revocation 20× slower.**

**The claim-count effect.** JSON parsing scales with the claim count, not the byte
count alone. 5 claims → ~10 µs of parsing; 50 claims → ~60 µs; 500 claims → ~700 µs
and a token over 8 KB. **The mapping cost also scales** — 200 role strings means 200
`SimpleGrantedAuthority` allocations and a `hasAuthority` scan per rule evaluation.
The fix is **role IDs** (10–20 in the token) resolved at the edge.

**The `LinkedHashSet` in the authority converter.** A `Set` is not just for
deduplication — it **bounds the size** of the authority list, which bounds the cost of
every `hasAuthority` check downstream. A `List` would allow duplicates and unbounded
growth.

**The JWKS cache — the feature you forget.** `NimbusJwtDecoder.withJwkSetUri(...)`
caches the key set, so the hot path makes **zero** network calls. The cache refreshes
on an **unknown `kid`** and on a scheduled interval. This means a JWKS endpoint being
slow does not slow down your API — but the **cache-miss path** is a blocking fetch, so
an attacker sending random `kid`s can force a fetch per request (a cache-miss DoS).

**The DoS arithmetic.** An attacker sending 1,000 req/s with random `kid`s forces
1,000 JWKS fetches/s from your API to your auth service — a **1:1 amplification**.
Mitigations: rate-limit at the gateway, validate the `kid` format before the lookup
(the decoder does not, so wrap it), and put the JWKS behind a CDN.

**What to measure.**

| Metric | Target |
|---|---|
| `jwt.verification.duration` (p99) | < **0.5 ms** |
| `jwt.token.size` (p99) | < **1,500 bytes** |
| `jwt.unknown_kid.rate` | ≈ 0 — a spike is a rotation or an attack |
| `jwks.cache.hit_ratio` | > **99.9%** |
| `jwt.authorities.count` (p99) | < **50** — a rising tail means a claim-set leak |
| `security.oauth2.authorizations` | The 401 rate, split by reason |
| `auth.revocation.check.duration` | < **5 ms** |

---

## 12. Security Considerations

**The consolidated threat model.**

| Threat | Attack path | The defence | Where |
|---|---|---|---|
| **Forgery** | The attacker crafts a token | The signature; a pinned algorithm; a strong key | Decoder config |
| **Algorithm confusion** | `alg: HS256` + the public key as the secret | **Pin the algorithm** | Decoder config |
| **`alg: none`** | An unsigned token | **Pin the algorithm** | Decoder config |
| **Claim forgery** | The payload is edited | The signature covers the payload — *if verified* | Decoder |
| **Missing `exp`** | A token that never dies | Require `exp` | Validator |
| **Wrong audience** | A token for another service | Validate `aud` | Validator |
| **Wrong issuer** | A partner's token | Validate `iss` | Validator |
| **ID-token replay** | An OIDC ID token used at the API | Validate `typ` | Validator |
| **Privilege escalation** | A mis-mapped claim | An explicit claim→authority mapping | Converter |
| **Tenant spoofing** | A forged `X-Tenant-Id` | Derive the tenant from the validated claim | Controller |
| **Token leakage** | Logs, URLs, `localStorage` | Header only; redact; `HttpOnly` | Edge + code review |
| **XSS** | A script steals the token | `HttpOnly`; strict CSP; encoding | Browser |
| **CSRF** | The browser attaches the cookie | `SameSite=Strict`; CSRF token | `Set-Cookie` + `CsrfFilter` |
| **Brute force** | Guessing the secret | ≥256-bit secret; rate limits | Config + gateway |
| **DoS** | Huge tokens; JWKS cache-miss flood | Size caps; rate limits; verify-first | Gateway |
| **Replay** | A captured token within its TTL | Short TTL; nonce; DPoP | TTL + endpoint design |
| **Impersonation** | An `act` claim without validation | Validate the actor and the delegation | Validator |
| **Replay of a write** | A captured POST is repeated | An **idempotency key** on writes | Endpoint design |

**The rules, condensed into eleven lines.**

1. **Pin the algorithm.** Never read `alg` from the token.
2. **Verify the signature before you trust any claim.**
3. **Validate `iss`, `aud`, `typ`, `exp` and `nbf`.** Require `exp`.
4. **Map claims to authorities explicitly, by name.**
5. **Derive identity and tenancy from the validated token only.**
6. **No PII in the payload.** It is public.
7. **Bearer in the header only.** Never in a URL, never in a log.
8. **Refresh tokens: hashed, rotated, family-tracked, reuse-detected.**
9. **`HttpOnly; Secure; SameSite=Strict`** for the browser refresh token.
10. **Rate-limit the token endpoints.**
11. **One generic error for every rejection.**

> 📌 **The general principle, stated once.** Every control above is the same control
> applied to a different object: **do not let an untrusted input choose the rules.**
> The token must not choose the algorithm; the client must not choose the tenant; the
> claim set must not choose the privileges; the browser must not choose the CORS
> origin. That single sentence covers attacks 1, 2, 8, 10 and the multi-tenant trap.

---

## 13. Best Practices

1. **The algorithm is server configuration**, never token input.
2. **The validator chain is complete**: timestamp + issuer + audience + `exp`
   mandatory + `typ`.
3. **One generic `invalid_token`** for every rejection.
4. **The authority mapping is explicit**, one named claim at a time.
5. **Identity, tenancy and acting-as come from the validated token**, never from a
   header.
6. **The payload carries identifiers, not personal data.**
7. **The access token lives in the `Authorization` header** and nowhere else.
8. **The refresh token lives in an `HttpOnly` cookie** (browser) or the OS keychain
   (native), hashed server-side.
9. **`localStorage` is never the store for a long-lived credential.**
10. **Logs and traces redact the `Authorization` header** — at the framework level.
11. **The token size is capped** at the gateway, and the signature is verified before
    heavy parsing.
12. **The token endpoints are rate-limited** per IP and per account.
13. **Every authentication event is audited** — login, refresh, rotation, reuse
    detection, revocation.
14. **The signing key lives in a KMS**, is rotated with an overlap window, and is never
    in source control.
15. **The `kid` space is small and structured**, and the JWKS is cached behind a CDN.
16. **High-value operations require step-up authentication** — a fresh `auth_time` claim.
17. **Multi-tenant systems derive the tenant from the claim**, and a sharded JWKS keeps
    the key count sane.
18. **The revocation policy (fail-open / fail-closed) is explicit, per service**, and
    metriced.
19. **The hardening checklist is a CI gate**, not a document.
20. **The service-to-service flow validates the `act` actor** and bounds the delegation
    chain.

---

## 14. Hands-On Exercises & Code Challenges

### Exercise 1 — Prove the algorithm-confusion attack is blocked

**Steps.**
1. Generate an RSA key pair; expose the **public** key at a JWKS endpoint.
2. Write a test that mints an `HS256` token signed with the public key's bytes, claiming
   `roles: [ADMIN]`.
3. Decode it with a **pinned** decoder — assert it throws `JwtValidationException`.
4. Remove the pinning (in a test-local decoder) — assert it **succeeds** and the subject
   is the attacker.
5. Add the forged token to a permanent regression test suite.

**Acceptance criteria.**
- Step 3 throws with a message naming the algorithm mismatch.
- Step 4 succeeds — proving the vulnerability is real and not theoretical.
- The regression test stays in the suite permanently.
- You can explain, in one sentence, why removing the pinning is what enabled it.

**Stretch goal.** Add the same test for `alg: none` and for a token with an **unknown
`kid`**, and assert all three produce a 401 through the full filter chain (not just at
the decoder).

---

### Exercise 2 — Prove the audience check blocks the confused deputy

**Steps.**
1. Mint two tokens from the same issuer: one with `aud: ["billing-api"]`, one with
   `aud: ["orders-api"]`.
2. Configure the decoder with `aud` validation for `orders-api`.
3. Assert the first token is rejected and the second is accepted.
4. Mint a third with `aud: ["billing-api", "orders-api"]` — assert it is accepted.
5. Mint a fourth with **no** `aud` — assert it is rejected.

**Acceptance criteria.**
- Steps 3–5 all behave as expected.
- The rejection message is the **generic** `invalid_token`, not "audience mismatch".
- The test proves that a token with an **empty** audience is rejected (the
  "absence becomes an empty list" trap).

**Stretch goal.** Assert the HTTP status is **401** (not 403) through the full filter
chain — the distinction is a favourite interview question.

---

### Exercise 3 — Audit a claim→authority mapping for mass assignment

**Steps.**
1. Write a token with 12 claims, including `admin: true`, `is_superuser: true`,
   `role: "ADMIN"` and `scope: "admin:all"`.
2. Run it through your production converter and print the authorities.
3. Grep your codebase for `hasAuthority(` and `hasRole(` and list **every** authority
   string your rules check.
4. For each, decide: is it produced by your mapping, and by **design**?

**Acceptance criteria.**
- The authority list is printed and contains **only** what you intended.
- Every `hasAuthority(...)` string in the codebase is either produced by your mapping
   or is provably unreachable.
- The token with `admin: true` produces **no** admin authority.
- You can state, in one paragraph, what would happen if someone added a
   `jwt.getClaims().keySet()` map tomorrow.

**Stretch goal.** Add a startup assertion that the **set of authority names** your
converter can produce is a subset of an explicitly configured allowlist — so a claim
rename cannot silently change your authorisation.

---

### Code Challenge 1 — A hardened decoder that survives the whole attack suite

**Problem.** Build a `JwtDecoder` and configuration that rejects **every** token in a
list of 12 attacks, and returns the correct HTTP status for each.

**Acceptance criteria.**
- One `JwtDecoder` bean with a pinned algorithm.
- A `DelegatingOAuth2TokenValidator` containing: `JwtTimestampValidator`,
  `JwtIssuerValidator`, an `aud` validator, a **mandatory-`exp`** validator and a
  **`typ`** validator.
- An **explicit** `JwtAuthenticationConverter` mapping only `roles` and `scope`.
- A `@SpringBootTest` with **12** test methods — one per attack — each asserting the
  exact **HTTP status** and the **exact** error code:
  ① algorithm confusion ② `alg: none` ③ no `exp` ④ expired ⑤ wrong `iss`
  ⑥ wrong `aud` ⑦ wrong `typ` ⑧ blank signature ⑨ unknown `kid` ⑩ future `nbf`
  ⑪ blanketed `admin` claim ⑫ a valid token (the negative control).
- The valid-token test asserts the **exact** authority set.
- Every 401 response body is **identical** (one generic error).

**Stretch goal.** Add a **property-based** test that generates 1,000 random
malformed/edge-case tokens and asserts that none of them yields a status other than
401/400 — and that none causes an exception other than the expected one.

---

### Code Challenge 2 — A tenant isolation layer that cannot be bypassed

**Problem.** Implement tenancy for a multi-tenant API such that the tenant **cannot**
be influenced by the client at all.

**Acceptance criteria.**
- A `TenantContext` that resolves the tenant **only** from the validated `Jwt`.
- A `TenantValidator` (`OAuth2TokenValidator<Jwt>`) that rejects a token whose
  `tenant_id` is missing or blank.
- A repository layer where **every** query method takes the tenant as a mandatory
  parameter — there is no method that can query without it.
- A test proving that a request with a forged `X-Tenant-Id` header returns **403** and
  that a request **without** the header still returns the **token's** tenant's data.
- A test proving that a token **without** a `tenant_id` claim is rejected with **401**.
- An ArchUnit-style test (or a reflection scan) asserting that **no** repository
  method takes a `String tenantId` from a controller — it is always
  `TenantContext.currentTenantId()`.

**Stretch goal.** Add a **cache** layer that keys on `(tenantId, resourceId)` and prove
that a cross-tenant cache key collision is impossible — with a test that fetches the
same resource id under two tenants and asserts the results differ.

---

## 15. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: Is a JWT encrypted?**
**A:** No. A standard JWT is a **JWS** — it is *signed*, not encrypted. The header and
payload are base64url-**encoded**, so anyone who can see the token can decode and read
every claim. It is a bearer credential: whoever holds it holds the authority it
carries. That is why no PII goes in the payload.

**Q: What are the main security risks of using JWTs?**
**A:** Five, in order of how often they actually happen. **Algorithm confusion** — the
server reads `alg` from the token and uses the public key as an HMAC secret; the fix is
to pin the algorithm. **Missing claim validation** — no `exp`, or no `aud`, so a token
is accepted forever or by the wrong service. **Token leakage** — in logs, URLs or
`localStorage`. **Privilege escalation via claim mapping** — turning every claim into
an authority. And **confused deputy** — a token minted for one service accepted by
another.

**Q: Why do you validate the `aud` claim?**
**A:** Because a valid token is not automatically valid *here*. If several services
share an issuer, a token minted for `billing-api` is cryptographically genuine but
must not authorise a call to `orders-api`. Checking that your own service id is in the
`aud` list is the confused-deputy defence, and it costs one list scan.

**Q: What does "the payload is readable" mean practically?**
**A:** It means anyone who can see the token can read the claims — including your
reverse proxy, your access logs, your APM traces and the user's own browser. So the
payload may contain identifiers and authorisation facts, but **not** personal data,
secrets or anything you would not put in a support ticket.

**Q: Why is a short access-token lifetime a security control?**
**A:** Because the TTL *is* the revocation window. A signed token cannot be taken back,
so the only automatic way it stops working is by expiring. A 5–15 minute token means a
stolen token is usable for at most that long — unless you add a denylist or a token
version, which cost a lookup per request.

---

### 🟡 Intermediate

**Q: Explain the algorithm-confusion attack.**
**A:** The server publishes an RSA public key at its JWKS endpoint. The attacker crafts
a token with `"alg":"HS256"` and signs it using the **public key's bytes as the HMAC
secret** — which they can do, because the public key is public. If the verifier reads
the algorithm from the token header and dispatches on it, it verifies with
`HMAC(publicKeyBytes, …)`, the forged token matches, and the attacker has minted an
admin token. The defence is to **pin the algorithm in the decoder configuration** so
the token's header never chooses the verification path.

**Q: What is the mass-assignment vulnerability in a JWT system?**
**A:** It is not that the client can change the payload — the signature prevents that.
It is that the server might turn **every** claim into an authority. If the mapping is
"every claim key becomes an upper-cased authority", then a claim named `admin` (emitted
by the issuer for its own reasons) becomes the authority `ADMIN`, and any rule
checking `hasAuthority("ADMIN")` grants it. The defence is an **explicit, named**
mapping: `roles` → `ROLE_*`, `scope` → `SCOPE_*`, everything else ignored.

**Q: How would you prevent token leakage?**
**A:** Four controls. The token travels **only** in the `Authorization` header — never
in a URL (which lands in access logs, browser history and `Referer` headers). Logs and
traces **redact** the header — at the logging framework's level, so no individual log
statement can leak it. The **refresh token** lives in an `HttpOnly; Secure;
SameSite=Strict` cookie, so JavaScript cannot read it. And the access token in a
browser SPA lives in **memory**, not `localStorage`.

**Q: What is a confused deputy, in the JWT context?**
**A:** A service that accepts a token minted for a **different** service. The token is
perfectly genuine and correctly signed — it is simply addressed to somebody else. The
attack is realistic in a platform with many services sharing an issuer: a
low-trust service mints a token with `aud: ["payments-ledger"]` and calls the ledger
service. The defence is to validate `aud` against **your own** service id on every
service, with no exceptions.

**Q: How do you handle a user who is simultaneously logged in on several devices?**
**A:** Each device gets its own **refresh-token family**, so they are independent
sessions with independent revocation. "Log out this device" revokes one family;
"log out everywhere" revokes every family **and** bumps the user's token version —
because the access tokens already issued are stateless and would otherwise remain
valid until they expire.

---

### 🔴 Advanced / Senior

**Q: How do you validate a JWT correctly — the full pipeline?**
**A:** In this order: (1) cap the size, (2) parse the three parts, (3) **pin** the
algorithm and reject anything else — this blocks algorithm confusion and `alg: none`,
(4) resolve the key by `kid` from the cached JWK Set, (5) **verify the signature**,
(6) validate `iss` (exact match), `aud` (contains your service), `typ` if you accept
more than one token type, `exp` (**mandatory**) and `nbf`, with a 60-second skew
leeway, (7) check the revocation state, and only then (8) map the claims to authorities
**explicitly**. Every rejection returns the **same generic error**. The order matters:
the signature is verified before any claim is trusted, and the authority mapping runs
last, on claims already known to be genuine.

**Q: What is DPoP and when would you use it?**
**A:** DPoP (RFC 9449) binds an access token to a **client-held key pair**: the client
signs a proof with its private key on every request, and the server verifies it against
the public key it saw at token issuance. A stolen token is therefore useless without
the private key — which closes the XSS-theft problem that `HttpOnly` cookies only
partially solve. The costs are real: the client must manage a key pair, every request
carries a proof (more bytes), and the server must store the public key per token. I
would use it for high-value APIs; I would not add it to a public read API.

**Q: How do you design the service-to-service flow safely?**
**A:** Use **token exchange** (RFC 8693) rather than passing the user's token through.
Service A exchanges the user's token for a **new** token whose `aud` is the target
service, and whose `act` claim names Service A as the actor. The receiving service
validates three things: the signature, the **`aud`** (the token was minted for *me*),
and the **`act` actor** (it is a known service and it is permitted to delegate for
this subject). It then uses `sub` — the user — for the business decision and `act` for
the audit trail. Without the actor check, anyone holding a valid token can set `act`
and impersonate.

**Q: How do you protect against a DoS via tokens?**
**A:** Four layers. **Cap the size** at the gateway and in Tomcat (8 KB) so a huge token
never reaches the JSON parser. **Verify the signature before the heavy parsing** so an
unverified token cannot cost you a deep parse. **Keep the `kid` space small and
structured** and **rate-limit the JWKS refresh**, because an unknown `kid` triggers a
blocking fetch — a flood of random `kid`s is a 1:1 amplification against your own auth
service. And **rate-limit the API** itself. A megabyte-claim token is a parsing bomb
and a memory-pressure attack in one.

**Q: What is the risk of a leaked signing key, and how do you limit it?**
**A:** Total — the attacker can mint **any** token for **any** audience with **any**
roles, so every resource server that trusts that key is compromised. The limits are:
the key lives in a **KMS/HSM** and every signing operation is audited; the issuer
**never** mints an `admin` role without its own authorisation check; **separate keys
per environment and per partner**, so a leaked key does not cross a boundary; and
**short rotation with an overlap window**, so a compromise is bounded by the rotation
cadence. The resource server's `iss` and `aud` checks also help: a key for
`auth.acme.com` cannot mint a token for `partner.acme.com` if the issuers use
different keys.

---

### ⚫ Expert / Principal

**Q: How would you secure a multi-tenant JWT architecture where one customer's data
must never be visible to another?**
**A:** Four layers, and the first is the one people forget. **Source the tenant from
the validated claim**, never from a header, a sub-domain, a path prefix or a query
parameter — every one of those is client-controlled. Then: **bind the tenant into the
token** (`tenant_id`), signed, so it cannot be edited; **scope every query** to the
tenant, ideally by making the tenant a **mandatory** parameter of the repository layer
so a query without it does not compile; and **separate the tenants cryptographically**
where the isolation requirement demands it — per-tenant signing keys with a `kid` per
tenant, so one tenant's key compromise does not affect the others, at the cost of a
sharded JWKS. Add **per-tenant revocation** (`INCR epoch:tenant:<id>`) so a
compliance-driven tenant-wide revocation is one write. And add the test that matters:
a request with a forged `X-Tenant-Id` must return 403, and a request with **no** header
must return the **token's** tenant's data — proving the header is never a selector.

**Q: Design the security of a service mesh's token system.**
**A:** The mesh is where "internal" stops being a security argument, so the design has
to assume a compromised workload. **Short-lived tokens**: 5 minutes, `ES256` for speed
and small signatures, issued by a central issuer. **Audience per destination**: the
token names the service it is for, so a compromised sidecar cannot call the ledger —
this is the single most important control. **Workload identity**: each workload proves
its identity with a SPIFFE/SPIRE-style credential and gets a token bound to it, so a
stolen token is useless without the workload's key. **Delegation via `act`**: a token
exchanged for a specific target, with the actor recorded and validated, and the chain
bounded to one level. **Introspection only where revocation accuracy matters** — the
ledger service uses opaque tokens with RFC 7662, accepting a lookup per request in
exchange for exact revocation. And **fail closed** on the mesh's critical path: if the
issuer is unreachable, the mesh degrades rather than authorising anything it cannot
verify.

**Q: How do you reason about the residual risk of a JWT-based system to a
non-technical stakeholder?**
**A:** I frame it as **three residual risks with known magnitudes**. First, *a stolen
access token is usable until it expires* — bounded by the TTL, which we chose as a
business decision (5 minutes for the payments API, 10 elsewhere). Second, *a stolen
refresh token is a bigger problem* — which is why it is hashed, rotated, family-tracked
and reuse-detected, and why the blast radius of one stolen refresh token is **one
use**, with the theft *detected* at the next use. Third, *a compromised issuer is
game over* — which is why the key is in a KMS with audited signing, why the issuer
applies its own authorisation, and why we rotate on a fixed cadence. Then I say what I
would do with more budget: DPoP for the mobile app, step-up authentication for
high-value operations, and a per-request authorisation check against the live
authoritative store for anything where the 15-minute staleness is unacceptable. The
point is to make the risk **bounded and named**, not to claim it is zero.

**Q: What is the most dangerous JWT mistake you have seen in production, and what did
you change afterwards?**
**A:** The pattern is always the same: the token was **trusted for something it did
not prove**. In one case it was a `tenant_id` read from a request header, so a user of
one tenant could set the header and read another's invoices — and the header check
passed because the *token* was valid. In another it was an authority mapping that
turned every claim into an authority, so a claim the issuer had added for an unrelated
purpose silently became a privilege. The change afterwards was not a patch — it was a
**rule**: every input that influences an authorisation decision must be either (a)
cryptographically bound to the token, or (b) compared against something the token
proves. And I encoded it as CI checks and as a checklist item that has an owner, because
a rule that lives only in a reviewer's head is gone the moment they leave.

---

## 16. Scenario-Based & Real-Experience Scenarios

### Scenario 1 — "A token minted by service A is accepted by service B"

**The situation.** After the mesh migration, a security researcher demonstrates that a
token issued for `inventory-api` is accepted by `payments-api`. It works because the
payments service validates the signature and `iss` — but never `aud`.

**Your first move.**

```bash
# 1. Confirm: mint a token for one audience and call another service.
TOKEN=$(curl -s -X POST https://auth.acme.com/oauth2/token \
  -H "Authorization: Bearer $SERVICE_A_TOKEN" \
  -d 'grant_type=client_credentials&audience=inventory-api' | jq -r .access_token)
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{iss,aud,sub}'
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  https://payments.acme.com/api/payments/execute

# 2. What does every service actually validate?
grep -rn "JwtClaimValidator\|createDefaultWithIssuer\|aud" --include=*.java services/ | grep -v test

# 3. Is there a shared decoder configuration, or one per service?
grep -rln "JwtDecoder" --include=*.java services/ | wc -l
```

**Expected signal.** `grep` shows **nine** services, and only **three** of them have an
`aud` validator. Each service has its **own** copy of the decoder configuration — they
were copy-pasted and drifted.

**The trap.** Adding the `aud` validator to the three broken services. The other six
will be found by the next penetration test.

**The resolution.**
1. **Extract a shared starter library** (`platform-security`) with one hardened
   `JwtDecoder` and one `JwtAuthenticationConverter`, configured per service via
   properties (`spring.security.oauth2.resourceserver.jwt.audience=payments-api`).
2. **Add a CI test** in each service that mints a token for a *different* audience and
   asserts a **401**.
3. **Add a startup assertion** that the configured audience is non-empty — so a service
   cannot start without it.

**The lesson.** Security configuration that is copy-pasted into nine repositories will
drift. The fix is not a lint rule; it is a **shared library plus a test**.

---

### Scenario 2 — "The signature verification passes but the token is still rejected"

**The situation.** A developer is confused: their code verifies the signature
successfully, yet the request 401s.

**Your first move.**

```bash
# 1. Turn on the OAuth2 logging to see WHICH validator failed.
java -jar app.jar --logging.level.org.springframework.security.oauth2=TRACE \
                   --logging.level.org.springframework.security.oauth2.jwt=DEBUG

# 2. What does the token actually contain?
echo "$TOKEN" | cut -d. -f1 | tr '_-' '/+' | base64 -d 2>/dev/null | jq .
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{iss,aud,exp,nbf,iat}'

# 3. What does THIS service expect?
grep -rn "issuer-uri\|jwk-set-uri\|audience\|THIS_SERVICE" src/main/java src/main/resources
```

**Expected signal.** The TRACE log shows the failure is in
`JwtClaimValidator` for the audience:
`"aud" claim does not contain "payments-api"`. The token's payload has
`"aud": ["orders-api"]` — it was minted for a different service. The signature is fine;
the **addressing** is wrong.

**The trap.** Debugging the key, the algorithm and the clock for twenty minutes,
because "the signature verified" feels like it should mean the token is good.

**The resolution.** The token is addressed to another service. Either the **client** is
calling the wrong URL, or the **issuer** minted the token with the wrong audience
(a configuration bug in the token-exchange flow — the `requested_token_type`/`audience`
parameter was defaulted). Add an explicit `audience` to the exchange request and assert
it in the issuer's tests.

**The lesson.** "The signature verified" and "the token is valid **here**" are
different statements. The second is a claim about **addressing**, and it is a separate
check with a separate failure.

---

### Scenario 3 — "The clocks differ between the issuer and the verifier"

**The situation.** A partner in Frankfurt reports that their tokens work in staging
and fail in production with
`"exp" claim timestamp check failed (exp: ..., now: ...)`. The internal team sees no
problem.

**Your first move.**

```bash
# 1. Compare the clocks — from both sides.
date -u; timedatectl status | grep -i 'synchronized\|NTP'
ssh prod 'date -u; timedatectl status | grep -i synchronized'

# 2. How big is the difference, from the verifier's point of view?
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '.exp, .iat'
```

**Expected signal.** The production API's clock is **2026-01-14 09:14:22 UTC**; the
partner's is **2026-01-14 08:52:07 UTC**. A **22-minute** difference — far beyond the
**60-second** leeway. The API host's NTP service shows
`System clock synchronized: no`.

**The trap.** Raising the leeway to 30 minutes to "make it work". That silently
extends every token's effective life by 30 minutes and masks a broken host.

**The resolution.** Fix the NTP configuration. Keep the leeway at 60 seconds. Add a
**startup assertion** that the system clock is within 5 seconds of a reference source,
and alert on the offset — the drift will otherwise come back.

**The lesson.** Clock skew is an **infrastructure** fault, and the leeway is a
**tolerance**, not a fix. Say the number — 60 seconds — and say why.

---

### Scenario 4 — "The JWKS cache does not refresh after a rotation"

**The situation.** After the issuer rotates its key, tokens with the new `kid` are
rejected by some services but accepted by others. The rejected services are the older
pods.

**Your first move.**

```bash
# 1. Which pods have the stale cache? (rollout state)
kubectl get pods -l app=orders-api -o wide
kubectl describe deployment orders-api | grep -A3 'Image:'

# 2. Does the JWKS itself contain the new key?
curl -s https://auth.acme.com/.well-known/jwks.json | jq '.keys | map(.kid)'

# 3. What does the failing token's kid say?
echo "$TOKEN" | cut -d. -f1 | tr '_-' '/+' | base64 -d 2>/dev/null | jq -r .kid

# 4. Is the fetch failing inside the pod?
kubectl exec deploy/orders-api -- curl -sS -o /dev/null -w '%{http_code}\n' \
  https://auth.acme.com/.well-known/jwks.json
```

**Expected signal.** The JWKS contains `key-2026-02`, and the failing token carries
`kid: "key-2026-02"`. The old pods' caches predate the rotation. But `kubectl exec`
shows the pod can **reach** the JWKS (200) — so the fetch is not failing.

**The trap.** "The pods need a restart." That works, but it treats the symptom.

**The resolution.** The real issue is **the refresh trigger**: the `RemoteJWKSet`
refreshes on an unknown `kid`, but only when the cache policy allows it. In Spring
Security 6, the JWK Set is cached and refreshed on the unknown-`kid` path — but a
`jwk-set-uri` that is unreachable *at that moment* leaves the cache stale. The robust
fix is a **scheduled refresh** (a `Cache` with a TTL on the JWK source, or an
`NimbusJwtDecoder` with a `jwk-set-uri` behind a CDN with a short TTL) plus a
**restart-safe rollout**: publish the new key, wait for the overlap, then roll the
pods. And add an alert on the **unknown-`kid` rate** — it is the leading indicator for
this whole class of problem.

**The lesson.** A cached JWKS means the *rotation* is a **distributed** event: the
issuer must wait for every consumer's cache to refresh, not just for the clock. The
overlap window exists for exactly this reason.

---

## 17. Counter-Questions, Tricky Questions & Follow-Ups

### CQ1. "If the payload is signed, isn't the mass-assignment attack impossible?"

- **Naive answer:** "Yes — the client can't change the signed payload."  ← *true, and it
  describes a different attack*
- **The trap:** The trap is that the attacker's target is not the **token** but the
  **mapping**. The client cannot change the payload — but the *issuer* can, and the
  *server's mapping* decides what the payload means. A blanket mapping turns any
  claim into a privilege, and the issuer may emit a claim named `admin` for entirely
  unrelated reasons.
- **Expert answer:** The signature guarantees **integrity**, not **semantics**. It
  proves "the issuer said this", but it does not say "this is what the issuer meant".
  The mapping is the layer that turns a set of assertions into authority, and it is
  the layer that must be **explicit**. My rule: every claim that becomes an authority
  must appear **by name** in code, and every claim the mapping ignores must be
  irrelevant to authorisation by construction. I would even assert that the set of
  producible authority names is a subset of an explicitly configured allowlist — so a
  claim rename cannot silently change what `hasAuthority("X")` means.

### CQ2. "If you pin the algorithm, why bother with `kid` and a JWKS at all?"

- **Naive answer:** "`kid` is for key rotation."  ← *true, and it misses the deeper role*
- **The trap:** The trap is thinking pinning the algorithm means the key is fixed. It
  is not — the **algorithm** and the **key** are separate decisions. Pinning says "this
  token must be RS256"; the `kid` says "and this token was signed by *that* key".
- **Expert answer:** Pinning the algorithm closes the *algorithm-confusion* class, where
  the attacker chooses the verification method. The `kid` solves a different problem:
  **key selection during rotation**. With a JWKS containing several keys, the `kid`
  lets the verifier pick the right one in one lookup rather than trying all of them.
  Together they are a complete statement of trust: *"a token is acceptable iff it was
  signed with RS256, by the key whose id is in my published set."* Removing either
  half breaks something — remove the pinning and the attacker chooses the algorithm;
  remove the `kid` and rotation becomes ambiguous and slow.

### CQ3. "You've said 'never put a token in a URL'. What's the actual attack?"

- **Naive answer:** "It's less secure."  ← *true, and it misses the channels*
- **The trap:** The trap is thinking about the **user**'s risk (history) and missing
  the **server-side** channels. A URL is recorded by *four* independent systems that a
  header is not.
- **Expert answer:** A URL is written to: the **web server's access log** (retained for
  months, read by ops, shipped to a SIEM, and often less protected than the app); the
  **browser history** (recoverable by anyone with the profile, and by a shared
  machine); the **Referer header** sent to every third-party host the page loads an
  asset from — which leaks the token to analytics and ad networks; and **proxy and CDN
  logs** (which are typically aggregated and searchable by anyone on the platform).
  And none of those redact query parameters by default, because query parameters are
  usually not secret. The `Authorization` header avoids all four because headers are
  not logged by default and never travel to third parties. That is why the rule is
  "header only" rather than "be careful with URLs".

### CQ4. "Doesn't `HttpOnly` on the refresh token make the browser app's life harder?"

- **Naive answer:** "Yes, you have to use CSRF protection."  ← *true, and it misses the
  trade*
- **The trap:** The trap is framing it as "convenience versus security". It is a
  trade of **two different threats**: XSS (which reads the token out of the page) and
  CSRF (which rides the browser's automatic cookie attachment). `HttpOnly` closes the
  first completely and the second not at all.
- **Expert answer:** With the refresh token in `HttpOnly`, an XSS can no longer
  *steal* it — but it can still *use* it, by calling `/refresh` from the page and
  reading the response (the new access token comes back in the body). So `HttpOnly`
  is not sufficient on its own — the **access token should be in memory** and the
  response should not be readable by a hostile script... which it is, by definition, if
  the script is on the page. This is why the real answer is layered: `HttpOnly` +
  a **strict CSP** (which is what actually stops the XSS from existing) + in-memory
  access tokens + `SameSite=Strict` + CSRF protection for the cookie endpoints. Each
  layer covers a different threat, and the honest statement to a stakeholder is that
  `HttpOnly` raises the bar but does not replace the CSP.

### CQ5. "How is the audience check different from the issuer check?"

- **Naive answer:** "One checks who made it, the other checks who it's for."  ← *true,
  and it misses the operational difference*
- **The trap:** The trap is treating them as symmetric and interchangeable, and then
  omitting the audience one "because the issuer is already validated". They are **not**
  interchangeable, and the omission is the single most common microservices JWT bug.
- **Expert answer:** The **issuer** check answers "is this from a party I trust?" — and
  it is a **single, global** trust decision, so one trusted issuer means every token
  it mints is potentially acceptable to you. The **audience** check answers "is this
  addressed to *me*?" — and it is what makes **many services sharing one issuer**
  safe. Without it, your trust boundary is your *issuer*, which in a platform with 30
  services is effectively "the whole platform". With it, your trust boundary is
  "tokens that name me", which is per-service. The operational consequence is that the
  audience list must be **complete and accurate** — and a mis-configured audience is
  either a false rejection (an outage) or a false acceptance (a breach).

### CQ6. "Why does `Jwt.getAudience()` return an empty list rather than null when `aud`
is absent?"

- **Naive answer:** "Because Spring normalises it."  ← *true, and it hides a subtlety*
- **The trap:** The trap is that the normalisation makes **absence** and **empty**
  indistinguishable at that level — so a validator written as "if the audience is
  empty, treat it as optional" would accept a token with no `aud` at all.
- **Expert answer:** The normalisation exists because RFC 7519 §4.1.3 permits `aud` to
  be a string **or** an array, and Spring wants one shape for the common case. The
  consequence is that `getAudience()` returns `List.of()` for both "no `aud` claim" and
  "`aud: []`". So a validator of the form `aud -> aud != null && aud.contains(SERVICE)`
  correctly rejects both — the `contains` check on an empty list is false. The danger
  is only for policies where `aud` is *optional*: to distinguish the two cases you must
  read `jwt.getClaims().containsKey("aud")`. The practical rule: **require a non-empty
  audience that contains your service id**, and treat the absence as a rejection.

### CQ7. "Can an attacker just replay a captured access token?"

- **Naive answer:** "No — the signature protects it."  ← *false; the signature protects
  against forgery, not replay*
- **The trap:** The trap is conflating **integrity** with **freshness**. The signature
  proves the token was not modified; it says nothing about whether it was already used.
  A captured token is perfectly valid until it expires.
- **Expert answer:** Yes, within its TTL — that is the **replay window**, and it is
  why the TTL is short. The mitigations, in increasing order of strength: a **short
  TTL** (5–15 minutes) bounds the window; for high-value operations, a **nonce** or an
  **idempotency key** makes the second attempt detectable or harmless; the **revocation**
  mechanisms from chapter 03 (a denylist or a token version) let you kill it early;
  and **DPoP** (RFC 9449) makes the token useless without a client-held private key,
  which closes the window entirely. Note that a `jti` alone does **not** prevent
  replay — it only *enables* detection, and detection needs state, which is exactly
  what statelessness gives up.

### CQ8. "Why is the refresh token usually opaque rather than a JWT?"

- **Naive answer:** "Because it is looked up in the database anyway."  ← *true, and it
  misses the leakage argument*
- **The trap:** The trap is treating the two as equivalent containers and missing that
  an opaque token leaks **nothing**. A JWT refresh token carries `sub`, `tenant_id`,
  `roles` and the family id — all of which a thief learns immediately.
- **Expert answer:** Two reasons. **Revocation**: a JWT is self-verifying, so
  invalidating one requires a denylist — state on the refresh path, which is what you
  were trying to avoid. And **confidentiality of the contents**: an opaque token is a
  random string, so a stolen one tells the attacker nothing except its own value; a
  JWT refresh token hands over the user's identity, tenant and roles for free, which is
  reconnaissance. The one case where a JWT refresh token is right is a **stateless
  refresh service** at very high volume, where you accept the denylist and the leakage
  in exchange for removing the database from the refresh path.

## 18. Frequently Asked Production Issues & Debugging Scenarios

### Issue 1 — A token minted by service A is accepted by service B

**Symptom.** A token with `aud: ["inventory-api"]` authenticates a call to
`payments-api`.

**Diagnostic commands.**

```bash
# 1. Prove it and see the claims.
TOKEN=$(curl -s -X POST https://auth.acme.com/oauth2/token \
  -H "Authorization: Bearer $SERVICE_A_TOKEN" \
  -d 'grant_type=client_credentials&audience=inventory-api' | jq -r .access_token)
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{iss,aud,sub}'
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  https://payments.acme.com/api/payments/execute

# 2. Which services validate the audience?
grep -rln "JwtClaimValidator" --include=*.java services/ | wc -l
grep -rln "JwtDecoder"            --include=*.java services/ | wc -l

# 3. Is there a shared configuration, or one per service?
grep -rn "class .*SecurityConfig" --include=*.java services/ | head
```

**Expected signal.** `9` services define a `JwtDecoder` but only `3` have an
`aud` validator — copy-pasted configuration that **drifted**. The other six are
vulnerable.

**The fix.** Extract a shared hardened starter (`platform-security`) with one
`JwtDecoder` and one `JwtAuthenticationConverter`, parameterised by
`spring.security.oauth2.resourceserver.jwt.audience`. Add a per-service CI test that
mints a token for a **different** audience and asserts a **401**, and a startup
assertion that the audience is configured.

---

### Issue 2 — The signature verification passes but the token is still rejected

**Symptom.** The developer says "the signature is fine" but the API returns 401.

**Diagnostic commands.**

```bash
# 1. Which validator failed?
java -jar app.jar --logging.level.org.springframework.security.oauth2=TRACE

# 2. What does the token carry?
echo "$TOKEN" | cut -d. -f1 | tr '_-' '/+' | base64 -d 2>/dev/null | jq .
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{iss,aud,exp,nbf,iat,typ}'

# 3. What does this service expect?
grep -rn "issuer-uri\|jwk-set-uri\|THIS_SERVICE\|audience" src/main/resources src/main/java
```

**Expected signal.** The TRACE log names
`JwtClaimValidator` for the audience: the token's `aud` is `["orders-api"]` and this
service expects `payments-api`. The signature verified — the **addressing** is wrong.

**The fix.** The token was minted for another service. In a token-exchange flow, the
`audience`/`requested_token_type` parameter was defaulted or omitted; add it explicitly
and assert it in the issuer's tests.

---

### Issue 3 — The clocks differ between the issuer and the verifier

**Symptom.** Partner tokens fail with `"exp" claim timestamp check failed`.

**Diagnostic commands.**

```bash
date -u; timedatectl status | grep -i 'synchronized\|NTP'
ssh prod 'date -u'
# the difference, computed from the token itself:
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{exp,iat}'
```

**Expected signal.** The API host's clock is **22 minutes** ahead of the partner's,
and `timedatectl` reports `System clock synchronized: no`. The 60-second leeway is
irrelevant against a 22-minute gap.

**The fix.** Fix the NTP configuration — **do not** raise the leeway. Add a startup
assertion that the clock is within 5 seconds of a reference source, and alert on the
offset.

---

### Issue 4 — The JWKS cache does not refresh after a rotation

**Symptom.** Some services reject the new tokens; others accept them.

**Diagnostic commands.**

```bash
# 1. Which pods are stale?
kubectl get pods -l app=orders-api -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.spec.containers[0].image}{"\n"}{end}'
kubectl rollout status deployment/orders-api

# 2. Does the JWKS contain the new key?
curl -s https://auth.acme.com/.well-known/jwks.json | jq '.keys | map(.kid)'
echo "$TOKEN" | cut -d. -f1 | tr '_-' '/+' | base64 -d 2>/dev/null | jq -r .kid

# 3. Can the pod reach the JWKS?
kubectl exec deploy/orders-api -- curl -sS -o /dev/null -w '%{http_code}\n' \
  https://auth.acme.com/.well-known/jwks.json

# 4. The unknown-kid rate — the leading indicator.
curl -s localhost:8080/actuator/metrics/jwt.unknown_kid | jq '.measurements'
```

**Expected signal.** The JWKS has `key-2026-02` and the failing token carries
`kid: "key-2026-02"`, but the pod's cache predates the rotation and the pod **can**
reach the endpoint (200) — so the refresh trigger, not connectivity, is the issue. The
`jwt.unknown_kid` metric is spiking.

**The fix.** Make the refresh reliable: a scheduled JWK-cache TTL (or the JWKS behind
a CDN with a short TTL), and rotate in the order **publish → wait → switch → wait →
remove**. Alert on the unknown-`kid` rate — it is the earliest signal of a rotation
problem.

---

### Issue 5 — A privilege escalation is found via a claim mapping

**Symptom.** A user with the claim `"admin": false` is treated as an admin. The
founder has a token whose payload contains `{"admin": true}` — issued by their own
service for a completely different purpose.

**Diagnostic commands.**

```bash
# 1. What authorities are actually granted?
grep -rn "setJwtGrantedAuthoritiesConverter\|getClaims()" --include=*.java src/main/java
grep -rn "hasAuthority\|hasRole" --include=*.java src/main/java

# 2. Decode a live token and list the claims.
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq 'keys'

# 3. Which of those claim names match an authority your rules check?
```

**Expected signal.** The mapping is
`jwt.getClaims().keySet().stream().map(k -> new SimpleGrantedAuthority(k.toUpperCase()))`
and a rule checks `hasAuthority("ADMIN")`. The issuer emits an `admin` boolean for its
own purposes — and it becomes a privilege.

**The fix.** Replace the mapping with an explicit one (`roles` → `ROLE_*`, `scope` →
`SCOPE_*`), and add a regression test that mints a token with
`{"admin": true, "is_superuser": true, "role": "ADMIN"}` and asserts the authority set
contains **none** of them.

---

## 19. Architecture Design Questions

### 19.1 Design the authentication and authorisation architecture for a banking platform

**The prompt.** "A retail bank. 300,000 customers, 40 microservices, a mobile app, a
branch teller system, a partner API and an internal admin console. Payment regulations
require step-up authentication for high-value operations and instant revocation."

**Reference design.**

```text
                        ┌───────────────────────────────────────┐
                        │            AUTH SERVER                │
                        │  POST /oauth2/token (auth code + PKCE,│
                        │             password, refresh,        │
                        │             token-exchange)           │
                        │  KMS: RS256 private key (HSM-backed)  │
                        │  JWKS at /.well-known/jwks.json        │
                        │  Refresh tokens: SHA-256 hashed,      │
                        │    family-tracked, reuse detection    │
                        │  Token version per customer (Redis)    │
                        │  Audit trail → immutable store        │
                        └───────────────┬───────────────────────┘
                                        │ issues
   ┌────────────┬───────────────┬────────┴────────┬───────────────┬────────────┐
   ▼            ▼               ▼                 ▼               ▼            ▼
 MOBILE APP   WEB APP      BRANCH TELLER      PARTNER API    ADMIN        40 APIs
 PKCE         redirect+     device-bound       client_credentials + MFA    aud=svc-*
 10 min       PKCE          + step-up         30 min          5 min       step-up
              10 min                                                          for value>2L
```

**The key decisions and their reasons.**

1. **RS256 (RSA-PSS) with the private key in a KMS** — the bank must be able to prove
   non-repudiation, and an HSM-backed key gives an audit trail for every signature.
2. **5–10 minute access tokens, `aud` per service** — a token for `cards-api` cannot
   call `payments-api`. This is the confused-deputy defence at platform scale.
3. **Step-up authentication for high-value operations** — the token carries
   `auth_time` and `amr`; the payments API checks that `auth_time` is within **5
   minutes** and that `amr` contains a strong factor. No second token, no second
   round trip.
4. **Token version per customer** — the regulatory "log out everywhere" requirement,
   implemented in O(1).
5. **Device binding for the teller system** — a device certificate, plus a short
   session.
6. **The teller system gets a distinct issuer** — the branch network has different
   controls from the mobile app.
7. **A full audit trail** — every authentication event, step-up and high-value
   operation, with the actor, the subject and the device. Immutable, retained for the
   regulatory period.

**The trade-offs the interviewer wants to hear.**

| Decision | Gain | Cost |
|---|---|---|
| KMS/HSM signing | Non-repudiation, audited, key never in the app | ~2–5 ms per signature; an HSM dependency |
| 5-minute admin tokens | A very small revocation window | More refresh traffic; a strict proxy |
| Step-up via `auth_time` | No extra round trip at the API | The issuer must track the factor's freshness |
| Per-service `aud` | A confused deputy cannot cross services | The issuer must know the service list |
| Token version | Instant, O(1) revocation | One Redis GET per request |
| Separate teller issuer | A different control baseline | Two issuers to operate |
| Partner `client_credentials` | Machine identity, no user token | No per-user audit granularity |

**What I would say at the end.** "The bank's risk is not 'can someone log in' — it is
'can someone move money without a fresh factor'. So the architecture puts the
**freshness of the authentication** into the token (`auth_time`, `amr`) and makes the
API check it, rather than making the API call back to the auth server."

---

### 19.2 Design the token architecture for a multi-tenant SaaS with per-tenant isolation

**The prompt.** "4,000 tenants, 800,000 users, 8,000 req/s peak. Each tenant must be
independently rotatable and revocable. A tenant's users must never see another
tenant's data. Some tenants are on a dedicated deployment."

**Reference design.**

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                              ISSUER                                     │
│  Per-tenant key pair (or shared pair + per-tenant kid)                  │
│  kid = "t-<tenantId>-v3"                                               │
│  Token claims: sub, tenant_id, roles, aud=[svc list], ver, exp, jti    │
│  Revocation scopes: ver:user:<id> | epoch:tenant:<id> | denylist:jti  │
└───────────────────────────────┬──────────────────────────────────────────┘
                                │ JWKS (SHARDED + CDN-cached)
        ┌───────────────────────┼───────────────────────┐
        ▼                       ▼                       ▼
   SHARED DEPLOYMENT      DEDICATED DEPLOYMENT    PARTNER TENANTS
   multi-tenant API       single-tenant API       their own IdP
   aud=api-shared         aud=api-<tenant>        aud=partner
   tenant from the CLAIM  tenant fixed at deploy  separate keys entirely
```

**The decisions and their reasons.**

1. **The tenant comes from the validated claim** — never a header, a sub-domain or a
   path prefix. A dedicated deployment simply hard-codes the tenant and *also*
   validates that the claim matches, so the same code is safe in both shapes.
2. **Per-tenant `kid`** — a tenant's key can be rotated or revoked independently. The
   JWKS is **sharded by tenant** and CDN-cached so an API serving 200 tenants fetches
   200 shards, not 4,000 keys.
3. **Three revocation scopes** — user, tenant and token — each one `INCR` or one
   `SETEX`, each O(1).
4. **Dedicated deployments** still validate the tenant from the claim, so a
   misconfiguration cannot leak across tenants.
5. **Every repository query is tenant-scoped by construction** — the tenant is a
   mandatory parameter, not an optional filter.
6. **A dedicated-tenant key** for the highest tier — the isolation is cryptographic,
   not just logical.

**The trade-offs.**

| Decision | Gain | Cost |
|---|---|---|
| Per-tenant keys | Independent rotation and revocation | 4,000 key pairs; a sharded JWKS; KMS cost |
| Shared pair with per-tenant `kid` | One key pair | Rotation is platform-wide |
| Sharded JWKS + CDN | The API fetches only what it needs | Endpoint complexity; a larger CDN key space |
| Three revocation scopes | Fine-grained, O(1) | Three lookups per request |
| Tenant from the claim | Cannot be spoofed | The token's tenant is fixed for its lifetime |
| Dedicated deployment | The strongest isolation | Operational fragmentation |

**What I would say at the end.** "Tenancy has two independent dimensions — **where the
data is** (logical isolation, enforced by the query layer) and **who can mint a token
for it** (cryptographic isolation, enforced by the keys). Most incidents are in the
first dimension; a compromise is in the second. I design for both, and I derive the
tenant from the one artefact that is cryptographically bound to the user."

---

## 20. Rapid-Fire MCQs

**1.** What is the primary defence against the algorithm-confusion attack?
- A) Use a long secret
- B) Encrypt the payload
- C) **Pin the expected algorithm in the decoder configuration**
- D) Validate `aud`

**2.** What does `"alg": "none"` mean?
- A) The token is encrypted
- B) **No digital signature or MAC was performed**
- C) The token uses SHA-256
- D) The token is expired

**3.** Why must the token's payload contain no PII?
- A) It bloats the token
- B) **The payload is encoded, not encrypted — anyone who sees the token can read it**
- C) The JWT spec forbids it
- D) It breaks the signature

**4.** What does an absent `exp` claim mean to a naive validator?
- A) The token is valid forever
- B) The token is invalid
- C) The token expires on issue
- D) The token is malformed

**5.** What is the confused-deputy attack?
- A) Using the wrong signing key
- B) **A token minted for one service being accepted by another**
- C) Replaying a token
- D) Forging the payload

**6.** Why must the algorithm be pinned rather than read from the token?
- A) For performance
- B) **Because reading it lets the attacker choose the verification path (confusion and `none`)**
- C) Because the header is untrusted
- D) To support key rotation

**7.** In a claim→authority mapping, what is the danger of mapping every claim?
- A) Performance
- B) **Any claim the issuer names becomes a privilege**
- C) Token size
- D) It breaks `hasAuthority`

**8.** Where should the tenant id come from in a multi-tenant API?
- A) The `X-Tenant-Id` header
- B) The path prefix
- C) **The validated `tenant_id` claim**
- D) The client's DNS subdomain

**9.** What is the status code for a valid token with insufficient permissions?
- A) 400
- B) 401
- C) **403**
- D) 500

**10.** Why is a JWT refresh token usually opaque?
- A) It is shorter
- B) **It leaks no information if stolen and can be revoked individually**
- C) It is faster to verify
- D) JWTs cannot be stored

**11.** What is `jti` used for?
- A) The issuer
- B) **A unique token id — the handle for per-token revocation**
- C) The audience
- D) The key id

**12.** Which `typ` value marks an OAuth2 access token (RFC 9068)?
- A) `JWT`
- B) **`at+jwt`**
- C) `JOSE`
- D) `refresh`

**13.** What happens if a token's `aud` does not contain your service id?
- A) It is accepted if the `iss` matches
- B) **It is rejected with 401 — the token was addressed to someone else**
- C) It is accepted with reduced privileges
- D) It is redirected

### Answer Key

| # | Answer | One-line reason |
|---|--------|-----------------|
| 1 | **C** | The token must never choose the verification path. |
| 2 | **B** | RFC 7515 §4.1.10: `none` means no signature or MAC was applied. |
| 3 | **B** | base64url is encoding — the payload is public to anyone who sees the token. |
| 4 | **A** | There is no `exp` to fail the check, so the token never dies. |
| 5 | **B** | The token is genuine but addressed to a different service. |
| 6 | **B** | Reading `alg` is precisely what enables confusion and `none`. |
| 7 | **B** | A blanket map turns any claim name into a potential privilege. |
| 8 | **C** | Only the validated claim is cryptographically bound to the user. |
| 9 | **C** | 403 = authenticated but not authorized; 401 = not authenticated. |
| 10 | **B** | Opacity leaks nothing, and a hash on the server allows revocation. |
| 11 | **B** | `jti` is the per-token revocation handle. |
| 12 | **B** | RFC 9068 defines `at+jwt` for JWT-format access tokens. |
| 13 | **B** | A wrong audience means the token was addressed to someone else. |

> 📌 **The three to re-read:** **Q1** (pin the algorithm), **Q7** (the mass-assignment
> trap in the claim mapping) and **Q13** (`aud` is the confused-deputy defence).

---

## 21. Cheat Sheet / One-Page Summary

| Area | Fact |
|---|---|
| **Core model** | A JWT is **untrusted assertions**; your validation pipeline turns them into authorisation |
| **The 5 gates** | Genuine → For me → Still valid → What may it do → Who is behind it |
| **Algorithm confusion** | The server uses the **public key as an HMAC secret**. Defence: **pin the algorithm** |
| **`alg: none`** | No signature at all. Defence: **pin the algorithm** |
| **Missing claim validation** | No `exp` / wrong `iss` / wrong `aud`. Defence: validate all four |
| **Token leakage** | Logs, URLs, `localStorage`. Defence: header only, redact, `HttpOnly` |
| **XSS** | Steals the token. Defence: `HttpOnly` + strict CSP + output encoding |
| **CSRF** | The browser attaches the cookie. Defence: `SameSite=Strict` + CSRF token |
| **Brute force** | A weak *secret*, not a weak algorithm. Defence: ≥256-bit secret, rate limits |
| **Mass assignment** | Every claim → an authority. Defence: **explicit, named mapping** |
| **DoS** | Huge tokens; JWKS cache-miss flood. Defence: size caps, rate limits, verify-first |
| **Confused deputy** | A token for another service. Defence: **validate `aud`** |
| **Replay** | Valid within the TTL. Defence: short TTL, nonce/idempotency key, DPoP |
| **Impersonation** | An unvalidated `act`. Defence: validate the actor + the delegation |
| **Algorithm** | Pin it — `NimbusJwtDecoder.withPublicKey(k).signatureAlgorithm(RS256)` |
| **Validator chain** | `JwtTimestampValidator` + `JwtIssuerValidator` + `aud` + **mandatory `exp`** + `typ` |
| **`exp` leeway** | **60 seconds** |
| **Authority mapping** | `roles` → `ROLE_*`, `scope` → `SCOPE_*`, **everything else ignored** |
| **Tenancy** | From the **validated claim** — never a header, a path or a sub-domain |
| **Payload** | **Public.** Identifiers only — no PII, no secrets |
| **Refresh token** | Opaque, **hashed**, rotated, family-tracked, reuse-detected |
| **Transport** | `Authorization` header (access) · `HttpOnly; Secure; SameSite=Strict` cookie (refresh, browser) · body + keychain (native) |
| **Never** | A token in a URL · a token in `localStorage` · `Authorization` in logs · PII in the payload |
| **Errors** | One generic `invalid_token` for every rejection reason |
| **Status codes** | 401 = not authenticated · 403 = authenticated but not authorised |
| **Service-to-service** | Token exchange (RFC 8693): `sub` = the subject, `act` = the actor, `aud` = the target |

---

## 22. Real-World Production Notes

| Knob / Behaviour | Default | When to change it |
|---|---|---|
| Signature algorithm | `RS256` (or `EdDSA` for new systems) | `ES256` for small tokens / high volume; `HS256` only for a single trusted verifier |
| Algorithm pinning | **Always** | **Never** relax it |
| Clock-skew leeway | 60 s | 10 s if you control both clocks; never above 60 s |
| Access-token TTL | 10 min | 5 min for payments/admin; 30 min only for low-privilege reads |
| `exp` | **Mandatory** | Never optional |
| `aud` | **Mandatory** | Never optional on a multi-service platform |
| `typ` | `at+jwt` | Check it whenever you accept more than one token type |
| Claim count | < 20 | Role IDs, not role objects; resolve at the edge |
| Payload contents | Identifiers only | No PII — ever |
| `jti` | Always (UUID) | Required for per-token revocation |
| Refresh token | Opaque, 256-bit, SHA-256 hashed | Never plaintext |
| Refresh transport (browser) | `HttpOnly; Secure; SameSite=Strict; Path=/api/auth` | `SameSite=None` + `Secure` only for a genuinely cross-site API |
| Refresh transport (native) | Body + OS keychain | — |
| Access token (SPA) | In memory | Never `localStorage` |
| Key size | RSA 2048 / P-256 / Ed25519 | RSA 3072 for regulated environments |
| Key storage | KMS/HSM | Never in the application process |
| Rotation overlap | ≥ max TTL + skew | Derive it from the configuration |
| Rate limit on `/token`, `/refresh` | ~20/min per IP, 10/min per account | Stricter for high-value endpoints |
| Token size cap | 8 KB (Tomcat) | Also cap at the gateway |
| Log redaction | On, framework-level | Never off |
| Step-up window | 5 min (`auth_time`) | Shorter for higher-value operations |
| Revocation fail policy | Fail-closed on writes, fail-open on reads | Explicit, per-service, metriced |
| JWT error message | One generic `invalid_token` | Never distinguish the reason |

**Metrics worth having on a dashboard.**

| Metric | Why |
|---|---|
| `security.oauth2.authorizations` | The 401 rate — the master auth metric |
| `jwt.failures.by_reason` | Signature / claims / audience / version — separates attack from misconfiguration |
| `jwt.unknown_kid.rate` | A rotation in progress, or an attack |
| `jwt.token.size` (p99) | A rising tail means a claim-set leak |
| `jwt.verification.duration` (p99) | Detects an algorithm or key-size regression |
| `jwt.authorities.count` (p99) | Detects a mass-assignment regression |
| `jwks.key.count` | 1 normally, 2 during a rotation |
| `auth.login.count` / `auth.refresh.count` | A rising ratio = a refresh or session bug |
| `auth.refresh.reuse_detected` | Must be ≈ 0 — an incident or a client bug |
| `auth.step_up.count` | High-value operations are being properly re-authenticated |

---

## 23. Follow-up Learning

**Related folders**

- → [01 · JWT Architecture, Statelessness & Signature Algorithms](./01-JWT-Architecture-Statelessness-and-Signature-Algorithms.md)
  — the JOSE family, the registered claims, the algorithm families, the JWKS rotation
  flow.
- → [02 · Access & Refresh Tokens and Token Rotation](./02-Access-Refresh-Tokens-and-Token-Rotation.md)
  — the two-token model, the refresh flow, reuse detection, the device management.
- → [03 · Token Revocation, Storage & Production Design](./03-Token-Revocation-Storage-and-Production-Design.md)
  — the six strategies, the token version, the denylist TTL maths, the rotation runbook.
- [05 · Spring Security](../05-Spring-Security/01-Architecture-and-Security-Filter-Chain.md) — the filter chain, CSRF and
  CORS, password encoding, session management.
- [07 · OAuth2 & OpenID Connect](../07-OAuth2-OpenID-Connect/04-Social-Login-Keycloak-Enterprise-SSO-and-Security.md) — the
  authorization code + PKCE flow, ID tokens, token exchange (RFC 8693), introspection
  (RFC 7662) and DPoP (RFC 9449).
- **12 · System Design for Java Backend** (sibling folder) — gateway design, rate
  limiting and multi-region auth.

**Source pointers (RFCs and Spring Security 6.1)**

| Document | Why read it |
|---|---|
| RFC 8725 — JWT BCP | The six "always validate" rules that summarise the whole chapter |
| RFC 7519 | The claims and their validation semantics |
| RFC 7515 §4.1.10–4.1.11 | `alg: none` and `crit` |
| RFC 9068 — JWT Access Token Profile | `at+jwt`, the standard access-token claims |
| RFC 8693 — Token Exchange | The service-to-service pattern and `act` |
| RFC 9449 — DPoP | Sender-constrained tokens — the strongest answer to token theft |
| RFC 9449 §5 | The proof-of-possession header |
| RFC 9700 — OAuth 2.0 Security BCP | Replay, DPoP and sender-constrained tokens |
| RFC 6750 §3 | The `WWW-Authenticate` header on a 401 |
| Spring Security `NimbusJwtDecoder` | Algorithm pinning and the validator chain |
| Spring Security `JwtAuthenticationConverter` | The claims → authorities mapping |

---

**Next Chapter:** → [07 · OAuth2 & OpenID Connect](../07-OAuth2-OpenID-Connect/README.md) — where JWTs *come from*: the authorization code + PKCE flow, ID tokens, introspection and token exchange.
**Previous Chapter:** ← [03 · Token Revocation, Storage & Production Design](./03-Token-Revocation-Storage-and-Production-Design.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
