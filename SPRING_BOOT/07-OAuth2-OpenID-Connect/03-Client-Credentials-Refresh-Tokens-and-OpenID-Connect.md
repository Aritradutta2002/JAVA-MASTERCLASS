# Client Credentials, Refresh Tokens & OpenID Connect — The Complete Interview Guide

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★☆ ("how does a service call another service without a user?", "what does OIDC add to OAuth?", "why doesn't my API validate the audience?")
> **Prerequisites:** [01 · OAuth2 Architecture, Roles & Grant Types](./01-OAuth2-Architecture-Roles-and-Grant-Types.md) · [02 · Authorization Code & PKCE](./02-Authorization-Code-and-PKCE-Flows.md) · [README](./README.md)
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
- [10. Common Mistakes & Gotchas](#10-mistakes-and-gotchas)
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

- **Client Credentials has no user.** The access token's `sub` is the **application**, the scope is the application's own permissions, and there is **no refresh token** — the client simply asks for a new access token when the old one expires. If you find yourself putting a user's identity in a client-credentials token, you have built a workaround for a missing user-facing grant.
- **The Client Credentials flow is one HTTP POST.** `grant_type=client_credentials` + `scope` + `client_secret_basic` → `{"access_token":"…","expires_in":300,"token_type":"Bearer"}`. No browser, no redirect, no code.
- **Refresh tokens are for *long-lived user sessions*, not for services.** A service can re-mint a token in ~10 ms; a human cannot re-enter a password every 15 minutes. That asymmetry is the entire reason the refresh token exists — and the reason it does not belong in a machine flow.
- **Rotation is the modern default.** Each refresh returns a **new** refresh token and **invalidates the old one**; presenting an already-consumed token is **reuse**, which means it was stolen, and the whole **family** is revoked (chapter 06/02's rotation model).
- **OpenID Connect is the identity layer on top of OAuth 2.0.** It adds exactly five things: the **`openid` scope**, the **ID token**, the **UserInfo endpoint**, the **discovery document**, and **RP-initiated logout**.
- **Without the `openid` scope there is no identity.** The flow is plain OAuth 2.0 and returns **no** ID token — this is the single most common OIDC confusion, and it is why `scope=openid` must be the first thing on your list.
- **The ID token is for the client, never for the API.** Its `aud` is your client. The API's credential is the **access token**. Sending an ID token to your own API is the confused-deputy bug.
- **`issuer-uri` triggers OIDC discovery at startup.** Spring fetches `issuer-uri + /.well-known/openid-configuration`, reads `jwks_uri` from it, and builds the `JwtDecoder` from that. **The application will not start if the authorization server is unreachable** — that is the single most important operational fact in this chapter.
- **`jwk-set-uri` skips discovery.** Point it straight at the JWKS and the app starts even when the authorization server is down — but you then hard-code the key location and lose the automatic issuer validation unless you also set `issuer-uri`.
- **`JwtValidators.createDefaultWithIssuer(issuer)` does NOT validate the audience.** It checks `iss` and the timestamps. Without a `JwtClaimValidator<List<String>>` for `aud`, a token minted for `orders-api` authenticates against `billing-api`.
- **Introspection vs local validation.** Introspection gives **immediate revocation** but costs a network hop on **every** request and makes the authorization server part of your API's availability chain. Local validation costs ~90 µs and survives an auth outage.
- **OIDC logout is a redirect, not a token call.** RP-initiated logout hits `end_session_endpoint` with `id_token_hint` and a **registered** `post_logout_redirect_uri` — and it only logs the user out of the *provider*, not of your application (your session is yours to destroy).

---

## 1. What & Why — The Problem This Solves

**The problem — three gaps in the pure-OAuth story.**

```text
   ❌ WHAT OAUTH 2.0 ALONE DOES NOT SOLVE
   ──────────────────────────────────────────────────────────
   ① SERVICE-TO-SERVICE CALLS HAVE NO USER
      The ledger-service calls the fraud-service 900 times a
      second. There is no end user in that call. The
      authorization-code flow needs one — it starts with a
      browser and a login. So the classic workaround was:
      "fake a user login" with a shared service account and
      a password. That is a shared secret with a session
      cookie and an interactive login. It is awful.

   ② SHORT-LIVED TOKENS BREAK LONG SESSIONS
      An access token must live 5–15 minutes. But a user
      works for 8 hours. Something must let the client keep
      working without asking the user to log in again — and
      without making the access token live for 8 hours.

   ③ THE CLIENT STILL DOES NOT KNOW WHO THE USER IS
      An access token says "this bearer may read orders".
      It says nothing reliable about the human. The client
      needs an identity assertion — signed, audience-bound
      and unambiguous — or it cannot render "Hello, Alice".
```

**The solution — three distinct mechanisms, each solving exactly one gap.**

```text
   ✅ THE THREE ANSWERS
   ──────────────────────────────────────────────────────────

   ① SERVICE-TO-SERVICE → CLIENT CREDENTIALS (RFC 6749 §4.4)
      The client authenticates with ITS OWN credentials and
      receives a token that represents ITSELF. No user, no
      redirect, no session, no refresh token.
         ┌──────────────────────────────────────────────────┐
         │ POST /token                                      │
         │ grant_type=client_credentials&scope=orders:read  │
         │ Authorization: Basic base64(client_id:secret)    │
         │                                                  │
         │ → {"access_token":"…","expires_in":300,          │
         │    "token_type":"Bearer"}                        │
         │                                                  │
         │ The token's `sub` IS the client_id.              │
         └──────────────────────────────────────────────────┘

   ② LONG SESSIONS → THE REFRESH TOKEN GRANT (RFC 6749 §6)
      The client stores a refresh token at login and
      exchanges it for a new access token whenever the
      access token expires. The user never re-authenticates.
         ┌──────────────────────────────────────────────────┐
         │ POST /token                                      │
         │ grant_type=refresh_token&refresh_token=…        │
         │                                                  │
         │ → {"access_token":"…","expires_in":300,          │
         │    "refresh_token":"… (a NEW one — rotation)"}  │
         └──────────────────────────────────────────────────┘

   ③ IDENTITY → OPENID CONNECT 1.0
      Add the `openid` scope, and the authorization server
      additionally returns an ID TOKEN — a signed JWT whose
      `aud` is YOUR CLIENT and whose claims describe the
      user. Plus a UserInfo endpoint, a discovery document
      and RP-initiated logout.
         ┌──────────────────────────────────────────────────┐
         │ { "access_token":"…",                            │
         │   "refresh_token":"…",                           │
         │   "id_token":"eyJhbGciOiJSUzI1NiJ9…" }          │
         │          └─ iss, sub, aud, exp, iat, auth_time, │
         │             nonce, email, email_verified, name   │
         └──────────────────────────────────────────────────┘
```

**The analogy — three different keys to three different doors.**

- The **access token** is a **day pass** to a building: it gets you in now, for the rooms it
  names, and it expires at 6 pm.
- The **refresh token** is the **monthly parking pass** you keep in your wallet: you do not
  show it at the gate, you use it at the office to renew your day passes.
- The **ID token** is the **staff badge**: it says who you are, it is photo-checked, and it is
  only ever shown to **reception** — never to the people guarding the floors.

The mistake everybody makes is showing the **badge** to the floor guard, or the **day pass**
to reception. The audience is what matters, and the `aud` claim is the machine-readable
statement of "who is this credential for".

**What breaks without each?**

| Missing | The break |
|---|---|
| No client credentials | Service-to-service calls need a fake user session, a shared password and a login page — and a breach of that shared password compromises every service |
| No refresh token | Either the access token lives for 8 hours (huge blast radius, no revocation) or the user logs in every 15 minutes |
| No OIDC | The client either calls a custom `/me` endpoint you had to build, or it parses an access token's claims and hopes — which is exactly the confused-deputy risk |

> 💡 **The interview framing.** "How does one service call another without a user?" The
> answer is: **the token represents the application, not the person.** The service authenticates
> with its own credentials at the token endpoint, receives a token whose subject is the service
> itself, and presents that token to the downstream API — which validates the audience and the
> scope, not a user. And if you need the *user's* authority downstream, that is the
> authorization-code + refresh flow (chapter 02) or **token exchange** (chapter 01), not
> client credentials.

---

## 2. Mental Model — The One Picture

```text
   THREE FLOWS, THREE SUBJECTS
   ═══════════════════════════════════════════════════════════════════

   ┌────────────────────────────────────────────────────────────────────┐
   │  ① CLIENT CREDENTIALS — the subject is the APPLICATION              │
   │                                                                    │
   │   ledger-service ──POST /token──► AS                               │
   │   (client_id + secret)           │                                │
   │                                 ▼                                │
   │                  token: sub="ledger-service"                      │
   │                        scope="orders:read"                       │
   │                        aud=["orders-api"]                         │
   │                                                                    │
   │   ledger-service ──Bearer token──► orders-api                     │
   │                      (NO USER ANYWHERE IN THE PICTURE)           │
   └────────────────────────────────────────────────────────────────────┘

   ┌────────────────────────────────────────────────────────────────────┐
   │  ② REFRESH TOKEN — the subject is still the USER                   │
   │                                                                    │
   │   web-app ──login──► access_token (5 min) + refresh_token (30 d)  │
   │      │                                                             │
   │      │  every 5 minutes, silently:                                 │
   │      ▼                                                             │
   │   POST /token  grant_type=refresh_token&refresh_token=…            │
   │      │                                                             │
   │      ▼                                                             │
   │   access_token (5 min) + refresh_token (NEW — rotation!)           │
   │      │                                                             │
   │      ▼                                                             │
   │   GET /api/orders   Authorization: Bearer <new access token>       │
   └────────────────────────────────────────────────────────────────────┘

   ┌────────────────────────────────────────────────────────────────────┐
   │  ③ OPENID CONNECT — adds the IDENTITY                              │
   │                                                                    │
   │   web-app ◄── id_token (JWT, aud = THE CLIENT) ── AS              │
   │      │                                                             │
   │      │  NEVER send this to an API.                                │
   │      ▼                                                             │
   │   GET /api/orders   Authorization: Bearer <ACCESS token>           │
   │                      (aud = THE API)                              │
   └────────────────────────────────────────────────────────────────────┘

   ★ THE AUDIENCE IS THE WHOLE STORY
   ────────────────────────────────────────────────────────────────────
   ┌───────────────┬────────────────────┬──────────────────────────────┐
   │ TOKEN         │ ITS aud IS         │ IT MAY BE SENT TO           │
   ├───────────────┼────────────────────┼──────────────────────────────┤
   │ access token  │ the resource       │ THE API                     │
   │ id token      │ your client        │ YOUR CLIENT (never the API) │
   │ refresh token │ (usually absent)   │ the token endpoint only     │
   └───────────────┴────────────────────┴──────────────────────────────┘
```

**What to look at:** the three flows differ in exactly one dimension — **who the token is
about**. Client credentials: the application. Refresh: still the user, with a longer-lived
credential to keep that session alive. OIDC: the user, with a **separate, audience-bound
assertion** that says who they are. Every "which token do I send?" confusion in a codebase
comes from not having this picture in your head.

---

## 3. Deep Dive — Theory (Beginner → Advanced)

### 3.1 Beginner Level

**The Client Credentials grant (RFC 6749 §4.4) — the exact wire format.**

```text
   POST https://auth.acme.com/realms/acme/protocol/openid-connect/token
   Authorization: Basic bGVhZGVyLXNlcnZpY2U6czNjcjN0LXZhbHVl
   Content-Type: application/x-www-form-urlencoded

   grant_type=client_credentials&scope=orders%3Aread
```

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIjoi…",
  "token_type": "Bearer",
  "expires_in": 300,
  "scope": "orders:read"
}
```

**Four things that are true and that people get wrong:**

| Fact | Consequence |
|---|---|
| The `sub` is the **client id** | A client-credentials token has **no user** in it. If you need a user, this is the wrong grant. |
| There is **no refresh token** | There is no user session to preserve. The client requests a new access token when the old one expires. |
| The scope is the **application's** permissions | `orders:read` here means "the `reporting-worker` application may read orders", not "the current user may read orders". |
| The client **must** authenticate | It is a confidential client by definition — a public client cannot use this grant. |

**The Refresh Token grant (RFC 6749 §6) — the exact wire format.**

```text
   POST https://auth.acme.com/realms/acme/protocol/openid-connect/token
   Authorization: Basic bGVhZGVyLXNlcnZpY2U6czNjcjN0LXZhbHVl
   Content-Type: application/x-www-form-urlencoded

   grant_type=refresh_token&refresh_token=1%2F%2F0eXa7BdQ…
```

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs…",
  "token_type": "Bearer",
  "expires_in": 300,
  "refresh_token": "1%2F%2F0eXa7BdR…",
  "scope": "openid profile orders:read"
}
```

**The two refresh policies.**

| Policy | How it works | Trade-off |
|---|---|---|
| **Non-rotating** | The same refresh token is returned every time | Simpler, but a stolen refresh token stays valid until it expires — and there is no way to detect the theft |
| **Rotating (recommended)** | Each refresh returns a **new** refresh token and invalidates the old one | A stolen token is used **once** and detected on the second use; the cost is state on the server and a race if two devices refresh simultaneously |

**Rotation with reuse detection** — the mechanism, because it is the most important
security pattern in this chapter:

```text
   ┌───────────────────────────────────────────────────────────────────┐
   │  SERVER STATE: a refresh-token FAMILY                             │
   │                                                                   │
   │  family_id = 7f3a…        (one per login)                        │
   │    ├── token A (hash)  issued 10:00   CONSUMED at 10:05          │
   │    ├── token B (hash)  issued 10:05   CONSUMED at 10:10          │
   │    └── token C (hash)  issued 10:10   ← current                  │
   │  revoked_at = null                                             │
   └───────────────────────────────────────────────────────────────────┘

   USE OF TOKEN B (already consumed)  →  REUSE DETECTED
     → revoke the ENTIRE family (A, B, C)
     → force a fresh login
     → log a security event

   USE OF TOKEN C (current)  →  issue D, mark C consumed
```

**Absolute vs relative lifetime.**

| Concept | Meaning | Example |
|---|---|---|
| **Relative / sliding** | The expiry is extended each time the token is used | A refresh token valid for 30 days of *inactivity* |
| **Absolute / hard** | The expiry is fixed at issuance and never extends | A refresh token that dies 30 days after login, no matter how active the user is |
| **Both** | The effective expiry is `min(absolute, relative)` | 30 days hard, 7 days of inactivity |

The production recommendation is **both**: an absolute cap (so an old session eventually
dies) and a relative window (so active users are not logged out). Google calls these
"offline access" and exposes them through the `offline_access` scope; Keycloak exposes them
as the token lifespans plus the non-standard **`refresh_token_expires_in`** field in the token
response.

**OpenID Connect 1.0 — the identity layer.**

```text
   OIDC = OAuth 2.0 + FIVE THINGS
   ────────────────────────────────────────────────────────────
   ① THE `openid` SCOPE
      The literal string "openid" in the scope request. It is
      what switches the flow from "OAuth" to "OpenID Connect"
      and it is what causes an ID token to be issued.
      WITHOUT IT: no ID token, no identity, plain OAuth 2.0.

   ② THE ID TOKEN
      A JWT signed by the authorization server, whose aud is
      YOUR CLIENT. It is an ASSERTION OF IDENTITY, not a
      credential for your API.

   ③ THE USERINFO ENDPOINT
      POST/GET with the access token → the user's claims,
      as JSON. Used when you need claims the ID token does
      not carry.

   ④ THE DISCOVERY DOCUMENT
      /.well-known/openid-configuration → the whole endpoint
      set, so a client can be configured with ONE url.

   ⑤ RP-INITIATED LOGOUT
      end_session_endpoint + id_token_hint + a registered
      post_logout_redirect_uri.
```

**The ID token's claims.**

```json
{
  "iss": "https://idp.acme.com/realms/acme",
  "sub": "9f8c7b6a-…",
  "aud": "orders-web",
  "exp": 1745000000,
  "iat": 1744999900,
  "auth_time": 1744999000,
  "nonce": "b7e1c4a9",
  "at_hash": "77QmUPtjPfzWtF2AnpK9RQ",
  "email": "alice@acme.com",
  "email_verified": true,
  "name": "Alice Sharma",
  "preferred_username": "alice",
  "given_name": "Alice",
  "family_name": "Sharma",
  "picture": "https://lh3.googleusercontent.com/a/…",
  "locale": "en-IN",
  "updated_at": 1744999000,
  "azp": "orders-web"
}
```

| Claim | Meaning | Notes |
|---|---|---|
| `iss` | The issuer | Must exactly match the configured issuer |
| `sub` | The subject — a stable, per-client **opaque** id | The only claim you should use as the user's key |
| `aud` | The audience — **your client** | A client that receives an ID token for a different client must reject it |
| `exp` / `iat` | Expiry / issued-at | Standard JWT validations |
| `auth_time` | When the user was **actually** authenticated | This is the claim you use to decide whether a step-up is needed |
| `nonce` | The value the client sent in the authorization request | **Must** be verified — this is the ID-token replay defence |
| `at_hash` | A hash of the access token | Lets the client verify that the ID token and the access token belong together (hybrid flow) |
| `c_hash` | A hash of the authorization code | Same purpose for the code |
| `acr` / `amr` | The authentication context / methods | `amr: ["pwd","otp"]` tells you a second factor was used |
| `azp` | The authorized party | The client the token was issued to; must equal your client id |
| `email_verified` | Whether the provider verified the email | **Critical** for account linking — see chapter 04 |
| The profile claims | `name`, `picture`, `given_name`, … | Requested via the `profile` scope |

**The scope → claim mapping — which scope buys you which claims.**

| Scope | Claims it returns |
|---|---|
| `openid` | `sub`, `iss`, `aud`, `exp`, `iat`, `auth_time`, `nonce` — **the mandatory set** |
| `profile` | `name`, `family_name`, `given_name`, `middle_name`, `nickname`, `preferred_username`, `profile`, `picture`, `website`, `gender`, `birthdate`, `zoneinfo`, `locale`, `updated_at` |
| `email` | `email`, `email_verified` |
| `phone` | `phone_number`, `phone_number_verified` |
| `address` | `address` |
| `offline_access` | A **refresh token** (a provider extension — not part of OIDC Core) |

> 💡 **Scope minimisation is a security control.** Google's `profile` scope returns your
> **full name, your picture URL and your locale**. If you only need an email, ask for
> `openid email` — not `openid profile email`. Chapter 04 makes this concrete.

**The UserInfo endpoint.**

```text
   GET https://idp.acme.com/realms/acme/protocol/openid-connect/userinfo
   Authorization: Bearer eyJhbGciOiJSUzI1NiJ9…
```

```json
{
  "sub": "9f8c7b6a-…",
  "name": "Alice Sharma",
  "family_name": "Sharma",
  "given_name": "Alice",
  "preferred_username": "alice",
  "email": "alice@acme.com",
  "email_verified": true
}
```

The `sub` in the UserInfo response **must** be identical to the `sub` in the ID token — that
is how the client binds the two together.

**RP-initiated logout.**

```text
   GET https://idp.acme.com/realms/acme/protocol/openid-connect/logout
       ?id_token_hint=eyJhbGciOiJSUzI1NiJ9…
       &post_logout_redirect_uri=https%3A%2F%2Fapp.acme.com%2Fgoodbye
       &state=6f3a9c2b…
```

Three things to know:

1. `id_token_hint` tells the AS **which session** to end — without it, the AS may just show
   a confirmation screen.
2. `post_logout_redirect_uri` **must be pre-registered**; it is not a free-form parameter.
3. It logs the user out of the **provider**. Your application's own session is **yours** to
   destroy — you must invalidate it yourself, or the user stays logged into *your* app.

### 3.2 Intermediate Level

**The discovery document — what is actually in it.**

```json
{
  "issuer": "https://idp.acme.com/realms/acme",
  "authorization_endpoint": "https://idp.acme.com/realms/acme/protocol/openid-connect/auth",
  "token_endpoint": "https://idp.acme.com/realms/acme/protocol/openid-connect/token",
  "userinfo_endpoint": "https://idp.acme.com/realms/acme/protocol/openid-connect/userinfo",
  "end_session_endpoint": "https://idp.acme.com/realms/acme/protocol/openid-connect/logout",
  "jwks_uri": "https://idp.acme.com/realms/acme/protocol/openid-connect/certs",
  "introspection_endpoint": "https://idp.acme.com/realms/acme/protocol/openid-connect/token/introspect",
  "revocation_endpoint": "https://idp.acme.com/realms/acme/protocol/openid-connect/revoke",
  "device_authorization_endpoint": "https://idp.acme.com/realms/acme/protocol/openid-connect/device/auth",
  "grant_types_supported": ["authorization_code", "client_credentials", "refresh_token", "urn:ietf:params:oauth:grant-type:device_code"],
  "response_types_supported": ["code", "none", "id_token", "token", "code id_token"],
  "subject_types_supported": ["public", "pairwise"],
  "id_token_signing_alg_values_supported": ["RS256", "ES256", "PS256"],
  "scopes_supported": ["openid", "profile", "email", "offline_access", "orders:read"],
  "token_endpoint_auth_methods_supported": ["client_secret_basic", "client_secret_post", "private_key_jwt"],
  "claims_supported": ["sub", "iss", "aud", "exp", "iat", "auth_time", "nonce", "name", "email", "email_verified"],
  "code_challenge_methods_supported": ["S256", "plain"],
  "grant_types_supported": ["authorization_code", "client_credentials", "refresh_token"]
}
```

**The fields that matter to a developer.**

| Field | Used by | Why it matters |
|---|---|---|
| `issuer` | The resource server's `issuer-uri` validation | **Must** match the `iss` claim exactly, trailing slash included |
| `jwks_uri` | The resource server's key fetching | Discovered automatically when you use `issuer-uri` |
| `token_endpoint` | The client | The back-channel call |
| `userinfo_endpoint` | The client | The OIDC identity claims |
| `end_session_endpoint` | The client | RP-initiated logout |
| `id_token_signing_alg_values_supported` | The client | Which `alg` you may pin |
| `token_endpoint_auth_methods_supported` | The client | Whether `client_secret_basic` or `private_key_jwt` |
| `code_challenge_methods_supported` | The client | Whether `S256` is available (it should be) |

**Introspection (RFC 7662) — the request and the response.**

```text
   POST https://auth.acme.com/realms/acme/protocol/openid-connect/token/introspect
   Authorization: Basic bGVhZGVyLXNlcnZpY2U6czNjcjN0LXZhbHVl
   Content-Type: application/x-www-form-urlencoded

   token=eyJhbGciOiJSUzI1NiJ9…&token_type_hint=access_token
```

```json
{
  "active": true,
  "scope": "orders:read",
  "client_id": "ledger-service",
  "username": "service-account-ledger",
  "token_type": "Bearer",
  "exp": 1745000000,
  "iat": 1744999700,
  "nbf": 1744999700,
  "sub": "ledger-service",
  "aud": ["orders-api"],
  "iss": "https://idp.acme.com/realms/acme",
  "jti": "b7c1…"
}
```

`"active": false` is returned for an expired, revoked or unknown token — and it is the **only**
field that matters for the decision. An introspection-based resource server **must** treat a
network error as a failure, not as "inactive" — and that decision is a security choice
(fail-closed vs fail-open).

### 3.3 Advanced Level

**The Spring resource server — the two configuration modes and what each does at startup.**

| Property | What Spring does at startup | Network call? | Starts if the AS is down? |
|---|---|---|---|
| `issuer-uri` | **OIDC discovery** → reads `jwks_uri` → builds `NimbusJwtDecoder` → sets `JwtValidators.createDefaultWithIssuer(issuer)` | ✅ Yes, 2 calls (metadata + JWKS) | ❌ **No** |
| `jwk-set-uri` | Builds `NimbusJwtDecoder` directly against the JWKS. **No discovery.** Sets the issuer validator **only if** `issuer-uri` is also configured | ✅ One call (the JWKS), cached | ✅ **Yes** |

```yaml
# MODE A — discovery (the default choice)
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://idp.acme.com/realms/acme

# MODE B — direct, with the issuer still validated
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://idp.acme.com/realms/acme
          jwk-set-uri: https://idp.acme.com/realms/acme/protocol/openid-connect/certs
```

> 📌 **A nuance that surprises people.** Setting **both** does **not** trigger discovery in
> Spring Boot — the `jwk-set-uri` wins for key resolution, and the `issuer-uri` is used to
> configure the issuer validator. So "both" is the **availability-safe** configuration: the app
> starts without the AS, and it still validates `iss`. That is the recommended production
> setting for a resource server that must survive an auth outage.

**`JwtDecoder` variants — the actual API surface.**

```java
// 1. From a JWK Set URI — no discovery.
NimbusJwtDecoder decoder = NimbusJwtDecoder
        .withJwkSetUri("https://idp.acme.com/realms/acme/protocol/openid-connect/certs")
        .jwsAlgorithm(SignatureAlgorithm.RS256)     // PIN the algorithm
        .build();

// 2. From the issuer — performs discovery.
JwtDecoder decoder = JwtDecoders.fromIssuerLocation("https://idp.acme.com/realms/acme");

// 3. From a public key you already have.
NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaPublicKey)
        .signatureAlgorithm(SignatureAlgorithm.RS256)
        .build();

// 4. From a symmetric secret (HS256) — only for a single trusted verifier.
NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
```

**Opaque tokens and introspection.**

```java
// Opaque (non-JWT) access tokens require the resource server to ask the AS.
@Bean
SecurityFilterChain chain(HttpSecurity http) throws Exception {
    http.oauth2ResourceServer(oauth2 -> oauth2
            .opaqueToken(opaque -> opaque
                    .introspectionUri("https://idp.acme.com/realms/acme/protocol/openid-connect/token/introspect")
                    .clientId("orders-api")
                    .clientSecret("{noop}placeholder")));
    return http.build();
}
```

| Option | Note |
|---|---|
| `NimbusOpaqueTokenIntrospector` | The current, Nimbus-backed implementation |
| `SpringOpaqueTokenIntrospector` | The older implementation — **deprecated in Spring Security 6.4**; migrate |
| `IntrospectionClient` | The newer **client-side** introspection API added in **Spring Security 6.5** — a first-class OAuth2 client for the introspection endpoint |

**`JwtAuthenticationConverter` and the scope → authority mapping.**

```java
@Bean
JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    // WHY: the default is "SCOPE_". ".hasRole("ADMIN")" expects "ROLE_".
    authorities.setAuthorityPrefix("ROLE_");
    // WHY: the default reads "scope" OR "scp" — pin it explicitly.
    authorities.setAuthoritiesClaimName("scope");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
}
```

The default `JwtGrantedAuthoritiesConverter` behaviour, stated precisely:

| Behaviour | Detail |
|---|---|
| Claim read | `"scope"` if present, otherwise `"scp"` |
| Value split on | Whitespace (space) — RFC 6749 §3.3 uses a space-separated list |
| Prefix | `"SCOPE_"` |
| Empty scope | The string `"null"` is skipped if the claim is literally the string `"null"` |

That last one is a real edge case: some providers send `"scope": "null"` for a token with no
scopes, and Spring's converter explicitly skips it rather than creating the authority
`SCOPE_null`.

**The audience validator — the line everyone forgets.**

```java
// WHY: createDefaultWithIssuer validates iss + timestamps ONLY. It does NOT validate aud.
OAuth2TokenValidator<Jwt> issuerAndTimestamps =
        JwtValidators.createDefaultWithIssuer("https://idp.acme.com/realms/acme");

// WHY: without this, a token minted for "billing-api" authenticates against "orders-api".
OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
        JwtClaimNames.AUD,
        aud -> aud != null && aud.contains("orders-api"));

decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(issuerAndTimestamps, audience));
```

`JwtValidators.createDefaultWithIssuer(issuer)` is exactly:

```text
   DelegatingOAuth2TokenValidator(
       new JwtTimestampValidator(Duration.ofSeconds(60)),   // exp + nbf, 60 s skew
       new JwtIssuerValidator(issuer)                       // iss == issuer, exactly
   )
   ★ NO AUDIENCE VALIDATOR
```

**How to combine validators without losing the defaults.**

```java
// WRONG: replaces the default validators entirely.
decoder.setJwtValidator(new JwtClaimValidator<>(JwtClaimNames.AUD, aud -> true));

// RIGHT: compose.
decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer),
        new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains("orders-api"))));
```

**The `SecurityContext` for a resource server.** After a successful validation:

```text
   JwtAuthenticationToken
     ├─ getToken()        → the Jwt (all claims)
     ├─ getName()         → jwt.getSubject()      (the `sub`)
     └─ getAuthorities()  → from JwtAuthenticationConverter
```

Note that the principal name is the **`sub`**, and for a client-credentials token the `sub`
is the **application**. A resource server serving both user tokens and service tokens will
therefore see principals that look like `9f8c7b6a-…` and `ledger-service` side by side — and
your authorization rules must distinguish them.

**Introspection-based configuration in Boot.**

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        opaquetoken:
          introspection-uri: https://idp.acme.com/realms/acme/protocol/openid-connect/token/introspect
          client-id: orders-api
          client-secret: ${ORDERS_API_INTROSPECTION_SECRET}
```

### 3.4 Expert Level

**The trust topologies — one AS, many resource servers, and the multi-issuer problem.**

```text
   TOPOLOGY A — ONE ISSUER (the default)
   ──────────────────────────────────────
   AS (issuer=https://idp.acme.com/realms/acme)
     └── 12 resource servers, each with issuer-uri = the same value
   Every service validates iss + aud. One key set, one rotation.

   TOPOLOGY B — MULTIPLE ISSUERS IN ONE FLEET
   ──────────────────────────────────────────────
   AS-A (issuer=https://idp.acme.com/realms/employees)
   AS-B (issuer=https://partner.okta.com/oauth2/default)
   A gateway normalises them into ONE internal issuer:
       https://internal.acme.com/identity
   → the internal issuer re-signs the claims with its own key
   → downstream services validate ONE issuer and ONE key set
   → the partner's `iss` never reaches the internal APIs
```

Topology B is what large enterprises converge on, because it means: one trust anchor, one
rotation runbook, one `aud` convention, and **no dependency on a partner's availability or
key hygiene**.

**Opaque vs JWT — the real decision criteria.**

| Criterion | JWT (self-contained) | Opaque (introspected) |
|---|---|---|
| Latency per request | **~90 µs** | **5–40 ms** |
| AS availability coupling | None after the JWKS is cached | **Total** — the API is down without the AS |
| Revocation | At expiry only | **Immediate** |
| AS throughput requirement | ~1 request per key rotation | **1 request per API request** |
| Token size | 1–3 KB | ~40 chars |
| Claim agility | Requires a **new token** to change a claim | The AS answers with **live** data |
| Audit richness | Only what was in the token at issuance | Live state (is the user still banned? is the grant still active?) |
| Good for | High-volume public APIs, microservices | High-value APIs needing live policy |

**The middle ground people miss: a hybrid.** Use a **JWT access token** for the hot path and
a **short-lived, server-side "session state"** (a Redis record) consulted only for
**high-value endpoints**. Most endpoints pay 90 µs; the payment endpoint pays a Redis lookup.
That is a **per-endpoint** decision, and it is the answer to "how do I get instant revocation
without putting the AS on the hot path".

**Sender-constrained tokens for a resource server.** A bearer token is replayable by anyone
who copies it. Two mechanisms bind the token to a key:

```text
   mTLS (RFC 8705)
     the token carries  cnf: { "x5t#S256": "<thumbprint>" }
     the AS binds the token to the client's TLS certificate
     the RS checks that the presenting certificate's thumbprint matches

   DPoP (RFC 9449)
     the token carries  cnf: { "jkt": "<JWK thumbprint>" }
     every request carries a DPoP header: a JWT signed by the
     client's key, containing htu (the URL) and htm (the method)
     the RS checks the proof's key thumbprint against cnf.jkt
```

Both change the RS's decision from *"is this token valid?"* to *"is this token valid **and**
presented by the key it is bound to?"*.

**The `aud` problem in a multi-tenant SaaS.** A single API serving 12,000 workspaces might
issue tokens with `aud = ["acme-api"]` for every tenant — and then the tenant boundary must
come from a **claim** (`tenant_id`), not from the audience. That is the correct design, but it
means the audience validator must be *per-service*, not *per-tenant*, and the tenant check is
a **separate, mandatory** validator:

```java
OAuth2TokenValidator<Jwt> tenant = new JwtClaimValidator<String>(
        "tenant_id", tenantId -> tenantId != null && tenantId.equals(currentTenantId));
```

**Token exchange for service-to-service (RFC 8693).** When `ledger-service` needs
`fraud-service` to act on behalf of a **user**, client credentials are not enough — they
prove the *service*, not the *user*. The correct pattern is:

```text
   1. ledger-service holds the USER's access token (from the authorization-code flow)
   2. it exchanges it at /token for a token scoped to fraud-service:
        grant_type=urn:ietf:params:oauth:grant-type:token-exchange
        subject_token=<the user's token>
        subject_token_type=urn:ietf:params:oauth:token-type:access_token
        audience=https://fraud-service.acme.com
        client_id=ledger-service&client_secret=…
   3. the AS checks that the user's token is valid AND that the subject's scopes cover
      what fraud-service needs, then issues a NARROWER token
   4. the new token carries an `act` claim naming ledger-service as the acting party
```

**FAPI 2.0** (the financial-grade profile) adds, on top of everything above: mandatory PKCE,
sender-constrained tokens (mTLS or DPoP), **PAR**, exact redirect matching, no implicit, and
refresh tokens that are sender-constrained or rotated.

**Refresh-token rotation across a fleet — the concurrency problem.** With rotation, two
concurrent refreshes with the same token are a **race**: one wins, the other looks like
**reuse** and revokes the family. The solutions:

| Approach | How | Trade-off |
|---|---|---|
| **Short grace window** | A consumed token is accepted for **N seconds** and returns the **same** new token | Simple; a real thief inside the window gets through |
| **Idempotency key** | The client sends a unique key per refresh; the server returns the cached result | Clean; needs a cache and a client change |
| **Family with a "latest wins" counter** | Each refresh increments a counter; a token with a counter below the latest-1 is reuse | Needs state; more complex |
| **Serialize per family** | A lock per family id | Simple; a throughput bottleneck |

The production answer is usually the **short grace window (10–30 s)** combined with **family
revocation**, plus a **client-side mutex** so one device never refreshes twice concurrently.

---

## 4. Internal Working Mechanism

### 4.1 Resource-server startup with `issuer-uri` — the exact sequence

```text
   ApplicationContext refresh
     └─▶ OAuth2ResourceServerAutoConfiguration
           └─▶ OAuth2ResourceServerJwtConfiguration.JwtDecoderConfiguration
                 └─▶ jwtDecoderByIssuerUri()
                       ├─ JwtDecoders.fromIssuerLocation(issuer)
                       │     ├─ HTTP GET  {issuer}/.well-known/openid-configuration
                       │     │     ├─ 200 → parse the metadata
                       │     │     └─ 404 → try {issuer}/.well-known/oauth-authorization-server
                       │     │           (RFC 8414 fallback — RFC 8414 §3.1)
                       │     ├─ read jwks_uri, issuer, id_token_signing_alg_values_supported
                       │     └─ NimbusJwtDecoder.withJwkSetUri(jwks_uri)…
                       │           → a RemoteJWKSet that fetches and CACHES the JWKS
                       └─ set the validator: JwtValidators.createDefaultWithIssuer(issuer)
                                                 ★ NO AUDIENCE VALIDATOR
     └─▶ OAuth2ResourceServerSecurityConfiguration
           └─▶ OAuth2ResourceServerConfigurer
                 ├─▶ BearerTokenAuthenticationFilter
                 │     └─▶ DefaultBearerTokenResolver
                 │           (reads "Authorization: Bearer <token>")
                 └─▶ OAuth2ResourceServerConfigurer.JwtConfigurer
                       ├─▶ BearerTokenAuthenticationProvider
                       │     └─▶ JwtDecoder#decode
                       │           ├─ resolve the key by `kid` from the cached JWKS
                       │           │     └─ on an UNKNOWN kid → refresh the JWKS once
                       │           ├─ verify the signature (pinned algorithm)
                       │           └─ run the OAuth2TokenValidator chain
                       │                 ├─ JwtTimestampValidator (exp, nbf, 60 s skew)
                       │                 ├─ JwtIssuerValidator  (iss)
                       │                 └─ YOUR audience validator
                       └─▶ JwtAuthenticationConverter → authorities
```

**The JWKS cache.** The `RemoteJWKSet` behaves like this:

```text
   ┌─────────────────────────────────────────────────────────────────┐
   │  FIRST CALL                                                  │
   │    → GET jwks_uri → cache the JWK Set (all keys, keyed by kid) │
   │  EVERY CALL                                                 │
   │    → look up the kid in the CACHE → verify → no network       │
   │  UNKNOWN kid                                                 │
   │    → GET jwks_uri again → retry once                          │
   │      └─ still unknown → JwtException → 401                    │
   │  CACHE REFRESH                                              │
   │    → the cached entry expires (the library's own TTL) or the  │
   │      key is not found → refetch                               │
   └─────────────────────────────────────────────────────────────────┘
```

**This is why a key rotation that removes the old key immediately produces a 401 storm** — the
resource server sees an unknown `kid`, refetches, and still does not find it. The rotation
runbook must keep the old key published for **at least the maximum token TTL plus clock skew**.

### 4.2 The introspection chain

```text
   BearerTokenAuthenticationFilter
     └─▶ BearerTokenResolver
     └─▶ BearerTokenAuthenticationProvider
           └─▶ OpaqueTokenIntrospector#introspect(token)
                 ├─▶ NimbusOpaqueTokenIntrospector (or SpringOpaqueTokenIntrospector)
                 │     ├─ POST {introspection_uri}
                 │     │     Authorization: Basic base64(client_id:client_secret)
                 │     │     Content-Type: application/x-www-form-urlencoded
                 │     │     body: token=<the opaque token>
                 │     ├─ parse the JSON response
                 │     ├─ if "active" != true → throw OAuth2InvalidTokenException
                 │     └─ build an OAuth2AuthenticatedPrincipal from the claims
                 └─▶ authorities from the `scope` claim
```

**The critical failure-mode decision.** What happens when the introspection endpoint is
**unreachable**?

| Choice | Behaviour | Consequence |
|---|---|---|
| **Fail closed** (the default) | The exception propagates → 401 | The API cannot authenticate anyone — the AS outage becomes an API outage |
| **Fail open** | Accept the token as valid | An attacker can authenticate by breaking the introspection endpoint |

**Fail closed is correct.** The alternative — failing open — means the *attacker* only has to
block the network path to the AS. That is why introspection is a poor choice for a
high-volume API, and why local validation is the default.

### 4.3 The authorized-client refresh chain

```text
   The client needs a fresh access token
     └─▶ OAuth2AuthorizedClientManager#authorize(request)
           ├─▶ OAuth2AuthorizedClientRepository#loadAuthorizedClient(...)
           │     → found and NOT expired → RETURN (no network call)
           ├─▶ found and EXPIRED → OAuth2AuthorizedClientProvider#authorize(...)
           │     └─▶ RefreshTokenOAuth2AuthorizedClientProvider
           │           ├─ POST /token  grant_type=refresh_token&refresh_token=…
           │           ├─ the response carries a NEW refresh token (rotation)
           │           └─ the new token is saved by the manager
           └─▶ not found → the manager cannot refresh → an exception
                  → in a server-side web app this means the user must log in again
```

**The `OAuth2AuthorizedClientRepository` in a clustered deployment.** The default
`AuthenticatedPrincipalOAuth2AuthorizedClientRepository` stores the grant in the **`HttpSession`**
— which has the same multi-instance problem as the `state` (chapter 02 §18). For a clustered
server-side app, replace it with a **database-backed** `OAuth2AuthorizedClientService`:

```java
@Bean
OAuth2AuthorizedClientService authorizedClientService(DataSource dataSource) {
    // WHY a database: the grant must survive a restart AND be visible to every instance.
    JdbcOAuth2AuthorizedClientService service = new JdbcOAuth2AuthorizedClientService(dataSource);
    // WHY a bounded retention: the rows accumulate otherwise.
    service.setJdbcParameters(Collections.emptyMap());
    return service;
}
```

> ⚠️ **A note on the JDBC variant.** Spring Security's
> `JdbcOAuth2AuthorizedClientService` exists, but the schema is not auto-created — you must
> provide it (the DDL is in the Spring Security reference documentation) and you must decide
> the retention. A simpler, more explicit approach for most teams is a small table with your
> own DAO, because you also want to store the **provider** and the **tenant** alongside the
> token.

---

## 5. Step-by-Step Execution Flow

### 5.1 The Client Credentials flow, step by step

1. The service determines it needs a token — at startup, or lazily on the first call.
2. It POSTs to the token endpoint with `grant_type=client_credentials`, the `scope`, and its
   own credentials in the Basic header.
3. The authorization server authenticates the client (`client_secret_basic`), checks the
   requested scope against the client's **assigned** scopes, and rejects with
   `400 invalid_scope` if it exceeds them.
4. The AS mints an access token: `sub` = the client id, `aud` = the resource server(s), `scope`
   = the granted scope, `exp` = now + TTL.
5. The service caches the token for `expires_in - 60 s`.
6. The service calls the API: `GET /api/orders` with `Authorization: Bearer <token>`.
7. The resource server validates the signature locally, checks `iss`, `exp` **and `aud`**, and
   maps the `scope` to authorities.
8. On the next call after the token expires, the service mints a new one (step 2 again).

### 5.2 The Refresh Token grant with rotation, step by step

1. The client's access token expires (`exp` passes) — or it proactively refreshes at 80% of
   the TTL.
2. The client POSTs `grant_type=refresh_token` with the stored refresh token.
3. The AS validates the refresh token: exists? not revoked? not expired? belongs to this
   client? is the family still active?
4. **If the token was already consumed → REUSE DETECTED.** The AS revokes the whole family,
   returns `400 invalid_grant`, and the client must send the user back through a full login.
5. Otherwise, the AS **consumes** the token, issues a new access token, and — with rotation —
   issues a **new refresh token** and invalidates the old one.
6. The client replaces both tokens atomically and retries the original request **once**.
7. If the refresh fails with `invalid_grant`, the client clears its local session and
   redirects to the login page.

### 5.3 The resource server startup, step by step

1. Spring Boot starts, reads `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
2. `JwtDecoders.fromIssuerLocation(issuer)` issues
   `GET {issuer}/.well-known/openid-configuration`.
3. It parses the metadata and reads `jwks_uri`, `issuer` and the supported algorithms.
4. It builds a `NimbusJwtDecoder` with a `RemoteJWKSet` pointed at `jwks_uri`.
5. The JWKS is fetched and cached (the `kid` → key map).
6. The validator is set to `JwtValidators.createDefaultWithIssuer(issuer)` — **no audience
   check**.
7. The `SecurityFilterChain` is built with `BearerTokenAuthenticationFilter`.
8. The application is ready.

**The failure mode:** if step 2 fails, the application **fails to start** with a
`BeanCreationException` caused by an `IllegalArgumentException` mentioning the issuer URL.
This is the single most common deployment surprise in this area.

### 5.4 The OIDC login with the identity layer, step by step

1. The client redirects the browser to `/authorize` with `scope=openid profile email` and a
   `nonce`.
2. The AS authenticates the user and redirects back with the `code`.
3. The client exchanges the code at `/token` and receives `access_token`, `refresh_token` **and
   `id_token`**.
4. The client **decodes and validates** the ID token: signature against the AS's keys, `iss`,
   `aud` == **your client id**, `exp`, and **`nonce` == what you sent**.
5. The client builds an `OidcUser` from the ID token's claims; if it needs more, it calls the
   UserInfo endpoint.
6. The client stores its own session; the ID token is **not** stored and **not** sent to the
   API.
7. On logout, the client destroys its session and — if the provider supports it — redirects to
   `end_session_endpoint` with `id_token_hint` and `post_logout_redirect_uri`.

---

## 6. Visualizations

### 6.1 The Client Credentials flow (sequence)

```mermaid
sequenceDiagram
    autonumber
    participant S as ledger-service<br/>(client)
    participant AS as Authorization Server
    participant RS as orders-api<br/>(resource server)

    Note over S: there is NO user anywhere in this flow
    S->>S: token expired (or first call)
    S->>AS: POST /token<br/>Authorization: Basic base64(ledger-service:secret)<br/>grant_type=client_credentials<br/>scope=orders:read
    AS->>AS: authenticate the client (client_secret_basic)
    AS->>AS: check "orders:read" ∈ the client's assigned scopes
    alt scope not assigned
        AS-->>S: 400 {"error":"invalid_scope"}
    else ok
        AS-->>S: 200 {"access_token":"eyJ…","expires_in":300,"token_type":"Bearer","scope":"orders:read"}
    end
    S->>S: cache the token for expires_in - 60 s
    S->>RS: GET /api/orders<br/>Authorization: Bearer eyJ…
    RS->>RS: verify signature (JWKS, cached)<br/>validate iss + aud + exp<br/>map scope → authorities
    RS-->>S: 200 OK
```

**What to look at:** the flow has **one** back-channel call and **no browser**. The token's
subject is `ledger-service` — the client itself. And note the `invalid_scope` branch: the
scope check happens at the **authorization server**, before any token is minted.

### 6.2 The Refresh Token grant with rotation (sequence)

```mermaid
sequenceDiagram
    autonumber
    participant C as Web app (client)
    participant AS as Authorization Server
    participant RS as Resource Server

    C->>AS: POST /token<br/>grant_type=refresh_token&refresh_token=<T_B>
    AS->>AS: look up T_B → found, belongs to family 7f3a
    alt T_B was ALREADY CONSUMED
        AS->>AS: ★ REUSE DETECTED → revoke the whole family 7f3a
        AS-->>C: 400 {"error":"invalid_grant"}
        C->>C: clear the session → force a full re-login
    else T_B is current
        AS->>AS: consume T_B (mark it used)
        AS-->>C: 200 {"access_token":"eyJ…","expires_in":300,"refresh_token":"<T_C>"}
        Note right of AS: T_C is NEW — rotation
    end
    C->>C: replace both tokens atomically
    C->>RS: GET /api/orders  Authorization: Bearer <new access token>
    RS-->>C: 200 OK
```

**What to look at:** the **reuse branch** is the whole value of rotation. A stolen refresh
token that is presented twice is detected the second time, and the response is to kill the
**entire family**, not just the one token.

### 6.3 The discovery document (ASCII)

```text
   GET https://idp.acme.com/realms/acme/.well-known/openid-configuration
   ────────────────────────────────────────────────────────────────────────────

   {
     "issuer": "https://idp.acme.com/realms/acme",              ★ THE ANCHOR
        │                                                       every token's `iss`
        │                                                       must equal this EXACTLY
        │                                                       (watch the trailing slash)
        ▼
     "authorization_endpoint": ".../protocol/openid-connect/auth",   ┐
     "token_endpoint":         ".../protocol/openid-connect/token",  │  THE ENDPOINT SET
     "userinfo_endpoint":      ".../protocol/openid-connect/userinfo",│  one URL in your
     "end_session_endpoint":   ".../protocol/openid-connect/logout", │  config replaces all
     "jwks_uri":               ".../protocol/openid-connect/certs",  │  of these
     "introspection_endpoint": ".../token/introspect",               │
     "revocation_endpoint":    ".../token/revoke",                  ┘
                                                                          │
     "grant_types_supported":     ["authorization_code",                │
                                   "client_credentials",                │
                                   "refresh_token",                     │
                                   "urn:ietf:params:oauth:grant-type:device_code"],
                                                                          │
     "id_token_signing_alg_values_supported": ["RS256","ES256","PS256"],│
     "token_endpoint_auth_methods_supported": ["client_secret_basic",   │
                                               "client_secret_post",    │
                                               "private_key_jwt"],      │
     "code_challenge_methods_supported":      ["S256","plain"],         │
     "scopes_supported": ["openid","profile","email","offline_access"],│
     "subject_types_supported": ["public","pairwise"],                 │
     "claims_supported": ["sub","iss","aud","exp","iat","auth_time",    │
                          "nonce","name","email","email_verified"]    │
   }

   ★ WHAT SPRING DOES WITH IT (issuer-uri mode)
   ─────────────────────────────────────────────
   1. fetch this document                      ← the app FAILS TO START if this fails
   2. read "jwks_uri"                          ← the key location
   3. read "issuer"                            ← the iss to validate against
   4. build NimbusJwtDecoder.withJwkSetUri(jwks_uri)
   5. fetch the JWKS and CACHE it
   6. set the validator: createDefaultWithIssuer(issuer)
                                    ★ NO AUDIENCE VALIDATOR — add it yourself
```

**Caption:** the discovery document is the reason you configure **one** URL per provider. It
also explains the startup coupling: the application cannot build its decoder without this
document, so an authorization-server outage at boot time is a **deployment failure**, not a
runtime degradation.

### 6.4 How the resource server discovers the JWKS at startup (flowchart)

```mermaid
flowchart TD
    A["Application starts"] --> B{"Which properties are set?"}

    B -->|"jwk-set-uri ONLY"| C["NimbusJwtDecoder.withJwkSetUri(jwkSetUri)"]
    B -->|"issuer-uri ONLY"| D["JwtDecoders.fromIssuerLocation(issuer)"]
    B -->|"BOTH"| E["withJwkSetUri(jwkSetUri)<br/>+ issuer validator from issuer-uri<br/>★ NO discovery call"]

    D --> F["HTTP GET {issuer}/.well-known/openid-configuration"]
    F --> G{"200 OK?"}
    G -- "YES" --> H["parse the metadata<br/>read jwks_uri, issuer, algs"]
    G -- "404" --> I["fallback: GET {issuer}/.well-known/oauth-authorization-server<br/>(RFC 8414)"]
    I --> J{"200 OK?"}
    J -- "NO" --> K["★ BeanCreationException<br/>the application DOES NOT START"]
    J -- "YES" --> H

    H --> L["NimbusJwtDecoder.withJwkSetUri(jwks_uri)"]
    L --> M["fetch the JWKS → CACHE it"]
    M --> N["set JwtValidators.createDefaultWithIssuer(issuer)"]
    N --> O["★ add YOUR audience validator"]

    C --> P["fetch the JWKS → CACHE it"]
    E --> P
    P --> Q{"also an issuer-uri?"}
    Q -- "YES" --> N
    Q -- "NO" --> R["no iss validation<br/>— set it yourself"]

    O --> S["READY — the first request is validated"]
    R --> S

    T["AUTHORIZATION SERVER IS DOWN"] -.-> U{"Which mode?"}
    U -- "issuer-uri only" --> V["★ startup FAILS<br/>IllegalArgumentException:<br/>'Unable to retrieve the configuration'"]
    U -- "jwk-set-uri (with or without issuer-uri)" --> W["✅ starts fine<br/>(the JWKS fetch may still fail —<br/>use a cached/local copy if needed)"]
```

**What to look at:** the difference between "the application will not start" and "the
application starts and serves traffic" is entirely down to whether you used `jwk-set-uri`.
That is a **deployment** decision, not a security decision — and it is worth making
explicitly for any service that must survive an auth outage.

### 6.5 Token audiences at a glance (ASCII)

```text
   ┌────────────────┬──────────────────────┬───────────────────────────────┐
   │ TOKEN          │ `aud`                │ WHO MAY ACCEPT IT             │
   ├────────────────┼──────────────────────┼───────────────────────────────┤
   │ access token   │ the RESOURCE SERVER  │ the API — and ONLY the API    │
   │                │ e.g. ["orders-api"]  │ whose id is in the aud        │
   ├────────────────┼──────────────────────┼───────────────────────────────┤
   │ id token       │ THE CLIENT           │ the client — and ONLY that   │
   │ (OIDC)         │ e.g. "orders-web"    │ client, and only for LOGIN    │
   ├────────────────┼──────────────────────┼───────────────────────────────┤
   │ refresh token  │ usually ABSENT       │ the token endpoint only       │
   │                │                      │ (never an API)                │
   └────────────────┴──────────────────────┴───────────────────────────────┘

   ★ THE CONFUSED-DEPUTY TEST
   ────────────────────────────────────────────────────────────────────
   "May this API accept a token whose aud does not contain
    this API's own id?"

        YES  →  you are a confused deputy.
                A token minted for ANY service in the estate
                will authenticate here.

        NO   →  the audience validator is present and correct.

   ★ THE ID-TOKEN TEST
   ────────────────────────────────────────────────────────────────────
   "Could a token captured from a browser be replayed at my API?"
        (a user token IS in the browser — in a cookie or in memory)

        YES  →  you are accepting ID tokens (or you are not
                validating `aud`). Both are bugs.
```

**Caption:** the audience is the machine-checkable statement of *who this credential is
for*. Everything else — the token type, the scope, the claim shape — is secondary.

---

## 7. Production-Level Use Cases & Real Industry Examples

### 7.1 Microservices — 40 services calling each other with no user

**The situation.** A payments platform has 40 microservices. `ledger-service` calls
`fraud-service` **900 times per second**. Both are internal, and the user context was
resolved at the edge long before.

**The design.** Every internal service is a **confidential client** using the **Client
Credentials** grant. Each service has its own client id and secret, rotated via a secrets
manager. `fraud-service` is the resource server and validates the access token **locally**
against the shared JWKS — with an **audience validator** so a token minted for
`ledger-service` is rejected by `fraud-service`.

**The numbers.** Token TTL **60 s** (short, because re-minting is cheap); the client caches
the token for `expires_in - 60 s`; the AS handles **~15 requests/second** of token issuance
across the whole fleet (40 services × 1 refresh per minute × 1/60 ≈ 0.7/s per service — the
peak is burstier, hence the caching). Local validation at 90 µs × 900 rps = **81 ms of CPU
per second** across the fleet.

### 7.2 A banking API — the hybrid: JWT hot path, live check on the money endpoint

**The situation.** A retail bank's payments API handles **14 transactions/second** on average
and **220/second** at 09:00 on payday. The compliance requirement is that a **revoked**
account's session must die **immediately**.

**The design.** A **hybrid**, and it is the answer most candidates miss when they insist on
"local validation or introspection":

```text
   99% of endpoints  → LOCAL JWT validation         (90 µs, no AS dependency)
   GET /accounts/*   → LOCAL JWT + Redis session check  (+1 ms, immediate logout)
   POST /payments    → LOCAL JWT + Redis session check  (+1 ms, immediate logout)
```

The Redis record is the **session state** written at login and deleted at logout. One
lookup on the money path is a fraction of an introspection call and keeps the AS off the hot
path entirely.

**The numbers.** 220 rps × 1 ms of Redis time = 0.22 s of waiting per second — negligible.
The same 220 rps through introspection at 12 ms would be **2.64 s** of AS-bound time per
second, and the AS would need to handle every one of the bank's API requests.

### 7.3 A SaaS — OIDC login, refresh rotation and a domain allowlist

**The situation.** A B2B SaaS (12,000 workspaces) logs users in through an enterprise OIDC
provider (Okta) or through social login. Sessions must last a working day, and a
compromised refresh token must be detectable.

**The design.** The authorization code flow with PKCE (chapter 02), a **rotating** refresh
token with a **7-day absolute / 30-day relative** lifetime, and a **token-version** counter
for "log out everywhere". The refresh token is stored **hashed** in PostgreSQL with the
family id; a reuse event revokes the family and raises a security event.

**The numbers.** Access token TTL **10 min**; refresh token **7 days absolute, 30 days
relative**; the refresh-token table holds ~**180,000** rows at 12,000 DAU (15 sessions per
user); a nightly job deletes rows whose absolute expiry has passed — ~4,800 rows/day.

### 7.4 Ad-tech — 200 partners, one AS, opaque tokens for the high-value endpoint

**The situation.** An ad network exposes 5 internal APIs and 200 partner APIs. Most partner
traffic is read-only and low-value; the "billable impression" endpoint is high-value and must
be revocable within seconds.

**The design.** **JWT** access tokens for the 200 partner APIs (local validation, 90 µs, no
AS dependency). **Opaque** tokens with **introspection** for the billable endpoint only
(5–40 ms, immediate revocation). The two live side by side in the same fleet, in different
services.

**The numbers.** Partner traffic **22,000 req/s** at peak; at 90 µs of local validation that
is **1.98 s of CPU per second** — about half a core, spread over the fleet. The billable
endpoint is **140 req/s**; at 12 ms of introspection that is **1.68 s** — which is 1.4% of
the partner traffic but 100% of the AS's introspection load, and it is a deliberate,
budgeted cost.

---

## 8. Worked Examples (with Output + Dry Run)

### 8.1 A resource server with `issuer-uri` — the startup log and the 401/200 proof

**The problem.** Configure a resource server against Keycloak, capture the discovery and JWKS
fetch at startup, and prove the API is 401 without a token and 200 with one.

**The configuration.**

```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8081/realms/acme
          jwk-set-uri: http://localhost:8081/realms/acme/protocol/openid-connect/certs
```

> ⚠️ **Note.** Setting **both** properties is deliberate: `jwk-set-uri` is used to fetch the
> keys **without** a discovery call, and `issuer-uri` still drives the `iss` validation. This
> is the configuration that starts even when the authorization server is briefly unavailable.
> With `issuer-uri` **only**, Boot performs discovery — and the log below will show the
> metadata fetch instead.

**The expected startup output.**

```text
2026-01-14 08:59:02.114  INFO  o.s.b.a.o.s.r.OAuth2ResourceServerAutoConfiguration :
    Resource server JWT configuration using issuer-uri: 'http://localhost:8081/realms/acme'

2026-01-14 08:59:02.221  INFO  o.s.s.oauth2.jwt.NimbusJwtDecoder : Loaded 2 JWKs from
    http://localhost:8081/realms/acme/protocol/openid-connect/certs

2026-01-14 08:59:02.238  INFO  c.a.c.i.web.OAuth2ResourceServerJwtConfiguration :
    Will secure any request with [BearerTokenAuthenticationFilter]

2026-01-14 08:59:02.401  INFO  o.s.boot.web.embedded.tomcat.TomcatWebServer :
    Tomcat started on port 8080 (http) with context path '/'
```

With `issuer-uri` **only**, you would additionally see the discovery request in the
authorization server's access log:

```text
GET /realms/acme/.well-known/openid-configuration    200   1,842 bytes
GET /realms/acme/protocol/openid-connect/certs       200   1,204 bytes
```

**The proof — 401 without a token.**

```bash
curl -s -i http://localhost:8080/api/orders | head -8
```

```text
HTTP/1.1 401
WWW-Authenticate: Bearer realm="null", error="invalid_token",
                  error_description="An error occurred while attempting to decode the Jwt: Signed JWT rejected: Another algorithm is expected"
```

Hmm — a real Keycloak token would say `"JWT rejected: Another algorithm is expected"` only
if the algorithm was not pinned. With a correct Keycloak token and a pinned `RS256` decoder
the body is empty and the header is:

```text
HTTP/1.1 401
WWW-Authenticate: Bearer realm="null", error="invalid_token",
                  error_description="An error occurred while attempting to decode the Jwt: JWT rejected: Invalid signature"
```

**The proof — 200 with a token.**

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/acme/protocol/openid-connect/token \
  -u 'orders-web:secret' -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials&scope=orders:read' | jq -r .access_token)

curl -s -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/orders | head -6
```

```text
HTTP/1.1 200
Content-Type: application/json
X-Request-Id: 8b1c4f2a-77d3-4e91-a0c5-3f6b9d2e8a41

{"orders":[{"id":10231,"total":2499.00,"currency":"INR"}]}
```

**Dry run — step by step.**

1. Boot reads `issuer-uri` and `jwk-set-uri`.
2. `OAuth2ResourceServerJwtConfiguration` builds a `NimbusJwtDecoder` with a
   `RemoteJWKSet` pointed at the `jwk-set-uri`. The JWKS is fetched **once** and cached —
   the log line "Loaded 2 JWKs" confirms it.
3. Because `issuer-uri` is also set, the validator is
   `JwtValidators.createDefaultWithIssuer("http://localhost:8081/realms/acme")`.
4. **No request has been served yet** — the key material is loaded at startup, but no
   verification has happened.
5. The first request, with no `Authorization` header, reaches
   `BearerTokenAuthenticationFilter`; `DefaultBearerTokenResolver` returns `null`; the filter
   continues unauthenticated; `AuthorizationFilter` sees an anonymous principal and
   `ExceptionTranslationFilter` calls the `BearerTokenAuthenticationEntryPoint` → **401**
   with the `WWW-Authenticate: Bearer error="invalid_token"` header.
6. The second request carries the token. The decoder resolves the `kid` from the **cached**
   JWKS — **no network call** — verifies the `RS256` signature, and runs the validators:
   `exp` (valid, 300 s TTL), `nbf`, `iss` (matches). It passes.
7. `JwtAuthenticationConverter` maps the `scope` claim to `SCOPE_orders:read`.
8. `AuthorizationFilter` sees an authenticated principal with the required authority →
   **200**.

**The takeaway.** The 401 and the 200 differ by **one header**; the cost of the 200 is
**zero network calls**. That is the whole argument for local validation — and the startup log
("Loaded 2 JWKs") is the proof that the keys are cached, not fetched per request.

---

### 8.2 The `aud` gotcha — a token for service A accepted by service B, and the fix

**The problem.** `orders-api` and `billing-api` are both resource servers of the same
authorization server. A token minted for `billing-api` is **accepted by `orders-api`**.

**Step 1 — prove the bug.**

```bash
# Mint a token whose audience is billing-api.
TOKEN=$(curl -s -X POST http://localhost:8081/realms/acme/protocol/openid-connect/token \
  -u 'billing-worker:secret' -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials&scope=orders:read' | jq -r .access_token)

# Decode the audience.
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq .aud
# → [ "billing-api" ]

# Call orders-api with it.
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/orders
# → 200     ← THE BUG
```

**Step 2 — see the missing validator.**

```bash
grep -rn "JwtValidators\|JwtClaimValidator\|DelegatingOAuth2TokenValidator" src/main/java/
# → only: JwtValidators.createDefaultWithIssuer(issuer)
#    ★ no audience validator
```

**Step 3 — the fix.**

```java
package com.example.orders.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

/**
 * Shared resource-server configuration.
 *
 * WHY this is a shared @Configuration: the audience validator is the single most-missed
 * control in Spring Security, and a per-service copy is a per-service chance to forget it.
 * Making the service id a REQUIRED property turns an omission into a startup failure.
 */
@Configuration
public class ResourceServerConfig {

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${acme.service-id}") String serviceId) {          // ← required

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        // iss + exp + nbf (60 s skew). NOTE: NO audience check.
        OAuth2TokenValidator<Jwt> issuerAndTimestamps =
                JwtValidators.createDefaultWithIssuer(issuer);

        // The audience validator — the fix.
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(serviceId));

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(issuerAndTimestamps, audience));
        return decoder;
    }
}
```

```yaml
acme:
  service-id: orders-api     # required — no default
```

**Step 4 — re-test.**

```bash
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/orders
# → 401     ← FIXED
```

**The expected error body.**

```text
HTTP/1.1 401
WWW-Authenticate: Bearer realm="null", error="invalid_token",
                  error_description="An error occurred while attempting to decode the Jwt:
                  Jwt Validation failed"
```

**Dry run — step by step.**

1. The token's `aud` is `["billing-api"]`.
2. `JwtValidators.createDefaultWithIssuer` checks `iss` — the token's issuer **matches**, because
   both services use the same authorization server. It checks `exp` — valid. It **does not
   look at `aud`**. The token passes.
3. `JwtAuthenticationConverter` maps the `scope` claim — `"orders:read"` → `SCOPE_orders:read`.
   The endpoint requires `SCOPE_orders:read`. It passes. **200.**
4. After the fix, the `JwtClaimValidator<List<String>>` runs:
   `aud.contains("orders-api")` → `["billing-api"].contains("orders-api")` → **false** → the
   validator fails → `JwtValidationException` → **401**.
5. The legitimate token — minted with `aud=["orders-api"]` — now passes both validators.

**The takeaway.** `iss` tells you *who minted it*; `aud` tells you *who it is for*. In a
single-issuer estate, `iss` is identical for every service and therefore **useless for
service-to-service isolation**. `aud` is the control that makes one service's token useless
against another.

---

### 8.3 An introspection-based resource server — the exact HTTP calls

**The problem.** A high-value endpoint must honour an immediate revocation, so it uses
introspection rather than local validation.

**The configuration.**

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        opaquetoken:
          introspection-uri: http://localhost:8081/realms/acme/protocol/openid-connect/token/introspect
          client-id: orders-api
          client-secret: ${ORDERS_API_INTROSPECTION_SECRET}
```

```java
package com.example.orders.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OpaqueResourceServerConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health").permitAll()
                    .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                    .opaqueToken(opaque -> opaque
                            .introspectionUri("http://localhost:8081/realms/acme/protocol/openid-connect/token/introspect")
                            .clientId("orders-api")
                            .clientSecret("${ORDERS_API_INTROSPECTION_SECRET}")));
        return http.build();
    }
}
```

**The introspection call the framework makes, for every request.**

```text
   POST http://localhost:8081/realms/acme/protocol/openid-connect/token/introspect
   Authorization: Basic b3JkZXJzLWFwaTpvcmRlcnMtYXBpLWludHJvc3BlY3Rpb24tc2VjcmV0
   Content-Type: application/x-www-form-urlencoded

   token=eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIjoi…
```

**The response.**

```json
{
  "active": true,
  "scope": "orders:read",
  "client_id": "ledger-service",
  "token_type": "Bearer",
  "exp": 1745000000,
  "iat": 1744999700,
  "sub": "ledger-service",
  "aud": ["orders-api"],
  "iss": "http://localhost:8081/realms/acme"
}
```

**What happens for a revoked token.**

```text
   POST …/token/introspect
   token=eyJhbGciOiJSUzI1NiIs…

   HTTP/1.1 200 OK
   { "active": false }
```

**And the API's response to the caller.**

```text
   HTTP/1.1 401
   WWW-Authenticate: Bearer realm="null", error="invalid_token",
                     error_description="The token was expired, revoked, or the token id is invalid"
```

**Dry run — step by step.**

1. `BearerTokenAuthenticationFilter` extracts the opaque token from the header.
2. `BearerTokenAuthenticationProvider` calls
   `OpaqueTokenIntrospector#introspect(token)` — a **network call** to the authorization
   server, on **every** request.
3. The introspector POSTs the token with **its own** client credentials
   (`orders-api` + its secret). The AS authenticates the *introspecting* client and checks
   that it is **allowed to introspect** this token.
4. The AS returns the live state. `"active": true` → the framework builds an
   `OAuth2AuthenticatedPrincipal` with the `scope` claim; the authorities become
   `SCOPE_orders:read`.
5. `"active": false` → `OAuth2InvalidTokenException` → **401**.
6. **If the AS is unreachable**, the exception propagates → **401** (fail closed). The API is
   effectively down for authenticated traffic, which is the availability price of
   introspection.

**The takeaway.** Introspection gives you **instant revocation** and **live claims**, and it
costs a network hop and an AS dependency on every request. That is a deliberate trade — and
it is the right trade for a small number of high-value endpoints, not for the whole API.

---

## 9. Code from Scratch — Production-Grade

### 9.1 A resource server with a mandatory audience validator

```java
package com.example.orders.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class OrdersResourceServerConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http,
                              JwtAuthenticationConverter converter) throws Exception {
        http
                // WHY stateless: no session — a JWT needs no server state.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // WHY csrf disabled: no cookie carries the credential.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        // WHY the entry point: it renders the RFC 6750 WWW-Authenticate header.
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${acme.service-id}") String serviceId) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                // WHY pin the algorithm: the token's header must never choose it.
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> issuerAndTimestamps =
                JwtValidators.createDefaultWithIssuer(issuer);

        // ★ THE LINE EVERYONE FORGETS
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(serviceId));

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(issuerAndTimestamps, audience));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        // WHY an explicit prefix and claim name: do not rely on the defaults.
        authorities.setAuthorityPrefix("SCOPE_");
        authorities.setAuthoritiesClaimName("scope");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
```

### 9.2 A client-credentials provider with a proper cache and a fallback

```java
package com.example.ledger.client;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * A client-credentials token provider for a service calling another service.
 *
 * WHY no refresh token: there is no user session. The client simply mints a new access token
 * when the old one expires — a single cheap back-channel call.
 */
@Component
public class ServiceTokenProvider {

    private static final long SKEW_SECONDS = 60;

    private final RestClient restClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    private record CachedToken(String value, Instant expiresAt) { }

    public ServiceTokenProvider(RestClient.Builder builder,
                                @Value("${acme.downstream.token-uri}") String tokenUri,
                                @Value("${acme.downstream.client-id}") String clientId,
                                @Value("${acme.downstream.client-secret}") String clientSecret,
                                @Value("${acme.downstream.scope}") String scope) {
        this.restClient = builder.build();
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
    }

    public String getAccessToken() {
        CachedToken cached = cache.get(scope);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(SKEW_SECONDS))) {
            return cached.value();
        }
        CachedToken fresh = fetch();
        cache.put(scope, fresh);
        return fresh.value();
    }

    private CachedToken fetch() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", scope);

        TokenResponse response = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                // WHY client_secret_basic: the default method for a confidential client.
                .header(HttpHeaders.AUTHORIZATION, "Basic " + java.util.Base64.getEncoder()
                        .encodeToString((clientId + ":" + clientSecret)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("No access_token in the token response");
        }
        return new CachedToken(response.accessToken(),
                Instant.now().plusSeconds(response.expiresIn() - SKEW_SECONDS));
    }

    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") long expiresIn,
            @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType,
            String scope) { }
}
```

### 9.3 A refresh-token rotation service with reuse detection

```java
package com.example.orders.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token rotation with reuse detection.
 *
 * ★ THE MODEL: a "family" is one login. Each refresh issues a new token and consumes the
 *   old one. Presenting a CONSUMED token means it was stolen — so the whole family dies.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    /** Called at login. Creates the first token of a new family. */
    @Transactional
    public Issued issue(String userId, Duration absoluteLifetime) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String familyId = newFamilyId();

        Instant now = Instant.now();
        repository.save(new RefreshTokenEntity(
                null, familyId, userId, sha256(raw), now, now.plus(absoluteLifetime), now, null));
        return new Issued(raw, familyId, now.plus(absoluteLifetime));
    }

    /**
     * Called on every refresh.
     *
     * @throws ReuseDetectedException when a CONSUMED token is presented — the whole family
     *         is revoked and the user must log in again.
     */
    @Transactional
    public Issued rotate(String rawToken, Duration absoluteLifetime) {
        String hash = sha256(rawToken);
        RefreshTokenEntity current = repository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException());

        if (current.getConsumedAt() != null) {
            // ★ REUSE: this token was already used. It was stolen.
            repository.revokeFamily(current.getFamilyId(), Instant.now());
            throw new ReuseDetectedException(current.getFamilyId());
        }
        if (current.getRevokedAt() != null || current.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        Instant now = Instant.now();
        current.setConsumedAt(now);

        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String nextRaw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshTokenEntity(
                null, current.getFamilyId(), current.getUserId(), sha256(nextRaw),
                now, now.plus(absoluteLifetime), now, null));

        return new Issued(nextRaw, current.getFamilyId(), now.plus(absoluteLifetime));
    }

    @Transactional
    public void revokeFamily(String familyId) {
        repository.revokeFamily(familyId, Instant.now());
    }

    private String newFamilyId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * WHY SHA-256 and not bcrypt: the refresh token is 256 bits of CSPRNG output — it has
     * no guessable structure, so a fast hash is safe and a slow KDF would only add latency.
     * (A user-chosen password is the opposite case — see the Password Encoding chapter.)
     */
    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is mandatory on every JRE", ex);
        }
    }

    public record Issued(String rawToken, String familyId, Instant expiresAt) { }
}
```

> ⚠️ **A note on `rotate` and concurrency.** With rotation, two concurrent refreshes with the
> same token are a race: one wins, and the other looks like reuse and kills the family. The
> production fix is a **short grace window** (accept a just-consumed token for 10–30 s and
> return the same successor) plus a **client-side mutex** so one device never refreshes
> twice at once. The snippet above shows the core logic; add the grace window before
> deploying it.

### 9.4 An OIDC-aware logout that also ends the provider session

```java
package com.example.orders.auth;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OidcLogoutSuccessHandler implements LogoutSuccessHandler {

    private final String endSessionEndpoint;
    private final String postLogoutRedirectUri;

    public OidcLogoutSuccessHandler(
            @Value("${acme.oidc.end-session-endpoint}") String endSessionEndpoint,
            @Value("${acme.oidc.post-logout-redirect-uri}") String postLogoutRedirectUri) {
        this.endSessionEndpoint = endSessionEndpoint;
        this.postLogoutRedirectUri = postLogoutRedirectUri;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                Authentication authentication) throws java.io.IOException {

        String idTokenHint = null;
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token
                && oauth2Token.getPrincipal() instanceof OidcUser oidcUser
                && oidcUser.getIdToken() != null) {
            idTokenHint = oidcUser.getIdToken().getTokenValue();
        }

        // WHY the id_token_hint: it tells the AS WHICH session to end. Without it the AS
        // may show a confirmation screen, or end the wrong session.
        StringBuilder url = new StringBuilder(endSessionEndpoint);
        url.append(idTokenHint == null ? "?" : "?id_token_hint=")
           .append(idTokenHint == null ? "" : encode(idTokenHint));
        if (idTokenHint != null) {
            url.append("&post_logout_redirect_uri=").append(encode(postLogoutRedirectUri));
        }

        // NOTE: the local session has ALREADY been invalidated by Spring's LogoutFilter
        // before this handler runs. This only ends the PROVIDER's session.
        response.sendRedirect(url.toString());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
```

---

## 10. Common Mistakes & Gotchas

| ❌ Wrong | ✅ Right | Why it breaks |
|---|---|---|
| Using **Client Credentials** for a user action | Authorization Code (+ PKCE) | There is **no user** in a client-credentials token — the `sub` is the application. Any "who is this?" logic is broken. |
| Expecting a **refresh token** from Client Credentials | None — request a new access token | There is no user session to preserve; a refresh token would be a second long-lived credential for no benefit. |
| Forgetting `scope=openid` and wondering why there is no ID token | Add the `openid` scope | The `openid` scope is what switches the flow from OAuth 2.0 to OIDC and triggers the ID token. |
| Sending the **ID token** to your API | Send the **access token** | The ID token's `aud` is your client; using it at the API is the confused-deputy bug. |
| `JwtValidators.createDefaultWithIssuer(...)` and nothing else | Add a `JwtClaimValidator<List<String>>` for `aud` | It validates `iss` + timestamps only; without `aud`, every service in the estate accepts every token. |
| `issuer-uri` only, and the app will not boot because the AS is down | Add `jwk-set-uri` alongside it | `issuer-uri` triggers discovery at startup; the failure is a `BeanCreationException`, not a degraded mode. |
| Relying on the JWKS being re-fetched on every request | Let it be cached | The `RemoteJWKSet` caches the keys; an unknown `kid` triggers **one** refresh, not a fetch per request. |
| `decoder.setJwtValidator(myValidator)` replacing the defaults | `DelegatingOAuth2TokenValidator` | You lose the `iss` and the timestamp validation. Always compose. |
| Rotating refresh tokens without **reuse detection** | Revoke the family on reuse | Without reuse detection, a stolen refresh token is indistinguishable from a legitimate one. |
| Failing **open** when introspection is unreachable | Fail closed | Failing open means the attacker only has to break the network path to the AS. |
| Requesting `openid profile email` when you only need the email | Request `openid email` | Scope minimisation is a security control — `profile` returns the full name, picture and locale. |
| Treating the ID token's `sub` as a global user id | Key by `(iss, sub)` | The `sub` is **per-issuer and per-client**; two providers will use the same `sub` for different humans. |
| Assuming the provider's logout ends your session | Destroy your own session too | RP-initiated logout ends the **provider's** session; yours is yours to invalidate. |
| Passing `post_logout_redirect_uri` that is not registered | Register it with the provider | An unregistered redirect is rejected or ignored — and it is a redirect allowlist, not a suggestion. |
| Trusting `email` from the provider without checking `email_verified` | Check `email_verified` (chapter 04) | An unverified email is an account-takeover vector. |

---

## 11. Performance Considerations

**The cost of each validation mode, per request.**

| Mode | Cost per request | At 1,000 rps | At 20,000 rps |
|---|---|---|---|
| **Local JWT (RS256)** | ~90 µs | 90 ms/s of CPU | 1.8 s/s of CPU |
| **Local JWT (ES256)** | ~45 µs | 45 ms/s | 0.9 s/s |
| **Local JWT (HS256)** | ~5 µs | 5 ms/s | 0.1 s/s |
| **Introspection** | 5–40 ms (network + AS) | **5–40 s/s** | **100–800 s/s** |

**The arithmetic that decides it.** At **20,000 rps** (a large partner API), introspection
would need **100–800 seconds of AS-bound time per second of wall clock** — i.e. **100–800
cores** of blocking work, or a connection pool of tens of thousands. It is not a scaling
problem you can tune; it is an architecture change. That is the whole reason local validation
is the default.

**The JWKS cache — the numbers that matter.**

| Behaviour | Value |
|---|---|
| JWKS fetch frequency | Once at startup, then only on an **unknown `kid`** or on the library's cache expiry |
| JWKS size | Typically **2–5 keys**, ~1–3 KB total |
| Cost of a cache miss | One HTTPS GET, ~5–40 ms |
| Keys per rotation | **2** (old + new) during the overlap window |
| Overlap window | **≥ max token TTL + clock skew** (e.g. 5 min + 60 s) |

**The token-minting cost, and why caching matters.**

| Scenario | Without caching | With caching |
|---|---|---|
| 900 rps, 60 s TTL | **900** token requests/second | **15** token requests/second |
| 20,000 rps, 300 s TTL | 20,000 rps | **67** rps |
| Cost of one mint | 10–80 ms | 10–80 ms |

The cache skew (`expires_in - 60 s`) is the difference between **zero** and **thousands** of
token requests in a burst. At a 20,000 rps peak, if every instance's token expired at the
same moment, you would get a **thundering herd** of 20,000 token requests — the classic
"expiry stampede". Randomising the initial skew by a few seconds prevents it.

**The refresh-path cost of rotation.**

```text
   WITHOUT ROTATION:  0 writes per refresh
   WITH ROTATION:     1 INSERT + 1 UPDATE per refresh   (+ the read)

   At 12,000 DAU × 12 refreshes/hour (a 5-minute access token)
   = 144,000 refreshes/hour = 40 refreshes/second = 80 writes/second
```

That is a trivial load for PostgreSQL. The cost of rotation is not throughput — it is
**complexity** and the **grace-window race**.

**What to measure.**

| Metric | Why |
|---|---|
| `oauth2.token.issued.count{grant_type,client_id}` | Detects a client that re-mints instead of caching |
| `oauth2.token.introspect.duration{p99}` | The introspection tax, if you use it |
| `jwt.validation.duration{p99}` | Detects a regression to a slow algorithm or a huge claim set |
| `jwt.failures.by_reason` | Signature vs claims vs unknown-`kid` vs audience |
| `jwks.cache.hit_ratio` | A fall means unknown `kid`s — a rotation, or an attack |
| `refresh.token.reuse.detected` | **The single most important security metric in this chapter** |
| `refresh.token.rotation.count` | Rotation volume, and an unexpected drop means clients are not refreshing |
| `token.size.p99` | A rising tail means the claim set is growing |

**Tuning knobs.**

- `spring.security.oauth2.resourceserver.jwt.issuer-uri` — discovery on/off.
- `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` — direct key fetching; also the
  availability escape hatch.
- `spring.security.oauth2.resourceserver.opaquetoken.*` — the introspection endpoint and
  credentials.
- The client-credentials cache skew (`expires_in - 60 s`) — and **randomise** the first
  fetch across instances to avoid a stampede.
- The refresh-token grace window (10–30 s) — the trade between a false-positive family
  revocation and a small theft window.
- The `JwtTimestampValidator` leeway (60 s default) — never raise it to fix a clock problem.

---

## 12. Security Considerations

### 12.1 The threat model

| Threat | Attack path | Defence |
|---|---|---|
| **Confused deputy** | A token minted for service A is presented to service B | The **`aud` validator** — the single most-missed control |
| **ID token replay at the API** | A user token captured from a browser is replayed at the API | Validate `aud` (the ID token's audience is the client, not the API) |
| **Token from another issuer** | A partner's AS issues a token your API accepts | The **`iss` validator** + `createDefaultWithIssuer` |
| **Stolen refresh token** | The attacker uses it before the legitimate client does | **Rotation with reuse detection** — the second use kills the family |
| **Refresh-token family confusion** | A race causes a legitimate client to look like a thief | A **short grace window** + a client-side mutex |
| **Algorithm confusion** | The token's header picks the verification algorithm | **Pin the algorithm** in the decoder |
| **Key removal during rotation** | A 401 storm because the old `kid` vanished | Keep the old key published for **≥ max TTL + skew** |
| **AS outage → API outage** | Introspection is unreachable | **Fail closed**, and prefer local validation; use `jwk-set-uri` |
| **Scope escalation** | The client asks for more than it was granted | The **AS** enforces the client's registered scopes; the **RS** enforces the token's scopes |
| **OIDC claim spoofing** | A forged ID token with a spoofed email | The signature + `iss` + `aud` + `nonce` validation |
| **Unverified email account takeover** | The provider says the email, but it is not verified | Check **`email_verified`** before linking (chapter 04) |
| **`sub` collision across providers** | Two different humans share a `sub` | Key by **`(iss, sub)`**, never by `sub` alone |
| **Introspection endpoint abuse** | An attacker introspects arbitrary tokens | The AS restricts which clients may introspect, and which tokens |
| **Token in the logs** | The access or refresh token is written to disk | Redact at the proxy; **never** log a refresh token |

### 12.2 The pinned token-lifetime table (canonical — from `STANDARD.md` §4.4)

| Token | TTL | Storage | Sent as |
|---|---|---|---|
| Access token | **5–15 min** | Server: none (stateless) | `Authorization: Bearer <jwt>` |
| Refresh token | **7–30 days** | Server: **hashed** in DB/Redis | `HttpOnly; Secure; SameSite=Strict` cookie |
| ID token (OIDC) | 5–60 min | none | never sent to the browser JS |

**And the service-to-service lifetimes** (not in the canonical table):

| Token | Recommended TTL | Why |
|---|---|---|
| Client-credentials access token | **30–60 s** | The client can re-mint in ~10 ms; a long TTL is pure risk |
| Refresh token — absolute | **7–30 days** | A hard cap so an old session eventually dies |
| Refresh token — relative (sliding) | **7–30 days of inactivity** | Active users are not logged out |
| Introspection cache | **0 s** (always live) | The whole point of introspection is immediacy |

### 12.3 The pinned HTTP status table (canonical — from `STANDARD.md` §4.5)

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

> 📌 **How the OAuth errors map.** At the **token endpoint**, per RFC 6749 §5.2: a malformed
> request is **400**; a client that fails to authenticate is **401** (`invalid_client`); a
> scope the client does not hold is **400 `invalid_scope`**; a grant the AS will not honour is
> **400 `invalid_grant`**. At the **resource server**: no token, an invalid token, an expired
> token, an unknown `kid`, an `iss` mismatch and an **`aud` mismatch** are **all 401** — the
> request was not authenticated. A **valid** token whose scope is insufficient is **403**,
> with `WWW-Authenticate: Bearer error="insufficient_scope"`. And an **authorization-server
> outage** during introspection is a **503** from the API's point of view — the dependency is
> unavailable, not the client's credential.

### 12.4 The rules that are not negotiable

1. **Always validate `aud`.** Make it a shared, mandatory configuration.
2. **Always validate `iss`** — `createDefaultWithIssuer` does it, but only if you do not
   replace the validator chain.
3. **Pin the signing algorithm.** Never let the token's header choose.
4. **Require `exp`.** A token without an expiry is a permanent credential.
5. **Keep the old signing key published** for at least the maximum token TTL plus skew.
6. **Hash refresh tokens at rest**, rotate them, and implement reuse detection.
7. **Give refresh tokens an absolute lifetime** as well as a relative one.
8. **Never put a refresh token in a cookie without `HttpOnly; Secure; SameSite=Strict`.**
9. **Minimise scopes**, and map them to domain authorities explicitly.
10. **Check `email_verified`** before trusting a provider's email.
11. **Key users by `(iss, sub)`**, never by `sub` alone.
12. **Fail closed** when the authorization server or the introspection endpoint is unreachable.

---

## 13. Best Practices

**Client credentials**

1. **One client id per service**, not one per team or per environment-wide secret.
2. **Rotate client secrets** on a schedule and on offboarding; support **two active secrets**
   so rotation needs no downtime.
3. **Cache the token** for `expires_in - 60 s`, and **randomise** the first fetch across
   instances to avoid an expiry stampede.
4. **Never** use client credentials to impersonate a user — that is token exchange's job.

**Refresh tokens**

5. **Rotate on every use**, and implement **reuse detection** with family revocation.
6. **Store them hashed** (SHA-256 is fine for 256 bits of CSPRNG output) — and note that a
   *provider's* refresh token must be **encrypted** instead, because you must present it back.
7. **Give them an absolute lifetime** as well as a relative one.
8. **Handle `invalid_grant` as a logout**, not as a retryable error.
9. **Add a short grace window** (10–30 s) to absorb the concurrent-refresh race.

**Resource servers**

10. **Validate locally by default** (JWKS), and reserve introspection for a small number of
    high-value endpoints.
11. **Always validate `aud`**, in shared configuration, with the service id as a required
    property.
12. **Configure `jwk-set-uri` alongside `issuer-uri`** so a deployment does not depend on the
    authorization server at boot.
13. **Pin the algorithm** and **require `exp`**.
14. **Map scopes to domain authorities explicitly** — never hand provider scopes through.
15. **Distinguish user tokens from service tokens** in your authorization rules (the `sub`
    looks different, and so should your code).

**OIDC**

16. **Always request `openid`**, and **always verify `nonce`**.
17. **Minimise the scopes** — `openid email` is usually enough.
18. **Key users by `(iss, sub)`**.
19. **Register `post_logout_redirect_uri`** and end the provider session on logout.
20. **Treat the ID token as an assertion for your client only** — never as an API credential.

**Operations**

21. **Redact tokens** at the proxy, in the APM tracer and in the log pipeline.
22. **Monitor `refresh.token.reuse.detected`** — it is your early-warning system.
23. **Alert on `jwt.failures.by_reason{reason="unknown_kid"}`** — it catches both a bad
    rotation and an attack.
24. **Document the key-rotation runbook**: publish → sign → wait (≥ max TTL + skew) → remove.

---

## 14. Hands-On Exercises & Code Challenges

### 14.1 Hands-on exercises

**Exercise 1 — Watch the discovery happen.**

1. Start a resource server with `issuer-uri` only, against a running Keycloak.
2. Enable `logging.level.org.springframework.security.oauth2=DEBUG` and
   `logging.level.com.nimbusds=DEBUG`.
3. Restart the app and capture the startup sequence.
4. **Acceptance criteria:** the log shows the discovery fetch of
   `/.well-known/openid-configuration`, the JWKS fetch, and the "Loaded N JWKs" line — and
   **no** HTTP request to the authorization server appears during the *request* phase.

**Exercise 2 — Prove the audience validator matters.**

1. Configure the resource server with `issuer-uri` **only** and start it.
2. Mint a token whose `aud` is `["another-api"]` and call the API → observe **200**.
3. Add the `JwtClaimValidator<List<String>>` audience validator and restart → observe **401**.
4. **Acceptance criteria:** the 200/401 difference is reproduced; the 401 body says
   `invalid_token`; and a `grep` over the source confirms the validator is present.

**Exercise 3 — Implement client-credentials caching and measure.**

1. Implement `ServiceTokenProvider` with a `ConcurrentHashMap` cache.
2. Call `getAccessToken()` 1,000 times in a loop.
3. **Acceptance criteria:** exactly **one** HTTP call to the token endpoint is made; a test
   proves that a cached token with less than 30 s of life left is **not** returned; and a
   test proves that two different scopes get two different cache entries.

**Exercise 4 — Watch the `aud` in the wild.**

1. Decode the access tokens of three different clients in your Keycloak realm.
2. Compare their `aud` values.
3. **Acceptance criteria:** you can state, for each client, which resource servers will accept
   its token — and you have discovered at least one client whose token has **no** `aud`, which
   is a finding worth raising.

### 14.2 Code challenges

**Challenge 1 — A refresh-token rotation service with reuse detection.**

> Implement a refresh-token store that rotates on every use and revokes the family on reuse.

**Acceptance criteria:**
- [ ] The refresh token is stored **hashed** (SHA-256) — the raw token is never persisted.
- [ ] Each refresh returns a **new** token and marks the old one consumed, in **one
      transaction**.
- [ ] Presenting a **consumed** token revokes the **entire family** and raises a
      `ReuseDetectedException`.
- [ ] A family has both an **absolute** and a **relative** expiry, and the effective expiry
      is the earlier of the two.
- [ ] A test asserts: rotate → rotate → present the **first** token → the whole family is
      revoked and a new login is required.
- [ ] A test asserts that the raw token does **not** appear in the database.

**Stretch goal:** add a **grace window** — a consumed token presented within 30 s returns the
*same* successor instead of triggering a revocation.

**Challenge 2 — A shared audience validator that cannot be forgotten.**

> Build a `JwtDecoder` configuration that is **mandatory** for every resource server in the
> estate.

**Acceptance criteria:**
- [ ] The service id comes from a **required** property with no default — a missing value is
      a **startup failure**.
- [ ] The validator chain is `iss` + timestamps **and** `aud`, composed with
      `DelegatingOAuth2TokenValidator`.
- [ ] The audience check requires a **non-empty** `aud` that **contains** the service id.
- [ ] A test asserts that a token with no `aud` claim is **rejected**.
- [ ] The configuration lives in a **shared starter module**, so a service cannot omit it.
- [ ] A test asserts the algorithm is pinned (a token with `alg: none` is rejected).

**Stretch goal:** add a **tenant** validator for a multi-tenant SaaS, reading `tenant_id` from
a request-scoped context.

**Challenge 3 — An introspection-based resource server with a strict failure policy.**

> Implement an introspection-based resource server that fails closed and exposes a health
> signal for the introspection dependency.

**Acceptance criteria:**
- [ ] The introspector uses client credentials from configuration (never hard-coded).
- [ ] A network failure to the introspection endpoint results in **401**, never in "accepted".
- [ ] The service exposes an Actuator health indicator that reports the introspection
      endpoint as **DOWN** when it is unreachable.
- [ ] A test asserts that an `"active": false` response produces a **401**.
- [ ] The `scope` claim is mapped to `SCOPE_`-prefixed authorities.
- [ ] A Micrometer counter records the introspection latency (p50/p95/p99).

**Stretch goal:** add a **short-lived introspection cache** (e.g. 5 s) for the read-only
endpoints, keeping the high-value endpoint uncached.

---

## 15. Interview Q&A — Level-wise

### 🟢 Beginner

**Q:** What is the Client Credentials grant used for?
**A:** Machine-to-machine calls where **there is no end user** — a backend service calling
another service, a worker consuming an internal API, a CI pipeline. The client authenticates
with its **own** credentials at the token endpoint and receives an access token that
represents the **application**, not a person. The scope is the application's own permission
set.

**Q:** Why does the Client Credentials grant not return a refresh token?
**A:** Because there is **no user session to preserve**. A refresh token exists so a human
does not have to log in every 15 minutes. A service can simply request a new access token
when the old one expires — that is a single ~10 ms back-channel call, and the service
authenticates itself anyway.

**Q:** What does OpenID Connect add to OAuth 2.0?
**A:** An **identity layer**: the `openid` scope, the **ID token** (a signed JWT asserting
who the user is, addressed to the client), the **UserInfo endpoint**, the **discovery
document**, and **RP-initiated logout**. Without the `openid` scope it is plain OAuth 2.0 and
no identity is returned.

**Q:** What is the difference between an access token and an ID token?
**A:** The **audience** is the difference. The access token's `aud` is the **resource server** —
it is a credential for your API. The ID token's `aud` is **your client** — it is an assertion
of identity for the client to read. Sending an ID token to your own API is a confused-deputy
bug, and an API that accepts it will trust a credential minted for someone else.

### 🟡 Intermediate

**Q:** What is refresh-token rotation, and why does it include reuse detection?
**A:** Rotation means each refresh returns a **new** refresh token and **invalidates the old
one**, so a refresh token is single-use. Reuse detection is what makes rotation safe: if an
**already-consumed** token is presented, either the legitimate client retried or a thief
copied it — and the response is to revoke the entire **token family** (the whole login) and
force a fresh authentication. Without reuse detection, rotation buys you very little, because
the stolen token can be used exactly once, legitimately, before the real owner notices.

**Q:** What is the discovery document and why does it matter?
**A:** It is a JSON document at `/.well-known/openid-configuration` that publishes the
authorization server's **entire endpoint set** — `authorization_endpoint`, `token_endpoint`,
`userinfo_endpoint`, `jwks_uri`, `end_session_endpoint`, the supported grants, the supported
ID-token algorithms and the supported scopes. It matters because it lets you configure a
client with **one URL** (`issuer-uri`) instead of four hard-coded URLs per provider, and it
makes adding or moving an IdP a configuration change rather than a coordinated redeploy.

**Q:** What does `JwtValidators.createDefaultWithIssuer(issuer)` actually validate?
**A:** The **issuer** and the **timestamps** (`exp` and `nbf`, with a 60-second clock-skew
leeway). It does **not** validate the **audience**. In a single-issuer estate where every
service uses the same authorization server, `iss` is identical for every token, so it
provides **no** service-to-service isolation. You must add a
`JwtClaimValidator<List<String>>` for `aud` yourself, composed with
`DelegatingOAuth2TokenValidator`.

**Q:** When would you use introspection instead of local token validation?
**A:** When you need **immediate revocation** — a token that must die the moment it is
revoked, not at its expiry — or when the token is **opaque** rather than a JWT. The cost is
a network hop on **every** request (5–40 ms versus ~90 µs) and it makes the authorization
server's availability part of your API's availability. So it is right for a small number of
high-value endpoints, and wrong for a high-volume API.

### 🔴 Advanced / Senior

**Q:** Your resource server will not start and the error mentions "Unable to retrieve the
configuration from the issuer". Why?
**A:** Because `spring.security.oauth2.resourceserver.jwt.issuer-uri` triggers **OIDC
discovery at startup**: Boot fetches `{issuer}/.well-known/openid-configuration`, reads the
`jwks_uri` from it, and builds the `JwtDecoder` from that. If the fetch fails, the bean
cannot be created and the context fails to refresh. The fix is either to fix the
connectivity, or to configure `jwk-set-uri` as well — that skips discovery entirely (the
keys are fetched directly) while `issuer-uri` still drives the `iss` validation. That
configuration is the one to use for any service that must survive an authorization-server
outage.

**Q:** How do you distinguish a user token from a service token in a resource server?
**A:** By the `sub` claim and by the token's provenance: a client-credentials token has the
**client id** as its subject and a `client_id` claim, while a user token has a user's opaque
`sub`. I would expose that as an explicit attribute — for example a
`ClientAuthenticationToken` for service tokens versus a `JwtAuthenticationToken` for user
tokens — so that the authorization rules can differ. The failure mode to avoid is assuming
every principal is a human: a code path that renders `user.getName()` into a UI, or that
grants a "self" scope to whatever `sub` it sees, will happily treat
`ledger-service` as a person.

**Q:** How do you handle the concurrent-refresh race with rotating refresh tokens?
**A:** With rotation, two refreshes with the same token are a race — one wins, and the other
looks like reuse, which would revoke the family and log the user out. Three standard fixes:
a **short grace window** where a just-consumed token returns the *same* successor (10–30
seconds), an **idempotency key** so the client can retry safely, or a **counter per family**
so the server can tell a late retry from a thief. In practice I use the grace window plus a
**client-side mutex** so one device never refreshes twice concurrently, and I alert on
`reuse_detected` — because a *real* theft looks exactly like a race, and the grace window
should not hide it.

**Q:** How do you implement "log out everywhere" with stateless access tokens?
**A:** With a **token-version claim**. At login you increment a per-user counter and put its
value in the token as a `ver` claim. Every request compares the token's `ver` with the
current counter — one Redis read, O(1) state that does not grow with the number of revoked
tokens. When the counter is incremented, **every** outstanding token for that user dies at
once, regardless of its TTL. The alternative — a denylist of revoked `jti`s — grows with the
revocation rate and needs an entry per token; the version counter is one key per user.

### ⚫ Expert / Principal

**Q:** 40 microservices need to call each other. Walk me through your authentication design.
**A:** One authorization server. Each service is a **confidential client** using the
**Client Credentials** grant, with its own client id and secret from a secrets manager,
rotated quarterly with two active secrets per client. The downstream API is a **resource
server** that validates the token **locally** against the cached JWKS — because at 900 calls
per second, introspection at 12 ms would be 10.8 seconds of AS-bound time per second of
traffic, and it would make the authorization server a hard dependency of the whole mesh.
Critically, each resource server validates **`aud`**: the token's audience must contain that
service's own id, otherwise a token minted for `ledger-service` would authenticate against
`fraud-service`. The audience is the only control that gives service-to-service isolation
in a single-issuer estate, because `iss` is identical for every token. I would also pin the
algorithm, require `exp`, use a **30–60 second** TTL for service tokens (the client can
re-mint in ~10 ms, so a long TTL buys nothing), and cache with a 60-second skew, randomising
the first fetch across instances to avoid an expiry stampede.

**Q:** When would you use token exchange rather than client credentials?
**A:** When the downstream service needs to act **for a user**, not for itself. Client
credentials prove the *service*; they say nothing about the user's authority. Token exchange
(RFC 8693) takes the user's access token as the `subject_token`, and the authorization server
issues a **narrower** token for the downstream audience, optionally with an `act` claim
naming the service that is actually acting. The security requirements are the hard part:
the AS must verify the subject token, must check that the requesting client is allowed to
perform that exchange, must **not** allow scope escalation, and must record the delegation
chain for audit. The default is **deny**, and every exchange should be logged — an
unexplained token-exchange endpoint is a privilege-escalation waiting to be found.

**Q:** How would you migrate a fleet to sender-constrained tokens?
**A:** In stages. **Stage 1 — issue dual tokens:** the AS issues the current bearer token *and*
an mTLS-bound or DPoP-bound variant, and the resource servers accept both. **Stage 2 —
instrument:** log the percentage of requests that present a bound token, per client, without
rejecting the bearer ones. **Stage 3 — enforce per client:** flip the requirement for the
clients that are at 100% bound. **Stage 4 — retire:** stop issuing the bearer token for those
clients. The blockers are practical, not conceptual: DPoP needs key management on each
client platform (a non-extractable key in the browser's WebCrypto, or the platform
keystore), and mTLS needs certificate distribution and rotation. I would start with the
**highest-value, lowest-count** clients — the payment services — rather than trying to
convert everything at once, and I would never enable it for the entire fleet on the same
day.

---

## 16. Scenario-Based & Real-Experience Scenarios

### Scenario 1 — "The API started accepting tokens meant for another service"

**The situation.** During an internal security review, you are shown a proof of concept: a
token minted for the `billing-worker` client — with `aud: ["billing-api"]` and
`scope: "orders:read"` — is accepted by `orders-api` with a **200**. The reviewer calls it a
"high-severity confused-deputy finding".

**Your first move.**

```bash
# 1. Reproduce it deterministically.
TOKEN=$(curl -s -X POST https://auth.acme.com/realms/acme/protocol/openid-connect/token \
  -u 'billing-worker:secret' -d 'grant_type=client_credentials&scope=orders:read' \
  | jq -r .access_token)
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{iss,aud,scope,sub}'
# → { "iss": "https://auth.acme.com/realms/acme",
#     "aud": ["billing-api"],
#     "scope": "orders:read",
#     "sub": "billing-worker" }

curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  https://api.acme.com/orders/api/orders
# → 200    ← the finding

# 2. Find the validator configuration.
grep -rn "setJwtValidator\|createDefaultWithIssuer\|JwtClaimValidator" src/main/java/
```

**Expected signal.** The configuration contains exactly one validator:
`JwtValidators.createDefaultWithIssuer(issuer)`. The `iss` matches (one authorization server
for the whole estate), the token is not expired, and the `scope` grants
`SCOPE_orders:read` — so every check passes. There is **no `aud` check anywhere in the
codebase**.

**The trap.** "The scope is `orders:read`, so it is legitimate." The scope being right does
not make the token legitimate **for this service** — the whole point of the audience is that
it says which service the token was minted *for*. Also, the trap of "let me just remove
`orders:read` from the billing-worker client" — that fixes this one instance while leaving
the class of bug open.

**The resolution.** (1) Add the audience validator, in **shared configuration**, with the
service id as a **required property** so a missing value is a startup failure. (2) Rotate the
signing keys to invalidate every outstanding token. (3) Audit the authorization server's
issuance logs to find out **how many cross-service token presentations already happened** —
that is the real blast radius, and it is the number the reviewer will ask for. (4) Add the
`aud` check to the CI pipeline as a **regression test**: mint a token for another audience
and assert a 401.

**The lesson.** The `aud` check is one line and its absence is a fleet-wide hole. Put it in
**shared, mandatory configuration** — a per-service copy is a per-service chance to forget.

---

### Scenario 2 — "The application will not deploy because the authorization server is down"

**The situation.** A routine deployment at 02:00 fails. Every pod crashes on startup with:

```text
org.springframework.beans.factory.BeanCreationException:
  Error creating bean with name 'jwtDecoder':
  Failed to configure a JwtDecoder for issuer 'https://auth.acme.com/realms/acme'
Caused by: java.lang.IllegalArgumentException:
  Unable to retrieve the configuration from the issuer
```

The authorization server is genuinely down — a database migration went wrong.

**Your first move.**

```bash
# 1. Confirm the AS really is down and see what it returns.
curl -s -o /dev/null -w '%{http_code}\n' \
  https://auth.acme.com/realms/acme/.well-known/openid-configuration

# 2. Is the JWKS itself reachable?  (it may be served from a different host/CDN)
curl -s -o /dev/null -w '%{http_code}\n' \
  https://auth.acme.com/realms/acme/protocol/openid-connect/certs

# 3. What does the deployment actually configure?
kubectl get deploy orders-api -o jsonpath='{.spec.template.spec.containers[0].env}' \
  | tr ',' '\n' | grep -i issuer
```

**Expected signal.** Both the discovery document and the JWKS return **502** — the
authorization server is genuinely down. And the deployment configures **only**
`issuer-uri`, so Boot performs discovery at startup and the context cannot be built.

**The trap.** "Roll back the deployment." That would restore a working version of the
application, but it does not fix the design — the next deployment, or the next pod restart
during a routine autoscaling event, will fail the same way. The deployment was **coupled to
the authorization server's availability at boot time**.

**The resolution.** (1) Unblock the immediate deploy: add
`jwk-set-uri` alongside `issuer-uri`. The keys are fetched directly, **no discovery call**,
and the `issuer-uri` still drives the `iss` validation — so the security posture is unchanged
and the boot coupling is gone. (2) Fix the authorization server's database. (3) For the
future, make the deployment resilient: a `startupProbe` with a generous failure threshold,
so a pod that cannot reach the AS waits rather than crash-looping.

**The lesson.** `issuer-uri` is **convenient**, `jwk-set-uri` is **available**. Use both.
The two-line configuration is the difference between "the app is down when the AS is down"
and "the app serves cached tokens when the AS is down".

---

### Scenario 3 — "Users are being logged out every few minutes"

**The situation.** After a release that introduced refresh-token rotation, support tickets
arrive: *"I am logged out every 2–3 minutes."* The access token TTL is 5 minutes. Nobody
logs in twice in three minutes.

**Your first move.**

```bash
# 1. What is actually happening? Look for the reuse detection.
grep -i "reuse\|family\|invalid_grant" app.log | tail -30

# 2. How many distinct refresh tokens are presented per user per minute?
grep 'grant_type=refresh_token' as-access.log \
  | awk '{print $1, $NF}' | sort | uniq -c | sort -rn | head -20

# 3. Is the client's retry loop double-refreshing?
grep -n "refresh" src/main/java/com/example/orders/auth/*.java | head -20
```

**Expected signal.** The log shows a burst of **two or three** `grant_type=refresh_token`
requests with the **same** refresh token, within a few hundred milliseconds. The first one
succeeds and rotates; the second presents the now-**consumed** token → **reuse detected** →
the whole family is revoked → the user's session dies.

The cause is in the client: the SPA's HTTP layer **retries** a failed request, and the
refresh interceptor runs on **every** retried request, so three in-flight requests each
trigger a refresh with the same token.

**The trap.** "Increase the access-token TTL so they refresh less often." That treats the
symptom and reduces the security posture — and the race would still happen, just less
often.

**The resolution.** (1) **Client-side:** refresh inside a **mutex** — one in-flight refresh
per session, with all other callers awaiting it. Never let two requests refresh
concurrently. (2) **Server-side:** add a **short grace window** (10–30 s) — a token consumed
within that window returns the *same* successor instead of triggering a family revocation.
This is the standard fix for exactly this race. (3) Keep the family revocation, but make
the **reuse detection smarter**: distinguish "a token consumed within the grace window"
from "a token consumed days ago" — the latter is a theft, the former is a race. (4) Fix the
retry policy so the client does not retry a 401 from the refresh endpoint blindly.

**The lesson.** Rotation without a grace window turns every concurrency hiccup into a
forced logout. The reuse detector must distinguish **a race** from **a theft** — they look
identical on the wire, and the grace window is how you tell them apart.

---

### Scenario 4 — "A partner's token is accepted by our API and they should not be"

**The situation.** A partner integration can call an endpoint that was supposed to be
internal-only. Their token is perfectly valid — issued by the same authorization server, with
a valid signature and a valid `iss`. The only thing wrong is that they should not have been
able to obtain the scope.

**Your first move.**

```bash
# 1. What scopes does their token carry?
echo "$PARTNER_TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{aud,scope,sub,client_id}'

# 2. What does our endpoint require?
grep -rn "hasAuthority\|hasRole\|PreAuthorize" src/main/java/ | grep -i orders

# 3. What scopes is the partner client registered with at the AS?
#    Keycloak → realm → Clients → partner-x → Client scopes → Optional
```

**Expected signal.** The partner's token carries `scope: "orders:read orders:write admin"`.
The `admin` scope was granted because the partner's client was registered with an **optional
client scope** called `admin` — added during an earlier integration that nobody removed.
Our endpoint requires `ROLE_ADMIN`, which maps from `admin`, so the partner's token grants
it.

**The trap.** "Add a check for the partner's client id." That is a denylist, and denylists
grow — and it does not address the actual problem, which is that the **scope universe at the
authorization server** is too broad.

**The resolution.** (1) **Remove the unused optional client scope** from the partner's client
at the AS — the scope should never have been grantable. (2) **Audit every client's assigned
scopes** — this is a fleet-wide finding, not a partner-specific one; an "optional client
scope" left over from a pilot is the classic way privilege leaks. (3) Make the **scope →
authority mapping explicit and narrow**: a custom `JwtGrantedAuthoritiesConverter` that
maps only the scopes the application actually implements, and drops everything else — so an
unexpected scope can never become an authority. (4) Add a **CI test** that asserts the
mapping table contains exactly the expected entries.

**The lesson.** The scope universe is the authorization server's responsibility, and a scope
nobody cleans up is latent privilege. The application must also be **robust to an unexpected
scope** — map explicitly, drop the unknown.

---

## 17. Counter-Questions, Tricky Questions & Follow-Ups

### CQ1. "If client credentials has no user, why is the token's `sub` set to the client id?"

- **Naive answer:** "Because the client is the subject of the request."  ← *true, and it misses the design intent*
- **The trap:** The trap is thinking `sub` always means "a user". In the client-credentials
  grant there **is** no user, and the subject is the **application** — that is precisely the
  point. But `sub` is also the **audit key**: the token's subject is what you log, what you
  bill for, and what you rate-limit by.
- **Expert answer:** `sub` in OAuth 2.0 means "the entity the token is about", and RFC 6749
  §7.1 says the subject is the resource owner — but in the client-credentials grant there is
  no resource owner, so the AS sets the subject to the **client**, and RFC 6749 §4.4 says so
  explicitly. That makes the token **self-describing for audit**: the resource server can
  log "this request was made by `ledger-service`" from the token alone, with no extra
  lookup. The practical consequence is that your authorization rules must not assume every
  `sub` is a human — and a resource server that serves both user tokens and service tokens
  will see both shapes side by side.

### CQ2. "Why validate `aud` if the `iss` already identifies who issued the token?"

- **Naive answer:** "For extra security."  ← *true, and it misses the distinction*
- **The trap:** The trap is thinking `iss` and `aud` answer the same question. They answer
  **different** questions: `iss` answers *"who minted this?"* and `aud` answers *"who is it
  for?"*. In a **single-issuer** estate every token has the same `iss`, so `iss` provides
  **zero** service-to-service isolation.
- **Expert answer:** `iss` is a **fleet-level** trust anchor: "this token came from a
  system I trust". `aud` is a **service-level** authorisation: "this token was minted for
  *me*". Without `aud`, every service in the estate accepts every token, which means the
  estate's trust boundary is the authorization server and nothing else — a compromise of one
  service yields the whole estate. The attack it stops is the **confused deputy**: service A
  obtains a token for service B's audience and replays it at service A. The RFC 8707 `aud`
  parameter on the authorization request exists precisely to let a client *ask* for a
  narrowed token, and the resource server's `aud` validator is what makes that narrowing
  meaningful.

### CQ3. "Why not just use one long-lived token instead of access + refresh?"

- **Naive answer:** "Because short-lived tokens are more secure."  ← *true, and it misses the mechanism*
- **The trap:** The trap is assuming the only reason for two tokens is the TTL. The real
  reason is that the two tokens have **different trust properties**. The access token is
  **designed to leak** — it goes to every API, into logs, into browser memory. The refresh
  token is **designed never to leak** — it goes to exactly one endpoint, over TLS, and can be
  stored hashed.
- **Expert answer:** The split exists so that the credential that travels everywhere can be
  short-lived and cheap to verify, while the credential that is valuable can be long-lived
  and **statefully protected**. If you had one long-lived token, you would have to choose:
  make it short (and re-login every 15 minutes) or make it long (and it is a 8-hour
  credential sitting in every log). The refresh token also gives you **revocation** — you
  can kill the session without touching the access token, and with **rotation** you can
  *detect* the theft. The cost of the split is a stateful refresh path, which is exactly the
  state the access token was designed to avoid.

### CQ4. "Why does the resource server cache the JWKS? What if a key is revoked?"

- **Naive answer:** "Caching is for performance."  ← *true, and it hides the consequence*
- **The trap:** The trap is treating the JWKS cache as a pure optimisation. It is a
  **security-relevant cache**: a key removed from the JWKS is no longer usable by *new*
  verifications, but **tokens signed with it remain valid until they expire** — because the
  cache means the resource server may still be holding the old key.
- **Expert answer:** The cache is what makes local validation viable — a JWKS fetch per
  request would be an AS dependency on the hot path, which is the thing local validation
  exists to avoid. The consequence is that **key removal is not immediate**: the cache
  expires on its own schedule, and the library also refreshes on an **unknown `kid`**. The
  correct runbook is therefore **not** "remove the key when you revoke it" but
  **"publish → sign → wait (≥ max TTL + skew) → remove"**. For an *emergency* revocation —
  a compromised signing key — the answer is different: you must rotate to a **new** key,
  because no amount of cache-expiry will un-sign the tokens the attacker minted.

### CQ5. "Is introspection more secure than local validation?"

- **Naive answer:** "Yes, because it checks revocation in real time."  ← *true, and one-sided*
- **The trap:** The trap is treating "more secure" as one-dimensional. Introspection gives
  **better revocation** and **worse availability**. It also makes the authorization server a
  **hard dependency** of every API request — which is not just an availability issue, it is
  a **blast-radius** issue: an AS outage becomes an API outage, and an attacker who can
  disrupt the path to the AS can disable authentication.
- **Expert answer:** The right framing is a trade between three properties: **revocation
  latency**, **availability coupling**, and **AS throughput**. Introspection optimises the
  first at the cost of the other two. Local validation optimises the second and third and
  accepts that revocation waits for the TTL. The production answer for most systems is a
  **hybrid**: local validation everywhere, plus an immediate live check on the
  high-value endpoints (a Redis session record is often enough and avoids the AS entirely).
  And "more secure" is not even the right axis — a system that is **down** is not
  protecting anything.

### CQ6. "Why does the ID token need a `nonce` if the authorization code already had PKCE?"

- **Naive answer:** "Because defence in depth."  ← *true, and it misses the attack*
- **The trap:** The trap is thinking both artefacts protect "the same thing". PKCE protects
  the **code exchange**; `nonce` protects the **identity assertion**. The attack `nonce`
  stops has nothing to do with intercepting a redirect: it is the **replay of a captured ID
  token** into a *different* session.
- **Expert answer:** Consider a client that supports multiple sessions — a user signed in on
  a phone and a laptop. An ID token captured from the phone session is a **valid, correctly
  signed** token. If the client only checks the signature and `exp`, it will accept that
  token in the laptop session too. The `nonce` breaks this: the client generated a fresh
  nonce **per authorization request** and stored it with the pending request, so an ID token
  whose nonce does not match the one stored for *this* session is rejected. PKCE cannot help
  — the replayed token is genuine, it was just captured. The general principle: **every
  artefact that a client generates should be bound to the specific request it belongs to.**

### CQ7. "Can the resource server tell a client-credentials token from a user token?"

- **Naive answer:** "No, they're both JWTs."  ← *true at the format level, false in practice*
- **The trap:** The trap is looking at the format instead of the **claims**. The two tokens
  have the same *shape* and completely different *content* — a client-credentials token has
  a `client_id` claim and a `sub` equal to the client id, and no user-specific claims.
- **Expert answer:** Yes, and it matters. The reliable discriminators are: (1) the **`sub`**
  — a client id, not a user identifier; (2) the presence of a **`client_id`** claim; (3) the
  **absence of user claims** like `email`, `name` or `preferred_username`; and (4) the
  **authorization server's own metadata**, if it records the grant type. In Spring, I would
  map this to a distinct `Authentication` type — a `ClientAuthenticationToken` for service
  tokens and a `JwtAuthenticationToken` for user tokens — so that method-level security can
  distinguish them with `@PreAuthorize("hasAuthority('SCOPE_orders:read')")` versus an
  explicit "must be a user" check. The failure mode to avoid is code that assumes every
  principal is a human and renders `authentication.getName()` into a UI.

### CQ8. "Why would you ever want an opaque token instead of a JWT?"

- **Naive answer:** "Opaque tokens are more secure because they can't be read."  ← *false, and it confuses the properties*
- **The trap:** The trap is assuming "opaque" means "encrypted". An opaque token is
  **encrypted or random**, so yes, its contents cannot be read — but **that is not why you
  choose it**. The real reason is that an opaque token forces the **authorization server to
  be consulted**, which means the AS can answer **live questions**: is this still valid?
  is this grant still active? is the user still allowed to do this? That is the property a
  JWT cannot have.
- **Expert answer:** You choose an opaque token when the **decision depends on live state**:
  instant revocation, a ban list, a permission that changes mid-session, a device that has
  been revoked. The cost is a network call per request and the availability coupling. The
  alternative — keeping the JWT and adding a Redis session lookup for the high-value
  endpoints — gives you most of the benefit without the AS dependency. So the honest answer
  is: opaque tokens are chosen for **live authorisation**, not for confidentiality, and in
  most systems a hybrid (JWT + a Redis session record) beats pure introspection.

### CQ9. "What happens if the authorization server issues a token with no `aud`?"

- **Naive answer:** "The resource server accepts it."  ← *depends entirely on your validator*
- **The trap:** The trap is assuming the framework rejects it. Spring's
  `JwtValidators.createDefaultWithIssuer` does **not** look at `aud` at all, so a token with
  no `aud` sails through the default validator chain. And with a naive audience validator
  written as `aud -> aud.contains(serviceId)`, the **absence** case must be handled
  explicitly.
- **Expert answer:** With the correct validator — `aud != null && aud.contains(serviceId)` —
  a token with **no** `aud` is **rejected**, because `getAudience()` returns an **empty list**
  for an absent claim and `contains` on an empty list is `false`. This is exactly why the
  `aud != null` guard matters: it documents the intent and protects against a future
  refactor that changes the check to something like `aud.stream().anyMatch(...)`, which
  would throw on `null`. In a multi-issuer estate, a missing `aud` is a **policy violation**
  by the authorization server and should be rejected, because a token with no stated audience
  is a token for everybody.

### CQ10. "Is it safe to keep the ID token in the browser session?"

- **Naive answer:** "It's just a token, it's fine in the session."  ← *true, and it misses the audience rule*
- **The trap:** The trap is treating the ID token as just another credential and asking only
  "is it secret?". The ID token is **not** a credential for your API — it is an **assertion
  you have already consumed**. Keeping it around adds no value and creates a liability: it
  is a signed assertion of the user's identity that, if leaked, can be replayed into any
  session that does not check the `nonce`.
- **Expert answer:** You do not need to keep the ID token after the login is complete. You
  need the **claims** (the user's identity) and — if you support RP-initiated logout — the
  ID token **once**, as the `id_token_hint` at logout. So the correct handling is: decode it,
  verify it, extract the claims into your own session or user record, and keep the raw token
  only if a logout flow needs it. If you keep it, keep it **server-side** (in the session,
  not in `localStorage`), and always remember that it is addressed to **your client** and
  must never be forwarded to an API.

---

## 18. Frequently Asked Production Issues & Debugging Scenarios

### Issue 1 — "The app fails to start with `issuer-uri`"

**Symptom.** A `BeanCreationException` for `jwtDecoder`, caused by
`IllegalArgumentException: Unable to retrieve the configuration from the issuer`. The app
crash-loops.

**Diagnostic tool/command.**

```bash
# 1. Can the app reach the discovery document at all?
curl -s -i https://auth.acme.com/realms/acme/.well-known/openid-configuration | head -5

# 2. Is the JWKS reachable separately?  (it may be on a different host)
curl -s -o /dev/null -w '%{http_code}\n' \
  https://auth.acme.com/realms/acme/protocol/openid-connect/certs

# 3. Does the issuer in the document match the configured issuer EXACTLY?
curl -s https://auth.acme.com/realms/acme/.well-known/openid-configuration | jq -r .issuer
echo "configured: https://auth.acme.com/realms/acme"

# 4. From inside the cluster (the app may not have egress):
kubectl run netcheck --rm -it --image=curlimages/curl --restart=Never -- \
  curl -s -o /dev/null -w '%{http_code}\n' \
  https://auth.acme.com/realms/acme/.well-known/openid-configuration
```

**Expected signal.** Four distinguishable causes:

1. **The discovery document returns 404** → the `issuer-uri` has a **trailing slash**
   (`…/realms/acme/` → `…/realms/acme//.well-known/…`) or the **realm name is wrong**.
2. **The discovery document returns 502/503** → the authorization server is genuinely down,
   or the pod has **no egress** to it (a NetworkPolicy or a missing route). The
   `kubectl run netcheck` test distinguishes these.
3. **The discovery document returns 200 but the issuer differs** → the `issuer` field in the
   document does not match the configured `issuer-uri` (a trailing slash on one side). Spring
   compares them **literally**.
4. **A TLS error** → the certificate is not trusted by the JVM's truststore (a private CA
   that was never added).

**Fix.** (1) Remove the trailing slash and fix the realm path. (2) Fix the egress or the AS.
(3) Make the `issuer` value identical on both sides. (4) Add the CA to the truststore — and
in a container, prefer a **ConfigMap-mounted truststore** over `-Djavax.net.ssl.trustStore`
pointing at a path inside the image.

---

### Issue 2 — "The token is rejected with an `aud` mismatch"

**Symptom.** A token that was working yesterday now returns 401 with
`Jwt Validation failed`, and the AS says the token is fine.

**Diagnostic tool/command.**

```bash
# 1. Decode the token and read the audience.
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{iss,aud,azp,client_id}'

# 2. What does THIS service believe its id is?
kubectl get deploy orders-api -o jsonpath='{.spec.template.spec.containers[0].env}' \
  | tr ',' '\n' | grep -i 'service-id\|audience'

# 3. What did the AS actually put in the token?
#    Keycloak → realm → Clients → the client → Client scopes → the audience mapper
```

**Expected signal.** The token's `aud` is `["orders-api-v2"]` while the service is configured
with `acme.service-id=orders-api`. The service was **renamed** (or a v2 was deployed
alongside the v1) and the audience mapper at the authorization server was updated — but the
service's own configuration was not. The `aud` mismatch is not a security incident; it is a
**rename that was only half-done**.

The other common signal: the token has **no `aud` at all** — the AS is not adding an
audience mapper for that client, and the validator (correctly) rejects it.

**Fix.** Align the service id with what the AS issues. Treat the service id as a **contract**
between the authorization server and the service, and put it in **one place** — a shared
config, or a value read from the AS's client registration — so a rename updates both sides.

---

### Issue 3 — "The JWKS key rotation broke verification"

**Symptom.** Starting at 14:02, a subset of requests returns 401. The token's `kid` is
`key-2026-02`; the JWKS contains only `key-2026-01` and `key-2026-03`. The tokens signed
with `key-2026-01` still work.

**Diagnostic tool/command.**

```bash
# 1. What kid is in the failing token?
echo "$BAD" | cut -d. -f1 | base64 -d 2>/dev/null | jq .kid

# 2. What does the JWKS publish?
curl -s "$JWKS_URI" | jq '.keys[] | {kid, alg, use}'

# 3. Is the resource server refreshing the JWKS when it sees an unknown kid?
kubectl logs deploy/orders-api --tail=200 | grep -i "JWK\|kid\|refresh"
```

**Expected signal.** The failing token's `kid` is **not in the JWKS at all** — it was never
published. This is a different failure from "the old key was removed too early". Here the
AS **signed with a key it never published**, so no verifier anywhere could ever validate
those tokens.

The other, more common variant: the failing tokens have `kid=key-2026-02`, the JWKS contains
`key-2026-01` and `key-2026-03`, and `key-2026-02` **was** published but has since been
removed. That is the "removed the old key too early" bug — the tokens signed with it are
still within their TTL and are now unverifiable.

**Fix.** (1) **Immediately** publish the missing key to the JWKS (a configuration push, no
redeploy). (2) Then fix the rotation runbook to the three-phase procedure: publish the new
key **first**, sign with it, wait **≥ max token TTL + skew**, *then* remove the old key.
(3) Add an alert on `jwt.failures.by_reason{reason="unknown_kid"}` — it would have fired at
the moment of the rotation.

---

### Issue 4 — "The resource server accepts a token meant for another service"

**Symptom.** A security tester demonstrates that a token minted for `billing-api` is accepted
by `orders-api`.

**Diagnostic tool/command.**

```bash
# 1. Prove it.
TOKEN=$(curl -s -X POST https://auth.acme.com/realms/acme/protocol/openid-connect/token \
  -u 'billing-worker:secret' -d 'grant_type=client_credentials&scope=orders:read' \
  | jq -r .access_token)
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq .aud
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  https://api.acme.com/orders/api/orders

# 2. Is there an audience validator at all?
grep -rn "JwtClaimValidator\|DelegatingOAuth2TokenValidator\|setJwtValidator" src/main/java/

# 3. Which services in the estate lack an audience validator?
grep -rLn "JwtClaimNames.AUD" --include=*.java services/*/src/main/java/ | head -20
```

**Expected signal.** The grep in step 2 finds exactly one `setJwtValidator` call, and it is
`JwtValidators.createDefaultWithIssuer(issuer)`. Step 3 shows the **fleet-wide** count of
services with no `aud` validator — and it is not one service, it is **all of them**.

**Fix.** (1) Add the audience validator in **shared configuration** with the service id as a
required property. (2) Rotate the signing keys to invalidate the outstanding tokens that
were accepted cross-service. (3) **Audit the authorization server's issuance and the API's
access logs** to find out how many cross-service presentations actually occurred — that is
the real blast radius. (4) Add a CI regression test.

---

### Issue 5 — "Refresh tokens fail intermittently with `invalid_grant`"

**Symptom.** A small percentage of refresh requests fail with `400 invalid_grant`, and the
users are logged out.

**Diagnostic tool/command.**

```bash
# 1. How many refresh requests share the SAME token in a short window?
grep 'grant_type=refresh_token' as-access.log \
  | awk '{print $1, $NF}' | sort | uniq -c | sort -rn | head -20

# 2. Is reuse detection firing?
grep -i "reuse\|family revoked" app.log | tail -20

# 3. What is the client's refresh behaviour?  (a retry loop, two tabs, a mobile + web client)
grep -rn "grant_type=refresh_token\|refreshToken" src/main/java/ | head -20
```

**Expected signal.** The same refresh token appears **two or three times within a few
hundred milliseconds**, and the reuse detector fires. This is the concurrent-refresh race
(scenario 3 in §16) — not a theft, but the system cannot tell the difference.

**Fix.** (1) Add a **grace window** of 10–30 seconds on the server: a token consumed within
the window returns the same successor instead of revoking the family. (2) Add a **client-side
mutex** so one client refreshes once. (3) Distinguish a *late* reuse (days later — a theft)
from a *concurrent* one (seconds later — a race) in the reuse detector. (4) Do **not** disable
reuse detection — that is the control that detects a stolen token.

---

## 19. Architecture Design Questions

### 19.1 "Design service-to-service authentication for 40 microservices"

**The requirement.** 40 microservices must call each other. Some calls are user-context
(an edge service forwarding a user's request) and some are not. The estate must survive an
authorization-server outage for at least the length of one access-token TTL. Audit must be
able to attribute every call to a principal.

**Reference design.**

```text
   ┌──────────────────────────────────────────────────────────────────────┐
   │  AUTHORIZATION SERVER (one, centralised)                            │
   │   • client_credentials for every internal service                    │
   │   • authorization_code + PKCE for the edge/user-facing services     │
   │   • client scopes per service (orders:read, fraud:write, …)         │
   │   • audience mappers: aud = the downstream service ids              │
   │   • one signing key, rotated quarterly with a 2-key overlap        │
   └───────────┬──────────────────────────────────────────────────────────┘
               │  issuer + JWKS (published, cached by every service)
               ▼
   ┌──────────────────────────────────────────────────────────────────────┐
   │  EVERY INTERNAL SERVICE                                            │
   │   • spring-boot-starter-oauth2-resource-server                      │
   │   • SHARED JwtDecoder configuration:                                │
   │       - jwk-set-uri + issuer-uri (no discovery at boot)            │
   │       - pinned RS256                                               │
   │       - validators: iss + timestamps + aud (REQUIRED property)      │
   │       - a tenant validator for multi-tenant services                │
   │   • a shared ClientCredentialsTokenProvider with a 60 s cache skew  │
   └──────────────────────────────────────────────────────────────────────┘

   USER-CONTEXT CALLS (the edge service forwards a user's request):
     → the edge holds the user's token and uses TOKEN EXCHANGE (RFC 8693)
       to obtain a downstream-scoped token, OR the edge forwards the
       user's access token directly if the audiences match.
```

**The decisions and the trade-offs.**

| Decision | Choice | Trade-off |
|---|---|---|
| **JWT vs opaque** | **JWT** (local validation) | Instant revocation is lost; the trade is that the estate survives an AS outage. Where a live check is required, add a **Redis session record** on the specific endpoint, not introspection everywhere |
| **Audience** | `aud` = the downstream service id, validated by every resource server | Requires the AS to know the service topology; a rename must update both sides |
| **Boot coupling** | `jwk-set-uri` + `issuer-uri` | You hard-code the key location; the payoff is that a deployment does not depend on the AS |
| **Token TTL (service)** | **30–60 s** | Short, because re-minting is ~10 ms; but a burst of expiries can stampede the AS — randomise the cache skew |
| **User-context calls** | Token exchange | More moving parts; the alternative (forwarding the user's token) only works if the audiences are the same |
| **Audit** | The token's `sub` + `client_id` logged on every request, plus an AS-side issuance log | A per-request DB write is too expensive; log to stdout and let the pipeline index it |

**The honest trade-off to say out loud.** Local validation means a revoked service credential
remains usable for up to its TTL (30–60 s here — a short window, deliberately). If the
compliance requirement is *instant* revocation of a **service** credential, the answer is
either a very short TTL (which increases the AS load by the same factor) or an opaque token
with introspection for that one service. There is no free lunch: **you can have instant
revocation or you can have availability, but the TTL is the price of both.**

---

### 19.2 "Design the identity layer for a marketplace with 12,000 tenants"

**The requirement.** A multi-tenant marketplace with 12,000 workspaces. Tenants may bring
their own identity provider (OIDC or SAML) or use the platform's. Users must be able to log
in with social providers. Tenant boundaries must be airtight. The platform must issue its own
tokens to its own APIs.

**Reference design.**

```text
   ┌──────────────────────────────────────────────────────────────────────┐
   │  A TENANT-AWARE AUTHORIZATION SERVER                               │
   │  (Spring Authorization Server, or a per-tenant realm in Keycloak)   │
   │                                                                      │
   │  • ONE platform issuer                                              │
   │      https://auth.acme.com                                          │
   │  • per-tenant OIDC clients (client_id = tenant + app)               │
   │  • per-tenant SAML IdPs federated in                                │
   │  • a `tenant_id` claim on EVERY token                               │
   │  • a `sub` that is stable per (iss, tenant, external_subject)       │
   └───────────┬──────────────────────────────────────────────────────────┘
               │  issuer + JWKS
               ▼
   ┌──────────────────────────────────────────────────────────────────────┐
   │  THE PLATFORM APIs                                                  │
   │  • shared JwtDecoder config                                         │
   │  • validators: iss + timestamps + aud + ★ tenant_id                 │
   │  • the tenant_id validator reads the tenant from the request context │
   │    (the Host header or a resolved workspace) and requires a match    │
   └──────────────────────────────────────────────────────────────────────┘

   ┌──────────────────────────────────────────────────────────────────────┐
   │  USER ACCOUNTS                                                      │
   │  • a local user row keyed by (issuer, external_subject)             │
   │  • a membership table: (user_id, tenant_id, role)                   │
   │  • JIT provisioning on first login                                  │
   │  • email-based linking ONLY for verified emails                     │
   └──────────────────────────────────────────────────────────────────────┘
```

**The decisions.**

| Decision | Choice | Why |
|---|---|---|
| **One issuer or one per tenant?** | **One issuer**, with per-tenant clients and a `tenant_id` claim | One key set, one rotation runbook, one `aud` convention. Per-tenant realms multiply the operational surface enormously |
| **Tenant isolation** | A **mandatory `tenant_id` validator** comparing the token's claim with the request's resolved tenant | The `aud` alone cannot isolate tenants — one API serves all of them |
| **How is the tenant resolved?** | From the **Host** (a subdomain) resolved to a workspace **server-side**, never from a client-supplied header | A client-supplied tenant header is trivially forged |
| **Local user identity** | Keyed by **`(issuer, external_subject)`**, never by email | `sub` is stable per issuer; email changes and can be recycled |
| **Social login** | Allowed, but the account is linked only on a **verified** email, and a social account can never be auto-linked to an existing account with a different `sub` | Prevents account takeover (chapter 04) |
| **SAML tenants** | Federated into the platform AS via OIDC | One token format downstream, one validator set |
| **Token TTL** | 10 min access, 8 h absolute refresh, rotated | A working day is ~8 h; the absolute cap stops an old session living forever |

**The trade-offs to say out loud.**

1. **One issuer concentrates blast radius.** A compromise of the platform AS affects all
   12,000 tenants. The mitigations are strict client isolation, per-tenant client ids, and
   a very short token TTL.
2. **`tenant_id` in every token grows the payload** and adds a per-request validation that
   must be cheap. It is O(1) and negligible, but it means every query must be filtered by it
   — and the real risk is a **missing filter in a repository query**, not the token.
3. **Federating SAML tenants** means supporting two identity protocols at the edge. The
   platform itself only ever sees OIDC tokens, which is the point — but the federation layer
   is a component you now own.
4. **Social login weakens the tenant boundary**: a user with a Google account can join any
   tenant that allows social sign-in. Tenant policy must therefore decide *which* tenants
   allow it, and the domain allowlist must be enforced **at the AS** (via a social
   provider's domain restriction) as well as in your code.

---

## 20. Rapid-Fire MCQs

**1.** In the Client Credentials grant, the access token's `sub` is:
- A) The end user's id
- B) `null`
- C) **The client (application) id**
- D) A random UUID per token

**2.** The Client Credentials grant returns a refresh token:
- A) Always
- B) If the client asks for `offline_access`
- C) **Never — there is no user session to refresh**
- D) Only for confidential clients

**3.** Which scope triggers OpenID Connect behaviour and an ID token?
- A) `profile`
- B) `email`
- C) **`openid`**
- D) `userinfo`

**4.** The ID token's `aud` is:
- A) The resource server
- B) **Your client**
- C) The authorization server
- D) The user's email

**5.** `JwtValidators.createDefaultWithIssuer(issuer)` validates:
- A) `iss`, `exp` and `aud`
- B) **`iss`, `exp` and `nbf` only**
- C) Only the signature
- D) `iss` and `aud` only

**6.** Using `issuer-uri` for a Spring resource server means the application:
- A) Fetches the JWKS on every request
- B) **Performs OIDC discovery at startup and will fail to start if the AS is unreachable**
- C) Never validates the issuer
- D) Uses introspection

**7.** To make a resource server start even when the authorization server is down, add:
- A) `opaque-token.introspection-uri`
- B) **`jwk-set-uri`**
- C) A second `issuer-uri`
- D) `spring.security.oauth2.client.registration`

**8.** The OIDC discovery document lives at:
- A) `/oauth2/authorize`
- B) `/.well-known/openid-configuration`
- C) `/jwks.json`
- D) `/userinfo`

**9.** In RFC 7662 introspection, the decisive field is:
- A) `scope`
- B) `client_id`
- C) **`active`**
- D) `exp`

**10.** Refresh-token **rotation** means:
- A) The refresh token's TTL is extended on use
- B) **Each refresh issues a new refresh token and invalidates the old one**
- C) The refresh token is encrypted at rest
- D) The refresh token is signed

**11.** Presenting an already-consumed refresh token is called:
- A) Expiry
- B) **Reuse, and the correct response is to revoke the whole token family**
- C) A race condition, always
- D) A scope violation

**12.** The `scope` claim of an access token maps by default to Spring authorities:
- A) `ROLE_`-prefixed
- B) **`SCOPE_`-prefixed**
- C) `AUTHORITY_`-prefixed
- D) Mapped verbatim, no prefix

**13.** `HttpCookieOAuth2AuthorizationRequestRepository` requires:
- A) A shared session store
- B) A wildcard redirect URI
- C) **CSRF protection to be enabled for the callback**
- D) A client secret

**14.** The OIDC logout endpoint is reached through:
- A) `POST /oauth2/logout` on your API
- B) **`end_session_endpoint` with `id_token_hint` and a registered `post_logout_redirect_uri`**
- C) Revoking the refresh token
- D) Deleting the local session

**15.** `sub` should be used as a user's key together with:
- A) The email
- B) The scope
- C) **The issuer (`iss`)**
- D) The access token's `jti`

**16.** A client-credentials token has no user. How does the resource server tell it apart
from a user token?
- A) It cannot
- B) **By the `sub`/`client_id` claims and the absence of user claims**
- C) By the token's length
- D) By the HTTP method

**17.** Which is the strongest reason to prefer local JWT validation over introspection?
- A) It gives instant revocation
- B) **It removes the authorization server from the per-request path, so the API survives an AS outage**
- C) It hides the claims
- D) It works without a client secret

**18.** When the introspection endpoint is unreachable, a resource server should:
- A) Accept the token
- B) Return 500
- C) **Fail closed — 401**
- D) Retry indefinitely

**19.** `email_verified` matters because:
- A) It speeds up the login
- B) **An unverified email must not be used to link an existing account**
- C) It is required by the `openid` scope
- D) It replaces the `nonce`

**20.** A good scope-to-authority mapping should:
- A) Map every scope to a `ROLE_` authority
- B) Use the token's `scope` verbatim
- C) **Map known scopes explicitly and drop unknown ones**
- D) Put the scopes in the `SecurityContext` name

---

### Answer Key

| # | Answer | One-line reason |
|---|--------|-----------------|
| 1 | **C** | There is no user in the flow; the subject is the application. |
| 2 | **C** | No user session exists, so there is nothing for a refresh token to preserve. |
| 3 | **C** | The literal `openid` scope is what makes it OIDC and triggers the ID token. |
| 4 | **B** | The ID token is addressed to the client; the access token is addressed to the API. |
| 5 | **B** | Issuer and timestamps — the audience is never checked by the default. |
| 6 | **B** | `issuer-uri` triggers discovery at startup; the failure is a startup failure, not a degraded mode. |
| 7 | **B** | `jwk-set-uri` fetches the keys directly and skips discovery. |
| 8 | **B** | The OIDC metadata document; RFC 8414's `/.well-known/oauth-authorization-server` is the OAuth-only variant. |
| 9 | **C** | `active: false` is the only decision-relevant field. |
| 10 | **B** | Rotation makes the refresh token single-use. |
| 11 | **B** | Reuse means a copy escaped — the family is revoked and a fresh login is required. |
| 12 | **B** | `JwtGrantedAuthoritiesConverter` defaults to the `SCOPE_` prefix. |
| 13 | **C** | The pending request is in a cookie, so the callback is a cross-site state-changing request. |
| 14 | **B** | RP-initiated logout redirects to the provider's `end_session_endpoint`. |
| 15 | **C** | `sub` is unique per issuer; two providers will use the same value for different humans. |
| 16 | **B** | The claims differ even though the format does not. |
| 17 | **B** | Local validation removes the AS from the hot path; introspection adds a hop and an availability dependency. |
| 18 | **C** | Failing open lets an attacker authenticate by breaking the path to the AS. |
| 19 | **B** | Auto-linking on an unverified email is an account-takeover vector. |
| 20 | **C** | A blanket mapping turns a mis-named or newly-granted scope into unintended privilege. |

> 📌 **The four to re-read:** **Q5** (the `aud` gotcha), **Q7** (the availability escape
> hatch), **Q11** (reuse detection) and **Q15** (key users by `(iss, sub)`).

---

## 21. Cheat Sheet / One-Page Summary

| Area | Fact |
|---|---|
| **Client Credentials** | No user · the token represents the **application** · no refresh token · one POST |
| **Client Credentials request** | `POST /token` · `grant_type=client_credentials` · `scope` · `Basic(client_id:secret)` |
| **Client Credentials response** | `access_token` · `token_type` · `expires_in` · `scope` — **no `id_token`, no `refresh_token`** |
| **Service token TTL** | **30–60 s** · cache for `expires_in - 60 s` · randomise the first fetch |
| **Refresh Token grant** | `POST /token` · `grant_type=refresh_token` · `refresh_token=…` |
| **Rotation** | Each refresh returns a **new** refresh token and consumes the old one |
| **Reuse detection** | A consumed token presented again ⇒ **revoke the whole family** |
| **Lifetimes** | Absolute (hard cap) + relative (sliding); effective = the earlier |
| **`refresh_token_expires_in`** | A non-standard extension (Keycloak, Auth0) reporting the absolute refresh lifetime |
| **OIDC = OAuth2 +** | the `openid` scope · the **ID token** · **UserInfo** · **discovery** · **RP-initiated logout** |
| **ID token** | A JWT, `aud` = **your client** · never an API credential |
| **ID token claims** | `iss` `sub` `aud` `exp` `iat` `auth_time` `nonce` `at_hash` `azp` `acr` `amr` + profile claims |
| **`nonce`** | Must be sent and verified inside the ID token |
| **UserInfo** | `GET /userinfo` with the access token · the `sub` must match the ID token's |
| **Discovery** | `/.well-known/openid-configuration` · `/.well-known/oauth-authorization-server` |
| **Logout** | `end_session_endpoint` + `id_token_hint` + a registered `post_logout_redirect_uri` |
| **Scope → claims** | `openid` → the mandatory set · `profile` → name/picture/locale · `email` → email + verified |
| **`issuer-uri`** | Triggers **OIDC discovery at startup** · the app will **not start** if the AS is down |
| **`jwk-set-uri`** | Skips discovery · the availability escape hatch · pair it with `issuer-uri` |
| **`createDefaultWithIssuer`** | Validates `iss` + `exp` + `nbf` — **NOT** `aud` |
| **The `aud` validator** | `new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(serviceId))` |
| **Compose validators** | `new DelegatingOAuth2TokenValidator<>(defaultValidator, audienceValidator)` |
| **Scope → authority** | `SCOPE_`-prefixed by default · map explicitly and drop unknown scopes |
| **Introspection** | `POST /token/introspect` · the decisive field is **`active`** · fail **closed** |
| **Introspection vs local** | 5–40 ms + AS dependency · vs 90 µs + no dependency |
| **User key** | **`(iss, sub)`** — never the email, never `sub` alone |
| **Hard rules** | Validate `aud` · pin the algorithm · hash + rotate refresh tokens · reuse detection · minimal scopes · check `email_verified` · fail closed |

---

## 22. Real-World Production Notes

| Knob / Behaviour | Default | When to change it |
|---|---|---|
| Service access-token TTL | 60 s | 30 s for a high-value internal API; never above 5 min — re-minting is cheap |
| Service token cache skew | `expires_in - 60 s` | Keep ≥ 30 s; **randomise** the first fetch to avoid an expiry stampede |
| User access-token TTL | 10 min | 5 min for banking; 15 min only for a low-privilege SPA |
| Refresh-token absolute TTL | 30 days | 7 days for regulated industries |
| Refresh-token relative TTL | 30 days | 7 days of inactivity for a shared device |
| Refresh rotation | On | Always — with reuse detection |
| Refresh grace window | 0 s | **10–30 s** — the standard fix for the concurrent-refresh race |
| Introspection cache | 0 s (always live) | 5 s for read-only endpoints only; never on a high-value one |
| `issuer-uri` | Set | Always — it gives the `iss` validation for free |
| `jwk-set-uri` | Not set | **Set it** alongside `issuer-uri` for any service that must survive an AS outage |
| Discovery at startup | On | Off by using `jwk-set-uri` |
| Clock-skew leeway | 60 s | 10 s when you control both clocks; **never** above 60 s |
| Key-rotation overlap | ≥ max TTL + skew | **Always** publish → sign → wait → remove |
| Token audience | One service id per API | An array only when one token legitimately serves two APIs — and validate it |
| Refresh-token hashing | SHA-256 | Fine for 256 bits of CSPRNG output; use a slow KDF only for user-chosen secrets |
| Scope set | Minimal per client | Split broad scopes so a grant can be narrowed without re-consent |
| `email_verified` check | Required | **Never** link an account on an unverified email |
| User key | `(iss, sub)` | Never the email — emails change and are recycled |
| Logout | Provider + local | Always end **both** sessions |

**Metrics worth having on a dashboard.**

| Metric | Why |
|---|---|
| `oauth2.token.issued.count{grant_type,client_id}` | Detects a client that re-mints instead of caching |
| `refresh.token.reuse.detected` | **The most important security metric in this chapter** |
| `refresh.token.rotation.count` | A sudden drop means clients are not refreshing — or are failing |
| `jwt.failures.by_reason` (signature / claims / unknown-kid / audience) | Separates an attack from a misconfiguration |
| `jwks.cache.hit_ratio` | A fall means unknown `kid`s — a rotation, or an attack |
| `oauth2.introspect.duration{p99}` | The introspection tax, if you use it |
| `security.oauth2.authorizations` | The 401 rate at the API |
| `token.size.p99` | A rising tail means the claim set is growing |

**Defaults to deviate from, deliberately.**

- **Never** raise the service-token TTL to "avoid re-minting" — re-minting is cheap.
- **Never** disable reuse detection to stop the race — add a grace window instead.
- **Never** remove the old signing key before the TTL has elapsed.
- **Never** fail open when the introspection endpoint is unreachable.
- **Never** auto-link accounts on an unverified email.
- **Never** use `sub` alone as a user key.

---

## 23. Follow-up Learning

**Next in this folder**

- → [04 · Social Login, Keycloak, Enterprise SSO & Security](./04-Social-Login-Keycloak-Enterprise-SSO-and-Security.md) —
  "login with Google", the GitHub private-email gotcha, Keycloak realms and roles, SAML vs
  OIDC, JIT provisioning, scope minimisation, the corporate-domain allowlist.

**Previous in this folder**

- ← [01 · OAuth2 Architecture, Roles & Grant Types](./01-OAuth2-Architecture-Roles-and-Grant-Types.md) —
  the roles, the tokens, the grant types, the Spring starters.
- ← [02 · Authorization Code & PKCE Flows](./02-Authorization-Code-and-PKCE-Flows.md) — the
  code flow, `state`, `nonce`, PKCE.

**Related folders**

- [06 · JWT Authentication](../06-JWT-Authentication/README.md) — the token format, the
  validators, the `JwtDecoder` chain.
  → [06/01 · JWT Architecture](../06-JWT-Authentication/01-JWT-Architecture-Statelessness-and-Signature-Algorithms.md)
  → [06/02 · Access & Refresh Tokens and Token Rotation](../06-JWT-Authentication/02-Access-Refresh-Tokens-and-Token-Rotation.md)
- [05 · Spring Security](../05-Spring-Security/README.md) — the filter chain, the
  `SecurityContext`, method security.
- **08 · Microservices with Spring Boot** (folder, being written in parallel) — the gateway,
  service-to-service calls, resilience.

**Source pointers (RFCs and Spring Security 6.1)**

| Document | Why read it |
|---|---|
| RFC 6749 §4.4 | The client-credentials grant, and the explicit "no refresh token" rule |
| RFC 6749 §6 | The refresh-token grant |
| RFC 6749 §5.2 | The token-endpoint error codes (`invalid_client`, `invalid_grant`, `invalid_scope`) |
| RFC 7636 | PKCE — see chapter 02 |
| RFC 7662 | Token introspection — the request, the response, the `active` field |
| RFC 7009 | Token revocation |
| RFC 8414 | Authorization server metadata (`/.well-known/oauth-authorization-server`) |
| RFC 8693 | Token exchange — delegation and impersonation |
| RFC 8707 | Resource indicators — the `resource`/`audience` parameters |
| RFC 8705 | mTLS-bound access tokens |
| RFC 9449 | DPoP |
| RFC 9700 | The OAuth 2.0 Security BCP |
| OpenID Connect Core 1.0 | The `openid` scope, the ID token claims, `nonce`, `auth_time`, `azp` |
| OpenID Connect Discovery 1.0 | The discovery document and the `issuer` match rule |
| OpenID Connect RP-Initiated Logout 1.0 | `end_session_endpoint`, `id_token_hint`, `post_logout_redirect_uri` |
| Spring Security 6.x `OAuth2ResourceServerConfigurer` | `jwt()` vs `opaqueToken()` |
| Spring Security 6.x `JwtValidators` / `JwtClaimValidator` | The validator chain and the audience gap |
| Spring Security 6.x `NimbusOpaqueTokenIntrospector` | The introspection implementation (`SpringOpaqueTokenIntrospector` is deprecated in 6.4) |

---

**Next Chapter:** → [04 · Social Login, Keycloak, Enterprise SSO & Security](./04-Social-Login-Keycloak-Enterprise-SSO-and-Security.md)
**Previous Chapter:** ← [02 · Authorization Code & PKCE Flows](./02-Authorization-Code-and-PKCE-Flows.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026


